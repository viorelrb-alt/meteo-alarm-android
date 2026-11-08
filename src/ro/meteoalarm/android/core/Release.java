package ro.meteoalarm.android.core;

import java.util.Map;

/** The newest published version, from the GitHub Releases of the app's repository. */
public final class Release {
	public static final String REPO = "viorelrb-alt/meteo-alarm-android";
	public static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";

	public String version = "", apkUrl = "", notes = "", page = "";
	public long size = -1;
	/** v3.03: where it was found: 0 GitHub Releases, 1 the second GitHub server, 2 Google Drive. */
	public int src;

	/**
	 * v3.03 (Viorel 2026-11-08): the second GitHub server (raw.githubusercontent.com) holds
	 * dist/latest.txt and the APK in dist/ of the main branch.
	 */
	public static final String RAW = "https://raw.githubusercontent.com/" + REPO + "/main/dist/";
	public static final String RAW_LATEST = RAW + "latest.txt";

	/**
	 * v3.03: the copy on Google Drive, folder "Meteo Alarm Android - actualizari": the document
	 * "versiune" (shared with anyone who has the link). Its link is never shown to the user.
	 */
	public static final String DRIVE_DOC = "https://docs.google.com/document/d/1_qVjT-WcfTu7k_FomGoI42kVFk6WxCAoxE2E7hX-sj8/export?format=txt";

	/** The "versiune=" / "apk=" / "marime=" text of source SRC (1 or 2). */
	public static Release fromText(String text, int src) {
		Release r = new Release();
		r.src = src;
		String apk = "";
		for (String line : text.replace("\uFEFF", "").split("\n")) {
			int eq = line.indexOf('=');
			if (eq <= 0) continue;
			String k = line.substring(0, eq).trim().toLowerCase(), v = line.substring(eq + 1).trim();
			if (k.equals("versiune") || k.equals("version")) r.version = v.replaceFirst("^[vV]", "");
			else if (k.equals("apk")) apk = v;
			else if (k.equals("marime") || k.equals("size")) {
				try {
					r.size = Long.parseLong(v);
				} catch (NumberFormatException e) {
					r.size = -1;
				}
			}
		}
		if (!r.version.matches("[0-9]+(\\.[0-9]+)*")) throw new IllegalArgumentException("source " + src + ": no version");
		if (src == 1) {
			if (!apk.matches("[A-Za-z0-9._-]+\\.apk")) throw new IllegalArgumentException("source 1: no apk");
			r.apkUrl = RAW + apk;
		} else {
			if (!apk.matches("[A-Za-z0-9_-]{10,}")) throw new IllegalArgumentException("drive: no apk");
			r.apkUrl = "https://drive.usercontent.google.com/download?id=" + apk + "&export=download&confirm=t";
		}
		return r;
	}

	/** The release JSON of the GitHub API: tag "v0.14", the first .apk asset, the notes. */
	@SuppressWarnings("unchecked")
	public static Release parse(String json) {
		Map<String, Object> o = (Map<String, Object>) Json.parse(json);
		Release r = new Release();
		Object tag = o.get("tag_name");
		r.version = tag instanceof String ? ((String) tag).replaceFirst("^[vV]", "") : "";
		Object body = o.get("body"), url = o.get("html_url");
		r.notes = body instanceof String ? ((String) body).trim() : "";
		r.page = url instanceof String ? (String) url : "";
		for (Object a : Json.arr(o, "assets")) {
			if (!(a instanceof Map)) continue;
			Map<String, Object> m = (Map<String, Object>) a;
			Object name = m.get("name"), dl = m.get("browser_download_url");
			if (name instanceof String && ((String) name).toLowerCase().endsWith(".apk") && dl instanceof String) {
				r.apkUrl = (String) dl;
				double sz = Json.num(m, "size");
				r.size = Double.isNaN(sz) ? -1 : (long) sz;
				break;
			}
		}
		return r;
	}

	/** v3.02: the release of TAG ("v3.01") without the API: the APK by its usual name, no notes. */
	public static Release fromTag(String tag) {
		Release r = new Release();
		r.version = tag.trim().replaceFirst("^[vV]", "");
		if (!r.version.matches("[0-9]+(\\.[0-9]+)*")) throw new IllegalArgumentException("tag " + tag);
		r.apkUrl = "https://github.com/" + REPO + "/releases/download/v" + r.version + "/MeteoAlarmAndroid-v" + r.version + ".apk";
		r.page = "https://github.com/" + REPO + "/releases/tag/v" + r.version;
		return r;
	}

	/** Compares "0.13" and "0.14" part by part as numbers: negative when A is older. */
	public static int compare(String a, String b) {
		String[] x = a.split("[^0-9]+"), y = b.split("[^0-9]+");
		for (int k = 0; k < Math.max(x.length, y.length); k++) {
			int p = k < x.length && x[k].length() > 0 ? Integer.parseInt(x[k]) : 0;
			int q = k < y.length && y[k].length() > 0 ? Integer.parseInt(y[k]) : 0;
			if (p != q) return p < q ? -1 : 1;
		}
		return 0;
	}

	public boolean newerThan(String installed) {
		return version.length() > 0 && apkUrl.length() > 0 && compare(installed, version) < 0;
	}

}
