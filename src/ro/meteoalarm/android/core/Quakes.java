package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Earthquakes from EMSC (seismicportal.eu, FDSN event service, GeoJSON): the ones of the last 24 h
 * of magnitude 3 or more in the phone's country (EMSC's Flynn region names the country, e.g.
 * "ROMANIA", "CRETE, GREECE"). For the town shown, how strongly it was likely felt there: the
 * intensity estimated from the magnitude and the distance to the focus (EMSC does not publish the
 * felt reports of one town, so the estimate is always called an estimate).
 */
public final class Quakes {
	public static final double MIN_MAG = 3.0;
	public static final long WINDOW_MS = 24 * 3600000L;

	public static final class Quake {
		public double mag, lat, lon, depth, dist;
		public String magType = "", region = "", id = "";
		public long time;
		/** Estimated intensity in the town (EMS-98, 1–12), below 2: not felt. */
		public double felt;
		/**
		 * v2.030 (Viorel, quake of 2026-11-18): who gave the values (EMSC, INFP, USGS), when the
		 * source last changed them (ms, 0 unknown), another id the same quake had (INFP before EMSC
		 * had it), and the first value the app saw (NaN when it never changed).
		 */
		public String src = "EMSC", altId = "";
		public long updated;
		public double firstMag = Double.NaN;
		public String firstSrc = "";
		public long firstAt, changedAt;
	}

	// --- v2.030: INFP (Institutul Național de Fizica Pământului), the official values for Romania ---

	/** INFP's FDSN event service (SeisComP), text format: the quakes since SINCE, magnitude 2.5 and over. */
	public static String infpUrl(long since) {
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
		f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
		return "https://eida-sc3.infp.ro/fdsnws/event/1/query?format=text&orderby=time&limit=300&minmagnitude=2.5&starttime="
				+ f.format(new java.util.Date(since));
	}

	/** Romania with a little room (INFP's own quakes there count even before EMSC has them). */
	static boolean inRomania(double la, double lo) {
		return la > 43.6 && la < 48.3 && lo > 20.2 && lo < 29.8;
	}

	/**
	 * INFP's text answer (EventID|Time|Latitude|Longitude|Depth/km|Author|Catalog|Contributor|
	 * ContributorID|MagType|Magnitude|MagAuthor|EventLocationName) merged into QS: a quake both have
	 * (within 90 s and 150 km) takes INFP's magnitude, place and depth; one only INFP has, in
	 * Romania, of magnitude 3 or more in the last WINDOW ms, is added. Distances and felt intensity
	 * are worked out again for the town at LAT LON. True when INFP had anything usable.
	 */
	public static boolean mergeInfp(List<Quake> qs, String text, double lat, double lon, long now, long window) {
		if (text == null || text.trim().length() == 0) return false;
		List<Quake> inf = new ArrayList<Quake>();
		for (String line : text.split("\n")) {
			if (line.startsWith("#") || line.trim().length() == 0) continue;
			String[] c = line.split("\\|", -1);
			if (c.length < 11) continue;
			Quake n = new Quake();
			try {
				String id = c[0].trim().replaceAll("[^A-Za-z0-9]+", "");
				n.id = id.startsWith("infp") ? id : "infp" + id;
				n.time = Warnings.epoch(c[1].trim());
				n.lat = Double.parseDouble(c[2].trim());
				n.lon = Double.parseDouble(c[3].trim());
				n.depth = c[4].trim().length() > 0 ? Math.abs(Double.parseDouble(c[4].trim())) : 10;
				n.magType = c[9].trim();
				n.mag = Double.parseDouble(c[10].trim());
			} catch (Exception e) {
				continue;
			}
			if (Double.isNaN(n.mag) || n.time <= 0 || now - n.time > window || n.time - now > 600000L) continue;
			n.region = c.length > 12 && c[12].trim().length() > 0 ? c[12].trim() : "ROMANIA";
			n.src = "INFP";
			inf.add(n);
		}
		if (inf.isEmpty()) return false;
		// Each EMSC quake takes the closest INFP one (in time, then place); each INFP one is used once.
		boolean[] used = new boolean[inf.size()];
		for (Quake q : qs) {
			int best = -1;
			double bs = Double.MAX_VALUE;
			for (int k = 0; k < inf.size(); k++) {
				if (used[k]) continue;
				Quake n = inf.get(k);
				double dt = Math.abs(q.time - n.time) / 1000.0, dk = km(q.lat, q.lon, n.lat, n.lon);
				if (dt > 90 || dk > 150) continue;
				double sc = dt + dk / 5 + Math.abs(q.mag - n.mag) * 20;
				if (sc < bs) {
					bs = sc;
					best = k;
				}
			}
			if (best < 0) continue;
			used[best] = true;
			Quake n = inf.get(best);
			q.mag = n.mag;
			q.magType = n.magType;
			q.lat = n.lat;
			q.lon = n.lon;
			q.depth = n.depth;
			q.time = n.time;
			q.src = "INFP";
			q.altId = n.id;
			q.updated = 0;
			q.dist = km(lat, lon, q.lat, q.lon);
			q.felt = intensity(q.mag, Math.sqrt(q.dist * q.dist + q.depth * q.depth));
		}
		for (int k = 0; k < inf.size(); k++) {
			Quake n = inf.get(k);
			if (used[k] || n.mag < MIN_MAG || !inRomania(n.lat, n.lon)) continue;
			// INFP names the country: a quake just over the border stays out (as with EMSC).
			if (!Warnings.fold(n.region).toUpperCase(Locale.US).contains("ROMANIA")) continue;
			n.dist = km(lat, lon, n.lat, n.lon);
			n.felt = intensity(n.mag, Math.sqrt(n.dist * n.dist + n.depth * n.depth));
			qs.add(n);
		}
		java.util.Collections.sort(qs, new java.util.Comparator<Quake>() {
			public int compare(Quake a, Quake b) {
				return a.time > b.time ? -1 : a.time < b.time ? 1 : 0;
			}
		});
		return true;
	}

