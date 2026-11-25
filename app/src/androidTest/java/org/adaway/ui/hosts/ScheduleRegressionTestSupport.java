package org.adaway.ui.hosts;

import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.util.AppExecutors;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class ScheduleRegressionTestSupport {
    static final int TIMEOUT_MS = 10_000;

    private ScheduleRegressionTestSupport() {}

    static void drainDiskAndMain() throws InterruptedException {
        CountDownLatch drained = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(() ->
                AppExecutors.getInstance().mainThread().execute(drained::countDown));
        assertTrue("Queued disk results must reach the main thread",
                drained.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    static void waitFor(String description, BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    static boolean hasText(String expected) {
        AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(expected);
        try {
            for (AccessibilityNodeInfo match : matches) {
                if (matchesText(match, expected)) return true;
            }
            return false;
        } finally {
            for (AccessibilityNodeInfo match : matches) match.recycle();
            root.recycle();
        }
    }

    static void clickText(String expected) {
        waitFor("clickable text: " + expected, () -> {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().getRootInActiveWindow();
            if (root == null) return false;
            List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(expected);
            try {
                for (AccessibilityNodeInfo match : matches) {
                    if (!matchesText(match, expected)) continue;
                    AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(match);
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
                    } finally {
                        if (current != null) current.recycle();
                    }
                }
                return false;
            } finally {
                for (AccessibilityNodeInfo match : matches) match.recycle();
                root.recycle();
            }
        });
    }

    private static boolean matchesText(AccessibilityNodeInfo node, String expected) {
        return node.isVisibleToUser() && node.getText() != null
                && expected.equalsIgnoreCase(node.getText().toString());
    }
}
