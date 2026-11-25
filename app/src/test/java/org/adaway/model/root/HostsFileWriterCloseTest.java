package org.adaway.model.root;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

public class HostsFileWriterCloseTest {
    @Test
    public void fullBufferFlushFailureStillClosesOwnedOutput() throws Exception {
        RecordingOutput output = new RecordingOutput(true, false);
        Closeable writer = writerWithBytes(output, 1024 * 1024);
        assertEquals(0, output.mWrites);

        assertSame(output.mWriteFailure, assertThrows(IOException.class, writer::close));
        assertEquals(1, output.mWrites);
        assertEquals(1, output.mCloses);
    }

    @Test
    public void fullBufferFlushFailureRetainsSuppressedCloseFailure() throws Exception {
        RecordingOutput output = new RecordingOutput(true, true);
        Closeable writer = writerWithBytes(output, 1024 * 1024);
        assertEquals(0, output.mWrites);

        IOException failure = assertThrows(IOException.class, writer::close);
        assertSame(output.mWriteFailure, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(output.mCloseFailure, failure.getSuppressed()[0]);
        assertEquals(1, output.mCloses);
    }

    @Test
    public void partialBufferFlushFailureStillClosesOwnedOutput() throws Exception {
        RecordingOutput output = new RecordingOutput(true, false);
        Closeable writer = writerWithBytes(output, 8);

        assertSame(output.mWriteFailure, assertThrows(IOException.class, writer::close));
        assertEquals(1, output.mCloses);
    }

    @Test
    public void closeOnlyFailureIsPropagated() throws Exception {
        RecordingOutput output = new RecordingOutput(false, true);
        Closeable writer = writerWithBytes(output, 8);

        assertSame(output.mCloseFailure, assertThrows(IOException.class, writer::close));
        assertEquals(8, output.mBytes);
        assertEquals(1, output.mCloses);
    }

    @Test
    public void successfulCloseFlushesAllBytesAndClosesOwnedOutput() throws Exception {
        RecordingOutput output = new RecordingOutput(false, false);
        writerWithBytes(output, 1024 * 1024).close();

        assertEquals(1024 * 1024, output.mBytes);
        assertEquals(1, output.mCloses);
    }

    private static Closeable writerWithBytes(OutputStream output, int size) throws Exception {
        // Exercise the actual private writer without constructing a root model or using a shell.
        Class<?> type = Class.forName("org.adaway.model.root.RootModel$HostsFileWriter");
        Constructor<?> constructor = type.getDeclaredConstructor(OutputStream.class);
        constructor.setAccessible(true);
        Closeable writer = (Closeable) constructor.newInstance(output);
        Method write = type.getDeclaredMethod("write", String.class);
        write.setAccessible(true);
        char[] bytes = new char[size];
        Arrays.fill(bytes, 'a');
        write.invoke(writer, new String(bytes));
        return writer;
    }

    private static final class RecordingOutput extends OutputStream {
        final IOException mWriteFailure = new IOException("write failed");
        final IOException mCloseFailure = new IOException("close failed");
        final boolean mFailWrite;
        final boolean mFailClose;
        int mWrites;
        int mBytes;
        int mCloses;

        RecordingOutput(boolean failWrite, boolean failClose) {
            mFailWrite = failWrite;
            mFailClose = failClose;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[]{(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            ++mWrites;
            if (mFailWrite) {
                throw mWriteFailure;
            }
            mBytes += length;
        }

        @Override
        public void close() throws IOException {
            ++mCloses;
            if (mFailClose) {
                throw mCloseFailure;
            }
        }
    }
}
