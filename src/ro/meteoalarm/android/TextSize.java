package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * The text size of the pages (Ajutor, Cutremure, Despre, Ce e nou) and of the ☰ menu bubbles
 * (Viorel 2026-11-04, v2.10): one setting saved on the device, 80–180 %, set with a bar like the
 * brightness one. On a tablet the menus start 30 % larger.
 */
final class TextSize {
	static final float MIN = 0.8f, MAX = 1.8f;

	private TextSize() {
	}

	static float get(Context c) {
		return Math.max(MIN, Math.min(MAX, Widget.prefs(c).getFloat("textScale", 1f)));
	}

	static void set(Context c, float s, boolean save) {
		s = Math.max(MIN, Math.min(MAX, s));
		Widget.prefs(c).edit().putFloat("textScale", s).apply();
		if (save) Log.i(c, "GEST", "mărimea textului " + Math.round(s * 100) + "%");
	}

	/** The bar's position 0–1 for scale S, and back. */
	static float pos(float s) {
		return (s - MIN) / (MAX - MIN);
	}

	static float scale(float pos) {
		return MIN + Math.max(0, Math.min(1, pos)) * (MAX - MIN);
	}

	/** A tablet: at least 600 dp on the short side. */
	static boolean tablet(Context c) {
		return c.getResources().getConfiguration().smallestScreenWidthDp >= 600;
	}

	/** Sets T's size to BASE sp times S and remembers BASE (for set(ViewGroup…) later). */
	static void size(TextView t, float base, float s) {
		t.setTag(R.id.widget_root, base);
		t.setTextSize(base * s);
	}

	/** Every text under V sized with size(): BASE times S. */
	static void apply(View v, float s) {
		if (v instanceof TextView) {
			Object b = v.getTag(R.id.widget_root);
			if (b instanceof Float) ((TextView) v).setTextSize((Float) b * s);
		}
		if (v instanceof ViewGroup) {
			ViewGroup g = (ViewGroup) v;
			for (int k = 0; k < g.getChildCount(); k++) apply(g.getChildAt(k), s);
		}
	}

	/**
	 * The text-size bar on a page: a rounded track filled from the bottom, a small "A" at the
	 * bottom and a large one at the top; a finger on it sets the size (Listener hears it).
	 */
	static final class Bar extends View {
		interface Listener {
			/** The new size; SAVE when the finger lifts. */
			void sized(float s, boolean save);
		}

		private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final float dp;
		private float level;
		private boolean dark;
		private Listener listener;

		Bar(Context c, Typeface bold) {
			super(c);
			dp = c.getResources().getDisplayMetrics().density;
			p.setTypeface(bold);
			p.setTextAlign(Paint.Align.CENTER);
			level = pos(get(c));
		}

		void setListener(Listener l) {
			listener = l;
		}

		void setDark(boolean d) {
			dark = d;
			invalidate();
		}

		void sync() {
			level = pos(get(getContext()));
			invalidate();
		}

		@Override
		protected void onDraw(Canvas c) {
			float w = getWidth(), h = getHeight(), r = w / 2;
			int ink = dark ? 0xFFFAF9F5 : 0xFF000000;
			p.setStyle(Paint.Style.FILL);
			p.setColor(dark ? 0x665A5954 : 0x33000000);
			c.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
			float f = w + level * (h - w);
			p.setColor(dark ? 0xFFD97757 : 0xFF555555);
			c.drawRoundRect(new RectF(0, h - f, w, h), r, r, p);
			p.setColor(dark ? 0xFF262624 : 0xFFFFFFFF);
			p.setTextSize(w * 0.42f);
			c.drawText("A", w / 2, h - w / 2 + w * 0.15f, p);
			p.setColor(ink);
			p.setTextSize(w * 0.7f);
			c.drawText("A", w / 2, w / 2 + w * 0.25f, p);
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(Math.max(1, dp));
			p.setColor(dark ? 0xFF5A5954 : 0xFF999999);
			c.drawRoundRect(new RectF(dp / 2, dp / 2, w - dp / 2, h - dp / 2), r, r, p);
		}

		@Override
		public boolean onTouchEvent(MotionEvent e) {
			int a = e.getActionMasked();
			float w = getWidth(), h = getHeight();
			level = Math.max(0, Math.min(1, (h - w / 2 - e.getY()) / Math.max(1, h - w)));
			invalidate();
			boolean up = a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL;
			if (a == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
			float s = scale(level);
			set(getContext(), s, up);
			if (listener != null) listener.sized(s, up);
			return true;
		}
	}
}
