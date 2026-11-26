package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.inspector.WindowInspector;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class SourcesCategorySwitchInstrumentedTest {
    private static final String ADS_A = "Ads A", ADS_B = "Ads B";
    private static final String PRIVACY_A = "Privacy A", PRIVACY_B = "Privacy B";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final ExecutorService roomReads = Executors.newSingleThreadExecutor();
    private Context context;
    private Field singleton;
    private AppDatabase database, previous;
    private String previousProfile, ads, privacy, custom;
    private ActivityScenario<HomeActivity> scenario;
    private HomeActivity home;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "category switch setup");
        previousProfile = FilterSetStore.getActiveProfile(context);
        drainDisk();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .setQueryExecutor(roomReads).build();
        seed(11, ADS_A, "https://adaway.org/hosts.txt", true, 7);
        seed(12, ADS_B, "https://hosts.oisd.nl/", false, 11);
        seed(21, PRIVACY_A, "https://v.firebog.net/hosts/Easyprivacy.txt", true, 2);
        seed(22, PRIVACY_B, "https://s3.amazonaws.com/lists.disconnect.me/simple_tracking.txt", false, 3);
        singleton = AppDatabase.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        previous = (AppDatabase) singleton.get(null);
        singleton.set(null, database);
        ads = context.getString(R.string.filter_category_ads);
        privacy = context.getString(R.string.filter_category_privacy);
        custom = context.getString(R.string.filter_category_custom);
    }

    private void openSources() {
        scenario = ActivityScenario.launch(HomeActivity.class);
        scenario.onActivity(activity -> {
            home = activity;
            home.navigateTo(R.id.nav_sources);
        });
        expectRows(ads, ADS_A, ADS_B, privacy, custom);
    }

    @After
    public void closeSources() throws Exception {
        try {
            if (scenario != null) {
                if (home != null) instrumentation.runOnMainSync(home::finish);
                scenario.close();
            }
            drainDisk();
        } finally {
            if (singleton != null) singleton.set(null, previous);
            if (database != null) database.close();
            roomReads.shutdown();
            if (previousProfile != null) FilterSetStore.setActiveProfile(context, previousProfile);
        }
    }

    @Test
    public void mixedMasterChangesOnlyPrivacyWhileCollapsedAndExpanded() throws Exception {
        openSources();
        expectGroup(privacy, 1, 2, false);
        click(privacy, R.id.categoryCard);
        expectRows(ads, ADS_A, ADS_B, privacy, PRIVACY_A, PRIVACY_B, custom);
        expectGroup(privacy, 1, 2, true);
        expectSource(PRIVACY_A, true);
        expectSource(PRIVACY_B, false);
        click(privacy, R.id.categoryCard);
        expectRows(ads, ADS_A, ADS_B, privacy, custom);
        expectGroup(privacy, 1, 2, false);
        expectSelection(true, false);

        click(privacy, R.id.categorySwitch);
        expectSelection(true, true);
        expectRows(ads, ADS_A, ADS_B, privacy, custom);
        expectGroup(privacy, 2, 5, false);
        expectApply();
        click(privacy, R.id.categoryCard);
        expectSource(PRIVACY_A, true);
        expectSource(PRIVACY_B, true);

        click(privacy, R.id.categorySwitch);
        expectSelection(false, false);
        expectGroup(privacy, 0, 0, true);
        expectSource(PRIVACY_A, false);
        expectSource(PRIVACY_B, false);
        expectApply();
        click(privacy, R.id.categorySwitch);
        expectSelection(true, true);
        expectGroup(privacy, 2, 5, true);
        expectSource(PRIVACY_A, true);
        expectSource(PRIVACY_B, true);
        expectGroup(ads, 1, 7, true);
        expectSource(ADS_A, true);
        expectSource(ADS_B, false);
    }

    @Test
    public void secondPrivacyWriteFailureRollsBackSourcesAndRulesAndMasterRetries() throws Exception {
        database.hostsSourceDao().setSourceEnabled(21, false);
        seedStoredRules();
        openSources();
        click(privacy, R.id.categoryCard);
        expectGroup(privacy, 0, 0, true);
        expectSourcesAndRules(false, false);

        installSecondPrivacyFailure();
        try {
            click(privacy, R.id.categorySwitch);
            drainDisk();
            expectSourcesAndRules(false, false);
            awaitUi(this::assertInlineFailure);
            expectGroup(privacy, 0, 0, true);
            expectSource(PRIVACY_A, false);
            expectSource(PRIVACY_B, false);
            expectGroup(ads, 1, 7, true);
        } finally {
            database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER fail_second_privacy");
        }

        click(privacy, R.id.categorySwitch);
        drainDisk();
        expectSourcesAndRules(true, true);
        expectGroup(privacy, 2, 5, true);
        expectSource(PRIVACY_A, true);
        expectSource(PRIVACY_B, true);
        expectSource(ADS_A, true);
        expectSource(ADS_B, false);
        expectApply();
    }

    @Test
    public void failedPrivacyThenSuccessfulAdsKeepsInlineFailureAndApply() throws Exception {
        database.hostsSourceDao().setSourceEnabled(21, false);
        seedStoredRules();
        openSources();
        click(ads, R.id.categoryCard);
        expectRows(ads, privacy, custom);
        expectGroup(privacy, 0, 0, false);
        expectGroup(ads, 1, 7, false);

        installSecondPrivacyFailure();
        try {
            clickBeforeDelivery(privacy, R.id.categorySwitch, ads, R.id.categorySwitch);
        } finally {
            database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER fail_second_privacy");
        }
        expectSourcesAndRules(true, false, false);
        // Check the touched master before scrolling could fix it by recycling its holder.
        awaitUi(() -> {
            assertFalse("Rolled-back Privacy still checked", shownSwitch(privacy, R.id.categorySwitch).isChecked());
            assertTrue("Ads master did not reflect its success", shownSwitch(ads, R.id.categorySwitch).isChecked());
            assertInlineFailure();
            assertApplyVisible();
        });
        expectGroup(privacy, 0, 0, false);
        expectGroup(ads, 2, 18, false);

        database.runInTransaction(() -> {
            for (int id : new int[]{21, 22}) {
                database.hostsSourceDao().delete(database.hostsSourceDao().getById(id).orElseThrow());
            }
        });
        awaitUi(() -> {
            RecyclerView list = home.findViewById(R.id.hosts_sources_list);
            assertEquals("Only the Ads and Custom headers should remain", 2, list.getAdapter().getItemCount());
            TextView error = inlineSelectionError();
            assertFalse("Deleted Privacy sources still show a selection error", error != null && error.isShown());
            assertTrue("Ads must stay enabled", shownSwitch(ads, R.id.categorySwitch).isChecked());
        });
        assertSavedSelection(true, false, false);
        for (int id : new int[]{11, 12}) {
            HostListItem rule = database.hostsListItemDao().getById(id);
            assertNotNull("Missing Ads rule " + id, rule);
            assertTrue("Ads rule was disabled: " + id, rule.isEnabled());
        }
    }

    @Test
    public void equalHostMembershipSwapAfterMasterAndChildClearsMaster() throws Exception {
        HostsSource b = database.hostsSourceDao().getById(22).orElseThrow();
        b.setSize(2);
        database.hostsSourceDao().update(b);
        seedStoredRules();
        openSources();
        click(ads, R.id.categoryCard);
        click(privacy, R.id.categoryCard);
        expectGroup(privacy, 1, 2, true);
        expectSource(PRIVACY_A, true);
        expectSource(PRIVACY_B, false);

        // Initially A alone contributes two hosts; finally B alone contributes two.
        clickBeforeDelivery(privacy, R.id.categorySwitch, PRIVACY_A, R.id.sourceSwitch);
        expectSourcesAndRules(false, true);
        awaitUi(() -> {
            assertApplyVisible();
            assertFalse("1 of 2 is not all enabled", shownSwitch(privacy, R.id.categorySwitch).isChecked());
        });
        expectSource(PRIVACY_A, false);
        expectSource(PRIVACY_B, true);
        expectGroup(privacy, 1, 2, true);
    }

    private void installSecondPrivacyFailure() {
        // Fire only after the sibling row has changed, regardless of Room's write order.
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER fail_second_privacy BEFORE UPDATE OF enabled ON hosts_sources "
                        + "WHEN OLD.id IN (21,22) AND OLD.enabled = 0 AND NEW.enabled = 1 "
                        + "AND EXISTS (SELECT 1 FROM hosts_sources WHERE id IN (21,22) "
                        + "AND id != OLD.id AND enabled = 1) "
                        + "BEGIN SELECT RAISE(ABORT, 'second Privacy source write'); END");
    }

    private void clickBeforeDelivery(String first, int firstId, String second, int secondId)
            throws Exception {
        List<String> labels = rows();
        int anchor = Math.min(labels.indexOf(first), labels.indexOf(second));
        assertTrue("Missing tap target in " + labels, anchor >= 0);
        instrumentation.runOnMainSync(() -> {
            RecyclerView list = home.findViewById(R.id.hosts_sources_list);
            ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(anchor, 0);
        });
        awaitUi(() -> {
            RecyclerView list = home.findViewById(R.id.hosts_sources_list);
            assertFalse(list.isLayoutRequested() || list.hasPendingAdapterUpdates() || list.isAnimating());
            shownSwitch(first, firstId);
            shownSwitch(second, secondId);
        });

        // Room must not read the intermediate selection. The writes themselves still run
        // through the app's normal executor, DAO and separate transactions.
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        Future<Boolean> gate = roomReads.submit(() -> {
            paused.countDown();
            return resume.await(10, TimeUnit.SECONDS);
        });
        try {
            assertTrue("Room reads did not pause", paused.await(5, TimeUnit.SECONDS));
            FutureTask<Void> taps = new FutureTask<>(() -> {
                CompoundButton one = shownSwitch(first, firstId);
                CompoundButton two = shownSwitch(second, secondId);
                assertTrue(first, one.isEnabled());
                assertTrue(second, two.isEnabled());
                one.performClick();
                // CompoundButton can change state via its checked listener without a click listener.
                two.performClick();
                // Do not yield the main looper between the failure/success completions.
                CountDownLatch written = new CountDownLatch(1);
                AppExecutors.getInstance().diskIO().execute(written::countDown);
                assertTrue("Both writes must finish in this UI turn", written.await(3, TimeUnit.SECONDS));
                return null;
            });
            instrumentation.runOnMainSync(taps);
            taps.get();
        } finally {
            resume.countDown();
            assertTrue("Room read gate expired", gate.get(5, TimeUnit.SECONDS));
        }
    }

    private CompoundButton shownSwitch(String label, int id) {
        int description = id == R.id.categorySwitch ? R.string.filter_category_toggle_description
                : R.string.filter_source_toggle_description;
        String expected = home.getString(description, label);
        RecyclerView list = home.findViewById(R.id.hosts_sources_list);
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            if (list.getChildAdapterPosition(row) == RecyclerView.NO_POSITION) continue;
            CompoundButton toggle = row.findViewById(id);
            if (toggle != null && expected.contentEquals(toggle.getContentDescription())
                    && toggle.getGlobalVisibleRect(new Rect()) && toggle.isShown()) return toggle;
        }
        throw new AssertionError("No visible switch: " + expected);
    }

    @Test
    public void rapidOpposingPrivacyMasterTapsPersistLastIntent() throws Exception {
        seedStoredRules();
        openSources();
        click(privacy, R.id.categoryCard);
        expectGroup(privacy, 1, 2, true);
        AtomicReference<CompoundButton> master = new AtomicReference<>();
        withRow(privacy, row -> master.set(row.findViewById(R.id.categorySwitch)));

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(() -> {
            entered.countDown();
            try {
                release.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exited.countDown();
            }
        });
        try {
            assertTrue("Disk gate did not start", entered.await(10, TimeUnit.SECONDS));
            boolean[] tapped = new boolean[2];
            instrumentation.runOnMainSync(() -> {
                CompoundButton toggle = master.get();
                tapped[0] = toggle.isShown() && toggle.isEnabled() && !toggle.isChecked()
                        && toggle.performClick();
                tapped[1] = toggle.isShown() && toggle.isEnabled() && toggle.isChecked()
                        && toggle.performClick();
            });
            assertTrue("Could not turn mixed Privacy on", tapped[0]);
            assertTrue("Could not turn Privacy back off before the first write", tapped[1]);
            expectSourcesAndRules(true, false);
            assertEquals("Disk gate expired before both taps", 1L, exited.getCount());
        } finally {
            release.countDown();
        }
        drainDisk();
        expectSourcesAndRules(false, false);
        expectGroup(privacy, 0, 0, true);
        expectSource(PRIVACY_A, false);
        expectSource(PRIVACY_B, false);
        expectGroup(ads, 1, 7, true);
        expectApply();
    }

    @Test
    public void collapsedAdsSurvivesRecreationAndHomeTabReturn() throws Exception {
        openSources();
        click(ads, R.id.categoryCard);
        expectRows(ads, privacy, custom);
        expectGroup(ads, 1, 7, false);

        List<String> reopened = new ArrayList<>();
        scenario.recreate();
        scenario.onActivity(activity -> home = activity);
        recordReopening("activity recreation", reopened);

        tapTab(R.id.nav_home);
        awaitUi(() -> assertTrue("Sources should be gone on Home",
                home.findViewById(R.id.hosts_sources_list) == null));
        tapTab(R.id.nav_sources);
        recordReopening("Home -> Sources", reopened);

        click(privacy, R.id.categoryCard);
        expectRows(ads, privacy, PRIVACY_A, PRIVACY_B, custom);
        click(privacy, R.id.categorySwitch);
        expectSelection(true, true);
        expectGroup(ads, 1, 7, false);
        expectGroup(privacy, 2, 5, true);
        expectSource(PRIVACY_A, true);
        expectSource(PRIVACY_B, true);
        expectApply();
        assertTrue("User-collapsed Ads reopened after: " + reopened, reopened.isEmpty());
    }

    private void recordReopening(String boundary, List<String> reopened) {
        // Record both boundaries on an unfixed build, independently starting each collapsed.
        if (rows().contains(ADS_A)) {
            reopened.add(boundary);
            expectRows(ads, ADS_A, ADS_B, privacy, custom);
            click(ads, R.id.categoryCard);
        }
        expectRows(ads, privacy, custom);
        expectGroup(ads, 1, 7, false);
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    public void emptyCustomSwitchIsInertAndHeaderOpensAddOptions() throws Exception {
        openSources();
        int[] point = new int[2];
        withRow(custom, row -> {
            CompoundButton toggle = row.findViewById(R.id.categorySwitch);
            expectEmpty(row, toggle);
            Rect bounds = new Rect();
            if (!toggle.getLocalVisibleRect(bounds)) {
                RecyclerView list = home.findViewById(R.id.hosts_sources_list);
                list.scrollToPosition(list.getAdapter().getItemCount() - 1);
                throw new AssertionError("Waiting for visible Custom switch");
            }
            toggle.getLocationOnScreen(point);
            point[0] += bounds.centerX();
            point[1] += bounds.centerY();
        });
        // performClick() bypasses Android's disabled touch handling.
        long down = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    point[0], point[1], 0);
            try {
                instrumentation.sendPointerSync(event);
            } finally {
                event.recycle();
            }
        }
        instrumentation.waitForIdleSync();
        withRow(custom, row -> {
            expectEmpty(row, row.findViewById(R.id.categorySwitch));
            assertFalse("Disabled switch opened the sheet", addSheetVisible());
        });
        click(custom, R.id.categoryCard);
        awaitUi(() -> assertTrue("Expected both add-source options", addSheetVisible()));
        expectSelection(true, false);
    }

    private void expectEmpty(View row, CompoundButton toggle) {
        assertFalse(toggle.isEnabled());
        assertFalse(toggle.isChecked());
        assertEquals(context.getString(R.string.filter_category_state_empty),
                ViewCompat.getStateDescription(toggle));
        assertEquals(context.getString(R.string.filter_catalog_subtitle), text(row, R.id.categorySummary));
    }

    @androidx.annotation.RequiresApi(29)
    private static boolean addSheetVisible() {
        for (View root : WindowInspector.getGlobalWindowViews()) {
            View browse = root.findViewById(R.id.browseCatalogOption);
            View add = root.findViewById(R.id.addCustomOption);
            if (browse != null && browse.isShown() && add != null && add.isShown()) return true;
        }
        return false;
    }

    private void expectGroup(String label, int enabled, int hosts, boolean expanded) {
        withRow(label, row -> {
            CompoundButton toggle = row.findViewById(R.id.categorySwitch);
            assertTrue(toggle.isEnabled());
            assertEquals(context.getString(R.string.filter_category_toggle_description, label),
                    toggle.getContentDescription());
            assertEquals(enabled == 2, toggle.isChecked());
            String state = enabled == 1 ? context.getResources().getQuantityString(
                    R.plurals.filter_category_state_some_enabled, 1, 1, 2) : context.getString(
                    enabled == 2 ? R.string.filter_category_state_all_enabled : R.string.filter_category_state_disabled);
            assertEquals(state, ViewCompat.getStateDescription(toggle));
            String summary = enabled == 0 ? context.getString(R.string.filter_category_summary_disabled)
                    : context.getResources().getQuantityString(R.plurals.filter_category_summary_enabled,
                            hosts, enabled, 2, String.valueOf(hosts));
            assertEquals(summary, text(row, R.id.categorySummary));
            View indicator = row.findViewById(R.id.expandIndicator);
            assertTrue(indicator.isShown());
            assertEquals(expanded ? 180f : 0f, indicator.getRotation(), 0.01f);
        });
    }

    private void expectSource(String label, boolean enabled) {
        withRow(label, row -> {
            CompoundButton toggle = row.findViewById(R.id.sourceSwitch);
            assertTrue(toggle.isShown());
            assertEquals(context.getString(R.string.filter_source_toggle_description, label),
                    toggle.getContentDescription());
            assertEquals(label, enabled, toggle.isChecked());
        });
    }

    private void expectApply() {
        awaitUi(this::assertApplyVisible);
    }

    private void assertApplyVisible() {
        int message = com.google.android.material.R.id.snackbar_text;
        int action = com.google.android.material.R.id.snackbar_action;
        View root = home.getWindow().getDecorView();
        assertEquals(context.getString(R.string.notification_configuration_changed), text(root, message));
        assertEquals(context.getString(R.string.notification_configuration_changed_action), text(root, action));
        assertTrue(root.findViewById(message).isShown() && root.findViewById(action).isShown());
    }

    private void assertInlineFailure() {
        // Runtime IDs let the same regression compile against a build without the inline view.
        int message = home.getResources().getIdentifier(
                "filter_category_update_failed", "string", home.getPackageName());
        assertTrue("Missing localized failure message", message != 0);
        TextView error = inlineSelectionError();
        assertNotNull("Missing inline failure view", error);
        assertTrue("Inline failure is not visible", error.isShown() && error.getGlobalVisibleRect(new Rect()));
        assertEquals(home.getString(message, privacy), error.getText().toString());
    }

    private TextView inlineSelectionError() {
        int id = home.getResources().getIdentifier("source_selection_error", "id", home.getPackageName());
        assertTrue("Missing source_selection_error resource", id != 0);
        return home.findViewById(id);
    }

    private void expectSelection(boolean privacyA, boolean privacyB) throws InterruptedException {
        drainDisk(); // The master updates the view optimistically; require the real saved choices too.
        assertSavedSelection(false, privacyA, privacyB);
    }

    private void assertSavedSelection(boolean adsB, boolean privacyA, boolean privacyB) {
        List<String> enabled = new ArrayList<>();
        for (HostsSource source : database.hostsSourceDao().getAll()) {
            if (source.isEnabled()) enabled.add(source.getLabel());
        }
        List<String> expected = new ArrayList<>();
        expected.add(ADS_A);
        if (adsB) expected.add(ADS_B);
        if (privacyA) expected.add(PRIVACY_A);
        if (privacyB) expected.add(PRIVACY_B);
        assertEquals("Saved enabled sources, including unrelated Ads", expected, enabled);
    }

    private void seedStoredRules() {
        for (HostsSource source : database.hostsSourceDao().getAll()) {
            HostListItem rule = new HostListItem();
            rule.setId(source.getId());
            rule.setSourceId(source.getId());
            rule.setHost("category-" + source.getId() + ".invalid");
            rule.setType(ListType.BLOCKED);
            rule.setEnabled(source.isEnabled());
            database.hostsListItemDao().insert(rule);
        }
    }

    private void expectSourcesAndRules(boolean privacyA, boolean privacyB) {
        expectSourcesAndRules(false, privacyA, privacyB);
    }

    private void expectSourcesAndRules(boolean adsB, boolean privacyA, boolean privacyB) {
        assertSavedSelection(adsB, privacyA, privacyB);
        int[] ids = {11, 12, 21, 22};
        boolean[] states = {true, adsB, privacyA, privacyB};
        for (int i = 0; i < ids.length; i++) {
            HostListItem rule = database.hostsListItemDao().getById(ids[i]);
            assertNotNull("Missing stored rule for source " + ids[i], rule);
            assertEquals(ids[i], rule.getSourceId());
            assertEquals("Stored rule for source " + ids[i], states[i], rule.isEnabled());
        }
    }

    private void seed(int id, String label, String url, boolean enabled, int size) {
        HostsSource source = new HostsSource();
        source.setId(id);
        source.setLabel(label);
        source.setUrl(url);
        source.setEnabled(enabled);
        source.setSize(size);
        database.hostsSourceDao().insert(source);
    }

    private void click(String label, int id) {
        withRow(label, row -> {
            View target = row.findViewById(id);
            assertNotNull(label, target);
            assertTrue(label, target.isEnabled() && target.isShown());
            assertTrue(label, target.performClick());
        });
    }

    private void tapTab(int id) {
        awaitUi(() -> {
            View tab = home.findViewById(id);
            assertNotNull(tab);
            assertTrue(tab.isShown() && tab.performClick());
        });
    }

    private void expectRows(String... labels) {
        assertEquals(Arrays.asList(labels), rows());
    }

    private List<String> rows() {
        int[] count = new int[1];
        awaitUi(() -> {
            RecyclerView list = home.findViewById(R.id.hosts_sources_list);
            assertNotNull("Sources list", list);
            assertNotNull(list.getAdapter());
            count[0] = list.getAdapter().getItemCount();
            assertTrue("Waiting for all three fixture categories", count[0] >= 3);
        });
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < count[0]; i++) atPosition(i, row -> labels.add(labelOf(row)));
        return labels;
    }

    private void withRow(String label, Consumer<View> action) {
        List<String> labels = rows();
        int position = labels.indexOf(label);
        assertTrue("Missing " + label + " in " + labels, position >= 0);
        atPosition(position, row -> {
            assertEquals(label, labelOf(row));
            action.accept(row);
        });
    }

    private static String labelOf(View row) {
        return text(row, row.getId() == R.id.categoryCard ? R.id.categoryTitle : R.id.sourceLabel);
    }

    private static String text(View row, int id) {
        TextView view = row.findViewById(id);
        assertNotNull("Missing text view " + id, view);
        return view.getText().toString();
    }

    private void atPosition(int position, Consumer<View> action) {
        // Inspect actual attached rows, including ones initially outside the viewport.
        awaitUi(() -> {
            RecyclerView list = home.findViewById(R.id.hosts_sources_list);
            assertNotNull(list);
            assertFalse("List is updating", list.hasPendingAdapterUpdates() || list.isAnimating());
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
            if (holder == null || !holder.itemView.getGlobalVisibleRect(new Rect())) {
                ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(position, 0);
                throw new AssertionError("Waiting for row " + position);
            }
            action.accept(holder.itemView);
        });
    }

    private void awaitUi(Runnable check) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        AtomicReference<AssertionError> failure = new AtomicReference<>();
        do {
            instrumentation.runOnMainSync(() -> {
                try {
                    check.run();
                    failure.set(null);
                } catch (AssertionError error) {
                    failure.set(error);
                }
            });
            if (failure.get() == null) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        throw failure.get();
    }

    private static void drainDisk() throws InterruptedException {
        CountDownLatch drained = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(drained::countDown);
        assertTrue("Source writes did not finish", drained.await(10, TimeUnit.SECONDS));
    }
}
