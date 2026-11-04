package ro.meteoalarm.android;

import ro.meteoalarm.android.core.Tr;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;

/**
 * The diary (Log) on the phone: read it here, share it (WhatsApp, Drive, e-mail… through the phone's
 * share menu) or save it as a file on the phone.
 */
public final class LogActivity extends Activity {
	private static final int SAVE = 7;
	private boolean en;

	@Override
	protected void onCreate(Bundle b) {
		// The phone's light or dark look.
		boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
		setTheme(night ? android.R.style.Theme_DeviceDefault_NoActionBar : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
		super.onCreate(b);
		en = Lang.en(this);
		boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
		int bg = dark ? 0xFF1C1C1E : 0xFFFFFFFF, ink = dark ? 0xFFEDEDED : 0xFF111111;
		float dp = getResources().getDisplayMetrics().density;
		Log.i(this, "JURNAL", "deschis");

		LinearLayout col = new LinearLayout(this);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setBackgroundColor(bg);
		col.setPadding((int) (12 * dp), (int) (12 * dp), (int) (12 * dp), (int) (8 * dp));

		TextView title = new TextView(this);
		title.setText(Tr.t("Diary (log)", "Jurnal"));
		title.setTextSize(22);
		title.setTypeface(Typeface.DEFAULT_BOLD);
		title.setTextColor(ink);
		col.addView(title);
		TextView sub = new TextView(this);
		sub.setText(Tr.t("Everything the app did in the last 5 days; older errors kept 10 days.", "Tot ce a făcut aplicația în ultimele 5 zile; erorile mai vechi rămân 10 zile."));
		sub.setTextSize(13);
		sub.setTextColor(dark ? 0xFFA0A0A0 : 0xFF666666);
		sub.setPadding(0, (int) (2 * dp), 0, (int) (8 * dp));
		col.addView(sub);

		String text = Log.export(this);
		// The newest at the end; long diaries are shown from their last 400 KB (the file has it all).
		if (text.length() > 400000) text = "…\n" + text.substring(text.length() - 400000);
		final TextView body = new TextView(this);
		body.setText(text);
		body.setTextSize(11);
		body.setTypeface(Typeface.MONOSPACE);
		body.setTextColor(ink);
		body.setTextIsSelectable(true);
		final ScrollView sv = new ScrollView(this);
		sv.addView(body);
		col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
		sv.post(new Runnable() {
			public void run() {
				sv.fullScroll(View.FOCUS_DOWN);
			}
		});

		LinearLayout row = new LinearLayout(this);
		row.setGravity(Gravity.CENTER);
		row.addView(button(Tr.t("Share", "Trimite"), new View.OnClickListener() {
			public void onClick(View v) {
				share();
			}
		}), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		row.addView(button(Tr.t("Save", "Salvează"), new View.OnClickListener() {
			public void onClick(View v) {
				save();
			}
		}), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		row.addView(button(Tr.t("Close", "Închide"), new View.OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		}), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		col.addView(row);
		setContentView(col);
		// Each label on one line in its button (Viorel 2026-11-04, A52s: "Salvează" broke in two):
		// smaller letters for a long word or large phone fonts.
		final LinearLayout r = row;
		r.post(new Runnable() {
			public void run() {
				float min = Float.MAX_VALUE;
				for (int k = 0; k < r.getChildCount(); k++) min = Math.min(min, fit((Button) r.getChildAt(k)));
				// The same size on all three.
				for (int k = 0; k < r.getChildCount(); k++) ((Button) r.getChildAt(k)).setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, min);
			}
		});
	}

	private Button button(String t, View.OnClickListener l) {
		Button b = new Button(this);
		b.setText(t);
		b.setAllCaps(false);
		b.setSingleLine(true);
		b.setMaxLines(1);
		int p = (int) (6 * getResources().getDisplayMetrics().density);
		b.setPadding(p, b.getPaddingTop(), p, b.getPaddingBottom());
		b.setOnClickListener(l);
		return b;
	}

	/** The largest text size (px) at which B's label fits its width on one line. */
	private static float fit(Button b) {
		float room = b.getWidth() - b.getCompoundPaddingLeft() - b.getCompoundPaddingRight();
		float size = b.getTextSize();
		android.graphics.Paint pt = new android.graphics.Paint(b.getPaint());
		String t = b.getText().toString();
		while (size > 8 && room > 0 && pt.measureText(t) > room) {
			size *= 0.94f;
			pt.setTextSize(size);
		}
		return size;
	}

	/** The phone's share menu: WhatsApp, Google Drive, e-mail… */
	private void share() {
		File f = Log.exportFile(this);
		Log.i(this, "JURNAL", "trimis " + f.getName() + " " + f.length() / 1024 + " KB");
		Uri u = ApkProvider.logUri(f.getName());
		Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
				.putExtra(Intent.EXTRA_STREAM, u)
				.putExtra(Intent.EXTRA_SUBJECT, "Meteo Alarm — " + (Tr.t("log ", "jurnal ")) + Log.device())
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		send.setClipData(android.content.ClipData.newRawUri(f.getName(), u));
		try {
			startActivity(Intent.createChooser(send, Tr.t("Send the log", "Trimite jurnalul")));
		} catch (Exception e) {
			Log.e(this, "JURNAL", "trimitere", e);
		}
	}

	/** Saves the diary as a file where the user picks (Downloads, Documents…). */
	private void save() {
		Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain")
				.putExtra(Intent.EXTRA_TITLE, Log.exportName());
		try {
			startActivityForResult(i, SAVE);
		} catch (Exception e) {
			Log.e(this, "JURNAL", "salvare", e);
		}
	}

	@Override
	protected void onActivityResult(int req, int res, Intent data) {
		if (req != SAVE || res != RESULT_OK || data == null || data.getData() == null) return;
		try {
			OutputStream out = getContentResolver().openOutputStream(data.getData());
			out.write(Log.export(this).getBytes("UTF-8"));
			out.close();
			Log.i(this, "JURNAL", "salvat pe telefon");
			Toast.makeText(this, Tr.t("Log saved", "Jurnal salvat"), Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			Log.e(this, "JURNAL", "salvare", e);
			Toast.makeText(this, Tr.t("Could not save", "Nu s-a putut salva"), Toast.LENGTH_SHORT).show();
		}
	}
}