	/**
	 * v2.030: how far (km from the epicentre) the estimated intensity is LEVEL or more for magnitude
	 * M at DEPTH km (intensity() rounded, so its border is LEVEL − 0.5); 0 when nowhere.
	 */
	public static double ringKm(double m, double depth, int level) {
		double r = Math.pow(10, (1.5 * m + 3 - (level - 0.5)) / 3);
		return r <= depth ? 0 : Math.sqrt(r * r - depth * depth);
	}

	/** v2.030: the Vrancea seismic zone (deep quakes under the bend of the Carpathians). */
	public static boolean vrancea(Quake q) {
		return q.lat > 45.3 && q.lat < 46.2 && q.lon > 26.0 && q.lon < 27.2 && q.depth >= 50;
	}

	/** The query for quakes since SINCE (epoch ms) within about 9° of LAT LON. */
	public static String url(double lat, double lon, long since) {
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
		f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
		return "https://www.seismicportal.eu/fdsnws/event/1/query?format=json&limit=500&orderby=time&minmag=" + MIN_MAG
				+ "&start=" + f.format(new java.util.Date(since))
				+ "&minlat=" + Weather.fmt(lat - 9) + "&maxlat=" + Weather.fmt(lat + 9)
				+ "&minlon=" + Weather.fmt(lon - 12) + "&maxlon=" + Weather.fmt(lon + 12);
	}

	/** Kilometres between two points. */
	public static double km(double la1, double lo1, double la2, double lo2) {
		double r = Math.PI / 180, dla = (la2 - la1) * r, dlo = (lo2 - lo1) * r;
		double a = Math.sin(dla / 2) * Math.sin(dla / 2) + Math.cos(la1 * r) * Math.cos(la2 * r) * Math.sin(dlo / 2) * Math.sin(dlo / 2);
		return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}

	/**
	 * Estimated intensity at a hypocentral distance of R km for magnitude M (a usual attenuation for
	 * the Carpathian and Balkan region; Vrancea 1977, M7.4, gives about VII–VIII in Bucharest).
	 */
	public static double intensity(double m, double r) {
		return 1.5 * m - 3.0 * Math.log10(Math.max(5, r)) + 3.0;
	}

	/**
	 * The quakes of the last 24 h in the country named COUNTRY_EN (English, any case), newest first,
	 * with the distance and the estimated intensity for the town at LAT LON.
	 */
	public static List<Quake> parse(String json, String countryEn, double lat, double lon, long now) {
		return parse(json, countryEn == null ? new String[0] : new String[] { countryEn }, lat, lon, now, WINDOW_MS);
	}

