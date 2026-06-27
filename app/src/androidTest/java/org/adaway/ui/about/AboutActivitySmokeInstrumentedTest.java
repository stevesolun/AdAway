/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.adaway.ui.about;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.adaway.BuildConfig;
import org.adaway.R;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.ui.prefs.PrefsMainFragment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;

@RunWith(AndroidJUnit4.class)
public class AboutActivitySmokeInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000L;

    private Context context;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "set up About smoke");
    }

    @After
    public void tearDown() {
        finishResumedActivities();
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(this.context, "tear down About smoke");
        }
    }

    @Test(timeout = 60_000)
    public void preferencesAboutOpensProjectVersionAndLicenseAttribution() {
        try (ActivityScenario<PrefsActivity> scenario =
                     ActivityScenario.launch(PrefsActivity.class)) {
            openAboutPreference(scenario);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertAboutScreenRendered();
        }
    }

    private static void openAboutPreference(ActivityScenario<PrefsActivity> scenario) {
        scenario.onActivity(activity -> {
            activity.getSupportFragmentManager().executePendingTransactions();
            Fragment fragment = activity.getSupportFragmentManager()
                    .findFragmentById(android.R.id.content);
            assertTrue(fragment instanceof PrefsMainFragment);

            Preference aboutPreference =
                    ((PrefsMainFragment) fragment).findPreference("pref_about");
            assertNotNull(aboutPreference);
            aboutPreference.performClick();
        });
    }

    private static void assertAboutScreenRendered() {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] rendered = new boolean[1];
            final AssertionError[] failure = new AssertionError[1];
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                AboutActivity activity = findResumedAboutActivity();
                if (activity == null) {
                    return;
                }
                try {
                    assertAboutText(activity);
                    rendered[0] = true;
                } catch (AssertionError error) {
                    failure[0] = error;
                }
            });
            if (rendered[0]) {
                return;
            }
            if (failure[0] != null) {
                throw failure[0];
            }
            SystemClock.sleep(100L);
        }
        throw new AssertionError("About screen did not become visible.");
    }

    private static void assertAboutText(AboutActivity activity) {
        assertEquals(activity.getString(R.string.app_name),
                visibleText(activity, R.id.aboutTitle));

        String versionText = visibleText(activity, R.id.aboutVersion);
        assertTrue("About version should include app description: " + versionText,
                versionText.contains(activity.getString(R.string.app_description)));
        assertTrue("About version should include build version: " + versionText,
                versionText.contains(BuildConfig.VERSION_NAME));

        assertTrue("About credits should be visible.",
                visibleText(activity, R.id.aboutCredits).trim().length() > 0);

        String attributionText = visibleText(activity, R.id.aboutAttribution);
        assertTrue("About attribution should identify AdAway: " + attributionText,
                attributionText.contains("AdAway"));
        assertTrue("About attribution should identify open source status: " + attributionText,
                attributionText.contains("Open source"));
        assertTrue("About attribution should identify GPL license family: " + attributionText,
                attributionText.contains("GPL-3.0"));
    }

    private static String visibleText(Activity activity, int viewId) {
        TextView view = activity.findViewById(viewId);
        assertNotNull(view);
        assertEquals(View.VISIBLE, view.getVisibility());
        return view.getText().toString();
    }

    private static AboutActivity findResumedAboutActivity() {
        Collection<Activity> activities = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED);
        for (Activity activity : activities) {
            if (activity instanceof AboutActivity) {
                return (AboutActivity) activity;
            }
        }
        return null;
    }

    private static void finishResumedActivities() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Collection<Activity> activities = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED);
            for (Activity activity : activities) {
                activity.finish();
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
