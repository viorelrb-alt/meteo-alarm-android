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
