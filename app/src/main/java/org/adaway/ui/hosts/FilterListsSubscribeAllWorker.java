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

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.NotificationHelper;
import org.adaway.model.source.FilterListsDirectoryApi;
import org.adaway.model.source.SourceModel;
import org.adaway.ui.home.HomeActivity;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Background "Subscribe to all" job for FilterLists.
 * Shows a progress notification and posts a completion notification when finished.
 */
public class FilterListsSubscribeAllWorker extends Worker {

    public static final String UNIQUE_WORK_NAME = "filterlists_subscribe_all";

    private static final int NOTIFICATION_ID = 42;

    private static final String PREFS = "filterlists_cache";
    private static final String KEY_URL_PREFIX = "listUrl_";

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

        SourceModel sourceModel = new SourceModel(context);
        FilterListsDirectoryApi api = new FilterListsDirectoryApi(sourceModel.getHttpClientForUi());
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        try {
            List<FilterListsDirectoryApi.ListSummary> lists = api.getLists();
            int total = lists.size();
            int done = 0;
            int subscribed = 0;
            int skippedNoUrl = 0;
            int already = 0;

            // Start as foreground work so Android won't kill it.
            setForegroundAsync(new ForegroundInfo(
                    NOTIFICATION_ID,
                    buildProgressNotification(context, 0, total, null)
            ));

            // Publish initial progress so UI shows immediately (even if ENQUEUED -> RUNNING delay).
            setProgressAsync(new Data.Builder()
                    .putInt(PROGRESS_DONE, 0)
                    .putInt(PROGRESS_TOTAL, total)
                    .putInt(PROGRESS_CURRENT_ID, -1)
                    .putString(PROGRESS_CURRENT_NAME, null)
                    .build());

            for (FilterListsDirectoryApi.ListSummary s : lists) {
                if (isStopped()) {
                    notificationManager.cancel(NOTIFICATION_ID);
                    return Result.failure();
                }

                String url = null;
                try {
                    FilterListsDirectoryApi.ListDetails details = api.getListDetails(s.id);
                    url = details.pickBestDownloadUrl();
                } catch (IOException ignored) {
                    // treat as no url this run
                }
                if (url != null) {
                    // Persist mapping so UI can mark subscribed items without extra network.
                    prefs.edit().putString(KEY_URL_PREFIX + s.id, url).apply();
                }

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
                    hostsSourceDao.insert(src);
                    existingUrls.add(url);
                    subscribed++;
                }

                done++;

                // Publish progress for in-app UI (Home screen + FilterLists screen).
                // Update less aggressively to reduce overhead: every 5 items and on last item.
                if (done == 1 || done % 5 == 0 || done == total) {
                    setProgressAsync(new Data.Builder()
                            .putInt(PROGRESS_DONE, done)
                            .putInt(PROGRESS_TOTAL, total)
                            .putInt(PROGRESS_CURRENT_ID, s.id)
                            .putString(PROGRESS_CURRENT_NAME, s.name)
                            .build());
                }

                // Update notification (every item, but cheap).
                notificationManager.notify(
                        NOTIFICATION_ID,
                        buildProgressNotification(context, done, total, s.name)
                );
            }

            // Final notification
            notificationManager.notify(
                    NOTIFICATION_ID,
                    buildDoneNotification(context, subscribed, already, skippedNoUrl)
            );

            return Result.success();
        } catch (IOException e) {
            notificationManager.notify(
                    NOTIFICATION_ID,
                    buildErrorNotification(context)
            );
            return Result.retry();
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
        String text = done + "/" + total + " (" + percent + "%)";
        if (currentName != null && !currentName.isEmpty()) {
            text = text + " • " + currentName;
        }
        return baseBuilder(context)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setProgress(total, done, total <= 0)
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
