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

		/** Sets the app's brightness (below 0: the phone's again); SAVE when the finger lifts. */
		void setBrightness(float b, boolean save);

		/** Switches between the dark and the light theme. */
		void toggleTheme();
	}

	// The phone's brightness curve (Android's BrightnessUtils, HLG): slider position 0–1 to the
	// screen's level 0–1 and back, so the bar moves like the system's.
	private static final float R = 0.5f, A = 0.17883277f, B = 0.28466892f, C = 0.55991073f;

	static float toLevel(float pos) {
		pos = Math.max(0, Math.min(1, pos));
		float v = pos <= R ? (pos / R) * (pos / R) : (float) Math.exp((pos - C) / A) + B;
		return Math.max(0.004f, Math.min(1, v / 12));
	}

	static float toPos(float level) {
		float v = Math.max(0, Math.min(1, level)) * 12;
		return v <= 1 ? R * (float) Math.sqrt(v) : A * (float) Math.log(v - B) + C;
	}

	final Screen screen = new Screen();
	private final AndroidGfx gfx;
	private Taps taps;
	private float scale = 1, ox, oy, downX, downY;
	/** Pull-to-refresh: how far the finger has pulled down (pixels), 0 when not pulling. */
	private float pull;
	private boolean pulling;
	/**
	 * Brightness bar, as on the Kindle: a finger moving up or down in a strip a little in from the
	 * left edge (clear of the back gesture), or on the bar the sun button shows, brightens or dims.
	 * The bar goes away 3 s after the sun button if unused, 4 s after the finger lifts, or at a
	 * touch elsewhere.
	 */
	private boolean edge, sliding, barDown, barDrag, moonDown;
	private float pos0;
	private final Runnable hideBar = new Runnable() {
		public void run() {
			screen.brightBar = false;
			invalidate();
		}
	};

	private void showBar(long ms) {
		if (!screen.brightBar) screen.brightLevel = toPos(taps.brightness());
		screen.brightBar = true;
		removeCallbacks(hideBar);
		if (ms > 0) postDelayed(hideBar, ms);
		invalidate();
	}
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

	/** The bar under the finger, DY pixels from where it went down. */
	private void slide(float dy) {
		screen.brightLevel = Math.max(0, Math.min(1, pos0 - dy / (screen.barArea[3] * scale)));
		taps.setBrightness(toLevel(screen.brightLevel), false);
		invalidate();
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		int act = e.getActionMasked();
		if (act == MotionEvent.ACTION_DOWN) {
			downX = e.getX();
			downY = e.getY();
			pulling = false;
			pull = 0;
			edge = !screen.brightBar && downX >= 12 * dp && downX <= 60 * dp;
			sliding = false;
			barDown = screen.brightBar;
			barDrag = false;
			moonDown = false;
			// The bar is shown: drag on it, tap the moon, or a touch elsewhere hides it.
			if (barDown) {
				float ux = (downX - ox) / scale, uy = (downY - oy) / scale;
				removeCallbacks(hideBar);
				if (in(screen.barArea, ux, uy)) {
					barDrag = true;
					pos0 = screen.brightLevel;
				} else if (in(screen.btnMoon, ux, uy)) moonDown = true;
				else hideBar.run();
				return true;
			}
		}
		if (barDown) {
			if (act == MotionEvent.ACTION_MOVE && barDrag) slide(e.getY() - downY);
			if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
				barDown = false;
				float ux = (e.getX() - ox) / scale, uy = (e.getY() - oy) / scale;
				if (barDrag) taps.setBrightness(toLevel(screen.brightLevel), true);
				if (moonDown && in(screen.btnMoon, ux, uy)) taps.toggleTheme();
				if (barDrag || moonDown) showBar(4000);
			}
			return true;
		}
		float dx = e.getX() - downX, dy = e.getY() - downY;
		if (act == MotionEvent.ACTION_MOVE) {
			if (edge && !pulling && !sliding && Math.abs(dy) > 16 * dp && Math.abs(dy) > 2 * Math.abs(dx)) {
				sliding = true;
				showBar(0);
				pos0 = screen.brightLevel;
			}
			if (sliding) {
				// The whole bar's length runs from the dimmest to the brightest.
				if (screen.barArea[3] > 0) slide(dy);
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
			if (sliding) {
				taps.setBrightness(toLevel(screen.brightLevel), true);
				showBar(4000);
			}
			sliding = false;
			pulling = false;
			pull = 0;
			invalidate();
			return true;
		}
		if (act != MotionEvent.ACTION_UP) return true;
		if (sliding) {
			sliding = false;
			taps.setBrightness(toLevel(screen.brightLevel), true);
			showBar(4000);
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
		else if (in(screen.btnBright, x, y)) showBar(3000);
		else if (in(screen.btnCity, x, y) || in(screen.cityArea, x, y)) taps.onCity();
		return true;
	}
}
