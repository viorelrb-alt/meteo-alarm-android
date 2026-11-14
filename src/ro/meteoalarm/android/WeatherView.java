package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import ro.meteoalarm.android.core.Screen;
import ro.meteoalarm.android.core.Strip;

/** Draws the Screen scaled to the phone (full width, the extra height going to the current weather) or the tablet. */
final class WeatherView extends View {
	interface Taps {
		void onMenu();

		void onRefresh();

		/** The town picker, opened from the button at B (canvas units). */
		void onCity(float[] b);

		/** The sun button showed the brightness bar. */
		void onBright();

		/** Any touch (it also leaves the night clock). */
		void onTouched();

		void onLocate();

		/** A tap on the town's name. */
		void onTownName();

		/** A tap on "Statistics · PDF" of a parcel's main screen (v3.10). */
		void onStats();

		/** Brightness 0.01–1 now (the app's own or the phone's). */
		float brightness();

		/** Sets the app's brightness (below 0: the phone's again); SAVE when the finger lifts. */
		void setBrightness(float b, boolean save);

		/** Switches between the dark and the light theme. */
		void toggleTheme();

		/** The automatic theme (phone sensor), on or off. */
		void toggleSensor();

		/** A tap on box K of the warnings row, at B (canvas units: x, y, w, h). */
		void onAlert(int k, float[] b);

		/** Farmer mode: a tap on hour box K (KIND 0) or rain box K (1), at B. */
		void onFarmBox(int kind, int k, float[] b);

		/** v3.90: a tap on the precipitation chart of the next 12 hours, at B. */
		void onPrecip(float[] b, int part);

		/** v4.00: a tap on box K of the bottom row (not the farmer's), at B. */
		void onInfoBox(int k, float[] b);

		/** v4.00: a tap on an hour box (KIND 0) or a day box (1) outside the farmer mode. */
		void onBox(int kind, int k, float[] b);

		/** v4.00: a tap on the compass box: the big compass. */
		void onCompass();

		/** Farmer mode (v2.60): a swipe over the weather now, to the next (+1) or previous (-1) main screen. */
		void onPage(int step);

		/** v2.90: shows the next (+1) or previous (-1) main screen while the finger slides; false: none. */
		boolean pagePeek(int step);

		/** v2.90: back to the screen the finger started from. */
		void pageBack();

		/** v2.90: the finger let the new screen stay. */
		void pageDone();

		/** v5.30: a tap on tab K (0 the town, 1.. the parcels, the parcels' count + 1: "+"). */
		void onTab(int k);

		/** v5.30: the small farmer button: farmer mode off. */
		void onFarmButton();
	}

