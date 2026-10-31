package ro.meteoalarm.android.core;

/**
 * The Moon, low precision (about a quarter of a degree, a few minutes on the rise and set times):
 * the next moonrise and moonset after a moment, and the phase.
 */
public final class Moon {
	private Moon() {}

	private static final double RAD = Math.PI / 180;

	/** Days since 2000-01-01 12:00 UTC. */
	private static double days(long ms) {
		return ms / 86400000.0 - 10957.5;
	}

	private static double norm(double deg) {
		deg %= 360;
		return deg < 0 ? deg + 360 : deg;
	}

	/** Ecliptic longitude and latitude of the Moon (degrees). */
	private static double[] ecliptic(double d) {
		double L = 218.316 + 13.176396 * d, M = (134.963 + 13.064993 * d) * RAD, F = (93.272 + 13.229350 * d) * RAD;
		double D = (297.850 + 12.190749 * d) * RAD, Ms = (357.529 + 0.98560028 * d) * RAD;
		double lon = L + 6.289 * Math.sin(M) + 1.274 * Math.sin(2 * D - M) + 0.658 * Math.sin(2 * D) + 0.214 * Math.sin(2 * M)
				- 0.186 * Math.sin(Ms) - 0.114 * Math.sin(2 * F);
		double lat = 5.128 * Math.sin(F) + 0.281 * Math.sin(M + F) + 0.278 * Math.sin(M - F) + 0.173 * Math.sin(2 * D - F);
		return new double[] { norm(lon), lat };
	}

	/** The Sun's ecliptic longitude (degrees). */
	private static double sunLon(double d) {
		double g = (357.528 + 0.9856003 * d) * RAD;
		return norm(280.460 + 0.9856474 * d + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g));
	}

	/** The Moon's altitude (degrees, centre, geocentric) at MS over LAT LON. */
	static double altitude(long ms, double lat, double lon) {
		double d = days(ms);
		double[] e = ecliptic(d);
		double l = e[0] * RAD, b = e[1] * RAD, eps = 23.4397 * RAD;
		double ra = Math.atan2(Math.sin(l) * Math.cos(eps) - Math.tan(b) * Math.sin(eps), Math.cos(l));
		double dec = Math.asin(Math.sin(b) * Math.cos(eps) + Math.cos(b) * Math.sin(eps) * Math.sin(l));
		double lst = (280.16 + 360.9856235 * d + lon) * RAD;
		double h = lst - ra, phi = lat * RAD;
		return Math.asin(Math.sin(phi) * Math.sin(dec) + Math.cos(phi) * Math.cos(dec) * Math.cos(h)) / RAD;
	}

	/**
	 * The next moonrise [0] and moonset [1] after FROM (epoch ms) within 36 hours; 0 when there is
	 * none (near the poles, or the Moon stays up or down).
	 */
	public static long[] riseSet(long from, double lat, double lon) {
		// The Moon's centre on the horizon: its parallax (0.95°) less refraction and its radius.
		final double h0 = 0.125;
		long[] r = new long[2];
		long step = 10 * 60000L;
		double prev = altitude(from, lat, lon) - h0;
		for (long t = from + step; t <= from + 36 * 3600000L && (r[0] == 0 || r[1] == 0); t += step) {
			double a = altitude(t, lat, lon) - h0;
			if ((prev < 0) != (a < 0)) {
				long at = t - step + (long) (step * (prev / (prev - a)));
				if (a > 0 && r[0] == 0) r[0] = at;
				if (a < 0 && r[1] == 0) r[1] = at;
			}
			prev = a;
		}
		return r;
	}

	/** The lit fraction (0..1) at MS. */
	public static double lit(long ms) {
		double d = days(ms);
		double[] e = ecliptic(d);
		double el = Math.acos(Math.cos(e[1] * RAD) * Math.cos((e[0] - sunLon(d)) * RAD));
		return (1 - Math.cos(el)) / 2;
	}

	/** True while the lit part grows (new Moon to full Moon). */
	public static boolean waxing(long ms) {
		double d = days(ms);
		return norm(ecliptic(d)[0] - sunLon(d)) < 180;
	}

	/** 0 new, 1 waxing crescent, 2 first quarter, 3 waxing gibbous, 4 full, 5 waning gibbous, 6 last quarter, 7 waning crescent. */
	public static int phase(long ms) {
		double d = days(ms);
		double age = norm(ecliptic(d)[0] - sunLon(d));
		return (int) Math.floor((age + 22.5) / 45) % 8;
	}
}
