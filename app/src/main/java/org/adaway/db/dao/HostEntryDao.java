package org.adaway.db.dao;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Transaction;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static androidx.room.OnConflictStrategy.REPLACE;
import static org.adaway.db.entity.ListType.REDIRECTED;

/**
 * This interface is the DAO for {@link HostEntry} records.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Dao
public interface HostEntryDao {
    Pattern ANY_CHAR_PATTERN = Pattern.compile("\\*");
    Pattern A_CHAR_PATTERN = Pattern.compile("\\?");

    @Query("DELETE FROM `host_entries`")
    void clear();

    /**
     * Fast blocked host count based on the built host_entries table (unique hosts, no DISTINCT needed).
     * This is used for the Home "Blocked" counter so it doesn't stall on huge DISTINCT queries.
     */
    @Query("SELECT COUNT(*) FROM `host_entries` WHERE `type` = 0")
    LiveData<Integer> getBlockedEntryCount();

    @Query("SELECT COUNT(*) FROM `host_entries` WHERE `type` = 0")
    int getBlockedEntryCountNow();

    @Query("INSERT INTO `host_entries` " +
            "SELECT DISTINCT `host`, `type`, `redirection` FROM `hosts_lists` " +
            "WHERE `type` = 0 AND `enabled` = 1 AND (`source_id` = 1 OR `generation` = (SELECT active_generation FROM hosts_meta WHERE id = 0))")
    void importBlocked();

    @Query("SELECT host FROM hosts_lists WHERE type = 1 AND enabled = 1 AND (source_id = 1 OR generation = (SELECT active_generation FROM hosts_meta WHERE id = 0))")
    List<String> getEnabledAllowedHosts();

    @Query("DELETE FROM `host_entries` WHERE `host` LIKE :hostPattern")
    void allowHost(String hostPattern);

    @RawQuery
    int allowHostsRaw(SupportSQLiteQuery query);

    @Query("SELECT * FROM hosts_lists WHERE type = 2 AND enabled = 1 AND (source_id = 1 OR generation = (SELECT active_generation FROM hosts_meta WHERE id = 0)) ORDER BY host ASC, source_id DESC")
    List<HostListItem> getEnabledRedirectedHosts();

    @Insert(onConflict = REPLACE)
    void redirectHost(HostEntry redirection);

    /**
     * Batch insert redirect entries.
     */
    @Insert(onConflict = REPLACE)
    void insertAll(List<HostEntry> entries);

    /**
     * Synchronize the host entries based on the current hosts lists table records.
     * Wrapped in @Transaction to ensure all operations commit atomically,
     * dramatically reducing disk I/O overhead.
     */
    @Transaction
    default void sync() {
        clear();
        importBlocked();

        // Batch delete allowed hosts - build dynamic query with OR'd LIKE patterns
        List<String> allowedHosts = getEnabledAllowedHosts();
        if (!allowedHosts.isEmpty()) {
            // Process in batches of 50 to avoid query size limits
            int batchSize = 50;
            for (int i = 0; i < allowedHosts.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allowedHosts.size());
                List<String> batch = allowedHosts.subList(i, end);

                // Build patterns and query
                StringBuilder sql = new StringBuilder("DELETE FROM host_entries WHERE ");
                Object[] args = new Object[batch.size()];
                for (int j = 0; j < batch.size(); j++) {
                    if (j > 0) sql.append(" OR ");
                    sql.append("host LIKE ?");
                    String pattern = ANY_CHAR_PATTERN.matcher(batch.get(j)).replaceAll("%");
                    pattern = A_CHAR_PATTERN.matcher(pattern).replaceAll("_");
                    args[j] = pattern;
                }

                allowHostsRaw(new SimpleSQLiteQuery(sql.toString(), args));
            }
        }

        // Batch insert all redirect entries at once instead of one-by-one
        List<HostListItem> redirectedHosts = getEnabledRedirectedHosts();
        if (!redirectedHosts.isEmpty()) {
            List<HostEntry> entries = new ArrayList<>(redirectedHosts.size());
            for (HostListItem item : redirectedHosts) {
                HostEntry entry = new HostEntry();
                entry.setHost(item.getHost());
                entry.setType(REDIRECTED);
                entry.setRedirection(item.getRedirection());
                entries.add(entry);
            }
            insertAll(entries);
        }
    }

    @Query("SELECT * FROM `host_entries` ORDER BY `host`")
    List<HostEntry> getAll();

    @Query("SELECT `type` FROM `host_entries` WHERE `host` == :host LIMIT 1")
    ListType getTypeOfHost(String host);

    @Query("SELECT IFNULL((SELECT `type` FROM `host_entries` WHERE `host` == :host LIMIT 1), 1)")
    ListType getTypeForHost(String host);

    @Nullable
    @Query("SELECT * FROM `host_entries` WHERE `host` == :host LIMIT 1")
    HostEntry getEntry(String host);
}
