package org.adaway.ui.home;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.ui.prefs.PrefsUpdateFragment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(AndroidJUnit4.class)
public class HostsStartupCheckInstrumentedTest {
    private static final String URL = "https://startup-fixture.invalid/hosts";
    private static final ZonedDateTime OLD = ZonedDateTime.parse("2020-01-01T00:00:00Z");
    private final AtomicInteger heads = new AtomicInteger();
    private final AtomicInteger otherRequests = new AtomicInteger();
    private final List<String> requestedUrls = new CopyOnWriteArrayList<>();
    private final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    private boolean holdFirst;
    private final List<HostsSource> inserted = new ArrayList<>();
    private AdAwayApplication app;
    private HostsSourceDao dao;
    private List<HostsSource> previouslyEnabled;
    private Object oldHead, oldBody, oldBlock;
    private Field processGate;
    private Object previousAttempt;
    private CountingProtection protection;

    @Before public void setUp() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(app, "startup fixture");
        PreferenceHelper.setAbBlockMethod(app, AdBlockMethod.VPN);
        processGate = HomeActivity.class.getDeclaredField("hostsStartupCheck");
        processGate.setAccessible(true);
        previousAttempt = processGate.get(null);
        processGate.set(null, null);
        dao = AppDatabase.getInstance(app).hostsSourceDao();
        previouslyEnabled = dao.getEnabled();
        for (HostsSource s : previouslyEnabled) dao.setSourceEnabled(s.getId(), false);
        seed(URL);
        OkHttpClient local = new OkHttpClient.Builder().addInterceptor(chain -> {
            String modified = "Thu, 02 Jan 2020 00:00:00 GMT";
            if (chain.request().method().equals("HEAD")
                    && chain.request().url().host().equals("startup-fixture.invalid")) {
                requestedUrls.add(chain.request().url().toString());
                int number = heads.incrementAndGet();
                if (holdFirst && number == 1) {
                    entered.countDown();
                    try {
                        if (!release.await(45, TimeUnit.SECONDS)) throw new IOException("fixture timeout");
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                } else if (holdFirst) {
                    modified = "Fri, 03 Jan 2020 00:00:00 GMT";
                }
            } else {
                otherRequests.incrementAndGet();
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("fixture").header("Last-Modified", modified)
                    .body(ResponseBody.create(null, new byte[0])).build();
        }).build();
        oldHead = swap(SourceModel.class, app.getSourceModel(), "cachedHeadClient", local);
        oldBody = swap(SourceModel.class, app.getSourceModel(), "cachedHttpClient", local);
        protection = new CountingProtection(app);
        oldBlock = swap(AdAwayApplication.class, app, "adBlockModel", protection);
    }

    @After public void tearDown() throws Exception {
        release.countDown();
        awaitIdle();
        swap(SourceModel.class, app.getSourceModel(), "cachedHeadClient", oldHead);
        swap(SourceModel.class, app.getSourceModel(), "cachedHttpClient", oldBody);
        swap(AdAwayApplication.class, app, "adBlockModel", oldBlock);
        for (HostsSource s : inserted) dao.delete(s);
        for (HostsSource s : previouslyEnabled) dao.setSourceEnabled(s.getId(), true);
        processGate.set(null, previousAttempt);
        InstrumentedTestState.resetForPassiveRootUi(app, "startup fixture cleanup");
    }

    @Test public void enabledLauncherChecksMetadataOnceAndRecreationDoesNotDownloadOrApply() {
        toggleHostsPreference();
        assertTrue(PreferenceHelper.getUpdateCheck(app));
        assertFalse(PreferenceHelper.getUpdateCheckAppStartup(app));
        try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(launcher())) {
            awaitIdle();
            assertEquals("enabled startup must probe metadata", 1, heads.get());
            assertTrue(dao.getByUrl(URL).orElseThrow().hasNewerOnlineCopy());
            assertEquals(OLD.toInstant(), dao.getByUrl(URL).orElseThrow()
                    .getLocalModificationDate().toInstant());
            assertHomeCount(home, 1);
            home.recreate();
        }
        try (ActivityScenario<HomeActivity> ignored = ActivityScenario.launch(launcher())) {
            awaitIdle();
            assertEquals(1, heads.get());
            assertNoImport();
        }
    }

