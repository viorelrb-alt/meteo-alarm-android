package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * v2.001 (Viorel 2026-11-15/17): the farmer's statistics, from the answers already downloaded:
 * the spray window (score 0–100 and Delta-T, Stull 2011), the leaf wetness and the fungal risk,
 * the growing degree days per crop from the sowing date, the water balance, the soil, the frost /
 * heat / wind risks and "the action of the day". Formulas as in Viorel's strategy file.
 */
public final class Agro {
	private Agro() {
	}

	// --- Crops ----------------------------------------------------------------------------------

	/** The crop types (for the thresholds): winter cereals, spring row crops, vegetables, orchard / vine, forage. */
	public static final int WINTER = 0, SPRING = 1, VEG = 2, TREES = 3, FORAGE = 4;

	public static String typeName(int t) {
		switch (t) {
		case WINTER:
			return Tr.t("Winter cereals", "Păioase de toamnă");
		case SPRING:
			return Tr.t("Spring row crops", "Prășitoare de primăvară");
		case VEG:
			return Tr.t("Vegetables", "Legume");
		case TREES:
			return Tr.t("Orchard / vine", "Pomi / vie");
		case FORAGE:
			return Tr.t("Forage", "Furaje");
		default:
			return "";
		}
	}

	/** One crop of the list. */
	public static final class Crop {
		public final String key, en, ro;
		public final int type;
		/** GDD base and ceiling (°C), sowing months (1–12, FROM..TO, 0 = planted, no season), the soil's sowing threshold (°C). */
		public final double base, cap, soilMin;
		public final int from, to;
		/** The disease watched and the heat-critical stage (EN, RO). */
		final String disEn, disRo, heatEn, heatRo;

		Crop(String key, String en, String ro, int type, double base, int from, int to, double soilMin, String disEn, String disRo, String heatEn,
				String heatRo) {
			this.key = key;
			this.en = en;
			this.ro = ro;
			this.type = type;
			this.base = base;
			this.cap = 30;
			this.from = from;
			this.to = to;
			this.soilMin = soilMin;
			this.disEn = disEn;
			this.disRo = disRo;
			this.heatEn = heatEn;
			this.heatRo = heatRo;
		}

		public String name() {
			return Tr.t(en, ro);
		}

		public String disease() {
			return Tr.t(disEn, disRo);
		}

		public String heatStage() {
			return Tr.t(heatEn, heatRo);
		}

		/** Sown in MONTH (1–12) is in season; planted crops always are. */
		public boolean fits(int month) {
			if (from == 0) return true;
			return from <= to ? month >= from && month <= to : month >= from || month <= to;
		}
	}

	public static final Crop[] CROPS = {
			new Crop("wheat", "Wheat", "Grâu", WINTER, 0, 9, 10, 5, "Septoria", "Septorioză", "grain filling", "umplerea bobului"),
			new Crop("barley", "Barley", "Orz", WINTER, 0, 9, 10, 5, "Net blotch", "Pătarea în rețea", "grain filling", "umplerea bobului"),
			new Crop("triticale", "Triticale", "Triticale", WINTER, 0, 10, 10, 5, "Septoria", "Septorioză", "grain filling", "umplerea bobului"),
			new Crop("rapeseed", "Rapeseed", "Rapiță", WINTER, 0, 8, 9, 5, "Phoma", "Phoma", "flowering", "înflorire"),
			new Crop("maize", "Maize", "Porumb", SPRING, 10, 4, 5, 10, "Fusarium", "Fuzarioză", "pollination", "polenizare"),
			new Crop("sunflower", "Sunflower", "Floarea-soarelui", SPRING, 10, 3, 4, 8, "Downy mildew", "Mană", "flowering", "înflorire"),
			new Crop("soy", "Soybean", "Soia", SPRING, 10, 4, 5, 10, "Downy mildew", "Mană", "flowering", "înflorire"),
			new Crop("beet", "Sugar beet", "Sfeclă de zahăr", SPRING, 5, 3, 4, 6, "Cercospora", "Cercosporioză", "root growth", "creșterea rădăcinii"),
			new Crop("potato", "Potato", "Cartof", VEG, 7, 3, 4, 7, "Late blight", "Mană", "tuber forming", "formarea tuberculilor"),
			new Crop("veg", "Vegetables", "Legume", VEG, 10, 3, 7, 10, "Downy mildew", "Mană", "flowering", "înflorire"),
			new Crop("vine", "Vineyard", "Viță-de-vie", TREES, 10, 0, 0, 10, "Downy mildew", "Mană", "flowering", "înflorire"),
			new Crop("orchard", "Orchard", "Livadă", TREES, 10, 0, 0, 10, "Scab", "Rapăn", "flowering", "înflorire"),
			new Crop("alfalfa", "Alfalfa", "Lucernă", FORAGE, 5, 3, 4, 5, "Anthracnose", "Antracnoză", "regrowth", "refacere") };

