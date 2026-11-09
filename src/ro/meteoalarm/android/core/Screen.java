package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The screens of Meteo Alarm Kindle, laid out for a phone. Portrait: the canvas is 758 units wide
 * like the Paperwhite's; the height follows the phone (at least BASE_H) and the extra room goes to
 * the current weather. From the top: clock, city and warnings, the temperature and icon, the
 * precipitation chart, tomorrow and the day after, three more days, sun / UV / pollen and the
 * status line. Landscape: the canvas is 758 units high and at least BASE_LW wide, in three
 * columns: the current weather (it takes the extra width), precipitation over sun / UV / pollen,
 * and the five days. Tablets (at least 600 dp on the short side): the screen split in two, the
 * weather now in one half and the boxes, drawn 20% larger, in the other; the short side is TAB
 * units.
 */
public final class Screen {
	public static final float W = 758, BASE_H = 1538, LH = 758, BASE_LW = 1560, TAB = 1091;

	/**
	 * How a screen PW x PH pixels shows the canvas: { scale, canvas width, canvas height, x offset,
	 * y offset }. Phones: portrait 758 units wide (the extra height goes to the weather now),
	 * landscape 758 high; tablets fill the screen with TAB units on the short side.
	 */
	public static float[] fit(float pw, float ph, boolean tablet) {
		float scale, cw, ch;
		if (tablet) {
			scale = Math.min(pw, ph) / TAB;
			return new float[] { scale, pw / scale, ph / scale, 0, 0 };
		}
		if (pw > ph) {
			scale = Math.min(ph / LH, pw / BASE_LW);
			cw = pw / scale;
			ch = LH;
			return new float[] { scale, cw, ch, 0, (ph - ch * scale) / 2 };
		}
		scale = pw / W;
		ch = ph / scale;
		if (ch < BASE_H) {
			scale = ph / BASE_H;
			ch = BASE_H;
		}
		return new float[] { scale, W, ch, (pw - W * scale) / 2, 0 };
	}

	// Inputs.
	public Weather w;
	public List<Warnings.Warn> warns;
	/** The earthquakes of the last 24 h in the country (EMSC), newest first. */
	public List<Quakes.Quake> quakes;
	public int pollenLvl = -1;
	/** European air quality index, -1 when unknown. */
	public int aqi = -1;
	public String pollenPlant = "";
	public Cities.City city;
	/** The neighbourhood or sector the location found, shown after the town (never kept as a town). */
	public String district = "";

	/** "Călărași, CL", or with the neighbourhood "Călărași, Zona 10 Nivele, CL". */
	public String place() {
		if (parcel != null) return parcel;
		if (city == null) return "";
		String l = city.label();
		if (district == null || district.length() == 0) return l;
		int k = l.lastIndexOf(", ");
		return k > 0 ? l.substring(0, k) + ", " + district + l.substring(k) : l + ", " + district;
	}
	public Texts T = new Texts(false);
	public boolean unitF, clk12;
	public Calendar now = Zone.now();
	public int batt = -1;
	/** v3.00: the connection, drawn left of the clock: -1 not shown, 0 none, 1 Wi-Fi, 2 mobile, 3 other. */
	public int net = -1;
	/** Where the clock (and the connection icon left of it) begins, in units. */
	private float clockLeft;
	public boolean charging, busy, offline;
	public long fetched;
	public String error = "";
	/** Why the last download failed ("no internet", "certificate error", "HTTP 503"...), empty after one that worked. */
	public String failReason = "";
	/** Where the forecast came from when not Open-Meteo (MET Norway), shown after "updated". */
	public String source = "";
	/** Dark theme with the Claude app's colours; otherwise black and white like the Kindle. */
	public boolean dark;
	/** Looking for the phone's position (the locate button is drawn in the accent colour). */
	public boolean locating;
	/** Automatic location on; off: the locate button is gray. */
	public boolean autoLoc = true;
	/** Tablet layout (see fit). */
	public boolean tablet;
	/**
	 * Brightness: the vertical bar near the left edge (shown by the sun button or by sliding on the
	 * edge), with the dark / light theme button over it, and its level (0–1, the system's curve).
	 */
	public boolean brightBar;
	/** The theme follows the phone's light sensor (the button next to the sun is filled). */
	public boolean sensorTheme;
	/** The phone has a compass (rotation vector) / a light sensor; without one, its box is left out
	 * and its button grayed (Viorel 2026-11-04: the INCAR G3 tablet has neither). */
	public boolean hasCompass = true, hasLight = true;
	public float brightLevel = 0.5f;
	/**
	 * The bar's colours, from the phone's theme (Android 12+: its palette, so a pink theme gives a
	 * pink bar); otherwise the phone's usual light gray. The track is drawn half see-through.
	 */
	public int barFill = 0xFFE3E3E3, barTrack = 0xFF8A8686, barIcon = 0xFF1C1B1F;
	/** The bar and the theme button (x, y, w, h); set by draw. */
	public final float[] barArea = { 0, 0, 0, 0 }, btnMoon = { 0, 0, 0, 0 };

	// Buttons (x, y, w, h in canvas units), set by draw.
	public final float[] btnMenu = { 40, 24, 64, 64 }, btnCity = { 40, 0, 64, 64 },
			btnLocate = { 124, 0, 64, 64 }, btnBright = { 208, 0, 64, 64 }, btnSensor = { 292, 0, 64, 64 },
			cityArea = { 40, 104, 380, 56 };
	/** Where the warning boxes are (for the swipe) and the first one shown; set by draw. */
	public final float[] warnArea = { 0, 0, 0, 0 };
	public int warnStart, warnBoxes;
	/** Where the hour boxes are (for the swipe) and the page shown; set by draw. */
	/** The rows moved by the finger: next hours, five days (landscape), info boxes, info grid (landscape). */
	public final Strip hours = new Strip(false), days = new Strip(false), info = new Strip(false), grid = new Strip(true),
			alerts = new Strip(false), rain = new Strip(false);

	public Strip[] strips() {
		return new Strip[] { hours, days, info, grid, alerts, rain };
	}

	/**
	 * Farmer mode (v2.50): the hour boxes open a bubble with the farmer's details and the rain
	 * chart gives way to a row of hourly rain-chance boxes (in place of the five days). FARM is
	 * the farmer's forecast for the town (Open-Meteo; null when it has not come).
	 */
	public boolean farmer;
	/** Farmer mode (v2.70, Viorel 2026-11-07): one row of wide boxes, the hour and its rain together (phones). */
	public boolean farmMerged = true;
	/** The merged boxes were drawn in this frame: a tap on one shows the hour and its rain together. */
	public boolean mergedShown;
	/**
	 * Farmer mode (v2.60): the main screens moved sideways over the weather now, the town first
	 * and then one per parcel. PARCEL is the name of the parcel shown (null on the town's);
	 * PAGE / PAGES the dots under the line. NOW_AREA is where the finger swipes (x, y, w, h).
	 */
	public String parcel;
	public int page, pages;
	public final float[] nowArea = { 0, 0, 0, 0 };
	/** The "rain in the last 24 hours" box next to the sun button (x, y, w, h); set by draw. */
	public final float[] btnRain24 = { 0, 0, 0, 0 };
	/** The parcel's boxes drawn in the bottom row: { box index, x, y, w, h }; set by draw. */
	private final List<float[]> infoHits = new java.util.ArrayList<float[]>();
	private List<String> infoIds = new java.util.ArrayList<String>();
	public Farm.Data farm;
	/** The hour and rain boxes drawn: { hour index, x, y, w, h } in canvas units; set by draw. */
	private final List<float[]> hourHits = new java.util.ArrayList<float[]>(), rainHits = new java.util.ArrayList<float[]>();
	/** The scale of the boxes being drawn (tablet: 1.2). */
	private float hs = 1;

	/** Canvas width: 758 in portrait, wider in landscape. */
	private float cw = W;

	private Gfx g;
	private Palette c = Palette.LIGHT;

	/** The colours of a theme, by role. */
	static final class Palette {
		final int page, ink, dim, line, band, card, btn, dry, snow, rain, accent, warnY, warnO, warnR, pillInk, bandInk;

		Palette(int... v) {
			page = v[0]; ink = v[1]; dim = v[2]; line = v[3]; band = v[4]; card = v[5]; btn = v[6]; dry = v[7];
			snow = v[8]; rain = v[9]; accent = v[10]; warnY = v[11]; warnO = v[12]; warnR = v[13]; pillInk = v[14]; bandInk = v[15];
		}

		// Kindle: black on white with grays; box titles white on a black band (the mockups);
		// orange and red codes are the darker gray.
		static final Palette LIGHT = new Palette(Gfx.WHITE, Gfx.BLACK, Gfx.GRAY5, Gfx.BLACK, Gfx.BLACK, Gfx.WHITE, Gfx.WHITE,
				Gfx.GRAYA, Gfx.GRAY5, Gfx.BLACK, Gfx.BLACK, Gfx.GRAYD, Gfx.GRAYA, Gfx.GRAYA, Gfx.BLACK, Gfx.WHITE);
		// Claude dark: warm charcoal page, boxes black inside with a cream outline and a cream title
		// band (light cream, Viorel 2026-10-31) with dark titles (Viorel's mockups); terracotta accent; the codes in muted real colours.
		static final Palette DARK = new Palette(0xFF262624, 0xFFFAF9F5, 0xFFA6A39A, 0xFFEFE6D2, 0xFFEFE6D2, 0xFF000000,
				0xFF30302E, 0xFF4F4E4A, 0xFFC2C0B6, 0xFFD97757, 0xFFD97757, 0xFFE3B341, 0xFFD97757, 0xFFD14D4D, 0xFF262624, 0xFF262624);
	}

	// Middle of the weather icon's drawing inside its 200 x 200 file (icons/centru.txt).
	private static final String[] CENTRE = { "clear-day 24 24 177 177", "clear-night 38 38 144 163", "cloudy 26 59 174 136",
			"drizzle 26 21 174 158", "fog 25 28 176 176", "partly-day 8 8 183 157", "partly-night 31 24 183 157",
			"rain 26 17 174 179", "snow 26 17 174 182", "thunder 26 17 174 197" };

	/** Draws the screen on a canvas CW x CH: landscape when it is wider than high. */
	public void draw(Gfx gfx, float cw, float ch) {
		g = gfx;
		c = dark ? Palette.DARK : Palette.LIGHT;
		this.cw = cw;
		updHead = false;
		hourHits.clear();
		rainHits.clear();
		infoHits.clear();
		mergedShown = false;
		nowArea[2] = 0;
		btnRain24[2] = 0;
		hs = 1;
		if (nightClock) {
			drawNightClock(ch);
			return;
		}
		g.rect(0, 0, cw, ch, c.page);
		button(btnMenu);
		for (int k = 0; k < 3; k++) g.rect(57, 41 + k * 13, 30, 4, c.ink);
		// Refreshing is in the ☰ menu and by pulling the screen down: the clock takes the corner.
		drawClock();
		rtext(false, 26, 71, cw - 40, T.date(now), c.ink);
		// v3.10 (Viorel): in landscape "FARMER MODE" sits at the bottom left, after the buttons.
		tagLow = cw > ch;
		statsLow = tagLow || tablet;
		btnStats[2] = 0;
		if (tablet) drawTablet(ch);
		else if (cw > ch) drawLandscape(ch);
		else drawPortrait(ch);
		barArea[2] = 0;
		btnMoon[2] = 0;
		if (brightBar) drawBrightBar(ch);
		sizeArea[2] = 0;
		if (sizeBar) drawSizeBar(ch);
	}

	/**
	 * The text-size bar (v2.10): shown by sliding near the right edge, on the left like the
	 * brightness one (Viorel's spec); over it the size in percent. SIZE_LEVEL 0–1 (TextSize 80–180 %).
	 */
	public boolean sizeBar;
	public float sizeLevel = 0.2f;
	public final float[] sizeArea = { 0, 0, 0, 0 };

	/** Only the text-size bar, on GFX over a page (SizeOverlay, v2.12). */
	public void drawSizeBarOnly(Gfx gfx, float ch) {
		g = gfx;
		c = dark ? Palette.DARK : Palette.LIGHT;
		drawSizeBar(ch);
	}

	private void drawSizeBar(float ch) {
		float bw = 84, x = 50, bh = ch * 0.42f, gap = 24, top = (ch - bw - gap - bh) / 2, by = top + bw + gap;
		int ink = dark ? Gfx.WHITE : Gfx.BLACK, bg = dark ? Gfx.BLACK : Gfx.WHITE;
		g.roundRect(x, top, bw, bw, bw / 2, ink);
		g.roundRect(x + 7, top + 7, bw - 14, bw - 14, bw / 2 - 7, bg);
		// Only the value, no percent sign (Viorel v2.21).
		String pc = String.valueOf(Math.round((0.8f + sizeLevel) * 100));
		ctext(true, fit(true, 28, bw - 22, pc), top + bw / 2 - 15, x + bw / 2, pc, ink);
		sizeArea[0] = x; sizeArea[1] = by; sizeArea[2] = bw; sizeArea[3] = bh;
		int track = (barTrack & 0xFFFFFF) | 0x80000000;
		float f = bw + Math.max(0, Math.min(1, sizeLevel)) * (bh - bw);
		g.roundRect(x, by, bw, bh, bw / 2, track);
		g.roundRect(x, by + bh - f, bw, f, bw / 2, barFill);
		ctext(true, 28, by + bh - bw / 2 - 15, x + bw / 2, "A", barIcon);
		ctext(true, 46, by + bw / 2 - 25, x + bw / 2, "A", barIcon);
	}

	private void drawPortrait(float ch) {
		float fb = ch - 76;
		for (Strip st : strips()) st.area[2] = 0;
		btnCity[1] = fb;
		btnLocate[1] = fb;
		g.rect(40, 102, 678, 2, c.line);
		warnArea[2] = 0;
		alerts.area[2] = 0;
		// The town next to the menu button, above the line.
		String label = headName(340);
		float lp = fit(true, 40, 340, label);
		ltext(true, lp, 56 - lp * 0.5f, 124, label, c.ink);
		cityArea[0] = 124; cityArea[1] = 24; cityArea[2] = Math.max(200, g.width(true, lp, label)); cityArea[3] = 64;
		if (w == null) {
			String msg = error.length() > 0 ? error : T.loading;
			float p = fit(true, 40, 640, msg);
			ctext(true, p, 300, W / 2, msg, c.ink);
			footer(fb);
			return;
		}

		// The five days are one row of boxes now (no small columns): their room goes to the weather now.
		// Proposal (farmer, merged boxes): the boxes are lower, the weather now gets their room.
		boolean prop = farmer && farmMerged;
		float MH = 286, grow = prop ? 494 - MH - 18 : 0;
		float nowBot = 663 + grow + Math.max(0, ch - BASE_H);
		float fy = nowBot - 4;
		dots(W / 2, 112, pages, page);
		// v3.50 (Viorel): the "Statistics · PDF" button under the information / warning / earthquake
		// boxes when they show; in their place (under the top line) when they do not.
		float top = drawWarnBoxes(40, 146, 678, 2, 1), sb;
		if (top > 0) {
			sb = statsButton(40, top + 12, 58, 400, 26) > 40 ? 70 : 0;
			top += sb;
		} else sb = statsButton(40, 150, 58, 400, 26) > 40 ? 72 : 0;
		String[] d = T.describe(w.code, w.isDay);
		float nt = top > 0 ? top + 10 : pages > 1 ? 124 + sb : 104;
		if (prop) drawNowHalves(d[0], 40, nt, 718, fy - 8, fy + 4 - grow);
		else drawNowStack(d[0], 40, nt, 718, fy + 4);
		setNow(40, nt, 678, fy + 80 - nt);

		// Description and wind on the right, "feels like" and high / low on the left.
		if (prop) {
			nowTexts(d[1], fy - 26, 40, 718);
		} else {
		float sp = skySize(39, 330, d[1]);
		rtext(true, sp, fy, 712, d[1], c.ink);
		windLine(30, fy + 40, 712);
		if (!Double.isNaN(w.feels)) ltext(true, sp, fy, 40, feelsText(), c.ink);
		if (!Double.isNaN(w.tmax) && !Double.isNaN(w.tmin))
			ltext(false, 30, fy + 40, 40, upper(T.hi + " " + t(w.tmax) + "° / " + T.lo + " " + t(w.tmin) + "°"), c.ink);
		}

		float y0 = fy + 80;
		// Both lines sit 15 units from the boxes above and under them.
		// The precipitation box takes the whole row; the hour boxes after it still show the next one's edge.
		if (prop) {
			drawMergedRow(40, y0, 678, MH);
			y0 -= 494 - MH - 0;
			y0 += 0;
		} else if (false) drawMergedRow(40, y0, 678, 494);
		else {
		chartFull = true;
		drawNextHours(40, y0, 678, 264, false);
		chartFull = false;
		g.rect(40, y0 + 279, 678, 2, c.line);
		}
		if (farmer && farmMerged) { }
		else if (farmer) drawRainRow(40, y0 + 296, 678, 198);
		else drawDayStrip(40, y0 + 296, 678);
		// A line over the row of info boxes.
		g.rect(40, y0 + 509, 678, 2, c.line);
		drawInfoStrip(40, y0 + 526, 678, 184);
		footer(fb);
	}

	/**
	 * Landscape in three parts: the weather now on the left; a line; in the middle the next hours
	 * (or the precipitation chart) on top and the five days under them, both moved sideways by the
	 * finger; on the right the info boxes two by two, moved up and down.
	 */
	private void drawLandscape(float ch) {
		float fb = ch - 76;
		btnCity[1] = fb;
		btnLocate[1] = fb;
		for (Strip st : strips()) st.area[2] = 0;
		float z1 = Math.round(cw * 0.45f), z2 = Math.round(cw * 0.75f), leftR = z1 - 11;
		float mx = z1 + 11, mw = z2 - 11 - mx, rx = z2 + 11, rw = cw - 40 - rx;
		updHead = true;
		g.rect(40, 102, cw - 80, 2, c.line);
		String label = headName(cw - 124 - 380);
		float lp = fit(true, 40, cw - 124 - 380, label);
		ltext(true, lp, 56 - lp * 0.5f, 124, label, c.ink);
		cityArea[0] = 124; cityArea[1] = 24; cityArea[2] = Math.max(200, g.width(true, lp, label)); cityArea[3] = 64;
		if (w == null) {
			String msg = error.length() > 0 ? error : T.loading;
			ctext(true, fit(true, 40, cw - 200, msg), 330, cw / 2, msg, c.ink);
			footer(fb);
			return;
		}
		warnArea[2] = 0;
		alerts.area[2] = 0;
		dots((40 + leftR) / 2, 110, pages, page);
		float wt = pages > 1 && !tagLow ? 130 : 116;
		float wb = drawWarnBoxes(40, wt, leftR - 40, 2, 1);
		float nowTop = wb > 0 ? wb + 14 : wt;
		float fy = 568;
		String[] d = T.describe(w.code, w.isDay);
		drawNowSide(d[0], 40, nowTop - 60, leftR, fy + 4);
		setNow(40, nowTop, leftR - 40, fy + 80 - nowTop);
		if (farmer && farmMerged) nowTexts(d[1], fy - 10, 40, leftR - 6);
		else {
		float sp = skySize(39, (leftR - 40) / 2, d[1]);
		rtext(true, sp, fy, leftR - 6, d[1], c.ink);
		windLine(30, fy + 40, leftR - 6);
		if (!Double.isNaN(w.feels)) ltext(true, sp, fy, 40, feelsText(), c.ink);
		if (!Double.isNaN(w.tmax) && !Double.isNaN(w.tmin))
			ltext(false, 30, fy + 40, 40, upper(T.hi + " " + t(w.tmax) + "° / " + T.lo + " " + t(w.tmin) + "°"), c.ink);
		}
		// Lines between the three parts, like the one under the clock.
		g.rect(z1 - 1, 124, 2, fb - 14 - 124, c.line);
		g.rect(z2 - 1, 124, 2, fb - 14 - 124, c.line);

		float bt = 124, bb = fb - 14, hh = 250;
		peekW = 15;
		// The rain chart takes the whole width here; the hour boxes follow it when dragged.
		if (farmer && farmMerged) drawMergedColumn(mx, bt, mw, bb - bt);
		else {
		chartFull = true;
		drawNextHours(mx, bt, mw, hh, false);
		chartFull = false;
		}
		if (farmer && farmMerged) { }
		else if (farmer) drawRainRow(mx, bt + hh + 14, mw, bb - (bt + hh + 14));
		else drawDaysRow(mx, bt + hh + 14, mw, bb - (bt + hh + 14));
		peekW = 30;
		drawInfoGrid(rx, bt, rw, bb - bt);
		footer(fb);
	}

	/** Landscape: the five days as a row moved sideways (as many as fit, the next one peeking in). */
	private void drawDaysRow(float x, float top, float wd, float h) {
		int n = Math.min(5, w.dayDates.size());
		if (n == 0) return;
		float gap = 14, peek = peekW;
		int per = Math.max(2, Math.min(3, (int) ((wd - peek + gap) / (180 + gap))));
		float dw = (wd - peek - per * gap) / per, step = dw + gap;
		float[] starts = new float[n];
		for (int k = 0; k < n; k++) starts[k] = k * step;
		days.set(starts, n * step - gap - wd);
		days.area[0] = x; days.area[1] = top; days.area[2] = wd; days.area[3] = h;
		g.clip(x - 2, top - 2, wd + 4, h + 4);
		for (int k = 0; k < n; k++) {
			float bx = x + k * step - days.scroll, cx = bx + dw / 2;
			if (bx + dw < x - 4 || bx > x + wd + 4) continue;
			box(bx, top, dw, h, 44);
			String title = k == 0 ? T.day1 : k == 1 ? T.day2 : T.weekdayUp(w.dayDates.get(k), true);
			head(top, 44, cx, dw - 20, title, 0);
			String[] dd = T.describe(w.dayCodes.get(k), true);
			String hl = t(w.dayHi.get(k)) + "° / " + t(w.dayLo.get(k)) + "°";
			float p = fit(true, 38, dw - 16, hl), ic = Math.min(90, h - 44 - p - 96);
			float iy = top + 44 + Math.max(6, (h - 44 - ic - p - 86) / 2), ty = iy + ic + 4;
			drawIcon(dd[0], cx - ic / 2, iy, ic);
			ctext(true, p, ty, cx, hl, c.ink);
			ctext(false, fit(false, 25, dw - 14, dd[1]), ty + p + 6, cx, dd[1], c.ink);
			ctext(false, 22, ty + p + 40, cx, T.pop + " " + w.dayPop.get(k) + "%", c.dim);
		}
		g.unclip();
	}

