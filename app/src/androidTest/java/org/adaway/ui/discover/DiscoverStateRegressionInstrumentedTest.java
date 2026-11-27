package org.adaway.ui.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.SystemClock;
import android.view.View;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.SourceModel;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.testing.UiEvidence;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.ui.source.SourceEditActivity;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Deterministic refresh/lifecycle replay for AUD-SRC-003 through AUD-SRC-006. */
@RunWith(AndroidJUnit4.class)
public class DiscoverStateRegressionInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000;
    private static final String URL_A = "https://example.invalid/discover-state-a.txt";
    private static final String URL_B = "https://example.invalid/discover-state-b.txt";
    private static final String PROFILE_NAME = "Discover state regression profile";
    private static final String LISTS = "["
            + "{\"id\":101,\"name\":\"Regional English Safe\","
            + "\"syntaxIds\":[1],\"tagIds\":[7],\"languageIds\":[2]},"
            + "{\"id\":102,\"name\":\"Regional English Browser\","
            + "\"syntaxIds\":[3],\"tagIds\":[7],\"languageIds\":[2]},"
            + "{\"id\":103,\"name\":\"Regional German Safe\","
            + "\"syntaxIds\":[1],\"tagIds\":[7],\"languageIds\":[3]},"
            + "{\"id\":104,\"name\":\"Privacy English Safe\","
            + "\"syntaxIds\":[1],\"tagIds\":[8],\"languageIds\":[2]}]";
    private static final String REFRESHED_LISTS = LISTS.replace(
            "Regional English Safe", "Regional English Safe refreshed");
    private static final String SYNTAXES = "[{\"id\":1,\"name\":\"Hosts\"},"
            + "{\"id\":3,\"name\":\"Browser rules\"}]";
    private static final String TAGS = "[{\"id\":7,\"name\":\"Regional\"},"
            + "{\"id\":8,\"name\":\"Privacy\"}]";
    private static final String LANGUAGES = "[{\"id\":2,\"name\":\"English\"},"
            + "{\"id\":3,\"name\":\"German\"}]";

    private Context context;
    private SharedPreferences cache;
    private HostsSourceDao sourceDao;
    private SourceModel sourceModel;
    private OkHttpClient previousClient;
    private final CountDownLatch refreshEntered = new CountDownLatch(1);
    private final CountDownLatch releaseRefresh = new CountDownLatch(1);
    private volatile String networkLists = REFRESHED_LISTS;
    private volatile String networkLanguages = LANGUAGES;
    private static final String DETAIL_URL = "https://example.invalid/german-hosts.txt";
    private volatile String detailResponse = "{\"viewUrls\":[{\"url\":\"" + DETAIL_URL + "\"}]}";

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up Discover state replay");
        AppDatabase database = AppDatabase.getInstance(context);
        database.getOpenHelper().getWritableDatabase();
        drainDiskIo();
        sourceDao = database.hostsSourceDao();
        sourceDao.deleteAll();
        cache = context.getSharedPreferences("filterlists_cache", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        assertTrue(cache.edit().clear()
                .putString("listsJson", LISTS).putString("syntaxesJson", SYNTAXES)
                .putString("tagsJson", TAGS).putString("languagesJson", LANGUAGES)
                .putString("listUrl_101", URL_A).putString("listUrl_102", URL_B)
                .putLong("cachedAt", now).putLong("tagsCachedAt", now).commit());
        sourceModel = ((AdAwayApplication) context).getSourceModel();
        Field client = SourceModel.class.getDeclaredField("cachedHttpClient");
        client.setAccessible(true);
        previousClient = (OkHttpClient) client.get(sourceModel);
        client.set(sourceModel, new OkHttpClient.Builder().addInterceptor(chain -> {
            String path = chain.request().url().encodedPath();
            String body;
            switch (path) {
                case "/lists":
                    refreshEntered.countDown();
                    try {
                        if (!releaseRefresh.await(30, TimeUnit.SECONDS)) {
                            throw new IOException("Timed out holding directory refresh");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException(exception);
                    }
                    body = networkLists;
                    break;
                case "/syntaxes": body = SYNTAXES; break;
                case "/tags": body = TAGS; break;
                case "/languages": body = networkLanguages; break;
                case "/lists/103":
                    body = detailResponse;
                    if (body == null) throw new IOException("Directory detail unavailable");
                    break;
                default: throw new IOException("Unexpected fixture request: " + path);
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
        }).build());
    }

    @After
    public void tearDown() throws Exception {
        releaseRefresh.countDown();
        try {
            if (refreshEntered.getCount() == 0) {
                waitFor("refresh persisted before fixture cleanup",
                        () -> cache.getLong("cachedAt", 0) > 0);
            }
            drainDiskIo();
        } finally {
            if (sourceModel != null) {
                Field client = SourceModel.class.getDeclaredField("cachedHttpClient");
                client.setAccessible(true);
                client.set(sourceModel, previousClient);
            }
            if (context != null) {
                if (sourceDao != null) sourceDao.deleteAll();
                if (cache != null) assertTrue(cache.edit().clear().commit());
                FilterSetStore.deleteSet(context, PROFILE_NAME);
                InstrumentedTestState.resetForPassiveRootUi(context,
                        "tear down Discover state replay");
            }
        }
    }

    @Test
    public void lateSubscribedRowCannotOpenAnEditorAfterANewerPick() throws Exception {
        seedSource(101, URL_A);
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        List<Integer> editors = new CopyOnWriteArrayList<>();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (intent.getComponent() == null || !SourceEditActivity.class.getName().equals(
                        intent.getComponent().getClassName())) return null;
                editors.add(intent.getIntExtra(SourceEditActivity.EXTRA_FILTER_LIST_ID, -1));
                return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        CountDownLatch releaseDisk = new CountDownLatch(1);
        inst.addMonitor(monitor);
        try (ActivityScenario<HomeActivity> scenario = launchDirectory()) {
            waitForCount(scenario, 4);
            scrollToRow(scenario, 0);
            waitForUi(scenario, "first row subscribed", activity ->
                    ((MaterialSwitch) row(activity, 0).findViewById(R.id.filterlistsItemSwitch)).isChecked());
            // Hold the first editor's database lookup until the later network lookup launches.
            holdDiskUntil(releaseDisk);
            scenario.onActivity(activity -> assertTrue(row(activity, 0).performClick()));
            scrollToRow(scenario, 2);
            scenario.onActivity(activity -> assertTrue(row(activity, 2).performClick()));
            waitFor("latest editor launched", () -> editors.contains(103));
            releaseDisk.countDown();
            drainDiskIo();
            inst.waitForIdleSync();
            assertEquals("Only the last tapped filter may launch an editor",
                    java.util.Collections.singletonList(103), editors);
            assertEquals("Opening editors must not subscribe or remove a list",
                    1, sourceDao.getAll().stream().filter(s -> s.getFilterListId() != null).count());
        } finally {
            releaseDisk.countDown();
            inst.removeMonitor(monitor);
        }
    }

    @Test
    public void dismissedDirectoryDoesNotLaunchPendingEditorAndCanOpenItOnReturn() throws Exception {
        seedSource(101, URL_A);
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor editor = inst.addMonitor(SourceEditActivity.class.getName(),
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
        CountDownLatch releaseDisk = new CountDownLatch(1);
        try (ActivityScenario<HomeActivity> scenario = launchDirectory()) {
            waitForCount(scenario, 4);
            scrollToRow(scenario, 0);
            waitForUi(scenario, "subscribed row", activity ->
                    ((MaterialSwitch) row(activity, 0).findViewById(R.id.filterlistsItemSwitch)).isChecked());
            holdDiskUntil(releaseDisk);
            scenario.onActivity(activity -> assertTrue(row(activity, 0).performClick()));
            scenario.moveToState(Lifecycle.State.CREATED);
            releaseDisk.countDown();
            drainDiskIo();
            inst.waitForIdleSync();
            assertEquals("Stopped directory opened an editor in the background", 0, editor.getHits());
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> assertTrue(row(activity, 0).performClick()));
            waitFor("fresh tap after returning opens editor", () -> editor.getHits() == 1);
        } finally {
            releaseDisk.countDown();
            inst.removeMonitor(editor);
        }
    }

    @Test
    public void compatibleRowRetriesMissingDetailsAndKeepsDirectoryLabelInEditor() {
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor editor = inst.addMonitor(SourceEditActivity.class.getName(), null, false);
        try (ActivityScenario<HomeActivity> scenario = launchDirectory()) {
            waitForCount(scenario, 4);
            scrollToRow(scenario, 2);
            String valid = detailResponse;
            detailResponse = null;
            scenario.onActivity(activity -> assertTrue(row(activity, 2).performClick()));
            waitForSnackbar(scenario, R.string.filterlists_resolve_url_failed);
            detailResponse = "{\"viewUrls\":[]}";
            scenario.onActivity(activity -> assertTrue(row(activity, 2).performClick()));
            waitForSnackbar(scenario, R.string.filterlists_no_direct_hosts_url);
            assertEquals(0, editor.getHits());
            detailResponse = valid;
            scenario.onActivity(activity -> assertTrue(row(activity, 2).performClick()));
            SourceEditActivity opened = (SourceEditActivity) inst.waitForMonitorWithTimeout(editor, TIMEOUT_MS);
            assertNotNull("Successful retry did not open an editor", opened);
            try {
                inst.runOnMainSync(() -> {
                    assertEquals("Regional German Safe", ((EditText) opened.findViewById(R.id.labelEditText))
                            .getText().toString());
                    assertEquals(DETAIL_URL, ((EditText) opened.findViewById(R.id.location_edit_text))
                            .getText().toString());
                    opened.getOnBackPressedDispatcher().onBackPressed();
                });
                waitForUi(scenario, "Back returned to directory", activity ->
                        activity.hasWindowFocus() && activity.findViewById(R.id.filterlistsRecyclerView) != null);
                assertFalse("Back must not save a subscription", sourceDao.getByUrl(DETAIL_URL).isPresent());
            } finally {
                inst.runOnMainSync(opened::finish);
            }
        } finally {
            inst.removeMonitor(editor);
        }
    }

    private void waitForSnackbar(ActivityScenario<HomeActivity> scenario, int message) {
        waitForUi(scenario, context.getString(message), activity -> {
            TextView snack = activity.findViewById(com.google.android.material.R.id.snackbar_text);
            return snack != null && snack.isShown() && context.getString(message).contentEquals(snack.getText());
        });
    }

    private static void holdDiskUntil(CountDownLatch release) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Test
    public void lateRefreshPreservesUnsubscribeObservedWhileCachedRowsAreVisible()
            throws Exception {
        seedSource(101, URL_A);
        assertLateRefreshPreservesSubscription(false);
    }

    @Test
    public void lateRefreshPreservesSubscribeObservedWhileCachedRowsAreVisible()
            throws Exception {
        assertLateRefreshPreservesSubscription(true);
    }

    @Test
    public void refreshRetainsLanguageWithUnchangedMetadata() throws Exception {
        assertRefreshRetainsLanguage();
    }

    @Test
    public void refreshRetainsLanguageIdAcrossMetadataReordering() throws Exception {
        networkLanguages = "[{\"id\":3,\"name\":\"German\"},"
                + "{\"id\":2,\"name\":\"English\"}]";
        assertRefreshRetainsLanguage();
    }

    @Test
    public void recreationPreservesCheckedIdsAndTagScope() {
        try (ActivityScenario<HomeActivity> scenario = launchDirectory()) {
            selectRegionalEnglishRows(scenario);
            scenario.recreate();
            assertRegionalSelection(scenario);

            // Restoring selection must not weaken the existing hidden-selection pruning rule.
            scenario.onActivity(activity -> chip(activity, 8).performClick());
            waitForCount(scenario, 0);
            scenario.onActivity(activity -> chip(activity, 7).performClick());
            waitForCount(scenario, 2);
            assertChecked(scenario, 0, false);
            assertChecked(scenario, 1, false);
            scenario.onActivity(activity -> assertFalse(activity.findViewById(
                    R.id.filterlistsSubscribeVisibleButton).isEnabled()));
        }
    }

    @Test
    public void parcelledStateKeepsCheckedIdsUntilColdDirectorySnapshotArrives()
            throws Exception {
        try (ActivityScenario<HomeActivity> scenario = launchDirectory()) {
            selectRegionalEnglishRows(scenario);
            assertTrue(cache.edit().remove("listsJson").remove("syntaxesJson")
                    .putLong("cachedAt", 0).commit());
            restoreFromParcel(scenario);
            assertTrue(refreshEntered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            waitForCount(scenario, 0);
            releaseRefresh.countDown();
            assertRegionalSelection(scenario);
        }
    }

    @Test
    public void authoritativeSnapshotPrunesRemovedCheckedIds() throws Exception {
        networkLists = "[{\"id\":101,\"name\":\"Regional English Safe refreshed\","
                + "\"syntaxIds\":[1],\"tagIds\":[7],\"languageIds\":[2]}]";
        try (ActivityScenario<HomeActivity> scenario = launchDirectory()) {
            selectRegionalEnglishRows(scenario);
            assertTrue(cache.edit().remove("listsJson").remove("syntaxesJson")
                    .putLong("cachedAt", 0).commit());
            restoreFromParcel(scenario);
            assertTrue(refreshEntered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            releaseRefresh.countDown();
            waitForCount(scenario, 1);
            assertChecked(scenario, 0, true);

            // Reintroducing a removed list must not resurrect its old checked state.
            assertTrue(cache.edit().putString("listsJson", LISTS).commit());
            restoreFromParcel(scenario);
            waitForCount(scenario, 2);
            assertChecked(scenario, 0, true);
            assertChecked(scenario, 1, false);
        }
    }

    @Test
    public void restoredSubscribedSelectionWaitsForInitialSourceObservation() throws Exception {
        seedSource(101, URL_A);
        seedSource(102, URL_B);
        CountDownLatch releaseSources = new CountDownLatch(1);
        CountDownLatch diskHeld = new CountDownLatch(1);
        try (ActivityScenario<HomeActivity> scenario = launchDirectory()) {
            selectRegionalEnglishRows(scenario);
            scenario.onActivity(activity -> ((MaterialSwitch) activity.findViewById(
                    R.id.filterlistsShowSubscribedSwitch)).setChecked(true));
            waitForCount(scenario, 2);
            AppExecutors.getInstance().diskIO().execute(() -> {
                diskHeld.countDown();
                try {
                    releaseSources.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(diskHeld.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            restoreFromParcel(scenario);
            // Metadata is available, but subscription truth is still held on diskIO.
            waitForUi(scenario, "cached language metadata restored", activity -> {
                Spinner spinner = activity.findViewById(R.id.languageSpinner);
                return spinner != null && "English".equals(spinner.getSelectedItem());
            });
            releaseSources.countDown();
            assertRegionalSelection(scenario);
            scenario.onActivity(activity -> assertTrue(((MaterialSwitch) activity.findViewById(
                    R.id.filterlistsShowSubscribedSwitch)).isChecked()));
        } finally {
            releaseSources.countDown();
        }
    }

    @Test
    public void profileStatusUpdatesAfterChildSubscriptionWithoutLeavingDiscover() {
        seedSource(101, URL_A);
        seedSource(102, URL_B);
        FilterSetStore.saveSet(context, PROFILE_NAME, new HashSet<>(Arrays.asList(URL_A, URL_B)));
        FilterSetStore.setActiveProfile(context, PROFILE_NAME);
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_discover));
            waitForProfile(scenario, R.string.filter_profile_status_exact);
            waitForCount(scenario, 4);
            scrollToRow(scenario, 0);
            waitForUi(scenario, "subscribed child switch", activity -> {
                MaterialSwitch toggle = row(activity, 0).findViewById(R.id.filterlistsItemSwitch);
                return toggle.isChecked();
            });
            scenario.onActivity(activity -> row(activity, 0)
                    .findViewById(R.id.filterlistsItemSwitch).performClick());
            clickDialogAction(context.getString(R.string.filterlists_confirm_unsubscribe_action));
            waitForProfile(scenario, R.string.filter_profile_status_partial);
            assertFalse(sourceDao.getByUrl(URL_A).isPresent());

            seedSource(101, URL_A);
            waitForProfile(scenario, R.string.filter_profile_status_exact);
            String extraUrl = "https://example.invalid/discover-state-extra.txt";
            seedSource(104, extraUrl);
            waitForProfile(scenario, R.string.filter_profile_status_extended);
            sourceDao.setSourceEnabled(sourceDao.getByUrl(extraUrl).orElseThrow().getId(), false);
            waitForProfile(scenario, R.string.filter_profile_status_exact);
        }
    }

    @Test
    public void landscapeDiscoverKeepsFilterRowsReachable() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                activity.navigateTo(R.id.nav_discover);
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            });
            waitForUi(scenario, "landscape Discover", activity ->
                    activity.getResources().getConfiguration().orientation
                            == Configuration.ORIENTATION_LANDSCAPE
                            && activity.findViewById(R.id.filterlistsRecyclerView) != null);
            waitForCount(scenario, 4);
            UiEvidence.capture(context, "discover-landscape");
            waitForUi(scenario, "visible filter list in landscape", activity -> {
                View list = activity.findViewById(R.id.filterlistsRecyclerView);
                Rect visible = new Rect();
                return list.getGlobalVisibleRect(visible)
                        && visible.height() >= 48 * activity.getResources()
                                .getDisplayMetrics().density;
            });
            scrollToRow(scenario, 0);
            Rect target = new Rect();
            scenario.onActivity(activity -> {
                View selection = row(activity, 0).findViewById(R.id.filterlistsItemSelectionCheckBox);
                assertTrue("First row selection must be on screen",
                        visibleScreenRect(selection, target));
            });
            tapCenter(target);
            waitForUi(scenario, "tapped filter checkbox selected", activity -> ((MaterialCheckBox)
                    row(activity, 0).findViewById(R.id.filterlistsItemSelectionCheckBox)).isChecked());
        }
    }

    @Test
    public void enabledBulkActionsAndRowSelectionContrastWithTheirActualSurfaces() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_discover));
            waitForCount(scenario, 4);
            Rect checkbox = new Rect(), selected = new Rect(), all = new Rect(), chip = new Rect();
            waitForUi(scenario, "visible Discover selection controls", activity -> {
                View check = activity.findViewById(R.id.filterlistsItemSelectionCheckBox);
                ChipGroup group = activity.findViewById(R.id.tagChipGroup);
                return check != null && visibleScreenRect(check, checkbox)
                        && visibleScreenRect(activity.findViewById(R.id.filterlistsSubscribeVisibleButton), selected)
                        && visibleScreenRect(activity.findViewById(R.id.filterlistsSubscribeAllButton), all)
                        && group.getCheckedChipId() != View.NO_ID
                        && visibleScreenRect(group.findViewById(group.getCheckedChipId()), chip);
            });
            scenario.onActivity(activity -> {
                assertTrue(activity.findViewById(R.id.filterlistsSubscribeAllButton).isEnabled());
                assertFalse(activity.findViewById(R.id.filterlistsSubscribeVisibleButton).isEnabled());
            });
            Bitmap before = screenshot();
            double disabled = pixelContrast(before, selected);
            try {
                assertTrue("Enabled bulk text", pixelContrast(before, all) >= 4.5);
                assertTrue("Unchecked box outline", pixelContrast(before, checkbox) >= 3);
                // Skip the chip's transparent touch padding and rounded corners.
                chip.inset(chip.width() / 5, chip.height() / 4);
                assertTrue("Selected tag label", pixelContrast(before, chip) >= 4.5);
            } finally {
                before.recycle();
            }
            tapCenter(checkbox);
            waitForUi(scenario, "rebound checked row and enabled actions", activity -> {
                RecyclerView list = activity.findViewById(R.id.filterlistsRecyclerView);
                MaterialCheckBox box = row(activity, 0).findViewById(R.id.filterlistsItemSelectionCheckBox);
                return !list.hasPendingAdapterUpdates() && !list.isAnimating() && box.isChecked()
                        && visibleScreenRect(box, checkbox)
                        && activity.findViewById(R.id.filterlistsSubscribeVisibleButton).isEnabled()
                        && activity.findViewById(R.id.filterlistsRemoveVisibleButton).isEnabled();
            });
            Bitmap after = screenshot();
            try {
                double enabled = pixelContrast(after, selected);
                assertTrue("Selected action must look enabled", enabled >= 4.5 && enabled > disabled);
                assertTrue("Checked box fill", pixelContrast(after, checkbox) >= 3);
            } finally {
                after.recycle();
            }
            tapCenter(selected);
            clickDialogAction(context.getString(R.string.button_cancel));
            assertFalse("Selection and Cancel must not subscribe", sourceDao.getByUrl(URL_A).isPresent());
        }
    }

    private static Bitmap screenshot() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automation.waitForIdle(500, TIMEOUT_MS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("Discover did not settle before the screenshot", e);
        }
        Bitmap shot = automation.takeScreenshot();
        assertNotNull(shot);
        return shot;
    }

    @Test
    public void shortLtrRowMetadataClearsBothControlsBeforeAndAfterSubscription() {
        assertTrue(cache.edit().putString("listsJson", LISTS.replace("Regional English Safe", "Ads")).commit());
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_discover));
            waitForCount(scenario, 4);
            scrollToRow(scenario, 0);
            Rect target = new Rect();
            scenario.onActivity(activity -> {
                View item = row(activity, 0);
                Configuration config = item.getResources().getConfiguration();
                String locale = InstrumentationRegistry.getArguments().getString("uxLocale");
                if (locale != null) {
                    java.util.Locale expected = java.util.Locale.forLanguageTag(locale);
                    assertEquals(expected.getLanguage(), config.getLocales().get(0).getLanguage());
                    assertEquals(android.text.TextUtils.getLayoutDirectionFromLocale(expected),
                            item.getLayoutDirection());
                    assertEquals(Float.parseFloat(InstrumentationRegistry.getArguments()
                            .getString("uxFontScale")), config.fontScale, 0.02f);
                }
                assertRowTextClearsControls(item, false);
                assertTrue(visibleScreenRect(item.findViewById(R.id.filterlistsItemSwitch), target));
            });
            tapCenter(target);
            waitFor("cached row subscribed to correct URL", () -> sourceDao.getByUrl(URL_A).isPresent());
            waitForUi(scenario, "subscribed row laid out", activity -> {
                RecyclerView list = activity.findViewById(R.id.filterlistsRecyclerView);
                return !list.hasPendingAdapterUpdates() && !list.isAnimating()
                        && row(activity, 0).findViewById(R.id.filterlistsItemStatus).getVisibility() == View.VISIBLE;
            });
            scenario.onActivity(activity -> assertRowTextClearsControls(row(activity, 0), true));
        }
    }

    private static void assertRowTextClearsControls(View row, boolean subscribed) {
        Rect box = new Rect(), toggle = new Rect(), text = new Rect();
        row.findViewById(R.id.filterlistsItemSelectionCheckBox).getHitRect(box);
        row.findViewById(R.id.filterlistsItemSwitch).getHitRect(toggle);
        assertEquals("Ads", ((TextView) row.findViewById(R.id.filterlistsItemName)).getText().toString());
        assertEquals("Hosts", ((TextView) row.findViewById(R.id.filterlistsItemSyntax)).getText().toString());
        assertEquals(subscribed ? View.VISIBLE : View.GONE,
                row.findViewById(R.id.filterlistsItemStatus).getVisibility());
        for (int id : new int[]{R.id.filterlistsItemName, R.id.filterlistsItemSyntax, R.id.filterlistsItemStatus}) {
            View label = row.findViewById(id);
            if (label.getVisibility() == View.GONE) continue;
            label.getHitRect(text);
            assertTrue(label.getResources().getResourceEntryName(id) + " must fit between controls: "
                            + text + " vs " + box + " and " + toggle,
                    !text.isEmpty() && text.left >= Math.min(box.right, toggle.right)
                            && text.right <= Math.max(box.left, toggle.left));
        }
        row.findViewById(R.id.filterlistsItemDesc).getHitRect(text);
        assertTrue("Description must start below both touch targets",
                !text.isEmpty() && text.top >= Math.max(box.bottom, toggle.bottom));
    }

    private static double pixelContrast(Bitmap shot, Rect bounds) {
        // These controls have a flat fill. Count solid ink and surface, not antialiased edges.
        Map<Integer, Integer> counts = new HashMap<>();
        int[] pixels = new int[bounds.width() * bounds.height()];
        shot.getPixels(pixels, 0, bounds.width(), bounds.left, bounds.top,
                bounds.width(), bounds.height());
        for (int pixel : pixels) {
            counts.merge(pixel, 1, Integer::sum);
        }
        List<Integer> colors = counts.keySet().stream()
                .sorted((a, b) -> Integer.compare(counts.get(b), counts.get(a)))
                .limit(2).collect(java.util.stream.Collectors.toList());
        return colors.size() < 2 ? 1 : ColorUtils.calculateContrast(colors.get(0), colors.get(1));
    }

    private static boolean visibleScreenRect(View view, Rect target) {
        if (!view.getGlobalVisibleRect(target)) return false;
        // Input injection and screenshots use display coordinates, including any cutout offset.
        int[] origin = new int[2];
        view.getRootView().getLocationOnScreen(origin);
        target.offset(origin[0], origin[1]);
        return true;
    }

    private static void tapCenter(Rect target) {
        long down = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(),
                    action, target.centerX(), target.centerY(), 0);
            try {
                assertTrue(InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .injectInputEvent(event, true));
            } finally {
                event.recycle();
            }
        }
    }

    private void waitForProfile(ActivityScenario<HomeActivity> scenario, int textResource) {
        String expected = context.getString(textResource, PROFILE_NAME);
        waitForUi(scenario, expected, activity -> {
            TextView label = activity.findViewById(R.id.discoverProfileStatus);
            return label != null && expected.contentEquals(label.getText());
        });
    }

    private static void clickDialogAction(String label) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(label);
                try {
                    for (AccessibilityNodeInfo node : matches) {
                        if (node.getText() != null
                                && label.equalsIgnoreCase(node.getText().toString())
                                && node.isClickable()
                                && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            return;
                        }
                    }
                } finally {
                    for (AccessibilityNodeInfo node : matches) node.recycle();
                    root.recycle();
                }
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Dialog action not clickable: " + label);
    }

    private static void selectRegionalEnglishRows(ActivityScenario<HomeActivity> scenario) {
        waitForCount(scenario, 4);
        scenario.onActivity(activity -> {
            chip(activity, 7).performClick();
            ((Spinner) activity.findViewById(R.id.languageSpinner)).setSelection(1);
            ((EditText) activity.findViewById(R.id.filterlistsSearchEditText)).setText("regional");
        });
        waitForCount(scenario, 2);
        for (int position = 0; position < 2; position++) {
            int index = position;
            scrollToRow(scenario, index);
            scenario.onActivity(activity -> ((MaterialCheckBox) row(activity, index)
                    .findViewById(R.id.filterlistsItemSelectionCheckBox)).setChecked(true));
        }
    }

    private static void assertRegionalSelection(ActivityScenario<HomeActivity> scenario) {
        waitForCount(scenario, 2);
        scenario.onActivity(activity -> {
            assertTrue(chip(activity, 7).isChecked());
            assertEquals("English", ((Spinner) activity.findViewById(
                    R.id.languageSpinner)).getSelectedItem());
            assertEquals("regional", ((EditText) activity.findViewById(
                    R.id.filterlistsSearchEditText)).getText().toString());
            assertTrue(activity.findViewById(R.id.filterlistsSubscribeVisibleButton).isEnabled());
            assertTrue(activity.findViewById(R.id.filterlistsRemoveVisibleButton).isEnabled());
        });
        assertChecked(scenario, 0, true);
        assertChecked(scenario, 1, true);
    }

    private static void assertChecked(ActivityScenario<HomeActivity> scenario,
            int position, boolean expected) {
        scrollToRow(scenario, position);
        scenario.onActivity(activity -> assertEquals(expected, ((MaterialCheckBox)
                row(activity, position).findViewById(
                        R.id.filterlistsItemSelectionCheckBox)).isChecked()));
    }

    private static void scrollToRow(ActivityScenario<HomeActivity> scenario, int position) {
        scenario.onActivity(activity -> ((RecyclerView) activity.findViewById(
                R.id.filterlistsRecyclerView)).scrollToPosition(position));
        waitForUi(scenario, "bound row " + position, activity -> ((RecyclerView)
                activity.findViewById(R.id.filterlistsRecyclerView))
                .findViewHolderForAdapterPosition(position) != null);
    }

    private static Chip chip(HomeActivity activity, int tagId) {
        ChipGroup group = activity.findViewById(R.id.tagChipGroup);
        assertNotNull(group);
        for (int i = 0; i < group.getChildCount(); i++) {
            Chip chip = (Chip) group.getChildAt(i);
            if (Integer.valueOf(tagId).equals(chip.getTag())) return chip;
        }
        throw new AssertionError("Missing tag " + tagId);
    }

    private static void restoreFromParcel(ActivityScenario<HomeActivity> scenario) {
        scenario.onActivity(activity -> {
            FragmentManager manager = activity.getSupportFragmentManager();
            Fragment previous = manager.findFragmentById(R.id.nav_fragment_container);
            assertNotNull(previous);
            Fragment.SavedState saved = manager.saveFragmentInstanceState(previous);
            assertNotNull(saved);
            Parcel output = Parcel.obtain();
            Parcel input = Parcel.obtain();
            try {
                saved.writeToParcel(output, 0);
                byte[] bytes = output.marshall();
                input.unmarshall(bytes, 0, bytes.length);
                input.setDataPosition(0);
                DiscoverFilterListsFragment restored = new DiscoverFilterListsFragment();
                restored.setInitialSavedState(Fragment.SavedState.CREATOR.createFromParcel(input));
                manager.beginTransaction().replace(R.id.nav_fragment_container, restored)
                        .commitNow();
            } finally {
                input.recycle();
                output.recycle();
            }
        });
    }

    private void assertRefreshRetainsLanguage() throws Exception {
        expireCache();
        try (ActivityScenario<HomeActivity> scenario = launchDirectory()) {
            assertTrue(refreshEntered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            waitForCount(scenario, 4);
            scenario.onActivity(activity -> ((Spinner) activity.findViewById(
                    R.id.languageSpinner)).setSelection(1));
            waitForCount(scenario, 3);
            releaseRefresh.countDown();
            waitForRefreshedRow(scenario);
            waitForCount(scenario, 3);
            scenario.onActivity(activity -> {
                Spinner spinner = activity.findViewById(R.id.languageSpinner);
                assertEquals("English", spinner.getSelectedItem());
            });
        }
    }

    private void assertLateRefreshPreservesSubscription(boolean subscribe) throws Exception {
        expireCache();
        try (ActivityScenario<HomeActivity> scenario = launchDirectory()) {
            assertTrue(refreshEntered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            waitForCount(scenario, 4);
            scenario.onActivity(activity -> ((MaterialSwitch) activity.findViewById(
                    R.id.filterlistsShowSubscribedSwitch)).setChecked(true));
            waitForCount(scenario, subscribe ? 0 : 1);

            // Simulate a concurrent worker/editor write. Only the DAO observer can update this UI.
            if (subscribe) {
                seedSource(101, URL_A);
            } else {
                sourceDao.delete(sourceDao.getByUrl(URL_A).orElseThrow());
            }
            waitForCount(scenario, subscribe ? 1 : 0);
            releaseRefresh.countDown();

            // A changed row title proves the late response was applied, not just downloaded.
            scenario.onActivity(activity -> ((MaterialSwitch) activity.findViewById(
                    R.id.filterlistsShowSubscribedSwitch)).setChecked(false));
            waitForRefreshedRow(scenario);
            scenario.onActivity(activity -> {
                MaterialSwitch toggle = row(activity, 0).findViewById(R.id.filterlistsItemSwitch);
                assertEquals("Late metadata must not replace observed subscription truth",
                        subscribe, toggle.isChecked());
                ((MaterialSwitch) activity.findViewById(
                        R.id.filterlistsShowSubscribedSwitch)).setChecked(true);
            });
            waitForCount(scenario, subscribe ? 1 : 0);
        }
    }

    private void seedSource(int listId, String url) {
        HostsSource source = new HostsSource();
        source.setLabel("Discover state " + listId);
        source.setUrl(url);
        source.setEnabled(true);
        source.setFilterListId(listId);
        source.setFilterListSelectedUrl(url);
        sourceDao.insert(source);
    }

    private void expireCache() {
        assertTrue(cache.edit().putLong("cachedAt", 0).putLong("tagsCachedAt", 0).commit());
    }

    private static ActivityScenario<HomeActivity> launchDirectory() {
        ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class);
        scenario.onActivity(activity -> activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_fragment_container, new DiscoverFilterListsFragment())
                .commitNow());
        return scenario;
    }

    private static View row(HomeActivity activity, int position) {
        RecyclerView recycler = activity.findViewById(R.id.filterlistsRecyclerView);
        assertNotNull(recycler);
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        assertNotNull("Row " + position + " must be bound", holder);
        return holder.itemView;
    }

    private static void waitForCount(ActivityScenario<HomeActivity> scenario, int expected) {
        waitForUi(scenario, "directory count " + expected, activity -> {
            RecyclerView recycler = activity.findViewById(R.id.filterlistsRecyclerView);
            return recycler != null && recycler.getAdapter() != null
                    && recycler.getAdapter().getItemCount() == expected;
        });
    }

    private static void waitForRefreshedRow(ActivityScenario<HomeActivity> scenario) {
        waitForUi(scenario, "refreshed row bound", activity -> {
            TextView name = activity.findViewById(R.id.filterlistsItemName);
            return name != null
                    && "Regional English Safe refreshed".contentEquals(name.getText());
        });
    }

    private static void waitForUi(ActivityScenario<HomeActivity> scenario, String description,
            Predicate<HomeActivity> condition) {
        waitFor(description, () -> {
            AtomicBoolean ready = new AtomicBoolean();
            scenario.onActivity(activity -> ready.set(condition.test(activity)));
            return ready.get();
        });
    }

    private static void waitFor(String description, BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private static void drainDiskIo() throws InterruptedException {
        CountDownLatch drained = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(drained::countDown);
        assertTrue(drained.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }
}
