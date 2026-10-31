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

	private List<City> listOf(City c, boolean make) {
		Map<String, List<City>> m = c.abroad() ? abroad : byCounty;
		String k = c.abroad() ? c.country : c.county;
		List<City> l = m.get(k);
		if (l == null && make) m.put(k, l = new ArrayList<City>());
		return l;
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
		List<City> l = listOf(c, true);
		City old = find(c);
		if (old != null) l.set(l.indexOf(old), c);
		else l.add(c);
	}

	/** Adds a town (a new county or country goes at the end), unless it is listed already. */
	public void add(City c) {
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
