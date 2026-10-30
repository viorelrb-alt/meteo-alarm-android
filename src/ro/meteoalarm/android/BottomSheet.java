package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * A small window at the bottom of the screen, over the weather: it slides up to open and down to
 * close (with "Renunță", a tap above it, or a downward swipe on its top). The buttons sit under
 * the title and the text scrolls under them, so they stay in reach however long the text is.
 * Used for the updates, the first-start message and About.
 */
final class BottomSheet extends FrameLayout {
	private final View scrim;
	private final LinearLayout sheet;
	private final TextView title, text;
	private final ScrollView scroll;
	private final Button primary, secondary;
	private final float dp;
	private boolean open, dark, cancelable = true;
	private Runnable onPrimary;
	private float downY;

	BottomSheet(Context c) {
		super(c);
		dp = c.getResources().getDisplayMetrics().density;
		setVisibility(GONE);
		scrim = new View(c);
		scrim.setBackgroundColor(0x66000000);
		scrim.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (cancelable) hide();
			}
		});
		addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		sheet = new LinearLayout(c);
		sheet.setOrientation(LinearLayout.VERTICAL);
		int pad = (int) (22 * dp);
		sheet.setPadding(pad, (int) (10 * dp), pad, (int) (18 * dp));
		sheet.setClickable(true);
		// Handle: a short bar on top, the usual sign that the window can be swiped.
		View handle = new View(c);
		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams((int) (40 * dp), (int) (4 * dp));
		hp.gravity = Gravity.CENTER_HORIZONTAL;
		hp.bottomMargin = (int) (14 * dp);
		handle.setTag("handle");
		sheet.addView(handle, hp);
		title = new TextView(c);
		title.setTextSize(20);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		sheet.addView(title);
		LinearLayout row = new LinearLayout(c);
		row.setGravity(Gravity.END);
		secondary = button(c);
		primary = button(c);
		row.addView(secondary);
		LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		pp.leftMargin = (int) (12 * dp);
		row.addView(primary, pp);
		LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		rp.topMargin = (int) (12 * dp);
		rp.bottomMargin = (int) (12 * dp);
		sheet.addView(row, rp);
		text = new TextView(c);
		text.setTextSize(16);
		text.setLineSpacing(0, 1.15f);
		// The text scrolls; at most about two thirds of the screen high.
		scroll = new ScrollView(c) {
			@Override
			protected void onMeasure(int wSpec, int hSpec) {
				int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.62f);
				super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
			}
		};
		scroll.addView(text);
		sheet.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		secondary.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				hide();
			}
		});
		primary.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (onPrimary != null) onPrimary.run();
			}
		});
		// Swiping down on the top part (handle, title, buttons' row) closes; the text scrolls.
		sheet.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent e) {
				return swipe(e);
			}
		});
		LayoutParams sp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
		addView(sheet, sp);
		setDark(false);
	}

	private Button button(Context c) {
		Button b = new Button(c);
		b.setAllCaps(false);
		b.setTextSize(16);
		b.setTypeface(Typeface.DEFAULT_BOLD);
		b.setMinHeight((int) (44 * dp));
		b.setPadding((int) (18 * dp), 0, (int) (18 * dp), 0);
		return b;
	}

	private static GradientDrawable shape(int fill, int stroke, float r, float dp, boolean topOnly) {
		GradientDrawable d = new GradientDrawable();
		d.setColor(fill);
		if (stroke != 0) d.setStroke((int) (2 * dp), stroke);
		if (topOnly) d.setCornerRadii(new float[] { r, r, r, r, 0, 0, 0, 0 });
		else d.setCornerRadius(r);
		return d;
	}

	/** Kindle black and white, or the Claude dark colours. */
	void setDark(boolean dark) {
		this.dark = dark;
		int bg = dark ? 0xFF30302E : 0xFFFFFFFF, ink = dark ? 0xFFFAF9F5 : 0xFF000000, line = dark ? 0xFF4A4944 : 0xFF000000;
		int accent = dark ? 0xFFD97757 : 0xFF000000;
		sheet.setBackground(shape(bg, line, 18 * dp, dp, true));
		sheet.findViewWithTag("handle").setBackground(shape(dark ? 0xFF6B6A65 : 0xFFAAAAAA, 0, 2 * dp, dp, false));
		title.setTextColor(ink);
		text.setTextColor(dark ? 0xFFC2C0B6 : 0xFF333333);
		scroll.setBackgroundColor(0);
		primary.setBackground(shape(accent, 0, 10 * dp, dp, false));
		primary.setTextColor(dark ? 0xFF262624 : 0xFFFFFFFF);
		secondary.setBackground(shape(bg, line, 10 * dp, dp, false));
		secondary.setTextColor(ink);
	}

	boolean isOpen() {
		return open;
	}

	boolean isCancelable() {
		return cancelable;
	}

	/**
	 * Opens (sliding up) or updates the window. PRIMARY null: only the close button. CLOSE null:
	 * only the primary button, and the window closes only through it (no tap above, swipe or Back).
	 */
	void show(String t, String msg, String primaryLabel, Runnable action, String closeLabel) {
		title.setText(t);
		text.setText(msg);
		scroll.scrollTo(0, 0);
		cancelable = closeLabel != null;
		secondary.setVisibility(closeLabel == null ? GONE : VISIBLE);
		if (closeLabel != null) secondary.setText(closeLabel);
		onPrimary = action;
		primary.setVisibility(primaryLabel == null ? GONE : VISIBLE);
		if (primaryLabel != null) primary.setText(primaryLabel);
		if (open) return;
		open = true;
		setVisibility(VISIBLE);
		scrim.setAlpha(0);
		scrim.animate().alpha(1).setDuration(220).start();
		sheet.post(new Runnable() {
			public void run() {
				sheet.setTranslationY(sheet.getHeight());
				sheet.animate().translationY(0).setDuration(260).start();
			}
		});
	}

	void setMessage(String msg) {
		text.setText(msg);
	}

	/** Closes, sliding down. */
	void hide() {
		if (!open) return;
		open = false;
		scrim.animate().alpha(0).setDuration(200).start();
		sheet.animate().translationY(sheet.getHeight()).setDuration(220).withEndAction(new Runnable() {
			public void run() {
				setVisibility(GONE);
			}
		}).start();
	}

	/** The window follows a downward swipe; past a quarter of its height it closes, else it springs back. */
	private boolean swipe(MotionEvent e) {
		switch (e.getActionMasked()) {
		case MotionEvent.ACTION_DOWN:
			downY = e.getRawY();
			return cancelable;
		case MotionEvent.ACTION_MOVE:
			sheet.setTranslationY(Math.max(0, e.getRawY() - downY));
			return true;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (sheet.getTranslationY() > sheet.getHeight() / 4f) hide();
			else sheet.animate().translationY(0).setDuration(150).start();
			return true;
		}
		return false;
	}
}
