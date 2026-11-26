package org.adaway.model.git;

import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.ForwardingSource;
import okio.Okio;

import static org.junit.Assert.*;

public class GitMetadataBodyTest {
    @Test
    public void validObservationThenOversizeReturnsUnavailableAndClosesBothBodies() throws Exception {
        AtomicBoolean oversize = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            Buffer json = new Buffer().writeUtf8("{\"updated_at\":\"2026-10-01T00:00:00Z\"}");
            ForwardingSource bytes = new ForwardingSource(json) {
                @Override public long read(Buffer sink, long count) throws java.io.IOException {
                    assertFalse("Oversize declaration must fail before parse", oversize.get());
                    return super.read(sink, count);
                }
                @Override public void close() throws java.io.IOException {
                    closed.set(true);
                    super.close();
                }
            };
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(Okio.buffer(bytes),
                            MediaType.get("application/json"), oversize.get() ? 1048577L : -1L))
                    .build();
        }).build();
        GistHostsSource gist = new GistHostsSource("https://gist.test/user/sample");
        String url = gist.getCommitApiUrl();
        assertEquals(ZonedDateTime.parse("2026-10-01T00:00:00Z"), gist.getLastUpdateFromApi(url, client));
        assertTrue(closed.getAndSet(false));
        oversize.set(true);
        assertNull(gist.getLastUpdateFromApi(url, client));
        assertTrue(closed.get());
    }
}
