package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

import ro.meteoalarm.android.core.Screen;
import ro.meteoalarm.android.core.Texts;
import ro.meteoalarm.android.core.Theme;

/**
 * v2.130 (Viorel 2026-11-19): the camera's band (the day at the left, the network and the clock at
 * the right) as one floating layer over any window: the earthquake screen, the Parcels page, the
 * Export, Import and Log windows. The letters are as large as on the main screen (Screen.drawBand:
 * the clock 44 units, the day 28 units, at most 0.62 and 0.42 of the band). With CAPS the day and
 * the clock sit on small capsules of matte glass, to be read over a map.
 */
final class TopBand extends View {
	private final Screen s;
	private final Texts T;
	private final boolean dark, caps, clk12;
	private final Paint pd, pc, pn, pg;
	private final int left;
	private final float dp;
	private int net = -1, netLevel = -1;
	private String netTag = "";
	private final Runnable tick = new Runnable() {
		public void run() {
			if (s == null) readNet();
			invalidate();
			postDelayed(this, 20000);
		}
	};

	/** S: the main screen's state (its network, clock style and language), or null: T and CLK12, the network read here. */
	TopBand(Context ctx, Screen s, Texts T, boolean clk12, boolean dark, Typeface reg, Typeface bold, int bg, int left, boolean caps) {
		super(ctx);
		this.s = s;
		this.T = s != null ? s.T : T;
		this.clk12 = s != null ? s.clk12 : clk12;
		this.dark = dark;
		this.left = left;
		this.caps = caps;
		dp = ctx.getResources().getDisplayMetrics().density;
		if (bg != 0) setBackgroundColor(bg);
		pd = new Paint(Paint.ANTI_ALIAS_FLAG);
		pd.setTypeface(reg);
		pd.setColor(Theme.ink(dark));
		pc = new Paint(pd);
		pc.setTypeface(bold);
		pn = new Paint(Paint.ANTI_ALIAS_FLAG);
		pn.setStyle(Paint.Style.STROKE);
		pn.setStrokeCap(Paint.Cap.ROUND);
		pg = new Paint(Paint.ANTI_ALIAS_FLAG);
		if (s == null) readNet();
	}

	private void readNet() {
		try {
			net = NetJob.kind(getContext());
			Object[] sg = NetJob.signal(getContext(), net);
			netLevel = (Integer) sg[0];
			netTag = (String) sg[1];
		} catch (Exception e) {
			net = -1;
		}
	}

	/** The band's height: the camera's strip, at least 30 dp (from the window's insets, else the status bar's size). */
	static int height(Context ctx, View root) {
		float dp = ctx.getResources().getDisplayMetrics().density;
		int h = 0;
		if (android.os.Build.VERSION.SDK_INT >= 28 && root != null && root.getRootWindowInsets() != null && root.getRootWindowInsets().getDisplayCutout() != null)
			h = root.getRootWindowInsets().getDisplayCutout().getSafeInsetTop();
		if (h == 0) {
			int id = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
			if (id > 0) h = ctx.getResources().getDimensionPixelSize(id);
		}
		return Math.max(h, (int) (30 * dp));
	}

