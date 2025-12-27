/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.adaway.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Global executor pools for the whole application.
 * <p>
 * Grouping tasks like this avoids the effects of task starvation (e.g. disk reads don't wait behind
 * webservice requests).
 * Source: https://github.com/googlecodelabs/android-build-an-app-architecture-components/blob/7364e9013419952b00aa582553aa1466c1a1dede/app/src/main/java/com/example/android/sunshine/AppExecutors.java
 */
public class AppExecutors {

    // For Singleton instantiation
    private static final Object LOCK = new Object();
    private static AppExecutors sInstance;
    private final Executor diskIO;
    private final Executor mainThread;
    private final Executor networkIO;

    // Source update pools - hardware-adaptive, reused across updates
    private final ExecutorService checkIO;
    private final ExecutorService downloadIO;
    private final ExecutorService parseCompute;

    // Hardware-adaptive parallelism computed once
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    private static final long MAX_MEMORY = Runtime.getRuntime().maxMemory();
    // CHECK is I/O bound (HEAD requests), so use high parallelism for speed
    private static final int CHECK_PARALLELISM = Math.max(32, Math.min(CPU_CORES * 8, 64));
    private static final int DOWNLOAD_PARALLELISM = Math.max(12, Math.min(CPU_CORES * 4, 32));
    private static final int PARSE_PARALLELISM;
    static {
        long heapMB = MAX_MEMORY / (1024 * 1024);
        if (heapMB < 128) {
            PARSE_PARALLELISM = 2;
        } else if (heapMB < 256) {
            PARSE_PARALLELISM = 4;
        } else if (heapMB < 512) {
            PARSE_PARALLELISM = 6;
        } else {
            PARSE_PARALLELISM = 8;
        }
    }

    private AppExecutors(Executor diskIO, Executor networkIO, Executor mainThread,
                         ExecutorService checkIO, ExecutorService downloadIO, ExecutorService parseCompute) {
        this.diskIO = diskIO;
        this.networkIO = networkIO;
        this.mainThread = mainThread;
        this.checkIO = checkIO;
        this.downloadIO = downloadIO;
        this.parseCompute = parseCompute;
    }

    public static AppExecutors getInstance() {
        if (sInstance == null) {
            synchronized (LOCK) {
                // Double-checked locking: must check again inside synchronized block
                if (sInstance == null) {
                    // Source update pools with descriptive thread names and optimized priorities
                    ExecutorService checkPool = Executors.newFixedThreadPool(CHECK_PARALLELISM,
                            r -> {
                                Thread t = new Thread(r, "CheckWorker");
                                t.setPriority(Thread.NORM_PRIORITY + 1); // I/O bound - higher priority
                                return t;
                            });
                    ExecutorService downloadPool = Executors.newFixedThreadPool(DOWNLOAD_PARALLELISM,
                            r -> {
                                Thread t = new Thread(r, "DownloadWorker");
                                t.setPriority(Thread.NORM_PRIORITY + 1); // I/O bound - higher priority
                                return t;
                            });
                    ExecutorService parsePool = Executors.newFixedThreadPool(PARSE_PARALLELISM + 1,
                            r -> {
                                Thread t = new Thread(r, "ParseWorker");
                                t.setPriority(Thread.NORM_PRIORITY); // CPU bound - normal priority
                                return t;
                            });

                    sInstance = new AppExecutors(
                            Executors.newSingleThreadExecutor(),
                            Executors.newFixedThreadPool(3),
                            new MainThreadExecutor(),
                            checkPool,
                            downloadPool,
                            parsePool
                    );
                }
            }
        }
        return sInstance;
    }

    public Executor diskIO() {
        return diskIO;
    }

    public Executor networkIO() {
        return networkIO;
    }

    public Executor mainThread() {
        return mainThread;
    }

    /**
     * Returns the check I/O pool for HEAD requests during source checking.
     * Pool size is adaptive based on CPU cores.
     */
    public ExecutorService checkIO() {
        return checkIO;
    }

    /**
     * Returns the download I/O pool for downloading filter lists.
     * Pool size is adaptive based on CPU cores.
     */
    public ExecutorService downloadIO() {
        return downloadIO;
    }

    /**
     * Returns the parse compute pool for parsing filter lists.
     * Pool size is adaptive based on available heap memory.
     */
    public ExecutorService parseCompute() {
        return parseCompute;
    }

    /**
     * Returns the parallelism level for check operations.
     */
    public static int getCheckParallelism() {
        return CHECK_PARALLELISM;
    }

    /**
     * Returns the parallelism level for download operations.
     */
    public static int getDownloadParallelism() {
        return DOWNLOAD_PARALLELISM;
    }

    /**
     * Returns the parallelism level for parse operations.
     */
    public static int getParseParallelism() {
        return PARSE_PARALLELISM;
    }

    private static class MainThreadExecutor implements Executor {
        private Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}