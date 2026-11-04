package ro.meteoalarm.android.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The forecast from Open-Meteo (DWD ICON), read the same way as bin/meteoalarmkindle.sh on the
 * Kindle: the current weather with the cloud-cover and wet-hour corrections, 12 hours of
 * precipitation and the next five days.
 */
public final class Weather {
	public static final int LOOKAHEAD_HOURS = 12, TILE_HOURS = 25;
	public static final int RAIN_PROBABILITY = 50;
	public static final int LOW_OVERCAST = 35;
	public static final String MODEL = "icon_seamless";

	public double temp, feels = Double.NaN, wind = Double.NaN, windDir = Double.NaN, precip;
	public int code;
	public boolean isDay = true;
	public double tmax = Double.NaN, tmin = Double.NaN;
	public String sunrise = "", sunset = "";
	/** Tomorrow's sunrise (for the widget after sunset). */
	public String sunrise2 = "";
	public int uv = -1;
	/** From the second request (Open-Meteo's own model mix): relative humidity %, sea-level pressure
	 * hPa and its change over the last 3 hours, visibility in metres; the European air quality index. */
	public int humidity = -1, aqi = -1;
	public double pressure = Double.NaN, pressTrend = Double.NaN, visibility = Double.NaN;

	// Hourly chart.
	public final List<String> hours = new ArrayList<String>();
	public int[] probs = new int[0];
	public boolean[] wet = new boolean[0], snowy = new boolean[0];
	public String rainAt = "", rainEnd = "";
	public int rainMaxP;
	public boolean snowMaj;

	// Hour by hour for the tiles (from the current hour): "HH:MM", temperature, code, day or night.
	public final List<String> tileHours = new ArrayList<String>();
	public double[] tileTemp = new double[0];
	public int[] tileCode = new int[0];
	public boolean[] tileDay = new boolean[0];

	// Next days (tomorrow first): ISO date, code, rounded high/low, max precipitation probability.
	public final List<String> dayDates = new ArrayList<String>();
	public final List<Integer> dayCodes = new ArrayList<Integer>();
	public final List<Double> dayHi = new ArrayList<Double>(), dayLo = new ArrayList<Double>();
	public final List<Integer> dayPop = new ArrayList<Integer>();

	public static String forecastUrl(double lat, double lon, String tzId) throws Exception {
		return forecastUrl(lat, lon, tzId, MODEL);
	}

	/** The forecast URL with MODEL (null or "" = Open-Meteo's own best mix of models). */
	public static String forecastUrl(double lat, double lon, String tzId, String model) throws Exception {
		return "https://api.open-meteo.com/v1/forecast?latitude=" + fmt(lat) + "&longitude=" + fmt(lon)
				+ "&current=temperature_2m,weather_code,precipitation,is_day,cloud_cover,cloud_cover_low,"
				+ "cloud_cover_mid,cloud_cover_high,apparent_temperature,wind_speed_10m,wind_direction_10m"
				+ "&hourly=precipitation_probability,precipitation,weather_code,snowfall,temperature_2m,is_day"
				+ "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,"
				+ "sunrise,sunset,uv_index_max"
				+ "&forecast_days=6&forecast_hours=" + TILE_HOURS
				+ "&timezone=" + URLEncoder.encode(tzId, "UTF-8") + (model == null || model.length() == 0 ? "" : "&models=" + model);
	}

	/**
	 * What the ICON model lacks, from Open-Meteo's own model mix: today's UV index, the humidity,
	 * the pressure (and the last 3 hours of it, for the trend) and the visibility.
	 */
	public static String uvUrl(double lat, double lon, String tzId) throws Exception {
		return "https://api.open-meteo.com/v1/forecast?latitude=" + fmt(lat) + "&longitude=" + fmt(lon)
				+ "&daily=uv_index_max&current=relative_humidity_2m,pressure_msl,visibility&hourly=pressure_msl"
				+ "&past_hours=3&forecast_hours=1&forecast_days=1&timezone=" + URLEncoder.encode(tzId, "UTF-8");
	}

