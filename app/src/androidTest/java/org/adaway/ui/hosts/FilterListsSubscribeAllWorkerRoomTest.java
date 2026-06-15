package org.adaway.ui.hosts;

import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_ALREADY;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_CANCELLED;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_REVIEW_COUNT;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_REVIEW_PREVIEW;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_SKIPPED_NO_URL;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_SKIPPED_UNSUPPORTED;
import static org.adaway.ui.hosts.FilterListsSubscribeAllWorker.OUTPUT_SUBSCRIBED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Data;

import org.adaway.db.DbTest;
import org.adaway.db.entity.HostsSource;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Connected coverage for the subscribe-all Room write path.
 */
@RunWith(AndroidJUnit4.class)
public class FilterListsSubscribeAllWorkerRoomTest extends DbTest {

    @Test
    public void subscribeAllRecorder_writesCompatibleSourcesAndReturnsExactCounts() {
        FilterListsSubscribeAllWorker.SubscribeAllRecorder recorder =
                FilterListsSubscribeAllWorker.SubscribeAllRecorder.create(hostsSourceDao);

        recorder.accept(77, "New safe", new int[]{1}, new int[]{7}, new int[]{2},
                "https://new.test/hosts.txt");
        recorder.accept("Existing", new int[]{1}, "https://adaway.org/hosts.txt");
        recorder.accept("No URL", new int[]{14}, null);
        recorder.accept("Unsupported", new int[]{3}, "https://abp.test/list.txt");
        recorder.accept("Duplicate new", new int[]{2}, "https://new.test/hosts.txt");
        recorder.flush();

        Data output = recorder.finish(false);

        assertEquals(1, output.getInt(OUTPUT_SUBSCRIBED, -1));
        assertEquals(2, output.getInt(OUTPUT_ALREADY, -1));
        assertEquals(1, output.getInt(OUTPUT_SKIPPED_NO_URL, -1));
        assertEquals(1, output.getInt(OUTPUT_SKIPPED_UNSUPPORTED, -1));
        assertFalse(output.getBoolean(OUTPUT_CANCELLED, true));
        assertEquals(2, output.getInt(OUTPUT_REVIEW_COUNT, -1));
        assertTrue(output.getString(OUTPUT_REVIEW_PREVIEW).contains("No URL"));
        assertTrue(output.getString(OUTPUT_REVIEW_PREVIEW).contains("Unsupported"));

        HostsSource source = hostsSourceDao.getByUrl("https://new.test/hosts.txt")
                .orElse(null);
        assertNotNull(source);
        assertEquals("New safe", source.getLabel());
        assertTrue(source.isEnabled());
        assertFalse(source.isAllowEnabled());
        assertFalse(source.isRedirectEnabled());
        assertEquals(Integer.valueOf(77), source.getFilterListId());
        assertEquals("New safe", source.getFilterListName());
        assertEquals("1", source.getFilterListSyntaxIds());
        assertEquals("7", source.getFilterListTagIds());
        assertEquals("2", source.getFilterListLanguageIds());
        assertEquals("DNS-safe", source.getFilterListCompatibility());
        assertEquals(100, source.getFilterListCompatibilityScore());
        assertEquals("https://new.test/hosts.txt", source.getFilterListSelectedUrl());
        assertFalse(hostsSourceDao.getByUrl("https://abp.test/list.txt").isPresent());
    }
}
