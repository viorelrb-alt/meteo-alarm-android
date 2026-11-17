package ro.meteoalarm.android;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ro.meteoalarm.android.core.Agro;
import ro.meteoalarm.android.core.Theme;
import ro.meteoalarm.android.core.Tr;

/**
 * v2.001 (Viorel, mockups 6–8): the pencil of a parcel: its name, its outline (moved on the
 * map), the sowing date, the area (from the corners), the crop picked from a list ordered by the
 * sowing date's season ("Another crop…" with its type), a warning for a crop out of season, and
 * the thresholds the statistics use.
 */
final class EditPage {
	interface Host {
		void close();

		void save(Parcels.Parcel p);

		void map(Parcels.Parcel p);
	}

	private final Activity a;
	private final Host host;
	private final StatsPage fmt;
	private final boolean dark;
	private final float dp, fs;
	/** v2.010: the texts' factor for the page's width (412 dp = 1; smaller phones a little less). */
	private float wk = 1;
	private final int ink, dim, accent, G, Y;
	private FrameLayout root;
	/** The parcel as edited (saved only with Save). */
	Parcels.Parcel p;
	private EditText name;

	EditPage(Activity a, Host host, StatsPage fmt, boolean dark) {
		this.a = a;
		this.host = host;
		this.fmt = fmt;
		this.dark = dark;
		dp = a.getResources().getDisplayMetrics().density;
		fs = TextSize.get(a);
		ink = Theme.ink(dark);
		dim = Theme.dim(dark);
		accent = Theme.accent(dark);
		G = dark ? 0xFF22C55E : 0xFF16A34A;
		Y = dark ? 0xFFF59E0B : 0xFFD97706;
	}

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

	private TextView caps(String s, int color) {
		FitText t = tv(s.toUpperCase(Locale.getDefault()), 12.5f, true, color);
		t.setLetterSpacing(0.06f);
		t.one(0.6f);
		return t;
	}

	private LinearLayout col() {
		LinearLayout l = new LinearLayout(a);
		l.setOrientation(LinearLayout.VERTICAL);
		return l;
	}

	private LinearLayout row() {
		LinearLayout l = new LinearLayout(a);
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

	private TextView pill(String s, boolean main, View.OnClickListener l) {
		FitText t = tv(s, 15, true, main ? 0xFFFFFFFF : ink);
		t.setGravity(Gravity.CENTER);
		t.setPadding(px(16), px(10), px(16), px(10));
		t.setBackground(main ? Glass.pill(accent, 0, px(22), dp) : Glass.pill(Glass.disc(dark), Glass.edge(dark), px(22), dp));
		t.setOnClickListener(l);
		return t;
	}

	private View section(String title, String right) {
		LinearLayout r = row();
		r.setPadding(0, px(18), 0, px(8));
		r.addView(caps(title, dim));
		View line = new View(a);
		line.setBackgroundColor(Glass.edge(dark));
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.max(1, px(1)), 1);
		lp.leftMargin = px(10);
		lp.rightMargin = right != null ? px(10) : 0;
		r.addView(line, lp);
		if (right != null) r.addView(tv(right, 12.5f, false, dim));
		return r;
	}

	/** The page of parcel P (a copy is edited) in ROOT. */
	void show(FrameLayout root, Parcels.Parcel src) {
		widthK();
		this.root = root;
		if (p != null && p.id == src.id) keepName();
		if (p == null || p.id != src.id) {
			p = Parcels.Parcel.of(src.line());
			if (p == null) p = src;
		}
		draw();
	}

