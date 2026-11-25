package org.adaway.ui.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.model.source.FilterListsDirectoryApi.ListSummary;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Real adapter inflation/binding with local configuration contexts, not a loading-screen probe. */
@RunWith(AndroidJUnit4.class)
public class DiscoverLabelLayoutInstrumentedTest {
    private static final int LIST_ID = 991701;
    private static final String NAME = "MoaAB comprehensive advertising and privacy protection "
            + "with regional community additions, extended tracking prevention, malware domains, "
            + "and carefully maintained compatibility exceptions for everyday browsing and services";
    private static final String HOSTS_FORMAT = "Hosts entries for IPv4 and IPv6 networks with "
            + "extended regional domain coverage and compatibility annotations";
    private static final String DOMAIN_FORMAT = "Domain names with subdomain coverage, "
            + "community-maintained privacy additions, and documented source format variations";
    private static final String FORMAT = HOSTS_FORMAT + ", " + DOMAIN_FORMAT;

    @Test
    public void portraitNormalFontShowsCompleteNameAndFormat() {
        assertLabels(1.0f, Configuration.ORIENTATION_PORTRAIT);
    }

    @Test
    public void portraitLargeFontShowsCompleteNameAndFormat() {
        assertLabels(1.5f, Configuration.ORIENTATION_PORTRAIT);
    }

    @Test
    public void landscapeNormalFontShowsCompleteNameAndFormat() {
        assertLabels(1.0f, Configuration.ORIENTATION_LANDSCAPE);
    }

    @Test
    public void landscapeLargeFontShowsCompleteNameAndFormat() {
        assertLabels(1.5f, Configuration.ORIENTATION_LANDSCAPE);
    }

    private static void assertLabels(float fontScale, int orientation) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context application = ApplicationProvider.getApplicationContext();
            Configuration configuration = new Configuration(application.getResources()
                    .getConfiguration());
            int shortSide = Math.min(configuration.screenWidthDp, configuration.screenHeightDp);
            int longSide = Math.max(configuration.screenWidthDp, configuration.screenHeightDp);
            assertTrue("The fixture needs real available screen bounds", shortSide > 0);
            configuration.screenWidthDp = orientation == Configuration.ORIENTATION_PORTRAIT
                    ? shortSide : longSide;
            configuration.screenHeightDp = orientation == Configuration.ORIENTATION_PORTRAIT
                    ? longSide : shortSide;
            configuration.orientation = orientation;
            configuration.fontScale = fontScale;
            configuration.setLocale(Locale.US);
            configuration.setLayoutDirection(Locale.US);
            Context context = new ContextThemeWrapper(
                    application.createConfigurationContext(configuration),
                    R.style.Theme_AdAway_NoActionBar_Content);
            assertEquals(fontScale, context.getResources().getConfiguration().fontScale, 0.001f);
            assertEquals(orientation, context.getResources().getConfiguration().orientation);

            DiscoverFilterListsFragment fragment = new ContextOnlyDirectory(context);
            List<ListSummary> filtered = field(fragment, "filtered");
            filtered.add(new ListSummary(LIST_ID, NAME, "Fixture row with real adapter content",
                    new int[]{1, 2}, new int[0], new int[0]));
            Map<Integer, String> syntaxes = new HashMap<>();
            syntaxes.put(1, HOSTS_FORMAT);
            syntaxes.put(2, DOMAIN_FORMAT);
            setField(fragment, "syntaxNames", syntaxes);
            Map<Integer, String> urls = field(fragment, "resolvedUrlCache");
            urls.put(LIST_ID, "https://layout-fixture.invalid/hosts.txt");

