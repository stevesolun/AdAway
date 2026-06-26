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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

        // Determine which schedules are due. Source and filter-set schedules update only their
        // scoped sources; the global schedule is the only path that updates all enabled sources.
        boolean globalScheduleDue = false;
        Set<String> dueSetNames = new java.util.HashSet<>();
        Set<String> dueSourceUrls = new LinkedHashSet<>();
        if (setNames != null) {
            for (String setName : setNames) {
                if (FilterSetStore.isDue(context, setName, now)) {
                    dueSetNames.add(setName);
                    dueSourceUrls.addAll(FilterSetStore.getSetUrls(context, setName));
                }
            }
        }

        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_SOURCE_SCHEDULES, Context.MODE_PRIVATE);
        Map<String, HostsSource> enabledSourcesByUrl = new LinkedHashMap<>();
        for (HostsSource src : dao.getAll()) {
            if (src.getId() == HostsSource.USER_SOURCE_ID) continue;
            if (!src.isEnabled()) continue;
            String url = src.getUrl();
            enabledSourcesByUrl.put(url, src);
            int schedule = prefs.getInt(KEY_SCHEDULE_PREFIX + url, SCHEDULE_OFF);
            if (schedule == SCHEDULE_OFF) continue;
            long last = prefs.getLong(KEY_LAST_RUN_PREFIX + url, 0L);
            if (last <= 0L) {
                dueSourceUrls.add(url);
                continue;
            }
            int hour = prefs.getInt(KEY_HOUR_PREFIX + url, 3);
            int minute = prefs.getInt(KEY_MINUTE_PREFIX + url, 0);
            int weekdayIso = prefs.getInt(KEY_WEEKDAY_PREFIX + url, 1);
            boolean due = FilterSetStore.isDueByWallClock(now, last, schedule, weekdayIso, hour, minute);
            if (due) {
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
            if (last <= 0L) {
                FilterSetStore.setGlobalLastRun(context, now);
            } else if (FilterSetStore.isDueByWallClock(now, last, schedule, weekdayIso, hour,
                    minute)) {
                globalScheduleDue = true;
            }
        }

        if (!globalScheduleDue && dueSetNames.isEmpty() && dueSourceUrls.isEmpty()) {
            return Result.success();
        }

        Map<String, HostsSource> dueSourcesByUrl = new LinkedHashMap<>();
        for (String url : dueSourceUrls) {
            HostsSource source = enabledSourcesByUrl.get(url);
            if (source != null) {
                dueSourcesByUrl.put(url, source);
            }
        }
        // Worker-level progress (the per-source percent is handled by SourceModel.getProgress()).
        setProgressAsync(progressData(0, globalScheduleDue
                ? "Updating all enabled sources"
                : "Updating scheduled sources"));

        Timber.i("Scheduled update due (global=%b, sets=%d, sources=%d)",
                globalScheduleDue, dueSetNames.size(), dueSourcesByUrl.size());
        try {
            // Set scheduler task name for UI display
            String taskName = dueSetNames.isEmpty() ? "Scheduled Update" : String.join(", ", dueSetNames);
            sourceModel.setSchedulerTaskName(taskName);
            if (globalScheduleDue) {
                // Global schedule intentionally updates all enabled sources with adaptive batching.
                if (!sourceModel.checkAndRetrieveHostsSources()) {
                    return Result.failure();
                }
            } else {
                List<Integer> dueSourceIds = new ArrayList<>(dueSourcesByUrl.size());
                for (HostsSource source : dueSourcesByUrl.values()) {
                    if (isStopped()) {
                        return Result.failure();
                    }
                    setProgressAsync(progressData(0, source.getLabel()));
                    dueSourceIds.add(source.getId());
                }
                sourceModel.retrieveHostsSources(dueSourceIds);
            }
            adBlockModel.apply();
        } catch (HostErrorException e) {
            Timber.w(e, "Failed scheduled source update");
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

        if (globalScheduleDue) {
            FilterSetStore.setGlobalLastRun(context, now);
        }

        setProgressAsync(progressData(1, "Done"));

        return Result.success();
    }

    static Data progressData(int done, @NonNull String current) {
        return new Data.Builder()
                .putInt(PROGRESS_DONE, done)
                .putInt(PROGRESS_TOTAL, 1)
                .putString(PROGRESS_CURRENT, current)
                .build();
    }
}


