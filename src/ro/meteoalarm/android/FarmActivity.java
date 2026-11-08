package ro.meteoalarm.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Context;
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
	/**
	 * Opened from the parcel's name on the main screen (v2.60): "parcel", "stats" or "map" for
	 * parcel ENTRY_ID; back from that page closes this window. Null: opened from ☰ › Parcele.
	 */
	private String entry;
	/** The parcels ticked for deleting (v2.60). */
	private final java.util.Set<Long> ticked = new java.util.HashSet<Long>();

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
		entry = getIntent().getStringExtra("open");
		long id = getIntent().getLongExtra("id", -1);
		Parcels.Parcel p = id >= 0 ? Parcels.get(this, id) : null;
		if (entry != null && p != null) {
			Log.i(this, "FERMIER", "parcela " + p.name + ": " + entry);
			curId = id;
			if (entry.equals("stats")) {
				Calendar now = Calendar.getInstance();
				from = Farm.date(now, -6);
				to = Farm.date(now, 0);
				showStats(id);
			} else if (entry.equals("map")) showMap(id, p.name, p.lat, p.lon, 17, "finish");
			else showParcel(id);
			return;
		}
		entry = null;
		Log.i(this, "FERMIER", "pagina Parcele deschisă (" + Parcels.all(this).size() + " parcele)");
		showList();
	}

	@Override
	public void onBackPressed() {
		if (screen.equals("map")) {
			stopLoc();
			if (mapBack.equals("finish")) finish();
			else if (mapBack.equals("parcel")) showParcel(curId);
			else showList();
		} else if (screen.equals("stats")) {
			if ("stats".equals(entry)) finish();
			else showParcel(curId);
		} else if (screen.equals("parcel")) {
			if ("parcel".equals(entry)) finish();
			else showList();
		} else if (screen.equals("delete") || screen.equals("edit")) showList();
		else super.onBackPressed();
	}

	/** Parcel ID picked: its main screen (v2.60). */
	private void select(long id) {
		setResult(RESULT_OK, new android.content.Intent().putExtra("select", id));
		finish();
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

	private boolean land() {
		return getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
	}

	@Override
	public void onConfigurationChanged(android.content.res.Configuration c) {
		super.onConfigurationChanged(c);
		// The list and the edit list change their layout with the phone's position (v2.80).
		if (screen.equals("list")) showList();
		else if (screen.equals("edit")) showEditList();
	}

	/** Views in rows of two in landscape (v2.80, Viorel), one under the other in portrait. */
	private void addGrid(LinearLayout col, List<View> views, int top, int gap) {
		if (!land()) {
			for (int k = 0; k < views.size(); k++) col.addView(views.get(k), wide(k == 0 ? top : gap));
			return;
		}
		for (int k = 0; k < views.size(); k += 2) col.addView(pair(views.get(k), k + 1 < views.size() ? views.get(k + 1) : null), wide(k == 0 ? top : gap));
	}

	/** The inner width of a parcel's card, in pixels: half the page in landscape. */
	private int cardInner() {
		if (screen.equals("list") && cardW > 0) return cardW;
		int w = getResources().getDisplayMetrics().widthPixels - px(32);
		if (land()) w = (w - px(12)) / 2;
		return w - px(28);
	}

	/** The biggest text size (sp, 15 to 26) at which every line of LINES fits WIDTH pixels. */
	private float fitSp(int width, String... lines) {
		Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
		pt.setTypeface(Typeface.DEFAULT);
		float scale = TextSize.get(this) * getResources().getDisplayMetrics().scaledDensity;
		for (float sp = 26; sp > 15; sp -= 0.5f) {
			pt.setTextSize(sp * scale);
			boolean ok = true;
			for (String l : lines) if (pt.measureText(l) > width) ok = false;
			if (ok) return sp;
		}
		return 15;
	}

	/** A small framed pencil (v2.80): drawn, so it looks the same on every phone. */
	private View pencil(final Parcels.Parcel p) {
		View v = new View(this) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			protected void onDraw(Canvas c) {
				float s = Math.min(getWidth(), getHeight()), m = s * 0.24f;
				pt.setColor(ink);
				pt.setStyle(Paint.Style.STROKE);
				pt.setStrokeWidth(dp * 1.5f);
				c.drawRoundRect(new android.graphics.RectF(dp, dp, s - dp, s - dp), dp * 8, dp * 8, pt);
				pt.setStyle(Paint.Style.FILL);
				android.graphics.Path path = new android.graphics.Path();
				float w = s * 0.09f, x0 = m, y0 = s - m, x1 = s - m, y1 = m;
				float tx = x0 + (x1 - x0) * 0.2f, ty = y0 + (y1 - y0) * 0.2f;
				float nx = w * 0.7071f, ny = w * 0.7071f;
				path.moveTo(x0, y0);
				path.lineTo(tx - nx, ty - ny);
				path.lineTo(x1 - nx, y1 - ny);
				path.lineTo(x1 + nx, y1 + ny);
				path.lineTo(tx + nx, ty + ny);
				path.close();
				c.drawPath(path, pt);
			}
		};
		v.setContentDescription(Tr.t("Edit", "Editare"));
		v.setOnClickListener(new View.OnClickListener() {
			public void onClick(View x) {
				edit(p);
			}
		});
		return v;
	}

	/** Parcel P's name and position, on the map page (v2.80: "Edit"); back goes to the list. */
	private void edit(Parcels.Parcel p) {
		Log.i(this, "FERMIER", "editare parcelă: " + p.name);
		showMap(p.id, p.name, p.lat, p.lon, 17, "list");
	}

	/**
	 * The name and, in landscape, the coordinates on the same line when there is room (v2.80,
	 * Viorel); else the coordinates under the name.
	 */
	private void nameLines(LinearLayout card, Parcels.Parcel p, String extra, View right) {
		LinearLayout top = new LinearLayout(this);
		top.setGravity(Gravity.TOP);
		LinearLayout left = column();
		TextView name = text(p.name, 19, true, ink);
		name.setMaxLines(2);
		name.setEllipsize(android.text.TextUtils.TruncateAt.END);
		String c = coords(p.lat, p.lon) + extra;
		TextView co = text(c, 13, false, dim);
		int room = cardInner() - (right != null ? px(40) : 0);
		boolean same = land() && name.getPaint().measureText(p.name) + px(14) + co.getPaint().measureText(c) <= room;
		if (same) {
			LinearLayout row = new LinearLayout(this);
			row.setGravity(Gravity.BOTTOM);
			row.addView(name);
			co.setPadding(px(14), 0, 0, px(2));
			row.addView(co);
			left.addView(row);
		} else {
			left.addView(name);
			left.addView(co);
		}
		top.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		if (right != null) {
			LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(px(30), px(30));
			rl.leftMargin = px(10);
			top.addView(right, rl);
		}
		card.addView(top);
	}

	/** Downloads parcel P's data now (off the main thread), then redraws the list if it is open. */
	private void fetchNow(final Parcels.Parcel p) {
		final Context ctx = getApplicationContext();
		new Thread(new Runnable() {
			public void run() {
				try {
					Parcels.fetch(ctx, p);
					Log.i(ctx, "FERMIER", "parcela " + p.name + " descărcată după salvare");
				} catch (Exception e) {
					Log.e(ctx, "FERMIER", "datele parcelei " + p.name + " au eșuat după salvare: " + e.getMessage());
					return;
				}
				runOnUiThread(new Runnable() {
					public void run() {
						if (isFinishing()) return;
						if (screen.equals("list")) showList();
						else if (screen.equals("edit")) showEditList();
					}
				});
			}
		}).start();
	}

	/** The list map's view, kept when the list is drawn again (NaN: fit it to the parcels). */
	private double listLat = Double.NaN, listLon, listZoom;
	/** The phone's position found with the list map's GPS button (NaN: none). */
	private double meLa = Double.NaN, meLo;
	private MapView listMap;
	/** The bubble shown while the GPS button looks for the position. */
	private View gpsBubble;
	/** The inner width of a parcel's card on the list (pixels), 0 before the list is built. */
	private int cardW;

	/** A smaller button for the list's fixed 2×2 block (v2.90): smaller on a phone. */
	private TextView smallButton(String label, View.OnClickListener l) {
		boolean tab = TextSize.tablet(this);
		TextView t = button(label, l);
		t.setTextSize((tab ? 16 : 13) * TextSize.get(this));
		t.setMinHeight(px(tab ? 48 : 38));
		t.setPadding(px(6), px(tab ? 10 : 6), px(6), px(tab ? 10 : 6));
		t.setSingleLine(true);
		t.setEllipsize(android.text.TextUtils.TruncateAt.END);
		return t;
	}

	/** A small square button floating over the map. */
	private View floatBtn(View v, View.OnClickListener l) {
		v.setBackground(frame(btn, 10));
		v.setOnClickListener(l);
		v.setElevation(px(3));
		return v;
	}

	/** The GPS button's crosshair (drawn, blue as the dot of the phone's position). */
	private View crosshair() {
		return new View(this) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			protected void onDraw(Canvas c) {
				float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) * 0.26f;
				pt.setColor(0xFF2F6BEA);
				pt.setStyle(Paint.Style.STROKE);
				pt.setStrokeWidth(dp * 2.2f);
				c.drawCircle(cx, cy, r, pt);
				c.drawLine(cx - r * 1.5f, cy, cx - r * 0.6f, cy, pt);
				c.drawLine(cx + r * 0.6f, cy, cx + r * 1.5f, cy, pt);
				c.drawLine(cx, cy - r * 1.5f, cx, cy - r * 0.6f, pt);
				c.drawLine(cx, cy + r * 0.6f, cx, cy + r * 1.5f, pt);
				pt.setStyle(Paint.Style.FILL);
				c.drawCircle(cx, cy, r * 0.4f, pt);
			}
		};
	}

	/**
	 * v2.90 (Viorel 2026-11-08): the map with every parcel (a pin and its name; a tap on the pin
	 * = a tap on its card), the back button and the title floating at its top right, the GPS button
	 * under them; landscape: the map on the left 2/3, on the right the 2×2 buttons (fixed) and the
	 * cards (scrolled); portrait: the map on top, under it the buttons and 2 cards (a phone) or 2½
	 * rows of 2 cards (a tablet).
	 */
	private void showList() {
		screen = "list";
		if (listMap != null && listMap.getWidth() > 0) {
			listLat = listMap.lat();
			listLon = listMap.lon();
			listZoom = listMap.zoom();
		}
		root.removeAllViews();
		final boolean tab = TextSize.tablet(this), land = land();
		final List<Parcels.Parcel> all = Parcels.sorted(this);
		LinearLayout body = new LinearLayout(this);
		body.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

		// --- The map and what floats over it.
		FrameLayout mapBox = new FrameLayout(this);
		final MapView m = new MapView(this);
		map = listMap = m;
		m.setDark(dark);
		m.clean();
		m.setTopPad(px(tab ? 130 : 120));
		List<MapView.Pin> pins = new java.util.ArrayList<MapView.Pin>();
		for (Parcels.Parcel p : all) pins.add(new MapView.Pin(p.id, p.lat, p.lon, p.name));
		m.setPins(pins, new MapView.PinTap() {
			public void tapped(long id) {
				Parcels.Parcel p = Parcels.get(FarmActivity.this, id);
				Log.i(FarmActivity.this, "FERMIER", "parcelă aleasă pe hartă: " + (p != null ? p.name : id));
				select(id);
			}
		});
		if (!Double.isNaN(listLat)) {
			m.setZoom(listZoom);
			m.center(listLat, listLon);
		} else if (all.isEmpty()) {
			Cities.City c = Cities.City.fromKey(Fetcher.prefs(this).getString("city", ""));
			m.setZoom(13);
			m.center(c != null ? c.lat : 44.2058, c != null ? c.lon : 27.3306);
		} else m.fitPins();
		if (!Double.isNaN(meLa)) m.setMe(meLa, meLo);
		mapBox.addView(m, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		int fb = px(tab ? 46 : 42), mg = px(12);
		TextView zin = text("＋", 22, true, ink), zout = text("−", 22, true, ink);
		zin.setGravity(Gravity.CENTER);
		zout.setGravity(Gravity.CENTER);
		FrameLayout.LayoutParams zl = new FrameLayout.LayoutParams(fb, fb, Gravity.TOP | Gravity.LEFT);
		zl.setMargins(mg, mg, 0, 0);
		mapBox.addView(floatBtn(zin, new View.OnClickListener() {
			public void onClick(View v) {
				m.zoomBy(1);
			}
		}), zl);
		FrameLayout.LayoutParams zl2 = new FrameLayout.LayoutParams(fb, fb, Gravity.TOP | Gravity.LEFT);
		zl2.setMargins(mg, mg + fb + px(8), 0, 0);
		mapBox.addView(floatBtn(zout, new View.OnClickListener() {
			public void onClick(View v) {
				m.zoomBy(-1);
			}
		}), zl2);
		// The back button and the title: a badge at the map's top right edge (Viorel 2026-11-08).
		LinearLayout badge = new LinearLayout(this);
		badge.setGravity(Gravity.CENTER_VERTICAL);
		badge.setBackground(frame(btn, 12));
		badge.setElevation(px(3));
		TextView back = text("‹", 24, true, ink);
		back.setGravity(Gravity.CENTER);
		back.setPadding(0, 0, 0, px(4));
		back.setBackground(frame(btn, 12));
		back.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onBackPressed();
			}
		});
		badge.addView(back, new LinearLayout.LayoutParams(fb + px(2), fb + px(2)));
		TextView title = text(Tr.t("Parcels", "Parcele"), tab ? 22 : 20, true, ink);
		title.setPadding(px(12), 0, px(14), 0);
		title.setSingleLine(true);
		badge.addView(title);
		FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
		bl.setMargins(0, mg, mg, 0);
		mapBox.addView(badge, bl);
		// The GPS button under it: the bubble says what happens, the blue dot shows the place.
		FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(fb + px(4), fb + px(4), Gravity.TOP | Gravity.RIGHT);
		gl.setMargins(0, mg + fb + px(14), mg, 0);
		View gps = floatBtn(crosshair(), new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(FarmActivity.this, "FERMIER", "lista parcelelor: localizare precisă (GPS)");
				if (gpsBubble != null) gpsBubble.setVisibility(View.VISIBLE);
				afterLoc = new Runnable() {
					public void run() {
						findMe(false);
					}
				};
				if (!Locator.allowed(FarmActivity.this))
					requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, ASK_LOC);
				else findMe(false);
			}
		});
		gps.setContentDescription(Tr.t("Precise location (GPS)", "Localizare precisă (GPS)"));
		mapBox.addView(gps, gl);
		LinearLayout bub = column();
		bub.setBackground(frame(card, 12));
		bub.setPadding(px(14), px(10), px(14), px(12));
		bub.setElevation(px(5));
		bub.addView(text(Tr.t("Precise location (GPS)", "Localizare precisă (GPS)"), 16, true, ink));
		bub.addView(text(Tr.t("Looking for your exact place by GPS… Stay outside; the blue dot shows where you are.",
				"Caut locația ta exactă prin GPS… Stai afară; punctul albastru arată unde ești."), 14, false, ink), wide(4));
		bub.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				v.setVisibility(View.GONE);
			}
		});
		bub.setVisibility(locListener != null ? View.VISIBLE : View.GONE);
		gpsBubble = bub;
		FrameLayout.LayoutParams ql = new FrameLayout.LayoutParams(px(tab ? 320 : 250), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
		ql.setMargins(0, mg + 2 * fb + px(26), mg, 0);
		mapBox.addView(bub, ql);

		// --- The panel: the fixed buttons, then the scrolled cards.
		LinearLayout panel = column();
		int pad = px(12);
		panel.setPadding(pad, px(land ? 12 : 10), pad, land ? px(8) : px(6));
		List<View> buttons = new java.util.ArrayList<View>();
		buttons.add(smallButton("＋  " + Tr.t("Save GPS place", "Salvare loc. GPS"), new View.OnClickListener() {
			public void onClick(View v) {
				saveHere();
			}
		}));
		buttons.add(smallButton(Tr.t("Choose on the map", "Alege pe hartă"), new View.OnClickListener() {
			public void onClick(View v) {
				showMap(-1, "", m.lat(), m.lon(), Math.max(13, m.zoom()), "list");
			}
		}));
		if (!all.isEmpty()) {
			buttons.add(smallButton(Tr.t("Edit", "Editare"), new View.OnClickListener() {
				public void onClick(View v) {
					showEditList();
				}
			}));
			buttons.add(smallButton(Tr.t("Delete parcels", "Șterge parcele"), new View.OnClickListener() {
				public void onClick(View v) {
					ticked.clear();
					showDelete();
				}
			}));
		}
		int bgap = px(tab ? 10 : 8);
		for (int k = 0; k < buttons.size(); k += 2) {
			LinearLayout row = pair(buttons.get(k), k + 1 < buttons.size() ? buttons.get(k + 1) : null);
			panel.addView(row, wide(k == 0 ? 0 : bgap));
		}
		status = text("", 14, false, dim);
		status.setVisibility(View.GONE);
		panel.addView(status, wide(6));
		View sep = new View(this);
		sep.setBackgroundColor(ink);
		LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(2));
		sl.topMargin = px(tab ? 12 : 9);
		panel.addView(sep, sl);
		final ScrollView sv = new ScrollView(this);
		sv.setVerticalScrollBarEnabled(true);
		LinearLayout list = column();
		list.setPadding(0, px(tab ? 12 : 9), 0, px(8));
		sv.addView(list);

		// The width of the panel, so the cards' text fits it.
		int sw = getResources().getDisplayMetrics().widthPixels;
		int panelW = land ? sw / 3 : sw;
		int cols = tab ? 2 : 1, gap = px(tab ? 10 : 10);
		int colW = (panelW - 2 * pad - (cols - 1) * gap) / cols;
		cardW = colW - px(28);
		final boolean stacked = tab && land;
		List<View> cards = new java.util.ArrayList<View>();
		for (Parcels.Parcel p : all) cards.add(listCard(p, stacked));
		final List<View> rows = new java.util.ArrayList<View>();
		if (all.isEmpty())
			list.addView(text(Tr.t("No parcel saved yet. Stand on the parcel and tap “Save GPS place”, or choose it on the map.",
					"Nicio parcelă salvată încă. Stai pe parcelă și atinge „Salvare loc. GPS”, sau alege-o pe hartă."), 16, false, ink));
		for (int k = 0; k < cards.size(); k += cols) {
			View row = cols == 1 ? cards.get(k) : pairGap(cards.get(k), k + 1 < cards.size() ? cards.get(k + 1) : null, gap);
			list.addView(row, wide(k == 0 ? 0 : gap));
			rows.add(row);
		}
		LinearLayout.LayoutParams svl;
		if (land || rows.isEmpty()) svl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, land ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT, land ? 1 : 0);
		else {
			// Portrait: the cards' room is 2 cards (a phone) or 2½ rows (a tablet); the map the rest.
			int h = px(9);
			int show = tab ? 3 : 2;
			for (int k = 0; k < Math.min(show, rows.size()); k++) {
				View r = rows.get(k);
				r.measure(View.MeasureSpec.makeMeasureSpec(panelW - 2 * pad, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
				int rh = r.getMeasuredHeight();
				h += tab && k == 2 ? rh / 2 : rh + (k > 0 ? gap : 0);
				if (tab && k == 2) h += gap;
			}
			svl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
		}
		panel.addView(sv, svl);
		if (stacked && !rows.isEmpty()) {
			// Tablet landscape: rows of 2 tall cards, 2 rows and the top half of the 3rd (Viorel).
			sv.post(new Runnable() {
				public void run() {
					int avail = sv.getHeight() - px(9);
					if (avail <= 0) return;
					int rh = Math.round((avail - 2 * px(10)) / 2.55f);
					for (View r : rows) {
						r.measure(View.MeasureSpec.makeMeasureSpec(sv.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
						ViewGroup.LayoutParams lp = r.getLayoutParams();
						lp.height = Math.max(rh, r.getMeasuredHeight());
						r.setLayoutParams(lp);
					}
				}
			});
		}
		panel.setBackgroundColor(page);
		if (land) {
			body.addView(mapBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2));
			View line = new View(this);
			line.setBackgroundColor(ink);
			body.addView(line, new LinearLayout.LayoutParams(px(2), ViewGroup.LayoutParams.MATCH_PARENT));
			body.addView(panel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
		} else {
			body.addView(mapBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
			View line = new View(this);
			line.setBackgroundColor(ink);
			body.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(2)));
			body.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		root.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	/** Two views side by side with GAP between them (the tablet's 2 columns of cards). */
	private LinearLayout pairGap(View a, View b, int gap) {
		LinearLayout row = new LinearLayout(this);
		LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
		la.rightMargin = gap / 2;
		row.addView(a, la);
		LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
		lb.leftMargin = gap - gap / 2;
		row.addView(b != null ? b : new View(this), lb);
		return row;
	}

	/**
	 * A parcel's card on the list: the name (2 lines at most), the coordinates, the weather now in
	 * 2 lines ("temp · wind" / "rain · soil"), or one value per line when STACKED (tablet landscape),
	 * as big as the card allows; a tap shows the parcel on the main screen, the pencil edits it.
	 */
	private View listCard(final Parcels.Parcel p, boolean stacked) {
		LinearLayout card = column();
		card.setBackground(frame(this.card, 14));
		card.setPadding(px(14), px(10), px(14), px(12));
		nameLines(card, p, p.alerts ? " · " + Tr.t("alerts on", "alerte pornite") : "", pencil(p));
		long f = Parcels.fetched(this, p.id);
		Farm.Data d = Parcels.data(this, p.id);
		if (d != null) {
			int i = d.nowIndex(Calendar.getInstance());
			if (i >= 0) {
				String t = Tr.t("temp: ", "temp: ") + deg(d.temp[i]), w = Tr.t("wind ", "vânt ") + dir8(d.dir[i]) + " " + kmh(d.wind[i]);
				String r = Tr.t("rain ", "ploaie ") + pct(d.prob[i]), s = Tr.t("soil ", "sol ") + soil(d.soilM[i]);
				String[] lines = stacked ? new String[] { t, w, r, s } : new String[] { t + " · " + w, r + " · " + s };
				float sp = fitSp(cardInner(), lines) / TextSize.get(this);
				for (int k = 0; k < lines.length; k++) {
					TextView tv = text(lines[k], sp, false, ink);
					tv.setSingleLine(true);
					card.addView(tv, wide(k == 0 ? 4 : 0));
				}
			}
			if (f > 0) {
				if (stacked) {
					View fill = new View(this);
					card.addView(fill, new LinearLayout.LayoutParams(1, 0, 1));
				}
				card.addView(text(Tr.t("updated ", "actualizat ") + time(f), 12, false, dim), wide(2));
			}
		}
		card.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(FarmActivity.this, "FERMIER", "parcelă aleasă pentru ecranul principal: " + p.name);
				select(p.id);
			}
		});
		return card;
	}

	/** "Edit" (v2.80): the parcels; a tap opens one for editing its name and its place. */
	private void showEditList() {
		screen = "edit";
		LinearLayout col = pageOf(Tr.t("Edit", "Editare"));
		col.addView(text(Tr.t("Tap the parcel to edit: its name and the pin on the map.", "Atinge parcela de editat: numele și acul pe hartă."), 14, false, dim), wide(8));
		List<View> cards = new java.util.ArrayList<View>();
		for (final Parcels.Parcel p : Parcels.sorted(this)) {
			LinearLayout card = column();
			card.setBackground(frame(this.card, 14));
			card.setPadding(px(14), px(10), px(14), px(12));
			nameLines(card, p, "", null);
			card.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					edit(p);
				}
			});
			cards.add(card);
		}
		addGrid(col, cards, 12, 12);
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
			else {
				if (gpsBubble != null) gpsBubble.setVisibility(View.GONE);
				toast(Tr.t("Without location the place can only be chosen on the map.", "Fără localizare, locul se poate alege doar pe hartă."));
			}
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
		if (isNew && status != null) {
			status.setText(Tr.t("Looking for the position (GPS)… stay outside, on the parcel.", "Caut poziția (GPS)… stai afară, pe parcelă."));
			status.setVisibility(View.VISIBLE);
		}
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
			if (gpsBubble != null) gpsBubble.setVisibility(View.GONE);
			if (l != null && map == listMap) {
				meLa = l.getLatitude();
				meLo = l.getLongitude();
			}
			if (l != null && map != null) {
				map.setMe(l.getLatitude(), l.getLongitude());
				map.center(l.getLatitude(), l.getLongitude());
			} else if (l == null) toast(Tr.t("The position was not found.", "Poziția nu a fost găsită."));
			return;
		}
		if (status != null) {
			status.setText("");
			if (screen.equals("list")) status.setVisibility(View.GONE);
		}
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
		col.addView(header(id < 0 ? Tr.t("New parcel", "Parcelă nouă") : Tr.t("Edit", "Editare")));
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
				boolean moved = id < 0 || Math.abs(p.lat - map.lat()) > 1e-6 || Math.abs(p.lon - map.lon()) > 1e-6;
				p.name = n;
				p.lat = map.lat();
				p.lon = map.lon();
				Parcels.put(FarmActivity.this, p);
				Log.i(FarmActivity.this, "FERMIER", (id >= 0 ? (moved ? "parcelă editată și mutată: " : "parcelă editată: ") : "parcelă salvată: ") + n + " " + coords(p.lat, p.lon));
				stopLoc();
				// Moved: the old data were for another place (the main screen downloads them again too).
				if (id >= 0 && moved) Fetcher.prefs(FarmActivity.this).edit().putLong("pf_" + p.id, 0).putLong("pmf_" + p.id, 0).apply();
				// v2.81 (Viorel): a new or moved parcel gets its weather at once, and its card fills in
				// on the list without leaving the page.
				if (moved) fetchNow(p);
				// v2.90: the list map shows all the parcels again, the new or moved one among them.
				if (moved) listLat = Double.NaN;
				if (mapBack.equals("finish")) finish();
				else if (id >= 0 && mapBack.equals("parcel")) showParcel(p.id);
				else if (id >= 0) {
					toast(Tr.t("Saved: ", "Salvată: ") + n);
					showList();
				}
				else {
					toast(Tr.t("Saved: ", "Salvată: ") + n);
					showList();
				}
			}
		}), wide(8));
		root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	// --- Deleting parcels (v2.60) ----------------------------------------------------------------

	/**
	 * The parcels with a tick box each; "Delete" asks once more (Viorel 2026-11-07: if the farmer
	 * says no, the ticks stay and can be changed).
	 */
	private void showDelete() {
		screen = "delete";
		LinearLayout col = pageOf(Tr.t("Delete parcels", "Șterge parcele"));
		col.addView(text(Tr.t("Tick the parcels to delete, then tap Delete.", "Bifează parcelele de șters, apoi atinge Șterge."), 14, false, dim), wide(8));
		final TextView go = button("", null);
		final List<Parcels.Parcel> all = Parcels.sorted(this);
		final Runnable label = new Runnable() {
			public void run() {
				go.setText(Tr.t("Delete", "Șterge") + (ticked.isEmpty() ? "" : " (" + ticked.size() + ")"));
				go.setAlpha(ticked.isEmpty() ? 0.4f : 1f);
			}
		};
		for (final Parcels.Parcel p : all) {
			final android.widget.CheckBox cb = new android.widget.CheckBox(this);
			cb.setText(p.name);
			cb.setTextColor(ink);
			cb.setTextSize(18 * TextSize.get(this));
			cb.setChecked(ticked.contains(p.id));
			if (Build.VERSION.SDK_INT >= 21) cb.setButtonTintList(android.content.res.ColorStateList.valueOf(ink));
			cb.setPadding(px(8), px(10), px(8), px(10));
			cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
				public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
					if (on) ticked.add(p.id);
					else ticked.remove(p.id);
					label.run();
				}
			});
			col.addView(cb, wide(4));
		}
		go.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (ticked.isEmpty()) return;
				StringBuilder names = new StringBuilder();
				for (Parcels.Parcel p : all) if (ticked.contains(p.id)) names.append(names.length() > 0 ? ", " : "").append(p.name);
				AlertDialog.Builder d = dialog();
				d.setTitle(Tr.t("Are you sure?", "Ești sigur?"));
				d.setMessage(Tr.t("These parcels and their statistics are deleted from the phone: ", "Aceste parcele și statistica lor se șterg din telefon: ") + names);
				d.setPositiveButton(Tr.t("Yes, delete", "Da, șterge"), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface di, int w) {
						for (Long id : ticked) {
							Parcels.delete(FarmActivity.this, id);
						}
						Log.i(FarmActivity.this, "FERMIER", "parcele șterse: " + names);
						toast(Tr.t("Deleted: ", "Șterse: ") + ticked.size());
						ticked.clear();
						showList();
					}
				});
				// No: back to the ticks, as they were.
				d.setNegativeButton(Tr.t("No", "Nu"), null);
				d.show();
			}
		});
		label.run();
		col.addView(go, wide(18));
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
						if (entry != null) finish();
						else showList();
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
