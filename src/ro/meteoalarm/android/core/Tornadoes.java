package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Tornado reports of the NOAA Storm Prediction Center (spc.noaa.gov, v2.40): the preliminary
 * reports of today and yesterday, as CSV ("Time,F_Scale,Location,County,State,Lat,Lon,Comments").
 * An SPC day runs from 12:00 UTC to 12:00 UTC the next day; the times are UTC (HHMM).
 */
public final class Tornadoes {
	private Tornadoes() {
	}

	public static final String TODAY = "https://www.spc.noaa.gov/climo/reports/today_torn.csv",
			YESTERDAY = "https://www.spc.noaa.gov/climo/reports/yesterday_torn.csv";

	public static final class Report {
		public long time;
		public String scale = "", place = "", county = "", state = "", comment = "";
		public double lat, lon, dist;
	}

	/** The start (epoch ms) of the SPC day that holds NOW, DAYS_BACK days earlier. */
	public static long dayStart(long now, int daysBack) {
		Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		c.setTimeInMillis(now);
		if (c.get(Calendar.HOUR_OF_DAY) < 12) c.add(Calendar.DAY_OF_MONTH, -1);
		c.set(Calendar.HOUR_OF_DAY, 12);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.add(Calendar.DAY_OF_MONTH, -daysBack);
		return c.getTimeInMillis();
	}

	/** Splits a CSV line (commas inside quotes kept). */
	static List<String> cells(String line) {
		List<String> l = new ArrayList<String>();
		StringBuilder b = new StringBuilder();
		boolean q = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (ch == '"') q = !q;
			else if (ch == ',' && !q) {
				l.add(b.toString().trim());
				b.setLength(0);
			} else b.append(ch);
		}
		l.add(b.toString().trim());
		return l;
	}

	/** The reports of one SPC CSV for the day starting at START, with the distance (km) from LAT LON. */
	public static List<Report> parse(String csv, long start, double lat, double lon) {
		List<Report> out = new ArrayList<Report>();
		if (csv == null) return out;
		for (String line : csv.replace("\r", "").split("\n")) {
			List<String> c = cells(line);
			if (c.size() < 7 || !c.get(0).matches("\\d{3,4}")) continue;
			try {
				Report r = new Report();
				int hm = Integer.parseInt(c.get(0));
				long t = start + ((hm / 100) * 60L + hm % 100) * 60000L - 12 * 3600000L;
				if (hm < 1200) t += 24 * 3600000L;
				r.time = t;
				r.scale = c.get(1);
				r.place = c.get(2);
				r.county = c.get(3);
				r.state = c.get(4);
				r.lat = Double.parseDouble(c.get(5));
				r.lon = Double.parseDouble(c.get(6));
				if (c.size() > 7) {
					StringBuilder b = new StringBuilder(c.get(7));
					for (int i = 8; i < c.size(); i++) b.append(", ").append(c.get(i));
					r.comment = b.toString().replaceAll("\\s*\\([A-Z]{3}\\)\\s*$", "").trim();
				}
				r.dist = Quakes.km(lat, lon, r.lat, r.lon);
				out.add(r);
			} catch (Exception e) {
				// a bad line
			}
		}
		return out;
	}

	/** Newest first. */
	public static void sort(List<Report> l) {
		java.util.Collections.sort(l, new java.util.Comparator<Report>() {
			public int compare(Report a, Report b) {
				return a.time > b.time ? -1 : a.time < b.time ? 1 : 0;
			}
		});
	}
}
