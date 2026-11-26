package org.adaway.ui.home;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.lifecycle.MutableLiveData;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.source.FilterOperationState;
import org.adaway.model.source.SourceModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.util.AppExecutors;
import org.adaway.vpn.VpnStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** UI snapshot tests: no VPN consent, real tunnel, hosts writes, or network imports. */
@RunWith(AndroidJUnit4.class)
public class HomeProtectionStatusRegressionInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000;

    private Context context;
    private AdAwayApplication application;
    private TestAdBlockModel model;
    private AdBlockModel originalModel;
    private ContentValues originalStats;
    private boolean statsCaptured;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        this.application = (AdAwayApplication) this.context.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up Home rule status");
        Field modelField = field(AdAwayApplication.class, "adBlockModel");
        this.originalModel = (AdBlockModel) modelField.get(this.application);
        this.model = new TestAdBlockModel(this.context);
        modelField.set(this.application, this.model);
        PreferenceHelper.setAbBlockMethod(this.context, VPN);
        publishApplied(false);
        databaseWork(db -> {
            try (Cursor cursor = db.query("SELECT * FROM hosts_stats WHERE id = 0")) {
                if (cursor.moveToFirst()) {
                    this.originalStats = new ContentValues();
                    DatabaseUtils.cursorRowToContentValues(cursor, this.originalStats);
                }
            }
            this.statsCaptured = true;
        });
        snapshot(0, 0);
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (this.statsCaptured) {
                databaseWork(db -> {
                    db.delete("hosts_stats", "id = 0", new Object[0]);
                    if (this.originalStats != null) {
                        db.insert("hosts_stats", SQLiteDatabase.CONFLICT_REPLACE,
                                this.originalStats);
                    }
                });
            }
            if (this.application != null) {
                field(AdAwayApplication.class, "adBlockModel")
                        .set(this.application, this.originalModel);
            }
        } finally {
            if (this.context != null) {
                InstrumentedTestState.resetForPassiveRootUi(
                        this.context, "tear down Home rule status");
            }
        }
    }

    @Test
    public void visibleHeaderTracksWaitingWithoutClearingRequestOrClaimingMissingTunnel()
            throws Exception {
        snapshot(2, 2);
        publishApplied(true);
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            PreferenceHelper.setVpnServiceStatus(this.context, VpnStatus.WAITING_FOR_NETWORK);
            assertStatus(scenario, R.string.home_protection_status_vpn_waiting, true, false);
            scenario.recreate();
            assertStatus(scenario, R.string.home_protection_status_vpn_waiting, true, false);
            PreferenceHelper.setVpnServiceStatus(this.context, VpnStatus.RUNNING);
            assertStatus(scenario, R.string.home_protection_status_vpn_disconnected, true, false);
            publishApplied(false);
            assertStatus(scenario, R.string.home_protection_status_inactive, false, false);
            assertEquals(0, this.model.mutations.get());
        } finally {
            PreferenceHelper.setVpnServiceStatus(this.context, VpnStatus.STOPPED);
        }
    }

    @Test
    public void vpnWithZeroRulesStaysEnabledWithoutClaimingActiveProtection() throws Exception {
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            publishApplied(true);
            publishEstablished(scenario);
            assertStatus(scenario, R.string.home_protection_status_vpn_no_rules, true, false);
            snapshot(2, 2);
            assertStatus(scenario, R.string.home_protection_status_active, true, true);
            snapshot(0, 0);
            assertStatus(scenario, R.string.home_protection_status_vpn_no_rules, true, false);
            publishApplied(false);
            snapshot(2, 2);
            assertStatus(scenario, R.string.home_protection_status_inactive, false, false);
            assertEquals("Rendering must not apply or revert protection", 0,
                    this.model.mutations.get());
        }
    }

    @Test
    public void rootStatusUsesExactBlockingCountNotOtherRuleTypes() throws Exception {
        this.model.method = ROOT;
        PreferenceHelper.setAbBlockMethod(this.context, ROOT);
        snapshot(4, 0);
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            publishApplied(true);
            assertStatus(scenario, R.string.home_protection_status_no_rules, true, false);
            snapshot(4, 1);
            assertStatus(scenario, R.string.home_protection_status_active, true, true);
            snapshot(0, 0);
            assertStatus(scenario, R.string.home_protection_status_no_rules, true, false);
            assertEquals(0, this.model.mutations.get());
        }
    }

    @Test
    public void missingSnapshotIsCheckingRatherThanActiveOrEmpty() throws Exception {
        databaseWork(db -> db.delete("hosts_stats", "id = 0", new Object[0]));
        publishApplied(true);
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            publishEstablished(scenario);
            assertStatus(scenario, R.string.home_protection_status_vpn_checking_rules, true, false);
            snapshot(0, 0);
            assertStatus(scenario, R.string.home_protection_status_vpn_no_rules, true, false);
            snapshot(1, 1);
            assertStatus(scenario, R.string.home_protection_status_active, true, true);
        }
    }

    @Test
    public void statusFollowsCurrentSnapshotWhileDisplayCountersAreDetached() throws Exception {
        publishApplied(true);
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            publishEstablished(scenario);
            assertStatus(scenario, R.string.home_protection_status_vpn_no_rules, true, false);
            waitForText(scenario, R.id.blockedHostCounterTextView, "0");
            drainCounterRefresh();
            publishOperation(FilterOperationState.Phase.PARSE);
            drainCounterRefresh();
            scenario.onActivity(activity -> {
                HomeFragment fragment = home(activity);
                try {
                    assertFalse(field(HomeFragment.class, "hostCountersAttached")
                            .getBoolean(fragment));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            });

            snapshot(3, 3);
            assertStatus(scenario, R.string.home_protection_status_active, true, true);
            scenario.onActivity(activity -> assertEquals("0",
                    ((TextView) activity.findViewById(R.id.blockedHostCounterTextView))
                            .getText().toString()));
            snapshot(0, 0);
            assertStatus(scenario, R.string.home_protection_status_vpn_no_rules, true, false);
            snapshot(3, 3);
            assertStatus(scenario, R.string.home_protection_status_active, true, true);

            scenario.recreate();
            publishEstablished(scenario);
            assertStatus(scenario, R.string.home_protection_status_active, true, true);
            snapshot(0, 0);
            assertStatus(scenario, R.string.home_protection_status_vpn_no_rules, true, false);
            publishOperation(FilterOperationState.Phase.COMPLETE);
            waitForText(scenario, R.id.blockedHostCounterTextView, "0");
            publishOperation(FilterOperationState.Phase.IDLE);
            snapshot(2, 2);
            assertStatus(scenario, R.string.home_protection_status_active, true, true);
            waitForText(scenario, R.id.blockedHostCounterTextView, "2");
            assertEquals(0, this.model.mutations.get());
        }
    }

    @Test
    public void recreatingTheSameFragmentViewReattachesSnapshotAndCounterObservers()
            throws Exception {
        publishApplied(true);
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            publishEstablished(scenario);
            assertStatus(scenario, R.string.home_protection_status_vpn_no_rules, true, false);
            scenario.onActivity(activity -> {
                HomeFragment fragment = home(activity);
                activity.getSupportFragmentManager().beginTransaction()
                        .detach(fragment).commitNow();
                activity.getSupportFragmentManager().beginTransaction()
                        .attach(fragment).commitNow();
            });
            publishEstablished(scenario);
            snapshot(2, 2);
            assertStatus(scenario, R.string.home_protection_status_active, true, true);
            waitForText(scenario, R.id.blockedHostCounterTextView, "2");
            snapshot(0, 0);
            assertStatus(scenario, R.string.home_protection_status_vpn_no_rules, true, false);
            waitForText(scenario, R.id.blockedHostCounterTextView, "0");
        }
    }

    // These rule-count fixtures simulate an already-established tunnel, not a real VPN.
    private static void publishEstablished(ActivityScenario<HomeActivity> scenario) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> home(activity).onVpnTunnelObserved(true));
    }

    private void assertStatus(ActivityScenario<HomeActivity> scenario, int textId,
            boolean applied, boolean effective) {
        waitForText(scenario, R.id.protectionStatusTextView, this.context.getString(textId));
        scenario.onActivity(activity -> {
            MaterialSwitch control = activity.findViewById(R.id.protectionSwitch);
            assertNotNull(control);
            assertEquals("Switch follows applied state, not rule count",
                    applied, control.isChecked());
            assertEquals(applied, Boolean.TRUE.equals(this.model.isApplied().getValue()));
            TextView status = activity.findViewById(R.id.protectionStatusTextView);
            assertTrue("Empty-rule wording must be allowed to wrap", status.getMaxLines() > 1);
            int color = !applied ? R.color.home_protection_off_text
                    : effective ? R.color.home_protection_on_text : R.color.ui_text_primary;
            assertEquals(activity.getColor(color), status.getCurrentTextColor());
            assertTrue("Protection status must be readable over the Home background",
                    androidx.core.graphics.ColorUtils.calculateContrast(
                            status.getCurrentTextColor(), activity.getColor(R.color.ui_bg)) >= 4.5);
        });
    }

    private static HomeFragment home(HomeActivity activity) {
        HomeFragment fragment = (HomeFragment) activity.getSupportFragmentManager()
                .findFragmentByTag("home");
        assertNotNull(fragment);
        return fragment;
    }

    private void snapshot(int blocked, int exactBlocked) throws Exception {
        // Replay the tiny active-truth table consumed by Home, independently of import counters.
        databaseWork(db -> db.execSQL("INSERT OR REPLACE INTO hosts_stats "
                        + "(id, blocked_count, blocked_exact_count, allowed_count, "
                        + "redirected_count, active_rule_count) VALUES (0, ?, ?, 2, 1, ?)",
                new Object[] {blocked, exactBlocked, blocked + 3}));
    }

    private void databaseWork(DatabaseWork work) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                AppDatabase database = AppDatabase.getInstance(this.context);
                database.runInTransaction(() -> work.run(
                        database.getOpenHelper().getWritableDatabase()));
            }).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private void publishApplied(boolean applied) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> ((MutableLiveData<Boolean>) this.model.isApplied()).setValue(applied));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void drainCounterRefresh() throws InterruptedException {
        CountDownLatch drained = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(drained::countDown);
        assertTrue("Counter snapshot refresh did not settle",
                drained.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @SuppressWarnings("unchecked")
    private void publishOperation(FilterOperationState.Phase phase) throws Exception {
        Constructor<FilterOperationState> constructor = FilterOperationState.class
                .getDeclaredConstructor(FilterOperationState.Kind.class,
                        FilterOperationState.Phase.class, int.class, int.class, int.class,
                        int.class, int.class, long.class, String.class, String.class,
                        long.class, long.class, boolean.class, boolean.class);
        constructor.setAccessible(true);
        FilterOperationState state = phase == FilterOperationState.Phase.IDLE
                ? FilterOperationState.idle()
                : constructor.newInstance(FilterOperationState.Kind.SOURCE_UPDATE, phase,
                        2, 2, 2, 1, 70, 900L, "Status fixture", null, 1L, 2L, false, false);
        MutableLiveData<FilterOperationState> data = (MutableLiveData<FilterOperationState>)
                field(SourceModel.class, "filterOperationState")
                        .get(this.application.getSourceModel());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> data.setValue(state));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void waitForText(ActivityScenario<HomeActivity> scenario,
            int id, String expected) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        String[] actual = {""};
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                TextView text = activity.findViewById(id);
                actual[0] = text == null ? "" : text.getText().toString();
            });
            if (expected.equals(actual[0])) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Expected '" + expected + "', got '" + actual[0] + "'");
    }

    private interface DatabaseWork {
        void run(SupportSQLiteDatabase database);
    }

    private static final class TestAdBlockModel extends AdBlockModel {
        private AdBlockMethod method = VPN;
        private final AtomicInteger mutations = new AtomicInteger();

        private TestAdBlockModel(Context context) {
            super(context);
        }

        @Override public AdBlockMethod getMethod() { return this.method; }
        @Override public void apply() { this.mutations.incrementAndGet(); }
        @Override public void revert() { this.mutations.incrementAndGet(); }
        @Override public boolean isRecordingLogs() { return false; }
        @Override public void setRecordingLogs(boolean recording) { }
        @Override public List<String> getLogs() { return Collections.emptyList(); }
        @Override public void clearLogs() { }
    }
}
