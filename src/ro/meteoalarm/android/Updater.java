package ro.meteoalarm.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import ro.meteoalarm.android.core.Tr;
import ro.meteoalarm.android.core.Release;

/**
 * Updates, only from ☰ › Check for updates (v5.31: no automatic checks): the newest GitHub Release
 * is compared with the installed version and shown in the bottom window; "Descarcă" opens the APK
 * in the browser (the app installs nothing itself, no REQUEST_INSTALL_PACKAGES).
 */
final class Updater {
	private final Activity a;
	private final BottomSheet sheet;
	private final Handler handler = new Handler();
	private boolean busy;

	/** v5.24: checking now (the app does not close itself with the screen off then). */
	boolean working() {
		return busy;
	}
	boolean en;

	/** Why Play Protect may warn when the app (or one of its updates) is installed. */
	static String protectNote(boolean en) {
		return Tr.t(PROTECT_EN, PROTECT_RO);
	}

	/** v5.31 (Viorel 2026-11-14): the app no longer installs anything; the browser downloads the new version. */
	static final String PROTECT_EN = "About the Play Protect warning: Google may say the app \"may be harmful\" because it does not come from Google Play and its developer is not registered with Google. The app does not look for updates by itself and does not install anything. When the Play Protect window appears, tap \"More details\" (if shown), then \"Install without scanning\" (or \"Install anyway\").",
			PROTECT_RO = "Despre avertizarea Play Protect: Google poate spune că aplicația „poate fi dăunătoare” pentru că nu vine din Google Play și dezvoltatorul nu e înregistrat la Google. Aplicația nu caută singură actualizări și nu instalează nimic. Când apare fereastra Play Protect, apasă „Mai multe” (dacă apare), apoi „Instalează fără verificare” (sau „Instalează oricum”).";

	/**
	 * Samsung's Auto Blocker stops apps from outside Galaxy Store / Google Play from installing
	 * (Viorel 2026-11-04, v2.22): it must be off before installing or updating.
	 */
	static String autoBlocker() {
		return Tr.t("Samsung phones with Auto Blocker: turn it off before installing or updating the app (Settings › Security and privacy › Auto Blocker), otherwise the phone refuses to install; you can turn it back on afterwards.",
				"Telefoane Samsung cu Auto Blocker (Blocare automată): dezactivează-l înainte de instalarea sau actualizarea aplicației (Setări › Securitate și confidențialitate › Auto Blocker), altfel telefonul refuză instalarea; după aceea îl poți reactiva.");
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
		return Tr.t("Cancel", "Renunță");
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
		return Tr.t("To get updates: open ☰ › Check for updates and, when asked, give your consent.", "Pentru actualizări: intră în ☰ › Verifică actualizări și, când ești întrebat, dă-ți acordul.");
	}

	/**
	 * Viorel 2026-11-02: before any update, a notice that the updates come from GitHub (not from
	 * Google Play) and the user's consent, only from ☰ › Check for updates: once when given, every
	 * time while refused. Without it nothing is checked or downloaded. THEN runs after "I agree".
	 */
	private void askConsent(final Runnable then) {
		String msg = Tr.t("Meteo Alarm does not come from Google Play. Its new versions are published on GitHub (github.com/viorelrb-alt/meteo-alarm-android). The app looks for a new version only when you choose Check for updates; it shows what is new and, when you tap Download, opens the download in the browser. You install it yourself from the downloaded file.\n\nDo you allow the app to check GitHub for updates? Without your consent it checks nothing; you can agree later from ☰ › Check for updates.",
				"Meteo Alarm nu vine din Google Play. Versiunile noi sunt publicate pe GitHub (github.com/viorelrb-alt/meteo-alarm-android). Aplicația caută o versiune nouă doar când alegi Verifică actualizări; arată ce e nou și, când apeși Descarcă, deschide descărcarea în browser. O instalezi tu din fișierul descărcat.\n\nEști de acord ca aplicația să verifice actualizările pe GitHub? Fără acordul tău nu verifică nimic; îți poți da acordul mai târziu din ☰ › Verifică actualizări.");
		Log.i(a, "UPDATE", "informare actualizare din GitHub afișată");
		sheet.show(Tr.t("Updates from GitHub", "Actualizări din GitHub"), msg, Tr.t("I agree", "Sunt de acord"), new Runnable() {
			public void run() {
				prefs().edit().putBoolean("ghConsent2", true).apply();
				Log.i(a, "UPDATE", "utilizatorul a acceptat actualizarea din GitHub");
				then.run();
			}
		}, Tr.t("Not now", "Nu acum"), new Runnable() {
			public void run() {
				Log.i(a, "UPDATE", "utilizatorul a refuzat actualizarea din GitHub");
				sheet.show(Tr.t("Updates from GitHub", "Actualizări din GitHub"), Tr.t("Without your consent the app will not update. You can change your mind any time from ☰ › Check for updates.", "Fără acordul tău, aplicația nu se va actualiza. Te poți răzgândi oricând din ☰ › Verifică actualizări."),
						null, null, Tr.t("Close", "Închide"));
			}
		});
	}

