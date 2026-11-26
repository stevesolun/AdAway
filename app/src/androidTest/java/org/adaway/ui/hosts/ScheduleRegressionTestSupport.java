package org.adaway.ui.hosts;

import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inspector.WindowInspector;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.appbar.MaterialToolbar;

import org.adaway.R;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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

    static void openToolbarPicker(ActivityScenario<HomeActivity> screen) {
        waitFor("Sources toolbar", () -> {
            AtomicBoolean ready = new AtomicBoolean();
            screen.onActivity(a -> {
                MaterialToolbar bar = a.findViewById(R.id.hosts_sources_toolbar);
                var tab = a.getSupportFragmentManager().findFragmentById(R.id.nav_fragment_container);
                var child = tab == null ? null : tab.getChildFragmentManager()
                        .findFragmentById(R.id.hosts_sources_child_container);
                ready.set(child instanceof HostsSourcesFragment && child.isResumed()
                        && bar != null && bar.isShown() && bar.hasWindowFocus()
                        && bar.getMenu().findItem(R.id.action_hosts_schedule_filter_set) != null);
            });
            return ready.get();
        });
        screen.onActivity(a -> {
            MaterialToolbar bar = a.findViewById(R.id.hosts_sources_toolbar);
            assertTrue(bar.getMenu().performIdentifierAction(R.id.action_hosts_schedule_filter_set, 0));
        });
    }

    static void clickPickerRow(ActivityScenario<?> screen, String label) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            clickText(label);
            return;
        }
        withDialogView(screen, ListView.class, label, list -> {
            for (int i = 0; i < list.getCount(); i++) {
                if (label.equals(list.getItemAtPosition(i))) {
                    View row = list.getChildAt(i - list.getFirstVisiblePosition());
                    assertTrue("Missing visible picker row: " + label, row != null && row.isShown());
                    assertTrue(list.performItemClick(row, i, list.getItemIdAtPosition(i)));
                    return;
                }
            }
            throw new AssertionError("Missing picker choice: " + label);
        });
    }

    static void waitForPickerRows(ActivityScenario<?> screen, String label) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            waitFor(label, () -> hasText(label));
            return;
        }
        withDialogView(screen, ListView.class, label, list -> assertTrue(list.getCount() > 0));
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    static <T extends View> void withDialogView(ActivityScenario<?> screen, Class<T> type,
                                                        String label, Consumer<T> action) {
        waitFor("focused dialog with " + type.getSimpleName() + (label == null ? "" : ": " + label), () -> {
            AtomicBoolean found = new AtomicBoolean();
            screen.onActivity(a -> {
                for (View root : WindowInspector.getGlobalWindowViews()) {
                    if (root == a.getWindow().getDecorView() || !root.hasWindowFocus()) continue;
                    if (label != null) {
                        ArrayList<View> matches = new ArrayList<>();
                        root.findViewsWithText(matches, label, View.FIND_VIEWS_WITH_TEXT);
                        if (matches.stream().noneMatch(v -> v instanceof TextView text && v.isShown()
                                && label.contentEquals(text.getText()))) continue;
                    }
                    T view = find(root, type);
                    if (view == null || !view.isShown() || !view.isLaidOut()) continue;
                    action.accept(view);
                    found.set(true);
                    return;
                }
            });
            return found.get();
        });
    }

    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                T child = find(group.getChildAt(i), type);
                if (child != null) return child;
            }
        }
        return null;
    }
}
