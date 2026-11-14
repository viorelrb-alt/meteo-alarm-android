package ro.meteoalarm.android;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.RemoteViews;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ro.meteoalarm.android.core.Tr;
import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Texts;
import ro.meteoalarm.android.core.Warnings;
import ro.meteoalarm.android.core.Weather;

/**
 * Home-screen widgets in the style of the phone's weather widget: the weather icon, a big
 * temperature, the town and the next sunset (or sunrise). Three layouts after Viorel's mockups,
 * 2x2 (square), 4x1 (4 : 1) and 4x2 (2 : 1); a widget always takes the one closest to its frame's
 * shape and fills the frame (Viorel 2026-11-02): resizing it moves from one to the other, larger
 * frames zoom it. Each widget has its own settings (WidgetConfig): town, background (on / off,
 * how see-through), its colours (by the weather, the phone's, dark) and the text colour. The data
 * is what the app saved (Refresher brings it in the background). A tap opens the app.
 */
public class Widget extends AppWidgetProvider {
	/** The widget list's entries (Viorel 2026-11-02), like the phone's own weather widgets: the size they start with. */
	public static final class W2x2 extends Widget {
	}

	public static final class W4x1 extends Widget {
	}

	public static final class W4x2 extends Widget {
	}

	/** 4x2 with the next days under the weather now (Viorel 2026-11-04, v2.10). */
	public static final class W4x2d extends Widget {
	}

	/** v4.10 (Viorel): like the phone's weather widget, a big temperature on the left and a list of the next days (one icon each) or hours on the right. */
	public static final class W4x2l extends Widget {
	}

	static final Class<?>[] KINDS = { Widget.class, W2x2.class, W4x1.class, W4x2.class, W4x2d.class, W4x2l.class };

