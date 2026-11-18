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
import android.os.Build;
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

import ro.meteoalarm.android.core.Tr;
import ro.meteoalarm.android.core.Area;
import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Visits;
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
	static final String VERSION = "Meteo Alarm Android v2.103";
	/** v5.25: the widget opened the app (its windows close, the town's main screen shows). */
	static final String FROM_WIDGET = "fromWidget";
	/** v2.030: an earthquake notice opened the app: its bubble with the map shows. */
	static final String OPEN_QUAKE = "openQuake";
	private android.graphics.Typeface fReg, fBold;
	/** v2.030: the earthquake's bubble is waiting to be opened (a tap on its notice). */
	private boolean quakeWanted;
	private static final long WEATHER_MS = Refresher.SCREEN_ON_MS, LOW_BATTERY_MS = Refresher.LOW_BATTERY_MS;
	/** The app is on the screen (the refresh in the background then leaves the work to it). */
	static volatile boolean onScreen;

	private WeatherView view;
	private Screen s;
	private Cities cities;
	private SharedPreferences prefs;
	private final Handler handler = new Handler();
	private boolean fetching, asked;
	/**
	 * Farmer mode (v2.60): the town (the location's or the one picked, kept as "city") and the
	 * main screen shown: 0 the town's, else a parcel's id (kept as "farmPage").
	 */
	private Cities.City town;
	private long pageId;
	private boolean townFetching;
	private static final int REQ_PARCELS = 21, REQ_SAVE_DATA = 22, REQ_OPEN_DATA = 23;
	private long lastTry, lastLocate, lastDone;
	static final long REPULL_MS = 10000;
	private Locator locator;
	private Updater updater;
	private BottomSheet sheet;
	private Bubble bubble;
	/** v2.020 (Viorel): the parcel's Statistics inside the main screen. */
	private android.widget.FrameLayout statsBox;
	private StatsPage stats;
	/** The season downloads tried in this run (once a day per parcel and sowing date). */
	private final java.util.Set<String> seasonTried = new java.util.HashSet<String>();
	private long seasonLoading = -1;
	private Countries countries;
	private HelpPanel help;
	/** v4.00: the big compass over the screen (a tap on the Compass box). */
	private CompassPanel compassPanel;
	/** v5.00: the ☰ Settings window. */
	private SettingsPanel settings;
	/** The text-size bar over the pages (SizeOverlay, v2.12). */
	private SizeOverlay sizer;
	/** v5.00: the blurred, darkened screen behind every open window (Glass). */
	private Glass.Backdrop backdrop;
	private static final long LOCATE_MS = 60 * 60000L;

	private final Runnable tick = new Runnable() {
		public void run() {
			s.now = ro.meteoalarm.android.core.Zone.now();
			showNet();
			s.offline = s.fetched > 0 && System.currentTimeMillis() - s.fetched > 2 * interval() + 5 * 60000L;
			long iv = s.w == null ? 60000L : interval();
			if (System.currentTimeMillis() - Math.max(s.fetched, lastTry) >= iv) fetch();
			// A parcel shown (v2.60): the town's weather still comes, for the widgets and the notices.
			if (pageId != 0 && System.currentTimeMillis() - prefs.getLong("fetched", 0) >= interval()) fetchTownQuiet();
			// Farmer data without the last 24 hours (saved by v2.50, or not come): asked again, at most every 5 minutes (v2.61).
			// v3.11: also data saved before v3.10 (without the daily ET0, CAPE, the 16 days): the new boxes stayed empty until the next download.
			boolean oldFarm = s.farm != null && (s.farm.dEt0 == null || s.farm.dEt0.length == 0 || s.farm.cape == null);
			if (s.farmer && s.w != null && (Double.isNaN(s.rain24()) || oldFarm) && System.currentTimeMillis() - lastFarmTry >= 5 * 60000L) fetchFarm();
			usFarm();
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
		// v3.11 (Viorel): the right-edge text-size bar is gone; a size set by it by mistake (it also grew the ☰ menu) goes back to 100% once.
		if (!prefs.getBoolean("textReset311", false)) {
			Widget.prefs(this).edit().remove("textScale").apply();
			prefs.edit().putBoolean("textReset311", true).apply();
		}
		view = new WeatherView(this, this);
		s = view.screen;
		try {
			cities = new Cities(readAll(getAssets().open("cities.txt")));
		} catch (Exception e) {
			cities = new Cities("");
		}
		try {
			cities.addWorld(readAll(getAssets().open("world.txt")));
			// The United States (v2.30): towns of every state.
			cities.addUS(readAll(getAssets().open("us.txt")));
			countries = new Countries(readAll(getAssets().open("countries.txt")));
		} catch (Exception e) {
			countries = new Countries("");
		}
		// Towns found by the location, kept on the phone; v4.20 (Viorel): a place not in the app's
		// own list (a village) goes after 7 days without passing there, unless passed there often.
		for (List<Cities.City> l : cities.byCounty.values()) for (Cities.City c : l) builtIn.add(Visits.key(c));
		// v4.40: abroad and in the US too (the towns of world.txt and us.txt stay).
		for (List<Cities.City> l : cities.abroad.values()) for (Cities.City c : l) builtIn.add(Visits.key(c));
		for (List<Cities.City> l : cities.usStates.values()) for (Cities.City c : l) builtIn.add(Visits.key(c));
		Places.tidy(this);
		cleanNeighbourhoods();
		expirePlaces();
		for (String k : prefs.getString("myCities", "").split("\n")) {
			Cities.City c = Cities.City.fromKey(k);
			if (c != null) cities.put(c);
		}
		checkSensors();
		locator = new Locator(this);
		// The town from the phone's geocoder (the neighbourhood option is gone).
		locator.osm = false;
		sheet = new BottomSheet(this);
		updater = new Updater(this, sheet);
		pageId = prefs.getLong("farmPage", 0);
		applySettings();
		loadPage();
		android.widget.FrameLayout root = new android.widget.FrameLayout(this);
		root.addView(view);
		statsBox = new android.widget.FrameLayout(this);
		statsBox.setVisibility(View.GONE);
		root.addView(statsBox, new android.widget.FrameLayout.LayoutParams(0, 0));
		root.addView(sheet);
		quakeWanted = getIntent() != null && getIntent().getBooleanExtra(OPEN_QUAKE, false);
		fReg = android.graphics.Typeface.createFromAsset(getAssets(), "fonts/LiberationSans-Regular.ttf");
		fBold = android.graphics.Typeface.createFromAsset(getAssets(), "fonts/LiberationSans-Bold.ttf");
		bubble = new Bubble(this, fReg, fBold);
		bubble.setDark(s.dark);
		bubble.setOnClose(new Runnable() {
			public void run() {
				hideBars();
				// v5.00: a window opened from the Settings (town, language, test state): back to the Settings, as they are now.
				if (settings != null && settings.isOpen()) view.post(new Runnable() {
					public void run() {
						if (settings.isOpen() && !bubble.isOpen()) onMenu();
					}
				});
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
		compassPanel = new CompassPanel(this, android.graphics.Typeface.createFromAsset(getAssets(), "fonts/LiberationSans-Regular.ttf"),
				android.graphics.Typeface.createFromAsset(getAssets(), "fonts/LiberationSans-Bold.ttf"));
		compassPanel.setOnClose(new Runnable() {
			public void run() {
				hideBars();
			}
		});
		root.addView(compassPanel, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		settings = new SettingsPanel(this, android.graphics.Typeface.createFromAsset(getAssets(), "fonts/LiberationSans-Regular.ttf"),
				android.graphics.Typeface.createFromAsset(getAssets(), "fonts/LiberationSans-Bold.ttf"));
		settings.setOnClose(new Runnable() {
			public void run() {
				reopen = null;
				langMore = false;
				hideBars();
			}
		});
		root.addView(settings, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		// The text-size bar over the pages (v2.12): above every window, on the left like the brightness bar.
		sizer = new SizeOverlay(this, view.screen);
		root.addView(sizer, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		help.setSizer(sizer);
		sheet.setSizer(sizer);
		backdrop = new Glass.Backdrop(this, view, sheet, bubble, help, compassPanel, settings);
		backdrop.setDark(s.dark);
		root.addView(backdrop, 2, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		backdrop.watch(root);
		cutout(root);
		setContentView(root);
	}

	/**
	 * v3.61 (Viorel): the page reaches up into the band of the camera (the hidden status bar was a
	 * black band there): the window uses the cutout's edges (Android 9+), the page's colour fills
	 * the band and the content starts right under the camera.
	 */
	private int lastBand = -1;
	private int cutLogged = -1;

	private void cutout(final android.widget.FrameLayout root) {
		if (Build.VERSION.SDK_INT < 28) return;
		try {
			WindowManager.LayoutParams lp = getWindow().getAttributes();
			// LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES = 1
			WindowManager.LayoutParams.class.getField("layoutInDisplayCutoutMode").setInt(lp, 1);
			getWindow().setAttributes(lp);
			root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
				public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets in) {
					int l = 0, t = 0, r = 0;
					try {
						Object dc = in.getClass().getMethod("getDisplayCutout").invoke(in);
						if (dc != null) {
							l = (Integer) dc.getClass().getMethod("getSafeInsetLeft").invoke(dc);
							t = (Integer) dc.getClass().getMethod("getSafeInsetTop").invoke(dc);
							r = (Integer) dc.getClass().getMethod("getSafeInsetRight").invoke(dc);
						}
					} catch (Exception e) {
					}
					// v3.70 (Viorel): in portrait on a phone the band of the camera holds the clock,
					// the connection and the date (WeatherView draws them); the panels stay below it.
					android.content.res.Configuration cf = getResources().getConfiguration();
					int band = t > 0 && cf.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT && cf.smallestScreenWidthDp < 600 ? t : 0;
					// v3.91 (Viorel): the status bar is hidden, so its room goes to the page (only the camera stays free).
					@SuppressWarnings("deprecation")
					int stable = in.getStableInsetTop();
					int pt = band > 0 ? 0 : t;
					if (cutLogged != t * 10000 + stable) {
						cutLogged = t * 10000 + stable;
						Log.i(MainActivity.this, "APP", "margini ecran: decupaj sus " + t + " px, bara de stare " + stable + " px" + (band > 0 ? " -> banda camerei folosită" : ""));
					}
					if (v.getPaddingTop() != pt || v.getPaddingLeft() != l || v.getPaddingRight() != r || band != lastBand) {
						v.setPadding(l, pt, r, 0);
						lastBand = band;
						view.band(band);
						View[] panels = { sheet, help, sizer, settings };
						for (View p : panels) {
							if (p == null || !(p.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) continue;
							ViewGroup.MarginLayoutParams mp = (ViewGroup.MarginLayoutParams) p.getLayoutParams();
							if (mp.topMargin != band) {
								mp.topMargin = band;
								p.setLayoutParams(mp);
							}
						}
						Log.i(MainActivity.this, "APP", "decupaj cameră: stânga " + l + ", sus " + t + ", dreapta " + r + " px" + (band > 0 ? "; ceas, rețea și dată în banda camerei" : ""));
					}
					return in;
				}
			});
		} catch (Exception e) {
			Log.w(this, "APP", "decupaj cameră indisponibil: " + e);
		}
	}

	@Override
	public void onBackPressed() {
		if (sizer != null && sizer.isOpen()) sizer.hide();
		else if (compassPanel != null && compassPanel.isOpen()) compassPanel.hide();
		else if (bubble.isOpen()) bubble.hide();
		else if (settings != null && settings.isOpen()) settings.hide();
		else if (help.isOpen()) help.hide();
		else if (sheet.isOpen()) {
			if (sheet.isCancelable()) sheet.cancel();
		} else if (s.statsOn) {
			if (stats == null || !stats.closeSheet()) closeStats();
		} else if (s.quakeOn) closeQuakeInfo();
		else super.onBackPressed();
	}

	/** About the app: shown once at the first start (before the location question) and from ☰ › Despre. */
	private String aboutText() {
		boolean en = s.T.en;
		return Updater.howTo(en) + "\n\n"
				+ Tr.t("This is a test version of Meteo Alarm Android.", "Aceasta este o versiune de test a Meteo Alarm Android.") + "\n\n"
				+ Tr.t("No data collected: no account, no ads, no statistics. To get the weather, only the place's coordinates are sent to Open-Meteo (weather, pollen) and, when Open-Meteo is overloaded, to MET Norway (weather); the warnings come from MeteoAlarm, the earthquakes from EMSC (seismicportal.eu).",
						"Nu colectăm date: fără cont, fără reclame, fără statistici. Pentru vreme se trimit doar coordonatele localității către Open-Meteo (vremea, polenul) și, când Open-Meteo e supraîncărcat, către MET Norway (vremea); avertizările vin de la MeteoAlarm (ANM), cutremurele de la EMSC (seismicportal.eu).") + "\n\n"
				+ (Region.showUS(this) ? Tr.t("In the United States the warnings come from the National Weather Service (api.weather.gov) and the earthquakes from USGS (earthquake.usgs.gov); they get only the town's coordinates too.",
						"În Statele Unite avertizările vin de la National Weather Service (api.weather.gov), iar cutremurele de la USGS (earthquake.usgs.gov); și ele primesc doar coordonatele localității.") + "\n\n" : "")
				+ Tr.t("Location: used to show the weather where you are, at start and once an hour. To name the town, the coordinates go to OpenStreetMap (or the phone's geocoder), which also gives the town's boundary. Towns found, with where you were, are kept only on the phone. The app asks for it with an explanation at the first start; if you say no, you choose the town from the list and it asks again only after an update.",
						"Locația: o folosim ca să arătăm vremea acolo unde ești, la pornire și o dată pe oră. Pentru numele localității, coordonatele merg la OpenStreetMap (sau la geocoderul telefonului), de unde vin și granițele localității. Localitățile găsite, cu locul exact, rămân doar pe telefon. Aplicația o cere cu o explicație la prima pornire; dacă refuzi, alegi localitatea din listă și te mai întreabă doar după o actualizare.") + "\n\n"
				+ Tr.t("Notifications (only if you agree): rain or snow starting, new weather warnings for your town, earthquakes of magnitude 3 or more in your country. Each event once. Turn them on or off in ☰ › Notifications.",
						"Notificări (doar cu acordul tău): ploaie sau ninsoare care începe, avertizări meteo noi pentru localitatea ta, cutremure cu magnitudinea 3 sau mai mare în țara ta. Fiecare eveniment o dată. Le pornești sau oprești din ☰ › Notificări.") + "\n\n"
				+ Tr.t("Language: at the first start the phone's language (when the app has it, else English), then the one chosen in ☰ › Language.",
						"Limba: la prima pornire limba telefonului (dacă aplicația o are, altfel engleza), apoi cea aleasă în ☰ › Limba.") + "\n\n"
				+ Tr.t("Weather icons: Classic: “Coloured weather icons collection”, titusurya (magnific.com), free vectors. Glossy, Flat, Realistic, Atmospheric and Outline: made with Google Gemini, adapted for the app.", "Iconurile vremii: Clasic: “Coloured weather icons collection”, titusurya (magnific.com), vectori gratuiți. Lucios, Plat, Realist, Atmosferic și Contur: create cu Google Gemini, adaptate pentru aplicație.") + "\n\n"
				+ Tr.t("Sensors: the app reads the battery state (night clock while charging), the phone's light sensor with ☰ › Theme › Automatic (phone sensor), and the compass, which turns the wind arrow with the phone. What the phone does not have is turned off. Nothing leaves the phone.",
						"Senzori: aplicația citește starea bateriei (ceasul de noapte la încărcare), senzorul de lumină al telefonului cu ☰ › Temă › Automat (senzor telefon) și busola, care rotește săgeata vântului după telefon. Ce nu are telefonul se dezactivează. Nimic nu pleacă de pe telefon.") + "\n\n"
				+ Tr.t("Updates: ", "Actualizări: ") + Updater.howTo(en) + " "
				+ Tr.t("The app checks only when you tap Check for updates; Download opens the new version in the browser and you install it from the downloaded file.",
						"Aplicația verifică doar când apeși Verifică actualizări; Descarcă deschide versiunea nouă în browser, iar tu o instalezi din fișierul descărcat.") + "\n\n"
				+ Updater.protectNote(en) + "\n\n" + Updater.autoBlocker();
	}

	private void welcome() {
		sheet.show(VERSION, aboutText(), Tr.t("I understand", "Am înțeles"), new Runnable() {
			public void run() {
				prefs.edit().putBoolean("welcomed", true).putString("seenVersion", updater.installed()).apply();
				consents();
			}
		}, null);
	}

	/** With the app open the screen is on: every 15 minutes, every hour below 25% battery (not charging). */
	private long interval() {
		if (Refresher.storm(this)) return Refresher.STORM_MS;
		return s.batt >= 0 && s.batt < Refresher.LOW_BATTERY && !s.charging ? LOW_BATTERY_MS : WEATHER_MS;
	}

	private void applySettings() {
		s.T = new Texts(Lang.apply(this));
		Cities.City c = Cities.City.fromKey(prefs.getString("city", ""));
		if (c == null) c = cities.find("Călărași");
		if (c == null) c = new Cities.City("Călărași", "Călărași", 44.2058, 27.3306);
		// Keep the town shown even when it was never picked, so the widget finds it too.
		if (prefs.getString("city", "").length() == 0) prefs.edit().putString("city", c.key()).apply();
		town = c;
		pageState();
		applyUnits();
		// No neighbourhood after the town any more (Viorel 2026-11-02).
		s.district = "";
		// Theme: "light" (Kindle), "dark" (Claude colours), "auto" (follows the phone's dark mode) or
		// "sensor" (dark when the room is dark, from the light sensor).
		ro.meteoalarm.android.core.Theme.pal = prefs.getInt("palette", 0);
		ro.meteoalarm.android.core.WxIcon.set = Fetcher.iconTheme(prefs); // v5.20
		String theme = prefs.getString("theme", "auto");
		boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
		s.dark = theme.equals("dark") || (theme.equals("auto") && night) || (theme.equals("sensor") && roomDark);
		listenLight(theme.equals("sensor") && resumed);
		applyBrightness();
		s.autoLoc = autoLoc();
		s.sensorTheme = theme.equals("sensor");
		barColours();
		view.setBackgroundColor(ro.meteoalarm.android.core.Theme.page(s.dark));
		hideBars();
		getWindow().getDecorView().setBackgroundColor(ro.meteoalarm.android.core.Theme.page(s.dark));
		if (prefs.getBoolean("keepOn", true)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else if (!s.nightClock) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if (sheet != null) sheet.setDark(s.dark);
		if (bubble != null) bubble.setDark(s.dark);
		if (backdrop != null) backdrop.setDark(s.dark);
		if (updater != null) updater.en = s.T.en;
	}

	// --- Main screens of farmer mode (v2.60) --------------------------------------------

	/** Parcel P as a place: its name, the nearest town's country and county (for the warnings). */
	private Cities.City placeOf(Parcels.Parcel p) {
		Cities.City n = cities.nearest(p.lat, p.lon);
		if (n == null) n = town;
		String name = p.name.replace('|', '/');
		return new Cities.City(n.country, n.county, name, p.lat, p.lon, n.match);
	}

	/** The parcels in the screens' order (none outside farmer mode). */
	private List<Parcels.Parcel> pageList() {
		return prefs.getBoolean("farmer", false) ? Parcels.sorted(this) : new ArrayList<Parcels.Parcel>();
	}

	/** The place, the name and the dots of the main screen PAGE_ID (the town's when it is gone). */
	private void pageState() {
		List<Parcels.Parcel> l = pageList();
		Parcels.Parcel p = null;
		int idx = 0;
		for (int k = 0; k < l.size(); k++) if (l.get(k).id == pageId) {
			p = l.get(k);
			idx = k + 1;
		}
		if (p == null && pageId != 0) {
			pageId = 0;
			prefs.edit().putLong("farmPage", 0).apply();
		}
		s.pages = l.isEmpty() ? 1 : l.size() + 1;
		s.page = idx;
		// v5.30: the tabs: the town, then the parcels (a "+" follows them).
		String[] tn = new String[l.size() + 1];
		tn[0] = town == null ? "" : town.name;
		for (int k = 0; k < l.size(); k++) tn[k + 1] = l.get(k).name.replace('|', '/');
		s.tabNames = tn;
		s.parcel = p == null ? null : p.name;
		s.city = p == null ? town : placeOf(p);
		usFarm();
	}

	/** v3.10: in farmer mode, in the US, the NWS grid, the drought and the storm reports of the place shown. */
	private void usFarm() {
		if (!s.farmer || s.city == null || !s.city.us()) {
			s.us = null;
			return;
		}
		s.us = UsFetch.get(s.city.lat, s.city.lon);
		UsFetch.refresh(this, s.city.lat, s.city.lon, new Runnable() {
			public void run() {
				view.invalidate();
			}
		});
	}

	/** Shows main screen ID (0 the town's, else a parcel's) with what is saved, and downloads when old. */
	private void showPage(long id) {
		showPage(id, false);
	}

	/**
	 * Main screen ID. QUIET (v2.90, while a finger slides the screens): only its saved data are
	 * shown, it is not remembered, logged or downloaded until the finger lets it stay (pageDone).
	 */
	private void showPage(long id, boolean quiet) {
		boolean other = id != pageId;
		pageId = id;
		if (!quiet) prefs.edit().putLong("farmPage", id).apply();
		pageState();
		applyUnits();
		if (quiet) {
			clearPage();
			loadPage();
			view.invalidate();
			return;
		}
		if (other) Log.i(this, "FERMIER", "ecran principal: " + (s.parcel == null ? town.label() : "parcela " + s.parcel));
		clearPage();
		loadPage();
		view.invalidate();
		// v2.020 (Viorel): another parcel keeps the Statistics open, with its own data; the town closes them.
		if (s.statsOn) {
			if (pageId == 0 || !s.farmer) closeStats();
			else refreshStats();
		}
		if (other) closeQuakeInfo();
		if (System.currentTimeMillis() - s.fetched >= interval()) {
			lastTry = 0;
			fetch();
		}
	}

	/** The main screen before the finger started sliding (v2.90). */
	private long pagePrev;

	/** v2.90: the finger slides toward the next (+1) or previous (-1) screen: shows it quietly. */
	public boolean pagePeek(int step) {
		List<Parcels.Parcel> l = pageList();
		int to = s.page + step;
		if (l.isEmpty() || to < 0 || to > l.size()) return false;
		bubble.hide();
		pagePrev = pageId;
		showPage(to == 0 ? 0 : l.get(to - 1).id, true);
		return true;
	}

	/** v2.90: the finger went back: the screen it started from. */
	public void pageBack() {
		showPage(pagePrev, true);
	}

	/** v2.90: the finger let the new screen stay: remembered, and downloaded when old. */
	public void pageDone() {
		long id = pageId;
		pageId = pagePrev;
		showPage(id);
	}

	/** Forgets the data of the screen shown (before another's are loaded). */
	/** v2.010 (Viorel): "Rapiță · semănat 10 sept" for the status card ("" without a crop). */
	private String cropLine(Parcels.Parcel p) {
		ro.meteoalarm.android.core.Agro.Crop c = p.crop();
		if (c == null) return "";
		String t = c.name();
		if (p.sow.length() == 10) try {
			int m = Integer.parseInt(p.sow.substring(5, 7)) - 1, day = Integer.parseInt(p.sow.substring(8, 10));
			String l = Tr.lang();
			String mo = new java.text.DateFormatSymbols(new java.util.Locale(l.equals("us") ? "en" : l)).getShortMonths()[m].replace(".", "");
			t += " · " + Tr.t("sown ", "semănat ") + (l.equals("us") ? mo + " " + day : day + " " + mo);
		} catch (Exception e) {
			// a broken date: the crop alone
		}
		return t;
	}

	private void clearPage() {
		s.crop = null;
		s.cropLine = "";
		s.w = null;
		s.warns = null;
		s.quakes = null;
		s.farm = null;
		s.pollenLvl = -1;
		s.pollenPlant = "";
		s.aqi = -1;
		s.fetched = 0;
		s.error = "";
		s.failReason = "";
		s.source = "";
		s.hours.scroll = 0;
		s.rain.scroll = 0;
		s.info.scroll = 0;
		s.grid.scroll = 0;
		s.alerts.scroll = 0;
		s.pageS.scroll = 0;
		s.days2.scroll = 0;
	}

	/** v5.30: a tap on a tab: that main screen; on "+": the parcels' page (to add one). */
	public void onTab(int k) {
		List<Parcels.Parcel> l = pageList();
		if (k > l.size()) {
			// v5.50 (Viorel): straight to the new parcel's box.
			Log.i(this, "FERMIER", "tab +: parcelă nouă");
			startActivityForResult(new Intent(this, FarmActivity.class).putExtra("dark", s.dark).putExtra("add", true), REQ_PARCELS);
			return;
		}
		if (k == s.page) {
			// The tab of the screen shown: back to its top.
			if (s.pageS.scroll > 0) {
				s.pageS.scroll = 0;
				view.invalidate();
			}
			return;
		}
		bubble.hide();
		Log.i(this, "GEST", "tab " + k);
		showPage(k == 0 ? 0 : l.get(k - 1).id);
	}

	/** v5.30: the small farmer button next to the buttons: farmer mode off. */
	public void onFarmButton() {
		Log.i(this, "GEST", "butonul fermier");
		toggleFarmer();
	}

	/** A swipe over the weather now: the next or the previous main screen, the town first. */
	public void onPage(int step) {
		List<Parcels.Parcel> l = pageList();
		if (l.isEmpty()) return;
		int to = s.page + step;
		if (to < 0 || to > l.size()) {
			note(to < 0 ? Tr.t("This is the first screen", "Acesta e primul ecran") : Tr.t("This is the last screen", "Acesta e ultimul ecran"), 1500);
			return;
		}
		bubble.hide();
		showPage(to == 0 ? 0 : l.get(to - 1).id);
	}

	/** What is saved for the main screen shown. */
	private void loadPage() {
		if (pageId == 0) {
			loadCache();
			return;
		}
		Parcels.Parcel p = Parcels.get(this, pageId);
		if (p == null) return;
		try {
			String f = readCache(Fetcher.parcelFile(p.id, "forecast.json"));
			if (f != null) s.w = Weather.parse(f);
			String u = readCache(Fetcher.parcelFile(p.id, "uv.json"));
			if (s.w != null && u != null) Weather.addExtra(s.w, u);
			s.fetched = s.w == null ? 0 : prefs.getLong("pmf_" + p.id, 0);
			s.source = prefs.getString("pms_" + p.id, "");
			String a = readCache(Fetcher.parcelFile(p.id, "air.json"));
			if (a != null) s.aqi = Weather.parseAqi(a, s.city.us());
			s.warns = Fetcher.parcelWarnings(this, p.id, s.city);
			s.quakes = new ArrayList<ro.meteoalarm.android.core.Quakes.Quake>();
			s.farm = Parcels.data(this, p.id);
			s.crop = p.crop();
			s.cropLine = cropLine(p);
		} catch (Exception e) {
			s.w = null;
			Log.e(this, "FERMIER", "datele salvate ale parcelei nu au putut fi citite", e);
		}
	}

	/** Downloads the parcel shown (its own files; nothing for the widgets or the notices). */
	private void fetchParcel() {
		final Parcels.Parcel p = Parcels.get(this, pageId);
		if (p == null) {
			showPage(0);
			return;
		}
		fetching = true;
		lastTry = System.currentTimeMillis();
		Log.i(this, "FERMIER", "descarc vremea pentru parcela " + p.name);
		s.busy = true;
		view.invalidate();
		final Cities.City c = s.city;
		final Context ctx = getApplicationContext();
		new Thread(new Runnable() {
			public void run() {
				final Fetcher.Result r = Fetcher.downloadParcel(ctx, c, p);
				Fetcher.saveParcel(ctx, p, r);
				handler.post(new Runnable() {
					public void run() {
						doneParcel(p, c, r);
					}
				});
			}
		}).start();
	}

	private void doneParcel(Parcels.Parcel p, Cities.City c, Fetcher.Result r) {
		fetching = false;
		lastDone = System.currentTimeMillis();
		s.busy = false;
		if (pageId != p.id || c != s.city) {
			fetch();
			return;
		}
		if (r.forecast != null) {
			s.w = Weather.parse(r.forecast);
			if (r.uv != null) Weather.addExtra(s.w, r.uv);
			s.fetched = System.currentTimeMillis();
			s.offline = false;
			s.failReason = "";
			s.error = "";
			s.source = r.source;
			view.autoAgain();
		} else {
			s.failReason = Fetcher.reason(r.error, s.T.en);
			if (s.w == null) s.error = Tr.t("The weather did not come: ", "Vremea nu a venit: ") + s.failReason;
			else if (resumed) note(Tr.t("The weather did not come: ", "Vremea nu a venit: ") + s.failReason, 3000);
		}
		if (r.pollen != null) {
			try {
				s.aqi = Weather.parseAqi(r.pollen, c.us());
			} catch (Exception e) {
				// keep the last one
			}
		}
		if (r.warn != null) s.warns = Fetcher.parcelWarnings(this, p.id, c);
		if (r.farm != null) {
			try {
				s.farm = ro.meteoalarm.android.core.Farm.parse(r.farm);
			} catch (Exception e) {
				// keep the last one
			}
		} else if (r.error == null) {
			Parcels.pending(this, p.id);
			if (resumed) note(Tr.t("Farmer's data: Open-Meteo is not answering; they are downloaded when it answers again", "Date fermier: Open-Meteo nu răspunde; se descarcă atunci când răspunde din nou"), 3500);
		}
		view.invalidate();
		if (s.statsOn) refreshStats();
	}

	/** The town's weather while a parcel is shown: saved for the widgets and the notices only. */
	private void fetchTownQuiet() {
		if (townFetching) return;
		townFetching = true;
		final Cities.City c = town;
		final Context ctx = getApplicationContext();
		Log.i(this, "DATE", "descarc în fundal vremea pentru " + c.label() + " (pe ecran e o parcelă)");
		new Thread(new Runnable() {
			public void run() {
				Fetcher.Result r = Fetcher.download(ctx, c);
				Fetcher.save(ctx, c, r);
				if (r.forecast != null) Widget.show(ctx);
				handler.post(new Runnable() {
					public void run() {
						townFetching = false;
					}
				});
			}
		}).start();
	}

	/** v3.10: "Statistics · PDF" on a parcel's main screen opens its statistics. */
	public void onStats() {
		Parcels.Parcel p = Parcels.get(this, pageId);
		if (p == null) return;
		Log.i(this, "GEST", "buton statistică: " + p.name);
		openStats();
	}

	// --- v2.020 (Viorel): the Statistics inside the main screen -------------------------------------

	/** Opens the Statistics of the parcel shown (portrait: under its status card; landscape: at the left). */
	private void openStats() {
		bubble.hide();
		closeQuakeInfo();
		s.statsOn = true;
		s.pageS.scroll = 0;
		view.invalidate();
	}

	/** Closes them: the boxes of the main screen come back. */
	private void closeStats() {
		if (!s.statsOn) return;
		Log.i(this, "FERMIER", "statistici închise");
		s.statsOn = false;
		s.pageS.scroll = 0;
		statsBox.removeAllViews();
		statsBox.setTag(null);
		statsBox.setVisibility(View.GONE);
		stats = null;
		view.invalidate();
	}

	/** Where the Statistics go (from the main screen's drawing); null: nowhere (no parcel). */
	public void statsFrame(int[] r) {
		if (!s.statsOn && !s.quakeOn) return;
		if (r == null) {
			statsBox.setVisibility(View.GONE);
			return;
		}
		android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(r[2] - r[0], r[3] - r[1]);
		lp.leftMargin = r[0];
		lp.topMargin = r[1];
		statsBox.setLayoutParams(lp);
		statsBox.setVisibility(View.VISIBLE);
		if (s.quakeOn) {
			// v2.100: the earthquake's information in the same place.
			if (quakeSpacer != r[4] || quakeW != r[2] - r[0]) showQuakeInfo(r[4], r[2] - r[0]);
			return;
		}
		boolean again = stats == null || stats.spacer != r[4] || stats.embedW != r[2] - r[0];
		if (again) showStats(r[4], r[2] - r[0]);
	}

	/** The Statistics of the parcel shown, drawn again (new data, another parcel, turned). */
	private void showStats(int spacer, int width) {
		final Parcels.Parcel p = Parcels.get(this, pageId);
		if (p == null) {
			closeStats();
			return;
		}
		if (stats == null) {
			Log.i(this, "FERMIER", "statistici în ecranul principal: " + p.name);
			stats = new StatsPage(this, new StatsPage.Host() {
				public void back() {
					closeStats();
				}

				public void edit(Parcels.Parcel q) {
					startActivityForResult(new Intent(MainActivity.this, FarmActivity.class).putExtra("dark", s.dark).putExtra("open", "edit").putExtra("id", q.id), REQ_PARCELS);
				}

				public void open(long id) {
				}

				public void export(Parcels.Parcel q, boolean pdf) {
					exportStats(q, pdf);
				}

				public void redraw() {
					refreshStats();
				}

				public void scrolled(int y) {
					view.statsScrolled(y);
				}

				public void forward(android.view.MotionEvent e, View from) {
					// v2.021: a tap (down and up) straight to the main screen, where the status and its bubble are.
					int[] b = new int[2];
					view.getLocationOnScreen(b);
					view.tapAt(e.getRawX() - b[0], e.getRawY() - b[1]);
				}
			}, s.dark, Region.imperial(this, s.city), s.clk12);
			stats.embed = true;
		}
		stats.spacer = spacer;
		stats.embedW = width;
		refreshStats();
	}

	/** The Statistics with the parcel's saved data; the season's archive downloaded once a day. */
	private void refreshStats() {
		if (stats == null || !s.statsOn) return;
		final Parcels.Parcel p = Parcels.get(this, pageId);
		if (p == null) {
			closeStats();
			return;
		}
		long f = Parcels.fetched(this, p.id);
		stats.set(p, Parcels.data(this, p.id), Parcels.season(this, p, false));
		stats.show(statsBox, f, f > 0 ? Tr.t("Updated ", "Actualizat ") + android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(f)) + "." : "");
		final String today = ro.meteoalarm.android.core.Farm.date(java.util.Calendar.getInstance(), 0);
		final String start = Parcels.seasonStart(p, today);
		String key = p.id + start + today;
		if (start.length() > 0 && seasonLoading != p.id && !seasonTried.contains(key)
				&& !(start + "|" + today + "|" + p.lat + "|" + p.lon).equals(Fetcher.prefs(this).getString("ps_" + p.id, ""))) {
			seasonTried.add(key);
			seasonLoading = p.id;
			final Context app = getApplicationContext();
			new Thread(new Runnable() {
				public void run() {
					final String got = Parcels.season(app, p, true);
					handler.post(new Runnable() {
						public void run() {
							seasonLoading = -1;
							if (got != null && !isFinishing() && s.statsOn && pageId == p.id) refreshStats();
						}
					});
				}
			}).start();
		}
	}

	/** The Statistics as a PDF report or a CSV table, in Downloads. */
	private byte[] saveBytes;
	private String saveName = "", saveMime = "";
	private static final int REQ_SAVE_STATS = 24;

	private void exportStats(Parcels.Parcel p, boolean asPdf) {
		try {
			StatsExport ex = new StatsExport(this, stats, s.dark);
			String base = "MeteoAlarm-" + Tr.t("Statistics", "Statistici") + "-" + p.name.replaceAll("[^A-Za-z0-9ĂÂÎȘȚăâîșț_-]+", "-") + "-"
					+ ro.meteoalarm.android.core.Farm.date(java.util.Calendar.getInstance(), 0);
			saveBytes = asPdf ? ex.pdf() : ex.csv().getBytes("UTF-8");
			saveName = base + (asPdf ? ".pdf" : ".csv");
			saveMime = asPdf ? "application/pdf" : "text/csv";
			Log.i(this, "FERMIER", "fișier " + saveName + " " + saveBytes.length / 1024 + " KB");
			if (android.os.Build.VERSION.SDK_INT >= 29) {
				android.content.ContentValues v = new android.content.ContentValues();
				v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, saveName);
				v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, saveMime);
				v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
				android.net.Uri u = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
				if (u == null) throw new Exception("MediaStore");
				java.io.OutputStream out = getContentResolver().openOutputStream(u);
				out.write(saveBytes);
				out.close();
				statsSaved(u);
			} else startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(saveMime).putExtra(Intent.EXTRA_TITLE, saveName), REQ_SAVE_STATS);
		} catch (Exception e) {
			Log.e(this, "FERMIER", "export statistici", e);
			note(Tr.t("The file could not be saved: ", "Fișierul nu a putut fi salvat: ") + e.getClass().getSimpleName(), 3000);
		}
	}

	private void statsSaved(final android.net.Uri u) {
		final boolean csv = !"application/pdf".equals(saveMime);
		AlertDialog.Builder d = new AlertDialog.Builder(this, s.dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
		d.setTitle(csv ? Tr.t("CSV saved", "CSV salvat") : Tr.t("PDF saved", "PDF salvat"));
		d.setMessage(Tr.t("In Downloads: ", "În Descărcări: ") + saveName);
		d.setPositiveButton(Tr.t("Open", "Deschide"), new android.content.DialogInterface.OnClickListener() {
			public void onClick(android.content.DialogInterface di, int w) {
				try {
					startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, saveMime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
				} catch (Exception e) {
					note(Tr.t("No app to open it", "Nicio aplicație care să-l deschidă"), 2500);
				}
			}
		});
		d.setNegativeButton("OK", null);
		Dlg.show(d);
	}

	/**
	 * A tap on the parcel's name: its whole name and its coordinates only (v3.30, Viorel); the
	 * parcel's options are on its edit page (☰ › Parcels, the pencil).
	 */
	private void parcelBubble() {
		final Parcels.Parcel p = Parcels.get(this, pageId);
		if (p == null) return;
		Log.i(this, "GEST", "atingere nume parcelă: " + p.name);
		reopen = new Runnable() {
			public void run() {
				parcelBubble();
			}
		};
		List<Bubble.Row> rows = Bubble.rows();
		rows.add(new Bubble.Row(Tr.t("Coordinates: ", "Coordonate: ") + String.format(java.util.Locale.US, "%.5f, %.5f", p.lat, p.lon), null, Bubble.Row.TEXT, false));
		bubble.show(view.px(s.cityArea), bubbleWidth(0.86f, 280), p.name, rows, s.T.en, new Bubble.Pick() {
			public void picked(int k) {
			}
		});
	}

	@Override
	protected void onActivityResult(int code, int res, Intent data) {
		super.onActivityResult(code, res, data);
		if (code == REQ_SAVE_STATS) {
			if (res == RESULT_OK && data != null && data.getData() != null && saveBytes != null) try {
				java.io.OutputStream out = getContentResolver().openOutputStream(data.getData());
				out.write(saveBytes);
				out.close();
				statsSaved(data.getData());
			} catch (Exception e) {
				Log.e(this, "FERMIER", "export statistici", e);
			}
			return;
		}
		if (code == REQ_SAVE_DATA || code == REQ_OPEN_DATA) {
			if (res == RESULT_OK && data != null && data.getData() != null) {
				if (code == REQ_SAVE_DATA) saveData(data.getData());
				else openData(data.getData());
			}
			return;
		}
		if (code != REQ_PARCELS) return;
		// v5.52 (Viorel): back from the Parcels: the main screen, never the ☰ window again.
		menuAfterTurn = false;
		reopen = null;
		if (settings != null && settings.isOpen()) settings.hide();
		long sel = data != null ? data.getLongExtra("select", -1) : -1;
		// A parcel picked in ☰ › Parcele; or back: the parcel shown may be renamed, moved or deleted.
		showPage(sel >= 0 ? sel : pageId);
	}

	/** Units and clock of the town shown (US towns imperial with a 12-hour clock, v2.30). */
	private void applyUnits() {
		s.unitF = Region.imperial(this, s.city);
		s.clk12 = Region.clk12(this, s.city);
		// A US town: its own local time (v2.50); elsewhere the phone's.
		String was = ro.meteoalarm.android.core.Zone.get().getID();
		ro.meteoalarm.android.core.Zone.set(Fetcher.tz(s.city));
		s.now = ro.meteoalarm.android.core.Zone.now();
		if (!was.equals(ro.meteoalarm.android.core.Zone.get().getID()))
			Log.i(this, "US", "ora afișată: " + ro.meteoalarm.android.core.Zone.get().getID());
		s.farmer = Parcels.farmer(this);
	}

	@Override
	public void onConfigurationChanged(Configuration cfg) {
		super.onConfigurationChanged(cfg);
		applySettings();
		view.invalidate();
		if (menuAfterTurn && cfg.orientation == Configuration.ORIENTATION_PORTRAIT) {
			menuAfterTurn = false;
			view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				public void onLayoutChange(View v, int l, int t, int ri, int b, int ol, int ot, int or, int ob) {
					view.removeOnLayoutChangeListener(this);
					handler.postDelayed(new Runnable() {
						public void run() {
							onMenu();
						}
					}, 150);
				}
			});
		}
		// v5.53 (Viorel): on a tablet the ☰ window is drawn again for the turned screen.
		if (settings != null && settings.isOpen() && TextSize.tablet(this)) {
			view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				public void onLayoutChange(View v, int l, int t, int ri, int b, int ol, int ot, int or, int ob) {
					view.removeOnLayoutChangeListener(this);
					handler.postDelayed(new Runnable() {
						public void run() {
							if (settings.isOpen()) onMenu();
						}
					}, 150);
				}
			});
		}
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
			if (compassPanel != null) compassPanel.heading(headSmooth);
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

	/**
	 * Every sensor the app may use, present or missing, in the log at start (Viorel 2026-11-04);
	 * the compass box and the light-sensor button follow.
	 */
	private void checkSensors() {
		if (sensors == null) sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
		int[] types = { Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE,
				Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY, Sensor.TYPE_PRESSURE, Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_RELATIVE_HUMIDITY };
		String[] names = { "busolă (vector de rotație)", "magnetometru", "accelerometru", "giroscop", "lumină", "proximitate", "presiune",
				"temperatură", "umiditate" };
		StringBuilder have = new StringBuilder(), miss = new StringBuilder();
		for (int k = 0; k < types.length; k++) {
			boolean ok = sensors != null && sensors.getDefaultSensor(types[k]) != null;
			StringBuilder b = ok ? have : miss;
			b.append(b.length() > 0 ? ", " : "").append(names[k]);
		}
		android.content.pm.PackageManager pm = getPackageManager();
		String[][] feats = { { "android.hardware.location.gps", "GPS" }, { "android.hardware.location.network", "localizare prin rețea (WiFi / mobil)" },
				{ "android.hardware.telephony", "telefonie (SIM)" }, { "android.hardware.wifi", "WiFi" } };
		for (String[] f : feats) {
			StringBuilder b = pm.hasSystemFeature(f[0]) ? have : miss;
			b.append(b.length() > 0 ? ", " : "").append(f[1]);
		}
		Log.i(this, "SENZOR", "prezenți: " + have);
		Log.i(this, "SENZOR", "lipsă: " + (miss.length() > 0 ? miss : "niciunul"));
		s.hasCompass = sensors != null && sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null;
		s.hasLight = hasLight();
		if (!s.hasCompass) Log.i(this, "SENZOR", "fără busolă: caseta Busolă e ascunsă, săgeata vântului arată nordul sus");
		if (!s.hasLight) Log.i(this, "SENZOR", "fără senzor de lumină: butonul Temă automată e dezactivat");
		// A theme set to the sensor on a phone without one: the phone's dark mode instead.
		if (!s.hasLight && "sensor".equals(prefs.getString("theme", "auto"))) prefs.edit().putString("theme", "auto").apply();
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
			Log.i(this, "SENZOR", "atingere pe tema automată, dar telefonul nu are senzor de lumină (buton dezactivat)");
			toast(Tr.t("This phone has no light sensor.", "Telefonul nu are senzor de lumină."));
			return;
		}
		boolean on = !"sensor".equals(prefs.getString("theme", "auto"));
		Log.i(this, "BUTON", "temă automată (senzor) " + (on ? "pornită" : "oprită"));
		// Off: the theme stays as it is now, chosen by hand.
		prefs.edit().putString("theme", on ? "sensor" : s.dark ? "dark" : "light").apply();
		applySettings();
		view.invalidate();
		note(on ? (Tr.t("Automatic theme (phone sensor): on", "Temă automată (senzor telefon): pornită"))
				: (Tr.t("Automatic theme (phone sensor): off", "Temă automată (senzor telefon): oprită")));
	}

	/** The moon button over the brightness bar: dark or light theme, chosen by hand. */
	public void toggleTheme() {
		Log.i(this, "BUTON", "temă " + (s.dark ? "luminoasă" : "întunecată"));
		prefs.edit().putString("theme", s.dark ? "light" : "dark").apply();
		applySettings();
		note(s.dark ? (Tr.t("Dark theme", "Tema întunecată")) : (Tr.t("Light theme", "Tema luminoasă")));
		view.invalidate();
	}

	/** v3.00 (Viorel): the connection icon next to the clock follows the phone's network. */
	private android.net.ConnectivityManager.NetworkCallback netCb;

	private void showNet() {
		int k = NetJob.kind(this);
		Object[] sg = NetJob.signal(this, k);
		int lv = (Integer) sg[0];
		String tag = (String) sg[1];
		if (k != s.net || lv != s.netLevel || !tag.equals(s.netTag)) {
			if (k != s.net || !tag.equals(s.netTag))
				Log.i(this, "REȚEA", (k == 0 ? "fără internet" : k == 1 ? "Wi-Fi" : k == 2 ? "date mobile" : "altă rețea") + (tag.length() > 0 ? " " + tag : "") + (lv >= 0 ? ", semnal " + lv + "/4" : ", semnal necunoscut"));
			s.net = k;
			s.netLevel = lv;
			s.netTag = tag;
			view.invalidate();
		}
	}

	private void listenNet(boolean on) {
		showNet();
		if (Build.VERSION.SDK_INT < 24) return;
		android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		if (cm == null) return;
		try {
			if (on && netCb == null) {
				netCb = new android.net.ConnectivityManager.NetworkCallback() {
					private void again() {
						handler.post(new Runnable() {
							public void run() {
								showNet();
							}
						});
					}

					@Override
					public void onAvailable(android.net.Network n) {
						again();
					}

					@Override
					public void onLost(android.net.Network n) {
						again();
					}

					@Override
					public void onCapabilitiesChanged(android.net.Network n, android.net.NetworkCapabilities nc) {
						again();
					}
				};
				cm.registerDefaultNetworkCallback(netCb);
			} else if (!on && netCb != null) {
				cm.unregisterNetworkCallback(netCb);
				netCb = null;
			}
		} catch (Exception e) {
			Log.e(this, "REȚEA", "urmărirea rețelei nu a pornit", e);
			netCb = null;
		}
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
		watchScreen(true); // v5.24: stays on until the app closes, to catch the screen turning off
		listenNet(true);
		view.replayIcon();
		if (quakeWanted) handler.postDelayed(new Runnable() {
			public void run() {
				openQuakeIfWanted();
			}
		}, 700);
		handler.removeCallbacks(tick);
		handler.post(tick);
		if (!prefs.getBoolean("welcomed", false)) {
			if (!asked) {
				asked = true;
				welcome();
			}
			return;
		}
		// Back from the phone's settings, where the user went to allow location or notifications.
		backFromSettings();
		// Just updated: what is new in this version, once (instead of the update check this time).
		String cur = updater.installed();
		if (!cur.equals(prefs.getString("seenVersion", ""))) {
			news();
			return;
		}
		// Location and notifications: at the first start and, while refused, after each update.
		if (!asked && !updater.installed().equals(prefs.getString("consentVer", ""))) {
			asked = true;
			consents();
			return;
		}
		// v5.31 (Viorel 2026-11-14): no automatic update check; only ☰ › Check for updates.
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

	// --- Consents (Viorel 2026-11-04) --------------------------------------------------
	// Location and notifications are asked with a friendly explanation first: at the first start
	// and, while refused, again only after an app update (never at every start). After two refusals
	// Android no longer shows its question: then the button opens the phone's settings.

	private static final int REQ_LOC = 1, REQ_NOTIFY = 2;
	/** The consents asked one after the other (location, then notifications). */
	private boolean chain;

	/** Asks what is still missing, in the bottom window (it closes when nothing is). */
	private void consents() {
		prefs.edit().putString("consentVer", updater.installed()).apply();
		chain = true;
		if (!Locator.allowed(this)) {
			explainLocation();
			return;
		}
		chainNotify();
	}

	private void chainNotify() {
		if (chain && !Notifier.on(this) && !prefs.getBoolean("notifyUserOff", false)) {
			explainNotify();
			return;
		}
		chain = false;
		sheet.hide();
	}

	private void explainLocation() {
		boolean blocked = prefs.getBoolean("locBlocked", false);
		Log.i(this, "ACORD", "explicație localizare" + (blocked ? " (blocată, spre setări)" : ""));
		String msg = Tr.t("Meteo Alarm can find your town by itself, so the forecast, the weather warnings and the earthquakes are for the exact place where you are, also when you travel. The position stays on the phone: it is used only to choose the town.",
				"Meteo Alarm poate găsi singură localitatea ta, ca prognoza, avertizările meteo și cutremurele să fie pentru locul exact în care ești, și când călătorești. Poziția rămâne pe telefon: e folosită doar ca să aleagă localitatea.")
				+ "\n\n" + (blocked ? Tr.t("The phone no longer shows its question, so allow Location for Meteo Alarm in the phone's settings (Permissions › Location).",
						"Telefonul nu mai arată întrebarea lui, așa că permite Locația pentru Meteo Alarm din setările telefonului (Permisiuni › Locație).")
						: Tr.t("If you say no, choose the town from the list; the app asks again only after an update.",
								"Dacă refuzi, alegi localitatea din listă; aplicația te mai întreabă doar după o actualizare."));
		sheet.show(Tr.t("Location", "Localizare"), msg, blocked ? Tr.t("Open settings", "Deschide setările") : Tr.t("Allow", "Permite"), new Runnable() {
			public void run() {
				if (prefs.getBoolean("locBlocked", false)) {
					prefs.edit().putBoolean("locWant", true).apply();
					openSettings(false);
					chainNotify();
				} else {
					Log.i(MainActivity.this, "ACORD", "localizare: cerere sistem");
					requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, REQ_LOC);
				}
			}
		}, Tr.t("Not now", "Nu acum"), new Runnable() {
			public void run() {
				Log.i(MainActivity.this, "ACORD", "localizare: refuzată în explicație");
				setAutoLoc(false);
				chainNotify();
			}
		});
	}

	private void explainNotify() {
		boolean blocked = prefs.getBoolean("notifyBlocked", false) || (Build.VERSION.SDK_INT < 33 || hasNotifyPermission()) && !Notifier.allowed(this);
		Log.i(this, "ACORD", "explicație notificări" + (blocked ? " (blocate, spre setări)" : ""));
		String msg = Tr.t("Meteo Alarm can tell you, also while it is closed: when rain or snow starts or will start within the hour, when a weather warning is issued for your town, and when an earthquake of magnitude 3 or more happens in your country. Each event only once, nothing else. So you learn in time, without opening the app.",
				"Meteo Alarm îți poate spune, și când e închisă: când începe ploaia sau ninsoarea ori începe în următoarea oră, când se emite o avertizare meteo pentru localitatea ta și când are loc un cutremur cu magnitudinea 3 sau mai mare în țara ta. Fiecare eveniment o singură dată, nimic altceva. Așa afli la timp, fără să deschizi aplicația.")
				+ "\n\n" + (blocked ? Tr.t("The phone does not let the app notify, so turn on the notifications of Meteo Alarm in the phone's settings.",
						"Telefonul nu lasă aplicația să notifice, așa că pornește notificările pentru Meteo Alarm din setările telefonului.")
						: Tr.t("You can turn them off anytime in ☰ › Notifications. If you say no, the app asks again only after an update.",
								"Le poți opri oricând din ☰ › Notificări. Dacă refuzi, aplicația te mai întreabă doar după o actualizare."));
		sheet.show(Tr.t("Notifications", "Notificări"), msg, blocked ? Tr.t("Open settings", "Deschide setările") : Tr.t("Allow", "Permite"), new Runnable() {
			public void run() {
				askNotify();
			}
		}, Tr.t("Not now", "Nu acum"), new Runnable() {
			public void run() {
				Log.i(MainActivity.this, "ACORD", "notificări: refuzate în explicație");
				chain = false;
				sheet.hide();
			}
		});
	}

	private boolean hasNotifyPermission() {
		return Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED;
	}

	/** Turns the notifications on: the system question (13+), the settings when blocked, else on. */
	private void askNotify() {
		if (!hasNotifyPermission() && !prefs.getBoolean("notifyBlocked", false)) {
			Log.i(this, "ACORD", "notificări: cerere sistem");
			requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, REQ_NOTIFY);
			return;
		}
		if (!Notifier.allowed(this)) {
			prefs.edit().putBoolean("notifyWant", true).apply();
			chain = false;
			sheet.hide();
			openSettings(true);
			return;
		}
		notifyOn();
		chain = false;
		sheet.hide();
	}

	private void notifyOn() {
		prefs.edit().putBoolean("notify", true).putBoolean("notifyUserOff", false).putBoolean("notifyWant", false)
				.putLong("notifySince", System.currentTimeMillis()).apply();
		Log.i(this, "ACORD", "notificări: PORNITE");
		// v3.50 (Viorel): no notice on the phone that the notifications are on; only the message in the app.
		note(Tr.t("Notifications ON", "Notificări PORNITE"), 2000);
	}

	/** ☰ › Notifications: a switch. */
	private void toggleNotify() {
		if (Notifier.on(this)) {
			prefs.edit().putBoolean("notify", false).putBoolean("notifyUserOff", true).apply();
			Log.i(this, "ACORD", "notificări: OPRITE din meniu");
			note(Tr.t("Notifications OFF", "Notificări OPRITE"), 2000);
			return;
		}
		chain = false;
		explainNotify();
	}

	/** The app's page in the phone's settings (NOTIFY: its notifications page). */
	private void openSettings(boolean notify) {
		Log.i(this, "ACORD", "deschid setările telefonului (" + (notify ? "notificări" : "permisiuni") + ")");
		try {
			Intent i;
			if (notify && Build.VERSION.SDK_INT >= 26) {
				i = new Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
			} else {
				i = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", android.net.Uri.parse("package:" + getPackageName()));
			}
			startActivity(i);
		} catch (Exception e) {
			Log.e(this, "ACORD", "setările nu s-au deschis", e);
		}
	}

	/** On return from the settings: what the user allowed there. */
	private void backFromSettings() {
		if (prefs.getBoolean("notifyWant", false) && Notifier.allowed(this)) {
			prefs.edit().putBoolean("notifyBlocked", false).apply();
			notifyOn();
		}
		if (prefs.getBoolean("locWant", false) && Locator.allowed(this)) {
			prefs.edit().putBoolean("locWant", false).putBoolean("locBlocked", false).apply();
			Log.i(this, "ACORD", "localizare: permisă din setări");
			setAutoLoc(true);
			locate(false);
		}
	}

	/** From the buttons (town name, locate): the explanation only when Android no longer asks. */
	private void askLocation() {
		asked = true;
		if (prefs.getBoolean("locBlocked", false)) {
			chain = false;
			explainLocation();
			return;
		}
		requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, REQ_LOC);
	}

	@Override
	public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
		if (code == REQ_NOTIFY) {
			if (hasNotifyPermission()) {
				askNotify();
			} else {
				// No question shown any more (two refusals): the next time, the settings.
				boolean blocked = !shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS");
				prefs.edit().putBoolean("notifyBlocked", blocked).apply();
				Log.i(this, "ACORD", "notificări: refuzate în sistem" + (blocked ? " (Android nu mai întreabă)" : ""));
				chain = false;
				sheet.hide();
			}
			return;
		}
		if (Locator.allowed(this)) {
			Log.i(this, "ACORD", "localizare: permisă");
			prefs.edit().putBoolean("locBlocked", false).apply();
			setAutoLoc(true);
			locate(true);
		} else {
			boolean blocked = !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
					&& !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
			prefs.edit().putBoolean("locBlocked", blocked).apply();
			Log.i(this, "ACORD", "localizare: refuzată în sistem" + (blocked ? " (Android nu mai întreabă)" : ""));
			setAutoLoc(false);
			toast(Tr.t("Without location access, choose the town from the list.", "Fără acces la locație, alege localitatea din listă."));
		}
		if (chain) chainNotify();
		else sheet.hide();
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
		note(Tr.t("Brightness: slide on the bar", "Luminozitate: glisează pe bară"));
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
		if (k == 0 && s.mainQuake() != null) {
			// v2.100 (Viorel): the earthquake's information inside the main screen, not a bubble.
			reopen = null;
			openQuakeInfo();
			return;
		}
		List<String[]> info = s.alertInfo(k);
		if (info.isEmpty()) return;
		String head = info.get(0)[1];
		List<Bubble.Row> rows = Bubble.info(info);
		bubble.place = s.place();
		bubble.show(view.px(b), bubbleWidth(0.86f, 280), head, rows, s.T.en, new Bubble.Pick() {
			public void picked(int i) {
			}
		});
	}

	/**
	 * v2.030 (Viorel, after the quake of 2026-11-18): the earthquake's bubble: its magnitude by the
	 * title, a map with the epicentre, the town and the felt circles (a tap opens it on the whole
	 * screen), distance and depth, how it was felt here, the source with its revision and why the
	 * Android alert may differ; then the other earthquakes of the day and the sources.
	 */
	/** v2.032: the INFP button's text after a check that brought no INFP value (null = as at first). */
	private String infpMiss;

	/** v2.100: the text size of the earthquake's screens. */
	private float quakeZk() {
		return (TextSize.tablet(this) ? 1.11f : 1f) * TextSize.get(this);
	}

	/** The other earthquakes of the day and the sources (rows as from Screen.alertInfo). */
	private List<String[]> quakeMore(ro.meteoalarm.android.core.Quakes.Official off) {
		List<String[]> info = s.alertInfo(0);
		boolean rest = false;
		List<String[]> more = new ArrayList<String[]>();
		for (int i = 1; i < info.size(); i++) {
			String[] r = info.get(i);
			if (r[0].equals("h") && r[1].equals(Tr.t("All in the last 24 h", "Toate în ultimele 24 h"))) rest = true;
			if (rest || r[0].equals("s")) more.add(r);
		}
		if (off != null && off.code.equals("ro")) more.add(new String[] { "s", Tr.t("Romania: the official values come from INFP (National Institute for Earth Physics, infp.ro); EMSC when INFP does not answer.",
				"România: valorile oficiale vin de la INFP (Institutul Național de Fizica Pământului, infp.ro); EMSC când INFP nu răspunde.") });
		else if (off != null) more.add(new String[] { "s", Tr.t("The official values come from ", "Valorile oficiale vin de la ") + off.name + " (" + off.full + ", " + off.site + ")"
				+ Tr.t("; EMSC when it does not answer.", "; EMSC când nu răspunde.") });
		return more;
	}

	/**
	 * v2.100 (Viorel, mockups m8, m9): a tap on the Earthquake box (or on its notice) opens the
	 * earthquake's information inside the main screen: portrait under the Feels like card (the
	 * boxes below hidden), landscape in the left part; ‹ or Back close it and the boxes come back.
	 */
	private void openQuakeInfo() {
		if (s.mainQuake() == null) return;
		bubble.hide();
		if (s.statsOn) closeStats();
		if (!s.quakeOn) {
			Log.i(this, "CUTREMUR", "informații cutremur deschise în ecranul principal");
			s.quakeOn = true;
			s.pageS.scroll = 0;
			quakeSpacer = -1;
			view.statsReset();
		}
		view.invalidate();
	}

	private void closeQuakeInfo() {
		if (!s.quakeOn) return;
		Log.i(this, "CUTREMUR", "informații cutremur închise");
		s.quakeOn = false;
		s.pageS.scroll = 0;
		statsBox.removeAllViews();
		statsBox.setVisibility(View.GONE);
		quakeSpacer = -1;
		view.statsReset();
		view.invalidate();
	}

	/** Where the earthquake's information was last built: the room above it and its width (pixels). */
	private int quakeSpacer = -1, quakeW = -1;

	/** New data: the information built again, at the same place in its scrolling. */
	private void refreshQuakeInfo() {
		if (!s.quakeOn) return;
		if (s.mainQuake() == null) {
			closeQuakeInfo();
			return;
		}
		if (quakeSpacer >= 0) showQuakeInfo(quakeSpacer, quakeW);
	}

	/** Builds the earthquake's information in the Statistics' place (SPACER: the room above it that scrolls away). */
	private void showQuakeInfo(int spacer, int width) {
		final ro.meteoalarm.android.core.Quakes.Quake q = s.mainQuake();
		if (q == null) {
			closeQuakeInfo();
			return;
		}
		android.widget.ScrollView old = statsBox.getChildCount() > 0 && statsBox.getChildAt(0) instanceof android.widget.ScrollView ? (android.widget.ScrollView) statsBox.getChildAt(0) : null;
		final int y0 = old != null && quakeSpacer == spacer ? old.getScrollY() : 0;
		quakeSpacer = spacer;
		quakeW = width;
		statsBox.removeAllViews();
		final float dp = getResources().getDisplayMetrics().density;
		final float zk = quakeZk();
		final Cities.City town = s.city;
		final String[] t = s.quakeTexts(q);
		final ro.meteoalarm.android.core.Quakes.Official off = Fetcher.officialOf(town);
		final String on = off == null ? "" : off.name;
		QuakeCard.Hooks k = new QuakeCard.Hooks();
		k.back = new Runnable() {
			public void run() {
				closeQuakeInfo();
			}
		};
		k.full = new Runnable() {
			public void run() {
				Log.i(MainActivity.this, "CUTREMUR", "ecran întreg din ecranul principal");
				openQuakeFull(q, town, true);
			}
		};
		String miss = infpMiss;
		infpMiss = null;
		if (off != null && !q.official) {
			k.refresh = new Runnable() {
				// v2.032 (Viorel): ask the institute now; the information is built again with what came.
				public void run() {
					Log.i(MainActivity.this, "CUTREMUR", "verificare " + on + " cerută din informații");
					new Thread(new Runnable() {
						public void run() {
							Fetcher.quakesOnly(MainActivity.this, town);
							runOnUiThread(new Runnable() {
								public void run() {
									if (isFinishing() || s == null || town != s.city) return;
									s.quakes = Fetcher.quakes(MainActivity.this, town);
									ro.meteoalarm.android.core.Quakes.Quake nq = s.mainQuake();
									infpMiss = nq != null && nq.official ? null
											: on + Tr.t(" has no value for it yet · try again", " nu are încă o valoare · încearcă din nou");
									refreshQuakeInfo();
									view.invalidate();
								}
							});
						}
					}).start();
				}
			};
			k.refreshText = miss != null ? miss : "↻ " + Tr.t("Refresh from ", "Reîmprospătează de la ") + on;
			k.refreshName = on;
		}
		k.more = quakeMore(off);
		k.titlePx = 23 * view.scale(); // v2.101: as the section titles (NEXT HOURS)
		android.widget.LinearLayout all = new android.widget.LinearLayout(this);
		all.setOrientation(android.widget.LinearLayout.VERTICAL);
		if (spacer > 0) {
			// As with the Statistics: a tap on the room (the page above shows through) goes to the main screen.
			View room = new View(this) {
				float x0, y0;
				boolean far;

				@Override
				public boolean onTouchEvent(android.view.MotionEvent e) {
					int act = e.getActionMasked();
					if (act == android.view.MotionEvent.ACTION_DOWN) {
						x0 = e.getRawX();
						y0 = e.getRawY();
						far = false;
					} else if (act == android.view.MotionEvent.ACTION_MOVE) {
						if (Math.abs(e.getRawX() - x0) > 10 * dp || Math.abs(e.getRawY() - y0) > 10 * dp) far = true;
					} else if (act == android.view.MotionEvent.ACTION_UP && !far) {
						int[] b = new int[2];
						view.getLocationOnScreen(b);
						view.tapAt(e.getRawX() - b[0], e.getRawY() - b[1]);
					}
					return true;
				}
			};
			all.addView(room, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, spacer));
		}
		all.addView(QuakeCard.info(this, q, town, s, t, s.dark, fReg, fBold, zk, width, k));
		final android.widget.ScrollView sv = new android.widget.ScrollView(this);
		sv.setVerticalScrollBarEnabled(false);
		sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
		sv.addView(all);
		sv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
			public void onScrollChange(View v, int x, int y, int ox, int oy) {
				view.statsScrolled(y);
			}
		});
		statsBox.addView(sv, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		view.statsScrolled(y0);
		if (y0 > 0) sv.post(new Runnable() {
			public void run() {
				sv.scrollTo(0, y0);
				view.statsScrolled(sv.getScrollY());
			}
		});
		Log.i(this, "CUTREMUR", "informații: M" + t[2] + " " + q.src + ", " + t[4]);
	}

	/**
	 * v2.100 (Viorel, mockups m10, m11): an earthquake on the whole screen (from ☰ › Earthquakes, or
	 * the square / the map of the information); ‹ or Back return where it was opened from.
	 */
	private void openQuakeFull(ro.meteoalarm.android.core.Quakes.Quake q, Cities.City c, boolean withMore) {
		// v2.102 (Viorel): the value's history the same everywhere: the list's quake is the town's
		// quake (with its first value and revisions) when it is the same event.
		if (s.quakes != null) for (ro.meteoalarm.android.core.Quakes.Quake m : s.quakes)
			if (m != q && Math.abs(m.time - q.time) < 120000L && Math.abs(m.lat - q.lat) < 1 && Math.abs(m.lon - q.lon) < 1) {
				q = m;
				break;
			}
		QuakeCard.Hooks k = new QuakeCard.Hooks();
		if (withMore) k.more = quakeMore(Fetcher.officialOf(c));
		k.titlePx = 23 * view.scale();
		QuakeCard.full(this, q, c, s, s.dark, fReg, fBold, quakeZk(), k);
	}

	/** v2.030: a tap on an earthquake notice: the town's screen and the earthquake's bubble, once the data are there. */
	private void openQuakeIfWanted() {
		if (!quakeWanted || view == null || s == null) return;
		if (s.mainQuake() == null || view.getWidth() == 0) return;
		quakeWanted = false;
		if (s.farmer && pageId != 0) showPage(0);
		handler.postDelayed(new Runnable() {
			public void run() {
				if (s.mainQuake() != null) onAlert(0, s.alertRect(0));
			}
		}, 500);
	}

	/**
	 * A tap on the town's name: one look with the GPS for a better forecast; automatic location
	 * stays as it is.
	 */
	public void onTownName() {
		if (pageId != 0) {
			parcelBubble();
			return;
		}
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
			note(Tr.t("Automatic location OFF", "Localizare automată OPRITĂ"), 2000);
			return;
		}
		setAutoLoc(true);
		note(Tr.t("Automatic location ON", "Localizare automată PORNITĂ"), 2000);
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
		if (say) note(Tr.t("Looking for the exact position…", "Caut poziția exactă…"));
		locator.locate(new Locator.Result() {
			public void found(double lat, double lon, String town, String rawCounty, String district, String country, String match) {
				s.locating = false;
				Log.i(MainActivity.this, "LOC", "poziție găsită: " + town + ", " + rawCounty + ", " + country + " (" + Math.round(lat * 1000) / 1000.0 + ", " + Math.round(lon * 1000) / 1000.0 + ")");
				boolean home = country.equals("ro");
				String county = home ? cities.county(rawCounty) : rawCounty.trim();
				// United States (v2.30): the state, in English (OpenStreetMap's local names come first in MATCH).
				if (country.equals("us")) {
					String st = Cities.stateName(match.split("/")[0]);
					if (st.length() == 0) st = Cities.stateName(rawCounty);
					if (st.length() > 0) county = st;
				}
				usFound(country, country.equals("us") ? county : "");
				if (county.length() == 0) county = town;
				// The neighbourhood is only shown, never kept: the town keeps its name in the list.
				String name = town;
				Cities.City found = new Cities.City(country, county, name, 0, 0, match);
				Cities.City listed = cities.find(found);
				if (listed != null) name = listed.name;
				double la = Math.round(lat * 10000) / 10000.0, lo = Math.round(lon * 10000) / 10000.0;
				// The town shown changes only when the phone is out of its boundary by more than
				// EDGE_M metres (a more exact name, as a sector of the same town, is taken at once).
				Cities.City cur = MainActivity.this.town;
				boolean same = found.same(cur);
				Area area = cur == null || same ? null : loadArea(cur);
				boolean keep = area != null && area.holds(lat, lon, EDGE_M);
				Cities.City here = keep ? new Cities.City(cur.country, cur.county, cur.name, la, lo, cur.match)
						: new Cities.City(country, county, name, la, lo, match);
				boolean added = cities.find(here) == null;
				learn(here);
				// v4.40: the first time in another country or US state, its small localities are downloaded.
				if (!locator.lastOffline) Places.located(MainActivity.this, here.country, here.county);
				if (added && say) toast((Tr.t("Added to the list: ", "Adăugată în listă: ")) + here.label());
				if (!keep) fetchArea(here, town, null, lat, lon);
				boolean moved = cur == null || !here.same(cur) || distKm(cur.lat, cur.lon, la, lo) > 1;
				// The first town found (first install): it becomes the town shown and a favourite,
				// even when it is the town shown by default (Viorel 2026-11-04, tablet log).
				boolean first = !prefs.getBoolean("firstFound", false) || prefs.getString(Region.favsKey(here), "").length() == 0;
				if (moved || first) {
					setCity(here, false);
					if (first) {
						prefs.edit().putBoolean("firstFound", true).apply();
						Log.i(MainActivity.this, "LOC", "prima localitate găsită: " + here.label() + " setată + adăugată la favorite");
					}
				}
				view.invalidate();
				if (moved || say) {
					if (pageId != 0) fetchTownQuiet();
					else fetch();
				}
				noteTop = false;
			}

			public void failed(String why) {
				s.locating = false;
				if (why.equals("busy")) Log.i(MainActivity.this, "LOC", "căutare deja în curs");
				else Log.e(MainActivity.this, "LOC", "poziția nu a fost găsită: " + why);
				noteTop = false;
				view.invalidate();
				if (!say || why.equals("busy")) return;
				if (why.equals("off")) toast(Tr.t("Location is off on the phone.", "Localizarea e oprită pe telefon."));
				else if (why.equals("nofix")) toast(Tr.t("The position could not be found.", "Poziția nu a putut fi găsită."));
				else toast(Tr.t("The town could not be found (internet?).", "Localitatea nu a putut fi găsită (internet?)."));
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
		if (passing(c)) {
			Visits v = new Visits(prefs.getString("visits", ""));
			if (v.visit(c, Visits.today(System.currentTimeMillis()))) Log.i(this, "LOC", c.label() + ": trecut des pe aici, rămâne în lista județului");
			prefs.edit().putString("visits", v.toString()).apply();
		}
		cities.put(c);
		StringBuilder b = new StringBuilder(c.key());
		for (String k : prefs.getString("myCities", "").split("\n")) {
			Cities.City m = Cities.City.fromKey(k);
			if (m == null || m.same(c)) continue;
			b.append('\n').append(k);
		}
		prefs.edit().putString("myCities", b.toString()).apply();
	}

	/** v4.20: the towns of cities.txt (v4.40: and of world.txt, us.txt), always listed; not counted as places passed through. */
	private final java.util.HashSet<String> builtIn = new java.util.HashSet<String>();

	/** A place not in the app's own list (v4.40: in any country): listed only while the phone passes there. */
	private boolean passing(Cities.City c) {
		return c != null && !builtIn.contains(Visits.key(c));
	}

	/** v4.20 (Viorel): drops from the learned towns the places not passed through lately (not a favourite, not the town shown). */
	private void expirePlaces() {
		Visits v = new Visits(prefs.getString("visits", ""));
		long day = Visits.today(System.currentTimeMillis());
		Cities.City cur = Cities.City.fromKey(prefs.getString("city", ""));
		List<Cities.City> fav = favList("favs");
		fav.addAll(favList("favsUS"));
		StringBuilder b = new StringBuilder();
		for (String k : prefs.getString("myCities", "").split("\n")) {
			Cities.City c = Cities.City.fromKey(k);
			if (c == null) continue;
			boolean keep = !passing(c) || c.same(cur) || v.stays(c, day);
			if (!keep) for (Cities.City f : fav) keep |= f.same(c);
			if (keep) b.append(b.length() > 0 ? "\n" : "").append(k);
			else {
				v.forget(c);
				Log.i(this, "LOC", c.label() + ": scoasă din listă (nicio trecere în ultimele " + Visits.GONE + " zile)");
			}
		}
		prefs.edit().putString("myCities", b.toString()).putString("visits", v.toString()).apply();
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
		handler.removeCallbacks(offClose);
		handler.removeCallbacks(offQuit);
		watchScreen(false);
		super.onDestroy();
	}

	/**
	 * v5.24 (Viorel): when the phone's screen turns off, every window of the app closes after 3
	 * seconds and the app itself after 10, so the phone's home screen shows when it turns on again.
	 * The times are checked again when the screen comes on (a sleeping phone can hold the timers back).
	 */
	private long screenOffAt;
	private boolean screenWatch;
	private final Runnable offClose = new Runnable() {
		public void run() {
			closeWindows();
		}
	};
	private final Runnable offQuit = new Runnable() {
		public void run() {
			quitAfterScreenOff();
		}
	};
	private final android.content.BroadcastReceiver screen = new android.content.BroadcastReceiver() {
		@Override
		public void onReceive(Context c, Intent i) {
			if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
				screenOffAt = android.os.SystemClock.elapsedRealtime();
				handler.removeCallbacks(offClose);
				handler.removeCallbacks(offQuit);
				handler.postDelayed(offClose, 3000);
				handler.postDelayed(offQuit, 10000);
				Log.i(MainActivity.this, "APP", "ecran stins");
			} else if (Intent.ACTION_SCREEN_ON.equals(i.getAction()) && screenOffAt > 0) {
				long off = android.os.SystemClock.elapsedRealtime() - screenOffAt;
				screenOffAt = 0;
				handler.removeCallbacks(offClose);
				handler.removeCallbacks(offQuit);
				if (off >= 10000) quitAfterScreenOff();
				else if (off >= 3000) closeWindows();
			}
		}
	};

	private void watchScreen(boolean on) {
		if (on == screenWatch) return;
		screenWatch = on;
		try {
			if (on) {
				IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
				f.addAction(Intent.ACTION_SCREEN_ON);
				registerReceiver(screen, f);
			} else unregisterReceiver(screen);
		} catch (Exception e) {
			// already (un)registered
		}
	}

	/** Every open window closes (not a window that must be answered, such as the first start's). */
	private void closeWindows() {
		if (sizer != null && sizer.isOpen()) sizer.hide();
		if (compassPanel != null && compassPanel.isOpen()) compassPanel.hide();
		if (bubble != null && bubble.isOpen()) bubble.hide();
		if (settings != null && settings.isOpen()) settings.hide();
		if (help != null && help.isOpen()) help.hide();
		if (sheet != null && sheet.isOpen() && sheet.isCancelable()) sheet.hide();
		if (s != null && s.quakeOn) closeQuakeInfo();
	}

	private void quitAfterScreenOff() {
		screenOffAt = 0;
		closeWindows();
		if (updater != null && updater.working()) return; // an update being downloaded goes on
		Log.i(this, "APP", "închisă: ecranul a fost stins 10 s");
		finish();
	}

	/**
	 * v5.25 (Viorel): a tap on a widget while the app waits in the background with a window open:
	 * every window closes and the main screen of the widget's place (the town) shows.
	 */
	@Override
	protected void onNewIntent(Intent i) {
		super.onNewIntent(i);
		setIntent(i);
		if (i != null && i.getBooleanExtra(OPEN_QUAKE, false)) {
			Log.i(this, "APP", "deschisă din notificarea de cutremur");
			closeWindows();
			quakeWanted = true;
			openQuakeIfWanted();
			return;
		}
		if (i == null || !i.getBooleanExtra(FROM_WIDGET, false)) return;
		Log.i(this, "APP", "deschisă din widget");
		closeWindows();
		if (sheet != null && sheet.isOpen() && sheet.isCancelable()) sheet.hide();
		if (s != null && s.farmer && pageId != 0) showPage(0);
		if (view != null) view.invalidate();
	}

	/** v5.23 (Viorel): a bubble open when the screen turns off (or the app leaves) closes; it came back empty. */
	@Override
	protected void onStop() {
		super.onStop();
		if (bubble != null) bubble.hide();
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
		if (compassPanel != null) compassPanel.hide();
		handler.removeCallbacks(tick);
		listenNet(false);
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
		// v3.71: on Android 9+ the page is laid out under the hidden status bar (LAYOUT_FULLSCREEN), so
		// the cutout's insets reach cutout(); before, LAYOUT_STABLE alone kept a bar's height free on top
		// and the camera band was never used (A54 log 2026-11-09: no "decupaj cameră" line).
		int lay = Build.VERSION.SDK_INT >= 28 ? View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN : 0;
		view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | lay | (s.dark ? 0 : 0x10));
		getWindow().setNavigationBarColor(ro.meteoalarm.android.core.Theme.page(s.dark));
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
				s.aqi = Weather.parseAqi(p, s.city.us());
			}
			s.warns = Fetcher.warnings(this, s.city);
			s.quakes = Fetcher.quakes(this, s.city);
			s.farm = Fetcher.farm(this, s.city);
			s.failReason = prefs.getString(Tr.t("failReasonEn", "failReason"), "");
			s.source = prefs.getString("source", "");
		} catch (Exception e) {
			s.w = null;
			Log.e(this, "DATE", "vremea salvată nu a putut fi citită", e);
		}
	}

	private void fetch() {
		if (fetching) return;
		if (pageId != 0) {
			fetchParcel();
			return;
		}
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
				// Farmer mode (v2.50): the parcels' alerts and the data promised, while the app is open.
				if (Parcels.anything(ctx)) Parcels.background(ctx);
			}
		}).start();
	}

	private void done(Cities.City c, Fetcher.Result r) {
		fetching = false;
		lastDone = System.currentTimeMillis();
		s.busy = false;
		if (c != s.city) { // the town (or the main screen) changed meanwhile
			if (c == town) {
				Fetcher.save(this, c, r);
				if (r.forecast != null) Widget.show(this);
			}
			fetch();
			return;
		}
		long now = System.currentTimeMillis();
		Fetcher.save(this, c, r);
		if (r.forecast != null) {
			s.w = Weather.parse(r.forecast);
			if (r.uv != null) Weather.addExtra(s.w, r.uv);
			s.fetched = now;
			// v3.90 (Viorel): the weather icons move again after every refresh.
			view.replayIcon();
			s.offline = false;
			s.failReason = "";
			s.source = r.source;
			Widget.show(this);
		} else {
			// Why it did not come: under the line ("no data since …") and in a notice now.
			s.failReason = Fetcher.reason(r.error, s.T.en);
			if (s.w == null) s.error = (Tr.t("The weather did not come: ", "Vremea nu a venit: ")) + s.failReason;
			else if (resumed) note((Tr.t("The weather did not come: ", "Vremea nu a venit: ")) + s.failReason, 3000);
		}
		if (r.pollen != null) {
			try {
				Object[] pr = Weather.parsePollen(r.pollen);
				s.pollenLvl = (Integer) pr[0];
				s.pollenPlant = (String) pr[1];
				s.aqi = Weather.parseAqi(r.pollen, c.us());
			} catch (Exception e) {
				// keep the last pollen
			}
		}
		if (r.warn != null) s.warns = Fetcher.warnings(this, c);
		if (r.quake != null || r.infp != null) s.quakes = Fetcher.quakes(this, c);
		if (r.quake != null || r.infp != null) openQuakeIfWanted();
		if (r.quake != null || r.infp != null) refreshQuakeInfo();
		if (r.farm != null) {
			try {
				s.farm = ro.meteoalarm.android.core.Farm.parse(r.farm);
			} catch (Exception e) {
				// keep the last one
			}
		} else if (s.farmer && r.error == null) {
			// Open-Meteo gave no farmer's data (MET Norway has none): said once, notified when they come.
			Parcels.pendingMain(this);
			if (resumed) note(Tr.t("Farmer's data: Open-Meteo is not answering; they are downloaded when it answers again", "Date fermier: Open-Meteo nu răspunde; se descarcă atunci când răspunde din nou"), 3500);
		}
		view.invalidate();
	}

	/** Farmer mode on or off (☰, first item; v2.50). */
	private void toggleFarmer() {
		boolean on = !prefs.getBoolean("farmer", false);
		prefs.edit().putBoolean("farmer", on).apply();
		Log.i(this, "FERMIER", on ? "mod fermier pornit" : "mod normal");
		s.farmer = on;
		s.hours.scroll = 0;
		s.rain.scroll = 0;
		// Back to the town's screen when leaving farmer mode; the dots when coming in (v2.60).
		if (!on && pageId != 0) showPage(0);
		pageState();
		view.invalidate();
		note(on ? Tr.t("Farmer mode: tap an hour or a rain box for details", "Mod Fermier: atinge o oră sau o casetă de ploaie pentru detalii")
				: Tr.t("Normal mode", "Mod Normal"), 2500);
		if (on && s.farm == null) fetchFarm();
	}

	/** Downloads only the farmer's forecast for the town shown. */
	private long lastFarmTry;

	private void fetchFarm() {
		lastFarmTry = System.currentTimeMillis();
		if (pageId != 0) {
			fetch();
			return;
		}
		final Cities.City c = s.city;
		final Context ctx = getApplicationContext();
		new Thread(new Runnable() {
			public void run() {
				String json = null;
				try {
					json = Weather.get(ro.meteoalarm.android.core.Farm.url(c.lat, c.lon, Fetcher.tz(c), ro.meteoalarm.android.core.Farm.KEEP_DAYS, ro.meteoalarm.android.core.Farm.AHEAD));
					ro.meteoalarm.android.core.Farm.parse(json);
					Fetcher.saveFarm(ctx, c, json);
					Log.i(ctx, "FERMIER", "prognoza pentru fermieri " + c.name + " OK");
				} catch (Exception e) {
					json = null;
					Log.e(ctx, "FERMIER", "prognoza pentru fermieri a eșuat: " + Fetcher.reason(e, false));
				}
				final String got = json;
				handler.post(new Runnable() {
					public void run() {
						if (c != s.city) return;
						if (got != null) s.farm = ro.meteoalarm.android.core.Farm.parse(got);
						else {
							Parcels.pendingMain(MainActivity.this);
							note(Tr.t("Farmer's data: Open-Meteo is not answering; they are downloaded when it answers again", "Date fermier: Open-Meteo nu răspunde; se descarcă atunci când răspunde din nou"), 3500);
						}
						view.invalidate();
					}
				});
			}
		}).start();
	}

	/** Farmer mode: a tap on an hour box (KIND 0) or a rain box (1), at B: its details in a bubble. */
	public void onFarmBox(final int kind, final int k, final float[] b) {
		Log.i(this, "GEST", "fermier: atingere casetă " + (kind == 0 ? "oră" : kind == 1 ? "ploaie" : s.r24At(k) ? "apa în sol" : "parcelă") + " " + k);
		reopen = new Runnable() {
			public void run() {
				onFarmBox(kind, k, s.farmBoxRect(kind, k));
			}
		};
		List<String[]> info = kind == 0 ? (s.mergedShown ? s.hourRainInfo(k) : s.hourInfo(k)) : kind == 1 ? s.rainInfo(k) : kind == 2 ? s.parcelInfo(k) : s.rain24Info();
		if (info.isEmpty()) return;
		boolean missing = kind <= 1 ? s.farm == null || s.farmIndex(k) < 0
				: s.r24At(k) ? Double.isNaN(s.rain24()) : s.farm == null || s.farm.nowIndex(s.now) < 0;
		if (missing) {
			// Told that the data come later: they are fetched and notified (Viorel 2026-11-07).
			if (pageId != 0) Parcels.pending(this, pageId);
			else Parcels.pendingMain(this);
			fetchFarm();
		}
		List<Bubble.Row> rows = Bubble.info(info);
		bubble.panel = true;
		bubble.place = s.place();
		bubble.show(view.px(b), bubbleWidth(0.86f, 280), info.get(0)[1], rows, s.T.en, new Bubble.Pick() {
			public void picked(int i) {
			}
		});
	}

	/** v4.00 (Viorel): a tap on the Compass box: the big compass, the GPS coordinates and the nearest towns. */
	public void onCompass() {
		Log.i(this, "GEST", "atingere casetă busolă");
		double la = s.city != null ? s.city.lat : Double.NaN, lo = s.city != null ? s.city.lon : Double.NaN;
		compassPanel.place = s.place();
		compassPanel.show(cities, la, lo, s.heading, s.dark, s.unitF, s.w);
	}

	/** v4.00 (Viorel): a tap on a box of the bottom row: its value on top, the explanations under it. */
	public void onInfoBox(final int k, final float[] b) {
		Log.i(this, "GEST", "atingere casetă " + s.infoId(k));
		reopen = new Runnable() {
			public void run() {
				onInfoBox(k, s.infoRect(k));
			}
		};
		infoBubble(s.boxInfo(k), b);
	}

	/** v4.00: a tap on an hour box (KIND 0) or a day box (1) outside the farmer mode. */
	public void onBox(final int kind, final int k, final float[] b) {
		Log.i(this, "GEST", "atingere casetă " + (kind == 0 ? "oră" : "zi") + " " + k);
		reopen = new Runnable() {
			public void run() {
				onBox(kind, k, s.boxRect(kind, k));
			}
		};
		infoBubble(kind == 0 ? s.townHourInfo(k) : s.dayInfo(k), b);
	}

	private void infoBubble(List<String[]> info, float[] b) {
		if (info.isEmpty()) return;
		bubble.panel = true;
		bubble.place = s.place();
		bubble.show(view.px(b), bubbleWidth(0.86f, 280), info.get(0)[1], Bubble.info(info), s.T.en, new Bubble.Pick() {
			public void picked(int i) {
			}
		});
	}

	/** v3.90 (Viorel): a tap on the precipitation chart: what it shows, in a bubble. */
	public void onPrecip(final float[] b, final int part) {
		Log.i(this, "GEST", "atingere grafic precipitații" + (part >= 0 ? ", caseta " + (part + 1) : ""));
		reopen = new Runnable() {
			public void run() {
				onPrecip(b.clone(), part);
			}
		};
		List<String[]> info = new ArrayList<String[]>();
		// v3.91 (Viorel): a box of 4 hours is "Precipitation (from–to)"; first each hour's chance and amount, then the help.
		info.add(new String[] { "", part >= 0 ? s.T.precipUp + " (" + s.partRange(part) + ")" : s.T.precipUp + " · " + s.T.next12 });
		if (s.w != null) info.add(s.partTable(part));
		info.add(new String[] { "h", Tr.t("What the chart shows", "Ce arată graficul") });
		info.add(new String[] { "t", Tr.t("One bar for each of the next 12 hours; its height is the chance of rain or snow in that hour (0–100%).",
				"O bară pentru fiecare din următoarele 12 ore; înălțimea ei e probabilitatea de ploaie sau ninsoare în acea oră (0–100%).") });
		info.add(new String[] { "t", Tr.t("Coloured bars: precipitation expected (a chance of 50% or more, or at least 0.1 mm), in the snow colour when it snows. Grey bars: dry.",
				"Barele colorate: precipitații așteptate (probabilitate de 50% sau mai mult, ori cel puțin 0,1 mm), în culoarea zăpezii când ninge. Barele gri: fără precipitații.") });
		info.add(new String[] { "t", Tr.t("The dotted line marks 50%; “now” shows the current time. On top: when it rains (from–to) and the highest chance.",
				"Linia punctată arată 50%; „acum” arată ora curentă. Sus: când plouă (de la–până la) și probabilitatea cea mai mare.") });
		info.add(new String[] { "s", ro.meteoalarm.android.core.Screen.srcOM() });
		bubble.panel = true;
		bubble.place = s.place();
		bubble.show(view.px(b), bubbleWidth(0.86f, 280), info.get(0)[1], Bubble.info(info), s.T.en, new Bubble.Pick() {
			public void picked(int i) {
			}
		});
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
		Dlg.show(d);
	}

	public void onRefresh() {
		view.replayIcon();
		long since = System.currentTimeMillis() - lastDone;
		// Pulled again and again (A70 log, 4 times in 3 s): one download at a time, and none again
		// within 10 seconds of the last one; the notice says why.
		if (fetching) {
			Log.i(this, "GEST", "reîmprospătare cerută, dar descărcarea e deja în curs");
			note(Tr.t("Already updating…", "Se actualizează deja…"));
			return;
		}
		if (since < REPULL_MS) {
			Log.i(this, "GEST", "reîmprospătare cerută la " + since / 1000 + " s după ultima, nu descarc iar");
			note(s.failReason.length() == 0 ? (Tr.t("The weather has just been updated", "Vremea tocmai a fost actualizată"))
					: (Tr.t("Just tried; try again in a few seconds", "Tocmai am încercat; mai încearcă peste câteva secunde")));
			return;
		}
		Log.i(this, "GEST", "reîmprospătare cerută");
		note(Tr.t("Updating the weather…", "Actualizez vremea…"));
		fetch();
	}

	/**
	 * ☰ › Trimite aplicația (Viorel 2026-11-04): the download link of the app in a WhatsApp
	 * message, so someone else can install it; without WhatsApp, the phone's share window.
	 */
	private void shareApp() {
		String repo = "https://github.com/" + ro.meteoalarm.android.core.Release.REPO;
		String ver = updater.installed();
		String msg = Tr.t("Meteo Alarm: the weather, weather warnings and earthquakes where you are, free and without ads.",
				"Meteo Alarm: vremea, avertizările meteo și cutremurele de unde ești, gratuit și fără reclame.")
				+ "\n\n" + Tr.t("Download the app (Android):", "Descarcă aplicația (Android):") + "\n" + repo + "/releases/download/v" + updater.installedCode() + "." + ver + "/MeteoAlarmAndroid-v" + ver + ".apk"
				+ "\n\n" + Tr.t("Open the downloaded file and install it. The phone may warn that the app does not come from Google Play: choose \"Install anyway\". The app then updates itself from GitHub.",
						"Deschide fișierul descărcat și instalează-l. Telefonul poate avertiza că aplicația nu vine din Google Play: alege „Instalează oricum”. Apoi aplicația se actualizează singură de pe GitHub.")
				+ "\n" + Updater.autoBlocker()
				+ "\n" + Tr.t("All versions:", "Toate versiunile:") + " " + repo + "/releases";
		Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg);
		for (String pkg : new String[] { "com.whatsapp", "com.whatsapp.w4b" }) {
			try {
				startActivity(new Intent(send).setPackage(pkg));
				Log.i(this, "MENIU", "link de descărcare trimis prin " + pkg);
				return;
			} catch (android.content.ActivityNotFoundException e) {
				// not installed: the next one
			}
		}
		Log.i(this, "MENIU", "WhatsApp lipsește: fereastra de partajare a telefonului");
		try {
			startActivity(Intent.createChooser(send, Tr.t("Send the app", "Trimite aplicația")));
		} catch (Exception e) {
			Log.e(this, "MENIU", "partajare", e);
		}
	}

	/** A menu / help icon (Screen.menuIcon) as a bitmap. */
	/** v2.001: the small icons of the Parcels page's buttons, for Help (drawn like on the page). */
	private android.graphics.Bitmap glyph(String k) {
		float d = getResources().getDisplayMetrics().density;
		int px = Math.round(40 * d);
		android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888);
		android.graphics.Canvas c = new android.graphics.Canvas(b);
		c.scale(px / 64f, px / 64f);
		android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
		int ink = ro.meteoalarm.android.core.Theme.ink(s.dark);
		p.setColor(ink);
		p.setStyle(android.graphics.Paint.Style.STROKE);
		p.setStrokeWidth(4.5f);
		p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
		p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
		android.graphics.Path q = new android.graphics.Path();
		switch (k) {
		case "plus":
			c.drawLine(32, 14, 32, 50, p);
			c.drawLine(14, 32, 50, 32, p);
			break;
		case "pin":
			p.setStyle(android.graphics.Paint.Style.FILL);
			p.setColor(0xFFD97757);
			q.moveTo(32, 56);
			q.lineTo(21, 30);
			q.lineTo(43, 30);
			q.close();
			c.drawPath(q, p);
			c.drawCircle(32, 24, 14, p);
			p.setColor(0xFFFFFFFF);
			c.drawCircle(32, 24, 6, p);
			break;
		case "point":
		case "circle":
			p.setColor(0xFFE0A800);
			if (k.equals("circle")) c.drawCircle(32, 32, 20, p);
			else {
				q.moveTo(12, 20);
				q.lineTo(50, 14);
				q.lineTo(54, 48);
				q.lineTo(16, 52);
				q.close();
				c.drawPath(q, p);
			}
			float[][] pts = k.equals("circle") ? new float[][] { { 32, 12 }, { 51, 26 }, { 44, 48 }, { 20, 48 }, { 13, 26 } } : new float[][] { { 12, 20 }, { 50, 14 }, { 54, 48 }, { 16, 52 } };
			for (float[] t : pts) {
				p.setStyle(android.graphics.Paint.Style.FILL);
				p.setColor(0xFFFFFFFF);
				c.drawCircle(t[0], t[1], 6, p);
				p.setStyle(android.graphics.Paint.Style.STROKE);
				p.setStrokeWidth(3);
				p.setColor(0xFFE0A800);
				c.drawCircle(t[0], t[1], 6, p);
			}
			break;
		case "save":
			p.setColor(ro.meteoalarm.android.core.Theme.accent(s.dark));
			q.moveTo(14, 14);
			q.lineTo(42, 14);
			q.lineTo(50, 22);
			q.lineTo(50, 50);
			q.lineTo(14, 50);
			q.close();
			c.drawPath(q, p);
			c.drawRect(22, 14, 40, 25, p);
			p.setStyle(android.graphics.Paint.Style.FILL);
			c.drawRoundRect(new android.graphics.RectF(20, 34, 44, 49), 2, 2, p);
			break;
		case "pencil":
			p.setStyle(android.graphics.Paint.Style.FILL);
			q.moveTo(14, 50);
			q.lineTo(18, 38);
			q.lineTo(42, 14);
			q.lineTo(50, 22);
			q.lineTo(26, 46);
			q.close();
			c.drawPath(q, p);
			break;
		case "bars":
			p.setStyle(android.graphics.Paint.Style.FILL);
			c.drawRect(17, 34, 24, 46, p);
			c.drawRect(28.5f, 22, 35.5f, 46, p);
			c.drawRect(40, 28, 47, 46, p);
			p.setStyle(android.graphics.Paint.Style.STROKE);
			c.drawLine(13, 50, 51, 50, p);
			break;
		case "bin":
			c.drawLine(16, 20, 48, 20, p);
			c.drawLine(26, 20, 28, 13, p);
			c.drawLine(28, 13, 36, 13, p);
			c.drawLine(36, 13, 38, 20, p);
			q.moveTo(20, 24);
			q.lineTo(23, 52);
			q.lineTo(41, 52);
			q.lineTo(44, 24);
			c.drawPath(q, p);
			c.drawLine(28, 30, 29, 46, p);
			c.drawLine(36, 30, 35, 46, p);
			break;
		}
		return b;
	}

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

	/**
	 * v5.02 (Viorel): on a phone the Settings open in portrait (in landscape they do not fit): ☰ in
	 * landscape turns the screen first, then opens them. The screen stays in portrait while they or a
	 * window opened from them is open, and follows the phone again when all are closed.
	 * Returns false when the menu opens later (after the turn).
	 */
	private boolean menuLock, menuAfterTurn;

	private boolean lockPortrait() {
		if (TextSize.tablet(this)) return true;
		boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
		setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		if (!menuLock) {
			menuLock = true;
			handler.postDelayed(unlockCheck, 800);
		}
		if (land) {
			menuAfterTurn = true;
			handler.postDelayed(new Runnable() {
				public void run() {
					// The screen did not turn (in time): open them anyway.
					if (menuAfterTurn) {
						menuAfterTurn = false;
						onMenu();
					}
				}
			}, 1500);
			return false;
		}
		return true;
	}

	/** Lets the screen turn again once the Settings and the windows over them are closed. */
	private final Runnable unlockCheck = new Runnable() {
		public void run() {
			if (!menuLock) return;
			if (menuAfterTurn || settings.isOpen() || bubble.isOpen() || help.isOpen() || sheet.isOpen()) {
				handler.postDelayed(this, 500);
				return;
			}
			menuLock = false;
			setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
		}
	};


	/**
	 * v5.00 (Viorel, mockups machete-meniu-setari): ☰ opens the Settings window: the settings by
	 * section, in cards, and the fixed bar SISTEM & SUPORT under them. Refresh left the menu (pull
	 * the screen down, or tap the "updated" pill). Changes show at once in the open window.
	 */
	public void onMenu() {
		if (!settings.isOpen() && !lockPortrait()) return; // v5.02: opens after the turn to portrait
		if (!settings.isOpen()) Log.i(this, "BUTON", "meniu ☰ (setări)");
		reopen = new Runnable() {
			public void run() {
				onMenu();
			}
		};
		final SettingsPanel p = settings;
		float zk = (TextSize.tablet(this) ? 1.11f : 1f) * TextSize.get(this);
		p.begin(s.dark, Tr.t("App settings", "Setări aplicație").toUpperCase(), Tr.t("Make it yours", "Personalizează-ți experiența"), zk);
		// Location & favourites.
		final boolean eu = Region.showEU(this), us = Region.showUS(this);
		if (us) seedUSFavs();
		p.section(Tr.t("Location & favourites", "Localizare & Favorite"), false);
		final Runnable towns = new Runnable() {
			public void run() {
				onCity(s.btnMenu);
			}
		};
		if (eu) {
			p.favHead(us ? Tr.t("Favourites EU", "Favorite UE") : Tr.t("Favourites", "Favorite"), Tr.t("Add", "Adaugă"), towns);
			for (final Cities.City c : favList("favs")) p.fav(c.label(), town != null && c.same(town) && pageId == 0, new Runnable() {
				public void run() {
					Log.i(MainActivity.this, "LOC", "aleasă din favorite (setări): " + c.label());
					choose(c);
					onMenu();
				}
			});
		}
		if (us) {
			List<Cities.City> fu = favList("favsUS");
			if (eu) {
				p.line();
				p.favHead(Tr.t("Favourites USA", "Favorite SUA"), null, null);
				List<String> names = new ArrayList<String>();
				List<Runnable> taps = new ArrayList<Runnable>();
				for (final Cities.City c : fu) {
					names.add(c.label());
					taps.add(new Runnable() {
						public void run() {
							Log.i(MainActivity.this, "LOC", "aleasă din favorite SUA (setări): " + c.label());
							choose(c);
							onMenu();
						}
					});
				}
				p.chips(names, taps);
			} else {
				p.favHead(Tr.t("Favourites", "Favorite"), Tr.t("Add", "Adaugă"), towns);
				for (final Cities.City c : fu) p.fav(c.label(), town != null && c.same(town) && pageId == 0, new Runnable() {
					public void run() {
						choose(c);
						onMenu();
					}
				});
			}
		}
		StringBuilder some = new StringBuilder();
		int n = 0;
		for (String co : eu ? cities.byCounty.keySet() : cities.usStates.keySet()) {
			if (n++ == 3) break;
			some.append(n > 1 ? ", " : "").append(co);
		}
		p.link("map", eu ? Tr.t("Choose the county", "Alegeți județul") : Tr.t("Choose the state", "Alegeți statul"), some + " …", null, towns);
		// Preferences & display.
		p.section(Tr.t("Preferences & display", "Preferințe & Afișaj"), false);
		final boolean farmer = prefs.getBoolean("farmer", false);
		p.toggle("farm", Tr.t("Farmer mode", "Mod Fermier"), Tr.t("Weather for your land plots", "Vremea pentru parcelele tale"), farmer, new Runnable() {
			public void run() {
				toggleFarmer();
				onMenu();
			}
		});
		if (farmer) p.subLink("parcel", Tr.t("Parcels", "Parcele"), Tr.t("Your land plots, map, statistics", "Parcelele tale, hartă, statistici"), new Runnable() {
			public void run() {
				settings.hide();
				openParcels();
			}
		});
		p.segmented("thermo", Tr.t("Temperature", "Temperatură"), new String[] { "°C", "°F" }, s.unitF ? 1 : 0, new SettingsPanel.Choice() {
			public void chose(int k) {
				if ((k == 1) != s.unitF) toggleUnits();
				onMenu();
			}
		});
		p.segmented("clock", Tr.t("Clock", "Ceas"), new String[] { "12 h", "24 h" }, s.clk12 ? 0 : 1, new SettingsPanel.Choice() {
			public void chose(int k) {
				if ((k == 0) != s.clk12) {
					Region.setClk12(MainActivity.this, s.city, k == 0);
					Log.i(MainActivity.this, "MENIU", "ceas " + (k == 0 ? "12" : "24") + " ore");
					applySettings();
					view.invalidate();
				}
				onMenu();
			}
		});
		// v2.045 (Viorel): the language right after the clock.
		// v5.53 (Viorel): the languages as buttons in the menu, 2 on a row; the minorities' under
		// "Other languages", shown on a tap and hidden again once one is chosen.
		final List<String> lc = Lang.offered(this), lm = Lang.more(this);
		p.info("globe", Tr.t("Display language", "Limbă afișaj"), mini("lang-" + s.T.lang));
		p.langButtons(lc.toArray(new String[0]), langNames(lc), lc.indexOf(s.T.lang), new SettingsPanel.Choice() {
			public void chose(int k) {
				langMore = false;
				setLang(lc.get(k));
				onMenu();
			}
		});
		if (!lm.isEmpty()) {
			boolean mine = lm.contains(s.T.lang);
			p.wideButton(Tr.t("Other languages", "Alte limbi") + (mine ? " · " + Tr.name(s.T.lang) : ""), mine, langMore, new Runnable() {
				public void run() {
					langMore = !langMore;
					Log.i(MainActivity.this, "LIMBA", "alte limbi: " + (langMore ? "deschise " + lm : "închise"));
					onMenu();
				}
			});
			if (langMore) p.langButtons(lm.toArray(new String[0]), langNames(lm), lm.indexOf(s.T.lang), new SettingsPanel.Choice() {
				public void chose(int k) {
					langMore = false;
					setLang(lm.get(k));
					onMenu();
				}
			});
		}
		final int nt = ro.meteoalarm.android.core.Theme.NAMES.length;
		String[] tn = new String[nt];
		int[] tc = new int[nt];
		for (int k = 0; k < nt; k++) {
			tn[k] = Tr.t(ro.meteoalarm.android.core.Theme.NAMES[k][0], ro.meteoalarm.android.core.Theme.NAMES[k][1]);
			tc[k] = ro.meteoalarm.android.core.Theme.swatch(k);
		}
		final int pal = prefs.getInt("palette", 0);
		String cur = prefs.getString("theme", "auto");
		final String auto = hasLight() ? "sensor" : "auto";
		int mode = cur.equals("light") ? 0 : cur.equals("dark") ? 1 : 2;
		String[] modes = { Tr.t("Light", "Luminos"), Tr.t("Dark", "Întunecat"), Tr.t("Automatic", "Automat") };
		p.theme(Tr.t("Theme", "Temă"), tn[Math.max(0, Math.min(nt - 1, pal))] + " · " + modes[mode], tn, tc, pal, new SettingsPanel.Choice() {
			public void chose(int k) {
				prefs.edit().putInt("palette", k).apply();
				Log.i(MainActivity.this, "TEMA", "temă: " + ro.meteoalarm.android.core.Theme.NAMES[k][1]);
				applySettings();
				view.invalidate();
				onMenu();
			}
		}, modes, mode, new SettingsPanel.Choice() {
			public void chose(int k) {
				String[] m = { "light", "dark", auto };
				prefs.edit().putString("theme", m[k]).apply();
				Log.i(MainActivity.this, "TEMA", "mod: " + m[k]);
				applySettings();
				view.invalidate();
				onMenu();
			}
		});
		// v5.20 (Viorel): the weather icon theme: 6 coloured dots, the chosen one's name under the title; widgets too.
		final int nIc = ro.meteoalarm.android.core.WxIcon.SETS.length;
		String[] icn = new String[nIc];
		for (int k = 0; k < nIc; k++) icn[k] = Tr.t(ro.meteoalarm.android.core.WxIcon.SET_NAMES[k][0], ro.meteoalarm.android.core.WxIcon.SET_NAMES[k][1]);
		p.dots("sun", Tr.t("Icon theme", "Temă icon"), icn[ro.meteoalarm.android.core.WxIcon.set], icn, ro.meteoalarm.android.core.WxIcon.SET_DOTS,
				ro.meteoalarm.android.core.WxIcon.set, new SettingsPanel.Choice() {
					public void chose(int k) {
						prefs.edit().putInt("iconTheme", k).apply();
						ro.meteoalarm.android.core.WxIcon.set = k;
						Log.i(MainActivity.this, "TEMA", "temă icon: " + ro.meteoalarm.android.core.WxIcon.SETS[k]);
						applySettings();
						view.replayIcon();
						view.invalidate();
						Widget.show(MainActivity.this);
						onMenu();
					}
				});
		// Alerts & behaviour.
		p.section(Tr.t("Alerts & behaviour", "Alerte & Comportament"), false);
		// v2.045 (Viorel): the rows sorted alphabetically by their title in the shown language (a link stays under its switch).
		final List<Object[]> al = new ArrayList<Object[]>();
		al.add(new Object[] { Tr.t("Auto location", "Localizare auto"), new Runnable() {
			public void run() {
				p.toggle("locate", Tr.t("Auto location", "Localizare auto"), Tr.t("Follows your position by GPS", "Actualizează poziția prin GPS"), autoLoc(), new Runnable() {
					public void run() {
						onLocate();
						onMenu();
					}
				});
			}
		} });
		al.add(new Object[] { Tr.t("Earthquake", "Cutremur"), new Runnable() {
			public void run() {
				p.toggle("quake", Tr.t("Earthquake", "Cutremur"), Tr.t("Notifications for your place", "Notificări pentru locul tău"), Notifier.quakes(MainActivity.this), new Runnable() {
					public void run() {
						boolean on = !Notifier.quakes(MainActivity.this);
						prefs.edit().putBoolean("quakeNotify", on).apply();
						Log.i(MainActivity.this, "MENIU", "notificări cutremure: " + (on ? "PORNITE" : "OPRITE"));
						note(on ? Tr.t("Earthquake notices within 300 km of your place", "Notificări de cutremur la cel mult 300 km de locul tău")
								: Tr.t("No earthquake notices", "Fără notificări de cutremur"), 2200);
						onMenu();
					}
				});
				p.subLink("quakes", Tr.t("Earthquakes", "Cutremure"), Tr.t("Time, place, magnitude, distance", "Ora, locul, magnitudinea, distanța"), new Runnable() {
					public void run() {
						settings.hide();
						quakesPage();
					}
				});
			}
		} });
		if (us) al.add(new Object[] { Tr.t("Tornadoes", "Tornade"), new Runnable() {
			public void run() {
				p.toggle("tornado", Tr.t("Tornadoes", "Tornade"), Tr.t("Notifications for your place", "Notificări pentru locul tău"), Notifier.tornadoes(MainActivity.this), new Runnable() {
					public void run() {
						boolean on = !Notifier.tornadoes(MainActivity.this);
						prefs.edit().putBoolean("tornadoNotify", on).apply();
						Log.i(MainActivity.this, "MENIU", "notificări tornade: " + (on ? "PORNITE" : "OPRITE"));
						note(on ? Tr.t("Tornado warnings for your town", "Avertizări de tornadă pentru localitatea ta") : Tr.t("No tornado notices", "Fără notificări de tornadă"), 2200);
						onMenu();
					}
				});
				p.subLink("alert", Tr.t("Alerts and reports nearby", "Alerte și raportări din jur"), Tr.t("What to do in a tornado", "Ce faci la tornadă"), new Runnable() {
					public void run() {
						settings.hide();
						tornadoPage();
					}
				});
			}
		} });
		al.add(new Object[] { Tr.t("Notifications", "Notificări"), new Runnable() {
			public void run() {
				p.toggle("bell", Tr.t("Notifications", "Notificări"), Tr.t("Rain, snow, warnings", "Ploaie, ninsoare, avertizări"), Notifier.on(MainActivity.this), new Runnable() {
					public void run() {
						toggleNotify();
						onMenu();
					}
				});
			}
		} });
		al.add(new Object[] { Tr.t("Night clock", "Ceas noapte"), new Runnable() {
			public void run() {
				p.toggle("moon", Tr.t("Night clock", "Ceas noapte"), Tr.t("Clock while charging", "Afișare ceas la încărcare"), prefs.getBoolean("nightClock", true), new Runnable() {
					public void run() {
						boolean on = !prefs.getBoolean("nightClock", true);
						prefs.edit().putBoolean("nightClock", on).apply();
						Log.i(MainActivity.this, "MENIU", "ceas noapte " + on);
						applySettings();
						onMenu();
					}
				});
			}
		} });
		al.add(new Object[] { Tr.t("Screen on", "Ecran aprins"), new Runnable() {
			public void run() {
				p.toggle("screen", Tr.t("Screen on", "Ecran aprins"), Tr.t("The screen does not go off", "Ecranul nu se stinge"), prefs.getBoolean("keepOn", true), new Runnable() {
					public void run() {
						boolean on = !prefs.getBoolean("keepOn", true);
						prefs.edit().putBoolean("keepOn", on).apply();
						Log.i(MainActivity.this, "MENIU", "ecran aprins " + on);
						applySettings();
						onMenu();
					}
				});
			}
		} });
		final java.text.Collator coll = java.text.Collator.getInstance(new java.util.Locale(s.T.lang));
		java.util.Collections.sort(al, new java.util.Comparator<Object[]>() {
			public int compare(Object[] x, Object[] y) {
				return coll.compare((String) x[0], (String) y[0]);
			}
		});
		for (Object[] r : al) ((Runnable) r[1]).run();

		// The hidden US test mode (not in Help nor in the change log): only while it is on.
		if (Region.test(this)) {
			p.section(Tr.t("Hidden mode", "Mod ascuns"), true);
			p.toggle("flask", Tr.t("Test mode · USA", "Mod de test · SUA"), Tr.t("On · the app believes it is in the US", "Pornit · aplicația se crede în SUA"), true, new Runnable() {
				public void run() {
					prefs.edit().putBoolean("usTest", false).apply();
					Log.i(MainActivity.this, "US", "modul de test OPRIT (din setări)");
					note(Tr.t("Test mode off", "Mod de test oprit"), 2000);
					applySettings();
					view.invalidate();
					onMenu();
				}
			});
			String st = prefs.getString("usTestState", "OK");
			p.link("map", Tr.t("Test state", "Stat de test"), null, Cities.stateName(st) + " (" + st + ")", new Runnable() {
				public void run() {
					testMode();
				}
			});
			p.note(Tr.t("Shown only while the test mode is on (turned on with 7 taps on the About title). Off: the app goes back to Europe and this card goes away.",
					"Apare doar cât timp modul de test e pornit (se pornește cu 7 atingeri pe titlul Despre). Oprit = aplicația revine la Europa, iar cardul dispare."));
		}
		// The fixed bar.
		p.footTitle(Tr.t("System & support", "Sistem & Suport"));
		p.footRow(new String[] { "widget", "download>upload", "log" },
				new String[] { Tr.t("Widgets", "Widget-uri"), Tr.t("Export / Import", "Export / Import"), Tr.t("Log", "Jurnal") }, new Runnable[] { new Runnable() {
					public void run() {
						settings.hide();
						startActivity(new Intent(MainActivity.this, WidgetConfig.class));
					}
				}, new Runnable() {
					public void run() {
						settings.hide();
						backup();
					}
				}, new Runnable() {
					public void run() {
						settings.hide();
						startActivity(new Intent(MainActivity.this, LogActivity.class));
					}
				} });
		p.footRow(new String[] { "sync", "plane", "help", "info" },
				new String[] { Tr.t("Update", "Actualizare"), Tr.t("Share", "Partajare"), Tr.t("Help", "Ajutor"), Tr.t("About", "Despre") },
				new Runnable[] { new Runnable() {
					public void run() {
						settings.hide();
						updater.check(true);
					}
				}, new Runnable() {
					public void run() {
						settings.hide();
						shareApp();
					}
				}, new Runnable() {
					public void run() {
						settings.hide();
						help();
					}
				}, new Runnable() {
					public void run() {
						settings.hide();
						about();
					}
				} });
		p.show();
	}

	/**
	 * ☰ › Temperature °C/°F (v2.30, Viorel 2026-11-04). From °F the question whether to switch to
	 * the metric system (°C, km/h, millimetres, hPa, kilometres, 24-hour clock); from °C straight to
	 * the imperial units (°F, mph, inches, inHg, miles). Kept for this kind of town (US or not).
	 */
	private void toggleUnits() {
		final Cities.City c = s.city;
		if (!s.unitF) {
			Region.setImperial(this, c, true);
			Log.i(this, "US", "unități imperiale pentru " + (c.us() ? "localitățile din SUA" : "celelalte localități"));
			applySettings();
			view.invalidate();
			note(Tr.t("°F · imperial units (mph, inches, inHg, miles)", "°F · unități imperiale (mph, inch, inHg, mile)"), 2500);
			return;
		}
		AlertDialog.Builder d = dialog();
		d.setTitle(Tr.t("Switch to the metric system?", "Treci la sistemul metric?"));
		d.setMessage(Tr.t("°C, km/h, millimetres, hPa, kilometres and the 24-hour clock instead of °F, mph, inches, inHg, miles and the 12-hour clock. You can switch back anytime from ☰ › Temperature °C/°F.",
				"°C, km/h, milimetri, hPa, kilometri și ceasul de 24 de ore în loc de °F, mph, inch, inHg, mile și ceasul de 12 ore. Poți reveni oricând din ☰ › Temperatura °C/°F."));
		d.setPositiveButton(Tr.t("Metric", "Metric"), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface di, int w) {
				Region.setImperial(MainActivity.this, c, false);
				Region.setClk12(MainActivity.this, c, false);
				Log.i(MainActivity.this, "US", "sistemul metric ales pentru " + (c.us() ? "localitățile din SUA" : "celelalte localități"));
				applySettings();
				Widget.show(MainActivity.this);
				view.invalidate();
				note(Tr.t("°C · metric system", "°C · sistemul metric"), 2000);
			}
		});
		d.setNegativeButton(Tr.t("Keep °F", "Rămân la °F"), null);
		show(d);
	}

	// --- Export / Import (v4.30) ---------------------------------------------------------

	/** The counts {towns, favourites, parcels, settings} as lines. */
	private String countLines(int[] n) {
		return Tr.t("Towns", "Localități") + ": " + n[0] + "\n" + Tr.t("Favourites", "Favorite") + ": " + n[1] + "\n"
				+ Tr.t("Parcels", "Parcele") + ": " + n[2] + "\n" + Tr.t("Settings", "Setări") + ": "
				+ (n[3] > 0 ? Tr.t("Theme, language, units, clock, text size", "Temă, limbă, unități, ceas, mărimea textului") : "—");
	}

	/** ☰ › Export / Import (Viorel 2026-11-10): Save or Share this phone's data, or Import a file. */
	private void backup() {
		Log.i(this, "EXPORT", "fereastra Export / Import");
		AlertDialog.Builder d = dialog();
		d.setTitle(Tr.t("Export / Import", "Export / Import"));
		d.setMessage(Tr.t("Moves your towns, favourites, parcels and settings to another device through a file.",
				"Mută localitățile, favoritele, parcelele și setările pe alt dispozitiv, printr-un fișier.")
				+ "\n\n" + countLines(Backup.counts(this, null)));
		d.setNeutralButton(Tr.t("Import", "Import"), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface di, int w) {
				Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
				try {
					startActivityForResult(i, REQ_OPEN_DATA);
				} catch (Exception e) {
					Log.e(MainActivity.this, "IMPORT", "alegere fișier", e);
				}
			}
		});
		d.setNegativeButton(Tr.t("Save", "Salvează"), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface di, int w) {
				Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
						.putExtra(Intent.EXTRA_TITLE, Backup.fileName());
				try {
					startActivityForResult(i, REQ_SAVE_DATA);
				} catch (Exception e) {
					Log.e(MainActivity.this, "EXPORT", "salvare", e);
				}
			}
		});
		d.setPositiveButton(Tr.t("Share", "Trimite"), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface di, int w) {
				shareData();
			}
		});
		show(d);
	}

	/** The file through the phone's share menu: WhatsApp, e-mail, Google Drive, Bluetooth… */
	private void shareData() {
		java.io.File f = Backup.exportFile(this);
		if (f == null) { note(Tr.t("Could not save", "Nu s-a putut salva")); return; }
		Log.i(this, "EXPORT", "trimis " + f.getName() + " " + f.length() + " B");
		android.net.Uri u = ApkProvider.dataUri(f.getName());
		Intent send = new Intent(Intent.ACTION_SEND).setType("application/json")
				.putExtra(Intent.EXTRA_STREAM, u)
				.putExtra(Intent.EXTRA_SUBJECT, "Meteo Alarm — " + Tr.t("Export / Import", "Export / Import"))
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		send.setClipData(android.content.ClipData.newRawUri(f.getName(), u));
		try {
			startActivity(Intent.createChooser(send, Tr.t("Share", "Trimite")));
		} catch (Exception e) {
			Log.e(this, "EXPORT", "trimitere", e);
		}
	}

	private void saveData(android.net.Uri where) {
		try {
			java.io.OutputStream out = getContentResolver().openOutputStream(where);
			out.write(Backup.export(this).getBytes("UTF-8"));
			out.close();
			Log.i(this, "EXPORT", "salvat pe telefon");
			note(Tr.t("Saved: ", "Salvată: ") + Backup.fileName(), 2500);
		} catch (Exception e) {
			Log.e(this, "EXPORT", "salvare", e);
			note(Tr.t("Could not save", "Nu s-a putut salva"));
		}
	}

	/** Reads the picked file and asks Add or Replace. */
	private void openData(android.net.Uri from) {
		org.json.JSONObject o = null;
		try {
			java.io.InputStream in = getContentResolver().openInputStream(from);
			java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0 && b.size() < 4 * 1024 * 1024) b.write(buf, 0, n);
			in.close();
			o = Backup.read(b.toString("UTF-8"));
		} catch (Exception e) {
			Log.e(this, "IMPORT", "citire", e);
		}
		if (o == null) {
			Log.i(this, "IMPORT", "fișierul nu e un export Meteo Alarm");
			note(Tr.t("This file is not a Meteo Alarm export.", "Acest fișier nu este un export Meteo Alarm."), 3000);
			return;
		}
		final org.json.JSONObject data = o;
		String when = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.US).format(new java.util.Date(o.optLong("time")));
		Log.i(this, "IMPORT", "fișier din " + when + ", " + o.optString("device") + ", " + o.optString("version"));
		AlertDialog.Builder d = dialog();
		d.setTitle(Tr.t("Import", "Import"));
		d.setMessage(Tr.t("File from", "Fișier din") + " " + when + " · " + o.optString("device") + "\n\n" + countLines(Backup.counts(this, o)) + "\n\n"
				+ Tr.t("Add keeps what you have and adds what is missing; Replace makes everything as in the file.",
						"Adaugă păstrează ce ai și adaugă ce lipsește; Înlocuiește face totul ca în fișier."));
		d.setNeutralButton(Tr.t("Cancel", "Renunță"), null);
		d.setNegativeButton(Tr.t("Add", "Adaugă"), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface di, int w) {
				imported(data, false);
			}
		});
		d.setPositiveButton(Tr.t("Replace", "Înlocuiește"), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface di, int w) {
				imported(data, true);
			}
		});
		show(d);
	}

	private void imported(org.json.JSONObject o, boolean replace) {
		Backup.apply(this, o, replace);
		android.widget.Toast.makeText(this, Tr.t("Imported", "Importat"), android.widget.Toast.LENGTH_SHORT).show();
		Widget.show(this);
		// Again from the start: the town lists, the theme and the language as in the file.
		recreate();
	}

	/** ☰ › Parcels (farmer mode): the parcels' page, in the app's theme. */
	private void openParcels() {
		startActivityForResult(new Intent(this, FarmActivity.class).putExtra("dark", s.dark), REQ_PARCELS);
	}

	private Bubble.Row row(String text, String icon) {
		return new Bubble.Row(text, mini(icon), Bubble.Row.ITEM, false);
	}

	/**
	 * The languages (Viorel 2026-11-04): English, Romanian and the official languages of the
	 * country the phone is in (Lang.offered), each in its own language.
	 */
	private void pickLang() {
		reopen = new Runnable() {
			public void run() {
				pickLang();
			}
		};
		final List<String> codes = Lang.offered(this);
		final List<String> more = Lang.more(this);
		List<Bubble.Row> rows = Bubble.rows();
		for (String c : codes) rows.add(new Bubble.Row(Tr.name(c), mini("lang-" + c), Bubble.Row.ITEM, c.equals(s.T.lang)));
		// Romania: the minorities' languages in a submenu of their own (Viorel 2026-11-07).
		if (!more.isEmpty()) rows.add(new Bubble.Row(Tr.t("Other languages", "Alte limbi") + " ›", null, Bubble.Row.ITEM, more.contains(s.T.lang)));
		Log.i(this, "LIMBA", "lista de limbi: " + codes + (more.isEmpty() ? "" : " + " + more) + " (țara " + Lang.country(this) + ")");
		bubble.show(view.px(s.btnMenu), bubbleWidth(0.3f, 170), Tr.t("Display language", "Limbă afișaj"), rows, s.T.en, new Bubble.Pick() {
			public void picked(int k) {
				if (k >= codes.size()) {
					pickMinority(more);
					return;
				}
				setLang(codes.get(k));
			}
		});
	}

	/** ☰ › Limba › Mai multe limbi: the languages of Romania's minorities (v2.60). */
	private void pickMinority(final List<String> codes) {
		reopen = new Runnable() {
			public void run() {
				pickMinority(codes);
			}
		};
		Log.i(this, "LIMBA", "mai multe limbi: " + codes);
		List<Bubble.Row> rows = Bubble.rows();
		for (String c : codes) rows.add(new Bubble.Row(Tr.name(c), mini("lang-" + c), Bubble.Row.ITEM, c.equals(s.T.lang)));
		bubble.show(view.px(s.btnMenu), bubbleWidth(0.3f, 170), Tr.t("Languages of the minorities", "Limbile minorităților"), rows, s.T.en, new Bubble.Pick() {
			public void picked(int k) {
				setLang(codes.get(k));
			}
		});
	}

	/** v5.53: the minorities' languages open under the language buttons in ☰. */
	private boolean langMore;

	private static String[] langNames(List<String> codes) {
		String[] n = new String[codes.size()];
		for (int i = 0; i < n.length; i++) n[i] = Tr.name(codes.get(i));
		return n;
	}

	private void setLang(String c) {
		Log.i(this, "LIMBA", "aleasă: " + Tr.name(c) + " (" + c + ")");
		prefs.edit().putString("lang", c).apply();
		applySettings();
		Widget.show(this);
		view.invalidate();
		note(Tr.name(c));
	}

	/** Light, Dark, and Automatic (the phone's light sensor, when it has one). */
	/**
	 * v3.60 (Viorel): the 6 colour themes, a line, then Light and Dark with a box each (and
	 * Automatic); every theme has both sides.
	 */
	private void pickTheme() {
		reopen = new Runnable() {
			public void run() {
				pickTheme();
			}
		};
		final boolean en = s.T.en;
		final int nt = ro.meteoalarm.android.core.Theme.NAMES.length;
		int pal = prefs.getInt("palette", 0);
		String cur = prefs.getString("theme", "auto");
		List<Bubble.Row> rows = Bubble.rows();
		for (int k = 0; k < nt; k++) {
			String[] nm = ro.meteoalarm.android.core.Theme.NAMES[k];
			rows.add(new Bubble.Row(Tr.t(nm[0], nm[1]), null, Bubble.Row.ITEM, k == pal));
		}
		rows.add(new Bubble.Row("", null, Bubble.Row.LINE, false));
		final String auto = hasLight() ? "sensor" : "auto";
		rows.add(new Bubble.Row(Tr.t("Light", "Luminos"), mini("light"), Bubble.Row.ITEM, false).tick(cur.equals("light")));
		rows.add(new Bubble.Row(Tr.t("Dark", "Întunecat"), mini("dark"), Bubble.Row.ITEM, false).tick(cur.equals("dark")));
		rows.add(new Bubble.Row(Tr.t("Automatic", "Automat"), mini("sensor"), Bubble.Row.ITEM, false).tick(cur.equals("auto") || cur.equals("sensor")));
		bubble.show(view.px(s.btnMenu), bubbleWidth(0.3f, 160), Tr.t("Theme", "Temă"), rows, en, new Bubble.Pick() {
			public void picked(int k) {
				if (k < nt) {
					prefs.edit().putInt("palette", k).apply();
					Log.i(MainActivity.this, "TEMA", "temă: " + ro.meteoalarm.android.core.Theme.NAMES[k][1]);
				} else if (k > nt) {
					String[] modes = { "light", "dark", auto };
					prefs.edit().putString("theme", modes[k - nt - 1]).apply();
				}
				applySettings();
				view.invalidate();
			}
		});
	}

	/** The last lists of the earthquakes page: { country 7 days, Europe 48 h } (EMSC answers). */
	private String quakesCountry, quakesEurope, quakesKey;
	/** v2.035: INFP's JSON list for a town in Romania (null = not asked / not come). */
	private String quakesInfp;
	private long quakesAt;

	/**
	 * ☰ › Cutremure (Viorel 2026-11-04): a whole page like Help. On top the earthquakes of the
	 * last 7 days in the country of the town shown, then those of the last 48 hours in the EU,
	 * Ukraine and Moldova; magnitude 3 and over, from EMSC.
	 */
	private void quakesPage() {
		Log.i(this, "CUTREMUR", "pagina de cutremure deschisă");
		// The lists are of the town they were downloaded for (EMSC or USGS, v2.30).
		if (!town.key().equals(quakesKey)) {
			quakesCountry = null;
			quakesEurope = null;
			quakesInfp = null;
			quakesKey = town.key();
		}
		showQuakes();
		if (quakesCountry != null && System.currentTimeMillis() - quakesAt < 5 * 60000L) return;
		final Cities.City c = town;
		final Context ctx = getApplicationContext();
		new Thread(new Runnable() {
			public void run() {
				long now = System.currentTimeMillis();
				String a = null, b = null;
				String ci = null;
				// v2.035 (Viorel): in Romania INFP's official values first, then EMSC.
				if (Fetcher.infpFor(c)) try {
					ci = Fetcher.officialRaw(ctx, Fetcher.officialOf(c));
				} catch (Exception e) {
					Log.e(ctx, "CUTREMUR", "lista " + Fetcher.officialOf(c).name + " a eșuat: " + Fetcher.reason(e, false));
				}
				try {
					a = Weather.get(c.us() ? ro.meteoalarm.android.core.Quakes.usgsUrl(c.lat, c.lon, now - 7 * 86400000L)
							: ro.meteoalarm.android.core.Quakes.url(c.lat, c.lon, now - 7 * 86400000L));
				} catch (Exception e) {
					Log.e(ctx, "CUTREMUR", "lista țării (7 zile) a eșuat: " + Fetcher.reason(e, false));
				}
				try {
					b = Weather.get(c.us() ? ro.meteoalarm.android.core.Quakes.usUrl(now - 2 * 86400000L) : ro.meteoalarm.android.core.Quakes.europeUrl(now - 2 * 86400000L));
				} catch (Exception e) {
					Log.e(ctx, "CUTREMUR", "lista Europei (48 h) a eșuat: " + Fetcher.reason(e, false));
				}
				final String fa = a, fb = b, fi = ci;
				handler.post(new Runnable() {
					public void run() {
						if (!c.key().equals(quakesKey)) return;
						quakesCountry = fa == null ? "" : fa;
						quakesEurope = fb == null ? "" : fb;
						quakesInfp = fi;
						quakesAt = System.currentTimeMillis();
						if (help.isOpen() && quakesOpen) showQuakes();
					}
				});
			}
		}).start();
	}

	private boolean quakesOpen;

	private void showQuakes() {
		quakesOpen = true;
		tornOpen = false;
		help.begin(Tr.t("Earthquakes", "Cutremure"), Tr.t("Close", "Închide"), s.dark);
		help.setOnClose(new Runnable() {
			public void run() {
				quakesOpen = false;
				hideBars();
			}
		});
		Cities.City c = town;
		if (c.us()) {
			showQuakesUS(c);
			return;
		}
		String country = countries.shown(c.country);
		long now = System.currentTimeMillis();
		help.section(Tr.t("Last 7 days · ", "Ultimele 7 zile · ") + country, true);
		if (quakesCountry == null) help.text(Tr.t("Downloading from EMSC…", "Se descarcă de la EMSC…"));
		else if (quakesCountry.length() == 0 && quakesInfp == null) help.text(Tr.t("The list could not be downloaded. Is the internet on?", "Lista nu a putut fi descărcată. Ai internet?"));
		else {
			List<ro.meteoalarm.android.core.Quakes.Quake> l;
			try {
				l = ro.meteoalarm.android.core.Quakes.parse(quakesCountry, new String[] { Fetcher.countryEn(this, c) }, c.lat, c.lon, now, 7 * 86400000L);
			} catch (Exception e) {
				l = new ArrayList<ro.meteoalarm.android.core.Quakes.Quake>();
			}
			// v2.035 (Viorel): INFP's value next to EMSC's (and INFP's own quakes EMSC has not got).
			java.util.Map<String, double[]> emsc = new java.util.HashMap<String, double[]>();
			int matched = 0;
			if (quakesInfp != null && Fetcher.infpFor(c)) {
				for (ro.meteoalarm.android.core.Quakes.Quake q : l) emsc.put(q.id, new double[] { q.mag });
				try {
					String it = ro.meteoalarm.android.core.Quakes.officialText(Fetcher.officialOf(c), quakesInfp, l);
					if (it != null) ro.meteoalarm.android.core.Quakes.mergeOfficial(l, it, Fetcher.officialOf(c).name, c.lat, c.lon, now, 7 * 86400000L);
				} catch (Exception e) {
					Log.e(this, "CUTREMUR", "valorile INFP: " + e);
				}
			}
			if (l.isEmpty()) help.text(Tr.t("No earthquake of magnitude 3 or more.", "Niciun cutremur cu magnitudinea 3 sau mai mare."));
			for (ro.meteoalarm.android.core.Quakes.Quake q : l) {
				double[] em = emsc.get(q.id);
				if (em != null && q.official) matched++;
				quakeEntry(q, c, true, em == null ? Double.NaN : em[0]);
			}
			Log.i(this, "CUTREMUR", country + ", 7 zile: " + l.size() + (quakesInfp != null && Fetcher.infpFor(c) ? " (" + matched + " cu valoarea " + Fetcher.officialOf(c).name + ")" : ""));
		}
		help.section(Tr.t("Last 48 hours · EU, Ukraine, Moldova", "Ultimele 48 de ore · UE, Ucraina, Moldova"), true);
		if (quakesEurope == null) help.text(Tr.t("Downloading from EMSC…", "Se descarcă de la EMSC…"));
		else if (quakesEurope.length() == 0) help.text(Tr.t("The list could not be downloaded. Is the internet on?", "Lista nu a putut fi descărcată. Ai internet?"));
		else {
			List<ro.meteoalarm.android.core.Quakes.Quake> l;
			try {
				l = ro.meteoalarm.android.core.Quakes.parse(quakesEurope, ro.meteoalarm.android.core.Quakes.EUROPE, c.lat, c.lon, now, 2 * 86400000L);
			} catch (Exception e) {
				l = new ArrayList<ro.meteoalarm.android.core.Quakes.Quake>();
			}
			// v2.036 (Viorel): INFP's value here too (Romania and the neighbours INFP locates).
			java.util.Map<String, double[]> emsc = new java.util.HashMap<String, double[]>();
			if (quakesInfp != null && Fetcher.infpFor(c)) {
				for (ro.meteoalarm.android.core.Quakes.Quake q : l) emsc.put(q.id, new double[] { q.mag });
				try {
					String it = ro.meteoalarm.android.core.Quakes.officialText(Fetcher.officialOf(c), quakesInfp, l);
					if (it != null) ro.meteoalarm.android.core.Quakes.mergeOfficial(l, it, Fetcher.officialOf(c).name, c.lat, c.lon, now, 2 * 86400000L);
				} catch (Exception e) {
					Log.e(this, "CUTREMUR", "valorile INFP (UE): " + e);
				}
			}
			if (l.isEmpty()) help.text(Tr.t("No earthquake of magnitude 3 or more.", "Niciun cutremur cu magnitudinea 3 sau mai mare."));
			for (ro.meteoalarm.android.core.Quakes.Quake q : l) {
				double[] em = emsc.get(q.id);
				quakeEntry(q, c, false, em == null ? Double.NaN : em[0]);
			}
			Log.i(this, "CUTREMUR", "UE + UA + MD, 48 h: " + l.size());
		}
		ro.meteoalarm.android.core.Quakes.Official off = Fetcher.officialOf(c);
		if (off != null && off.code.equals("ro")) help.source(Tr.t("Romania: next to EMSC's value, INFP's official one (infp.ro), when INFP has it.", "România: lângă valoarea EMSC, cea oficială a INFP (infp.ro), când INFP o are."));
		else if (off != null) help.source(Tr.t("Next to EMSC's value, the official one of ", "Lângă valoarea EMSC, cea oficială a ") + off.name + " (" + off.full + ", " + off.site + ")"
				+ Tr.t(", when it has it.", ", când o are."));
		help.source(Tr.t("Source: EMSC (European-Mediterranean Seismological Centre, seismicportal.eu), magnitude 3 and over. \"Felt\" is an estimate on the EMS-98 scale from the magnitude and the distance to the focus.",
				"Sursa: EMSC (Centrul Seismologic European-Mediteranean, seismicportal.eu), magnitudine 3 și peste. „Resimțit” e o estimare pe scara EMS-98, după magnitudine și distanța până la focar."));
		help.show();
	}

	/** ☰ › Earthquakes for a US town (v2.30): USGS, 7 days around the town, 48 hours in the US. */
	private void showQuakesUS(Cities.City c) {
		long now = System.currentTimeMillis();
		String radius = s.unitF ? Tr.t("within 300 miles", "pe o rază de 300 de mile") : Tr.t("within 480 km", "pe o rază de 480 km");
		help.section(Tr.t("Last 7 days · ", "Ultimele 7 zile · ") + radius, true);
		if (quakesCountry == null) help.text(Tr.t("Downloading from USGS…", "Se descarcă de la USGS…"));
		else if (quakesCountry.length() == 0) help.text(Tr.t("The list could not be downloaded. Is the internet on?", "Lista nu a putut fi descărcată. Ai internet?"));
		else {
			List<ro.meteoalarm.android.core.Quakes.Quake> l;
			try {
				l = ro.meteoalarm.android.core.Quakes.parseUsgs(quakesCountry, c.lat, c.lon, now, 7 * 86400000L, ro.meteoalarm.android.core.Quakes.US_MIN_MAG, false);
			} catch (Exception e) {
				l = new ArrayList<ro.meteoalarm.android.core.Quakes.Quake>();
			}
			if (l.isEmpty()) help.text(Tr.t("No earthquake of magnitude 2.5 or more.", "Niciun cutremur cu magnitudinea 2,5 sau mai mare."));
			for (ro.meteoalarm.android.core.Quakes.Quake q : l) quakeEntry(q, c, true, Double.NaN);
			Log.i(this, "CUTREMUR", "USGS în jurul " + c.name + ", 7 zile: " + l.size());
		}
		help.section(Tr.t("Last 48 hours · United States", "Ultimele 48 de ore · Statele Unite"), true);
		if (quakesEurope == null) help.text(Tr.t("Downloading from USGS…", "Se descarcă de la USGS…"));
		else if (quakesEurope.length() == 0) help.text(Tr.t("The list could not be downloaded. Is the internet on?", "Lista nu a putut fi descărcată. Ai internet?"));
		else {
			List<ro.meteoalarm.android.core.Quakes.Quake> l;
			try {
				l = ro.meteoalarm.android.core.Quakes.parseUsgs(quakesEurope, c.lat, c.lon, now, 2 * 86400000L, ro.meteoalarm.android.core.Quakes.MIN_MAG, true);
			} catch (Exception e) {
				l = new ArrayList<ro.meteoalarm.android.core.Quakes.Quake>();
			}
			// v2.036 (Viorel): INFP's value here too (Romania and the neighbours INFP locates).
			java.util.Map<String, double[]> emsc = new java.util.HashMap<String, double[]>();
			if (quakesInfp != null && Fetcher.infpFor(c)) {
				for (ro.meteoalarm.android.core.Quakes.Quake q : l) emsc.put(q.id, new double[] { q.mag });
				try {
					String it = ro.meteoalarm.android.core.Quakes.officialText(Fetcher.officialOf(c), quakesInfp, l);
					if (it != null) ro.meteoalarm.android.core.Quakes.mergeOfficial(l, it, Fetcher.officialOf(c).name, c.lat, c.lon, now, 2 * 86400000L);
				} catch (Exception e) {
					Log.e(this, "CUTREMUR", "valorile INFP (UE): " + e);
				}
			}
			if (l.isEmpty()) help.text(Tr.t("No earthquake of magnitude 3 or more.", "Niciun cutremur cu magnitudinea 3 sau mai mare."));
			for (ro.meteoalarm.android.core.Quakes.Quake q : l) {
				double[] em = emsc.get(q.id);
				quakeEntry(q, c, false, em == null ? Double.NaN : em[0]);
			}
			Log.i(this, "CUTREMUR", "USGS SUA, 48 h: " + l.size());
		}
		help.source(Tr.t("Source: USGS (U.S. Geological Survey, earthquake.usgs.gov): magnitude 2.5 and over around your town, 3 and over in the whole country. \"Felt\" is an estimate from the magnitude and the distance to the focus, on a scale like the Modified Mercalli one.",
				"Sursa: USGS (Serviciul Geologic al SUA, earthquake.usgs.gov): magnitudine 2,5 și peste în jurul localității, 3 și peste în toată țara. „Resimțit” e o estimare după magnitudine și distanța până la focar, pe o scară ca Mercalli modificată."));
		help.show();
	}

	/** The last lists of the tornado page: { state alerts, US warnings, US watches, SPC today, SPC yesterday } ("" = failed). */
	private String[] torn;
	private String tornKey;
	private long tornAt;
	private boolean tornOpen;

	/** The postal code of the state of the town shown, else of the state the phone is in ("OK"). */
	private String tornState() {
		String st = town.us() ? Cities.stateCode(town.county) : "";
		if (st.length() == 0) st = Cities.stateCode(Region.state(this));
		return st;
	}

	/**
	 * ☰ › Tornade (Viorel 2026-11-04, v2.40), built like ☰ › Cutremure: the tornado watches and
	 * warnings of the town and of its state (NWS), the tornado reports of the last 2 days around
	 * the town (NOAA Storm Prediction Center), the count in the whole US and what to do.
	 */
	private void tornadoPage() {
		Log.i(this, "TORNADE", "pagina de tornade deschisă");
		final String st = tornState();
		final String key = town.key() + "|" + st;
		if (!key.equals(tornKey)) {
			torn = null;
			tornKey = key;
		}
		showTornadoes();
		if (torn != null && System.currentTimeMillis() - tornAt < 5 * 60000L) return;
		final Context ctx = getApplicationContext();
		new Thread(new Runnable() {
			public void run() {
				String[] urls = { st.length() == 2 ? ro.meteoalarm.android.core.Nws.stateUrl(st) : null,
						ro.meteoalarm.android.core.Nws.eventUrl("Tornado Warning"), ro.meteoalarm.android.core.Nws.eventUrl("Tornado Watch"),
						ro.meteoalarm.android.core.Tornadoes.TODAY, ro.meteoalarm.android.core.Tornadoes.YESTERDAY };
				final String[] got = new String[urls.length];
				for (int i = 0; i < urls.length; i++) {
					got[i] = "";
					if (urls[i] == null) continue;
					try {
						got[i] = Weather.get(urls[i]);
					} catch (Exception e) {
						Log.e(ctx, "TORNADE", urls[i] + " a eșuat: " + Fetcher.reason(e, false));
					}
				}
				handler.post(new Runnable() {
					public void run() {
						if (!key.equals(tornKey)) return;
						torn = got;
						tornAt = System.currentTimeMillis();
						if (help.isOpen() && tornOpen) showTornadoes();
					}
				});
			}
		}).start();
	}

	private List<Warnings.Warn> tornAlerts(String json, long now) {
		List<Warnings.Warn> l = new ArrayList<Warnings.Warn>();
		try {
			for (Warnings.Warn w : ro.meteoalarm.android.core.Nws.parse(json, now, false)) if (ro.meteoalarm.android.core.Nws.tornado(w.type)) l.add(w);
		} catch (Exception e) {
			// a bad answer
		}
		return l;
	}

	private void tornEntry(Warnings.Warn w, boolean area) {
		long now = System.currentTimeMillis();
		String when = w.from <= now ? Tr.t("In force now, until ", "În vigoare acum, până ") + s.T.weekdayTime(w.until, s.clk12)
				: Tr.t("From ", "De ") + s.T.weekdayTime(w.from, s.clk12) + Tr.t(" until ", " până ") + s.T.weekdayTime(w.until, s.clk12);
		if (area && w.area.length() > 0) when += "\n" + w.area;
		help.entry(mini("tornado"), ro.meteoalarm.android.core.Nws.event(w.type), when);
	}

	private void showTornadoes() {
		tornOpen = true;
		quakesOpen = false;
		help.begin(Tr.t("Tornadoes", "Tornade"), Tr.t("Close", "Închide"), s.dark);
		help.setOnClose(new Runnable() {
			public void run() {
				tornOpen = false;
				hideBars();
			}
		});
		Cities.City c = town;
		long now = System.currentTimeMillis();
		String st = tornState();
		String stName = Cities.stateName(st);
		String down = Tr.t("Downloading from the National Weather Service…", "Se descarcă de la National Weather Service…");
		String fail = Tr.t("The list could not be downloaded. Is the internet on?", "Lista nu a putut fi descărcată. Ai internet?");
		// 1. The town shown: its own alerts (already downloaded with the weather).
		if (c.us()) {
			help.section(Tr.t("Now · ", "Acum · ") + c.name);
			int n = 0;
			if (s.warns != null) for (Warnings.Warn w : s.warns) if (w.nws && ro.meteoalarm.android.core.Nws.tornado(w.type)) {
				tornEntry(w, false);
				n++;
			}
			if (n == 0) help.text(Tr.t("No tornado watch or warning for ", "Nicio atenționare sau avertizare de tornadă pentru ") + c.name + ".");
		}
		// 2. The state.
		if (stName.length() > 0) {
			help.section(stName + Tr.t(" · tornado watches and warnings now", " · atenționări și avertizări de tornadă acum"));
			if (torn == null) help.text(down);
			else if (torn[0].length() == 0) help.text(fail);
			else {
				List<Warnings.Warn> l = tornAlerts(torn[0], now);
				if (l.isEmpty()) help.text(Tr.t("None in the whole state.", "Niciuna în tot statul."));
				for (Warnings.Warn w : l) tornEntry(w, true);
				Log.i(this, "TORNADE", st + ": " + l.size() + " alerte de tornadă");
			}
		}
		// 3. Reports of the last 2 days around the town (SPC).
		boolean mi = s.unitF;
		help.section(Tr.t("Tornado reports · last 2 days · ", "Tornade raportate · ultimele 2 zile · ") + (mi ? Tr.t("within 300 miles", "pe o rază de 300 de mile") : Tr.t("within 480 km", "pe o rază de 480 km")));
		if (torn == null) help.text(Tr.t("Downloading from the Storm Prediction Center…", "Se descarcă de la Storm Prediction Center…"));
		else if (torn[3].length() == 0 && torn[4].length() == 0) help.text(fail);
		else {
			List<ro.meteoalarm.android.core.Tornadoes.Report> all = new ArrayList<ro.meteoalarm.android.core.Tornadoes.Report>();
			all.addAll(ro.meteoalarm.android.core.Tornadoes.parse(torn[3], ro.meteoalarm.android.core.Tornadoes.dayStart(now, 0), c.lat, c.lon));
			all.addAll(ro.meteoalarm.android.core.Tornadoes.parse(torn[4], ro.meteoalarm.android.core.Tornadoes.dayStart(now, 1), c.lat, c.lon));
			List<ro.meteoalarm.android.core.Tornadoes.Report> near = new ArrayList<ro.meteoalarm.android.core.Tornadoes.Report>();
			for (ro.meteoalarm.android.core.Tornadoes.Report r : all) if (r.dist <= ro.meteoalarm.android.core.Quakes.US_RADIUS_KM) near.add(r);
			ro.meteoalarm.android.core.Tornadoes.sort(near);
			if (near.isEmpty()) help.text(Tr.t("No tornado reported nearby.", "Nicio tornadă raportată în apropiere.")
					+ (all.size() > 0 ? " " + Tr.t("In the whole United States: ", "În toate Statele Unite: ") + all.size() + "." : ""));
			for (ro.meteoalarm.android.core.Tornadoes.Report r : near) {
				String name = r.place + (r.state.length() > 0 ? ", " + r.state : "");
				if (r.scale.length() > 0 && !r.scale.equalsIgnoreCase("UNK")) name += " · " + r.scale;
				String what = s.T.weekdayTime(r.time, s.clk12) + " " + new java.text.SimpleDateFormat(mi ? "M/d" : "dd.MM", java.util.Locale.US).format(new java.util.Date(r.time))
						+ " · " + (r.county.length() > 0 ? r.county + Tr.t(" County", " (comitat)") + " · " : "") + s.dist(r.dist) + " " + Tr.t("from ", "de ") + c.name;
				if (r.comment.length() > 0) what += "\n" + r.comment;
				help.entry(mini("tornado"), name, what);
			}
			Log.i(this, "TORNADE", "SPC 2 zile: " + all.size() + ", aproape: " + near.size());
		}
		// 4. The whole country.
		help.section(Tr.t("United States now", "Statele Unite acum"));
		if (torn == null) help.text(down);
		else if (torn[1].length() == 0 && torn[2].length() == 0) help.text(fail);
		else {
			List<Warnings.Warn> wa = tornAlerts(torn[1], now), wt = tornAlerts(torn[2], now);
			help.text(Tr.t("Tornado warnings: ", "Avertizări de tornadă: ") + wa.size() + " · " + Tr.t("tornado watches: ", "atenționări de tornadă: ") + wt.size()
					+ (wa.isEmpty() ? "" : "\n" + Tr.t("Warnings in: ", "Avertizări în: ") + places(wa)));
		}
		// 5. What to do.
		help.section(Tr.t("What to do", "Ce faci"));
		help.entry(mini("tornado"), Tr.t("Tornado Watch (orange)", "Tornado Watch · atenționare (portocaliu)"), Tr.t("The weather can make tornadoes in and near the area. Keep the phone with you, know where you would take shelter and watch the sky.", "Vremea poate produce tornade în zonă și în apropiere. Ține telefonul la tine, află unde te-ai adăposti și urmărește cerul."));
		help.entry(mini("tornado"), Tr.t("Tornado Warning (red)", "Tornado Warning · avertizare (roșu)"), Tr.t("A tornado has been seen or shown by radar. Take shelter now: the lowest floor, an inside room without windows, away from doors and outside walls; cover your head. Leave a car or a mobile home for a sturdy building.", "O tornadă a fost văzută sau arătată de radar. Adăpostește-te acum: cel mai de jos etaj, o cameră interioară fără ferestre, departe de uși și de pereții exteriori; acoperă-ți capul. Părăsește mașina sau casa mobilă pentru o clădire solidă."));
		help.source(Tr.t("Source: National Weather Service (weather.gov) for the watches and warnings; NOAA Storm Prediction Center (spc.noaa.gov) for the reports, which are preliminary: a tornado can be reported more than once, and EF is the strength when known.",
				"Sursa: National Weather Service (weather.gov) pentru atenționări și avertizări; NOAA Storm Prediction Center (spc.noaa.gov) pentru rapoarte, care sunt preliminare: o tornadă poate fi raportată de mai multe ori, iar EF e puterea, când se cunoaște."));
		help.show();
	}

	/** The places of the alerts, short: the state codes at the end of the areas ("OK, KS, TX"). */
	private static String places(List<Warnings.Warn> l) {
		java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<String>();
		for (Warnings.Warn w : l) {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile(",\\s*([A-Z]{2})(?=;|$)").matcher(w.area);
			while (m.find()) set.add(m.group(1));
		}
		StringBuilder b = new StringBuilder();
		for (String x : set) b.append(b.length() > 0 ? ", " : "").append(x);
		return b.toString();
	}

	private void quakeEntry(ro.meteoalarm.android.core.Quakes.Quake q, Cities.City c, boolean felt, double emscMag) {
		String name = "M " + String.format(java.util.Locale.US, "%.1f", q.mag).replace('.', Tr.dot()) + " · " + Screen.place(q.region, s.unitF);
		// v2.035: with INFP's value, both: "INFP ML 5,7 · EMSC 5,3".
		String vals = "";
		if (q.official) vals = q.src + " " + (q.magType.length() > 0 ? q.magType + " " : "") + String.format(java.util.Locale.US, "%.1f", q.mag).replace('.', Tr.dot())
				+ (Double.isNaN(emscMag) ? "" : " · EMSC " + String.format(java.util.Locale.US, "%.1f", emscMag).replace('.', Tr.dot())) + "\n";
		String what = vals + s.T.weekdayTime(q.time, s.clk12) + " " + new java.text.SimpleDateFormat(s.unitF ? "M/d" : "dd.MM", java.util.Locale.US).format(new java.util.Date(q.time))
				+ " · " + Tr.t("depth ", "adâncime ") + s.dist(q.depth) + " · " + s.dist(q.dist) + " " + Tr.t("from ", "de ") + c.name;
		if (felt || q.dist < 300) what += "\n" + (q.felt >= 2 ? Tr.t("Probably felt in ", "Probabil resimțit în ") + c.name + ": " + Screen.roman(q.felt) + ", " + s.feltWords(q.felt)
				: Tr.t("Probably not felt in ", "Probabil nu s-a simțit în ") + c.name);
		// v2.100 (Viorel, mockup m5): the coloured magnitude square instead of the icon; a tap on it
		// or on the title opens the earthquake on the whole screen.
		final ro.meteoalarm.android.core.Quakes.Quake fq = q;
		final Cities.City fc = c;
		float dp = getResources().getDisplayMetrics().density;
		String mg = String.format(java.util.Locale.US, "%.1f", q.mag).replace('.', Tr.dot());
		help.quakeEntry(QuakeCard.badge(mg, q.magType == null ? "" : q.magType, 48 * dp, fBold), name, what, new Runnable() {
			public void run() {
				Log.i(MainActivity.this, "CUTREMUR", "ecran întreg din listă: M" + fq.mag + " " + fq.region);
				openQuakeFull(fq, fc, false);
			}
		});
	}

	/** ☰ › Ajutor: every button, gesture and menu option with its icon and what it does. */
	private void help() {
		boolean en = s.T.en;
		quakesOpen = false;
		tornOpen = false;
		help.setOnClose(new Runnable() {
			public void run() {
				hideBars();
			}
		});
		help.begin(Tr.t("Help", "Ajutor"), Tr.t("Close", "Închide"), s.dark);
		// Short texts (v2.80, Viorel 2026-11-07): one or two sentences per function.
		boolean us = town.us() || Region.usPhone(this);
		if (us) {
			help.section(Tr.t("United States", "Statele Unite"));
			help.entry(mini("mph"), Tr.t("Imperial units", "Unități imperiale"), Tr.t("°F, mph, miles, inHg and the 12-hour clock for US towns.", "°F, mph, mile, inHg și ceasul de 12 ore pentru localitățile din SUA."));
			help.entry(mini("warn"), Tr.t("Weather alerts", "Avertizări meteo"), Tr.t("From the National Weather Service: Warning red, Watch orange, Advisory yellow.", "De la National Weather Service: Warning roșu, Watch portocaliu, Advisory galben."));
			help.entry(mini("clock"), Tr.t("Local time", "Ora locală"), Tr.t("The clock and the hours show the town's local time.", "Ceasul și orele arată ora locală a localității."));
		}
		help.section(Tr.t("Farmer mode", "Mod Fermier"));
		help.entry(mini("farm"), Tr.t("Farmer mode / Normal mode", "Mod Fermier / Mod Normal"), Tr.t("The Farmer mode switch in ☰ turns it on; the leaf button next to the buttons turns it off. Each hour is one wide box with its weather and its rain. Picking another town keeps farmer mode.",
				"Comutatorul Mod Fermier din ☰ îl pornește; butonul cu frunză de lângă butoane îl oprește. Fiecare oră e o casetă lată, cu vremea și ploaia. Alegerea altei localități păstrează modul fermier.") + " " + Tr.t("The wide boxes only on a parcel's screen; the town's screen stays as in normal mode.", "Casetele late și rândul de ploaie doar pe ecranul parcelei; ecranul localității rămâne ca în modul normal."));
		help.entry(mini("check"), Tr.t("Operational status", "Status operațional"), Tr.t("On a parcel's screen, only when needed: the verdict for spraying (red, yellow, green), the main reason with its limit and the risks of the next 24 hours. On top, the crop and its sowing date; with a crop the verdict also takes drop drying (ΔT) and its disease into account. A tap shows the next good window.",
				"Pe ecranul parcelei, doar când e nevoie: verdictul pentru stropit (roșu, galben, verde), motivul principal cu pragul lui și riscurile următoarelor 24 de ore. Sus, cultura și data semănatului; cu o cultură, verdictul ține cont și de uscarea picăturilor (ΔT) și de boala ei. Atingerea arată următoarea fereastră bună."));
		help.entry(mini("grid4"), Tr.t("A parcel's screen", "Ecranul parcelei"), Tr.t("Statistics · PDF under the status, 1 Hourly weather, the five days, 2 Soil and crops, 3 Risks and reports, 4 Weather details.",
				"Statistică · PDF sub status, 1 Vremea pe ore, cele cinci zile, 2 Starea solului și culturi, 3 Riscuri și rapoarte, 4 Detalii meteo."));
		help.entry(mini("hours"), Tr.t("Hour boxes", "Casetele orare"), Tr.t("A tap shows the hour's details: temperature, wind and its direction, humidity, rain, soil.",
				"Atingerea arată detaliile orei: temperatura, vântul și direcția lui, umiditatea, ploaia, solul."));
		help.entry(mini("soil"), Tr.t("Water in the soil", "Apa în sol"), Tr.t("The water held now in the soil's 3–9 cm layer and its change in 24 hours; a tap shows the hours with precipitation. An Open-Meteo estimate.",
				"Apa reținută acum în stratul de sol de 3–9 cm și schimbarea ei în 24 de ore; atingerea arată orele cu precipitații. Estimare Open-Meteo."));
		help.entry(mini("grid4"), Tr.t("Farmer boxes", "Casetele fermierului"), Tr.t("After Water in the soil: SPRAYING (wind, rain, heat), WATER BALANCE (rain minus evaporation, 7 days), STORMS, GROWING DEGREES, 16 DAYS. A tap explains each one.",
				"După Apa în sol: STROPIT (vânt, ploaie, căldură), BILANȚ APĂ (ploaie minus evaporare, 7 zile), FURTUNI, GRADE-ZILE, 16 ZILE. Atingerea explică fiecare casetă."));
		help.entry(mini("us"), Tr.t("Farmer boxes in the US", "Casetele fermierului în SUA"), Tr.t("For a US town also: NWS (official forecast), DROUGHT (US Drought Monitor), FIRE and HAIL (reports within 50 km).",
				"Pentru o localitate din SUA în plus: NWS (prognoza oficială), SECETĂ (US Drought Monitor), INCENDIU și GRINDINĂ (rapoarte pe 50 km)."));
		// v2.001 (Viorel): Parcels in short paragraphs, each button with its icon.
		help.steps(mini("parcel"), Tr.t("Parcels", "Parcele"), Tr.t("☰ › Parcels: the map with the parcels' cards (portrait a row at the bottom, landscape a column at the right). Kept only on this phone.",
				"☰ › Parcele: harta cu casetele parcelelor (în portret un rând jos, în peisaj o coloană la dreapta). Rămân doar în acest telefon."),
				new android.graphics.Bitmap[] { glyph("plus"), mini("gps"), mini("tap"), glyph("pin"), glyph("point"), glyph("circle"), glyph("save"), glyph("pencil"), glyph("bars"), mini("bell"), glyph("bin") },
				new String[] {
						Tr.t("＋ adds a parcel: the pin starts in the middle of the map, a tap on the map moves it; the name goes on top.", "＋ adaugă o parcelă: acul pornește din mijlocul hărții, o atingere pe hartă îl mută; numele se scrie sus."),
						Tr.t("The GPS button takes the map, and the new parcel's pin, where you are.", "Butonul GPS duce harta, și acul parcelei noi, unde ești."),
						Tr.t("A tap on a card zooms the map on the parcel and shows its points; Statistics opens its statistics.", "Atingerea unei casete mărește harta pe parcelă și arată punctele ei; Statistică deschide statistica."),
						Tr.t("A tap on a pin zooms on the parcel and shows its name; a second tap shows its points.", "Atingerea unui ac mărește harta pe parcelă și îi arată numele; a doua atingere arată punctele."),
						Tr.t("In the satellite view the 4 corners are found from the picture and move with a finger; + Point adds one (up to 7), a long press deletes one.", "În vederea din satelit cele 4 colțuri se găsesc din imagine și se mută cu degetul; + Punct adaugă unul (până la 7), apăsarea lungă șterge unul."),
						Tr.t("Circle puts the points evenly on a circle.", "Cerc așază punctele la distanțe egale pe un cerc."),
						Tr.t("Save and ✕, above the new parcel's box, keep it or close the box.", "Salvează și ✕, deasupra casetei parcelei noi, o păstrează sau închid caseta."),
						Tr.t("The pencil edits the parcel: name, sowing date, area, crop; the GPS of each point is typed in; a tap on the satellite view opens the parcel on the map, where its points move and the floppy saves them.", "Creionul editează parcela: numele, data semănatului, suprafața, cultura; coordonatele GPS ale fiecărui punct se scriu; atingerea vederii din satelit deschide parcela pe hartă, unde punctele se mută, iar discheta le salvează."),
						Tr.t("The bars open its Statistics.", "Barele deschid Statisticile ei."),
						Tr.t("The bell turns its notifications on or off.", "Clopoțelul pornește sau oprește notificările ei."),
						Tr.t("The bin deletes it; it stays 15 days in the bin under the GPS button.", "Coșul o șterge; stă 15 zile în coșul de sub butonul GPS.") });
		help.entry(mini("map"), Tr.t("Map views", "Vederile hărții"), Tr.t("Under ＋ and − on the Parcels map: the satellite view (Esri), the map and the radar (the rain of the last 2 hours, moving; RainViewer).",
				"Sub ＋ și − pe harta Parcele: vederea din satelit (Esri), harta și radarul (precipitațiile din ultimele 2 ore, în mișcare; RainViewer)."));
		help.entry(mini("parcel"), Tr.t("Bin", "Coș de gunoi"), Tr.t("A deleted parcel goes to the bin under the GPS button and is kept 15 days; the round button restores it (if a saved parcel has its name: a new name or replace it).",
				"O parcelă ștearsă trece în coșul de sub butonul GPS și se păstrează 15 zile; butonul rotund o restaurează (dacă o parcelă salvată are același nume: nume nou sau înlocuire)."));
		help.entry(mini("bubble"), Tr.t("Info bubbles", "Bulele de informații"), Tr.t("A frosted-glass window in the middle of the screen (the screen stays sharp); it closes with X or a tap outside it. Under the title, the place; ▲ and ▼ scroll; the source is at the bottom.",
				"O fereastră de sticlă mată în mijlocul ecranului (ecranul rămâne clar); se închide cu X sau cu o atingere în afara ei. Sub titlu, locul; ▲ și ▼ derulează; sursa e jos."));
		help.entry(mini("gps"), Tr.t("Precise location (GPS)", "Localizare precisă (GPS)"), Tr.t("On the parcel maps the blue dot shows where you are: the GPS starts with the page; the GPS button takes the map there.",
				"Pe hărțile parcelelor punctul albastru arată unde ești: GPS-ul pornește odată cu pagina; butonul GPS duce harta acolo."));
		help.entry(mini("pages"), Tr.t("Main screens", "Ecrane principale"), Tr.t("Tabs under the name (in landscape at the bottom): the town and one per parcel; a tap or a sideways slide changes the screen, + opens a new parcel's box.",
				"Tab-urile de sub nume (în peisaj, jos): localitatea și câte unul pentru fiecare parcelă; atingerea sau glisarea în lateral schimbă ecranul, + deschide caseta unei parcele noi."));
		// v2.001 (Viorel): the farmer's Statistics, a paragraph per part with its icon.
		help.steps(mini("table"), Tr.t("Statistics", "Statistici"), Tr.t("They open in the main screen: in portrait under the status card, in landscape at the left. ‹ closes them; another parcel's tab keeps them, with its data. On top: 4 tabs and PDF · CSV.",
				"Se deschid în ecranul principal: în portret sub Status operațional, în peisaj în stânga. ‹ le închide; tab-ul altei parcele le păstrează, cu datele ei. Sus: 4 file și PDF · CSV."),
				new android.graphics.Bitmap[] { mini("check"), mini("hours"), mini("soil"), mini("chart"), mini("warn"), glyph("pencil"), glyph("save") },
				new String[] {
						Tr.t("Action of the day: the best spray window of the next 24 hours (today or tomorrow), why not now, the spray score (0–100) and the biggest risk of the next 48 hours.", "Acțiunea zilei: cea mai bună fereastră de stropit din următoarele 24 de ore (azi sau mâine), de ce nu acum, scorul de stropit (0–100) și cel mai mare risc din următoarele 48 de ore."),
						Tr.t("Operational: the spray window hour by hour (green, yellow, red; the number is ΔT), it moves by itself; a tap on an hour opens its bubble. Drop drying (ΔT, 2–8 °C optimal), wind and gusts, wet leaf.", "Operațional: fereastra de stropit oră cu oră (verde, galben, roșu; cifra e ΔT), se mișcă singură; atingerea unei ore deschide bula ei. Uscare picături (ΔT, optim 2–8 °C), vânt și rafale, frunză umedă."),
						Tr.t("Water & Soil: 7 days of rain and evaporation (ET₀) and the balance; the chart of the past and the next 7 days: drag the indicator to a day, its values show under the chart, after 10 s it goes back to today; soil at 6 and 18 cm, surface moisture, machinery on the field.", "Apă & Sol: ploaia și evaporarea (ET₀) pe 7 zile și bilanțul; graficul trecut și următoarele 7 zile: trage indicatorul pe o zi, valorile ei apar sub grafic, după 10 s revine la azi; solul la 6 și 18 cm, umiditatea la suprafață, intrarea cu utilaje."),
						Tr.t("Season: degree days (GDD) from the sowing date against last year, the estimated stage, heat stress and hard frost days. It needs the crop and the sowing date. A tap on the chart shows that day.", "Sezon: gradele-zile (GDD) de la semănat față de anul trecut, stadiul estimat, stresul termic și zilele de ger. Are nevoie de cultură și de data semănatului. Atingerea graficului arată ziua aceea."),
						Tr.t("Risks 72 h: fungal diseases, ground frost, heat stress, wind and hail; a tap on a card explains it and says what to do. Under them: what comes next.", "Riscuri 72 h: boli fungice, îngheț la sol, stres termic, vânt și grindină; atingerea unui card îl explică și spune ce poți face. Dedesubt: ce urmează."),
						Tr.t("The crop is picked from a list ordered by the sowing date's season; Another crop… takes a name and a type; out of season the app asks again.", "Cultura se alege dintr-o listă ordonată după sezonul datei de semănat; Altă cultură… primește un nume și un tip; în afara sezonului aplicația întreabă din nou."),
						Tr.t("PDF · CSV saves the summary, the next 72 hours and the days in Downloads.", "PDF · CSV salvează rezumatul, următoarele 72 de ore și zilele în Descărcări.") });
		help.entry(mini("bellp"), Tr.t("Parcel notifications", "Notificări parcele"), Tr.t("Farmer mode: the bell on a parcel's card turns its notifications on (filled) or off; then the parcel gets all of them: rain or snow within the hour, after 8 pm rain or snow tomorrow, wind, snow, frost, freeze, fog, freezing rain, drought (dry soil, no rain for 7 days).",
				"Mod fermier: clopoțelul de pe caseta parcelei îi pornește (plin) sau oprește notificările; atunci parcela le primește pe toate: ploaie sau ninsoare în următoarea oră, după ora 20 ploaie sau ninsoare mâine, vânt, ninsoare, brumă, îngheț, ceață, polei, secetă (sol uscat, fără ploaie 7 zile)."));
		help.entry(mini("layers"), Tr.t("Soil data and source", "Datele solului și sursa"), Tr.t("Soil data are Open-Meteo model estimates, not measurements; nearby parcels can show the same values.",
				"Datele solului sunt estimări ale modelului Open-Meteo, nu măsurători; parcelele apropiate pot avea aceleași valori."));
		help.entry(mini("map"), Tr.t("The map", "Harta"), Tr.t("OpenStreetMap: move it with one finger, zoom with two; it needs internet.",
				"OpenStreetMap: o muți cu un deget, o mărești cu două; are nevoie de internet."));
		help.section(Tr.t("Updates", "Actualizări"));
		help.entry(mini("update"), Tr.t("How to update", "Cum actualizezi"), Updater.howTo(en) + " " + Tr.t("The app checks only when you tap Check for updates; Download opens the new version in the browser and you install it from the downloaded file.", "Aplicația verifică doar când apeși Verifică actualizări; Descarcă deschide versiunea nouă în browser, iar tu o instalezi din fișierul descărcat."));
		help.entry(mini("shield"), Tr.t("Samsung: Auto Blocker", "Samsung: Auto Blocker"), Updater.autoBlocker());
		help.entry(mini("check"), Tr.t("Before an update", "Înainte de actualizare"), Tr.t("The app first checks that it may install updates (else it opens that setting). On Samsung it reminds you to turn off Auto Blocker; Settings opens its page.",
				"Aplicația verifică întâi dacă are voie să instaleze actualizări (altfel deschide setarea). Pe Samsung amintește să oprești Auto Blocker; Setări îi deschide pagina."));
		help.entry(mini("retry"), Tr.t("When the download fails", "Când descărcarea nu merge"), Tr.t("The app checks the internet first, then looks on GitHub, on a second GitHub server and on Google Drive, and downloads from the first that answers. A version already downloaded is installed at once.",
				"Aplicația verifică întâi internetul, apoi caută pe GitHub, pe un server secundar GitHub și pe Google Drive și descarcă de la primul care răspunde. O versiune deja descărcată se instalează direct."));
		help.section(Tr.t("Buttons on the screen", "Butoane pe ecran"));
		help.entry(mini("layers"), Tr.t("The main screen", "Ecranul principal"), Tr.t("The page moves up and down under the glass at the top; the buttons and the battery float at the bottom. Sections: Next hours, Five-day forecast, Weather details.",
				"Pagina se mișcă sus-jos pe sub sticla de sus; butoanele și bateria plutesc jos. Secțiuni: Următoarele ore, Prognoza pe cinci zile, Detalii meteo."));
		help.entry(mini("row"), Tr.t("Five-day forecast", "Prognoza pe cinci zile"), Tr.t("Tomorrow and the day after on top; under them days 3–5 move on by themselves every 3 seconds until you touch them. The Weather details row moves a little after 6 seconds. A tap on Tomorrow or the day after shows its bubble.",
				"Mâine și poimâine sus; dedesubt zilele 3–5 trec singure la 3 secunde până le atingi. Rândul Detalii meteo se mișcă puțin după 6 secunde. Atingerea casetei Mâine sau Poimâine îi arată bula."));
		help.entry(mini("city"), Tr.t("Town", "Localitate"), Tr.t("Choose the town: favourites, then counties (or states).", "Alegi localitatea: favorite, apoi județe (sau state)."));
		help.entry(mini("locate"), Tr.t("Location", "Localizare"), Tr.t("Turns automatic location on (filled) or off (empty); it shows the weather where you are. A village you pass through joins its county's list; it goes after 7 days without passing there and stays if you pass there often (3 days in 2 weeks). The compass shows the villages too. In another country or US state, the small localities are downloaded once, the first time you are there with internet.",
				"Pornește (plin) sau oprește (gol) localizarea automată; arată vremea unde ești. Satul prin care treci intră în lista județului; dispare după 7 zile fără să mai treci pe acolo și rămâne dacă treci des (3 zile în 2 săptămâni). Busola arată și satele. În altă țară sau alt stat din SUA, localitățile mici se descarcă o singură dată, prima dată când ești acolo cu internet."));
		help.entry(mini("bright"), Tr.t("Brightness", "Luminozitate"), Tr.t("Shows the brightness bar on the left.", "Arată bara de luminozitate din stânga."));
		help.entry(mini("moon"), Tr.t("Light / dark theme", "Temă luminoasă / întunecată"), Tr.t("Over the brightness bar: switches the theme.", "Deasupra barei de luminozitate: schimbă tema."));
		help.entry(mini("sensor"), Tr.t("Automatic theme (phone sensor)", "Temă automată (senzor telefon)"), Tr.t("Light theme in the light, dark in the dark (needs a light sensor).", "Tema luminoasă la lumină, cea întunecată pe întuneric (are nevoie de senzor de lumină)."));
		help.entry(mini("wifi"), Tr.t("Connection", "Conexiune"), Tr.t("Next to the clock: Wi-Fi (arcs) or mobile data (bars); the full arcs or bars show the signal, the small text the kind (6 = Wi-Fi 6, 5G…). Another network: a globe; struck through without internet.",
				"Lângă ceas: Wi-Fi (arce) sau date mobile (bare); arcele sau barele pline arată semnalul, textul mic tipul (6 = Wi-Fi 6, 5G…). Altă rețea: glob; tăiat când nu ai internet."));
		help.entry(mini("wxicon"), Tr.t("Weather icons", "Iconurile vremii"), Tr.t("In the theme's colours; all of them move for 3 seconds when the screen opens, after every refresh or when the weather changes, on the widgets too.",
				"În culorile temei; toate se mișcă 3 secunde când se deschide ecranul, după fiecare reîmprospătare sau când se schimbă vremea, și pe widget-uri."));
		help.entry(mini("camera"), Tr.t("Camera band", "Banda camerei"), Tr.t("In portrait, on phones with the camera in the screen (Android 9+), the clock, the connection and the date sit next to the camera; the town gets the whole row.",
				"În portret, pe telefoanele cu camera în ecran (Android 9+), ceasul, conexiunea și data stau lângă cameră; localitatea are tot rândul."));
		help.section(Tr.t("Gestures", "Gesturi"));
		help.entry(mini("tap"), Tr.t("Tap on the town's name", "Atingerea numelui localității"), Tr.t("Looks once with the GPS.", "Caută o dată poziția cu GPS-ul."));
		help.entry(mini("pull"), Tr.t("Pull down", "Trage în jos"), Tr.t("Refreshes the weather; a tap on “updated” does the same.", "Reîmprospătează vremea; la fel și atingerea capsulei „actualizat”."));
		help.entry(mini("edge"), Tr.t("Left edge", "Marginea din stânga"), Tr.t("Slide up or down: brightness.", "Glisează sus-jos: luminozitatea."));
		help.entry(mini("textsize"), Tr.t("Text size", "Mărimea textului"), Tr.t("The Aa button on the pages shows the text-size bar.", "Butonul Aa de pe pagini arată bara de mărime a textului."));
		help.entry(mini("compass"), Tr.t("Wind arrow", "Săgeata vântului"), Tr.t("Shows where the wind blows, turned with the phone.", "Arată încotro bate vântul, rotită după telefon."));
		help.entry(mini("sleep"), Tr.t("Night clock", "Ceasul de noapte"), Tr.t("A big dim clock while the phone charges at night.", "Un ceas mare și estompat când telefonul se încarcă noaptea."));
		help.entry(mini("sleep"), Tr.t("Screen off", "Ecran stins"), Tr.t("With the phone's screen off, the app's windows close after 3 seconds and the app after 10.", "Cu ecranul telefonului stins, ferestrele aplicației se închid după 3 secunde, iar aplicația după 10."));
		help.entry(mini("roll"), Tr.t("Warnings and earthquake", "Avertizări și cutremur"), Tr.t("One row under the town: the earthquake, then the warnings by colour. A tap shows the values (gusts, amounts, temperatures) on top, then the details.",
				"Un rând sub localitate: cutremurul, apoi avertizările pe culori. Atingerea arată sus valorile (rafale, cantități, temperaturi), apoi detaliile.")
				+ " " + Tr.t("In landscape each warning box takes half the row.", "În peisaj, fiecare casetă de avertizare ocupă jumătate din rând."));
		if (!us) help.entry(mini("info"), Tr.t("Information boxes", "Casete de informare"), Tr.t("Blue INFORMATION boxes after the warnings: frost, freeze (spring and autumn), fog, freezing rain, strong wind in the next 24 hours. Worked out by the app from the forecast; not official. They also come as a notification of their own.",
				"Casetele albastre INFORMARE, după avertizări: brumă, îngheț (primăvara și toamna), ceață, polei, vânt tare în următoarele 24 de ore. Calculate de aplicație din prognoză; nu sunt oficiale. Vin și ca notificare separată."));
		help.entry(mini("chart"), Tr.t("Precipitation chart", "Graficul de precipitații"), Tr.t("A tap on the precipitation chart or on a 4-hour box shows each hour's chance and amount (mm), then explains the chart.",
				"Atingerea graficului de precipitații sau a unei casete de 4 ore arată probabilitatea și cantitatea (mm) pentru fiecare oră, apoi explică graficul."));
		help.entry(mini("quakebox"), Tr.t("Earthquake box", "Caseta Cutremur"), (us ? Tr.t("The strongest earthquake of the last 24 hours within 300 miles (USGS).", "Cel mai puternic cutremur din ultimele 24 de ore, pe o rază de 300 de mile (USGS).")
				: Tr.t("The strongest earthquake of the last 24 hours in your country and how it was likely felt (EMSC; in Romania INFP's official values).", "Cel mai puternic cutremur din ultimele 24 de ore din țara ta și cum s-a simțit probabil (EMSC; în România valorile oficiale INFP).")) + " " + Tr.t("The stripe at the left has the colour of the map zone your town is in; brown outside the zones.", "Banda din stânga are culoarea zonei de pe hartă în care e localitatea ta; maro în afara zonelor."));
		help.entry(mini("quake"), Tr.t("Earthquake information", "Informații cutremur"), Tr.t("A tap on the Earthquake box opens its information in the main screen (portrait under Feels like, landscape at the left): the map, under it the felt zones and the value's history, then the details; ‹ closes it.",
				"Atingerea casetei Cutremur deschide informațiile în ecranul principal (în portret sub Resimțit, în peisaj în stânga): harta, sub ea zonele resimțite și istoricul valorii, apoi detaliile; ‹ le închide.")
				+ " " + Tr.t("The coloured square or the map opens it on the whole screen.", "Pătratul colorat sau harta le deschid pe tot ecranul.")
				+ " " + Tr.t("☰ › Earthquakes: a tap on a quake's square or title opens it on the whole screen; in landscape the map at the left, the information at the right.", "☰ › Cutremure: atingerea pătratului sau a titlului unui cutremur îl deschide pe tot ecranul; în peisaj harta în stânga, informațiile în dreapta.")
				+ " " + Tr.t("The square's colour shows the magnitude: green under 3, yellow, amber, orange to red from 5.", "Culoarea pătratului arată magnitudinea: verde sub 3, galben, chihlimbar, portocaliu spre roșu de la 5.")
				+ " " + Tr.t("In Romania, ↻ INFP asks for the official value again.", "În România, ↻ INFP cere din nou valoarea oficială.")
				+ " " + Tr.t("Romania, France, Italy, the Netherlands, Switzerland, Greece: the country's institute first, EMSC second.", "România, Franța, Italia, Olanda, Elveția, Grecia: întâi institutul țării, apoi EMSC.") + " " + Tr.t("In Romania, the quakes around the country also get INFP's value (from infp.ro).", "În România, și cutremurele din jurul țării primesc valoarea INFP (de pe infp.ro)."));
		help.entry(mini("swipe"), Tr.t("Left / right on the boxes", "Stânga / dreapta pe casete"), Tr.t("The rows of boxes follow the finger.", "Rândurile de casete urmează degetul."));
		help.entry(mini("row"), Tr.t("The row of boxes at the bottom", "Rândul de casete de jos"), Tr.t("Sun / Moon, UV, Pollen, Humidity, Air quality, Pressure, Wind, Visibility, Compass. A tap on any box (also the hours and the days) shows its value on top and the explanations under it.",
				"Soare / Lună, UV, Polen, Umiditate, Calitatea aerului, Presiune, Vânt, Vizibilitate, Busolă. Atingerea oricărei casete (și a orelor și zilelor) arată valoarea sus și explicațiile dedesubt."));
		if (s.hasCompass) help.entry(mini("bigcompass"), Tr.t("Big compass", "Busola mare"), Tr.t("A tap on the Compass box: a big compass, the GPS coordinates, the town you are in (grey) and the 3 nearest towns, each in its colour. A tap on a town in the list or on its mark shows its name and distance on the dial; towns in the same direction are drawn side by side. Share sends a picture of the window with the place, direction, towns, battery, temperatures and wind; Towns shows or hides the marks.",
				"Atingerea casetei Busolă: o busolă mare, coordonatele GPS, localitatea în care ești (gri) și cele mai apropiate 3 localități, fiecare în culoarea ei. Atingerea unei localități din listă sau a semnului ei arată pe busolă numele și distanța; localitățile din aceeași direcție stau una lângă alta. Partajare trimite o imagine a ferestrei cu locul, direcția, localitățile, bateria, temperaturile și vântul; Localități arată sau ascunde semnele."));
		help.section(Tr.t("Home-screen widgets", "Widget-uri pe ecranul de pornire"));
		help.entry(mini("widget"), Tr.t("Meteo Alarm widgets", "Widget-urile Meteo Alarm"), Tr.t("Hold the home screen › Widgets › Meteo Alarm. Drag the edges to change the layout (2x2, 4x1, 4x2, 4x3).",
				"Ține apăsat pe ecranul de pornire › Widgeturi › Meteo Alarm. Trage de margini ca să schimbi modelul (2x2, 4x1, 4x2, 4x3)."));
		help.entry(mini("widget"), Tr.t("Widget 4x2 · list", "Widget-ul 4x2 · listă"), Tr.t("A big temperature and, on the right, the next 4 days (one icon each) or hours, as chosen in its settings: Show the weather. On one row it becomes the 4x1.",
				"Temperatura mare și, în dreapta, următoarele 4 zile (câte un icon) sau ore, după alegerea din setări: Afișare starea vremii. Pe un singur rând devine widgetul 4x1."));
		help.entry(mini("warn"), Tr.t("Codes on widgets", "Coduri pe widget-uri"), Tr.t("Three coloured lines for the warning codes, on every widget. Without codes: one blue line with the first information still in force (e.g. Frost).",
				"Trei linii colorate pentru codurile de avertizare, pe toate widget-urile. Fără coduri: o linie albastră cu prima informare încă valabilă (ex. Brumă)."));
		help.entry(mini("gear"), Tr.t("Widget settings", "Setările widget-ului"), Tr.t("Hold the widget › Settings, or ☰ › Widgets: town, background, colours.",
				"Ține apăsat pe widget › Setări sau ☰ › Widget-uri: localitatea, fundalul, culorile."));
		help.entry(mini("auto"), Tr.t("Refreshing by itself", "Reîmprospătare automată"), Tr.t("Every 15 minutes with the screen on, 30 with it off; MET Norway when Open-Meteo is busy.",
				"La 15 minute cu ecranul aprins, la 30 cu el stins; MET Norway când Open-Meteo e ocupat."));
		help.section(Tr.t("Settings (☰)", "Setări (☰)"));
		help.entry(mini("gear"), Tr.t("The Settings window", "Fereastra Setări"), Tr.t("☰ opens the settings in sections; a switch turns a function on or off at once. It closes with X or a tap outside it. On a phone it opens in portrait.",
				"☰ deschide setările pe secțiuni; un comutator pornește sau oprește o funcție pe loc. Se închide cu X sau cu o atingere în afara ei. Pe telefon se deschide în portret."));
		help.entry(mini("city"), Tr.t("Location & favourites", "Localizare & Favorite"), Tr.t("The favourites (the star: the town shown); a tap picks one; + Add and Choose the county open the list of towns.",
				"Favoritele (steaua: localitatea afișată); atingerea alege una; + Adaugă și Alegeți județul deschid lista localităților."));
		help.entry(mini("degf"), Tr.t("Temperature and clock", "Temperatură și ceas"), Tr.t("°C or °F, 12 h or 24 h: a tap on the choice.", "°C sau °F, 12 h sau 24 h: atingi alegerea."));
		help.entry(mini("palette"), Tr.t("Theme", "Temă"), Tr.t("6 colour themes: a tap on a colour; under them Light, Dark or Automatic (phone sensor).", "6 teme de culoare: atingi o culoare; sub ele Luminos, Întunecat sau Automat (senzor telefon)."));
		help.entry(mini("wxicon"), Tr.t("Icon theme", "Temă icon"), Tr.t("6 weather icon themes: a tap on a dot. Classic and Outline take the theme's colours; they move for 3 seconds. The widgets take the same icons.", "6 teme de iconuri: atingi o bulină. Clasic și Contur iau culorile temei; se mișcă 3 secunde. Widgeturile iau aceleași iconuri."));
		help.entry(mini("lang-" + s.T.lang), Tr.t("Display language", "Limbă afișaj"), Tr.t("Buttons in ☰, 2 on a row: English and the country's language; Other languages: the country's other languages, the big EU languages, Romanian and the phone's. In the US only English and Spanish.",
				"Butoane în ☰, câte 2 pe rând: engleza și limba țării; Alte limbi: celelalte limbi ale țării, marile limbi din UE, româna și limba telefonului. În SUA doar engleza și spaniola."));
		help.entry(mini("autoloc"), Tr.t("Auto location", "Localizare auto"), Tr.t("Turns automatic location on or off (the same as the location button).", "Pornește sau oprește localizarea automată (la fel ca butonul de localizare)."));
		help.entry(mini("quake"), Tr.t("Earthquakes", "Cutremure"), Tr.t("The switch: notifications of earthquakes within 300 km of your place, checked every 10 minutes, also at night with the screen off; a revised magnitude updates the notice. Under it, the page of the recent earthquakes around.",
				"Comutatorul: notificări pentru cutremurele pe o rază de 300 km de locul tău, verificate la 10 minute, și noaptea cu ecranul stins; o magnitudine revizuită actualizează notificarea. Sub el, pagina cutremurelor recente din jur."));
		if (Region.showUS(this)) help.entry(mini("tornado"), Tr.t("Tornadoes", "Tornade"), Tr.t("The switch: notifications of the tornado warnings for your town (NWS). Under it, the alerts and reports around and what to do.",
				"Comutatorul: notificări pentru avertizările de tornadă ale localității tale (NWS). Sub el, alertele și raportările din jur și ce faci."));
		help.entry(mini("bell"), Tr.t("Notifications", "Notificări"), Tr.t("Rain or snow within the hour, after 8 pm rain or snow tomorrow, new warnings and earthquakes, each only once.", "Ploaie sau ninsoare în următoarea oră, după ora 20 ploaie sau ninsoare mâine, avertizări noi și cutremure, fiecare o singură dată."));
		help.entry(mini("night"), Tr.t("Night clock", "Ceas noapte"), Tr.t("Turns the night clock on or off.", "Pornește sau oprește ceasul de noapte."));
		help.entry(mini("keepOn"), Tr.t("Screen on", "Ecran aprins"), Tr.t("Keeps the screen on while the app is open.", "Ține ecranul aprins cât aplicația e deschisă."));
		help.entry(mini("widgetplus"), Tr.t("System & support", "Sistem & Suport"), Tr.t("The bar at the bottom of the window: Widgets, Export / Import, Log, Update, Share, Help, About. Pulled up, it shows the names under the icons.",
				"Bara de jos a ferestrei: Widget-uri, Export / Import, Jurnal, Actualizare, Partajare, Ajutor, Despre. Trasă în sus, arată numele de sub iconuri."));
		help.entry(mini("transfer"), Tr.t("Export / Import", "Export / Import"), Tr.t("Moves your towns, favourites, parcels and settings to another device through a file.",
				"Mută localitățile, favoritele, parcelele și setările pe alt dispozitiv, printr-un fișier."));
		help.entry(mini("log"), Tr.t("Log", "Jurnal"), Tr.t("What the app did, with the time; read, share or save it.", "Ce a făcut aplicația, cu ora; îl citești, îl trimiți sau îl salvezi."));
		help.entry(mini("updnew"), Tr.t("Check for updates", "Verifică actualizări"), Tr.t("Looks for a new version on GitHub, with your consent.", "Caută o versiune nouă pe GitHub, cu acordul tău."));
		help.entry(mini("share"), Tr.t("Send the app", "Trimite aplicația"), Tr.t("Sends the download link, e.g. on WhatsApp.", "Trimite linkul de descărcare, de exemplu pe WhatsApp."));
		help.entry(mini("about"), Tr.t("About", "Despre"), Tr.t("About the app, its data and what's new.", "Despre aplicație, datele ei și ce e nou."));
		help.show();
	}

	/** After an update: what is new in the installed version. */
	private void news() {
		String log = changeLog();
		if (log.length() == 0) return;
		// v2.80 (Viorel): the change log is a summary; after an update only the installed version's part shows.
		// v3.00 (Viorel): the current version is the first block.
		log = log.split("\n\n")[0].trim();
		// Marked as seen only once it is really on the screen (on the A54 the app went to the
		// background right after the update and the window was never seen).
		if (!resumed) return;
		prefs.edit().putString("seenVersion", updater.installed()).apply();
		Log.i(this, "APP", "fereastra Ce e nou afișată");
		asked = true;
		sheet.show((Tr.t("What's new", "Ce e nou")) + " · v." + updater.installed(), Updater.howTo(s.T.en) + "\n\n" + log, null, null, Tr.t("Close", "Închide"),
				new Runnable() {
					public void run() {
						consents();
					}
				});
		sheet.sizeBar(true);
	}

	/** The change log from assets, newest first. */
	private String changeLog() {
		// v3.00 (Viorel 2026-11-08): one summary per language: the current version, version 2,
		// version 1; English when a language has none.
		String own = Tr.ro() || Tr.english() ? null : asset("jurnal-" + s.T.lang + ".txt");
		if (own != null) return own;
		String log = asset(Tr.t("jurnal-en.txt", "jurnal-ro.txt"));
		return log == null ? "" : log;
	}

	/** A text file from the assets (null when missing). */
	private String asset(String name) {
		try {
			java.io.InputStream in = getAssets().open(name);
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
			in.close();
			return new String(out.toByteArray(), "UTF-8").trim();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * The hidden test mode (v2.30, not in the Help nor in the change log): the phone behaves as if
	 * it were in a US state, and both the European and the US favourites and towns are listed.
	 */
	private void testMode() {
		sheet.hide();
		reopen = null;
		final List<String> codes = new ArrayList<String>();
		List<Bubble.Row> rows = Bubble.rows();
		boolean on = Region.test(this);
		String cur = prefs.getString("usTestState", "OK");
		codes.add("");
		rows.add(new Bubble.Row(Tr.t("Test mode off", "Mod de test oprit"), null, Bubble.Row.ITEM, !on));
		for (int k = 0; k < Cities.STATES.length; k += 2) {
			codes.add(Cities.STATES[k + 1]);
			rows.add(new Bubble.Row(Cities.STATES[k] + " (" + Cities.STATES[k + 1] + ")", null, Bubble.Row.ITEM, on && cur.equals(Cities.STATES[k + 1])));
		}
		Log.i(this, "US", "modul de test: lista statelor");
		bubble.show(view.px(s.btnMenu), bubbleWidth(0.6f, 240), Tr.t("Test mode · USA", "Mod de test · SUA"), rows, s.T.en, new Bubble.Pick() {
			public void picked(int k) {
				String c = codes.get(k);
				if (c.length() == 0) {
					prefs.edit().putBoolean("usTest", false).apply();
					Log.i(MainActivity.this, "US", "modul de test OPRIT");
					note(Tr.t("Test mode off", "Mod de test oprit"), 2000);
				} else {
					boolean change = !c.equals(prefs.getString("usTestState", "OK"));
					prefs.edit().putBoolean("usTest", true).putString("usTestState", c).apply();
					Log.i(MainActivity.this, "US", "modul de test PORNIT: telefonul e ca în " + Cities.stateName(c));
					seedUSFavs();
					note(Tr.t("Test mode: ", "Mod de test: ") + Cities.stateName(c), 2500);
					if (change) Log.i(MainActivity.this, "US", "statul de test schimbat; favoritele SUA rămân");
				}
				applySettings();
				view.invalidate();
			}
		});
	}

	/** v4.40 (Viorel 2026-11-10): the description of the app (the same as on GitHub), at the top of About. */
	private String description() {
		return Tr.t("The app has been updated and now includes Farmer Mode, allowing you to monitor the weather and key metrics for your land plots.",
				"Aplicația a fost actualizată și include acum Modul Fermier, cu care urmărești vremea și indicatorii importanți pentru terenurile tale.")
				+ "\n\n" + Tr.t("The main screen shows the weather where you are: the clock, temperature, feels-like, wind, rain and snow for the next hours, the next days, sun, UV and pollen, together with the official weather warnings (MeteoAlarm in the EU, the National Weather Service in the US) and nearby earthquakes. Notifications tell you when rain, snow or a warning is coming. Home-screen widgets in several sizes, six colour themes (light and dark) and 28 languages are included.",
				"Ecranul principal arată vremea acolo unde ești: ceasul, temperatura, temperatura resimțită, vântul, ploaia și ninsoarea în orele următoare, zilele următoare, soarele, UV și polenul, împreună cu avertizările meteo oficiale (MeteoAlarm în UE, National Weather Service în SUA) și cutremurele din apropiere. Notificările îți spun când vine ploaia, ninsoarea sau o avertizare. Aplicația are widgeturi de mai multe mărimi, șase teme de culoare (luminoase și întunecate) și 28 de limbi.")
				+ "\n\n" + Tr.t("If you get lost, the compass works offline and shows nearby towns and villages. You can also send your coordinates, together with a picture of the compass, so others can locate you. The villages you pass through are added to your county's list of towns and shown on the widgets too.",
				"Dacă te rătăcești, busola funcționează fără internet și arată localitățile și satele din apropiere. Poți trimite coordonatele tale, împreună cu o imagine a busolei, ca alții să te găsească. Satele prin care treci intră în lista localităților din județul tău și apar și pe widgeturi.")
				+ "\n\n" + Tr.t("In Farmer Mode, the list of monitored indicators and alerts expands significantly. Whether you want to know when wind conditions are right for spraying your crops, soil-level temperature, humidity, or precipitation levels, all of these can be tracked in Farmer Mode for each land plot set up in the app, with statistics that can be saved as PDF.",
				"În Modul Fermier, lista indicatorilor și a alertelor urmărite crește mult. Fie că vrei să știi când vântul e potrivit pentru stropit, temperatura la nivelul solului, umiditatea sau cantitatea de precipitații, toate se pot urmări în Modul Fermier pentru fiecare parcelă adăugată în aplicație, cu statistici care se pot salva ca PDF.")
				+ "\n\n" + Tr.t("All this information stays strictly on your device. No one else has access to it. Each farmer only tracks the locations they are interested in.",
				"Toate aceste informații rămân strict pe dispozitivul tău. Nimeni altcineva nu are acces la ele. Fiecare fermier urmărește doar locurile care îl interesează.")
				+ "\n\n" + Tr.t("You can export your app settings (towns, favourites, land plots and preferences, everything you have configured on one device) and easily import them onto a secondary Android device or when you switch to a new phone: ☰ › Export / Import.",
				"Poți exporta setările aplicației (localitățile, favoritele, parcelele și preferințele, tot ce ai configurat pe un dispozitiv) și le poți importa ușor pe un al doilea dispozitiv Android sau când treci pe un telefon nou: ☰ › Export / Import.")
				+ "\n\n" + Tr.t("The app is currently available only in the EU and the US, and only works on Android devices. It updates itself from GitHub: ☰ › Check for updates.",
				"Aplicația este disponibilă deocamdată doar în UE și în SUA și funcționează doar pe dispozitive Android. Se actualizează singură de pe GitHub: ☰ › Verifică actualizări.")
;
	}

	private void about() {
		String msg = description() + "\n\n" + aboutText() + "\n\n" + (Tr.t("The dark theme uses the Claude app colours. Font: Liberation Sans.", "Tema întunecată folosește culorile aplicației Claude. Font: Liberation Sans."));
		// v5.04 (Viorel): the author's full name, just above the installed version.
		msg += "\n\n" + Tr.t("Developed by Viorel-R Boancă", "Dezvoltată de Viorel-R Boancă");
		msg += "\n" + (Tr.t("Installed version: v", "Versiunea instalată: v")) + updater.installed()
				+ "\n" + (Tr.t("Updates: ", "Actualizări: ")) + "github.com/" + ro.meteoalarm.android.core.Release.REPO + "/releases";
		final String aboutMsg = msg;
		sheet.show(VERSION, msg, null, null, Tr.t("Close", "Închide"));
		sheet.sizeBar(true);
		// The hidden test mode for the US (v2.30): 7 taps on the title.
		sheet.titleTaps(new Runnable() {
			public void run() {
				testMode();
			}
		});
		// "Ce e nou" on the left: the change log in the same window, and back to About.
		final String newsL = Tr.t("What's new", "Ce e nou"), aboutL = Tr.t("About", "Despre");
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
		// Europe, the United States, or both in the test mode (v2.30): never mixed.
		final boolean eu = Region.showEU(this), us = Region.showUS(this);
		if (us) seedUSFavs();
		final List<Cities.City> favs = eu ? favList("favs") : new ArrayList<Cities.City>();
		final List<Cities.City> favsUS = us ? favList("favsUS") : new ArrayList<Cities.City>();
		final boolean en = s.T.en;
		final List<Runnable> acts = new ArrayList<Runnable>();
		final List<Bubble.Row> rows = Bubble.rows();
		if (!favs.isEmpty()) {
			rows.add(new Bubble.Row(us ? Tr.t("Favourites EU", "Favorite UE") : Tr.t("Favourites", "Favorite"), null, Bubble.Row.HEADER, false));
			acts.add(null);
			for (final Cities.City c : favs) {
				rows.add(Bubble.Row.item(c.label()));
				acts.add(new Runnable() {
					public void run() {
						Log.i(MainActivity.this, "LOC", "aleasă din favorite: " + c.label());
						choose(c);
					}
				});
			}
		}
		if (!favsUS.isEmpty()) {
			rows.add(new Bubble.Row(eu ? Tr.t("Favourites USA", "Favorite SUA") : Tr.t("Favourites", "Favorite"), null, Bubble.Row.HEADER, false));
			acts.add(null);
			for (final Cities.City c : favsUS) {
				rows.add(Bubble.Row.item(c.label()));
				acts.add(new Runnable() {
					public void run() {
						Log.i(MainActivity.this, "LOC", "aleasă din favorite SUA: " + c.label());
						choose(c);
					}
				});
			}
		}
		if (!favs.isEmpty() || !favsUS.isEmpty()) {
			rows.add(new Bubble.Row("", null, Bubble.Row.LINE, false));
			acts.add(null);
		}
		if (eu) {
			rows.add(new Bubble.Row(Tr.t("Choose the county", "Alegeți județul"), null, Bubble.Row.HEADER, false));
			acts.add(null);
			for (final String co : cities.byCounty.keySet()) {
				rows.add(Bubble.Row.item(co));
				acts.add(new Runnable() {
					public void run() {
						Log.i(MainActivity.this, "LOC", "listă: județul " + co);
						pickTown(b, co);
					}
				});
			}
			// Other countries, by name: the starting towns and the ones found by the location.
			final List<String> ccs = new ArrayList<String>(cities.abroad.keySet());
			java.util.Collections.sort(ccs, new java.util.Comparator<String>() {
				public int compare(String a, String c) {
					return Warnings.fold(countries.shown(a)).compareTo(Warnings.fold(countries.shown(c)));
				}
			});
			rows.add(new Bubble.Row("", null, Bubble.Row.LINE, false));
			acts.add(null);
			rows.add(new Bubble.Row(Tr.t("Other countries", "Alte țări"), null, Bubble.Row.HEADER, false));
			acts.add(null);
			for (final String cc : ccs) {
				rows.add(Bubble.Row.item(countries.shown(cc)));
				acts.add(new Runnable() {
					public void run() {
						Log.i(MainActivity.this, "LOC", "listă: țara " + cc);
						pickAbroad(b, cc);
					}
				});
			}
		}
		if (us) {
			if (eu) {
				rows.add(new Bubble.Row("", null, Bubble.Row.LINE, false));
				acts.add(null);
			}
			// The state the phone is in: all its towns; the other states: 3 towns each.
			final String st = Region.state(this);
			List<Cities.City> mine = st.length() > 0 ? cities.usStates.get(st) : null;
			if (mine != null) {
				rows.add(new Bubble.Row(st, null, Bubble.Row.HEADER, false));
				acts.add(null);
				for (final Cities.City c : mine) {
					rows.add(Bubble.Row.item(c.name));
					acts.add(new Runnable() {
						public void run() {
							Log.i(MainActivity.this, "LOC", "aleasă din statul " + st + ": " + c.label());
							choose(c);
						}
					});
				}
			} else {
				rows.add(new Bubble.Row(Tr.t("Your state", "Statul tău"), null, Bubble.Row.HEADER, false));
				acts.add(null);
				rows.add(Bubble.Row.item(Tr.t("Allow location to see your state's towns", "Permite localizarea ca să vezi localitățile statului tău")));
				acts.add(new Runnable() {
					public void run() {
						Log.i(MainActivity.this, "US", "statul nu e cunoscut: caut poziția");
						if (!Locator.allowed(MainActivity.this)) askLocation();
						else {
							setAutoLoc(true);
							locate(true);
						}
					}
				});
			}
			rows.add(new Bubble.Row("", null, Bubble.Row.LINE, false));
			acts.add(null);
			rows.add(new Bubble.Row(Tr.t("Other states", "Alte state"), null, Bubble.Row.HEADER, false));
			acts.add(null);
			for (final String o : cities.usStates.keySet()) {
				if (o.equals(st)) continue;
				rows.add(Bubble.Row.item(o));
				acts.add(new Runnable() {
					public void run() {
						Log.i(MainActivity.this, "LOC", "listă: statul " + o);
						pickState(b, o);
					}
				});
			}
		}
		bubble.show(view.px(b), bubbleWidth(0.9f, 300), null, rows, en, new Bubble.Pick() {
			public void picked(int k) {
				if (k >= 0 && k < acts.size() && acts.get(k) != null) acts.get(k).run();
			}
		});
	}

	/** The favourites kept under KEY ("favs" or "favsUS"). */
	private List<Cities.City> favList(String key) {
		List<Cities.City> l = new ArrayList<Cities.City>();
		for (String k : prefs.getString(key, "").split("\n")) {
			Cities.City c = Cities.City.fromKey(k);
			if (c != null) l.add(c);
		}
		return l;
	}

	/** Another state (v2.30): its first 3 towns (all of them for the state the phone is in). */
	private void pickState(final float[] b, final String st) {
		reopen = new Runnable() {
			public void run() {
				pickState(b, st);
			}
		};
		List<Cities.City> all = cities.usStates.get(st);
		if (all == null) return;
		final List<Cities.City> l = new ArrayList<Cities.City>(st.equals(Region.state(this)) ? all : all.subList(0, Math.min(3, all.size())));
		List<Bubble.Row> rows = Bubble.rows();
		rows.add(Bubble.Row.item(Tr.t("‹  Back", "‹  Înapoi")));
		for (Cities.City c : l) rows.add(Bubble.Row.item(c.name));
		bubble.show(view.px(b), bubbleWidth(0.9f, 300), st, rows, s.T.en, new Bubble.Pick() {
			public void picked(int k) {
				if (k == 0) onCity(b);
				else if (k - 1 < l.size()) {
					Log.i(MainActivity.this, "LOC", "aleasă din listă (" + st + "): " + l.get(k - 1).label());
					choose(l.get(k - 1));
				}
			}
		});
	}

	/** The 3 US favourites, when there are none yet: the first 3 towns of the state (Oklahoma: Claremore, Pryor, Catoosa). */
	private void seedUSFavs() {
		if (prefs.getString("favsUS", "").trim().length() > 0) return;
		String st = Region.state(this);
		List<Cities.City> l = st.length() > 0 ? cities.usStates.get(st) : null;
		if (l == null || l.isEmpty()) return;
		StringBuilder b = new StringBuilder(), names = new StringBuilder();
		for (int k = 0; k < Math.min(3, l.size()); k++) {
			b.append(b.length() > 0 ? "\n" : "").append(l.get(k).key());
			names.append(names.length() > 0 ? ", " : "").append(l.get(k).name);
		}
		prefs.edit().putString("favsUS", b.toString()).apply();
		Log.i(this, "US", "favorite SUA de pornire (" + st + "): " + names);
	}

	/**
	 * The location found a town in COUNTRY (STATE in the US, v2.30): the phone's country and state
	 * are kept; a new state changes the towns listed, the US favourites stay.
	 */
	private void usFound(String country, String state) {
		String was = prefs.getString("locCountry", ""), wasSt = prefs.getString("usState", "");
		prefs.edit().putString("locCountry", country).putString("usState", state).apply();
		if (!country.equals(was) || !state.equals(wasSt))
			Log.i(this, "US", "telefonul e în " + country.toUpperCase() + (state.length() > 0 ? ", statul " + state : "") + (was.length() > 0 ? " (înainte " + was.toUpperCase() + (wasSt.length() > 0 ? ", " + wasSt : "") + ")" : ""));
		if (country.equals("us")) seedUSFavs();
		applyUnits();
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
		rows.add(Bubble.Row.item(Tr.t("‹  Back", "‹  Înapoi")));
		for (Cities.City c : l) rows.add(Bubble.Row.item(c.name));
		bubble.show(view.px(b), bubbleWidth(0.9f, 300), countries.shown(cc), rows, en, new Bubble.Pick() {
			public void picked(int k) {
				if (k == 0) onCity(b);
				else {
					Log.i(MainActivity.this, "LOC", "aleasă din listă (" + cc + "): " + l.get(k - 1).label());
					choose(l.get(k - 1));
				}
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
		rows.add(Bubble.Row.item(Tr.t("‹  Back to the counties", "‹  Înapoi la județe")));
		for (Cities.City c : l) rows.add(Bubble.Row.item(c.name));
		bubble.show(view.px(b), bubbleWidth(0.9f, 300), county, rows, en, new Bubble.Pick() {
			public void picked(int k) {
				if (k == 0) onCity(b);
				else {
					Log.i(MainActivity.this, "LOC", "aleasă din listă (" + county + "): " + l.get(k - 1).label());
					choose(l.get(k - 1));
				}
			}
		});
	}

	/** A town picked from the list: automatic location goes off until the locate button. */
	private void choose(Cities.City c) {
		// v5.30 (Viorel): another town keeps farmer mode and the parcels' screens; only the town's tab changes.
		setAutoLoc(false);
		setCity(c, true);
		setDistrict("");
		fetch();
	}

	/** The shown town, kept in the settings and first among the favourites (the last three). */
	private void setCity(Cities.City c, boolean clear) {
		Log.i(this, "LOC", "localitatea: " + c.label() + " (" + c.lat + ", " + c.lon + ")");
		// US towns and the others have their own 3 favourites, never mixed (v2.30).
		String key = Region.favsKey(c);
		if (c.us()) seedUSFavs();
		StringBuilder favs = new StringBuilder(c.key());
		int n = 1;
		for (String k : prefs.getString(key, "").split("\n")) {
			Cities.City f = Cities.City.fromKey(k);
			if (n >= 3 || f == null || f.same(c)) continue;
			favs.append('\n').append(k);
			n++;
		}
		prefs.edit().putString("city", c.key()).putString(key, favs.toString()).apply();
		boolean same = c.same(town);
		boolean kind = town == null || town.us() != c.us();
		// v3.90 (Viorel): the notices of the town left behind go away.
		if (!same && town != null) Notifier.clearTown(this, town);
		town = c;
		// A parcel shown (v2.60): the location changed the town in the background, the parcel stays.
		if (pageId != 0 && !clear) {
			pageState();
			return;
		}
		// A town picked by hand: its own screen.
		if (pageId != 0) {
			pageId = 0;
			prefs.edit().putLong("farmPage", 0).apply();
			clear = true;
		}
		pageState();
		applyUnits();
		if (kind) Log.i(this, "US", "localitate " + (c.us() ? "din SUA: unități " : "din afara SUA: unități ") + (s.unitF ? "imperiale" : "metrice") + ", ceas " + (s.clk12 ? "12 h" : "24 h"));
		if (clear || !same) {
			s.farm = null;
			s.w = null;
			s.warns = null;
			s.quakes = null;
			s.pollenLvl = -1;
			s.aqi = -1;
			s.fetched = 0;
		}
		s.error = "";
		view.invalidate();
	}
}
