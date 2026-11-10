package ro.meteoalarm.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ro.meteoalarm.android.core.Area;
import ro.meteoalarm.android.core.Json;
import ro.meteoalarm.android.core.Weather;

/**
 * The phone's position and the town there: a fresh fix from the location providers (or the last
 * known one), then the town and county from the phone's geocoder, or from OpenStreetMap
 * (Nominatim) when the geocoder has no answer. No Google Play services needed.
 */
final class Locator {
	interface Result {
		/** DISTRICT: the sector or neighbourhood of a city (null for a town or village). */
		/**
		 * COUNTRY: lower-case code ("ro", "bg"); MATCH: abroad, the local names of the region and
		 * town for the warnings, separated by "/".
		 */
		void found(double lat, double lon, String town, String county, String district, String country, String match);

		/** WHY: "off" (location switched off), "nofix" or "noplace". */
		void failed(String why);
	}

	private static final long FRESH_MS = 2 * 60000L, TIMEOUT_MS = 30000L;

	static boolean allowed(Context c) {
		return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
				|| c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	private final Context ctx;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private boolean busy;
	/** The neighbourhood from OpenStreetMap (true) or from the phone's geocoder (false). */
	volatile boolean osm;

	Locator(Context ctx) {
		this.ctx = ctx.getApplicationContext();
	}

	void locate(final Result r) {
		if (busy) {
			r.failed("busy");
			return;
		}
		final LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
		List<String> providers = lm.getProviders(true);
		providers.remove(LocationManager.PASSIVE_PROVIDER);
		if (providers.isEmpty()) {
			r.failed("off");
			return;
		}
		Location last = null;
		for (String p : lm.getAllProviders()) {
			try {
				Location l = lm.getLastKnownLocation(p);
				if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
			} catch (SecurityException e) {
				// this provider needs the precise location
			}
		}
		if (last != null && System.currentTimeMillis() - last.getTime() < FRESH_MS) {
			place(last, r);
			return;
		}
		// A fresh fix: the fused provider (Android 12+), else the network, else GPS.
		String provider = providers.contains("fused") ? "fused"
				: providers.contains(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : providers.get(0);
		busy = true;
		final Location fallback = last;
		final LocationListener listener = new LocationListener() {
			public void onLocationChanged(Location l) {
				handler.removeCallbacksAndMessages(null);
				lm.removeUpdates(this);
				busy = false;
				place(l, r);
			}

			public void onStatusChanged(String p, int status, Bundle extras) {}

			public void onProviderEnabled(String p) {}

			public void onProviderDisabled(String p) {}
		};
		try {
			lm.requestSingleUpdate(provider, listener, Looper.getMainLooper());
		} catch (Exception e) {
			busy = false;
			if (fallback != null) place(fallback, r);
			else r.failed("nofix");
			return;
		}
		handler.postDelayed(new Runnable() {
			public void run() {
				lm.removeUpdates(listener);
				busy = false;
				if (fallback != null) place(fallback, r);
				else r.failed("nofix");
			}
		}, TIMEOUT_MS);
	}

	/** Town and county of a fix, looked up off the main thread. */
	private void place(final Location l, final Result r) {
		busy = true;
		new Thread(new Runnable() {
			public void run() {
				final double lat = l.getLatitude(), lon = l.getLongitude();
				String town = null, county = null, district = null, country = null, match = "", state = null;
				boolean offline = false;
				// OpenStreetMap first: it names the sector or neighbourhood ("București, Sector 3").
				try {
					String u = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=16&addressdetails=1&accept-language="
							+ URLEncoder.encode("ro", "UTF-8") + "&lat=" + lat + "&lon=" + lon;
					Map<String, Object> ad = Json.obj(Json.parse(Weather.get(u)), "address");
					if (ad != null) {
						String city = (String) ad.get("city");
						town = first(city, (String) ad.get("town"), (String) ad.get("village"), (String) ad.get("municipality"));
						county = first((String) ad.get("county"), (String) ad.get("state"));
						// A Bucharest sector from OpenStreetMap (it holds the point); a neighbourhood comes below.
						String cd = (String) ad.get("city_district");
						district = cd != null && cd.startsWith("Sector") ? cd : null;
						if (district != null && town != null && district.equalsIgnoreCase(town)) district = null;
						country = (String) ad.get("country_code");
						state = (String) ad.get("state");
					}
				} catch (Exception e) {
					town = null;
				}
				boolean sector = district != null;
				// The phone's geocoder (Google's data on most phones): asked for the town when OpenStreetMap
				// could not be reached, and for the neighbourhood when the phone is the chosen source.
				try {
					if (Geocoder.isPresent() && (town == null || (!osm && !sector))) {
						@SuppressWarnings("deprecation")
						List<Address> as = new Geocoder(ctx, new Locale("ro", "RO")).getFromLocation(lat, lon, 10);
						if (as != null && !as.isEmpty()) {
							Address a = as.get(0);
							if (town == null) {
								town = first(a.getLocality(), a.getSubAdminArea(), a.getFeatureName());
								county = a.getAdminArea();
								country = a.getCountryCode();
							}
							// The neighbourhood: a result's sub-locality, or a result that is an area (no street)
							// named other than the town, county or country, e.g. "Zona 10 Nivele". Both are areas
							// the point lies in, not the nearest one.
							String sub = null;
							if (!osm && !sector) {
								for (Address x : as) if (sub == null) sub = first(x.getSubLocality());
								if (sub == null) for (Address x : as) {
									String f = first(x.getFeatureName());
									if (f == null || x.getThoroughfare() != null || x.getSubThoroughfare() != null || f.matches("[0-9]+[A-Za-z]?([/-][0-9A-Za-z]+)?") || f.equals(x.getPostalCode())) continue;
									if (f.equalsIgnoreCase(x.getLocality()) || f.equalsIgnoreCase(x.getAdminArea()) || f.equalsIgnoreCase(x.getSubAdminArea())
											|| f.equalsIgnoreCase(x.getCountryName()) || (town != null && f.equalsIgnoreCase(town))) continue;
									if (x.getLocality() == null && x.getSubAdminArea() == null && x.getAdminArea() == null) continue;
									sub = f;
									break;
								}
							}
							if (sub != null && town != null && !sub.equalsIgnoreCase(town)) district = sub;
						}
					}
				} catch (Exception e) {
					// keep what OpenStreetMap gave
				}
				// OpenStreetMap as the source: only a named area that contains the phone.
				if (osm && !sector && town != null) {
					String in = containingArea(lat, lon);
					if (in != null && !in.equalsIgnoreCase(town)) district = in;
				}
				// v4.20: no map service answered (no internet): the nearest locality of Romania, villages
				// too, when the phone is within 4 km of it.
				if (town == null) {
					ro.meteoalarm.android.core.Cities.City v = Places.at(ctx, lat, lon, 4);
					if (v != null) {
						town = v.name;
						county = v.county;
						country = v.country;
						offline = true;
						Log.i(ctx, "LOC", "fără serviciu de hartă: localitatea din lista aplicației, " + v.name + ", " + v.county);
					}
				}
				if (country == null) country = "ro";
				country = country.toLowerCase();
				// United States (v2.30): the state stands where a Romanian town has its county.
				if (country.equals("us") && state != null && state.trim().length() > 0) county = state.trim();
				// Abroad, the warnings name the areas in the local language: ask again without a language.
				if (town != null && !country.equals("ro")) {
					try {
						Thread.sleep(1100); // OpenStreetMap asks for at most one request a second
						String u = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&addressdetails=1&lat=" + lat + "&lon=" + lon;
						Map<String, Object> ad = Json.obj(Json.parse(Weather.get(u)), "address");
						StringBuilder b = new StringBuilder();
						if (ad != null) for (String k : new String[] { "state", "state_district", "county", "city", "town", "municipality", "village" }) {
							Object v = ad.get(k);
							if (v instanceof String && ((String) v).trim().length() > 0) b.append(b.length() > 0 ? "/" : "").append(((String) v).trim());
						}
						match = b.toString();
					} catch (Exception e) {
						match = "";
					}
				}
				final String t = town, c = county, d = district, cc = country, mt = match;
				final boolean off = offline;
				handler.post(new Runnable() {
					public void run() {
						busy = false;
						lastOffline = off;
						if (t == null) r.failed("noplace");
						else r.found(lat, lon, t, c == null ? "" : c, d, cc, mt);
					}
				});
			}
		}).start();
	}

	/**
	 * The boundary of TOWN (and DISTRICT in it) in COUNTY from OpenStreetMap, checked to hold
	 * LAT LON; null when OpenStreetMap has no boundary for it. Blocking: call it off the main thread.
	 */
	/** v4.40: the last place came from the app's own lists (no map service answered). */
	volatile boolean lastOffline;

	static Area boundary(String town, String district, String county, double lat, double lon) {
		try {
			String q = (district != null ? district + ", " : "") + town + (county.length() > 0 && !county.equals(town) ? ", " + county : "");
			String u = "https://nominatim.openstreetmap.org/search?format=jsonv2&polygon_geojson=1&polygon_threshold=0.0003&limit=5"
					+ "&accept-language=ro&q=" + URLEncoder.encode(q, "UTF-8");
			Object res = Json.parse(Weather.get(u));
			if (!(res instanceof List)) return null;
			for (Object o : (List<?>) res) {
				if (!(o instanceof Map)) continue;
				@SuppressWarnings("unchecked")
				Map<String, Object> m = (Map<String, Object>) o;
				Object at = m.get("addresstype");
				if ("county".equals(at) || "state".equals(at) || "country".equals(at) || "region".equals(at)) continue;
				Area a = Area.fromGeoJson(Json.obj(m, "geojson"));
				// The right place holds the phone (a wrong match from the search is dropped).
				if (a != null && a.holds(lat, lon, 2000)) return a;
			}
		} catch (Exception e) {
			// no boundary: the town then changes as soon as the name does
		}
		return null;
	}

	/**
	 * The name of the smallest named area OpenStreetMap draws around the point (a neighbourhood,
	 * then a quarter, a named housing area, a suburb), from the Overpass service; null when the
	 * point is in none of them or the service cannot be reached.
	 */
	static String containingArea(double lat, double lon) {
		try {
			String q = "[out:json][timeout:10];is_in(" + lat + "," + lon + ")->.a;(area.a[place~\"^(neighbourhood|quarter|suburb|city_block)$\"][name];"
					+ "area.a[landuse=residential][name];);out tags;";
			Object res = Json.parse(Weather.get("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8")));
			List<Object> els = res instanceof Map ? Json.arr(castMap(res), "elements") : null;
			if (els == null) return null;
			String[] order = { "city_block", "neighbourhood", "quarter", "residential", "suburb" };
			String best = null;
			int bestR = order.length;
			for (Object o : els) {
				if (!(o instanceof Map)) continue;
				Map<String, Object> tags = Json.obj(castMap(o), "tags");
				if (tags == null) continue;
				Object nm = tags.get("name:ro") instanceof String ? tags.get("name:ro") : tags.get("name");
				if (!(nm instanceof String)) continue;
				Object kind = tags.get("place") instanceof String ? tags.get("place") : tags.get("landuse");
				int r = java.util.Arrays.asList(order).indexOf(kind);
				if (r >= 0 && r < bestR) {
					bestR = r;
					best = ((String) nm).trim();
				}
			}
			return best;
		} catch (Exception e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> castMap(Object o) {
		return (Map<String, Object>) o;
	}

	private static String first(String... v) {
		for (String s : v) if (s != null && s.trim().length() > 0) return s.trim();
		return null;
	}
}
