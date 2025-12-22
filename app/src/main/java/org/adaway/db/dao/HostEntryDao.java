package org.adaway.db.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

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

    @Query("INSERT INTO `host_entries` SELECT DISTINCT `host`, `type`, `redirection` FROM `hosts_lists` WHERE `type` = 0 AND `enabled` = 1")
    void importBlocked();

    @Query("SELECT host FROM hosts_lists WHERE type = 1 AND enabled = 1")
    List<String> getEnabledAllowedHosts();

    @Query("DELETE FROM `host_entries` WHERE `host` LIKE :hostPattern")
    void allowHost(String hostPattern);

    @Query("SELECT * FROM hosts_lists WHERE type = 2 AND enabled = 1 ORDER BY host ASC, source_id DESC")
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

        // Process allowed hosts - LIKE patterns can't be batched, but transaction wrapping
        // ensures all deletes commit together (the main performance win)
        for (String allowedHost : getEnabledAllowedHosts()) {
            String pattern = ANY_CHAR_PATTERN.matcher(allowedHost).replaceAll("%");
            pattern = A_CHAR_PATTERN.matcher(pattern).replaceAll("_");
            allowHost(pattern);
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
