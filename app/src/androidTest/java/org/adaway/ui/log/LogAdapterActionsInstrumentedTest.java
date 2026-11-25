package org.adaway.ui.log;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.R;
import org.adaway.db.entity.ListType;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LogAdapterActionsInstrumentedTest {
    @Test
    public void neutralRowDescriptionsAndClicksAgreeForAllThreeTypes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture fixture = new Fixture();
            fixture.bindAndCheck(null, "neutral.example");
        });
    }

    @Test
    public void recycledRowUpdatesRemovalLabelsListenersTintAndHost() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture fixture = new Fixture();
            fixture.bindAndCheck(ListType.ALLOWED, "allow.example");
            fixture.bindAndCheck(ListType.REDIRECTED, "redirect.example");
            fixture.bindAndCheck(ListType.BLOCKED, "block.example");
            fixture.bindAndCheck(null, "reset.example");
        });
    }

    @Test
    public void inheritedDecisionsAreTintedButCannotRemoveRules() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture f = new Fixture();
            for (ListType type : ListType.values()) {
                f.bindAndCheck(type, "personal.example");
                f.adapter.submitList(null);
                f.adapter.submitList(Collections.singletonList(new LogEntry("inherited.example", type, false)));
                f.adapter.onBindViewHolder(f.holder, 0);
                ImageView icon = switch (type) {
                    case BLOCKED -> f.holder.binding.blockImageView;
                    case ALLOWED -> f.holder.binding.allowImageView;
                    case REDIRECTED -> f.holder.binding.redirectionImageView;
                };
                assertNotNull(icon.getColorFilter());
                assertFalse(icon.isClickable());
                assertFalse(icon.performClick());
                f.bindAndCheck(null, "unlisted.example");
            }
        });
    }

    private static final class Fixture {
        final Context context = new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.Theme_AdAway);
        final RecordingCallback callback = new RecordingCallback();
        final LogAdapter adapter = new LogAdapter(this.callback);
        final LogAdapter.ViewHolder holder = this.adapter.onCreateViewHolder(
                new FrameLayout(this.context), 0);

        void bindAndCheck(ListType selected, String host) {
            // Clear the differ first so binding is synchronous while reusing the actual holder.
            this.adapter.submitList(null);
            this.adapter.submitList(Collections.singletonList(new LogEntry(host, selected, selected != null)));
            this.adapter.onBindViewHolder(this.holder, 0);
            check(this.holder.binding.blockImageView, ListType.BLOCKED, selected, host,
                    R.string.tcpdump_entry_add_blacklist, R.string.log_remove_blocked_entry);
            check(this.holder.binding.allowImageView, ListType.ALLOWED, selected, host,
                    R.string.tcpdump_entry_add_whitelist, R.string.log_remove_allowed_entry);
            check(this.holder.binding.redirectionImageView, ListType.REDIRECTED, selected, host,
                    R.string.tcpdump_entry_add_redirect, R.string.log_remove_redirected_entry);
        }

        void check(ImageView icon, ListType type, ListType selected, String host,
                   int addLabel, int removeLabel) {
            boolean removing = type == selected;
            assertEquals(this.context.getString(removing ? removeLabel : addLabel),
                    icon.getContentDescription().toString());
            if (removing) assertNotNull(icon.getColorFilter());
            else assertNull(icon.getColorFilter());
            this.callback.action = null;
            this.callback.host = null;
            this.callback.type = null;
            assertTrue(icon.performClick());
            assertEquals(removing ? "remove" : "add", this.callback.action);
            assertEquals(host, this.callback.host);
            assertEquals(type, this.callback.type);
        }
    }

    private static final class RecordingCallback implements LogViewCallback {
        String action;
        String host;
        ListType type;
        @Override public void addListItem(String hostName, ListType type) {
            this.action = "add";
            this.host = hostName;
            this.type = type;
        }
        @Override public void removeListItem(String hostName, ListType type) {
            this.action = "remove";
            this.host = hostName;
            this.type = type;
        }
        @Override public void openHostInBrowser(String hostName) { fail("Unexpected browser action"); }
        @Override public void copyHostToClipboard(String hostName) { fail("Unexpected copy action"); }
        @Override public int getColor(int colorId) { return 0xff008800; }
    }
}
