package ro.meteoalarm.android;

import android.app.Activity;
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
 * In-app updates: the newest GitHub Release of the app is compared with the installed version and
 * shown in the bottom window. "Actualizează" downloads the APK to the app's cache and hands it to
 * the system installer, which asks the user to confirm (and, the first time, to allow installs
 * from this app). Play Protect may warn about it; protectNote() explains why.
 */
final class Updater {
	private final Activity a;
	private final BottomSheet sheet;
	private final Handler handler = new Handler();
	private boolean busy;
	boolean en;

	/** Why Play Protect may warn when the app (or one of its updates) is installed. */
	static String protectNote(boolean en) {
		return en
				? "About the Play Protect warning: Google may say the app \"may be harmful\". It does so because the app "
						+ "does not come from Google Play, its developer is not registered with Google, and it can install its "
						+ "own updates (from github.com/viorelrb-alt/meteo-alarm-android). The app does nothing else. When the Play "
						+ "Protect window appears, tap \"More details\" (if shown), then \"Install without scanning\" "
						+ "(or \"Install anyway\")."
				: "Despre avertizarea Play Protect: Google poate spune că aplicația „poate fi dăunătoare”. O face pentru că "
						+ "aplicația nu vine din Google Play, dezvoltatorul nu e înregistrat la Google și aplicația își poate "
						+ "instala singură actualizările (de pe github.com/viorelrb-alt/meteo-alarm-android). Aplicația nu face "
						+ "nimic altceva. Când apare fereastra Play Protect, apasă „Mai multe” (dacă apare), apoi "
						+ "„Instalează fără verificare” (sau „Instalează oricum”).";
	}

	Updater(Activity a, BottomSheet sheet) {
		this.a = a;
		this.sheet = sheet;
	}

	String installed() {
		try {
			return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
		} catch (Exception e) {
			return "0";
		}
	}

	private String close() {
		return en ? "Cancel" : "Renunță";
	}

	private android.content.SharedPreferences prefs() {
		return a.getSharedPreferences("settings", Activity.MODE_PRIVATE);
	}

	/**
	 * The user agreed to updates from GitHub, in ☰ › Check for updates. "ghConsent2" (v1.93): the
	 * consents given in v1.92, where the question came up by itself, do not count.
	 */
	boolean agreed() {
		return prefs().getBoolean("ghConsent2", false);
	}

	/** The line at the top of What's new, About and Help (Viorel 2026-11-02). */
	static String howTo(boolean en) {
		return en ? "To get updates: open ☰ › Check for updates and, when asked, give your consent."
				: "Pentru actualizări: intră în ☰ › Verifică actualizări și, când ești întrebat, dă-ți acordul.";
	}

	/**
	 * Viorel 2026-11-02: before any update, a notice that the updates come from GitHub (not from
	 * Google Play) and the user's consent, only from ☰ › Check for updates: once when given, every
	 * time while refused. Without it nothing is checked or downloaded. THEN runs after "I agree".
	 */
	private void askConsent(final Runnable then) {
		String msg = en
				? "Meteo Alarm does not come from Google Play. Its new versions are published on GitHub "
						+ "(github.com/viorelrb-alt/meteo-alarm-android), and the app updates itself from there: it checks "
						+ "about once a week (or when you choose Check for updates), shows what is new, and only when you "
						+ "tap Update downloads the new version and opens the phone's installer, where you confirm again.\n\n"
						+ "Do you allow the app to update this way? Without your consent the app does not look for updates "
						+ "and does not download anything; you can agree later from ☰ › Check for updates."
				: "Meteo Alarm nu vine din Google Play. Versiunile noi sunt publicate pe GitHub "
						+ "(github.com/viorelrb-alt/meteo-alarm-android), iar aplicația se actualizează de acolo: verifică "
						+ "cam o dată pe săptămână (sau când alegi Verifică actualizări), arată ce e nou și abia când apeși "
						+ "Actualizează descarcă versiunea nouă și deschide programul de instalare al telefonului, unde confirmi din nou.\n\n"
						+ "Ești de acord ca aplicația să se actualizeze așa? Fără acordul tău aplicația nu caută actualizări "
						+ "și nu descarcă nimic; îți poți da acordul mai târziu din ☰ › Verifică actualizări.";
		Log.i(a, "UPDATE", "informare actualizare din GitHub afișată");
		sheet.show(en ? "Updates from GitHub" : "Actualizări din GitHub", msg, en ? "I agree" : "Sunt de acord", new Runnable() {
			public void run() {
				prefs().edit().putBoolean("ghConsent2", true).apply();
				Log.i(a, "UPDATE", "utilizatorul a acceptat actualizarea din GitHub");
				then.run();
			}
		}, en ? "Not now" : "Nu acum", new Runnable() {
			public void run() {
				Log.i(a, "UPDATE", "utilizatorul a refuzat actualizarea din GitHub");
				sheet.show(en ? "Updates from GitHub" : "Actualizări din GitHub", en
						? "Without your consent the app will not update. You can change your mind any time from ☰ › Check for updates."
						: "Fără acordul tău, aplicația nu se va actualiza. Te poți răzgândi oricând din ☰ › Verifică actualizări.",
						null, null, en ? "Close" : "Închide");
			}
		});
	}

