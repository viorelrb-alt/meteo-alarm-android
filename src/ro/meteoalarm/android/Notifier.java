package ro.meteoalarm.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;
import java.util.List;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Quakes;
import ro.meteoalarm.android.core.Texts;
import ro.meteoalarm.android.core.Tr;
import ro.meteoalarm.android.core.Warnings;
import ro.meteoalarm.android.core.Weather;

/**
 * Notifications (Viorel 2026-11-04), only with the user's consent and only for: rain or snow
 * starting (now or within the hour), new MeteoAlarm warnings for the town, and earthquakes of
 * magnitude 3 or more in the town's country. Each event is notified once.
 */
final class Notifier {
	private static final String CHANNEL = "alerts";
	/** How long a notified event is remembered (so it is not notified again). */
	private static final long KEEP_MS = 3 * 86400000L;

	private Notifier() {
	}

	static SharedPreferences prefs(Context ctx) {
		return Fetcher.prefs(ctx);
	}

	/** The user said yes (in the app) and the phone allows them. */
	static boolean on(Context ctx) {
		return prefs(ctx).getBoolean("notify", false) && allowed(ctx);
	}

	/** The phone lets the app notify (Android 13+: the permission; older: not blocked in the settings). */
	static boolean allowed(Context ctx) {
		if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
			return false;
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		return nm == null || Build.VERSION.SDK_INT < 24 || nm.areNotificationsEnabled();
	}

	private static void channel(Context ctx, NotificationManager nm) {
		if (Build.VERSION.SDK_INT < 26) return;
		NotificationChannel ch = new NotificationChannel(CHANNEL, Tr.t("Weather, warnings and earthquakes", "Vreme, avertizări și cutremure"),
				NotificationManager.IMPORTANCE_DEFAULT);
		ch.setDescription(Tr.t("Rain or snow starting, new weather warnings, earthquakes in your country.",
				"Ploaie sau ninsoare care începe, avertizări meteo noi, cutremure în țara ta."));
		nm.createNotificationChannel(ch);
	}

