package ro.meteoalarm.android.core;

import java.util.Calendar;
import java.util.List;

/**
 * The screens of Meteo Alarm Kindle, laid out for a phone. Portrait: the canvas is 758 units wide
 * like the Paperwhite's; the height follows the phone (at least BASE_H) and the extra room goes to
 * the current weather. From the top: clock, city and warnings, the temperature and icon, the
 * precipitation chart, tomorrow and the day after, three more days, sun / UV / pollen and the
 * status line. Landscape: the canvas is 758 units high and at least BASE_LW wide, in three
 * columns: the current weather (it takes the extra width), precipitation over sun / UV / pollen,
 * and the five days.
 */
public final class Screen {
	public static final float W = 758, BASE_H = 1538, LH = 758, BASE_LW = 1560;

	// Inputs.
	public Weather w;
	public List<Warnings.Warn> warns;
	public int pollenLvl = -1;
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

	// Buttons (x, y, w, h in canvas units), set by draw.
	public final float[] btnMenu = { 40, 24, 64, 64 }, btnRefresh = { 654, 24, 64, 64 }, btnCity = { 40, 0, 64, 64 },
			btnLocate = { 124, 0, 64, 64 },
			cityArea = { 40, 104, 380, 56 };

	/** Canvas width: 758 in portrait, wider in landscape. */
	private float cw = W;

	private Gfx g;
	private Palette c = Palette.LIGHT;

	/** The colours of a theme, by role. */
	static final class Palette {
		final int page, ink, dim, line, band, card, btn, dry, snow, rain, accent, warnY, warnO, warnR, pillInk;

		Palette(int... v) {
			page = v[0]; ink = v[1]; dim = v[2]; line = v[3]; band = v[4]; card = v[5]; btn = v[6]; dry = v[7];
			snow = v[8]; rain = v[9]; accent = v[10]; warnY = v[11]; warnO = v[12]; warnR = v[13]; pillInk = v[14];
		}

		// Kindle: black on white with three grays; orange and red codes are the darker gray.
		static final Palette LIGHT = new Palette(Gfx.WHITE, Gfx.BLACK, Gfx.GRAY5, Gfx.BLACK, Gfx.GRAYD, Gfx.WHITE, Gfx.WHITE,
				Gfx.GRAYA, Gfx.GRAY5, Gfx.BLACK, Gfx.BLACK, Gfx.GRAYD, Gfx.GRAYA, Gfx.GRAYA, Gfx.BLACK);
		// Claude dark: warm charcoal, ivory text, terracotta accent; the codes in muted real colours.
		static final Palette DARK = new Palette(0xFF262624, 0xFFFAF9F5, 0xFFA6A39A, 0xFF4A4944, 0xFF3A3935, 0xFF30302E,
				0xFF30302E, 0xFF4F4E4A, 0xFFC2C0B6, 0xFFD97757, 0xFFD97757, 0xFFE3B341, 0xFFD97757, 0xFFD14D4D, 0xFF262624);
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
		g.rect(0, 0, cw, ch, c.page);
		button(btnMenu);
		for (int k = 0; k < 3; k++) g.rect(57, 41 + k * 13, 30, 4, c.ink);
		float rx = cw - 104;
		btnRefresh[0] = rx;
		button(btnRefresh);
		g.arc(rx + 32, 56, 15, -50, 290, 4, c.ink);
		// Arrowhead at the arc's start (upper right), pointing along the circle.
		g.poly(new float[] { rx + 36, 33, rx + 51, 46, rx + 35, 52 }, c.ink);
		drawClock();
		rtext(false, 26, 71, cw - 117, T.date(now), c.ink);
		if (cw > ch) drawLandscape(ch);
		else drawPortrait(ch);
	}

