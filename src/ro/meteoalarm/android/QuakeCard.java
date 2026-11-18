package ro.meteoalarm.android;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Quakes;
import ro.meteoalarm.android.core.Screen;
import ro.meteoalarm.android.core.Theme;

/**
 * v2.030 (Viorel, after the Vrancea quake of 2026-11-18, mockups m1–m3): the earthquake's bubble
 * with a map (the epicentre, the town, the distance between them and the circles of the estimated
 * intensity), the distance and the depth, how it was probably felt in the town, the source with
 * its revision, and a short note on why the Android alert may show another value. A tap on the map
 * opens it on the whole screen, with the circles' legend and the value's history.
 */
final class QuakeCard {
	private QuakeCard() {
	}

	/** The magnitude in an orange rounded square, for the bubble's title (S pixels). */
	static Bitmap badge(String mag, String type, float s, Typeface bold) {
		Bitmap b = Bitmap.createBitmap(Math.max(1, (int) s), Math.max(1, (int) s), Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(b);
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		// v2.100 (Viorel): the square's colour by the magnitude: green, yellow, amber, orange to red.
		double m = Double.NaN;
		try {
			m = Double.parseDouble(mag.replace(',', '.').replaceAll("[^0-9.\\-]", ""));
		} catch (Exception e) {
		}
		int c0 = 0xFFF59E0B, c1 = 0xFFEA580C;
		if (!Double.isNaN(m)) {
			if (m >= 5) { c0 = 0xFFF97316; c1 = 0xFFDC2626; }
			else if (m >= 4) { c0 = 0xFFF59E0B; c1 = 0xFFEA580C; }
			else if (m >= 3) { c0 = 0xFFFDE047; c1 = 0xFFF59E0B; }
			else { c0 = 0xFF86EFAC; c1 = 0xFF22C55E; }
		}
		p.setShader(new LinearGradient(0, 0, s * 0.4f, s, c0, c1, Shader.TileMode.CLAMP));
		c.drawRoundRect(new RectF(0, 0, s, s), s * 0.27f, s * 0.27f, p);
		p.setShader(null);
		p.setTypeface(bold);
		p.setColor(0xFF1A0F00);
		p.setTextAlign(Paint.Align.CENTER);
		// v2.044 (Viorel): the value larger and more prominent, the type smaller under it.
		p.setColor(0xFF000000);
		p.setFakeBoldText(true);
		float ts = s * (mag.length() > 3 ? 0.46f : 0.54f);
		p.setTextSize(ts);
		float tw = p.measureText(mag);
		if (tw > s * 0.9f) p.setTextSize(ts * s * 0.9f / tw);
		c.drawText(mag, s / 2, s * 0.60f, p);
		p.setFakeBoldText(false);
		p.setTextSize(s * 0.15f);
		p.setAlpha(190);
		c.drawText(type, s / 2, s * 0.86f, p);
		return b;
	}

	/** The map's marks for quake Q seen from town C. */
	static MapView.QuakeMark mark(Quakes.Quake q, Cities.City c, String[] t) {
		MapView.QuakeMark m = new MapView.QuakeMark();
		m.lat = q.lat;
		m.lon = q.lon;
		if (c != null) {
			m.tLat = c.lat;
			m.tLon = c.lon;
			m.town = c.name;
		}
		m.dist = t[4];
		m.epi = t[16];
		int[] lv = Screen.quakeLevels(q);
		m.km = new double[lv.length];
		m.lab = new String[lv.length];
		m.col = new int[lv.length];
		for (int i = 0; i < lv.length; i++) {
			m.km[i] = Quakes.ringKm(q.mag, q.depth, lv[i]);
			m.lab[i] = Screen.roman(lv[i]);
			m.col[i] = Screen.levelColor(lv[i]);
		}
		return m;
	}

	/**
	 * The bubble's body for quake Q (texts T from Screen.quakeTexts) in a bubble WIDTH pixels wide:
	 * in landscape (wide) the map at the left and the rest at the right. BIG opens the whole map.
	 */
	static View body(final Context ctx, Quakes.Quake q, Cities.City town, String[] t, boolean dark, Typeface reg, Typeface bold, float zk, int width,
			final Runnable big, final Runnable refresh, String refreshText, final String refreshName) {
		float dp = ctx.getResources().getDisplayMetrics().density;
		boolean wide = width > 560 * dp;
		LinearLayout root = new LinearLayout(ctx);
		root.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
		root.setPadding((int) (16 * dp), (int) (2 * dp), (int) (16 * dp), (int) (6 * dp));
		// The map, with the "Big map" pill; a tap anywhere on it opens the whole map.
		FrameLayout map = new FrameLayout(ctx);
		final MapView mv = new MapView(ctx);
		mv.setDark(dark);
		mv.setNight(dark);
		mv.setQuake(mark(q, town, t));
		map.addView(mv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		TextView pill = text(ctx, "⛶ " + t[17], 12 * zk, bold, 0xFFFFFFFF);
		pill.setPadding((int) (10 * dp), (int) (5 * dp), (int) (10 * dp), (int) (5 * dp));
		pill.setBackground(Glass.pill(0xD90F172A, 0x55FFFFFF, 14 * dp, dp));
		FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
		pl.rightMargin = (int) (8 * dp);
		pl.bottomMargin = (int) (24 * dp);
		map.addView(pill, pl);
		View tap = new View(ctx);
		tap.setContentDescription(t[17]);
		tap.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				big.run();
			}
		});
		map.addView(tap, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		map.setClipToOutline(true);
		GradientDrawable mb = new GradientDrawable();
		mb.setCornerRadius(16 * dp);
		mb.setColor(0xFF1B2430);
		map.setBackground(mb);
		map.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
		int inner = width - (int) (32 * dp);
		int mapW = wide ? (int) (inner * 0.48f) : inner;
		int mapH = wide ? (int) Math.min(300 * dp, mapW * 0.95f) : (int) Math.min(260 * dp, inner * 0.68f);
		// In landscape the map fits in the bubble's height (at most 80 % of the screen, less the title).
		if (wide) mapH = (int) Math.max(160 * dp, Math.min(mapH, ctx.getResources().getDisplayMetrics().heightPixels * 0.8f - 120 * dp));
		LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(wide ? mapW : ViewGroup.LayoutParams.MATCH_PARENT, mapH);
		root.addView(map, ml);
		mv.fitQuake(false, 16 * dp, 30 * dp, 16 * dp, 28 * dp);
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(wide ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, wide ? 1 : 0);
		if (wide) cl.leftMargin = (int) (14 * dp);
		else cl.topMargin = (int) (10 * dp);
		root.addView(col, cl);
		details(col, ctx, q, t, dark, reg, bold, zk, refresh, refreshText, refreshName);
		return root;
	}