	/** After a download for the app's town C: the new events, notified once each. */
	static void check(Context ctx, Cities.City c) {
		try {
			Lang.apply(ctx);
			if (!on(ctx) || c == null) return;
			SharedPreferences p = prefs(ctx);
			long now = System.currentTimeMillis();
			Texts T = new Texts(Tr.lang());
			boolean clk12 = p.getBoolean("clk12", false);
			// Rain or snow: falling now, or starting within the hour.
			String f = Fetcher.read(ctx, "forecast.json");
			if (f != null) {
				Weather w = Weather.parse(f);
				boolean wet = w.wetNow() || (w.wet.length > 1 && w.wet[1]);
				if (wet) {
					boolean snow = w.wetNow() ? Weather.snowCode(w.code) : w.snowy.length > 1 && w.snowy[1];
					// One notice per spell: again only after 3 dry hours.
					if (now - p.getLong("nPrecipLast", 0) > 3 * 3600000L) {
						String title = snow ? Tr.t("Snow", "Ninsoare") : Tr.t("Rain", "Ploaie");
						String body = (w.wetNow() ? (snow ? Tr.t("It is snowing in ", "Ninge în ") : Tr.t("It is raining in ", "Plouă în "))
								: (snow ? Tr.t("Snow is expected within the hour in ", "Ninsoare în următoarea oră în ") : Tr.t("Rain is expected within the hour in ", "Ploaie în următoarea oră în ")))
								+ c.name + (w.rainEnd.length() > 0 ? " · " + T.until + " " + Texts.clock(w.rainEnd, clk12) : "");
						post(ctx, 1, title, body);
						Log.i(ctx, "NOTIF", "precipitații: " + body);
					}
					p.edit().putLong("nPrecipLast", now).apply();
				}
			}
			// New warnings for the town.
			String x = Fetcher.read(ctx, "warn.xml");
			if (x != null) {
				List<Warnings.Warn> ws = Warnings.parse(x, c.warnNames(), now);
				for (Warnings.Warn wr : ws) {
					String key = "nW_" + Warnings.fold(c.key() + "_" + wr.level + "_" + wr.type + "_" + wr.from).replaceAll("[^a-z0-9_]+", "");
					if (p.getLong(key, 0) > 0) continue;
					p.edit().putLong(key, now).apply();
					String lvl = T.warn[Math.max(0, Math.min(2, wr.level - 1))];
					String title = Tr.t("Warning", "Avertizare") + " " + T.warnCode.toLowerCase() + " " + lvl.toLowerCase() + " · " + c.name;
					String body = T.warnType(wr.type) + " · " + T.weekdayTime(wr.from, clk12) + " – " + T.weekdayTime(wr.until, clk12);
					post(ctx, 100 + (key.hashCode() & 0xFFFF), title, body);
					Log.i(ctx, "NOTIF", "avertizare: " + title + " · " + body);
				}
			}
			// Earthquakes in the country.
			for (Quakes.Quake q : Fetcher.quakes(ctx, c)) {
				if (q.time < p.getLong("notifySince", now)) continue;
				String key = "nQ_" + (q.id.length() > 0 ? q.id : Long.toString(q.time)).replaceAll("[^A-Za-z0-9_]+", "");
				if (p.getLong(key, 0) > 0) continue;
				p.edit().putLong(key, now).apply();
				String title = Tr.t("Earthquake M", "Cutremur M") + String.format(java.util.Locale.US, "%.1f", q.mag) + " · " + ro.meteoalarm.android.core.Screen.titleCase(q.region);
				String body = T.weekdayTime(q.time, clk12) + " · " + Math.round(q.dist) + " km " + Tr.t("from ", "de ") + c.name
						+ (q.felt >= 2 ? " · " + Tr.t("probably felt: ", "probabil resimțit: ") + ro.meteoalarm.android.core.Screen.roman(q.felt) : "");
				post(ctx, 1000 + (key.hashCode() & 0xFFFF), title, body);
				Log.i(ctx, "NOTIF", "cutremur: " + title + " · " + body);
			}
			clean(p, now);
		} catch (Exception e) {
			Log.e(ctx, "NOTIF", "verificarea notificărilor", e);
		}
	}

	/** Forgets events notified more than KEEP_MS ago. */
	private static void clean(SharedPreferences p, long now) {
		SharedPreferences.Editor e = null;
		for (java.util.Map.Entry<String, ?> en : p.getAll().entrySet()) {
			String k = en.getKey();
			if (!(k.startsWith("nW_") || k.startsWith("nQ_")) || !(en.getValue() instanceof Long)) continue;
			if (now - (Long) en.getValue() < KEEP_MS) continue;
			if (e == null) e = p.edit();
			e.remove(k);
		}
		if (e != null) e.apply();
	}

	@SuppressWarnings("deprecation")
	private static void post(Context ctx, int id, String title, String body) {
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		if (nm == null) return;
		channel(ctx, nm);
		Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent pi = PendingIntent.getActivity(ctx, 3, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CHANNEL) : new Notification.Builder(ctx);
		b.setSmallIcon(R.drawable.ic_notify).setContentTitle(title).setContentText(body).setContentIntent(pi).setAutoCancel(true)
				.setStyle(new Notification.BigTextStyle().bigText(body)).setWhen(System.currentTimeMillis()).setShowWhen(true);
		try {
			nm.notify(id, b.build());
		} catch (SecurityException e) {
			Log.e(ctx, "NOTIF", "telefonul nu permite notificarea", e);
		}
	}

	/** Test notice, after the user said yes. */
	static void hello(Context ctx) {
		post(ctx, 2, "Meteo Alarm", Tr.t("Notifications are on: rain or snow, weather warnings and earthquakes in your country.",
				"Notificările sunt pornite: ploaie sau ninsoare, avertizări meteo și cutremure în țara ta."));
	}

	/** Unused helper kept for the hour of day (night quiet hours may come later). */
	static int hour() {
		return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
	}
}
