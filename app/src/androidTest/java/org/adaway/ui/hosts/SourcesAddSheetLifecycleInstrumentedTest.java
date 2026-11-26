package org.adaway.ui.hosts;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.view.View;
import android.view.inspector.WindowInspector;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.source.SourceEditActivity;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29)
public class SourcesAddSheetLifecycleInstrumentedTest {
    @Test(timeout = 90_000)
    public void detachedSheetIgnoresLateTapsAndFreshSheetOpensEditor() {
        Context context = ApplicationProvider.getApplicationContext();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        InstrumentedTestState.resetForPassiveRootUi(context, "start add sheet lifecycle");
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            openSheet(scenario, instrumentation);
            AtomicReference<RuntimeException> lateTapError = new AtomicReference<>();
            Instrumentation.ActivityMonitor editor = instrumentation.addMonitor(
                    SourceEditActivity.class.getName(), null, false);
            Instrumentation.ActivityMonitor catalog = instrumentation.addMonitor(
                    HomeActivity.class.getName(), null, false);
            try {
                scenario.onActivity(home -> {
                    View custom = option(R.id.addCustomOption);
                    View browse = option(R.id.browseCatalogOption);
                    Fragment tab = home.getSupportFragmentManager()
                            .findFragmentById(R.id.nav_fragment_container);
                    Fragment owner = tab.getChildFragmentManager()
                            .findFragmentById(R.id.hosts_sources_child_container);
                    assertNotNull(owner);
                    home.navigateTo(R.id.nav_more);
                    home.getSupportFragmentManager().executePendingTransactions();
                    assertFalse("Exercise a detached owner", owner.isAdded());
                    // Replay clicks retained by the window before its owner was replaced.
                    try {
                        custom.performClick();
                        browse.performClick();
                    } catch (RuntimeException exception) {
                        lateTapError.set(exception);
                    }
                    if (lateTapError.get() == null) {
                        assertFalse("Obsolete dialog still attached", custom.isAttachedToWindow());
                    }
                });
                if (lateTapError.get() != null) {
                    throw new AssertionError("Detached add sheet dispatched a stale tap", lateTapError.get());
                }
                instrumentation.waitForIdleSync();
                assertEquals(0, editor.getHits());
                assertEquals(0, catalog.getHits());

                openSheet(scenario, instrumentation);
                scenario.onActivity(home -> assertTrue(option(R.id.addCustomOption).performClick()));
                Activity opened = instrumentation.waitForMonitorWithTimeout(editor, 10_000);
                assertNotNull("Current sheet must still open the custom editor", opened);
                instrumentation.runOnMainSync(opened::finish);
            } finally {
                instrumentation.removeMonitor(editor);
                instrumentation.removeMonitor(catalog);
            }
        } finally {
            InstrumentedTestState.resetForPassiveRootUi(context, "finish add sheet lifecycle");
        }
    }

    private static void openSheet(ActivityScenario<HomeActivity> scenario, Instrumentation instrumentation) {
        scenario.onActivity(home -> home.navigateTo(R.id.nav_sources));
        instrumentation.waitForIdleSync();
        scenario.onActivity(home -> assertTrue(home.findViewById(R.id.hosts_sources_add).performClick()));
        instrumentation.waitForIdleSync();
    }

    private static View option(int id) {
        for (View root : WindowInspector.getGlobalWindowViews()) {
            View option = root.findViewById(id);
            if (option != null && option.isShown()) return option;
        }
        throw new AssertionError("Missing visible add-sheet option: " + id);
    }
}
