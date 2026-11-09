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

	/**
	 * The parcels in the order of their names, the numbers in them by value ("parcela 9" before
	 * "parcela 96/5" before "parcela 120"), letters without case and diacritics (Viorel 2026-11-07).
	 */
	static List<Parcel> sorted(Context ctx) {
		List<Parcel> l = all(ctx);
		java.util.Collections.sort(l, new java.util.Comparator<Parcel>() {
			public int compare(Parcel a, Parcel b) {
				int c = natural(a.name, b.name);
				return c != 0 ? c : Long.compare(a.id, b.id);
			}
		});
		return l;
	}

	static int natural(String a, String b) {
		a = ro.meteoalarm.android.core.Warnings.fold(a);
		b = ro.meteoalarm.android.core.Warnings.fold(b);
		int i = 0, j = 0;
		while (i < a.length() && j < b.length()) {
			char x = a.charAt(i), y = b.charAt(j);
			if (Character.isDigit(x) && Character.isDigit(y)) {
				int i2 = i, j2 = j;
				while (i2 < a.length() && Character.isDigit(a.charAt(i2))) i2++;
				while (j2 < b.length() && Character.isDigit(b.charAt(j2))) j2++;
				String na = a.substring(i, i2).replaceFirst("^0+(?=.)", ""), nb = b.substring(j, j2).replaceFirst("^0+(?=.)", "");
				if (na.length() != nb.length()) return na.length() - nb.length();
				int c = na.compareTo(nb);
				if (c != 0) return c;
				i = i2;
				j = j2;
			} else {
				if (x != y) return x - y;
				i++;
				j++;
			}
		}
		return (a.length() - i) - (b.length() - j);
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
		for (String part : new String[] { "forecast.json", "uv.json", "air.json", "warn.xml" }) ctx.deleteFile(Fetcher.parcelFile(id, part));
		prefs(ctx).edit().remove("pmf_" + id).remove("pms_" + id).remove("pf_" + id).remove("pa_" + id).remove("pn_" + id + "_r").remove("pn_" + id + "_w").remove("pn_" + id + "_s").remove("pd_" + id).remove("pt_" + id).remove("pp_" + id).apply();
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
		String json = Weather.get(Farm.url(p.lat, p.lon, tz(), Farm.KEEP_DAYS, Farm.AHEAD));
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
				// v3.30 (Viorel): a parcel's notifications only in farmer mode.
				boolean due = p.alerts && farmer(ctx) && now - sp.getLong("pa_" + p.id, 0) >= ALERT_MS - 60000L;
				boolean on = p.alerts && farmer(ctx);
				if (!pend && !due) {
					// v3.40 (Viorel): rain in the next hour and, in the evening, tomorrow's rain, from the saved data.
					if (on) {
						Farm.Data d0 = data(ctx, p.id);
						if (d0 != null && now - fetched(ctx, p.id) < 6 * 3600000L) soon(ctx, p, d0);
					}
					continue;
				}
				Farm.Data d;
				try {
					d = fetch(ctx, p);
				} catch (Exception e) {
					Log.w(ctx, "FERMIER", "parcela " + p.name + ": Open-Meteo nu a răspuns (" + Fetcher.reason(e, false) + ")");
					if (due) sp.edit().putLong("pa_" + p.id, now - ALERT_MS + 30 * 60000L).apply();
					continue;
				}
				if (pend) {
					// v3.40 (Viorel): no notice any more when the data come after a pause of Open-Meteo.
					sp.edit().remove("pp_" + p.id).apply();
					Log.i(ctx, "FERMIER", "parcela " + p.name + ": date descărcate după pauza Open-Meteo");
				}
				if (on) soon(ctx, p, d);
				if (due) {
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
		// v3.30 (Viorel): snow in the next 12 hours, a notice of its own (it stands for the rain one).
		int snowAt = -1;
		double cm = 0;
		if (d.snow != null) for (int k = cur; k < Math.min(d.size(), cur + ALERT_HOURS + 1); k++) {
			if (Double.isNaN(d.snow[k]) || d.snow[k] < 0.1) continue;
			if (snowAt < 0) snowAt = k;
			cm += d.snow[k];
		}
		if (snowAt >= 0) {
			String key = "pn_" + p.id + "_s";
			long last = sp.getLong(key, 0), at = System.currentTimeMillis();
			if (at - last > 6 * 3600000L) {
				String body = Tr.t("Snow expected from ", "Ninsoare prognozată de la ") + u.clock(d.hourOf(snowAt)) + " · "
						+ (u.imp ? String.format(java.util.Locale.US, "%.1f in", cm / 2.54) : String.format(java.util.Locale.US, "%.1f cm", cm).replace('.', Tr.dot()))
						+ Tr.t(" in the next 12 hours", " în următoarele 12 ore");
				Notifier.post(ctx, 3400 + (int) (p.id & 0xFFF), title(p.name, Tr.t("Snow · ", "Ninsoare · ")), body);
				Log.i(ctx, "NOTIF", "parcela " + p.name + ": " + body);
			}
			sp.edit().putLong(key, at).apply();
		}
		drought(ctx, p, d, cur);
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
				Notifier.post(ctx, 3200 + (int) (p.id & 0xFFF), title(p.name, Tr.t("Wind · ", "Vânt · ")), body);
				Log.i(ctx, "NOTIF", "parcela " + p.name + ": " + body);
			}
			sp.edit().putLong(key, at).apply();
		}
		info(ctx, p, d, cur, u);
	}

	/**
	 * v3.40 (Viorel): rain (or snow) possible in the next hour on parcel P, like the phone's weather
	 * app ("Parcel | Rain", "Rain possible within the hour"), again only after 3 dry hours; and after
	 * 8 in the evening, once a day, rain (or snow) possible tomorrow.
	 */
	static void soon(Context ctx, Parcel p, Farm.Data d) {
		Calendar now = Calendar.getInstance();
		int cur = d.nowIndex(now);
		if (cur < 0) return;
		SharedPreferences sp = prefs(ctx);
		long at = System.currentTimeMillis();
		int k = cur + 1;
		if (k < d.size()) {
			double pr = d.prob[k], mm = d.precip[k];
			boolean wet = (!Double.isNaN(pr) && pr >= Weather.RAIN_PROBABILITY) || (!Double.isNaN(mm) && mm >= 0.1);
			if (wet) {
				boolean sn = d.snow != null && !Double.isNaN(d.snow[k]) && d.snow[k] > 0;
				String key = "pn_" + p.id + "_r";
				if (at - sp.getLong(key, 0) > 3 * 3600000L) {
					String body = (sn ? Tr.t("Snow possible within the hour", "Ninsoare posibilă în următoarea oră") : Tr.t("Rain possible within the hour", "Ploaie posibilă în următoarea oră"))
							+ (Double.isNaN(pr) ? "" : " · " + Math.round(pr) + "%");
					Notifier.post(ctx, 3100 + (int) (p.id & 0xFFF), p.name + " | " + (sn ? Tr.t("Snow", "Ninsoare") : Tr.t("Rain", "Ploaie")), body);
					Log.i(ctx, "NOTIF", "parcela " + p.name + ": " + body);
				}
				sp.edit().putLong(key, at).apply();
			}
		}
		if (now.get(Calendar.HOUR_OF_DAY) < 20) return;
		String today = Farm.date(now, 0), key = "pt_" + p.id;
		if (today.equals(sp.getString(key, ""))) return;
		int t = d.dayIndex(Farm.date(now, 1));
		if (t < 0 || t >= d.dPop.length) return;
		sp.edit().putString(key, today).apply();
		double pop = d.dPop[t], mm = t < d.dRain.length ? d.dRain[t] : Double.NaN;
		boolean wet = (!Double.isNaN(pop) && pop >= Weather.RAIN_PROBABILITY && (Double.isNaN(mm) || mm >= 0.5)) || (!Double.isNaN(mm) && mm >= 1);
		if (!wet) return;
		double cm = 0;
		String tom = Farm.date(now, 1);
		if (d.snow != null) for (int h = 0; h < d.size(); h++) if (tom.equals(d.dateOf(h)) && !Double.isNaN(d.snow[h])) cm += d.snow[h];
		boolean sn = cm >= 0.5;
		Cities0 u = new Cities0(ctx);
		String body = (sn ? Tr.t("Snow possible tomorrow", "Ninsoare posibilă mâine") : Tr.t("Rain possible tomorrow", "Ploaie posibilă mâine"))
				+ (Double.isNaN(pop) ? "" : " · " + Math.round(pop) + "%") + (Double.isNaN(mm) ? "" : " · " + u.mm(mm));
		Notifier.post(ctx, 3000 + (int) (p.id & 0xFFF), p.name + " | " + (sn ? Tr.t("Snow", "Ninsoare") : Tr.t("Rain", "Ploaie")), body);
		Log.i(ctx, "NOTIF", "parcela " + p.name + ": mâine " + body);
	}

	/**
	 * v3.30 (Viorel): drought on parcel P: the soil at 3–9 cm "dry" now and no rain (under 1 mm) in
	 * the next 7 days; told once, again only after the soil got wetter or rain came in the forecast.
	 */
	static void drought(Context ctx, Parcel p, Farm.Data d, int cur) {
		if (d.soilM == null || cur >= d.soilM.length) return;
		double m = d.soilM[cur];
		int end = Math.min(d.size(), cur + 1 + 7 * 24);
		double rain = 0;
		for (int k = cur + 1; k < end; k++) if (!Double.isNaN(d.precip[k])) rain += d.precip[k];
		boolean dry = Farm.soilLevel(m) == 0 && end - cur > 6 * 24 && rain < 1;
		SharedPreferences sp = prefs(ctx);
		String key = "pd_" + p.id;
		if (!dry) {
			if (sp.getBoolean(key, false)) sp.edit().remove(key).apply();
			return;
		}
		if (sp.getBoolean(key, false)) return;
		sp.edit().putBoolean(key, true).apply();
		String body = Tr.t("Dry soil (", "Sol uscat (") + Math.round(m * 100) + Tr.t("% at 3–9 cm, estimated) and no rain in the next 7 days.", "% la 3–9 cm, estimat) și fără ploaie în următoarele 7 zile.");
		Notifier.post(ctx, 3500 + (int) (p.id & 0xFFF), title(p.name, Tr.t("Drought · ", "Secetă · ")), body);
		Log.i(ctx, "NOTIF", "parcela " + p.name + ": " + body);
	}

	/**
	 * v3.20 (Viorel): the information boxes (frost, freeze, fog, freezing rain, strong wind) found
	 * for parcel P in the next 25 hours, in a notice of its own for each parcel; each spell once.
	 * Europe only, as the boxes (in the US the NWS advisories do this).
	 */
	static void info(Context ctx, Parcel p, Farm.Data d, int cur, Cities0 u) {
		if (p.lat > 15 && p.lat < 72 && p.lon < -50 && p.lon > -180) return;
		if (d.code == null || d.isDay == null) return;
		int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
		List<ro.meteoalarm.android.core.Screen.OwnHit> hits = ro.meteoalarm.android.core.Screen.ownHits(d.temp, d.cloud, d.wind, d.gust, d.code, d.isDay, cur, 25, month, "");
		SharedPreferences sp = prefs(ctx);
		StringBuilder body = new StringBuilder();
		SharedPreferences.Editor ed = sp.edit();
		String today = d.dateOf(cur);
		for (ro.meteoalarm.android.core.Screen.OwnHit h : hits) {
			int a = cur + h.a, b = cur + h.b;
			// Each spell once: the end of the last one told; a spell starting before it is the same one.
			String key = "pi_" + p.id + "_" + h.key, start = d.time[a], end = d.time[b];
			String told = sp.getString(key, "");
			if (told.length() > 0 && start.compareTo(told) <= 0) {
				if (end.compareTo(told) > 0) ed.putString(key, end);
				continue;
			}
			ed.putString(key, end);
			String name = ro.meteoalarm.android.core.Screen.ownName(h.key);
			String when = (d.dateOf(a).equals(today) ? "" : Tr.t("tomorrow ", "mâine ")) + u.clock(d.hourOf(a)) + "–" + u.clock(d.hourOf(Math.min(d.size() - 1, b + 1)));
			String val = h.key.equals("freeze") || h.key.equals("frost") ? " · " + Tr.t("lowest ", "minima ") + u.deg(h.val)
					: h.key.equals("wind") ? " · " + Tr.t("gusts ", "rafale ") + u.kmh(h.val) : "";
			if (body.length() > 0) body.append("\n");
			body.append(name.substring(0, 1)).append(name.substring(1).toLowerCase()).append(": ").append(when).append(val);
		}
		ed.apply();
		if (body.length() == 0) return;
		Notifier.post(ctx, 3300 + (int) (p.id & 0xFFF), title(p.name, Tr.t("Information · ", "Informare · ")), body.toString());
		Log.i(ctx, "NOTIF", "parcela " + p.name + ": informare " + body.toString().replace('\n', ';'));
	}

	/** v3.40 (Viorel): a notice's title as the phone's weather app writes it: "Place | Rain". */
	static String title(String place, String what) {
		what = what.trim();
		if (what.endsWith("·")) what = what.substring(0, what.length() - 1).trim();
		return place + " | " + what;
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

		String deg(double c) {
			return (imp ? Math.round(c * 9 / 5 + 32) : Math.round(c)) + "°";
		}
	}
}
