package ro.meteoalarm.android;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.TimeZone;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Tr;
import ro.meteoalarm.android.core.Countries;
import ro.meteoalarm.android.core.MetNo;
import ro.meteoalarm.android.core.Nws;
import ro.meteoalarm.android.core.Warnings;
import ro.meteoalarm.android.core.Quakes;
import ro.meteoalarm.android.core.Weather;

/**
 * Downloads everything the app shows for a town (forecast, UV, pollen / air, MeteoAlarm warnings,
 * EMSC earthquakes) and keeps it on the phone, for the app, the widgets and the refresh in the
 * background alike. Each part that fails keeps what was saved before.
 */
final class Fetcher {
	static final class Result {
		String forecast, uv, pollen, warn, quake;
		/** Where the forecast came from: "Open-Meteo" or MetNo.NAME. */
		String source = "";
		/** Why the forecast did not come (null when it came). */
		Exception error;
	}

	/** EX in a few words, so the screen can say why the weather did not come. */
	static String reason(Exception ex, boolean en) {
		if (ex == null) return "";
		Throwable t = ex;
		while (t.getCause() != null && t.getCause() != t && !(t instanceof java.io.IOException)) t = t.getCause();
		String m = t.getMessage() == null ? "" : t.getMessage();
		if (t instanceof java.net.UnknownHostException) return en ? Tr.t("no internet (server not found)") : "fără internet (serverul nu e găsit)";
		if (t instanceof java.net.SocketTimeoutException) return en ? Tr.t("the server did not answer in time") : "serverul nu a răspuns la timp";
		if (t instanceof java.net.ConnectException || t instanceof java.net.NoRouteToHostException) return en ? Tr.t("no connection") : "fără conexiune";
		if (t instanceof javax.net.ssl.SSLException || t instanceof java.security.cert.CertificateException)
			return (en ? Tr.t("secure connection error: ") : "eroare conexiune securizată: ") + t.getClass().getSimpleName();
		if (t instanceof SecurityException) return en ? Tr.t("internet not allowed for the app") : "internetul nu e permis aplicației";
		if (m.startsWith("HTTP ")) {
			int sp = m.indexOf(' ', 5);
			return (en ? Tr.t("server error ") : "eroare server ") + (sp > 0 ? m.substring(0, sp) : m);
		}
		if (t instanceof java.io.IOException) return (en ? Tr.t("network error: ") : "eroare rețea: ") + t.getClass().getSimpleName() + (m.length() > 0 && m.length() < 40 ? " " + m : "");
		return (en ? Tr.t("data error: ") : "eroare date: ") + t.getClass().getSimpleName();
	}

	private static Countries countries;

	static synchronized Countries countries(Context ctx) {
		if (countries == null) {
			try {
				countries = new Countries(readAll(ctx.getAssets().open("countries.txt")));
			} catch (Exception e) {
				countries = new Countries("");
			}
		}
		return countries;
	}

	static SharedPreferences prefs(Context ctx) {
		return ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
	}

	private static final Object LOCK = new Object();
	private static Result last;
	private static String lastKey = "";
	private static long lastAt;
	/** A download for the same town finished this recently is used again instead of a new one. */
	static final long SHARE_MS = 60000;

	/**
	 * Off the main thread: every part for town C (null where it failed). One download at a time
	 * for the whole app: after an update the background refresh and the app started at the same
	 * moment and both downloaded (A52s log 2026-11-02 17:15:19); the second now waits and takes
	 * the first one's result.
	 */
	static Result download(Context ctx, Cities.City c) {
		synchronized (LOCK) {
			long now = System.currentTimeMillis();
			if (last != null && c.key().equals(lastKey) && now - lastAt < SHARE_MS) {
				Log.i(ctx, "DATE", "descărcarea pentru " + c.name + " tocmai s-a făcut (acum " + (now - lastAt) / 1000 + " s), o folosesc");
				return last;
			}
			Result r = downloadNow(ctx, c);
			last = r;
			lastKey = c.key();
			lastAt = System.currentTimeMillis();
			return r;
		}
	}

