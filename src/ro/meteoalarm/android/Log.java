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

	/** The whole diary in one text: the header, the errors section (10 days), then the 5 days. */
	static synchronized String export(Context c) {
		init(c);
		StringBuilder b = new StringBuilder(header(c));
		File d = dir(c);
		String err = read(new File(d, "erori.txt"));
		b.append("\n===== ERORI (zilele mai vechi, păstrate 10 zile) =====\n").append(err.length() > 0 ? err : "(niciuna)\n");
		String[] names = d.list();
		List<String> days = new ArrayList<String>();
		if (names != null) for (String n : names) if (n.matches("\\d{4}-\\d{2}-\\d{2}\\.txt")) days.add(n);
		java.util.Collections.sort(days);
		for (String n : days) b.append("\n===== ").append(n.substring(0, 10)).append(" =====\n").append(read(new File(d, n)));
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
