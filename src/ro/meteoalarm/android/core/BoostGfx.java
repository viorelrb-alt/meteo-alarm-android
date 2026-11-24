package ro.meteoalarm.android.core;

/**
 * Design audit (2026-11-23): the main screen's small text follows the phone's font size and the
 * app's Aa. Text under CAP canvas units grows by K, up to CAP; larger text stays as drawn (the
 * layout keeps its places). Width is measured at the same size, so fit() still fits.
 */
final class BoostGfx implements Gfx {
	static final float CAP = 26;
	private final Gfx g;
	private final float k;

	BoostGfx(Gfx g, float k) {
		this.g = g;
		this.k = k;
	}

	private float px(float px) {
		return px >= CAP ? px : Math.min(CAP, px * k);
	}

	public void rect(float x, float y, float w, float h, int color) { g.rect(x, y, w, h, color); }

	public void roundRect(float x, float y, float w, float h, float r, int color) { g.roundRect(x, y, w, h, r, color); }

	public void text(boolean bold, float px, float top, float left, String s, int color) {
		float p = px(px);
		// Grown text keeps its middle where the small one had it.
		g.text(bold, p, top - (p - px) * 0.5f, left, s, color);
	}

	public float width(boolean bold, float px, String s) { return g.width(bold, px(px), s); }

	public void icon(String asset, float x, float y, float w, float h, int color) { g.icon(asset, x, y, w, h, color); }

	public void sprite(String asset, int sx, int sy, int sw, int sh, float x, float y, float w, float h, int color, float alpha, float rot, float px, float py) {
		g.sprite(asset, sx, sy, sw, sh, x, y, w, h, color, alpha, rot, px, py);
	}

	public void image(String asset, int sx, int sy, int sw, int sh, float x, float y, float w, float h, float alpha) {
		g.image(asset, sx, sy, sw, sh, x, y, w, h, alpha);
	}

	public void arc(float cx, float cy, float r, float startDeg, float sweepDeg, float stroke, int color) { g.arc(cx, cy, r, startDeg, sweepDeg, stroke, color); }

	public void poly(float[] xy, int color) { g.poly(xy, color); }

	public void frost(float[] xy, int tint, int edge) { g.frost(xy, tint, edge); }

	public void clip(float x, float y, float w, float h) { g.clip(x, y, w, h); }

	public void unclip() { g.unclip(); }
}
