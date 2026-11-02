package ro.meteoalarm.android;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.PowerManager;

import java.util.Calendar;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Weather;

/**
 * Brings the weather in the background (Viorel 2026-11-02), for the widgets and so the app opens
 * fresh: every 15 minutes while the screen is on, every 30 minutes with the screen off, not at all
 * between sunset and sunrise while the screen is off (it waits for the screen to come on), and
 * every hour below 25% battery when not charging. With the app open, the app refreshes itself.
 */
public final class Refresher extends BroadcastReceiver {
	static final long SCREEN_ON_MS = 15 * 60000L, SCREEN_OFF_MS = 30 * 60000L, LOW_BATTERY_MS = 60 * 60000L;
	static final int LOW_BATTERY = 25;
	private static final String ACTION = "ro.meteoalarm.android.REFRESH";

	@Override
	public void onReceive(final Context ctx, Intent intent) {
		final Context app = ctx.getApplicationContext();
		final PendingResult done = goAsync();
		new Thread(new Runnable() {
			public void run() {
				try {
					refresh(app);
				} catch (Exception e) {
					// try again at the next alarm
				} finally {
					schedule(app);
					done.finish();
				}
			}
		}).start();
	}

	/** The phone's state: { screen on, battery low, night (sunset to sunrise) }. */
	private static boolean[] state(Context ctx) {
		PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
		boolean on = pm == null || pm.isInteractive();
		Intent b = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		boolean low = false;
		if (b != null) {
			int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
			boolean plugged = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
			low = lvl >= 0 && lvl * 100 / Math.max(1, sc) < LOW_BATTERY && !plugged;
		}
		return new boolean[] { on, low, night(ctx) };
	}

	/** Between today's sunset and sunrise, from the saved forecast (else 19:00–07:00). */
	static boolean night(Context ctx) {
		String rise = "07:00", set = "19:00";
		try {
			Weather w = Weather.parse(Fetcher.read(ctx, "forecast.json"));
			if (w.sunrise.length() >= 5) rise = w.sunrise;
			if (w.sunset.length() >= 5) set = w.sunset;
		} catch (Exception e) {
			// the usual hours
		}
		Calendar now = Calendar.getInstance();
		String hm = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
		return hm.compareTo(set) >= 0 || hm.compareTo(rise) < 0;
	}

	/** How often now (ms), 0 = paused (night, screen off). */
	private static long every(boolean[] st) {
		if (!st[0] && st[2]) return 0;
		if (st[1]) return LOW_BATTERY_MS;
		return st[0] ? SCREEN_ON_MS : SCREEN_OFF_MS;
	}

	/** Downloads everything for the town shown, if it is due and the app is not open. */
	private static void refresh(Context ctx) {
		if (MainActivity.onScreen) return;
		long every = every(state(ctx));
		if (every == 0) return;
		SharedPreferences p = Fetcher.prefs(ctx);
		Cities.City c = Cities.City.fromKey(p.getString("city", ""));
		if (c == null) c = Cities.City.fromKey(p.getString("cacheCity", ""));
		if (c == null) return;
		long age = c.key().equals(p.getString("cacheCity", "")) ? System.currentTimeMillis() - p.getLong("fetched", 0) : Long.MAX_VALUE;
		// A minute early is on time (the alarms are not exact).
		if (age < every - 60000L) return;
		Fetcher.Result r = Fetcher.download(ctx, c);
		Fetcher.save(ctx, c, r);
		Widget.show(ctx);
	}

	/**
	 * The next look. Paused (night, screen off): an alarm that does not wake the phone, so it comes
	 * as soon as the phone is woken (the screen on), then it fetches if the weather is old.
	 */
	static void schedule(Context ctx) {
		AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
		if (am == null) return;
		Intent i = new Intent(ctx, Refresher.class).setAction(ACTION);
		PendingIntent pi = PendingIntent.getBroadcast(ctx, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		boolean[] st = state(ctx);
		long every = every(st), at = System.currentTimeMillis();
		try {
			if (every == 0) am.set(AlarmManager.RTC, at + SCREEN_ON_MS, pi);
			else {
				// When the weather was fetched counts: the next one when it is due.
				SharedPreferences p = Fetcher.prefs(ctx);
				long due = Math.max(at + 60000L, p.getLong("fetched", 0) + every);
				am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Math.min(due, at + every), pi);
			}
		} catch (Exception e) {
			// no alarms: the app and the widget still refresh by themselves
		}
	}
}
