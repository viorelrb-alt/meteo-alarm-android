package ro.meteoalarm.android;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import ro.meteoalarm.android.core.Farm;
import ro.meteoalarm.android.core.Tr;
import ro.meteoalarm.android.core.Weather;

/**
 * Farmer mode (v2.50, Viorel 2026-11-07): the farmer's parcels, kept only on this phone until the
 * farmer deletes them; each with its last download and its statistics, a line per day, kept 28
 * days. The rain / wind alerts of the parcels that have them on, and the notices promised when
 * Open-Meteo did not answer, come from here, in the background.
 */
final class Parcels {
	static final class Parcel {
		long id;
		String name;
		double lat, lon;
		boolean alerts;

		String line() {
			return id + "\t" + name.replace('\t', ' ').replace('\n', ' ') + "\t" + lat + "\t" + lon + "\t" + (alerts ? 1 : 0);
		}

		static Parcel of(String l) {
			String[] p = l.split("\t");
			if (p.length < 4) return null;
			try {
				Parcel x = new Parcel();
				x.id = Long.parseLong(p[0]);
				x.name = p[1];
				x.lat = Double.parseDouble(p[2]);
				x.lon = Double.parseDouble(p[3]);
				x.alerts = p.length > 4 && p[4].equals("1");
				return x;
			} catch (Exception e) {
				return null;
			}
		}
	}

	/** Background look at the parcels with alerts: every 3 hours, for the next 12 hours. */
	static final long ALERT_MS = 3 * 3600000L;
	static final int ALERT_HOURS = 12;

