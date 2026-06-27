package org.adaway.ui.prefs;

import static android.app.Activity.RESULT_CANCELED;
import static android.content.Intent.ACTION_CREATE_DOCUMENT;
import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.adaway.R;
import org.adaway.testing.InstrumentedTestState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PrefsBackupRestoreSafInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final String JSON_MIME_TYPE = "application/json";
    private static final String WILDCARD_MIME_TYPE = "*/*";

    private Context context;
    private Instrumentation instrumentation;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        this.instrumentation = InstrumentationRegistry.getInstrumentation();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up backup SAF");
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down backup SAF");
        }
    }

    @Test(timeout = 60_000)
    public void backupPreferenceLaunchesJsonCreateDocumentSafIntent() throws Exception {
        Instrumentation.ActivityMonitor monitor = addSafMonitor(
                ACTION_CREATE_DOCUMENT, JSON_MIME_TYPE);
        try (ActivityScenario<PrefsActivity> ignored =
                     ActivityScenario.launch(PrefsActivity.createBackupRestoreIntent(this.context))) {
            clickAccessibilityText(this.context.getString(R.string.pref_backup));

            assertMonitorHit(monitor, "backup create-document SAF intent");
        } finally {
            this.instrumentation.removeMonitor(monitor);
        }
    }

    @Test(timeout = 60_000)
    public void restorePreferenceLaunchesOpenDocumentSafIntent() throws Exception {
        Instrumentation.ActivityMonitor monitor = addSafMonitor(
                ACTION_OPEN_DOCUMENT, WILDCARD_MIME_TYPE);
        try (ActivityScenario<PrefsActivity> ignored =
                     ActivityScenario.launch(PrefsActivity.createBackupRestoreIntent(this.context))) {
            clickAccessibilityText(this.context.getString(R.string.pref_restore));

            assertMonitorHit(monitor, "restore open-document SAF intent");
        } finally {
            this.instrumentation.removeMonitor(monitor);
        }
    }

    private Instrumentation.ActivityMonitor addSafMonitor(String action, String mimeType) {
        IntentFilter filter = new IntentFilter(action);
        filter.addCategory(CATEGORY_OPENABLE);
        try {
            filter.addDataType(mimeType);
        } catch (IntentFilter.MalformedMimeTypeException exception) {
            throw new AssertionError("Bad test MIME type: " + mimeType, exception);
        }
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                filter,
                new Instrumentation.ActivityResult(RESULT_CANCELED, null),
                true
        );
        this.instrumentation.addMonitor(monitor);
        return monitor;
    }

    private static void assertMonitorHit(
            Instrumentation.ActivityMonitor monitor,
            String description) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (monitor.getHits() > 0) {
                return;
            }
            SystemClock.sleep(100);
        }
        assertTrue("Did not launch " + description, monitor.getHits() > 0);
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
