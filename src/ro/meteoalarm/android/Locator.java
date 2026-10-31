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
		void found(double lat, double lon, String town, String county, String district);

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

	Locator(Context ctx) {
		this.ctx = ctx.getApplicationContext();
	}

	void locate(final Result r) {
		if (busy) return;
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
				String town = null, county = null, district = null;
				// OpenStreetMap first: it names the sector or neighbourhood ("București, Sector 3").
				try {
					String u = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=14&addressdetails=1&accept-language="
							+ URLEncoder.encode("ro", "UTF-8") + "&lat=" + lat + "&lon=" + lon;
					Map<String, Object> ad = Json.obj(Json.parse(Weather.get(u)), "address");
					if (ad != null) {
						String city = (String) ad.get("city");
						town = first(city, (String) ad.get("town"), (String) ad.get("village"), (String) ad.get("municipality"));
						county = first((String) ad.get("county"), (String) ad.get("state"));
						if (city != null) district = first((String) ad.get("city_district"), (String) ad.get("suburb"), (String) ad.get("borough"));
						if (district != null && town != null && district.equalsIgnoreCase(town)) district = null;
					}
				} catch (Exception e) {
					town = null;
				}
				if (town == null) {
					try {
						if (Geocoder.isPresent()) {
							@SuppressWarnings("deprecation")
							List<Address> as = new Geocoder(ctx, new Locale("ro", "RO")).getFromLocation(lat, lon, 1);
							if (as != null && !as.isEmpty()) {
								Address a = as.get(0);
								town = first(a.getLocality(), a.getSubAdminArea(), a.getFeatureName());
								county = a.getAdminArea();
								if (a.getLocality() != null) district = first(a.getSubLocality());
							}
						}
					} catch (Exception e) {
						town = null;
					}
				}
				final String t = town, c = county, d = district;
				handler.post(new Runnable() {
					public void run() {
						busy = false;
						if (t == null) r.failed("noplace");
						else r.found(lat, lon, t, c == null ? "" : c, d);
					}
				});
			}
		}).start();
	}

	/**
	 * The boundary of TOWN (and DISTRICT in it) in COUNTY from OpenStreetMap, checked to hold
	 * LAT LON; null when OpenStreetMap has no boundary for it. Blocking: call it off the main thread.
	 */
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

	private static String first(String... v) {
		for (String s : v) if (s != null && s.trim().length() > 0) return s.trim();
		return null;
	}
}
