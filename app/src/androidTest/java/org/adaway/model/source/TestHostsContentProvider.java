package org.adaway.model.source;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class TestHostsContentProvider extends ContentProvider {
    private static final String AUTHORITY = "org.adaway.test.hosts";
    private static final String SUCCESS_PATH = "/success.txt";
    private static final String BACKUP_PATH = "/backup.json";
    private static final String SUCCESS_FILE_NAME = "adaway-test-success-hosts.txt";
    private static final String BACKUP_FILE_NAME = "adaway-test-backup.json";
    private static final String TEXT_MIME_TYPE = "text/plain";
    private static final String JSON_MIME_TYPE = "application/json";
    private static final String SUCCESS_CONTENT =
            "0.0.0.0 fresh-success.example\n" +
            "0.0.0.0 second-success.example\n";
    public static final Uri BACKUP_URI =
            Uri.parse("content://" + AUTHORITY + BACKUP_PATH);
    private static volatile byte[] backupContent;

    public static void clearBackup(Context context) {
        context.getContentResolver().delete(BACKUP_URI, null, null);
    }

    public static String readBackup(Context context) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(BACKUP_URI);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new FileNotFoundException("Backup fixture stream is null");
            }
            byte[] buffer = new byte[4096];
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, count);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

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
        String displayName;
        long size;
        if (SUCCESS_PATH.equals(uri.getPath())) {
            displayName = "success.txt";
            size = SUCCESS_CONTENT.getBytes(StandardCharsets.UTF_8).length;
        } else if (BACKUP_PATH.equals(uri.getPath())) {
            displayName = BACKUP_FILE_NAME;
            byte[] snapshot = backupContent;
            size = snapshot == null ? 0 : snapshot.length;
        } else {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        });
        cursor.addRow(new Object[]{
                displayName,
                size,
                System.currentTimeMillis()
        });
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        if (BACKUP_PATH.equals(uri.getPath())) {
            return JSON_MIME_TYPE;
        }
        return TEXT_MIME_TYPE;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("insert");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        if (BACKUP_PATH.equals(uri.getPath())) {
            backupContent = null;
            return 1;
        }
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
        if (BACKUP_PATH.equals(uri.getPath())) {
            return mode.contains("w") ? openBackupWritePipe(uri) : openBackupReadPipe(uri);
        }
        if (!SUCCESS_PATH.equals(uri.getPath())) {
            throw new FileNotFoundException("Unknown test host source: " + uri);
        }
        File file = new File(getRequiredContext().getCacheDir(), SUCCESS_FILE_NAME);
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
    private static ParcelFileDescriptor openBackupWritePipe(@NonNull Uri uri)
            throws FileNotFoundException {
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread reader = new Thread(() -> readBackupPipe(pipe[0]), "BackupFixtureReader");
            reader.start();
            return pipe[1];
        } catch (IOException exception) {
            FileNotFoundException fileNotFoundException =
                    new FileNotFoundException("Failed to open write pipe for " + uri);
            fileNotFoundException.initCause(exception);
            throw fileNotFoundException;
        }
    }

    @NonNull
    private static ParcelFileDescriptor openBackupReadPipe(@NonNull Uri uri)
            throws FileNotFoundException {
        byte[] snapshot = backupContent;
        if (snapshot == null) {
            throw new FileNotFoundException("Backup fixture has no content for " + uri);
        }
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread writer = new Thread(() -> writeBackupPipe(pipe[1], snapshot),
                    "BackupFixtureWriter");
            writer.start();
            return pipe[0];
        } catch (IOException exception) {
            FileNotFoundException fileNotFoundException =
                    new FileNotFoundException("Failed to open read pipe for " + uri);
            fileNotFoundException.initCause(exception);
            throw fileNotFoundException;
        }
    }

    private static void readBackupPipe(@NonNull ParcelFileDescriptor descriptor) {
        try (ParcelFileDescriptor.AutoCloseInputStream inputStream =
                     new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, count);
            }
            backupContent = outputStream.toByteArray();
        } catch (IOException ignored) {
            backupContent = null;
        }
    }

    private static void writeBackupPipe(
            @NonNull ParcelFileDescriptor descriptor,
            @NonNull byte[] snapshot) {
        try (OutputStream outputStream =
                     new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
            outputStream.write(snapshot);
        } catch (IOException ignored) {
            // The reader side owns failure reporting.
        }
    }

    @NonNull
    private Context getRequiredContext() {
        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Provider context is not attached");
        }
        return context;
    }
}
