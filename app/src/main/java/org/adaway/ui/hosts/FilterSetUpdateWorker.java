package org.adaway.ui.hosts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import timber.log.Timber;

/**
 * Periodic worker that updates sources for scheduled filter sets.
 */
public class FilterSetUpdateWorker extends Worker {
    private static final String PREFS_SOURCE_SCHEDULES = "source_schedules";
    private static final String KEY_SCHEDULE_PREFIX = "schedule_url_";
    private static final String KEY_LAST_RUN_PREFIX = "last_run_url_";
    private static final String KEY_HOUR_PREFIX = "hour_url_";
    private static final String KEY_MINUTE_PREFIX = "minute_url_";
    private static final String KEY_WEEKDAY_PREFIX = "weekday_url_";

    private static final int SCHEDULE_OFF = 0;
    private static final int SCHEDULE_DAILY = 1;
    private static final int SCHEDULE_WEEKLY = 2;

    public static final String PROGRESS_DONE = "done";
    public static final String PROGRESS_TOTAL = "total";
    public static final String PROGRESS_CURRENT = "current";
    public FilterSetUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        long now = System.currentTimeMillis();

        Set<String> setNames = FilterSetStore.getSetNames(context);
        AdAwayApplication app = (AdAwayApplication) context;
        SourceModel sourceModel = app.getSourceModel();
        AdBlockModel adBlockModel = app.getAdBlockModel();
        HostsSourceDao dao = AppDatabase.getInstance(context).hostsSourceDao();

        // Determine if ANY schedule is due. If due: update ALL enabled sources (and apply),
        // otherwise do nothing. This matches "manual update unless scheduled" behavior.
        boolean anyScheduleDue = false;
        Set<String> dueSetNames = new java.util.HashSet<>();
        if (setNames != null) {
            for (String setName : setNames) {
                if (FilterSetStore.isDue(context, setName, now)) {
                    anyScheduleDue = true;
                    dueSetNames.add(setName);
                }
            }
        }

        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_SOURCE_SCHEDULES, Context.MODE_PRIVATE);
        Set<String> dueSourceUrls = new java.util.HashSet<>();
        for (HostsSource src : dao.getAll()) {
            if (src.getId() == HostsSource.USER_SOURCE_ID) continue;
            if (!src.isEnabled()) continue;
            String url = src.getUrl();
            int schedule = prefs.getInt(KEY_SCHEDULE_PREFIX + url, SCHEDULE_OFF);
            if (schedule == SCHEDULE_OFF) continue;
            long last = prefs.getLong(KEY_LAST_RUN_PREFIX + url, 0L);
            if (last <= 0L) {
                anyScheduleDue = true;
                dueSourceUrls.add(url);
                continue;
            }
            int hour = prefs.getInt(KEY_HOUR_PREFIX + url, 3);
            int minute = prefs.getInt(KEY_MINUTE_PREFIX + url, 0);
            int weekdayIso = prefs.getInt(KEY_WEEKDAY_PREFIX + url, 1);
            boolean due = FilterSetStore.isDueByWallClock(now, last, schedule, weekdayIso, hour, minute);
            if (due) {
                anyScheduleDue = true;
                dueSourceUrls.add(url);
            }
        }

        // Global schedule (default daily, configurable)
        FilterSetStore.ensureGlobalDefaults(context);
        if (FilterSetStore.isGlobalScheduleEnabled(context)) {
            int schedule = FilterSetStore.getGlobalSchedule(context);
            long last = FilterSetStore.getGlobalLastRun(context);
            int hour = FilterSetStore.getGlobalHour(context);
            int minute = FilterSetStore.getGlobalMinute(context);
            int weekdayIso = FilterSetStore.getGlobalWeekdayIso(context);
            boolean due = last <= 0L || FilterSetStore.isDueByWallClock(now, last, schedule, weekdayIso, hour, minute);
            if (due) {
                anyScheduleDue = true;
            }
        }

        if (!anyScheduleDue) {
            return Result.success();
        }

        // Worker-level progress (the per-source percent is handled by SourceModel.getProgress()).
        setProgressAsync(new Data.Builder()
                .putInt(PROGRESS_DONE, 0)
                .putInt(PROGRESS_TOTAL, 1)
                .putString(PROGRESS_CURRENT, "Updating all enabled sources")
                .build());

        Timber.i("Scheduled update due (sets=%d, sources=%d): updating ALL enabled sources", dueSetNames.size(), dueSourceUrls.size());
        try {
            // Set scheduler task name for UI display
            String taskName = dueSetNames.isEmpty() ? "Scheduled Update" : String.join(", ", dueSetNames);
            sourceModel.setSchedulerTaskName(taskName);
            // Update all enabled sources with adaptive batching, then apply config.
            sourceModel.checkAndRetrieveHostsSources();
            adBlockModel.apply();
        } catch (HostErrorException e) {
            Timber.w(e, "Failed scheduled update-all");
            return Result.retry();
        }

        // Mark schedules as run (even if no actual online update occurred, we ran as scheduled).
        for (String setName : dueSetNames) {
            FilterSetStore.setLastRun(context, setName, now);
        }
        if (!dueSourceUrls.isEmpty()) {
            android.content.SharedPreferences.Editor editor = prefs.edit();
            for (String url : dueSourceUrls) {
                editor.putLong(KEY_LAST_RUN_PREFIX + url, now);
            }
            editor.apply();
        }

        if (FilterSetStore.isGlobalScheduleEnabled(context)) {
            FilterSetStore.setGlobalLastRun(context, now);
        }

        setProgressAsync(new Data.Builder()
                .putInt(PROGRESS_DONE, 1)
                .putInt(PROGRESS_TOTAL, 1)
                .putString(PROGRESS_CURRENT, "Done")
                .build());

        return Result.success();
    }
}


