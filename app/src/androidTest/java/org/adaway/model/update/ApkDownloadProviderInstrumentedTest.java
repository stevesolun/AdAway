package org.adaway.model.update;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/** Opt-in external-network proof; default regression runs remain deterministic. */
@RunWith(AndroidJUnit4.class)
public class ApkDownloadProviderInstrumentedTest {
    private static final String SOURCE = "https://raw.githubusercontent.com/stevesolun/AdAway/"
            + "d51eb7aea06871c2c6ad2bbedb9ddbaaaca48d77/LICENSE.md";
    private static final String WRONG_DIGEST = "0".repeat(64);

    @Test(timeout = 120_000)
    public void systemProviderBroadcastReachesReceiverAndRejectsUntrustedBytes() throws Exception {
        assumeTrue("Real system download is opt-in.", "true".equals(
                InstrumentationRegistry.getArguments().getString("adawayLiveDownloadSmoke")));
        Context context = ApplicationProvider.getApplicationContext();
        DownloadManager downloads = context.getSystemService(DownloadManager.class);
        assertNotNull(downloads);
        AtomicLong id = new AtomicLong(-1);
        AtomicReference<RecordingReceiver> receiver = new AtomicReference<>();
        CountDownLatch terminal = new CountDownLatch(1);
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(SOURCE))
                        .setTitle("AdAway download delivery test")
                        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                                "provider-test-" + System.nanoTime() + ".txt");
                id.set(downloads.enqueue(request));
                RecordingReceiver target = new RecordingReceiver(id.get(), terminal);
                receiver.set(target);
                ContextCompat.registerReceiver(context, target,
                        new IntentFilter(ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED);
            });
            assertTrue("DownloadProvider must deliver completion without polling/manual broadcast.",
                    terminal.await(90, TimeUnit.SECONDS));
            try (Cursor row = downloads.query(new DownloadManager.Query().setFilterById(id.get()))) {
                assertNotNull(row);
                assertTrue(row.moveToFirst());
                assertEquals("The network download must actually succeed.",
                        DownloadManager.STATUS_SUCCESSFUL,
                        row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)));
            }
            assertEquals(1, receiver.get().matchingBroadcasts.get());
            assertEquals(1, receiver.get().verificationAttempts.get());
            assertEquals("Untrusted content must never reach installer handoff.",
                    0, receiver.get().installAttempts.get());
            assertFalse(receiver.get().hasPendingInstall());
        } finally {
            RecordingReceiver target = receiver.get();
            if (target != null) {
                target.cancel();
                try {
                    context.unregisterReceiver(target);
                } catch (IllegalArgumentException alreadyUnregistered) {
                    // Terminal processing already unregisters the receiver.
                }
            }
            if (id.get() > 0) downloads.remove(id.get());
        }
    }

    private static final class RecordingReceiver extends ApkDownloadReceiver {
        private final long expectedId;
        private final AtomicInteger matchingBroadcasts = new AtomicInteger();
        private final AtomicInteger verificationAttempts = new AtomicInteger();
        private final AtomicInteger installAttempts = new AtomicInteger();

        RecordingReceiver(long id, CountDownLatch terminal) {
            super(id, WRONG_DIGEST, WRONG_DIGEST, terminal::countDown);
            this.expectedId = id;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())
                    && intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == expectedId) {
                matchingBroadcasts.incrementAndGet();
            }
            super.onReceive(context, intent);
        }

        @Override
        boolean isVerifiedApk(Context context, Uri uri) {
            verificationAttempts.incrementAndGet();
            return super.isVerifiedApk(context, uri);
        }

        @Override
        void installApk(Context context, Uri uri) {
            // Do not open an installer if a regression wrongly authorizes these test bytes.
            installAttempts.incrementAndGet();
        }
    }
}