	private void draw() {
		root.removeAllViews();
		LinearLayout page = col();
		LinearLayout top = row();
		top.setPadding(px(14), px(10), px(14), px(10));
		top.setBackground(Glass.panelBg(dark, 0, dp));
		View x = new View(a);
		x.setBackground(Glass.closeIcon(dark, dp));
		x.setContentDescription(Tr.t("Close", "Închide"));
		x.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				host.close();
			}
		});
		top.addView(x, new LinearLayout.LayoutParams(px(40), px(40)));
		FitText t = tv(Tr.t("Edit parcel", "Editează parcela"), 21, true, ink);
		t.one(0.6f);
		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		tl.leftMargin = px(12);
		top.addView(t, tl);
		top.addView(pill("✓ " + Tr.t("Save", "Salvează"), true, new View.OnClickListener() {
			public void onClick(View v) {
				String n = name.getText().toString().trim();
				if (n.length() > 0) p.name = n;
				host.save(p);
			}
		}));
		page.addView(top);

		LinearLayout body = col();
		body.setPadding(px(14), px(12), px(14), px(24));
		// The name.
		LinearLayout nc = card();
		LinearLayout nr = row();
		nr.addView(caps(Tr.t("Name", "Nume"), dim));
		name = new EditText(a);
		name.setText(p.name);
		name.setSingleLine(true);
		name.setTextColor(ink);
		name.setTextSize(17 * fs);
		name.setTypeface(Typeface.DEFAULT_BOLD);
		name.setBackground(null);
		name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		name.setPadding(px(10), px(4), px(4), px(4));
		nr.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		nc.addView(nr);
		nc.addView(tv(String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon), 12.5f, false, dim), wide(2));
		body.addView(nc);

		// The outline.
		body.addView(section(Tr.t("Outline on the map", "Conturul pe hartă"), null));
		LinearLayout oc = card();
		double per = p.perimeter();
		int n = p.corners == null ? 0 : p.corners.length / 2;
		oc.addView(tv(n == 0 ? Tr.t("No corners yet: only the pin.", "Încă fără colțuri: doar acul.")
				: n + Tr.t(" corners · perimeter ", " colțuri · perimetru ") + fmt.km(per), 15, true, ink));
		oc.addView(tv(Tr.t("On the map the corners move with a finger; + Point, Circle and the long press work there too. The weather comes from the parcel's centre.",
				"Pe hartă colțurile se mută cu degetul; + Punct, Cerc și apăsarea lungă merg tot acolo. Vremea vine din centrul parcelei."), 13, false, dim), wide(4));
		TextView mapB = pill(Tr.t("Change on the map", "Modifică pe hartă"), false, new View.OnClickListener() {
			public void onClick(View v) {
				String nn = name.getText().toString().trim();
				if (nn.length() > 0) p.name = nn;
				host.map(p);
			}
		});
		LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		ml.topMargin = px(10);
		oc.addView(mapB, ml);
		body.addView(oc);

		// The sowing date and the area.
		LinearLayout two = new LinearLayout(a);
		LinearLayout dc = card();
		dc.addView(caps(Tr.t("Sowing date", "Data semănatului"), dim));
		FitText dt = tv(p.sow.length() == 10 ? fmt.longDate(p.sow) : Tr.t("choose…", "alege…"), 17, true, p.sow.length() == 10 ? ink : accent);
		dt.one(0.6f);
		dc.addView(dt, wide(6));
		dc.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				pickDate();
			}
		});
		two.addView(dc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3));
		LinearLayout ac = card();
		ac.addView(caps(Tr.t("Area", "Suprafață"), dim));
		// v2.010: the value on one row, under it where it comes from.
		FitText av = tv(fmt.hectares(p.hectares()), 17, true, ink);
		av.one(0.6f);
		ac.addView(av, wide(6));
		FitText as = tv(Tr.t("from the corners", "din colțuri"), 12.5f, false, dim);
		as.one(0.7f);
		ac.addView(as, wide(3));
		LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2);
		al.leftMargin = px(10);
		two.addView(ac, al);
		body.addView(two, wide(12));

		// The crop.
		Agro.Crop c = p.crop();
		LinearLayout cc = card();
		LinearLayout cr = row();
		cr.addView(fmt.sprout(accent), new LinearLayout.LayoutParams(px(36), px(36)));
		LinearLayout ct = col();
		ct.addView(caps(Tr.t("Crop", "Cultura"), dim));
		FitText cn = tv(c == null ? Tr.t("choose…", "alege…") : c.name() + "  ·  " + Agro.typeName(c.type), 16, true, c == null ? accent : ink);
		cn.rows(2);
		ct.addView(cn, wide(4));
		LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		cl.leftMargin = px(12);
		cr.addView(ct, cl);
		if (c != null && p.sow.length() == 10) {
			boolean ok = c.fits(month(p.sow));
			TextView b = tv(ok ? Tr.t("IN SEASON", "POTRIVITĂ ACUM") : Tr.t("OUT OF SEASON", "NU E SEZONUL"), 11.5f, true, ok ? G : Y);
			b.setPadding(px(8), px(3), px(8), px(3));
			b.setBackground(Glass.pill(Glass.alpha(ok ? G : Y, 0.16f), 0, px(10), dp));
			cr.addView(b);
		}
		cr.addView(tv("  ▾", 15, true, dim));
		cc.addView(cr);
		cc.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				picker();
			}
		});
		body.addView(cc, wide(12));

		// The thresholds.
		if (c != null) {
			LinearLayout th = card();
			th.addView(caps(Tr.t("Thresholds for ", "Praguri pentru ") + c.name(), dim));
			th.addView(tv(Tr.t("GDD base ", "GDD bază ") + Math.round(c.base) + " °C" + Tr.t(", ceiling ", ", plafon ") + Math.round(c.cap) + " °C · "
					+ Tr.t("disease watched: ", "boală urmărită: ") + c.disease().toLowerCase(Locale.getDefault()) + " · " + Tr.t("critical heat at ", "arșiță critică la ")
					+ c.heatStage() + " (> 32 °C) · " + Tr.t("sowing soil ", "sol pentru semănat ") + Math.round(c.soilMin) + " °C", 13.5f, false, dim), wide(6));
			body.addView(th, wide(12));
		}
		ScrollView sv = new ScrollView(a);
		int w = a.getResources().getDisplayMetrics().widthPixels;
		FrameLayout f = new FrameLayout(a);
		f.addView(body, new FrameLayout.LayoutParams(Math.min(w, px(TextSize.tablet(a) ? 720 : 600)), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
		sv.addView(f);
		page.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	private static int month(String iso) {
		return Integer.parseInt(iso.substring(5, 7));
	}

	private void keepName() {
		if (name == null) return;
		String n = name.getText().toString().trim();
		if (n.length() > 0) p.name = n;
	}

	private void pickDate() {
		keepName();
		Calendar now = Calendar.getInstance();
		int y = now.get(Calendar.YEAR), m = now.get(Calendar.MONTH), dd = now.get(Calendar.DAY_OF_MONTH);
		if (p.sow.length() == 10) {
			y = Integer.parseInt(p.sow.substring(0, 4));
			m = Integer.parseInt(p.sow.substring(5, 7)) - 1;
			dd = Integer.parseInt(p.sow.substring(8, 10));
		}
		DatePickerDialog d = new DatePickerDialog(a, new DatePickerDialog.OnDateSetListener() {
			public void onDateSet(DatePicker v, int yy, int mm, int day) {
				p.sow = String.format(Locale.US, "%04d-%02d-%02d", yy, mm + 1, day);
				Log.i(a, "FERMIER", "data semănatului: " + p.sow);
				draw();
			}
		}, y, m, dd);
		d.getDatePicker().setMaxDate(System.currentTimeMillis() + 60 * 86400000L);
		d.getDatePicker().setMinDate(System.currentTimeMillis() - 400 * 86400000L);
		Dlg.show(d);
	}

	// --- The crop list (mockup 7) ------------------------------------------------------------------

	private FrameLayout sheet;

	/** The month the list is ordered by: the sowing date's, else this one. */
	private int sowMonth() {
		return p.sow.length() == 10 ? month(p.sow) : Calendar.getInstance().get(Calendar.MONTH) + 1;
	}

	private void picker() {
		keepName();
		closeSheet();
		int m = sowMonth();
		final FrameLayout scrim = new FrameLayout(a);
		scrim.setBackgroundColor(0x60000000);
		scrim.setClickable(true);
		LinearLayout sh = col();
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(Theme.surface(dark));
		float rr = px(22);
		bg.setCornerRadii(new float[] { rr, rr, rr, rr, 0, 0, 0, 0 });
		sh.setBackground(bg);
		sh.setPadding(px(16), px(10), px(16), px(18));
		sh.setClickable(true);
		View grip = new View(a);
		grip.setBackground(Glass.pill(Glass.edge(dark), 0, px(3), dp));
		LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(px(40), px(5));
		gl.gravity = Gravity.CENTER_HORIZONTAL;
		sh.addView(grip, gl);
		LinearLayout top = row();
		LinearLayout tt = col();
		tt.addView(tv(Tr.t("Choose the crop", "Alege cultura"), 20, true, ink));
		tt.addView(tv(p.sow.length() == 10 ? Tr.t("ordered by the sowing date: ", "ordonată după data semănatului: ") + fmt.dayMonth(p.sow)
				: Tr.t("ordered by this month", "ordonată după luna aceasta"), 13, false, dim), wide(3));
		top.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		View x = new View(a);
		x.setBackground(Glass.closeIcon(dark, dp));
		x.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				closeSheet();
			}
		});
		top.addView(x, new LinearLayout.LayoutParams(px(36), px(36)));
		sh.addView(top, wide(8));

		List<Agro.Crop> fit = new ArrayList<Agro.Crop>(), other = new ArrayList<Agro.Crop>();
		for (Agro.Crop c : Agro.CROPS) (c.from != 0 && c.fits(m) ? fit : other).add(c);
		sh.addView(caps(Tr.t("Right for ", "Potrivite pentru ") + fmt.monthName(m), G), wide(14));
		LinearLayout list = col();
		list.setBackground(Glass.cardBg(dark, px(14), dp));
		if (fit.isEmpty()) {
			TextView none = tv(Tr.t("No field crop is sown in this month; orchards, vines and the others are below.",
					"În luna aceasta nu se seamănă culturi de câmp; livezile, via și celelalte sunt mai jos."), 13.5f, false, dim);
			none.setPadding(px(14), px(12), px(14), px(12));
			list.addView(none);
		}
		for (int k = 0; k < fit.size(); k++) {
			final Agro.Crop c = fit.get(k);
			LinearLayout r = row();
			r.setPadding(px(14), px(10), px(14), px(10));
			LinearLayout t = col();
			boolean on = c.key.equals(p.crop);
			t.addView(tv(c.name(), 16, true, on ? accent : ink));
			t.addView(tv(Agro.typeName(c.type).toLowerCase(Locale.getDefault()) + " · " + fmt.season(c), 12.5f, false, dim), wide(2));
			r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
			if (on) r.addView(tv("✓", 18, true, accent));
			r.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					choose(c.key, c.type);
				}
			});
			if (k > 0) {
				View line = new View(a);
				line.setBackgroundColor(Glass.edge(dark));
				list.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(1))));
			}
			list.addView(r);
		}
		sh.addView(list, wide(6));
		sh.addView(caps(Tr.t("Other crops · in another season", "Alte culturi · în alt sezon"), dim), wide(16));
		Flow chips = new Flow(a, px(8));
		for (final Agro.Crop c : other) {
			TextView ch = chip(c.name(), fmt.season(c), c.key.equals(p.crop));
			ch.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (c.fits(sowMonth()) || p.sow.length() != 10) choose(c.key, c.type);
					else warn(c);
				}
			});
			chips.addView(ch);
		}
		sh.addView(chips, wide(8));

		// Another crop.
		sh.addView(caps(Tr.t("Another crop…", "Altă cultură…"), dim), wide(16));
		LinearLayout own = col();
		own.setBackground(Glass.cardBg(dark, px(14), dp));
		own.setPadding(px(12), px(10), px(12), px(12));
		final EditText et = new EditText(a);
		et.setHint(Tr.t("write the name", "scrie numele"));
		et.setSingleLine(true);
		et.setTextColor(ink);
		et.setHintTextColor(dim);
		et.setTextSize(16 * fs);
		et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		if (p.crop.length() > 0 && Agro.crop(p.crop) == null) et.setText(p.crop);
		GradientDrawable eb = new GradientDrawable();
		eb.setCornerRadius(px(12));
		eb.setStroke(Math.max(1, px(1.5f)), accent);
		et.setBackground(eb);
		et.setPadding(px(12), px(10), px(12), px(10));
		own.addView(et);
		own.addView(caps(Tr.t("Crop type · for the thresholds", "Tipul culturii · pentru praguri"), dim), wide(10));
		Flow types = new Flow(a, px(8));
		for (int k = 0; k <= Agro.FORAGE; k++) {
			final int type = k;
			boolean on = Agro.crop(p.crop) == null && p.crop.length() > 0 && p.ctype == k;
			TextView ch = chip(Agro.typeName(k), null, on);
			ch.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					String n = et.getText().toString().trim();
					if (n.length() == 0) {
						et.requestFocus();
						et.setError(Tr.t("Write the crop's name", "Scrie numele culturii"));
						return;
					}
					choose(n, type);
				}
			});
			types.addView(ch);
		}
		own.addView(types, wide(6));
		sh.addView(own, wide(6));

		ScrollView sv = new ScrollView(a);
		sv.addView(sh);
		int w = a.getResources().getDisplayMetrics().widthPixels, h = a.getResources().getDisplayMetrics().heightPixels;
		FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(Math.min(w, px(600)), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
		sl.topMargin = Math.round(h * 0.08f);
		scrim.addView(sv, sl);
		scrim.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				closeSheet();
			}
		});
		sheet = scrim;
		root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	private TextView chip(String name, String sub, boolean on) {
		TextView t = new TextView(a);
		String s = sub == null ? name : name + "  " + sub;
		android.text.SpannableString sp = new android.text.SpannableString(s);
		sp.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, name.length(), 0);
		if (sub != null) {
			sp.setSpan(new android.text.style.ForegroundColorSpan(dim), name.length(), s.length(), 0);
			sp.setSpan(new android.text.style.RelativeSizeSpan(0.82f), name.length(), s.length(), 0);
		}
		t.setText(sp);
		t.setTextSize(14.5f * fs);
		t.setTextColor(on ? accent : ink);
		t.setPadding(px(12), px(8), px(12), px(8));
		t.setBackground(Glass.pill(on ? Glass.alpha(accent, 0.18f) : Glass.disc(dark), on ? accent : Glass.edge(dark), px(18), dp));
		return t;
	}

	private void choose(String crop, int type) {
		p.crop = crop;
		p.ctype = type;
		Log.i(a, "FERMIER", "cultura aleasă: " + crop + " (tip " + type + ")");
		closeSheet();
		draw();
	}

	boolean closeSheet() {
		if (sheet == null) return false;
		root.removeView(sheet);
		sheet = null;
		return true;
	}

	/** Mockup 8: crop C is not sown in the month of the sowing date. */
	private void warn(final Agro.Crop c) {
		closeSheet();
		final FrameLayout scrim = new FrameLayout(a);
		scrim.setBackgroundColor(0x70000000);
		scrim.setClickable(true);
		LinearLayout box = col();
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(Theme.surface(dark));
		bg.setCornerRadius(px(20));
		bg.setStroke(Math.max(1, px(1)), Glass.edge(dark));
		box.setBackground(bg);
		box.setPadding(px(18), px(14), px(18), px(16));
		box.setClickable(true);
		LinearLayout top = row();
		TextView b = tv("⚠ " + Tr.t("NOT THE SEASON", "NU E SEZONUL"), 12, true, Y);
		b.setPadding(px(8), px(4), px(8), px(4));
		b.setBackground(Glass.pill(Glass.alpha(Y, 0.18f), 0, px(10), dp));
		top.addView(b);
		top.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
		View x = new View(a);
		x.setBackground(Glass.closeIcon(dark, dp));
		top.addView(x, new LinearLayout.LayoutParams(px(36), px(36)));
		box.addView(top);
		box.addView(tv(c.name() + Tr.t(" sown on ", " semănat pe ") + fmt.dayMonth(p.sow) + "?", 20, true, ink), wide(10));
		String months = c.from == c.to ? fmt.monthName(c.from) : fmt.monthName(c.from) + "–" + fmt.monthName(c.to);
		box.addView(tv(c.name() + Tr.t(" is usually sown in ", " se seamănă de obicei în ") + months + Tr.t(", when the soil has ", ", când solul are ") + Math.round(c.soilMin)
				+ "–" + Math.round(c.soilMin + 2) + " °C. " + Tr.t("With this date the degree days and the risks can come out wrong.",
						"Cu data aceasta, gradele-zile și riscurile pot ieși greșit."), 14.5f, false, dim), wide(8));
		StringBuilder now = new StringBuilder();
		for (Agro.Crop k : Agro.CROPS) if (k.from != 0 && k.fits(month(p.sow))) now.append(now.length() > 0 ? " · " : "").append(k.name());
		if (now.length() > 0) {
			LinearLayout nb = col();
			nb.setBackground(Glass.cardBg(dark, px(12), dp));
			nb.setPadding(px(12), px(9), px(12), px(10));
			nb.addView(caps(Tr.t("Right now", "Potrivite acum"), dim));
			nb.addView(tv(now.toString(), 14.5f, true, ink), wide(4));
			box.addView(nb, wide(12));
		}
		LinearLayout btns = new LinearLayout(a);
		btns.addView(pill(Tr.t("Choose another crop", "Alege altă cultură"), true, new View.OnClickListener() {
			public void onClick(View v) {
				root.removeView(scrim);
				sheet = null;
				picker();
			}
		}), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		kl.leftMargin = px(10);
		btns.addView(pill(Tr.t("Keep", "Păstrează"), false, new View.OnClickListener() {
			public void onClick(View v) {
				root.removeView(scrim);
				sheet = null;
				Log.i(a, "FERMIER", "cultură în afara sezonului păstrată: " + c.key);
				choose(c.key, c.type);
			}
		}), kl);
		box.addView(btns, wide(14));
		box.addView(tv(Tr.t("\"Keep\" is useful for greenhouse crops or a wrong date you will correct later.",
				"„Păstrează” e util pentru culturi în seră sau o dată greșită pe care o corectezi apoi."), 12, false, dim), wide(8));
		int w = a.getResources().getDisplayMetrics().widthPixels;
		FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Math.min(w - px(32), px(520)), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
		ScrollView sv = new ScrollView(a);
		sv.addView(box);
		scrim.addView(sv, bl);
		View.OnClickListener close = new View.OnClickListener() {
			public void onClick(View v) {
				root.removeView(scrim);
				sheet = null;
			}
		};
		x.setOnClickListener(close);
		scrim.setOnClickListener(close);
		sheet = scrim;
		root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	/** Chips that flow onto the next line when the row is full. */
	static final class Flow extends ViewGroup {
		private final int gap;

		Flow(android.content.Context c, int gap) {
			super(c);
			this.gap = gap;
		}

		@Override
		protected void onMeasure(int ws, int hs) {
			int max = MeasureSpec.getSize(ws), x = 0, y = 0, line = 0;
			for (int k = 0; k < getChildCount(); k++) {
				View v = getChildAt(k);
				v.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
				if (x > 0 && x + v.getMeasuredWidth() > max) {
					x = 0;
					y += line + gap;
					line = 0;
				}
				x += v.getMeasuredWidth() + gap;
				line = Math.max(line, v.getMeasuredHeight());
			}
			setMeasuredDimension(max, y + line);
		}

		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			int max = r - l, x = 0, y = 0, line = 0;
			for (int k = 0; k < getChildCount(); k++) {
				View v = getChildAt(k);
				if (x > 0 && x + v.getMeasuredWidth() > max) {
					x = 0;
					y += line + gap;
					line = 0;
				}
				v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
				x += v.getMeasuredWidth() + gap;
				line = Math.max(line, v.getMeasuredHeight());
			}
		}
	}
}