	/**
	 * Tablet: the screen split in two. Landscape: the weather now in the left half, the boxes in
	 * the right one; portrait: the weather now on top (42%), the boxes under it. The weather now
	 * keeps room for three code warnings, shown or not; the boxes (next hours, five days, sun / UV /
	 * pollen) are drawn 20% larger.
	 */
	private void drawTablet(float ch) {
		boolean land = cw > ch;
		float fb = ch - 76;
		btnCity[1] = fb;
		btnLocate[1] = fb;
		g.rect(40, 102, cw - 80, 2, c.line);
		warnArea[2] = 0;
		alerts.area[2] = 0;
		for (Strip st : strips()) st.area[2] = 0;
		String label = headName(cw / 2 - 160);
		float lp = fit(true, 40, cw / 2 - 160, label);
		ltext(true, lp, 56 - lp * 0.5f, 124, label, c.ink);
		cityArea[0] = 124; cityArea[1] = 24; cityArea[2] = Math.max(200, g.width(true, lp, label)); cityArea[3] = 64;
		if (w == null) {
			String msg = error.length() > 0 ? error : T.loading;
			ctext(true, fit(true, 48, cw - 200, msg), ch / 3, cw / 2, msg, c.ink);
			footer(fb);
			return;
		}
		// Farmer mode, merged boxes (v2.81, as on the phone): portrait keeps one row of wide boxes
		// under the weather now, which gets the room of the second row.
		boolean merged = farmer && farmMerged;
		float bs = 1.2f, MH = 330, ih = 190;
		float nl = 40, nr = land ? cw / 2 - 20 : cw - 40, nt = land ? (pages > 1 && !tagLow ? 132 : 116) : 146, nb = land ? fb - 20 : ch * 0.42f;
		if (merged && !land) nb = Math.max(nb, fb - 14 - (MH + 14 + ih + 24) * bs);
		dots((nl + nr) / 2, 112, pages, page);
		float wf = 1.25f, wb = drawWarnBoxes(nl, nt, nr - nl, 3, wf);
		float top = Math.max(wb, nt + 130 * wf) + 16;
		float lineY = nb - 76;
		String[] d = T.describe(w.code, w.isDay);
		float mid = nl + (nr - nl) * 0.55f, cy = (top + lineY) / 2, hw = nr - mid;
		icLeft = mid + 2; icRight = nr; icPct = 100;
		drawIconC(d[0], mid + hw / 2, cy, 520, (lineY - top) / 2 - 10, hw - 20);
		icLeft = 0; icRight = W;
		tempSize(420, mid - nl - 30);
		drawTempAt(mid - 12 - tW, cy + tp * 32 / 100);
		setNow(nl, top, nr - nl, lineY + 80 - top);
		if (merged) nowTexts(d[1], lineY - 10, nl, nr);
		else {
		float sp = skySize(44, (nr - nl) / 2 - 12, d[1]);
		rtext(true, sp, lineY, nr - 6, d[1], c.ink);
		windLine(32, lineY + 46, nr - 6);
		if (!Double.isNaN(w.feels)) ltext(true, sp, lineY, nl, feelsText(), c.ink);
		if (!Double.isNaN(w.tmax) && !Double.isNaN(w.tmin))
			ltext(false, 32, lineY + 46, nl, upper(T.hi + " " + t(w.tmax) + "° / " + T.lo + " " + t(w.tmin) + "°"), c.ink);
		}

		// The boxes, in units 1.2 times larger.
		Gfx g0 = g;
		g = new Scaled(g0, bs);
		hs = bs;
		float bx = (land ? cw / 2 + 20 : 40) / bs, bw = (land ? cw / 2 - 60 : cw - 80) / bs, by = (land ? 146 : nb + 24) / bs;
		if (merged) {
			float avail = (fb - 14) / bs - by, mh = avail - ih - 14;
			peekW = land ? 15 : 30;
			if (land) drawMergedColumn(bx, by, bw, mh);
			else drawMergedRow(bx, by, bw, mh);
			peekW = 30;
			for (int k = 0; k < 4; k++) hours.area[k] *= bs;
			drawInfoStrip(bx, by + mh + 14, bw, ih);
			for (int k = 0; k < 4; k++) info.area[k] *= bs;
			g = g0;
			hs = 1;
			footer(fb);
			return;
		}
		float avail = (fb - 14) / bs - by, hh = farmer ? Math.min(420, (avail - ih - 28) / 2) : Math.min(300, (avail - ih - 28) / 2), dh = avail - ih - 28 - hh;
		drawNextHours(bx, by, bw, hh, true);
		for (int k = 0; k < 4; k++) hours.area[k] *= bs;
		float dy = by + hh + 14, dw = (bw - 4 * 12) / 5;
		if (farmer) {
			drawRainRow(bx, dy, bw, dh);
			for (int k = 0; k < 4; k++) rain.area[k] *= bs;
		}
		for (int k = 0; k < (farmer ? 0 : 5); k++) {
			float x = bx + k * (dw + 12), cx = x + dw / 2;
			box(x, dy, dw, dh, 44);
			if (k >= w.dayDates.size()) continue;
			head(dy, 44, cx, dw - 16, k == 0 ? T.day1 : k == 1 ? T.day2 : T.weekdayUp(w.dayDates.get(k), true), 0);
			String[] dd = T.describe(w.dayCodes.get(k), true);
			String hl = t(w.dayHi.get(k)) + "° / " + t(w.dayLo.get(k)) + "°";
			float p = fit(true, 40, dw - 16, hl), ic = Math.max(40, Math.min(Math.min(120, dw - 24), dh - 44 - p - 84));
			float iy = dy + 44 + Math.max(6, (dh - 44 - ic - 6 - p - 62) / 2), ty = iy + ic + 6;
			drawIcon(dd[0], cx - ic / 2, iy, ic);
			ctext(true, p, ty, cx, hl, c.ink);
			ctext(false, fit(false, 23, dw - 12, dd[1]), ty + p + 6, cx, dd[1], c.ink);
			ctext(false, 21, ty + p + 36, cx, T.pop + " " + w.dayPop.get(k) + "%", c.dim);
		}
		drawInfoStrip(bx, dy + dh + 14, bw, ih);
		for (int k = 0; k < 4; k++) info.area[k] *= bs;
		g = g0;
		hs = 1;
		footer(fb);
	}

	/** Draws through G with every length multiplied by S. */
	private static final class Scaled implements Gfx {
		final Gfx g;
		final float s;

		Scaled(Gfx g, float s) {
			this.g = g;
			this.s = s;
		}

		public void rect(float x, float y, float w, float h, int c) {
			g.rect(x * s, y * s, w * s, h * s, c);
		}

		public void roundRect(float x, float y, float w, float h, float r, int c) {
			g.roundRect(x * s, y * s, w * s, h * s, r * s, c);
		}

		public void text(boolean b, float px, float top, float left, String t, int c) {
			g.text(b, px * s, top * s, left * s, t, c);
		}

		public float width(boolean b, float px, String t) {
			return g.width(b, px * s, t) / s;
		}

		public void icon(String a, float x, float y, float w, float h, int c) {
			g.icon(a, x * s, y * s, w * s, h * s, c);
		}

		public void arc(float cx, float cy, float r, float st, float sw, float k, int c) {
			g.arc(cx * s, cy * s, r * s, st, sw, k * s, c);
		}

		public void poly(float[] xy, int c) {
			float[] q = new float[xy.length];
			for (int k = 0; k < xy.length; k++) q[k] = xy[k] * s;
			g.poly(q, c);
		}

		public void clip(float x, float y, float w, float h) {
			g.clip(x * s, y * s, w * s, h * s);
		}

		public void unclip() {
			g.unclip();
		}
	}

	/**
	 * The next hours as a row moved by the finger: first the precipitation chart when rain or snow
	 * is due within 12 hours, then one box per hour (the next one peeks in at the right); a line
	 * under the row.
	 */
	private void drawNextHours(float x, float y, float wd, float h, boolean line) {
		hours.area[2] = 0;
		hoursAlong(false);
		int avail = w.tileHours.size() - 1;
		if (avail < 1) {
			drawPrecipBox(x, y, wd, h, 46);
			return;
		}
		boolean chart = !farmer && rainText().length() > 0;
		float gap = 16, peek = peekW, bh = line ? h - 26 : h;
		int per = Math.max(1, Math.min(avail, (int) ((wd - peek + gap) / (140 + gap))));
		float bw = avail <= per && !chart ? (wd - gap * (avail - 1)) / avail : (wd - peek - gap * per) / per;
		// The chart leaves room for the edge of the first hour box, so it is clear the row goes on.
		float cwd = chartFull ? wd : wd - peek - gap, x0 = chart ? cwd + gap : 0, step = bw + gap;
		float[] starts = new float[avail + (chart ? 1 : 0)];
		int n = 0;
		if (chart) starts[n++] = x0;
		for (int i = 1; i < avail; i++) starts[n++] = x0 + i * step;
		hours.set(java.util.Arrays.copyOf(starts, n), x0 + avail * step - gap - wd);
		hours.area[0] = x; hours.area[1] = y; hours.area[2] = wd; hours.area[3] = bh;
		float tp = 44, dp = 24;
		for (int k = 1; k <= avail; k++) {
			if (!Double.isNaN(w.tileTemp[k])) tp = fit(true, tp, bw - 16, t(w.tileTemp[k]) + "°");
			dp = fit(false, dp, bw - 12, T.describe(w.tileCode[k], w.tileDay[k])[1]);
		}
		float sc = hours.scroll;
		g.clip(x - 2, y - 2, wd + 4, bh + 4);
		if (chart && sc < cwd + 4) drawPrecipBox(x - sc, y, cwd, bh, 46);
		for (int k = 1; k <= avail; k++) {
			float bx = x + x0 + (k - 1) * step - sc;
			if (bx + bw < x - 4 || bx > x + wd + 4) continue;
			drawHourBox(k, bx, y, bw, bh, tp, dp);
		}
		g.unclip();
		if (line) g.rect(x, y + bh + 12, wd, 2, c.line);
	}

	/**
	 * Proposal: the weather now in two halves, the icon centred in the upper one and the
	 * temperature centred in the lower one, at the size they have today (computed for OLDBOT).
	 */
	private void drawNowHalves(String icon, float l, float top, float r, float bot, float oldBot) {
		float y0 = 0, y1 = 200;
		for (String c : CENTRE) {
			String[] p = c.split(" ");
			if (p[0].equals(icon)) { y0 = Float.parseFloat(p[2]); y1 = Float.parseFloat(p[4]); }
		}
		uPct = 34;
		dPct = 27;
		float s = 400, px = 348, room = oldBot - top - 40, cx = (l + r) / 2;
		while (true) {
			tempSize(px, r - l - 40);
			float tot = s * (y1 - y0) / 200 + tp * 15 / 100 + tp * 64 / 100;
			if (tot <= room || s < 120) break;
			s = s * 94 / 100;
			px = px * 94 / 100;
		}
		float ih = s * (y1 - y0) / 200, mid = (top + bot) / 2;
		icLeft = l; icRight = r; icPct = 100; icTop = -100000;
		drawIconC(icon, cx, (top + mid) / 2, s, ih / 2 + 1, r - l);
		icTop = 0;
		// Viorel (2026-11-07): the temperature 10 % larger and nearer the icon, still in the lower half.
		tempSize(tp * 1.1f, r - l - 40);
		float dh = tp * 64 / 100, dTop = Math.max(mid + 24, (mid + bot) / 2 - dh / 2 - (bot - mid) * 0.18f);
		drawTempAt(cx - tW / 2, dTop + dh);
		uPct = 43;
		dPct = 43;
	}

	/**
	 * Proposal: the lines under the weather now a little larger, the wind ring larger; the left
	 * and the right texts each keep to their half, so they never run over each other.
	 */
	/** v3.00: "Feels like 4°", written like the sky's word (bold, not in capitals). */
	private String feelsText() {
		return T.feels + " " + t(w.feels) + "°";
	}

	/** The size of the sky's word and of "Feels like" (the same for both): at most MAX, each within ROOM. */
	private float skySize(float max, float room, String sky) {
		float s = fit(true, max, room, sky);
		if (!Double.isNaN(w.feels)) s = Math.min(s, fit(true, max, room, feelsText()));
		return s;
	}

	private void nowTexts(String sky, float fy, float l, float r) {
		float half = (r - l) / 2 - 12, sz = 34;
		String hl = Double.isNaN(w.tmax) || Double.isNaN(w.tmin) ? "" : upper(T.hi + " " + t(w.tmax) + "° / " + T.lo + " " + t(w.tmin) + "°");
		float lp = fit(false, sz, half, hl);
		// v3.00 (Viorel): "Feels like" written like the sky: bold, the same size, on its line.
		float sp = skySize(44, half, sky);
		if (!Double.isNaN(w.feels)) ltext(true, sp, fy - 10, l, feelsText(), c.ink);
		if (hl.length() > 0) ltext(false, lp, fy + 48, l, hl, c.ink);
		rtext(true, sp, fy - 10, r - 6, sky, c.ink);
		if (!Double.isNaN(w.wind)) {
			String speed = unitF ? Math.round(w.wind * 0.621371) + " mph" : Math.round(w.wind) + " km/h";
			String from = !Double.isNaN(w.windDir) ? Tr.dirs8()[(int) Math.round(((w.windDir % 360) + 360) % 360 / 45) % 8] + " " : "";
			float wp = fit(false, sz, half - sz * 1.7f - 12, upper(T.wind + " " + from + speed));
			windRing = 0.85f;
			windLine(wp, fy + 48, r - 6);
			windRing = 0.62f;
		}
	}

	/**
	 * Farmer mode, proposal: one box per hour, twice as wide, with the hour's weather (icon,
	 * temperature, sky) on the left and its rain (chance, bar, mm, kind) on the right.
	 */
	private void drawMergedRow(float x, float y, float wd, float h) {
		hours.area[2] = 0;
		rain.area[2] = 0;
		hoursAlong(false);
		mergedShown = true;
		int avail = w.tileHours.size() - 1;
		if (avail < 1) return;
		float gap = 16, peek = peekW;
		int per = Math.max(1, Math.min(avail, (int) ((wd - peek + gap) / (280 + gap))));
		float bw = (wd - peek - gap * per) / per, step = bw + gap;
		float[] starts = new float[avail];
		for (int i = 0; i < avail; i++) starts[i] = i * step;
		hours.set(starts, avail * step - gap - wd);
		hours.area[0] = x; hours.area[1] = y; hours.area[2] = wd; hours.area[3] = h;
		g.clip(x - 2, y - 2, wd + 4, h + 4);
		for (int k = 1; k <= avail; k++) {
			float bx = x + (k - 1) * step - hours.scroll;
			if (bx + bw < x - 4 || bx > x + wd + 4) continue;
			drawMergedBox(k, bx, y, bw, h);
		}
		g.unclip();
	}

	/** Landscape, proposal: the same boxes as wide as the middle part, two one over the other, moved up and down. */
	private void drawMergedColumn(float x, float y, float wd, float h) {
		hours.area[2] = 0;
		rain.area[2] = 0;
		hoursAlong(true);
		mergedShown = true;
		int avail = w.tileHours.size() - 1;
		if (avail < 1) return;
		float gap = 14, peek = 26, bh = (h - peek - 2 * gap) / 2, step = bh + gap;
		float[] starts = new float[avail];
		for (int i = 0; i < avail; i++) starts[i] = i * step;
		hours.set(starts, avail * step - gap - h);
		hours.area[0] = x; hours.area[1] = y; hours.area[2] = wd; hours.area[3] = h;
		g.clip(x - 2, y - 2, wd + 4, h + 4);
		for (int k = 1; k <= avail; k++) {
			float by = y + (k - 1) * step - hours.scroll;
			if (by + bh < y - 4 || by > y + h + 4) continue;
			drawMergedBox(k, x, by, wd, bh);
		}
		g.unclip();
	}

	/** The hours move sideways, or up and down (landscape, merged boxes); turning starts again at the first hour. */
	private void hoursAlong(boolean vertical) {
		if (hours.vertical == vertical) return;
		hours.vertical = vertical;
		hours.scroll = 0;
	}

	/** One merged box: the hour on top; its weather in the left half, its rain in the right half. */
	private void drawMergedBox(int k, float bx, float y, float bw, float h) {
		float hd = 46, half = bw / 2, body = h - hd;
		if (farmer) hourHits.add(new float[] { k, bx * hs, y * hs, bw * hs, h * hs });
		box(bx, y, bw, h, hd);
		head(y, hd, bx + bw / 2, bw - 12, Texts.clock(w.tileHours.get(k), clk12), 0);
		g.rect(bx + half - 1, y + hd + 14, 2, body - 28, c.line);
		String[] d = T.describe(w.tileCode[k], w.tileDay[k]);
		String tt = Double.isNaN(w.tileTemp[k]) ? "–" : t(w.tileTemp[k]) + "°";
		int p = probAt(k), kind = kindAt(k);
		String pc = p + "%";
		double a = mmAt(k);
		String amt = a >= 0.05 ? mm(a) : "0 " + (unitF ? "in" : "mm");
		// Viorel (2026-11-07): "Type: rain", or "no precipitation".
		String kw = kind == 0 ? Tr.t("no precipitation", "fără precipitații") : Tr.t("Type: ", "Tip: ") + kindWord(kind);
		// The sky and the kind of rain in larger letters near the bottom line, centred in their half.
		float lx = bx + half / 2, rx = bx + half + half / 2;
		// Viorel: as large as the box allows, in the light ink (not grey), each fitted to its half.
		float labMax = Math.min(42, body * 0.2f);
		float labL = fit(true, labMax, half - 20, d[1]), labR = fit(true, labMax, half - 20, kw), lab = Math.max(labL, labR);
		float labY = y + h - 14 - lab;
		ctext(true, labL, labY + (lab - labL) / 2, lx, d[1], c.ink);
		ctext(true, labR, labY + (lab - labR) / 2, rx, kw, c.ink);
		float room = labY - 10 - (y + hd);
		if (body >= 200) {
			// Tall: icon over temperature; chance over bar over mm.
			float tp = fit(true, 50, half - 16, tt);
			float ic = Math.min(Math.min(96, half * 0.66f), room - tp - 26);
			float top = y + hd + (room - (ic + 8 + tp)) / 2;
			drawIcon(Weather.iconOf(w.tileCode[k], w.tileDay[k]), lx - ic / 2, top, ic);
			ctext(true, tp, top + ic + 8, lx, tt, c.ink);
			float pp = fit(true, 50, half - 16, pc);
			float rt = y + hd + (room - (pp + 16 + 12 + 12 + 28)) / 2;
			ctext(true, pp, rt, rx, pc, p >= Weather.RAIN_PROBABILITY ? c.ink : c.dim);
			float by = rt + pp + 16, bl = bx + half + 14, bwid = half - 28;
			g.roundRect(bl, by, bwid, 12, 6, c.dry);
			if (p > 0) g.roundRect(bl, by, Math.max(12, bwid * p / 100), 12, 6, kind == 3 ? c.snow : c.rain);
			ctext(false, fit(false, 28, half - 14, amt), by + 24, rx, amt, c.ink);
		} else {
			// Low and wide (landscape): icon beside temperature; chance beside bar and mm.
			float ic = Math.min(room - 16, half * 0.34f), ix = bx + 22, iy = y + hd + (room - ic) / 2;
			drawIcon(Weather.iconOf(w.tileCode[k], w.tileDay[k]), ix, iy, ic);
			float tx = ix + ic + 12, tw = bx + half - 14 - tx, tc = tx + tw / 2;
			float tp = fit(true, 52, tw, tt);
			ctext(true, tp, y + hd + (room - tp) / 2, tc, tt, c.ink);
			float rl = bx + half + 18, rw = half - 36, pw = rw * 0.44f;
			float pp = fit(true, 50, pw, pc);
			ctext(true, pp, y + hd + (room - pp) / 2, rl + pw / 2, pc, p >= Weather.RAIN_PROBABILITY ? c.ink : c.dim);
			float ml = rl + pw + 12, mw = rw - pw - 12, mc = ml + mw / 2;
			float mt = y + hd + (room - (12 + 10 + 28)) / 2;
			g.roundRect(ml, mt, mw, 12, 6, c.dry);
			if (p > 0) g.roundRect(ml, mt, Math.max(12, mw * p / 100), 12, 6, kind == 3 ? c.snow : c.rain);
			ctext(false, fit(false, 28, mw, amt), mt + 22, mc, amt, c.ink);
		}
	}

	/** One hour: the hour on top, the icon, the temperature and the sky. */
	private void drawHourBox(int k, float x, float y, float bw, float bh, float tp, float dp) {
		float hd = 44, cx = x + bw / 2;
		if (farmer) hourHits.add(new float[] { k, x * hs, y * hs, bw * hs, bh * hs });
		box(x, y, bw, bh, hd);
		head(y, hd, cx, bw - 12, Texts.clock(w.tileHours.get(k), clk12), 0);
		String[] d = T.describe(w.tileCode[k], w.tileDay[k]);
		float room = bh - hd - tp - dp - 34, ic = Math.min(Math.min(96, bw * 0.6f), room);
		float top = y + hd + (bh - hd - (ic + 4 + tp + 6 + dp)) / 2;
		drawIcon(Weather.iconOf(w.tileCode[k], w.tileDay[k]), cx - ic / 2, top, ic);
		float ty = top + ic + 4;
		if (!Double.isNaN(w.tileTemp[k])) ctext(true, tp, ty, cx, t(w.tileTemp[k]) + "°", c.ink);
		ctext(false, dp, ty + tp + 6, cx, d[1], c.dim);
	}

	/** N dots centred on CX, the current one in ink (nothing for a single page). */
	/** Room taken at the left under the top line by "FARMER MODE" (the "updated" text keeps clear of it). */
	private float farmTagW;

	/**
	 * Farmer mode (v2.63, Viorel): "FARMER MODE" under the top line, left under the ☰ button, at the
	 * height of the dots and of "updated"; it keeps clear of the dots centred at CX.
	 */
	private boolean tagLow, statsLow;

	private void farmTag(float cx, int n) {
		farmTagW = 0;
		if (!farmer || tagLow) return;
		String s = Tr.t("FARMER MODE", "MODUL FERMIER");
		float maxw = n > 1 ? cx - (n * 22 - 10) / 2f - 24 - 40 : 380;
		float p = fit(true, 25, Math.max(60, maxw), s);
		ltext(true, p, 110 + (25 - p) / 2, 40, s, c.accent);
		farmTagW = g.width(true, p, s) + 30;
	}

	/**
	 * v3.10 (Viorel): on a parcel's main screen a "Statistics · PDF" button; phone portrait under
	 * "FARMER MODE", otherwise in the status row right of the buttons. Not on the town's screen.
	 */
	public final float[] btnStats = { 0, 0, 0, 0 };

	private float statsButton(float x, float y, float h, float maxW, float px) {
		btnStats[2] = 0;
		if (parcel == null || !farmer) return x;
		String s = Tr.t("Statistics · PDF", "Statistică · PDF");
		float p = fit(true, px, Math.max(60, maxW - 36), s), bw = g.width(true, p, s) + 36;
		btnStats[0] = x; btnStats[1] = y; btnStats[2] = bw; btnStats[3] = h;
		button(btnStats);
		ltext(true, p, y + h / 2 - p * 0.56f, x + 18, s, c.ink);
		return x + bw;
	}

	/** Landscape (v3.10, Viorel): the dots go down into the status row, centred. */
	private int lowPages, lowPage;

	private void dots(float cx, float y, int n, int cur) {
		farmTag(cx, n);
		lowPages = 0;
		if (tagLow) { lowPages = n; lowPage = cur; return; }
		if (n <= 1) return;
		float dx = cx - (n * 22 - 10) / 2f;
		for (int k = 0; k < n; k++) g.roundRect(dx + k * 22, y, 12, 12, 6, k == cur ? c.ink : c.dim);
	}

	// --- Farmer mode (v2.50) -----------------------------------------------------------

	/** The index in FARM of tile hour K (-1 when FARM lacks it). */
	public int farmIndex(int k) {
		if (farm == null || w == null || w.tileHours.isEmpty() || k < 0 || k >= w.tileHours.size()) return -1;
		int cur = farm.nowIndex(now), start = Math.max(0, cur < 0 ? 0 : cur - 3);
		String first = w.tileHours.get(0);
		for (int i = start; i < farm.size() && i < start + 30; i++) {
			if (farm.hourOf(i).equals(first)) return farm.has(i + k) ? i + k : -1;
		}
		return -1;
	}

	/** The chance (%) of rain in tile hour K: from the farmer's forecast, else the usual one. */
	private int probAt(int k) {
		int i = farmIndex(k);
		if (i >= 0 && !Double.isNaN(farm.prob[i])) return (int) Math.round(farm.prob[i]);
		return k < w.tileProb.length ? w.tileProb[k] : 0;
	}

	private double mmAt(int k) {
		int i = farmIndex(k);
		if (i >= 0 && !Double.isNaN(farm.precip[i])) return farm.precip[i];
		return k < w.tileAmt.length ? w.tileAmt[k] : 0;
	}

	/** 0 dry, 1 rain, 2 showers, 3 snow, 4 sleet (rain and snow) of tile hour K. */
	private int kindAt(int k) {
		int i = farmIndex(k);
		double mm = mmAt(k);
		if (i >= 0) {
			double sn = nz(farm.snow[i]), ra = nz(farm.rain[i]), sh = nz(farm.showers[i]);
			if (sn > 0 && ra + sh > 0) return 4;
			if (sn > 0) return 3;
			if (sh > ra && sh > 0) return 2;
			if (ra > 0 || mm > 0) return 1;
		} else if (mm > 0) return Weather.snowCode(w.tileCode[k]) ? 3 : 1;
		int p = probAt(k);
		if (p >= 30) return k < w.tileCode.length && Weather.snowCode(w.tileCode[k]) ? 3 : 1;
		return 0;
	}

	private String kindWord(int kind) {
		switch (kind) {
		case 1: return Tr.t("rain", "ploaie");
		case 2: return Tr.t("showers", "averse");
		case 3: return Tr.t("snow", "ninsoare");
		case 4: return Tr.t("sleet", "lapoviță");
		default: return Tr.t("dry", "uscat");
		}
	}

	private static double nz(double v) {
		return Double.isNaN(v) ? 0 : v;
	}

	/** A number with one decimal, the language's decimal point. */
	private static String d1(double v) {
		return String.format(java.util.Locale.US, "%.1f", v).replace('.', Tr.dot());
	}

	/** Millimetres (or inches when imperial). */
	private String mm(double v) {
		return unitF ? String.format(java.util.Locale.US, "%.2f", v / 25.4).replace('.', Tr.dot()) + " in" : d1(v) + " mm";
	}

