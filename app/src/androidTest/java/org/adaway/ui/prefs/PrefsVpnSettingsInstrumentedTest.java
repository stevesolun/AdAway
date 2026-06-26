package org.adaway.ui.prefs;

import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

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
public class PrefsVpnSettingsInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;

    private Context context;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up VPN settings");
        PreferenceHelper.setAbBlockMethod(this.context, VPN);
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down VPN settings");
        }
    }

    @Test(timeout = 60_000)
    public void vpnBypassAndSystemExclusionSettingsPersistExplicitLeakChoices()
            throws Exception {
        ActivityScenario.launch(PrefsActivity.class);

        clickAccessibilityText(this.context.getString(R.string.pref_vpn_ad_blocker_configuration));
        assertAccessibilityText(this.context.getString(R.string.pref_vpn_allow_app_bypass));
        assertAccessibilityText(this.context.getString(
                R.string.pref_vpn_allow_app_bypass_description));
        assertFalse(PreferenceHelper.getVpnAllowAppBypass(this.context));

        clickAccessibilityText(this.context.getString(R.string.pref_vpn_allow_app_bypass));
        waitForVpnBypass(true);

        assertAccessibilityText(this.context.getString(R.string.pref_vpn_exclude_system_apps));
        assertEquals("none", PreferenceHelper.getVpnExcludedSystemApps(this.context));
        clickAccessibilityText(this.context.getString(R.string.pref_vpn_exclude_system_apps));
        clickAccessibilityText("All");
        waitForSystemExclusion("all");
    }

    private void waitForVpnBypass(boolean expected) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (PreferenceHelper.getVpnAllowAppBypass(this.context) == expected) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("VPN app bypass did not become " + expected);
    }

    private void waitForSystemExclusion(String expected) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (expected.equals(PreferenceHelper.getVpnExcludedSystemApps(this.context))) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("System app exclusion did not become " + expected);
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
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text was not visible: " + expectedText);
    }

    private static void clickAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                AccessibilityNodeInfo node = root == null ? null : findText(root, expectedText);
                if (node != null) {
                    try {
                        if (clickNodeOrParent(node)) {
                            return;
                        }
                    } finally {
                        node.recycle();
                    }
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text was not clickable: " + expectedText);
    }

    private static boolean containsText(AccessibilityNodeInfo node, String expectedText) {
        if (nodeTextContains(node, expectedText)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
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
            AccessibilityNodeInfo node,
            String expectedText) {
        if (nodeTextMatches(node, expectedText)) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
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
