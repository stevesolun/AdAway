package org.adaway.ui.hosts;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    private static final String KEY_SCHEDULE_HOUR_PREFIX = "schedule_hour_";
    private static final String KEY_SCHEDULE_MINUTE_PREFIX = "schedule_minute_";
    private static final String KEY_SCHEDULE_WEEKDAY_PREFIX = "schedule_weekday_";

    // Global schedule (update all enabled sources)
    private static final String KEY_GLOBAL_ENABLED = "global_enabled";
    private static final String KEY_GLOBAL_SCHEDULE = "global_schedule";
    private static final String KEY_GLOBAL_WEEKDAY = "global_weekday";
    private static final String KEY_GLOBAL_HOUR = "global_hour";
    private static final String KEY_GLOBAL_MINUTE = "global_minute";
    private static final String KEY_GLOBAL_LAST_RUN = "global_last_run";

    public static final int SCHEDULE_OFF = 0;
    public static final int SCHEDULE_DAILY = 1;
    public static final int SCHEDULE_WEEKLY = 2;

    private FilterSetStore() {}

    /**
     * Ensure global defaults exist: enabled daily at 03:00.
     */
    public static void ensureGlobalDefaults(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_GLOBAL_ENABLED)) {
            prefs.edit()
                    .putBoolean(KEY_GLOBAL_ENABLED, true)
                    .putInt(KEY_GLOBAL_SCHEDULE, SCHEDULE_DAILY)
                    .putInt(KEY_GLOBAL_WEEKDAY, 1)
                    .putInt(KEY_GLOBAL_HOUR, 3)
                    .putInt(KEY_GLOBAL_MINUTE, 0)
                    .apply();
        }
    }

    public static boolean isGlobalScheduleEnabled(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_GLOBAL_ENABLED, true);
    }

    public static void setGlobalEnabled(@NonNull Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_GLOBAL_ENABLED, enabled).apply();
    }

    public static int getGlobalSchedule(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_GLOBAL_SCHEDULE, SCHEDULE_DAILY);
    }

    public static int getGlobalWeekdayIso(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_GLOBAL_WEEKDAY, 1);
    }

    public static int getGlobalHour(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_GLOBAL_HOUR, 3);
    }

    public static int getGlobalMinute(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_GLOBAL_MINUTE, 0);
    }

    public static long getGlobalLastRun(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_GLOBAL_LAST_RUN, 0L);
    }

    public static void setGlobalLastRun(@NonNull Context context, long epochMs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_GLOBAL_LAST_RUN, epochMs).apply();
    }

    public static void setGlobalSchedule(@NonNull Context context, int schedule, int weekdayIso, int hour24, int minute) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_GLOBAL_ENABLED, schedule != SCHEDULE_OFF)
                .putInt(KEY_GLOBAL_SCHEDULE, schedule)
                .putInt(KEY_GLOBAL_WEEKDAY, clamp(weekdayIso, 1, 7))
                .putInt(KEY_GLOBAL_HOUR, clamp(hour24, 0, 23))
                .putInt(KEY_GLOBAL_MINUTE, clamp(minute, 0, 59))
                .remove(KEY_GLOBAL_LAST_RUN)
                .apply();
    }

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

    /**
     * Set schedule with a preferred local time and optional weekday.
     *
     * @param schedule     One of SCHEDULE_OFF/SCHEDULE_DAILY/SCHEDULE_WEEKLY
     * @param weekdayIso   ISO weekday 1..7 (Mon..Sun). Only used for weekly schedules.
     * @param hour24       0..23
     * @param minute       0..59
     */
    public static void setSchedule(@NonNull Context context, @NonNull String name, int schedule, int weekdayIso, int hour24, int minute) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit();
        e.putInt(KEY_SCHEDULE_PREFIX + name, schedule);
        e.putInt(KEY_SCHEDULE_WEEKDAY_PREFIX + name, clamp(weekdayIso, 1, 7));
        e.putInt(KEY_SCHEDULE_HOUR_PREFIX + name, clamp(hour24, 0, 23));
        e.putInt(KEY_SCHEDULE_MINUTE_PREFIX + name, clamp(minute, 0, 59));
        e.apply();
    }

    public static int getSchedule(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SCHEDULE_PREFIX + name, SCHEDULE_OFF);
    }

    public static int getScheduleHour(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SCHEDULE_HOUR_PREFIX + name, 3);
    }

    public static int getScheduleMinute(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SCHEDULE_MINUTE_PREFIX + name, 0);
    }

    /**
     * ISO weekday 1..7 (Mon..Sun).
     */
    public static int getScheduleWeekdayIso(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_SCHEDULE_WEEKDAY_PREFIX + name, DayOfWeek.MONDAY.getValue());
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
        int hour = getScheduleHour(context, name);
        int minute = getScheduleMinute(context, name);
        int weekdayIso = getScheduleWeekdayIso(context, name);
        return isDueByWallClock(nowMs, last, schedule, weekdayIso, hour, minute);
    }

    /**
     * Wall-clock schedule check:
     * - Daily: run once after the chosen local time each day
     * - Weekly: run once after the chosen weekday+time each week
     */
    public static boolean isDueByWallClock(long nowMs, long lastRunMs, int schedule, int weekdayIso, int hour24, int minute) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = Instant.ofEpochMilli(nowMs).atZone(zone);
        ZonedDateTime last = Instant.ofEpochMilli(lastRunMs).atZone(zone);
        LocalTime time = LocalTime.of(clamp(hour24, 0, 23), clamp(minute, 0, 59));

        ZonedDateTime mostRecent;
        if (schedule == SCHEDULE_DAILY) {
            LocalDate date = now.toLocalDate();
            LocalDateTime todayAt = LocalDateTime.of(date, time);
            ZonedDateTime candidate = todayAt.atZone(zone);
            mostRecent = now.isBefore(candidate) ? candidate.minusDays(1) : candidate;
        } else if (schedule == SCHEDULE_WEEKLY) {
            int target = clamp(weekdayIso, 1, 7);
            DayOfWeek dow = DayOfWeek.of(target);
            // Candidate in this week at the chosen weekday+time.
            LocalDate date = now.toLocalDate();
            int diff = dow.getValue() - now.getDayOfWeek().getValue();
            LocalDate thisWeekTarget = date.plusDays(diff);
            ZonedDateTime candidate = LocalDateTime.of(thisWeekTarget, time).atZone(zone);
            mostRecent = now.isBefore(candidate) ? candidate.minusWeeks(1) : candidate;
        } else {
            return false;
        }

        return last.isBefore(mostRecent);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}