	private void drawPortrait(float ch) {
		cityArea[0] = 40; cityArea[1] = 104; cityArea[2] = 380; cityArea[3] = 56;
		float fb = ch - 76;
		btnCity[1] = fb;
		btnLocate[1] = fb;
		if (w == null) {
			g.rect(40, 102, 678, 2, c.line);
			String msg = error.length() > 0 ? error : T.loading;
			float p = fit(true, 40, 640, msg);
			ctext(true, p, 300, W / 2, msg, c.ink);
			footer(fb);
			return;
		}

		String label = city.label();
		ltext(true, fit(true, 36, 340, label), 116, 40, label, c.ink);

		float nowBot = 498 + Math.max(0, ch - BASE_H);
		float fy = nowBot - 4;
		int shown = drawWarnings();
		g.rect(40, 102, hdrEnd - 40, 2, c.line);

		String[] d = T.describe(w.code, w.isDay);
		if (shown == 0) {
			drawNowSide(d[0], 40, 104, 718, fy + 4);
		} else {
			float base = nowBot - 21;
			drawTemp(40, base, 252, 405);
			float cy = base - tp * 32 / 100;
			icLeft = tempEnd + 2; icRight = 748; icTop = warnBot + 4; icFit = true; icBot = fy - 6;
			drawIconC(d[0], (tempEnd + 718) / 2, cy, 260, cy - warnBot - 10, 718 - tempEnd - 16);
			icTop = 0; icFit = false;
		}

		// Description and wind on the right, "feels like" and high / low on the left.
		rtext(true, fit(true, 39, 330, d[1]), fy, 712, d[1], c.ink);
		if (!Double.isNaN(w.wind)) {
			String wind = unitF ? T.wind + " " + Math.round(w.wind * 0.621371) + " mph" : T.wind + " " + Math.round(w.wind) + " km/h";
			rtext(false, 30, fy + 40, 712, upper(wind), c.ink);
		}
		if (!Double.isNaN(w.feels)) ltext(false, 30, fy + 7, 40, upper(T.feels + " " + t(w.feels) + "°"), c.ink);
		if (!Double.isNaN(w.tmax) && !Double.isNaN(w.tmin))
			ltext(false, 30, fy + 40, 40, upper(T.hi + " " + t(w.tmax) + "° / " + T.lo + " " + t(w.tmin) + "°"), c.ink);

		float y0 = fy + 80;
		drawNextHours(40, y0, 678, 300);
		drawDayCard(40, y0 + 314, 0, T.day1);
		drawDayCard(387, y0 + 314, 1, T.day2);
		drawDayCols(2, y0 + 526);
		drawSunBox(40, y0 + 690, 678, 184);
		footer(fb);
	}

