package ro.meteoalarm.android;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Villages;

/**
 * v4.20: every locality of Romania (assets/villages.txt), read once when first needed.
 * v4.40 (Viorel 2026-11-10): the small localities of the other countries and US states, downloaded
 * once from OpenStreetMap the first time the phone is located there with internet (files/places/AREA.txt),
 * and one US state's list in the app from the start (assets/places-us-OK.txt), left aside 7 days after
 * the start on a phone in Europe. A downloaded list not used for UNUSED_DAYS is deleted.
 */
final class Places {
	private static Villages all;
	private static final Map<String, Villages> areas = new LinkedHashMap<String, Villages>();
	private static boolean loaded;
	private static volatile boolean busy;

	static final String BUNDLED = "us-OK", BUNDLED_FILE = "places-us-OK.txt";
	static final long DAY = 86400000L;
	static final int UNUSED_DAYS = 60, BUNDLED_DAYS = 7, RETRY_DAYS = 3;

	private Places() {
	}

	static synchronized Villages villages(Context c) {
		if (all == null) {
			try {
				all = new Villages(Fetcher.readAll(c.getAssets().open("villages.txt")));
			} catch (Exception e) {
				all = new Villages("");
			}
		}
		return all;
	}

	private static File dir(Context c) {
		File d = new File(c.getFilesDir(), "places");
		d.mkdirs();
		return d;
	}

	/** "us-OK" for a US state, the country code elsewhere; null in Romania (its list is in the app). */
	static String area(String country, String county) {
		if (country == null || country.length() == 0 || country.equals("ro")) return null;
		if (country.equals("us")) {
			String st = Cities.stateCode(county);
			return st.length() == 2 ? "us-" + st : null;
		}
		return country.matches("[a-z]{2}") ? country : null;
	}

	private static String countryOf(String area) {
		return area.startsWith("us-") ? "us" : area;
	}

	/** The lists of the other countries and states on the phone. */
	static synchronized List<Villages> areas(Context c) {
		if (!loaded) {
			loaded = true;
			areas.clear();
			File[] fs = dir(c).listFiles();
			if (fs != null) for (File f : fs) {
				String n = f.getName();
				if (!n.endsWith(".txt")) continue;
				String a = n.substring(0, n.length() - 4);
				try {
					areas.put(a, new Villages(Fetcher.readAll(new java.io.FileInputStream(f)), countryOf(a)));
				} catch (Exception e) {
					Log.e(c, "LOCALITĂȚI", "citire " + n, e);
				}
			}
			if (!areas.containsKey(BUNDLED) && !Fetcher.prefs(c).getBoolean("okDropped", false)) {
				try {
					areas.put(BUNDLED, new Villages(Fetcher.readAll(c.getAssets().open(BUNDLED_FILE)), "us"));
				} catch (Exception e) {
					// not in this build
				}
			}
		}
		return new ArrayList<Villages>(areas.values());
	}

	/** The N localities nearest to LAT, LON from all the lists (Romania's and the others'), nearest first. */
	static List<Cities.City> nearest(Context c, double la, double lo, int n) {
		List<Cities.City> out = new ArrayList<Cities.City>();
		if (villages(c).covers(la, lo)) out.addAll(villages(c).nearest(la, lo, n));
		for (Villages v : areas(c)) if (v.covers(la, lo)) out.addAll(v.nearest(la, lo, n));
		return out;
	}

	/** The locality within KM of LAT, LON from any list (Romania first); null when none. */
	static Cities.City at(Context c, double la, double lo, double km) {
		Cities.City v = villages(c).at(la, lo, km);
		if (v != null) return v;
		for (Villages a : areas(c)) {
			v = a.at(la, lo, km);
			if (v != null) return v;
		}
		return null;
	}

	/**
	 * At the start: the US state's list in the app is left aside on a phone in Europe 7 days after
	 * the first start (only the app's own US towns stay); downloaded lists not used for 60 days go.
	 */
	static void tidy(Context c) {
		SharedPreferences p = Fetcher.prefs(c);
		long now = System.currentTimeMillis();
		if (!p.contains("okSince")) p.edit().putLong("okSince", now).apply();
		if (!p.getBoolean("okDropped", false) && !Region.usPhone(c) && now - p.getLong("okSince", now) > BUNDLED_DAYS * DAY) {
			p.edit().putBoolean("okDropped", true).apply();
			synchronized (Places.class) {
				loaded = false;
			}
			Log.i(c, "LOCALITĂȚI", "telefon în Europa: lista ascunsă din SUA scoasă după 7 zile");
		}
		File[] fs = dir(c).listFiles();
		if (fs != null) for (File f : fs) {
			String a = f.getName().replace(".txt", "");
			long used = p.getLong("plUsed_" + a, f.lastModified());
			if (now - used > UNUSED_DAYS * DAY && f.delete()) {
				p.edit().remove("plUsed_" + a).apply();
				synchronized (Places.class) {
					loaded = false;
				}
				Log.i(c, "LOCALITĂȚI", "lista " + a + " ștearsă (nefolosită " + UNUSED_DAYS + " de zile)");
			}
		}
	}

