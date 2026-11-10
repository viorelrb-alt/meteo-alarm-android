package ro.meteoalarm.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Hands the downloaded update to the system installer, and the exported diary (Log) to the app the
 * user shares it with (read only, with a granted permission).
 */
public final class ApkProvider extends ContentProvider {
	static final String AUTHORITY = "ro.meteoalarm.android.update";
	static final String NAME = "MeteoAlarmAndroid-update.apk";

	static File file(android.content.Context c) {
		File dir = new File(c.getCacheDir(), "update");
		dir.mkdirs();
		return new File(dir, NAME);
	}

	static Uri uri() {
		return Uri.parse("content://" + AUTHORITY + "/" + NAME);
	}

	/** The exported diary NAME (in the cache), shared as content://…/log/NAME. */
	static Uri logUri(String name) {
		return Uri.parse("content://" + AUTHORITY + "/log/" + name);
	}

	/** v4.10: the compass window's picture, shared as content://…/shot/busola.png. */
	static final String SHOT = "busola.png";

	static Uri shotUri() {
		return Uri.parse("content://" + AUTHORITY + "/shot/" + SHOT);
	}

	/** v4.30: the exported data file NAME (in the cache), shared as content://…/data/NAME. */
	static Uri dataUri(String name) {
		return Uri.parse("content://" + AUTHORITY + "/data/" + name);
	}

	private File target(Uri uri) {
		java.util.List<String> seg = uri.getPathSegments();
		if (seg.size() == 2 && seg.get(0).equals("shot") && seg.get(1).equals(SHOT)) return new File(new File(getContext().getCacheDir(), "shot"), SHOT);
		if (seg.size() == 2 && seg.get(0).equals("log") && seg.get(1).matches("[A-Za-z0-9._-]+\\.txt"))
			return new File(new File(getContext().getCacheDir(), "log"), seg.get(1));
		if (seg.size() == 2 && seg.get(0).equals("data") && seg.get(1).matches("[A-Za-z0-9._-]+\\.json"))
			return new File(new File(getContext().getCacheDir(), "data"), seg.get(1));
		return file(getContext());
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		return ParcelFileDescriptor.open(target(uri), ParcelFileDescriptor.MODE_READ_ONLY);
	}

	@Override
	public String getType(Uri uri) {
		String n = target(uri).getName();
		return n.endsWith(".txt") ? "text/plain" : n.endsWith(".json") ? "application/json" : n.endsWith(".png") ? "image/png" : "application/vnd.android.package-archive";
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String sel, String[] args, String sort) {
		MatrixCursor c = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
		File f = target(uri);
		c.addRow(new Object[] { f.getName(), f.length() });
		return c;
	}

	@Override
	public Uri insert(Uri uri, ContentValues v) {
		return null;
	}

	@Override
	public int delete(Uri uri, String sel, String[] args) {
		return 0;
	}

	@Override
	public int update(Uri uri, ContentValues v, String sel, String[] args) {
		return 0;
	}
}
