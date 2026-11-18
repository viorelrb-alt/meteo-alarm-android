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
		p.setShader(new LinearGradient(0, 0, s * 0.4f, s, 0xFFF59E0B, 0xFFEA580C, Shader.TileMode.CLAMP));
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
		return root;
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
}