	/**
	 * Landscape: the current weather on the left (it takes the extra width); on the right the next
	 * hours (or the precipitation chart) across the top and the five days under it; sun, UV and
	 * pollen as one line in the status row.
	 */
	private void drawLandscape(float ch) {
		float fb = ch - 76;
		btnCity[1] = fb;
		btnLocate[1] = fb;
		float rbw = 946, rbx = cw - 40 - rbw, leftR = rbx - 24;
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
		float nowTop = 104 + 12 + drawWarningsRow(40, 116, leftR - 40);
		float fy = 568;
		String[] d = T.describe(w.code, w.isDay);
		drawNowSide(d[0], 40, nowTop - 60, leftR, fy + 4);
		rtext(true, fit(true, 39, (leftR - 40) / 2, d[1]), fy, leftR - 6, d[1], c.ink);
		if (!Double.isNaN(w.wind)) {
			String wind = unitF ? T.wind + " " + Math.round(w.wind * 0.621371) + " mph" : T.wind + " " + Math.round(w.wind) + " km/h";
			rtext(false, 30, fy + 40, leftR - 6, upper(wind), c.ink);
		}
		if (!Double.isNaN(w.feels)) ltext(false, 30, fy + 7, 40, upper(T.feels + " " + t(w.feels) + "°"), c.ink);
		if (!Double.isNaN(w.tmax) && !Double.isNaN(w.tmin))
			ltext(false, 30, fy + 40, 40, upper(T.hi + " " + t(w.tmax) + "° / " + T.lo + " " + t(w.tmin) + "°"), c.ink);

		drawNextHours(rbx, 116, rbw, 260);
		float dw = (rbw - 4 * 12) / 5;
		for (int k = 0; k < 5; k++) {
			float x = rbx + k * (dw + 12), cx = x + dw / 2, top = 390;
			box(x, top, dw, 270, 44);
			if (k >= w.dayDates.size()) continue;
			String title = k == 0 ? T.day1 : k == 1 ? T.day2 : T.weekdayUp(w.dayDates.get(k), true);
			head(top, 44, cx, dw - 20, title, 0);
			String[] dd = T.describe(w.dayCodes.get(k), true);
			drawIcon(dd[0], cx - 45, top + 52, 90);
			String hl = t(w.dayHi.get(k)) + "° / " + t(w.dayLo.get(k)) + "°";
			float p = fit(true, 38, dw - 16, hl);
			ctext(true, p, top + 150 + (38 - p) * 6 / 10, cx, hl, c.ink);
			ctext(false, fit(false, 25, dw - 14, dd[1]), top + 198, cx, dd[1], c.ink);
			ctext(false, 22, top + 232, cx, T.pop + " " + w.dayPop.get(k) + "%", c.dim);
		}
		footer(fb);
		// Sun, UV and pollen in the middle of the status row.
		StringBuilder sb = new StringBuilder();
		sb.append(T.sunrise).append(' ').append(Texts.clock(w.sunrise, clk12)).append("   ")
				.append(T.sunset).append(' ').append(Texts.clock(w.sunset, clk12));
		if (w.uv >= 0) {
			int l = w.uv <= 2 ? 1 : w.uv <= 5 ? 2 : w.uv <= 7 ? 3 : w.uv <= 10 ? 4 : 5;
			sb.append("   UV ").append(w.uv).append(" (").append(T.uvLv[l - 1].toLowerCase()).append(')');
		}
		if (pollenLvl > 0) {
			sb.append("   ").append(T.pollen.charAt(0)).append(T.pollen.substring(1).toLowerCase()).append(": ")
					.append(T.polLv[pollenLvl - 1].toLowerCase());
			if (pollenPlant.length() > 0) sb.append(" (").append(T.plant(pollenPlant)).append(')');
		}
		String sun = sb.toString();
		float sp = fit(false, 27, cw - 520 - 260, sun);
		ctext(false, sp, fb + 26 + (27 - sp) / 2, (520 + cw - 260) / 2, sun, c.ink);
	}

	/** Precipitation chart when rain or snow is due within 12 hours, else the next hours as tiles. */
	private void drawNextHours(float x, float y, float wd, float h) {
		if (rainText().length() > 0 || w.tileHours.size() < 2) drawPrecipBox(x, y, wd, h, 46);
		else drawHourTiles(x, y, wd, h, 46);
	}

