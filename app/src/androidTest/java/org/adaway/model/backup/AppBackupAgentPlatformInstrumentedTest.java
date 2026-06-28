package org.adaway.model.backup;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
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

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.Optional;

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

        clearProbeState();
        assertFalse("Probe preference should be cleaned after assertion.",
                this.preferences.contains(PREF_KEY));
    }

    private static String phase() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        return arguments.getString(PHASE_ARG, "");
    }

    private void seedRoundTripData() {
        HostsSource source = new HostsSource();
        source.setLabel(BACKUP_SOURCE_LABEL);
        source.setUrl(BACKUP_SOURCE_URL);
        source.setEnabled(true);
        source.setAllowEnabled(true);
        source.setRedirectEnabled(true);
        this.hostsSourceDao.insert(source);

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
