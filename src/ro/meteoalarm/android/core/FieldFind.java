package ro.meteoalarm.android.core;

/**
 * v6.00 (Viorel): a parcel's 4 corners guessed from the satellite picture around the pin. From the
 * pin the field is grown over the pixels of the same colour (smoothed), stopping at strong edges
 * (roads, tree rows, another crop); the grown area's outline is then fitted with the largest
 * 4-cornered shape. Only a proposal: the farmer moves the corners when they are wrong.
 */
public final class FieldFind {
	private FieldFind() {
	}

	/**
	 * ARGB pixels of a W×H picture and the pin at SX, SY: the 4 corners (x, y × 4, clockwise from
	 * the top left), or null when no field stands out (it runs to the picture's edge or is tiny).
	 */
	public static float[] find(int[] argb, int w, int h, int sx, int sy) {
		if (sx < 2 || sy < 2 || sx >= w - 2 || sy >= h - 2) return null;
		int n = w * h;
		float[] r = new float[n], g = new float[n], b = new float[n];
		for (int i = 0; i < n; i++) {
			int c = argb[i];
			r[i] = (c >> 16) & 255;
			g[i] = (c >> 8) & 255;
			b[i] = c & 255;
		}
		// Smoothed twice (5×5 box): the rows of a crop and the noise melt into the field's colour.
		for (int k = 0; k < 2; k++) {
			r = box(r, w, h, 2);
			g = box(g, w, h, 2);
			b = box(b, w, h, 2);
		}
		// The edges: the colour's change across a pixel.
		float[] edge = new float[n];
		for (int y = 1; y < h - 1; y++)
			for (int x = 1; x < w - 1; x++) {
				int i = y * w + x;
				float dx = Math.abs(r[i + 1] - r[i - 1]) + Math.abs(g[i + 1] - g[i - 1]) + Math.abs(b[i + 1] - b[i - 1]);
				float dy = Math.abs(r[i + w] - r[i - w]) + Math.abs(g[i + w] - g[i - w]) + Math.abs(b[i + w] - b[i - w]);
				edge[i] = Math.max(dx, dy);
			}
		boolean[] best = null;
		int bestArea = 0, prevArea = 0;
		for (float lim = 12; lim <= 40; lim += 4) {
			int[] area = new int[1];
			boolean[] in = grow(r, g, b, edge, w, h, sx, sy, lim, lim * 2.2f, area);
			if (in == null) break; // ran to the edge: the field does not stand out at this limit
			if (prevArea > 0 && area[0] > prevArea * 2.2f) break; // leaked into the next field
			if (area[0] >= n / 400) {
				best = in;
				bestArea = area[0];
			}
			prevArea = area[0];
		}
		if (best == null || bestArea < n / 400) return null;
		// Thin bridges into a neighbour (a gap in a tree row, a track) cut off: eroded, the seed's
		// piece kept, grown back.
		int er = Math.max(2, Math.min(w, h) / 80);
		boolean[] open = dilate(seedPart(erode(best, w, h, er), w, h, sx, sy), w, h, er);
		if (open != null) {
			for (int i = 0; i < n; i++) open[i] &= best[i];
			best = open;
		}
		// The outline: the first and the last pixel of each row, then their convex hull.
		int[] px = new int[2 * h], py = new int[2 * h];
		int m = 0;
		for (int y = 0; y < h; y++) {
			int a = -1, z = -1;
			for (int x = 0; x < w; x++)
				if (best[y * w + x]) {
					if (a < 0) a = x;
					z = x;
				}
			if (a >= 0) {
				px[m] = a;
				py[m++] = y;
				px[m] = z;
				py[m++] = y;
			}
		}
		int[][] hull = hull(px, py, m);
		int k = hull[0].length;
		if (k < 4) return null;
		int[] q = quad(hull[0], hull[1]);
		float[] out = new float[8];
		for (int i = 0; i < 4; i++) {
			out[2 * i] = hull[0][q[i]];
			out[2 * i + 1] = hull[1][q[i]];
		}
		return order(out);
	}

	/** The pixels whose whole square of radius R is inside. */
	private static boolean[] erode(boolean[] in, int w, int h, int r) {
		return square(in, w, h, r, true);
	}

	private static boolean[] dilate(boolean[] in, int w, int h, int r) {
		return in == null ? null : square(in, w, h, r, false);
	}

