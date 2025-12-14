package org.adaway.model.source;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.util.Constants.BOGUS_IPV4;
import static org.adaway.util.Constants.LOCALHOST_HOSTNAME;
import static org.adaway.util.Constants.LOCALHOST_IPV4;
import static org.adaway.util.Constants.LOCALHOST_IPV6;

import androidx.annotation.Nullable;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
    private static final int INSERT_BATCH_SIZE = 100;
    private static final String HOSTS_PARSER = "^\\s*([^#\\s]+)\\s+([^#\\s]+).*$";
    static final Pattern HOSTS_PARSER_PATTERN = Pattern.compile(HOSTS_PARSER);
    private static final Pattern ADBLOCK_DOUBLE_PIPE = Pattern.compile("^\\|\\|([^\\^/$]+).*$");
    private static final Pattern URL_HOST = Pattern.compile("^\\|?https?://([^/\\^$]+).*$");
    private static final Pattern DNSMASQ_ADDRESS = Pattern.compile("^address=/([^/]+)/.*$");

    private final HostsSource source;

    SourceLoader(HostsSource hostsSource) {
        this.source = hostsSource;
    }

    void parse(BufferedReader reader, HostListItemDao hostListItemDao) {
        // Clear current hosts
        hostListItemDao.clearSourceHosts(this.source.getId());
        // Create batch
        int parserCount = 3;
        LinkedBlockingQueue<String> hostsLineQueue = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<HostListItem> hostsListItemQueue = new LinkedBlockingQueue<>();
        SourceReader sourceReader = new SourceReader(reader, hostsLineQueue, parserCount);
        ItemInserter inserter = new ItemInserter(hostsListItemQueue, hostListItemDao, parserCount);
        ExecutorService executorService = Executors.newFixedThreadPool(
                parserCount + 2,
                r -> new Thread(r, TAG)
        );
        executorService.execute(sourceReader);
        for (int i = 0; i < parserCount; i++) {
            executorService.execute(new HostListItemParser(this.source, hostsLineQueue, hostsListItemQueue));
        }
        Future<Integer> inserterFuture = executorService.submit(inserter);
        try {
            Integer inserted = inserterFuture.get();
            Timber.i("%s host list items inserted.", inserted);
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
                this.reader.lines().forEach(this.queue::add);
            } catch (Throwable t) {
                Timber.w(t, "Failed to read hosts source.");
            } finally {
                // Send end of queue marker to parsers
                for (int i = 0; i < this.parserCount; i++) {
                    this.queue.add(END_OF_QUEUE_MARKER);
                }
            }
        }
    }

    private static class HostListItemParser implements Runnable {
        private final HostsSource source;
        private final BlockingQueue<String> lineQueue;
        private final BlockingQueue<HostListItem> itemQueue;

        private HostListItemParser(HostsSource source, BlockingQueue<String> lineQueue, BlockingQueue<HostListItem> itemQueue) {
            this.source = source;
            this.lineQueue = lineQueue;
            this.itemQueue = itemQueue;
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
                        this.itemQueue.add(endItem);
                    } // Check comments
                    else if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '!') {
                        Timber.d("Skip comment: %s.", line);
                    } else {
                        HostListItem item = allowedList ? parseAllowListItem(line) : parseHostListItem(line);
                        if (item != null && isRedirectionValid(item) && isHostValid(item)) {
                            this.itemQueue.add(item);
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
                // Best-effort: accept common \"filter syntax\" formats by extracting a hostname.
                String extracted = extractHostnameFromNonHostsSyntax(line);
                if (extracted == null) {
                    Timber.d("Does not match: %s.", line);
                    return null;
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
        private final int parserCount;

        private ItemInserter(BlockingQueue<HostListItem> itemQueue, HostListItemDao hostListItemDao, int parserCount) {
            this.hostListItemQueue = itemQueue;
            this.hostListItemDao = hostListItemDao;
            this.parserCount = parserCount;
        }

        @Override
        public Integer call() {
            int inserted = 0;
            int workerStopped = 0;
            HostListItem[] batch = new HostListItem[INSERT_BATCH_SIZE];
            int cacheSize = 0;
            boolean queueEmptied = false;
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
                            this.hostListItemDao.insert(batch);
                            inserted += cacheSize;
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
            this.hostListItemDao.insert(remaining);
            inserted += cacheSize;
            // Return number of inserted items
            return inserted;
        }
    }
}
