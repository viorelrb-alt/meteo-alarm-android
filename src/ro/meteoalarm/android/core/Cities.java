package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The towns: Romania's from cities.txt ("county|town|lat|lon"), by county, and the other countries'
 * from world.txt and the location, by country.
 */
public final class Cities {
	public static final class City {
		/** Lower-case country code ("ro", "bg"...). */
		public final String country, county, name;
		public final double lat, lon;
		/** Abroad: local names for the warnings, separated by "/" (empty in Romania). */
		public final String match;

		public City(String county, String name, double lat, double lon) {
			this("ro", county, name, lat, lon, "");
		}

		public City(String country, String county, String name, double lat, double lon, String match) {
			this.country = country == null || country.length() == 0 ? "ro" : country.toLowerCase();
			this.county = county;
			this.name = name;
			this.lat = lat;
			this.lon = lon;
			this.match = match == null ? "" : match.replace('|', '/');
		}

		public boolean abroad() {
			return !country.equals("ro");
		}

		/** A town in the United States (county = the state's name; v2.30). */
		public boolean us() {
			return country.equals("us");
		}

		/** "Călărași|Călărași|44.2058|27.3306" in Romania; "bg|Sofia|Sofia|42.69|23.32|Sofia" abroad. */
		public String key() {
			String k = county + "|" + name + "|" + lat + "|" + lon;
			return abroad() ? country + "|" + k + "|" + match : k;
		}

