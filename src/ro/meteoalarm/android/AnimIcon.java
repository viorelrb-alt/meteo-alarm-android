package ro.meteoalarm.android;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import java.io.InputStream;
import java.util.HashMap;

/**
 * v5.20 (Viorel): the animated icons of the Sistem &amp; Suport bar (assets/sis/NAME.webp: 20
 * frames of 96 px over 3 seconds, black on white), drawn in one colour (the theme's ink). They
 * move for 3 seconds from {@link #restart()} (the Settings window opening), then stay still.
 */
final class AnimIcon extends Drawable {
	static final int FRAMES = 20, PX = 96;
	private static final HashMap<String, Bitmap> masks = new HashMap<String, Bitmap>();
	private static long start = 0;

	private final Bitmap mask;
	private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
	private final Rect src = new Rect(), dst = new Rect();

	/** The bar's icons for the old names (LineIcon), or null when there is none. */
	static String file(String lineName) {
		if (lineName.equals("widget")) return "grid";
		if (lineName.equals("transfer") || lineName.contains(">")) return "transfer";
		if (lineName.equals("log")) return "log";
		if (lineName.equals("sync")) return "update";
		if (lineName.equals("plane")) return "send";
		if (lineName.equals("help")) return "help";
		if (lineName.equals("info")) return "about";
		return null;
	}

	static void restart() {
		start = SystemClock.uptimeMillis();
	}

	private AnimIcon(Bitmap mask, int color) {
		this.mask = mask;
		p.setColor(color);
	}

	/** The animated icon for LINENAME in COLOR, or the plain line icon when the picture is missing. */
	static Drawable of(AssetManager am, String lineName, int color) {
		String f = file(lineName);
		Bitmap m = f == null ? null : load(am, f);
		if (m == null) return new LineIcon(lineName.contains(">") ? "transfer" : lineName, color);
		return new AnimIcon(m, color);
	}

	private static Bitmap load(AssetManager am, String f) {
		Bitmap m = masks.get(f);
		if (m != null) return m;
		try {
			InputStream in = am.open("sis/" + f + ".webp");
			Bitmap b = BitmapFactory.decodeStream(in);
			in.close();
			if (b == null) return null;
			int w = b.getWidth(), h = b.getHeight();
			int[] px = new int[w * h];
			b.getPixels(px, 0, w, 0, 0, w, h);
			for (int k = 0; k < px.length; k++) {
				int q = px[k];
				int lum = (((q >> 16) & 255) * 299 + ((q >> 8) & 255) * 587 + (q & 255) * 114) / 1000;
				px[k] = (255 - lum) << 24;
			}
			Bitmap a = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
			a.setPixels(px, 0, w, 0, 0, w, h);
			m = a.extractAlpha();
			a.recycle();
			b.recycle();
			masks.put(f, m);
			return m;
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public void draw(Canvas c) {
		float t = (SystemClock.uptimeMillis() - start) / 3000f;
		int f = t >= 1 ? FRAMES - 1 : Math.max(0, Math.min(FRAMES - 1, (int) (t * FRAMES)));
		src.set(f * PX, 0, f * PX + PX, PX);
		Rect b = getBounds();
		int s = Math.min(b.width(), b.height());
		dst.set(b.centerX() - s / 2, b.centerY() - s / 2, b.centerX() - s / 2 + s, b.centerY() - s / 2 + s);
		c.drawBitmap(mask, src, dst, p);
		if (t < 1) invalidateSelf();
	}

	@Override
	public void setAlpha(int a) {
		p.setAlpha(a);
	}

	@Override
	public void setColorFilter(ColorFilter cf) {
		p.setColorFilter(cf);
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}
}