	/** ALL: every pixel of the square is in (erosion); else any (dilation). Separable: rows, then columns. */
	private static boolean[] square(boolean[] in, int w, int h, int r, boolean all) {
		boolean[] t = new boolean[in.length], o = new boolean[in.length];
		for (int y = 0; y < h; y++) {
			int c = 0;
			for (int x = -r; x < w; x++) {
				int add = x + r, del = x - r - 1;
				if (add < w && in[y * w + add]) c++;
				if (del >= 0 && in[y * w + del]) c--;
				if (x >= 0) {
					int span = Math.min(w - 1, x + r) - Math.max(0, x - r) + 1;
					t[y * w + x] = all ? c == span : c > 0;
				}
			}
		}
		for (int x = 0; x < w; x++) {
			int c = 0;
			for (int y = -r; y < h; y++) {
				int add = y + r, del = y - r - 1;
				if (add < h && t[add * w + x]) c++;
				if (del >= 0 && t[del * w + x]) c--;
				if (y >= 0) {
					int span = Math.min(h - 1, y + r) - Math.max(0, y - r) + 1;
					o[y * w + x] = all ? c == span : c > 0;
				}
			}
		}
		return o;
	}

	/** The piece of IN joined to the seed (or to its nearest pixel within 20), null when none. */
	private static boolean[] seedPart(boolean[] in, int w, int h, int sx, int sy) {
		int s = -1;
		for (int d = 0; d <= 20 && s < 0; d++)
			for (int y = sy - d; y <= sy + d && s < 0; y++)
				for (int x = sx - d; x <= sx + d; x++)
					if (x >= 0 && y >= 0 && x < w && y < h && in[y * w + x]) {
						s = y * w + x;
						break;
					}
		if (s < 0) return null;
		boolean[] out = new boolean[in.length];
		int[] st = new int[in.length];
		int sp = 0;
		out[s] = true;
		st[sp++] = s;
		while (sp > 0) {
			int i = st[--sp], x = i % w;
			int[] nb = { x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, i - w, i + w };
			for (int j : nb)
				if (j >= 0 && j < in.length && in[j] && !out[j]) {
					out[j] = true;
					st[sp++] = j;
				}
		}
		return out;
	}

	/** A box blur of radius R. */
	private static float[] box(float[] v, int w, int h, int rad) {
		float[] t = new float[v.length], o = new float[v.length];
		for (int y = 0; y < h; y++) {
			float s = 0;
			int c = 0;
			for (int x = -rad; x < w; x++) {
				int add = x + rad, del = x - rad - 1;
				if (add < w) {
					s += v[y * w + add];
					c++;
				}
				if (del >= 0) {
					s -= v[y * w + del];
					c--;
				}
				if (x >= 0) t[y * w + x] = s / c;
			}
		}
		for (int x = 0; x < w; x++) {
			float s = 0;
			int c = 0;
			for (int y = -rad; y < h; y++) {
				int add = y + rad, del = y - rad - 1;
				if (add < h) {
					s += t[add * w + x];
					c++;
				}
				if (del >= 0) {
					s -= t[del * w + x];
					c--;
				}
				if (y >= 0) o[y * w + x] = s / c;
			}
		}
		return o;
	}

	/** The pixels joined to the seed, near the seed's colour (LIM) and off strong edges (EDGE); null when it reaches the border. */
	private static boolean[] grow(float[] r, float[] g, float[] b, float[] edge, int w, int h, int sx, int sy, float lim, float edgeLim, int[] area) {
		int n = w * h;
		float mr = 0, mg = 0, mb = 0;
		int c = 0;
		for (int y = sy - 2; y <= sy + 2; y++)
			for (int x = sx - 2; x <= sx + 2; x++) {
				int i = y * w + x;
				mr += r[i];
				mg += g[i];
				mb += b[i];
				c++;
			}
		mr /= c;
		mg /= c;
		mb /= c;
		boolean[] in = new boolean[n];
		int[] stack = new int[n];
		int sp = 0, cnt = 0;
		int s0 = sy * w + sx;
		in[s0] = true;
		stack[sp++] = s0;
		while (sp > 0) {
			int i = stack[--sp];
			cnt++;
			int x = i % w, y = i / w;
			if (x == 0 || y == 0 || x == w - 1 || y == h - 1) return null;
			int[] nb = { i - 1, i + 1, i - w, i + w };
			for (int j : nb) {
				if (in[j]) continue;
				float dr = r[j] - mr, dg = g[j] - mg, db = b[j] - mb;
				if (dr * dr + dg * dg + db * db > lim * lim) continue;
				if (edge[j] > edgeLim) continue;
				in[j] = true;
				stack[sp++] = j;
			}
		}
		area[0] = cnt;
		return in;
	}

