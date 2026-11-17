package ro.meteoalarm.android;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ro.meteoalarm.android.core.Tr;

/**
 * The language on the phone (Viorel 2026-11-04): at the first start the phone's own language when
 * the app has it (else English); later the one chosen in ☰ › Language. The list offers only
 * English, the phone's own language and the official languages of the country the phone
 * is in (from the SIM or the mobile network, else the town shown, else the phone's settings), plus
 * the one in use.
 */
final class Lang {
	private Lang() {
	}

	/** Loads the language for CTX (once per change) and returns its code. */
	static synchronized String apply(Context ctx) {
		SharedPreferences p = Fetcher.prefs(ctx);
		String code = p.getString("lang", "");
		if (code.length() == 0 && p.getBoolean("welcomed", false)) {
			// Installed before v2.00 and never switched to English: Romanian, as it was.
			code = "ro";
			p.edit().putString("lang", code).apply();
		}
		if (code.length() == 0) {
			String sys = system();
			code = Tr.known(sys) ? sys : "en";
			// An American phone in English: American English (v2.30).
			if (code.equals("en") && "US".equalsIgnoreCase(Locale.getDefault().getCountry())) code = "us";
			p.edit().putString("lang", code).apply();
			Log.i(ctx, "LIMBA", "prima pornire: limba telefonului " + sys + " -> " + Tr.name(code));
		}
		if (!code.equals(Tr.lang()) || loaded == null) {
			String table = null;
			if (!code.equals("ro") && !code.equals("en")) {
				try {
					table = Fetcher.readAll(ctx.getAssets().open("lang/" + code + ".txt"));
				} catch (Exception e) {
					Log.e(ctx, "LIMBA", "traducerea " + code + " lipsește", e);
				}
			}
			Tr.set(code, table);
			loaded = code;
		}
		return code;
	}

	private static String loaded;

	/** The phone's language code ("mo", used by some Moldovan phones, is Romanian). */
	static String system() {
		String sys = Locale.getDefault().getLanguage();
		if ("iw".equals(sys)) return "he";
		if ("in".equals(sys)) return "id";
		if ("mo".equals(sys)) return "ro";
		return sys;
	}

	/** "ro" is the only language that is not shown in English for the long texts. */
	static boolean en(Context ctx) {
		return !"ro".equals(apply(ctx));
	}

	/** The phone's country (ISO, lower case): SIM, network, then the town shown, then the settings. */
	static String country(Context ctx) {
		try {
			android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
			if (tm != null) {
				String c = tm.getSimCountryIso();
				if (c != null && c.length() == 2) return c.toLowerCase();
				c = tm.getNetworkCountryIso();
				if (c != null && c.length() == 2) return c.toLowerCase();
			}
		} catch (Exception e) {
			// a tablet without SIM
		}
		ro.meteoalarm.android.core.Cities.City t = ro.meteoalarm.android.core.Cities.City.fromKey(Fetcher.prefs(ctx).getString("city", ""));
		if (t != null && t.country != null && t.country.length() == 2) return t.country.toLowerCase();
		String c = Locale.getDefault().getCountry();
		return c == null ? "" : c.toLowerCase();
	}

	/**
	 * Romania (v2.60, Viorel 2026-11-07): the languages of its main minorities the app has, in
	 * their own submenu ("Other languages"); those already in the main list are left out.
	 */
	static List<String> minorities(Context ctx) {
		List<String> l = new ArrayList<String>();
		if (!"ro".equals(country(ctx))) return l;
		List<String> main = offered(ctx);
		for (String c : new String[] { "hu", "de", "uk", "sk", "bg", "hr", "cs", "pl", "el", "it", "tr", "ru", "sr" }) if (Tr.known(c) && !main.contains(c)) l.add(c);
		return l;
	}

	/**
	 * v2.001 (Viorel): "Other languages". Romania: its minorities' languages, as before. The US:
	 * none. Elsewhere: the country's other official languages, 5 big EU languages (German, French,
	 * Italian, Spanish, Dutch; Polish for one already shown), Romanian always 6th (the developer is
	 * Romanian, for the Romanians in Europe), then the phone's language when not shown yet.
	 */
	static List<String> more(Context ctx) {
		String cc = country(ctx);
		if ("ro".equals(cc)) return minorities(ctx);
		List<String> l = new ArrayList<String>();
		if ("us".equals(cc)) return l;
		List<String> main = offered(ctx);
		String[] own = Tr.ofCountry(cc);
		for (int k = 1; k < own.length; k++) if (Tr.known(own[k]) && !main.contains(own[k]) && !l.contains(own[k])) l.add(own[k]);
		int big = 0;
		for (String c : new String[] { "de", "fr", "it", "es", "nl", "pl", "pt", "sv" }) {
			if (big == 5) break;
			if (c.equals("ro") || main.contains(c) || l.contains(c) || !Tr.known(c)) continue;
			l.add(c);
			big++;
		}
		if (!main.contains("ro") && !l.contains("ro")) l.add("ro");
		String sys = system();
		if (Tr.known(sys) && !main.contains(sys) && !l.contains(sys) && !sys.equals("en")) l.add(sys);
		return l;
	}

	/** The languages offered: English, the phone's own, the country's own, and the one in use (Romanian only as one of them). */
	static List<String> offered(Context ctx) {
		List<String> l = new ArrayList<String>();
		// v5.53 (Viorel): in the US only English and Spanish; elsewhere English and the country's
		// languages (no longer the phone's own language).
		if ("US".equalsIgnoreCase(country(ctx))) {
			l.add("us");
			l.add("es");
			return l;
		}
		l.add("en");
		// v2.001 (Viorel): only the country's first language next to English; its other official
		// languages go under "Other languages" (as Romania's minorities).
		String[] own = Tr.ofCountry(country(ctx));
		if (own.length > 0 && !l.contains(own[0])) l.add(own[0]);
		// The US test mode: American English and Spanish too (v2.30).
		if (Region.showUS(ctx)) for (String c : new String[] { "us", "es" }) if (!l.contains(c)) l.add(c);
		return l;
	}
}
