package ro.meteoalarm.android.core;

/**
 * v3.60 (Viorel): the colour themes. Each theme has a light and a dark side; ☰ › Temă picks the
 * theme and, under a line, light or dark. 0 = Monocrom (the Kindle look; dark = graphite).
 * The 16 colours, by role: page ink dim line band card btn dry snow rain accent warnY warnO warnR
 * pillInk bandInk (as Screen.Palette).
 */
public final class Theme {
	private Theme() {
	}

	/** The theme in use (0..5), set from the settings by the app. */
	public static int pal;

	public static final String[][] NAMES = { { "Monochrome", "Monocrom" }, { "Pink", "Roz" }, { "Yellow", "Galben" }, { "Green", "Verde" }, { "Blue", "Albastru" }, { "Violet", "Violet" } };

	private static final int WY = 0xFFF4E59A, WO = 0xFFF7BE8A, WR = 0xFFF29B9B, DY = 0xFFE3B341, DO = 0xFFD97757, DR = 0xFFD14D4D;

	private static final int[][] LIGHT = {
			{ 0xFFFFFFFF, 0xFF000000, 0xFF555555, 0xFF000000, 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFAAAAAA, 0xFF555555, 0xFF000000, 0xFF000000, 0xFFDDDDDD, 0xFFAAAAAA, 0xFFAAAAAA, 0xFF000000, 0xFFFFFFFF },
			{ 0xFFFAD1DF, 0xFF29141B, 0xFF5E454E, 0xFF502B37, 0xFF6D3B4C, 0xFFFDF1F5, 0xFFF6B1C8, 0xFFCEB6BE, 0xFF906A77, 0xFF6D3B4C, 0xFF29696C, WY, WO, WR, 0xFF29141B, 0xFFFFFFFF },
			{ 0xFFF8E9AA, 0xFF292514, 0xFF5E5945, 0xFF50482B, 0xFF6D633B, 0xFFFBF3D0, 0xFFF5E089, 0xFFCEC9B6, 0xFF90886A, 0xFF6D633B, 0xFF4B3EA3, WY, WO, WR, 0xFF292514, 0xFFFFFFFF },
			{ 0xFFBAE8C2, 0xFF142917, 0xFF455E49, 0xFF2B5031, 0xFF3B6D43, 0xFFD9F2DD, 0xFF9FDFAA, 0xFFB6CEBA, 0xFF6A9070, 0xFF3B6D43, 0xFF9F3D76, WY, WO, WR, 0xFF142917, 0xFFFFFFFF },
			{ 0xFFC8DEF9, 0xFF141E29, 0xFF45505E, 0xFF2B3B50, 0xFF3B526D, 0xFFEDF4FD, 0xFFA8CBF5, 0xFFB6C1CE, 0xFF6A7B90, 0xFF3B526D, 0xFF296C55, WY, WO, WR, 0xFF141E29, 0xFFFFFFFF },
			{ 0xFFE7CEF3, 0xFF221429, 0xFF56455E, 0xFF432B50, 0xFF5D3B6D, 0xFFF7EFFB, 0xFFD8B1EC, 0xFFC6B6CE, 0xFF836A90, 0xFF5D3B6D, 0xFF2C6840, WY, WO, WR, 0xFF221429, 0xFFFFFFFF } };
	/**
	 * v2.153 (Viorel): six themes from base colours (Monocrom, Roz, Galben, Verde, Albastru, Violet).
	 * Monocrom stays white, black and grey (dark = graphite). The buttons and the accent take a
	 * contrasting hue (BTN_*), dark with white text in light mode, light with dark text in dark mode.
	 */
	private static final int[] BTN_LIGHT = { 0xFF000000, 0xFF307B7E, 0xFF4B3EA3, 0xFFA33E79, 0xFF307E64, 0xFF378150 },
			BTN_DARK = { 0xFFFFFFFF, 0xFFAEDEE0, 0xFFB4AEE0, 0xFFE0AECB, 0xFFAEE0CF, 0xFFB0DDBF };
	/** v2.150 (Viorel): the dark page, card and raised surface take the hue of the theme's accent (Monocrom keeps Claude's colours). */
	private static final int[][] DARK = {
			{ 0xFF2E2E2E, 0xFFF5F5F5, 0xFFB4B4B4, 0xFFE0E0E0, 0xFFE0E0E0, 0xFF242424, 0xFF404040, 0xFF555555, 0xFFC2C2C2, 0xFFFFFFFF, 0xFFFFFFFF, DY, DO, DR, 0xFF2E2E2E, 0xFF2E2E2E },
			{ 0xFF4B1628, 0xFFF8F2F4, 0xFFC2AEB4, 0xFFD4AFBC, 0xFFD4AFBC, 0xFF37101D, 0xFF672339, 0xFF614850, 0xFFCBB9BF, 0xFFC07C93, 0xFFAEDEE0, DY, DO, DR, 0xFF4B1628, 0xFF4B1628 },
			{ 0xFF3B3311, 0xFFF8F7F2, 0xFFC2BEAE, 0xFFD4CDAF, 0xFFD4CDAF, 0xFF28220B, 0xFF584C1E, 0xFF615C48, 0xFFCBC7B9, 0xFFC0B27C, 0xFFB4AEE0, DY, DO, DR, 0xFF3B3311, 0xFF3B3311 },
			{ 0xFF0F3315, 0xFFF2F8F3, 0xFFAEC2B1, 0xFFAFD4B6, 0xFFAFD4B6, 0xFF09200D, 0xFF1B5024, 0xFF48614C, 0xFFB9CBBC, 0xFF7CC087, 0xFFE0AECB, DY, DO, DR, 0xFF0F3315, 0xFF0F3315 },
			{ 0xFF0E2B4E, 0xFFF2F4F8, 0xFFAEB7C2, 0xFFAFC0D4, 0xFFAFC0D4, 0xFF0A1F38, 0xFF193E6C, 0xFF485361, 0xFFB9C1CB, 0xFF7C9BC0, 0xFFAEE0CF, DY, DO, DR, 0xFF0E2B4E, 0xFF0E2B4E },
			{ 0xFF391849, 0xFFF6F2F8, 0xFFBBAEC2, 0xFFC8AFD4, 0xFFC8AFD4, 0xFF2A1236, 0xFF4F2664, 0xFF584861, 0xFFC5B9CB, 0xFFA97CC0, 0xFFB0DDBF, DY, DO, DR, 0xFF391849, 0xFF391849 } };

