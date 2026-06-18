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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Simple storage for "filter sets" (custom blocks of filters).
 *
 * Stores by URL, because URL is unique in the DB.
 */
public final class FilterSetStore {
    private static final String PREFS_FILTER_SETS = "filter_sets";
    private static final String KEY_SET_NAMES = "names";
    private static final String KEY_SET_PREFIX = "set_";
    private static final String KEY_SET_IDS = "ids";
    private static final String KEY_SET_DISPLAY_PREFIX = "display_";
    private static final String KEY_SET_CANONICAL_PREFIX = "canonical_";
    private static final String KEY_SET_ID_PREFIX = "set_id_";
    private static final String KEY_SCHEDULE_PREFIX = "schedule_";
    private static final String KEY_LAST_RUN_PREFIX = "last_run_";
    private static final String KEY_SCHEDULE_HOUR_PREFIX = "schedule_hour_";
    private static final String KEY_SCHEDULE_MINUTE_PREFIX = "schedule_minute_";
    private static final String KEY_SCHEDULE_WEEKDAY_PREFIX = "schedule_weekday_";
    private static final String KEY_SCHEDULE_ID_PREFIX = "schedule_id_";
    private static final String KEY_LAST_RUN_ID_PREFIX = "last_run_id_";
    private static final String KEY_SCHEDULE_HOUR_ID_PREFIX = "schedule_hour_id_";
    private static final String KEY_SCHEDULE_MINUTE_ID_PREFIX = "schedule_minute_id_";
    private static final String KEY_SCHEDULE_WEEKDAY_ID_PREFIX = "schedule_weekday_id_";
    private static final String KEY_ACTIVE_PROFILE = "active_profile";
    private static final String KEY_ACTIVE_PROFILE_ID = "active_profile_id";

    public static final String PROFILE_SAFE = "safe";
    public static final String PROFILE_BALANCED = "balanced";
    public static final String PROFILE_AGGRESSIVE = "aggressive";
    public static final String PROFILE_CUSTOM = "custom";

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

    public enum SetNameValidation {
        OK,
        EMPTY,
        RESERVED,
        DUPLICATE
    }

