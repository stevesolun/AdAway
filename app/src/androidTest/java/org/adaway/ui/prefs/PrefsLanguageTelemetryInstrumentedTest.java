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

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.util.Constants;
import org.adaway.util.log.SentryLog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PrefsLanguageTelemetryInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;

    private Application application;
    private Context context;
    private SharedPreferences prefs;
    private String forceEnglishKey;

    @Before
    public void setUp() {
        this.application = ApplicationProvider.getApplicationContext();
        this.context = this.application;
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up language telemetry");
        this.prefs = this.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        this.forceEnglishKey = this.context.getString(R.string.pref_force_english_key);
        setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        assertFalse(getForceEnglish());
        assertFalse(PreferenceHelper.getTelemetryEnabled(this.context));
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down language telemetry");
        }
    }

    @Test(timeout = 90_000)
    public void languageAndTelemetryPreferencesPersistAndApplyTheirSideEffects()
            throws Exception {
        try (ActivityScenario<PrefsActivity> ignored = ActivityScenario.launch(PrefsActivity.class)) {
            assertPrefsActivityResumed();
            assertAccessibilityTextExact(this.context.getString(R.string.pref_force_english));

            clickAccessibilityTextExact(this.context.getString(R.string.pref_force_english));
            waitForForceEnglish(true);
            assertEquals("en", AppCompatDelegate.getApplicationLocales().toLanguageTags());

            clickAccessibilityTextExact(this.context.getString(R.string.pref_force_english));
            waitForForceEnglish(false);
            assertEquals("", AppCompatDelegate.getApplicationLocales().toLanguageTags());

            scrollPreferencesToBottom(ignored);
            assertAccessibilityTextExact(this.context.getString(R.string.pref_enable_telemetry));
            if (!SentryLog.isSupported(this.application)) {
                assertAccessibilityTextExact(
                        this.context.getString(R.string.pref_enable_telemetry_disabled_summary));
                assertFalse(PreferenceHelper.getTelemetryEnabled(this.context));
            } else {
                clickAccessibilityTextExact(this.context.getString(R.string.pref_enable_telemetry));
                waitForTelemetryEnabled(true);

                clickAccessibilityTextExact(this.context.getString(R.string.pref_enable_telemetry));
                waitForTelemetryEnabled(false);
            }
        }
    }

    private boolean getForceEnglish() {
        return this.prefs.getBoolean(this.forceEnglishKey, false);
    }

    private void waitForForceEnglish(boolean expected) throws Exception {
        waitForCondition("Force English did not become " + expected,
                () -> getForceEnglish() == expected && localesMatch(expected));
    }

    private void waitForTelemetryEnabled(boolean expected) throws Exception {
        waitForCondition("Telemetry preference did not become " + expected,
                () -> PreferenceHelper.getTelemetryEnabled(this.context) == expected);
    }

    private static boolean localesMatch(boolean forceEnglish) {
        String languageTags = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        return forceEnglish ? "en".equals(languageTags) : languageTags.isEmpty();
    }

    private interface Condition {
        boolean isSatisfied() throws Exception;
    }

    private static void waitForCondition(
            @NonNull String failureMessage,
            @NonNull Condition condition) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.isSatisfied() && isPrefsActivityResumed()) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError(failureMessage);
    }

    private static void setApplicationLocales(@NonNull LocaleListCompat locales) {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(() -> AppCompatDelegate.setApplicationLocales(locales));
    }

    private static void assertAccessibilityTextExact(String expectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                if (root != null && containsExactText(root, expectedText)) {
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

    private static void clickAccessibilityTextExact(String expectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                List<AccessibilityNodeInfo> matches = new ArrayList<>();
                if (root != null) {
                    collectExactText(root, expectedText, matches);
                }
                try {
                    for (AccessibilityNodeInfo match : matches) {
                        if (clickNodeOrParent(match)) {
                            return;
                        }
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
        throw new AssertionError("Text was not clickable: " + expectedText);
    }

    private static void scrollPreferencesToBottom(
            @NonNull ActivityScenario<PrefsActivity> scenario) {
        scenario.onActivity(activity -> {
            for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
                if (fragment instanceof PrefsMainFragment) {
                    RecyclerView listView = ((PrefsMainFragment) fragment).getListView();
                    RecyclerView.Adapter<?> adapter = listView.getAdapter();
                    if (adapter != null && adapter.getItemCount() > 0) {
                        listView.scrollToPosition(adapter.getItemCount() - 1);
                    }
                }
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static boolean containsExactText(
            @NonNull AccessibilityNodeInfo node,
            @NonNull String expectedText) {
        if (nodeTextMatches(node, expectedText)) {
            return true;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                if (containsExactText(child, expectedText)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private static void collectExactText(
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
                collectExactText(child, expectedText, matches);
            } finally {
                child.recycle();
            }
        }
    }

    private static boolean nodeTextMatches(
            @NonNull AccessibilityNodeInfo node,
            @NonNull String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && expectedText.contentEquals(text))
                || (description != null && expectedText.contentEquals(description));
    }

    private static boolean clickNodeOrParent(@NonNull AccessibilityNodeInfo node) {
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

    private static void assertPrefsActivityResumed() throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (isPrefsActivityResumed()) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("PrefsActivity was not resumed.");
    }

    private static boolean isPrefsActivityResumed() {
        boolean[] resumed = new boolean[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                if (activity instanceof PrefsActivity && !activity.isFinishing()) {
                    resumed[0] = true;
                    return;
                }
            }
        });
        return resumed[0];
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