	/**
	 * v3.70: the three colours of the weather icons (sun, light parts, dark parts) for each
	 * theme, light and dark, as in the pictures Viorel chose.
	 */
	private static final int[][] ICON_LIGHT = {
			{ 0xFF000000, 0xFF8A8A8A, 0xFF000000 },
			{ 0xFFD9A93F, 0xFFCD98AA, 0xFF6D3B4C },
			{ 0xFFB8860B, 0xFFCDC398, 0xFF6D633B },
			{ 0xFFD9A93F, 0xFF98CDA1, 0xFF3B6D43 },
			{ 0xFFD9A93F, 0xFF98B0CD, 0xFF3B526D },
			{ 0xFFD9A93F, 0xFFBB98CD, 0xFF5D3B6D } };
	private static final int[][] ICON_DARK = {
			{ 0xFFFFFFFF, 0xFFA6A6A6, 0xFFF5F5F5 },
			{ 0xFFE8C46A, 0xFFDEBAC6, 0xFFB96E87 },
			{ 0xFFF2CD5A, 0xFFDED7BA, 0xFFB9AA6E },
			{ 0xFFE8C46A, 0xFFBADEC0, 0xFF6EB97B },
			{ 0xFFE8C46A, 0xFFBACADE, 0xFF6E90B9 },
			{ 0xFFE8C46A, 0xFFD2BADE, 0xFFA06EB9 } };

	public static int[] icon(boolean dark) {
		int p = pal < 0 || pal >= ICON_LIGHT.length ? 0 : pal;
		return (dark ? ICON_DARK : ICON_LIGHT)[p];
	}

	public static int[] colors(boolean dark) {
		int p = pal < 0 || pal >= LIGHT.length ? 0 : pal;
		return (dark ? DARK : LIGHT)[p];
	}

	/** v2.153: the dot that stands for theme K in the Settings is its button colour (Monocrom: white). */
	public static int swatch(int k, boolean d) {
		if (k <= 0 || k >= BTN_LIGHT.length) return 0xFFFFFFFF;
		return (d ? BTN_DARK : BTN_LIGHT)[k];
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
		return pal == 0 ? (d ? 0xFF5A5A5A : 0xFF999999) : colors(d)[7];
	}

	/** The lines of a table. */
	public static int grid(boolean d) {
		return pal == 0 ? (d ? 0xFF6B6B6B : 0xFF000000) : d ? colors(d)[2] : colors(d)[1];
	}

	/** A table's header band (in bubbles). */
	public static int tableBand(boolean d) {
		return pal == 0 ? (d ? 0xFF3C3C3C : 0xFFE8E6DC) : colors(d)[6];
	}

	/** A raised surface (the bottom sheet, buttons in the dark). */
	public static int surface(boolean d) {
		return pal == 0 ? (d ? 0xFF383838 : 0xFFFFFFFF) : d ? colors(d)[6] : colors(d)[0];
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
		int p = pal < 0 || pal >= BTN_LIGHT.length ? 0 : pal;
		return (d ? BTN_DARK : BTN_LIGHT)[p];
	}

	/** The text on a pastel button. */
	public static int onPastel(boolean d) {
		return d ? card(true) : 0xFFFFFFFF;
	}
}
