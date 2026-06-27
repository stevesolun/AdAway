/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.lists;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.ui.lists.ListsActivity.BLOCKED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.TAB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.textfield.TextInputEditText;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.testing.InstrumentedTestState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

@RunWith(AndroidJUnit4.class)
public class ListsSearchInstrumentedTest {
    private static final String TEST_DATABASE_NAME = "lists-search-instrumented-test.db";
    private static final int TEST_SOURCE_ID = 929292;
    private static final String MATCHING_HOST = "alpha-search-visible.invalid";
    private static final String HIDDEN_HOST = "beta-search-hidden.invalid";
    private static final String HOSTS_LISTS_TABLE = "hosts_lists";
    private static final String BROKEN_HOSTS_LISTS_TABLE = "hosts_lists_unavailable_for_test";
    private static final long UI_WAIT_TIMEOUT_MS = 10_000L;

    private Context context;
    private AppDatabase database;
    private HostListItemDao hostListItemDao;
    private HostsSourceDao hostsSourceDao;
    private AppDatabase previousDatabase;
    private boolean databaseSingletonSwapped;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up lists search");
        this.context.deleteDatabase(TEST_DATABASE_NAME);
        this.database = Room.databaseBuilder(this.context, AppDatabase.class, TEST_DATABASE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .allowMainThreadQueries()
                .build();
        initializeTestDatabase(this.database.getOpenHelper().getWritableDatabase());
        this.previousDatabase = swapAppDatabaseSingleton(this.database);
        this.databaseSingletonSwapped = true;
        this.hostListItemDao = this.database.hostsListItemDao();
        this.hostsSourceDao = this.database.hostsSourceDao();
        insertSource();
        insertListItem(BLOCKED, MATCHING_HOST);
        insertListItem(BLOCKED, HIDDEN_HOST);
        assertVisibleRuntimeRow(BLOCKED, MATCHING_HOST);
        assertVisibleRuntimeRow(BLOCKED, HIDDEN_HOST);
    }

    @After
    public void tearDown() throws Exception {
        try {
            restoreHostsListsTableIfNeeded();
            if (this.context != null) {
                InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down lists search");
            }
        } finally {
            if (this.databaseSingletonSwapped) {
                swapAppDatabaseSingleton(this.previousDatabase);
                this.databaseSingletonSwapped = false;
            }
            if (this.database != null) {
                this.database.close();
            }
            if (this.context != null) {
                this.context.deleteDatabase(TEST_DATABASE_NAME);
            }
        }
    }

    @Test(timeout = 60_000)
    public void searchFieldFiltersVisibleRulesAndShowsNoMatchesState() throws Exception {
        Intent intent = new Intent(this.context, ListsActivity.class)
                .putExtra(TAB, BLOCKED_HOSTS_TAB);
        try (ActivityScenario<ListsActivity> scenario = ActivityScenario.launch(intent)) {
            waitForFragmentText(scenario, MATCHING_HOST);
            waitForFragmentText(scenario, HIDDEN_HOST);

            setSearchQuery(scenario, "alpha");
            waitForSearchState(
                    scenario,
                    MATCHING_HOST,
                    HIDDEN_HOST,
                    null);

            setSearchQuery(scenario, "no-matching-domain");
            waitForSearchState(
                    scenario,
                    null,
                    MATCHING_HOST,
                    this.context.getString(R.string.lists_state_no_matches_title));
        }
    }

    @Test(timeout = 60_000)
    public void emptyListShowsNoRulesState() throws Exception {
        this.hostListItemDao.clearSourceHosts(TEST_SOURCE_ID);
        assertRuntimeRowCount(BLOCKED, 0);

        Intent intent = new Intent(this.context, ListsActivity.class)
                .putExtra(TAB, BLOCKED_HOSTS_TAB);
        try (ActivityScenario<ListsActivity> scenario = ActivityScenario.launch(intent)) {
            waitForSearchState(
                    scenario,
                    this.context.getString(R.string.lists_state_no_rules_title),
                    MATCHING_HOST,
                    this.context.getString(R.string.lists_state_no_rules_message));
        }
    }

