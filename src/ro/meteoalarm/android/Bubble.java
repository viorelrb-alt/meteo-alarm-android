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
 * screen high (the rows scroll, with "⌄ more ⌃" on the title's line where there are more) and closes
 * at a tap outside it.
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
	private final TextView title, where, nav;
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
		nav = text(c, 14, false);
		nav.setSingleLine(true);
		nav.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
		nav.setPadding((int) (4 * dp), (int) (10 * dp), (int) (14 * dp), (int) (10 * dp));
		nav.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent e) {
				if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
				// The left half (⌄) goes down, the right half (⌃) goes up, a screenful at a time.
				boolean down = e.getX() < v.getWidth() / 2f;
				int step = Math.max(1, scroll.getHeight() - (int) (24 * dp));
				scroll.smoothScrollBy(0, down ? step : -step);
				return true;
			}
		});
		top.addView(nav, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
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
		return dark ? 0xFFFAF9F5 : 0xFF000000;
	}

	private int page() {
		return dark ? 0xFF262624 : 0xFFFFFFFF;
	}

	private int line() {
		return dark ? 0xFF5A5954 : 0xFF999999;
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
		nav.setTextSize(14 * zk);
		int ink = ink();
		boolean hasTitle = head != null && head.length() > 0;
		title.setVisibility(hasTitle ? VISIBLE : INVISIBLE);
		title.setText(hasTitle ? head : "");
		title.setTextColor(ink);
		nav.setText("⌄  " + Tr.t("more", "mai multe") + "  ⌃");
		nav.setTextColor(dark ? 0xFFA6A39A : 0xFF555555);
		where.setTextSize(15 * zk);
		where.setTextColor(dark ? 0xFFA6A39A : 0xFF555555);
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
		List<TextView> values = new ArrayList<TextView>();
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
				TextView t = text(c, (src ? 15 : 18) * zk, false);
				t.setTag("text");
				t.setText(nb(r.text));
				if (r.kind == Row.VALUE) values.add(t);
				t.setTextColor(src ? (dark ? 0xFFA6A39A : 0xFF555555) : ink);
				t.setLineSpacing(2 * dp, 1);
				t.setPadding((int) (16 * dp), (int) (5 * dp), (int) (16 * dp), (int) (5 * dp));
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
				lp.topMargin = (int) (6 * dp);
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
			t.setText(r.kind == Row.HEADER ? r.text.toUpperCase() : (r.checked ? "✓  " : "") + r.text);
			t.setTextColor(r.kind == Row.HEADER ? (dark ? 0xFFA6A39A : 0xFF555555) : ink);
			t.setGravity(Gravity.CENTER_VERTICAL);
			int ph = (int) (16 * dp);
			if (r.kind == Row.HEADER) t.setPadding(ph, (int) ((info ? 6 : 10) * dp), ph, (int) (4 * dp));
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
		for (Grid g : grids) g.minW = inner;
		list.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		nav.setVisibility(GONE);
		top.setVisibility(title.getVisibility() == VISIBLE ? VISIBLE : GONE);
		int pad = (int) (6 * dp), titleH = head(width);
		// The same shape in both orientations: at most half the portrait height, and never past the
		// screen's edge (in landscape the room under the button).
		boolean below = (anchor[1] + anchor[3]) / 2 < sh / 2f;
		int room = below ? sh - (int) anchor[3] - margin - (int) (2 * dp) : (int) anchor[1] - margin - (int) (2 * dp);
		if (pan) tip = 0;
		int maxH = pan ? sh - 2 * margin : Math.min((int) (Math.max(sw, sh) * (TextSize.tablet(c) ? 0.7f : 0.5f)), room - tip), rowsH = list.getMeasuredHeight();
		boolean scrolls = titleH + rowsH + 2 * pad > maxH;
		if (scrolls) {
			// The "⌄ more ⌃" on the title's line, smaller when the title would not fit on one line.
			top.setVisibility(VISIBLE);
			nav.setVisibility(VISIBLE);
			float ns = 14 * zk;
			String tt = title.getText().toString();
			Paint tp = new Paint(title.getPaint()), np = new Paint(nav.getPaint());
			float sd = getResources().getDisplayMetrics().scaledDensity;
			for (; ns > 9; ns -= 0.5f) {
				np.setTextSize(ns * sd);
				float navW = np.measureText(nav.getText().toString()) + nav.getPaddingLeft() + nav.getPaddingRight();
				float titleW = tt.length() == 0 ? 0 : tp.measureText(tt) + title.getPaddingLeft() + title.getPaddingRight();
				if (navW + titleW <= width) break;
			}
			nav.setTextSize(ns);
			titleH = head(width);
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

	/** The height of the title's line (with "more") and the place under it, at WIDTH. */
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

	/** v3.50: the arrows of "⌄ more ⌃" fade at the end they cannot go further. */
	private void hints() {
		if (nav.getVisibility() != VISIBLE) return;
		int y = scroll.getScrollY(), end = list.getHeight() - scroll.getHeight();
		String w = Tr.t("more", "mai multe");
		android.text.SpannableString sp = new android.text.SpannableString("⌄  " + w + "  ⌃");
		int dim = dark ? 0x40A6A39A : 0x40555555;
		if (y >= end - 1) sp.setSpan(new android.text.style.ForegroundColorSpan(dim), 0, 1, 0);
		if (y <= 0) sp.setSpan(new android.text.style.ForegroundColorSpan(dim), sp.length() - 1, sp.length(), 0);
		nav.setText(sp);
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
		private float rowH, padX;
		private boolean[] right;

		Grid(Context c, String[][] cells, float sp) {
			super(c);
			this.cells = cells;
			float px = sp * getResources().getDisplayMetrics().scaledDensity;
			tp.setTextSize(px);
			tp.setTypeface(reg);
			hp.setTextSize(px);
			hp.setTypeface(bold);
			lp.setStrokeWidth(Math.max(1, dp));
			padX = 8 * dp;
			rowH = px * 1.9f;
			int n = 0;
			for (String[] r : cells) n = Math.max(n, r.length);
			colW = new float[n];
			right = new boolean[n];
			for (int j = 0; j < n; j++) {
				int num = 0, all = 0;
				for (int i = 0; i < cells.length; i++) {
					String v = j < cells[i].length ? cells[i][j] : "";
					colW[j] = Math.max(colW[j], (i == 0 ? hp : tp).measureText(v) + 2 * padX);
					if (i > 0 && v.length() > 0) {
						all++;
						if (v.matches("^[−+\\-–]?\\d.*")) num++;
					}
				}
				right[j] = j > 0 && all > 0 && num * 2 >= all;
			}
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
			band.setColor(dark ? 0xFF3A3936 : 0xFFE8E6DC);
			c.drawRect(0, 0, getWidth(), rowH, band);
			lp.setColor(dark ? 0xFF6B6A65 : 0xFF000000);
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
					float tx = right[j] ? cx + cw - padX - p.measureText(v) : cx + padX;
					c.drawText(v, tx, base, p);
					cx += cw;
				}
			}
		}
	}

	static List<Row> rows() {
		return new ArrayList<Row>();
	}
}
