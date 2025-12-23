package org.adaway.model.source;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED;
import static org.adaway.model.error.HostError.DOWNLOAD_FAILED;
import static org.adaway.model.error.HostError.NO_CONNECTION;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.time.format.FormatStyle.MEDIUM;
import static java.time.temporal.ChronoUnit.WEEKS;
import static java.util.Objects.requireNonNull;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.converter.ZonedDateTimeConverter;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.git.GitHostsSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;
import timber.log.Timber;

/**
 * This class is the model to represent hosts source management.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class SourceModel {
    /**
     * The HTTP client cache size.
     */
    private static final long CACHE_SIZE = 250L * 1024L * 1024L; // 250MB

    // Hardware-adaptive parallelism - computed once based on device capabilities
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    private static final long MAX_MEMORY = Runtime.getRuntime().maxMemory();

    /**
     * Check parallelism: I/O bound (network HEAD requests), scales with cores.
     * Higher is better since most time is waiting for network.
     */
    private static final int CHECK_PARALLELISM = Math.max(4, Math.min(CPU_CORES * 2, 16));

    /**
     * Download parallelism: Network bound, scales with cores but caps at 12.
     * Too many causes socket exhaustion on some devices.
     */
    private static final int DOWNLOAD_PARALLELISM = Math.max(6, Math.min(CPU_CORES * 2, 12));

    /**
     * Parse parallelism: Memory intensive, based on heap size.
     * Each SourceLoader can use 50-100MB for large lists.
     * - <128MB heap: 2 parsers
     * - 128-256MB heap: 3 parsers
     * - 256-512MB heap: 4 parsers
     * - >512MB heap: 6 parsers
     */
    private static final int PARSE_PARALLELISM;
    static {
        long heapMB = MAX_MEMORY / (1024 * 1024);
        if (heapMB < 128) {
            PARSE_PARALLELISM = 2;
        } else if (heapMB < 256) {
            PARSE_PARALLELISM = 3;
        } else if (heapMB < 512) {
            PARSE_PARALLELISM = 4;
        } else {
            PARSE_PARALLELISM = 6;
        }
    }

    /**
     * Parser threads per source (internal to SourceLoader).
     * Scales with cores but caps at 8 to avoid thread explosion.
     */
    static final int PARSER_THREADS_PER_SOURCE = Math.max(4, Math.min(CPU_CORES, 8));
    private static final String LAST_MODIFIED_HEADER = "Last-Modified";
    private static final String IF_NONE_MATCH_HEADER = "If-None-Match";
    private static final String IF_MODIFIED_SINCE_HEADER = "If-Modified-Since";
    private static final String ENTITY_TAG_HEADER = "ETag";
    private static final String WEAK_ENTITY_TAG_PREFIX = "W/";
    /**
     * The application context.
     */
    private final Context context;
    /**
     * The {@link HostsSource} DAO.
     */
    private final HostsSourceDao hostsSourceDao;
    /**
     * The {@link HostListItem} DAO.
     */
    private final HostListItemDao hostListItemDao;
    /**
     * The {@link HostEntry} DAO.
     */
    private final HostEntryDao hostEntryDao;
    /**
     * The update available status.
     */
    private final MutableLiveData<Boolean> updateAvailable;
    /**
     * The model state.
     */
    private final MutableLiveData<String> state;
    /**
     * The model progress (for in-app percentage display).
     */
    private final MutableLiveData<Progress> progress;
    /**
     * Multi-phase progress for detailed UI (Check/Download/Parse bars).
     */
    private final MutableLiveData<MultiPhaseProgress> multiPhaseProgress;
    /**
     * Builder for multi-phase progress updates.
     */
    private final MultiPhaseProgressBuilder progressBuilder;
    /**
     * The HTTP client to download hosts sources ({@code null} until initialized by {@link #getHttpClient()}).
     */
    private OkHttpClient cachedHttpClient;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public SourceModel(Context context) {
        this.context = context;
        AppDatabase database = AppDatabase.getInstance(this.context);
        this.hostsSourceDao = database.hostsSourceDao();
        this.hostListItemDao = database.hostsListItemDao();
        this.hostEntryDao = database.hostEntryDao();
        // Avoid setValue() here: SourceModel may be instantiated off the main thread (e.g. from WorkManager).
        this.state = new MutableLiveData<>();
        this.state.postValue("");
        this.progress = new MutableLiveData<>();
        this.progress.postValue(Progress.idle());
        this.multiPhaseProgress = new MutableLiveData<>();
        this.multiPhaseProgress.postValue(MultiPhaseProgress.idle());
        this.progressBuilder = new MultiPhaseProgressBuilder();
        this.updateAvailable = new MutableLiveData<>();
        this.updateAvailable.postValue(false);
        SourceUpdateService.syncPreferences(context);
    }

    /**
     * Get the model state.
     *
     * @return The model state.
     */
    public LiveData<String> getState() {
        return this.state;
    }

    /**
     * Get current progress for long-running source operations.
     */
    public LiveData<Progress> getProgress() {
        return this.progress;
    }

    /**
     * Get detailed multi-phase progress (Check/Download/Parse).
     */
    public LiveData<MultiPhaseProgress> getMultiPhaseProgress() {
        return this.multiPhaseProgress;
    }

    /**
     * Request pause of the current update operation.
     */
    public void requestPause() {
        this.progressBuilder.setPaused(true);
    }

    /**
     * Resume a paused update operation.
     */
    public void requestResume() {
        this.progressBuilder.setPaused(false);
    }

    /**
     * Request stop of the current update operation.
     */
    public void requestStop() {
        this.progressBuilder.setStopped(true);
    }

    /**
     * Set the scheduler task name for display in the UI.
     * @param name The scheduler task name.
     */
    public void setSchedulerTaskName(String name) {
        this.progressBuilder.setSchedulerTaskName(name);
    }

    /**
     * Get the update available status.
     *
     * @return {@code true} if source update is available, {@code false} otherwise.
     */
    public LiveData<Boolean> isUpdateAvailable() {
        return this.updateAvailable;
    }

    /**
     * Check if there is update available for hosts sources.
     *
     * @throws HostErrorException If the hosts sources could not be checked.
     */
    public boolean checkForUpdate() throws HostErrorException {
        // Check current connection
        if (isDeviceOffline()) {
            throw new HostErrorException(NO_CONNECTION);
        }
        // Initialize update status
        boolean updateAvailable = false;
        // Get enabled hosts sources
        List<HostsSource> sources = this.hostsSourceDao.getEnabled();
        if (sources.isEmpty()) {
            // Return no update as no source
            this.updateAvailable.postValue(false);
            return false;
        }
        // Update state
        setState(R.string.status_check);
        // Check each source
        for (HostsSource source : sources) {
            // Get URL and lastModified from db
            ZonedDateTime lastModifiedLocal = source.getLocalModificationDate();
            // Update state
            setState(R.string.status_check_source, source.getLabel());
            // Get hosts source last update
            ZonedDateTime lastModifiedOnline = getHostsSourceLastUpdate(source);
            // Some help with debug here
            Timber.d("lastModifiedLocal: %s", dateToString(lastModifiedLocal));
            Timber.d("lastModifiedOnline: %s", dateToString(lastModifiedOnline));
            // Save last modified online
            this.hostsSourceDao.updateOnlineModificationDate(source.getId(), lastModifiedOnline);
            // Check if last modified online retrieved
            if (lastModifiedOnline == null) {
                // If not, consider update is available if install is older than a week
                ZonedDateTime lastWeek = ZonedDateTime.now().minus(1, WEEKS);
                if (lastModifiedLocal != null && lastModifiedLocal.isBefore(lastWeek)) {
                    updateAvailable = true;
                }
            } else {
                // Check if source was never installed or installed before the last update
                if (lastModifiedLocal == null || lastModifiedOnline.isAfter(lastModifiedLocal)) {
                    updateAvailable = true;
                }
            }
        }
        // Update statuses
        Timber.d("Update check result: %s.", updateAvailable);
        if (updateAvailable) {
            setState(R.string.status_update_available);
        } else {
            setState(R.string.status_no_update_found);
        }
        this.updateAvailable.postValue(updateAvailable);
        return updateAvailable;
    }

    /**
     * Format {@link ZonedDateTime} for printing.
     *
     * @param zonedDateTime The date to format.
     * @return The formatted date string.
     */
    private String dateToString(ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return "not defined";
        } else {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(MEDIUM);
            return zonedDateTime + " (" + zonedDateTime.format(dateTimeFormatter) + ")";
        }
    }

    /**
     * Checks if device is offline.
     *
     * @return returns {@code true} if device is offline, {@code false} otherwise.
     */
    private boolean isDeviceOffline() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.context.getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        return netInfo == null || !netInfo.isConnectedOrConnecting();
    }

    /**
     * Get the hosts source last online update.
     *
     * @param source The hosts source to get last online update.
     * @return The last online date, {@code null} if the date could not be retrieved.
     */
    @Nullable
    private ZonedDateTime getHostsSourceLastUpdate(HostsSource source) {
        switch (source.getType()) {
            case URL:
                return getUrlLastUpdate(source);
            case FILE:
                Uri fileUri = Uri.parse(source.getUrl());
                return getFileLastUpdate(fileUri);
            default:
                return null;
        }
    }

    /**
     * Get the url last online update.
     *
     * @param source The source to get last online update.
     * @return The last online date, {@code null} if the date could not be retrieved.
     */
    private ZonedDateTime getUrlLastUpdate(HostsSource source) {
        String url = source.getUrl();
        Timber.v("Checking url last update for source: %s.", url);
        // Check Git hosting
        if (GitHostsSource.isHostedOnGit(url)) {
            try {
                return GitHostsSource.getSource(url).getLastUpdate();
            } catch (MalformedURLException e) {
                Timber.w(e, "Failed to get Git last commit for url %s.", url);
                return null;
            }
        }
        // Default hosting
        Request request = getRequestFor(source).head().build();
        try (Response response = getHttpClient().newCall(request).execute()) {
            String lastModified = response.header(LAST_MODIFIED_HEADER);
            if (lastModified == null) {
                return response.code() == HTTP_NOT_MODIFIED ?
                     source.getOnlineModificationDate() : null;
            }
            return ZonedDateTime.parse(lastModified, RFC_1123_DATE_TIME);
        } catch (IOException | DateTimeParseException e) {
            Timber.e(e, "Exception while fetching last modified date of source %s.", url);
            return null;
        }
    }

    /**
     * Get the file last modified date.
     *
     * @param fileUri The file uri to get last modified date.
     * @return The file last modified date, {@code null} if date could not be retrieved.
     */
    private ZonedDateTime getFileLastUpdate(Uri fileUri) {
        ContentResolver contentResolver = this.context.getContentResolver();
        try (Cursor cursor = contentResolver.query(fileUri, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                Timber.w("The content resolver could not find %s.", fileUri);
                return null;
            }
            int columnIndex = cursor.getColumnIndex(COLUMN_LAST_MODIFIED);
            if (columnIndex == -1) {
                Timber.w("The content resolver does not support last modified column %s.", fileUri);
                return null;
            }
            return ZonedDateTimeConverter.fromTimestamp(cursor.getLong(columnIndex));
        } catch (SecurityException e) {
            Timber.i(e, "The SAF permission was removed.");
            return null;
        }
    }

    /**
     * Retrieve all hosts sources files to copy into a private local file.
     *
     * @throws HostErrorException If the hosts sources could not be downloaded.
     */
    public void retrieveHostsSources() throws HostErrorException {
        // Check connection status
        if (isDeviceOffline()) {
            throw new HostErrorException(NO_CONNECTION);
        }
        // Update state to downloading
        setState(R.string.status_retrieve);

        // Split sources:
        // - Disabled: clear DB entries
        // - Enabled FILE: read sequentially (no network)
        // - Enabled URL: download in parallel (capped) + parse sequentially to avoid DB contention
        List<HostsSource> all = this.hostsSourceDao.getAll();
        List<HostsSource> enabledUrlSources = new ArrayList<>();
        List<HostsSource> enabledFileSources = new ArrayList<>();

        for (HostsSource source : all) {
            if (!source.isEnabled()) {
                int sourceId = source.getId();
                this.hostListItemDao.clearSourceHosts(sourceId);
                this.hostsSourceDao.clearProperties(sourceId);
                continue;
            }
            switch (source.getType()) {
                case URL:
                    enabledUrlSources.add(source);
                    break;
                case FILE:
                    enabledFileSources.add(source);
                    break;
                default:
                    Timber.w("Hosts source type is not supported.");
            }
        }

        final int total = enabledUrlSources.size() + enabledFileSources.size();
        postProgress(0, total, null, total > 0 ? 0 : 0, 0);

        int numberOfCopies = 0;
        int numberOfFailedCopies = 0;
        int done = 0;

        // First handle FILE sources sequentially (they are usually quick and keep UX predictable).
        ZonedDateTime now = ZonedDateTime.now();
        for (HostsSource source : enabledFileSources) {
            postProgress(done, total, source.getLabel(), total > 0 ? (int) Math.floor(done * 10000.0 / total) : 0, 0);
            numberOfCopies++;
            try {
                readSourceFile(source);
                ZonedDateTime onlineModificationDate = getHostsSourceLastUpdate(source);
                if (onlineModificationDate == null) {
                    onlineModificationDate = now;
                }
                ZonedDateTime localModificationDate = onlineModificationDate.isAfter(now) ? onlineModificationDate : now;
                this.hostsSourceDao.updateModificationDates(source.getId(), localModificationDate, onlineModificationDate);
                this.hostsSourceDao.updateSize(source.getId());
            } catch (IOException e) {
                Timber.w(e, "Failed to retrieve host source %s.", source.getUrl());
                numberOfFailedCopies++;
            }
            done++;
            postProgress(done, total, source.getLabel(), total > 0 ? (int) Math.floor(done * 10000.0 / total) : 0, 0);
        }

        // Then handle URL sources with parallel downloads but bounded parallel parsing.
        // Downloads are network-bound (safe to parallelize), but parsing is memory-intensive.
        // We use adaptive parallelism based on hardware capabilities.
        if (!enabledUrlSources.isEmpty()) {
            int downloadParallelism = Math.min(DOWNLOAD_PARALLELISM, enabledUrlSources.size());
            // Parse pool can have more threads, but semaphore limits actual concurrent parsing
            int parsePoolSize = PARSE_PARALLELISM + 1;

            ExecutorService downloadPool = Executors.newFixedThreadPool(downloadParallelism);
            ExecutorService parsePool = Executors.newFixedThreadPool(parsePoolSize);
            CompletionService<DownloadResult> completion = new ExecutorCompletionService<>(downloadPool);

            // Semaphore limits concurrent SourceLoader.parse() calls to prevent OOM
            final Semaphore parseSemaphore = new Semaphore(PARSE_PARALLELISM);

            // Global deduplication set - ensures each host is inserted only once across ALL sources
            final java.util.Set<String> globalSeenHosts = ConcurrentHashMap.newKeySet();

            // Submit all downloads
            for (HostsSource source : enabledUrlSources) {
                completion.submit(new DownloadCallable(source, null));
            }

            // Collect parse futures to wait for them at the end
            List<Future<?>> parseFutures = new ArrayList<>();
            final AtomicInteger failedCount = new AtomicInteger(0);
            final ZonedDateTime nowFinal = now;

            // Process download results as they complete - parsing goes to background
            int urlTotal = enabledUrlSources.size();
            for (int i = 0; i < urlTotal; i++) {
                try {
                    DownloadResult result = completion.take().get();
                    HostsSource source = result.source;
                    numberOfCopies++;

                    // Download finished = done++ (immediate progress update)
                    done++;
                    postProgress(done, total, source.getLabel(), done * 10000 / Math.max(1, total), 0);

                    if (!result.success) {
                        failedCount.incrementAndGet();
                    } else if (!result.notModified) {
                        // Queue parsing in background with semaphore-bounded concurrency
                        final DownloadResult finalResult = result;
                        parseFutures.add(parsePool.submit(() -> {
                            try {
                                // Acquire permit before parsing - blocks if PARSE_PARALLELISM already running
                                parseSemaphore.acquire();
                                try (BufferedReader reader = finalResult.openReader()) {
                                    // Pass globalSeenHosts for cross-source deduplication
                                    parseSourceInputStream(finalResult.source, reader, globalSeenHosts);
                                } finally {
                                    parseSemaphore.release();
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                Timber.w(e, "Parse interrupted for %s", finalResult.source.getUrl());
                            } catch (IOException e) {
                                Timber.w(e, "Failed to parse %s", finalResult.source.getUrl());
                            } finally {
                                finalResult.cleanup();
                            }
                            // Update DB
                            if (finalResult.entityTag != null) {
                                hostsSourceDao.updateEntityTag(finalResult.source.getId(), finalResult.entityTag);
                            }
                            ZonedDateTime onlineMod = finalResult.onlineModificationDate != null ? finalResult.onlineModificationDate : nowFinal;
                            ZonedDateTime localMod = onlineMod.isAfter(nowFinal) ? onlineMod : nowFinal;
                            hostsSourceDao.updateModificationDates(finalResult.source.getId(), localMod, onlineMod);
                            hostsSourceDao.updateSize(finalResult.source.getId());
                        }));
                    }

                } catch (Exception e) {
                    Timber.w(e, "Failed to retrieve a URL host source.");
                    failedCount.incrementAndGet();
                    done++;
                    postProgress(done, total, null, done * 10000 / Math.max(1, total), 0);
                }
            }

            numberOfFailedCopies += failedCount.get();
            downloadPool.shutdown();

            // Wait for all parsing to complete
            setState(R.string.status_parse_source, "Finishing parsing...");
            for (Future<?> f : parseFutures) {
                try {
                    f.get();
                } catch (Exception e) {
                    Timber.w(e, "Parse task failed");
                }
            }
            parsePool.shutdown();
            try {
                parsePool.awaitTermination(10, TimeUnit.MINUTES);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            Timber.i("Global deduplication: %d unique hosts stored (duplicates skipped)", globalSeenHosts.size());
        }

        if (numberOfCopies == numberOfFailedCopies && numberOfCopies != 0) {
            throw new HostErrorException(DOWNLOAD_FAILED);
        }

        // Synchronize hosts entries
        syncHostEntries();
        // Mark no update available
        this.updateAvailable.postValue(false);
        postProgress(total, total, null, 10000, 100);
    }

    /**
     * Check and retrieve hosts sources using a true pipeline architecture.
     * Three concurrent phases run independently:
     * 1. Check phase: Runs in parallel, feeds sources to download queue as they're found
     * 2. Download phase: Pool of workers consuming from download queue, feeding to parse queue
     * 3. Parse phase: Semaphore-bounded pool parsing completed downloads
     *
     * This maximizes throughput by overlapping all three phases.
     *
     * @throws HostErrorException If the hosts sources could not be downloaded.
     */
    public void checkAndRetrieveHostsSources() throws HostErrorException {
        if (isDeviceOffline()) {
            throw new HostErrorException(NO_CONNECTION);
        }

        // Reset progress builder for this operation
        progressBuilder.reset();

        // Get all sources and categorize
        List<HostsSource> all = this.hostsSourceDao.getAll();
        List<HostsSource> enabledUrlSources = new ArrayList<>();
        List<HostsSource> enabledFileSources = new ArrayList<>();

        for (HostsSource source : all) {
            if (!source.isEnabled()) {
                int sourceId = source.getId();
                this.hostListItemDao.clearSourceHosts(sourceId);
                this.hostsSourceDao.clearProperties(sourceId);
                continue;
            }
            switch (source.getType()) {
                case URL:
                    enabledUrlSources.add(source);
                    break;
                case FILE:
                    enabledFileSources.add(source);
                    break;
                default:
                    Timber.w("Hosts source type is not supported.");
            }
        }

        final int totalSources = enabledUrlSources.size() + enabledFileSources.size();
        if (totalSources == 0) {
            this.updateAvailable.postValue(false);
            return;
        }

        // Initialize progress tracking
        progressBuilder.setTotalToCheck(totalSources);
        setState(R.string.status_check);
        postMultiPhaseProgress(progressBuilder.build());

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime lastWeek = now.minus(1, WEEKS);
        final AtomicInteger failedCount = new AtomicInteger(0);

        // Handle FILE sources first (quick, no network) - count as check+download+parse in one
        for (HostsSource source : enabledFileSources) {
            if (progressBuilder.isStopped()) {
                Timber.i("Update stopped by user");
                return;
            }
            while (progressBuilder.isPaused()) {
                try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            }

            progressBuilder.setCurrentLabel(source.getLabel());
            progressBuilder.incrementChecked();
            progressBuilder.incrementTotalToDownload();
            progressBuilder.incrementTotalToParse();
            postMultiPhaseProgress(progressBuilder.build());

            try {
                readSourceFile(source);
                ZonedDateTime onlineModificationDate = getHostsSourceLastUpdate(source);
                if (onlineModificationDate == null) onlineModificationDate = now;
                ZonedDateTime localModificationDate = onlineModificationDate.isAfter(now) ? onlineModificationDate : now;
                this.hostsSourceDao.updateModificationDates(source.getId(), localModificationDate, onlineModificationDate);
                this.hostsSourceDao.updateSize(source.getId());
                progressBuilder.incrementDownloaded();
                progressBuilder.incrementParsed();
            } catch (IOException e) {
                Timber.w(e, "Failed to retrieve host source %s.", source.getUrl());
                failedCount.incrementAndGet();
                progressBuilder.incrementDownloaded();
                progressBuilder.incrementParsed();
            }
            postMultiPhaseProgress(progressBuilder.build());
        }

        // Pipeline architecture for URL sources
        if (!enabledUrlSources.isEmpty()) {
            // Queues for pipeline stages
            LinkedBlockingQueue<HostsSource> downloadQueue = new LinkedBlockingQueue<>();
            LinkedBlockingQueue<DownloadResult> parseQueue = new LinkedBlockingQueue<>();

            // Global deduplication set - ConcurrentHashMap.newKeySet() is thread-safe for concurrent adds
            // This ensures each host is only inserted once across ALL sources
            final java.util.Set<String> globalSeenHosts = ConcurrentHashMap.newKeySet();

            // Adaptive thread pools based on hardware with optimized priorities
            Timber.d("Pipeline start: check=%d, download=%d, parse=%d workers (cores=%d, heap=%dMB)",
                    CHECK_PARALLELISM, DOWNLOAD_PARALLELISM, PARSE_PARALLELISM, CPU_CORES, MAX_MEMORY / (1024 * 1024));
            long pipelineStartTime = System.currentTimeMillis();

            // Check and download are I/O bound - higher priority
            ExecutorService checkPool = Executors.newFixedThreadPool(CHECK_PARALLELISM,
                    r -> {
                        Thread t = new Thread(r, "CheckWorker");
                        t.setPriority(Thread.NORM_PRIORITY + 1);
                        return t;
                    });
            ExecutorService downloadPool = Executors.newFixedThreadPool(DOWNLOAD_PARALLELISM,
                    r -> {
                        Thread t = new Thread(r, "DownloadWorker");
                        t.setPriority(Thread.NORM_PRIORITY + 1);
                        return t;
                    });
            // Parse is CPU bound - normal priority
            ExecutorService parsePool = Executors.newFixedThreadPool(PARSE_PARALLELISM + 1,
                    r -> {
                        Thread t = new Thread(r, "ParseWorker");
                        t.setPriority(Thread.NORM_PRIORITY);
                        return t;
                    });
            Semaphore parseSemaphore = new Semaphore(PARSE_PARALLELISM);

            // Track completion
            AtomicInteger checksRemaining = new AtomicInteger(enabledUrlSources.size());
            AtomicInteger downloadsRemaining = new AtomicInteger(0);
            AtomicInteger sourcesNeedingUpdate = new AtomicInteger(0);
            AtomicReference<Boolean> checkPhaseComplete = new AtomicReference<>(false);
            AtomicReference<Boolean> downloadPhaseComplete = new AtomicReference<>(false);

            final ZonedDateTime nowFinal = now;

            // PHASE 1: Check phase - parallel checking, feeds download queue
            CompletionService<Void> checkCompletion = new ExecutorCompletionService<>(checkPool);
            for (HostsSource source : enabledUrlSources) {
                checkCompletion.submit(() -> {
                    if (progressBuilder.isStopped()) return null;
                    while (progressBuilder.isPaused() && !progressBuilder.isStopped()) {
                        Thread.sleep(100);
                    }

                    progressBuilder.setCurrentLabel(source.getLabel());
                    setState(R.string.status_check_source, source.getLabel());

                    ZonedDateTime lastModifiedLocal = source.getLocalModificationDate();
                    ZonedDateTime lastModifiedOnline = getHostsSourceLastUpdate(source);
                    hostsSourceDao.updateOnlineModificationDate(source.getId(), lastModifiedOnline);

                    progressBuilder.incrementChecked();

                    boolean needsUpdate = false;
                    if (lastModifiedOnline == null) {
                        if (lastModifiedLocal == null || lastModifiedLocal.isBefore(lastWeek)) {
                            needsUpdate = true;
                        }
                    } else {
                        if (lastModifiedLocal == null || lastModifiedOnline.isAfter(lastModifiedLocal)) {
                            needsUpdate = true;
                        }
                    }

                    if (needsUpdate) {
                        sourcesNeedingUpdate.incrementAndGet();
                        downloadsRemaining.incrementAndGet();
                        progressBuilder.incrementTotalToDownload();
                        progressBuilder.incrementTotalToParse();
                        downloadQueue.put(source); // Feed to download queue
                    }

                    postMultiPhaseProgress(progressBuilder.build());
                    checksRemaining.decrementAndGet();
                    return null;
                });
            }

            // PHASE 2: Download workers - consume from download queue, feed parse queue
            for (int i = 0; i < DOWNLOAD_PARALLELISM; i++) {
                downloadPool.submit(() -> {
                    try {
                        while (!progressBuilder.isStopped()) {
                            // Poll with timeout to allow checking completion
                            HostsSource source = downloadQueue.poll(100, TimeUnit.MILLISECONDS);
                            if (source == null) {
                                // Check if check phase is complete and queue is empty
                                if (checkPhaseComplete.get() && downloadQueue.isEmpty()) {
                                    break;
                                }
                                continue;
                            }

                            // CRITICAL: try-finally ensures downloadsRemaining is always decremented
                            // Otherwise, main thread hangs forever waiting for counter to reach 0
                            try {
                                while (progressBuilder.isPaused() && !progressBuilder.isStopped()) {
                                    Thread.sleep(100);
                                }
                                if (progressBuilder.isStopped()) continue;

                                setState(R.string.status_download_source, source.getUrl());
                                DownloadResult result = downloadToTempFile(source, null);
                                progressBuilder.incrementDownloaded();
                                postMultiPhaseProgress(progressBuilder.build());

                                if (result.success && !result.notModified) {
                                    parseQueue.put(result); // Feed to parse queue
                                } else {
                                    if (!result.success) failedCount.incrementAndGet();
                                    progressBuilder.incrementParsed(); // Count as done (no parse needed)
                                    postMultiPhaseProgress(progressBuilder.build());
                                }
                            } finally {
                                downloadsRemaining.decrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            // PHASE 3: Parse workers - consume from parse queue
            for (int i = 0; i < PARSE_PARALLELISM + 1; i++) {
                parsePool.submit(() -> {
                    try {
                        while (!progressBuilder.isStopped()) {
                            // Poll with timeout to allow checking completion
                            DownloadResult result = parseQueue.poll(100, TimeUnit.MILLISECONDS);
                            if (result == null) {
                                // Check if download phase is complete and queue is empty
                                if (downloadPhaseComplete.get() && parseQueue.isEmpty()) {
                                    break;
                                }
                                continue;
                            }

                            try {
                                parseSemaphore.acquire();
                                while (progressBuilder.isPaused() && !progressBuilder.isStopped()) {
                                    Thread.sleep(100);
                                }
                                if (!progressBuilder.isStopped()) {
                                    setState(R.string.status_parse_source, result.source.getLabel());
                                    try (BufferedReader reader = result.openReader()) {
                                        // Pass globalSeenHosts for cross-source deduplication
                                        parseSourceInputStream(result.source, reader, globalSeenHosts);
                                    }
                                    if (result.entityTag != null) {
                                        hostsSourceDao.updateEntityTag(result.source.getId(), result.entityTag);
                                    }
                                    ZonedDateTime onlineMod = result.onlineModificationDate != null ? result.onlineModificationDate : nowFinal;
                                    ZonedDateTime localMod = onlineMod.isAfter(nowFinal) ? onlineMod : nowFinal;
                                    hostsSourceDao.updateModificationDates(result.source.getId(), localMod, onlineMod);
                                    hostsSourceDao.updateSize(result.source.getId());
                                }
                            } catch (IOException e) {
                                Timber.w(e, "Failed to parse %s", result.source.getUrl());
                                failedCount.incrementAndGet();
                            } finally {
                                parseSemaphore.release();
                                result.cleanup();
                            }
                            progressBuilder.incrementParsed();
                            postMultiPhaseProgress(progressBuilder.build());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            // Wait for check phase to complete
            for (int i = 0; i < enabledUrlSources.size(); i++) {
                try {
                    checkCompletion.take();
                } catch (Exception e) {
                    Timber.w(e, "Check task failed");
                }
            }
            checkPhaseComplete.set(true);
            checkPool.shutdown();
            Timber.d("Check phase done in %dms: %d sources need update",
                    System.currentTimeMillis() - pipelineStartTime, sourcesNeedingUpdate.get());

            // Wait for download phase to complete
            while (downloadsRemaining.get() > 0 || !downloadQueue.isEmpty()) {
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            downloadPhaseComplete.set(true);
            downloadPool.shutdown();
            Timber.d("Download phase done in %dms", System.currentTimeMillis() - pipelineStartTime);

            // Wait for parse phase to complete
            try {
                parsePool.shutdown();
                parsePool.awaitTermination(10, TimeUnit.MINUTES);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            long totalTime = System.currentTimeMillis() - pipelineStartTime;
            Timber.i("Pipeline complete in %ds with %d unique hosts", totalTime / 1000, globalSeenHosts.size());
        }

        int totalFailed = failedCount.get();
        MultiPhaseProgress finalProgress = progressBuilder.build();
        int totalDownloaded = finalProgress.downloadedCount;

        if (totalDownloaded == 0 && totalFailed > 0) {
            throw new HostErrorException(DOWNLOAD_FAILED);
        }

        // Synchronize hosts entries
        syncHostEntries();
        this.updateAvailable.postValue(false);
        postProgress(totalSources, totalSources, null, 10000, 100);
        Timber.i("Pipeline update complete: %d sources checked, %d downloaded, %d failed",
                finalProgress.checkedCount, totalDownloaded, totalFailed);
    }

    /**
     * Retrieve a single hosts source (download/read + parse) and synchronize entries.
     * This is used for per-list "Update" actions in the UI.
     *
     * @param sourceId The source id to update.
     * @throws HostErrorException If the hosts source could not be downloaded.
     */
    public void retrieveHostsSource(int sourceId) throws HostErrorException {
        // Check connection status
        if (isDeviceOffline()) {
            throw new HostErrorException(NO_CONNECTION);
        }
        HostsSource source = this.hostsSourceDao.getById(sourceId).orElse(null);
        if (source == null || source.getId() == HostsSource.USER_SOURCE_ID) {
            return;
        }
        postProgress(0, 1, source.getLabel(), 0, 0);
        if (!source.isEnabled()) {
            // Keep DB clean for disabled sources
            this.hostListItemDao.clearSourceHosts(sourceId);
            this.hostsSourceDao.clearProperties(sourceId);
            syncHostEntries();
            postProgress(1, 1, source.getLabel(), 10000, 100);
            return;
        }

        // Compute current date in UTC timezone
        ZonedDateTime now = ZonedDateTime.now();

        // Download/read + parse (URL uses conditional GET; no HEAD pre-check)
        try {
            switch (source.getType()) {
                case URL:
                    DownloadResult result = downloadToTempFile(source, (label, withinFraction) -> {
                        int withinPercent = (int) Math.floor(withinFraction * 100.0);
                        int basisPoints = (int) Math.floor(Math.min(0.999d, Math.max(0d, withinFraction)) * 10000.0);
                        postProgress(0, 1, label, basisPoints, withinPercent);
                    });
                    if (!result.success) {
                        throw new IOException("Download failed");
                    }
                    if (!result.notModified) {
                        try (BufferedReader reader = result.openReader()) {
                            parseSourceInputStream(source, reader);
                        }
                    }
                    if (result.entityTag != null) {
                        this.hostsSourceDao.updateEntityTag(source.getId(), result.entityTag);
                    }
                    ZonedDateTime onlineModificationDate = result.onlineModificationDate != null ? result.onlineModificationDate : now;
                    ZonedDateTime localModificationDate = onlineModificationDate.isAfter(now) ? onlineModificationDate : now;
                    this.hostsSourceDao.updateModificationDates(sourceId, localModificationDate, onlineModificationDate);
                    this.hostsSourceDao.updateSize(sourceId);
                    result.cleanup();
                    break;
                case FILE:
                    readSourceFile(source);
                    ZonedDateTime onlineModificationDateFile = getHostsSourceLastUpdate(source);
                    if (onlineModificationDateFile == null) {
                        onlineModificationDateFile = now;
                    }
                    ZonedDateTime localModificationDateFile = onlineModificationDateFile.isAfter(now) ? onlineModificationDateFile : now;
                    this.hostsSourceDao.updateModificationDates(sourceId, localModificationDateFile, onlineModificationDateFile);
                    this.hostsSourceDao.updateSize(sourceId);
                    break;
                default:
                    Timber.w("Hosts source type is not supported.");
                    return;
            }
        } catch (IOException e) {
            Timber.w(e, "Failed to retrieve host source %s.", source.getUrl());
            throw new HostErrorException(DOWNLOAD_FAILED);
        }

        // Synchronize hosts entries
        syncHostEntries();
        // Mark no update available
        this.updateAvailable.postValue(false);
        postProgress(1, 1, source.getLabel(), 10000, 100);
    }

    /**
     * Synchronize hosts entries from current source states.
     */
    public void syncHostEntries() {
        setState(R.string.status_sync_database);
        this.hostEntryDao.sync();
    }

    /**
     * Get the HTTP client to download hosts sources.
     * Configured for high parallelism with optimized dispatcher and connection pool.
     *
     * @return The HTTP client to download hosts sources.
     */
    @NonNull
    private OkHttpClient getHttpClient() {
        if (this.cachedHttpClient == null) {
            File cacheDir = new File(this.context.getCacheDir(), "okhttp");

            // Configure dispatcher for high parallelism - default limits (64 total, 5 per host) are too low
            okhttp3.Dispatcher dispatcher = new okhttp3.Dispatcher();
            dispatcher.setMaxRequests(DOWNLOAD_PARALLELISM * 2);  // 24 for typical device
            dispatcher.setMaxRequestsPerHost(DOWNLOAD_PARALLELISM);  // 12 for typical device

            // Configure connection pool to support parallel connections
            okhttp3.ConnectionPool connectionPool = new okhttp3.ConnectionPool(
                    DOWNLOAD_PARALLELISM * 2,  // Max idle connections
                    5, TimeUnit.MINUTES        // Keep-alive duration
            );

            this.cachedHttpClient = new OkHttpClient.Builder()
                    .dispatcher(dispatcher)
                    .connectionPool(connectionPool)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .cache(new Cache(cacheDir, CACHE_SIZE))
                    .build();
        }
        return this.cachedHttpClient;
    }

    /**
     * Expose the cached HTTP client for UI helpers.
     * (Used by FilterLists.com directory importer.)
     */
    @NonNull
    public OkHttpClient getHttpClientForUi() {
        return getHttpClient();
    }

    /**
     * Get request builder for an hosts source.
     * All cache data available are filled into the headers.
     *
     * @param source The hosts source to get request builder.
     * @return The hosts source request builder.
     */
    private Request.Builder getRequestFor(HostsSource source) {
        Request.Builder request = new Request.Builder().url(source.getUrl());
        if (source.getEntityTag() != null) {
            request = request.header(IF_NONE_MATCH_HEADER, source.getEntityTag());
        }
        if (source.getOnlineModificationDate() != null) {
            String lastModified = source.getOnlineModificationDate().format(RFC_1123_DATE_TIME);
            request = request.header(IF_MODIFIED_SINCE_HEADER, lastModified);
        }
        return request;
    }

    /**
     * Download an hosts source file and append it to the database.
     *
     * @param source The hosts source to download.
     * @throws IOException If the hosts source could not be downloaded.
     */
    private void downloadHostSource(HostsSource source) throws IOException {
        // Get hosts file URL
        String hostsFileUrl = source.getUrl();
        Timber.v("Downloading hosts file: %s.", hostsFileUrl);
        // Set state to downloading hosts source
        setState(R.string.status_download_source, hostsFileUrl);
        // Create request
        Request request = getRequestFor(source).build();
        // Request hosts file and open byte stream
        try (Response response = getHttpClient().newCall(request).execute()) {
            ResponseBody rawBody = requireNonNull(response.body());
            // Skip source parsing if not modified
            if (response.code() == HTTP_NOT_MODIFIED) {
                Timber.d("Source %s was not updated since last fetch.", source.getUrl());
                return;
            }
            // Extract ETag if present
            String entityTag = response.header(ENTITY_TAG_HEADER);
            if (entityTag != null) {
                if (entityTag.startsWith(WEAK_ENTITY_TAG_PREFIX)) {
                    entityTag = entityTag.substring(WEAK_ENTITY_TAG_PREFIX.length());
                }
                this.hostsSourceDao.updateEntityTag(source.getId(), entityTag);
            }
            // Parse source (with progress updates while reading bytes).
            ResponseBody body = new ProgressResponseBody(rawBody, (bytesRead, contentLength, done) -> {
                // Best-effort "within current source" progress fraction.
                double within;
                if (contentLength > 0L) {
                    within = Math.min(0.999d, Math.max(0d, bytesRead / (double) contentLength));
                } else {
                    // Unknown content-length: still advance smoothly as bytes are read.
                    // 1 - exp(-x/k) grows quickly then slows down (bounded).
                    final double k = 750_000d; // ~0.75MB
                    within = 1d - Math.exp(-(bytesRead / k));
                    within = Math.min(0.95d, Math.max(0d, within));
                }
                // Convert to overall percent using current "done/total sources" progress as base.
                Progress p = this.progress.getValue();
                int doneSources = p != null ? p.done : 0;
                int totalSources = p != null ? p.total : 0;
                if (totalSources <= 0) {
                    return;
                }
                // doneSources is "completed so far"; while reading current source, we are between doneSources and doneSources+1.
                double overall = (doneSources + within) / (double) totalSources;
                int basisPoints = (int) Math.floor(overall * 10000.0);
                int withinPercent = (int) Math.floor(within * 100.0);
                // Re-post progress to force UI refresh even if done/total unchanged.
                postProgress(doneSources, totalSources, source.getLabel(), basisPoints, withinPercent);
            });
            try (Reader reader = body.charStream();
                 BufferedReader bufferedReader = new BufferedReader(reader)) {
                parseSourceInputStream(source, bufferedReader);
            }
        } catch (IOException e) {
            throw new IOException("Exception while downloading hosts file from " + hostsFileUrl + ".", e);
        }
    }

    /**
     * Progress callback used during parallel download.
     */
    private interface DownloadProgress {
        void onProgress(@NonNull String label, double withinFraction);
    }

    /**
     * Download a URL source to a temp file using a single conditional GET.
     * Returns 304/notModified when applicable (no temp file needed).
     */
    @NonNull
    private DownloadResult downloadToTempFile(@NonNull HostsSource source, @Nullable DownloadProgress progress) {
        String url = source.getUrl();
        setState(R.string.status_download_source, url);

        Request request = getRequestFor(source).build();
        File tmp = null;
        try (Response response = getHttpClient().newCall(request).execute()) {
            if (response.code() == HTTP_NOT_MODIFIED) {
                return DownloadResult.notModified(source);
            }
            ResponseBody body = response.body();
            if (body == null) {
                return DownloadResult.failed(source);
            }

            String entityTag = response.header(ENTITY_TAG_HEADER);
            if (entityTag != null && entityTag.startsWith(WEAK_ENTITY_TAG_PREFIX)) {
                entityTag = entityTag.substring(WEAK_ENTITY_TAG_PREFIX.length());
            }

            ZonedDateTime lastModified = null;
            String lastModifiedHeader = response.header(LAST_MODIFIED_HEADER);
            if (lastModifiedHeader != null) {
                try {
                    lastModified = ZonedDateTime.parse(lastModifiedHeader, RFC_1123_DATE_TIME);
                } catch (DateTimeParseException ignored) {
                    lastModified = null;
                }
            }

            // Write response to temp file
            tmp = File.createTempFile("adaway-src-", ".txt", this.context.getCacheDir());
            try (InputStream in = body.byteStream();
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[32 * 1024];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
                out.flush();
            }

            return DownloadResult.success(source, tmp, entityTag, lastModified);
        } catch (IOException e) {
            Timber.w(e, "Download failed for %s", url);
            if (tmp != null) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            return DownloadResult.failed(source);
        }
    }

    /**
     * Download result (URL source only).
     */
    private static final class DownloadResult {
        final HostsSource source;
        final int sourceId;
        final boolean success;
        final boolean notModified;
        @Nullable
        final File tmpFile;
        @Nullable
        final String entityTag;
        @Nullable
        final ZonedDateTime onlineModificationDate;

        private DownloadResult(@NonNull HostsSource source,
                               boolean success,
                               boolean notModified,
                               @Nullable File tmpFile,
                               @Nullable String entityTag,
                               @Nullable ZonedDateTime onlineModificationDate) {
            this.source = source;
            this.sourceId = source.getId();
            this.success = success;
            this.notModified = notModified;
            this.tmpFile = tmpFile;
            this.entityTag = entityTag;
            this.onlineModificationDate = onlineModificationDate;
        }

        static DownloadResult notModified(@NonNull HostsSource source) {
            return new DownloadResult(source, true, true, null, null, source.getOnlineModificationDate());
        }

        static DownloadResult failed(@NonNull HostsSource source) {
            return new DownloadResult(source, false, false, null, null, null);
        }

        static DownloadResult success(@NonNull HostsSource source,
                                      @NonNull File tmpFile,
                                      @Nullable String entityTag,
                                      @Nullable ZonedDateTime onlineModificationDate) {
            return new DownloadResult(source, true, false, tmpFile, entityTag, onlineModificationDate);
        }

        BufferedReader openReader() throws IOException {
            if (tmpFile == null) throw new IOException("No temp file");
            return new BufferedReader(new InputStreamReader(new java.io.FileInputStream(tmpFile)));
        }

        void cleanup() {
            if (tmpFile != null) {
                //noinspection ResultOfMethodCallIgnored
                tmpFile.delete();
            }
        }
    }

    /**
     * Callable wrapper so we can parallelize downloads.
     */
    private final class DownloadCallable implements Callable<DownloadResult> {
        private final HostsSource source;

        DownloadCallable(@NonNull HostsSource source, @Nullable DownloadProgress ignored) {
            this.source = source;
        }

        @Override
        public DownloadResult call() {
            return downloadToTempFile(source, null);
        }
    }

    /**
     * Listener for bytes read while downloading a URL source.
     */
    private interface ProgressListener {
        void update(long bytesRead, long contentLength, boolean done);
    }

    /**
     * Wraps an OkHttp ResponseBody to report incremental read progress.
     */
    private static final class ProgressResponseBody extends ResponseBody {
        private final ResponseBody delegate;
        private final ProgressListener listener;
        private BufferedSource bufferedSource;

        ProgressResponseBody(@NonNull ResponseBody delegate, @NonNull ProgressListener listener) {
            this.delegate = delegate;
            this.listener = listener;
        }

        @Override
        public long contentLength() {
            return delegate.contentLength();
        }

        @Override
        public okhttp3.MediaType contentType() {
            return delegate.contentType();
        }

        @Override
        public BufferedSource source() {
            if (bufferedSource == null) {
                bufferedSource = Okio.buffer(wrapSource(delegate.source()));
            }
            return bufferedSource;
        }

        private Source wrapSource(Source source) {
            return new ForwardingSource(source) {
                long totalBytesRead = 0L;

                @Override
                public long read(@NonNull Buffer sink, long byteCount) throws IOException {
                    long bytesRead = super.read(sink, byteCount);
                    if (bytesRead > 0) {
                        totalBytesRead += bytesRead;
                    }
                    listener.update(totalBytesRead, contentLength(), bytesRead == -1);
                    return bytesRead;
                }
            };
        }
    }

    /**
     * Read a hosts source file and append it to the database.
     *
     * @param hostsSource The hosts source to copy.
     * @throws IOException If the hosts source could not be copied.
     */
    private void readSourceFile(HostsSource hostsSource) throws IOException {
        // Get hosts file URI
        String hostsFileUrl = hostsSource.getUrl();
        Uri fileUri = Uri.parse(hostsFileUrl);
        Timber.v("Reading hosts source file: %s.", hostsFileUrl);
        // Set state to copying hosts source
        setState(R.string.status_read_source, hostsFileUrl);
        try (InputStream inputStream = this.context.getContentResolver().openInputStream(fileUri);
             InputStreamReader reader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            parseSourceInputStream(hostsSource, bufferedReader);
        } catch (IOException e) {
            throw new IOException("Error while reading hosts file from " + hostsFileUrl + ".", e);
        }
    }

    /**
     * Parse a source from its input stream to store it into database.
     *
     * @param hostsSource The host source to parse.
     * @param reader      The host source reader.
     */
    private void parseSourceInputStream(HostsSource hostsSource, BufferedReader reader) {
        parseSourceInputStream(hostsSource, reader, null);
    }

    /**
     * Parse a source from its input stream to store it into database with global deduplication.
     *
     * @param hostsSource      The host source to parse.
     * @param reader           The host source reader.
     * @param globalSeenHosts  Set for global deduplication across sources (null to disable).
     */
    private void parseSourceInputStream(HostsSource hostsSource, BufferedReader reader,
                                        @Nullable java.util.Set<String> globalSeenHosts) {
        setState(R.string.status_parse_source, hostsSource.getLabel());
        long startTime = System.currentTimeMillis();
        // Pass callback to update live blocked count in UI
        new SourceLoader(hostsSource).parse(reader, this.hostListItemDao, count -> {
            progressBuilder.addParsedHosts(count);
            MultiPhaseProgress mp = progressBuilder.build();
            postMultiPhaseProgress(mp);
        }, globalSeenHosts);
        long endTime = System.currentTimeMillis();
        Timber.i("Parsed " + hostsSource.getUrl() + " in " + (endTime - startTime) / 1000 + "s");
    }

    /**
     * Enable all hosts sources.
     *
     * @return {@code true} if at least one source was updated, {@code false} otherwise.
     */
    public boolean enableAllSources() {
        boolean updated = false;
        for (HostsSource source : this.hostsSourceDao.getAll()) {
            if (!source.isEnabled()) {
                this.hostsSourceDao.toggleEnabled(source);
                updated = true;
            }
        }
        return updated;
    }

    private void setState(@StringRes int stateResId, Object... details) {
        String state = this.context.getString(stateResId, details);
        Timber.d("Source model state: %s.", state);
        this.state.postValue(state);
    }

    private void postProgress(int done, int total, @Nullable String currentLabel, int basisPoints, int currentSourcePercent) {
        // Legacy single-bar progress - still used for backward compatibility
        this.progress.postValue(new Progress(done, total, currentLabel, basisPoints, currentSourcePercent));
    }

    private void postMultiPhaseProgress(MultiPhaseProgress mp) {
        this.progress.postValue(mp.toLegacyProgress());
        this.multiPhaseProgress.postValue(mp);
    }

    /**
     * Simple progress value object for UI (legacy single-bar).
     */
    public static final class Progress {
        public final int done;
        public final int total;
        @Nullable
        public final String currentLabel;
        public final int basisPoints;
        public final int currentSourcePercent;

        public Progress(int done, int total, @Nullable String currentLabel, int basisPoints, int currentSourcePercent) {
            this.done = Math.max(0, done);
            this.total = Math.max(0, total);
            this.currentLabel = currentLabel;
            this.basisPoints = Math.max(0, Math.min(10000, basisPoints));
            this.currentSourcePercent = Math.max(0, Math.min(100, currentSourcePercent));
        }

        public static Progress idle() {
            return new Progress(0, 0, null, 0, 0);
        }

        public boolean isActive() {
            return total > 0 && basisPoints < 10000;
        }
    }

    /**
     * Multi-phase progress for detailed UI with separate bars for Check/Download/Parse.
     */
    public static final class MultiPhaseProgress {
        // Check phase
        public final int checkedCount;
        public final int totalToCheck;

        // Download phase
        public final int downloadedCount;
        public final int totalToDownload;  // Only sources needing update
        public final int downloadQueued;   // Waiting to download

        // Parse phase
        public final int parsedCount;
        public final int totalToParse;
        public final int parseQueued;      // Waiting to parse
        public final long parsedHostCount; // Live count of blocked hosts parsed

        // Scheduler info
        @Nullable
        public final String schedulerTaskName;
        public final boolean isPaused;
        public final boolean isStopped;

        // Current activity
        @Nullable
        public final String currentLabel;

        // Monotonic percentages (never decrease)
        public final int monotonicCheckPercent;
        public final int monotonicDownloadPercent;
        public final int monotonicParsePercent;
        public final int monotonicOverallPercent;

        public MultiPhaseProgress(
                int checkedCount, int totalToCheck,
                int downloadedCount, int totalToDownload, int downloadQueued,
                int parsedCount, int totalToParse, int parseQueued,
                long parsedHostCount,
                @Nullable String schedulerTaskName, boolean isPaused, boolean isStopped,
                @Nullable String currentLabel,
                int monotonicCheckPercent, int monotonicDownloadPercent,
                int monotonicParsePercent, int monotonicOverallPercent) {
            this.checkedCount = checkedCount;
            this.totalToCheck = totalToCheck;
            this.downloadedCount = downloadedCount;
            this.totalToDownload = totalToDownload;
            this.downloadQueued = downloadQueued;
            this.parsedCount = parsedCount;
            this.totalToParse = totalToParse;
            this.parseQueued = parseQueued;
            this.parsedHostCount = parsedHostCount;
            this.schedulerTaskName = schedulerTaskName;
            this.isPaused = isPaused;
            this.isStopped = isStopped;
            this.currentLabel = currentLabel;
            this.monotonicCheckPercent = monotonicCheckPercent;
            this.monotonicDownloadPercent = monotonicDownloadPercent;
            this.monotonicParsePercent = monotonicParsePercent;
            this.monotonicOverallPercent = monotonicOverallPercent;
        }

        public static MultiPhaseProgress idle() {
            return new MultiPhaseProgress(0, 0, 0, 0, 0, 0, 0, 0, 0, null, false, false, null, 0, 0, 0, 0);
        }

        public boolean isActive() {
            // Active if any phase has work remaining (using >= for safety since counts can exceed totals)
            return totalToCheck > 0 && (getCheckPercent() < 100 || getDownloadPercent() < 100 || getParsePercent() < 100);
        }

        public int getCheckPercent() {
            return monotonicCheckPercent;
        }

        public double getCheckPercentDouble() {
            return monotonicCheckPercent;
        }

        public int getDownloadPercent() {
            return monotonicDownloadPercent;
        }

        public double getDownloadPercentDouble() {
            return monotonicDownloadPercent;
        }

        public int getParsePercent() {
            return monotonicParsePercent;
        }

        public double getParsePercentDouble() {
            return monotonicParsePercent;
        }

        public int getOverallPercent() {
            return monotonicOverallPercent;
        }

        public double getOverallPercentDouble() {
            return monotonicOverallPercent;
        }

        /** Convert to legacy Progress for backward compatibility. */
        public Progress toLegacyProgress() {
            int total = Math.max(1, totalToCheck);
            int done = checkedCount;
            int basisPoints = total > 0 ? (done * 10000 / total) : 0;
            return new Progress(done, total, currentLabel, basisPoints, getDownloadPercent());
        }
    }

    /**
     * Builder for updating MultiPhaseProgress atomically.
     */
    public static final class MultiPhaseProgressBuilder {
        private final AtomicInteger checkedCount = new AtomicInteger(0);
        private final AtomicInteger totalToCheck = new AtomicInteger(0);
        private final AtomicInteger downloadedCount = new AtomicInteger(0);
        private final AtomicInteger totalToDownload = new AtomicInteger(0);
        private final AtomicInteger downloadQueued = new AtomicInteger(0);
        private final AtomicInteger parsedCount = new AtomicInteger(0);
        private final AtomicInteger totalToParse = new AtomicInteger(0);
        private final AtomicInteger parseQueued = new AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicLong parsedHostCount = new java.util.concurrent.atomic.AtomicLong(0);
        // High water marks - percentages NEVER decrease
        private final AtomicInteger maxCheckPercent = new AtomicInteger(0);
        private final AtomicInteger maxDownloadPercent = new AtomicInteger(0);
        private final AtomicInteger maxParsePercent = new AtomicInteger(0);
        private final AtomicInteger maxOverallPercent = new AtomicInteger(0);
        private volatile String schedulerTaskName;
        private volatile boolean isPaused;
        private volatile boolean isStopped;
        private volatile String currentLabel;

        public void setTotalToCheck(int total) { totalToCheck.set(total); }
        public int incrementChecked() { return checkedCount.incrementAndGet(); }
        public void incrementTotalToDownload() { totalToDownload.incrementAndGet(); downloadQueued.incrementAndGet(); }
        public int incrementDownloaded() { downloadQueued.decrementAndGet(); return downloadedCount.incrementAndGet(); }
        public void incrementTotalToParse() { totalToParse.incrementAndGet(); parseQueued.incrementAndGet(); }
        public int incrementParsed() { parseQueued.decrementAndGet(); return parsedCount.incrementAndGet(); }
        public void addParsedHosts(long count) { parsedHostCount.addAndGet(count); }
        public long getParsedHostCount() { return parsedHostCount.get(); }
        public void setSchedulerTaskName(String name) { schedulerTaskName = name; }
        public void setPaused(boolean paused) { isPaused = paused; }
        public void setStopped(boolean stopped) { isStopped = stopped; }
        public void setCurrentLabel(String label) { currentLabel = label; }
        public boolean isPaused() { return isPaused; }
        public boolean isStopped() { return isStopped; }

        public MultiPhaseProgress build() {
            // Calculate current percentages
            int ttc = totalToCheck.get();
            int ttd = totalToDownload.get();
            int ttp = totalToParse.get();
            int curCheck = ttc > 0 ? Math.min(100, checkedCount.get() * 100 / ttc) : 0;
            int curDownload = ttd > 0 ? Math.min(100, downloadedCount.get() * 100 / ttd) : 0;
            int curParse = ttp > 0 ? Math.min(100, parsedCount.get() * 100 / ttp) : 0;
            int curOverall = (int) Math.min(100, curCheck * 0.30 + curDownload * 0.40 + curParse * 0.30);

            // Update high water marks (monotonic - only increase)
            maxCheckPercent.updateAndGet(prev -> Math.max(prev, curCheck));
            maxDownloadPercent.updateAndGet(prev -> Math.max(prev, curDownload));
            maxParsePercent.updateAndGet(prev -> Math.max(prev, curParse));
            maxOverallPercent.updateAndGet(prev -> Math.max(prev, curOverall));

            return new MultiPhaseProgress(
                    checkedCount.get(), totalToCheck.get(),
                    downloadedCount.get(), totalToDownload.get(), downloadQueued.get(),
                    parsedCount.get(), totalToParse.get(), parseQueued.get(),
                    parsedHostCount.get(),
                    schedulerTaskName, isPaused, isStopped, currentLabel,
                    maxCheckPercent.get(), maxDownloadPercent.get(), maxParsePercent.get(), maxOverallPercent.get()
            );
        }

        public void reset() {
            checkedCount.set(0);
            totalToCheck.set(0);
            downloadedCount.set(0);
            totalToDownload.set(0);
            downloadQueued.set(0);
            parsedCount.set(0);
            totalToParse.set(0);
            parseQueued.set(0);
            parsedHostCount.set(0);
            maxCheckPercent.set(0);
            maxDownloadPercent.set(0);
            maxParsePercent.set(0);
            maxOverallPercent.set(0);
            schedulerTaskName = null;
            isPaused = false;
            isStopped = false;
            currentLabel = null;
        }
    }
}