	/**
	 * The phone was located in COUNTRY (COUNTY = the US state): the first time there, with internet,
	 * the area's small localities are downloaded in the background. Call it after a location found online.
	 */
	static void located(final Context ctx, String country, String county) {
		final String a = area(country, county);
		if (a == null) return;
		final SharedPreferences p = Fetcher.prefs(ctx);
		final long now = System.currentTimeMillis();
		p.edit().putLong("plUsed_" + a, now).apply();
		synchronized (Places.class) {
			areas(ctx);
			if (areas.containsKey(a) || busy) return;
		}
		if (now - p.getLong("plTry_" + a, 0) < RETRY_DAYS * DAY) return;
		busy = true;
		final Context app = ctx.getApplicationContext();
		new Thread(new Runnable() {
			public void run() {
				try {
					download(app, a);
				} catch (Exception e) {
					p.edit().putLong("plTry_" + a, System.currentTimeMillis()).apply();
					Log.e(app, "LOCALITĂȚI", "descărcare " + a, e);
				} finally {
					busy = false;
				}
			}
		}).start();
	}

	private static void download(Context c, String a) throws Exception {
		boolean us = a.startsWith("us-");
		String st = us ? Cities.stateName(a.substring(3)) : "";
		// US states: hamlets too (few); a whole European country: villages, towns and cities.
		String q = "[out:csv(name,::lat,::lon;false;\"|\")][timeout:180];"
				+ (us ? "area[\"ISO3166-2\"=\"US-" + a.substring(3) + "\"][admin_level=4]->.a;node(area.a)[place~\"^(city|town|village|hamlet)$\"][name];"
						: "area[\"ISO3166-1\"=\"" + a.toUpperCase() + "\"][admin_level=2]->.a;node(area.a)[place~\"^(city|town|village)$\"][name];")
				+ "out;";
		Log.i(c, "LOCALITĂȚI", "descarc localitățile mici pentru " + a + " (OpenStreetMap)");
		String body = get("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8"));
		StringBuilder b = new StringBuilder("# v4.40: " + a + " din OpenStreetMap (© contribuitorii OpenStreetMap, ODbL). Format: zonă|localitate|lat|lon\n");
		int n = 0;
		for (String line : body.split("\n")) {
			String[] f = line.split("\\|");
			if (f.length != 3 || f[0].trim().length() == 0 || f[0].indexOf('|') >= 0) continue;
			try {
				double la = Double.parseDouble(f[1]), lo = Double.parseDouble(f[2]);
				b.append(st).append('|').append(f[0].trim()).append('|').append(Math.round(la * 10000) / 10000.0).append('|').append(Math.round(lo * 10000) / 10000.0).append('\n');
				n++;
			} catch (NumberFormatException e) {
				// a line without coordinates
			}
		}
		if (n < 10) throw new Exception("doar " + n + " localități");
		File tmp = new File(dir(c), a + ".part"), f = new File(dir(c), a + ".txt");
		FileOutputStream out = new FileOutputStream(tmp);
		out.write(b.toString().getBytes("UTF-8"));
		out.close();
		if (!tmp.renameTo(f)) throw new Exception("redenumire");
		synchronized (Places.class) {
			loaded = false;
		}
		Log.i(c, "LOCALITĂȚI", a + ": " + n + " localități salvate (" + f.length() / 1024 + " KB)");
	}

	private static String get(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(20000);
		c.setReadTimeout(200000);
		c.setRequestProperty("User-Agent", "MeteoAlarmAndroid github.com/viorelrb-alt/meteo-alarm-android");
		try {
			if (c.getResponseCode() / 100 != 2) throw new Exception("HTTP " + c.getResponseCode());
			InputStream in = c.getInputStream();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[16384];
			int n;
			while ((n = in.read(buf)) > 0 && out.size() < 40 * 1024 * 1024) out.write(buf, 0, n);
			in.close();
			return new String(out.toByteArray(), "UTF-8");
		} finally {
			c.disconnect();
		}
	}
}
