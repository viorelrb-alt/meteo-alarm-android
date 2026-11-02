package ro.meteoalarm.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ro.meteoalarm.android.core.Cities;
import ro.meteoalarm.android.core.Countries;

/**
 * One widget's settings, after the phone's weather widget (Viorel 2026-11-02): a preview, the town
 * (the app's or another) with "Change", the background on / off and how see-through, its colours
 * (by the weather, the phone's, dark), the text colour, and Cancel / Save. Opened by holding the
 * widget › Settings (Android 12+) or from ☰ › Widgets; not when the widget is put on the screen.
 */
public final class WidgetConfig extends Activity {
	private int id = AppWidgetManager.INVALID_APPWIDGET_ID, kind = 2;
	private SharedPreferences prefs;
	private Widget.Set set;
	private boolean en, dark;
	private float dp;
	private ImageView preview;
	private TextView townText, colorsValue, fontValue, alphaText;
	private SeekBar alpha;
	private Switch bgSwitch;
	private Cities cities;
	private int ink, dim, accent, card, page;

	@Override
	protected void onCreate(Bundle b) {
		// The phone's light or dark look.
		boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
		setTheme(night ? android.R.style.Theme_DeviceDefault_NoActionBar : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
		super.onCreate(b);
		prefs = Widget.prefs(this);
		en = "en".equals(prefs.getString("lang", "ro"));
		dp = getResources().getDisplayMetrics().density;
		dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
		page = dark ? 0xFF000000 : 0xFFF4F4F6;
		card = dark ? 0xFF1E1E21 : 0xFFFFFFFF;
		ink = dark ? 0xFFF2F2F2 : 0xFF111111;
		dim = dark ? 0xFF8A8A8E : 0xFF8A8A8E;
		accent = dark ? 0xFF6FA3FF : 0xFF2F6BE0;
		Bundle ex = getIntent().getExtras();
		if (ex != null) id = ex.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
			// The widget stays on the screen whatever happens here.
			setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
			// Put on the screen (Viorel: no settings window then). Android 13+ does not open this
			// screen then (configuration_optional); older ones do, so it closes at once the first
			// time it sees the widget, and the settings come later from the widget or the app.
			if (!prefs.getBoolean("w" + id + "_seen", false)) {
				prefs.edit().putBoolean("w" + id + "_seen", true).apply();
				if (Build.VERSION.SDK_INT < 33) {
					Log.i(this, "WIDGET", "#" + id + " pus pe ecran (Android " + Build.VERSION.RELEASE + "): setările nu se deschid");
					Widget.show(this);
					finish();
					return;
				}
			}
			open(id);
			return;
		}
		// From the app: the widget to set, when there are several.
		final Map<Integer, String> all = Widget.all(this);
		if (all.isEmpty()) {
			new AlertDialog.Builder(this, theme())
					.setTitle(en ? "Meteo Alarm widgets" : "Widget-uri Meteo Alarm")
					.setMessage(en ? "There is no Meteo Alarm widget on the home screen. Hold a free spot on the home screen › Widgets › Meteo Alarm."
							: "Nu ai niciun widget Meteo Alarm pe ecranul de pornire. Ține apăsat pe un loc liber › Widgeturi › Meteo Alarm.")
					.setPositiveButton("OK", null)
					.setOnDismissListener(new DialogInterface.OnDismissListener() {
						public void onDismiss(DialogInterface d) {
							finish();
						}
					}).show();
			return;
		}
		if (all.size() == 1) {
			open(all.keySet().iterator().next());
			return;
		}
		final List<Integer> ids = new ArrayList<Integer>(all.keySet());
		String[] names = new String[ids.size()];
		for (int k = 0; k < ids.size(); k++) {
			int[] f = Widget.frame(this, AppWidgetManager.getInstance(this), ids.get(k), new StringBuilder());
			Widget.Data d = Widget.data(this, Widget.Set.of(this, ids.get(k)).town);
			names[k] = "Widget " + Widget.NAMES[Widget.kind(f[0], f[1])] + " · " + (d.c != null ? d.c.name : "");
		}
		new AlertDialog.Builder(this, theme()).setTitle(en ? "Which widget?" : "Care widget?")
				.setItems(names, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface di, int k) {
						open(ids.get(k));
					}
				})
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					public void onCancel(DialogInterface d) {
						finish();
					}
				}).show();
	}

	private int theme() {
		return dark ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
	}

	private TextView text(String s, float sp, int color, boolean bold) {
		TextView t = new TextView(this);
		t.setText(s);
		t.setTextSize(sp);
		t.setTextColor(color);
		if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
		return t;
	}

	private GradientDrawable round(int color, float r) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(color);
		g.setCornerRadius(r * dp);
		return g;
	}

	private View divider() {
		View v = new View(this);
		v.setBackgroundColor(dark ? 0xFF333336 : 0xFFE2E2E6);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) dp));
		lp.leftMargin = lp.rightMargin = (int) (18 * dp);
		v.setLayoutParams(lp);
		return v;
	}

	/** A row of the card: a title and, under it, its value in the accent colour. */
	private TextView[] choiceRow(LinearLayout parent, String title, View.OnClickListener l) {
		LinearLayout r = new LinearLayout(this);
		r.setOrientation(LinearLayout.VERTICAL);
		r.setPadding((int) (20 * dp), (int) (14 * dp), (int) (20 * dp), (int) (14 * dp));
		TextView t = text(title, 18, ink, false), v = text("", 15, accent, false);
		r.addView(t);
		r.addView(v);
		r.setOnClickListener(l);
		r.setBackgroundResource(android.R.drawable.list_selector_background);
		parent.addView(r);
		return new TextView[] { t, v };
	}

	private TextView colorsTitle;

	private void open(int widgetId) {
		id = widgetId;
		set = Widget.Set.of(this, id);
		prefs.edit().putBoolean("w" + id + "_seen", true).apply();
		int[] f = Widget.frame(this, AppWidgetManager.getInstance(this), id, new StringBuilder());
		kind = Widget.kind(f[0], f[1]);
		Log.i(this, "WIDGET", "#" + id + " setări deschise · " + set.describe());
		loadCities();
		int pad = (int) (16 * dp);

		LinearLayout col = new LinearLayout(this);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(pad, (int) (24 * dp), pad, pad);
		col.addView(text(en ? "Widget settings" : "Setări widget", 24, ink, true));

		// The preview on a wallpaper-like background.
		FrameLayout wall = new FrameLayout(this);
		GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] { 0xFF4B5A6B, 0xFF22262C, 0xFF6B4A55 });
		g.setCornerRadius(22 * dp);
		wall.setBackground(g);
		wall.setPadding(pad * 2, pad * 2, pad * 2, pad * 2);
		preview = new ImageView(this);
		preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
		wall.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
		LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (230 * dp));
		wl.topMargin = (int) (14 * dp);
		col.addView(wall, wl);

		// The town and "Change".
		LinearLayout town = new LinearLayout(this);
		town.setGravity(Gravity.CENTER_VERTICAL);
		town.setBackground(round(card, 26));
		town.setPadding((int) (22 * dp), (int) (16 * dp), (int) (22 * dp), (int) (16 * dp));
		townText = text("", 19, ink, false);
		town.addView(townText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		TextView change = text(en ? "Change" : "Schimbare", 19, accent, true);
		town.addView(change);
		change.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				pickTown();
			}
		});
		LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		tl.topMargin = (int) (14 * dp);
		col.addView(town, tl);

		// The card: background, transparency, colours, text colour.
		LinearLayout c = new LinearLayout(this);
		c.setOrientation(LinearLayout.VERTICAL);
		c.setBackground(round(card, 26));
		LinearLayout bgRow = new LinearLayout(this);
		bgRow.setGravity(Gravity.CENTER_VERTICAL);
		bgRow.setPadding((int) (20 * dp), (int) (14 * dp), (int) (16 * dp), (int) (6 * dp));
		bgRow.addView(text(en ? "Background" : "Fundal", 18, ink, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		bgSwitch = new Switch(this);
		bgSwitch.setChecked(set.bg);
		bgRow.addView(bgSwitch);
		c.addView(bgRow);
		alphaText = text("", 14, dim, false);
		alphaText.setPadding((int) (20 * dp), 0, (int) (20 * dp), 0);
		c.addView(alphaText);
		alpha = new SeekBar(this);
		alpha.setMax(100);
		alpha.setProgress(100 - set.alpha);
		alpha.setPadding((int) (24 * dp), (int) (10 * dp), (int) (24 * dp), (int) (12 * dp));
		c.addView(alpha);
		c.addView(divider());
		TextView[] cr = choiceRow(c, en ? "Colours" : "Culori", new View.OnClickListener() {
			public void onClick(View v) {
				if (set.bg) pickColors();
			}
		});
		colorsTitle = cr[0];
		colorsValue = cr[1];
		c.addView(divider());
		fontValue = choiceRow(c, en ? "Text colour" : "Culoare font", new View.OnClickListener() {
			public void onClick(View v) {
				pickFont();
			}
		})[1];
		LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		cl.topMargin = (int) (14 * dp);
		col.addView(c, cl);

		bgSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
				set.bg = on;
				refresh();
			}
		});
		alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			public void onProgressChanged(SeekBar s, int p, boolean user) {
				set.alpha = 100 - p;
				refresh();
			}

			public void onStartTrackingTouch(SeekBar s) {
			}

			public void onStopTrackingTouch(SeekBar s) {
			}
		});

		// Cancel | Save, as a pill at the bottom.
		LinearLayout bar = new LinearLayout(this);
		bar.setGravity(Gravity.CENTER);
		bar.setBackground(round(card, 30));
		TextView cancel = text(en ? "Cancel" : "Anulare", 19, ink, true), save = text(en ? "Save" : "Salvare", 19, ink, true);
		for (TextView t : new TextView[] { cancel, save }) {
			t.setGravity(Gravity.CENTER);
			t.setPadding((int) (28 * dp), (int) (14 * dp), (int) (28 * dp), (int) (14 * dp));
		}
		bar.addView(cancel);
		View sep = new View(this);
		sep.setBackgroundColor(dim);
		bar.addView(sep, new LinearLayout.LayoutParams(Math.max(1, (int) dp), (int) (22 * dp)));
		bar.addView(save);
		cancel.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(WidgetConfig.this, "WIDGET", "#" + id + " setări: Anulare");
				finish();
			}
		});
		save.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				save();
			}
		});
		LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		bl.gravity = Gravity.CENTER_HORIZONTAL;
		bl.topMargin = (int) (24 * dp);
		col.addView(bar, bl);

		ScrollView sv = new ScrollView(this);
		sv.setBackgroundColor(page);
		sv.addView(col);
		getWindow().setStatusBarColor(page);
		getWindow().setNavigationBarColor(page);
		setContentView(sv);
		refresh();
	}

	private void loadCities() {
		if (cities != null) return;
		try {
			cities = new Cities(Fetcher.readAll(getAssets().open("cities.txt")));
			cities.addWorld(Fetcher.readAll(getAssets().open("world.txt")));
		} catch (Exception e) {
			cities = new Cities("");
		}
		for (String k : prefs.getString("myCities", "").split("\n")) {
			Cities.City c = Cities.City.fromKey(k);
			if (c != null) cities.put(c);
		}
	}

	/** Shows the settings as they are now, in the preview too. */
	private void refresh() {
		Widget.Data d = Widget.data(this, set.town);
		Cities.City app = Widget.data(this, "").c;
		townText.setText("⌖  " + (d.c != null ? d.c.name : "–") + (set.town.length() == 0 ? (en ? "  (as in the app)" : "  (ca în aplicație)") : ""));
		alphaText.setText((en ? "Transparency: " : "Transparență: ") + (100 - set.alpha) + "%");
		alpha.setEnabled(set.bg);
		alphaText.setAlpha(set.bg ? 1f : 0.4f);
		colorsTitle.setAlpha(set.bg ? 1f : 0.4f);
		colorsValue.setAlpha(set.bg ? 1f : 0.4f);
		colorsValue.setText(colorsName(set.colors));
		fontValue.setText(fontName(set.font));
		try {
			int w = (int) Widget.RW[kind], h = (int) Widget.RH[kind];
			Bitmap bm = Widget.render(this, d, set, w, h, dp, kind);
			preview.setImageBitmap(bm);
		} catch (Throwable e) {
			Log.e(this, "WIDGET", "previzualizare", e);
		}
		if (app == null) app = d.c;
	}

	private String colorsName(String c) {
		if (c.equals("phone")) return en ? "Match the phone's settings" : "Potrivire la setările telefonului";
		if (c.equals("dark")) return en ? "Dark" : "Închis";
		return en ? "By the weather" : "După vreme";
	}

	private String fontName(String f) {
		if (f.equals("white")) return en ? "White" : "Alb";
		if (f.equals("black")) return en ? "Black" : "Negru";
		return en ? "Automatic" : "Automat";
	}

	private void pickColors() {
		final String[] keys = { "weather", "phone", "dark" };
		String[] names = new String[3];
		int cur = 0;
		for (int k = 0; k < 3; k++) {
			names[k] = colorsName(keys[k]);
			if (keys[k].equals(set.colors)) cur = k;
		}
		new AlertDialog.Builder(this, theme()).setTitle(en ? "Colours" : "Culori").setSingleChoiceItems(names, cur, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface d, int k) {
				set.colors = keys[k];
				refresh();
				d.dismiss();
			}
		}).show();
	}

	private void pickFont() {
		final String[] keys = { "auto", "white", "black" };
		String[] names = new String[3];
		int cur = 0;
		for (int k = 0; k < 3; k++) {
			names[k] = fontName(keys[k]);
			if (keys[k].equals(set.font)) cur = k;
		}
		new AlertDialog.Builder(this, theme()).setTitle(en ? "Text colour" : "Culoare font").setSingleChoiceItems(names, cur, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface d, int k) {
				set.font = keys[k];
				refresh();
				d.dismiss();
			}
		}).show();
	}

	/** The app's town, the favourites, or a town from a county (another country). */
	private void pickTown() {
		final List<String> keys = new ArrayList<String>();
		List<String> names = new ArrayList<String>();
		Cities.City app = Widget.data(this, "").c;
		keys.add("");
		names.add((en ? "As in the app" : "Ca în aplicație") + (app != null ? " (" + app.name + ")" : ""));
		for (String k : prefs.getString("favs", "").split("\n")) {
			Cities.City c = Cities.City.fromKey(k);
			if (c != null && !keys.contains(c.key())) {
				keys.add(c.key());
				names.add(c.label());
			}
		}
		keys.add("*");
		names.add(en ? "Choose the county / country…" : "Alege județul / țara…");
		new AlertDialog.Builder(this, theme()).setTitle(en ? "Town" : "Localitate")
				.setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface d, int k) {
						if (keys.get(k).equals("*")) pickCounty();
						else {
							set.town = keys.get(k);
							refresh();
						}
					}
				}).show();
	}

	private void pickCounty() {
		final List<List<Cities.City>> lists = new ArrayList<List<Cities.City>>();
		List<String> names = new ArrayList<String>();
		for (Map.Entry<String, List<Cities.City>> e : cities.byCounty.entrySet()) {
			names.add(e.getKey());
			lists.add(e.getValue());
		}
		Countries cs = Fetcher.countries(this);
		for (Map.Entry<String, List<Cities.City>> e : cities.abroad.entrySet()) {
			names.add(cs.name(e.getKey(), en));
			lists.add(e.getValue());
		}
		new AlertDialog.Builder(this, theme()).setTitle(en ? "County / country" : "Județ / țară")
				.setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface d, int k) {
						pickIn(lists.get(k));
					}
				}).show();
	}

	private void pickIn(final List<Cities.City> towns) {
		String[] names = new String[towns.size()];
		for (int k = 0; k < names.length; k++) names[k] = towns.get(k).name;
		new AlertDialog.Builder(this, theme()).setTitle(en ? "Town" : "Localitate")
				.setItems(names, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface d, int k) {
						set.town = towns.get(k).key();
						refresh();
					}
				}).show();
	}

	private void save() {
		set.save(this, id);
		Log.i(this, "WIDGET", "#" + id + " setări salvate · " + set.describe());
		Widget.show(this);
		// A town of its own: its forecast now (then with the refresh in the background).
		final Cities.City own = Cities.City.fromKey(set.town);
		if (own != null) {
			final android.content.Context app = getApplicationContext();
			new Thread(new Runnable() {
				public void run() {
					Refresher.fetchTown(app, own, true);
					Widget.show(app);
				}
			}).start();
		}
		finish();
	}
}
