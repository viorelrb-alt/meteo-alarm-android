package ro.meteoalarm.android.core;

/**
 * v3.60 (Viorel): the colour themes. Each theme has a light and a dark side; ☰ › Temă picks the
 * theme and, under a line, light or dark. 0 = Monocrom (the Kindle look; dark = Claude colours).
 * The 16 colours, by role: page ink dim line band card btn dry snow rain accent warnY warnO warnR
 * pillInk bandInk (as Screen.Palette).
 */
public final class Theme {
	private Theme() {
	}

	/** The theme in use (0..5), set from the settings by the app. */
	public static int pal;

	public static final String[][] NAMES = { { "Monochrome", "Monocrom" }, { "Sage", "Salvie" }, { "Lavender", "Lavandă" },
			{ "Sky", "Cer" }, { "Peach", "Piersică" }, { "Rose", "Trandafir" } };

	private static final int WY = 0xFFF4E59A, WO = 0xFFF7BE8A, WR = 0xFFF29B9B, DY = 0xFFE3B341, DO = 0xFFD97757, DR = 0xFFD14D4D;

	private static final int[][] LIGHT = {
			{ 0xFFFFFFFF, 0xFF000000, 0xFF555555, 0xFF000000, 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFF000000, 0xFF000000, 0xFFDDDDDD, 0xFFAAAAAA, 0xFFAAAAAA, 0xFF000000, 0xFFFFFFFF },
			{ 0xFFD4E3DB, 0xFF1F2A24, 0xFF546159, 0xFF2F4A3C, 0xFF3E6B55, 0xFFF0F4F2, 0xFFC2D6CB, 0xFFB9C9BE, 0xFF6E8A7A, 0xFF3E6B55, 0xFF4E8A6A, WY, WO, WR, 0xFF1F2A24, 0xFFFFFFFF },
			{ 0xFFD8CFE8, 0xFF2A2433, 0xFF5E5568, 0xFF4B3F66, 0xFF6B5B95, 0xFFF1EFF6, 0xFFC8BBDD, 0xFFCFC6DE, 0xFF8C80A8, 0xFF6B5B95, 0xFF8E6CC9, WY, WO, WR, 0xFF2A2433, 0xFFFFFFFF },
			{ 0xFFCCDCEA, 0xFF16263A, 0xFF4B5B70, 0xFF24476B, 0xFF2F6690, 0xFFEEF3F7, 0xFFB8CDE0, 0xFFBCD0E2, 0xFF7C98B5, 0xFF2F6690, 0xFF3A86C8, WY, WO, WR, 0xFF16263A, 0xFFFFFFFF },
			{ 0xFFE8D8CE, 0xFF33261D, 0xFF6A584A, 0xFF6B4A35, 0xFFB5714A, 0xFFF6F1EE, 0xFFDEC7BA, 0xFFE2CDBA, 0xFFA88B76, 0xFFB5714A, 0xFFC07246, WY, WO, WR, 0xFF33261D, 0xFFFFFFFF },
			{ 0xFFE8CFDA, 0xFF33202A, 0xFF6A515D, 0xFF6B3A50, 0xFFA8577A, 0xFFF6EFF2, 0xFFDDBBCA, 0xFFE3C6D2, 0xFFA8869A, 0xFFA8577A, 0xFFBD648B, WY, WO, WR, 0xFF33202A, 0xFFFFFFFF } };
	/** v2.150 (Viorel): the dark page, card and raised surface take the hue of the theme's accent (Monocrom keeps Claude's colours). */
	private static final int[][] DARK = {
			{ 0xFF262624, 0xFFFAF9F5, 0xFFA6A39A, 0xFFEFE6D2, 0xFFEFE6D2, 0xFF1A1917, 0xFF33312D, 0xFF4F4E4A, 0xFFC2C0B6, 0xFFD97757, 0xFFD97757, DY, DO, DR, 0xFF262624, 0xFF262624 },
			{ 0xFF21362B, 0xFFEEF3EE, 0xFF9BAAA0, 0xFFA9C7B5, 0xFFA9C7B5, 0xFF16221C, 0xFF334D3F, 0xFF486054, 0xFFB8CABE, 0xFF7FB89A, 0xFF7FB89A, DY, DO, DR, 0xFF21362B, 0xFF21362B },
			{ 0xFF23193E, 0xFFF2EFF7, 0xFFA69FB3, 0xFFC9BDE3, 0xFFC9BDE3, 0xFF171127, 0xFF362A56, 0xFF4B3F69, 0xFFC2B8D8, 0xFFA48FD9, 0xFFA48FD9, DY, DO, DR, 0xFF23193E, 0xFF23193E },
			{ 0xFF122D44, 0xFFEEF4FA, 0xFF97A6B6, 0xFFA9C8E6, 0xFFA9C8E6, 0xFF0D1D2B, 0xFF22425E, 0xFF385771, 0xFFB3C8DD, 0xFF6FAEE3, 0xFF6FAEE3, DY, DO, DR, 0xFF122D44, 0xFF122D44 },
			{ 0xFF432613, 0xFFF8EFE7, 0xFFB0A093, 0xFFEBC9AE, 0xFFEBC9AE, 0xFF2B190E, 0xFF5D3923, 0xFF6F4E39, 0xFFD0BBAA, 0xFFE09A6E, 0xFFE09A6E, DY, DO, DR, 0xFF432613, 0xFF432613 },
			{ 0xFF411628, 0xFFF8EEF2, 0xFFB39BA6, 0xFFE8BFD0, 0xFFE8BFD0, 0xFF290F1A, 0xFF5A263C, 0xFF6D3C50, 0xFFD4B6C3, 0xFFE08AAE, 0xFFE08AAE, DY, DO, DR, 0xFF411628, 0xFF411628 } };

