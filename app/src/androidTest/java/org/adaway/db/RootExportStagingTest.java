package org.adaway.db;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;

import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostsSource;
import org.adaway.util.Hostnames;
import org.junit.Test;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Database-only export regressions: no RootModel, system hosts writes or network. */
public class RootExportStagingTest extends DbTest {
    private static final int GENERATION = 2;
    private static final String FILLER_PREFIX = "filler";
    private static final String FILLER_SUFFIX = ".export.example";

    @Test
    public void stageBackedExportTracksSelectionDeletionAndCopiedFallback() {
        setActiveGeneration(GENERATION);
        insertSource(3, "https://toggle.example/hosts");
        insertSource(4, "https://deleted.example/hosts");
        stage("toggle.block.example", null, 3, GENERATION);
        stage("toggle.redirect.example", "9.9.9.9", 3, GENERATION);
        // An earlier duplicate from an inactive source must not shadow an active row.
        stage(FILLER_PREFIX + "0" + FILLER_SUFFIX, null, 3, GENERATION);
        stage("deleted.block.example", null, 4, GENERATION);
        stage("deleted.redirect.example", "8.8.8.8", 4, GENERATION);
        stage("stale.example", null, EXTERNAL_SOURCE_ID, GENERATION - 1);
        stage("future.example", null, EXTERNAL_SOURCE_ID, GENERATION + 1);
        stage("kept.redirect.example", "1.1.1.1", EXTERNAL_SOURCE_ID, GENERATION);
        int fillerRows = (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1;
        seedFiller(fillerRows);
        copyStageToRules();
        this.hostsSourceDao.applySourceSelection(3, false);
        this.hostsSourceDao.delete(getSourceFromId(4));

        Map<String, String> expected = new HashMap<>();
        expected.put("kept.redirect.example", "1.1.1.1");
        rebuild();
        assertTrue(this.hostEntryDao.hasStageMaterializedRootExportRows());
        assertExports(fillerRows, expected);
        assertInactiveControls();

        this.hostsSourceDao.applySourceSelection(3, true);
        rebuild();
        expected.put("toggle.block.example", null);
        expected.put("toggle.redirect.example", "9.9.9.9");
        assertTrue(this.hostEntryDao.hasStageMaterializedRootExportRows());
        assertExports(fillerRows, expected);
        assertEquals(BLOCKED, this.hostEntryDao.resolveEntry("toggle.block.example").getType());
        assertEquals("9.9.9.9",
                this.hostEntryDao.resolveEntry("toggle.redirect.example").getRedirection());

        this.hostsSourceDao.delete(getSourceFromId(3));
        rebuild();
        expected.remove("toggle.block.example");
        expected.remove("toggle.redirect.example");
        assertTrue(this.hostEntryDao.hasStageMaterializedRootExportRows());
        assertExports(fillerRows, expected);
        assertInactiveControls();
        assertEquals(5, scalar("SELECT COUNT(*) FROM root_host_entries_stage " +
                "WHERE source_id IN (3, 4)"));

        // Reuse the threshold-sized fixture to exercise the real copied-stage dispatch too.
        insertSource(5, "https://lower-priority.example/hosts");
        stage("priority.example", "1.1.1.1", 5, GENERATION);
        insertRedirectedHost("priority.example", "1.1.1.1", 5, GENERATION);
        stage("priority.example", "9.9.9.9", EXTERNAL_SOURCE_ID, GENERATION);
        insertRedirectedHost("priority.example", "9.9.9.9", EXTERNAL_SOURCE_ID, GENERATION);
        insertAllowedHost("*.unrelated.example", USER_SOURCE_ID, 0);
        rebuild();
        expected.put("priority.example", "9.9.9.9");
        assertFalse(this.hostEntryDao.hasStageMaterializedRootExportRows());
        assertEquals(fillerRows + expected.size(), scalar("SELECT COUNT(*) FROM root_host_entries"));
        assertExports(fillerRows, expected);
    }

    @Test
    public void copiedStageRedirectsUseCanonicalPriorityInBothImportOrders() {
        setActiveGeneration(GENERATION);
        insertSource(3, "https://lower-priority.example/hosts");
        insertSource(4, "https://disabled.example/hosts");
        for (boolean reverse : new boolean[]{false, true}) {
            writableDb().execSQL("DELETE FROM hosts_lists");
            writableDb().execSQL("DELETE FROM root_host_entries_stage");
            stage("priority.example", reverse ? "9.9.9.9" : "1.1.1.1",
                    reverse ? 2 : 3, GENERATION);
            stage("priority.example", reverse ? "1.1.1.1" : "9.9.9.9",
                    reverse ? 3 : 2, GENERATION);
            stage("same-source.example", reverse ? "1.1.1.1" : "9.9.9.9", 2, GENERATION);
            stage("same-source.example", reverse ? "9.9.9.9" : "1.1.1.1", 2, GENERATION);
            stage("same-source.example", "1.1.1.1", 2, GENERATION);
            stage("user.example", "1.1.1.1", 2, GENERATION);
            stage("user.example", "8.8.8.8", USER_SOURCE_ID, 0);
            stage("priority.example", "0.0.0.1", 2, GENERATION - 1);
            stage("disabled.example", "2.2.2.2", 4, GENERATION);
            stage("priority.example", null, 2, GENERATION);
            stage("kept.block.example", null, 2, GENERATION);
            stage("exact-allowed.example", null, 2, GENERATION);
            stage("child.suffix-allowed.example", null, 2, GENERATION);
            stage("child.wildcard-allowed.example", null, 2, GENERATION);
            copyStageToRules();
            this.hostsSourceDao.applySourceSelection(4, false);
            insertAllowedHost("exact-allowed.example", USER_SOURCE_ID, 0);
            insertSuffixAllowedHost("suffix-allowed.example", USER_SOURCE_ID, 0);
            insertAllowedHost("*.wildcard-allowed.example", USER_SOURCE_ID, 0);
            // Redirects still override allows; blocked rows still use stage IDs for allow skips.
            insertAllowedHost("priority.example", USER_SOURCE_ID, 0);
            this.db.runInTransaction(() -> {
                this.hostEntryDao.refreshStatsFromActiveRows();
                this.hostEntryDao.materializeRootExportFromStagedCandidates(
                        writableDb(), true, true, true, GENERATION,
                        scalar("SELECT COUNT(*) FROM root_host_entries_stage"));
            });
            Map<String, String> expected = new HashMap<>();
            expected.put("priority.example", "9.9.9.9");
            expected.put("same-source.example", "1.1.1.1");
            expected.put("user.example", "8.8.8.8");
            expected.put("kept.block.example", null);
            assertFalse(this.hostEntryDao.hasStageMaterializedRootExportRows());
            assertExports(0, expected);
            assertEquals(ALLOWED, this.hostEntryDao.resolveEntry("disabled.example").getType());
            assertEquals(ALLOWED, this.hostEntryDao.resolveEntry("exact-allowed.example").getType());
            assertEquals(ALLOWED,
                    this.hostEntryDao.resolveEntry("child.suffix-allowed.example").getType());
            assertEquals(ALLOWED,
                    this.hostEntryDao.resolveEntry("child.wildcard-allowed.example").getType());
        }
    }

    @Test
    public void stageReadersKeepUserRowsIndependentOfGenerationAndSourceSelection() {
        setActiveGeneration(GENERATION);
        stage("user.block.example", null, USER_SOURCE_ID, 0);
        stage("user.redirect.example", "8.8.8.8", USER_SOURCE_ID, 0);
        stage("external.example", null, EXTERNAL_SOURCE_ID, GENERATION);
        copyStageToRules();
        // User rows are intentionally independent of source enabled/generation predicates.
        this.hostsSourceDao.setSourceEnabled(USER_SOURCE_ID, false);
        this.hostsSourceDao.applySourceSelection(EXTERNAL_SOURCE_ID, false);
        this.hostEntryDao.refreshStatsFromActiveRows();
        this.hostEntryDao.setRootExportStageMaterialized(true);
        Map<String, String> expected = new HashMap<>();
        expected.put("user.block.example", null);
        expected.put("user.redirect.example", "8.8.8.8");
        assertExports(0, expected);
    }

    private void assertInactiveControls() {
        for (String host : new String[]{"toggle.block.example", "toggle.redirect.example",
                "deleted.block.example", "deleted.redirect.example", "stale.example",
                "future.example"}) {
            assertEquals(host, ALLOWED, this.hostEntryDao.resolveEntry(host).getType());
        }
        assertEquals(BLOCKED,
                this.hostEntryDao.resolveEntry(FILLER_PREFIX + "0" + FILLER_SUFFIX).getType());
    }

    private void rebuild() {
        this.db.runInTransaction(() -> {
            for (HostsSource source : this.hostsSourceDao.getAll()) {
                this.hostsSourceDao.updateActiveRuleStats(source.getId());
            }
            this.hostEntryDao.rebuildFromActiveGeneration(writableDb());
        });
        assertEquals(0, scalar("SELECT COUNT(*) FROM host_entries"));
    }

    private void stage(String host, String address, int sourceId, int generation) {
        writableDb().execSQL("INSERT INTO root_host_entries_stage " +
                        "(host, reverse_host, type, redirection, source_id, generation) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                new Object[]{host, Hostnames.reverseLabels(host),
                        address == null ? BLOCKED.getValue() : REDIRECTED.getValue(),
                        address, sourceId, generation});
    }

