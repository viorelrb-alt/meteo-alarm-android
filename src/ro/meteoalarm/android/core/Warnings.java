package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MeteoAlarm warnings (ANM, Romania) for one county, as on the Kindle: warnings active now or
 * starting within 24 h; same level and type merged; the highest first.
 */
public final class Warnings {
	public static final String FEED = "https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-romania";

	public static final class Warn {
		public int level; // 1 yellow, 2 orange, 3 red
		public String type;
		public long from, until; // epoch ms
		/** A US warning from the National Weather Service (v2.30): type is its event ("Tornado Warning"). */
		public boolean nws;
		/** NWS only: the headline, what / where / when, what to do, the areas (English, as NWS writes them). */
		public String headline = "", desc = "", instr = "", area = "";
		long sentAt;
	}

	/** Lower case without diacritics, so "Călărași" matches "Calarasi" (and "Kärnten" "Karnten"). */
	public static String fold(String s) {
		String n = java.text.Normalizer.normalize(s.toLowerCase(), java.text.Normalizer.Form.NFD);
		StringBuilder b = new StringBuilder(n.length());
		for (int k = 0; k < n.length(); k++) {
			char c = n.charAt(k);
			if (Character.getType(c) != Character.NON_SPACING_MARK) b.append(c);
		}
		return b.toString();
	}

	private static String tag(String e, String n) {
		Matcher m = Pattern.compile("<" + n + "[^>]*>([^<]*)</" + n + ">").matcher(e);
		return m.find() ? m.group(1).trim() : "";
	}

	/** "2026-10-25T13:00:00+00:00" in epoch ms (0 when empty). */
	static long epoch(String s) {
		if (s.length() < 19) return 0;
		Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		c.clear();
		c.set(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(5, 7)) - 1, Integer.parseInt(s.substring(8, 10)),
				Integer.parseInt(s.substring(11, 13)), Integer.parseInt(s.substring(14, 16)), Integer.parseInt(s.substring(17, 19)));
		long t = c.getTimeInMillis();
		if (s.length() >= 25 && (s.charAt(19) == '+' || s.charAt(19) == '-')) {
			long off = (Integer.parseInt(s.substring(20, 22)) * 60L + Integer.parseInt(s.substring(23, 25))) * 60000L;
			t += s.charAt(19) == '+' ? -off : off;
		}
		return t;
	}

	/** The warnings of town C from the saved text: NWS JSON for a US town, the MeteoAlarm feed for the others. */
	public static List<Warn> of(String text, Cities.City c, long now) {
		if (text == null || c == null) return new ArrayList<Warn>();
		if (c.us()) return Nws.parse(text, now);
		if (text.trim().startsWith("{")) return new ArrayList<Warn>();
		return parse(text, c.warnNames(), now);
	}

	public static List<Warn> parse(String xml, String area, long now) {
		return parse(xml, new String[] { area }, now);
	}

	/** The warnings whose area names one of NAMES (a county, a region, a town). */
	public static List<Warn> parse(String xml, String[] names, long now) {
		List<String> keys = new ArrayList<String>();
		for (String n : names) if (n != null && n.trim().length() >= 3) keys.add(fold(n.trim()));
		String text = fold(xml.replace('\n', ' ').replace('\r', ' '));
		List<Warn> list = new ArrayList<Warn>();
		String[] entries = text.split("<entry");
		for (int k = 1; k < entries.length; k++) {
			String e = entries[k];
			String ad = tag(e, "cap:areadesc");
			boolean hit = false;
			for (String key : keys) if (ad.contains(key)) hit = true;
			if (!hit) continue;
			String t = tag(e, "title");
			int lv = t.startsWith("yellow") ? 1 : t.startsWith("orange") ? 2 : t.startsWith("red") ? 3 : 0;
			if (lv == 0) continue;
			String ty = t.replaceFirst("^[a-z]+ ", "").replaceFirst(" warning.*$", "");
			long on = epoch(tag(e, "cap:onset"));
			if (on == 0) on = epoch(tag(e, "cap:effective"));
			long ex = epoch(tag(e, "cap:expires"));
			if (ex <= now || on - now > 86400000L) continue;
			Warn same = null;
			for (Warn w : list) if (w.level == lv && w.type.equals(ty)) same = w;
			if (same == null) {
				same = new Warn();
				same.level = lv;
				same.type = ty;
				same.from = on;
				same.until = ex;
				list.add(same);
			} else {
				same.from = Math.min(same.from, on);
				same.until = Math.max(same.until, ex);
			}
		}
		// Higher level first, then the earlier start (all of them: the boxes are one row that scrolls).
		java.util.Collections.sort(list, new java.util.Comparator<Warn>() {
			public int compare(Warn a, Warn b) {
				if (a.level != b.level) return b.level - a.level;
				return a.from < b.from ? -1 : a.from > b.from ? 1 : 0;
			}
		});
		return list;
	}
}
