/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.home;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.PatternMatcher;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.model.error.HostError;
import org.adaway.model.update.Manifest;
import org.adaway.model.update.UpdateModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.update.UpdateActivity;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@RunWith(AndroidJUnit4.class)
public class HomeUpdateSignalErrorInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000L;
    private static final Uri HELP_URI = Uri.parse("https://github.com/AdAway/AdAway/wiki");

    private Context context;
    private AdAwayApplication application;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.application = (AdAwayApplication) this.context.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up Home update UI");
    }

    @After
    public void tearDown() throws Exception {
        try {
            publishManifest(null);
            finishResumedActivities();
        } finally {
            if (this.context != null) {
                InstrumentedTestState.resetForPassiveRootUi(
                        this.context,
                        "tear down Home update UI");
            }
        }
    }

    @Test(timeout = 60_000)
    public void appUpdateManifestMarksVersionLabelAndOpensUpdateActivity() throws Exception {
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            waitForVersionText(scenario, this.application.getUpdateModel().getVersionName());

            publishManifest(createManifest());

            waitForVersionText(scenario, this.context.getString(R.string.update_available));
            scenario.onActivity(activity -> {
                TextView versionTextView = activity.findViewById(R.id.versionTextView);
                assertNotNull(versionTextView);
                assertTrue("Update-available label must be visually emphasized.",
                        (versionTextView.getTypeface().getStyle() & Typeface.BOLD) != 0);
                assertTrue("Version label click must open the update details screen.",
                        versionTextView.performClick());
            });

            waitForResumedActivity(UpdateActivity.class);
        }
    }

    @Test(timeout = 60_000)
    public void hostErrorShowsDetailsAndHelpAction() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor helpMonitor = instrumentation.addMonitor(
                helpIntentFilter(),
                new Instrumentation.ActivityResult(Activity.RESULT_OK, null),
                true);
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            publishHostError(scenario, HostError.NO_CONNECTION);

            waitForTextContaining(this.context.getString(R.string.error_no_connection_message));
            waitForTextContaining(this.context.getString(R.string.error_no_connection_details));
            waitForTextContaining(this.context.getString(R.string.error_dialog_help));

            AccessibilityNodeInfo helpButton =
                    waitForClickableText(this.context.getString(R.string.button_help));
            assertTrue("Help button must be clickable.",
                    helpButton.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            instrumentation.waitForMonitorWithTimeout(helpMonitor, TIMEOUT_MS);
            assertTrue("Help action must launch the wiki ACTION_VIEW intent.",
                    helpMonitor.getHits() > 0);
        } finally {
            instrumentation.removeMonitor(helpMonitor);
        }
    }

    private void publishManifest(Manifest manifest) throws Exception {
        MutableLiveData<Manifest> manifestLiveData = getManifestLiveData();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> manifestLiveData.setValue(manifest));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @SuppressWarnings("unchecked")
    private MutableLiveData<Manifest> getManifestLiveData() throws Exception {
        Field field = UpdateModel.class.getDeclaredField("manifest");
        field.setAccessible(true);
        return (MutableLiveData<Manifest>) field.get(this.application.getUpdateModel());
    }

    private Manifest createManifest() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        int nextVersion = this.application.getUpdateModel().getVersionCode() + 1;
        String sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        JSONObject payload = new JSONObject()
                .put("version", "13.5.1-test")
                .put("versionCode", nextVersion)
                .put("changelog", "Home update signal test")
                .put("apkSha256", sha256)
                .put("signingCertificateSha256", sha256)
                .put("apkUrl", "https://app.adaway.org/adaway.apk?versionCode=" + nextVersion)
                .put("channel", "stable")
                .put("store", "adaway")
                .put("expiresAt", Instant.now().plus(1, ChronoUnit.DAYS).toString());
        String payloadText = payload.toString();
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(payloadText.getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject()
                .put("payload", payloadText)
                .put("signature", Base64.getEncoder().encodeToString(signer.sign()));
        return new Manifest(envelope.toString(), nextVersion - 1, publicKey, "stable", "adaway");
    }

    private static void publishHostError(
            @NonNull ActivityScenario<HomeActivity> scenario,
            @NonNull HostError error) {
        scenario.onActivity(activity -> {
            HomeViewModel viewModel = new ViewModelProvider(activity).get(HomeViewModel.class);
            try {
                Field field = HomeViewModel.class.getDeclaredField("error");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                MutableLiveData<HostError> errorLiveData =
                        (MutableLiveData<HostError>) field.get(viewModel);
                errorLiveData.setValue(error);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Failed to publish Home error.", exception);
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void waitForVersionText(
            @NonNull ActivityScenario<HomeActivity> scenario,
            @NonNull String expectedText) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final boolean[] matched = new boolean[1];
            scenario.onActivity(activity -> {
                TextView versionTextView = activity.findViewById(R.id.versionTextView);
                matched[0] = versionTextView != null
                        && expectedText.contentEquals(versionTextView.getText())
                        && versionTextView.isShown();
            });
            if (matched[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Version label did not show \"" + expectedText + "\".");
    }

    private static void waitForResumedActivity(
            @NonNull Class<? extends Activity> expectedActivityClass) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final boolean[] resumed = new boolean[1];
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (expectedActivityClass.isInstance(activity)) {
                        resumed[0] = true;
                        return;
                    }
                }
            });
            if (resumed[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError(expectedActivityClass.getSimpleName() + " was not resumed.");
    }

    private static IntentFilter helpIntentFilter() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_VIEW);
        filter.addDataScheme(HELP_URI.getScheme());
        filter.addDataAuthority(HELP_URI.getHost(), null);
        filter.addDataPath(HELP_URI.getPath(), PatternMatcher.PATTERN_LITERAL);
        return filter;
    }

    private static void waitForTextContaining(@NonNull String expectedText) {
        if (waitForNode(expectedText, false, false) == null) {
            throw new AssertionError("Active window did not show text containing \""
                    + expectedText + "\".");
        }
    }

    @NonNull
    private static AccessibilityNodeInfo waitForClickableText(@NonNull String expectedText) {
        AccessibilityNodeInfo node = waitForNode(expectedText, true, true);
        if (node != null) {
            AccessibilityNodeInfo clickable = firstClickableAncestor(node);
            if (clickable != null) {
                return clickable;
            }
        }
        throw new AssertionError("Active window did not show a clickable \"" + expectedText
                + "\" button.");
    }

    private static AccessibilityNodeInfo waitForNode(
            @NonNull String expectedText,
            boolean exact,
            boolean ignoreCase) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            AccessibilityNodeInfo node = findNode(root, expectedText, exact, ignoreCase);
            if (node != null) {
                return node;
            }
            SystemClock.sleep(100L);
        }
        return null;
    }

    private static AccessibilityNodeInfo findNode(
            AccessibilityNodeInfo node,
            @NonNull String expectedText,
            boolean exact,
            boolean ignoreCase) {
        if (node == null) {
            return null;
        }
        CharSequence text = node.getText();
        if (text != null && textMatches(text.toString(), expectedText, exact, ignoreCase)) {
            return node;
        }
        CharSequence contentDescription = node.getContentDescription();
        if (contentDescription != null
                && textMatches(contentDescription.toString(), expectedText, exact, ignoreCase)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo match = findNode(child, expectedText, exact, ignoreCase);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static boolean textMatches(
            @NonNull String actualText,
            @NonNull String expectedText,
            boolean exact,
            boolean ignoreCase) {
        String actual = ignoreCase ? actualText.toLowerCase(java.util.Locale.ROOT) : actualText;
        String expected = ignoreCase
                ? expectedText.toLowerCase(java.util.Locale.ROOT)
                : expectedText;
        return exact ? actual.equals(expected) : actual.contains(expected);
    }

    private static AccessibilityNodeInfo firstClickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            current = current.getParent();
        }
        return null;
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