	/** v2.100: distance and depth, how it was felt here, the source with its revision, the INFP button and the Android note, into COL. */
	static void details(LinearLayout col, final Context ctx, Quakes.Quake q, String[] t, boolean dark, Typeface reg, Typeface bold, float zk,
			final Runnable refresh, String refreshText, final String refreshName) {
		float dp = ctx.getResources().getDisplayMetrics().density;
		int ink = Theme.ink(dark), dim = Theme.dim(dark);
		// Distance and depth, side by side.
		LinearLayout cells = new LinearLayout(ctx);
		cells.setOrientation(LinearLayout.HORIZONTAL);
		cells.setBaselineAligned(false);
		cells.addView(cell(ctx, Tr2.distance(), t[4], t[5], dark, reg, bold, zk), cellLp(0, dp));
		cells.addView(cell(ctx, Tr2.depth(), t[6], t[7], dark, reg, bold, zk), cellLp(8, dp));
		col.addView(cells, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		// How it was probably felt in the town.
		LinearLayout felt = new LinearLayout(ctx);
		felt.setOrientation(LinearLayout.HORIZONTAL);
		felt.setGravity(Gravity.CENTER_VERTICAL);
		felt.setPadding((int) (12 * dp), (int) (10 * dp), (int) (12 * dp), (int) (10 * dp));
		int lvc = q.felt >= 2 ? Screen.levelColor((int) Math.round(q.felt)) : 0xFF94A3B8;
		GradientDrawable fb = new GradientDrawable();
		fb.setCornerRadius(14 * dp);
		fb.setColor((lvc & 0x00FFFFFF) | (dark ? 0x26000000 : 0x30000000));
		fb.setStroke(Math.max(1, Math.round(dp)), (lvc & 0x00FFFFFF) | 0x66000000);
		felt.setBackground(fb);
		TextView lv = text(ctx, t[8], 15 * zk, bold, 0xFF111111);
		lv.setGravity(Gravity.CENTER);
		GradientDrawable lb = new GradientDrawable();
		lb.setShape(GradientDrawable.OVAL);
		lb.setColor(lvc);
		lv.setBackground(lb);
		int ls = (int) (40 * dp * zk);
		felt.addView(lv, new LinearLayout.LayoutParams(ls, ls));
		LinearLayout ft = new LinearLayout(ctx);
		ft.setOrientation(LinearLayout.VERTICAL);
		ft.addView(text(ctx, t[9], 14.5f * zk, bold, ink));
		TextView fw = text(ctx, t[10], 12.5f * zk, reg, dim);
		fw.setLineSpacing(1.5f * dp, 1);
		ft.addView(fw);
		LinearLayout.LayoutParams ftl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		ftl.leftMargin = (int) (10 * dp);
		felt.addView(ft, ftl);
		LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		fl.topMargin = (int) (8 * dp);
		col.addView(felt, fl);
		// The source, its revision and the first value.
		LinearLayout sr = new LinearLayout(ctx);
		sr.setOrientation(LinearLayout.HORIZONTAL);
		sr.setGravity(Gravity.CENTER_VERTICAL);
		TextView sl = text(ctx, t[11], 12.5f * zk, bold, ink);
		// v2.034 (Viorel): a long source line used to take the whole row and squeeze the "official"
		// chip into a thin green column of letters; now the line wraps and the chip keeps its word.
		sr.addView(sl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		if (t[12].length() > 0) {
			TextView chip = text(ctx, t[12], 11 * zk, bold, dark ? 0xFF86EFAC : 0xFF15803D);
			chip.setSingleLine(true);
			chip.setPadding((int) (8 * dp), (int) (1 * dp), (int) (8 * dp), (int) (2 * dp));
			chip.setBackground(Glass.pill(0x2E22C55E, 0x6622C55E, 10 * dp, dp));
			LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			chl.leftMargin = (int) (8 * dp);
			sr.addView(chip, chl);
		}
		LinearLayout.LayoutParams srl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		srl.topMargin = (int) (10 * dp);
		col.addView(sr, srl);
		TextView first = text(ctx, t[13], 12 * zk, reg, dim);
		col.addView(first);
		// v2.032 (Viorel): in Romania, ask INFP again now (its official value may come later than EMSC's).
		if (refresh != null) {
			final TextView rb = text(ctx, refreshText, 13 * zk, bold, dark ? 0xFF86EFAC : 0xFF15803D);
			rb.setGravity(Gravity.CENTER);
			rb.setPadding((int) (14 * dp), (int) (8 * dp), (int) (14 * dp), (int) (8 * dp));
			rb.setBackground(Glass.pill(0x2622C55E, 0x6622C55E, 18 * dp, dp));
			rb.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					rb.setEnabled(false);
					rb.setAlpha(0.6f);
					rb.setText(Bubble.nb(ro.meteoalarm.android.core.Tr.t("Checking ", "Se verifică la ") + refreshName + "…"));
					refresh.run();
				}
			});
			LinearLayout.LayoutParams rbl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			rbl.topMargin = (int) (8 * dp);
			rbl.gravity = Gravity.CENTER_HORIZONTAL;
			col.addView(rb, rbl);
		}
		// Why the Android alert may show another value.
		TextView note = text(ctx, "", 12.5f * zk, reg, ink);
		android.text.SpannableStringBuilder nb = new android.text.SpannableStringBuilder(t[14]);
		nb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, nb.length(), 0);
		nb.setSpan(new android.text.style.ForegroundColorSpan(dark ? 0xFFBFDBFE : 0xFF1D4ED8), 0, nb.length(), 0);
		nb.append(" ").append(t[15]);
		note.setText(nb);
		note.setLineSpacing(2 * dp, 1);
		note.setPadding((int) (12 * dp), (int) (9 * dp), (int) (12 * dp), (int) (9 * dp));
		note.setBackground(Glass.pill(dark ? 0x1A60A5FA : 0x1A2563EB, dark ? 0x4D60A5FA : 0x4D2563EB, 14 * dp, dp));
		LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		nl.topMargin = (int) (10 * dp);
		col.addView(note, nl);
	}

	private static LinearLayout.LayoutParams cellLp(int left, float dp) {
		LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		l.leftMargin = (int) (left * dp);
		return l;
	}

	private static View cell(Context ctx, String label, String value, String sub, boolean dark, Typeface reg, Typeface bold, float zk) {
		float dp = ctx.getResources().getDisplayMetrics().density;
		LinearLayout c = new LinearLayout(ctx);
		c.setOrientation(LinearLayout.VERTICAL);
		c.setPadding((int) (10 * dp), (int) (8 * dp), (int) (10 * dp), (int) (8 * dp));
		c.setBackground(Glass.cardBg(dark, 12 * dp, dp));
		// v2.036 (Viorel): centred, like the value history's boxes.
		c.setGravity(Gravity.CENTER_HORIZONTAL);
		TextView l = text(ctx, label.toUpperCase(), 10.5f * zk, bold, Theme.dim(dark));
		l.setLetterSpacing(0.05f);
		l.setGravity(Gravity.CENTER);
		l.setSingleLine(true);
		c.addView(l);
		TextView v = text(ctx, value, 16 * zk, bold, Theme.ink(dark));
		v.setGravity(Gravity.CENTER);
		v.setSingleLine(true);
		c.addView(v);
		// v2.044 (Viorel): both boxes as tall, even when one has less text (two rows under the value).
		TextView s = text(ctx, sub, 11.5f * zk, reg, Theme.dim(dark));
		s.setGravity(Gravity.CENTER);
		s.setMinLines(2);
		s.setMaxLines(2);
		c.addView(s);
		return c;
	}

	/** One box of the value's history: WHEN on top, the value large, the source as a coloured chip, all centred. */
	/** v2.033 (Viorel): what the number is, in small letters under it. */
	static String magWhat(String type) {
		String t = type == null ? "" : type.trim().toLowerCase(java.util.Locale.US);
		if (t.startsWith("ml")) return ro.meteoalarm.android.core.Tr.t("local magnitude (Richter)", "magnitudine locală (Richter)");
		if (t.startsWith("mw")) return ro.meteoalarm.android.core.Tr.t("moment magnitude (energy)", "magnitudine de moment (energie)");
		if (t.startsWith("mb")) return ro.meteoalarm.android.core.Tr.t("body-wave magnitude", "magnitudine din unde de volum");
		return ro.meteoalarm.android.core.Tr.t("magnitude", "magnitudine");
	}

	/**
	 * v2.042: the history's boxes share the row's width; v2.043: their own height (MATCH_PARENT in
	 * the sheet squeezed them to the arrow's height). Their rows are the same (one-row label and
	 * time, two-row meaning), so both come out as tall.
	 */
	private static LinearLayout.LayoutParams histLp() {
		return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
	}

	private static View histCell(Context ctx, String label, String at, String value, String src, String what, boolean dark, Typeface reg, Typeface bold, float zk) {
		String when = label;
		float dp = ctx.getResources().getDisplayMetrics().density;
		LinearLayout c = new LinearLayout(ctx);
		c.setOrientation(LinearLayout.VERTICAL);
		c.setGravity(Gravity.CENTER_HORIZONTAL);
		c.setPadding((int) (10 * dp), (int) (10 * dp), (int) (10 * dp), (int) (12 * dp));
		c.setBackground(Glass.cardBg(dark, 14 * dp, dp));
		TextView w = text(ctx, when.toUpperCase(), 11.5f * zk, bold, Theme.dim(dark));
		w.setLetterSpacing(0.05f);
		w.setGravity(Gravity.CENTER);
		w.setSingleLine(true);
		c.addView(w);
		TextView a = text(ctx, ro.meteoalarm.android.core.Tr.t("reported ", "raportată ") + at, 11.5f * zk, reg, Theme.dim(dark));
		a.setGravity(Gravity.CENTER);
		a.setSingleLine(true);
		c.addView(a);
		TextView v = text(ctx, value, 26 * zk, bold, Theme.ink(dark));
		v.setGravity(Gravity.CENTER);
		c.addView(v);
		TextView wh = text(ctx, what, 11 * zk, reg, Theme.dim(dark));
		wh.setGravity(Gravity.CENTER);
		wh.setMaxLines(2);
		wh.setMinLines(2);
		c.addView(wh);
		boolean infp = src != null && src.length() > 0 && !"EMSC".equals(src) && !"USGS".equals(src);
		int col = infp ? 0xFF22C55E : "USGS".equals(src) ? 0xFF94A3B8 : 0xFF60A5FA;
		TextView ch = text(ctx, src == null ? "" : src, 11 * zk, bold, dark ? (infp ? 0xFF86EFAC : 0xFFBFDBFE) : (infp ? 0xFF15803D : 0xFF1D4ED8));
		ch.setGravity(Gravity.CENTER);
		ch.setPadding((int) (10 * dp), (int) (1 * dp), (int) (10 * dp), (int) (2 * dp));
		ch.setBackground(Glass.pill((col & 0x00FFFFFF) | 0x2E000000, (col & 0x00FFFFFF) | 0x66000000, 10 * dp, dp));
		LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		cl.topMargin = (int) (4 * dp);
		c.addView(ch, cl);
		return c;
	}

	private static TextView text(Context ctx, String s, float sp, Typeface tf, int color) {
		TextView t = new TextView(ctx);
		t.setText(Bubble.nb(s));
		t.setTextSize(sp);
		t.setTypeface(tf);
		t.setTextColor(color);
		t.setIncludeFontPadding(true);
		return t;
	}

	/** The labels of the cells, in the app's language. */
	static final class Tr2 {
		static String distance() {
			return ro.meteoalarm.android.core.Tr.t("Distance", "Distanță");
		}

		static String depth() {
			return ro.meteoalarm.android.core.Tr.t("Depth", "Adâncime");
		}
	}

	/**
	 * The whole map (a tap on the bubble's map): the earthquake with its circles, moved and zoomed
	 * with the fingers; on top ‹ and what happened, at the bottom the circles' legend and the value's
	 * history. ‹ or Back return to the bubble.
	 */
	static void bigMap(Context ctx, Quakes.Quake q, Cities.City town, Screen s, boolean dark, Typeface reg, Typeface bold, float zk) {
		final float dp = ctx.getResources().getDisplayMetrics().density;
		String[] t = s.quakeTexts(q);
		final Dialog d = new Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);
		FrameLayout root = new FrameLayout(ctx);
		root.setBackgroundColor(0xFF1B2430);
		final MapView mv = new MapView(ctx);
		mv.setDark(dark);
		mv.setNight(dark);
		mv.setQuake(mark(q, town, t));
		root.addView(mv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		int ink = Theme.ink(dark), dim = Theme.dim(dark);
		// On top: ‹ and the earthquake in two lines.
		LinearLayout bar = new LinearLayout(ctx);
		bar.setOrientation(LinearLayout.HORIZONTAL);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setPadding((int) (10 * dp), (int) (8 * dp), (int) (14 * dp), (int) (8 * dp));
		bar.setBackground(Glass.panelBg(dark, 20 * dp, dp));
		TextView back = text(ctx, "‹", 24 * zk, bold, ink);
		back.setGravity(Gravity.CENTER);
		back.setIncludeFontPadding(false);
		back.setBackground(Glass.pill(Glass.disc(dark), 0, 20 * dp, dp));
		back.setContentDescription(ro.meteoalarm.android.core.Tr.t("Back", "Înapoi"));
		back.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				d.dismiss();
			}
		});
		bar.addView(back, new LinearLayout.LayoutParams((int) (40 * dp), (int) (40 * dp)));
		LinearLayout bt = new LinearLayout(ctx);
		bt.setOrientation(LinearLayout.VERTICAL);
		bt.addView(text(ctx, t[18], 15.5f * zk, bold, ink));
		bt.addView(text(ctx, t[19], 12.5f * zk, reg, dim));
		LinearLayout.LayoutParams btl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		btl.leftMargin = (int) (10 * dp);
		bar.addView(bt, btl);
		FrameLayout.LayoutParams barL = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
		barL.leftMargin = barL.rightMargin = (int) (12 * dp);
		barL.topMargin = (int) (36 * dp);
		root.addView(bar, barL);
		// At the bottom: the circles' legend and the value's history. v2.031 (Viorel): the sheet
		// opens small, down to the history's box; dragging its handle up (or a tap) shows the legend
		// too (scrolling when it is taller than the room), dragging it down makes it small again.
		final Pull sheet = new Pull(ctx, dp);
		sheet.setOrientation(LinearLayout.VERTICAL);
		android.animation.LayoutTransition lt = new android.animation.LayoutTransition();
		lt.enableTransitionType(android.animation.LayoutTransition.CHANGING);
		sheet.setLayoutTransition(lt);
		sheet.setPadding((int) (14 * dp), (int) (6 * dp), (int) (14 * dp), (int) (12 * dp));
		sheet.setBackground(Glass.panelBg(dark, 22 * dp, dp));
		sheet.setClickable(true);
		// The same handle as the ☰ menu's Sistem & Suport bar.
		View grip = new View(ctx);
		grip.setBackground(Glass.pill(Glass.alpha(ink, 0.3f), 0, 3 * dp, dp));
		LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(Math.round(40 * dp), Math.round(5 * dp));
		gl.gravity = Gravity.CENTER_HORIZONTAL;
		gl.topMargin = Math.round(2 * dp);
		gl.bottomMargin = Math.round(6 * dp);
		sheet.addView(grip, gl);
		LinearLayout legend = new LinearLayout(ctx);
		legend.setOrientation(LinearLayout.VERTICAL);
		TextView h1 = text(ctx, t[20].toUpperCase(), 11 * zk, bold, dim);
		h1.setLetterSpacing(0.06f);
		legend.addView(h1);
		int[] lvs = Screen.quakeLevels(q);
		for (int lv : lvs) {
			LinearLayout row = new LinearLayout(ctx);
			row.setOrientation(LinearLayout.HORIZONTAL);
			row.setGravity(Gravity.CENTER_VERTICAL);
			TextView chip = text(ctx, Screen.roman(lv), 11.5f * zk, bold, 0xFF111111);
			chip.setGravity(Gravity.CENTER);
			// v2.047 (Viorel): the numeral centred in the pill (the font's padding pushed it down).
			chip.setIncludeFontPadding(false);
			chip.setPadding(0, 0, 0, 0);
			chip.setBackground(Glass.pill(Screen.levelColor(lv), 0, 10 * dp, dp));
			row.addView(chip, new LinearLayout.LayoutParams((int) (34 * dp * zk), (int) (20 * dp * zk)));
			TextView w = text(ctx, s.feltCap(lv) + " · " + t[22] + s.dist(Quakes.ringKm(q.mag, q.depth, lv)), 12.5f * zk, reg, ink);
			LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
			wl.leftMargin = (int) (8 * dp);
			row.addView(w, wl);
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			rl.topMargin = (int) (6 * dp);
			legend.addView(row, rl);
		}
		View line = new View(ctx);
		line.setBackgroundColor(Glass.edge(dark));
		LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) dp));
		ll.topMargin = ll.bottomMargin = (int) (10 * dp);
		legend.addView(line, ll);
		final MaxScroll ls = new MaxScroll(ctx);
		ls.setVerticalScrollBarEnabled(false);
		ls.setOverScrollMode(View.OVER_SCROLL_NEVER);
		ls.addView(legend);
		ls.setVisibility(View.GONE);
		sheet.addView(ls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		TextView h2 = text(ctx, t[21].toUpperCase(), 11 * zk, bold, dim);
		h2.setLetterSpacing(0.06f);
		sheet.addView(h2);
		// v2.032 (Viorel): the history's boxes centred: when, the value large, the source as a chip;
		// the first value and the current one with an arrow between them.
		LinearLayout hist = new LinearLayout(ctx);
		hist.setOrientation(LinearLayout.HORIZONTAL);
		hist.setBaselineAligned(false);
		hist.setGravity(Gravity.CENTER_VERTICAL);
		// v2.042 (Viorel): both boxes alike: same size, same rows (what, when it was reported, the
		// value, what the number is, the source), the texts on the same lines.
		long nowMs = q.changedAt > 0 ? q.changedAt : q.updated > 0 ? q.updated : q.time;
		hist.setGravity(Gravity.CENTER_VERTICAL);
		if (!Double.isNaN(q.firstMag)) {
			String fm = String.format(java.util.Locale.US, "%.1f", q.firstMag).replace('.', ro.meteoalarm.android.core.Tr.dot());
			hist.addView(histCell(ctx, first(t[13]), s.qhm(q.firstAt), fm, q.firstSrc, magWhat(""), dark, reg, bold, zk), histLp());
			TextView arrow = text(ctx, "→", 20 * zk, bold, dim);
			LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			al.gravity = Gravity.CENTER_VERTICAL;
			al.leftMargin = al.rightMargin = (int) (6 * dp);
			hist.addView(arrow, al);
		}
		hist.addView(histCell(ctx, ro.meteoalarm.android.core.Tr.t("Current", "Actuală"), s.qhm(nowMs), t[2], q.src, magWhat(q.magType), dark, reg, bold, zk), histLp());
		LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		hl.topMargin = (int) (6 * dp);
		sheet.addView(hist, hl);
		FrameLayout.LayoutParams shL = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
		shL.leftMargin = shL.rightMargin = (int) (12 * dp);
		shL.bottomMargin = (int) (26 * dp);
		root.addView(sheet, shL);
		final FrameLayout rootF = root;
		final View barV = bar;
		final View histV = hist, h2V = h2;
		// Pulled up the legend shows, pulled down only the history (as the ☰ menu's Sistem & Suport bar).
		sheet.legend = ls;
		sheet.toggle = new Runnable() {
			public void run() {
				boolean want = sheet.open;
				if (want) ls.max = rootF.getHeight() - barV.getBottom() - (int) (12 * dp) - shL.bottomMargin - sheet.getPaddingTop() - sheet.getPaddingBottom()
						- (int) (24 * dp) - h2V.getHeight() - histV.getHeight();
				ls.scrollTo(0, 0);
				ls.setVisibility(want ? View.VISIBLE : View.GONE);
			}
		};
		d.setContentView(root);
		Window w = d.getWindow();
		if (w != null) w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
		d.show();
		// The largest circle in sight, between the bar and the small sheet (measured once laid out).
		root.post(new Runnable() {
			public void run() {
				float top = barV.getBottom() > 0 ? barV.getBottom() + 10 * dp : 106 * dp;
				float bottom = sheet.getHeight() > 0 ? rootF.getHeight() - sheet.getTop() + 10 * dp : 230 * dp;
				mv.fitQuake(true, 20 * dp, top, 20 * dp, bottom);
			}
		});
	}

	/**
	 * v2.031 (Viorel): the big map's sheet moves like the ☰ menu's Sistem & Suport bar: a drag of
	 * 24 dp up opens it (the legend shows), down closes it, a tap on its handle switches it.
	 */
	private static class Pull extends LinearLayout {
		final float dp;
		float y0;
		boolean drag, open;
		ScrollView legend;
		Runnable toggle;

		Pull(Context c, float dp) {
			super(c);
			this.dp = dp;
		}

		void setOpen(boolean o) {
			if (open == o) return;
			open = o;
			if (toggle != null) toggle.run();
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent e) {
			// The legend's own scrolling wins while it is open and can scroll.
			if (e.getActionMasked() == MotionEvent.ACTION_DOWN && open && legend != null && legend.getVisibility() == VISIBLE
					&& e.getY() >= legend.getTop() && e.getY() < legend.getBottom() && legend.canScrollVertically(1) | legend.canScrollVertically(-1)) {
				y0 = Float.NaN;
				return false;
			}
			return track(e);
		}

		@Override
		public boolean onTouchEvent(MotionEvent e) {
			track(e);
			return true;
		}

		private boolean track(MotionEvent e) {
			switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				y0 = e.getRawY();
				drag = false;
				break;
			case MotionEvent.ACTION_MOVE:
				if (Float.isNaN(y0)) return false;
				float d = e.getRawY() - y0;
				if (!drag && Math.abs(d) > 12 * dp) drag = true;
				if (drag && Math.abs(d) > 24 * dp) {
					setOpen(d < 0);
					y0 = e.getRawY();
				}
				break;
			case MotionEvent.ACTION_UP:
				if (!drag && !Float.isNaN(y0) && e.getY() < 22 * dp) setOpen(!open);
				break;
			}
			return drag;
		}
	}

	/** A scroll view no taller than MAX pixels (0 = as tall as its content). */
	private static class MaxScroll extends ScrollView {
		int max;

		MaxScroll(Context c) {
			super(c);
		}

		@Override
		protected void onMeasure(int wSpec, int hSpec) {
			if (max > 0) hSpec = MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST);
			super.onMeasure(wSpec, hSpec);
		}
	}

	/** "First: 5,1 (EMSC, 02:51) · …" -> "First" (the history cell's label). */
	private static String first(String line) {
		int k = line.indexOf(':');
		return k > 0 ? line.substring(0, k) : line;
	}

	// --- v2.100 (Viorel, mockups m8–m11): the earthquake's information screens ---------------------

	/** What the information screen needs besides the earthquake. */
	static final class Hooks {
		/** ‹: back (closes the embedded view, or the full screen back to the list). */
		Runnable back;
		/** The square or the map: the whole screen (null: the map moves and zooms itself). */
		Runnable full;
		/** Ask the official institute again (null: no button). */
		Runnable refresh;
		String refreshText = "", refreshName = "";
		/** The other earthquakes and the sources (rows as from Screen.alertInfo). */
		java.util.List<String[]> more;
	}

	/** ‹ in a round button, "EARTHQUAKE INFORMATION" and a thin line to the right edge. */
	static View head(Context ctx, boolean dark, Typeface bold, float zk, float size, final Runnable back) {
		float dp = ctx.getResources().getDisplayMetrics().density;
		LinearLayout h = new LinearLayout(ctx);
		h.setOrientation(LinearLayout.HORIZONTAL);
		h.setGravity(Gravity.CENTER_VERTICAL);
		TextView b = text(ctx, "‹", size * 0.6f * zk, bold, Theme.ink(dark));
		b.setGravity(Gravity.CENTER);
		b.setIncludeFontPadding(false);
		b.setPadding(0, 0, 0, (int) (2 * dp));
		b.setBackground(Glass.pill(Glass.disc(dark), 0, size * dp, dp));
		b.setContentDescription(ro.meteoalarm.android.core.Tr.t("Back", "Înapoi"));
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (back != null) back.run();
			}
		});
		int bs = (int) (size * dp * zk);
		h.addView(b, new LinearLayout.LayoutParams(bs, bs));
		TextView t = text(ctx, ro.meteoalarm.android.core.Tr.t("EARTHQUAKE INFORMATION", "INFORMAȚII CUTREMUR"), 13 * zk, bold, Theme.dim(dark));
		t.setLetterSpacing(0.06f);
		t.setSingleLine(true);
		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		tl.leftMargin = (int) (12 * dp);
		h.addView(t, tl);
		View line = new View(ctx);
		line.setBackgroundColor(Glass.edge(dark));
		LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(0, Math.max(1, (int) dp), 1);
		ll.leftMargin = (int) (12 * dp);
		h.addView(line, ll);
		return h;
	}

	/** The coloured square with the magnitude, "Earthquake in …" and the date with the depth. */
	static View titleRow(Context ctx, Quakes.Quake q, String[] t, boolean dark, Typeface reg, Typeface bold, float zk, float size, final Runnable tap) {
		float dp = ctx.getResources().getDisplayMetrics().density;
		LinearLayout r = new LinearLayout(ctx);
		r.setOrientation(LinearLayout.HORIZONTAL);
		r.setGravity(Gravity.CENTER_VERTICAL);
		android.widget.ImageView b = new android.widget.ImageView(ctx);
		int bs = (int) (size * dp * zk);
		b.setImageBitmap(badge(t[2], t[3], bs, bold));
		if (tap != null) {
			b.setContentDescription(t[17]);
			b.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					tap.run();
				}
			});
		}
		r.addView(b, new LinearLayout.LayoutParams(bs, bs));
		LinearLayout tx = new LinearLayout(ctx);
		tx.setOrientation(LinearLayout.VERTICAL);
		tx.addView(text(ctx, t[0], 19 * zk, bold, Theme.ink(dark)));
		tx.addView(text(ctx, t[1], 12.5f * zk, reg, Theme.dim(dark)));
		LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		xl.leftMargin = (int) (12 * dp);
		r.addView(tx, xl);
		return r;
	}

	/** The big map (the one of "Big map"): TAP non-null puts a tap on it (the map does not move then). */
	static FrameLayout mapBox(Context ctx, Quakes.Quake q, Cities.City town, String[] t, boolean dark, final Runnable tap, float radius) {
		float dp = ctx.getResources().getDisplayMetrics().density;
		FrameLayout map = new FrameLayout(ctx);
		MapView mv = new MapView(ctx);
		mv.setDark(dark);
		mv.setNight(dark);
		mv.setQuake(mark(q, town, t));
		map.addView(mv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		if (tap != null) {
			View v = new View(ctx);
			v.setContentDescription(t[17]);
			v.setOnClickListener(new View.OnClickListener() {
				public void onClick(View x) {
					tap.run();
				}
			});
			map.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		}
		GradientDrawable mb = new GradientDrawable();
		mb.setCornerRadius(radius);
		mb.setColor(0xFF1B2430);
		map.setBackground(mb);
		if (radius > 0) {
			map.setClipToOutline(true);
			map.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
		}
		map.setTag(mv);
		return map;
	}

	/** Fits the map of MAPBOX on the largest circle, less the margins (pixels) at its edges. */
	static void fit(FrameLayout mapBox, float l, float t, float r, float b) {
		((MapView) mapBox.getTag()).fitQuake(true, l, t, r, b);
	}

	/**
	 * The two boxes that go under the map (Viorel): the estimated intensity by zones and the
	 * value's history; side by side, one under the other when STACK.
	 */
	static View boxes(Context ctx, Quakes.Quake q, Screen s, String[] t, boolean dark, Typeface reg, Typeface bold, float zk, boolean stack, int card) {
		float dp = ctx.getResources().getDisplayMetrics().density;
		int ink = Theme.ink(dark), dim = Theme.dim(dark);
		LinearLayout row = new LinearLayout(ctx);
		row.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
		row.setBaselineAligned(false);
		// The zones.
		LinearLayout lg = new LinearLayout(ctx);
		lg.setOrientation(LinearLayout.VERTICAL);
		lg.setPadding((int) (10 * dp), (int) (8 * dp), (int) (10 * dp), (int) (9 * dp));
		lg.setBackground(card != 0 ? Glass.pill(card, Glass.edge(dark), 14 * dp, dp) : Glass.cardBg(dark, 14 * dp, dp));
		TextView h1 = text(ctx, t[20].toUpperCase(), 10.5f * zk, bold, dim);
		h1.setLetterSpacing(0.05f);
		lg.addView(h1);
		for (int lv : Screen.quakeLevels(q)) {
			LinearLayout r = new LinearLayout(ctx);
			r.setOrientation(LinearLayout.HORIZONTAL);
			r.setGravity(Gravity.CENTER_VERTICAL);
			TextView chip = text(ctx, Screen.roman(lv), 10.5f * zk, bold, 0xFF111111);
			chip.setGravity(Gravity.CENTER);
			chip.setIncludeFontPadding(false);
			chip.setPadding(0, 0, 0, 0);
			chip.setBackground(Glass.pill(Screen.levelColor(lv), 0, 9 * dp, dp));
			r.addView(chip, new LinearLayout.LayoutParams((int) (30 * dp * zk), (int) (18 * dp * zk)));
			TextView w = text(ctx, s.feltCap(lv) + " · ~" + s.dist(Quakes.ringKm(q.mag, q.depth, lv)), 12 * zk, reg, ink);
			LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
			wl.leftMargin = (int) (7 * dp);
			r.addView(w, wl);
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			rl.topMargin = (int) (5 * dp);
			lg.addView(r, rl);
		}
		// The value's history: the first value, an arrow, the current one.
		LinearLayout hs = new LinearLayout(ctx);
		hs.setOrientation(LinearLayout.VERTICAL);
		hs.setPadding((int) (10 * dp), (int) (8 * dp), (int) (10 * dp), (int) (9 * dp));
		hs.setBackground(card != 0 ? Glass.pill(card, Glass.edge(dark), 14 * dp, dp) : Glass.cardBg(dark, 14 * dp, dp));
		TextView h2 = text(ctx, t[21].toUpperCase(), 10.5f * zk, bold, dim);
		h2.setLetterSpacing(0.05f);
		hs.addView(h2);
		LinearLayout vals = new LinearLayout(ctx);
		vals.setOrientation(LinearLayout.HORIZONTAL);
		vals.setGravity(Gravity.CENTER);
		vals.setBaselineAligned(false);
		long nowMs = q.changedAt > 0 ? q.changedAt : q.updated > 0 ? q.updated : q.time;
		if (!Double.isNaN(q.firstMag)) {
			String fm = String.format(java.util.Locale.US, "%.1f", q.firstMag).replace('.', ro.meteoalarm.android.core.Tr.dot());
			vals.addView(histVal(ctx, fm, q.firstSrc, s.qhm(q.firstAt), dark, bold, zk), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
			vals.addView(text(ctx, "→", 16 * zk, bold, dim));
		}
		vals.addView(histVal(ctx, t[2], q.src, s.qhm(nowMs), dark, bold, zk), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
		vl.topMargin = (int) (4 * dp);
		hs.addView(vals, vl);
		TextView what = text(ctx, magWhat(q.magType), 10.5f * zk, reg, dim);
		what.setGravity(Gravity.CENTER);
		hs.addView(what, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		if (stack) {
			row.addView(lg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			hl.topMargin = (int) (8 * dp);
			row.addView(hs, hl);
		} else {
			row.addView(lg, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f));
			LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
			hl.leftMargin = (int) (8 * dp);
			row.addView(hs, hl);
		}
		return row;
	}

	private static View histVal(Context ctx, String v, String src, String at, boolean dark, Typeface bold, float zk) {
		LinearLayout c = new LinearLayout(ctx);
		c.setOrientation(LinearLayout.VERTICAL);
		c.setGravity(Gravity.CENTER_HORIZONTAL);
		TextView a = text(ctx, v, 20 * zk, bold, Theme.ink(dark));
		a.setGravity(Gravity.CENTER);
		c.addView(a);
		boolean infp = src != null && src.length() > 0 && !"EMSC".equals(src) && !"USGS".equals(src);
		TextView b = text(ctx, (src == null ? "" : src) + " " + at, 10.5f * zk, bold, infp ? (dark ? 0xFF86EFAC : 0xFF15803D) : Theme.dim(dark));
		b.setGravity(Gravity.CENTER);
		b.setSingleLine(true);
		c.addView(b);
		return c;
	}

	/** The other earthquakes and the sources, as plain rows (headers in small capitals). */
	static void more(LinearLayout col, Context ctx, java.util.List<String[]> rows, boolean dark, Typeface reg, Typeface bold, float zk) {
		if (rows == null) return;
		float dp = ctx.getResources().getDisplayMetrics().density;
		int ink = Theme.ink(dark), dim = Theme.dim(dark);
		for (String[] r : rows) {
			if (r.length < 2 || r[0].equals("title")) continue;
			TextView v;
			LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			if (r[0].equals("h")) {
				v = text(ctx, r[1].toUpperCase(), 11 * zk, bold, dim);
				v.setLetterSpacing(0.05f);
				l.topMargin = (int) (14 * dp);
			} else if (r[0].equals("T")) {
				StringBuilder b = new StringBuilder();
				for (int j = 1; j < r.length; j++) {
					if (b.length() > 0) b.append('\n');
					b.append(r[j].replace("\t", " · "));
				}
				v = text(ctx, b.toString(), 12.5f * zk, reg, ink);
				l.topMargin = (int) (4 * dp);
			} else if (r[0].equals("s")) {
				v = text(ctx, r[1], 11.5f * zk, reg, dim);
				l.topMargin = (int) (8 * dp);
			} else {
				v = text(ctx, r[1], 13 * zk, r[0].equals("V") ? bold : reg, ink);
				l.topMargin = (int) (4 * dp);
			}
			v.setLineSpacing(1.5f * dp, 1);
			col.addView(v, l);
		}
	}

	/**
	 * Method 1 (a tap on the Earthquake box): the information inside the main screen, WIDTH pixels
	 * wide: ‹ with the title, the square with what happened, the map, the two boxes under it, the
	 * details and the other earthquakes.
	 */
	static LinearLayout info(Context ctx, Quakes.Quake q, Cities.City town, Screen s, String[] t, boolean dark, Typeface reg, Typeface bold, float zk, int width, Hooks k) {
		float dp = ctx.getResources().getDisplayMetrics().density;
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(0, (int) (4 * dp), 0, (int) (24 * dp));
		col.addView(head(ctx, dark, bold, zk, 38, k.back));
		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		tl.topMargin = (int) (12 * dp);
		col.addView(titleRow(ctx, q, t, dark, reg, bold, zk, 54, k.full), tl);
		final FrameLayout map = mapBox(ctx, q, town, t, dark, k.full, 16 * dp);
		LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) Math.min(300 * dp, width * 0.62f));
		ml.topMargin = (int) (12 * dp);
		col.addView(map, ml);
		fit(map, 12 * dp, 12 * dp, 12 * dp, 12 * dp);
		LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		bl.topMargin = (int) (8 * dp);
		col.addView(boxes(ctx, q, s, t, dark, reg, bold, zk, width < 330 * dp * zk, 0), bl);
		LinearLayout d = new LinearLayout(ctx);
		d.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		dl.topMargin = (int) (10 * dp);
		col.addView(d, dl);
		details(d, ctx, q, t, dark, reg, bold, zk, k.refresh, k.refreshText, k.refreshName);
		more(col, ctx, k.more, dark, reg, bold, zk);
		return col;
	}

	/**
	 * Method 2 (☰ › Earthquakes, a tap on an earthquake): the whole screen. Portrait: ‹ with the
	 * title, the square, the map, the two boxes under it, the details, all scrolling. Landscape:
	 * the map always at the left (‹ and the title floating on it, the two boxes under it), the
	 * information at the right. ‹ or Back return to the list.
	 */
	static Dialog full(Context ctx, Quakes.Quake q, Cities.City town, Screen s, boolean dark, Typeface reg, Typeface bold, float zk, Hooks k) {
		final float dp = ctx.getResources().getDisplayMetrics().density;
		String[] t = s.quakeTexts(q);
		final Dialog d = new Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
		d.requestWindowFeature(Window.FEATURE_NO_TITLE);
		Runnable close = new Runnable() {
			public void run() {
				d.dismiss();
			}
		};
		android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
		boolean land = dm.widthPixels > dm.heightPixels;
		int pad = (int) (16 * dp);
		FrameLayout root = new FrameLayout(ctx);
		root.setBackgroundColor(Theme.page(dark));
		if (!land) {
			ScrollView sv = new ScrollView(ctx);
			sv.setVerticalScrollBarEnabled(false);
			LinearLayout col = new LinearLayout(ctx);
			col.setOrientation(LinearLayout.VERTICAL);
			col.setPadding(pad, (int) (36 * dp), pad, (int) (28 * dp));
			col.addView(head(ctx, dark, bold, zk, 40, close));
			LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			tl.topMargin = (int) (12 * dp);
			col.addView(titleRow(ctx, q, t, dark, reg, bold, zk, 56, null), tl);
			FrameLayout map = mapBox(ctx, q, town, t, dark, null, 16 * dp);
			int inner = dm.widthPixels - 2 * pad;
			LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) Math.min(dm.heightPixels * 0.5f, inner * 1.05f));
			ml.topMargin = (int) (12 * dp);
			col.addView(map, ml);
			fit(map, 12 * dp, 12 * dp, 12 * dp, 12 * dp);
			LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			bl.topMargin = (int) (8 * dp);
			col.addView(boxes(ctx, q, s, t, dark, reg, bold, zk, inner < 330 * dp * zk, 0), bl);
			LinearLayout dd = new LinearLayout(ctx);
			dd.setOrientation(LinearLayout.VERTICAL);
			LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			dl.topMargin = (int) (10 * dp);
			col.addView(dd, dl);
			details(dd, ctx, q, t, dark, reg, bold, zk, k.refresh, k.refreshText, k.refreshName);
			more(col, ctx, k.more, dark, reg, bold, zk);
			sv.addView(col);
			root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		} else {
			LinearLayout split = new LinearLayout(ctx);
			split.setOrientation(LinearLayout.HORIZONTAL);
			// The left half: the map with ‹ and the title floating on it, the two boxes under it.
			LinearLayout left = new LinearLayout(ctx);
			left.setOrientation(LinearLayout.VERTICAL);
			FrameLayout mf = new FrameLayout(ctx);
			final FrameLayout map = mapBox(ctx, q, town, t, dark, null, 0);
			mf.addView(map, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			final View hd = head(ctx, dark, bold, zk * 0.9f, 36, close);
			hd.setPadding((int) (8 * dp), (int) (6 * dp), (int) (14 * dp), (int) (6 * dp));
			hd.setBackground(Glass.panelBg(dark, 18 * dp, dp));
			FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
			hl.leftMargin = hl.rightMargin = hl.topMargin = (int) (10 * dp);
			mf.addView(hd, hl);
			left.addView(mf, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
			View bx = boxes(ctx, q, s, t, dark, reg, bold, zk * 0.9f, false, 0);
			bx.setPadding((int) (8 * dp), (int) (8 * dp), (int) (8 * dp), (int) (8 * dp));
			left.addView(bx, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			split.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
			// The right half: the information, scrolling when it does not fit.
			ScrollView sv = new ScrollView(ctx);
			sv.setVerticalScrollBarEnabled(false);
			LinearLayout col = new LinearLayout(ctx);
			col.setOrientation(LinearLayout.VERTICAL);
			col.setPadding((int) (18 * dp), (int) (14 * dp), (int) (22 * dp), (int) (20 * dp));
			col.addView(titleRow(ctx, q, t, dark, reg, bold, zk, 50, null));
			LinearLayout dd = new LinearLayout(ctx);
			dd.setOrientation(LinearLayout.VERTICAL);
			LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			dl.topMargin = (int) (10 * dp);
			col.addView(dd, dl);
			details(dd, ctx, q, t, dark, reg, bold, zk, k.refresh, k.refreshText, k.refreshName);
			more(col, ctx, k.more, dark, reg, bold, zk);
			sv.addView(col);
			split.addView(sv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
			root.addView(split, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			root.post(new Runnable() {
				public void run() {
					fit(map, 16 * dp, hd.getBottom() + 8 * dp, 16 * dp, 12 * dp);
				}
			});
		}
		d.setContentView(root);
		Window w = d.getWindow();
		if (w != null) w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
		d.show();
		return d;
	}
}
