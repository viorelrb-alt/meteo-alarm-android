package ro.meteoalarm.android;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import ro.meteoalarm.android.core.Visits;

/**
 * v4.30 (Viorel 2026-11-10): ☰ › Export / Import. The towns, favourites, villages passed through,
 * parcels and settings in one JSON file, to move them to another device. Import adds what is
 * missing (Add) or makes everything as in the file (Replace).
 */
final class Backup {
	/** Lists of lines: the towns found by location, the favourites (EU, US). */
	static final String[] LINES = { "myCities", "favs", "favsUS" };
	/** The settings carried over (not notifications or location: those need the phone's permission). */
	static final String[] SETTINGS = { "theme", "palette", "lang", "textScale", "keepOn", "nightClock", "farmer", "compassTowns",
			"imp_eu", "imp_us", "clk_eu", "clk_us", "widgetBg", "iconTheme" };

	private Backup() {
	}

	static String fileName() {
		return "MeteoAlarm-" + new java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(new java.util.Date()) + ".json";
	}

	/** This phone's data as the file's text. */
	static String export(Context ctx) throws Exception {
		SharedPreferences p = Fetcher.prefs(ctx);
		Map<String, ?> all = p.getAll();
		JSONObject o = new JSONObject();
		o.put("app", "meteo-alarm-android");
		o.put("format", 1);
		o.put("version", MainActivity.VERSION);
		o.put("time", System.currentTimeMillis());
		o.put("device", Log.device());
		for (String k : LINES) o.put(k, p.getString(k, ""));
		o.put("visits", p.getString("visits", ""));
		o.put("parcels", p.getString("parcels", ""));
		o.put("city", p.getString("city", ""));
		JSONObject s = new JSONObject();
		for (String k : SETTINGS) {
			Object v = all.get(k);
			if (v == null) continue;
			JSONObject t = new JSONObject();
			t.put("t", v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof Long ? "l" : v instanceof Float ? "f" : "s");
			t.put("v", String.valueOf(v));
			s.put(k, t);
		}
		o.put("settings", s);
		return o.toString(1);
	}

	/** Writes the file in the cache (for sharing); null if it fails. */
	static File exportFile(Context ctx) {
		try {
			File dir = new File(ctx.getCacheDir(), "data");
			dir.mkdirs();
			File[] old = dir.listFiles();
			if (old != null) for (File f : old) f.delete();
			File f = new File(dir, fileName());
			FileOutputStream out = new FileOutputStream(f);
			out.write(export(ctx).getBytes("UTF-8"));
			out.close();
			return f;
		} catch (Exception e) {
			Log.e(ctx, "EXPORT", "fișier", e);
			return null;
		}
	}

	/** The file's text read back; null when it is not an export of this app. */
	static JSONObject read(String text) {
		try {
			JSONObject o = new JSONObject(text);
			return "meteo-alarm-android".equals(o.optString("app")) ? o : null;
		} catch (Exception e) {
			return null;
		}
	}

	static int lines(String s) {
		int n = 0;
		if (s != null) for (String l : s.split("\n")) if (l.trim().length() > 0) n++;
		return n;
	}

	/** {towns, favourites, parcels, settings} in O (or on this phone when O is null). */
	static int[] counts(Context ctx, JSONObject o) {
		SharedPreferences p = Fetcher.prefs(ctx);
		if (o == null) {
			int set = 0;
			for (String k : SETTINGS) if (p.contains(k)) set++;
			return new int[] { lines(p.getString("myCities", "")), lines(p.getString("favs", "")) + lines(p.getString("favsUS", "")),
					lines(p.getString("parcels", "")), set };
		}
		JSONObject s = o.optJSONObject("settings");
		return new int[] { lines(o.optString("myCities")), lines(o.optString("favs")) + lines(o.optString("favsUS")), lines(o.optString("parcels")),
				s == null ? 0 : s.length() };
	}

	/** Puts O's data on this phone: REPLACE makes everything as in the file, else adds what is missing. */
	static void apply(Context ctx, JSONObject o, boolean replace) {
		SharedPreferences p = Fetcher.prefs(ctx);
		SharedPreferences.Editor e = p.edit();
		for (String k : LINES) {
			String in = o.optString(k, "");
			if (replace) { e.putString(k, in); continue; }
			LinkedHashSet<String> set = new LinkedHashSet<String>();
			for (String l : (p.getString(k, "") + "\n" + in).split("\n")) if (l.trim().length() > 0) set.add(l);
			StringBuilder b = new StringBuilder();
			for (String l : set) b.append(l).append('\n');
			e.putString(k, b.toString());
		}
		Visits v = new Visits(o.optString("visits", ""));
		if (!replace) {
			Visits mine = new Visits(p.getString("visits", ""));
			mine.merge(v);
			v = mine;
		}
		e.putString("visits", v.toString());
		// Parcels by id: Add keeps this phone's parcel when both have it.
		List<Parcels.Parcel> mine = replace ? new ArrayList<Parcels.Parcel>() : Parcels.all(ctx);
		for (String l : o.optString("parcels", "").split("\n")) {
			Parcels.Parcel x = Parcels.Parcel.of(l);
			if (x == null) continue;
			boolean have = false;
			for (Parcels.Parcel m : mine) if (m.id == x.id) have = true;
			if (!have) mine.add(x);
		}
		StringBuilder b = new StringBuilder();
		for (Parcels.Parcel x : mine) b.append(x.line()).append('\n');
		e.putString("parcels", b.toString());
		if (replace && o.optString("city", "").length() > 0) e.putString("city", o.optString("city"));
		JSONObject s = o.optJSONObject("settings");
		if (s != null) for (Iterator<String> it = s.keys(); it.hasNext();) {
			String k = it.next();
			if (!known(k) || (!replace && p.contains(k))) continue;
			JSONObject t = s.optJSONObject(k);
			if (t == null) continue;
			String ty = t.optString("t"), val = t.optString("v");
			try {
				if (ty.equals("b")) e.putBoolean(k, Boolean.parseBoolean(val));
				else if (ty.equals("i")) e.putInt(k, Integer.parseInt(val));
				else if (ty.equals("l")) e.putLong(k, Long.parseLong(val));
				else if (ty.equals("f")) e.putFloat(k, Float.parseFloat(val));
				else e.putString(k, val);
			} catch (NumberFormatException x) {
				// a broken value: left as it is
			}
		}
		e.commit();
		int[] n = counts(ctx, null);
		Log.i(ctx, "IMPORT", (replace ? "înlocuit" : "adăugat") + ": " + n[0] + " localități, " + n[1] + " favorite, " + n[2] + " parcele, " + v.size() + " locuri vizitate");
	}

	private static boolean known(String k) {
		for (String x : SETTINGS) if (x.equals(k)) return true;
		return false;
	}
}
