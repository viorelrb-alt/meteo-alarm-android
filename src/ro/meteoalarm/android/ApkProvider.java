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

/** Hands the downloaded update to the system installer (read only, with a granted permission). */
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

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		return ParcelFileDescriptor.open(file(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
	}

	@Override
	public String getType(Uri uri) {
		return "application/vnd.android.package-archive";
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String sel, String[] args, String sort) {
		MatrixCursor c = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
		c.addRow(new Object[] { NAME, file(getContext()).length() });
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
