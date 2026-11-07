package ro.meteoalarm.android.core;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Farmer mode (v2.50, Viorel 2026-11-07): the data a farmer needs, from Open-Meteo only (the soil
 * values are model estimates; MET Norway has none): hour by hour the temperature, air humidity,
 * chance and amount of rain, cloud cover, wind with gusts and direction, the soil temperature at
 * the surface and the soil moisture 3–9 cm deep; per day the high, low, rain total and highest
 * wind. The same answer serves the main screen (from the current hour) and a saved parcel (with
 * the last 28 days, for its statistics).
 */
public final class Farm {
	public static final String SOURCE = "Open-Meteo";
	/** The statistics cover this many days back (Viorel: kept 28 days). */
	public static final int KEEP_DAYS = 28;
	/** Alerts: a rain hour (chance and amount), a strong wind (km/h, gusts or mean). */
	public static final int ALERT_PROB = 60;
	public static final double ALERT_MM = 0.5, ALERT_MM_ANY = 1.0, ALERT_GUST = 50, ALERT_WIND = 40;

	private static final String HOURLY = "temperature_2m,relative_humidity_2m,precipitation_probability,precipitation,rain,showers,snowfall,"
			+ "cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m,soil_temperature_0cm,soil_moisture_3_to_9cm";

	private Farm() {
	}

	/** The request: PAST days back (0–92) and DAYS ahead (from today), hour by hour and per day. */
	public static String url(double lat, double lon, String tz, int past, int days) throws Exception {
		return "https://api.open-meteo.com/v1/forecast?latitude=" + Weather.fmt(lat) + "&longitude=" + Weather.fmt(lon)
				+ "&current=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m"
				+ "&hourly=" + HOURLY
				+ "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max,precipitation_probability_max"
				+ (past > 0 ? "&past_days=" + past : "") + "&forecast_days=" + days
				+ "&timezone=" + URLEncoder.encode(tz, "UTF-8");
	}

	/** One answer, read. Missing values are NaN. */
	public static final class Data {
		public String[] time = new String[0];
		public double[] temp, hum, prob, precip, rain, showers, snow, cloud, wind, dir, gust, soilT, soilM;
		public String[] dDate = new String[0];
		public double[] dMax, dMin, dRain, dWind, dGust, dPop;
		public double cTemp = Double.NaN, cHum = Double.NaN, cPrecip = Double.NaN, cWind = Double.NaN, cDir = Double.NaN, cGust = Double.NaN;
		public int cCode = -1;

		public int size() {
			return time.length;
		}

		/** The index of hour ISO ("2026-11-07T18:00"), -1 when not there. */
		public int index(String iso) {
			for (int k = 0; k < time.length; k++) if (time[k] != null && time[k].startsWith(iso)) return k;
			return -1;
		}

		/** The index of the hour NOW is in. */
		public int nowIndex(Calendar now) {
			return index(String.format(Locale.US, "%04d-%02d-%02dT%02d:00", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
					now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.HOUR_OF_DAY)));
		}

		public int dayIndex(String date) {
			for (int k = 0; k < dDate.length; k++) if (date.equals(dDate[k])) return k;
			return -1;
		}

		/** The date ("2026-11-07") of hour K. */
		public String dateOf(int k) {
			return k >= 0 && k < time.length && time[k] != null && time[k].length() >= 10 ? time[k].substring(0, 10) : "";
		}

		/** "18:00" of hour K. */
		public String hourOf(int k) {
			return k >= 0 && k < time.length && time[k] != null && time[k].length() >= 16 ? time[k].substring(11, 16) : "";
		}