	/** The UV index of a uvUrl answer, -1 without one. */
	public static int parseUv(String text) {
		try {
			double uv = Json.num(Json.arr(Json.obj(Json.parse(text), "daily"), "uv_index_max"), 0);
			return Double.isNaN(uv) ? -1 : (int) Math.round(uv);
		} catch (Exception e) {
			return -1;
		}
	}

	/** Puts a uvUrl answer into W: UV (when W has none), humidity, pressure and its trend, visibility. */
	public static void addExtra(Weather w, String text) {
		try {
			Object root = Json.parse(text);
			if (w.uv < 0) {
				double uv = Json.num(Json.arr(Json.obj(root, "daily"), "uv_index_max"), 0);
				if (!Double.isNaN(uv)) w.uv = (int) Math.round(uv);
			}
			Map<String, Object> cur = Json.obj(root, "current");
			double h = Json.num(cur, "relative_humidity_2m");
			if (!Double.isNaN(h)) w.humidity = (int) Math.round(h);
			w.pressure = Json.num(cur, "pressure_msl");
			w.visibility = Json.num(cur, "visibility");
			List<Object> ps = Json.arr(Json.obj(root, "hourly"), "pressure_msl");
			if (ps != null && !ps.isEmpty() && !Double.isNaN(w.pressure)) {
				double first = Json.num(ps, 0);
				if (!Double.isNaN(first)) w.pressTrend = w.pressure - first;
			}
		} catch (Exception e) {
			// the extra boxes then show a dash
		}
	}

	/** The European air quality index of an air-quality answer, -1 without one. */
	public static int parseAqi(String text) {
		return parseAqi(text, false);
	}

	/** The European air quality index, or the US one (EPA, 0–500) when US (v2.31); -1 without one. */
	public static int parseAqi(String text, boolean us) {
		try {
			double a = Json.num(Json.obj(Json.parse(text), "current"), us ? "us_aqi" : "european_aqi");
			return Double.isNaN(a) ? -1 : (int) Math.round(a);
		} catch (Exception e) {
			return -1;
		}
	}

	public static String pollenUrl(double lat, double lon) {
		return "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + fmt(lat) + "&longitude=" + fmt(lon)
				+ "&current=alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen,european_aqi,us_aqi";
	}

	static String fmt(double v) { return String.format(Locale.US, "%.4f", v); }

	public static String get(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(20000);
		c.setReadTimeout(30000);
		c.setRequestProperty("User-Agent", "MeteoAlarmAndroid/1.92 github.com/viorelrb-alt/meteo-alarm-android");
		try {
			int code = c.getResponseCode();
			if (code / 100 != 2) throw new Exception("HTTP " + code + errorText(c));
			InputStream in = c.getInputStream();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[16384];
			int n;
			while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
			in.close();
			return new String(out.toByteArray(), "UTF-8");
		} finally {
			c.disconnect();
		}
	}

	/** The start of the server's error answer (Open-Meteo says why in JSON "reason"), for the log. */
	private static String errorText(HttpURLConnection c) {
		try {
			InputStream in = c.getErrorStream();
			if (in == null) return "";
			byte[] buf = new byte[400];
			int n = in.read(buf);
			in.close();
			if (n <= 0) return "";
			String t = new String(buf, 0, n, "UTF-8").replaceAll("\\s+", " ").trim();
			return t.length() == 0 ? "" : " (" + (t.length() > 160 ? t.substring(0, 160) + "…" : t) + ")";
		} catch (Exception e) {
			return "";
		}
	}

	/** True for answers worth asking again (server busy or down): HTTP 5xx and 429. */
	public static boolean busy(Exception e) {
		String m = e.getMessage() == null ? "" : e.getMessage();
		return m.startsWith("HTTP 5") || m.startsWith("HTTP 429");
	}