	/** MANUAL (from the menu): the window opens at once and also says when there is nothing new. */
	void check(final boolean manual) {
		if (busy) return;
		if (!agreed()) {
			if (!manual) {
				Log.i(a, "UPDATE", "fără acord pentru actualizarea din GitHub, nu verific");
				return;
			}
			askConsent(new Runnable() {
				public void run() {
					check(true);
				}
			});
			return;
		}
		busy = true;
		if (manual) sheet.show(en ? "Updates" : "Actualizări", en ? "Checking GitHub for a new version…" : "Caut pe GitHub o versiune nouă…",
				null, null, close());
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
		String cur = installed();
		String t = en ? "Updates" : "Actualizări";
		if (r == null) Log.e(a, "UPDATE", "verificarea actualizărilor a eșuat: " + err);
		else Log.i(a, "UPDATE", "pe GitHub: v" + r.version + ", instalată v" + cur + (manual ? " (cerut)" : ""));
		if (r == null) {
			if (!manual) return;
			if (err != null && err.contains("404")) sheet.show(t, en ? "No version published yet." : "Încă nu e publicată nicio versiune.", null, null, en ? "Close" : "Închide");
			else sheet.show(t, en ? "Could not check for updates. Is the internet on?" : "Nu am putut verifica actualizările. Ai internet?", null, null, en ? "Close" : "Închide");
			return;
		}
		if (!r.newerThan(cur)) {
			if (manual) sheet.show(t, (en ? "You have the latest version, v" : "Ai ultima versiune, v") + cur + ".", null, null, en ? "Close" : "Închide");
			return;
		}
		String msg = (en ? "Installed: v" : "Instalată: v") + cur + "    " + (en ? "New: v" : "Nouă: v") + r.version;
		if (r.notes.length() > 0) msg += "\n\n" + r.notes;
		msg += "\n\n" + protectNote(en);
		sheet.show(en ? "New version: v" + r.version : "Versiune nouă: v" + r.version, msg, en ? "Update" : "Actualizează", new Runnable() {
			public void run() {
				download(r);
			}
		}, close());
	}

	private void download(final Release r) {
		final String t = "v" + r.version;
		sheet.show(t, en ? "Downloading…" : "Se descarcă…", null, null, en ? "Hide" : "Ascunde");
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
									sheet.setMessage((en ? "Downloading… " : "Se descarcă… ") + (pct >= 0 ? pct + "%" : ""));
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
						if (ee != null) sheet.show(t, en ? "The download failed. Is the internet on?" : "Descărcarea a eșuat. Ai internet?", null, null, en ? "Close" : "Închide");
						else install(t);
					}
				});
			}
		}).start();
	}

	/** Opens the system installer on the downloaded APK; the window closes, the installer takes over. */
	private void install(String t) {
		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setDataAndType(ApkProvider.uri(), "application/vnd.android.package-archive");
		i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			a.startActivity(i);
			sheet.hide();
		} catch (Exception e) {
			sheet.show(t, en ? "The installer could not be opened." : "Instalarea nu a putut fi pornită.", null, null, en ? "Close" : "Închide");
		}
	}
}