	/** MANUAL (from the menu): the window opens at once and also says when there is nothing new. */
	void check(final boolean manual) {
		if (busy) {
			// v3.02: a second tap while looking shows the window again (it did nothing).
			if (manual && checkMsg != null) {
				sheet.show(Tr.t("Updates", "Actualizări"), checkMsg, null, null, close());
				dots(checkMsg);
			}
			return;
		}
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
		// v3.00 (Viorel): first, is there a connection at all?
		if (NetJob.kind(a) == 0) {
			Log.i(a, "UPDATE", "fără conexiune la internet, nu verific");
			if (manual) sheet.show(Tr.t("Updates", "Actualizări"), noNet(), null, null, Tr.t("Close", "Închide"));
			return;
		}
		busy = true;
		checkMsg = Tr.t("Checking GitHub for a new version…", "Caut pe GitHub o versiune nouă…");
		if (manual) {
			sheet.show(Tr.t("Updates", "Actualizări"), checkMsg, null, null, close());
			dots(checkMsg);
		}
		new Thread(new Runnable() {
			public void run() {
				Release r = null;
				String err = null;
				Exception ex = null;
				// v3.03 (Viorel 2026-11-08): one try on each: GitHub, the second GitHub server, Google Drive.
				for (int k = 0; k < 3 && r == null; k++) {
					if (k > 0) {
						if (NetJob.kind(a) == 0) break;
						final String m = k == 1 ? Tr.t("GitHub did not answer, trying the second server…", "GitHub nu a răspuns, încerc serverul secundar…")
								: Tr.t("GitHub did not answer, looking on Google Drive…", "GitHub nu a răspuns, caut pe Google Drive…");
						checkMsg = m;
						if (manual) handler.post(new Runnable() {
							public void run() {
								if (busy) dots(m);
							}
						});
					}
					try {
						r = source(k);
						err = null;
						ex = null;
						Log.i(a, "UPDATE", SRC[k] + ": v" + r.version + (r.size > 0 ? ", " + r.size + " octeți" : ""));
					} catch (Exception e) {
						ex = e;
						err = String.valueOf(e.getMessage());
						Log.i(a, "UPDATE", "verificarea pe " + SRC[k] + ": " + err + " (" + Fetcher.reason(e, false) + ")");
					}
				}
				final Release rr = r;
				final String ee = err;
				final Exception fx = ex;
				handler.post(new Runnable() {
					public void run() {
						busy = false;
						checkMsg = null;
						checked(rr, ee, fx, manual);
					}
				});
			}
		}).start();
	}

	/** The names of the sources, for the log. */
	private static final String[] SRC = { "GitHub", "serverul secundar GitHub", "Google Drive" };

	/** The newest version as source K tells it (0 GitHub Releases, 1 second GitHub server, 2 Google Drive). */
	private static Release source(int k) throws Exception {
		if (k == 0) {
			Release r = Release.parse(get(Release.LATEST));
			r.src = 0;
			return r;
		}
		return Release.fromText(get(k == 1 ? Release.RAW_LATEST : Release.DRIVE_DOC), k);
	}

