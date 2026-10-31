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

	HelpPanel(Context c, Typeface reg, Typeface bold) {
		super(c);
		this.reg = reg;
		this.bold = bold;
		dp = c.getResources().getDisplayMetrics().density;
		setVisibility(GONE);
		setClickable(true);
		LinearLayout col = new LinearLayout(c);
		col.setOrientation(LinearLayout.VERTICAL);
		LinearLayout top = new LinearLayout(c);
		top.setGravity(Gravity.CENTER_VERTICAL);
		int pad = (int) (16 * dp);
		top.setPadding(pad, (int) (12 * dp), pad, (int) (12 * dp));
		title = new TextView(c);
		title.setTypeface(bold);
		title.setTextSize(22);
		top.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
		close = new TextView(c);
		close.setTypeface(bold);
		close.setTextSize(17);
		close.setGravity(Gravity.CENTER);
		close.setPadding((int) (18 * dp), (int) (8 * dp), (int) (18 * dp), (int) (8 * dp));
		close.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				hide();
			}
		});
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
		list.removeAllViews();
	}

	/** A section title. */
	void section(String text) {
		TextView t = new TextView(getContext());
		t.setTypeface(bold);
		t.setTextSize(14);
		t.setTextColor(dim());
		t.setText(text.toUpperCase());
		t.setPadding(0, (int) (18 * dp), 0, (int) (6 * dp));
		list.addView(t);
		View line = new View(getContext());
		line.setBackgroundColor(ink());
		list.addView(line, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Math.max(2, (int) (2 * dp))));
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
		n.setTextSize(17);
		n.setTextColor(ink());
		n.setText(name);
		txt.addView(n);
		TextView w = new TextView(c);
		w.setTypeface(reg);
		w.setTextSize(15);
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
		if (onClose != null) onClose.run();
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		return true;
	}
}
