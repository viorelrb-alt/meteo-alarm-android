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
import ro.meteoalarm.android.core.Farm;
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
		/** v2.030: INFP's answer (text) for a town in Romania, null when not asked or not come. */
		String infp;
		/** Farmer mode (v2.50): the farmer's forecast (Open-Meteo only), null when it did not come or is not wanted. */
		String farm;
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
		String tz = tz(c);
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
		if (Parcels.farmer(ctx)) {
			try {
				r.farm = Weather.get(Farm.url(c.lat, c.lon, tz, Farm.KEEP_DAYS, Farm.AHEAD));
				Farm.parse(r.farm);
				Log.i(ctx, "FERMIER", "prognoza pentru fermieri " + c.name + " OK " + r.farm.length() / 1024 + " KB");
			} catch (Exception e) {
				r.farm = null;
				Log.e(ctx, "FERMIER", "prognoza pentru fermieri a eșuat: " + reason(e, false));
			}
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
		if (r.warn == null) Log.e(ctx, "DATE", "avertizările MeteoAlarm (" + c.country + ") nu au venit" + (feedFail.length() > 0 ? ": " + feedFail : ""));
		else Log.i(ctx, "DATE", "avertizări MeteoAlarm OK " + r.warn.length() / 1024 + " KB");
		quakesInto(ctx, c, r);
		return r;
	}

	/**
	 * The earthquakes for a town outside the US: EMSC, and for Romania (v2.030, Viorel) also INFP,
	 * whose values are the official ones (EMSC stays when INFP does not answer).
	 */
	static void quakesInto(Context ctx, Cities.City c, Result r) {
		// v2.035 (Viorel): in Romania INFP is asked first, then EMSC (whose list also tells whether
		// INFP's times are UTC or local); v2.040: the same in every country with its own institute.
		Quakes.Official o = officialOf(c);
		String offRaw = null;
		Exception offErr = null;
		if (o != null) try {
			offRaw = officialRaw(ctx, o);
		} catch (Exception e) {
			offErr = e;
		}
		try {
			r.quake = Weather.get(Quakes.url(c.lat, c.lon, System.currentTimeMillis() - Quakes.WINDOW_MS));
			Log.i(ctx, "DATE", "cutremure EMSC OK: " + Quakes.parse(r.quake, countryEn(ctx, c), c.lat, c.lon, System.currentTimeMillis()).size() + " în țară în 24 h");
		} catch (Exception e) {
			r.quake = null;
			Log.e(ctx, "DATE", "cutremure EMSC a eșuat: " + reason(e, false));
		}
		if (o == null) return;
		try {
			if (offErr != null) throw offErr;
			List<Quakes.Quake> em = r.quake != null ? Quakes.parse(r.quake, countryEn(ctx, c), c.lat, c.lon, System.currentTimeMillis()) : null;
			r.infp = Quakes.officialText(o, offRaw, em);
			if (r.infp == null) throw new Exception("răspuns necunoscut (" + (offRaw == null ? 0 : offRaw.length()) + " B)");
			int n = 0, all = 0;
			long now = System.currentTimeMillis();
			String newest = "";
			for (String l : r.infp.split("\n")) {
				if (l.length() == 0 || l.startsWith("#")) continue;
				all++;
				String[] f = l.split("\\|", -1);
				if (newest.length() == 0 && f.length > 10) newest = f[1].trim() + " UTC M" + f[10].trim() + (f.length > 12 ? " " + f[12].trim() : "");
				long tm = f.length > 1 ? Quakes.infpTime(f[1].trim()) : 0;
				if (tm > 0 && now - tm <= Quakes.WINDOW_MS) n++;
			}
			Log.i(ctx, "DATE", "cutremure " + o.name + " OK: " + n + " în 24 h (" + all + " în listă" + (r.infp.contains("|local\n") ? ", ora locală" : "") + (newest.length() > 0 ? ", ultimul " + newest : "") + ")");
		} catch (Exception e) {
			r.infp = null;
			Log.e(ctx, "DATE", "cutremure " + o.name + " a eșuat: " + reason(e, false) + " (rămân valorile EMSC)");
		}
	}

	/** v2.030: towns in Romania get INFP's values; v2.040: every country with its own institute. */
	static boolean infpFor(Cities.City c) {
		return officialOf(c) != null;
	}

	/** v2.040: the town's country's seismological institute (null: none, or a US town, which has USGS). */
	/**
	 * v2.050: the institute's answer; for INFP also its home page after Quakes.WEB_SEP (the quakes
	 * around Romania its JSON list leaves out). The page failing leaves the list alone.
	 */
	static String officialRaw(Context ctx, Quakes.Official o) throws Exception {
		String url = o.url;
		if (url.contains("{start}")) {
			java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
			f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
			url = url.replace("{start}", f.format(new java.util.Date(System.currentTimeMillis() - 3 * 86400000L)));
		}
		String raw = Weather.get(url);
		if (o.web != null) try {
			String w = Weather.get(o.web);
			String t = Quakes.infpFromWeb(w);
			int n = t == null ? 0 : t.split("\n").length - 1;
			Log.i(ctx, "DATE", "cutremure " + o.name + " de pe " + o.site + ": " + n + " în pagină" + (n > 0 ? ", ultimul " + t.split("\n")[1].replaceAll("^[^|]*\\|([^|]*)\\|.*\\|INFP\\|", "$1 UTC ") : ""));
			if (n > 0) raw = raw + Quakes.WEB_SEP + w;
		} catch (Exception e) {
			Log.e(ctx, "DATE", "pagina " + o.site + " a eșuat: " + reason(e, false));
		}
		return raw;
	}

	static Quakes.Official officialOf(Cities.City c) {
		return c == null || c.us() ? null : Quakes.official(c.country);
	}

	/**
	 * v2.030 (Viorel): the light check of the earthquakes in the background, also at night with the
	 * screen off: only EMSC (and INFP for Romania), a few KB, then their notices.
	 */
	static void quakesOnly(Context ctx, Cities.City c) {
		Result r = new Result();
		if (c.us()) {
			try {
				r.quake = Weather.get(Quakes.usgsUrl(c.lat, c.lon, System.currentTimeMillis() - Quakes.WINDOW_MS));
			} catch (Exception e) {
				Log.e(ctx, "US", "cutremure USGS a eșuat: " + reason(e, false));
			}
		} else quakesInto(ctx, c, r);
		saveQuakes(ctx, c, r);
		if (r.quake != null || r.infp != null) Notifier.checkQuakes(ctx, c);
	}

	/**
	 * v2.107 (Viorel): after the app is installed or updated, in a country with its own institute
	 * (INFP…), the earthquakes are downloaded at once (the institute first, then EMSC), even when the
	 * weather saved by the older version is still fresh. Once per version; true when it downloaded.
	 */
	static synchronized boolean quakesAfterUpdate(Context ctx, Cities.City c) {
		if (c == null || officialOf(c) == null) return false;
		SharedPreferences p = prefs(ctx);
		if (MainActivity.VERSION.equals(p.getString("quakeVer", ""))) return false;
		if (!NetJob.online(ctx)) return false;
		Log.i(ctx, "DATE", "cutremure după instalare: " + officialOf(c).name + ", apoi EMSC");
		quakesOnly(ctx, c);
		p.edit().putString("quakeVer", MainActivity.VERSION).apply();
		return true;
	}

	/** The earthquake answers of R for town C, and each quake's first value (to show revisions). */
	static void saveQuakes(Context ctx, Cities.City c, Result r) {
		long now = System.currentTimeMillis();
		if (r.quake != null) {
			write(ctx, "quake.json", r.quake);
			prefs(ctx).edit().putString("quakeCity", c.key()).putLong("quakeAt", now).apply();
		}
		if (r.infp != null) {
			write(ctx, "infp.txt", r.infp);
			prefs(ctx).edit().putString("infpCity", c.key()).apply();
		}
		if (r.quake == null && r.infp == null) return;
		// v2.030: "qh_<id>" = first magnitude | its source | when seen | last magnitude | its source | when it changed | last seen.
		SharedPreferences p = prefs(ctx);
		SharedPreferences.Editor e = p.edit();
		for (Quakes.Quake q : quakes(ctx, c)) {
			String key = "qh_" + q.id.replaceAll("[^A-Za-z0-9]+", "");
			String old = p.getString(key, null);
			if (old == null && q.altId.length() > 0) old = p.getString("qh_" + q.altId.replaceAll("[^A-Za-z0-9]+", ""), null);
			String m = String.format(java.util.Locale.US, "%.1f", q.mag);
			String[] h = old == null ? null : old.split("\\|", -1);
			if (h == null || h.length < 7) e.putString(key, m + "|" + q.src + "|" + now + "|" + m + "|" + q.src + "|0|" + now);
			else if (!h[3].equals(m) || !h[4].equals(q.src)) {
				e.putString(key, h[0] + "|" + h[1] + "|" + h[2] + "|" + m + "|" + q.src + "|" + now + "|" + now);
				Log.i(ctx, "CUTREMUR", "valoare revizuită: M" + h[3] + " (" + h[4] + ") -> M" + m + " (" + q.src + ")");
			} else e.putString(key, old.substring(0, old.lastIndexOf('|') + 1) + now);
		}
		for (java.util.Map.Entry<String, ?> en : p.getAll().entrySet()) {
			if (!en.getKey().startsWith("qh_") || !(en.getValue() instanceof String)) continue;
			String v = (String) en.getValue();
			try {
				if (now - Long.parseLong(v.substring(v.lastIndexOf('|') + 1)) > 3 * 86400000L) e.remove(en.getKey());
			} catch (Exception x) {
				e.remove(en.getKey());
			}
		}
		e.apply();
	}

	/**
	 * Off the main thread: parcel P shown as a main screen (v2.60), as town C (its name and the
	 * nearest town's country and county): the forecast, the extra boxes, the farmer's data (the
	 * parcel's own, with its statistics), the air quality and the warnings; no earthquakes or
	 * pollen (Viorel 2026-11-07). Nothing here is notified or goes to the widgets.
	 */
	static Result downloadParcel(Context ctx, Cities.City c, Parcels.Parcel p) {
		Result r = new Result();
		String tz = tz(c);
		try {
			String[] f = forecast(ctx, c, tz);
			r.forecast = f[0];
			r.uv = f[1];
			r.source = f[2];
			Log.i(ctx, "FERMIER", "prognoza parcelei " + p.name + " OK · " + r.source);
		} catch (Exception e) {
			r.error = e;
			Log.e(ctx, "FERMIER", "prognoza parcelei " + p.name + " a eșuat: " + reason(e, false));
		}
		try {
			if (r.forecast != null && r.uv == null) r.uv = Weather.get(Weather.uvUrl(c.lat, c.lon, tz));
		} catch (Exception e) {
			r.uv = null;
		}
		try {
			Parcels.fetch(ctx, p);
			r.farm = read(ctx, Parcels.dataFile(p.id));
		} catch (Exception e) {
			r.farm = null;
			Log.e(ctx, "FERMIER", "datele parcelei " + p.name + " au eșuat: " + reason(e, false));
		}
		try {
			r.pollen = Weather.get(Weather.pollenUrl(c.lat, c.lon));
		} catch (Exception e) {
			r.pollen = null;
		}
		if (c.us()) {
			try {
				r.warn = Weather.get(Nws.url(c.lat, c.lon));
			} catch (Exception e) {
				r.warn = null;
			}
		} else r.warn = warningsFeed(ctx, c.country);
		return r;
	}

	/**
	 * v2.150 (Viorel): another town of the list shown as a main screen of its own: the forecast, the
	 * extra boxes, the air, the warnings and the farmer's data, kept in its own files (id &lt; 0, see
	 * parcelFile). Nothing here is notified or goes to the widgets (they follow the first town).
	 */
	static Result downloadPlace(Context ctx, Cities.City c) {
		Result r = new Result();
		String tz = tz(c);
		try {
			String[] f = forecast(ctx, c, tz);
			r.forecast = f[0];
			r.uv = f[1];
			r.source = f[2];
			Log.i(ctx, "LOC", "prognoza pentru ecranul " + c.label() + " OK · " + r.source);
		} catch (Exception e) {
			r.error = e;
			Log.e(ctx, "LOC", "prognoza pentru ecranul " + c.label() + " a eșuat: " + reason(e, false));
		}
		try {
			if (r.forecast != null && r.uv == null) r.uv = Weather.get(Weather.uvUrl(c.lat, c.lon, tz));
		} catch (Exception e) {
			r.uv = null;
		}
		try {
			r.pollen = Weather.get(Weather.pollenUrl(c.lat, c.lon));
		} catch (Exception e) {
			r.pollen = null;
		}
		try {
			r.farm = Weather.get(Farm.url(c.lat, c.lon, tz, Farm.KEEP_DAYS, Farm.AHEAD));
		} catch (Exception e) {
			r.farm = null;
		}
		if (c.us()) {
			try {
				r.warn = Weather.get(Nws.url(c.lat, c.lon));
			} catch (Exception e) {
				r.warn = null;
			}
		} else r.warn = warningsFeed(ctx, c.country);
		return r;
	}

	/** v2.150: the id of the screen of town C of the list (negative, the same for the same town). */
	static long placeId(Cities.City c) {
		return -1L - (c.key().hashCode() & 0x7fffffffL);
	}

	/** v2.150: keeps what came for the town of screen ID. */
	static void savePlace(Context ctx, long id, Result r) {
		if (r.forecast != null) {
			if (r.uv != null) write(ctx, parcelFile(id, "uv.json"), r.uv);
			write(ctx, parcelFile(id, "forecast.json"), r.forecast);
			prefs(ctx).edit().putLong("pmf_" + id, System.currentTimeMillis()).putString("pms_" + id, r.source).apply();
		}
		if (r.pollen != null) write(ctx, parcelFile(id, "air.json"), r.pollen);
		if (r.warn != null) write(ctx, parcelFile(id, "warn.xml"), r.warn);
		if (r.farm != null) write(ctx, parcelFile(id, "farm.json"), r.farm);
	}

	/**
	 * v2.152 (Viorel: after a reorder the first town showed "Se descarcă vremea…"): the first
	 * town keeps its data in the main files, the others in their screen's files. When another
	 * town becomes the first, the old first's data go to its screen's files and the new first's
	 * come into the main files, so nothing is downloaded again.
	 */
	static synchronized void swapFirst(Context ctx, Cities.City was, Cities.City now) {
		SharedPreferences p = prefs(ctx);
		SharedPreferences.Editor e = p.edit();
		String[][] parts = { { "forecast.json", "forecast.json" }, { "uv.json", "uv.json" }, { "pollen.json", "air.json" }, { "warn.xml", "warn.xml" }, { "farm.json", "farm.json" } };
		// The new first's saved screen, read before anything is overwritten.
		long nid = placeId(now);
		String[] in = new String[parts.length];
		for (int k = 0; k < parts.length; k++) in[k] = read(ctx, parcelFile(nid, parts[k][1]));
		long nFetched = p.getLong("pmf_" + nid, 0);
		String nSource = p.getString("pms_" + nid, "");
		if (was != null && was.key().equals(p.getString("cacheCity", "")) && read(ctx, "forecast.json") != null) {
			long wid = placeId(was);
			for (String[] pt : parts) {
				String x = read(ctx, pt[0]);
				if (x != null && (!pt[0].equals("warn.xml") || was.key().equals(p.getString("warnCity", was.key()))) && (!pt[0].equals("farm.json") || was.key().equals(p.getString("farmCity", ""))))
					write(ctx, parcelFile(wid, pt[1]), x);
			}
			e.putLong("pmf_" + wid, p.getLong("fetched", 0)).putString("pms_" + wid, p.getString("source", ""));
		}
		if (in[0] != null) {
			for (int k = 0; k < parts.length; k++) if (in[k] != null) write(ctx, parts[k][0], in[k]);
			e.putString("cacheCity", now.key()).putLong("fetched", nFetched).putString("source", nSource).putString("warnCity", now.key());
			if (in[4] != null) e.putString("farmCity", now.key()).putLong("farmFetched", nFetched);
			Log.i(ctx, "LOC", "prima localitate " + now.label() + ": datele ei salvate trec în ecranul principal, fără descărcare");
		}
		e.apply();
	}

	/** v2.150: forgets the files of the screen of a town taken out of the list. */
	static void dropPlace(Context ctx, long id) {
		for (String part : new String[] { "uv.json", "forecast.json", "air.json", "warn.xml", "farm.json" }) ctx.deleteFile(parcelFile(id, part));
		prefs(ctx).edit().remove("pmf_" + id).remove("pms_" + id).apply();
	}

	/** The files of parcel ID shown as a main screen: "pm-ID-forecast.json" and so on. */
	static String parcelFile(long id, String part) {
		return "pm-" + id + "-" + part;
	}

	/** Keeps on the phone what came for parcel P (never the town's files: the widgets show the town). */
	static void saveParcel(Context ctx, Parcels.Parcel p, Result r) {
		if (r.forecast != null) {
			if (r.uv != null) write(ctx, parcelFile(p.id, "uv.json"), r.uv);
			write(ctx, parcelFile(p.id, "forecast.json"), r.forecast);
			prefs(ctx).edit().putLong("pmf_" + p.id, System.currentTimeMillis()).putString("pms_" + p.id, r.source).apply();
		}
		if (r.pollen != null) write(ctx, parcelFile(p.id, "air.json"), r.pollen);
		if (r.warn != null) write(ctx, parcelFile(p.id, "warn.xml"), r.warn);
	}

	/** The warnings saved for parcel ID, as place C. */
	static List<Warnings.Warn> parcelWarnings(Context ctx, long id, Cities.City c) {
		try {
			return Warnings.of(read(ctx, parcelFile(id, "warn.xml")), c, System.currentTimeMillis());
		} catch (Exception e) {
			return new java.util.ArrayList<Warnings.Warn>();
		}
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
	/** v2.151: why the last warnings feed did not come (for the Log). */
	static volatile String feedFail = "";

	static String warningsFeed(Context ctx, String cc) {
		SharedPreferences prefs = prefs(ctx);
		List<String> urls = countries(ctx).feeds(cc);
		String known = prefs.getString("feed_" + cc, null);
		if (known != null) {
			urls.remove(known);
			urls.add(0, known);
		}
		feedFail = "";
		for (String u : urls) {
			try {
				String x = Weather.get(u);
				if (!u.equals(known)) prefs.edit().putString("feed_" + cc, u).apply();
				return x;
			} catch (Exception e) {
				feedFail = reason(e, false); // try the next one
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
		if (r.farm != null) saveFarm(ctx, c, r.farm);
		if (r.warn != null) {
			write(ctx, "warn.xml", r.warn);
			prefs(ctx).edit().putString("warnCity", c.key()).apply();
		}
		saveQuakes(ctx, c, r);
		// Rain or snow starting, new warnings, earthquakes: notified once (when the user agreed).
		if (r.forecast != null || r.warn != null || r.quake != null || r.infp != null) Notifier.check(ctx, c);
	}

	/** The time zone of town C: a US town's own (v2.50, its local time), else the phone's. */
	static String tz(Cities.City c) {
		String z = c != null && c.us() ? Cities.tzOf(c) : null;
		return z != null ? z : TimeZone.getDefault().getID();
	}

	/** Keeps the farmer's forecast of town C; the notice promised when it had not come (v2.50). */
	static void saveFarm(Context ctx, Cities.City c, String json) {
		write(ctx, "farm.json", json);
		SharedPreferences p = prefs(ctx);
		p.edit().putString("farmCity", c.key()).putLong("farmFetched", System.currentTimeMillis()).apply();
		if (p.getBoolean("farmPending", false)) {
			// v3.40 (Viorel): no notice any more when the data come after a pause of Open-Meteo.
			p.edit().remove("farmPending").apply();
			Log.i(ctx, "FERMIER", "datele pentru fermieri au venit după pauza Open-Meteo");
		}
	}

	/** The farmer's forecast saved for town C (null when none or for another town). */
	static Farm.Data farm(Context ctx, Cities.City c) {
		if (c == null || !c.key().equals(prefs(ctx).getString("farmCity", ""))) return null;
		try {
			String f = read(ctx, "farm.json");
			return f == null ? null : Farm.parse(f);
		} catch (Exception e) {
			return null;
		}
	}

	/** The English name of the town's country, as EMSC names regions ("Romania"). */
	static String countryEn(Context ctx, Cities.City c) {
		return countries(ctx).name(c.country, true);
	}

	/** The earthquakes saved for town C (empty when none or saved for another town). */
	static List<Quakes.Quake> quakes(Context ctx, Cities.City c) {
		String q = c != null && c.key().equals(prefs(ctx).getString("quakeCity", "")) ? read(ctx, "quake.json") : null;
		try {
			List<Quakes.Quake> l;
			if (c != null && c.us()) l = quakesOf(q, c);
			else {
				long now = System.currentTimeMillis();
				l = Quakes.parse(q, countryEn(ctx, c), c.lat, c.lon, now);
				// v2.030: INFP's official values for a town in Romania.
				if (infpFor(c) && c.key().equals(prefs(ctx).getString("infpCity", ""))) Quakes.mergeOfficial(l, read(ctx, "infp.txt"), officialOf(c).name, c.lat, c.lon, now, Quakes.WINDOW_MS);
			}
			history(ctx, l);
			return l;
		} catch (Exception e) {
			return new java.util.ArrayList<Quakes.Quake>();
		}
	}

	/**
	 * v2.230 (Viorel): the quakes of the last 24 h around town C from any country, with the
	 * intensity estimated for the town (for the "Earthquake" button: one from another country that
	 * was felt here). Empty when none.
	 */
	static List<Quakes.Quake> quakesFelt(Context ctx, Cities.City c) {
		try {
			if (c == null || c.us()) return new java.util.ArrayList<Quakes.Quake>();
			String q = c.key().equals(prefs(ctx).getString("quakeCity", "")) ? read(ctx, "quake.json") : null;
			return Quakes.parse(q, new String[0], c.lat, c.lon, System.currentTimeMillis(), Quakes.WINDOW_MS);
		} catch (Exception e) {
			return new java.util.ArrayList<Quakes.Quake>();
		}
	}

	/** v2.030: each quake's first value seen and when its value last changed (saveQuakes()). */
	private static void history(Context ctx, List<Quakes.Quake> l) {
		SharedPreferences p = prefs(ctx);
		for (Quakes.Quake q : l) {
			String v = p.getString("qh_" + q.id.replaceAll("[^A-Za-z0-9]+", ""), null);
			if (v == null && q.altId.length() > 0) v = p.getString("qh_" + q.altId.replaceAll("[^A-Za-z0-9]+", ""), null);
			if (v == null) continue;
			String[] h = v.split("\\|", -1);
			if (h.length < 7) continue;
			try {
				q.changedAt = Long.parseLong(h[5]);
				if (q.changedAt > 0 && !h[0].equals(String.format(java.util.Locale.US, "%.1f", q.mag))) {
					q.firstAt = Long.parseLong(h[2]);
					q.firstMag = Double.parseDouble(h[0]);
					q.firstSrc = h[1];
				} else if (Double.isNaN(q.firstMag)) q.firstAt = Long.parseLong(h[2]);
			} catch (Exception e) {
				// no history
			}
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

	/** v5.20: the weather icon theme from the settings (WxIcon.SETS; v5.10's luxIcons = Lucios). */
	static int iconTheme(android.content.SharedPreferences p) {
		int k = p.getInt("iconTheme", p.getBoolean("luxIcons", false) ? 1 : 0);
		return k < 0 || k >= ro.meteoalarm.android.core.WxIcon.SETS.length ? 0 : k;
	}
}