	/** The text of the window while looking (null when not looking). */
	private String checkMsg;
	private String dotsHead, dotsTail;
	private int dotsN;

	/** v3.02 (Viorel): the "…" of MSG grows dot by dot while the window waits. */
	private void dots(String msg) {
		int i = msg.indexOf('…');
		boolean running = dotsHead != null;
		dotsHead = i < 0 ? msg : msg.substring(0, i);
		dotsTail = i < 0 ? "" : msg.substring(i + 1);
		if (!running) {
			dotsN = 0;
			handler.post(dotsTick);
		}
	}

	private void stopDots() {
		dotsHead = null;
		handler.removeCallbacks(dotsTick);
	}

	private final Runnable dotsTick = new Runnable() {
		public void run() {
			if (dotsHead == null) return;
			if (!sheet.isOpen()) {
				dotsHead = null;
				return;
			}
			dotsN = (dotsN + 1) % 4;
			sheet.setMessage(dotsHead + "...".substring(0, dotsN) + dotsTail);
			handler.postDelayed(this, 400);
		}
	};

	/** A short GET (8 s to connect, 12 s to answer); "HTTP code" on a bad answer. */
	private static String get(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(8000);
		c.setReadTimeout(12000);
		c.setRequestProperty("User-Agent", "MeteoAlarmAndroid");
		c.setRequestProperty("Accept", "application/vnd.github+json");
		try {
			int code = c.getResponseCode();
			if (code / 100 != 2) throw new Exception("HTTP " + code);
			InputStream in = c.getInputStream();
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[16384];
			int n;
			while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
			in.close();
			return out.toString("UTF-8");
		} finally {
			c.disconnect();
		}
	}

	private void checked(final Release r, String err, Exception ex, boolean manual) {
		stopDots();
		String cur = installed();
		String t = Tr.t("Updates", "Actualizări");
		if (r == null) Log.e(a, "UPDATE", "verificarea actualizărilor a eșuat: " + err);
		else Log.i(a, "UPDATE", "pe GitHub: v" + r.version + ", instalată v" + cur + (manual ? " (cerut)" : ""));
		if (r == null) {
			if (!manual) return;
			if (err != null && err.contains("404")) sheet.show(t, Tr.t("No version published yet.", "Încă nu e publicată nicio versiune."), null, null, Tr.t("Close", "Închide"));
			else {
				// v3.02 (Viorel): always the reason, and a button to try again.
				String m = NetJob.kind(a) == 0 ? noNet() : Tr.t("None of the update servers answered (GitHub, the second server, Google Drive). Try again in a few minutes.", "Niciun server de actualizare nu a răspuns (GitHub, serverul secundar, Google Drive). Încearcă din nou peste câteva minute.");
				Log.i(a, "UPDATE", "afișat: " + m);
				sheet.show(t, m, Tr.t("Try again", "Încearcă din nou"), new Runnable() {
					public void run() {
						check(true);
					}
				}, Tr.t("Close", "Închide"));
			}
			return;
		}
		if (!r.newerThan(cur)) {
			if (manual) sheet.show(t, (Tr.t("You have the latest version, v", "Ai ultima versiune, v")) + cur + ".", null, null, Tr.t("Close", "Închide"));
			return;
		}
		String msg = (Tr.t("Installed: v", "Instalată: v")) + cur + "    " + (Tr.t("New: v", "Nouă: v")) + r.version;
		if (r.notes.length() > 0) msg += "\n\n" + r.notes;
		msg += "\n\n" + protectNote(en) + "\n\n" + autoBlocker();
		sheet.show(Tr.t("New version: v", "Versiune nouă: v") + r.version, msg, Tr.t("Download", "Descarcă"), new Runnable() {
			public void run() {
				browse(r);
			}
		}, close());
		if (!autoBlockerPhone()) sheet.setLeft(Tr.t("Security", "Securitate"), new Runnable() {
			public void run() {
				openAutoBlocker(a);
			}
		});
	}

