package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Farmer mode in the US (v3.10, Viorel 2026-11-08): what the US services add to Open-Meteo for
 * the place shown. The National Weather Service forecast grid (api.weather.gov, 2.5 km: highs,
 * lows, chance and amount of rain, thunder, lightning, the wind 20 ft up, the grassland fire
 * danger, the red-flag threat), the US Drought Monitor (drought level D0–D4 at the point, the
 * map of every Thursday) and the hail and wind reports of the NOAA Storm Prediction Center
 * (today's and yesterday's, near the place).
 */
public final class UsFarm {
	private UsFarm() {
	}

	/** The NWS grid layers read (their names in the NWS answer). */
	public static final String[] LAYERS = { "maxTemperature", "minTemperature", "probabilityOfPrecipitation", "quantitativePrecipitation",
			"probabilityOfThunder", "lightningActivityLevel", "twentyFootWindSpeed", "windGust", "grasslandFireDangerIndex", "redFlagThreatIndex" };

	public static final String HAIL_TODAY = "https://www.spc.noaa.gov/climo/reports/today_hail.csv",
			HAIL_YESTERDAY = "https://www.spc.noaa.gov/climo/reports/yesterday_hail.csv",
			WIND_TODAY = "https://www.spc.noaa.gov/climo/reports/today_wind.csv",
			WIND_YESTERDAY = "https://www.spc.noaa.gov/climo/reports/yesterday_wind.csv";

	/** The reports counted within this distance (km) of the place. */
	public static final double NEAR_KM = 50;

	public static final class Data {
		/** Per layer: rows { start ms, end ms, value } (°C, km/h, mm, %). Empty when NWS did not answer. */
		public Map<String, double[][]> grid = new HashMap<String, double[][]>();
		public long gridAt;
		/** The drought level at the place: -2 unknown, -1 none, 0 D0 … 4 D4; the map's date. */
		public int drought = -2;
		public String droughtDate = "";
		public long droughtAt;
		/** Hail and damaging wind reports of today and yesterday (SPC), the nearest first. */
		public List<Tornadoes.Report> hail = new ArrayList<Tornadoes.Report>(), wind = new ArrayList<Tornadoes.Report>();
		public boolean reportsOk;
		public long reportsAt;

		public boolean hasGrid() {
			return !grid.isEmpty();
		}

		/** The value of LAYER at time T (ms), NaN when none. */
		public double at(String layer, long t) {
			double[][] s = grid.get(layer);
			if (s == null) return Double.NaN;
			for (double[] r : s) if (t >= r[0] && t < r[1]) return r[2];
			return Double.NaN;
		}

		/** The highest value of LAYER between A and B (ms), NaN when none. */
		public double max(String layer, long a, long b) {
			double[][] s = grid.get(layer);
			double m = Double.NaN;
			if (s == null) return m;
			for (double[] r : s) if (r[1] > a && r[0] < b && !Double.isNaN(r[2]) && (Double.isNaN(m) || r[2] > m)) m = r[2];
			return m;
		}

		/** The sum of LAYER (amounts, e.g. mm) for the intervals starting between A and B. */
		public double sum(String layer, long a, long b) {
			double[][] s = grid.get(layer);
			if (s == null) return Double.NaN;
			double m = 0;
			boolean any = false;
			for (double[] r : s) if (r[0] >= a && r[0] < b && !Double.isNaN(r[2])) {
				m += r[2];
				any = true;
			}
			return any ? m : Double.NaN;
		}

		/** The first interval of LAYER whose start is at or after T: { start, end, value }, or null. */
		public double[] from(String layer, long t) {
			double[][] s = grid.get(layer);
			if (s == null) return null;
			for (double[] r : s) if (r[1] > t) return r;
			return null;
		}
	}

	/** The NWS answer for a point: it names the forecast grid (forecastGridData). */
	public static String pointsUrl(double lat, double lon) {
		return "https://api.weather.gov/points/" + Weather.fmt(Math.round(lat * 10000) / 10000.0) + "," + Weather.fmt(Math.round(lon * 10000) / 10000.0);
	}

	/** The grid's address in a points answer, null when missing. */
	public static String gridUrl(String pointsJson) {
		Object root = Json.parse(pointsJson);
		return Json.str1(Json.obj(root, "properties"), "forecastGridData");
	}

	/** The layers of a forecast grid answer, in °C, km/h, mm and %. */
	public static Map<String, double[][]> parseGrid(String json) {
		Map<String, double[][]> out = new HashMap<String, double[][]>();
		Map<String, Object> p = Json.obj(Json.parse(json), "properties");
		if (p == null) throw new IllegalArgumentException("no properties");
		for (String name : LAYERS) {
			Map<String, Object> l = Json.obj(p, name);
			if (l == null) continue;
			String uom = Json.str1(l, "uom");
			List<Object> vs = Json.arr(l, "values");
			List<double[]> rows = new ArrayList<double[]>();
			for (Object o : vs) {
				if (!(o instanceof Map)) continue;
				@SuppressWarnings("unchecked")
				Map<String, Object> v = (Map<String, Object>) o;
				long[] iv = interval(Json.str1(v, "validTime"));
				if (iv == null) continue;
				rows.add(new double[] { iv[0], iv[1], convert(Json.num(v, "value"), uom) });
			}
			out.put(name, rows.toArray(new double[rows.size()][]));
		}
		return out;
	}

