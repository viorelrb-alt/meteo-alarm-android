package ro.meteoalarm.android;

import ro.meteoalarm.android.core.Tr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A menu as a speech bubble, as on the Kindle: a rounded frame next to the button that opened it,
 * with a small tip pointing at the button, and rows separated by thin lines. It opens under a
 * button in the upper half of the screen and over one in the lower half, is at most half the
 * screen high (the rows scroll, with ▲ / ▼ floating outside the frame where there are more, v3.51)
 * and closes at a tap outside it.
 */
final class Bubble extends FrameLayout {
	/** A row: a choice, a section title, or a line between sections. */
	static final class Row {
		final String text;
		final Bitmap icon;
		final int kind;
		final boolean checked;
		/** A table's cells, the first row is the header (kind TABLE). */
		String[][] cells;
		/**
		 * v3.20: VALUE = "label: value" kept on one line (smaller when needed), TABLE = cells with
		 * lines, sliding sideways, SOURCE = where the data come from, gathered at the bottom once.
		 */
		static final int ITEM = 0, HEADER = 1, LINE = 2, TEXT = 3, VALUE = 4, TABLE = 5, SOURCE = 6;

		Row(String text, Bitmap icon, int kind, boolean checked) {
			this.text = text;
			this.icon = icon;
			this.kind = kind;
			this.checked = checked;
		}

		static Row item(String text) {
			return new Row(text, null, ITEM, false);
		}

		/** v3.32 (Viorel): a function that is on or off: a box at the right, X when on, empty when off. */
		int tick = -1;

		Row tick(boolean on) {
			tick = on ? 1 : 0;
			return this;
		}
	}

	/**
	 * Info rows as Screen gives them (after the title, from index 1): "h" header, "t" text, "v"
	 * value, "s" source, "T" table (each next element a tab-separated row).
	 */
	static List<Row> info(List<String[]> info) {
		List<Row> rows = new ArrayList<Row>();
		for (int i = 1; i < info.size(); i++) {
			String[] r = info.get(i);
			String k = r[0];
			if (k.equals("T")) {
				Row t = new Row("", null, Row.TABLE, false);
				t.cells = new String[r.length - 1][];
				for (int j = 1; j < r.length; j++) t.cells[j - 1] = r[j].split("\t", -1);
				rows.add(t);
			} else rows.add(new Row(r[1], null, k.equals("h") ? Row.HEADER : k.equals("v") ? Row.VALUE : k.equals("s") ? Row.SOURCE : Row.TEXT, false));
		}
		return rows;
	}

	/** A number keeps its unit on its line: the space after a digit does not break (v3.20). */
	static String nb(String s) {
		return s.replaceAll("(\\d) (?=\\S)", "$1\u00A0");
	}

	interface Pick {
		/** Row K (its index in the list, headers and lines included) was tapped. */
		void picked(int k);
	}

	private final float dp;
	private final Typeface reg, bold;
	private final Card card;
	private final ScrollView scroll;
	private final LinearLayout list;
	private final TextView title, where, up, down;
	private final LinearLayout top;
	private boolean dark, open;
	private Runnable onClose;

