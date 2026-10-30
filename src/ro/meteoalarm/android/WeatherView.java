package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import ro.meteoalarm.android.core.Screen;

/** Draws the Screen scaled to the phone (full width, the extra height going to the current weather) or the tablet. */
final class WeatherView extends View {
	interface Taps {
		void onMenu();

		void onRefresh();

		void onCity();

		void onLocate();
	}

	final Screen screen = new Screen();
	private final AndroidGfx gfx;
	private Taps taps;
	private float scale = 1, ox, oy, downX, downY;
	/** Pull-to-refresh: how far the finger has pulled down (pixels), 0 when not pulling. */
	private float pull;
	private boolean pulling;
	private final Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final float dp;

	WeatherView(Context ctx, Taps taps) {
		super(ctx);
		this.taps = taps;
		gfx = new AndroidGfx(ctx.getAssets());
		dp = ctx.getResources().getDisplayMetrics().density;
		setBackgroundColor(0xFFFFFFFF);
	}

	@Override
	protected void onDraw(Canvas c) {
		int w = getWidth(), h = getHeight();
		if (w == 0 || h == 0) return;
		// A tablet: at least 600 dp on the short side (a foldable when open, too).
		screen.tablet = Math.min(w, h) / dp >= 600;
		float[] f = Screen.fit(w, h, screen.tablet);
		scale = f[0];
		float cw = f[1], ch = f[2];
		ox = f[3];
		oy = f[4];
		c.drawColor(screen.dark ? 0xFF262624 : 0xFFFFFFFF);
		c.save();
		c.translate(ox, oy);
		c.scale(scale, scale);
		gfx.c = c;
		screen.draw(gfx, cw, ch);
		c.restore();
		if (pull > 0) drawPull(c);
	}

	/** A round button with the refresh arrow that follows the finger down; full arrow = release to refresh. */
	private void drawPull(Canvas c) {
		float r = 22 * dp, cx = getWidth() / 2f, cy = Math.min(pull, max()) - r + 8 * dp;
		int ink = screen.dark ? 0xFFFAF9F5 : 0xFF000000, page = screen.dark ? 0xFF30302E : 0xFFFFFFFF;
		pp.setStyle(Paint.Style.FILL);
		pp.setColor(page);
		c.drawCircle(cx, cy, r, pp);
		pp.setStyle(Paint.Style.STROKE);
		pp.setStrokeWidth(2 * dp);
		pp.setColor(ink);
		c.drawCircle(cx, cy, r, pp);
		float a = Math.min(1, pull / max());
		pp.setStrokeWidth(3 * dp);
		pp.setColor(a >= 1 && screen.dark ? 0xFFD97757 : ink);
		float ar = 11 * dp;
		c.drawArc(new RectF(cx - ar, cy - ar, cx + ar, cy + ar), -90, 330 * a, false, pp);
	}

	private float max() {
		return 110 * dp;
	}

	private static boolean in(float[] b, float x, float y) {
		return x >= b[0] - 12 && x <= b[0] + b[2] + 12 && y >= b[1] - 12 && y <= b[1] + b[3] + 12;
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		int act = e.getActionMasked();
		if (act == MotionEvent.ACTION_DOWN) {
			downX = e.getX();
			downY = e.getY();
			pulling = false;
			pull = 0;
		}
		float dx = e.getX() - downX, dy = e.getY() - downY;
		if (act == MotionEvent.ACTION_MOVE) {
			// Pulling down (more down than sideways) shows the refresh button; releasing it far enough refreshes.
			if (!pulling && dy > 24 * dp && dy > 2 * Math.abs(dx)) pulling = true;
			if (pulling) {
				pull = Math.max(0, dy - 24 * dp) * 0.8f;
				invalidate();
			}
			return true;
		}
		if (act == MotionEvent.ACTION_CANCEL) {
			pulling = false;
			pull = 0;
			invalidate();
			return true;
		}
		if (act != MotionEvent.ACTION_UP) return true;
		if (pulling) {
			boolean go = pull >= max();
			pulling = false;
			pull = 0;
			invalidate();
			if (go) taps.onRefresh();
			return true;
		}
		float x = (e.getX() - ox) / scale, y = (e.getY() - oy) / scale;
		// A sideways swipe on the warning boxes or the hour boxes shows the next or the previous ones.
		float sx = (downX - ox) / scale, sy = (downY - oy) / scale;
		if (Math.abs(dx) > 40 * scale && Math.abs(dx) > Math.abs(dy)) {
			if (screen.warnArea[2] > 0 && in(screen.warnArea, sx, sy)) screen.warnStart += dx < 0 ? 1 : -1;
			else if (screen.hourArea[2] > 0 && in(screen.hourArea, sx, sy)) screen.hourPage += dx < 0 ? 1 : -1;
			invalidate();
			return true;
		}
		if (in(screen.btnMenu, x, y)) taps.onMenu();
		else if (in(screen.btnRefresh, x, y)) taps.onRefresh();
		else if (in(screen.btnLocate, x, y)) taps.onLocate();
		else if (in(screen.btnCity, x, y) || in(screen.cityArea, x, y)) taps.onCity();
		return true;
	}
}
