package ro.meteoalarm.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import ro.meteoalarm.android.core.Release;
import ro.meteoalarm.android.core.Weather;

/**
 * In-app updates: the newest GitHub Release of the app is compared with the installed version;
 * a newer APK is downloaded to the app's cache and handed to the system installer, which asks
 * the user to confirm (and, the first time, to allow installs from this app).
 */
final class Updater {
	interface Ui {
		AlertDialog.Builder dialog();

		void show(AlertDialog.Builder b);

		boolean en();
	}

	private final Activity a;
	private final Ui ui;
	private final Handler handler = new Handler();
	private boolean busy;

	Updater(Activity a, Ui ui) {
		this.a = a;
		this.ui = ui;
	}

	String installed() {
		try {
			return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
		} catch (Exception e) {
			return "0";
		}
	}

	/** MANUAL: also say when there is nothing new or the check failed. */
	void check(final boolean manual) {
		if (busy) return;
		busy = true;
		if (manual) toast(ui.en() ? "Checking for updates…" : "Caut actualizări…");
		new Thread(new Runnable() {
			public void run() {
				Release r = null;
				String err = null;
				try {
					r = Release.parse(Weather.get(Release.LATEST));
				} catch (Exception e) {
					err = String.valueOf(e.getMessage());
				}
				final Release rr = r;
				final String ee = err;
				handler.post(new Runnable() {
					public void run() {
						busy = false;
						checked(rr, ee, manual);
					}
				});
			}
		}).start();
	}

	private void checked(final Release r, String err, boolean manual) {
		boolean en = ui.en();
		String cur = installed();
		if (r == null) {
			if (!manual) return;
			if (err != null && err.contains("404")) toast(en ? "No version published yet." : "Încă nu e publicată nicio versiune.");
			else toast(en ? "Could not check for updates (internet?)." : "Nu am putut verifica actualizările (internet?).");
			return;
		}
		if (!r.newerThan(cur)) {
			if (manual) toast((en ? "You have the latest version: v" : "Ai ultima versiune: v") + cur);
			return;
		}
		String msg = (en ? "Installed: v" : "Instalată: v") + cur + "\n" + (en ? "New: v" : "Nouă: v") + r.version;
		if (r.notes.length() > 0) msg += "\n\n" + r.notes;
		ui.show(ui.dialog().setTitle(en ? "New version available" : "Versiune nouă disponibilă").setMessage(msg)
				.setPositiveButton(en ? "Download and install" : "Descarcă și instalează", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface d, int w) {
						download(r);
					}
				}).setNegativeButton(en ? "Later" : "Mai târziu", null));
	}

	private void download(final Release r) {
		final boolean en = ui.en();
		final AlertDialog progress = ui.dialog().setTitle("Meteo Alarm v" + r.version)
				.setMessage(en ? "Downloading…" : "Se descarcă…").setCancelable(false).create();
		progress.show();
		new Thread(new Runnable() {
			public void run() {
				final File f = ApkProvider.file(a);
				String err = null;
				try {
					f.delete();
					HttpURLConnection c = (HttpURLConnection) new URL(r.apkUrl).openConnection();
					c.setConnectTimeout(20000);
					c.setReadTimeout(60000);
					c.setInstanceFollowRedirects(true);
					c.setRequestProperty("User-Agent", "MeteoAlarmAndroid");
					if (c.getResponseCode() / 100 != 2) throw new Exception("HTTP " + c.getResponseCode());
					long total = r.size > 0 ? r.size : c.getContentLength();
					InputStream in = c.getInputStream();
					FileOutputStream out = new FileOutputStream(f);
					byte[] buf = new byte[32768];
					long got = 0;
					int n, last = -1;
					while ((n = in.read(buf)) > 0) {
						out.write(buf, 0, n);
						got += n;
						final int pct = total > 0 ? (int) (got * 100 / total) : -1;
						if (pct != last) {
							last = pct;
							handler.post(new Runnable() {
								public void run() {
									progress.setMessage((en ? "Downloading… " : "Se descarcă… ") + (pct >= 0 ? pct + "%" : ""));
								}
							});
						}
					}
					out.close();
					in.close();
					c.disconnect();
					if (r.size > 0 && f.length() != r.size) throw new Exception("size " + f.length() + " != " + r.size);
				} catch (Exception e) {
					err = String.valueOf(e.getMessage());
					f.delete();
				}
				final String ee = err;
				handler.post(new Runnable() {
					public void run() {
						progress.dismiss();
						if (ee != null) toast(en ? "The download failed." : "Descărcarea a eșuat.");
						else install();
					}
				});
			}
		}).start();
	}

	/** Opens the system installer on the downloaded APK. */
	private void install() {
		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setDataAndType(ApkProvider.uri(), "application/vnd.android.package-archive");
		i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			a.startActivity(i);
		} catch (Exception e) {
			toast(ui.en() ? "The installer could not be opened." : "Instalarea nu a putut fi pornită.");
		}
	}

	private void toast(String m) {
		android.widget.Toast.makeText(a, m, android.widget.Toast.LENGTH_LONG).show();
	}
}
