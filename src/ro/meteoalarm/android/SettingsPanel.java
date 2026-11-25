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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ro.meteoalarm.android.core.Theme;
import ro.meteoalarm.android.core.Tr;

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
	/** v5.01 (Viorel): texts of one kind (ROLE) on one line, all shrunk by the same factor so the longest fits. */
	private final Map<String, List<TextView>> roles = new HashMap<String, List<TextView>>();
	private boolean fitPending;
	/** The bar's names under the icons (shown when the bar is pulled up). */
	private final List<TextView> footLabels = new ArrayList<TextView>();
	private boolean footOpen;
	private View grab;
	/** v5.03 (Viorel): the bar down = all its icons in one row (COMPACT); pulled up = its rows with the names. */
	private LinearLayout compact;
	private final List<View> footRows = new ArrayList<View>();
	/**
	 * v2.200 (Viorel): 0 the bar as before; 1 phone landscape: a floating box at the bottom left, the
	 * title and the icons on one row, folded into one button (its grip widens it again); 2 tablet
	 * landscape on two columns: the title and the buttons with their names on one row.
	 */
	private int footMode;
	private LinearLayout oneRow, headRow, headText;
	private View moreBtn, fgrip;
	/** v2.190 (Viorel): in landscape the settings on two columns (TWO), the grip at the right edge folds them into one. */
	private boolean land, two;
	private LinearLayout colA, colB, target;
	private final View cgrip;
	private View twoBtn;
	private Columns onColumns;

	interface Columns {
		void set(boolean two);
	}

	void setOnColumns(Columns c) {
		onColumns = c;
	}

	/** v2.190: the names of System & Support show 3 s when the window opens, then only the icons. */
	private final Runnable footFold = new Runnable() {
		public void run() {
			setFootOpen(false);
		}
	};

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
		headRow = head;
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(px(24), px(20), px(18), px(16));
		LinearLayout tt = new LinearLayout(c);
		headText = tt;
		tt.setOrientation(LinearLayout.VERTICAL);
		title = text(c, 20, true);
		title.setSingleLine(true);
		subtitle = text(c, 13.5f, false);
		subtitle.setSingleLine(true);
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
		// v2.190 (Viorel): folded into one column (landscape), a button brings the two columns back at once.
		twoBtn = new View(c);
		twoBtn.setVisibility(GONE);
		twoBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (onColumns != null) onColumns.set(true);
			}
		});
		LinearLayout.LayoutParams tbl = new LinearLayout.LayoutParams(px(42), px(42));
		tbl.rightMargin = px(10);
		head.addView(twoBtn, tbl);
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
		foot = new LinearLayout(c) {
			// v5.01 (Viorel): pulled up, the names show under the icons; pulled down, only the icons.
			float y0;
			boolean drag;

			@Override
			public boolean onInterceptTouchEvent(MotionEvent e) {
				return track(e);
			}

			@Override
			public boolean onTouchEvent(MotionEvent e) {
				track(e);
				return true;
			}

			private boolean track(MotionEvent e) {
				switch (e.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					y0 = e.getRawY();
					drag = false;
					break;
				case MotionEvent.ACTION_MOVE:
					float d = e.getRawY() - y0;
					if (!drag && Math.abs(d) > px(12)) drag = true;
					if (drag && Math.abs(d) > px(24)) {
						removeCallbacks(footFold);
						setFootOpen(d < 0);
						y0 = e.getRawY();
					}
					break;
				case MotionEvent.ACTION_UP:
					if (!drag && e.getY() < px(22)) {
						removeCallbacks(footFold);
						setFootOpen(!footOpen);
					}
					break;
				}
				return drag;
			}
		};
		foot.setOrientation(LinearLayout.VERTICAL);
		foot.setPadding(px(10), px(4), px(10), px(8));
		foot.setLayoutTransition(new android.animation.LayoutTransition());
		footTitle = text(c, 12.5f, true);
		footTitle.setLetterSpacing(0.12f);
		footTitle.setSingleLine(true);
		footTitle.setPadding(px(14), 0, 0, px(2));
		panel.addView(foot);
		addView(panel, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		// v2.190 (Viorel): the grip at the window's right edge (landscape): pulled left, one column; right, two.
		cgrip = new View(c) {
			float x0;
			boolean done;

			@Override
			public boolean onTouchEvent(MotionEvent e) {
				switch (e.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					x0 = e.getRawX();
					done = false;
					return true;
				case MotionEvent.ACTION_MOVE:
					float d = e.getRawX() - x0;
					if (!done && Math.abs(d) > px(28)) {
						done = true;
						if ((d < 0) == two && onColumns != null) onColumns.set(d > 0);
					}
					return true;
				case MotionEvent.ACTION_UP:
					if (!done && onColumns != null) onColumns.set(!two); // a tap switches too (TalkBack)
					return true;
				}
				return true;
			}
		};
		cgrip.setVisibility(GONE);
		addView(cgrip, new LayoutParams(px(28), px(84)));
		panel.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
			public void onGlobalLayout() {
				if (fitPending && open && panel.getWidth() > 0) {
					fitPending = false;
					fitAll();
				}
			}
		});
	}

	/** TextView T is of kind ROLE: one line, shrunk with the others of its kind when one does not fit. */
	private TextView role(String r, TextView t) {
		t.setSingleLine(true);
		t.setTag(t.getTextSize());
		List<TextView> l = roles.get(r);
		if (l == null) roles.put(r, l = new ArrayList<TextView>());
		l.add(t);
		return t;
	}

	/** For each kind, the factor that fits its longest text in its room (never under 0.55), applied to all of it. */
	private void fitAll() {
		boolean changed = false;
		for (List<TextView> l : roles.values()) {
			float f = 1;
			for (TextView t : l) {
				int room = t.getWidth() - t.getCompoundPaddingLeft() - t.getCompoundPaddingRight();
				if (room <= 0 || t.getVisibility() != VISIBLE) continue;
				Paint p = new Paint(t.getPaint());
				p.setTextSize((Float) t.getTag());
				float need = p.measureText(t.getText().toString()) + 2;
				if (need > room) f = Math.min(f, room / need);
			}
			f = Math.max(0.55f, f);
			for (TextView t : l) {
				float want = (Float) t.getTag() * f;
				if (Math.abs(t.getTextSize() - want) > 0.5f) {
					t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, want);
					changed = true;
				}
			}
		}
		if (changed) panel.requestLayout();
	}

	private void setFootOpen(boolean o) {
		if (footOpen == o) return;
		footOpen = o;
		for (View r : footRows) r.setVisibility(o ? VISIBLE : GONE);
		if (footMode == 1) {
			floatShow();
			return;
		}
		if (compact != null) compact.setVisibility(o ? GONE : VISIBLE);
		if (o) {
			fitPending = true;
			panel.requestLayout();
		}
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
		return Theme.onPastel(dark); // v2.150 (Viorel): chosen buttons are pastel
	}

	/** v2.150: accent text, darkened just enough to read on the tinted page. */
	private int accentText() {
		return Theme.atLeast(accent(), Theme.page(dark), Theme.ink(dark), 4.5);
	}

	boolean isOpen() {
		return open;
	}

	void setOnClose(Runnable r) {
		onClose = r;
	}

	/** v2.190: LAND: the phone (or tablet) is in landscape; TWO: the settings on two columns there. */
	void columns(boolean land, boolean two) {
		this.land = land;
		this.two = land && two;
	}

	/** Where the next section goes: the content, or (two columns) the left, then the right column. */
	private LinearLayout target() {
		if (!two) return content;
		if (target == null) {
			LinearLayout cols = new LinearLayout(getContext());
			cols.setOrientation(LinearLayout.HORIZONTAL);
			colA = new LinearLayout(getContext());
			colA.setOrientation(LinearLayout.VERTICAL);
			colB = new LinearLayout(getContext());
			colB.setOrientation(LinearLayout.VERTICAL);
			LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
			a.rightMargin = px(7);
			LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
			b.leftMargin = px(7);
			cols.addView(colA, a);
			cols.addView(colB, b);
			content.addView(cols, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			target = colA;
		}
		return target;
	}

	/** v2.190: the sections after this go in the right column (two columns only). */
	void nextColumn() {
		if (two) {
			target();
			target = colB;
		}
	}

	/** Starts filling the window anew (the scroll stays where it was when KEEP). */
	void begin(boolean dark, String head, String sub, float zk) {
		this.dark = dark;
		this.zk = zk;
		content.removeAllViews();
		foot.removeAllViews();
		colA = colB = target = null;
		roles.clear();
		footLabels.clear();
		footRows.clear();
		compact = null;
		fitPending = true;
		title.setText(head);
		title.setTextSize(20 * zk);
		role("title", title);
		title.setTextColor(ink());
		title.setLetterSpacing(0.04f);
		subtitle.setText(sub);
		subtitle.setTextSize(13.5f * zk);
		role("subtitle", subtitle);
		subtitle.setTextColor(dim());
		closeX.setBackground(Glass.closeIcon(dark, dp));
		headLine.setBackgroundColor(Glass.edge(dark));
		GradientDrawable pb = Glass.panelBg(dark, 30 * dp, dp);
		pb.setColor(Glass.alpha(Theme.page(dark), dark ? 0.86f : 0.92f)); // v2.108 (Viorel): more matte, readable over the earthquake page; v5.22 (Viorel): frosted glass, the screen shows through
		// v5.40 (Viorel): on a tablet the screen around stays sharp, the panel draws its own frosted glass.
		panel.setBackground(TextSize.tablet(getContext()) ? Glass.frostBg(panel, 30 * dp, dark, dp, false) : pb);
		GradientDrawable fb = new GradientDrawable();
		fb.setColor(Glass.alpha(Theme.page(dark), dark ? 0.8f : 0.86f));
		fb.setCornerRadii(new float[] { 0, 0, 0, 0, 30 * dp, 30 * dp, 30 * dp, 30 * dp });
		fb.setStroke(Math.max(1, (int) dp), Glass.edge(dark));
		foot.setBackground(fb);
		card = null;
		// v2.200 (Viorel): in landscape the head on one row (title · subtitle), lower.
		boolean tab = TextSize.tablet(getContext());
		headText.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
		headText.setGravity(land ? Gravity.CENTER_VERTICAL : Gravity.NO_GRAVITY);
		LinearLayout.LayoutParams sl = (LinearLayout.LayoutParams) subtitle.getLayoutParams();
		sl.leftMargin = land ? px(14) : 0;
		sl.width = land ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
		sl.weight = land ? 1 : 0;
		subtitle.setLayoutParams(sl);
		headRow.setPadding(px(24), px(land ? 10 : 20), px(18), px(land ? 8 : 16));
		footMode = land ? (tab ? (two ? 2 : 0) : 1) : 0;
		oneRow = null;
		moreBtn = fgrip = null;
		ViewGroup fp = (ViewGroup) foot.getParent();
		if (footMode == 1) {
			if (fp != this) {
				if (fp != null) fp.removeView(foot);
				addView(foot, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT));
			}
			GradientDrawable fl = new GradientDrawable();
			fl.setColor(Glass.alpha(Theme.page(dark), dark ? 0.92f : 0.95f));
			fl.setCornerRadius(28 * dp);
			fl.setStroke(Math.max(1, (int) dp), Glass.edge(dark));
			foot.setBackground(fl);
			foot.setElevation(6 * dp);
			foot.setPadding(px(8), px(4), px(4), px(4));
			content.setPadding(px(14), px(4), px(14), px(84));
		} else {
			if (fp != panel) {
				if (fp != null) fp.removeView(foot);
				panel.addView(foot);
			}
			foot.setElevation(0);
			foot.setPadding(px(10), px(4), px(10), px(8));
			content.setPadding(px(14), px(4), px(14), px(18));
		}
	}

	/**
	 * v2.152 (Viorel): at the top of ☰, a box saying a new version is ready (short); a tap on it
	 * starts the download at once.
	 */
	void updateBox(String head, String sub, final Runnable tap) {
		LinearLayout box = new LinearLayout(getContext());
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER_VERTICAL);
		box.setMinimumHeight(px(56));
		box.setPadding(px(16), px(10), px(16), px(10));
		box.setBackground(Glass.pill(Theme.pastel(dark), 0, 18 * dp, dp));
		int on = Theme.onPastel(dark);
		TextView t = role("updHead", text(getContext(), 16 * zk, true));
		t.setText(head);
		t.setTextColor(on);
		box.addView(t);
		TextView u = role("updSub", text(getContext(), 13.5f * zk, false));
		u.setText(sub);
		u.setTextColor(on);
		box.addView(u);
		box.setClickable(true);
		box.setFocusable(true);
		box.setContentDescription(head + ". " + sub);
		box.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				tap.run();
			}
		});
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.topMargin = px(12);
		content.addView(box, lp);
	}

	/** A section's title (accent, spaced capitals) and a new card under it; DASHED: the hidden test card. */
	void section(String name, boolean dashed) {
		TextView t = role("section", text(getContext(), 12.5f * zk, true));
		t.setText(name.toUpperCase());
		t.setTextColor(accentText());
		t.setLetterSpacing(0.1f);
		t.setPadding(px(10), px(16), px(10), px(8));
		LinearLayout to = target();
		to.addView(t);
		card = new LinearLayout(getContext());
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(px(14), px(6), px(14), px(6));
		if (dashed) {
			card.setBackground(new Dashed());
		} else card.setBackground(Glass.cardBg(dark, 22 * dp, dp));
		to.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
		// v5.01 (Viorel): a row under another (Parcele, Cutremure recente) lines up with it.
		r.setPadding(px(4), px(indent ? 4 : 10), px(4), px(10));
		r.setMinimumHeight(px(54 * zk));
		if (icon != null) {
			int ts = px(38 * zk);
			LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ts, ts);
			tl.rightMargin = px(14);
			r.addView(tile(icon, indent), tl);
		}
		LinearLayout tx = new LinearLayout(c);
		tx.setOrientation(LinearLayout.VERTICAL);
		TextView t = role("head", text(c, 16 * zk, true));
		t.setText(head);
		t.setTextColor(ink());
		tx.addView(t);
		if (sub != null && sub.length() > 0) {
			// v2.010 (Viorel): the grey line on 2 rows at most, smaller when it does not fit.
			FitText s = new FitText(c, 2, 0.75f);
			s.setTextSize(12.5f * zk);
			s.setTypeface(reg);
			s.setIncludeFontPadding(true);
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
			TextView t = text(getContext(), 14.5f * zk, false);
			t.setText(v);
			t.setTextColor(dim());
			t.setSingleLine(true);
			t.setMaxWidth(px(130 * zk));
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
		LinearLayout r = row(icon, head, sub, null, false, tap);
		LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(px(48 * zk), px(28 * zk));
		rl.leftMargin = px(10);
		r.addView(sw, rl);
	}

	/** v2.190 (Viorel): a row with its switch greyed out: a function to come (it does nothing yet). */
	void toggleOff(String icon, String head, String sub) {
		sep();
		Switch sw = new Switch(false);
		LinearLayout r = row(icon, head, sub, null, false, null);
		LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(px(48 * zk), px(28 * zk));
		rl.leftMargin = px(10);
		r.addView(sw, rl);
		r.setAlpha(0.45f);
		r.setEnabled(false);
		r.setContentDescription(head + ". " + sub);
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
			TextView t = role(wide ? "seg" : "segs", text(getContext(), 14.5f * zk, true));
			t.setText(opts[i]);
			t.setGravity(Gravity.CENTER);
			t.setPadding(px(wide ? 6 : 12), px(6), px(wide ? 6 : 12), px(6));
			t.setTextColor(i == sel ? onAccent() : dim());
			if (i == sel) t.setBackground(Glass.pill(Theme.pastel(dark), 0, 11 * dp, dp));
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

	/**
	 * v5.53 (Viorel): the languages as buttons, 2 on a row: the language's code in a frame and its
	 * name; the chosen one filled in the theme's colour.
	 */
	void langButtons(String[] codes, String[] names, int sel, final Choice pick) {
		for (int i = 0; i < codes.length; i += 2) {
			LinearLayout r = new LinearLayout(getContext());
			for (int j = i; j < i + 2; j++) {
				LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, px(44 * zk), 1);
				if (j > i) bl.leftMargin = px(10);
				if (j >= codes.length) {
					r.addView(new View(getContext()), bl);
					continue;
				}
				final int k = j;
				boolean on = j == sel;
				LinearLayout b = new LinearLayout(getContext());
				b.setGravity(Gravity.CENTER);
				b.setPadding(px(8), 0, px(8), 0);
				b.setBackground(on ? Glass.pill(Theme.pastel(dark), 0, 22 * dp * zk, dp) : Glass.pill(Glass.disc(dark), Glass.edge(dark), 22 * dp * zk, dp));
				TextView code = text(getContext(), 10 * zk, true);
				code.setText(codes[j].toUpperCase(java.util.Locale.ROOT));
				code.setTextColor(on ? onAccent() : dim());
				code.setPadding(px(4), 0, px(4), 0);
				code.setBackground(Glass.pill(0, on ? onAccent() : dim(), 4 * dp, dp));
				b.addView(code);
				TextView name = role("lang", text(getContext(), 15 * zk, on));
				name.setText(names[j]);
				name.setTextColor(on ? onAccent() : ink());
				LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				nl.leftMargin = px(8);
				b.addView(name, nl);
				b.setContentDescription(names[j]);
				b.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						pick.chose(k);
					}
				});
				r.addView(b, bl);
			}
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			rl.bottomMargin = px(10);
			rl.leftMargin = rl.rightMargin = px(4);
			card.addView(r, rl);
		}
	}

	/** v5.53: a wide button (Other languages): MARKED in the theme's colour, its arrow down or (OPEN) up. */
	void wideButton(String label, boolean marked, boolean open, final Runnable tap) {
		LinearLayout b = new LinearLayout(getContext());
		b.setGravity(Gravity.CENTER_VERTICAL);
		b.setPadding(px(16), 0, px(12), 0);
		// v6.00 (Viorel): a minorities' language chosen: the button stays selected (filled).
		b.setBackground(marked ? Glass.pill(Theme.pastel(dark), 0, 22 * dp * zk, dp) : Glass.pill(Glass.disc(dark), Glass.edge(dark), 22 * dp * zk, dp));
		TextView t = role("wide", text(getContext(), 15 * zk, marked));
		t.setText(label);
		t.setTextColor(marked ? onAccent() : ink());
		b.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		View ch = new View(getContext());
		ch.setBackground(new LineIcon("chevron", marked ? onAccent() : dim()));
		ch.setRotation(open ? -90 : 90);
		b.addView(ch, new LinearLayout.LayoutParams(px(22), px(22)));
		b.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				tap.run();
			}
		});
		LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(44 * zk));
		rl.bottomMargin = px(10);
		rl.leftMargin = rl.rightMargin = px(4);
		card.addView(b, rl);
	}

	/** v5.53: a row with only its icon, name and (v6.00) a picture at the right, as the current language's icon (no tap). */
	void info(String icon, String head, android.graphics.Bitmap pic) {
		sep();
		android.widget.ImageView v = new android.widget.ImageView(getContext());
		v.setImageBitmap(pic);
		int s = px(30 * zk);
		v.setMinimumWidth(s);
		v.setMinimumHeight(s);
		v.setAdjustViewBounds(true);
		v.setMaxWidth(s);
		v.setMaxHeight(s);
		row(icon, head, null, v, false, null);
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
			// v5.01 (Viorel): no names under the colours (the chosen one is in the row above).
			col.addView(d, new LinearLayout.LayoutParams(px(38 * zk), px(38 * zk)));
			col.setContentDescription(names[i]);
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

	/** v5.20 (Viorel): a row with its name, the chosen one under it, and coloured dots to choose (as the theme's colours). */
	void dots(String icon, String head, String sub, String[] names, int[] colors, int sel, final Choice pick) {
		sep();
		row(icon, head, sub, null, false, null);
		LinearLayout dots = new LinearLayout(getContext());
		dots.setPadding(0, 0, 0, px(12));
		for (int i = 0; i < names.length; i++) {
			final int k = i;
			FrameLayout col = new FrameLayout(getContext());
			Dot d = new Dot(colors[i], i == sel);
			FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(px(38 * zk), px(38 * zk));
			dl.gravity = Gravity.CENTER;
			col.addView(d, dl);
			col.setContentDescription(names[i]);
			col.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					pick.chose(k);
				}
			});
			dots.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		}
		card.addView(dots);
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
		TextView t = role("favhead", text(getContext(), 14.5f * zk, true));
		t.setText(name);
		t.setTextColor(dim());
		r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		if (add != null) {
			TextView a = text(getContext(), 14.5f * zk, true);
			a.setSingleLine(true);
			a.setText(add.endsWith("›") ? add : "+ " + add);
			a.setTextColor(accentText());
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
		// v5.03 (Viorel): the names as big as the function titles.
		TextView t = role("head", text(getContext(), 16 * zk, true));
		t.setText(name);
		t.setTextColor(shown ? ink() : dim());
		r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		r.setBackgroundResource(android.R.drawable.list_selector_background);
		r.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				tap.run();
			}
		});
		card.addView(r);
	}

	/**
	 * v2.152 (Viorel): a favourite with its star: filled = it has a main screen. The star turns it
	 * on or off (v2.190: never the last one, LOCKED, grey); the name opens its screen. CURRENT: the screen shown.
	 */
	void favStar(String name, final boolean starred, boolean locked, boolean current, final Runnable tapName, final Runnable tapStar) {
		LinearLayout r = new LinearLayout(getContext());
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setMinimumHeight(px(52));
		r.setPadding(0, px(2), px(4), px(2));
		View st = new View(getContext());
		// v2.190 (Viorel): the last starred town: a grey star that cannot be unticked.
		st.setBackground(new LineIcon(starred ? "star" : "star-off", starred && !locked ? accent() : dim()));
		FrameLayout hit = new FrameLayout(getContext());
		FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(px(24 * zk), px(24 * zk), Gravity.CENTER);
		hit.addView(st, il);
		hit.setContentDescription(name + ", " + (starred ? Tr.t("has a main screen", "are ecran principal") : Tr.t("no main screen", "fără ecran principal"))
				+ (locked ? ". " + Tr.t("At least one town stays shown.", "Măcar o localitate rămâne afișată.") : ""));
		if (!locked) {
			hit.setBackgroundResource(android.R.drawable.list_selector_background);
			hit.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					tapStar.run();
				}
			});
		}
		r.addView(hit, new LinearLayout.LayoutParams(px(48), px(48)));
		TextView t = role("head", text(getContext(), 16 * zk, true));
		t.setText(name);
		t.setTextColor(starred ? ink() : dim());
		if (current) t.setTypeface(Fonts.bold(getContext()));
		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		tl.leftMargin = px(6);
		r.addView(t, tl);
		r.setBackgroundResource(android.R.drawable.list_selector_background);
		r.setContentDescription(name + (current ? ", " + Tr.t("shown now", "afișată acum") : ""));
		r.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				tapName.run();
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
			TextView t = text(getContext(), 14.5f * zk, false);
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
		TextView t = text(getContext(), 12.5f * zk, false);
		t.setText(s);
		t.setTextColor(dim());
		t.setPadding(px(6), px(6), px(6), px(12));
		card.addView(t);
	}

	/** The fixed bar's title. */
	void footTitle(String s) {
		footTitle.setText(s.toUpperCase());
		footTitle.setTextColor(accentText());
		footTitle.setTextSize(12.5f * zk);
		role("foottitle", footTitle);
		ViewGroup tp = (ViewGroup) footTitle.getParent();
		if (tp != null) tp.removeView(footTitle);
		if (footMode == 1) {
			floatBar();
			return;
		}
		if (footMode == 2) {
			// v2.200 (Viorel): tablet, two columns: the title and the buttons on one row.
			oneRow = new LinearLayout(getContext());
			oneRow.setGravity(Gravity.CENTER_VERTICAL);
			footTitle.setPadding(px(14), 0, px(10), 0);
			oneRow.addView(footTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			foot.addView(oneRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			return;
		}
		footTitle.setPadding(px(14), 0, 0, px(2));
		// A small handle: pull the bar up for the names, down for only the icons.
		grab = new View(getContext());
		grab.setBackground(Glass.pill(Glass.alpha(ink(), 0.3f), 0, 3 * dp, dp));
		LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(px(40), px(5));
		gl.gravity = Gravity.CENTER_HORIZONTAL;
		gl.topMargin = px(2);
		gl.bottomMargin = px(6);
		foot.addView(grab, gl);
		foot.addView(footTitle);
		compact = new LinearLayout(getContext());
		compact.setGravity(Gravity.CENTER_VERTICAL);
		compact.setVisibility(footOpen ? GONE : VISIBLE);
		foot.addView(compact);
	}

	/** A row of the fixed bar: plain icons with their name under them. ICONS: one name, or "a>b" for two with › between. */
	void footRow(String[] icons, String[] labels, final Runnable[] taps) {
		LinearLayout r = footMode == 2 && oneRow != null ? oneRow : new LinearLayout(getContext());
		r.setGravity(footMode == 2 ? Gravity.CENTER_VERTICAL : Gravity.TOP);
		for (int i = 0; i < icons.length; i++) {
			final int k = i;
			LinearLayout col = new LinearLayout(getContext());
			col.setOrientation(LinearLayout.VERTICAL);
			col.setGravity(Gravity.CENTER_HORIZONTAL);
			col.setPadding(px(2), px(5), px(2), px(5));
			int is = px(26 * zk);
			LinearLayout ic = new LinearLayout(getContext());
			ic.setGravity(Gravity.CENTER);
			// v5.20 (Viorel): the bar's animated icons (Export / Import: one icon with both arrows).
			String[] parts = AnimIcon.file(icons[i]) != null ? new String[] { icons[i] } : icons[i].split(">");
			for (int j = 0; j < parts.length; j++) {
				if (j > 0) {
					View ch = new View(getContext());
					ch.setBackground(new LineIcon("chevron", dim()));
					ic.addView(ch, new LinearLayout.LayoutParams(px(13 * zk), px(13 * zk)));
				}
				View v = new View(getContext());
				v.setBackground(AnimIcon.of(getContext().getAssets(), parts[j], accent()));
				ic.addView(v, new LinearLayout.LayoutParams(is, is));
			}
			col.addView(ic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, is));
			TextView t = role("foot", text(getContext(), 12 * zk, false));
			t.setText(labels[i]);
			t.setTextColor(ink());
			t.setGravity(Gravity.CENTER);
			t.setPadding(0, px(4), 0, 0);
			footLabels.add(t);
			col.setContentDescription(labels[i]);
			col.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			col.setBackgroundResource(android.R.drawable.list_selector_background);
			col.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					taps[k].run();
				}
			});
			r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, icons[i].contains(">") ? 1.5f : 1));
			if (compact != null) {
				// The same in the one row: one icon each (Export / Import: two arrows).
				View v = new View(getContext());
				v.setBackground(AnimIcon.of(getContext().getAssets(), icons[i].contains(">") ? "transfer" : icons[i], accent()));
				FrameLayout cell = new FrameLayout(getContext());
				cell.setPadding(0, px(8), 0, px(8));
				cell.setContentDescription(labels[i]);
				FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(is, is);
				vl.gravity = Gravity.CENTER;
				cell.addView(v, vl);
				cell.setBackgroundResource(android.R.drawable.list_selector_background);
				cell.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						taps[k].run();
					}
				});
				if (footMode == 1) compact.addView(cell, new LinearLayout.LayoutParams(px(46 * zk), ViewGroup.LayoutParams.WRAP_CONTENT));
				else compact.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
			}
		}
		if (footMode != 0) return; // the floating box: only the icons; the tablet: one row already in place
		r.setVisibility(footOpen ? VISIBLE : GONE);
		footRows.add(r);
		foot.addView(r);
	}

	/** v2.200 (Viorel): phone landscape: the floating box: [⋯] or the title and the icons, then its grip. */
	private void floatBar() {
		LinearLayout line = new LinearLayout(getContext());
		line.setGravity(Gravity.CENTER_VERTICAL);
		TextView more = text(getContext(), 20 * zk, true);
		more.setText("⋯");
		more.setGravity(Gravity.CENTER);
		more.setTextColor(accent());
		more.setBackground(Glass.pill(Glass.disc(dark), 0, 22 * dp, dp));
		more.setContentDescription(footTitle.getText());
		more.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				removeCallbacks(footFold);
				setFootOpen(true);
			}
		});
		moreBtn = more;
		line.addView(more, new LinearLayout.LayoutParams(px(44), px(44)));
		footTitle.setPadding(px(10), 0, px(6), 0);
		line.addView(footTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		compact = new LinearLayout(getContext());
		compact.setGravity(Gravity.CENTER_VERTICAL);
		line.addView(compact, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		// the grip: a tap or a pull to the right widens the box, to the left folds it
		fgrip = new View(getContext()) {
			float x0;
			boolean done;

			@Override
			public boolean onTouchEvent(MotionEvent e) {
				switch (e.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					x0 = e.getRawX();
					done = false;
					return true;
				case MotionEvent.ACTION_MOVE:
					float d = e.getRawX() - x0;
					if (!done && Math.abs(d) > px(20)) {
						done = true;
						removeCallbacks(footFold);
						setFootOpen(d > 0);
					}
					return true;
				case MotionEvent.ACTION_UP:
					if (!done) {
						removeCallbacks(footFold);
						setFootOpen(!footOpen);
					}
					return true;
				}
				return true;
			}
		};
		fgrip.setBackground(Glass.pill(Glass.alpha(ink(), 0.35f), 0, 3 * dp, dp));
		fgrip.setContentDescription(footTitle.getText());
		LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(px(6), px(30));
		gl.leftMargin = px(8);
		gl.rightMargin = px(8);
		line.addView(fgrip, gl);
		foot.addView(line);
		floatShow();
	}

	private void floatShow() {
		if (moreBtn == null) return;
		moreBtn.setVisibility(footOpen ? GONE : VISIBLE);
		footTitle.setVisibility(footOpen ? VISIBLE : GONE);
		if (compact != null) compact.setVisibility(footOpen ? VISIBLE : GONE);
	}

	/** Opens (or, already open, shows the new content keeping the scroll). */
	void show() {
		View pv = (View) getParent();
		int sw = pv != null && pv.getWidth() > 0 ? pv.getWidth() - pv.getPaddingLeft() - pv.getPaddingRight() : getResources().getDisplayMetrics().widthPixels;
		int sh = pv != null && pv.getHeight() > 0 ? pv.getHeight() - pv.getPaddingTop() - pv.getPaddingBottom() : getResources().getDisplayMetrics().heightPixels;
		int m = px(14);
		int w = Math.min(sw - 2 * m, sw < sh ? sw : Math.round(Math.min(sw * 0.62f, 620 * dp * zk)));
		// v5.53 (Viorel): on a tablet only as wide as needed, at the left edge.
		boolean tab = TextSize.tablet(getContext());
		if (tab) w = Math.min(sw - 2 * m, Math.round(430 * dp * zk));
		// v2.190 (Viorel): landscape (phone and tablet): at the left edge; two columns only as wide as they need.
		if (land) w = Math.min(sw - 2 * m - px(30), Math.round((two ? 760 : (tab ? 430 : 400)) * dp * zk));
		boolean left = tab || land;
		LayoutParams lp = (LayoutParams) panel.getLayoutParams();
		lp.width = w;
		lp.height = sh - 2 * m;
		lp.gravity = left ? Gravity.LEFT | Gravity.CENTER_VERTICAL : Gravity.CENTER;
		lp.leftMargin = left ? m : 0;
		panel.setLayoutParams(lp);
		if (footMode == 1) {
			LayoutParams fl = (LayoutParams) foot.getLayoutParams();
			fl.gravity = Gravity.BOTTOM | Gravity.LEFT;
			fl.leftMargin = m + px(12);
			fl.bottomMargin = m + px(12);
			foot.setLayoutParams(fl);
			foot.bringToFront();
		}
		cgrip.setVisibility(land ? VISIBLE : GONE);
		twoBtn.setVisibility(land && !two ? VISIBLE : GONE);
		if (land && !two) {
			twoBtn.setContentDescription(ro.meteoalarm.android.core.Tr.t("Two columns", "Două coloane"));
			android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(new Drawable[] { Glass.pill(Glass.disc(dark), 0, 21 * dp, dp), new LineIcon("cols", ink()) });
			ld.setLayerInset(0, 0, 0, 0, 0);
			int in = px(10);
			ld.setLayerInset(1, in, in, in, in);
			twoBtn.setBackground(ld);
		}
		if (land) {
			cgrip.setBackground(new Drawable() {
				final Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);

				@Override
				public void draw(Canvas c) {
					android.graphics.Rect b = getBounds();
					gp.setColor(Glass.alpha(ink(), 0.35f));
					float cx = b.exactCenterX();
					c.drawRoundRect(new RectF(cx - 2.5f * dp, b.top + px(20), cx + 2.5f * dp, b.bottom - px(20)), 3 * dp, 3 * dp, gp);
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
			});
			cgrip.setContentDescription(two ? ro.meteoalarm.android.core.Tr.t("One column", "O coloană") : ro.meteoalarm.android.core.Tr.t("Two columns", "Două coloane"));
			LayoutParams gl = (LayoutParams) cgrip.getLayoutParams();
			gl.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
			gl.leftMargin = m + w - px(4);
			cgrip.setLayoutParams(gl);
		}
		if (!open) {
			AnimIcon.still = Motion.off(panel.getContext()); // design audit: "Remove animations"
			AnimIcon.restart(); // v5.20: the bar's icons move for 3 seconds when the window opens
			scroll.scrollTo(0, 0);
			// v2.190 (Viorel): the bar opens with the names for 3 seconds, then only its icons.
			footOpen = false;
			setFootOpen(true);
			removeCallbacks(footFold);
			postDelayed(footFold, 3000);
		}
		open = true;
		fitPending = true;
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
