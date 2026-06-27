package org.adaway.ui.log;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.vpn.VpnModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class LogRuntimeTruthTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int TEST_SOURCE_ID = 949494;
    private static final String LOG_HOST = "vpn-log-runtime-ci.invalid";

    private Context context;
    private AdAwayApplication application;
    private AppDatabase database;
    private HostListItemDao hostListItemDao;
    private HostEntryDao hostEntryDao;
    private HostsSourceDao hostsSourceDao;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up Log runtime truth");
        waitForDiskIoIdle();
        application = (AdAwayApplication) context.getApplicationContext();
        database = AppDatabase.getInstance(application);
        hostListItemDao = database.hostsListItemDao();
        hostEntryDao = database.hostEntryDao();
        hostsSourceDao = database.hostsSourceDao();
        cleanup();
        insertSource();
        insertBlockedHost();
        hostsSourceDao.updateActiveRuleStats(TEST_SOURCE_ID);
        hostEntryDao.sync();
        application.invalidateVpnRulesCache();
        PreferenceHelper.setAbBlockMethod(context, VPN);
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        if (application != null) {
            AdBlockModel model = application.getAdBlockModel();
            model.setRecordingLogs(false);
            model.clearLogs();
        }
        cleanup();
        if (hostEntryDao != null) {
            hostEntryDao.sync();
        }
        if (context != null) {
            InstrumentedTestState.resetForPassiveRootUi(context, "tear down Log runtime truth");
        }
    }

    @Test(timeout = 120_000)
    public void vpnGeneratedDnsLogAppearsInLogUi() {
        AdBlockModel model = application.getAdBlockModel();
        assertTrue("Expected VPN model after selecting VPN mode.", model instanceof VpnModel);
        model.clearLogs();
        model.setRecordingLogs(true);

        HostEntry entry = ((VpnModel) model).getEntry(LOG_HOST);
        assertEquals("Fixture host must be blocked by runtime truth before checking Log UI.",
                BLOCKED, entry.getType());
        model.setRecordingLogs(false);
        assertTrue("VPN model must record generated DNS request.",
                model.getLogs().contains(LOG_HOST));

        try (ActivityScenario<LogActivity> scenario = ActivityScenario.launch(LogActivity.class)) {
            LogActivity activity = waitForActivity(LogActivity.class);
            waitForVisibleText(activity, LOG_HOST);
        }
    }

    private void insertSource() {
        HostsSource source = new HostsSource();
        source.setId(TEST_SOURCE_ID);
        source.setLabel("Log runtime truth test");
        source.setUrl("https://example.invalid/log-runtime-truth.txt");
        source.setEnabled(true);
        hostsSourceDao.insert(source);
    }

    private void insertBlockedHost() {
        HostListItem item = new HostListItem();
        item.setHost(LOG_HOST);
        item.setType(BLOCKED);
        item.setKind(EXACT);
        item.setEnabled(true);
        item.setSourceId(TEST_SOURCE_ID);
        item.setGeneration(getActiveGeneration());
        hostListItemDao.insert(item);
    }

    private int getActiveGeneration() {
        try (Cursor cursor = database.getOpenHelper().getWritableDatabase().query(
                "SELECT active_generation FROM hosts_meta WHERE id = 0 LIMIT 1")) {
            if (!cursor.moveToFirst()) {
                return 0;
            }
            return cursor.getInt(0);
        }
    }

    private void cleanup() {
        if (database == null) {
            return;
        }
        database.getOpenHelper().getWritableDatabase().execSQL(
                "DELETE FROM hosts_lists WHERE host = ?",
                new Object[]{LOG_HOST});
        hostListItemDao.clearSourceHosts(TEST_SOURCE_ID);
        hostsSourceDao.getById(TEST_SOURCE_ID).ifPresent(hostsSourceDao::delete);
        hostEntryDao.invalidateMaterializedRuntimeCaches();
    }

    private static <T extends Activity> T waitForActivity(@NonNull Class<T> activityClass) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<T> resumed = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (activityClass.isInstance(activity)) {
                        resumed.set(activityClass.cast(activity));
                        return;
                    }
                }
            });
            T activity = resumed.get();
            if (activity != null) {
                return activity;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + activityClass.getSimpleName() + ".");
    }

    private static void waitForVisibleText(Activity activity, String expectedText) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    found.set(hasVisibleText(activity.getWindow().getDecorView(), expectedText)));
            if (Boolean.TRUE.equals(found.get())) {
                return;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Text did not appear: " + expectedText);
    }

    private static boolean hasVisibleText(@Nullable View view, String expectedText) {
        if (view instanceof TextView
                && view.isShown()
                && expectedText.contentEquals(((TextView) view).getText())) {
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (hasVisibleText(group.getChildAt(i), expectedText)) {
                return true;
            }
        }
        return false;
    }

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                activity.finish();
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void waitForDiskIoIdle() {
        CountDownLatch latch = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(latch::countDown);
        try {
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Timed out waiting for app disk executor to become idle.");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for app disk executor.",
                    interruptedException);
        }
    }
}
