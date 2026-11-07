package ro.meteoalarm.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Farm;
import ro.meteoalarm.android.core.Screen;
import ro.meteoalarm.android.core.Texts;
import ro.meteoalarm.android.core.Tr;

/**
 * Farmer mode (v2.50, Viorel 2026-11-07), ☰ › Parcels: the farmer's saved places. "Save the place
 * here" takes the phone's position, then the map (OpenStreetMap) shows it with a pin to move and a
 * name to give ("parcel 96/5"). A parcel's page has its weather in separate boxes (wind, wind
 * direction, chance of rain, air humidity, soil moisture, soil temperature, air temperature), its
 * alerts on or off, and its statistics for a chosen period of the last 28 days, which can be saved
 * as a PDF in Downloads. Everything stays on this phone.
 */
public final class FarmActivity extends Activity {
	private static final int ASK_LOC = 11, ASK_NOTIFY = 12, SAVE_PDF = 13;
	/** A parcel's data are downloaded again when older than this. */
	private static final long FRESH_MS = 30 * 60000L;

	private boolean dark, imp, clk12;
	private int page, ink, dim, band, bandInk, card, btn, accent;
	private float dp;
	private FrameLayout root;
	private final Handler ui = new Handler(Looper.getMainLooper());
	/** What is shown: "list", "parcel", "stats", "map". */
	private String screen = "list";
	private long curId;
	/** The map: the parcel moved (its id) or a new one (-1); where to go back. */
	private long mapId = -1;
	private String mapBack = "list";
	private String from, to;
	private byte[] pdfBytes;
	private String pdfName;
	private Runnable afterLoc;
	private LocationManager lm;
	private LocationListener locListener;
	private TextView status;

	@Override
	protected void onCreate(Bundle b) {
		dark = getIntent().getBooleanExtra("dark", false);
		setTheme(dark ? android.R.style.Theme_DeviceDefault_NoActionBar : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
		super.onCreate(b);
		Lang.apply(this);
		dp = getResources().getDisplayMetrics().density;
		// The app's colours: the Kindle's black and white, or Claude's dark ones.
		page = dark ? 0xFF262624 : 0xFFFFFFFF;
		ink = dark ? 0xFFFAF9F5 : 0xFF000000;
		dim = dark ? 0xFFA6A39A : 0xFF555555;
		band = dark ? 0xFFEFE6D2 : 0xFF000000;
		bandInk = dark ? 0xFF262624 : 0xFFFFFFFF;
		card = dark ? 0xFF000000 : 0xFFFFFFFF;
		btn = dark ? 0xFF30302E : 0xFFFFFFFF;
		accent = dark ? 0xFFD97757 : 0xFF000000;
		Cities.City c = Cities.City.fromKey(Fetcher.prefs(this).getString("city", ""));
		imp = Region.imperial(this, c);
		clk12 = Region.clk12(this, c);
		getWindow().getDecorView().setBackgroundColor(page);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		root = new FrameLayout(this);
		root.setBackgroundColor(page);
		setContentView(root);
		Log.i(this, "FERMIER", "pagina Parcele deschisă (" + Parcels.all(this).size() + " parcele)");
		showList();
	}

	@Override
	public void onBackPressed() {
		if (screen.equals("map")) {
			stopLoc();
			if (mapBack.equals("parcel")) showParcel(curId);
			else showList();
		} else if (screen.equals("stats")) showParcel(curId);
		else if (screen.equals("parcel")) showList();
		else super.onBackPressed();
	}

	@Override
	protected void onDestroy() {
		stopLoc();
		super.onDestroy();
	}

	// --- Building blocks ------------------------------------------------------------------

	private int px(float v) {
		return Math.round(v * dp);
	}

	private TextView text(String s, float sp, boolean bold, int color) {
		TextView t = new TextView(this);
		t.setText(s);
		t.setTextSize(sp * TextSize.get(this));
		t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
		t.setTextColor(color);
		return t;
	}

	private GradientDrawable frame(int fill, float radius) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(fill);
		g.setStroke(px(2), ink);
		g.setCornerRadius(px(radius));
		return g;
	}

	private TextView button(String label, View.OnClickListener l) {
		TextView t = text(label, 16, true, ink);
		t.setGravity(Gravity.CENTER);
		t.setPadding(px(12), px(10), px(12), px(10));
		t.setMinHeight(px(48));
		t.setBackground(frame(btn, 10));
		t.setOnClickListener(l);
		return t;
	}

	private LinearLayout column() {
		LinearLayout l = new LinearLayout(this);
		l.setOrientation(LinearLayout.VERTICAL);
		return l;
	}

