package org.adaway.ui.prefs;

import static android.app.Activity.RESULT_CANCELED;
import static android.content.Intent.ACTION_CREATE_DOCUMENT;
import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.junit.Assert.assertFalse;
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
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.model.backup.BackupExporter;
import org.adaway.model.backup.BackupImporter;
import org.adaway.model.source.TestHostsContentProvider;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class PrefsBackupRestoreSafInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final String JSON_MIME_TYPE = "application/json";
    private static final String WILDCARD_MIME_TYPE = "*/*";
    private static final String BACKUP_SOURCE_LABEL = "Backup Round Trip Source";
    private static final String BACKUP_SOURCE_URL =
            "https://backup-roundtrip.example/hosts.txt";
    private static final String BLOCKED_HOST = "backup-blocked-roundtrip.example";
    private static final String ALLOWED_HOST = "backup-allowed-roundtrip.example";
    private static final String REDIRECTED_HOST = "backup-redirected-roundtrip.example";
    private static final String REDIRECTED_IP = "8.8.8.8";

    private Context context;
    private Context providerContext;
    private Instrumentation instrumentation;
    private AppDatabase database;
    private HostListItemDao hostListItemDao;
    private HostsSourceDao hostsSourceDao;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        this.instrumentation = InstrumentationRegistry.getInstrumentation();
        this.providerContext = this.instrumentation.getContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up backup SAF");
        this.database = AppDatabase.getInstance(this.context);
        drainDiskIo();
        this.hostListItemDao = this.database.hostsListItemDao();
        this.hostsSourceDao = this.database.hostsSourceDao();
        clearRoundTripData();
        TestHostsContentProvider.clearBackup(this.providerContext);
    }

    @After
    public void tearDown() throws Exception {
        finishResumedActivities();
        if (this.hostListItemDao != null && this.hostsSourceDao != null) {
            clearRoundTripData();
        }
        if (this.providerContext != null) {
            TestHostsContentProvider.clearBackup(this.providerContext);
        }
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

    @Test(timeout = 120_000)
    public void backupExporterAndImporterRoundTripThroughProviderUri() throws Exception {
        assertProviderFixtureRoundTrip();
        seedRoundTripData();

        BackupExporter.exportToBackup(this.context, TestHostsContentProvider.BACKUP_URI);
        waitForBackupToContainRoundTripData();

        clearRoundTripData();
        assertFalse(hasUserRule(BLOCKED_HOST, ListType.BLOCKED, RuleKind.SUFFIX, true, null));
        assertFalse(this.hostsSourceDao.getByUrl(BACKUP_SOURCE_URL).isPresent());

        BackupImporter.importFromBackup(this.context, TestHostsContentProvider.BACKUP_URI);
        waitForRestoredRoundTripData();
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

    private void assertProviderFixtureRoundTrip() throws IOException {
        TestHostsContentProvider.clearBackup(this.providerContext);
        try (OutputStream outputStream = this.context.getContentResolver()
                .openOutputStream(TestHostsContentProvider.BACKUP_URI)) {
            assertTrue("Provider write stream was null", outputStream != null);
            outputStream.write("fixture-smoke".getBytes(StandardCharsets.UTF_8));
        }
        waitForBackupToContain("provider fixture smoke content", "fixture-smoke");
        TestHostsContentProvider.clearBackup(this.providerContext);
    }

    private void seedRoundTripData() {
        clearRoundTripData();

        HostsSource source = new HostsSource();
        source.setLabel(BACKUP_SOURCE_LABEL);
        source.setUrl(BACKUP_SOURCE_URL);
        source.setEnabled(true);
        source.setAllowEnabled(true);
        source.setRedirectEnabled(true);
        this.hostsSourceDao.insert(source);

        insertUserRule(BLOCKED_HOST, ListType.BLOCKED, RuleKind.SUFFIX, true, null);
        insertUserRule(ALLOWED_HOST, ListType.ALLOWED, RuleKind.EXACT, true, null);
        insertUserRule(REDIRECTED_HOST, ListType.REDIRECTED, RuleKind.EXACT, true,
                REDIRECTED_IP);
    }

    private void clearRoundTripData() {
        this.hostListItemDao.deleteUserFromHost(BLOCKED_HOST);
        this.hostListItemDao.deleteUserFromHost(ALLOWED_HOST);
        this.hostListItemDao.deleteUserFromHost(REDIRECTED_HOST);
        Optional<HostsSource> source = this.hostsSourceDao.getByUrl(BACKUP_SOURCE_URL);
        source.ifPresent(this.hostsSourceDao::delete);
    }

    private void insertUserRule(
            String host,
            ListType type,
            RuleKind kind,
            boolean enabled,
            String redirection) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(type);
        item.setKind(kind);
        item.setEnabled(enabled);
        item.setRedirection(redirection);
        item.setSourceId(USER_SOURCE_ID);
        this.hostListItemDao.insert(item);
    }

    private void waitForBackupToContainRoundTripData() {
        waitForBackupToContain(
                "backup provider JSON to contain exported rows",
                BACKUP_SOURCE_LABEL,
                BLOCKED_HOST,
                ALLOWED_HOST,
                REDIRECTED_HOST,
                REDIRECTED_IP
        );
    }

    private void waitForBackupToContain(String description, String... expectedSnippets) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        String lastRead = "not attempted";
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                String backup = TestHostsContentProvider.readBackup(this.providerContext);
                lastRead = backup.length() <= 200 ? backup : backup.substring(0, 200);
                if (containsAll(backup, expectedSnippets)) {
                    return;
                }
            } catch (IOException exception) {
                lastRead = exception.toString();
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + description + ". Last read: " +
                lastRead);
    }

    private static boolean containsAll(String content, String... expectedSnippets) {
        for (String snippet : expectedSnippets) {
            if (!content.contains(snippet)) {
                return false;
            }
        }
        return true;
    }

    private void waitForRestoredRoundTripData() {
        waitForCondition("backup import to restore provider JSON rows", () -> {
            Optional<HostsSource> restoredSource =
                    this.hostsSourceDao.getByUrl(BACKUP_SOURCE_URL);
            return restoredSource.isPresent()
                    && !restoredSource.get().isRedirectEnabled()
                    && hasUserRule(BLOCKED_HOST, ListType.BLOCKED, RuleKind.SUFFIX, true, null)
                    && hasUserRule(ALLOWED_HOST, ListType.ALLOWED, RuleKind.EXACT, true, null)
                    && hasUserRule(REDIRECTED_HOST, ListType.REDIRECTED, RuleKind.EXACT, true,
                    REDIRECTED_IP);
        });
    }

    private boolean hasUserRule(
            String host,
            ListType type,
            RuleKind kind,
            boolean enabled,
            String redirection) {
        for (HostListItem item : this.hostListItemDao.getUserList()) {
            if (host.equals(item.getHost())
                    && item.getType() == type
                    && item.getKind() == kind
                    && item.isEnabled() == enabled
                    && Objects.equals(redirection, item.getRedirection())) {
                return true;
            }
        }
        return false;
    }

    private static void waitForCondition(String description, Condition condition) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.isSatisfied()) {
                return;
            }
            SystemClock.sleep(100);
        }
        assertTrue("Timed out waiting for " + description, condition.isSatisfied());
    }

    private static void drainDiskIo() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(latch::countDown);
        if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("Timed out draining disk executor");
        }
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

    private interface Condition {
        boolean isSatisfied();
    }
}
