package ro.meteoalarm.android.preview;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;

import javax.imageio.ImageIO;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Gfx;
import ro.meteoalarm.android.core.Screen;
import ro.meteoalarm.android.core.Texts;
import ro.meteoalarm.android.core.Warnings;
import ro.meteoalarm.android.core.Weather;

/** Desktop preview: draws the phone screen with Java2D to a PNG (java Preview ASSETS OUT.png [W H] [en] [F] [warn] [tab] [bar]). */
public final class Preview {
	static final class J2d implements Gfx {
		final Graphics2D g;
		final File assets;
		final Font reg, bold;

		J2d(Graphics2D g, File assets) throws Exception {
			this.g = g;
			this.assets = assets;
			reg = Font.createFont(Font.TRUETYPE_FONT, new File(assets, "fonts/LiberationSans-Regular.ttf"));
			bold = Font.createFont(Font.TRUETYPE_FONT, new File(assets, "fonts/LiberationSans-Bold.ttf"));
		}

		public void rect(float x, float y, float w, float h, int c) {
			g.setColor(new Color(c, true));
			g.fill(new java.awt.geom.Rectangle2D.Float(x, y, w, h));
		}

		public void roundRect(float x, float y, float w, float h, float r, int c) {
			g.setColor(new Color(c, true));
			g.fill(new RoundRectangle2D.Float(x, y, w, h, 2 * r, 2 * r));
		}

		Font font(boolean b, float px) {
			return (b ? bold : reg).deriveFont(px / 1.1172f);
		}

		public void text(boolean b, float px, float top, float left, String s, int c) {
			g.setColor(new Color(c, true));
			g.setFont(font(b, px));
			g.drawString(s, left, top + px * 0.81f);
		}

		public float width(boolean b, float px, String s) {
			return (float) font(b, px).getStringBounds(s, g.getFontRenderContext()).getWidth();
		}

