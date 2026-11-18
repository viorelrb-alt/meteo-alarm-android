package ro.meteoalarm.android.core;

import java.util.Map;

/** The newest published version, from the GitHub Releases of the app's repository. */
public final class Release {
	public static final String REPO = "viorelrb-alt/meteo-alarm-android";
	public static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";

	public String version = "", apkUrl = "", notes = "", page = "";
	/**
	 * v1.520 (Viorel, new numbering): the tag starts with the app's build number (versionCode),
	 * e.g. v118.1.520, so the apps of the old numbering (6.10) see it as newer; -1 for old tags.
	 */
	public int code = -1;
	/** The tag as published (the build number and the version), for links. */
	public String tag = "";

	/** Splits a "118.1.520" version into the build number and "1.520". */
	private void split() {
		tag = version;
		String[] p = version.split("\\.");
		if (p.length >= 3 && p[0].length() >= 3) {
			try {
				code = Integer.parseInt(p[0]);
				version = version.substring(p[0].length() + 1);
			} catch (NumberFormatException e) {
				code = -1;
			}
		}
	}

	/** v1.520: a version in the new numbering: X.YZ (old, X >= 1) becomes 1.(X-1)YZ. */
	public static String display(String v) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("([1-9])\\.(\\d\\d)").matcher(v);
		if (m.matches()) return "1." + (Integer.parseInt(m.group(1)) - 1) + m.group(2);
		return v;
	}
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
		r.split();
		if (src == 1) {
			if (!apk.matches("[A-Za-z0-9._-]+\\.apk")) throw new IllegalArgumentException("source 1: no apk");
			r.apkUrl = RAW + apk;
		} else {
			if (!apk.matches("[A-Za-z0-9_-]{10,}")) throw new IllegalArgumentException("drive: no apk");
			r.apkUrl = "https://drive.usercontent.google.com/download?id=" + apk + "&export=download&confirm=t";
		}
		return r;
	}

	/** v2.117 (Viorel): the update window shows only the short description of the version in the app's
	 *  language: the "## Română" part in Romanian, the "## English" part otherwise; "- " items become "• ".
	 *  Notes in an older layout give "" (the window then shows no description, never the whole text). */
	public static String summary(String notes, boolean ro) {
		if (notes == null) return "";
		String want = ro ? "## Română" : "## English";
		StringBuilder b = new StringBuilder();
		boolean in = false;
		for (String l : notes.replace("\r", "").split("\n")) {
			String t = l.trim();
			if (t.startsWith("## ")) { in = t.equals(want); continue; }
			if (!in || t.length() == 0) continue;
			if (t.startsWith("#")) break;
			if (b.length() > 0) b.append('\n');
			b.append(t.startsWith("- ") ? "• " + t.substring(2) : t);
		}
		return b.toString();
	}

	/** The release JSON of the GitHub API: tag "v0.14", the first .apk asset, the notes. */
	@SuppressWarnings("unchecked")
	public static Release parse(String json) {
		Map<String, Object> o = (Map<String, Object>) Json.parse(json);
		Release r = new Release();
		Object tag = o.get("tag_name");
		r.version = tag instanceof String ? ((String) tag).replaceFirst("^[vV]", "") : "";
		r.split();
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
		r.split();
		r.apkUrl = "https://github.com/" + REPO + "/releases/download/v" + r.tag + "/MeteoAlarmAndroid-v" + r.version + ".apk";
		r.page = "https://github.com/" + REPO + "/releases/tag/v" + r.tag;
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

	/** v1.520: by the build numbers when the tag has one, else by the versions (new numbering). */
	public boolean newerThan(String installed, int installedCode) {
		if (version.length() == 0 || apkUrl.length() == 0) return false;
		if (code > 0 && installedCode > 0) return code > installedCode;
		return compare(display(installed), display(version)) < 0;
	}

}
