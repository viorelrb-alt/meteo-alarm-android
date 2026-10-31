package ro.meteoalarm.android.core;

/**
 * A row (or column) of boxes moved by the finger: how far it is scrolled, the farthest it goes,
 * the positions it settles on, and where it is on the screen (canvas units: x, y, w, h).
 */
public final class Strip {
	public float scroll, max;
	public float[] snaps = new float[] { 0 };
	public final float[] area = { 0, 0, 0, 0 };
	public boolean vertical;

	public Strip(boolean vertical) {
		this.vertical = vertical;
	}

	/** Snap positions from box starts: 0, then each START, clamped to the end (MAX). */
	void set(float[] starts, float max) {
		this.max = Math.max(0, max);
		float[] s = new float[starts.length + 1];
		int n = 0;
		for (float v : starts) {
			float c = Math.min(v, this.max);
			if (n == 0 || c > s[n - 1] + 0.5f) s[n++] = c;
		}
		if (n == 0 || s[n - 1] < this.max - 0.5f) s[n++] = this.max;
		snaps = new float[n];
		System.arraycopy(s, 0, snaps, 0, n);
		scroll = Math.max(0, Math.min(scroll, this.max));
	}

	/** Where to settle from FROM with a fling of V units a second (positive: forward). */
	public float target(float from, float v) {
		int best = 0;
		for (int k = 1; k < snaps.length; k++) if (Math.abs(snaps[k] - from) < Math.abs(snaps[best] - from)) best = k;
		if (Math.abs(v) > 300) {
			if (v > 0 && snaps[best] <= from && best + 1 < snaps.length) best++;
			else if (v < 0 && snaps[best] >= from && best > 0) best--;
		}
		return snaps[best];
	}
}