		public boolean has(int k) {
			return k >= 0 && k < time.length;
		}
	}

	public static Data parse(String text) {
		Object root = Json.parse(text);
		Map<String, Object> h = Json.obj(root, "hourly"), d = Json.obj(root, "daily"), c = Json.obj(root, "current");
		if (h == null) throw new IllegalArgumentException("no hourly data");
		Data r = new Data();
		List<Object> t = Json.arr(h, "time");
		int n = t == null ? 0 : t.size();
		r.time = new String[n];
		for (int k = 0; k < n; k++) r.time[k] = Json.str(t, k);
		r.temp = nums(h, "temperature_2m", n);
		r.hum = nums(h, "relative_humidity_2m", n);
		r.prob = nums(h, "precipitation_probability", n);
		r.precip = nums(h, "precipitation", n);
		r.rain = nums(h, "rain", n);
		r.showers = nums(h, "showers", n);
		r.snow = nums(h, "snowfall", n);
		r.cloud = nums(h, "cloud_cover", n);
		r.wind = nums(h, "wind_speed_10m", n);
		r.dir = nums(h, "wind_direction_10m", n);
		r.gust = nums(h, "wind_gusts_10m", n);
		r.soilT = nums(h, "soil_temperature_0cm", n);
		r.soilM = nums(h, "soil_moisture_3_to_9cm", n);
		if (d != null) {
			List<Object> dt = Json.arr(d, "time");
			int m = dt == null ? 0 : dt.size();
			r.dDate = new String[m];
			for (int k = 0; k < m; k++) r.dDate[k] = Json.str(dt, k);
			r.dMax = nums(d, "temperature_2m_max", m);
			r.dMin = nums(d, "temperature_2m_min", m);
			r.dRain = nums(d, "precipitation_sum", m);
			r.dWind = nums(d, "wind_speed_10m_max", m);
			r.dGust = nums(d, "wind_gusts_10m_max", m);
			r.dPop = nums(d, "precipitation_probability_max", m);
		} else {
			r.dMax = r.dMin = r.dRain = r.dWind = r.dGust = r.dPop = new double[0];
		}
		if (c != null) {
			r.cTemp = Json.num(c, "temperature_2m");
			r.cHum = Json.num(c, "relative_humidity_2m");
			r.cPrecip = Json.num(c, "precipitation");
			r.cWind = Json.num(c, "wind_speed_10m");
			r.cDir = Json.num(c, "wind_direction_10m");
			r.cGust = Json.num(c, "wind_gusts_10m");
			double cc = Json.num(c, "weather_code");
			r.cCode = Double.isNaN(cc) ? -1 : (int) cc;
		}
		return r;
	}

	private static double[] nums(Map<String, Object> o, String key, int n) {
		double[] v = new double[n];
		List<Object> a = Json.arr(o, key);
		for (int k = 0; k < n; k++) v[k] = a == null || k >= a.size() ? Double.NaN : Json.num(a, k);
		return v;
	}

	// --- Wind ---------------------------------------------------------------------------

	/** One of the 8 directions (0 = N … 7 = NW) the wind comes from. */
	public static int sector(double deg) {
		return (int) Math.round(((deg % 360) + 360) % 360 / 45) % 8;
	}

	private static double angle(double a, double b) {
		double d = Math.abs(((a - b) % 360 + 360) % 360);
		return d > 180 ? 360 - d : d;
	}

	/**
	 * When the wind turns within HOURS after hour FROM: the first hour whose direction is at least
	 * 60° from FROM's and stays so the hour after (calm hours, under 3 km/h, ignored); -1 when it
	 * keeps its direction.
	 */
	public static int windShift(Data d, int from, int hours) {
		if (!d.has(from) || Double.isNaN(d.dir[from])) return -1;
		double base = d.dir[from];
		int end = Math.min(d.size() - 1, from + hours);
		for (int k = from + 1; k < end; k++) {
			if (Double.isNaN(d.dir[k]) || Double.isNaN(d.dir[k + 1])) continue;
			if (!Double.isNaN(d.wind[k]) && d.wind[k] < 3) continue;
			if (angle(d.dir[k], base) >= 60 && angle(d.dir[k + 1], base) >= 60) return k;
		}
		return -1;
	}

	// --- Soil ---------------------------------------------------------------------------

	/** Soil moisture (m³/m³) in words: 0 dry, 1 moderate, 2 moist, 3 very wet; -1 unknown. */
	public static int soilLevel(double m) {
		if (Double.isNaN(m)) return -1;
		return m < 0.15 ? 0 : m < 0.25 ? 1 : m < 0.35 ? 2 : 3;
	}

	// --- Alerts -------------------------------------------------------------------------

	/**
	 * Within HOURS after hour FROM: { the first rain hour, the first strong-wind hour } (indexes,
	 * -1 for none). Rain: a chance of ALERT_PROB % with at least ALERT_MM mm, or ALERT_MM_ANY mm.
	 * Wind: gusts of ALERT_GUST km/h or a mean wind of ALERT_WIND km/h.
	 */
	public static int[] alert(Data d, int from, int hours) {
		int rain = -1, wind = -1, end = Math.min(d.size(), from + 1 + hours);
		for (int k = Math.max(0, from); k < end; k++) {
			double p = nz(d.prob[k]), mm = nz(d.precip[k]);
			if (rain < 0 && ((p >= ALERT_PROB && mm >= ALERT_MM) || mm >= ALERT_MM_ANY)) rain = k;
			if (wind < 0 && (nz(d.gust[k]) >= ALERT_GUST || nz(d.wind[k]) >= ALERT_WIND)) wind = k;
		}
		return new int[] { rain, wind };
	}

	private static double nz(double v) {
		return Double.isNaN(v) ? 0 : v;
	}

	// --- Statistics -----------------------------------------------------------------------

	/** One day of a parcel's statistics. */
	public static final class Day {
		public String date;
		public double tmax = Double.NaN, tmin = Double.NaN, rain = Double.NaN, wind = Double.NaN, gust = Double.NaN, soil = Double.NaN;

		public String line() {
			return date + "|" + v(tmax) + "|" + v(tmin) + "|" + v(rain) + "|" + v(wind) + "|" + v(soil) + "|" + v(gust);
		}

		private static String v(double x) {
			return Double.isNaN(x) ? "" : String.format(Locale.US, "%.3f", x);
		}

		public static Day of(String line) {
			String[] p = line.split("\\|", -1);
			if (p.length < 6 || p[0].length() != 10) return null;
			Day d = new Day();
			d.date = p[0];
			d.tmax = n(p[1]);
			d.tmin = n(p[2]);
			d.rain = n(p[3]);
			d.wind = n(p[4]);
			d.soil = n(p[5]);
			if (p.length > 6) d.gust = n(p[6]);
			return d;
		}

		private static double n(String s) {
			try {
				return s.length() == 0 ? Double.NaN : Double.parseDouble(s);
			} catch (Exception e) {
				return Double.NaN;
			}
		}
	}

	/** The days of D up to TODAY ("yyyy-MM-dd"), the soil moisture as the mean of their hours. */
	public static List<Day> days(Data d, String today) {
		List<Day> out = new ArrayList<Day>();
		for (int k = 0; k < d.dDate.length; k++) {
			String date = d.dDate[k];
			if (date == null || date.compareTo(today) > 0) continue;
			Day x = new Day();
			x.date = date;
			x.tmax = d.dMax[k];
			x.tmin = d.dMin[k];
			x.rain = d.dRain[k];
			x.wind = d.dWind[k];
			x.gust = d.dGust[k];
			double sum = 0;
			int n = 0;
			for (int i = 0; i < d.size(); i++) {
				if (d.time[i] == null || !d.time[i].startsWith(date) || Double.isNaN(d.soilM[i])) continue;
				sum += d.soilM[i];
				n++;
			}
			if (n > 0) x.soil = sum / n;
			if (Double.isNaN(x.tmax) && Double.isNaN(x.rain)) continue;
			out.add(x);
		}
		return out;
	}

	/** The days kept (STORED, one line each) with FRESH ones put in, without those before OLDEST. */
	public static String merge(String stored, List<Day> fresh, String oldest) {
		TreeMap<String, String> m = new TreeMap<String, String>();
		if (stored != null) for (String l : stored.split("\n")) {
			Day d = Day.of(l.trim());
			if (d != null) m.put(d.date, d.line());
		}
		for (Day d : fresh) m.put(d.date, d.line());
		StringBuilder b = new StringBuilder();
		for (Map.Entry<String, String> e : m.entrySet()) if (e.getKey().compareTo(oldest) >= 0) b.append(e.getValue()).append('\n');
		return b.toString();
	}

	/** The days kept, from FROM to TO (dates, both included). */
	public static List<Day> read(String stored, String from, String to) {
		List<Day> out = new ArrayList<Day>();
		if (stored == null) return out;
		for (String l : stored.split("\n")) {
			Day d = Day.of(l.trim());
			if (d != null && d.date.compareTo(from) >= 0 && d.date.compareTo(to) <= 0) out.add(d);
		}
		return out;
	}

	/** The period's summary: { lowest, highest, rain total, highest wind, mean soil moisture, rainy days (1 mm+), strongest gust }. */
	public static double[] summary(List<Day> days) {
		double lo = Double.NaN, hi = Double.NaN, rain = 0, wind = Double.NaN, soil = 0, gust = Double.NaN;
		int ns = 0, wet = 0, nr = 0;
		for (Day d : days) {
			if (!Double.isNaN(d.tmin) && (Double.isNaN(lo) || d.tmin < lo)) lo = d.tmin;
			if (!Double.isNaN(d.tmax) && (Double.isNaN(hi) || d.tmax > hi)) hi = d.tmax;
			if (!Double.isNaN(d.rain)) {
				rain += d.rain;
				nr++;
				if (d.rain >= 1) wet++;
			}
			if (!Double.isNaN(d.wind) && (Double.isNaN(wind) || d.wind > wind)) wind = d.wind;
			if (!Double.isNaN(d.gust) && (Double.isNaN(gust) || d.gust > gust)) gust = d.gust;
			if (!Double.isNaN(d.soil)) {
				soil += d.soil;
				ns++;
			}
		}
		return new double[] { lo, hi, nr > 0 ? rain : Double.NaN, wind, ns > 0 ? soil / ns : Double.NaN, wet, gust };
	}

	/** "yyyy-MM-dd" of CAL plus DAYS. */
	public static String date(Calendar cal, int days) {
		Calendar c = (Calendar) cal.clone();
		c.add(Calendar.DAY_OF_MONTH, days);
		return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
	}
}
