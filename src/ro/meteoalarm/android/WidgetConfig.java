package ro.meteoalarm.android;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Widget settings (Viorel 2026-11-02): how see-through the widgets' background is, from none (the
 * widget on the wallpaper, as before) to solid, with a preview. Opens when a widget is put on the
 * home screen and from ☰ › Widgets; the setting is for all the widgets.
 */
public final class WidgetConfig extends Activity {
	private int id = AppWidgetManager.INVALID_APPWIDGET_ID, kind = 2;
	private SharedPreferences prefs;
	private ImageView preview;
	private TextView value;
	private float dp;

	@Override
	protected void onCreate(Bundle b) {
		super.onCreate(b);
		prefs = getSharedPreferences("settings", MODE_PRIVATE);
		dp = getResources().getDisplayMetrics().density;
		Bundle ex = getIntent().getExtras();
		if (ex != null) id = ex.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		// Put on the home screen: the widget stays even when this screen is left with Back.
		if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
			setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
			android.appwidget.AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
			String cls = info != null && info.provider != null ? info.provider.getClassName() : "";
			kind = cls.endsWith("W2x2") ? 0 : cls.endsWith("W4x1") ? 1 : cls.endsWith("W4x2") ? 2 : 0;
		}
		boolean en = "en".equals(prefs.getString("lang", "ro"));
		int pad = (int) (20 * dp);

		LinearLayout col = new LinearLayout(this);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(pad, pad, pad, pad);
		col.setBackgroundColor(0xFFFFFFFF);

		TextView title = new TextView(this);
		title.setText(en ? "Meteo Alarm widgets" : "Widget-uri Meteo Alarm");
		title.setTextSize(22);
		title.setTextColor(0xFF000000);
		title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
		col.addView(title);

		TextView sub = new TextView(this);
		sub.setText(en ? "Background transparency (for all the widgets)" : "Transparența fundalului (pentru toate widget-urile)");
		sub.setTextSize(16);
		sub.setTextColor(0xFF555555);
		sub.setPadding(0, (int) (6 * dp), 0, (int) (14 * dp));
		col.addView(sub);

		// The preview on a wallpaper-like gradient.
		FrameLayout wall = new FrameLayout(this);
		GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] { 0xFF6A8CAF, 0xFF2E4A62, 0xFFC9A27E });
		g.setCornerRadius(18 * dp);
		wall.setBackground(g);
		wall.setPadding(pad, pad, pad, pad);
		preview = new ImageView(this);
		preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
		preview.setAdjustViewBounds(true);
		wall.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
		col.addView(wall, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (230 * dp)));

		value = new TextView(this);
		value.setTextSize(17);
		value.setTextColor(0xFF000000);
		value.setPadding(0, (int) (18 * dp), 0, (int) (4 * dp));
		col.addView(value);

		SeekBar bar = new SeekBar(this);
		bar.setMax(100);
		final int start = 100 - prefs.getInt("widgetBg", 0);
		bar.setProgress(start);
		bar.setPadding((int) (8 * dp), (int) (12 * dp), (int) (8 * dp), (int) (12 * dp));
		col.addView(bar);

		TextView ends = new TextView(this);
		ends.setText(en ? "solid  ←                    →  fully see-through" : "opac  ←                    →  complet transparent");
		ends.setTextSize(13);
		ends.setTextColor(0xFF777777);
		ends.setGravity(Gravity.CENTER_HORIZONTAL);
		col.addView(ends);

		Button ok = new Button(this);
		ok.setText(en ? "Done" : "Gata");
		ok.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.topMargin = (int) (24 * dp);
		col.addView(ok, lp);

		bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			public void onProgressChanged(SeekBar s, int p, boolean user) {
				prefs.edit().putInt("widgetBg", 100 - p).apply();
				refresh(100 - p);
			}

			public void onStartTrackingTouch(SeekBar s) {
			}

			public void onStopTrackingTouch(SeekBar s) {
				Widget.show(WidgetConfig.this);
			}
		});
		ScrollView sv = new ScrollView(this);
		sv.setBackgroundColor(0xFFFFFFFF);
		sv.addView(col);
		setContentView(sv);
		refresh(100 - start);
	}

	/** The preview and the value for a background of OPACITY percent. */
	private void refresh(int opacity) {
		boolean en = "en".equals(prefs.getString("lang", "ro"));
		value.setText((en ? "Transparency: " : "Transparență: ") + (100 - opacity) + "%");
		try {
			Widget.Data d = Widget.data(this);
			d.alpha = opacity;
			int w = kind == 0 ? 192 : 384, h = kind == 1 ? 96 : 192;
			Bitmap bm = Widget.render(this, d, w, h, dp, kind);
			preview.setImageBitmap(bm);
		} catch (Throwable e) {
			// no preview
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		Widget.show(this);
	}
}
