package org.adaway.ui.hosts;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple storage for "filter sets" (custom blocks of filters).
 *
 * Stores by URL, because URL is unique in the DB.
 */
public final class FilterSetStore {
    private static final String PREFS_FILTER_SETS = "filter_sets";
    private static final String KEY_SET_NAMES = "names";
    private static final String KEY_SET_PREFIX = "set_";
    private static final String KEY_SCHEDULE_PREFIX = "schedule_";
    private static final String KEY_LAST_RUN_PREFIX = "last_run_";

    public static final int SCHEDULE_OFF = 0;
    public static final int SCHEDULE_DAILY = 1;
    public static final int SCHEDULE_WEEKLY = 2;

    private FilterSetStore() {}

    @NonNull
    public static Set<String> getSetNames(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        Set<String> names = prefs.getStringSet(KEY_SET_NAMES, null);
        return names != null ? new HashSet<>(names) : new HashSet<>();
    }

    public static void saveSet(@NonNull Context context, @NonNull String name, @NonNull Set<String> urls) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        Set<String> names = getSetNames(context);
        names.add(name);
        prefs.edit()
                .putStringSet(KEY_SET_NAMES, names)
                .putStringSet(KEY_SET_PREFIX + name, new HashSet<>(urls))
                .apply();
    }

    @NonNull
    public static Set<String> getSetUrls(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        Set<String> urls = prefs.getStringSet(KEY_SET_PREFIX + name, null);
        return urls != null ? new HashSet<>(urls) : new HashSet<>();
    }

    public static void setSchedule(@NonNull Context context, @NonNull String name, int schedule) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_SCHEDULE_PREFIX + name, schedule).apply();
    }

    public static int getSchedule(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SCHEDULE_PREFIX + name, SCHEDULE_OFF);
    }

    public static void setLastRun(@NonNull Context context, @NonNull String name, long epochMs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_RUN_PREFIX + name, epochMs).apply();
    }

    public static long getLastRun(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_RUN_PREFIX + name, 0L);
    }

    /**
     * Decide whether a set is due based on its schedule.
     */
    public static boolean isDue(@NonNull Context context, @NonNull String name, long nowMs) {
        int schedule = getSchedule(context, name);
        if (schedule == SCHEDULE_OFF) return false;
        long last = getLastRun(context, name);
        if (last <= 0L) return true;
        long age = nowMs - last;
        if (schedule == SCHEDULE_DAILY) {
            return age >= 24L * 60L * 60L * 1000L;
        }
        if (schedule == SCHEDULE_WEEKLY) {
            return age >= 7L * 24L * 60L * 60L * 1000L;
        }
        return false;
    }
}