		public void icon(String a, float x, float y, float w, float h, int c) {
			try {
				BufferedImage im = ImageIO.read(new File(assets, a));
				BufferedImage m = new BufferedImage(im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_ARGB);
				for (int j = 0; j < im.getHeight(); j++)
					for (int i = 0; i < im.getWidth(); i++) {
						int p = im.getRGB(i, j);
						int lum = (((p >> 16) & 255) * 299 + ((p >> 8) & 255) * 587 + (p & 255) * 114) / 1000;
						m.setRGB(i, j, ((255 - lum) * (p >>> 24) / 255) << 24 | (c & 0xFFFFFF));
					}
				g.drawImage(m, Math.round(x), Math.round(y), Math.round(w), Math.round(h), null);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		public void arc(float cx, float cy, float r, float st, float sw, float stroke, int c) {
			g.setColor(new Color(c, true));
			g.setStroke(new BasicStroke(stroke));
			// Java2D angles run counter-clockwise; Android's clockwise.
			g.draw(new Arc2D.Float(cx - r, cy - r, 2 * r, 2 * r, -st, -sw, Arc2D.OPEN));
		}

		private java.awt.Shape oldClip;

		public void clip(float x, float y, float w, float h) {
			oldClip = g.getClip();
			g.clip(new java.awt.geom.Rectangle2D.Float(x, y, w, h));
		}

		public void unclip() {
			g.setClip(oldClip);
		}

		public void poly(float[] xy, int c) {
			Path2D.Float p = new Path2D.Float();
			p.moveTo(xy[0], xy[1]);
			for (int k = 2; k < xy.length; k += 2) p.lineTo(xy[k], xy[k + 1]);
			p.closePath();
			g.setColor(new Color(c, true));
			g.fill(p);
		}
	}

	/** The live data, or the fixture next to this file when the network is closed. */
	static String fetch(String url, String fixture) throws Exception {
		try {
			return Weather.get(url);
		} catch (Exception e) {
			System.out.println("offline (" + e.getMessage() + "), using fixture " + fixture);
			return fixture == null ? "" : new String(Files.readAllBytes(new File("preview/fixtures", fixture).toPath()), "UTF-8");
		}
	}

	public static void main(String[] a) throws Exception {
		File assets = new File(a[0]);
		int pw = a.length > 3 ? Integer.parseInt(a[2]) : 1080, ph = a.length > 3 ? Integer.parseInt(a[3]) : 2340;
		String opts = String.join(" ", a);
		if (opts.contains(" land")) { int t = pw; pw = ph; ph = t; }
		Cities cities = new Cities(new String(Files.readAllBytes(new File(assets, "cities.txt").toPath()), "UTF-8"));
		Screen s = new Screen();
		s.city = cities.find(opts.contains("brasov") ? "Brașov" : "Călărași");
		if (opts.contains("sector")) s.city = new Cities.City("București", "București, Sector 3", s.city.lat, s.city.lon);
		s.T = new Texts(opts.contains(" en"));
		s.unitF = opts.contains(" F");
		s.clk12 = opts.contains(" 12h");
		s.dark = opts.contains(" dark");
		s.w = Weather.parse(fetch(Weather.forecastUrl(s.city.lat, s.city.lon, "Europe/Bucharest"), opts.contains(" dry") ? "forecast-dry.json" : "forecast.json"));
		Object[] pol = Weather.parsePollen(fetch(Weather.pollenUrl(s.city.lat, s.city.lon), "pollen.json"));
		s.pollenLvl = (Integer) pol[0];
		s.pollenPlant = (String) pol[1];
		long now = System.currentTimeMillis();
		s.warns = Warnings.parse(fetch(Warnings.FEED, null), s.city.county, now);
		if (opts.contains(" warn")) {
			s.warns = new ArrayList<Warnings.Warn>();
			int n = opts.contains(" warn3") ? 4 : 2;
			for (int k = 0; k < n; k++) {
				Warnings.Warn w = new Warnings.Warn();
				w.level = k == 0 ? 3 : k == 3 ? 1 : k;
				w.type = k == 0 ? "wind" : k == 1 ? "rain" : k == 2 ? "high-temperature" : "thunderstorm";
				w.from = now - 3600000;
				w.until = now + (20 + k) * 3600000L;
				s.warns.add(w);
			}
		}
		if (opts.contains(" page1")) s.hours.scroll = 700;
		if (opts.contains(" snow")) {
			s.w.temp = -12.4;
			s.w.code = 73;
			for (int k = 3; k < s.w.probs.length; k++) { s.w.probs[k] = 80; s.w.wet[k] = true; s.w.snowy[k] = k > 5; }
			s.w.rainAt = s.w.hours.get(3);
		}
		s.batt = 78;
		s.fetched = now;
		s.now = Calendar.getInstance();
		s.tablet = opts.contains(" tab");
		s.brightBar = opts.contains(" bar");
		s.sensorTheme = opts.contains(" sensor");
		s.nightClock = opts.contains(" night");
		if (opts.contains(" charging")) s.charging = true;
		if (opts.contains(" head")) s.heading = 60;
		if (opts.contains(" info")) {
			s.w.humidity = 87; s.w.pressure = 1021.7; s.w.pressTrend = -1.6; s.w.visibility = 8400; s.aqi = 27; s.w.precip = 0.4;
		}
		if (opts.contains(" scroll1")) s.info.scroll = 231;
		if (opts.contains(" scroll2")) s.info.scroll = 693;
		if (opts.contains(" scroll3")) { s.info.scroll = 5000; s.grid.scroll = 5000; s.days.scroll = 5000; };
		if (opts.contains(" days2")) s.days.scroll = 5000;
		if (opts.contains(" evening")) s.now.set(Calendar.HOUR_OF_DAY, 22);
		if (opts.contains(" note")) s.note = "Actualizez vremea…";
		s.brightLevel = 0.45f;
		float[] f = Screen.fit(pw, ph, s.tablet);
		float scale = f[0], cw = f[1], ch = f[2], ox = f[3], oy = f[4];
		BufferedImage img = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setColor(new Color(s.dark ? 0x262624 : 0xFFFFFF));
		g.fillRect(0, 0, pw, ph);
		g.translate(ox, oy);
		g.scale(scale, scale);
		s.draw(new J2d(g, assets), cw, ch);
		ImageIO.write(img, "png", new File(a[1]));
		System.out.println("warnings for " + s.city.county + ": " + s.warns.size() + ", pollen " + s.pollenLvl + " " + s.pollenPlant + ", code " + s.w.code);
	}
}
