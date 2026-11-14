package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;

import ro.meteoalarm.android.core.Theme;

/**
 * v5.00 (Viorel, "Opțiunea 3"): the frosted-glass look of every window. While a window is open
 * (a bubble, the compass, Help, a bottom sheet, the Settings) the screen behind it is blurred and
 * a little darkened; the windows themselves are translucent cards with soft edges.
 * Android 12+ blurs the screen itself (RenderEffect); older phones get a small blurred copy of it.
 */
final class Glass {
	private Glass() {
	}

	// --- Colours -----------------------------------------------------------------------------

	static int alpha(int c, float a) {
		return (Math.round(a * 255) << 24) | (c & 0xFFFFFF);
	}

	/** The window's own fill: the page's colour, mostly opaque (the blur shows through). */
	static int panel(boolean dark) {
		return alpha(Theme.page(dark), dark ? 0.80f : 0.84f);
	}

	/** A card inside a window. */
	static int card(boolean dark) {
		return alpha(Theme.ink(dark), dark ? 0.07f : 0.05f);
	}

	/** The thin edge of the windows and their cards. */
	static int edge(boolean dark) {
		return alpha(Theme.ink(dark), dark ? 0.14f : 0.12f);
	}

	/** The darkening over the blurred screen. */
	static int scrim(boolean dark) {
		return dark ? 0x73000000 : 0x40202830;
	}

	/** The round buttons (X, Aa): a faint disc. */
	static int disc(boolean dark) {
		return alpha(Theme.ink(dark), dark ? 0.12f : 0.08f);
	}

