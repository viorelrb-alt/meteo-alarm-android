package ro.meteoalarm.android.core;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The spare forecast (Viorel 2026-11-02, when Open-Meteo answered "The service is overloaded" for
 * an hour on every phone): MET Norway's Locationforecast (api.met.no, free, needs a User-Agent
 * that names the app). Its answer is turned into an Open-Meteo-shaped answer, so the app, the
 * widgets and Weather.parse read it as always. Sunrise and sunset are worked out here (NOAA).
 */
public final class MetNo {
	public static final String NAME = "MET Norway";

	public static String url(double lat, double lon) {
		// MET Norway asks for at most 4 decimals.
		return "https://api.met.no/weatherapi/locationforecast/2.0/complete?lat=" + Weather.fmt(lat) + "&lon=" + Weather.fmt(lon);
	}

	/** One time step of the answer. */
	private static final class Step {
		long t;
		double temp = Double.NaN, rh = Double.NaN, wind = Double.NaN, dir = Double.NaN, press = Double.NaN,
				cloud = Double.NaN, low = Double.NaN, mid = Double.NaN, high = Double.NaN, uv = Double.NaN, fog = Double.NaN;
		/** From next_1_hours (or next_6_hours when there is no hour). */
		String sym;
		double amt = Double.NaN, pop = Double.NaN, tmax6 = Double.NaN, tmin6 = Double.NaN, pop6 = Double.NaN;
		String sym6;
		boolean hourly;
	}

	/**
	 * { forecast, extra }: the Open-Meteo-shaped forecast (for Weather.parse) and the extra
	 * (for Weather.addExtra: humidity, pressure, UV). TZ: the phone's time zone.
	 */
	public static String[] toOpenMeteo(String text, double lat, double lon, TimeZone tz, long now) {
		Object root = Json.parse(text);
		List<Object> ts = Json.arr(Json.obj(root, "properties"), "timeseries");
		if (ts == null || ts.isEmpty()) throw new IllegalArgumentException("MET Norway: no timeseries");
		SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
		iso.setTimeZone(TimeZone.getTimeZone("UTC"));
		List<Step> steps = new ArrayList<Step>();
		for (Object o : ts) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m = (Map<String, Object>) o;
			Step s = new Step();
			try {
				s.t = iso.parse((String) m.get("time")).getTime();
			} catch (Exception e) {
				continue;
			}
			Map<String, Object> data = Json.obj(m, "data");
			Map<String, Object> d = Json.obj(Json.obj(data, "instant"), "details");
			if (d == null) continue;
			s.temp = Json.num(d, "air_temperature");
			s.rh = Json.num(d, "relative_humidity");
			s.wind = Json.num(d, "wind_speed");
			s.dir = Json.num(d, "wind_from_direction");
			s.press = Json.num(d, "air_pressure_at_sea_level");
			s.cloud = Json.num(d, "cloud_area_fraction");
			s.low = Json.num(d, "cloud_area_fraction_low");
			s.mid = Json.num(d, "cloud_area_fraction_medium");
			s.high = Json.num(d, "cloud_area_fraction_high");
			s.uv = Json.num(d, "ultraviolet_index_clear_sky");
			s.fog = Json.num(d, "fog_area_fraction");
			Map<String, Object> n1 = Json.obj(data, "next_1_hours"), n6 = Json.obj(data, "next_6_hours");
			if (n1 != null) {
				s.hourly = true;
				s.sym = Json.str1(Json.obj(n1, "summary"), "symbol_code");
				Map<String, Object> nd = Json.obj(n1, "details");
				s.amt = Json.num(nd, "precipitation_amount");
				s.pop = Json.num(nd, "probability_of_precipitation");
			}
			if (n6 != null) {
				s.sym6 = Json.str1(Json.obj(n6, "summary"), "symbol_code");
				Map<String, Object> nd = Json.obj(n6, "details");
				s.tmax6 = Json.num(nd, "air_temperature_max");
				s.tmin6 = Json.num(nd, "air_temperature_min");
				s.pop6 = Json.num(nd, "probability_of_precipitation");
				if (n1 == null) {
					s.sym = s.sym6;
					s.pop = s.pop6;
					double a6 = Json.num(nd, "precipitation_amount");
					s.amt = Double.isNaN(a6) ? Double.NaN : a6 / 6;
				}
			}
			steps.add(s);
		}
		if (steps.isEmpty()) throw new IllegalArgumentException("MET Norway: empty");

		SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US), local = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
		day.setTimeZone(tz);
		local.setTimeZone(tz);

		// Now: the step of this hour (the first one not older than an hour).
		long hourStart = now - now % 3600000L;
		int first = 0;
		while (first < steps.size() - 1 && steps.get(first).t < hourStart) first++;
		Step c = steps.get(first);

		// Sunrise / sunset for the next 7 days.
		Map<String, long[]> sun = new LinkedHashMap<String, long[]>();
		Calendar cal = Calendar.getInstance(tz);
		cal.setTimeInMillis(now);
		for (int k = 0; k < 7; k++) {
			sun.put(day.format(cal.getTime()), sunTimes(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), lat, lon, tz));
			cal.add(Calendar.DAY_OF_MONTH, 1);
		}

		StringBuilder b = new StringBuilder(16000);
		b.append("{\"source\":\"").append(NAME).append("\",\"current\":{");
		kv(b, "time", local.format(new Date(c.t))).append(',');
		kv(b, "temperature_2m", c.temp).append(',');
		kv(b, "weather_code", code(c.sym, c.fog)).append(',');
		kv(b, "precipitation", Double.isNaN(c.amt) ? 0 : c.amt).append(',');
		kv(b, "is_day", isDay(c.t, sun, day) ? 1 : 0).append(',');
		kv(b, "cloud_cover", c.cloud).append(',');
		kv(b, "cloud_cover_low", c.low).append(',');
		kv(b, "cloud_cover_mid", c.mid).append(',');
		kv(b, "cloud_cover_high", c.high).append(',');
		kv(b, "apparent_temperature", apparent(c.temp, c.rh, c.wind)).append(',');
		kv(b, "wind_speed_10m", Double.isNaN(c.wind) ? Double.NaN : c.wind * 3.6).append(',');
		kv(b, "wind_direction_10m", c.dir).append('}');

		// Hour by hour from this hour (only the steps one hour apart).
		List<Step> hrs = new ArrayList<Step>();
		for (int k = first; k < steps.size() && hrs.size() < Weather.TILE_HOURS; k++)
			if (steps.get(k).hourly) hrs.add(steps.get(k));
		b.append(",\"hourly\":{");
		StringBuilder t = new StringBuilder(), pp = new StringBuilder(), pr = new StringBuilder(), wc = new StringBuilder(),
				sn = new StringBuilder(), tp = new StringBuilder(), dy = new StringBuilder();
		for (int k = 0; k < hrs.size(); k++) {
			Step s = hrs.get(k);
			String sep = k == 0 ? "" : ",";
			int cd = code(s.sym, s.fog);
			double amt = Double.isNaN(s.amt) ? 0 : s.amt;
			t.append(sep).append('"').append(local.format(new Date(s.t))).append('"');
			pp.append(sep).append(num(Double.isNaN(s.pop) ? (amt >= 0.1 ? 70 : 0) : s.pop));
			pr.append(sep).append(num(amt));
			wc.append(sep).append(cd);
			sn.append(sep).append(num(Weather.snowCode(cd) ? amt * 0.7 : 0));
			tp.append(sep).append(num(s.temp));
			dy.append(sep).append(isDay(s.t, sun, day) ? 1 : 0);
		}
		b.append("\"time\":[").append(t).append("],\"precipitation_probability\":[").append(pp).append("],\"precipitation\":[").append(pr)
				.append("],\"weather_code\":[").append(wc).append("],\"snowfall\":[").append(sn).append("],\"temperature_2m\":[").append(tp)
				.append("],\"is_day\":[").append(dy).append("]}");

		// Day by day (local dates): high / low, the sky around midday, the highest chance of rain, UV.
		Map<String, double[]> days = new LinkedHashMap<String, double[]>(); // hi, lo, pop, uv, code, codeDist
		for (Step s : steps) {
			String dk = day.format(new Date(s.t));
			if (!sun.containsKey(dk)) continue;
			double[] v = days.get(dk);
			if (v == null) {
				v = new double[] { -999, 999, 0, 0, -1, 99 };
				days.put(dk, v);
			}
			if (!Double.isNaN(s.temp)) {
				v[0] = Math.max(v[0], s.temp);
				v[1] = Math.min(v[1], s.temp);
			}
			if (!Double.isNaN(s.tmax6)) v[0] = Math.max(v[0], s.tmax6);
			if (!Double.isNaN(s.tmin6)) v[1] = Math.min(v[1], s.tmin6);
			double p = Double.isNaN(s.pop) ? s.pop6 : s.pop;
			if (!Double.isNaN(p)) v[2] = Math.max(v[2], p);
			if (!Double.isNaN(s.uv)) v[3] = Math.max(v[3], s.uv);
			// The sky of the 6 hours from the step nearest 09:00 (so 09–15), else the nearest to noon.
			String sym = s.sym6 != null ? s.sym6 : s.sym;
			if (sym != null) {
				Calendar h = Calendar.getInstance(tz);
				h.setTimeInMillis(s.t);
				double dist = Math.abs(h.get(Calendar.HOUR_OF_DAY) - (s.sym6 != null ? 9 : 12));
				if (dist < v[5]) {
					v[5] = dist;
					v[4] = code(sym, Double.NaN);
				}
			}
		}
		b.append(",\"daily\":{");
		StringBuilder dt = new StringBuilder(), mx = new StringBuilder(), mn = new StringBuilder(), dc = new StringBuilder(),
				dp = new StringBuilder(), sr = new StringBuilder(), ss = new StringBuilder(), uv = new StringBuilder();
		int k = 0;
		for (Map.Entry<String, double[]> e : days.entrySet()) {
			if (k >= 6) break;
			double[] v = e.getValue();
			long[] st = sun.get(e.getKey());
			String sep = k == 0 ? "" : ",";
			dt.append(sep).append('"').append(e.getKey()).append('"');
			mx.append(sep).append(v[0] < -900 ? "null" : num(v[0]));
			mn.append(sep).append(v[1] > 900 ? "null" : num(v[1]));
			dc.append(sep).append((int) v[4]);
			dp.append(sep).append(num(v[2]));
			sr.append(sep).append(st[0] > 0 ? "\"" + local.format(new Date(st[0])) + "\"" : "null");
			ss.append(sep).append(st[1] > 0 ? "\"" + local.format(new Date(st[1])) + "\"" : "null");
			uv.append(sep).append(num(v[3]));
			k++;
		}
		b.append("\"time\":[").append(dt).append("],\"temperature_2m_max\":[").append(mx).append("],\"temperature_2m_min\":[").append(mn)
				.append("],\"weather_code\":[").append(dc).append("],\"precipitation_probability_max\":[").append(dp)
				.append("],\"sunrise\":[").append(sr).append("],\"sunset\":[").append(ss).append("],\"uv_index_max\":[").append(uv).append("]}}");

		// The extra (Weather.addExtra): humidity and pressure now, the pressure 3 hours ago is not
		// known (no trend), UV of today.
		StringBuilder x = new StringBuilder();
		x.append("{\"source\":\"").append(NAME).append("\",\"current\":{");
		kv(x, "relative_humidity_2m", c.rh).append(',');
		kv(x, "pressure_msl", c.press).append('}');
		x.append(",\"daily\":{\"uv_index_max\":[").append(days.isEmpty() ? "null" : num(days.values().iterator().next()[3])).append("]}}");
		return new String[] { b.toString(), x.toString() };
	}

	private static StringBuilder kv(StringBuilder b, String k, double v) {
		return b.append('"').append(k).append("\":").append(num(v));
	}

	private static StringBuilder kv(StringBuilder b, String k, int v) {
		return b.append('"').append(k).append("\":").append(v);
	}

	private static StringBuilder kv(StringBuilder b, String k, String v) {
		return b.append('"').append(k).append("\":\"").append(v).append('"');
	}

	private static String num(double v) {
		if (Double.isNaN(v) || Double.isInfinite(v)) return "null";
		return String.format(Locale.US, "%.1f", v);
	}

	private static boolean isDay(long t, Map<String, long[]> sun, SimpleDateFormat day) {
		long[] s = sun.get(day.format(new Date(t)));
		if (s == null || s[0] <= 0 || s[1] <= 0) {
			Calendar h = Calendar.getInstance(day.getTimeZone());
			h.setTimeInMillis(t);
			int hr = h.get(Calendar.HOUR_OF_DAY);
			return hr >= 7 && hr < 19;
		}
		// The hour from T: day when most of it is between sunrise and sunset.
		long mid = t + 1800000L;
		return mid >= s[0] && mid < s[1];
	}

	/** Apparent temperature (Steadman / Australian BoM): air T °C, humidity %, wind m/s. */
	static double apparent(double t, double rh, double ws) {
		if (Double.isNaN(t)) return Double.NaN;
		if (Double.isNaN(rh) || Double.isNaN(ws)) return t;
		double e = rh / 100 * 6.105 * Math.exp(17.27 * t / (237.7 + t));
		return t + 0.33 * e - 0.70 * ws - 4.00;
	}

	/** MET Norway symbol ("lightrainshowers_day") to the WMO code the app knows. */
	static int code(String sym, double fog) {
		if (sym == null) return Double.isNaN(fog) || fog < 50 ? 3 : 45;
		String s = sym.replace("_day", "").replace("_night", "").replace("_polartwilight", "");
		if (s.contains("thunder")) return s.contains("heavy") ? 99 : 95;
		boolean light = s.startsWith("light"), heavy = s.startsWith("heavy"), shower = s.contains("showers");
		if (s.contains("sleet")) return light ? 61 : heavy ? 65 : 63;
		if (s.contains("snow")) return shower ? (heavy ? 86 : 85) : light ? 71 : heavy ? 75 : 73;
		if (s.contains("rain")) return shower ? (light ? 80 : heavy ? 82 : 81) : light ? 61 : heavy ? 65 : 63;
		if (s.equals("fog")) return 45;
		if (s.equals("clearsky")) return 0;
		if (s.equals("fair")) return 1;
		if (s.equals("partlycloudy")) return 2;
		return 3;
	}

	/**
	 * { sunrise, sunset } in ms (0 when the sun does not rise or set) for a date at LAT / LON,
	 * NOAA's sunrise equation (zenith 90.833°), a minute or two off at most.
	 */
	static long[] sunTimes(int y, int mo, int d, double lat, double lon, TimeZone tz) {
		Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		c.clear();
		c.set(y, mo - 1, d, 12, 0, 0);
		long noonUtc = c.getTimeInMillis();
		int n = c.get(Calendar.DAY_OF_YEAR);
		double g = 2 * Math.PI / 365 * (n - 1);
		double eqt = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
		double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g)
				- 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
		double la = Math.toRadians(lat);
		double cosH = Math.cos(Math.toRadians(90.833)) / (Math.cos(la) * Math.cos(decl)) - Math.tan(la) * Math.tan(decl);
		if (cosH > 1 || cosH < -1) return new long[] { 0, 0 };
		double ha = Math.toDegrees(Math.acos(cosH));
		// Minutes from midnight UTC.
		double rise = 720 - 4 * (lon + ha) - eqt, set = 720 - 4 * (lon - ha) - eqt;
		long midnight = noonUtc - 12 * 3600000L;
		// The local date must be the one asked: the UTC day of the local noon.
		long off = tz.getOffset(noonUtc);
		long shift = 0;
		if (off > 12 * 3600000L) shift = -86400000L;
		if (off < -12 * 3600000L) shift = 86400000L;
		return new long[] { midnight + shift + Math.round(rise * 60000), midnight + shift + Math.round(set * 60000) };
	}
}