    @Test public void disabledHostsPreferenceLeavesMetadataUntouched() {
        toggleHostsPreference();
        toggleHostsPreference();
        assertFalse(PreferenceHelper.getUpdateCheck(app));
        try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(launcher())) {
            home.recreate();
            assertSkipped();
        }
    }

    @Test public void passiveHomeEntryAndBusyLauncherDoNotDeferAProbe() throws Exception {
        toggleHostsPreference();
        try (ActivityScenario<HomeActivity> ignored = ActivityScenario.launch(new Intent(app, HomeActivity.class)
                .setAction(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            assertSkipped();
        }
        try (SourceOperationCoordinator.Lease lease = app.getSourceOperations().acquire();
                ActivityScenario<HomeActivity> ignored = ActivityScenario.launch(launcher())) {
            assertEquals(0, heads.get());
        }
        assertSkipped();
    }

    @Test public void nineSourcesRotateAcrossLaunchesAndHomeShowsFreshness() throws Exception {
        for (int i = 0; i < 8; i++) seed(URL + i);
        toggleHostsPreference(true);
        try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(launcher())) {
            awaitIdle();
            assertEquals(8, heads.get());
            for (int i = 0; i < 8; i++) assertEquals(inserted.get(i).getUrl(), requestedUrls.get(i));
            assertFalse(dao.getByUrl(URL + 7).orElseThrow().hasNewerOnlineCopy());
            assertHomeCount(home, 8);
            home.recreate();
            awaitIdle();
            assertEquals(8, heads.get());
        }
        processGate.set(null, null); // Simulate only the process reset; keep real preferences and Room.
        try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(launcher())) {
            awaitIdle();
            assertEquals(9, heads.get());
            assertEquals(URL + 7, requestedUrls.get(8));
            assertHomeCount(home, 9);
            assertNoImport();
        }
        processGate.set(null, null);
        try (ActivityScenario<HomeActivity> ignored = ActivityScenario.launch(launcher())) {
            awaitIdle();
            assertEquals(17, heads.get());
            assertEquals(requestedUrls.subList(0, 8), requestedUrls.subList(9, 17));
            assertNoImport();
        }
    }

    @Test public void slowStartupDoesNotBlockManualCheckOrOverwriteItsNewerResult() throws Exception {
        for (int i = 0; i < 8; i++) seed(URL + i);
        holdFirst = true;
        toggleHostsPreference();
        try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(launcher())) {
            try {
                assertTrue("startup HEAD reached fixture", entered.await(10, TimeUnit.SECONDS));
                try (var lease = app.getSourceOperations().tryAcquire()) {
                    assertNotNull("manual operation must not wait for startup", lease);
                    assertTrue(app.getSourceModel().runSourceMutation(() -> true));
                }
                home.onActivity(a -> a.findViewById(R.id.nav_sources).performClick());
                home.onActivity(a -> {
                    MaterialToolbar toolbar = a.findViewById(R.id.hosts_sources_toolbar);
                    assertEquals(a.getString(R.string.menu_check_updates), toolbar.getMenu().getItem(0).getTitle());
                    assertTrue(toolbar.showOverflowMenu());
                });
                var input = InstrumentationRegistry.getInstrumentation();
                input.waitForIdleSync();
                home.onActivity(a -> assertTrue(((MaterialToolbar) a.findViewById(
                        R.id.hosts_sources_toolbar)).isOverflowMenuShowing()));
                input.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
                input.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
                long deadline = SystemClock.elapsedRealtime() + 10000;
                while (!dao.getByUrl(URL + 7).orElseThrow().hasNewerOnlineCopy()
                        && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20);
                for (HostsSource s : inserted) assertEquals("manual check completed before release",
                        OLD.plusDays(2).toInstant(),
                        dao.getByUrl(s.getUrl()).orElseThrow().getOnlineModificationDate().toInstant());
            } finally {
                release.countDown();
            }
            awaitIdle();
            assertEquals("one held startup HEAD plus all nine manual HEADs", 10, heads.get());
            assertEquals(OLD.plusDays(2).toInstant(),
                    dao.getByUrl(URL).orElseThrow().getOnlineModificationDate().toInstant());
            home.onActivity(a -> a.findViewById(R.id.nav_home).performClick());
            assertHomeCount(home, 9);
            assertNoImport();
        }
    }

    private void assertHomeCount(ActivityScenario<HomeActivity> home, int n) {
        String expected = app.getResources().getQuantityString(R.plurals.outdated_source_label, n, n);
        AtomicBoolean visible = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + 5000;
        do {
            home.onActivity(a -> {
                TextView count = a.findViewById(R.id.outdatedSourcesTextView);
                visible.set(count != null && count.isShown() && expected.contentEquals(count.getText()));
            });
            if (!visible.get()) SystemClock.sleep(25);
        } while (!visible.get() && SystemClock.elapsedRealtime() < deadline);
        assertTrue("Home must show " + expected, visible.get());
    }

    private Intent launcher() {
        return Intent.makeMainActivity(new android.content.ComponentName(app, HomeActivity.class));
    }

    private void toggleHostsPreference() {
        toggleHostsPreference(false);
    }

    private void toggleHostsPreference(boolean verifyPresentedSummary) {
        try (ActivityScenario<PrefsActivity> settings = ActivityScenario.launch(PrefsActivity.class)) {
            settings.onActivity(a -> {
                PrefsUpdateFragment f = new PrefsUpdateFragment();
                a.getSupportFragmentManager().beginTransaction().replace(android.R.id.content, f).commitNow();
                f.findPreference(app.getString(R.string.pref_update_check_key)).performClick();
                if (verifyPresentedSummary) f.scrollToPreference(app.getString(R.string.pref_update_check_key));
            });
            if (verifyPresentedSummary) assertVisibleSummary();
        }
    }

    private void assertVisibleSummary() {
        String expected = app.getString(R.string.pref_update_check_summary);
        long deadline = SystemClock.elapsedRealtime() + 5000;
        do {
            var root = InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (var node : root.findAccessibilityNodeInfosByText(expected)) {
                    if (node.isVisibleToUser() && node.getText() != null
                            && expected.contentEquals(node.getText())) return;
                }
            }
            SystemClock.sleep(30);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Expected visible summary: " + expected);
    }

    private void seed(String url) {
        assertFalse(dao.getByUrl(url).isPresent());
        HostsSource s = new HostsSource();
        s.setLabel("Startup check"); s.setUrl(url); s.setEnabled(true);
        s.setLocalModificationDate(OLD); s.setOnlineModificationDate(OLD); s.setSize(17);
        dao.insert(s);
        inserted.add(dao.getByUrl(url).orElseThrow());
    }

    private void assertSkipped() {
        awaitIdle();
        assertEquals(0, heads.get());
        assertFalse(dao.getByUrl(URL).orElseThrow().hasNewerOnlineCopy());
        assertNoImport();
    }

    private void assertNoImport() {
        assertEquals("No body or unrelated requests", 0, otherRequests.get());
        assertEquals(0, protection.applies.get());
        for (HostsSource s : inserted) {
            HostsSource saved = dao.getByUrl(s.getUrl()).orElseThrow();
            assertEquals(17, saved.getSize());
            assertEquals(OLD.toInstant(), saved.getLocalModificationDate().toInstant());
        }
    }

    private void awaitIdle() {
        try {
            CompletableFuture<?> check = (CompletableFuture<?>) processGate.get(null);
            if (check != null) check.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("startup check did not finish", e);
        }
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (app.getSourceOperations().isBusy() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(10);
        }
        assertFalse("Startup work must release the source operation", app.getSourceOperations().isBusy());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static Object swap(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        Object old = f.get(target);
        f.set(target, value);
        return old;
    }

    private static final class CountingProtection extends AdBlockModel {
        final AtomicInteger applies = new AtomicInteger();
        CountingProtection(Context c) { super(c); applied.postValue(false); }
        @Override public AdBlockMethod getMethod() { return AdBlockMethod.VPN; }
        @Override public void apply() { applies.incrementAndGet(); }
        @Override public void revert() { }
        @Override public boolean isRecordingLogs() { return false; }
        @Override public void setRecordingLogs(boolean value) { }
        @Override public List<String> getLogs() { return Collections.emptyList(); }
        @Override public void clearLogs() { }
    }
}
