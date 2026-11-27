package org.adaway.testing;

import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.List;

public final class VisibleTextAction {
    private VisibleTextAction() {}

    /** Select the visible label in the active window, independent of keyboard focus or row order. */
    public static void click(String label) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        do {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
                try {
                    for (AccessibilityNodeInfo node : nodes) {
                        if (!node.isVisibleToUser() || node.getText() == null
                                || !label.equalsIgnoreCase(node.getText().toString())) continue;
                        AccessibilityNodeInfo target = AccessibilityNodeInfo.obtain(node);
                        try {
                            while (target != null) {
                                if (target.isClickable() && target.performAction(
                                        AccessibilityNodeInfo.ACTION_CLICK)) return;
                                AccessibilityNodeInfo parent = target.getParent();
                                target.recycle();
                                target = parent;
                            }
                        } finally {
                            if (target != null) target.recycle();
                        }
                    }
                } finally {
                    for (AccessibilityNodeInfo node : nodes) node.recycle();
                    root.recycle();
                }
            }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("No clickable visible label in active window: " + label);
    }
}
