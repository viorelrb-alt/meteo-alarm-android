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
			{ 0xFFFBF5EF, 0xFF33261D, 0xFF7A6657, 0xFF6B4A35, 0xFFB5714A, 0xFFFFFFFF, 0xFFF5E8DC, 0xFFE2CDBA, 0xFFA88B76, 0xFFB5714A, 0xFFD9895B, WY, WO, WR, 0xFF33261D, 0xFFFFFFFF },
			{ 0xFFFBF3F5, 0xFF33202A, 0xFF7A5F6B, 0xFF6B3A50, 0xFFA8577A, 0xFFFFFFFF, 0xFFF5E4EA, 0xFFE3C6D2, 0xFFA8869A, 0xFFA8577A, 0xFFC96A93, WY, WO, WR, 0xFF33202A, 0xFFFFFFFF } };
	private static final int[][] DARK = {
			{ 0xFF262624, 0xFFFAF9F5, 0xFFA6A39A, 0xFFEFE6D2, 0xFFEFE6D2, 0xFF000000, 0xFF30302E, 0xFF4F4E4A, 0xFFC2C0B6, 0xFFD97757, 0xFFD97757, DY, DO, DR, 0xFF262624, 0xFF262624 },
			{ 0xFF1E2420, 0xFFEEF3EE, 0xFF9BAAA0, 0xFFA9C7B5, 0xFFA9C7B5, 0xFF121614, 0xFF2A322D, 0xFF3F4B44, 0xFFB8CABE, 0xFF7FB89A, 0xFF7FB89A, DY, DO, DR, 0xFF1E2420, 0xFF1E2420 },
			{ 0xFF211E27, 0xFFF2EFF7, 0xFFA69FB3, 0xFFC9BDE3, 0xFFC9BDE3, 0xFF141218, 0xFF2D2935, 0xFF48425A, 0xFFC2B8D8, 0xFFA48FD9, 0xFFA48FD9, DY, DO, DR, 0xFF211E27, 0xFF211E27 },
			{ 0xFF1A2129, 0xFFEEF4FA, 0xFF97A6B6, 0xFFA9C8E6, 0xFFA9C8E6, 0xFF10151B, 0xFF26303B, 0xFF3B4A5A, 0xFFB3C8DD, 0xFF6FAEE3, 0xFF6FAEE3, DY, DO, DR, 0xFF1A2129, 0xFF1A2129 },
			{ 0xFF2A221D, 0xFFF8EFE7, 0xFFB0A093, 0xFFEBC9AE, 0xFFEBC9AE, 0xFF17120F, 0xFF362C25, 0xFF54453B, 0xFFD0BBAA, 0xFFE09A6E, 0xFFE09A6E, DY, DO, DR, 0xFF2A221D, 0xFF2A221D },
			{ 0xFF291E23, 0xFFF8EEF2, 0xFFB39BA6, 0xFFE8BFD0, 0xFFE8BFD0, 0xFF161013, 0xFF35282E, 0xFF544049, 0xFFD4B6C3, 0xFFE08AAE, 0xFFE08AAE, DY, DO, DR, 0xFF291E23, 0xFF291E23 } };

	public static int[] colors(boolean dark) {
		int p = pal < 0 || pal >= LIGHT.length ? 0 : pal;
		return (dark ? DARK : LIGHT)[p];
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
}
