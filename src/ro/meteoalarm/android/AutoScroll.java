package ro.meteoalarm.android;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/**
 * v5.04 (Viorel): a row of buttons wider than the screen moves on by itself like the row of
 * warnings on the main screen: one button every 2 seconds, back to the first after the last; a
 * touch stops it for 20 seconds. Nothing moves when the whole row fits.
 */
final class AutoScroll implements Runnable, View.OnTouchListener {
	private static final long STEP = 2000, PAUSE = 20000;
	private final HorizontalScrollView hs;
	private final LinearLayout row;
	private long touched;
	private int at;

	private AutoScroll(HorizontalScrollView hs, LinearLayout row) {
		this.hs = hs;
		this.row = row;
	}

	static void attach(HorizontalScrollView hs, LinearLayout row) {
		AutoScroll a = new AutoScroll(hs, row);
		hs.setOnTouchListener(a);
		hs.postDelayed(a, STEP);
	}

	public boolean onTouch(View v, MotionEvent e) {
		touched = SystemClock.uptimeMillis();
		return false;
	}

	public void run() {
		if (!hs.isAttachedToWindow()) return;
		hs.postDelayed(this, STEP);
		if (!hs.isShown() || SystemClock.uptimeMillis() - touched < PAUSE) return;
		int n = row.getChildCount(), room = hs.getWidth(), full = row.getWidth();
		if (n < 2 || full <= room) return;
		// The first button whose left edge is past the scroll: go there; past the end, start over.
		int x = hs.getScrollX(), next = -1;
		for (int i = 0; i < n; i++) {
			int l = row.getChildAt(i).getLeft() - row.getPaddingLeft();
			if (l > x + 4) {
				next = l;
				break;
			}
		}
		if (next < 0 || x >= full - room - 4) next = 0;
		hs.smoothScrollTo(Math.min(next, full - room), 0);
	}
}
