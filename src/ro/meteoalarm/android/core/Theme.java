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

	/** v2.180 (Viorel's theme file): Monocrom editorial, Roz quartz & bordeaux, Galben parchment & espresso, Verde sage & forest, Albastru sky & navy, Violet lavender & night. */
	private static final int[][] LIGHT = {
			{ 0xFFFFFFFF, 0xFF09090B, 0xFF3F3F46, 0xFF18181B, 0xFF09090B, 0xFFF4F4F5, 0xFFE4E4E7, 0xFF18181B, 0xFF71717A, 0xFF09090B, 0xFF09090B, WY, WO, WR, 0xFF09090B, 0xFFFFFFFF },
{ 0xFFFFF5F7, 0xFF2E0F18, 0xFF6E3042, 0xFFF7DCE3, 0xFFC22952, 0xFFF7DCE3, 0xFFECC3CE, 0xFFF7DCE3, 0xFF995468, 0xFFC22952, 0xFFC22952, WY, WO, WR, 0xFF2E0F18, 0xFFFFFFFF },
{ 0xFFF5E8C7, 0xFF291D0C, 0xFF5E4826, 0xFFE0CE9E, 0xFFB86B00, 0xFFFFFDF5, 0xFFEAD5A4, 0xFFE0CE9E, 0xFF8A6F42, 0xFFB86B00, 0xFFB86B00, WY, WO, WR, 0xFF291D0C, 0xFFFFFFFF },
{ 0xFFD8EBE0, 0xFF0D261A, 0xFF2E5943, 0xFFB2D4C0, 0xFF16794A, 0xFFF5FBF7, 0xFFC2E3D0, 0xFFB2D4C0, 0xFF53826A, 0xFF16794A, 0xFF16794A, WY, WO, WR, 0xFF0D261A, 0xFFFFFFFF },
{ 0xFFDCEBFA, 0xFF0A2240, 0xFF2B4C75, 0xFFB6D4F2, 0xFF155EEF, 0xFFF7FBFF, 0xFFC7DFFA, 0xFFB6D4F2, 0xFF52759E, 0xFF155EEF, 0xFF155EEF, WY, WO, WR, 0xFF0A2240, 0xFFFFFFFF },
{ 0xFFE8E0F7, 0xFF1F1038, 0xFF4C3378, 0xFFC9B8EB, 0xFF6936D4, 0xFFFAF8FF, 0xFFD8C9F5, 0xFFC9B8EB, 0xFF7459A3, 0xFF6936D4, 0xFF6936D4, WY, WO, WR, 0xFF1F1038, 0xFFFFFFFF } };
	/**
	 * v2.153 (Viorel): six themes from base colours (Monocrom, Roz, Galben, Verde, Albastru, Violet).
	 * Monocrom stays white, black and grey (dark = graphite). The buttons and the accent take a
	 * contrasting hue (BTN_*), dark with white text in light mode, light with dark text in dark mode.
	 */
	private static final int[] BTN_LIGHT = { 0xFF09090B, 0xFFC22952, 0xFFB86B00, 0xFF16794A, 0xFF155EEF, 0xFF6936D4 },
			BTN_DARK = { 0xFFFFFFFF, 0xFFFF7597, 0xFFF5B027, 0xFF4ADE80, 0xFF539BFF, 0xFFB68CFF };
	/** v2.150 (Viorel): the dark page, card and raised surface take the hue of the theme's accent (Monocrom keeps Claude's colours). */
	private static final int[][] DARK = {
{ 0xFF0A0A0B, 0xFFFFFFFF, 0xFFA1A1AA, 0xFF3F3F46, 0xFFFFFFFF, 0xFF161618, 0xFF27272A, 0xFF3F3F46, 0xFF71717A, 0xFFFFFFFF, 0xFFFFFFFF, DY, DO, DR, 0xFFFFFFFF, 0xFF09090B },
{ 0xFF211117, 0xFFFCEEF2, 0xFFD698A9, 0xFF4F2B39, 0xFFFF7597, 0xFF301922, 0xFF4D2634, 0xFF4F2B39, 0xFF9E6273, 0xFFFF7597, 0xFFFF7597, DY, DO, DR, 0xFFFCEEF2, 0xFF240D14 },
{ 0xFF1F170D, 0xFFFDF6E7, 0xFFD1BC94, 0xFF4D3A22, 0xFFF5B027, 0xFF2E2213, 0xFF47351B, 0xFF4D3A22, 0xFF99835C, 0xFFF5B027, 0xFFF5B027, DY, DO, DR, 0xFFFDF6E7, 0xFF1F1608 },
{ 0xFF0B1F16, 0xFFE8F7EE, 0xFF91C2A8, 0xFF27523F, 0xFF4ADE80, 0xFF132E22, 0xFF1F4735, 0xFF27523F, 0xFF5E8F76, 0xFF4ADE80, 0xFF4ADE80, DY, DO, DR, 0xFFE8F7EE, 0xFF081C12 },
{ 0xFF0B192C, 0xFFEBF4FF, 0xFF94B8E0, 0xFF264670, 0xFF539BFF, 0xFF132742, 0xFF1E3E69, 0xFF264670, 0xFF6388B3, 0xFF539BFF, 0xFF539BFF, DY, DO, DR, 0xFFEBF4FF, 0xFF071526 },
{ 0xFF160E26, 0xFFF3EEFF, 0xFFB5A1DB, 0xFF412C6B, 0xFFB68CFF, 0xFF22163B, 0xFF392561, 0xFF412C6B, 0xFF806BA6, 0xFFB68CFF, 0xFFB68CFF, DY, DO, DR, 0xFFF3EEFF, 0xFF170C2B } };

	/**
	 * v3.70: the three colours of the weather icons (sun, light parts, dark parts) for each
	 * theme, light and dark, as in the pictures Viorel chose.
	 */
	private static final int[][] ICON_LIGHT = {
{ 0xFF52525B, 0xFFA1A1AA, 0xFF3F3F46 },
{ 0xFFF59E0B, 0xFF94A3B8, 0xFF64748B },
{ 0xFFF59E0B, 0xFF94A3B8, 0xFF64748B },
{ 0xFFF59E0B, 0xFF94A3B8, 0xFF64748B },
{ 0xFFF59E0B, 0xFF94A3B8, 0xFF64748B },
{ 0xFFF59E0B, 0xFF94A3B8, 0xFF64748B } };
	private static final int[][] ICON_DARK = {
{ 0xFFD4D4D8, 0xFF8A8A92, 0xFFE4E4E7 },
{ 0xFFFBBF24, 0xFFCBD5E1, 0xFFE2E8F0 },
{ 0xFFFBBF24, 0xFFCBD5E1, 0xFFE2E8F0 },
{ 0xFFFBBF24, 0xFFCBD5E1, 0xFFE2E8F0 },
{ 0xFFFBBF24, 0xFFCBD5E1, 0xFFE2E8F0 },
{ 0xFFFBBF24, 0xFFCBD5E1, 0xFFE2E8F0 } };

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
		// v2.180 (Viorel): the dot of each theme has the theme's own colour (its accent).
		if (k < 0 || k >= BTN_LIGHT.length) k = 0;
		return (d ? BTN_DARK : BTN_LIGHT)[k];
	}

	/** v2.180 (Viorel's theme file, 6 atmospheres): the raised surface (top bar, dock, sheets) and the text on the accent. */
	public static final int[] ELEV_L = { 0xFFE4E4E7, 0xFFF2CCD6, 0xFFEFE0B9, 0xFFE3F2E9, 0xFFE8F2FC, 0xFFEFE8FC }, ELEV_D = { 0xFF222225, 0xFF3D202B, 0xFF3B2C19, 0xFF1B3D2E, 0xFF1B3557, 0xFF2E1E4F }, ON_L = { 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF }, ON_D = { 0xFF09090B, 0xFF240D14, 0xFF1F1608, 0xFF081C12, 0xFF071526, 0xFF170C2B };
	public static int elev(boolean d) { return (d ? ELEV_D : ELEV_L)[pal < 0 || pal > 5 ? 0 : pal]; }
	public static int onAcc(boolean d) { return (d ? ON_D : ON_L)[pal < 0 || pal > 5 ? 0 : pal]; }
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
		return colors(d)[7];
	}

	/** The lines of a table. */
	public static int grid(boolean d) {
		return colors(d)[3];
	}

	/** A table's header band (in bubbles). */
	public static int tableBand(boolean d) {
		return colors(d)[6];
	}

	/** A raised surface (the bottom sheet, buttons in the dark). */
	public static int surface(boolean d) {
		return elev(d);
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
		return onAcc(d);
	}
}
