package ro.meteoalarm.android;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Theme;
import ro.meteoalarm.android.core.Tr;

/**
 * v4.00 (Viorel): a tap on the Compass box opens this window over the screen: a big compass that
 * turns with the phone, the direction, the GPS coordinates and, around the dial, up to three
 * marks toward the nearest towns (a tap on one: its name, distance and direction). Buttons:
 * Share (the coordinates to other apps), Towns (the marks on or off) and Close. As wide as the
 * screen in portrait, as high as it in landscape (the mockups machete-busola-v400).
 */
final class CompassPanel extends View {
	/** A nearby town: its name, the direction to it (degrees from north) and the distance in km. */
	static final class Town {
		final String name;
		final double bearing, km;

		Town(String name, double bearing, double km) {
			this.name = name;
			this.bearing = bearing;
			this.km = km;
		}
	}

	private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
	/** v4.00 (Viorel): each town its own colour, the same on the rim and in the list. */
	static final int[] TOWN_COL = { 0xFFD97757, 0xFF4F9BE0, 0xFF5DB07A };
	private final Typeface reg, bold;
	private final float dp;
	private boolean open, dark, imperial;
	private Runnable onClose;
	/** The phone's heading in degrees (NaN: no compass). */
	private float heading = Float.NaN;
	private double lat = Double.NaN, lon = Double.NaN;
	private float accuracy = Float.NaN;
	/** The coordinates are the town's (no fix from the phone yet). */
	private boolean townOnly = true;
	private Cities cities;
	private final List<Town> towns = new ArrayList<Town>();
	/** v4.10 (Viorel): the town you are in (nearest, within HERE_KM), first in the list, grey, no mark on the rim. */
	private Town here;
	static final double HERE_KM = 4;
	static final int HERE_COL = 0xFF9A9A9E;
	/** The weather now, for Share (v4.10). */
	private ro.meteoalarm.android.core.Weather w;
	/** The phone's height above sea level from the GPS (m; NaN without). */
	private double alt = Double.NaN;
	private boolean showTowns = true;
	private int picked = -1;
	// Where the parts are, for the taps (pixels).
	private final RectF card = new RectF(), bShare = new RectF(), bTowns = new RectF(), bClose = new RectF();
	private final float[][] marks = new float[3][3];
	private LocationManager lm;
	private final LocationListener fix = new LocationListener() {
		public void onLocationChanged(Location l) {
			take(l);
		}

		public void onStatusChanged(String s, int i, Bundle b) {
		}

		public void onProviderEnabled(String s) {
		}

		public void onProviderDisabled(String s) {
		}
	};

	CompassPanel(Context c, Typeface reg, Typeface bold) {
		super(c);
		this.reg = reg;
		this.bold = bold;
		dp = c.getResources().getDisplayMetrics().density;
		setVisibility(GONE);
		setClickable(true);
		showTowns = Fetcher.prefs(c).getBoolean("compassTowns", true);
	}

	boolean isOpen() {
		return open;
	}

	void setOnClose(Runnable r) {
		onClose = r;
	}

	void heading(float h) {
		if (!open) return;
		if (Float.isNaN(heading) || Float.isNaN(h) || Math.abs(((h - heading + 540) % 360) - 180) > 0.8f) {
			heading = h;
			invalidate();
		}
	}

