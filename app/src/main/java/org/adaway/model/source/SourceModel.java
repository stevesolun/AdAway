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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

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
     * The HTTP client cache size (100Mo).
     */
    private static final long CACHE_SIZE = 100L * 1024L * 1024L;
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
        this.state = new MutableLiveData<>("");
        this.progress = new MutableLiveData<>(Progress.idle());
        this.updateAvailable = new MutableLiveData<>();
        this.updateAvailable.setValue(false);
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
        // Init progress for enabled sources only.
        int total = 0;
        for (HostsSource s : this.hostsSourceDao.getAll()) {
            if (s.isEnabled()) total++;
        }
        postProgress(0, total, null, total > 0 ? 0 : 0, 0);
        // Initialize copy counters
        int numberOfCopies = 0;
        int numberOfFailedCopies = 0;
        // Compute current date in UTC timezone
        ZonedDateTime now = ZonedDateTime.now();
        // Get each hosts source
        int done = 0;
        for (HostsSource source : this.hostsSourceDao.getAll()) {
            int sourceId = source.getId();
            // Clear disabled source
            if (!source.isEnabled()) {
                this.hostListItemDao.clearSourceHosts(sourceId);
                this.hostsSourceDao.clearProperties(sourceId);
                continue;
            }
            // Progress update at the start of each enabled source.
            postProgress(done, total, source.getLabel(), total > 0 ? (int) Math.floor(done * 10000.0 / total) : 0, 0);
            // Get hosts source last update
            ZonedDateTime onlineModificationDate = getHostsSourceLastUpdate(source);
            if (onlineModificationDate == null) {
                onlineModificationDate = now;
            }
            // Check if update available
            ZonedDateTime localModificationDate = source.getLocalModificationDate();
            if (localModificationDate != null && localModificationDate.isAfter(onlineModificationDate)) {
                Timber.i("Skip source %s: no update.", source.getLabel());
                // Still count this source as processed so progress % moves forward.
                done++;
                postProgress(done, total, source.getLabel(), total > 0 ? (int) Math.floor(done * 10000.0 / total) : 0, 0);
                continue;
            }
            // Increment number of copy
            numberOfCopies++;
            try {
                // Check hosts source type
                switch (source.getType()) {
                    case URL:
                        downloadHostSource(source);
                        break;
                    case FILE:
                        readSourceFile(source);
                        break;
                    default:
                        Timber.w("Hosts source type  is not supported.");
                }
                // Update local and online modification dates to now
                localModificationDate = onlineModificationDate.isAfter(now) ? onlineModificationDate : now;
                this.hostsSourceDao.updateModificationDates(sourceId, localModificationDate, onlineModificationDate);
                // Update size
                this.hostsSourceDao.updateSize(sourceId);
            } catch (IOException e) {
                Timber.w(e, "Failed to retrieve host source %s.", source.getUrl());
                // Increment number of failed copy
                numberOfFailedCopies++;
            }
            done++;
            postProgress(done, total, source.getLabel(), total > 0 ? (int) Math.floor(done * 10000.0 / total) : 0, 0);
        }
        // Check if all copies failed
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
        ZonedDateTime onlineModificationDate = getHostsSourceLastUpdate(source);
        if (onlineModificationDate == null) {
            onlineModificationDate = now;
        }

        // Download/read + parse
        try {
            switch (source.getType()) {
                case URL:
                    downloadHostSource(source);
                    break;
                case FILE:
                    readSourceFile(source);
                    break;
                default:
                    Timber.w("Hosts source type is not supported.");
                    return;
            }
            ZonedDateTime localModificationDate = onlineModificationDate.isAfter(now) ? onlineModificationDate : now;
            this.hostsSourceDao.updateModificationDates(sourceId, localModificationDate, onlineModificationDate);
            this.hostsSourceDao.updateSize(sourceId);
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
     *
     * @return The HTTP client to download hosts sources.
     */
    @NonNull
    private OkHttpClient getHttpClient() {
        if (this.cachedHttpClient == null) {
            this.cachedHttpClient = new OkHttpClient.Builder()
                    .cache(new Cache(this.context.getCacheDir(), CACHE_SIZE))
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
        setState(R.string.status_parse_source, hostsSource.getLabel());
        long startTime = System.currentTimeMillis();
        new SourceLoader(hostsSource).parse(reader, this.hostListItemDao);
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
        this.progress.postValue(new Progress(done, total, currentLabel, basisPoints, currentSourcePercent));
    }

    /**
     * Simple progress value object for UI.
     */
    public static final class Progress {
        public final int done;
        public final int total;
        @Nullable
        public final String currentLabel;
        /**
         * Overall progress in basis points (0..10000 = 0.00%..100.00%).
         * This can change even when done/total don't (e.g. while downloading current source).
         */
        public final int basisPoints;
        /**
         * Progress within the current source download (0..100), best-effort.
         */
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
}