            RecyclerView parent = new RecyclerView(context);
            parent.setLayoutManager(new LinearLayoutManager(context));
            RecyclerView.Adapter<DiscoverFilterListsFragment.RowVH> adapter = adapter(fragment);
            assertEquals(1, adapter.getItemCount());
            DiscoverFilterListsFragment.RowVH holder = adapter.createViewHolder(parent, 0);
            adapter.bindViewHolder(holder, 0);
            ViewGroup row = (ViewGroup) holder.itemView;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) row.getLayoutParams();
            int availableWidth = Math.round(configuration.screenWidthDp
                    * context.getResources().getDisplayMetrics().density);
            int rowWidth = availableWidth - params.getMarginStart() - params.getMarginEnd();
            assertTrue("Row must retain a usable width after production margins", rowWidth > 0);
            row.measure(View.MeasureSpec.makeMeasureSpec(rowWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            row.layout(0, 0, rowWidth, row.getMeasuredHeight());
            assertEquals(availableWidth,
                    row.getWidth() + params.getMarginStart() + params.getMarginEnd());

            assertCompleteText(row, holder.name, NAME);
            assertCompleteText(row, holder.syntax, FORMAT);
            Rect name = bounds(row, holder.name);
            Rect syntax = bounds(row, holder.syntax);
            Rect description = bounds(row, holder.desc);
            Rect selection = bounds(row, holder.selectionCheckBox);
            Rect toggle = bounds(row, holder.switchView);
            assertTrue("Format must follow the entire wrapped name", syntax.top >= name.bottom);
            assertTrue("Description must follow the entire wrapped format",
                    description.top >= syntax.bottom);
            assertTrue("Selection control must keep its own column", name.left >= selection.right);
            assertTrue("Subscription control must keep its own column", name.right <= toggle.left);
        });
    }

    private static void assertCompleteText(ViewGroup row, TextView view, String expected) {
        assertEquals("Adapter must bind the entire fixture string", expected, view.getText().toString());
        assertEquals(View.VISIBLE, view.getVisibility());
        assertNull("Identity labels must not ellipsize", view.getEllipsize());
        Layout layout = view.getLayout();
        assertNotNull("A measured text layout is required", layout);
        assertTrue("Long fixture text must actually wrap", layout.getLineCount() > 1);
        int consumed = 0;
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertEquals("Every line must begin where the previous line ended",
                    consumed, layout.getLineStart(line));
            assertEquals("No line may hide an ellipsized range", 0, layout.getEllipsisCount(line));
            consumed = layout.getLineEnd(line);
            assertTrue("A line must consume visible text", consumed > layout.getLineStart(line));
        }
        assertEquals("The final line must include the complete string", expected.length(), consumed);
        assertTrue("The view must fit the complete text layout and padding",
                view.getHeight() >= layout.getHeight()
                        + view.getCompoundPaddingTop() + view.getCompoundPaddingBottom());
        Rect bounds = bounds(row, view);
        assertTrue("The label must have positive measured dimensions",
                bounds.width() > 0 && bounds.height() > 0);
        assertTrue("The label must remain inside the measured row",
                bounds.left >= 0 && bounds.right <= row.getWidth()
                        && bounds.top >= 0 && bounds.bottom <= row.getHeight());
    }

    private static Rect bounds(ViewGroup row, View child) {
        Rect bounds = new Rect(0, 0, child.getWidth(), child.getHeight());
        row.offsetDescendantRectToMyCoords(child, bounds);
        return bounds;
    }

    @SuppressWarnings("unchecked")
    private static RecyclerView.Adapter<DiscoverFilterListsFragment.RowVH> adapter(
            DiscoverFilterListsFragment fragment) {
        try {
            Class<?> type = Class.forName(DiscoverFilterListsFragment.class.getName() + "$Adapter");
            Constructor<?> constructor = type.getDeclaredConstructor(DiscoverFilterListsFragment.class);
            constructor.setAccessible(true);
            return (RecyclerView.Adapter<DiscoverFilterListsFragment.RowVH>) constructor
                    .newInstance(fragment);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(DiscoverFilterListsFragment fragment, String name) {
        try {
            Field field = DiscoverFilterListsFragment.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(fragment);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void setField(DiscoverFilterListsFragment fragment, String name, Object value) {
        try {
            Field field = DiscoverFilterListsFragment.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(fragment, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class ContextOnlyDirectory extends DiscoverFilterListsFragment {
        private final Context context;

        ContextOnlyDirectory(Context context) { this.context = context; }
        @Override public Context getContext() { return this.context; }
    }
}
