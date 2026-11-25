package org.adaway.model.backup;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.vpn.VpnStatus.STOPPED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.ListType;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.model.vpn.VpnModel;
import org.adaway.util.AppExecutors;
import org.adaway.vpn.VpnServiceControls;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Exercises the real Context/URI restore path without starting a VPN or writing root hosts. */
@RunWith(AndroidJUnit4.class)
public class BackupRestoreRuntimeInstrumentedTest {
    private AdAwayApplication application;
    private AppDatabase database;
    private VpnModel model;
    private AdBlockMethod originalMethod;
    private String host;
    private File document;

    @Before
    public void setUp() throws Exception {
        AdAwayApplication app = ApplicationProvider.getApplicationContext();
        assumeTrue("Do not change a live VPN fixture.", !VpnServiceControls.isStarted(app));
        CountDownLatch idle = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(idle::countDown);
        assertTrue(idle.await(10, TimeUnit.SECONDS));
        assumeTrue("No concurrent source-operation fixture.", !app.getSourceOperations().isBusy());
        application = app;
        originalMethod = PreferenceHelper.getAdBlockMethod(app);
        PreferenceHelper.setAbBlockMethod(app, VPN);
        database = AppDatabase.getInstance(app);
        model = (VpnModel) app.getAdBlockModel();
        host = "settings-restore-" + System.nanoTime() + ".example";
        document = File.createTempFile("settings-restore-", ".json", app.getCacheDir());
    }

    @After
    public void tearDown() throws Exception {
        if (application == null) return;
        try {
            if (database != null && host != null) {
                database.hostsListItemDao().deleteUserFromHost(host);
                application.getSourceModel().syncHostEntries();
            }
            if (document != null) Files.deleteIfExists(document.toPath());
        } finally {
            PreferenceHelper.setAbBlockMethod(application, originalMethod);
        }
    }

    @Test
    public void restoreInvalidatesWarmedAllowedDecisionWithoutStartingVpn() throws Exception {
        assertEquals(ALLOWED, model.getEntry(host).getType());
        restore(BLOCKED);
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(host).getType());
        assertEquals(BLOCKED, model.getEntry(host).getType());
        assertEquals(STOPPED, PreferenceHelper.getVpnServiceStatus(application));
        assertFalse(application.getSourceOperations().isBusy());
    }

    @Test
    public void restoreInvalidatesWarmedBlockedDecisionWithoutManualApply() throws Exception {
        restore(BLOCKED);
        assertEquals(BLOCKED, model.getEntry(host).getType());
        restore(ALLOWED);
        assertEquals(ALLOWED, database.hostEntryDao().resolveEntry(host).getType());
        assertEquals(ALLOWED, model.getEntry(host).getType());
        assertEquals(STOPPED, PreferenceHelper.getVpnServiceStatus(application));
    }

    @Test
    public void heldOperationLeaseRejectsWithoutMutationAndRetrySucceeds() throws Exception {
        writeBackup(BLOCKED);
        try (SourceOperationCoordinator.Lease ignored = application.getSourceOperations().acquire()) {
            BackupImporter.BackupBusyException error = assertThrows(
                    BackupImporter.BackupBusyException.class, this::importDocument);
            assertTrue(error.getMessage().contains("retry"));
            assertTrue(application.getSourceOperations().isBusy());
            assertTrue(database.hostsListItemDao().getExactEntriesForHost(host).isEmpty());
        }
        importDocument();
        assertEquals(BLOCKED, model.getEntry(host).getType());
        assertFalse(application.getSourceOperations().isBusy());
    }

    @Test
    public void heldSourceMutationGuardRejectsAndReleasesTheOuterLease() throws Exception {
        writeBackup(BLOCKED);
        application.getSourceModel().runSourceMutation(() -> {
            assertThrows(BackupImporter.BackupBusyException.class, this::importDocument);
            return null;
        });
        assertFalse(application.getSourceOperations().isBusy());
        assertTrue(database.hostsListItemDao().getExactEntriesForHost(host).isEmpty());
        importDocument();
        assertEquals(BLOCKED, model.getEntry(host).getType());
    }

    @Test
    public void malformedDocumentDoesNotMutateOrLeakOwnership() throws Exception {
        Files.write(document.toPath(), "{bad json".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, this::importDocument);
        assertFalse(application.getSourceOperations().isBusy());
        assertTrue(database.hostsListItemDao().getExactEntriesForHost(host).isEmpty());
        restore(BLOCKED);
        assertEquals(BLOCKED, model.getEntry(host).getType());
    }

    @Test
    public void databaseFailureReleasesBothGuardsAndAllowsRetry() throws Exception {
        writeBackup(BLOCKED);
        String trigger = "settings_restore_failure";
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER " + trigger + " BEFORE INSERT ON hosts_lists "
                        + "WHEN NEW.host = '" + host + "' "
                        + "BEGIN SELECT RAISE(ABORT, 'test failure'); END");
        try {
            assertThrows(IOException.class, this::importDocument);
            assertFalse(application.getSourceOperations().isBusy());
            assertTrue(database.hostsListItemDao().getExactEntriesForHost(host).isEmpty());
        } finally {
            database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER " + trigger);
        }
        importDocument();
        assertEquals(BLOCKED, model.getEntry(host).getType());
    }

    private void restore(ListType type) throws Exception {
        writeBackup(type);
        importDocument();
    }

    private void writeBackup(ListType type) throws Exception {
        JSONObject backup = new JSONObject().put("sources", new JSONArray())
                .put("blocked", new JSONArray()).put("allowed", new JSONArray())
                .put("redirected", new JSONArray());
        backup.getJSONArray(type == BLOCKED ? "blocked" : "allowed").put(
                new JSONObject().put("host", host).put("enabled", true).put("kind", "exact"));
        Files.write(document.toPath(), backup.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void importDocument() throws IOException {
        BackupImporter.importBackup(application, Uri.fromFile(document));
    }
}