	/** The window's flags for a band of our own: no status bar, the page also under the camera. */
	static void fullScreen(android.view.Window w, boolean dark) {
		if (w == null) return;
		if (android.os.Build.VERSION.SDK_INT >= 28) {
			android.view.WindowManager.LayoutParams lp = w.getAttributes();
			lp.layoutInDisplayCutoutMode = android.os.Build.VERSION.SDK_INT >= 30 ? 3 : android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
			w.setAttributes(lp);
		}
		w.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
		w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | (dark ? 0 : 0x10));
	}

	/** Back to the phone's status bar. */
	static void normal(android.view.Window w, boolean dark) {
		if (w == null) return;
		w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
		w.getDecorView().setSystemUiVisibility(dark ? 0 : 0x2000);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		post(tick);
	}

	@Override
	protected void onDetachedFromWindow() {
		removeCallbacks(tick);
		super.onDetachedFromWindow();
	}

	@Override
	protected void onDraw(Canvas c) {
		float w = getWidth(), h = getHeight(), mid = h / 2;
		android.util.DisplayMetrics m = getResources().getDisplayMetrics();
		// The main screen's units (portrait), whatever way the phone is turned.
		float u = Screen.fit(Math.min(m.widthPixels, m.heightPixels), Math.max(m.widthPixels, m.heightPixels), TextSize.tablet(getContext()))[0];
		float units = h / u;
		float ck = Math.min(44, units * 0.62f) * u, ds = Math.min(28, units * 0.42f) * u, edge = Math.max(Math.max(40 * u, left), getPaddingLeft() > 0 ? getPaddingLeft() + 16 * u : 0);
		int ink = Theme.ink(dark), faint = (ink & 0xFFFFFF) | 0x48000000;
		Calendar now = ro.meteoalarm.android.core.Zone.now();
		int hr = now.get(Calendar.HOUR_OF_DAY), mn = now.get(Calendar.MINUTE);
		String hm = clk12 ? (hr % 12 == 0 ? 12 : hr % 12) + String.format(Locale.US, ":%02d", mn) : String.format(Locale.US, "%02d:%02d", hr, mn);
		String ap = clk12 ? (hr < 12 ? "AM" : "PM") : "";
		int nt = s != null ? s.net : net, nl = s != null ? s.netLevel : netLevel;
		String tag = s != null ? s.netTag : netTag;
		// The right group's width first (for its capsule): [tag][net]  clock [AM].
		pc.setTextSize(ck);
		float right = w - Math.max(40 * u, getPaddingRight() > 0 ? getPaddingRight() + 16 * u : 0);
		float apw = 0;
		if (ap.length() > 0) {
			pc.setTextSize(ck * 0.44f);
			apw = pc.measureText(ap) + 4 * u;
			pc.setTextSize(ck);
		}
		float cw = pc.measureText(hm);
		float nh = ck * 0.64f, nw = nh * 36 / 32;
		pc.setTextSize(nh * 0.8f);
		float tw = nt >= 0 && (nt == 1 || nt == 2) && tag.length() > 0 ? pc.measureText(tag) + 5 * u : 0;
		float groupL = right - apw - cw - (nt >= 0 ? 12 * u + nw + tw : 0);
		String d = T.date(now);
		pd.setTextSize(ds);
		float room = groupL - edge - 30 * u;
		if (room > 0 && pd.measureText(d) > room) pd.setTextSize(Math.max(ds * 0.6f, ds * room / pd.measureText(d)));
		float dw = pd.measureText(d);
		if (caps) {
			float ph = Math.min(h - 4 * dp, ck * 1.25f), r = ph / 2, padX = 12 * u;
			pg.setStyle(Paint.Style.FILL);
			pg.setColor(Glass.alpha(Theme.page(dark), dark ? 0.72f : 0.8f));
			RectF a = new RectF(edge - padX, mid - ph / 2, edge + dw + padX, mid + ph / 2), b = new RectF(groupL - padX, mid - ph / 2, right + padX * 0.8f, mid + ph / 2);
			c.drawRoundRect(a, r, r, pg);
			c.drawRoundRect(b, r, r, pg);
			pg.setStyle(Paint.Style.STROKE);
			pg.setStrokeWidth(Math.max(1, dp));
			pg.setColor(Glass.edge(dark));
			c.drawRoundRect(a, r, r, pg);
			c.drawRoundRect(b, r, r, pg);
		}
		// The clock (and AM/PM).
		pc.setColor(ink);
		pc.setTextAlign(Paint.Align.RIGHT);
		if (apw > 0) {
			pc.setTextSize(ck * 0.44f);
			c.drawText(ap, right, mid + ck * 0.36f, pc);
			right -= apw;
		}
		pc.setTextSize(ck);
		c.drawText(hm, right, mid + ck * 0.36f, pc);
		right -= cw;
		// The network: Wi-Fi arcs, mobile bars, another network (a globe), none (struck through).
		if (nt >= 0) {
			right -= 12 * u;
			float x = right - nw, y = mid - nh / 2, f = nh / 32;
			int col = nt == 0 ? faint : ink;
			pn.setStrokeWidth(4 * f);
			if (nt == 1 || nt == 0) {
				int lit = nt == 0 || nl < 0 ? 3 : new int[] { 0, 1, 1, 2, 3 }[Math.max(0, Math.min(4, nl))];
				float cx = x + nw / 2, cy = y + nh - 3 * f;
				for (int k = 1; k <= 3; k++) {
					pn.setColor(k <= lit ? col : (col & 0xFFFFFF) | 0x48000000);
					float r = k * 9.5f * f;
					c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), 225, 90, false, pn);
				}
				pd.setColor(col);
				c.drawCircle(cx, cy, 3.5f * f, pd);
				if (nt == 0) {
					pn.setColor(ink);
					c.drawLine(x + 2, y + nh - 1, x + nw - 2, y + 1, pn);
				}
			} else if (nt == 2) {
				float bw = 6 * f, gap = 3.3f * f, x0 = x + (nw - 4 * bw - 3 * gap) / 2;
				for (int k = 0; k < 4; k++) {
					float bh = nh * (k + 1) / 4f;
					pd.setColor(nl < 0 || k < nl ? ink : faint);
					c.drawRect(x0 + k * (bw + gap), y + nh - bh, x0 + k * (bw + gap) + bw, y + nh, pd);
				}
			} else {
				float cx = x + nw / 2, cy = y + nh / 2, r = Math.min(nw, nh) / 2 - 2;
				pn.setColor(col);
				pn.setStrokeWidth(3 * f);
				c.drawCircle(cx, cy, r, pn);
				c.drawLine(cx - r, cy, cx + r, cy, pn);
				c.drawLine(cx, cy - r, cx, cy + r, pn);
			}
			pd.setColor(ink);
			if (tw > 0) {
				pc.setTextSize(nh * 0.8f);
				c.drawText(tag, x - 5 * u, y + nh, pc);
			}
		}
		// The day at the left.
		pd.setColor(ink);
		pd.setTextAlign(Paint.Align.LEFT);
		c.drawText(d, edge, mid + pd.getTextSize() * 0.36f, pd);
	}
}
