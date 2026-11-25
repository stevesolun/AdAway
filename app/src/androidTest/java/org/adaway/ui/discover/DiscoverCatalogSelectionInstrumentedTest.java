package org.adaway.ui.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.adaway.R;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.FilterListCatalog.CatalogEntry;
import org.adaway.model.source.FilterListCategory;
import org.adaway.testing.InstrumentedTestState;
import org.adaway.ui.home.HomeActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/** Direct legacy host only: this does not add or claim a production catalog route. */
@RunWith(AndroidJUnit4.class)
public class DiscoverCatalogSelectionInstrumentedTest {
    private static final long TIMEOUT_MS = 10_000;
    private Context context;
    private ActivityScenario<HomeActivity> scenario;
    private DiscoverCatalogFragment fragment;
    private final CatalogEntry alpha = entry("Keep Alpha", "alpha");
    private final CatalogEntry beta = entry("Keep Beta", "beta");
    private final CatalogEntry gamma = entry("Other Gamma", "gamma");

    @Before
    public void setUp() {
        this.context = ApplicationProvider.getApplicationContext();
        InstrumentedTestState.resetForPassiveRootUi(this.context, "legacy catalog selection");
        this.fragment = new DiscoverCatalogFragment();
        this.scenario = ActivityScenario.launch(HomeActivity.class);
        this.scenario.onActivity(activity -> activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.nav_fragment_container, this.fragment).commitNow());
        waitForUi("catalog load completed", activity -> {
            RecyclerView list = activity.findViewById(R.id.catalogRecyclerView);
            return list != null && list.getAdapter() != null && list.getAdapter().getItemCount() > 0;
        });
        seedRows(Arrays.asList(this.alpha, this.beta, this.gamma));
    }

    @After
    public void tearDown() {
        if (this.scenario != null) this.scenario.close();
        if (this.context != null) {
            InstrumentedTestState.resetForPassiveRootUi(this.context,
                    "legacy catalog selection cleanup");
        }
    }

    @Test
    public void catalogDeselectOneAfterSelectAllKeepsOthers() {
        clickSelectAll();
        assertSelection(this.alpha, this.beta, this.gamma);
        clickRow(1);
        assertSelection(this.alpha, this.gamma);
        assertMasterChecked(false);
        assertRowChecked(0, true);
        assertRowChecked(1, false);
        assertRowChecked(2, true);
    }

    @Test
    public void filteringSelectAllRetainsVisibleSelectionsWithoutResurrectingHiddenRows() {
        clickSelectAll();
        search("Keep");
        assertSelection(this.alpha, this.beta);
        assertMasterChecked(true);
        search("");
        assertSelection(this.alpha, this.beta);
        assertMasterChecked(false);
        assertRowChecked(2, false);
    }

    @Test
    public void filteringIndividualSelectionPrunesHiddenRowAndHidesAddButton() {
        clickRow(0);
        assertSelection(this.alpha);
        search("Beta");
        assertSelection();
        assertMasterChecked(false);
        search("");
        assertSelection();
        assertRowChecked(0, false);
    }

    @Test
    public void selectingEveryRowReflectsMasterAndExplicitClearAllStillClears() {
        clickRow(0);
        clickRow(1);
        clickRow(2);
        assertSelection(this.alpha, this.beta, this.gamma);
        assertMasterChecked(true);
        clickSelectAll();
        assertSelection();
        assertMasterChecked(false);
    }

    @Test
    public void filteredPresetDoesNotSelectHiddenRowsOrRetainFullPresetIdentity() {
        List<CatalogEntry> defaults = FilterListCatalog.getDefaults();
        assertTrue("Fixture needs at least two preset rows", defaults.size() >= 2);
        CatalogEntry first = defaults.get(0);
        seedRows(defaults);
        search(first.url);
        this.scenario.onActivity(activity -> activity.findViewById(R.id.chipSafe).performClick());
        assertSelection(first);
        this.scenario.onActivity(activity -> {
            assertNull(field("selectedPresetProfile"));
            assertTrue(((Set<?>) field("selectedProfileUrls")).isEmpty());
        });
        search("");
        assertSelection(first);
        assertMasterChecked(false);
    }

    @Test
    public void selectAllExcludesAlreadyAddedRowsAndEmptySearchCannotLeaveHiddenSelection() {
        this.scenario.onActivity(activity -> {
            Set<String> existing = field("existingUrls");
            existing.add(this.alpha.url);
            recycler(activity).getAdapter().notifyDataSetChanged();
        });
        clickSelectAll();
        assertSelection(this.beta, this.gamma);
        assertMasterChecked(true);
        search("no-matching-catalog-entry");
        assertSelection();
        assertMasterChecked(false);
        search("");
        assertSelection();
    }

    private void seedRows(List<CatalogEntry> rows) {
        this.scenario.onActivity(activity -> {
            setField("allEntries", new ArrayList<>(rows));
            setField("filteredEntries", new ArrayList<>(rows));
            Set<?> existing = field("existingUrls");
            existing.clear();
            recycler(activity).setItemAnimator(null);
            recycler(activity).getAdapter().notifyDataSetChanged();
        });
    }

    private void clickSelectAll() {
        this.scenario.onActivity(activity -> {
            MaterialSwitch master = activity.findViewById(R.id.catalogSelectAllSwitch);
            assertNotNull(master);
            assertTrue("Select All must be enabled and clickable",
                    master.isEnabled() && master.isClickable());
            boolean wasChecked = master.isChecked();
            // CompoundButton toggles even when no OnClickListener handles the click.
            master.performClick();
            assertEquals("The actual click must change the master checked state",
                    !wasChecked, master.isChecked());
        });
    }

    private void search(String query) {
        this.scenario.onActivity(activity -> ((EditText) activity.findViewById(
                R.id.searchEditText)).setText(query));
    }

    private void clickRow(int position) {
        awaitRow(position);
        this.scenario.onActivity(activity -> assertTrue(row(activity, position)
                .findViewById(R.id.catalogItemCard).performClick()));
    }

    private void assertRowChecked(int position, boolean checked) {
        awaitRow(position);
        this.scenario.onActivity(activity -> assertEquals(checked, ((MaterialSwitch)
                row(activity, position).findViewById(R.id.catalogItemSwitch)).isChecked()));
    }

    private void assertMasterChecked(boolean checked) {
        this.scenario.onActivity(activity -> assertEquals(checked, ((MaterialSwitch)
                activity.findViewById(R.id.catalogSelectAllSwitch)).isChecked()));
    }

    private void assertSelection(CatalogEntry... expected) {
        this.scenario.onActivity(activity -> {
            Set<CatalogEntry> selected = field("selectedEntries");
            assertEquals(new HashSet<>(Arrays.asList(expected)), selected);
            TextView add = activity.findViewById(R.id.addSelectedButton);
            if (expected.length == 0) {
                assertEquals(View.GONE, add.getVisibility());
            } else {
                assertEquals(View.VISIBLE, add.getVisibility());
                assertEquals(activity.getString(R.string.filter_catalog_add_selected_count,
                        expected.length), add.getText().toString());
            }
        });
    }

    private void awaitRow(int position) {
        this.scenario.onActivity(activity -> recycler(activity).scrollToPosition(position));
        waitForUi("bound catalog row " + position, activity -> {
            RecyclerView list = recycler(activity);
            return !list.hasPendingAdapterUpdates()
                    && list.findViewHolderForAdapterPosition(position) != null;
        });
    }

    private void waitForUi(String description, Predicate<HomeActivity> condition) {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicBoolean ready = new AtomicBoolean();
            this.scenario.onActivity(activity -> ready.set(condition.test(activity)));
            if (ready.get()) return;
            SystemClock.sleep(25);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private static RecyclerView recycler(HomeActivity activity) {
        return activity.findViewById(R.id.catalogRecyclerView);
    }

    private static View row(HomeActivity activity, int position) {
        RecyclerView.ViewHolder holder = recycler(activity).findViewHolderForAdapterPosition(position);
        assertNotNull(holder);
        return holder.itemView;
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) {
        try {
            Field field = DiscoverCatalogFragment.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(this.fragment);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private void setField(String name, Object value) {
        try {
            Field field = DiscoverCatalogFragment.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this.fragment, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static CatalogEntry entry(String label, String key) {
        return new CatalogEntry(label, "https://legacy-selection.invalid/" + key,
                FilterListCategory.ADS, false);
    }
}
