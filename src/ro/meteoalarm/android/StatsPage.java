package ro.meteoalarm.android;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ro.meteoalarm.android.core.Agro;
import ro.meteoalarm.android.core.Farm;
import ro.meteoalarm.android.core.Texts;
import ro.meteoalarm.android.core.Theme;
import ro.meteoalarm.android.core.Tr;
import ro.meteoalarm.android.core.Weather;
import ro.meteoalarm.android.core.WxIcon;

/**
 * v2.001 (Viorel 2026-11-15/17): a parcel's Statistics, as in the mockups: the header with the
 * parcel picker and 4 tabs (Operational, Water & Soil, Season, Risks), the "action of the day"
 * card on each, and PDF · CSV. Everything is worked out on the phone from the parcel's
 * Open-Meteo answer (and, for the season, from Open-Meteo's archive).
 */
final class StatsPage {
	interface Host {
		void back();

		void edit(Parcels.Parcel p);

		void open(long id);

		void export(Parcels.Parcel p, boolean pdf);

		void redraw();
	}

	private final Activity a;
	private final Host host;
	private final boolean dark, imp, clk12;
	private final float dp, fs;
	/** v2.010: the texts' factor for the page's width (412 dp = 1; smaller phones a little less). */
	private float wk = 1;
	private final int ink, dim, page, accent, G, AMB, R, B, O;

	/** What is shown: the tab (0–3), the spray band's hours (24 / 72), the chart's days back, the hour picked. */
	int tab, hours = 24, span = 7, sel = -1;

	// The data of the page being shown.
	Parcels.Parcel p;
	Farm.Data d;
	Agro.Days days;
	Agro.Crop crop;
	int cur = -1;
	String today, seasonStart = "";
	boolean seasonMissing;
	private FrameLayout root;

	StatsPage(Activity a, Host host, boolean dark, boolean imp, boolean clk12) {
		this.a = a;
		this.host = host;
		this.dark = dark;
		this.imp = imp;
		this.clk12 = clk12;
		dp = a.getResources().getDisplayMetrics().density;
		fs = TextSize.get(a);
		ink = Theme.ink(dark);
		dim = Theme.dim(dark);
		page = Theme.page(dark);
		accent = Theme.accent(dark);
		G = dark ? 0xFF22C55E : 0xFF16A34A;
		AMB = dark ? 0xFFF59E0B : 0xFFD97706;
		R = dark ? 0xFFEF4444 : 0xFFDC2626;
		B = dark ? 0xFF38BDF8 : 0xFF0284C7;
		O = dark ? 0xFFFB923C : 0xFFEA580C;
	}

	// --- The data ---------------------------------------------------------------------------------

	/** Takes parcel P with its answer D and its season's archive SEASON (null when none yet). */
	void set(Parcels.Parcel p, Farm.Data d, String season) {
		if (this.p == null || this.p.id != p.id) sel = -1;
		this.p = p;
		this.d = d;
		crop = p.crop();
		Calendar now = Calendar.getInstance();
		today = Farm.date(now, 0);
		cur = d == null ? -1 : d.nowIndex(now);
		days = new Agro.Days();
		if (season != null) try {
			Agro.archive(season, days);
		} catch (Exception e) {
			// a broken answer: the forecast's days only
		}
		if (d != null) for (int k = 0; k < d.dDate.length; k++) days.put(d.dDate[k], d.dMax[k], d.dMin[k], d.dRain[k]);
		seasonStart = Parcels.seasonStart(p, today);
		seasonMissing = season == null && seasonStart.length() > 0;
	}

	// --- Building blocks ------------------------------------------------------------------------

	private void widthK() {
		android.content.res.Configuration c = a.getResources().getConfiguration();
		float w = Math.min(c.screenWidthDp, TextSize.tablet(a) ? 720 : 600);
		wk = Math.max(0.82f, Math.min(1f, w / 412f));
	}

	private int px(float v) {
		return Math.round(v * dp);
	}

	/** v2.010: a text that never breaks a word (its size goes down instead), sized for the page's width. */
	private FitText tv(String s, float sp, boolean bold, int color) {
		FitText t = new FitText(a, 1000, 0.7f);
		t.setText(s);
		t.setTextSize(sp * fs * wk);
		t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
		t.setTextColor(color);
		t.setIncludeFontPadding(false);
		return t;
	}

	private LinearLayout col() {
		LinearLayout l = new LinearLayout(a);
		l.setOrientation(LinearLayout.VERTICAL);
		return l;
	}

	private LinearLayout row() {
		LinearLayout l = new LinearLayout(a);
		l.setOrientation(LinearLayout.HORIZONTAL);
		l.setGravity(Gravity.CENTER_VERTICAL);
		return l;
	}

	private LinearLayout card() {
		LinearLayout c = col();
		c.setBackground(Glass.cardBg(dark, px(16), dp));
		c.setPadding(px(14), px(12), px(14), px(12));
		return c;
	}

