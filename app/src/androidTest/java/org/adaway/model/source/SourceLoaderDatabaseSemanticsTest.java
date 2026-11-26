package org.adaway.model.source;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.db.entity.RuleKind.SUFFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    public void sourceTextCannotImpersonateEndMarkers() {
        int skipped = parseText("#endofqueuemarker\n#endofqueuemarker\n"
                + "0.0.0.0 after-marker.example\n");

        assertEquals(0, skipped);
        assertHostRow("after-marker.example", EXACT.getValue());
    }

    @Test
    public void utf8BomAndMixedLineEndingsPreserveFirstAndLastRules() {
        assertEquals(0, parseText("\ufeff0.0.0.0 first.example\r\n"
                + "0.0.0.0 middle.example\r0.0.0.0 last.example"));
        assertHostRow("first.example", EXACT.getValue());
        assertHostRow("middle.example", EXACT.getValue());
        assertHostRow("last.example", EXACT.getValue());
    }

    @Test
    public void oversizedLineFailsWithoutPublishingAPartialImport() {
        String oversized = "x".repeat(SourceLoader.MAX_LINE_LENGTH + 1);
        try {
            parseText("0.0.0.0 partial.example\n" + oversized);
            fail("An oversized line must fail the import");
        } catch (IllegalStateException expected) {
            assertNotNull(expected.getCause());
        }
        assertMissingHost("partial.example");
        assertEquals(0, hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, GENERATION));
    }

    @Test(timeout = 15000)
    public void interruptedParseJoinsWorkersBeforeReturningAndRollsBack() throws Exception {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            content.append("0.0.0.0 cancel-").append(i).append(".example\n");
        }
        CountDownLatch inserting = new CountDownLatch(1);
        CountDownLatch writerInterrupted = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        CountDownLatch writerExited = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Thread> writer = new AtomicReference<>();
        AtomicReference<Boolean> writerFinishedAtReturn = new AtomicReference<>(false);
        Thread caller = new Thread(() -> {
            try {
                new SourceLoader(source, GENERATION).parse(
                        new BufferedReader(new StringReader(content.toString())),
                        hostListItemDao, database.getOpenHelper().getWritableDatabase(),
                        count -> {
                            writer.set(Thread.currentThread());
                            inserting.countDown();
                            boolean interrupted = false;
                            try {
                                while (true) {
                                    try {
                                        releaseWriter.await();
                                        break;
                                    } catch (InterruptedException e) {
                                        interrupted = true;
                                        writerInterrupted.countDown();
                                    }
                                }
                            } finally {
                                if (interrupted) {
                                    Thread.currentThread().interrupt();
                                }
                                writerExited.countDown();
                            }
                        }, null);
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                writerFinishedAtReturn.set(writerExited.getCount() == 0);
                exited.countDown();
            }
        }, "SourceLoaderCancellationTest");
        caller.start();
        try {
            assertTrue(inserting.await(5, TimeUnit.SECONDS));
            caller.interrupt();
            assertTrue("Cancellation must interrupt this import's writer",
                    writerInterrupted.await(5, TimeUnit.SECONDS));
            assertFalse("parse() must wait for the active writer to finish",
                    exited.await(100, TimeUnit.MILLISECONDS));
            releaseWriter.countDown();
            assertTrue("Cancellation must stop the inserter", exited.await(5, TimeUnit.SECONDS));
            assertTrue("The writer must finish before parse() returns", writerFinishedAtReturn.get());
            assertTrue(failure.get() instanceof IllegalStateException);
            assertEquals(0,
                    hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, GENERATION));
            assertEquals(0L, scalarLong("SELECT COUNT(*) FROM root_host_entries_stage"));
            // Executor termination precedes the backing Thread's final run() return.
            // Join only this import's writer, not unrelated threads sharing its name.
            writer.get().join(1000);
            assertFalse("This import's writer must terminate", writer.get().isAlive());
        } finally {
            releaseWriter.countDown();
            caller.interrupt();
            caller.join(5000);
        }
    }

    private int parseText(String content) {
        return new SourceLoader(source, GENERATION).parse(
                new BufferedReader(new StringReader(content)), hostListItemDao,
                database.getOpenHelper().getWritableDatabase(), null, null);
    }

    @Test
    public void boundedDrainsRollBackRowsStageAndSeenKeysOnWriterFailure() {
        assertEquals(0, parseText("0.0.0.0 retained.example\n"));
        SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();
        db.execSQL("UPDATE hosts_meta SET active_generation = ? WHERE id = 0",
                new Object[]{GENERATION});
        int candidate = GENERATION + 1;
        int pendingLimit = 16_000;
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < 36_001; i++) {
            input.append("0.0.0.0 row-").append(i).append(".example\n");
        }
        SqlUpdateDeduper deduper = new SqlUpdateDeduper(db);
        try {
            // A different source has already succeeded in this update's shared deduper.
            HostsSource other = insertSource(database.hostsSourceDao(), SOURCE_ID + 1, false);
            assertEquals(0, new SourceLoader(other, candidate).parse(
                    new BufferedReader(new StringReader("0.0.0.0 other.example\n")),
                    hostListItemDao, db, null, deduper));
            AtomicLong accepted = new AtomicLong();
            RuntimeException injected = new IllegalStateException("test failure after SQL drains");
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new SourceLoader(source, candidate).parse(
                            new BufferedReader(new StringReader(input.toString())),
                            hostListItemDao, db, batch -> {
                                // The progress hook runs on the writer and sees its uncommitted rows.
                                long total = accepted.addAndGet(batch);
                                long pending = deduper.pendingCount();
                                assertTrue("Pending rows at " + total + " accepted: " + pending,
                                        pending <= pendingLimit);
                                if (total >= 34_000) {
                                    long flushed = deduper.count() - 1;
                                    assertTrue("Expected intermediate drains", flushed >= 2 * pendingLimit);
                                    assertEquals(total, flushed + pending);
                                    assertEquals(flushed, hostListItemDao.countSourceHostsForGeneration(
                                            SOURCE_ID, candidate));
                                    assertEquals(flushed, scalarLong("SELECT COUNT(*) FROM "
                                                    + "root_host_entries_stage WHERE source_id = ? AND generation = ?",
                                            SOURCE_ID, candidate));
                                    assertEquals(GENERATION, database.hostEntryDao().getActiveGeneration());
                                    assertHostRow("retained.example", EXACT.getValue());
                                    throw injected;
                                }
                            }, deduper));
            // Propagate an unexpected parser or assertion failure with its original stack.
            Throwable cause = failure;
            while (cause != null && cause != injected) {
                cause = cause.getCause();
            }
            if (cause != injected) {
                throw failure;
            }
            assertEquals(0L, deduper.pendingCount());
            assertEquals(1L, deduper.count());
            assertEquals(1L, scalarLong("SELECT COUNT(*) FROM update_seen_hosts "
                    + "WHERE source_id = ? AND host = 'other.example'", other.getId()));
            assertEquals(0, hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, candidate));
            assertEquals(0L, scalarLong("SELECT COUNT(*) FROM root_host_entries_stage "
                    + "WHERE source_id = ? AND generation = ?", SOURCE_ID, candidate));
            assertEquals(GENERATION, database.hostEntryDao().getActiveGeneration());
            assertEquals(BLOCKED, database.hostEntryDao().resolveEntry("retained.example").getType());
            assertEquals(1L, scalarLong("SELECT COUNT(*) FROM root_host_entries_stage "
                            + "WHERE source_id = ? AND generation = ? AND host = 'retained.example'",
                    SOURCE_ID, GENERATION));
        } finally {
            deduper.drop();
        }
        assertScratchTableAbsent();
    }

    @Test
    public void allowModePersistsOnlyRepresentableRulesInMixedInput() {
        source.setAllowEnabled(true);
        database.hostsSourceDao().update(source);
        int skipped = parseText("plain.example# note\n"
                + "*.wild.example\t# note\n"
                + "||suffix.example^\n"
                + "address=/dns.example/# # note\n"
                + "cosmetic.example##.badge\n"
                + "exception.example#@#.badge\n"
                + "*.procedure.example#?#.badge\n"
                + "extended.example#@?#.badge\n"
                + "||conditional.example^$third-party\n"
                + "@@||restricted.example^$document\n");

        assertAllowRow("plain.example", EXACT.getValue());
        assertAllowRow("*.wild.example", EXACT.getValue());
        assertAllowRow("suffix.example", SUFFIX.getValue());
        assertAllowRow("dns.example", SUFFIX.getValue());
        for (String host : new String[]{"cosmetic.example", "exception.example", "*.procedure.example",
                "extended.example", "conditional.example", "restricted.example"}) {
            assertMissingHost(host);
        }
        assertEquals(6, skipped);
        assertEquals(4, hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, GENERATION));
    }

    @Test
    public void blockListSkipsBothConditionalBranchesButKeepsUnconditionalRules() {
        int skipped = parseText("0.0.0.0 before.example\n  !#if env_browser\n"
                + "||first.example^\n!#if nested\n0.0.0.0 nested.example\n!#endif\n"
                + "!#else\nelse.example\n!#endif\n||after.example^");
        for (String host : new String[]{"first.example", "nested.example", "else.example"}) {
            assertMissingHost(host);
        }
        assertEquals(3, skipped);
        assertHostRow("before.example", EXACT.getValue());
        assertHostRow("after.example", SUFFIX.getValue());
        assertEquals(2, hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, GENERATION));
    }

    @Test
    public void unusableBlockRevisionKeepsThePreviouslyActiveRules() {
        assertEquals(0, parseText("0.0.0.0 retained.example\n"));
        SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();
        db.execSQL("UPDATE hosts_meta SET active_generation = ? WHERE id = 0", new Object[]{GENERATION});
        for (String revision : new String[]{"!#if browser\ninside.example\n!#endif\n",
                "unpublished.example\n!#if browser\n", "!#else\nunpublished.example\n",
                "##.global-ad-slot\n"}) {
            assertThrows(IllegalStateException.class, () -> new SourceLoader(source, GENERATION + 1)
                    .parse(new BufferedReader(new StringReader(revision)), hostListItemDao, db, null, null));
            assertEquals(0, hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, GENERATION + 1));
            assertEquals(GENERATION, database.hostEntryDao().getActiveGeneration());
            assertHostRow("retained.example", EXACT.getValue());
            assertEquals(BLOCKED, database.hostEntryDao().resolveEntry("retained.example").getType());
        }
    }

    @Test
    public void allowModeDoesNotFlattenConditionalSections() {
        source.setAllowEnabled(true);
        database.hostsSourceDao().update(source);
        int skipped = parseText("before.example\n!#if environment\nfirst.example\n"
                + "!#if nested\nnested.example\n!#endif\n!#else\nelse.example\n"
                + "!#endif\nafter.example");
        assertMissingHost("first.example");
        assertMissingHost("nested.example");
        assertMissingHost("else.example");
        assertEquals(3, skipped);
        assertAllowRow("before.example", EXACT.getValue());
        assertAllowRow("after.example", EXACT.getValue());
        assertEquals(2, hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, GENERATION));
    }

    @Test
    public void unsupportedOnlyAllowRevisionRetainsLastGoodGeneration() {
        source.setAllowEnabled(true);
        database.hostsSourceDao().update(source);
        assertEquals(0, parseText("retained.example\n"));
        SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();
        db.execSQL("UPDATE hosts_meta SET active_generation = ? WHERE id = 0",
                new Object[]{GENERATION});
        int candidate = GENERATION + 1;
        try {
            new SourceLoader(source, candidate).parse(new BufferedReader(new StringReader(
                    "rejected.example##.badge\n")), hostListItemDao, db, null, null);
            fail("An unsupported-only allow revision must not succeed");
        } catch (IllegalStateException expected) {
            assertEquals("Hosts source contains no supported rules", expected.getMessage());
        }
        assertEquals(0, hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, candidate));
        assertEquals(GENERATION, database.hostEntryDao().getActiveGeneration());
        assertAllowRow("retained.example", EXACT.getValue());
        assertTrue(database.hostEntryDao().isAllowedByActiveRule("retained.example"));
        assertFalse(database.hostEntryDao().isAllowedByActiveRule("rejected.example"));
        assertMissingHost("rejected.example");
    }

    @Test
    public void globalCosmeticAndUnbalancedConditionalsAreNotSuccessfulEmptyAllowLists() {
        source.setAllowEnabled(true);
        database.hostsSourceDao().update(source);
        for (String revision : new String[]{"##.badge\n", "!#if fixture\ninside.example\n!#endif",
                "unpublished.example\n!#if fixture\n", "!#else\nunpublished.example\n"}) {
            try {
                parseText(revision);
                fail("Unsupported or unbalanced revision should fail");
            } catch (IllegalStateException expected) {
                assertEquals(0, hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, GENERATION));
            }
        }
        assertEquals(0, parseText("# ordinary comment\n! list header\n"));
        assertEquals(0, hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, GENERATION));
    }

    private void assertAllowRow(String host, int kind) {
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM hosts_lists WHERE host = ? AND type = ? "
                        + "AND kind = ? AND enabled = 1 AND redirection IS NULL AND source_id = ? "
                        + "AND generation = ?", host, ALLOWED.getValue(), kind, SOURCE_ID, GENERATION));
    }

    @Test
    public void hostsAliasesUseFastPathDeduplicationAndIgnoreComments() {
        SqlUpdateDeduper deduper =
                new SqlUpdateDeduper(database.getOpenHelper().getWritableDatabase());
        try {
            assertEquals(0, new SourceLoader(source, GENERATION).parse(
                    new BufferedReader(new StringReader(
                            "0.0.0.0 one.example\ttwo.example one.example # comment.example\n"
                                    + "127.0.0.1 two.example three.example#ignored.example\n"
                                    + "::1 four.example five.example\n")),
                    hostListItemDao, database.getOpenHelper().getWritableDatabase(),
                    null, deduper));
        } finally {
            deduper.drop();
        }
        database.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE hosts_meta SET active_generation = ? WHERE id = 0",
                new Object[]{GENERATION});
        for (String host : new String[]{"one.example", "two.example", "three.example",
                "four.example", "five.example"}) {
            assertHostRow(host, EXACT.getValue());
            assertEquals(BLOCKED, database.hostEntryDao().resolveEntry(host).getType());
        }
        assertEquals(5, hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, GENERATION));
        assertEquals(5L, scalarLong("SELECT COUNT(*) FROM root_host_entries_stage"));
        assertMissingHost("comment.example");
        assertMissingHost("ignored.example");
        assertScratchTableAbsent();
    }

    @Test
    public void hostsAliasesUseRoomPathAndCountMalformedLinesOnce() {
        int skipped = new SourceLoader(source, GENERATION).parse(
                new BufferedReader(new StringReader(
                        "0.0.0.0 one.example two.example # comment.example\n"
                                + "127.0.0.1 localhost three.example *.invalid.example bad/host\n")),
                hostListItemDao);

        assertEquals(1, skipped);
        assertHostRow("one.example", EXACT.getValue());
        assertHostRow("two.example", EXACT.getValue());
        assertHostRow("three.example", EXACT.getValue());
        assertEquals(3, hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, GENERATION));
        assertMissingHost("localhost");
        assertMissingHost("*.invalid.example");
        assertMissingHost("bad/host");
        assertMissingHost("comment.example");
    }

    @Test
    public void redirectAliasesPreservePublicAddressAndRejectPrivateAddresses() {
        source.setRedirectEnabled(true);
        database.hostsSourceDao().update(source);
        assertEquals(1, parseText("9.9.9.9 one.example two.example # comment.example\n"
                + "192.168.1.1 private-one.example private-two.example\n"));

        for (String host : new String[]{"one.example", "two.example"}) {
            assertEquals(1L, scalarLong("SELECT COUNT(*) FROM hosts_lists "
                            + "WHERE host = ? AND type = ? AND redirection = ? "
                            + "AND source_id = ? AND generation = ?",
                    host, REDIRECTED.getValue(), "9.9.9.9", SOURCE_ID, GENERATION));
        }
        assertMissingHost("private-one.example");
        assertMissingHost("private-two.example");
        assertMissingHost("comment.example");
    }

    @Test
    public void hostsAliasesDoNotEnableRedirectsOrBroadenAllowListSyntax() {
        assertEquals(1, parseText("9.9.9.9 one.example two.example\n"
                + "0.0.0.0 blocked.example\n"));
        assertMissingHost("one.example");
        assertMissingHost("two.example");
        assertHostRow("blocked.example", EXACT.getValue());

        source.setAllowEnabled(true);
        database.hostsSourceDao().update(source);
        assertEquals(1, parseText("0.0.0.0 one.example two.example\nallowed.example\n"));
        assertMissingHost("one.example");
        assertMissingHost("two.example");
        assertEquals(1, hostListItemDao.countSourceHostsForGeneration(SOURCE_ID, GENERATION));
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
        assertEquals(host, 0L, scalarLong("SELECT COUNT(*) FROM hosts_lists WHERE host = ?", host));
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
        source.setUrl("https://parser.example.test/hosts-" + sourceId + ".txt");
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
