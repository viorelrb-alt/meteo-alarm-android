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
	static final String VERSION = "Meteo Alarm Android v3.20";
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
	private static final int REQ_PARCELS = 21;
	private long lastTry, lastLocate, lastDone;
	static final long REPULL_MS = 10000;
	private Locator locator;
	private Updater updater;
	private BottomSheet sheet;
	private Bubble bubble;
	private Countries countries;
	private HelpPanel help;
	/** The text-size bar over the pages (SizeOverlay, v2.12). */
	private SizeOverlay sizer;
	private static final long UPDATE_CHECK_MS = 7 * 24 * 3600000L;
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
		// Towns found by the location, kept on the phone.
		cleanNeighbourhoods();
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
		// The text-size bar over the pages (v2.12): above every window, on the left like the brightness bar.
		sizer = new SizeOverlay(this, view.screen);
		root.addView(sizer, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		help.setSizer(sizer);
		sheet.setSizer(sizer);
		setContentView(root);
	}

	@Override
	public void onBackPressed() {
		if (sizer != null && sizer.isOpen()) sizer.hide();
		else if (bubble.isOpen()) bubble.hide();
		else if (help.isOpen()) help.hide();
		else if (sheet.isOpen()) {
			if (sheet.isCancelable()) sheet.cancel();
		} else super.onBackPressed();
	}

	/** About the app: shown once at the first start (before the location question) and from ☰ › Despre. */
	private String aboutText() {
		boolean en = s.T.en;
		return Updater.howTo(en) + "\n\n"
				+ Tr.t("This is a test version of Meteo Alarm Android, made from Meteo Alarm Kindle.",
						"Aceasta este o versiune de test a Meteo Alarm Android, făcută din Meteo Alarm Kindle.") + "\n\n"
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
				+ Tr.t("Sensors: the app reads the battery state (night clock while charging), the phone's light sensor with ☰ › Theme › Automatic (phone sensor), and the compass, which turns the wind arrow with the phone. What the phone does not have is turned off. Nothing leaves the phone.",
						"Senzori: aplicația citește starea bateriei (ceasul de noapte la încărcare), senzorul de lumină al telefonului cu ☰ › Temă › Automat (senzor telefon) și busola, care rotește săgeata vântului după telefon. Ce nu are telefonul se dezactivează. Nimic nu pleacă de pe telefon.") + "\n\n"
				+ Tr.t("Updates: ", "Actualizări: ") + Updater.howTo(en) + " "
				+ Tr.t("After that, once a week the app checks GitHub for a new version and asks before downloading it.",
						"După aceea, o dată pe săptămână aplicația caută pe GitHub o versiune nouă și te întreabă înainte s-o descarce.") + "\n\n"
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
	private void clearPage() {
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
			if (resumed) note(Tr.t("Farmer's data: Open-Meteo is not answering; you get a notification when they come", "Date fermier: Open-Meteo nu răspunde; primești o notificare când vin"), 3500);
		}
		view.invalidate();
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
		startActivityForResult(new Intent(this, FarmActivity.class).putExtra("dark", s.dark).putExtra("open", "stats").putExtra("id", p.id), REQ_PARCELS);
	}

	/** A tap on the parcel's name: its whole name, its coordinates and what can be done with it. */
	private void parcelBubble() {
		final Parcels.Parcel p = Parcels.get(this, pageId);
		if (p == null) return;
		Log.i(this, "GEST", "atingere nume parcelă: " + p.name);
		reopen = null;
		List<Bubble.Row> rows = Bubble.rows();
		rows.add(new Bubble.Row(Tr.t("Coordinates: ", "Coordonate: ") + String.format(java.util.Locale.US, "%.5f, %.5f", p.lat, p.lon), null, Bubble.Row.TEXT, false));
		rows.add(new Bubble.Row(Tr.t("Near ", "Lângă ") + s.city.county + (s.city.abroad() ? ", " + s.city.country.toUpperCase() : ""), null, Bubble.Row.TEXT, false));
		rows.add(new Bubble.Row("", null, Bubble.Row.LINE, false));
		final String[] acts = { "stats", "map", "parcel" };
		rows.add(Bubble.Row.item(Tr.t("Statistics and PDF", "Statistici și PDF")));
		rows.add(Bubble.Row.item(Tr.t("Move the pin on the map", "Mută acul pe hartă")));
		rows.add(Bubble.Row.item(Tr.t("Alerts, rename, delete", "Alerte, redenumire, ștergere")));
		final int first = rows.size() - acts.length;
		bubble.show(view.px(s.cityArea), bubbleWidth(0.86f, 280), p.name, rows, s.T.en, new Bubble.Pick() {
			public void picked(int k) {
				if (k < first || k >= first + acts.length) return;
				bubble.hide();
				startActivityForResult(new Intent(MainActivity.this, FarmActivity.class).putExtra("dark", s.dark)
						.putExtra("open", acts[k - first]).putExtra("id", p.id), REQ_PARCELS);
			}
		});
	}

	@Override
	protected void onActivityResult(int code, int res, Intent data) {
		super.onActivityResult(code, res, data);
		if (code != REQ_PARCELS) return;
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
		if (k != s.net) {
			if (s.net >= 0) Log.i(this, "REȚEA", k == 0 ? "fără internet" : k == 1 ? "Wi-Fi" : k == 2 ? "date mobile" : "altă rețea");
			s.net = k;
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
		listenNet(true);
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
		// Look for a newer version once a week (the bottom window opens only when there is one).
		long now = System.currentTimeMillis();
		// Only with the user's consent, given in ☰ › Check for updates (Viorel 2026-11-02).
		if (updater.agreed() && now - prefs.getLong("updateChecked", 0) >= UPDATE_CHECK_MS) {
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
		Notifier.hello(this);
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
		List<String[]> info = s.alertInfo(k);
		if (info.isEmpty()) return;
		String head = info.get(0)[1];
		List<Bubble.Row> rows = Bubble.info(info);
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
		if (r.quake != null) s.quakes = Fetcher.quakes(this, c);
		if (r.farm != null) {
			try {
				s.farm = ro.meteoalarm.android.core.Farm.parse(r.farm);
			} catch (Exception e) {
				// keep the last one
			}
		} else if (s.farmer && r.error == null) {
			// Open-Meteo gave no farmer's data (MET Norway has none): said once, notified when they come.
			Parcels.pendingMain(this);
			if (resumed) note(Tr.t("Farmer's data: Open-Meteo is not answering; you get a notification when they come", "Date fermier: Open-Meteo nu răspunde; primești o notificare când vin"), 3500);
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
							note(Tr.t("Farmer's data: Open-Meteo is not answering; you get a notification when they come", "Date fermier: Open-Meteo nu răspunde; primești o notificare când vin"), 3500);
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
		bubble.show(view.px(b), bubbleWidth(0.86f, 280), info.get(0)[1], rows, s.T.en, new Bubble.Pick() {
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
		d.show();
	}

	public void onRefresh() {
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
				+ "\n\n" + Tr.t("Download the app (Android):", "Descarcă aplicația (Android):") + "\n" + repo + "/releases/download/v" + ver + "/MeteoAlarmAndroid-v" + ver + ".apk"
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
		// Farmer mode (v2.50): always the first item, naming the other mode; then the parcels.
		boolean farmer = prefs.getBoolean("farmer", false);
		ids.add("farmer"); rows.add(row(farmer ? Tr.t("Normal mode", "Mod Normal") : Tr.t("Farmer mode", "Mod Fermier"), farmer ? "normal" : "farm"));
		if (farmer) { ids.add("parcels"); rows.add(row(Tr.t("Parcels", "Parcele"), "parcel")); }
		ids.add("refresh"); rows.add(row(Tr.t("Refresh", "Reîmprospătare"), "refresh"));
		ids.add("city"); rows.add(row(Tr.t("Town", "Localitate"), "city"));
		ids.add("unit"); rows.add(row(Tr.t("Temperature °C/°F", "Temperatura °C/°F"), "unit"));
		ids.add("clock"); rows.add(row(Tr.t("Clock 12/24", "Ceas 12/24"), "clock"));
		ids.add("theme"); rows.add(row(Tr.t("Theme", "Temă"), "moon"));
		ids.add("keepOn"); rows.add(row(keepOn ? (Tr.t("Screen on OFF", "Ecran aprins OFF")) : (Tr.t("Screen on ON", "Ecran aprins ON")), "keepOn"));
		ids.add("lang"); rows.add(row(Tr.t("Language", "Limba") + " · " + Tr.name(s.T.lang), "lang-" + s.T.lang));
		ids.add("night"); rows.add(row(prefs.getBoolean("nightClock", true) ? (Tr.t("Night clock OFF", "Ceas noapte OFF"))
				: (Tr.t("Night clock ON", "Ceas noapte ON")), "night"));
		ids.add("loc"); rows.add(row(auto ? (Tr.t("Location Off", "Localizare Off")) : (Tr.t("Location On", "Localizare On")), "locate"));
		ids.add("quakes"); rows.add(row(Tr.t("Earthquakes", "Cutremure"), "quake"));
		if (Region.showUS(this)) { ids.add("tornado"); rows.add(row(Tr.t("Tornadoes", "Tornade"), "tornado")); }
		ids.add("notify"); rows.add(row(Notifier.on(this) ? Tr.t("Notifications OFF", "Notificări OFF") : Tr.t("Notifications ON", "Notificări ON"), "bell"));
		ids.add("widget"); rows.add(row(Tr.t("Widgets", "Widget-uri"), "widget"));
		ids.add("log"); rows.add(row(Tr.t("Log", "Jurnal"), "log"));
		ids.add("update"); rows.add(row(Tr.t("Check for updates", "Verifică actualizări"), "update"));
		ids.add("share"); rows.add(row(Tr.t("Send the app", "Trimite aplicația"), "share"));
		ids.add("help"); rows.add(row(Tr.t("Help", "Ajutor"), "help"));
		ids.add("about"); rows.add(row(Tr.t("About", "Despre"), "about"));
		bubble.show(view.px(s.btnMenu), bubbleWidth(0.3f, 160), null, rows, en, new Bubble.Pick() {
			public void picked(int k) {
				SharedPreferences.Editor e = prefs.edit();
				String it = ids.get(k);
				Log.i(MainActivity.this, "MENIU", it);
				String msg = null;
				long ms = 1500;
				if (it.equals("refresh")) { onRefresh(); return; }
				else if (it.equals("farmer")) { toggleFarmer(); return; }
				else if (it.equals("parcels")) { openParcels(); return; }
				else if (it.equals("city")) { onCity(s.btnMenu); return; }
				else if (it.equals("unit")) { toggleUnits(); return; }
				else if (it.equals("clock")) {
					// The clock of this kind of town (US or not, v2.30).
					Region.setClk12(MainActivity.this, s.city, !s.clk12);
					msg = s.clk12 ? (Tr.t("24-hour clock", "Ceas 24 de ore")) : (Tr.t("12-hour clock", "Ceas 12 ore"));
				} else if (it.equals("lang")) { pickLang(); return; }
				else if (it.equals("keepOn")) {
					boolean on = !prefs.getBoolean("keepOn", true);
					e.putBoolean("keepOn", on);
					ms = 2000;
					msg = on ? (Tr.t("The screen stays on while the app is open", "Ecranul rămâne aprins cât timp aplicația e deschisă"))
							: (Tr.t("The screen goes off after the phone's timeout", "Ecranul se stinge după timpul setat în telefon"));
				} else if (it.equals("theme")) { pickTheme(); return; }
				else if (it.equals("loc")) {
					onLocate();
					return;
				} else if (it.equals("update")) { updater.check(true); return; }
				else if (it.equals("share")) { shareApp(); return; }
				else if (it.equals("widget")) { startActivity(new Intent(MainActivity.this, WidgetConfig.class)); return; }
				else if (it.equals("log")) { startActivity(new Intent(MainActivity.this, LogActivity.class)); return; }
				else if (it.equals("help")) { help(); return; }
				else if (it.equals("quakes")) { quakesPage(); return; }
				else if (it.equals("tornado")) { tornadoPage(); return; }
				else if (it.equals("notify")) { toggleNotify(); return; }
				else if (it.equals("night")) {
					boolean on = !prefs.getBoolean("nightClock", true);
					e.putBoolean("nightClock", on);
					ms = 2000;
					msg = on ? (Tr.t("While charging at night, the screen shows a big dim clock", "La încărcare, noaptea, ecranul arată un ceas mare și estompat"))
							: (Tr.t("Night clock off", "Ceasul de noapte e oprit"));
				}
				else { about(); return; }
				e.apply();
				applySettings();
				view.invalidate();
				if (msg != null) note(msg, ms);
			}
		});
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
		final List<String> more = Lang.minorities(this);
		List<Bubble.Row> rows = Bubble.rows();
		for (String c : codes) rows.add(new Bubble.Row(Tr.name(c), mini("lang-" + c), Bubble.Row.ITEM, c.equals(s.T.lang)));
		// Romania: the minorities' languages in a submenu of their own (Viorel 2026-11-07).
		if (!more.isEmpty()) rows.add(new Bubble.Row(Tr.t("Other languages", "Alte limbi") + " ›", null, Bubble.Row.ITEM, more.contains(s.T.lang)));
		Log.i(this, "LIMBA", "lista de limbi: " + codes + (more.isEmpty() ? "" : " + " + more) + " (țara " + Lang.country(this) + ")");
		bubble.show(view.px(s.btnMenu), bubbleWidth(0.3f, 170), Tr.t("Language", "Limba"), rows, s.T.en, new Bubble.Pick() {
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

	private void setLang(String c) {
		Log.i(this, "LIMBA", "aleasă: " + Tr.name(c) + " (" + c + ")");
		prefs.edit().putString("lang", c).apply();
		applySettings();
		Widget.show(this);
		view.invalidate();
		note(Tr.name(c));
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
		String[] names = { Tr.t("Light", "Luminos"), Tr.t("Dark", "Întunecat"), Tr.t("Automatic", "Automat") };
		String[] icons = { "light", "dark", "sensor" };
		String cur = prefs.getString("theme", "auto");
		List<Bubble.Row> rows = Bubble.rows();
		for (int k = 0; k < keys.length; k++) rows.add(new Bubble.Row(names[k], mini(icons[k]), Bubble.Row.ITEM, keys[k].equals(cur)));
		bubble.show(view.px(s.btnMenu), bubbleWidth(0.3f, 160), Tr.t("Theme", "Temă"), rows, en, new Bubble.Pick() {
			public void picked(int k) {
				prefs.edit().putString("theme", keys[k]).apply();
				applySettings();
				view.invalidate();
			}
		});
	}

	/** The last lists of the earthquakes page: { country 7 days, Europe 48 h } (EMSC answers). */
	private String quakesCountry, quakesEurope, quakesKey;
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
				final String fa = a, fb = b;
				handler.post(new Runnable() {
					public void run() {
						if (!c.key().equals(quakesKey)) return;
						quakesCountry = fa == null ? "" : fa;
						quakesEurope = fb == null ? "" : fb;
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
		help.section(Tr.t("Last 7 days · ", "Ultimele 7 zile · ") + country);
		if (quakesCountry == null) help.text(Tr.t("Downloading from EMSC…", "Se descarcă de la EMSC…"));
		else if (quakesCountry.length() == 0) help.text(Tr.t("The list could not be downloaded. Is the internet on?", "Lista nu a putut fi descărcată. Ai internet?"));
		else {
			List<ro.meteoalarm.android.core.Quakes.Quake> l;
			try {
				l = ro.meteoalarm.android.core.Quakes.parse(quakesCountry, new String[] { Fetcher.countryEn(this, c) }, c.lat, c.lon, now, 7 * 86400000L);
			} catch (Exception e) {
				l = new ArrayList<ro.meteoalarm.android.core.Quakes.Quake>();
			}
			if (l.isEmpty()) help.text(Tr.t("No earthquake of magnitude 3 or more.", "Niciun cutremur cu magnitudinea 3 sau mai mare."));
			for (ro.meteoalarm.android.core.Quakes.Quake q : l) quakeEntry(q, c, true);
			Log.i(this, "CUTREMUR", country + ", 7 zile: " + l.size());
		}
		help.section(Tr.t("Last 48 hours · EU, Ukraine, Moldova", "Ultimele 48 de ore · UE, Ucraina, Moldova"));
		if (quakesEurope == null) help.text(Tr.t("Downloading from EMSC…", "Se descarcă de la EMSC…"));
		else if (quakesEurope.length() == 0) help.text(Tr.t("The list could not be downloaded. Is the internet on?", "Lista nu a putut fi descărcată. Ai internet?"));
		else {
			List<ro.meteoalarm.android.core.Quakes.Quake> l;
			try {
				l = ro.meteoalarm.android.core.Quakes.parse(quakesEurope, ro.meteoalarm.android.core.Quakes.EUROPE, c.lat, c.lon, now, 2 * 86400000L);
			} catch (Exception e) {
				l = new ArrayList<ro.meteoalarm.android.core.Quakes.Quake>();
			}
			if (l.isEmpty()) help.text(Tr.t("No earthquake of magnitude 3 or more.", "Niciun cutremur cu magnitudinea 3 sau mai mare."));
			for (ro.meteoalarm.android.core.Quakes.Quake q : l) quakeEntry(q, c, false);
			Log.i(this, "CUTREMUR", "UE + UA + MD, 48 h: " + l.size());
		}
		help.text(Tr.t("Source: EMSC (European-Mediterranean Seismological Centre, seismicportal.eu), magnitude 3 and over. \"Felt\" is an estimate on the EMS-98 scale from the magnitude and the distance to the focus.",
				"Sursa: EMSC (Centrul Seismologic European-Mediteranean, seismicportal.eu), magnitudine 3 și peste. „Resimțit” e o estimare pe scara EMS-98, după magnitudine și distanța până la focar."));
		help.show();
	}

	/** ☰ › Earthquakes for a US town (v2.30): USGS, 7 days around the town, 48 hours in the US. */
	private void showQuakesUS(Cities.City c) {
		long now = System.currentTimeMillis();
		String radius = s.unitF ? Tr.t("within 300 miles", "pe o rază de 300 de mile") : Tr.t("within 480 km", "pe o rază de 480 km");
		help.section(Tr.t("Last 7 days · ", "Ultimele 7 zile · ") + radius);
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
			for (ro.meteoalarm.android.core.Quakes.Quake q : l) quakeEntry(q, c, true);
			Log.i(this, "CUTREMUR", "USGS în jurul " + c.name + ", 7 zile: " + l.size());
		}
		help.section(Tr.t("Last 48 hours · United States", "Ultimele 48 de ore · Statele Unite"));
		if (quakesEurope == null) help.text(Tr.t("Downloading from USGS…", "Se descarcă de la USGS…"));
		else if (quakesEurope.length() == 0) help.text(Tr.t("The list could not be downloaded. Is the internet on?", "Lista nu a putut fi descărcată. Ai internet?"));
		else {
			List<ro.meteoalarm.android.core.Quakes.Quake> l;
			try {
				l = ro.meteoalarm.android.core.Quakes.parseUsgs(quakesEurope, c.lat, c.lon, now, 2 * 86400000L, ro.meteoalarm.android.core.Quakes.MIN_MAG, true);
			} catch (Exception e) {
				l = new ArrayList<ro.meteoalarm.android.core.Quakes.Quake>();
			}
			if (l.isEmpty()) help.text(Tr.t("No earthquake of magnitude 3 or more.", "Niciun cutremur cu magnitudinea 3 sau mai mare."));
			for (ro.meteoalarm.android.core.Quakes.Quake q : l) quakeEntry(q, c, false);
			Log.i(this, "CUTREMUR", "USGS SUA, 48 h: " + l.size());
		}
		help.text(Tr.t("Source: USGS (U.S. Geological Survey, earthquake.usgs.gov): magnitude 2.5 and over around your town, 3 and over in the whole country. \"Felt\" is an estimate from the magnitude and the distance to the focus, on a scale like the Modified Mercalli one.",
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
		help.text(Tr.t("Source: National Weather Service (weather.gov) for the watches and warnings; NOAA Storm Prediction Center (spc.noaa.gov) for the reports, which are preliminary: a tornado can be reported more than once, and EF is the strength when known.",
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

	private void quakeEntry(ro.meteoalarm.android.core.Quakes.Quake q, Cities.City c, boolean felt) {
		String name = "M " + String.format(java.util.Locale.US, "%.1f", q.mag).replace('.', Tr.dot()) + " · " + Screen.place(q.region, s.unitF);
		String what = s.T.weekdayTime(q.time, s.clk12) + " " + new java.text.SimpleDateFormat(s.unitF ? "M/d" : "dd.MM", java.util.Locale.US).format(new java.util.Date(q.time))
				+ " · " + Tr.t("depth ", "adâncime ") + s.dist(q.depth) + " · " + s.dist(q.dist) + " " + Tr.t("from ", "de ") + c.name;
		if (felt || q.dist < 300) what += "\n" + (q.felt >= 2 ? Tr.t("Probably felt in ", "Probabil resimțit în ") + c.name + ": " + Screen.roman(q.felt) + ", " + s.feltWords(q.felt)
				: Tr.t("Probably not felt in ", "Probabil nu s-a simțit în ") + c.name);
		help.entry(mini("quake"), name, what);
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
			help.entry(mini("unit"), Tr.t("Imperial units", "Unități imperiale"), Tr.t("°F, mph, miles, inHg and the 12-hour clock for US towns.", "°F, mph, mile, inHg și ceasul de 12 ore pentru localitățile din SUA."));
			help.entry(mini("roll"), Tr.t("Weather alerts", "Avertizări meteo"), Tr.t("From the National Weather Service: Warning red, Watch orange, Advisory yellow.", "De la National Weather Service: Warning roșu, Watch portocaliu, Advisory galben."));
			help.entry(mini("clock"), Tr.t("Local time", "Ora locală"), Tr.t("The clock and the hours show the town's local time.", "Ceasul și orele arată ora locală a localității."));
		}
		help.section(Tr.t("Farmer mode", "Mod Fermier"));
		help.entry(mini("farm"), Tr.t("Farmer mode / Normal mode", "Mod Fermier / Mod Normal"), Tr.t("The first item of ☰ switches the mode. In farmer mode each hour is one wide box with its weather and its rain; on its side, two boxes one under the other, slid up and down. Picking another town goes back to normal mode.",
				"Primul rând din ☰ schimbă modul. În modul fermier fiecare oră e o casetă lată, cu vremea și ploaia; cu telefonul culcat, două casete una sub alta, glisate sus-jos. Alegerea altei localități revine la modul normal."));
		help.entry(mini("farm"), Tr.t("Hour boxes", "Casetele orare"), Tr.t("A tap shows the hour's details: temperature, wind and its direction, humidity, rain, soil.",
				"Atingerea arată detaliile orei: temperatura, vântul și direcția lui, umiditatea, ploaia, solul."));
		help.entry(mini("farm"), Tr.t("Water in the soil", "Apa în sol"), Tr.t("The water held now in the soil's 3–9 cm layer and its change in 24 hours; a tap shows the hours with precipitation. An Open-Meteo estimate.",
				"Apa reținută acum în stratul de sol de 3–9 cm și schimbarea ei în 24 de ore; atingerea arată orele cu precipitații. Estimare Open-Meteo."));
		help.entry(mini("farm"), Tr.t("Farmer boxes", "Casetele fermierului"), Tr.t("After Water in the soil: SPRAYING (wind, rain, heat), WATER BALANCE (rain minus evaporation, 7 days), STORMS, GROWING DEGREES, 16 DAYS. A tap explains each one.",
				"După Apa în sol: STROPIT (vânt, ploaie, căldură), BILANȚ APĂ (ploaie minus evaporare, 7 zile), FURTUNI, GRADE-ZILE, 16 ZILE. Atingerea explică fiecare casetă."));
		help.entry(mini("farm"), Tr.t("Farmer boxes in the US", "Casetele fermierului în SUA"), Tr.t("For a US town also: NWS (official forecast), DROUGHT (US Drought Monitor), FIRE and HAIL (reports within 50 km).",
				"Pentru o localitate din SUA în plus: NWS (prognoza oficială), SECETĂ (US Drought Monitor), INCENDIU și GRINDINĂ (rapoarte pe 50 km)."));
		help.entry(mini("parcel"), Tr.t("Parcels", "Parcele"), Tr.t("☰ › Parcels: the map shows all the parcels; a tap on a pin or on a parcel opens it on the main screen. “Save GPS place” saves where you stand, “Choose on the map” works from home; the GPS button shows where you are (blue dot). Edit (or the pencil) changes the name and the pin. “Statistics · PDF” (on the map and on a parcel's main screen) opens its statistics. Kept only on this phone.",
				"☰ › Parcele: harta arată toate parcelele; atingerea unui ac sau a unei parcele o deschide pe ecranul principal. „Salvare loc. GPS” salvează locul unde stai, „Alege pe hartă” merge și de acasă; butonul GPS arată unde ești (punctul albastru). Editare (sau creionul) schimbă numele și acul. „Statistică · PDF” (pe hartă și pe ecranul principal al unei parcele) deschide statistica. Rămân doar în acest telefon."));
		help.entry(mini("parcel"), Tr.t("The ⋮ menu", "Meniul ⋮"), Tr.t("In Parcels, on a parcel's page and in Statistics the commands are in the ⋮ menu at the top right. The boxes show only the value; a tap shows the details. A wide table slides sideways.",
				"În Parcele, pe pagina unei parcele și în Statistică, comenzile sunt în meniul ⋮ din dreapta sus. Casetele arată doar valoarea; atingerea arată detaliile. Un tabel lat se glisează în lateral."));
		help.entry(mini("farm"), Tr.t("Info bubbles", "Bulele de informații"), Tr.t("Sections are split by lines; tables have lines and slide sideways; the source is at the bottom, once.",
				"Secțiunile sunt despărțite de linii; tabelele au linii și se glisează în lateral; sursa e jos, o singură dată."));
		help.entry(mini("parcel"), Tr.t("Precise location (GPS)", "Localizare precisă (GPS)"), Tr.t("On the parcel maps the blue dot shows where you are: the GPS starts with the page; the GPS button takes the map there.",
				"Pe hărțile parcelelor punctul albastru arată unde ești: GPS-ul pornește odată cu pagina; butonul GPS duce harta acolo."));
		help.entry(mini("swipe"), Tr.t("Main screens", "Ecrane principale"), Tr.t("One main screen for the town and one per parcel: slide sideways; the middle of the screen follows the finger and past half goes to the next one.",
				"Un ecran principal pentru localitate și câte unul pentru fiecare parcelă: glisează în lateral; mijlocul ecranului urmează degetul și după jumătate trece la următorul."));
		help.entry(mini("parcel"), Tr.t("A parcel's page", "Pagina unei parcele"), Tr.t("A tap on the parcel's name: its boxes, the next 12 hours, statistics with PDF, alerts, rename, delete.",
				"Atingerea numelui parcelei: casetele ei, următoarele 12 ore, statistică cu PDF, alerte, redenumire, ștergere."));
		help.entry(mini("bell"), Tr.t("Parcel alerts", "Alerte parcelă"), Tr.t("When ON, a notification for rain or strong wind on the parcel, and one per parcel for frost, freeze, fog, freezing rain or strong gusts.",
				"Când sunt PORNITE, o notificare pentru ploaie sau vânt puternic pe parcelă și câte una pe parcelă pentru brumă, îngheț, ceață, polei sau rafale puternice."));
		help.entry(mini("farm"), Tr.t("Soil data and source", "Datele solului și sursa"), Tr.t("Soil data are Open-Meteo model estimates, not measurements; nearby parcels can show the same values.",
				"Datele solului sunt estimări ale modelului Open-Meteo, nu măsurători; parcelele apropiate pot avea aceleași valori."));
		help.entry(mini("parcel"), Tr.t("The map", "Harta"), Tr.t("OpenStreetMap: move it with one finger, zoom with two; it needs internet.",
				"OpenStreetMap: o muți cu un deget, o mărești cu două; are nevoie de internet."));
		help.section(Tr.t("Updates", "Actualizări"));
		help.entry(mini("update"), Tr.t("How to update", "Cum actualizezi"), Updater.howTo(en));
		help.entry(mini("update"), Tr.t("Samsung: Auto Blocker", "Samsung: Auto Blocker"), Updater.autoBlocker());
		help.entry(mini("update"), Tr.t("When the download fails", "Când descărcarea nu merge"), Tr.t("The app checks the internet first, then looks on GitHub, on a second GitHub server and on Google Drive, and downloads from the first that answers. A version already downloaded is installed at once.",
				"Aplicația verifică întâi internetul, apoi caută pe GitHub, pe un server secundar GitHub și pe Google Drive și descarcă de la primul care răspunde. O versiune deja descărcată se instalează direct."));
		help.section(Tr.t("Buttons on the screen", "Butoane pe ecran"));
		help.entry(mini("city"), Tr.t("Town", "Localitate"), Tr.t("Choose the town: favourites, then counties (or states).", "Alegi localitatea: favorite, apoi județe (sau state)."));
		help.entry(mini("locate"), Tr.t("Location", "Localizare"), Tr.t("Turns automatic location on (filled) or off (empty); it shows the weather where you are.",
				"Pornește (plin) sau oprește (gol) localizarea automată; arată vremea unde ești."));
		help.entry(mini("bright"), Tr.t("Brightness", "Luminozitate"), Tr.t("Shows the brightness bar on the left.", "Arată bara de luminozitate din stânga."));
		help.entry(mini("moon"), Tr.t("Light / dark theme", "Temă luminoasă / întunecată"), Tr.t("Over the brightness bar: switches the theme.", "Deasupra barei de luminozitate: schimbă tema."));
		help.entry(mini("sensor"), Tr.t("Automatic theme (phone sensor)", "Temă automată (senzor telefon)"), Tr.t("Light theme in the light, dark in the dark (needs a light sensor).", "Tema luminoasă la lumină, cea întunecată pe întuneric (are nevoie de senzor de lumină)."));
		help.entry(mini("clock"), Tr.t("Connection", "Conexiune"), Tr.t("Next to the clock: Wi-Fi (arcs), mobile data (bars), another network (globe), struck through when there is no internet.",
				"Lângă ceas: Wi-Fi (arce), date mobile (bare), altă rețea (glob), tăiat când nu ai internet."));
		help.section(Tr.t("Gestures", "Gesturi"));
		help.entry(mini("locate"), Tr.t("Tap on the town's name", "Atingerea numelui localității"), Tr.t("Looks once with the GPS.", "Caută o dată poziția cu GPS-ul."));
		help.entry(mini("pull"), Tr.t("Pull down", "Trage în jos"), Tr.t("Refreshes the weather.", "Reîmprospătează vremea."));
		help.entry(mini("edge"), Tr.t("Left edge", "Marginea din stânga"), Tr.t("Slide up or down: brightness.", "Glisează sus-jos: luminozitatea."));
		help.entry(mini("textsize"), Tr.t("Text size", "Mărimea textului"), Tr.t("The Aa button on the pages shows the text-size bar.", "Butonul Aa de pe pagini arată bara de mărime a textului."));
		help.entry(mini("compass"), Tr.t("Wind arrow", "Săgeata vântului"), Tr.t("Shows where the wind blows, turned with the phone.", "Arată încotro bate vântul, rotită după telefon."));
		help.entry(mini("night"), Tr.t("Night clock", "Ceasul de noapte"), Tr.t("A big dim clock while the phone charges at night.", "Un ceas mare și estompat când telefonul se încarcă noaptea."));
		help.entry(mini("roll"), Tr.t("Warnings and earthquake", "Avertizări și cutremur"), Tr.t("One row under the line: the earthquake, then the warnings by colour. A tap shows the details.",
				"Un rând sub linie: cutremurul, apoi avertizările pe culori. Atingerea arată detaliile."));
		if (!us) help.entry(mini("roll"), Tr.t("Information boxes", "Casete de informare"), Tr.t("Blue INFORMATION boxes after the warnings: frost, freeze (spring and autumn), fog, freezing rain, strong wind in the next 24 hours. Worked out by the app from the forecast, like the NWS advisories; not official.",
				"Casetele albastre INFORMARE, după avertizări: brumă, îngheț (primăvara și toamna), ceață, polei, vânt tare în următoarele 24 de ore. Calculate de aplicație din prognoză, ca informările NWS; nu sunt oficiale."));
		help.entry(mini("quake"), Tr.t("Earthquake box", "Caseta Cutremur"), us ? Tr.t("The strongest earthquake of the last 24 hours within 300 miles (USGS).", "Cel mai puternic cutremur din ultimele 24 de ore, pe o rază de 300 de mile (USGS).")
				: Tr.t("The strongest earthquake of the last 24 hours in your country and how it was likely felt (EMSC).", "Cel mai puternic cutremur din ultimele 24 de ore din țara ta și cum s-a simțit probabil (EMSC)."));
		help.entry(mini("swipe"), Tr.t("Left / right on the boxes", "Stânga / dreapta pe casete"), Tr.t("The rows of boxes follow the finger.", "Rândurile de casete urmează degetul."));
		help.entry(mini("swipe"), Tr.t("The row of boxes at the bottom", "Rândul de casete de jos"), Tr.t("Sun / Moon, UV, Pollen, Humidity, Air quality, Pressure, Wind, Visibility, Compass; a tap shows the details.",
				"Soare / Lună, UV, Polen, Umiditate, Calitatea aerului, Presiune, Vânt, Vizibilitate, Busolă; atingerea arată detaliile."));
		help.section(Tr.t("Home-screen widgets", "Widget-uri pe ecranul de pornire"));
		help.entry(mini("widget"), Tr.t("Meteo Alarm widgets", "Widget-urile Meteo Alarm"), Tr.t("Hold the home screen › Widgets › Meteo Alarm. Drag the edges to change the layout (2x2, 4x1, 4x2, 4x3).",
				"Ține apăsat pe ecranul de pornire › Widgeturi › Meteo Alarm. Trage de margini ca să schimbi modelul (2x2, 4x1, 4x2, 4x3)."));
		help.entry(mini("widget"), Tr.t("Widget settings", "Setările widget-ului"), Tr.t("Hold the widget › Settings, or ☰ › Widgets: town, background, colours.",
				"Ține apăsat pe widget › Setări sau ☰ › Widget-uri: localitatea, fundalul, culorile."));
		help.entry(mini("refresh"), Tr.t("Refreshing by itself", "Reîmprospătare automată"), Tr.t("Every 15 minutes with the screen on, 30 with it off; MET Norway when Open-Meteo is busy.",
				"La 15 minute cu ecranul aprins, la 30 cu el stins; MET Norway când Open-Meteo e ocupat."));
		help.section(Tr.t("The ☰ menu", "Meniul ☰"));
		help.entry(mini("refresh"), Tr.t("Refresh", "Reîmprospătare"), Tr.t("Downloads the weather now.", "Descarcă vremea acum."));
		help.entry(mini("unit"), Tr.t("Temperature °C/°F", "Temperatura °C/°F"), Tr.t("Switches between Celsius and Fahrenheit.", "Comută între grade Celsius și Fahrenheit."));
		help.entry(mini("clock"), Tr.t("Clock 12/24", "Ceas 12/24"), Tr.t("Switches the clock between 24 hours and 12 hours (AM/PM).", "Comută ceasul între 24 de ore și 12 ore (AM/PM)."));
		help.entry(mini("moon"), Tr.t("Theme", "Temă"), Tr.t("Light, Dark, or Automatic (phone sensor).", "Luminos, Întunecat sau Automat (senzor telefon)."));
		help.entry(mini("keepOn"), Tr.t("Screen on ON / OFF", "Ecran aprins ON / OFF"), Tr.t("Keeps the screen on while the app is open.", "Ține ecranul aprins cât aplicația e deschisă."));
		help.entry(mini("lang-" + s.T.lang), Tr.t("Language", "Limba"), Tr.t("English, the phone's language and the country's languages; in Romania also “Other languages”.",
				"Engleza, limba telefonului și limbile țării; în România și „Alte limbi”."));
		help.entry(mini("locate"), Tr.t("Location On / Off", "Localizare On / Off"), Tr.t("Turns automatic location on or off (the same as the location button).", "Pornește sau oprește localizarea automată (la fel ca butonul de localizare)."));
		help.entry(mini("quake"), Tr.t("Earthquakes", "Cutremure"), Tr.t("The earthquakes of the last days near you, with time, place, magnitude and distance.", "Cutremurele din ultimele zile din jur, cu ora, locul, magnitudinea și distanța."));
		if (Region.showUS(this)) help.entry(mini("tornado"), Tr.t("Tornadoes", "Tornade"), Tr.t("Tornado alerts and reports near you, and what to do.", "Alertele și raportările de tornade din jur și ce faci."));
		help.entry(mini("bell"), Tr.t("Notifications ON / OFF", "Notificări ON / OFF"), Tr.t("Rain or snow starting, new warnings and earthquakes, each only once.", "Ploaie sau ninsoare care începe, avertizări noi și cutremure, fiecare o singură dată."));
		help.entry(mini("night"), Tr.t("Night clock ON / OFF", "Ceas noapte ON / OFF"), Tr.t("Turns the night clock on or off.", "Pornește sau oprește ceasul de noapte."));
		help.entry(mini("widget"), Tr.t("Widgets", "Widget-uri"), Tr.t("The settings of the widgets on the home screen.", "Setările widget-urilor de pe ecranul de pornire."));
		help.entry(mini("log"), Tr.t("Log", "Jurnal"), Tr.t("What the app did, with the time; read, share or save it.", "Ce a făcut aplicația, cu ora; îl citești, îl trimiți sau îl salvezi."));
		help.entry(mini("update"), Tr.t("Check for updates", "Verifică actualizări"), Tr.t("Looks for a new version on GitHub, with your consent.", "Caută o versiune nouă pe GitHub, cu acordul tău."));
		help.entry(mini("share"), Tr.t("Send the app", "Trimite aplicația"), Tr.t("Sends the download link, e.g. on WhatsApp.", "Trimite linkul de descărcare, de exemplu pe WhatsApp."));
		help.entry(mini("help"), Tr.t("Help", "Ajutor"), Tr.t("This page.", "Această pagină."));
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

	private void about() {
		String msg = aboutText() + "\n\n" + (Tr.t("The dark theme uses the Claude app colours. Font: Liberation Sans.", "Tema întunecată folosește culorile aplicației Claude. Font: Liberation Sans."));
		msg += "\n\n" + (Tr.t("Installed version: v", "Versiunea instalată: v")) + updater.installed()
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
			rows.add(new Bubble.Row(us ? Tr.t("Favourites · Europe", "Favorite · Europa") : Tr.t("Favourites", "Favorite"), null, Bubble.Row.HEADER, false));
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
			rows.add(new Bubble.Row(eu ? Tr.t("Favourites · USA", "Favorite · SUA") : Tr.t("Favourites", "Favorite"), null, Bubble.Row.HEADER, false));
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
				rows.add(new Bubble.Row(Tr.t("Towns · ", "Localități · ") + st, null, Bubble.Row.HEADER, false));
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
		// v3.03 (Viorel): another town picked by hand leaves farmer mode (the parcels belong to the old place).
		if (prefs.getBoolean("farmer", false) && !c.same(town)) {
			Log.i(this, "FERMIER", "localitate schimbată: ies din modul fermier");
			toggleFarmer();
		}
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
