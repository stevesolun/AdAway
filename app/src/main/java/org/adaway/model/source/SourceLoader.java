package org.adaway.model.source;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.util.Constants.BOGUS_IPV4;
import static org.adaway.util.Constants.LOCALHOST_HOSTNAME;
import static org.adaway.util.Constants.LOCALHOST_IPV4;
import static org.adaway.util.Constants.LOCALHOST_IPV6;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.util.RegexUtils;

import java.io.BufferedReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.os.SystemClock;

import timber.log.Timber;

/**
 * This class is an {@link HostsSource} loader.<br>
 * It parses a source and loads it to database.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class SourceLoader {
    private static final String TAG = "SourceLoader";
    private static final String END_OF_QUEUE_MARKER = "#EndOfQueueMarker";
    // Batch size tradeoff:
    // - Smaller => smoother UI updates but slower DB throughput
    // - Larger  => much faster parsing/DB insert for large filter sets
    //
    // 2000 is tuned for throughput on large filter sets. This increases per-batch latency, but can
    // significantly reduce overall parse time when SQLite/Room is the bottleneck.
    private static final int INSERT_BATCH_SIZE = 2000;
    // Queue capacity - balances throughput vs memory usage.
    // 20,000 allows reader to stay ahead of parsers without blocking.
    private static final int QUEUE_CAPACITY = 20000;
    // Parser thread count - adaptive based on hardware (from SourceModel)
    private static final int PARSER_COUNT = SourceModel.PARSER_THREADS_PER_SOURCE;
    private static final String HOSTS_PARSER = "^\\s*([^#\\s]+)\\s+([^#\\s]+).*$";
    static final Pattern HOSTS_PARSER_PATTERN = Pattern.compile(HOSTS_PARSER);
    private static final Pattern ADBLOCK_DOUBLE_PIPE = Pattern.compile("^\\|\\|([^\\^/$]+).*$");
    private static final Pattern URL_HOST = Pattern.compile("^\\|?https?://([^/\\^$]+).*$");
    private static final Pattern DNSMASQ_ADDRESS = Pattern.compile("^address=/([^/]+)/.*$");

    private final HostsSource source;
    private final int generation;

    SourceLoader(HostsSource hostsSource) {
        this.source = hostsSource;
        this.generation = 0;
    }

    SourceLoader(HostsSource hostsSource, int generation) {
        this.source = hostsSource;
        this.generation = generation;
    }

    void parse(BufferedReader reader, HostListItemDao hostListItemDao) {
        parse(reader, hostListItemDao, null, null, null, Integer.MAX_VALUE, null);
    }

    /**
     * Parse hosts source and insert into database.
     * @param reader The source reader.
     * @param hostListItemDao The DAO for inserting items.
     * @param onBatchInserted Callback called after each batch insert with the count of inserted items.
     */
    void parse(BufferedReader reader, HostListItemDao hostListItemDao, @Nullable LongConsumer onBatchInserted) {
        parse(reader, hostListItemDao, null, onBatchInserted, null, Integer.MAX_VALUE, null);
    }

    /**
     * Parse hosts source and insert into database with global deduplication.
     * @param reader The source reader.
     * @param hostListItemDao The DAO for inserting items.
     * @param onBatchInserted Callback called after each batch insert with the count of inserted items.
     * @param globalSeenHosts Set of globally seen hosts for deduplication across sources. Can be null.
     */
    void parse(BufferedReader reader, HostListItemDao hostListItemDao, @Nullable LongConsumer onBatchInserted,
               @Nullable java.util.Set<String> globalSeenHosts) {
        parse(reader, hostListItemDao, null, onBatchInserted, globalSeenHosts, Integer.MAX_VALUE, null);
    }

    /**
     * Parse hosts source and insert into database with global deduplication and memory cap.
     * @param reader The source reader.
     * @param hostListItemDao The DAO for inserting items.
     * @param onBatchInserted Callback called after each batch insert with the count of inserted items.
     * @param globalSeenHosts Set of globally seen hosts for deduplication across sources. Can be null.
     * @param maxDedupEntries Maximum entries in dedup set before disabling dedup to prevent OOM.
     * @param dedupCapReached Flag set to true when dedup cap is reached (may be null).
     */
    void parse(BufferedReader reader, HostListItemDao hostListItemDao, @Nullable LongConsumer onBatchInserted,
               @Nullable java.util.Set<String> globalSeenHosts, int maxDedupEntries,
               @Nullable AtomicBoolean dedupCapReached) {
        parse(reader, hostListItemDao, null, onBatchInserted, globalSeenHosts, maxDedupEntries, dedupCapReached);
    }

    /**
     * Parse hosts source and insert into database, optionally using raw SQLite for high-throughput bulk inserts.
     * When {@code db} is provided, inserts are performed using a compiled statement + explicit transactions,
     * which is significantly faster than Room @Insert for large batches.
     */
    void parse(BufferedReader reader,
               HostListItemDao hostListItemDao,
               @Nullable SupportSQLiteDatabase db,
               @Nullable LongConsumer onBatchInserted,
               @Nullable java.util.Set<String> globalSeenHosts,
               int maxDedupEntries,
               @Nullable AtomicBoolean dedupCapReached) {
        // Clear any previous partial import for THIS generation only (atomic updates keep old generations intact).
        hostListItemDao.clearSourceHostsForGeneration(this.source.getId(), this.generation);
        // Create queues with bounded capacity to prevent OOM during parallel parsing
        LinkedBlockingQueue<String> hostsLineQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        LinkedBlockingQueue<HostListItem> hostsListItemQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        SourceReader sourceReader = new SourceReader(reader, hostsLineQueue, PARSER_COUNT);
        ItemInserter inserter = new ItemInserter(hostsListItemQueue, hostListItemDao, db, this.generation, PARSER_COUNT, onBatchInserted);

        // Shared counter for malformed lines across all parser threads
        AtomicInteger skippedLines = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(
                PARSER_COUNT + 2,
                r -> {
                    Thread t = new Thread(r, TAG);
                    // Parser threads are CPU-intensive - slightly lower priority to avoid starving I/O
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
        );
        executorService.execute(sourceReader);
        for (int i = 0; i < PARSER_COUNT; i++) {
            executorService.execute(new HostListItemParser(this.source, hostsLineQueue, hostsListItemQueue,
                    globalSeenHosts, maxDedupEntries, dedupCapReached, skippedLines));
        }
        Future<Integer> inserterFuture = executorService.submit(inserter);
        try {
            Integer inserted = inserterFuture.get();
            int skipped = skippedLines.get();
            if (skipped > 0) {
                Timber.w("Source %s: %d lines skipped (malformed or unrecognized format)", this.source.getLabel(), skipped);
            }
            Timber.i("%s: %d host list items inserted, %d lines skipped.", this.source.getLabel(), inserted, skipped);
        } catch (ExecutionException e) {
            Timber.w(e, "Failed to parse hosts sources.");
        } catch (InterruptedException e) {
            Timber.w(e, "Interrupted while parsing sources.");
            Thread.currentThread().interrupt();
        }
        executorService.shutdown();
    }

    private static class SourceReader implements Runnable {
        private final BufferedReader reader;
        private final BlockingQueue<String> queue;
        private final int parserCount;

        private SourceReader(BufferedReader reader, BlockingQueue<String> queue, int parserCount) {
            this.reader = reader;
            this.queue = queue;
            this.parserCount = parserCount;
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = this.reader.readLine()) != null) {
                    this.queue.put(line);  // put() blocks if queue is full, preventing OOM
                }
            } catch (Throwable t) {
                Timber.w(t, "Failed to read hosts source.");
            } finally {
                // Send end of queue marker to parsers
                for (int i = 0; i < this.parserCount; i++) {
                    try {
                        this.queue.put(END_OF_QUEUE_MARKER);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private static class HostListItemParser implements Runnable {
        private final HostsSource source;
        private final BlockingQueue<String> lineQueue;
        private final BlockingQueue<HostListItem> itemQueue;
        @Nullable
        private final java.util.Set<String> globalSeenHosts;
        private final int maxDedupEntries;
        @Nullable
        private final AtomicBoolean dedupCapReached;
        private final AtomicInteger skippedLines;

        private HostListItemParser(HostsSource source, BlockingQueue<String> lineQueue,
                                   BlockingQueue<HostListItem> itemQueue,
                                   @Nullable java.util.Set<String> globalSeenHosts,
                                   int maxDedupEntries,
                                   @Nullable AtomicBoolean dedupCapReached,
                                   AtomicInteger skippedLines) {
            this.source = source;
            this.lineQueue = lineQueue;
            this.itemQueue = itemQueue;
            this.globalSeenHosts = globalSeenHosts;
            this.maxDedupEntries = maxDedupEntries;
            this.dedupCapReached = dedupCapReached;
            this.skippedLines = skippedLines;
        }

        @Override
        public void run() {
            boolean allowedList = this.source.isAllowEnabled();
            boolean endOfSource = false;
            while (!endOfSource) {
                try {
                    String line = this.lineQueue.take();
                    // Check end of queue marker
                    //noinspection StringEquality
                    if (line == END_OF_QUEUE_MARKER) {
                        endOfSource = true;
                        // Send end of queue marker to inserter
                        HostListItem endItem = new HostListItem();
                        endItem.setHost(line);
                        this.itemQueue.put(endItem);  // put() blocks if full, add() throws!
                    } // Skip comments and empty lines (no logging - too slow for millions of lines)
                    else if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '!') {
                        // skip
                    } else {
                        HostListItem item = allowedList ? parseAllowListItem(line) : parseHostListItem(line);
                        if (item == null || !isRedirectionValid(item) || !isHostValid(item)) {
                            // Track failed parse attempts
                            this.skippedLines.incrementAndGet();
                        } else {
                            // Memory-safe deduplication with cap
                            if (globalSeenHosts == null) {
                                this.itemQueue.put(item);
                            } else if (dedupCapReached != null && dedupCapReached.get()) {
                                // Cap reached - allow through without dedup (correctness over memory)
                                this.itemQueue.put(item);
                            } else if (globalSeenHosts.size() >= maxDedupEntries) {
                                // Just hit the cap
                                if (dedupCapReached != null) dedupCapReached.set(true);
                                this.itemQueue.put(item);
                            } else if (globalSeenHosts.add(item.getHost())) {
                                this.itemQueue.put(item);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Timber.w(e, "Interrupted while parsing hosts list item.");
                    endOfSource = true;
                    Thread.currentThread().interrupt();
                }
            }
        }

        private HostListItem parseHostListItem(String line) {
            Matcher matcher = HOSTS_PARSER_PATTERN.matcher(line);
            if (!matcher.matches()) {
                // Best-effort: accept common "filter syntax" formats by extracting a hostname.
                String extracted = extractHostnameFromNonHostsSyntax(line);
                if (extracted == null) {
                    return null;  // Skip non-matching lines silently for performance
                }
                // Treat extracted domain as blocked (0.0.0.0 domain)
                HostListItem item = new HostListItem();
                item.setType(BLOCKED);
                item.setHost(extracted);
                item.setEnabled(true);
                item.setSourceId(this.source.getId());
                return item;
            }
            // Check IP address validity or while list entry (if allowed)
            String ip = matcher.group(1);
            String hostname = matcher.group(2);
            assert hostname != null;
            // Skip localhost name
            if (LOCALHOST_HOSTNAME.equals(hostname)) {
                return null;
            }
            // check if ip is 127.0.0.1 or 0.0.0.0
            ListType type;
            if (LOCALHOST_IPV4.equals(ip)
                    || BOGUS_IPV4.equals(ip)
                    || LOCALHOST_IPV6.equals(ip)) {
                type = BLOCKED;
            } else if (this.source.isRedirectEnabled()) {
                type = REDIRECTED;
            } else {
                return null;
            }
            HostListItem item = new HostListItem();
            item.setType(type);
            item.setHost(hostname);
            item.setEnabled(true);
            if (type == REDIRECTED) {
                item.setRedirection(ip);
            }
            item.setSourceId(this.source.getId());
            return item;
        }

        /**
         * Extract a hostname from non-hosts syntaxes (ABP/uBO/AdGuard style lists, domain lists, dnsmasq, etc.).
         * This intentionally ignores rule types that are not representable as a hosts entry.
         */
        @Nullable
        private static String extractHostnameFromNonHostsSyntax(String rawLine) {
            if (rawLine == null) return null;
            String line = rawLine.trim();
            if (line.isEmpty()) return null;

            // Drop inline comments for common syntaxes
            int hash = line.indexOf('#');
            if (hash > 0) {
                line = line.substring(0, hash).trim();
            }
            if (line.isEmpty()) return null;

            // Skip cosmetic/scriptlet rules and section headers
            if (line.startsWith("[") || line.contains("##") || line.contains("#@#") || line.contains("#$#") || line.contains("#?#")) {
                return null;
            }

            // Skip exceptions
            if (line.startsWith("@@")) {
                return null;
            }

            // dnsmasq: address=/example.com/0.0.0.0
            Matcher dnsmasq = DNSMASQ_ADDRESS.matcher(line);
            if (dnsmasq.matches()) {
                return sanitizeHostname(dnsmasq.group(1));
            }

            // Plain domain line: example.com
            String plain = sanitizeHostname(line);
            if (plain != null) {
                return plain;
            }

            // uBO/ABP style: ||example.com^$third-party
            Matcher dbl = ADBLOCK_DOUBLE_PIPE.matcher(line);
            if (dbl.matches()) {
                return sanitizeHostname(dbl.group(1));
            }

            // URL style: |https://example.com/path
            Matcher url = URL_HOST.matcher(line);
            if (url.matches()) {
                return sanitizeHostname(url.group(1));
            }

            return null;
        }

        @Nullable
        private static String sanitizeHostname(@Nullable String raw) {
            if (raw == null) return null;
            String h = raw.trim();
            if (h.isEmpty()) return null;

            // Remove leading separators used in some syntaxes
            while (!h.isEmpty() && (h.charAt(0) == '.' || h.charAt(0) == '|')) {
                h = h.substring(1);
            }

            // Truncate at common ABP/uBO delimiters
            int cut = h.length();
            int caret = h.indexOf('^');
            if (caret >= 0) cut = Math.min(cut, caret);
            int dollar = h.indexOf('$');
            if (dollar >= 0) cut = Math.min(cut, dollar);
            int slash = h.indexOf('/');
            if (slash >= 0) cut = Math.min(cut, slash);
            if (cut != h.length()) {
                h = h.substring(0, cut);
            }

            h = h.trim();
            if (h.isEmpty()) return null;

            // Reject patterns/wildcards/regex-like tokens
            if (h.indexOf('*') != -1 || h.indexOf('?') != -1 || h.indexOf('%') != -1) return null;
            if (h.indexOf(' ') != -1 || h.indexOf('\t') != -1) return null;

            // Reject IP addresses here (we only accept hostnames via this path)
            if (RegexUtils.isValidIP(h)) return null;

            // Basic hostname validation
            return RegexUtils.isValidHostname(h) ? h : null;
        }

        private HostListItem parseAllowListItem(String line) {
            // Extract hostname
            int indexOf = line.indexOf('#');
            if (indexOf == 1) {
                line = line.substring(0, indexOf);
            }
            line = line.trim();
            // Create item
            HostListItem item = new HostListItem();
            item.setType(ALLOWED);
            item.setHost(line);
            item.setEnabled(true);
            item.setSourceId(this.source.getId());
            return item;
        }

        private boolean isRedirectionValid(HostListItem item) {
            return item.getType() != REDIRECTED || RegexUtils.isValidIP(item.getRedirection());
        }

        private boolean isHostValid(HostListItem item) {
            String hostname = item.getHost();
            if (item.getType() == BLOCKED) {
                if (hostname.indexOf('?') != -1 || hostname.indexOf('*') != -1) {
                    return false;
                }
                return RegexUtils.isValidHostname(hostname);
            }
            return RegexUtils.isValidWildcardHostname(hostname);
        }
    }

    private static class ItemInserter implements Callable<Integer> {
        private final BlockingQueue<HostListItem> hostListItemQueue;
        private final HostListItemDao hostListItemDao;
        @Nullable
        private final SupportSQLiteDatabase db;
        private final int generation;
        private final int parserCount;
        @Nullable
        private final LongConsumer onBatchInserted;

        private ItemInserter(BlockingQueue<HostListItem> itemQueue,
                             HostListItemDao hostListItemDao,
                             @Nullable SupportSQLiteDatabase db,
                             int generation,
                             int parserCount, @Nullable LongConsumer onBatchInserted) {
            this.hostListItemQueue = itemQueue;
            this.hostListItemDao = hostListItemDao;
            this.db = db;
            this.generation = generation;
            this.parserCount = parserCount;
            this.onBatchInserted = onBatchInserted;
        }

        @Override
        public Integer call() {
            int inserted = 0;
            int workerStopped = 0;
            HostListItem[] batch = new HostListItem[INSERT_BATCH_SIZE];
            int cacheSize = 0;
            boolean queueEmptied = false;

            // Lightweight DB perf logging: rows/sec + batch insert latency.
            // Rate-limited to once every ~2s to avoid log spam.
            final boolean perfLog = true;
            final long startMs = SystemClock.elapsedRealtime();
            long lastLogMs = startMs;
            int insertedSinceLastLog = 0;
            long totalDbInsertMs = 0L;

            // Optional fast path: compiled statement for bulk insert.
            final SupportSQLiteStatement insertStmt;
            if (db != null) {
                insertStmt = db.compileStatement(
                        "INSERT INTO hosts_lists (host, type, enabled, redirection, source_id, generation) VALUES (?, ?, ?, ?, ?, ?)"
                );
            } else {
                insertStmt = null;
            }

            while (!queueEmptied) {
                try {
                    HostListItem item = this.hostListItemQueue.take();
                    // Check end of queue marker
                    //noinspection StringEquality
                    if (item.getHost() == END_OF_QUEUE_MARKER) {
                        workerStopped++;
                        if (workerStopped >= this.parserCount) {
                            queueEmptied = true;
                        }
                    } else {
                        batch[cacheSize++] = item;
                        if (cacheSize >= batch.length) {
                            long t0 = SystemClock.elapsedRealtimeNanos();
                            if (db != null && insertStmt != null) {
                                long dbMs = bulkInsert(db, insertStmt, batch, cacheSize, this.generation);
                                long batchDbMs = dbMs;
                                inserted += cacheSize;
                                if (perfLog) {
                                    totalDbInsertMs += batchDbMs;
                                    insertedSinceLastLog += cacheSize;
                                    long nowMs = SystemClock.elapsedRealtime();
                                    if (nowMs - lastLogMs >= 2000L) {
                                        long windowMs = Math.max(1L, nowMs - lastLogMs);
                                        double rowsPerSec = (insertedSinceLastLog * 1000.0) / windowMs;
                                        double batchRowsPerSec = batchDbMs > 0 ? (cacheSize * 1000.0) / batchDbMs : 0.0;
                                        Timber.i("DB insert perf: win=%.0f rows/s, batch=%.0f rows/s (batchSize=%d, lastBatchMs=%d), totalInserted=%d, elapsed=%ds",
                                                rowsPerSec, batchRowsPerSec, cacheSize, batchDbMs, inserted, (nowMs - startMs) / 1000);
                                        insertedSinceLastLog = 0;
                                        lastLogMs = nowMs;
                                    }
                                }
                                if (this.onBatchInserted != null) {
                                    this.onBatchInserted.accept(cacheSize);
                                }
                                cacheSize = 0;
                                continue;
                            } else {
                                // Ensure generation is set for Room insert
                                for (int i = 0; i < cacheSize; i++) {
                                    if (batch[i] != null) batch[i].setGeneration(this.generation);
                                }
                                this.hostListItemDao.insert(batch);
                            }
                            long batchDbMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000L;
                            inserted += cacheSize;
                            if (perfLog) {
                                totalDbInsertMs += batchDbMs;
                                insertedSinceLastLog += cacheSize;
                                long nowMs = SystemClock.elapsedRealtime();
                                if (nowMs - lastLogMs >= 2000L) {
                                    long windowMs = Math.max(1L, nowMs - lastLogMs);
                                    double rowsPerSec = (insertedSinceLastLog * 1000.0) / windowMs;
                                    double batchRowsPerSec = batchDbMs > 0 ? (cacheSize * 1000.0) / batchDbMs : 0.0;
                                    Timber.i("DB insert perf: win=%.0f rows/s, batch=%.0f rows/s (batchSize=%d, lastBatchMs=%d), totalInserted=%d, elapsed=%ds",
                                            rowsPerSec, batchRowsPerSec, cacheSize, batchDbMs, inserted, (nowMs - startMs) / 1000);
                                    insertedSinceLastLog = 0;
                                    lastLogMs = nowMs;
                                }
                            }
                            // Notify callback of batch insert for live UI updates
                            if (this.onBatchInserted != null) {
                                this.onBatchInserted.accept(cacheSize);
                            }
                            cacheSize = 0;
                        }
                    }
                } catch (InterruptedException e) {
                    Timber.w(e, "Interrupted while inserted hosts list item.");
                    queueEmptied = true;
                    Thread.currentThread().interrupt();
                }
            }
            // Flush current batch
            HostListItem[] remaining = new HostListItem[cacheSize];
            System.arraycopy(batch, 0, remaining, 0, remaining.length);
            if (cacheSize > 0) {
                long t0 = SystemClock.elapsedRealtimeNanos();
                if (db != null && insertStmt != null) {
                    long batchDbMs = bulkInsert(db, insertStmt, remaining, cacheSize, this.generation);
                    totalDbInsertMs += batchDbMs;
                } else {
                    for (int i = 0; i < cacheSize; i++) {
                        if (remaining[i] != null) remaining[i].setGeneration(this.generation);
                    }
                    this.hostListItemDao.insert(remaining);
                    long batchDbMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000L;
                    totalDbInsertMs += batchDbMs;
                }
            }
            inserted += cacheSize;
            // Notify callback of final batch
            if (cacheSize > 0 && this.onBatchInserted != null) {
                this.onBatchInserted.accept(cacheSize);
            }
            if (perfLog) {
                long nowMs = SystemClock.elapsedRealtime();
                long elapsedMs = Math.max(1L, nowMs - startMs);
                double rowsPerSec = (inserted * 1000.0) / elapsedMs;
                double dbOnlyRowsPerSec = totalDbInsertMs > 0 ? (inserted * 1000.0) / totalDbInsertMs : 0.0;
                Timber.i("DB insert perf done: overall=%.0f rows/s, dbOnly=%.0f rows/s (totalInserted=%d, dbInsertMs=%d, elapsed=%ds)",
                        rowsPerSec, dbOnlyRowsPerSec, inserted, totalDbInsertMs, elapsedMs / 1000);
            }
            // Return number of inserted items
            return inserted;
        }

        /**
         * Bulk insert using a compiled statement + a single transaction.
         * Returns time spent inside DB transaction (ms).
         */
        private static long bulkInsert(@NonNull SupportSQLiteDatabase db,
                                       @NonNull SupportSQLiteStatement stmt,
                                       @NonNull HostListItem[] items,
                                       int count,
                                       int generation) {
            long t0 = SystemClock.elapsedRealtimeNanos();
            db.beginTransaction();
            try {
                for (int i = 0; i < count; i++) {
                    HostListItem it = items[i];
                    if (it == null) continue;
                    stmt.clearBindings();
                    stmt.bindString(1, it.getHost());
                    ListType type = it.getType();
                    stmt.bindLong(2, type != null ? type.getValue() : 0);
                    stmt.bindLong(3, it.isEnabled() ? 1L : 0L);
                    String redirection = it.getRedirection();
                    if (redirection != null) {
                        stmt.bindString(4, redirection);
                    } else {
                        stmt.bindNull(4);
                    }
                    stmt.bindLong(5, it.getSourceId());
                    stmt.bindLong(6, generation);
                    stmt.executeInsert();
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000L;
        }
    }
}