	@Override
	public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
		show(ctx);
		// The weather comes with the refresh in the background, if it is due.
		ctx.sendBroadcast(new Intent(ctx, Refresher.class));
	}

	@Override
	public void onDeleted(Context ctx, int[] ids) {
		SharedPreferences.Editor e = prefs(ctx).edit();
		for (int id : ids) {
			for (String k : new String[] { "town", "bg", "alpha", "colors", "font", "seen", "mode", "cell" }) e.remove("w" + id + "_" + k);
			Log.i(ctx, "WIDGET", "#" + id + " șters de pe ecran");
		}
		e.apply();
	}

	static SharedPreferences prefs(Context ctx) {
		return ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
	}

	/** Every widget on the home screen: id to its entry's class name ("W2x2"…, "Widget"). */
	static Map<Integer, String> all(Context ctx) {
		Map<Integer, String> m = new LinkedHashMap<Integer, String>();
		AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
		for (Class<?> c : KINDS) {
			int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, c));
			if (ids != null) for (int id : ids) m.put(id, c.getSimpleName());
		}
		return m;
	}

	/** Draws every widget again from the weather on the phone (the app calls it after a fetch). */
	static void show(Context ctx) {
		AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
		Map<String, Data> byTown = new HashMap<String, Data>();
		for (int id : all(ctx).keySet()) {
			Set st = Set.of(ctx, id);
			Data d = byTown.get(st.town);
			if (d == null) {
				d = data(ctx, st.town);
				byTown.put(st.town, d);
			}
			draw(ctx, mgr, id, d, st);
		}
	}

	@Override
	public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, android.os.Bundle opts) {
		Set st = Set.of(ctx, id);
		draw(ctx, mgr, id, data(ctx, st.town), st);
	}

	// --- Settings of one widget ----------------------------------------------------------------

	/** One widget's settings (prefs "w<id>_…"). */
	static final class Set {
		/** The town's key, empty = the app's town. */
		String town = "";
		boolean bg;
		/** How solid the background is, 0–100 %. */
		int alpha = 60;
		/** "weather" (by the weather, Viorel's item 20), "phone" (the phone's colours), "dark". */
		String colors = "weather";
		/** "auto", "white", "black". */
		String font = "auto";
		/** v4.10, the list widget: "days" or "hours". */
		String mode = "days";

		static Set of(Context ctx, int id) {
			SharedPreferences p = prefs(ctx);
			Set s = new Set();
			String k = "w" + id + "_";
			s.town = p.getString(k + "town", "");
			// Before v1.90 one setting for all: the background's opacity (0 = none).
			int old = p.getInt("widgetBg", 0);
			s.bg = p.getBoolean(k + "bg", old > 0);
			s.alpha = p.getInt(k + "alpha", old > 0 ? old : 60);
			s.colors = p.getString(k + "colors", old > 0 ? "dark" : "weather");
			s.font = p.getString(k + "font", "auto");
			s.mode = p.getString(k + "mode", "days");
			return s;
		}

		void save(Context ctx, int id) {
			String k = "w" + id + "_";
			prefs(ctx).edit().putString(k + "town", town).putBoolean(k + "bg", bg).putInt(k + "alpha", alpha)
					.putString(k + "colors", colors).putString(k + "font", font).putString(k + "mode", mode).apply();
		}

		String describe() {
			return "oraș=" + (town.length() == 0 ? "aplicație" : town) + " fundal=" + (bg ? alpha + "%" : "nu") + " culori=" + colors + " font=" + font + " mod=" + mode;
		}
	}

	/** The towns chosen for widgets other than the app's (the background refresh brings their forecast too). */
	static List<Cities.City> ownTowns(Context ctx) {
		List<Cities.City> l = new ArrayList<Cities.City>();
		for (int id : all(ctx).keySet()) {
			Cities.City c = Cities.City.fromKey(Set.of(ctx, id).town);
			if (c == null) continue;
			boolean dup = false;
			for (Cities.City o : l) dup |= o.key().equals(c.key());
			if (!dup) l.add(c);
		}
		return l;
	}

	/** The forecast file of a widget's own town. */
	static String townFile(Cities.City c) {
		return "wf_" + Integer.toHexString(c.key().hashCode()) + ".json";
	}

	// --- Data ----------------------------------------------------------------------------------

	/** What a widget shows (one per town). */
	static final class Data {
		Weather w;
		Texts T;
		boolean en, unitF, clk12;
		Cities.City c;
		int[] levels = new int[0];
		/** v4.50: without codes, the app's first information still in force ("BRUMĂ"), else null. */
		String event;
	}

	/** The data for TOWN (a key; empty = the app's town). */
	static Data data(Context ctx, String town) {
		Data d = new Data();
		SharedPreferences p = prefs(ctx);
		d.en = Lang.en(ctx);
		d.T = new Texts(Lang.apply(ctx));
		Cities.City cached = Cities.City.fromKey(p.getString("cacheCity", ""));
		Cities.City own = Cities.City.fromKey(town == null ? "" : town);
		boolean appTown = own == null || (cached != null && own.same(cached));
		try {
			if (appTown) {
				d.c = Cities.City.fromKey(p.getString("city", ""));
				// The town the app last downloaded the weather for, when it is the same place.
				if (cached != null && (d.c == null || cached.same(d.c))) d.c = cached;
				if (d.c != null && d.c.key().equals(p.getString("cacheCity", ""))) d.w = Weather.parse(read(new File(ctx.getFilesDir(), "forecast.json")));
			} else {
				d.c = own;
				File f = new File(ctx.getFilesDir(), townFile(own));
				if (f.exists()) d.w = Weather.parse(read(f));
			}
		} catch (Exception e) {
			d.w = null;
		}
		// Units and clock as the app shows them for this kind of town (US or not, v2.30).
		d.unitF = Region.imperial(ctx, d.c);
		d.clk12 = Region.clk12(ctx, d.c);
		// The warnings the app saved (the country's feed): which codes (1 yellow, 2 orange, 3 red) are active here.
		try {
			if (d.c != null && cached != null && cached.country.equals(d.c.country)) {
				List<Warnings.Warn> ws = Fetcher.warnings(ctx, d.c);
				boolean[] has = new boolean[4];
				for (Warnings.Warn x : ws) if (x.level >= 1 && x.level <= 3) has[x.level] = true;
				int n = 0;
				for (int k = 3; k >= 1; k--) if (has[k]) n++;
				d.levels = new int[n];
				n = 0;
				for (int k = 3; k >= 1; k--) if (has[k]) d.levels[n++] = k;
			}
		} catch (Exception e) {
			d.levels = new int[0];
		}
		if (d.levels.length == 0) d.event = event(ctx, d, cached);
		return d;
	}

	/**
	 * v4.50 (Viorel): the app's own information (frost, fog, freezing rain, wind...) still in force
	 * or the next one, as on the main screen; null in the US, without a forecast or without any.
	 */
	static String event(Context ctx, Data d, Cities.City cached) {
		try {
			Weather w = d.w;
			if (w == null || d.c == null || d.c.us() || w.tileTemp.length == 0) return null;
			long now = System.currentTimeMillis();
			String off = "";
			if (cached != null && cached.country.equals(d.c.country))
				for (Warnings.Warn x : Fetcher.warnings(ctx, d.c)) if (!x.nws && x.until > now) off += x.type + ",";
			int n = Math.min(w.tileTemp.length, 25);
			double[] code = new double[n], day = new double[n];
			for (int k = 0; k < n; k++) {
				code[k] = k < w.tileCode.length ? w.tileCode[k] : 0;
				day[k] = k < w.tileDay.length && !w.tileDay[k] ? 0 : 1;
			}
			int month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1;
			int at = Math.max(0, w.hourIndex(now));
			ro.meteoalarm.android.core.Screen.OwnHit best = null;
			for (ro.meteoalarm.android.core.Screen.OwnHit h : ro.meteoalarm.android.core.Screen.ownHits(w.tileTemp, w.tileCloud, w.tileWind, w.tileGust, code, day, 0, n, month, off))
				if (h.b >= at && (best == null || h.a < best.a)) best = h;
			if (best == null) return null;
			String name = ro.meteoalarm.android.core.Screen.ownName(best.key);
			return name.substring(0, 1) + name.substring(1).toLowerCase();
		} catch (Exception e) {
			return null;
		}
	}

	static final int RED = 0xFFFD0000, ORANGE = 0xFFFD8B00, YELLOW = 0xFFDFD51B, GRAY = 0xFFA9A9A9;
	/** v4.50: the line of the app's own information (the blue of the INFORMARE boxes). */
	static final int INFO = 0xFF4FA3E0;

	/**
	 * The three pills of the warnings row (Viorel's rule): yellow = gray gray yellow; orange = gray
	 * orange orange; red = red red red; two codes = the worse one, gray, the other; three = red orange yellow.
	 */
	static int[] pills(int[] levels) {
		int[] col = { GRAY, YELLOW, ORANGE, RED };
		if (levels.length == 1) {
			int l = levels[0];
			return l == 1 ? new int[] { GRAY, GRAY, YELLOW } : l == 2 ? new int[] { GRAY, ORANGE, ORANGE } : new int[] { RED, RED, RED };
		}
		if (levels.length == 2) return new int[] { col[levels[0]], GRAY, col[levels[1]] };
		return new int[] { RED, ORANGE, YELLOW };
	}

	// --- Size and layout -----------------------------------------------------------------------

	/**
	 * The frame of widget ID in dp, { width, height }, as the phone lays it out now. Android 12+
	 * gives the exact sizes (portrait and landscape); older ones the min / max (portrait: the min
	 * width and the max height).
	 */
	static int[] frame(Context ctx, AppWidgetManager mgr, int id, StringBuilder why) {
		android.os.Bundle o = mgr.getAppWidgetOptions(id);
		boolean land = ctx.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
		int minW = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), maxW = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
		int minH = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT), maxH = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
		int w = land ? maxW : minW, h = land ? minH : maxH;
		why.append("min ").append(minW).append('x').append(minH).append(" max ").append(maxW).append('x').append(maxH);
		try {
			java.util.ArrayList<?> sizes = o == null ? null : o.getParcelableArrayList("appWidgetSizes");
			if (sizes != null && !sizes.isEmpty()) {
				why.append(" sizes");
				float bw = 0, bh = 0;
				for (Object s : sizes) {
					android.util.SizeF z = (android.util.SizeF) s;
					why.append(' ').append(Math.round(z.getWidth())).append('x').append(Math.round(z.getHeight()));
					// Portrait: the tallest of them; landscape: the widest.
					if (bw == 0 || (land ? z.getWidth() > bw : z.getHeight() > bh)) {
						bw = z.getWidth();
						bh = z.getHeight();
					}
				}
				w = Math.round(bw);
				h = Math.round(bh);
			}
		} catch (Throwable t) {
			// Android 11 and older
		}
		if (w <= 0) w = 180;
		if (h <= 0) h = 180;
		return new int[] { w, h };
	}

	/**
	 * The layouts' shapes (dp of Viorel's mockups): 2x2 = 192 x 192, 4x1 = 384 x 96, 4x2 = 384 x 192,
	 * and the small one, 2x1 = 192 x 96 (icon and temperature only, for frames too small for the others).
	 */
	static final float[] RW = { 192, 384, 384, 192, 384, 384, 384 }, RH = { 192, 96, 192, 96, 192, 280, 192 };
	/** Below this zoom the texts get too small: such a layout is not used (logs of 2026-11-02: 68% was too small). */
	static final float MIN_ZOOM = 0.8f;
	/** The 4x3 needs at least this part of its height (v2.20). */
	static final float TALL = 0.95f;
	/** How much taller than the layout (part of its height) the widget may spread; more is left empty. */
	static final float MAX_SPREAD = 0.25f;

	/**
	 * The layout for a frame W x H dp: 0 = 2x2, 1 = 4x1, 2 = 4x2, 3 = 2x1. By the width first
	 * (Viorel 2026-11-02, logs of v1.91: a 4x3 frame became a zoomed 2x2): wide enough for a 4-wide
	 * layout (at MIN_ZOOM) = 4x2 when tall enough, else 4x1; narrower = 2x2, or 2x1 when low.
	 * A frame taller than the layout needs keeps the layout (render() centres it).
	 */
	/**
	 * The layout for widget ID in a frame W x H dp, by the cells it covers when the size of a cell
	 * on this home screen is known (Viorel 2026-11-04: on the tablet one row is 197 dp tall, so a
	 * 4x1 always looked like a 4x2). The cell size is learned from the first frame of a widget put
	 * on the screen at its starting size (2x2, 4x1 or 4x2).
	 */
	static int kind(Context ctx, AppWidgetManager mgr, int id, float w, float h) {
		SharedPreferences p = prefs(ctx);
		learnCell(ctx, mgr, id, w, h);
		// The list widget keeps its one layout, zoomed to the frame.
		if (list(mgr, id)) return 6;
		boolean days = days(mgr, id);
		float cw = p.getFloat("cellW", 0), ch = p.getFloat("cellH", 0);
		// A cell smaller than any home screen's (A54 v2.11: 45x90 dp, learned from an empty
		// first frame) is wrong: forgotten, the plain rule until a widget teaches the right one.
		if ((cw > 0 && cw < 60) || (ch > 0 && ch < 60)) {
			p.edit().remove("cellW").remove("cellH").apply();
			Log.i(ctx, "WIDGET", "celula greșită " + Math.round(cw) + "x" + Math.round(ch) + " dp uitată");
			cw = ch = 0;
		}
		if (cw <= 0 || ch <= 0) return kind(w, h, days);
		int cols = Math.max(1, Math.round(w / cw)), rows = Math.max(1, Math.round(h / ch));
		// Wide and 3 rows or more: the 4x3, with the hours and the next days (v2.10).
		int k = rows <= 1 ? (cols >= 3 ? 1 : 3) : (cols >= 3 ? (rows >= 3 ? 5 : days ? 4 : 2) : 0);
		if (k == 5 && zoom(5, w, h) < MIN_ZOOM) k = days ? 4 : 2;
		// Still never shrunk below MIN_ZOOM: then the plain rule.
		return zoom(k, w, h) >= MIN_ZOOM ? k : kind(w, h, days);
	}

	/** The widget shows the next days in its second row (the "4x2 days" entry). */
	static boolean days(AppWidgetManager mgr, int id) {
		try {
			return mgr.getAppWidgetInfo(id).provider.getClassName().endsWith("W4x2d");
		} catch (Exception e) {
			return false;
		}
	}

	/** The list widget (v4.10). */
	static boolean list(AppWidgetManager mgr, int id) {
		try {
			return mgr.getAppWidgetInfo(id).provider.getClassName().endsWith("W4x2l");
		} catch (Exception e) {
			return false;
		}
	}

	/** On a widget's first frame: its cells (from its kind) give the size of one cell. */
	private static void learnCell(Context ctx, AppWidgetManager mgr, int id, float w, float h) {
		SharedPreferences p = prefs(ctx);
		if (p.getBoolean("w" + id + "_cell", false)) return;
		// Only from a real size: One UI 9 sends a first frame without one (0x0), which frame()
		// makes 180x180 (A54 v2.11: a 4x2 learned 45x90 dp cells, so 2 rows counted as 3).
		android.os.Bundle o = mgr.getAppWidgetOptions(id);
		if (o == null || o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) <= 0 || o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) <= 0) return;
		p.edit().putBoolean("w" + id + "_cell", true).apply();
		int cols = 2, rows = 2;
		try {
			String cls = mgr.getAppWidgetInfo(id).provider.getClassName();
			if (cls.endsWith("W4x1")) { cols = 4; rows = 1; }
			else if (cls.endsWith("W4x2") || cls.endsWith("W4x2d") || cls.endsWith("W4x2l")) { cols = 4; rows = 2; }
			else if (!cls.endsWith("W2x2")) return; // "any size": it may start anywhere
		} catch (Exception e) {
			return;
		}
		float cw = w / cols, ch = h / rows;
		if (cw < 40 || ch < 40) return;
		p.edit().putFloat("cellW", cw).putFloat("cellH", ch).apply();
		Log.i(ctx, "WIDGET", "#" + id + " celula ecranului de pornire: " + Math.round(cw) + "x" + Math.round(ch) + " dp (din " + cols + "x" + rows + ")");
	}

	static int kind(float w, float h, boolean days) {
		h = Math.max(1f, h);
		w = Math.max(1f, w);
		boolean wide = w >= RW[2] * MIN_ZOOM;
		int tall = wide ? (days ? 4 : 2) : 0, low = wide ? 1 : 3;
		// The 4x3 only when nearly as tall as it is drawn (a 2-row frame on the A54 is 233 dp:
		// 83 %, which showed the 4x3 instead of the 4x2; 3 rows on a phone are 350 dp or more).
		if (wide && zoom(5, w, h) >= MIN_ZOOM && h >= RH[5] * TALL) return 5;
		if (zoom(tall, w, h) >= MIN_ZOOM) return tall;
		if (zoom(low, w, h) >= MIN_ZOOM) return low;
		// Smaller still: the one shown largest.
		int best = 0;
		for (int k = 1; k < 4; k++) if (zoom(k, w, h) > zoom(best, w, h)) best = k;
		if (best == 2 && days) best = 4;
		return best;
	}

	static float zoom(int k, float w, float h) {
		return Math.min(w / RW[k], h / RH[k]);
	}

	static final String[] NAMES = { "2x2", "4x1", "4x2", "2x1", "4x2", "4x3", "4x2" };

	/** One widget, drawn as a picture of its frame. */
	private static void draw(Context ctx, AppWidgetManager mgr, int id, Data d, Set st) {
		float dens = ctx.getResources().getDisplayMetrics().density;
		StringBuilder why = new StringBuilder();
		int[] f = frame(ctx, mgr, id, why);
		int kind = kind(ctx, mgr, id, f[0], f[1]);
		float s = Math.min(f[0] / RW[kind], f[1] / RH[kind]);
		Log.i(ctx, "WIDGET", "#" + id + " " + why + " -> cadru " + f[0] + "x" + f[1] + " dp, model " + NAMES[kind] + (kind == 4 ? " zile" : kind == 6 ? " listă " + st.mode : "")
				+ " zoom " + Math.round(s * 100) + "% · " + (d.c != null ? d.c.name : "-") + (d.w == null ? " fără date" : "") + " · " + st.describe());
		try {
			send(ctx, mgr, id, d, render(ctx, d, st, f[0], f[1], dens, kind, 0));
			animate(ctx, mgr, id, d, st, f, dens, kind);
		} catch (Throwable e) {
			// Too large a picture for the home screen: half the sharpness, still the same widget.
			Log.e(ctx, "WIDGET", "#" + id + " imagine prea mare, desenez la jumătate", e);
			try {
				send(ctx, mgr, id, d, render(ctx, d, st, f[0], f[1], dens / 2, kind, 1));
			} catch (Throwable e2) {
				Log.e(ctx, "WIDGET", "#" + id + " nu s-a putut desena", e2);
			}
		}
	}

	// --- v4.00 (Viorel): the weather icon moves for 3 s after each drawing, as in the app ----------

	private static final android.os.Handler UI = new android.os.Handler(android.os.Looper.getMainLooper());
	private static final HashMap<Integer, Runnable> RUNS = new HashMap<Integer, Runnable>();
	private static final int FRAMES = 12;

	/** Sends the next frames of widget ID (only while the screen is on; the last one is the still icon). */
	private static void animate(final Context ctx, final AppWidgetManager mgr, final int id, final Data d, final Set st, final int[] f, final float dens, final int kind) {
		synchronized (RUNS) {
			Runnable old = RUNS.remove(id);
			if (old != null) UI.removeCallbacks(old);
		}
		boolean on = true;
		try {
			on = ((android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE)).isInteractive();
		} catch (Throwable t) {
		}
		if (d.w == null) return;
		final int[] k = { on ? 1 : FRAMES };
		Runnable r = new Runnable() {
			public void run() {
				try {
					send(ctx, mgr, id, d, render(ctx, d, st, f[0], f[1], dens, kind, k[0] / (float) FRAMES));
				} catch (Throwable e) {
					return;
				}
				if (++k[0] <= FRAMES) UI.postDelayed(this, 3000 / FRAMES);
				else synchronized (RUNS) {
					RUNS.remove(id);
				}
			}
		};
		synchronized (RUNS) {
			RUNS.put(id, r);
		}
		UI.postDelayed(r, on ? 3000 / FRAMES : 0);
	}

	private static void send(Context ctx, AppWidgetManager mgr, int id, Data d, Bitmap bm) {
		RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
		v.setImageViewBitmap(R.id.widget_image, bm);
		v.setContentDescription(R.id.widget_image, d.w != null ? d.T.describe(d.w.code, d.w.isDay)[1] : "Meteo Alarm");
		Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.FROM_WIDGET, true);
		v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, 0, open,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
		mgr.updateAppWidget(id, v);
	}

	// --- Colours -------------------------------------------------------------------------------

	/** The background's two colours (top, bottom) for ST and the weather W. */
	static int[] background(Context ctx, Set st, Weather w) {
		if (st.colors.equals("phone")) {
			boolean night = (ctx.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
					== android.content.res.Configuration.UI_MODE_NIGHT_YES;
			int c = systemColor(ctx, night ? "system_accent1_800" : "system_accent1_100", night ? 0xFF2D2D30 : 0xFFF2F2F2);
			return new int[] { c, c };
		}
		if (st.colors.equals("dark") || w == null) return new int[] { 0xFF2A2A2D, 0xFF1C1C1E };
		return weatherColors(w.code, w.isDay);
	}

	/** The background by the weather (Viorel's item 20): sun, moon, clouds, rain, snow, storm, fog. */
	static int[] weatherColors(int code, boolean day) {
		if (code >= 95) return new int[] { 0xFF3A3F63, 0xFF1F2238 }; // storm
		if ((code >= 71 && code <= 77) || code == 85 || code == 86) return new int[] { 0xFFD3DEEA, 0xFF9FB3C8 }; // snow
		if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return new int[] { 0xFF4E6A86, 0xFF2C3E52 }; // rain
		if (code == 45 || code == 48) return new int[] { 0xFF9AA1A8, 0xFF6E757C }; // fog
		if (code == 3) return new int[] { 0xFF6F7A85, 0xFF48525C }; // heavy cloud
		if (code == 2) return day ? new int[] { 0xFF5F95C9, 0xFF3C6E9E } : new int[] { 0xFF34405F, 0xFF1D2539 }; // partly
		return day ? new int[] { 0xFF4AA3EC, 0xFF2471C2 } : new int[] { 0xFF26315A, 0xFF111833 }; // sun / moon
	}

	private static int systemColor(Context ctx, String name, int fallback) {
		try {
			if (android.os.Build.VERSION.SDK_INT >= 31) {
				int id = ctx.getResources().getIdentifier(name, "color", "android");
				if (id != 0) return ctx.getResources().getColor(id, null);
			}
		} catch (Throwable t) {
			// fallback
		}
		return fallback;
	}

	private static double luminance(int c) {
		return (((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114) / 255000.0;
	}

	/** The text colour: white with a shadow, or black (on a light, fairly solid background or when chosen). */
	static boolean blackText(Context ctx, Set st, Weather w) {
		if (st.font.equals("black")) return true;
		if (st.font.equals("white")) return false;
		if (!st.bg || st.alpha < 45) return false;
		int[] b = background(ctx, st, w);
		return (luminance(b[0]) + luminance(b[1])) / 2 > 0.62;
	}

	// --- Drawing -------------------------------------------------------------------------------

	/**
	 * Draws the widget WDP x HDP dp at DENS px per dp in layout KIND, filling the frame: the
	 * layout is scaled to the frame and what is left over goes between its left and right parts
	 * (and its top and bottom), so no empty bands at the sides.
	 */
	static Bitmap render(Context ctx, Data d, Set st, int wdp, int hdp, float dens, int kind) {
		return render(ctx, d, st, wdp, hdp, dens, kind, 1);
	}

	/** T: the moment of the icons' movement (0–1, 1 = still). */
	static Bitmap render(Context ctx, Data d, Set st, int wdp, int hdp, float dens, int kind, float t) {
		float rw = RW[kind], rh = RH[kind];
		float s = Math.min(wdp / rw, hdp / rh);
		int W = Math.max(1, Math.round(wdp * dens)), H = Math.max(1, Math.round(hdp * dens));
		Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
		Canvas cv = new Canvas(out);
		Weather w = d.w;
		if (st.bg && st.alpha > 0) {
			int[] b = background(ctx, st, w);
			int a = st.alpha * 255 / 100;
			Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
			float k0 = s * dens, ey0 = H / k0 - rh, t0 = (ey0 - Math.min(ey0, rh * MAX_SPREAD)) / 2 * k0;
			bg.setShader(new LinearGradient(0, t0, 0, H - t0, (a << 24) | (b[0] & 0xFFFFFF), (a << 24) | (b[1] & 0xFFFFFF), Shader.TileMode.CLAMP));
			float r = Math.min(28 * dens, Math.min(W, H - 2 * t0) / 4f);
			cv.drawRoundRect(new RectF(0, t0, W, H - t0), r, r, bg);
			// v5.04 (Viorel): the app's glass look: a thin light edge around the widget.
			Paint eg = new Paint(Paint.ANTI_ALIAS_FLAG);
			eg.setStyle(Paint.Style.STROKE);
			eg.setStrokeWidth(Math.max(1, dens));
			eg.setColor(blackText(ctx, st, w) ? 0x22000000 : 0x38FFFFFF);
			float h0 = eg.getStrokeWidth() / 2;
			cv.drawRoundRect(new RectF(h0, t0 + h0, W - h0, H - t0 - h0), r, r, eg);
		}
		float k = s * dens;
		// Taller than the layout needs (a 4x3 frame for the 4x2): a little of the extra height
		// spreads the layout, the rest stays empty above and below, the widget centred.
		float ey = H / k - rh, keep = Math.min(ey, rh * MAX_SPREAD), top = (ey - keep) / 2 * k;
		cv.translate(0, top);
		cv.scale(k, k);
		ro.meteoalarm.android.core.Theme.pal = Fetcher.prefs(ctx).getInt("palette", 0);
		ro.meteoalarm.android.core.WxIcon.set = Fetcher.iconTheme(Fetcher.prefs(ctx)); // v5.20: as in the app
		Pen pen = new Pen(ctx, cv, blackText(ctx, st, w), W / k - rw, keep);
		pen.t = t;
		String temp = w == null ? "--" : Long.toString(Math.round(d.unitF ? w.temp * 9 / 5 + 32 : w.temp));
		String icon = w == null ? null : d.T.describe(w.code, w.isDay)[0];
		String town = d.c != null ? d.c.name : "";
		String sun = w == null ? (Tr.t("Open the app", "Deschide aplicația")) : sunLine(w, d.en);
		String desc = w == null ? "" : d.T.describe(w.code, w.isDay)[1];
		if (kind == 6) {
			listLayout(pen, d, st, icon, temp, desc, town);
		} else if (kind == 0) {
			pen.at(0, 0);
			pen.icon(icon, 11.1f, 28.8f, 83.3f);
			pen.at(1, 0);
			pen.town(town, 183.5f, 28.0f, 12.4f, 120);
			pen.text(sun, 183.5f, 40.4f, 8.9f, 1, 1f, 3, 115);
			pen.at(1, 1);
			pen.temp(temp, 184.5f, 160.5f, 87.7f, 49.5f, 132.6f, 95, true);
			pen.at(0, 1);
			pen.centred(d, 192f + pen.ex, 171.4f);
		} else if (kind == 3) {
			// The small one: the left part of the 4x1, the temperature pushed to the right edge.
			boolean row = d.levels.length > 0 || d.event != null;
			pen.at(0, 0.5f);
			if (row) pen.icon(icon, 10f, 6f, 68f);
			else pen.icon(icon, 8f, 12f, 72f);
			pen.at(1, 0.5f);
			pen.temp(temp, 184f, row ? 68f : 75.2f, 77.4f, 59.3f, row ? 56f : 63.1f, 100, true);
			// v4.50: the code lines (or the information) under them.
			pen.at(0, 1);
			if (row) pen.warnings(d, 12f, 82.5f, false);
		} else if (kind == 1) {
			pen.at(0, 0.5f);
			pen.icon(icon, 12.9f, 17.3f, 72f);
			pen.temp(temp, 79.9f, 75.2f, 77.4f, 59.3f, 63.1f, 110, false);
			pen.at(1, 0.5f);
			pen.town(town, 369.5f, 23.0f, 13, 170);
			pen.text(desc, 370.1f, 40.6f, 13, 1, 1f, 3, 170);
			pen.text(sun, 370.1f, 57.1f, 13, 1, 1f, 3, 170);
			pen.warnings(d, 260.3f, 69.7f, true);
		} else {
			// The weather now keeps to the top, the next hours to the bottom; the hours spread across.
			pen.at(0, 0);
			pen.icon(icon, 10.3f, 17.1f, 84.4f);
			pen.temp(temp, 89f, 95.4f, 102f, 53.6f, 67.2f, 125, false);
			pen.at(1, 0);
			pen.town(town, 369.5f, 27.5f, 13, 150);
			pen.text(desc, 370.1f, 45.5f, 13, 1, 1f, 3, 150);
			if (w != null && !Double.isNaN(w.tmax) && !Double.isNaN(w.tmin))
				pen.text(d.T.hi + " " + deg(w.tmax, d.unitF) + "° · " + d.T.lo + " " + deg(w.tmin, d.unitF) + "°", 369.1f, 62.3f, 13, 1, 1f, 3, 150);
			pen.text(sun, 369.4f, 80.2f, 13, 1, 1f, 3, 150);
			pen.warnings(d, 260.7f, 101.4f, true);
			// 4x2: the next hours (or, "4x2 days", the next days) under a line; 4x3: both rows.
			float lw = 359.1f + pen.ex;
			// v5.04 (Viorel): the rows in glass cards (as on the app's main screen) instead of a line.
			if (kind == 5) {
				pen.at(0, 1 / 3f);
				pen.card(6f, 113f, lw + 10.8f, 75f, 12f);
				hoursRow(pen, d, 111.3f, lw);
				pen.at(0, 1);
				pen.card(6f, 198.2f, lw + 10.8f, 75f, 12f);
				daysRow(pen, d, 196.5f, lw);
			} else {
				pen.at(0, 1);
				pen.card(6f, 113f, lw + 10.8f, 75f, 12f);
				if (kind == 4) daysRow(pen, d, 111.3f, lw);
				else hoursRow(pen, d, 111.3f, lw);
			}
		}
		pen.done();
		return out;
	}

	/**
	 * v4.10 (Viorel), the list widget, after the phone's weather widget: on the left the icon, a big
	 * temperature, the weather, high / low and the town with the compass's town mark; on the right
	 * four rows of the next days (day, one icon, high and low) or hours (hour, icon, degrees).
	 */
	private static void listLayout(Pen pen, Data d, Set st, String icon, String temp, String desc, String town) {
		Weather w = d.w;
		// v4.11 (Viorel): the left part split in two, the temperature on the left, a big icon on its right.
		pen.at(0, 0.5f);
		pen.temp(temp, 12f, 112f, 86f, 44f, 87f, 96, false);
		pen.icon(icon, 102f, 40f, 74f);
		pen.at(0, 1);
		pen.text(desc, 15f, 145f, 13.5f, -1, 1f, 3, 150);
		if (w != null && !Double.isNaN(w.tmax) && !Double.isNaN(w.tmin))
			pen.text("\u2191" + deg(w.tmax, d.unitF) + "° / \u2193" + deg(w.tmin, d.unitF) + "°", 15f, 162.5f, 12.5f, -1, 0.95f, 3, 150);
		pen.home(town, 15f, 182f, 12.5f, 140);
		// v4.50: the code lines (or the information) between the temperature and the weather.
		pen.at(0, 0.75f);
		pen.warnings(d, 15f, 121f, false);
		if (w == null) return;
		boolean hours = st.mode.equals("hours");
		float x0 = 186f, x1 = 372f, rh = 46f, y0 = 4f;
		// v5.04 (Viorel): the rows in one glass card (its height follows the rows' spreading).
		pen.at(1, 0);
		pen.card(x0 - 8f, y0, x1 - x0 + 14f, 4 * rh + pen.ey, 12f);
		for (int i = 0; i < 4; i++) {
			String label, ic;
			String hi = null, lo = null;
			if (hours) {
				int h = i + 1;
				if (h >= w.tileHours.size()) break;
				label = Texts.clock(w.tileHours.get(h), d.clk12);
				ic = d.T.describe(w.tileCode[h], w.tileDay[h])[0];
				if (h < w.tileTemp.length && !Double.isNaN(w.tileTemp[h])) hi = deg(w.tileTemp[h], d.unitF) + "°";
			} else {
				if (i >= w.dayDates.size() || i >= w.dayCodes.size()) break;
				label = i == 0 ? cap(d.T.day1) : cap(d.T.weekdayUp(w.dayDates.get(i), false));
				ic = d.T.describe(w.dayCodes.get(i), true)[0];
				if (i < w.dayHi.size() && i < w.dayLo.size()) {
					hi = deg(w.dayHi.get(i), d.unitF) + "°";
					lo = deg(w.dayLo.get(i), d.unitF) + "°";
				}
			}
			float y = y0 + i * rh, mid = y + rh / 2;
			pen.at(1, i / 3f);
			pen.text(label, x0, mid + 5f, 14f, -1, 0.75f, 3, 62);
			pen.icon(ic, (x0 + x1) / 2f - 4f - 14f, mid - 14f, 28f);
			if (lo != null) {
				float lw = pen.text(lo, x1, mid + 5.5f, 15f, 1, 0.7f, 3, 0);
				pen.text(hi, x1 - lw - 7f, mid + 5.5f, 15f, 1, 1f, 3, 0);
			} else if (hi != null) pen.text(hi, x1, mid + 5.5f, 15f, 1, 1f, 3, 0);
			if (i < 3) {
				pen.at(1, (i + 0.5f) / 3f);
				pen.line(x0, y + rh, x1 - x0, 0.75f, 0.35f);
			}
		}
	}

	/** "MÂINE" -> "Mâine" (the list widget's quieter labels). */
	private static String cap(String s) {
		if (s == null || s.length() < 2) return s;
		return s.substring(0, 1) + s.substring(1).toLowerCase();
	}

	/** The next 5 hours under the line at Y: hour, icon, temperature. */
	private static void hoursRow(Pen pen, Data d, float y, float lw) {
		Weather w = d.w;
		if (w == null) return;
		float cw = lw / 5;
		for (int i = 0; i < 5 && i + 1 < w.tileHours.size(); i++) {
			int h = i + 1;
			float cx = 11.4f + cw * i + cw / 2;
			pen.text(Texts.clock(w.tileHours.get(h), d.clk12), cx, y + 18.7f, 14.2f, 0, 0.9f, 3, cw - 4);
			pen.icon(d.T.describe(w.tileCode[h], w.tileDay[h])[0], cx - 13.15f, y + 24.5f, 26.3f);
			if (h < w.tileTemp.length && !Double.isNaN(w.tileTemp[h]))
				pen.text(deg(w.tileTemp[h], d.unitF) + "°", cx, y + 67.4f, 15, 0, 1f, 3, cw - 4);
		}
	}

	/** The next 5 days under the line at Y: tomorrow / weekday, icon, high / low. */
	private static void daysRow(Pen pen, Data d, float y, float lw) {
		Weather w = d.w;
		if (w == null) return;
		float cw = lw / 5;
		for (int k = 0; k < 5 && k < w.dayDates.size() && k < w.dayCodes.size(); k++) {
			float cx = 11.4f + cw * k + cw / 2;
			String day = k == 0 ? d.T.day1 : d.T.weekdayUp(w.dayDates.get(k), true);
			pen.text(day, cx, y + 18.7f, 12.6f, 0, 0.9f, 3, cw - 4);
			pen.icon(d.T.describe(w.dayCodes.get(k), true)[0], cx - 13.15f, y + 24.5f, 26.3f);
			if (k < w.dayHi.size() && k < w.dayLo.size())
				pen.text(deg(w.dayHi.get(k), d.unitF) + "° / " + deg(w.dayLo.get(k), d.unitF) + "°", cx, y + 67.4f, 14, 0, 1f, 3, cw - 4);
		}
	}

	private static long deg(double c, boolean unitF) {
		return Math.round(unitF ? c * 9 / 5 + 32 : c);
	}

	/**
	 * Draws on the widget picture in the mockup's dp: the text white with a soft shadow (or black),
	 * the app's icons in the same colour. EX / EY: the room left over in the frame (dp of the
	 * layout); at(ax, ay) moves what follows by that part of it (0 = left / top, 1 = right / bottom).
	 */
	private static final class Pen {
		final Context ctx;
		final Canvas cv;
		final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
		final boolean black;
		final float ex, ey;
		private boolean saved;

		Pen(Context ctx, Canvas cv, boolean black, float ex, float ey) {
			this.ctx = ctx;
			this.cv = cv;
			this.black = black;
			this.ex = Math.max(0, ex);
			this.ey = Math.max(0, ey);
			p.setTypeface(android.graphics.Typeface.DEFAULT);
		}

		void at(float ax, float ay) {
			if (saved) cv.restore();
			cv.save();
			saved = true;
			cv.translate(ax * ex, ay * ey);
		}

		void done() {
			if (saved) cv.restore();
			saved = false;
		}

		/** The text colour at OPACITY (0–1). */
		int ink(float opacity) {
			return ((int) (opacity * 255) << 24) | (black ? 0x000000 : 0xFFFFFF);
		}

		/** S with its baseline at BASE: ALIGN -1 left edge at X, 0 centred on X, 1 right edge at X; shrunk to MAXW. */
		float text(String s, float x, float base, float size, int align, float opacity, float shadow, float maxw) {
			if (s == null || s.length() == 0) return 0;
			p.setTextSize(size);
			float w = p.measureText(s);
			if (maxw > 0 && w > maxw) {
				p.setTextSize(size * maxw / w);
				w = maxw;
			}
			p.setColor(ink(opacity));
			if (black) p.clearShadowLayer();
			else p.setShadowLayer(shadow, 0, 1, 0x99000000);
			float left = align < 0 ? x : align == 0 ? x - w / 2 : x - w;
			cv.drawText(s, left, base, p);
			return w;
		}

		/** The town mark at the right (v4.12, Viorel: the compass's mark instead of the pin), the town's name before it, its right edge at RIGHT. */
		void town(String name, float right, float base, float size, float maxw) {
			float rr = 13.2f * 0.5f, cx = right - rr, cy = base - 10.9f + 6.6f;
			Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
			if (!black) hp.setShadowLayer(1.5f, 0, 0.6f, 0x66000000);
			CompassPanel.houseMark(cv, hp, cx, cy, rr, CompassPanel.TOWN_COL[0], 0xFFFFFFFF);
			text(name, right - rr * 2 - 3f, base, size, 1, 0.9f, 3, maxw);
		}

		/** v4.10 (Viorel): the town after the compass's town mark (a coloured disc with a small house), from LEFT. */
		void home(String name, float left, float base, float size, float maxw) {
			if (name == null || name.length() == 0) return;
			float rr = size * 0.55f, cx = left + rr, cy = base - size * 0.36f;
			Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
			if (!black) hp.setShadowLayer(1.5f, 0, 0.6f, 0x66000000);
			CompassPanel.houseMark(cv, hp, cx, cy, rr, CompassPanel.TOWN_COL[0], 0xFFFFFFFF);
			text(name, left + rr * 2 + 4f, base, size, -1, 0.95f, 3, maxw - rr * 2 - 4f);
		}

		/** The moment of the icons' movement (0–1). */
		float t = 1;

		void icon(String name, float x, float y, float size) {
			if (name == null) return;
			// v4.00 (Viorel): the coloured icons of the app, in the theme's colours, moving.
			if (ro.meteoalarm.android.core.WxIcon.has(name)) {
				AndroidGfx g = gfx(ctx);
				g.c = cv;
				ro.meteoalarm.android.core.WxIcon.draw(g, name, x, y, size, t, ro.meteoalarm.android.core.Theme.icon(!black));
				return;
			}
			Bitmap b = Widget.icon(ctx, "icons/" + name + ".png", 200, black);
			// The picture keeps 1/16 of its side free on each edge for the shadow: the drawing fills X Y SIZE.
			float e = size / 14;
			if (b != null) cv.drawBitmap(b, null, new RectF(x - e, y - e, x + size + e, y + size + e), new Paint(Paint.FILTER_BITMAP_FLAG));
		}

		/** The temperature and a smaller "°": RIGHT_ALIGNED with the "°" ending at X, else the digits start at X; at most MAXW wide. */
		void temp(String t, float x, float base, float size, float dsize, float dbase, float maxw, boolean rightAligned) {
			p.setTextSize(size);
			float tw = p.measureText(t);
			p.setTextSize(dsize);
			float dw = p.measureText("°");
			float f = Math.min(1, maxw / (tw + 1.5f + dw * 0.6f));
			float sz = size * f, ds = dsize * f, db = base - (base - dbase) * f;
			tw *= f;
			dw *= f;
			if (rightAligned) {
				text("°", x, db, ds, 1, 1f, 4, 0);
				text(t, x - dw - 1.3f * f, base, sz, 1, 1f, 4, 0);
			} else {
				text(t, x, base, sz, -1, 1f, 4, 0);
				text("°", x + tw + 1.5f * f, db, ds, -1, 1f, 4, 0);
			}
		}

		/**
		 * The three code lines from PX at PY (v4.50: no "AVERTIZARE!" text any more; RIGHT = they end
		 * where the three would end). Without codes, the app's information: one line of its own
		 * colour with its name next to it. Nothing without either.
		 */
		void warnings(Data d, float px, float py, boolean right) {
			Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
			if (d.levels.length == 0) {
				if (d.event == null) return;
				float pw = 30.9f, end = px + 2 * 38.4f + pw, x = right ? end - pw : px;
				pill(f, x, py, INFO);
				if (right) text(d.event, x - 5f, py + 6.6f, 10.5f, 1, 1f, 3, 120);
				else text(d.event, x + pw + 5f, py + 6.6f, 10.5f, -1, 1f, 3, 120);
				return;
			}
			int[] cols = pills(d.levels);
			for (int i = 0; i < 3; i++) pill(f, px + i * 38.4f, py, cols[i]);
		}

		/** v4.51 (Viorel): the 2x2's code lines at the bottom, centred across the widget's width W. */
		void centred(Data d, float w, float py) {
			if (d.levels.length > 0) { warnings(d, (w - (2 * 38.4f + 30.9f)) / 2, py, false); return; }
			if (d.event == null) return;
			p.setTextSize(10.5f);
			float tw = Math.min(120, p.measureText(d.event)), x = (w - (30.9f + 5f + tw)) / 2;
			pill(new Paint(Paint.ANTI_ALIAS_FLAG), x, py, INFO);
			text(d.event, x + 30.9f + 5f, py + 6.6f, 10.5f, -1, 1f, 3, 120);
		}

		private void pill(Paint f, float x, float py, int col) {
			RectF r = new RectF(x, py, x + 30.9f, py + 6.4f);
			f.setStyle(Paint.Style.FILL);
			f.setColor(col);
			if (!black) f.setShadowLayer(2, 0, 1, 0x66000000);
			cv.drawRoundRect(r, 3.2f, 3.2f, f);
			f.clearShadowLayer();
			f.setStyle(Paint.Style.STROKE);
			f.setStrokeWidth(0.7f);
			f.setColor(ink(1f));
			cv.drawRoundRect(r, 3.2f, 3.2f, f);
		}

		/** v5.04: a glass card: the text colour faintly, with a thin edge. */
		void card(float x, float y, float w, float h, float r) {
			Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
			RectF rc = new RectF(x, y, x + w, y + h);
			f.setColor(ink(black ? 0.06f : 0.12f));
			cv.drawRoundRect(rc, r, r, f);
			f.setStyle(Paint.Style.STROKE);
			f.setStrokeWidth(0.6f);
			f.setColor(ink(black ? 0.12f : 0.2f));
			cv.drawRoundRect(rc, r, r, f);
		}

		void line(float x, float y, float w, float h, float opacity) {
			Paint f = new Paint();
			f.setColor(ink(opacity));
			cv.drawRect(x, y, x + w, y + h, f);
		}
	}

	/** "Sunset at 17:02" until sunset, then "Sunrise at 06:45" (tomorrow's after sunset). */
	private static String sunLine(Weather w, boolean en) {
		Calendar now = Calendar.getInstance();
		String hm = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
		String rise = Tr.t("Sunrise at ", "Răsărit la "), set = Tr.t("Sunset at ", "Apus la ");
		if (w.sunrise.length() > 0 && hm.compareTo(w.sunrise) < 0) return rise + w.sunrise;
		if (w.sunset.length() > 0 && hm.compareTo(w.sunset) < 0) return set + w.sunset;
		return w.sunrise2.length() > 0 ? rise + w.sunrise2 : "";
	}

	private static AndroidGfx GFX;

	private static AndroidGfx gfx(Context ctx) {
		if (GFX == null) GFX = new AndroidGfx(ctx.getApplicationContext().getAssets());
		return GFX;
	}

	private static final HashMap<String, Bitmap> ICONS = new HashMap<String, Bitmap>();

	/** An icon of the app (black on transparent) drawn white with a soft shadow (or black), SIZE pixels square. */
	private static Bitmap icon(Context ctx, String asset, int size, boolean black) {
		String key = asset + size + black;
		if (ICONS.containsKey(key)) return ICONS.get(key);
		try {
			InputStream in = ctx.getAssets().open(asset);
			Bitmap src = BitmapFactory.decodeStream(in);
			in.close();
			int w = src.getWidth(), h = src.getHeight();
			int[] px = new int[w * h];
			src.getPixels(px, 0, w, 0, 0, w, h);
			for (int k = 0; k < px.length; k++) {
				int q = px[k];
				int lum = (((q >> 16) & 255) * 299 + ((q >> 8) & 255) * 587 + (q & 255) * 114) / 1000;
				px[k] = ((255 - lum) * (q >>> 24) / 255) << 24;
			}
			Bitmap m = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
			m.setPixels(px, 0, w, 0, 0, w, h);
			Bitmap a = m.extractAlpha();
			int pad = Math.max(2, size / 16);
			Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
			Canvas cv = new Canvas(out);
			Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
			pt.setColor(black ? 0xFF000000 : 0xFFFFFFFF);
			if (!black) pt.setShadowLayer(pad, 0, pad / 2f, 0x80000000);
			cv.drawBitmap(a, new Rect(0, 0, w, h), new RectF(pad, pad, size - pad, size - pad), pt);
			ICONS.put(key, out);
			return out;
		} catch (Exception e) {
			return null;
		}
	}

	static String read(File f) throws Exception {
		FileInputStream in = new FileInputStream(f);
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] buf = new byte[16384];
		int n;
		while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
		in.close();
		return new String(out.toByteArray(), "UTF-8");
	}
}