	/** A rounded glass card as a background (radius R px). */
	static GradientDrawable cardBg(boolean dark, float r, float dp) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(card(dark));
		g.setCornerRadius(r);
		g.setStroke(Math.max(1, Math.round(dp)), edge(dark));
		return g;
	}

	/** The window itself as a background (radius R px). */
	static GradientDrawable panelBg(boolean dark, float r, float dp) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(panel(dark));
		g.setCornerRadius(r);
		g.setStroke(Math.max(1, Math.round(dp)), edge(dark));
		return g;
	}

	/** A pill in a colour (buttons). */
	static GradientDrawable pill(int fill, int stroke, float r, float dp) {
		GradientDrawable g = new GradientDrawable();
		g.setColor(fill);
		g.setCornerRadius(r);
		if (stroke != 0) g.setStroke(Math.max(1, Math.round(dp)), stroke);
		return g;
	}

	/** The X of a window: a cross on a faint disc. */
	static Drawable closeIcon(final boolean dark, final float dp) {
		return new Drawable() {
			final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

			@Override
			public void draw(Canvas c) {
				android.graphics.Rect b = getBounds();
				float cx = b.exactCenterX(), cy = b.exactCenterY(), r = Math.min(b.width(), b.height()) / 2f;
				p.setStyle(Paint.Style.FILL);
				p.setColor(disc(dark));
				c.drawCircle(cx, cy, r, p);
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeCap(Paint.Cap.ROUND);
				p.setStrokeWidth(Math.max(1.5f * dp, r * 0.11f));
				p.setColor(Theme.ink(dark));
				float s = r * 0.33f;
				c.drawLine(cx - s, cy - s, cx + s, cy + s, p);
				c.drawLine(cx - s, cy + s, cx + s, cy - s, p);
			}

			@Override
			public void setAlpha(int a) {
			}

			@Override
			public void setColorFilter(android.graphics.ColorFilter f) {
			}

			@Override
			public int getOpacity() {
				return android.graphics.PixelFormat.TRANSLUCENT;
			}
		};
	}

	/** Draws the X of a window (for views that draw themselves, as the compass). */
	static void drawClose(Canvas c, Paint p, float cx, float cy, float r, boolean dark) {
		p.setStyle(Paint.Style.FILL);
		p.setColor(disc(dark));
		c.drawCircle(cx, cy, r, p);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeCap(Paint.Cap.ROUND);
		p.setStrokeWidth(r * 0.11f);
		p.setColor(Theme.ink(dark));
		float s = r * 0.33f;
		c.drawLine(cx - s, cy - s, cx + s, cy + s, p);
		c.drawLine(cx - s, cy + s, cx + s, cy - s, p);
	}

	/** Draws a glass window (for views that draw themselves). */
	static void drawPanel(Canvas c, Paint p, RectF r, float rad, boolean dark, float dp) {
		p.setStyle(Paint.Style.FILL);
		p.setColor(panel(dark));
		c.drawRoundRect(r, rad, rad, p);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(Math.max(1, dp));
		p.setColor(edge(dark));
		c.drawRoundRect(r, rad, rad, p);
	}

	/** v5.01 (Viorel): the bubbles more matte (they often open over another window): the page colour at 95 %. */
	static void drawMatte(Canvas c, Paint p, RectF r, float rad, boolean dark, float dp) {
		p.setStyle(Paint.Style.FILL);
		p.setColor(alpha(Theme.page(dark), 0.95f));
		c.drawRoundRect(r, rad, rad, p);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(Math.max(1, dp));
		p.setColor(edge(dark));
		c.drawRoundRect(r, rad, rad, p);
	}

	/** Draws a glass card inside a window. */
	static void drawCard(Canvas c, Paint p, RectF r, float rad, boolean dark, float dp) {
		p.setStyle(Paint.Style.FILL);
		p.setColor(card(dark));
		c.drawRoundRect(r, rad, rad, p);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(Math.max(1, dp));
		p.setColor(edge(dark));
		c.drawRoundRect(r, rad, rad, p);
	}

	// --- The blurred screen behind the windows -----------------------------------------------

	/**
	 * Sits right over the screen and under the windows. Each frame it checks whether a window is
	 * open; when one opens it blurs CONTENT (Android 12+) or shows a blurred copy of it, and darkens.
	 */
	static final class Backdrop extends View {
		private final View content;
		private final View[] windows;
		private boolean on, dark;
		private Bitmap copy;
		private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
		private final float dp;

		Backdrop(Context c, View content, View... windows) {
			super(c);
			this.content = content;
			this.windows = windows;
			dp = c.getResources().getDisplayMetrics().density;
			setVisibility(GONE);
			setWillNotDraw(false);
		}

		void setDark(boolean d) {
			if (dark != d) {
				dark = d;
				invalidate();
			}
		}

		/** Starts watching the windows (call once the views are in place). */
		void watch(View root) {
			root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
				public boolean onPreDraw() {
					boolean want = false;
					for (View w : windows) if (w != null && w.getVisibility() == VISIBLE) want = true;
					if (want != on) {
						set(want);
						return false;
					}
					return true;
				}
			});
		}

		private void set(boolean want) {
			on = want;
			if (Build.VERSION.SDK_INT >= 31) {
				try {
					content.setRenderEffect(want ? android.graphics.RenderEffect.createBlurEffect(18 * dp, 18 * dp, android.graphics.Shader.TileMode.CLAMP) : null);
				} catch (Throwable t) {
				}
			} else {
				if (copy != null) {
					copy.recycle();
					copy = null;
				}
				if (want) copy = snapshot();
			}
			setVisibility(want ? VISIBLE : GONE);
			invalidate();
		}

		/** A small copy of the screen, blurred (older Android). */
		private Bitmap snapshot() {
			try {
				int W = content.getWidth(), H = content.getHeight();
				if (W <= 0 || H <= 0) return null;
				int f = 10, w = Math.max(1, W / f), h = Math.max(1, H / f);
				Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
				Canvas c = new Canvas(b);
				c.scale(1f / f, 1f / f);
				content.draw(c);
				blur(b, 3);
				blur(b, 3);
				return b;
			} catch (Throwable t) {
				return null;
			}
		}

		/** A box blur of radius R, horizontal then vertical. */
		private static void blur(Bitmap b, int r) {
			int w = b.getWidth(), h = b.getHeight();
			int[] px = new int[w * h], out = new int[w * h];
			b.getPixels(px, 0, w, 0, 0, w, h);
			for (int pass = 0; pass < 2; pass++) {
				boolean hor = pass == 0;
				int n = hor ? w : h, m = hor ? h : w;
				for (int j = 0; j < m; j++) {
					for (int i = 0; i < n; i++) {
						int a = 0, rr = 0, g = 0, bb = 0, cnt = 0;
						for (int k = -r; k <= r; k++) {
							int q = Math.max(0, Math.min(n - 1, i + k));
							int v = hor ? px[j * w + q] : px[q * w + j];
							a += v >>> 24;
							rr += (v >> 16) & 255;
							g += (v >> 8) & 255;
							bb += v & 255;
							cnt++;
						}
						int o = (a / cnt) << 24 | (rr / cnt) << 16 | (g / cnt) << 8 | (bb / cnt);
						if (hor) out[j * w + i] = o;
						else out[i * w + j] = o;
					}
				}
				int[] t = px;
				px = out;
				out = t;
			}
			b.setPixels(px, 0, w, 0, 0, w, h);
		}

		@Override
		protected void onDraw(Canvas c) {
			if (!on) return;
			if (copy != null) c.drawBitmap(copy, null, new android.graphics.Rect(0, 0, getWidth(), getHeight()), p);
			c.drawColor(scrim(dark));
		}
	}
}
