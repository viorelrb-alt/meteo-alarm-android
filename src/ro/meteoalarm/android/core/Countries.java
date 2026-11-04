package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The MeteoAlarm countries of countries.txt: names and warning feeds. */
public final class Countries {
	public static final String FEED_BASE = "https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-";

	private final Map<String, String[]> byCode = new LinkedHashMap<String, String[]>();

	public Countries(String text) {
		for (String line : text.split("\n")) {
			line = line.trim();
			if (line.length() == 0 || line.startsWith("#")) continue;
			String[] p = line.split("\\|");
			if (p.length == 4) byCode.put(p[0].toLowerCase(), p);
		}
	}

	public boolean has(String cc) {
		return byCode.containsKey(cc);
	}

	/** "Bulgaria" (the code in capitals when not a MeteoAlarm country). */
	public String name(String cc, boolean en) {
		String[] p = byCode.get(cc);
		return p == null ? cc.toUpperCase() : en ? p[2] : p[1];
	}

	/** The name shown in the app's language (Romanian and English from the list, the others from the phone). */
	public String shown(String cc) {
		if (Tr.ro()) return name(cc, false);
		if (Tr.lang().equals("en") || !byCode.containsKey(cc)) return name(cc, true);
		String n = new java.util.Locale("", cc.toUpperCase()).getDisplayCountry(new java.util.Locale(Tr.lang()));
		return n == null || n.length() == 0 || n.equalsIgnoreCase(cc) ? name(cc, true) : n;
	}

	/** The feed URLs to try for CC, in order (none outside MeteoAlarm). */
	public List<String> feeds(String cc) {
		List<String> l = new ArrayList<String>();
		String[] p = byCode.get(cc);
		if (p != null) for (String s : p[3].split(",")) l.add(FEED_BASE + s.trim());
		return l;
	}
}
