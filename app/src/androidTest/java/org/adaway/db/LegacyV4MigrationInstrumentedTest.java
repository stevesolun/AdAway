package org.adaway.db;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.migration.Migration;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LegacyV4MigrationInstrumentedTest {
    private static final String TEST_DB = "legacy-v4-migration-test";
    private static final String SOURCE_URL = "https://legacy-v4.example/hosts";
    private static final String INDEX_SQL = "SELECT sql FROM sqlite_master "
            + "WHERE name = 'index_host_entries_host'";
    private static final Migration[] MIGRATIONS_4_TO_32 = {
            Migrations.MIGRATION_4_5,
            Migrations.MIGRATION_5_6,
            Migrations.MIGRATION_6_7,
            Migrations.MIGRATION_7_8,
            Migrations.MIGRATION_8_9,
            Migrations.MIGRATION_9_10,
            Migrations.MIGRATION_10_11,
            Migrations.MIGRATION_11_12,
            Migrations.MIGRATION_12_13,
            Migrations.MIGRATION_13_14,
            Migrations.MIGRATION_14_15,
            Migrations.MIGRATION_15_16,
            Migrations.MIGRATION_16_17,
            Migrations.MIGRATION_17_18,
            Migrations.MIGRATION_18_19,
            Migrations.MIGRATION_19_20,
            Migrations.MIGRATION_20_21,
            Migrations.MIGRATION_21_22,
            Migrations.MIGRATION_22_23,
            Migrations.MIGRATION_23_24,
            Migrations.MIGRATION_24_25,
            Migrations.MIGRATION_25_26,
            Migrations.MIGRATION_26_27,
            Migrations.MIGRATION_27_28,
            Migrations.MIGRATION_28_29,
            Migrations.MIGRATION_29_30,
            Migrations.MIGRATION_30_31,
            Migrations.MIGRATION_31_32
    };

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    @Test
    public void tableForm4To32PreservesUserRulesAndSources() throws Exception {
        try (SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 4)) {
            seedSavedState(db);
            seedCachedEntry(db);
            assertEquals("table", hostEntriesType(db));
        }

        try (SupportSQLiteDatabase db = this.helper.runMigrationsAndValidate(
                TEST_DB, 32, true, MIGRATIONS_4_TO_32)) {
            assertSavedState(db);
            assertCachedEntry(db);
            assertEquals("example.cached", queryString(db,
                    "SELECT reverse_host FROM host_entries WHERE host = 'cached.example'"));
        }
    }

    @Test
    public void viewForm4To32PreservesUserRulesAndSources() throws Exception {
        // The checked-in v4 export has the table form; reach the older view via real 3->4.
        try (SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 3)) {
            db.beginTransaction();
            try {
                Migrations.MIGRATION_3_4.migrate(db);
                db.setVersion(4);
                seedSavedState(db);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            assertEquals("view", hostEntriesType(db));
            assertEquals(4, db.getVersion());
        }

        try (SupportSQLiteDatabase db = this.helper.runMigrationsAndValidate(
                TEST_DB, 32, true, MIGRATIONS_4_TO_32)) {
            assertSavedState(db);
            assertEquals("table", hostEntriesType(db));
        }
    }

    @Test
    public void tableForm4To5RetainsCachedRowsAndUniqueIndex() throws Exception {
        String originalIndexSql;
        try (SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 4)) {
            seedSavedState(db);
            seedCachedEntry(db);
            originalIndexSql = queryString(db, INDEX_SQL);
        }

        try (SupportSQLiteDatabase db = this.helper.runMigrationsAndValidate(
                TEST_DB, 5, true, Migrations.MIGRATION_4_5)) {
            assertCachedEntry(db);
            assertEquals(originalIndexSql, queryString(db, INDEX_SQL));
            assertTrue(originalIndexSql.contains("CREATE UNIQUE INDEX"));
            assertEquals(4, queryInt(db, "SELECT COUNT(*) FROM hosts_lists"));
            assertEquals(2, queryInt(db, "SELECT COUNT(*) FROM hosts_sources"));
        }
    }

    private static void seedSavedState(SupportSQLiteDatabase db) {
        db.execSQL("INSERT INTO hosts_sources (id, url, enabled) VALUES "
                + "(1, 'file://app/user/hosts', 1), (42, '" + SOURCE_URL + "', 0)");
        db.execSQL("INSERT INTO hosts_lists "
                + "(id, host, type, enabled, redirection, source_id) VALUES "
                + "(10, 'blocked.example', 0, 1, NULL, 1), "
                + "(11, 'allowed.example', 1, 0, NULL, 1), "
                + "(12, 'redirected.example', 2, 1, '8.8.4.4', 1), "
                + "(13, 'source.example', 0, 0, NULL, 42)");
    }

    private static void seedCachedEntry(SupportSQLiteDatabase db) {
        db.execSQL("INSERT INTO host_entries (host, type, redirection) "
                + "VALUES ('cached.example', 2, '9.9.9.9')");
    }

    private static void assertSavedState(SupportSQLiteDatabase db) {
        assertEquals(32, db.getVersion());
        assertEquals(2, queryInt(db, "SELECT COUNT(*) FROM hosts_sources"));
        assertEquals(SOURCE_URL, queryString(db,
                "SELECT url FROM hosts_sources WHERE id = 42"));
        assertEquals(0, queryInt(db, "SELECT enabled FROM hosts_sources WHERE id = 42"));
        assertEquals(USER_SOURCE_URL, queryString(db,
                "SELECT url FROM hosts_sources WHERE id = 1"));
        assertEquals(1, queryInt(db, "SELECT enabled FROM hosts_sources WHERE id = 1"));
        assertEquals(4, queryInt(db, "SELECT COUNT(*) FROM hosts_lists"));
        assertRule(db, 10, "blocked.example", 0, 1, null, 1);
        assertRule(db, 11, "allowed.example", 1, 0, null, 1);
        assertRule(db, 12, "redirected.example", 2, 1, "8.8.4.4", 1);
        assertRule(db, 13, "source.example", 0, 0, null, 42);
        assertEquals(0, queryInt(db, "SELECT COUNT(*) FROM hosts_lists "
                + "WHERE kind != 0 OR generation != 0 OR reverse_host = ''"));
        try (Cursor cursor = db.query("PRAGMA foreign_key_check")) {
            assertFalse(cursor.moveToFirst());
        }
    }

    private static void assertRule(SupportSQLiteDatabase db, int id, String host, int type,
                                   int enabled, String redirection, int sourceId) {
        try (Cursor cursor = db.query("SELECT host, type, enabled, redirection, source_id "
                + "FROM hosts_lists WHERE id = ?", new Object[]{id})) {
            assertTrue(cursor.moveToFirst());
            assertEquals(host, cursor.getString(0));
            assertEquals(type, cursor.getInt(1));
            assertEquals(enabled, cursor.getInt(2));
            assertEquals(redirection, cursor.getString(3));
            assertEquals(sourceId, cursor.getInt(4));
            assertFalse(cursor.moveToNext());
        }
    }

    private static void assertCachedEntry(SupportSQLiteDatabase db) {
        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM host_entries"));
        assertEquals("cached.example", queryString(db, "SELECT host FROM host_entries"));
        assertEquals(2, queryInt(db, "SELECT type FROM host_entries"));
        assertEquals("9.9.9.9", queryString(db, "SELECT redirection FROM host_entries"));
    }

    private static String hostEntriesType(SupportSQLiteDatabase db) {
        return queryString(db, "SELECT type FROM sqlite_master WHERE name = 'host_entries'");
    }

    private static int queryInt(SupportSQLiteDatabase db, String sql) {
        try (Cursor cursor = db.query(sql)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private static String queryString(SupportSQLiteDatabase db, String sql) {
        try (Cursor cursor = db.query(sql)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }
}