	/** A value in UOM ("wmoUnit:degF", "wmoUnit:m_s-1", ...) in °C, km/h or mm. */
	static double convert(double v, String uom) {
		if (Double.isNaN(v) || uom == null) return v;
		if (uom.endsWith("degF")) return (v - 32) * 5 / 9;
		if (uom.endsWith("m_s-1")) return v * 3.6;
		if (uom.endsWith("mi_h-1") || uom.endsWith("mph")) return v * 1.609344;
		if (uom.endsWith("kn")) return v * 1.852;
		if (uom.endsWith(":in")) return v * 25.4;
		if (uom.endsWith(":cm")) return v * 10;
		return v;
	}

	/** "2026-11-08T12:00:00+00:00/PT3H" (or P1DT6H) as { start ms, end ms }; null when unreadable. */
	public static long[] interval(String s) {
		if (s == null || s.length() < 20) return null;
		try {
			int slash = s.indexOf('/');
			String t = slash > 0 ? s.substring(0, slash) : s, d = slash > 0 ? s.substring(slash + 1) : "PT1H";
			Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
			c.clear();
			c.set(Integer.parseInt(t.substring(0, 4)), Integer.parseInt(t.substring(5, 7)) - 1, Integer.parseInt(t.substring(8, 10)),
					Integer.parseInt(t.substring(11, 13)), Integer.parseInt(t.substring(14, 16)), 0);
			long ms = c.getTimeInMillis();
			String off = t.length() > 19 ? t.substring(19) : "Z";
			if (off.length() >= 6 && (off.charAt(0) == '+' || off.charAt(0) == '-')) {
				long o = (Integer.parseInt(off.substring(1, 3)) * 60L + Integer.parseInt(off.substring(4, 6))) * 60000L;
				ms -= off.charAt(0) == '+' ? o : -o;
			}
			long len = 0;
			int days = d.indexOf('D'), tt = d.indexOf('T');
			if (days > 0) len += Long.parseLong(d.substring(1, days)) * 86400000L;
			if (tt >= 0) {
				String h = d.substring(tt + 1);
				int hi = h.indexOf('H');
				if (hi > 0) len += Long.parseLong(h.substring(0, hi)) * 3600000L;
				int mi = h.indexOf('M');
				if (mi > 0) len += Long.parseLong(h.substring(hi > 0 ? hi + 1 : 0, mi)) * 60000L;
			}
			if (len <= 0) len = 3600000L;
			return new long[] { ms, ms + len };
		} catch (Exception e) {
			return null;
		}
	}

	/** The US Drought Monitor map of this week (ArcGIS), asked for the areas that hold the point. */
	public static String droughtUrl(double lat, double lon) {
		return "https://services5.arcgis.com/0OTVzJS4K09zlixn/ArcGIS/rest/services/USDM_current/FeatureServer/0/query?geometry="
				+ Weather.fmt(lon) + "," + Weather.fmt(lat)
				+ "&geometryType=esriGeometryPoint&inSR=4326&spatialRel=esriSpatialRelIntersects&outFields=*&returnGeometry=false&f=json";
	}

	/** { level (-1 none, 0–4), the map's date as epoch ms or 0 } from a drought answer. */
	@SuppressWarnings("unchecked")
	public static long[] parseDrought(String json) {
		Object root = Json.parse(json);
		if (!(root instanceof Map) || ((Map<String, Object>) root).containsKey("error")) throw new IllegalArgumentException("drought: error");
		if (!((Map<String, Object>) root).containsKey("features")) throw new IllegalArgumentException("drought: no features");
		int best = -1;
		long date = 0;
		for (Object f : Json.arr((Map<String, Object>) root, "features")) {
			Map<String, Object> a = Json.obj(f, "attributes");
			if (a == null) continue;
			for (Map.Entry<String, Object> e : a.entrySet()) {
				String k = e.getKey().toLowerCase();
				Object v = e.getValue();
				if ((k.equals("dm") || k.equals("dm_class") || k.equals("drought")) && v instanceof Double) best = Math.max(best, (int) Math.round((Double) v));
				else if ((k.equals("dm") || k.equals("drought")) && v instanceof String && ((String) v).matches("D?[0-4]"))
					best = Math.max(best, ((String) v).charAt(((String) v).length() - 1) - '0');
				else if (k.contains("date") && v instanceof Double && (Double) v > 1e11) date = Math.max(date, (long) (double) (Double) v);
			}
		}
		return new long[] { best, date };
	}

	/** The reports of an SPC CSV within NEAR_KM of LAT LON. */
	public static List<Tornadoes.Report> near(String csv, long start, double lat, double lon) {
		List<Tornadoes.Report> out = new ArrayList<Tornadoes.Report>();
		for (Tornadoes.Report r : Tornadoes.parse(csv, start, lat, lon)) if (r.dist <= NEAR_KM) out.add(r);
		return out;
	}

	/** An SPC hail size ("175" = 1.75 in) in inches, NaN when not a number. */
	public static double hailInches(String size) {
		try {
			return Integer.parseInt(size.trim()) / 100.0;
		} catch (Exception e) {
			return Double.NaN;
		}
	}
}