    /**
     * Ensure global defaults exist: enabled daily at 03:00.
     */
    public static void ensureGlobalDefaults(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_GLOBAL_ENABLED)) {
            long now = System.currentTimeMillis();
            prefs.edit()
                    .putBoolean(KEY_GLOBAL_ENABLED, true)
                    .putInt(KEY_GLOBAL_SCHEDULE, SCHEDULE_DAILY)
                    .putInt(KEY_GLOBAL_WEEKDAY, 1)
                    .putInt(KEY_GLOBAL_HOUR, 3)
                    .putInt(KEY_GLOBAL_MINUTE, 0)
                    .putLong(KEY_GLOBAL_LAST_RUN, now)
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
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(KEY_GLOBAL_ENABLED, schedule != SCHEDULE_OFF)
                .putInt(KEY_GLOBAL_SCHEDULE, schedule)
                .putInt(KEY_GLOBAL_WEEKDAY, clamp(weekdayIso, 1, 7))
                .putInt(KEY_GLOBAL_HOUR, clamp(hour24, 0, 23))
                .putInt(KEY_GLOBAL_MINUTE, clamp(minute, 0, 59));
        if (schedule == SCHEDULE_OFF) {
            editor.remove(KEY_GLOBAL_LAST_RUN);
        } else {
            editor.putLong(KEY_GLOBAL_LAST_RUN, System.currentTimeMillis());
        }
        editor.apply();
    }

    @NonNull
    public static Set<String> getSetNames(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        migrateLegacySets(prefs);
        Set<String> names = new HashSet<>();
        for (String id : getSetIds(prefs)) {
            String displayName = getProfileDisplayName(prefs, id);
            if (displayName != null) {
                names.add(displayName);
            }
        }
        return names;
    }

    public static void saveSet(@NonNull Context context, @NonNull String name, @NonNull Set<String> urls) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String displayName = normalizeActiveProfile(name);
        String id = resolveOrCreateSetId(prefs, displayName);
        prefs.edit()
                .putStringSet(profileValueKey(KEY_SET_ID_PREFIX, id), new HashSet<>(urls))
                .apply();
    }

    public static boolean renameSet(
            @NonNull Context context, @NonNull String oldName, @NonNull String newName) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String id = resolveSetId(prefs, oldName);
        if (id == null || isReservedProfileId(id)) {
            return false;
        }
        String displayName = normalizeActiveProfile(newName);
        if (isReservedSetName(displayName)) {
            return false;
        }
        String canonicalName = canonicalSetName(displayName);
        String existingId = prefs.getString(KEY_SET_CANONICAL_PREFIX + canonicalName, null);
        if (existingId != null && !existingId.equals(id)) {
            return false;
        }
        String oldDisplayName = getProfileDisplayName(prefs, id);
        if (oldDisplayName == null) {
            oldDisplayName = normalizeActiveProfile(oldName);
        }
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_SET_DISPLAY_PREFIX + id, displayName)
                .remove(KEY_SET_CANONICAL_PREFIX + canonicalSetName(oldDisplayName))
                .putString(KEY_SET_CANONICAL_PREFIX + canonicalName, id);
        if (id.equals(prefs.getString(KEY_ACTIVE_PROFILE_ID, null))) {
            editor.putString(KEY_ACTIVE_PROFILE, displayName);
        }
        editor.apply();
        return true;
    }

    public static boolean deleteSet(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String id = resolveSetId(prefs, name);
        if (id == null || isReservedProfileId(id)) {
            return false;
        }
        String displayName = getProfileDisplayName(prefs, id);
        if (displayName == null) {
            displayName = normalizeActiveProfile(name);
        }
        Set<String> ids = getSetIds(prefs);
        ids.remove(id);
        SharedPreferences.Editor editor = prefs.edit()
                .putStringSet(KEY_SET_IDS, ids)
                .remove(KEY_SET_DISPLAY_PREFIX + id)
                .remove(KEY_SET_CANONICAL_PREFIX + canonicalSetName(displayName))
                .remove(profileValueKey(KEY_SET_ID_PREFIX, id))
                .remove(profileValueKey(KEY_SCHEDULE_ID_PREFIX, id))
                .remove(profileValueKey(KEY_LAST_RUN_ID_PREFIX, id))
                .remove(profileValueKey(KEY_SCHEDULE_HOUR_ID_PREFIX, id))
                .remove(profileValueKey(KEY_SCHEDULE_MINUTE_ID_PREFIX, id))
                .remove(profileValueKey(KEY_SCHEDULE_WEEKDAY_ID_PREFIX, id));
        if (id.equals(prefs.getString(KEY_ACTIVE_PROFILE_ID, null))) {
            editor.putString(KEY_ACTIVE_PROFILE, PROFILE_CUSTOM)
                    .putString(KEY_ACTIVE_PROFILE_ID, "profile_custom");
        }
        editor.apply();
        return true;
    }

    @NonNull
    public static String canonicalSetName(@Nullable String name) {
        String normalized = normalizeActiveProfile(name);
        return normalized.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static boolean isReservedSetName(@Nullable String name) {
        String canonicalName = canonicalSetName(name);
        return PROFILE_CUSTOM.equals(canonicalName)
                || PROFILE_SAFE.equals(canonicalName)
                || PROFILE_BALANCED.equals(canonicalName)
                || PROFILE_AGGRESSIVE.equals(canonicalName)
                || "safe mode".equals(canonicalName)
                || "balanced mode".equals(canonicalName)
                || "aggressive mode".equals(canonicalName)
                || "current selection".equals(canonicalName);
    }

    public static boolean hasCanonicalSetName(@NonNull Set<String> existingNames,
            @Nullable String candidateName) {
        String candidate = canonicalSetName(candidateName);
        for (String existing : existingNames) {
            if (canonicalSetName(existing).equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public static SetNameValidation validateSetName(@Nullable String name,
            @NonNull Set<String> existingNames) {
        if (name == null || name.trim().isEmpty()) {
            return SetNameValidation.EMPTY;
        }
        if (isReservedSetName(name)) {
            return SetNameValidation.RESERVED;
        }
        if (hasCanonicalSetName(existingNames, name)) {
            return SetNameValidation.DUPLICATE;
        }
        return SetNameValidation.OK;
    }

    public static boolean hasSet(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        return resolveSetId(prefs, name) != null;
    }

    public static boolean hasSetUrls(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String id = resolveSetId(prefs, name);
        if (id != null && prefs.contains(profileValueKey(KEY_SET_ID_PREFIX, id))) {
            return true;
        }
        return prefs.contains(KEY_SET_PREFIX + normalizeActiveProfile(name));
    }

    public static void savePresetProfile(
            @NonNull Context context, @NonNull String preset, @NonNull Set<String> urls) {
        String profile = normalizePresetProfile(preset);
        saveSet(context, profile, urls);
        setActiveProfile(context, profile);
    }

    public static void saveCustomProfile(@NonNull Context context, @NonNull Set<String> urls) {
        saveSet(context, PROFILE_CUSTOM, urls);
        setActiveProfile(context, PROFILE_CUSTOM);
    }

    public static void markCustomProfile(@NonNull Context context) {
        setActiveProfile(context, PROFILE_CUSTOM);
    }

    public static void setActiveProfile(@NonNull Context context, @NonNull String profile) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String displayName = normalizeActiveProfile(profile);
        String id = resolveSetId(prefs, displayName);
        if (id == null) {
            id = reservedProfileId(displayName);
        }
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_ACTIVE_PROFILE, displayName);
        if (id != null) {
            editor.putString(KEY_ACTIVE_PROFILE_ID, id);
        } else {
            editor.remove(KEY_ACTIVE_PROFILE_ID);
        }
        editor.apply();
    }

    @NonNull
    public static String getActiveProfile(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        migrateLegacySets(prefs);
        String id = prefs.getString(KEY_ACTIVE_PROFILE_ID, null);
        if (id != null) {
            String displayName = getProfileDisplayName(prefs, id);
            if (displayName != null) {
                return displayName;
            }
        }
        String profile = prefs.getString(KEY_ACTIVE_PROFILE, PROFILE_CUSTOM);
        return normalizeActiveProfile(profile);
    }

    @NonNull
    public static String reconcileActiveProfile(
            @NonNull Context context, @NonNull Set<String> enabledUrls) {
        String activeProfile = getActiveProfile(context);
        if (getSetUrls(context, activeProfile).equals(enabledUrls)) {
            return activeProfile;
        }

        String exactProfile = findExactProfileForUrls(context, enabledUrls);
        if (exactProfile != null) {
            setActiveProfile(context, exactProfile);
            return exactProfile;
        }
        return activeProfile;
    }

    @NonNull
    public static String normalizePresetProfile(@Nullable String preset) {
        String normalized = normalizeActiveProfile(preset).toLowerCase(java.util.Locale.ROOT);
        if (PROFILE_SAFE.equals(normalized)
                || PROFILE_BALANCED.equals(normalized)
                || PROFILE_AGGRESSIVE.equals(normalized)) {
            return normalized;
        }
        return PROFILE_CUSTOM;
    }

    @NonNull
    public static String normalizeActiveProfile(@Nullable String profile) {
        if (profile == null) {
            return PROFILE_CUSTOM;
        }
        String normalized = profile.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? PROFILE_CUSTOM : normalized;
    }

    @NonNull
    public static Set<String> getSetUrls(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String id = resolveSetId(prefs, name);
        Set<String> urls = id != null
                ? prefs.getStringSet(profileValueKey(KEY_SET_ID_PREFIX, id), null)
                : null;
        if (urls == null) {
            urls = prefs.getStringSet(KEY_SET_PREFIX + normalizeActiveProfile(name), null);
        }
        return urls != null ? new HashSet<>(urls) : new HashSet<>();
    }

    @Nullable
    private static String findExactProfileForUrls(
            @NonNull Context context, @NonNull Set<String> enabledUrls) {
        List<String> names = new ArrayList<>(getSetNames(context));
        Collections.sort(names, (left, right) -> {
            boolean leftCustom = PROFILE_CUSTOM.equals(canonicalSetName(left));
            boolean rightCustom = PROFILE_CUSTOM.equals(canonicalSetName(right));
            if (leftCustom != rightCustom) {
                return leftCustom ? 1 : -1;
            }
            return canonicalSetName(left).compareTo(canonicalSetName(right));
        });
        for (String name : names) {
            if (getSetUrls(context, name).equals(enabledUrls)) {
                return name;
            }
        }
        return null;
    }

    public static void setSchedule(@NonNull Context context, @NonNull String name, int schedule) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String id = resolveOrCreateSetId(prefs, name);
        prefs.edit().putInt(profileValueKey(KEY_SCHEDULE_ID_PREFIX, id), schedule).apply();
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
        String id = resolveOrCreateSetId(prefs, name);
        SharedPreferences.Editor e = prefs.edit();
        e.putInt(profileValueKey(KEY_SCHEDULE_ID_PREFIX, id), schedule);
        e.putInt(profileValueKey(KEY_SCHEDULE_WEEKDAY_ID_PREFIX, id), clamp(weekdayIso, 1, 7));
        e.putInt(profileValueKey(KEY_SCHEDULE_HOUR_ID_PREFIX, id), clamp(hour24, 0, 23));
        e.putInt(profileValueKey(KEY_SCHEDULE_MINUTE_ID_PREFIX, id), clamp(minute, 0, 59));
        e.apply();
    }

    public static int getSchedule(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String id = resolveSetId(prefs, name);
        if (id != null) {
            String key = profileValueKey(KEY_SCHEDULE_ID_PREFIX, id);
            if (prefs.contains(key)) {
                return prefs.getInt(key, SCHEDULE_OFF);
            }
        }
        return prefs.getInt(KEY_SCHEDULE_PREFIX + normalizeActiveProfile(name), SCHEDULE_OFF);
    }

    public static int getScheduleHour(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String id = resolveSetId(prefs, name);
        if (id != null) {
            String key = profileValueKey(KEY_SCHEDULE_HOUR_ID_PREFIX, id);
            if (prefs.contains(key)) {
                return prefs.getInt(key, 3);
            }
        }
        return prefs.getInt(KEY_SCHEDULE_HOUR_PREFIX + normalizeActiveProfile(name), 3);
    }

    public static int getScheduleMinute(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String id = resolveSetId(prefs, name);
        if (id != null) {
            String key = profileValueKey(KEY_SCHEDULE_MINUTE_ID_PREFIX, id);
            if (prefs.contains(key)) {
                return prefs.getInt(key, 0);
            }
        }
        return prefs.getInt(KEY_SCHEDULE_MINUTE_PREFIX + normalizeActiveProfile(name), 0);
    }

    /**
     * ISO weekday 1..7 (Mon..Sun).
     */
    public static int getScheduleWeekdayIso(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String id = resolveSetId(prefs, name);
        if (id != null) {
            String key = profileValueKey(KEY_SCHEDULE_WEEKDAY_ID_PREFIX, id);
            if (prefs.contains(key)) {
                return prefs.getInt(key, DayOfWeek.MONDAY.getValue());
            }
        }
        return prefs.getInt(KEY_SCHEDULE_WEEKDAY_PREFIX + normalizeActiveProfile(name),
                DayOfWeek.MONDAY.getValue());
    }

    public static void setLastRun(@NonNull Context context, @NonNull String name, long epochMs) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String id = resolveOrCreateSetId(prefs, name);
        prefs.edit().putLong(profileValueKey(KEY_LAST_RUN_ID_PREFIX, id), epochMs).apply();
    }

    public static long getLastRun(@NonNull Context context, @NonNull String name) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        String id = resolveSetId(prefs, name);
        if (id != null) {
            String key = profileValueKey(KEY_LAST_RUN_ID_PREFIX, id);
            if (prefs.contains(key)) {
                return prefs.getLong(key, 0L);
            }
        }
        return prefs.getLong(KEY_LAST_RUN_PREFIX + normalizeActiveProfile(name), 0L);
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

    @Nullable
    static String reservedProfileId(@Nullable String name) {
        String canonicalName = canonicalSetName(name);
        if (PROFILE_SAFE.equals(canonicalName) || "safe mode".equals(canonicalName)) {
            return "profile_safe";
        }
        if (PROFILE_BALANCED.equals(canonicalName) || "balanced mode".equals(canonicalName)) {
            return "profile_balanced";
        }
        if (PROFILE_AGGRESSIVE.equals(canonicalName) || "aggressive mode".equals(canonicalName)) {
            return "profile_aggressive";
        }
        if (PROFILE_CUSTOM.equals(canonicalName) || "current selection".equals(canonicalName)) {
            return "profile_custom";
        }
        return null;
    }

    private static boolean isReservedProfileId(@NonNull String id) {
        return "profile_safe".equals(id)
                || "profile_balanced".equals(id)
                || "profile_aggressive".equals(id)
                || "profile_custom".equals(id);
    }

    @NonNull
    static String newOpaqueProfileIdForTest(@NonNull String displayName) {
        return newOpaqueProfileId(displayName);
    }

    @NonNull
    static String profileValueKeyForTest(@NonNull String prefix, @NonNull String id) {
        return profileValueKey(prefix, id);
    }

    private static void migrateLegacySets(@NonNull SharedPreferences prefs) {
        Set<String> legacyNames = prefs.getStringSet(KEY_SET_NAMES, null);
        String activeProfile = prefs.getString(KEY_ACTIVE_PROFILE, null);
        if ((legacyNames == null || legacyNames.isEmpty())
                && activeProfile == null
                && !prefs.contains(KEY_ACTIVE_PROFILE_ID)) {
            return;
        }

        Set<String> ids = getSetIds(prefs);
        Map<String, String> migratedIdsByCanonicalName = new HashMap<>();
        Map<String, String> migratedIdsByLegacyCanonicalName = new HashMap<>();
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;

        if (legacyNames != null) {
            for (String legacyName : legacyNames) {
                String legacyDisplayName = normalizeActiveProfile(legacyName);
                String displayName = normalizeLegacySetDisplayName(prefs,
                        migratedIdsByCanonicalName, legacyDisplayName);
                String canonicalName = canonicalSetName(displayName);
                String id = prefs.getString(KEY_SET_CANONICAL_PREFIX + canonicalName, null);
                if (id == null) {
                    id = reservedProfileId(displayName);
                }
                if (id == null) {
                    id = newUniqueOpaqueProfileId(ids, displayName);
                }

                changed |= putProfileMetadata(prefs, editor, ids, id, displayName);
                migratedIdsByCanonicalName.put(canonicalName, id);
                String legacyCanonicalName = canonicalSetName(legacyDisplayName);
                if (!migratedIdsByLegacyCanonicalName.containsKey(legacyCanonicalName)) {
                    migratedIdsByLegacyCanonicalName.put(legacyCanonicalName, id);
                }

                Set<String> legacySetUrls = prefs.getStringSet(KEY_SET_PREFIX + legacyDisplayName,
                        null);
                if (legacySetUrls == null && !legacyDisplayName.equals(legacyName)) {
                    legacySetUrls = prefs.getStringSet(KEY_SET_PREFIX + legacyName, null);
                }
                String idSetKey = profileValueKey(KEY_SET_ID_PREFIX, id);
                if (legacySetUrls != null && !prefs.contains(idSetKey)) {
                    editor.putStringSet(idSetKey, new HashSet<>(legacySetUrls));
                    changed = true;
                }

                changed |= migrateLegacyInt(prefs, editor, KEY_SCHEDULE_PREFIX, legacyDisplayName,
                        legacyName, KEY_SCHEDULE_ID_PREFIX, id, SCHEDULE_OFF);
                changed |= migrateLegacyInt(prefs, editor, KEY_SCHEDULE_HOUR_PREFIX,
                        legacyDisplayName,
                        legacyName, KEY_SCHEDULE_HOUR_ID_PREFIX, id, 3);
                changed |= migrateLegacyInt(prefs, editor, KEY_SCHEDULE_MINUTE_PREFIX,
                        legacyDisplayName,
                        legacyName, KEY_SCHEDULE_MINUTE_ID_PREFIX, id, 0);
                changed |= migrateLegacyInt(prefs, editor, KEY_SCHEDULE_WEEKDAY_PREFIX,
                        legacyDisplayName,
                        legacyName, KEY_SCHEDULE_WEEKDAY_ID_PREFIX, id,
                        DayOfWeek.MONDAY.getValue());
                changed |= migrateLegacyLong(prefs, editor, KEY_LAST_RUN_PREFIX, legacyDisplayName,
                        legacyName, KEY_LAST_RUN_ID_PREFIX, id, 0L);
            }
            editor.remove(KEY_SET_NAMES);
            changed = true;
        }

        if (activeProfile != null && !prefs.contains(KEY_ACTIVE_PROFILE_ID)) {
            String activeCanonicalName = canonicalSetName(activeProfile);
            String id = migratedIdsByLegacyCanonicalName.get(activeCanonicalName);
            if (id == null) {
                id = prefs.getString(KEY_SET_CANONICAL_PREFIX + activeCanonicalName, null);
            }
            if (id == null) {
                id = reservedProfileId(activeProfile);
            }
            if (id != null) {
                editor.putString(KEY_ACTIVE_PROFILE_ID, id);
                changed = true;
            }
        }

        if (changed) {
            editor.putStringSet(KEY_SET_IDS, ids);
            editor.apply();
        }
    }

    @Nullable
    private static String resolveSetId(@NonNull SharedPreferences prefs, @Nullable String name) {
        migrateLegacySets(prefs);
        String displayName = normalizeActiveProfile(name);
        String id = prefs.getString(KEY_SET_CANONICAL_PREFIX + canonicalSetName(displayName), null);
        if (id != null) {
            return id;
        }
        id = reservedProfileId(displayName);
        if (id != null && getSetIds(prefs).contains(id)) {
            return id;
        }
        return null;
    }

    @NonNull
    private static String resolveOrCreateSetId(@NonNull SharedPreferences prefs,
            @NonNull String name) {
        migrateLegacySets(prefs);
        String displayName = normalizeActiveProfile(name);
        String id = prefs.getString(KEY_SET_CANONICAL_PREFIX + canonicalSetName(displayName), null);
        if (id != null) {
            return id;
        }
        id = reservedProfileId(displayName);
        Set<String> ids = getSetIds(prefs);
        if (id == null) {
            id = newUniqueOpaqueProfileId(ids, displayName);
        }
        SharedPreferences.Editor editor = prefs.edit();
        putProfileMetadata(prefs, editor, ids, id, displayName);
        editor.putStringSet(KEY_SET_IDS, ids);
        editor.apply();
        return id;
    }

    @NonNull
    private static Set<String> getSetIds(@NonNull SharedPreferences prefs) {
        Set<String> ids = prefs.getStringSet(KEY_SET_IDS, null);
        return ids != null ? new HashSet<>(ids) : new HashSet<>();
    }

    @Nullable
    private static String getProfileDisplayName(@NonNull SharedPreferences prefs,
            @NonNull String id) {
        String displayName = prefs.getString(KEY_SET_DISPLAY_PREFIX + id, null);
        if (displayName != null) {
            return displayName;
        }
        switch (id) {
            case "profile_safe":
                return PROFILE_SAFE;
            case "profile_balanced":
                return PROFILE_BALANCED;
            case "profile_aggressive":
                return PROFILE_AGGRESSIVE;
            case "profile_custom":
                return PROFILE_CUSTOM;
            default:
                return null;
        }
    }

    private static boolean putProfileMetadata(@NonNull SharedPreferences prefs,
            @NonNull SharedPreferences.Editor editor, @NonNull Set<String> ids,
            @NonNull String id, @NonNull String displayName) {
        boolean changed = ids.add(id);
        String displayKey = KEY_SET_DISPLAY_PREFIX + id;
        if (!displayName.equals(prefs.getString(displayKey, null))) {
            editor.putString(displayKey, displayName);
            changed = true;
        }
        String canonicalKey = KEY_SET_CANONICAL_PREFIX + canonicalSetName(displayName);
        if (!id.equals(prefs.getString(canonicalKey, null))) {
            editor.putString(canonicalKey, id);
            changed = true;
        }
        return changed;
    }

    private static boolean migrateLegacyInt(@NonNull SharedPreferences prefs,
            @NonNull SharedPreferences.Editor editor, @NonNull String legacyPrefix,
            @NonNull String displayName, @NonNull String legacyName, @NonNull String idPrefix,
            @NonNull String id, int fallback) {
        String idKey = profileValueKey(idPrefix, id);
        if (prefs.contains(idKey)) {
            return false;
        }
        String legacyKey = legacyPrefix + displayName;
        if (!prefs.contains(legacyKey) && !displayName.equals(legacyName)) {
            legacyKey = legacyPrefix + legacyName;
        }
        if (!prefs.contains(legacyKey)) {
            return false;
        }
        editor.putInt(idKey, prefs.getInt(legacyKey, fallback));
        return true;
    }

    @NonNull
    private static String normalizeLegacySetDisplayName(@NonNull SharedPreferences prefs,
            @NonNull Map<String, String> migratedIdsByCanonicalName,
            @NonNull String legacyDisplayName) {
        String baseName = "current selection".equals(canonicalSetName(legacyDisplayName))
                ? "Scheduled selection" : legacyDisplayName;
        String candidate = baseName;
        int suffix = 2;
        while (hasExistingCanonicalName(prefs, migratedIdsByCanonicalName,
                canonicalSetName(candidate))) {
            candidate = baseName + " (legacy " + suffix + ")";
            suffix++;
        }
        return candidate;
    }

    private static boolean hasExistingCanonicalName(@NonNull SharedPreferences prefs,
            @NonNull Map<String, String> migratedIdsByCanonicalName,
            @NonNull String canonicalName) {
        return migratedIdsByCanonicalName.containsKey(canonicalName)
                || prefs.contains(KEY_SET_CANONICAL_PREFIX + canonicalName);
    }

    private static boolean migrateLegacyLong(@NonNull SharedPreferences prefs,
            @NonNull SharedPreferences.Editor editor, @NonNull String legacyPrefix,
            @NonNull String displayName, @NonNull String legacyName, @NonNull String idPrefix,
            @NonNull String id, long fallback) {
        String idKey = profileValueKey(idPrefix, id);
        if (prefs.contains(idKey)) {
            return false;
        }
        String legacyKey = legacyPrefix + displayName;
        if (!prefs.contains(legacyKey) && !displayName.equals(legacyName)) {
            legacyKey = legacyPrefix + legacyName;
        }
        if (!prefs.contains(legacyKey)) {
            return false;
        }
        editor.putLong(idKey, prefs.getLong(legacyKey, fallback));
        return true;
    }

    @NonNull
    private static String newUniqueOpaqueProfileId(@NonNull Set<String> existingIds,
            @NonNull String displayName) {
        String id = newOpaqueProfileId(displayName);
        int suffix = 2;
        while (existingIds.contains(id)) {
            id = newOpaqueProfileId(displayName + "\n" + suffix);
            suffix++;
        }
        return id;
    }

    @NonNull
    private static String newOpaqueProfileId(@NonNull String displayName) {
        return "profile_" + UUID.randomUUID();
    }

    @NonNull
    private static String profileValueKey(@NonNull String prefix, @NonNull String id) {
        return prefix + id;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}


