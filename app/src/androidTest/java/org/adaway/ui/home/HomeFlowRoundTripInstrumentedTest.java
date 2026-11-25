package org.adaway.ui.home;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.helper.PreferenceHelper;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.lists.ListsActivity;
import org.adaway.ui.lists.type.AllowedHostsFragment;
import org.adaway.ui.lists.type.BlockedHostsFragment;
import org.adaway.ui.lists.type.RedirectedHostsFragment;
import org.adaway.util.AppExecutors;
import org.adaway.util.Constants;
import org.adaway.vpn.VpnServiceControls;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Actual Home controls and unblocked Android settings; no injected navigation callbacks. */
@RunWith(AndroidJUnit4.class)
public class HomeFlowRoundTripInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000;
    private static final String DNS_ACTION = "android.settings.PRIVATE_DNS_SETTINGS";
    private static final String DNS_MODE = "private_dns_mode";
    private static final String DNS_SPECIFIER = "private_dns_specifier";
    private static final String PROVIDER = "dns.example";
    private static final int[] CARDS = {R.id.blockedHostCardView,
            R.id.allowedHostCardView, R.id.redirectHostCardView};
    private static final int[] COUNTERS = {R.id.blockedHostCounterTextView,
            R.id.allowedHostCounterTextView, R.id.redirectHostCounterTextView};
    private static final int[] TABS = {ListsActivity.BLOCKED_HOSTS_TAB,
            ListsActivity.ALLOWED_HOSTS_TAB, ListsActivity.REDIRECTED_HOSTS_TAB};
    private static final int[] NAVIGATION = {R.id.lists_navigation_blocked,
            R.id.lists_navigation_allowed, R.id.lists_navigation_redirected};
    private static final Class<?>[] FRAGMENTS = {BlockedHostsFragment.class,
            AllowedHostsFragment.class, RedirectedHostsFragment.class};
    private static final String[] HOSTS = {"blocked.home-flow.invalid",
            "allowed.home-flow.invalid", "redirect.home-flow.invalid"};

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Context context;
    private AppDatabase database;
    private AppDatabase previousDatabase;
    private ActivityScenario<HomeActivity> scenario;
    private Instrumentation.ActivityMonitor intentMonitor;
    private boolean databaseSwapped;
    private boolean resetStarted;
    private boolean dnsTouched;
    private String originalMode;
    private String originalSpecifier;
    private String settingsPackage;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        assumeFalse("Home round trips require a disposable device with no active VPN",
                hasVpnNetwork());
        resetStarted = true;
        InstrumentedTestState.resetForPassiveRootUi(context, "Home flow setup");
        drainDisk();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        database.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT OR REPLACE INTO hosts_meta (id, active_generation) VALUES (0, 0)");
        HostsSource user = new HostsSource();
        user.setId(USER_SOURCE_ID);
        user.setLabel("User");
        user.setUrl(USER_SOURCE_URL);
        database.hostsSourceDao().insert(user);
        database.hostEntryDao().sync();
        Field singleton = databaseField();
        previousDatabase = (AppDatabase) singleton.get(null);
        singleton.set(null, database);
        databaseSwapped = true;
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (intentMonitor != null) instrumentation.removeMonitor(intentMonitor);
            for (int attempt = 0; attempt < 3 && settingsPackage != null
                    && settingsPackage.equals(foregroundPackage()); attempt++) {
                pressBack();
                SystemClock.sleep(150);
            }
            // A failed destination assertion must not leave Lists on top of the next test.
            instrumentation.runOnMainSync(() -> {
                for (Stage stage : Stage.values()) {
                    for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry
                            .getInstance().getActivitiesInStage(stage))) {
                        if (activity instanceof ListsActivity) activity.finish();
                    }
                }
            });
            if (scenario != null) scenario.close();
        } finally {
            try {
                if (dnsTouched) {
                    try {
                        putGlobal(DNS_SPECIFIER, originalSpecifier);
                    } finally {
                        putGlobal(DNS_MODE, originalMode);
                    }
                }
            } finally {
                try {
                    if (resetStarted) {
                        InstrumentedTestState.resetForPassiveRootUi(context, "Home flow cleanup");
                        drainDisk();
                    }
                } finally {
                    if (databaseSwapped) databaseField().set(null, previousDatabase);
                    if (database != null) database.close();
                }
            }
        }
    }

    @Test(timeout = 120_000)
    public void emptyCounterCardsOpenTheirRuleTabsAndBackReturnsToRecreatedHome() {
        exerciseCounterCards(false);
    }

    @Test(timeout = 120_000)
    public void populatedCounterCardsKeepCorrectRulesAcrossListAndHomeRecreation() {
        ListType[] types = {ListType.BLOCKED, ListType.ALLOWED, ListType.REDIRECTED};
        for (int i = 0; i < types.length; i++) {
            HostListItem item = new HostListItem();
            item.setHost(HOSTS[i]);
            item.setType(types[i]);
            item.setSourceId(USER_SOURCE_ID);
            item.setEnabled(true);
            item.setGeneration(0);
            if (types[i] == ListType.REDIRECTED) item.setRedirection("203.0.113.42");
            database.hostsListItemDao().insert(item);
        }
        database.hostEntryDao().sync();
        exerciseCounterCards(true);
    }

    @Test(timeout = 90_000)
    public void privateDnsButtonLaunchesAndroidSettingsAndBackRefreshesRealGlobalState() {
        prepareLeakSettings();
        scenario = ActivityScenario.launch(HomeActivity.class);
        waitForText(R.id.leakStatusDetailTextView,
                context.getString(R.string.leak_status_private_dns_provider, PROVIDER));
        scenario.recreate();
        List<Intent> launched = watchIntents(DNS_ACTION, Settings.ACTION_WIRELESS_SETTINGS);
        tapHome(R.id.leakPrivateDnsButton);
        waitForSettings(DNS_ACTION, launched);
        putGlobal(DNS_MODE, LeakStatus.PRIVATE_DNS_MODE_OFF);
        assertEquals(LeakStatus.PRIVATE_DNS_MODE_OFF, global(DNS_MODE));
        pressBack();
        waitForResumed(HomeActivity.class, null);
        assertHomeSelected();
        waitForText(R.id.leakStatusDetailTextView,
                context.getString(R.string.leak_status_private_dns_off));
        waitForText(R.id.leakStatusSummaryTextView, context.getResources().getQuantityString(
                R.plurals.leak_status_summary_risky, 2, 2));
        scenario.onActivity(activity -> assertEquals(View.GONE,
                activity.findViewById(R.id.leakPrivateDnsButton).getVisibility()));
        scenario.recreate();
        waitForText(R.id.leakStatusDetailTextView,
                context.getString(R.string.leak_status_private_dns_off));
        assertFalse("Settings round trip must not start VPN", VpnServiceControls.isRunning(context));
    }

    @Test(timeout = 90_000)
    public void vpnSettingsButtonReturnsWithUpdatedExclusionStateAndNoVpnStart() {
        prepareLeakSettings();
        PreferenceHelper.setVpnExcludedApps(context, Collections.emptySet());
        scenario = ActivityScenario.launch(HomeActivity.class);
        waitForText(R.id.leakStatusDetailTextView,
                context.getString(R.string.leak_status_mode_vpn_stopped));
        scenario.recreate();
        List<Intent> launched = watchIntents(Settings.ACTION_VPN_SETTINGS);
        tapHome(R.id.leakVpnSettingsButton);
        waitForSettings(Settings.ACTION_VPN_SETTINGS, launched);
        // Real persisted app preference changed while Home is paused; not a settings toggle claim.
        PreferenceHelper.setVpnExcludedApps(context, Collections.singleton(context.getPackageName()));
        pressBack();
        waitForResumed(HomeActivity.class, null);
        assertHomeSelected();
        String excluded = context.getResources().getQuantityString(
                R.plurals.leak_status_excluded_user_apps, 1, 1);
        waitForText(R.id.leakStatusDetailTextView, excluded);
        waitForText(R.id.leakStatusSummaryTextView, context.getResources().getQuantityString(
                R.plurals.leak_status_summary_risky, 4, 4));
        waitForText(R.id.leakStatusDetailTextView,
                context.getString(R.string.leak_status_mode_vpn_stopped));
        scenario.recreate();
        waitForText(R.id.leakStatusDetailTextView, excluded);
        assertFalse("Opening VPN settings must not start protection", hasVpnNetwork());
        assertFalse(VpnServiceControls.isRunning(context));
    }

    private void exerciseCounterCards(boolean populated) {
        scenario = ActivityScenario.launch(HomeActivity.class);
        for (int i = 0; i < CARDS.length; i++) {
            int index = i;
            waitForText(COUNTERS[i], populated ? "1" : "0", true);
            HomeActivity home = waitForResumed(HomeActivity.class, null);
            tapHome(CARDS[i]);
            ListsActivity list = waitForResumed(ListsActivity.class, null);
            assertList(index, populated);
            instrumentation.runOnMainSync(list::recreate);
            waitForResumed(ListsActivity.class, list);
            assertList(index, populated);
            pressBack();
            assertEquals("Back must return to the same Home instance", home,
                    waitForResumed(HomeActivity.class, null));
            assertHomeSelected();
            scenario.recreate();
            assertHomeSelected();
        }
        assertEquals("Navigation must not mutate user rules", populated ? 3 : 0,
                database.hostsListItemDao().getAll().size());
    }

    private void assertList(int index, boolean populated) {
        await("requested tab and its content", () -> {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            instrumentation.runOnMainSync(() -> {
                ListsActivity activity = resumed(ListsActivity.class, null);
                if (activity == null) return;
                assertEquals(TABS[index], activity.getIntent().getIntExtra(ListsActivity.TAB, -1));
                if (((ViewPager2) activity.findViewById(R.id.lists_view_pager))
                        .getCurrentItem() != TABS[index]) return;
                if (((BottomNavigationView) activity.findViewById(R.id.navigation))
                        .getSelectedItemId() != NAVIGATION[index]) return;
                Fragment fragment = activity.getSupportFragmentManager()
                        .findFragmentByTag("f" + TABS[index]);
                if (!FRAGMENTS[index].isInstance(fragment) || fragment.getView() == null) return;
                View root = fragment.getView();
                if (populated) {
                    if (!containsText(root, HOSTS[index])) return;
                    for (int other = 0; other < HOSTS.length; other++) {
                        if (other != index) assertFalse(containsText(root, HOSTS[other]));
                    }
                    if (index == 2 && !containsText(root, "203.0.113.42")) return;
                } else if (!containsText(root,
                        context.getString(R.string.lists_state_no_rules_title))) return;
                ready.set(true);
            });
            return ready.get();
        });
    }

    private void prepareLeakSettings() {
        assumeTrue("Real settings writes require API 29+ shell permission delegation",
                Build.VERSION.SDK_INT >= 29);
        originalMode = global(DNS_MODE);
        originalSpecifier = global(DNS_SPECIFIER);
        dnsTouched = true;
        putGlobal(DNS_SPECIFIER, PROVIDER);
        putGlobal(DNS_MODE, LeakStatus.PRIVATE_DNS_MODE_HOSTNAME);
        PreferenceHelper.setAbBlockMethod(context, VPN);
        PreferenceHelper.setVpnExcludedApps(context, Collections.emptySet());
        assertTrue(context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(context.getString(R.string.pref_vpn_allow_app_bypass_key), false)
                .putString(context.getString(R.string.pref_vpn_excluded_system_apps_key), "none")
                .commit());
        assertFalse("Fixture requires stopped AdAway VPN", VpnServiceControls.isRunning(context));
    }

    private List<Intent> watchIntents(String... actions) {
        List<Intent> observed = new CopyOnWriteArrayList<>();
        intentMonitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                for (String action : actions) {
                    if (action.equals(intent.getAction())) {
                        observed.add(new Intent(intent));
                        break;
                    }
                }
                return null; // Observe without blocking or substituting the real settings activity.
            }
        };
        instrumentation.addMonitor(intentMonitor);
        return observed;
    }

    private void waitForSettings(String action, List<Intent> observed) {
        await("outgoing Android settings intent", () -> !observed.isEmpty());
        await("resolved settings destination in foreground for " + action, () -> {
            List<Intent> attempts = new ArrayList<>(observed);
            assertEquals(action, attempts.get(0).getAction());
            boolean fallback = DNS_ACTION.equals(action) && attempts.size() == 2;
            assertTrue("Only Private DNS may retry once through wireless settings: " + attempts,
                    attempts.size() == 1 || fallback);
            Intent destination = attempts.get(attempts.size() - 1);
            assertEquals(fallback ? Settings.ACTION_WIRELESS_SETTINGS : action,
                    destination.getAction());
            // A monitor sees failed launches too; wait for the production fallback, not the first attempt.
            ResolveInfo resolved = context.getPackageManager().resolveActivity(destination, 0);
            if (resolved == null) return false;
            settingsPackage = resolved.activityInfo.packageName;
            assertFalse("Must leave AdAway for real Android settings",
                    context.getPackageName().equals(settingsPackage));
            return settingsPackage.equals(foregroundPackage());
        });
        System.out.println("Home settings route: " + action + " -> "
                + observed.get(observed.size() - 1).getAction() + "; foreground=" + settingsPackage);
    }

    private String foregroundPackage() {
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        try {
            return root.getPackageName() == null ? null : root.getPackageName().toString();
        } finally {
            root.recycle();
        }
    }

    private void tapHome(int id) {
        scenario.onActivity(activity -> {
            View view = activity.findViewById(id);
            assertNotNull(view);
            view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
        });
        Rect target = new Rect();
        scenario.onActivity(activity -> {
            View view = activity.findViewById(id);
            assertTrue("Control must be enabled and clickable", view.isEnabled() && view.isClickable());
            assertTrue("Control must be on screen", view.isShown() && view.getGlobalVisibleRect(target));
            assertTrue(target.width() > 0 && target.height() > 0);
        });
        long down = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    target.centerX(), target.centerY(), 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {
                assertTrue(instrumentation.getUiAutomation().injectInputEvent(event, true));
            } finally {
                event.recycle();
            }
        }
    }

    private void pressBack() {
        long down = SystemClock.uptimeMillis();
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            assertTrue(instrumentation.getUiAutomation().injectInputEvent(
                    new KeyEvent(down, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_BACK, 0), true));
        }
    }

    private void assertHomeSelected() {
        waitForResumed(HomeActivity.class, null);
        scenario.onActivity(activity -> assertEquals(R.id.nav_home,
                ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).getSelectedItemId()));
    }

    private void waitForText(int id, String expected) {
        waitForText(id, expected, false);
    }

    private void waitForText(int id, String expected, boolean exact) {
        await("Home text " + expected, () -> {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            scenario.onActivity(activity -> {
                TextView view = activity.findViewById(id);
                found.set(view != null && (exact
                        ? expected.contentEquals(view.getText())
                        : view.getText().toString().contains(expected)));
            });
            return found.get();
        });
    }

    private <T extends Activity> T waitForResumed(Class<T> type, Activity previous) {
        AtomicReference<T> result = new AtomicReference<>();
        await("resumed " + type.getSimpleName(), () -> {
            instrumentation.runOnMainSync(() -> result.set(resumed(type, previous)));
            return result.get() != null;
        });
        return result.get();
    }

    private static <T extends Activity> T resumed(Class<T> type, Activity previous) {
        for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)) {
            if (activity != previous && type.isInstance(activity)) return type.cast(activity);
        }
        return null;
    }

    private static boolean containsText(View view, String text) {
        if (view instanceof TextView && view.isShown()
                && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    private boolean hasVpnNetwork() {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        assertNotNull(manager);
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
        }
        return false;
    }

    private String global(String key) {
        return Settings.Global.getString(context.getContentResolver(), key);
    }

    private void putGlobal(String key, String value) {
        instrumentation.getUiAutomation().adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS");
        try {
            assertTrue("Write real global setting " + key,
                    Settings.Global.putString(context.getContentResolver(), key, value));
            assertEquals(value, global(key));
        } finally {
            instrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private static Field databaseField() throws Exception {
        Field field = AppDatabase.class.getDeclaredField("instance");
        field.setAccessible(true);
        return field;
    }

    private static void drainDisk() throws InterruptedException {
        CountDownLatch drained = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(drained::countDown);
        assertTrue(drained.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private static void await(String description, BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }
}
