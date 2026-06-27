package org.adaway.ui.prefs;

import static android.app.Activity.RESULT_CANCELED;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.testing.InstrumentedTestState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PrefsRootWebServerUnavailableInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;

    private Context context;
    private Instrumentation instrumentation;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.instrumentation = InstrumentationRegistry.getInstrumentation();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up root webserver prefs");
        PreferenceHelper.setAbBlockMethod(this.context, ROOT);
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(
                    this.context,
                    "tear down root webserver prefs");
        }
    }

    @Test(timeout = 60_000)
    public void rootWebServerControlsStayDisabledWhenNativeExecutableUnavailable()
            throws Exception {
        Instrumentation.ActivityMonitor monitor = addHttpsViewMonitor();
        try (ActivityScenario<PrefsActivity> ignored = ActivityScenario.launch(PrefsActivity.class)) {
            clickAccessibilityText(
                    this.context.getString(R.string.pref_root_ad_blocker_configuration));
            waitForFragment(PrefsRootFragment.class, "root settings");

            assertAccessibilityText(this.context.getString(R.string.pref_webserver));
            assertAccessibilityText(this.context.getString(R.string.pref_webserver_enabled));
            assertAccessibilityText(this.context.getString(R.string.pref_webserver_test));
            assertAccessibilityText(this.context.getString(R.string.pref_webserver_state_unavailable));
            assertRootWebServerPreferencesDisabled();

            clickAccessibilityTextIfPossible(this.context.getString(R.string.pref_webserver_test));
            SystemClock.sleep(500L);
            assertEquals("Disabled webserver test row must not launch https://localhost.",
                    0, monitor.getHits());
        } finally {
            this.instrumentation.removeMonitor(monitor);
        }
    }

    private Instrumentation.ActivityMonitor addHttpsViewMonitor() throws Exception {
        IntentFilter filter = new IntentFilter(Intent.ACTION_VIEW);
        filter.addDataScheme("https");
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                filter,
                new Instrumentation.ActivityResult(RESULT_CANCELED, null),
                true);
        this.instrumentation.addMonitor(monitor);
        return monitor;
    }

    private static void assertRootWebServerPreferencesDisabled() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            PrefsRootFragment fragment = currentFragment(PrefsRootFragment.class);
            SwitchPreferenceCompat enabled = requireSwitch(
                    fragment,
                    R.string.pref_webserver_enabled_key);
            Preference test = requirePreference(fragment, R.string.pref_webserver_test_key);
            SwitchPreferenceCompat icon = requireSwitch(fragment, R.string.pref_webserver_icon_key);

            assertFalse("Webserver switch must be disabled when the native executable is absent.",
                    enabled.isEnabled());
            assertFalse("Webserver switch must be forced off when unavailable.",
                    enabled.isChecked());
            assertEquals(fragment.getString(R.string.pref_webserver_unavailable),
                    String.valueOf(enabled.getSummary()));
            assertFalse("Webserver test row must be disabled when unavailable.", test.isEnabled());
            assertEquals(fragment.getString(R.string.pref_webserver_state_unavailable),
                    String.valueOf(test.getSummary()));
            assertFalse("Webserver icon toggle must be disabled when unavailable.", icon.isEnabled());
            assertFalse("Webserver icon toggle must be forced off when unavailable.",
                    icon.isChecked());
        });
    }

    @NonNull
    private static Preference requirePreference(
            @NonNull PrefsRootFragment fragment,
            int keyResId) {
        Preference preference = fragment.findPreference(fragment.getString(keyResId));
        assertNotNull("Preference must exist: " + fragment.getString(keyResId), preference);
        return preference;
    }

    @NonNull
    private static SwitchPreferenceCompat requireSwitch(
            @NonNull PrefsRootFragment fragment,
            int keyResId) {
        Preference preference = requirePreference(fragment, keyResId);
        if (!(preference instanceof SwitchPreferenceCompat)) {
            throw new AssertionError("Preference must be a switch: " + fragment.getString(keyResId));
        }
        return (SwitchPreferenceCompat) preference;
    }

    @NonNull
    private static <T extends Fragment> T currentFragment(@NonNull Class<T> fragmentClass) {
        Activity activity = currentResumedActivity(PrefsActivity.class);
        Fragment fragment = ((PrefsActivity) activity).getSupportFragmentManager()
                .findFragmentById(android.R.id.content);
        if (!fragmentClass.isInstance(fragment)) {
            throw new AssertionError("Expected visible fragment: " + fragmentClass.getName());
        }
        return fragmentClass.cast(fragment);
    }

    private static <T extends Fragment> void waitForFragment(
            @NonNull Class<T> fragmentClass,
            @NonNull String description) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] ready = new boolean[1];
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                Activity activity = currentResumedActivity(PrefsActivity.class);
                ((PrefsActivity) activity).getSupportFragmentManager().executePendingTransactions();
                Fragment fragment = ((PrefsActivity) activity).getSupportFragmentManager()
                        .findFragmentById(android.R.id.content);
                ready[0] = fragmentClass.isInstance(fragment)
                        && fragment.isAdded()
                        && fragment.getView() != null
                        && fragment.getView().isShown();
            });
            if (ready[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError(description + " preferences fragment did not become visible.");
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

    private static void clickAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (clickAccessibilityTextIfPossible(expectedText)) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Text was not clickable: " + expectedText);
    }

    private static boolean clickAccessibilityTextIfPossible(String expectedText) {
        AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .getRootInActiveWindow();
        try {
            AccessibilityNodeInfo node = root == null ? null : findText(root, expectedText);
            if (node == null) {
                return false;
            }
            try {
                return clickNodeOrParent(node);
            } finally {
                node.recycle();
            }
        } finally {
            if (root != null) {
                root.recycle();
            }
        }
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

    private static AccessibilityNodeInfo findText(
            @NonNull AccessibilityNodeInfo node,
            @NonNull String expectedText) {
        if (nodeTextMatches(node, expectedText)) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                AccessibilityNodeInfo result = findText(child, expectedText);
                if (result != null) {
                    return result;
                }
            } finally {
                child.recycle();
            }
        }
        return null;
    }

    private static boolean nodeTextMatches(
            @NonNull AccessibilityNodeInfo node,
            @NonNull String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && expectedText.contentEquals(text))
                || (description != null && expectedText.contentEquals(description));
    }

    private static boolean nodeTextContains(
            @NonNull AccessibilityNodeInfo node,
            @NonNull String expectedText) {
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

    @NonNull
    private static Activity currentResumedActivity(@NonNull Class<? extends Activity> activityClass) {
        for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)) {
            if (activityClass.isInstance(activity)) {
                return activity;
            }
        }
        throw new AssertionError("No resumed activity found: " + activityClass.getName());
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
