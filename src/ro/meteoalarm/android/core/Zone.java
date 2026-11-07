package ro.meteoalarm.android.core;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * The time zone the screen shows (v2.50, Viorel 2026-11-07): the phone's, or a US town's own when
 * one is chosen (the hidden test mode shows the town's local time, not the phone's).
 */
public final class Zone {
	private static volatile TimeZone tz = TimeZone.getDefault();

	private Zone() {
	}

	/** Sets the zone by its id (null or unknown: the phone's). */
	public static void set(String id) {
		tz = id == null ? TimeZone.getDefault() : TimeZone.getTimeZone(id);
	}

	public static TimeZone get() {
		return tz;
	}

	/** Now, in the zone shown. */
	public static Calendar now() {
		return Calendar.getInstance(tz);
	}

	/** The zone differs from the phone's at this moment. */
	public static boolean foreign() {
		long t = System.currentTimeMillis();
		return tz.getOffset(t) != TimeZone.getDefault().getOffset(t);
	}
}
