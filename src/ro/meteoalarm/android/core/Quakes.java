package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Earthquakes from EMSC (seismicportal.eu, FDSN event service, GeoJSON): the ones of the last 24 h
 * of magnitude 3 or more in the phone's country (EMSC's Flynn region names the country, e.g.
 * "ROMANIA", "CRETE, GREECE"). For the town shown, how strongly it was likely felt there: the
 * intensity estimated from the magnitude and the distance to the focus (EMSC does not publish the
 * felt reports of one town, so the estimate is always called an estimate).
 */
public final class Quakes {
	public static final double MIN_MAG = 3.0;
	public static final long WINDOW_MS = 24 * 3600000L;

	public static final class Quake {
		public double mag, lat, lon, depth, dist;
		public String magType = "", region = "", id = "";
		public long time;
		/** Estimated intensity in the town (EMS-98, 1–12), below 2: not felt. */
		public double felt;
		/**
		 * v2.030 (Viorel, quake of 2026-11-18): who gave the values (EMSC, INFP, USGS), when the
		 * source last changed them (ms, 0 unknown), another id the same quake had (INFP before EMSC
		 * had it), and the first value the app saw (NaN when it never changed).
		 */
		public String src = "EMSC", altId = "";
		public long updated;
		public double firstMag = Double.NaN;
		public String firstSrc = "";
		public long firstAt, changedAt;
		/** v2.040: the values are the country's seismological institute's (INFP, INGV, BCSF…). */
		public boolean official;
	}

	/**
	 * v2.040 (Viorel): the country's own seismological institute, asked before EMSC: its short name
	 * (shown as the source), full name, site and list. Romania: INFP's JSON list; the others: their
	 * FDSN event service (text, times in UTC). Null for a country without a public list.
	 */
	public static final class Official {
		public final String code, name, full, site, url;
		public final boolean json;
		/** v2.050: a second list (INFP's home page: the quakes around Romania and the world's big ones), or null. */
		public String web;

		Official(String code, String name, String full, String site, String url, boolean json) {
			this.code = code;
			this.name = name;
			this.full = full;
			this.site = site;
			this.url = url;
			this.json = json;
		}
	}

	public static Official official(String country) {
		String c = country == null ? "" : country.toLowerCase(Locale.US);
		if (c.equals("ro")) {
			Official o = new Official(c, "INFP", "Institutul Național de Fizica Pământului", "infp.ro", "https://fastapi.infp.ro/v1/?product=shakemap4", true);
			o.web = "https://www.infp.ro/";
			return o;
		}
		if (c.equals("it")) return new Official(c, "INGV", "Istituto Nazionale di Geofisica e Vulcanologia", "ingv.it",
				"https://webservices.ingv.it/fdsnws/event/1/query?minmag=-1&format=text&timezone=UTC&limit=300&orderby=time", false);
		if (c.equals("fr")) return new Official(c, "BCSF", "BCSF-Rénass", "franceseisme.fr",
				"https://api.franceseisme.fr/fdsnws/event/1/query?minmag=-1&format=text&timezone=UTC&limit=300&orderby=time", false);
		if (c.equals("nl")) return new Official(c, "KNMI", "Koninklijk Nederlands Meteorologisch Instituut", "knmi.nl",
				"https://rdsa.knmi.nl/fdsnws/event/1/query?minmag=-1&format=text&limit=300&orderby=time", false);
		if (c.equals("ch")) return new Official(c, "SED", "Schweizerischer Erdbebendienst (ETH Zürich)", "seismo.ethz.ch",
				"https://arclink.ethz.ch/fdsnws/event/1/query?minmag=-1&format=text&limit=300&orderby=time", false);
		if (c.equals("gr")) return new Official(c, "NOA", "National Observatory of Athens", "noa.gr",
				// v2.112: NOA answered HTTP 400 without a start time (A54 log 18.11): the last 3 days asked.
				"https://eida.gein.noa.gr/fdsnws/event/1/query?format=text&orderby=time&starttime={start}", false);
		return null;
	}

