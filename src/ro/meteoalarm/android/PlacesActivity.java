package ro.meteoalarm.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Texts;
import ro.meteoalarm.android.core.Theme;
import ro.meteoalarm.android.core.Tr;
import ro.meteoalarm.android.core.Warnings;
import ro.meteoalarm.android.core.Weather;
import ro.meteoalarm.android.core.WxIcon;

/**
 * v2.150 (Viorel 2026-11-24): the towns that have a main screen, opened from the location button.
 * The list is on the theme's page. The first town is the first screen (it follows the GPS while
 * automatic location is on, and the widgets and notices follow it); every other town gets a main
 * screen of its own, in the list's order, reached by swiping. Three buttons: the magnifier (a map
 * with the search at the bottom; a place found shows its weather with Details and Add), select (tick
 * one or more towns and delete them; the last town can never go) and reorder (press the move sign
 * at the right of a town and drag it up or down).
 */
public final class PlacesActivity extends Activity {
	private static final String UA = "MeteoAlarmAndroid/2.150 (+https://github.com/viorelrb-alt/meteo-alarm-android)";

	private boolean dark, autoLoc, changedFirst;
	private int page, ink, dim, accent, pri, priInk, cardC, edge, selC, warnY, warnO, warnR, info;
	private float dp, ts;
	private SharedPreferences prefs;
	private Cities cities;
	private final Handler ui = new Handler(Looper.getMainLooper());
	private FrameLayout root;
	/** The towns, the first town first. */
	private final List<Cities.City> list = new ArrayList<Cities.City>();
	private final Set<String> dropped = new HashSet<String>();
	/** "list", "select", "sort", "map". */
	private String mode = "list";
	private final Set<Integer> ticked = new HashSet<Integer>();
	private LinearLayout rows;
	private String highlight;
	private Texts texts;

