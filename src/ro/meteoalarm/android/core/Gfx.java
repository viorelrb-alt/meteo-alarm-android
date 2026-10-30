package ro.meteoalarm.android.core;

/**
 * Drawing surface in canvas units (the screen is 758 units wide). Text sizes are FBInk px as on
 * the Kindle: the line height, about 1.12 x the em size, with the baseline 0.81 px below TOP.
 */
public interface Gfx {
	int BLACK = 0xFF000000, WHITE = 0xFFFFFFFF, GRAYD = 0xFFDDDDDD, GRAYA = 0xFFAAAAAA, GRAY5 = 0xFF555555;

	void rect(float x, float y, float w, float h, int color);

	void roundRect(float x, float y, float w, float h, float r, int color);

	/** Text with its top (line top, not the glyph top) at TOP and its left edge at LEFT. */
	void text(boolean bold, float px, float top, float left, String s, int color);

	float width(boolean bold, float px, String s);

	/**
	 * A black-on-white image of the app's assets (icons/…, ui/…) scaled to W x H and drawn in
	 * COLOR: the dark pixels are the shape, white is see-through.
	 */
	void icon(String asset, float x, float y, float w, float h, int color);

	void arc(float cx, float cy, float r, float startDeg, float sweepDeg, float stroke, int color);

	void poly(float[] xy, int color);
}
