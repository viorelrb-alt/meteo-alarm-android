package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ro.meteoalarm.android.core.Agro;
import ro.meteoalarm.android.core.Farm;
import ro.meteoalarm.android.core.Tr;

/**
 * v2.001 (Viorel's strategy file: "a discreet button for exporting the summary as PDF or CSV,
 * for the record of the treatments"): the Statistics of the parcel on show as an A4 report or a
 * table (the next 72 hours and the days), from the same numbers as the screen.
 */
final class StatsExport {
	private final Context ctx;
	private final StatsPage s;

	StatsExport(Context ctx, StatsPage s, boolean dark) {
		this.ctx = ctx;
		this.s = s;
	}

	/** The summary: { section title, then label / value pairs }. */
	private List<String[]> summary() {
		List<String[]> out = new ArrayList<String[]>();
		Farm.Data d = s.d;
		int cur = s.cur;
		Object[] ac = s.action();
		out.add(new String[] { Tr.t("Action of the day", "Acțiunea zilei") });
		out.add(new String[] { (String) ac[0], ac[2] + "/100" });
		if (((String) ac[1]).length() > 0) out.add(new String[] { (String) ac[1], "" });
		if (((String) ac[5]).length() > 0) out.add(new String[] { (String) ac[5], "" });
		if (((String) ac[4]).length() > 0) out.add(new String[] { (String) ac[4], "" });
		if (cur < 0) return out;
		out.add(new String[] { Tr.t("Operational", "Operațional") });
		double dt = Agro.deltaT(d, cur);
		out.add(new String[] { Tr.t("Drop drying (ΔT) now", "Uscare picături (ΔT) acum"), s.degDelta(dt) });
		out.add(new String[] { Tr.t("Wind / gusts now", "Vânt / rafale acum"), s.kmh(d.wind[cur]) + " / " + s.kmh(d.gust[cur]) });
		int[] w = Agro.wetNow(d, cur);
		out.add(new String[] { Tr.t("Wet leaf now", "Frunză umedă acum"), w[0] + " h" });
		int good = 0;
		for (int k = cur; k < Math.min(d.size(), cur + 72); k++) if (Agro.level(Agro.score(d, k)) == 0) good++;
		out.add(new String[] { Tr.t("Good spray hours in 72 h", "Ore bune de stropit în 72 h"), String.valueOf(good) });

		out.add(new String[] { Tr.t("Water & Soil", "Apă & Sol") });
		int t = d.dayIndex(s.today);
		double r7 = t >= 0 ? s.sum(d.dRain, t - 6, t) : Double.NaN, r28 = t >= 0 ? s.sum(d.dRain, t - 27, t) : Double.NaN, e7 = 0;
		if (t >= 0) for (int k = Math.max(0, t - 6); k <= t; k++) e7 += nz(Agro.et0(d, k, s.p.lat));
		out.add(new String[] { Tr.t("Rain 7 days / 28 days", "Ploi 7 zile / 28 zile"), s.mm(r7) + " / " + s.mm(r28) });
		out.add(new String[] { Tr.t("Evaporation (ET₀) 7 days", "Evaporare (ET₀) 7 zile"), s.mm(e7) });
		out.add(new String[] { Tr.t("Balance 7 days", "Bilanț 7 zile"), (r7 - e7 >= 0 ? "+" : "−") + s.mm(Math.abs(r7 - e7)) });
		out.add(new String[] { Tr.t("Soil temperature 6 / 18 cm", "Temperatura solului 6 / 18 cm"), s.degU(d.soilT6[cur]) + " / " + s.degU(d.soilT18[cur]) });
		out.add(new String[] { Tr.t("Surface moisture 0–1 cm", "Umiditate suprafață 0–1 cm"), Double.isNaN(d.soilM1[cur]) ? "–" : Math.round(d.soilM1[cur] * 100) + " %" });

		if (s.crop != null && s.p.sow.length() == 10) {
			out.add(new String[] { Tr.t("Season", "Sezon") });
			List<double[]> now = s.days.cum(s.seasonStart, s.today, s.crop.base, s.crop.cap);
			List<double[]> last = s.days.cum(Agro.plus(s.seasonStart, -365), Agro.plus(s.today, -365), s.crop.base, s.crop.cap);
			double gs = now.isEmpty() ? Double.NaN : now.get(now.size() - 1)[1], gl = last.isEmpty() ? Double.NaN : last.get(last.size() - 1)[1];
			out.add(new String[] { Tr.t("GDD since ", "GDD de la ") + s.dayMonth(s.seasonStart), Double.isNaN(gs) ? "–" : Math.round(gs) + " °C·" + Tr.t("days", "zile") });
			out.add(new String[] { Tr.t("Last year on the same date", "Anul trecut la aceeași dată"), Double.isNaN(gl) ? "–" : String.valueOf(Math.round(gl)) });
			int st = Agro.stage(s.crop.type, Double.isNaN(gs) ? 0 : gs);
			out.add(new String[] { Tr.t("Estimated stage", "Stadiul estimat"), st < 0 ? "–" : Agro.stageNames(s.crop.type)[st] });
		}

		out.add(new String[] { Tr.t("Risks 72 h", "Riscuri 72 h") });
		Object[][] rk = s.risks();
		for (int k = 0; k < 4; k++)
			out.add(new String[] { Tr.t(StatsPage.RISK_EN[k], StatsPage.RISK_RO[k]),
					Math.max(0, (Integer) rk[k][0]) + "% · " + rk[k][1] + (((String) rk[k][2]).length() > 0 ? " · " + rk[k][2] : "") });
		List<Object[]> ev = s.events();
		if (!ev.isEmpty()) {
			out.add(new String[] { Tr.t("Coming up", "Urmează") });
			for (Object[] e : ev) out.add(new String[] { (String) e[2] + " · " + e[3], (String) e[4] });
		}
		return out;
	}