	private String kmh(double v) {
		return unitF ? Math.round(v * 0.621371) + " mph" : Math.round(v) + " km/h";
	}

	private String deg(double v) {
		return Double.isNaN(v) ? "–" : t(v) + "°";
	}

	private static String dir8(double deg) {
		return Double.isNaN(deg) ? "–" : Tr.dirs8()[Farm.sector(deg)];
	}

	/**
	 * Farmer mode: the hours as rain-chance boxes, moved sideways like the hour boxes (each: the
	 * hour, the chance in big digits, a bar as full as the chance, the amount and the kind).
	 */
	private void drawRainRow(float x, float y, float wd, float h) {
		rain.area[2] = 0;
		int avail = w.tileHours.size() - 1;
		if (avail < 1) return;
		float gap = 16, peek = peekW;
		int per = Math.max(1, Math.min(avail, (int) ((wd - peek + gap) / (140 + gap))));
		float bw = (wd - peek - gap * per) / per, step = bw + gap;
		float[] starts = new float[avail];
		for (int i = 0; i < avail; i++) starts[i] = i * step;
		rain.set(starts, avail * step - gap - wd);
		rain.area[0] = x; rain.area[1] = y; rain.area[2] = wd; rain.area[3] = h;
		float hd = 44, pp = Math.min(54, (h - hd) * 0.34f);
		g.clip(x - 2, y - 2, wd + 4, h + 4);
		for (int k = 1; k <= avail; k++) {
			float bx = x + (k - 1) * step - rain.scroll, cx = bx + bw / 2;
			if (bx + bw < x - 4 || bx > x + wd + 4) continue;
			rainHits.add(new float[] { k, bx * hs, y * hs, bw * hs, h * hs });
			box(bx, y, bw, h, hd);
			head(y, hd, cx, bw - 12, Texts.clock(w.tileHours.get(k), clk12), 0);
			int p = probAt(k), kind = kindAt(k);
			float body = h - hd, top = y + hd + Math.max(6, (body - pp - 22 - 26 - 24) / 2 - 4);
			String pc = p + "%";
			ctext(true, fit(true, pp, bw - 16, pc), top, cx, pc, p >= Weather.RAIN_PROBABILITY ? c.ink : c.dim);
			float by = top + pp + 8, bl = bx + 14, bwid = bw - 28;
			g.roundRect(bl, by, bwid, 12, 6, c.dry);
			if (p > 0) g.roundRect(bl, by, Math.max(12, bwid * p / 100), 12, 6, kind == 3 ? c.snow : c.rain);
			double a = mmAt(k);
			String amt = a >= 0.05 ? mm(a) : "0 " + (unitF ? "in" : "mm");
			ctext(false, fit(false, 24, bw - 12, amt), by + 22, cx, amt, c.ink);
			String kw = kindWord(kind);
			ctext(false, fit(false, 22, bw - 12, kw), by + 52, cx, kw, c.dim);
		}
		g.unclip();
	}

	/** Farmer mode: the box under X, Y: { 0 hour box / 1 rain box, hour index }; null for none. */
	public int[] farmBoxAt(float x, float y) {
		if (!farmer || w == null) return null;
		for (Strip st : new Strip[] { info, grid }) {
			if (st.area[2] <= 0 || x < st.area[0] || x > st.area[0] + st.area[2] || y < st.area[1] || y > st.area[1] + st.area[3]) continue;
			for (float[] b : infoHits)
				if (x >= b[1] && x <= b[1] + b[3] && y >= b[2] && y <= b[2] + b[4]) return new int[] { 2, (int) b[0] };
		}
		for (int kind = 0; kind < 2; kind++) {
			Strip st = kind == 0 ? hours : rain;
			if (st.area[2] <= 0 || x < st.area[0] || x > st.area[0] + st.area[2] || y < st.area[1] || y > st.area[1] + st.area[3]) continue;
			for (float[] b : kind == 0 ? hourHits : rainHits)
				if (x >= b[1] && x <= b[1] + b[3] && y >= b[2] && y <= b[2] + b[4]) return new int[] { kind, (int) b[0] };
		}
		return null;
	}

	/** Where box KIND / K is (x, y, w, h), for the bubble's arrow. */
	public float[] farmBoxRect(int kind, int k) {
		if (kind == 2) {
			for (float[] b : infoHits) if ((int) b[0] == k) return new float[] { b[1], b[2], b[3], b[4] };
			return new float[] { cw / 2 - 50, 900, 100, 100 };
		}
		for (float[] b : kind == 0 ? hourHits : rainHits) if ((int) b[0] == k) return new float[] { b[1], b[2], b[3], b[4] };
		return new float[] { cw / 2 - 50, 400, 100, 100 };
	}

	/** The notice when the farmer's data did not come (Viorel: say they come and are notified later). */
	public static String farmMissing() {
		return Tr.t("The farmer's data (hourly wind, humidity, soil) come only from Open-Meteo, which is not answering now. When access is back, the app downloads them by itself.",
				"Datele pentru fermieri (vânt orar, umiditate, sol) vin doar de la Open-Meteo, care nu răspunde acum. Când accesul revine, aplicația le descarcă singură.");
	}

	/** The Open-Meteo source line (v3.20: in a bubble or on a page only once, at the bottom). */
	public static String srcOM() {
		return Tr.t("Source: Open-Meteo (open-meteo.com), weather models.", "Sursa: Open-Meteo (open-meteo.com), modele meteo.");
	}

	/** The soil values are estimates (v3.20: without the source, which comes at the bottom). */
	public static String soilEst() {
		return Tr.t("Soil moisture and soil temperature are estimates of the weather model, not measurements.",
				"Umiditatea și temperatura solului sunt estimări ale modelului meteo, nu măsurători.");
	}

	private static String join(String[] a) {
		StringBuilder b = new StringBuilder();
		for (int k = 0; k < a.length; k++) b.append(k > 0 ? "\t" : "").append(a[k]);
		return b.toString();
	}

	/** A table for a bubble (v3.20): { "T", header, rows… }, the cells separated by tabs. */
	static String[] table(String[] head, List<String[]> rows) {
		String[] out = new String[rows.size() + 2];
		out[0] = "T";
		out[1] = join(head);
		for (int k = 0; k < rows.size(); k++) out[k + 2] = join(rows.get(k));
		return out;
	}

	/** "Estimated data" note for the soil values (Viorel: say they are estimated and from where). */
	public static String soilNote() {
		return Tr.t("Soil moisture and soil temperature are estimates of the weather model, not measurements · source: Open-Meteo (open-meteo.com).",
				"Umiditatea și temperatura solului sunt estimări ale modelului meteo, nu măsurători · sursa: Open-Meteo (open-meteo.com).");
	}

	/** Soil moisture as percent of volume and in words. */
	public String soilText(double m) {
		if (Double.isNaN(m)) return "–";
		int lv = Farm.soilLevel(m);
		String[] words = { Tr.t("dry", "uscat"), Tr.t("moderate", "potrivit"), Tr.t("moist", "umed"), Tr.t("very wet", "foarte umed") };
		return Math.round(m * 100) + "% · " + words[lv];
	}

	/** The title of hour K: "15:00 · Sâm 7". */
	private String hourTitle(int k) {
		String hr = Texts.clock(w.tileHours.get(k), clk12);
		int i = farmIndex(k);
		String date = i >= 0 ? farm.dateOf(i) : "";
		return date.length() == 10 ? hr + " · " + T.weekdayUp(date, true) : hr;
	}

	/**
	 * Farmer mode, the hour box K: the details for the bubble, first { "title", … }, then
	 * { "h" header | "t" text, … }.
	 */
	public List<String[]> hourInfo(int k) {
		List<String[]> out = new java.util.ArrayList<String[]>();
		if (w == null || k < 0 || k >= w.tileHours.size()) return out;
		out.add(new String[] { "title", hourTitle(k) });
		String[] d = T.describe(w.tileCode[k], w.tileDay[k]);
		out.add(new String[] { "v", Tr.t("Temp.: ", "Temp.: ") + deg(w.tileTemp[k]) + " · " + d[1] });
		int i = farmIndex(k);
		if (i < 0) {
			out.add(new String[] { "v", Tr.t("Rain chance: ", "Prob. ploaie: ") + probAt(k) + "% · " + mm(mmAt(k)) });
			out.add(new String[] { "t", farmMissing() });
			return out;
		}
		int di = farm.dayIndex(farm.dateOf(i));
		if (di >= 0) out.add(new String[] { "v", Tr.t("Day min. / max.: ", "Min. / max. zi: ") + deg(farm.dMin[di]) + " / " + deg(farm.dMax[di]) });
		out.add(new String[] { "h", Tr.t("Wind", "Vânt") });
		if (!Double.isNaN(farm.wind[i]))
			out.add(new String[] { "v", Tr.t("Speed: ", "Viteza: ") + kmh(farm.wind[i]) + (Double.isNaN(farm.gust[i]) ? "" : " · " + Tr.t("gusts ", "rafale ") + kmh(farm.gust[i])) });
		out.add(new String[] { "v", Tr.t("Direction: from ", "Direcția: dinspre ") + dir8(farm.dir[i]) + (Double.isNaN(farm.dir[i]) ? "" : " (" + Math.round(farm.dir[i]) + "°)") });
		int sh = Farm.windShift(farm, i, 12);
		if (sh >= 0)
			out.add(new String[] { "t", Tr.t("Change: it turns from ", "Schimbare: se întoarce dinspre ") + dir8(farm.dir[i]) + Tr.t(" to ", " spre ") + dir8(farm.dir[sh])
					+ Tr.t(" around ", " în jurul orei ") + Texts.clock(farm.hourOf(sh), clk12) });
		else if (!Double.isNaN(farm.dir[i]))
			out.add(new String[] { "t", Tr.t("Change: it keeps its direction for the next 12 hours", "Schimbare: își păstrează direcția în următoarele 12 ore") });
		out.add(new String[] { "h", Tr.t("Air and rain", "Aer și ploaie") });
		if (!Double.isNaN(farm.hum[i])) out.add(new String[] { "v", Tr.t("Air hum.: ", "Umid. aer: ") + Math.round(farm.hum[i]) + "%" });
		out.add(new String[] { "v", Tr.t("Rain chance: ", "Prob. ploaie: ") + probAt(k) + "% · " + mm(mmAt(k)) });
		out.add(new String[] { "h", Tr.t("Soil (estimated)", "Sol (estimat)") });
		out.add(new String[] { "v", Tr.t("Soil moist. 3–9 cm: ", "Umid. sol 3–9 cm: ") + soilText(farm.soilM[i]) });
		out.add(new String[] { "v", Tr.t("Soil temp. (surf.): ", "Temp. sol (supraf.): ") + deg(farm.soilT[i]) });
		out.add(new String[] { "t", soilEst() });
		out.add(new String[] { "s", srcOM() });
		return out;
	}

	/** Farmer mode, a merged box K (v2.70): the hour's details, then its rain, in one bubble. */
	public List<String[]> hourRainInfo(int k) {
		List<String[]> out = hourInfo(k);
		if (out.isEmpty() || farmIndex(k) < 0) return out;
		List<String[]> r = rainInfo(k);
		out.add(new String[] { "h", Tr.t("Rain", "Ploaie") });
		for (int j = 1; j < r.size(); j++) out.add(r.get(j));
		return out;
	}

	/** Farmer mode, the rain box K: the details for the bubble (as hourInfo). */
	public List<String[]> rainInfo(int k) {
		List<String[]> out = new java.util.ArrayList<String[]>();
		if (w == null || k < 0 || k >= w.tileHours.size()) return out;
		out.add(new String[] { "title", Tr.t("Rain · ", "Ploaie · ") + hourTitle(k) });
		int p = probAt(k), kind = kindAt(k), i = farmIndex(k);
		String chance = p >= 60 ? Tr.t("likely", "probabilă") : p >= 30 ? Tr.t("possible", "posibilă") : Tr.t("unlikely", "puțin probabilă");
		out.add(new String[] { "v", Tr.t("Rain chance: ", "Prob. ploaie: ") + p + "% · " + chance });
		out.add(new String[] { "v", Tr.t("Amount: ", "Cantitate: ") + mm(mmAt(k)) });
		out.add(new String[] { "v", Tr.t("Kind: ", "Tip: ") + kindWord(kind) });
		if (i < 0) {
			out.add(new String[] { "t", farmMissing() });
			return out;
		}
		if (nz(farm.rain[i]) + nz(farm.showers[i]) + nz(farm.snow[i]) > 0) {
			StringBuilder b = new StringBuilder();
			if (nz(farm.rain[i]) > 0) b.append(Tr.t("rain ", "ploaie ")).append(mm(farm.rain[i]));
			if (nz(farm.showers[i]) > 0) b.append(b.length() > 0 ? " · " : "").append(Tr.t("showers ", "averse ")).append(mm(farm.showers[i]));
			if (nz(farm.snow[i]) > 0) b.append(b.length() > 0 ? " · " : "").append(Tr.t("snow ", "ninsoare ")).append(unitF ? d1(farm.snow[i] / 2.54) + " in" : d1(farm.snow[i]) + " cm");
			out.add(new String[] { "v", Tr.t("Of which: ", "Din care: ") + b });
		}
		if (!Double.isNaN(farm.cloud[i])) out.add(new String[] { "v", Tr.t("Cloud cover: ", "Nebulozitate: ") + Math.round(farm.cloud[i]) + "%" });
		if (!Double.isNaN(farm.hum[i])) out.add(new String[] { "v", Tr.t("Air hum.: ", "Umid. aer: ") + Math.round(farm.hum[i]) + "%" });
		// The rest of the day: how much rain and when it stops.
		String date = farm.dateOf(i);
		double tot = 0;
		for (int j = i; j < farm.size() && farm.dateOf(j).equals(date); j++) tot += nz(farm.precip[j]);
		out.add(new String[] { "v", Tr.t("Until 24:00: ", "Până la 24:00: ") + mm(tot) });
		out.add(new String[] { "t", Tr.t("The chance is how likely rain is in that hour, not how long it rains.", "Probabilitatea arată cât de probabil plouă în acea oră, nu cât timp plouă.") });
		out.add(new String[] { "s", srcOM() });
		return out;
	}

	// --- Parcels as main screens (v2.60) ----------------------------------------------

	// --- v3.10: the farmer's new boxes next to Sun (Viorel 2026-11-08) -------------------

	/** The US data of the place shown (NWS grid, drought, storm reports); null outside the US. */
	public UsFarm.Data us;

	/** The new farmer boxes: "f…" everywhere (Open-Meteo), "u…" only in the US. */
	static boolean xbox(String id) {
		return id.length() > 1 && (id.charAt(0) == 'f' || id.charAt(0) == 'u') && Character.isUpperCase(id.charAt(1));
	}

	static final String[] FARM_BOXES = { "fSpray", "fWater", "fStorm", "fGdd", "fTrend" };
	static final String[] US_BOXES = { "uNws", "uDrought", "uFire", "uHail" };

	private String xTitle(String id) {
		if (id.equals("fSpray")) return Tr.t("SPRAYING", "STROPIT");
		if (id.equals("fWater")) return Tr.t("WATER BALANCE", "BILANȚ APĂ");
		if (id.equals("fStorm")) return Tr.t("STORMS", "FURTUNI");
		if (id.equals("fGdd")) return Tr.t("GROWING DEGREES", "GRADE-ZILE");
		if (id.equals("fTrend")) return Tr.t("16 DAYS", "16 ZILE");
		if (id.equals("uNws")) return "NWS";
		if (id.equals("uDrought")) return Tr.t("DROUGHT", "SECETĂ");
		if (id.equals("uFire")) return Tr.t("FIRE", "INCENDIU");
		return Tr.t("HAIL", "GRINDINĂ");
	}

	private String xLong(String id) {
		if (id.equals("fSpray")) return Tr.t("Spraying window", "Fereastra de stropit");
		if (id.equals("fWater")) return Tr.t("Water balance", "Bilanțul apei");
		if (id.equals("fStorm")) return Tr.t("Storm risk", "Riscul de furtună");
		if (id.equals("fGdd")) return Tr.t("Growing degree days", "Grade-zile de creștere");
		if (id.equals("fTrend")) return Tr.t("Trend for 16 days", "Tendința pe 16 zile");
		if (id.equals("uNws")) return Tr.t("Official forecast (NWS)", "Prognoza oficială (NWS)");
		if (id.equals("uDrought")) return Tr.t("Drought (US Drought Monitor)", "Seceta (US Drought Monitor)");
		if (id.equals("uFire")) return Tr.t("Fire danger and lightning", "Pericol de incendiu și fulgere");
		return Tr.t("Hail and wind reports", "Rapoarte de grindină și vânt");
	}

	private String sprayWhy(int s) {
		if (s == 0) return Tr.t("good", "bun");
		if (s == 1) return Tr.t("too little wind", "vânt prea slab");
		if (s == 2) return Tr.t("too much wind", "vânt prea tare");
		if (s == 3) return Tr.t("rain", "ploaie");
		if (s == 4) return Tr.t("too hot", "prea cald");
		if (s == 5) return Tr.t("dry air", "aer uscat");
		return "–";
	}

	private String stormWord(int l) {
		if (l == 0) return Tr.t("low", "scăzut");
		if (l == 1) return Tr.t("moderate", "moderat");
		if (l == 2) return Tr.t("high", "ridicat");
		if (l == 3) return Tr.t("very high", "foarte ridicat");
		return "–";
	}

	/** A temperature difference in °C or °F, with its sign. */
	private String dDeg(double d) {
		long v = Math.round(unitF ? d * 9 / 5 : d);
		return (v > 0 ? "+" : v < 0 ? "−" : "") + Math.abs(v) + "°";
	}

	/** Millimetres with a sign. */
	private String sMm(double v) {
		return (v > 0.05 ? "+" : v < -0.05 ? "−" : "") + mm(Math.abs(v));
	}

	private String gddText(double v) {
		return Double.isNaN(v) ? "–" : Long.toString(Math.round(unitF ? v * 1.8 : v));
	}

	/** The fire danger class word of the NWS grassland index (1–5), or the number. */
	private String fireWord(double v) {
		if (Double.isNaN(v)) return "–";
		int k = (int) Math.round(v);
		if (k == 1) return Tr.t("low", "scăzut");
		if (k == 2) return Tr.t("moderate", "moderat");
		if (k == 3) return Tr.t("high", "ridicat");
		if (k == 4) return Tr.t("very high", "foarte ridicat");
		if (k >= 5) return Tr.t("extreme", "extrem");
		return Long.toString(Math.round(v));
	}

	/** { value, line under it, second line } of new box ID. */
	private String[] xValues(String id) {
		String[] none = { "–", Tr.t("no data", "fără date"), "" };
		long ms = now.getTimeInMillis();
		if (id.startsWith("u")) {
			if (us == null) return none;
			if (id.equals("uNws")) {
				if (!us.hasGrid()) return new String[] { "–", Tr.t("NWS not answering", "NWS nu răspunde"), "" };
				double hi = us.max("maxTemperature", ms, ms + 24 * 3600000L), lo = us.max("minTemperature", ms, ms + 24 * 3600000L);
				double pop = us.max("probabilityOfPrecipitation", ms, ms + 12 * 3600000L);
				return new String[] { deg(hi) + " / " + deg(lo), Tr.t("rain ", "ploaie ") + (Double.isNaN(pop) ? "–" : Math.round(pop) + "%") + Tr.t(" in 12 h", " în 12 h"), "" };
			}
			if (id.equals("uDrought")) {
				if (us.drought == -2) return none;
				String[] w = { Tr.t("abnormally dry", "anormal de uscat"), Tr.t("moderate", "moderată"), Tr.t("severe", "severă"), Tr.t("extreme", "extremă"),
						Tr.t("exceptional", "excepțională") };
				if (us.drought < 0) return new String[] { Tr.t("none", "fără"), Tr.t("no drought", "fără secetă"), "" };
				return new String[] { "D" + us.drought, w[Math.min(4, us.drought)], "" };
			}
			if (id.equals("uFire")) {
				if (!us.hasGrid()) return new String[] { "–", Tr.t("NWS not answering", "NWS nu răspunde"), "" };
				double f = us.max("grasslandFireDangerIndex", ms, ms + 12 * 3600000L), th = us.max("probabilityOfThunder", ms, ms + 24 * 3600000L);
				return new String[] { fireWord(f), Tr.t("thunder ", "oraje ") + (Double.isNaN(th) ? "–" : Math.round(th) + "%"), "" };
			}
			if (!us.reportsOk) return none;
			int n = us.hail.size() + us.wind.size();
			String big = "";
			double mx = Double.NaN;
			for (Tornadoes.Report r : us.hail) {
				double in = UsFarm.hailInches(r.scale);
				if (!Double.isNaN(in) && (Double.isNaN(mx) || in > mx)) mx = in;
			}
			if (!Double.isNaN(mx)) big = Tr.t("hail up to ", "grindină până la ") + (unitF ? String.format(java.util.Locale.US, "%.2f in", mx) : Math.round(mx * 25.4) + " mm");
			return new String[] { Integer.toString(n), n == 0 ? Tr.t("none in 24–48 h", "niciunul în 24–48 h") : big.length() > 0 ? big : Tr.t("wind reports", "rapoarte de vânt"),
					unitF ? "≤ 31 mi" : "≤ 50 km" };
		}
		int i = farm == null ? -1 : farm.nowIndex(now);
		if (i < 0) return none;
		String today = Farm.date(now, 0);
		if (id.equals("fSpray")) {
			int s = Farm.spray(farm, i);
			if (s == 0) {
				int[] wdw = Farm.sprayWindow(farm, i, 24);
				return new String[] { Tr.t("YES", "DA"), wdw[1] > 0 ? Tr.t("until ", "până la ") + Texts.clock(farm.hourOf(wdw[1]), clk12) : Tr.t("next 24 h", "următoarele 24 h"), "" };
			}
			int[] wdw = Farm.sprayWindow(farm, i + 1, 24);
			return new String[] { Tr.t("NO", "NU"), sprayWhy(s), wdw[0] > 0 ? Tr.t("good from ", "bun de la ") + Texts.clock(farm.hourOf(wdw[0]), clk12) : Tr.t("not in 24 h", "nu în 24 h") };
		}
		if (id.equals("fWater")) {
			double[] b = Farm.waterBalance(farm, today, 7);
			if (Double.isNaN(b[2])) return none;
			return new String[] { sMm(b[2]), Tr.t("7 days: rain − evaporation", "7 zile: ploaie − evaporare"), "" };
		}
		if (id.equals("fStorm")) {
			double[] st = Farm.storm(farm, i, 24);
			int l = Farm.stormLevel(st[0]);
			if (l < 0) return none;
			return new String[] { stormWord(l), "CAPE " + Math.round(st[0]) + " J/kg", st[1] >= 0 ? Tr.t("highest at ", "maxim la ") + Texts.clock(farm.hourOf((int) st[1]), clk12) : "" };
		}
		if (id.equals("fGdd")) {
			double[] g = Farm.gddSum(farm, today, Farm.KEEP_DAYS);
			if (Double.isNaN(g[0])) return none;
			return new String[] { gddText(g[0]), Tr.t("last ", "ultimele ") + Math.round(g[1]) + Tr.t(" days", " zile"), Tr.t("base ", "bază ") + (unitF ? "50°F" : "10°C") };
		}
		double[] tr = Farm.trend(farm, today);
		if (Double.isNaN(tr[1]) || Double.isNaN(tr[0])) return none;
		double dlt = tr[1] - tr[0];
		String word = dlt > 1.5 ? Tr.t("warmer", "mai cald") : dlt < -1.5 ? Tr.t("colder", "mai rece") : Tr.t("similar", "similar");
		return new String[] { word, dDeg(dlt) + Tr.t(" after day 7", " după ziua 7"), Tr.t("rain ", "ploaie ") + mm(tr[3]) };
	}