	/**
	 * v3.70: the three colours of the weather icons (sun, light parts, dark parts) for each
	 * theme, light and dark, as in the pictures Viorel chose.
	 */
	private static final int[][] ICON_LIGHT = { { 0xFF000000, 0xFF8A8A8A, 0xFF000000 }, { 0xFFD9A93F, 0xFF8FB8A0, 0xFF3E6B55 },
			{ 0xFFE0B04A, 0xFFB3A3D6, 0xFF6B5B95 }, { 0xFFF0C030, 0xFF6FAEE3, 0xFF2F6690 }, { 0xFFE8A23A, 0xFFE0B596, 0xFFB5714A },
			{ 0xFFE8B04A, 0xFFE3A8C0, 0xFFA8577A } };
	private static final int[][] ICON_DARK = { { 0xFFD97757, 0xFFA6A39A, 0xFFFAF9F5 }, { 0xFFE3C46A, 0xFFA9C7B5, 0xFF6E9E84 },
			{ 0xFFE8C46A, 0xFFC9BDE3, 0xFF9381C9 }, { 0xFFF2CD5A, 0xFFA9C8E6, 0xFF5A8DBA }, { 0xFFEDB65E, 0xFFEBC9AE, 0xFFC98A62 },
			{ 0xFFEDC06A, 0xFFE8BFD0, 0xFFC47394 } };

	public static int[] icon(boolean dark) {
		int p = pal < 0 || pal >= ICON_LIGHT.length ? 0 : pal;
		return (dark ? ICON_DARK : ICON_LIGHT)[p];
	}

	public static int[] colors(boolean dark) {
		int p = pal < 0 || pal >= LIGHT.length ? 0 : pal;
		return (dark ? DARK : LIGHT)[p];
	}

	/** v5.00: the colour that stands for theme K in the Settings (its dark accent). */
	public static int swatch(int k) {
		return DARK[k < 0 || k >= DARK.length ? 0 : k][10];
	}

	public static int page(boolean d) { return colors(d)[0]; }
	public static int ink(boolean d) { return colors(d)[1]; }
	public static int dim(boolean d) { return colors(d)[2]; }
	public static int line(boolean d) { return colors(d)[3]; }
	public static int band(boolean d) { return colors(d)[4]; }
	public static int card(boolean d) { return colors(d)[5]; }
	public static int btn(boolean d) { return colors(d)[6]; }
	public static int accent(boolean d) { return colors(d)[10]; }
	public static int bandInk(boolean d) { return colors(d)[15]; }

