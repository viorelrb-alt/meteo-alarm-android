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

	static final Class<?>[] KINDS = { Widget.class, W2x2.class, W4x1.class, W4x2.class };

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
			for (String k : new String[] { "town", "bg", "alpha", "colors", "font", "seen" }) e.remove("w" + id + "_" + k);
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
			return s;
		}

		void save(Context ctx, int id) {
			String k = "w" + id + "_";
			prefs(ctx).edit().putString(k + "town", town).putBoolean(k + "bg", bg).putInt(k + "alpha", alpha)
					.putString(k + "colors", colors).putString(k + "font", font).apply();
		}

		String describe() {
			return "oraș=" + (town.length() == 0 ? "aplicație" : town) + " fundal=" + (bg ? alpha + "%" : "nu") + " culori=" + colors + " font=" + font;
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
	}

	/** The data for TOWN (a key; empty = the app's town). */
	static Data data(Context ctx, String town) {
		Data d = new Data();
		SharedPreferences p = prefs(ctx);
		d.en = "en".equals(p.getString("lang", "ro"));
		d.unitF = p.getBoolean("unitF", false);
		d.clk12 = p.getBoolean("clk12", false);
		d.T = new Texts(d.en);
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
		// The warnings the app saved (the country's feed): which codes (1 yellow, 2 orange, 3 red) are active here.
		try {
			if (d.c != null && cached != null && cached.country.equals(d.c.country)) {
				List<Warnings.Warn> ws = Warnings.parse(read(new File(ctx.getFilesDir(), "warn.xml")), d.c.warnNames(), System.currentTimeMillis());
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
		return d;
	}

	static final int RED = 0xFFFD0000, ORANGE = 0xFFFD8B00, YELLOW = 0xFFDFD51B, GRAY = 0xFFA9A9A9;

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
	static final float[] RW = { 192, 384, 384, 192 }, RH = { 192, 96, 192, 96 };
	/** Below this zoom the texts get too small: such a layout is not used (logs of 2026-11-02: 68% was too small). */
	static final float MIN_ZOOM = 0.8f;

	/**
	 * The layout for a frame W x H dp: 0 = 2x2, 1 = 4x1, 2 = 4x2, 3 = 2x1. The one whose shape is
	 * closest to the frame's (width / height: 2x2 = 1, 4x2 = 2, 4x1 = 4), among those that are not
	 * shrunk below MIN_ZOOM; the small 2x1 only when none of the others fits. Logs of 2026-11-02:
	 * 357x244 and 365x252 are 4x2 shapes (not 2x2 zoomed); 261x108 was 4x1 at 68%, now 2x1.
	 */
	static int kind(float w, float h) {
		h = Math.max(1f, h);
		w = Math.max(1f, w);
		int best = -1;
		double near = Double.MAX_VALUE;
		for (int k = 0; k < 3; k++) {
			if (zoom(k, w, h) < MIN_ZOOM) continue;
			double d = Math.abs(Math.log((w / h) / (RW[k] / RH[k])));
			// On a tie (equal shape), the larger layout (more to see): 4x2 before 2x2 before 4x1.
			if (d < near - 1e-3) {
				near = d;
				best = k;
			}
		}
		if (best >= 0) return best;
		if (zoom(3, w, h) >= MIN_ZOOM) return 3;
		// Smaller still (should not happen: minResizeWidth / Height): the one shown largest.
		best = 0;
		for (int k = 1; k < 4; k++) if (zoom(k, w, h) > zoom(best, w, h)) best = k;
		return best;
	}

	static float zoom(int k, float w, float h) {
		return Math.min(w / RW[k], h / RH[k]);
	}

	static final String[] NAMES = { "2x2", "4x1", "4x2", "2x1" };

	/** One widget, drawn as a picture of its frame. */
	private static void draw(Context ctx, AppWidgetManager mgr, int id, Data d, Set st) {
		float dens = ctx.getResources().getDisplayMetrics().density;
		StringBuilder why = new StringBuilder();
		int[] f = frame(ctx, mgr, id, why);
		int kind = kind(f[0], f[1]);
		float s = Math.min(f[0] / RW[kind], f[1] / RH[kind]);
		Log.i(ctx, "WIDGET", "#" + id + " " + why + " -> cadru " + f[0] + "x" + f[1] + " dp, model " + NAMES[kind]
				+ " zoom " + Math.round(s * 100) + "% · " + (d.c != null ? d.c.name : "-") + (d.w == null ? " fără date" : "") + " · " + st.describe());
		try {
			send(ctx, mgr, id, d, render(ctx, d, st, f[0], f[1], dens, kind));
		} catch (Throwable e) {
			// Too large a picture for the home screen: half the sharpness, still the same widget.
			Log.e(ctx, "WIDGET", "#" + id + " imagine prea mare, desenez la jumătate", e);
			try {
				send(ctx, mgr, id, d, render(ctx, d, st, f[0], f[1], dens / 2, kind));
			} catch (Throwable e2) {
				Log.e(ctx, "WIDGET", "#" + id + " nu s-a putut desena", e2);
			}
		}
	}

	private static void send(Context ctx, AppWidgetManager mgr, int id, Data d, Bitmap bm) {
		RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
		v.setImageViewBitmap(R.id.widget_image, bm);
		v.setContentDescription(R.id.widget_image, d.w != null ? d.T.describe(d.w.code, d.w.isDay)[1] : "Meteo Alarm");
		Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
			bg.setShader(new LinearGradient(0, 0, 0, H, (a << 24) | (b[0] & 0xFFFFFF), (a << 24) | (b[1] & 0xFFFFFF), Shader.TileMode.CLAMP));
			float r = Math.min(24 * dens, Math.min(W, H) / 4f);
			cv.drawRoundRect(new RectF(0, 0, W, H), r, r, bg);
		}
		float k = s * dens;
		cv.scale(k, k);
		Pen pen = new Pen(ctx, cv, blackText(ctx, st, w), W / k - rw, H / k - rh);
		String temp = w == null ? "--" : Long.toString(Math.round(d.unitF ? w.temp * 9 / 5 + 32 : w.temp));
		String icon = w == null ? null : d.T.describe(w.code, w.isDay)[0];
		String town = d.c != null ? d.c.name : "";
		String sun = w == null ? (d.en ? "Open the app" : "Deschide aplicația") : sunLine(w, d.en);
		String desc = w == null ? "" : d.T.describe(w.code, w.isDay)[1];
		if (kind == 0) {
			pen.at(0, 0);
			pen.icon(icon, 11.1f, 28.8f, 83.3f);
			pen.at(1, 0);
			pen.town(town, 183.5f, 28.0f, 12.4f, 120);
			pen.text(sun, 183.5f, 40.4f, 8.9f, 1, 1f, 3, 115);
			pen.at(1, 1);
			pen.temp(temp, 184.5f, 160.5f, 87.7f, 49.5f, 132.6f, 95, true);
			pen.at(0, 1);
			pen.warnings(d.levels, 12.9f, 177.7f, 75.4f + pen.ex, 171.4f, d.en);
		} else if (kind == 3) {
			// The small one: the left part of the 4x1, the temperature pushed to the right edge.
			pen.at(0, 0.5f);
			pen.icon(icon, 8f, 12f, 72f);
			pen.at(1, 0.5f);
			pen.temp(temp, 184f, 75.2f, 77.4f, 59.3f, 63.1f, 100, true);
		} else if (kind == 1) {
			pen.at(0, 0.5f);
			pen.icon(icon, 12.9f, 17.3f, 72f);
			pen.temp(temp, 79.9f, 75.2f, 77.4f, 59.3f, 63.1f, 110, false);
			pen.at(1, 0.5f);
			pen.town(town, 369.5f, 23.0f, 13, 170);
			pen.text(desc, 370.1f, 40.6f, 13, 1, 1f, 3, 170);
			pen.text(sun, 370.1f, 57.1f, 13, 1, 1f, 3, 170);
			pen.warnings(d.levels, 197.8f, 76.0f, 260.3f, 69.7f, d.en);
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
			pen.warnings(d.levels, 198.2f, 107.6f, 260.7f, 101.4f, d.en);
			pen.at(0, 0.5f);
			float lw = 359.1f + pen.ex;
			pen.line(11.4f, 111.3f, lw, 0.75f, 0.45f);
			pen.at(0, 1);
			if (w != null) {
				float cw = lw / 5;
				for (int i = 0; i < 5 && i + 1 < w.tileHours.size(); i++) {
					int h = i + 1;
					float cx = 11.4f + cw * i + cw / 2;
					pen.text(Texts.clock(w.tileHours.get(h), d.clk12), cx, 130f, 14.2f, 0, 0.9f, 3, cw - 4);
					pen.icon(d.T.describe(w.tileCode[h], w.tileDay[h])[0], cx - 13.15f, 135.8f, 26.3f);
					if (h < w.tileTemp.length && !Double.isNaN(w.tileTemp[h]))
						pen.text(deg(w.tileTemp[h], d.unitF) + "°", cx, 178.7f, 15, 0, 1f, 3, cw - 4);
				}
			}
		}
		pen.done();
		return out;
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

		/** Pin at the right, the town's name before it, its right edge at RIGHT. */
		void town(String name, float right, float base, float size, float maxw) {
			float ps = 13.2f;
			Bitmap pin = Widget.icon(ctx, "icons/btn-city.png", 64, black);
			float e = ps / 14;
			if (pin != null) cv.drawBitmap(pin, null, new RectF(right - ps - e, base - 10.9f - e, right + e, base - 10.9f + ps + e), new Paint(Paint.FILTER_BITMAP_FLAG));
			text(name, right - ps - 1.5f, base, size, 1, 0.9f, 3, maxw);
		}

		void icon(String name, float x, float y, float size) {
			if (name == null) return;
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

		/** "AVERTIZARE!" at LX (baseline LB) and the three pills from PX at PY; nothing without warnings. */
		void warnings(int[] levels, float lx, float lb, float px, float py, boolean en) {
			if (levels.length == 0) return;
			text(en ? "WARNING!" : "AVERTIZARE!", lx, lb, 8.9f, -1, 0.9f, 3, px - lx - 4);
			int[] cols = pills(levels);
			Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
			for (int i = 0; i < 3; i++) {
				RectF r = new RectF(px + i * 38.4f, py, px + i * 38.4f + 30.9f, py + 6.4f);
				f.setStyle(Paint.Style.FILL);
				f.setColor(cols[i]);
				if (!black) f.setShadowLayer(2, 0, 1, 0x66000000);
				cv.drawRoundRect(r, 3.2f, 3.2f, f);
				f.clearShadowLayer();
				f.setStyle(Paint.Style.STROKE);
				f.setStrokeWidth(0.7f);
				f.setColor(ink(1f));
				cv.drawRoundRect(r, 3.2f, 3.2f, f);
			}
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
		String rise = en ? "Sunrise at " : "Răsărit la ", set = en ? "Sunset at " : "Apus la ";
		if (w.sunrise.length() > 0 && hm.compareTo(w.sunrise) < 0) return rise + w.sunrise;
		if (w.sunset.length() > 0 && hm.compareTo(w.sunset) < 0) return set + w.sunset;
		return w.sunrise2.length() > 0 ? rise + w.sunrise2 : "";
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
