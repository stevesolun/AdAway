package org.adaway.model.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.vpn.VpnModel;
import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.adaway.util.Constants;
import org.adaway.vpn.VpnStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class SourceUpdateServiceWorkManagerTest {
    private Context context;
    private WorkManager workManager;
    private Field sourceField;
    private Object originalSources;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        workManager = WorkManager.getInstance(context);
        resetWorkManager();
        // Scheduling assertions enqueue real work. Don't let a network probe outlive its test.
        sourceField = AdAwayApplication.class.getDeclaredField("sourceModel");
        sourceField.setAccessible(true);
        originalSources = sourceField.get(context.getApplicationContext());
        sourceField.set(context.getApplicationContext(), new SourceModel(context) {
            @Override public boolean checkForUpdate() { return false; }
        });
    }

    @After
    public void tearDown() throws Exception {
        try {
            resetWorkManager();
        } finally {
            if (sourceField != null) sourceField.set(context.getApplicationContext(), originalSources);
        }
    }

    @Test
    public void immediateWorkerRetriesWhenRetrievalDoesNotComplete() throws Exception {
        assertImmediateWorkerRetries(new SourceModel(context) {
            @Override
            public String retrieveHostsSourcesWithReceipt() {
                return null;
            }
        });
    }

    @Test
    public void immediateWorkerRetriesWhenAnotherUpdateOwnsTheGate() throws Exception {
        assertImmediateWorkerRetries(new SourceModel(context) {
            @Override
            public String retrieveHostsSourcesWithReceipt() throws HostErrorException {
                throw new HostErrorException(HostError.UPDATE_IN_PROGRESS);
            }
        });
    }

    private void assertImmediateWorkerRetries(SourceModel sourceModel) throws Exception {
        sourceField.set(context.getApplicationContext(), sourceModel);
        SourceUpdateService.HostsSourcesImmediateUpdateWorker worker =
                TestListenableWorkerBuilder.from(context,
                        SourceUpdateService.HostsSourcesImmediateUpdateWorker.class).build();
        assertEquals(ListenableWorker.Result.retry(), worker.doWork());
    }

    @Test
    public void dailyAndQueuedRefreshDoNotEnableStoppedVpnOrClaimProtectionApplied() throws Exception {
        AdAwayApplication app = (AdAwayApplication) context.getApplicationContext();
        AdBlockMethod method = PreferenceHelper.getAdBlockMethod(app);
        VpnStatus status = PreferenceHelper.getVpnServiceStatus(app);
        org.junit.Assume.assumeTrue("Do not disturb an active service", status == VpnStatus.STOPPED);
        SharedPreferences prefs = app.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String daily = app.getString(R.string.pref_automatic_update_daily_key);
        boolean hadDaily = prefs.contains(daily);
        boolean previousDaily = prefs.getBoolean(daily, false);
        Field blocker = AdAwayApplication.class.getDeclaredField("adBlockModel");
        blocker.setAccessible(true);
        Object previousBlocker = blocker.get(app);
        int[] downloads = {0};
        try {
            PreferenceHelper.setAbBlockMethod(app, AdBlockMethod.VPN);
            assertTrue(prefs.edit().putBoolean(daily, true).commit());
            sourceField.set(app, new SourceModel(app) {
                @Override public boolean checkForUpdate() { return true; }
                @Override public String retrieveHostsSourcesWithReceipt() {
                    downloads[0]++;
                    return SourceApplyState.fingerprint(AppDatabase.getInstance(app).hostsSourceDao().getAll());
                }
            });
            blocker.set(app, new VpnModel(app) {
                @Override public void apply() {
                    throw new AssertionError("A rule download must not request VPN enable");
                }
            });
            assertEquals(ListenableWorker.Result.success(), TestListenableWorkerBuilder.from(app,
                    SourceUpdateService.HostsSourcesUpdateWorker.class).build().doWork());
            String downloaded = SourceApplyState.fingerprint(
                    AppDatabase.getInstance(app).hostsSourceDao().getAll());
            assertEquals(ListenableWorker.Result.success(new Data.Builder()
                    .putString(SourceUpdateService.DOWNLOADED_CONFIGURATION, downloaded).build()),
                    TestListenableWorkerBuilder.from(app,
                    SourceUpdateService.HostsSourcesImmediateUpdateWorker.class).build().doWork());
            assertEquals(2, downloads[0]);
            assertEquals(VpnStatus.STOPPED, PreferenceHelper.getVpnServiceStatus(app));
        } finally {
            blocker.set(app, previousBlocker);
            PreferenceHelper.setAbBlockMethod(app, method);
            SharedPreferences.Editor editor = prefs.edit();
            if (hadDaily) editor.putBoolean(daily, previousDaily); else editor.remove(daily);
            assertTrue(editor.commit());
        }
    }

    @Test
    public void syncPreferences_enqueuesAndCancelsHostsUpdateWork() throws Exception {
        setBoolean(R.string.pref_update_check_hosts_daily_key, true);

        SourceUpdateService.syncPreferences(context);

        assertSingleActiveWork(SourceUpdateService.WORK_NAME);

        setBoolean(R.string.pref_update_check_hosts_daily_key, false);
        SourceUpdateService.syncPreferences(context);

        assertNoActiveWork(SourceUpdateService.WORK_NAME);
    }

    @Test
    public void enable_replacesExistingHostsUpdateWorkWithoutDuplicates() throws Exception {
        SourceUpdateService.enable(context, false);
        SourceUpdateService.enable(context, true);

        assertSingleActiveWork(SourceUpdateService.WORK_NAME);
    }

    @Test
    public void enable_updatesExistingHostsUpdateConstraints() throws Exception {
        enqueueStaleHostsUpdateWork(NetworkType.CONNECTED);

        assertEquals(NetworkType.CONNECTED, singleActiveWork(SourceUpdateService.WORK_NAME)
                .getConstraints().getRequiredNetworkType());

        SourceUpdateService.enable(context, true);

        WorkInfo updated = singleActiveWork(SourceUpdateService.WORK_NAME);
        assertEquals(NetworkType.UNMETERED, updated.getConstraints().getRequiredNetworkType());
        assertTrue(updated.getConstraints().requiresStorageNotLow());
    }

    private void resetWorkManager() throws Exception {
        workManager.cancelAllWork().getResult().get(5, TimeUnit.SECONDS);
        workManager.pruneWork().getResult().get(5, TimeUnit.SECONDS);
    }

    private void setBoolean(int keyResId, boolean value) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        assertTrue(prefs.edit().putBoolean(context.getString(keyResId), value).commit());
    }

    private void enqueueStaleHostsUpdateWork(NetworkType networkType) throws Exception {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SourceUpdateService.HostsSourcesUpdateWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();

        workManager.enqueueUniquePeriodicWork(
                SourceUpdateService.WORK_NAME, ExistingPeriodicWorkPolicy.REPLACE, request)
                .getResult()
                .get(5, TimeUnit.SECONDS);
    }

    private void assertSingleActiveWork(String workName) throws Exception {
        assertEquals(1, activeWork(workName).size());
    }

    private void assertNoActiveWork(String workName) throws Exception {
        assertEquals(0, activeWork(workName).size());
    }

    private List<WorkInfo> activeWork(String workName) throws Exception {
        return workManager.getWorkInfosForUniqueWork(workName).get(5, TimeUnit.SECONDS)
                .stream()
                .filter(info -> !info.getState().isFinished())
                .collect(Collectors.toList());
    }

    private WorkInfo singleActiveWork(String workName) throws Exception {
        List<WorkInfo> active = activeWork(workName);
        assertEquals(1, active.size());
        return active.get(0);
    }
}
