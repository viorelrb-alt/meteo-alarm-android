package ro.meteoalarm.android.core;

import java.util.Map;

/** The newest published version, from the GitHub Releases of the app's repository. */
public final class Release {
	public static final String REPO = "viorelrb-alt/meteo-alarm-android";
	public static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";

	public String version = "", apkUrl = "", notes = "", page = "";
	public long size = -1;

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
