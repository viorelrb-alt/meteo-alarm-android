package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * US weather warnings from the National Weather Service (api.weather.gov, v2.30): every active
 * alert whose county or polygon holds the town. Mapped to the app's three colours like the NWS
 * itself ranks them: Advisory / Statement = yellow, Watch = orange, Warning / Emergency = red.
 */
public final class Nws {
	private Nws() {
	}

	/** The active alerts for the point LAT LON (GeoJSON). */
	public static String url(double lat, double lon) {
		return "https://api.weather.gov/alerts/active?status=actual&point=" + Weather.fmt(Math.round(lat * 10000) / 10000.0) + ","
				+ Weather.fmt(Math.round(lon * 10000) / 10000.0);
	}

	/** 3 Warning / Emergency, 2 Watch, 1 Advisory, Statement and the rest. */
	public static int level(String event) {
		String e = event == null ? "" : event.trim().toLowerCase();
		if (e.endsWith("warning") || e.endsWith("emergency")) return 3;
		if (e.endsWith("watch")) return 2;
		return 1;
	}

	/** Events that get the loud notification (tornado and the like). */
	public static boolean extreme(String event) {
		String e = event == null ? "" : event.toLowerCase();
		return e.startsWith("tornado warning") || e.startsWith("tornado emergency") || e.startsWith("severe thunderstorm warning")
				|| e.startsWith("flash flood warning") || e.startsWith("flash flood emergency") || e.startsWith("extreme wind warning")
				|| e.startsWith("hurricane warning") || e.startsWith("storm surge warning") || e.startsWith("tsunami warning")
				|| e.startsWith("blizzard warning") || e.startsWith("ice storm warning");
	}

	/** Active alerts in STATE (postal code, GeoJSON). */
	public static String stateUrl(String state) {
		return "https://api.weather.gov/alerts/active?status=actual&area=" + state;
	}

	/** Active alerts of EVENT in the whole country ("Tornado Warning"). */
	public static String eventUrl(String event) {
		return "https://api.weather.gov/alerts/active?status=actual&event=" + event.replace(" ", "%20");
	}

	/** A tornado watch or warning (the background refresh then comes every 5 minutes). */
	public static boolean tornado(String event) {
		return event != null && event.toLowerCase().startsWith("tornado");
	}

	/** Order inside one colour: tornado first, then the other extreme ones, then the rest. */
	private static int rank(String event) {
		if (tornado(event)) return 0;
		return extreme(event) ? 1 : 2;
	}

	/** NWS text as paragraphs: the hard line breaks joined, the blank lines kept. */
	static String unwrap(String t) {
		if (t == null) return "";
		String s = t.replace("\r", "").trim();
		s = s.replaceAll("\n\\s*\n", "\u0001").replaceAll("\\s*\n\\s*", " ").replace('\u0001', '\n');
		return s.replaceAll("[ \t]+", " ").trim();
	}

	private static String str(Map<String, Object> m, String k) {
		Object v = m.get(k);
		return v instanceof String ? (String) v : "";
	}

	/** The alerts in force now or starting within 24 h, one per event (the latest text), red first, tornado first. */
	public static List<Warnings.Warn> parse(String json, long now) {
		return parse(json, now, true);
	}

