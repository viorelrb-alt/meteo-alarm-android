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

		/** Brightness 0.01–1 now (the app's own or the phone's). */
		float brightness();

		/** Sets the app's brightness; SAVE when the finger lifts. */
		void setBrightness(float b, boolean save);
	}

	final Screen screen = new Screen();
	private final AndroidGfx gfx;
	private Taps taps;
	private float scale = 1, ox, oy, downX, downY;
	/** Pull-to-refresh: how far the finger has pulled down (pixels), 0 when not pulling. */
	private float pull;
	private boolean pulling;
	/**
	 * Left-edge brightness slider, as on the Kindle: a finger moving up or down in a strip a little in
	 * from the left edge (clear of the back gesture) brightens or dims; a bar shows the level.
	 */
	private boolean edge, sliding;
	private float bright, bright0;
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
		if (sliding) drawBright(c);
	}

	/** The brightness bar next to the left edge: the level filled from the bottom, a sun and the percentage. */
	private void drawBright(Canvas c) {
		float bw = 14 * dp, bh = getHeight() * 0.5f, x = 64 * dp, y = (getHeight() - bh) / 2, r = bw / 2;
		int ink = screen.dark ? 0xFFFAF9F5 : 0xFF000000, page = screen.dark ? 0xFF30302E : 0xFFFFFFFF;
		float p = (float) Math.sqrt(bright);
		pp.setStyle(Paint.Style.FILL);
		pp.setColor(page);
		c.drawRoundRect(new RectF(x - 6 * dp, y - 52 * dp, x + bw + 6 * dp, y + bh + 6 * dp), 12 * dp, 12 * dp, pp);
		pp.setStyle(Paint.Style.STROKE);
		pp.setStrokeWidth(2 * dp);
		pp.setColor(ink);
		c.drawRoundRect(new RectF(x, y, x + bw, y + bh), r, r, pp);
		pp.setStyle(Paint.Style.FILL);
		c.drawRoundRect(new RectF(x, y + bh * (1 - p), x + bw, y + bh), r, r, pp);
		// A small sun above the bar.
		float cx = x + bw / 2, cy = y - 30 * dp;
		c.drawCircle(cx, cy, 5 * dp, pp);
		pp.setStrokeWidth(2 * dp);
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4;
			float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
			c.drawLine(cx + ca * 8 * dp, cy + sa * 8 * dp, cx + ca * 12 * dp, cy + sa * 12 * dp, pp);
		}
		pp.setTextSize(15 * dp);
		pp.setFakeBoldText(true);
		c.drawText(Math.round(bright * 100) + "%", x + bw + 14 * dp, y + bh * (1 - p) + 6 * dp, pp);
		pp.setFakeBoldText(false);
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
			edge = downX >= 12 * dp && downX <= 60 * dp;
			sliding = false;
		}
		float dx = e.getX() - downX, dy = e.getY() - downY;
		if (act == MotionEvent.ACTION_MOVE) {
			if (edge && !pulling && !sliding && Math.abs(dy) > 16 * dp && Math.abs(dy) > 2 * Math.abs(dx)) {
				sliding = true;
				bright0 = taps.brightness();
			}
			if (sliding) {
				// The bar follows the square root of the level, so the dim end gets more room.
				float p = (float) Math.sqrt(bright0) - dy / (getHeight() * 0.5f);
				p = Math.max(0.1f, Math.min(1, p));
				bright = p * p;
				taps.setBrightness(bright, false);
				invalidate();
				return true;
			}
			// Pulling down (more down than sideways) shows the refresh button; releasing it far enough refreshes.
			if (!pulling && dy > 24 * dp && dy > 2 * Math.abs(dx)) pulling = true;
			if (pulling) {
				pull = Math.max(0, dy - 24 * dp) * 0.8f;
				invalidate();
			}
			return true;
		}
		if (act == MotionEvent.ACTION_CANCEL) {
			if (sliding) taps.setBrightness(bright, true);
			sliding = false;
			pulling = false;
			pull = 0;
			invalidate();
			return true;
		}
		if (act != MotionEvent.ACTION_UP) return true;
		if (sliding) {
			sliding = false;
			taps.setBrightness(bright, true);
			invalidate();
			return true;
		}
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
