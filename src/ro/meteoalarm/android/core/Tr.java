package ro.meteoalarm.android.core;

import java.util.HashMap;
import java.util.Map;

/**
 * The app's languages (Viorel 2026-11-04): Romanian and English written in the code, the other
 * EU languages and Ukrainian from assets/lang/CODE.txt, one "English text<TAB>translation" per
 * line. A text without a translation shows in English. t(en, ro) is used where the code said
 * "en ? English : Romanian".
 */
public final class Tr {
	/** The language codes, as the list shows them, and their names in their own language. */
	public static final String[] CODES = { "ro", "en", "bg", "cs", "da", "de", "el", "es", "et", "fi", "fr", "ga", "hr", "hu", "it",
			"lt", "lv", "mt", "nl", "pl", "pt", "sk", "sl", "sv", "uk" };
	public static final String[] NAMES = { "Română", "English", "Български", "Čeština", "Dansk", "Deutsch", "Ελληνικά", "Español",
			"Eesti", "Suomi", "Français", "Gaeilge", "Hrvatski", "Magyar", "Italiano", "Lietuvių", "Latviešu", "Malti", "Nederlands",
			"Polski", "Português", "Slovenčina", "Slovenščina", "Svenska", "Українська" };

	private static String lang = "ro";
	private static Map<String, String> map = new HashMap<String, String>();

	private Tr() {
	}

	/** Sets the language CODE with its table TEXT (null or empty for ro / en). */
	public static synchronized void set(String code, String text) {
		lang = code == null || code.length() == 0 ? "ro" : code;
		Map<String, String> m = new HashMap<String, String>();
		if (text != null) {
			for (String line : text.split("\n")) {
				int tab = line.indexOf('\t');
				if (tab <= 0 || line.startsWith("#")) continue;
				String v = line.substring(tab + 1);
				if (v.endsWith("\r")) v = v.substring(0, v.length() - 1);
				m.put(unescape(line.substring(0, tab)), unescape(v));
			}
		}
		map = m;
	}

	private static String unescape(String s) {
		return s.indexOf('\\') < 0 ? s : s.replace("\\n", "\n").replace("\\t", "\t");
	}

	public static String lang() {
		return lang;
	}

	/** Romanian is on. */
	public static boolean ro() {
		return "ro".equals(lang);
	}

	/** The text in the current language: RO in Romanian, else the translation of EN, else EN. */
	public static String t(String en, String ro) {
		if ("ro".equals(lang)) return ro;
		if ("en".equals(lang)) return en;
		String v = map.get(en);
		return v != null ? v : en;
	}

	/** The translation of an English text (EN itself when none). */
	public static String t(String en) {
		if ("en".equals(lang) || "ro".equals(lang)) return en;
		String v = map.get(en);
		return v != null ? v : en;
	}

	/** The name of language CODE in its own language. */
	public static String name(String code) {
		for (int k = 0; k < CODES.length; k++) if (CODES[k].equals(code)) return NAMES[k];
		return code;
	}

	public static boolean known(String code) {
		for (String c : CODES) if (c.equals(code)) return true;
		return false;
	}

	/** The official EU languages (and Ukrainian) of a country (ISO code, lower case). */
	public static String[] ofCountry(String cc) {
		if (cc == null) return new String[0];
		cc = cc.toLowerCase();
		if (cc.equals("at") || cc.equals("de") || cc.equals("li")) return new String[] { "de" };
		if (cc.equals("be")) return new String[] { "nl", "fr", "de" };
		if (cc.equals("bg")) return new String[] { "bg" };
		if (cc.equals("hr")) return new String[] { "hr" };
		if (cc.equals("cy") || cc.equals("gr")) return new String[] { "el" };
		if (cc.equals("cz")) return new String[] { "cs" };
		if (cc.equals("dk")) return new String[] { "da" };
		if (cc.equals("ee")) return new String[] { "et" };
		if (cc.equals("fi")) return new String[] { "fi", "sv" };
		if (cc.equals("fr") || cc.equals("mc")) return new String[] { "fr" };
		if (cc.equals("hu")) return new String[] { "hu" };
		if (cc.equals("ie")) return new String[] { "ga" };
		if (cc.equals("it") || cc.equals("sm") || cc.equals("va")) return new String[] { "it" };
		if (cc.equals("lv")) return new String[] { "lv" };
		if (cc.equals("lt")) return new String[] { "lt" };
		if (cc.equals("lu")) return new String[] { "fr", "de" };
		if (cc.equals("mt")) return new String[] { "mt" };
		if (cc.equals("nl")) return new String[] { "nl" };
		if (cc.equals("pl")) return new String[] { "pl" };
		if (cc.equals("pt")) return new String[] { "pt" };
		if (cc.equals("sk")) return new String[] { "sk" };
		if (cc.equals("si")) return new String[] { "sl" };
		if (cc.equals("es") || cc.equals("ad")) return new String[] { "es" };
		if (cc.equals("se")) return new String[] { "sv" };
		if (cc.equals("ua")) return new String[] { "uk" };
		if (cc.equals("ch")) return new String[] { "de", "fr", "it" };
		return new String[0];
	}
}