	private static Result downloadNow(Context ctx, Cities.City c) {
		Result r = new Result();
		String tz = TimeZone.getDefault().getID();
		try {
			String[] f = forecast(ctx, c, tz);
			r.forecast = f[0];
			r.uv = f[1];
			r.source = f[2];
			Log.i(ctx, "DATE", "prognoza " + c.name + " OK " + r.forecast.length() / 1024 + " KB · " + r.source);
		} catch (Exception e) {
			r.forecast = null;
			r.error = e;
			Log.e(ctx, "DATE", "prognoza " + c.name + " a eșuat: " + reason(e, false), e);
		}
		try {
			// The extra boxes (UV, humidity, pressure, visibility): from Open-Meteo, unless MET Norway gave them.
			if (r.forecast != null && r.uv == null) r.uv = Weather.get(Weather.uvUrl(c.lat, c.lon, tz));
		} catch (Exception e) {
			r.uv = null;
			Log.e(ctx, "DATE", "UV a eșuat: " + reason(e, false));
		}
		try {
			r.pollen = Weather.get(Weather.pollenUrl(c.lat, c.lon));
		} catch (Exception e) {
			r.pollen = null;
			Log.e(ctx, "DATE", "polen / aer a eșuat: " + reason(e, false));
		}
		if (c.us()) {
			// United States (v2.30): the National Weather Service for the town's point.
			try {
				r.warn = Weather.get(Nws.url(c.lat, c.lon));
				List<Warnings.Warn> ws = Nws.parse(r.warn, System.currentTimeMillis());
				StringBuilder b = new StringBuilder();
				for (Warnings.Warn w : ws) b.append(b.length() > 0 ? ", " : "").append(w.type);
				Log.i(ctx, "US", "avertizări NWS OK: " + ws.size() + (ws.isEmpty() ? "" : " (" + b + ")"));
			} catch (Exception e) {
				r.warn = null;
				Log.e(ctx, "US", "avertizările NWS au eșuat: " + reason(e, false));
			}
			try {
				r.quake = Weather.get(Quakes.usgsUrl(c.lat, c.lon, System.currentTimeMillis() - Quakes.WINDOW_MS));
				Log.i(ctx, "US", "cutremure USGS OK: " + quakesOf(r.quake, c).size() + " în 24 h");
			} catch (Exception e) {
				r.quake = null;
				Log.e(ctx, "US", "cutremure USGS a eșuat: " + reason(e, false));
			}
			return r;
		}
		r.warn = warningsFeed(ctx, c.country);
		if (r.warn == null) Log.e(ctx, "DATE", "avertizările MeteoAlarm (" + c.country + ") nu au venit");
		else Log.i(ctx, "DATE", "avertizări MeteoAlarm OK " + r.warn.length() / 1024 + " KB");
		try {
			r.quake = Weather.get(Quakes.url(c.lat, c.lon, System.currentTimeMillis() - Quakes.WINDOW_MS));
			Log.i(ctx, "DATE", "cutremure EMSC OK: " + Quakes.parse(r.quake, countryEn(ctx, c), c.lat, c.lon, System.currentTimeMillis()).size() + " în țară în 24 h");
		} catch (Exception e) {
			r.quake = null;
			Log.e(ctx, "DATE", "cutremure EMSC a eșuat: " + reason(e, false));
		}
		return r;
	}

	/** After Open-Meteo said "overloaded", MET Norway is asked first for this long. */
	static final long OPEN_METEO_REST_MS = 20 * 60000L;

	/**
	 * The forecast for C (checked): { forecast, extra or null, source }. Open-Meteo with the ICON
	 * model first, then without a model; when Open-Meteo is busy (HTTP 503 "The service is
	 * overloaded", as for an hour on 2026-11-02 on every phone) or does not answer, MET Norway
	 * (MetNo), turned into the same shape. After a "busy", Open-Meteo rests 20 minutes and MET
	 * Norway is asked straight away. Throws the first error when all fail.
	 */
	static String[] forecast(Context ctx, Cities.City c, String tz) throws Exception {
		SharedPreferences p = prefs(ctx);
		long now = System.currentTimeMillis();
		boolean rest = now < p.getLong("omBusyUntil", 0);
		Exception first = null;
		if (rest) Log.i(ctx, "DATE", "Open-Meteo a fost supraîncărcat, iau prognoza de la " + MetNo.NAME);
		else {
			String[] models = { Weather.MODEL, "" };
			for (int i = 0; i < models.length; i++) {
				try {
					String json = Weather.get(Weather.forecastUrl(c.lat, c.lon, tz, models[i]));
					Weather.parse(json);
					if (i > 0) Log.i(ctx, "DATE", "prognoza " + c.name + " venită fără modelul " + Weather.MODEL);
					return new String[] { json, null, "Open-Meteo" };
				} catch (Exception e) {
					if (first == null) first = e;
					Log.w(ctx, "DATE", "Open-Meteo " + (models[i].length() > 0 ? "(" + models[i] + ")" : "(model implicit)") + ": " + e.getMessage());
					// No internet: asking anyone else will not help.
					if (e instanceof java.net.UnknownHostException) throw e;
					if (e instanceof java.io.IOException) break;
				}
			}
			if (first != null && Weather.busy(first)) p.edit().putLong("omBusyUntil", now + OPEN_METEO_REST_MS).apply();
		}
		try {
			String[] m = MetNo.toOpenMeteo(Weather.get(MetNo.url(c.lat, c.lon)), c.lat, c.lon, TimeZone.getTimeZone(tz), System.currentTimeMillis());
			Weather.parse(m[0]);
			Log.i(ctx, "DATE", "prognoza " + c.name + " de la " + MetNo.NAME + " (rezervă)");
			return new String[] { m[0], m[1], MetNo.NAME };
		} catch (Exception e) {
			Log.w(ctx, "DATE", MetNo.NAME + ": " + e.getClass().getSimpleName() + " " + e.getMessage());
			if (first == null) first = e;
		}
		throw first;
	}