	/**
	 * The quakes of the last WINDOW ms whose EMSC region names one of NAMES (English; none = all),
	 * newest first, with the distance and the estimated intensity for the town at LAT LON.
	 */
	public static List<Quake> parse(String json, String[] names, double lat, double lon, long now, long window) {
		List<Quake> list = new ArrayList<Quake>();
		if (json == null || json.trim().length() == 0) return list;
		String[] keys = new String[names.length];
		for (int k = 0; k < names.length; k++) keys[k] = Warnings.fold(names[k]).toUpperCase(Locale.US);
		Object root = Json.parse(json);
		@SuppressWarnings("unchecked")
		List<Object> fs = root instanceof Map ? Json.arr((Map<String, Object>) root, "features") : new ArrayList<Object>();
		for (Object o : fs) {
			Map<String, Object> p = Json.obj(o, "properties");
			if (p == null) continue;
			Quake q = new Quake();
			q.mag = Json.num(p, "mag");
			q.lat = Json.num(p, "lat");
			q.lon = Json.num(p, "lon");
			q.depth = Math.abs(Json.num(p, "depth"));
			Object rg = p.get("flynn_region"), mt = p.get("magtype"), t = p.get("time"), id = p.get("unid"), up = p.get("lastupdate");
			q.updated = up instanceof String ? Warnings.epoch((String) up) : 0;
			q.region = rg instanceof String ? (String) rg : "";
			q.magType = mt instanceof String ? (String) mt : "";
			q.id = id instanceof String ? (String) id : "";
			q.time = t instanceof String ? Warnings.epoch((String) t) : 0;
			if (Double.isNaN(q.mag) || Double.isNaN(q.lat) || Double.isNaN(q.lon) || q.mag < MIN_MAG) continue;
			if (q.time <= 0 || now - q.time > window || q.time - now > 600000L) continue;
			if (keys.length > 0) {
				String rg2 = Warnings.fold(q.region).toUpperCase(Locale.US);
				boolean hit = false;
				for (String k : keys) hit |= k.length() > 0 && rg2.contains(k);
				if (!hit) continue;
			}
			if (Double.isNaN(q.depth)) q.depth = 10;
			q.dist = km(lat, lon, q.lat, q.lon);
			q.felt = intensity(q.mag, Math.sqrt(q.dist * q.dist + q.depth * q.depth));
			list.add(q);
		}
		java.util.Collections.sort(list, new java.util.Comparator<Quake>() {
			public int compare(Quake a, Quake b) {
				return a.time > b.time ? -1 : a.time < b.time ? 1 : 0;
			}
		});
		return list;
	}

	/** EU countries, Ukraine and Moldova as EMSC names them (and the European seas and ranges between them). */
	public static final String[] EUROPE = { "AUSTRIA", "BELGIUM", "BULGARIA", "CROATIA", "CYPRUS", "CZECH", "DENMARK", "ESTONIA", "FINLAND",
			"FRANCE", "GERMANY", "GREECE", "HUNGARY", "IRELAND", "ITALY", "LATVIA", "LITHUANIA", "LUXEMBOURG", "MALTA", "NETHERLANDS",
			"POLAND", "PORTUGAL", "ROMANIA", "SLOVAKIA", "SLOVENIA", "SPAIN", "SWEDEN", "UKRAINE", "MOLDOVA", "SICILY", "SARDINIA",
			"CORSICA", "CRETE", "DODECANESE", "AZORES", "CANARY", "BALEARIC", "MADEIRA", "PYRENEES", "ADRIATIC", "IONIAN", "AEGEAN",
			"TYRRHENIAN", "LIGURIAN", "BALTIC", "BLACK SEA" };

	/** The query for Europe (the EU, Ukraine, Moldova and around) since SINCE. */
	public static String europeUrl(long since) {
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
		f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
		return "https://www.seismicportal.eu/fdsnws/event/1/query?format=json&limit=1000&orderby=time&minmag=" + MIN_MAG
				+ "&start=" + f.format(new java.util.Date(since)) + "&minlat=27&maxlat=71&minlon=-32&maxlon=41";
	}

	// --- United States: USGS (v2.30) ------------------------------------------------------

	/** Magnitude from which USGS quakes are shown in the US (Oklahoma has many small ones). */
	public static final double US_MIN_MAG = 2.5;
	/** Radius around a US town, km (about 300 miles). */
	public static final double US_RADIUS_KM = 483;

