package org.adaway.model.source;

import org.adaway.db.entity.HostsSource;
import org.junit.Test;

import java.time.ZonedDateTime;

import static org.junit.Assert.*;

public class RemoteMetadataObservationTest {
    @Test
    public void failureDoesNotClearLastGoodMetadataOrLoseAPendingUpdate() {
        HostsSource source = new HostsSource();
        ZonedDateTime installed = ZonedDateTime.parse("2026-10-01T00:00:00Z");
        ZonedDateTime newer = installed.plusDays(1);
        source.setLocalModificationDate(installed);
        assertEquals(newer, SourceModel.recordRemoteObservation(source, newer,
                source::setOnlineModificationDate));
        assertEquals(newer, SourceModel.recordRemoteObservation(source, null,
                ignored -> fail("Unavailable observation must not write metadata")));
        assertEquals(newer, source.getOnlineModificationDate());
    }

    @Test
    public void failureWithoutKnownUpdateLeavesWeeklyFallbackIntact() {
        HostsSource source = new HostsSource();
        ZonedDateTime installed = ZonedDateTime.parse("2026-10-01T00:00:00Z");
        source.setLocalModificationDate(installed);
        source.setOnlineModificationDate(installed);
        assertNull(SourceModel.recordRemoteObservation(source, null,
                ignored -> fail("Unavailable observation must not write metadata")));
        assertEquals(installed, source.getOnlineModificationDate());
    }
}