	private String[] hourHead() {
		return new String[] { Tr.t("Hour", "Ora"), Tr.t("Temp.", "Temp."), Tr.t("RH", "UR"), Tr.t("Wind", "Vânt"), Tr.t("Gusts", "Rafale"), "ΔT", Tr.t("Score", "Scor"),
				Tr.t("Rain", "Ploaie"), "mm", Tr.t("Wet leaf", "Frunză udă") };
	}

	private List<String[]> hours() {
		List<String[]> out = new ArrayList<String[]>();
		Farm.Data d = s.d;
		if (s.cur < 0) return out;
		for (int k = s.cur; k < Math.min(d.size(), s.cur + 72); k++)
			out.add(new String[] { s.dayMonth(d.dateOf(k)) + " " + s.clock(k), s.deg(d.temp[k]), Math.round(nz(d.hum[k])) + "%", s.kmh(d.wind[k]), s.kmh(d.gust[k]),
					s.f1(Agro.deltaT(d, k)), String.valueOf(Math.max(0, Agro.score(d, k))), Math.round(nz(d.prob[k])) + "%", s.f1(nz(d.precip[k])),
					Agro.wet(d, k) ? Tr.t("yes", "da") : "–" });
		return out;
	}

	private String[] dayHead() {
		return new String[] { Tr.t("Date", "Data"), "Max", "Min", Tr.t("Rain", "Ploaie"), "ET₀", "GDD" };
	}

	private List<String[]> dayRows() {
		List<String[]> out = new ArrayList<String[]>();
		Farm.Data d = s.d;
		double base = s.crop != null ? s.crop.base : 10, cap = s.crop != null ? s.crop.cap : 30;
		for (int k = 0; k < d.dDate.length; k++) {
			String date = d.dDate[k];
			if (date == null || date.compareTo(Agro.plus(s.today, 7)) > 0) continue;
			out.add(new String[] { s.dayMonth(date) + (date.compareTo(s.today) > 0 ? " *" : ""), s.deg(d.dMax[k]), s.deg(d.dMin[k]), s.mm(d.dRain[k]), s.f1(Agro.et0(d, k, s.p.lat)),
					s.f1(Agro.gdd(d.dMax[k], d.dMin[k], base, cap)) });
		}
		return out;
	}

