package org.adaway.ui.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Looper;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** In-memory fixtures only: no root shell, remount, or system hosts read/write. */
@RunWith(AndroidJUnit4.class)
public class HostsFileViewerInstrumentedTest {
    @Test
    public void truncatedSnapshotIsReadOnlyAndLoadedOffMainThread() throws Exception {
        AtomicBoolean background = new AtomicBoolean();
        FixtureDialog dialog = fixture(() -> {
            background.set(Looper.myLooper() != Looper.getMainLooper());
            byte[] bytes = new byte[HostsFileSnapshot.MAX_BYTES + 20];
            Arrays.fill(bytes, (byte) 'a');
            return HostsFileSnapshot.read(new ByteArrayInputStream(bytes));
        });
        try (ActivityScenario<PrefsActivity> scenario = ActivityScenario.launch(PrefsActivity.class)) {
            show(scenario, dialog);
            awaitStatus(scenario, dialog, R.string.hosts_viewer_truncated);
            scenario.onActivity(activity -> {
                TextView content = dialog.requireDialog().findViewById(R.id.hosts_viewer_content);
                assertFalse(content instanceof EditText);
                assertNull(content.getKeyListener());
                assertTrue(content.isTextSelectable());
                assertFalse(content.isSaveEnabled());
                assertEquals(HostsFileSnapshot.MAX_BYTES, content.length());
            });
            assertTrue(background.get());
        }
    }

    @Test
    public void readAndPermissionErrorsStayInTheReadOnlyDialog() {
        try (ActivityScenario<PrefsActivity> scenario = ActivityScenario.launch(PrefsActivity.class)) {
            for (boolean permissionFailure : new boolean[]{false, true}) {
                FixtureDialog dialog = fixture(() -> {
                    if (permissionFailure) throw new SecurityException("fixture permission failure");
                    throw new IOException("fixture read failure");
                });
                show(scenario, dialog);
                awaitStatus(scenario, dialog, R.string.hosts_viewer_error);
                scenario.onActivity(activity -> {
                    TextView content = dialog.requireDialog().findViewById(R.id.hosts_viewer_content);
                    assertEquals("", content.getText().toString());
                    assertTrue(dialog.requireDialog().isShowing());
                    dialog.dismissNow();
                });
            }
        }
    }

    @Test
    public void dismissedDialogIgnoresLateReadResult() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        FixtureDialog dialog = fixture(() -> delayedSnapshot(started, release, "old"));
        AtomicReference<TextView> oldContent = new AtomicReference<>();
        try (ActivityScenario<PrefsActivity> scenario = ActivityScenario.launch(PrefsActivity.class)) {
            show(scenario, dialog);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            scenario.onActivity(activity -> {
                oldContent.set(dialog.requireDialog().findViewById(R.id.hosts_viewer_content));
                dialog.dismissNow();
            });
            release.countDown();
            org.adaway.util.AppExecutors.getInstance().diskIO().execute(finished::countDown);
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals("", oldContent.get().getText().toString());
                assertNull(activity.getSupportFragmentManager()
                        .findFragmentByTag(HostsFileViewerDialog.TAG));
            });
        } finally {
            release.countDown();
        }
    }

    @Test
    public void stoppedDialogDropsOldResultAndReloadsOnStart() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        FixtureDialog dialog = fixture(() -> reads.incrementAndGet() == 1
                ? delayedSnapshot(started, release, "old") : snapshot("current"));
        AtomicReference<TextView> oldContent = new AtomicReference<>();
        try (ActivityScenario<PrefsActivity> scenario = ActivityScenario.launch(PrefsActivity.class)) {
            show(scenario, dialog);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            scenario.onActivity(activity -> oldContent.set(
                    dialog.requireDialog().findViewById(R.id.hosts_viewer_content)));
            scenario.moveToState(Lifecycle.State.CREATED);
            release.countDown();
            org.adaway.util.AppExecutors.getInstance().diskIO().execute(finished::countDown);
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    assertEquals("", oldContent.get().getText().toString()));
            scenario.moveToState(Lifecycle.State.RESUMED);
            awaitStatus(scenario, dialog, R.string.hosts_viewer_complete);
            scenario.onActivity(activity -> {
                TextView content = dialog.requireDialog().findViewById(R.id.hosts_viewer_content);
                assertEquals("current", content.getText().toString());
            });
        } finally {
            release.countDown();
        }
    }

    private static FixtureDialog fixture(SnapshotRead read) {
        FixtureDialog dialog = new FixtureDialog();
        dialog.mRead = read;
        return dialog;
    }

    private static void show(ActivityScenario<PrefsActivity> scenario, FixtureDialog dialog) {
        scenario.onActivity(activity -> dialog.showNow(activity.getSupportFragmentManager(),
                HostsFileViewerDialog.TAG));
    }

    private static void awaitStatus(ActivityScenario<PrefsActivity> scenario, FixtureDialog dialog,
                                    int expected) {
        AtomicBoolean matched = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 5000;
        do {
            scenario.onActivity(activity -> {
                assertNotNull(dialog.getDialog());
                TextView status = dialog.requireDialog().findViewById(R.id.hosts_viewer_status);
                matched.set(activity.getString(expected).contentEquals(status.getText()));
            });
            if (matched.get()) return;
            SystemClock.sleep(20);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Hosts viewer did not reach status " + expected);
    }

    private static HostsFileSnapshot delayedSnapshot(CountDownLatch started, CountDownLatch release,
                                                     String text) throws IOException {
        started.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        // Deliberately finish after cancellation to exercise stale-delivery protection.
        while (release.getCount() != 0 && System.nanoTime() < deadline) {
            try {
                release.await(20, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                // The production snapshot reader cooperates; this fixture simulates a late read.
            }
        }
        return snapshot(text);
    }

    private static HostsFileSnapshot snapshot(String text) throws IOException {
        return HostsFileSnapshot.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private interface SnapshotRead {
        HostsFileSnapshot read() throws IOException;
    }

    public static class FixtureDialog extends HostsFileViewerDialog {
        private SnapshotRead mRead;

        @Override
        HostsFileSnapshot readSnapshot() throws IOException {
            return mRead.read();
        }
    }
}
