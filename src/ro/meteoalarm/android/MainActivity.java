package ro.meteoalarm.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import android.Manifest;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Screen;
import ro.meteoalarm.android.core.Texts;
import ro.meteoalarm.android.core.Warnings;
import ro.meteoalarm.android.core.Weather;

/**
 * Meteo Alarm Android: the Kindle weather clock on a phone. The weather is fetched every 15 minutes
 * while the app is open (every hour at night) and kept on disk, so it opens at once offline.
 */
public final class MainActivity extends Activity implements WeatherView.Taps {
	static final String VERSION = "Meteo Alarm Android v0.32";
	private static final long WEATHER_MS = 15 * 60000L, NIGHT_MS = 60 * 60000L;

	private WeatherView view;
	private Screen s;
	private Cities cities;
	private SharedPreferences prefs;
	private final Handler handler = new Handler();
	private boolean fetching, asked;
	private long lastTry, lastLocate;
	private Locator locator;
	private Updater updater;
	private BottomSheet sheet;
	private static final long UPDATE_CHECK_MS = 7 * 24 * 3600000L;
	private static final long LOCATE_MS = 60 * 60000L;

	private final Runnable tick = new Runnable() {
		public void run() {
			s.now = Calendar.getInstance();
			s.offline = s.fetched > 0 && System.currentTimeMillis() - s.fetched > 2 * interval() + 5 * 60000L;
			long iv = s.w == null ? 60000L : interval();
			if (System.currentTimeMillis() - Math.max(s.fetched, lastTry) >= iv) fetch();
			// The exact position at start and then every hour, while automatic location is on.
			if (autoLoc() && Locator.allowed(MainActivity.this) && System.currentTimeMillis() - lastLocate >= LOCATE_MS) locate(false);
			view.invalidate();
			// Next tick at the start of the next minute, for the clock.
			handler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50);
		}
	};

	private final BroadcastReceiver battery = new BroadcastReceiver() {
		public void onReceive(Context ctx, Intent i) {
			int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
			s.batt = lvl < 0 ? -1 : lvl * 100 / Math.max(1, sc);
			s.charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
			view.invalidate();
		}
	};

	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		prefs = getSharedPreferences("settings", MODE_PRIVATE);
		view = new WeatherView(this, this);
		s = view.screen;
		try {
			cities = new Cities(readAll(getAssets().open("cities.txt")));
		} catch (Exception e) {
			cities = new Cities("");
		}
		// Towns found by the location, kept on the phone.
		for (String k : prefs.getString("myCities", "").split("\n")) {
			Cities.City c = Cities.City.fromKey(k);
			if (c != null) cities.add(c);
		}
		locator = new Locator(this);
		sheet = new BottomSheet(this);
		updater = new Updater(this, sheet);
		applySettings();
		loadCache();
		android.widget.FrameLayout root = new android.widget.FrameLayout(this);
		root.addView(view);
		root.addView(sheet);
		setContentView(root);
	}

	@Override
	public void onBackPressed() {
		if (sheet.isOpen()) {
			if (sheet.isCancelable()) sheet.hide();
		} else super.onBackPressed();
	}

	/** About the app: shown once at the first start (before the location question) and from ☰ › Despre. */
	private String aboutText() {
		return s.T.en
				? "This is a test version of Meteo Alarm Android, made from Meteo Alarm Kindle.\n\n"
						+ "No data collected: no account, no ads, no statistics. To get the weather, only the place's coordinates are sent "
						+ "to Open-Meteo (weather, pollen); the warnings come from MeteoAlarm (ANM).\n\n"
						+ "Location: used to show the weather where you are, at start and once an hour. To name the town, the "
						+ "coordinates go to the phone's geocoder or to OpenStreetMap. Towns found are kept only on the phone. "
						+ "Without location access, you choose the town from the list.\n\n"
						+ "Sensors: the app reads the battery state and, with ☰ › Theme › Light sensor, the room's light (dark "
						+ "theme in the dark); a future version will use the compass (wind direction). Nothing leaves the phone.\n\n"
						+ "Updates: once a week the app checks GitHub for a new version and asks before downloading it; "
						+ "you can also check from ☰ › Check for updates.\n\n"
						+ Updater.protectNote(true)
				: "Aceasta este o versiune de test a Meteo Alarm Android, făcută din Meteo Alarm Kindle.\n\n"
						+ "Nu colectăm date: fără cont, fără reclame, fără statistici. Pentru vreme se trimit doar coordonatele "
						+ "localității către Open-Meteo (vremea, polenul); avertizările vin de la MeteoAlarm (ANM).\n\n"
						+ "Locația: o folosim ca să arătăm vremea acolo unde ești, la pornire și o dată pe oră. Pentru numele "
						+ "localității, coordonatele merg la geocoderul telefonului sau la OpenStreetMap. Localitățile găsite "
						+ "rămân doar pe telefon. Fără acces la locație, alegi localitatea din listă.\n\n"
						+ "Senzori: aplicația citește starea bateriei și, cu ☰ › Temă › După lumina din cameră, senzorul de lumină "
						+ "(tema întunecată pe întuneric); o versiune viitoare va folosi busola (direcția vântului). Nimic nu pleacă "
						+ "de pe telefon.\n\n"
						+ "Actualizări: o dată pe săptămână aplicația caută pe GitHub o versiune nouă și te întreabă înainte "
						+ "s-o descarce; poți verifica oricând din ☰ › Verifică actualizări.\n\n"
						+ Updater.protectNote(false);
	}

	private void welcome() {
		sheet.show(VERSION, aboutText(), s.T.en ? "I understand" : "Am înțeles", new Runnable() {
			public void run() {
				sheet.hide();
				prefs.edit().putBoolean("welcomed", true).putString("seenVersion", updater.installed()).apply();
				if (autoLoc() && !Locator.allowed(MainActivity.this)) askLocation();
			}
		}, null);
	}

	private long interval() {
		int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
		return h >= 23 || h < 6 ? NIGHT_MS : WEATHER_MS;
	}

	private void applySettings() {
		s.T = new Texts("en".equals(prefs.getString("lang", "ro")));
		s.unitF = prefs.getBoolean("unitF", false);
		s.clk12 = prefs.getBoolean("clk12", false);
		Cities.City c = Cities.City.fromKey(prefs.getString("city", ""));
		if (c == null) c = cities.find("Călărași");
		if (c == null) c = new Cities.City("Călărași", "Călărași", 44.2058, 27.3306);
		s.city = c;
		// Theme: "light" (Kindle), "dark" (Claude colours), "auto" (follows the phone's dark mode) or
		// "sensor" (dark when the room is dark, from the light sensor).
		String theme = prefs.getString("theme", "auto");
		boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
		s.dark = theme.equals("dark") || (theme.equals("auto") && night) || (theme.equals("sensor") && roomDark);
		listenLight(theme.equals("sensor") && resumed);
		applyBrightness();
		s.autoLoc = autoLoc();
		view.setBackgroundColor(s.dark ? 0xFF262624 : 0xFFFFFFFF);
		getWindow().getDecorView().setBackgroundColor(s.dark ? 0xFF262624 : 0xFFFFFFFF);
		if (prefs.getBoolean("keepOn", true)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if (sheet != null) sheet.setDark(s.dark);
		if (updater != null) updater.en = s.T.en;
	}

	@Override
	public void onConfigurationChanged(Configuration cfg) {
		super.onConfigurationChanged(cfg);
		applySettings();
		view.invalidate();
	}

	// --- Light sensor and brightness ------------------------------------------------------

	/** Below DARK_LUX the room counts as dark, above LIGHT_LUX as lit (in between: no change). */
	private static final float DARK_LUX = 8, LIGHT_LUX = 25;
	/** The room has to stay dark (or lit) this long before the theme follows. */
	private static final long LIGHT_HOLD_MS = 4000;
	private SensorManager sensors;
	private boolean resumed, roomDark, lightOn, lightFirst;
	private long lightSince;

	private final SensorEventListener light = new SensorEventListener() {
		public void onSensorChanged(SensorEvent e) {
			float lux = e.values[0];
			boolean want = lux < DARK_LUX ? true : lux > LIGHT_LUX ? false : roomDark;
			long now = System.currentTimeMillis();
			// The first reading counts at once (the room is as it is when the app opens).
			if (lightFirst) {
				lightFirst = false;
				lightSince = 0;
				if (want != roomDark) {
					roomDark = want;
					applySettings();
					view.invalidate();
				}
				return;
			}
			if (want == roomDark) {
				lightSince = 0;
				return;
			}
			if (lightSince == 0) lightSince = now;
			if (now - lightSince < LIGHT_HOLD_MS) return;
			lightSince = 0;
			roomDark = want;
			applySettings();
			view.invalidate();
		}

		public void onAccuracyChanged(Sensor sn, int acc) {
		}
	};

	/** Listens to the light sensor while ON (theme "sensor" and the app in front). */
	private void listenLight(boolean on) {
		if (sensors == null) sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor sn = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
		if (sn == null || on == lightOn) return;
		lightOn = on;
		lightSince = 0;
		lightFirst = true;
		if (on) sensors.registerListener(light, sn, SensorManager.SENSOR_DELAY_NORMAL);
		else sensors.unregisterListener(light);
	}

	private boolean hasLight() {
		if (sensors == null) sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
		return sensors != null && sensors.getDefaultSensor(Sensor.TYPE_LIGHT) != null;
	}

	/** The app's own brightness (0.004–1) set with the slider, or the phone's (-1). */
	private void applyBrightness() {
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.screenBrightness = prefs.getFloat("bright", -1);
		getWindow().setAttributes(lp);
	}

	/** The slider's current level: the app's own, or the phone's setting when there is none. */
	public float brightness() {
		float b = prefs.getFloat("bright", -1);
		if (b >= 0) return b;
		try {
			return Math.max(0.004f, Math.min(1, android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f));
		} catch (Exception e) {
			return 0.5f;
		}
	}

	public void setBrightness(float b, boolean save) {
		if (b < 0) {
			prefs.edit().remove("bright").apply();
			applyBrightness();
			return;
		}
		b = Math.max(0.004f, Math.min(1, b));
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.screenBrightness = b;
		getWindow().setAttributes(lp);
		if (save) prefs.edit().putFloat("bright", b).apply();
	}

	public boolean ownBrightness() {
		return prefs.getFloat("bright", -1) >= 0;
	}

	@Override
	protected void onResume() {
		super.onResume();
		resumed = true;
		listenLight("sensor".equals(prefs.getString("theme", "auto")));
		hideBars();
		registerReceiver(battery, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		handler.removeCallbacks(tick);
		handler.post(tick);
		if (!prefs.getBoolean("welcomed", false)) {
			if (!asked) {
				asked = true;
				welcome();
			}
			return;
		}
		if (autoLoc() && !Locator.allowed(this) && !asked) askLocation();
		// Just updated: what is new in this version, once (instead of the update check this time).
		String cur = updater.installed();
		if (!cur.equals(prefs.getString("seenVersion", ""))) {
			prefs.edit().putString("seenVersion", cur).apply();
			news();
			return;
		}
		// Look for a newer version once a week (the bottom window opens only when there is one).
		long now = System.currentTimeMillis();
		if (now - prefs.getLong("updateChecked", 0) >= UPDATE_CHECK_MS) {
			prefs.edit().putLong("updateChecked", now).apply();
			updater.check(false);
		}
	}

	// --- Location ----------------------------------------------------------------------

	private boolean autoLoc() {
		return prefs.getBoolean("autoLoc", true);
	}

	/** Automatic location on or off; the locate button is gray while it is off. */
	private void setAutoLoc(boolean on) {
		prefs.edit().putBoolean("autoLoc", on).apply();
		s.autoLoc = on;
		view.invalidate();
	}

	private void askLocation() {
		asked = true;
		requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, 1);
	}

	@Override
	public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
		if (Locator.allowed(this)) {
			locate(true);
		} else {
			setAutoLoc(false);
			toast(s.T.en ? "Without location access, choose the town from the list." : "Fără acces la locație, alege localitatea din listă.");
		}
	}

	private void toast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
	}

	/** Locate button: automatic location on, and look now. */
	public void onLocate() {
		setAutoLoc(true);
		if (!Locator.allowed(this)) askLocation();
		else locate(true);
	}

	private AlertDialog locDlg;

	/** The small window that says the exact position is being looked for; it closes by itself. */
	private void locatingDialog() {
		closeLocatingDialog();
		boolean en = s.T.en;
		locDlg = dialog().setTitle(en ? "Exact location" : "Localizare exactă")
				.setMessage(en ? "Looking for the phone's exact position and the town… This takes a few seconds."
						: "Caut poziția exactă a telefonului și localitatea… Durează câteva secunde.")
				.setPositiveButton("OK", null).create();
		locDlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
			public void onDismiss(DialogInterface di) {
				hideBars();
			}
		});
		locDlg.show();
	}

	private void closeLocatingDialog() {
		if (locDlg != null && locDlg.isShowing()) locDlg.dismiss();
		locDlg = null;
	}

	/**
	 * Finds the phone's town. The weather is then for the exact position; the town is added to
	 * the list on the phone when cities.txt does not have it. SAY: report failures (a tap).
	 */
	private void locate(final boolean say) {
		lastLocate = System.currentTimeMillis();
		s.locating = true;
		view.invalidate();
		if (say) locatingDialog();
		locator.locate(new Locator.Result() {
			public void found(double lat, double lon, String town, String rawCounty) {
				s.locating = false;
				closeLocatingDialog();
				String county = cities.county(rawCounty);
				if (county.length() == 0) county = town;
				Cities.City listed = cities.find(county, town);
				double la = Math.round(lat * 10000) / 10000.0, lo = Math.round(lon * 10000) / 10000.0;
				if (listed == null) {
					Cities.City add = new Cities.City(county, town, la, lo);
					cities.add(add);
					String mine = prefs.getString("myCities", "");
					prefs.edit().putString("myCities", mine.length() == 0 ? add.key() : mine + "\n" + add.key()).apply();
					if (say) toast((s.T.en ? "Added to the list: " : "Adăugată în listă: ") + add.label());
				}
				Cities.City here = new Cities.City(county, listed != null ? listed.name : town, la, lo);
				boolean moved = s.city == null || !s.city.name.equals(here.name) || !s.city.county.equals(here.county)
						|| distKm(s.city.lat, s.city.lon, la, lo) > 1;
				if (moved) setCity(here, false);
				else view.invalidate();
				if (moved || say) fetch();
			}

			public void failed(String why) {
				s.locating = false;
				closeLocatingDialog();
				view.invalidate();
				if (!say) return;
				if (why.equals("off")) toast(s.T.en ? "Location is off on the phone." : "Localizarea e oprită pe telefon.");
				else if (why.equals("nofix")) toast(s.T.en ? "The position could not be found." : "Poziția nu a putut fi găsită.");
				else toast(s.T.en ? "The town could not be found (internet?)." : "Localitatea nu a putut fi găsită (internet?).");
			}
		});
	}

	private static double distKm(double la1, double lo1, double la2, double lo2) {
		double dy = (la2 - la1) * 111.2, dx = (lo2 - lo1) * 111.2 * Math.cos(Math.toRadians(la1));
		return Math.sqrt(dx * dx + dy * dy);
	}

	@Override
	protected void onPause() {
		super.onPause();
		resumed = false;
		listenLight(false);
		handler.removeCallbacks(tick);
		try {
			unregisterReceiver(battery);
		} catch (Exception e) {
			// not registered
		}
	}

	@Override
	public void onWindowFocusChanged(boolean focus) {
		super.onWindowFocusChanged(focus);
		if (focus) hideBars();
	}

	/** Full screen like the Kindle: status and navigation bars hidden until a swipe from the edge. */
	private void hideBars() {
		view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
	}

	// --- Data --------------------------------------------------------------------------

	private static String readAll(InputStream in) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[16384];
		int n;
		while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
		in.close();
		return new String(out.toByteArray(), "UTF-8");
	}

	private String readCache(String name) {
		try {
			return readAll(new FileInputStream(new File(getFilesDir(), name)));
		} catch (Exception e) {
			return null;
		}
	}

	private void writeCache(String name, String text) {
		try {
			FileOutputStream out = new FileOutputStream(new File(getFilesDir(), name));
			out.write(text.getBytes("UTF-8"));
			out.close();
		} catch (Exception e) {
			// the cache is only a convenience
		}
	}

	/** The last download, if it was for the chosen town. */
	private void loadCache() {
		if (!s.city.key().equals(prefs.getString("cacheCity", ""))) return;
		try {
			String f = readCache("forecast.json");
			if (f != null) s.w = Weather.parse(f);
			s.fetched = prefs.getLong("fetched", 0);
			String p = readCache("pollen.json");
			if (p != null) {
				Object[] r = Weather.parsePollen(p);
				s.pollenLvl = (Integer) r[0];
				s.pollenPlant = (String) r[1];
			}
			String x = readCache("warn.xml");
			if (x != null) s.warns = Warnings.parse(x, s.city.county, System.currentTimeMillis());
		} catch (Exception e) {
			s.w = null;
		}
	}

	private void fetch() {
		if (fetching) return;
		fetching = true;
		lastTry = System.currentTimeMillis();
		s.busy = true;
		view.invalidate();
		final Cities.City c = s.city;
		new Thread(new Runnable() {
			public void run() {
				String f = null, p = null, x = null;
				try {
					f = Weather.get(Weather.forecastUrl(c.lat, c.lon, TimeZone.getDefault().getID()));
					Weather.parse(f);
				} catch (Exception e) {
					f = null;
				}
				try {
					p = Weather.get(Weather.pollenUrl(c.lat, c.lon));
				} catch (Exception e) {
					p = null;
				}
				try {
					x = Weather.get(Warnings.FEED);
				} catch (Exception e) {
					x = null;
				}
				final String ff = f, pp = p, xx = x;
				handler.post(new Runnable() {
					public void run() {
						done(c, ff, pp, xx);
					}
				});
			}
		}).start();
	}

	private void done(Cities.City c, String f, String p, String x) {
		fetching = false;
		s.busy = false;
		if (c != s.city) { // the town changed meanwhile
			fetch();
			return;
		}
		long now = System.currentTimeMillis();
		if (f != null) {
			s.w = Weather.parse(f);
			s.fetched = now;
			s.offline = false;
			writeCache("forecast.json", f);
			prefs.edit().putLong("fetched", now).putString("cacheCity", c.key()).apply();
		} else if (s.w == null) {
			s.error = s.T.en ? "No internet connection" : "Fără conexiune la internet";
		}
		if (p != null) {
			try {
				Object[] r = Weather.parsePollen(p);
				s.pollenLvl = (Integer) r[0];
				s.pollenPlant = (String) r[1];
				writeCache("pollen.json", p);
			} catch (Exception e) {
				// keep the last pollen
			}
		}
		if (x != null) {
			s.warns = Warnings.parse(x, c.county, now);
			writeCache("warn.xml", x);
		}
		view.invalidate();
	}

	// --- Taps --------------------------------------------------------------------------

	private AlertDialog.Builder dialog() {
		return new AlertDialog.Builder(this, s.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert
				: android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
	}

	private void show(AlertDialog.Builder b) {
		AlertDialog d = b.create();
		d.setOnDismissListener(new DialogInterface.OnDismissListener() {
			public void onDismiss(DialogInterface di) {
				hideBars();
			}
		});
		d.show();
	}

	public void onRefresh() {
		fetch();
	}

	public void onMenu() {
		final boolean en = s.T.en;
		boolean keepOn = prefs.getBoolean("keepOn", true), auto = autoLoc();
		// Item ids in menu order.
		final List<String> ids = new ArrayList<String>();
		List<String> items = new ArrayList<String>();
		ids.add("city"); items.add(en ? "Choose town" : "Alege localitatea");
		ids.add("unit"); items.add(s.unitF ? (en ? "Show °C" : "Afișează °C") : (en ? "Show °F" : "Afișează °F"));
		ids.add("clock"); items.add(s.clk12 ? (en ? "24-hour clock" : "Ceas 24 de ore") : (en ? "12-hour clock" : "Ceas 12 ore (AM/PM)"));
		ids.add("theme"); items.add(en ? "Theme…" : "Temă…");
		ids.add("keepOn"); items.add(keepOn ? (en ? "Let the screen sleep" : "Lasă ecranul să se stingă") : (en ? "Keep the screen on" : "Ține ecranul aprins"));
		ids.add("lang"); items.add(en ? "Limba română" : "Limba engleză");
		ids.add("loc"); items.add(auto ? (en ? "Turn location off" : "Oprire localizare") : (en ? "Turn location on" : "Pornire localizare"));
		ids.add("update"); items.add(en ? "Check for updates" : "Verifică actualizări");
		ids.add("about"); items.add(en ? "About" : "Despre");
		// The menu shows five items; the rest scroll up, the window keeps its size. "⌄ more" under
		// the list says there is more, until the last item is in view.
		final android.widget.ListView lv = new android.widget.ListView(this);
		AlertDialog.Builder b = dialog().setTitle("☰");
		android.widget.ArrayAdapter<String> ad = new android.widget.ArrayAdapter<String>(b.getContext(),
				android.R.layout.select_dialog_item, items);
		lv.setAdapter(ad);
		lv.setDivider(null);
		View row = ad.getView(0, null, lv);
		row.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		int rh = row.getMeasuredHeight();
		android.widget.LinearLayout box = new android.widget.LinearLayout(this);
		box.setOrientation(android.widget.LinearLayout.VERTICAL);
		final android.widget.TextView less = hint(b.getContext(), en ? "⌃  more  ⌃" : "⌃  mai multe  ⌃");
		final android.widget.TextView more = hint(b.getContext(), en ? "⌄  more  ⌄" : "⌄  mai multe  ⌄");
		less.setVisibility(View.INVISIBLE);
		more.setVisibility(items.size() > 5 ? View.VISIBLE : View.INVISIBLE);
		box.addView(less, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		box.addView(lv, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rh * 5));
		box.addView(more, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		lv.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
			public void onScrollStateChanged(android.widget.AbsListView v, int st) {
			}

			public void onScroll(android.widget.AbsListView v, int first, int vis, int total) {
				View top = v.getChildAt(0), last = v.getChildAt(v.getChildCount() - 1);
				boolean start = first == 0 && (top == null || top.getTop() >= 0);
				boolean end = first + vis >= total && last != null && last.getBottom() <= v.getHeight();
				less.setVisibility(start ? View.INVISIBLE : View.VISIBLE);
				more.setVisibility(end ? View.INVISIBLE : View.VISIBLE);
			}
		});
		less.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				lv.smoothScrollToPosition(0);
			}
		});
		more.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				lv.smoothScrollToPosition(ids.size() - 1);
			}
		});
		b.setView(box);
		final AlertDialog menu = b.create();
		menu.setOnDismissListener(new DialogInterface.OnDismissListener() {
			public void onDismiss(DialogInterface di) {
				hideBars();
			}
		});
		lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
			public void onItemClick(android.widget.AdapterView<?> p, View v, int k, long id) {
				menu.dismiss();
				SharedPreferences.Editor e = prefs.edit();
				String it = ids.get(k);
				if (it.equals("city")) { onCity(); return; }
				else if (it.equals("unit")) e.putBoolean("unitF", !s.unitF);
				else if (it.equals("clock")) e.putBoolean("clk12", !s.clk12);
				else if (it.equals("lang")) e.putString("lang", en ? "ro" : "en");
				else if (it.equals("keepOn")) e.putBoolean("keepOn", !prefs.getBoolean("keepOn", true));
				else if (it.equals("theme")) { pickTheme(); return; }
				else if (it.equals("loc")) {
					if (autoLoc()) setAutoLoc(false);
					else onLocate();
					return;
				} else if (it.equals("update")) { updater.check(true); return; }
				else { about(); return; }
				e.apply();
				applySettings();
				view.invalidate();
			}
		});
		menu.show();
	}

	/** "⌃ more ⌃" / "⌄ more ⌄" over and under the menu list: there are more items that way. */
	private android.widget.TextView hint(Context ctx, String text) {
		android.widget.TextView t = new android.widget.TextView(ctx);
		t.setText(text);
		t.setGravity(android.view.Gravity.CENTER);
		t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
		t.setTextColor(s.dark ? 0xFFA6A39A : 0xFF555555);
		int pad = Math.round(6 * getResources().getDisplayMetrics().density);
		t.setPadding(0, pad, 0, pad);
		return t;
	}

	private void pickTheme() {
		final String[] keys = hasLight() ? new String[] { "light", "dark", "auto", "sensor" } : new String[] { "light", "dark", "auto" };
		String[] all = s.T.en
				? new String[] { "Light (Kindle, black and white)", "Dark (Claude colours)", "Automatic (as the phone)", "By the room's light (sensor)" }
				: new String[] { "Luminoasă (Kindle, alb-negru)", "Întunecată (culorile Claude)", "Automată (ca telefonul)", "După lumina din cameră (senzor)" };
		String[] items = java.util.Arrays.copyOf(all, keys.length);
		int cur = java.util.Arrays.asList(keys).indexOf(prefs.getString("theme", "auto"));
		show(dialog().setTitle(s.T.en ? "Theme" : "Temă").setSingleChoiceItems(items, cur, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface d, int k) {
				prefs.edit().putString("theme", keys[k]).apply();
				d.dismiss();
				applySettings();
				view.invalidate();
			}
		}));
	}

	/** After an update: what is new in the installed version. */
	private void news() {
		String log = changeLog();
		if (log.length() == 0) return;
		int cut = log.indexOf("\n\n");
		if (cut > 0) log = log.substring(0, cut);
		sheet.show((s.T.en ? "What's new" : "Ce e nou") + " · v" + updater.installed(), log, null, null, s.T.en ? "Close" : "Închide");
	}

	/** The change log from assets, newest first. */
	private String changeLog() {
		String log;
		try {
			java.io.InputStream in = getAssets().open(s.T.en ? "jurnal-en.txt" : "jurnal-ro.txt");
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
			in.close();
			log = new String(out.toByteArray(), "UTF-8").trim();
		} catch (Exception e) {
			return "";
		}
		return log;
	}

	private void about() {
		String msg = aboutText() + "\n\n" + (s.T.en ? "The dark theme uses the Claude app colours. Font: Liberation Sans."
				: "Tema întunecată folosește culorile aplicației Claude. Font: Liberation Sans.");
		msg += "\n\n" + (s.T.en ? "Installed version: v" : "Versiunea instalată: v") + updater.installed()
				+ "\n" + (s.T.en ? "Updates: " : "Actualizări: ") + "github.com/" + ro.meteoalarm.android.core.Release.REPO + "/releases";
		final String aboutMsg = msg;
		sheet.show(VERSION, msg, null, null, s.T.en ? "Close" : "Închide");
		// "Ce e nou" on the left: the change log in the same window, and back to About.
		final String newsL = s.T.en ? "What's new" : "Ce e nou", aboutL = s.T.en ? "About" : "Despre";
		sheet.setLeft(newsL, new Runnable() {
			boolean onNews;

			public void run() {
				onNews = !onNews;
				sheet.replace(onNews ? newsL : VERSION, onNews ? changeLog() : aboutMsg);
				sheet.setLeft(onNews ? aboutL : newsL, this);
			}
		});
	}

	/** Favourites (the last three towns) and the county list. */
	public void onCity() {
		final List<Cities.City> favs = new ArrayList<Cities.City>();
		for (String k : prefs.getString("favs", "").split("\n")) {
			Cities.City c = Cities.City.fromKey(k);
			if (c != null) favs.add(c);
		}
		final boolean en = s.T.en;
		if (favs.isEmpty()) {
			pickCounty();
			return;
		}
		String[] items = new String[favs.size() + 1];
		for (int k = 0; k < favs.size(); k++) items[k] = favs.get(k).label();
		items[favs.size()] = en ? "Choose county…" : "Alege județul…";
		show(dialog().setTitle(en ? "Town" : "Localitatea").setItems(items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface d, int k) {
				if (k < favs.size()) choose(favs.get(k));
				else pickCounty();
			}
		}));
	}

	private void pickCounty() {
		final String[] counties = cities.byCounty.keySet().toArray(new String[0]);
		show(dialog().setTitle(s.T.en ? "Choose county" : "Alege județul").setItems(counties, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface d, int k) {
				pickTown(counties[k]);
			}
		}));
	}

	private void pickTown(String county) {
		final List<Cities.City> l = cities.byCounty.get(county);
		String[] items = new String[l.size()];
		for (int k = 0; k < l.size(); k++) items[k] = l.get(k).name;
		show(dialog().setTitle(county).setItems(items, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface d, int k) {
				choose(l.get(k));
			}
		}));
	}

	/** A town picked from the list: automatic location goes off until the locate button. */
	private void choose(Cities.City c) {
		setAutoLoc(false);
		setCity(c, true);
		fetch();
	}

	/** The shown town, kept in the settings and first among the favourites (the last three). */
	private void setCity(Cities.City c, boolean clear) {
		StringBuilder favs = new StringBuilder(c.key());
		int n = 1;
		for (String k : prefs.getString("favs", "").split("\n")) {
			Cities.City f = Cities.City.fromKey(k);
			if (n >= 3 || f == null || (f.name.equals(c.name) && f.county.equals(c.county))) continue;
			favs.append('\n').append(k);
			n++;
		}
		prefs.edit().putString("city", c.key()).putString("favs", favs.toString()).apply();
		boolean same = s.city != null && s.city.name.equals(c.name) && s.city.county.equals(c.county);
		s.city = c;
		if (clear || !same) {
			s.w = null;
			s.warns = null;
			s.pollenLvl = -1;
			s.fetched = 0;
		}
		s.error = "";
		view.invalidate();
	}
}
