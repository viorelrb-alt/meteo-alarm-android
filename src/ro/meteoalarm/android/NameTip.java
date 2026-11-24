package ro.meteoalarm.android;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import ro.meteoalarm.android.core.Theme;

/**
 * v2.152 (Viorel): a round button tapped that stays on the screen shows its name in a small
 * bubble for about 1.5 s, then the bubble fades (at once with the phone's animations off).
 * Only the name; TalkBack reads it once.
 */
final class NameTip {
	private NameTip() {
	}

	private static TextView shown;

	/** The name of the button V (a View on A's screen). */
	static void show(Activity a, View v, String name, boolean dark) {
		if (a == null || v == null || !v.isShown()) return;
		int[] at = new int[2];
		v.getLocationInWindow(at);
		show(a, at[0], at[1], at[0] + v.getWidth(), at[1] + v.getHeight(), name, dark);
	}

	/** The name of the button at L, T, R, B (pixels in A's window). */
	static void show(Activity a, float l, float t, float r, float b, String name, boolean dark) {
		if (a == null || a.isFinishing() || name == null || name.length() == 0) return;
		View root = a.findViewById(android.R.id.content);
		if (!(root instanceof FrameLayout)) return;
		final FrameLayout fr = (FrameLayout) root;
		hide();
		float dp = a.getResources().getDisplayMetrics().density;
		final TextView tv = new TextView(a);
		tv.setText(name);
		tv.setTypeface(Fonts.bold(a));
		tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		// The bubble in the theme's ink, the text in the page's colour (contrast as ink on the page).
		tv.setTextColor(Theme.page(dark));
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(Theme.ink(dark));
		bg.setCornerRadius(12 * dp);
		tv.setBackground(bg);
		tv.setPadding(Math.round(12 * dp), Math.round(6 * dp), Math.round(12 * dp), Math.round(6 * dp));
		tv.setGravity(Gravity.CENTER);
		tv.setSingleLine(true);
		tv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		tv.setElevation(6 * dp);
		int[] fo = new int[2];
		fr.getLocationInWindow(fo);
		tv.measure(View.MeasureSpec.makeMeasureSpec(fr.getWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
		int w = tv.getMeasuredWidth(), h = tv.getMeasuredHeight(), gap = Math.round(8 * dp);
		float cx = (l + r) / 2 - fo[0];
		// Under the button in the upper half of the screen, over it in the lower half.
		boolean below = (t + b) / 2 - fo[1] < fr.getHeight() / 2f;
		int x = Math.round(Math.max(gap, Math.min(fr.getWidth() - gap - w, cx - w / 2f)));
		int y = Math.round(below ? b - fo[1] + gap : t - fo[1] - gap - h);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.gravity = Gravity.TOP | Gravity.LEFT;
		lp.leftMargin = x;
		lp.topMargin = Math.max(0, y);
		fr.addView(tv, lp);
		shown = tv;
		fr.announceForAccessibility(name);
		final boolean still = Motion.off(a);
		tv.postDelayed(new Runnable() {
			public void run() {
				if (still) remove(tv);
				else tv.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
					public void run() {
						remove(tv);
					}
				}).start();
			}
		}, 1500);
	}

	/** Takes the bubble away at once (another one comes, or the screen changes). */
	static void hide() {
		if (shown != null) remove(shown);
	}

	private static void remove(TextView tv) {
		if (tv.getParent() instanceof ViewGroup) ((ViewGroup) tv.getParent()).removeView(tv);
		if (shown == tv) shown = null;
	}
}
