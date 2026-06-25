package org.adaway.model.source;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.db.entity.RuleKind.SUFFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.StringReader;

@RunWith(AndroidJUnit4.class)
public class SourceLoaderDatabaseSemanticsTest {
    private static final int SOURCE_ID = 939391;
    private static final int GENERATION = 11;

    private AppDatabase database;
    private HostListItemDao hostListItemDao;
    private HostsSource source;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        hostListItemDao = database.hostsListItemDao();
        insertHostsMeta();
        source = insertSource(database.hostsSourceDao());
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void fastPathParse_persistsExtractedRuleSemanticsAndSkipsUnsafeRows() {
        SqlUpdateDeduper deduper =
                new SqlUpdateDeduper(database.getOpenHelper().getWritableDatabase());
        int skipped;
        try {
            skipped = new SourceLoader(source, GENERATION).parse(
                    new BufferedReader(new StringReader(
                            "0.0.0.0 exact.example\n" +
                                    "||suffix.example^\n" +
                                    "address=/dnsmasq.example/#\n" +
                                    "local=/local.example/\n" +
                                    "local-data: \"unbound-null.example A 0.0.0.0\"\n" +
                                    "local-data: \"unbound-private.example A 192.168.1.1\"\n" +
                                    "@@||exception.example^\n" +
                                    "||path.example/path\n" +
                                    "server=/upstream.example/8.8.8.8\n")),
                    hostListItemDao,
                    database.getOpenHelper().getWritableDatabase(),
                    null,
                    deduper);
        } finally {
            deduper.drop();
        }

        assertEquals(4, skipped);
        assertEquals(5L, scalarLong(
                "SELECT COUNT(*) FROM hosts_lists WHERE source_id = ? AND generation = ?",
                SOURCE_ID, GENERATION));
        assertHostRow("exact.example", EXACT.getValue());
        assertHostRow("suffix.example", SUFFIX.getValue());
        assertHostRow("dnsmasq.example", SUFFIX.getValue());
        assertHostRow("local.example", SUFFIX.getValue());
        assertHostRow("unbound-null.example", EXACT.getValue());
        assertMissingHost("unbound-private.example");
        assertMissingHost("exception.example");
        assertMissingHost("path.example");
        assertMissingHost("upstream.example");
        assertEquals(5L, scalarLong(
                "SELECT COUNT(*) FROM root_host_entries_stage " +
                        "WHERE source_id = ? AND generation = ?",
                SOURCE_ID, GENERATION));
        assertScratchTableAbsent();
    }

    @Test
    public void redirectEnabledSource_fallsBackToBlockSafeRpzSyntaxBeforeRedirecting() {
        HostsSource redirectSource = insertSource(database.hostsSourceDao(), SOURCE_ID + 1, true);
        SqlUpdateDeduper deduper =
                new SqlUpdateDeduper(database.getOpenHelper().getWritableDatabase());
        int skipped;
        try {
            skipped = new SourceLoader(redirectSource, GENERATION).parse(
                    new BufferedReader(new StringReader("ads.example.com CNAME .\n")),
                    hostListItemDao,
                    database.getOpenHelper().getWritableDatabase(),
                    null,
                    deduper);
        } finally {
            deduper.drop();
        }

        assertEquals(0, skipped);
        assertEquals(1L, scalarLong(
                "SELECT COUNT(*) FROM hosts_lists " +
                        "WHERE host = ? AND type = ? AND kind = ? AND redirection IS NULL " +
                        "AND source_id = ? AND generation = ?",
                "ads.example.com", BLOCKED.getValue(), EXACT.getValue(),
                SOURCE_ID + 1, GENERATION));
        assertScratchTableAbsent();
    }

    private void assertHostRow(String host, int kind) {
        assertEquals(1L, scalarLong(
                "SELECT COUNT(*) FROM hosts_lists " +
                        "WHERE host = ? AND type = ? AND kind = ? AND enabled = 1 " +
                        "AND redirection IS NULL AND source_id = ? AND generation = ?",
                host, BLOCKED.getValue(), kind, SOURCE_ID, GENERATION));
    }

    private void assertMissingHost(String host) {
        assertEquals(0L, scalarLong("SELECT COUNT(*) FROM hosts_lists WHERE host = ?", host));
        assertEquals(0L, scalarLong(
                "SELECT COUNT(*) FROM root_host_entries_stage WHERE host = ?", host));
    }

    private void insertHostsMeta() {
        SupportSQLiteDatabase writableDb = database.getOpenHelper().getWritableDatabase();
        writableDb.execSQL("INSERT OR IGNORE INTO hosts_meta " +
                "(id, active_generation) VALUES (0, 0)");
        writableDb.execSQL("INSERT OR IGNORE INTO hosts_stats " +
                "(id, blocked_count, blocked_exact_count, allowed_count, redirected_count, " +
                "active_rule_count, root_export_materialized) VALUES (0, 0, 0, 0, 0, 0, 0)");
    }

    private static HostsSource insertSource(HostsSourceDao hostsSourceDao) {
        return insertSource(hostsSourceDao, SOURCE_ID, false);
    }

    private static HostsSource insertSource(HostsSourceDao hostsSourceDao, int sourceId,
            boolean redirectEnabled) {
        HostsSource source = new HostsSource();
        source.setId(sourceId);
        source.setLabel("Parser DB semantics");
        source.setUrl("https://parser.example.test/hosts.txt");
        source.setEnabled(true);
        source.setRedirectEnabled(redirectEnabled);
        hostsSourceDao.insert(source);
        return source;
    }

    private long scalarLong(String sql, Object... args) {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase()
                .query(new SimpleSQLiteQuery(sql, args))) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private void assertScratchTableAbsent() {
        assertEquals(0L, scalarLong(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' " +
                        "AND name IN ('update_seen_hosts', 'update_pending_hosts')"));
    }
}
