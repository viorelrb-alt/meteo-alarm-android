package ro.meteoalarm.android;

import android.content.Context;

import ro.meteoalarm.android.core.Villages;

/** v4.20: every locality of Romania (assets/villages.txt), read once when first needed. */
final class Places {
	private static Villages all;

	private Places() {
	}

	static synchronized Villages villages(Context c) {
		if (all == null) {
			try {
				all = new Villages(Fetcher.readAll(c.getAssets().open("villages.txt")));
			} catch (Exception e) {
				all = new Villages("");
			}
		}
		return all;
	}
}
