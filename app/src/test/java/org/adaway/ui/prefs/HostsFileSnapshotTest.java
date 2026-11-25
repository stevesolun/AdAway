package org.adaway.ui.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class HostsFileSnapshotTest {
    @Test
    public void readsUtf8AndClosesInput() throws Exception {
        String hosts = "# \u00e9\n127.0.0.1 localhost\n";
        TrackingInput input = new TrackingInput(hosts.getBytes(StandardCharsets.UTF_8));
        HostsFileSnapshot snapshot = HostsFileSnapshot.read(input);

        assertEquals(hosts, snapshot.mText);
        assertFalse(snapshot.mTruncated);
        assertTrue(input.mClosed);
    }

    @Test
    public void emptyFileIsACompleteSnapshot() throws Exception {
        HostsFileSnapshot snapshot = HostsFileSnapshot.read(new ByteArrayInputStream(new byte[0]));
        assertEquals("", snapshot.mText);
        assertFalse(snapshot.mTruncated);
    }

    @Test
    public void exactLimitIsNotTruncated() throws Exception {
        TrackingInput input = new TrackingInput(asciiBytes(HostsFileSnapshot.MAX_BYTES));
        HostsFileSnapshot snapshot = HostsFileSnapshot.read(input);
        assertEquals(HostsFileSnapshot.MAX_BYTES, snapshot.mText.length());
        assertFalse(snapshot.mTruncated);
        assertTrue(input.mClosed);
    }

    @Test
    public void oversizedFileReadsOnlyLimitPlusOneAndMarksTruncation() throws Exception {
        TrackingInput input = new TrackingInput(asciiBytes(HostsFileSnapshot.MAX_BYTES * 2));
        HostsFileSnapshot snapshot = HostsFileSnapshot.read(input);
        assertEquals(HostsFileSnapshot.MAX_BYTES, snapshot.mText.length());
        assertEquals(HostsFileSnapshot.MAX_BYTES + 1, input.mRead);
        assertTrue(snapshot.mTruncated);
        assertTrue(input.mClosed);
    }

    @Test
    public void readFailureClosesInputAndRetainsSuppressedCloseFailure() {
        IOException readFailure = new IOException("read failed");
        IOException closeFailure = new IOException("close failed");
        InputStream input = new InputStream() {
            @Override
            public int read() throws IOException {
                throw readFailure;
            }

            @Override
            public void close() throws IOException {
                throw closeFailure;
            }
        };
        IOException failure = assertThrows(IOException.class, () -> HostsFileSnapshot.read(input));
        assertSame(readFailure, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(closeFailure, failure.getSuppressed()[0]);
    }

    @Test
    public void zeroProgressFailsInsteadOfSpinningAndClosesInput() {
        TrackingInput input = new TrackingInput(new byte[1]) {
            @Override
            public int read(byte[] bytes, int offset, int length) {
                return 0;
            }
        };
        assertThrows(IOException.class, () -> HostsFileSnapshot.read(input));
        assertTrue(input.mClosed);
    }

    @Test
    public void cancelledReadClosesInputWithoutReading() {
        TrackingInput input = new TrackingInput(asciiBytes(10));
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedIOException.class, () -> HostsFileSnapshot.read(input));
            assertEquals(0, input.mRead);
            assertTrue(input.mClosed);
        } finally {
            Thread.interrupted();
        }
    }

    private static byte[] asciiBytes(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 'a');
        return bytes;
    }

    private static class TrackingInput extends ByteArrayInputStream {
        boolean mClosed;
        int mRead;

        TrackingInput(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) {
            int read = super.read(bytes, offset, length);
            if (read > 0) mRead += read;
            return read;
        }

        @Override
        public void close() throws IOException {
            mClosed = true;
            super.close();
        }
    }
}