	private LinearLayout.LayoutParams wide(int top) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.topMargin = px(top);
		return lp;
	}

	private LinearLayout.LayoutParams weight(int left) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
		lp.leftMargin = px(left);
		return lp;
	}

	/** A small title in capitals. */
	private TextView caps(String s, int color) {
		FitText t = tv(s.toUpperCase(locale()), 12.5f, true, color);
		t.setLetterSpacing(0.06f);
		t.one(0.6f);
		return t;
	}

	/** A section's title, a thin line and RIGHT at its end. */
	private View section(String title, View right) {
		LinearLayout r = row();
		r.setPadding(0, px(18), 0, px(8));
		// v2.010: with something at the end, the title takes the room left by it (smaller if need be).
		if (right != null) {
			LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
			tl.rightMargin = px(10);
			r.addView(caps(title, dim), tl);
			r.addView(right);
			return r;
		}
		r.addView(caps(title, dim));
		View line = new View(a);
		line.setBackgroundColor(Glass.edge(dark));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.max(1, px(1)), 1);
		lp.leftMargin = px(10);
		r.addView(line, lp);
		return r;
	}

	private TextView hint(String s) {
		FitText t = tv(s, 12.5f, false, dim);
		t.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
		return t;
	}

	/** A small segmented choice: LABELS, ON picked, PICK called with the index. */
	private View seg(String[] labels, int on, final Pick pick) {
		LinearLayout r = row();
		r.setBackground(Glass.pill(Glass.disc(dark), 0, px(14), dp));
		r.setPadding(px(3), px(3), px(3), px(3));
		for (int k = 0; k < labels.length; k++) {
			final int i = k;
			FitText t = tv(labels[k], 12.5f, true, k == on ? ink : dim);
			t.setPadding(px(9), px(5), px(9), px(5));
			if (k == on) t.setBackground(Glass.pill(Glass.alpha(page, 0.9f), 0, px(11), dp));
			t.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					pick.at(i);
				}
			});
			r.addView(t);
		}
		return r;
	}

	interface Pick {
		void at(int k);
	}

	private TextView badge(String s, int color) {
		FitText t = tv(s, 12, true, color);
		t.setPadding(px(8), px(3), px(8), px(3));
		t.setBackground(Glass.pill(Glass.alpha(color, 0.18f), 0, px(10), dp));
		return t;
	}

	private int levelColor(int lv) {
		return lv == 0 ? G : lv == 1 ? AMB : lv == 2 ? R : dim;
	}

	private int pctColor(int pct) {
		return pct < 0 ? dim : pct < 34 ? G : pct < 67 ? AMB : R;
	}

	// --- Texts -------------------------------------------------------------------------------------

	Locale locale() {
		String l = Tr.lang();
		return new Locale(l.equals("us") ? "en" : l);
	}

	String f1(double v) {
		return Double.isNaN(v) ? "–" : String.format(Locale.US, "%.1f", v).replace('.', Tr.dot());
	}

	String deg(double c) {
		return Double.isNaN(c) ? "–" : Math.round(imp ? c * 9 / 5 + 32 : c) + "°";
	}

	String degU(double c) {
		return Double.isNaN(c) ? "–" : imp ? Math.round(c * 9 / 5 + 32) + " °F" : f1(c) + " °C";
	}

	String kmh(double v) {
		return Double.isNaN(v) ? "–" : imp ? Math.round(v * 0.621371) + " mph" : Math.round(v) + " km/h";
	}

	String mm(double v) {
		if (Double.isNaN(v)) return "–";
		return imp ? String.format(Locale.US, "%.2f in", v / 25.4).replace('.', Tr.dot()) : f1(v) + " mm";
	}

	private String signed(String s, double v) {
		return v > 0.05 ? "+" + s : v < -0.05 ? "−" + s.replace("-", "") : s;
	}

	String clock(int k) {
		return Texts.clock(d.hourOf(k), clk12);
	}

	/** "Mon 06:00" of hour K (today: only the hour). */
	String dayHour(int k) {
		if (!d.has(k)) return "";
		String date = d.dateOf(k);
		return (date.equals(today) ? "" : weekday(date) + " ") + clock(k);
	}

	String weekday(String iso) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(Agro.millis(iso));
		String w = new java.text.DateFormatSymbols(locale()).getShortWeekdays()[c.get(Calendar.DAY_OF_WEEK)];
		w = w.replace(".", "");
		return w.length() > 0 ? Character.toUpperCase(w.charAt(0)) + w.substring(1) : w;
	}

	/** "12 oct" of a date. */
	String dayMonth(String iso) {
		if (iso == null || iso.length() < 10) return "";
		int m = Integer.parseInt(iso.substring(5, 7)) - 1, day = Integer.parseInt(iso.substring(8, 10));
		String mo = new java.text.DateFormatSymbols(locale()).getShortMonths()[m].replace(".", "");
		return imp ? mo + " " + day : day + " " + mo;
	}

	/** "12 October 2026" of a date. */
	String longDate(String iso) {
		if (iso == null || iso.length() < 10) return "";
		int m = Integer.parseInt(iso.substring(5, 7)) - 1, day = Integer.parseInt(iso.substring(8, 10));
		String mo = new java.text.DateFormatSymbols(locale()).getMonths()[m];
		if (Tr.ro()) mo = mo.toLowerCase(locale());
		return imp ? mo + " " + day + ", " + iso.substring(0, 4) : day + " " + mo + " " + iso.substring(0, 4);
	}

	String monthName(int m) {
		String s = new java.text.DateFormatSymbols(locale()).getMonths()[m - 1];
		return Tr.ro() ? s.toLowerCase(locale()) : s;
	}

	String shortMonth(int m) {
		return new java.text.DateFormatSymbols(locale()).getShortMonths()[m - 1].replace(".", "");
	}

	/** "Sep–Oct" for a crop's sowing months; "planted" for orchards and vines. */
	String season(Agro.Crop c) {
		if (c.from == 0) return Tr.t("planted", "plantație");
		return c.from == c.to ? shortMonth(c.from) : shortMonth(c.from) + "–" + shortMonth(c.to);
	}

	String km(double km) {
		if (Double.isNaN(km)) return "–";
		return imp ? String.format(Locale.US, "%.2f mi", km * 0.621371).replace('.', Tr.dot()) : String.format(Locale.US, "%.2f km", km).replace('.', Tr.dot());
	}

	String hectares(double ha) {
		if (Double.isNaN(ha)) return "–";
		return imp ? f1(ha * 2.47105) + " ac" : f1(ha) + " ha";
	}

	// --- The page ----------------------------------------------------------------------------------

	/** The page in ROOT (all over again). */
	void show(FrameLayout root, long fetched, String status) {
		widthK();
		this.root = root;
		final List<int[]> keep = new ArrayList<int[]>();
		ScrollView old = findScroll(root);
		final int oldY = old != null && root.getTag() instanceof Long && (Long) root.getTag() == p.id * 10 + tab ? old.getScrollY() : 0;
		root.removeAllViews();
		bubble = null;
		Glass.grab(null);
		root.setTag(p.id * 10 + tab);
		boolean tablet = TextSize.tablet(a);
		int maxW = px(tablet ? 720 : 600);

		LinearLayout top = col();
		top.setPadding(px(14), px(10), px(14), px(10));
		top.setBackground(Glass.panelBg(dark, 0, dp));
		top.addView(header());
		top.addView(picker(), wide(10));
		top.addView(tabs(), wide(10));

		LinearLayout body = col();
		body.setPadding(px(14), px(12), px(14), px(18));
		if (d == null) {
			body.addView(tv(Tr.t("No data for this parcel yet. Open it once with internet: the app downloads its data from Open-Meteo.",
					"Încă nu sunt date pentru această parcelă. Deschide-o o dată cu internet: aplicația descarcă datele de la Open-Meteo."), 15, false, ink));
		} else {
			body.addView(actionCard());
			if (tab == 0) operational(body);
			else if (tab == 1) water(body);
			else if (tab == 2) season(body);
			else risks(body);
		}
		TextView src = tv(Tr.t("Source: Open-Meteo (models; the soil and the risks are estimates). ", "Sursa: Open-Meteo (modele; solul și riscurile sunt estimări). ")
				+ (status != null ? status : ""), 11.5f, false, dim);
		body.addView(src, wide(16));

		final ScrollView sv = new ScrollView(a);
		sv.setFillViewport(true);
		boolean low = a.getResources().getDisplayMetrics().heightPixels / dp < 520;
		LinearLayout page = col();
		if (low) {
			LinearLayout all = col();
			all.addView(top);
			all.addView(body);
			sv.addView(centred(all, maxW));
			page.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		} else {
			page.addView(centred(top, maxW));
			sv.addView(centred(body, maxW));
			page.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		}
		root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		if (oldY > 0) sv.post(new Runnable() {
			public void run() {
				sv.scrollTo(0, oldY);
			}
		});
	}

	private static ScrollView findScroll(View v) {
		if (v instanceof ScrollView) return (ScrollView) v;
		if (v instanceof ViewGroup) for (int k = 0; k < ((ViewGroup) v).getChildCount(); k++) {
			ScrollView s = findScroll(((ViewGroup) v).getChildAt(k));
			if (s != null) return s;
		}
		return null;
	}

	/** V at most MAX wide, in the middle (tablets, landscape). */
	private View centred(View v, int max) {
		FrameLayout f = new FrameLayout(a);
		int w = a.getResources().getDisplayMetrics().widthPixels;
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(w, max), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
		f.addView(v, lp);
		return f;
	}

	private View header() {
		LinearLayout r = row();
		View back = new View(a) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			protected void onDraw(Canvas c) {
				float w = getWidth(), h = getHeight();
				pt.setStyle(Paint.Style.FILL);
				pt.setColor(Glass.disc(dark));
				c.drawCircle(w / 2, h / 2, Math.min(w, h) / 2, pt);
				pt.setStyle(Paint.Style.STROKE);
				pt.setStrokeWidth(2.2f * dp);
				pt.setStrokeCap(Paint.Cap.ROUND);
				pt.setColor(ink);
				float s = w * 0.14f;
				c.drawLine(w / 2 + s * 0.5f, h / 2 - s, w / 2 - s * 0.6f, h / 2, pt);
				c.drawLine(w / 2 - s * 0.6f, h / 2, w / 2 + s * 0.5f, h / 2 + s, pt);
			}
		};
		back.setContentDescription(Tr.t("Back", "Înapoi"));
		back.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				host.back();
			}
		});
		r.addView(back, new LinearLayout.LayoutParams(px(40), px(40)));
		FitText t = tv(Tr.t("Statistics", "Statistici"), 22, true, ink);
		t.one(0.6f);
		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		tl.leftMargin = px(12);
		r.addView(t, tl);
		LinearLayout ex = row();
		ex.setBackground(Glass.pill(Glass.disc(dark), Glass.edge(dark), px(20), dp));
		ex.setPadding(px(12), px(8), px(14), px(8));
		View doc = new View(a) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			protected void onDraw(Canvas c) {
				float w = getWidth(), h = getHeight();
				pt.setStyle(Paint.Style.STROKE);
				pt.setStrokeWidth(1.6f * dp);
				pt.setStrokeJoin(Paint.Join.ROUND);
				pt.setStrokeCap(Paint.Cap.ROUND);
				pt.setColor(ink);
				Path pa = new Path();
				pa.moveTo(w * 0.22f, h * 0.08f);
				pa.lineTo(w * 0.6f, h * 0.08f);
				pa.lineTo(w * 0.8f, h * 0.28f);
				pa.lineTo(w * 0.8f, h * 0.92f);
				pa.lineTo(w * 0.22f, h * 0.92f);
				pa.close();
				c.drawPath(pa, pt);
				c.drawLine(w * 0.51f, h * 0.38f, w * 0.51f, h * 0.72f, pt);
				c.drawLine(w * 0.38f, h * 0.6f, w * 0.51f, h * 0.73f, pt);
				c.drawLine(w * 0.64f, h * 0.6f, w * 0.51f, h * 0.73f, pt);
			}
		};
		ex.addView(doc, new LinearLayout.LayoutParams(px(18), px(20)));
		TextView et = tv("PDF · CSV", 14, true, ink);
		et.setPadding(px(6), 0, 0, 0);
		ex.addView(et);
		ex.setContentDescription(Tr.t("Save as PDF or CSV", "Salvează ca PDF sau CSV"));
		ex.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				exportAsk();
			}
		});
		LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		el.leftMargin = px(8);
		r.addView(ex, el);
		return r;
	}

	private void exportAsk() {
		android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(a,
				dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
		b.setTitle(Tr.t("Save the statistics", "Salvează statistica"));
		b.setItems(new String[] { Tr.t("PDF report", "Raport PDF"), Tr.t("CSV table (Excel)", "Tabel CSV (Excel)") }, new android.content.DialogInterface.OnClickListener() {
			public void onClick(android.content.DialogInterface di, int w) {
				host.export(p, w == 0);
			}
		});
		b.setNegativeButton(Tr.t("Cancel", "Renunță"), null);
		Dlg.show(b);
	}

	/** The parcel's name with its crop, the sowing date and the area. */
	private View picker() {
		LinearLayout r = row();
		r.setBackground(Glass.cardBg(dark, px(16), dp));
		r.setPadding(px(10), px(9), px(12), px(9));
		r.addView(sprout(accent), new LinearLayout.LayoutParams(px(34), px(34)));
		LinearLayout t = col();
		FitText n = tv(p.name + (crop != null ? " · " + crop.name() : ""), 17, true, ink);
		n.one(0.6f);
		t.addView(n);
		StringBuilder s = new StringBuilder();
		if (p.sow.length() == 10) s.append(Tr.t("sown ", "semănat ")).append(dayMonth(p.sow)).append(" · ").append(Agro.between(p.sow, today)).append(Tr.t(" days", " zile"));
		else s.append(Tr.t("no crop or sowing date yet", "fără cultură sau dată de semănat"));
		double ha = p.hectares();
		if (!Double.isNaN(ha)) s.append(" · ").append(hectares(ha));
		FitText sub = tv(s.toString(), 13.5f, false, dim);
		sub.one(0.6f);
		t.addView(sub, wide(3));
		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		tl.leftMargin = px(10);
		r.addView(t, tl);
		// v2.010 (Viorel): no picking of another parcel here for now.
		return r;
	}

	/** A sprouting plant on a disc (the parcel's mark). */
	View sprout(final int color) {
		return new View(a) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			protected void onDraw(Canvas c) {
				float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2;
				pt.setStyle(Paint.Style.FILL);
				pt.setColor(Glass.alpha(color, 0.22f));
				c.drawCircle(w / 2, h / 2, r, pt);
				pt.setStyle(Paint.Style.STROKE);
				pt.setStrokeWidth(Math.max(1.5f * dp, r * 0.11f));
				pt.setStrokeCap(Paint.Cap.ROUND);
				pt.setColor(color);
				float cx = w / 2, cy = h / 2;
				c.drawLine(cx, cy + r * 0.5f, cx, cy - r * 0.15f, pt);
				Path l = new Path();
				l.moveTo(cx, cy);
				l.quadTo(cx - r * 0.5f, cy - r * 0.05f, cx - r * 0.45f, cy - r * 0.45f);
				l.quadTo(cx - r * 0.05f, cy - r * 0.4f, cx, cy);
				Path q = new Path();
				q.moveTo(cx, cy - r * 0.12f);
				q.quadTo(cx + r * 0.5f, cy - r * 0.2f, cx + r * 0.45f, cy - r * 0.58f);
				q.quadTo(cx + r * 0.05f, cy - r * 0.55f, cx, cy - r * 0.12f);
				pt.setStyle(Paint.Style.FILL_AND_STROKE);
				c.drawPath(l, pt);
				c.drawPath(q, pt);
			}
		};
	}

	private View tabs() {
		String[] n = { Tr.t("Operational", "Operațional"), Tr.t("Water & Soil", "Apă & Sol"), Tr.t("Season", "Sezon"), Tr.t("Risks", "Riscuri") };
		LinearLayout r = row();
		r.setBackground(Glass.pill(Glass.disc(dark), 0, px(22), dp));
		r.setPadding(px(4), px(4), px(4), px(4));
		final List<TextView> all = new ArrayList<TextView>();
		for (int k = 0; k < n.length; k++) {
			final int i = k;
			FitText t = tv(n[k], 14.5f, true, k == tab ? 0xFFFFFFFF : dim);
			t.setGravity(Gravity.CENTER);
			t.one(0.6f);
			t.setPadding(px(2), px(8), px(2), px(8));
			if (k == tab) t.setBackground(Glass.pill(accent, 0, px(18), dp));
			t.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (tab == i) return;
					tab = i;
					Log.i(a, "FERMIER", "statistici: fila " + i);
					host.redraw();
				}
			});
			all.add(t);
			r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		}
		fitTabs(all);
		return r;
	}

	/** The tabs' names all in one size, smaller when one does not fit. */
	private void fitTabs(final List<TextView> all) {
		all.get(0).post(new Runnable() {
			public void run() {
				float sp = 14.5f * fs;
				float sd = a.getResources().getDisplayMetrics().scaledDensity;
				for (; sp > 9; sp -= 0.5f) {
					boolean ok = true;
					for (TextView t : all) {
						Paint pt = new Paint(t.getPaint());
						pt.setTextSize(sp * sd);
						if (pt.measureText(t.getText().toString()) > t.getWidth() - t.getPaddingLeft() - t.getPaddingRight()) ok = false;
					}
					if (ok) break;
				}
				for (TextView t : all) t.setTextSize(sp);
			}
		});
	}

	// --- The action of the day --------------------------------------------------------------------

	/** { title, the line under it, the score (0–100), its level }. */
	Object[] action() {
		int[] w = cur >= 0 ? Agro.window(d, cur, 24) : null;
		String title, sub;
		int score = 0, lv = 2;
		if (w != null) {
			score = w[2];
			lv = Agro.level(score);
			int best = w[0];
			for (int k = w[0]; k < w[1]; k++) if (Agro.score(d, k) > Agro.score(d, best)) best = k;
			title = (lv == 0 ? Tr.t("Spray between ", "Stropește între ") : Tr.t("Spray with care between ", "Stropește cu grijă între ")) + clock(w[0])
					+ Tr.t(" and ", " și ") + Texts.clock(d.hourOf(Math.min(d.size() - 1, w[1])), clk12);
			double v = d.wind[best];
			sub = (v < 10 ? Tr.t("Light wind ", "Vânt slab ") : Tr.t("Wind ", "Vânt ")) + kmh(v) + ", ΔT " + degDelta(Agro.deltaT(d, best)) + ".";
			if (lv == 1) sub += Tr.t(" Anti-drift nozzles.", " Duze anti-derivă.");
		} else {
			title = Tr.t("No spraying today", "Azi nu stropi");
			sub = reason(mostBlock());
		}
		int rainAt = -1;
		if (cur >= 0) for (int k = w != null ? w[0] : cur; k < Math.min(d.size(), cur + 24); k++)
			if (Agro.block(d, k) == 1 && d.precip != null && (nz(d.precip[k]) > 0.2 || nz(d.prob[k]) >= 50)) {
				rainAt = k;
				break;
			}
		if (rainAt >= 0 && w != null && rainAt >= w[1]) sub += Tr.t(" Rain from ", " De la ") + clock(rainAt) + Tr.t(".", " ploaie.");
		String risk = riskLine();
		if (risk.length() > 0) sub += " " + risk;
		return new Object[] { title, sub, score, lv };
	}

	String degDelta(double dt) {
		return Double.isNaN(dt) ? "–" : f1(dt) + " °C";
	}

	/** The most common reason against spraying in the next 24 hours. */
	private int mostBlock() {
		int[] n = new int[7];
		if (cur >= 0) for (int k = cur; k < Math.min(d.size(), cur + 24); k++) {
			int b = Agro.block(d, k);
			if (b > 0) n[b]++;
		}
		int best = 0;
		for (int k = 1; k < 7; k++) if (n[k] > n[best]) best = k;
		return best;
	}

	String reason(int b) {
		switch (b) {
		case 1:
			return Tr.t("Rain now or soon: it washes the treatment off.", "Plouă acum sau în curând: tratamentul se spală.");
		case 2:
			return Tr.t("Too much wind (drift).", "Vânt prea puternic (derivă).");
		case 3:
			return Tr.t("Too cold (under 5 °C).", "Prea frig (sub 5 °C).");
		case 4:
			return Tr.t("Too hot (over 28 °C).", "Prea cald (peste 28 °C).");
		case 5:
			return Tr.t("Drop drying (ΔT) under 1.5 °C: the drops dry too slowly and drift.", "Uscare picături (ΔT) sub 1,5 °C: picăturile se usucă greu și derivă.");
		case 6:
			return Tr.t("Drop drying (ΔT) over 10.5 °C: the drops dry in the air.", "Uscare picături (ΔT) peste 10,5 °C: picăturile se usucă în aer.");
		default:
			return Tr.t("No good hours in the next 24 hours.", "Nicio oră bună în următoarele 24 de ore.");
		}
	}

	/** The strongest risk of the next 48 hours, in a sentence ("" when none). */
	String riskLine() {
		if (cur < 0) return "";
		int[] f = Agro.fungalRun(d, cur, 48);
		String dis = crop != null ? crop.disease().toLowerCase(locale()) : Tr.t("fungal diseases", "boli fungice");
		if (f[0] >= 12) return Tr.t("High risk of ", "Risc ridicat de ") + dis + Tr.t(" in 48 h.", " în 48 h.");
		double[] fr = Agro.frost(d, cur, 48);
		if (!Double.isNaN(fr[0]) && fr[0] <= 0) return Tr.t("Ground frost ", "Brumă la sol ") + dayHour((int) fr[1]) + ".";
		if (f[0] >= 6) return Tr.t("Moderate risk of ", "Risc moderat de ") + dis + Tr.t(" in 48 h.", " în 48 h.");
		return "";
	}

	private View actionCard() {
		Object[] ac = action();
		int lv = (Integer) ac[3], color = levelColor(lv);
		LinearLayout outer = row();
		outer.setBackground(Glass.cardBg(dark, px(16), dp));
		outer.setGravity(Gravity.TOP);
		View bar = new View(a);
		bar.setBackground(Glass.pill(color, 0, px(2), dp));
		LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(4), ViewGroup.LayoutParams.MATCH_PARENT);
		bl.topMargin = px(10);
		bl.bottomMargin = px(10);
		bl.leftMargin = px(4);
		outer.addView(bar, bl);
		LinearLayout left = col();
		left.setGravity(Gravity.CENTER_HORIZONTAL);
		int code = cur >= 0 && !Double.isNaN(d.code[cur]) ? (int) d.code[cur] : d.cCode;
		boolean day = cur < 0 || Double.isNaN(d.isDay[cur]) || d.isDay[cur] > 0.5;
		left.addView(icon(code < 0 ? null : Weather.iconOf(code, day)), new LinearLayout.LayoutParams(px(48), px(48)));
		TextView sc = tv(ac[2] + "/100", 12.5f, true, 0xFFFFFFFF);
		sc.setPadding(px(7), px(2), px(7), px(2));
		sc.setBackground(Glass.pill(color, 0, px(9), dp));
		left.addView(sc, wide(4));
		LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		ll.leftMargin = px(10);
		ll.topMargin = px(12);
		ll.bottomMargin = px(12);
		outer.addView(left, ll);
		LinearLayout txt = col();
		txt.addView(caps(Tr.t("Action of the day", "Acțiunea zilei"), color));
		txt.addView(tv((String) ac[0], 17, true, ink), wide(4));
		txt.addView(tv((String) ac[1], 14, false, dim), wide(4));
		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		tl.leftMargin = px(12);
		tl.rightMargin = px(12);
		tl.topMargin = px(12);
		tl.bottomMargin = px(12);
		outer.addView(txt, tl);
		return outer;
	}

	/** A weather icon NAME. */
	private View icon(final String name) {
		return new View(a) {
			AndroidGfx g;

			@Override
			protected void onDraw(Canvas c) {
				if (name == null) return;
				if (g == null) g = new AndroidGfx(a.getAssets());
				g.c = c;
				float s = Math.min(getWidth(), getHeight());
				WxIcon.draw(g, name, 0, 0, s, 1, Theme.icon(dark));
			}
		};
	}

	// --- Operational ----------------------------------------------------------------------------

	private void operational(LinearLayout body) {
		body.addView(section(Tr.t("Spray window", "Fereastra de stropit"), seg(new String[] { "24 h", "72 h" }, hours == 24 ? 0 : 1, new Pick() {
			public void at(int k) {
				hours = k == 0 ? 24 : 72;
				host.redraw();
			}
		})));
		LinearLayout c = card();
		c.setPadding(px(10), px(10), px(10), px(12));
		if (cur < 0) {
			c.addView(tv(Tr.t("The hours are missing from the data.", "Orele lipsesc din date."), 14, false, dim));
			body.addView(c);
			return;
		}
		final int n = Math.min(hours, d.size() - cur);
		if (sel >= cur + n) sel = -1;
		final HorizontalScrollView hs = new HorizontalScrollView(a);
		hs.setHorizontalScrollBarEnabled(false);
		View band = new View(a) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
			final RectF rf = new RectF();
			float downX;

			@Override
			protected void onMeasure(int ws, int hs2) {
				setMeasuredDimension(Math.round(n * cell() + px(4)), px(hours > 24 ? 88 : 72));
			}

			float cell() {
				return px(42);
			}

			@Override
			protected void onDraw(Canvas cv) {
				float top = hours > 24 ? px(16) : px(2), h = px(64), w = px(36);
				pt.setTextAlign(Paint.Align.CENTER);
				for (int k = 0; k < n; k++) {
					int i = cur + k;
					float x = px(2) + k * cell();
					if (hours > 24 && (k == 0 || d.hourOf(i).startsWith("00"))) {
						pt.setStyle(Paint.Style.FILL);
						pt.setColor(dim);
						pt.setTextAlign(Paint.Align.LEFT);
						pt.setTypeface(Typeface.DEFAULT_BOLD);
						pt.setTextSize(11 * dp * fs);
						cv.drawText(d.dateOf(i).equals(today) ? Tr.t("Today", "Azi") : weekday(d.dateOf(i)), x + px(2), px(11), pt);
						pt.setTextAlign(Paint.Align.CENTER);
					}
					int s = Agro.score(d, i), lv = Agro.level(s), b = Agro.block(d, i);
					rf.set(x, top, x + w, top + h);
					pt.setStyle(Paint.Style.FILL);
					pt.setColor(levelColor(lv));
					cv.drawRoundRect(rf, px(9), px(9), pt);
					if (i == sel) {
						pt.setStyle(Paint.Style.STROKE);
						pt.setStrokeWidth(2.5f * dp);
						pt.setColor(ink);
						rf.inset(-px(1.5f), -px(1.5f));
						cv.drawRoundRect(rf, px(10), px(10), pt);
					}
					pt.setStyle(Paint.Style.FILL);
					pt.setColor(0xFFFFFFFF);
					pt.setTypeface(Typeface.DEFAULT_BOLD);
					pt.setTextSize(12.5f * dp);
					String hh = d.hourOf(i);
					hh = hh.length() >= 2 ? hh.substring(0, 2) : "";
					if (clk12 && hh.length() == 2) {
						int hv = Integer.parseInt(hh) % 12;
						hh = (hv == 0 ? 12 : hv) + (Integer.parseInt(d.hourOf(i).substring(0, 2)) < 12 ? "a" : "p");
					}
					cv.drawText(hh, x + w / 2, top + px(18), pt);
					float cy = top + h - px(16);
					if (b == 1) drop(cv, x + w / 2, cy, px(7));
					else if (b == 2) gusts(cv, x + w / 2, cy, px(8));
					else {
						pt.setTypeface(Typeface.DEFAULT);
						pt.setTextSize(12 * dp);
						cv.drawText(f1(Agro.deltaT(d, i)), x + w / 2, cy + px(4), pt);
					}
				}
			}

			void drop(Canvas cv, float cx, float cy, float r) {
				Path pa = new Path();
				pa.moveTo(cx, cy - r * 1.3f);
				pa.quadTo(cx + r * 1.1f, cy, cx, cy + r * 0.8f);
				pa.quadTo(cx - r * 1.1f, cy, cx, cy - r * 1.3f);
				pt.setStyle(Paint.Style.FILL);
				pt.setColor(0xFFFFFFFF);
				cv.drawPath(pa, pt);
			}

			void gusts(Canvas cv, float cx, float cy, float r) {
				pt.setStyle(Paint.Style.STROKE);
				pt.setStrokeWidth(1.8f * dp);
				pt.setStrokeCap(Paint.Cap.ROUND);
				pt.setColor(0xFFFFFFFF);
				cv.drawLine(cx - r, cy - r * 0.5f, cx + r * 0.6f, cy - r * 0.5f, pt);
				cv.drawLine(cx - r, cy, cx + r, cy, pt);
				cv.drawLine(cx - r, cy + r * 0.5f, cx + r * 0.3f, cy + r * 0.5f, pt);
			}

			@Override
			public boolean onTouchEvent(MotionEvent e) {
				if (e.getAction() == MotionEvent.ACTION_DOWN) {
					downX = e.getX();
					return true;
				}
				if (e.getAction() == MotionEvent.ACTION_UP && Math.abs(e.getX() - downX) < px(10)) {
					int k = (int) ((e.getX() - px(2)) / cell());
					if (k >= 0 && k < n) {
						sel = cur + k;
						invalidate();
						hourBubble(sel, this);
					}
				}
				return true;
			}
		};
		hs.addView(band);
		c.addView(hs);
		autoScroll(hs);
		LinearLayout legend = row();
		legend.setPadding(px(2), px(8), px(2), 0);
		legend.addView(dot(G, Tr.t("Optimal", "Optim")));
		legend.addView(dot(AMB, Tr.t("Care", "Atenție")));
		legend.addView(dot(R, Tr.t("No", "Interzis")));
		View sp = new View(a);
		legend.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
		FitText num = tv(Tr.t("number = ΔT", "cifra = ΔT"), 12, false, dim);
		num.one(0.6f);
		legend.addView(num);
		c.addView(legend);
		// v2.010 (Viorel): a tap on an hour opens its bubble.
		FitText hint = tv(Tr.t("Tap an hour for its explanation.", "Atinge o oră pentru explicație."), 12, false, dim);
		hint.one(0.6f);
		c.addView(hint, wide(6));
		body.addView(c);

		body.addView(deltaCard(), wide(12));

		LinearLayout two = new LinearLayout(a);
		two.addView(windCard(), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
		two.addView(wetCard(), weight(10));
		body.addView(two, wide(12));
	}

	private View dot(int color, String label) {
		LinearLayout r = row();
		r.setPadding(0, 0, px(12), 0);
		View d0 = new View(a);
		d0.setBackground(Glass.pill(color, 0, px(5), dp));
		r.addView(d0, new LinearLayout.LayoutParams(px(10), px(10)));
		FitText t = tv(label, 12.5f, false, dim);
		t.setPadding(px(5), 0, 0, 0);
		r.addView(t);
		return r;
	}

	/**
	 * v2.010 (Viorel): the bubble of an hour of the spray window: the verdict, the score, why,
	 * and the wind, ΔT, temperature and rain of that hour.
	 */
	private void hourBubble(int i, final View band) {
		if (!d.has(i)) return;
		int s = Agro.score(d, i), lv = Agro.level(s);
		String[] words = { Tr.t("optimal", "optim"), Tr.t("care", "atenție"), Tr.t("no", "interzis") };
		LinearLayout box = col();
		LinearLayout vr = row();
		if (lv >= 0) vr.addView(badge(words[lv].toUpperCase(locale()), levelColor(lv)));
		FitText sc = tv(Tr.t("score ", "scor ") + Math.max(0, s) + " / 100", 15, true, ink);
		sc.one(0.6f);
		sc.setPadding(px(lv >= 0 ? 10 : 0), 0, 0, 0);
		vr.addView(sc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		box.addView(vr);
		int b = Agro.block(d, i);
		String why = b > 0 ? reason(b) : lv == 0 ? Tr.t("Wind, drop drying (ΔT) and temperature are all in the good range.", "Vântul, uscarea picăturilor (ΔT) și temperatura sunt toate în intervalul bun.")
				: lv > 0 ? Tr.t("Wind, drop drying (ΔT) or temperature outside the ideal range.", "Vânt, uscarea picăturilor (ΔT) sau temperatură în afara intervalului ideal.") : "";
		if (why.length() > 0) box.addView(tv(why, 13, false, dim), wide(8));
		LinearLayout r = new LinearLayout(a);
		double dt = Agro.deltaT(d, i);
		int dl = Agro.dtLevel(dt);
		r.addView(stat(Tr.t("Wind", "Vânt"), kmh(d.wind[i]), Tr.t("gusts ", "rafale ") + kmh(d.gust[i])), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		r.addView(stat("ΔT", degDelta(dt), dl >= 0 ? words[dl] : ""), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		r.addView(stat(Tr.t("Temp.", "Temp."), deg(d.temp[i]), Tr.t("RH ", "UR ") + Math.round(nz(d.hum[i])) + "%"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		r.addView(stat(Tr.t("Rain", "Ploaie"), Math.round(nz(d.prob[i])) + "%", mm(nz(d.precip[i]))), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		box.addView(r, wide(12));
		bubble(Tr.t("Hour ", "Ora ") + dayHour(i), box, new Runnable() {
			public void run() {
				sel = -1;
				band.invalidate();
			}
		});
	}

	// --- v2.010: the bubbles and the moving band ------------------------------------------------

	private FrameLayout bubble;

	private void closeBubble() {
		if (bubble != null && root != null) root.removeView(bubble);
		bubble = null;
		Glass.grab(null);
	}

	/**
	 * v2.010 (Viorel): a bubble over the page: TITLE, BODY and a round ✕ at the top right; a tap
	 * outside it also closes it. Only the bubble is frosted glass, the page stays sharp.
	 */
	private void bubble(String title, View body, final Runnable onClose) {
		if (root == null) return;
		closeBubble();
		Glass.grab(root);
		final FrameLayout scrim = new FrameLayout(a);
		scrim.setClickable(true);
		final LinearLayout card = col();
		card.setClickable(true);
		card.setBackground(Glass.frostBg(card, px(20), dark, dp, false));
		card.setElevation(px(8));
		card.setPadding(px(16), px(12), px(12), px(16));
		LinearLayout head = row();
		FitText t = tv(title, 16, true, ink);
		t.one(0.6f);
		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		tl.rightMargin = px(8);
		head.addView(t, tl);
		View x = new View(a);
		x.setBackground(Glass.closeIcon(dark, dp));
		head.addView(x, new LinearLayout.LayoutParams(px(34), px(34)));
		card.addView(head);
		LinearLayout.LayoutParams bl = wide(6);
		bl.rightMargin = px(4);
		card.addView(body, bl);
		View.OnClickListener close = new View.OnClickListener() {
			public void onClick(View v) {
				if (bubble != scrim) return;
				closeBubble();
				if (onClose != null) onClose.run();
			}
		};
		x.setOnClickListener(close);
		scrim.setOnClickListener(close);
		int w = Math.min(root.getWidth() > 0 ? root.getWidth() - px(32) : px(340), px(TextSize.tablet(a) ? 460 : 380));
		scrim.addView(card, new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
		root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		bubble = scrim;
	}

	interface TapAt {
		void at(float x, float y);
	}

	/** v2.010: a tap (not a slide) on V gives its place. */
	private void onTap(final View v, final TapAt t) {
		v.setOnTouchListener(new View.OnTouchListener() {
			float x0, y0;

			public boolean onTouch(View w, MotionEvent e) {
				if (e.getAction() == MotionEvent.ACTION_DOWN) {
					x0 = e.getX();
					y0 = e.getY();
					return true;
				}
				if (e.getAction() == MotionEvent.ACTION_UP && Math.abs(e.getX() - x0) < px(10) && Math.abs(e.getY() - y0) < px(10)) t.at(e.getX(), e.getY());
				return true;
			}
		});
	}

	/** A row of a bubble: LABEL at the left, VALUE at the right. */
	private View pair(String label, String value, int color) {
		LinearLayout r = row();
		FitText l = tv(label, 14, false, dim);
		l.one(0.6f);
		r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		FitText v = tv(value, 15, true, color);
		v.one(0.6f);
		v.setPadding(px(8), 0, 0, 0);
		r.addView(v);
		return r;
	}

	/** v2.010 (Viorel): the bubble of a day of the rain and evaporation chart. */
	private void dayBubble(int k, int t) {
		if (k < 0 || k >= d.dDate.length) return;
		String date = d.dDate[k];
		double rn = nz(d.dRain[k]), et = Agro.et0(d, k, p.lat), bal = rn - nz(et);
		LinearLayout box = col();
		box.addView(pair(Tr.t("Rain", "Ploaie"), mm(rn), B));
		box.addView(pair(Tr.t("Evaporation (ET₀)", "Evaporare (ET₀)"), Double.isNaN(et) ? "–" : mm(et), O), wide(6));
		box.addView(pair(Tr.t("Balance of the day", "Bilanțul zilei"), Double.isNaN(et) ? "–" : (bal >= 0 ? "+" : "−") + mm(Math.abs(bal)), bal >= 0 ? B : AMB), wide(6));
		box.addView(pair(Tr.t("Temperature", "Temperatura"), deg(d.dMax[k]) + " / " + deg(d.dMin[k]), ink), wide(6));
		box.addView(tv((k <= t ? Tr.t("Measured (model), ", "Măsurat (model), ") : Tr.t("Forecast, ", "Prognoză, "))
				+ Tr.t("ET₀ = how much water the soil and the plants lose in a day.", "ET₀ = câtă apă pierd solul și plantele într-o zi."), 12.5f, false, dim), wide(10));
		String when = date.equals(today) ? Tr.t("Today", "Azi") : weekday(date);
		bubble(when + " · " + dayMonth(date), box, null);
	}

	/** v2.010 (Viorel): the bubble of day Q of the degree-days chart. */
	private void gddBubble(int q, List<double[]> now, List<double[]> last, int xt) {
		double s1 = Double.NaN, s0 = Double.NaN, l1 = Double.NaN;
		for (double[] v : now) {
			if (v[0] == q - 1) s0 = v[1];
			if (v[0] == q) s1 = v[1];
		}
		for (double[] v : last) if (v[0] <= q) l1 = v[1];
		String date = Agro.plus(seasonStart, q);
		LinearLayout box = col();
		box.addView(pair(Tr.t("This season", "Sezonul acesta"), Double.isNaN(s1) ? "–" : Math.round(s1) + " °C·" + Tr.t("days", "zile"), G));
		box.addView(pair(Tr.t("Last year", "Anul trecut"), Double.isNaN(l1) ? "–" : Math.round(l1) + " °C·" + Tr.t("days", "zile"), dim), wide(6));
		if (!Double.isNaN(s1) && !Double.isNaN(s0)) box.addView(pair(Tr.t("Gained that day", "Câștigat în ziua aceea"), "+" + Math.round(s1 - s0), ink), wide(6));
		if (!Double.isNaN(s1)) {
			int st = Agro.stage(crop.type, s1);
			String[] nm = Agro.stageNames(crop.type);
			if (st >= 0 && st < nm.length) box.addView(pair(Tr.t("Stage", "Stadiu"), nm[st], ink), wide(6));
		}
		box.addView(tv((q > xt ? Tr.t("Forecast. ", "Prognoză. ") : "") + Tr.t("Degree days add up the warmth above the crop's base temperature; the stages follow them.",
				"Gradele-zile adună căldura de peste temperatura de bază a culturii; stadiile le urmează."), 12.5f, false, dim), wide(10));
		bubble(dayMonth(date), box, null);
	}

	/** v2.010 (Viorel): the bubble of a soil depth. */
	private void soilBubble(String depth, double v, double lo) {
		LinearLayout box = col();
		box.addView(pair(Tr.t("Now", "Acum"), degU(v), ink));
		box.addView(pair(Tr.t("Sowing threshold", "Prag semănat"), Math.round(lo) + "–" + Math.round(lo + 2) + " °C", G), wide(6));
		String verdict = Double.isNaN(v) ? "" : v < lo ? Tr.t("Too cold for sowing: the seeds come up slowly and unevenly.", "Prea rece pentru semănat: semințele răsar greu și neuniform.")
				: v <= lo + 2 ? Tr.t("At the threshold: sowing can start if the next days stay warm.", "La prag: se poate semăna dacă zilele următoare rămân calde.")
				: Tr.t("Warm enough for sowing.", "Destul de cald pentru semănat.");
		if (verdict.length() > 0) box.addView(tv(verdict, 13.5f, false, ink), wide(10));
		box.addView(tv(Tr.t("Open-Meteo model estimate, not a measurement.", "Estimare a modelului Open-Meteo, nu măsurătoare."), 12, false, dim), wide(8));
		bubble(Tr.t("Soil at ", "Solul la ") + depth, box, null);
	}

	/**
	 * v2.010 (Viorel): after 4 s the band moves to the right by itself and back when it reaches
	 * the end; a finger on it stops it.
	 */
	private void autoScroll(final HorizontalScrollView hs) {
		final android.animation.ValueAnimator[] an = new android.animation.ValueAnimator[1];
		final Runnable start = new Runnable() {
			public void run() {
				if (!hs.isAttachedToWindow() || hs.getChildCount() == 0) return;
				int max = hs.getChildAt(0).getWidth() - hs.getWidth();
				if (max <= px(8)) return;
				android.animation.ValueAnimator v = android.animation.ValueAnimator.ofInt(hs.getScrollX(), max);
				v.setDuration(Math.max(2500, Math.round(max / dp * 22)));
				v.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
				v.setRepeatCount(android.animation.ValueAnimator.INFINITE);
				v.setRepeatMode(android.animation.ValueAnimator.REVERSE);
				v.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
					public void onAnimationUpdate(android.animation.ValueAnimator x) {
						hs.scrollTo((Integer) x.getAnimatedValue(), 0);
					}
				});
				an[0] = v;
				v.start();
			}
		};
		hs.postDelayed(start, 4000);
		hs.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent e) {
				hs.removeCallbacks(start);
				if (an[0] != null) {
					an[0].cancel();
					an[0] = null;
				}
				return false;
			}
		});
		hs.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
			public void onViewAttachedToWindow(View v) {
			}

			public void onViewDetachedFromWindow(View v) {
				hs.removeCallbacks(start);
				if (an[0] != null) an[0].cancel();
			}
		});
	}

	private View stat(String label, String value, String sub) {
		LinearLayout c = col();
		c.setGravity(Gravity.CENTER_HORIZONTAL);
		TextView l = tv(label, 12.5f, true, dim);
		l.setGravity(Gravity.CENTER);
		c.addView(l);
		FitText v = tv(value, 18, true, ink);
		v.one(0.6f);
		v.setGravity(Gravity.CENTER);
		c.addView(v, wide(3));
		FitText s = tv(sub, 12, false, dim);
		s.setGravity(Gravity.CENTER);
		s.one(0.6f);
		c.addView(s, wide(2));
		return c;
	}

	private View deltaCard() {
		LinearLayout c = card();
		final double dt = Agro.deltaT(d, cur);
		int lv = Agro.dtLevel(dt);
		LinearLayout top = row();
		top.addView(caps(Tr.t("Drop drying (ΔT) now", "Uscare picături (ΔT) acum"), dim), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		top.addView(tv(degDelta(dt), 22, true, ink));
		String[] w = { Tr.t("OPTIMAL", "OPTIM"), Tr.t("CARE", "ATENȚIE"), Tr.t("NO", "INTERZIS") };
		if (lv >= 0) {
			TextView b = badge(w[lv], levelColor(lv));
			LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			bl.leftMargin = px(8);
			top.addView(b, bl);
		}
		c.addView(top);
		View g = new View(a) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			protected void onDraw(Canvas cv) {
				float w0 = getWidth(), pad = px(10), bw = w0 - 2 * pad, y = px(18), th = px(8);
				// v2.010: the zones as in Agro.dtLevel (1.5 and 10.5 the hard limits).
				float[] stops = { 0, 1.5f, 2, 8, 10.5f, 14 };
				int[] cols = { R, AMB, G, AMB, R };
				pt.setStyle(Paint.Style.FILL);
				for (int k = 0; k < 5; k++) {
					pt.setColor(cols[k]);
					float x1 = pad + bw * stops[k] / 14, x2 = pad + bw * stops[k + 1] / 14;
					RectF r = new RectF(x1, y - th / 2, x2, y + th / 2);
					if (k == 0 || k == 4) cv.drawRoundRect(r, th / 2, th / 2, pt);
					cv.drawRect(k == 0 ? x1 + th / 2 : x1, y - th / 2, k == 4 ? x2 - th / 2 : x2, y + th / 2, pt);
				}
				pt.setColor(dim);
				pt.setTextSize(12 * dp * fs * wk);
				pt.setTextAlign(Paint.Align.CENTER);
				for (float s : new float[] { 0, 2, 8, 14 }) cv.drawText(String.valueOf((int) s), pad + bw * s / 14, y + px(24), pt);
				if (!Double.isNaN(dt)) {
					float x = pad + bw * (float) Math.min(14, Math.max(0, dt)) / 14;
					pt.setColor(0xFFFFFFFF);
					cv.drawCircle(x, y, px(10), pt);
					pt.setStyle(Paint.Style.STROKE);
					pt.setStrokeWidth(2.5f * dp);
					pt.setColor(dark ? 0xFF333333 : 0xFF666666);
					cv.drawCircle(x, y, px(10), pt);
				}
			}
		};
		c.addView(g, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(48)));
		c.addView(tv(Tr.t("ΔT = air temperature minus wet-bulb temperature: how fast a drop dries. 2–8 °C optimal. Under 2 °C the drops stay and drift, over 10 °C they dry in the air.",
				"ΔT = temperatura aerului minus temperatura umedă: cât de repede se usucă o picătură. 2–8 °C optim. Sub 2 °C picăturile rămân și derivă, peste 10 °C se usucă în aer."), 13, false, dim), wide(4));
		return c;
	}

	private View windCard() {
		LinearLayout c = card();
		c.addView(caps(Tr.t("Wind and gusts", "Vânt și rafale"), dim));
		double w = !Double.isNaN(d.cWind) ? d.cWind : d.wind[cur], dir = !Double.isNaN(d.cDir) ? d.cDir : d.dir[cur], g = !Double.isNaN(d.cGust) ? d.cGust : d.gust[cur];
		LinearLayout r = row();
		r.setGravity(Gravity.BOTTOM);
		r.addView(tv(kmh(w), 22, true, ink));
		TextView dr = tv(Double.isNaN(dir) ? "" : "  " + Tr.dirs8()[Farm.sector(dir)], 15, true, dim);
		r.addView(dr);
		c.addView(r, wide(6));
		double[] mg = Agro.maxGust(d, cur, 24);
		c.addView(tv(Tr.t("gusts ", "rafale ") + kmh(g) + (mg[1] >= 0 ? " · " + Tr.t("max ", "max ") + kmh(mg[0]) + Tr.t(" at ", " la ") + dayHour((int) mg[1]) : ""), 12.5f, false, dim),
				wide(4));
		return c;
	}

	private View wetCard() {
		LinearLayout c = card();
		c.addView(caps(Tr.t("Wet leaf", "Frunză umedă"), dim));
		int[] w = Agro.wetNow(d, cur);
		LinearLayout r = row();
		r.setGravity(Gravity.BOTTOM);
		r.addView(tv(w[0] + " h", 22, true, ink));
		TextView st = tv("  " + (w[0] > 0 ? Tr.t("wet", "udă") : Tr.t("dry", "uscată")), 15, true, w[0] > 0 ? B : G);
		r.addView(st);
		c.addView(r, wide(6));
		int night = 0;
		for (int k = Math.max(0, cur - 12); k <= cur; k++) if (Agro.wet(d, k)) night++;
		String sub = w[0] > 0 ? Tr.t("the leaves are wet now", "frunzele sunt ude acum") : w[1] >= 0 ? Tr.t("dried at ", "s-a uscat la ") + clock(w[1]) : Tr.t("no dew or rain lately", "fără rouă sau ploaie de curând");
		sub += " · " + night + Tr.t(" h in the last 12", " h în ultimele 12");
		c.addView(tv(sub, 12.5f, false, dim), wide(4));
		return c;
	}

	// --- Water & Soil --------------------------------------------------------------------------

	double sum(double[] v, int a0, int b0) {
		double s = 0;
		for (int k = Math.max(0, a0); k <= b0 && k < v.length; k++) s += nz(v[k]);
		return s;
	}

	private void water(LinearLayout body) {
		if (cur < 0) {
			body.addView(tv(Tr.t("The hours are missing from the data.", "Orele lipsesc din date."), 14, false, dim), wide(12));
			return;
		}
		int t = d.dayIndex(today);
		double r7 = t >= 0 ? sum(d.dRain, t - 6, t) : Double.NaN, r28 = t >= 0 ? sum(d.dRain, t - 27, t) : Double.NaN;
		double e7 = 0;
		if (t >= 0) for (int k = Math.max(0, t - 6); k <= t; k++) e7 += nz(Agro.et0(d, k, p.lat));
		else e7 = Double.NaN;
		double bal = r7 - e7;
		LinearLayout kp = new LinearLayout(a);
		kp.addView(kpi(Tr.t("Rain 7 days", "Ploi 7 zile"), mm(r7), Tr.t("28 days: ", "28 zile: ") + mm(r28), ink), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
		kp.addView(kpi(Tr.t("Evaporation", "Evaporare"), "−" + mm(e7), "ET₀ " + Tr.t("7 days", "7 zile"), ink), weight(8));
		String bw = Double.isNaN(bal) ? "" : bal > 0 ? Tr.t("surplus", "excedent") : bal < -15 ? Tr.t("deficit, irrigate", "deficit, irigă") : Tr.t("slight deficit", "deficit ușor");
		kp.addView(kpi(Tr.t("Balance", "Bilanț"), Double.isNaN(bal) ? "–" : (bal >= 0 ? "+" : "−") + mm(Math.abs(bal)), bw, bal >= 0 ? B : bal < -15 ? R : AMB), weight(8));
		body.addView(kp, wide(12));

		body.addView(section(Tr.t("History and forecast", "Istoric și prognoză"), seg(new String[] { Tr.t("7 d", "7 z"), Tr.t("14 d", "14 z"), Tr.t("28 d", "28 z") },
				span == 7 ? 0 : span == 14 ? 1 : 2, new Pick() {
					public void at(int k) {
						span = k == 0 ? 7 : k == 1 ? 14 : 28;
						host.redraw();
					}
				})));
		LinearLayout c = card();
		c.addView(rainChart(t), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(190)));
		LinearLayout lg = row();
		lg.setPadding(0, px(6), 0, 0);
		lg.addView(dot(B, Tr.t("rain mm/day", "ploaie mm/zi")));
		lg.addView(dot(O, Tr.t("evaporation (ET₀)", "evaporare (ET₀)")));
		c.addView(lg);
		body.addView(c);

		body.addView(section(Tr.t("The soil", "Solul"), null));
		LinearLayout s = card();
		s.addView(caps(Tr.t("Soil temperature", "Temperatura solului"), dim));
		final double lo = crop != null ? crop.soilMin : 8;
		String who = crop != null ? crop.name().toLowerCase(locale()) : Tr.t("maize", "porumb");
		s.addView(tv(Tr.t("green zone = sowing threshold for ", "zona verde = prag semănat ") + who + " " + Math.round(lo) + "–" + Math.round(lo + 2) + " °C", 12, false, dim), wide(3));
		s.addView(soilRow("6 cm", d.soilT6[cur], lo), wide(8));
		s.addView(soilRow("18 cm", d.soilT18[cur], lo), wide(6));
		View line = new View(a);
		line.setBackgroundColor(Glass.edge(dark));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(1)));
		lp.topMargin = px(12);
		s.addView(line, lp);
		LinearLayout two = new LinearLayout(a);
		double m1 = d.soilM1[cur];
		String[] mw = { Tr.t("dry", "uscat"), Tr.t("moderate", "potrivit"), Tr.t("moist", "umed"), Tr.t("near saturation", "aproape de saturație") };
		int ml = Farm.soilLevel(m1);
		two.addView(half(Tr.t("Surface moisture", "Umiditate suprafață"), Double.isNaN(m1) ? "–" : Math.round(m1 * 100) + " %", "0–1 cm" + (ml >= 0 ? " · " + mw[ml] : ""), ink),
				new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		double rain3 = t >= 0 ? sum(d.dRain, t - 2, t) : Double.NaN;
		int ac = Agro.access(rain3, d.soilM[cur]);
		String[] aw = { Tr.t("Good", "Bună"), Tr.t("Care", "Atenție"), Tr.t("Wait", "Așteaptă") };
		String[] as = { Tr.t("firm ground", "teren portant"), Tr.t("risk of compaction", "risc de compactare"), Tr.t("the soil compacts", "solul se compactează") };
		two.addView(half(Tr.t("Machinery on the field", "Intrare cu utilaje"), ac >= 0 ? aw[ac] : "–", (ac >= 0 ? as[ac] + " · " : "") + Tr.t("rain 3 days ", "ploaie 3 zile ") + mm(rain3),
				levelColor(ac)), weight(12));
		s.addView(two, wide(10));
		body.addView(s);
	}

	private View kpi(String label, String value, String sub, int color) {
		LinearLayout c = card();
		c.setGravity(Gravity.CENTER_HORIZONTAL);
		c.setPadding(px(6), px(10), px(6), px(10));
		TextView l = caps(label, dim);
		l.setGravity(Gravity.CENTER);
		c.addView(l);
		FitText v = tv(value, 20, true, color);
		v.one(0.6f);
		v.setGravity(Gravity.CENTER);
		c.addView(v, wide(6));
		FitText s = tv(sub, 12, false, dim);
		s.setGravity(Gravity.CENTER);
		c.addView(s, wide(4));
		return c;
	}

	private View half(String label, String value, String sub, int color) {
		LinearLayout c = col();
		c.addView(caps(label, dim));
		c.addView(tv(value, 20, true, color), wide(5));
		c.addView(tv(sub, 12.5f, false, dim), wide(3));
		return c;
	}

	private View soilRow(String label, final double v, final double lo) {
		LinearLayout r = row();
		r.addView(tv(label, 14, true, ink), new LinearLayout.LayoutParams(px(52), ViewGroup.LayoutParams.WRAP_CONTENT));
		View bar = new View(a) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			protected void onDraw(Canvas c) {
				float w = getWidth(), h = getHeight(), th = px(8), y = h / 2, max = 30;
				pt.setStyle(Paint.Style.FILL);
				pt.setColor(Glass.disc(dark));
				c.drawRoundRect(new RectF(0, y - th / 2, w, y + th / 2), th / 2, th / 2, pt);
				if (!Double.isNaN(v)) {
					pt.setColor(B);
					float x = (float) (w * Math.max(0, Math.min(max, v)) / max);
					c.drawRoundRect(new RectF(0, y - th / 2, Math.max(th, x), y + th / 2), th / 2, th / 2, pt);
				}
				float z1 = (float) (w * lo / max), z2 = (float) (w * (lo + 2) / max);
				pt.setColor(Glass.alpha(G, 0.45f));
				RectF z = new RectF(z1, y - th, z2, y + th);
				c.drawRoundRect(z, px(3), px(3), pt);
				pt.setStyle(Paint.Style.STROKE);
				pt.setStrokeWidth(1.2f * dp);
				pt.setPathEffect(new DashPathEffect(new float[] { px(3), px(2) }, 0));
				pt.setColor(G);
				c.drawRoundRect(z, px(3), px(3), pt);
				pt.setPathEffect(null);
			}
		};
		r.addView(bar, new LinearLayout.LayoutParams(0, px(24), 1));
		TextView val = tv(degU(v), 16, true, ink);
		val.setGravity(Gravity.RIGHT);
		r.addView(val, new LinearLayout.LayoutParams(px(70), ViewGroup.LayoutParams.WRAP_CONTENT));
		final String depth = label;
		r.setOnClickListener(new View.OnClickListener() {
			public void onClick(View w) {
				soilBubble(depth, v, lo);
			}
		});
		return r;
	}

	/** The rain bars and the ET0 line from SPAN days back to 7 ahead, with "TODAY" between. */
	private View rainChart(final int t) {
		final int from = Math.max(0, t - span), to = Math.min(d.dDate.length - 1, t + 7);
		final int n = to - from + 1;
		final double[] rain = new double[n], et = new double[n];
		double mx = 5;
		for (int k = 0; k < n; k++) {
			rain[k] = nz(d.dRain[from + k]);
			et[k] = Agro.et0(d, from + k, p.lat);
			mx = Math.max(mx, Math.max(rain[k], nz(et[k])));
		}
		final double top = Math.ceil(mx / 5) * 5;
		final View ch = new View(a) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			protected void onDraw(Canvas c) {
				if (t < 0 || n <= 1) return;
				float w = getWidth(), h = getHeight(), l = px(24), r = px(6), tp = px(26), bt = h - px(20);
				float cw = (w - l - r) / n;
				pt.setTextSize(11 * dp * fs);
				// The measured part darker.
				float xt = l + (t - from + 0.5f) * cw;
				pt.setStyle(Paint.Style.FILL);
				pt.setColor(Glass.alpha(ink, dark ? 0.05f : 0.04f));
				c.drawRect(l, tp, xt, bt, pt);
				// The grid.
				pt.setColor(Glass.edge(dark));
				pt.setStrokeWidth(Math.max(1, dp * 0.8f));
				for (int g = 0; g <= 2; g++) {
					float y = bt - (bt - tp) * g / 2f;
					c.drawLine(l, y, w - r, y, pt);
					pt.setColor(dim);
					pt.setTextAlign(Paint.Align.RIGHT);
					c.drawText(String.valueOf(Math.round(top * g / 2)), l - px(4), y + px(4), pt);
					pt.setColor(Glass.edge(dark));
				}
				// The bars.
				for (int k = 0; k < n; k++) {
					float x = l + k * cw + cw * 0.22f, bh = (float) ((bt - tp) * rain[k] / top);
					pt.setColor(from + k <= t ? B : Glass.alpha(B, 0.55f));
					if (bh > 0) c.drawRect(x, bt - bh, x + cw * 0.56f, bt, pt);
				}
				// The ET0 line: solid behind, dashed ahead.
				pt.setStyle(Paint.Style.STROKE);
				pt.setStrokeWidth(2.2f * dp);
				pt.setColor(O);
				Path past = new Path(), next = new Path();
				boolean p1 = false, p2 = false;
				for (int k = 0; k < n; k++) {
					if (Double.isNaN(et[k])) continue;
					float x = l + (k + 0.5f) * cw, y = (float) (bt - (bt - tp) * et[k] / top);
					if (from + k <= t) {
						if (!p1) past.moveTo(x, y);
						else past.lineTo(x, y);
						p1 = true;
					}
					if (from + k >= t) {
						if (!p2) next.moveTo(x, y);
						else next.lineTo(x, y);
						p2 = true;
					}
				}
				c.drawPath(past, pt);
				pt.setPathEffect(new DashPathEffect(new float[] { px(5), px(4) }, 0));
				c.drawPath(next, pt);
				// Today.
				pt.setColor(ink);
				pt.setStrokeWidth(1.4f * dp);
				pt.setPathEffect(new DashPathEffect(new float[] { px(3), px(3) }, 0));
				c.drawLine(xt, tp - px(4), xt, bt, pt);
				pt.setPathEffect(null);
				String tl = Tr.t("TODAY", "AZI");
				pt.setStyle(Paint.Style.FILL);
				pt.setTypeface(Typeface.DEFAULT_BOLD);
				float tw = pt.measureText(tl) + px(14);
				pt.setColor(accent);
				c.drawRoundRect(new RectF(xt - tw / 2, tp - px(24), xt + tw / 2, tp - px(6)), px(9), px(9), pt);
				pt.setColor(0xFFFFFFFF);
				pt.setTextAlign(Paint.Align.CENTER);
				c.drawText(tl, xt, tp - px(10.5f), pt);
				pt.setTypeface(Typeface.DEFAULT);
				pt.setColor(dim);
				pt.setTextAlign(Paint.Align.LEFT);
				c.drawText(Tr.t("measured", "măsurat"), l + px(4), tp + px(12), pt);
				pt.setTextAlign(Paint.Align.RIGHT);
				c.drawText(Tr.t("forecast", "prognoză"), w - r - px(2), tp + px(12), pt);
				// The days under.
				pt.setTextAlign(Paint.Align.CENTER);
				int stepN = n > 20 ? 7 : n > 12 ? 2 : 1;
				for (int k = 0; k < n; k++) {
					int off = from + k - t;
					if (off % stepN != 0) continue;
					c.drawText(off > 0 ? "+" + off : String.valueOf(off), l + (k + 0.5f) * cw, h - px(5), pt);
				}
			}
		};
		onTap(ch, new TapAt() {
			public void at(float x, float y) {
				float l = px(24), r = px(6), cw = (ch.getWidth() - l - r) / n;
				int k = (int) Math.floor((x - l) / cw);
				if (t >= 0 && k >= 0 && k < n) dayBubble(from + k, t);
			}
		});
		return ch;
	}

	// --- Season ---------------------------------------------------------------------------------

	private void season(LinearLayout body) {
		if (p.sow.length() != 10 || crop == null) {
			LinearLayout c = card();
			c.setPadding(px(16), px(16), px(16), px(16));
			c.addView(tv(Tr.t("Choose the parcel's crop and sowing date: the season's degree days, its stage and the comparisons start from them.",
					"Alege cultura și data semănatului parcelei: gradele-zile ale sezonului, stadiul și comparațiile pornesc de la ele."), 15, false, ink));
			TextView b = tv(Tr.t("Edit parcel", "Editează parcela"), 15, true, 0xFFFFFFFF);
			b.setGravity(Gravity.CENTER);
			b.setPadding(px(16), px(12), px(16), px(12));
			b.setBackground(Glass.pill(accent, 0, px(22), dp));
			b.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					host.edit(p);
				}
			});
			c.addView(b, wide(14));
			body.addView(c, wide(12));
			return;
		}
		String start = seasonStart, end = Agro.plus(today, 7);
		final List<double[]> now = days.cum(start, end, crop.base, crop.cap);
		final List<double[]> last = days.cum(Agro.plus(start, -365), Agro.plus(end, -365), crop.base, crop.cap);
		final int xt = Agro.between(start, today);
		double sumT = Double.NaN, lastT = Double.NaN;
		for (double[] q : now) if (q[0] <= xt) sumT = q[1];
		for (double[] q : last) if (q[0] <= xt) lastT = q[1];
		// The days ahead (+) or behind (−) last year.
		Integer adv = null;
		if (!Double.isNaN(sumT) && last.size() > 20) {
			int at = -1;
			for (double[] q : last) if (q[1] >= sumT) {
				at = (int) q[0];
				break;
			}
			if (at >= 0) adv = xt - at;
		}
		final double[] g = Agro.stageGdd(crop.type);
		final int st = Agro.stage(crop.type, Double.isNaN(sumT) ? 0 : sumT);
		final int next = st + 1 < g.length ? st + 1 : -1;

		LinearLayout c = card();
		LinearLayout top = row();
		top.addView(caps(Tr.t("Growing degree days (GDD)", "Grade-zile utile (GDD)"), dim), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		if (adv != null) top.addView(badge(adv > 0 ? "+" + adv + Tr.t(" days ahead", " zile avans termic") : adv < 0 ? -adv + Tr.t(" days behind", " zile întârziere")
				: Tr.t("as last year", "ca anul trecut"), adv >= 0 ? G : AMB));
		c.addView(top);
		LinearLayout v = row();
		v.setGravity(Gravity.BOTTOM);
		v.addView(tv(Double.isNaN(sumT) ? "–" : String.valueOf(Math.round(sumT)), 26, true, ink));
		TextView un = tv("  °C·" + Tr.t("days", "zile") + (Double.isNaN(lastT) ? "" : " · " + Tr.t("last year ", "anul trecut ") + Math.round(lastT)), 14, false, dim);
		v.addView(un);
		c.addView(v, wide(6));
		final double lineG = next >= 0 ? g[next] : Double.NaN;
		final String lineL = next >= 0 ? Agro.stageNames(crop.type)[next] + " ≈ " + Math.round(lineG) : "";
		View chart = new View(a) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			protected void onDraw(Canvas cv) {
				if (now.isEmpty()) return;
				float w = getWidth(), h = getHeight(), l = px(34), r = px(8), tp = px(10), bt = h - px(20);
				double xmax = Math.max(1, now.get(now.size() - 1)[0]), ymax = 50;
				for (double[] q : now) ymax = Math.max(ymax, q[1]);
				for (double[] q : last) if (q[0] <= xmax) ymax = Math.max(ymax, q[1]);
				if (!Double.isNaN(lineG) && lineG < ymax * 1.6) ymax = Math.max(ymax, lineG);
				double step = ymax > 1500 ? 500 : ymax > 600 ? 200 : ymax > 250 ? 100 : 50;
				ymax = Math.ceil(ymax * 1.05 / step) * step;
				pt.setTextSize(11 * dp * fs);
				pt.setStrokeWidth(Math.max(1, dp * 0.8f));
				for (double y0 = 0; y0 <= ymax + 0.1; y0 += step) {
					float y = (float) (bt - (bt - tp) * y0 / ymax);
					pt.setColor(Glass.edge(dark));
					cv.drawLine(l, y, w - r, y, pt);
					pt.setColor(dim);
					pt.setTextAlign(Paint.Align.RIGHT);
					cv.drawText(String.valueOf(Math.round(y0)), l - px(4), y + px(4), pt);
				}
				float kx = (float) ((w - l - r) / xmax);
				// The next stage's threshold.
				if (!Double.isNaN(lineG) && lineG <= ymax) {
					float y = (float) (bt - (bt - tp) * lineG / ymax);
					pt.setColor(Glass.alpha(B, 0.8f));
					pt.setStrokeWidth(1.2f * dp);
					cv.drawLine(l, y, w - r, y, pt);
					pt.setColor(B);
					pt.setTextAlign(Paint.Align.LEFT);
					cv.drawText(lineL, l + px(6), y - px(4), pt);
				}
				// Last year, dashed.
				Path ly = new Path();
				boolean f = false;
				for (double[] q : last) {
					if (q[0] > xmax) break;
					float x = l + (float) q[0] * kx, y = (float) (bt - (bt - tp) * q[1] / ymax);
					if (!f) ly.moveTo(x, y);
					else ly.lineTo(x, y);
					f = true;
				}
				pt.setStyle(Paint.Style.STROKE);
				pt.setStrokeWidth(1.8f * dp);
				pt.setColor(dim);
				pt.setPathEffect(new DashPathEffect(new float[] { px(6), px(4) }, 0));
				cv.drawPath(ly, pt);
				pt.setPathEffect(null);
				// This season: an area up to today, dashed ahead.
				Path line = new Path(), area = new Path(), ahead = new Path();
				f = false;
				boolean fa = false;
				float lx = l, lyy = bt;
				for (double[] q : now) {
					float x = l + (float) q[0] * kx, y = (float) (bt - (bt - tp) * q[1] / ymax);
					if (q[0] <= xt) {
						if (!f) {
							line.moveTo(x, y);
							area.moveTo(x, bt);
							area.lineTo(x, y);
						} else {
							line.lineTo(x, y);
							area.lineTo(x, y);
						}
						f = true;
						lx = x;
						lyy = y;
					}
					if (q[0] >= xt) {
						if (!fa) ahead.moveTo(x, y);
						else ahead.lineTo(x, y);
						fa = true;
					}
				}
				area.lineTo(lx, bt);
				area.close();
				pt.setStyle(Paint.Style.FILL);
				pt.setShader(new LinearGradient(0, tp, 0, bt, Glass.alpha(G, 0.4f), Glass.alpha(G, 0.02f), Shader.TileMode.CLAMP));
				cv.drawPath(area, pt);
				pt.setShader(null);
				pt.setStyle(Paint.Style.STROKE);
				pt.setStrokeWidth(2.6f * dp);
				pt.setColor(G);
				cv.drawPath(line, pt);
				pt.setPathEffect(new DashPathEffect(new float[] { px(5), px(4) }, 0));
				cv.drawPath(ahead, pt);
				// Today.
				float x = l + xt * kx;
				pt.setColor(ink);
				pt.setStrokeWidth(1.3f * dp);
				pt.setPathEffect(new DashPathEffect(new float[] { px(3), px(3) }, 0));
				cv.drawLine(x, tp, x, bt, pt);
				pt.setPathEffect(null);
				pt.setStyle(Paint.Style.FILL);
				pt.setColor(0xFFFFFFFF);
				cv.drawCircle(lx, lyy, px(5), pt);
				pt.setStyle(Paint.Style.STROKE);
				pt.setStrokeWidth(2 * dp);
				pt.setColor(G);
				cv.drawCircle(lx, lyy, px(5), pt);
				// The dates under.
				pt.setStyle(Paint.Style.FILL);
				pt.setColor(dim);
				pt.setTextAlign(Paint.Align.LEFT);
				cv.drawText(dayMonth(seasonStart), l, h - px(5), pt);
				pt.setTextAlign(Paint.Align.CENTER);
				if (x - l > px(60) && w - r - x > px(30)) cv.drawText(Tr.t("today", "azi"), x, h - px(5), pt);
				pt.setTextAlign(Paint.Align.RIGHT);
				cv.drawText(dayMonth(Agro.plus(seasonStart, (int) xmax)), w - r, h - px(5), pt);
			}
		};
		final View chartF = chart;
		onTap(chart, new TapAt() {
			public void at(float x, float y) {
				float l = px(34), r = px(8);
				double xmax = Math.max(1, now.get(now.size() - 1)[0]);
				int q = (int) Math.round((x - l) / ((chartF.getWidth() - l - r) / xmax));
				if (q < 0 || q > xmax) return;
				gddBubble(q, now, last, xt);
			}
		});
		c.addView(chart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(200)));
		LinearLayout lg = row();
		lg.setPadding(0, px(6), 0, 0);
		lg.addView(dot(G, Tr.t("this season", "sezonul acesta")));
		lg.addView(dot(dim, Tr.t("last year", "anul trecut")));
		c.addView(lg);
		c.addView(tv(crop.name() + Tr.t(": base temperature ", ": temperatura de bază ") + Math.round(crop.base) + " °C" + Tr.t(", ceiling ", ", plafon ")
				+ Math.round(crop.cap) + " °C" + Tr.t(", from ", ", de la ") + (seasonStart.equals(p.sow) ? Tr.t("the sowing date", "data semănatului") : dayMonth(seasonStart)) + ".", 12.5f,
				false, dim), wide(6));
		if (seasonMissing && xt > 26) c.addView(hint(Tr.t("The season's first days come from Open-Meteo's archive; they are downloaded with internet.",
				"Primele zile ale sezonului vin din arhiva Open-Meteo; se descarcă când este internet.")), wide(6));
		body.addView(c, wide(12));

		// The stage.
		body.addView(section(Tr.t("Estimated stage", "Stadiul estimat"), tv(Tr.t("from GDD", "după GDD"), 12.5f, false, dim)));
		LinearLayout sc = card();
		final String[] names = Agro.stageNames(crop.type);
		View steps = new View(a) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			protected void onDraw(Canvas cv) {
				int n = names.length;
				float w = getWidth(), cw = w / n, y = px(16), r = px(9);
				pt.setStrokeWidth(3 * dp);
				for (int k = 0; k < n - 1; k++) {
					pt.setColor(k < st ? G : Glass.disc(dark));
					cv.drawLine(cw * (k + 0.5f) + r, y, cw * (k + 1.5f) - r, y, pt);
				}
				pt.setTextAlign(Paint.Align.CENTER);
				for (int k = 0; k < n; k++) {
					float x = cw * (k + 0.5f);
					pt.setStyle(Paint.Style.FILL);
					if (k < st || (k == st && st == n - 1)) {
						pt.setColor(G);
						cv.drawCircle(x, y, r, pt);
						pt.setColor(0xFFFFFFFF);
						pt.setStyle(Paint.Style.STROKE);
						pt.setStrokeWidth(2 * dp);
						cv.drawLine(x - r * 0.45f, y, x - r * 0.1f, y + r * 0.35f, pt);
						cv.drawLine(x - r * 0.1f, y + r * 0.35f, x + r * 0.5f, y - r * 0.35f, pt);
					} else if (k == st) {
						pt.setColor(Glass.alpha(accent, 0.35f));
						cv.drawCircle(x, y, r * 1.45f, pt);
						pt.setColor(accent);
						cv.drawCircle(x, y, r, pt);
					} else {
						pt.setColor(Glass.disc(dark));
						cv.drawCircle(x, y, r * 0.8f, pt);
					}
					pt.setStyle(Paint.Style.FILL);
					pt.setTextSize(11.5f * dp * fs);
					pt.setTypeface(k == st ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
					pt.setColor(k == st ? ink : dim);
					String s = names[k];
					while (pt.measureText(s) > cw - px(2) && s.length() > 3) s = s.substring(0, s.length() - 2) + "…";
					cv.drawText(s, x, y + px(30), pt);
				}
			}
		};
		sc.addView(steps, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(52)));
		String est;
		if (now.isEmpty()) est = Tr.t("No data for the season yet.", "Încă nu sunt date pentru sezon.");
		else if (next < 0) est = Tr.t("The last stage is reached.", "Ultimul stadiu este atins.");
		else {
			String when = null;
			for (double[] q : now) if (q[0] > xt && q[1] >= g[next]) {
				when = dayMonth(Agro.plus(seasonStart, (int) q[0]));
				break;
			}
			if (when == null) {
				// After the forecast: the mean of the last 14 days.
				double s14 = 0;
				int n14 = 0;
				for (double[] q : now) if (q[0] > xt - 14 && q[0] <= xt) n14++;
				double a14 = Double.NaN, b14 = Double.NaN;
				for (double[] q : now) {
					if (q[0] <= xt - 14) a14 = q[1];
					if (q[0] <= xt) b14 = q[1];
				}
				if (!Double.isNaN(b14)) s14 = b14 - (Double.isNaN(a14) ? 0 : a14);
				double perDay = n14 > 0 ? s14 / n14 : 0, endSum = now.get(now.size() - 1)[1];
				int left = perDay < 0.3 ? -1 : (int) Math.ceil((g[next] - endSum) / perDay);
				if (left >= 0 && left < 200) when = dayMonth(Agro.plus(seasonStart, (int) now.get(now.size() - 1)[0] + left));
			}
			est = names[next] + Tr.t(" starts at ≈ ", " începe la ≈ ") + Math.round(g[next]) + " GDD, "
					+ (when != null ? Tr.t("estimated on ", "estimat pe ") + when : Tr.t("not to be estimated now (cold)", "nu se poate estima acum (frig)"))
					+ (crop.type == Agro.WINTER && wintry() ? Tr.t(" or after the winter rest", " sau după repausul de iarnă") : "") + ".";
		}
		sc.addView(tv(est, 13.5f, false, dim), wide(6));
		body.addView(sc);

		// Heat stress, last year, frost days.
		int heat = 0, frost = 0;
		for (int k = 0; k < days.date.size(); k++) {
			String dd = days.date.get(k);
			if (dd.compareTo(seasonStart) < 0 || dd.compareTo(today) > 0) continue;
			if (days.v.get(k)[0] > 32) heat++;
			if (days.v.get(k)[1] < -5) frost++;
		}
		LinearLayout three = new LinearLayout(a);
		three.addView(kpi(Tr.t("Heat stress", "Stres termic"), heat + Tr.t(" days", " zile"), Tr.t("over 32 °C", "peste 32 °C"), heat > 0 ? O : ink),
				new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
		double dl = sumT - lastT;
		three.addView(kpi(Tr.t("Vs. last year", "Vs. anul trecut"), Double.isNaN(dl) ? "–" : (dl >= 0 ? "+" : "−") + Math.round(Math.abs(dl)),
				Tr.t("GDD on the same date", "GDD la aceeași dată"), Double.isNaN(dl) ? ink : dl >= 0 ? G : AMB), weight(8));
		three.addView(kpi(Tr.t("Hard frost days", "Zile de ger"), String.valueOf(frost), Tr.t("under −5 °C", "sub −5 °C"), frost > 0 ? B : ink), weight(8));
		body.addView(three, wide(12));
	}

	/** November to February. */
	private boolean wintry() {
		int m = Integer.parseInt(today.substring(5, 7));
		return m >= 11 || m <= 2;
	}

	// --- Risks --------------------------------------------------------------------------------

	/** The 4 risks: { percent, main word, the line under } for fungi, ground frost, heat, wind / hail. */
	Object[][] risks() {
		Object[][] out = new Object[4][];
		int[] f = Agro.fungalRun(d, cur, 72);
		int fp = Agro.fungalPct(f[0]);
		out[0] = new Object[] { fp, crop != null ? crop.disease() : Tr.t("Fungal diseases", "Boli fungice"), f[0] + Tr.t(" h wet leaf", " h frunză umedă") };
		double[] fr = Agro.frost(d, cur, 72);
		int frp = Agro.frostPct(fr[0]);
		out[1] = new Object[] { frp, frp >= 67 ? Tr.t("Frost", "Îngheț") : frp >= 34 ? Tr.t("Hoarfrost", "Brumă") : Tr.t("No risk", "Fără risc"),
				fr[1] >= 0 ? dayHour((int) fr[1]) + " · " + Tr.t("soil ", "sol ") + deg(fr[0]) + "C" : "" };
		double[] mt = Agro.maxTemp(d, cur, 168);
		int hp = Agro.heatPct(mt[0]);
		out[2] = new Object[] { hp, word(hp), Tr.t("max ", "max ") + deg(mt[0]) + Tr.t(" in 7 days", " în 7 zile") };
		double[] mg = Agro.maxGust(d, cur, 72), sm = Farm.storm(d, cur, 72);
		int wp = Agro.windPct(mg[0], sm[0]);
		out[3] = new Object[] { wp, word(wp), Tr.t("gusts max ", "rafale max ") + kmh(mg[0]) };
		return out;
	}

	String word(int pct) {
		return pct <= 0 ? Tr.t("No risk", "Fără risc") : pct < 34 ? Tr.t("Low", "Scăzut") : pct < 67 ? Tr.t("Moderate", "Moderat") : Tr.t("High", "Ridicat");
	}

	static final String[] RISK_EN = { "Fungal diseases", "Ground frost", "Heat stress", "Wind · hail" };
	static final String[] RISK_RO = { "Boli fungice", "Îngheț la sol", "Stres termic", "Vânt · grindină" };

	private void risks(LinearLayout body) {
		body.addView(section(Tr.t("Risks 72 h", "Riscuri 72 h"), tv(Tr.t("tap a card", "atinge un card"), 12.5f, false, dim)));
		if (cur < 0) return;
		Object[][] rk = risks();
		for (int row0 = 0; row0 < 2; row0++) {
			LinearLayout r = new LinearLayout(a);
			for (int k = 0; k < 2; k++) {
				final int i = row0 * 2 + k;
				View c = ring(Tr.t(RISK_EN[i], RISK_RO[i]), (Integer) rk[i][0], (String) rk[i][1], (String) rk[i][2]);
				c.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						detail(i);
					}
				});
				r.addView(c, k == 0 ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1) : weight(10));
			}
			body.addView(r, wide(row0 == 0 ? 0 : 10));
		}
		body.addView(section(Tr.t("Coming up", "Urmează"), null));
		LinearLayout list = card();
		List<Object[]> ev = events();
		if (ev.isEmpty()) list.addView(tv(Tr.t("Nothing special in the next 72 hours.", "Nimic deosebit în următoarele 72 de ore."), 14, false, dim));
		for (int k = 0; k < ev.size(); k++) {
			Object[] e = ev.get(k);
			LinearLayout r = row();
			r.setGravity(Gravity.TOP);
			View d0 = new View(a);
			d0.setBackground(Glass.pill((Integer) e[1], 0, px(5), dp));
			LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(10), px(10));
			dl.topMargin = px(5);
			r.addView(d0, dl);
			LinearLayout t = col();
			t.addView(tv((String) e[2], 15, true, ink));
			t.addView(tv((String) e[3], 12.5f, false, dim), wide(2));
			LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
			tl.leftMargin = px(12);
			r.addView(t, tl);
			TextView when = tv((String) e[4], 13, true, dim);
			when.setGravity(Gravity.RIGHT);
			r.addView(when);
			list.addView(r, wide(k == 0 ? 0 : 12));
		}
		body.addView(list);
	}

	/** The next 72 hours' events: { hour index, colour, title, the line under, when }, in time order. */
	List<Object[]> events() {
		List<Object[]> out = new ArrayList<Object[]>();
		int end = Math.min(d.size(), cur + 72);
		for (int k = cur; k < end; k++) {
			double s = d.soilT[k];
			if (!Double.isNaN(s) && s <= 0) {
				out.add(new Object[] { k, AMB, Tr.t("Ground frost", "Brumă la sol"), Tr.t("air ", "aer ") + deg(d.temp[k]) + "C, " + Tr.t("soil ", "sol ") + deg(s) + "C", dayHour(k) });
				break;
			}
		}
		int[] f = Agro.fungalRun(d, cur, 72);
		if (f[0] >= 6) {
			double h = 0, lo = 99, hi = -99;
			for (int k = f[1]; k < f[2]; k++) {
				h = Math.max(h, nz(d.hum[k]));
				lo = Math.min(lo, d.temp[k]);
				hi = Math.max(hi, d.temp[k]);
			}
			out.add(new Object[] { f[1], f[0] >= 12 ? R : AMB, Tr.t("Infection: ", "Infecție: ") + (crop != null ? crop.disease().toLowerCase(locale()) : Tr.t("fungi", "ciuperci")),
					Tr.t("RH ", "UR ") + Math.round(h) + " %, " + deg(lo) + "–" + deg(hi) + "C", dayHour(f[1]) + " – " + dayHour(f[2] - 1) });
		}
		for (int k = cur; k < end; k++) {
			if (nz(d.gust[k]) >= 50) {
				out.add(new Object[] { k, R, Tr.t("Gusts ", "Rafale ") + kmh(d.gust[k]), Tr.t("no spraying, secure what can fly", "fără stropit, asigură ce poate zbura"), dayHour(k) });
				break;
			}
		}
		for (int k = cur; k < end; k++) {
			if (nz(d.temp[k]) >= 32) {
				out.add(new Object[] { k, O, Tr.t("Heat ", "Arșiță ") + deg(d.temp[k]) + "C", Tr.t("no treatments in the hot hours", "fără tratamente în orele calde"), dayHour(k) });
				break;
			}
		}
		// Rainy days (4 mm and more).
		int t = d.dayIndex(today);
		for (int k = Math.max(0, t); t >= 0 && k <= t + 3 && k < d.dDate.length; k++) {
			if (nz(d.dRain[k]) < 4) continue;
			int hi = d.index(d.dDate[k] + "T00:00");
			if (hi < cur) hi = cur;
			out.add(new Object[] { hi, B, Tr.t("Rain ", "Ploaie ") + mm(d.dRain[k]), Tr.t("the field is hard to work", "traficabilitate redusă"),
					d.dDate[k].equals(today) ? Tr.t("Today", "Azi") : weekday(d.dDate[k]) });
		}
		java.util.Collections.sort(out, new java.util.Comparator<Object[]>() {
			public int compare(Object[] x, Object[] y) {
				return ((Integer) x[0]).compareTo((Integer) y[0]);
			}
		});
		return out;
	}

	private View ring(String title, final int pct, String main, String sub) {
		LinearLayout c = card();
		c.setGravity(Gravity.CENTER_HORIZONTAL);
		final int color = pctColor(pct);
		TextView t = caps(title, dim);
		t.setGravity(Gravity.CENTER);
		c.addView(t);
		View r = new View(a) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			protected void onDraw(Canvas cv) {
				float w = getWidth(), h = getHeight(), rr = Math.min(w, h) / 2 - px(6);
				RectF o = new RectF(w / 2 - rr, h / 2 - rr, w / 2 + rr, h / 2 + rr);
				pt.setStyle(Paint.Style.STROKE);
				pt.setStrokeWidth(px(7));
				pt.setStrokeCap(Paint.Cap.ROUND);
				pt.setColor(Glass.disc(dark));
				cv.drawCircle(w / 2, h / 2, rr, pt);
				pt.setColor(color);
				if (pct > 0) cv.drawArc(o, -90, 360f * pct / 100, false, pt);
				else cv.drawPoint(w / 2, h / 2 - rr, pt);
				pt.setStyle(Paint.Style.FILL);
				pt.setColor(ink);
				pt.setTypeface(Typeface.DEFAULT_BOLD);
				pt.setTextSize(19 * dp * fs);
				pt.setTextAlign(Paint.Align.CENTER);
				cv.drawText(pct < 0 ? "–" : pct + "%", w / 2, h / 2 + px(7), pt);
			}
		};
		c.addView(r, new LinearLayout.LayoutParams(px(96), px(96)));
		TextView m = tv(main, 16, true, color);
		m.setGravity(Gravity.CENTER);
		c.addView(m, wide(4));
		FitText s = tv(sub, 12, false, dim);
		s.setGravity(Gravity.CENTER);
		c.addView(s, wide(2));
		return c;
	}

	/** The bottom sheet of risk K: what it is, its critical hours, what to do, how it is worked out. */
	private void detail(int k) {
		Object[][] rk = risks();
		int pct = (Integer) rk[k][0];
		int color = pctColor(pct);
		String lvl = pct < 34 ? Tr.t("LOW RISK", "RISC SCĂZUT") : pct < 67 ? Tr.t("MODERATE RISK", "RISC MODERAT") : Tr.t("HIGH RISK", "RISC RIDICAT");
		String title, text, b1l, b1, b2l, b2, todo, calc;
		String cn = crop != null ? crop.name().toLowerCase(locale()) : "";
		if (k == 0) {
			int[] f = Agro.fungalRun(d, cur, 72);
			title = crop != null ? crop.disease() + Tr.t(" on ", " pe ") + cn : Tr.t("Fungal diseases", "Boli fungice");
			if (f[0] > 0) {
				double lo = 99, hi = -99;
				for (int i = f[1]; i < f[2]; i++) {
					lo = Math.min(lo, d.temp[i]);
					hi = Math.max(hi, d.temp[i]);
				}
				text = Tr.t("The leaves stay wet ", "Frunzele vor sta ude ") + f[0] + Tr.t(" hours in a row, at ", " ore la rând, la ") + deg(lo) + "–" + deg(hi)
						+ Tr.t("C. That is how the fungus takes hold on the leaves.", "C. Așa se prinde ciuperca pe frunze.");
			} else text = Tr.t("No long wet spell in the next 72 hours.", "Nicio perioadă lungă cu frunza udă în următoarele 72 de ore.");
			b1l = Tr.t("Critical interval", "Interval critic");
			b1 = f[0] > 0 ? dayHour(f[1]) + " – " + dayHour(f[2] - 1) : "–";
			b2l = Tr.t("Wet leaf hours", "Ore frunză umedă");
			b2 = f[0] + " h · " + Tr.t("threshold 12 h", "prag 12 h");
			int[] w = Agro.window(d, cur, 24);
			String win = w != null && Agro.level(w[2]) == 0 ? " (" + clock(w[0]) + "–" + Texts.clock(d.hourOf(Math.min(d.size() - 1, w[1])), clk12) + ", ΔT "
					+ degDelta(Agro.deltaT(d, w[0])) + ")" : "";
			todo = pct >= 67 ? Tr.t("Preventive treatment before the wet spell, in a green window", "Tratament preventiv înainte de perioada umedă, într-o fereastră verde") + win + "."
					: pct >= 34 ? Tr.t("Watch the parcel; a treatment may be needed if the wet spell lasts.", "Urmărește parcela; poate fi nevoie de tratament dacă umezeala durează.")
							: Tr.t("No treatment needed for now.", "Nu e nevoie de tratament deocamdată.");
			calc = Tr.t("Calculation: wet leaf = rain, dew or humidity ≥ 90 %, at 12–26 °C; 12 h in a row or more = high risk.",
					"Calcul: frunză umedă = ploaie, rouă sau UR ≥ 90 %, la 12–26 °C; 12 h la rând sau mai mult = risc ridicat.");
		} else if (k == 1) {
			double[] fr = Agro.frost(d, cur, 72);
			title = Tr.t("Ground frost", "Îngheț la sol");
			text = Tr.t("At the soil surface it can be ", "La suprafața solului pot fi ") + deg(fr[0]) + "C" + (fr[1] >= 0 ? " (" + dayHour((int) fr[1]) + ")" : "")
					+ Tr.t(", with ", ", cu ") + deg(fr[2]) + "C" + Tr.t(" in the air at the lowest. The soil can be 2–4 °C colder than the air.", " în aer cel mai frig. Solul poate fi cu 2–4 °C mai rece decât aerul.");
			b1l = Tr.t("Coldest hour", "Ora cea mai rece");
			b1 = fr[1] >= 0 ? dayHour((int) fr[1]) : "–";
			b2l = Tr.t("Soil / air", "Sol / aer");
			b2 = deg(fr[0]) + " / " + deg(fr[2]) + "C";
			todo = pct >= 34 ? Tr.t("Protect young plants and sensitive crops; no treatments in the cold hours.", "Protejează plantele tinere și culturile sensibile; fără tratamente în orele reci.")
					: Tr.t("Nothing to do.", "Nimic de făcut.");
			calc = Tr.t("Calculation: the soil-surface temperature from Open-Meteo (estimate); +4 °C no risk, −2 °C and lower certain.",
					"Calcul: temperatura la suprafața solului de la Open-Meteo (estimare); +4 °C fără risc, −2 °C și mai jos sigur.");
		} else if (k == 2) {
			double[] mt = Agro.maxTemp(d, cur, 168);
			int over = 0;
			for (int i = cur; i < Math.min(d.size(), cur + 168); i++) if (nz(d.temp[i]) > 32) over++;
			title = Tr.t("Heat stress", "Stres termic");
			text = Tr.t("The highest temperature of the next 7 days: ", "Cea mai mare temperatură din următoarele 7 zile: ") + deg(mt[0]) + "C"
					+ (mt[1] >= 0 ? " (" + dayHour((int) mt[1]) + ")" : "") + Tr.t(". Over 32 °C the crop suffers", ". Peste 32 °C cultura suferă")
					+ (crop != null ? Tr.t(", critically at ", ", critic la ") + crop.heatStage() : "") + ".";
			b1l = Tr.t("Hottest hour", "Ora cea mai caldă");
			b1 = mt[1] >= 0 ? dayHour((int) mt[1]) : "–";
			b2l = Tr.t("Hours over 32 °C", "Ore peste 32 °C");
			b2 = over + " h";
			todo = pct >= 34 ? Tr.t("Irrigate if you can and avoid treatments in the hot hours.", "Irigă dacă poți și evită tratamentele în orele calde.") : Tr.t("Nothing to do.", "Nimic de făcut.");
			calc = Tr.t("Calculation: the highest air temperature; 28 °C no risk, 34 °C and over certain.", "Calcul: temperatura maximă a aerului; 28 °C fără risc, 34 °C și peste sigur.");
		} else {
			double[] mg = Agro.maxGust(d, cur, 72), sm = Farm.storm(d, cur, 72);
			title = Tr.t("Wind · hail", "Vânt · grindină");
			text = Tr.t("The strongest gust of the next 72 hours: ", "Cea mai puternică rafală din următoarele 72 de ore: ") + kmh(mg[0])
					+ (mg[1] >= 0 ? " (" + dayHour((int) mg[1]) + ")" : "") + Tr.t("; storm energy (CAPE) up to ", "; energia de furtună (CAPE) până la ")
					+ (Double.isNaN(sm[0]) ? "–" : Math.round(sm[0]) + " J/kg") + ".";
			b1l = Tr.t("Strongest gust", "Rafala maximă");
			b1 = kmh(mg[0]);
			b2l = Tr.t("Storm energy", "Energie furtună");
			b2 = Double.isNaN(sm[0]) ? "–" : Math.round(sm[0]) + " J/kg";
			todo = pct >= 34 ? Tr.t("Secure what the wind can take; no spraying in the gusts.", "Asigură ce poate lua vântul; fără stropit în rafale.") : Tr.t("Nothing to do.", "Nimic de făcut.");
			calc = Tr.t("Calculation: gusts from 20 km/h and CAPE over 300 J/kg count; 75 km/h or 2500 J/kg = certain.",
					"Calcul: contează rafalele de la 20 km/h și CAPE peste 300 J/kg; 75 km/h sau 2500 J/kg = sigur.");
		}
		final FrameLayout scrim = new FrameLayout(a);
		scrim.setBackgroundColor(Glass.scrim(dark) | 0x30000000);
		scrim.setClickable(true);
		final LinearLayout sh = col();
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(Theme.surface(dark));
		float rr = px(22);
		bg.setCornerRadii(new float[] { rr, rr, rr, rr, 0, 0, 0, 0 });
		sh.setBackground(bg);
		sh.setPadding(px(18), px(10), px(18), px(20));
		sh.setClickable(true);
		View grip = new View(a);
		grip.setBackground(Glass.pill(Glass.edge(dark), 0, px(3), dp));
		LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(px(40), px(5));
		gl.gravity = Gravity.CENTER_HORIZONTAL;
		sh.addView(grip, gl);
		LinearLayout top = row();
		top.addView(badge(lvl + " · " + Math.max(0, pct) + "%", color));
		top.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
		View x = new View(a);
		x.setBackground(Glass.closeIcon(dark, dp));
		x.setContentDescription(Tr.t("Close", "Închide"));
		top.addView(x, new LinearLayout.LayoutParams(px(36), px(36)));
		sh.addView(top, wide(8));
		sh.addView(tv(title, 21, true, ink), wide(8));
		sh.addView(tv(text, 14.5f, false, dim), wide(8));
		LinearLayout two = new LinearLayout(a);
		two.addView(box(b1l, b1), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
		two.addView(box(b2l, b2), weight(10));
		sh.addView(two, wide(12));
		LinearLayout td = col();
		td.setBackground(Glass.pill(Glass.alpha(G, 0.10f), G, px(14), dp));
		td.setPadding(px(14), px(10), px(14), px(12));
		td.addView(caps(Tr.t("What you can do", "Ce poți face"), G));
		td.addView(tv(todo, 14.5f, false, ink), wide(4));
		sh.addView(td, wide(12));
		sh.addView(tv(calc, 11.5f, false, dim), wide(10));
		ScrollView sv = new ScrollView(a);
		sv.addView(sh);
		int w = a.getResources().getDisplayMetrics().widthPixels;
		FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(Math.min(w, px(600)), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
		scrim.addView(sv, sl);
		View.OnClickListener close = new View.OnClickListener() {
			public void onClick(View v) {
				root.removeView(scrim);
			}
		};
		scrim.setOnClickListener(close);
		x.setOnClickListener(close);
		root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		Log.i(a, "FERMIER", "statistici: detaliu risc " + k);
	}

	/** Closes an open risk sheet; true when there was one. */
	boolean closeSheet() {
		if (root == null || root.getChildCount() < 2) return false;
		root.removeViewAt(root.getChildCount() - 1);
		return true;
	}

	private View box(String label, String value) {
		LinearLayout c = col();
		c.setBackground(Glass.cardBg(dark, px(12), dp));
		c.setPadding(px(12), px(9), px(12), px(10));
		c.addView(caps(label, dim));
		c.addView(tv(value, 16, true, ink), wide(4));
		return c;
	}

	private static double nz(double v) {
		return Double.isNaN(v) ? 0 : v;
	}
}