	/** The alerts; MERGE false keeps every alert apart (a state's tornado list, v2.40), with its area. */
	public static List<Warnings.Warn> parse(String json, long now, boolean merge) {
		List<Warnings.Warn> list = new ArrayList<Warnings.Warn>();
		if (json == null || json.trim().length() == 0 || !json.trim().startsWith("{")) return list;
		Object root = Json.parse(json);
		@SuppressWarnings("unchecked")
		List<Object> fs = root instanceof Map ? Json.arr((Map<String, Object>) root, "features") : new ArrayList<Object>();
		for (Object o : fs) {
			Map<String, Object> p = Json.obj(o, "properties");
			if (p == null) continue;
			String status = str(p, "status"), type = str(p, "messageType"), event = str(p, "event").trim();
			if (event.length() == 0 || (status.length() > 0 && !status.equalsIgnoreCase("Actual")) || type.equalsIgnoreCase("Cancel")) continue;
			if (event.toLowerCase().contains("test message")) continue;
			long on = Warnings.epoch(str(p, "onset"));
			if (on == 0) on = Warnings.epoch(str(p, "effective"));
			long ex = Warnings.epoch(str(p, "ends"));
			if (ex == 0) ex = Warnings.epoch(str(p, "expires"));
			if (ex == 0) ex = now + 3600000L;
			if (ex <= now || on - now > 86400000L) continue;
			long sent = Warnings.epoch(str(p, "sent"));
			Warnings.Warn same = null;
			if (merge) for (Warnings.Warn w : list) if (w.type.equalsIgnoreCase(event)) same = w;
			boolean newer = true;
			if (same == null) {
				same = new Warnings.Warn();
				same.nws = true;
				same.type = event;
				same.level = level(event);
				same.from = on;
				same.until = ex;
				list.add(same);
			} else {
				newer = sent >= same.sentAt;
				same.from = Math.min(same.from, on);
				same.until = Math.max(same.until, ex);
			}
			if (newer) {
				same.sentAt = sent;
				same.headline = str(p, "headline").trim();
				same.desc = unwrap(str(p, "description"));
				same.instr = unwrap(str(p, "instruction"));
				same.area = str(p, "areaDesc").trim();
			}
		}
		java.util.Collections.sort(list, new java.util.Comparator<Warnings.Warn>() {
			public int compare(Warnings.Warn a, Warnings.Warn b) {
				if (a.level != b.level) return b.level - a.level;
				int ra = rank(a.type), rb = rank(b.type);
				if (ra != rb) return ra - rb;
				return a.from < b.from ? -1 : a.from > b.from ? 1 : 0;
			}
		});
		return list;
	}

	// --- Names -----------------------------------------------------------------------------

	/** The phenomenon without the level: "Tornado Warning" -> "Tornado". */
	public static String phenomenon(String event) {
		String e = event == null ? "" : event.trim();
		String p = e.replaceFirst("(?i)\\s+(warning|watch|advisory|statement|emergency|outlook|message|alert)$", "");
		return p.length() == 0 ? e : p;
	}

	/** The level's word: Warning, Watch, Advisory (Statement and others are counted as Advisory). */
	public static String levelWord(int level) {
		if (level >= 3) return Tr.t("Warning", "Avertizare");
		if (level == 2) return Tr.t("Watch", "Atenționare");
		return Tr.t("Advisory", "Informare");
	}

	/** The same in capitals, for the box's title bar. */
	public static String levelUp(int level) {
		if (level >= 3) return Tr.t("WARNING", "AVERTIZARE");
		if (level == 2) return Tr.t("WATCH", "ATENȚIONARE");
		return Tr.t("ADVISORY", "INFORMARE");
	}

	/** The event in the app's language: English as NWS names it; Romanian and Spanish "Level: phenomenon". */
	public static String event(String event) {
		String l = Tr.lang();
		if (!l.equals("ro") && !l.equals("es")) return event;
		if (event.equalsIgnoreCase("special weather statement")) return l.equals("ro") ? "Informare meteorologică specială" : "Aviso meteorológico especial";
		String ph = name(phenomenon(event));
		if (ph == null) return event;
		return levelWord(level(event)) + ": " + ph.substring(0, 1).toLowerCase() + ph.substring(1);
	}

	/** The phenomenon in the app's language (English when it is not known). */
	public static String phenomenonShown(String event) {
		String ph = phenomenon(event);
		String n = name(ph);
		return n != null ? n : ph;
	}

	/** Romanian or Spanish name of an NWS phenomenon (null when unknown or in English). */
	private static String name(String ph) {
		boolean ro = Tr.lang().equals("ro");
		if (!ro && !Tr.lang().equals("es")) return null;
		String k = ph.toLowerCase().trim();
		for (int i = 0; i < NAMES.length; i += 3) if (NAMES[i].equals(k)) return ro ? NAMES[i + 1] : NAMES[i + 2];
		return null;
	}

