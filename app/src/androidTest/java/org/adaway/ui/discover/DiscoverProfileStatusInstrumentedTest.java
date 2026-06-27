/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DiscoverProfileStatusInstrumentedTest {
    private static final int TIMEOUT_MS = 10_000;
    private static final int TIMEOUT_SECONDS = 10;
    private static final int SOURCE_A_ID = 30101;
    private static final int SOURCE_B_ID = 30102;
    private static final int SOURCE_C_ID = 30103;
    private static final String URL_A = "https://example.invalid/profile-a.txt";
    private static final String URL_B = "https://example.invalid/profile-b.txt";
    private static final String URL_C = "https://example.invalid/profile-c.txt";

    private Context context;
    private HostsSourceDao hostsSourceDao;

    @Before
    public void setUp() throws Exception {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up profile status");
        AppDatabase database = AppDatabase.getInstance(this.context);
        database.getOpenHelper().getWritableDatabase();
        this.hostsSourceDao = database.hostsSourceDao();
        runDiskIo(() -> {
            // Let AppDatabase.onCreate finish its queued safety allowlist seed before cleanup.
        });
        runDiskIo(() -> {
            this.hostsSourceDao.deleteAll();
            insertSource(SOURCE_A_ID, URL_A, true);
            insertSource(SOURCE_B_ID, URL_B, true);
            insertSource(SOURCE_C_ID, URL_C, false);
        });
        Set<String> safeUrls = new HashSet<>();
        safeUrls.add(URL_A);
        safeUrls.add(URL_B);
        FilterSetStore.savePresetProfile(this.context, FilterSetStore.PROFILE_SAFE, safeUrls);
    }

    @After
    public void tearDown() throws Exception {
        if (this.context != null) {
            runDiskIo(() -> this.hostsSourceDao.deleteAll());
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down profile status");
        }
    }

    @Test(timeout = 60_000)
    public void profileStatusReflectsExactExtendedPartialAndCustomState() throws Exception {
        try (ActivityScenario<HomeActivity> scenario =
                     ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_discover));

            waitForProfileStatus(
                    scenario,
                    this.context.getString(
                            R.string.filter_profile_status_exact,
                            this.context.getString(R.string.filter_preset_safe)));

            runDiskIo(() -> this.hostsSourceDao.setSourceEnabled(SOURCE_C_ID, true));
            refreshDiscover(scenario);
            waitForProfileStatus(
                    scenario,
                    this.context.getString(
                            R.string.filter_profile_status_extended,
                            this.context.getString(R.string.filter_preset_safe)));

            runDiskIo(() -> this.hostsSourceDao.setSourceEnabled(SOURCE_B_ID, false));
            refreshDiscover(scenario);
            waitForProfileStatus(
                    scenario,
                    this.context.getString(
                            R.string.filter_profile_status_partial,
                            this.context.getString(R.string.filter_preset_safe)));

            FilterSetStore.markCustomProfile(this.context);
            refreshDiscover(scenario);
            waitForProfileStatus(
                    scenario,
                    this.context.getString(R.string.filter_profile_status_custom));
        }
    }

    private void insertSource(int id, @NonNull String url, boolean enabled) {
        HostsSource source = new HostsSource();
        source.setId(id);
        source.setLabel("Profile status " + id);
        source.setUrl(url);
        source.setEnabled(enabled);
        this.hostsSourceDao.insert(source);
    }

    private static void refreshDiscover(@NonNull ActivityScenario<HomeActivity> scenario) {
        scenario.onActivity(activity -> {
            activity.navigateTo(R.id.nav_home);
            activity.navigateTo(R.id.nav_discover);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void waitForProfileStatus(
            @NonNull ActivityScenario<HomeActivity> scenario,
            @NonNull String expectedText) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final String[] actual = new String[1];
            scenario.onActivity(activity -> {
                TextView status = activity.findViewById(R.id.discoverProfileStatus);
                assertNotNull(status);
                actual[0] = status.getText().toString();
            });
            if (expectedText.equals(actual[0])) {
                return;
            }
            SystemClock.sleep(100L);
        }
        scenario.onActivity(activity -> {
            TextView status = activity.findViewById(R.id.discoverProfileStatus);
            assertNotNull(status);
            assertEquals(expectedText, status.getText().toString());
        });
    }

    private static void runDiskIo(@NonNull ThrowingRunnable runnable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError("Disk fixture failed.", failure[0]);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
