package org.adaway.model.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.adaway.db.DbTest;
import org.adaway.db.entity.HostsSource;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

public class BackupExportCapacityInstrumentedTest extends DbTest {
    private static final String SENTINEL = "existing backup must survive";
    private Context context;
    private File destination;

    @Before
    public void createDestination() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        destination = File.createTempFile("settings-export-", ".json", context.getCacheDir());
        Files.write(destination.toPath(), SENTINEL.getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void deleteDestination() throws Exception {
        if (destination != null) Files.deleteIfExists(destination.toPath());
    }

    @Test
    public void twoHundredSourcesExportAndReimportWithoutTruncation() throws Exception {
        seedSources(200);
        BackupExporter.exportBackup(context, Uri.fromFile(destination), db);
        JSONObject backup = new JSONObject(readDestination());
        assertEquals(200, backup.getJSONArray("sources").length());
        hostsSourceDao.deleteAll();
        BackupImporter.importBackup(db, backup);
        assertEquals(200, hostsSourceDao.getAll().size());
    }

    @Test
    public void overSourceLimitDoesNotOpenOrTruncateExistingDestination() {
        seedSources(201);
        assertPreflightPreservesDestination();
    }

    @Test
    public void invalidGeneratedRowDoesNotTruncateExistingDestination() {
        insertBlockedHost("https://invalid-backup.example/path", 1);
        assertPreflightPreservesDestination();
    }

    @Test
    public void oversizedSerializedBackupDoesNotTruncateExistingDestination() {
        seedSources(BackupFormat.MAX_SOURCES);
        // Exceed the document limit without exceeding a CursorWindow's per-row capacity.
        char[] label = new char[BackupFormat.MAX_CHARACTERS / BackupFormat.MAX_SOURCES + 1];
        Arrays.fill(label, 'x');
        String sourceLabel = new String(label);
        assertTrue((long) sourceLabel.length() * BackupFormat.MAX_SOURCES
                > BackupFormat.MAX_CHARACTERS);
        db.runInTransaction(() -> {
            for (HostsSource source : hostsSourceDao.getAll()) {
                source.setLabel(sourceLabel);
                hostsSourceDao.update(source);
            }
        });
        assertEquals(BackupFormat.MAX_SOURCES, hostsSourceDao.getAll().size());
        IOException error = assertPreflightPreservesDestination();
        assertTrue(error.getCause() instanceof JSONException);
        assertEquals("Backup exceeds maximum size.", error.getCause().getMessage());
    }

    private IOException assertPreflightPreservesDestination() {
        IOException error = assertThrows(IOException.class,
                () -> BackupExporter.exportBackup(context, Uri.fromFile(destination), db));
        assertFalse(error.getMessage().isEmpty());
        try {
            assertEquals(SENTINEL, readDestination());
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        return error;
    }

    private void seedSources(int total) {
        db.runInTransaction(() -> {
            for (int i = hostsSourceDao.getAll().size(); i < total; i++) {
                insertSource(10_000 + i, "https://capacity-" + i + ".example/hosts");
            }
        });
    }

    private String readDestination() throws IOException {
        return new String(Files.readAllBytes(destination.toPath()), StandardCharsets.UTF_8);
    }
}