	@Override
	protected void onCreate(Bundle b) {
		dark = getIntent().getBooleanExtra("dark", false);
		prefs = Fetcher.prefs(this);
		Theme.pal = prefs.getInt("palette", 0);
		WxIcon.set = Fetcher.iconTheme(prefs);
		setTheme(dark ? android.R.style.Theme_DeviceDefault_NoActionBar : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
		super.onCreate(b);
		Lang.apply(this);
		texts = new Texts(Tr.lang());
		dp = getResources().getDisplayMetrics().density;
		ts = TextSize.get(this);
		page = Theme.page(dark);
		ink = Theme.ink(dark);
		dim = Theme.dim(dark);
		accent = Theme.accent(dark);
		// The filled button: the theme's pastel with dark text (≥ 6:1).
		pri = Theme.pastel(dark); // v2.150 (Viorel): the menu buttons are pastel in the theme's hue
		priInk = Theme.onPastel(dark);
		cardC = dark ? mix(page, 0xFFFFFFFF, 0.05f) : Theme.card(false);
		edge = mix(page, ink, dark ? 0.16f : 0.14f);
		selC = mix(page, accent, dark ? 0.16f : 0.12f);
		int[] col = Theme.colors(dark);
		warnY = col[11];
		warnO = col[12];
		warnR = col[13];
		info = dark ? 0xFF8CC4F0 : 0xFF2F6690;
		autoLoc = prefs.getBoolean("autoLoc", true);
		cities = loadCities();
		Cities.City first = Cities.City.fromKey(prefs.getString("city", ""));
		if (first != null) list.add(first);
		for (String k : prefs.getString("screens", "").split("\n")) {
			Cities.City c = Cities.City.fromKey(k);
			if (c != null && !has(c)) list.add(c);
		}
		getWindow().getDecorView().setBackgroundColor(page);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		if (Build.VERSION.SDK_INT >= 21) {
			getWindow().setStatusBarColor(page);
			getWindow().setNavigationBarColor(page);
		}
		if (Build.VERSION.SDK_INT >= 23 && !dark) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
		root = new FrameLayout(this);
		root.setBackgroundColor(page);
		root.setFitsSystemWindows(true);
		setContentView(root);
		Log.i(this, "LOC", "lista localităților: " + list.size());
		if (getIntent().getBooleanExtra("search", false)) showMap();
		else showList();
	}

	private Cities loadCities() {
		Cities c;
		try {
			c = new Cities(readAll(getAssets().open("cities.txt")));
		} catch (Exception e) {
			c = new Cities("");
		}
		try {
			c.addWorld(readAll(getAssets().open("world.txt")));
			c.addUS(readAll(getAssets().open("us.txt")));
		} catch (Exception e) {
			// the Romanian list alone
		}
		return c;
	}

	private boolean has(Cities.City c) {
		for (Cities.City o : list) if (o.same(c)) return true;
		return false;
	}

	// --- saving -------------------------------------------------------------------------------

	/** The list kept: the first town as "city", the others as "screens"; their files when taken out. */
	private void save() {
		if (list.isEmpty()) return;
		StringBuilder sb = new StringBuilder();
		for (int k = 1; k < list.size(); k++) sb.append(sb.length() > 0 ? "\n" : "").append(list.get(k).key());
		SharedPreferences.Editor e = prefs.edit().putString("screens", sb.toString());
		String was = prefs.getString("city", "");
		if (!list.get(0).key().equals(was)) {
			e.putString("city", list.get(0).key());
			// Another first town: the GPS no longer replaces it (as a town picked from the list).
			e.putBoolean("autoLoc", false);
			autoLoc = false;
			changedFirst = true;
			Log.i(this, "LOC", "prima localitate: " + list.get(0).label() + " (localizarea automată oprită)");
		}
		e.apply();
		for (String k : dropped) {
			Cities.City c = Cities.City.fromKey(k);
			if (c != null && !has(c)) Fetcher.dropPlace(this, Fetcher.placeId(c));
		}
		dropped.clear();
		Intent r = new Intent().putExtra("firstChanged", changedFirst);
		setResult(RESULT_OK, r);
	}

	private void finishShow(Cities.City c) {
		save();
		Intent r = new Intent().putExtra("firstChanged", changedFirst).putExtra("show", c == null ? "" : c.key());
		setResult(RESULT_OK, r);
		finish();
	}

	@Override
	public void onBackPressed() {
		if (mode.equals("select") || mode.equals("sort")) {
			ticked.clear();
			mode = "list";
			showList();
			return;
		}
		if (mode.equals("map") && !list.isEmpty()) {
			hideKeys();
			mode = "list";
			showList();
			return;
		}
		save();
		super.onBackPressed();
	}

	// --- the list -----------------------------------------------------------------------------

	private void showList() {
		root.removeAllViews();
		LinearLayout col = new LinearLayout(this);
		col.setOrientation(LinearLayout.VERTICAL);
		root.addView(col, new FrameLayout.LayoutParams(-1, -1));
		LinearLayout hdr = new LinearLayout(this);
		hdr.setGravity(Gravity.CENTER_VERTICAL);
		hdr.setPadding(px(6), px(4), px(6), px(4));
		col.addView(hdr, new LinearLayout.LayoutParams(-1, px(60)));
		boolean sel = mode.equals("select"), sort = mode.equals("sort");
		if (sel) {
			hdr.addView(iconBtn("x", Tr.t("Cancel", "Anulează"), new Runnable() {
				public void run() {
					onBackPressed();
				}
			}));
			hdr.addView(title(ticked.size() == 0 ? Tr.t("Select towns", "Selectează localități") : ticked.size() + " " + Tr.t("selected", "selectate")), new LinearLayout.LayoutParams(0, -2, 1));
			final boolean all = ticked.size() == list.size();
			TextView a = pillText((all ? "☑ " : "☐ ") + Tr.t("All", "Toate"), false);
			a.setContentDescription(Tr.t("Select all", "Selectează toate") + (all ? ", " + Tr.t("ticked", "bifat") : ""));
			a.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (all) ticked.clear();
					else for (int k = 0; k < list.size(); k++) ticked.add(k);
					showList();
				}
			});
			hdr.addView(a);
		} else if (sort) {
			hdr.addView(iconBtn("back", Tr.t("Back", "Înapoi"), new Runnable() {
				public void run() {
					onBackPressed();
				}
			}));
			hdr.addView(title(Tr.t("Reorder", "Reordonează")), new LinearLayout.LayoutParams(0, -2, 1));
			hdr.addView(iconBtn("check", Tr.t("Done", "Gata"), new Runnable() {
				public void run() {
					save();
					mode = "list";
					showList();
				}
			}));
		} else {
			hdr.addView(iconBtn("back", Tr.t("Back", "Înapoi"), new Runnable() {
				public void run() {
					onBackPressed();
				}
			}));
			hdr.addView(title(Tr.t("Towns", "Localități")), new LinearLayout.LayoutParams(0, -2, 1));
			hdr.addView(iconBtn("search", Tr.t("Search a town on the map", "Caută o localitate pe hartă"), new Runnable() {
				public void run() {
					showMap();
				}
			}));
			hdr.addView(iconBtn("select", Tr.t("Select towns to delete", "Selectează localități pentru ștergere"), new Runnable() {
				public void run() {
					mode = "select";
					ticked.clear();
					showList();
				}
			}));
			View so = iconBtn("sort", Tr.t("Reorder the towns", "Reordonează localitățile"), new Runnable() {
				public void run() {
					mode = "sort";
					showList();
				}
			});
			so.setEnabled(list.size() > 1);
			so.setAlpha(list.size() > 1 ? 1f : 0.38f);
			hdr.addView(so);
		}
		final ScrollView sv = new ScrollView(this);
		col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
		LinearLayout inner = new LinearLayout(this);
		inner.setOrientation(LinearLayout.VERTICAL);
		inner.setPadding(px(16), px(4), px(16), px(16));
		sv.addView(inner);
		if (sort) inner.addView(hint("✋", Tr.t("Press the move sign at the right and drag up or down. The number is the screen the town will have.",
				"Apasă pe semnul de mutare din dreapta și trage în sus sau în jos. Numărul este ecranul pe care îl va avea localitatea.")), lp(-1, -2, 0, 12));
		rows = new LinearLayout(this);
		rows.setOrientation(LinearLayout.VERTICAL);
		inner.addView(rows);
		for (int k = 0; k < list.size(); k++) rows.addView(new Row(k), lp(-1, -2, 0, 12));
		if (mode.equals("list")) {
			inner.addView(small(Tr.t("The order of the list is the order of the main screens: the first town is the first screen (the widgets and notices follow it). Swipe left or right over the weather now to go from one to another.",
					"Ordinea din listă este ordinea ecranelor principale: prima localitate e primul ecran (widget-urile și notificările o urmează). Glisează stânga-dreapta peste vremea de acum ca să treci de la una la alta."), 14), lp(-1, -2, 4, 8));
		} else if (sel) {
			inner.addView(small(Tr.t("Tap a town to tick it. The screens of the deleted towns go; the others keep their order.",
					"Atinge o localitate ca s-o bifezi. Ecranele celor șterse dispar; celelalte își păstrează ordinea."), 14), lp(-1, -2, 4, 8));
		}
		if (sel) {
			LinearLayout bottom = new LinearLayout(this);
			bottom.setOrientation(LinearLayout.VERTICAL);
			bottom.setPadding(px(16), px(8), px(16), px(16));
			col.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
			boolean allSel = ticked.size() >= list.size() && list.size() > 0;
			if (allSel) bottom.addView(hint("ⓘ", Tr.t("The last town cannot be deleted. Leave at least one unticked.", "Ultima localitate nu se poate șterge. Lasă cel puțin una nebifată.")), lp(-1, -2, 0, 10));
			TextView del = bigButton("🗑  " + Tr.t("Delete", "Șterge") + " (" + ticked.size() + ")", true);
			boolean ok = ticked.size() > 0 && !allSel;
			del.setEnabled(ok);
			del.setAlpha(ok ? 1f : 0.4f);
			if (!ok) del.setContentDescription(Tr.t("Delete, unavailable", "Șterge, indisponibil") + (allSel ? ". " + Tr.t("The last town cannot be deleted.", "Ultima localitate nu se poate șterge.") : ""));
			del.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					deleteTicked();
				}
			});
			bottom.addView(del, new LinearLayout.LayoutParams(-1, px(52)));
		}
		if (highlight != null) {
			final String h = highlight;
			highlight = null;
			ui.postDelayed(new Runnable() {
				public void run() {
					sv.fullScroll(View.FOCUS_DOWN);
				}
			}, 50);
			toast(h);
		}
	}

	private void deleteTicked() {
		if (ticked.isEmpty() || ticked.size() >= list.size()) return;
		List<Cities.City> keep = new ArrayList<Cities.City>();
		StringBuilder names = new StringBuilder();
		for (int k = 0; k < list.size(); k++) {
			if (ticked.contains(k)) {
				dropped.add(list.get(k).key());
				names.append(names.length() > 0 ? ", " : "").append(list.get(k).name);
			} else keep.add(list.get(k));
		}
		list.clear();
		list.addAll(keep);
		ticked.clear();
		Log.i(this, "LOC", "șterse din listă: " + names);
		save();
		mode = "list";
		showList();
		toast(Tr.t("Deleted: ", "Șterse: ") + names);
	}

	/** Moves the town at FROM to TO (the rows follow) and keeps the list. */
	private void move(int from, int to) {
		if (from == to || to < 0 || to >= list.size()) return;
		Cities.City c = list.remove(from);
		list.add(to, c);
		View v = rows.getChildAt(from);
		rows.removeViewAt(from);
		rows.addView(v, to);
		for (int k = 0; k < rows.getChildCount(); k++) ((Row) rows.getChildAt(k)).idx = k;
		rows.invalidate();
		for (int k = 0; k < rows.getChildCount(); k++) rows.getChildAt(k).invalidate();
	}

	/** What a row shows of a town: from what is saved on the phone. */
	private static final class Look {
		String icon = "", temp = "", minmax = "", desc = "", when = "", warn = "";
		int level;
	}

	private Look look(int idx) {
		Look o = new Look();
		Cities.City c = list.get(idx);
		try {
			String f, u;
			long at;
			List<Warnings.Warn> ws;
			if (idx == 0 && c.key().equals(prefs.getString("cacheCity", ""))) {
				f = Fetcher.read(this, "forecast.json");
				u = Fetcher.read(this, "uv.json");
				at = prefs.getLong("fetched", 0);
				ws = Fetcher.warnings(this, c);
			} else {
				long id = Fetcher.placeId(c);
				f = Fetcher.read(this, Fetcher.parcelFile(id, "forecast.json"));
				u = Fetcher.read(this, Fetcher.parcelFile(id, "uv.json"));
				at = prefs.getLong("pmf_" + id, 0);
				ws = Fetcher.parcelWarnings(this, id, c);
			}
			if (f != null) {
				Weather w = Weather.parse(f);
				if (u != null) Weather.addExtra(w, u);
				String[] d = texts.describe(w.code, w.isDay);
				o.icon = d[0];
				o.desc = d.length > 1 ? d[1] : "";
				boolean imp = Region.imperial(this, c);
				o.temp = deg(w.temp, imp) + "°";
				if (!Double.isNaN(w.tmax) && !Double.isNaN(w.tmin)) o.minmax = deg(w.tmax, imp) + "° / " + deg(w.tmin, imp) + "°";
			}
			if (at > 0) o.when = Tr.t("updated ", "actualizat ") + new java.text.SimpleDateFormat(Region.clk12(this, c) ? "d MMM, h:mm a" : "d MMM, HH:mm", new java.util.Locale(Tr.lang().equals("us") ? "en" : Tr.lang())).format(new java.util.Date(at));
			if (ws != null && !ws.isEmpty()) {
				Warnings.Warn w = ws.get(0);
				for (Warnings.Warn x : ws) if (x.level > w.level) w = x;
				o.level = w.level;
				String code = w.level >= 3 ? Tr.t("RED CODE", "COD ROȘU") : w.level == 2 ? Tr.t("ORANGE CODE", "COD PORTOCALIU") : Tr.t("YELLOW CODE", "COD GALBEN");
				if (w.nws) code = w.type;
				o.warn = (code + (w.nws || w.type == null || w.type.length() == 0 ? "" : " · " + w.type)).toUpperCase();
			}
		} catch (Exception e) {
			// nothing saved yet: the name alone
		}
		return o;
	}

	private static long deg(double t, boolean imp) {
		return Math.round(imp ? t * 9 / 5 + 32 : t);
	}

	/** One town as a card: tap (list: its screen; select: tick), the move sign (reorder). */
	private final class Row extends View {
		int idx;
		final Look o;
		private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final AndroidGfx g = new AndroidGfx(getAssets());
		private float dragY, startTop;
		private boolean dragging;

		Row(int k) {
			super(PlacesActivity.this);
			idx = k;
			o = look(k);
			setClickable(true);
			setFocusable(true);
			setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					if (mode.equals("select")) {
						if (!ticked.remove(idx)) ticked.add(idx);
						showList();
					} else if (mode.equals("list")) {
						Log.i(PlacesActivity.this, "LOC", "din listă: ecranul " + (idx + 1) + " (" + list.get(idx).label() + ")");
						finishShow(list.get(idx));
					}
				}
			});
			setOnLongClickListener(new View.OnLongClickListener() {
				public boolean onLongClick(View v) {
					if (!mode.equals("list")) return false;
					mode = "select";
					ticked.clear();
					ticked.add(idx);
					showList();
					return true;
				}
			});
			if (Build.VERSION.SDK_INT >= 21) setAccessibilityDelegate(new View.AccessibilityDelegate() {
				public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo n) {
					super.onInitializeAccessibilityNodeInfo(host, n);
					n.setContentDescription(speak());
					if (mode.equals("sort")) {
						if (idx > 0) n.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.widget_root + 1, Tr.t("Move up", "Mută mai sus")));
						if (idx < list.size() - 1) n.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.widget_root + 2, Tr.t("Move down", "Mută mai jos")));
					}
					if (mode.equals("select")) {
						n.setCheckable(true);
						n.setChecked(ticked.contains(idx));
					}
				}

				public boolean performAccessibilityAction(View host, int action, Bundle args) {
					if (action == R.id.widget_root + 1 || action == R.id.widget_root + 2) {
						int to = idx + (action == R.id.widget_root + 1 ? -1 : 1);
						move(idx, to);
						save();
						announceForAccessibility(list.get(to).name + ", " + Tr.t("screen ", "ecranul ") + (to + 1));
						return true;
					}
					return super.performAccessibilityAction(host, action, args);
				}
			});
		}

		String speak() {
			Cities.City c = list.get(idx);
			StringBuilder b = new StringBuilder(c.name);
			if (idx == 0 && autoLoc) b.append(", ").append(Tr.t("your location", "locația ta"));
			if (o.temp.length() > 0) b.append(", ").append(o.temp.replace("°", " " + Tr.t("degrees", "grade")));
			if (o.desc.length() > 0) b.append(", ").append(o.desc);
			if (o.warn.length() > 0) b.append(", ").append(o.warn.toLowerCase());
			b.append(". ").append(Tr.t("Screen ", "Ecranul ")).append(idx + 1).append(Tr.t(" of ", " din ")).append(list.size());
			if (mode.equals("sort")) b.append(". ").append(Tr.t("Move: drag up or down", "Mută: trage în sus sau în jos"));
			return b.toString();
		}

		private float sp(float s) {
			return s * ts * getResources().getDisplayMetrics().scaledDensity;
		}

		@Override
		protected void onMeasure(int wm, int hm) {
			int w = MeasureSpec.getSize(wm);
			float h = px(14) * 2 + sp(21) * 1.25f + sp(14) * 1.3f * 2;
			setMeasuredDimension(w, (int) Math.max(px(mode.equals("sort") ? 80 : 96), h));
		}

		private boolean onHandle(float x) {
			return x > getWidth() - px(64);
		}

		@Override
		public boolean onTouchEvent(MotionEvent e) {
			if (!mode.equals("sort")) return super.onTouchEvent(e);
			switch (e.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				if (!onHandle(e.getX())) return super.onTouchEvent(e);
				dragging = true;
				dragY = e.getRawY();
				startTop = 0;
				getParent().requestDisallowInterceptTouchEvent(true);
				setElevation(px(8));
				setScaleX(1.02f);
				setScaleY(1.02f);
				invalidate();
				return true;
			case MotionEvent.ACTION_MOVE:
				if (!dragging) return super.onTouchEvent(e);
				float dy = e.getRawY() - dragY + startTop;
				setTranslationY(dy);
				float step = getHeight() + px(12);
				if (dy < -step / 2 && idx > 0) {
					int from = idx;
					move(from, from - 1);
					startTop += step;
					setTranslationY(e.getRawY() - dragY + startTop);
				} else if (dy > step / 2 && idx < list.size() - 1) {
					int from = idx;
					move(from, from + 1);
					startTop -= step;
					setTranslationY(e.getRawY() - dragY + startTop);
				}
				return true;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				if (!dragging) return super.onTouchEvent(e);
				dragging = false;
				animate().translationY(0).scaleX(1f).scaleY(1f).setDuration(Motion.off(PlacesActivity.this) ? 0 : 150).start();
				setElevation(0);
				save();
				Log.i(PlacesActivity.this, "LOC", "reordonare: " + list.get(idx).name + " pe ecranul " + (idx + 1));
				invalidate();
				return true;
			}
			return super.onTouchEvent(e);
		}

		@Override
		protected void onDraw(Canvas c) {
			float w = getWidth(), h = getHeight(), r = px(16);
			boolean sel = mode.equals("select") && ticked.contains(idx);
			boolean lifted = dragging;
			RectF rf = new RectF(px(1), px(1), w - px(1), h - px(1));
			p.setStyle(Paint.Style.FILL);
			p.setColor(lifted ? page : sel ? selC : cardC);
			c.drawRoundRect(rf, r, r, p);
			if (o.warn.length() > 0) {
				c.save();
				Path clip = new Path();
				clip.addRoundRect(rf, r, r, Path.Direction.CW);
				c.clipPath(clip);
				p.setColor(o.level >= 3 ? warnR : o.level == 2 ? warnO : warnY);
				c.drawRect(0, 0, px(5), h, p);
				c.restore();
			}
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(sel || lifted ? px(2) : px(1));
			p.setColor(sel || lifted ? accent : edge);
			c.drawRoundRect(rf, r, r, p);
			p.setStyle(Paint.Style.FILL);
			float x = px(18);
			if (mode.equals("select")) {
				float cr = px(14), cx = x + cr, cy = h / 2;
				if (sel) {
					p.setColor(pri);
					c.drawCircle(cx, cy, cr, p);
					icon(c, "check", cx, cy, px(18), priInk);
				} else {
					p.setStyle(Paint.Style.STROKE);
					p.setStrokeWidth(px(2));
					p.setColor(dim);
					c.drawCircle(cx, cy, cr - px(1), p);
					p.setStyle(Paint.Style.FILL);
				}
				x += cr * 2 + px(12);
			} else if (mode.equals("sort")) {
				float cr = px(16), cx = x + cr, cy = h / 2;
				p.setColor(selC);
				c.drawCircle(cx, cy, cr, p);
				text(c, true, sp(16), String.valueOf(idx + 1), cx, cy + sp(16) * 0.36f, accent, Paint.Align.CENTER);
				x += cr * 2 + px(12);
			}
			// The right side.
			float right = w - px(16);
			if (mode.equals("sort")) {
				icon(c, "sort", w - px(36), h / 2, px(28), lifted ? accent : dim);
				right = w - px(64);
			} else if (o.temp.length() > 0) {
				float tsz = sp(mode.equals("select") ? 28 : 34);
				p.setTypeface(Fonts.bold(PlacesActivity.this));
				p.setTextSize(tsz);
				float tw = p.measureText(o.temp);
				float base = mode.equals("select") ? h / 2 + tsz * 0.36f : h / 2 + tsz * 0.1f;
				text(c, true, tsz, o.temp, right, base, ink, Paint.Align.RIGHT);
				float left = right - tw;
				if (!mode.equals("select")) {
					if (o.icon.length() > 0 && WxIcon.has(o.icon)) {
						float is = tsz * 1.15f;
						g.c = c;
						WxIcon.draw(g, o.icon, left - is - px(4), base - tsz * 0.8f - is * 0.12f, is, 1, Theme.icon(dark));
						left -= is + px(4);
					}
					if (o.minmax.length() > 0) text(c, false, sp(14), o.minmax, right, base + sp(14) * 1.35f, dim, Paint.Align.RIGHT);
				}
				right = left - px(10);
			}
			// The name, the place, the warning or the update time.
			float y = px(14) + sp(21);
			float nx = x;
			if (idx == 0 && autoLoc) {
				icon(c, "pin", nx + sp(9), y - sp(7.5f), sp(18), accent);
				nx += sp(22);
			}
			text(c, true, sp(21), fitText(list.get(idx).name, right - nx, true, sp(21)), nx, y, ink, Paint.Align.LEFT);
			y += sp(14) * 1.35f;
			text(c, false, sp(14), fitText(place(list.get(idx)), right - x, false, sp(14)), x, y, dim, Paint.Align.LEFT);
			y += sp(14) * 1.35f;
			if (o.warn.length() > 0) {
				float fs = sp(11.5f);
				p.setTypeface(Fonts.bold(PlacesActivity.this));
				p.setTextSize(fs);
				String t = fitText(o.warn, right - x - px(16), true, fs);
				float tw = p.measureText(t);
				int bg = o.level >= 3 ? warnR : o.level == 2 ? warnO : warnY;
				p.setColor(dark ? Glass.alpha(bg, 0.22f) : bg);
				c.drawRoundRect(new RectF(x, y - fs * 1.05f, x + tw + px(16), y + fs * 0.45f), px(9), px(9), p);
				text(c, true, fs, t, x + px(8), y, dark ? mix(bg, 0xFFFFFFFF, 0.35f) : ink, Paint.Align.LEFT);
			} else if (o.when.length() > 0 && !mode.equals("sort")) {
				text(c, false, sp(13), fitText(o.when, right - x, false, sp(13)), x, y, dim, Paint.Align.LEFT);
			} else if (mode.equals("sort")) {
				text(c, false, sp(13), Tr.t("Screen ", "Ecranul ") + (idx + 1), x, y, dim, Paint.Align.LEFT);
			}
		}

		private void text(Canvas c, boolean bold, float size, String s, float x, float base, int color, Paint.Align a) {
			p.setTypeface(Fonts.of(PlacesActivity.this, bold));
			p.setTextSize(size);
			p.setColor(color);
			p.setTextAlign(a);
			c.drawText(s, x, base, p);
			p.setTextAlign(Paint.Align.LEFT);
		}

		private String fitText(String s, float max, boolean bold, float size) {
			p.setTypeface(Fonts.of(PlacesActivity.this, bold));
			p.setTextSize(size);
			if (max <= 0 || p.measureText(s) <= max) return s;
			int n = p.breakText(s, true, max - p.measureText("…"), null);
			return s.substring(0, Math.max(0, n)) + "…";
		}
	}

	/** "Călărași, România"; abroad the region and the country. */
	private String place(Cities.City c) {
		String country = new java.util.Locale("", c.country.toUpperCase()).getDisplayCountry(new java.util.Locale(Tr.lang().equals("us") ? "en" : Tr.lang()));
		if (c.us()) country = "USA";
		String co = c.county == null || c.county.equals(c.name) ? "" : c.county;
		return co.length() > 0 ? co + ", " + country : country;
	}

	// --- the map with the search at the bottom ---------------------------------------------------

	private MapView map;
	private EditText q;
	private LinearLayout results, card;
	private Cities.City found;
	private Fetcher.Result foundData;
	private int searchSeq;

	private void showMap() {
		mode = "map";
		root.removeAllViews();
		map = new MapView(this);
		map.setDark(dark);
		map.setNight(dark);
		Cities.City c = list.isEmpty() ? null : list.get(0);
		if (c != null) {
			map.center(c.lat, c.lon);
			map.setZoom(9);
			map.setMe(c.lat, c.lon);
		}
		map.setOnMapTap(new MapView.MapTap() {
			public void at(double la, double lo) {
				hideKeys();
				results.setVisibility(View.GONE);
				pickPoint(la, lo);
			}
		});
		root.addView(map, new FrameLayout.LayoutParams(-1, -1));
		View back = fab("back", Tr.t("Back", "Înapoi"), new Runnable() {
			public void run() {
				onBackPressed();
			}
		});
		FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(px(48), px(48), Gravity.TOP | Gravity.LEFT);
		bl.setMargins(px(16), px(12), 0, 0);
		root.addView(back, bl);
		// The search box at the bottom (reached with the thumb), the results and the place above it.
		LinearLayout bottom = new LinearLayout(this);
		bottom.setOrientation(LinearLayout.VERTICAL);
		bottom.setPadding(px(16), 0, px(16), px(20));
		FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
		root.addView(bottom, bp);
		results = new LinearLayout(this);
		results.setOrientation(LinearLayout.VERTICAL);
		results.setBackground(round(Glass.alpha(page, 0.97f), px(20), edge));
		results.setVisibility(View.GONE);
		bottom.addView(results, lp(-1, -2, 0, 10));
		card = new LinearLayout(this);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setBackground(round(Glass.alpha(page, 0.97f), px(22), edge));
		card.setPadding(px(18), px(16), px(18), px(12));
		card.setVisibility(View.GONE);
		bottom.addView(card, lp(-1, -2, 0, 10));
		LinearLayout bar = new LinearLayout(this);
		bar.setGravity(Gravity.CENTER_VERTICAL);
		bar.setBackground(round(Glass.alpha(page, 0.94f), px(28), edge));
		bar.setPadding(px(14), 0, px(4), 0);
		bottom.addView(bar, new LinearLayout.LayoutParams(-1, px(56)));
		IconView si = new IconView("search", dim);
		bar.addView(si, new LinearLayout.LayoutParams(px(28), px(28)));
		q = new EditText(this);
		q.setHint(Tr.t("Search", "Căutare"));
		q.setHintTextColor(dim);
		q.setTextColor(ink);
		q.setTypeface(Fonts.reg(this));
		q.setBackground(null);
		q.setSingleLine(true);
		q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
		q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
		TextSize.size(q, 18, ts);
		q.setContentDescription(Tr.t("Search a town", "Caută o localitate"));
		bar.addView(q, new LinearLayout.LayoutParams(0, -1, 1));
		final View clear = iconBtn("x", Tr.t("Clear the search", "Șterge căutarea"), new Runnable() {
			public void run() {
				q.setText("");
				results.setVisibility(View.GONE);
			}
		});
		bar.addView(clear);
		q.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int id, KeyEvent e) {
				hideKeys();
				search(q.getText().toString(), true);
				return true;
			}
		});
		q.addTextChangedListener(new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int a, int b2, int c2) {
			}

			public void onTextChanged(CharSequence s, int a, int b2, int c2) {
			}

			public void afterTextChanged(Editable e) {
				final String t = e.toString();
				final int seq = ++searchSeq;
				card.setVisibility(View.GONE);
				search(t, false);
				// Online after a short pause (OpenStreetMap asks for few requests).
				ui.postDelayed(new Runnable() {
					public void run() {
						if (seq == searchSeq) search(t, true);
					}
				}, 900);
			}
		});
		if (getIntent().getBooleanExtra("search", false) || list.isEmpty()) {
			q.requestFocus();
			ui.postDelayed(new Runnable() {
				public void run() {
					InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					if (im != null) im.showSoftInput(q, 0);
				}
			}, 300);
		}
	}

	/** The towns matching TEXT: the app's own list at once, then OpenStreetMap (ONLINE). */
	private void search(final String text, boolean online) {
		final String t = text.trim();
		if (t.length() < 2) {
			results.setVisibility(View.GONE);
			return;
		}
		final List<Cities.City> local = new ArrayList<Cities.City>();
		String f = Warnings.fold(t);
		List<List<Cities.City>> all = new ArrayList<List<Cities.City>>();
		all.addAll(cities.byCounty.values());
		all.addAll(cities.abroad.values());
		all.addAll(cities.usStates.values());
		List<Cities.City> mine = new ArrayList<Cities.City>();
		for (String k : prefs.getString("myCities", "").split("\n")) {
			Cities.City c = Cities.City.fromKey(k);
			if (c != null) mine.add(c);
		}
		all.add(mine);
		for (List<Cities.City> l : all)
			for (Cities.City c : l) {
				if (local.size() >= 6) break;
				if (c.lat == 0 && c.lon == 0) continue;
				if (Warnings.fold(c.name).startsWith(f) && !contains(local, c)) local.add(c);
			}
		showResults(local, null);
		if (!online || t.length() < 3) return;
		final int seq = searchSeq;
		new Thread(new Runnable() {
			public void run() {
				final List<Cities.City> got = new ArrayList<Cities.City>();
				final List<String> subs = new ArrayList<String>();
				try {
					String lang = Tr.lang().equals("us") ? "en" : Tr.lang();
					String u = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=8&accept-language=" + lang + "&q=" + URLEncoder.encode(t, "UTF-8");
					org.json.JSONArray a = new org.json.JSONArray(get(u));
					for (int k = 0; k < a.length(); k++) {
						org.json.JSONObject o = a.getJSONObject(k);
						String cls = o.optString("addresstype", o.optString("type", ""));
						if (!(cls.equals("city") || cls.equals("town") || cls.equals("village") || cls.equals("hamlet") || cls.equals("municipality") || cls.equals("suburb")
								|| cls.equals("administrative") || cls.equals("locality") || cls.equals("neighbourhood") || cls.equals("quarter"))) continue;
						Cities.City c = fromOsm(o);
						if (c != null && !contains(got, c)) {
							got.add(c);
							subs.add(o.optString("display_name", ""));
						}
					}
					Log.i(PlacesActivity.this, "LOC", "căutare „" + t + "”: " + got.size() + " locuri (OpenStreetMap)");
				} catch (Exception e) {
					Log.w(PlacesActivity.this, "LOC", "căutarea pe OpenStreetMap a eșuat: " + Fetcher.reason(e, false));
				}
				ui.post(new Runnable() {
					public void run() {
						if (seq != searchSeq || !"map".equals(mode)) return;
						List<Cities.City> l = new ArrayList<Cities.City>(local);
						for (Cities.City c : got) if (!contains(l, c)) l.add(c);
						showResults(l, null);
					}
				});
			}
		}).start();
	}

	private static boolean contains(List<Cities.City> l, Cities.City c) {
		for (Cities.City o : l) if (o.same(c) || (Math.abs(o.lat - c.lat) < 0.01 && Math.abs(o.lon - c.lon) < 0.01)) return true;
		return false;
	}

	/** A place of OpenStreetMap as a town of the app: its country, its county (for the warnings). */
	private Cities.City fromOsm(org.json.JSONObject o) {
		org.json.JSONObject ad = o.optJSONObject("address");
		if (ad == null) return null;
		double la = Math.round(o.optDouble("lat", 0) * 10000) / 10000.0, lo = Math.round(o.optDouble("lon", 0) * 10000) / 10000.0;
		String cc = ad.optString("country_code", "ro").toLowerCase();
		String name = o.optString("name", "");
		if (name.length() == 0) name = ad.optString("village", ad.optString("town", ad.optString("city", "")));
		if (name.length() == 0) return null;
		String rawCounty = ad.optString("county", ad.optString("state", ""));
		String county;
		String match = "";
		if (cc.equals("ro")) {
			county = cities.county(rawCounty);
			if (county.length() == 0 || cities.byCounty.get(county) == null) {
				Cities.City n = cities.nearest(la, lo);
				county = n != null && n.country.equals("ro") ? n.county : county;
			}
			if (Warnings.fold(name).contains("bucuresti")) county = cities.county("București");
		} else if (cc.equals("us")) {
			String st = Cities.stateName(ad.optString("state", ""));
			county = st.length() > 0 ? st : ad.optString("state", "");
		} else {
			county = ad.optString("state", rawCounty);
			match = (ad.optString("state", "") + "/" + ad.optString("county", "")).replaceAll("^/|/$", "");
		}
		if (county.length() == 0) county = name;
		return new Cities.City(cc, county, name, la, lo, match);
	}

	private void showResults(final List<Cities.City> l, String note) {
		results.removeAllViews();
		if (l.isEmpty()) {
			results.setVisibility(View.GONE);
			return;
		}
		for (int k = 0; k < Math.min(6, l.size()); k++) {
			final Cities.City c = l.get(k);
			LinearLayout it = new LinearLayout(this);
			it.setGravity(Gravity.CENTER_VERTICAL);
			it.setPadding(px(16), px(8), px(16), px(8));
			it.setMinimumHeight(px(56));
			it.addView(new IconView("pin", dim), new LinearLayout.LayoutParams(px(22), px(22)));
			LinearLayout tx = new LinearLayout(this);
			tx.setOrientation(LinearLayout.VERTICAL);
			tx.setPadding(px(12), 0, 0, 0);
			TextView a = text(c.name, 17, ink, true);
			TextView b2 = text(place(c), 13.5f, dim, false);
			tx.addView(a);
			tx.addView(b2);
			it.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
			it.setContentDescription(c.name + ", " + place(c));
			it.setBackground(ripple());
			it.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					hideKeys();
					results.setVisibility(View.GONE);
					showPlace(c);
				}
			});
			if (k > 0) {
				View line = new View(this);
				line.setBackgroundColor(edge);
				results.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, px(1) / 1)));
			}
			results.addView(it, new LinearLayout.LayoutParams(-1, -2));
		}
		results.setVisibility(View.VISIBLE);
	}

	/** A tap on the map: the place there (OpenStreetMap's name for it). */
	private void pickPoint(final double la, final double lo) {
		map.setMark(la, lo);
		final int seq = ++searchSeq;
		new Thread(new Runnable() {
			public void run() {
				Cities.City c = null;
				try {
					String lang = Tr.lang().equals("us") ? "en" : Tr.lang();
					String u = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=14&addressdetails=1&accept-language=" + lang + "&lat=" + la + "&lon=" + lo;
					org.json.JSONObject o = new org.json.JSONObject(get(u));
					c = fromOsm(o);
					if (c != null) c = new Cities.City(c.country, c.county, c.name, Math.round(la * 10000) / 10000.0, Math.round(lo * 10000) / 10000.0, c.match);
				} catch (Exception e) {
					Log.w(PlacesActivity.this, "LOC", "locul de pe hartă nu are nume: " + Fetcher.reason(e, false));
				}
				final Cities.City got = c;
				ui.post(new Runnable() {
					public void run() {
						if (seq != searchSeq || !"map".equals(mode)) return;
						if (got == null) {
							toast(Tr.t("No town here, or no internet", "Nicio localitate aici sau fără internet"));
							return;
						}
						showPlace(got);
					}
				});
			}
		}).start();
	}

	/** The place's card over the map: name, region, its weather now, Details and Add. */
	private void showPlace(final Cities.City c) {
		found = c;
		foundData = null;
		map.setMark(c.lat, c.lon);
		map.center(c.lat, c.lon);
		if (map.zoom() < 10) map.setZoom(11);
		card.removeAllViews();
		LinearLayout top = new LinearLayout(this);
		top.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout tx = new LinearLayout(this);
		tx.setOrientation(LinearLayout.VERTICAL);
		tx.addView(text(c.name, 23, ink, true));
		tx.addView(text(place(c), 14.5f, dim, false));
		top.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
		final TextView now = text("…", 34, ink, true);
		now.setGravity(Gravity.RIGHT);
		top.addView(now);
		card.addView(top);
		final TextView more = text("", 14, dim, false);
		more.setVisibility(View.GONE);
		more.setPadding(0, px(8), 0, 0);
		card.addView(more);
		LinearLayout two = new LinearLayout(this);
		two.setPadding(0, px(14), 0, 0);
		final TextView det = bigButton(Tr.t("Details", "Detalii"), false);
		final TextView add = bigButton("+ " + Tr.t("Add", "Adaugă"), true);
		final boolean already = has(c);
		if (already) {
			add.setText("✓ " + Tr.t("In the list", "În listă"));
			add.setEnabled(false);
			add.setAlpha(0.6f);
		}
		LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, px(48), 1);
		l1.setMargins(0, 0, px(10), 0);
		two.addView(det, l1);
		two.addView(add, new LinearLayout.LayoutParams(0, px(48), 1));
		card.addView(two);
		card.setVisibility(View.VISIBLE);
		det.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				more.setVisibility(more.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
			}
		});
		add.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				addFound();
			}
		});
		final Cities.City want = c;
		new Thread(new Runnable() {
			public void run() {
				final Fetcher.Result r = Fetcher.downloadPlace(PlacesActivity.this, want);
				ui.post(new Runnable() {
					public void run() {
						if (found != want) return;
						foundData = r;
						if (r.forecast == null) {
							now.setText("");
							more.setText(Tr.t("The weather did not come: ", "Vremea nu a venit: ") + Fetcher.reason(r.error, Tr.lang().equals("en")));
							return;
						}
						try {
							Weather w = Weather.parse(r.forecast);
							boolean imp = Region.imperial(PlacesActivity.this, want);
							String[] d = texts.describe(w.code, w.isDay);
							now.setText(deg(w.temp, imp) + "°");
							now.setContentDescription(deg(w.temp, imp) + " " + Tr.t("degrees", "grade") + ", " + (d.length > 1 ? d[1] : ""));
							StringBuilder m = new StringBuilder(d.length > 1 ? d[1] : "");
							if (!Double.isNaN(w.tmax)) m.append(" · ").append(Tr.t("max ", "max ")).append(deg(w.tmax, imp)).append("° / ").append(Tr.t("min ", "min ")).append(deg(w.tmin, imp)).append("°");
							if (!Double.isNaN(w.feels)) m.append("\n").append(Tr.t("Feels like ", "Resimțit ")).append(deg(w.feels, imp)).append("°");
							if (!Double.isNaN(w.wind)) m.append(" · ").append(Tr.t("wind ", "vânt ")).append(imp ? Math.round(w.wind / 1.609) + " mph" : Math.round(w.wind) + " km/h");
							if (w.humidity >= 0) m.append(" · ").append(Tr.t("humidity ", "umiditate ")).append(w.humidity).append("%");
							for (int k = 1; k < Math.min(4, w.dayHi.size()); k++) {
								m.append(k == 1 ? "\n" : "   ").append(texts.describe(w.dayCodes.get(k), true).length > 1 ? "" : "");
								m.append(dayName(w.dayDates.get(k), k)).append(" ").append(deg(w.dayHi.get(k), imp)).append("°/").append(deg(w.dayLo.get(k), imp)).append("°");
							}
							more.setText(m.toString());
						} catch (Exception e) {
							now.setText("");
						}
					}
				});
			}
		}).start();
	}

	private String dayName(String iso, int k) {
		if (k == 1) return Tr.t("Tomorrow", "Mâine");
		try {
			java.util.Date d = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(iso);
			return new java.text.SimpleDateFormat("EEE", new java.util.Locale(Tr.lang().equals("us") ? "en" : Tr.lang())).format(d);
		} catch (Exception e) {
			return iso;
		}
	}

	/** Add: the town goes at the end of the list, with a main screen of its own (its weather kept already). */
	private void addFound() {
		if (found == null || has(found)) return;
		Cities.City c = found;
		list.add(c);
		if (foundData != null) Fetcher.savePlace(this, Fetcher.placeId(c), foundData);
		save();
		Log.i(this, "LOC", "adăugată în listă: " + c.label() + " (ecranul " + list.size() + ")");
		hideKeys();
		mode = "list";
		highlight = Tr.t("Added ", "Am adăugat ") + c.name + Tr.t(". Its screen is number ", ". Ecranul ei este al ") + list.size() + Tr.t(": swipe to it.", "-lea: glisează până la el.");
		showList();
	}

	// --- small parts ----------------------------------------------------------------------------

	private int px(float d) {
		return Math.round(d * dp);
	}

	private LinearLayout.LayoutParams lp(int w, int h, int top, int bottom) {
		LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h);
		l.setMargins(0, px(top), 0, px(bottom));
		return l;
	}

	private static int mix(int a, int b, float t) {
		int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		return 0xFF000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
	}

	private GradientDrawable round(int fill, float r, int stroke) {
		GradientDrawable d = new GradientDrawable();
		d.setColor(fill);
		d.setCornerRadius(r);
		if (stroke != 0) d.setStroke(Math.max(1, px(1)), stroke);
		return d;
	}

	private android.graphics.drawable.Drawable ripple() {
		if (Build.VERSION.SDK_INT >= 21) return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Glass.alpha(accent, 0.25f)), null, new android.graphics.drawable.ColorDrawable(0xFFFFFFFF));
		return null;
	}

	private TextView title(String t) {
		TextView v = text(t, 22, ink, true);
		v.setSingleLine(true);
		v.setEllipsize(android.text.TextUtils.TruncateAt.END);
		v.setPadding(px(8), 0, px(8), 0);
		if (Build.VERSION.SDK_INT >= 28) v.setAccessibilityHeading(true);
		return v;
	}

	private TextView text(String t, float sp, int color, boolean bold) {
		TextView v = new TextView(this);
		v.setText(t);
		v.setTextColor(color);
		v.setTypeface(Fonts.of(this, bold));
		TextSize.size(v, sp, ts);
		return v;
	}

	private TextView small(String t, float sp) {
		TextView v = text(t, sp, dim, false);
		v.setLineSpacing(0, 1.15f);
		v.setPadding(px(4), 0, px(4), 0);
		return v;
	}

	private View hint(String mark, String t) {
		LinearLayout h = new LinearLayout(this);
		h.setPadding(px(14), px(12), px(14), px(12));
		h.setBackground(round(selC, px(14), 0));
		TextView m = text(mark, 16, accent, true);
		m.setPadding(0, 0, px(10), 0);
		m.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		h.addView(m);
		TextView v = text(t, 14.5f, ink, false);
		v.setLineSpacing(0, 1.12f);
		h.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
		return h;
	}

	private TextView pillText(String t, boolean filled) {
		TextView v = text(t, 15, filled ? priInk : ink, true);
		v.setGravity(Gravity.CENTER);
		v.setMinHeight(px(48));
		v.setPadding(px(14), 0, px(14), 0);
		return v;
	}

	private TextView bigButton(String t, boolean filled) {
		TextView v = text(t, 17, filled ? priInk : ink, true);
		v.setGravity(Gravity.CENTER);
		v.setBackground(filled ? round(pri, px(26), 0) : round(0x00000000, px(26), edge));
		v.setMinHeight(px(48));
		return v;
	}

	private View iconBtn(String name, String desc, final Runnable r) {
		IconView v = new IconView(name, ink);
		v.setContentDescription(desc);
		v.setClickable(true);
		v.setFocusable(true);
		v.setBackground(ripple());
		v.setOnClickListener(new View.OnClickListener() {
			public void onClick(View x) {
				r.run();
			}
		});
		v.setLayoutParams(new LinearLayout.LayoutParams(px(48), px(48)));
		return v;
	}

	private View fab(String name, String desc, Runnable r) {
		View v = iconBtn(name, desc, r);
		GradientDrawable d = round(Glass.alpha(page, 0.9f), px(24), edge);
		v.setBackground(d);
		if (Build.VERSION.SDK_INT >= 21) v.setElevation(px(4));
		return v;
	}

	/** A line icon drawn in the middle of its box. */
	private final class IconView extends View {
		private final String name;
		private final int color;

		IconView(String name, int color) {
			super(PlacesActivity.this);
			this.name = name;
			this.color = color;
		}

		@Override
		protected void onDraw(Canvas c) {
			icon(c, name, getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()) * (getWidth() > px(40) ? 0.5f : 1f), isEnabled() ? color : dim);
		}
	}

	/** Icon NAME (24 × 24 lines, as the mockups) centred at CX, CY, SIZE wide. */
	private void icon(Canvas c, String name, float cx, float cy, float size, int color) {
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		p.setColor(color);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(1.9f);
		p.setStrokeCap(Paint.Cap.ROUND);
		p.setStrokeJoin(Paint.Join.ROUND);
		c.save();
		float s = size / 24f;
		c.translate(cx - 12 * s, cy - 12 * s);
		c.scale(s, s);
		Path h = new Path();
		switch (name) {
		case "back":
			h.moveTo(14.5f, 6); h.lineTo(8.5f, 12); h.lineTo(14.5f, 18);
			break;
		case "x":
			h.moveTo(7, 7); h.lineTo(17, 17); h.moveTo(17, 7); h.lineTo(7, 17);
			break;
		case "check":
			h.moveTo(5, 12.5f); h.lineTo(9.5f, 17); h.lineTo(19, 7.5f);
			break;
		case "search":
			h.addCircle(10.5f, 10.5f, 6, Path.Direction.CW);
			h.moveTo(15, 15); h.lineTo(20, 20);
			break;
		case "select":
			h.addRoundRect(new RectF(3.5f, 4.5f, 8.5f, 9.5f), 1.2f, 1.2f, Path.Direction.CW);
			h.moveTo(4.6f, 7.1f); h.lineTo(5.8f, 8.3f); h.lineTo(7.8f, 6.1f);
			h.moveTo(11.5f, 7); h.lineTo(20.5f, 7);
			h.addRoundRect(new RectF(3.5f, 14.5f, 8.5f, 19.5f), 1.2f, 1.2f, Path.Direction.CW);
			h.moveTo(11.5f, 17); h.lineTo(20.5f, 17);
			break;
		case "sort":
			h.moveTo(7.5f, 9.5f); h.lineTo(12, 5); h.lineTo(16.5f, 9.5f);
			h.moveTo(7.5f, 14.5f); h.lineTo(12, 19); h.lineTo(16.5f, 14.5f);
			break;
		case "pin":
			h.moveTo(12, 21); h.cubicTo(12, 21, 6, 15.4f, 6, 10); h.cubicTo(6, 6.7f, 8.7f, 4, 12, 4); h.cubicTo(15.3f, 4, 18, 6.7f, 18, 10); h.cubicTo(18, 15.4f, 12, 21, 12, 21);
			h.addCircle(12, 10, 2.2f, Path.Direction.CW);
			break;
		default:
			break;
		}
		c.drawPath(h, p);
		c.restore();
	}

	private void toast(String t) {
		Toast.makeText(this, t, Toast.LENGTH_LONG).show();
	}

	private void hideKeys() {
		InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if (im != null && q != null) im.hideSoftInputFromWindow(q.getWindowToken(), 0);
	}

	private static String readAll(InputStream in) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[16384];
		int n;
		while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
		in.close();
		return new String(out.toByteArray(), "UTF-8");
	}

	private static String get(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setConnectTimeout(12000);
		c.setReadTimeout(15000);
		c.setRequestProperty("User-Agent", UA);
		InputStream in = c.getInputStream();
		String s = readAll(in);
		c.disconnect();
		return s;
	}
}
