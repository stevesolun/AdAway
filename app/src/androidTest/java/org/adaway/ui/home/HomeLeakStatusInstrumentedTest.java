package org.adaway.ui.home;

import static android.view.View.VISIBLE;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class HomeLeakStatusInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final String PRIVATE_DNS_MODE = "private_dns_mode";
    private static final String PRIVATE_DNS_SPECIFIER = "private_dns_specifier";
    private static final String TEST_PRIVATE_DNS_PROVIDER = "dns.example";

    private Context context;
    private String originalPrivateDnsMode;
    private String originalPrivateDnsSpecifier;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.originalPrivateDnsMode = getGlobalSetting(PRIVATE_DNS_MODE);
        this.originalPrivateDnsSpecifier = getGlobalSetting(PRIVATE_DNS_SPECIFIER);
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up Home leak status");
    }

    @After
    public void tearDown() {
        restoreGlobalSetting(PRIVATE_DNS_MODE, this.originalPrivateDnsMode);
        restoreGlobalSetting(PRIVATE_DNS_SPECIFIER, this.originalPrivateDnsSpecifier);
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down Home leak status");
        }
    }

    @Test
    public void homeLeakCardRendersPrivateDnsAndVpnBypassRisksFromDeviceSettings() {
        setPrivateDnsProvider(TEST_PRIVATE_DNS_PROVIDER);
        PreferenceHelper.setAbBlockMethod(this.context, VPN);
        PreferenceHelper.setVpnExcludedApps(this.context, new HashSet<>(Arrays.asList(
                "example.first",
                "example.second")));
        SharedPreferences.Editor editor = this.context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE).edit();
        editor.putBoolean(this.context.getString(R.string.pref_vpn_allow_app_bypass_key), true);
        editor.putString(this.context.getString(R.string.pref_vpn_excluded_system_apps_key), "all");
        assertTrue("Failed to configure VPN leak preferences.", editor.commit());

        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            waitForText(scenario, R.id.leakStatusSummaryTextView, "4 risks need attention");
            waitForTextContaining(scenario, R.id.leakStatusDetailTextView,
                    "Protection method: VPN selected but not running");
            waitForTextContaining(scenario, R.id.leakStatusDetailTextView,
                    "Private DNS: " + TEST_PRIVATE_DNS_PROVIDER);
            waitForTextContaining(scenario, R.id.leakStatusDetailTextView,
                    "Browser DoH: can bypass DNS-level filtering");
            waitForTextContaining(scenario, R.id.leakStatusDetailTextView,
                    "VPN bypass: app-managed bypass allowed");
            waitForTextContaining(scenario, R.id.leakStatusDetailTextView,
                    "VPN bypass: 2 user apps excluded");
            waitForTextContaining(scenario, R.id.leakStatusDetailTextView,
                    "System apps: all excluded");
            waitForTextContaining(scenario, R.id.leakStatusDetailTextView,
                    "Strict mode: enable Always-on VPN and Block connections without VPN in "
                            + "Android settings.");
            waitForVisibility(scenario, R.id.leakStatusActions, VISIBLE);
            waitForVisibility(scenario, R.id.leakPrivateDnsButton, VISIBLE);
            waitForVisibility(scenario, R.id.leakVpnSettingsButton, VISIBLE);
        }
    }

    private void setPrivateDnsProvider(String provider) {
        executeShellCommand("settings put global " + PRIVATE_DNS_MODE + " hostname");
        executeShellCommand("settings put global " + PRIVATE_DNS_SPECIFIER + " " + provider);
        waitForGlobalSetting(PRIVATE_DNS_MODE, LeakStatus.PRIVATE_DNS_MODE_HOSTNAME);
        waitForGlobalSetting(PRIVATE_DNS_SPECIFIER, provider);
    }

    private static void restoreGlobalSetting(String key, String value) {
        if (value == null || "null".equals(value)) {
            executeShellCommand("settings delete global " + key);
        } else {
            executeShellCommand("settings put global " + key + " " + value);
        }
    }

    private static String getGlobalSetting(String key) {
        return Settings.Global.getString(
                ApplicationProvider.getApplicationContext().getContentResolver(),
                key);
    }

    private static void waitForGlobalSetting(String key, String expectedValue) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            String actual = getGlobalSetting(key);
            if (expectedValue.equals(actual)) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Global setting " + key + " did not become " + expectedValue);
    }

    private static void executeShellCommand(String command) {
        try (ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .executeShellCommand(command);
             FileInputStream output = new FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[128];
            while (output.read(buffer) != -1) {
                // Drain the stream so the shell command completes before the test continues.
            }
        } catch (IOException exception) {
            throw new AssertionError("Failed to execute shell command: " + command, exception);
        }
    }

    private static void waitForText(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            String expectedText) {
        waitForTextCondition(scenario, viewId, actualText -> expectedText.equals(actualText),
                "View " + viewId + " did not show \"" + expectedText + "\".");
    }

    private static void waitForTextContaining(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            String expectedText) {
        waitForTextCondition(scenario, viewId, actualText -> actualText.contains(expectedText),
                "View " + viewId + " did not contain \"" + expectedText + "\".");
    }

    private static void waitForTextCondition(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            TextCondition condition,
            String failureMessage) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        AtomicReference<String> lastText = new AtomicReference<>("");
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                TextView view = activity.findViewById(viewId);
                assertNotNull("Missing text view " + viewId, view);
                lastText.set(view.getText().toString());
            });
            if (condition.matches(lastText.get())) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError(failureMessage + " Last text was: " + lastText.get());
    }

    private static void waitForVisibility(
            ActivityScenario<HomeActivity> scenario,
            int viewId,
            int expectedVisibility) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        AtomicReference<Integer> lastVisibility = new AtomicReference<>(View.GONE);
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                assertNotNull("Missing view " + viewId, view);
                lastVisibility.set(view.getVisibility());
            });
            if (lastVisibility.get() == expectedVisibility) {
                return;
            }
            SystemClock.sleep(100);
        }
        assertEquals("View " + viewId + " visibility", expectedVisibility,
                (int) lastVisibility.get());
    }

    private interface TextCondition {
        boolean matches(String actualText);
    }
}
