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
final class HelpPanel extends FrameLayout implements Glass.Local {
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
		// v5.24 (Viorel): the same bar on top as About; swiping it (or the title) down closes Help.
		handle = new View(c);
		LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams((int) (40 * dp), (int) (4 * dp));
		hp.gravity = Gravity.CENTER_HORIZONTAL;
		hp.topMargin = (int) (10 * dp);
		col.addView(handle, hp);
		top.setPadding(pad, (int) (6 * dp), pad, (int) (12 * dp));
		col.addView(top);
		OnTouchListener drag = new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent e) {
				return swipe(e);
			}
		};
		top.setOnTouchListener(drag);
		handle.setOnTouchListener(drag);
		// v5.00 (Viorel, mockup 5-ajutor): the sections as tabs under the title; a tap scrolls there.
		tabsScroll = new android.widget.HorizontalScrollView(c);
		tabsScroll.setHorizontalScrollBarEnabled(false);
		tabs = new LinearLayout(c);
		tabs.setPadding(pad, 0, pad, (int) (8 * dp));
		tabsScroll.addView(tabs);
		col.addView(tabsScroll);
		AutoScroll.attach(tabsScroll, tabs); // v5.04: moves on by itself like the warnings
		scroll = new ScrollView(c);
		list = new LinearLayout(c);
		list.setOrientation(LinearLayout.VERTICAL);
		list.setPadding(pad, 0, pad, (int) (24 * dp));
		scroll.addView(list);
		col.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
		panel = col;
		// v5.00: a glass window over the blurred screen, a small margin around it.
		LayoutParams cl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		cl.leftMargin = cl.rightMargin = cl.topMargin = cl.bottomMargin = (int) (14 * dp);
		addView(col, cl);
	}

	private final LinearLayout panel, tabs;
	private final android.widget.HorizontalScrollView tabsScroll;
	private final java.util.List<View> sections = new java.util.ArrayList<View>();
	/** v2.047 (Viorel): the 2-row titles of the page, all in the size of the smallest. */
	private final java.util.List<FitText> twos = new java.util.ArrayList<FitText>();
	private final Runnable sameSize = new Runnable() {
		public void run() {
			float m = 0;
			for (FitText f : twos) {
				f.cap = 0;
				float z = f.getWidth() > 0 ? f.sizeFor(f.getWidth() - f.getCompoundPaddingLeft() - f.getCompoundPaddingRight()) : 0;
				if (z > 0 && (m == 0 || z < m)) m = z;
			}
			if (m > 0) for (FitText f : twos) {
				f.cap = m;
				f.requestLayout();
			}
		}
	};

	private void tab(final String name, final View target) {
		TextView t = new TextView(getContext());
		t.setTypeface(bold);
		TextSize.size(t, 15, 1);
		t.setText(name);
		t.setSingleLine(true);
		t.setTextColor(ink());
		t.setPadding((int) (16 * dp), (int) (8 * dp), (int) (16 * dp), (int) (8 * dp));
		t.setBackground(Glass.pill(Glass.disc(dark), 0, 20 * dp, dp));
		t.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				for (int i = 0; i < tabs.getChildCount(); i++) {
					TextView o = (TextView) tabs.getChildAt(i);
					boolean on = o == v;
					o.setTextColor(on ? ro.meteoalarm.android.core.Theme.onPastel(dark) : ink());
					o.setBackground(Glass.pill(on ? ro.meteoalarm.android.core.Theme.pastel(dark) : Glass.disc(dark), 0, 20 * dp, dp));
				}
				scroll.smoothScrollTo(0, Math.max(0, target.getTop() - (int) (4 * dp)));
				// v2.190 (Viorel): on 2 columns the section found blinks its outline twice.
				if (twoCols) pulse(target);
			}
		});
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.rightMargin = (int) (8 * dp);
		tabs.addView(t, lp);
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
		return ro.meteoalarm.android.core.Theme.ink(dark);
	}

	private int dim() {
		return ro.meteoalarm.android.core.Theme.dim(dark);
	}

	/** Starts a new help text: TITLE, the close button's label, the theme. */
	void begin(String head, String closeLabel, boolean d) {
		dark = d;
		setBackgroundColor(0);
		// v5.24 (Viorel): the window itself is frosted glass; the screen behind it stays sharp.
		panel.setBackground(Glass.frostBg(panel, 30 * dp, dark, dp, false));
		GradientDrawable hb = new GradientDrawable();
		hb.setColor(dark ? 0xFF6B6A65 : 0xFFAAAAAA);
		hb.setCornerRadius(2 * dp);
		handle.setBackground(hb);
		title.setText(head);
		title.setTextColor(ink());
		// v5.00 (Viorel): X in a round button instead of "Închide" (its name stays for TalkBack).
		close.setText("");
		close.setContentDescription(closeLabel);
		close.setBackground(Glass.closeIcon(dark, dp));
		close.setLayoutParams(new LinearLayout.LayoutParams((int) (42 * dp), (int) (42 * dp)));
		close.setPadding(0, 0, 0, 0);
		sizeBtn.setTextColor(ink());
		sizeBtn.setBackground(Glass.pill(Glass.disc(dark), 0, 21 * dp, dp));
		sizeBtn.setMinHeight((int) (42 * dp));
		list.removeAllViews();
		tabs.removeAllViews();
		sections.clear();
		box = null;
		pair = null;
		twoCols = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
		twos.clear();
		tabsScroll.setVisibility(GONE);
		ts = TextSize.get(getContext());
		TextSize.apply(title, ts);
	}

	/** A section title. */
	void section(String text) {
		section(text, false);
	}

	/**
	 * v2.041 (Viorel): TWO = the title on 2 rows (split at its " · ", or at its last ", " when it is
	 * the country list), centred, smaller when a row is too long.
	 */
	void section(String text, boolean two) {
		TextView t = two ? new FitText(getContext(), 2, 0.3f) : new TextView(getContext());
		t.setTypeface(bold);
		// v2.042 (Viorel): the 2-row titles as large as the width allows, whatever the Aa size.
		if (two) t.setTextSize(26);
		else TextSize.size(t, 14, ts);
		t.setTextColor(ro.meteoalarm.android.core.Theme.accent(dark));
		t.setLetterSpacing(0.1f);
		t.setText(two ? text.toUpperCase().replaceFirst(" · ", "\n") : text.toUpperCase());
		if (two) t.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
		t.setPadding((int) (4 * dp), (int) (18 * dp), two ? (int) (4 * dp) : 0, (int) (8 * dp));
		// v2.190: each section in its own box (the tabs scroll to it; on 2 columns it blinks).
		box = new LinearLayout(getContext());
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding((int) (4 * dp), 0, (int) (4 * dp), (int) (4 * dp));
		pair = null;
		list.addView(box);
		box.addView(t);
		sections.add(t);
		if (two) {
			twos.add((FitText) t);
			list.removeCallbacks(sameSize);
			list.post(sameSize);
		}
		tab(text, box);
		tabsScroll.setVisibility(sections.size() >= 3 ? VISIBLE : GONE);
	}

	/** v2.108 (Viorel): a row with a round ↻ button and WHAT (when the list was last updated). */
	void refreshRow(String what, final Runnable tap) {
		refreshRow(what, null, 0, tap);
	}

	/**
	 * v2.120 (Viorel): the ↻ button is drawn (centred, not a text glyph). STATE 1 = updating: the
	 * arrow turns and an arc runs round the circle; 2 = just done: a green tick for 1 s, then ↻;
	 * 3 = failed: a red ! for 2 s. SUB = a second, smaller line (the saved list's time).
	 */
	void refreshRow(String what, String sub, int state, final Runnable tap) {
		LinearLayout r = new LinearLayout(getContext());
		r.setOrientation(LinearLayout.HORIZONTAL);
		r.setGravity(android.view.Gravity.CENTER_VERTICAL);
		r.setPadding(0, (int) (10 * dp), 0, 0);
		Spin b = new Spin(getContext(), dark, dp, state);
		b.setBackground(Glass.pill(Glass.disc(dark), Glass.edge(dark), 21 * dp, dp));
		b.setContentDescription(ro.meteoalarm.android.core.Tr.t("Refresh", "Actualizează"));
		r.addView(b, new LinearLayout.LayoutParams((int) (42 * dp), (int) (42 * dp)));
		LinearLayout col = new LinearLayout(getContext());
		col.setOrientation(LinearLayout.VERTICAL);
		TextView w = new TextView(getContext());
		w.setTypeface(reg);
		TextSize.size(w, 14, ts);
		w.setTextColor(dim());
		w.setText(what);
		col.addView(w);
		if (sub != null && sub.length() > 0) {
			TextView t = new TextView(getContext());
			t.setTypeface(reg);
			TextSize.size(t, 11, ts);
			t.setTextColor(dim());
			t.setAlpha(0.8f);
			t.setText(sub);
			col.addView(t);
		}
		LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		wl.leftMargin = (int) (12 * dp);
		r.addView(col, wl);
		r.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (tap != null) tap.run();
			}
		});
		b.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (tap != null) tap.run();
			}
		});
		pair = null;
		into().addView(r);
	}

	/** v2.120: the drawn refresh mark of refreshRow, with its animation. */
	static class Spin extends View {
		final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
		final boolean dark;
		final float dp;
		int state;
		final long t0 = android.os.SystemClock.uptimeMillis();

		Spin(Context c, boolean dark, float dp, int state) {
			super(c);
			this.dark = dark;
			this.dp = dp;
			this.state = state;
			if (state == 2 || state == 3) postDelayed(new Runnable() {
				public void run() {
					Spin.this.state = 0;
					invalidate();
				}
			}, state == 2 ? 1000 : 2000);
		}

		@Override
		protected void onDraw(android.graphics.Canvas c) {
			float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, s = Math.min(w, h);
			int acc = ro.meteoalarm.android.core.Theme.accent(dark);
			p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
			p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
			if (state == 2) {
				p.setStyle(android.graphics.Paint.Style.STROKE);
				p.setStrokeWidth(s * 0.1f);
				p.setColor(0xFF4FBF7F);
				android.graphics.Path k = new android.graphics.Path();
				k.moveTo(cx - s * 0.2f, cy + s * 0.01f);
				k.lineTo(cx - s * 0.05f, cy + s * 0.16f);
				k.lineTo(cx + s * 0.22f, cy - s * 0.14f);
				c.drawPath(k, p);
				return;
			}
			if (state == 3) {
				p.setStyle(android.graphics.Paint.Style.FILL);
				p.setColor(0xFFE0524A);
				p.setTextAlign(android.graphics.Paint.Align.CENTER);
				p.setTextSize(s * 0.55f);
				p.setFakeBoldText(true);
				android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
				c.drawText("!", cx, cy - (fm.ascent + fm.descent) / 2, p);
				return;
			}
			long t = android.os.SystemClock.uptimeMillis() - t0;
			float rot = state == 1 ? (t % 900) * 360f / 900 : 0;
			if (state == 1) {
				// the arc that runs round the circle
				float R = s / 2 - 2.5f * dp;
				p.setStyle(android.graphics.Paint.Style.STROKE);
				p.setStrokeWidth(2.5f * dp);
				p.setColor(acc);
				p.setAlpha(50);
				c.drawCircle(cx, cy, R, p);
				p.setAlpha(255);
				android.graphics.RectF o = new android.graphics.RectF(cx - R, cy - R, cx + R, cy + R);
				c.drawArc(o, (t % 1400) * 360f / 1400 - 90, 100, false, p);
			}
			c.save();
			c.rotate(rot, cx, cy);
			float r = s * 0.2f, sw = s * 0.075f;
			p.setStyle(android.graphics.Paint.Style.STROKE);
			p.setStrokeWidth(sw);
			p.setColor(acc);
			android.graphics.RectF o = new android.graphics.RectF(cx - r, cy - r, cx + r, cy + r);
			float a0 = -60, sweep = 300;
			c.drawArc(o, a0, sweep, false, p);
			double a1 = Math.toRadians(a0 + sweep);
			float ex = cx + (float) (r * Math.cos(a1)), ey = cy + (float) (r * Math.sin(a1));
			float tx = (float) -Math.sin(a1), ty = (float) Math.cos(a1), nx = (float) Math.cos(a1), ny = (float) Math.sin(a1);
			float hl = s * 0.13f;
			android.graphics.Path a = new android.graphics.Path();
			a.moveTo(ex + tx * hl * 0.9f, ey + ty * hl * 0.9f);
			a.lineTo(ex + nx * hl * 0.75f - tx * hl * 0.25f, ey + ny * hl * 0.75f - ty * hl * 0.25f);
			a.lineTo(ex - nx * hl * 0.75f - tx * hl * 0.25f, ey - ny * hl * 0.75f - ty * hl * 0.25f);
			a.close();
			p.setStyle(android.graphics.Paint.Style.FILL);
			c.drawPath(a, p);
			c.restore();
			if (state == 1) postInvalidateOnAnimation();
		}
	}

	/** A plain paragraph (a notice: downloading, nothing found…). */
	void text(String what) {
		text(what, 15);
	}

	/** v5.24 (Viorel): where the data come from, in the one size of every source text (Bubble.SRC). */
	void source(String what) {
		text(what, Bubble.SRC);
	}

	private void text(String what, float sp) {
		TextView w = new TextView(getContext());
		w.setTypeface(reg);
		TextSize.size(w, sp, ts);
		w.setTextColor(dim());
		w.setLineSpacing(0, 1.1f);
		w.setText(what);
		w.setPadding(0, (int) (12 * dp), 0, (int) (12 * dp));
		pair = null;
		into().addView(w);
	}

	/**
	 * v2.001 (Viorel): an entry in short paragraphs: the icon, the name and INTRO, then one row per
	 * button with its own small icon and what it does.
	 */
	void steps(Bitmap icon, String name, String intro, Bitmap[] icons, String[] whats) {
		steps(icon, name, intro, icons, null, whats);
	}

	/**
	 * v2.190 (Viorel): a long entry split into its sub-functions: each row its icon, its name
	 * (HEADS, bold; may be null) and what it does.
	 */
	void steps(Bitmap icon, String name, String intro, Bitmap[] icons, String[] heads, String[] whats) {
		// v2.010 (Viorel): the steps under the card's head, their icons in the same column as the
		// section's icon (the card's left edge), the text in the rest of the width.
		LinearLayout head = card(icon, name, intro);
		Context c = getContext();
		LinearLayout card = new LinearLayout(c);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(head.getPaddingLeft(), head.getPaddingTop(), head.getPaddingRight(), head.getPaddingBottom());
		card.setBackground(head.getBackground());
		head.setBackground(null);
		head.setPadding(0, 0, 0, 0);
		card.addView(head, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		for (int k = 0; k < whats.length; k++) {
			LinearLayout r = new LinearLayout(c);
			r.setGravity(heads != null ? android.view.Gravity.TOP : android.view.Gravity.CENTER_VERTICAL);
			ImageView iv = new ImageView(c);
			iv.setImageBitmap(icons[k]);
			int sz = (int) (34 * dp);
			iv.setPadding((int) (5 * dp), (int) (5 * dp), (int) (5 * dp), (int) (5 * dp));
			iv.setBackground(Glass.pill(Glass.disc(dark), 0, 17 * dp, dp));
			LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(sz, sz);
			ip.rightMargin = (int) (heads != null ? 14 * dp : 24 * dp);
			r.addView(iv, ip);
			LinearLayout tx = new LinearLayout(c);
			tx.setOrientation(LinearLayout.VERTICAL);
			if (heads != null && heads[k] != null) {
				TextView n = new TextView(c);
				n.setTypeface(bold);
				TextSize.size(n, 15.5f, ts);
				n.setTextColor(ink());
				n.setText(heads[k]);
				tx.addView(n);
			}
			TextView w = new TextView(c);
			w.setTypeface(reg);
			TextSize.size(w, 15, ts);
			w.setTextColor(dim());
			w.setLineSpacing(0, 1.1f);
			w.setText(whats[k]);
			tx.addView(w);
			r.addView(tx, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
			rl.topMargin = (int) (12 * dp);
			card.addView(r, rl);
		}
		add(card);
	}

	/** One entry: the icon, its name and what it does. */
	void entry(Bitmap icon, String name, String what) {
		add(card(icon, name, what));
	}

	private LinearLayout card(Bitmap icon, String name, String what) {
		Context c = getContext();
		LinearLayout row = new LinearLayout(c);
		// v5.00 (Viorel): each explanation in a glass card, its icon on a faint disc.
		int cp = (int) (14 * dp);
		row.setPadding(cp, cp, cp, cp);
		row.setBackground(Glass.paneBg(dark, 20 * dp, dp)); // v5.25: more opaque, easier to read
		ImageView iv = new ImageView(c);
		iv.setImageBitmap(icon);
		int s = (int) (44 * dp);
		iv.setPadding((int) (6 * dp), (int) (6 * dp), (int) (6 * dp), (int) (6 * dp));
		iv.setBackground(Glass.pill(Glass.disc(dark), 0, 22 * dp, dp));
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
		// v2.190 (Viorel): a long text shows 2 lines and "Show all ▾".
		if (what != null && what.length() > LONG) more(txt, w);
		row.addView(txt, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
		return row;
	}

	/** Texts longer than this fold to 2 lines. */
	private static final int LONG = 170;

	private void more(LinearLayout txt, final TextView w) {
		w.setMaxLines(2);
		w.setEllipsize(android.text.TextUtils.TruncateAt.END);
		final TextView m = new TextView(getContext());
		m.setTypeface(bold);
		TextSize.size(m, 14, ts);
		m.setTextColor(ro.meteoalarm.android.core.Theme.accent(dark));
		final String all = ro.meteoalarm.android.core.Tr.t("Show all ▾", "Arată tot ▾"), less = ro.meteoalarm.android.core.Tr.t("Show less ▴", "Arată mai puțin ▴");
		m.setText(all);
		m.setPadding(0, (int) (6 * dp), (int) (12 * dp), (int) (2 * dp));
		m.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				boolean open = w.getMaxLines() == 2;
				w.setMaxLines(open ? Integer.MAX_VALUE : 2);
				w.setEllipsize(open ? null : android.text.TextUtils.TruncateAt.END);
				m.setText(open ? less : all);
			}
		});
		txt.addView(m, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
	}

	/** v2.190: the section's box (or the page before the first section). */
	private LinearLayout box, pair;
	/** v2.190 (Viorel): in landscape the cards stand on 2 columns. */
	private boolean twoCols;

	private LinearLayout into() {
		return box != null ? box : list;
	}

	/** A card into the page: one under the other, or 2 side by side in landscape. */
	private void add(View card) {
		if (!twoCols) {
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
			rl.bottomMargin = (int) (10 * dp);
			into().addView(card, rl);
			return;
		}
		LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
		if (pair == null || pair.getChildCount() >= 2) {
			pair = new LinearLayout(getContext());
			pair.setOrientation(LinearLayout.HORIZONTAL);
			pair.setBaselineAligned(false);
			LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
			pl.bottomMargin = (int) (10 * dp);
			into().addView(pair, pl);
			// an empty place keeps a lone card half wide
			View sp = new View(getContext());
			sp.setTag("pad");
			pair.addView(card, cl);
			LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, 1, 1);
			sl.leftMargin = (int) (10 * dp);
			pair.addView(sp, sl);
			return;
		}
		pair.removeViewAt(1);
		cl.leftMargin = (int) (10 * dp);
		pair.addView(card, cl);
	}

	/** v2.190 (Viorel): the section's outline blinks twice. */
	private void pulse(final View v) {
		final GradientDrawable o = new GradientDrawable();
		o.setCornerRadius(22 * dp);
		o.setStroke((int) (2.5f * dp), ro.meteoalarm.android.core.Theme.accent(dark));
		o.setColor(0);
		o.setAlpha(0);
		v.setBackground(o);
		android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0, 1);
		a.setDuration(1400);
		a.setStartDelay(250);
		a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
			public void onAnimationUpdate(android.animation.ValueAnimator x) {
				float f = (Float) x.getAnimatedValue();
				o.setAlpha(Math.round(255 * (float) Math.abs(Math.sin(f * Math.PI * 2))));
			}
		});
		a.addListener(new android.animation.AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(android.animation.Animator x) {
				v.setBackground(null);
			}
		});
		a.start();
	}

	/**
	 * v2.100 (Viorel): an earthquake of ☰ › Earthquakes: its coloured magnitude square instead of
	 * the icon, the title by it and the text under both, the whole width; a tap on the square or
	 * on the title opens its information screen.
	 */
	void quakeEntry(Bitmap badge, String name, String what, final Runnable open) {
		Context c = getContext();
		LinearLayout card = new LinearLayout(c);
		card.setOrientation(LinearLayout.VERTICAL);
		int cp = (int) (14 * dp);
		card.setPadding(cp, (int) (12 * dp), cp, cp);
		card.setBackground(Glass.paneBg(dark, 20 * dp, dp));
		LinearLayout top = new LinearLayout(c);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(android.view.Gravity.CENTER_VERTICAL);
		ImageView iv = new ImageView(c);
		iv.setImageBitmap(badge);
		int s = (int) (48 * dp);
		top.addView(iv, new LinearLayout.LayoutParams(s, s));
		TextView n = new TextView(c);
		n.setTypeface(bold);
		TextSize.size(n, 18, ts);
		n.setTextColor(ink());
		n.setText(name);
		LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
		nl.leftMargin = (int) (12 * dp);
		top.addView(n, nl);
		if (open != null) {
			OnClickListener go = new OnClickListener() {
				public void onClick(View v) {
					open.run();
				}
			};
			// v2.102 (Viorel): a tap anywhere on the card, its text too.
			card.setOnClickListener(go);
		}
		card.addView(top);
		TextView w = new TextView(c);
		w.setTypeface(reg);
		TextSize.size(w, 15, ts);
		w.setTextColor(dim());
		w.setLineSpacing(0, 1.1f);
		w.setText(what);
		LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		wl.topMargin = (int) (8 * dp);
		card.addView(w, wl);
		add(card);
	}

	void show() {
		scroll.scrollTo(0, 0);
		panel.animate().cancel();
		panel.setTranslationY(0);
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

	private final View handle;
	private float downY;

	/** v5.24: the frosted glass shows the screen under the window's place, so it is redrawn while it moves. */
	private final android.animation.ValueAnimator.AnimatorUpdateListener redraw = new android.animation.ValueAnimator.AnimatorUpdateListener() {
		public void onAnimationUpdate(android.animation.ValueAnimator a) {
			panel.invalidate();
		}
	};

	/** The window follows a downward swipe on its top; past a quarter of its height it closes, else it springs back. */
	private boolean swipe(MotionEvent e) {
		switch (e.getActionMasked()) {
		case MotionEvent.ACTION_DOWN:
			downY = e.getRawY();
			return true;
		case MotionEvent.ACTION_MOVE:
			panel.setTranslationY(Math.max(0, e.getRawY() - downY));
			panel.invalidate();
			return true;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (panel.getTranslationY() > panel.getHeight() / 4f)
				panel.animate().translationY(getHeight()).setDuration(200).setUpdateListener(redraw).withEndAction(new Runnable() {
					public void run() {
						hide();
						panel.setTranslationY(0);
					}
				}).start();
			else panel.animate().translationY(0).setDuration(150).setUpdateListener(redraw).start();
			return true;
		}
		return false;
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		return true;
	}
}
