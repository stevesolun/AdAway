/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.lists;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.ui.lists.ListsActivity.ALLOWED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.BLOCKED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.REDIRECTED_HOSTS_TAB;
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
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.lists.type.AllowedHostsFragment;
import org.adaway.ui.lists.type.BlockedHostsFragment;
import org.adaway.ui.lists.type.RedirectedHostsFragment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

@RunWith(AndroidJUnit4.class)
public class ListsTabsInstrumentedTest {
    private static final String TEST_DATABASE_NAME = "lists-tabs-instrumented-test.db";
    private static final int TEST_SOURCE_ID = 919191;
    private static final String BLOCKED_HOST = "000-list-blocked-ui.invalid";
    private static final String ALLOWED_HOST = "000-list-allowed-ui.invalid";
    private static final String REDIRECTED_HOST = "000-list-redirected-ui.invalid";
    private static final String REDIRECT_IP = "8.8.4.4";
    private static final long UI_WAIT_TIMEOUT_MS = 5_000L;

    private Context context;
    private AppDatabase database;
    private HostListItemDao hostListItemDao;
    private HostsSourceDao hostsSourceDao;
    private AppDatabase previousDatabase;
    private boolean databaseSingletonSwapped;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up lists tabs");
        context.deleteDatabase(TEST_DATABASE_NAME);
        database = Room.databaseBuilder(context, AppDatabase.class, TEST_DATABASE_NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .allowMainThreadQueries()
                .build();
        initializeTestDatabase(database.getOpenHelper().getWritableDatabase());
        previousDatabase = swapAppDatabaseSingleton(database);
        databaseSingletonSwapped = true;
        hostListItemDao = database.hostsListItemDao();
        hostsSourceDao = database.hostsSourceDao();
        insertSource();
        insertListItem(BLOCKED, BLOCKED_HOST, null);
        insertListItem(ALLOWED, ALLOWED_HOST, null);
        insertListItem(REDIRECTED, REDIRECTED_HOST, REDIRECT_IP);
        assertVisibleRuntimeRow(BLOCKED, BLOCKED_HOST);
        assertVisibleRuntimeRow(ALLOWED, ALLOWED_HOST);
        assertVisibleRuntimeRow(REDIRECTED, REDIRECTED_HOST);
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (context != null) {
                InstrumentedTestState.resetForPassiveRootUi(context, "tear down lists tabs");
            }
        } finally {
            if (databaseSingletonSwapped) {
                swapAppDatabaseSingleton(previousDatabase);
                databaseSingletonSwapped = false;
            }
            if (database != null) {
                database.close();
            }
            if (context != null) {
                context.deleteDatabase(TEST_DATABASE_NAME);
            }
        }
    }

    @Test
    public void requestedTabsSelectAndRenderBlockedAllowedAndRedirectedRules() throws Exception {
        assertTabShowsOnlyItsTestRule(
                BLOCKED_HOSTS_TAB,
                R.id.lists_navigation_blocked,
                BlockedHostsFragment.class,
                BLOCKED_HOST,
                null,
                ALLOWED_HOST,
                REDIRECTED_HOST);
        assertTabShowsOnlyItsTestRule(
                ALLOWED_HOSTS_TAB,
                R.id.lists_navigation_allowed,
                AllowedHostsFragment.class,
                ALLOWED_HOST,
                null,
                BLOCKED_HOST,
                REDIRECTED_HOST);
        assertTabShowsOnlyItsTestRule(
                REDIRECTED_HOSTS_TAB,
                R.id.lists_navigation_redirected,
                RedirectedHostsFragment.class,
                REDIRECTED_HOST,
                REDIRECT_IP,
                BLOCKED_HOST,
                ALLOWED_HOST);
    }

    private void assertTabShowsOnlyItsTestRule(
            int tab,
            int selectedNavigationId,
            Class<? extends Fragment> expectedFragmentClass,
            String expectedHost,
            @Nullable String expectedSubtext,
            String firstUnexpectedHost,
            String secondUnexpectedHost) throws Exception {
        Intent intent = new Intent(context, ListsActivity.class).putExtra(TAB, tab);
        try (ActivityScenario<ListsActivity> scenario = ActivityScenario.launch(intent)) {
            waitForFragmentText(scenario, tab, expectedHost);
            scenario.onActivity(activity -> {
                BottomNavigationView navigation = activity.findViewById(R.id.navigation);
                assertEquals(selectedNavigationId, navigation.getSelectedItemId());

                ViewPager2 viewPager = activity.findViewById(R.id.lists_view_pager);
                assertEquals(tab, viewPager.getCurrentItem());

                Fragment fragment = currentFragment(activity, tab);
                assertTrue("Expected " + expectedFragmentClass.getSimpleName()
                                + " for tab " + tab + " but was " + fragment,
                        expectedFragmentClass.isInstance(fragment));
                View root = fragment.requireView();
                assertTrue(expectedHost + " must be visible in tab " + tab,
                        containsShownText(root, expectedHost));
                if (expectedSubtext != null) {
                    assertTrue(expectedSubtext + " must be visible in redirected tab.",
                            containsShownText(root, expectedSubtext));
                }
                assertFalse(firstUnexpectedHost + " leaked into tab " + tab,
                        containsShownText(root, firstUnexpectedHost));
                assertFalse(secondUnexpectedHost + " leaked into tab " + tab,
                        containsShownText(root, secondUnexpectedHost));
            });
        }
    }

    private void waitForFragmentText(ActivityScenario<ListsActivity> scenario, int tab, String text)
            throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + UI_WAIT_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final boolean[] found = new boolean[1];
            scenario.onActivity(activity -> {
                Fragment fragment = currentFragment(activity, tab);
                found[0] = fragment != null && fragment.getView() != null
                        && containsShownText(fragment.requireView(), text);
            });
            if (found[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        fail("Timed out waiting for " + text + " in tab " + tab);
    }

    @Nullable
    private static Fragment currentFragment(@NonNull ListsActivity activity, int tab) {
        return activity.getSupportFragmentManager().findFragmentByTag("f" + tab);
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
        source.setLabel("Lists tabs UI test");
        source.setUrl("https://example.invalid/lists-tabs-ui.txt");
        source.setEnabled(true);
        hostsSourceDao.insert(source);
    }

    private void insertListItem(ListType type, String host, @Nullable String redirection) {
        HostListItem item = new HostListItem();
        item.setType(type);
        item.setHost(host);
        item.setRedirection(redirection);
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

    private void assertVisibleRuntimeRow(@NonNull ListType type, @NonNull String host) {
        try (Cursor cursor = database.getOpenHelper().getReadableDatabase().query(
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