	/** New box ID: its details for the bubble. */
	private List<String[]> xInfo(String id) {
		List<String[]> out = new java.util.ArrayList<String[]>();
		out.add(new String[] { "title", xLong(id) + " · " + place() });
		String[] v = xValues(id);
		out.add(new String[] { "v", Tr.t("Now: ", "Acum: ") + v[0] + (v[1].length() > 0 ? " · " + v[1] : "") + (v[2].length() > 0 ? " · " + v[2] : "") });
		long ms = now.getTimeInMillis();
		if (id.startsWith("u")) {
			if (us == null) return out;
			if (id.equals("uNws") && us.hasGrid()) {
				out.add(new String[] { "h", Tr.t("Next 24 hours, every 3 hours", "Următoarele 24 de ore, din 3 în 3") });
				List<String[]> tb = new java.util.ArrayList<String[]>();
				for (int k = 0; k < 8; k++) {
					long t = ms + k * 3 * 3600000L;
					Calendar c = (Calendar) now.clone();
					c.setTimeInMillis(t);
					String h = String.format(java.util.Locale.US, "%02d:00", c.get(Calendar.HOUR_OF_DAY));
					double pop = us.at("probabilityOfPrecipitation", t), wd = us.at("twentyFootWindSpeed", t), th = us.at("probabilityOfThunder", t);
					tb.add(new String[] { Texts.clock(h, clk12), Double.isNaN(pop) ? "–" : Math.round(pop) + "%", Double.isNaN(wd) ? "–" : kmh(wd), Double.isNaN(th) ? "–" : Math.round(th) + "%" });
				}
				out.add(table(new String[] { Tr.t("Hour", "Ora"), Tr.t("Rain", "Ploaie"), Tr.t("Wind 20 ft", "Vânt 6 m"), Tr.t("Thunder", "Oraje") }, tb));
				double q = us.sum("quantitativePrecipitation", ms - 3600000L, ms + 24 * 3600000L);
				out.add(new String[] { "v", Tr.t("Rain in 24 h: ", "Ploaie în 24 h: ") + (Double.isNaN(q) ? "–" : mm(q)) });
				out.add(new String[] { "s", Tr.t("The official forecast of the National Weather Service for a 2.5 km square (weather.gov).",
						"Prognoza oficială a National Weather Service pentru un pătrat de 2,5 km (weather.gov).") });
			} else if (id.equals("uDrought")) {
				out.add(new String[] { "t", Tr.t("D0 abnormally dry, D1 moderate, D2 severe, D3 extreme, D4 exceptional drought.",
						"D0 anormal de uscat, D1 secetă moderată, D2 severă, D3 extremă, D4 excepțională.") });
				out.add(new String[] { "t", Tr.t("The US Drought Monitor map comes out every Thursday", "Harta US Drought Monitor apare în fiecare joi")
						+ (us.droughtDate.length() > 0 ? " (" + us.droughtDate + ")." : ".") });
			} else if (id.equals("uFire") && us.hasGrid()) {
				double f = us.max("grasslandFireDangerIndex", ms, ms + 12 * 3600000L), rf = us.max("redFlagThreatIndex", ms, ms + 24 * 3600000L);
				double lal = us.max("lightningActivityLevel", ms, ms + 24 * 3600000L), th = us.max("probabilityOfThunder", ms, ms + 24 * 3600000L);
				out.add(new String[] { "v", Tr.t("Grass fire (12 h): ", "Incendiu pășuni (12 h): ") + fireWord(f) });
				out.add(new String[] { "v", Tr.t("Red flag index (24 h): ", "Indice „steag roșu” (24 h): ") + (Double.isNaN(rf) ? "–" : Long.toString(Math.round(rf))) });
				out.add(new String[] { "v", Tr.t("Lightning (1–6, 24 h): ", "Fulgere (1–6, 24 h): ") + (Double.isNaN(lal) ? "–" : Long.toString(Math.round(lal))) });
				out.add(new String[] { "v", Tr.t("Thunder chance (24 h): ", "Prob. oraje (24 h): ") + (Double.isNaN(th) ? "–" : Math.round(th) + "%") });
				out.add(new String[] { "t", Tr.t("A Red Flag Warning, when there is one, is among the warnings.", "Un Red Flag Warning, când există, apare între avertizări.") });
			} else if (id.equals("uHail") && us.reportsOk) {
				out.add(new String[] { "h", Tr.t("Reports today and yesterday, within 50 km", "Rapoarte azi și ieri, pe o rază de 50 km") });
				List<Tornadoes.Report> all = new java.util.ArrayList<Tornadoes.Report>(us.hail);
				all.addAll(us.wind);
				Tornadoes.sort(all);
				List<String[]> tb = new java.util.ArrayList<String[]>();
				for (Tornadoes.Report r : all) {
					boolean hail = us.hail.contains(r);
					String what = hail ? Tr.t("hail ", "grindină ") + (Double.isNaN(UsFarm.hailInches(r.scale)) ? r.scale
							: unitF ? String.format(java.util.Locale.US, "%.2f in", UsFarm.hailInches(r.scale)) : Math.round(UsFarm.hailInches(r.scale) * 25.4) + " mm")
							: Tr.t("wind ", "vânt ") + (r.scale.matches("\\d+") ? kmh(Integer.parseInt(r.scale) * 1.609344) : r.scale);
					Calendar c = (Calendar) now.clone();
					c.setTimeInMillis(r.time);
					String h = String.format(java.util.Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
					tb.add(new String[] { Texts.clock(h, clk12), what, dist(r.dist), r.place });
				}
				if (all.isEmpty()) out.add(new String[] { "t", Tr.t("No hail or damaging wind reported.", "Nicio grindină sau vânt puternic raportate.") });
				else out.add(table(new String[] { Tr.t("Hour", "Ora"), Tr.t("What", "Ce"), Tr.t("Dist.", "Dist."), Tr.t("Place", "Loc") }, tb));
				out.add(new String[] { "s", Tr.t("Source: NOAA Storm Prediction Center (spc.noaa.gov); the reports are preliminary.",
						"Sursa: NOAA Storm Prediction Center (spc.noaa.gov); rapoartele sunt preliminare.") });
			}
			return out;
		}
		int i = farm == null ? -1 : farm.nowIndex(now);
		if (i < 0) {
			out.add(new String[] { "t", farmMissing() });
			return out;
		}
		String today = Farm.date(now, 0);
		if (id.equals("fSpray")) {
			out.add(new String[] { "t", Tr.t("Good: wind 3–15 km/h, gusts under 25, no rain for 2 hours, under 28°, air humidity over 40%.",
					"Bun: vânt 3–15 km/h, rafale sub 25, fără ploaie 2 ore, sub 28°, umiditatea aerului peste 40%.") });
			out.add(new String[] { "h", Tr.t("Next 24 hours", "Următoarele 24 de ore") });
			// The hours grouped while the verdict stays the same (short, Viorel).
			int end = Math.min(farm.size(), i + 24);
			List<String[]> tb = new java.util.ArrayList<String[]>();
			for (int j = i; j < end;) {
				int st = Farm.spray(farm, j), k = j;
				while (k + 1 < end && Farm.spray(farm, k + 1) == st) k++;
				String span = Texts.clock(farm.hourOf(j), clk12) + (k > j ? " – " + Texts.clock(farm.hourOf(k), clk12) : "");
				tb.add(new String[] { span, (st == 0 ? "✓ " : "✗ ") + sprayWhy(st) });
				j = k + 1;
			}
			out.add(table(new String[] { Tr.t("Hours", "Orele"), Tr.t("Spraying", "Stropit") }, tb));
		} else if (id.equals("fWater")) {
			out.add(new String[] { "t", Tr.t("Rain minus the water the soil and the plants give off (evapotranspiration ET0, FAO): below zero the soil dries.",
					"Ploaia minus apa pe care solul și plantele o pierd (evapotranspirația ET0, FAO): sub zero solul se usucă.") });
			out.add(new String[] { "h", Tr.t("By day", "Pe zile") });
			int t = farm.dayIndex(today);
			List<String[]> tb = new java.util.ArrayList<String[]>();
			for (int k = Math.max(0, t - 6); t >= 0 && k <= Math.min(farm.dDate.length - 1, t + 3); k++) {
				double e = farm.dEt0 != null && k < farm.dEt0.length ? farm.dEt0[k] : Double.NaN;
				double bal = nz(farm.dRain[k]) - (Double.isNaN(e) ? 0 : e);
				tb.add(new String[] { farm.dDate[k].substring(5) + (k > t ? " *" : ""), mm(nz(farm.dRain[k])), Double.isNaN(e) ? "–" : mm(e), Double.isNaN(e) ? "–" : soilMm(bal) });
			}
			out.add(table(new String[] { Tr.t("Day", "Ziua"), Tr.t("Rain", "Ploaie"), "ET0", Tr.t("Balance", "Bilanț") }, tb));
			out.add(new String[] { "t", Tr.t("* forecast", "* prognoză") });
		} else if (id.equals("fStorm")) {
			out.add(new String[] { "t", Tr.t("CAPE is the energy a storm can draw from the air: under 300 J/kg low, 300–1000 moderate, 1000–2500 high, over that very high.",
					"CAPE e energia pe care o furtună o poate lua din aer: sub 300 J/kg scăzut, 300–1000 moderat, 1000–2500 ridicat, peste, foarte ridicat.") });
			out.add(new String[] { "h", Tr.t("Next 24 hours, every 3 hours", "Următoarele 24 de ore, din 3 în 3") });
			List<String[]> tb = new java.util.ArrayList<String[]>();
			for (int j = i; j < i + 24 && j < farm.size(); j += 3) {
				double cp = farm.cape == null ? Double.NaN : farm.cape[j];
				tb.add(new String[] { Texts.clock(farm.hourOf(j), clk12), Double.isNaN(cp) ? "–" : Long.toString(Math.round(cp)), stormWord(Farm.stormLevel(cp)),
						Double.isNaN(farm.prob[j]) ? "–" : Math.round(farm.prob[j]) + "%" });
			}
			out.add(table(new String[] { Tr.t("Hour", "Ora"), "CAPE J/kg", Tr.t("Risk", "Risc"), Tr.t("Rain", "Ploaie") }, tb));
		} else if (id.equals("fGdd")) {
			out.add(new String[] { "t", Tr.t("The warmth the crops gathered: each day the mean of the high and the low (kept between 10 and 30 °C) minus 10. Maize needs about 1300–1500 from sowing to ripening.",
					"Căldura adunată de culturi: în fiecare zi media dintre maximă și minimă (ținute între 10 și 30 °C) minus 10. Porumbul are nevoie de circa 1300–1500 de la semănat la coacere.") });
			int t = farm.dayIndex(today);
			out.add(new String[] { "h", Tr.t("By week", "Pe săptămâni") });
			List<String[]> tb = new java.util.ArrayList<String[]>();
			for (int wk = 0; wk < 4 && t >= 0; wk++) {
				double sum = 0;
				int a = t - wk * 7 - 6, b = t - wk * 7;
				for (int k = Math.max(0, a); k <= b; k++) {
					double g = Farm.gdd(farm, k);
					if (!Double.isNaN(g)) sum += g;
				}
				if (b < 0) break;
				tb.add(new String[] { farm.dDate[Math.max(0, a)].substring(5) + " – " + farm.dDate[b].substring(5), gddText(sum) });
			}
			out.add(table(new String[] { Tr.t("Week", "Săptămâna"), Tr.t("Warmth", "Căldură") }, tb));
		} else {
			out.add(new String[] { "t", Tr.t("The model's outlook: the second week is less certain than the first.", "Perspectiva modelului: a doua săptămână e mai nesigură decât prima.") });
			int t = farm.dayIndex(today);
			List<String[]> tb = new java.util.ArrayList<String[]>();
			for (int k = Math.max(0, t + 1); t >= 0 && k < farm.dDate.length; k++)
				tb.add(new String[] { T.weekdayUp(farm.dDate[k], true), deg(farm.dMax[k]), deg(farm.dMin[k]), mm(nz(farm.dRain[k])) });
			out.add(table(new String[] { Tr.t("Day", "Ziua"), "Max", "Min", Tr.t("Rain", "Ploaie") }, tb));
		}
		out.add(new String[] { "s", srcOM() });
		return out;
	}

	/** The parcel's boxes in the bottom row (Viorel 2026-11-07), in this order. */
	/** A parcel box id ("pTemp", "pWind", ...), not "pollen" or "press" (v2.61). */
	static boolean pbox(String id) {
		return id.length() > 1 && id.charAt(0) == 'p' && Character.isUpperCase(id.charAt(1));
	}

	/** A box of the bottom row with details on tap in farmer mode: the parcel boxes and the rain of 24 hours. */
	static boolean farmInfo(String id) {
		return pbox(id) || xbox(id) || id.equals("r24");
	}

	static final String[] PARCEL_BOXES = { "pTemp", "pWind", "pDir", "pProb", "pHum", "pSoilM", "pSoilT" };

	/**
	 * The name over the line, up to the clock: a parcel's name is not made smaller than 28 units;
	 * longer, it is cut with "…" (the whole name shows with the coordinates, at a tap; Viorel 2026-11-07).
	 */
	private String headName(float maxw) {
		String l = place();
		if (parcel == null || g.width(true, 28, l) <= maxw) return l;
		String cut = l;
		while (cut.length() > 1 && g.width(true, 28, cut.trim() + "…") > maxw) cut = cut.substring(0, cut.length() - 1);
		return cut.trim() + "…";
	}

	private void setNow(float x, float y, float wd, float h) {
		nowArea[0] = x; nowArea[1] = y; nowArea[2] = wd; nowArea[3] = Math.max(0, h);
	}

	/** The water in the soil's 3–9 cm layer now (moisture × 60 mm) and its change over 24 hours; NaN when unknown. */
	public double[] soilWater() {
		double[] r = { Double.NaN, Double.NaN };
		if (farm == null || farm.soilM == null) return r;
		int cur = farm.nowIndex(now);
		if (cur < 0 || Double.isNaN(farm.soilM[cur])) return r;
		r[0] = farm.soilM[cur] * 60;
		if (cur >= 24 && !Double.isNaN(farm.soilM[cur - 24])) r[1] = (farm.soilM[cur] - farm.soilM[cur - 24]) * 60;
		return r;
	}

	/** The rain (mm) of the last 24 hours at the place shown, NaN when unknown. */
	public double rain24() {
		if (farm == null) return Double.NaN;
		return Farm.last24(farm, farm.nowIndex(now));
	}

	/** The short title of parcel box ID (the long one heads its bubble). */
	private String boxTitle(String id) {
		if (id.equals("pTemp")) return Tr.t("AIR", "AER");
		if (id.equals("pWind")) return Tr.t("WIND", "VÂNT");
		if (id.equals("pDir")) return Tr.t("DIRECTION", "DIRECȚIE");
		if (id.equals("pProb")) return Tr.t("RAIN", "PLOAIE");
		if (id.equals("pHum")) return Tr.t("HUMIDITY", "UMIDITATE");
		if (id.equals("pSoilM")) return Tr.t("SOIL", "SOL");
		return Tr.t("SOIL TEMP.", "TEMP. SOL");
	}

	private String parcelTitle(String id) {
		if (id.equals("pTemp")) return Tr.t("Air temperature", "Temperatura aerului");
		if (id.equals("pWind")) return Tr.t("Wind", "Vânt");
		if (id.equals("pDir")) return Tr.t("Wind direction", "Direcția vântului");
		if (id.equals("pProb")) return Tr.t("Chance of rain", "Șansa de ploaie");
		if (id.equals("pHum")) return Tr.t("Air humidity", "Umiditatea aerului");
		if (id.equals("pSoilM")) return Tr.t("Soil moisture", "Umiditatea solului");
		return Tr.t("Soil temperature", "Temperatura solului");
	}

	private static double first(double a, double b) {
		return Double.isNaN(a) ? b : a;
	}

	/** { value, line under it, second line } of parcel box ID. */
	private String[] parcelValues(String id) {
		int i = farm == null ? -1 : farm.nowIndex(now);
		boolean h = i >= 0;
		int di = h ? farm.dayIndex(farm.dateOf(i)) : -1;
		if (id.equals("pTemp")) {
			String lh = di >= 0 ? Tr.t("low ", "min ") + deg(farm.dMin[di]) + " · " + Tr.t("high ", "max ") + deg(farm.dMax[di]) : "";
			return new String[] { deg(first(farm != null ? farm.cTemp : Double.NaN, w.temp)), lh, "" };
		}
		if (!h) return new String[] { "–", Tr.t("no data", "fără date"), "" };
		if (id.equals("pWind"))
			return new String[] { kmh(first(farm.cWind, farm.wind[i])), Tr.t("gusts ", "rafale ") + kmh(first(farm.cGust, farm.gust[i])), "" };
		if (id.equals("pDir")) {
			double dd = first(farm.cDir, farm.dir[i]);
			int sh = Farm.windShift(farm, i, 12);
			String turn = sh >= 0 ? Tr.t("turns to ", "se întoarce spre ") + dir8(farm.dir[sh]) : Tr.t("steady for 12 hours", "stabilă 12 ore");
			String at = sh >= 0 ? Tr.t(" around ", " în jurul orei ").trim() + " " + Texts.clock(farm.hourOf(sh), clk12) : "";
			return new String[] { dir8(dd), turn, at };
		}
		if (id.equals("pProb")) {
			String day = di >= 0 ? Tr.t("today up to ", "azi până la ") + (Double.isNaN(farm.dPop[di]) ? "–" : Math.round(farm.dPop[di]) + "%") : "";
			String mmDay = di >= 0 && !Double.isNaN(farm.dRain[di]) ? mm(farm.dRain[di]) : "";
			return new String[] { Double.isNaN(farm.prob[i]) ? "–" : Math.round(farm.prob[i]) + "%", day, mmDay };
		}
		if (id.equals("pHum")) {
			double hu = first(farm.cHum, farm.hum[i]);
			return new String[] { Double.isNaN(hu) ? "–" : Math.round(hu) + "%", "", "" };
		}
		if (id.equals("pSoilM")) {
			double m = farm.soilM[i];
			String[] words = { Tr.t("dry", "uscat"), Tr.t("moderate", "potrivit"), Tr.t("moist", "umed"), Tr.t("very wet", "foarte umed") };
			int lv = Farm.soilLevel(m);
			return new String[] { Double.isNaN(m) ? "–" : Math.round(m * 100) + "%", lv >= 0 ? words[lv] : "", Tr.t("3–9 cm, estimated", "3–9 cm, estimat") };
		}
		return new String[] { deg(farm.soilT[i]), Tr.t("surface, estimated", "suprafață, estimat"), "" };
	}

	private void drawParcelBox(String id, float cx, float top, float bw) {
		String[] v = xbox(id) ? xValues(id) : parcelValues(id);
		ctext(true, fit(true, 52, bw - 16, v[0]), top + 58, cx, v[0], c.ink);
		if (v[1].length() > 0) ctext(false, fit(false, 21, bw - 12, v[1]), top + 122, cx, v[1], c.ink);
		if (v[2].length() > 0) ctext(false, fit(false, 19, bw - 12, v[2]), top + 150, cx, v[2], c.dim);
	}

	/** Whether box K of the bottom row is the 24-hour precipitation one. */
	public boolean r24At(int k) {
		return k >= 0 && k < infoIds.size() && infoIds.get(k).equals("r24");
	}

	/** Parcel box K of the bottom row: its details for the bubble (as hourInfo). */
	public List<String[]> parcelInfo(int k) {
		List<String[]> out = new java.util.ArrayList<String[]>();
		if (w == null || k < 0 || k >= infoIds.size() || !farmInfo(infoIds.get(k))) return out;
		String id = infoIds.get(k);
		if (id.equals("r24")) return rain24Info();
		if (xbox(id)) return xInfo(id);
		out.add(new String[] { "title", parcelTitle(id) + " · " + place() });
		String[] v = parcelValues(id);
		out.add(new String[] { "v", Tr.t("Now: ", "Acum: ") + v[0] + (v[1].length() > 0 ? " · " + v[1] : "") + (v[2].length() > 0 ? " · " + v[2] : "") });
		int i = farm == null ? -1 : farm.nowIndex(now);
		if (i < 0) {
			out.add(new String[] { "t", farmMissing() });
			return out;
		}
		out.add(new String[] { "h", Tr.t("Next 12 hours", "Următoarele 12 ore") });
		// v3.20: a table with lines (Viorel), the values with their units.
		String[] head;
		if (id.equals("pWind")) head = new String[] { Tr.t("Hour", "Ora"), Tr.t("Wind", "Vânt"), Tr.t("Gusts", "Rafale") };
		else if (id.equals("pDir")) head = new String[] { Tr.t("Hour", "Ora"), Tr.t("From", "Dinspre"), "°" };
		else if (id.equals("pProb")) head = new String[] { Tr.t("Hour", "Ora"), Tr.t("Chance", "Prob."), Tr.t("Amount", "Cant.") };
		else if (id.equals("pSoilM")) head = new String[] { Tr.t("Hour", "Ora"), Tr.t("Moisture", "Umid."), Tr.t("Soil", "Sol") };
		else head = new String[] { Tr.t("Hour", "Ora"), parcelTitle(id) };
		List<String[]> tb = new java.util.ArrayList<String[]>();
		String[] lv = { Tr.t("dry", "uscat"), Tr.t("moderate", "potrivit"), Tr.t("moist", "umed"), Tr.t("very wet", "foarte umed") };
		for (int j = i + 1; j <= i + 12 && j < farm.size(); j++) {
			String hr = Texts.clock(farm.hourOf(j), clk12);
			if (id.equals("pTemp")) tb.add(new String[] { hr, deg(farm.temp[j]) });
			else if (id.equals("pWind")) tb.add(new String[] { hr, kmh(farm.wind[j]), Double.isNaN(farm.gust[j]) ? "–" : kmh(farm.gust[j]) });
			else if (id.equals("pDir")) tb.add(new String[] { hr, dir8(farm.dir[j]), Double.isNaN(farm.dir[j]) ? "–" : Math.round(farm.dir[j]) + "°" });
			else if (id.equals("pProb")) tb.add(new String[] { hr, Double.isNaN(farm.prob[j]) ? "–" : Math.round(farm.prob[j]) + "%", mm(nz(farm.precip[j])) });
			else if (id.equals("pHum")) tb.add(new String[] { hr, Double.isNaN(farm.hum[j]) ? "–" : Math.round(farm.hum[j]) + "%" });
			else if (id.equals("pSoilM")) tb.add(new String[] { hr, Double.isNaN(farm.soilM[j]) ? "–" : Math.round(farm.soilM[j] * 100) + "%", Double.isNaN(farm.soilM[j]) ? "–" : lv[Farm.soilLevel(farm.soilM[j])] });
			else tb.add(new String[] { hr, deg(farm.soilT[j]) });
		}
		out.add(table(head, tb));
		if (id.startsWith("pSoil")) out.add(new String[] { "t", soilEst() });
		out.add(new String[] { "s", srcOM() });
		return out;
	}

	/** The precipitation of the last 24 hours: what it is, the total and the hours it fell, for the bubble. */
	public List<String[]> rain24Info() {
		List<String[]> out = new java.util.ArrayList<String[]>();
		out.add(new String[] { "title", Tr.t("Water in the soil", "Apa în sol") + " · " + place() });
		double[] sw = soilWater();
		out.add(new String[] { "v", Tr.t("In the soil now (3–9 cm): ", "În sol acum (3–9 cm): ")
				+ (Double.isNaN(sw[0]) ? "–" : mm(sw[0]) + " · " + Tr.t("moisture ", "umiditate ") + Math.round(sw[0] / 60 * 100) + "%") });
		out.add(new String[] { "h", Tr.t("Precipitation of the last 24 hours", "Precipitațiile din ultimele 24 de ore") });
		out.add(new String[] { "t", Tr.t("The water fallen on the ground in the last 24 hours (rain, sleet, melted snow) at the place shown, hour by hour.",
				"Apa căzută pe sol în ultimele 24 de ore (ploaie, lapoviță, zăpadă topită) la locul afișat, oră cu oră.") });
		double r = rain24();
		if (Double.isNaN(r)) {
			out.add(new String[] { "t", Tr.t("The data of the last 24 hours are being downloaded; tap again in a moment. If Open-Meteo is not answering, they come later by themselves.",
					"Datele ultimelor 24 de ore se descarcă; atinge din nou peste puțin timp. Dacă Open-Meteo nu răspunde, vin mai târziu singure.") });
			return out;
		}
		out.add(new String[] { "h", Tr.t("Total: ", "Total: ") + mm(r) });
		int cur = farm.nowIndex(now), n = 0;
		// The water kept by the soil (v2.62, Viorel): the change of the 3–9 cm layer's moisture, 60 mm thick.
		double m0 = farm.soilM == null ? Double.NaN : farm.soilM[cur - 24 >= 0 ? cur - 24 : cur - 23], m1 = farm.soilM == null ? Double.NaN : farm.soilM[cur];
		boolean soil = !Double.isNaN(m0) && !Double.isNaN(m1);
		if (soil) {
			String kept = soilMm((m1 - m0) * 60);
			out.add(new String[] { "v", Tr.t("Kept by soil (3–9 cm): ", "Reținută de sol (3–9 cm): ") + kept + " · "
					+ Tr.t("moisture ", "umiditate ") + Math.round(m0 * 100) + "% → " + Math.round(m1 * 100) + "%" });
		}
		for (int k = cur - 23; k <= cur; k++) if (nz(farm.precip[k]) >= 0.05) n++;
		if (n == 0) out.add(new String[] { "t", Tr.t("No precipitation in the last 24 hours.", "Fără precipitații în ultimele 24 de ore.") });
		else {
			out.add(new String[] { "h", Tr.t("Hours with precipitation", "Orele cu precipitații") });
			List<String[]> tb = new java.util.ArrayList<String[]>();
			for (int k = cur - 23; k <= cur; k++) {
				if (nz(farm.precip[k]) < 0.05) continue;
				int from = k - 1;
				String a = from >= 0 ? Texts.clock(farm.hourOf(from), clk12) : "", b = Texts.clock(farm.hourOf(k), clk12);
				String sk = "–";
				if (soil && k >= 1 && !Double.isNaN(farm.soilM[k]) && !Double.isNaN(farm.soilM[k - 1]))
					sk = soilMm((farm.soilM[k] - farm.soilM[k - 1]) * 60);
				tb.add(soil ? new String[] { a + "–" + b, mm(farm.precip[k]), sk } : new String[] { a + "–" + b, mm(farm.precip[k]) });
			}
			out.add(table(soil ? new String[] { Tr.t("Hours", "Orele"), Tr.t("Water", "Apă"), Tr.t("Soil", "Sol") } : new String[] { Tr.t("Hours", "Orele"), Tr.t("Water", "Apă") }, tb));
		}
		if (soil) out.add(new String[] { "t", Tr.t("“Kept by the soil”: how much the water in the soil's 3–9 cm layer grew (or fell, with −); the rest ran off, evaporated or went deeper.",
				"„Reținută de sol”: cât a crescut (sau a scăzut, cu −) apa din stratul de sol de 3–9 cm; restul s-a scurs, s-a evaporat sau a ajuns mai adânc.") });
		out.add(new String[] { "t", Tr.t("An estimate from the weather models, not a rain-gauge measurement at the place.",
				"Estimare din modelele meteo, nu o măsurătoare de pluviometru în acel loc.") });
		out.add(new String[] { "s", srcOM() });
		return out;
	}

	/** Water in mm with its sign, for the soil's change (inches in the US). */
	private String soilMm(double v) {
		if (Math.abs(v) < 0.05) return "0 " + (unitF ? "in" : "mm");
		return (v > 0 ? "+" : "−") + mm(Math.abs(v));
	}

	// --- Helpers ---------------------------------------------------------------------

	private String t(double c) {
		long v = Math.round(unitF ? c * 9 / 5 + 32 : c);
		return Long.toString(v);
	}

	private static String upper(String s) {
		return s.toUpperCase();
	}

	private void button(float[] b) {
		g.roundRect(b[0], b[1], b[2], b[3], 10, c.line);
		g.roundRect(b[0] + 2, b[1] + 2, b[2] - 4, b[3] - 4, 8, c.btn);
	}

	private void ltext(boolean bold, float px, float top, float left, String s, int color) {
		g.text(bold, px, top, left, s, color);
	}

	/** Right edge at RIGHT; returns the left edge. */
	private float rtext(boolean bold, float px, float top, float right, String s, int color) {
		float x = right - g.width(bold, px, s);
		g.text(bold, px, top, x, s, color);
		return x;
	}

	private void ctext(boolean bold, float px, float top, float cx, String s, int color) {
		g.text(bold, px, top, cx - g.width(bold, px, s) / 2, s, color);
	}

	/** The largest size up to PX at which S fits in MAXW. */
	private float fit(boolean bold, float px, float maxw, String s) {
		while (px > 14 && g.width(bold, px, s) > maxw) px = px * 92 / 100;
		return px;
	}

	/** Box title in the header band (white on black; dark on ivory in the dark theme): capitals at about 3/4 of the band, drawn twice 1 unit apart. */
	private void head(float top, float h, float cx, float maxw, String s, float px) {
		float p = fit(true, px > 0 ? px : (h - 4) * 76 / 100, maxw - 1, s);
		float x = cx - g.width(true, p, s) / 2, y = top + h / 2 - p * 485 / 1000;
		g.text(true, p, y, x, s, c.bandInk);
		g.text(true, p, y, x + 1, s, c.bandInk);
	}

	/** Frame with rounded corners, a 2-unit outline and a header band HD high. */
	private void box(float x, float y, float w, float h, float hd) {
		float r = 14;
		g.roundRect(x, y, w, h, r, c.line);
		g.roundRect(x + 2, y + 2, w - 4, h - 4, r - 2, c.band);
		g.rect(x + 2, y + hd, w - 4, h - hd - r - 2, c.card);
		g.roundRect(x + 2, y + h - 2 * r - 2, w - 4, 2 * r, r - 2, c.card);
		g.rect(x + 2, y + hd - 2, w - 4, 2, c.line);
	}

	private void drawClock() {
		int h = now.get(Calendar.HOUR_OF_DAY), m = now.get(Calendar.MINUTE);
		float right = cw - 40;
		if (clk12) {
			String pm = h < 12 ? "AM" : "PM";
			int h12 = h % 12 == 0 ? 12 : h % 12;
			float pw = g.width(true, 22, pm);
			rtext(true, 50, 17, right - pw - 4, h12 + String.format(":%02d", m), c.ink);
			ltext(true, 22, 17 + 50 * 81 / 100 - 18, right - pw, pm, c.ink);
		} else {
			rtext(true, 50, 17, right, String.format("%02d:%02d", h, m), c.ink);
		}
		String hm = clk12 ? (h % 12 == 0 ? 12 : h % 12) + String.format(":%02d", m) : String.format("%02d:%02d", h, m);
		clockLeft = right - g.width(true, 50, hm) - (clk12 ? g.width(true, 22, "PM") + 4 : 0);
		if (net >= 0) {
			drawNet(clockLeft - 14 - 36, 25, 36, 32);
			clockLeft -= 14 + 36;
		}
	}

	/**
	 * v3.00 (Viorel): the connection in a box X Y W H: Wi-Fi (3 arcs and a dot), mobile data (4
	 * bars), another network (a globe), none (the Wi-Fi drawing dim and struck through).
	 */
	private void drawNet(float x, float y, float wd, float h) {
		int col = net == 0 ? c.dim : c.ink;
		if (net == 1 || net == 0) {
			float cx = x + wd / 2, cy = y + h - 3;
			for (int k = 1; k <= 3; k++) g.arc(cx, cy, k * 9.5f, 225, 90, 4, col);
			g.roundRect(cx - 3.5f, cy - 3.5f, 7, 7, 3.5f, col);
			if (net == 0) g.poly(new float[] { x + 2, y + h - 1, x + 5, y + h + 2, x + wd - 2, y + 1, x + wd - 5, y - 2 }, c.ink);
		} else if (net == 2) {
			float bw = 6, gap = 3.3f, x0 = x + (wd - 4 * bw - 3 * gap) / 2;
			for (int k = 0; k < 4; k++) {
				float bh = h * (k + 1) / 4f;
				g.rect(x0 + k * (bw + gap), y + h - bh, bw, bh, col);
			}
		} else {
			float cx = x + wd / 2, cy = y + h / 2, r = Math.min(wd, h) / 2 - 2;
			g.arc(cx, cy, r, 0, 360, 3, col);
			g.rect(cx - r, cy - 1.5f, 2 * r, 3, col);
			g.rect(cx - 1.5f, cy - r, 3, 2 * r, col);
		}
	}

	// --- Current weather ---------------------------------------------------------------

	private float icLeft = 0, icRight = 758, icTop = 0, icPct = 100;

	private void drawIcon(String icon, float x, float y, float s) {
		g.icon("icons/" + icon + ".png", x, y, s, s, c.ink);
	}

	/**
	 * The icon with the middle of its drawing at CX CY, as large as fits: at most SIZE, the
	 * drawing at most HALF_H above and below CY and MAXW wide.
	 */
	private void drawIconC(String icon, float cx, float cy, float size, float halfH, float maxw) {
		float x0 = 0, y0 = 0, x1 = 200, y1 = 200;
		for (String c : CENTRE) {
			String[] p = c.split(" ");
			if (p[0].equals(icon)) {
				x0 = Float.parseFloat(p[1]); y0 = Float.parseFloat(p[2]); x1 = Float.parseFloat(p[3]); y1 = Float.parseFloat(p[4]);
			}
		}
		float s = size, dh = y1 - y0, dw = x1 - x0;
		if (s * dh / 200 > halfH * 2) s = halfH * 2 * 200 / dh;
		if (s * dw / 200 > maxw) s = maxw * 200 / dw;
		s = s * icPct / 100;
		float x = cx - s * (x0 + x1) / 400;
		// Keep the drawing itself (not its file's square) between IC_LEFT and IC_RIGHT.
		if (x + s * x0 / 200 < icLeft) x = icLeft - s * x0 / 200;
		if (x + s * x1 / 200 > icRight) x = icRight - s * x1 / 200;
		float y = cy - s * (y0 + y1) / 400;
		if (y < icTop) y = icTop;
		drawIcon(icon, x, y, s);
	}

	// Measured temperature: size, minus, digits, unit.
	private float tp, tMw, tWn, tGap, tWu, tU, tW, tD;
	/** Size of the unit letter and of the "°" in hundredths of the digits' size (smaller when stacked). */
	private int uPct = 43, dPct = 43;
	private boolean tNeg;
	private String tN;

	private void tempSize(float px, float maxw) {
		tN = t(w.temp);
		tNeg = tN.startsWith("-");
		if (tNeg) tN = tN.substring(1);
		tp = px;
		while (true) {
			tU = tp * uPct / 100;
			tD = tp * dPct / 100;
			tWn = g.width(true, tp, tN);
			tWu = g.width(true, tU, unitF ? "F" : "C");
			tGap = tp / 25;
			tMw = tNeg ? tp * 64 * 42 / 10000 : 0;
			tW = tMw + tWn + tGap + tWu;
			if (tW <= maxw || tp < 60) break;
			tp = tp * 94 / 100;
		}
	}

	/** Minus bar, digits, a small "°" level with their top and the unit on their baseline. */
	private void drawTempAt(float left, float base) {
		float dh = tp * 64 / 100, x = left;
		if (tNeg) {
			float mt = Math.max(4, dh * 75 / 1000);
			g.rect(x, base - dh / 2 - mt / 2, dh * 30 / 100, mt, c.ink);
			x += tMw;
		}
		g.text(true, tp, base - tp * 81 / 100, x, tN, c.ink);
		x += tWn + tGap;
		g.text(true, tD, base - dh - tD * 17 / 100, x, "°", c.ink);
		g.text(true, tU, base - tU * 81 / 100, x, unitF ? "F" : "C", c.ink);
	}

	/**
	 * Portrait: the icon on top and the temperature under it, both centred across, the two
	 * together centred between TOP and BOT; smaller when they do not fit.
	 */
	private void drawNowStack(String icon, float l, float top, float r, float bot) {
		float y0 = 0, y1 = 200;
		for (String c : CENTRE) {
			String[] p = c.split(" ");
			if (p[0].equals(icon)) { y0 = Float.parseFloat(p[2]); y1 = Float.parseFloat(p[4]); }
		}
		uPct = 34;
		dPct = 27;
		float s = 400, px = 348, room = bot - top - 40, cx = (l + r) / 2;
		while (true) {
			tempSize(px, r - l - 40);
			float tot = s * (y1 - y0) / 200 + tp * 15 / 100 + tp * 64 / 100;
			if (tot <= room || s < 120) break;
			s = s * 94 / 100;
			px = px * 94 / 100;
		}
		float ih = s * (y1 - y0) / 200, gap = tp * 15 / 100, dh = tp * 64 / 100, tot = ih + gap + dh;
		float bt = top + 20 + (room - tot) / 2;
		// The drawing itself starts at BT; no clamp on the file's top (its drawing may sit low in it).
		icLeft = l; icRight = r; icPct = 100; icTop = -100000;
		drawIconC(icon, cx, bt + ih / 2, s, ih / 2 + 1, r - l);
		icTop = 0;
		drawTempAt(cx - tW / 2, bt + ih + gap + dh);
		uPct = 43;
		dPct = 43;
	}

	/** No warnings: the temperature right-aligned on the middle, the icon centred in the right half. */
	private void drawNowSide(String icon, float l, float top, float r, float bot) {
		float mid = (l + r) / 2, cy = (top + bot) / 2 + 20, hw = (r - l) / 2;
		icLeft = mid + 2; icRight = r; icPct = 100; icTop = top + 60;
		drawIconC(icon, mid + hw / 2, cy, 320, (bot - top) / 2 - 40, hw - 30);
		icTop = 0;
		tempSize(252, hw - 12);
		float dh = tp * 64 / 100;
		drawTempAt(mid + 2 - tW, cy + dh / 2);
	}

	// --- Warnings ------------------------------------------------------------------------

	/** The warnings of one code (level) merged: the types and the whole period. */
	private static final class WarnGroup {
		int level;
		/** US (National Weather Service) warnings: Advisory / Watch / Warning instead of the codes. */
		boolean nws;
		/** The app's own information box (v3.10, Europe): TYPES is its key, VAL the lowest temperature or the strongest gust. */
		boolean own;
		double val = Double.NaN;
		String types = "";
		long from = Long.MAX_VALUE, until;
	}

	/** The active warnings, one group per code, red first. */
	private List<WarnGroup> warnGroups() {
		List<WarnGroup> gs = new java.util.ArrayList<WarnGroup>();
		if (warns == null) return gs;
		long nowMs = now.getTimeInMillis();
		for (int lv = 3; lv >= 1; lv--) {
			WarnGroup gr = new WarnGroup();
			gr.level = lv;
			for (Warnings.Warn wr : warns) {
				if (wr.until <= nowMs || Math.min(3, Math.max(1, wr.level)) != lv) continue;
				String ty = wr.nws ? Nws.phenomenonShown(wr.type) : wr.type;
				if (wr.nws) gr.nws = true;
				if (!(", " + gr.types + ",").contains(", " + ty + ",")) gr.types += (gr.types.length() > 0 ? ", " : "") + ty;
				gr.from = Math.min(gr.from, wr.from);
				gr.until = Math.max(gr.until, wr.until);
			}
			if (gr.types.length() > 0) gs.add(gr);
		}
		gs.addAll(ownGroups());
		return gs;
	}

	/**
	 * v3.10 (Viorel): in Europe, information boxes like the NWS advisories in the US, worked out by
	 * the app from the next 25 hours of the forecast: frost, freeze (spring and autumn only), fog,
	 * freezing rain, strong wind. Left out when an official warning already covers it.
	 */
	private List<WarnGroup> ownGroups() {
		List<WarnGroup> out = new java.util.ArrayList<WarnGroup>();
		if (w == null || city == null || city.us() || w.tileTemp.length == 0) return out;
		String off = "";
		long nowMs = now.getTimeInMillis();
		if (warns != null) for (Warnings.Warn wr : warns) if (!wr.nws && wr.until > nowMs) off += wr.type + ",";
		int mon = now.get(Calendar.MONTH) + 1;
		boolean grow = (mon >= 3 && mon <= 5) || (mon >= 9 && mon <= 11);
		int n = Math.min(w.tileTemp.length, 25);
		double[] code = new double[n], day = new double[n];
		for (int k = 0; k < n; k++) {
			code[k] = k < w.tileCode.length ? w.tileCode[k] : 0;
			day[k] = k < w.tileDay.length && !w.tileDay[k] ? 0 : 1;
		}
		List<OwnHit> hits = ownHits(w.tileTemp, w.tileCloud, w.tileWind, w.tileGust, code, day, 0, n, mon, off);
		Calendar h0 = (Calendar) now.clone();
		h0.set(Calendar.MINUTE, 0); h0.set(Calendar.SECOND, 0); h0.set(Calendar.MILLISECOND, 0);
		long t0 = h0.getTimeInMillis();
		for (OwnHit h : hits) {
			WarnGroup gr = new WarnGroup();
			gr.own = true;
			gr.level = 1;
			gr.types = h.key;
			gr.from = Math.max(nowMs, t0 + h.a * 3600000L);
			gr.until = t0 + (h.b + 1) * 3600000L;
			gr.val = h.val;
			out.add(gr);
		}
		return out;
	}

	/** One own information found in the forecast: its kind, first and last hour (indexes), value. */
	public static final class OwnHit {
		public String key;
		public int a, b;
		public double val;
	}

	/**
	 * The own information of the hours FROM .. FROM+N-1 (v3.10 rules; v3.20 shared with the parcels'
	 * notices): MONTH 1–12, OFF = the types of the official warnings in force (they win).
	 */
	public static List<OwnHit> ownHits(double[] temp, double[] cloud, double[] wind, double[] gust, double[] code, double[] day, int from, int n, int month, String off) {
		boolean grow = (month >= 3 && month <= 5) || (month >= 9 && month <= 11);
		String[] keys = { "freeze", "frost", "fog", "ice", "wind" };
		int[] a = new int[5], b = new int[5], cnt = new int[5];
		double[] val = { 99, 99, 0, 0, 0 };
		java.util.Arrays.fill(a, -1);
		boolean rime = false;
		for (int k = from; k < from + n && k < temp.length; k++) {
			double t = temp[k], cl = at(cloud, k), wi = at(wind, k), gu = at(gust, k), cd = at(code, k);
			if (Double.isNaN(t)) continue;
			int c = Double.isNaN(cd) ? 0 : (int) Math.round(cd);
			boolean night = !Double.isNaN(at(day, k)) && at(day, k) < 0.5;
			boolean[] hit = new boolean[5];
			hit[0] = grow && t <= -2;
			hit[1] = grow && night && t <= (Double.isNaN(cl) ? 2 : 3) && t > -2 && (Double.isNaN(cl) || cl < 40) && (Double.isNaN(wi) || wi < 12);
			hit[2] = c == 45 || c == 48;
			hit[3] = c == 56 || c == 57 || c == 66 || c == 67;
			hit[4] = !Double.isNaN(gu) && gu >= 65;
			if (c == 48) rime = true;
			for (int i = 0; i < 5; i++) {
				if (!hit[i]) continue;
				if (a[i] < 0) a[i] = k;
				b[i] = k;
				cnt[i]++;
				if (i < 2) val[i] = Math.min(val[i], t);
				if (i == 4) val[i] = Math.max(val[i], gu);
			}
		}
		if (cnt[0] > 0) cnt[1] = 0;
		if (cnt[2] < 2 || off.contains("fog")) cnt[2] = 0;
		if (off.contains("snow")) cnt[3] = 0;
		if (off.contains("wind")) cnt[4] = 0;
		if (off.contains("low-temperature")) cnt[0] = 0;
		List<OwnHit> out = new java.util.ArrayList<OwnHit>();
		for (int i = 0; i < 5; i++) {
			if (cnt[i] == 0) continue;
			OwnHit h = new OwnHit();
			h.key = i == 2 && rime ? "rime" : keys[i];
			h.a = a[i] - from;
			h.b = b[i] - from;
			h.val = val[i];
			out.add(h);
		}
		return out;
	}

	private static double at(double[] v, int k) {
		return k < v.length ? v[k] : Double.NaN;
	}

	/** The name of an own information box. */
	public static String ownName(String key) {
		if (key.equals("freeze")) return Tr.t("FREEZE", "ÎNGHEȚ");
		if (key.equals("frost")) return Tr.t("FROST", "BRUMĂ");
		if (key.equals("rime")) return Tr.t("FREEZING FOG", "CEAȚĂ CU CHICIURĂ");
		if (key.equals("fog")) return Tr.t("FOG", "CEAȚĂ");
		if (key.equals("ice")) return Tr.t("FREEZING RAIN", "POLEI");
		return Tr.t("STRONG WIND", "VÂNT TARE");
	}

	private List<String[]> ownInfo(WarnGroup gr, List<String[]> out) {
		String k = gr.types, name = ownName(k);
		out.add(new String[] { "title", Tr.t("Information", "Informare") + " · " + name.substring(0, 1) + name.substring(1).toLowerCase() });
		String what;
		if (k.equals("freeze")) what = Tr.t("Temperatures of −2° and lower: crops, blossoms and vegetables left uncovered can freeze.", "Temperaturi de −2° și mai jos: culturile, florile și legumele neprotejate pot îngheța.");
		else if (k.equals("frost")) what = Tr.t("A clear, calm night with 3° or less: frost can form and burn tender plants.", "Noapte senină și fără vânt, cu 3° sau mai puțin: se poate forma brumă, care arde plantele sensibile.");
		else if (k.equals("rime")) what = Tr.t("Fog that leaves rime on trees, wires and roads: low visibility and slippery spots.", "Ceață care depune chiciură pe copaci, fire și drumuri: vizibilitate redusă și porțiuni alunecoase.");
		else if (k.equals("fog")) what = Tr.t("Fog for several hours: visibility can drop a lot. Drive carefully.", "Ceață câteva ore: vizibilitatea poate scădea mult. Conduceți cu atenție.");
		else if (k.equals("ice")) what = Tr.t("Rain or drizzle that freezes on the ground: slippery roads and pavements, ice on plants and wires.", "Ploaie sau burniță care îngheață la sol: drumuri și trotuare alunecoase, gheață pe plante și fire.");
		else what = Tr.t("Strong gusts: secure loose objects, watch for falling branches; not a time for spraying.", "Rafale puternice: fixați obiectele, atenție la crengi; nu e momentul pentru stropit.");
		out.add(new String[] { "t", what });
		out.add(new String[] { "v", Tr.t("Interval: ", "Interval: ") + T.weekdayTime(gr.from, clk12) + " – " + T.weekdayTime(gr.until, clk12) });
		if (k.equals("freeze") || k.equals("frost")) {
			out.add(new String[] { "v", Tr.t("Lowest: ", "Minima: ") + t(gr.val) + "°" });
			out.add(new String[] { "t", Tr.t("Shown only in spring (March–May) and autumn (September–November).", "Apare doar primăvara (martie–mai) și toamna (septembrie–noiembrie).") });
		}
		if (k.equals("wind")) out.add(new String[] { "v", Tr.t("Strongest gust: ", "Rafala cea mai puternică: ") + Math.round(gr.val) + " km/h" });
		out.add(new String[] { "s", Tr.t("Worked out by the app from the Open-Meteo forecast, like the NWS advisories in the US; not an official warning.", "Calculată de aplicație din prognoza Open-Meteo, ca informările NWS din SUA; nu e o avertizare oficială.") });
		return out;
	}

	/** The earthquake shown first in the row, or null (the strongest of the last 24 h in the country). */
	private Quakes.Quake quake() {
		Quakes.Quake q = Quakes.main(quakes);
		return q != null && now.getTimeInMillis() - q.time <= Quakes.WINDOW_MS ? q : null;
	}

	/** How many boxes the row of warnings has (the earthquake counts). */
	public int alertCount() {
		return warnGroups().size() + (quake() != null ? 1 : 0);
	}

	/** The box of the row at X Y (canvas units), -1 when none. */
	public int alertAt(float x, float y) {
		if (alerts.area[2] <= 0 || y < alerts.area[1] || y > alerts.area[1] + alerts.area[3]) return -1;
		if (x < alerts.area[0] || x > alerts.area[0] + alerts.area[2]) return -1;
		float rel = x - alerts.area[0] + alerts.scroll;
		int k = (int) Math.floor(rel / alertStep);
		return k >= 0 && k < alertCount() ? k : -1;
	}

	/** Where box K of the row is on the canvas (x, y, w, h). */
	public float[] alertRect(int k) {
		return new float[] { alerts.area[0] + k * alertStep - alerts.scroll, alerts.area[1], alertStep - 16, alerts.area[3] };
	}

	private float alertStep = 1;

	// Title bars: light pastels with black text, or dark shades with white text (yellow, orange, red).
	private static final int[] BAR_LIGHT = { 0xFFEDF9A5, 0xFFFFC285, 0xFFFF8585 }, BAR_DARK = { 0xFF8C7A12, 0xFFA8561E, 0xFF9E2B2B };
	private static final int QUAKE_LIGHT = 0xFFD9D9D9, QUAKE_DARK = 0xFF5A5954;

	/**
	 * The warnings as ONE row of boxes across WD (Viorel 2026-11-02): the earthquake first (when
	 * there is one), then one box per warning code, red first. COLS fit (a phone: two, a tablet:
	 * three); with more, the row moves on by itself every 2 s (WeatherView) and follows the finger.
	 * F times larger. Returns the bottom of what was drawn, 0 without boxes.
	 */
	private float drawWarnBoxes(float x0, float y, float wd, int cols, float f) {
		List<WarnGroup> gs = warnGroups();
		Quakes.Quake q = quake();
		int n = gs.size() + (q != null ? 1 : 0), vis = Math.min(cols, n);
		warnBoxes = n;
		alerts.area[2] = 0;
		if (n == 0) return 0;
		int across = cols > 2 ? cols : vis;
		float gap = 16, bw = (wd - gap * (across - 1)) / across, h = 130 * f, step = bw + gap;
		alertStep = step;
		float[] starts = new float[n];
		for (int k = 0; k < n; k++) starts[k] = k * step;
		alerts.set(starts, n * step - gap - wd);
		alerts.area[0] = x0; alerts.area[1] = y; alerts.area[2] = wd; alerts.area[3] = h;
		warnArea[0] = x0; warnArea[1] = y; warnArea[2] = wd; warnArea[3] = h;
		g.clip(x0 - 2, y - 2, wd + 4, h + 4);
		for (int i = 0; i < n; i++) {
			float x = x0 + i * step - alerts.scroll;
			if (x + bw < x0 - 4 || x > x0 + wd + 4) continue;
			if (q != null && i == 0) drawQuakeBox(q, x, y, bw, h, f);
			else drawWarnBox(gs.get(i - (q != null ? 1 : 0)), x, y, bw, h, f);
		}
		g.unclip();
		return y + h;
	}

	/** The frame of a box with its title bar in BAR and TITLE in it; returns the bar's height. */
	private float alertFrame(float x, float y, float bw, float h, float f, int bar, String title) {
		float hb = 44 * f, r = 14;
		int border = c.ink, body = dark ? 0xFF000000 : c.page;
		g.roundRect(x, y, bw, h, r, border);
		g.roundRect(x + 2, y + 2, bw - 4, h - 4, r - 2, body);
		g.roundRect(x + 2, y + 2, bw - 4, hb, r - 2, bar);
		g.rect(x + 2, y + r, bw - 4, hb - r + 2, bar);
		g.rect(x + 2, y + hb, bw - 4, 2, border);
		float hp = fit(true, 30 * f, bw - 24, title), ty = y + 2 + (hb - 2) / 2 - hp * 485 / 1000;
		int tc = dark ? 0xFFFFFFFF : 0xFF000000;
		float tx = x + bw / 2 - g.width(true, hp, title) / 2;
		g.text(true, hp, ty, tx, title, tc);
		g.text(true, hp, ty, tx + 1, title, tc);
		return hb;
	}

	private void drawWarnBox(WarnGroup gr, float x, float y, float bw, float h, float f) {
		if (gr.own) {
			float hb = alertFrame(x, y, bw, h, f, dark ? 0xFF2F5F7E : 0xFFBFDDF2, Tr.t("INFORMATION", "INFORMARE"));
			float room = bw - 30;
			String txt = ownName(gr.types), per = T.weekdayTime(gr.from, clk12) + " – " + T.weekdayTime(gr.until, clk12);
			ltext(true, fit(true, 30 * f, room, txt), y + hb + 12 * f, x + 18, txt, c.ink);
			ltext(false, fit(false, 26 * f, room, per), y + hb + 50 * f, x + 18, per, c.dim);
			return;
		}
		String name = T.warn[gr.level - 1];
		String title = gr.nws ? Nws.levelUp(gr.level) : T.en ? name + " " + T.warnCode : T.warnCode + " " + name;
		float hb = alertFrame(x, y, bw, h, f, (dark ? BAR_DARK : BAR_LIGHT)[gr.level - 1], title);
		float lx = x + 18;
		if (gr.level >= 3) {
			g.icon("ui/atentie.png", x + 14, y + hb + (h - hb - 46 * f) / 2, 50 * f, 46 * f, dark ? c.warnR : c.ink);
			lx = x + 28 + 50 * f;
		}
		float room = x + bw - 12 - lx;
		String txt = gr.nws ? gr.types.toUpperCase() : T.warnType(gr.types);
		String per = T.weekdayTime(gr.from, clk12) + " – " + T.weekdayTime(gr.until, clk12);
		ltext(true, fit(true, 30 * f, room, txt), y + hb + 12 * f, lx, txt, c.ink);
		ltext(false, fit(false, 26 * f, room, per), y + hb + 50 * f, lx, per, c.dim);
	}

	/** "4,2" (Romanian) or "4.2". */
	private String dec(double v) {
		String s = String.format(java.util.Locale.US, "%.1f", v);
		return s.replace('.', Tr.dot());
	}

	private void drawQuakeBox(Quakes.Quake q, float x, float y, float bw, float h, float f) {
		float hb = alertFrame(x, y, bw, h, f, dark ? QUAKE_DARK : QUAKE_LIGHT, Tr.t("EARTHQUAKE", "CUTREMUR"));
		float lx = x + 18, room = bw - 30;
		String l1 = "M " + dec(q.mag) + " · " + T.weekdayTime(q.time, clk12);
		String l2 = q.felt >= 2 ? (Tr.t("Felt here: ", "Resimțit aici: ")) + roman(q.felt) + (Tr.t(" (estimate)", " (estimat)"))
				: (Tr.t("depth ", "adânc. ")) + dist(q.depth) + " · " + dist(q.dist);
		ltext(true, fit(true, 30 * f, room, l1), y + hb + 12 * f, lx, l1, c.ink);
		ltext(false, fit(false, 26 * f, room, l2), y + hb + 50 * f, lx, l2, c.dim);
	}

	/** A distance of KM kilometres: "12 km", or "7 mi" in imperial units (v2.30). */
	public String dist(double km) {
		return unitF ? Math.round(km * 0.621371) + " mi" : Math.round(km) + " km";
	}

	private static final String[] ROMAN = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII" };

	public static String roman(double i) {
		int k = (int) Math.round(Math.max(1, Math.min(12, i)));
		return ROMAN[k - 1];
	}

	/** EMS-98 in a few words, for intensity I. */
	public String feltWords(double i) {
		int k = (int) Math.round(Math.max(1, Math.min(12, i)));
		String[] ro = { "nu se simte", "abia simțit, doar de câțiva oameni aflați în repaus", "slab, simțit de puțini oameni, în interior",
				"simțit de mulți oameni în interior; geamurile și ușile vibrează", "puternic, simțit de aproape toți; obiectele se mișcă, unii se sperie",
				"ușoare avarii (fisuri în tencuială); mulți ies afară", "avarii la clădiri; greu de stat în picioare", "avarii grave",
				"distrugeri", "distrugeri mari", "distrugeri foarte mari", "distrugere totală" };
		String[] en = { "not felt", "scarcely felt, only by a few people at rest", "weak, felt by a few people indoors",
				"felt by many indoors; windows and doors rattle", "strong, felt by nearly everyone; objects move, some are frightened",
				"slight damage (cracks in plaster); many run outdoors", "damage to buildings; hard to stand", "heavy damage",
				"destruction", "great destruction", "very great destruction", "complete destruction" };
		return T.en ? Tr.t(en[k - 1]) : ro[k - 1];
	}

	private String dateTime(long ms) {
		Calendar k = Zone.now();
		k.setTimeInMillis(ms);
		String hm = Texts.clock(String.format("%02d:%02d", k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE)), clk12);
		String date = unitF ? String.format("%d/%d/%d", k.get(Calendar.MONTH) + 1, k.get(Calendar.DAY_OF_MONTH), k.get(Calendar.YEAR))
				: String.format("%02d.%02d.%d", k.get(Calendar.DAY_OF_MONTH), k.get(Calendar.MONTH) + 1, k.get(Calendar.YEAR));
		return T.weekdayTime(ms, clk12).replace(" " + hm, "") + " " + date + ", " + hm + ":" + String.format("%02d", k.get(Calendar.SECOND));
	}

