package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import ro.meteoalarm.android.core.Theme;

/**
 * v5.00 (Viorel, "Opțiunea 3 Overlay Elegant", mockups machete-meniu-setari): the ☰ menu as a
 * window of frosted glass in the middle of the screen: SETĂRI APLICAȚIE and X on top (no GATA:
 * X or a tap outside closes it), the settings in cards by section, scrolling, and under them the
 * fixed bar SISTEM & SUPORT with plain icons. MainActivity fills it (begin, section, card rows…).
 */
final class SettingsPanel extends FrameLayout {
	interface Choice {
		void chose(int k);
	}

	private final float dp;
	private final Typeface reg, bold;
	private final LinearLayout panel, content, foot;
	private final ScrollView scroll;
	private final TextView title, subtitle, footTitle;
	private final View closeX, headLine;
	private boolean dark, open;
	private float zk = 1;
	private Runnable onClose;
	/** The card being filled. */
	private LinearLayout card;
	private boolean cardFirst;

	SettingsPanel(Context c, Typeface reg, Typeface bold) {
		super(c);
		this.reg = reg;
		this.bold = bold;
		dp = c.getResources().getDisplayMetrics().density;
		setVisibility(GONE);
		panel = new LinearLayout(c) {
			@Override
			public boolean onTouchEvent(MotionEvent e) {
				return true;
			}
		};
		panel.setOrientation(LinearLayout.VERTICAL);
		LinearLayout head = new LinearLayout(c);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(px(24), px(20), px(18), px(16));
		LinearLayout tt = new LinearLayout(c);
		tt.setOrientation(LinearLayout.VERTICAL);
		title = text(c, 24, true);
		title.setSingleLine(true);
		title.setEllipsize(TextUtils.TruncateAt.END);
		subtitle = text(c, 15, false);
		subtitle.setSingleLine(true);
		subtitle.setEllipsize(TextUtils.TruncateAt.END);
		tt.addView(title);
		tt.addView(subtitle);
		head.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		closeX = new View(c);
		closeX.setContentDescription("X");
		closeX.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				hide();
			}
		});
		head.addView(closeX, new LinearLayout.LayoutParams(px(42), px(42)));
		panel.addView(head);
		headLine = new View(c);
		panel.addView(headLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) dp)));
		scroll = new ScrollView(c);
		scroll.setVerticalScrollBarEnabled(false);
		content = new LinearLayout(c);
		content.setOrientation(LinearLayout.VERTICAL);
		content.setPadding(px(14), px(4), px(14), px(18));
		scroll.addView(content);
		panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		foot = new LinearLayout(c);
		foot.setOrientation(LinearLayout.VERTICAL);
		foot.setPadding(px(10), px(14), px(10), px(14));
		footTitle = text(c, 14, true);
		footTitle.setLetterSpacing(0.12f);
		footTitle.setPadding(px(14), 0, 0, px(8));
		panel.addView(foot);
		addView(panel, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	private int px(float d) {
		return Math.round(d * dp);
	}

	private TextView text(Context c, float sp, boolean b) {
		TextView t = new TextView(c);
		t.setTextSize(sp);
		t.setTypeface(b ? bold : reg);
		t.setIncludeFontPadding(true);
		return t;
	}

	private int ink() {
		return Theme.ink(dark);
	}

	private int dim() {
		return Theme.dim(dark);
	}

	private int accent() {
		return Theme.accent(dark);
	}

	/** The text on the accent colour. */
	private int onAccent() {
		return dark ? Theme.page(true) : 0xFFFFFFFF;
	}

	boolean isOpen() {
		return open;
	}

	void setOnClose(Runnable r) {
		onClose = r;
	}

	/** Starts filling the window anew (the scroll stays where it was when KEEP). */
	void begin(boolean dark, String head, String sub, float zk) {
		this.dark = dark;
		this.zk = zk;
		content.removeAllViews();
		foot.removeAllViews();
		title.setText(head);
		title.setTextSize(23 * zk);
		title.setTextColor(ink());
		title.setLetterSpacing(0.04f);
		subtitle.setText(sub);
		subtitle.setTextSize(15 * zk);
		subtitle.setTextColor(dim());
		closeX.setBackground(Glass.closeIcon(dark, dp));
		headLine.setBackgroundColor(Glass.edge(dark));
		GradientDrawable pb = Glass.panelBg(dark, 30 * dp, dp);
		pb.setColor(Glass.alpha(Theme.page(dark), dark ? 0.86f : 0.9f));
		panel.setBackground(pb);
		GradientDrawable fb = new GradientDrawable();
		fb.setColor(Glass.alpha(Theme.page(dark), dark ? 0.5f : 0.55f));
		fb.setCornerRadii(new float[] { 0, 0, 0, 0, 30 * dp, 30 * dp, 30 * dp, 30 * dp });
		fb.setStroke(Math.max(1, (int) dp), Glass.edge(dark));
		foot.setBackground(fb);
		card = null;
	}

	/** A section's title (accent, spaced capitals) and a new card under it; DASHED: the hidden test card. */
	void section(String name, boolean dashed) {
		TextView t = text(getContext(), 14 * zk, true);
		t.setText(name.toUpperCase());
		t.setTextColor(accent());
		t.setLetterSpacing(0.12f);
		t.setPadding(px(10), px(18), px(10), px(10));
		content.addView(t);
		card = new LinearLayout(getContext());
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(px(14), px(6), px(14), px(6));
		if (dashed) {
			card.setBackground(new Dashed());
		} else card.setBackground(Glass.cardBg(dark, 22 * dp, dp));
		content.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		cardFirst = true;
	}

	/** The test card's dashed edge. */
	private final class Dashed extends Drawable {
		final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

		@Override
		public void draw(Canvas c) {
			android.graphics.Rect b = getBounds();
			p.setStyle(Paint.Style.FILL);
			p.setColor(Glass.alpha(accent(), 0.06f));
			RectF r = new RectF(b.left + dp, b.top + dp, b.right - dp, b.bottom - dp);
			c.drawRoundRect(r, 22 * dp, 22 * dp, p);
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(1.5f * dp);
			p.setColor(Glass.alpha(accent(), 0.8f));
			p.setPathEffect(new DashPathEffect(new float[] { 6 * dp, 4 * dp }, 0));
			c.drawRoundRect(r, 22 * dp, 22 * dp, p);
			p.setPathEffect(null);
		}

		@Override
		public void setAlpha(int a) {
		}

		@Override
		public void setColorFilter(android.graphics.ColorFilter f) {
		}

		@Override
		public int getOpacity() {
			return android.graphics.PixelFormat.TRANSLUCENT;
		}
	}

	/** A thin line between the rows of a card. */
	void line() {
		View v = new View(getContext());
		v.setBackgroundColor(Glass.edge(dark));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) dp));
		lp.leftMargin = lp.rightMargin = px(4);
		card.addView(v, lp);
	}

	private void sep() {
		if (!cardFirst) line();
		cardFirst = false;
	}

	/** The icon in its rounded tile. */
	private View tile(String icon, boolean small) {
		View v = new View(getContext());
		LayerTile d = new LayerTile(icon, small);
		v.setBackground(d);
		return v;
	}

	private final class LayerTile extends Drawable {
		final String icon;
		final boolean small;
		final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

		LayerTile(String icon, boolean small) {
			this.icon = icon;
			this.small = small;
		}

		@Override
		public void draw(Canvas c) {
			android.graphics.Rect b = getBounds();
			p.setStyle(Paint.Style.FILL);
			p.setColor(Glass.disc(dark));
			c.drawRoundRect(new RectF(b), b.width() * 0.26f, b.width() * 0.26f, p);
			float s = b.width() * (small ? 0.5f : 0.52f) / 24f;
			c.save();
			c.translate(b.exactCenterX() - 12 * s, b.exactCenterY() - 12 * s);
			c.scale(s, s);
			LineIcon.draw(c, p, icon, accent());
			c.restore();
		}

		@Override
		public void setAlpha(int a) {
		}

		@Override
		public void setColorFilter(android.graphics.ColorFilter f) {
		}

		@Override
		public int getOpacity() {
			return android.graphics.PixelFormat.TRANSLUCENT;
		}
	}

	/** A row: icon tile, title and line under it, and RIGHT (a switch, a value, ›) at the end. */
	private LinearLayout row(String icon, String head, String sub, View right, boolean indent, final Runnable tap) {
		Context c = getContext();
		LinearLayout r = new LinearLayout(c);
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setPadding(px(indent ? 48 : 4), px(indent ? 8 : 12), px(4), px(indent ? 8 : 12));
		r.setMinimumHeight(px((indent ? 52 : 64) * zk));
		if (icon != null) {
			int ts = px((indent ? 34 : 42) * zk);
			LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ts, ts);
			tl.rightMargin = px(14);
			r.addView(tile(icon, indent), tl);
		}
		LinearLayout tx = new LinearLayout(c);
		tx.setOrientation(LinearLayout.VERTICAL);
		TextView t = text(c, (indent ? 16 : 18) * zk, true);
		t.setText(head);
		t.setTextColor(ink());
		tx.addView(t);
		if (sub != null && sub.length() > 0) {
			TextView s = text(c, (indent ? 14 : 15) * zk, false);
			s.setText(sub);
			s.setTextColor(dim());
			tx.addView(s);
		}
		r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		if (right != null) {
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			rl.leftMargin = px(10);
			r.addView(right, rl);
		}
		if (tap != null) {
			r.setBackgroundResource(android.R.drawable.list_selector_background);
			r.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					tap.run();
				}
			});
		}
		card.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return r;
	}

	/** A value in grey and ›. */
	private View value(String v) {
		LinearLayout l = new LinearLayout(getContext());
		l.setGravity(Gravity.CENTER_VERTICAL);
		if (v != null && v.length() > 0) {
			TextView t = text(getContext(), 16 * zk, false);
			t.setText(v);
			t.setTextColor(dim());
			t.setSingleLine(true);
			t.setMaxWidth(px(150 * zk));
			t.setEllipsize(TextUtils.TruncateAt.END);
			l.addView(t);
		}
		View ch = new View(getContext());
		ch.setBackground(new LineIcon("chevron", dim()));
		LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(22), px(22));
		cl.leftMargin = px(4);
		l.addView(ch, cl);
		return l;
	}

	/** A row that opens something (› at the right, VALUE before it). */
	void link(String icon, String head, String sub, String value, Runnable tap) {
		sep();
		row(icon, head, sub, value(value), false, tap);
	}

	/** A row under the one before, indented, with a smaller icon (opens a page). */
	void subLink(String icon, String head, String sub, Runnable tap) {
		cardFirst = false;
		row(icon, head, sub, value(null), true, tap);
	}

	/** A row with a switch; TAP runs on a tap anywhere on the row. */
	void toggle(String icon, String head, String sub, boolean on, Runnable tap) {
		sep();
		Switch sw = new Switch(on);
		sw.setLayoutParams(new LinearLayout.LayoutParams(px(54 * zk), px(32 * zk)));
		LinearLayout r = row(icon, head, sub, null, false, tap);
		LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(px(54 * zk), px(32 * zk));
		rl.leftMargin = px(10);
		r.addView(sw, rl);
	}

	private final class Switch extends View {
		final boolean on;
		final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

		Switch(boolean on) {
			super(SettingsPanel.this.getContext());
			this.on = on;
		}

		@Override
		protected void onDraw(Canvas c) {
			float w = getWidth(), h = getHeight();
			p.setColor(on ? accent() : Glass.alpha(Theme.ink(dark), dark ? 0.2f : 0.16f));
			c.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
			p.setColor(dark ? Theme.page(true) : 0xFFFFFFFF);
			float r = h / 2 - 3.5f * dp;
			c.drawCircle(on ? w - h / 2 : h / 2, h / 2, r, p);
		}
	}

	/** A row with a choice of 2–3 short options at the right (°C / °F, 12 h / 24 h). */
	void segmented(String icon, String head, String[] opts, int sel, Choice ch) {
		sep();
		row(icon, head, null, seg(opts, sel, ch, false), false, null);
	}

	/** The options as a pill of segments, the chosen one filled; WIDE: across the whole width. */
	private LinearLayout seg(String[] opts, int sel, final Choice ch, boolean wide) {
		LinearLayout l = new LinearLayout(getContext());
		l.setPadding(px(4), px(4), px(4), px(4));
		l.setBackground(Glass.pill(Glass.disc(dark), 0, 14 * dp, dp));
		for (int i = 0; i < opts.length; i++) {
			final int k = i;
			TextView t = text(getContext(), 16 * zk, true);
			t.setText(opts[i]);
			t.setSingleLine(true);
			t.setGravity(Gravity.CENTER);
			t.setPadding(px(14), px(7), px(14), px(7));
			t.setTextColor(i == sel ? onAccent() : dim());
			if (i == sel) t.setBackground(Glass.pill(accent(), 0, 11 * dp, dp));
			t.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					ch.chose(k);
				}
			});
			l.addView(t, wide ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1)
					: new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		return l;
	}

	/** The theme row: its name, the 6 colours (tap: that theme) and Light / Dark / Automatic. */
	void theme(String head, String sub, String[] names, int[] colors, int sel, final Choice pick, String[] modes, int mode, Choice modePick) {
		sep();
		row("palette", head, sub, null, false, null);
		LinearLayout dots = new LinearLayout(getContext());
		dots.setPadding(0, 0, 0, px(8));
		for (int i = 0; i < names.length; i++) {
			final int k = i;
			LinearLayout col = new LinearLayout(getContext());
			col.setOrientation(LinearLayout.VERTICAL);
			col.setGravity(Gravity.CENTER_HORIZONTAL);
			Dot d = new Dot(colors[i], i == sel);
			col.addView(d, new LinearLayout.LayoutParams(px(42 * zk), px(42 * zk)));
			TextView t = text(getContext(), 12.5f * zk, i == sel);
			t.setText(names[i] + (i == sel ? " ✓" : ""));
			t.setTextColor(i == sel ? ink() : dim());
			t.setSingleLine(true);
			t.setEllipsize(TextUtils.TruncateAt.END);
			t.setGravity(Gravity.CENTER);
			col.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			col.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					pick.chose(k);
				}
			});
			dots.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		}
		card.addView(dots);
		LinearLayout s = seg(modes, mode, modePick, true);
		LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		sl.bottomMargin = px(12);
		sl.leftMargin = sl.rightMargin = px(4);
		card.addView(s, sl);
	}

	private final class Dot extends View {
		final int color;
		final boolean on;
		final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

		Dot(int color, boolean on) {
			super(SettingsPanel.this.getContext());
			this.color = color;
			this.on = on;
		}

		@Override
		protected void onDraw(Canvas c) {
			float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - 3 * dp;
			p.setStyle(Paint.Style.FILL);
			p.setColor(color);
			c.drawCircle(cx, cy, on ? r - 4 * dp : r - 2 * dp, p);
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth((on ? 3 : 1.5f) * dp);
			p.setColor(on ? ink() : Glass.edge(dark));
			c.drawCircle(cx, cy, r, p);
		}
	}

	/** The favourites' title with "+ Add" at the right. */
	void favHead(String name, String add, final Runnable tap) {
		sep();
		cardFirst = true;
		LinearLayout r = new LinearLayout(getContext());
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setPadding(px(6), px(10), px(4), px(4));
		TextView t = text(getContext(), 16 * zk, true);
		t.setText(name);
		t.setTextColor(dim());
		r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		if (add != null) {
			TextView a = text(getContext(), 16 * zk, true);
			a.setText("+ " + add);
			a.setTextColor(accent());
			a.setPadding(px(14), px(6), px(14), px(6));
			a.setBackground(Glass.pill(0, accent(), 18 * dp, 1.5f * dp));
			a.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					tap.run();
				}
			});
			r.addView(a);
		}
		card.addView(r);
	}

	/** A favourite: a star (filled for the town shown) and its name. */
	void fav(String name, boolean shown, final Runnable tap) {
		LinearLayout r = new LinearLayout(getContext());
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setPadding(px(6), px(9), px(4), px(9));
		View st = new View(getContext());
		st.setBackground(new LineIcon(shown ? "star" : "star-off", shown ? accent() : dim()));
		LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(px(24 * zk), px(24 * zk));
		sl.rightMargin = px(16);
		r.addView(st, sl);
		TextView t = text(getContext(), 18 * zk, shown);
		t.setText(name);
		t.setTextColor(ink());
		t.setSingleLine(true);
		t.setEllipsize(TextUtils.TruncateAt.END);
		r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		r.setBackgroundResource(android.R.drawable.list_selector_background);
		r.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				tap.run();
			}
		});
		card.addView(r);
	}

	/** Names as pills on as many lines as needed (the US favourites). */
	void chips(List<String> names, final List<Runnable> taps) {
		Flow f = new Flow(getContext());
		f.setPadding(px(4), px(4), px(4), px(10));
		for (int i = 0; i < names.size(); i++) {
			final int k = i;
			TextView t = text(getContext(), 16 * zk, false);
			t.setText(names.get(i));
			t.setTextColor(ink());
			t.setSingleLine(true);
			t.setPadding(px(14), px(7), px(14), px(7));
			t.setBackground(Glass.pill(Glass.disc(dark), 0, 18 * dp, dp));
			t.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					taps.get(k).run();
				}
			});
			f.addView(t);
		}
		card.addView(f);
	}

	/** Lays its children left to right, wrapping. */
	private final class Flow extends ViewGroup {
		Flow(Context c) {
			super(c);
		}

		@Override
		protected void onMeasure(int ws, int hs) {
			int w = MeasureSpec.getSize(ws) - getPaddingLeft() - getPaddingRight(), x = 0, y = 0, lh = 0, g = px(8);
			for (int i = 0; i < getChildCount(); i++) {
				View v = getChildAt(i);
				v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
				if (x > 0 && x + v.getMeasuredWidth() > w) {
					x = 0;
					y += lh + g;
					lh = 0;
				}
				x += v.getMeasuredWidth() + g;
				lh = Math.max(lh, v.getMeasuredHeight());
			}
			setMeasuredDimension(MeasureSpec.getSize(ws), y + lh + getPaddingTop() + getPaddingBottom());
		}

		@Override
		protected void onLayout(boolean ch, int l, int t, int r, int b) {
			int w = r - l - getPaddingLeft() - getPaddingRight(), x = 0, y = 0, lh = 0, g = px(8);
			for (int i = 0; i < getChildCount(); i++) {
				View v = getChildAt(i);
				if (x > 0 && x + v.getMeasuredWidth() > w) {
					x = 0;
					y += lh + g;
					lh = 0;
				}
				v.layout(getPaddingLeft() + x, getPaddingTop() + y, getPaddingLeft() + x + v.getMeasuredWidth(), getPaddingTop() + y + v.getMeasuredHeight());
				x += v.getMeasuredWidth() + g;
				lh = Math.max(lh, v.getMeasuredHeight());
			}
		}
	}

	/** A note in small grey letters inside the card. */
	void note(String s) {
		TextView t = text(getContext(), 13.5f * zk, false);
		t.setText(s);
		t.setTextColor(dim());
		t.setPadding(px(6), px(6), px(6), px(12));
		card.addView(t);
	}

	/** The fixed bar's title. */
	void footTitle(String s) {
		footTitle.setText(s.toUpperCase());
		footTitle.setTextColor(accent());
		footTitle.setTextSize(14 * zk);
		foot.addView(footTitle);
	}

	/** A row of the fixed bar: plain icons with their name under them. ICONS: one name, or "a>b" for two with › between. */
	void footRow(String[] icons, String[] labels, final Runnable[] taps) {
		LinearLayout r = new LinearLayout(getContext());
		r.setGravity(Gravity.TOP);
		for (int i = 0; i < icons.length; i++) {
			final int k = i;
			LinearLayout col = new LinearLayout(getContext());
			col.setOrientation(LinearLayout.VERTICAL);
			col.setGravity(Gravity.CENTER_HORIZONTAL);
			col.setPadding(px(2), px(6), px(2), px(6));
			int is = px(32 * zk);
			LinearLayout ic = new LinearLayout(getContext());
			ic.setGravity(Gravity.CENTER);
			String[] parts = icons[i].split(">");
			for (int j = 0; j < parts.length; j++) {
				if (j > 0) {
					View ch = new View(getContext());
					ch.setBackground(new LineIcon("chevron", dim()));
					ic.addView(ch, new LinearLayout.LayoutParams(px(16 * zk), px(16 * zk)));
				}
				View v = new View(getContext());
				v.setBackground(new LineIcon(parts[j], ink()));
				ic.addView(v, new LinearLayout.LayoutParams(is, is));
			}
			col.addView(ic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, is));
			TextView t = text(getContext(), 15 * zk, false);
			t.setText(labels[i]);
			t.setTextColor(ink());
			t.setGravity(Gravity.CENTER);
			t.setMaxLines(2);
			t.setPadding(0, px(6), 0, 0);
			col.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			col.setBackgroundResource(android.R.drawable.list_selector_background);
			col.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					taps[k].run();
				}
			});
			r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, icons[i].contains(">") ? 1.5f : 1));
		}
		foot.addView(r);
	}

	/** Opens (or, already open, shows the new content keeping the scroll). */
	void show() {
		View pv = (View) getParent();
		int sw = pv != null && pv.getWidth() > 0 ? pv.getWidth() : getResources().getDisplayMetrics().widthPixels;
		int sh = pv != null && pv.getHeight() > 0 ? pv.getHeight() : getResources().getDisplayMetrics().heightPixels;
		int m = px(14);
		int w = Math.min(sw - 2 * m, sw < sh ? sw : Math.round(Math.min(sw * 0.62f, 620 * dp * zk)));
		if (sw < sh && TextSize.tablet(getContext())) w = Math.min(w, Math.round(sw * 0.78f));
		LayoutParams lp = (LayoutParams) panel.getLayoutParams();
		lp.width = w;
		lp.height = sh - 2 * m;
		lp.gravity = Gravity.CENTER;
		panel.setLayoutParams(lp);
		if (!open) scroll.scrollTo(0, 0);
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

	/** A tap outside the window closes it. */
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (e.getActionMasked() == MotionEvent.ACTION_DOWN) hide();
		return true;
	}
}
