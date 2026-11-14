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
import ro.meteoalarm.android.core.Nws;
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
	/** v5.00 (Viorel): earthquake notices only within this distance of your place (km). */
	static final double QUAKE_KM = 300;

	/** ☰ › Cutremure: notices of earthquakes near your place (on unless turned off). */
	static boolean quakes(Context ctx) {
		return prefs(ctx).getBoolean("quakeNotify", true);
	}

	/** ☰ › Tornade (US): tornado notices for your town (on unless turned off). */
	static boolean tornadoes(Context ctx) {
		return prefs(ctx).getBoolean("tornadoNotify", true);
	}

	/** US tornado and other extreme warnings (v2.30): high importance, alarm sound, vibration. */
	private static final String CHANNEL_EXTREME = "extreme";
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
		ch.setDescription(Tr.t("Rain or snow starting, new weather warnings, earthquakes around you.",
				"Ploaie sau ninsoare care începe, avertizări meteo noi, cutremure din jurul tău."));
		nm.createNotificationChannel(ch);
		// Only where the US part is shown (v2.30).
		if (!Region.showUS(ctx)) return;
		NotificationChannel ex = new NotificationChannel(CHANNEL_EXTREME, Tr.t("Tornado and extreme warnings", "Tornade și avertizări extreme"),
				NotificationManager.IMPORTANCE_HIGH);
		ex.setDescription(Tr.t("United States: tornado, severe thunderstorm, flash flood and other dangerous warnings of the National Weather Service for your town.",
				"Statele Unite: avertizările de tornadă, furtună severă, viitură și alte fenomene periculoase ale National Weather Service pentru localitatea ta."));
		ex.enableVibration(true);
		ex.setVibrationPattern(new long[] { 0, 800, 400, 800, 400, 800 });
		ex.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
		android.net.Uri alarm = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
		if (alarm != null) ex.setSound(alarm, new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM)
				.setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
		nm.createNotificationChannel(ex);
	}

	/** After a download for the app's town C: the new events, notified once each. */
	static void check(Context ctx, Cities.City c) {
		try {
			Lang.apply(ctx);
			if (!on(ctx) || c == null) return;
			// The times in the town's own zone (a US town's local time, v2.50).
			ro.meteoalarm.android.core.Zone.set(Fetcher.tz(c));
			SharedPreferences p = prefs(ctx);
			long now = System.currentTimeMillis();
			// Only for the town chosen now (v2.50, Viorel 2026-11-07: a US earthquake came while the
			// app showed Călărași): a download for a town left meanwhile notifies nothing, and US
			// events only where the US part is shown (a phone in the US, or the test mode).
			Cities.City sel = Cities.City.fromKey(p.getString("city", ""));
			if (sel != null && !sel.same(c)) {
				Log.i(ctx, "NOTIF", "descărcare pentru " + c.name + ", dar localitatea aleasă e " + sel.name + ": fără notificări");
				return;
			}
			if (c.us() ? !Region.showUS(ctx) : !Region.showEU(ctx)) {
				Log.i(ctx, "NOTIF", c.name + " nu e în partea arătată acum (Europa / SUA): fără notificări");
				return;
			}
			// A newly chosen town: its earthquakes from before the choice are not notified.
			if (!c.key().equals(p.getString("nCity", ""))) p.edit().putString("nCity", c.key()).putLong("nCityAt", now).apply();
			long quakesSince = Math.max(p.getLong("notifySince", now), p.getLong("nCityAt", now));
			Texts T = new Texts(Tr.lang());
			boolean clk12 = Region.clk12(ctx, c), imp = Region.imperial(ctx, c);
			// Rain or snow: falling now, or starting within the hour.
			String f = Fetcher.read(ctx, "forecast.json");
			if (f != null) {
				Weather w = Weather.parse(f);
				boolean wet = w.wetNow() || (w.wet.length > 1 && w.wet[1]);
				if (wet) {
					boolean snow = w.wetNow() ? Weather.snowCode(w.code) : w.snowy.length > 1 && w.snowy[1];
					// One notice per spell: again only after 3 dry hours.
					if (now - p.getLong("nPrecipLast", 0) > 3 * 3600000L) {
						// v3.40 (Viorel): like the phone's weather app: "Town | Rain", "Rain possible within the hour".
						String title = c.name + " | " + (snow ? Tr.t("Snow", "Ninsoare") : Tr.t("Rain", "Ploaie"));
						String body = (w.wetNow() ? (snow ? Tr.t("It is snowing now", "Ninge acum") : Tr.t("It is raining now", "Plouă acum"))
								: (snow ? Tr.t("Snow possible within the hour", "Ninsoare posibilă în următoarea oră") : Tr.t("Rain possible within the hour", "Ploaie posibilă în următoarea oră"))
										+ (w.probs.length > 1 && w.probs[1] > 0 ? " · " + w.probs[1] + "%" : ""))
								+ (w.rainEnd.length() > 0 ? " · " + T.until + " " + Texts.clock(w.rainEnd, clk12) : "");
						post(ctx, 1, title, body);
						Log.i(ctx, "NOTIF", "precipitații: " + body);
					}
					p.edit().putLong("nPrecipLast", now).apply();
				}
				tomorrow(ctx, c, w, p);
			}
			// New warnings for the town.
			{
				List<Warnings.Warn> ws = Fetcher.warnings(ctx, c);
				for (Warnings.Warn wr : ws) {
					// v5.00 (Viorel): ☰ › Tornade off: no tornado notices (the other NWS warnings stay).
					if (wr.nws && !tornadoes(ctx) && wr.type.toLowerCase(java.util.Locale.US).contains("tornado")) continue;
					String key = "nW_" + Warnings.fold(c.key() + "_" + wr.level + "_" + wr.type + "_" + wr.from).replaceAll("[^a-z0-9_]+", "");
					if (p.getLong(key, 0) > 0) continue;
					p.edit().putLong(key, now).apply();
					String lvl = T.warn[Math.max(0, Math.min(2, wr.level - 1))];
					String title = c.name + " | " + Tr.t("Warning", "Avertizare") + " " + T.warnCode.toLowerCase() + " " + lvl.toLowerCase();
					String body = T.warnType(wr.type) + " · " + T.weekdayTime(wr.from, clk12) + " – " + T.weekdayTime(wr.until, clk12);
					boolean loud = false;
					if (wr.nws) {
						// United States: the NWS event as the title; the dangerous ones on the loud channel.
						loud = Nws.extreme(wr.type);
						title = (loud ? "⚠ " : "") + c.name + " | " + Nws.event(wr.type).toUpperCase();
						body = T.weekdayTime(wr.from, clk12) + " – " + T.weekdayTime(wr.until, clk12)
								+ (wr.headline.length() > 0 ? "\n" + wr.headline : "") + (wr.instr.length() > 0 ? "\n" + wr.instr : "");
					}
					post(ctx, 100 + (key.hashCode() & 0xFFFF), title, body, loud);
					Log.i(ctx, "NOTIF", "avertizare: " + title + " · " + body);
				}
			}
			// v3.30 (Viorel): the app's own information (frost, freeze, fog, freezing rain, strong
			// gusts) for the town, in a notice of its own; Europe only (the US has the NWS advisories).
			if (!c.us() && f != null) info(ctx, c, Weather.parse(f), Fetcher.warnings(ctx, c), clk12, imp);
			// Earthquakes: v5.00 (Viorel) only within QUAKE_KM of the place, and only while ☰ › Cutremure is on.
			for (Quakes.Quake q : quakes(ctx) ? Fetcher.quakes(ctx, c) : new java.util.ArrayList<Quakes.Quake>()) {
				if (q.time < quakesSince) continue;
				if (q.dist > QUAKE_KM) continue;
				// US: the box shows from 2.5, the notice comes from 3 (as in Europe).
				if (c.us() && q.mag < Quakes.MIN_MAG) continue;
				String key = "nQ_" + (q.id.length() > 0 ? q.id : Long.toString(q.time)).replaceAll("[^A-Za-z0-9_]+", "");
				if (p.getLong(key, 0) > 0) continue;
				p.edit().putLong(key, now).apply();
				String title = Tr.t("Earthquake M", "Cutremur M") + String.format(java.util.Locale.US, "%.1f", q.mag) + " · " + ro.meteoalarm.android.core.Screen.place(q.region, imp);
				String body = T.weekdayTime(q.time, clk12) + " · " + (imp ? Math.round(q.dist * 0.621371) + " mi " : Math.round(q.dist) + " km ") + Tr.t("from ", "de ") + c.name
						+ (q.felt >= 2 ? " · " + Tr.t("probably felt: ", "probabil resimțit: ") + ro.meteoalarm.android.core.Screen.roman(q.felt) : "");
				post(ctx, 1000 + (key.hashCode() & 0xFFFF), title, body);
				Log.i(ctx, "NOTIF", "cutremur: " + title + " · " + body);
			}
			clean(p, now);
		} catch (Exception e) {
			Log.e(ctx, "NOTIF", "verificarea notificărilor", e);
		}
	}

	/** The town's own information found in the next 25 hours; each spell told once. */
	/**
	 * v3.40 (Viorel): after 8 in the evening, once a day, a notice when rain (or snow) is possible
	 * tomorrow in town C ("Town | Rain", "Rain possible tomorrow · 70%").
	 */
	private static void tomorrow(Context ctx, Cities.City c, Weather w, SharedPreferences p) {
		Calendar now = Calendar.getInstance();
		if (now.get(Calendar.HOUR_OF_DAY) < 20) return;
		String today = ro.meteoalarm.android.core.Farm.date(now, 0), tom = ro.meteoalarm.android.core.Farm.date(now, 1);
		if (today.equals(p.getString("nTom", ""))) return;
		int i = w.dayDates.indexOf(tom);
		if (i < 0) return;
		p.edit().putString("nTom", today).apply();
		int pop = w.dayPop.get(i), code = w.dayCodes.get(i);
		if (pop < Weather.RAIN_PROBABILITY || code < 50) {
			// v3.51: the evening check is in the log even when nothing is told.
			Log.i(ctx, "NOTIF", "mâine: fără anunț (" + c.name + ", prob. " + pop + "% < " + Weather.RAIN_PROBABILITY + "% sau cod " + code + ")");
			return;
		}
		boolean snow = Weather.snowCode(code);
		String body = (snow ? Tr.t("Snow possible tomorrow", "Ninsoare posibilă mâine") : Tr.t("Rain possible tomorrow", "Ploaie posibilă mâine")) + " · " + pop + "%";
		post(ctx, 1600, c.name + " | " + (snow ? Tr.t("Snow", "Ninsoare") : Tr.t("Rain", "Ploaie")), body);
		Log.i(ctx, "NOTIF", "mâine: " + body);
	}

	private static void info(Context ctx, Cities.City c, Weather w, List<Warnings.Warn> ws, boolean clk12, boolean imp) {
		int n = Math.min(w.tileTemp.length, 25);
		if (n == 0) return;
		long nowMs = System.currentTimeMillis();
		String off = "";
		for (Warnings.Warn wr : ws) if (!wr.nws && wr.until > nowMs) off += wr.type + ",";
		double[] code = new double[n], day = new double[n];
		for (int k = 0; k < n; k++) {
			code[k] = k < w.tileCode.length ? w.tileCode[k] : 0;
			day[k] = k < w.tileDay.length && !w.tileDay[k] ? 0 : 1;
		}
		java.util.Calendar h0 = ro.meteoalarm.android.core.Zone.now();
		int month = h0.get(java.util.Calendar.MONTH) + 1;
		h0.set(java.util.Calendar.MINUTE, 0);
		h0.set(java.util.Calendar.SECOND, 0);
		h0.set(java.util.Calendar.MILLISECOND, 0);
		long t0 = h0.getTimeInMillis();
		int today = h0.get(java.util.Calendar.DAY_OF_YEAR);
		List<ro.meteoalarm.android.core.Screen.OwnHit> hits = ro.meteoalarm.android.core.Screen.ownHits(w.tileTemp, w.tileCloud, w.tileWind, w.tileGust, code, day, 0, n, month, off);
		SharedPreferences p = prefs(ctx);
		SharedPreferences.Editor ed = p.edit();
		StringBuilder body = new StringBuilder();
		for (ro.meteoalarm.android.core.Screen.OwnHit h : hits) {
			long a = t0 + h.a * 3600000L, b = t0 + (h.b + 1) * 3600000L;
			String key = "nI_" + Warnings.fold(c.key()).replaceAll("[^a-z0-9_]+", "") + "_" + h.key;
			long told = p.getLong(key, 0);
			// A spell starting before the end of the last one told is the same one.
			if (told > 0 && a < told) {
				if (b > told) ed.putLong(key, b);
				continue;
			}
			ed.putLong(key, b);
			java.util.Calendar ca = (java.util.Calendar) h0.clone(), cb = (java.util.Calendar) h0.clone();
			ca.setTimeInMillis(a);
			cb.setTimeInMillis(b);
			String name = ro.meteoalarm.android.core.Screen.ownName(h.key);
			String when = (ca.get(java.util.Calendar.DAY_OF_YEAR) == today ? "" : Tr.t("tomorrow ", "mâine "))
					+ Texts.clock(String.format(java.util.Locale.US, "%02d:00", ca.get(java.util.Calendar.HOUR_OF_DAY)), clk12) + "–"
					+ Texts.clock(String.format(java.util.Locale.US, "%02d:00", cb.get(java.util.Calendar.HOUR_OF_DAY)), clk12);
			String val = h.key.equals("freeze") || h.key.equals("frost") ? " · " + Tr.t("lowest ", "minima ") + (imp ? Math.round(h.val * 9 / 5 + 32) : Math.round(h.val)) + "°"
					: h.key.equals("wind") ? " · " + Tr.t("gusts ", "rafale ") + (imp ? Math.round(h.val * 0.621371) + " mph" : Math.round(h.val) + " km/h") : "";
			if (body.length() > 0) body.append("\n");
			body.append(name.substring(0, 1)).append(name.substring(1).toLowerCase()).append(": ").append(when).append(val);
		}
		ed.apply();
		if (body.length() == 0) return;
		post(ctx, 1500, Parcels.title(c.name, Tr.t("Information · ", "Informare · ")), body.toString());
		Log.i(ctx, "NOTIF", "informare " + c.name + ": " + body.toString().replace('\n', ';'));
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

	/**
	 * v3.90 (Viorel): another town was chosen: the notices of OLD (rain, warnings, information,
	 * tomorrow; their titles start with "Town |") are removed. Parcels and earthquakes stay.
	 */
	static void clearTown(Context ctx, Cities.City old) {
		try {
			NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
			if (nm == null || Build.VERSION.SDK_INT < 23) return;
			String a = old.name + " |", b = "⚠ " + old.name + " |";
			int n = 0;
			for (android.service.notification.StatusBarNotification sb : nm.getActiveNotifications()) {
				int id = sb.getId();
				CharSequence t = sb.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
				String title = t == null ? "" : t.toString();
				if (id == 1 || id == 1500 || id == 1600 || title.startsWith(a) || title.startsWith(b)) {
					nm.cancel(sb.getTag(), id);
					n++;
				}
			}
			if (n > 0) Log.i(ctx, "NOTIF", "localitate schimbată: " + n + " notificări pentru " + old.name + " șterse");
		} catch (Exception e) {
			Log.e(ctx, "NOTIF", "notificările vechi nu au putut fi șterse", e);
		}
	}

	static void post(Context ctx, int id, String title, String body) {
		post(ctx, id, title, body, false);
	}

	@SuppressWarnings("deprecation")
	private static void post(Context ctx, int id, String title, String body, boolean loud) {
		NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		if (nm == null) return;
		channel(ctx, nm);
		Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent pi = PendingIntent.getActivity(ctx, 3, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, loud ? CHANNEL_EXTREME : CHANNEL) : new Notification.Builder(ctx);
		if (loud) {
			b.setPriority(Notification.PRIORITY_MAX).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC);
			if (Build.VERSION.SDK_INT < 26) b.setDefaults(Notification.DEFAULT_ALL);
		}
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
		post(ctx, 2, "Meteo Alarm", Tr.t("Notifications are on: rain or snow, weather warnings and earthquakes around you.",
				"Notificările sunt pornite: ploaie sau ninsoare, avertizări meteo și cutremure din jurul tău."));
	}

	/** Unused helper kept for the hour of day (night quiet hours may come later). */
	static int hour() {
		return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
	}
}
