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
	void show(Cities cities, double tLat, double tLon, float head, boolean dark, boolean imperial) {
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
		townOnly = false;
		nearby();
		invalidate();
	}

	/** The three listed towns nearest to the place shown. */
	private void nearby() {
		towns.clear();
		if (cities == null || Double.isNaN(lat)) return;
		for (Cities.City c : cities.nearest(lat, lon, 3)) towns.add(new Town(c.name, bearing(lat, lon, c.lat, c.lon), km(lat, lon, c.lat, c.lon)));
		if (picked >= towns.size()) picked = -1;
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
		int page = k[0], ink = k[1], dim = k[2], line = k[3], band = k[4], box = k[5], accent = k[10], bandInk = k[15];
		c.drawColor(0x99000000);
		boolean tablet = Math.min(W, H) / dp >= 600, portrait = H > W;
		float u = Math.min(W, H) / 1080f * (tablet ? 0.85f : 1f);
		float m = (tablet ? 40 : 24) * u, r = 28 * u, hb = 78 * u, fs = (portrait ? 34 : 38) * u, bh = 104 * u;
		float R, dcx, dcy, x0, rw, iy;
		int n = Math.min(3, towns.size());
		if (portrait) {
			float cw = W - 2 * m;
			R = cw * (tablet ? 0.3f : 0.36f);
			float ch = hb + R * 2.5f + 40 * u + fs * 1.75f * 3 + fs + 60 * u + (n > 0 ? fs * 1.75f * (n + 0.6f) + 40 * u : 0) + bh + 40 * u;
			ch = Math.min(ch, H - 2 * m);
			card.set(m, (H - ch) / 2, m + cw, (H + ch) / 2);
			dcx = W / 2f;
			dcy = card.top + hb + R * 1.25f;
			x0 = card.left + 44 * u;
			rw = cw - 88 * u;
			iy = dcy + R * 1.25f + 40 * u;
		} else {
			float ch = H - 2 * m, cw = Math.min(W - 2 * m, ch * (tablet ? 1.6f : 1.75f));
			card.set((W - cw) / 2, m, (W + cw) / 2, m + ch);
			R = (ch - hb) * 0.36f;
			dcx = card.left + 60 * u + R * 1.15f;
			dcy = card.top + hb + (ch - hb) / 2;
			x0 = dcx + R * 1.3f + 50 * u;
			rw = card.right - 50 * u - x0;
			iy = card.top + hb + 70 * u;
		}
		// The window: a frame, the title band on top.
		fill(c, box);
		c.drawRoundRect(card, r, r, p);
		Path top = new Path();
		top.addRoundRect(new RectF(card.left, card.top, card.right, card.top + hb), new float[] { r, r, r, r, 0, 0, 0, 0 }, Path.Direction.CW);
		fill(c, band);
		c.drawPath(top, p);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(3 * u);
		p.setColor(band);
		c.drawRoundRect(card, r, r, p);
		text(c, Tr.t("COMPASS", "BUSOLĂ"), card.centerX(), card.top + hb / 2, hb * 0.48f, bandInk, true, Paint.Align.CENTER);
		dial(c, dcx, dcy, R, u, ink, dim, line, box, accent, band);
		if (showTowns) marks(c, dcx, dcy, R, u, page, box, accent, band, W);
		else for (float[] mk : marks) mk[2] = 0;
		// The values.
		String[][] rows = { { Tr.t("Direction", "Direcție"), Float.isNaN(heading) ? "–" : dir8(heading) + " · " + Math.round((heading % 360 + 360) % 360) % 360 + "°" },
				{ Tr.t("Latitude", "Latitudine"), Double.isNaN(lat) ? "–" : coord(lat, true) },
				{ Tr.t("Longitude", "Longitudine"), Double.isNaN(lon) ? "–" : coord(lon, false) },
				{ townOnly ? Tr.t("Place", "Loc") : Tr.t("GPS accuracy", "Precizie GPS"),
						townOnly ? Tr.t("the town (no GPS fix)", "localitatea (fără GPS)") : Float.isNaN(accuracy) ? "–" : "±" + Math.round(imperial ? accuracy * 3.28084 : accuracy) + (imperial ? " ft" : " m") } };
		for (int i = 0; i < rows.length; i++) {
			float y = iy + i * fs * 1.75f;
			text(c, rows[i][0], x0, y, fs * 0.85f, dim, false, Paint.Align.LEFT);
			float vs = fs;
			while (vs > fs * 0.6f && width(rows[i][1], vs, true) > rw - width(rows[i][0], fs * 0.85f, false) - 20 * u) vs *= 0.94f;
			text(c, rows[i][1], x0 + rw, y, vs, ink, true, Paint.Align.RIGHT);
			if (i < rows.length - 1) {
				fill(c, line);
				c.drawRect(x0, y + fs * 0.88f, x0 + rw, y + fs * 0.88f + Math.max(1, 1.2f * u), p);
			}
		}
		float ly = iy + (rows.length - 1) * fs * 1.75f + fs * 0.6f + 60 * u;
		if (n > 0) {
			text(c, Tr.t("NEARBY TOWNS", "LOCALITĂȚI APROPIATE"), x0, ly, fs * 0.8f, band, true, Paint.Align.LEFT);
			for (int i = 0; i < n; i++) {
				Town t = towns.get(i);
				float y = ly + (i + 1) * fs * 1.75f;
				house(c, x0 + fs * 0.4f, y, fs * 0.42f, TOWN_COL[i], box, i == picked, band, u * 0.6f);
				String d = dist(t.km) + " · " + dir8(t.bearing) + " " + Math.round(t.bearing) + "°";
				float dw = width(d, fs * 0.9f, false);
				String nm = t.name;
				float ns = fs;
				while (ns > fs * 0.6f && width(nm, ns, true) > rw - fs * 1.1f - dw - 20 * u) ns *= 0.94f;
				text(c, nm, x0 + fs * 1.1f, y, ns, ink, true, Paint.Align.LEFT);
				text(c, d, x0 + rw, y, fs * 0.9f, dim, false, Paint.Align.RIGHT);
			}
		}
		buttons(c, x0, card.bottom - bh - (portrait ? 36 : 40) * u, rw, bh, u, ink, page, box, band);
	}

	private void dial(Canvas c, float cx, float cy, float R, float u, int ink, int dim, int line, int box, int accent, int band) {
		boolean has = !Float.isNaN(heading);
		float hd = has ? heading : 0;
		fill(c, mix(box, ink, 0.07f));
		c.drawCircle(cx, cy, R, p);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(3 * u);
		p.setColor(band);
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
		fill(c, box);
		c.drawCircle(cx, cy, R * 0.3f, p);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(2 * u);
		p.setColor(line);
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

	/** The towns as small houses on the rim; the picked one with its name and distance. */
	private void marks(Canvas c, float cx, float cy, float R, float u, int page, int box, int accent, int band, int W) {
		float hd = Float.isNaN(heading) ? 0 : heading, rr = R * 0.075f;
		for (float[] mk : marks) mk[2] = 0;
		for (int i = 0; i < Math.min(3, towns.size()); i++) {
			double a = Math.toRadians(towns.get(i).bearing - hd);
			float x = cx + R * 1.11f * (float) Math.sin(a), y = cy - R * 1.11f * (float) Math.cos(a);
			marks[i][0] = x;
			marks[i][1] = y;
			marks[i][2] = rr * 1.8f;
			house(c, x, y, rr, TOWN_COL[i], box, i == picked, band, u);
		}
		if (picked >= 0 && picked < towns.size()) {
			Town t = towns.get(picked);
			float x = marks[picked][0], y = marks[picked][1];
			String t1 = t.name, t2 = dist(t.km) + " · " + dir8(t.bearing) + " " + Math.round(t.bearing) + "°";
			float fs = R * 0.085f, bw = Math.max(width(t1, fs, true), width(t2, fs * 0.8f, false)) + fs * 1.6f, bh = fs * 2.9f;
			float bx = Math.min(Math.max(x - bw / 2, 12 * u), W - bw - 12 * u);
			boolean below = y < cy + R * 0.6f;
			float by = below ? y + rr + fs * 0.7f : y - rr - fs * 0.7f - bh;
			fill(c, band);
			c.drawRoundRect(new RectF(bx, by, bx + bw, by + bh), fs * 0.5f, fs * 0.5f, p);
			Path tp = new Path();
			float ty = below ? by : by + bh;
			tp.moveTo(x - fs * 0.45f, ty);
			tp.lineTo(x + fs * 0.45f, ty);
			tp.lineTo(x, below ? ty - fs * 0.5f : ty + fs * 0.5f);
			tp.close();
			c.drawPath(tp, p);
			text(c, t1, bx + bw / 2, by + bh * 0.33f, fs, page, true, Paint.Align.CENTER);
			text(c, t2, bx + bw / 2, by + bh * 0.72f, fs * 0.8f, page, false, Paint.Align.CENTER);
		}
	}

	private void buttons(Canvas c, float x, float y, float w, float h, float u, int ink, int page, int box, int band) {
		String[] labels = { Tr.t("Share", "Partajare"), Tr.t("Towns", "Localități"), Tr.t("Close", "Închidere") };
		RectF[] rs = { bShare, bTowns, bClose };
		float g = h * 0.25f, bw = (w - 2 * g) / 3, fs = h * 0.3f;
		for (String l : labels) while (fs > 8 && width(l, fs, true) + h * 0.6f > bw - 16 * u) fs *= 0.94f;
		for (int i = 0; i < 3; i++) {
			RectF r = rs[i];
			r.set(x + i * (bw + g), y, x + i * (bw + g) + bw, y + h);
			boolean on = i == 1 && showTowns;
			int fg = on ? page : ink;
			fill(c, on ? band : box);
			c.drawRoundRect(r, h * 0.18f, h * 0.18f, p);
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(2.5f * u);
			p.setColor(band);
			c.drawRoundRect(r, h * 0.18f, h * 0.18f, p);
			float tw = width(labels[i], fs, true), s = h * 0.45f, sx = r.left + (bw - tw - s * 1.15f) / 2 + s / 2, cy = r.centerY();
			if (i == 0) {
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeWidth(s * 0.09f);
				p.setColor(fg);
				c.drawLine(sx + s * 0.3f, cy - s * 0.32f, sx - s * 0.3f, cy, p);
				c.drawLine(sx - s * 0.3f, cy, sx + s * 0.3f, cy + s * 0.32f, p);
				fill(c, fg);
				c.drawCircle(sx + s * 0.3f, cy - s * 0.32f, s * 0.15f, p);
				c.drawCircle(sx - s * 0.3f, cy, s * 0.15f, p);
				c.drawCircle(sx + s * 0.3f, cy + s * 0.32f, s * 0.15f, p);
			} else if (i == 1) {
				fill(c, fg);
				c.drawCircle(sx, cy - s * 0.12f, s * 0.3f, p);
				Path pin = new Path();
				pin.moveTo(sx - s * 0.26f, cy - s * 0.02f);
				pin.lineTo(sx + s * 0.26f, cy - s * 0.02f);
				pin.lineTo(sx, cy + s * 0.42f);
				pin.close();
				c.drawPath(pin, p);
				fill(c, on ? band : box);
				c.drawCircle(sx, cy - s * 0.12f, s * 0.11f, p);
			} else {
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeWidth(s * 0.1f);
				p.setColor(fg);
				c.drawLine(sx - s * 0.25f, cy - s * 0.25f, sx + s * 0.25f, cy + s * 0.25f, p);
				c.drawLine(sx - s * 0.25f, cy + s * 0.25f, sx + s * 0.25f, cy - s * 0.25f, p);
			}
			text(c, labels[i], sx + s * 0.62f, cy, fs, fg, true, Paint.Align.LEFT);
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
		fill(c, color);
		c.drawCircle(x, y, rr, p);
		float s = rr * 0.55f;
		Path h = new Path();
		h.moveTo(x - s, y);
		h.lineTo(x, y - s * 0.95f);
		h.lineTo(x + s, y);
		h.close();
		fill(c, houseCol);
		c.drawPath(h, p);
		c.drawRect(x - s * 0.7f, y - 1, x + s * 0.7f, y + s * 0.8f, p);
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
			picked = hit >= 0 && hit != picked ? hit : -1;
			if (hit >= 0) Log.i(getContext(), "GEST", "busolă: atingere localitate " + towns.get(hit).name);
			invalidate();
		}
		return true;
	}

	/** The coordinates to other apps (WhatsApp, maps, messages…) with a map link. */
	private void share() {
		if (Double.isNaN(lat)) return;
		String q = String.format(java.util.Locale.US, "%.5f,%.5f", lat, lon);
		String body = coord(lat, true) + ", " + coord(lon, false) + "\nhttps://maps.google.com/?q=" + q;
		Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body);
		Log.i(getContext(), "GEST", "busolă: partajare coordonate");
		try {
			getContext().startActivity(Intent.createChooser(i, Tr.t("Share the coordinates", "Partajează coordonatele")));
		} catch (Exception ex) {
			Log.w(getContext(), "BUSOLA", "partajare imposibilă: " + ex);
		}
	}
}
