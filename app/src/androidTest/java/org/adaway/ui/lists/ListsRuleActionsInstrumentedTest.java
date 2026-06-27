/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.lists;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.ui.lists.ListsActivity.BLOCKED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.TAB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

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
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class ListsRuleActionsInstrumentedTest {
    private static final String TEST_DATABASE_NAME = "lists-rule-actions-test.db";
    private static final int DOWNLOADED_SOURCE_ID = 939393;
    private static final String USER_HOST = "toggle-user-rule.invalid";
    private static final String EDITED_USER_HOST = "edited-user-rule.invalid";
    private static final String DELETE_USER_HOST = "delete-user-rule.invalid";
    private static final String DOWNLOADED_HOST = "downloaded-override-rule.invalid";
    private static final long TIMEOUT_MS = 10_000L;

    private Context context;
    private AppDatabase database;
    private HostListItemDao hostListItemDao;
    private HostsSourceDao hostsSourceDao;
    private AppDatabase previousDatabase;
    private boolean databaseSingletonSwapped;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up lists rule actions");
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
        insertUserSource();
        insertDownloadedSource();
    }

    @After
    public void tearDown() throws Exception {
        try {
            finishResumedActivities();
            if (this.context != null) {
                InstrumentedTestState.resetForPassiveRootUi(this.context,
                        "tear down lists rule actions");
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

    @Test(timeout = 90_000)
    public void userRuleCanToggleEditCancelDeleteAndConfirmDelete() throws Exception {
        insertListItem(BLOCKED, USER_HOST, true, USER_SOURCE_ID);
        insertListItem(BLOCKED, DELETE_USER_HOST, true, USER_SOURCE_ID);

        launchBlockedLists();
        waitForTabFragment(BLOCKED_HOSTS_TAB);
        waitForAccessibilityText(DELETE_USER_HOST);
        waitForAccessibilityText(USER_HOST);

        clickRowText(USER_HOST);
        waitForRuleEnabled(BLOCKED, USER_HOST, USER_SOURCE_ID, false);

        clickRowText(USER_HOST);
        waitForRuleEnabled(BLOCKED, USER_HOST, USER_SOURCE_ID, true);

        longClickRowText(USER_HOST);
        clickAccessibilityText(this.context.getString(R.string.checkbox_list_context_edit));
        waitForAccessibilityText(this.context.getString(R.string.list_edit_dialog_black));
        setEditTextValues(EDITED_USER_HOST);
        clickAccessibilityText(this.context.getString(R.string.button_save));
        waitForRuleCount(BLOCKED, EDITED_USER_HOST, USER_SOURCE_ID, true, 1);
        waitForRuleCount(BLOCKED, USER_HOST, USER_SOURCE_ID, true, 0);

        longClickRowText(DELETE_USER_HOST);
        clickAccessibilityText(this.context.getString(R.string.checkbox_list_context_delete));
        waitForAccessibilityText(this.context.getString(R.string.list_delete_confirm_title));
        clickAccessibilityText(this.context.getString(R.string.button_cancel));
        waitForRuleCount(BLOCKED, DELETE_USER_HOST, USER_SOURCE_ID, true, 1);

        longClickRowText(DELETE_USER_HOST);
        clickAccessibilityText(this.context.getString(R.string.checkbox_list_context_delete));
        waitForAccessibilityText(this.context.getString(R.string.list_delete_confirm_title));
        clickAccessibilityText(this.context.getString(R.string.list_delete_confirm_action));
        waitForRuleCount(BLOCKED, DELETE_USER_HOST, USER_SOURCE_ID, true, 0);
    }

    @Test(timeout = 90_000)
    public void downloadedRuleToggleCreatesAllowedOverride() throws Exception {
        insertListItem(BLOCKED, DOWNLOADED_HOST, true, DOWNLOADED_SOURCE_ID);

        launchBlockedLists();
        waitForTabFragment(BLOCKED_HOSTS_TAB);
        waitForAccessibilityText(DOWNLOADED_HOST);

        clickRowText(DOWNLOADED_HOST);
        waitForAccessibilityText(
                this.context.getString(R.string.checkbox_list_override_downloaded_message));
        clickAccessibilityText(this.context.getString(android.R.string.yes));

        waitForRuleEnabled(BLOCKED, DOWNLOADED_HOST, DOWNLOADED_SOURCE_ID, false);
        waitForRuleCount(ALLOWED, DOWNLOADED_HOST, USER_SOURCE_ID, true, 1);
    }

    private ActivityScenario<ListsActivity> launchBlockedLists() {
        return ActivityScenario.launch(
                new Intent(this.context, ListsActivity.class).putExtra(TAB, BLOCKED_HOSTS_TAB));
    }

    private static void waitForTabFragment(int tab) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] ready = new boolean[1];
            runOnResumedListsActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager()
                        .findFragmentByTag("f" + tab);
                ready[0] = fragment != null && fragment.getView() != null;
            });
            if (ready[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Tab fragment was not ready: " + tab);
    }

    private void waitForRuleEnabled(
            @NonNull ListType type,
            @NonNull String host,
            int sourceId,
            boolean enabled) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (countRules(type, host, sourceId, enabled) == 1) {
                return;
            }
            SystemClock.sleep(100L);
        }
        assertEquals("Expected " + host + " enabled=" + enabled, 1,
                countRules(type, host, sourceId, enabled));
    }

    private void waitForRuleCount(
            @NonNull ListType type,
            @NonNull String host,
            int sourceId,
            boolean enabled,
            int expectedCount) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (countRules(type, host, sourceId, enabled) == expectedCount) {
                return;
            }
            SystemClock.sleep(100L);
        }
        assertEquals("Unexpected count for " + host, expectedCount,
                countRules(type, host, sourceId, enabled));
    }

    private int countRules(
            @NonNull ListType type,
            @NonNull String host,
            int sourceId,
            boolean enabled) {
        try (Cursor cursor = this.database.getOpenHelper().getReadableDatabase().query(
                new SimpleSQLiteQuery("SELECT COUNT(*) FROM hosts_lists "
                        + "WHERE type = ? AND host = ? AND source_id = ? AND enabled = ?",
                        new Object[]{
                                type.getValue(),
                                host,
                                sourceId,
                                enabled ? 1 : 0
                        }))) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private static void waitForAccessibilityText(String expectedText) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                if (root != null && containsText(root, expectedText)) {
                    return;
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Text was not visible: " + expectedText);
    }

    private static void clickAccessibilityText(String expectedText) throws Exception {
        performAccessibilityActionOnText(
                expectedText,
                AccessibilityNodeInfo.ACTION_CLICK,
                "click");
    }

    private static void clickRowText(String expectedText) throws Exception {
        performAccessibilityActionOnText(
                expectedText,
                AccessibilityNodeInfo.ACTION_CLICK,
                "row click");
    }

    private static void longClickRowText(String expectedText) throws Exception {
        performAccessibilityActionOnText(
                expectedText,
                AccessibilityNodeInfo.ACTION_LONG_CLICK,
                "row long click");
    }

    private static void performAccessibilityActionOnText(
            String expectedText,
            int action,
            String actionName) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                List<AccessibilityNodeInfo> matches = new ArrayList<>();
                if (root != null) {
                    collectTextMatches(root, expectedText, matches);
                }
                try {
                    for (AccessibilityNodeInfo node : matches) {
                        if (performActionOnNodeOrParent(node, action)) {
                            return;
                        }
                    }
                } finally {
                    for (AccessibilityNodeInfo node : matches) {
                        node.recycle();
                    }
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Could not " + actionName + " text: " + expectedText);
    }

    private static void setEditTextValues(String... values) throws Exception {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .getRootInActiveWindow();
            try {
                List<AccessibilityNodeInfo> editTexts = new ArrayList<>();
                if (root != null) {
                    collectEditTexts(root, editTexts);
                }
                if (editTexts.size() >= values.length) {
                    try {
                        for (int index = 0; index < values.length; index++) {
                            setNodeText(editTexts.get(index), values[index]);
                        }
                        return;
                    } finally {
                        for (AccessibilityNodeInfo editText : editTexts) {
                            editText.recycle();
                        }
                    }
                }
                for (AccessibilityNodeInfo editText : editTexts) {
                    editText.recycle();
                }
            } finally {
                if (root != null) {
                    root.recycle();
                }
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("Expected at least " + values.length + " edit fields");
    }

    private static void setNodeText(@NonNull AccessibilityNodeInfo node, @NonNull String text) {
        Bundle arguments = new Bundle();
        arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text);
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            throw new AssertionError("Could not set dialog field text");
        }
    }

    private static void collectEditTexts(
            @NonNull AccessibilityNodeInfo node,
            @NonNull List<AccessibilityNodeInfo> editTexts) {
        CharSequence className = node.getClassName();
        if (className != null && className.toString().endsWith("EditText")) {
            editTexts.add(AccessibilityNodeInfo.obtain(node));
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                collectEditTexts(child, editTexts);
            } finally {
                child.recycle();
            }
        }
    }

    private static boolean containsText(AccessibilityNodeInfo node, String expectedText) {
        if (nodeTextContains(node, expectedText)) {
            return true;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                if (containsText(child, expectedText)) {
                    return true;
                }
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private static void collectTextMatches(
            AccessibilityNodeInfo node,
            String expectedText,
            @NonNull List<AccessibilityNodeInfo> matches) {
        if (nodeTextMatches(node, expectedText)) {
            matches.add(AccessibilityNodeInfo.obtain(node));
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            try {
                collectTextMatches(child, expectedText, matches);
            } finally {
                child.recycle();
            }
        }
    }

    private static boolean nodeTextMatches(AccessibilityNodeInfo node, String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && expectedText.contentEquals(text))
                || (description != null && expectedText.contentEquals(description));
    }

    private static boolean nodeTextContains(AccessibilityNodeInfo node, String expectedText) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        return (text != null && text.toString().contains(expectedText))
                || (description != null && description.toString().contains(expectedText));
    }

    private static boolean performActionOnNodeOrParent(AccessibilityNodeInfo node, int action) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        try {
            while (current != null) {
                if (current.getActionList().stream().anyMatch(info -> info.getId() == action)
                        && current.performAction(action)) {
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

    private void insertUserSource() {
        HostsSource source = new HostsSource();
        source.setId(USER_SOURCE_ID);
        source.setLabel("User rules");
        source.setUrl(USER_SOURCE_URL);
        source.setAllowEnabled(true);
        source.setRedirectEnabled(true);
        this.hostsSourceDao.insert(source);
    }

    private void insertDownloadedSource() {
        HostsSource source = new HostsSource();
        source.setId(DOWNLOADED_SOURCE_ID);
        source.setLabel("Downloaded rule actions source");
        source.setUrl("https://example.invalid/downloaded-rule-actions.txt");
        source.setEnabled(true);
        this.hostsSourceDao.insert(source);
    }

    private void insertListItem(
            @NonNull ListType type,
            @NonNull String host,
            boolean enabled,
            int sourceId) {
        HostListItem item = new HostListItem();
        item.setType(type);
        item.setHost(host);
        item.setEnabled(enabled);
        item.setSourceId(sourceId);
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

    private static void runOnResumedListsActivity(@NonNull Consumer<ListsActivity> action) {
        final boolean[] handled = new boolean[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                if (activity instanceof ListsActivity) {
                    action.accept((ListsActivity) activity);
                    handled[0] = true;
                    return;
                }
            }
        });
        if (!handled[0]) {
            throw new AssertionError("No resumed ListsActivity");
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
}
