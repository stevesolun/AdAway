package org.adaway.model.source;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TestHostsContentProvider extends ContentProvider {
    private static final String SUCCESS_PATH = "/success.txt";
    private static final String SUCCESS_CONTENT =
            "0.0.0.0 fresh-success.example\n" +
            "0.0.0.0 second-success.example\n";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        if (!SUCCESS_PATH.equals(uri.getPath())) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        });
        cursor.addRow(new Object[]{
                "success.txt",
                SUCCESS_CONTENT.getBytes(StandardCharsets.UTF_8).length,
                System.currentTimeMillis()
        });
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "text/plain";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("insert");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("delete");
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("update");
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        if (!SUCCESS_PATH.equals(uri.getPath())) {
            throw new FileNotFoundException("Unknown test host source: " + uri);
        }
        File file = new File(getRequiredContext().getCacheDir(), "adaway-test-success-hosts.txt");
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            outputStream.write(SUCCESS_CONTENT.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            FileNotFoundException fileNotFoundException =
                    new FileNotFoundException("Failed to write " + uri);
            fileNotFoundException.initCause(e);
            throw fileNotFoundException;
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @NonNull
    private android.content.Context getRequiredContext() {
        android.content.Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Provider context is not attached");
        }
        return context;
    }
}
