package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.List;

/**
 * v4.20 (Viorel): every locality of Romania, villages too (assets/villages.txt, "county|name|lat|lon"),
 * for the big compass and for the location when no map service answers. Not a town list to choose
 * from: a village joins the town list only when the phone passes through it (see Visits).
 */
public final class Villages {
	private final String[] county, name;
	private final float[] lat, lon;
	public final int size;
	/** v4.40: the country of the list ("ro", "us", "de"...) and the box its places lie in. */
	public final String country;
	private float minLa = 90, maxLa = -90, minLo = 180, maxLo = -180;

	public Villages(String text) {
		this(text, "ro");
	}

	public Villages(String text, String country) {
		this.country = country;
		List<String[]> rows = new ArrayList<String[]>();
		for (String line : text.split("\n")) {
			if (line.length() == 0 || line.charAt(0) == '#') continue;
			String[] p = line.trim().split("\\|");
			if (p.length == 4) rows.add(p);
		}
		int n = 0;
		county = new String[rows.size()];
		name = new String[rows.size()];
		lat = new float[rows.size()];
		lon = new float[rows.size()];
		for (String[] p : rows) {
			try {
				lat[n] = Float.parseFloat(p[2]);
				lon[n] = Float.parseFloat(p[3]);
				county[n] = p[0].intern();
				name[n] = p[1];
				minLa = Math.min(minLa, lat[n]);
				maxLa = Math.max(maxLa, lat[n]);
				minLo = Math.min(minLo, lon[n]);
				maxLo = Math.max(maxLo, lon[n]);
				n++;
			} catch (NumberFormatException e) {
				// a bad line
			}
		}
		size = n;
	}

	/** The N localities nearest to LAT, LON, nearest first. */
	public List<Cities.City> nearest(double la, double lo, int n) {
		double k = Math.cos(Math.toRadians(la));
		int[] best = new int[n];
		double[] bd = new double[n];
		int have = 0;
		for (int i = 0; i < size; i++) {
			double dy = lat[i] - la, dx = (lon[i] - lo) * k, d = dx * dx + dy * dy;
			if (have == n && d >= bd[n - 1]) continue;
			int j = Math.min(have, n - 1);
			while (j > 0 && bd[j - 1] > d) {
				bd[j] = bd[j - 1];
				best[j] = best[j - 1];
				j--;
			}
			bd[j] = d;
			best[j] = i;
			if (have < n) have++;
		}
		List<Cities.City> out = new ArrayList<Cities.City>();
		for (int i = 0; i < have; i++) {
			int x = best[i];
			out.add(new Cities.City(country, county[x], name[x], Math.round(lat[x] * 10000) / 10000.0, Math.round(lon[x] * 10000) / 10000.0, ""));
		}
		return out;
	}

	/** Whether LAT, LON lies in the box of the list's places (with about 30 km around it). */
	public boolean covers(double la, double lo) {
		return size > 0 && la >= minLa - 0.3 && la <= maxLa + 0.3 && lo >= minLo - 0.4 && lo <= maxLo + 0.4;
	}

	/** The locality nearest to LAT, LON when it is at most KM away (null otherwise or outside the list's area). */
	public Cities.City at(double la, double lo, double km) {
		if (!covers(la, lo)) return null;
		List<Cities.City> l = nearest(la, lo, 1);
		if (l.isEmpty()) return null;
		Cities.City c = l.get(0);
		double dy = (c.lat - la) * 111.2, dx = (c.lon - lo) * 111.2 * Math.cos(Math.toRadians(la));
		return Math.sqrt(dx * dx + dy * dy) <= km ? c : null;
	}
}
