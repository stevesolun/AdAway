package org.adaway.model.vpn;

import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.model.error.HostError.ENABLE_VPN_FAIL;

import android.content.Context;
import android.util.LruCache;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.entity.HostEntry;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.vpn.VpnServiceControls;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import timber.log.Timber;

/**
 * This class is the model to represent VPN service configuration.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class VpnModel extends AdBlockModel {
    private static final int MAX_LOG_ENTRIES = 1000;
    private final HostEntryDao hostEntryDao;
    private volatile LruCache<String, HostEntry> blockCache;
    private final LinkedHashSet<String> logs;
    private boolean recordingLogs;
    private int requestCount;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    public VpnModel(Context context) {
        this(context, AppDatabase.getInstance(context).hostEntryDao());
    }

    VpnModel(Context context, HostEntryDao hostEntryDao) {
        super(context);
        this.hostEntryDao = hostEntryDao;
        this.blockCache = createRulesCache();
        this.logs = new LinkedHashSet<>();
        this.recordingLogs = false;
        this.requestCount = 0;
        this.applied.postValue(VpnServiceControls.isRunning(context));
    }

    @Override
    public AdBlockMethod getMethod() {
        return VPN;
    }

    @Override
    public void apply() throws HostErrorException {
        // Clear cache
        invalidateRulesCache();
        // Start VPN
        boolean started = android.net.VpnService.prepare(this.context) == null
                && VpnServiceControls.start(this.context);
        this.applied.postValue(started);
        if (!started) {
            throw new HostErrorException(ENABLE_VPN_FAIL);
        }
        setState(R.string.status_vpn_configuration_updated);
    }

    /**
     * Clear cached rule lookups after the runtime rule table changes.
     */
    public void invalidateRulesCache() {
        // LruCache.create runs outside its lock. Retire the entire cache so an in-flight
        // lookup can only publish into its old cache, without blocking on database I/O.
        this.blockCache = createRulesCache();
        this.requestCount = 0;
    }

    private LruCache<String, HostEntry> createRulesCache() {
        return new LruCache<String, HostEntry>(4 * 1024) {
            @Override
            protected HostEntry create(String key) {
                return VpnModel.this.hostEntryDao.resolveEntry(key);
            }
        };
    }

    @Override
    public void revert() {
        VpnServiceControls.stop(this.context);
        this.applied.postValue(false);
    }

    @Override
    public boolean isRecordingLogs() {
        return this.recordingLogs;
    }

    @Override
    public void setRecordingLogs(boolean recording) {
        this.recordingLogs = recording;
    }

    @Override
    public List<String> getLogs() {
        synchronized (this.logs) {
            return new ArrayList<>(this.logs);
        }
    }

    @Override
    public void clearLogs() {
        synchronized (this.logs) {
            this.logs.clear();
        }
    }

    /**
     * Checks host entry related to an host name.
     *
     * @param host A hostname to check.
     * @return The related host entry.
     */
    public HostEntry getEntry(String host) {
        LruCache<String, HostEntry> cache = this.blockCache;
        // Compute miss rate periodically
        this.requestCount++;
        if (this.requestCount >= 1000) {
            int hits = cache.hitCount();
            int misses = cache.missCount();
            double missRate = 100D * (hits + misses) / misses;
            Timber.d("Host cache miss rate: %s.", missRate);
            this.requestCount = 0;
        }
        // Add host to logs
        if (this.recordingLogs) {
            synchronized (this.logs) {
                if (this.logs.add(host) && this.logs.size() > MAX_LOG_ENTRIES) {
                    String first = this.logs.iterator().next();
                    this.logs.remove(first);
                }
            }
        }
        // Check cache
        return cache.get(host);
    }
}
