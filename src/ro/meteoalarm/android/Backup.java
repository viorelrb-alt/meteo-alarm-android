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
			"imp_eu", "imp_us", "clk_eu", "clk_us", "widgetBg", "iconTheme", "luxIcons", "clk12", "unitF" };
	/** v2.120: the notification switches (the phone still asks its own permission). */
	static final String[] ALERTS = { "notify", "quakeNotify", "tornadoNotify" };
	/** v2.120: the parcels' map. */
	static final String[] MAP = { "mapStyle", "mapRadar" };

	/** v2.120 (Viorel): what goes into the file, chosen with switches in the Export window. */
	static final int TOWNS = 0, PARCELS = 1, BELLS = 2, BIN = 3, VISITS = 4, LOOK = 5, ALERT = 6, MAPS = 7, WIDGETS = 8, QUAKES = 9, LOG = 10, N = 11;

	static final class Sel {
		final boolean[] on = new boolean[N];
		/** The town lines and parcel ids left out (all others go in). */
		final java.util.Set<String> offTowns = new java.util.HashSet<String>();
		final java.util.Set<Long> offParcels = new java.util.HashSet<Long>();

		/** The last choice of this phone (everything on but the bin, the quakes and the log the first time). */
		static Sel load(Context ctx) {
			SharedPreferences p = Fetcher.prefs(ctx);
			Sel s = new Sel();
			String f = p.getString("expSel", "11101111100");
			for (int i = 0; i < N; i++) s.on[i] = i < f.length() ? f.charAt(i) == '1' : false;
			for (String l : p.getString("expOffTowns", "").split("\n")) if (l.length() > 0) s.offTowns.add(l);
			for (String l : p.getString("expOffParcels", "").split(",")) try {
				if (l.length() > 0) s.offParcels.add(Long.parseLong(l));
			} catch (NumberFormatException e) {
				// skip
			}
			return s;
		}

		void save(Context ctx) {
			StringBuilder f = new StringBuilder();
			for (boolean b : on) f.append(b ? '1' : '0');
			StringBuilder t = new StringBuilder();
			for (String l : offTowns) t.append(l).append('\n');
			StringBuilder q = new StringBuilder();
			for (Long id : offParcels) q.append(q.length() > 0 ? "," : "").append(id);
			Fetcher.prefs(ctx).edit().putString("expSel", f.toString()).putString("expOffTowns", t.toString()).putString("expOffParcels", q.toString()).apply();
		}

		/** Everything (the old Export, and Import without choices). */
		static Sel all() {
			Sel s = new Sel();
			for (int i = 0; i < N; i++) s.on[i] = i != LOG;
			return s;
		}
	}

	private Backup() {
	}

	static String fileName() {
		return "MeteoAlarm-" + new java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(new java.util.Date()) + ".json";
	}

	/** The lines of KEY on this phone. */
	static List<String> lines(SharedPreferences p, String key) {
		List<String> l = new ArrayList<String>();
		for (String x : p.getString(key, "").split("\n")) if (x.trim().length() > 0) l.add(x);
		return l;
	}

	private static String keep(SharedPreferences p, String key, java.util.Set<String> off) {
		StringBuilder b = new StringBuilder();
		for (String l : lines(p, key)) if (!off.contains(l)) b.append(l).append('\n');
		return b.toString();
	}

	private static void typed(JSONObject into, String k, Object v) throws Exception {
		if (v == null) return;
		JSONObject t = new JSONObject();
		t.put("t", v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof Long ? "l" : v instanceof Float ? "f" : "s");
		t.put("v", String.valueOf(v));
		into.put(k, t);
	}

	private static JSONObject typed(Map<String, ?> all, String[] keys) throws Exception {
		JSONObject s = new JSONObject();
		for (String k : keys) typed(s, k, all.get(k));
		return s;
	}

	/** This phone's data as the file's text: everything. */
	static String export(Context ctx) throws Exception {
		return export(ctx, Sel.all());
	}

	/** v2.120: only what SEL holds. */
	static String export(Context ctx, Sel sel) throws Exception {
		SharedPreferences p = Fetcher.prefs(ctx);
		Map<String, ?> all = p.getAll();
		JSONObject o = new JSONObject();
		o.put("app", "meteo-alarm-android");
		o.put("format", 2);
		o.put("version", MainActivity.VERSION);
		o.put("time", System.currentTimeMillis());
		o.put("device", Log.device());
		if (sel.on[TOWNS]) {
			for (String k : LINES) o.put(k, keep(p, k, sel.offTowns));
			o.put("city", p.getString("city", ""));
		}
		if (sel.on[VISITS]) o.put("visits", p.getString("visits", ""));
		java.util.List<Long> ids = new ArrayList<Long>();
		if (sel.on[PARCELS]) {
			StringBuilder b = new StringBuilder();
			for (Parcels.Parcel x : Parcels.all(ctx)) {
				if (sel.offParcels.contains(x.id)) continue;
				b.append(x.line()).append('\n');
				ids.add(x.id);
			}
			o.put("parcels", b.toString());
		}
		if (sel.on[BELLS] && ids.size() > 0) {
			JSONObject pp = new JSONObject();
			for (Map.Entry<String, ?> en : all.entrySet()) {
				String k = en.getKey();
				if (!parcelKey(k)) continue;
				long id = Long.parseLong(k.replaceAll("^p[np]_(\\d+).*$", "$1"));
				if (ids.contains(id)) typed(pp, k, en.getValue());
			}
			o.put("parcelPrefs", pp);
		}
		if (sel.on[BIN]) o.put("parcelBin", p.getString("parcelBin", ""));
		if (sel.on[LOOK]) o.put("settings", typed(all, SETTINGS));
		if (sel.on[ALERT]) o.put("alerts", typed(all, ALERTS));
		if (sel.on[MAPS]) o.put("map", typed(all, MAP));
		if (sel.on[WIDGETS]) {
			org.json.JSONArray a = new org.json.JSONArray();
			for (Map.Entry<Integer, String> w : Widget.all(ctx).entrySet()) {
				Widget.Set ws = Widget.Set.of(ctx, w.getKey());
				JSONObject x = new JSONObject();
				x.put("kind", w.getValue());
				x.put("town", ws.town);
				x.put("bg", ws.bg);
				x.put("alpha", ws.alpha);
				x.put("colors", ws.colors);
				x.put("font", ws.font);
				x.put("mode", ws.mode);
				a.put(x);
			}
			o.put("widgets", a);
		}
		if (sel.on[QUAKES]) {
			JSONObject q = new JSONObject();
			for (Map.Entry<String, ?> en : all.entrySet()) if (en.getKey().startsWith("qh_")) typed(q, en.getKey(), en.getValue());
			o.put("quakeHistory", q);
		}
		if (sel.on[LOG]) o.put("log", Log.export(ctx));
		return o.toString(1);
	}

	/** v2.120: a parcel's notification switches: pn_ID_r/_w/_s and pp_ID. */
	static boolean parcelKey(String k) {
		return k.matches("pn_\\d+_[a-z]+|pp_\\d+");
	}

	/** v2.120: how many of each kind there are in O (or on this phone when O is null), -1 = not in the file. */
	static int[] what(Context ctx, JSONObject o) {
		int[] n = new int[N];
		SharedPreferences p = Fetcher.prefs(ctx);
		if (o == null) {
			Map<String, ?> all = p.getAll();
			n[TOWNS] = lines(p, "myCities").size() + lines(p, "favs").size() + lines(p, "favsUS").size();
			n[PARCELS] = Parcels.all(ctx).size();
			for (String k : all.keySet()) if (parcelKey(k)) n[BELLS]++;
			n[BIN] = lines(p, "parcelBin").size();
			n[VISITS] = new Visits(p.getString("visits", "")).size();
			for (String k : SETTINGS) if (all.containsKey(k)) n[LOOK]++;
			for (String k : ALERTS) if (all.containsKey(k)) n[ALERT]++;
			for (String k : MAP) if (all.containsKey(k)) n[MAPS]++;
			n[WIDGETS] = Widget.all(ctx).size();
			for (String k : all.keySet()) if (k.startsWith("qh_")) n[QUAKES]++;
			n[LOG] = 1;
			return n;
		}
		java.util.Arrays.fill(n, -1);
		if (o.has("myCities") || o.has("favs")) n[TOWNS] = lines(o.optString("myCities")) + lines(o.optString("favs")) + lines(o.optString("favsUS"));
		if (o.has("parcels")) n[PARCELS] = lines(o.optString("parcels"));
		if (o.optJSONObject("parcelPrefs") != null) n[BELLS] = o.optJSONObject("parcelPrefs").length();
		if (o.has("parcelBin")) n[BIN] = lines(o.optString("parcelBin"));
		if (o.has("visits")) n[VISITS] = new Visits(o.optString("visits")).size();
		if (o.optJSONObject("settings") != null) n[LOOK] = o.optJSONObject("settings").length();
		if (o.optJSONObject("alerts") != null) n[ALERT] = o.optJSONObject("alerts").length();
		if (o.optJSONObject("map") != null) n[MAPS] = o.optJSONObject("map").length();
		if (o.optJSONArray("widgets") != null) n[WIDGETS] = o.optJSONArray("widgets").length();
		if (o.optJSONObject("quakeHistory") != null) n[QUAKES] = o.optJSONObject("quakeHistory").length();
		for (int i = 0; i < N; i++) if (n[i] == 0) n[i] = -1;
		n[LOG] = -1;
		return n;
	}

	/** Writes the file in the cache (for sharing); null if it fails. */
	static File exportFile(Context ctx) {
		return exportFile(ctx, Sel.all());
	}

	static File exportFile(Context ctx, Sel sel) {
		try {
			File dir = new File(ctx.getCacheDir(), "data");
			dir.mkdirs();
			File[] old = dir.listFiles();
			if (old != null) for (File f : old) f.delete();
			File f = new File(dir, fileName());
			FileOutputStream out = new FileOutputStream(f);
			out.write(export(ctx, sel).getBytes("UTF-8"));
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
		boolean[] on = new boolean[N];
		java.util.Arrays.fill(on, true);
		apply(ctx, o, replace, on);
	}

	/** v2.120: only the kinds switched ON in the Import window. */
	static void apply(Context ctx, JSONObject o, boolean replace, boolean[] on) {
		SharedPreferences p = Fetcher.prefs(ctx);
		SharedPreferences.Editor e = p.edit();
		if (on[TOWNS] && (o.has("myCities") || o.has("favs"))) {
			for (String k : LINES) {
				String in = o.optString(k, "");
				if (replace) { e.putString(k, in); continue; }
				LinkedHashSet<String> set = new LinkedHashSet<String>();
				for (String l : (p.getString(k, "") + "\n" + in).split("\n")) if (l.trim().length() > 0) set.add(l);
				StringBuilder b = new StringBuilder();
				for (String l : set) b.append(l).append('\n');
				e.putString(k, b.toString());
			}
			if (replace && o.optString("city", "").length() > 0) e.putString("city", o.optString("city"));
		}
		Visits v = null;
		if (on[VISITS] && o.has("visits")) {
			v = new Visits(o.optString("visits", ""));
			if (!replace) {
				Visits mine = new Visits(p.getString("visits", ""));
				mine.merge(v);
				v = mine;
			}
			e.putString("visits", v.toString());
		}
		// Parcels by id: Add keeps this phone's parcel when both have it.
		if (on[PARCELS] && o.has("parcels")) {
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
		}
		if (on[BELLS]) put(e, p, o.optJSONObject("parcelPrefs"), null, replace);
		if (on[BIN] && o.has("parcelBin")) {
			if (replace) e.putString("parcelBin", o.optString("parcelBin"));
			else {
				LinkedHashSet<String> set = new LinkedHashSet<String>(lines(p, "parcelBin"));
				for (String l : o.optString("parcelBin").split("\n")) if (l.trim().length() > 0) set.add(l);
				StringBuilder b = new StringBuilder();
				for (String l : set) b.append(l).append('\n');
				e.putString("parcelBin", b.toString());
			}
		}
		if (on[LOOK]) put(e, p, o.optJSONObject("settings"), SETTINGS, replace);
		if (on[ALERT]) put(e, p, o.optJSONObject("alerts"), ALERTS, replace);
		if (on[MAPS]) put(e, p, o.optJSONObject("map"), MAP, replace);
		if (on[QUAKES]) put(e, p, o.optJSONObject("quakeHistory"), null, replace);
		e.commit();
		// The widgets: each setting goes to a widget of the same kind on this phone, in order.
		org.json.JSONArray ws = o.optJSONArray("widgets");
		if (on[WIDGETS] && ws != null) {
			java.util.Set<Integer> used = new java.util.HashSet<Integer>();
			int n = 0;
			for (int i = 0; i < ws.length(); i++) {
				JSONObject x = ws.optJSONObject(i);
				if (x == null) continue;
				for (Map.Entry<Integer, String> w : Widget.all(ctx).entrySet()) {
					if (used.contains(w.getKey()) || !w.getValue().equals(x.optString("kind"))) continue;
					used.add(w.getKey());
					Widget.Set st = Widget.Set.of(ctx, w.getKey());
					st.town = x.optString("town", st.town);
					st.bg = x.optBoolean("bg", st.bg);
					st.alpha = x.optInt("alpha", st.alpha);
					st.colors = x.optString("colors", st.colors);
					st.font = x.optString("font", st.font);
					st.mode = x.optString("mode", st.mode);
					st.save(ctx, w.getKey());
					n++;
					break;
				}
			}
			Log.i(ctx, "IMPORT", "setări puse pe " + n + " din " + ws.length() + " widget-uri");
		}
		int[] c = counts(ctx, null);
		Log.i(ctx, "IMPORT", (replace ? "înlocuit" : "adăugat") + ": " + c[0] + " localități, " + c[1] + " favorite, " + c[2] + " parcele" + (v != null ? ", " + v.size() + " locuri vizitate" : ""));
	}

	/** The typed values of S on this phone: KNOWN keys only (null = parcel or quake keys); Add keeps what is set. */
	private static void put(SharedPreferences.Editor e, SharedPreferences p, JSONObject s, String[] known, boolean replace) {
		if (s == null) return;
		for (Iterator<String> it = s.keys(); it.hasNext();) {
			String k = it.next();
			boolean ok = known != null ? in(known, k) : parcelKey(k) || k.startsWith("qh_");
			if (!ok || (!replace && p.contains(k))) continue;
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
	}

	private static boolean in(String[] a, String k) {
		for (String x : a) if (x.equals(k)) return true;
		return false;
	}

	private static boolean known(String k) {
		for (String x : SETTINGS) if (x.equals(k)) return true;
		return false;
	}
}
