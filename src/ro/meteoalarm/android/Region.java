package ro.meteoalarm.android;

import android.content.Context;
import android.content.SharedPreferences;

import ro.meteoalarm.android.core.Cities;

/**
 * Europe or the United States (v2.30, Viorel 2026-11-04).
 *
 * A phone in the US (from the SIM / mobile network, or the last town the location found) sees only
 * the US part: its 3 US favourites, the towns of the state it is in and 3 towns of every other
 * state, imperial units and the National Weather Service warnings. A phone elsewhere sees only
 * what it saw before. A hidden test mode (7 taps on the title of ☰ › About) shows both, as if the
 * phone were in a chosen state.
 *
 * Units and clock are kept for each of the two kinds of town: US towns start imperial with a
 * 12-hour clock; the others start metric, except on a phone in the US, which starts imperial
 * everywhere. Leaving °F for °C asks whether to switch to the metric system.
 */
final class Region {
	private Region() {
	}

	private static SharedPreferences prefs(Context ctx) {
		return Fetcher.prefs(ctx);
	}

	/** The hidden test mode is on. */
	static boolean test(Context ctx) {
		return prefs(ctx).getBoolean("usTest", false);
	}

	/** The state the phone is in, as the last location found it ("Oklahoma"; empty when not in the US or unknown). */
	static String foundState(Context ctx) {
		return prefs(ctx).getString("usState", "");
	}

	/** The phone is in the US: the last location found a US town, or (without one) the SIM / network is American. */
	static boolean usPhone(Context ctx) {
		SharedPreferences p = prefs(ctx);
		String loc = p.getString("locCountry", "");
		if (loc.length() == 2) return loc.equals("us");
		return simCountry(ctx).equals("us");
	}

	/** The US part is shown (a phone in the US, or the test mode). */
	static boolean showUS(Context ctx) {
		return usPhone(ctx) || test(ctx);
	}

	/** The European part is shown (a phone outside the US, or the test mode). */
	static boolean showEU(Context ctx) {
		return !usPhone(ctx) || test(ctx);
	}

	/** The state whose towns are all listed: the test mode's, else the one the phone is in (empty when unknown). */
	static String state(Context ctx) {
		if (test(ctx)) {
			String t = Cities.stateName(prefs(ctx).getString("usTestState", "OK"));
			return t.length() > 0 ? t : "Oklahoma";
		}
		return usPhone(ctx) ? foundState(ctx) : "";
	}

	/** The country of the SIM card, else of the mobile network (lower case; empty without them). */
	static String simCountry(Context ctx) {
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
		return "";
	}

	/** The settings key of town C's kind: "us" or "eu". */
	private static String kind(Cities.City c) {
		return c != null && c.us() ? "us" : "eu";
	}

	/** Imperial units (°F, mph, inches, inHg, miles) for town C. */
	static boolean imperial(Context ctx, Cities.City c) {
		SharedPreferences p = prefs(ctx);
		if (c != null && c.us()) return p.getBoolean("imp_us", true);
		if (p.contains("imp_eu")) return p.getBoolean("imp_eu", false);
		// Before v2.30 there was one switch, °C/°F.
		return usPhone(ctx) || p.getBoolean("unitF", false);
	}

	/** The 12-hour clock for town C. */
	static boolean clk12(Context ctx, Cities.City c) {
		SharedPreferences p = prefs(ctx);
		if (c != null && c.us()) return p.getBoolean("clk_us", true);
		if (p.contains("clk_eu")) return p.getBoolean("clk_eu", false);
		return usPhone(ctx) || p.getBoolean("clk12", false);
	}

	static void setImperial(Context ctx, Cities.City c, boolean on) {
		prefs(ctx).edit().putBoolean("imp_" + kind(c), on).apply();
	}

	static void setClk12(Context ctx, Cities.City c, boolean on) {
		prefs(ctx).edit().putBoolean("clk_" + kind(c), on).apply();
	}

	/** The favourites' settings key for town C's kind: "favsUS" for US towns, "favs" for the others. */
	static String favsKey(Cities.City c) {
		return c != null && c.us() ? "favsUS" : "favs";
	}
}
