package org.adaway.ui.hosts;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.getActivity;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.NotificationHelper;
import org.adaway.model.source.FilterListCompatibility;
import org.adaway.model.source.FilterListsSourceMetadata;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.model.source.FilterListsDirectoryApi;
import org.adaway.ui.home.HomeActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Background "Subscribe to all" job for FilterLists.
 * Shows a progress notification and posts a completion notification when finished.
 */
public class FilterListsSubscribeAllWorker extends Worker {

    public static final String UNIQUE_WORK_NAME = "filterlists_subscribe_all";

    private static final int NOTIFICATION_ID = 42;

    private static final String PREFS = "filterlists_cache";
    private static final String KEY_URL_PREFIX = "listUrl_";
    private static final String KEY_CANCEL_REQUESTED = "subscribeAllCancelRequested";
    private static final int MAX_PARALLEL_DETAILS = 8;
    private static final int BATCH_DB = 200;
    private static final int BATCH_PREFS = 200;
    private static final int PROGRESS_EVERY = 25;
    private static final int MAX_OUTCOME_LEDGER_ENTRIES = 500;
    private static final int MAX_REVIEW_PREVIEW_ENTRIES = 3;

    public static final String PROGRESS_DONE = "done";
    public static final String PROGRESS_TOTAL = "total";
    public static final String PROGRESS_CURRENT_ID = "currentId";
    public static final String PROGRESS_CURRENT_NAME = "currentName";
    public static final String OUTPUT_SUBSCRIBED = "subscribed";
    public static final String OUTPUT_ALREADY = "already";
    public static final String OUTPUT_SKIPPED_NO_URL = "skippedNoUrl";
    public static final String OUTPUT_SKIPPED_UNSUPPORTED = "skippedUnsupported";
    public static final String OUTPUT_CANCELLED = "cancelled";
    public static final String OUTPUT_REVIEW_COUNT = "reviewCount";
    public static final String OUTPUT_REVIEW_PREVIEW = "reviewPreview";
    public static final String INPUT_QUERY = "query";
    public static final String INPUT_TAG_ID = "tagId";
    public static final String INPUT_LANGUAGE_ID = "languageId";
    public static final String INPUT_COMPATIBLE_ONLY = "compatibleOnly";
    public static final String INPUT_LIST_IDS = "listIds";
    public static final String KEY_LAST_RUN_OUTCOMES = "lastRunOutcomes";
    public static final String KEY_LAST_RUN_OUTCOME_COUNT = "lastRunOutcomeCount";
    public static final String KEY_LAST_RUN_REVIEW_COUNT = "lastRunReviewCount";
    public static final String KEY_LAST_RUN_REVIEW_PREVIEW = "lastRunReviewPreview";
    public static final String KEY_LAST_RUN_CANCELLED = "lastRunCancelled";
    public static final String KEY_LAST_RUN_FINISHED_AT = "lastRunFinishedAt";

    private static final Dependencies REAL_DEPENDENCIES = new RealDependencies();
    private static volatile Dependencies sDependencies = REAL_DEPENDENCIES;

    // Track last emitted progress to ensure monotonic delivery.
    // WorkManager's setProgressAsync is async and doesn't guarantee order.
    private int lastEmittedDone = -1;

    public FilterListsSubscribeAllWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static boolean prepareForNewRun(@NonNull Context context) {
        return sDependencies.getCachePreferences(context.getApplicationContext())
                .edit()
                .putBoolean(KEY_CANCEL_REQUESTED, false)
                .commit();
    }

    public static boolean requestCancel(@NonNull Context context) {
        return sDependencies.getCachePreferences(context.getApplicationContext())
                .edit()
                .putBoolean(KEY_CANCEL_REQUESTED, true)
                .commit();
    }

