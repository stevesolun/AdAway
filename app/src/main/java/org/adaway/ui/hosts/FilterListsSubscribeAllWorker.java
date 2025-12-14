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

import androidx.annotation.NonNull;
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

/**
 * Background "Subscribe to all" job for FilterLists.
 * Shows a progress notification and posts a completion notification when finished.
 */
public class FilterListsSubscribeAllWorker extends Worker {

    public static final String UNIQUE_WORK_NAME = "filterlists_subscribe_all";

    private static final int NOTIFICATION_ID = 42;

    private static final String PREFS = "filterlists_cache";
    private static final String KEY_URL_PREFIX = "listUrl_";
    private static final int MAX_PARALLEL_DETAILS = 8;
    private static final int BATCH_DB = 200;
    private static final int BATCH_PREFS = 200;
    private static final int PROGRESS_EVERY = 25;

    public static final String PROGRESS_DONE = "done";
    public static final String PROGRESS_TOTAL = "total";
    public static final String PROGRESS_CURRENT_ID = "currentId";
    public static final String PROGRESS_CURRENT_NAME = "currentName";

    public FilterListsSubscribeAllWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return Result.failure();
        }

        HostsSourceDao hostsSourceDao = AppDatabase.getInstance(context).hostsSourceDao();

        // Snapshot existing URLs once.
        Set<String> existingUrls = new HashSet<>();
        for (HostsSource s : hostsSourceDao.getAll()) {
            existingUrls.add(s.getUrl());
        }

        AdAwayApplication app = (AdAwayApplication) context.getApplicationContext();
        FilterListsDirectoryApi api = new FilterListsDirectoryApi(app.getSourceModel().getHttpClientForUi());
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        try {
            // Start as foreground work immediately so Android won't kill it, and so UI can show "Preparing…" instantly.
            setForegroundAsync(new ForegroundInfo(
                    NOTIFICATION_ID,
                    buildProgressNotification(context, 0, 0, context.getString(R.string.filterlists_import))
            ));
            setProgressAsync(new Data.Builder()
                    .putInt(PROGRESS_DONE, 0)
                    .putInt(PROGRESS_TOTAL, 0)
                    .putInt(PROGRESS_CURRENT_ID, -1)
                    .putString(PROGRESS_CURRENT_NAME, "Preparing…")
                    .build());

            List<FilterListsDirectoryApi.ListSummary> lists = api.getLists();
            int total = lists.size();
            int done = 0;
            int subscribed = 0;
            int skippedNoUrl = 0;
            int already = 0;

            // Publish initial progress now that we know total.
            setProgressAsync(new Data.Builder()
                    .putInt(PROGRESS_DONE, 0)
                    .putInt(PROGRESS_TOTAL, total)
                    .putInt(PROGRESS_CURRENT_ID, -1)
                    .putString(PROGRESS_CURRENT_NAME, null)
                    .build());
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
            List<HostsSource> pendingInsert = new ArrayList<>(BATCH_DB);

            ExecutorService pool = Executors.newFixedThreadPool(MAX_PARALLEL_DETAILS);
            CompletionService<Resolved> completion = new ExecutorCompletionService<>(pool);
            int pending = 0;

            // First, enqueue network tasks only for items without cached mapping.
            for (FilterListsDirectoryApi.ListSummary s : lists) {
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
                if (isStopped()) {
                    pool.shutdownNow();
                    notificationManager.cancel(NOTIFICATION_ID);
                    return Result.failure();
                }
                String cached = prefs.getString(KEY_URL_PREFIX + s.id, null);
                if (cached == null) continue; // handled by parallel completion loop

                String url = cached.isEmpty() ? null : cached;
                if (url == null) {
                    skippedNoUrl++;
                } else if (existingUrls.contains(url)) {
                    already++;
                } else {
                    HostsSource src = new HostsSource();
                    src.setLabel(s.name != null ? s.name : url);
                    src.setUrl(url);
                    src.setEnabled(true);
                    src.setAllowEnabled(false);
                    src.setRedirectEnabled(false);
                    pendingInsert.add(src);
                    existingUrls.add(url);
                    subscribed++;
                    if (pendingInsert.size() >= BATCH_DB) {
                        hostsSourceDao.insertAll(pendingInsert);
                        pendingInsert.clear();
                    }
                }

                done++;
                if (done == 1 || done % PROGRESS_EVERY == 0 || done == total) {
                    setProgressAsync(new Data.Builder()
                            .putInt(PROGRESS_DONE, done)
                            .putInt(PROGRESS_TOTAL, total)
                            .putInt(PROGRESS_CURRENT_ID, s.id)
                            .putString(PROGRESS_CURRENT_NAME, s.name)
                            .build());
                }
                notificationManager.notify(
                        NOTIFICATION_ID,
                        buildProgressNotification(context, done, total, s.name)
                );
            }

            // Now process uncached items as their network results complete (out of order, faster).
            while (pending > 0) {
                if (isStopped()) {
                    pool.shutdownNow();
                    notificationManager.cancel(NOTIFICATION_ID);
                    return Result.failure();
                }
                Resolved r;
                try {
                    r = completion.take().get();
                } catch (Exception e) {
                    // Treat as one failed resolution.
                    pending--;
                    done++;
                    if (done == 1 || done % 5 == 0 || done == total) {
                        setProgressAsync(new Data.Builder()
                                .putInt(PROGRESS_DONE, done)
                                .putInt(PROGRESS_TOTAL, total)
                                .putInt(PROGRESS_CURRENT_ID, -1)
                                .putString(PROGRESS_CURRENT_NAME, "Resolving…")
                                .build());
                    }
                    notificationManager.notify(
                            NOTIFICATION_ID,
                            buildProgressNotification(context, done, total, "Resolving…")
                    );
                    continue;
                }
                pending--;

                // Cache mapping (including negative cache as empty string)
                prefsEditor.putString(KEY_URL_PREFIX + r.id, r.url == null ? "" : r.url);
                prefsEdits++;
                if (prefsEdits >= BATCH_PREFS) {
                    prefsEditor.apply();
                    prefsEditor = prefs.edit();
                    prefsEdits = 0;
                }

                if (r.url == null) {
                    skippedNoUrl++;
                } else if (existingUrls.contains(r.url)) {
                    already++;
                } else {
                    HostsSource src = new HostsSource();
                    src.setLabel(r.name != null ? r.name : r.url);
                    src.setUrl(r.url);
                    src.setEnabled(true);
                    src.setAllowEnabled(false);
                    src.setRedirectEnabled(false);
                    pendingInsert.add(src);
                    existingUrls.add(r.url);
                    subscribed++;
                    if (pendingInsert.size() >= BATCH_DB) {
                        hostsSourceDao.insertAll(pendingInsert);
                        pendingInsert.clear();
                    }
                }

                done++;
                if (done == 1 || done % PROGRESS_EVERY == 0 || done == total) {
                    setProgressAsync(new Data.Builder()
                            .putInt(PROGRESS_DONE, done)
                            .putInt(PROGRESS_TOTAL, total)
                            .putInt(PROGRESS_CURRENT_ID, r.id)
                            .putString(PROGRESS_CURRENT_NAME, r.name)
                            .build());
                }
                notificationManager.notify(
                        NOTIFICATION_ID,
                        buildProgressNotification(context, done, total, r.name)
                );
            }

            pool.shutdownNow();
            if (!pendingInsert.isEmpty()) {
                hostsSourceDao.insertAll(pendingInsert);
                pendingInsert.clear();
            }
            if (prefsEdits > 0) {
                prefsEditor.apply();
            }

            // Final notification
            notificationManager.notify(
                    NOTIFICATION_ID,
                    buildDoneNotification(context, subscribed, already, skippedNoUrl)
            );

            // Kick off an update run so newly added sources actually download/convert right away.
            // This runs as a separate WorkManager job so Home can show source update % once subscribe-all completes.
            SourceUpdateService.enqueueUpdateNow(context);

            return Result.success();
        } catch (IOException e) {
            notificationManager.notify(
                    NOTIFICATION_ID,
                    buildErrorNotification(context)
            );
            return Result.retry();
        }
    }

    private static final class Resolved {
        final int id;
        final String name;
        final String url; // nullable

        Resolved(int id, String name, String url) {
            this.id = id;
            this.name = name;
            this.url = url;
        }
    }

    private static final class ResolveCallable implements Callable<Resolved> {
        private final FilterListsDirectoryApi api;
        private final FilterListsDirectoryApi.ListSummary summary;

        ResolveCallable(FilterListsDirectoryApi api, FilterListsDirectoryApi.ListSummary summary) {
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
            return new Resolved(summary.id, summary.name, url);
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

    private static android.app.Notification buildDoneNotification(Context context, int subscribed, int already, int skippedNoUrl) {
        String title = context.getString(R.string.notification_filterlists_subscribe_all_done_title);
        String text = "Added " + subscribed + " • Already " + already + " • Skipped " + skippedNoUrl;
        return baseBuilder(context)
                .setContentTitle(title)
                .setContentText(text)
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
