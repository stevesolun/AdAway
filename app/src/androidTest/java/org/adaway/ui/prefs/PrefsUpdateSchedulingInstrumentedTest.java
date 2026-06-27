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

import org.adaway.R;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.testing.InstrumentedTestState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class PrefsUpdateSchedulingInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int TIMEOUT_SECONDS = 10;

    private Context context;
    private WorkManager workManager;
    private String hostsUpdateWorkName;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up update scheduling");
        this.workManager = WorkManager.getInstance(this.context);
        this.hostsUpdateWorkName = getHostsUpdateWorkName();
        resetWorkManager();
    }

    @After
    public void tearDown() throws Exception {
        finishResumedActivities();
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
        ActivityScenario.launch(PrefsActivity.class);

        clickAccessibilityText(this.context.getString(R.string.pref_update_configuration), 1);
        assertAccessibilityText(this.context.getString(R.string.pref_update_hosts_category));

        clickAccessibilityText(this.context.getString(R.string.pref_update_check_hosts_daily), 2);
        assertSingleActiveWork(NetworkType.CONNECTED);

        clickAccessibilityText(this.context.getString(R.string.pref_update_sync_unmetered_only), 1);
        assertSingleActiveWork(NetworkType.UNMETERED);

        clickAccessibilityText(this.context.getString(R.string.pref_update_check_hosts_daily), 2);
        assertNoActiveWork();
    }

    private void resetWorkManager() throws Exception {
        this.workManager.cancelAllWork().getResult().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        this.workManager.pruneWork().getResult().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void assertSingleActiveWork(@NonNull NetworkType expectedNetworkType)
            throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            List<WorkInfo> active = activeWork();
            if (active.size() == 1
                    && active.get(0).getConstraints().getRequiredNetworkType()
                    == expectedNetworkType) {
                assertTrue(active.get(0).getConstraints().requiresStorageNotLow());
                return;
            }
            SystemClock.sleep(100L);
        }
        List<WorkInfo> active = activeWork();
        assertEquals(1, active.size());
        assertEquals(expectedNetworkType,
                active.get(0).getConstraints().getRequiredNetworkType());
    }

    private void assertNoActiveWork() throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (activeWork().isEmpty()) {
                return;
            }
            SystemClock.sleep(100L);
        }
        assertEquals(0, activeWork().size());
    }

    private List<WorkInfo> activeWork() throws Exception {
        return this.workManager.getWorkInfosForUniqueWork(this.hostsUpdateWorkName)
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

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
    }
}