	/**
	 * The warnings row moves on by itself (Viorel 2026-11-02): every 2 s it slides smoothly to the
	 * next box and from the last back to the first, while it has more boxes than fit; it waits
	 * while a finger is on the screen and for 20 s after a tap on a box.
	 */
	private static final long ROLL_MS = 2000, ROLL_PAUSE_MS = 20000;
	private long rollPausedUntil;
	private android.animation.ValueAnimator roll;
	private final Runnable rollNext = new Runnable() {
		public void run() {
			postDelayed(this, ROLL_MS);
			final Strip st = screen.alerts;
			if (st.area[2] <= 0 || st.max <= 0 || (dragging && drag == st) || screen.nightClock) return;
			if (System.currentTimeMillis() < rollPausedUntil || !isShown()) return;
			int k = 0;
			while (k < st.snaps.length && st.snaps[k] <= st.scroll + 0.5f) k++;
			float to = k >= st.snaps.length ? 0 : st.snaps[k];
			if (roll != null) roll.cancel();
			roll = android.animation.ValueAnimator.ofFloat(st.scroll, to);
			roll.setDuration(to == 0 ? 900 : 650);
			roll.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
			roll.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
				public void onAnimationUpdate(android.animation.ValueAnimator a) {
					st.scroll = (Float) a.getAnimatedValue();
					invalidate();
				}
			});
			roll.start();
		}
	};

	// --- v5.30 (Viorel): days 3–5 move on by themselves every 3 s (until a finger touches them, back
	// at the next opening of the screen or new weather); 6 s after that the weather details row
	// goes a little to the left and back, to show it moves.
	private static final long CAROUSEL_MS = 3000, NUDGE_MS = 6000;
	private boolean carouselOn = true;
	private android.animation.ValueAnimator carousel, nudge;
	private final Runnable carouselNext = new Runnable() {
		public void run() {
			postDelayed(this, CAROUSEL_MS);
			final Strip st = screen.days2;
			float step = screen.days2Step;
			if (!carouselOn || st.area[2] <= 0 || step <= 0 || (dragging && drag == st) || screen.nightClock || !isShown() || pgA != null) return;
			// Far along: back by whole rounds (the same boxes show), then on by one.
			while (st.scroll >= 3 * step * 30) st.scroll -= 3 * step * 30;
			float to = (Math.round(st.scroll / step) + 1) * step;
			if (carousel != null) carousel.cancel();
			carousel = android.animation.ValueAnimator.ofFloat(st.scroll, to);
			carousel.setDuration(700);
			carousel.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
			carousel.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
				public void onAnimationUpdate(android.animation.ValueAnimator a) {
					st.scroll = (Float) a.getAnimatedValue();
					invalidate();
				}
			});
			carousel.start();
		}
	};
	private final Runnable nudgeRun = new Runnable() {
		public void run() {
			final Strip st = screen.info;
			if (st.area[2] <= 0 || st.max <= 0 || st.scroll > 1 || dragging || !isShown()) return;
			nudge = android.animation.ValueAnimator.ofFloat(0, Math.min(90, st.max), 0);
			nudge.setDuration(1400);
			nudge.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
			nudge.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
				public void onAnimationUpdate(android.animation.ValueAnimator a) {
					if (dragging && drag == st) return;
					st.scroll = (Float) a.getAnimatedValue();
					invalidate();
				}
			});
			nudge.start();
		}
	};

	/** v5.30: the screen opened again or new weather came: the days move on again, the details row nudges. */
	void autoAgain() {
		carouselOn = true;
		removeCallbacks(carouselNext);
		postDelayed(carouselNext, CAROUSEL_MS);
		removeCallbacks(nudgeRun);
		postDelayed(nudgeRun, NUDGE_MS);
	}

	private void stopAuto(Strip st) {
		if (st == screen.days2) {
			if (carouselOn) Log.i(getContext(), "GEST", "caruselul zilelor oprit de atingere");
			carouselOn = false;
			if (carousel != null) carousel.cancel();
		}
		if (st == screen.info && nudge != null) nudge.cancel();
	}

	private boolean slideLogged;

	private String stripName(Strip st) {
		return st == screen.hours ? "orele" : st == screen.days ? "cele 5 zile" : st == screen.info ? "casetele de jos" : st == screen.grid ? "grila de info" : st == screen.alerts ? "avertizări" : st == screen.rain ? "casetele de ploaie" : "rând";
	}

	/** Stops the row moving by itself for 20 s (a tap on a box, a finger on the row). */
	void pauseRoll() {
		rollPausedUntil = System.currentTimeMillis() + ROLL_PAUSE_MS;
		if (roll != null) roll.cancel();
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		removeCallbacks(rollNext);
		postDelayed(rollNext, ROLL_MS);
		autoAgain();
	}

	@Override
	protected void onDetachedFromWindow() {
		removeCallbacks(rollNext);
		removeCallbacks(carouselNext);
		removeCallbacks(nudgeRun);
		super.onDetachedFromWindow();
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
	private boolean edge, sliding, barDown, barDrag, moonDown, pageCand;
	private float pos0;
	private final Runnable hideBar = new Runnable() {
		public void run() {
			screen.brightBar = false;
			invalidate();
		}
	};

	/**
	 * The text-size bar (v2.10): sliding up or down near the right edge shows it on the left and
	 * sets the size of the menus and pages (TextSize); it goes away 4 s after the finger lifts.
	 */
	private boolean edgeR, sizing, sizeDown, sizeDrag;
	private final Runnable hideSize = new Runnable() {
		public void run() {
			screen.sizeBar = false;
			invalidate();
		}
	};

	private void showSize(long ms) {
		if (!screen.sizeBar) screen.sizeLevel = TextSize.pos(TextSize.get(getContext()));
		screen.sizeBar = true;
		removeCallbacks(hideSize);
		if (ms > 0) postDelayed(hideSize, ms);
		invalidate();
	}

	private void slideSize(float dy, boolean save) {
		if (screen.sizeArea[3] > 0) screen.sizeLevel = Math.max(0, Math.min(1, pos0 - dy / (screen.sizeArea[3] * scale)));
		TextSize.set(getContext(), TextSize.scale(screen.sizeLevel), save);
		invalidate();
	}

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
		if (pgA != null) {
			drawPaging(c);
			return;
		}
		drawScreen(c);
		if (pull > 0) drawPull(c);
		String big = screen.bigIcon;
		if (big != null && !big.equals(iconDone)) startIcon(big);
	}

	// --- v3.70 (Viorel): the icon of the weather now moves for 3 seconds, then stays still ------

	/** The icon that already moved; another one (other weather, town or screen) moves again. */
	private String iconDone;
	private long iconStart;
	private boolean iconRunning;

	/** The icon moves again (the app comes back on the screen). */
	void replayIcon() {
		iconDone = null;
		autoAgain();
		invalidate();
	}

	// v3.71: its own clock, not a ValueAnimator: with the phone's animations shortened or turned
	// off (developer options, power saving) a ValueAnimator ends at once and the icon never moved.
	private final Runnable iconStep = new Runnable() {
		public void run() {
			float t = (android.os.SystemClock.uptimeMillis() - iconStart) / 3000f;
			if (t >= 1 || !isShown()) {
				screen.iconT = 1;
				iconRunning = false;
			} else {
				screen.iconT = t;
				postOnAnimation(this);
			}
			invalidate();
		}
	};

	private void startIcon(String big) {
		boolean first = iconDone == null;
		iconDone = big;
		if (iconRunning) return;
		iconRunning = true;
		iconStart = android.os.SystemClock.uptimeMillis();
		screen.iconT = 0;
		if (first) Log.i(getContext(), "ECRAN", "iconul vremii se mișcă 3 s: " + big);
		postOnAnimation(iconStep);
		invalidate();
	}

	// --- v3.70 (Viorel): in portrait the clock, the connection and the date sit in the camera band --

	/** Height of the camera band at the top (pixels; 0 = none or not used). */
	private int bandPx;

	void band(int px) {
		if (px == bandPx) return;
		bandPx = px;
		invalidate();
	}

	/** The band is used only in portrait on a phone. */
	private int bandUsed() {
		return bandPx > 0 && getWidth() < getHeight() && !screen.tablet ? bandPx : 0;
	}

	// --- v2.90 (Viorel 2026-11-08): farmer mode, the main screens slide with the finger -----------
	// Only the part between the top line (with the FARMER MODE row and the dots) and the footer
	// (the buttons) moves; past half the width the next screen stays, else the screen goes back.

	/** The screen the finger started on, and the next one (null when there is none). */
	private Bitmap pgA, pgB;
	/** Toward which screen (+1 next, -1 previous), whether it exists, and how far (pixels). */
	private int pgStep;
	private boolean pgOk, pgCand, paging;
	private float pgDx;
	private android.animation.ValueAnimator pgAnim;

	/** The screen drawn into a picture of the view's size. */
	private Bitmap shot() {
		Bitmap b = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
		drawScreen(new Canvas(b));
		return b;
	}

	/** v3.91 (Viorel): on a phone the menu, the town and the line sit this many units higher; the weather gets the room. */
	private float lift() {
		// v3.92 (Viorel): in portrait under the camera band right up to it.
		return screen.tablet ? 0 : bandUsed() > 0 ? 22 : 16;
	}

	/** The top and the bottom of the moving part, in pixels. */
	private float[] band() {
		float[] f = Screen.fit(getWidth(), getHeight() - bandUsed(), screen.tablet);
		f[2] += lift();
		// v5.30: phones: the page under the glass (portrait) or the left part (landscape).
		if (!screen.tablet && screen.pageS.area[2] > 0) return new float[] { oy + screen.pageS.area[1] * scale, oy + (f[2] - 90) * scale };
		// v3.10: in landscape the boxes start right under the dots (124 units), so the slide starts there.
		float top = getWidth() > getHeight() ? 123 : 137;
		return new float[] { oy + top * scale, oy + (f[2] - 80) * scale };
	}

	private void drawPaging(Canvas c) {
		int w = getWidth();
		float[] b = band();
		c.drawBitmap(pgA, 0, 0, null);
		c.save();
		c.clipRect(0, b[0], w, b[1]);
		c.drawColor(ro.meteoalarm.android.core.Theme.page(screen.dark));
		c.drawBitmap(pgA, pgDx, 0, null);
		if (pgB != null) c.drawBitmap(pgB, pgDx + (pgStep > 0 ? w : -w), 0, null);
		c.restore();
	}

	/** The finger started sliding the screens toward STEP: pictures of this screen and of that one. */
	private void pageStart(int step) {
		if (settle != null) settle.cancel();
		pgA = shot();
		pageTo(step);
	}

	private void pageTo(int step) {
		pgStep = step;
		pgOk = taps.pagePeek(step);
		if (pgB != null) pgB.recycle();
		pgB = pgOk ? shot() : null;
		invalidate();
	}

	private void pageMove(float dx) {
		int step = dx < 0 ? 1 : -1;
		if (dx != 0 && step != pgStep) {
			if (pgOk) taps.pageBack();
			pageTo(step);
		}
		// No screen that way: the screen only gives a little.
		pgDx = pgOk ? dx : dx * 0.25f;
		invalidate();
	}

	private void pageEnd(float vx) {
		final int w = getWidth();
		boolean fling = Math.abs(vx) > 1200 * dp && (vx < 0) == (pgStep > 0) && Math.abs(pgDx) > 30 * dp;
		final boolean go = pgOk && (Math.abs(pgDx) > w / 2f || fling);
		if (!pgOk && Math.abs(pgDx) > 12 * dp) taps.onPage(pgStep);
		float to = go ? (pgStep > 0 ? -w : w) : 0;
		Log.i(getContext(), "GEST", "glisare între ecrane: " + (go ? (pgStep > 0 ? "următorul" : "anteriorul") : "rămâne"));
		pgAnim = android.animation.ValueAnimator.ofFloat(pgDx, to);
		pgAnim.setDuration((long) Math.min(320, 140 + Math.abs(to - pgDx) / dp * 0.6f));
		pgAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
		pgAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
			public void onAnimationUpdate(android.animation.ValueAnimator a) {
				pgDx = (Float) a.getAnimatedValue();
				invalidate();
			}
		});
		pgAnim.addListener(new android.animation.AnimatorListenerAdapter() {
			boolean done;

			@Override
			public void onAnimationEnd(android.animation.Animator a) {
				if (done) return;
				done = true;
				if (go) taps.pageDone();
				else if (pgOk) taps.pageBack();
				if (pgA != null) pgA.recycle();
				if (pgB != null) pgB.recycle();
				pgA = pgB = null;
				pgDx = 0;
				invalidate();
			}
		});
		pgAnim.start();
	}

	private void drawScreen(Canvas c) {
		int w = getWidth(), h = getHeight();
		if (w == 0 || h == 0) return;
		// A tablet: at least 600 dp on the short side (a foldable when open, too).
		screen.tablet = Math.min(w, h) / dp >= 600;
		int bp = bandUsed();
		float[] f = Screen.fit(w, h - bp, screen.tablet);
		scale = f[0];
		float lift = lift(), cw = f[1], ch = f[2] + lift;
		ox = f[3];
		oy = f[4] + bp - lift * scale;
		screen.lift = lift;
		screen.band = bp / scale;
		screen.bandTop = -oy / scale;
		c.drawColor(ro.meteoalarm.android.core.Theme.page(screen.dark));
		gfx.frostBmp = null;
		if (!screen.tablet && !screen.nightClock) frostCopy(w, h, cw, ch);
		c.save();
		c.translate(ox, oy);
		c.scale(scale, scale);
		gfx.c = c;
		screen.draw(gfx, cw, ch);
		c.restore();
	}

	// --- v5.30: the blurred copy of the page that shows through the glass ----------------------

	private Bitmap frost;
	private Canvas frostC;
	private static final int FROST_F = 6;

	private void frostCopy(int w, int h, float cw, float ch) {
		try {
			int fw = Math.max(1, w / FROST_F), fh = Math.max(1, h / FROST_F);
			if (frost == null || frost.getWidth() != fw || frost.getHeight() != fh) {
				if (frost != null) frost.recycle();
				frost = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
				frostC = new Canvas(frost);
			}
			frostC.drawColor(ro.meteoalarm.android.core.Theme.page(screen.dark));
			frostC.save();
			frostC.scale(fw / (float) w, fh / (float) h);
			frostC.translate(ox, oy);
			frostC.scale(scale, scale);
			gfx.c = frostC;
			screen.layer = 1;
			screen.draw(gfx, cw, ch);
			frostC.restore();
			blur(frost, 2);
			blur(frost, 2);
			gfx.frostBmp = frost;
			gfx.frostDst.set(-ox / scale, -oy / scale, (w - ox) / scale, (h - oy) / scale);
		} catch (Throwable t) {
			gfx.frostBmp = null;
		} finally {
			screen.layer = 0;
		}
	}

	private int[] fpx, fout;

	/** A box blur of radius R, across then down. */
	private void blur(Bitmap b, int r) {
		int w = b.getWidth(), h = b.getHeight(), n = w * h;
		if (fpx == null || fpx.length != n) {
			fpx = new int[n];
			fout = new int[n];
		}
		int[] px = fpx, out = fout;
		b.getPixels(px, 0, w, 0, 0, w, h);
		for (int pass = 0; pass < 2; pass++) {
			boolean hor = pass == 0;
			int len = hor ? w : h, m = hor ? h : w;
			for (int j = 0; j < m; j++) {
				int rr = 0, gg = 0, bb = 0, cnt = 2 * r + 1;
				for (int k = -r; k <= r; k++) {
					int q = Math.max(0, Math.min(len - 1, k)), v = hor ? px[j * w + q] : px[q * w + j];
					rr += (v >> 16) & 255; gg += (v >> 8) & 255; bb += v & 255;
				}
				for (int i = 0; i < len; i++) {
					int o = 0xFF000000 | (rr / cnt) << 16 | (gg / cnt) << 8 | (bb / cnt);
					if (hor) out[j * w + i] = o;
					else out[i * w + j] = o;
					int qa = Math.max(0, Math.min(len - 1, i - r)), qb = Math.max(0, Math.min(len - 1, i + r + 1));
					int va = hor ? px[j * w + qa] : px[qa * w + j], vb = hor ? px[j * w + qb] : px[qb * w + j];
					rr += ((vb >> 16) & 255) - ((va >> 16) & 255);
					gg += ((vb >> 8) & 255) - ((va >> 8) & 255);
					bb += (vb & 255) - (va & 255);
				}
			}
			int[] t = px;
			px = out;
			out = t;
		}
		b.setPixels(px, 0, w, 0, 0, w, h);
	}

	/** A round button with the refresh arrow that follows the finger down; full arrow = release to refresh. */
	private void drawPull(Canvas c) {
		float r = 22 * dp, cx = getWidth() / 2f, cy = Math.min(pull, max()) - r + 8 * dp;
		int ink = ro.meteoalarm.android.core.Theme.ink(screen.dark), page = ro.meteoalarm.android.core.Theme.surface(screen.dark);
		pp.setStyle(Paint.Style.FILL);
		pp.setColor(page);
		c.drawCircle(cx, cy, r, pp);
		pp.setStyle(Paint.Style.STROKE);
		pp.setStrokeWidth(2 * dp);
		pp.setColor(ink);
		c.drawCircle(cx, cy, r, pp);
		float a = Math.min(1, pull / max());
		pp.setStrokeWidth(3 * dp);
		pp.setColor(a >= 1 && screen.dark ? ro.meteoalarm.android.core.Theme.accent(true) : ink);
		float ar = 11 * dp;
		c.drawArc(new RectF(cx - ar, cy - ar, cx + ar, cy + ar), -90, 330 * a, false, pp);
	}

	private float max() {
		return 110 * dp;
	}

	/** A button at B (canvas units: x, y, w, h) on the screen, in pixels: left, top, right, bottom. */
	float[] px(float[] b) {
		return new float[] { ox + b[0] * scale, oy + b[1] * scale, ox + (b[0] + b[2]) * scale, oy + (b[1] + b[3]) * scale };
	}

	/** Canvas units to pixels. */
	float scale() {
		return scale;
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

	/** The row under the finger (moved by it), and where it was when the finger went down. */
	private Strip drag;
	private boolean dragging;
	private float scroll0;
	private VelocityTracker vt;
	private android.animation.ValueAnimator settle;

	/** Slides ST to the box nearest to where it is (the next one in a fling, V units/s). */
	private void settle(final Strip st, float v) {
		float from = st.scroll, to = st.target(from, v);
		// v5.30: the page glides on after a fling and stops where it slows down (no boxes to settle on).
		if (st == screen.pageS) to = Math.max(0, Math.min(st.max, from + v * 0.32f));
		settle = android.animation.ValueAnimator.ofFloat(from, to);
		settle.setDuration(st == screen.pageS ? (long) Math.min(900, 220 + Math.abs(to - from) * 0.5f) : (long) Math.min(320, 120 + Math.abs(to - from) * 0.8f));
		settle.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
		settle.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
			public void onAnimationUpdate(android.animation.ValueAnimator a) {
				st.scroll = (Float) a.getAnimatedValue();
				invalidate();
			}
		});
		settle.start();
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		int act = e.getActionMasked();
		// The night clock: a touch only brings the weather back.
		if (screen.nightClock) {
			if (act == MotionEvent.ACTION_DOWN) taps.onTouched();
			return true;
		}
		if (act == MotionEvent.ACTION_DOWN) taps.onTouched();
		// The screens are still sliding into place.
		if (pgA != null && !paging) return true;
		if (act == MotionEvent.ACTION_DOWN) {
			downX = e.getX();
			downY = e.getY();
			pulling = false;
			pull = 0;
			edge = !screen.brightBar && !screen.sizeBar && downX >= 12 * dp && downX <= 60 * dp;
			edgeR = false; // v3.11 (Viorel): no text-size bar from the right edge any more (Aa in Help / About stays).
			sizing = false;
			sizeDown = screen.sizeBar;
			sizeDrag = false;
			if (sizeDown) {
				float ux = (downX - ox) / scale, uy = (downY - oy) / scale;
				removeCallbacks(hideSize);
				if (in(screen.sizeArea, ux, uy)) {
					sizeDrag = true;
					pos0 = screen.sizeLevel;
				} else hideSize.run();
				return true;
			}
			sliding = false;
			slideLogged = false;
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
		if (sizeDown) {
			if (act == MotionEvent.ACTION_MOVE && sizeDrag) slideSize(e.getY() - downY, false);
			if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
				sizeDown = false;
				if (sizeDrag) {
					slideSize(e.getY() - downY, true);
					showSize(4000);
				}
			}
			return true;
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
		// The rows of boxes follow the finger (sideways, the info grid up and down) and settle on a box.
		if (act == MotionEvent.ACTION_DOWN) {
			dragging = false;
			drag = null;
			float ux = (downX - ox) / scale, uy = (downY - oy) / scale;
			boolean onPage = screen.inPage(ux, uy);
			for (Strip st : screen.strips())
				if (st.area[2] > 0 && st.max > 0 && in(st.area, ux, uy) && (onPage || st == screen.alerts || st == screen.tabs)) drag = st;
			if (drag == screen.alerts) pauseRoll();
			if (drag != null) stopAuto(drag);
			// v5.30: the page itself moves up and down (not from the brightness edge).
			pageCand = onPage && !edge && screen.pageS.area[2] > 0 && screen.pageS.max > 0;
			if (pageCand && settle != null) settle.cancel();
			// v2.90: farmer mode with parcels: a sideways slide between the top line and the
			// footer, not on a row of boxes, moves the whole middle of the screen.
			float[] bd = getWidth() > 0 ? band() : new float[] { 0, 0 };
			paging = false;
			// v3.03 (Viorel): also above the top line (town, clock, dots).
			pgCand = drag == null && screen.pages > 1 && !edge && !edgeR && downY < bd[1];
			if (pgCand || pageCand) {
				if (vt == null) vt = VelocityTracker.obtain();
				vt.clear();
			}
			if (drag != null) {
				if (settle != null) settle.cancel();
				scroll0 = drag.scroll;
				if (vt == null) vt = VelocityTracker.obtain();
				vt.clear();
			}
		}
		if ((drag != null || pgCand || pageCand) && vt != null) vt.addMovement(e);
		if (pgCand && !paging && act == MotionEvent.ACTION_MOVE && !pulling && Math.abs(dx) > 10 * dp && Math.abs(dx) > Math.abs(dy)) {
			paging = true;
			Log.i(getContext(), "GEST", "glisare între ecrane începută");
			pageStart(dx < 0 ? 1 : -1);
		}
		if (paging) {
			if (act == MotionEvent.ACTION_MOVE) pageMove(dx);
			else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
				paging = false;
				pgCand = false;
				vt.computeCurrentVelocity(1000);
				pageEnd(act == MotionEvent.ACTION_CANCEL ? 0 : vt.getXVelocity());
			}
			return true;
		}
		if (drag != null && act == MotionEvent.ACTION_MOVE && !dragging && !pulling && !sliding) {
			float along = drag.vertical ? dy : dx, across = drag.vertical ? dx : dy;
			if (Math.abs(along) > 10 * dp && Math.abs(along) > Math.abs(across)) dragging = true;
		}
		// v5.30: up and down the page moves (pulling down at its top still refreshes).
		if (pageCand && !dragging && act == MotionEvent.ACTION_MOVE && !pulling && !sliding && Math.abs(dy) > 10 * dp && Math.abs(dy) > Math.abs(dx)
				&& !(dy > 0 && screen.pageS.scroll <= 0.5f)) {
			drag = screen.pageS;
			scroll0 = drag.scroll;
			dragging = true;
			if (nudge != null) nudge.cancel();
		}
		if (dragging) {
			float along = drag.vertical ? dy : dx;
			if (act == MotionEvent.ACTION_MOVE) {
				float s = scroll0 - along / scale;
				// A little give past the ends.
				if (s < 0) s *= 0.3f;
				else if (s > drag.max) s = drag.max + (s - drag.max) * 0.3f;
				drag.scroll = s;
				invalidate();
			} else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
				dragging = false;
				Log.i(getContext(), "GEST", "glisare pe " + stripName(drag));
				vt.computeCurrentVelocity(1000);
				settle(drag, -(drag.vertical ? vt.getYVelocity() : vt.getXVelocity()) / scale);
				drag = null;
			}
			return true;
		}
		if (act == MotionEvent.ACTION_MOVE) {
			if (edgeR && !pulling && !sizing && Math.abs(dy) > 16 * dp && Math.abs(dy) > 2 * Math.abs(dx)) {
				sizing = true;
				showSize(0);
				pos0 = screen.sizeLevel;
				Log.i(getContext(), "GEST", "marginea dreaptă: bara de mărime a textului");
			}
			if (sizing) {
				slideSize(dy, false);
				return true;
			}
			if (edge && !pulling && !sliding && Math.abs(dy) > 16 * dp && Math.abs(dy) > 2 * Math.abs(dx)) {
				sliding = true;
				showBar(0);
				pos0 = screen.brightLevel;
			}
			if (sliding) {
				if (!slideLogged) {
					slideLogged = true;
					Log.i(getContext(), "GEST", "marginea stângă: bara de luminozitate");
				}
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
		if (sizing && (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL)) {
			sizing = false;
			slideSize(dy, true);
			showSize(4000);
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
			Log.i(getContext(), "GEST", "tras în jos" + (go ? ": reîmprospătare" : ", eliberat prea devreme"));
			if (go) taps.onRefresh();
			return true;
		}
		float x = (e.getX() - ox) / scale, y = (e.getY() - oy) / scale;
		if (Math.abs(dx) > 40 * scale && Math.abs(dx) > Math.abs(dy)) {
			// Farmer mode (v2.60): sideways over the weather now goes to the next main screen.
			float ux = (downX - ox) / scale, uy = (downY - oy) / scale;
			// v3.00 (Viorel): only a slide that began under the top line (above it nothing moves).
			float[] bd = band();
			if (screen.pages > 1 && screen.nowArea[2] > 0 && Math.abs(dx) > 50 * dp && in(screen.nowArea, ux, uy) && downY > bd[0] && downY < bd[1]) {
				Log.i(getContext(), "GEST", "glisare pe vremea de acum: ecranul " + (dx < 0 ? "următor" : "anterior"));
				taps.onPage(dx < 0 ? 1 : -1);
			}
			return true;
		}
		// v5.30: the glass and the capsules first; the page's boxes only where the page shows.
		int tk = screen.tabAt(x, y);
		if (tk >= 0) {
			taps.onTab(tk);
			return true;
		}
		if (screen.btnFarm[2] > 0 && in(screen.btnFarm, x, y)) {
			taps.onFarmButton();
			return true;
		}
		boolean onPage = screen.inPage(x, y);
		int sk = onPage ? screen.statusAt(x, y) : -1;
		if (sk >= 0) {
			taps.onFarmBox(2, sk, screen.statusArea.clone());
			return true;
		}
		// A tap on a warning (or earthquake) box: its details, and the row waits 20 s.
		int[] fb = onPage ? screen.farmBoxAt(x, y) : null;
		if (fb != null) {
			taps.onFarmBox(fb[0], fb[1], screen.farmBoxRect(fb[0], fb[1]));
			return true;
		}
		int ak = screen.alertAt(x, y);
		if (ak >= 0) {
			pauseRoll();
			taps.onAlert(ak, screen.alertRect(ak));
			return true;
		}
		if (onPage && screen.precipArea[2] > 0 && in(screen.precipArea, x, y) && (screen.hours.area[2] <= 0 || in(screen.hours.area, x, y))) {
			int j = screen.partAt(x, y);
			taps.onPrecip(j < 0 ? screen.precipArea.clone() : screen.partArea[j].clone(), j < 0 ? -1 : screen.partIdx[j]);
			return true;
		}
		int ik = onPage ? screen.infoAt(x, y) : -1;
		if (ik >= 0) {
			if (screen.infoId(ik).equals("compass")) taps.onCompass();
			else taps.onInfoBox(ik, screen.infoRect(ik));
			return true;
		}
		int[] hb = onPage ? screen.boxAt(x, y) : null;
		if (hb != null) {
			taps.onBox(hb[0], hb[1], screen.boxRect(hb[0], hb[1]));
			return true;
		}
		if (in(screen.btnMenu, x, y)) taps.onMenu();
		else if (in(screen.btnLocate, x, y)) taps.onLocate();
		else if (in(screen.btnBright, x, y)) {
			showBar(3000);
			taps.onBright();
		}
		else if (in(screen.btnSensor, x, y)) taps.toggleSensor();
		else if (in(screen.btnCity, x, y)) taps.onCity(screen.btnCity);
		else if (screen.btnStats[2] > 0 && in(screen.btnStats, x, y) && (screen.tablet || onPage)) taps.onStats();
		else if (in(screen.cityArea, x, y)) taps.onTownName();
		else if (screen.updArea[2] > 0 && in(screen.updArea, x, y)) taps.onRefresh(); // v5.00: the "updated" pill
		return true;
	}
}
