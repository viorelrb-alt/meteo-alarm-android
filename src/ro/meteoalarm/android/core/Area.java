package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A town's (or a sector's) boundary from OpenStreetMap: one or more outer rings of lat/lon points.
 * The town shown changes only when the position is out of the current town's boundary by more
 * than a small margin, so it does not flip back and forth at the edge.
 */
public final class Area {
	/** Each ring: lat0, lon0, lat1, lon1, ... */
	public final List<double[]> rings = new ArrayList<double[]>();

	/** The outer rings of a GeoJSON Polygon or MultiPolygon (holes ignored); null for anything else. */
	@SuppressWarnings("unchecked")
	public static Area fromGeoJson(Map<String, Object> g) {
		if (g == null) return null;
		Object type = g.get("type"), coords = g.get("coordinates");
		if (!(coords instanceof List)) return null;
		Area a = new Area();
		if ("Polygon".equals(type)) {
			a.addRing((List<Object>) coords);
		} else if ("MultiPolygon".equals(type)) {
			for (Object poly : (List<Object>) coords) if (poly instanceof List) a.addRing((List<Object>) poly);
		} else {
			return null;
		}
		return a.rings.isEmpty() ? null : a;
	}

	/** The first (outer) ring of a polygon's rings, [lon, lat] pairs. */
	private void addRing(List<Object> poly) {
		if (poly.isEmpty() || !(poly.get(0) instanceof List)) return;
		List<?> ring = (List<?>) poly.get(0);
		double[] r = new double[ring.size() * 2];
		int n = 0;
		for (Object p : ring) {
			if (!(p instanceof List) || ((List<?>) p).size() < 2) continue;
			Object lo = ((List<?>) p).get(0), la = ((List<?>) p).get(1);
			if (!(lo instanceof Double) || !(la instanceof Double)) continue;
			r[n++] = (Double) la;
			r[n++] = (Double) lo;
		}
		if (n >= 6) {
			double[] t = new double[n];
			System.arraycopy(r, 0, t, 0, n);
			rings.add(t);
		}
	}

	/** True when LAT LON is inside, or outside by at most MARGIN metres. */
	public boolean holds(double lat, double lon, double margin) {
		for (double[] r : rings) if (inside(r, lat, lon)) return true;
		return margin > 0 && edgeMetres(lat, lon) <= margin;
	}

	private static boolean inside(double[] r, double lat, double lon) {
		boolean in = false;
		int n = r.length / 2;
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double yi = r[2 * i], xi = r[2 * i + 1], yj = r[2 * j], xj = r[2 * j + 1];
			if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) in = !in;
		}
		return in;
	}

	/** Distance in metres from LAT LON to the nearest edge. */
	public double edgeMetres(double lat, double lon) {
		double ky = 111200, kx = 111200 * Math.cos(Math.toRadians(lat)), best = Double.MAX_VALUE;
		for (double[] r : rings) {
			int n = r.length / 2;
			for (int i = 0, j = n - 1; i < n; j = i++) {
				double ax = (r[2 * j + 1] - lon) * kx, ay = (r[2 * j] - lat) * ky;
				double bx = (r[2 * i + 1] - lon) * kx, by = (r[2 * i] - lat) * ky;
				double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
				double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / l2));
				double px = ax + t * dx, py = ay + t * dy;
				best = Math.min(best, Math.sqrt(px * px + py * py));
			}
		}
		return best;
	}

	/** One ring per line, "lat,lon;lat,lon;...", as kept on the phone. */
	public String save() {
		StringBuilder b = new StringBuilder();
		for (double[] r : rings) {
			for (int k = 0; k < r.length; k += 2) {
				if (k > 0) b.append(';');
				b.append(String.format(java.util.Locale.US, "%.5f,%.5f", r[k], r[k + 1]));
			}
			b.append('\n');
		}
		return b.toString();
	}

	public static Area load(String text) {
		if (text == null) return null;
		Area a = new Area();
		for (String line : text.split("\n")) {
			String[] pts = line.trim().split(";");
			if (pts.length < 3) continue;
			double[] r = new double[pts.length * 2];
			int n = 0;
			try {
				for (String p : pts) {
					String[] ll = p.split(",");
					r[n++] = Double.parseDouble(ll[0]);
					r[n++] = Double.parseDouble(ll[1]);
				}
			} catch (Exception e) {
				continue;
			}
			a.rings.add(r);
		}
		return a.rings.isEmpty() ? null : a;
	}
}
