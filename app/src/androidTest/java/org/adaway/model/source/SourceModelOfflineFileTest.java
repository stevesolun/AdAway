package org.adaway.model.source;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.model.error.HostError.NO_CONNECTION;
import static org.adaway.model.error.HostError.UPDATE_IN_PROGRESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.error.HostErrorException;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class SourceModelOfflineFileTest {
    private static final int FILE_SOURCE_ID = 949491;
    private static final int URL_SOURCE_ID = 949492;
    private static final int ACTIVE_GENERATION = 7;
    private static final String FILE_URL = "content://org.adaway.test.hosts/success.txt";
    private static final String URL = "https://offline.example/hosts.txt";
    private static final String OLD_HOST = "offline-old.example";
    private static final String USER_HOST = "offline-user.example";

    private AppDatabase database;
    private SourceModel sourceModel;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        database.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT OR REPLACE INTO hosts_meta (id, active_generation) VALUES (0, ?)",
                new Object[]{ACTIVE_GENERATION});
        Context offlineContext = new ContextWrapper(context) {
            @Override
            public Object getSystemService(String name) {
                return CONNECTIVITY_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
        };
        sourceModel = new SourceModel(offlineContext, database);
    }

    @After
    public void tearDown() throws Exception {
        awaitRuntimeCacheRefresh();
        database.close();
    }

    @Test
    public void localFileImportsOfflineAndUpdatesRuntimeRules() throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL, true);
        insertHost(OLD_HOST, FILE_SOURCE_ID);

        assertTrue(sourceModel.checkAndRetrieveHostsSources());

        assertImportedFile();
        assertEquals(ALLOWED, database.hostEntryDao().resolveEntry(OLD_HOST).getType());
        assertEquals(Integer.valueOf(17), sourceModel.runSourceMutation(() -> 17));
    }

    @Test
    public void disabledUrlDoesNotBlockLocalFileImportOffline() throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL, true);
        insertSource(URL_SOURCE_ID, URL, false);
        insertHost(OLD_HOST, URL_SOURCE_ID);

        assertTrue(sourceModel.checkAndRetrieveHostsSources());

        assertImportedFile();
        assertEquals(0, queryInt("SELECT COUNT(*) FROM hosts_lists WHERE source_id = ?",
                URL_SOURCE_ID));
        assertNull(database.hostsSourceDao().getById(URL_SOURCE_ID).orElseThrow()
                .getLastDownloadError());
    }

    @Test
    public void userOnlyRulesRemainUsableOffline() throws Exception {
        insertSource(USER_SOURCE_ID, USER_SOURCE_URL, true);
        insertHost(USER_HOST, USER_SOURCE_ID);

        assertTrue(sourceModel.checkAndRetrieveHostsSources());

        assertEquals(ACTIVE_GENERATION, activeGeneration());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(USER_HOST).getType());
    }

    @Test
    public void disabledUrlOnlyCleansOldRowsOfflineAndPreservesUserRules() throws Exception {
        insertSource(USER_SOURCE_ID, USER_SOURCE_URL, true);
        insertHost(USER_HOST, USER_SOURCE_ID);
        insertSource(URL_SOURCE_ID, URL, false);
        insertHost(OLD_HOST, URL_SOURCE_ID);

        assertTrue(sourceModel.checkAndRetrieveHostsSources());

        assertEquals(ACTIVE_GENERATION, activeGeneration());
        assertEquals(0, queryInt("SELECT COUNT(*) FROM hosts_lists WHERE source_id = ?",
                URL_SOURCE_ID));
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(USER_HOST).getType());
    }

    @Test
    public void enabledUrlOfflinePreservesGenerationAndReleasesGateForLocalRetry()
            throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL, true);
        insertSource(URL_SOURCE_ID, URL, true);
        insertHost(OLD_HOST, URL_SOURCE_ID);

        try {
            sourceModel.checkAndRetrieveHostsSources();
            fail("An enabled URL still requires connectivity");
        } catch (HostErrorException expected) {
            assertEquals(NO_CONNECTION, expected.getError());
        }

        assertEquals(ACTIVE_GENERATION, activeGeneration());
        assertEquals(1, queryInt("SELECT COUNT(*) FROM hosts_lists"));
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(OLD_HOST).getType());
        assertEquals(ALLOWED, database.hostEntryDao().resolveEntry("fresh-success.example")
                .getType());
        assertEquals(0, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' "
                + "AND name = 'update_seen_hosts'"));
        assertEquals(2, queryInt("PRAGMA synchronous"));
        assertNull(database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow()
                .getLocalModificationDate());

        sourceModel.runSourceMutation(() -> {
            database.hostsSourceDao().applySourceSelection(URL_SOURCE_ID, false);
            return null;
        });
        assertTrue(sourceModel.checkAndRetrieveHostsSources());
        assertImportedFile();
    }

    @Test
    public void offlineUpdateCannotInspectSelectionWhileMutationOwnsGate() throws Exception {
        insertSource(URL_SOURCE_ID, URL, true);
        sourceModel.runSourceMutation(() -> {
            try {
                sourceModel.checkAndRetrieveHostsSources();
                fail("Update must respect the mutation gate before inspecting connectivity");
            } catch (HostErrorException expected) {
                assertEquals(UPDATE_IN_PROGRESS, expected.getError());
            }
            return null;
        });
    }

    @Test
    public void targetedLocalFileImportsOfflineAndRepairsOldMillisecondMetadata() throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL + "?modified=1700000000000", true);
        insertHost(OLD_HOST, FILE_SOURCE_ID);
        ZonedDateTime corruptDate = ZonedDateTime.parse("+55840-11-08T22:13:20Z");
        database.hostsSourceDao().updateModificationDates(FILE_SOURCE_ID, corruptDate, corruptDate);
        Instant before = Instant.now().minusSeconds(1);

        sourceModel.retrieveHostsSource(FILE_SOURCE_ID);

        assertEquals(ACTIVE_GENERATION, activeGeneration());
        assertImportedFileRows(FILE_SOURCE_ID);
        assertEquals(ALLOWED, database.hostEntryDao().resolveEntry(OLD_HOST).getType());
        assertFileDates(before);
        assertTargetedCleanup();
        assertEquals(Integer.valueOf(17), sourceModel.runSourceMutation(() -> 17));
    }

    @Test
    public void fullLocalFileImportUsesMillisecondsForOnlineDate() throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL + "?modified=1700000000000", true);
        Instant before = Instant.now().minusSeconds(1);

        assertTrue(sourceModel.checkAndRetrieveHostsSources());

        assertImportedFile();
        assertFileDates(before);
    }

    @Test
    public void offlineCheckInvalidatesLegacyFileDateBeforeAutomaticImport() throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL + "?modified=1700000000000", true);
        insertHost(OLD_HOST, FILE_SOURCE_ID);
        ZonedDateTime corruptDate = ZonedDateTime.parse("+55840-11-08T22:13:20Z");
        database.hostsSourceDao().updateModificationDates(FILE_SOURCE_ID, corruptDate, corruptDate);

        assertTrue(sourceModel.checkForUpdate());

        HostsSource pending = database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow();
        assertNull(pending.getLocalModificationDate());
        assertEquals(Instant.ofEpochMilli(1700000000000L),
                pending.getOnlineModificationDate().toInstant());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(OLD_HOST).getType());
        Instant before = Instant.now().minusSeconds(1);
        assertTrue(sourceModel.checkAndRetrieveHostsSources());
        assertImportedFile();
        assertFileDates(before);
        assertFalse(sourceModel.checkForUpdate());
    }

    @Test
    public void offlineCheckInvalidatesLegacyFileDateWhenProviderDateIsUnknown() throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL + "?modified=unknown", true);
        ZonedDateTime corruptDate = ZonedDateTime.parse("+55840-11-08T22:13:20Z");
        database.hostsSourceDao().updateModificationDates(FILE_SOURCE_ID, corruptDate, corruptDate);

        assertTrue(sourceModel.checkForUpdate());

        HostsSource pending = database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow();
        assertNull(pending.getLocalModificationDate());
        assertNull(pending.getOnlineModificationDate());
        assertTrue(sourceModel.checkForUpdate());
        sourceModel.retrieveHostsSource(FILE_SOURCE_ID);
        assertFalse(sourceModel.checkForUpdate());
    }

    @Test
    public void offlineCheckPreservesMatchingFutureFileDates() throws Exception {
        ZonedDateTime future = ZonedDateTime.now().plusDays(1).withNano(0);
        insertSource(FILE_SOURCE_ID, FILE_URL + "?modified=" + future.toInstant().toEpochMilli(), true);
        database.hostsSourceDao().updateModificationDates(FILE_SOURCE_ID, future, future);

        assertFalse(sourceModel.checkForUpdate());

        HostsSource unchanged = database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow();
        assertEquals(future.toInstant(), unchanged.getLocalModificationDate().toInstant());
        assertEquals(future.toInstant(), unchanged.getOnlineModificationDate().toInstant());
    }

    @Test
    public void targetedMultipleLocalFilesImportOfflineWithoutChangingGeneration() throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL, true);
        insertSource(FILE_SOURCE_ID + 2, FILE_URL + "?second=true", true);
        insertHost(OLD_HOST, FILE_SOURCE_ID);
        insertHost("second-old.example", FILE_SOURCE_ID + 2);

        sourceModel.retrieveHostsSources(Arrays.asList(FILE_SOURCE_ID, FILE_SOURCE_ID + 2,
                FILE_SOURCE_ID));

        assertEquals(ACTIVE_GENERATION, activeGeneration());
        assertImportedFileRows(FILE_SOURCE_ID);
        assertImportedFileRows(FILE_SOURCE_ID + 2);
        assertEquals(ALLOWED, database.hostEntryDao().resolveEntry(OLD_HOST).getType());
        assertEquals(ALLOWED, database.hostEntryDao().resolveEntry("second-old.example").getType());
        assertTargetedCleanup();
    }

    @Test
    public void targetedDisabledUrlCleansUpOfflineWithoutTouchingUserRules() throws Exception {
        insertSource(USER_SOURCE_ID, USER_SOURCE_URL, true);
        insertHost(USER_HOST, USER_SOURCE_ID);
        insertSource(URL_SOURCE_ID, URL, false);
        insertHost(OLD_HOST, URL_SOURCE_ID);
        database.hostsSourceDao().updateDownloadError(URL_SOURCE_ID, "previous failure");

        sourceModel.retrieveHostsSource(URL_SOURCE_ID);
        sourceModel.retrieveHostsSource(USER_SOURCE_ID);
        sourceModel.retrieveHostsSource(-999);
        sourceModel.retrieveHostsSources(Arrays.asList(URL_SOURCE_ID, USER_SOURCE_ID, -999));

        assertEquals(ACTIVE_GENERATION, activeGeneration());
        assertEquals(0, queryInt("SELECT COUNT(*) FROM hosts_lists WHERE source_id = ?",
                URL_SOURCE_ID));
        assertEquals("Disabling a source does not acknowledge a successful download",
                "previous failure", database.hostsSourceDao().getById(URL_SOURCE_ID).orElseThrow()
                .getLastDownloadError());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(USER_HOST).getType());
        assertTargetedCleanup();
    }

    @Test
    public void targetedEnabledUrlStillRejectsOfflineAndReleasesGate() throws Exception {
        insertSource(URL_SOURCE_ID, URL, true);
        insertHost(OLD_HOST, URL_SOURCE_ID);
        try {
            sourceModel.retrieveHostsSource(URL_SOURCE_ID);
            fail("An enabled URL requires connectivity");
        } catch (HostErrorException expected) {
            assertEquals(NO_CONNECTION, expected.getError());
        }
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(OLD_HOST).getType());
        assertTargetedCleanup();
        insertSource(FILE_SOURCE_ID, FILE_URL, true);
        sourceModel.retrieveHostsSource(FILE_SOURCE_ID);
        assertImportedFileRows(FILE_SOURCE_ID);
    }

    @Test
    public void targetedMixedOfflineFailureRollsBackEarlierLocalImport() throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL, true);
        insertSource(URL_SOURCE_ID, URL, true);
        insertHost(OLD_HOST, FILE_SOURCE_ID);
        try {
            sourceModel.retrieveHostsSources(Arrays.asList(FILE_SOURCE_ID, URL_SOURCE_ID));
            fail("A mixed update must not commit a partial local import");
        } catch (HostErrorException expected) {
            assertEquals(NO_CONNECTION, expected.getError());
        }
        assertEquals(ACTIVE_GENERATION, activeGeneration());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(OLD_HOST).getType());
        assertEquals(ALLOWED, database.hostEntryDao().resolveEntry("fresh-success.example")
                .getType());
        assertNull(database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow()
                .getLocalModificationDate());
        assertTargetedCleanup();
        assertEquals(Integer.valueOf(17), sourceModel.runSourceMutation(() -> 17));
    }

    @Test
    public void offlineChecksHandleNoSourcesAndLocalSourcesWithUnknownDates() throws Exception {
        assertFalse(sourceModel.checkForUpdate());
        insertSource(USER_SOURCE_ID, USER_SOURCE_URL, true);
        insertSource(URL_SOURCE_ID, URL, false);
        assertFalse(sourceModel.checkForUpdate());
        insertSource(FILE_SOURCE_ID, FILE_URL + "?modified=unknown", true);

        assertTrue(sourceModel.checkForUpdate());
        assertNull(database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow()
                .getOnlineModificationDate());
        sourceModel.retrieveHostsSource(FILE_SOURCE_ID);
        assertFalse(sourceModel.checkForUpdate());
        assertNull(database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow()
                .getOnlineModificationDate());
        ZonedDateTime oldDate = ZonedDateTime.now().minusDays(8);
        database.hostsSourceDao().updateModificationDates(FILE_SOURCE_ID, oldDate, null);
        assertTrue(sourceModel.checkForUpdate());
    }

    @Test
    public void offlineCheckStillRejectsEnabledUrlBeforeUpdatingLocalMetadata() throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL, true);
        insertSource(URL_SOURCE_ID, URL, true);
        try {
            sourceModel.checkForUpdate();
            fail("An enabled URL check requires connectivity");
        } catch (HostErrorException expected) {
            assertEquals(NO_CONNECTION, expected.getError());
        }
        assertNull(database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow()
                .getOnlineModificationDate());
    }

    @Test
    public void targetedLocalUpdatesStillRespectMutationGateOffline() throws Exception {
        insertSource(FILE_SOURCE_ID, FILE_URL, true);
        sourceModel.runSourceMutation(() -> {
            try {
                sourceModel.retrieveHostsSource(FILE_SOURCE_ID);
                fail("A targeted update must respect the mutation gate");
            } catch (HostErrorException expected) {
                assertEquals(UPDATE_IN_PROGRESS, expected.getError());
            }
            try {
                sourceModel.retrieveHostsSources(Collections.singletonList(FILE_SOURCE_ID));
                fail("A targeted batch must respect the mutation gate");
            } catch (HostErrorException expected) {
                assertEquals(UPDATE_IN_PROGRESS, expected.getError());
            }
            return null;
        });
        assertNull(database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow()
                .getLocalModificationDate());
    }

    private void assertFileDates(Instant before) {
        HostsSource source = database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow();
        assertEquals(Instant.ofEpochMilli(1700000000000L),
                source.getOnlineModificationDate().toInstant());
        assertFalse(source.getLocalModificationDate().toInstant().isBefore(before));
        assertFalse(source.getLocalModificationDate().toInstant().isAfter(Instant.now()));
    }

    private void assertImportedFileRows(int sourceId) {
        assertEquals(2, queryInt("SELECT COUNT(*) FROM hosts_lists "
                + "WHERE source_id = ? AND generation = ?", sourceId, ACTIVE_GENERATION));
        assertEquals(2, database.hostsSourceDao().getById(sourceId).orElseThrow().getSize());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry("fresh-success.example")
                .getType());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry("second-success.example")
                .getType());
    }

    private void assertTargetedCleanup() throws InterruptedException {
        awaitRuntimeCacheRefresh();
        assertEquals(0, queryInt("SELECT COUNT(*) FROM hosts_lists WHERE generation = ?",
                ACTIVE_GENERATION + 1));
        assertEquals(0, queryInt("SELECT COUNT(*) FROM root_host_entries_stage WHERE generation = ?",
                ACTIVE_GENERATION + 1));
        assertEquals(0, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' "
                + "AND name IN ('update_seen_hosts', 'update_pending_hosts')"));
        assertEquals(2, queryInt("PRAGMA synchronous"));
    }

    private void awaitRuntimeCacheRefresh() throws InterruptedException {
        // Finalization queues a refresh that temporarily sets synchronous=NORMAL. Wait for
        // the single-threaded disk queue before checking cleanup or closing the database.
        CountDownLatch drained = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(drained::countDown);
        assertTrue("Runtime cache refresh must finish", drained.await(10, TimeUnit.SECONDS));
    }

    private void assertImportedFile() {
        assertEquals(ACTIVE_GENERATION + 1, activeGeneration());
        assertEquals(2, database.hostsSourceDao().getById(FILE_SOURCE_ID).orElseThrow().getSize());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry("fresh-success.example")
                .getType());
        assertEquals(BLOCKED, database.hostEntryDao().resolveEntry("second-success.example")
                .getType());
    }

    private void insertSource(int id, String url, boolean enabled) {
        HostsSource source = new HostsSource();
        source.setId(id);
        source.setLabel(url);
        source.setUrl(url);
        source.setEnabled(enabled);
        database.hostsSourceDao().insert(source);
    }

    private void insertHost(String host, int sourceId) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(BLOCKED);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        item.setGeneration(ACTIVE_GENERATION);
        database.hostsListItemDao().insert(item);
    }

    private int activeGeneration() {
        return queryInt("SELECT active_generation FROM hosts_meta WHERE id = 0");
    }

    private int queryInt(String sql, Object... args) {
        try (Cursor cursor = database.getOpenHelper().getWritableDatabase().query(sql, args)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }
}
