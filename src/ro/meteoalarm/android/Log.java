package ro.meteoalarm.android;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The app's diary on the phone (Viorel 2026-11-02): everything it does, one file a day for 5 days
 * (files/log/2026-11-02.txt); each day the oldest goes, and its errors move to files/log/erori.txt,
 * kept 10 days. A line: "14:20:03.512 I DATE  forecast OK 41 KB" (I = information, W = warning, E = error).
 * Exported as one text file named after the phone, to read on the phone or share.
 */
final class Log {
	static final int DAYS = 5, ERROR_DAYS = 10;
	private static Context app;
	private static String today = "";
	private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.US),
			TIME = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

	static void init(Context ctx) {
		if (app == null && ctx != null) app = ctx.getApplicationContext();
	}

	static void i(Context ctx, String cat, String msg) {
		write(ctx, 'I', cat, msg);
	}

	/** A warning: something went wrong but the app got around it (W). */
	static void w(Context ctx, String cat, String msg) {
		write(ctx, 'W', cat, msg);
	}

	static void e(Context ctx, String cat, String msg) {
		write(ctx, 'E', cat, msg);
	}

	static void e(Context ctx, String cat, String msg, Throwable t) {
		write(ctx, 'E', cat, msg + (t == null ? "" : ": " + t.getClass().getSimpleName() + (t.getMessage() != null ? " " + t.getMessage() : "")));
	}

	private static File dir(Context c) {
		File d = new File(c.getFilesDir(), "log");
		d.mkdirs();
		return d;
	}

	private static synchronized void write(Context ctx, char lvl, String cat, String msg) {
		init(ctx);
		if (app == null) return;
		try {
			Date now = new Date();
			String day = DAY.format(now);
			if (!day.equals(today)) {
				today = day;
				rotate(app, day);
			}
			StringBuilder b = new StringBuilder(64 + msg.length());
			b.append(TIME.format(now)).append(' ').append(lvl).append(' ');
			b.append(cat);
			for (int k = cat.length(); k < 7; k++) b.append(' ');
			b.append(' ');
			b.append(msg.replace('\n', ' ')).append('\n');
			append(new File(dir(app), day + ".txt"), b.toString());
		} catch (Throwable t) {
			// the diary must never stop the app
		}
	}

	private static void append(File f, String s) throws Exception {
		Writer w = new OutputStreamWriter(new FileOutputStream(f, true), "UTF-8");
		w.write(s);
		w.close();
	}

	private static String read(File f) {
		try {
			return Fetcher.readAll(new FileInputStream(f));
		} catch (Exception e) {
			return "";
		}
	}

	private static String dayBefore(String day, int n) {
		try {
			Calendar c = Calendar.getInstance();
			c.setTime(DAY.parse(day));
			c.add(Calendar.DAY_OF_MONTH, -n);
			return DAY.format(c.getTime());
		} catch (Exception e) {
			return "";
		}
	}

	/** Keeps the last 5 days; the errors of an older day go to erori.txt, which keeps 10 days. */
	private static void rotate(Context c, String day) {
		File d = dir(c);
		String first = dayBefore(day, DAYS - 1), firstErr = dayBefore(day, ERROR_DAYS - 1);
		String[] names = d.list();
		if (names == null) return;
		Arrays.sort(names);
		StringBuilder moved = new StringBuilder();
		for (String n : names) {
			if (!n.matches("\\d{4}-\\d{2}-\\d{2}\\.txt")) continue;
			String nd = n.substring(0, 10);
			if (nd.compareTo(first) >= 0) continue;
			File f = new File(d, n);
			for (String line : read(f).split("\n"))
				if (line.length() > 13 && line.charAt(13) == 'E') moved.append(nd).append(' ').append(line).append('\n');
			f.delete();
		}
		File err = new File(d, "erori.txt");
		StringBuilder keep = new StringBuilder();
		for (String line : read(err).split("\n"))
			if (line.length() >= 10 && line.substring(0, 10).compareTo(firstErr) >= 0) keep.append(line).append('\n');
		keep.append(moved);
		try {
			FileOutputStream out = new FileOutputStream(err);
			out.write(keep.toString().getBytes("UTF-8"));
			out.close();
		} catch (Exception e) {
			// keep going
		}
	}

	/** "Samsung SM-A546B (Galaxy A54 …)": the phone, for the file's name and its header. */
	static String device() {
		return (Build.MANUFACTURER + " " + Build.MODEL).trim();
	}

	/** One UI version from Samsung's build property (e.g. 90000 = 9.0), empty elsewhere. */
	private static String oneUi() {
		try {
			java.lang.reflect.Field f = Build.VERSION.class.getDeclaredField("SEM_PLATFORM_INT");
			int v = f.getInt(null) - 90000;
			if (v > 0) return " · One UI " + (v / 10000) + "." + (v % 10000) / 100;
		} catch (Throwable t) {
			// not a Samsung phone
		}
		return "";
	}

	static String header(Context c) {
		String ver = MainActivity.VERSION;
		return "Meteo Alarm — jurnal / log\n"
				+ "Dispozitiv: " + device() + " (" + Build.DEVICE + ", " + Build.PRODUCT + ")\n"
				+ "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")" + oneUi() + " · build " + Build.DISPLAY + "\n"
				+ "Aplicația: " + ver + "\n"
				+ "Ecran: " + c.getResources().getDisplayMetrics().widthPixels + "x" + c.getResources().getDisplayMetrics().heightPixels
				+ " px, densitate " + c.getResources().getDisplayMetrics().density + "\n"
				+ "Exportat: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "\n";
	}

	/**
	 * v2.141 (Viorel): errors fixed in a version: a piece of the line and that version. Such an
	 * error, written by an older version, goes to the section ERORI REZOLVATE and is not counted.
	 */
	private static final String[][] FIXED = { { "lista NOA a eșuat: eroare server HTTP 400", "2.112" }, { "cutremure NOA a eșuat: eroare server HTTP 400", "2.112" } };
	static final String RESOLVED = "===== ERORI REZOLVATE (nu sunt numărate) =====";

	/** A version as a number: 2.112 -> 2112; the old numbering (v5.31, 2 decimals) comes before 2.000. */
	private static int verKey(String v) {
		try {
			int d = v.indexOf('.');
			int major = Integer.parseInt(v.substring(0, d));
			String f = v.substring(d + 1);
			return f.length() >= 3 ? major * 1000 + Integer.parseInt(f.substring(0, 3)) : 1000 + major * 100 + Integer.parseInt(f);
		} catch (Exception e) {
			return 0;
		}
	}

	/** The version that fixed this error line, or null; VER: the app's version when it was written (null: not known, older). */
	private static String fixedIn(String line, String ver) {
		for (String[] f : FIXED)
			if (line.contains(f[0]) && (ver == null || verKey(ver) < verKey(f[1]))) return f[1];
		return null;
	}

	/** The level of a line (I, W, E) or a space: "HH:mm:ss.SSS L" or "yyyy-MM-dd HH:mm:ss.SSS L". */
	static char level(String l) {
		if (l.length() > 14 && l.charAt(2) == ':' && l.charAt(12) == ' ' && l.charAt(14) == ' ') return l.charAt(13);
		if (l.length() > 25 && l.charAt(4) == '-' && l.charAt(23) == ' ' && l.charAt(25) == ' ') return l.charAt(24);
		return ' ';
	}

	/** v2.141 (Viorel): an error of the network (no answer in time, no internet): a warning, not an error of the app. */
	static boolean network(String l) {
		return l.contains("nu a răspuns la timp") || l.contains("fără internet") || l.contains("fără conexiune") || l.contains("eroare rețea:");
	}

	/** {lines, errors, warnings} of an export: errors = the real ones not yet fixed; the fixed ones are not counted. */
	static int[] tally(String text) {
		int[] n = new int[3];
		boolean fixed = false;
		for (String l : text.split("\n")) {
			if (l.startsWith("=====")) {
				fixed = l.equals(RESOLVED);
				continue;
			}
			char v = level(l);
			if (v == ' ') continue;
			n[0]++;
			if (fixed) continue;
			if (v == 'W' || v == 'E' && network(l)) n[2]++;
			else if (v == 'E') n[1]++;
		}
		return n;
	}

	/** The whole diary in one text: the header, the errors section (10 days), the 5 days, then the fixed errors. */
	static synchronized String export(Context c) {
		init(c);
		StringBuilder b = new StringBuilder(header(c)), fixed = new StringBuilder();
		File d = dir(c);
		StringBuilder old = new StringBuilder();
		for (String l : read(new File(d, "erori.txt")).split("\n")) {
			if (l.length() == 0) continue;
			String f = fixedIn(l, null);
			if (f != null) fixed.append(l).append("  → rezolvată în v").append(f).append('\n');
			else old.append(l).append('\n');
		}
		b.append("\n===== ERORI (zilele mai vechi, păstrate 10 zile) =====\n").append(old.length() > 0 ? old : "(niciuna)\n");
		String[] names = d.list();
		List<String> days = new ArrayList<String>();
		if (names != null) for (String n : names) if (n.matches("\\d{4}-\\d{2}-\\d{2}\\.txt")) days.add(n);
		java.util.Collections.sort(days);
		java.util.regex.Pattern vp = java.util.regex.Pattern.compile("pornită Meteo Alarm Android v(\\d+\\.\\d+)");
		String ver = null;
		for (String n : days) {
			String day = n.substring(0, 10);
			b.append("\n===== ").append(day).append(" =====\n");
			for (String l : read(new File(d, n)).split("\n")) {
				if (l.length() == 0) continue;
				java.util.regex.Matcher m = vp.matcher(l);
				if (m.find()) ver = m.group(1);
				String f = level(l) == 'E' ? fixedIn(l, ver) : null;
				if (f != null) fixed.append(day).append(' ').append(l).append("  → rezolvată în v").append(f).append('\n');
				else b.append(l).append('\n');
			}
		}
		if (fixed.length() > 0) b.append('\n').append(RESOLVED).append('\n').append(fixed);
		return b.toString();
	}

	/** The export written to the cache for sharing: its file. */
	static File exportFile(Context c) {
		File dir = new File(c.getCacheDir(), "log");
		dir.mkdirs();
		for (File o : dir.listFiles()) o.delete();
		File f = new File(dir, exportName());
		try {
			FileOutputStream out = new FileOutputStream(f);
			out.write(export(c).getBytes("UTF-8"));
			out.close();
		} catch (Exception e) {
			// nothing to share
		}
		return f;
	}

	static String exportName() {
		return "MeteoAlarm-jurnal-" + device().replaceAll("[^A-Za-z0-9]+", "-") + "-" + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date()) + ".txt";
	}
}