	/** The institute's answer RAW as the text lines mergeOfficial reads (null when unreadable). */
	public static String officialText(Official o, String raw, List<Quake> emsc) {
		if (o == null || raw == null) return null;
		if (o.json) {
			// v2.050: the JSON list, then (after WEB_SEP) INFP's home page; the page adds the quakes the list has not got.
			int k = raw.indexOf(WEB_SEP);
			String api = infpFromApi(k < 0 ? raw : raw.substring(0, k), emsc);
			if (k < 0) return api;
			String web = infpFromWeb(raw.substring(k + WEB_SEP.length()));
			if (api == null) return web;
			if (web == null) return api;
			List<Long> have = new ArrayList<Long>();
			for (String line : api.split("\n")) {
				String[] c = line.split("\\|", -1);
				if (c.length > 1 && !line.startsWith("#")) try {
					have.add(Warnings.epoch(c[1]));
				} catch (Exception e) {
				}
			}
			StringBuilder b = new StringBuilder(api);
			for (String line : web.split("\n")) {
				String[] c = line.split("\\|", -1);
				if (line.startsWith("#") || c.length < 11) continue;
				long t;
				try {
					t = Warnings.epoch(c[1]);
				} catch (Exception e) {
					continue;
				}
				boolean dup = false;
				for (long h : have) if (Math.abs(h - t) < 90000L) dup = true;
				if (!dup) b.append(line).append('\n');
			}
			return b.toString();
		}
		return raw.indexOf('|') >= 0 || raw.trim().length() == 0 ? raw : null;
	}

	// --- v2.030: INFP (Institutul Național de Fizica Pământului), the official values for Romania ---

	/** INFP's FDSN event service (SeisComP), text format: the quakes since SINCE, magnitude 2.5 and over. */
	public static String infpUrl(long since) {
		// v2.033 (Viorel: infp.ro had M5.7 while the app had nothing): INFP's FDSN catalog stopped
		// on 2026-10-13, so the app reads the list infp.ro itself shows (its shakemap product):
		// [{"id","time","locstring","lat","lon","mag","depth","event_type",...}].
		return "https://fastapi.infp.ro/v1/?product=shakemap4";
	}

	/**
	 * v2.033: INFP's JSON list turned into the text lines mergeInfp reads
	 * (EventID|Time UTC|Lat|Lon|Depth|||||MagType|Mag||Region). Its "time" carries no zone: UTC is
	 * taken, unless the events match EMSC's quakes (EMSC_UTC) only when read as Romania's local time.
	 * Returns null for an unreadable answer.
	 */
	public static String infpFromApi(String json, List<Quake> emsc) {
		if (json == null) return null;
		Object root;
		try {
			root = Json.parse(json);
		} catch (Exception e) {
			return null;
		}
		if (!(root instanceof List)) return null;
		@SuppressWarnings("unchecked")
		List<Object> a = (List<Object>) root;
		java.util.TimeZone ro = java.util.TimeZone.getTimeZone("Europe/Bucharest");
		List<String[]> ev = new ArrayList<String[]>();
		List<long[]> times = new ArrayList<long[]>();
		int utcHits = 0, localHits = 0;
		for (Object o : a) {
			if (!(o instanceof Map)) continue;
			@SuppressWarnings("unchecked")
			Map<String, Object> m = (Map<String, Object>) o;
			String type = val(m, "event_type");
			if (type.length() > 0 && !type.equalsIgnoreCase("ACTUAL")) continue;
			String t = val(m, "time").replace(' ', 'T');
			long u = infpTime(t);
			if (u <= 0) continue;
			long l = u - ro.getOffset(u);
			double la = dbl(val(m, "lat")), lo = dbl(val(m, "lon")), mg = dbl(val(m, "mag"));
			if (Double.isNaN(la) || Double.isNaN(lo) || Double.isNaN(mg)) continue;
			if (emsc != null) for (Quake q : emsc) {
				double dk = km(la, lo, q.lat, q.lon);
				if (dk > 150) continue;
				if (Math.abs(q.time - u) <= 90000L) utcHits++;
				if (Math.abs(q.time - l) <= 90000L) localHits++;
			}
			ev.add(new String[] { val(m, "id"), val(m, "lat"), val(m, "lon"), val(m, "depth"), val(m, "mag"), val(m, "locstring") });
			times.add(new long[] { u, l });
		}
		boolean local = localHits > utcHits;
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
		f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
		StringBuilder b = new StringBuilder("#EventID|Time|Latitude|Longitude|Depth/km|Author|Catalog|Contributor|ContributorID|MagType|Magnitude|MagAuthor|EventLocationName" + (local ? "|local" : "") + "\n");
		for (int i = 0; i < ev.size(); i++) {
			String[] e = ev.get(i);
			String region = e[5];
			b.append(e[0]).append('|').append(f.format(new java.util.Date(times.get(i)[local ? 1 : 0]))).append('|').append(e[1]).append('|').append(e[2]).append('|')
					.append(e[3]).append("|INFP||||ML|").append(e[4]).append("|INFP|").append(region.replace('|', ' ')).append('\n');
		}
		return b.toString();
	}

