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

import ro.meteoalarm.android.core.Tr;

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

	/** v5.40: a tap on the map away from the pins (the new parcel's place). */
	interface MapTap {
		void at(double lat, double lon);
	}

	/** v2.162 (Viorel): a tap on a town's name written over the map picks that very town. */
	interface TownTap {
		void town(String name, String county, double lat, double lon);
	}

	private TownTap townTap;

	void setOnTownTap(TownTap t) {
		townTap = t;
	}

	/** The names drawn last (their boxes and which town each is), for the taps. */
	private final java.util.List<RectF> tBoxes = new java.util.ArrayList<RectF>();
	private final java.util.List<Integer> tIdx = new java.util.ArrayList<Integer>();

	/** v2.90: a parcel pin with its name, drawn where it is (the list map). */
	static final class Pin {
		final long id;
		final double lat, lon;
		final String name;
		/** v6.00: the parcel's 4 corners (lat, lon × 4), or null. */
		double[] poly;

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
				if (townTap != null && tName != null) {
					for (int k = tBoxes.size() - 1; k >= 0; k--) {
						if (!tBoxes.get(k).contains(e.getX(), e.getY())) continue;
						int i = tIdx.get(k);
						townTap.town(tName[i], tCounty[i], tLat[i], tLon[i]);
						return true;
					}
				}
				if (mapTap != null) {
					double world = tile * Math.pow(2, zoom);
					double left = wx(lon, world) - getWidth() / 2.0, top = wy(lat, world) - getHeight() / 2.0;
					double cy = Math.max(0, Math.min(world, top + e.getY()));
					mapTap.at(latOf(cy, world), lonOf((((left + e.getX()) % world) + world) % world, world));
				} else if (id != Long.MIN_VALUE && pinTap != null) pinTap.tapped(id);
				else if (focusId != Long.MIN_VALUE) {
					focusId = Long.MIN_VALUE;
					invalidate();
				}
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
		// v2.91: the blue dot too, when it is near (not 50 km away).
		boolean me = !Double.isNaN(meLat) && Math.abs(meLat - (la0 + la1) / 2) < 0.4 && Math.abs(meLon - (lo0 + lo1) / 2) < 0.6;
		if (me) {
			la0 = Math.min(la0, meLat);
			la1 = Math.max(la1, meLat);
			lo0 = Math.min(lo0, meLon);
			lo1 = Math.max(lo1, meLon);
		}
		double z = 15;
		if (pins.size() > 1 || me) {
			// Room for the pins and their names: 50 dp on the sides, the pin height at the top.
			// v2.130 (Viorel): in the middle of the screen, so the same room above and below (the
			// buttons at the top, the cards at the bottom or at the right).
			float vp = Math.max(topPad, bottomPad), hp = rightPad;
			float w = getWidth() - 2 * hp - 100 * dp, h = getHeight() - 2 * vp - 90 * dp;
			if (w < getWidth() * 0.3f) w = getWidth() - hp - 100 * dp;
			if (h < getHeight() * 0.25f) h = getHeight() - topPad - bottomPad - 90 * dp;
			for (z = 17; z > MIN_Z; z -= 0.25) {
				double world = tile * Math.pow(2, z);
				if (Math.abs(wx(lo1, world) - wx(lo0, world)) <= w && Math.abs(wy(la0, world) - wy(la1, world)) <= h) break;
			}
		}
		setZoom(z);
		// v2.130 (Viorel): the middle of the pins in the middle of the screen (the pin's head a little
		// above its point, so 15 dp lower).
		double world = tile * Math.pow(2, zoom);
		double cy = (wy(la0, world) + wy(la1, world)) / 2 - 15 * dp;
		lat = latOf(Math.max(0, Math.min(world, cy)), world);
		lon = (lo0 + lo1) / 2;
		invalidate();
	}

	/** v2.020: zoomed and centred on corners C (or on LA, LO without them), with some room round them. */
	void fitCorners(double[] c, double la, double lo) {
		fitCorners(c, la, lo, 24 * dp, 28 * dp, 24 * dp, 28 * dp);
	}

	/** v2.022: the same, inside the map less L, T, R, B pixels (a label, a box over the map). */
	void fitCorners(double[] c, double la, double lo, float l, float t, float r, float b) {
		if (getWidth() == 0) return;
		double la0 = la, la1 = la, lo0 = lo, lo1 = lo;
		if (c != null && c.length >= 2) {
			la0 = la1 = c[0];
			lo0 = lo1 = c[1];
			for (int k = 0; k < c.length / 2; k++) {
				la0 = Math.min(la0, c[2 * k]);
				la1 = Math.max(la1, c[2 * k]);
				lo0 = Math.min(lo0, c[2 * k + 1]);
				lo1 = Math.max(lo1, c[2 * k + 1]);
			}
		}
		float fw = Math.max(getWidth() - l - r, 40 * dp), fh = Math.max(getHeight() - t - b, 40 * dp);
		double z = 18;
		for (; z > MIN_Z; z -= 0.25) {
			double world = tile * Math.pow(2, z);
			if (Math.abs(wx(lo1, world) - wx(lo0, world)) <= fw && Math.abs(wy(la0, world) - wy(la1, world)) <= fh) break;
		}
		setZoom(z);
		double world = tile * Math.pow(2, zoom);
		// The parcel in the middle of the free area (not of the whole map).
		double cy = (wy(la0, world) + wy(la1, world)) / 2 - (t - b) / 2, cx = (wx(lo0, world) + wx(lo1, world)) / 2 - (l - r) / 2;
		lat = latOf(Math.max(0, Math.min(world, cy)), world);
		lon = lonOf(cx, world);
		invalidate();
	}

	/** v6.10: room covered by the cards at the right (landscape) and at the bottom (portrait). */
	private float rightPad, bottomPad;

	void setPads(float right, float bottom) {
		rightPad = right;
		bottomPad = bottom;
	}

	/** v6.10 (Viorel): the pin tapped: the map zooms on its parcel and a bubble shows its name. */
	private long focusId = Long.MIN_VALUE;

	long focused() {
		return focusId;
	}

	void focus(long id) {
		focusIn(id, 0, topPad);
	}

	/** v1.520: the same with the free room starting at LEFT and TOP (a bubble over the map). */
	void focusIn(long id, float left, float top) {
		top = Math.max(top, 0);
		Pin f = null;
		if (pins != null) for (Pin p : pins) if (p.id == id) f = p;
		if (f == null || getWidth() == 0) return;
		focusId = id;
		touched = true;
		double la0 = f.lat, la1 = f.lat, lo0 = f.lon, lo1 = f.lon;
		if (f.poly != null) for (int k = 0; k < f.poly.length / 2; k++) {
			la0 = Math.min(la0, f.poly[2 * k]);
			la1 = Math.max(la1, f.poly[2 * k]);
			lo0 = Math.min(lo0, f.poly[2 * k + 1]);
			lo1 = Math.max(lo1, f.poly[2 * k + 1]);
		}
		// The free part of the screen: under the top sheet, left of / above the cards; the pin
		// and its bubble need about 80 dp above the parcel.
		float fw = getWidth() - left - rightPad - 80 * dp, fh = getHeight() - top - bottomPad - 110 * dp;
		fw = Math.max(fw, 60 * dp);
		fh = Math.max(fh, 60 * dp);
		double z = 18;
		for (; z > MIN_Z; z -= 0.25) {
			double world = tile * Math.pow(2, z);
			if (Math.abs(wx(lo1, world) - wx(lo0, world)) <= fw && Math.abs(wy(la0, world) - wy(la1, world)) <= fh) break;
		}
		setZoom(z);
		double world = tile * Math.pow(2, zoom);
		double bx = (wx(lo0, world) + wx(lo1, world)) / 2, by = (wy(la0, world) + wy(la1, world)) / 2;
		// Where the parcel's middle should stand on the screen (the free part's middle, a bit
		// lower for the pin), and so the map's middle.
		float sx = left + (getWidth() - left - rightPad) / 2f, sy = top + (getHeight() - top - bottomPad) / 2f + 35 * dp;
		double cx = bx - (sx - getWidth() / 2.0), cy = by - (sy - getHeight() / 2.0);
		cy = Math.max(0, Math.min(world, cy));
		lon = lonOf(((cx % world) + world) % world, world);
		lat = latOf(cy, world);
		invalidate();
		if (moved != null) moved.moved(lat, lon);
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
			// v6.10: the focused pin's name bubble counts as the pin.
			if (p.id == focusId && y > py - 70 * dp && y < py - 10 * dp && Math.abs(x - px) < 140 * dp) return p.id;
			double d = Math.hypot(px - x, py - y);
			if (d < bd) {
				bd = d;
				best = p.id;
			}
		}
		return best;
	}

	private MapTap mapTap;
	/** v5.40: the new parcel's pin (NaN: none). */
	private double markLat = Double.NaN, markLon = Double.NaN;

	/** v6.00: the new parcel's 4 corners (lat, lon × 4; null: none), moved with a finger. */
	interface CornersMoved {
		void moved(double[] corners);

		/** v6.10: a long press on point K. */
		void longPressed(int k);
	}

	private double[] corners;
	private CornersMoved cornersMoved;
	private int drag = -1;
	/** v6.10: a long press on a point (no move): its index, run after 550 ms. */
	private float downX, downY;
	private final Runnable longPress = new Runnable() {
		public void run() {
			if (drag >= 0 && cornersMoved != null) {
				int k = drag;
				drag = -1;
				lastX = downX;
				lastY = downY;
				performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
				cornersMoved.longPressed(k);
			}
		}
	};

	void setCorners(double[] c, CornersMoved cb) {
		corners = c == null ? null : c.clone();
		cornersMoved = cb;
		invalidate();
	}

	/** The lat, lon at the view's pixel X, Y. */
	double[] latLonAt(float x, float y) {
		double world = tile * Math.pow(2, zoom);
		double left = wx(lon, world) - getWidth() / 2.0, top = wy(lat, world) - getHeight() / 2.0;
		double cy = Math.max(0, Math.min(world, top + y));
		return new double[] { latOf(cy, world), lonOf((((left + x) % world) + world) % world, world) };
	}

	/** The view's pixel of LA, LO. */
	float[] pixelOf(double la, double lo) {
		double world = tile * Math.pow(2, zoom);
		double left = wx(lon, world) - getWidth() / 2.0, top = wy(lat, world) - getHeight() / 2.0;
		return new float[] { (float) (wx(lo, world) - left), (float) (wy(la, world) - top) };
	}

	/**
	 * v6.00: the map's tiles only (no pins, radar, labels) at SCALE of the view's size, or null
	 * while a tile of the screen is still missing.
	 */
	Bitmap bareShot(float scale) {
		int w = getWidth(), h = getHeight();
		if (w == 0 || h == 0) return null;
		Bitmap b = Bitmap.createBitmap(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(b);
		c.scale(scale, scale);
		if (!drawTiles(c, w, h, true)) {
			b.recycle();
			return null;
		}
		return b;
	}

	/** v5.40: taps on the map go to T (null: the pins get them again). */
	void setOnMapTap(MapTap t) {
		mapTap = t;
	}

	/** v5.40: the new parcel's pin at LA, LO (NaN: none). */
	void setMark(double la, double lo) {
		markLat = la;
		markLon = lo;
		invalidate();
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
		touched = true;
		setZoom(zoom + dz);
	}

	/** v2.91: the farmer moved or zoomed the map (then it is not fitted again by itself). */
	private boolean touched;

	boolean touched() {
		return touched;
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
		// v6.00: a finger on a corner of the new parcel moves that corner.
		int am = e.getActionMasked();
		if (am == MotionEvent.ACTION_DOWN && corners != null) {
			drag = -1;
			float bd = 30 * dp;
			for (int k = 0; k < corners.length / 2; k++) {
				float[] p = pixelOf(corners[2 * k], corners[2 * k + 1]);
				float d = (float) Math.hypot(p[0] - e.getX(), p[1] - e.getY());
				if (d < bd) {
					bd = d;
					drag = k;
				}
			}
		}
		if (am == MotionEvent.ACTION_DOWN && drag >= 0) {
			downX = e.getX();
			downY = e.getY();
			removeCallbacks(longPress);
			postDelayed(longPress, 550);
		}
		if (drag < 0 && am != MotionEvent.ACTION_DOWN) removeCallbacks(longPress);
		if (drag >= 0) {
			if (am == MotionEvent.ACTION_MOVE && Math.hypot(e.getX() - downX, e.getY() - downY) > 8 * dp) removeCallbacks(longPress);
			if (am == MotionEvent.ACTION_MOVE && e.getPointerCount() == 1 && Math.hypot(e.getX() - downX, e.getY() - downY) > 4 * dp) {
				double[] ll = latLonAt(e.getX(), e.getY());
				corners[2 * drag] = ll[0];
				corners[2 * drag + 1] = ll[1];
				invalidate();
			} else if (am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL || am == MotionEvent.ACTION_POINTER_DOWN) {
				removeCallbacks(longPress);
				if (cornersMoved != null) cornersMoved.moved(corners.clone());
				drag = -1;
			}
			return true;
		}
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
			touched = true;
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

	/** The tiles of the screen; BARE: only loaded ones (no enlarged stand-ins). True when all were there. */
	private boolean drawTiles(Canvas c, int w, int h, boolean bare) {
		boolean all = true;
		c.drawColor(night ? 0xFF4A4F55 : dark ? 0xFF3A3A38 : 0xFFE8E4DC);
		if (night && style == 0) paint.setColorFilter(names ? NIGHT_MINT : NIGHT);
		else if (names && style == 0) paint.setColorFilter(MINT);
		int z = (int) Math.round(Math.max(MIN_Z, Math.min(MAX_Z, zoom)));
		float ts = (float) (tile * Math.pow(2, zoom - z));
		double world = ts * Math.pow(2, z);
		double cx = wx(lon, world), cy = wy(lat, world);
		double left = cx - w / 2.0, top = cy - h / 2.0;
		int n = 1 << z;
		int tx0 = (int) Math.floor(left / ts), ty0 = (int) Math.floor(top / ts);
		int tx1 = (int) Math.floor((left + w) / ts), ty1 = (int) Math.floor((top + h) / ts);
		RectF dst = new RectF();
		for (int ty = ty0; ty <= ty1; ty++) {
			if (ty < 0 || ty >= n) continue;
			for (int tx = tx0; tx <= tx1; tx++) {
				int x = ((tx % n) + n) % n;
				float px = (float) (tx * ts - left), py = (float) (ty * ts - top);
				dst.set(px, py, px + ts + 0.5f, py + ts + 0.5f);
				Bitmap b = get(pre() + z + "/" + x + "/" + ty);
				if (b != null) {
					c.drawBitmap(b, null, dst, paint);
					continue;
				}
				all = false;
				if (bare) continue;
				// Meanwhile the tile one level up, enlarged.
				if (z > MIN_Z) {
					Bitmap up = mem.get(pre() + (z - 1) + "/" + (x / 2) + "/" + (ty / 2));
					if (up != null) {
						int hw = up.getWidth() / 2;
						Rect half = new Rect((x % 2) * hw, (ty % 2) * hw, (x % 2) * hw + hw, (ty % 2) * hw + hw);
						c.drawBitmap(up, half, dst, paint);
					}
				}
			}
		}
		paint.setColorFilter(null);
		return all;
	}

	@Override
	protected void onDraw(Canvas c) {
		int w = getWidth(), h = getHeight();
		drawTiles(c, w, h, false);
		int z = (int) Math.round(Math.max(MIN_Z, Math.min(MAX_Z, zoom)));
		float ts = (float) (tile * Math.pow(2, zoom - z));
		double world = ts * Math.pow(2, z);
		double cx = wx(lon, world), cy = wy(lat, world);
		double left = cx - w / 2.0, top = cy - h / 2.0;
		drawLayer(c, w, h, world, left, top);
		drawTowns(c, w, h, world, left, top);
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
		if (quake != null) drawQuake(c, world, left, top);
		else if (pins == null) drawPin(c, w / 2f, h / 2f);
		else {
			paint.setTextSize(13 * dp);
			paint.setFakeBoldText(true);
			for (Pin p : pins) if (p.poly != null) drawPoly(c, p.poly, world, left, top, false);
			for (Pin p : pins) {
				float px = (float) (wx(p.lon, world) - left), py = (float) (wy(p.lat, world) - top);
				if (px < -60 * dp || px > w + 60 * dp || py < -10 * dp || py > h + 50 * dp) continue;
				drawPin(c, px, py);
				if (p.id == focusId) {
					drawFocus(c, p.name, px, py - 42 * dp);
					paint.setTextSize(13 * dp);
					paint.setFakeBoldText(true);
					continue;
				}
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
		if (corners != null) drawPoly(c, corners, world, left, top, true);
		if (!Double.isNaN(markLat)) drawPin(c, (float) (wx(markLon, world) - left), (float) (wy(markLat, world) - top));
		// The attribution the OpenStreetMap licence asks for.
		drawLayerInfo(c, w, h, world, left, top);
		String att = (style == 1 ? "Imagery © Esri, Maxar, Earthstar Geographics" : names ? "Esri, HERE, Garmin, © OpenStreetMap contributors" : "© OpenStreetMap contributors") + (layer != 0 ? Tr.t(" · Weather: Open-Meteo", " · Vremea: Open-Meteo") : "");
		paint.setTextSize(11 * dp);
		float aw = paint.measureText(att);
		paint.setColor(0xCCFFFFFF);
		c.drawRect(w - aw - 10 * dp, h - 18 * dp, w, h, paint);
		paint.setColor(0xFF333333);
		c.drawText(att, w - aw - 5 * dp, h - 5 * dp, paint);
	}

	// --- v2.030 (Viorel, the quake of 2026-11-18): an earthquake on the map -------------------------

	/** v2.152 (Viorel): the map in the dark theme only a little dimmer (no longer inverted), the names easy to read. */
	private static final android.graphics.ColorMatrixColorFilter NIGHT = new android.graphics.ColorMatrixColorFilter(new float[] {
			0.80f, 0, 0, 0, 0,
			0, 0.80f, 0, 0, 0,
			0, 0, 0.80f, 0, 0,
			0, 0, 0, 1, 0 });
	private boolean night;
	/** v2.161 (Viorel, like the phone's weather map): the plain grey base of the towns' map, light mint; dimmer when dark. */
	private static final android.graphics.ColorMatrixColorFilter MINT = new android.graphics.ColorMatrixColorFilter(new float[] {
			0.90f, 0, 0, 0, 0,
			0, 1.06f, 0, 0, 0,
			0, 0, 0.97f, 0, 0,
			0, 0, 0, 1, 0 });
	private static final android.graphics.ColorMatrixColorFilter NIGHT_MINT = new android.graphics.ColorMatrixColorFilter(new float[] {
			0.72f, 0, 0, 0, 0,
			0, 0.85f, 0, 0, 0,
			0, 0, 0.78f, 0, 0,
			0, 0, 0, 1, 0 });

	/** v2.030: dark tiles (the earthquake's map in the dark theme). */
	void setNight(boolean on) {
		night = on;
		invalidate();
	}

	/**
	 * What the earthquake's map shows: the epicentre, the town with the dashed line and the
	 * distance between them, and the circles where the estimated intensity is LAB[i] or more (KM[i]
	 * from the epicentre, colour COL[i]).
	 */
	static final class QuakeMark {
		double lat, lon, tLat = Double.NaN, tLon = Double.NaN;
		String town = "", dist = "", epi = "";
		double[] km = new double[0];
		String[] lab = new String[0];
		int[] col = new int[0];
	}

	private QuakeMark quake;

	void setQuake(QuakeMark q) {
		quake = q;
		pins = new java.util.ArrayList<Pin>();
		invalidate();
	}

	/**
	 * Zoomed on the earthquake inside the map less L, T, R, B pixels: WIDE shows its largest circle
	 * (up to 700 km), else the epicentre and the town with some room round them.
	 */
	/** v2.032: the earthquake's fit waiting for the map's size. */
	private Runnable fitLater;

	@Override
	protected void onSizeChanged(int w, int h, int ow, int oh) {
		super.onSizeChanged(w, h, ow, oh);
		if (w > 0 && h > 0 && fitLater != null) {
			final Runnable f = fitLater;
			fitLater = null;
			post(f);
		}
	}

	void fitQuake(boolean wide, float l, float t, float r, float b) {
		if (quake == null) return;
		if (getWidth() == 0 || getHeight() == 0) {
			// v2.032 (Viorel: the first bubble showed the town, not the quake): fit once the map
			// has its size (onSizeChanged), however long the bubble takes to be laid out.
			final boolean w2 = wide;
			final float l2 = l, t2 = t, r2 = r, b2 = b;
			fitLater = new Runnable() {
				public void run() {
					fitQuake(w2, l2, t2, r2, b2);
				}
			};
			return;
		}
		fitLater = null;
		QuakeMark q = quake;
		double la0 = q.lat, la1 = q.lat, lo0 = q.lon, lo1 = q.lon;
		boolean town = !Double.isNaN(q.tLat);
		if (town) {
			la0 = Math.min(la0, q.tLat);
			la1 = Math.max(la1, q.tLat);
			lo0 = Math.min(lo0, q.tLon);
			lo1 = Math.max(lo1, q.tLon);
		}
		double rk = 60;
		if (wide) for (double k : q.km) if (k <= 700) rk = Math.max(rk, k);
		double cos = Math.max(0.2, Math.cos(Math.toRadians(q.lat)));
		double dla = rk / 111.2, dlo = rk / (111.2 * cos);
		if (wide) {
			la0 = Math.min(la0, q.lat - dla);
			la1 = Math.max(la1, q.lat + dla);
			lo0 = Math.min(lo0, q.lon - dlo);
			lo1 = Math.max(lo1, q.lon + dlo);
		} else {
			// The two points with a quarter more round them (at least 40 km).
			double ma = Math.max((la1 - la0) * 0.25, 40 / 111.2), mo = Math.max((lo1 - lo0) * 0.25, 40 / (111.2 * cos));
			la0 -= ma;
			la1 += ma;
			lo0 -= mo;
			lo1 += mo;
		}
		touched = false;
		fitCorners(new double[] { la0, lo0, la1, lo1 }, q.lat, q.lon, l, t, r, b);
	}

	private void drawQuake(Canvas c, double world, double left, double top) {
		QuakeMark q = quake;
		float ex = (float) (wx(q.lon, world) - left), ey = (float) (wy(q.lat, world) - top);
		// Pixels per km at the epicentre's latitude.
		double pk = world / (40075.0 * Math.cos(Math.toRadians(q.lat)));
		Paint p = paint;
		p.setStyle(Paint.Style.FILL);
		android.graphics.DashPathEffect dash = new android.graphics.DashPathEffect(new float[] { 6 * dp, 5 * dp }, 0);
		// The circles, the largest first.
		for (int i = q.km.length - 1; i >= 0; i--) {
			float r = (float) (q.km[i] * pk);
			if (r < 4 * dp) continue;
			p.setStyle(Paint.Style.FILL);
			p.setColor((q.col[i] & 0x00FFFFFF) | 0x16000000);
			c.drawCircle(ex, ey, r, p);
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(1.8f * dp);
			p.setPathEffect(dash);
			p.setColor((q.col[i] & 0x00FFFFFF) | 0xD0000000);
			c.drawCircle(ex, ey, r, p);
			p.setPathEffect(null);
		}
		// Their labels, up and to the right on each circle, where they are on the map.
		p.setStyle(Paint.Style.FILL);
		p.setTextSize(11 * dp);
		p.setFakeBoldText(true);
		for (int i = 0; i < q.km.length; i++) {
			float r = (float) (q.km[i] * pk);
			if (r < 14 * dp) continue;
			float lx = ex + r * 0.819f, ly = ey - r * 0.574f;
			if (lx < 12 * dp || lx > getWidth() - 12 * dp || ly < 10 * dp || ly > getHeight() - 10 * dp) {
				lx = ex;
				ly = ey - r;
				if (ly < 10 * dp) continue;
			}
			float tw = p.measureText(q.lab[i]), hw = Math.max(11 * dp, tw / 2 + 6 * dp);
			p.setColor(0x66000000);
			c.drawRoundRect(new RectF(lx - hw, ly - 8 * dp + 1.5f * dp, lx + hw, ly + 8 * dp + 1.5f * dp), 8 * dp, 8 * dp, p);
			p.setColor(q.col[i]);
			c.drawRoundRect(new RectF(lx - hw, ly - 8 * dp, lx + hw, ly + 8 * dp), 8 * dp, 8 * dp, p);
			p.setColor(0xFF111111);
			c.drawText(q.lab[i], lx - tw / 2, ly + 4 * dp, p);
		}
		float tx = Float.NaN, ty = Float.NaN;
		if (!Double.isNaN(q.tLat)) {
			tx = (float) (wx(q.tLon, world) - left);
			ty = (float) (wy(q.tLat, world) - top);
			// The dashed line to the town and the distance in a pill in its middle.
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(2 * dp);
			p.setPathEffect(new android.graphics.DashPathEffect(new float[] { 5 * dp, 5 * dp }, 0));
			p.setColor(0x80000000);
			c.drawLine(ex + dp, ey + dp, tx + dp, ty + dp, p);
			p.setColor(0xF0FFFFFF);
			c.drawLine(ex, ey, tx, ty, p);
			p.setPathEffect(null);
			p.setStyle(Paint.Style.FILL);
			if (q.dist.length() > 0 && Math.hypot(tx - ex, ty - ey) > 60 * dp) {
				p.setTextSize(13 * dp);
				p.setFakeBoldText(true);
				float tw = p.measureText(q.dist), mx = (ex + tx) / 2 + tw / 2 + 12 * dp, my = (ey + ty) / 2;
				RectF pill = new RectF(mx - tw / 2 - 10 * dp, my - 12 * dp, mx + tw / 2 + 10 * dp, my + 12 * dp);
				p.setColor(0xEE0F172A);
				c.drawRoundRect(pill, 12 * dp, 12 * dp, p);
				p.setStyle(Paint.Style.STROKE);
				p.setStrokeWidth(dp);
				p.setColor(0x80FFFFFF);
				c.drawRoundRect(pill, 12 * dp, 12 * dp, p);
				p.setStyle(Paint.Style.FILL);
				p.setColor(0xFFFFFFFF);
				c.drawText(q.dist, mx - tw / 2, my + 4.5f * dp, p);
			}
		}
		// The epicentre: a soft red glow, a ring and a red disc with a seismograph's line.
		p.setStyle(Paint.Style.FILL);
		p.setShader(new android.graphics.RadialGradient(ex, ey, 30 * dp, 0x88EF4444, 0x00EF4444, android.graphics.Shader.TileMode.CLAMP));
		c.drawCircle(ex, ey, 30 * dp, p);
		p.setShader(null);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(2 * dp);
		p.setColor(0x99EF4444);
		c.drawCircle(ex, ey, 15 * dp, p);
		p.setStyle(Paint.Style.FILL);
		p.setColor(0xFFEF4444);
		c.drawCircle(ex, ey, 10 * dp, p);
		p.setStyle(Paint.Style.STROKE);
		p.setColor(0xFFFFFFFF);
		p.setStrokeWidth(2 * dp);
		c.drawCircle(ex, ey, 10 * dp, p);
		Path zz = new Path();
		zz.moveTo(ex - 6 * dp, ey);
		zz.lineTo(ex - 3.5f * dp, ey);
		zz.lineTo(ex - 2 * dp, ey - 4 * dp);
		zz.lineTo(ex + 0.5f * dp, ey + 4 * dp);
		zz.lineTo(ex + 2.5f * dp, ey - 1.5f * dp);
		zz.lineTo(ex + 3.5f * dp, ey);
		zz.lineTo(ex + 6 * dp, ey);
		p.setStrokeWidth(1.5f * dp);
		p.setStrokeJoin(Paint.Join.ROUND);
		c.drawPath(zz, p);
		p.setStyle(Paint.Style.FILL);
		p.setTextSize(12 * dp);
		p.setFakeBoldText(true);
		if (q.epi.length() > 0) {
			float tw = p.measureText(q.epi);
			float lx = ex - 16 * dp - tw, ly = ey - 14 * dp;
			if (lx < 4 * dp) lx = ex + 16 * dp;
			p.setColor(0xB0000000);
			c.drawText(q.epi, lx + dp, ly + dp, p);
			p.setColor(0xFFFECACA);
			c.drawText(q.epi, lx, ly, p);
		}
		// The town: a blue pin with its name.
		if (!Float.isNaN(tx)) {
			float r = 8.5f * dp, cy = ty - 15 * dp;
			Path pin = new Path();
			pin.moveTo(tx, ty);
			pin.lineTo(tx - r * 0.75f, cy + r * 0.55f);
			pin.lineTo(tx + r * 0.75f, cy + r * 0.55f);
			pin.close();
			p.setColor(0x55000000);
			c.drawOval(new RectF(tx - 5 * dp, ty - 2 * dp, tx + 5 * dp, ty + 2 * dp), p);
			p.setColor(0xFF60A5FA);
			c.drawPath(pin, p);
			c.drawCircle(tx, cy, r, p);
			p.setStyle(Paint.Style.STROKE);
			p.setColor(0xFFFFFFFF);
			p.setStrokeWidth(1.6f * dp);
			c.drawCircle(tx, cy, r, p);
			p.setStyle(Paint.Style.FILL);
			c.drawCircle(tx, cy, 3 * dp, p);
			p.setTextSize(13 * dp);
			float tw = p.measureText(q.town), lx = tx + 12 * dp;
			if (lx + tw > getWidth() - 4 * dp) lx = tx - 12 * dp - tw;
			p.setColor(0xB0000000);
			c.drawText(q.town, lx + dp, cy + 5 * dp + dp, p);
			p.setColor(night || dark ? 0xFFFFFFFF : 0xFF0F172A);
			c.drawText(q.town, lx, cy + 5 * dp, p);
		}
		p.setFakeBoldText(false);
		p.setStrokeJoin(Paint.Join.MITER);
	}

	/** v6.10: the focused pin's bubble with its name, its tip at X, Y (above the pin's head). */
	private void drawFocus(Canvas c, String name, float x, float y) {
		paint.setTextSize(16 * dp);
		paint.setFakeBoldText(true);
		String nm = name.length() > 28 ? name.substring(0, 27) + "…" : name;
		float tw = paint.measureText(nm), h = 34 * dp, r = 14 * dp;
		float l = Math.max(6 * dp, Math.min(getWidth() - rightPad - tw - 34 * dp, x - tw / 2 - 14 * dp));
		RectF b = new RectF(l, y - 8 * dp - h, l + tw + 28 * dp, y - 8 * dp);
		Path tip = new Path();
		tip.moveTo(x - 8 * dp, b.bottom - 1);
		tip.lineTo(x, y);
		tip.lineTo(x + 8 * dp, b.bottom - 1);
		tip.close();
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(0x33000000);
		c.drawRoundRect(new RectF(b.left, b.top + 2 * dp, b.right, b.bottom + 2 * dp), r, r, paint);
		paint.setColor(dark ? 0xEE2E2E2C : 0xF2FFFFFF);
		c.drawRoundRect(b, r, r, paint);
		c.drawPath(tip, paint);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(1 * dp);
		paint.setColor(dark ? 0x55FFFFFF : 0x22000000);
		c.drawRoundRect(b, r, r, paint);
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(dark ? 0xFFF2F0EA : 0xFF1A1A1A);
		Paint.FontMetrics fm = paint.getFontMetrics();
		c.drawText(nm, b.left + 14 * dp, b.centerY() - (fm.ascent + fm.descent) / 2, paint);
		paint.setFakeBoldText(false);
	}

	/** v6.00: a parcel's outline (4 corners); HANDLES: the new parcel's, with round handles to move. */
	private void drawPoly(Canvas c, double[] q, double world, double left, double top, boolean handles) {
		android.graphics.Path p = new android.graphics.Path();
		for (int k = 0; k < q.length / 2; k++) {
			float x = (float) (wx(q[2 * k + 1], world) - left), y = (float) (wy(q[2 * k], world) - top);
			if (k == 0) p.moveTo(x, y);
			else p.lineTo(x, y);
		}
		p.close();
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(handles ? 0x40FFD54F : 0x30FFD54F);
		c.drawPath(p, paint);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(2.5f * dp);
		paint.setColor(0xFFFFD54F);
		c.drawPath(p, paint);
		if (handles) for (int k = 0; k < q.length / 2; k++) {
			float x = (float) (wx(q[2 * k + 1], world) - left), y = (float) (wy(q[2 * k], world) - top);
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(0xFFFFFFFF);
			c.drawCircle(x, y, 10 * dp, paint);
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(3 * dp);
			paint.setColor(0xFFE0A800);
			c.drawCircle(x, y, 10 * dp, paint);
		}
		paint.setStyle(Paint.Style.FILL);
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

	// --- v5.50 (Viorel): satellite view; v2.160: weather layers (clouds, temperature, rain) -----------------------

	/** 0 the map (OpenStreetMap), 1 the satellite view (Esri). */
	private int style;

	private String pre() {
		return style == 1 ? "s/" : names ? "g/" : "";
	}

	/** v2.152 (Viorel): the towns' map (search, earthquakes): mostly the names; the parcels keep the detailed OpenStreetMap. */
	private boolean names;

	void setNames(boolean on) {
		names = on;
		invalidate();
	}

	void setStyle(int s) {
		if (style == s) return;
		style = s;
		invalidate();
	}

	int style() {
		return style;
	}

	/**
	 * v2.160 (Viorel): one weather layer over the parcels' map at a time: 1 clouds, 2 temperature,
	 * 3 rain (0 none). The values come from Open-Meteo (no key) for a grid of points round the view;
	 * the grid is drawn as a small picture stretched over its area, so the colours flow smoothly.
	 */
	static final int L_NONE = 0, L_CLOUD = 1, L_TEMP = 2, L_RAIN = 3;
	private int layer;
	private boolean imperial;
	/** The grid: its edges, rows and columns (points at the cells' middles), the values, when and at what zoom. */
	private double gN, gS, gW, gE;
	private int gRows, gCols;
	private float[] gTemp, gCloud, gRainNow, gRainMax;
	private long gAt;
	private double gZoom;
	private String gTime = "", gUntil = "";
	private boolean gBusy;
	private Bitmap gPic;
	private int gPicKind = -1;
	private Runnable onGrid;
	private static final int G_N = 7;

	void setLayer(int k) {
		layer = k;
		gPicKind = -1;
		if (k != L_NONE) wantGrid();
		invalidate();
	}

	int layer() {
		return layer;
	}

	void setImperial(boolean on) {
		imperial = on;
	}

	/** R runs (on the main thread) each time new values arrive. */
	void setOnGrid(Runnable r) {
		onGrid = r;
	}

	/** True when the values are known. */
	boolean gridKnown() {
		return gRainMax != null;
	}

	/** True when it rains (or snows) somewhere on the grid now or in the next 2 hours (at least 0.1 mm per hour). */
	boolean rainNear() {
		if (gRainMax == null) return false;
		for (float v : gRainMax) if (v >= 0.1f) return true;
		return false;
	}

	/** Asks for the values when the view is not covered by the grid, the zoom changed much, or they are 15 minutes old. */
	void wantGrid() {
		ui.removeCallbacks(gAsk);
		ui.postDelayed(gAsk, 600);
	}

	private final Runnable gAsk = new Runnable() {
		public void run() {
			int w = getWidth(), h = getHeight();
			if (w == 0 || h == 0) {
				ui.postDelayed(this, 500);
				return;
			}
			double world = tile * Math.pow(2, zoom), cx = wx(lon, world), cy = wy(lat, world);
			double n = latAt(cy - h / 2.0, world), s = latAt(cy + h / 2.0, world), we = lon - w / 2.0 / world * 360, e = lon + w / 2.0 / world * 360;
			boolean inside = gRainMax != null && n <= gN && s >= gS && we >= gW && e <= gE;
			if (inside && Math.abs(zoom - gZoom) < 1.5 && System.currentTimeMillis() - gAt < 15 * 60000L) return;
			if (gBusy) {
				ui.postDelayed(this, 1500);
				return;
			}
			// A quarter more on each side, so a little moving needs nothing new.
			double dLat = (n - s) * 0.25, dLon = (e - we) * 0.25;
			fetchGrid(Math.min(85, n + dLat), Math.max(-85, s - dLat), we - dLon, e + dLon, zoom);
		}
	};

	private static double latAt(double y, double world) {
		double t = Math.PI - 2 * Math.PI * y / world;
		return Math.toDegrees(Math.atan(Math.sinh(t)));
	}

	/** Off the main thread: the values of the G_N x G_N points from Open-Meteo (one request for all). */
	private void fetchGrid(final double n, final double s, final double w, final double e, final double z) {
		gBusy = true;
		new Thread(new Runnable() {
			public void run() {
				try {
					StringBuilder la = new StringBuilder(), lo = new StringBuilder();
					java.util.Locale us = java.util.Locale.US;
					for (int r = 0; r < G_N; r++)
						for (int k = 0; k < G_N; k++) {
							if (la.length() > 0) {
								la.append(',');
								lo.append(',');
							}
							la.append(String.format(us, "%.3f", n - (r + 0.5) * (n - s) / G_N));
							lo.append(String.format(us, "%.3f", w + (k + 0.5) * (e - w) / G_N));
						}
					String url = "https://api.open-meteo.com/v1/forecast?latitude=" + la + "&longitude=" + lo
							+ "&current=temperature_2m,cloud_cover,precipitation&minutely_15=precipitation&forecast_minutely_15=9&timezone=auto";
					byte[] b = fetch(url);
					if (b == null) throw new Exception("no answer");
					String txt = new String(b, "UTF-8").trim();
					org.json.JSONArray arr = txt.startsWith("[") ? new org.json.JSONArray(txt) : new org.json.JSONArray().put(new org.json.JSONObject(txt));
					int m = G_N * G_N;
					final float[] t = new float[m], cl = new float[m], rn = new float[m], rx = new float[m];
					String time = "", until = "";
					for (int i = 0; i < m && i < arr.length(); i++) {
						org.json.JSONObject o = arr.getJSONObject(i), cur = o.getJSONObject("current");
						t[i] = (float) cur.optDouble("temperature_2m", 0);
						cl[i] = (float) cur.optDouble("cloud_cover", 0);
						// Millimetres in 15 minutes, as millimetres per hour.
						rn[i] = (float) cur.optDouble("precipitation", 0) * 4;
						float mx = rn[i];
						org.json.JSONObject q = o.optJSONObject("minutely_15");
						if (q != null) {
							org.json.JSONArray pv = q.optJSONArray("precipitation"), tv = q.optJSONArray("time");
							for (int j = 0; pv != null && j < pv.length(); j++) mx = Math.max(mx, (float) pv.optDouble(j, 0) * 4);
							if (i == 0 && tv != null && tv.length() > 0) until = tv.optString(tv.length() - 1);
						}
						rx[i] = mx;
						if (i == 0) time = cur.optString("time", "");
					}
					final String tm = hhmm(time), un = hhmm(until);
					ui.post(new Runnable() {
						public void run() {
							gN = n;
							gS = s;
							gW = w;
							gE = e;
							gRows = gCols = G_N;
							gTemp = t;
							gCloud = cl;
							gRainNow = rn;
							gRainMax = rx;
							gAt = System.currentTimeMillis();
							gZoom = z;
							gTime = tm;
							gUntil = un;
							gPicKind = -1;
							gBusy = false;
							invalidate();
							if (onGrid != null) onGrid.run();
						}
					});
				} catch (Exception ex) {
					ui.post(new Runnable() {
						public void run() {
							gBusy = false;
							gAt = System.currentTimeMillis() - 14 * 60000L; // again in a minute
						}
					});
				}
			}
		}).start();
	}

	/** "2026-11-25T07:00" → "07:00". */
	private static String hhmm(String iso) {
		int i = iso.indexOf('T');
		return i >= 0 && iso.length() >= i + 6 ? iso.substring(i + 1, i + 6) : "";
	}

	/** The temperature scale: violet and blue when cold, then teal, green, yellow, orange and red when hot (°C). */
	private static final float[] T_AT = { -20, -10, -3, 0, 4, 10, 18, 26, 34 };
	private static final int[] T_COL = { 0x783CAA, 0x4650C8, 0x3C82DC, 0x46AAD2, 0x50BE96, 0x78C85A, 0xE6D246, 0xF0963C, 0xD23C32 };
	/** The rain scale, millimetres per hour: light blue to dark blue, violet when torrential (no warning colours). */
	private static final float[] R_AT = { 0.1f, 1, 3, 8, 15 };
	private static final int[] R_COL = { 0x96C8FA, 0x5096EB, 0x285AD2, 0x3C28AA, 0x8232AA };

	private static int scale(float v, float[] at, int[] col) {
		if (v <= at[0]) return col[0];
		for (int i = 1; i < at.length; i++)
			if (v <= at[i]) {
				float f = (v - at[i - 1]) / (at[i] - at[i - 1]);
				int a = col[i - 1], b = col[i];
				int r = Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f), g = Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f),
						bl = Math.round((a & 255) * (1 - f) + (b & 255) * f);
				return r << 16 | g << 8 | bl;
			}
		return col[col.length - 1];
	}

	/** The layer under the pins: the grid as a small picture, stretched (and smoothed) over its area. */
	private void drawLayer(Canvas c, int w, int h, double world, double left, double top) {
		if (layer == L_NONE || gRainMax == null) return;
		if (gPicKind != layer || gPic == null) {
			int[] px = new int[gRows * gCols];
			for (int i = 0; i < px.length; i++) {
				int a, rgb;
				if (layer == L_CLOUD) {
					a = Math.round(Math.max(0, Math.min(100, gCloud[i])) * 1.7f);
					rgb = 0x6E737D;
				} else if (layer == L_TEMP) {
					a = 140;
					rgb = scale(gTemp[i], T_AT, T_COL);
				} else {
					float v = gRainMax[i];
					a = v < 0.1f ? 0 : Math.round(Math.min(190, 110 + v * 12));
					rgb = scale(v, R_AT, R_COL);
				}
				px[i] = a << 24 | rgb;
			}
			gPic = Bitmap.createBitmap(px, gCols, gRows, Bitmap.Config.ARGB_8888);
			gPicKind = layer;
		}
		float x0 = (float) (wx(gW, world) - left), x1 = (float) (wx(gE, world) - left), y0 = (float) (wy(gN, world) - top), y1 = (float) (wy(gS, world) - top);
		Paint gp = new Paint(Paint.FILTER_BITMAP_FLAG);
		c.drawBitmap(gPic, null, new RectF(x0, y0, x1, y1), gp);
		if (!gBusy) wantGrid();
	}

	/** The layer's label at the top (its name and the time) and its legend at the bottom, above the parcels' cards. */
	private void drawLayerInfo(Canvas c, int w, int h, double world, double left, double top) {
		if (layer == L_NONE) return;
		String name = layer == L_CLOUD ? Tr.t("Clouds", "Nori") : layer == L_TEMP ? Tr.t("Temperatures", "Temperaturi") : Tr.t("Rain", "Ploi");
		// v2.161 (Viorel): the name and the time stand in the legend, above the cards, no longer at the top.
		String t = name + (gTime.length() > 0 ? " " + gTime + (layer == L_RAIN && gUntil.length() > 0 ? "–" + gUntil : "") : "");
		float lw = 230 * dp, bh = 7 * dp, cx = (w - rightPad) / 2f, bottom = h - Math.max(bottomPad, 22 * dp) - 8 * dp;
		int ink = dark ? 0xFFFAF9F5 : 0xFF1A1A1A;
		if (gRainMax == null) {
			paint.setTextSize(14 * dp);
			paint.setFakeBoldText(true);
			String wait = t + " · " + Tr.t("loading…", "se încarcă…");
			float tw = paint.measureText(wait);
			pill(c, new RectF(cx - tw / 2 - 14 * dp, bottom - 32 * dp, cx + tw / 2 + 14 * dp, bottom));
			paint.setColor(ink);
			c.drawText(wait, cx - tw / 2, bottom - 11 * dp, paint);
			paint.setFakeBoldText(false);
			return;
		}
		// The legend: in the middle of the map's free part, above the cards (portrait) or the attribution.
		float lh = 78 * dp, lt = bottom - lh, bx = cx - lw / 2;
		pill(c, new RectF(bx - 12 * dp, lt, bx + lw + 12 * dp, bottom));
		paint.setTextSize(14 * dp);
		paint.setFakeBoldText(true);
		paint.setColor(ink);
		float tw = paint.measureText(t);
		c.drawText(t, cx - tw / 2, lt + 20 * dp, paint);
		paint.setFakeBoldText(false);
		float by = lt + 44 * dp;
		paint.setTextSize(11 * dp);
		paint.setColor(dark ? 0xFFFAF9F5 : 0xFF1A1A1A);
		String head;
		String[] ticks;
		float[] tickAt;
		if (layer == L_TEMP) {
			// Linear from -20 to 30 °C; a frame round the values seen on the map now.
			android.graphics.LinearGradient lg;
			int[] cols = new int[T_AT.length];
			float[] pos = new float[T_AT.length];
			for (int i = 0; i < T_AT.length; i++) {
				cols[i] = 0xFF000000 | T_COL[i];
				pos[i] = Math.max(0, Math.min(1, (T_AT[i] + 20) / 50f));
			}
			lg = new android.graphics.LinearGradient(bx, 0, bx + lw, 0, cols, pos, android.graphics.Shader.TileMode.CLAMP);
			paint.setShader(lg);
			c.drawRoundRect(new RectF(bx, by, bx + lw, by + bh), bh / 2, bh / 2, paint);
			paint.setShader(null);
			float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
			for (int rr = 0; rr < gRows; rr++)
				for (int k = 0; k < gCols; k++) {
					double la = gN - (rr + 0.5) * (gN - gS) / gRows, lo2 = gW + (k + 0.5) * (gE - gW) / gCols;
					float sx = (float) (wx(lo2, world) - left), sy = (float) (wy(la, world) - top);
					if (sx < 0 || sx > w || sy < 0 || sy > h) continue;
					float v = gTemp[rr * gCols + k];
					lo = Math.min(lo, v);
					hi = Math.max(hi, v);
				}
			if (lo > hi) {
				for (float v : gTemp) {
					lo = Math.min(lo, v);
					hi = Math.max(hi, v);
				}
			}
			float f0 = bx + Math.max(0, Math.min(1, (lo + 20) / 50f)) * lw, f1 = bx + Math.max(0, Math.min(1, (hi + 20) / 50f)) * lw;
			if (f1 - f0 < 6 * dp) {
				float m = (f0 + f1) / 2;
				f0 = m - 3 * dp;
				f1 = m + 3 * dp;
			}
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(1.6f * dp);
			paint.setColor(dark ? 0xFFFFFFFF : 0xFF111111);
			c.drawRoundRect(new RectF(f0 - 1.5f * dp, by - 3.5f * dp, f1 + 1.5f * dp, by + bh + 3.5f * dp), 3 * dp, 3 * dp, paint);
			paint.setStyle(Paint.Style.FILL);
			head = Tr.t("on the map: ", "pe hartă: ") + deg(lo) + " … " + deg(hi);
			ticks = new String[] { deg(-20), deg(-10), deg(0), deg(10), deg(20), deg(30) };
			tickAt = new float[] { 0, 0.2f, 0.4f, 0.6f, 0.8f, 1 };
		} else {
			int[] cols;
			if (layer == L_RAIN) {
				cols = new int[R_COL.length];
				for (int i = 0; i < cols.length; i++) cols[i] = 0xFF000000 | R_COL[i];
				head = Tr.t("mm per hour, now and in the next 2 hours", "mm pe oră, acum și în următoarele 2 ore");
				ticks = new String[] { "0,1", "1", "3", "8", "15+" };
				tickAt = new float[] { 0, 0.25f, 0.5f, 0.75f, 1 };
			} else {
				cols = new int[] { 0x206E737D, 0xFF6E737D };
				head = Tr.t("sky covered", "cer acoperit");
				ticks = new String[] { "0%", "50%", "100%" };
				tickAt = new float[] { 0, 0.5f, 1 };
			}
			paint.setShader(new android.graphics.LinearGradient(bx, 0, bx + lw, 0, cols, null, android.graphics.Shader.TileMode.CLAMP));
			c.drawRoundRect(new RectF(bx, by, bx + lw, by + bh), bh / 2, bh / 2, paint);
			paint.setShader(null);
		}
		paint.setColor(ink);
		paint.setFakeBoldText(false);
		float hw = paint.measureText(head);
		c.drawText(head, cx - hw / 2, lt + 36 * dp, paint);
		paint.setFakeBoldText(false);
		for (int i = 0; i < ticks.length; i++) {
			float x = bx + tickAt[i] * lw, tw2 = paint.measureText(ticks[i]);
			c.drawText(ticks[i], x - tw2 / 2, by + bh + 15 * dp, paint);
		}
	}

	/** A temperature in °C or °F, with its sign. */
	private String deg(float c) {
		return Math.round(imperial ? c * 9 / 5 + 32 : c) + "°";
	}

	/** A frosted pill (the label, the legend). */
	private void pill(Canvas c, RectF r) {
		float rad = Math.min(r.height() / 2, 16 * dp);
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(dark ? 0xE6262624 : 0xEFFFFFFF);
		c.drawRoundRect(r, rad, rad, paint);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(Math.max(1, dp));
		paint.setColor(dark ? 0x40FFFFFF : 0x30000000);
		c.drawRoundRect(r, rad, rad, paint);
		paint.setStyle(Paint.Style.FILL);
	}

	// --- v2.161 (Viorel): the towns' names big and clear over the map (the tiles' own are too small) ---

	/** Every locality of Romania: the towns of the app's list first, then the villages. */
	private static String[] tName, tCounty;
	private static float[] tLat, tLon;
	private static int tCities;
	private static volatile boolean tBusy;

	private void loadTowns() {
		if (tName != null || tBusy) return;
		tBusy = true;
		final Context ctx = getContext().getApplicationContext();
		new Thread(new Runnable() {
			public void run() {
				try {
					java.util.LinkedHashMap<String, float[]> m = new java.util.LinkedHashMap<String, float[]>();
					java.util.HashMap<String, String> mc = new java.util.HashMap<String, String>();
					String cities = Fetcher.readAll(ctx.getAssets().open("cities.txt"));
					for (String line : cities.split("\n")) {
						String[] p = line.trim().split("\\|");
						if (p.length < 4 || line.startsWith("#")) continue;
						try {
							m.put(p[1], new float[] { Float.parseFloat(p[2]), Float.parseFloat(p[3]) });
							if (!p[0].equals("*")) mc.put(p[1], p[0]);
						} catch (NumberFormatException e) {
							// a bad line
						}
					}
					int nc = m.size();
					ro.meteoalarm.android.core.Villages v = Places.villages(ctx);
					String[] n = new String[nc + v.size], cn = new String[nc + v.size];
					float[] la = new float[n.length], lo = new float[n.length];
					int k = 0;
					for (java.util.Map.Entry<String, float[]> e : m.entrySet()) {
						n[k] = e.getKey();
						cn[k] = mc.containsKey(e.getKey()) ? mc.get(e.getKey()) : "";
						la[k] = e.getValue()[0];
						lo[k++] = e.getValue()[1];
					}
					for (int i = 0; i < v.size; i++) {
						// The towns of the list are there already (a village of the same name far away stays).
						float[] c = m.get(v.name(i));
						if (c != null && Math.abs(c[0] - v.lat(i)) < 0.05f && Math.abs(c[1] - v.lon(i)) < 0.05f) {
							// A favourite ("*") gets its county from the full list.
							for (int j = 0; j < nc; j++)
								if (n[j].equals(v.name(i)) && cn[j].length() == 0) cn[j] = v.county(i);
							continue;
						}
						n[k] = v.name(i);
						cn[k] = v.county(i);
						la[k] = v.lat(i);
						lo[k++] = v.lon(i);
					}
					tLat = java.util.Arrays.copyOf(la, k);
					tLon = java.util.Arrays.copyOf(lo, k);
					tCities = nc;
					tCounty = java.util.Arrays.copyOf(cn, k);
					tName = java.util.Arrays.copyOf(n, k);
				} catch (Exception e) {
					tCounty = new String[0];
					tLat = tLon = new float[0];
					tName = new String[0];
				}
				tBusy = false;
				ui.post(new Runnable() {
					public void run() {
						invalidate();
					}
				});
			}
		}).start();
	}

	/**
	 * The names, big, with a light edge: the towns of the list from zoom 8, the villages from
	 * zoom 11; a name that would cover one already drawn is left out.
	 */
	private void drawTowns(Canvas c, int w, int h, double world, double left, double top) {
		tBoxes.clear();
		tIdx.clear();
		if (zoom < 8) return;
		if (tName == null) {
			loadTowns();
			return;
		}
		double n = latAt(top, world), s = latAt(top + h, world), we = lon - w / 2.0 / world * 360, e = lon + w / 2.0 / world * 360;
		float sp = getResources().getDisplayMetrics().scaledDensity;
		java.util.List<RectF> used = new java.util.ArrayList<RectF>();
		paint.setFakeBoldText(false);
		paint.setStrokeJoin(Paint.Join.ROUND);
		int end = zoom >= 11 ? tName.length : tCities, drawn = 0;
		for (int i = 0; i < end && drawn < 90; i++) {
			float la = tLat[i], lo = tLon[i];
			if (la > n || la < s || lo < we || lo > e) continue;
			float sx = (float) (wx(lo, world) - left), sy = (float) (wy(la, world) - top);
			paint.setTextSize((i < tCities ? 19 : 17) * sp);
			float tw = paint.measureText(tName[i]), th = paint.getTextSize();
			// Room round each name, so the map stays airy.
			// v2.162: under the town's point, so a pin set on the town leaves its name readable.
			float my = sy + th * 0.6f + 2 * dp;
			RectF r = new RectF(sx - tw / 2 - 10 * dp, my - th * 0.6f - 8 * dp, sx + tw / 2 + 10 * dp, my + th * 0.6f + 8 * dp);
			boolean free = true;
			for (RectF u : used)
				if (RectF.intersects(u, r)) {
					free = false;
					break;
				}
			if (!free) continue;
			used.add(r);
			tBoxes.add(new RectF(r.left + 4 * dp, r.top + 4 * dp, r.right - 4 * dp, r.bottom - 4 * dp));
			tIdx.add(i);
			drawn++;
			float by = my + th * 0.35f;
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(3 * dp);
			// v2.162 (Viorel): the names look the same in the dark theme too.
			paint.setColor(0xE6FFFFFF);
			c.drawText(tName[i], sx - tw / 2, by, paint);
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(0xFF2E3330);
			c.drawText(tName[i], sx - tw / 2, by, paint);
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		ui.removeCallbacks(gAsk);
	}

	/** Tile Z/X/Y from memory, else from the phone (then shown), else asked for (null meanwhile). */
	private Bitmap get(String key) {
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
		boolean sat = key.startsWith("s/"), voy = key.startsWith("g/");
		try {
			if (f.exists() && System.currentTimeMillis() - f.lastModified() < KEEP_MS) {
				Bitmap b = BitmapFactory.decodeFile(f.getPath());
				if (b != null) return b;
			}
			String url;
			if (sat) {
				// v5.50 (Viorel): the satellite view, Esri World Imagery (z/y/x).
				String[] k = key.substring(2).split("/");
				url = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/" + k[0] + "/" + k[2] + "/" + k[1];
			} else if (voy) {
				// v2.161 (Viorel): no API key: Esri's plain grey base, no names on it; the app writes them big.
				String[] k = key.substring(2).split("/");
				url = ESRI + "Canvas/World_Light_Gray_Base/MapServer/tile/" + k[0] + "/" + k[2] + "/" + k[1];
			} else url = "https://tile.openstreetmap.org/" + key + ".png";
			HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
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

	private static final String ESRI = "https://server.arcgisonline.com/ArcGIS/rest/services/";

	/** The bytes at URL (200 only), else null. */
	private static byte[] fetch(String url) {
		try {
			HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
			c.setConnectTimeout(15000);
			c.setReadTimeout(20000);
			c.setRequestProperty("User-Agent", UA);
			try {
				if (c.getResponseCode() != 200) return null;
				InputStream in = c.getInputStream();
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[16384];
				int n;
				while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
				in.close();
				return out.toByteArray();
			} finally {
				c.disconnect();
			}
		} catch (Exception e) {
			return null;
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