	/** Thin separators (bubble rows). */
	public static int thin(boolean d) {
		return pal == 0 ? (d ? 0xFF5A5954 : 0xFF999999) : colors(d)[7];
	}

	/** The lines of a table. */
	public static int grid(boolean d) {
		return pal == 0 ? (d ? 0xFF6B6A65 : 0xFF000000) : d ? colors(d)[2] : colors(d)[1];
	}

	/** A table's header band (in bubbles). */
	public static int tableBand(boolean d) {
		return pal == 0 ? (d ? 0xFF3A3936 : 0xFFE8E6DC) : colors(d)[6];
	}

	/** A raised surface (the bottom sheet, buttons in the dark). */
	public static int surface(boolean d) {
		return pal == 0 ? (d ? 0xFF30302E : 0xFFFFFFFF) : d ? colors(d)[6] : colors(d)[0];
	}

	/**
	 * Design audit (2026-11-23): the strong colours of the codes (yellow, orange, red; 1..3), the
	 * same in every theme, for stripes, widget lines and chips. INFO is the blue of INFORMARE
	 * (the app's own information), one token everywhere.
	 */
	public static final int INFO = 0xFF6FAEE3;

	public static int warnStrong(int level) {
		return level >= 3 ? DR : level == 2 ? DO : DY;
	}

	/** The INFORMARE box's tint behind the stripe (light / dark). */
	public static int infoTint(boolean d) {
		return d ? 0xFF2F5F7E : 0xFFBFDDF2;
	}

	/** Relative luminance (WCAG) of colour C. */
	public static double lum(int c) {
		double[] v = { ((c >> 16) & 255) / 255.0, ((c >> 8) & 255) / 255.0, (c & 255) / 255.0 };
		for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
		return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
	}

	/** Contrast ratio (WCAG, 1..21) of A against B. */
	public static double contrast(int a, int b) {
		double x = lum(a), y = lum(b);
		return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
	}

	/** FG moved toward TO (ink or white) just enough to reach RATIO against BG. */
	public static int atLeast(int fg, int bg, int to, double ratio) {
		for (int k = 0; k <= 20 && contrast(fg, bg) < ratio; k++) fg = mix(fg, to, 0.08f);
		return fg;
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
				bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	/**
	 * v2.150 (Viorel): the chosen buttons of the menus (and the main button of a window) take the
	 * theme's hue in a pastel tone, with dark text. Monocrom light stays black with white text.
	 */
	public static int pastel(boolean d) {
		if (pal == 0 && !d) return 0xFF000000;
		float[] h = hsl(accent(d));
		return fromHsl(h[0], Math.min(h[1], 0.55f), d ? 0.82f : 0.74f);
	}

	/** The text on a pastel button. */
	public static int onPastel(boolean d) {
		if (pal == 0 && !d) return 0xFFFFFFFF;
		return d ? page(true) : ink(false);
	}

	private static float[] hsl(int c) {
		float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
		float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b)), l = (mx + mn) / 2, h = 0, s = 0;
		if (mx != mn) {
			float dd = mx - mn;
			s = l > 0.5f ? dd / (2 - mx - mn) : dd / (mx + mn);
			h = mx == r ? (g - b) / dd + (g < b ? 6 : 0) : mx == g ? (b - r) / dd + 2 : (r - g) / dd + 4;
			h /= 6;
		}
		return new float[] { h, s, l };
	}

	private static int fromHsl(float h, float s, float l) {
		float q = l < 0.5f ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
		return 0xFF000000 | Math.round(hue(p, q, h + 1f / 3) * 255) << 16 | Math.round(hue(p, q, h) * 255) << 8 | Math.round(hue(p, q, h - 1f / 3) * 255);
	}

	private static float hue(float p, float q, float t) {
		if (t < 0) t += 1;
		if (t > 1) t -= 1;
		if (t < 1f / 6) return p + (q - p) * 6 * t;
		if (t < 0.5f) return q;
		if (t < 2f / 3) return p + (q - p) * (2f / 3 - t) * 6;
		return p;
	}
}