	private static String usgsTime(long since) {
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
		f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
		return f.format(new java.util.Date(since));
	}

	/** USGS quakes since SINCE within US_RADIUS_KM of LAT LON, magnitude US_MIN_MAG and more (GeoJSON). */
	public static String usgsUrl(double lat, double lon, long since) {
		return "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&orderby=time&limit=500&minmagnitude=" + US_MIN_MAG
				+ "&starttime=" + usgsTime(since) + "&latitude=" + Weather.fmt(lat) + "&longitude=" + Weather.fmt(lon)
				+ "&maxradiuskm=" + Math.round(US_RADIUS_KM);
	}

	/** USGS quakes of magnitude 3 and more since SINCE in and around the United States (Alaska and Hawaii too). */
	public static String usUrl(long since) {
		return "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&orderby=time&limit=1000&minmagnitude=" + MIN_MAG
				+ "&starttime=" + usgsTime(since) + "&minlatitude=17&maxlatitude=72&minlongitude=-180&maxlongitude=-64";
	}

	/**
	 * USGS GeoJSON: the quakes of the last WINDOW ms of magnitude MIN or more, newest first, with the
	 * distance and the estimated intensity for LAT LON. US_ONLY keeps those whose place names a state.
	 */
	public static List<Quake> parseUsgs(String json, double lat, double lon, long now, long window, double min, boolean usOnly) {
		List<Quake> list = new ArrayList<Quake>();
		if (json == null || json.trim().length() == 0 || !json.trim().startsWith("{")) return list;
		Object root = Json.parse(json);
		@SuppressWarnings("unchecked")
		List<Object> fs = root instanceof Map ? Json.arr((Map<String, Object>) root, "features") : new ArrayList<Object>();
		for (Object o : fs) {
			Map<String, Object> p = Json.obj(o, "properties"), g = Json.obj(o, "geometry");
			if (p == null || g == null) continue;
			List<Object> xyz = Json.arr(g, "coordinates");
			if (xyz == null || xyz.size() < 2) continue;
			Quake q = new Quake();
			q.mag = Json.num(p, "mag");
			q.lon = Json.num(xyz, 0);
			q.lat = Json.num(xyz, 1);
			q.depth = xyz.size() > 2 ? Math.abs(Json.num(xyz, 2)) : 10;
			Object pl = p.get("place"), mt = p.get("magType"), t = p.get("time"), id = ((Map<?, ?>) o).get("id");
			q.region = pl instanceof String ? (String) pl : "";
			q.magType = mt instanceof String ? (String) mt : "";
			q.id = id instanceof String ? (String) id : "";
			q.time = t instanceof Number ? ((Number) t).longValue() : 0;
			Object up = p.get("updated");
			q.updated = up instanceof Number ? ((Number) up).longValue() : 0;
			q.src = "USGS";
			if (Double.isNaN(q.mag) || Double.isNaN(q.lat) || Double.isNaN(q.lon) || q.mag < min) continue;
			if (q.time <= 0 || now - q.time > window || q.time - now > 600000L) continue;
			if (usOnly && !namesState(q.region)) continue;
			if (Double.isNaN(q.depth)) q.depth = 10;
			q.dist = km(lat, lon, q.lat, q.lon);
			q.felt = intensity(q.mag, Math.sqrt(q.dist * q.dist + q.depth * q.depth));
			list.add(q);
		}
		java.util.Collections.sort(list, new java.util.Comparator<Quake>() {
			public int compare(Quake a, Quake b) {
				return a.time > b.time ? -1 : a.time < b.time ? 1 : 0;
			}
		});
		return list;
	}

	/** A USGS place in a state: "5 km NE of Prague, Oklahoma", "10 km SW of Ridgecrest, CA". */
	static boolean namesState(String place) {
		if (place == null) return false;
		int c = place.lastIndexOf(", ");
		String tail = c >= 0 ? place.substring(c + 2).trim() : place.trim();
		if (Cities.stateCode(tail).length() > 0) return true;
		for (int k = 0; k < Cities.STATES.length; k += 2) if (place.contains(Cities.STATES[k])) return true;
		return false;
	}

	/** The one shown in the box: the strongest of the last 24 h (the newest of equal ones). */
	public static Quake main(List<Quake> qs) {
		Quake best = null;
		if (qs != null) for (Quake q : qs) if (best == null || q.mag > best.mag + 0.05) best = q;
		return best;
	}
}
