package org.adaway.util;

import org.junit.Test;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;

import static org.junit.Assert.*;

public class MetadataBodyTest {
    private static final int LIMIT = 8;
    private static final MediaType JSON = MediaType.get("application/json");

    private static ResponseBody body(String text, long declared) {
        return ResponseBody.create(new Buffer().writeUtf8(text), JSON, declared);
    }

    @Test
    public void belowAndAtLimitAcceptKnownOrUnknownLength() throws IOException {
        for (String text : new String[]{"1234567", "12345678"}) {
            for (long declared : new long[]{-1, text.length()}) {
                assertEquals(text, MetadataBody.read(body(text, declared), LIMIT));
            }
        }
    }

    @Test
    public void declaredOneOverFailsBeforeTheFirstRead() {
        BufferedSource unread = Okio.buffer(new ForwardingSource(new Buffer()) {
            @Override public long read(Buffer sink, long count) {
                throw new AssertionError("Oversize declaration must not read the body");
            }
        });
        assertThrows(IOException.class, () -> MetadataBody.read(
                ResponseBody.create(unread, JSON, LIMIT + 1L), LIMIT));
    }

    @Test
    public void missingOrUnderstatedLengthCannotReturnAValidPrefix() {
        // The first eight bytes alone are a complete, valid JSON object.
        for (long declared : new long[]{-1, LIMIT}) {
            assertThrows(IOException.class, () ->
                    MetadataBody.read(body("{\"id\":1} ", declared), LIMIT));
        }
    }

    @Test
    public void unknownStreamStopsAtFirstOverLimitChunkWithoutReadingToEof() {
        Buffer payload = new Buffer().writeUtf8("123456789abcdefgh");
        BufferedSource chunks = Okio.buffer(new ForwardingSource(payload) {
            @Override public long read(Buffer sink, long count) throws IOException {
                return super.read(sink, Math.min(1L, count));
            }
        });
        assertThrows(IOException.class, () -> MetadataBody.read(
                ResponseBody.create(chunks, JSON, -1), LIMIT));
        assertEquals("abcdefgh", payload.readUtf8());
    }

    @Test
    public void incompleteDeclaredBodyFailsEvenWhenPrefixIsValidJson() {
        assertThrows(IOException.class, () -> MetadataBody.read(body("{}", LIMIT), LIMIT));
    }

    @Test
    public void byteLimitIncludesBomAndPreservesOkHttpDecoding() throws IOException {
        String encoded = "\uFEFF\u00E9"; // 3-byte BOM and a 2-byte UTF-8 character
        assertThrows(IOException.class, () -> MetadataBody.read(body(encoded, -1), 4));
        assertEquals("\u00E9", MetadataBody.read(body(encoded, -1), 5));
    }
}
