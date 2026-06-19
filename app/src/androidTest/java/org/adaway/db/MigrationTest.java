package org.adaway.db;

import static org.adaway.db.Migrations.MIGRATION_11_12;
import static org.adaway.db.Migrations.MIGRATION_12_13;
import static org.adaway.db.Migrations.MIGRATION_13_14;
import static org.adaway.db.Migrations.MIGRATION_14_15;
import static org.adaway.db.Migrations.MIGRATION_15_16;
import static org.adaway.db.Migrations.MIGRATION_16_17;
import static org.adaway.db.Migrations.MIGRATION_17_18;
import static org.adaway.db.Migrations.MIGRATION_18_19;
import static org.adaway.db.Migrations.MIGRATION_19_20;
import static org.adaway.db.Migrations.MIGRATION_20_21;
import static org.adaway.db.Migrations.MIGRATION_21_22;
import static org.adaway.db.Migrations.MIGRATION_22_23;
import static org.adaway.db.Migrations.MIGRATION_23_24;
import static org.adaway.db.Migrations.MIGRATION_24_25;
import static org.adaway.db.Migrations.MIGRATION_25_26;
import static org.adaway.db.Migrations.MIGRATION_26_27;
import static org.adaway.db.Migrations.MIGRATION_27_28;
import static org.adaway.db.Migrations.MIGRATION_28_29;
import static org.adaway.db.Migrations.MIGRATION_29_30;
import static org.adaway.db.Migrations.MIGRATION_30_31;
import static org.adaway.db.Migrations.MIGRATION_31_32;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MigrationTest {
    private static final String TEST_DB = "migration-test";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class);

    @Test
    public void migration11To12_preservesExactRowsAndAddsRuleKind() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 11);
        db.execSQL("INSERT INTO hosts_sources " +
                "(id, label, url, enabled, allowEnabled, redirectEnabled, size, skipped_count) " +
                "VALUES (1, 'User', 'content://user', 1, 1, 1, 0, 0)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, enabled, source_id, generation) " +
                "VALUES ('ads.example.com', 0, 1, 1, 0)");
        db.execSQL("INSERT INTO host_entries (host, type, redirection) " +
                "VALUES ('ads.example.com', 0, NULL)");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12);

        assertEquals(0, queryInt(db, "SELECT kind FROM hosts_lists WHERE host = 'ads.example.com'"));
        assertEquals(0, queryInt(db, "SELECT kind FROM host_entries WHERE host = 'ads.example.com'"));
        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM host_entries " +
                "WHERE host = 'ads.example.com' AND kind = 0 AND type = 0"));

        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('example.com', 0, 1, 1, 1, 0)");
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('example.com', 1, 0, NULL)");

        assertEquals(1, queryInt(db, "SELECT kind FROM hosts_lists WHERE host = 'example.com'"));
        assertEquals(1, queryInt(db, "SELECT kind FROM host_entries WHERE host = 'example.com'"));
        assertTrue(hasIndex(db, "index_hosts_lists_kind_host"));
        assertTrue(hasIndex(db, "index_host_entries_kind_host"));
    }

    @Test
    public void migration12To13_dropsRedundantHostIndexAndKeepsExactLookupIndexed()
            throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 12);
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('ads.example.com', 0, 0, NULL)");
        assertTrue(hasIndex(db, "index_host_entries_host"));
        assertTrue(hasIndex(db, "index_host_entries_kind_host"));
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13);

        assertFalse(hasIndex(db, "index_host_entries_host"));
        assertTrue(hasIndex(db, "index_host_entries_kind_host"));
        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM host_entries " +
                "WHERE host = 'ads.example.com' AND kind = 0 AND type = 0"));

        String plan = queryPlan(db, "EXPLAIN QUERY PLAN SELECT * FROM host_entries " +
                "WHERE host = 'ads.example.com' AND kind = 0 LIMIT 1");
        assertFalse(plan, plan.contains("index_host_entries_host"));
        assertTrue(plan, plan.contains("sqlite_autoindex_host_entries_1")
                || plan.contains("PRIMARY KEY"));
    }

    @Test
    public void migration13To14_rebuildsRootExportIndexAsCoveringIndex()
            throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 13);
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('ads.example.com', 0, 0, NULL)");
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('suffix.example.com', 1, 0, NULL)");
        assertTrue(hasIndex(db, "index_host_entries_kind_host"));
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14);

        assertTrue(hasIndex(db, "index_host_entries_kind_host"));
        assertEquals("kind,host,type,redirection",
                indexColumns(db, "index_host_entries_kind_host"));
        String plan = queryPlan(db, "EXPLAIN QUERY PLAN SELECT host, kind, type, redirection " +
                "FROM host_entries WHERE kind = 0");
        assertTrue(plan, plan.contains("COVERING INDEX index_host_entries_kind_host"));
    }

    @Test
    public void migration14To15_createsAndPopulatesMaterializedRootExport()
            throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 14);
        db.execSQL("INSERT OR REPLACE INTO hosts_meta (id, active_generation) VALUES (0, 2)");
        db.execSQL("INSERT INTO hosts_sources " +
                "(id, label, url, enabled, allowEnabled, redirectEnabled, size, skipped_count) " +
                "VALUES (1, 'User', 'content://user', 1, 1, 1, 0, 0)");
        db.execSQL("INSERT INTO hosts_sources " +
                "(id, label, url, enabled, allowEnabled, redirectEnabled, size, skipped_count) " +
                "VALUES (2, 'Source', 'https://example.test/hosts.txt', 1, 1, 1, 0, 0)");
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('ads.example.com', 0, 0, NULL)");
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('example.com', 1, 0, NULL)");
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('dupe.example.com', 0, 2, '1.1.1.1')");
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('dupe.example.com', 1, 0, NULL)");
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('allowed.example.com', 1, 0, NULL)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('allowed.example.com', 1, 0, 1, 2, 2)");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 15, true, MIGRATION_14_15);

        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type = 'table' AND name = 'root_host_entries'"));
        assertEquals(3, queryInt(db, "SELECT COUNT(*) FROM root_host_entries"));
        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM root_host_entries " +
                "WHERE host = 'ads.example.com' AND kind = 0 AND type = 0"));
        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM root_host_entries " +
                "WHERE host = 'example.com' AND kind = 0 AND type = 0"));
        assertEquals("1.1.1.1", queryString(db, "SELECT redirection FROM root_host_entries " +
                "WHERE host = 'dupe.example.com' AND type = 2"));
        assertEquals(0, queryInt(db, "SELECT COUNT(*) FROM root_host_entries " +
                "WHERE host = 'allowed.example.com'"));
    }

    @Test
    public void migration15To16_addsActiveAllowProbeIndexes() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 15);
        assertFalse(hasIndex(db, "index_hosts_lists_active_allow_source_kind_host"));
        assertFalse(hasIndex(db, "index_hosts_lists_active_allow_generation_source_kind_host"));
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 16, true, MIGRATION_15_16);

        assertTrue(hasIndex(db, "index_hosts_lists_active_allow_source_kind_host"));
        assertTrue(hasIndex(db, "index_hosts_lists_active_allow_generation_source_kind_host"));
        assertEquals("type,enabled,kind,source_id,host",
                indexColumns(db, "index_hosts_lists_active_allow_source_kind_host"));
        assertEquals("type,enabled,kind,generation,source_id,host",
                indexColumns(db, "index_hosts_lists_active_allow_generation_source_kind_host"));
    }

    @Test
    public void migration16To17_rebuildsHostEntriesWithoutRowid() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 16);
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('ads.example.com', 0, 0, NULL)");
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('example.com', 1, 0, NULL)");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 17, true, MIGRATION_16_17);

        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM host_entries " +
                "WHERE host = 'ads.example.com' AND kind = 0 AND type = 0"));
        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM host_entries " +
                "WHERE host = 'example.com' AND kind = 1 AND type = 0"));
        assertTrue(hasIndex(db, "index_host_entries_kind_host"));
        assertTrue(queryString(db, "SELECT sql FROM sqlite_master " +
                "WHERE type = 'table' AND name = 'host_entries'")
                .contains("WITHOUT ROWID"));
    }

    @Test
    public void migration17To18_rebuildsKindHostAsRuntimeImportCoveringIndex()
            throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 17);
        assertEquals("kind,host", indexColumns(db, "index_hosts_lists_kind_host"));
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 18, true, MIGRATION_17_18);

        assertTrue(hasIndex(db, "index_hosts_lists_kind_host"));
        assertEquals("kind,host,type,enabled,generation,source_id,redirection",
                indexColumns(db, "index_hosts_lists_kind_host"));
    }

    @Test
    public void migration18To19_createsRuntimeStatsTable() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 18);
        db.execSQL("INSERT OR REPLACE INTO hosts_meta (id, active_generation) VALUES (0, 2)");
        db.execSQL("INSERT INTO hosts_sources " +
                "(id, label, url, enabled, allowEnabled, redirectEnabled, size, skipped_count) " +
                "VALUES (1, 'User', 'content://user', 1, 1, 1, 0, 0)");
        db.execSQL("INSERT INTO hosts_sources " +
                "(id, label, url, enabled, allowEnabled, redirectEnabled, size, skipped_count) " +
                "VALUES (2, 'Source', 'https://example.test/hosts.txt', 1, 1, 1, 0, 0)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('active.example', 0, 0, 1, 2, 2)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('suffix.example', 0, 1, 1, 2, 2)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('user.example', 0, 0, 1, 1, 0)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('stale.example', 0, 0, 1, 2, 1)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('allowed.example', 1, 0, 1, 2, 2)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation, redirection) " +
                "VALUES ('redirect.example', 2, 0, 1, 2, 2, '1.1.1.1')");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 19, true, MIGRATION_18_19);

        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type = 'table' AND name = 'hosts_stats'"));
        assertEquals(3, queryInt(db,
                "SELECT blocked_count FROM hosts_stats WHERE id = 0"));
        assertEquals(2, queryInt(db,
                "SELECT blocked_exact_count FROM hosts_stats WHERE id = 0"));
        assertEquals(1, queryInt(db,
                "SELECT allowed_count FROM hosts_stats WHERE id = 0"));
        assertEquals(1, queryInt(db,
                "SELECT redirected_count FROM hosts_stats WHERE id = 0"));
        assertEquals(5, queryInt(db,
                "SELECT active_rule_count FROM hosts_stats WHERE id = 0"));
    }

    @Test
    public void migration19To20_backfillsSourceStatsMetadata() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 19);
        db.execSQL("INSERT OR REPLACE INTO hosts_meta (id, active_generation) VALUES (0, 2)");
        db.execSQL("INSERT INTO hosts_sources " +
                "(id, label, url, enabled, allowEnabled, redirectEnabled, size, skipped_count) " +
                "VALUES (1, 'User', 'content://user', 1, 1, 1, 0, 0)");
        db.execSQL("INSERT INTO hosts_sources " +
                "(id, label, url, enabled, allowEnabled, redirectEnabled, size, skipped_count) " +
                "VALUES (2, 'Source', 'https://example.test/hosts.txt', 1, 1, 1, 0, 0)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('active.example', 0, 0, 1, 2, 2)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('suffix.example', 0, 1, 1, 2, 2)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('disabled.example', 0, 0, 0, 2, 2)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('stale.example', 0, 0, 1, 2, 1)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('allowed.example', 1, 0, 1, 1, 0)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation, redirection) " +
                "VALUES ('redirect.example', 2, 0, 1, 2, 2, '1.1.1.1')");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 20, true, MIGRATION_19_20);

        assertEquals(4, queryInt(db,
                "SELECT size FROM hosts_sources WHERE id = 2"));
        assertEquals(3, queryInt(db,
                "SELECT active_rule_count FROM hosts_sources WHERE id = 2"));
        assertEquals(2, queryInt(db,
                "SELECT blocked_count FROM hosts_sources WHERE id = 2"));
        assertEquals(1, queryInt(db,
                "SELECT blocked_exact_count FROM hosts_sources WHERE id = 2"));
        assertEquals(1, queryInt(db,
                "SELECT allowed_count FROM hosts_sources WHERE id = 1"));
        assertEquals(2, queryInt(db,
                "SELECT blocked_count FROM hosts_stats WHERE id = 0"));
        assertEquals(1, queryInt(db,
                "SELECT blocked_exact_count FROM hosts_stats WHERE id = 0"));
        assertEquals(1, queryInt(db,
                "SELECT allowed_count FROM hosts_stats WHERE id = 0"));
        assertEquals(1, queryInt(db,
                "SELECT redirected_count FROM hosts_stats WHERE id = 0"));
        assertEquals(4, queryInt(db,
                "SELECT active_rule_count FROM hosts_stats WHERE id = 0"));
    }

    @Test
    public void migration20To21_addsFilterListsProvenanceColumns() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 20);
        db.execSQL("INSERT INTO hosts_sources " +
                "(id, label, url, enabled, allowEnabled, redirectEnabled, size, " +
                "skipped_count, active_rule_count, blocked_count, blocked_exact_count, " +
                "allowed_count, redirected_count) " +
                "VALUES (2, 'Source', 'https://example.test/hosts.txt', 1, 0, 0, 0, " +
                "0, 0, 0, 0, 0, 0)");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 21, true, MIGRATION_20_21);

        assertEquals(0, queryInt(db,
                "SELECT filter_list_compatibility_score FROM hosts_sources WHERE id = 2"));
        assertEquals(null, queryNullableString(db,
                "SELECT filter_list_id FROM hosts_sources WHERE id = 2"));
        assertEquals(null, queryNullableString(db,
                "SELECT filter_list_syntax_ids FROM hosts_sources WHERE id = 2"));
        assertEquals(null, queryNullableString(db,
                "SELECT filter_list_compatibility FROM hosts_sources WHERE id = 2"));
        assertEquals(null, queryNullableString(db,
                "SELECT filter_list_selected_url FROM hosts_sources WHERE id = 2"));
    }

    @Test
    public void migration21To22_addsAndBackfillsReverseHostKeys() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 21);
        db.execSQL("INSERT INTO hosts_sources " +
                "(id, label, url, enabled, allowEnabled, redirectEnabled, size, " +
                "skipped_count, active_rule_count, blocked_count, blocked_exact_count, " +
                "allowed_count, redirected_count, filter_list_compatibility_score) " +
                "VALUES (1, 'User', 'content://user', 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('Ads.Example.COM', 0, 0, 1, 1, 0)");
        db.execSQL("INSERT INTO hosts_lists " +
                "(host, type, kind, enabled, source_id, generation) " +
                "VALUES ('child.ads.example.com', 1, 1, 1, 1, 0)");
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('Ads.Example.COM', 0, 0, NULL)");
        db.execSQL("INSERT INTO host_entries (host, kind, type, redirection) " +
                "VALUES ('child.ads.example.com', 1, 0, NULL)");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 22, true, MIGRATION_21_22);

        assertEquals("com.example.ads", queryString(db,
                "SELECT reverse_host FROM hosts_lists WHERE host = 'Ads.Example.COM'"));
        assertEquals("com.example.ads.child", queryString(db,
                "SELECT reverse_host FROM hosts_lists WHERE host = 'child.ads.example.com'"));
        assertEquals("com.example.ads", queryString(db,
                "SELECT reverse_host FROM host_entries WHERE host = 'Ads.Example.COM'"));
        assertEquals("com.example.ads.child", queryString(db,
                "SELECT reverse_host FROM host_entries WHERE host = 'child.ads.example.com'"));
        assertEquals(0, queryInt(db,
                "SELECT COUNT(*) FROM hosts_lists WHERE reverse_host = ''"));
        assertEquals(0, queryInt(db,
                "SELECT COUNT(*) FROM host_entries WHERE reverse_host = ''"));
        assertTrue(hasIndex(db, "index_host_entries_kind_reverse_host"));
        assertTrue(hasIndex(db, "index_hosts_lists_active_generation_kind_host"));
        assertTrue(hasIndex(db, "index_hosts_lists_active_allow_source_kind_reverse_host"));
        assertTrue(hasIndex(db, "index_hosts_lists_active_allow_generation_kind_reverse_host"));
        assertEquals("kind,reverse_host,host",
                indexColumns(db, "index_host_entries_kind_reverse_host"));
        assertEquals("type,enabled,generation,kind,host,source_id,reverse_host,redirection",
                indexColumns(db, "index_hosts_lists_active_generation_kind_host"));
        assertEquals("kind,host,type,enabled,generation,source_id,redirection,reverse_host",
                indexColumns(db, "index_hosts_lists_kind_host"));
        assertFalse(queryString(db, "SELECT sql FROM sqlite_master " +
                "WHERE type = 'table' AND name = 'host_entries'")
                .contains("WITHOUT ROWID"));
    }

    @Test
    public void migration22To23_addsRootExportMaterializedState() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 22);
        db.execSQL("INSERT OR REPLACE INTO hosts_stats " +
                "(id, blocked_count, blocked_exact_count, allowed_count, redirected_count, " +
                "active_rule_count) VALUES (0, 1, 1, 0, 0, 1)");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 23, true, MIGRATION_22_23);

        assertTrue(hasColumn(db, "hosts_stats", "root_export_materialized"));
        assertEquals(0, queryInt(db,
                "SELECT root_export_materialized FROM hosts_stats WHERE id = 0"));
    }

    @Test
    public void migration23To24_addsRootExportReverseHostIndex() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 23);
        db.execSQL("INSERT INTO root_host_entries (host, kind, type, redirection) VALUES " +
                "('Ads.Example.COM', 0, 0, NULL), " +
                "('child.ads.example.com', 0, 0, NULL), " +
                "('redirect.example.com', 0, 2, '1.1.1.1')");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 24, true, MIGRATION_23_24);

        assertTrue(hasColumn(db, "root_host_entries", "reverse_host"));
        assertEquals("com.example.ads", queryString(db,
                "SELECT reverse_host FROM root_host_entries WHERE host = 'Ads.Example.COM'"));
        assertEquals("com.example.ads.child", queryString(db,
                "SELECT reverse_host FROM root_host_entries " +
                        "WHERE host = 'child.ads.example.com'"));
        assertEquals("com.example.redirect", queryString(db,
                "SELECT reverse_host FROM root_host_entries " +
                        "WHERE host = 'redirect.example.com'"));
        assertTrue(hasIndex(db, "index_root_host_entries_reverse_host"));
        assertEquals("reverse_host,host",
                indexColumns(db, "index_root_host_entries_reverse_host"));
        assertEquals(0, queryInt(db,
                "SELECT COUNT(*) FROM root_host_entries WHERE reverse_host = ''"));
    }

    @Test
    public void migration24To25_rebuildsRootExportWithoutRowid() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 24);
        db.execSQL("INSERT INTO root_host_entries " +
                "(host, reverse_host, kind, type, redirection) VALUES " +
                "('ads.example.com', 'com.example.ads', 0, 0, NULL), " +
                "('redirect.example.com', 'com.example.redirect', 0, 2, '1.1.1.1')");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 25, true, MIGRATION_24_25);

        assertEquals(2, queryInt(db, "SELECT COUNT(*) FROM root_host_entries"));
        assertEquals("1.1.1.1", queryString(db,
                "SELECT redirection FROM root_host_entries WHERE host = 'redirect.example.com'"));
        assertTrue(hasIndex(db, "index_root_host_entries_reverse_host"));
        assertEquals("reverse_host,host",
                indexColumns(db, "index_root_host_entries_reverse_host"));
        assertTrue(queryString(db, "SELECT sql FROM sqlite_master " +
                "WHERE type = 'table' AND name = 'root_host_entries'")
                .contains("WITHOUT ROWID"));
    }

    @Test
    public void migration25To26_rebuildsRootExportForAppendWrites() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 25);
        db.execSQL("INSERT INTO root_host_entries " +
                "(host, reverse_host, kind, type, redirection) VALUES " +
                "('ads.example.com', 'com.example.ads', 0, 0, NULL), " +
                "('redirect.example.com', 'com.example.redirect', 0, 2, '1.1.1.1')");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 26, true, MIGRATION_25_26);

        assertTrue(hasColumn(db, "root_host_entries", "id"));
        assertEquals(2, queryInt(db, "SELECT COUNT(*) FROM root_host_entries"));
        assertEquals("1.1.1.1", queryString(db,
                "SELECT redirection FROM root_host_entries WHERE host = 'redirect.example.com'"));
        assertTrue(hasIndex(db, "index_root_host_entries_host"));
        assertTrue(hasIndex(db, "index_root_host_entries_reverse_host"));
        assertEquals("host", indexColumns(db, "index_root_host_entries_host"));
        assertEquals("reverse_host,host",
                indexColumns(db, "index_root_host_entries_reverse_host"));
        String createSql = queryString(db, "SELECT sql FROM sqlite_master " +
                "WHERE type = 'table' AND name = 'root_host_entries'");
        assertFalse(createSql.contains("WITHOUT ROWID"));
        assertTrue(createSql.contains("PRIMARY KEY(`id`)"));
    }

    @Test
    public void migration26To27_createsRootExportStageTable() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 26);
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 27, true, MIGRATION_26_27);

        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type = 'table' AND name = 'root_host_entries_stage'"));
        assertTrue(hasColumn(db, "root_host_entries_stage", "host"));
        assertTrue(hasColumn(db, "root_host_entries_stage", "reverse_host"));
        assertTrue(hasColumn(db, "root_host_entries_stage", "source_id"));
        assertTrue(hasColumn(db, "root_host_entries_stage", "generation"));
        assertTrue(hasIndex(db, "index_root_host_entries_stage_source_generation"));
        assertTrue(hasIndex(db, "index_root_host_entries_stage_generation_source"));
        assertTrue(hasIndex(db, "index_root_host_entries_stage_reverse_host"));
        assertEquals("source_id,generation",
                indexColumns(db, "index_root_host_entries_stage_source_generation"));
        assertEquals("generation,source_id",
                indexColumns(db, "index_root_host_entries_stage_generation_source"));
        assertEquals("reverse_host,host",
                indexColumns(db, "index_root_host_entries_stage_reverse_host"));
    }

    @Test
    public void migration27To28_addsFilterListsDirectoryProvenanceColumns() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 27);
        db.execSQL("INSERT INTO hosts_sources " +
                "(id, label, url, enabled, allowEnabled, redirectEnabled, size, " +
                "skipped_count, active_rule_count, blocked_count, blocked_exact_count, " +
                "allowed_count, redirected_count, filter_list_compatibility_score) " +
                "VALUES (2, 'Source', 'https://example.test/hosts.txt', 1, 0, 0, 0, " +
                "0, 0, 0, 0, 0, 0, 0)");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 28, true, MIGRATION_27_28);

        assertTrue(hasColumn(db, "hosts_sources", "filter_list_name"));
        assertTrue(hasColumn(db, "hosts_sources", "filter_list_tag_ids"));
        assertTrue(hasColumn(db, "hosts_sources", "filter_list_language_ids"));
        assertEquals(null, queryNullableString(db,
                "SELECT filter_list_name FROM hosts_sources WHERE id = 2"));
        assertEquals(null, queryNullableString(db,
                "SELECT filter_list_tag_ids FROM hosts_sources WHERE id = 2"));
        assertEquals(null, queryNullableString(db,
                "SELECT filter_list_language_ids FROM hosts_sources WHERE id = 2"));
    }

    @Test
    public void migration28To29_dropsUnusedRootExportStageReverseIndex() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 28);
        assertTrue(hasIndex(db, "index_root_host_entries_stage_reverse_host"));
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 29, true, MIGRATION_28_29);

        assertTrue(hasIndex(db, "index_root_host_entries_stage_source_generation"));
        assertTrue(hasIndex(db, "index_root_host_entries_stage_generation_source"));
        assertFalse(hasIndex(db, "index_root_host_entries_stage_reverse_host"));
    }

    @Test
    public void migration29To30_dropsPersistentRootExportIndexes() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 29);
        assertTrue(hasIndex(db, "index_root_host_entries_host"));
        assertTrue(hasIndex(db, "index_root_host_entries_reverse_host"));
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 30, true, MIGRATION_29_30);

        assertFalse(hasIndex(db, "index_root_host_entries_host"));
        assertFalse(hasIndex(db, "index_root_host_entries_reverse_host"));
        assertTrue(hasIndex(db, "index_root_host_entries_stage_reverse_host"));
        assertEquals("reverse_host",
                indexColumns(db, "index_root_host_entries_stage_reverse_host"));
    }

    @Test
    public void migration30To31_addsStageBackedRootExportState() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 30);
        db.execSQL("INSERT OR REPLACE INTO hosts_stats " +
                "(id, blocked_count, blocked_exact_count, allowed_count, redirected_count, " +
                "active_rule_count, root_export_materialized) " +
                "VALUES (0, 1, 1, 0, 0, 1, 1)");
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 31, true, MIGRATION_30_31);

        assertTrue(hasColumn(db, "hosts_stats", "root_export_stage_materialized"));
        assertEquals(0, queryInt(db,
                "SELECT root_export_stage_materialized FROM hosts_stats WHERE id = 0"));
        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type = 'table' AND name = 'root_export_skip_stage_ids'"));
        assertTrue(hasColumn(db, "root_export_skip_stage_ids", "id"));
    }

    @Test
    public void migration31To32_makesRootStageReverseIndexCovering() throws Exception {
        SupportSQLiteDatabase db = this.helper.createDatabase(TEST_DB, 31);
        assertEquals("reverse_host",
                indexColumns(db, "index_root_host_entries_stage_reverse_host"));
        db.close();

        db = this.helper.runMigrationsAndValidate(TEST_DB, 32, true, MIGRATION_31_32);

        assertTrue(hasIndex(db, "index_root_host_entries_stage_reverse_host"));
        assertEquals("reverse_host,type,source_id,generation",
                indexColumns(db, "index_root_host_entries_stage_reverse_host"));
    }

    private static int queryInt(SupportSQLiteDatabase db, String sql) {
        try (Cursor cursor = db.query(sql)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private static boolean hasIndex(SupportSQLiteDatabase db, String name) {
        try (Cursor cursor = db.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
                new Object[]{name})) {
            cursor.moveToFirst();
            return cursor.getInt(0) == 1;
        }
    }

    private static boolean hasColumn(SupportSQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.query("PRAGMA table_info(`" + table + "`)")) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String indexColumns(SupportSQLiteDatabase db, String name) {
        StringBuilder columns = new StringBuilder();
        try (Cursor cursor = db.query("PRAGMA index_info(`" + name + "`)")) {
            while (cursor.moveToNext()) {
                if (columns.length() > 0) {
                    columns.append(',');
                }
                columns.append(cursor.getString(2));
            }
        }
        return columns.toString();
    }

    private static String queryPlan(SupportSQLiteDatabase db, String sql) {
        StringBuilder plan = new StringBuilder();
        try (Cursor cursor = db.query(sql)) {
            while (cursor.moveToNext()) {
                if (plan.length() > 0) {
                    plan.append('\n');
                }
                plan.append(cursor.getString(3));
            }
        }
        return plan.toString();
    }

    private static String queryString(SupportSQLiteDatabase db, String sql) {
        try (Cursor cursor = db.query(sql)) {
            cursor.moveToFirst();
            return cursor.getString(0);
        }
    }

    private static String queryNullableString(SupportSQLiteDatabase db, String sql) {
        try (Cursor cursor = db.query(sql)) {
            cursor.moveToFirst();
            return cursor.isNull(0) ? null : cursor.getString(0);
        }
    }
}
