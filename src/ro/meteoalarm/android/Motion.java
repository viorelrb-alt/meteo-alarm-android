package ro.meteoalarm.android;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

/**
 * Design audit (2026-11-23): Android's "Remove animations" (Accessibility): when it is on, the
 * weather icons stand still (app, Settings bar and widgets). It sets the three animation scales
 * to 0. Power saving alone does not count (v3.71: with it the icons kept moving on purpose).
 */
final class Motion {
	private Motion() {
	}

	private static long checked;
	private static boolean off;

	/** True when the phone asks for no animations (read at most once a second). */
	static boolean off(Context ctx) {
		long now = android.os.SystemClock.uptimeMillis();
		if (ctx == null || (checked > 0 && now - checked < 1000)) return off;
		checked = now;
		try {
			ContentResolver r = ctx.getContentResolver();
			off = Settings.Global.getFloat(r, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
					&& Settings.Global.getFloat(r, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
					&& Settings.Global.getFloat(r, Settings.Global.WINDOW_ANIMATION_SCALE, 1f) == 0f;
			android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
			if (off && pm != null && pm.isPowerSaveMode()) off = false;
		} catch (Throwable e) {
			off = false;
		}
		return off;
	}
}