	Bubble(Context c, Typeface reg, Typeface bold) {
		super(c);
		this.reg = reg;
		this.bold = bold;
		dp = c.getResources().getDisplayMetrics().density;
		setVisibility(GONE);
		card = new Card(c);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setClickable(true);
		// v3.50 (Viorel): "⌄ more ⌃" on the title's line, at the right; under the title the place.
		top = new LinearLayout(c);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);
		title = text(c, 17, true);
		title.setPadding((int) (16 * dp), (int) (12 * dp), (int) (8 * dp), (int) (8 * dp));
		top.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
		card.addView(top, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		where = text(c, 15, false);
		where.setSingleLine(true);
		where.setEllipsize(android.text.TextUtils.TruncateAt.END);
		where.setPadding((int) (16 * dp), 0, (int) (16 * dp), (int) (6 * dp));
		card.addView(where);
		scroll = new ScrollView(c);
		scroll.setVerticalScrollBarEnabled(true);
		list = new LinearLayout(c);
		list.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(list);
		card.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
			public void onScrollChange(View v, int x, int y, int ox, int oy) {
				hints();
			}
		});
		addView(card, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		// v3.51 (Viorel): only ▲ and ▼, floating outside the bubble, ▲ at its top, ▼ at its bottom.
		up = arrow(c, false);
		down = arrow(c, true);
	}

	private TextView arrow(Context c, final boolean dn) {
		TextView a = text(c, 16, true);
		a.setText(dn ? "▼" : "▲");
		a.setGravity(Gravity.CENTER);
		a.setIncludeFontPadding(false);
		a.setVisibility(GONE);
		a.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				int step = Math.max(1, scroll.getHeight() - (int) (24 * dp));
				scroll.smoothScrollBy(0, dn ? step : -step);
			}
		});
		addView(a, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		return a;
	}

	boolean isOpen() {
		return open;
	}

	void setDark(boolean d) {
		dark = d;
	}

	void setOnClose(Runnable r) {
		onClose = r;
	}

	private TextView text(Context c, float sp, boolean b) {
		TextView t = new TextView(c);
		t.setTextSize(sp);
		t.setTypeface(b ? bold : reg);
		return t;
	}

	private int ink() {
		return ro.meteoalarm.android.core.Theme.ink(dark);
	}

	private int page() {
		return ro.meteoalarm.android.core.Theme.page(dark);
	}

	private int line() {
		return ro.meteoalarm.android.core.Theme.thin(dark);
	}

	/**
	 * Opens next to the button at ANCHOR (pixels: left, top, right, bottom), WIDTH pixels wide, with
	 * TITLE (none when empty) and ROWS; PICK hears the tapped row, after the bubble has closed.
	 */
	/**
	 * Set before show(): in landscape the bubble is one standard panel, the same place and size for
	 * every box of the right side (Viorel 2026-11-07: the small bubbles could not be used there).
	 */
	boolean panel;

	/** v3.50 (Viorel): set before show(): the place an information bubble is about, under its title. */
	String place;

	void show(float[] anchor, int width, String head, List<Row> rows, boolean en, final Pick pick) {
		boolean pan = panel;
		panel = false;
		String at = place;
		place = null;
		Context c = getContext();
		// Larger on a tablet (Viorel 2026-11-04: the ☰ menu was too small there; +30 %, then 10 % and
		// 5 % less in v2.20 / v2.21), and the text size of ☰.
		float zk = (TextSize.tablet(c) ? 1.11f : 1f) * TextSize.get(c);
		width = (int) (width * zk);
		title.setTextSize(17 * zk);
		int ink = ink();
		// v3.51 (Viorel): the place is written under the title, not in it as well.
		if (head != null && at != null && at.length() > 0 && head.endsWith(" · " + at)) head = head.substring(0, head.length() - at.length() - 3);
		boolean hasTitle = head != null && head.length() > 0;
		title.setVisibility(hasTitle ? VISIBLE : INVISIBLE);
		title.setText(hasTitle ? head : "");
		title.setTextColor(ink);
		where.setTextSize(15 * zk);
		where.setTextColor(ro.meteoalarm.android.core.Theme.dim(dark));
		where.setText(at == null ? "" : at);
		where.setVisibility(at == null || at.length() == 0 || !hasTitle ? GONE : VISIBLE);
		list.removeAllViews();
		boolean first = true, info = false;
		for (Row r : rows) if (r.kind >= Row.TEXT) info = true;
		// v3.20 (Viorel): the sources once, at the bottom, after a line.
		List<String> sources = new ArrayList<String>();
		List<Row> body = new ArrayList<Row>();
		for (Row r : rows) {
			if (r.kind != Row.SOURCE) body.add(r);
			else if (!sources.contains(r.text)) sources.add(r.text);
		}
		if (!sources.isEmpty()) {
			// Only info bubbles have sources; a pick's index counts all rows, so these never mix with choices.
			rows = body;
			rows.add(new Row("", null, Row.LINE, false));
			for (String src : sources) rows.add(new Row(src, null, Row.SOURCE, false));
		}
		List<Grid> grids = new ArrayList<Grid>();
		List<TextView> values = new ArrayList<TextView>(), srcs = new ArrayList<TextView>();
		for (int k = 0; k < rows.size(); k++) {
			final Row r = rows.get(k);
			final int idx = k;
			if (r.kind == Row.LINE) {
				View v = new View(c);
				boolean thin = info && !sources.isEmpty() && k > 0 && k < rows.size() - 1 && rows.get(k + 1).kind == Row.SOURCE;
				v.setBackgroundColor(thin ? line() : ink);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Math.max(thin ? 1 : 2, (int) ((thin ? 1 : 2) * dp)));
				lp.leftMargin = lp.rightMargin = (int) (10 * dp);
				lp.topMargin = lp.bottomMargin = (int) (4 * dp);
				list.addView(v, lp);
				first = true;
				continue;
			}
			if (r.kind == Row.TABLE) {
				Grid g = new Grid(c, r.cells, 15 * zk);
				grids.add(g);
				android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
				hs.setHorizontalScrollBarEnabled(true);
				hs.addView(g);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
				lp.leftMargin = lp.rightMargin = (int) (16 * dp);
				lp.topMargin = lp.bottomMargin = (int) (6 * dp);
				list.addView(hs, lp);
				first = true;
				continue;
			}
			if (r.kind == Row.TEXT || r.kind == Row.VALUE || r.kind == Row.SOURCE) {
				// Information in larger letters, on as many lines as it needs (the warning details).
				boolean src = r.kind == Row.SOURCE;
				TextView t = text(c, (src ? 14 : 18) * zk, false); // v3.51: sources as big as the old "more"
				t.setTag("text");
				t.setText(nb(r.text));
				if (r.kind == Row.VALUE) values.add(t);
				if (src) srcs.add(t);
				t.setTextColor(src ? ro.meteoalarm.android.core.Theme.dim(dark) : ink);
				t.setLineSpacing(2 * dp, 1);
				t.setPadding((int) (16 * dp), (int) (2.5f * dp), (int) (16 * dp), (int) (2.5f * dp)); // v3.51: half the gap
				list.addView(t, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
				first = true;
				continue;
			}
			if (info && r.kind == Row.HEADER && k > 0) {
				// v3.20 (Viorel): a line before each section's title, to tell the parts apart.
				View sep = new View(c);
				sep.setBackgroundColor(line());
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, (int) dp));
				lp.leftMargin = lp.rightMargin = (int) (10 * dp);
				lp.topMargin = (int) (3 * dp);
				list.addView(sep, lp);
			}
			if (!first && r.kind == Row.ITEM) {
				View sep = new View(c);
				sep.setBackgroundColor(line());
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, (int) dp));
				lp.leftMargin = lp.rightMargin = (int) (10 * dp);
				list.addView(sep, lp);
			}
			first = false;
			// v3.50 (Viorel): in an information bubble the section titles as big as the texts (0.5 smaller at most).
			TextView t = text(c, (r.kind == Row.HEADER ? (info ? 17.5f : 13) : 15) * zk, r.kind == Row.HEADER || r.checked);
			t.setText(r.kind == Row.HEADER ? r.text.toUpperCase() : r.text + (r.checked ? "  ✓" : "")); // v3.51: ✓ at the right
			t.setTextColor(r.kind == Row.HEADER ? ro.meteoalarm.android.core.Theme.dim(dark) : ink);
			t.setGravity(Gravity.CENTER_VERTICAL);
			int ph = (int) (16 * dp);
			if (r.kind == Row.HEADER) t.setPadding(ph, (int) ((info ? 3 : 10) * dp), ph, (int) ((info ? 2 : 4) * dp));
			else {
				t.setPadding(ph, 0, ph, 0);
				t.setMinHeight((int) (46 * dp * zk));
				t.setBackgroundResource(android.R.drawable.list_selector_background);
			}
			if (r.icon != null) {
				BitmapDrawable d = new BitmapDrawable(getResources(), r.icon);
				int s = (int) (26 * dp * zk);
				d.setBounds(0, 0, s, s);
				t.setCompoundDrawables(d, null, null, null);
				t.setCompoundDrawablePadding((int) (14 * dp * zk));
			}
			if (r.tick >= 0) {
				// v3.32 (Viorel): the same box in every language, X when the function is on.
				final boolean on = r.tick == 1;
				final int col = ink;
				android.graphics.drawable.Drawable box = new android.graphics.drawable.Drawable() {
					final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

					@Override
					public void draw(android.graphics.Canvas cv) {
						android.graphics.Rect b = getBounds();
						float w = dp * 2;
						p.setColor(col);
						p.setStyle(Paint.Style.STROKE);
						p.setStrokeWidth(w);
						p.setStrokeCap(Paint.Cap.ROUND);
						android.graphics.RectF rf = new android.graphics.RectF(b.left + w, b.top + w, b.right - w, b.bottom - w);
						cv.drawRoundRect(rf, dp * 3, dp * 3, p);
						if (on) {
							float in = b.width() * 0.27f;
							cv.drawLine(b.left + in, b.top + in, b.right - in, b.bottom - in, p);
							cv.drawLine(b.right - in, b.top + in, b.left + in, b.bottom - in, p);
						}
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
				};
				int bs = (int) (22 * dp * zk);
				box.setBounds(0, 0, bs, bs);
				android.graphics.drawable.Drawable[] cd = t.getCompoundDrawables();
				t.setCompoundDrawables(cd[0], null, box, null);
				t.setContentDescription(r.text + (on ? " ✓" : ""));
			}
			if (r.kind == Row.ITEM) t.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					hide();
					pick.picked(idx);
				}
			});
			list.addView(t, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		}
		// Size: the rows' height, at most half the screen (the hints count too when they show).
		// The screen as it is now: the parent's size (the bubble itself, hidden, keeps the size it had
		// before the phone turned).
		View pv = (View) getParent();
		int sw = pv != null && pv.getWidth() > 0 ? pv.getWidth() : getResources().getDisplayMetrics().widthPixels;
		int sh = pv != null && pv.getHeight() > 0 ? pv.getHeight() : getResources().getDisplayMetrics().heightPixels;
		int margin = (int) (12 * dp), tip = (int) (10 * dp);
		// Every row on one line, all choices in the same font: the bubble grows to hold the longest
		// one; only when even the screen is too narrow do all of them get the same smaller font.
		float need = 0, sp = 15 * zk;
		boolean choices = false;
		for (int pass = 0; pass < 40; pass++) {
			need = 0;
			for (int k = 0; k < list.getChildCount(); k++) {
				View v = list.getChildAt(k);
				if (!(v instanceof TextView) || "text".equals(v.getTag())) continue;
				TextView t = (TextView) v;
				t.setSingleLine(true);
				t.setEllipsize(android.text.TextUtils.TruncateAt.END);
				if (t.isClickable()) {
					t.setTextSize(sp);
					choices = true;
				}
				android.graphics.drawable.Drawable ic = t.getCompoundDrawables()[0], tk = t.getCompoundDrawables()[2];
				need = Math.max(need, t.getPaint().measureText(t.getText().toString()) + t.getPaddingLeft() + t.getPaddingRight()
						+ (ic != null ? ic.getBounds().width() + t.getCompoundDrawablePadding() : 0)
						+ (tk != null ? tk.getBounds().width() + t.getCompoundDrawablePadding() + 12 * dp : 0) + 4 * dp);
			}
			if (need <= sw - 2 * margin || sp <= 11) break;
			sp -= 0.5f;
		}
		// On a tablet just as wide as the longest row (Viorel v2.21: it was too wide there).
		pan = pan && sw > sh;
		if (pan) width = (int) (sw * 0.62f);
		else {
			// v2.90: only a bubble of choices shrinks to its longest row; one of info rows (only
			// headers and texts) keeps its own width (it was narrow and tall on the Tab S9 FE).
			boolean texts = false;
			for (Row r : rows) if (r.kind == Row.TEXT) texts = true;
			if (TextSize.tablet(c) && choices && !texts) width = (int) Math.ceil(need + 8 * dp);
			width = Math.min(Math.max(width, (int) Math.ceil(need)), sw - 2 * margin);
		}
		// A value with its label on one line, smaller when needed; a table at least as wide as the bubble.
		int inner = width - (int) (32 * dp);
		for (TextView t : values) {
			Paint pt = new Paint(t.getPaint());
			float vs = 18 * zk;
			for (; vs > 12 * zk; vs -= 0.5f) {
				pt.setTextSize(vs * getResources().getDisplayMetrics().scaledDensity);
				if (pt.measureText(t.getText().toString()) <= inner - 2 * dp) break;
			}
			t.setTextSize(vs);
		}
		for (Grid g : grids) g.fit(inner, 11 * zk);
		small(srcs, values, grids, 18 * zk);
		list.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		up.setVisibility(GONE);
		down.setVisibility(GONE);
		top.setVisibility(title.getVisibility() == VISIBLE ? VISIBLE : GONE);
		int pad = (int) (6 * dp), titleH = head(width);
		// The same shape in both orientations: at most half the portrait height, and never past the
		// screen's edge (in landscape the room under the button).
		boolean below = (anchor[1] + anchor[3]) / 2 < sh / 2f;
		int room = below ? sh - (int) anchor[3] - margin - (int) (2 * dp) : (int) anchor[1] - margin - (int) (2 * dp);
		if (pan) tip = 0;
		int maxH = pan ? sh - 2 * margin : Math.min((int) (Math.max(sw, sh) * (TextSize.tablet(c) ? 0.7f : 0.5f)), room - tip), rowsH = list.getMeasuredHeight();
		boolean scrolls = titleH + rowsH + 2 * pad > maxH;
		// v3.51: ▲ / ▼ outside the frame, at the right where there is room, else at the left; when
		// neither side has room the bubble gets narrower by the arrows' width.
		int aw = (int) (28 * dp * zk), gap = (int) (1 * dp);
		if (scrolls && !pan) {
			int nw = sw - gap - aw - 2 * gap;
			if (nw < width) {
				width = nw;
				inner = width - (int) (32 * dp);
				for (Grid g : grids) g.fit(inner, 11 * zk);
				small(srcs, values, grids, 18 * zk);
				list.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
				rowsH = list.getMeasuredHeight();
				titleH = head(width);
			}
		}
		int scrollH = scrolls ? maxH - titleH - 2 * pad : rowsH;
		// The panel keeps its full height whatever it holds.
		if (pan && !scrolls) scrollH = maxH - titleH - 2 * pad;
		scroll.getLayoutParams().height = scrollH;
		scroll.scrollTo(0, 0);
		if (scrolls) post(new Runnable() {
			public void run() {
				hints();
			}
		});
		int cardH = titleH + scrollH + 2 * pad + tip;
		// Under the button in the upper half of the screen, over it in the lower half.
		int x = (int) Math.max(margin, Math.min(anchor[0], sw - margin - width));
		int y = below ? (int) anchor[3] + (int) (2 * dp) : (int) anchor[1] - (int) (2 * dp) - cardH;
		y = Math.max(margin, Math.min(y, sh - margin - cardH));
		if (pan) {
			// The right side of the screen, from the top to the bottom, reaching far to the left.
			x = sw - margin - width;
			y = margin;
			below = false;
		}
		if (scrolls) {
			int ax;
			if (x + width + gap + aw <= sw - gap) ax = x + width + gap;
			else if (x - gap - aw >= gap) ax = x - gap - aw;
			else {
				x = Math.max(gap, sw - 2 * gap - aw - width);
				ax = x + width + gap;
			}
			int ink2 = ink();
			for (TextView a : new TextView[]{up, down}) {
				// v3.52 (Viorel): only the arrow heads, no circle, close to the bubble's side.
				a.setTextSize(20 * zk);
				a.setTextColor(ink2);
				a.setBackground(null);
				a.setAlpha(1f);
				LayoutParams al = (LayoutParams) a.getLayoutParams();
				al.width = al.height = aw;
				al.leftMargin = ax;
				a.setVisibility(VISIBLE);
			}
			int cardTop = y + (below ? tip : 0), cardBot = y + cardH - (below ? 0 : tip);
			((LayoutParams) up.getLayoutParams()).topMargin = cardTop;
			((LayoutParams) down.getLayoutParams()).topMargin = cardBot - aw;
			up.requestLayout();
			down.requestLayout();
		}
		card.tipUp = below;
		card.tipX = (anchor[0] + anchor[2]) / 2 - x;
		card.tip = tip;
		card.setPadding(0, pad + (below ? tip : 0), 0, pad + (below ? 0 : tip));
		LayoutParams lp = (LayoutParams) card.getLayoutParams();
		lp.width = width;
		lp.height = cardH;
		lp.leftMargin = x;
		lp.topMargin = y;
		card.setLayoutParams(lp);
		card.invalidate();
		open = true;
		setVisibility(VISIBLE);
		bringToFront();
	}

	/**
	 * v3.52 (Viorel): the source / estimate texts at the bottom are 0.5 points smaller than the
	 * smallest other text of the bubble (TEXT size, values and tables).
	 */
	private void small(List<TextView> srcs, List<TextView> values, List<Grid> grids, float text) {
		if (srcs.isEmpty()) return;
		float sd = getResources().getDisplayMetrics().scaledDensity, min = text;
		for (TextView t : values) min = Math.min(min, t.getTextSize() / sd);
		for (Grid g : grids) min = Math.min(min, g.sp());
		for (TextView t : srcs) t.setTextSize(min - 0.5f);
	}

	/** The height of the title's line and the place under it, at WIDTH. */
	private int head(int width) {
		int h = 0;
		if (top.getVisibility() == VISIBLE) {
			top.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
			h += top.getMeasuredHeight();
		}
		if (where.getVisibility() == VISIBLE) {
			where.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
			h += where.getMeasuredHeight();
		}
		return h;
	}

	/** The arrows fade at the end they cannot go further. */
	private void hints() {
		if (up.getVisibility() != VISIBLE) return;
		int y = scroll.getScrollY(), end = list.getHeight() - scroll.getHeight();
		up.setAlpha(y <= 0 ? 0.3f : 1f);
		down.setAlpha(y >= end - 1 ? 0.3f : 1f);
	}

	void hide() {
		if (!open) return;
		open = false;
		setVisibility(GONE);
		if (onClose != null) onClose.run();
	}

	/** A tap outside the bubble closes it (and does nothing else). */
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (e.getActionMasked() == MotionEvent.ACTION_DOWN) hide();
		return true;
	}

	/** The bubble's frame: rounded, 2 dp outline, a tip toward the button. */
	private final class Card extends LinearLayout {
		boolean tipUp;
		float tipX, tip;
		private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path path = new Path();

		Card(Context c) {
			super(c);
			setWillNotDraw(false);
		}

		@Override
		protected void onDraw(Canvas c) {
			float w = getWidth(), h = getHeight(), r = 12 * dp, sw = 2 * dp, half = sw / 2;
			float top = tipUp ? tip : 0, bot = tipUp ? h : h - tip;
			float tx = Math.max(r + tip, Math.min(tipX, w - r - tip));
			path.reset();
			path.addRoundRect(new RectF(half, top + half, w - half, bot - half), r, r, Path.Direction.CW);
			Path t = new Path();
			if (tipUp) {
				t.moveTo(tx - tip, top + sw);
				t.lineTo(tx, half);
				t.lineTo(tx + tip, top + sw);
			} else {
				t.moveTo(tx - tip, bot - sw);
				t.lineTo(tx, h - half);
				t.lineTo(tx + tip, bot - sw);
			}
			t.close();
			path.op(t, Path.Op.UNION);
			p.setStyle(Paint.Style.FILL);
			p.setColor(page());
			c.drawPath(path, p);
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(sw);
			p.setColor(ink());
			c.drawPath(path, p);
		}
	}

	/**
	 * A table in a bubble (v3.20, Viorel): lines around every cell, the header on a shaded band;
	 * wider than the bubble it slides sideways (in a HorizontalScrollView).
	 */
	private final class Grid extends View {
		final String[][] cells;
		int minW;
		private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG), hp = new Paint(Paint.ANTI_ALIAS_FLAG), lp = new Paint();
		private float[] colW;
		private float rowH, padX, px0;

		Grid(Context c, String[][] cells, float sp) {
			super(c);
			this.cells = cells;
			px0 = sp * getResources().getDisplayMetrics().scaledDensity;
			tp.setTypeface(reg);
			hp.setTypeface(bold);
			lp.setStrokeWidth(Math.max(1, dp));
			size(px0);
		}

		private void size(float px) {
			tp.setTextSize(px);
			hp.setTextSize(px);
			padX = 8 * dp * px / px0;
			rowH = px * 1.9f;
			int n = 0;
			for (String[] r : cells) n = Math.max(n, r.length);
			colW = new float[n];
			for (int j = 0; j < n; j++)
				for (int i = 0; i < cells.length; i++) {
					String v = j < cells[i].length ? cells[i][j] : "";
					colW[j] = Math.max(colW[j], (i == 0 ? hp : tp).measureText(v) + 2 * padX);
				}
		}

		/**
		 * v3.51 (Viorel): the table fits the bubble: its letters get smaller down to MINSP; only past
		 * that it slides sideways.
		 */
		void fit(int w, float minSp) {
			minW = w;
			float min = minSp * getResources().getDisplayMetrics().scaledDensity, px = px0;
			size(px);
			while (natural() > w && px > min) {
				px = Math.max(min, px - 0.5f * dp);
				size(px);
			}
			requestLayout();
		}

		/** The table's text size now (sp). */
		float sp() {
			return tp.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
		}

		private float natural() {
			float w = 0;
			for (float c : colW) w += c;
			return w + lp.getStrokeWidth();
		}

		@Override
		protected void onMeasure(int ws, int hs) {
			setMeasuredDimension((int) Math.ceil(Math.max(natural(), minW)), (int) Math.ceil(rowH * cells.length + lp.getStrokeWidth()));
		}

		@Override
		protected void onDraw(Canvas c) {
			float extra = Math.max(0, getWidth() - natural()) / Math.max(1, colW.length), sw = lp.getStrokeWidth(), h = rowH * cells.length;
			int ink = ink();
			tp.setColor(ink);
			hp.setColor(ink);
			Paint band = new Paint();
			band.setColor(ro.meteoalarm.android.core.Theme.tableBand(dark));
			c.drawRect(0, 0, getWidth(), rowH, band);
			lp.setColor(ro.meteoalarm.android.core.Theme.grid(dark));
			float x = sw / 2;
			for (int j = 0; j <= colW.length; j++) {
				c.drawLine(x, 0, x, h + sw / 2, lp);
				if (j < colW.length) x += colW[j] + extra;
			}
			for (int i = 0; i <= cells.length; i++) c.drawLine(0, sw / 2 + i * rowH, getWidth(), sw / 2 + i * rowH, lp);
			Paint.FontMetrics fm = tp.getFontMetrics();
			for (int i = 0; i < cells.length; i++) {
				float cx = sw / 2, base = sw / 2 + i * rowH + rowH / 2 - (fm.ascent + fm.descent) / 2;
				for (int j = 0; j < colW.length; j++) {
					String v = j < cells[i].length ? cells[i][j] : "";
					Paint p = i == 0 ? hp : tp;
					float cw = colW[j] + extra;
					// v3.51 (Viorel): titles and values centred in their column.
					c.drawText(v, cx + (cw - p.measureText(v)) / 2, base, p);
					cx += cw;
				}
			}
		}
	}

	static List<Row> rows() {
		return new ArrayList<Row>();
	}
}
