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
		return Tr.t("About the Play Protect warning: Google may say the app \"may be harmful\". It does so because the app "
						+ "does not come from Google Play, its developer is not registered with Google, and it can install its "
						+ "own updates (from github.com/viorelrb-alt/meteo-alarm-android). The app does nothing else. When the Play "
						+ "Protect window appears, tap \"More details\" (if shown), then \"Install without scanning\" "
						+ "(or \"Install anyway\").", "Despre avertizarea Play Protect: Google poate spune că aplicația „poate fi dăunătoare”. O face pentru că "
						+ "aplicația nu vine din Google Play, dezvoltatorul nu e înregistrat la Google și aplicația își poate "
						+ "instala singură actualizările (de pe github.com/viorelrb-alt/meteo-alarm-android). Aplicația nu face "
						+ "nimic altceva. Când apare fereastra Play Protect, apasă „Mai multe” (dacă apare), apoi "
						+ "„Instalează fără verificare” (sau „Instalează oricum”).");
	}

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
		String msg = Tr.t("Meteo Alarm does not come from Google Play. Its new versions are published on GitHub "
						+ "(github.com/viorelrb-alt/meteo-alarm-android), and the app updates itself from there: it checks "
						+ "about once a week (or when you choose Check for updates), shows what is new, and only when you "
						+ "tap Update downloads the new version and opens the phone's installer, where you confirm again.\n\n"
						+ "Do you allow the app to update this way? Without your consent the app does not look for updates "
						+ "and does not download anything; you can agree later from ☰ › Check for updates.", "Meteo Alarm nu vine din Google Play. Versiunile noi sunt publicate pe GitHub "
						+ "(github.com/viorelrb-alt/meteo-alarm-android), iar aplicația se actualizează de acolo: verifică "
						+ "cam o dată pe săptămână (sau când alegi Verifică actualizări), arată ce e nou și abia când apeși "
						+ "Actualizează descarcă versiunea nouă și deschide programul de instalare al telefonului, unde confirmi din nou.\n\n"
						+ "Ești de acord ca aplicația să se actualizeze așa? Fără acordul tău aplicația nu caută actualizări "
						+ "și nu descarcă nimic; îți poți da acordul mai târziu din ☰ › Verifică actualizări.");
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
		sheet.show(Tr.t("New version: v", "Versiune nouă: v") + r.version, msg, Tr.t("Update", "Actualizează"), new Runnable() {
			public void run() {
				download(r);
			}
		}, close());
		if (!autoBlockerPhone()) sheet.setLeft(Tr.t("Security", "Securitate"), new Runnable() {
			public void run() {
				openAutoBlocker(a);
			}
		});
	}

	private static final int TRIES = 5;

	private static String noNet() {
		return Tr.t("No internet connection. Turn on Wi-Fi or mobile data and try again.", "Nu ai conexiune la internet. Pornește Wi-Fi-ul sau datele mobile și încearcă din nou.");
	}

	/** An answer of a busy server (5xx, 429): worth trying again. */
	private static boolean busyServer(String err) {
		return err != null && (err.startsWith("HTTP 5") || err.startsWith("HTTP 429"));
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			// go on
		}
	}

	/** Why the download stopped, in words, for the window (v3.00). */
	private static String why(Exception e, String err) {
		if (busyServer(err)) {
			String code = err.length() >= 8 ? err.substring(5, 8) : "";
			return Tr.t("GitHub is not answering now (error ", "GitHub nu răspunde acum (eroare ") + code
					+ Tr.t("). Try again in a few minutes.", "). Încearcă din nou peste câteva minute.");
		}
		if (err != null && err.startsWith("drive")) return Tr.t("The update could not be downloaded from any server. Try again in a few minutes.", "Actualizarea nu a putut fi descărcată de pe niciun server. Încearcă din nou peste câteva minute.");
		if (err != null && err.startsWith("size ")) return Tr.t("The downloaded file is incomplete. Try again.", "Fișierul descărcat e incomplet. Încearcă din nou.");
		return Tr.t("The download failed: ", "Descărcarea a eșuat: ") + Fetcher.reason(e, !Tr.ro()) + ".";
	}

	/**
	 * Downloads the new version (v3.00, Viorel 2026-11-08: GitHub answered 504 to about two tries
	 * in three): up to 5 tries a few seconds apart, each step in the log, the reason on the screen.
	 */
	private void download(final Release r) {
		final String t = "v" + r.version;
		// v3.50 (Viorel): before an update, what would stop the phone from installing it.
		if (!canInstall()) {
			Log.i(a, "UPDATE", "aplicația nu are voie să instaleze aplicații: deschid setarea");
			sheet.show(t, Tr.t("Meteo Alarm is not allowed to install updates. Tap Open settings, turn on Allow from this source, then come back and tap Update again.",
					"Meteo Alarm nu are voie să instaleze actualizări. Apasă Deschide setările, pornește Permite din această sursă, apoi revino și apasă din nou Actualizează."),
					Tr.t("Open settings", "Deschide setările"), new Runnable() {
						public void run() {
							sheet.hide();
							openInstallSetting();
						}
					}, close());
			return;
		}
		if (autoBlockerPhone() && !prefs().getBoolean("abSkip", false) && !r.version.equals(abSeen)) {
			abSeen = r.version;
			Log.i(a, "UPDATE", "telefon Samsung cu Auto Blocker: arăt nota înainte de descărcare");
			sheet.show(t, Tr.t("Samsung: Auto Blocker must be off, otherwise the phone refuses the update. The app cannot see whether it is on. Tap Settings to open its page, turn it off, then come back and tap Continue. You can turn it back on after the update.",
					"Samsung: Auto Blocker trebuie să fie oprit, altfel telefonul refuză actualizarea. Aplicația nu poate vedea dacă e pornit. Apasă Setări ca să-i deschizi pagina, oprește-l, apoi revino și apasă Continuă. Îl poți porni din nou după actualizare."),
					Tr.t("Continue", "Continuă"), new Runnable() {
						public void run() {
							download(r);
						}
					}, Tr.t("Don't show again", "Nu mai afișa"), new Runnable() {
						public void run() {
							prefs().edit().putBoolean("abSkip", true).apply();
							Log.i(a, "UPDATE", "nota Auto Blocker: nu mai afișa");
							download(r);
						}
					});
			sheet.setLeft(Tr.t("Settings", "Setări"), new Runnable() {
				public void run() {
					openAutoBlocker(a);
				}
			});
			return;
		}
		if (NetJob.kind(a) == 0) {
			Log.i(a, "UPDATE", "descărcare oprită: fără conexiune la internet");
			sheet.show(t, noNet(), null, null, Tr.t("Close", "Închide"));
			return;
		}
		// v3.00 (Viorel): the same version already downloaded whole: straight to the installer.
		if (downloaded(r)) {
			Log.i(a, "UPDATE", "v" + r.version + " e deja descărcată (" + ApkProvider.file(a).length() + " octeți): pornesc instalarea");
			install(t);
			return;
		}
		sheet.show(t, Tr.t("Downloading…", "Se descarcă…"), null, null, Tr.t("Hide", "Ascunde"));
		dots(Tr.t("Downloading…", "Se descarcă…"));
		Log.i(a, "UPDATE", "descarc v" + r.version + " (" + r.size + " octeți) de la " + r.apkUrl);
		new Thread(new Runnable() {
			public void run() {
				final File f = ApkProvider.file(a);
				String err = null;
				Exception ex = null;
				for (int k = 1; k <= TRIES; k++) {
					err = null;
					ex = null;
					try {
						fetch(r, f, k);
						Log.i(a, "UPDATE", "descărcat: " + f.length() + " octeți, încercarea " + k);
						break;
					} catch (Exception e) {
						ex = e;
						err = String.valueOf(e.getMessage());
						f.delete();
						Log.e(a, "UPDATE", "descărcarea, încercarea " + k + "/" + TRIES + ": " + err + " (" + Fetcher.reason(e, false) + ")");
						boolean again = k < TRIES && (busyServer(err) || e instanceof java.io.IOException || err.startsWith("size ")) && NetJob.kind(a) != 0;
						if (!again) break;
						// v3.03 (Viorel): after a failed try, the next source that has the same version.
						boolean moved = false;
						for (int n = r.src + 1; n < 3 && !moved; n++) {
							try {
								Release d = source(n);
								if (d.version.equals(r.version) && d.apkUrl.length() > 0) {
									r.apkUrl = d.apkUrl;
									if (d.size > 0) r.size = d.size;
									r.src = n;
									moved = true;
									Log.i(a, "UPDATE", "descarc de pe " + SRC[n]);
								} else Log.i(a, "UPDATE", SRC[n] + " are v" + d.version + ", nu v" + r.version);
							} catch (Exception de) {
								Log.i(a, "UPDATE", SRC[n] + ": " + de.getMessage() + " (" + Fetcher.reason(de, false) + ")");
							}
						}
						final int next = k + 1;
						final int sr = r.src;
						handler.post(new Runnable() {
							public void run() {
								dots(sr == 2 ? Tr.t("Downloading from Google Drive…", "Se descarcă de pe Google Drive…")
										: sr == 1 ? Tr.t("Downloading from the second server…", "Se descarcă de pe serverul secundar…")
										: Tr.t("GitHub did not answer, trying again (", "GitHub nu a răspuns, reîncerc (") + next + "/" + TRIES + ")…");
							}
						});
						if (!moved) sleep(3000L * k);
					}
				}
				final String ee = err;
				final Exception fe = ex;
				handler.post(new Runnable() {
					public void run() {
						stopDots();
						if (ee == null) {
							install(t);
							return;
						}
						String m = NetJob.kind(a) == 0 ? noNet() : why(fe, ee);
						Log.e(a, "UPDATE", "actualizarea a eșuat: " + m);
						sheet.show(t, m, Tr.t("Try again", "Încearcă din nou"), new Runnable() {
							public void run() {
								download(r);
							}
						}, Tr.t("Close", "Închide"));
					}
				});
			}
		}).start();
	}

	/** The APK kept from an earlier download is this version, whole (its size and its version name). */
	private boolean downloaded(Release r) {
		try {
			File f = ApkProvider.file(a);
			if (!f.exists() || f.length() == 0 || (r.size > 0 && f.length() != r.size)) return false;
			android.content.pm.PackageInfo pi = a.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
			return pi != null && r.version.equals(pi.versionName);
		} catch (Exception e) {
			return false;
		}
	}

	/** One try (K) of the download into F; throws on a bad answer or a short file. */
	private void fetch(Release r, File f, int k) throws Exception {
		f.delete();
		HttpURLConnection c = (HttpURLConnection) new URL(r.apkUrl).openConnection();
		c.setConnectTimeout(20000);
		c.setReadTimeout(60000);
		c.setInstanceFollowRedirects(true);
		c.setRequestProperty("User-Agent", "MeteoAlarmAndroid");
		try {
			int code = c.getResponseCode();
			Log.i(a, "UPDATE", "încercarea " + k + ": răspuns HTTP " + code + (c.getURL() != null ? " de la " + c.getURL().getHost() : ""));
			if (code / 100 != 2) throw new Exception("HTTP " + code);
			// A Google Drive file that is not shared answers with a sign-in page.
			String ct = c.getContentType();
			if (ct != null && ct.startsWith("text/html")) throw new Exception("drive html (not shared?)");
			long total = r.size > 0 ? r.size : c.getContentLength();
			InputStream in = c.getInputStream();
			FileOutputStream out = new FileOutputStream(f);
			try {
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
								dots((Tr.t("Downloading… ", "Se descarcă… ")) + (pct >= 0 ? pct + "%" : ""));
							}
						});
					}
				}
			} finally {
				out.close();
				in.close();
			}
			if (r.size > 0 && f.length() != r.size) throw new Exception("size " + f.length() + " != " + r.size);
		} finally {
			c.disconnect();
		}
	}

	/** The version whose Auto Blocker note was shown in this window (shown once per update). */
	private String abSeen;

	/** v3.50: may the app install packages (Android 8+: "Install unknown apps" for this app)? */
	private boolean canInstall() {
		if (android.os.Build.VERSION.SDK_INT < 26) return true;
		try {
			Object ok = a.getPackageManager().getClass().getMethod("canRequestPackageInstalls").invoke(a.getPackageManager());
			return !(ok instanceof Boolean) || (Boolean) ok;
		} catch (Exception e) {
			return true;
		}
	}

	/** The phone's "Install unknown apps" page for this app. */
	private void openInstallSetting() {
		Intent i = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", android.net.Uri.parse("package:" + a.getPackageName()));
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			a.startActivity(i);
		} catch (Exception e) {
			open(a, new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS), "securitate");
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

	/** Opens the system installer on the downloaded APK; the window closes, the installer takes over. */
	private void install(String t) {
		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setDataAndType(ApkProvider.uri(), "application/vnd.android.package-archive");
		i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			a.startActivity(i);
			Log.i(a, "UPDATE", "programul de instalare al telefonului a fost deschis");
			sheet.hide();
		} catch (Exception e) {
			Log.e(a, "UPDATE", "programul de instalare nu a putut fi deschis: " + e);
			sheet.show(t, Tr.t("The installer could not be opened.", "Instalarea nu a putut fi pornită."), null, null, Tr.t("Close", "Închide"));
		}
	}
}