	private LinearLayout.LayoutParams wide(int top) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.topMargin = px(top);
		return lp;
	}

	/** The page's title row: a back arrow and TITLE. */
	private LinearLayout header(String title) {
		LinearLayout row = new LinearLayout(this);
		row.setGravity(Gravity.CENTER_VERTICAL);
		TextView back = button("‹", new View.OnClickListener() {
			public void onClick(View v) {
				onBackPressed();
			}
		});
		back.setTextSize(24);
		back.setPadding(0, 0, 0, px(4));
		row.addView(back, new LinearLayout.LayoutParams(px(52), px(52)));
		TextView t = text(title, 22, true, ink);
		t.setPadding(px(14), 0, 0, 0);
		t.setSingleLine(true);
		t.setEllipsize(android.text.TextUtils.TruncateAt.END);
		row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		return row;
	}

	/** A box like the app's: a title band, a big value and a line under it. */
	private View box(String title, String value, String sub) {
		LinearLayout b = column();
		b.setBackground(frame(card, 14));
		b.setPadding(px(2), px(2), px(2), px(10));
		TextView h = text(title.toUpperCase(), 13, true, bandInk);
		h.setGravity(Gravity.CENTER);
		h.setSingleLine(true);
		h.setEllipsize(android.text.TextUtils.TruncateAt.END);
		h.setPadding(px(6), px(6), px(6), px(6));
		GradientDrawable hb = new GradientDrawable();
		hb.setColor(band);
		float r = px(12);
		hb.setCornerRadii(new float[] { r, r, r, r, 0, 0, 0, 0 });
		h.setBackground(hb);
		b.addView(h, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		TextView v = text(value, 30, true, ink);
		v.setGravity(Gravity.CENTER);
		v.setPadding(px(6), px(8), px(6), 0);
		b.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		if (sub != null && sub.length() > 0) {
			TextView s = text(sub, 13, false, dim);
			s.setGravity(Gravity.CENTER);
			s.setPadding(px(6), px(2), px(6), 0);
			b.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		return b;
	}

	/** Two views side by side, of equal width. */
	private LinearLayout pair(View a, View b) {
		LinearLayout row = new LinearLayout(this);
		LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
		la.rightMargin = px(6);
		row.addView(a, la);
		LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
		lb.leftMargin = px(6);
		if (b != null) row.addView(b, lb);
		else row.addView(new View(this), lb);
		return row;
	}

	/** A scrolled page: SET filled by the caller. */
	private LinearLayout pageOf(String title) {
		root.removeAllViews();
		ScrollView sv = new ScrollView(this);
		sv.setFillViewport(true);
		LinearLayout col = column();
		col.setPadding(px(16), px(14), px(16), px(24));
		col.addView(header(title));
		sv.addView(col);
		root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		return col;
	}

	private void toast(String s) {
		Toast.makeText(this, s, Toast.LENGTH_LONG).show();
	}

	private AlertDialog.Builder dialog() {
		return new AlertDialog.Builder(this, dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
	}

	// --- Units ------------------------------------------------------------------------------

	private String deg(double c) {
		return Double.isNaN(c) ? "–" : Math.round(imp ? c * 9 / 5 + 32 : c) + "°";
	}

	private String kmh(double v) {
		return Double.isNaN(v) ? "–" : imp ? Math.round(v * 0.621371) + " mph" : Math.round(v) + " km/h";
	}

	private String mm(double v) {
		if (Double.isNaN(v)) return "–";
		return imp ? String.format(Locale.US, "%.2f in", v / 25.4).replace('.', Tr.dot()) : String.format(Locale.US, "%.1f mm", v).replace('.', Tr.dot());
	}

	private static String pct(double v) {
		return Double.isNaN(v) ? "–" : Math.round(v) + "%";
	}

	private static String dir8(double d) {
		return Double.isNaN(d) ? "–" : Tr.dirs8()[Farm.sector(d)];
	}

	private String soil(double m) {
		if (Double.isNaN(m)) return "–";
		return Math.round(m * 100) + "%";
	}

	private String soilWord(double m) {
		String[] words = { Tr.t("dry", "uscat"), Tr.t("moderate", "potrivit"), Tr.t("moist", "umed"), Tr.t("very wet", "foarte umed") };
		int lv = Farm.soilLevel(m);
		return lv < 0 ? "" : words[lv];
	}

	private String coords(double lat, double lon) {
		return String.format(Locale.US, "%.5f, %.5f", lat, lon);
	}

	/** "07.11.2026" (or "11/7/2026" in imperial) of a date "2026-11-07". */
	private String dateText(String iso) {
		if (iso == null || iso.length() < 10) return "";
		String y = iso.substring(0, 4), m = iso.substring(5, 7), d = iso.substring(8, 10);
		return imp ? Integer.parseInt(m) + "/" + Integer.parseInt(d) + "/" + y : d + "." + m + "." + y;
	}

	private String time(long ms) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(ms);
		return Texts.clock(String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)), clk12);
	}

	// --- The list -----------------------------------------------------------------------------

	private void showList() {
		screen = "list";
		LinearLayout col = pageOf(Tr.t("Parcels", "Parcele"));
		TextView sub = text(Tr.t("Kept only on this phone, until you delete them. Their statistics are kept 28 days.",
				"Salvate doar pe acest telefon, până le ștergi. Statistica lor se păstrează 28 de zile."), 14, false, dim);
		col.addView(sub, wide(8));
		col.addView(button("＋  " + Tr.t("Save the place here", "Salvează locul aici"), new View.OnClickListener() {
			public void onClick(View v) {
				saveHere();
			}
		}), wide(14));
		col.addView(button(Tr.t("Choose on the map", "Alege pe hartă"), new View.OnClickListener() {
			public void onClick(View v) {
				Cities.City c = Cities.City.fromKey(Fetcher.prefs(FarmActivity.this).getString("city", ""));
				showMap(-1, "", c != null ? c.lat : 44.2058, c != null ? c.lon : 27.3306, 13, "list");
			}
		}), wide(10));
		status = text("", 14, false, dim);
		col.addView(status, wide(8));
		List<Parcels.Parcel> all = Parcels.all(this);
		if (all.isEmpty()) {
			col.addView(text(Tr.t("No parcel saved yet. Stand on the parcel and tap “Save the place here”, or choose it on the map.",
					"Nicio parcelă salvată încă. Stai pe parcelă și atinge „Salvează locul aici”, sau alege-o pe hartă."), 16, false, ink), wide(16));
			return;
		}
		for (final Parcels.Parcel p : all) {
			LinearLayout card = column();
			card.setBackground(frame(this.card, 14));
			card.setPadding(px(14), px(10), px(14), px(12));
			card.addView(text(p.name, 19, true, ink));
			long f = Parcels.fetched(this, p.id);
			String line = coords(p.lat, p.lon) + (p.alerts ? " · " + Tr.t("alerts on", "alerte pornite") : "");
			card.addView(text(line, 13, false, dim));
			Farm.Data d = Parcels.data(this, p.id);
			if (d != null) {
				int i = d.nowIndex(Calendar.getInstance());
				if (i >= 0) card.addView(text(deg(d.temp[i]) + " · " + Tr.t("wind ", "vânt ") + dir8(d.dir[i]) + " " + kmh(d.wind[i]) + " · "
						+ Tr.t("rain ", "ploaie ") + pct(d.prob[i]) + " · " + Tr.t("soil ", "sol ") + soil(d.soilM[i]), 15, false, ink));
				if (f > 0) card.addView(text(Tr.t("updated ", "actualizat ") + time(f), 12, false, dim));
			}
			card.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					showParcel(p.id);
				}
			});
			col.addView(card, wide(12));
		}
	}

	// --- The phone's position -------------------------------------------------------------------

	/** "Save the place here": the phone's position, then the map with the pin there and a name. */
	private void saveHere() {
		Log.i(this, "FERMIER", "salvează locul aici");
		afterLoc = new Runnable() {
			public void run() {
				findMe(true);
			}
		};
		if (!Locator.allowed(this)) {
			requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, ASK_LOC);
			return;
		}
		afterLoc.run();
	}

	@Override
	public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
		if (code == ASK_LOC) {
			if (Locator.allowed(this) && afterLoc != null) afterLoc.run();
			else toast(Tr.t("Without location the place can only be chosen on the map.", "Fără localizare, locul se poate alege doar pe hartă."));
		} else if (code == ASK_NOTIFY && !Notifier.allowed(this))
			toast(Tr.t("The phone does not allow notifications for the app: the alerts cannot be shown.", "Telefonul nu permite notificări aplicației: alertele nu pot fi afișate."));
	}

	/**
	 * Looks for the phone's position (GPS first, it is the most precise; 40 s at most). NEW: then the
	 * map for a new parcel; else the map's blue dot and its middle.
	 */
	@SuppressWarnings("MissingPermission")
	private void findMe(final boolean isNew) {
		stopLoc();
		lm = (LocationManager) getSystemService(LOCATION_SERVICE);
		if (lm == null) return;
		if (isNew && status != null) status.setText(Tr.t("Looking for the position (GPS)… stay outside, on the parcel.", "Caut poziția (GPS)… stai afară, pe parcelă."));
		Location best = null;
		try {
			for (String p : lm.getAllProviders()) {
				Location l = lm.getLastKnownLocation(p);
				if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
			}
		} catch (SecurityException e) {
			// not allowed
		}
		final Location last = best;
		List<String> on = lm.getProviders(true);
		String prov = on.contains(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER
				: on.contains("fused") ? "fused" : on.contains(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null;
		timeout = new Runnable() {
			public void run() {
				stopLoc();
				found(last, isNew, true);
			}
		};
		if (prov == null) {
			found(last, isNew, true);
			return;
		}
		locListener = new LocationListener() {
			public void onLocationChanged(Location l) {
				stopLoc();
				found(l, isNew, false);
			}

			public void onStatusChanged(String p, int s, Bundle e) {}

			public void onProviderEnabled(String p) {}

			public void onProviderDisabled(String p) {}
		};
		try {
			lm.requestLocationUpdates(prov, 0, 0, locListener, Looper.getMainLooper());
			ui.postDelayed(timeout, 40000);
		} catch (Exception e) {
			found(last, isNew, true);
		}
	}

	private Runnable timeout;

	private void stopLoc() {
		if (timeout != null) ui.removeCallbacks(timeout);
		timeout = null;
		if (lm != null && locListener != null) {
			try {
				lm.removeUpdates(locListener);
			} catch (Exception e) {
				// gone
			}
		}
		locListener = null;
	}

	private MapView map;

	private void found(Location l, boolean isNew, boolean late) {
		if (l != null) Log.i(this, "FERMIER", "poziție " + l.getProvider() + " ±" + Math.round(l.getAccuracy()) + " m" + (late ? " (ultima cunoscută)" : ""));
		if (!isNew) {
			if (l != null && map != null) {
				map.setMe(l.getLatitude(), l.getLongitude());
				map.center(l.getLatitude(), l.getLongitude());
			} else if (l == null) toast(Tr.t("The position was not found.", "Poziția nu a fost găsită."));
			return;
		}
		if (status != null) status.setText("");
		Cities.City c = Cities.City.fromKey(Fetcher.prefs(this).getString("city", ""));
		if (l == null) {
			toast(Tr.t("The position was not found: move the map to the parcel.", "Poziția nu a fost găsită: mută harta la parcelă."));
			showMap(-1, "", c != null ? c.lat : 44.2058, c != null ? c.lon : 27.3306, 13, "list");
			return;
		}
		showMap(-1, "", l.getLatitude(), l.getLongitude(), 17, "list");
		map.setMe(l.getLatitude(), l.getLongitude());
		if (l.getAccuracy() > 50)
			toast(Tr.t("The position is approximate (±", "Poziția e aproximativă (±") + Math.round(l.getAccuracy()) + Tr.t(" m): check the pin on the map.", " m): verifică acul pe hartă."));
	}

	// --- The map --------------------------------------------------------------------------------

	/** The map with the pin at LAT, LON: a new parcel (ID -1, with NAME to give) or one to move. */
	private void showMap(final long id, String name, double lat, double lon, double zoom, String back) {
		screen = "map";
		mapId = id;
		mapBack = back;
		root.removeAllViews();
		LinearLayout col = column();
		col.setPadding(px(12), px(10), px(12), px(10));
		col.addView(header(id < 0 ? Tr.t("New parcel", "Parcelă nouă") : Tr.t("Move the pin", "Mută acul")));
		final EditText nameIn = new EditText(this);
		nameIn.setSingleLine(true);
		nameIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		nameIn.setHint(Tr.t("Name, e.g. parcel 96/5", "Nume, ex. parcela 96/5"));
		nameIn.setText(name);
		nameIn.setTextColor(ink);
		nameIn.setHintTextColor(dim);
		nameIn.setTextSize(18);
		col.addView(nameIn, wide(6));
		final TextView where = text("", 13, false, dim);
		col.addView(where, wide(2));
		map = new MapView(this);
		map.setDark(dark);
		map.clean();
		map.setOnMoved(new MapView.Moved() {
			public void moved(double la, double lo) {
				where.setText(coords(la, lo) + "  ·  " + Tr.t("move the map to put the pin on the parcel", "mută harta ca acul să fie pe parcelă"));
			}
		});
		map.setZoom(zoom);
		map.center(lat, lon);
		LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
		ml.topMargin = px(6);
		col.addView(map, ml);
		LinearLayout zoomRow = new LinearLayout(this);
		zoomRow.addView(button("−", new View.OnClickListener() {
			public void onClick(View v) {
				map.zoomBy(-1);
			}
		}), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		View gap1 = new View(this);
		zoomRow.addView(gap1, new LinearLayout.LayoutParams(px(8), 1));
		zoomRow.addView(button("＋", new View.OnClickListener() {
			public void onClick(View v) {
				map.zoomBy(1);
			}
		}), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		View gap2 = new View(this);
		zoomRow.addView(gap2, new LinearLayout.LayoutParams(px(8), 1));
		zoomRow.addView(button(Tr.t("My position", "Poziția mea"), new View.OnClickListener() {
			public void onClick(View v) {
				afterLoc = new Runnable() {
					public void run() {
						findMe(false);
					}
				};
				if (!Locator.allowed(FarmActivity.this))
					requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, ASK_LOC);
				else findMe(false);
			}
		}), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2));
		col.addView(zoomRow, wide(8));
		col.addView(button(Tr.t("Save", "Salvează"), new View.OnClickListener() {
			public void onClick(View v) {
				String n = nameIn.getText().toString().trim();
				if (n.length() == 0) {
					nameIn.setError(Tr.t("Give the parcel a name", "Dă un nume parcelei"));
					nameIn.requestFocus();
					return;
				}
				Parcels.Parcel p = id >= 0 ? Parcels.get(FarmActivity.this, id) : null;
				if (p == null) {
					p = new Parcels.Parcel();
					p.id = System.currentTimeMillis();
				}
				p.name = n;
				p.lat = map.lat();
				p.lon = map.lon();
				Parcels.put(FarmActivity.this, p);
				Log.i(FarmActivity.this, "FERMIER", (id >= 0 ? "parcelă mutată: " : "parcelă salvată: ") + n + " " + coords(p.lat, p.lon));
				stopLoc();
				// Moved: the old data were for another place.
				if (id >= 0) Fetcher.prefs(FarmActivity.this).edit().putLong("pf_" + p.id, 0).apply();
				showParcel(p.id);
			}
		}), wide(8));
		root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	// --- A parcel ---------------------------------------------------------------------------------

	private void showParcel(final long id) {
		final Parcels.Parcel p = Parcels.get(this, id);
		if (p == null) {
			showList();
			return;
		}
		screen = "parcel";
		curId = id;
		LinearLayout col = pageOf(p.name);
		col.addView(text(coords(p.lat, p.lon), 13, false, dim), wide(4));
		status = text("", 14, false, dim);
		col.addView(status, wide(4));
		Farm.Data d = Parcels.data(this, id);
		long f = Parcels.fetched(this, id);
		if (d != null) fill(col, d, f);
		if (d == null || System.currentTimeMillis() - f > FRESH_MS) download(p);
		else buttons(col, p);
	}

	/** Off the main thread: the parcel's data; the page again when they come. */
	private void download(final Parcels.Parcel p) {
		if (status != null) status.setText(Tr.t("Downloading from Open-Meteo…", "Descarc de la Open-Meteo…"));
		final android.content.Context app = getApplicationContext();
		new Thread(new Runnable() {
			public void run() {
				Exception err = null;
				try {
					Parcels.fetch(app, p);
				} catch (Exception e) {
					err = e;
					Log.e(app, "FERMIER", "parcela " + p.name + ": " + Fetcher.reason(e, false));
				}
				final Exception fe = err;
				ui.post(new Runnable() {
					public void run() {
						if (isFinishing() || !screen.equals("parcel") || curId != p.id) return;
						if (fe == null) {
							showParcel(p.id);
							return;
						}
						// Viorel: say that the data come later and are notified.
						Parcels.pending(FarmActivity.this, p.id);
						if (status != null) status.setText(Tr.t("Open-Meteo is not answering now (", "Open-Meteo nu răspunde acum (") + Fetcher.reason(fe, !Tr.ro())
								+ Tr.t("). When access is back, the app downloads the parcel's data and sends you a notification.",
										"). Când accesul revine, aplicația descarcă datele parcelei și îți trimite o notificare."));
						LinearLayout col = (LinearLayout) status.getParent();
						if (col != null && col.findViewWithTag("buttons") == null) buttons(col, p);
					}
				});
			}
		}).start();
	}

	/** The parcel's weather in separate boxes, the next 12 hours, the note on the soil. */
	private void fill(LinearLayout col, Farm.Data d, long fetched) {
		int i = d.nowIndex(Calendar.getInstance());
		boolean h = i >= 0;
		int di = h ? d.dayIndex(d.dateOf(i)) : -1;
		double temp = !Double.isNaN(d.cTemp) ? d.cTemp : h ? d.temp[i] : Double.NaN;
		double wind = !Double.isNaN(d.cWind) ? d.cWind : h ? d.wind[i] : Double.NaN;
		double gust = !Double.isNaN(d.cGust) ? d.cGust : h ? d.gust[i] : Double.NaN;
		double dir = !Double.isNaN(d.cDir) ? d.cDir : h ? d.dir[i] : Double.NaN;
		double hum = !Double.isNaN(d.cHum) ? d.cHum : h ? d.hum[i] : Double.NaN;
		if (fetched > 0) status.setText(Tr.t("updated ", "actualizat ") + time(fetched) + " · Open-Meteo");
		String hiLo = di >= 0 ? Tr.t("low ", "min ") + deg(d.dMin[di]) + " · " + Tr.t("high ", "max ") + deg(d.dMax[di]) : "";
		int sh = h ? Farm.windShift(d, i, 12) : -1;
		String shift = sh >= 0 ? Tr.t("turns to ", "se întoarce spre ") + dir8(d.dir[sh]) + Tr.t(" around ", " în jurul orei ") + Texts.clock(d.hourOf(sh), clk12)
				: Tr.t("steady for 12 hours", "stabilă 12 ore");
		String rainSub = di >= 0 ? Tr.t("today up to ", "azi până la ") + pct(d.dPop[di]) + " · " + mm(d.dRain[di]) : "";
		col.addView(pair(box(Tr.t("Air temperature", "Temperatura aerului"), deg(temp), hiLo),
				box(Tr.t("Wind", "Vânt"), kmh(wind), Tr.t("gusts ", "rafale ") + kmh(gust))), wide(12));
		col.addView(pair(box(Tr.t("Wind direction", "Direcția vântului"), dir8(dir), shift),
				box(Tr.t("Chance of rain", "Șansa de ploaie"), h ? pct(d.prob[i]) : "–", rainSub)), wide(12));
		col.addView(pair(box(Tr.t("Air humidity", "Umiditatea aerului"), pct(hum), ""),
				box(Tr.t("Soil moisture", "Umiditatea solului"), h ? soil(d.soilM[i]) : "–", (h ? soilWord(d.soilM[i]) + " · " : "") + Tr.t("3–9 cm, estimated", "3–9 cm, estimat"))), wide(12));
		col.addView(pair(box(Tr.t("Soil temperature", "Temperatura solului"), h ? deg(d.soilT[i]) : "–", Tr.t("surface, estimated", "suprafață, estimat")), null), wide(12));
		if (h) {
			col.addView(text(Tr.t("Next 12 hours", "Următoarele 12 ore").toUpperCase(), 13, true, dim), wide(16));
			for (int k = i + 1; k <= i + 12 && k < d.size(); k++) {
				String l = Texts.clock(d.hourOf(k), clk12) + "   " + deg(d.temp[k]) + "   " + Tr.t("rain ", "ploaie ") + pct(d.prob[k])
						+ (d.precip[k] >= 0.05 ? " " + mm(d.precip[k]) : "") + "   " + dir8(d.dir[k]) + " " + kmh(d.wind[k]);
				TextView t = text(l, 15, false, ink);
				t.setTypeface(Typeface.MONOSPACE);
				col.addView(t, wide(2));
			}
		}
		col.addView(text(Screen.soilNote(), 13, false, dim), wide(14));
	}

	/** The parcel's buttons: statistics, map, alerts, rename, delete. */
	private void buttons(LinearLayout col, final Parcels.Parcel p) {
		LinearLayout b = column();
		b.setTag("buttons");
		b.addView(button(Tr.t("Statistics (last 28 days)", "Statistică (ultimele 28 zile)"), new View.OnClickListener() {
			public void onClick(View v) {
				Calendar now = Calendar.getInstance();
				from = Farm.date(now, -6);
				to = Farm.date(now, 0);
				showStats(p.id);
			}
		}), wide(16));
		b.addView(button(Tr.t("Move the pin on the map", "Mută acul pe hartă"), new View.OnClickListener() {
			public void onClick(View v) {
				showMap(p.id, p.name, p.lat, p.lon, 17, "parcel");
			}
		}), wide(10));
		b.addView(button(p.alerts ? Tr.t("Rain / wind alerts: ON", "Alerte ploaie / vânt: PORNITE") : Tr.t("Rain / wind alerts: OFF", "Alerte ploaie / vânt: OPRITE"),
				new View.OnClickListener() {
					public void onClick(View v) {
						p.alerts = !p.alerts;
						Parcels.put(FarmActivity.this, p);
						Log.i(FarmActivity.this, "FERMIER", "alerte " + p.name + ": " + (p.alerts ? "pornite" : "oprite"));
						if (p.alerts) {
							if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
								requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, ASK_NOTIFY);
							toast(Tr.t("You get a notification when rain or strong wind is forecast for this parcel in the next 12 hours (checked every 3 hours).",
									"Primești o notificare când se prognozează ploaie sau vânt puternic pe această parcelă în următoarele 12 ore (verificat la 3 ore)."));
						}
						showParcel(p.id);
					}
				}), wide(10));
		b.addView(button(Tr.t("Rename", "Redenumește"), new View.OnClickListener() {
			public void onClick(View v) {
				rename(p);
			}
		}), wide(10));
		b.addView(button(Tr.t("Delete the parcel", "Șterge parcela"), new View.OnClickListener() {
			public void onClick(View v) {
				AlertDialog.Builder d = dialog();
				d.setTitle(Tr.t("Delete ", "Ștergi ") + p.name + "?");
				d.setMessage(Tr.t("The parcel and its statistics are deleted from the phone.", "Parcela și statistica ei se șterg din telefon."));
				d.setPositiveButton(Tr.t("Delete", "Șterge"), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface di, int w) {
						Parcels.delete(FarmActivity.this, p.id);
						Log.i(FarmActivity.this, "FERMIER", "parcelă ștearsă: " + p.name);
						showList();
					}
				});
				d.setNegativeButton(Tr.t("Cancel", "Anulează"), null);
				d.show();
			}
		}), wide(10));
		col.addView(b, wide(0));
	}

	private void rename(final Parcels.Parcel p) {
		final EditText in = new EditText(this);
		in.setSingleLine(true);
		in.setText(p.name);
		in.setSelection(p.name.length());
		AlertDialog.Builder d = dialog();
		d.setTitle(Tr.t("Rename", "Redenumește"));
		d.setView(in);
		d.setPositiveButton(Tr.t("Save", "Salvează"), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface di, int w) {
				String n = in.getText().toString().trim();
				if (n.length() == 0) return;
				p.name = n;
				Parcels.put(FarmActivity.this, p);
				showParcel(p.id);
			}
		});
		d.setNegativeButton(Tr.t("Cancel", "Anulează"), null);
		d.show();
	}

	// --- Statistics ----------------------------------------------------------------------------

	private void showStats(final long id) {
		final Parcels.Parcel p = Parcels.get(this, id);
		if (p == null) {
			showList();
			return;
		}
		screen = "stats";
		curId = id;
		final Calendar now = Calendar.getInstance();
		final String oldest = Farm.date(now, -(Farm.KEEP_DAYS - 1)), today = Farm.date(now, 0);
		if (from == null || from.compareTo(oldest) < 0) from = oldest;
		if (to == null || to.compareTo(today) > 0) to = today;
		if (from.compareTo(to) > 0) from = to;
		LinearLayout col = pageOf(Tr.t("Statistics · ", "Statistică · ") + p.name);
		col.addView(text(Tr.t("Choose the period, within the last 28 days.", "Alege perioada, în ultimele 28 de zile."), 14, false, dim), wide(6));
		LinearLayout chips = new LinearLayout(this);
		int[] spans = { 7, 14, 28 };
		for (int k = 0; k < spans.length; k++) {
			final int n = spans[k];
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
			if (k > 0) lp.leftMargin = px(8);
			chips.addView(button(n + Tr.t(" days", " zile"), new View.OnClickListener() {
				public void onClick(View v) {
					from = Farm.date(now, -(n - 1));
					to = today;
					showStats(id);
				}
			}), lp);
		}
		col.addView(chips, wide(10));
		LinearLayout range = new LinearLayout(this);
		LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		range.addView(button(Tr.t("From ", "De la ") + dateText(from), new View.OnClickListener() {
			public void onClick(View v) {
				pick(true, oldest, today, id);
			}
		}), l1);
		LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
		l2.leftMargin = px(8);
		range.addView(button(Tr.t("To ", "Până la ") + dateText(to), new View.OnClickListener() {
			public void onClick(View v) {
				pick(false, oldest, today, id);
			}
		}), l2);
		col.addView(range, wide(8));

		final List<Farm.Day> days = Farm.read(Parcels.stats(this, id), from, to);
		if (days.isEmpty()) {
			col.addView(text(Tr.t("No data for this period yet. Open the parcel once with internet: the app downloads the last 28 days from Open-Meteo.",
					"Încă nu sunt date pentru această perioadă. Deschide parcela o dată cu internet: aplicația descarcă ultimele 28 de zile de la Open-Meteo."), 16, false, ink), wide(16));
			return;
		}
		final double[] s = Farm.summary(days);
		col.addView(text(Tr.t("Summary", "Rezumat").toUpperCase() + " · " + dateText(from) + " – " + dateText(to), 13, true, dim), wide(16));
		col.addView(pair(box(Tr.t("Lowest / highest", "Minima / maxima"), deg(s[0]) + " / " + deg(s[1]), days.size() + Tr.t(" days", " zile")),
				box(Tr.t("Rain total", "Ploaie totală"), mm(s[2]), Math.round(s[5]) + Tr.t(" days with rain (1 mm+)", " zile cu ploaie (1 mm+)"))), wide(8));
		col.addView(pair(box(Tr.t("Highest wind", "Vânt maxim"), kmh(s[3]), Double.isNaN(s[6]) ? "" : Tr.t("gusts up to ", "rafale până la ") + kmh(s[6])),
				box(Tr.t("Soil moisture", "Umiditatea solului"), soil(s[4]), Tr.t("mean, estimated", "medie, estimată"))), wide(12));
		col.addView(text(Tr.t("Day by day", "Zi cu zi").toUpperCase(), 13, true, dim), wide(16));
		col.addView(row(true, Tr.t("Date", "Data"), Tr.t("Max", "Max"), Tr.t("Min", "Min"), Tr.t("Rain", "Ploaie"), Tr.t("Wind", "Vânt"), Tr.t("Soil", "Sol")), wide(4));
		for (int k = days.size() - 1; k >= 0; k--) {
			Farm.Day d = days.get(k);
			col.addView(row(false, dateText(d.date).substring(0, imp ? dateText(d.date).lastIndexOf('/') : 5), deg(d.tmax), deg(d.tmin), mm(d.rain), kmh(d.wind), soil(d.soil)), wide(2));
		}
		col.addView(text(Tr.t("Source: Open-Meteo (models and the past days' estimates). Soil moisture 3–9 cm deep, estimated.",
				"Sursa: Open-Meteo (modele și estimări pentru zilele trecute). Umiditatea solului la 3–9 cm, estimată."), 13, false, dim), wide(12));
		col.addView(button(Tr.t("Save as PDF in Downloads", "Salvează PDF în Descărcări"), new View.OnClickListener() {
			public void onClick(View v) {
				pdf(p, days, s);
			}
		}), wide(14));
	}

	/** A row of the table: date, high, low, rain, wind, soil. */
	private LinearLayout row(boolean head, String... v) {
		LinearLayout r = new LinearLayout(this);
		float[] wts = { 1.2f, 0.8f, 0.8f, 1.3f, 1.3f, 0.8f };
		for (int k = 0; k < v.length; k++) {
			TextView t = text(v[k], 14, head, head ? dim : ink);
			t.setSingleLine(true);
			t.setGravity(k == 0 ? Gravity.START : Gravity.END);
			r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, wts[k]));
		}
		r.setPadding(0, px(3), 0, px(3));
		return r;
	}

	private void pick(final boolean start, String oldest, String today, final long id) {
		String cur = start ? from : to;
		DatePickerDialog d = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
			public void onDateSet(DatePicker v, int y, int m, int day) {
				String s = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day);
				if (start) from = s;
				else to = s;
				if (from.compareTo(to) > 0) {
					if (start) to = from;
					else from = to;
				}
				showStats(id);
			}
		}, Integer.parseInt(cur.substring(0, 4)), Integer.parseInt(cur.substring(5, 7)) - 1, Integer.parseInt(cur.substring(8, 10)));
		d.getDatePicker().setMinDate(millis(oldest));
		d.getDatePicker().setMaxDate(millis(today) + 86400000L - 1);
		d.show();
	}

	private static long millis(String iso) {
		Calendar c = Calendar.getInstance();
		c.clear();
		c.set(Integer.parseInt(iso.substring(0, 4)), Integer.parseInt(iso.substring(5, 7)) - 1, Integer.parseInt(iso.substring(8, 10)));
		return c.getTimeInMillis();
	}

	// --- PDF -------------------------------------------------------------------------------------

	/** The statistics as an A4 PDF, saved in Downloads (Android 10+), else where the user picks. */
	private void pdf(Parcels.Parcel p, List<Farm.Day> days, double[] s) {
		try {
			pdfBytes = makePdf(p, days, s);
			pdfName = "MeteoAlarm-" + p.name.replaceAll("[^A-Za-z0-9ĂÂÎȘȚăâîșț_-]+", "-") + "-" + from + "_" + to + ".pdf";
			Log.i(this, "FERMIER", "PDF " + pdfName + " " + pdfBytes.length / 1024 + " KB");
			if (Build.VERSION.SDK_INT >= 29) {
				ContentValues v = new ContentValues();
				v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, pdfName);
				v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
				v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
				Uri u = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
				if (u == null) throw new Exception("MediaStore");
				OutputStream out = getContentResolver().openOutputStream(u);
				out.write(pdfBytes);
				out.close();
				saved(u);
			} else {
				Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf")
						.putExtra(Intent.EXTRA_TITLE, pdfName);
				startActivityForResult(i, SAVE_PDF);
			}
		} catch (Exception e) {
			Log.e(this, "FERMIER", "PDF", e);
			toast(Tr.t("The PDF could not be saved: ", "PDF-ul nu a putut fi salvat: ") + e.getClass().getSimpleName());
		}
	}

	@Override
	protected void onActivityResult(int code, int res, Intent data) {
		if (code != SAVE_PDF || res != RESULT_OK || data == null || data.getData() == null || pdfBytes == null) return;
		try {
			OutputStream out = getContentResolver().openOutputStream(data.getData());
			out.write(pdfBytes);
			out.close();
			saved(data.getData());
		} catch (Exception e) {
			Log.e(this, "FERMIER", "PDF", e);
			toast(Tr.t("The PDF could not be saved: ", "PDF-ul nu a putut fi salvat: ") + e.getClass().getSimpleName());
		}
	}

	private void saved(final Uri u) {
		AlertDialog.Builder d = dialog();
		d.setTitle(Tr.t("PDF saved", "PDF salvat"));
		d.setMessage(Tr.t("In Downloads: ", "În Descărcări: ") + pdfName);
		d.setPositiveButton(Tr.t("Open", "Deschide"), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface di, int w) {
				try {
					startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
				} catch (Exception e) {
					toast(Tr.t("No app on the phone opens PDF files.", "Nicio aplicație din telefon nu deschide fișiere PDF."));
				}
			}
		});
		d.setNegativeButton("OK", null);
		d.show();
	}

	private byte[] makePdf(Parcels.Parcel p, List<Farm.Day> days, double[] s) throws Exception {
		PdfDocument doc = new PdfDocument();
		int W = 595, H = 842, M = 48;
		Paint t = new Paint(Paint.ANTI_ALIAS_FLAG), b = new Paint(Paint.ANTI_ALIAS_FLAG), l = new Paint();
		t.setColor(0xFF000000);
		b.setColor(0xFF000000);
		b.setTypeface(Typeface.DEFAULT_BOLD);
		l.setColor(0xFF000000);
		l.setStrokeWidth(1);
		float[] cols = { M, M + 120, M + 190, M + 260, M + 350, M + 440 };
		String[] heads = { Tr.t("Date", "Data"), Tr.t("Max", "Max"), Tr.t("Min", "Min"), Tr.t("Rain", "Ploaie"), Tr.t("Max wind", "Vânt max"), Tr.t("Soil", "Sol") };
		int pageNo = 0, k = days.size() - 1;
		Calendar now = Calendar.getInstance();
		while (true) {
			pageNo++;
			PdfDocument.Page pg = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
			Canvas c = pg.getCanvas();
			float y = M;
			if (pageNo == 1) {
				b.setTextSize(20);
				c.drawText(Tr.t("Parcel statistics · ", "Statistică parcelă · ") + p.name, M, y + 14, b);
				y += 34;
				t.setTextSize(11);
				c.drawText(Tr.t("Coordinates: ", "Coordonate: ") + coords(p.lat, p.lon), M, y, t);
				y += 16;
				c.drawText(Tr.t("Period: ", "Perioada: ") + dateText(from) + " – " + dateText(to) + " (" + days.size() + Tr.t(" days)", " zile)"), M, y, t);
				y += 16;
				c.drawText(Tr.t("Made on ", "Generat la ") + dateText(Farm.date(now, 0)) + " " + time(now.getTimeInMillis()) + " · Meteo Alarm Android", M, y, t);
				y += 26;
				b.setTextSize(13);
				c.drawText(Tr.t("Summary", "Rezumat"), M, y, b);
				y += 18;
				t.setTextSize(12);
				String[] sum = { Tr.t("Lowest temperature: ", "Temperatura minimă: ") + deg(s[0]), Tr.t("Highest temperature: ", "Temperatura maximă: ") + deg(s[1]),
						Tr.t("Rain total: ", "Ploaie totală: ") + mm(s[2]) + " · " + Math.round(s[5]) + Tr.t(" days with rain (1 mm+)", " zile cu ploaie (1 mm+)"),
						Tr.t("Highest wind: ", "Vânt maxim: ") + kmh(s[3]) + (Double.isNaN(s[6]) ? "" : " · " + Tr.t("gusts up to ", "rafale până la ") + kmh(s[6])),
						Tr.t("Mean soil moisture (3–9 cm, estimated): ", "Umiditatea medie a solului (3–9 cm, estimată): ") + soil(s[4]) + " " + (Double.isNaN(s[4]) ? "" : soilWord(s[4])) };
				for (String x : sum) {
					c.drawText(x, M + 8, y, t);
					y += 17;
				}
				y += 14;
			}
			b.setTextSize(11);
			for (int i = 0; i < cols.length; i++) c.drawText(heads[i], cols[i], y, b);
			y += 6;
			c.drawLine(M, y, W - M, y, l);
			y += 15;
			t.setTextSize(11);
			while (k >= 0 && y < H - M - 40) {
				Farm.Day d = days.get(k--);
				String[] v = { dateText(d.date), deg(d.tmax), deg(d.tmin), mm(d.rain), kmh(d.wind), soil(d.soil) };
				for (int i = 0; i < cols.length; i++) c.drawText(v[i], cols[i], y, t);
				y += 16;
			}
			t.setTextSize(9);
			t.setColor(0xFF555555);
			c.drawText(Tr.t("Source: Open-Meteo (open-meteo.com), weather models.", "Sursa: Open-Meteo (open-meteo.com), modele meteo."), M, H - M - 6, t);
			c.drawText(Tr.t("Soil moisture and soil temperature are estimates, not measurements.", "Umiditatea și temperatura solului sunt estimări, nu măsurători."), M, H - M + 6, t);
			c.drawText(pageNo + "", W - M - 10, H - M + 20, t);
			t.setColor(0xFF000000);
			doc.finishPage(pg);
			if (k < 0) break;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		doc.writeTo(out);
		doc.close();
		return out.toByteArray();
	}
}
