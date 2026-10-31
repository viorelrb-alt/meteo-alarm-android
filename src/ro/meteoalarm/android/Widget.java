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
import java.util.TimeZone;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Texts;
import ro.meteoalarm.android.core.Weather;

/**
 * Home-screen widget, in the style of the phone's weather widget: on the wallpaper, a small weather
 * icon, a big temperature, the next sunset (or sunrise) and the town. It shows the weather the app
 * saved; when that is older than 30 minutes it fetches the forecast itself. A tap opens the app.
 */
public final class Widget extends AppWidgetProvider {
	private static final long FRESH_MS = 30 * 60000L;

	@Override
	public void onUpdate(final Context ctx, AppWidgetManager mgr, int[] ids) {
		show(ctx);
		final SharedPreferences prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
		if (System.currentTimeMillis() - newest(prefs) < FRESH_MS) return;
		final Cities.City c = Cities.City.fromKey(prefs.getString("city", ""));
		if (c == null) return;
		final PendingResult done = goAsync();
		new Thread(new Runnable() {
			public void run() {
				try {
					String f = Weather.get(Weather.forecastUrl(c.lat, c.lon, TimeZone.getDefault().getID()));
					Weather.parse(f);
					FileOutputStream out = new FileOutputStream(new File(ctx.getFilesDir(), "widget.json"));
					out.write(f.getBytes("UTF-8"));
					out.close();
					prefs.edit().putLong("widgetFetched", System.currentTimeMillis()).putString("widgetCity", c.key()).apply();
					show(ctx);
				} catch (Exception e) {
					// keep what is shown
				} finally {
					done.finish();
				}
			}
		}).start();
	}

	/** When the newest weather on the phone (the app's or the widget's own) was fetched. */
	private static long newest(SharedPreferences p) {
		String city = p.getString("city", "");
		long a = city.equals(p.getString("cacheCity", "")) ? p.getLong("fetched", 0) : 0;
		long b = city.equals(p.getString("widgetCity", "")) ? p.getLong("widgetFetched", 0) : 0;
		return Math.max(a, b);
	}

	/** Draws every widget again from the weather on the phone (the app calls it after a fetch). */
	static void show(Context ctx) {
		AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
		int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, Widget.class));
		if (ids == null || ids.length == 0) return;
		SharedPreferences p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
		boolean en = "en".equals(p.getString("lang", "ro")), unitF = p.getBoolean("unitF", false);
		Texts T = new Texts(en);
		Cities.City c = Cities.City.fromKey(p.getString("city", ""));
		String city = c != null ? c.key() : "";
		boolean own = city.equals(p.getString("widgetCity", ""))
				&& (!city.equals(p.getString("cacheCity", "")) || p.getLong("widgetFetched", 0) > p.getLong("fetched", 0));
		Weather w = null;
		try {
			if (own || city.equals(p.getString("cacheCity", ""))) w = Weather.parse(read(new File(ctx.getFilesDir(), own ? "widget.json" : "forecast.json")));
		} catch (Exception e) {
			w = null;
		}
		RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
		float d = ctx.getResources().getDisplayMetrics().density;
		if (w != null) {
			long t = Math.round(unitF ? w.temp * 9 / 5 + 32 : w.temp);
			v.setTextViewText(R.id.widget_temp, t + "°");
			Bitmap ic = icon(ctx, "icons/" + T.describe(w.code, w.isDay)[0] + ".png", Math.round(44 * d));
			if (ic != null) v.setImageViewBitmap(R.id.widget_icon, ic);
			v.setTextViewText(R.id.widget_sun, sunLine(w, en));
		} else {
			v.setTextViewText(R.id.widget_temp, "--°");
			v.setTextViewText(R.id.widget_sun, en ? "Open the app" : "Deschide aplicația");
		}
		Bitmap pin = icon(ctx, "icons/btn-city.png", Math.round(16 * d));
		if (pin != null) v.setImageViewBitmap(R.id.widget_pin, pin);
		v.setTextViewText(R.id.widget_town, c != null ? c.name : "");
		Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, 0, open,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
		mgr.updateAppWidget(ids, v);
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

	/** An icon of the app (black on transparent) drawn white with a soft shadow, SIZE pixels square. */
	private static Bitmap icon(Context ctx, String asset, int size) {
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