	public static Weather parse(String text) {
		Object root = Json.parse(text);
		Map<String, Object> cur = Json.obj(root, "current"), hourly = Json.obj(root, "hourly"), daily = Json.obj(root, "daily");
		if (cur == null || Double.isNaN(Json.num(cur, "temperature_2m"))) throw new IllegalArgumentException("no current weather");
		Weather w = new Weather();
		w.temp = Json.num(cur, "temperature_2m");
		w.code = (int) Json.num(cur, "weather_code");
		w.precip = nz(Json.num(cur, "precipitation"));
		w.isDay = Json.num(cur, "is_day") != 0;
		w.feels = Json.num(cur, "apparent_temperature");
		w.wind = Json.num(cur, "wind_speed_10m");
		w.windDir = Json.num(cur, "wind_direction_10m");
		double cloud = Json.num(cur, "cloud_cover"), low = Json.num(cur, "cloud_cover_low"), mid = Json.num(cur, "cloud_cover_mid");

		// The model says "overcast" too often: for sky codes 1-3 use the low + mid cloud cover
		// (high cirrus ignored), and low clouds from LOW_OVERCAST % up always mean overcast.
		double sky = cloud;
		if (!Double.isNaN(low) && !Double.isNaN(mid)) {
			int l = (int) low, m = (int) mid;
			sky = l + m - l * m / 100;
		}
		if (!Double.isNaN(sky) && w.code <= 3) {
			int c = (int) sky;
			w.code = c < 20 ? 0 : c < 45 ? 1 : c < 80 ? 2 : 3;
			if (!Double.isNaN(low) && (int) low >= LOW_OVERCAST) w.code = 3;
		}

		List<Object> hTime = Json.arr(hourly, "time"), hProb = Json.arr(hourly, "precipitation_probability"),
				hAmt = Json.arr(hourly, "precipitation"), hCode = Json.arr(hourly, "weather_code"), hSnow = Json.arr(hourly, "snowfall");
		// A dry code while it rains: use this hour's amount and probability instead.
		if (w.code <= 3 || w.code == 45 || w.code == 48) {
			double m = Math.max(w.precip, nz(Json.num(hAmt, 0)));
			boolean snow = nz(Json.num(hSnow, 0)) > 0 || w.temp <= 0;
			double p = nz(Json.num(hProb, 0));
			if (m >= 0.5) w.code = snow ? 71 : 61;
			else if (m >= 0.1) w.code = snow ? 71 : 53;
			else if (p >= 60) w.code = snow ? 71 : 51;
		}

		List<Object> dTime = Json.arr(daily, "time"), dMax = Json.arr(daily, "temperature_2m_max"), dMin = Json.arr(daily, "temperature_2m_min"),
				dCode = Json.arr(daily, "weather_code"), dPop = Json.arr(daily, "precipitation_probability_max");
		w.tmax = Json.num(dMax, 0);
		w.tmin = Json.num(dMin, 0);
		w.sunrise = hhmm(Json.str(Json.arr(daily, "sunrise"), 0));
		w.sunset = hhmm(Json.str(Json.arr(daily, "sunset"), 0));
		w.sunrise2 = hhmm(Json.str(Json.arr(daily, "sunrise"), 1));
		double uv = Json.num(Json.arr(daily, "uv_index_max"), 0);
		w.uv = Double.isNaN(uv) ? -1 : (int) Math.round(uv);
		for (int k = 1; k < dTime.size(); k++) {
			double hi = Json.num(dMax, k), lo = Json.num(dMin, k);
			if (Double.isNaN(hi) || Double.isNaN(lo)) continue;
			w.dayDates.add(Json.str(dTime, k));
			double c = Json.num(dCode, k);
			w.dayCodes.add(Double.isNaN(c) ? -1 : (int) c);
			w.dayHi.add(hi);
			w.dayLo.add(lo);
			w.dayPop.add((int) nz(Json.num(dPop, k)));
		}

		int nt = hTime.size(), n = Math.min(nt, LOOKAHEAD_HOURS), nsn = 0, nrn = 0;
		List<Object> hTemp = Json.arr(hourly, "temperature_2m"), hDay = Json.arr(hourly, "is_day");
		w.tileTemp = new double[nt];
		w.tileCode = new int[nt];
		w.tileDay = new boolean[nt];
		for (int k = 0; k < nt; k++) {
			String t = Json.str(hTime, k);
			w.tileHours.add(t != null && t.length() >= 16 ? t.substring(11, 16) : "");
			w.tileTemp[k] = Json.num(hTemp, k);
			w.tileCode[k] = (int) nz(Json.num(hCode, k));
			w.tileDay[k] = Json.num(hDay, k) != 0;
		}
		w.probs = new int[n];
		w.wet = new boolean[n];
		w.snowy = new boolean[n];
		for (int k = 0; k < n; k++) {
			String t = Json.str(hTime, k);
			String hr = t != null && t.length() >= 16 ? t.substring(11, 16) : "";
			int pv = (int) nz(Json.num(hProb, k));
			double av = nz(Json.num(hAmt, k)), sv = nz(Json.num(hSnow, k));
			int cv = (int) nz(Json.num(hCode, k));
			boolean wet = pv >= RAIN_PROBABILITY || av >= 0.1;
			boolean sn = (cv >= 71 && cv <= 77) || cv == 85 || cv == 86 || sv > 0;
			if (wet) { if (sn) nsn++; else nrn++; }
			if (pv > w.rainMaxP) w.rainMaxP = pv;
			if (w.rainAt.length() == 0 && wet) w.rainAt = hr;
			else if (w.rainAt.length() > 0 && w.rainEnd.length() == 0 && !wet) w.rainEnd = hr;
			w.hours.add(hr);
			w.probs[k] = pv;
			w.wet[k] = wet;
			w.snowy[k] = sn;
		}
		w.snowMaj = nsn > nrn;
		return w;
	}