	private Parcels() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return Fetcher.prefs(ctx);
	}

	/** Farmer mode is on. */
	static boolean farmer(Context ctx) {
		return prefs(ctx).getBoolean("farmer", false);
	}

	static synchronized List<Parcel> all(Context ctx) {
		List<Parcel> out = new ArrayList<Parcel>();
		for (String l : prefs(ctx).getString("parcels", "").split("\n")) {
			Parcel p = Parcel.of(l);
			if (p != null) out.add(p);
		}
		return out;
	}

	static Parcel get(Context ctx, long id) {
		for (Parcel p : all(ctx)) if (p.id == id) return p;
		return null;
	}

	private static synchronized void saveAll(Context ctx, List<Parcel> l) {
		StringBuilder b = new StringBuilder();
		for (Parcel p : l) b.append(p.line()).append('\n');
		prefs(ctx).edit().putString("parcels", b.toString()).commit();
	}

	/** Adds or changes P (by its id). */
	static synchronized void put(Context ctx, Parcel p) {
		List<Parcel> l = all(ctx);
		boolean found = false;
		for (int k = 0; k < l.size(); k++) if (l.get(k).id == p.id) {
			l.set(k, p);
			found = true;
		}
		if (!found) l.add(p);
		saveAll(ctx, l);
	}

	/** Deletes the parcel with its data and statistics. */
	static synchronized void delete(Context ctx, long id) {
		List<Parcel> l = all(ctx);
		for (int k = l.size() - 1; k >= 0; k--) if (l.get(k).id == id) l.remove(k);
		saveAll(ctx, l);
		ctx.deleteFile(dataFile(id));
		ctx.deleteFile(statsFile(id));
		prefs(ctx).edit().remove("pf_" + id).remove("pa_" + id).remove("pn_" + id + "_r").remove("pn_" + id + "_w").remove("pp_" + id).apply();
	}

	static String dataFile(long id) {
		return "parcel-" + id + ".json";
	}

	static String statsFile(long id) {
		return "parcel-" + id + "-stats.txt";
	}

	/** The last download of the parcel (null when none). */
	static Farm.Data data(Context ctx, long id) {
		String s = Fetcher.read(ctx, dataFile(id));
		try {
			return s == null ? null : Farm.parse(s);
		} catch (Exception e) {
			return null;
		}
	}

	/** When the parcel's data came (0 never). */
	static long fetched(Context ctx, long id) {
		return prefs(ctx).getLong("pf_" + id, 0);
	}

	static String stats(Context ctx, long id) {
		return Fetcher.read(ctx, statsFile(id));
	}

	/** The time zone of the parcels (the phone's). */
	static String tz() {
		return TimeZone.getDefault().getID();
	}

	/**
	 * Off the main thread: downloads parcel P (the last 28 days and the next 3) and puts its days in
	 * the statistics; throws when Open-Meteo does not answer.
	 */
	static Farm.Data fetch(Context ctx, Parcel p) throws Exception {
		String json = Weather.get(Farm.url(p.lat, p.lon, tz(), Farm.KEEP_DAYS, 3));
		Farm.Data d = Farm.parse(json);
		Fetcher.write(ctx, dataFile(p.id), json);
		Calendar now = Calendar.getInstance();
		String today = Farm.date(now, 0), oldest = Farm.date(now, -Farm.KEEP_DAYS);
		Fetcher.write(ctx, statsFile(p.id), Farm.merge(stats(ctx, p.id), Farm.days(d, today), oldest));
		prefs(ctx).edit().putLong("pf_" + p.id, System.currentTimeMillis()).apply();
		Log.i(ctx, "FERMIER", "parcela " + p.name + " descărcată: " + d.size() + " ore");
		return d;
	}

	/** Open-Meteo did not answer for parcel ID: the notice comes when its data are downloaded. */
	static void pending(Context ctx, long id) {
		prefs(ctx).edit().putBoolean("pp_" + id, true).apply();
	}

	/** The farmer's forecast of the main screen did not come: notified when it does. */
	static void pendingMain(Context ctx) {
		prefs(ctx).edit().putBoolean("farmPending", true).apply();
	}

	/** Something waits for the background (parcels with alerts, data promised). */
	static boolean anything(Context ctx) {
		for (Parcel p : all(ctx)) if (p.alerts || prefs(ctx).getBoolean("pp_" + p.id, false)) return true;
		return false;
	}

	/**
	 * Off the main thread, from the background refresh and the app's: the promised data of the
	 * parcels (then a notice), and the alerts of the parcels that have them on.
	 */
	static void background(Context ctx) {
		try {
			Lang.apply(ctx);
			SharedPreferences sp = prefs(ctx);
			long now = System.currentTimeMillis();
			for (Parcel p : all(ctx)) {
				boolean pend = sp.getBoolean("pp_" + p.id, false);
				boolean due = p.alerts && now - sp.getLong("pa_" + p.id, 0) >= ALERT_MS - 60000L;
				if (!pend && !due) continue;
				Farm.Data d;
				try {
					d = fetch(ctx, p);
				} catch (Exception e) {
					Log.w(ctx, "FERMIER", "parcela " + p.name + ": Open-Meteo nu a răspuns (" + Fetcher.reason(e, false) + ")");
					if (due) sp.edit().putLong("pa_" + p.id, now - ALERT_MS + 30 * 60000L).apply();
					continue;
				}
				if (pend) {
					sp.edit().remove("pp_" + p.id).apply();
					Notifier.post(ctx, 3000 + (int) (p.id & 0xFFF), Tr.t("Farmer · ", "Fermier · ") + p.name,
							Tr.t("Open-Meteo answers again: the parcel's data have been downloaded.", "Open-Meteo răspunde din nou: datele parcelei au fost descărcate."));
					Log.i(ctx, "NOTIF", "parcela " + p.name + ": date descărcate după pauza Open-Meteo");
				}
				if (p.alerts) {
					sp.edit().putLong("pa_" + p.id, now).apply();
					check(ctx, p, d);
				}
			}
		} catch (Exception e) {
			Log.e(ctx, "FERMIER", "verificarea parcelelor", e);
		}
	}

	/** Parcel P's alerts: rain or strong wind within 12 hours, each spell notified once. */
	static void check(Context ctx, Parcel p, Farm.Data d) {
		int cur = d.nowIndex(Calendar.getInstance());
		if (cur < 0) return;
		int[] a = Farm.alert(d, cur, ALERT_HOURS);
		SharedPreferences sp = prefs(ctx);
		Cities0 u = new Cities0(ctx);
		if (a[0] >= 0) {
			// One notice per rain spell: a new one only 6 hours after the last rain hour notified.
			String key = "pn_" + p.id + "_r";
			long last = sp.getLong(key, 0), at = System.currentTimeMillis();
			if (at - last > 6 * 3600000L) {
				double mm = 0;
				int pmax = 0;
				for (int k = a[0]; k < Math.min(d.size(), cur + ALERT_HOURS + 1); k++) {
					mm += Double.isNaN(d.precip[k]) ? 0 : d.precip[k];
					if (!Double.isNaN(d.prob[k])) pmax = Math.max(pmax, (int) Math.round(d.prob[k]));
				}
				String body = Tr.t("Rain expected from ", "Ploaie prognozată de la ") + u.clock(d.hourOf(a[0])) + " · " + pmax + "% · "
						+ u.mm(mm) + Tr.t(" in the next 12 hours", " în următoarele 12 ore");
				Notifier.post(ctx, 3100 + (int) (p.id & 0xFFF), Tr.t("Rain · ", "Ploaie · ") + p.name, body);
				Log.i(ctx, "NOTIF", "parcela " + p.name + ": " + body);
			}
			sp.edit().putLong(key, at).apply();
		}
		if (a[1] >= 0) {
			String key = "pn_" + p.id + "_w";
			long last = sp.getLong(key, 0), at = System.currentTimeMillis();
			if (at - last > 6 * 3600000L) {
				double gmax = 0, wmax = 0;
				for (int k = a[1]; k < Math.min(d.size(), cur + ALERT_HOURS + 1); k++) {
					if (!Double.isNaN(d.gust[k])) gmax = Math.max(gmax, d.gust[k]);
					if (!Double.isNaN(d.wind[k])) wmax = Math.max(wmax, d.wind[k]);
				}
				String body = Tr.t("Strong wind from ", "Vânt puternic de la ") + u.clock(d.hourOf(a[1])) + " · " + u.kmh(wmax)
						+ Tr.t(", gusts ", ", rafale ") + u.kmh(gmax);
				Notifier.post(ctx, 3200 + (int) (p.id & 0xFFF), Tr.t("Wind · ", "Vânt · ") + p.name, body);
				Log.i(ctx, "NOTIF", "parcela " + p.name + ": " + body);
			}
			sp.edit().putLong(key, at).apply();
		}
	}

	/** Units of the phone's region for the notices (metric or imperial, 24 or 12 hours). */
	static final class Cities0 {
		final boolean imp, clk12;

		Cities0(Context ctx) {
			ro.meteoalarm.android.core.Cities.City c = ro.meteoalarm.android.core.Cities.City.fromKey(prefs(ctx).getString("city", ""));
			imp = Region.imperial(ctx, c);
			clk12 = Region.clk12(ctx, c);
		}

		String clock(String hm) {
			return ro.meteoalarm.android.core.Texts.clock(hm, clk12);
		}

		String mm(double v) {
			return imp ? String.format(java.util.Locale.US, "%.2f in", v / 25.4) : String.format(java.util.Locale.US, "%.1f mm", v).replace('.', Tr.dot());
		}

		String kmh(double v) {
			return imp ? Math.round(v * 0.621371) + " mph" : Math.round(v) + " km/h";
		}
	}
}