	private String title() {
		return Tr.t("Statistics · ", "Statistici · ") + s.p.name + (s.crop != null ? " · " + s.crop.name() : "");
	}

	private String info() {
		StringBuilder b = new StringBuilder();
		if (s.p.sow.length() == 10) b.append(Tr.t("sown ", "semănat ")).append(s.longDate(s.p.sow)).append(" · ");
		double ha = s.p.hectares();
		if (!Double.isNaN(ha)) b.append(s.hectares(ha)).append(" · ");
		b.append(String.format(Locale.US, "%.5f, %.5f", s.p.lat, s.p.lon));
		return b.toString();
	}

	private String made() {
		Calendar c = Calendar.getInstance();
		return Tr.t("Made on ", "Făcut pe ") + s.longDate(Farm.date(c, 0)) + String.format(Locale.US, " %02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
				+ Tr.t(" · source Open-Meteo (models; the soil and the risks are estimates) · Meteo Alarm Android", " · sursa Open-Meteo (modele; solul și riscurile sunt estimări) · Meteo Alarm Android");
	}

	// --- CSV ------------------------------------------------------------------------------------

	String csv() {
		StringBuilder b = new StringBuilder("﻿");
		line(b, new String[] { title() });
		line(b, new String[] { info() });
		line(b, new String[] { made() });
		b.append("\r\n");
		for (String[] r : summary()) line(b, r);
		b.append("\r\n");
		line(b, new String[] { Tr.t("Next 72 hours", "Următoarele 72 de ore") });
		line(b, hourHead());
		for (String[] r : hours()) line(b, r);
		b.append("\r\n");
		line(b, new String[] { Tr.t("Days (* = forecast)", "Zile (* = prognoză)") });
		line(b, dayHead());
		for (String[] r : dayRows()) line(b, r);
		return b.toString();
	}

	private static void line(StringBuilder b, String[] r) {
		for (int k = 0; k < r.length; k++) {
			String v = r[k] == null ? "" : r[k];
			if (k > 0) b.append(';');
			if (v.indexOf(';') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0) v = "\"" + v.replace("\"", "\"\"") + "\"";
			b.append(v);
		}
		b.append("\r\n");
	}

	// --- PDF ------------------------------------------------------------------------------------

	private PdfDocument doc;
	private PdfDocument.Page page;
	private Canvas c;
	private int pageNo;
	private float y;
	private static final int W = 595, H = 842, M = 40;
	private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG), b = new Paint(Paint.ANTI_ALIAS_FLAG), l = new Paint(Paint.ANTI_ALIAS_FLAG);