    /**
     * Emit progress only if done > lastEmittedDone (monotonic guarantee).
     * This prevents out-of-order async deliveries from confusing the UI.
     */
    private void emitProgress(int done, int total, int currentId, String currentName) {
        if (done > lastEmittedDone) {
            lastEmittedDone = done;
            setProgressAsync(new Data.Builder()
                    .putInt(PROGRESS_DONE, done)
                    .putInt(PROGRESS_TOTAL, total)
                    .putInt(PROGRESS_CURRENT_ID, currentId)
                    .putString(PROGRESS_CURRENT_NAME, currentName)
                    .build());
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return Result.failure();
        }

        Dependencies dependencies = sDependencies;
        SubscribeAllRecorder recorder = dependencies.createRecorder(context);
        DirectoryClient api = dependencies.createDirectoryClient(context);
        SharedPreferences prefs = dependencies.getCachePreferences(context);

        try {
            // Start as foreground work immediately so Android won't kill it, and so UI can show "Preparing…" instantly.
            setForegroundAsync(new ForegroundInfo(
                    NOTIFICATION_ID,
                    buildProgressNotification(context, 0, 0, context.getString(R.string.filterlists_import)),
                    getForegroundServiceType()
            ));
            setProgressAsync(new Data.Builder()
                    .putInt(PROGRESS_DONE, 0)
                    .putInt(PROGRESS_TOTAL, 0)
                    .putInt(PROGRESS_CURRENT_ID, -1)
                    .putString(PROGRESS_CURRENT_NAME, "Preparing…")
                    .build());

            Data input = getInputData();
            List<FilterListsDirectoryApi.ListSummary> lists = filterListsForScope(api.getLists(),
                    input.getString(INPUT_QUERY),
                    input.getInt(INPUT_TAG_ID, 0),
                    input.getInt(INPUT_LANGUAGE_ID, 0),
                    input.getBoolean(INPUT_COMPATIBLE_ONLY, false),
                    input.getIntArray(INPUT_LIST_IDS));
            int total = lists.size();
            int done = 0;

            // Publish initial progress now that we know total.
            // Reset monotonic guard for new run.
            lastEmittedDone = -1;
            emitProgress(0, total, -1, null);
            notificationManager.notify(
                    NOTIFICATION_ID,
                    buildProgressNotification(context, 0, total, null)
            );

            // Speedups:
            // - avoid /lists/{id} when we already have cached URL mapping (or cached "no url")
            // - fetch remaining /lists/{id} in parallel with a cap
            // - batch SharedPreferences writes and DB inserts
            SharedPreferences.Editor prefsEditor = prefs.edit();
            int prefsEdits = 0;

            ExecutorService pool = Executors.newFixedThreadPool(MAX_PARALLEL_DETAILS);
            CompletionService<Resolved> completion = new ExecutorCompletionService<>(pool);
            int pending = 0;

            // First, enqueue network tasks only for items without cached mapping.
            for (FilterListsDirectoryApi.ListSummary s : lists) {
                if (!FilterListCompatibility.isBulkSafe(s.syntaxIds)) {
                    continue;
                }
                String cached = prefs.getString(KEY_URL_PREFIX + s.id, null);
                if (cached != null) {
                    // We'll process cached items immediately below to keep progress moving,
                    // but we still want parallel network for uncached ones.
                    continue;
                }
                pending++;
                completion.submit(new ResolveCallable(api, s));
            }

            // Process cached items immediately (no network).
            for (FilterListsDirectoryApi.ListSummary s : lists) {
                if (isCancellationRequested(prefs)) {
                    return finishCancelled(pool, notificationManager, recorder);
                }
                String cached = prefs.getString(KEY_URL_PREFIX + s.id, null);
                if (cached == null && FilterListCompatibility.isBulkSafe(s.syntaxIds)) {
                    continue; // handled by parallel completion loop
                }

                String url = cached == null || cached.isEmpty() ? null : cached;
                if (isCancellationRequested(prefs)) {
                    return finishCancelled(pool, notificationManager, recorder);
                }
                recorder.accept(s.id, s.name, s.syntaxIds, s.tagIds, s.languageIds, url);

                done++;
                if (shouldEmitProgressUpdate(done, total)) {
                    emitProgress(done, total, s.id, s.name);
                    notificationManager.notify(
                            NOTIFICATION_ID,
                            buildProgressNotification(context, done, total, s.name)
                    );
                }
            }

            // Now process uncached items as their network results complete (out of order, faster).
            while (pending > 0) {
                if (isCancellationRequested(prefs)) {
                    return finishCancelled(pool, notificationManager, recorder);
                }
                Future<Resolved> future;
                try {
                    future = completion.poll(250, TimeUnit.MILLISECONDS);
                    if (future == null) {
                        continue;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return finishCancelled(pool, notificationManager, recorder);
                }
                Resolved r;
                try {
                    r = future.get();
                } catch (Exception e) {
                    // Treat as one failed resolution.
                    pending--;
                    done++;
                    recorder.recordSkippedNoUrl();
                    if (shouldEmitProgressUpdate(done, total)) {
                        emitProgress(done, total, -1, "Resolving…");
                        notificationManager.notify(
                                NOTIFICATION_ID,
                                buildProgressNotification(context, done, total, "Resolving…")
                        );
                    }
                    continue;
                }
                pending--;
                if (isCancellationRequested(prefs)) {
                    return finishCancelled(pool, notificationManager, recorder);
                }

                // Cache mapping (including negative cache as empty string)
                prefsEditor.putString(KEY_URL_PREFIX + r.id, r.url == null ? "" : r.url);
                prefsEdits++;
                if (prefsEdits >= BATCH_PREFS) {
                    prefsEditor.apply();
                    prefsEditor = prefs.edit();
                    prefsEdits = 0;
                }

                recorder.accept(r.id, r.name, r.syntaxIds, r.tagIds, r.languageIds, r.url);

                done++;
                if (shouldEmitProgressUpdate(done, total)) {
                    emitProgress(done, total, r.id, r.name);
                    notificationManager.notify(
                            NOTIFICATION_ID,
                            buildProgressNotification(context, done, total, r.name)
                    );
                }
            }

            pool.shutdownNow();
            recorder.flush();
            if (prefsEdits > 0) {
                prefsEditor.apply();
            }
            persistLastRunOutcomes(prefs, recorder, false);

            // Final notification
            notificationManager.notify(
                    NOTIFICATION_ID,
                    buildDoneNotification(context, recorder.getSubscribed(), recorder.getAlready(),
                            recorder.getSkippedNoUrl(), recorder.getSkippedUnsupported())
            );

            // Kick off an update run only when newly added sources need download/convert work.
            // This runs as a separate WorkManager job so Home can show source update % once needed.
            if (recorder.getSubscribed() > 0) {
                dependencies.enqueueUpdateNow(context);
            }

            return Result.success(recorder.finish(false));
        } catch (IOException e) {
            notificationManager.notify(
                    NOTIFICATION_ID,
                    buildErrorNotification(context)
            );
            return Result.retry();
        }
    }

    private boolean isCancellationRequested(@NonNull SharedPreferences prefs) {
        return isStopped() || prefs.getBoolean(KEY_CANCEL_REQUESTED, false);
    }

    private Result finishCancelled(ExecutorService pool, NotificationManager notificationManager,
            SubscribeAllRecorder recorder) {
        Data output = finalizeCancelledRun(pool,
                sDependencies.getCachePreferences(getApplicationContext()),
                recorder,
                () -> notificationManager.cancel(NOTIFICATION_ID));
        return Result.failure(output);
    }

    static Data finalizeCancelledRun(ExecutorService pool, SharedPreferences prefs,
            SubscribeAllRecorder recorder, Runnable cancelNotification) {
        pool.shutdownNow();
        recorder.flush();
        persistLastRunOutcomes(prefs, recorder, true);
        cancelNotification.run();
        return recorder.finish(true);
    }

    private static int getForegroundServiceType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
        }
        return 0;
    }

    private static Data buildOutputData(int subscribed, int already, int skippedNoUrl,
            int skippedIncompatible, boolean cancelled, int reviewCount, String reviewPreview) {
        return new Data.Builder()
                .putInt(OUTPUT_SUBSCRIBED, subscribed)
                .putInt(OUTPUT_ALREADY, already)
                .putInt(OUTPUT_SKIPPED_NO_URL, skippedNoUrl)
                .putInt(OUTPUT_SKIPPED_UNSUPPORTED, skippedIncompatible)
                .putBoolean(OUTPUT_CANCELLED, cancelled)
                .putInt(OUTPUT_REVIEW_COUNT, reviewCount)
                .putString(OUTPUT_REVIEW_PREVIEW, reviewPreview)
                .build();
    }

    private static void persistLastRunOutcomes(SharedPreferences prefs, SubscribeAllRecorder recorder,
            boolean cancelled) {
        prefs.edit()
                .putString(KEY_LAST_RUN_OUTCOMES, recorder.getOutcomeLedger())
                .putInt(KEY_LAST_RUN_OUTCOME_COUNT, recorder.getOutcomeCount())
                .putInt(KEY_LAST_RUN_REVIEW_COUNT, recorder.getReviewCount())
                .putString(KEY_LAST_RUN_REVIEW_PREVIEW, recorder.getReviewPreview())
                .putBoolean(KEY_LAST_RUN_CANCELLED, cancelled)
                .putLong(KEY_LAST_RUN_FINISHED_AT, System.currentTimeMillis())
                .commit();
    }

    static boolean shouldEmitProgressUpdate(int done, int total) {
        return done == 1 || done % PROGRESS_EVERY == 0 || done == total;
    }

    @NonNull
    public static Data buildScopeInput(
            @Nullable String query,
            int tagId,
            int languageId,
            boolean compatibleOnly,
            @Nullable int[] listIds) {
        Data.Builder builder = new Data.Builder()
                .putString(INPUT_QUERY, normalizeQuery(query))
                .putInt(INPUT_TAG_ID, Math.max(0, tagId))
                .putInt(INPUT_LANGUAGE_ID, Math.max(0, languageId))
                .putBoolean(INPUT_COMPATIBLE_ONLY, compatibleOnly);
        if (listIds != null) {
            builder.putIntArray(INPUT_LIST_IDS, listIds);
        }
        return builder.build();
    }

    @NonNull
    public static Data buildScopeInput(
            @Nullable String query,
            int tagId,
            int languageId,
            boolean compatibleOnly) {
        return buildScopeInput(query, tagId, languageId, compatibleOnly, null);
    }

    @NonNull
    static List<FilterListsDirectoryApi.ListSummary> filterListsForScope(
            @NonNull List<FilterListsDirectoryApi.ListSummary> lists,
            @Nullable String query,
            int tagId,
            int languageId,
            boolean compatibleOnly) {
        return filterListsForScope(lists, query, tagId, languageId, compatibleOnly, null);
    }

    @NonNull
    static List<FilterListsDirectoryApi.ListSummary> filterListsForScope(
            @NonNull List<FilterListsDirectoryApi.ListSummary> lists,
            @Nullable String query,
            int tagId,
            int languageId,
            boolean compatibleOnly,
            @Nullable int[] listIds) {
        String normalizedQuery = normalizeQuery(query);
        List<FilterListsDirectoryApi.ListSummary> scoped = new ArrayList<>(lists.size());
        for (FilterListsDirectoryApi.ListSummary summary : lists) {
            if (!matchesScope(summary, normalizedQuery, tagId, languageId, compatibleOnly,
                    listIds)) {
                continue;
            }
            scoped.add(summary);
        }
        return scoped;
    }

    private static boolean matchesScope(
            @NonNull FilterListsDirectoryApi.ListSummary summary,
            @NonNull String query,
            int tagId,
            int languageId,
            boolean compatibleOnly,
            @Nullable int[] listIds) {
        if (listIds != null) {
            return hasId(listIds, summary.id)
                    && (!compatibleOnly || FilterListCompatibility.isBulkSafe(summary.syntaxIds));
        }
        if (!query.isEmpty()) {
            String name = summary.name != null ? summary.name.toLowerCase(java.util.Locale.ROOT) : "";
            String description = summary.description != null
                    ? summary.description.toLowerCase(java.util.Locale.ROOT)
                    : "";
            if (!name.contains(query) && !description.contains(query)) {
                return false;
            }
        }
        if (tagId != 0 && !hasId(summary.tagIds, tagId)) {
            return false;
        }
        if (languageId != 0 && !hasId(summary.languageIds, languageId)) {
            return false;
        }
        return !compatibleOnly || FilterListCompatibility.isBulkSafe(summary.syntaxIds);
    }

    private static boolean hasId(@Nullable int[] ids, int want) {
        if (ids == null) {
            return false;
        }
        for (int id : ids) {
            if (id == want) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String normalizeQuery(@Nullable String query) {
        return query != null ? query.toLowerCase(java.util.Locale.ROOT).trim() : "";
    }

    interface DirectoryClient {
        List<FilterListsDirectoryApi.ListSummary> getLists() throws IOException;

        FilterListsDirectoryApi.ListDetails getListDetails(int id) throws IOException;
    }

    interface Dependencies {
        SubscribeAllRecorder createRecorder(Context context);

        DirectoryClient createDirectoryClient(Context context);

        SharedPreferences getCachePreferences(Context context);

        void enqueueUpdateNow(Context context);
    }

    static void setDependenciesForTest(Dependencies dependencies) {
        sDependencies = dependencies != null ? dependencies : REAL_DEPENDENCIES;
    }

    static void resetDependenciesForTest() {
        sDependencies = REAL_DEPENDENCIES;
    }

    enum CandidateOutcome {
        SUBSCRIBED,
        ALREADY,
        SKIPPED_NO_URL,
        SKIPPED_UNSUPPORTED
    }

    static CandidateOutcome applyCandidate(Set<String> existingUrls,
            List<HostsSource> pendingInsert, String name, int[] syntaxIds, String url) {
        return applyCandidate(existingUrls, pendingInsert, 0, name, syntaxIds, url);
    }

    static CandidateOutcome applyCandidate(Set<String> existingUrls,
            List<HostsSource> pendingInsert, int filterListId, String name, int[] syntaxIds,
            String url) {
        return applyCandidate(existingUrls, pendingInsert, filterListId, name, syntaxIds,
                null, null, url);
    }

    static CandidateOutcome applyCandidate(Set<String> existingUrls,
            List<HostsSource> pendingInsert, int filterListId, String name, int[] syntaxIds,
            int[] tagIds, int[] languageIds, String url) {
        if (!FilterListCompatibility.isBulkSafe(syntaxIds)) {
            return CandidateOutcome.SKIPPED_UNSUPPORTED;
        }
        if (!FilterListCompatibility.isUsableDownloadUrl(url)) {
            return CandidateOutcome.SKIPPED_NO_URL;
        }

        String cleanUrl = url.trim();
        if (existingUrls.contains(cleanUrl)) {
            return CandidateOutcome.ALREADY;
        }

        HostsSource source = new HostsSource();
        source.setLabel(name != null ? name : cleanUrl);
        source.setUrl(cleanUrl);
        source.setEnabled(true);
        source.setAllowEnabled(false);
        source.setRedirectEnabled(false);
        FilterListsSourceMetadata.apply(source, filterListId, name, syntaxIds, tagIds,
                languageIds, cleanUrl);
        pendingInsert.add(source);
        existingUrls.add(cleanUrl);
        return CandidateOutcome.SUBSCRIBED;
    }

    static final class SubscribeAllRecorder {
        private final HostsSourceDao hostsSourceDao;
        private final Set<String> existingUrls;
        private final List<HostsSource> pendingInsert;
        private final List<String> outcomeLedger;
        private final List<String> reviewPreview;
        private int outcomeCount;
        private int reviewCount;
        private int subscribed;
        private int already;
        private int skippedNoUrl;
        private int skippedUnsupported;

        private SubscribeAllRecorder(HostsSourceDao hostsSourceDao, Set<String> existingUrls) {
            this.hostsSourceDao = hostsSourceDao;
            this.existingUrls = existingUrls;
            this.pendingInsert = new ArrayList<>(BATCH_DB);
            this.outcomeLedger = new ArrayList<>();
            this.reviewPreview = new ArrayList<>(MAX_REVIEW_PREVIEW_ENTRIES);
        }

        static SubscribeAllRecorder create(HostsSourceDao hostsSourceDao) {
            Set<String> existingUrls = new HashSet<>();
            for (HostsSource source : hostsSourceDao.getAll()) {
                existingUrls.add(source.getUrl());
            }
            return new SubscribeAllRecorder(hostsSourceDao, existingUrls);
        }

        CandidateOutcome accept(String name, int[] syntaxIds, String url) {
            return accept(0, name, syntaxIds, url);
        }

        CandidateOutcome accept(int filterListId, String name, int[] syntaxIds, String url) {
            return accept(filterListId, name, syntaxIds, null, null, url);
        }

        CandidateOutcome accept(int filterListId, String name, int[] syntaxIds,
                int[] tagIds, int[] languageIds, String url) {
            CandidateOutcome outcome = applyCandidate(existingUrls, pendingInsert, filterListId,
                    name, syntaxIds, tagIds, languageIds, url);
            record(outcome, filterListId, name, url);
            if (pendingInsert.size() >= BATCH_DB) {
                flush();
            }
            return outcome;
        }

        void recordSkippedNoUrl() {
            skippedNoUrl++;
            appendOutcome(CandidateOutcome.SKIPPED_NO_URL, 0, null, null);
        }

        void flush() {
            if (!pendingInsert.isEmpty()) {
                hostsSourceDao.insertAll(pendingInsert);
                pendingInsert.clear();
            }
        }

        Data finish(boolean cancelled) {
            return buildOutputData(subscribed, already, skippedNoUrl, skippedUnsupported,
                    cancelled, reviewCount, getReviewPreview());
        }

        int getSubscribed() {
            return subscribed;
        }

        int getAlready() {
            return already;
        }

        int getSkippedNoUrl() {
            return skippedNoUrl;
        }

        int getSkippedUnsupported() {
            return skippedUnsupported;
        }

        int getOutcomeCount() {
            return outcomeCount;
        }

        int getReviewCount() {
            return reviewCount;
        }

        String getOutcomeLedger() {
            return joinLines(outcomeLedger);
        }

        String getReviewPreview() {
            return joinPreview(reviewPreview);
        }

        private void record(CandidateOutcome outcome, int filterListId, String name, String url) {
            switch (outcome) {
                case SUBSCRIBED:
                    subscribed++;
                    break;
                case ALREADY:
                    already++;
                    break;
                case SKIPPED_NO_URL:
                    skippedNoUrl++;
                    break;
                case SKIPPED_UNSUPPORTED:
                    skippedUnsupported++;
                    break;
                default:
                    throw new IllegalStateException("Unhandled candidate outcome: " + outcome);
            }
            appendOutcome(outcome, filterListId, name, url);
        }

        private void appendOutcome(CandidateOutcome outcome, int filterListId, String name,
                String url) {
            outcomeCount++;
            if (outcome == CandidateOutcome.SKIPPED_NO_URL
                    || outcome == CandidateOutcome.SKIPPED_UNSUPPORTED) {
                reviewCount++;
                if (reviewPreview.size() < MAX_REVIEW_PREVIEW_ENTRIES) {
                    reviewPreview.add(formatReviewPreview(outcome, filterListId, name));
                }
            }
            if (outcomeLedger.size() < MAX_OUTCOME_LEDGER_ENTRIES) {
                outcomeLedger.add(formatOutcomeLedgerLine(outcome, filterListId, name, url));
            }
        }

        private static String formatReviewPreview(CandidateOutcome outcome, int filterListId,
                String name) {
            String label = describeList(filterListId, name);
            switch (outcome) {
                case SKIPPED_NO_URL:
                    return "No URL: " + label;
                case SKIPPED_UNSUPPORTED:
                    return "Unsupported: " + label;
                default:
                    return outcome.name() + ": " + label;
            }
        }

        private static String formatOutcomeLedgerLine(CandidateOutcome outcome, int filterListId,
                String name, String url) {
            return outcome.name() + "\t" + filterListId + "\t" + sanitizeField(name)
                    + "\t" + sanitizeField(url);
        }

        private static String describeList(int filterListId, String name) {
            String cleaned = sanitizeField(name);
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
            return filterListId > 0 ? "list " + filterListId : "unknown list";
        }

        private static String sanitizeField(String value) {
            if (value == null) {
                return "";
            }
            return value.replace('\t', ' ')
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                    .trim();
        }

        private static String joinLines(List<String> lines) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    builder.append('\n');
                }
                builder.append(lines.get(i));
            }
            return builder.toString();
        }

        private static String joinPreview(List<String> preview) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < preview.size(); i++) {
                if (i > 0) {
                    builder.append("; ");
                }
                builder.append(preview.get(i));
            }
            return builder.toString();
        }
    }

    private static final class RealDependencies implements Dependencies {
        @Override
        public SubscribeAllRecorder createRecorder(Context context) {
            HostsSourceDao hostsSourceDao = AppDatabase.getInstance(context).hostsSourceDao();
            return SubscribeAllRecorder.create(hostsSourceDao);
        }

        @Override
        public DirectoryClient createDirectoryClient(Context context) {
            AdAwayApplication app = (AdAwayApplication) context.getApplicationContext();
            FilterListsDirectoryApi api =
                    new FilterListsDirectoryApi(app.getSourceModel().getHttpClientForUi());
            return new RealDirectoryClient(api);
        }

        @Override
        public SharedPreferences getCachePreferences(Context context) {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        @Override
        public void enqueueUpdateNow(Context context) {
            SourceUpdateService.enqueueUpdateNow(context);
        }
    }

    private static final class RealDirectoryClient implements DirectoryClient {
        private final FilterListsDirectoryApi api;

        RealDirectoryClient(FilterListsDirectoryApi api) {
            this.api = api;
        }

        @Override
        public List<FilterListsDirectoryApi.ListSummary> getLists() throws IOException {
            return api.getLists();
        }

        @Override
        public FilterListsDirectoryApi.ListDetails getListDetails(int id) throws IOException {
            return api.getListDetails(id);
        }
    }

    private static final class Resolved {
        final int id;
        final String name;
        final int[] syntaxIds;
        final int[] tagIds;
        final int[] languageIds;
        final String url; // nullable

        Resolved(int id, String name, int[] syntaxIds, int[] tagIds, int[] languageIds,
                String url) {
            this.id = id;
            this.name = name;
            this.syntaxIds = syntaxIds;
            this.tagIds = tagIds;
            this.languageIds = languageIds;
            this.url = url;
        }
    }

    private static final class ResolveCallable implements Callable<Resolved> {
        private final DirectoryClient api;
        private final FilterListsDirectoryApi.ListSummary summary;

        ResolveCallable(DirectoryClient api, FilterListsDirectoryApi.ListSummary summary) {
            this.api = api;
            this.summary = summary;
        }

        @Override
        public Resolved call() {
            String url = null;
            try {
                FilterListsDirectoryApi.ListDetails details = api.getListDetails(summary.id);
                url = details.pickBestDownloadUrl();
            } catch (IOException ignored) {
                url = null;
            }
            return new Resolved(summary.id, summary.name, summary.syntaxIds, summary.tagIds,
                    summary.languageIds, url);
        }
    }

    private static NotificationCompat.Builder baseBuilder(Context context) {
        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = getActivity(context, 0, intent, FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(context, NotificationHelper.FILTERLISTS_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.logo)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);
    }

    private static android.app.Notification buildProgressNotification(Context context, int done, int total, String currentName) {
        int percent = total > 0 ? (int) Math.floor((done * 100.0) / total) : 0;
        String title = context.getString(R.string.notification_filterlists_subscribe_all_title);
        String text = (total > 0) ? (done + "/" + total + " (" + percent + "%)") : "Preparing…";
        if (currentName != null && !currentName.isEmpty()) {
            text = text + " • " + currentName;
        }
        return baseBuilder(context)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setProgress(Math.max(0, total), Math.max(0, done), total <= 0)
                .build();
    }

    private static android.app.Notification buildDoneNotification(Context context, int subscribed,
            int already, int skippedNoUrl, int skippedIncompatible) {
        String title = context.getString(R.string.notification_filterlists_subscribe_all_done_title);
        String summaryText = "Added " + subscribed + " | Already " + already + " | No URL "
                + skippedNoUrl + " | Unsupported " + skippedIncompatible;
        String text = "Added " + subscribed + " • Already " + already + " • Skipped " + skippedNoUrl;
        return baseBuilder(context)
                .setContentTitle(title)
                .setContentText(summaryText)
                .setOngoing(false)
                .setAutoCancel(true)
                .build();
    }

    private static android.app.Notification buildErrorNotification(Context context) {
        String title = context.getString(R.string.notification_filterlists_subscribe_all_title);
        String text = "Network error. Will retry.";
        return baseBuilder(context)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(false)
                .setAutoCancel(true)
                .build();
    }
}