	/** The crop of KEY (null for another crop, written by the farmer). */
	public static Crop crop(String key) {
		if (key == null) return null;
		for (Crop c : CROPS) if (c.key.equals(key)) return c;
		return null;
	}

	/** A crop of the farmer's own (name NAME, type TYPE): the thresholds of its type. */
	public static Crop own(String name, int type) {
		switch (type) {
		case WINTER:
			return new Crop("*", name, name, WINTER, 0, 9, 10, 5, "Fungal diseases", "Boli fungice", "grain filling", "umplerea bobului");
		case VEG:
			return new Crop("*", name, name, VEG, 10, 3, 7, 10, "Downy mildew", "Mană", "flowering", "înflorire");
		case TREES:
			return new Crop("*", name, name, TREES, 10, 0, 0, 10, "Fungal diseases", "Boli fungice", "flowering", "înflorire");
		case FORAGE:
			return new Crop("*", name, name, FORAGE, 5, 3, 4, 5, "Fungal diseases", "Boli fungice", "regrowth", "refacere");
		default:
			return new Crop("*", name, name, SPRING, 10, 4, 5, 10, "Fungal diseases", "Boli fungice", "flowering", "înflorire");
		}
	}

	/** The growth stages of a crop type: { GDD } and their names. */
	public static double[] stageGdd(int type) {
		switch (type) {
		case WINTER:
			return new double[] { 150, 300, 450, 1100, 1400, 2000 };
		case VEG:
			return new double[] { 60, 300, 600, 1000 };
		case TREES:
			return new double[] { 50, 250, 450, 1100, 1500 };
		case FORAGE:
			return new double[] { 100, 300, 600, 750 };
		default:
			return new double[] { 80, 250, 450, 750, 1400 };
		}
	}

	public static String[] stageNames(int type) {
		switch (type) {
		case WINTER:
			return new String[] { Tr.t("Emergence", "Răsărire"), Tr.t("3 leaves", "3 frunze"), Tr.t("Tillering", "Înfrățire"),
					Tr.t("Stem elongation", "Alungire"), Tr.t("Heading", "Înspicare"), Tr.t("Ripening", "Coacere") };
		case VEG:
			return new String[] { Tr.t("Emergence", "Răsărire"), Tr.t("Growth", "Creștere"), Tr.t("Flowering", "Înflorire"), Tr.t("Harvest", "Recoltă") };
		case TREES:
			return new String[] { Tr.t("Bud break", "Dezmugurire"), Tr.t("Flowering", "Înflorire"), Tr.t("Fruit set", "Legare"),
					Tr.t("Veraison", "Pârgă"), Tr.t("Ripening", "Coacere") };
		case FORAGE:
			return new String[] { Tr.t("Emergence", "Răsărire"), Tr.t("Branching", "Ramificare"), Tr.t("Budding", "Îmbobocire"), Tr.t("Flowering", "Înflorire") };
		default:
			return new String[] { Tr.t("Emergence", "Răsărire"), Tr.t("4–6 leaves", "4–6 frunze"), Tr.t("8–10 leaves", "8–10 frunze"),
					Tr.t("Flowering", "Înflorire"), Tr.t("Ripening", "Coacere") };
		}
	}

	/** The stage reached with SUM GDD: -1 none yet, else the last one passed. */
	public static int stage(int type, double sum) {
		double[] g = stageGdd(type);
		int s = -1;
		for (int k = 0; k < g.length; k++) if (sum >= g[k]) s = k;
		return s;
	}

