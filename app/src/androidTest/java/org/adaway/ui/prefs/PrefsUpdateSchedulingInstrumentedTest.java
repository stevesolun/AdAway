/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.model.update.ApkUpdateService;
import org.adaway.model.update.UpdateModel;
import org.adaway.model.update.UpdateStore;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class PrefsUpdateSchedulingInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int TIMEOUT_SECONDS = 10;

    private Context context;
    private WorkManager workManager;
    private String hostsUpdateWorkName;
    private String apkUpdateWorkName;
    private UpdateModel originalUpdateModel;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up update scheduling");
        this.workManager = WorkManager.getInstance(this.context);
        this.hostsUpdateWorkName = getHostsUpdateWorkName();
        this.apkUpdateWorkName = getApkUpdateWorkName();
        resetWorkManager();
    }

    @After
    public void tearDown() throws Exception {
        finishResumedActivities();
        restoreUpdateModel();
        if (this.context != null) {
            resetWorkManager();
            InstrumentedTestState.resetForPassiveRootUi(
                    this.context,
                    "tear down update scheduling");
        }
    }

    @Test(timeout = 60_000)
    public void hostsUpdatePreferenceTogglesPeriodicWorkAndUnmeteredConstraint()
            throws Exception {
        try (ActivityScenario<PrefsActivity> ignored =
                     ActivityScenario.launch(PrefsActivity.class)) {
            openUpdatePreferences();

            clickAccessibilityText(
                    this.context.getString(R.string.pref_update_check_hosts_daily),
                    2);
            assertSingleActiveWork(this.hostsUpdateWorkName, NetworkType.CONNECTED);

            clickAccessibilityText(
                    this.context.getString(R.string.pref_update_sync_unmetered_only),
                    1);
            assertSingleActiveWork(this.hostsUpdateWorkName, NetworkType.UNMETERED);

            clickAccessibilityText(
                    this.context.getString(R.string.pref_update_check_hosts_daily),
                    2);
            assertNoActiveWork(this.hostsUpdateWorkName);
        }
    }

    @Test(timeout = 60_000)
    public void appUpdatePreferencesToggleStartupDailyWorkAndChannel()
            throws Exception {
        try (ActivityScenario<PrefsActivity> ignored =
                     ActivityScenario.launch(PrefsActivity.class)) {
            openUpdatePreferences();

            assertFalse(PreferenceHelper.getUpdateCheckAppStartup(this.context));
            clickAccessibilityText(
                    this.context.getString(R.string.pref_update_check_app_startup),
                    1);
            assertTrue(PreferenceHelper.getUpdateCheckAppStartup(this.context));

            assertFalse(PreferenceHelper.getUpdateCheckAppDaily(this.context));
            clickAccessibilityText(this.context.getString(R.string.pref_update_check_app_daily), 1);
            assertTrue(PreferenceHelper.getUpdateCheckAppDaily(this.context));
            assertSingleActiveApkWork();

            clickAccessibilityText(this.context.getString(R.string.pref_update_check_app_daily), 1);
            assertFalse(PreferenceHelper.getUpdateCheckAppDaily(this.context));
            assertNoActiveWork(this.apkUpdateWorkName);

            assertChannelPreferenceMatchesStore();
        }
    }

    @Test(timeout = 90_000)
    public void startupPreferenceFromUiControlsFreshHomeAppUpdateCheck()
            throws Exception {
        CountingUpdateModel updateModel = installCountingUpdateModel();

        setStartupPreferenceThroughUi(true);
        try (ActivityScenario<HomeActivity> ignored = ActivityScenario.launch(HomeActivity.class)) {
            assertTrue("Fresh Home launch should check for app updates when startup check is on.",
                    updateModel.waitForCheckCountAtLeast(1, TIMEOUT_MS));
        }

        setStartupPreferenceThroughUi(false);
        try (ActivityScenario<HomeActivity> ignored = ActivityScenario.launch(HomeActivity.class)) {
            assertFalse(
                    "Fresh Home launch must not check for app updates when startup check is off.",
                    updateModel.waitForCheckCountAtLeast(2, 1_500));
        }
    }

    private void openUpdatePreferences() throws Exception {
        clickAccessibilityText(this.context.getString(R.string.pref_update_configuration), 1);
        assertAccessibilityText(this.context.getString(R.string.pref_update_app_category));
        assertAccessibilityText(this.context.getString(R.string.pref_update_hosts_category));
    }

    private void setStartupPreferenceThroughUi(boolean enabled) throws Exception {
        try (ActivityScenario<PrefsActivity> ignored =
                     ActivityScenario.launch(PrefsActivity.class)) {
            openUpdatePreferences();
            if (PreferenceHelper.getUpdateCheckAppStartup(this.context) != enabled) {
                clickAccessibilityText(
                        this.context.getString(R.string.pref_update_check_app_startup),
                        1);
            }
            assertEquals(enabled, PreferenceHelper.getUpdateCheckAppStartup(this.context));
        }
    }

    private void resetWorkManager() throws Exception {
        this.workManager.cancelAllWork().getResult().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        this.workManager.pruneWork().getResult().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void assertSingleActiveWork(
            @NonNull String workName,
            @NonNull NetworkType expectedNetworkType)
            throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            List<WorkInfo> active = activeWork(workName);
            if (active.size() == 1
                    && active.get(0).getConstraints().getRequiredNetworkType()
                    == expectedNetworkType) {
                assertTrue(active.get(0).getConstraints().requiresStorageNotLow());
                return;
            }
            SystemClock.sleep(100L);
        }
        List<WorkInfo> active = activeWork(workName);
        assertEquals(1, active.size());
        assertEquals(expectedNetworkType,
                active.get(0).getConstraints().getRequiredNetworkType());
    }

    private void assertSingleActiveApkWork() throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            List<WorkInfo> active = activeWork(this.apkUpdateWorkName);
            if (active.size() == 1
                    && active.get(0).getPeriodicityInfo() != null
                    && active.get(0).getPeriodicityInfo().getRepeatIntervalMillis()
                    == TimeUnit.DAYS.toMillis(1)) {
                return;
            }
            SystemClock.sleep(100L);
        }
        List<WorkInfo> active = activeWork(this.apkUpdateWorkName);
        assertEquals(1, active.size());
        assertEquals(TimeUnit.DAYS.toMillis(1),
                active.get(0).getPeriodicityInfo().getRepeatIntervalMillis());
    }

    private void assertNoActiveWork(@NonNull String workName) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (activeWork(workName).isEmpty()) {
                return;
            }
            SystemClock.sleep(100L);
        }
        assertEquals(0, activeWork(workName).size());
    }

    private List<WorkInfo> activeWork(@NonNull String workName) throws Exception {
        return this.workManager.getWorkInfosForUniqueWork(workName)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .stream()
                .filter(info -> !info.getState().isFinished())
                .collect(Collectors.toList());
    }

    private static String getHostsUpdateWorkName() throws Exception {
        Field field = SourceUpdateService.class.getDeclaredField("WORK_NAME");
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static String getApkUpdateWorkName() throws Exception {
        Field field = ApkUpdateService.class.getDeclaredField("WORK_NAME");
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private void assertChannelPreferenceMatchesStore() throws Exception {
        AdAwayApplication application = (AdAwayApplication) this.context.getApplicationContext();
        boolean expectedEnabled = application.getUpdateModel().getStore() == UpdateStore.ADAWAY;
        assertEquals(expectedEnabled,
                isCurrentUpdatePreferenceEnabled(R.string.pref_update_include_beta_releases_key));
        assertEquals("stable", application.getUpdateModel().getChannel());

        if (expectedEnabled) {
            clickAccessibilityText(
                    this.context.getString(R.string.pref_update_include_beta_releases),
                    1);
            assertEquals("beta", application.getUpdateModel().getChannel());
        }
    }

    private CountingUpdateModel installCountingUpdateModel() throws Exception {
        CountingUpdateModel updateModel = new CountingUpdateModel(this.context);
        Field field = AdAwayApplication.class.getDeclaredField("updateModel");
        field.setAccessible(true);
        AdAwayApplication application = (AdAwayApplication) this.context.getApplicationContext();
        this.originalUpdateModel = (UpdateModel) field.get(application);
        field.set(application, updateModel);
        return updateModel;
    }

    private void restoreUpdateModel() throws Exception {
        if (this.originalUpdateModel == null || this.context == null) {
            return;
        }
        Field field = AdAwayApplication.class.getDeclaredField("updateModel");
        field.setAccessible(true);
        field.set(this.context.getApplicationContext(), this.originalUpdateModel);
        this.originalUpdateModel = null;
    }

    private static boolean isCurrentUpdatePreferenceEnabled(int keyResId) throws Exception {
        boolean[] enabled = new boolean[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Activity activity = currentResumedActivity(PrefsActivity.class);
            PrefsUpdateFragment fragment = (PrefsUpdateFragment) ((PrefsActivity) activity)
                    .getSupportFragmentManager()
                    .findFragmentById(android.R.id.content);
            if (fragment == null) {
                throw new AssertionError("Update preferences fragment is not visible.");
            }
            androidx.preference.Preference preference =
                    fragment.findPreference(activity.getString(keyResId));
            if (preference == null) {
                throw new AssertionError("Update preference was not found.");
            }
            enabled[0] = preference.isEnabled();
        });
        return enabled[0];
    }

    private static void assertAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                if (root != null && containsText(root, expectedText)) {
                    return;
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Text was not visible: " + expectedText);
    }

    private static void clickAccessibilityText(String expectedText, int occurrence)
            throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                List<AccessibilityNodeInfo> matches = new ArrayList<>();
                if (root != null) {
                    collectMatchingText(root, expectedText, matches);
                }
                try {
                    if (matches.size() >= occurrence
                            && clickNodeOrParent(matches.get(occurrence - 1))) {
                        return;
                    }
                } finally {
                    for (AccessibilityNodeInfo match : matches) {
                        match.recycle();
                    }
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Text occurrence was not clickable: " + expectedText
                + " #" + occurrence);
    }

    private static boolean containsText(AccessibilityNodeInfo node, String expectedText) {
        if (nodeTextContains(node, expectedText)) {
            return true;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                if (containsText(child, expectedText)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private static void collectMatchingText(
            @NonNull AccessibilityNodeInfo node,
            @NonNull String expectedText,
            @NonNull List<AccessibilityNodeInfo> matches) {
        if (nodeTextMatches(node, expectedText)) {
            matches.add(AccessibilityNodeInfo.obtain(node));
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                collectMatchingText(child, expectedText, matches);
            } finally {
                child.recycle();
            }
        }
    }

    private static boolean nodeTextMatches(AccessibilityNodeInfo node, String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && expectedText.contentEquals(text))
                || (description != null && expectedText.contentEquals(description));
    }

    private static boolean nodeTextContains(AccessibilityNodeInfo node, String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && text.toString().contains(expectedText))
                || (description != null && description.toString().contains(expectedText));
    }

    private static boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        try {
            while (current != null) {
                if (current.isClickable()
                        && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
                AccessibilityNodeInfo parent = current.getParent();
                current.recycle();
                current = parent;
            }
            return false;
        } finally {
            if (current != null) {
                current.recycle();
            }
        }
    }

    private static <T extends Activity> Activity currentResumedActivity(Class<T> activityClass) {
        for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)) {
            if (activityClass.isInstance(activity)) {
                return activity;
            }
        }
        throw new AssertionError("No resumed activity found for " + activityClass.getName());
    }

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
    }

    private static final class CountingUpdateModel extends UpdateModel {
        private final AtomicInteger checkCount = new AtomicInteger();

        private CountingUpdateModel(Context context) {
            super(context);
        }

        @Override
        public void checkForUpdate() {
            this.checkCount.incrementAndGet();
        }

        private boolean waitForCheckCountAtLeast(int expectedCount, int timeoutMs)
                throws InterruptedException {
            long deadline = SystemClock.uptimeMillis() + timeoutMs;
            while (SystemClock.uptimeMillis() < deadline) {
                if (this.checkCount.get() >= expectedCount) {
                    return true;
                }
                SystemClock.sleep(50L);
            }
            return this.checkCount.get() >= expectedCount;
        }
    }
}
