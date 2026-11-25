package org.adaway.ui.prefs;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;

/** A bounded, in-memory preview; never an editable copy of the system file. */
final class HostsFileSnapshot {
    static final int MAX_BYTES = 64 * 1024;
    final String mText;
    final boolean mTruncated;

    private HostsFileSnapshot(String text, boolean truncated) {
        mText = text;
        mTruncated = truncated;
    }

    /** Takes ownership of the input, including when reading fails or is cancelled. */
    static HostsFileSnapshot read(InputStream source) throws IOException {
        try (InputStream input = source) {
            byte[] bytes = new byte[MAX_BYTES + 1];
            int count = 0;
            while (count < bytes.length) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Hosts preview cancelled.");
                }
                int read = input.read(bytes, count, Math.min(8192, bytes.length - count));
                if (read == -1) {
                    break;
                }
                if (read == 0) {
                    throw new IOException("Hosts preview read made no progress.");
                }
                count += read;
            }
            return new HostsFileSnapshot(new String(bytes, 0, Math.min(count, MAX_BYTES),
                    StandardCharsets.UTF_8), count > MAX_BYTES);
        }
    }
}
