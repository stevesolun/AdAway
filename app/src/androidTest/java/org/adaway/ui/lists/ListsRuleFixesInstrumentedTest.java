package org.adaway.ui.lists;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.ui.lists.ListsActivity.ALLOWED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.BLOCKED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.REDIRECTED_HOSTS_TAB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.ActionMode;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inspector.WindowInspector;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.viewpager2.widget.ViewPager2;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.lists.type.AbstractListFragment;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29)
public class ListsRuleFixesInstrumentedTest {
    private static final int SOURCE = 974501;
    private static final int OTHER_SOURCE = 974502;
    private static final String HOST = "rules-fixes.invalid";
    private static final long TIMEOUT_MS = 10_000;

    private Context context;
    private AppDatabase database;
    private AppDatabase previousDatabase;
    private boolean swapped;
    private ActivityScenario<ListsActivity> scenario;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "rules fixes setup");
        awaitDisk();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        SupportSQLiteDatabase sql = database.getOpenHelper().getWritableDatabase();
        AppDatabase.optimizeCreatedDatabaseStorage(sql);
        sql.execSQL("INSERT OR IGNORE INTO hosts_meta (id, active_generation) VALUES (0, 0)");
        sql.execSQL("INSERT OR IGNORE INTO hosts_stats "
                + "(id, blocked_count, blocked_exact_count, allowed_count, redirected_count, "
                + "active_rule_count, root_export_materialized, root_export_stage_materialized) "
                + "VALUES (0, 0, 0, 0, 0, 0, 0, 0)");
        previousDatabase = swapDatabase(database);
        swapped = true;
        insertSource(USER_SOURCE_ID);
        insertSource(SOURCE);
        insertSource(OTHER_SOURCE);
    }

    @After
    public void tearDown() throws Exception {
        try {
            onMain(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    activity.finish();
                }
            });
            if (scenario != null) {
                scenario.close();
            }
            awaitDisk();
            InstrumentedTestState.resetForPassiveRootUi(context, "rules fixes cleanup");
        } finally {
            if (swapped) {
                swapDatabase(previousDatabase);
            }
            if (database != null) {
                database.close();
            }
        }
    }

    @Test(timeout = 60_000)
    public void downloadedRedirectCheckboxCreatesExactAllowAndDisablesOnlySelectedRow()
            throws Exception {
        insertRule(REDIRECTED, HOST, SOURCE);
        launch(REDIRECTED_HOSTS_TAB);
        awaitRow(REDIRECTED_HOSTS_TAB, HOST, true);
        onActivity(activity -> {
            row(activity, REDIRECTED_HOSTS_TAB, HOST).performLongClick();
            assertTrue(actionMode(activity, REDIRECTED_HOSTS_TAB).getMenu()
                    .findItem(R.id.move_action).isVisible());
            actionMode(activity, REDIRECTED_HOSTS_TAB).finish();
            checkbox(row(activity, REDIRECTED_HOSTS_TAB, HOST)).performClick();
        });
        awaitDialog();
        onMain(() -> {
            TextView message = dialog().findViewById(android.R.id.message);
            assertEquals(context.getString(R.string.checkbox_list_override_redirect_message),
                    message.getText().toString());
            dialog().findViewById(android.R.id.button1).performClick();
        });
        awaitRule(SOURCE, HOST, false);
        awaitRow(REDIRECTED_HOSTS_TAB, HOST, false);
        HostListItem allowed = rule(USER_SOURCE_ID, HOST);
        assertNotNull(allowed);
        assertEquals(ALLOWED, allowed.getType());
        assertEquals(RuleKind.EXACT, allowed.getKind());
        assertNull(allowed.getRedirection());
        assertTrue(allowed.isEnabled());
        assertEquals(ALLOWED, database.hostEntryDao().getTypeForHost(HOST));
        onActivity(activity -> checkbox(row(activity, REDIRECTED_HOSTS_TAB, HOST)).performClick());
        awaitRule(SOURCE, HOST, true);
        awaitRow(REDIRECTED_HOSTS_TAB, HOST, true);
        assertEquals(REDIRECTED, database.hostEntryDao().getTypeForHost(HOST));
    }

    @Test(timeout = 60_000)
    public void redirectOverrideDoesNotDisableAnotherSourcesRedirect() throws Exception {
        insertRule(REDIRECTED, HOST, SOURCE);
        insertRule(REDIRECTED, HOST, OTHER_SOURCE);
        ListsViewModel model = newModel();
        HostListItem selected = rule(SOURCE, HOST);
        onMain(() -> model.moveListItem(selected));
        awaitDisk();
        assertFalse(rule(SOURCE, HOST).isEnabled());
        assertTrue(rule(OTHER_SOURCE, HOST).isEnabled());
        assertTrue("Paging snapshot must not be mutated", selected.isEnabled());
        assertEquals(ALLOWED, rule(USER_SOURCE_ID, HOST).getType());
        assertEquals(REDIRECTED, database.hostEntryDao().getTypeForHost(HOST));
    }

    @Test(timeout = 60_000)
    public void contextMoveIsHiddenForUserRedirectAndVisibleForUserBlock() throws Exception {
        insertRule(REDIRECTED, HOST, USER_SOURCE_ID);
        insertRule(BLOCKED, "move-block.invalid", USER_SOURCE_ID);
        launch(REDIRECTED_HOSTS_TAB);
        awaitRow(REDIRECTED_HOSTS_TAB, HOST, true);
        onActivity(activity -> {
            row(activity, REDIRECTED_HOSTS_TAB, HOST).performLongClick();
            assertFalse(actionMode(activity, REDIRECTED_HOSTS_TAB).getMenu()
                    .findItem(R.id.move_action).isVisible());
            actionMode(activity, REDIRECTED_HOSTS_TAB).finish();
        });
        selectTab(BLOCKED_HOSTS_TAB);
        awaitRow(BLOCKED_HOSTS_TAB, "move-block.invalid", true);
        onActivity(activity -> {
            row(activity, BLOCKED_HOSTS_TAB, "move-block.invalid").performLongClick();
            assertTrue(actionMode(activity, BLOCKED_HOSTS_TAB).getMenu()
                    .findItem(R.id.move_action).isVisible());
            actionMode(activity, BLOCKED_HOSTS_TAB).getMenu()
                    .performIdentifierAction(R.id.move_action, 0);
        });
        awaitDisk();
        assertEquals(ALLOWED, rule(USER_SOURCE_ID, "move-block.invalid").getType());
    }

    @Test(timeout = 60_000)
    public void noButtonRestoresDownloadedCheckboxBeforeNextConfirmedChange() throws Exception {
        cancelThenChange("no", false);
    }

    @Test(timeout = 60_000)
    public void backRestoresDownloadedCheckboxBeforeNextConfirmedChange() throws Exception {
        cancelThenChange("back", false);
    }

    @Test(timeout = 60_000)
    public void outsideTouchRestoresDownloadedCheckboxBeforeNextConfirmedChange() throws Exception {
        cancelThenChange("outside", false);
    }

    @Test(timeout = 60_000)
    public void rowClickCancellationAlsoKeepsAuthoritativeState() throws Exception {
        cancelThenChange("back", true);
    }

    @Test(timeout = 60_000)
    public void twoUserRowTapsBeforePagingRebindRestoreTheRule() throws Exception {
        insertRule(BLOCKED, HOST, USER_SOURCE_ID);
        launch(BLOCKED_HOSTS_TAB);
        awaitRow(BLOCKED_HOSTS_TAB, HOST, true);
        onActivity(activity -> {
            View bound = row(activity, BLOCKED_HOSTS_TAB, HOST);
            android.widget.CompoundButton checkbox = bound.findViewById(R.id.checkbox_list_checkbox);
            bound.performClick();
            assertFalse(checkbox.isChecked());
            bound.performClick();
            assertTrue(checkbox.isChecked());
        });
        awaitDisk();
        assertTrue(rule(USER_SOURCE_ID, HOST).isEnabled());
        awaitRow(BLOCKED_HOSTS_TAB, HOST, true);
    }

    @Test(timeout = 60_000)
    public void repeatedRequestedStateIsIdempotentAndDoesNotMutatePagingSnapshot() throws Exception {
        insertRule(BLOCKED, HOST, USER_SOURCE_ID);
        HostListItem snapshot = rule(USER_SOURCE_ID, HOST);
        ListsViewModel model = newModel();
        onMain(() -> {
            model.setItemEnabled(snapshot, false);
            model.setItemEnabled(snapshot, false);
        });
        awaitDisk();
        assertFalse(rule(USER_SOURCE_ID, HOST).isEnabled());
        assertTrue(snapshot.isEnabled());
        onMain(() -> model.setItemEnabled(snapshot, true));
        awaitDisk();
        assertTrue(rule(USER_SOURCE_ID, HOST).isEnabled());
    }

    @Test(timeout = 60_000)
    public void editingSelectedSnapshotPreservesCompletedCheckboxChange() throws Exception {
        insertRule(BLOCKED, HOST, USER_SOURCE_ID);
        HostListItem selected = rule(USER_SOURCE_ID, HOST);
        ListsViewModel model = newModel();
        onMain(() -> model.setItemEnabled(selected, false));
        awaitDisk();
        onMain(() -> assertTrue(model.updateListItem(selected, "edited-rule.invalid", null)));
        awaitDisk();

        HostListItem edited = rule(USER_SOURCE_ID, "edited-rule.invalid");
        assertNotNull(edited);
        assertFalse(edited.isEnabled());
        assertEquals(selected.getId(), edited.getId());
        assertNull(rule(USER_SOURCE_ID, HOST));
        assertTrue(selected.isEnabled());
        assertEquals(HOST, selected.getHost());
    }

    @Test(timeout = 60_000)
    public void movingSelectedSnapshotPreservesCompletedCheckboxAndEditChanges() throws Exception {
        insertRule(BLOCKED, HOST, USER_SOURCE_ID);
        HostListItem selected = rule(USER_SOURCE_ID, HOST);
        ListsViewModel model = newModel();
        onMain(() -> model.setItemEnabled(selected, false));
        awaitDisk();
        onMain(() -> assertTrue(model.updateListItem(selected, "edited-rule.invalid", null)));
        awaitDisk();
        onMain(() -> model.moveListItem(selected));
        awaitDisk();

        HostListItem moved = rule(USER_SOURCE_ID, "edited-rule.invalid");
        assertNotNull(moved);
        assertEquals(ALLOWED, moved.getType());
        assertFalse(moved.isEnabled());
        assertNull(rule(USER_SOURCE_ID, HOST));
        assertEquals(BLOCKED, selected.getType());
        assertTrue(selected.isEnabled());
        assertEquals(HOST, selected.getHost());
    }

    @Test(timeout = 60_000)
    public void staleCheckboxSnapshotPreservesCompletedEditAndMoveChanges() throws Exception {
        insertRule(BLOCKED, HOST, USER_SOURCE_ID);
        HostListItem selected = rule(USER_SOURCE_ID, HOST);
        ListsViewModel model = newModel();
        onMain(() -> assertTrue(model.updateListItem(selected, "edited-rule.invalid", null)));
        awaitDisk();
        onMain(() -> model.moveListItem(selected));
        awaitDisk();
        onMain(() -> model.setItemEnabled(selected, false));
        awaitDisk();

        HostListItem disabled = rule(USER_SOURCE_ID, "edited-rule.invalid");
        assertNotNull(disabled);
        assertEquals(ALLOWED, disabled.getType());
        assertFalse(disabled.isEnabled());
        assertNull(rule(USER_SOURCE_ID, HOST));
        assertEquals(BLOCKED, selected.getType());
        assertTrue(selected.isEnabled());
        assertEquals(HOST, selected.getHost());
    }

    @Test(timeout = 90_000)
    public void retainedTabsShareQueryAndKeepNewestQueryAfterRecreation() throws Exception {
        for (ListType type : new ListType[]{BLOCKED, ALLOWED}) {
            insertRule(type, "alpha-" + type.getValue() + ".invalid", USER_SOURCE_ID);
            insertRule(type, "beta-" + type.getValue() + ".invalid", USER_SOURCE_ID);
        }
        launch(BLOCKED_HOSTS_TAB);
        selectTab(ALLOWED_HOSTS_TAB);
        awaitRow(ALLOWED_HOSTS_TAB, "alpha-1.invalid", true);
        selectTab(BLOCKED_HOSTS_TAB);
        onActivity(activity -> editor(activity, BLOCKED_HOSTS_TAB).setText("alpha"));
        awaitRow(BLOCKED_HOSTS_TAB, "alpha-0.invalid", true);
        selectTab(ALLOWED_HOSTS_TAB);
        onActivity(activity -> editor(activity, ALLOWED_HOSTS_TAB).setText("beta"));
        awaitRow(ALLOWED_HOSTS_TAB, "beta-1.invalid", true);
        selectTab(BLOCKED_HOSTS_TAB);
        awaitOnlyRow(BLOCKED_HOSTS_TAB, "beta-0.invalid", "alpha-0.invalid");
        onActivity(activity -> {
            assertEquals("beta", editor(activity, BLOCKED_HOSTS_TAB).getText().toString());
            assertEquals("beta", editor(activity, ALLOWED_HOSTS_TAB).getText().toString());
            assertNull(row(activity, BLOCKED_HOSTS_TAB, "alpha-0.invalid"));
        });
        ListsActivity[] previous = new ListsActivity[1];
        onActivity(activity -> {
            previous[0] = activity;
            activity.recreate();
        });
        await("replacement activity", () -> {
            ListsActivity activity = currentActivity();
            return activity != null && activity != previous[0];
        });
        awaitRow(BLOCKED_HOSTS_TAB, "beta-0.invalid", true);
        onActivity(activity -> {
            assertEquals("beta", editor(activity, BLOCKED_HOSTS_TAB).getText().toString());
            model(activity).clearSearch();
        });
        awaitRow(BLOCKED_HOSTS_TAB, "alpha-0.invalid", true);
        onActivity(activity -> assertEquals("", editor(activity, BLOCKED_HOSTS_TAB)
                .getText().toString()));
    }

    @Test(timeout = 60_000)
    public void completedMutationCannotReplayAnOlderFilterAndRoomStillRefreshesRows()
            throws Exception {
        insertRule(BLOCKED, "beta-source.invalid", SOURCE);
        launch(BLOCKED_HOSTS_TAB);
        awaitRow(BLOCKED_HOSTS_TAB, "beta-source.invalid", true);
        onActivity(activity -> {
            ListsViewModel model = model(activity);
            model.search("alpha");
            assertTrue(model.addListItem(BLOCKED, "beta-new.invalid", null));
            // The old implementation posted alpha while main was held here.
            awaitDiskUnchecked();
            model.search("beta");
            model.toggleSources();
        });
        awaitOnlyRow(BLOCKED_HOSTS_TAB, "beta-new.invalid", "beta-source.invalid");
        onActivity(activity -> {
            assertEquals("beta", model(activity).getSearchQuery());
            assertEquals("beta", editor(activity, BLOCKED_HOSTS_TAB).getText().toString());
            assertNull(row(activity, BLOCKED_HOSTS_TAB, "beta-source.invalid"));
            assertTrue(model(activity).addListItem(BLOCKED, "beta-second.invalid", null));
        });
        awaitRow(BLOCKED_HOSTS_TAB, "beta-second.invalid", true);
        onActivity(activity -> assertEquals("beta", model(activity).getSearchQuery()));
    }

    @Test(timeout = 60_000)
    public void allowAddRejectsLongLineVisiblyWithoutSubmittingTheOtherLine() throws Exception {
        launch(ALLOWED_HOSTS_TAB);
        awaitFragment(ALLOWED_HOSTS_TAB);
        onActivity(activity -> activity.findViewById(R.id.lists_add).performClick());
        awaitDialog();
        onMain(() -> {
            EditText input = dialog().findViewById(R.id.list_dialog_hostname);
            input.setText(longWildcard() + "\ncontrol.invalid");
            assertNotNull(input.getError());
            assertFalse(dialog().findViewById(android.R.id.button1).isEnabled());
        });
        awaitDisk();
        assertNull(rule(USER_SOURCE_ID, "control.invalid"));
        assertNull(rule(USER_SOURCE_ID, longWildcard()));
        onMain(() -> {
            EditText input = dialog().findViewById(R.id.list_dialog_hostname);
            input.setText("*.valid-rule.invalid\ncontrol.invalid");
            assertNull(input.getError());
            assertTrue(dialog().findViewById(android.R.id.button1).isEnabled());
            dialog().findViewById(android.R.id.button1).performClick();
        });
        awaitRule(USER_SOURCE_ID, "*.valid-rule.invalid", true);
        awaitRule(USER_SOURCE_ID, "control.invalid", true);
    }

    @Test(timeout = 60_000)
    public void allowEditRejectsLongInputAndKeepsExistingRuleUntilValidSave() throws Exception {
        insertRule(ALLOWED, HOST, USER_SOURCE_ID);
        launch(ALLOWED_HOSTS_TAB);
        awaitRow(ALLOWED_HOSTS_TAB, HOST, true);
        onActivity(activity -> {
            row(activity, ALLOWED_HOSTS_TAB, HOST).performLongClick();
            actionMode(activity, ALLOWED_HOSTS_TAB).getMenu()
                    .performIdentifierAction(R.id.edit_action, 0);
        });
        awaitDialog();
        onMain(() -> {
            EditText input = dialog().findViewById(R.id.list_dialog_hostname);
            input.setText(longWildcard());
            assertNotNull(input.getError());
            assertFalse(dialog().findViewById(android.R.id.button1).isEnabled());
        });
        awaitDisk();
        assertNotNull(rule(USER_SOURCE_ID, HOST));
        assertNull(rule(USER_SOURCE_ID, longWildcard()));
        onMain(() -> {
            EditText input = dialog().findViewById(R.id.list_dialog_hostname);
            input.setText("*.edited-rule.invalid");
            assertNull(input.getError());
            assertTrue(dialog().findViewById(android.R.id.button1).isEnabled());
            dialog().findViewById(android.R.id.button1).performClick();
        });
        awaitRule(USER_SOURCE_ID, "*.edited-rule.invalid", true);
        assertNull(rule(USER_SOURCE_ID, HOST));
    }

    @Test(timeout = 60_000)
    public void writeBoundaryRejectsSameOverlengthPatternForAddAndEdit() throws Exception {
        insertRule(ALLOWED, HOST, USER_SOURCE_ID);
        ListsViewModel model = newModel();
        HostListItem original = rule(USER_SOURCE_ID, HOST);
        onMain(() -> {
            assertFalse(model.addListItem(ALLOWED, longWildcard(), null));
            assertFalse(model.updateListItem(original, longWildcard(), null));
        });
        awaitDisk();
        assertEquals(HOST, original.getHost());
        assertNotNull(rule(USER_SOURCE_ID, HOST));
        assertNull(rule(USER_SOURCE_ID, longWildcard()));
    }

    private void cancelThenChange(String cancellation, boolean useRow) throws Exception {
        insertRule(BLOCKED, HOST, SOURCE);
        launch(BLOCKED_HOSTS_TAB);
        awaitRow(BLOCKED_HOSTS_TAB, HOST, true);
        clickRule(useRow);
        awaitDialog();
        if ("no".equals(cancellation)) {
            onMain(() -> dialog().findViewById(android.R.id.button2).performClick());
        } else if ("back".equals(cancellation)) {
            pressBackOnDialog();
        } else {
            Rect bounds = new Rect();
            int[] windowTouchSlop = new int[1];
            onMain(() -> {
                View root = dialog();
                int[] location = new int[2];
                root.getLocationOnScreen(location);
                bounds.set(location[0], location[1], location[0] + root.getWidth(),
                        location[1] + root.getHeight());
                windowTouchSlop[0] = ViewConfiguration.get(root.getContext())
                        .getScaledWindowTouchSlop();
            });
            // Window ignores near-edge taps within its density-scaled outside-touch tolerance.
            int clearance = windowTouchSlop[0] + 1;
            assertTrue("Confirmation must leave a touch target beyond window touch slop",
                    bounds.top > clearance);
            injectTap(bounds.centerX(), bounds.top - clearance);
        }
        await("confirmation dismissed", () -> onMainResult(() -> dialogOrNull() == null));
        awaitDisk();
        assertTrue(rule(SOURCE, HOST).isEnabled());
        assertNull(rule(USER_SOURCE_ID, HOST));
        awaitRow(BLOCKED_HOSTS_TAB, HOST, true);
        clickRule(useRow);
        awaitDialog();
        onMain(() -> dialog().findViewById(android.R.id.button1).performClick());
        awaitRule(SOURCE, HOST, false);
        awaitRow(BLOCKED_HOSTS_TAB, HOST, false);
        clickRule(useRow);
        awaitRule(SOURCE, HOST, true);
        awaitRow(BLOCKED_HOSTS_TAB, HOST, true);
    }

    private void clickRule(boolean useRow) {
        onActivity(activity -> {
            View target = row(activity, BLOCKED_HOSTS_TAB, HOST);
            if (useRow) {
                target.performClick();
            } else {
                checkbox(target).performClick();
            }
        });
    }

    private void launch(int tab) {
        scenario = ActivityScenario.launch(new Intent(context, ListsActivity.class)
                .putExtra(ListsActivity.TAB, tab));
        onActivity(activity -> ((ViewPager2) activity.findViewById(R.id.lists_view_pager))
                .setOffscreenPageLimit(2));
    }

    private void selectTab(int tab) {
        onActivity(activity -> ((ViewPager2) activity.findViewById(R.id.lists_view_pager))
                .setCurrentItem(tab, false));
        awaitFragment(tab);
    }

    private void awaitFragment(int tab) {
        await("tab " + tab, () -> onMainResult(() -> {
            ListsActivity activity = currentActivityOnMain();
            Fragment fragment = activity == null ? null : fragment(activity, tab);
            return fragment != null && fragment.getView() != null;
        }));
    }

    private void awaitRow(int tab, String host, boolean checked) {
        await("row " + host + " checked=" + checked, () -> onMainResult(() -> {
            ListsActivity activity = currentActivityOnMain();
            View row = activity == null ? null : row(activity, tab, host);
            return row != null && row.isShown() && checkbox(row).isChecked() == checked;
        }));
    }

    private void awaitOnlyRow(int tab, String expected, String removed) {
        // The wanted row may already exist in the old page while the diff is animating.
        await("only " + expected + ", without " + removed, () -> onMainResult(() -> {
            ListsActivity activity = currentActivityOnMain();
            if (activity == null) return false;
            View present = row(activity, tab, expected);
            if (present == null || !present.isShown() || !checkbox(present).isChecked()) return false;
            RecyclerView list = fragment(activity, tab).requireView().findViewById(R.id.hosts_lists_list);
            return list.getAdapter().getItemCount() == 1 && row(activity, tab, removed) == null;
        }));
    }

    private void awaitRule(int source, String host, boolean enabled) {
        await("persisted " + host, () -> {
            HostListItem item = rule(source, host);
            return item != null && item.isEnabled() == enabled;
        });
    }

    private static void await(String description, BooleanSupplier condition) {
        long end = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            SystemClock.sleep(50);
        }
        assertTrue("Timed out: " + description, condition.getAsBoolean());
    }

    private static Fragment fragment(ListsActivity activity, int tab) {
        return activity.getSupportFragmentManager().findFragmentByTag("f" + tab);
    }

    private static EditText editor(ListsActivity activity, int tab) {
        return fragment(activity, tab).requireView().findViewById(R.id.hostsSearchEditText);
    }

    private static View row(ListsActivity activity, int tab, String host) {
        Fragment fragment = fragment(activity, tab);
        if (fragment == null || fragment.getView() == null) {
            return null;
        }
        RecyclerView list = fragment.requireView().findViewById(R.id.hosts_lists_list);
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            TextView text = row.findViewById(R.id.checkbox_list_text);
            if (text != null && host.contentEquals(text.getText())) {
                return row;
            }
        }
        return null;
    }

    private static CompoundButton checkbox(View row) {
        assertNotNull("Expected bound row", row);
        return row.findViewById(R.id.checkbox_list_checkbox);
    }

    private static ActionMode actionMode(ListsActivity activity, int tab) {
        try {
            Field field = AbstractListFragment.class.getDeclaredField("mActionMode");
            field.setAccessible(true);
            ActionMode mode = (ActionMode) field.get(fragment(activity, tab));
            assertNotNull(mode);
            return mode;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static ListsViewModel model(ListsActivity activity) {
        return new ViewModelProvider(activity).get(ListsViewModel.class);
    }

    private ListsViewModel newModel() {
        ListsViewModel[] result = new ListsViewModel[1];
        onMain(() -> result[0] = new ListsViewModel((Application) context));
        return result[0];
    }

    private HostListItem rule(int source, String host) {
        List<HostListItem> rows = database.hostsListItemDao().getExactEntriesForHost(host);
        for (HostListItem row : rows) {
            if (row.getSourceId() == source) {
                return row;
            }
        }
        return null;
    }

    private void insertSource(int id) {
        HostsSource source = new HostsSource();
        source.setId(id);
        source.setLabel("Rules fixes " + id);
        source.setUrl(id == USER_SOURCE_ID ? USER_SOURCE_URL
                : "https://rules-fixes.invalid/" + id);
        source.setEnabled(true);
        source.setAllowEnabled(true);
        source.setRedirectEnabled(true);
        database.hostsSourceDao().insert(source);
    }

    @Test(timeout = 60_000)
    public void exactApexEditsDoNotOverwriteUserSuffixRules() throws Exception {
        HostListItem suffix = new HostListItem();
        suffix.setHost(HOST);
        suffix.setSourceId(USER_SOURCE_ID);
        suffix.setKind(RuleKind.SUFFIX);
        suffix.setType(BLOCKED);
        suffix.setEnabled(true);
        database.hostsListItemDao().insert(suffix);
        ListsViewModel model = newModel();
        onMain(() -> assertTrue(model.addListItem(ALLOWED, HOST, null)));
        awaitDisk();
        database.hostEntryDao().sync();
        assertEquals(ALLOWED, database.hostEntryDao().getTypeForHost(HOST));
        assertEquals(BLOCKED, database.hostEntryDao().getTypeForHost("child." + HOST));
        database.hostsListItemDao().deleteExactUserRules(HOST);
        database.hostEntryDao().sync();
        assertEquals(BLOCKED, database.hostEntryDao().getTypeForHost(HOST));
        assertEquals(BLOCKED, database.hostEntryDao().getTypeForHost("child." + HOST));
    }

    private void insertRule(ListType type, String host, int source) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(type);
        item.setSourceId(source);
        item.setEnabled(true);
        item.setGeneration(0);
        if (type == REDIRECTED) {
            item.setRedirection("8.8.8.8");
        }
        database.hostsListItemDao().insert(item);
    }

    private static AppDatabase swapDatabase(AppDatabase replacement) throws Exception {
        Field field = AppDatabase.class.getDeclaredField("instance");
        field.setAccessible(true);
        AppDatabase previous = (AppDatabase) field.get(null);
        field.set(null, replacement);
        return previous;
    }

    private static void awaitDisk() throws InterruptedException {
        CountDownLatch drained = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(drained::countDown);
        assertTrue("diskIO did not drain", drained.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private static void awaitDiskUnchecked() {
        try {
            awaitDisk();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static boolean onMainResult(BooleanSupplier condition) {
        boolean[] result = new boolean[1];
        onMain(() -> result[0] = condition.getAsBoolean());
        return result[0];
    }

    private static ListsActivity currentActivity() {
        ListsActivity[] result = new ListsActivity[1];
        onMain(() -> result[0] = currentActivityOnMain());
        return result[0];
    }

    private static ListsActivity currentActivityOnMain() {
        for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)) {
            if (activity instanceof ListsActivity) {
                return (ListsActivity) activity;
            }
        }
        return null;
    }

    private static void onActivity(Consumer<ListsActivity> action) {
        onMain(() -> {
            ListsActivity activity = currentActivityOnMain();
            assertNotNull("Expected resumed ListsActivity", activity);
            action.accept(activity);
        });
    }

    private static View dialogOrNull() {
        for (View root : WindowInspector.getGlobalWindowViews()) {
            if (root.isShown() && root.findViewById(android.R.id.button1) != null) {
                return root;
            }
        }
        return null;
    }

    private static View dialog() {
        View root = dialogOrNull();
        assertNotNull("Expected visible confirmation/editor", root);
        return root;
    }

    private static void awaitDialog() {
        await("dialog", () -> onMainResult(() -> dialogOrNull() != null));
    }

    private static void pressBackOnDialog() {
        // Visibility alone does not mean this window is ready to receive Back.
        await("confirmation window focused for Back", () -> onMainResult(() -> {
            View root = dialogOrNull();
            return root != null && root.isAttachedToWindow() && root.isLaidOut()
                    && root.hasWindowFocus();
        }));
        long downTime = SystemClock.uptimeMillis();
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent event = new KeyEvent(downTime, SystemClock.uptimeMillis(), action,
                    KeyEvent.KEYCODE_BACK, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
            assertTrue("Back injection failed for action " + action,
                    InstrumentationRegistry.getInstrumentation().getUiAutomation()
                            .injectInputEvent(event, true));
        }
    }

    private static void injectTap(float x, float y) {
        long time = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                    x, y, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {
                assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .injectInputEvent(event, true));
            } finally {
                event.recycle();
            }
        }
    }

    private static String longWildcard() {
        char[] stars = new char[244];
        java.util.Arrays.fill(stars, '*');
        return "example.com" + new String(stars);
    }
}
