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
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.RemoteViews;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Texts;
import ro.meteoalarm.android.core.Warnings;
import ro.meteoalarm.android.core.Weather;

/**
 * Home-screen widget, in the style of the phone's weather widget: on the wallpaper, a small weather
 * icon, a big temperature, the next sunset (or sunrise) and the town. It shows the weather the app
 * saved (brought in the background by Refresher). Four entries in the phone's widget list: 2x2,
 * 4x1, 4x2, and one that picks its layout by its size. A tap opens the app.
 */
public class Widget extends AppWidgetProvider {
	/** The widget list's entries with one layout each (Viorel 2026-11-02), like the phone's own weather widgets. */
	public static final class W2x2 extends Widget {
		@Override
		int fixed() {
			return 0;
		}
	}

	public static final class W4x1 extends Widget {
		@Override
		int fixed() {
			return 1;
		}
	}

	public static final class W4x2 extends Widget {
		@Override
		int fixed() {
			return 2;
		}
	}

	/** This entry's layout: 0 = 2x2, 1 = 4x1, 2 = 4x2; -1 = chosen by the widget's size (the first widget). */
	int fixed() {
		return -1;
	}

	private static final Class<?>[] KINDS = { Widget.class, W2x2.class, W4x1.class, W4x2.class };

	@Override
	public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
		show(ctx);
		// The weather comes with the refresh in the background, if it is due.
		ctx.sendBroadcast(new Intent(ctx, Refresher.class));
	}

	/** Draws every widget again from the weather on the phone (the app calls it after a fetch). */
	static void show(Context ctx) {
		AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
		Data dt = null;
		for (int k = 0; k < KINDS.length; k++) {
			int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, KINDS[k]));
			if (ids == null || ids.length == 0) continue;
			if (dt == null) dt = data(ctx);
			for (int id : ids) draw(ctx, mgr, id, dt, k - 1);
		}
	}

	@Override
	public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, android.os.Bundle opts) {
		draw(ctx, mgr, id, data(ctx), fixed());
	}

	/** What the widget shows, read once for all widgets. */
	static final class Data {
		Weather w;
		Texts T;
		boolean en, unitF, clk12;
		Cities.City c;
		int[] levels = new int[0];
		/** How much the background shows, 0 (none, on the wallpaper) to 100 (solid). */
		int alpha;
	}

	static Data data(Context ctx) {
		Data d = new Data();
		SharedPreferences p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
		d.en = "en".equals(p.getString("lang", "ro"));
		d.unitF = p.getBoolean("unitF", false);
		d.clk12 = p.getBoolean("clk12", false);
		d.T = new Texts(d.en);
		d.c = Cities.City.fromKey(p.getString("city", ""));
		// The town the app last downloaded the weather for, when it is the same place (or no town
		// was ever picked: the app then shows its default town, which is not saved).
		Cities.City cached = Cities.City.fromKey(p.getString("cacheCity", ""));
		if (cached != null && (d.c == null || cached.same(d.c))) d.c = cached;
		String city = d.c != null ? d.c.key() : "";
		d.alpha = Math.max(0, Math.min(100, p.getInt("widgetBg", 0)));
		try {
			if (city.equals(p.getString("cacheCity", ""))) d.w = Weather.parse(read(new File(ctx.getFilesDir(), "forecast.json")));
		} catch (Exception e) {
			d.w = null;
		}
		// The warnings the app saved for this town: which codes (1 yellow, 2 orange, 3 red) are active.
		try {
			if (d.c != null && city.equals(p.getString("cacheCity", ""))) {
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

	/** One widget, drawn as a picture of its size: 2x2 (square), 4x1 (low and wide) or 4x2 (wide). */
	private static void draw(Context ctx, AppWidgetManager mgr, int id, Data d, int fixed) {
		float dens = ctx.getResources().getDisplayMetrics().density;
		android.os.Bundle o = mgr.getAppWidgetOptions(id);
		boolean land = ctx.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
		int wdp = o == null ? 0 : o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
		int hdp = o == null ? 0 : o.getInt(land ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
		int minH = o == null ? 0 : Math.min(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT));
		if (wdp <= 0) wdp = 180;
		if (hdp <= 0) hdp = 180;
		int kind = fixed >= 0 ? fixed : kind(wdp, minH > 0 ? minH : hdp);
		// A fixed layout keeps its own shape inside whatever frame the launcher gives it.
		if (fixed >= 0) {
			float rw = kind == 0 ? 192 : 384, rh = kind == 1 ? 96 : 192, f = Math.min(wdp / rw, hdp / rh);
			if (f > 0) {
				wdp = Math.max(40, Math.round(rw * f));
				hdp = Math.max(20, Math.round(rh * f));
			}
		}
		try {
			send(ctx, mgr, id, d, render(ctx, d, wdp, hdp, dens, kind));
		} catch (Throwable e) {
			// Too large a picture for the home screen: half the sharpness, still the same widget.
			send(ctx, mgr, id, d, render(ctx, d, wdp, hdp, dens / 2, kind));
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

	/**
	 * The layout for the first widget (any size), WDP wide and at least HDP high: by the width, as
	 * the columns (Samsung A70, Android 11: a 4x3 widget is nearly square, and the heights it
	 * reports are not the frame's): the whole width of the screen is 4x1 when one row high, else
	 * 4x2; less is 2x2.
	 */
	static int kind(int wdp, int hdp) {
		if (wdp >= 230) return hdp < 100 ? 1 : 2;
		return 0;
	}

	/** Draws the widget WDP x HDP dp at DENS px per dp; the layouts are Viorel's mockups (192x192, 384x96, 384x192 dp). */
	static Bitmap render(Context ctx, Data d, int wdp, int hdp, float dens, int kind) {
		float rw = kind == 0 ? 192 : 384, rh = kind == 1 ? 96 : 192;
		float s = Math.min(wdp / rw, hdp / rh);
		int W = Math.max(1, Math.round(wdp * dens)), H = Math.max(1, Math.round(hdp * dens));
		Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
		Canvas cv = new Canvas(out);
		// The background, as see-through as set in Widgets (none by default: on the wallpaper).
		if (d.alpha > 0) {
			Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
			bg.setColor(((d.alpha * 255 / 100) << 24) | 0x1C1C1E);
			float r = Math.min(22 * dens, Math.min(W, H) / 4f);
			cv.drawRoundRect(new RectF(0, 0, W, H), r, r, bg);
		}
		float k = s * dens;
		cv.translate((W - rw * k) / 2, (H - rh * k) / 2);
		cv.scale(k, k);
		Pen pen = new Pen(ctx, cv);
		Weather w = d.w;
		String temp = w == null ? "--" : Long.toString(Math.round(d.unitF ? w.temp * 9 / 5 + 32 : w.temp));
		String icon = w == null ? null : d.T.describe(w.code, w.isDay)[0];
		String town = d.c != null ? d.c.name : "";
		String sun = w == null ? (d.en ? "Open the app" : "Deschide aplicația") : sunLine(w, d.en);
		String desc = w == null ? "" : d.T.describe(w.code, w.isDay)[1];
		if (kind == 0) {
			pen.icon(icon, 11.1f, 28.8f, 83.3f);
			pen.town(town, 183.5f, 28.0f, 12.4f, 120);
			pen.text(sun, 183.5f, 40.4f, 8.9f, 1, 0xFFFFFFFF, 3, 115);
			pen.temp(temp, 184.5f, 160.5f, 87.7f, 49.5f, 132.6f, 95, true);
			pen.warnings(d.levels, 12.9f, 177.7f, 75.4f, 171.4f, d.en);
		} else if (kind == 1) {
			pen.icon(icon, 12.9f, 17.3f, 72f);
			pen.temp(temp, 79.9f, 75.2f, 77.4f, 59.3f, 63.1f, 110, false);
			pen.town(town, 369.5f, 23.0f, 13, 170);
			pen.text(desc, 370.1f, 40.6f, 13, 1, 0xFFFFFFFF, 3, 170);
			pen.text(sun, 370.1f, 57.1f, 13, 1, 0xFFFFFFFF, 3, 170);
			pen.warnings(d.levels, 197.8f, 76.0f, 260.3f, 69.7f, d.en);
		} else {
			pen.icon(icon, 10.3f, 17.1f, 84.4f);
			pen.temp(temp, 89f, 95.4f, 102f, 53.6f, 67.2f, 125, false);
			pen.town(town, 369.5f, 27.5f, 13, 150);
			pen.text(desc, 370.1f, 45.5f, 13, 1, 0xFFFFFFFF, 3, 150);
			if (w != null && !Double.isNaN(w.tmax) && !Double.isNaN(w.tmin))
				pen.text(d.T.hi + " " + deg(w.tmax, d.unitF) + "° · " + d.T.lo + " " + deg(w.tmin, d.unitF) + "°", 369.1f, 62.3f, 13, 1, 0xFFFFFFFF, 3, 150);
			pen.text(sun, 369.4f, 80.2f, 13, 1, 0xFFFFFFFF, 3, 150);
			pen.warnings(d.levels, 198.2f, 107.6f, 260.7f, 101.4f, d.en);
			pen.line(11.4f, 111.3f, 359.1f, 0.75f, 0x73FFFFFF);
			if (w != null) {
				float cw = 359.1f / 5;
				for (int i = 0; i < 5 && i + 1 < w.tileHours.size(); i++) {
					int h = i + 1;
					float cx = 11.4f + cw * i + cw / 2;
					pen.text(Texts.clock(w.tileHours.get(h), d.clk12), cx, 130f, 14.2f, 0, 0xE6FFFFFF, 3, cw - 4);
					pen.icon(d.T.describe(w.tileCode[h], w.tileDay[h])[0], cx - 13.15f, 135.8f, 26.3f);
					if (h < w.tileTemp.length && !Double.isNaN(w.tileTemp[h]))
						pen.text(deg(w.tileTemp[h], d.unitF) + "°", cx, 178.7f, 15, 0, 0xFFFFFFFF, 3, cw - 4);
				}
			}
		}
		return out;
	}

	private static long deg(double c, boolean unitF) {
		return Math.round(unitF ? c * 9 / 5 + 32 : c);
	}

	/** Draws on the widget picture in the mockup's dp: white text with a soft shadow, the app's icons in white. */
	private static final class Pen {
		final Context ctx;
		final Canvas cv;
		final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

		Pen(Context ctx, Canvas cv) {
			this.ctx = ctx;
			this.cv = cv;
			p.setTypeface(android.graphics.Typeface.DEFAULT);
		}

		/** S with its baseline at BASE: ALIGN -1 left edge at X, 0 centred on X, 1 right edge at X; shrunk to MAXW. */
		float text(String s, float x, float base, float size, int align, int color, float shadow, float maxw) {
			if (s == null || s.length() == 0) return 0;
			p.setTextSize(size);
			float w = p.measureText(s);
			if (maxw > 0 && w > maxw) {
				p.setTextSize(size * maxw / w);
				w = maxw;
			}
			p.setColor(color);
			p.setShadowLayer(shadow, 0, 1, 0x99000000);
			float left = align < 0 ? x : align == 0 ? x - w / 2 : x - w;
			cv.drawText(s, left, base, p);
			return w;
		}

		/** Pin at the right, the town's name before it, its right edge at RIGHT. */
		void town(String name, float right, float base, float size, float maxw) {
			float ps = 13.2f;
			Bitmap pin = Widget.icon(ctx, "icons/btn-city.png", 64);
			float e = ps / 14;
			if (pin != null) cv.drawBitmap(pin, null, new RectF(right - ps - e, base - 10.9f - e, right + e, base - 10.9f + ps + e), new Paint(Paint.FILTER_BITMAP_FLAG));
			text(name, right - ps - 1.5f, base, size, 1, 0xE6FFFFFF, 3, maxw);
		}

		void icon(String name, float x, float y, float size) {
			if (name == null) return;
			Bitmap b = Widget.icon(ctx, "icons/" + name + ".png", 200);
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
				text("°", x, db, ds, 1, 0xFFFFFFFF, 4, 0);
				text(t, x - dw - 1.3f * f, base, sz, 1, 0xFFFFFFFF, 4, 0);
			} else {
				text(t, x, base, sz, -1, 0xFFFFFFFF, 4, 0);
				text("°", x + tw + 1.5f * f, db, ds, -1, 0xFFFFFFFF, 4, 0);
			}
		}

		/** "AVERTIZARE!" at LX (baseline LB) and the three pills from PX at PY; nothing without warnings. */
		void warnings(int[] levels, float lx, float lb, float px, float py, boolean en) {
			if (levels.length == 0) return;
			text(en ? "WARNING!" : "AVERTIZARE!", lx, lb, 8.9f, -1, 0xE6FFFFFF, 3, px - lx - 4);
			int[] cols = pills(levels);
			Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
			for (int i = 0; i < 3; i++) {
				RectF r = new RectF(px + i * 38.4f, py, px + i * 38.4f + 30.9f, py + 6.4f);
				f.setStyle(Paint.Style.FILL);
				f.setColor(cols[i]);
				f.setShadowLayer(2, 0, 1, 0x66000000);
				cv.drawRoundRect(r, 3.2f, 3.2f, f);
				f.clearShadowLayer();
				f.setStyle(Paint.Style.STROKE);
				f.setStrokeWidth(0.7f);
				f.setColor(0xFFFFFFFF);
				cv.drawRoundRect(r, 3.2f, 3.2f, f);
			}
		}

		void line(float x, float y, float w, float h, int color) {
			Paint f = new Paint();
			f.setColor(color);
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

	private static final java.util.HashMap<String, Bitmap> ICONS = new java.util.HashMap<String, Bitmap>();

	/** An icon of the app (black on transparent) drawn white with a soft shadow, SIZE pixels square. */
	private static Bitmap icon(Context ctx, String asset, int size) {
		String key = asset + size;
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
			pt.setColor(0xFFFFFFFF);
			pt.setShadowLayer(pad, 0, pad / 2f, 0x80000000);
			cv.drawBitmap(a, new Rect(0, 0, w, h), new RectF(pad, pad, size - pad, size - pad), pt);
			ICONS.put(key, out);
			return out;
		} catch (Exception e) {
			return null;
		}
	}

	private static String read(File f) throws Exception {
		FileInputStream in = new FileInputStream(f);
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] buf = new byte[16384];
		int n;
		while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
		in.close();
		return new String(out.toByteArray(), "UTF-8");
	}
}
