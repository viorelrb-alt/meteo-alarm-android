package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import ro.meteoalarm.android.core.Screen;

/**
 * The text-size bar over a page (Ajutor, Cutremure, Despre, Ce e nou), v2.12 (Viorel
 * 2026-11-04): shown by the "Aa" button next to the page's buttons on the right, above the page,
 * on the left of the screen in the same place, size and look as the brightness bar. A finger
 * moving up or down on it sets the size live; it goes away 3 s after the button if unused, 4 s
 * after the finger lifts, or at a touch elsewhere.
 */
final class SizeOverlay extends View {
	interface Listener {
		/** The new size; SAVE when the finger lifts. */
		void sized(float s, boolean save);
	}

	private final Screen screen;
	private final AndroidGfx gfx;
	private Listener listener;
	private float scale = 1, ox, oy, downY, pos0;
	private boolean down;
	private final Runnable hide = new Runnable() {
		public void run() {
			hide();
		}
	};

	SizeOverlay(Context c, Screen screen) {
		super(c);
		this.screen = screen;
		gfx = new AndroidGfx(c.getAssets());
		setVisibility(GONE);
	}

	boolean isOpen() {
		return getVisibility() == VISIBLE;
	}

	/** Shows the bar (on top of everything) for L; hides it after 3 s if unused. */
	void show(Listener l) {
		listener = l;
		screen.sizeLevel = TextSize.pos(TextSize.get(getContext()));
		setVisibility(VISIBLE);
		bringToFront();
		removeCallbacks(hide);
		postDelayed(hide, 3000);
		invalidate();
		Log.i(getContext(), "GEST", "butonul Aa: bara de mărime a textului");
	}

	void hide() {
		removeCallbacks(hide);
		listener = null;
		down = false;
		setVisibility(GONE);
	}

	@Override
	protected void onDraw(Canvas c) {
		int w = getWidth(), h = getHeight();
		if (w == 0 || h == 0) return;
		float[] f = Screen.fit(w, h, screen.tablet);
		scale = f[0];
		ox = f[3];
		oy = f[4];
		c.save();
		c.translate(ox, oy);
		c.scale(scale, scale);
		gfx.c = c;
		screen.drawSizeBarOnly(gfx, f[2]);
		c.restore();
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		float[] a = screen.sizeArea;
		switch (e.getActionMasked()) {
		case MotionEvent.ACTION_DOWN: {
			float x = (e.getX() - ox) / scale, y = (e.getY() - oy) / scale;
			down = a[3] > 0 && x >= a[0] - 30 && x <= a[0] + a[2] + 30 && y >= a[1] - 30 && y <= a[1] + a[3] + 30;
			if (!down) {
				// A touch elsewhere closes the bar and does nothing else.
				hide();
				return true;
			}
			removeCallbacks(hide);
			downY = e.getY();
			pos0 = screen.sizeLevel;
			return true;
		}
		case MotionEvent.ACTION_MOVE:
			if (down) slide(e.getY() - downY, false);
			return true;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (down) {
				slide(e.getY() - downY, true);
				down = false;
				removeCallbacks(hide);
				postDelayed(hide, 4000);
			}
			return true;
		}
		return true;
	}

	/** The bar under the finger, DY pixels from where it went down (as the brightness bar). */
	private void slide(float dy, boolean save) {
		float[] a = screen.sizeArea;
		if (a[3] > 0) screen.sizeLevel = Math.max(0, Math.min(1, pos0 - dy / (a[3] * scale)));
		float s = TextSize.scale(screen.sizeLevel);
		TextSize.set(getContext(), s, save);
		if (listener != null) listener.sized(s, save);
		invalidate();
	}
}
