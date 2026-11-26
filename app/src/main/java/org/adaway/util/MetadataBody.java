package org.adaway.util;

import java.io.IOException;

import okhttp3.ResponseBody;
import okio.BufferedSource;

/** Bounds the decoded HTTP entity before handing any text to a JSON parser or cache. */
public final class MetadataBody {
    public static final int DETAIL_BYTES = 1024 * 1024;
    public static final int DIRECTORY_CATALOG_BYTES = 8 * 1024 * 1024;

    private MetadataBody() {
    }

    /** Caller owns the response. Content-Length can be absent, including after transparent gzip. */
    public static String read(ResponseBody body, int limit) throws IOException {
        long declared = body.contentLength();
        if (declared > limit) {
            throw new IOException("Metadata exceeds " + limit + " bytes");
        }
        BufferedSource source = body.source();
        // request() stops at this threshold or EOF; never read an unbounded stream as a string.
        if (source.request((long) limit + 1)) {
            throw new IOException("Metadata exceeds " + limit + " bytes");
        }
        long size = source.getBuffer().size();
        if (declared >= 0 && size != declared) {
            throw new IOException("Metadata length differs from Content-Length");
        }
        // Keep OkHttp's charset and BOM handling, reading only from the complete bounded buffer.
        return ResponseBody.create(source.getBuffer(), body.contentType(), size).string();
    }
}
