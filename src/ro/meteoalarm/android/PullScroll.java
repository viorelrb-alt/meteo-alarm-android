package ro.meteoalarm.android;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

/**
 * v2.106 (Viorel, Motorola): a page over the main screen (the earthquake information) that, pulled
 * down while at its top, hands the finger to the main screen, so pulling down still refreshes.
 */
class PullScroll extends ScrollView {
	private final View under;
	private final float dp;
	private float x0, y0;
	private boolean top, handing;

	PullScroll(Context ctx, View under) {
		super(ctx);
		this.under = under;
		dp = ctx.getResources().getDisplayMetrics().density;
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent e) {
		int act = e.getActionMasked();
		if (act == MotionEvent.ACTION_DOWN) {
			x0 = e.getRawX();
			y0 = e.getRawY();
			top = getScrollY() <= 0;
			handing = false;
		} else if (act == MotionEvent.ACTION_MOVE && top && !handing && under != null) {
			float dx = e.getRawX() - x0, dy = e.getRawY() - y0;
			if (dy > 12 * dp && dy > 2 * Math.abs(dx)) {
				handing = true;
				// The scroll view lets go; the main screen gets the touch from where it began.
				MotionEvent c = MotionEvent.obtain(e);
				c.setAction(MotionEvent.ACTION_CANCEL);
				super.dispatchTouchEvent(c);
				c.recycle();
				send(MotionEvent.ACTION_DOWN, x0, y0, e);
			} else if (dy < -4 * dp) top = false;
		}
		if (handing) {
			if (act != MotionEvent.ACTION_DOWN) send(act, e.getRawX(), e.getRawY(), e);
			if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) handing = false;
			return true;
		}
		return super.dispatchTouchEvent(e);
	}

	private void send(int act, float rx, float ry, MotionEvent like) {
		int[] b = new int[2];
		under.getLocationOnScreen(b);
		MotionEvent m = MotionEvent.obtain(like.getDownTime(), like.getEventTime(), act, rx - b[0], ry - b[1], 0);
		under.dispatchTouchEvent(m);
		m.recycle();
	}
}