	/** NWS phenomenon (lower case), Romanian, Spanish. */
	private static final String[] NAMES = { "tornado", "Tornadă", "Tornado",
			"severe thunderstorm", "Furtună severă", "Tormenta severa",
			"flash flood", "Viitură", "Inundación repentina",
			"flood", "Inundații", "Inundación",
			"areal flood", "Inundații locale", "Inundación de área",
			"coastal flood", "Inundații costiere", "Inundación costera",
			"lakeshore flood", "Inundații pe malul lacului", "Inundación en la costa del lago",
			"winter storm", "Furtună de iarnă", "Tormenta invernal",
			"blizzard", "Viscol", "Ventisca",
			"ice storm", "Furtună de gheață (polei)", "Tormenta de hielo",
			"winter weather", "Vreme de iarnă", "Tiempo invernal",
			"lake effect snow", "Ninsoare de lac", "Nieve por efecto lago",
			"snow squall", "Rafale de ninsoare", "Ráfagas de nieve",
			"wind", "Vânt", "Viento",
			"high wind", "Vânt puternic", "Viento fuerte",
			"extreme wind", "Vânt extrem", "Viento extremo",
			"lake wind", "Vânt pe lac", "Viento en el lago",
			"heat", "Caniculă", "Calor",
			"excessive heat", "Caniculă extremă", "Calor excesivo",
			"extreme heat", "Caniculă extremă", "Calor extremo",
			"extreme cold", "Ger extrem", "Frío extremo",
			"cold weather", "Ger", "Frío",
			"wind chill", "Ger (vânt rece)", "Sensación térmica",
			"freeze", "Îngheț", "Helada",
			"hard freeze", "Îngheț puternic", "Helada fuerte",
			"frost", "Brumă", "Escarcha",
			"dense fog", "Ceață densă", "Niebla densa",
			"freezing fog", "Ceață care depune chiciură", "Niebla helada",
			"dense smoke", "Fum dens", "Humo denso",
			"red flag", "Risc mare de incendiu", "Bandera roja (riesgo de incendio)",
			"fire weather", "Risc de incendiu", "Riesgo de incendio",
			"fire", "Incendiu", "Incendio",
			"dust storm", "Furtună de praf", "Tormenta de polvo",
			"blowing dust", "Praf ridicat de vânt", "Polvo en suspensión",
			"air quality", "Calitatea aerului", "Calidad del aire",
			"air stagnation", "Aer stagnant", "Estancamiento del aire",
			"hurricane", "Uragan", "Huracán",
			"hurricane force wind", "Vânt de forța uraganului", "Vientos con fuerza de huracán",
			"tropical storm", "Furtună tropicală", "Tormenta tropical",
			"storm surge", "Maree de furtună", "Marejada ciclónica",
			"storm", "Furtună", "Tormenta",
			"gale", "Vânt foarte puternic (larg)", "Temporal",
			"small craft", "Ambarcațiuni mici", "Embarcaciones pequeñas",
			"rip current", "Curenți de rupere", "Corrientes de resaca",
			"beach hazards", "Pericole pe plajă", "Peligros en la playa",
			"high surf", "Valuri mari", "Oleaje alto",
			"tsunami", "Tsunami", "Tsunami",
			"avalanche", "Avalanșe", "Avalanchas",
			"freezing rain", "Ploaie care îngheață", "Lluvia helada",
			"freezing spray", "Stropi care îngheață", "Rocío helado",
			"hydrologic", "Hidrologic", "Hidrológico",
			"special marine", "Marin special", "Marino especial",
			"earthquake", "Cutremur", "Terremoto",
			"civil emergency", "Urgență civilă", "Emergencia civil",
			"child abduction", "Răpire de copil", "Secuestro de menor",
			"911 telephone outage", "Linia 911 nu funcționează", "Interrupción del 911" };
}
