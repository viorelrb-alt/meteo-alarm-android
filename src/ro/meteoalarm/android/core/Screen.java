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
	public int pollenLvl = -1;
	/** European air quality index, -1 when unknown. */
	public int aqi = -1;
	public String pollenPlant = "";
	public Cities.City city;
	public Texts T = new Texts(false);
	public boolean unitF, clk12;
	public Calendar now = Calendar.getInstance();
	public int batt = -1;
	public boolean charging, busy, offline;
	public long fetched;
	public String error = "";
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
	public final Strip hours = new Strip(false), days = new Strip(false), info = new Strip(false), grid = new Strip(true);

	public Strip[] strips() {
		return new Strip[] { hours, days, info, grid };
	}

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
		if (tablet) drawTablet(ch);
		else if (cw > ch) drawLandscape(ch);
		else drawPortrait(ch);
		barArea[2] = 0;
		btnMoon[2] = 0;
		if (brightBar) drawBrightBar(ch);
	}

	private void drawPortrait(float ch) {
		float fb = ch - 76;
		for (Strip st : strips()) st.area[2] = 0;
		btnCity[1] = fb;
		btnLocate[1] = fb;
		g.rect(40, 102, 678, 2, c.line);
		warnArea[2] = 0;
		// The town next to the menu button, above the line.
		String label = city != null ? city.label() : "";
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

		float nowBot = 498 + Math.max(0, ch - BASE_H);
		float fy = nowBot - 4;
		float top = drawWarnBoxes(40, 146, 678, 2, 1);
		String[] d = T.describe(w.code, w.isDay);
		drawNowSide(d[0], 40, top > 0 ? top - 50 : 104, 718, fy + 4);

		// Description and wind on the right, "feels like" and high / low on the left.
		rtext(true, fit(true, 39, 330, d[1]), fy, 712, d[1], c.ink);
		windLine(30, fy + 40, 712);
		if (!Double.isNaN(w.feels)) ltext(false, 30, fy + 7, 40, upper(T.feels + " " + t(w.feels) + "°"), c.ink);
		if (!Double.isNaN(w.tmax) && !Double.isNaN(w.tmin))
			ltext(false, 30, fy + 40, 40, upper(T.hi + " " + t(w.tmax) + "° / " + T.lo + " " + t(w.tmin) + "°"), c.ink);

		float y0 = fy + 80;
		drawNextHours(40, y0, 678, 300);
		drawDayCard(40, y0 + 314, 0, T.day1);
		drawDayCard(387, y0 + 314, 1, T.day2);
		drawDayCols(2, y0 + 526);
		drawInfoStrip(40, y0 + 690, 678, 184);
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
		float z1 = Math.round(cw * 0.36f), z2 = Math.round(cw * 0.73f), leftR = z1 - 22;
		float mx = z1 + 22, mw = z2 - 10 - mx, rx = z2 + 10, rw = cw - 40 - rx;
		g.rect(40, 102, cw - 80, 2, c.line);
		String label = city != null ? city.label() : "";
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
		float wb = drawWarnBoxes(40, 116, leftR - 40, 2, 1);
		float nowTop = wb > 0 ? wb + 14 : 116;
		float fy = 568;
		String[] d = T.describe(w.code, w.isDay);
		drawNowSide(d[0], 40, nowTop - 60, leftR, fy + 4);
		rtext(true, fit(true, 39, (leftR - 40) / 2, d[1]), fy, leftR - 6, d[1], c.ink);
		windLine(30, fy + 40, leftR - 6);
		if (!Double.isNaN(w.feels)) ltext(false, 30, fy + 7, 40, upper(T.feels + " " + t(w.feels) + "°"), c.ink);
		if (!Double.isNaN(w.tmax) && !Double.isNaN(w.tmin))
			ltext(false, 30, fy + 40, 40, upper(T.hi + " " + t(w.tmax) + "° / " + T.lo + " " + t(w.tmin) + "°"), c.ink);
		// The line between the weather now and the boxes, like the one under the clock.
		g.rect(z1 - 1, 124, 2, fb - 14 - 124, c.line);

		float bt = 124, bb = fb - 14, hh = 258;
		drawNextHours(mx, bt, mw, hh);
		drawDaysRow(mx, bt + hh + 4, mw, bb - (bt + hh + 4));
		drawInfoGrid(rx, 146, rw, bb - 146);
		footer(fb);
	}

	/** Landscape: the five days as a row moved sideways (three shown, the next one peeking in). */
	private void drawDaysRow(float x, float top, float wd, float h) {
		int n = Math.min(5, w.dayDates.size());
		if (n == 0) return;
		float gap = 14, peek = 30, dw = (wd - peek - 2 * gap) / 3, step = dw + gap;
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
		for (Strip st : strips()) st.area[2] = 0;
		String label = city != null ? city.label() : "";
		float lp = fit(true, 40, cw / 2 - 160, label);
		ltext(true, lp, 56 - lp * 0.5f, 124, label, c.ink);
		cityArea[0] = 124; cityArea[1] = 24; cityArea[2] = Math.max(200, g.width(true, lp, label)); cityArea[3] = 64;
		if (w == null) {
			String msg = error.length() > 0 ? error : T.loading;
			ctext(true, fit(true, 48, cw - 200, msg), ch / 3, cw / 2, msg, c.ink);
			footer(fb);
			return;
		}
		float nl = 40, nr = land ? cw / 2 - 20 : cw - 40, nt = land ? 116 : 146, nb = land ? fb - 20 : ch * 0.42f;
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
		rtext(true, fit(true, 44, (nr - nl) / 2, d[1]), lineY, nr - 6, d[1], c.ink);
		windLine(32, lineY + 46, nr - 6);
		if (!Double.isNaN(w.feels)) ltext(false, 32, lineY + 7, nl, upper(T.feels + " " + t(w.feels) + "°"), c.ink);
		if (!Double.isNaN(w.tmax) && !Double.isNaN(w.tmin))
			ltext(false, 32, lineY + 46, nl, upper(T.hi + " " + t(w.tmax) + "° / " + T.lo + " " + t(w.tmin) + "°"), c.ink);

		// The boxes, in units 1.2 times larger.
		Gfx g0 = g;
		float bs = 1.2f;
		g = new Scaled(g0, bs);
		float bx = (land ? cw / 2 + 20 : 40) / bs, bw = (land ? cw / 2 - 60 : cw - 80) / bs, by = (land ? 146 : nb + 24) / bs;
		float avail = (fb - 14) / bs - by, ih = 190, hh = Math.min(300, (avail - ih - 28) / 2), dh = avail - ih - 28 - hh;
		drawNextHours(bx, by, bw, hh);
		for (int k = 0; k < 4; k++) hours.area[k] *= bs;
		float dy = by + hh + 14, dw = (bw - 4 * 12) / 5;
		for (int k = 0; k < 5; k++) {
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
	private void drawNextHours(float x, float y, float wd, float h) {
		hours.area[2] = 0;
		int avail = w.tileHours.size() - 1;
		if (avail < 1) {
			drawPrecipBox(x, y, wd, h, 46);
			return;
		}
		boolean chart = rainText().length() > 0;
		float gap = 16, peek = 30, bh = h - 26;
		int per = Math.max(1, Math.min(avail, (int) ((wd - peek + gap) / (140 + gap))));
		float bw = avail <= per && !chart ? (wd - gap * (avail - 1)) / avail : (wd - peek - gap * per) / per;
		float x0 = chart ? wd + gap : 0, step = bw + gap;
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
		if (chart && sc < wd) drawPrecipBox(x - sc, y, wd, bh, 46);
		for (int k = 1; k <= avail; k++) {
			float bx = x + x0 + (k - 1) * step - sc;
			if (bx + bw < x - 4 || bx > x + wd + 4) continue;
			drawHourBox(k, bx, y, bw, bh, tp, dp);
		}
		g.unclip();
		g.rect(x, y + bh + 12, wd, 2, c.line);
	}

	/** One hour: the hour on top, the icon, the temperature and the sky. */
	private void drawHourBox(int k, float x, float y, float bw, float bh, float tp, float dp) {
		float hd = 44, cx = x + bw / 2;
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
	private void dots(float cx, float y, int n, int cur) {
		if (n <= 1) return;
		float dx = cx - (n * 22 - 10) / 2f;
		for (int k = 0; k < n; k++) g.roundRect(dx + k * 22, y, 12, 12, 6, k == cur ? c.ink : c.dim);
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
	private float tp, tMw, tWn, tGap, tWu, tU, tW;
	private boolean tNeg;
	private String tN;

	private void tempSize(float px, float maxw) {
		tN = t(w.temp);
		tNeg = tN.startsWith("-");
		if (tNeg) tN = tN.substring(1);
		tp = px;
		while (true) {
			tU = tp * 43 / 100;
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
		g.text(true, tU, base - dh - tU * 17 / 100, x, "°", c.ink);
		g.text(true, tU, base - tU * 81 / 100, x, unitF ? "F" : "C", c.ink);
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
				if (!(", " + gr.types + ",").contains(", " + wr.type + ",")) gr.types += (gr.types.length() > 0 ? ", " : "") + wr.type;
				gr.from = Math.min(gr.from, wr.from);
				gr.until = Math.max(gr.until, wr.until);
			}
			if (gr.types.length() > 0) gs.add(gr);
		}
		return gs;
	}

	// Title bars: light pastels with black text, or dark shades with white text (yellow, orange, red).
	private static final int[] BAR_LIGHT = { 0xFFEDF9A5, 0xFFFFC285, 0xFFFF8585 }, BAR_DARK = { 0xFF8C7A12, 0xFFA8561E, 0xFF9E2B2B };

	/**
	 * One box per warning code across WD: the code in a coloured title bar, the types and the
	 * period under it, a warning triangle in the red one. COLS fit (a phone: two, the third is
	 * reached by swiping, with dots under the boxes; a tablet: three equal boxes), F times larger.
	 * Returns the bottom of what was drawn, 0 without warnings.
	 */
	private float drawWarnBoxes(float x0, float y, float wd, int cols, float f) {
		List<WarnGroup> gs = warnGroups();
		int n = gs.size(), vis = Math.min(cols, n);
		warnBoxes = n;
		if (n == 0) return 0;
		warnStart = Math.max(0, Math.min(warnStart, n - vis));
		int across = cols > 2 ? cols : vis;
		float gap = 16, bw = (wd - gap * (across - 1)) / across, h = 130 * f, hb = 44 * f, r = 14;
		warnArea[0] = x0; warnArea[1] = y; warnArea[2] = wd; warnArea[3] = h;
		int border = c.ink, body = dark ? 0xFF000000 : c.page;
		for (int i = 0; i < vis; i++) {
			WarnGroup gr = gs.get(warnStart + i);
			float x = x0 + i * (bw + gap);
			int bar = (dark ? BAR_DARK : BAR_LIGHT)[gr.level - 1];
			g.roundRect(x, y, bw, h, r, border);
			g.roundRect(x + 2, y + 2, bw - 4, h - 4, r - 2, body);
			g.roundRect(x + 2, y + 2, bw - 4, hb, r - 2, bar);
			g.rect(x + 2, y + r, bw - 4, hb - r + 2, bar);
			g.rect(x + 2, y + hb, bw - 4, 2, border);
			String name = T.warn[gr.level - 1];
			String title = T.en ? name + " " + T.warnCode : T.warnCode + " " + name;
			float hp = fit(true, 30 * f, bw - 24, title), ty = y + 2 + (hb - 2) / 2 - hp * 485 / 1000;
			int tc = dark ? 0xFFFFFFFF : 0xFF000000;
			float tx = x + bw / 2 - g.width(true, hp, title) / 2;
			g.text(true, hp, ty, tx, title, tc);
			g.text(true, hp, ty, tx + 1, title, tc);
			float lx = x + 18;
			if (gr.level >= 3) {
				g.icon("ui/atentie.png", x + 14, y + hb + (h - hb - 46 * f) / 2, 50 * f, 46 * f, dark ? c.warnR : c.ink);
				lx = x + 28 + 50 * f;
			}
			float room = x + bw - 12 - lx;
			String txt = T.warnType(gr.types);
			String per = T.weekdayTime(gr.from, clk12) + " – " + T.weekdayTime(gr.until, clk12);
			ltext(true, fit(true, 30 * f, room, txt), y + hb + 12 * f, lx, txt, c.ink);
			ltext(false, fit(false, 26 * f, room, per), y + hb + 50 * f, lx, per, c.dim);
		}
		if (n <= vis) return y + h;
		// One dot per position of the boxes; the current one in ink.
		dots(x0 + wd / 2, y + h + 10, n - vis + 1, warnStart);
		return y + h + 26;
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
		head(pt, phd, pl + pw / 2, pw - 40, (snow ? T.snowUp : T.rainUp) + " · " + T.next12, 0);
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
		l.add(night() ? "moon" : "sun");
		l.add("uv");
		l.add("pollen");
		if (w.precip > 0 || w.code >= 51) l.add("hum");
		l.add("aqi");
		l.add("press");
		l.add("wind");
		l.add("vis");
		l.add("compass");
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
		for (String id : ids) tp = fit(true, tp, bw - 16, infoTitle(id));
		g.clip(x - 2, top - 2, wd + 4, h + 4);
		for (int i = 0; i < ids.size(); i++) {
			float bx = x + i * step - info.scroll;
			if (bx + bw < x - 4 || bx > x + wd + 4) continue;
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
		for (String id : ids) tp = fit(true, tp, bw - 16, infoTitle(id));
		g.clip(x - 2, top - 2, wd + 4, h + 4);
		for (int i = 0; i < ids.size(); i++) {
			float by = top + (i / 2) * step - grid.scroll;
			if (by + bh < top - 4 || by > top + h + 4) continue;
			drawInfoBox(ids.get(i), x + (i % 2) * (bw + gap), by, bw, bh, tp);
		}
		g.unclip();
	}

	private String infoTitle(String id) {
		boolean en = T.en;
		if (id.equals("sun")) return T.sun;
		if (id.equals("moon")) return en ? "MOON" : "LUNA";
		if (id.equals("uv")) return T.uvT;
		if (id.equals("pollen")) return T.pollen;
		if (id.equals("hum")) return en ? "HUMIDITY" : "UMIDITATE";
		if (id.equals("aqi")) return en ? "AIR QUALITY" : "ICA";
		if (id.equals("press")) return en ? "PRESSURE" : "PRESIUNE";
		if (id.equals("wind")) return en ? "WIND" : "VÂNT";
		if (id.equals("vis")) return en ? "VISIBILITY" : "VIZIBILITATE";
		return en ? "COMPASS" : "BUSOLĂ";
	}

	private static String hm(long ms) {
		Calendar k = Calendar.getInstance();
		k.setTimeInMillis(ms);
		return String.format("%02d:%02d", k.get(Calendar.HOUR_OF_DAY), k.get(Calendar.MINUTE));
	}

	private void drawInfoBox(String id, float x, float top, float bw, float h, float tp) {
		float cx = x + bw / 2, hd = 46, body = h - hd;
		boolean en = T.en;
		box(x, top, bw, h, hd);
		head(top, hd, cx, bw - 16, infoTitle(id).equals("LUNA") ? "LUNĂ" : infoTitle(id), tp);
		float top0 = top;
		// The content is laid out for a box 184 high: in a taller one it sits in the middle.
		top += Math.max(0, (h - 184) / 2);
		if (id.equals("sun")) {
			ctext(false, 20, top + 54, cx, T.sunrise, c.ink);
			ctext(true, 29, top + 78, cx, Texts.clock(w.sunrise, clk12), c.ink);
			ctext(false, 20, top + 121, cx, T.sunset, c.ink);
			ctext(true, 29, top + 145, cx, Texts.clock(w.sunset, clk12), c.ink);
		} else if (id.equals("moon")) {
			long ms = now.getTimeInMillis();
			long[] rs = city != null ? Moon.riseSet(ms, city.lat, city.lon) : new long[2];
			float tx = x + (bw - 52) / 2 + 4;
			ctext(false, 20, top + 54, tx, en ? "Moonrise" : "Răsare", c.ink);
			ctext(true, 29, top + 78, tx, rs[0] > 0 ? Texts.clock(hm(rs[0]), clk12) : "–", c.ink);
			ctext(false, 20, top + 121, tx, en ? "Moonset" : "Apune", c.ink);
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
			bigValue(cx, top, w.humidity >= 0 ? w.humidity + "%" : "–", en ? "relative humidity" : "umiditate relativă", bw);
			scale(cx - 60, top + 162, 5, w.humidity < 0 ? 0 : Math.max(1, (w.humidity + 19) / 20), 21);
		} else if (id.equals("aqi")) {
			String[] lv = en ? new String[] { "Good", "Fair", "Moderate", "Poor", "Very poor", "Extremely poor" }
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
			String val = Double.isNaN(v) ? "–" : v >= 10000 ? Math.round(v / 1000) + " km" : v >= 1000 ? String.format("%.1f km", v / 1000).replace('.', en ? '.' : ',') : Math.round(v / 10) * 10 + " m";
			int l = Double.isNaN(v) ? 0 : v >= 10000 ? 4 : v >= 4000 ? 3 : v >= 1000 ? 2 : 1;
			String[] lv = en ? new String[] { "Poor (fog)", "Moderate", "Good", "Very good" } : new String[] { "Slabă (ceață)", "Moderată", "Bună", "Foarte bună" };
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
		String val = Long.toString(Math.round(p));
		ctext(true, fit(true, 34, r * 1.5f, val), cy - 26, cx, val, c.ink);
		ctext(false, 18, cy + 10, cx, "hPa", c.ink);
		double t = w.pressTrend;
		String tr = Double.isNaN(t) ? "" : Math.abs(t) < 1 ? (en ? "steady" : "stabilă") : t <= -3 ? (en ? "↓ falling fast" : "↓ scade rapid")
				: t < 0 ? (en ? "↓ falling" : "↓ scade") : t >= 3 ? (en ? "↑ rising fast" : "↑ crește rapid") : (en ? "↑ rising" : "↑ crește");
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
		String[] L = T.en ? new String[] { "N", "E", "S", "W" } : new String[] { "N", "E", "S", "V" };
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
			ctext(false, fit(false, 17, r * 1.1f, en ? "no compass" : "fără busolă"), cy - 10, cx, en ? "no compass" : "fără busolă", c.dim);
			return;
		}
		int deg = Math.round(((heading % 360) + 360) % 360) % 360;
		String dir = (en ? DIR_EN : DIR_RO)[(int) Math.round(deg / 45.0) % 8];
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

	private static final String[] DIR_RO = { "N", "NE", "E", "SE", "S", "SV", "V", "NV" }, DIR_EN = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };

	/**
	 * "WIND NE 9 KM/H" right-aligned at RIGHT, and before it a small compass: a ring with the north
	 * mark and an arrow the way the wind blows, both turned with the phone (north up without a compass).
	 */
	private void windLine(float px, float top, float right) {
		if (Double.isNaN(w.wind)) return;
		String speed = unitF ? Math.round(w.wind * 0.621371) + " mph" : Math.round(w.wind) + " km/h";
		boolean dir = !Double.isNaN(w.windDir);
		String from = dir ? (T.en ? DIR_EN : DIR_RO)[(int) Math.round(((w.windDir % 360) + 360) % 360 / 45) % 8] + " " : "";
		String txt = upper(T.wind + " " + from + speed);
		float x = rtext(false, px, top, right, txt, c.ink);
		if (!dir || Math.round(w.wind) == 0) return;
		float r = px * 0.62f, cx = x - r - 12, cy = top + px * 0.52f;
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
			String line = t(w.temp) + "°  " + d[1] + (city != null ? "  ·  " + city.label() : "");
			float lp = fit(false, 32, cw - 220, line), lw = g.width(false, lp, line), ic = lp * 1.6f;
			float lx = cw / 2 - (lw + ic + 14) / 2;
			g.icon("icons/" + d[0] + ".png", lx, y + 64 - ic * 0.25f, ic, ic, ink);
			ltext(false, lp, y + 64, lx + ic + 14, line, ink);
		}
		String bat = (batt >= 0 ? batt + "%  " : "") + T.charging;
		ctext(false, 26, ch - 70, cw / 2, bat, dimInk);
		ctext(false, 22, ch - 36, cw / 2, T.en ? "Tap to leave the night clock" : "Atinge ecranul ca să ieși din ceasul de noapte", dimInk);
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
		footButton("locate", btnLocate, false);
		footButton("bright", btnBright, brightBar);
		footButton("sensor", btnSensor, sensorTheme);
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
			int lc = locating ? c.accent : autoLoc ? ink : c.dim;
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
		else if (which.equals("refresh")) {
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
			String t = which.endsWith("ro") ? "RO" : "EN";
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
		}
	}

	/** A short notice over the status row after a button (MainActivity clears it after 1.5 s). */
	public String note = "";

	private void drawNote(float fb) {
		if (note.length() == 0) return;
		float p = fit(true, 30, cw - 160, note), w = g.width(true, p, note) + 56, h = 66, x = (cw - w) / 2, y = fb - h - 18;
		g.roundRect(x, y, w, h, 33, c.ink);
		g.roundRect(x + 2, y + 2, w - 4, h - 4, 31, c.page);
		ctext(true, p, y + (h - p * 1.12f) / 2, cw / 2, note, c.ink);
	}

	/** "updated 12:34" under the line, at the right. */
	private void drawUpdated() {
		String upd = "";
		boolean bold = false;
		if (busy) {
			upd = T.updating;
		} else if (fetched > 0) {
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(fetched);
			String when = Texts.clock(String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)), clk12);
			upd = T.updated + " " + when;
			if (offline) {
				upd = "! " + T.noData + " " + when;
				bold = true;
			}
		}
		if (upd.length() > 0) rtext(bold, fit(bold, 25, 400, upd), 110, cw - 40, upd, bold ? c.ink : c.dim);
	}
}
