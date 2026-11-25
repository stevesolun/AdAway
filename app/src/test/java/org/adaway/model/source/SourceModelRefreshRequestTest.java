package org.adaway.model.source;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.adaway.db.entity.HostsSource;
import org.junit.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import okhttp3.Request;

public class SourceModelRefreshRequestTest {
    private final ZonedDateTime fetchedAt = ZonedDateTime.now(ZoneOffset.UTC)
            .minusDays(1).truncatedTo(ChronoUnit.SECONDS);

    @Test
    public void newerDateFromCheckCannotValidateThePreviouslyFetchedBody() {
        HostsSource source = previouslyFetchedSource();
        // checkForUpdate records the new Last-Modified before the next GET.
        source.setOnlineModificationDate(fetchedAt.plusHours(1));

        Request request = SourceModel.getRequestFor(source).build();

        assertNull(request.header("If-Modified-Since"));
        assertTrue(request.cacheControl().noCache());
    }

    @Test
    public void fetchedBodyCanStillUseItsKnownValidators() {
        HostsSource source = previouslyFetchedSource();
        ZonedDateTime modifiedAt = fetchedAt.minusHours(1);
        source.setOnlineModificationDate(modifiedAt);
        source.setEntityTag("\"revision-a\"");

        Request request = SourceModel.getRequestFor(source).build();

        assertEquals(modifiedAt.format(RFC_1123_DATE_TIME), request.header("If-Modified-Since"));
        assertEquals("\"revision-a\"", request.header("If-None-Match"));
        assertFalse(request.cacheControl().noCache());
    }

    private HostsSource previouslyFetchedSource() {
        HostsSource source = new HostsSource();
        source.setUrl("https://lists.example/hosts");
        source.setLocalModificationDate(fetchedAt);
        return source;
    }
}