	/** The convex hull (monotone chain) of the first M points: {xs, ys} counter-clockwise. */
	private static int[][] hull(int[] px, int[] py, int m) {
		Integer[] idx = new Integer[m];
		for (int i = 0; i < m; i++) idx[i] = i;
		final int[] fx = px, fy = py;
		java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
			public int compare(Integer a, Integer b) {
				return fx[a] != fx[b] ? fx[a] - fx[b] : fy[a] - fy[b];
			}
		});
		int[] hx = new int[2 * m + 1], hy = new int[2 * m + 1];
		int k = 0;
		for (int t = 0; t < m; t++) {
			int i = idx[t];
			while (k >= 2 && cross(hx[k - 2], hy[k - 2], hx[k - 1], hy[k - 1], px[i], py[i]) <= 0) k--;
			hx[k] = px[i];
			hy[k++] = py[i];
		}
		for (int t = m - 2, lo = k + 1; t >= 0; t--) {
			int i = idx[t];
			while (k >= lo && cross(hx[k - 2], hy[k - 2], hx[k - 1], hy[k - 1], px[i], py[i]) <= 0) k--;
			hx[k] = px[i];
			hy[k++] = py[i];
		}
		k = Math.max(1, k - 1);
		return new int[][] { java.util.Arrays.copyOf(hx, k), java.util.Arrays.copyOf(hy, k) };
	}

	private static long cross(long ax, long ay, long bx, long by, long cx, long cy) {
		return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
	}

	/** 4 of the hull's points with the largest area between them (the field's corners). */
	private static int[] quad(int[] x, int[] y) {
		int k = x.length;
		// A start: the farthest points along the two diagonals.
		int[] q = new int[4];
		long[] best = { Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE };
		for (int i = 0; i < k; i++) {
			long[] s = { -(x[i] + y[i]), x[i] - y[i], x[i] + y[i], -(x[i] - y[i]) };
			for (int j = 0; j < 4; j++) if (s[j] > best[j]) {
				best[j] = s[j];
				q[j] = i;
			}
		}
		java.util.Arrays.sort(q);
		// Better corners, one at a time, between their neighbours.
		for (int round = 0; round < 4; round++) {
			boolean moved = false;
			for (int j = 0; j < 4; j++) {
				int a = q[(j + 3) % 4], c = q[(j + 1) % 4];
				double bestA = area(x, y, q), keep = q[j];
				for (int i = (a + 1) % k; i != c; i = (i + 1) % k) {
					q[j] = i;
					int[] s = q.clone();
					java.util.Arrays.sort(s);
					double ar = area(x, y, s);
					if (ar > bestA + 1e-6) {
						bestA = ar;
						keep = i;
						moved = true;
					}
				}
				q[j] = (int) keep;
			}
			java.util.Arrays.sort(q);
			if (!moved) break;
		}
		return q;
	}

	private static double area(int[] x, int[] y, int[] q) {
		double s = 0;
		for (int i = 0; i < 4; i++) {
			int a = q[i], b = q[(i + 1) % 4];
			s += (double) x[a] * y[b] - (double) x[b] * y[a];
		}
		return Math.abs(s) / 2;
	}

	/** The corners clockwise on the screen, the top-left one (smallest x + y) first. */
	private static float[] order(float[] c) {
		float mx = 0, my = 0;
		for (int i = 0; i < 4; i++) {
			mx += c[2 * i] / 4;
			my += c[2 * i + 1] / 4;
		}
		final float fx = mx, fy = my;
		Integer[] idx = { 0, 1, 2, 3 };
		final float[] cc = c;
		java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
			public int compare(Integer a, Integer b) {
				return Double.compare(Math.atan2(cc[2 * a + 1] - fy, cc[2 * a] - fx), Math.atan2(cc[2 * b + 1] - fy, cc[2 * b] - fx));
			}
		});
		int first = 0;
		float bs = Float.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			float s = c[2 * idx[i]] + c[2 * idx[i] + 1];
			if (s < bs) {
				bs = s;
				first = i;
			}
		}
		float[] o = new float[8];
		for (int i = 0; i < 4; i++) {
			int j = idx[(first + i) % 4];
			o[2 * i] = c[2 * j];
			o[2 * i + 1] = c[2 * j + 1];
		}
		return o;
	}
}
