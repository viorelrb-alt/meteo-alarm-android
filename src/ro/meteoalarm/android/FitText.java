package ro.meteoalarm.android;

import android.content.Context;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * v2.010 (Viorel 2026-11-17): a text that keeps to LINES rows (1 or 2) without breaking a word:
 * when it does not fit its room, its size goes down (to MIN of the size it was given at most).
 * Used on the Statistics and Edit parcel pages and for the grey texts of the ☰ menu.
 */
class FitText extends TextView {
	private int lines;
	private float min;
	private float base;
	/** v2.047: at most this size in pixels (0: none), so titles of one page come out the same size. */
	float cap;

	FitText(Context c, int lines, float min) {
		super(c);
		this.lines = lines;
		this.min = min;
		if (lines == 1) setSingleLine(true);
		else if (lines < 100) setMaxLines(lines);
	}

	@Override
	public void setTextSize(int unit, float size) {
		base = TypedValue.applyDimension(unit, size, getResources().getDisplayMetrics());
		super.setTextSize(TypedValue.COMPLEX_UNIT_PX, base);
	}

	/** From now on one row (shrunk down to MIN). */
	FitText one(float min) {
		lines = 1;
		this.min = min;
		setSingleLine(true);
		setEllipsize(null);
		requestLayout();
		return this;
	}

	/** At most N rows. */
	FitText rows(int n) {
		lines = n;
		setMaxLines(n);
		requestLayout();
		return this;
	}

	/** Did the size SZ leave the text whole in AVAIL pixels? */
	boolean fits(float sz, int avail) {
		String s = getText().toString();
		TextPaint p = new TextPaint(getPaint());
		p.setTextSize(sz);
		if (lines == 1) return p.measureText(s) <= avail;
		for (String w : s.split("\\s+"))
			if (p.measureText(w) > avail) return false;
		StaticLayout l = new StaticLayout(s, p, avail, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
		return l.getLineCount() <= lines;
	}

	/** The size the text gets in AVAIL pixels. */
	float sizeFor(int avail) {
		float sz = cap > 0 ? Math.min(base, cap) : base;
		while (sz > base * min && !fits(sz, avail)) sz *= 0.95f;
		return Math.max(sz, base * min);
	}

	@Override
	protected void onMeasure(int wSpec, int hSpec) {
		int avail = MeasureSpec.getSize(wSpec) - getCompoundPaddingLeft() - getCompoundPaddingRight();
		if (base > 0 && avail > 0 && MeasureSpec.getMode(wSpec) != MeasureSpec.UNSPECIFIED) {
			float sz = sizeFor(avail);
			if (Math.abs(getTextSize() - sz) > 0.3f) super.setTextSize(TypedValue.COMPLEX_UNIT_PX, sz);
		}
		super.onMeasure(wSpec, hSpec);
	}

	/** For a group of texts that should share one size: the factor that fits them all. */
	static float common(FitText[] ts) {
		float f = 1;
		for (FitText t : ts) {
			int avail = t.getWidth() - t.getCompoundPaddingLeft() - t.getCompoundPaddingRight();
			if (avail > 0 && t.base > 0) f = Math.min(f, t.sizeFor(avail) / t.base);
		}
		return f;
	}

}
