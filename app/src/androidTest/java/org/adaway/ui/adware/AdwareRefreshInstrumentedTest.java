package org.adaway.ui.adware;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.R;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.more.MoreFragment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;

/** Disposable UI fixture only; does not launch the system uninstaller or delete packages. */
@RunWith(AndroidJUnit4.class)
public class AdwareRefreshInstrumentedTest {
    private Context context;
    private Fixture data;
    private QueueExecutor scans;
    private AdwareViewModel model;

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "support refresh setup");
    }

    @After
    public void tearDown() {
        InstrumentedTestState.resetForPassiveRootUi(this.context, "support refresh cleanup");
    }

    @Test
    public void returningToScannerRefreshesAndPublishesEmptyState() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                installModel(activity);
                openScanner(activity);
                this.scans.drain();
                assertSingleRow(activity);
            });
            scenario.moveToState(Lifecycle.State.STARTED);
            scenario.onActivity(activity -> this.data.next = Collections.emptyList());
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                assertTrue("Resume must queue a scan.", !this.scans.tasks.isEmpty());
                assertSingleRow(activity);
                this.scans.drain();
                assertEquals(View.GONE, activity.findViewById(R.id.adware_list).getVisibility());
                TextView status = activity.findViewById(R.id.adware_status_text);
                assertEquals(activity.getString(R.string.adware_empty), status.getText().toString());
                assertEquals(View.VISIBLE, status.getVisibility());
            });
        }
    }

    @Test
    public void reopeningScannerUsesSameModelAndRescansAfterAnEmptyResult() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                installModel(activity);
                this.data.next = Collections.emptyList();
                openScanner(activity);
                this.scans.drain();
                assertTrue(this.data.getValue().isEmpty());
                activity.getSupportFragmentManager().beginTransaction()
                        .replace(R.id.nav_fragment_container, new MoreFragment()).commitNow();
                this.data.next = installed();
                openScanner(activity);
                assertSame(this.model,
                        new ViewModelProvider(activity).get(AdwareViewModel.class));
                assertTrue(!this.scans.tasks.isEmpty());
                this.scans.drain();
                assertSingleRow(activity);
            });
        }
    }

    @Test
    public void interceptedUninstallAndCanceledReturnPreserveInstalledRow() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                installModel(activity);
                CapturingAdwareFragment fragment = openScanner(activity);
                this.scans.drain();
                ListView list = activity.findViewById(R.id.adware_list);
                assertTrue(list.performItemClick(null, 0, 0));
                assertNotNull(fragment.intent);
                assertEquals(Intent.ACTION_DELETE, fragment.intent.getAction());
                assertEquals("package:org.adaway.fixture.support", fragment.intent.getDataString());
                assertSingleRow(activity);
            });
            // The intent is intercepted above: simulate return after Cancel with no package change.
            scenario.moveToState(Lifecycle.State.STARTED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                assertSingleRow(activity);
                assertTrue(!this.scans.tasks.isEmpty());
                this.scans.drain();
                assertSingleRow(activity);
            });
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void installedAppHasNormalUninstallPermissionButNotPrivilegedDelete() throws Exception {
        PackageManager pm = this.context.getPackageManager();
        String packageName = this.context.getPackageName();
        assertEquals(PackageManager.PERMISSION_GRANTED,
                pm.checkPermission(Manifest.permission.REQUEST_DELETE_PACKAGES, packageName));
        PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        assertNotNull(info.requestedPermissions);
        List<String> requested = Arrays.asList(info.requestedPermissions);
        assertTrue(requested.contains(Manifest.permission.REQUEST_DELETE_PACKAGES));
        assertFalse(requested.contains(Manifest.permission.DELETE_PACKAGES));
    }

    private void installModel(HomeActivity activity) {
        this.scans = new QueueExecutor();
        this.data = new Fixture(this.scans);
        this.model = new ViewModelProvider(activity, new ViewModelProvider.Factory() {
            @NonNull
            @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                assertEquals(AdwareViewModel.class, modelClass);
                return modelClass.cast(new AdwareViewModel(
                        (Application) context.getApplicationContext(), data));
            }
        }).get(AdwareViewModel.class);
    }

    private static CapturingAdwareFragment openScanner(HomeActivity activity) {
        CapturingAdwareFragment fragment = new CapturingAdwareFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_fragment_container, fragment).commitNow();
        return fragment;
    }

    private static void assertSingleRow(HomeActivity activity) {
        ListView list = activity.findViewById(R.id.adware_list);
        assertEquals(View.VISIBLE, list.getVisibility());
        assertEquals(1, list.getAdapter().getCount());
        AdwareInstall item = (AdwareInstall) list.getAdapter().getItem(0);
        assertEquals("org.adaway.fixture.support", item.get(AdwareInstall.PACKAGE_NAME_KEY));
    }

    private static List<AdwareInstall> installed() {
        return Collections.singletonList(
                new AdwareInstall("Support Fixture", "org.adaway.fixture.support"));
    }

    public static class CapturingAdwareFragment extends AdwareFragment {
        Intent intent;
        @Override public void startActivity(Intent intent) { this.intent = intent; }
    }

    private static final class Fixture extends AdwareLiveData {
        List<AdwareInstall> next = installed();
        Fixture(Executor scans) { super(null, scans, Runnable::run); }
        @Override List<AdwareInstall> loadData() { return new ArrayList<>(this.next); }
    }

    private static final class QueueExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) { this.tasks.add(task); }
        void drain() { while (!this.tasks.isEmpty()) this.tasks.remove().run(); }
    }
}
