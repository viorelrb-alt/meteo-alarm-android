package ro.meteoalarm.android;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import ro.meteoalarm.android.core.Theme;
import ro.meteoalarm.android.core.Tr;

/**
 * v2.140 (Viorel 2026-11-19): a question in the app's own matte window (instead of the phone's
 * dialog): TITLE (and ✕), TEXT, an optional card of ROWS {label, value}, the buttons B2 (plain,
 * left) and B1 (filled, right). The shade starts under the main screen's camera band, so the band
 * stays as it is (TopBand.shadeTop); a tap on the shade or Back closes it.
 */
final class Ask {
	interface Pick {
		/** 1: B1 (the filled button), 2: B2. */
		void pick(int b);
	}

	private Ask() {
	}

	/** TOP: where the shade starts in pixels, or -1 for under the main screen's band. */
	static Dialog show(final Context ctx, boolean dark, String title, String text, String[][] rows, String b1, String b2, boolean close, int top, final Pick p) {
		final float dp = ctx.getResources().getDisplayMetrics().density;
		final float ts = TextSize.get(ctx);
		Typeface reg = Typeface.createFromAsset(ctx.getAssets(), "fonts/LiberationSans-Regular.ttf"),
				bold = Typeface.createFromAsset(ctx.getAssets(), "fonts/LiberationSans-Bold.ttf");
		int ink = Theme.ink(dark), dim = Theme.dim(dark);
		final Dialog d = new Dialog(ctx, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
		final Window w = d.getWindow();
		w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
		final FrameLayout root = new FrameLayout(ctx);
		final View shade = new View(ctx);
		shade.setBackgroundColor(0); // v2.142 (Viorel): no shade, the screen behind stays as it is
		shade.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				d.dismiss();
			}
		});
		final FrameLayout.LayoutParams shl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		shl.topMargin = top >= 0 ? top : TopBand.shadeTop(ctx, null);
		root.addView(shade, shl);
		if (top < 0) root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
			public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets in) {
				shl.topMargin = TopBand.shadeTop(ctx, root);
				shade.requestLayout();
				return in;
			}
		});
		LinearLayout col = new LinearLayout(ctx);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setClickable(true);
		int pad = Math.round(22 * dp);
		col.setPadding(pad, Math.round(20 * dp), pad, Math.round(18 * dp));
		android.graphics.drawable.GradientDrawable pb = Glass.panelBg(dark, 24 * dp, dp);
		pb.setColor(Glass.alpha(Theme.page(dark), dark ? 0.92f : 0.95f));
		col.setBackground(pb);
		LinearLayout head = new LinearLayout(ctx);
		head.setGravity(Gravity.CENTER_VERTICAL);
		FitText t = new FitText(ctx, 1, 0.5f);
		t.setTypeface(bold);
		t.setTextColor(ink);
		t.setIncludeFontPadding(false);
		TextSize.size(t, 22, ts);
		t.setText(title);
		head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		if (close) {
			View x = new View(ctx);
			x.setBackground(Glass.closeIcon(dark, dp));
			x.setContentDescription(Tr.t("Close", "Închide"));
			x.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					d.dismiss();
				}
			});
			head.addView(x, new LinearLayout.LayoutParams(Math.round(44 * dp), Math.round(44 * dp)));
		}
		col.addView(head);
		if (text != null) {
			TextView m = new TextView(ctx);
			m.setTypeface(reg);
			m.setTextColor(dim);
			TextSize.size(m, 15, ts);
			m.setLineSpacing(0, 1.12f);
			m.setText(text);
			m.setPadding(0, Math.round(10 * dp), 0, 0);
			col.addView(m);
		}
		if (rows != null && rows.length > 0) {
			LinearLayout card = new LinearLayout(ctx);
			card.setOrientation(LinearLayout.VERTICAL);
			card.setBackground(Glass.cardBg(dark, 16 * dp, dp));
			card.setPadding(Math.round(14 * dp), Math.round(8 * dp), Math.round(14 * dp), Math.round(8 * dp));
			for (String[] r : rows) {
				LinearLayout l = new LinearLayout(ctx);
				l.setGravity(Gravity.CENTER_VERTICAL);
				l.setPadding(0, Math.round(4 * dp), 0, Math.round(4 * dp));
				TextView a = new TextView(ctx);
				a.setTypeface(reg);
				a.setTextColor(ink);
				TextSize.size(a, 15, ts);
				a.setText(r[0]);
				l.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
				TextView b = new TextView(ctx);
				b.setTypeface(bold);
				b.setTextColor(ink);
				TextSize.size(b, 15, ts);
				b.setText(r[1]);
				b.setPadding(Math.round(10 * dp), 0, 0, 0);
				l.addView(b);
				card.addView(l);
			}
			LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			cl.topMargin = Math.round(12 * dp);
			col.addView(card, cl);
		}
		LinearLayout bs = new LinearLayout(ctx);
		bs.setPadding(0, Math.round(18 * dp), 0, 0);
		for (int i = 0; i < 2; i++) {
			final int k = i == 0 ? 2 : 1;
			String s = k == 1 ? b1 : b2;
			if (s == null) continue;
			FitText b = new FitText(ctx, 1, 0.5f);
			b.setTypeface(bold);
			b.setTextColor(k == 1 ? Theme.onPastel(dark) : ink);
			b.setIncludeFontPadding(false);
			TextSize.size(b, 17, ts);
			b.setText(s);
			b.setGravity(Gravity.CENTER);
			b.setPadding(Math.round(8 * dp), 0, Math.round(8 * dp), 0);
			b.setBackground(Glass.pill(k == 1 ? Theme.pastel(dark) : Glass.disc(dark), k == 1 ? 0 : Glass.edge(dark), 24 * dp, dp));
			b.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					d.dismiss();
					if (p != null) p.pick(k);
				}
			});
			LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, Math.round(48 * dp), 1);
			if (bs.getChildCount() > 0) bl.leftMargin = Math.round(12 * dp);
			bs.addView(b, bl);
		}
		col.addView(bs);
		android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
		FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(Math.min(dm.widthPixels - Math.round(32 * dp), Math.round(460 * dp)), ViewGroup.LayoutParams.WRAP_CONTENT,
				Gravity.CENTER);
		root.addView(col, cl);
		d.setContentView(root);
		w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
		TopBand.fullScreen(w, dark);
		if (android.os.Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
		w.getDecorView().setPadding(0, 0, 0, 0);
		w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
		d.show();
		return d;
	}
}
