package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * v2.170 (Viorel): the weather map at the end of the main screen's page. A map like the towns' one
 * (light, the names big) round the screen's town, with one weather layer (clouds, temperatures or
 * rain) and its legend, drawn off the screen into a picture that the page shows ("live:wxmap"). It
 * does not move with the finger, so the page scrolls as before.
 */
final class WxMap {
	static final String PIC = "live:wxmap";
	private final Context ctx;
	private final Runnable onPic;
	private final Handler ui = new Handler(Looper.getMainLooper());
	private MapView m;
	private int w, h, r;
	private double lat = Double.NaN, lon = Double.NaN;
	private int darkSet = -1;
	private boolean queued, drawing;

	WxMap(Context ctx, Runnable onPic) {
		this.ctx = ctx;
		this.onPic = onPic;
	}

	/** The map W x H pixels with corners R, round LA, LO, in the dark theme or not, the layer K (0 none). */
	void set(int w, int h, int r, double la, double lo, boolean dark, boolean imperial, int k) {
		if (w <= 0 || h <= 0 || Double.isNaN(la)) return;
		if (m == null) {
			m = new MapView(ctx);
			m.setNames(true);
			m.onChange = new Runnable() {
				public void run() {
					if (!drawing) later();
				}
			};
			m.setOnGrid(new Runnable() {
				public void run() {
					later();
				}
			});
		}
		boolean again = false;
		if (w != this.w || h != this.h || r != this.r) {
			this.w = w;
			this.h = h;
			this.r = r;
			m.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
			m.layout(0, 0, w, h);
			again = true;
		}
		if (darkSet != (dark ? 1 : 0)) {
			darkSet = dark ? 1 : 0;
			m.setDark(dark);
			m.setNight(dark);
			again = true;
		}
		if (Math.abs(la - lat) > 1e-4 || Math.abs(lo - lon) > 1e-4) {
			lat = la;
			lon = lo;
			m.setZoom(11);
			m.center(la, lo);
			m.setMe(la, lo);
			again = true;
		}
		m.setImperial(imperial);
		if (m.layer() != k) {
			m.setLayer(k);
			again = true;
		} else m.wantGrid();
		if (again) later();
	}

	int layer() {
		return m == null ? 0 : m.layer();
	}

	boolean rainNear() {
		return m != null && m.rainNear();
	}

	boolean gridKnown() {
		return m != null && m.gridKnown();
	}

	/** v2.180 (Viorel): the hours of the animation (null while unknown), the current one's place, the hour shown (-1 now). */
	String[] hours() {
		return m == null ? null : m.hours();
	}

	int hourNow() {
		return m == null ? -1 : m.hourNow();
	}

	int frame() {
		return m == null ? -1 : m.frame();
	}

	/** Shows hour K (-1: now) and makes a new picture. */
	void frame(int k) {
		if (m == null || m.frame() == k) return;
		m.setFrame(k);
		later();
	}

	/** Asks for new values when they are old (the screen came back). */
	void refresh() {
		if (m != null) m.wantGrid();
	}

	private void later() {
		if (queued) return;
		queued = true;
		ui.postDelayed(draw, 150);
	}

	private final Runnable draw = new Runnable() {
		public void run() {
			queued = false;
			if (m == null || w <= 0 || h <= 0) return;
			Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
			Canvas c = new Canvas(b);
			Path p = new Path();
			p.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
			c.clipPath(p);
			drawing = true;
			try {
				m.draw(c);
			} finally {
				drawing = false;
			}
			// The old picture is left to the collector (the screen may still hold it).
			AndroidGfx.live.put(PIC, b);
			onPic.run();
		}
	};
}