	/** v2.050: between the JSON list and the home page in one raw answer. */
	public static final String WEB_SEP = "\n<!--meteoalarm-infp-web-->\n";

	/**
	 * v2.050 (Viorel: INFP reported a quake at the Greece–Bulgaria border the app did not show as
	 * INFP's): INFP's home page lists its latest located quakes, each in a small table (ID
	 * Eveniment, Data şi ora evenimentului in Romania's time, Magnitudine "ml 3.9", Adâncime,
	 * Regiune, Latitudine, Longitudine). Turned into the text lines mergeOfficial reads; null when
	 * nothing could be read.
	 */
	public static String infpFromWeb(String html) {
		if (html == null) return null;
		String t = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ").replaceAll("<[^>]+>", "|");
		t = unescape(t).replace('\u00a0', ' ');
		t = t.replaceAll("[ \\t\\r\\n]*\\|[ \\t\\r\\n|]*", "|");
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)ora evenimentului\\|?\\s*(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4}),?\\s*(\\d{1,2}):(\\d{2}):(\\d{2})").matcher(t);
		List<int[]> at = new ArrayList<int[]>();
		while (m.find()) at.add(new int[] { m.start(), m.end() });
		if (at.isEmpty()) return null;
		java.util.TimeZone ro = java.util.TimeZone.getTimeZone("Europe/Bucharest");
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
		f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
		StringBuilder b = new StringBuilder("#EventID|Time|Latitude|Longitude|Depth/km|Author|Catalog|Contributor|ContributorID|MagType|Magnitude|MagAuthor|EventLocationName\n");
		int n = 0;
		for (int i = 0; i < at.size(); i++) {
			// The event's table: from a little before its date (its ID) to the next event's.
			int from = Math.max(i > 0 ? at.get(i - 1)[1] : 0, at.get(i)[0] - 200), to = i + 1 < at.size() ? at.get(i + 1)[0] - 1 : Math.min(t.length(), at.get(i)[1] + 1500);
			String blk = t.substring(from, Math.max(from, to));
			m.reset();
			java.util.regex.Matcher d = java.util.regex.Pattern.compile("(?i)ora evenimentului\\|?\\s*(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4}),?\\s*(\\d{1,2}):(\\d{2}):(\\d{2})").matcher(blk);
			if (!d.find()) continue;
			java.util.Calendar k = java.util.Calendar.getInstance(ro, Locale.US);
			k.clear();
			k.set(Integer.parseInt(d.group(3)), Integer.parseInt(d.group(2)) - 1, Integer.parseInt(d.group(1)), Integer.parseInt(d.group(4)), Integer.parseInt(d.group(5)), Integer.parseInt(d.group(6)));
			String rest = blk.substring(d.end());
			String[] mag = grab(rest, "(?i)\\|Magnitudine\\|\\s*([A-Za-z]{1,4})\\s*(\\d+(?:[.,]\\d+)?)");
			String[] lat = grab(rest, "(?i)\\|Latitudine\\|\\s*(-?\\d+(?:[.,]\\d+)?)\\s*°?\\s*([NS]?)");
			String[] lon = grab(rest, "(?i)\\|Longitudine\\|\\s*(-?\\d+(?:[.,]\\d+)?)\\s*°?\\s*([EVWO]?)");
			if (mag == null || lat == null || lon == null) continue;
			String[] dep = grab(rest, "(?i)\\|Ad.ncime\\|\\s*(\\d+(?:[.,]\\d+)?)");
			String[] reg = grab(rest, "(?i)\\|Regiune\\|\\s*([^|]+)");
			String[] id = grab(blk.substring(0, d.start()), "(?i)ID Eveniment\\|\\s*(\\d+)");
			double la = Math.abs(dbl(lat[0].replace(',', '.'))), lo = Math.abs(dbl(lon[0].replace(',', '.')));
			if (lat[0].startsWith("-") || "S".equalsIgnoreCase(lat[1])) la = -la;
			if (lon[0].startsWith("-") || "W".equalsIgnoreCase(lon[1]) || "V".equalsIgnoreCase(lon[1])) lo = -lo;
			double mg = dbl(mag[1].replace(',', '.'));
			if (Double.isNaN(la) || Double.isNaN(lo) || Double.isNaN(mg)) continue;
			String type = mag[0].length() == 0 ? "ML" : mag[0].toUpperCase(Locale.US);
			b.append("w").append(id != null ? id[0] : String.valueOf(k.getTimeInMillis() / 1000)).append('|').append(f.format(k.getTime())).append('|').append(la).append('|').append(lo).append('|')
					.append(dep != null ? dep[0].replace(',', '.') : "10").append("|INFPW||||").append(type).append('|').append(mg).append("|INFP|")
					.append(reg != null ? reg[0].trim().replace('|', ' ') : "").append('\n');
			n++;
		}
		return n == 0 ? null : b.toString();
	}

	/** The groups of the first match of RE in S (null when none). */
	private static String[] grab(String s, String re) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile(re).matcher(s);
		if (!m.find()) return null;
		String[] g = new String[m.groupCount()];
		for (int i = 0; i < g.length; i++) g[i] = m.group(i + 1) == null ? "" : m.group(i + 1);
		return g;
	}

	/** The HTML entities a page may use for Romanian letters and signs. */
	private static String unescape(String s) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("&#(x?)([0-9a-fA-F]+);").matcher(s);
		StringBuffer b = new StringBuffer();
		while (m.find()) {
			int c;
			try {
				c = Integer.parseInt(m.group(2), m.group(1).length() > 0 ? 16 : 10);
			} catch (Exception e) {
				c = ' ';
			}
			m.appendReplacement(b, java.util.regex.Matcher.quoteReplacement(String.valueOf((char) c)));
		}
		m.appendTail(b);
		return b.toString().replace("&deg;", "°").replace("&acirc;", "â").replace("&Acirc;", "Â").replace("&icirc;", "î").replace("&abreve;", "ă").replace("&nbsp;", " ")
				.replace("&amp;", "&").replace("&scedil;", "ş").replace("&tcedil;", "ţ");
	}

	private static String val(Map<String, Object> m, String k) {
		Object v = m.get(k);
		if (v == null) return "";
		if (v instanceof Double) {
			double d = (Double) v;
			return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.valueOf(d);
		}
		return v.toString().trim();
	}

	private static double dbl(String s) {
		try {
			return Double.parseDouble(s);
		} catch (Exception e) {
			return Double.NaN;
		}
	}

	/** INFP's time (UTC, no zone) in ms, 0 when unreadable. */
	public static long infpTime(String s) {
		try {
			return Warnings.epoch(s);
		} catch (Exception e) {
			return 0;
		}
	}

	/** Romania with a little room (INFP's own quakes there count even before EMSC has them). */
	static boolean inRomania(double la, double lo) {
		return la > 43.6 && la < 48.3 && lo > 20.2 && lo < 29.8;
	}

	/**
	 * INFP's text answer (EventID|Time|Latitude|Longitude|Depth/km|Author|Catalog|Contributor|
	 * ContributorID|MagType|Magnitude|MagAuthor|EventLocationName) merged into QS: a quake both have
	 * (within 90 s and 150 km) takes INFP's magnitude, place and depth; one only INFP has, in
	 * Romania, of magnitude 3 or more in the last WINDOW ms, is added. Distances and felt intensity
	 * are worked out again for the town at LAT LON. True when INFP had anything usable.
	 */
	public static boolean mergeInfp(List<Quake> qs, String text, double lat, double lon, long now, long window) {
		return mergeOfficial(qs, text, "INFP", lat, lon, now, window);
	}

	/** v2.040: the same for any country's institute SRC (outside Romania its own quakes within 400 km of the town are added). */
	public static boolean mergeOfficial(List<Quake> qs, String text, String src, double lat, double lon, long now, long window) {
		return mergeOfficial(qs, text, src, lat, lon, now, window, true);
	}

	/**
	 * v2.109 (Viorel): the country of a quake by EMSC's region name, as the code of its institute
	 * (official()); "" when none.
	 */
	public static String instituteOf(String region) {
		String r = Warnings.fold(region == null ? "" : region).toUpperCase(Locale.US);
		if (r.contains("ROMANIA")) return "ro";
		if (r.contains("ITALY") || r.contains("SICILY") || r.contains("SARDINIA")) return "it";
		if (r.contains("SWITZERLAND")) return "ch";
		if (r.contains("FRANCE") || r.contains("CORSICA")) return "fr";
		if (r.contains("NETHERLANDS")) return "nl";
		if (r.contains("GREECE") || r.contains("CRETE") || r.contains("DODECANESE") || r.contains("AEGEAN") || r.contains("IONIAN")) return "gr";
		return "";
	}

	/** ADD false (v2.109): only the quakes of QS take the institute's values; none of its own are added. */
	public static boolean mergeOfficial(List<Quake> qs, String text, String src, double lat, double lon, long now, long window, boolean add) {
		boolean ro = "INFP".equals(src);
		String pre = src.toLowerCase(Locale.US);
		if (text == null || text.trim().length() == 0) return false;
		List<Quake> inf = new ArrayList<Quake>();
		for (String line : text.split("\n")) {
			if (line.startsWith("#") || line.trim().length() == 0) continue;
			String[] c = line.split("\\|", -1);
			if (c.length < 11) continue;
			Quake n = new Quake();
			try {
				String id = c[0].trim().replaceAll("[^A-Za-z0-9]+", "");
				n.id = id.startsWith(pre) ? id : pre + id;
				n.time = Warnings.epoch(c[1].trim());
				n.lat = Double.parseDouble(c[2].trim());
				n.lon = Double.parseDouble(c[3].trim());
				n.depth = c[4].trim().length() > 0 ? Math.abs(Double.parseDouble(c[4].trim())) : 10;
				n.magType = c[9].trim();
				n.mag = Double.parseDouble(c[10].trim());
			} catch (Exception e) {
				continue;
			}
			if (Double.isNaN(n.mag) || n.time <= 0 || now - n.time > window || n.time - now > 600000L) continue;
			n.region = c.length > 12 && c[12].trim().length() > 0 ? c[12].trim() : ro ? "ROMANIA" : "";
			n.src = src;
			n.official = true;
			// v2.033: INFP's own list (author INFP) is the Romanian network's: its places need no country name.
			n.firstSrc = c.length > 5 && "INFP".equals(c[5].trim()) ? "api" : "";
			inf.add(n);
		}
		if (inf.isEmpty()) return false;
		// Each EMSC quake takes the closest INFP one (in time, then place); each INFP one is used once.
		boolean[] used = new boolean[inf.size()];
		for (Quake q : qs) {
			if (!add && q.official) continue;
			int best = -1;
			double bs = Double.MAX_VALUE;
			for (int k = 0; k < inf.size(); k++) {
				if (used[k]) continue;
				Quake n = inf.get(k);
				double dt = Math.abs(q.time - n.time) / 1000.0, dk = km(q.lat, q.lon, n.lat, n.lon);
				if (dt > 90 || dk > 150) continue;
				double sc = dt + dk / 5 + Math.abs(q.mag - n.mag) * 20;
				if (sc < bs) {
					bs = sc;
					best = k;
				}
			}
			if (best < 0) continue;
			used[best] = true;
			Quake n = inf.get(best);
			// v2.106 (Viorel, Motorola): EMSC's value kept as the first one, so the history shows both
			// even on a phone that never saw EMSC's value alone (no saved history).
			// v2.113 (Viorel): both values kept even when they are equal.
			if (Double.isNaN(q.firstMag) && !q.official) {
				q.firstMag = q.mag;
				q.firstSrc = q.src;
				q.firstAt = q.updated > 0 ? q.updated : q.time;
			}
			q.mag = n.mag;
			q.magType = n.magType;
			q.lat = n.lat;
			q.lon = n.lon;
			q.depth = n.depth;
			q.time = n.time;
			q.src = src;
			q.official = true;
			q.altId = n.id;
			q.updated = 0;
			q.dist = km(lat, lon, q.lat, q.lon);
			q.felt = intensity(q.mag, Math.sqrt(q.dist * q.dist + q.depth * q.depth));
		}
		for (int k = 0; add && k < inf.size(); k++) {
			Quake n = inf.get(k);
			if (used[k] || n.mag < MIN_MAG) continue;
			n.dist = km(lat, lon, n.lat, n.lon);
			if (ro) {
				if (!inRomania(n.lat, n.lon)) continue;
				// INFP names the country: a quake just over the border stays out (as with EMSC).
				if (!"api".equals(n.firstSrc) && !Warnings.fold(n.region).toUpperCase(Locale.US).contains("ROMANIA")) continue;
			} else if (n.dist > 400) continue;
			n.firstSrc = "";
			n.felt = intensity(n.mag, Math.sqrt(n.dist * n.dist + n.depth * n.depth));
			qs.add(n);
		}
		java.util.Collections.sort(qs, new java.util.Comparator<Quake>() {
			public int compare(Quake a, Quake b) {
				return a.time > b.time ? -1 : a.time < b.time ? 1 : 0;
			}
		});
		return true;
	}

	/**
	 * v2.030: how far (km from the epicentre) the estimated intensity is LEVEL or more for magnitude
	 * M at DEPTH km (intensity() rounded, so its border is LEVEL − 0.5); 0 when nowhere.
	 */
	public static double ringKm(double m, double depth, int level) {
		double r = Math.pow(10, (1.5 * m + 3 - (level - 0.5)) / 3);
		return r <= depth ? 0 : Math.sqrt(r * r - depth * depth);
	}

	/** v2.030: the Vrancea seismic zone (deep quakes under the bend of the Carpathians). */
	public static boolean vrancea(Quake q) {
		return q.lat > 45.3 && q.lat < 46.2 && q.lon > 26.0 && q.lon < 27.2 && q.depth >= 50;
	}

	/** The query for quakes since SINCE (epoch ms) within about 9° of LAT LON. */
	public static String url(double lat, double lon, long since) {
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
		f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
		return "https://www.seismicportal.eu/fdsnws/event/1/query?format=json&limit=500&orderby=time&minmag=" + MIN_MAG
				+ "&start=" + f.format(new java.util.Date(since))
				+ "&minlat=" + Weather.fmt(lat - 9) + "&maxlat=" + Weather.fmt(lat + 9)
				+ "&minlon=" + Weather.fmt(lon - 12) + "&maxlon=" + Weather.fmt(lon + 12);
	}

	/** Kilometres between two points. */
	public static double km(double la1, double lo1, double la2, double lo2) {
		double r = Math.PI / 180, dla = (la2 - la1) * r, dlo = (lo2 - lo1) * r;
		double a = Math.sin(dla / 2) * Math.sin(dla / 2) + Math.cos(la1 * r) * Math.cos(la2 * r) * Math.sin(dlo / 2) * Math.sin(dlo / 2);
		return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}

	/**
	 * Estimated intensity at a hypocentral distance of R km for magnitude M (a usual attenuation for
	 * the Carpathian and Balkan region; Vrancea 1977, M7.4, gives about VII–VIII in Bucharest).
	 */
	public static double intensity(double m, double r) {
		return 1.5 * m - 3.0 * Math.log10(Math.max(5, r)) + 3.0;
	}

	/**
	 * The quakes of the last 24 h in the country named COUNTRY_EN (English, any case), newest first,
	 * with the distance and the estimated intensity for the town at LAT LON.
	 */
	public static List<Quake> parse(String json, String countryEn, double lat, double lon, long now) {
		return parse(json, countryEn == null ? new String[0] : new String[] { countryEn }, lat, lon, now, WINDOW_MS);
	}

	/**
	 * The quakes of the last WINDOW ms whose EMSC region names one of NAMES (English; none = all),
	 * newest first, with the distance and the estimated intensity for the town at LAT LON.
	 */
	public static List<Quake> parse(String json, String[] names, double lat, double lon, long now, long window) {
		List<Quake> list = new ArrayList<Quake>();
		if (json == null || json.trim().length() == 0) return list;
		String[] keys = new String[names.length];
		for (int k = 0; k < names.length; k++) keys[k] = Warnings.fold(names[k]).toUpperCase(Locale.US);
		Object root = Json.parse(json);
		@SuppressWarnings("unchecked")
		List<Object> fs = root instanceof Map ? Json.arr((Map<String, Object>) root, "features") : new ArrayList<Object>();
		for (Object o : fs) {
			Map<String, Object> p = Json.obj(o, "properties");
			if (p == null) continue;
			Quake q = new Quake();
			q.mag = Json.num(p, "mag");
			q.lat = Json.num(p, "lat");
			q.lon = Json.num(p, "lon");
			q.depth = Math.abs(Json.num(p, "depth"));
			Object rg = p.get("flynn_region"), mt = p.get("magtype"), t = p.get("time"), id = p.get("unid"), up = p.get("lastupdate");
			q.updated = up instanceof String ? Warnings.epoch((String) up) : 0;
			q.region = rg instanceof String ? (String) rg : "";
			q.magType = mt instanceof String ? (String) mt : "";
			q.id = id instanceof String ? (String) id : "";
			q.time = t instanceof String ? Warnings.epoch((String) t) : 0;
			if (Double.isNaN(q.mag) || Double.isNaN(q.lat) || Double.isNaN(q.lon) || q.mag < MIN_MAG) continue;
			if (q.time <= 0 || now - q.time > window || q.time - now > 600000L) continue;
			if (keys.length > 0) {
				String rg2 = Warnings.fold(q.region).toUpperCase(Locale.US);
				boolean hit = false;
				for (String k : keys) hit |= k.length() > 0 && rg2.contains(k);
				if (!hit) continue;
			}
			if (Double.isNaN(q.depth)) q.depth = 10;
			q.dist = km(lat, lon, q.lat, q.lon);
			q.felt = intensity(q.mag, Math.sqrt(q.dist * q.dist + q.depth * q.depth));
			list.add(q);
		}
		java.util.Collections.sort(list, new java.util.Comparator<Quake>() {
			public int compare(Quake a, Quake b) {
				return a.time > b.time ? -1 : a.time < b.time ? 1 : 0;
			}
		});
		return list;
	}

	/** EU countries, Ukraine and Moldova as EMSC names them (and the European seas and ranges between them). */
	public static final String[] EUROPE = { "AUSTRIA", "BELGIUM", "BULGARIA", "CROATIA", "CYPRUS", "CZECH", "DENMARK", "ESTONIA", "FINLAND",
			"FRANCE", "GERMANY", "GREECE", "HUNGARY", "IRELAND", "ITALY", "LATVIA", "LITHUANIA", "LUXEMBOURG", "MALTA", "NETHERLANDS",
			"POLAND", "PORTUGAL", "ROMANIA", "SLOVAKIA", "SLOVENIA", "SPAIN", "SWEDEN", "UKRAINE", "MOLDOVA", "SICILY", "SARDINIA",
			"CORSICA", "CRETE", "DODECANESE", "AZORES", "CANARY", "BALEARIC", "MADEIRA", "PYRENEES", "ADRIATIC", "IONIAN", "AEGEAN",
			"TYRRHENIAN", "LIGURIAN", "BALTIC", "BLACK SEA" };

	/** The query for Europe (the EU, Ukraine, Moldova and around) since SINCE. */
	public static String europeUrl(long since) {
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
		f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
		return "https://www.seismicportal.eu/fdsnws/event/1/query?format=json&limit=1000&orderby=time&minmag=" + MIN_MAG
				+ "&start=" + f.format(new java.util.Date(since)) + "&minlat=27&maxlat=71&minlon=-32&maxlon=41";
	}

	// --- United States: USGS (v2.30) ------------------------------------------------------

	/** Magnitude from which USGS quakes are shown in the US (Oklahoma has many small ones). */
	public static final double US_MIN_MAG = 2.5;
	/** Radius around a US town, km (about 300 miles). */
	public static final double US_RADIUS_KM = 483;

	private static String usgsTime(long since) {
		java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
		f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
		return f.format(new java.util.Date(since));
	}

	/** USGS quakes since SINCE within US_RADIUS_KM of LAT LON, magnitude US_MIN_MAG and more (GeoJSON). */
	public static String usgsUrl(double lat, double lon, long since) {
		return "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&orderby=time&limit=500&minmagnitude=" + US_MIN_MAG
				+ "&starttime=" + usgsTime(since) + "&latitude=" + Weather.fmt(lat) + "&longitude=" + Weather.fmt(lon)
				+ "&maxradiuskm=" + Math.round(US_RADIUS_KM);
	}

	/** USGS quakes of magnitude 3 and more since SINCE in and around the United States (Alaska and Hawaii too). */
	public static String usUrl(long since) {
		return "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&orderby=time&limit=1000&minmagnitude=" + MIN_MAG
				+ "&starttime=" + usgsTime(since) + "&minlatitude=17&maxlatitude=72&minlongitude=-180&maxlongitude=-64";
	}

	/**
	 * USGS GeoJSON: the quakes of the last WINDOW ms of magnitude MIN or more, newest first, with the
	 * distance and the estimated intensity for LAT LON. US_ONLY keeps those whose place names a state.
	 */
	public static List<Quake> parseUsgs(String json, double lat, double lon, long now, long window, double min, boolean usOnly) {
		List<Quake> list = new ArrayList<Quake>();
		if (json == null || json.trim().length() == 0 || !json.trim().startsWith("{")) return list;
		Object root = Json.parse(json);
		@SuppressWarnings("unchecked")
		List<Object> fs = root instanceof Map ? Json.arr((Map<String, Object>) root, "features") : new ArrayList<Object>();
		for (Object o : fs) {
			Map<String, Object> p = Json.obj(o, "properties"), g = Json.obj(o, "geometry");
			if (p == null || g == null) continue;
			List<Object> xyz = Json.arr(g, "coordinates");
			if (xyz == null || xyz.size() < 2) continue;
			Quake q = new Quake();
			q.mag = Json.num(p, "mag");
			q.lon = Json.num(xyz, 0);
			q.lat = Json.num(xyz, 1);
			q.depth = xyz.size() > 2 ? Math.abs(Json.num(xyz, 2)) : 10;
			Object pl = p.get("place"), mt = p.get("magType"), t = p.get("time"), id = ((Map<?, ?>) o).get("id");
			q.region = pl instanceof String ? (String) pl : "";
			q.magType = mt instanceof String ? (String) mt : "";
			q.id = id instanceof String ? (String) id : "";
			q.time = t instanceof Number ? ((Number) t).longValue() : 0;
			Object up = p.get("updated");
			q.updated = up instanceof Number ? ((Number) up).longValue() : 0;
			q.src = "USGS";
			if (Double.isNaN(q.mag) || Double.isNaN(q.lat) || Double.isNaN(q.lon) || q.mag < min) continue;
			if (q.time <= 0 || now - q.time > window || q.time - now > 600000L) continue;
			if (usOnly && !namesState(q.region)) continue;
			if (Double.isNaN(q.depth)) q.depth = 10;
			q.dist = km(lat, lon, q.lat, q.lon);
			q.felt = intensity(q.mag, Math.sqrt(q.dist * q.dist + q.depth * q.depth));
			list.add(q);
		}
		java.util.Collections.sort(list, new java.util.Comparator<Quake>() {
			public int compare(Quake a, Quake b) {
				return a.time > b.time ? -1 : a.time < b.time ? 1 : 0;
			}
		});
		return list;
	}

	/** A USGS place in a state: "5 km NE of Prague, Oklahoma", "10 km SW of Ridgecrest, CA". */
	static boolean namesState(String place) {
		if (place == null) return false;
		int c = place.lastIndexOf(", ");
		String tail = c >= 0 ? place.substring(c + 2).trim() : place.trim();
		if (Cities.stateCode(tail).length() > 0) return true;
		for (int k = 0; k < Cities.STATES.length; k += 2) if (place.contains(Cities.STATES[k])) return true;
		return false;
	}

	/** The one shown in the box: the strongest of the last 24 h (the newest of equal ones). */
	public static Quake main(List<Quake> qs) {
		Quake best = null;
		if (qs != null) for (Quake q : qs) if (best == null || q.mag > best.mag + 0.05) best = q;
		return best;
	}
}
