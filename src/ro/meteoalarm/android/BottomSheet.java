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
	private final Button primary, secondary, left;
	private final float dp;
	private boolean open, dark, cancelable = true;
	private Runnable onPrimary, onLeft, onClose;
	private float downY;
	/** The "Aa" button next to the right ones (Despre, Ce e nou): it shows the text-size bar (SizeOverlay). */
	private final Button size;
	private SizeOverlay sizer;
	private boolean sized;

	BottomSheet(Context c) {
		super(c);
		dp = c.getResources().getDisplayMetrics().density;
		setVisibility(GONE);
		scrim = new View(c);
		scrim.setBackgroundColor(0x66000000);
		scrim.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (cancelable) cancel();
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
		// v3.61 (Viorel): with three buttons (Settings · Don't show again · Continue) the row was wider
		// than the screen and the left button went off the left edge: the labels now get smaller
		// (and the buttons narrower) until the whole row fits.
		LinearLayout row = new LinearLayout(c) {
			@Override
			protected void onMeasure(int ws, int hs) {
				fitRow(this, MeasureSpec.getSize(ws));
				super.onMeasure(ws, hs);
			}
		};
		row.setGravity(Gravity.END);
		left = button(c);
		left.setVisibility(GONE);
		row.addView(left);
		// Free room between the left button and the ones on the right.
		row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
		size = button(c);
		size.setText("Aa");
		size.setVisibility(GONE);
		// v3.01: narrow, so the left button ("Ce e nou") is not pushed off the edge (Viorel).
		size.setMinWidth(0);
		size.setMinimumWidth(0);
		size.setPadding((int) (10 * dp), 0, (int) (10 * dp), 0);
		LinearLayout.LayoutParams zp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		zp.leftMargin = (int) (6 * dp);
		zp.rightMargin = (int) (8 * dp);
		row.addView(size, zp);
		secondary = button(c);
		primary = button(c);
		row.addView(secondary);
		LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		pp.leftMargin = (int) (12 * dp);
		row.addView(primary, pp);
		LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		rp.topMargin = (int) (12 * dp);
		rp.bottomMargin = 0;
		text = new TextView(c);
		text.setTextSize(16);
		text.setLineSpacing(0, 1.15f);
		// The text scrolls; at most about two thirds of the screen high.
		scroll = new ScrollView(c) {
			@Override
			protected void onMeasure(int wSpec, int hSpec) {
				int fixed = getLayoutParams().height;
				if (fixed > 0) {
					super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(fixed, MeasureSpec.EXACTLY));
					return;
				}
				int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.62f);
				super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
			}
		};
		scroll.addView(text);
		LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		sl.topMargin = (int) (12 * dp);
		sheet.addView(scroll, sl);
		// v3.00 (Viorel): the buttons under the text.
		sheet.addView(row, rp);
		size.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (sizer == null) return;
				// Always shows it (a second tap no longer hides it: A70 v2.20).
				sizer.show(new SizeOverlay.Listener() {
					public void sized(float s, boolean save) {
						if (sized) text.setTextSize(16 * s);
					}
				});
			}
		});
		secondary.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				cancel();
			}
		});
		primary.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (onPrimary != null) onPrimary.run();
			}
		});
		left.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (onLeft != null) onLeft.run();
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

	/** The buttons of ROW in one size, 16 sp or smaller, so that together they fit WIDTH pixels. */
	private void fitRow(LinearLayout row, int width) {
		if (width <= 0) return;
		float sd = getResources().getDisplayMetrics().scaledDensity;
		for (float sp = 16; ; sp -= 0.5f) {
			int pad = (int) ((sp >= 14 ? 18 : 10) * dp);
			float need = 0;
			android.graphics.Paint p = new android.graphics.Paint(primary.getPaint());
			p.setTextSize(sp * sd);
			for (int k = 0; k < row.getChildCount(); k++) {
				View v = row.getChildAt(k);
				if (v.getVisibility() == GONE || !(v instanceof Button)) continue;
				LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
				Button b = (Button) v;
				need += lp.leftMargin + lp.rightMargin + (b == size ? b.getPaddingLeft() + b.getPaddingRight() + p.measureText("Aa")
						: Math.max(p.measureText(b.getText().toString()) + 2 * pad, 0));
			}
			if (need <= width || sp <= 10) {
				for (int k = 0; k < row.getChildCount(); k++) {
					View v = row.getChildAt(k);
					if (!(v instanceof Button)) continue;
					Button b = (Button) v;
					if (Math.abs(b.getTextSize() - sp * sd) > 0.5f) b.setTextSize(sp);
					b.setMinWidth(0);
					b.setMinimumWidth(0);
					b.setSingleLine(true);
					if (b != size && b.getPaddingLeft() != pad) b.setPadding(pad, 0, pad, 0);
				}
				return;
			}
		}
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
		int bg = ro.meteoalarm.android.core.Theme.surface(dark), ink = ro.meteoalarm.android.core.Theme.ink(dark), line = ro.meteoalarm.android.core.Theme.pal == 0 ? (dark ? 0xFF4A4944 : 0xFF000000) : ro.meteoalarm.android.core.Theme.grid(dark);
		int accent = ro.meteoalarm.android.core.Theme.accent(dark);
		sheet.setBackground(shape(bg, line, 18 * dp, dp, true));
		sheet.findViewWithTag("handle").setBackground(shape(dark ? 0xFF6B6A65 : 0xFFAAAAAA, 0, 2 * dp, dp, false));
		title.setTextColor(ink);
		text.setTextColor(dark ? 0xFFC2C0B6 : 0xFF333333);
		scroll.setBackgroundColor(0);
		primary.setBackground(shape(accent, 0, 10 * dp, dp, false));
		primary.setTextColor(ro.meteoalarm.android.core.Theme.page(dark));
		secondary.setBackground(shape(bg, line, 10 * dp, dp, false));
		secondary.setTextColor(ink);
		left.setBackground(shape(bg, line, 10 * dp, dp, false));
		left.setTextColor(ink);
		size.setBackground(shape(bg, line, 10 * dp, dp, false));
		size.setTextColor(ink);
	}

	/** The text-size bar over every window (made by MainActivity). */
	void setSizer(SizeOverlay o) {
		sizer = o;
	}

	/**
	 * The "Aa" button (Viorel 2026-11-04, v2.12) next to the right buttons, which shows the
	 * text-size bar on the left of the screen, or none; the text in the saved size, or the usual one.
	 */
	void sizeBar(boolean on) {
		sized = on;
		size.setVisibility(on ? VISIBLE : GONE);
		text.setTextSize(16 * (on ? TextSize.get(getContext()) : 1));
		if (!on && sizer != null) sizer.hide();
	}

	boolean isOpen() {
		return open;
	}

	private int taps;
	private long tapAt;

	/** RUN after 7 taps on the title within a few seconds (null: none; the title then swipes like the rest). */
	void titleTaps(final Runnable run) {
		taps = 0;
		if (run == null) {
			title.setOnClickListener(null);
			title.setClickable(false);
			return;
		}
		title.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				long now = System.currentTimeMillis();
				taps = now - tapAt < 1500 ? taps + 1 : 1;
				tapAt = now;
				if (taps >= 7) {
					taps = 0;
					run.run();
				}
			}
		});
	}

	boolean isCancelable() {
		return cancelable;
	}

	/**
	 * Opens (sliding up) or updates the window. PRIMARY null: only the close button. CLOSE null:
	 * only the primary button, and the window closes only through it (no tap above, swipe or Back).
	 */
	void show(String t, String msg, String primaryLabel, Runnable action, String closeLabel) {
		show(t, msg, primaryLabel, action, closeLabel, null);
	}

	/**
	 * As above; CLOSED runs when the window is closed without the primary button (the close
	 * button, a tap above, a swipe or Back), instead of closing it: it usually shows another text.
	 */
	void show(String t, String msg, String primaryLabel, Runnable action, String closeLabel, Runnable closed) {
		onClose = closed;
		title.setText(t);
		titleTaps(null);
		text.setText(msg);
		scroll.scrollTo(0, 0);
		scroll.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
		scroll.requestLayout();
		left.setVisibility(GONE);
		onLeft = null;
		sizeBar(false);
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

	/** A button on the left of the row (after show, which removes it). */
	void setLeft(String label, Runnable action) {
		left.setText(label);
		left.setVisibility(VISIBLE);
		onLeft = action;
	}

	/** Other text and title in the same window: its size stays, the new text scrolls from the top. */
	void replace(String t, String msg) {
		int h = scroll.getHeight();
		if (h > 0) scroll.getLayoutParams().height = h;
		title.setText(t);
		text.setText(msg);
		scroll.scrollTo(0, 0);
		scroll.requestLayout();
	}

	/** Closed by the user (not through the primary button): CLOSED if set, else it slides down. */
	void cancel() {
		Runnable r = onClose;
		onClose = null;
		if (r != null) {
			sheet.animate().translationY(0).setDuration(120).start();
			r.run();
		} else hide();
	}

	/** Closes, sliding down. */
	void hide() {
		onClose = null;
		if (!open) return;
		open = false;
		if (sizer != null) sizer.hide();
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
			if (sheet.getTranslationY() > sheet.getHeight() / 4f) cancel();
			else sheet.animate().translationY(0).setDuration(150).start();
			return true;
		}
		return false;
	}
}
