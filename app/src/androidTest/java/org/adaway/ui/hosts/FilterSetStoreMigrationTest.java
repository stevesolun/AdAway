package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class FilterSetStoreMigrationTest {
    private static final String PREFS_FILTER_SETS = "filter_sets";
    private static final String LEGACY_NAMES = "names";
    private static final String LEGACY_SET_PREFIX = "set_";
    private static final String LEGACY_SCHEDULE_PREFIX = "schedule_";
    private static final String LEGACY_LAST_RUN_PREFIX = "last_run_";
    private static final String LEGACY_HOUR_PREFIX = "schedule_hour_";
    private static final String LEGACY_MINUTE_PREFIX = "schedule_minute_";
    private static final String LEGACY_WEEKDAY_PREFIX = "schedule_weekday_";
    private static final String LEGACY_ACTIVE_PROFILE = "active_profile";

    private static final String IDS = "ids";
    private static final String DISPLAY_PREFIX = "display_";
    private static final String CANONICAL_PREFIX = "canonical_";
    private static final String SET_ID_PREFIX = "set_id_";
    private static final String SCHEDULE_ID_PREFIX = "schedule_id_";
    private static final String LAST_RUN_ID_PREFIX = "last_run_id_";
    private static final String HOUR_ID_PREFIX = "schedule_hour_id_";
    private static final String MINUTE_ID_PREFIX = "schedule_minute_id_";
    private static final String WEEKDAY_ID_PREFIX = "schedule_weekday_id_";
    private static final String ACTIVE_PROFILE_ID = "active_profile_id";

    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS_FILTER_SETS, Context.MODE_PRIVATE);
        clearPrefs();
    }

    @After
    public void tearDown() {
        clearPrefs();
    }

    @Test
    public void legacyDisplayNameProfileMigratesToStableIdKeys() {
        Set<String> urls = setOf("https://filters.example/family.txt",
                "https://filters.example/privacy.txt");
        assertTrue(prefs.edit()
                .putStringSet(LEGACY_NAMES, setOf("Family Pack"))
                .putStringSet(LEGACY_SET_PREFIX + "Family Pack", urls)
                .putInt(LEGACY_SCHEDULE_PREFIX + "Family Pack", FilterSetStore.SCHEDULE_WEEKLY)
                .putLong(LEGACY_LAST_RUN_PREFIX + "Family Pack", 1234L)
                .putInt(LEGACY_HOUR_PREFIX + "Family Pack", 21)
                .putInt(LEGACY_MINUTE_PREFIX + "Family Pack", 45)
                .putInt(LEGACY_WEEKDAY_PREFIX + "Family Pack", 5)
                .putString(LEGACY_ACTIVE_PROFILE, "Family Pack")
                .commit());

        assertTrue(FilterSetStore.getSetNames(context).contains("Family Pack"));
        String id = onlyProfileId();
        assertEquals("Migration must write active_profile_id during the first public read.",
                id, prefs.getString(ACTIVE_PROFILE_ID, null));
        assertEquals(urls, FilterSetStore.getSetUrls(context, "Family Pack"));
        assertEquals(FilterSetStore.SCHEDULE_WEEKLY,
                FilterSetStore.getSchedule(context, "Family Pack"));
        assertEquals(1234L, FilterSetStore.getLastRun(context, "Family Pack"));
        assertEquals(21, FilterSetStore.getScheduleHour(context, "Family Pack"));
        assertEquals(45, FilterSetStore.getScheduleMinute(context, "Family Pack"));
        assertEquals(5, FilterSetStore.getScheduleWeekdayIso(context, "Family Pack"));
        assertEquals("Family Pack", FilterSetStore.getActiveProfile(context));

        assertEquals("Family Pack", prefs.getString(DISPLAY_PREFIX + id, null));
        assertEquals(id, prefs.getString(CANONICAL_PREFIX + "family pack", null));
        assertEquals(urls, prefs.getStringSet(SET_ID_PREFIX + id, null));
        assertEquals(FilterSetStore.SCHEDULE_WEEKLY,
                prefs.getInt(SCHEDULE_ID_PREFIX + id, FilterSetStore.SCHEDULE_OFF));
        assertEquals(1234L, prefs.getLong(LAST_RUN_ID_PREFIX + id, 0L));
        assertEquals(21, prefs.getInt(HOUR_ID_PREFIX + id, -1));
        assertEquals(45, prefs.getInt(MINUTE_ID_PREFIX + id, -1));
        assertEquals(5, prefs.getInt(WEEKDAY_ID_PREFIX + id, -1));
        assertEquals(id, prefs.getString(ACTIVE_PROFILE_ID, null));
    }

    @Test
    public void newProfileWritesUseStableIdKeysOnly() {
        Set<String> urls = setOf("https://filters.example/travel.txt");

        FilterSetStore.saveSet(context, "Travel", urls);
        FilterSetStore.setSchedule(context, "Travel", FilterSetStore.SCHEDULE_DAILY, 1, 6, 30);
        FilterSetStore.setLastRun(context, "Travel", 5678L);
        FilterSetStore.setActiveProfile(context, "Travel");

        String id = onlyProfileId();
        assertEquals("Travel", prefs.getString(DISPLAY_PREFIX + id, null));
        assertEquals(id, prefs.getString(CANONICAL_PREFIX + "travel", null));
        assertEquals(urls, prefs.getStringSet(SET_ID_PREFIX + id, null));
        assertEquals(FilterSetStore.SCHEDULE_DAILY,
                prefs.getInt(SCHEDULE_ID_PREFIX + id, FilterSetStore.SCHEDULE_OFF));
        assertEquals(5678L, prefs.getLong(LAST_RUN_ID_PREFIX + id, 0L));
        assertEquals(id, prefs.getString(ACTIVE_PROFILE_ID, null));
        assertFalse(prefs.contains(LEGACY_SET_PREFIX + "Travel"));
        assertFalse(prefs.contains(LEGACY_SCHEDULE_PREFIX + "Travel"));
        assertFalse(prefs.contains(LEGACY_LAST_RUN_PREFIX + "Travel"));
    }

    @Test
    public void reservedProfilesUseDeterministicProfileIds() {
        Set<String> urls = setOf("https://filters.example/safe.txt");

        FilterSetStore.savePresetProfile(context, FilterSetStore.PROFILE_SAFE, urls);

        assertEquals(setOf("profile_safe"), prefs.getStringSet(IDS, null));
        assertEquals(FilterSetStore.PROFILE_SAFE,
                prefs.getString(DISPLAY_PREFIX + "profile_safe", null));
        assertEquals(urls, prefs.getStringSet(SET_ID_PREFIX + "profile_safe", null));
        assertEquals("profile_safe", prefs.getString(ACTIVE_PROFILE_ID, null));
    }

    @Test
    public void legacyCurrentSelectionDoesNotRenameCustomProfile() {
        Set<String> urls = setOf("https://filters.example/current.txt");
        assertTrue(prefs.edit()
                .putStringSet(LEGACY_NAMES, setOf("Current selection"))
                .putStringSet(LEGACY_SET_PREFIX + "Current selection", urls)
                .putString(LEGACY_ACTIVE_PROFILE, "Current selection")
                .commit());

        Set<String> names = FilterSetStore.getSetNames(context);

        assertTrue(names.contains("Scheduled selection"));
        assertFalse(names.contains("Current selection"));
        assertEquals(urls, FilterSetStore.getSetUrls(context, "Scheduled selection"));
        assertFalse(prefs.getStringSet(IDS, setOf()).contains("profile_custom"));
        assertEquals("Scheduled selection", prefs.getString(
                DISPLAY_PREFIX + prefs.getString(ACTIVE_PROFILE_ID, ""), null));
    }

    @Test
    public void legacyCanonicalDuplicatesRemainSeparatelyReachable() {
        Set<String> firstUrls = setOf("https://filters.example/family-one.txt");
        Set<String> secondUrls = setOf("https://filters.example/family-two.txt");
        assertTrue(prefs.edit()
                .putStringSet(LEGACY_NAMES, setOf("Family Pack", "family   pack"))
                .putStringSet(LEGACY_SET_PREFIX + "Family Pack", firstUrls)
                .putStringSet(LEGACY_SET_PREFIX + "family   pack", secondUrls)
                .commit());

        Set<String> names = FilterSetStore.getSetNames(context);

        assertEquals(2, names.size());
        Set<String> canonicalNames = new HashSet<>();
        Set<Set<String>> resolvedUrlSets = new HashSet<>();
        for (String name : names) {
            assertTrue("Migrated duplicate display names must be canonical-unique.",
                    canonicalNames.add(FilterSetStore.canonicalSetName(name)));
            resolvedUrlSets.add(FilterSetStore.getSetUrls(context, name));
        }
        assertTrue(resolvedUrlSets.contains(firstUrls));
        assertTrue(resolvedUrlSets.contains(secondUrls));
    }

    @Test
    public void reconcileActiveProfileRepairsExactSavedProfileAfterLostPreferenceWrite() {
        Set<String> travelUrls = setOf("https://filters.example/travel.txt",
                "https://filters.example/privacy.txt");
        Set<String> familyUrls = setOf("https://filters.example/family.txt");
        FilterSetStore.saveSet(context, "Travel", travelUrls);
        FilterSetStore.saveSet(context, "Family", familyUrls);
        FilterSetStore.setActiveProfile(context, "Family");

        String reconciled = FilterSetStore.reconcileActiveProfile(context, travelUrls);

        assertEquals("Travel", reconciled);
        assertEquals("Travel", FilterSetStore.getActiveProfile(context));
    }

    @Test
    public void reconcileActiveProfilePreservesExtendedCustomization() {
        Set<String> safeUrls = setOf("https://filters.example/safe.txt");
        Set<String> extendedUrls = setOf("https://filters.example/safe.txt",
                "https://filters.example/extra.txt");
        FilterSetStore.savePresetProfile(context, FilterSetStore.PROFILE_SAFE, safeUrls);

        String reconciled = FilterSetStore.reconcileActiveProfile(context, extendedUrls);

        assertEquals(FilterSetStore.PROFILE_SAFE, reconciled);
        assertEquals(FilterSetStore.PROFILE_SAFE, FilterSetStore.getActiveProfile(context));
    }

    @Test
    public void renameUserProfilePreservesStableIdScheduleAndActiveIdentity() {
        Set<String> urls = setOf("https://filters.example/work.txt");
        FilterSetStore.saveSet(context, "Work", urls);
        FilterSetStore.setSchedule(context, "Work", FilterSetStore.SCHEDULE_DAILY, 1, 7, 15);
        FilterSetStore.setActiveProfile(context, "Work");
        String id = onlyProfileId();

        assertTrue(FilterSetStore.renameSet(context, "Work", "Office"));

        assertFalse(FilterSetStore.hasSet(context, "Work"));
        assertTrue(FilterSetStore.hasSet(context, "Office"));
        assertEquals(urls, FilterSetStore.getSetUrls(context, "Office"));
        assertEquals(FilterSetStore.SCHEDULE_DAILY,
                FilterSetStore.getSchedule(context, "Office"));
        assertEquals(7, FilterSetStore.getScheduleHour(context, "Office"));
        assertEquals(15, FilterSetStore.getScheduleMinute(context, "Office"));
        assertEquals("Office", FilterSetStore.getActiveProfile(context));
        assertEquals(id, prefs.getString(CANONICAL_PREFIX + "office", null));
        assertFalse(prefs.contains(CANONICAL_PREFIX + "work"));
    }

    @Test
    public void deleteActiveUserProfileClearsStableKeysAndFallsBackToCustom() {
        Set<String> urls = setOf("https://filters.example/work.txt");
        FilterSetStore.saveSet(context, "Work", urls);
        FilterSetStore.setSchedule(context, "Work", FilterSetStore.SCHEDULE_WEEKLY, 5, 8, 30);
        FilterSetStore.setLastRun(context, "Work", 12345L);
        FilterSetStore.setActiveProfile(context, "Work");
        String id = onlyProfileId();

        assertTrue(FilterSetStore.deleteSet(context, "Work"));

        assertFalse(FilterSetStore.hasSet(context, "Work"));
        assertFalse(FilterSetStore.getSetNames(context).contains("Work"));
        assertEquals(FilterSetStore.PROFILE_CUSTOM, FilterSetStore.getActiveProfile(context));
        assertFalse(prefs.getStringSet(IDS, setOf()).contains(id));
        assertFalse(prefs.contains(DISPLAY_PREFIX + id));
        assertFalse(prefs.contains(CANONICAL_PREFIX + "work"));
        assertFalse(prefs.contains(SET_ID_PREFIX + id));
        assertFalse(prefs.contains(SCHEDULE_ID_PREFIX + id));
        assertFalse(prefs.contains(LAST_RUN_ID_PREFIX + id));
    }

    @NonNull
    private String onlyProfileId() {
        Set<String> ids = prefs.getStringSet(IDS, null);
        assertNotNull(ids);
        assertEquals(1, ids.size());
        return ids.iterator().next();
    }

    @NonNull
    private static Set<String> setOf(@NonNull String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private void clearPrefs() {
        assertTrue(prefs.edit().clear().commit());
    }
}
