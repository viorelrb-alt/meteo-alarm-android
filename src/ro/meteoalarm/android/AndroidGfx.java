package ro.meteoalarm.android;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import ro.meteoalarm.android.core.Gfx;

/** Gfx on an Android Canvas that is already scaled to canvas units. */
final class AndroidGfx implements Gfx {
	private final AssetManager assets;
	private final Typeface reg, bold;
	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG),
			stroke = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
	private final Map<String, Bitmap> bitmaps = new HashMap<String, Bitmap>();
	private final RectF rf = new RectF();
	private final Rect src = new Rect();
	Canvas c;

	AndroidGfx(AssetManager assets) {
		this.assets = assets;
		reg = Typeface.createFromAsset(assets, "fonts/LiberationSans-Regular.ttf");
		bold = Typeface.createFromAsset(assets, "fonts/LiberationSans-Bold.ttf");
		fill.setStyle(Paint.Style.FILL);
		stroke.setStyle(Paint.Style.STROKE);
		stroke.setStrokeCap(Paint.Cap.BUTT);
	}

	public void rect(float x, float y, float w, float h, int color) {
		fill.setColor(color);
		c.drawRect(x, y, x + w, y + h, fill);
	}

	public void roundRect(float x, float y, float w, float h, float r, int color) {
		fill.setColor(color);
		rf.set(x, y, x + w, y + h);
		c.drawRoundRect(rf, r, r, fill);
	}

	private Paint font(boolean b, float px) {
		text.setTypeface(b ? bold : reg);
		text.setTextSize(px / 1.1172f);
		return text;
	}

	public void text(boolean b, float px, float top, float left, String s, int color) {
		Paint p = font(b, px);
		p.setColor(color);
		c.drawText(s, left, top + px * 0.81f, p);
	}

	public float width(boolean b, float px, String s) {
		return font(b, px).measureText(s);
	}

	public void icon(String asset, float x, float y, float w, float h, int color) {
		Bitmap b = bitmaps.get(asset);
		if (b == null) {
			try {
				InputStream in = assets.open(asset);
				b = mask(BitmapFactory.decodeStream(in));
				in.close();
			} catch (Exception e) {
				return;
			}
			bitmaps.put(asset, b);
		}
		src.set(0, 0, b.getWidth(), b.getHeight());
		rf.set(x, y, x + w, y + h);
		bmp.setColor(color);
		c.drawBitmap(b, src, rf, bmp);
	}

	/** Alpha mask of a black-on-white image: dark pixels opaque, white transparent. */
	private static Bitmap mask(Bitmap src) {
		int w = src.getWidth(), h = src.getHeight();
		int[] px = new int[w * h];
		src.getPixels(px, 0, w, 0, 0, w, h);
		for (int k = 0; k < px.length; k++) {
			int p = px[k];
			int lum = (((p >> 16) & 255) * 299 + ((p >> 8) & 255) * 587 + (p & 255) * 114) / 1000;
			int a = (255 - lum) * (p >>> 24) / 255;
			px[k] = a << 24;
		}
		Bitmap m = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		m.setPixels(px, 0, w, 0, 0, w, h);
		return m.extractAlpha();
	}

	public void arc(float cx, float cy, float r, float start, float sweep, float width, int color) {
		stroke.setColor(color);
		stroke.setStrokeWidth(width);
		rf.set(cx - r, cy - r, cx + r, cy + r);
		c.drawArc(rf, start, sweep, false, stroke);
	}

	public void poly(float[] xy, int color) {
		Path p = new Path();
		p.moveTo(xy[0], xy[1]);
		for (int k = 2; k < xy.length; k += 2) p.lineTo(xy[k], xy[k + 1]);
		p.close();
		fill.setColor(color);
		c.drawPath(p, fill);
	}
}