	private static String noNet() {
		return Tr.t("No internet connection. Turn on Wi-Fi or mobile data and try again.", "Nu ai conexiune la internet. Pornește Wi-Fi-ul sau datele mobile și încearcă din nou.");
	}

	/** An answer of a busy server (5xx, 429): worth trying again. */
	/**
	 * v5.31 (Viorel 2026-11-14, Play Protect): the app no longer downloads or installs the new
	 * version itself; the browser downloads it and the user installs it from the downloaded file.
	 */
	private void browse(Release r) {
		String t = "v" + r.version;
		String url = r.apkUrl.length() > 0 ? r.apkUrl : r.page.length() > 0 ? r.page : "https://github.com/" + Release.REPO + "/releases/latest";
		Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			a.startActivity(i);
			Log.i(a, "UPDATE", "descărcarea v" + r.version + " deschisă în browser: " + url);
			sheet.show(t, Tr.t("The download opens in the browser. When it is done, open the file from the notification or from Downloads and confirm the install. The first time, the phone asks you to allow installs from the browser.",
					"Descărcarea se deschide în browser. Când se termină, deschide fișierul din notificare sau din Descărcări și confirmă instalarea. Prima dată, telefonul îți cere să permiți instalarea din browser."), null, null, Tr.t("Close", "Închide"));
		} catch (Exception e) {
			Log.e(a, "UPDATE", "browserul nu a putut fi deschis: " + e);
			sheet.show(t, Tr.t("No browser could be opened. The new version is at github.com/viorelrb-alt/meteo-alarm-android/releases.",
					"Nu s-a putut deschide niciun browser. Versiunea nouă e la github.com/viorelrb-alt/meteo-alarm-android/releases."), null, null, Tr.t("Close", "Închide"));
		}
	}

	/** A Samsung phone with One UI 6 or newer (Android 14+), where Auto Blocker exists. */
	static boolean autoBlockerPhone() {
		return "samsung".equalsIgnoreCase(android.os.Build.MANUFACTURER) && android.os.Build.VERSION.SDK_INT >= 34;
	}

	/**
	 * v3.50 (Viorel): Auto Blocker's own page when the phone opens it, else Security and privacy, else
	 * the settings; on other phones Play Protect, else the security settings. Each try is logged.
	 */
	static void openAutoBlocker(Activity a) {
		java.util.List<Intent> tries = new java.util.ArrayList<Intent>();
		if ("samsung".equalsIgnoreCase(android.os.Build.MANUFACTURER)) {
			tries.add(new Intent("com.samsung.android.settings.AUTO_BLOCKER"));
			tries.add(new Intent("com.samsung.settings.AUTO_BLOCKER_SETTINGS"));
			tries.add(new Intent().setClassName("com.android.settings", "com.samsung.android.settings.privacy.AutoBlockerSettingsActivity"));
			tries.add(new Intent().setClassName("com.android.settings", "com.samsung.android.settings.autoblocker.AutoBlockerSettingsActivity"));
			tries.add(new Intent("com.samsung.android.settings.SECURITY_AND_PRIVACY"));
		} else {
			tries.add(new Intent().setClassName("com.google.android.gms", "com.google.android.gms.security.settings.VerifyAppsSettingsActivity"));
		}
		tries.add(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));
		tries.add(new Intent(android.provider.Settings.ACTION_SETTINGS));
		for (Intent i : tries) if (open(a, i, String.valueOf(i.getComponent() != null ? i.getComponent().getClassName() : i.getAction()))) return;
	}

	private static boolean open(Activity a, Intent i, String what) {
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			if (i.resolveActivity(a.getPackageManager()) == null) {
				Log.i(a, "UPDATE", "setări: " + what + " nu există pe acest telefon");
				return false;
			}
			a.startActivity(i);
			Log.i(a, "UPDATE", "setări deschise: " + what);
			return true;
		} catch (Exception e) {
			Log.i(a, "UPDATE", "setări: " + what + " nu s-a putut deschide (" + e + ")");
			return false;
		}
	}
}
