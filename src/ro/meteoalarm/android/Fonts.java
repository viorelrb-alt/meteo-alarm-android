package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Design audit (2026-11-23): the app's one font, Liberation Sans (Regular, Bold, from assets),
 * loaded once and shared, for every TextView and Paint (no Roboto anywhere).
 */
final class Fonts {
	private Fonts() {
	}

	private static Typeface reg, bold;

	/** Loads the two faces (once), from CTX's assets. */
	static void init(Context ctx) {
		if (reg != null) return;
		try {
			reg = Typeface.createFromAsset(ctx.getAssets(), "fonts/LiberationSans-Regular.ttf");
			bold = Typeface.createFromAsset(ctx.getAssets(), "fonts/LiberationSans-Bold.ttf");
		} catch (Exception e) {
			reg = Typeface.DEFAULT;
			bold = Typeface.DEFAULT_BOLD;
		}
	}

	static Typeface reg(Context ctx) {
		init(ctx);
		return reg;
	}

	static Typeface bold(Context ctx) {
		init(ctx);
		return bold;
	}

	static Typeface of(Context ctx, boolean b) {
		return b ? bold(ctx) : reg(ctx);
	}

	/** The face for a Paint where no Context is at hand (after init; else the system's). */
	static Typeface reg() {
		return reg != null ? reg : Typeface.DEFAULT;
	}

	static Typeface bold() {
		return bold != null ? bold : Typeface.DEFAULT_BOLD;
	}

	/** Every TextView under V that still has the system's face gets Liberation Sans (bold kept). */
	static void tree(View v) {
		if (v instanceof TextView) {
			TextView t = (TextView) v;
			Typeface f = t.getTypeface();
			if (f == null || (f != reg && f != bold && f != Typeface.MONOSPACE)) t.setTypeface(of(v.getContext(), f != null && f.isBold()));
		}
		if (v instanceof ViewGroup) {
			ViewGroup g = (ViewGroup) v;
			for (int i = 0; i < g.getChildCount(); i++) tree(g.getChildAt(i));
		}
	}
}
