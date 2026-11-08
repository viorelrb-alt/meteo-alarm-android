package ro.meteoalarm.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * Farmer mode (v2.50): a map of OpenStreetMap tiles (free, no key; "© OpenStreetMap
 * contributors"), moved with one finger and zoomed with two or a double tap. The pin stays in the
 * middle: the farmer moves the map under it to put it on the parcel. Tiles are kept on the phone
 * a week (cache), as the OpenStreetMap tile policy asks, and fetched two at a time.
 */
final class MapView extends View {
	interface Moved {
		void moved(double lat, double lon);
	}

	/** v2.90: a tap on one of the parcel pins of the Parcels list. */
	interface PinTap {
		void tapped(long id);
	}

	/** v2.90: a parcel pin with its name, drawn where it is (the list map). */
	static final class Pin {
		final long id;
		final double lat, lon;
		final String name;

		Pin(long id, double lat, double lon, String name) {
			this.id = id;
			this.lat = lat;
			this.lon = lon;
			this.name = name;
		}
	}

	private static final String UA = "MeteoAlarmAndroid/2.50 (+https://github.com/viorelrb-alt/meteo-alarm-android)";
	private static final int MIN_Z = 3, MAX_Z = 19;
	private static final long KEEP_MS = 7 * 86400000L;

	private double lat = 44.2, lon = 27.33, zoom = 15;
	/** The phone's position (a blue dot), NaN when unknown. */
	private double meLat = Double.NaN, meLon = Double.NaN;
	private final float dp, tile;
	private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
	private final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(60);
	private final Set<String> loading = new HashSet<String>();
	private final LinkedList<String> queue = new LinkedList<String>();
	private int running;
	private final Handler ui = new Handler(Looper.getMainLooper());
	private final File dir;
	private Moved moved;
	private final ScaleGestureDetector pinch;
	private final GestureDetector taps;
	private float lastX, lastY;
	private boolean dark;
	/** v2.90: the pins of all the parcels (list map); with them no pin in the middle. */
	private java.util.List<Pin> pins;
	private PinTap pinTap;
	private float topPad;

	MapView(Context ctx) {
		super(ctx);
		dp = ctx.getResources().getDisplayMetrics().density;
		// A 256-pixel tile drawn about 128 dp wide, so the streets read on a dense screen.
		tile = Math.max(256, 128 * dp);
		dir = new File(ctx.getCacheDir(), "tiles");
		dir.mkdirs();
		pinch = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
			@Override
			public boolean onScale(ScaleGestureDetector d) {
				zoomBy(Math.log(d.getScaleFactor()) / Math.log(2));
				return true;
			}
		});
		taps = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onDoubleTap(MotionEvent e) {
				zoomBy(1);
				return true;
			}

			@Override
			public boolean onSingleTapConfirmed(MotionEvent e) {
				long id = pinAt(e.getX(), e.getY());
				if (id != Long.MIN_VALUE && pinTap != null) pinTap.tapped(id);
				return true;
			}
		});
		setClickable(true);
	}

	void setDark(boolean d) {
		dark = d;
		invalidate();
	}

	/** v2.90: shows these pins (with their names) instead of the pin in the middle. */
	void setPins(java.util.List<Pin> ps, PinTap t) {
		pins = ps;
		pinTap = t;
		invalidate();
	}

	/** v2.90: room at the top covered by floating buttons, kept free when fitting. */
	void setTopPad(float px) {
		topPad = px;
	}

	/**
	 * v2.90: centre and zoom so every pin is seen (one pin: zoom 15). Needs the size: before the
	 * first layout it is done right after it.
	 */
	void fitPins() {
		if (pins == null || pins.isEmpty()) return;
		if (getWidth() == 0) {
			post(new Runnable() {
				public void run() {
					if (getWidth() > 0) fitPins();
				}
			});
			return;
		}
		double la0 = 90, la1 = -90, lo0 = 180, lo1 = -180;
		for (Pin p : pins) {
			la0 = Math.min(la0, p.lat);
			la1 = Math.max(la1, p.lat);
			lo0 = Math.min(lo0, p.lon);
			lo1 = Math.max(lo1, p.lon);
		}
		double z = 15;
		if (pins.size() > 1) {
			// Room for the pins and their names: 50 dp on the sides, the pin height at the top.
			float w = getWidth() - 100 * dp, h = getHeight() - topPad - 90 * dp;
			for (z = 17; z > MIN_Z; z -= 0.25) {
				double world = tile * Math.pow(2, z);
				if (Math.abs(wx(lo1, world) - wx(lo0, world)) <= w && Math.abs(wy(la0, world) - wy(la1, world)) <= h) break;
			}
		}
		setZoom(z);
		// The middle of the pins a bit lower, under the floating buttons at the top.
		double world = tile * Math.pow(2, zoom);
		double cy = (wy(la0, world) + wy(la1, world)) / 2 - (topPad / 2 + 15 * dp);
		lat = latOf(Math.max(0, Math.min(world, cy)), world);
		lon = (lo0 + lo1) / 2;
		invalidate();
	}

	/** The id of the pin under X, Y (the head of the pin or its name), else Long.MIN_VALUE. */
	private long pinAt(float x, float y) {
		if (pins == null) return Long.MIN_VALUE;
		double world = tile * Math.pow(2, zoom);
		double left = wx(lon, world) - getWidth() / 2.0, top = wy(lat, world) - getHeight() / 2.0;
		long best = Long.MIN_VALUE;
		double bd = 30 * dp;
		for (Pin p : pins) {
			double px = wx(p.lon, world) - left, py = wy(p.lat, world) - top - 22 * dp;
			double d = Math.hypot(px - x, py - y);
			if (d < bd) {
				bd = d;
				best = p.id;
			}
		}
		return best;
	}

	void setOnMoved(Moved m) {
		moved = m;
	}

	void center(double la, double lo) {
		lat = Math.max(-85, Math.min(85, la));
		lon = lo;
		invalidate();
		if (moved != null) moved.moved(lat, lon);
	}

	void setZoom(double z) {
		zoom = Math.max(MIN_Z, Math.min(MAX_Z, z));
		invalidate();
	}

	void setMe(double la, double lo) {
		meLat = la;
		meLon = lo;
		invalidate();
	}

	double lat() {
		return lat;
	}

	double lon() {
		return lon;
	}

	double zoom() {
		return zoom;
	}

	void zoomBy(double dz) {
		setZoom(zoom + dz);
	}

	// World pixels (of TILE-sized tiles) at zoom Z.
	private static double wx(double lon, double world) {
		return (lon + 180) / 360 * world;
	}

	private static double wy(double lat, double world) {
		double r = Math.toRadians(lat);
		return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * world;
	}

	private static double lonOf(double x, double world) {
		return x / world * 360 - 180;
	}

	private static double latOf(double y, double world) {
		double n = Math.PI - 2 * Math.PI * y / world;
		return Math.toDegrees(Math.atan(Math.sinh(n)));
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		getParent().requestDisallowInterceptTouchEvent(true);
		pinch.onTouchEvent(e);
		taps.onTouchEvent(e);
		int act = e.getActionMasked();
		float x = 0, y = 0;
		int n = e.getPointerCount();
		for (int i = 0; i < n; i++) {
			x += e.getX(i);
			y += e.getY(i);
		}
		x /= n;
		y /= n;
		if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN || act == MotionEvent.ACTION_POINTER_UP) {
			lastX = x;
			lastY = y;
			if (act == MotionEvent.ACTION_POINTER_UP) {
				// The finger lifting is left out of the middle.
				int up = e.getActionIndex();
				float sx = 0, sy = 0;
				for (int i = 0; i < n; i++) if (i != up) { sx += e.getX(i); sy += e.getY(i); }
				lastX = sx / Math.max(1, n - 1);
				lastY = sy / Math.max(1, n - 1);
			}
			return true;
		}
		if (act == MotionEvent.ACTION_MOVE) {
			double world = tile * Math.pow(2, zoom);
			double cx = wx(lon, world) - (x - lastX), cy = wy(lat, world) - (y - lastY);
			cy = Math.max(0, Math.min(world, cy));
			lon = lonOf(((cx % world) + world) % world, world);
			lat = latOf(cy, world);
			lastX = x;
			lastY = y;
			invalidate();
			if (moved != null) moved.moved(lat, lon);
		}
		return true;
	}

	@Override
	protected void onDraw(Canvas c) {
		int w = getWidth(), h = getHeight();
		c.drawColor(dark ? 0xFF3A3A38 : 0xFFE8E4DC);
		int z = (int) Math.round(Math.max(MIN_Z, Math.min(MAX_Z, zoom)));
		float ts = (float) (tile * Math.pow(2, zoom - z));
		double world = ts * Math.pow(2, z);
		double cx = wx(lon, world), cy = wy(lat, world);
		double left = cx - w / 2.0, top = cy - h / 2.0;
		int n = 1 << z;
		int tx0 = (int) Math.floor(left / ts), ty0 = (int) Math.floor(top / ts);
		int tx1 = (int) Math.floor((left + w) / ts), ty1 = (int) Math.floor((top + h) / ts);
		Rect src = new Rect(0, 0, 256, 256);
		RectF dst = new RectF();
		for (int ty = ty0; ty <= ty1; ty++) {
			if (ty < 0 || ty >= n) continue;
			for (int tx = tx0; tx <= tx1; tx++) {
				int x = ((tx % n) + n) % n;
				float px = (float) (tx * ts - left), py = (float) (ty * ts - top);
				dst.set(px, py, px + ts + 0.5f, py + ts + 0.5f);
				Bitmap b = get(z, x, ty);
				if (b != null) {
					c.drawBitmap(b, src, dst, paint);
					continue;
				}
				// Meanwhile the tile one level up, enlarged.
				if (z > MIN_Z) {
					Bitmap up = mem.get((z - 1) + "/" + (x / 2) + "/" + (ty / 2));
					if (up != null) {
						Rect half = new Rect((x % 2) * 128, (ty % 2) * 128, (x % 2) * 128 + 128, (ty % 2) * 128 + 128);
						c.drawBitmap(up, half, dst, paint);
					}
				}
			}
		}
		// The phone's position.
		if (!Double.isNaN(meLat)) {
			float mx = (float) (wx(meLon, world) - left), my = (float) (wy(meLat, world) - top);
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(0x553D7BF5);
			c.drawCircle(mx, my, 14 * dp, paint);
			paint.setColor(0xFFFFFFFF);
			c.drawCircle(mx, my, 7 * dp, paint);
			paint.setColor(0xFF2F6BEA);
			c.drawCircle(mx, my, 5 * dp, paint);
		}
		if (pins == null) drawPin(c, w / 2f, h / 2f);
		else {
			paint.setTextSize(13 * dp);
			paint.setFakeBoldText(true);
			for (Pin p : pins) {
				float px = (float) (wx(p.lon, world) - left), py = (float) (wy(p.lat, world) - top);
				if (px < -60 * dp || px > w + 60 * dp || py < -10 * dp || py > h + 50 * dp) continue;
				drawPin(c, px, py);
				// The name under the pin, on a white label.
				String nm = p.name.length() > 18 ? p.name.substring(0, 17) + "…" : p.name;
				float tw = paint.measureText(nm);
				paint.setColor(0xE6FFFFFF);
				c.drawRoundRect(new RectF(px - tw / 2 - 5 * dp, py + 3 * dp, px + tw / 2 + 5 * dp, py + 21 * dp), 5 * dp, 5 * dp, paint);
				paint.setColor(0xFF1A1A1A);
				c.drawText(nm, px - tw / 2, py + 17 * dp, paint);
			}
			paint.setFakeBoldText(false);
		}
		// The attribution the OpenStreetMap licence asks for.
		String att = "© OpenStreetMap contributors";
		paint.setTextSize(11 * dp);
		float aw = paint.measureText(att);
		paint.setColor(0xCCFFFFFF);
		c.drawRect(w - aw - 10 * dp, h - 18 * dp, w, h, paint);
		paint.setColor(0xFF333333);
		c.drawText(att, w - aw - 5 * dp, h - 5 * dp, paint);
	}

	/** The pin with its tip at X, Y: the terracotta of the app with a white middle. */
	private void drawPin(Canvas c, float x, float y) {
		float r = 13 * dp, top = y - 40 * dp;
		Path p = new Path();
		p.moveTo(x, y);
		p.lineTo(x - r * 0.8f, top + r * 1.4f);
		p.lineTo(x + r * 0.8f, top + r * 1.4f);
		p.close();
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(0x55000000);
		c.drawOval(new RectF(x - 7 * dp, y - 3 * dp, x + 7 * dp, y + 3 * dp), paint);
		paint.setColor(0xFFD97757);
		c.drawPath(p, paint);
		c.drawCircle(x, top + r, r, paint);
		paint.setColor(0xFFFFFFFF);
		c.drawCircle(x, top + r, r * 0.42f, paint);
		paint.setColor(0xFF7A3520);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(1.5f * dp);
		c.drawCircle(x, top + r, r, paint);
		paint.setStyle(Paint.Style.FILL);
	}

	/** Tile Z/X/Y from memory, else from the phone (then shown), else asked for (null meanwhile). */
	private Bitmap get(int z, int x, int y) {
		String key = z + "/" + x + "/" + y;
		Bitmap b = mem.get(key);
		if (b != null) return b;
		synchronized (queue) {
			if (loading.contains(key)) return null;
			loading.add(key);
			queue.addFirst(key);
			// Only the tiles of the last screens: old wishes go.
			while (queue.size() > 40) loading.remove(queue.removeLast());
		}
		pump();
		return null;
	}

	private void pump() {
		synchronized (queue) {
			while (running < 2 && !queue.isEmpty()) {
				final String key = queue.removeFirst();
				running++;
				new Thread(new Runnable() {
					public void run() {
						Bitmap b = load(key);
						synchronized (queue) {
							running--;
							loading.remove(key);
						}
						if (b != null) {
							final Bitmap fb = b;
							ui.post(new Runnable() {
								public void run() {
									mem.put(key, fb);
									invalidate();
								}
							});
						}
						pump();
					}
				}).start();
			}
		}
	}

	/** Off the main thread: the tile from the phone's cache (a week), else from OpenStreetMap. */
	private Bitmap load(String key) {
		File f = new File(dir, key.replace('/', '_') + ".png");
		try {
			if (f.exists() && System.currentTimeMillis() - f.lastModified() < KEEP_MS) {
				Bitmap b = BitmapFactory.decodeFile(f.getPath());
				if (b != null) return b;
			}
			HttpURLConnection c = (HttpURLConnection) new URL("https://tile.openstreetmap.org/" + key + ".png").openConnection();
			c.setConnectTimeout(15000);
			c.setReadTimeout(20000);
			c.setRequestProperty("User-Agent", UA);
			try {
				if (c.getResponseCode() != 200) return f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
				InputStream in = c.getInputStream();
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[16384];
				int n;
				while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
				in.close();
				byte[] png = out.toByteArray();
				Bitmap b = BitmapFactory.decodeByteArray(png, 0, png.length);
				if (b != null) {
					FileOutputStream fo = new FileOutputStream(f);
					fo.write(png);
					fo.close();
				}
				return b;
			} finally {
				c.disconnect();
			}
		} catch (Exception e) {
			return f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
		}
	}

	/** Deletes the tiles older than a week (called when the map page opens). */
	void clean() {
		new Thread(new Runnable() {
			public void run() {
				File[] fs = dir.listFiles();
				if (fs == null) return;
				long now = System.currentTimeMillis();
				for (File f : fs) if (now - f.lastModified() > KEEP_MS) f.delete();
			}
		}).start();
	}
}
