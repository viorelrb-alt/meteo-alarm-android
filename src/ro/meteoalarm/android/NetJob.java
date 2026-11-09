package ro.meteoalarm.android;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Without internet the background refresh waits for the network instead of trying again every
 * minute (Viorel 2026-11-04, v2.20; A52s log: a failed try every 1–2 minutes): the phone starts
 * this job when a network is back, and it asks Refresher for the weather at once.
 */
public final class NetJob extends JobService {
	private static final int ID = 71;
	static final String ACTION = "ro.meteoalarm.android.NET";

	/** A network that is connected now. */
	@SuppressWarnings("deprecation")
	static boolean online(Context ctx) {
		try {
			ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
			return n != null && n.isConnected();
		} catch (Exception e) {
			return true; // unknown: try as before
		}
	}

	/**
	 * v3.80 (Viorel): how strong the connection is (0..4, -1 unknown) and its kind: "4", "5",
	 * "6", "6E", "7" for Wi-Fi 4..7, "2G".."5G" for mobile data ("" unknown).
	 */
	static Object[] signal(Context ctx, int kind) {
		int level = -1;
		String tag = "";
		try {
			if (kind == 1) {
				Object wi = null;
				if (android.os.Build.VERSION.SDK_INT >= 31) {
					ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
					android.net.Network n = cm.getActiveNetwork();
					android.net.NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
					if (nc != null) wi = nc.getClass().getMethod("getTransportInfo").invoke(nc);
				}
				if (!(wi instanceof android.net.wifi.WifiInfo)) {
					android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
					wi = wm == null ? null : wm.getConnectionInfo();
				}
				if (wi instanceof android.net.wifi.WifiInfo) {
					android.net.wifi.WifiInfo w = (android.net.wifi.WifiInfo) wi;
					int rssi = w.getRssi();
					if (rssi > -127 && rssi < 0) level = rssi >= -55 ? 4 : rssi >= -66 ? 3 : rssi >= -77 ? 2 : rssi >= -88 ? 1 : 0;
					if (android.os.Build.VERSION.SDK_INT >= 30) {
						int std = (Integer) w.getClass().getMethod("getWifiStandard").invoke(w);
						tag = std == 4 ? "4" : std == 5 ? "5" : std == 6 ? (w.getFrequency() > 5925 ? "6E" : "6") : std == 8 ? "7" : "";
					}
				}
			} else if (kind == 2 && android.os.Build.VERSION.SDK_INT >= 28) {
				android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
				Object ss = tm == null ? null : tm.getClass().getMethod("getSignalStrength").invoke(tm);
				if (ss != null) {
					level = (Integer) ss.getClass().getMethod("getLevel").invoke(ss);
					if (android.os.Build.VERSION.SDK_INT >= 29) {
						java.util.List<?> cells = (java.util.List<?>) ss.getClass().getMethod("getCellSignalStrengths").invoke(ss);
						int best = 0;
						for (Object c : cells) {
							String n = c.getClass().getSimpleName();
							int g = n.contains("Nr") ? 5 : n.contains("Lte") ? 4 : n.contains("Wcdma") || n.contains("Tdscdma") ? 3 : n.contains("Gsm") || n.contains("Cdma") ? 2 : 0;
							best = Math.max(best, g);
						}
						if (best > 0) tag = best + "G";
					}
				}
			}
		} catch (Throwable e) {
		}
		return new Object[] { Math.max(-1, Math.min(4, level)), tag };
	}

	/**
	 * v3.00: the connection now: 0 none, 1 Wi-Fi, 2 mobile data, 3 another (cable, VPN…), for the
	 * icon next to the clock and the check before an update.
	 */
	static int kind(Context ctx) {
		try {
			ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
			if (cm == null) return 3;
			android.net.Network n = cm.getActiveNetwork();
			android.net.NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
			if (nc == null) return 0;
			if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) return 1;
			if (nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) return 2;
			return 3;
		} catch (Exception e) {
			return 3; // unknown: as if there were one
		}
	}

	/** Asks the phone to start this job when a network comes; true when it was set. */
	static boolean waitForNet(Context ctx) {
		try {
			JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
			if (js == null) return false;
			for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == ID) return true;
			js.schedule(new JobInfo.Builder(ID, new ComponentName(ctx, NetJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
			return true;
		} catch (Exception e) {
			Log.e(ctx, "FUNDAL", "așteptarea rețelei nu a putut fi pusă", e);
			return false;
		}
	}

	@Override
	public boolean onStartJob(JobParameters p) {
		Log.i(this, "FUNDAL", "rețeaua a revenit");
		sendBroadcast(new Intent(this, Refresher.class).setAction(ACTION));
		return false;
	}

	@Override
	public boolean onStopJob(JobParameters p) {
		return false;
	}
}
