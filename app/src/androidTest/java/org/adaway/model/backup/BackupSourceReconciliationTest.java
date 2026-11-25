package org.adaway.model.backup;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import org.adaway.db.DbTest;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class BackupSourceReconciliationTest extends DbTest {
    private static final String HOST = "source-restore.example";
    private static final ZonedDateTime DATE = ZonedDateTime.of(
            2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);

    @Test
    public void selectionAndLabelRestoreKeepIdRowsValidatorsAndMetadata() throws Exception {
        seedSource(false);
        int rowId = hostListItemDao.getExactEntriesForHost(HOST).get(0).getId();
        JSONObject backup = backup(false, false);
        BackupImporter.importBackup(db, backup);

        HostsSource restored = getSourceFromId(EXTERNAL_SOURCE_ID);
        assertEquals("Saved", restored.getLabel());
        assertFalse(restored.isEnabled());
        assertEquals("etag", restored.getEntityTag());
        assertEquals(DATE, restored.getLocalModificationDate());
        assertEquals(Integer.valueOf(123), restored.getFilterListId());
        HostListItem row = hostListItemDao.getExactEntriesForHost(HOST).get(0);
        assertEquals(rowId, row.getId());
        assertFalse(row.isEnabled());

        backup.getJSONArray("sources").getJSONObject(0).put("enabled", true);
        BackupImporter.importBackup(db, backup);
        assertTrue(getSourceFromId(EXTERNAL_SOURCE_ID).isEnabled());
        assertTrue(hostListItemDao.getExactEntriesForHost(HOST).get(0).isEnabled());
        assertEquals(rowId, hostListItemDao.getExactEntriesForHost(HOST).get(0).getId());
    }

    @Test
    public void allowFormatChangeClearsRowsStageAndValidatorsWithoutReplacingSource() throws Exception {
        seedSource(false);
        BackupImporter.importBackup(db, backup(true, true));
        assertInvalidatedSource(true);
    }

    @Test
    public void existingRedirectTrustIsRevokedAndItsCachedContentDiscarded() throws Exception {
        seedSource(true);
        JSONObject backup = backup(true, false);
        backup.getJSONArray("sources").getJSONObject(0).put("redirect", true);
        BackupImporter.importBackup(db, backup);
        assertInvalidatedSource(false);
    }

    @Test
    public void newUrlIsInsertedWithoutGrantingRedirectTrust() throws Exception {
        JSONObject backup = backup(true, true);
        String url = "https://new-restore.example/hosts";
        backup.getJSONArray("sources").getJSONObject(0).put("url", url).put("redirect", true);
        BackupImporter.importBackup(db, backup);
        HostsSource inserted = hostsSourceDao.getByUrl(url).get();
        assertTrue(inserted.getId() != EXTERNAL_SOURCE_ID && inserted.getId() != USER_SOURCE_ID);
        assertTrue(inserted.isAllowEnabled());
        assertFalse(inserted.isRedirectEnabled());
    }

    @Test
    public void laterDatabaseFailureRollsBackExistingSourceAndStagingChanges() throws Exception {
        seedSource(false);
        db.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_restore_row BEFORE INSERT ON hosts_lists "
                        + "WHEN NEW.host = 'reject-restore.example' "
                        + "BEGIN SELECT RAISE(ABORT, 'test failure'); END");
        JSONObject backup = backup(false, true);
        backup.getJSONArray("blocked").put(new JSONObject()
                .put("host", "reject-restore.example").put("enabled", true));
        assertThrows(SQLiteException.class, () -> BackupImporter.importBackup(db, backup));
        HostsSource unchanged = getSourceFromId(EXTERNAL_SOURCE_ID);
        assertEquals("Current", unchanged.getLabel());
        assertTrue(unchanged.isEnabled());
        assertEquals("etag", unchanged.getEntityTag());
        assertEquals(1, scalar("SELECT COUNT(*) FROM root_host_entries_stage"));
        assertEquals(1, hostListItemDao.getExactEntriesForHost(HOST).size());
    }

    @Test
    public void reservedUserSourceCannotBeDisabledOrCleared() throws Exception {
        insertBlockedHost(HOST, USER_SOURCE_ID);
        JSONObject backup = backup(false, true);
        backup.getJSONArray("sources").getJSONObject(0).put("url", USER_SOURCE_URL);
        assertThrows(JSONException.class, () -> BackupImporter.importBackup(db, backup));
        assertTrue(hostsSourceDao.getById(USER_SOURCE_ID).get().isEnabled());
        assertEquals(1, hostListItemDao.getUserList().size());
    }

    private void seedSource(boolean redirects) {
        HostsSource source = getSourceFromId(EXTERNAL_SOURCE_ID);
        source.setLabel("Current");
        source.setRedirectEnabled(redirects);
        source.setEntityTag("etag");
        source.setLocalModificationDate(DATE);
        source.setOnlineModificationDate(DATE);
        source.setFilterListId(123);
        source.setSkippedCount(5);
        source.setLastDownloadError("old failure");
        hostsSourceDao.update(source);
        insertBlockedHost(HOST, EXTERNAL_SOURCE_ID);
        hostsSourceDao.updateActiveRuleStats(EXTERNAL_SOURCE_ID);
        db.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT INTO root_host_entries_stage "
                        + "(host, reverse_host, type, redirection, source_id, generation) "
                        + "VALUES (?, ?, 0, NULL, ?, 0)",
                new Object[]{HOST, "example.source-restore", EXTERNAL_SOURCE_ID});
    }

    private void assertInvalidatedSource(boolean allows) {
        HostsSource source = getSourceFromId(EXTERNAL_SOURCE_ID);
        assertEquals(EXTERNAL_SOURCE_ID, source.getId());
        assertEquals("Saved", source.getLabel());
        assertEquals(allows, source.isAllowEnabled());
        assertFalse(source.isRedirectEnabled());
        assertNull(source.getEntityTag());
        assertNull(source.getLocalModificationDate());
        assertNull(source.getOnlineModificationDate());
        assertNull(source.getLastDownloadError());
        assertEquals(0, source.getSkippedCount());
        assertEquals(0, source.getActiveRuleCount());
        assertEquals(0, scalar("SELECT COUNT(*) FROM root_host_entries_stage"));
        assertTrue(hostListItemDao.getExactEntriesForHost(HOST).isEmpty());
    }

    private JSONObject backup(boolean enabled, boolean allows) throws JSONException {
        return new JSONObject().put("sources", new JSONArray().put(new JSONObject()
                .put("label", "Saved").put("url", externalHostSource.getUrl())
                .put("enabled", enabled).put("allow", allows).put("redirect", false)))
                .put("blocked", new JSONArray()).put("allowed", new JSONArray())
                .put("redirected", new JSONArray());
    }

    private int scalar(String sql) {
        try (Cursor cursor = db.getOpenHelper().getReadableDatabase().query(sql)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }
}
