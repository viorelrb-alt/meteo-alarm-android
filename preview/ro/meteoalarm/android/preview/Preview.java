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
import ro.meteoalarm.android.core.Quakes;
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
		// United States (v2.30): " us" = Claremore, OK in American English with the NWS / USGS samples; " es" Spanish, " ro" Romanian.
		java.util.regex.Matcher lm = java.util.regex.Pattern.compile(" lang=(\\w+)").matcher(opts);
		if (lm.find()) { File lf = new File(assets, "lang/" + lm.group(1) + ".txt"); ro.meteoalarm.android.core.Tr.set(lm.group(1), new String(Files.readAllBytes(lf.toPath()), "UTF-8")); }
		boolean usa = opts.contains(" us");
		if (usa) {
			cities.addUS(new String(Files.readAllBytes(new File(assets, "us.txt").toPath()), "UTF-8"));
			s.city = cities.usStates.get("Oklahoma").get(0);
			String lg = opts.contains(" es") ? "es" : opts.contains(" ro") ? "ro" : "us";
			File lf = new File(assets, "lang/" + lg + ".txt");
			ro.meteoalarm.android.core.Tr.set(lg, lf.exists() ? new String(Files.readAllBytes(lf.toPath()), "UTF-8") : null);
			s.T = new Texts(lg);
		} else
		s.T = new Texts(opts.contains(" en"));
		s.unitF = opts.contains(" F") || (usa && !opts.contains(" metric"));
		s.clk12 = opts.contains(" 12h") || (usa && !opts.contains(" metric"));
		s.dark = opts.contains(" dark");
		s.w = Weather.parse(fetch(Weather.forecastUrl(s.city.lat, s.city.lon, "Europe/Bucharest"), opts.contains(" dry") ? "forecast-dry.json" : "forecast.json"));
		Object[] pol = Weather.parsePollen(fetch(Weather.pollenUrl(s.city.lat, s.city.lon), "pollen.json"));
		s.pollenLvl = (Integer) pol[0];
		s.pollenPlant = (String) pol[1];
		long now = System.currentTimeMillis();
		s.warns = Warnings.parse(fetch(Warnings.FEED, null), s.city.county, now);
		if (usa) {
			java.text.SimpleDateFormat iso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
			String nws = new String(Files.readAllBytes(new File("preview/fixtures/nws.json").toPath()), "UTF-8")
					.replace("{S}", iso.format(new java.util.Date(now - 600000L))).replace("{E1}", iso.format(new java.util.Date(now + 1800000L)))
					.replace("{E2}", iso.format(new java.util.Date(now + 5 * 3600000L))).replace("{E3}", iso.format(new java.util.Date(now + 14 * 3600000L)));
			s.warns = ro.meteoalarm.android.core.Nws.parse(nws, now);
			String qs = new String(Files.readAllBytes(new File("preview/fixtures/usgs.json").toPath()), "UTF-8")
					.replace("{T1}", Long.toString(now - 3 * 3600000L)).replace("{T2}", Long.toString(now - 11 * 3600000L));
			s.quakes = Quakes.parseUsgs(qs, s.city.lat, s.city.lon, now, Quakes.WINDOW_MS, Quakes.US_MIN_MAG, false);
			for (int k = 0; k < s.alertCount(); k++) for (String[] r : s.alertInfo(k)) System.out.println(k + " " + r[0] + ": " + r[1]);
		} else
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
		if (opts.contains(" quake")) {
			String t1 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'") {{ setTimeZone(java.util.TimeZone.getTimeZone("UTC")); }}.format(new java.util.Date(now - 2 * 3600000L));
			String t2 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'") {{ setTimeZone(java.util.TimeZone.getTimeZone("UTC")); }}.format(new java.util.Date(now - 9 * 3600000L));
			String js = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"lat\":45.68,\"lon\":26.58,\"depth\":128.0,\"mag\":4.6,\"magtype\":\"ml\",\"time\":\"" + t1 + "\",\"flynn_region\":\"ROMANIA\",\"unid\":\"a\"}},"
					+ "{\"type\":\"Feature\",\"properties\":{\"lat\":45.6,\"lon\":26.5,\"depth\":95.0,\"mag\":3.1,\"magtype\":\"ml\",\"time\":\"" + t2 + "\",\"flynn_region\":\"ROMANIA\",\"unid\":\"b\"}},"
					+ "{\"type\":\"Feature\",\"properties\":{\"lat\":42.6,\"lon\":25.5,\"depth\":10.0,\"mag\":3.5,\"magtype\":\"ml\",\"time\":\"" + t2 + "\",\"flynn_region\":\"BULGARIA\",\"unid\":\"c\"}}]}";
			s.quakes = Quakes.parse(js, "Romania", s.city.lat, s.city.lon, now);
			for (String[] r : s.alertInfo(0)) System.out.println(r[0] + ": " + r[1]);
		}
		if (opts.contains(" farm")) {
			// Farmer mode (v2.50) with a made-up farmer's forecast from today 00:00, 3 days.
			s.farmer = true;
			if (!opts.contains(" nofarm")) {
				Calendar d0 = Calendar.getInstance();
				d0.add(Calendar.DAY_OF_MONTH, -1);
				StringBuilder t = new StringBuilder(), v = new StringBuilder();
				String[] keys = { "temperature_2m", "relative_humidity_2m", "precipitation_probability", "precipitation", "rain", "showers", "snowfall", "cloud_cover",
						"wind_speed_10m", "wind_direction_10m", "wind_gusts_10m", "soil_temperature_0cm", "soil_moisture_3_to_9cm" };
				StringBuilder[] cols = new StringBuilder[keys.length];
				for (int j = 0; j < keys.length; j++) cols[j] = new StringBuilder();
				for (int h = 0; h < 96; h++) {
					String day = ro.meteoalarm.android.core.Farm.date(d0, h / 24);
					t.append(h > 0 ? "," : "").append('"').append(day).append(String.format("T%02d:00\"", h % 24));
					double p = h % 24 >= 14 && h % 24 <= 19 ? 70 : h % 24 > 19 ? 35 : 10;
					double mm = p >= 70 ? 1.2 : p >= 30 ? 0.2 : 0;
					double[] val = { 8 + 6 * Math.sin((h % 24 - 9) / 24.0 * 2 * Math.PI), 70 + p / 4, p, mm, mm * 0.7, mm * 0.3, 0, 40 + p / 2,
							12 + (h % 24 > 15 ? 10 : 0), h % 24 < 17 ? 300 : 190, 25 + (h % 24 > 15 ? 18 : 0), 6 + 4 * Math.sin((h % 24 - 10) / 24.0 * 2 * Math.PI), 0.27 };
					for (int j = 0; j < keys.length; j++) cols[j].append(h > 0 ? "," : "").append(String.format(java.util.Locale.US, "%.2f", val[j]));
				}
				v.append("{\"hourly\":{\"time\":[").append(t).append("]");
				for (int j = 0; j < keys.length; j++) v.append(",\"").append(keys[j]).append("\":[").append(cols[j]).append("]");
				v.append("},\"daily\":{\"time\":[\"").append(ro.meteoalarm.android.core.Farm.date(d0, 1)).append("\",\"").append(ro.meteoalarm.android.core.Farm.date(d0, 2))
						.append("\"],\"temperature_2m_max\":[14.2,12.1],\"temperature_2m_min\":[2.1,4.0],\"precipitation_sum\":[7.4,3.1],\"wind_speed_10m_max\":[22,18]}}");
				s.farm = ro.meteoalarm.android.core.Farm.parse(v.toString());
			}
			s.now = Calendar.getInstance();
			for (String[] r : s.hourInfo(2)) System.out.println("hour " + r[0] + ": " + r[1]);
			for (String[] r : s.rainInfo(3)) System.out.println("rain " + r[0] + ": " + r[1]);
			for (String[] r : s.rain24Info()) System.out.println("rain24 " + r[0] + ": " + r[1]);
			if (opts.contains(" parcel")) {
				// v2.60: a parcel as the main screen (3rd of 4), with a long name.
				s.parcel = "parcela 96/5 Valea Mare de lângă pădure";
				s.pages = 4;
				s.page = 2;
			}
		}
		if (opts.contains(" alert1")) s.alerts.scroll = 5000;
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
		if (opts.contains(" cloudy")) { s.w.code = 3; s.w.isDay = true; }
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
