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

import ro.meteoalarm.android.core.Area;
import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Countries;
import ro.meteoalarm.android.core.Screen;
import ro.meteoalarm.android.core.Texts;
import ro.meteoalarm.android.core.Warnings;
import ro.meteoalarm.android.core.Weather;

/**
 * Meteo Alarm Android: the Kindle weather clock on a phone. The weather is fetched every 15 minutes
 * while the screen is on (Refresher: also in the background) and kept on disk, so it opens at once offline.
 */
public final class MainActivity extends Activity implements WeatherView.Taps {
	static final String VERSION = "Meteo Alarm Android v1.92";
	private static final long WEATHER_MS = Refresher.SCREEN_ON_MS, LOW_BATTERY_MS = Refresher.LOW_BATTERY_MS;
	/** The app is on the screen (the refresh in the background then leaves the work to it). */
	static volatile boolean onScreen;

	private WeatherView view;
	private Screen s;
	private Cities cities;
	private SharedPreferences prefs;
	private final Handler handler = new Handler();
	private boolean fetching, asked;
	private long lastTry, lastLocate, lastDone;
	static final long REPULL_MS = 10000;
	private Locator locator;
	private Updater updater;
	private BottomSheet sheet;
	private Bubble bubble;
	private Countries countries;
	private HelpPanel help;
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
			checkNight();
			view.invalidate();
			// Next tick at the start of the next minute, for the clock.
			handler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50);
		}
	};

	private final BroadcastReceiver battery = new BroadcastReceiver() {
		public void onReceive(Context ctx, Intent i) {
			int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
			int was = s.batt;
			boolean wasCh = s.charging;
			s.batt = lvl < 0 ? -1 : lvl * 100 / Math.max(1, sc);
			s.charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
			if (wasCh != s.charging || was / 5 != s.batt / 5)
				Log.i(MainActivity.this, "BATERIE", s.batt + "%" + (s.charging ? ", se încarcă" : ""));
			checkNight();
			view.invalidate();
		}
	};

	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		Intent st = getIntent();
		Log.i(this, "APP", "pornită " + VERSION + " · " + Log.device() + " · Android " + android.os.Build.VERSION.RELEASE
				+ (b != null ? " · recreată de sistem" : "") + " · de la: " + (st == null || st.getAction() == null ? "?" : st.getAction().replace("android.intent.action.", ""))
				+ " · task " + getTaskId());
		prefs = getSharedPreferences("settings", MODE_PRIVATE);
		view = new WeatherView(this, this);
		s = view.screen;
		try {
			cities = new Cities(readAll(getAssets().open("cities.txt")));
		} catch (Exception e) {
			cities = new Cities("");
		}
		try {
			cities.addWorld(readAll(getAssets().open("world.txt")));
			countries = new Countries(readAll(getAssets().open("countries.txt")));
		} catch (Exception e) {
			countries = new Countries("");
		}
		// Towns found by the location, kept on the phone.
		cleanNeighbourhoods();
		for (String k : prefs.getString("myCities", "").split("\n")) {
			Cities.City c = Cities.City.fromKey(k);
			if (c != null) cities.put(c);
		}
		locator = new Locator(this);
		// The town from the phone's geocoder (the neighbourhood option is gone).
		locator.osm = false;
		sheet = new BottomSheet(this);
		updater = new Updater(this, sheet);
		applySettings();
		loadCache();
		android.widget.FrameLayout root = new android.widget.FrameLayout(this);
		root.addView(view);
		root.addView(sheet);
		bubble = new Bubble(this, android.graphics.Typeface.createFromAsset(getAssets(), "fonts/LiberationSans-Regular.ttf"),
				android.graphics.Typeface.createFromAsset(getAssets(), "fonts/LiberationSans-Bold.ttf"));
		bubble.setDark(s.dark);
		bubble.setOnClose(new Runnable() {
			public void run() {
				hideBars();
			}
		});
		root.addView(bubble, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		help = new HelpPanel(this, android.graphics.Typeface.createFromAsset(getAssets(), "fonts/LiberationSans-Regular.ttf"),
				android.graphics.Typeface.createFromAsset(getAssets(), "fonts/LiberationSans-Bold.ttf"));
		help.setOnClose(new Runnable() {
			public void run() {
				hideBars();
			}
		});
		root.addView(help, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		setContentView(root);
	}

	@Override
	public void onBackPressed() {
		if (bubble.isOpen()) bubble.hide();
		else if (help.isOpen()) help.hide();
		else if (sheet.isOpen()) {
			if (sheet.isCancelable()) sheet.hide();
		} else super.onBackPressed();
	}

	/** About the app: shown once at the first start (before the location question) and from ☰ › Despre. */
	private String aboutText() {
		return s.T.en
				? "This is a test version of Meteo Alarm Android, made from Meteo Alarm Kindle.\n\n"
						+ "No data collected: no account, no ads, no statistics. To get the weather, only the place's coordinates are sent "
						+ "to Open-Meteo (weather, pollen) and, when Open-Meteo is overloaded, to MET Norway (weather); the warnings come from MeteoAlarm (ANM), the earthquakes from EMSC (seismicportal.eu).\n\n"
						+ "Location: used to show the weather where you are, at start and once an hour. To name the town, the "
						+ "coordinates go to OpenStreetMap (or the phone's geocoder), which also gives the town's boundary. Towns found, "
						+ "with where you were, are kept only on the phone. "
						+ "Without location access, you choose the town from the list.\n\n"
						+ "Sensors: the app reads the battery state (night clock while charging), the phone's light sensor "
						+ "with ☰ › Theme › Automatic (phone sensor), and the compass, which turns the wind arrow with the phone. "
						+ "Nothing leaves the phone.\n\n"
						+ "Updates: once a week the app checks GitHub for a new version and asks before downloading it; "
						+ "you can also check from ☰ › Check for updates.\n\n"
						+ Updater.protectNote(true)
				: "Aceasta este o versiune de test a Meteo Alarm Android, făcută din Meteo Alarm Kindle.\n\n"
						+ "Nu colectăm date: fără cont, fără reclame, fără statistici. Pentru vreme se trimit doar coordonatele "
						+ "localității către Open-Meteo (vremea, polenul) și, când Open-Meteo e supraîncărcat, către MET Norway (vremea); avertizările vin de la MeteoAlarm (ANM), cutremurele de la EMSC (seismicportal.eu).\n\n"
						+ "Locația: o folosim ca să arătăm vremea acolo unde ești, la pornire și o dată pe oră. Pentru numele "
						+ "localității, coordonatele merg la OpenStreetMap (sau la geocoderul telefonului), de unde vin și granițele "
						+ "localității. Localitățile găsite, cu locul exact, rămân doar pe telefon. Fără acces la locație, alegi localitatea din listă.\n\n"
						+ "Senzori: aplicația citește starea bateriei (ceasul de noapte la încărcare), senzorul de lumină al "
						+ "telefonului cu ☰ › Temă › Automat (senzor telefon) și busola, care rotește săgeata vântului după "
						+ "telefon. Nimic nu pleacă de pe telefon.\n\n"
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

	/** With the app open the screen is on: every 15 minutes, every hour below 25% battery (not charging). */
	private long interval() {
		return s.batt >= 0 && s.batt < Refresher.LOW_BATTERY && !s.charging ? LOW_BATTERY_MS : WEATHER_MS;
	}

	private void applySettings() {
		s.T = new Texts("en".equals(prefs.getString("lang", "ro")));
		s.unitF = prefs.getBoolean("unitF", false);
		s.clk12 = prefs.getBoolean("clk12", false);
		Cities.City c = Cities.City.fromKey(prefs.getString("city", ""));
		if (c == null) c = cities.find("Călărași");
		if (c == null) c = new Cities.City("Călărași", "Călărași", 44.2058, 27.3306);
		// Keep the town shown even when it was never picked, so the widget finds it too.
		if (prefs.getString("city", "").length() == 0) prefs.edit().putString("city", c.key()).apply();
		s.city = c;
		// No neighbourhood after the town any more (Viorel 2026-11-02).
		s.district = "";
		// Theme: "light" (Kindle), "dark" (Claude colours), "auto" (follows the phone's dark mode) or
		// "sensor" (dark when the room is dark, from the light sensor).
		String theme = prefs.getString("theme", "auto");
		boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
		s.dark = theme.equals("dark") || (theme.equals("auto") && night) || (theme.equals("sensor") && roomDark);
		listenLight(theme.equals("sensor") && resumed);
		applyBrightness();
		s.autoLoc = autoLoc();
		s.sensorTheme = theme.equals("sensor");
		barColours();
		view.setBackgroundColor(s.dark ? 0xFF262624 : 0xFFFFFFFF);
		hideBars();
		getWindow().getDecorView().setBackgroundColor(s.dark ? 0xFF262624 : 0xFFFFFFFF);
		if (prefs.getBoolean("keepOn", true)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else if (!s.nightClock) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if (sheet != null) sheet.setDark(s.dark);
		if (bubble != null) bubble.setDark(s.dark);
		if (updater != null) updater.en = s.T.en;
	}

	@Override
	public void onConfigurationChanged(Configuration cfg) {
		super.onConfigurationChanged(cfg);
		applySettings();
		view.invalidate();
		// A bubble open while the phone turns is opened again for the new screen.
		if (bubble.isOpen() && reopen != null) {
			final Runnable r = reopen;
			bubble.hide();
			view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				public void onLayoutChange(View v, int l, int t, int ri, int b, int ol, int ot, int or, int ob) {
					view.removeOnLayoutChangeListener(this);
					handler.postDelayed(r, 150); // after the first drawing has placed the buttons
				}
			});
		}
	}

	// --- Compass ---------------------------------------------------------------------------

	private boolean compassOn;
	private float headSmooth = Float.NaN;
	private final float[] rot = new float[9];

	/**
	 * The phone's heading for the wind arrow: the way the top of the screen points when the phone
	 * lies flat, or the way its back faces when it stands (as a clock on a stand).
	 */
	private final SensorEventListener compass = new SensorEventListener() {
		public void onSensorChanged(SensorEvent e) {
			SensorManager.getRotationMatrixFromVector(rot, e.values);
			// Device vectors in the world (x east, y north, z up): R * v.
			int r = getWindowManager().getDefaultDisplay().getRotation();
			float ux = r == 1 ? 1 : r == 3 ? -1 : 0, uy = r == 0 ? 1 : r == 2 ? -1 : 0;
			float vx, vy;
			if (Math.abs(rot[8]) > 0.7f) {
				vx = rot[0] * ux + rot[1] * uy;
				vy = rot[3] * ux + rot[4] * uy;
			} else {
				vx = -rot[2];
				vy = -rot[5];
			}
			float hd = (float) Math.toDegrees(Math.atan2(vx, vy));
			if (Float.isNaN(headSmooth)) headSmooth = hd;
			else {
				float d = ((hd - headSmooth + 540) % 360) - 180;
				headSmooth = (headSmooth + d * 0.2f + 360) % 360;
			}
			float old = s.heading;
			if (Float.isNaN(old) || Math.abs(((headSmooth - old + 540) % 360) - 180) > 2) {
				s.heading = headSmooth;
				if (!s.nightClock) view.invalidate();
			}
		}

		public void onAccuracyChanged(Sensor sn, int acc) {
		}
	};

	private void listenCompass(boolean on) {
		if (sensors == null) sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
		Sensor sn = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		if (sn == null || on == compassOn) return;
		compassOn = on;
		Log.i(this, "SENZOR", "busola " + (on ? "pornită" : "oprită"));
		if (on) sensors.registerListener(compass, sn, SensorManager.SENSOR_DELAY_UI);
		else sensors.unregisterListener(compass);
	}

	// --- Night clock -------------------------------------------------------------------------

	private long lastTouch, nightPausedUntil;

	/** A touch on the screen: leaves the night clock for a minute, and restarts the idle minute. */
	public void onTouched() {
		lastTouch = System.currentTimeMillis();
		if (s.nightClock) {
			nightPausedUntil = lastTouch + 60000;
			setNight(false);
		}
	}

	/** Night: between sunset and sunrise (from the forecast; 21:00–07:00 without it). */
	private boolean isNight() {
		Calendar c = Calendar.getInstance();
		int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
		int rise = 7 * 60, set = 21 * 60;
		try {
			if (s.w != null && s.w.sunrise.length() >= 5 && s.w.sunset.length() >= 5) {
				rise = Integer.parseInt(s.w.sunrise.substring(0, 2)) * 60 + Integer.parseInt(s.w.sunrise.substring(3, 5));
				set = Integer.parseInt(s.w.sunset.substring(0, 2)) * 60 + Integer.parseInt(s.w.sunset.substring(3, 5));
			}
		} catch (Exception e) {
			// keep the defaults
		}
		return now >= set || now < rise;
	}

	/** The night clock comes when charging at night after a minute without touches. */
	private void checkNight() {
		long t = System.currentTimeMillis();
		boolean want = prefs.getBoolean("nightClock", true) && s.charging && isNight() && t >= nightPausedUntil
				&& t - lastTouch >= 60000 && !bubble.isOpen() && !sheet.isOpen() && !help.isOpen();
		if (want != s.nightClock) setNight(want);
		// While charging, look again in a minute even between clock ticks.
		handler.removeCallbacks(nightCheck);
		if (s.charging && !s.nightClock) handler.postDelayed(nightCheck, 61000 - Math.min(60000, t - lastTouch));
	}

	private final Runnable nightCheck = new Runnable() {
		public void run() {
			checkNight();
		}
	};

	/** Night clock on: the screen dim and kept on; off: the brightness and the screen setting as before. */
	private void setNight(boolean on) {
		s.nightClock = on;
		Log.i(this, "NOAPTE", "ceasul de noapte " + (on ? "pornit" : "oprit"));
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		if (on) {
			lp.screenBrightness = Math.min(0.03f, prefs.getFloat("bright", 1));
			getWindow().setAttributes(lp);
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else applySettings();
		view.invalidate();
	}

	// --- Light sensor and brightness ------------------------------------------------------

	/** Below DARK_LUX the room counts as dark, above LIGHT_LUX as lit (in between: no change). */
	private static final float DARK_LUX = 10, LIGHT_LUX = 30;
	/** The room has to stay dark (or lit) this long before the theme follows. */
	private static final long LIGHT_HOLD_MS = 1500;
	private SensorManager sensors;
	private boolean resumed, roomDark, lightOn, lightFirst, lightWant;
	private float lastLux;

	/**
	 * Applies the room's state once it has held for LIGHT_HOLD_MS. A timer, not the next reading:
	 * the sensor reports only changes, so a room that goes dark and stays dark sends one reading.
	 */
	private final Runnable lightApply = new Runnable() {
		public void run() {
			if (lightWant == roomDark) return;
			roomDark = lightWant;
			Log.i(MainActivity.this, "SENZOR", "lumina: camera " + (roomDark ? "întunecată -> tema întunecată" : "luminată -> tema luminoasă") + " (" + Math.round(lastLux) + " lux)");
			applySettings();
			view.invalidate();
		}
	};

	private final SensorEventListener light = new SensorEventListener() {
		public void onSensorChanged(SensorEvent e) {
			float lux = e.values[0];
			lastLux = lux;
			// Light: the light theme; dark: the dark theme.
			boolean want = lux < DARK_LUX ? true : lux > LIGHT_LUX ? false : lightWant;
			// The first reading counts at once (the room is as it is when the app opens).
			if (lightFirst) {
				lightFirst = false;
				lightWant = want;
				lightApply.run();
				return;
			}
			if (want == lightWant) return;
			lightWant = want;
			handler.removeCallbacks(lightApply);
			if (want != roomDark) handler.postDelayed(lightApply, LIGHT_HOLD_MS);
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
		Log.i(this, "SENZOR", "senzorul de lumină " + (on ? "pornit" : "oprit"));
		lightFirst = true;
		lightWant = roomDark;
		handler.removeCallbacks(lightApply);
		if (on) sensors.registerListener(light, sn, SensorManager.SENSOR_DELAY_NORMAL);
		else sensors.unregisterListener(light);
	}

	private boolean hasLight() {
		if (sensors == null) sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
		return sensors != null && sensors.getDefaultSensor(Sensor.TYPE_LIGHT) != null;
	}

	/**
	 * The brightness bar in the phone's colours: on Android 12+ the theme's palette (light accent
	 * for the level, neutral gray for the track, dark neutral for the sun), as the phone's own bar.
	 */
	private void barColours() {
		if (android.os.Build.VERSION.SDK_INT < 31) return;
		int f = sysColour("system_accent1_100"), t = sysColour("system_neutral2_500"), i = sysColour("system_neutral1_900");
		if (f != 0) s.barFill = f;
		if (t != 0) s.barTrack = t;
		if (i != 0) s.barIcon = i;
	}

	private int sysColour(String name) {
		int id = getResources().getIdentifier(name, "color", "android");
		if (id == 0) return 0;
		try {
			return getResources().getColor(id, getTheme());
		} catch (Exception e) {
			return 0;
		}
	}

	/** The app's own brightness (0.004–1) set with the slider, or the phone's (-1). */
	private void applyBrightness() {
		// The night clock keeps its dim screen until it ends.
		if (s.nightClock) return;
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
		if (save) {
			prefs.edit().putFloat("bright", b).apply();
			Log.i(this, "GEST", "luminozitate " + Math.round(b * 100) + "%");
		}
	}

	/** The button next to the sun: the automatic theme (phone sensor), on or off. */
	public void toggleSensor() {
		if (!hasLight()) {
			toast(s.T.en ? "This phone has no light sensor." : "Telefonul nu are senzor de lumină.");
			return;
		}
		boolean on = !"sensor".equals(prefs.getString("theme", "auto"));
		Log.i(this, "BUTON", "temă automată (senzor) " + (on ? "pornită" : "oprită"));
		// Off: the theme stays as it is now, chosen by hand.
		prefs.edit().putString("theme", on ? "sensor" : s.dark ? "dark" : "light").apply();
		applySettings();
		view.invalidate();
		note(on ? (s.T.en ? "Automatic theme (phone sensor): on" : "Temă automată (senzor telefon): pornită")
				: (s.T.en ? "Automatic theme (phone sensor): off" : "Temă automată (senzor telefon): oprită"));
	}

	/** The moon button over the brightness bar: dark or light theme, chosen by hand. */
	public void toggleTheme() {
		Log.i(this, "BUTON", "temă " + (s.dark ? "luminoasă" : "întunecată"));
		prefs.edit().putString("theme", s.dark ? "light" : "dark").apply();
		applySettings();
		note(s.dark ? (s.T.en ? "Dark theme" : "Tema întunecată") : (s.T.en ? "Light theme" : "Tema luminoasă"));
		view.invalidate();
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i(this, "APP", "pe ecran");
		resumed = true;
		onScreen = true;
		listenLight("sensor".equals(prefs.getString("theme", "auto")));
		listenCompass(true);
		lastTouch = System.currentTimeMillis();
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
		// Updates come from GitHub only with the user's consent: asked once, at the next start.
		if (!updater.agreed() && !prefs.getBoolean("ghAsked", false)) {
			updater.check(false);
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

	private final Runnable clearNote = new Runnable() {
		public void run() {
			s.note = "";
			view.invalidate();
		}
	};

	/** A short notice over the status row after a button; it goes away after 1.5 seconds. */
	private void note(String msg) {
		note(msg, 1500);
	}

	private void note(String msg, long ms) {
		s.note = msg;
		s.noteTop = noteTop;
		view.invalidate();
		handler.removeCallbacks(clearNote);
		handler.postDelayed(clearNote, ms);
	}

	public void onBright() {
		Log.i(this, "BUTON", "luminozitate (soare)");
		note(s.T.en ? "Brightness: slide on the bar" : "Luminozitate: glisează pe bară");
	}

	/** Notices of a look started from the town's name go under the line under the town. */
	private boolean noteTop;

	/** A tap on box K of the warnings row: its details in a bubble, in larger letters (the row waits 20 s). */
	public void onAlert(final int k, final float[] b) {
		Log.i(this, "GEST", "atingere casetă avertizare " + (k + 1) + " din " + s.alertCount());
		reopen = new Runnable() {
			public void run() {
				if (k < s.alertCount()) onAlert(k, s.alertRect(k));
			}
		};
		List<String[]> info = s.alertInfo(k);
		if (info.isEmpty()) return;
		String head = info.get(0)[1];
		List<Bubble.Row> rows = Bubble.rows();
		for (int i = 1; i < info.size(); i++) {
			String[] r = info.get(i);
			rows.add(new Bubble.Row(r[1], null, r[0].equals("h") ? Bubble.Row.HEADER : Bubble.Row.TEXT, false));
		}
		bubble.show(view.px(b), bubbleWidth(0.86f, 280), head, rows, s.T.en, new Bubble.Pick() {
			public void picked(int i) {
			}
		});
	}

	/**
	 * A tap on the town's name: one look with the GPS for a better forecast; automatic location
	 * stays as it is.
	 */
	public void onTownName() {
		Log.i(this, "GEST", "atingere nume localitate (o căutare GPS)");
		if (!Locator.allowed(this)) {
			askLocation();
			return;
		}
		noteTop = true;
		locate(true);
	}

	/**
	 * Locate button (and ☰ Localizare On / Off): a switch. Off -> automatic location on (the button
	 * filled) and a look now; on -> off (the button empty).
	 */
	public void onLocate() {
		Log.i(this, "BUTON", "localizare");
		noteTop = false;
		boolean en = s.T.en;
		if (autoLoc()) {
			setAutoLoc(false);
			note(en ? "Automatic location OFF" : "Localizare automată OPRITĂ", 2000);
			return;
		}
		setAutoLoc(true);
		note(en ? "Automatic location ON" : "Localizare automată PORNITĂ", 2000);
		if (!Locator.allowed(this)) askLocation();
		else locate(false);
	}

	/**
	 * Finds the phone's town. The weather is then for the exact position; the town is added to
	 * the list on the phone when cities.txt does not have it. SAY: report failures (a tap).
	 */
	private void locate(final boolean say) {
		// Already looking: the answer of that look comes (no second wait that would never end).
		if (s.locating) return;
		lastLocate = System.currentTimeMillis();
		s.locating = true;
		view.invalidate();
		if (say) note(s.T.en ? "Looking for the exact position…" : "Caut poziția exactă…");
		locator.locate(new Locator.Result() {
			public void found(double lat, double lon, String town, String rawCounty, String district, String country, String match) {
				s.locating = false;
				Log.i(MainActivity.this, "LOC", "poziție găsită: " + town + ", " + rawCounty + ", " + country + " (" + Math.round(lat * 1000) / 1000.0 + ", " + Math.round(lon * 1000) / 1000.0 + ")");
				boolean home = country.equals("ro");
				String county = home ? cities.county(rawCounty) : rawCounty.trim();
				if (county.length() == 0) county = town;
				// The neighbourhood is only shown, never kept: the town keeps its name in the list.
				String name = town;
				Cities.City found = new Cities.City(country, county, name, 0, 0, match);
				Cities.City listed = cities.find(found);
				if (listed != null) name = listed.name;
				double la = Math.round(lat * 10000) / 10000.0, lo = Math.round(lon * 10000) / 10000.0;
				// The town shown changes only when the phone is out of its boundary by more than
				// EDGE_M metres (a more exact name, as a sector of the same town, is taken at once).
				Cities.City cur = s.city;
				boolean same = found.same(cur);
				Area area = cur == null || same ? null : loadArea(cur);
				boolean keep = area != null && area.holds(lat, lon, EDGE_M);
				Cities.City here = keep ? new Cities.City(cur.country, cur.county, cur.name, la, lo, cur.match)
						: new Cities.City(country, county, name, la, lo, match);
				boolean added = cities.find(here) == null;
				learn(here);
				if (added && say) toast((s.T.en ? "Added to the list: " : "Adăugată în listă: ") + here.label());
				if (!keep) fetchArea(here, town, null, lat, lon);
				boolean moved = cur == null || !here.same(cur) || distKm(cur.lat, cur.lon, la, lo) > 1;
				if (moved) setCity(here, false);
				view.invalidate();
				if (moved || say) fetch();
				noteTop = false;
			}

			public void failed(String why) {
				s.locating = false;
				if (why.equals("busy")) Log.i(MainActivity.this, "LOC", "căutare deja în curs");
				else Log.e(MainActivity.this, "LOC", "poziția nu a fost găsită: " + why);
				noteTop = false;
				view.invalidate();
				if (!say || why.equals("busy")) return;
				if (why.equals("off")) toast(s.T.en ? "Location is off on the phone." : "Localizarea e oprită pe telefon.");
				else if (why.equals("nofix")) toast(s.T.en ? "The position could not be found." : "Poziția nu a putut fi găsită.");
				else toast(s.T.en ? "The town could not be found (internet?)." : "Localitatea nu a putut fi găsită (internet?).");
			}
		});
	}

	/** Shows D (null or empty: none) after the town, and keeps it for the next start. */
	private void setDistrict(String d) {
		s.district = "";
		prefs.edit().putString("district", s.district).putString("districtOf", s.city != null ? s.city.key() : "").apply();
	}

	/**
	 * Up to v1.40 a neighbourhood was kept as a town ("Călărași, Mircea Vodă"): such entries become
	 * their town again, in the learned towns, the favourites and the town shown.
	 */
	private void cleanNeighbourhoods() {
		SharedPreferences.Editor e = prefs.edit();
		for (String key : new String[] { "myCities", "favs" }) {
			StringBuilder b = new StringBuilder();
			List<Cities.City> kept = new ArrayList<Cities.City>();
			for (String k : prefs.getString(key, "").split("\n")) {
				Cities.City c = baseTown(Cities.City.fromKey(k));
				if (c == null) continue;
				boolean dup = false;
				for (Cities.City o : kept) dup |= o.same(c);
				if (dup) continue;
				kept.add(c);
				b.append(b.length() > 0 ? "\n" : "").append(c.key());
			}
			e.putString(key, b.toString());
		}
		Cities.City cur = Cities.City.fromKey(prefs.getString("city", ""));
		Cities.City base = baseTown(cur);
		if (cur != null && base != null && !base.name.equals(cur.name)) e.putString("city", base.key()).putString("district", "").putString("districtOf", "");
		e.apply();
	}

	private static Cities.City baseTown(Cities.City c) {
		if (c == null || c.abroad() || c.name.indexOf(", ") < 0) return c;
		return new Cities.City(c.country, c.county, c.name.substring(0, c.name.indexOf(", ")), c.lat, c.lon, c.match);
	}

	/** Margin (metres) past a town's boundary before the town shown changes. */
	private static final double EDGE_M = 150;

	/**
	 * Keeps a town found by the location, with where the phone was: it replaces the listed town's
	 * coordinates (choosing it from the list then gives the weather there) or joins the list.
	 */
	private void learn(Cities.City c) {
		cities.put(c);
		StringBuilder b = new StringBuilder(c.key());
		for (String k : prefs.getString("myCities", "").split("\n")) {
			Cities.City m = Cities.City.fromKey(k);
			if (m == null || m.same(c)) continue;
			b.append('\n').append(k);
		}
		prefs.edit().putString("myCities", b.toString()).apply();
	}

	private java.io.File areaFile(Cities.City c) {
		java.io.File dir = new java.io.File(getFilesDir(), "areas");
		dir.mkdirs();
		return new java.io.File(dir, Warnings.fold(c.country + "_" + c.county + "_" + c.name).replaceAll("[^a-z0-9]+", "_") + ".txt");
	}

	/** The boundary kept for C, or null (none fetched yet, or OpenStreetMap has none). */
	private Area loadArea(Cities.City c) {
		java.io.File f = areaFile(c);
		if (!f.exists()) return null;
		try {
			java.io.FileInputStream in = new java.io.FileInputStream(f);
			String t = readAll(in);
			return Area.load(t);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Fetches C's boundary once (TOWN and DISTRICT as OpenStreetMap names them) and keeps it; a town
	 * without one is marked so it is asked again only after a week.
	 */
	private void fetchArea(final Cities.City c, String town, String district, final double lat, final double lon) {
		final java.io.File f = areaFile(c);
		if (f.exists() && (f.length() > 2 || System.currentTimeMillis() - f.lastModified() < 7 * 86400000L)) return;
		final String t = town != null ? town : c.name, d = town != null ? district : null, co = c.county;
		new Thread(new Runnable() {
			public void run() {
				try {
					Thread.sleep(1100); // OpenStreetMap asks for at most one request a second
				} catch (InterruptedException e) {
					return;
				}
				Area a = Locator.boundary(t, d, co, lat, lon);
				try {
					java.io.FileOutputStream out = new java.io.FileOutputStream(f);
					out.write((a != null ? a.save() : "-").getBytes("UTF-8"));
					out.close();
				} catch (Exception e) {
					// kept only as a convenience
				}
			}
		}).start();
	}

	private static double distKm(double la1, double lo1, double la2, double lo2) {
		double dy = (la2 - la1) * 111.2, dx = (lo2 - lo1) * 111.2 * Math.cos(Math.toRadians(la1));
		return Math.sqrt(dx * dx + dy * dy);
	}

	@Override
	protected void onDestroy() {
		Log.i(this, "APP", "închisă" + (isFinishing() ? "" : " de sistem (schimbare de configurație sau memorie)"));
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.i(this, "APP", "în fundal" + (isFinishing() ? " (se închide)" : ""));
		onScreen = false;
		// The weather keeps coming in the background (every 15 / 30 / 60 minutes, paused at night with the screen off).
		Refresher.schedule(this);
		// Settings changed in the app (town, °C/°F, language) show on the home-screen widget too.
		Widget.show(this);
		resumed = false;
		listenLight(false);
		listenCompass(false);
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

	/**
	 * The status bar hidden like on the Kindle; the phone's navigation (the three buttons or the
	 * gesture line) stays visible and the screen is fitted above it, in the page's colour.
	 */
	private void hideBars() {
		if (view == null) return;
		// 0x10 = SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR (Android 8+): dark buttons on the light page.
		view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | (s.dark ? 0 : 0x10));
		getWindow().setNavigationBarColor(s.dark ? 0xFF262624 : 0xFFFFFFFF);
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
			String u = readCache("uv.json");
			if (s.w != null && u != null) Weather.addExtra(s.w, u);
			s.fetched = prefs.getLong("fetched", 0);
			// Saved by an older version without the wind direction: fetch again right away.
			if (s.w != null && Double.isNaN(s.w.windDir)) s.fetched = 0;
			// Saved before the pressure / visibility boxes: fetch again right away.
			if (s.w != null && Double.isNaN(s.w.pressure)) s.fetched = 0;
			String p = readCache("pollen.json");
			if (p != null) {
				Object[] r = Weather.parsePollen(p);
				s.pollenLvl = (Integer) r[0];
				s.pollenPlant = (String) r[1];
				s.aqi = Weather.parseAqi(p);
			}
			String x = readCache("warn.xml");
			if (x != null) s.warns = Warnings.parse(x, s.city.warnNames(), System.currentTimeMillis());
			s.quakes = Fetcher.quakes(this, s.city);
			s.failReason = prefs.getString(s.T.en ? "failReasonEn" : "failReason", "");
			s.source = prefs.getString("source", "");
		} catch (Exception e) {
			s.w = null;
			Log.e(this, "DATE", "vremea salvată nu a putut fi citită", e);
		}
	}

	private void fetch() {
		if (fetching) return;
		fetching = true;
		lastTry = System.currentTimeMillis();
		Log.i(this, "DATE", "descarc vremea pentru " + s.city.label());
		s.busy = true;
		view.invalidate();
		final Cities.City c = s.city;
		final Context ctx = getApplicationContext();
		new Thread(new Runnable() {
			public void run() {
				final Fetcher.Result r = Fetcher.download(ctx, c);
				handler.post(new Runnable() {
					public void run() {
						done(c, r);
					}
				});
			}
		}).start();
	}

	private void done(Cities.City c, Fetcher.Result r) {
		fetching = false;
		lastDone = System.currentTimeMillis();
		s.busy = false;
		if (c != s.city) { // the town changed meanwhile
			fetch();
			return;
		}
		long now = System.currentTimeMillis();
		Fetcher.save(this, c, r);
		if (r.forecast != null) {
			s.w = Weather.parse(r.forecast);
			if (r.uv != null) Weather.addExtra(s.w, r.uv);
			s.fetched = now;
			s.offline = false;
			s.failReason = "";
			s.source = r.source;
			Widget.show(this);
		} else {
			// Why it did not come: under the line ("no data since …") and in a notice now.
			s.failReason = Fetcher.reason(r.error, s.T.en);
			if (s.w == null) s.error = (s.T.en ? "The weather did not come: " : "Vremea nu a venit: ") + s.failReason;
			else if (resumed) note((s.T.en ? "The weather did not come: " : "Vremea nu a venit: ") + s.failReason, 3000);
		}
		if (r.pollen != null) {
			try {
				Object[] pr = Weather.parsePollen(r.pollen);
				s.pollenLvl = (Integer) pr[0];
				s.pollenPlant = (String) pr[1];
				s.aqi = Weather.parseAqi(r.pollen);
			} catch (Exception e) {
				// keep the last pollen
			}
		}
		if (r.warn != null) s.warns = Warnings.parse(r.warn, c.warnNames(), now);
		if (r.quake != null) s.quakes = Fetcher.quakes(this, c);
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
		long since = System.currentTimeMillis() - lastDone;
		// Pulled again and again (A70 log, 4 times in 3 s): one download at a time, and none again
		// within 10 seconds of the last one; the notice says why.
		if (fetching) {
			Log.i(this, "GEST", "reîmprospătare cerută, dar descărcarea e deja în curs");
			note(s.T.en ? "Already updating…" : "Se actualizează deja…");
			return;
		}
		if (since < REPULL_MS) {
			Log.i(this, "GEST", "reîmprospătare cerută la " + since / 1000 + " s după ultima, nu descarc iar");
			note(s.failReason.length() == 0 ? (s.T.en ? "The weather has just been updated" : "Vremea tocmai a fost actualizată")
					: (s.T.en ? "Just tried; try again in a few seconds" : "Tocmai am încercat; mai încearcă peste câteva secunde"));
			return;
		}
		Log.i(this, "GEST", "reîmprospătare cerută");
		note(s.T.en ? "Updating the weather…" : "Actualizez vremea…");
		fetch();
	}

	/** A menu / help icon (Screen.menuIcon) as a bitmap. */
	private android.graphics.Bitmap mini(String which) {
		int px = Math.round(40 * getResources().getDisplayMetrics().density);
		android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888);
		android.graphics.Canvas c = new android.graphics.Canvas(b);
		c.scale(px / 64f, px / 64f);
		AndroidGfx g = new AndroidGfx(getAssets());
		g.c = c;
		s.menuIcon(g, which);
		return b;
	}

	private int bubbleWidth(float frac, int minDp) {
		float d = getResources().getDisplayMetrics().density;
		// From the screen's short side, so the bubble keeps its portrait shape in landscape.
		int shortSide = Math.min(view.getWidth(), view.getHeight());
		return (int) Math.max(minDp * d, shortSide * frac);
	}

	/** Opens the bubble that is open now again (after the phone turns). */
	private Runnable reopen;


	public void onMenu() {
		Log.i(this, "BUTON", "meniu ☰");
		reopen = new Runnable() {
			public void run() {
				onMenu();
			}
		};
		final boolean en = s.T.en;
		boolean keepOn = prefs.getBoolean("keepOn", true), auto = autoLoc();
		// Item ids in menu order, each with its icon; the ones that are also a button on the screen
		// show that button. On / Off name what a tap does.
		final List<String> ids = new ArrayList<String>();
		List<Bubble.Row> rows = Bubble.rows();
		ids.add("refresh"); rows.add(row(en ? "Refresh" : "Reîmprospătare", "refresh"));
		ids.add("city"); rows.add(row(en ? "Town" : "Localitate", "city"));
		ids.add("unit"); rows.add(row(en ? "Temperature °C/°F" : "Temperatura °C/°F", "unit"));
		ids.add("clock"); rows.add(row(en ? "Clock 12/24" : "Ceas 12/24", "clock"));
		ids.add("theme"); rows.add(row(en ? "Theme" : "Temă", "moon"));
		ids.add("keepOn"); rows.add(row(keepOn ? (en ? "Screen on OFF" : "Ecran aprins OFF") : (en ? "Screen on ON" : "Ecran aprins ON"), "keepOn"));
		ids.add("lang"); rows.add(row(en ? "Romanian language" : "Limba engleză", en ? "lang-ro" : "lang-en"));
		ids.add("night"); rows.add(row(prefs.getBoolean("nightClock", true) ? (en ? "Night clock OFF" : "Ceas noapte OFF")
				: (en ? "Night clock ON" : "Ceas noapte ON"), "night"));
		ids.add("loc"); rows.add(row(auto ? (en ? "Location Off" : "Localizare Off") : (en ? "Location On" : "Localizare On"), "locate"));
		ids.add("widget"); rows.add(row(en ? "Widgets" : "Widget-uri", "widget"));
		ids.add("log"); rows.add(row(en ? "Log" : "Jurnal", "log"));
		ids.add("update"); rows.add(row(en ? "Check for updates" : "Verifică actualizări", "update"));
		ids.add("help"); rows.add(row(en ? "Help" : "Ajutor", "help"));
		ids.add("about"); rows.add(row(en ? "About" : "Despre", "about"));
		bubble.show(view.px(s.btnMenu), bubbleWidth(0.3f, 160), null, rows, en, new Bubble.Pick() {
			public void picked(int k) {
				SharedPreferences.Editor e = prefs.edit();
				String it = ids.get(k);
				Log.i(MainActivity.this, "MENIU", it);
				String msg = null;
				long ms = 1500;
				if (it.equals("refresh")) { onRefresh(); return; }
				else if (it.equals("city")) { onCity(s.btnMenu); return; }
				else if (it.equals("unit")) { e.putBoolean("unitF", !s.unitF); msg = s.unitF ? "°C" : "°F"; }
				else if (it.equals("clock")) {
					e.putBoolean("clk12", !s.clk12);
					msg = s.clk12 ? (en ? "24-hour clock" : "Ceas 24 de ore") : (en ? "12-hour clock" : "Ceas 12 ore");
				} else if (it.equals("lang")) { e.putString("lang", en ? "ro" : "en"); msg = en ? "Română" : "English"; }
				else if (it.equals("keepOn")) {
					boolean on = !prefs.getBoolean("keepOn", true);
					e.putBoolean("keepOn", on);
					ms = 2000;
					msg = on ? (en ? "The screen stays on while the app is open" : "Ecranul rămâne aprins cât timp aplicația e deschisă")
							: (en ? "The screen goes off after the phone's timeout" : "Ecranul se stinge după timpul setat în telefon");
				} else if (it.equals("theme")) { pickTheme(); return; }
				else if (it.equals("loc")) {
					onLocate();
					return;
				} else if (it.equals("update")) { updater.check(true); return; }
				else if (it.equals("widget")) { startActivity(new Intent(MainActivity.this, WidgetConfig.class)); return; }
				else if (it.equals("log")) { startActivity(new Intent(MainActivity.this, LogActivity.class)); return; }
				else if (it.equals("help")) { help(); return; }
				else if (it.equals("night")) {
					boolean on = !prefs.getBoolean("nightClock", true);
					e.putBoolean("nightClock", on);
					ms = 2000;
					msg = on ? (en ? "While charging at night, the screen shows a big dim clock" : "La încărcare, noaptea, ecranul arată un ceas mare și estompat")
							: (en ? "Night clock off" : "Ceasul de noapte e oprit");
				}
				else { about(); return; }
				e.apply();
				applySettings();
				view.invalidate();
				if (msg != null) note(msg, ms);
			}
		});
	}

	private Bubble.Row row(String text, String icon) {
		return new Bubble.Row(text, mini(icon), Bubble.Row.ITEM, false);
	}

	/** Light, Dark, and Automatic (the phone's light sensor, when it has one). */
	private void pickTheme() {
		reopen = new Runnable() {
			public void run() {
				pickTheme();
			}
		};
		final boolean en = s.T.en;
		final String[] keys = hasLight() ? new String[] { "light", "dark", "sensor" } : new String[] { "light", "dark" };
		String[] names = en ? new String[] { "Light", "Dark", "Automatic" } : new String[] { "Luminos", "Întunecat", "Automat" };
		String[] icons = { "light", "dark", "sensor" };
		String cur = prefs.getString("theme", "auto");
		List<Bubble.Row> rows = Bubble.rows();
		for (int k = 0; k < keys.length; k++) rows.add(new Bubble.Row(names[k], mini(icons[k]), Bubble.Row.ITEM, keys[k].equals(cur)));
		bubble.show(view.px(s.btnMenu), bubbleWidth(0.3f, 160), en ? "Theme" : "Temă", rows, en, new Bubble.Pick() {
			public void picked(int k) {
				prefs.edit().putString("theme", keys[k]).apply();
				applySettings();
				view.invalidate();
			}
		});
	}

	/** ☰ › Ajutor: every button, gesture and menu option with its icon and what it does. */
	private void help() {
		boolean en = s.T.en;
		help.begin(en ? "Help" : "Ajutor", en ? "Close" : "Închide", s.dark);
		help.section(en ? "Buttons on the screen" : "Butoane pe ecran");
		help.entry(mini("city"), en ? "Town" : "Localitate", en ? "Opens the towns: your last 3 (favourites) and the counties. Tap a county, then the town."
				: "Deschide localitățile: ultimele 3 folosite (favorite) și județele. Alegi județul, apoi localitatea.");
		help.entry(mini("locate"), en ? "Location" : "Localizare", en ? "Finds where you are and shows that town's weather; it looks again every hour. The towns found are kept on the phone with where you were, and the town shown changes only once you are out of its boundary. It works in other countries too, with MeteoAlarm warnings in about 38 European countries. A tap turns automatic location on (the button filled, it looks now) or off (the button empty)."
				: "Găsește unde ești și arată vremea de acolo; caută din nou în fiecare oră. Localitățile găsite rămân în telefon, cu locul exact, iar localitatea afișată se schimbă abia după ce ieși din granițele ei. Merge și în alte țări, cu avertizările MeteoAlarm din circa 38 de țări europene. O atingere pornește localizarea automată (butonul plin, caută acum) sau o oprește (butonul gol).");
		help.entry(mini("bright"), en ? "Brightness" : "Luminozitate", en ? "Shows the brightness bar on the left: slide on it up or down. It goes away by itself."
				: "Arată bara de luminozitate din stânga: glisează pe ea în sus sau în jos. Dispare singură.");
		help.entry(mini("moon"), en ? "Light / dark theme" : "Temă luminoasă / întunecată", en ? "Over the brightness bar: switches between the light and the dark theme."
				: "Deasupra barei de luminozitate: comută între tema luminoasă și cea întunecată.");
		help.entry(mini("sensor"), en ? "Automatic theme (phone sensor)" : "Temă automată (senzor telefon)", en ? "Light theme when there is light, dark theme in the dark. Filled while it is on."
				: "Tema luminoasă când e lumină, cea întunecată pe întuneric. E plin cât timp e pornită.");
		help.section(en ? "Gestures" : "Gesturi");
		help.entry(mini("locate"), en ? "Tap on the town's name" : "Atingerea numelui localității", en
				? "Looks once with the GPS, for a better forecast where you are; automatic location stays as it is."
				: "Caută o dată poziția cu GPS-ul, pentru o prognoză mai bună exact unde ești; localizarea automată rămâne cum era.");
		help.entry(mini("pull"), en ? "Pull down" : "Trage în jos", en ? "Pull the screen down and let go: the weather is refreshed. Pulling again while it downloads, or within 10 seconds of the last download, does not start another one; a notice says so." : "Trage ecranul în jos și dă drumul: vremea se reîmprospătează. Dacă tragi iar cât timp se descarcă sau în 10 secunde de la ultima descărcare, nu pornește încă una; o notificare îți spune asta.");
		help.entry(mini("edge"), en ? "Left edge" : "Marginea din stânga", en ? "Slide up or down near the left edge (a little in from it): the brightness bar appears, and sliding on it sets the brightness."
				: "Glisează sus-jos lângă marginea din stânga (puțin mai înăuntru): apare bara de lumină, iar glisând pe ea reglezi luminozitatea.");
		help.entry(mini("compass"), en ? "Wind arrow" : "Săgeata vântului", en ? "Next to the wind: the arrow shows where the wind blows, turned with the phone (compass); the orange mark is north."
				: "Lângă vânt: săgeata arată încotro bate vântul, rotită după cum ții telefonul (busola); semnul portocaliu e nordul.");
		help.entry(mini("night"), en ? "Night clock" : "Ceasul de noapte", en ? "While the phone charges at night (sunset to sunrise) and nobody touches it for a minute: a big dim clock. A tap goes back for a minute."
				: "Când telefonul se încarcă noaptea (de la apus la răsărit) și nu-l atingi un minut: un ceas mare și estompat. O atingere revine pentru un minut.");
		help.entry(mini("roll"), en ? "Warnings and earthquake" : "Avertizări și cutremur", en
				? "Under the line, one row of boxes: the earthquake first (when there was one), then one box per warning code (red, orange, yellow). Two fit on the screen; with more, the row moves on by itself every 2 seconds and starts again from the first. You can also slide it with the finger. Tap a box: the row stops for 20 seconds and a bubble shows the details in larger letters (for a code: what it means, each warning with its period and advice; for the earthquake: magnitude, time, region, depth, epicentre and distance, how it was likely felt in your town, and all the earthquakes of the last 24 hours)."
				: "Sub linie, un singur rând de casete: întâi cutremurul (dacă a fost unul), apoi câte o casetă pentru fiecare cod de avertizare (roșu, portocaliu, galben). Pe ecran încap două; când sunt mai multe, rândul se mută singur la 2 secunde și o ia de la capăt. Îl poți glisa și cu degetul. Atinge o casetă: rândul se oprește 20 de secunde și un balon arată detaliile, cu litere mai mari (la un cod: ce înseamnă, fiecare avertizare cu perioada și sfaturi; la cutremur: magnitudinea, ora, zona, adâncimea, epicentrul și distanța, cum s-a simțit probabil în localitatea ta și toate cutremurele din ultimele 24 de ore).");
		help.entry(mini("quake"), en ? "Earthquake box" : "Caseta Cutremur", en
				? "From EMSC (European-Mediterranean Seismological Centre): earthquakes of magnitude 3 or more in the last 24 hours in your town's country (the one the location found). The box shows the strongest one: magnitude and time, and how it was felt in your town as an estimate on the EMS-98 scale (from the magnitude and the distance to the focus; EMSC does not publish the reports of each town). It disappears after 24 hours."
				: "De la EMSC (Centrul Seismologic European-Mediteranean): cutremurele cu magnitudinea 3 sau mai mare din ultimele 24 de ore din țara localității tale (cea găsită de localizare). Caseta îl arată pe cel mai puternic: magnitudinea și ora, și cum s-a simțit în localitatea ta, ca estimare pe scara EMS-98 (după magnitudine și distanța până la focar; EMSC nu publică mărturiile pentru fiecare localitate). Dispare după 24 de ore.");
		help.entry(mini("swipe"), en ? "Left / right on the boxes" : "Stânga / dreapta pe casete", en ? "The warnings row, the hour boxes and the five days (tomorrow and the next 4 days, two at a time) follow the finger. The Precipitation · next 12 hours chart comes first in the hours row, only when rain or snow is expected."
				: "Rândul de avertizări, casetele cu ore și cele 5 zile (Mâine și încă 4 zile, câte două) urmează degetul. Graficul Precipitații · următoarele 12 ore apare primul în rândul cu ore, doar când e prognozată ploaie sau ninsoare.");
		help.entry(mini("swipe"), en ? "The row of boxes at the bottom" : "Rândul de casete de jos", en
				? "Slide it sideways (in landscape: up and down, two by two, without the wind box): Sun (the Moon after sunset, with its rise, set and phase), UV index, Pollen, Humidity (while it rains), Air quality, Pressure, Wind, Visibility and Compass."
				: "Glisează-l stânga-dreapta (în peisaj: sus-jos, câte două, fără caseta Vânt): Soare (după apus Luna, cu răsăritul, apusul și faza), Indice UV, Polen, Umiditate (când plouă), ICA (calitatea aerului), Presiune, Vânt, Vizibilitate și Busolă.");
		help.section(en ? "Home-screen widgets" : "Widget-uri pe ecranul de pornire");
		help.entry(mini("widget"), en ? "Meteo Alarm widgets" : "Widget-urile Meteo Alarm", en
				? "Hold a free spot on the home screen, choose Widgets, then Meteo Alarm: 2x2, 4x1, 4x2 and \"any size\", each with its preview; it goes on the screen at once, without a settings window. There are three layouts: 2x2 (square: icon, temperature, town, next sunset or sunrise), 4x1 (a low strip, also with the sky) and 4x2 (wide, also with high / low and the next 5 hours), plus a small 2x1 (icon and temperature) for frames too small for them. Hold it and drag its edges: wide (about 4 columns) and one row = 4x1, wide and taller = 4x2 (it stays 4x2 however tall you make it), narrow = 2x2, narrow and one row = 2x1. It fills the frame's width; when the frame is taller than the layout needs, the widget keeps its size, centred, with the background only behind it (the rest stays see-through). A layout is never shrunk so much that the texts get too small. With active warnings a row \"WARNING!\" shows three pills: yellow = gray gray yellow, orange = gray orange orange, red = all red; two codes = the worse one, gray, the other; three codes = red orange yellow. A tap opens the app."
				: "Ține apăsat pe un loc liber de pe ecranul de pornire, alege Widgeturi, apoi Meteo Alarm: 2x2, 4x1, 4x2 și „orice mărime”, fiecare cu imaginea lui; se pune direct pe ecran, fără fereastră de setări. Sunt trei modele: 2x2 (pătrat: icon, temperatură, localitate, următorul apus sau răsărit), 4x1 (bandă joasă, și cu descrierea cerului) și 4x2 (lat, și cu Max / Min și următoarele 5 ore), plus unul mic, 2x1 (icon și temperatură), pentru rame prea mici pentru celelalte. Ține-l apăsat și trage de margini: lat (cam 4 coloane) și un rând = 4x1, lat și mai înalt = 4x2 (rămâne 4x2 oricât de înalt îl faci), îngust = 2x2, îngust și un rând = 2x1. Umple lățimea ramei; când rama e mai înaltă decât are nevoie modelul, widget-ul își păstrează mărimea, centrat, cu fundal doar sub el (restul rămâne transparent). Un model nu se micșorează niciodată atât încât textele să devină prea mici. Când sunt avertizări, rândul „AVERTIZARE!” are trei pastile: galben = gri gri galben, portocaliu = gri portocaliu portocaliu, roșu = toate roșii; două coduri = cel mai grav, gri, celălalt; trei coduri = roșu portocaliu galben. O atingere deschide aplicația.");
		help.entry(mini("widget"), en ? "Widget settings" : "Setările widget-ului", en
				? "Hold the widget › Settings (Android 12 and newer), or ☰ › Widgets. Each widget has its own: a preview; the town (as in the app, or another one with Change); Background on / off and how see-through; Colours: by the weather (blue sky, night blue, gray clouds, rain, icy snow, storm, fog), matching the phone's settings, or dark; Text colour: automatic (black on a light background), white or black. Cancel or Save."
				: "Ține apăsat pe widget › Setări (Android 12 și mai nou) sau ☰ › Widget-uri. Fiecare widget are setările lui: previzualizare; localitatea (ca în aplicație sau alta, cu Schimbare); Fundal pornit / oprit și cât de transparent; Culori: după vreme (cer albastru, albastru de noapte, gri pentru nori, ploaie, zăpadă, furtună, ceață), potrivite cu setările telefonului sau închise; Culoare font: automat (negru pe fundal deschis), alb sau negru. Anulare sau Salvare.");
		help.entry(mini("refresh"), en ? "Refreshing by itself" : "Reîmprospătare automată", en
				? "The weather, the warnings and the earthquakes come by themselves, also with the app closed (for the widgets): every 15 minutes while the screen is on, every 30 minutes with the screen off, not at all between sunset and sunrise while the screen is off (it waits for the screen to come on), and every hour when the battery is below 25% and not charging. When a download fails, a notice and the line \"! no data since …\" under the clock say why (no internet, the server did not answer, secure connection error, server error…). When Open-Meteo is busy (HTTP 503 \"overloaded\") or does not answer, the forecast comes from MET Norway (api.met.no, the Norwegian weather institute) and the line under the clock says \"· MET Norway\"; after a \"busy\" the app goes straight to MET Norway for 20 minutes. Until new data comes, the last saved weather stays on the screen."
				: "Vremea, avertizările și cutremurele vin singure, și cu aplicația închisă (pentru widget-uri): la 15 minute cât ecranul e aprins, la 30 de minute cu ecranul stins, deloc între apus și răsărit cât ecranul e stins (așteaptă să se aprindă ecranul) și la o oră când bateria e sub 25% și nu se încarcă. Când descărcarea eșuează, o notificare și rândul „! fără date din …” de sub ceas spun de ce (fără internet, serverul nu a răspuns, eroare conexiune securizată, eroare server…). Când Open-Meteo e supraîncărcat (HTTP 503) sau nu răspunde, prognoza vine de la MET Norway (api.met.no, institutul meteo norvegian), iar rândul de sub ceas scrie „· MET Norway”; după un „supraîncărcat”, aplicația merge direct la MET Norway 20 de minute. Până vin date noi, pe ecran rămâne ultima vreme salvată.");
		help.section(en ? "The ☰ menu" : "Meniul ☰");
		help.entry(mini("refresh"), en ? "Refresh" : "Reîmprospătare", en ? "Downloads the weather, the warnings and the earthquakes now." : "Descarcă acum vremea, avertizările și cutremurele.");
		help.entry(mini("city"), en ? "Town" : "Localitate", en ? "The same as the town button." : "La fel ca butonul de localitate.");
		help.entry(mini("unit"), en ? "Temperature °C/°F" : "Temperatura °C/°F", en ? "Switches between Celsius and Fahrenheit." : "Comută între grade Celsius și Fahrenheit.");
		help.entry(mini("clock"), en ? "Clock 12/24" : "Ceas 12/24", en ? "Switches the clock between 24 hours and 12 hours (AM/PM)." : "Comută ceasul între 24 de ore și 12 ore (AM/PM).");
		help.entry(mini("moon"), en ? "Theme" : "Temă", en ? "Light, Dark, or Automatic (phone sensor)." : "Luminos, Întunecat sau Automat (senzor telefon).");
		help.entry(mini("keepOn"), en ? "Screen on ON / OFF" : "Ecran aprins ON / OFF", en ? "ON: the screen stays on while the app is open. OFF: it goes off after the phone's timeout."
				: "ON: ecranul rămâne aprins cât timp aplicația e deschisă. OFF: se stinge după timpul setat în telefon.");
		help.entry(mini(en ? "lang-ro" : "lang-en"), en ? "Language" : "Limba", en ? "Romanian or English." : "Română sau engleză.");
		help.entry(mini("locate"), en ? "Location On / Off" : "Localizare On / Off", en ? "Turns automatic location on or off (the same as the location button)."
				: "Pornește sau oprește localizarea automată (la fel ca butonul de localizare).");
		help.entry(mini("night"), en ? "Night clock ON / OFF" : "Ceas noapte ON / OFF", en ? "Turns the night clock on or off." : "Pornește sau oprește ceasul de noapte.");
		help.entry(mini("widget"), en ? "Widgets" : "Widget-uri", en ? "The settings of a widget on the home screen (with several, choose which first)."
				: "Setările unui widget de pe ecranul de pornire (când sunt mai multe, alegi întâi care).");
		help.entry(mini("log"), en ? "Log" : "Jurnal", en ? "Everything the app did, with the time: the sensors, the taps and gestures, the location, the data downloads (and why one failed), the refreshes in the background and the widgets (the size the phone gives them, the layout shown). It keeps 5 days, a new file each day; the errors of older days stay 10 more days in an Errors section. At the top: the phone (model, Android, One UI) and the app's version. Read it here, Share it (WhatsApp, Drive, e-mail…) or Save it on the phone."
				: "Tot ce a făcut aplicația, cu ora: senzorii, atingerile și gesturile, localizarea, descărcările de date (și de ce a eșuat una), reîmprospătările din fundal și widget-urile (mărimea dată de telefon, modelul arătat). Păstrează 5 zile, câte un fișier pe zi; erorile din zilele mai vechi rămân încă 10 zile în rubrica Erori. Sus: telefonul (model, Android, One UI) și versiunea aplicației. Îl citești aici, îl Trimiți (WhatsApp, Drive, e-mail…) sau îl Salvezi pe telefon.");
		help.entry(mini("update"), en ? "Check for updates" : "Verifică actualizări", en ? "Looks for a new version on GitHub; it also looks once a week by itself. The updates come from GitHub (not Google Play), so the first time the app explains this and asks for your consent: with \"I agree\" it looks for updates; with \"Not now\" it does not look and downloads nothing until you choose Check for updates again and agree. Even then, nothing is installed until you tap Update and confirm in the phone's installer."
				: "Caută o versiune nouă pe GitHub; caută singură și o dată pe săptămână. Actualizările vin de pe GitHub (nu din Google Play), așa că prima dată aplicația explică asta și îți cere acordul: cu „Sunt de acord” caută actualizări; cu „Nu acum” nu caută și nu descarcă nimic, până când alegi din nou Verifică actualizări și îți dai acordul. Chiar și atunci nu se instalează nimic până nu apeși Actualizează și confirmi în programul de instalare al telefonului.");
		help.entry(mini("help"), en ? "Help" : "Ajutor", en ? "This page." : "Această pagină.");
		help.entry(mini("about"), en ? "About" : "Despre", en ? "About the app, the data it uses, and what's new in each version."
				: "Despre aplicație, datele pe care le folosește și ce e nou în fiecare versiune.");
		help.show();
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

	/**
	 * The town picker as a bubble next to the button at B: "Favourites" with the last three towns,
	 * a line, then "Choose the county" and the counties; a county opens its towns. At most half the
	 * screen high; the rows scroll.
	 */
	public void onCity(final float[] b) {
		Log.i(this, "BUTON", "localitate");
		reopen = new Runnable() {
			public void run() {
				onCity(b);
			}
		};
		final List<Cities.City> favs = new ArrayList<Cities.City>();
		for (String k : prefs.getString("favs", "").split("\n")) {
			Cities.City c = Cities.City.fromKey(k);
			if (c != null) favs.add(c);
		}
		final boolean en = s.T.en;
		final String[] counties = cities.byCounty.keySet().toArray(new String[0]);
		List<Bubble.Row> rows = Bubble.rows();
		final int first;
		if (!favs.isEmpty()) {
			rows.add(new Bubble.Row(en ? "Favourites" : "Favorite", null, Bubble.Row.HEADER, false));
			for (Cities.City c : favs) rows.add(Bubble.Row.item(c.label()));
			rows.add(new Bubble.Row("", null, Bubble.Row.LINE, false));
		}
		rows.add(new Bubble.Row(en ? "Choose the county" : "Alegeți județul", null, Bubble.Row.HEADER, false));
		first = rows.size();
		for (String co : counties) rows.add(Bubble.Row.item(co));
		// Other countries, by name: the starting towns and the ones found by the location.
		final List<String> ccs = new ArrayList<String>(cities.abroad.keySet());
		java.util.Collections.sort(ccs, new java.util.Comparator<String>() {
			public int compare(String a, String c) {
				return Warnings.fold(countries.name(a, en)).compareTo(Warnings.fold(countries.name(c, en)));
			}
		});
		rows.add(new Bubble.Row("", null, Bubble.Row.LINE, false));
		rows.add(new Bubble.Row(en ? "Other countries" : "Alte țări", null, Bubble.Row.HEADER, false));
		final int firstC = rows.size();
		for (String cc : ccs) rows.add(Bubble.Row.item(countries.name(cc, en)));
		bubble.show(view.px(b), bubbleWidth(0.9f, 300), null, rows, en, new Bubble.Pick() {
			public void picked(int k) {
				if (k >= firstC) pickAbroad(b, ccs.get(k - firstC));
				else if (k >= first && k < first + counties.length) pickTown(b, counties[k - first]);
				else if (k >= 1 && k <= favs.size()) choose(favs.get(k - 1));
			}
		});
	}

	/** The towns of another country (CC): the starting ones and the ones found by the location. */
	private void pickAbroad(final float[] b, final String cc) {
		reopen = new Runnable() {
			public void run() {
				pickAbroad(b, cc);
			}
		};
		final List<Cities.City> l = new ArrayList<Cities.City>(cities.abroad.get(cc));
		java.util.Collections.sort(l, new java.util.Comparator<Cities.City>() {
			public int compare(Cities.City a, Cities.City c) {
				return Warnings.fold(a.name).compareTo(Warnings.fold(c.name));
			}
		});
		final boolean en = s.T.en;
		List<Bubble.Row> rows = Bubble.rows();
		rows.add(Bubble.Row.item(en ? "‹  Back" : "‹  Înapoi"));
		for (Cities.City c : l) rows.add(Bubble.Row.item(c.name));
		bubble.show(view.px(b), bubbleWidth(0.9f, 300), countries.name(cc, en), rows, en, new Bubble.Pick() {
			public void picked(int k) {
				if (k == 0) onCity(b);
				else choose(l.get(k - 1));
			}
		});
	}

	private void pickTown(final float[] b, final String county) {
		reopen = new Runnable() {
			public void run() {
				pickTown(b, county);
			}
		};
		final List<Cities.City> l = cities.byCounty.get(county);
		final boolean en = s.T.en;
		List<Bubble.Row> rows = Bubble.rows();
		rows.add(Bubble.Row.item(en ? "‹  Back to the counties" : "‹  Înapoi la județe"));
		for (Cities.City c : l) rows.add(Bubble.Row.item(c.name));
		bubble.show(view.px(b), bubbleWidth(0.9f, 300), county, rows, en, new Bubble.Pick() {
			public void picked(int k) {
				if (k == 0) onCity(b);
				else choose(l.get(k - 1));
			}
		});
	}

	/** A town picked from the list: automatic location goes off until the locate button. */
	private void choose(Cities.City c) {
		setAutoLoc(false);
		setCity(c, true);
		setDistrict("");
		fetch();
	}

	/** The shown town, kept in the settings and first among the favourites (the last three). */
	private void setCity(Cities.City c, boolean clear) {
		Log.i(this, "LOC", "localitatea: " + c.label() + " (" + c.lat + ", " + c.lon + ")");
		StringBuilder favs = new StringBuilder(c.key());
		int n = 1;
		for (String k : prefs.getString("favs", "").split("\n")) {
			Cities.City f = Cities.City.fromKey(k);
			if (n >= 3 || f == null || f.same(c)) continue;
			favs.append('\n').append(k);
			n++;
		}
		prefs.edit().putString("city", c.key()).putString("favs", favs.toString()).apply();
		boolean same = c.same(s.city);
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