	/**
	 * The country's MeteoAlarm warnings: the feed that worked last time first, then the others
	 * listed for the country; the one that answers is kept on the phone.
	 */
	static String warningsFeed(Context ctx, String cc) {
		SharedPreferences prefs = prefs(ctx);
		List<String> urls = countries(ctx).feeds(cc);
		String known = prefs.getString("feed_" + cc, null);
		if (known != null) {
			urls.remove(known);
			urls.add(0, known);
		}
		for (String u : urls) {
			try {
				String x = Weather.get(u);
				if (!u.equals(known)) prefs.edit().putString("feed_" + cc, u).apply();
				return x;
			} catch (Exception e) {
				// try the next one
			}
		}
		return null;
	}

	/** Keeps on the phone the parts of R that came (for town C). */
	static synchronized void save(Context ctx, Cities.City c, Result r) {
		long now = System.currentTimeMillis();
		if (r.forecast != null) {
			if (r.uv != null) write(ctx, "uv.json", r.uv);
			write(ctx, "forecast.json", r.forecast);
			prefs(ctx).edit().putLong("fetched", now).putString("cacheCity", c.key()).putString("failReason", "").putString("source", r.source).commit();
		} else {
			prefs(ctx).edit().putString("failReason", reason(r.error, false)).putString("failReasonEn", reason(r.error, true)).apply();
		}
		if (r.pollen != null) write(ctx, "pollen.json", r.pollen);
		if (r.warn != null) {
			write(ctx, "warn.xml", r.warn);
			prefs(ctx).edit().putString("warnCity", c.key()).apply();
		}
		if (r.quake != null) {
			write(ctx, "quake.json", r.quake);
			prefs(ctx).edit().putString("quakeCity", c.key()).apply();
		}
		// Rain or snow starting, new warnings, earthquakes: notified once (when the user agreed).
		if (r.forecast != null || r.warn != null || r.quake != null) Notifier.check(ctx, c);
	}

	/** The English name of the town's country, as EMSC names regions ("Romania"). */
	static String countryEn(Context ctx, Cities.City c) {
		return countries(ctx).name(c.country, true);
	}

	/** The earthquakes saved for town C (empty when none or saved for another town). */
	static List<Quakes.Quake> quakes(Context ctx, Cities.City c) {
		String q = c != null && c.key().equals(prefs(ctx).getString("quakeCity", "")) ? read(ctx, "quake.json") : null;
		try {
			if (c != null && c.us()) return quakesOf(q, c);
			return Quakes.parse(q, countryEn(ctx, c), c.lat, c.lon, System.currentTimeMillis());
		} catch (Exception e) {
			return new java.util.ArrayList<Quakes.Quake>();
		}
	}

	/** US town C: the USGS quakes of the last 24 h around it. */
	private static List<Quakes.Quake> quakesOf(String json, Cities.City c) {
		return Quakes.parseUsgs(json, c.lat, c.lon, System.currentTimeMillis(), Quakes.WINDOW_MS, Quakes.US_MIN_MAG, false);
	}

	/**
	 * The warnings saved for town C. A US town's (NWS, for its point) only when they were
	 * downloaded for it; the MeteoAlarm feed is the country's, matched by the area names.
	 */
	static List<Warnings.Warn> warnings(Context ctx, Cities.City c) {
		if (c == null) return new java.util.ArrayList<Warnings.Warn>();
		String x = read(ctx, "warn.xml");
		String of = prefs(ctx).getString("warnCity", "");
		if (c.us() && !c.key().equals(of)) return new java.util.ArrayList<Warnings.Warn>();
		try {
			return Warnings.of(x, c, System.currentTimeMillis());
		} catch (Exception e) {
			return new java.util.ArrayList<Warnings.Warn>();
		}
	}

	static String read(Context ctx, String name) {
		try {
			return readAll(new FileInputStream(new File(ctx.getFilesDir(), name)));
		} catch (Exception e) {
			return null;
		}
	}

	static void write(Context ctx, String name, String text) {
		try {
			File f = new File(ctx.getFilesDir(), name + ".new");
			FileOutputStream out = new FileOutputStream(f);
			out.write(text.getBytes("UTF-8"));
			out.close();
			f.renameTo(new File(ctx.getFilesDir(), name));
		} catch (Exception e) {
			// the cache is only a convenience
		}
	}

	static String readAll(InputStream in) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[16384];
		int n;
		while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
		in.close();
		return new String(out.toByteArray(), "UTF-8");
	}
}
