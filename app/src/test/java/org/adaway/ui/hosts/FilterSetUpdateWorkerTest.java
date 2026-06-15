package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;

import androidx.work.Data;

import org.junit.Test;

public class FilterSetUpdateWorkerTest {
    @Test
    public void progressDataUsesHonestSingleBatchTotal() {
        Data progress = FilterSetUpdateWorker.progressData(0, "Updating scheduled sources");

        assertEquals(0, progress.getInt(FilterSetUpdateWorker.PROGRESS_DONE, -1));
        assertEquals(1, progress.getInt(FilterSetUpdateWorker.PROGRESS_TOTAL, -1));
        assertEquals("Updating scheduled sources",
                progress.getString(FilterSetUpdateWorker.PROGRESS_CURRENT));
    }

    @Test
    public void finalProgressPreservesBatchTotal() {
        Data progress = FilterSetUpdateWorker.progressData(1, "Done");

        assertEquals(1, progress.getInt(FilterSetUpdateWorker.PROGRESS_DONE, -1));
        assertEquals(1, progress.getInt(FilterSetUpdateWorker.PROGRESS_TOTAL, -1));
        assertEquals("Done", progress.getString(FilterSetUpdateWorker.PROGRESS_CURRENT));
    }
}