	/**
	 * The next hours in two rows of tiles, as many columns as fit (at least 125 units each): the
	 * hour on top, the temperature on the left and the weather icon on the right.
	 */
	private void drawHourTiles(float pl, float pt, float pw, float ph, float phd) {
		box(pl, pt, pw, ph, phd);
		head(pt, phd, pl + pw / 2, pw - 40, T.hoursUp, 0);
		float ix = pl + 14, iw = pw - 28, top = pt + phd + 6, bot = pt + ph - 8;
		int cols = Math.max(1, (int) (iw / 125)), rows = 2;
		int avail = w.tileHours.size() - 1;
		if (avail < cols * rows) rows = avail > cols ? 2 : 1;
		float tw = iw / cols, th = (bot - top) / rows, ic = Math.min(th - 40, tw * 0.46f);
		// One temperature size for all tiles: the largest at which every one fits.
		float p = Math.min(48, th * 0.5f);
		for (int k = 1; k <= Math.min(avail, rows * cols); k++)
			if (!Double.isNaN(w.tileTemp[k])) p = fit(true, p, tw - ic - 22, t(w.tileTemp[k]) + "°");
		for (int r = 0; r < rows; r++) {
			if (r > 0) g.rect(ix + 6, top + r * th - 1, iw - 12, 2, c.line);
			for (int col = 0; col < cols; col++) {
				int k = 1 + r * cols + col;
				if (k > avail) break;
				float tx = ix + col * tw, ty = top + r * th;
				if (col > 0) g.rect(tx - 1, ty + 10, 2, th - 20, c.line);
				String hr = Texts.clock(w.tileHours.get(k), clk12);
				ctext(false, fit(false, 23, tw - 12, hr), ty + 6, tx + tw / 2, hr, c.dim);
				float cy = ty + 32 + (th - 32) / 2;
				drawIcon(Weather.iconOf(w.tileCode[k], w.tileDay[k]), tx + tw - ic - 8, cy - ic / 2, ic);
				if (Double.isNaN(w.tileTemp[k])) continue;
				String tt = t(w.tileTemp[k]) + "°";
				ltext(true, p, cy - p * 0.5f, tx + 10, tt, c.ink);
			}
		}
	}

	/**
	 * Landscape warnings: up to two side by side at the top of the weather column, each a code
	 * pill with the type and the period under it. Returns the height used (0 without warnings).
	 */
	private float drawWarningsRow(float x0, float y, float wd) {
		if (warns == null) return 0;
		long nowMs = now.getTimeInMillis();
		float colW = (wd - 20) / 2, x = x0;
		int shown = 0;
		for (Warnings.Warn wr : warns) {
			if (wr.until <= nowMs || shown >= 2) continue;
			String name = T.warn[Math.min(3, Math.max(1, wr.level)) - 1];
			String lab = T.en ? name + " " + T.warnCode : T.warnCode + " " + name;
			String txt = T.warnType(wr.type);
			String per = T.weekdayTime(wr.from, clk12) + " – " + T.weekdayTime(wr.until, clk12);
			float lp = fit(true, 39, colW - 70, lab);
			float bw = g.width(true, lp, lab) + 14;
			int fill = wr.level >= 3 ? c.warnR : wr.level == 2 ? c.warnO : c.warnY;
			g.roundRect(x, y, bw, 42, 12, c.line);
			g.roundRect(x + 2, y + 2, bw - 4, 38, 10, fill);
			ltext(true, lp, y + (42 - lp) / 2, x + 7, lab, c.pillInk);
			if (wr.level >= 3) g.icon("ui/atentie.png", x + bw + 10, y, 46, 42, dark ? c.warnR : c.ink);
			ltext(false, fit(false, 31, colW, txt), y + 50, x + 3, txt, c.ink);
			ltext(false, fit(false, 30, colW, per), y + 79, x + 3, per, c.dim);
			x += colW + 20;
			shown++;
		}
		return shown > 0 ? 116 : 0;
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

	/** Box title in a gray header band: capitals at about 3/4 of the band, drawn twice 1 unit apart. */
	private void head(float top, float h, float cx, float maxw, String s, float px) {
		float p = fit(true, px > 0 ? px : (h - 4) * 76 / 100, maxw - 1, s);
		float x = cx - g.width(true, p, s) / 2, y = top + h / 2 - p * 485 / 1000;
		g.text(true, p, y, x, s, c.ink);
		g.text(true, p, y, x + 1, s, c.ink);
	}

	/** Frame with rounded corners, a 2-unit outline and a gray header band HD high. */
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
		float right = cw - 123;
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

	private float icLeft = 0, icRight = 758, icTop = 0, icBot = 0, icPct = 100;
	private boolean icFit;

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
		if (icFit && cy - s * (y0 + y1) / 400 < icTop && icTop + s * y1 / 200 > icBot) s = (icBot - icTop) * 200 / y1;
		float x = cx - s * (x0 + x1) / 400;
		// Keep the drawing itself (not its file's square) between IC_LEFT and IC_RIGHT.
		if (x + s * x0 / 200 < icLeft) x = icLeft - s * x0 / 200;
		if (x + s * x1 / 200 > icRight) x = icRight - s * x1 / 200;
		float y = cy - s * (y0 + y1) / 400;
		if (y < icTop) y = icTop;
		drawIcon(icon, x, y, s);
	}

