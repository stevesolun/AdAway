package org.adaway.ui.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.model.source.FilterListsDirectoryApi;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.adaway.util.AppExecutors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class DiscoverFilterListsFiltersInstrumentedTest {
    private static final int TIMEOUT_SECONDS = 10;
    private static final String PREFS = "filterlists_cache";

    private Context context;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(context, "set up FilterLists filters");
        AppDatabase.getInstance(context).hostsSourceDao().deleteAll();
        seedFilterListsCache(context);
        drainDiskIo();
    }

    @After
    public void tearDown() throws Exception {
        if (context != null) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit();
            AppDatabase.getInstance(context).hostsSourceDao().deleteAll();
            InstrumentedTestState.resetForPassiveRootUi(context, "tear down FilterLists filters");
        }
    }

    @Test
    public void directorySearchTagLanguageAndDnsSafeControlsUpdateVisibleScope()
            throws Exception {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.navigateTo(R.id.nav_discover));
            waitForAdapterIds(scenario, 101, 102, 103, 104);

            scenario.onActivity(activity -> {
                EditText search = activity.findViewById(R.id.filterlistsSearchEditText);
                assertNotNull(search);
                search.setText("regional");
            });
            waitForAdapterIds(scenario, 101, 102, 103);

            scenario.onActivity(activity -> ((EditText) activity.findViewById(
                    R.id.filterlistsSearchEditText)).setText(""));
            waitForAdapterIds(scenario, 101, 102, 103, 104);

            scenario.onActivity(activity -> clickChip(activity.findViewById(R.id.tagChipGroup),
                    "Regional"));
            waitForAdapterIds(scenario, 101, 102, 103);

            scenario.onActivity(activity -> {
                Spinner spinner = activity.findViewById(R.id.languageSpinner);
                assertNotNull(spinner);
                spinner.setSelection(1);
            });
            waitForAdapterIds(scenario, 101, 102);

            scenario.onActivity(activity -> {
                MaterialSwitch compatibleOnly =
                        activity.findViewById(R.id.filterlistsCompatibleOnlySwitch);
                assertNotNull(compatibleOnly);
                compatibleOnly.setChecked(true);
            });
            waitForAdapterIds(scenario, 101);

            scenario.onActivity(activity -> {
                EditText search = activity.findViewById(R.id.filterlistsSearchEditText);
                assertNotNull(search);
                search.setText("no such list");
            });
            waitForAdapterIds(scenario);
            scenario.onActivity(activity -> {
                View state = activity.findViewById(R.id.filterlistsStateContainer);
                assertNotNull(state);
                assertEquals(View.VISIBLE, state.getVisibility());
            });
        }
    }

    private static void seedFilterListsCache(Context context) {
        long now = System.currentTimeMillis();
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .putString("listsJson", "["
                        + "{\"id\":101,\"name\":\"Regional Ads Safe\","
                        + "\"description\":\"Blocks regional ads\","
                        + "\"syntaxIds\":[1],\"tagIds\":[7],\"languageIds\":[2]},"
                        + "{\"id\":102,\"name\":\"Regional Browser Unsupported\","
                        + "\"description\":\"Browser-only regional rules\","
                        + "\"syntaxIds\":[3],\"tagIds\":[7],\"languageIds\":[2]},"
                        + "{\"id\":103,\"name\":\"Regional German Safe\","
                        + "\"description\":\"Blocks regional ads in German\","
                        + "\"syntaxIds\":[1],\"tagIds\":[7],\"languageIds\":[3]},"
                        + "{\"id\":104,\"name\":\"Privacy Safe\","
                        + "\"description\":\"Blocks tracking\","
                        + "\"syntaxIds\":[1],\"tagIds\":[8],\"languageIds\":[2]}"
                        + "]")
                .putString("syntaxesJson", "["
                        + "{\"id\":1,\"name\":\"Hosts\"},"
                        + "{\"id\":3,\"name\":\"Browser rules\"}"
                        + "]")
                .putString("tagsJson", "["
                        + "{\"id\":7,\"name\":\"Regional\",\"description\":\"Regional lists\"},"
                        + "{\"id\":8,\"name\":\"Privacy\",\"description\":\"Privacy lists\"}"
                        + "]")
                .putString("languagesJson", "["
                        + "{\"id\":2,\"name\":\"English\",\"iso6391\":\"en\"},"
                        + "{\"id\":3,\"name\":\"German\",\"iso6391\":\"de\"}"
                        + "]")
                .putLong("cachedAt", now)
                .putLong("tagsCachedAt", now);
        assertTrue(editor.commit());
    }

    private static void clickChip(ChipGroup chipGroup, String text) {
        assertNotNull(chipGroup);
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View child = chipGroup.getChildAt(i);
            if (child instanceof Chip && text.contentEquals(((Chip) child).getText())) {
                child.performClick();
                return;
            }
        }
        throw new AssertionError("Missing chip " + text);
    }

    private static void waitForAdapterIds(ActivityScenario<HomeActivity> scenario,
                                          Integer... expectedIds) throws Exception {
        Set<Integer> expected = new TreeSet<>(Arrays.asList(expectedIds));
        Field filtered = DiscoverFilterListsFragment.class.getDeclaredField("filtered");
        filtered.setAccessible(true);
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
        Set<Integer> last = null;
        while (SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicReference<Set<Integer>> ids = new AtomicReference<>();
            scenario.onActivity(activity -> {
                RecyclerView recycler = activity.findViewById(R.id.filterlistsRecyclerView);
                Fragment discover = activity.getSupportFragmentManager()
                        .findFragmentById(R.id.nav_fragment_container);
                if (recycler == null || recycler.getAdapter() == null || discover == null) {
                    return;
                }
                Fragment browser = discover.getChildFragmentManager()
                        .findFragmentById(R.id.discoverBrowserContainer);
                if (!(browser instanceof DiscoverFilterListsFragment)) {
                    return;
                }
                try {
                    @SuppressWarnings("unchecked")
                    List<FilterListsDirectoryApi.ListSummary> rows =
                            (List<FilterListsDirectoryApi.ListSummary>) filtered.get(browser);
                    Set<Integer> actual = new TreeSet<>();
                    for (FilterListsDirectoryApi.ListSummary row : rows) actual.add(row.id);
                    assertEquals("Adapter must expose every filtered identity exactly once",
                            actual.size(), recycler.getAdapter().getItemCount());
                    ids.set(actual);
                } catch (IllegalAccessException exception) {
                    throw new AssertionError(exception);
                }
            });
            last = ids.get();
            if (expected.equals(last)) {
                return;
            }
            SystemClock.sleep(100);
        }
        assertEquals("Timed out waiting for FilterLists row identities", expected, last);
    }

    private static void drainDiskIo() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(latch::countDown);
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }
}
