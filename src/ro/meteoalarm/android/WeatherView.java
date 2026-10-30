package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import ro.meteoalarm.android.core.Screen;

/** Draws the Screen scaled to the phone: full width, the extra height going to the current weather. */
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
	private float scale = 1, ox, oy;

	WeatherView(Context ctx, Taps taps) {
		super(ctx);
		this.taps = taps;
		gfx = new AndroidGfx(ctx.getAssets());
		setBackgroundColor(0xFFFFFFFF);
	}

	@Override
	protected void onDraw(Canvas c) {
		int w = getWidth(), h = getHeight();
		if (w == 0 || h == 0) return;
		float cw, ch;
		if (w > h) {
			// Landscape: 758 units high, at least BASE_LW wide; the extra width goes to the weather column.
			scale = Math.min(h / Screen.LH, w / Screen.BASE_LW);
			cw = w / scale;
			ch = Screen.LH;
			ox = 0;
			oy = (h - ch * scale) / 2;
		} else {
			scale = w / Screen.W;
			ch = h / scale;
			if (ch < Screen.BASE_H) {
				scale = h / Screen.BASE_H;
				ch = Screen.BASE_H;
			}
			cw = Screen.W;
			ox = (w - cw * scale) / 2;
			oy = 0;
		}
		c.drawColor(screen.dark ? 0xFF262624 : 0xFFFFFFFF);
		c.save();
		c.translate(ox, oy);
		c.scale(scale, scale);
		gfx.c = c;
		screen.draw(gfx, cw, ch);
		c.restore();
	}

	private static boolean in(float[] b, float x, float y) {
		return x >= b[0] - 12 && x <= b[0] + b[2] + 12 && y >= b[1] - 12 && y <= b[1] + b[3] + 12;
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (e.getAction() != MotionEvent.ACTION_UP) return true;
		float x = (e.getX() - ox) / scale, y = (e.getY() - oy) / scale;
		if (in(screen.btnMenu, x, y)) taps.onMenu();
		else if (in(screen.btnRefresh, x, y)) taps.onRefresh();
		else if (in(screen.btnLocate, x, y)) taps.onLocate();
		else if (in(screen.btnCity, x, y) || in(screen.cityArea, x, y)) taps.onCity();
		return true;
	}
}