	// Measured temperature: size, minus, digits, unit.
	private float tp, tMw, tWn, tGap, tWu, tU, tW, tempEnd;
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

	private void drawTemp(float left, float base, float px, float maxw) {
		tempSize(px, maxw);
		drawTempAt(left, base);
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
		tempEnd = x + tWu;
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

	private float hdrEnd = 718, warnBot = 102;

	/** Up to two warnings right-aligned under the clock; returns how many were drawn. */
	private int drawWarnings() {
		hdrEnd = 718;
		warnBot = 102;
		if (warns == null) return 0;
		float wy = 102;
		int shown = 0;
		long nowMs = now.getTimeInMillis();
		for (Warnings.Warn wr : warns) {
			if (wr.until <= nowMs || shown >= 2) continue;
			String name = T.warn[Math.min(3, Math.max(1, wr.level)) - 1];
			String lab = T.en ? name + " " + T.warnCode : T.warnCode + " " + name;
			String txt = T.warnType(wr.type);
			String per = T.weekdayTime(wr.from, clk12) + " – " + T.weekdayTime(wr.until, clk12);
			float lp = fit(true, 39, 330, lab);
			float bw = g.width(true, lp, lab) + 14;
			float txp = fit(false, 31, 330, txt);
			int fill = wr.level >= 3 ? c.warnR : wr.level == 2 ? c.warnO : c.warnY;
			float sx = 718 - bw;
			if (wr.level >= 3) {
				sx -= 56;
				g.icon("ui/atentie.png", sx, wy, 46, 42, dark ? c.warnR : c.ink);
			}
			if (shown == 0) hdrEnd = sx - 6;
			g.roundRect(718 - bw, wy, bw, 42, 12, c.line);
			g.roundRect(718 - bw + 2, wy + 2, bw - 4, 38, 10, fill);
			ltext(true, lp, wy + (42 - lp) / 2, 718 - bw + 7, lab, c.pillInk);
			rtext(false, txp, wy + 50, 716, txt, c.ink);
			rtext(false, 30, wy + 79, 712, per, c.dim);
			wy += 116;
			warnBot = wy - 6;
			shown++;
		}
		return shown;
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

	// --- Status line -----------------------------------------------------------------------

	private void footer(float fb) {
		button(btnCity);
		g.icon("icons/btn-city.png", 56, fb + 16, 32, 32, c.ink);
		// Locate: a crosshair.
		btnLocate[1] = fb;
		button(btnLocate);
		int lc = locating ? c.accent : c.ink;
		float lx = 156, ly = fb + 32;
		g.arc(lx, ly, 13, 0, 360, 3, lc);
		g.arc(lx, ly, 2.5f, 0, 360, 5, lc);
		g.rect(lx - 1.5f, ly - 22, 3, 8, lc);
		g.rect(lx - 1.5f, ly + 14, 3, 8, lc);
		g.rect(lx - 22, ly - 1.5f, 8, 3, lc);
		g.rect(lx + 14, ly - 1.5f, 8, 3, lc);
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
		if (upd.length() > 0) {
			float maxw = (cw > W ? 520 : batLeft) - 207 - 14;
			float up = fit(bold, 27, maxw, upd);
			ltext(bold, up, fb + 26, 207, upd, bold ? c.ink : c.dim);
		}
	}
}
