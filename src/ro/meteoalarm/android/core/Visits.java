package ro.meteoalarm.android.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * v4.20 (Viorel): the places the phone passed through that are not in the app's own list (mostly
 * villages). One passing puts the place in its county's list; it goes away after GONE days
 * without passing again. Passed on OFTEN different days within WINDOW days, it is kept, and goes
 * only after KEPT days without a visit. Stored as text: "key TAB last-day TAB days,... TAB kept".
 */
public final class Visits {
	public static final int GONE = 7, WINDOW = 14, OFTEN = 3, KEPT = 60;

	static final class Rec {
		long last;
		final TreeSet<Long> days = new TreeSet<Long>();
		boolean kept;
	}

	private final Map<String, Rec> recs = new LinkedHashMap<String, Rec>();

	public Visits(String text) {
		if (text == null) return;
		for (String line : text.split("\n")) {
			String[] p = line.split("\t", -1);
			if (p.length < 4) continue;
			try {
				Rec r = new Rec();
				r.last = Long.parseLong(p[1]);
				for (String d : p[2].split(",")) if (d.length() > 0) r.days.add(Long.parseLong(d));
				r.kept = p[3].equals("1");
				recs.put(p[0], r);
			} catch (NumberFormatException e) {
				// a broken line
			}
		}
	}

	/** The place's key: county and name without case and diacritics. */
	public static String key(Cities.City c) {
		return c.country + "|" + c.county + "|" + Warnings.fold(c.name);
	}

	/** Today as a day number, in the phone's time zone. */
	public static long today(long now) {
		return (now + java.util.TimeZone.getDefault().getOffset(now)) / 86400000L;
	}

	/** A pass through C on day DAY; true when this pass makes it a kept place. */
	public boolean visit(Cities.City c, long day) {
		Rec r = recs.get(key(c));
		if (r == null) recs.put(key(c), r = new Rec());
		r.last = day;
		r.days.add(day);
		while (!r.days.isEmpty() && r.days.first() < day - WINDOW + 1) r.days.pollFirst();
		boolean was = r.kept;
		if (r.days.size() >= OFTEN) r.kept = true;
		return r.kept && !was;
	}

	/** Whether C stays in the list on day DAY (a place never seen before counts as seen today). */
	public boolean stays(Cities.City c, long day) {
		Rec r = recs.get(key(c));
		if (r == null) {
			r = new Rec();
			r.last = day;
			r.days.add(day);
			recs.put(key(c), r);
			return true;
		}
		return day - r.last <= (r.kept ? KEPT : GONE);
	}

	public boolean kept(Cities.City c) {
		Rec r = recs.get(key(c));
		return r != null && r.kept;
	}

	public void forget(Cities.City c) {
		recs.remove(key(c));
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (Map.Entry<String, Rec> e : recs.entrySet()) {
			Rec r = e.getValue();
			StringBuilder d = new StringBuilder();
			for (Long x : r.days) d.append(d.length() > 0 ? "," : "").append(x);
			b.append(b.length() > 0 ? "\n" : "").append(e.getKey()).append('\t').append(r.last).append('\t').append(d).append('\t').append(r.kept ? "1" : "0");
		}
		return b.toString();
	}
}
