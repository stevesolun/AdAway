package org.adaway.ui.hosts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Lightweight wiring guards; behavioral Android tests remain the runtime proof. */
public class FilterListsRecoveryContractTest {
    @Test
    public void recoveryIdsAreRecordedBeforeDisplayCapAndPersistedTogether() throws IOException {
        String worker = source("hosts/FilterListsSubscribeAllWorker.java");
        String append = section(worker, "private void appendOutcome(",
                "private static String formatReviewPreview(");
        assertTrue(append.indexOf("noUrlIds.add(") < append.indexOf("outcomeLedger.size()"));
        assertTrue(append.indexOf("unsupportedIds.add(") < append.indexOf("outcomeLedger.size()"));
        String persist = section(worker, "static void persistLastRunOutcomes(",
                "static boolean shouldEmitProgressUpdate(");
        assertTrue(persist.contains("putStringSet(KEY_LAST_RUN_NO_URL_IDS"));
        assertTrue(persist.contains("putStringSet(KEY_LAST_RUN_UNSUPPORTED_IDS"));
        assertTrue(persist.contains("putString(KEY_LAST_RUN_OUTCOMES"));
        assertTrue(persist.contains(".commit()"));
    }

    @Test
    public void retryAndSelectedCommandsBothRouteLargeScopesToPersistence() throws IOException {
        String fragment = source("discover/DiscoverFilterListsFragment.java");
        String selected = section(fragment, "private void subscribeAll(",
                "private void confirmUnsubscribeAll(");
        String retry = section(fragment, "private void retryLastRunNoUrlLists(",
                "private void reviewLastRunUnsupportedLists(");
        for (String command : new String[]{selected, retry}) {
            assertTrue(command.contains("MAX_INLINE_SCOPE_IDS"));
            assertTrue(command.contains("buildPersistedScopeInput("));
            assertTrue(command.contains("catch (IOException e)"));
            assertTrue(command.indexOf("buildPersistedScopeInput(")
                    < command.indexOf(".setInputData(inputData)"));
        }
        assertTrue(retry.contains("readLastRunNoUrlIds(prefs)"));
        assertTrue(retry.contains("editor.remove(KEY_URL_PREFIX + id)"));
        assertTrue(retry.contains("resolvedUrlCache.remove(id)"));
        assertTrue(retry.contains("putBoolean(FilterListsSubscribeAllWorker.INPUT_RETRY_NO_URL, true)"));
    }

    @Test
    public void scopeInputContainsOnlyTokenAndMissingSnapshotFailsClosed() throws IOException {
        String worker = source("hosts/FilterListsSubscribeAllWorker.java");
        String persist = section(worker, "static synchronized Data persistScopeInput(",
                "static synchronized int[] readScopeIds(");
        assertTrue(persist.contains("UUID.randomUUID().toString()"));
        assertTrue(persist.contains("putStringSet(KEY_SCOPE_IDS, ids)"));
        assertTrue(persist.contains(".commit()"));
        assertTrue(persist.contains("putString(INPUT_SCOPE_TOKEN, token)"));
        assertFalse(persist.contains("putIntArray("));
        String read = section(worker, "static synchronized int[] readScopeIds(",
                "public static int[] readStoredIds(");
        assertTrue(read.contains("token.equals(prefs.getString(KEY_SCOPE_TOKEN, null))"));
        assertTrue(read.contains("throw new IllegalStateException"));
        String work = section(worker, "public Result doWork()", "private boolean isCancellationRequested(");
        assertTrue(work.indexOf("readScopeIds(prefs, input)") < work.indexOf("api.getLists()"));
        assertTrue(work.contains("return Result.failure();"));
    }

    @Test
    public void unsupportedChooserIsUncappedButTextSummaryRetainsMoreMarker() throws IOException {
        String fragment = source("discover/DiscoverFilterListsFragment.java");
        String choices = section(fragment, "private void showUnsupportedChoicesDialog(",
                "private List<FilterListsDirectoryApi.ListSummary> findSummariesByIds(");
        assertFalse(choices.contains("MAX_LAST_RUN_DIALOG_ROWS"));
        assertTrue(choices.contains("new CharSequence[summaries.size()]"));
        assertTrue(choices.contains("showUnsupportedReviewDialog(summaries.get(which))"));
        String summary = section(fragment, "static String formatLastRunReviewMessage(",
                "private static String formatOutcomeLine(");
        assertTrue(summary.contains("MAX_LAST_RUN_DIALOG_ROWS"));
        assertTrue(summary.contains("append(\" more\")"));
    }

    @Test
    public void cancellationUsesSameRecoveryPersistenceAndFutureFailuresKeepId() throws IOException {
        String worker = source("hosts/FilterListsSubscribeAllWorker.java");
        String cancellation = section(worker, "static Data finalizeCancelledRun(",
                "private static int getForegroundServiceType(");
        assertTrue(cancellation.contains("recorder.flush()"));
        assertTrue(cancellation.contains("persistLastRunOutcomes(prefs, recorder, true)"));
        assertTrue(worker.contains("resolving.remove(future)"));
        assertTrue(worker.contains("recorder.accept(summary.id, summary.name, summary.syntaxIds,"));
        assertTrue(worker.contains("recorder.retainPendingRetryIds(scopeIds)"));
        assertFalse(section(worker, "public Result doWork()", "private boolean isCancellationRequested(")
                .contains("recorder.recordSkippedNoUrl()"));
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue("Missing section: " + start, from >= 0 && to > from);
        return source.substring(from, to);
    }

    private static String source(String path) throws IOException {
        Path root = Paths.get("").toAbsolutePath();
        if (Files.isDirectory(root.resolve("app"))) root = root.resolve("app");
        return new String(Files.readAllBytes(root.resolve("src/main/java/org/adaway/ui/" + path)),
                StandardCharsets.UTF_8);
    }
}