	/** Opens the window at the town (LAT, LON until the phone gives its own place). */
	void show(Cities cities, double tLat, double tLon, float head, boolean dark, boolean imperial, ro.meteoalarm.android.core.Weather w) {
		this.w = w;
		alt = Double.NaN;
		this.cities = cities;
		this.dark = dark;
		this.imperial = imperial;
		heading = head;
		lat = tLat;
		lon = tLon;
		accuracy = Float.NaN;
		townOnly = true;
		picked = -1;
		open = true;
		lm = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
		if (Locator.allowed(getContext()) && lm != null) {
			Location best = null;
			try {
				for (String pr : lm.getProviders(true)) {
					Location l = lm.getLastKnownLocation(pr);
					if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
				}
				if (best != null && System.currentTimeMillis() - best.getTime() < 30 * 60000L) take(best);
				if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, fix);
				else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, fix);
			} catch (SecurityException e) {
				Log.w(getContext(), "BUSOLA", "fără permisiunea locației: " + e);
			}
		}
		nearby();
		logTowns();
		Log.i(getContext(), "BUSOLA", "fereastra busolei deschisă" + (Float.isNaN(heading) ? " (fără senzor)" : "") + (townOnly ? ", coordonatele localității" : ", coordonate GPS"));
		setVisibility(VISIBLE);
		invalidate();
	}

	void hide() {
		if (!open) return;
		open = false;
		try {
			if (lm != null) lm.removeUpdates(fix);
		} catch (Exception e) {
		}
		setVisibility(GONE);
		if (onClose != null) onClose.run();
	}

	private void take(Location l) {
		lat = l.getLatitude();
		lon = l.getLongitude();
		accuracy = l.hasAccuracy() ? l.getAccuracy() : Float.NaN;
		alt = altitude(l);
		townOnly = false;
		nearby();
		invalidate();
	}

	/** The three listed towns nearest to the place shown. */
	private void nearby() {
		towns.clear();
		here = null;
		if (cities == null || Double.isNaN(lat)) return;
		// v4.20 (Viorel): the villages too, from the list of every locality of Romania; v4.40 also
		// from the lists of the other countries and US states on the phone.
		List<Cities.City> all = new ArrayList<Cities.City>(cities.nearest(lat, lon, 4));
		all.addAll(Places.nearest(getContext(), lat, lon, 8));
		List<Town> l = new ArrayList<Town>();
		for (Cities.City c : all) {
			Town t = new Town(c.name, bearing(lat, lon, c.lat, c.lon), km(lat, lon, c.lat, c.lon));
			boolean dup = false;
			for (Town o : l) dup |= ro.meteoalarm.android.core.Warnings.fold(o.name).equals(ro.meteoalarm.android.core.Warnings.fold(t.name)) && km(lat, lon, c.lat, c.lon) - o.km < 4;
			if (dup) continue;
			int i = 0;
			while (i < l.size() && l.get(i).km <= t.km) i++;
			l.add(i, t);
		}
		for (Town t : l) {
			if (here == null && towns.isEmpty() && t.km <= HERE_KM) here = t;
			else if (towns.size() < 3) towns.add(t);
		}
		if (picked >= towns.size()) picked = -1;
	}

	/** v5.00: the listed towns in the log, to check the marks later. */
	private void logTowns() {
		StringBuilder b = new StringBuilder("localități:");
		if (here != null) b.append(" aici ").append(here.name).append(";");
		for (Town t : towns) b.append(" ").append(t.name).append(String.format(java.util.Locale.US, " %.1f km %.0f°;", t.km, t.bearing));
		Log.i(getContext(), "BUSOLA", b.toString());
	}

	static double km(double a1, double o1, double a2, double o2) {
		double f1 = Math.toRadians(a1), f2 = Math.toRadians(a2), df = f2 - f1, dl = Math.toRadians(o2 - o1);
		double h = Math.sin(df / 2) * Math.sin(df / 2) + Math.cos(f1) * Math.cos(f2) * Math.sin(dl / 2) * Math.sin(dl / 2);
		return 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
	}

	static double bearing(double a1, double o1, double a2, double o2) {
		double f1 = Math.toRadians(a1), f2 = Math.toRadians(a2), dl = Math.toRadians(o2 - o1);
		double y = Math.sin(dl) * Math.cos(f2), x = Math.cos(f1) * Math.sin(f2) - Math.sin(f1) * Math.cos(f2) * Math.cos(dl);
		return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
	}

	private static String dir8(double deg) {
		return Tr.dirs8()[(int) Math.round(((deg % 360) + 360) % 360 / 45.0) % 8];
	}

	private String dist(double km) {
		if (imperial) return String.format(java.util.Locale.US, "%.1f mi", km / 1.609344).replace('.', Tr.dot());
		return (km < 10 ? String.format(java.util.Locale.US, "%.1f", km) : Long.toString(Math.round(km))).replace('.', Tr.dot()) + " km";
	}

	private static String coord(double v, boolean latitude) {
		String h = latitude ? (v >= 0 ? "N" : "S") : (v >= 0 ? "E" : "W");
		if (!latitude && Tr.ro() && v < 0) h = "V";
		return String.format(java.util.Locale.US, "%.4f° %s", Math.abs(v), h).replace('.', Tr.dot());
	}

	// --- Drawing -----------------------------------------------------------------------------

	private int[] col() {
		return Theme.colors(dark);
	}

	private void text(Canvas c, String s, float x, float y, float size, int color, boolean b, Paint.Align a) {
		p.setStyle(Paint.Style.FILL);
		p.setTypeface(b ? bold : reg);
		p.setTextSize(size);
		p.setTextAlign(a);
		p.setColor(color);
		Paint.FontMetrics fm = p.getFontMetrics();
		c.drawText(s, x, y - (fm.ascent + fm.descent) / 2, p);
	}

	private float width(String s, float size, boolean b) {
		p.setTypeface(b ? bold : reg);
		p.setTextSize(size);
		return p.measureText(s);
	}

	private void fill(Canvas c, int color) {
		p.setStyle(Paint.Style.FILL);
		p.setColor(color);
	}

	@Override
	protected void onDraw(Canvas c) {
		if (!open) return;
		int W = getWidth(), H = getHeight();
		int[] k = col();
		int page = k[0], ink = k[1], dim = k[2], line = k[3], band = k[4], box = k[5], accent = k[10];
		boolean tablet = Math.min(W, H) / dp >= 600, portrait = H > W;
		float u = Math.min(W, H) / 1080f * (tablet ? 0.85f : 1f);
		int n = Math.min(3, towns.size()), nl = n + (here != null ? 1 : 0);
		// v5.00 (Viorel, mockup 3-busola): a glass window, the title and X on top, the dial, the
		// values in a 2×2 grid of cards, the nearby towns (a tap on one picks its mark) and two buttons.
		float m = (tablet ? 40 : 40) * u, r = 44 * u, pad = 44 * u;
		float hb = 150 * u, gh = 124 * u, gg = 20 * u, rowH = 76 * u, bh = 100 * u;
		float R, dcx, dcy, x0, rw, iy;
		if (portrait) {
			float cw = W - 2 * m;
			R = cw * (tablet ? 0.3f : 0.33f);
			x0 = m + pad;
			rw = cw - 2 * pad;
			float rest = 2 * gh + gg + 40 * u + (nl > 0 ? 56 * u + nl * rowH : 0) + 40 * u + bh + pad;
			float ch = hb + R * 2.42f + 30 * u + rest;
			if (ch > H - 2 * m) {
				// Too high: a smaller dial first.
				R = Math.max(cw * 0.22f, (H - 2 * m - hb - 30 * u - rest) / 2.42f);
				ch = Math.min(hb + R * 2.42f + 30 * u + rest, H - 2 * m);
			}
			card.set(m, (H - ch) / 2, m + cw, (H + ch) / 2);
			dcx = W / 2f;
			dcy = card.top + hb + R * 1.21f;
			iy = dcy + R * 1.21f + 30 * u;
		} else {
			float ch = H - 2 * m * 0.6f, cw = Math.min(W - 2 * m, ch * (tablet ? 1.6f : 1.9f));
			card.set((W - cw) / 2, (H - ch) / 2, (W + cw) / 2, (H + ch) / 2);
			R = Math.min((ch - hb - pad) / 2.5f, cw * 0.2f);
			dcx = card.left + pad + R * 1.2f;
			dcy = card.top + hb + (ch - hb) / 2 - pad / 2;
			x0 = dcx + R * 1.3f + 40 * u;
			rw = card.right - pad - x0;
			iy = card.top + hb;
			// The right column must hold the grid, the towns and the buttons.
			float need = 2 * gh + gg + 30 * u + (nl > 0 ? 50 * u + nl * rowH : 0) + 30 * u + bh + pad, room = card.bottom - iy;
			if (need > room) {
				float f = Math.max(0.6f, room / need);
				gh *= f;
				gg *= f;
				rowH *= f;
				bh *= f;
			}
		}
		Glass.drawPanel(c, p, card, r, dark, dp);
		// Title, the place under it, X.
		float tx = card.left + pad, ty = card.top + 62 * u;
		text(c, Tr.t("Compass", "Busolă"), tx, ty, 50 * u, ink, true, Paint.Align.LEFT);
		String sub = (place != null && place.length() > 0 ? place + " · " : "") + Tr.t("works offline", "funcționează fără internet");
		float ss = 30 * u;
		while (ss > 16 * u && width(sub, ss, false) > card.width() - 2 * pad - 110 * u) ss *= 0.95f;
		text(c, sub, tx, ty + 56 * u, ss, dim, false, Paint.Align.LEFT);
		float xr = 40 * u;
		bClose.set(card.right - pad + 10 * u - 2 * xr, card.top + 36 * u, card.right - pad + 10 * u, card.top + 36 * u + 2 * xr);
		Glass.drawClose(c, p, bClose.centerX(), bClose.centerY(), xr, dark);
		dial(c, dcx, dcy, R, u, ink, dim, line, box, accent, band);
		if (showTowns) marks(c, dcx, dcy, R, u, page, box, accent, band, W);
		else for (float[] mk : marks) mk[2] = 0;
		// The values: a 2×2 grid of cards.
		String[][] rows = { { Tr.t("Direction", "Direcție"), Float.isNaN(heading) ? "–" : dir8(heading) + " · " + Math.round((heading % 360 + 360) % 360) % 360 + "°" },
				{ townOnly ? Tr.t("Place", "Loc") : Tr.t("GPS accuracy", "Precizie GPS"),
						townOnly ? Tr.t("the town (no GPS fix)", "localitatea (fără GPS)") : Float.isNaN(accuracy) ? "–" : "±" + Math.round(imperial ? accuracy * 3.28084 : accuracy) + (imperial ? " ft" : " m") },
				{ Tr.t("Latitude", "Latitudine"), Double.isNaN(lat) ? "–" : coord(lat, true) },
				{ Tr.t("Longitude", "Longitudine"), Double.isNaN(lon) ? "–" : coord(lon, false) } };
		float cwid = (rw - gg) / 2;
		for (int i = 0; i < 4; i++) {
			float cx = x0 + (i % 2) * (cwid + gg), cy = iy + (i / 2) * (gh + gg);
			RectF cr = new RectF(cx, cy, cx + cwid, cy + gh);
			Glass.drawCard(c, p, cr, 26 * u, dark, dp);
			String lb = rows[i][0].toUpperCase();
			float ls = gh * 0.2f;
			while (ls > 8 * u && width(lb, ls, true) > cwid - 60 * u) ls *= 0.94f;
			text(c, lb, cx + 30 * u, cy + gh * 0.32f, ls, dim, true, Paint.Align.LEFT);
			float vs = gh * 0.3f;
			while (vs > 10 * u && width(rows[i][1], vs, true) > cwid - 60 * u) vs *= 0.94f;
			text(c, rows[i][1], cx + 30 * u, cy + gh * 0.66f, vs, ink, true, Paint.Align.LEFT);
		}
		float ly = iy + 2 * gh + gg + (portrait ? 40 : 30) * u;
		for (RectF rr : rowAt) rr.setEmpty();
		if (nl > 0) {
			text(c, Tr.t("NEARBY TOWNS", "LOCALITĂȚI APROPIATE"), x0, ly + 14 * u, Math.min(32 * u, rowH * 0.42f), accent, true, Paint.Align.LEFT);
			float y0 = ly + (portrait ? 56 : 50) * u * Math.min(1, rowH / (76 * u));
			float fs = rowH * 0.44f;
			for (int j = 0; j < nl; j++) {
				int i = here != null ? j - 1 : j;
				Town t = i < 0 ? here : towns.get(i);
				float top = y0 + j * rowH, y = top + rowH / 2;
				boolean sel = i >= 0 && i == picked;
				if (i >= 0) rowAt[i].set(x0 - 14 * u, top, x0 + rw + 14 * u, top + rowH);
				if (sel) {
					p.setStyle(Paint.Style.FILL);
					p.setColor(Glass.alpha(TOWN_COL[i], dark ? 0.22f : 0.16f));
					c.drawRoundRect(rowAt[i], 18 * u, 18 * u, p);
				}
				house(c, x0 + fs * 0.45f, y, fs * 0.45f, i < 0 ? HERE_COL : TOWN_COL[i], box, false, band, u * 0.6f);
				String d = dist(t.km) + " · " + dir8(t.bearing) + " " + Math.round(t.bearing) + "°";
				float dw = width(d, fs * 0.88f, false);
				String nm = i < 0 ? t.name + " (" + Tr.t("you are here", "ești aici") + ")" : t.name;
				float ns = fs;
				while (ns > fs * 0.5f && width(nm, ns, i < 0 || sel) > rw - fs * 1.4f - dw - 20 * u) ns *= 0.94f;
				text(c, nm, x0 + fs * 1.4f, y, ns, ink, i < 0 || sel, Paint.Align.LEFT);
				text(c, d, x0 + rw, y, fs * 0.88f, dim, false, Paint.Align.RIGHT);
				if (j < nl - 1) {
					fill(c, Glass.edge(dark));
					c.drawRect(x0, top + rowH - Math.max(1, dp) / 2, x0 + rw, top + rowH + Math.max(1, dp) / 2, p);
				}
			}
		}
		buttons(c, x0, card.bottom - bh - pad * (portrait ? 0.8f : 0.6f), rw, bh, u, ink, page, box, accent);
	}

	/** The place shown in the subtitle (the town or parcel of the screen), set before show(). */
	String place;
	/** Where each listed town's row is (taps pick its mark, v5.00). */
	private final RectF[] rowAt = { new RectF(), new RectF(), new RectF() };

	private void dial(Canvas c, float cx, float cy, float R, float u, int ink, int dim, int line, int box, int accent, int band) {
		boolean has = !Float.isNaN(heading);
		float hd = has ? heading : 0;
		fill(c, Glass.card(dark));
		c.drawCircle(cx, cy, R, p);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(2 * u);
		p.setColor(Glass.edge(dark));
		c.drawCircle(cx, cy, R, p);
		p.setStrokeCap(Paint.Cap.ROUND);
		for (int b = 0; b < 360; b += 5) {
			double a = Math.toRadians(b - hd);
			boolean lg = b % 30 == 0;
			float r1 = R - 3 * u, r2 = R - R * (lg ? 0.085f : 0.045f);
			p.setStrokeWidth((lg ? 3 : 1.6f) * u);
			p.setColor(lg ? ink : dim);
			c.drawLine(cx + r1 * (float) Math.sin(a), cy - r1 * (float) Math.cos(a), cx + r2 * (float) Math.sin(a), cy - r2 * (float) Math.cos(a), p);
		}
		for (int b = 30; b < 360; b += 30) {
			if (b % 90 == 0) continue;
			double a = Math.toRadians(b - hd);
			text(c, Integer.toString(b), cx + R * 0.83f * (float) Math.sin(a), cy - R * 0.83f * (float) Math.cos(a), R * 0.06f, dim, false, Paint.Align.CENTER);
		}
		String[] d8 = Tr.dirs8();
		for (int i = 0; i < 8; i++) {
			boolean big = i % 2 == 0;
			double a = Math.toRadians(i * 45 - hd);
			float rr = R * (big ? 0.69f : 0.71f);
			text(c, d8[i], cx + rr * (float) Math.sin(a), cy - rr * (float) Math.cos(a), R * (big ? 0.115f : 0.07f), i == 0 && has ? accent : ink, big, Paint.Align.CENTER);
		}
		// The needle toward the north.
		double a = Math.toRadians(-hd);
		float sx = (float) Math.sin(a), cyy = (float) Math.cos(a), w = R * 0.045f;
		Path north = new Path(), south = new Path();
		north.moveTo(cx + R * 0.5f * sx, cy - R * 0.5f * cyy);
		north.lineTo(cx + w * cyy, cy + w * sx);
		north.lineTo(cx - w * cyy, cy - w * sx);
		north.close();
		south.moveTo(cx - R * 0.5f * sx, cy + R * 0.5f * cyy);
		south.lineTo(cx - w * cyy, cy - w * sx);
		south.lineTo(cx + w * cyy, cy + w * sx);
		south.close();
		fill(c, has ? accent : dim);
		c.drawPath(north, p);
		fill(c, line);
		c.drawPath(south, p);
		fill(c, Glass.alpha(Theme.page(dark), 0.85f));
		c.drawCircle(cx, cy, R * 0.3f, p);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(2 * u);
		p.setColor(Glass.edge(dark));
		c.drawCircle(cx, cy, R * 0.3f, p);
		if (has) {
			text(c, dir8(heading), cx, cy - R * 0.07f, R * 0.15f, ink, true, Paint.Align.CENTER);
			text(c, Math.round((heading % 360 + 360) % 360) % 360 + "°", cx, cy + R * 0.12f, R * 0.085f, dim, false, Paint.Align.CENTER);
		} else {
			String t = Tr.t("no compass", "fără busolă");
			float s = R * 0.085f;
			while (s > 8 && width(t, s, false) > R * 0.55f) s *= 0.94f;
			text(c, t, cx, cy, s, dim, false, Paint.Align.CENTER);
		}
		// Where the phone points: a fixed mark on top.
		Path tip = new Path();
		tip.moveTo(cx - R * 0.055f, cy - R - R * 0.1f);
		tip.lineTo(cx + R * 0.055f, cy - R - R * 0.1f);
		tip.lineTo(cx, cy - R + R * 0.02f);
		tip.close();
		fill(c, has ? accent : dim);
		c.drawPath(tip, p);
	}

	/**
	 * The towns as small houses on the rim; the picked one with its name and distance. v5.00
	 * (Viorel: 3 towns listed, 2 marks seen): marks closer than their size are spread apart along
	 * the rim, so two towns in the same direction both show.
	 */
	private void marks(Canvas c, float cx, float cy, float R, float u, int page, int box, int accent, int band, int W) {
		float hd = Float.isNaN(heading) ? 0 : heading, rr = R * 0.075f, rad = R * 1.11f;
		for (float[] mk : marks) mk[2] = 0;
		int n = Math.min(3, towns.size());
		double[] ang = new double[n];
		for (int i = 0; i < n; i++) ang[i] = towns.get(i).bearing;
		spread(ang, Math.toDegrees(2 * rr * 1.25f / rad));
		for (int i = 0; i < n; i++) {
			double a = Math.toRadians(ang[i] - hd);
			float x = cx + rad * (float) Math.sin(a), y = cy - rad * (float) Math.cos(a);
			marks[i][0] = x;
			marks[i][1] = y;
			marks[i][2] = rr * 1.8f;
		}
		// The picked one last, on top of the others.
		for (int i = 0; i < n; i++) if (i != picked) house(c, marks[i][0], marks[i][1], rr, TOWN_COL[i], box, false, band, u);
		if (picked >= 0 && picked < n) house(c, marks[picked][0], marks[picked][1], rr, TOWN_COL[picked], box, true, Theme.ink(dark), u);
		if (picked >= 0 && picked < towns.size()) {
			Town t = towns.get(picked);
			float x = marks[picked][0], y = marks[picked][1];
			String t1 = t.name, t2 = dist(t.km) + " · " + dir8(t.bearing) + " " + Math.round(t.bearing) + "°";
			float fs = R * 0.085f, bw = Math.max(width(t1, fs, true), width(t2, fs * 0.8f, false)) + fs * 1.6f, bh = fs * 2.9f;
			float bx = Math.min(Math.max(x - bw / 2, 12 * u), W - bw - 12 * u);
			boolean below = y < cy + R * 0.6f;
			float by = below ? y + rr + fs * 0.7f : y - rr - fs * 0.7f - bh;
			int bg = TOWN_COL[picked];
			fill(c, bg);
			c.drawRoundRect(new RectF(bx, by, bx + bw, by + bh), fs * 0.6f, fs * 0.6f, p);
			Path tp = new Path();
			float ty = below ? by : by + bh;
			tp.moveTo(x - fs * 0.45f, ty);
			tp.lineTo(x + fs * 0.45f, ty);
			tp.lineTo(x, below ? ty - fs * 0.5f : ty + fs * 0.5f);
			tp.close();
			c.drawPath(tp, p);
			text(c, t1, bx + bw / 2, by + bh * 0.33f, fs, 0xFFFFFFFF, true, Paint.Align.CENTER);
			text(c, t2, bx + bw / 2, by + bh * 0.72f, fs * 0.8f, 0xFFFFFFFF, false, Paint.Align.CENTER);
		}
	}

	/** Moves the angles A (degrees) apart until any two are at least MIN degrees apart (as little as possible). */
	static void spread(double[] a, double min) {
		int n = a.length;
		if (n < 2) return;
		for (int it = 0; it < 60; it++) {
			boolean moved = false;
			for (int i = 0; i < n; i++)
				for (int j = i + 1; j < n; j++) {
					double d = ((a[j] - a[i]) % 360 + 540) % 360 - 180;
					if (Math.abs(d) >= min - 1e-6) continue;
					double push = (min - Math.abs(d)) / 2 + 0.01, s = d >= 0 ? 1 : -1;
					if (d == 0) s = i < j ? 1 : -1;
					a[i] -= s * push;
					a[j] += s * push;
					moved = true;
				}
			if (!moved) break;
		}
		for (int i = 0; i < n; i++) a[i] = (a[i] % 360 + 360) % 360;
	}

	/** v5.00: Share (glass) and Towns (filled while the marks show); no Close: X on top, or a tap outside. */
	private void buttons(Canvas c, float x, float y, float w, float h, float u, int ink, int page, int box, int accent) {
		String[] labels = { Tr.t("Share", "Partajare"), Tr.t("Towns", "Localități") };
		RectF[] rs = { bShare, bTowns };
		float g = 26 * u, bw = Math.min((w - g) / 2, 340 * u), fs = h * 0.34f;
		float left = x + (w - 2 * bw - g) / 2;
		for (String l : labels) while (fs > 8 && width(l, fs, true) + h * 0.7f > bw - 30 * u) fs *= 0.94f;
		for (int i = 0; i < 2; i++) {
			RectF r = rs[i];
			r.set(left + i * (bw + g), y, left + i * (bw + g) + bw, y + h);
			boolean on = i == 1 && showTowns;
			int fg = on ? 0xFFFFFFFF : ink;
			if (on) {
				fill(c, accent);
				c.drawRoundRect(r, h / 2, h / 2, p);
			} else Glass.drawCard(c, p, r, h / 2, dark, dp);
			float tw = width(labels[i], fs, true), s = h * 0.42f, sx = r.left + (bw - tw - s * 1.25f) / 2 + s / 2, cy = r.centerY();
			if (i == 0) {
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeWidth(s * 0.09f);
				p.setColor(fg);
				c.drawLine(sx + s * 0.3f, cy - s * 0.32f, sx - s * 0.3f, cy, p);
				c.drawLine(sx - s * 0.3f, cy, sx + s * 0.3f, cy + s * 0.32f, p);
				c.drawCircle(sx + s * 0.3f, cy - s * 0.32f, s * 0.13f, p);
				c.drawCircle(sx - s * 0.3f, cy, s * 0.13f, p);
				c.drawCircle(sx + s * 0.3f, cy + s * 0.32f, s * 0.13f, p);
			} else {
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeWidth(s * 0.09f);
				p.setColor(fg);
				Path pin = new Path();
				pin.moveTo(sx, cy + s * 0.45f);
				pin.cubicTo(sx - s * 0.5f, cy, sx - s * 0.36f, cy - s * 0.45f, sx, cy - s * 0.45f);
				pin.cubicTo(sx + s * 0.36f, cy - s * 0.45f, sx + s * 0.5f, cy, sx, cy + s * 0.45f);
				c.drawPath(pin, p);
				c.drawCircle(sx, cy - s * 0.1f, s * 0.12f, p);
			}
			text(c, labels[i], sx + s * 0.75f, cy, fs, fg, true, Paint.Align.LEFT);
		}
	}

	/** A town's mark: a disc in its colour with a small house; PICKED: a ring around it. */
	private void house(Canvas c, float x, float y, float rr, int color, int houseCol, boolean picked, int ring, float u) {
		if (picked) {
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(3 * u);
			p.setColor(ring);
			c.drawCircle(x, y, rr + 5 * u, p);
		}
		houseMark(c, p, x, y, rr, color, houseCol);
	}

	/** The mark itself: a disc in COLOR with a small house in HOUSE (also the widgets' town mark, v4.10). */
	static void houseMark(Canvas c, Paint p, float x, float y, float rr, int color, int house) {
		p.setStyle(Paint.Style.FILL);
		p.setColor(color);
		c.drawCircle(x, y, rr, p);
		p.clearShadowLayer();
		float s = rr * 0.55f;
		Path h = new Path();
		h.moveTo(x - s, y);
		h.lineTo(x, y - s * 0.95f);
		h.lineTo(x + s, y);
		h.close();
		p.setColor(house);
		c.drawPath(h, p);
		c.drawRect(x - s * 0.7f, y - s * 0.08f, x + s * 0.7f, y + s * 0.8f, p);
	}

	private static int mix(int a, int b, float f) {
		int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f), g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f),
				bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	// --- Taps --------------------------------------------------------------------------------

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (!open) return false;
		if (e.getAction() != MotionEvent.ACTION_UP) return true;
		float x = e.getX(), y = e.getY();
		if (!card.contains(x, y)) {
			hide();
			return true;
		}
		if (bClose.contains(x, y)) {
			Log.i(getContext(), "GEST", "busolă: închidere");
			hide();
		} else if (bShare.contains(x, y)) share();
		else if (bTowns.contains(x, y)) {
			showTowns = !showTowns;
			picked = -1;
			Fetcher.prefs(getContext()).edit().putBoolean("compassTowns", showTowns).apply();
			Log.i(getContext(), "GEST", "busolă: localități " + (showTowns ? "afișate" : "ascunse"));
			invalidate();
		} else {
			int hit = -1;
			for (int i = 0; i < 3; i++) {
				float[] mk = marks[i];
				if (mk[2] > 0 && Math.hypot(x - mk[0], y - mk[1]) <= mk[2]) hit = i;
			}
			// v5.00 (Viorel): a tap on a town's row picks its mark on the dial (the marks show again if hidden).
			boolean row = false;
			if (hit < 0) for (int i = 0; i < Math.min(3, towns.size()); i++) if (rowAt[i].contains(x, y)) {
				hit = i;
				row = true;
			}
			if (row && !showTowns) {
				showTowns = true;
				Fetcher.prefs(getContext()).edit().putBoolean("compassTowns", true).apply();
			}
			picked = hit >= 0 && hit != picked ? hit : -1;
			if (hit >= 0) Log.i(getContext(), "GEST", "busolă: atingere localitate " + towns.get(hit).name + (row ? " (din listă)" : ""));
			invalidate();
		}
		return true;
	}

	/**
	 * v4.10 (Viorel): Share sends what may matter to someone else: the GPS place (with a map link),
	 * the direction, the town you are in and the nearby ones with their distance and direction, the
	 * phone's battery and the temperature; with a picture of the compass window when it can be made.
	 */
	private void share() {
		if (Double.isNaN(lat)) return;
		Context ctx = getContext();
		String q = String.format(java.util.Locale.US, "%.5f,%.5f", lat, lon);
		StringBuilder b = new StringBuilder();
		b.append(Tr.t("COMPASS", "BUSOLĂ")).append(" · Meteo Alarm\n");
		b.append(Tr.t("GPS position", "Poziție GPS")).append(": ").append(coord(lat, true)).append(", ").append(coord(lon, false));
		if (!townOnly && !Float.isNaN(accuracy)) b.append(" (±").append(Math.round(imperial ? accuracy * 3.28084 : accuracy)).append(imperial ? " ft)" : " m)");
		if (townOnly) b.append(" (").append(Tr.t("the town (no GPS fix)", "localitatea (fără GPS)")).append(")");
		b.append("\nhttps://maps.google.com/?q=").append(q);
		if (!Float.isNaN(heading)) b.append("\n").append(Tr.t("Direction", "Direcție")).append(": ").append(dir8(heading)).append(" · ").append(Math.round((heading % 360 + 360) % 360) % 360).append("°");
		if (here != null) b.append("\n").append(Tr.t("You are in", "Ești în")).append(": ").append(here.name);
		if (!towns.isEmpty()) {
			b.append("\n").append(Tr.t("Nearby towns", "Localități apropiate")).append(":");
			for (Town t : towns) b.append("\n- ").append(t.name).append(": ").append(dist(t.km)).append(" · ").append(dir8(t.bearing)).append(" ").append(Math.round(t.bearing)).append("°");
		}
		int bat = battery(ctx);
		if (bat >= 0) b.append("\n").append(Tr.t("Phone battery", "Bateria telefonului")).append(": ").append(bat).append("%");
		weather(b);
		Intent i = new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, b.toString());
		android.net.Uri shot = shot(ctx);
		if (shot != null) {
			i.setType("image/png").putExtra(Intent.EXTRA_STREAM, shot).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			i.setClipData(android.content.ClipData.newRawUri("", shot));
		} else i.setType("text/plain");
		Log.i(ctx, "GEST", "busolă: partajare" + (shot != null ? " cu imagine" : " doar text"));
		try {
			ctx.startActivity(Intent.createChooser(i, Tr.t("Share the coordinates", "Partajează coordonatele")));
		} catch (Exception ex) {
			Log.w(ctx, "BUSOLA", "partajare imposibilă: " + ex);
		}
	}

	/** Above sea level: Android 14+ gives it directly; older ones the GPS height (over the ellipsoid, some 30–45 m more in Europe). */
	private static double altitude(Location l) {
		try {
			if (android.os.Build.VERSION.SDK_INT >= 34 && (Boolean) Location.class.getMethod("hasMslAltitude").invoke(l))
				return (Double) Location.class.getMethod("getMslAltitudeMeters").invoke(l);
		} catch (Throwable t) {
		}
		return l.hasAltitude() ? l.getAltitude() : Double.NaN;
	}

	private String deg(double c) {
		return Math.round(imperial ? c * 9 / 5 + 32 : c) + (imperial ? "°F" : "°C");
	}

	/** The weather lines of Share: the air (2 m), the soil surface, at the phone's height, the wind. */
	private void weather(StringBuilder b) {
		if (w == null) return;
		b.append("\n").append(Tr.t("Air temperature (2 m)", "Temperatura aerului (2 m)")).append(": ").append(deg(w.temp));
		if (!Double.isNaN(w.soil0)) b.append("\n").append(Tr.t("At ground level (estimate)", "La nivelul solului (estimare)")).append(": ").append(deg(w.soil0));
		// The phone's height above the ground: the GPS height less the model's ground height; air
		// cools about 0.65 °C every 100 m up (only shown from 10 m up, when the GPS has a height).
		if (!townOnly && !Double.isNaN(alt) && !Double.isNaN(w.elevation)) {
			double up = alt - w.elevation;
			if (up >= 10 && up <= 3000) b.append("\n").append(Tr.t("At the phone's height", "La înălțimea telefonului")).append(" (~")
					.append(Math.round(imperial ? up * 3.28084 : up)).append(imperial ? " ft" : " m").append(Tr.t(" above the ground, estimate)", " deasupra solului, estimare)"))
					.append(": ").append(deg(w.temp - 0.0065 * (up - 2)));
		}
		if (!Double.isNaN(w.wind)) {
			b.append("\n").append(Tr.t("Wind", "Vânt")).append(": ").append(imperial ? Math.round(w.wind / 1.609344) + " mph" : Math.round(w.wind) + " km/h");
			if (!Double.isNaN(w.windDir)) b.append(", ").append(Tr.t("from ", "dinspre ")).append(dir8(w.windDir)).append(" (").append(Math.round(w.windDir) % 360).append("°)");
		}
	}

	/** The battery's charge in %, or -1. */
	private static int battery(Context ctx) {
		try {
			Intent b = ctx.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			int l = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1), s = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
			return l >= 0 && s > 0 ? Math.round(l * 100f / s) : -1;
		} catch (Exception e) {
			return -1;
		}
	}

	/** The compass window as a picture (cache/shot/busola.png), shared through ApkProvider; null if it fails. */
	private android.net.Uri shot(Context ctx) {
		try {
			int W = getWidth(), H = getHeight();
			if (W <= 0 || H <= 0 || card.width() <= 0) return null;
			android.graphics.Bitmap full = android.graphics.Bitmap.createBitmap(W, H, android.graphics.Bitmap.Config.ARGB_8888);
			Canvas c = new Canvas(full);
			int keep = picked;
			picked = -1;
			draw(c);
			picked = keep;
			int pad = Math.round(8 * dp);
			int l = Math.max(0, Math.round(card.left) - pad), t = Math.max(0, Math.round(card.top) - pad);
			int r = Math.min(W, Math.round(card.right) + pad), btm = Math.min(H, Math.round(card.bottom) + pad);
			android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(r - l, btm - t, android.graphics.Bitmap.Config.ARGB_8888);
			Canvas oc = new Canvas(out);
			oc.drawColor(col()[0]);
			oc.drawBitmap(full, -l, -t, null);
			full.recycle();
			java.io.File dir = new java.io.File(ctx.getCacheDir(), "shot");
			dir.mkdirs();
			java.io.File f = new java.io.File(dir, ApkProvider.SHOT);
			java.io.FileOutputStream os = new java.io.FileOutputStream(f);
			out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os);
			os.close();
			out.recycle();
			return ApkProvider.shotUri();
		} catch (Throwable e) {
			Log.w(ctx, "BUSOLA", "captura busolei a eșuat: " + e);
			return null;
		}
	}
}