	private void newPage() {
		if (page != null) doc.finishPage(page);
		page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, ++pageNo).create());
		c = page.getCanvas();
		y = M;
		t.setTextSize(7.5f);
		t.setColor(0xFF666666);
		c.drawText(title() + " · " + pageNo, M, H - 20, t);
		t.setColor(0xFF000000);
	}

	private void room(float h) {
		if (y + h > H - M) newPage();
	}

	/** TEXT wrapped in WIDTH, with PAINT; returns the lines. */
	private static List<String> wrap(String text, Paint p, float width) {
		List<String> out = new ArrayList<String>();
		for (String para : text.split("\n")) {
			String[] w = para.split(" ");
			StringBuilder line = new StringBuilder();
			for (String x : w) {
				String tryL = line.length() == 0 ? x : line + " " + x;
				if (p.measureText(tryL) > width && line.length() > 0) {
					out.add(line.toString());
					line = new StringBuilder(x);
				} else line = new StringBuilder(tryL);
			}
			out.add(line.toString());
		}
		return out;
	}

	private void para(String s, Paint p, float size, float width, float x) {
		p.setTextSize(size);
		for (String ln : wrap(s, p, width)) {
			room(size * 1.35f);
			c.drawText(ln, x, y + size, p);
			y += size * 1.35f;
		}
	}

	byte[] pdf() throws Exception {
		doc = new PdfDocument();
		pageNo = 0;
		page = null;
		t.setColor(0xFF000000);
		b.setColor(0xFF000000);
		b.setTypeface(Typeface.DEFAULT_BOLD);
		l.setColor(0xFF999999);
		l.setStrokeWidth(0.6f);
		newPage();
		para(title(), b, 17, W - 2 * M, M);
		y += 2;
		para(info(), t, 9.5f, W - 2 * M, M);
		para(made(), t, 8, W - 2 * M, M);
		y += 8;
		float kw = 250;
		for (String[] r : summary()) {
			if (r.length == 1) {
				y += 6;
				room(30);
				para(r[0].toUpperCase(Locale.getDefault()), b, 10.5f, W - 2 * M, M);
				c.drawLine(M, y, W - M, y, l);
				y += 4;
				continue;
			}
			float y0 = y;
			t.setTextSize(9.5f);
			List<String> a = wrap(r[0], t, r[1].length() == 0 ? W - 2 * M : kw - 8);
			b.setTextSize(9.5f);
			List<String> v = wrap(r[1], b, W - 2 * M - kw);
			int n = Math.max(a.size(), v.size());
			room(n * 13);
			y0 = y;
			for (int k = 0; k < a.size(); k++) c.drawText(a.get(k), M, y0 + 10 + k * 13, t);
			for (int k = 0; k < v.size(); k++) c.drawText(v.get(k), M + kw, y0 + 10 + k * 13, b);
			y = y0 + n * 13 + 2;
		}
		y += 10;
		table(Tr.t("Next 72 hours", "Următoarele 72 de ore"), hourHead(), hours());
		y += 10;
		table(Tr.t("Days (* = forecast)", "Zile (* = prognoză)"), dayHead(), dayRows());
		doc.finishPage(page);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		doc.writeTo(out);
		doc.close();
		return out.toByteArray();
	}

	private void table(String title, String[] head, List<String[]> rows) {
		room(50);
		para(title.toUpperCase(Locale.getDefault()), b, 10.5f, W - 2 * M, M);
		y += 2;
		float size = 8;
		t.setTextSize(size);
		b.setTextSize(size);
		float[] w = new float[head.length];
		for (int k = 0; k < head.length; k++) w[k] = b.measureText(head[k]);
		for (String[] r : rows) for (int k = 0; k < r.length && k < w.length; k++) w[k] = Math.max(w[k], t.measureText(r[k]));
		float total = 0;
		for (float x : w) total += x;
		float gap = Math.max(6, (W - 2 * M - total) / Math.max(1, head.length - 1));
		if (total + gap * (head.length - 1) > W - 2 * M) gap = 4;
		head(head, w, gap, size);
		for (String[] r : rows) {
			if (y + size * 1.6f > H - M) {
				newPage();
				head(head, w, gap, size);
			}
			float x = M;
			for (int k = 0; k < r.length && k < w.length; k++) {
				c.drawText(r[k], x, y + size, t);
				x += w[k] + gap;
			}
			y += size * 1.6f;
		}
	}

	private void head(String[] head, float[] w, float gap, float size) {
		float x = M;
		for (int k = 0; k < head.length; k++) {
			c.drawText(head[k], x, y + size, b);
			x += w[k] + gap;
		}
		y += size * 1.5f;
		c.drawLine(M, y, W - M, y, l);
		y += 3;
	}

	private static double nz(double v) {
		return Double.isNaN(v) ? 0 : v;
	}
}