	private static double nz(double v) { return Double.isNaN(v) ? 0 : v; }

	private static String hhmm(String iso) {
		return iso != null && iso.length() >= 16 ? iso.substring(11, 16) : "";
	}

	/** Icon of a WMO code by day or night (the names under assets/icons). */
	public static String iconOf(int code, boolean day) {
		return new Texts(false).describe(code, day)[0];
	}

	public static boolean snowCode(int code) {
		return (code >= 71 && code <= 77) || code == 85 || code == 86;
	}

	/** Rain or snow falling now (the codes 5x, 6x, 7x, 8x, 9x). */
	public boolean wetNow() {
		return code >= 50;
	}

	// --- Pollen ------------------------------------------------------------------

	/** Highest pollen level (1 low .. 4 very high, 0 no data) and its plant, from the air quality JSON. */
	public static Object[] parsePollen(String text) {
		Map<String, Object> cur = Json.obj(Json.parse(text), "current");
		String[] plants = { "alder", "birch", "olive", "grass", "mugwort", "ragweed" };
		int[][] th = { { 15, 90, 1500 }, { 15, 90, 1500 }, { 15, 90, 1500 }, { 5, 20, 200 }, { 10, 50, 500 }, { 10, 50, 500 } };
		int best = 0;
		String bp = "";
		double bv = -1;
		boolean seen = false;
		for (int k = 0; k < plants.length; k++) {
			double v = Json.num(cur, plants[k] + "_pollen");
			if (Double.isNaN(v)) continue;
			seen = true;
			int lv = v < th[k][0] ? 1 : v < th[k][1] ? 2 : v < th[k][2] ? 3 : 4;
			if (lv > best || (lv == best && v > bv)) { best = lv; bp = plants[k]; bv = v; }
		}
		if (!seen) best = 0;
		if (bv <= 0) bp = "";
		return new Object[] { best, bp };
	}
}
