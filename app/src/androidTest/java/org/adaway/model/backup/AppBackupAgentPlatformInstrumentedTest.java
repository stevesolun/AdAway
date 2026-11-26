package org.adaway.model.backup;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.ui.hosts.FilterSetUpdateService;
import org.adaway.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class AppBackupAgentPlatformInstrumentedTest {
    private static final int TIMEOUT_MS = 20_000;
    private static final String PHASE_ARG = "platformBackupPhase";
    private static final String PHASE_SEED = "seed";
    private static final String PHASE_ASSERT = "assert";
    private static final String PREF_KEY = "platform_backup_restore_probe";
    private static final String PREF_VALUE = "restored-through-bmgr";
    private static final String BACKUP_SOURCE_LABEL = "Platform Backup Restore Source";
    private static final String BACKUP_SOURCE_URL =
            "https://platform-backup.example/hosts.txt";
    private static final String BLOCKED_HOST = "platform-backup-blocked.example";
    private static final String ALLOWED_HOST = "platform-backup-allowed.example";
    private static final String REDIRECTED_HOST = "platform-backup-redirected.example";
    private static final String REDIRECTED_IP = "8.8.4.4";
    private static final String SET = "TravelRestore";
    private static final String DISABLED_URL = "https://platform-backup.example/off.txt";

    private Context context;
    private SharedPreferences preferences;
    private HostListItemDao hostListItemDao;
    private HostsSourceDao hostsSourceDao;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        AppDatabase database = AppDatabase.getInstance(this.context);
        this.hostListItemDao = database.hostsListItemDao();
        this.hostsSourceDao = database.hostsSourceDao();
    }

    @Test(timeout = 60_000)
    public void seedPlatformBackupState() {
        assumeTrue("Only run from the platform backup smoke seed phase.",
                PHASE_SEED.equals(phase()));

        clearProbeState();

        assertTrue("Failed to seed backup preference.",
                this.preferences.edit().putString(PREF_KEY, PREF_VALUE).commit());
        seedRoundTripData();
        FilterSetStore.saveSet(context, SET, Collections.singleton(BACKUP_SOURCE_URL));
        FilterSetStore.setActiveProfile(context, SET);
        FilterSetStore.setGlobalEnabled(context, false);
        FilterSetStore.setSchedule(context, SET, setCadence(), 4, 17, 23);
        assertTrue(context.getSharedPreferences("source_schedules", Context.MODE_PRIVATE)
                .edit().putInt("schedule_url_" + BACKUP_SOURCE_URL, urlCadence())
                .putInt("hour_url_" + BACKUP_SOURCE_URL, 11).commit());

        assertTrue("Seeded preference should be readable before platform backup.",
                PREF_VALUE.equals(this.preferences.getString(PREF_KEY, "")));
        assertTrue("Expected seeded platform backup source and rules.", hasSeededRules());
    }

    @Test(timeout = 60_000)
    public void restoredPlatformBackupStateIsPresent() {
        assumeTrue("Only run from the platform backup smoke assert phase.",
                PHASE_ASSERT.equals(phase()));

        waitForCondition("platform backup preference and rules to restore", () ->
                PREF_VALUE.equals(this.preferences.getString(PREF_KEY, "")) &&
                        hasRestoredRules()
        );

        assertEquals(Collections.singleton(BACKUP_SOURCE_URL), FilterSetStore.getSetUrls(context, SET));
        assertEquals(SET, FilterSetStore.getActiveProfile(context));
        assertFalse(FilterSetStore.isGlobalScheduleEnabled(context));
        assertEquals(setCadence(), FilterSetStore.getSchedule(context, SET));
        assertEquals(4, FilterSetStore.getScheduleWeekdayIso(context, SET));
        assertEquals(17, FilterSetStore.getScheduleHour(context, SET));
        assertEquals(23, FilterSetStore.getScheduleMinute(context, SET));
        SharedPreferences schedules = context.getSharedPreferences("source_schedules", Context.MODE_PRIVATE);
        assertEquals(urlCadence(), schedules.getInt("schedule_url_" + BACKUP_SOURCE_URL, -1));
        assertEquals(11, schedules.getInt("hour_url_" + BACKUP_SOURCE_URL, -1));
        assertEquals(HostsSource.USER_SOURCE_URL, hostsSourceDao.getById(USER_SOURCE_ID).get().getUrl());
        assertTrue(hostsSourceDao.getAll().stream().anyMatch(s ->
                DISABLED_URL.equals(s.getUrl()) && !s.isEnabled()));
        waitForCondition("restored schedules to reconcile with Android", () -> {
            try {
                long active = WorkManager.getInstance(context)
                        .getWorkInfosForUniqueWork(FilterSetUpdateService.WORK_NAME)
                        .get(5, TimeUnit.SECONDS).stream()
                        .filter(work -> !work.getState().isFinished()).count();
                return active == (allSchedulesOff() ? 0 : 1);
            } catch (Exception exception) {
                throw new AssertionError("Could not read scheduled work", exception);
            }
        });

        clearProbeState();
        assertFalse("Probe preference should be cleaned after assertion.",
                this.preferences.contains(PREF_KEY));
    }

    private static String phase() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        return arguments.getString(PHASE_ARG, "");
    }

    private static boolean allSchedulesOff() {
        return "off".equals(InstrumentationRegistry.getArguments().getString("backupSchedules"));
    }

    private static int setCadence() {
        return allSchedulesOff() ? FilterSetStore.SCHEDULE_OFF : FilterSetStore.SCHEDULE_WEEKLY;
    }

    private static int urlCadence() {
        return allSchedulesOff() ? FilterSetStore.SCHEDULE_OFF : FilterSetStore.SCHEDULE_DAILY;
    }

    private void seedRoundTripData() {
        HostsSource source = new HostsSource();
        source.setLabel(BACKUP_SOURCE_LABEL);
        source.setUrl(BACKUP_SOURCE_URL);
        source.setEnabled(true);
        source.setAllowEnabled(true);
        source.setRedirectEnabled(true);
        this.hostsSourceDao.insert(source);
        HostsSource disabled = new HostsSource();
        disabled.setLabel("0 Disabled First");
        disabled.setUrl(DISABLED_URL);
        disabled.setEnabled(false);
        this.hostsSourceDao.insert(disabled);

        insertUserRule(BLOCKED_HOST, ListType.BLOCKED, RuleKind.SUFFIX, true, null);
        insertUserRule(ALLOWED_HOST, ListType.ALLOWED, RuleKind.EXACT, true, null);
        insertUserRule(REDIRECTED_HOST, ListType.REDIRECTED, RuleKind.EXACT, true,
                REDIRECTED_IP);
    }

    private void clearProbeState() {
        this.preferences.edit().remove(PREF_KEY).commit();
        this.hostListItemDao.deleteUserFromHost(BLOCKED_HOST);
        this.hostListItemDao.deleteUserFromHost(ALLOWED_HOST);
        this.hostListItemDao.deleteUserFromHost(REDIRECTED_HOST);
        Optional<HostsSource> source = this.hostsSourceDao.getByUrl(BACKUP_SOURCE_URL);
        source.ifPresent(this.hostsSourceDao::delete);
        this.hostsSourceDao.getByUrl(DISABLED_URL).ifPresent(this.hostsSourceDao::delete);
        FilterSetStore.deleteSet(context, SET);
        context.getSharedPreferences("source_schedules", Context.MODE_PRIVATE).edit()
                .remove("schedule_url_" + BACKUP_SOURCE_URL).remove("hour_url_" + BACKUP_SOURCE_URL).apply();
    }

    private void insertUserRule(
            String host,
            ListType type,
            RuleKind kind,
            boolean enabled,
            String redirection) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(type);
        item.setKind(kind);
        item.setEnabled(enabled);
        item.setRedirection(redirection);
        item.setSourceId(USER_SOURCE_ID);
        this.hostListItemDao.insert(item);
    }

    private boolean hasSeededRules() {
        Optional<HostsSource> restoredSource = this.hostsSourceDao.getByUrl(BACKUP_SOURCE_URL);
        return restoredSource.isPresent()
                && BACKUP_SOURCE_LABEL.equals(restoredSource.get().getLabel())
                && restoredSource.get().isRedirectEnabled()
                && hasUserRule(BLOCKED_HOST, ListType.BLOCKED, RuleKind.SUFFIX, true, null)
                && hasUserRule(ALLOWED_HOST, ListType.ALLOWED, RuleKind.EXACT, true, null)
                && hasUserRule(REDIRECTED_HOST, ListType.REDIRECTED, RuleKind.EXACT, true,
                REDIRECTED_IP);
    }

    private boolean hasRestoredRules() {
        Optional<HostsSource> restoredSource = this.hostsSourceDao.getByUrl(BACKUP_SOURCE_URL);
        return restoredSource.isPresent()
                && BACKUP_SOURCE_LABEL.equals(restoredSource.get().getLabel())
                && !restoredSource.get().isRedirectEnabled()
                && hasUserRule(BLOCKED_HOST, ListType.BLOCKED, RuleKind.SUFFIX, true, null)
                && hasUserRule(ALLOWED_HOST, ListType.ALLOWED, RuleKind.EXACT, true, null)
                && hasUserRule(REDIRECTED_HOST, ListType.REDIRECTED, RuleKind.EXACT, true,
                REDIRECTED_IP);
    }

    private boolean hasUserRule(
            String host,
            ListType type,
            RuleKind kind,
            boolean enabled,
            String redirection) {
        for (HostListItem item : this.hostListItemDao.getUserList()) {
            if (host.equals(item.getHost())
                    && item.getType() == type
                    && item.getKind() == kind
                    && item.isEnabled() == enabled
                    && Objects.equals(redirection, item.getRedirection())) {
                return true;
            }
        }
        return false;
    }

    private static void waitForCondition(String description, Condition condition) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.isSatisfied()) {
                return;
            }
            SystemClock.sleep(100);
        }
        assertTrue("Timed out waiting for " + description, condition.isSatisfied());
    }

    private interface Condition {
        boolean isSatisfied();
    }
}
