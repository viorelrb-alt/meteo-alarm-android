package ro.meteoalarm.android.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON reader (objects, arrays, strings, numbers, booleans, null), enough for Open-Meteo. */
public final class Json {
	private final String s;
	private int i;

	private Json(String s) { this.s = s; }

	public static Object parse(String s) {
		Json j = new Json(s);
		j.ws();
		return j.value();
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> obj(Object o, String key) {
		Object v = o instanceof Map ? ((Map<String, Object>) o).get(key) : null;
		return v instanceof Map ? (Map<String, Object>) v : null;
	}

	@SuppressWarnings("unchecked")
	public static List<Object> arr(Map<String, Object> o, String key) {
		Object v = o == null ? null : o.get(key);
		return v instanceof List ? (List<Object>) v : new ArrayList<Object>();
	}

	/** Number at KEY, or NaN when missing or null. */
	public static double num(Map<String, Object> o, String key) {
		Object v = o == null ? null : o.get(key);
		return v instanceof Double ? (Double) v : Double.NaN;
	}

	public static double num(List<Object> a, int k) {
		Object v = k < a.size() ? a.get(k) : null;
		return v instanceof Double ? (Double) v : Double.NaN;
	}

	public static String str(List<Object> a, int k) {
		Object v = k < a.size() ? a.get(k) : null;
		return v instanceof String ? (String) v : null;
	}

	private void ws() {
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
	}

	private Object value() {
		char c = s.charAt(i);
		if (c == '{') {
			i++;
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			ws();
			if (s.charAt(i) == '}') { i++; return m; }
			while (true) {
				ws();
				String k = string();
				ws();
				i++; // ':'
				ws();
				m.put(k, value());
				ws();
				if (s.charAt(i++) == '}') return m;
			}
		}
		if (c == '[') {
			i++;
			List<Object> a = new ArrayList<Object>();
			ws();
			if (s.charAt(i) == ']') { i++; return a; }
			while (true) {
				ws();
				a.add(value());
				ws();
				if (s.charAt(i++) == ']') return a;
			}
		}
		if (c == '"') return string();
		if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
		if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
		if (s.startsWith("null", i)) { i += 4; return null; }
		int st = i;
		while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
		return Double.valueOf(s.substring(st, i));
	}

	private String string() {
		StringBuilder b = new StringBuilder();
		i++; // opening quote
		while (true) {
			char c = s.charAt(i++);
			if (c == '"') return b.toString();
			if (c == '\\') {
				char e = s.charAt(i++);
				switch (e) {
				case 'n': b.append('\n'); break;
				case 't': b.append('\t'); break;
				case 'r': b.append('\r'); break;
				case 'b': b.append('\b'); break;
				case 'f': b.append('\f'); break;
				case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
				default: b.append(e);
				}
			} else {
				b.append(c);
			}
		}
	}
}
