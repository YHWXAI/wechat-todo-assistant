package com.wechattodo.assistant;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class UpdateApkProvider extends ContentProvider {
    private static final String NAME = "WeChatTodo-debug.apk";
    static final String MIME = "application/vnd.android.package-archive";

    static File updateFile(Context context) {
        return new File(context.getCacheDir(), "update-from-" + BuildConfig.VERSION_CODE + ".apk");
    }

    static Uri updateUri(Context context) {
        return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".update")
                .appendPath(NAME).build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return updateUri(getContext()).equals(uri) ? MIME : null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!updateUri(getContext()).equals(uri) || !"r".equals(mode)) {
            throw new FileNotFoundException();
        }
        return ParcelFileDescriptor.open(updateFile(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!updateUri(getContext()).equals(uri)) {
            return null;
        }
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor result = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = result.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(NAME);
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(updateFile(getContext()).length());
            } else {
                row.add(null);
            }
        }
        return result;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