	/**
	 * A quake's place: EMSC's capitals in title case ("CRETE, GREECE" -> "Crete, Greece"); USGS's
	 * "5 km NE of Prague, Oklahoma" kept, its kilometres in miles when IMPERIAL (v2.30).
	 */
	public static String place(String region, boolean imperial) {
		if (region == null) return "";
		if (!region.equals(region.toUpperCase())) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?) km (.*)$").matcher(region);
			if (imperial && m.find()) return Math.round(Double.parseDouble(m.group(1)) * 0.621371) + " mi " + m.group(2);
			return region;
		}
		return titleCase(region);
	}

	public static String titleCase(String s) {
		StringBuilder b = new StringBuilder();
		boolean up = true;
		for (char ch : s.toLowerCase().toCharArray()) {
			b.append(up ? Character.toUpperCase(ch) : ch);
			up = !Character.isLetter(ch);
		}
		return b.toString();
	}

	/**
	 * What the bubble of box K says: [0] = { "title", text }, then { "h", section } and { "t", text }
	 * lines (the app shows them in larger letters).
	 */
	public List<String[]> alertInfo(int k) {
		List<String[]> out = new ArrayList<String[]>();
		Quakes.Quake q = quake();
		boolean en = T.en;
		String town = city != null ? city.name : "";
		if (q != null && k == 0) {
			out.add(new String[] { "title", (Tr.t("Earthquake M", "Cutremur M")) + dec(q.mag) });
			out.add(new String[] { "t", (Tr.t("Magnitude: ", "Magnitudine: ")) + dec(q.mag) + (q.magType.length() > 0 ? " (" + q.magType.toUpperCase() + ")" : "") });
			out.add(new String[] { "t", (Tr.t("Time: ", "Ora: ")) + dateTime(q.time) });
			out.add(new String[] { "t", (Tr.t("Region: ", "Zona: ")) + place(q.region, unitF) });
			out.add(new String[] { "t", (Tr.t("Depth: ", "Adâncime: ")) + dist(q.depth) });
			out.add(new String[] { "t", (Tr.t("Epicentre: ", "Epicentru: ")) + dec(Math.abs(q.lat)) + "° " + Tr.compass()[q.lat >= 0 ? 0 : 2] + ", "
					+ dec(Math.abs(q.lon)) + "° " + Tr.compass()[q.lon >= 0 ? 1 : 3] + (Tr.t(", ", ", la ")) + dist(q.dist)
					+ (Tr.t(" from ", " de ")) + town });
			out.add(new String[] { "h", Tr.t("In ", "La ") + town });
			if (q.felt >= 2) out.add(new String[] { "t", (Tr.t("Probably felt, intensity ", "Probabil resimțit, intensitate ")) + roman(q.felt) + ": " + feltWords(q.felt) + "." });
			else out.add(new String[] { "t", Tr.t("Probably not felt here.", "Probabil nu s-a simțit aici.") });
			boolean us = city != null && city.us();
			if (us) out.add(new String[] { "t", Tr.t("Estimated from the magnitude and the distance to the focus (an intensity scale like the Modified Mercalli one); for reports from people, see USGS \"Did You Feel It?\".", "Estimare după magnitudine și distanța până la focar (o scară de intensitate ca Mercalli modificată); pentru mărturiile oamenilor, vezi USGS „Did You Feel It?”.") });
			else out.add(new String[] { "t", Tr.t("Estimated from the magnitude and the distance to the focus (EMS-98 scale); EMSC does not publish the felt reports of each town.", "Estimare după magnitudine și distanța până la focar (scara EMS-98); EMSC nu publică mărturiile pentru fiecare localitate.") });
			if (quakes != null && quakes.size() > 1) {
				out.add(new String[] { "h", Tr.t("All in the last 24 h", "Toate în ultimele 24 h") });
				for (Quakes.Quake o : quakes)
					out.add(new String[] { "t", "M" + dec(o.mag) + " · " + T.weekdayTime(o.time, clk12) + " · " + (Tr.t("depth ", "adânc. ")) + dist(o.depth) });
			}
			if (us) out.add(new String[] { "s", Tr.t("Source: USGS (earthquake.usgs.gov), magnitude 2.5 and over within 300 miles, last 24 h.", "Sursa: USGS (earthquake.usgs.gov), magnitudine 2,5 și peste, pe o rază de 300 de mile, ultimele 24 h.") });
			else out.add(new String[] { "s", Tr.t("Source: EMSC (seismicportal.eu), magnitude 3 and over, last 24 h.", "Sursa: EMSC (seismicportal.eu), magnitudine 3 și peste, ultimele 24 h.") });
			return out;
		}
		List<WarnGroup> gs = warnGroups();
		int gi = k - (q != null ? 1 : 0);
		if (gi < 0 || gi >= gs.size()) return out;
		WarnGroup gr = gs.get(gi);
		if (gr.nws) return nwsInfo(gr, out);
		if (gr.own) return ownInfo(gr, out);
		String name = T.warn[gr.level - 1];
		out.add(new String[] { "title", Tr.ro() ? "Cod " + name.toLowerCase() : Tr.english() ? titleCase(name) + " warning" : Tr.t("Warning") + " · " + titleCase(name) });
		String[] what = en ? new String[] { Tr.t("Potentially dangerous weather (usual for the season, but locally of higher intensity). Be careful outdoors."),
				Tr.t("Dangerous weather of high intensity. Be very careful, keep up with the forecast and follow the authorities' advice."),
				Tr.t("Very dangerous weather of very high intensity. Take protective measures and follow the authorities' instructions.") }
				: new String[] { "Fenomene meteorologice potențial periculoase (obișnuite pentru sezon, dar local mai intense). Fiți atenți dacă sunteți afară.",
						"Fenomene meteorologice periculoase, de intensitate mare. Fiți foarte vigilenți, urmăriți prognoza și sfaturile autorităților.",
						"Fenomene meteorologice foarte periculoase, de intensitate foarte mare. Luați măsuri de protecție și urmați instrucțiunile autorităților." };
		out.add(new String[] { "t", what[gr.level - 1] });
		long nowMs = now.getTimeInMillis();
		for (Warnings.Warn wr : warns) {
			if (wr.until <= nowMs || Math.min(3, Math.max(1, wr.level)) != gr.level) continue;
			out.add(new String[] { "h", T.warnType(wr.type) });
			String st = wr.from <= nowMs ? (Tr.t("In force now, until ", "În vigoare acum, până ")) + T.weekdayTime(wr.until, clk12)
					: (Tr.t("From ", "De ")) + T.weekdayTime(wr.from, clk12) + (Tr.t(" until ", " până ")) + T.weekdayTime(wr.until, clk12);
			out.add(new String[] { "t", st });
			String tip = advice(wr.type);
			if (tip.length() > 0) out.add(new String[] { "t", tip });
		}
		out.add(new String[] { "s", en ? Tr.t("Source: MeteoAlarm (the national weather service), for ") + (city != null ? city.label() : "")
				: "Sursa: MeteoAlarm (ANM), pentru " + (city != null ? city.label() : "") });
		return out;
	}

	/**
	 * The bubble of a US (NWS) box: what the level means, then each alert with its period, the
	 * NWS headline, the description (what, where, when, impacts) and what to do, as NWS wrote them.
	 */
	private List<String[]> nwsInfo(WarnGroup gr, List<String[]> out) {
		out.add(new String[] { "title", Nws.levelWord(gr.level) + " · " + (city != null ? city.name : "") });
		String[] what = { Tr.t("Advisory: weather that is a nuisance or can become dangerous if you are not careful.", "Informare: vreme neplăcută, care poate deveni periculoasă dacă nu ești atent."),
				Tr.t("Watch: the conditions are right for dangerous weather. Be ready to act, keep up with the warnings.", "Atenționare: condițiile sunt favorabile unor fenomene periculoase. Fii pregătit să acționezi și urmărește avertizările."),
				Tr.t("Warning: dangerous weather is happening or about to happen. Take action now to protect yourself.", "Avertizare: fenomenul periculos are loc sau e iminent. Acționează acum ca să te protejezi.") };
		out.add(new String[] { "t", what[gr.level - 1] });
		long nowMs = now.getTimeInMillis();
		for (Warnings.Warn wr : warns) {
			if (!wr.nws || wr.until <= nowMs || Math.min(3, Math.max(1, wr.level)) != gr.level) continue;
			out.add(new String[] { "h", Nws.event(wr.type) });
			String st = wr.from <= nowMs ? (Tr.t("In force now, until ", "În vigoare acum, până ")) + T.weekdayTime(wr.until, clk12)
					: (Tr.t("From ", "De ")) + T.weekdayTime(wr.from, clk12) + (Tr.t(" until ", " până ")) + T.weekdayTime(wr.until, clk12);
			out.add(new String[] { "t", st });
			if (wr.headline.length() > 0) out.add(new String[] { "t", wr.headline });
			if (wr.desc.length() > 0) out.add(new String[] { "t", wr.desc });
			if (wr.instr.length() > 0) {
				out.add(new String[] { "h", Tr.t("What to do", "Ce trebuie făcut") });
				out.add(new String[] { "t", wr.instr });
			}
			if (wr.area.length() > 0) out.add(new String[] { "t", Tr.t("Area: ", "Zona: ") + wr.area });
		}
		out.add(new String[] { "s", Tr.t("Source: National Weather Service (weather.gov), for ", "Sursa: National Weather Service (weather.gov), pentru ") + (city != null ? city.label() : "")
				+ ". " + Tr.t("The alert texts are in English, as NWS publishes them.", "Textele avertizărilor sunt în engleză, așa cum le publică NWS.") });
		return out;
	}

	/** A short piece of advice for a warning type. */
	private String advice(String type) {
		boolean en = T.en;
		if (type.equals("wind")) return Tr.t("Stay away from trees, poles and scaffolding; secure loose objects.", "Feriți-vă de copaci, stâlpi și schele; fixați obiectele care pot fi luate de vânt.");
		if (type.startsWith("thunderstorm")) return Tr.t("Lightning, downpours, hail and gusts are possible; take shelter indoors.", "Sunt posibile descărcări electrice, averse, grindină și intensificări ale vântului; adăpostiți-vă în clădiri.");
		if (type.startsWith("rain") || type.startsWith("flood")) return Tr.t("Avoid flooded roads and river banks.", "Evitați drumurile inundate și malurile râurilor.");
		if (type.startsWith("snow")) return Tr.t("Slippery roads; drive only if you must, with winter equipment.", "Drumuri alunecoase; circulați doar dacă e necesar, cu echipament de iarnă.");
		if (type.equals("fog")) return Tr.t("Low visibility; drive slowly with the fog lights on.", "Vizibilitate redusă; circulați încet, cu luminile de ceață aprinse.");
		if (type.startsWith("high-temperature")) return Tr.t("Avoid the sun between 11 and 18, drink plenty of water.", "Evitați soarele între orele 11 și 18 și beți multă apă.");
		if (type.startsWith("low-temperature")) return Tr.t("Dress warmly and limit time outdoors.", "Îmbrăcați-vă gros și stați cât mai puțin afară.");
		if (type.startsWith("forest-fire")) return Tr.t("Do not light fires outdoors.", "Nu aprindeți focul în aer liber.");
		return "";
	}

	// --- Precipitation -------------------------------------------------------------------

	private String rainText() {
		boolean snowNow = Weather.snowCode(w.code);
		if (w.wetNow() || w.precip > 0) {
			String word = snowNow ? T.snowW : T.rainW;
			if (w.rainEnd.length() > 0 && !w.hours.isEmpty() && w.rainAt.equals(w.hours.get(0)))
				return word + " " + T.until + " " + Texts.clock(w.rainEnd, clk12);
			return word + " " + T.now;
		}
		String word = w.snowMaj ? T.snow : T.rain, from = w.snowMaj ? T.snowFrom : T.rainFrom;
		if (w.rainAt.length() > 0 && w.rainEnd.length() > 0)
			return word + " " + Texts.clock(w.rainAt, clk12) + "–" + Texts.clock(w.rainEnd, clk12);
		if (w.rainAt.length() > 0) return from + " " + Texts.clock(w.rainAt, clk12);
		return "";
	}

	private void drawPrecipBox(float pl, float pt, float pw, float ph, float phd) {
		boolean snow = w.snowMaj || ((w.wetNow() || w.precip > 0) && Weather.snowCode(w.code));
		box(pl, pt, pw, ph, phd);
		String ttl = T.precipUp + " · " + T.next12;
		// In a narrow box a short title, so it stays readable.
		if (fit(true, (phd - 4) * 76 / 100, pw - 41, ttl) < 26) ttl = T.precipUp + " · 12 " + (Tr.t("H", "ORE"));
		head(pt, phd, pl + pw / 2, pw - 40, ttl, 0);
		String rt = rainText(), mx = T.max + " " + w.rainMaxP + "%";
		float room = pw - 42 - g.width(false, 29, mx) - 16;
		if (rt.length() > 0) ltext(true, fit(true, 36, room, rt), pt + 63, pl + 22, rt, c.ink);
		else ltext(true, fit(true, 36, room, T.noPrecip), pt + 63, pl + 22, T.noPrecip, c.dim);
		rtext(false, 29, pt + 68, pl + pw - 20, mx, c.dim);
		boolean leg = false;
		for (boolean s : w.snowy) leg |= s;
		if (leg) {
			drawLegend(pl + 20, pt + 104);
			drawChart(pl + 16, pw - 34, pt + 152, ph - 198);
		} else {
			drawChart(pl + 16, pw - 34, pt + 120, ph - 166);
		}
	}

	private void drawLegend(float x, float y) {
		int[] cs = { c.snow, c.rain, c.dry };
		String[] ls = { T.legSnow, T.legRain, T.legDry };
		for (int k = 0; k < 3; k++) {
			g.rect(x, y, 16, 16, cs[k]);
			ltext(false, 20, y - 2, x + 23, ls[k], c.dim);
			x += 23 + g.width(false, 20, ls[k]) + 22;
		}
	}

	/** One bar per hour, height = probability: black rain, dark gray snow, light gray dry. */
	private void drawChart(float cl, float cw, float ct, float ch) {
		int n = w.probs.length;
		if (n == 0) return;
		float pitch = (float) Math.floor(cw / n), bw = pitch * 85 / 100;
		float ty = ct + ch - Weather.RAIN_PROBABILITY * ch / 100;
		for (float x = cl; x < cl + cw; x += 10) g.rect(x, ty, 5, 2, c.dim);
		for (int k = 0; k < n; k++) {
			float bh = Math.max(4, w.probs[k] * ch / 100);
			int color = !w.wet[k] ? c.dry : w.snowy[k] ? c.snow : c.rain;
			float x = cl + k * pitch + (pitch - bw) / 2;
			g.rect(x, ct + ch - bh, bw, bh, color);
			if (k % 3 == 0) ltext(false, 23, ct + ch + 8, x, Texts.clock(w.hours.get(k), clk12), c.dim);
		}
		// "acum" over the chart where the current time falls, and a dotted line down to the bar.
		String first = w.hours.get(0);
		if (first.length() < 2) return;
		int o = (now.get(Calendar.HOUR_OF_DAY) - Integer.parseInt(first.substring(0, 2)) + 24) % 24;
		if (o >= n) return;
		float nx = cl + o * pitch + now.get(Calendar.MINUTE) * pitch / 60;
		g.poly(new float[] { nx - 8, ct - 8, nx + 8, ct - 8, nx, ct + 3 }, c.accent);
		ctext(true, 15, ct - 25, nx, T.nowMark, c.accent);
		float end = ct + ch - Math.max(4, w.probs[o] * ch / 100) - 2;
		for (float y = ct + 6; y < end; y += 10) g.rect(nx - 1, y, 2, Math.min(6, end - y), c.accent);
	}

	// --- Next days ---------------------------------------------------------------------

	/** Tomorrow / the day after: title, icon, big high, gray "/ low", description. */
	private void drawDayCard(float left, float top, int k, String title) {
		box(left, top, 331, 198, 52);
		head(top, 52, left + 165, 290, title, 0);
		if (k >= w.dayDates.size()) return;
		String[] d = T.describe(w.dayCodes.get(k), true);
		String hi = t(w.dayHi.get(k)) + "°", lo = "/ " + t(w.dayLo.get(k)) + "°";
		float hp = 93, lp, wh, wl;
		while (true) {
			lp = hp / 2;
			wh = g.width(true, hp, hi);
			wl = g.width(false, lp, lo);
			if (84 + wh + 4 + wl <= 311 || hp < 50) break;
			hp = hp * 94 / 100;
		}
		float gx = Math.max(left + 8, left + (331 - 84 - wh - 4 - wl) / 2);
		drawIcon(d[0], gx - 10, top + 67, 92);
		ltext(true, hp, top + 51 + (93 - hp) * 81 / 100, gx + 84, hi, c.ink);
		ltext(false, lp, top + 90 + (47 - lp) * 81 / 100, gx + 84 + wh + 4, lo, c.dim);
		ctext(false, fit(false, 30, 310, d[1]), top + 160, left + 165, d[1], c.ink);
	}

	/**
	 * Portrait: tomorrow and the next four days as boxes of one size, two at a time with no edge
	 * of the next one; the others come in by dragging the row sideways.
	 */
	private void drawDayStrip(float x, float top, float wd) {
		int n = Math.min(5, w.dayDates.size());
		days.area[2] = 0;
		if (n == 0) return;
		float gap = 16, dw = (wd - gap) / 2, step = dw + gap, h = 198;
		float[] starts = new float[n];
		for (int k = 0; k < n; k++) starts[k] = k * step;
		days.set(starts, n * step - gap - wd);
		days.area[0] = x; days.area[1] = top; days.area[2] = wd; days.area[3] = h;
		g.clip(x - 2, top - 2, wd + 4, h + 4);
		for (int k = 0; k < n; k++) {
			float bx = x + k * step - days.scroll;
			if (bx + dw < x - 4 || bx > x + wd + 4) continue;
			drawDayCard(bx, top, k, k == 0 ? T.day1 : k == 1 ? T.day2 : T.weekdayUp(w.dayDates.get(k), true));
		}
		g.unclip();
	}

	/** Three more days: weekday and date, icon, high / low, chance of rain. */
	private void drawDayCols(int first, float top) {
		for (int col = 0; col < 3; col++) {
			float x = 40 + col * 231.5f;
			box(x, top, 215, 150, 44);
			int k = first + col;
			if (k >= w.dayDates.size()) continue;
			float cx = x + 107.5f;
			head(top, 44, cx, 190, T.weekdayUp(w.dayDates.get(k), true), 0);
			drawIcon(T.describe(w.dayCodes.get(k), true)[0], cx - 30, top + 46, 60);
			String s = t(w.dayHi.get(k)) + "° / " + t(w.dayLo.get(k)) + "°";
			float p = fit(true, 36, 190, s);
			ctext(true, p, top + 104 + (36 - p) * 6 / 10, cx, s, c.ink);
		}
	}

	// --- Sun, UV, pollen -------------------------------------------------------------------

	private void scale(float x, float y, int n, int filled, float bw) {
		for (int k = 0; k < n; k++) {
			float sx = x + k * (bw + 4);
			g.rect(sx, y, bw, 10, c.ink);
			if (k >= filled) g.rect(sx + 2, y + 2, bw - 4, 6, c.card);
		}
	}

	// --- Info boxes (sun or moon, UV, pollen, ...) -------------------------------------------

	/** The boxes of the info row, in order: the moon after sunset, humidity only while it rains. */
	private List<String> infoBoxes() {
		List<String> l = new ArrayList<String>();
		// A parcel (v2.60): no pollen (Viorel 2026-11-07).
		boolean pc = parcel != null;
		// Sun / moon, the 24-hour precipitation, UV (Viorel 2026-11-07), then a parcel's own boxes.
		l.add(night() ? "moon" : "sun");
		if (farmer) l.add("r24");
		// v3.10 (Viorel): the farmer's new boxes next to Sun; the US ones only there.
		if (farmer) {
			for (String id : FARM_BOXES) l.add(id);
			if (city != null && city.us()) for (String id : US_BOXES) l.add(id);
		}
		l.add("uv");
		if (pc) for (String id : PARCEL_BOXES) l.add(id);
		// No pollen data in the US (Open-Meteo has it from Copernicus, for Europe only; v2.31).
		if (!pc && (city == null || !city.us())) l.add("pollen");
		if (!pc && (w.precip > 0 || w.code >= 51)) l.add("hum");
		l.add("aqi");
		l.add("press");
		if (!pc) l.add("wind");
		l.add("vis");
		if (hasCompass) l.add("compass");
		return l;
	}

	/** After sunset or before sunrise (today's times). */
	private boolean night() {
		try {
			int m = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
			int rise = Integer.parseInt(w.sunrise.substring(0, 2)) * 60 + Integer.parseInt(w.sunrise.substring(3, 5));
			int set = Integer.parseInt(w.sunset.substring(0, 2)) * 60 + Integer.parseInt(w.sunset.substring(3, 5));
			return m >= set || m < rise;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * The info boxes in a row WD wide, as many as fit (at least 3); the others come in by sliding
	 * the row sideways, box by box (the next one peeks in at the right).
	 */
	private void drawInfoStrip(float x, float top, float wd, float h) {
		List<String> ids = infoBoxes();
		float gap = 16, peek = 30;
		int per = Math.max(3, Math.min(ids.size() - 1, (int) ((wd - peek) / (200 + gap))));
		// The next box shows its edge at the right, so it is clear the row goes on.
		float bw = (wd - peek - gap * per) / per;
		float step = bw + gap;
		float[] starts = new float[ids.size()];
		for (int i = 0; i < ids.size(); i++) starts[i] = i * step;
		info.set(starts, ids.size() * step - gap - wd);
		info.area[0] = x; info.area[1] = top; info.area[2] = wd; info.area[3] = h;
		float tp = 31;
		for (String id : ids) if (!id.equals("r24") && !xbox(id)) tp = fit(true, tp, bw - 16, infoTitle(id));
		g.clip(x - 2, top - 2, wd + 4, h + 4);
		infoIds = ids;
		for (int i = 0; i < ids.size(); i++) {
			float bx = x + i * step - info.scroll;
			if (bx + bw < x - 4 || bx > x + wd + 4) continue;
			if (farmInfo(ids.get(i))) infoHits.add(new float[] { i, bx * hs, top * hs, bw * hs, h * hs });
			drawInfoBox(ids.get(i), bx, top, bw, h, tp);
		}
		g.unclip();
	}

	/**
	 * Landscape: the info boxes two by two (no wind box: the wind is next to the weather now),
	 * moved up and down by the finger; the next row peeks in at the bottom.
	 */
	private void drawInfoGrid(float x, float top, float wd, float h) {
		List<String> ids = infoBoxes();
		ids.remove("wind");
		float gap = 14, peek = 26, bw = (wd - gap) / 2;
		int rows = (ids.size() + 1) / 2;
		float bh = rows <= 2 ? (h - gap) / 2 : (h - peek - 2 * gap) / 2, step = bh + gap;
		float[] starts = new float[rows];
		for (int r = 0; r < rows; r++) starts[r] = r * step;
		grid.set(starts, rows * step - gap - h);
		grid.area[0] = x; grid.area[1] = top; grid.area[2] = wd; grid.area[3] = h;
		float tp = 31;
		for (String id : ids) if (!id.equals("r24") && !xbox(id)) tp = fit(true, tp, bw - 16, infoTitle(id));
		g.clip(x - 2, top - 2, wd + 4, h + 4);
		infoIds = ids;
		for (int i = 0; i < ids.size(); i++) {
			float by = top + (i / 2) * step - grid.scroll;
			if (by + bh < top - 4 || by > top + h + 4) continue;
			if (farmInfo(ids.get(i))) infoHits.add(new float[] { i, (x + (i % 2) * (bw + gap)) * hs, by * hs, bw * hs, bh * hs });
			drawInfoBox(ids.get(i), x + (i % 2) * (bw + gap), by, bw, bh, tp);
		}
		g.unclip();
	}

	private String infoTitle(String id) {
		boolean en = T.en;
		if (id.equals("sun")) return T.sun;
		if (id.equals("moon")) return Tr.t("MOON", "LUNA");
		if (id.equals("uv")) return T.uvT;
		if (id.equals("pollen")) return T.pollen;
		if (id.equals("hum")) return Tr.t("HUMIDITY", "UMIDITATE");
		if (id.equals("aqi")) return Tr.t("AIR QUALITY", "ICA");
		if (id.equals("press")) return Tr.t("PRESSURE", "PRESIUNE");
		if (id.equals("wind")) return Tr.t("WIND", "VÂNT");
		if (id.equals("vis")) return Tr.t("VISIBILITY", "VIZIBILITATE");
		if (id.equals("r24")) return Tr.t("WATER IN THE SOIL", "APA ÎN SOL");
		if (pbox(id)) return boxTitle(id);
		if (xbox(id)) return xTitle(id);
		return Tr.t("COMPASS", "BUSOLĂ");
	}

	private static String hm(long ms) {
		Calendar k = Zone.now();
		k.setTimeInMillis(ms);
		return String.format("%02d:%02d", k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE));
	}

	private void drawInfoBox(String id, float x, float top, float bw, float h, float tp) {
		float cx = x + bw / 2, hd = 46, body = h - hd;
		boolean en = T.en;
		box(x, top, bw, h, hd);
		String ttl = Tr.ro() && infoTitle(id).equals("LUNA") ? "LUNĂ" : infoTitle(id);
		// The long name of the 24-hour box is cut, not shrunk; the bubble has it whole (Viorel, v2.61).
		if (id.equals("r24") && g.width(true, tp, ttl) > bw - 17) {
			while (ttl.length() > 3 && g.width(true, tp, ttl + "…") > bw - 17) ttl = ttl.substring(0, ttl.length() - 1).trim();
			ttl += "…";
		}
		head(top, hd, cx, bw - 16, ttl, tp);
		float top0 = top;
		// The content is laid out for a box 184 high: in a taller one it sits in the middle.
		top += Math.max(0, (h - 184) / 2);
		if (pbox(id) || xbox(id)) {
			drawParcelBox(id, cx, top, bw);
		} else if (id.equals("r24")) {
			// The water held now in the soil's 3–9 cm layer (v2.70, Viorel), and its change in 24 hours.
			double[] sw = soilWater();
			String v = Double.isNaN(sw[0]) ? "–" : mm(sw[0]);
			ctext(true, fit(true, 52, bw - 16, v), top + 62, cx, v, c.ink);
			String sub = Double.isNaN(sw[1]) ? Tr.t("layer 3–9 cm", "stratul 3–9 cm") : soilMm(sw[1]) + Tr.t(" in 24 h", " în 24 h");
			ctext(false, fit(false, 21, bw - 12, sub), top + 128, cx, sub, c.dim);
		} else if (id.equals("sun")) {
			ctext(false, 20, top + 54, cx, T.sunrise, c.ink);
			ctext(true, 29, top + 78, cx, Texts.clock(w.sunrise, clk12), c.ink);
			ctext(false, 20, top + 121, cx, T.sunset, c.ink);
			ctext(true, 29, top + 145, cx, Texts.clock(w.sunset, clk12), c.ink);
		} else if (id.equals("moon")) {
			long ms = now.getTimeInMillis();
			long[] rs = city != null ? Moon.riseSet(ms, city.lat, city.lon) : new long[2];
			float tx = x + (bw - 52) / 2 + 4;
			ctext(false, 20, top + 54, tx, Tr.t("Moonrise", "Răsare"), c.ink);
			ctext(true, 29, top + 78, tx, rs[0] > 0 ? Texts.clock(hm(rs[0]), clk12) : "–", c.ink);
			ctext(false, 20, top + 121, tx, Tr.t("Moonset", "Apune"), c.ink);
			ctext(true, 29, top + 145, tx, rs[1] > 0 ? Texts.clock(hm(rs[1]), clk12) : "–", c.ink);
			float mx = x + bw - 34, my = top + hd + 138 * 0.42f;
			moonPhase(mx, my, 17, Moon.lit(ms), Moon.waxing(ms));
			ctext(false, 19, my + 24, mx, Math.round(Moon.lit(ms) * 100) + "%", c.dim);
		} else if (id.equals("uv")) {
			int uv = w.uv;
			if (uv >= 0) {
				int l = uv <= 2 ? 1 : uv <= 5 ? 2 : uv <= 7 ? 3 : uv <= 10 ? 4 : 5;
				ctext(true, 74, top + 50, cx, Integer.toString(uv), c.ink);
				ctext(false, fit(false, 22, bw - 12, T.uvLv[l - 1]), top + 128, cx, T.uvLv[l - 1], c.ink);
				scale(cx - 60, top + 162, 5, l, 21);
			} else {
				ctext(true, 74, top + 50, cx, "–", c.ink);
				scale(cx - 60, top + 162, 5, 0, 21);
			}
		} else if (id.equals("pollen")) {
			if (pollenLvl > 0) {
				ctext(true, fit(true, 29, bw - 12, T.polLv[pollenLvl - 1]), top + 62, cx, T.polLv[pollenLvl - 1], c.ink);
				String pl = pollenPlant.length() > 0 ? T.plant(pollenPlant) : T.noPollen;
				ctext(false, fit(false, 22, bw - 12, pl), top + 103, cx, pl, c.ink);
			} else {
				ctext(true, 29, top + 62, cx, "–", c.ink);
				ctext(false, 22, top + 103, cx, T.noData2, c.ink);
			}
			scale(cx - 60, top + 162, 4, Math.max(0, pollenLvl), 27);
		} else if (id.equals("hum")) {
			bigValue(cx, top, w.humidity >= 0 ? w.humidity + "%" : "–", Tr.t("relative humidity", "umiditate relativă"), bw);
			scale(cx - 60, top + 162, 5, w.humidity < 0 ? 0 : Math.max(1, (w.humidity + 19) / 20), 21);
		} else if (id.equals("aqi") && city != null && city.us()) {
			// US AQI (EPA): 0–50 good … 301+ hazardous (v2.31).
			String[] lv = { Tr.t("Good", "Bună"), Tr.t("Moderate", "Moderată"), Tr.t("Unhealthy for sensitive groups", "Nesănătoasă pentru sensibili"),
					Tr.t("Unhealthy", "Nesănătoasă"), Tr.t("Very unhealthy", "Foarte nesănătoasă"), Tr.t("Hazardous", "Periculoasă") };
			int l = aqi < 0 ? 0 : aqi <= 50 ? 1 : aqi <= 100 ? 2 : aqi <= 150 ? 3 : aqi <= 200 ? 4 : aqi <= 300 ? 5 : 6;
			bigValue(cx, top, aqi >= 0 ? Integer.toString(aqi) : "–", l > 0 ? lv[l - 1] : T.noData2, bw);
			scale(cx - 60, top + 162, 6, l, 16.67f);
		} else if (id.equals("aqi")) {
			String[] lv = en ? new String[] { Tr.t("Good"), Tr.t("Fair"), Tr.t("Moderate"), Tr.t("Poor"), Tr.t("Very poor"), Tr.t("Extremely poor") }
					: new String[] { "Bună", "Acceptabilă", "Moderată", "Proastă", "Foarte proastă", "Extrem de proastă" };
			int l = aqi < 0 ? 0 : aqi <= 20 ? 1 : aqi <= 40 ? 2 : aqi <= 60 ? 3 : aqi <= 80 ? 4 : aqi <= 100 ? 5 : 6;
			bigValue(cx, top, aqi >= 0 ? Integer.toString(aqi) : "–", l > 0 ? lv[l - 1] : T.noData2, bw);
			scale(cx - 60, top + 162, 6, l, 16.67f);
		} else if (id.equals("press")) {
			drawPressure(cx, top0 + hd, bw, body);
		} else if (id.equals("wind")) {
			drawWindRose(cx, top0 + hd, bw, body);
		} else if (id.equals("vis")) {
			double v = w.visibility;
			String val = Double.isNaN(v) ? "–" : v >= 10000 ? Math.round(v / 1000) + " km" : v >= 1000 ? String.format("%.1f km", v / 1000).replace('.', Tr.dot()) : Math.round(v / 10) * 10 + " m";
			// Imperial (v2.30): miles, tenths below 10.
			if (unitF && !Double.isNaN(v)) {
				double mi = v / 1609.344;
				val = mi >= 10 ? Math.round(mi) + " mi" : String.format(java.util.Locale.US, "%.1f mi", mi).replace('.', Tr.dot());
			}
			int l = Double.isNaN(v) ? 0 : v >= 10000 ? 4 : v >= 4000 ? 3 : v >= 1000 ? 2 : 1;
			String[] lv = en ? new String[] { Tr.t("Poor (fog)"), Tr.t("Moderate"), Tr.t("Good"), Tr.t("Very good") } : new String[] { "Slabă (ceață)", "Moderată", "Bună", "Foarte bună" };
			bigValue(cx, top, val, l > 0 ? lv[l - 1] : T.noData2, bw);
			scale(cx - 60, top + 162, 4, l, 27);
		} else {
			drawCompassBox(cx, top0 + hd, bw, body);
		}
	}

	/** A big value under the title and a short text under it. */
	private void bigValue(float cx, float top, String val, String sub, float bw) {
		float p = fit(true, 58, bw - 20, val);
		ctext(true, p, top + 58 + (58 - p) / 2, cx, val, c.ink);
		ctext(false, fit(false, 22, bw - 12, sub), top + 128, cx, sub, c.ink);
	}

	/** The Moon at CX CY, radius R: the lit part filled (on the right while it grows). */
	private void moonPhase(float cx, float cy, float r, double lit, boolean waxing) {
		g.arc(cx, cy, r, 0, 360, 2, c.ink);
		int n = 24;
		float sgn = waxing ? 1 : -1;
		float[] p = new float[(2 * n + 2) * 2];
		int k = 0;
		for (int i = 0; i <= n; i++) {
			double a = Math.PI * (-0.5 + (double) i / n);
			p[k++] = cx + sgn * r * (float) Math.cos(a);
			p[k++] = cy + r * (float) Math.sin(a);
		}
		for (int i = n; i >= 0; i--) {
			double a = Math.PI * (-0.5 + (double) i / n);
			p[k++] = cx + sgn * r * (float) (Math.cos(a) * (1 - 2 * lit));
			p[k++] = cy + r * (float) Math.sin(a);
		}
		g.poly(p, c.ink);
	}

	/** A thin line from (x1, y1) to (x2, y2). */
	private void line(float x1, float y1, float x2, float y2, float wdt, int col) {
		float dx = x2 - x1, dy = y2 - y1, l = (float) Math.sqrt(dx * dx + dy * dy);
		if (l == 0) return;
		float nx = -dy / l * wdt / 2, ny = dx / l * wdt / 2;
		g.poly(new float[] { x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny }, col);
	}

	/** Pressure as an arc from 960 to 1060 hPa (gap at the bottom), the value and the trend inside. */
	private void drawPressure(float cx, float top, float bw, float body) {
		boolean en = T.en;
		float r = Math.min(bw * 0.36f, body * 0.40f), cy = top + body * 0.50f;
		int track = dark ? 0xFF4F4E4A : Gfx.GRAYD;
		g.arc(cx, cy, r, 135, 270, 9, track);
		for (int i = 0; i <= 20; i++) {
			double a = Math.toRadians(135 + 270.0 * i / 20);
			float l = i % 5 == 0 ? 9 : 5;
			line(cx + (float) Math.cos(a) * (r - 8), cy + (float) Math.sin(a) * (r - 8), cx + (float) Math.cos(a) * (r - 8 - l),
					cy + (float) Math.sin(a) * (r - 8 - l), i % 5 == 0 ? 2 : 1, c.dim);
		}
		double p = w.pressure;
		if (Double.isNaN(p)) {
			ctext(true, 34, cy - 20, cx, "–", c.ink);
			return;
		}
		float f = (float) Math.max(0, Math.min(1, (p - 960) / 100));
		g.arc(cx, cy, r, 135, Math.max(2, 270 * f), 9, c.ink);
		// Imperial (v2.30): inches of mercury, as the US weather service gives it.
		String val = unitF ? String.format(java.util.Locale.US, "%.2f", p * 0.0295300).replace('.', Tr.dot()) : Long.toString(Math.round(p));
		ctext(true, fit(true, 34, r * 1.5f, val), cy - 26, cx, val, c.ink);
		ctext(false, 18, cy + 10, cx, unitF ? "inHg" : "hPa", c.ink);
		double t = w.pressTrend;
		String tr = Double.isNaN(t) ? "" : Math.abs(t) < 1 ? (Tr.t("steady", "stabilă")) : t <= -3 ? (Tr.t("↓ falling fast", "↓ scade rapid"))
				: t < 0 ? (Tr.t("↓ falling", "↓ scade")) : t >= 3 ? (Tr.t("↑ rising fast", "↑ crește rapid")) : (Tr.t("↑ rising", "↑ crește"));
		if (tr.length() > 0) ctext(false, fit(false, 18, bw - 16, tr), top + body - 28, cx, tr, c.dim);
	}

	/** A compass ring with the cardinal letters, turned by ROT degrees (north at the top for 0). */
	private void rose(float cx, float cy, float r, float rot, boolean accentN) {
		g.arc(cx, cy, r, 0, 360, 2, c.ink);
		for (int i = 0; i < 16; i++) {
			if (i % 4 == 0) continue;
			double a = Math.toRadians(i * 22.5 - rot - 90);
			line(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, cx + (float) Math.cos(a) * (r - (i % 2 == 0 ? 7 : 4)),
					cy + (float) Math.sin(a) * (r - (i % 2 == 0 ? 7 : 4)), 1.5f, c.dim);
		}
		String[] L = Tr.compass();
		for (int i = 0; i < 4; i++) {
			double a = Math.toRadians(i * 90 - rot - 90);
			float lx = cx + (float) Math.cos(a) * (r - 13), ly = cy + (float) Math.sin(a) * (r - 13);
			ctext(true, 16, ly - 9, lx, L[i], i == 0 && accentN ? c.accent : c.ink);
		}
	}

	/** Wind: the ring (north up), a pointer on the ring where the wind comes from, the speed inside. */
	private void drawWindRose(float cx, float top, float bw, float body) {
		float r = Math.min(bw * 0.38f, body * 0.44f), cy = top + body * 0.5f;
		rose(cx, cy, r, 0, true);
		if (Double.isNaN(w.wind)) {
			ctext(true, 30, cy - 18, cx, "–", c.ink);
			return;
		}
		String sp = unitF ? Long.toString(Math.round(w.wind * 0.621371)) : Long.toString(Math.round(w.wind));
		ctext(true, 30, cy - 26, cx, sp, c.ink);
		ctext(false, 16, cy + 6, cx, unitF ? "mph" : "km/h", c.ink);
		if (!Double.isNaN(w.windDir)) {
			double a = Math.toRadians(w.windDir - 90);
			float ca = (float) Math.cos(a), sa = (float) Math.sin(a), tipR = r - 4, baseR = r + 9, hw = 8;
			g.poly(new float[] { cx + ca * tipR, cy + sa * tipR, cx + ca * baseR - sa * hw, cy + sa * baseR + ca * hw,
					cx + ca * baseR + sa * hw, cy + sa * baseR - ca * hw }, c.ink);
		}
	}

	/** Compass: the ring turns with the phone; the mark on top is where the phone points. */
	private void drawCompassBox(float cx, float top, float bw, float body) {
		boolean en = T.en, has = !Float.isNaN(heading);
		float r = Math.min(bw * 0.38f, body * 0.42f), cy = top + body * 0.53f;
		rose(cx, cy, r, has ? heading : 0, has);
		g.poly(new float[] { cx, cy - r + 6, cx - 7, cy - r - 8, cx + 7, cy - r - 8 }, has ? c.accent : c.dim);
		if (!has) {
			ctext(false, fit(false, 17, r * 1.1f, Tr.t("no compass", "fără busolă")), cy - 10, cx, Tr.t("no compass", "fără busolă"), c.dim);
			return;
		}
		int deg = Math.round(((heading % 360) + 360) % 360) % 360;
		String dir = Tr.dirs8()[(int) Math.round(deg / 45.0) % 8];
		ctext(true, 26, cy - 24, cx, dir, c.ink);
		ctext(false, 18, cy + 4, cx, deg + "°", c.ink);
	}

	private void drawSunBox(float x, float top, float w3, float h) {
		box(x, top, w3, h, 46);
		float c1 = x + w3 / 6, c2 = x + w3 / 2, c3 = x + w3 * 5 / 6;
		float p = 31;
		float hw = Math.min(200, w3 / 3 - 16);
		for (String s : new String[] { T.sun, T.uvT, T.pollen }) p = fit(true, p, hw, s);
		head(top, 46, c1, hw, T.sun, p);
		head(top, 46, c2, hw, T.uvT, p);
		head(top, 46, c3, hw, T.pollen, p);
		for (float dx : new float[] { x + w3 / 3, x + w3 * 2 / 3 }) g.rect(dx - 1, top + 60, 2, h - 76, c.line);
		ctext(false, 20, top + 54, c1, T.sunrise, c.ink);
		ctext(true, 29, top + 78, c1, Texts.clock(this.w.sunrise, clk12), c.ink);
		ctext(false, 20, top + 121, c1, T.sunset, c.ink);
		ctext(true, 29, top + 145, c1, Texts.clock(this.w.sunset, clk12), c.ink);
		int uv = this.w.uv;
		if (uv >= 0) {
			int l = uv <= 2 ? 1 : uv <= 5 ? 2 : uv <= 7 ? 3 : uv <= 10 ? 4 : 5;
			ctext(true, 74, top + 50, c2, Integer.toString(uv), c.ink);
			ctext(false, 22, top + 128, c2, T.uvLv[l - 1], c.ink);
			scale(c2 - 60, top + 162, 5, l, 21);
		} else {
			ctext(true, 74, top + 50, c2, "–", c.ink);
			scale(c2 - 60, top + 162, 5, 0, 21);
		}
		if (pollenLvl > 0) {
			ctext(true, 29, top + 62, c3, T.polLv[pollenLvl - 1], c.ink);
			ctext(false, 22, top + 103, c3, pollenPlant.length() > 0 ? T.plant(pollenPlant) : T.noPollen, c.ink);
		} else {
			ctext(true, 29, top + 62, c3, "–", c.ink);
			ctext(false, 22, top + 103, c3, T.noData2, c.ink);
		}
		scale(c3 - 60, top + 162, 4, Math.max(0, pollenLvl), 27);
	}

	// --- Wind and compass --------------------------------------------------------------------

	/**
	 * The phone's heading (degrees from north, the way the top of the screen points when the phone
	 * lies flat, or the way its back faces when it stands), from the compass; NaN without one.
	 */
	public float heading = Float.NaN;


	/**
	 * "WIND NE 9 KM/H" right-aligned at RIGHT, and before it a small compass: a ring with the north
	 * mark and an arrow the way the wind blows, both turned with the phone (north up without a compass).
	 */
	private float windRing = 0.62f;

	private void windLine(float px, float top, float right) {
		if (Double.isNaN(w.wind)) return;
		String speed = unitF ? Math.round(w.wind * 0.621371) + " mph" : Math.round(w.wind) + " km/h";
		boolean dir = !Double.isNaN(w.windDir);
		String from = dir ? Tr.dirs8()[(int) Math.round(((w.windDir % 360) + 360) % 360 / 45) % 8] + " " : "";
		String txt = upper(T.wind + " " + from + speed);
		float x = rtext(false, px, top, right, txt, c.ink);
		if (!dir || Math.round(w.wind) == 0) return;
		float r = px * windRing, cx = x - r - 12, cy = top + px * 0.52f;
		float turn = Float.isNaN(heading) ? 0 : heading;
		g.arc(cx, cy, r, 0, 360, 2, c.ink);
		// North: a small triangle on the ring.
		double na = Math.toRadians(-turn - 90);
		float nx = cx + (float) Math.cos(na) * r * 0.8f, ny = cy + (float) Math.sin(na) * r * 0.8f;
		float tx = (float) Math.cos(na + Math.PI / 2) * r * 0.3f, ty = (float) Math.sin(na + Math.PI / 2) * r * 0.3f;
		g.poly(new float[] { nx + (float) Math.cos(na) * r * 0.42f, ny + (float) Math.sin(na) * r * 0.42f, nx + tx, ny + ty, nx - tx, ny - ty },
				Float.isNaN(heading) ? c.dim : c.accent);
		// The wind blows toward windDir + 180.
		double a = Math.toRadians(w.windDir + 180 - turn - 90), ca = Math.cos(a), sa = Math.sin(a);
		float L = r * 0.78f, hw = r * 0.42f;
		float hx = cx + (float) ca * L, hy = cy + (float) sa * L, bx = cx - (float) ca * L * 0.55f, by = cy - (float) sa * L * 0.55f;
		float nxp = (float) -sa, nyp = (float) ca;
		g.poly(new float[] { hx, hy, bx + nxp * hw, by + nyp * hw, cx - (float) ca * L * 0.2f, cy - (float) sa * L * 0.2f, bx - nxp * hw, by - nyp * hw }, c.ink);
	}

	// --- Night clock ----------------------------------------------------------------------------

	/** Night clock (charging at night): the screen dark, a big dim clock, the weather in one line. */
	public boolean nightClock;

	private void drawNightClock(float ch) {
		int bg = 0xFF000000, ink = 0xFF8C8A84, dimInk = 0xFF5E5C58;
		g.rect(0, 0, cw, ch, bg);
		int h = now.get(Calendar.HOUR_OF_DAY), m = now.get(Calendar.MINUTE);
		String hm;
		if (clk12) hm = (h % 12 == 0 ? 12 : h % 12) + String.format(":%02d", m);
		else hm = String.format("%02d:%02d", h, m);
		boolean land = cw > ch;
		float px = fit(true, land ? ch * 0.55f : cw * 0.42f, cw - 120, hm);
		float cy = ch * (land ? 0.42f : 0.40f);
		ctext(true, px, cy - px * 0.56f, cw / 2, hm, ink);
		if (clk12) ctext(true, px * 0.2f, cy - px * 0.56f + px * 1.02f, cw / 2, h < 12 ? "AM" : "PM", dimInk);
		float y = cy + px * 0.62f + (clk12 ? px * 0.22f : 0);
		ctext(false, fit(false, 36, cw - 120, T.date(now)), y, cw / 2, T.date(now), ink);
		if (w != null) {
			String[] d = T.describe(w.code, w.isDay);
			String line = t(w.temp) + "°  " + d[1] + (city != null ? "  ·  " + place() : "");
			float lp = fit(false, 32, cw - 220, line), lw = g.width(false, lp, line), ic = lp * 1.6f;
			float lx = cw / 2 - (lw + ic + 14) / 2;
			g.icon("icons/" + d[0] + ".png", lx, y + 64 - ic * 0.25f, ic, ic, ink);
			ltext(false, lp, y + 64, lx + ic + 14, line, ink);
		}
		String bat = (batt >= 0 ? batt + "%  " : "") + T.charging;
		ctext(false, 26, ch - 70, cw / 2, bat, dimInk);
		ctext(false, 22, ch - 36, cw / 2, Tr.t("Tap to leave the night clock", "Atinge ecranul ca să ieși din ceasul de noapte"), dimInk);
	}

	// --- Brightness ------------------------------------------------------------------------

	/** A ring of radius R at CX CY with its right half filled. */
	private void halfMoon(float cx, float cy, float r, int color) {
		g.arc(cx, cy, r, 0, 360, 3, color);
		float[] xy = new float[2 * 19];
		for (int k = 0; k <= 18; k++) {
			double a = -Math.PI / 2 + k * Math.PI / 18;
			xy[2 * k] = cx + (float) Math.cos(a) * r;
			xy[2 * k + 1] = cy + (float) Math.sin(a) * r;
		}
		g.poly(xy, color);
	}

	/** A sun R units across at CX CY: a disc and eight rays. */
	private void sun(float cx, float cy, float r, int color) {
		float d = r * 0.42f;
		g.roundRect(cx - d, cy - d, 2 * d, 2 * d, d, color);
		float r0 = r * 0.62f, r1 = r, hw = Math.max(1.5f, r * 0.08f);
		for (int k = 0; k < 8; k++) {
			double a = k * Math.PI / 4;
			float ca = (float) Math.cos(a), sa = (float) Math.sin(a), nx = -sa * hw, ny = ca * hw;
			g.poly(new float[] { cx + ca * r0 + nx, cy + sa * r0 + ny, cx + ca * r1 + nx, cy + sa * r1 + ny, cx + ca * r1 - nx,
					cy + sa * r1 - ny, cx + ca * r0 - nx, cy + sa * r0 - ny }, color);
		}
	}

	/**
	 * A slider like the phone's brightness bar: a rounded track, the level filled from the bottom
	 * (VERTICAL) or the left, never less than a round end, with a sun in that end.
	 */
	private void pill(float x, float y, float w, float h, boolean vertical, float level) {
		int track = (barTrack & 0xFFFFFF) | 0x80000000, fill = barFill, mark = barIcon;
		float t = vertical ? w : h, len = vertical ? h : w, f = t + Math.max(0, Math.min(1, level)) * (len - t);
		g.roundRect(x, y, w, h, t / 2, track);
		if (vertical) {
			g.roundRect(x, y + h - f, w, f, t / 2, fill);
			sun(x + w / 2, y + h - t / 2, t * 0.3f, mark);
		} else {
			g.roundRect(x, y, f, h, t / 2, fill);
			sun(x + t / 2, y + h / 2, t * 0.3f, mark);
		}
	}

	/**
	 * The brightness bar near the left edge, like the phone's (the track half see-through), and
	 * over it a round button with a moon that switches between the dark and the light theme.
	 */
	private void drawBrightBar(float ch) {
		float x = 50, bw = 84, bh = ch * 0.42f, gap = 24, top = (ch - bw - gap - bh) / 2, by = top + bw + gap;
		btnMoon[0] = x; btnMoon[1] = top; btnMoon[2] = bw; btnMoon[3] = bw;
		moonButton(x, top, bw);
		barArea[0] = x; barArea[1] = by; barArea[2] = bw; barArea[3] = bh;
		pill(x, by, bw, bh, true, brightLevel);
	}

	// --- Status line -----------------------------------------------------------------------

	private void footer(float fb) {
		btnLocate[1] = fb;
		btnBright[1] = fb;
		btnSensor[1] = fb;
		footButton("city", btnCity, false);
		footButton("locate", btnLocate, autoLoc);
		footButton("bright", btnBright, brightBar);
		if (hasLight) footButton("sensor", btnSensor, sensorTheme);
		else {
			button(btnSensor);
			buttonIcon("sensor", btnSensor[0] + 32, btnSensor[1] + 32, c.dim);
		}
		float batLeft = cw - 40;
		float gy = fb + 22;
		if (batt >= 0) {
			float gx = cw - 40 - 44;
			g.rect(gx, gy, 40, 20, c.ink);
			g.rect(gx + 2, gy + 2, 36, 16, c.page);
			g.rect(gx + 5, gy + 5, Math.max(2, batt * 30 / 100), 10, c.ink);
			g.rect(gx + 40, gy + 6, 4, 8, c.ink);
			batLeft = rtext(true, 26, gy - 4, gx - 7, batt + "%", c.ink);
			if (charging) {
				g.icon("icons/bolt.png", batLeft - 16, gy + 2, 11, 17, c.ink);
				batLeft -= 16;
				float mp = fit(true, 26, batLeft - 10 - 330, T.charging);
				batLeft = rtext(true, mp, gy - 4 + (26 - mp) * 81 / 100, batLeft - 10, T.charging, c.ink);
			}
		}
		float x = btnSensor[0] + btnSensor[2] + 24, dl = batLeft - 30;
		if (lowPages > 1) {
			float dw = lowPages * 22 - 10, dx = cw / 2 - dw / 2;
			for (int k = 0; k < lowPages; k++) g.roundRect(dx + k * 22, fb + 26, 12, 12, 6, k == lowPage ? c.ink : c.dim);
			dl = dx - 30;
		}
		if (statsLow) x = statsButton(x, fb, 64, Math.max(120, dl - x - (farmer && tagLow ? 200 : 0)), 26) + 30;
		else x += 6;
		if (farmer && tagLow) {
			// v3.10: landscape, right of the buttons, larger, centred on their row.
			String s = Tr.t("FARMER MODE", "MODUL FERMIER");
			float p = fit(true, 34, Math.max(40, dl - x), s);
			if (dl - x > 40) ltext(true, p, fb + 32 - p * 0.56f, x, s, c.accent);
		}
		drawUpdated();
		drawNote(fb);
	}

	/**
	 * A status-row button at B: its frame and its icon (WHICH: city, locate, bright, sensor),
	 * filled in ink when ON (the sun while its bar shows, the sensor theme while it is on).
	 */
	private void footButton(String which, float[] b, boolean on) {
		button(b);
		if (on) g.roundRect(b[0] + 2, b[1] + 2, b[2] - 4, b[3] - 4, 8, c.ink);
		buttonIcon(which, b[0] + 32, b[1] + 32, on ? c.page : c.ink);
	}

	private void buttonIcon(String which, float cx, float cy, int ink) {
		if (which.equals("city")) g.icon("icons/btn-city.png", cx - 16, cy - 16, 32, 32, ink);
		else if (which.equals("locate")) {
			// A crosshair; accent while looking, gray while automatic location is off.
			// Filled while automatic location is on (the button draws the fill); accent while looking.
			int lc = locating ? c.accent : ink;
			g.arc(cx, cy, 13, 0, 360, 3, lc);
			g.arc(cx, cy, 2.5f, 0, 360, 5, lc);
			g.rect(cx - 1.5f, cy - 22, 3, 8, lc);
			g.rect(cx - 1.5f, cy + 14, 3, 8, lc);
			g.rect(cx - 22, cy - 1.5f, 8, 3, lc);
			g.rect(cx + 14, cy - 1.5f, 8, 3, lc);
		} else if (which.equals("bright")) sun(cx, cy, 22, ink);
		else halfMoon(cx, cy, 17, ink);
	}

	/** The moon button (BW across at X, TOP): a thick ring, the crescent; inverted in the dark theme. */
	private void moonButton(float x, float top, float bw) {
		int ink = dark ? Gfx.WHITE : Gfx.BLACK, bg = dark ? Gfx.BLACK : Gfx.WHITE;
		float r = bw / 2, ring = bw / 12;
		g.roundRect(x, top, bw, bw, r, ink);
		g.roundRect(x + ring, top + ring, bw - 2 * ring, bw - 2 * ring, r - ring, bg);
		crescent(x + r, top + r, r * 0.46f, ink, bg);
	}

	/** A crescent of radius M at CX CY: a disc with another one, in BG, over its upper right. */
	private void crescent(float cx, float cy, float m, int ink, int bg) {
		g.roundRect(cx - m, cy - m, 2 * m, 2 * m, m, ink);
		float m2 = m * 0.8f;
		g.roundRect(cx + m * 0.42f - m2, cy - m * 0.42f - m2, 2 * m2, 2 * m2, m2, bg);
	}

	/**
	 * An icon for the ☰ menu and the help, in a 64 x 64 unit box on GFX. The ones that are also a
	 * button on the screen (city, locate, bright, sensor, moon) are drawn as that button; the
	 * others as a plain line icon (refresh, unit, clock, light, dark, keepOn, lang-ro, lang-en,
	 * update, help, about, edge, pull, swipe).
	 */
	public void menuIcon(Gfx gfx, String which) {
		g = gfx;
		c = dark ? Palette.DARK : Palette.LIGHT;
		int k = c.ink;
		float[] b = { 0, 0, 64, 64 };
		if (which.equals("city") || which.equals("locate") || which.equals("bright") || which.equals("sensor"))
			footButton(which, b, which.equals("sensor") && sensorTheme);
		else if (which.equals("moon")) moonButton(2, 2, 60);
		else if (which.equals("farm")) {
			// A sprout over a furrowed field (farmer mode, v2.50).
			g.rect(30, 20, 4, 26, k);
			g.poly(new float[] { 32, 30, 12, 24, 8, 10, 26, 14 }, k);
			g.poly(new float[] { 32, 24, 50, 8, 58, 18, 42, 30 }, k);
			g.rect(6, 48, 52, 4, k);
			g.rect(12, 56, 40, 4, k);
		} else if (which.equals("normal")) {
			// A sun and a cloud: back to the usual screen.
			sun(24, 24, 18, k);
			g.roundRect(18, 32, 40, 20, 10, k);
			g.roundRect(30, 22, 20, 20, 10, k);
		} else if (which.equals("parcel")) {
			// A pin over a field drawn in perspective.
			g.poly(new float[] { 4, 58, 60, 58, 50, 40, 14, 40 }, k);
			g.poly(new float[] { 8, 55, 56, 55, 48, 43, 16, 43 }, c.page);
			g.roundRect(20, 2, 24, 24, 12, k);
			g.poly(new float[] { 22, 18, 42, 18, 32, 44 }, k);
			g.roundRect(28, 10, 8, 8, 4, c.page);
		} else if (which.equals("refresh")) {
			g.arc(32, 32, 17, -50, 290, 4, k);
			g.poly(new float[] { 36, 7, 53, 21, 35, 28 }, k);
		} else if (which.equals("unit")) {
			// A thermometer.
			g.roundRect(25, 6, 14, 38, 7, k);
			g.roundRect(28, 9, 8, 32, 4, c.page);
			g.roundRect(21, 38, 22, 22, 11, k);
			g.rect(30, 22, 4, 20, k);
		} else if (which.equals("clock")) {
			g.arc(32, 32, 22, 0, 360, 4, k);
			g.rect(30, 15, 4, 19, k);
			g.rect(30, 30, 14, 4, k);
		} else if (which.equals("light")) sun(32, 32, 26, k);
		else if (which.equals("dark")) crescent(32, 32, 20, k, c.page);
		else if (which.equals("keepOn")) {
			// A phone with rays: the screen stays lit.
			g.roundRect(20, 6, 24, 44, 5, k);
			g.roundRect(23, 10, 18, 34, 3, c.page);
			g.rect(10, 22, 6, 3, k);
			g.rect(48, 22, 6, 3, k);
			g.rect(29, 54, 6, 6, k);
			g.rect(10, 32, 6, 3, k);
			g.rect(48, 32, 6, 3, k);
		} else if (which.startsWith("lang")) {
			String t = which.length() > 5 ? which.substring(5).toUpperCase() : "EN";
			g.roundRect(6, 12, 52, 40, 8, k);
			g.roundRect(9, 15, 46, 34, 6, c.page);
			ctext(true, 24, 18, 32, t, k);
		} else if (which.equals("update")) {
			g.rect(30, 6, 4, 30, k);
			g.poly(new float[] { 18, 30, 46, 30, 32, 46 }, k);
			g.rect(10, 52, 44, 4, k);
		} else if (which.equals("night")) {
			// A clock with a crescent: the night clock.
			g.arc(28, 36, 20, 0, 360, 4, k);
			g.rect(26, 22, 4, 15, k);
			g.rect(26, 34, 11, 4, k);
			crescent(50, 14, 11, k, c.page);
		} else if (which.equals("area")) {
			// Two houses: the neighbourhood.
			g.poly(new float[] { 4, 34, 22, 16, 40, 34 }, k);
			g.rect(9, 33, 26, 23, k);
			g.rect(13, 37, 18, 15, c.page);
			g.rect(19, 42, 6, 10, k);
			g.poly(new float[] { 30, 26, 45, 12, 60, 26 }, k);
			g.rect(37, 25, 19, 31, k);
			g.rect(41, 29, 11, 23, c.page);
			g.rect(44, 44, 5, 8, k);
		} else if (which.equals("compass")) {
			g.arc(32, 32, 24, 0, 360, 3, k);
			g.poly(new float[] { 32, 10, 40, 34, 32, 30, 24, 34 }, c.accent);
			g.poly(new float[] { 32, 54, 24, 30, 32, 34, 40, 30 }, k);
		} else if (which.equals("help") || which.equals("about")) {
			g.arc(32, 32, 24, 0, 360, 4, k);
			ctext(true, 34, 13, 32, which.equals("help") ? "?" : "i", k);
		} else if (which.equals("edge")) {
			// The left edge with an up-down arrow.
			g.rect(8, 4, 4, 56, k);
			g.rect(30, 16, 4, 32, k);
			g.poly(new float[] { 22, 18, 42, 18, 32, 6 }, k);
			g.poly(new float[] { 22, 46, 42, 46, 32, 58 }, k);
		} else if (which.equals("pull")) {
			g.rect(8, 6, 48, 4, k);
			g.rect(30, 14, 4, 30, k);
			g.poly(new float[] { 20, 40, 44, 40, 32, 56 }, k);
		} else if (which.equals("swipe")) {
			g.rect(14, 30, 36, 4, k);
			g.poly(new float[] { 18, 22, 18, 42, 6, 32 }, k);
			g.poly(new float[] { 46, 22, 46, 42, 58, 32 }, k);
		} else if (which.equals("widget")) {
			// Four tiles of the home screen, one of them wide.
			g.roundRect(6, 6, 52, 22, 5, k);
			g.roundRect(9, 9, 46, 16, 3, c.page);
			g.roundRect(6, 34, 22, 24, 5, k);
			g.roundRect(9, 37, 16, 18, 3, c.page);
			g.roundRect(36, 34, 22, 24, 5, k);
		} else if (which.equals("tornado")) {
			// A funnel: bands narrowing downwards and leaning (v2.40).
			float[][] bands = { { 6, 8, 52 }, { 11, 18, 42 }, { 17, 28, 31 }, { 23, 38, 21 }, { 27, 48, 13 }, { 29, 56, 7 } };
			for (float[] x : bands) g.roundRect(x[0], x[1], x[2], 6, 3, k);
		} else if (which.equals("quake")) {
			// A seismograph line.
			float[] xs = { 4, 16, 21, 27, 33, 39, 45, 60 }, ys = { 32, 32, 18, 50, 8, 56, 32, 32 };
			for (int i = 0; i + 1 < xs.length; i++) {
				float dx = xs[i + 1] - xs[i], dy = ys[i + 1] - ys[i], len = (float) Math.sqrt(dx * dx + dy * dy);
				float nx = -dy / len * 2.2f, ny = dx / len * 2.2f;
				g.poly(new float[] { xs[i] + nx, ys[i] + ny, xs[i + 1] + nx, ys[i + 1] + ny, xs[i + 1] - nx, ys[i + 1] - ny, xs[i] - nx, ys[i] - ny }, k);
			}
		} else if (which.equals("bell")) {
			// A bell: the notifications.
			g.roundRect(18, 8, 28, 30, 14, k);
			g.poly(new float[] { 18, 23, 46, 23, 52, 46, 12, 46 }, k);
			g.roundRect(8, 44, 48, 6, 3, k);
			g.roundRect(26, 50, 12, 10, 5, k);
			g.rect(30, 3, 4, 8, k);
		} else if (which.equals("log")) {
			// A page with lines.
			g.roundRect(10, 4, 44, 56, 6, k);
			g.roundRect(14, 8, 36, 48, 4, c.page);
			for (int i = 0; i < 4; i++) g.rect(19, 16 + i * 10, i == 3 ? 16 : 26, 4, k);
		} else if (which.equals("share")) {
			// Three dots joined: share the app.
			float[][] pt = { { 46, 14 }, { 16, 32 }, { 46, 50 } };
			for (int i = 0; i < 2; i++) {
				float x0 = pt[1][0], y0 = pt[1][1], x1 = pt[i * 2][0], y1 = pt[i * 2][1];
				float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy), nx = -dy / len * 2, ny = dx / len * 2;
				g.poly(new float[] { x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny }, k);
			}
			for (float[] q : pt) g.roundRect(q[0] - 8, q[1] - 8, 16, 16, 8, k);
		} else if (which.equals("textsize")) {
			// A small and a large A: the text size.
			ctext(true, 22, 30, 17, "A", k);
			ctext(true, 40, 14, 42, "A", k);
		} else if (which.equals("roll")) {
			// Two boxes side by side with an arrow under them.
			g.roundRect(4, 10, 26, 22, 4, k);
			g.roundRect(34, 10, 26, 22, 4, k);
			g.rect(12, 44, 34, 4, k);
			g.poly(new float[] { 44, 38, 44, 54, 56, 46 }, k);
		}
	}

	/** A short notice over the status row after a button (MainActivity clears it after 1.5 s). */
	public String note = "";
	/** The notice under the line under the town (after a tap on the town's name). */
	public boolean noteTop;

	private void drawNote(float fb) {
		if (note.length() == 0) return;
		float p = fit(true, 30, cw - 160, note), w = g.width(true, p, note) + 56, h = 66, x = (cw - w) / 2, y = fb - h - 18;
		if (noteTop) {
			x = 40;
			y = 116;
		}
		g.roundRect(x, y, w, h, 33, c.ink);
		g.roundRect(x + 2, y + 2, w - 4, h - 4, 31, c.page);
		ctext(true, p, y + (h - p * 1.12f) / 2, x + w / 2, note, c.ink);
	}

	/** "updated 12:34" under the line, at the right. */
	private void drawUpdated() {
		String upd = "";
		boolean bold = false;
		if (busy) {
			upd = T.updating;
		} else if (fetched > 0) {
			Calendar cal = Zone.now();
			cal.setTimeInMillis(fetched);
			String when = Texts.clock(String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)), clk12);
			upd = T.updated + " " + when + (source.length() > 0 && !source.equals("Open-Meteo") ? " · " + source : "");
			if (offline) {
				upd = "! " + T.noData + " " + when + (failReason.length() > 0 ? " · " + failReason : "");
				bold = true;
			}
		}
		if (upd.length() == 0) return;
		if (updHead) {
			// Landscape: in the header, left of the clock.
			rtext(bold, fit(bold, 25, 360, upd), 30, clockLeft - 24, upd, bold ? c.ink : c.dim);
		} else {
			// Clear of "FARMER MODE" at the left, and of the dots in the middle when there are several screens.
			float room = pages > 1 ? cw / 2f - (pages * 22 - 10) / 2f - 24 - 40 : cw - 80 - Math.max(40, farmTagW);
			rtext(bold, fit(bold, 25, room, upd), 110, cw - 40, upd, bold ? c.ink : c.dim);
		}
	}

	/** How much of the next box shows at the end of a row (units). */
	private float peekW = 30;
	private boolean chartFull;

	/** Landscape (phone): "updated" goes in the header next to the clock. */
	private boolean updHead;
}
