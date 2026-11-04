package ro.meteoalarm.android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * The text size of the pages (Ajutor, Cutremure, Despre, Ce e nou) and of the ☰ menu bubbles
 * (Viorel 2026-11-04, v2.10): one setting saved on the device, 80–180 %, set with a bar like the
 * brightness one. On a tablet the menus start 11 % larger (30 % in v2.10, 17 % in v2.20).
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
}