    private void seedFiller(int rowCount) {
        this.db.runInTransaction(() -> {
            for (int offset = 0; offset < rowCount; offset += 50_000) {
                int count = Math.min(50_000, rowCount - offset);
                writableDb().execSQL("WITH RECURSIVE numbers(n) AS (SELECT 0 UNION ALL " +
                        "SELECT n + 1 FROM numbers WHERE n + 1 < " + count + ") " +
                        "INSERT INTO root_host_entries_stage " +
                        "(host, reverse_host, type, redirection, source_id, generation) " +
                        "SELECT 'filler' || (n + " + offset + ") || '.export.example', " +
                        "'example.export.filler' || (n + " + offset + "), 0, NULL, 2, " +
                        GENERATION + " FROM numbers");
            }
        });
    }

    private void copyStageToRules() {
        this.db.runInTransaction(() -> writableDb().execSQL("INSERT INTO hosts_lists " +
                "(host, reverse_host, kind, type, redirection, source_id, generation, enabled) " +
                "SELECT host, reverse_host, 0, type, redirection, source_id, generation, 1 " +
                "FROM root_host_entries_stage"));
    }

    private SupportSQLiteDatabase writableDb() {
        return this.db.getOpenHelper().getWritableDatabase();
    }

    private long scalar(String sql) {
        try (Cursor cursor = writableDb().query(sql)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private void assertExports(int fillerRows, Map<String, String> expected) {
        long count = (long) fillerRows + expected.size();
        assertTrue(this.hostEntryDao.hasMaterializedRootExportRows());
        assertEquals(count, this.hostEntryDao.getMaterializedRootExportEntryCountNow());
        ExportLines listLines = new ExportLines(fillerRows, expected, false);
        for (HostEntry entry : this.hostEntryDao.getAllForRootHostsFile()) {
            assertEquals(EXACT, entry.getKind());
            listLines.accept(entry.getHost(), entry.getType() == REDIRECTED
                    ? entry.getRedirection() : "0.0.0.0");
        }
        listLines.assertComplete();
        ExportLines cursorLines = new ExportLines(fillerRows, expected, false);
        try (Cursor cursor = this.hostEntryDao.getRootHostsFileCursor()) {
            int host = cursor.getColumnIndexOrThrow("host");
            int type = cursor.getColumnIndexOrThrow("type");
            int address = cursor.getColumnIndexOrThrow("redirection");
            while (cursor.moveToNext()) {
                cursorLines.accept(cursor.getString(host), cursor.getInt(type) == 2
                        ? cursor.getString(address) : "0.0.0.0");
            }
        }
        cursorLines.assertComplete();
        assertChunks(fillerRows, expected, false);
        assertChunks(fillerRows, expected, true);
        for (Map.Entry<String, String> row : expected.entrySet()) {
            HostEntry runtime = this.hostEntryDao.resolveEntry(row.getKey());
            assertEquals(row.getValue() == null ? BLOCKED : REDIRECTED, runtime.getType());
            assertEquals(row.getValue(), runtime.getRedirection());
        }
    }

    private void assertChunks(int fillerRows, Map<String, String> expected, boolean ipv6) {
        ExportLines lines = new ExportLines(fillerRows, expected, ipv6);
        long afterId = 0;
        long total = 0;
        int limit = fillerRows == 0 ? 1 : 4096;
        boolean stage = this.hostEntryDao.hasStageMaterializedRootExportRows();
        while (true) {
            Cursor chunk = stage
                    ? (ipv6 ? this.hostEntryDao.getRootHostsFileChunkCursorStageMaterializedIpv6(
                            "0.0.0.0", "::", "\n", afterId, limit, GENERATION)
                            : this.hostEntryDao.getRootHostsFileChunkCursorStageMaterialized(
                                    "0.0.0.0", "\n", afterId, limit, GENERATION))
                    : (ipv6 ? this.hostEntryDao.getRootHostsFileChunkCursorMaterializedIpv6(
                            "0.0.0.0", "::", "\n", afterId, limit)
                            : this.hostEntryDao.getRootHostsFileChunkCursorMaterialized(
                                    "0.0.0.0", "\n", afterId, limit));
            try (Cursor cursor = chunk) {
                assertTrue(cursor.moveToFirst());
                int rows = cursor.getInt(cursor.getColumnIndexOrThrow("row_count"));
                if (rows == 0) {
                    break;
                }
                long lastId = cursor.getLong(cursor.getColumnIndexOrThrow("last_id"));
                assertTrue(lastId > afterId);
                afterId = lastId;
                total += rows;
                assertTrue(total <= (long) fillerRows + expected.size());
                String text = cursor.getString(cursor.getColumnIndexOrThrow("lines"));
                assertNotNull(text);
                for (String line : text.split("\n")) {
                    int separator = line.indexOf(' ');
                    assertTrue(separator > 0);
                    lines.accept(line.substring(separator + 1), line.substring(0, separator));
                }
            }
        }
        assertEquals((long) fillerRows + expected.size(), total);
        lines.assertComplete();
    }

    /** Track large fixtures with bitsets instead of retaining another full exported list. */
    private static class ExportLines {
        private final int fillerRows;
        private final boolean ipv6;
        private final BitSet ipv4Fillers = new BitSet();
        private final BitSet ipv6Fillers = new BitSet();
        private final Set<String> pending = new HashSet<>();

        ExportLines(int fillerRows, Map<String, String> expected, boolean ipv6) {
            this.fillerRows = fillerRows;
            this.ipv6 = ipv6;
            for (Map.Entry<String, String> row : expected.entrySet()) {
                this.pending.add((row.getValue() == null ? "0.0.0.0" : row.getValue()) +
                        " " + row.getKey());
                if (ipv6 && row.getValue() == null) {
                    this.pending.add(":: " + row.getKey());
                }
            }
        }

        void accept(String host, String address) {
            if (host.startsWith(FILLER_PREFIX) && host.endsWith(FILLER_SUFFIX)) {
                int index = Integer.parseInt(host.substring(FILLER_PREFIX.length(),
                        host.length() - FILLER_SUFFIX.length()));
                assertTrue(index >= 0 && index < this.fillerRows);
                assertTrue("0.0.0.0".equals(address) || (this.ipv6 && "::".equals(address)));
                BitSet seen = "::".equals(address) ? this.ipv6Fillers : this.ipv4Fillers;
                assertFalse("Duplicate filler " + host, seen.get(index));
                seen.set(index);
            } else {
                assertTrue("Unexpected or duplicate row: " + address + " " + host,
                        this.pending.remove(address + " " + host));
            }
        }

        void assertComplete() {
            assertTrue("Missing rows: " + this.pending, this.pending.isEmpty());
            assertEquals(this.fillerRows, this.ipv4Fillers.cardinality());
            assertEquals(this.ipv6 ? this.fillerRows : 0, this.ipv6Fillers.cardinality());
        }
    }
}
