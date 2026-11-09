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
	/** What is shown: "list", "parcel" (v3.30: with its statistics), "map". */
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
			if (entry.equals("map")) showMap(id, p.name, p.lat, p.lon, 17, "finish");
			else showParcel(id);
			return;
		}
		entry = null;
		Log.i(this, "FERMIER", "pagina Parcele deschisă (" + Parcels.all(this).size() + " parcele)");
		showList();
		// v2.91 (Viorel): the GPS starts with the page, the blue dot shows where the farmer is.
		centerMe = false;
		locate();
	}

	@Override
	public void onBackPressed() {
		if (closeOverlay()) return;
		if (screen.equals("map")) {
			stopLoc();
			if (mapBack.equals("finish")) finish();
			else if (mapBack.equals("parcel")) showParcel(curId);
			else showList();
		} else if (screen.equals("parcel")) {
			if (entry != null) finish();
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
		return header(title, null);
	}

	/** The page's title row: a back arrow, TITLE fitted to its room, and the ⋮ menu with ITEMS (v3.11). */
	private LinearLayout header(String title, List<Item> items) {
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
		fitTitle(t, 22);
		if (items != null && !items.isEmpty()) {
			LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(px(52), px(52));
			ml.leftMargin = px(10);
			row.addView(menuButton(items, px(52)), ml);
		}
		return row;
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

	// --- v3.11 (Viorel 2026-11-09): the ⋮ menu, square boxes with a bubble, tables with lines ---

	/** A command of the ⋮ menu. */
	private static final class Item {
		final String label;
		final Runnable run;

		Item(String label, Runnable run) {
			this.label = label;
			this.run = run;
		}
	}

	/** The overlay shown now (the ⋮ menu or a box's bubble), closed by back or a tap outside. */
	private View overlay;

	private boolean closeOverlay() {
		if (overlay == null) return false;
		root.removeView(overlay);
		overlay = null;
		return true;
	}

	/** The width of the page's content, in pixels. */
	private int contentW() {
		return getResources().getDisplayMetrics().widthPixels - px(32);
	}

	/** The ⋮ button (three dots in a frame, unlike the main screen's ☰): opens ITEMS. */
	private View menuButton(final List<Item> items, int size) {
		View v = new View(this) {
			final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			protected void onDraw(Canvas c) {
				pt.setColor(ink);
				float cx = getWidth() / 2f, cy = getHeight() / 2f, r = getWidth() * 0.065f, d = getWidth() * 0.22f;
				for (int k = -1; k <= 1; k++) c.drawCircle(cx, cy + k * d, r, pt);
			}
		};
		v.setBackground(frame(btn, 12));
		v.setElevation(px(3));
		v.setContentDescription(Tr.t("Menu", "Meniu"));
		v.setMinimumWidth(size);
		v.setMinimumHeight(size);
		v.setOnClickListener(new View.OnClickListener() {
			public void onClick(View x) {
				openMenu(items);
			}
		});
		return v;
	}

	/**
	 * The ⋮ menu (v3.30, Viorel): like the main ☰ menu, its commands in rows with thin lines between
	 * them, no buttons; at the top right, as wide as its longest row.
	 */
	private void openMenu(final List<Item> items) {
		closeOverlay();
		Log.i(this, "FERMIER", "meniul ⋮ deschis (" + screen + ")");
		FrameLayout ov = new FrameLayout(this);
		ov.setBackgroundColor(0x66000000);
		ov.setClickable(true);
		ov.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				closeOverlay();
			}
		});
		LinearLayout panel = column();
		panel.setBackground(frame(page, 14));
		panel.setPadding(0, px(6), 0, px(6));
		panel.setElevation(px(8));
		panel.setClickable(true);
		int sw = getResources().getDisplayMetrics().widthPixels;
		Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
		pt.setTextSize(16 * TextSize.get(this) * getResources().getDisplayMetrics().scaledDensity);
		float longest = 0;
		for (Item it : items) longest = Math.max(longest, pt.measureText(it.label));
		int pw = Math.min(sw - px(24), Math.max(px(220), Math.round(longest) + px(40)));
		int line = dark ? 0xFF6B6A65 : 0xFF000000;
		for (int k = 0; k < items.size(); k++) {
			final Item it = items.get(k);
			if (k > 0) {
				View sep = new View(this);
				sep.setBackgroundColor(line);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(1)));
				lp.leftMargin = lp.rightMargin = px(10);
				panel.addView(sep, lp);
			}
			TextView t = text(it.label, 16, false, ink);
			t.setGravity(Gravity.CENTER_VERTICAL);
			t.setPadding(px(16), 0, px(16), 0);
			t.setMinHeight(px(46));
			t.setSingleLine(true);
			t.setEllipsize(android.text.TextUtils.TruncateAt.END);
			t.setBackgroundResource(android.R.drawable.list_selector_background);
			t.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					closeOverlay();
					it.run.run();
				}
			});
			panel.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		ScrollView sv = new ScrollView(this);
		sv.addView(panel);
		FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(pw, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
		pl.setMargins(0, px(70), px(12), px(12));
		ov.addView(sv, pl);
		overlay = ov;
		root.addView(ov, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	/** T's text at the biggest size (MAX sp down to MIN) that fits LINES lines of WIDTH pixels. */
	private void fitLines(TextView t, int width, float max, float min, int lines) {
		Paint pt = new Paint(t.getPaint());
		float scale = TextSize.get(this) * getResources().getDisplayMetrics().scaledDensity;
		String s = t.getText().toString();
		float sp = max;
		for (; sp > min; sp -= 0.5f) {
			pt.setTextSize(sp * scale);
			if (lineCount(pt, s, width) <= lines) break;
		}
		t.setTextSize(Math.max(min, sp) * TextSize.get(this));
		t.setMaxLines(lines);
		t.setEllipsize(android.text.TextUtils.TruncateAt.END);
	}

	/** How many lines S takes at PT within WIDTH pixels, broken at spaces. */
	private static int lineCount(Paint pt, String s, int width) {
		int n = 1;
		float cur = 0, space = pt.measureText(" ");
		for (String w : s.split(" ")) {
			float ww = pt.measureText(w);
			if (ww > width) return 99;
			if (cur > 0 && cur + space + ww > width) {
				n++;
				cur = ww;
			} else cur += (cur > 0 ? space : 0) + ww;
		}
		return n;
	}

	/** The gap between the boxes, in pixels. */
	private int gap() {
		return px(8);
	}

	/**
	 * v3.30 (Viorel): the size of a box on a phone held upright (4 in a row); in landscape and on a
	 * tablet the boxes are at most 20% bigger than that.
	 */
	private int phoneBox() {
		int sdp = getResources().getConfiguration().smallestScreenWidthDp;
		if (TextSize.tablet(this) || sdp <= 0) sdp = 412;
		return Math.round((sdp - 32 - 3 * 8) * dp / 4f);
	}

	/** The boxes' size and how many in a row: [size, per row], rows as even as they can be. */
	private int[] boxGrid(int count) {
		int w = contentW(), g = gap();
		if (!land() && !TextSize.tablet(this)) return new int[] { (w - 3 * g) / 4, 4 };
		int max = Math.round(phoneBox() * 1.2f);
		int kmax = Math.max(1, (w + g) / (max + g));
		int rows = (count + kmax - 1) / kmax;
		int n = (count + rows - 1) / rows;
		return new int[] { Math.min(max, (w - (n - 1) * g) / n), n };
	}

	/** The size of the boxes being built (set by addBoxes' caller through boxGrid). */
	private int boxSize;

	/**
	 * A square box (v3.11, Viorel; v3.30: a short title, never wider than the box, on 2 close lines
	 * at most; SUM: a box of the period's summary, its band darker): the value fitted to the width;
	 * a tap shows FULL (the whole title), the value and the line under it in a bubble.
	 */
	private View box(String title, final String full, final String value, final String sub, boolean sum) {
		final int w = boxSize > 0 ? boxSize : boxGrid(11)[0];
		LinearLayout b = column();
		b.setBackground(frame(card, 12));
		b.setPadding(px(2), px(2), px(2), px(4));
		TextView h = text(title.toUpperCase(), 14, true, sum ? 0xFF262624 : bandInk);
		h.setGravity(Gravity.CENTER);
		h.setPadding(px(3), px(4), px(3), px(4));
		h.setLineSpacing(0, 0.88f);
		h.setIncludeFontPadding(false);
		fitLines(h, w - px(10), 14, 7, 2);
		GradientDrawable hb = new GradientDrawable();
		hb.setColor(sum ? (dark ? 0xFFD6C6A0 : 0xFFB8B8B8) : band);
		float r = px(10);
		hb.setCornerRadii(new float[] { r, r, r, r, 0, 0, 0, 0 });
		h.setBackground(hb);
		b.addView(h, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		// v3.20 (Viorel): only the value in the box; the line under it shows in the bubble.
		float big = Math.min(30, w / dp * 0.3f);
		TextView v = text(nb(value), big, true, ink);
		v.setGravity(Gravity.CENTER);
		fitLines(v, w - px(12), big, 10, 1);
		b.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		b.setOnClickListener(new View.OnClickListener() {
			public void onClick(View x) {
				bubble(full, value, sub);
			}
		});
		return b;
	}

	/** A number keeps its unit on the same line (v3.20): the space after a digit does not break. */
	static String nb(String s) {
		return s == null ? null : s.replaceAll("(\\d) (?=\\S)", "$1 ");
	}

	/**
	 * A box's bubble: its title, value and line, in the middle of the screen. v3.30 (Viorel): the
	 * title 19, the rest 18 (texts differ by one point at most).
	 */
	private void bubble(String title, String value, String sub) {
		closeOverlay();
		Log.i(this, "FERMIER", "casetă: " + title);
		FrameLayout ov = new FrameLayout(this);
		ov.setBackgroundColor(0x66000000);
		ov.setClickable(true);
		ov.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				closeOverlay();
			}
		});
		LinearLayout c = column();
		c.setBackground(frame(card, 16));
		c.setPadding(px(2), px(2), px(2), px(16));
		c.setElevation(px(8));
		TextView h = text(title.toUpperCase(), 19, true, bandInk);
		h.setGravity(Gravity.CENTER);
		h.setPadding(px(12), px(10), px(12), px(10));
		GradientDrawable hb = new GradientDrawable();
		hb.setColor(band);
		float r = px(14);
		hb.setCornerRadii(new float[] { r, r, r, r, 0, 0, 0, 0 });
		h.setBackground(hb);
		c.addView(h, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		int bw = Math.min(getResources().getDisplayMetrics().widthPixels - px(48), px(460));
		TextView v = text(nb(value), 18, true, ink);
		v.setGravity(Gravity.CENTER);
		v.setPadding(px(14), px(12), px(14), 0);
		fitLines(v, bw - px(32), 18, 12, 1);
		c.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		if (sub != null && sub.length() > 0) {
			TextView s = text(nb(sub), 18, false, ink);
			s.setGravity(Gravity.CENTER);
			s.setPadding(px(16), px(6), px(16), 0);
			c.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(bw, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
		ov.addView(c, cl);
		overlay = ov;
		root.addView(ov, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	/** BOXES in centred rows of N squares of SIZE (v3.30, Viorel). */
	private void addBoxes(LinearLayout col, List<View> boxes, int size, int n, int top) {
		int g = gap();
		for (int k = 0; k < boxes.size(); k += n) {
			LinearLayout row = new LinearLayout(this);
			row.setGravity(Gravity.CENTER_HORIZONTAL);
			for (int j = k; j < k + n && j < boxes.size(); j++) {
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
				if (j > k) lp.leftMargin = g;
				row.addView(boxes.get(j), lp);
			}
			col.addView(row, wide(k == 0 ? top : 8));
		}
	}

	/** The natural width (pixels) of the table's columns at SP, each with its padding. */
	private int[] colWidths(String[] head, List<String[]> rows, float sp) {
		Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
		float scale = TextSize.get(this) * getResources().getDisplayMetrics().scaledDensity;
		int nc = head.length;
		int[] cw = new int[nc];
		for (int k = 0; k < nc; k++) {
			pt.setTypeface(Typeface.DEFAULT_BOLD);
			pt.setTextSize(sp * scale);
			cw[k] = Math.round(pt.measureText(head[k]));
			pt.setTypeface(Typeface.DEFAULT);
			for (String[] r : rows) cw[k] = Math.max(cw[k], Math.round(pt.measureText(r[k])));
			cw[k] += 2 * px(sp * 0.6f) + px(2);
		}
		return cw;
	}

	private static int sum(int[] v) {
		int t = 0;
		for (int x : v) t += x;
		return t;
	}

	/** The table text's size (sp): 15 on a phone (v3.30 font rule), at most 20% more elsewhere. */
	private float tableSp() {
		return land() || TextSize.tablet(this) ? 18 : 15;
	}

	/**
	 * A table with lines between its rows and columns (v3.11, Viorel): HEAD on a band, then ROWS;
	 * RIGHT[k] aligns column k to the right; AVAIL pixels wide. v3.30: FIT shrinks the text until
	 * every column fits AVAIL (a tablet, a phone in landscape); else a wider table slides sideways.
	 * A narrower one is stretched to AVAIL.
	 */
	private View table(String[] head, List<String[]> rows, boolean[] right, int avail, boolean fit) {
		float sp = tableSp();
		avail -= 2 * Math.max(1, px(1));
		int[] cw = colWidths(head, rows, sp);
		while (fit && sum(cw) > avail && sp > 9) {
			sp -= 0.5f;
			cw = colWidths(head, rows, sp);
		}
		int nc = head.length, padX = px(sp * 0.6f);
		int total = sum(cw);
		if (total < avail) {
			int extra = (avail - total) / nc;
			for (int k = 0; k < nc; k++) cw[k] += extra;
		}
		int line = dark ? 0xFF6B6A65 : 0xFF000000, thin = Math.max(1, px(1));
		LinearLayout t = column();
		GradientDrawable outer = new GradientDrawable();
		outer.setStroke(thin, line);
		outer.setColor(card);
		t.setBackground(outer);
		t.setPadding(thin, thin, thin, thin);
		for (int r = -1; r < rows.size(); r++) {
			String[] v = r < 0 ? head : rows.get(r);
			LinearLayout row = new LinearLayout(this);
			if (r < 0) row.setBackgroundColor(band);
			for (int k = 0; k < nc; k++) {
				TextView c = text(v[k], sp, r < 0, r < 0 ? bandInk : ink);
				c.setSingleLine(true);
				c.setPadding(padX, px(sp * 0.42f), padX, px(sp * 0.42f));
				c.setGravity((right[k] ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
				row.addView(c, new LinearLayout.LayoutParams(cw[k] - (k < nc - 1 ? thin : 0), ViewGroup.LayoutParams.WRAP_CONTENT));
				if (k < nc - 1) {
					View vl = new View(this);
					vl.setBackgroundColor(line);
					row.addView(vl, new LinearLayout.LayoutParams(thin, ViewGroup.LayoutParams.MATCH_PARENT));
				}
			}
			t.addView(row);
			if (r < rows.size() - 1) {
				View hl = new View(this);
				hl.setBackgroundColor(line);
				t.addView(hl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, thin));
			}
		}
		if (fit) return t;
		android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
		hs.setHorizontalScrollBarEnabled(true);
		hs.setFillViewport(false);
		hs.addView(t);
		return hs;
	}

	/** T's one line shrunk (down to 14 sp) until it fits the room it gets; then cut with "…". */
	private void fitTitle(final TextView t, final float max) {
		t.post(new Runnable() {
			public void run() {
				int w = t.getWidth() - t.getPaddingLeft() - t.getPaddingRight();
				if (w <= 0) return;
				float sp = max;
				Paint pt = new Paint(t.getPaint());
				float scale = TextSize.get(FarmActivity.this) * getResources().getDisplayMetrics().scaledDensity;
				String s = t.getText().toString();
				while (sp > 14) {
					pt.setTextSize(sp * scale);
					if (pt.measureText(s) <= w) break;
					sp -= 0.5f;
				}
				t.setTextSize(sp * TextSize.get(FarmActivity.this));
			}
		});
	}

	/** A scrolled page: SET filled by the caller. */
	private LinearLayout pageOf(String title) {
		return pageOf(title, null);
	}

	private LinearLayout pageOf(String title, List<Item> items) {
		root.removeAllViews();
		overlay = null;
		ScrollView sv = new ScrollView(this);
		sv.setFillViewport(true);
		LinearLayout col = column();
		col.setPadding(px(16), px(14), px(16), px(24));
		col.addView(header(title, items));
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
		// v3.11: the square boxes and the tables take the new width.
		else if (screen.equals("parcel")) showParcel(curId);
		else if (screen.equals("map") && map != null && mapNameIn != null)
			showMap(mapId, mapNameIn.getText().toString(), map.lat(), map.lon(), map.zoom(), mapBack);
	}

	/** The name box of the map page (kept when the phone turns, v2.91). */
	private EditText mapNameIn;

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

	/** The biggest text size (sp, 11 to 16; v3.30 font rule) at which every line of LINES fits WIDTH pixels. */
	private float fitSp(int width, String... lines) {
		Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
		pt.setTypeface(Typeface.DEFAULT);
		float scale = TextSize.get(this) * getResources().getDisplayMetrics().scaledDensity;
		for (float sp = 16; sp > 11; sp -= 0.5f) {
			pt.setTextSize(sp * scale);
			boolean ok = true;
			for (String l : lines) if (pt.measureText(l) > width) ok = false;
			if (ok) return sp;
		}
		return 11;
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
		TextView name = text(p.name, 16, true, ink);
		name.setMaxLines(2);
		name.setEllipsize(android.text.TextUtils.TruncateAt.END);
		String c = coords(p.lat, p.lon) + extra;
		TextView co = text(c, 15, false, dim);
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

	/**
	 * v3.10 (Viorel 2026-11-08): the statistics (and their PDF) from a button floating at the
	 * bottom left of the map; with several parcels, a list to choose one.
	 */
	private void statsFloat(FrameLayout mapBox, final List<Parcels.Parcel> all, boolean tab) {
		int fb = px(tab ? 46 : 42), mg = px(12);
		TextView t = text("▤  " + Tr.t("Statistics · PDF", "Statistică · PDF"), tab ? 18 : 16, true, ink);
		t.setGravity(Gravity.CENTER);
		t.setPadding(px(14), 0, px(16), 0);
		FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, fb, Gravity.BOTTOM | Gravity.LEFT);
		sl.setMargins(mg, 0, 0, mg + px(14));
		mapBox.addView(floatBtn(t, new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(FarmActivity.this, "FERMIER", "buton Statistică · PDF");
				if (all.size() == 1) {
					openStats(all.get(0).id);
					return;
				}
				String[] names = new String[all.size()];
				for (int k = 0; k < names.length; k++) names[k] = all.get(k).name;
				dialog().setTitle(Tr.t("Statistics of which parcel?", "Statistica cărei parcele?")).setItems(names, new android.content.DialogInterface.OnClickListener() {
					public void onClick(android.content.DialogInterface d, int which) {
						openStats(all.get(which).id);
					}
				}).setNegativeButton(Tr.t("Cancel", "Renunță"), null).show();
			}
		}), sl);
	}

	/** The statistics of parcel ID for the last 7 days (the period can be changed there; PDF from there). */
	private void openStats(long id) {
		Calendar now = Calendar.getInstance();
		from = Farm.date(now, -6);
		to = Farm.date(now, 0);
		showParcel(id);
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

	/** Hides the GPS bubble 1.5 s after it is shown (v2.91, Viorel). */
	private final Runnable hideGps = new Runnable() {
		public void run() {
			if (gpsBubble != null) gpsBubble.setVisibility(View.GONE);
		}
	};
	/** The GPS button was tapped: the map goes to the position found (else only the blue dot). */
	private boolean centerMe;

	/**
	 * What floats over a map (v2.90, v2.91 also on the new parcel / edit map): ＋ and − at the top
	 * left, the back button with TITLE at the top right and the GPS button under it, whose bubble
	 * says for 1.5 s that the position is being looked for.
	 */
	private void mapFloats(FrameLayout mapBox, final MapView m, String title, boolean tab) {
		mapFloats(mapBox, m, title, tab, null);
	}

	/** The map's floating buttons; ITEMS (v3.11): the ⋮ menu at the top right, the title left of it. */
	private void mapFloats(FrameLayout mapBox, final MapView m, String title, boolean tab, List<Item> items) {
		int fb = px(tab ? 46 : 42), mg = px(12);
		int mr = 0;
		if (items != null) {
			FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(fb + px(2), fb + px(2), Gravity.TOP | Gravity.RIGHT);
			ml.setMargins(0, mg, mg, 0);
			mapBox.addView(menuButton(items, fb + px(2)), ml);
			mr = fb + px(10);
		}
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
		TextView tt = text(title, tab ? 22 : 20, true, ink);
		tt.setPadding(px(12), 0, px(14), 0);
		tt.setSingleLine(true);
		badge.addView(tt);
		FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
		bl.setMargins(0, mg, mg + mr, 0);
		mapBox.addView(badge, bl);
		FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(fb + px(4), fb + px(4), Gravity.TOP | Gravity.RIGHT);
		gl.setMargins(0, mg + fb + px(14), mg, 0);
		View gps = floatBtn(crosshair(), new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(FarmActivity.this, "FERMIER", "harta: localizare precisă (GPS)");
				if (gpsBubble != null) {
					gpsBubble.setVisibility(View.VISIBLE);
					ui.removeCallbacks(hideGps);
					ui.postDelayed(hideGps, 1500);
				}
				centerMe = true;
				locate();
			}
		});
		gps.setContentDescription(Tr.t("Precise location (GPS)", "Localizare precisă (GPS)"));
		mapBox.addView(gps, gl);
		LinearLayout bub = column();
		bub.setBackground(frame(card, 12));
		bub.setPadding(px(14), px(10), px(14), px(12));
		bub.setElevation(px(5));
		bub.addView(text(Tr.t("Precise location (GPS)", "Localizare precisă (GPS)"), 16, true, ink));
		bub.addView(text(Tr.t("Locating by GPS…", "Se efectuează localizarea GPS…"), 15, false, ink), wide(4));
		bub.setVisibility(View.GONE);
		ui.removeCallbacks(hideGps);
		gpsBubble = bub;
		FrameLayout.LayoutParams ql = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
		ql.setMargins(0, mg + 2 * fb + px(26), mg, 0);
		mapBox.addView(bub, ql);
	}

	/** Looks for the phone's position for the map's blue dot (asks for the permission first). */
	private void locate() {
		afterLoc = new Runnable() {
			public void run() {
				findMe(false);
			}
		};
		if (!Locator.allowed(this))
			requestPermissions(new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION }, ASK_LOC);
		else findMe(false);
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
		overlay = null;
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
		// v3.11 (Viorel): the page's commands in the ⋮ menu at the top right of the map.
		List<Item> items = new java.util.ArrayList<Item>();
		items.add(new Item("＋ " + Tr.t("Save GPS place", "Salvare loc. GPS"), new Runnable() {
			public void run() {
				saveHere();
			}
		}));
		items.add(new Item(Tr.t("Choose on the map", "Alege pe hartă"), new Runnable() {
			public void run() {
				showMap(-1, "", m.lat(), m.lon(), Math.max(13, m.zoom()), "list");
			}
		}));
		if (!all.isEmpty()) {
			items.add(new Item(Tr.t("Edit", "Editare"), new Runnable() {
				public void run() {
					showEditList();
				}
			}));
			items.add(new Item(Tr.t("Delete parcels", "Șterge parcele"), new Runnable() {
				public void run() {
					ticked.clear();
					showDelete();
				}
			}));
		}
		mapFloats(mapBox, m, Tr.t("Parcels", "Parcele"), tab, items);
		if (!all.isEmpty()) statsFloat(mapBox, all, tab);

		// --- The panel: the scrolled cards.
		LinearLayout panel = column();
		int pad = px(12);
		panel.setPadding(pad, px(land ? 12 : 10), pad, land ? px(8) : px(6));
		status = text("", 15, false, dim);
		status.setVisibility(View.GONE);
		panel.addView(status, wide(6));
		final ScrollView sv = new ScrollView(this);
		sv.setVerticalScrollBarEnabled(true);
		LinearLayout list = column();
		list.setPadding(0, 0, 0, px(8));
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
			list.addView(row, wide(k == 0 ? 0 : 10));
			rows.add(row);
		}
		LinearLayout.LayoutParams svl;
		if (land || rows.isEmpty()) svl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, land ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT, land ? 1 : 0);
		else {
			// Portrait: the cards' room is 1 card and the top of the next (a phone, v3.10: a bigger
			// map, Viorel) or 2½ rows (a tablet); the map the rest.
			int h = 0;
			int show = tab ? 3 : 2;
			for (int k = 0; k < Math.min(show, rows.size()); k++) {
				View r = rows.get(k);
				r.measure(View.MeasureSpec.makeMeasureSpec(panelW - 2 * pad, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
				int rh = r.getMeasuredHeight();
				if (!tab && k == 1) h += gap + Math.round(rh * 0.4f);
				else h += tab && k == 2 ? rh / 2 : rh + (k > 0 ? gap : 0);
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
		nameLines(card, p, "", pencil(p));
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
		}
		if (stacked) {
			View fill = new View(this);
			card.addView(fill, new LinearLayout.LayoutParams(1, 0, 1));
		}
		// v3.30 (Viorel): the last row: when it was updated, and at the right the parcel's ONLY switch
		// for all its notifications.
		LinearLayout last = new LinearLayout(this);
		last.setGravity(Gravity.BOTTOM);
		TextView up = text(d != null && f > 0 ? Tr.t("updated ", "actualizat ") + time(f) : "", 15, false, dim);
		up.setPadding(0, 0, 0, px(10));
		last.addView(up, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		last.addView(notifyBox(p));
		card.addView(last, wide(2));
		card.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.i(FarmActivity.this, "FERMIER", "parcelă aleasă pentru ecranul principal: " + p.name);
				select(p.id);
			}
		});
		return card;
	}

	/**
	 * The tick box "Notifications and warnings" (v3.30, Viorel): the only switch for every
	 * notification of parcel P (rain, wind, snow, frost, freeze, fog, freezing rain, drought).
	 */
	private View notifyBox(final Parcels.Parcel p) {
		final android.widget.CheckBox cb = new android.widget.CheckBox(this);
		cb.setText(Tr.t("Notifications and warnings", "Notificări și avertizări"));
		cb.setTextColor(ink);
		cb.setTextSize(15 * TextSize.get(this));
		cb.setChecked(p.alerts);
		if (Build.VERSION.SDK_INT >= 21) cb.setButtonTintList(android.content.res.ColorStateList.valueOf(ink));
		cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
				Parcels.Parcel cur = Parcels.get(FarmActivity.this, p.id);
				if (cur == null) return;
				cur.alerts = p.alerts = on;
				Parcels.put(FarmActivity.this, cur);
				Log.i(FarmActivity.this, "FERMIER", "notificări " + cur.name + ": " + (on ? "pornite" : "oprite"));
				if (on) {
					if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
						requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, ASK_NOTIFY);
					toast(Tr.t("Notifications for this parcel: rain, wind, snow, frost, freeze, fog, freezing rain, drought.",
							"Notificări pentru această parcelă: ploaie, vânt, ninsoare, brumă, îngheț, ceață, polei, secetă."));
				}
			}
		});
		return cb;
	}

	/** "Edit" (v2.80): the parcels; a tap opens one for editing its name and its place. */
	private void showEditList() {
		screen = "edit";
		LinearLayout col = pageOf(Tr.t("Edit", "Editare"));
		col.addView(text(Tr.t("Tap the parcel to edit: its name and the pin on the map.", "Atinge parcela de editat: numele și acul pe hartă."), 15, false, dim), wide(8));
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
		// v2.91: the blue dot at once where the phone was last, until GPS answers.
		if (!isNew && best != null && Double.isNaN(meLa) && map != null) {
			meLa = best.getLatitude();
			meLo = best.getLongitude();
			map.setMe(meLa, meLo);
			if (map == listMap && !map.touched() && !centerMe) map.fitPins();
		}
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
			boolean go = centerMe;
			centerMe = false;
			if (l != null) {
				meLa = l.getLatitude();
				meLo = l.getLongitude();
			}
			if (l != null && map != null) {
				map.setMe(l.getLatitude(), l.getLongitude());
				// v2.91: the GPS button takes the map there; when the Parcels page opens, the
				// map only makes room for the blue dot next to the parcels (if not moved yet).
				if (go) map.center(l.getLatitude(), l.getLongitude());
				else if (map == listMap && !map.touched()) map.fitPins();
			} else if (l == null && go) toast(Tr.t("The position was not found.", "Poziția nu a fost găsită."));
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
		overlay = null;
		// v2.91 (Viorel): like the Parcels page: the map large with its buttons floating over it
		// (landscape: the left 2/3; portrait: on top), the name and Save beside or under it.
		final boolean tab = TextSize.tablet(this), land = land();
		LinearLayout body = new LinearLayout(this);
		body.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
		FrameLayout mapBox = new FrameLayout(this);
		LinearLayout col = column();
		col.setPadding(px(14), px(12), px(14), px(12));
		final EditText nameIn = new EditText(this);
		nameIn.setSingleLine(true);
		nameIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
		nameIn.setHint(Tr.t("Name, e.g. parcel 96/5", "Nume, ex. parcela 96/5"));
		nameIn.setText(name);
		nameIn.setTextColor(ink);
		nameIn.setHintTextColor(dim);
		nameIn.setTextSize(16);
		mapNameIn = nameIn;
		col.addView(nameIn);
		final TextView where = text("", 15, false, dim);
		col.addView(where, wide(4));
		col.addView(text(Tr.t("move the map to put the pin on the parcel", "mută harta ca acul să fie pe parcelă"), 15, false, dim), wide(2));
		map = new MapView(this);
		map.setDark(dark);
		map.clean();
		map.setTopPad(px(tab ? 130 : 120));
		map.setOnMoved(new MapView.Moved() {
			public void moved(double la, double lo) {
				where.setText(coords(la, lo));
			}
		});
		map.setZoom(zoom);
		map.center(lat, lon);
		if (!Double.isNaN(meLa)) map.setMe(meLa, meLo);
		mapBox.addView(map, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		// v3.30 (Viorel): a parcel's options only here, on its edit page: its page with the
		// statistics and the PDF, and deleting it.
		List<Item> opts = null;
		final Parcels.Parcel ed = id >= 0 ? Parcels.get(this, id) : null;
		if (ed != null) {
			opts = new java.util.ArrayList<Item>();
			opts.add(new Item(Tr.t("Statistics · PDF", "Statistică · PDF"), new Runnable() {
				public void run() {
					stopLoc();
					openStats(ed.id);
				}
			}));
			opts.add(new Item(Tr.t("Delete the parcel", "Șterge parcela"), new Runnable() {
				public void run() {
					askDelete(ed);
				}
			}));
		}
		mapFloats(mapBox, map, id < 0 ? Tr.t("New parcel", "Parcelă nouă") : Tr.t("Edit", "Editare"), tab, opts);
		if (land) {
			// Save at the bottom of the right third.
			col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
		}
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
		}), wide(land ? 0 : 10));
		if (land) {
			body.addView(mapBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2));
			View line = new View(this);
			line.setBackgroundColor(ink);
			body.addView(line, new LinearLayout.LayoutParams(px(2), ViewGroup.LayoutParams.MATCH_PARENT));
			ScrollView sv = new ScrollView(this);
			sv.setFillViewport(true);
			sv.addView(col);
			body.addView(sv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
		} else {
			body.addView(mapBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
			View line = new View(this);
			line.setBackgroundColor(ink);
			body.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(2)));
			body.addView(col, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}
		root.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	// --- Deleting parcels (v2.60) ----------------------------------------------------------------

	/**
	 * The parcels with a tick box each; "Delete" asks once more (Viorel 2026-11-07: if the farmer
	 * says no, the ticks stay and can be changed).
	 */
	private void showDelete() {
		screen = "delete";
		LinearLayout col = pageOf(Tr.t("Delete parcels", "Șterge parcele"));
		col.addView(text(Tr.t("Tick the parcels to delete, then tap Delete.", "Bifează parcelele de șters, apoi atinge Șterge."), 15, false, dim), wide(8));
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
			cb.setTextSize(16 * TextSize.get(this));
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

	/**
	 * v3.30 (Viorel 2026-11-09): the parcel's page and its statistics in one. On top, fixed: the
	 * title with the ⋮ menu (the period, PDF), the coordinates, the boxes of now (7) and of the
	 * period's summary (4, the band darker), in centred rows; a line; under it the tables of the next
	 * 12 hours and of the days, which scroll up and down: one under the other, as wide as the screen
	 * (a phone held upright: a wider table slides sideways; a tablet: scaled to fit), on a tablet in
	 * landscape side by side, each scrolling by itself. A phone in landscape has no room for a fixed
	 * part: there the whole page scrolls.
	 */
	private void showParcel(final long id) {
		final Parcels.Parcel p = Parcels.get(this, id);
		if (p == null) {
			showList();
			return;
		}
		screen = "parcel";
		curId = id;
		final Calendar now = Calendar.getInstance();
		final String oldest = Farm.date(now, -(Farm.KEEP_DAYS - 1)), today = Farm.date(now, 0);
		if (from == null) from = Farm.date(now, -6);
		if (to == null) to = today;
		if (from.compareTo(oldest) < 0) from = oldest;
		if (to.compareTo(today) > 0) to = today;
		if (from.compareTo(to) > 0) from = to;
		final List<Farm.Day> days = Farm.read(Parcels.stats(this, id), from, to);
		final double[] s = days.isEmpty() ? null : Farm.summary(days);
		List<Item> items = new java.util.ArrayList<Item>();
		int[] spans = { 7, 14, 28 };
		for (int k = 0; k < spans.length; k++) {
			final int n = spans[k];
			items.add(new Item(Tr.t("Period: ", "Perioada: ") + n + Tr.t(" days", " zile"), new Runnable() {
				public void run() {
					from = Farm.date(now, -(n - 1));
					to = today;
					showParcel(id);
				}
			}));
		}
		items.add(new Item(Tr.t("From ", "De la ") + dateText(from), new Runnable() {
			public void run() {
				pick(true, oldest, today, id);
			}
		}));
		items.add(new Item(Tr.t("To ", "Până la ") + dateText(to), new Runnable() {
			public void run() {
				pick(false, oldest, today, id);
			}
		}));
		if (s != null) items.add(new Item(Tr.t("Save as PDF in Downloads", "Salvează PDF în Descărcări"), new Runnable() {
			public void run() {
				pdf(p, days, s);
			}
		}));
		Farm.Data d = Parcels.data(this, id);
		long f = Parcels.fetched(this, id);
		final boolean tab = TextSize.tablet(this), land = land(), whole = land && !tab;
		// v3.30 font rule (Viorel): the texts differ by one point at most, the source 3 points smaller.
		float fz = land || tab ? 1.2f : 1f, tx = 15 * fz;
		root.removeAllViews();
		overlay = null;

		// --- The fixed part: the title, the coordinates, the boxes.
		LinearLayout top = column();
		top.setPadding(px(16), px(14), px(16), px(10));
		top.addView(header(p.name, items));
		status = text(coords(p.lat, p.lon) + (f > 0 ? " · " + Tr.t("updated ", "actualizat ") + time(f) : ""), tx, false, dim);
		top.addView(status, wide(4));
		String period = dateText(from).substring(0, 5) + " – " + dateText(to).substring(0, 5);
		if (imp) period = dateText(from).substring(0, dateText(from).lastIndexOf('/')) + " – " + dateText(to).substring(0, dateText(to).lastIndexOf('/'));
		top.addView(text(Tr.t("Now", "Acum").toUpperCase() + "  ·  " + Tr.t("Summary", "Rezumat").toUpperCase() + " " + period, tx, true, dim), wide(8));
		int[] g = boxGrid(11);
		boxSize = g[0];
		List<View> bx = new java.util.ArrayList<View>();
		int i = d != null ? d.nowIndex(now) : -1;
		boolean h = i >= 0;
		int di = h ? d.dayIndex(d.dateOf(i)) : -1;
		double temp = d == null ? Double.NaN : !Double.isNaN(d.cTemp) ? d.cTemp : h ? d.temp[i] : Double.NaN;
		double wind = d == null ? Double.NaN : !Double.isNaN(d.cWind) ? d.cWind : h ? d.wind[i] : Double.NaN;
		double gust = d == null ? Double.NaN : !Double.isNaN(d.cGust) ? d.cGust : h ? d.gust[i] : Double.NaN;
		double dir = d == null ? Double.NaN : !Double.isNaN(d.cDir) ? d.cDir : h ? d.dir[i] : Double.NaN;
		double hum = d == null ? Double.NaN : !Double.isNaN(d.cHum) ? d.cHum : h ? d.hum[i] : Double.NaN;
		String hiLo = di >= 0 ? Tr.t("low ", "min ") + deg(d.dMin[di]) + " · " + Tr.t("high ", "max ") + deg(d.dMax[di]) : "";
		int sh = h ? Farm.windShift(d, i, 12) : -1;
		String shift = !h ? "" : sh >= 0 ? Tr.t("turns to ", "se întoarce spre ") + dir8(d.dir[sh]) + Tr.t(" around ", " în jurul orei ") + Texts.clock(d.hourOf(sh), clk12)
				: Tr.t("steady for 12 hours", "stabilă 12 ore");
		String rainSub = di >= 0 ? Tr.t("today up to ", "azi până la ") + pct(d.dPop[di]) + " · " + mm(d.dRain[di]) : "";
		bx.add(box(Tr.t("Air temp.", "Temp. aer"), Tr.t("Air temperature", "Temperatura aerului"), deg(temp), hiLo, false));
		bx.add(box(Tr.t("Wind", "Vânt"), Tr.t("Wind", "Vânt"), kmh(wind), Double.isNaN(gust) ? "" : Tr.t("gusts ", "rafale ") + kmh(gust), false));
		bx.add(box(Tr.t("Wind dir.", "Dir. vânt"), Tr.t("Wind direction", "Direcția vântului"), dir8(dir), shift, false));
		bx.add(box(Tr.t("Rain chance", "Prob. ploaie"), Tr.t("Chance of rain", "Șansa de ploaie"), h ? pct(d.prob[i]) : "–", rainSub, false));
		bx.add(box(Tr.t("Air hum.", "Umid. aer"), Tr.t("Air humidity", "Umiditatea aerului"), pct(hum), "", false));
		bx.add(box(Tr.t("Soil hum.", "Umid. sol"), Tr.t("Soil moisture", "Umiditatea solului"), h ? soil(d.soilM[i]) : "–",
				(h ? soilWord(d.soilM[i]) + " · " : "") + Tr.t("3–9 cm, estimated", "3–9 cm, estimat"), false));
		bx.add(box(Tr.t("Soil temp.", "Temp. sol"), Tr.t("Soil temperature", "Temperatura solului"), h ? deg(d.soilT[i]) : "–", Tr.t("surface, estimated", "suprafață, estimat"), false));
		String per = " · " + dateText(from) + " – " + dateText(to);
		bx.add(box(Tr.t("Min. / max.", "Min. / max."), Tr.t("Lowest / highest", "Minima / maxima"), s == null ? "–" : deg(s[0]) + " / " + deg(s[1]),
				s == null ? "" : days.size() + Tr.t(" days", " zile") + per, true));
		bx.add(box(Tr.t("Rain total", "Ploaie tot."), Tr.t("Rain total", "Ploaie totală"), s == null ? "–" : mm(s[2]),
				s == null ? "" : Math.round(s[5]) + Tr.t(" days with rain (1 mm+)", " zile cu ploaie (1 mm+)") + per, true));
		bx.add(box(Tr.t("Max wind", "Vânt max."), Tr.t("Highest wind", "Vânt maxim"), s == null ? "–" : kmh(s[3]),
				s == null || Double.isNaN(s[6]) ? "" : Tr.t("gusts up to ", "rafale până la ") + kmh(s[6]) + per, true));
		bx.add(box(Tr.t("Mean soil hum.", "Umid. sol med."), Tr.t("Soil moisture", "Umiditatea solului"), s == null ? "–" : soil(s[4]),
				s == null ? "" : Tr.t("mean, estimated", "medie, estimată") + per, true));
		addBoxes(top, bx, g[0], g[1], 6);
		boxSize = 0;

		// --- The tables.
		String[] h12 = { Tr.t("Hour", "Ora"), Tr.t("Temp.", "Temp."), Tr.t("Rain", "Ploaie"), "mm", Tr.t("Wind", "Vânt") };
		boolean[] r12 = { false, true, true, true, true };
		List<String[]> hr = new java.util.ArrayList<String[]>();
		if (h) for (int k = i + 1; k <= i + 12 && k < d.size(); k++)
			hr.add(new String[] { Texts.clock(d.hourOf(k), clk12), deg(d.temp[k]), pct(d.prob[k]), d.precip[k] >= 0.05 ? mm(d.precip[k]) : "–",
					dir8(d.dir[k]) + " " + kmh(d.wind[k]) });
		String[] hd = { Tr.t("Date", "Data"), Tr.t("Max", "Max"), Tr.t("Min", "Min"), Tr.t("Rain", "Ploaie"), Tr.t("Wind", "Vânt"), Tr.t("Soil", "Sol") };
		boolean[] rd = { false, true, true, true, true, true };
		List<String[]> dr = new java.util.ArrayList<String[]>();
		for (int k = days.size() - 1; k >= 0; k--) {
			Farm.Day x = days.get(k);
			String dt = dateText(x.date);
			dr.add(new String[] { dt.substring(0, imp ? dt.lastIndexOf('/') : 5), deg(x.tmax), deg(x.tmin), mm(x.rain), kmh(x.wind), soil(x.soil) });
		}
		String t12 = Tr.t("Next 12 hours", "Următoarele 12 ore").toUpperCase(), tdd = Tr.t("Day by day", "Zi cu zi").toUpperCase() + per;
		String none = Tr.t("No data for this period yet. Open the parcel once with internet: the app downloads the last 28 days from Open-Meteo.",
				"Încă nu sunt date pentru această perioadă. Deschide parcela o dată cu internet: aplicația descarcă ultimele 28 de zile de la Open-Meteo.");

		// --- The note on the soil and the source (3 points smaller), at the bottom.
		LinearLayout foot = column();
		foot.setPadding(px(16), px(6), px(16), px(10));
		foot.addView(text(Screen.soilEst(), tx, false, dim));
		foot.addView(text(Screen.srcOM(), tx - 3, false, dim), wide(4));

		int cw = contentW();
		if (whole) {
			// A phone in landscape: everything scrolls; the tables one under the other, scaled to fit.
			ScrollView sv = new ScrollView(this);
			sv.setFillViewport(true);
			LinearLayout col = column();
			col.addView(top);
			col.addView(rule(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(2)));
			LinearLayout tb = column();
			tb.setPadding(px(16), px(10), px(16), 0);
			addTable(tb, t12, h12, hr, r12, cw, true, null, tx);
			addTable(tb, tdd, hd, dr, rd, cw, true, none, tx);
			col.addView(tb);
			col.addView(foot);
			sv.addView(col);
			root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		} else {
			LinearLayout body = column();
			body.addView(top);
			body.addView(rule(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(2)));
			if (land) {
				// A tablet in landscape: two parts as wide as their tables need, each scrolling by itself.
				int gp = px(20), avail = cw - gp;
				float n1 = sum(colWidths(h12, hr, tableSp())), n2 = sum(colWidths(hd, dr, tableSp()));
				if (hr.isEmpty()) n1 = n2 * 0.8f;
				if (dr.isEmpty()) n2 = n1;
				int w1 = Math.round(avail * n1 / (n1 + n2)), w2 = avail - w1;
				LinearLayout two = new LinearLayout(this);
				two.setPadding(px(16), 0, px(16), 0);
				ScrollView a1 = new ScrollView(this), a2 = new ScrollView(this);
				LinearLayout c1 = column(), c2 = column();
				c1.setPadding(0, px(10), 0, px(10));
				c2.setPadding(0, px(10), 0, px(10));
				addTable(c1, t12, h12, hr, r12, w1, true, null, tx);
				addTable(c2, tdd, hd, dr, rd, w2, true, none, tx);
				a1.addView(c1);
				a2.addView(c2);
				two.addView(a1, new LinearLayout.LayoutParams(w1, ViewGroup.LayoutParams.MATCH_PARENT));
				View vl = new View(this);
				vl.setBackgroundColor(dark ? 0xFF6B6A65 : 0xFF000000);
				LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(Math.max(1, px(1)), ViewGroup.LayoutParams.MATCH_PARENT);
				vp.leftMargin = (gp - px(1)) / 2;
				vp.rightMargin = gp - px(1) - vp.leftMargin;
				two.addView(vl, vp);
				two.addView(a2, new LinearLayout.LayoutParams(w2, ViewGroup.LayoutParams.MATCH_PARENT));
				body.addView(two, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
			} else {
				// Upright: the tables one under the other, scrolling under the fixed boxes.
				ScrollView sv = new ScrollView(this);
				LinearLayout tb = column();
				tb.setPadding(px(16), px(10), px(16), px(10));
				addTable(tb, t12, h12, hr, r12, cw, tab, null, tx);
				addTable(tb, tdd, hd, dr, rd, cw, tab, none, tx);
				sv.addView(tb);
				body.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
			}
			body.addView(foot);
			root.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		}
		if (d == null || System.currentTimeMillis() - f > FRESH_MS) download(p);
	}

	/** The line between the fixed boxes and the tables. */
	private View rule() {
		View v = new View(this);
		v.setBackgroundColor(ink);
		return v;
	}

	/** TITLE, then the table (or NONE when it has no rows), AVAIL pixels wide. */
	private void addTable(LinearLayout col, String title, String[] head, List<String[]> rows, boolean[] right, int avail, boolean fit, String none, float tx) {
		if (rows.isEmpty() && none == null) return;
		col.addView(text(title, tx, true, dim), wide(col.getChildCount() == 0 ? 0 : 18));
		if (rows.isEmpty()) col.addView(text(none, tx, false, ink), wide(6));
		else col.addView(table(head, rows, right, avail, fit), wide(6));
	}

	/** Off the main thread: the parcel's data; the page again when they come. */
	private void download(final Parcels.Parcel p) {
		if (status != null) status.setText(coords(p.lat, p.lon) + " · " + Tr.t("Downloading from Open-Meteo…", "Descarc de la Open-Meteo…"));
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
					}
				});
			}
		}).start();
	}

	/** Asks before deleting parcel P with its statistics (v3.30: from the parcel's edit page). */
	private void askDelete(final Parcels.Parcel p) {
		AlertDialog.Builder d = dialog();
		d.setTitle(Tr.t("Delete ", "Ștergi ") + p.name + "?");
		d.setMessage(Tr.t("The parcel and its statistics are deleted from the phone.", "Parcela și statistica ei se șterg din telefon."));
		d.setPositiveButton(Tr.t("Delete", "Șterge"), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface di, int w) {
				Parcels.delete(FarmActivity.this, p.id);
				Log.i(FarmActivity.this, "FERMIER", "parcelă ștearsă: " + p.name);
				stopLoc();
				listLat = Double.NaN;
				if (entry != null) finish();
				else showList();
			}
		});
		d.setNegativeButton(Tr.t("Cancel", "Anulează"), null);
		d.show();
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
				showParcel(id);
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