	// --- Spraying -------------------------------------------------------------------------------

	/** The wet-bulb temperature (°C) of air at T °C and RH % (Stull 2011). */
	public static double wetBulb(double t, double rh) {
		return t * Math.atan(0.151977 * Math.sqrt(rh + 8.313659)) + Math.atan(t + rh) - Math.atan(rh - 1.676331)
				+ 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh) - 4.686035;
	}

	/** Delta-T (°C): the dry minus the wet bulb; NaN when unknown. */
	public static double deltaT(double t, double rh) {
		if (Double.isNaN(t) || Double.isNaN(rh)) return Double.NaN;
		rh = Math.max(5, Math.min(99, rh));
		return Math.max(0, t - wetBulb(t, rh));
	}

	/** The dew point (°C, Magnus). */
	public static double dew(double t, double rh) {
		if (Double.isNaN(t) || Double.isNaN(rh) || rh <= 0) return Double.NaN;
		double a = 17.62, b = 243.12, g = Math.log(rh / 100) + a * t / (b + t);
		return b * g / (a - g);
	}

	/** Delta-T of hour I. */
	public static double deltaT(Farm.Data d, int i) {
		return d.has(i) ? deltaT(d.temp[i], d.hum[i]) : Double.NaN;
	}

	/** Why hour I is not for spraying: 0 it is; 1 rain; 2 wind; 3 too cold; 4 too hot; 5 Delta-T low; 6 Delta-T high; -1 unknown. */
	public static int block(Farm.Data d, int i) {
		if (!d.has(i) || Double.isNaN(d.temp[i]) || Double.isNaN(d.wind[i])) return -1;
		double sum = 0;
		for (int j = i + 1; j <= i + 3 && j < d.size(); j++) sum += nz(d.precip[j]);
		// v2.021: the same rain and wind limits as the operational status (Farm.spray), so the two agree.
		int fs = Farm.spray(d, i);
		if (nz(d.precip[i]) > 0.2 || sum > 1.0 || fs == 3) return 1;
		if (fs == 2 || d.wind[i] > 15 || nz(d.gust[i]) > 25) return 2;
		if (d.temp[i] < 5) return 3;
		if (d.temp[i] > 28) return 4;
		double dt = deltaT(d, i);
		if (!Double.isNaN(dt) && dt < 1.5) return 5;
		if (!Double.isNaN(dt) && dt > 10.5) return 6;
		return 0;
	}

	/** The spray score (0–100) of hour I; -1 unknown. */
	public static int score(Farm.Data d, int i) {
		int b = block(d, i);
		if (b < 0) return -1;
		if (b > 0) return 0;
		double v = d.wind[i], t = d.temp[i], dt = deltaT(d, i);
		double pv = v >= 3 && v <= 10 ? 0 : Math.abs(v - 6.5) * 4;
		double pd = Double.isNaN(dt) || (dt >= 3 && dt <= 7) ? 0 : Math.abs(dt - 5) * 8;
		double pt = t >= 12 && t <= 22 ? 0 : Math.abs(t - 17) * 2.5;
		int sc = (int) Math.max(0, Math.min(100, Math.round(100 - pv - pd - pt)));
		// v2.010 (Viorel): the verdict never better than the ΔT's own zone.
		if (dtLevel(dt) == 1) sc = Math.min(sc, 69);
		return sc;
	}

	/** The colour level of a score: 0 good (70+), 1 care (30–69), 2 no; -1 unknown. */
	public static int level(int score) {
		return score < 0 ? -1 : score >= 70 ? 0 : score >= 30 ? 1 : 2;
	}

	/**
	 * The Delta-T zone: 0 good (2–8), 1 care (1.5–2 and 8–10.5), 2 no (under 1.5, over 10.5); -1 unknown.
	 * v2.010 (Viorel): the same limits as block(), so the hour's verdict and its ΔT agree.
	 */
	public static int dtLevel(double dt) {
		return Double.isNaN(dt) ? -1 : dt < 1.5 ? 2 : dt < 2 ? 1 : dt <= 8 ? 0 : dt <= 10.5 ? 1 : 2;
	}

	/** The best spray window in the N hours from I: { first hour, the hour after the last, best score }, or null. */
	public static int[] window(Farm.Data d, int i, int n) {
		int[] best = null;
		int a = -1, top = 0;
		long bestVal = -1;
		for (int j = Math.max(0, i); j <= Math.min(d.size(), i + n); j++) {
			int s = j < Math.min(d.size(), i + n) ? score(d, j) : -1;
			boolean ok = level(s) == 0;
			if (ok) {
				if (a < 0) {
					a = j;
					top = 0;
				}
				top = Math.max(top, s);
			} else if (a >= 0) {
				long val = (long) (j - a) * 1000 + top;
				if (val > bestVal) {
					bestVal = val;
					best = new int[] { a, j, top };
				}
				a = -1;
			}
		}
		if (best != null) return best;
		// None good: the longest "with care" stretch.
		a = -1;
		for (int j = Math.max(0, i); j <= Math.min(d.size(), i + n); j++) {
			int s = j < Math.min(d.size(), i + n) ? score(d, j) : -1;
			if (level(s) == 1) {
				if (a < 0) {
					a = j;
					top = 0;
				}
				top = Math.max(top, s);
			} else if (a >= 0) {
				long val = (long) (j - a) * 1000 + top;
				if (val > bestVal) {
					bestVal = val;
					best = new int[] { a, j, top };
				}
				a = -1;
			}
		}
		return best;
	}

	// --- Leaf wetness and fungi -------------------------------------------------------------------

	/** Hour I wets the leaves: rain over 0.1 mm, air within 1.8 °C of its dew point, or humidity 90%+. */
	public static boolean wet(Farm.Data d, int i) {
		if (!d.has(i)) return false;
		if (nz(d.precip[i]) > 0.1) return true;
		if (!Double.isNaN(d.hum[i]) && d.hum[i] >= 90) return true;
		double dp = dew(d.temp[i], d.hum[i]);
		return !Double.isNaN(dp) && d.temp[i] - dp <= 1.8;
	}

	/** Wet and between 12 and 26 °C (the fungi's hours). */
	public static boolean infects(Farm.Data d, int i) {
		return wet(d, i) && !Double.isNaN(d.temp[i]) && d.temp[i] >= 12 && d.temp[i] <= 26;
	}

	/** The longest run of the fungi's hours in the N hours from I: { hours, first, after the last }; hours 0 when none. */
	public static int[] fungalRun(Farm.Data d, int i, int n) {
		int best = 0, bs = -1, be = -1, a = -1;
		int end = Math.min(d.size(), i + n);
		for (int j = Math.max(0, i); j <= end; j++) {
			boolean w = j < end && infects(d, j);
			if (w && a < 0) a = j;
			if (!w && a >= 0) {
				if (j - a > best) {
					best = j - a;
					bs = a;
					be = j;
				}
				a = -1;
			}
		}
		return new int[] { best, bs, be };
	}

	/** The wet hours in a row up to hour CUR (0 when CUR is dry) and, when dry, the hour it dried (-1 none in the last 24). */
	public static int[] wetNow(Farm.Data d, int cur) {
		int n = 0;
		for (int k = cur; k >= 0 && wet(d, k); k--) n++;
		int dried = -1;
		if (n == 0) for (int k = cur - 1; k >= Math.max(0, cur - 24); k--) if (wet(d, k)) {
			dried = k + 1;
			break;
		}
		return new int[] { n, dried };
	}

	/** The fungal risk (0–100 %) of a run of H hours: 18 hours and more = 100. */
	public static int fungalPct(int h) {
		return Math.min(100, Math.round(h * 100f / 18));
	}

	// --- The other risks (72 hours) -------------------------------------------------------------

	/** { lowest soil-surface temperature in the N hours from I, its hour, lowest air temperature }. */
	public static double[] frost(Farm.Data d, int i, int n) {
		double g = Double.NaN, a = Double.NaN;
		int at = -1;
		for (int j = Math.max(0, i); j < Math.min(d.size(), i + n); j++) {
			double s = d.soilT[j];
			if (Double.isNaN(s)) s = Double.isNaN(d.temp[j]) ? Double.NaN : d.temp[j] - 2;
			if (!Double.isNaN(s) && (Double.isNaN(g) || s < g)) {
				g = s;
				at = j;
			}
			if (!Double.isNaN(d.temp[j]) && (Double.isNaN(a) || d.temp[j] < a)) a = d.temp[j];
		}
		return new double[] { g, at, a };
	}

	/** The ground frost risk (%) from the lowest soil-surface temperature: +4 °C none, −2 °C and lower certain. */
	public static int frostPct(double g) {
		if (Double.isNaN(g)) return -1;
		return (int) Math.max(0, Math.min(100, Math.round((4 - g) / 6 * 100)));
	}

	/** The heat stress risk (%) from the highest temperature: 28 °C none, 34 °C and over certain. */
	public static int heatPct(double max) {
		if (Double.isNaN(max)) return -1;
		return (int) Math.max(0, Math.min(100, Math.round((max - 28) / 6 * 100)));
	}

	/** The wind / hail risk (%) from the strongest gust (km/h) and the highest CAPE (J/kg). */
	public static int windPct(double gust, double cape) {
		double a = Double.isNaN(gust) ? 0 : (gust - 20) / 55 * 100, b = Double.isNaN(cape) ? 0 : (cape - 300) / 2200 * 100;
		return (int) Math.max(0, Math.min(100, Math.round(Math.max(a, b))));
	}

	/** { highest temperature in the N hours from I, its hour }. */
	public static double[] maxTemp(Farm.Data d, int i, int n) {
		double m = Double.NaN;
		int at = -1;
		for (int j = Math.max(0, i); j < Math.min(d.size(), i + n); j++)
			if (!Double.isNaN(d.temp[j]) && (Double.isNaN(m) || d.temp[j] > m)) {
				m = d.temp[j];
				at = j;
			}
		return new double[] { m, at };
	}

	/** { strongest gust in the N hours from I, its hour }. */
	public static double[] maxGust(Farm.Data d, int i, int n) {
		double m = Double.NaN;
		int at = -1;
		for (int j = Math.max(0, i); j < Math.min(d.size(), i + n); j++)
			if (!Double.isNaN(d.gust[j]) && (Double.isNaN(m) || d.gust[j] > m)) {
				m = d.gust[j];
				at = j;
			}
		return new double[] { m, at };
	}

	// --- Water and soil ---------------------------------------------------------------------------

	/** ET0 (mm/day) after Hargreaves–Samani, for a day of the year DOY at latitude LAT (when the source has none). */
	public static double hargreaves(double lat, int doy, double tmax, double tmin) {
		if (Double.isNaN(tmax) || Double.isNaN(tmin)) return Double.NaN;
		double phi = Math.toRadians(lat), dr = 1 + 0.033 * Math.cos(2 * Math.PI * doy / 365), dl = 0.409 * Math.sin(2 * Math.PI * doy / 365 - 1.39);
		double ws = Math.acos(Math.max(-1, Math.min(1, -Math.tan(phi) * Math.tan(dl))));
		double ra = 24 * 60 / Math.PI * 0.082 * dr * (ws * Math.sin(phi) * Math.sin(dl) + Math.cos(phi) * Math.cos(dl) * Math.sin(ws));
		return Math.max(0, 0.0023 * ((tmax + tmin) / 2 + 17.8) * Math.sqrt(Math.max(0, tmax - tmin)) * 0.408 * ra);
	}

	/** The day's ET0 (the source's, else Hargreaves). */
	public static double et0(Farm.Data d, int k, double lat) {
		double e = d.dEt0 != null && k < d.dEt0.length ? d.dEt0[k] : Double.NaN;
		if (!Double.isNaN(e)) return e;
		return hargreaves(lat, doy(d.dDate[k]), d.dMax[k], d.dMin[k]);
	}

	/** Machinery on the field: 0 good, 1 care, 2 no (compaction); -1 unknown. From the rain of the last 3 days and the soil moisture. */
	public static int access(double rain3, double moist) {
		if (Double.isNaN(rain3) && Double.isNaN(moist)) return -1;
		if (nz(moist) >= 0.38 || nz(rain3) >= 15) return 2;
		if (nz(moist) >= 0.32 || nz(rain3) >= 6) return 1;
		return 0;
	}

	// --- Growing degree days ----------------------------------------------------------------------

	/** One day's GDD for a crop with base BASE and ceiling CAP. */
	public static double gdd(double tmax, double tmin, double base, double cap) {
		if (Double.isNaN(tmax) || Double.isNaN(tmin)) return Double.NaN;
		double avg = (Math.min(tmax, cap) + Math.max(tmin, base)) / 2;
		return Math.max(0, avg - base);
	}

	/** The season's days: dates with their high / low / rain (history and forecast together), sorted. */
	public static final class Days {
		public final List<String> date = new ArrayList<String>();
		public final List<double[]> v = new ArrayList<double[]>();

		/** Puts in day DATE (high, low, rain); a later put of the same day wins. */
		public void put(String date, double max, double min, double rain) {
			if (date == null || Double.isNaN(max) || Double.isNaN(min)) return;
			int k = java.util.Collections.binarySearch(this.date, date);
			double[] x = { max, min, rain };
			if (k >= 0) v.set(k, x);
			else {
				k = -k - 1;
				this.date.add(k, date);
				v.add(k, x);
			}
		}

		public int index(String d) {
			return java.util.Collections.binarySearch(date, d);
		}

		/** The cumulated GDD from FROM to TO (dates, both in), day by day ({ day number from FROM, sum } for each known day). */
		public List<double[]> cum(String from, String to, double base, double cap) {
			List<double[]> out = new ArrayList<double[]>();
			double s = 0;
			for (int k = 0; k < date.size(); k++) {
				String d = date.get(k);
				if (d.compareTo(from) < 0 || d.compareTo(to) > 0) continue;
				double g = gdd(v.get(k)[0], v.get(k)[1], base, cap);
				if (!Double.isNaN(g)) s += g;
				out.add(new double[] { between(from, d), s });
			}
			return out;
		}
	}

	/** The days from A to B (dates "yyyy-MM-dd"). */
	public static int between(String a, String b) {
		return (int) Math.round((millis(b) - millis(a)) / 86400000.0);
	}

	public static long millis(String iso) {
		Calendar c = Calendar.getInstance();
		c.clear();
		c.set(Integer.parseInt(iso.substring(0, 4)), Integer.parseInt(iso.substring(5, 7)) - 1, Integer.parseInt(iso.substring(8, 10)), 12, 0);
		return c.getTimeInMillis();
	}

	/** DATE plus N days. */
	public static String plus(String iso, int n) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(millis(iso));
		c.add(Calendar.DAY_OF_MONTH, n);
		return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
	}

	public static int doy(String iso) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(millis(iso));
		return c.get(Calendar.DAY_OF_YEAR);
	}

	/** Reads the archive's answer (daily high, low, rain) into DAYS. */
	public static void archive(String json, Days days) {
		Object root = Json.parse(json);
		java.util.Map<String, Object> d = Json.obj(root, "daily");
		if (d == null) return;
		List<Object> t = Json.arr(d, "time"), mx = Json.arr(d, "temperature_2m_max"), mn = Json.arr(d, "temperature_2m_min"), r = Json.arr(d, "precipitation_sum");
		if (t == null) return;
		for (int k = 0; k < t.size(); k++)
			days.put(Json.str(t, k), mx == null ? Double.NaN : Json.num(mx, k), mn == null ? Double.NaN : Json.num(mn, k), r == null ? Double.NaN : Json.num(r, k));
	}

	/** The archive's request (ERA5, from FROM to TO). */
	public static String archiveUrl(double lat, double lon, String from, String to, String tz) throws Exception {
		return "https://archive-api.open-meteo.com/v1/archive?latitude=" + Weather.fmt(lat) + "&longitude=" + Weather.fmt(lon) + "&start_date=" + from
				+ "&end_date=" + to + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum&timezone=" + java.net.URLEncoder.encode(tz, "UTF-8");
	}

	private static double nz(double v) {
		return Double.isNaN(v) ? 0 : v;
	}
}
