package org.adaway.ui.prefs;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;

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
public class PrefsAdBlockMethodSwitchInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;

    private Context context;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up method switch");
        PreferenceHelper.setAbBlockMethod(this.context, ROOT);
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down method switch");
        }
    }

    @Test(timeout = 60_000)
    public void rootActiveRootRowOpensRootSettings()
            throws Exception {
        PreferenceHelper.setAbBlockMethod(this.context, ROOT);
        ActivityScenario.launch(PrefsActivity.class);

        clickAccessibilityText(this.context.getString(R.string.pref_root_ad_blocker_configuration));

        assertAccessibilityText(this.context.getString(R.string.pref_root_open_hosts));
        assertAccessibilityTextAbsent(this.context.getString(R.string.onboarding_title));
        assertEquals(ROOT, PreferenceHelper.getAdBlockMethod(this.context));
    }

    @Test(timeout = 60_000)
    public void rootActiveVpnRowLaunchesOnboardingWithoutChangingMethod()
            throws Exception {
        PreferenceHelper.setAbBlockMethod(this.context, ROOT);
        ActivityScenario.launch(PrefsActivity.class);

        assertAccessibilityText(this.context.getString(R.string.pref_root_ad_blocker_configuration));
        assertAccessibilityText(this.context.getString(R.string.pref_ad_block_method_active));
        assertAccessibilityText(this.context.getString(R.string.pref_vpn_ad_blocker_configuration));
        assertAccessibilityText(this.context.getString(R.string.pref_ad_block_method_switch));

        clickAccessibilityText(this.context.getString(R.string.pref_vpn_ad_blocker_configuration));

        assertAccessibilityText(this.context.getString(R.string.onboarding_title));
        assertAccessibilityText(this.context.getString(R.string.onboarding_vpn_title));
        assertAccessibilityText(this.context.getString(R.string.onboarding_start_button));
        assertAccessibilityTextAbsent(this.context.getString(R.string.pref_vpn_allow_app_bypass));
        assertEquals(ROOT, PreferenceHelper.getAdBlockMethod(this.context));
    }

    @Test(timeout = 60_000)
    public void vpnActiveVpnRowOpensVpnSettings()
            throws Exception {
        PreferenceHelper.setAbBlockMethod(this.context, VPN);
        ActivityScenario.launch(PrefsActivity.class);

        clickAccessibilityText(this.context.getString(R.string.pref_vpn_ad_blocker_configuration));

        assertAccessibilityText(this.context.getString(R.string.pref_vpn_allow_app_bypass));
        assertAccessibilityTextAbsent(this.context.getString(R.string.onboarding_title));
        assertEquals(VPN, PreferenceHelper.getAdBlockMethod(this.context));
    }

    @Test(timeout = 60_000)
    public void vpnActiveRootRowLaunchesOnboardingWithoutChangingMethod()
            throws Exception {
        PreferenceHelper.setAbBlockMethod(this.context, VPN);
        ActivityScenario.launch(PrefsActivity.class);

        assertAccessibilityText(this.context.getString(R.string.pref_vpn_ad_blocker_configuration));
        assertAccessibilityText(this.context.getString(R.string.pref_ad_block_method_active));
        assertAccessibilityText(this.context.getString(R.string.pref_root_ad_blocker_configuration));
        assertAccessibilityText(this.context.getString(R.string.pref_ad_block_method_switch));

        clickAccessibilityText(this.context.getString(R.string.pref_root_ad_blocker_configuration));

        assertAccessibilityText(this.context.getString(R.string.onboarding_title));
        assertAccessibilityText(this.context.getString(R.string.onboarding_root_title));
        assertAccessibilityText(this.context.getString(R.string.onboarding_start_button));
        assertAccessibilityTextAbsent(this.context.getString(R.string.pref_root_open_hosts));
        assertEquals(VPN, PreferenceHelper.getAdBlockMethod(this.context));
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

    private static void assertAccessibilityTextAbsent(String unexpectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                if (root == null || !containsText(root, unexpectedText)) {
                    return;
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text was still visible: " + unexpectedText);
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
