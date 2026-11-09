package ro.meteoalarm.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import ro.meteoalarm.android.core.Tornadoes;
import ro.meteoalarm.android.core.UsFarm;
import ro.meteoalarm.android.core.Weather;

/**
 * Farmer mode in the US (v3.10): downloads, off the main thread, what UsFarm reads for a place,
 * and keeps it in memory: the NWS grid for an hour, the drought map for 12 hours, the storm
 * reports for 30 minutes. Each source on its own: one that does not answer leaves the others.
 */
final class UsFetch {
	private UsFetch() {
	}

	private static final long GRID_MS = 3600000L, DROUGHT_MS = 12 * 3600000L, REPORTS_MS = 30 * 60000L;
	private static final Map<String, UsFarm.Data> cache = new HashMap<String, UsFarm.Data>();
	private static final Map<String, Boolean> running = new HashMap<String, Boolean>();
	private static final Handler ui = new Handler(Looper.getMainLooper());

	private static String key(double lat, double lon) {
		return String.format(Locale.US, "%.3f,%.3f", lat, lon);
	}

	/** What is known for LAT LON (never null; empty until downloaded). */
	static synchronized UsFarm.Data get(double lat, double lon) {
		String k = key(lat, lon);
		UsFarm.Data d = cache.get(k);
		if (d == null) {
			d = new UsFarm.Data();
			cache.put(k, d);
		}
		return d;
	}

	/** Downloads what is old for LAT LON; DONE runs on the main thread when something changed. */
	static void refresh(final Context ctx, final double lat, final double lon, final Runnable done) {
		final String k = key(lat, lon);
		final UsFarm.Data d = get(lat, lon);
		final long now = System.currentTimeMillis();
		final boolean grid = now - d.gridAt > GRID_MS, drought = now - d.droughtAt > DROUGHT_MS, reports = now - d.reportsAt > REPORTS_MS;
		if (!grid && !drought && !reports) return;
		synchronized (UsFetch.class) {
			if (Boolean.TRUE.equals(running.get(k))) return;
			running.put(k, true);
		}
		new Thread(new Runnable() {
			public void run() {
				boolean changed = false;
				try {
					if (grid) {
						d.gridAt = now;
						try {
							String g = UsFarm.gridUrl(Weather.get(UsFarm.pointsUrl(lat, lon)));
							if (g == null) throw new IllegalArgumentException("no forecastGridData");
							d.grid = UsFarm.parseGrid(Weather.get(g));
							Log.i(ctx, "SUA", "grila NWS: " + d.grid.size() + " straturi");
							changed = true;
						} catch (Exception e) {
							d.gridAt = now - GRID_MS + 10 * 60000L;
							Log.e(ctx, "SUA", "grila NWS: " + Fetcher.reason(e, false) + " (" + e.getMessage() + ")");
						}
					}
					if (drought) {
						d.droughtAt = now;
						try {
							long[] r = UsFarm.parseDrought(Weather.get(UsFarm.droughtUrl(lat, lon)));
							d.drought = (int) r[0];
							d.droughtDate = r[1] > 0 ? new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date(r[1])) : "";
							Log.i(ctx, "SUA", "seceta: " + (d.drought < 0 ? "fără" : "D" + d.drought) + (d.droughtDate.length() > 0 ? " (harta " + d.droughtDate + ")" : ""));
							changed = true;
						} catch (Exception e) {
							d.droughtAt = now - DROUGHT_MS + 30 * 60000L;
							Log.e(ctx, "SUA", "seceta (US Drought Monitor): " + Fetcher.reason(e, false) + " (" + e.getMessage() + ")");
						}
					}
					if (reports) {
						d.reportsAt = now;
						try {
							long t0 = Tornadoes.dayStart(now, 0), t1 = Tornadoes.dayStart(now, 1);
							java.util.List<Tornadoes.Report> h = UsFarm.near(Weather.get(UsFarm.HAIL_TODAY), t0, lat, lon);
							h.addAll(UsFarm.near(Weather.get(UsFarm.HAIL_YESTERDAY), t1, lat, lon));
							java.util.List<Tornadoes.Report> w = UsFarm.near(Weather.get(UsFarm.WIND_TODAY), t0, lat, lon);
							w.addAll(UsFarm.near(Weather.get(UsFarm.WIND_YESTERDAY), t1, lat, lon));
							Tornadoes.sort(h);
							Tornadoes.sort(w);
							d.hail = h;
							d.wind = w;
							d.reportsOk = true;
							Log.i(ctx, "SUA", "rapoarte SPC în " + Math.round(UsFarm.NEAR_KM) + " km: grindină " + h.size() + ", vânt " + w.size());
							changed = true;
						} catch (Exception e) {
							d.reportsAt = now - REPORTS_MS + 10 * 60000L;
							Log.e(ctx, "SUA", "rapoarte SPC: " + Fetcher.reason(e, false) + " (" + e.getMessage() + ")");
						}
					}
				} finally {
					synchronized (UsFetch.class) {
						running.remove(k);
					}
				}
				if (changed && done != null) ui.post(done);
			}
		}).start();
	}
}
