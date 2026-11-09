package ro.meteoalarm.android;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;

/**
 * v3.50 (Viorel): the buttons of the app's dialogs (OK, Cancel …) on one line in every language:
 * the text gets smaller until it fits the button's share of the row.
 */
final class Dlg {
	private Dlg() {
	}

	/** Shows the dialog of builder B with its buttons fitted. */
	static AlertDialog show(AlertDialog.Builder b) {
		AlertDialog d = b.create();
		show(d);
		return d;
	}

	static void show(final AlertDialog d) {
		d.setOnShowListener(new DialogInterface.OnShowListener() {
			public void onShow(DialogInterface di) {
				fit(d);
			}
		});
		d.show();
	}

	static void fit(final AlertDialog d) {
		final int[] ids = { DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_POSITIVE };
		int n = 0;
		for (int id : ids) {
			Button bt = d.getButton(id);
			if (bt == null || bt.getVisibility() != View.VISIBLE || bt.getText() == null || bt.getText().length() == 0) continue;
			bt.setSingleLine(true);
			bt.setMaxLines(1);
			bt.setEllipsize(null);
			n++;
		}
		if (n == 0) return;
		final int count = n;
		final View any = d.getButton(DialogInterface.BUTTON_POSITIVE) != null ? d.getButton(DialogInterface.BUTTON_POSITIVE) : d.getButton(DialogInterface.BUTTON_NEGATIVE);
		if (any == null) return;
		any.post(new Runnable() {
			public void run() {
				View row = (View) any.getParent();
				int rowW = row != null ? row.getWidth() - row.getPaddingLeft() - row.getPaddingRight() : 0;
				float dens = any.getResources().getDisplayMetrics().scaledDensity;
				for (int id : ids) {
					Button bt = d.getButton(id);
					if (bt == null || bt.getVisibility() != View.VISIBLE || bt.getText() == null || bt.getText().length() == 0) continue;
					int share = rowW > 0 ? rowW / count : bt.getWidth();
					int avail = share - bt.getPaddingLeft() - bt.getPaddingRight() - Math.round(4 * dens);
					if (avail <= 0) continue;
					Paint p = new Paint(bt.getPaint());
					String t = bt.getText().toString();
					if (bt.isAllCaps()) t = t.toUpperCase();
					float sz = bt.getTextSize(), min = 9 * dens;
					p.setTextSize(sz);
					while (sz > min && p.measureText(t) > avail) {
						sz -= dens * 0.5f;
						p.setTextSize(sz);
					}
					if (sz != bt.getTextSize()) bt.setTextSize(TypedValue.COMPLEX_UNIT_PX, sz);
				}
			}
		});
	}
}
