package ro.meteoalarm.android;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

/**
 * v2.151 (Viorel, A54 log 23.11: background downloads cut off with no result): the downloads ran
 * inside a broadcast, which Android stops after a short while; now they run in a job, which
 * may take minutes. One at a time: a call while one runs is skipped.
 */
public final class RefreshJob extends JobService {
	private static final int ID = 72;
	private static volatile boolean running;

	/** Asks for a refresh now (WHY: alarm, widget, network back…). */
	static void start(Context ctx, String why) {
		if (running) {
			Log.i(ctx, "FUNDAL", why + " · o descărcare e deja în curs");
			return;
		}
		try {
			JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
			if (js == null) throw new IllegalStateException("fără JobScheduler");
			for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == ID) return; // already asked
			PersistableBundle b = new PersistableBundle();
			b.putString("why", why);
			js.schedule(new JobInfo.Builder(ID, new ComponentName(ctx, RefreshJob.class)).setExtras(b).setOverrideDeadline(0).build());
		} catch (Exception e) {
			Log.e(ctx, "FUNDAL", "descărcarea din fundal nu a putut fi pornită", e);
		}
	}

	@Override
	public boolean onStartJob(final JobParameters p) {
		final Context app = getApplicationContext();
		final String why = p.getExtras() == null ? "fundal" : p.getExtras().getString("why", "fundal");
		running = true;
		new Thread(new Runnable() {
			public void run() {
				try {
					Refresher.work(app, why);
				} catch (Exception e) {
					Log.e(app, "FUNDAL", "reîmprospătare", e);
				} finally {
					running = false;
					jobFinished(p, false);
				}
			}
		}).start();
		return true;
	}

	@Override
	public boolean onStopJob(JobParameters p) {
		Log.w(this, "FUNDAL", "Android a oprit descărcarea din fundal înainte de sfârșit");
		running = false;
		return false;
	}
}
