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
	public static final String[] CODES = { "ro", "en", "us", "bg", "cs", "da", "de", "el", "es", "et", "fi", "fr", "ga", "hr", "hu", "it",
			"lt", "lv", "mt", "nl", "pl", "pt", "sk", "sl", "sv", "uk" };
	public static final String[] NAMES = { "Română", "English", "English (US)", "Български", "Čeština", "Dansk", "Deutsch", "Ελληνικά", "Español",
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

	/** English, British or American (v2.30: "us" = American English, the British texts with American spelling). */
	public static boolean english() {
		return "en".equals(lang) || "us".equals(lang);
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

	/** The official EU languages (and Ukrainian; Romanian for Moldova) of a country (ISO code, lower case). */
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
		if (cc.equals("ro") || cc.equals("md")) return new String[] { "ro" };
		if (cc.equals("ch")) return new String[] { "de", "fr", "it" };
		if (cc.equals("us")) return new String[] { "us", "es" };
		return new String[0];
	}
	/** North, east, south, west in the current language (the letters of a compass). */
	public static String[] compass() {
		String l = lang;
		if (l.equals("ro")) return new String[] { "N", "E", "S", "V" };
		if (l.equals("bg")) return new String[] { "С", "И", "Ю", "З" };
		if (l.equals("cs") || l.equals("sk") || l.equals("sl")) return new String[] { "S", "V", "J", "Z" };
		if (l.equals("hr")) return new String[] { "S", "I", "J", "Z" };
		if (l.equals("da")) return new String[] { "N", "Ø", "S", "V" };
		if (l.equals("sv")) return new String[] { "N", "Ö", "S", "V" };
		if (l.equals("de")) return new String[] { "N", "O", "S", "W" };
		if (l.equals("nl")) return new String[] { "N", "O", "Z", "W" };
		if (l.equals("el")) return new String[] { "Β", "Α", "Ν", "Δ" };
		if (l.equals("es") || l.equals("fr") || l.equals("it") || l.equals("pt")) return new String[] { "N", "E", "S", "O" };
		if (l.equals("fi")) return new String[] { "P", "I", "E", "L" };
		if (l.equals("hu")) return new String[] { "É", "K", "D", "Ny" };
		if (l.equals("lt")) return new String[] { "Š", "R", "P", "V" };
		if (l.equals("lv")) return new String[] { "Z", "A", "D", "R" };
		if (l.equals("uk")) return new String[] { "Пн", "Сх", "Пд", "Зх" };
		return new String[] { "N", "E", "S", "W" };
	}

	/** The 8 directions N, NE … NW in the current language. */
	public static String[] dirs8() {
		String[] c = compass();
		return new String[] { c[0], c[0] + c[1], c[1], c[2] + c[1], c[2], c[2] + c[3], c[3], c[0] + c[3] };
	}

	/** The decimal point: "." in English, Irish and Maltese, "," in the others. */
	public static char dot() {
		return lang.equals("en") || lang.equals("us") || lang.equals("ga") || lang.equals("mt") ? '.' : ',';
	}
}
