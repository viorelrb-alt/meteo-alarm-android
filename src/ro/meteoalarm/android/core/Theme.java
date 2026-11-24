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
			{ 0xFFF3F6F1, 0xFF1F2A24, 0xFF5E6B63, 0xFF2F4A3C, 0xFF3E6B55, 0xFFFFFFFF, 0xFFE6EEE8, 0xFFB9C9BE, 0xFF6E8A7A, 0xFF3E6B55, 0xFF4E8A6A, WY, WO, WR, 0xFF1F2A24, 0xFFFFFFFF },
			{ 0xFFF6F3FA, 0xFF2A2433, 0xFF6C6378, 0xFF4B3F66, 0xFF6B5B95, 0xFFFFFFFF, 0xFFECE6F5, 0xFFCFC6DE, 0xFF8C80A8, 0xFF6B5B95, 0xFF8E6CC9, WY, WO, WR, 0xFF2A2433, 0xFFFFFFFF },
			{ 0xFFF2F7FB, 0xFF16263A, 0xFF5A6B80, 0xFF24476B, 0xFF2F6690, 0xFFFFFFFF, 0xFFE3EEF7, 0xFFBCD0E2, 0xFF7C98B5, 0xFF2F6690, 0xFF3A86C8, WY, WO, WR, 0xFF16263A, 0xFFFFFFFF },
			{ 0xFFFBF5EF, 0xFF33261D, 0xFF7A6657, 0xFF6B4A35, 0xFFB5714A, 0xFFFFFFFF, 0xFFF5E8DC, 0xFFE2CDBA, 0xFFA88B76, 0xFFB5714A, 0xFFC07246, WY, WO, WR, 0xFF33261D, 0xFFFFFFFF },
			{ 0xFFFBF3F5, 0xFF33202A, 0xFF7A5F6B, 0xFF6B3A50, 0xFFA8577A, 0xFFFFFFFF, 0xFFF5E4EA, 0xFFE3C6D2, 0xFFA8869A, 0xFFA8577A, 0xFFC96A93, WY, WO, WR, 0xFF33202A, 0xFFFFFFFF } };
	/** v2.150 (Viorel): the dark page, card and raised surface take the hue of the theme's accent (Monocrom keeps Claude's colours). */
	private static final int[][] DARK = {
			{ 0xFF262624, 0xFFFAF9F5, 0xFFA6A39A, 0xFFEFE6D2, 0xFFEFE6D2, 0xFF1A1917, 0xFF33312D, 0xFF4F4E4A, 0xFFC2C0B6, 0xFFD97757, 0xFFD97757, DY, DO, DR, 0xFF262624, 0xFF262624 },
			{ 0xFF1C2922, 0xFFEEF3EE, 0xFF9BAAA0, 0xFFA9C7B5, 0xFFA9C7B5, 0xFF121915, 0xFF2D3C34, 0xFF44554C, 0xFFB8CABE, 0xFF7FB89A, 0xFF7FB89A, DY, DO, DR, 0xFF1C2922, 0xFF1C2922 },
			{ 0xFF1E182D, 0xFFF2EFF7, 0xFFA69FB3, 0xFFC9BDE3, 0xFFC9BDE3, 0xFF130F1C, 0xFF2F2741, 0xFF463D5C, 0xFFC2B8D8, 0xFFA48FD9, 0xFFA48FD9, DY, DO, DR, 0xFF1E182D, 0xFF1E182D },
			{ 0xFF17232E, 0xFFEEF4FA, 0xFF97A6B6, 0xFFA9C8E6, 0xFFA9C8E6, 0xFF0F161D, 0xFF263643, 0xFF3C4E5D, 0xFFB3C8DD, 0xFF6FAEE3, 0xFF6FAEE3, DY, DO, DR, 0xFF17232E, 0xFF17232E },
			{ 0xFF2E2017, 0xFFF8EFE7, 0xFFB0A093, 0xFFEBC9AE, 0xFFEBC9AE, 0xFF1D140F, 0xFF433126, 0xFF5D493C, 0xFFD0BBAA, 0xFFE09A6E, 0xFFE09A6E, DY, DO, DR, 0xFF2E2017, 0xFF2E2017 },
			{ 0xFF2E1721, 0xFFF8EEF2, 0xFFB39BA6, 0xFFE8BFD0, 0xFFE8BFD0, 0xFF1D0F15, 0xFF432632, 0xFF5D3C4A, 0xFFD4B6C3, 0xFFE08AAE, 0xFFE08AAE, DY, DO, DR, 0xFF2E1721, 0xFF2E1721 } };

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
}
