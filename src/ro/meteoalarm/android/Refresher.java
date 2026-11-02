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
		final String act = intent == null || intent.getAction() == null ? "widget" : intent.getAction();
		// After an update of the app: the widgets already on the screen were set up long ago (the
		// settings screen must not take them for new ones).
		if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(act)) {
			SharedPreferences.Editor e = Fetcher.prefs(app).edit();
			for (int id : Widget.all(app).keySet()) e.putBoolean("w" + id + "_seen", true);
			e.apply();
			Log.i(app, "APP", "aplicația a fost actualizată la " + MainActivity.VERSION);
		}
		if (Intent.ACTION_BOOT_COMPLETED.equals(act)) Log.i(app, "APP", "telefonul a pornit");
		final PendingResult done = goAsync();
		new Thread(new Runnable() {
			public void run() {
				try {
					refresh(app, act.endsWith("REFRESH") ? "alarmă" : act.endsWith("REPLACED") ? "actualizare" : act.endsWith("COMPLETED") ? "pornire" : act);
				} catch (Exception e) {
					Log.e(app, "FUNDAL", "reîmprospătare", e);
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
	private static void refresh(Context ctx, String why) {
		boolean[] st = state(ctx);
		long every = every(st);
		String state = (st[0] ? "ecran aprins" : "ecran stins") + (st[1] ? ", baterie sub 25%" : "") + (st[2] ? ", noapte" : ", zi");
		if (MainActivity.onScreen) {
			Log.i(ctx, "FUNDAL", why + " · " + state + " · aplicația e deschisă, se ocupă ea");
			return;
		}
		if (every == 0) {
			Log.i(ctx, "FUNDAL", why + " · " + state + " · pauză până se aprinde ecranul");
			return;
		}
		SharedPreferences p = Fetcher.prefs(ctx);
		Cities.City c = Cities.City.fromKey(p.getString("city", ""));
		if (c == null) c = Cities.City.fromKey(p.getString("cacheCity", ""));
		if (c != null) {
			long age = c.key().equals(p.getString("cacheCity", "")) ? System.currentTimeMillis() - p.getLong("fetched", 0) : Long.MAX_VALUE;
			// A minute early is on time (the alarms are not exact).
			if (age < every - 60000L) Log.i(ctx, "FUNDAL", why + " · " + state + " · vremea are " + age / 60000 + " min, următoarea la " + every / 60000 + " min");
			else {
				Log.i(ctx, "FUNDAL", why + " · " + state + " · descarc pentru " + c.name);
				Fetcher.Result r = Fetcher.download(ctx, c);
				Fetcher.save(ctx, c, r);
			}
		}
		// The widgets' own towns.
		for (Cities.City o : Widget.ownTowns(ctx)) if (c == null || !o.same(c)) fetchTown(ctx, o, false);
		Widget.show(ctx);
	}

	/** The forecast of a widget's own town, when older than 30 minutes (or NOW). */
	static void fetchTown(Context ctx, Cities.City c, boolean now) {
		SharedPreferences p = Fetcher.prefs(ctx);
		String f = Widget.townFile(c);
		if (!now && System.currentTimeMillis() - p.getLong(f, 0) < SCREEN_OFF_MS - 60000L) return;
		try {
			String json = Fetcher.forecast(ctx, c, java.util.TimeZone.getDefault().getID());
			Fetcher.write(ctx, f, json);
			p.edit().putLong(f, System.currentTimeMillis()).apply();
			Log.i(ctx, "DATE", "widget: prognoza pentru " + c.name + " OK");
		} catch (Exception e) {
			Log.e(ctx, "DATE", "widget: prognoza pentru " + c.name + " a eșuat: " + Fetcher.reason(e, false));
		}
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
			Log.e(ctx, "FUNDAL", "alarma nu a putut fi pusă", e);
		}
	}
}
