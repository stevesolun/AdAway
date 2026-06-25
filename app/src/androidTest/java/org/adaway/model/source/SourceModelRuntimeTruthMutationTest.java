package org.adaway.model.source;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.db.entity.RuleKind.SUFFIX;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class SourceModelRuntimeTruthMutationTest {
    private static final int EXTERNAL_SOURCE_ID = 929294;
    private static final int ACTIVE_GENERATION = 2;

    private AppDatabase database;
    private HostsSourceDao hostsSourceDao;
    private HostListItemDao hostListItemDao;
    private HostEntryDao hostEntryDao;
    private SourceModel sourceModel;
    private HostsSource externalSource;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        AppDatabase.optimizeCreatedDatabaseStorage(
                database.getOpenHelper().getWritableDatabase());
        writableDb().execSQL(
                "INSERT OR IGNORE INTO hosts_meta (id, active_generation) VALUES (0, 0)");
        setActiveGeneration(ACTIVE_GENERATION);
        hostsSourceDao = database.hostsSourceDao();
        hostListItemDao = database.hostsListItemDao();
        hostEntryDao = database.hostEntryDao();
        sourceModel = new SourceModel(context, database);
        insertSource(USER_SOURCE_ID, USER_SOURCE_URL);
        insertSource(EXTERNAL_SOURCE_ID, "https://example.invalid/runtime-truth.txt");
        externalSource = hostsSourceDao.getById(EXTERNAL_SOURCE_ID).orElseThrow();
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void syncHostEntries_keepsActiveTruthAcrossUserAndSourceMutations() {
        insertItem("mutation-active.example", BLOCKED, EXACT, EXTERNAL_SOURCE_ID,
                ACTIVE_GENERATION, null);
        insertItem("mutation-suffix.example", BLOCKED, SUFFIX, EXTERNAL_SOURCE_ID,
                ACTIVE_GENERATION, null);
        insertItem("mutation-redirect.example", REDIRECTED, EXACT, EXTERNAL_SOURCE_ID,
                ACTIVE_GENERATION, "1.1.1.1");
        insertItem("mutation-stale.example", BLOCKED, EXACT, EXTERNAL_SOURCE_ID,
                ACTIVE_GENERATION - 1, null);
        insertItem("mutation-future.example", BLOCKED, EXACT, EXTERNAL_SOURCE_ID,
                ACTIVE_GENERATION + 1, null);
        hostsSourceDao.updateActiveRuleStats(EXTERNAL_SOURCE_ID);

        sourceModel.syncHostEntries();
        assertRuntimeTruth(3, 2, 1, 1, 3, rootRows(
                "mutation-active.example|0|0|null",
                "mutation-suffix.example|0|0|null",
                "mutation-redirect.example|0|2|1.1.1.1"));
        assertEquals(ALLOWED, hostEntryDao.resolveEntry("mutation-stale.example").getType());
        assertEquals(ALLOWED, hostEntryDao.resolveEntry("mutation-future.example").getType());

        insertItem("mutation-active.example", ALLOWED, EXACT, USER_SOURCE_ID, 0, null);
        sourceModel.syncHostEntries();
        assertRuntimeTruth(2, 1, 0, 1, 4, rootRows(
                "mutation-suffix.example|0|0|null",
                "mutation-redirect.example|0|2|1.1.1.1"));
        assertEquals(ALLOWED, hostEntryDao.resolveEntry("mutation-active.example").getType());

        int userAllowId = hostListItemDao.getHostId("mutation-active.example").orElseThrow();
        hostListItemDao.deleteById(userAllowId);
        sourceModel.syncHostEntries();
        assertRuntimeTruth(3, 2, 1, 1, 3, rootRows(
                "mutation-active.example|0|0|null",
                "mutation-suffix.example|0|0|null",
                "mutation-redirect.example|0|2|1.1.1.1"));

        hostsSourceDao.toggleEnabled(externalSource);
        sourceModel.syncHostEntries();
        assertRuntimeTruth(0, 0, 0, 0, 0, Collections.emptySet());

        hostsSourceDao.toggleEnabled(externalSource);
        sourceModel.syncHostEntries();
        assertRuntimeTruth(3, 2, 1, 1, 3, rootRows(
                "mutation-active.example|0|0|null",
                "mutation-suffix.example|0|0|null",
                "mutation-redirect.example|0|2|1.1.1.1"));
        assertEquals(ALLOWED, hostEntryDao.resolveEntry("mutation-stale.example").getType());
        assertEquals(ALLOWED, hostEntryDao.resolveEntry("mutation-future.example").getType());
    }

    private void insertSource(int id, String url) {
        HostsSource source = new HostsSource();
        source.setId(id);
        source.setLabel(url);
        source.setUrl(url);
        source.setEnabled(true);
        hostsSourceDao.insert(source);
    }

    private void insertItem(String host, ListType type, RuleKind kind, int sourceId,
            int generation, String redirection) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(type);
        item.setKind(kind);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        item.setGeneration(generation);
        item.setRedirection(redirection);
        hostListItemDao.insert(item);
    }

    private void setActiveGeneration(int generation) {
        writableDb().execSQL("INSERT OR REPLACE INTO hosts_meta (id, active_generation) " +
                "VALUES (0, " + generation + ")");
    }

    private void assertRuntimeTruth(int runtimeRows, int blockedRows, int blockedExactRows,
            int redirectedRows, int activeRuleRows, Set<String> rootRows) {
        assertEquals(runtimeRows, hostEntryDao.getAll().size());
        assertEquals(blockedRows, hostEntryDao.getBlockedEntryCountNow());
        assertEquals(blockedExactRows, hostEntryDao.getBlockedExactEntryCountNow());
        assertEquals(redirectedRows, hostEntryDao.getRedirectedEntryCountNow());
        assertEquals(activeRuleRows, hostEntryDao.getActiveRuntimeRuleCountNow());
        assertEquals(rootRows, rootRowsFromList());
        assertEquals(rootRows, rootRowsFromCursor());
    }

    private Set<String> rootRows(String... rows) {
        return new HashSet<>(Arrays.asList(rows));
    }

    private Set<String> rootRowsFromList() {
        Set<String> rows = new HashSet<>();
        for (HostEntry entry : hostEntryDao.getAllForRootHostsFile()) {
            rows.add(entry.getHost() + "|" + entry.getKind().getValue() + "|"
                    + entry.getType().getValue() + "|" + entry.getRedirection());
        }
        return rows;
    }

    private Set<String> rootRowsFromCursor() {
        Set<String> rows = new HashSet<>();
        try (Cursor cursor = hostEntryDao.getRootHostsFileCursor()) {
            int host = cursor.getColumnIndexOrThrow("host");
            int type = cursor.getColumnIndexOrThrow("type");
            int redirection = cursor.getColumnIndexOrThrow("redirection");
            while (cursor.moveToNext()) {
                rows.add(cursor.getString(host) + "|" + EXACT.getValue() + "|"
                        + cursor.getInt(type) + "|" + cursor.getString(redirection));
            }
        }
        return rows;
    }

    private SupportSQLiteDatabase writableDb() {
        return database.getOpenHelper().getWritableDatabase();
    }
}
