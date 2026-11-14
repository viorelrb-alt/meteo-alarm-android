package ro.meteoalarm.android.core;

/**
 * v3.70 (Viorel): the coloured weather icons (free vectors by titusurya, magnific.com), in the
 * colours of the theme. Each icon is made of parts (sun, rays, clouds, drops…) that move for 3
 * seconds when the screen opens, then the icon stays still. T runs from 0 to 1; 1 = still.
 */
public final class WxIcon {
	private WxIcon() {
	}

	/**
	 * v5.20 (Viorel): the icon theme chosen in Settings (pref "iconTheme"): 0 Clasic (drawn here,
	 * in the theme's colours), 1 Lucios, 2 Plat, 3 Realist, 4 Atmosferic, 5 Contur (pictures in
	 * assets: 20 frames over 3 seconds, the last one still). Contur is tinted with the theme's colours.
	 */
	public static int set;
	public static final String[] SETS = { "Clasic", "Lucios", "Plat", "Realist", "Atmosferic", "Contur" };
	public static final String[][] SET_NAMES = { { "Classic", "Clasic" }, { "Glossy", "Lucios" }, { "Flat", "Plat" },
			{ "Realistic", "Realist" }, { "Atmospheric", "Atmosferic" }, { "Outline", "Contur" } };
	/** The dot of each theme in Settings (Viorel: each another colour, never grey; Clasic white). */
	public static final int[] SET_DOTS = { 0xFFFFFFFF, 0xFFF5B800, 0xFFE5484D, 0xFF14B8A6, 0xFF5B5FEF, 0xFF8BD12E };
	private static final String[] DIRS = { null, "lux", "plat", "real", "atmo", "cont" };
	static final int LUX_FRAMES = 20;


	public static boolean has(String name) {
		return index(name) >= 0;
	}

	private static int index(String name) {
		for (int k = 0; k < WxParts.NAMES.length; k++)
			if (WxParts.NAMES[k].equals(name)) return k;
		return -1;
	}

	/** Where the drawing is in a 200 x 200 square: x0 y0 x1 y1, or null. */
	public static float[] box(String name) {
		for (String c : WxParts.CENTRE) {
			String[] p = c.split(" ");
			if (p[0].equals(name)) return new float[] { Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]), Float.parseFloat(p[4]) };
		}
		return null;
	}

	private static float ease(float t) {
		t = Math.max(0, Math.min(1, t));
		return (float) (0.5 - 0.5 * Math.cos(Math.PI * t));
	}

	/** Contur's inside: white under a dark outline (light theme), a deep shade of it under a light one (dark theme). */
	static int fill(int ink) {
		int r = (ink >> 16) & 255, gg = (ink >> 8) & 255, b = ink & 255;
		if (r * 299 + gg * 587 + b * 114 < 128000) return 0xFFFFFFFF;
		return 0xFF000000 | (r * 22 / 100) << 16 | (gg * 22 / 100) << 8 | (b * 22 / 100);
	}

	/** The icon NAME in the square X Y S, at the moment T (0..1), in the colours COL (sun, light, dark). */
	public static void draw(Gfx g, String name, float x, float y, float s, float t, int[] col) {
		int i = index(name);
		if (i < 0) return;
		if (set > 0 && set < DIRS.length) {
			// v5.10 / v5.20 (Viorel): the picture themes: 20 pictures over the 3 seconds, the last one still.
			int n = LUX_FRAMES, f = t >= 1 ? n - 1 : Math.max(0, Math.min(n - 1, (int) (Math.max(0, t) * n)));
			int px = s >= 70 ? 256 : 96;
			String d = DIRS[set] + "/" + name;
			g.image(d + "-" + px + ".webp", f * px, 0, px, px, x, y, s, s, 1);
			if (set == 5) {
				// Contur: the outline in the theme's strong colour, the inside in the page's colour.
				int ink = col[2];
				g.sprite(d + "-f-" + px + ".webp", f * px, 0, px, px, x, y, s, s, fill(ink), 1, 0, 0, 0);
				g.sprite(d + "-s-" + px + ".webp", f * px, 0, px, px, x, y, s, s, ink, 1, 0, 0, 0);
			}
			return;
		}
		int[] p = WxParts.PARTS[i];
		String asset = "wx/" + name + ".png";
		float k = s / WxParts.CELL;
		boolean still = t >= 1;
		t = Math.max(0, Math.min(1, t));
		double two = 2 * Math.PI * t;
		float wave = (float) Math.sin(two), env = (float) Math.sin(Math.PI * t);
		int fog = 0;
		for (int j = 0; j + 9 < p.length; j += 10) {
			int lay = p[j], role = p[j + 1], sx = p[j + 2], sy = p[j + 3], sw = p[j + 4], sh = p[j + 5];
			float dx = p[j + 6], dy = p[j + 7], px = p[j + 8], py = p[j + 9];
			float mx = 0, my = 0, rot = 0, sc = 1, al = 1;
			if (!still) {
				switch (role) {
				case 1: sc = 1 + 0.04f * (float) Math.sin(2 * two); break;
				case 2: rot = -60 * ease(t); break;
				case 12: rot = 12 * wave; break;
				case 3: mx = 6 * wave; break;
				case 4: mx = -6 * wave; break;
				case 9: rot = 8 * wave; break;
				case 10: al = 1 - 0.45f * Math.abs((float) Math.sin(two * 2 + px / 50f)) * env; sc = 0.8f + 0.2f * al; break;
				case 7: {
					int tenth = (int) (t * 10);
					al = tenth == 2 || tenth == 3 || tenth == 6 ? 1 : 0.15f;
					break;
				}
				case 5:
				case 6: {
					// Three falls through the place of the drop; the last one ends there.
					float ph = (px * 7 % 100) / 100f, v = 3 * t - ph * (1 - t) + 10.5f;
					v = v % 1f - 0.5f;
					my = v * (role == 6 ? 34 : 40);
					if (role == 5) mx = -my * 0.25f;
					else { mx = 4 * (float) Math.sin(2 * Math.PI * v * 2) * env; rot = 120 * v; }
					al = Math.min(1, (0.5f - Math.abs(v)) * 6);
					break;
				}
				case 8: mx = (fog++ % 2 == 0 ? 14 : -14) * wave; break;
				}
			}
			float w = sw * sc, h = sh * sc;
			float left = px + (dx - px) * sc + mx, top = py + (dy - py) * sc + my;
			// The bolt is cut out of the cloud: while it is dim the cloud colour fills it.
			if (role == 7 && al < 1) g.sprite(asset, sx, sy, sw, sh, x + left * k, y + top * k, w * k, h * k, col[1], 1, 0, 0, 0);
			g.sprite(asset, sx, sy, sw, sh, x + left * k, y + top * k, w * k, h * k, col[lay], al, rot, x + (px + mx) * k, y + (py + my) * k);
		}
	}
}
