package org.adaway.ui.source;

import static org.adaway.ui.hosts.FilterSetStore.SCHEDULE_OFF;
import static org.adaway.ui.hosts.FilterSetStore.SCHEDULE_WEEKLY;
import static org.adaway.util.Constants.PREFS_SOURCE_SCHEDULES;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;

/** User edits to URL schedules, shared by the source editor and schedule manager. */
public final class SourceScheduleStore {
    static final String KEY_SCHEDULE_PREFIX = "schedule_url_";
    static final String KEY_LAST_RUN_PREFIX = "last_run_url_";
    static final String KEY_HOUR_PREFIX = "hour_url_";
    static final String KEY_MINUTE_PREFIX = "minute_url_";
    static final String KEY_WEEKDAY_PREFIX = "weekday_url_";

    private SourceScheduleStore() {}

    public static void save(Context context, String url, int schedule, int weekday, int hour, int minute) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SOURCE_SCHEDULES, Context.MODE_PRIVATE);
        weekday = Math.max(1, Math.min(7, weekday));
        hour = Math.max(0, Math.min(23, hour));
        minute = Math.max(0, Math.min(59, minute));
        boolean changed = prefs.getInt(KEY_SCHEDULE_PREFIX + url, SCHEDULE_OFF) != schedule
                || (schedule == SCHEDULE_WEEKLY && prefs.getInt(KEY_WEEKDAY_PREFIX + url, 1) != weekday)
                || (schedule != SCHEDULE_OFF && (prefs.getInt(KEY_HOUR_PREFIX + url, 3) != hour
                        || prefs.getInt(KEY_MINUTE_PREFIX + url, 0) != minute));
        if (!changed) {
            // The editor records an explicit Off even when this URL had no schedule.
            if (!prefs.contains(KEY_SCHEDULE_PREFIX + url)) {
                prefs.edit().putInt(KEY_SCHEDULE_PREFIX + url, SCHEDULE_OFF).apply();
            }
            return;
        }
        prefs.edit().putInt(KEY_SCHEDULE_PREFIX + url, schedule)
                .putInt(KEY_WEEKDAY_PREFIX + url, weekday)
                .putInt(KEY_HOUR_PREFIX + url, hour)
                .putInt(KEY_MINUTE_PREFIX + url, minute)
                .remove(KEY_LAST_RUN_PREFIX + url).apply();
        new BackupManager(context).dataChanged();
    }

    static void clear(Context context, String url) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SOURCE_SCHEDULES, Context.MODE_PRIVATE);
        boolean scheduled = prefs.getInt(KEY_SCHEDULE_PREFIX + url, SCHEDULE_OFF) != SCHEDULE_OFF;
        prefs.edit().remove(KEY_SCHEDULE_PREFIX + url).remove(KEY_WEEKDAY_PREFIX + url)
                .remove(KEY_HOUR_PREFIX + url).remove(KEY_MINUTE_PREFIX + url)
                .remove(KEY_LAST_RUN_PREFIX + url).apply();
        if (scheduled) new BackupManager(context).dataChanged();
    }
}
