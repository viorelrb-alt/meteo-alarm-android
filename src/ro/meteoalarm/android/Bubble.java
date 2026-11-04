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
 * screen high (the rows scroll, with "⌃ more ⌃" / "⌄ more ⌄" where there are more) and closes
 * at a tap outside it.
 */
final class Bubble extends FrameLayout {
	/** A row: a choice, a section title, or a line between sections. */
	static final class Row {
		final String text;
		final Bitmap icon;
		final int kind;
		final boolean checked;
		static final int ITEM = 0, HEADER = 1, LINE = 2, TEXT = 3;

		Row(String text, Bitmap icon, int kind, boolean checked) {
			this.text = text;
			this.icon = icon;
			this.kind = kind;
			this.checked = checked;
		}

		static Row item(String text) {
			return new Row(text, null, ITEM, false);
		}
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
	private final TextView title, less, more;
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
		title = text(c, 17, true);
		title.setPadding((int) (16 * dp), (int) (12 * dp), (int) (16 * dp), (int) (8 * dp));
		card.addView(title);
		less = hint(c);
		card.addView(less);
		scroll = new ScrollView(c);
		scroll.setVerticalScrollBarEnabled(true);
		list = new LinearLayout(c);
		list.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(list);
		card.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		more = hint(c);
		card.addView(more);
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

	private TextView hint(final Context c) {
		TextView t = text(c, 14, false);
		t.setGravity(Gravity.CENTER);
		t.setPadding(0, (int) (4 * dp), 0, (int) (4 * dp));
		t.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				scroll.smoothScrollTo(0, v == less ? 0 : list.getHeight());
			}
		});
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
	void show(float[] anchor, int width, String head, List<Row> rows, boolean en, final Pick pick) {
		Context c = getContext();
		int ink = ink();
		title.setVisibility(head == null || head.length() == 0 ? GONE : VISIBLE);
		title.setText(head);
		title.setTextColor(ink);
		less.setText(Tr.t("⌃  more  ⌃", "⌃  mai multe  ⌃"));
		more.setText(Tr.t("⌄  more  ⌄", "⌄  mai multe  ⌄"));
		less.setTextColor(dark ? 0xFFA6A39A : 0xFF555555);
		more.setTextColor(dark ? 0xFFA6A39A : 0xFF555555);
		list.removeAllViews();
		boolean first = true;
		for (int k = 0; k < rows.size(); k++) {
			final Row r = rows.get(k);
			final int idx = k;
			if (r.kind == Row.LINE) {
				View v = new View(c);
				v.setBackgroundColor(ink);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Math.max(2, (int) (2 * dp)));
				lp.leftMargin = lp.rightMargin = (int) (10 * dp);
				lp.topMargin = lp.bottomMargin = (int) (4 * dp);
				list.addView(v, lp);
				first = true;
				continue;
			}
			if (r.kind == Row.TEXT) {
				// Information in larger letters, on as many lines as it needs (the warning details).
				TextView t = text(c, 18, false);
				t.setTag("text");
				t.setText(r.text);
				t.setTextColor(ink);
				t.setLineSpacing(2 * dp, 1);
				t.setPadding((int) (16 * dp), (int) (5 * dp), (int) (16 * dp), (int) (5 * dp));
				list.addView(t, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
				first = true;
				continue;
			}
			if (!first && r.kind == Row.ITEM) {
				View sep = new View(c);
				sep.setBackgroundColor(line());
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, (int) dp));
				lp.leftMargin = lp.rightMargin = (int) (10 * dp);
				list.addView(sep, lp);
			}
			first = false;
			TextView t = text(c, r.kind == Row.HEADER ? 13 : 15, r.kind == Row.HEADER || r.checked);
			t.setText(r.kind == Row.HEADER ? r.text.toUpperCase() : (r.checked ? "✓  " : "") + r.text);
			t.setTextColor(r.kind == Row.HEADER ? (dark ? 0xFFA6A39A : 0xFF555555) : ink);
			t.setGravity(Gravity.CENTER_VERTICAL);
			int ph = (int) (16 * dp);
			if (r.kind == Row.HEADER) t.setPadding(ph, (int) (10 * dp), ph, (int) (4 * dp));
			else {
				t.setPadding(ph, 0, ph, 0);
				t.setMinHeight((int) (46 * dp));
				t.setBackgroundResource(android.R.drawable.list_selector_background);
			}
			if (r.icon != null) {
				BitmapDrawable d = new BitmapDrawable(getResources(), r.icon);
				int s = (int) (26 * dp);
				d.setBounds(0, 0, s, s);
				t.setCompoundDrawables(d, null, null, null);
				t.setCompoundDrawablePadding((int) (14 * dp));
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
		float need = 0, sp = 15;
		for (int pass = 0; pass < 40; pass++) {
			need = 0;
			for (int k = 0; k < list.getChildCount(); k++) {
				View v = list.getChildAt(k);
				if (!(v instanceof TextView) || "text".equals(v.getTag())) continue;
				TextView t = (TextView) v;
				t.setSingleLine(true);
				if (t.isClickable()) t.setTextSize(sp);
				android.graphics.drawable.Drawable ic = t.getCompoundDrawables()[0];
				need = Math.max(need, t.getPaint().measureText(t.getText().toString()) + t.getPaddingLeft() + t.getPaddingRight()
						+ (ic != null ? ic.getBounds().width() + t.getCompoundDrawablePadding() : 0) + 4 * dp);
			}
			if (need <= sw - 2 * margin || sp <= 11) break;
			sp -= 0.5f;
		}
		width = Math.min(Math.max(width, (int) Math.ceil(need)), sw - 2 * margin);
		list.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		title.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		more.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		int pad = (int) (6 * dp), titleH = title.getVisibility() == VISIBLE ? title.getMeasuredHeight() : 0;
		// The same shape in both orientations: at most half the portrait height, and never past the
		// screen's edge (in landscape the room under the button).
		boolean below = (anchor[1] + anchor[3]) / 2 < sh / 2f;
		int room = below ? sh - (int) anchor[3] - margin - (int) (2 * dp) : (int) anchor[1] - margin - (int) (2 * dp);
		int maxH = Math.min(Math.max(sw, sh) / 2, room - tip), rowsH = list.getMeasuredHeight();
		boolean scrolls = titleH + rowsH + 2 * pad > maxH;
		int scrollH = scrolls ? maxH - titleH - 2 * pad - 2 * more.getMeasuredHeight() : rowsH;
		less.setVisibility(scrolls ? INVISIBLE : GONE);
		more.setVisibility(scrolls ? VISIBLE : GONE);
		scroll.getLayoutParams().height = scrollH;
		scroll.scrollTo(0, 0);
		int cardH = titleH + scrollH + 2 * pad + (scrolls ? 2 * more.getMeasuredHeight() : 0) + tip;
		// Under the button in the upper half of the screen, over it in the lower half.
		int x = (int) Math.max(margin, Math.min(anchor[0], sw - margin - width));
		int y = below ? (int) anchor[3] + (int) (2 * dp) : (int) anchor[1] - (int) (2 * dp) - cardH;
		y = Math.max(margin, Math.min(y, sh - margin - cardH));
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

	private void hints() {
		if (more.getVisibility() == GONE) return;
		int y = scroll.getScrollY(), end = list.getHeight() - scroll.getHeight();
		less.setVisibility(y > 0 ? VISIBLE : INVISIBLE);
		more.setVisibility(y < end - 1 ? VISIBLE : INVISIBLE);
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

	static List<Row> rows() {
		return new ArrayList<Row>();
	}
}
