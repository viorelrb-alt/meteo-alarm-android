package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * ☰ › Ajutor: the whole screen, a title with "Închide" on top and, scrolling under it, every
 * button, gesture and menu option with its icon and a short explanation.
 */
final class HelpPanel extends FrameLayout {
	private final float dp;
	private final Typeface reg, bold;
	private final LinearLayout list;
	private final TextView title;
	private final TextView close;
	private final ScrollView scroll;
	private boolean open, dark;
	private Runnable onClose;
	/** The text size (TextSize); the "Aa" button left of "Închide" shows its bar (SizeOverlay). */
	private float ts;
	private final TextView sizeBtn;
	private final LinearLayout top;
	private SizeOverlay sizer;

	HelpPanel(Context c, Typeface reg, Typeface bold) {
		super(c);
		this.reg = reg;
		this.bold = bold;
		dp = c.getResources().getDisplayMetrics().density;
		setVisibility(GONE);
		setClickable(true);
		LinearLayout col = new LinearLayout(c);
		col.setOrientation(LinearLayout.VERTICAL);
		ts = TextSize.get(c);
		top = new LinearLayout(c);
		top.setGravity(Gravity.CENTER_VERTICAL);
		int pad = (int) (16 * dp);
		top.setPadding(pad, (int) (12 * dp), pad, (int) (12 * dp));
		// The title on one line (Viorel v2.21): it grows with the text size only as far as the
		// buttons on its right, then stays at the largest size that fits.
		title = new TextView(c) {
			@Override
			protected void onMeasure(int wSpec, int hSpec) {
				int avail = MeasureSpec.getSize(wSpec) - getPaddingLeft() - getPaddingRight();
				float want = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 22 * ts, getResources().getDisplayMetrics());
				if (avail > 0 && MeasureSpec.getMode(wSpec) != MeasureSpec.UNSPECIFIED) {
					android.graphics.Paint pt = new android.graphics.Paint(getPaint());
					String t = getText().toString();
					float sz = want;
					pt.setTextSize(sz);
					while (sz > 8 && pt.measureText(t) > avail) {
						sz *= 0.96f;
						pt.setTextSize(sz);
					}
					if (Math.abs(getTextSize() - sz) > 0.5f) setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, sz);
				}
				super.onMeasure(wSpec, hSpec);
			}
		};
		title.setSingleLine(true);
		title.setTypeface(bold);
		TextSize.size(title, 22, ts);
		top.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
		close = new TextView(c);
		close.setTypeface(bold);
		TextSize.size(close, 17, 1);
		close.setGravity(Gravity.CENTER);
		close.setPadding((int) (18 * dp), (int) (8 * dp), (int) (18 * dp), (int) (8 * dp));
		close.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				hide();
			}
		});
		sizeBtn = new TextView(c);
		sizeBtn.setTypeface(bold);
		sizeBtn.setText("Aa");
		TextSize.size(sizeBtn, 17, 1);
		sizeBtn.setGravity(Gravity.CENTER);
		sizeBtn.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (8 * dp));
		sizeBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (sizer == null) return;
				// Always shows it (a second tap no longer hides it: A70 v2.20).
				sizer.show(new SizeOverlay.Listener() {
					public void sized(float s, boolean save) {
						ts = s;
						TextSize.apply(list, ts);
						TextSize.apply(title, ts);
					}
				});
			}
		});
		LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		sbp.rightMargin = (int) (10 * dp);
		top.addView(sizeBtn, sbp);
		top.addView(close);
		col.addView(top);
		scroll = new ScrollView(c);
		list = new LinearLayout(c);
		list.setOrientation(LinearLayout.VERTICAL);
		list.setPadding(pad, 0, pad, (int) (24 * dp));
		scroll.addView(list);
		col.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
		addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
	}

	/** The text-size bar over the page (made by MainActivity, on top of every window). */
	void setSizer(SizeOverlay o) {
		sizer = o;
	}

	boolean isOpen() {
		return open;
	}

	void setOnClose(Runnable r) {
		onClose = r;
	}

	private int ink() {
		return dark ? 0xFFFAF9F5 : 0xFF000000;
	}

	private int dim() {
		return dark ? 0xFFA6A39A : 0xFF555555;
	}

	/** Starts a new help text: TITLE, the close button's label, the theme. */
	void begin(String head, String closeLabel, boolean d) {
		dark = d;
		setBackgroundColor(dark ? 0xFF262624 : 0xFFFFFFFF);
		title.setText(head);
		title.setTextColor(ink());
		close.setText(closeLabel);
		close.setTextColor(ink());
		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(10 * dp);
		bg.setStroke((int) (2 * dp), ink());
		close.setBackground(bg);
		sizeBtn.setTextColor(ink());
		GradientDrawable sb = new GradientDrawable();
		sb.setCornerRadius(10 * dp);
		sb.setStroke((int) (2 * dp), ink());
		sizeBtn.setBackground(sb);
		list.removeAllViews();
		ts = TextSize.get(getContext());
		TextSize.apply(title, ts);
	}

	/** A section title. */
	void section(String text) {
		TextView t = new TextView(getContext());
		t.setTypeface(bold);
		TextSize.size(t, 14, ts);
		t.setTextColor(dim());
		t.setText(text.toUpperCase());
		t.setPadding(0, (int) (18 * dp), 0, (int) (6 * dp));
		list.addView(t);
		View line = new View(getContext());
		line.setBackgroundColor(ink());
		list.addView(line, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Math.max(2, (int) (2 * dp))));
	}

	/** A plain paragraph (a notice: downloading, nothing found…). */
	void text(String what) {
		text(what, 15);
	}

	/** v3.52 (Viorel): where the data come from, 0.5 smaller than the page's texts (15). */
	void source(String what) {
		text(what, 14.5f);
	}

	private void text(String what, float sp) {
		TextView w = new TextView(getContext());
		w.setTypeface(reg);
		TextSize.size(w, sp, ts);
		w.setTextColor(dim());
		w.setLineSpacing(0, 1.1f);
		w.setText(what);
		w.setPadding(0, (int) (12 * dp), 0, (int) (12 * dp));
		list.addView(w);
	}

	/** One entry: the icon, its name and what it does. */
	void entry(Bitmap icon, String name, String what) {
		Context c = getContext();
		LinearLayout row = new LinearLayout(c);
		row.setPadding(0, (int) (12 * dp), 0, (int) (12 * dp));
		ImageView iv = new ImageView(c);
		iv.setImageBitmap(icon);
		int s = (int) (40 * dp);
		LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(s, s);
		ip.rightMargin = (int) (14 * dp);
		row.addView(iv, ip);
		LinearLayout txt = new LinearLayout(c);
		txt.setOrientation(LinearLayout.VERTICAL);
		TextView n = new TextView(c);
		n.setTypeface(bold);
		TextSize.size(n, 17, ts);
		n.setTextColor(ink());
		n.setText(name);
		txt.addView(n);
		TextView w = new TextView(c);
		w.setTypeface(reg);
		TextSize.size(w, 15, ts);
		w.setTextColor(dim());
		w.setLineSpacing(0, 1.1f);
		w.setText(what);
		txt.addView(w);
		row.addView(txt, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
		list.addView(row);
	}

	void show() {
		scroll.scrollTo(0, 0);
		open = true;
		setVisibility(VISIBLE);
		bringToFront();
	}

	void hide() {
		if (!open) return;
		open = false;
		setVisibility(GONE);
		if (sizer != null) sizer.hide();
		if (onClose != null) onClose.run();
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		return true;
	}
}
