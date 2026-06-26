package org.adaway.db;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.db.entity.RuleKind.SUFFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;

import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.util.Hostnames;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class tests {@link HostsSource} database manipulations.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SourceDbTest extends DbTest {
    private static final long HOST_ENTRY_SYNC_SCALE_BUDGET_MS = 5_000L;
    private static final long ROOT_EXPORT_CURSOR_SCALE_BUDGET_MS = 2_000L;
    private static final int LARGE_STAGE_REDIRECT_FILLER_ROWS =
            (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1;
    private static final int LARGE_STAGE_SEED_CHUNK_SIZE = 100_000;

    @Test
    public void testSourceCount() {
        // Test only external source is found
        List<HostsSource> sources = this.hostsSourceDao.getAll();
        assertEquals(1, sources.size());
        assertEquals("https://adaway.org/hosts.txt", sources.get(0).getUrl());
    }

    @Test
    public void testSourceDeletion() throws InterruptedException {
        // Insert blocked hosts
        insertBlockedHost("bingads.microsoft.com", EXTERNAL_SOURCE_ID);
        insertBlockedHost("ads.yahoo.com", EXTERNAL_SOURCE_ID);
        this.hostEntryDao.sync();
        // Test inserted blocked hosts
        assertEquals(2, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(2, this.hostEntryDao.getAll().size());
        // Delete source
        this.hostsSourceDao.delete(this.externalHostSource);
        this.hostEntryDao.sync();
        List<HostsSource> sources = this.hostsSourceDao.getAll();
        assertEquals(0, sources.size());
        // Check related hosts cleaning
        assertEquals(0, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(0, this.hostEntryDao.getAll().size());

    }

    @Test
    public void testRuntimeSyncUsesOnlyActiveGeneration() throws InterruptedException {
        setActiveGeneration(2);

        insertBlockedHost("active.ads.example", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("stale.ads.example", EXTERNAL_SOURCE_ID, 1);
        insertBlockedHost("future.ads.example", EXTERNAL_SOURCE_ID, 3);
        insertBlockedHost("user.ads.example", USER_SOURCE_ID, 0);

        insertBlockedHost("allowed-by-active.example", USER_SOURCE_ID, 0);
        insertAllowedHost("allowed-by-active.example", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("allowed-by-stale.example", USER_SOURCE_ID, 0);
        insertAllowedHost("allowed-by-stale.example", EXTERNAL_SOURCE_ID, 1);

        insertRedirectedHost("active.redirect.example", "1.1.1.1", EXTERNAL_SOURCE_ID, 2);
        insertRedirectedHost("stale.redirect.example", "2.2.2.2", EXTERNAL_SOURCE_ID, 1);

        this.hostEntryDao.sync();

        assertEquals(4, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(1, getOrAwaitValue(this.allowedHostCount).intValue());
        assertEquals(1, getOrAwaitValue(this.redirectedHostCount).intValue());
        assertEquals(4, this.hostEntryDao.getAll().size());
        assertEquals("1.1.1.1",
                this.hostEntryDao.getEntry("active.redirect.example").getRedirection());
        assertNull(this.hostEntryDao.getEntry("stale.ads.example"));
        assertNull(this.hostEntryDao.getEntry("future.ads.example"));
        assertNull(this.hostEntryDao.getEntry("allowed-by-active.example"));
        assertNull(this.hostEntryDao.getEntry("stale.redirect.example"));
    }

    @Test
    public void testRuntimeRedirectsUseCanonicalRedirectionWithinSource() {
        setActiveGeneration(2);

        insertRedirectedHost("same-source-redirect.example", "1.1.1.1",
                EXTERNAL_SOURCE_ID, 2);
        insertRedirectedHost("same-source-redirect.example", "9.9.9.9",
                EXTERNAL_SOURCE_ID, 2);

        this.hostEntryDao.sync();

        HostEntry runtimeEntry = this.hostEntryDao.getEntry("same-source-redirect.example");
        assertNotNull(runtimeEntry);
        assertEquals(REDIRECTED, runtimeEntry.getType());
        assertEquals("1.1.1.1", runtimeEntry.getRedirection());
        assertEquals("1.1.1.1",
                this.hostEntryDao.resolveEntry("same-source-redirect.example").getRedirection());
        Set<String> rootRows = rootRowsFromList();
        assertTrue(rootRows.contains("same-source-redirect.example|0|2|1.1.1.1"));
        assertFalse(rootRows.contains("same-source-redirect.example|0|2|9.9.9.9"));
        assertEquals(rootRows, rootRowsFromCursor());
    }

    @Test
    public void testSuffixRuntimeResolutionUsesOnlyActiveGeneration() {
        setActiveGeneration(2);

        insertSuffixBlockedHost("example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("stale.example", EXTERNAL_SOURCE_ID, 1);
        insertSuffixBlockedHost("future.example", EXTERNAL_SOURCE_ID, 3);

        this.hostEntryDao.sync();

        assertEquals(1, this.hostEntryDao.getAll().size());
        assertEquals(0, this.hostEntryDao.getAllExact().size());
        assertEquals(1, this.hostEntryDao.getBlockedEntryCountNow());
        assertEquals(0, this.hostEntryDao.getBlockedExactEntryCountNow());
        assertNotNull(this.hostEntryDao.getSuffixEntry("example.com"));
        assertEquals(SUFFIX, this.hostEntryDao.getSuffixEntry("example.com").getKind());
        assertNull(this.hostEntryDao.getEntry("sub.example.com"));

        List<HostEntry> rootEntries = this.hostEntryDao.getAllForRootHostsFile();
        assertEquals(1, rootEntries.size());
        assertEquals("example.com", rootEntries.get(0).getHost());
        assertEquals(EXACT, rootEntries.get(0).getKind());

        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("example.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("sub.example.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getRootTypeForHost("example.com"));
        assertEquals(ALLOWED, this.hostEntryDao.getRootTypeForHost("sub.example.com"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("badexample.com"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("sub.stale.example"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("sub.future.example"));
    }

    @Test
    public void testRuntimeResolutionReadsActiveTruthWithoutMaterializedRows() {
        setActiveGeneration(2);

        insertSuffixBlockedHost("direct.example.com", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("blocked.direct.example.com", EXTERNAL_SOURCE_ID, 2);
        insertAllowedHost("allowed.direct.example.com", USER_SOURCE_ID, 0);
        insertRedirectedHost("redirect.direct.example.com", "1.1.1.1", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("stale-direct.example.com", EXTERNAL_SOURCE_ID, 1);

        assertEquals(0, this.hostEntryDao.getAll().size());

        assertEquals(BLOCKED, this.hostEntryDao.resolveEntry("child.direct.example.com").getType());
        assertEquals(BLOCKED,
                this.hostEntryDao.resolveEntry("blocked.direct.example.com").getType());
        assertEquals(ALLOWED,
                this.hostEntryDao.resolveEntry("allowed.direct.example.com").getType());
        HostEntry redirect = this.hostEntryDao.resolveEntry("redirect.direct.example.com");
        assertEquals(REDIRECTED, redirect.getType());
        assertEquals("1.1.1.1", redirect.getRedirection());
        assertEquals(ALLOWED,
                this.hostEntryDao.resolveEntry("child.stale-direct.example.com").getType());

        assertEquals(BLOCKED,
                this.hostEntryDao.resolveRootEntry("direct.example.com").getType());
        assertEquals(ALLOWED,
                this.hostEntryDao.resolveRootEntry("child.direct.example.com").getType());
    }

    @Test
    public void testRootHostsExportReadsActiveTruthWithoutMaterializedRows() {
        setActiveGeneration(2);

        insertSuffixBlockedHost("root-direct.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("stale-root-direct.example.com", EXTERNAL_SOURCE_ID, 1);
        insertSuffixBlockedHost("allowed-root-direct.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixAllowedHost("allowed-root-direct.example.com", USER_SOURCE_ID, 0);
        insertRedirectedHost("redirect-root-direct.example.com", "1.1.1.1",
                EXTERNAL_SOURCE_ID, 2);

        assertEquals(0, this.hostEntryDao.getAll().size());
        assertEquals(0, queryInt("SELECT COUNT(*) FROM root_host_entries"));

        Set<String> expectedRows = new HashSet<>();
        expectedRows.add("redirect-root-direct.example.com|0|2|1.1.1.1");
        expectedRows.add("root-direct.example.com|0|0|null");

        assertEquals(expectedRows, rootRowsFromList());
        assertEquals(expectedRows, rootRowsFromCursor());
    }

    @Test
    public void testRootHostsExportDoesNotDuplicateSuffixWhenExactExists() {
        setActiveGeneration(2);

        insertBlockedHost("exact-before-source-suffix.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("exact-before-source-suffix.example.com", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("exact-before-user-suffix.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("exact-before-user-suffix.example.com", USER_SOURCE_ID, 0);

        assertEquals(0, this.hostEntryDao.getAll().size());
        assertEquals(0, queryInt("SELECT COUNT(*) FROM root_host_entries"));

        List<HostEntry> rootEntries = this.hostEntryDao.getAllForRootHostsFile();
        assertEquals(2, rootEntries.size());
        assertEquals(2, drainRootHostsCursor());
        assertEquals(rootRowsFromList(), rootRowsFromCursor());
    }

    @Test
    public void testBlockedStatsReadActiveTruthWithoutMaterializedRuntimeRows() {
        setActiveGeneration(2);

        insertBlockedHost("exact.stats.example", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("suffix.stats.example", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("stale.stats.example", EXTERNAL_SOURCE_ID, 1);
        insertBlockedHost("future.stats.example", EXTERNAL_SOURCE_ID, 3);
        insertBlockedHost("user.stats.example", USER_SOURCE_ID, 0);
        insertAllowedHost("allowed.stats.example", EXTERNAL_SOURCE_ID, 2);
        insertRedirectedHost("redirect.stats.example", "1.1.1.1", EXTERNAL_SOURCE_ID, 2);
        this.hostsSourceDao.updateActiveRuleStats(EXTERNAL_SOURCE_ID);

        this.hostEntryDao.clear();
        assertEquals(0, this.hostEntryDao.getAll().size());

        this.hostEntryDao.refreshStatsFromActiveGeneration();

        assertEquals(3, this.hostEntryDao.getBlockedEntryCountNow());
        assertEquals(2, this.hostEntryDao.getBlockedExactEntryCountNow());
        assertEquals(5, this.hostEntryDao.getActiveRuntimeRuleCountNow());
    }

    @Test
    public void testSourceSizeCanBeUpdatedForSingleGeneration() {
        insertBlockedHost("old.ads.example", EXTERNAL_SOURCE_ID, 1);
        insertBlockedHost("new.ads.example", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("new-tracker.example", EXTERNAL_SOURCE_ID, 2);

        this.hostsSourceDao.updateSizeForGeneration(EXTERNAL_SOURCE_ID, 2);

        assertEquals(2, getSourceFromId(EXTERNAL_SOURCE_ID).getSize());
    }

    @Test
    public void testAllowRulesOverrideSuffixBlocksAtRuntime() {
        setActiveGeneration(2);

        insertSuffixBlockedHost("example.com", EXTERNAL_SOURCE_ID, 2);
        insertAllowedHost("ads.example.com", USER_SOURCE_ID, 0);

        this.hostEntryDao.sync();

        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("ads.example.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("tracker.example.com"));

        insertBlockedHost("cdn.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixAllowedHost("example.com", USER_SOURCE_ID, 0);
        this.hostEntryDao.sync();

        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("cdn.example.com"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("tracker.example.com"));
        assertNull(this.hostEntryDao.getEntry("cdn.example.com"));
        assertNull(this.hostEntryDao.getSuffixEntry("example.com"));
        assertEquals(0, this.hostEntryDao.getAllForRootHostsFile().size());
    }

    @Test
    public void testSuffixAllowRulesRemoveNestedExactAndSuffixRuntimeRows() {
        setActiveGeneration(2);

        insertBlockedHost("child.ads.example.com", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("other.example.net", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("trackers.ads.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("other-trackers.example.net", EXTERNAL_SOURCE_ID, 2);
        insertSuffixAllowedHost("ads.example.com", USER_SOURCE_ID, 0);

        this.hostEntryDao.sync();

        assertNull(this.hostEntryDao.getEntry("child.ads.example.com"));
        assertNull(this.hostEntryDao.getSuffixEntry("trackers.ads.example.com"));
        assertNotNull(this.hostEntryDao.getEntry("other.example.net"));
        assertNotNull(this.hostEntryDao.getSuffixEntry("other-trackers.example.net"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("child.ads.example.com"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("deep.trackers.ads.example.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("other.example.net"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("deep.other-trackers.example.net"));
    }

    @Test
    public void testActiveGenerationSuffixAllowRulesRemoveNestedRuntimeRowsOnly() {
        setActiveGeneration(2);

        insertBlockedHost("child.active.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("trackers.active.example.com", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("child.stale.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("trackers.stale.example.com", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("child.future.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("trackers.future.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixAllowedHost("active.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixAllowedHost("stale.example.com", EXTERNAL_SOURCE_ID, 1);
        insertSuffixAllowedHost("future.example.com", EXTERNAL_SOURCE_ID, 3);

        this.hostEntryDao.sync();

        assertNull(this.hostEntryDao.getEntry("child.active.example.com"));
        assertNull(this.hostEntryDao.getSuffixEntry("trackers.active.example.com"));
        assertNotNull(this.hostEntryDao.getEntry("child.stale.example.com"));
        assertNotNull(this.hostEntryDao.getSuffixEntry("trackers.stale.example.com"));
        assertNotNull(this.hostEntryDao.getEntry("child.future.example.com"));
        assertNotNull(this.hostEntryDao.getSuffixEntry("trackers.future.example.com"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("child.active.example.com"));
        assertEquals(ALLOWED,
                this.hostEntryDao.getTypeForHost("deep.trackers.active.example.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("child.stale.example.com"));
        assertEquals(BLOCKED,
                this.hostEntryDao.getTypeForHost("deep.trackers.stale.example.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("child.future.example.com"));
        assertEquals(BLOCKED,
                this.hostEntryDao.getTypeForHost("deep.trackers.future.example.com"));
    }

    @Test
    public void testSuffixAllowRulesRespectLabelBoundaries() {
        setActiveGeneration(2);

        insertBlockedHost("ads.example.com", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("child.ads.example.com", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("badads.example.com", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("ads.example.com.evil.org", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("notads.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("deep.child.ads.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixAllowedHost("ads.example.com", USER_SOURCE_ID, 0);

        this.hostEntryDao.sync();

        assertNull(this.hostEntryDao.getEntry("ads.example.com"));
        assertNull(this.hostEntryDao.getEntry("child.ads.example.com"));
        assertNull(this.hostEntryDao.getSuffixEntry("deep.child.ads.example.com"));
        assertNotNull(this.hostEntryDao.getEntry("badads.example.com"));
        assertNotNull(this.hostEntryDao.getEntry("ads.example.com.evil.org"));
        assertNotNull(this.hostEntryDao.getEntry("notads.example.com"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("ads.example.com"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("deep.child.ads.example.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("badads.example.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("ads.example.com.evil.org"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("notads.example.com"));
    }

    @Test
    public void testRuntimeSyncCanonicalizesMixedCaseHosts() {
        setActiveGeneration(2);

        insertBlockedHost("Ads.Example.COM", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("Trackers.Example.COM", EXTERNAL_SOURCE_ID, 2);
        insertRedirectedHost("Redirect.Example.COM", "1.1.1.1", EXTERNAL_SOURCE_ID, 2);

        this.hostEntryDao.sync();

        assertNotNull(this.hostEntryDao.getEntry("ads.example.com"));
        assertNull(this.hostEntryDao.getEntry("Ads.Example.COM"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("pixel.trackers.example.com"));
        assertEquals("1.1.1.1", this.hostEntryDao.getEntry("redirect.example.com").getRedirection());
    }

    @Test
    public void testRootHostsExportDoesNotDuplicateExactRedirectAndSuffixBlock() {
        setActiveGeneration(2);

        insertSuffixBlockedHost("example.com", EXTERNAL_SOURCE_ID, 2);
        insertRedirectedHost("example.com", "1.1.1.1", USER_SOURCE_ID, 0);

        this.hostEntryDao.sync();

        List<HostEntry> rootEntries = this.hostEntryDao.getAllForRootHostsFile();
        assertEquals(1, rootEntries.size());
        assertEquals("example.com", rootEntries.get(0).getHost());
        assertEquals(REDIRECTED, rootEntries.get(0).getType());
        assertEquals("1.1.1.1", rootEntries.get(0).getRedirection());
        assertEquals(REDIRECTED, this.hostEntryDao.getTypeForHost("example.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("tracker.example.com"));
    }

    @Test
    public void testRuntimeSyncUsesSetBasedRulesAtScale() {
        setActiveGeneration(2);
        insertRuntimeScaleFixture();

        this.hostEntryDao.sync();
        long medianMs = medianElapsedMs(() -> this.hostEntryDao.sync());
        System.out.println("HostEntryDao.sync scale medianMs=" + medianMs);

        assertEquals(2200, this.hostEntryDao.getBlockedEntryCountNow());
        assertEquals(2202, this.hostEntryDao.getAll().size());
        assertNull(this.hostEntryDao.getEntry("ads0.example.com"));
        assertNull(this.hostEntryDao.getEntry("ads199.example.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("ads200.example.com"));
        assertNull(this.hostEntryDao.getSuffixEntry("suffix0.example.com"));
        assertEquals(ALLOWED, this.hostEntryDao.getTypeForHost("tracker.suffix0.example.com"));
        assertEquals(BLOCKED, this.hostEntryDao.getTypeForHost("tracker.suffix100.example.com"));
        assertEquals(REDIRECTED, this.hostEntryDao.getEntry("redirect-priority.example.com").getType());
        assertEquals("1.1.1.1",
                this.hostEntryDao.getEntry("redirect-priority.example.com").getRedirection());
        assertNull(this.hostEntryDao.getEntry("stale-redirect.example.com"));
        assertTrue("HostEntryDao.sync scale median exceeded " + HOST_ENTRY_SYNC_SCALE_BUDGET_MS
                        + "ms: " + medianMs + "ms",
                medianMs < HOST_ENTRY_SYNC_SCALE_BUDGET_MS);
    }

    @Test
    public void testRootHostsCursorExportStaysBoundedAtScale() {
        setActiveGeneration(2);
        insertRuntimeScaleFixture();
        this.hostEntryDao.sync();

        long medianMs = medianElapsedMs(() -> assertEquals(2202, drainRootHostsCursor()));
        System.out.println("HostEntryDao.rootCursor scale medianMs=" + medianMs);

        assertTrue("Root hosts cursor export median exceeded " + ROOT_EXPORT_CURSOR_SCALE_BUDGET_MS
                        + "ms: " + medianMs + "ms",
                medianMs < ROOT_EXPORT_CURSOR_SCALE_BUDGET_MS);
    }

    @Test
    public void testRuntimeRebuildWithTransactionDbRestoresRootExportIndex() {
        setActiveGeneration(2);
        insertBlockedHost("ads.example.com", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("suffix.example.com", EXTERNAL_SOURCE_ID, 2);

        SupportSQLiteDatabase writableDb = this.db.getOpenHelper().getWritableDatabase();
        writableDb.execSQL("DROP INDEX IF EXISTS `index_host_entries_kind_host`");
        this.hostEntryDao.rebuildFromActiveGeneration(writableDb);

        assertEquals(2, this.hostEntryDao.getBlockedEntryCountNow());
        assertTrue(hasIndex("index_host_entries_kind_host"));
        assertTrue(queryPlan("EXPLAIN QUERY PLAN SELECT host, kind, type, redirection " +
                "FROM host_entries WHERE kind = 0")
                .contains("COVERING INDEX index_host_entries_kind_host"));
    }

    @Test
    public void testBlockedRuntimeImportStreamsExistingKindHostIndex() {
        String userPlan = queryPlan("EXPLAIN QUERY PLAN " +
                "SELECT host, kind, 0, MIN(redirection) FROM hosts_lists " +
                "INDEXED BY index_hosts_lists_type_enabled_source_id " +
                "WHERE type = 0 AND enabled = 1 AND source_id = 1 " +
                "GROUP BY host, kind ORDER BY host, kind");
        assertTrue(userPlan, userPlan.contains("INDEX index_hosts_lists_type_enabled_source_id"));

        String exactPlan = queryPlan("EXPLAIN QUERY PLAN " +
                "SELECT host, 0, 0, redirection FROM hosts_lists " +
                "INDEXED BY index_hosts_lists_active_generation_kind_host " +
                "WHERE kind = 0 AND type = 0 AND enabled = 1 " +
                "AND generation = 2 AND source_id != 1 ORDER BY host");
        assertTrue(exactPlan,
                exactPlan.contains("COVERING INDEX index_hosts_lists_active_generation_kind_host"));
        assertFalse(exactPlan, exactPlan.contains("USE TEMP B-TREE"));

        String suffixPlan = queryPlan("EXPLAIN QUERY PLAN " +
                "SELECT host, 1, 0, redirection FROM hosts_lists " +
                "INDEXED BY index_hosts_lists_active_generation_kind_host " +
                "WHERE kind = 1 AND type = 0 AND enabled = 1 " +
                "AND generation = 2 AND source_id != 1 ORDER BY host");
        assertTrue(suffixPlan,
                suffixPlan.contains("COVERING INDEX index_hosts_lists_active_generation_kind_host"));
        assertFalse(suffixPlan, suffixPlan.contains("USE TEMP B-TREE"));
    }

    @Test
    public void testSuffixAllowDeleteUsesReverseHostIndexes() {
        assertTrue(hasIndex("index_host_entries_kind_reverse_host"));
        assertTrue(hasIndex("index_hosts_lists_active_allow_source_kind_reverse_host"));
        assertTrue(hasIndex("index_hosts_lists_active_allow_generation_kind_reverse_host"));

        String exactPlan = queryPlan("EXPLAIN QUERY PLAN " +
                "WITH active_suffix_allow AS (" +
                "SELECT reverse_host FROM hosts_lists " +
                "INDEXED BY index_hosts_lists_active_allow_source_kind_reverse_host " +
                "WHERE type = 1 AND enabled = 1 AND kind = 1 AND source_id = 1 " +
                "UNION ALL SELECT reverse_host FROM hosts_lists " +
                "INDEXED BY index_hosts_lists_active_allow_generation_kind_reverse_host " +
                "WHERE type = 1 AND enabled = 1 AND kind = 1 " +
                "AND generation = 2 AND source_id != 1) " +
                "SELECT entry.host FROM active_suffix_allow AS allowed " +
                "JOIN host_entries AS entry INDEXED BY index_host_entries_kind_reverse_host " +
                "ON entry.kind = 0 AND entry.reverse_host = allowed.reverse_host " +
                "UNION ALL SELECT entry.host FROM active_suffix_allow AS allowed " +
                "JOIN host_entries AS entry INDEXED BY index_host_entries_kind_reverse_host " +
                "ON entry.kind = 0 " +
                "AND entry.reverse_host >= allowed.reverse_host || '.' " +
                "AND entry.reverse_host < allowed.reverse_host || '/'");
        assertTrue(exactPlan, exactPlan.contains("index_host_entries_kind_reverse_host"));
        assertFalse(exactPlan, exactPlan.contains("SCAN entry"));
        assertFalse(exactPlan, exactPlan.contains("USE TEMP B-TREE"));

        String suffixPlan = exactPlan.replace("entry.kind = 0", "entry.kind = 1");
        assertTrue(suffixPlan, suffixPlan.contains("index_host_entries_kind_reverse_host"));
        assertFalse(suffixPlan, suffixPlan.contains("SCAN entry"));
        assertFalse(suffixPlan, suffixPlan.contains("USE TEMP B-TREE"));
    }

    @Test
    public void testRuntimeRebuildMaterializesRootExportRows() {
        setActiveGeneration(2);
        insertSuffixBlockedHost("example.com", EXTERNAL_SOURCE_ID, 2);
        insertAllowedHost("example.com", EXTERNAL_SOURCE_ID, 2);
        insertRedirectedHost("example.com", "1.1.1.1", USER_SOURCE_ID, 0);
        insertSuffixBlockedHost("blocked.example.net", EXTERNAL_SOURCE_ID, 2);
        insertAllowedHost("ads.blocked.example.net", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("allowed.example.org", EXTERNAL_SOURCE_ID, 2);
        insertAllowedHost("allowed.example.org", EXTERNAL_SOURCE_ID, 2);

        this.hostEntryDao.sync();

        assertEquals(2, queryInt("SELECT COUNT(*) FROM root_host_entries"));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM root_host_entries " +
                "WHERE host = 'example.com' AND type = 2 AND redirection = '1.1.1.1'"));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM root_host_entries " +
                "WHERE host = 'blocked.example.net' AND type = 0"));
        assertEquals(0, queryInt("SELECT COUNT(*) FROM root_host_entries " +
                "WHERE host = 'allowed.example.org'"));
        assertEquals(2, drainRootHostsCursor());
    }

    @Test
    public void testRootHostsMaterializedCursorBuildsFileLines() {
        setActiveGeneration(2);
        insertBlockedHost("blocked-line.example", EXTERNAL_SOURCE_ID, 2);
        insertRedirectedHost("redirect-line.example", "1.1.1.1", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("suffix-line.example", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("allowed-line.example", EXTERNAL_SOURCE_ID, 2);
        insertAllowedHost("allowed-line.example", USER_SOURCE_ID, 0);

        this.hostEntryDao.sync();

        assertTrue(this.hostEntryDao.hasMaterializedRootExportRows());
        assertRootLines(Arrays.asList(
                "0.0.0.0 blocked-line.example",
                "1.1.1.1 redirect-line.example",
                "0.0.0.0 suffix-line.example"),
                rootLinesFromMaterializedCursorRows("0.0.0.0", "::", false));
        assertRootLines(Arrays.asList(
                "0.0.0.0 blocked-line.example",
                "1.1.1.1 redirect-line.example",
                "0.0.0.0 suffix-line.example",
                ":: blocked-line.example",
                ":: suffix-line.example"),
                rootLinesFromMaterializedCursorRows("0.0.0.0", "::", true));
    }

    @Test
    public void testRootHostsCursorReadsActiveTruthAndMatchesListApi() {
        setActiveGeneration(2);
        insertRuntimeScaleFixture();

        this.hostEntryDao.sync();

        Set<String> listRows = rootRowsFromList();
        Set<String> cursorRows = rootRowsFromCursor();
        assertEquals(listRows, cursorRows);
        assertEquals(0, queryInt("SELECT COUNT(*) FROM (" +
                "SELECT host FROM root_host_entries GROUP BY host HAVING COUNT(*) > 1)"));
        assertTrue(this.hostEntryDao.hasMaterializedRootExportRows());

        String plan = queryPlan("EXPLAIN QUERY PLAN " +
                "SELECT host, type, redirection FROM root_host_entries");
        assertTrue(plan, plan.contains("root_host_entries"));
        assertFalse(plan, plan.contains("SCAN host_entries"));
        assertFalse(plan, plan.contains("SEARCH host_entries"));
    }

    @Test
    public void testRootExportKeepsMaterializedStateWhenValidOutputIsEmpty() {
        setActiveGeneration(2);
        insertSuffixBlockedHost("empty-root.example", EXTERNAL_SOURCE_ID, 2);
        insertSuffixAllowedHost("empty-root.example", USER_SOURCE_ID, 0);

        this.hostEntryDao.sync();

        assertEquals(0, queryInt("SELECT COUNT(*) FROM root_host_entries"));
        assertTrue(this.hostEntryDao.hasMaterializedRootExportRows());
        assertTrue(rootRowsFromList().isEmpty());
        assertTrue(rootRowsFromCursor().isEmpty());
    }

    @Test
    public void testLargeRuntimeSkipStillMaterializesRootExportRows() {
        setActiveGeneration(2);
        insertBlockedHost("large-root.example", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("large-suffix.example", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("duplicate-large.example", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("duplicate-large.example", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("allowed-exact.example", EXTERNAL_SOURCE_ID, 2);
        insertSuffixBlockedHost("allowed-by-suffix.example", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("redirect-shadow.example", EXTERNAL_SOURCE_ID, 2);
        insertRedirectedHost("redirect-shadow.example", "2.2.2.2", EXTERNAL_SOURCE_ID, 2);
        insertAllowedHost("allowed-exact.example", USER_SOURCE_ID, 0);
        insertSuffixAllowedHost("allowed-by-suffix.example", USER_SOURCE_ID, 0);
        insertRedirectedHost("large-redirect.example", "1.1.1.1", EXTERNAL_SOURCE_ID, 2);
        this.hostsSourceDao.updateRuleStats(EXTERNAL_SOURCE_ID,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                7, 5, 0, 2);

        this.hostEntryDao.sync();

        assertEquals(0, queryInt("SELECT COUNT(*) FROM host_entries"));
        assertEquals(5, queryInt("SELECT COUNT(*) FROM root_host_entries"));
        assertTrue(this.hostEntryDao.hasMaterializedRootExportRows());
        Set<String> rootRows = rootRowsFromList();
        assertTrue(rootRows.contains("large-root.example|0|0|null"));
        assertTrue(rootRows.contains("large-suffix.example|0|0|null"));
        assertTrue(rootRows.contains("duplicate-large.example|0|0|null"));
        assertTrue(rootRows.contains("large-redirect.example|0|2|1.1.1.1"));
        assertTrue(rootRows.contains("redirect-shadow.example|0|2|2.2.2.2"));
        assertFalse(rootRows.contains("allowed-exact.example|0|0|null"));
        assertFalse(rootRows.contains("allowed-by-suffix.example|0|0|null"));
        assertFalse(rootRows.contains("redirect-shadow.example|0|0|null"));
        assertEquals(rootRows, rootRowsFromCursor());
    }

    @Test
    public void testLargeRuntimeSkipRedirectsUseSourcePriorityBeforeRedirectionValue() {
        setActiveGeneration(2);
        insertSource(3, "https://lower-priority.example/hosts.txt");
        insertRedirectedHost("large-redirect-priority.example", "9.9.9.9",
                EXTERNAL_SOURCE_ID, 2);
        insertRedirectedHost("large-redirect-priority.example", "1.1.1.1", 3, 2);
        this.hostsSourceDao.updateRuleStats(EXTERNAL_SOURCE_ID,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                0, 0, 0, 1);
        this.hostsSourceDao.updateRuleStats(3, 1, 1, 0, 0, 0, 1);

        SupportSQLiteDatabase writableDb = this.db.getOpenHelper().getWritableDatabase();
        this.db.runInTransaction(() ->
                this.hostEntryDao.rebuildFromActiveGeneration(writableDb));

        assertEquals(0, queryInt("SELECT COUNT(*) FROM host_entries"));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM root_host_entries"));
        Set<String> rootRows = rootRowsFromList();
        assertTrue(rootRows.contains("large-redirect-priority.example|0|2|9.9.9.9"));
        assertFalse(rootRows.contains("large-redirect-priority.example|0|2|1.1.1.1"));
        assertEquals(rootRows, rootRowsFromCursor());
    }

    @Test
    public void testLargeRuntimeSkipUsesCompleteRootExportStage() {
        setActiveGeneration(2);
        insertSource(3, "https://lower-priority.example/hosts.txt");
        insertAllowedHost("stage-allowed.example", USER_SOURCE_ID, 0);
        insertSuffixAllowedHost("stage-allowed-suffix.example", USER_SOURCE_ID, 0);
        SupportSQLiteDatabase writableDb = this.db.getOpenHelper().getWritableDatabase();
        insertRootStageHost(writableDb, "stage-root.example", BLOCKED, null,
                EXTERNAL_SOURCE_ID, 2);
        insertRootStageHost(writableDb, "stage-root.example", BLOCKED, null,
                EXTERNAL_SOURCE_ID, 2);
        insertRootStageHost(writableDb, "stage-allowed.example", BLOCKED, null,
                EXTERNAL_SOURCE_ID, 2);
        insertRootStageHost(writableDb, "blocked.stage-allowed-suffix.example", BLOCKED, null,
                EXTERNAL_SOURCE_ID, 2);
        insertRootStageHost(writableDb, "stage-redirect.example", REDIRECTED, "1.1.1.1",
                EXTERNAL_SOURCE_ID, 2);
        insertRootStageHost(writableDb, "stage-redirect-shadow.example", BLOCKED, null,
                EXTERNAL_SOURCE_ID, 2);
        insertRootStageHost(writableDb, "stage-redirect-shadow.example", REDIRECTED, "2.2.2.2",
                EXTERNAL_SOURCE_ID, 2);
        insertRootStageHost(writableDb, "stage-redirect-priority.example", REDIRECTED, "9.9.9.9",
                EXTERNAL_SOURCE_ID, 2);
        insertRootStageHost(writableDb, "stage-redirect-priority.example", REDIRECTED, "1.1.1.1",
                3, 2);
        insertRootStageFillerHosts(writableDb, LARGE_STAGE_REDIRECT_FILLER_ROWS,
                EXTERNAL_SOURCE_ID, 2);
        int externalBlockedRows = LARGE_STAGE_REDIRECT_FILLER_ROWS + 5;
        int externalRedirectedRows = 3;
        this.hostsSourceDao.updateRuleStats(EXTERNAL_SOURCE_ID,
                externalBlockedRows + externalRedirectedRows,
                externalBlockedRows + externalRedirectedRows,
                externalBlockedRows, externalBlockedRows, 0, externalRedirectedRows);
        this.hostsSourceDao.updateRuleStats(3, 1, 1, 0, 0, 0, 1);

        this.db.runInTransaction(() ->
                this.hostEntryDao.rebuildFromActiveGeneration(writableDb));

        long expectedRootRows = (long) LARGE_STAGE_REDIRECT_FILLER_ROWS + 4;
        assertEquals(0, queryInt("SELECT COUNT(*) FROM host_entries"));
        assertEquals(0, queryInt("SELECT COUNT(*) FROM root_host_entries"));
        assertTrue(this.hostEntryDao.hasMaterializedRootExportRows());
        assertTrue(this.hostEntryDao.hasStageMaterializedRootExportRows());
        assertEquals(expectedRootRows, this.hostEntryDao.getMaterializedRootExportEntryCountNow());
        assertRootCursorContainsAndOmits(expectedRootRows,
                new HashSet<>(Arrays.asList(
                        "stage-root.example|0|0|null",
                        "stage-redirect.example|0|2|1.1.1.1",
                        "stage-redirect-shadow.example|0|2|2.2.2.2",
                        "stage-redirect-priority.example|0|2|9.9.9.9"
                )),
                new HashSet<>(Arrays.asList(
                        "stage-redirect-priority.example|0|2|1.1.1.1",
                        "stage-allowed.example|0|0|null",
                        "blocked.stage-allowed-suffix.example|0|0|null",
                        "stage-redirect-shadow.example|0|0|null"
                )));
    }

    @Test
    public void testIncompleteRootExportStageFallsBackToActiveRules() {
        setActiveGeneration(2);
        insertBlockedHost("fallback-only.example", EXTERNAL_SOURCE_ID, 2);
        SupportSQLiteDatabase writableDb = this.db.getOpenHelper().getWritableDatabase();
        insertRootStageHost(writableDb, "stage-only.example", BLOCKED, null,
                EXTERNAL_SOURCE_ID, 2);
        this.hostsSourceDao.updateRuleStats(EXTERNAL_SOURCE_ID,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                2, 2, 0, 0);

        this.db.runInTransaction(() ->
                this.hostEntryDao.rebuildFromActiveGeneration(writableDb));

        assertEquals(0, queryInt("SELECT COUNT(*) FROM host_entries"));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM root_host_entries"));
        Set<String> rootRows = rootRowsFromList();
        assertTrue(rootRows.contains("fallback-only.example|0|0|null"));
        assertFalse(rootRows.contains("stage-only.example|0|0|null"));
        assertEquals(rootRows, rootRowsFromCursor());
    }

    @Test
    public void testCompleteRootExportStageRequiresPerSourceCounts() {
        setActiveGeneration(2);
        insertSource(3, "https://second-source.example/hosts.txt");
        insertBlockedHost("fallback-source2.example", EXTERNAL_SOURCE_ID, 2);
        insertBlockedHost("fallback-source3.example", 3, 2);
        SupportSQLiteDatabase writableDb = this.db.getOpenHelper().getWritableDatabase();
        insertRootStageHost(writableDb, "stage-source2-a.example", BLOCKED, null,
                EXTERNAL_SOURCE_ID, 2);
        insertRootStageHost(writableDb, "stage-source2-b.example", BLOCKED, null,
                EXTERNAL_SOURCE_ID, 2);
        this.hostsSourceDao.updateRuleStats(EXTERNAL_SOURCE_ID,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                1, 1, 0, 0);
        this.hostsSourceDao.updateRuleStats(3, 1, 1, 1, 1, 0, 0);

        this.db.runInTransaction(() ->
                this.hostEntryDao.rebuildFromActiveGeneration(writableDb));

        assertEquals(0, queryInt("SELECT COUNT(*) FROM host_entries"));
        assertEquals(2, queryInt("SELECT COUNT(*) FROM root_host_entries"));
        Set<String> rootRows = rootRowsFromList();
        assertTrue(rootRows.contains("fallback-source2.example|0|0|null"));
        assertTrue(rootRows.contains("fallback-source3.example|0|0|null"));
        assertFalse(rootRows.contains("stage-source2-a.example|0|0|null"));
        assertFalse(rootRows.contains("stage-source2-b.example|0|0|null"));
        assertEquals(rootRows, rootRowsFromCursor());
    }

    @Test
    public void testCompleteRootExportStageIgnoresDisabledSources() {
        setActiveGeneration(2);
        insertSource(3, "https://disabled.example/hosts.txt");
        this.hostsSourceDao.toggleEnabled(getSourceFromId(3));
        SupportSQLiteDatabase writableDb = this.db.getOpenHelper().getWritableDatabase();
        insertRootStageHost(writableDb, "enabled-stage.example", BLOCKED, null,
                EXTERNAL_SOURCE_ID, 2);
        insertRootStageHost(writableDb, "disabled-stage.example", BLOCKED, null, 3, 2);
        this.hostsSourceDao.updateRuleStats(EXTERNAL_SOURCE_ID,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                1, 1, 0, 0);
        this.hostsSourceDao.updateRuleStats(3, 1, 1, 1, 1, 0, 0);

        this.db.runInTransaction(() ->
                this.hostEntryDao.rebuildFromActiveGeneration(writableDb));

        assertEquals(0, queryInt("SELECT COUNT(*) FROM host_entries"));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM root_host_entries"));
        Set<String> rootRows = rootRowsFromList();
        assertTrue(rootRows.contains("enabled-stage.example|0|0|null"));
        assertFalse(rootRows.contains("disabled-stage.example|0|0|null"));
        assertEquals(rootRows, rootRowsFromCursor());
    }

    @Test
    public void testCompleteRootExportStageDedupesBlockedDuplicatesWithoutRedirects() {
        setActiveGeneration(2);
        SupportSQLiteDatabase writableDb = this.db.getOpenHelper().getWritableDatabase();
        insertRootStageHost(writableDb, "duplicate-stage.example", BLOCKED, null,
                EXTERNAL_SOURCE_ID, 2);
        insertRootStageHost(writableDb, "duplicate-stage.example", BLOCKED, null,
                EXTERNAL_SOURCE_ID, 2);
        this.hostsSourceDao.updateRuleStats(EXTERNAL_SOURCE_ID,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                (int) HostEntryDao.MATERIALIZED_RUNTIME_CACHE_MAX_ROWS + 1,
                2, 2, 0, 0);

        this.db.runInTransaction(() ->
                this.hostEntryDao.rebuildFromActiveGeneration(writableDb));

        assertEquals(0, queryInt("SELECT COUNT(*) FROM host_entries"));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM root_host_entries"));
        Set<String> rootRows = rootRowsFromList();
        assertTrue(rootRows.contains("duplicate-stage.example|0|0|null"));
        assertEquals(rootRows, rootRowsFromCursor());
    }

    @Test
    public void testBlockedHostsFromDisabledSource() throws InterruptedException {
        // Insert blocked hosts
        insertBlockedHost("advertising.apple.com", USER_SOURCE_ID);
        insertBlockedHost("an.facebook.com", USER_SOURCE_ID);
        insertBlockedHost("ads.google.com", USER_SOURCE_ID);
        insertBlockedHost("bingads.microsoft.com", EXTERNAL_SOURCE_ID);
        insertBlockedHost("ads.yahoo.com", EXTERNAL_SOURCE_ID);
        this.hostEntryDao.sync();
        // Test inserted blocked hosts
        assertEquals(5, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size());
        // Disabled external source
        this.hostsSourceDao.toggleEnabled(this.externalHostSource);
        this.hostEntryDao.sync();
        assertEquals(3, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(3, this.hostEntryDao.getAll().size());
        // Re-enable external source
        this.hostsSourceDao.toggleEnabled(this.externalHostSource);
        this.hostEntryDao.sync();
        assertEquals(5, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(5, this.hostEntryDao.getAll().size());
    }

    @Test
    public void testAllowedHostsFromDisabledSource() throws InterruptedException {
        // Insert blocked and allowed host
        insertBlockedHost("adaway.org", USER_SOURCE_ID);
        insertAllowedHost("adaway.org", EXTERNAL_SOURCE_ID);
        this.hostEntryDao.sync();
        // Test inserted blocked hosts
        assertEquals(1, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(1, getOrAwaitValue(this.allowedHostCount).intValue());
        assertEquals(0, this.hostEntryDao.getAll().size());
        // Disabled a source
        this.hostsSourceDao.toggleEnabled(this.externalHostSource);
        this.hostEntryDao.sync();
        assertEquals(1, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(0, getOrAwaitValue(this.allowedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());
        // Re-enable a source
        this.hostsSourceDao.toggleEnabled(this.externalHostSource);
        this.hostEntryDao.sync();
        assertEquals(1, getOrAwaitValue(this.blockedHostCount).intValue());
        assertEquals(1, getOrAwaitValue(this.allowedHostCount).intValue());
        assertEquals(0, this.hostEntryDao.getAll().size());
    }

    @Test
    public void testRedirectedHostsFromDisabledSource() throws InterruptedException {
        // Insert redirected hosts
        insertRedirectedHost("github.com", "1.1.1.1", USER_SOURCE_ID);
        insertRedirectedHost("github.com", "2.2.2.2", EXTERNAL_SOURCE_ID);
        this.hostEntryDao.sync();
        // Test inserted blocked hosts
        assertEquals(1, getOrAwaitValue(this.redirectedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());
        // Disabled a source
        this.hostsSourceDao.toggleEnabled(this.externalHostSource);
        this.hostEntryDao.sync();
        assertEquals(1, getOrAwaitValue(this.redirectedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());
        // Re-enable a source
        this.hostsSourceDao.toggleEnabled(this.externalHostSource);
        this.hostEntryDao.sync();
        assertEquals(1, getOrAwaitValue(this.redirectedHostCount).intValue());
        assertEquals(1, this.hostEntryDao.getAll().size());
    }

    private static HostListItem item(String host, ListType type, RuleKind kind, int sourceId,
            int generation, String redirection) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(type);
        item.setKind(kind);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        item.setGeneration(generation);
        item.setRedirection(redirection);
        return item;
    }

    private void insertRuntimeScaleFixture() {
        List<HostListItem> items = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            items.add(item("ads" + i + ".example.com", BLOCKED, EXACT, EXTERNAL_SOURCE_ID, 2, null));
        }
        for (int i = 0; i < 500; i++) {
            items.add(item("suffix" + i + ".example.com", BLOCKED, SUFFIX, EXTERNAL_SOURCE_ID, 2, null));
        }
        for (int i = 0; i < 200; i++) {
            items.add(item("ads" + i + ".example.com", ALLOWED, EXACT, USER_SOURCE_ID, 0, null));
        }
        for (int i = 0; i < 100; i++) {
            items.add(item("suffix" + i + ".example.com", ALLOWED, SUFFIX, USER_SOURCE_ID, 0, null));
        }
        items.add(item("redirect-priority.example.com", REDIRECTED, EXACT,
                EXTERNAL_SOURCE_ID, 2, "2.2.2.2"));
        items.add(item("redirect-priority.example.com", REDIRECTED, EXACT,
                USER_SOURCE_ID, 0, "1.1.1.1"));
        items.add(item("active-redirect.example.com", REDIRECTED, EXACT,
                EXTERNAL_SOURCE_ID, 2, "3.3.3.3"));
        items.add(item("stale-redirect.example.com", REDIRECTED, EXACT,
                EXTERNAL_SOURCE_ID, 1, "4.4.4.4"));
        this.hostListItemDao.insert(items);
    }

    private int drainRootHostsCursor() {
        int count = 0;
        try (Cursor cursor = this.hostEntryDao.getRootHostsFileCursor()) {
            while (cursor.moveToNext()) {
                count++;
            }
        }
        return count;
    }

    private void insertRootStageHost(SupportSQLiteDatabase db, String host, ListType type,
            String redirection, int sourceId, int generation) {
        String normalizedHost = Hostnames.normalize(host);
        db.execSQL("INSERT INTO root_host_entries_stage " +
                        "(host, reverse_host, type, redirection, source_id, generation) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                new Object[]{
                        normalizedHost,
                        Hostnames.reverseLabels(normalizedHost),
                        type.getValue(),
                        redirection,
                        sourceId,
                        generation
                });
    }

    private void insertRootStageFillerHosts(SupportSQLiteDatabase db, int rowCount,
            int sourceId, int generation) {
        createLargeStageSeedDigits(db);
        for (int offset = 0; offset < rowCount; offset += LARGE_STAGE_SEED_CHUNK_SIZE) {
            int count = Math.min(LARGE_STAGE_SEED_CHUNK_SIZE, rowCount - offset);
            String i = "(" + offset + " + numbers.`n`)";
            db.execSQL("WITH `numbers`(`n`) AS (" +
                    "SELECT ones.`n` + 10 * tens.`n` + 100 * hundreds.`n` + " +
                    "1000 * thousands.`n` + 10000 * ten_thousands.`n` " +
                    "FROM `large_stage_digits` AS ones " +
                    "CROSS JOIN `large_stage_digits` AS tens " +
                    "CROSS JOIN `large_stage_digits` AS hundreds " +
                    "CROSS JOIN `large_stage_digits` AS thousands " +
                    "CROSS JOIN `large_stage_digits` AS ten_thousands " +
                    "WHERE ones.`n` + 10 * tens.`n` + 100 * hundreds.`n` + " +
                    "1000 * thousands.`n` + 10000 * ten_thousands.`n` < " + count + ") " +
                    "INSERT INTO `root_host_entries_stage` " +
                    "(`host`, `reverse_host`, `type`, `redirection`, `source_id`, " +
                    "`generation`) SELECT 'stage-filler' || " + i +
                    " || '.large.example', 'example.large.stage-filler' || " + i +
                    ", " + BLOCKED.getValue() + ", NULL, " + sourceId + ", " +
                    generation + " FROM `numbers`");
        }
    }

    private static void createLargeStageSeedDigits(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS `large_stage_digits` " +
                "(`n` INTEGER PRIMARY KEY) WITHOUT ROWID");
        db.execSQL("DELETE FROM `large_stage_digits`");
        db.execSQL("INSERT INTO `large_stage_digits` (`n`) VALUES " +
                "(0), (1), (2), (3), (4), (5), (6), (7), (8), (9)");
    }

    private void assertRootCursorContainsAndOmits(long expectedCount, Set<String> expectedRows,
            Set<String> rejectedRows) {
        Set<String> missingRows = new HashSet<>(expectedRows);
        Set<String> unexpectedRows = new HashSet<>();
        long count = 0L;
        try (Cursor cursor = this.hostEntryDao.getRootHostsFileCursor()) {
            int host = cursor.getColumnIndexOrThrow("host");
            int type = cursor.getColumnIndexOrThrow("type");
            int redirection = cursor.getColumnIndexOrThrow("redirection");
            while (cursor.moveToNext()) {
                count++;
                String row = cursor.getString(host) + "|" + EXACT.getValue() + "|"
                        + cursor.getInt(type) + "|" + cursor.getString(redirection);
                missingRows.remove(row);
                if (rejectedRows.contains(row)) {
                    unexpectedRows.add(row);
                }
            }
        }
        assertEquals(expectedCount, count);
        assertTrue("Missing root rows: " + missingRows, missingRows.isEmpty());
        assertTrue("Unexpected root rows: " + unexpectedRows, unexpectedRows.isEmpty());
    }

    private Set<String> rootRowsFromList() {
        Set<String> rows = new HashSet<>();
        for (HostEntry entry : this.hostEntryDao.getAllForRootHostsFile()) {
            rows.add(entry.getHost() + "|" + entry.getKind().getValue() + "|"
                    + entry.getType().getValue() + "|" + entry.getRedirection());
        }
        return rows;
    }

    private Set<String> rootRowsFromCursor() {
        Set<String> rows = new HashSet<>();
        try (Cursor cursor = this.hostEntryDao.getRootHostsFileCursor()) {
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

    private static void assertRootLines(List<String> expected, List<String> actual) {
        Collections.sort(expected);
        Collections.sort(actual);
        assertEquals(expected, actual);
    }

    private List<String> rootLinesFromMaterializedCursorRows(String ipv4, String ipv6,
            boolean enableIpv6) {
        List<String> rows = new ArrayList<>();
        try (Cursor cursor = this.hostEntryDao.getRootHostsFileCursorMaterialized()) {
            int host = cursor.getColumnIndexOrThrow("host");
            int type = cursor.getColumnIndexOrThrow("type");
            int redirection = cursor.getColumnIndexOrThrow("redirection");
            while (cursor.moveToNext()) {
                String hostname = cursor.getString(host);
                if (cursor.getInt(type) == REDIRECTED.getValue()) {
                    rows.add(cursor.getString(redirection) + " " + hostname);
                    continue;
                }
                rows.add(ipv4 + " " + hostname);
                if (enableIpv6) {
                    rows.add(ipv6 + " " + hostname);
                }
            }
        }
        return rows;
    }

    private boolean hasIndex(String name) {
        try (Cursor cursor = this.db.getOpenHelper().getReadableDatabase()
                .query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
                        new Object[]{name})) {
            cursor.moveToFirst();
            return cursor.getInt(0) == 1;
        }
    }

    private String queryPlan(String sql) {
        StringBuilder plan = new StringBuilder();
        try (Cursor cursor = this.db.getOpenHelper().getReadableDatabase().query(sql)) {
            while (cursor.moveToNext()) {
                plan.append(cursor.getString(3)).append('\n');
            }
        }
        return plan.toString();
    }

    private int queryInt(String sql) {
        try (Cursor cursor = this.db.getOpenHelper().getReadableDatabase().query(sql)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    private static long medianElapsedMs(Runnable runnable) {
        long[] samples = new long[3];
        for (int i = 0; i < samples.length; i++) {
            long startedAtMs = android.os.SystemClock.elapsedRealtime();
            runnable.run();
            samples[i] = android.os.SystemClock.elapsedRealtime() - startedAtMs;
        }
        Arrays.sort(samples);
        return samples[1];
    }
}