		public static City fromKey(String k) {
			String[] p = k.split("\\|", -1);
			try {
				if (p.length == 4) return new City(p[0], p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]));
				if (p.length == 6) return new City(p[0], p[1], p[2], Double.parseDouble(p[3]), Double.parseDouble(p[4]), p[5]);
			} catch (NumberFormatException e) {
				// a broken entry
			}
			return null;
		}

		/** The same place (country, county, name without case and diacritics). */
		public boolean same(City o) {
			return o != null && o.country.equals(country) && o.county.equals(county) && Warnings.fold(o.name).equals(Warnings.fold(name));
		}

		/** "Călărași, CL"; abroad "Sofia, BG". */
		public String label() {
			if (us()) {
				String st = stateCode(county);
				return st.length() > 0 ? name + ", " + st : name + ", US";
			}
			if (abroad()) return name + ", " + country.toUpperCase();
			String c = countyCode(county);
			return c.length() > 0 ? name + ", " + c : name;
		}

		/** The names a warning's area is matched against. */
		public String[] warnNames() {
			if (!abroad()) return new String[] { county };
			List<String> l = new ArrayList<String>();
			l.add(county);
			l.add(name.replaceFirst(",.*$", ""));
			for (String m : match.split("/")) if (m.trim().length() > 0) l.add(m.trim());
			return l.toArray(new String[0]);
		}
	}

	public final Map<String, List<City>> byCounty = new LinkedHashMap<String, List<City>>();
	/** Other countries: country code to its towns. */
	public final Map<String, List<City>> abroad = new LinkedHashMap<String, List<City>>();
	/** The United States (v2.30): state name to its towns from us.txt, the biggest first (the first 3 are the starting favourites). */
	public final Map<String, List<City>> usStates = new LinkedHashMap<String, List<City>>();

	public Cities(String text) {
		for (String line : text.split("\n")) {
			line = line.trim();
			if (line.length() == 0 || line.startsWith("#")) continue;
			String[] p = line.split("\\|");
			if (p.length != 4 || p[0].equals("*")) continue;
			try {
				City c = new City(p[0], p[1], Double.parseDouble(p[2]), Double.parseDouble(p[3]));
				List<City> l = byCounty.get(c.county);
				if (l == null) byCounty.put(c.county, l = new ArrayList<City>());
				l.add(c);
			} catch (NumberFormatException e) {
				// skip a bad line
			}
		}
	}

	/** The towns of world.txt ("cc|region|town|lat|lon|local names"). */
	public void addWorld(String text) {
		for (String line : text.split("\n")) {
			line = line.trim();
			if (line.length() == 0 || line.startsWith("#")) continue;
			String[] p = line.split("\\|", -1);
			if (p.length < 5) continue;
			try {
				add(new City(p[0], p[1], p[2], Double.parseDouble(p[3]), Double.parseDouble(p[4]), p.length > 5 ? p[5] : ""));
			} catch (NumberFormatException e) {
				// skip a bad line
			}
		}
	}

	/** The towns of us.txt ("ST|State|Town|lat|lon"), by state. */
	public void addUS(String text) {
		for (String line : text.split("\n")) {
			line = line.trim();
			if (line.length() == 0 || line.startsWith("#")) continue;
			String[] p = line.split("\\|", -1);
			if (p.length < 5) continue;
			try {
				add(new City("us", p[1], p[2], Double.parseDouble(p[3]), Double.parseDouble(p[4]), ""));
			} catch (NumberFormatException e) {
				// skip a bad line
			}
		}
	}

	private List<City> listOf(City c, boolean make) {
		Map<String, List<City>> m = c.us() ? usStates : c.abroad() ? abroad : byCounty;
		String k = c.us() ? c.county : c.abroad() ? c.country : c.county;
		List<City> l = m.get(k);
		if (l == null && make) m.put(k, l = new ArrayList<City>());
		return l;
	}

	/**
	 * The listed town nearest to LAT, LON (null when the list is empty): a parcel takes its
	 * country and county, for the warnings (v2.60).
	 */
	public City nearest(double lat, double lon) {
		City best = null;
		double bd = Double.MAX_VALUE, k = Math.cos(Math.toRadians(lat));
		List<Map<String, List<City>>> all = new ArrayList<Map<String, List<City>>>();
		all.add(byCounty);
		all.add(abroad);
		all.add(usStates);
		for (Map<String, List<City>> m : all)
			for (List<City> l : m.values())
				for (City c : l) {
					if (c.lat == 0 && c.lon == 0) continue;
					double dy = c.lat - lat, dx = (c.lon - lon) * k, d = dx * dx + dy * dy;
					if (d < bd) {
						bd = d;
						best = c;
					}
				}
		return best;
	}

	/** v4.00: the N listed towns nearest to LAT, LON, nearest first (for the big compass). */
	public List<City> nearest(double lat, double lon, int n) {
		final double k = Math.cos(Math.toRadians(lat));
		List<City> best = new ArrayList<City>();
		List<Double> dist = new ArrayList<Double>();
		List<Map<String, List<City>>> all = new ArrayList<Map<String, List<City>>>();
		all.add(byCounty);
		all.add(abroad);
		all.add(usStates);
		for (Map<String, List<City>> m : all)
			for (List<City> l : m.values())
				for (City c : l) {
					if (c.lat == 0 && c.lon == 0) continue;
					double dy = c.lat - lat, dx = (c.lon - lon) * k, d = dx * dx + dy * dy;
					boolean dup = false;
					for (City b : best) if (b.name.equals(c.name) && Math.abs(b.lat - c.lat) < 0.01) dup = true;
					if (dup) continue;
					int i = 0;
					while (i < dist.size() && dist.get(i) <= d) i++;
					if (i >= n) continue;
					best.add(i, c);
					dist.add(i, d);
					if (best.size() > n) {
						best.remove(n);
						dist.remove(n);
					}
				}
		return best;
	}

	public City find(String name) {
		for (List<City> l : byCounty.values()) for (City c : l) if (c.name.equals(name)) return c;
		return null;
	}

	/** The town NAME in COUNTY, compared without case and diacritics (null if not listed). */
	public City find(String county, String name) {
		return find(new City(county, name, 0, 0));
	}

	/** The listed town that is the same place as C (its coordinates may differ), or null. */
	public City find(City c) {
		List<City> l = listOf(c, false);
		if (l == null) return null;
		for (City x : l) if (x.same(c) || (c.abroad() && Warnings.fold(x.name).equals(Warnings.fold(c.name)))) return x;
		return null;
	}

	/**
	 * A town learned by the location: replaces the listed one of the same name (its coordinates
	 * become where the phone was found) or is added to its county (its country abroad).
	 */
	public void put(City c) {
		if (c.us() && stateCode(c.county).length() == 0) return;
		List<City> l = listOf(c, true);
		City old = find(c);
		if (old != null) l.set(l.indexOf(old), c);
		else l.add(c);
	}

	/** Adds a town (a new county or country goes at the end), unless it is listed already. */
	public void add(City c) {
		if (c.us() && stateCode(c.county).length() == 0) return;
		if (find(c) != null) return;
		listOf(c, true).add(c);
	}

	/**
	 * The county as cities.txt spells it, from a geocoder's "Județul Călărași", "Calarasi County"
	 * or "Bucharest"; anything else is returned trimmed.
	 */
	public String county(String raw) {
		if (raw == null) return "";
		String r = raw.trim().replaceFirst("^(?i)(jude[țţt]ul|jude[țţt]|municipiul)\\s+", "").replaceFirst("(?i)\\s+county$", "").trim();
		String f = Warnings.fold(r);
		if (f.contains("bucuresti") || f.contains("bucharest")) f = "bucuresti";
		for (String k : byCounty.keySet()) if (Warnings.fold(k).equals(f)) return k;
		return r;
	}

	/** The states (and Washington, D.C.) with their postal codes. */
	public static final String[] STATES = { "Alabama", "AL", "Alaska", "AK", "Arizona", "AZ", "Arkansas", "AR", "California", "CA",
			"Colorado", "CO", "Connecticut", "CT", "Delaware", "DE", "District of Columbia", "DC", "Florida", "FL", "Georgia", "GA",
			"Hawaii", "HI", "Idaho", "ID", "Illinois", "IL", "Indiana", "IN", "Iowa", "IA", "Kansas", "KS", "Kentucky", "KY",
			"Louisiana", "LA", "Maine", "ME", "Maryland", "MD", "Massachusetts", "MA", "Michigan", "MI", "Minnesota", "MN",
			"Mississippi", "MS", "Missouri", "MO", "Montana", "MT", "Nebraska", "NE", "Nevada", "NV", "New Hampshire", "NH",
			"New Jersey", "NJ", "New Mexico", "NM", "New York", "NY", "North Carolina", "NC", "North Dakota", "ND", "Ohio", "OH",
			"Oklahoma", "OK", "Oregon", "OR", "Pennsylvania", "PA", "Rhode Island", "RI", "South Carolina", "SC", "South Dakota", "SD",
			"Tennessee", "TN", "Texas", "TX", "Utah", "UT", "Vermont", "VT", "Virginia", "VA", "Washington", "WA",
			"West Virginia", "WV", "Wisconsin", "WI", "Wyoming", "WY" };

	/** "Oklahoma" -> "OK" (empty when not a state). */
	public static String stateCode(String state) {
		if (state == null) return "";
		String f = Warnings.fold(state.trim().replaceFirst("(?i)^state of\\s+", ""));
		for (int k = 0; k < STATES.length; k += 2) if (Warnings.fold(STATES[k]).equals(f) || STATES[k + 1].equalsIgnoreCase(state.trim())) return STATES[k + 1];
		if (f.equals("washington, d.c.") || f.equals("washington dc") || f.equals("dc")) return "DC";
		return "";
	}

	/**
	 * The time zone of a US town (v2.50, Viorel 2026-11-07: the clock shows the town's local time),
	 * from its state; the states split between two zones by the town's longitude (or latitude for
	 * Idaho). Null for a town outside the US.
	 */
	public static String tzOf(City c) {
		if (c == null || !c.us()) return null;
		String st = stateCode(c.county);
		double lon = c.lon, lat = c.lat;
		String E = "America/New_York", C = "America/Chicago", M = "America/Denver", P = "America/Los_Angeles";
		if (st.equals("HI")) return "Pacific/Honolulu";
		if (st.equals("AK")) return "America/Anchorage";
		if (st.equals("AZ")) return "America/Phoenix";
		if (st.equals("CA") || st.equals("WA") || st.equals("NV")) return P;
		if (st.equals("OR")) return lat < 44.5 && lon > -117.6 ? "America/Boise" : P;
		if (st.equals("ID")) return lat > 45.6 ? P : "America/Boise";
		if (st.equals("MT") || st.equals("WY") || st.equals("CO") || st.equals("UT") || st.equals("NM")) return M;
		if (st.equals("ND")) return lon < -101.3 && lat < 47.5 ? M : C;
		if (st.equals("SD")) return lon < -100.4 ? M : C;
		if (st.equals("NE")) return lon < -101.3 ? M : C;
		if (st.equals("KS")) return lon < -101.5 ? M : C;
		if (st.equals("TX")) return lon < -104.9 ? M : C;
		if (st.equals("FL")) return lon < -85.0 ? C : E;
		if (st.equals("TN")) return lon < -85.3 ? C : E;
		if (st.equals("KY")) return lon < -85.8 ? C : E;
		if (st.equals("IN")) return (lon < -86.8 && (lat > 41.0 || lat < 38.5)) ? C : "America/Indiana/Indianapolis";
		if (st.equals("MI")) return lon < -87.6 && lat > 45.0 ? C : "America/Detroit";
		if (st.equals("AL") || st.equals("AR") || st.equals("IL") || st.equals("IA") || st.equals("LA") || st.equals("MN") || st.equals("MS")
				|| st.equals("MO") || st.equals("OK") || st.equals("WI")) return C;
		if (st.length() == 2) return E;
		return null;
	}

	/** "OK" (or "Oklahoma") -> "Oklahoma" (empty when not a state). */
	public static String stateName(String s) {
		String c = stateCode(s);
		for (int k = 0; k < STATES.length; k += 2) if (STATES[k + 1].equals(c)) return STATES[k];
		return "";
	}

	/** Licence plate code of a Romanian county ("Călărași" -> "CL"). */
	public static String countyCode(String c) {
		String[] t = { "Alba", "AB", "Arad", "AR", "Argeș", "AG", "Bacău", "BC", "Bihor", "BH", "Bistrița-Năsăud", "BN",
				"Botoșani", "BT", "Brașov", "BV", "Brăila", "BR", "București", "B", "Buzău", "BZ", "Caraș-Severin", "CS",
				"Cluj", "CJ", "Constanța", "CT", "Covasna", "CV", "Călărași", "CL", "Dolj", "DJ", "Dâmbovița", "DB",
				"Galați", "GL", "Giurgiu", "GR", "Gorj", "GJ", "Harghita", "HR", "Hunedoara", "HD", "Ialomița", "IL",
				"Iași", "IS", "Ilfov", "IF", "Maramureș", "MM", "Mehedinți", "MH", "Mureș", "MS", "Neamț", "NT", "Olt",
				"OT", "Prahova", "PH", "Satu Mare", "SM", "Sibiu", "SB", "Suceava", "SV", "Sălaj", "SJ", "Teleorman",
				"TR", "Timiș", "TM", "Tulcea", "TL", "Vaslui", "VS", "Vrancea", "VN", "Vâlcea", "VL" };
		for (int k = 0; k < t.length; k += 2) if (t[k].equals(c)) return t[k + 1];
		return "";
	}
}