    @Test(timeout = 60_000)
    public void pagingLoadFailureShowsRetryAndRecovers() throws Exception {
        renameTable(HOSTS_LISTS_TABLE, BROKEN_HOSTS_LISTS_TABLE);

        Intent intent = new Intent(this.context, ListsActivity.class)
                .putExtra(TAB, BLOCKED_HOSTS_TAB);
        try (ActivityScenario<ListsActivity> scenario = ActivityScenario.launch(intent)) {
            waitForStateContainer(
                    scenario,
                    this.context.getString(R.string.lists_state_load_failed_title),
                    this.context.getString(R.string.lists_state_load_failed_message),
                    true);

            renameTable(BROKEN_HOSTS_LISTS_TABLE, HOSTS_LISTS_TABLE);
            clickRetryButton(scenario);
            waitForFragmentText(scenario, MATCHING_HOST);
        }
    }

    private static void setSearchQuery(
            @NonNull ActivityScenario<ListsActivity> scenario,
            @NonNull String query) {
        scenario.onActivity(activity -> {
            Fragment fragment = currentFragment(activity);
            TextInputEditText searchEditText = fragment.requireView()
                    .findViewById(R.id.hostsSearchEditText);
            searchEditText.setText(query);
            searchEditText.setSelection(query.length());
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void clickRetryButton(@NonNull ActivityScenario<ListsActivity> scenario) {
        scenario.onActivity(activity -> currentFragment(activity).requireView()
                .findViewById(R.id.hostsListsStateRetryButton)
                .performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void waitForSearchState(
            @NonNull ActivityScenario<ListsActivity> scenario,
            @Nullable String expectedVisibleText,
            @NonNull String expectedHiddenText,
            @Nullable String expectedStateText) {
        long deadline = SystemClock.uptimeMillis() + UI_WAIT_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final boolean[] matched = new boolean[1];
            scenario.onActivity(activity -> {
                View root = currentFragment(activity).requireView();
                boolean expectedVisible = expectedVisibleText == null
                        || containsShownText(root, expectedVisibleText);
                boolean hiddenAbsent = !containsShownText(root, expectedHiddenText);
                boolean stateVisible = expectedStateText == null
                        || containsShownText(root, expectedStateText);
                matched[0] = expectedVisible && hiddenAbsent && stateVisible;
            });
            if (matched[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        fail("Timed out waiting for search state.");
    }

    private static void waitForStateContainer(
            @NonNull ActivityScenario<ListsActivity> scenario,
            @NonNull String expectedTitle,
            @NonNull String expectedMessage,
            boolean retryVisible) {
        long deadline = SystemClock.uptimeMillis() + UI_WAIT_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final boolean[] matched = new boolean[1];
            scenario.onActivity(activity -> {
                View root = currentFragment(activity).requireView();
                View stateContainer = root.findViewById(R.id.hostsListsStateContainer);
                View recyclerView = root.findViewById(R.id.hosts_lists_list);
                View progress = root.findViewById(R.id.hostsListsStateProgress);
                View retryButton = root.findViewById(R.id.hostsListsStateRetryButton);
                matched[0] = stateContainer.isShown()
                        && !recyclerView.isShown()
                        && !progress.isShown()
                        && retryButton.isShown() == retryVisible
                        && containsShownText(root, expectedTitle)
                        && containsShownText(root, expectedMessage);
            });
            if (matched[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        fail("Timed out waiting for lists state container: " + expectedTitle);
    }

    private static void waitForFragmentText(
            @NonNull ActivityScenario<ListsActivity> scenario,
            @NonNull String text)
            throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + UI_WAIT_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final boolean[] found = new boolean[1];
            scenario.onActivity(activity -> {
                Fragment fragment = currentFragment(activity);
                found[0] = fragment != null && fragment.getView() != null
                        && containsShownText(fragment.requireView(), text);
            });
            if (found[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        fail("Timed out waiting for " + text);
    }

    @NonNull
    private static Fragment currentFragment(@NonNull ListsActivity activity) {
        Fragment fragment = activity.getSupportFragmentManager()
                .findFragmentByTag("f" + BLOCKED_HOSTS_TAB);
        if (fragment == null) {
            throw new AssertionError("Blocked list fragment is not attached.");
        }
        return fragment;
    }

    private static boolean containsShownText(@NonNull View view, @NonNull String expectedText) {
        if (view instanceof TextView && view.isShown()) {
            CharSequence text = ((TextView) view).getText();
            if (expectedText.contentEquals(text)) {
                return true;
            }
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (containsShownText(group.getChildAt(index), expectedText)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void insertSource() {
        HostsSource source = new HostsSource();
        source.setId(TEST_SOURCE_ID);
        source.setLabel("Lists search UI test");
        source.setUrl("https://example.invalid/lists-search-ui.txt");
        source.setEnabled(true);
        this.hostsSourceDao.insert(source);
    }

    private void insertListItem(ListType type, String host) {
        HostListItem item = new HostListItem();
        item.setType(type);
        item.setHost(host);
        item.setEnabled(true);
        item.setSourceId(TEST_SOURCE_ID);
        item.setGeneration(getActiveGeneration());
        this.hostListItemDao.insert(item);
    }

    private int getActiveGeneration() {
        try (Cursor cursor = this.database.getOpenHelper().getWritableDatabase().query(
                "SELECT active_generation FROM hosts_meta WHERE id = 0 LIMIT 1")) {
            if (!cursor.moveToFirst()) {
                return 0;
            }
            return cursor.getInt(0);
        }
    }

    private void assertVisibleRuntimeRow(@NonNull ListType type, @NonNull String host) {
        try (Cursor cursor = this.database.getOpenHelper().getReadableDatabase().query(
                new SimpleSQLiteQuery("SELECT COUNT(*) FROM hosts_lists "
                        + "WHERE type = ? AND host = ? AND "
                        + "(source_id = 1 OR generation = "
                        + "(SELECT active_generation FROM hosts_meta WHERE id = 0))",
                        new Object[]{type.getValue(), host}))) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Seeded row must be visible to the lists runtime query: " + host,
                    1, cursor.getInt(0));
        }
    }

    private void assertRuntimeRowCount(@NonNull ListType type, int expectedCount) {
        try (Cursor cursor = this.database.getOpenHelper().getReadableDatabase().query(
                new SimpleSQLiteQuery("SELECT COUNT(*) FROM hosts_lists "
                        + "WHERE type = ? AND "
                        + "(source_id = 1 OR generation = "
                        + "(SELECT active_generation FROM hosts_meta WHERE id = 0))",
                        new Object[]{type.getValue()}))) {
            assertTrue(cursor.moveToFirst());
            assertEquals(expectedCount, cursor.getInt(0));
        }
    }

    private void renameTable(@NonNull String fromTable, @NonNull String toTable) {
        this.database.getOpenHelper().getWritableDatabase()
                .execSQL("ALTER TABLE " + fromTable + " RENAME TO " + toTable);
    }

    private void restoreHostsListsTableIfNeeded() {
        if (this.database == null || !tableExists(BROKEN_HOSTS_LISTS_TABLE)
                || tableExists(HOSTS_LISTS_TABLE)) {
            return;
        }
        renameTable(BROKEN_HOSTS_LISTS_TABLE, HOSTS_LISTS_TABLE);
    }

    private boolean tableExists(@NonNull String tableName) {
        try (Cursor cursor = this.database.getOpenHelper().getReadableDatabase().query(
                new SimpleSQLiteQuery("SELECT 1 FROM sqlite_master "
                        + "WHERE type = 'table' AND name = ? LIMIT 1",
                        new Object[]{tableName}))) {
            return cursor.moveToFirst();
        }
    }

    private static void initializeTestDatabase(@NonNull SupportSQLiteDatabase writableDatabase) {
        AppDatabase.optimizeCreatedDatabaseStorage(writableDatabase);
        writableDatabase.execSQL("INSERT OR IGNORE INTO hosts_meta "
                + "(id, active_generation) VALUES (0, 0)");
        writableDatabase.execSQL("INSERT OR IGNORE INTO hosts_stats "
                + "(id, blocked_count, blocked_exact_count, allowed_count, redirected_count, "
                + "active_rule_count, root_export_materialized, root_export_stage_materialized) "
                + "VALUES (0, 0, 0, 0, 0, 0, 0, 0)");
    }

    private static AppDatabase swapAppDatabaseSingleton(@Nullable AppDatabase database)
            throws Exception {
        Field field = AppDatabase.class.getDeclaredField("instance");
        field.setAccessible(true);
        AppDatabase previous = (AppDatabase) field.get(null);
        field.set(null, database);
        return previous;
    }
}
