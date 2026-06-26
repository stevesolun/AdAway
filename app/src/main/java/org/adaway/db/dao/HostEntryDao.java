package org.adaway.db.dao;

import android.database.Cursor;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.adaway.db.ActiveRootHostsCursor;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.ListType;
import org.adaway.util.Hostnames;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import timber.log.Timber;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.db.entity.RuleKind.SUFFIX;

/**
 * This interface is the DAO for {@link HostEntry} records.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Dao
public interface HostEntryDao {
    String ROOT_EXPORT_INDEX_NAME = "index_host_entries_kind_host";
    String ROOT_EXPORT_INDEX_SQL = "CREATE INDEX IF NOT EXISTS `" + ROOT_EXPORT_INDEX_NAME +
            "` ON `host_entries` (`kind`, `host`, `type`, `redirection`)";
    String ROOT_EXPORT_REVERSE_INDEX_NAME = "index_root_host_entries_reverse_host";
    String ROOT_EXPORT_REVERSE_INDEX_SQL = "CREATE INDEX IF NOT EXISTS `" +
            ROOT_EXPORT_REVERSE_INDEX_NAME +
            "` ON `root_host_entries` (`reverse_host`, `host`)";
    String ROOT_EXPORT_REVERSE_LOOKUP_INDEX_SQL = "CREATE INDEX IF NOT EXISTS `" +
            ROOT_EXPORT_REVERSE_INDEX_NAME + "` ON `root_host_entries` (`reverse_host`)";
    String ROOT_EXPORT_HOST_INDEX_NAME = "index_root_host_entries_host";
    String ROOT_EXPORT_HOST_INDEX_SQL = "CREATE INDEX IF NOT EXISTS `" +
            ROOT_EXPORT_HOST_INDEX_NAME + "` ON `root_host_entries` (`host`)";
    String RUNTIME_SUFFIX_INDEX_NAME = "index_host_entries_kind_reverse_host";
    String RUNTIME_SUFFIX_INDEX_SQL = "CREATE INDEX IF NOT EXISTS `" + RUNTIME_SUFFIX_INDEX_NAME +
            "` ON `host_entries` (`kind`, `reverse_host`, `host`)";
    String ACTIVE_GENERATION_IMPORT_INDEX_NAME =
            "index_hosts_lists_active_generation_kind_host";
    String ROOT_EXPORT_STAGE_TABLE = "root_host_entries_stage";
    String ROOT_EXPORT_STAGE_SOURCE_GENERATION_INDEX_NAME =
            "index_root_host_entries_stage_source_generation";
    String ROOT_EXPORT_STAGE_GENERATION_SOURCE_INDEX_NAME =
            "index_root_host_entries_stage_generation_source";
    String ROOT_EXPORT_STAGE_REVERSE_HOST_INDEX_NAME =
            "index_root_host_entries_stage_reverse_host";
    String RUNTIME_REBUILD_CACHE_SIZE_SQL = "PRAGMA cache_size=-65536";
    String RUNTIME_REBUILD_TEMP_STORE_SQL = "PRAGMA temp_store=MEMORY";
    long MATERIALIZED_RUNTIME_CACHE_MAX_ROWS = 500_000L;
    int SUFFIX_ALLOW_DELETE_BATCH_SIZE = 50_000;
    String TEMP_SUFFIX_ALLOW_DELETE_HOSTS = "suffix_allow_delete_hosts";
    String TEMP_ROOT_EXPORT_SKIP_STAGE_IDS = "root_export_skip_stage_ids";
    String TEMP_ROOT_EXPORT_REDIRECT_STAGE_CANDIDATES =
            "root_export_redirect_stage_candidates";
    String TEMP_ROOT_EXPORT_REDIRECT_STAGE_CONFLICT_HOSTS =
            "root_export_redirect_stage_conflict_hosts";
    String TEMP_ROOT_EXPORT_REDIRECT_STAGE_WINNER_IDS =
            "root_export_redirect_stage_winner_ids";
    String TEMP_ROOT_EXPORT_REDIRECT_STAGE_WINNER_HOST_INDEX =
            "index_root_export_redirect_stage_winner_host";
    String ROOT_STAGE_ACTIVE_SOURCE_WHERE = "(`entry`.`source_id` = 1 OR " +
            "(`entry`.`generation` = :activeGeneration AND `entry`.`source_id` != 1 " +
            "AND EXISTS (SELECT 1 FROM `hosts_sources` AS `source` " +
            "WHERE `source`.`id` = `entry`.`source_id` AND `source`.`enabled` = 1)))";
    String ROOT_STAGE_MATERIALIZED_SOURCE_WHERE = "(`entry`.`source_id` = 1 OR " +
            "(`entry`.`generation` = :activeGeneration AND `entry`.`source_id` != 1))";
    String ROOT_STAGE_SKIP_WHERE = "NOT EXISTS (SELECT 1 FROM `" +
            TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "` AS `skipped` " +
            "WHERE `skipped`.`id` = `entry`.`id`)";
    String ACTIVE_RULES_CTE = "WITH `active_rules` AS (" +
            "SELECT LOWER(`host`) AS `host`, `reverse_host`, `kind`, `type`, " +
            "`redirection`, `source_id`, " +
            "CASE WHEN `source_id` = 1 THEN 0 ELSE 1 END AS `source_priority` " +
            "FROM `hosts_lists` WHERE `enabled` = 1 AND " +
            "(`source_id` = 1 OR (`generation` = " +
            "(SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0) " +
            "AND `source_id` != 1)))";
    String ACTIVE_GENERATION_SQL =
            "(SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0)";
    String ACTIVE_STATS_ROWS_SQL = "(" +
            "SELECT `type`, `kind` FROM `hosts_lists` " +
            "INDEXED BY `index_hosts_lists_kind_host` " +
            "WHERE `enabled` = 1 AND (`source_id` = 1 OR (`generation` = " +
            ACTIVE_GENERATION_SQL + " AND `source_id` != 1)))";
    String ACTIVE_ALLOW_NOT_EXISTS = "NOT EXISTS (" +
            "SELECT 1 FROM `active_rules` AS `allowed` WHERE `allowed`.`type` = 1 " +
            "AND ((`allowed`.`kind` = 0 AND `entry`.`host` LIKE " +
            "REPLACE(REPLACE(`allowed`.`host`, '*', '%'), '?', '_')) " +
            "OR (`allowed`.`kind` = 1 AND (`entry`.`host` = `allowed`.`host` " +
            "OR `entry`.`host` LIKE '%.' || `allowed`.`host`))))";
    String ROOT_EXPORT_CANDIDATES_CTE = ACTIVE_RULES_CTE + ", `root_candidates` AS (" +
            "SELECT `entry`.`host`, `entry`.`reverse_host`, `entry`.`type`, " +
            "`entry`.`redirection`, " +
            "CASE WHEN `entry`.`type` = 2 THEN 0 ELSE 1 END AS `precedence`, " +
            "`entry`.`source_priority`, `entry`.`source_id` " +
            "FROM `active_rules` AS `entry` WHERE `entry`.`kind` = 0 " +
            "AND `entry`.`type` IN (0, 2) " +
            "AND (`entry`.`type` = 2 OR " + ACTIVE_ALLOW_NOT_EXISTS + ") " +
            "UNION ALL SELECT `entry`.`host`, `entry`.`reverse_host`, 0 AS `type`, " +
            "`entry`.`redirection`, " +
            "2 AS `precedence`, `entry`.`source_priority`, `entry`.`source_id` " +
            "FROM `active_rules` AS `entry` WHERE `entry`.`kind` = 1 " +
            "AND `entry`.`type` = 0 AND " + ACTIVE_ALLOW_NOT_EXISTS + " " +
            "AND NOT EXISTS (SELECT 1 FROM `active_rules` AS `exact` " +
            "WHERE `exact`.`host` = `entry`.`host` AND `exact`.`kind` = 0 " +
            "AND `exact`.`type` IN (0, 2))) ";
    String ROOT_EXPORT_BEST_CANDIDATES_WHERE =
            "WHERE NOT EXISTS (SELECT 1 FROM `root_candidates` AS `better` " +
            "WHERE `better`.`host` = `candidate`.`host` AND " +
            "(`better`.`precedence` < `candidate`.`precedence` " +
            "OR (`better`.`precedence` = `candidate`.`precedence` " +
            "AND `better`.`source_priority` < `candidate`.`source_priority`) " +
            "OR (`better`.`precedence` = `candidate`.`precedence` " +
            "AND `better`.`source_priority` = `candidate`.`source_priority` " +
            "AND `better`.`source_id` < `candidate`.`source_id`) " +
            "OR (`better`.`precedence` = `candidate`.`precedence` " +
            "AND `better`.`source_priority` = `candidate`.`source_priority` " +
            "AND `better`.`source_id` = `candidate`.`source_id` " +
            "AND COALESCE(`better`.`redirection`, '') < " +
            "COALESCE(`candidate`.`redirection`, ''))))";
    String ROOT_EXPORT_ACTIVE_QUERY = ROOT_EXPORT_CANDIDATES_CTE +
            "SELECT DISTINCT `candidate`.`host`, `candidate`.`reverse_host`, " +
            "0 AS `kind`, `candidate`.`type`, " +
            "`candidate`.`redirection` FROM `root_candidates` AS `candidate` " +
            ROOT_EXPORT_BEST_CANDIDATES_WHERE + " " +
            "ORDER BY `candidate`.`host`";
    String ROOT_EXPORT_ACTIVE_INSERT_SQL = ROOT_EXPORT_CANDIDATES_CTE +
            "INSERT OR REPLACE INTO `root_host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT DISTINCT `candidate`.`host`, `candidate`.`reverse_host`, 0 AS `kind`, " +
            "`candidate`.`type`, `candidate`.`redirection` " +
            "FROM `root_candidates` AS `candidate` " +
            ROOT_EXPORT_BEST_CANDIDATES_WHERE + " " +
            "ORDER BY `candidate`.`host`";
    String ACTIVE_RULES_WITH_GENERATION_PARAM =
            "`enabled` = 1 AND (`source_id` = 1 OR (`generation` = :activeGeneration " +
                    "AND `source_id` != 1))";
    String ACTIVE_EXACT_RULE_NOT_EXISTS =
            "AND NOT EXISTS (SELECT 1 FROM `hosts_lists` AS `exact` " +
                    "WHERE `exact`.`host` = `entry`.`host` " +
                    "AND `exact`.`kind` = 0 AND `exact`.`type` IN (0, 2) " +
                    "AND `exact`.`enabled` = 1 AND (`exact`.`source_id` = 1 " +
                    "OR (`exact`.`generation` = :activeGeneration " +
                    "AND `exact`.`source_id` != 1)))";
    String ROOT_EXPORT_ACTIVE_CANDIDATE_QUERY =
            "SELECT LOWER(`host`) AS `host`, `type`, `redirection` " +
                    "FROM `hosts_lists` WHERE `type` IN (0, 2) AND " +
                    ACTIVE_RULES_WITH_GENERATION_PARAM + " " +
                    "AND (`type` = 2 OR `kind` IN (0, 1)) " +
                    "ORDER BY LOWER(`host`) ASC, " +
                    "CASE WHEN `type` = 2 THEN 0 WHEN `kind` = 0 THEN 1 ELSE 2 END ASC, " +
                    "CASE WHEN `source_id` = 1 THEN 0 ELSE 1 END ASC, " +
                    "`source_id` ASC, COALESCE(`redirection`, '') ASC";
    @Query("DELETE FROM `host_entries`")
    void clear();

    @Query("DELETE FROM `root_host_entries`")
    void clearRootExport();

    @Query("UPDATE `hosts_stats` SET `root_export_materialized` = :materialized, " +
            "`root_export_stage_materialized` = 0 " +
            "WHERE `id` = 0")
    void setRootExportMaterialized(boolean materialized);

    @Query("UPDATE `hosts_stats` SET `root_export_materialized` = :materialized, " +
            "`root_export_stage_materialized` = :materialized WHERE `id` = 0")
    void setRootExportStageMaterialized(boolean materialized);

    default void invalidateMaterializedRuntimeCaches() {
        clear();
        invalidateRootExportMaterializedCache();
    }

    default void invalidateRootExportMaterializedCache() {
        clearRootExport();
        setRootExportMaterialized(false);
    }

    @Query("UPDATE `hosts_sources` SET " +
            "`size` = (SELECT COUNT(`id`) FROM `hosts_lists` WHERE `source_id` = 1), " +
            "`active_rule_count` = (SELECT COUNT(`id`) FROM `hosts_lists` " +
            "WHERE `source_id` = 1 AND `enabled` = 1), " +
            "`blocked_count` = (SELECT COUNT(`id`) FROM `hosts_lists` " +
            "WHERE `source_id` = 1 AND `enabled` = 1 AND `type` = 0), " +
            "`blocked_exact_count` = (SELECT COUNT(`id`) FROM `hosts_lists` " +
            "WHERE `source_id` = 1 AND `enabled` = 1 AND `type` = 0 AND `kind` = 0), " +
            "`allowed_count` = (SELECT COUNT(`id`) FROM `hosts_lists` " +
            "WHERE `source_id` = 1 AND `enabled` = 1 AND `type` = 1), " +
            "`redirected_count` = (SELECT COUNT(`id`) FROM `hosts_lists` " +
            "WHERE `source_id` = 1 AND `enabled` = 1 AND `type` = 2) " +
            "WHERE `id` = 1")
    void refreshUserSourceStats();

    @Query("INSERT OR REPLACE INTO `hosts_stats` " +
            "(`id`, `blocked_count`, `blocked_exact_count`, `allowed_count`, " +
            "`redirected_count`, `active_rule_count`) SELECT 0, " +
            "COALESCE(SUM(`blocked_count`), 0), " +
            "COALESCE(SUM(`blocked_exact_count`), 0), " +
            "COALESCE(SUM(`allowed_count`), 0), " +
            "COALESCE(SUM(`redirected_count`), 0), " +
            "COALESCE(SUM(`active_rule_count`), 0) " +
            "FROM `hosts_sources` WHERE `enabled` = 1")
    void refreshStatsFromSourceMetadata();

    @Query("INSERT OR REPLACE INTO `hosts_stats` " +
            "(`id`, `blocked_count`, `blocked_exact_count`, `allowed_count`, " +
            "`redirected_count`, `active_rule_count`) SELECT 0, " +
            "COALESCE(SUM(CASE WHEN `type` = 0 THEN 1 ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN `type` = 0 AND `kind` = 0 THEN 1 ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN `type` = 1 THEN 1 ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN `type` = 2 THEN 1 ELSE 0 END), 0), " +
            "COUNT(*) FROM " + ACTIVE_STATS_ROWS_SQL)
    void refreshStatsFromActiveRows();

    @Query("UPDATE `hosts_stats` SET " +
            "`blocked_count` = (SELECT COUNT(*) FROM `host_entries` WHERE `type` = 0), " +
            "`blocked_exact_count` = (SELECT COUNT(*) FROM `host_entries` " +
            "WHERE `type` = 0 AND `kind` = 0), " +
            "`redirected_count` = (SELECT COUNT(*) FROM `host_entries` WHERE `type` = 2) " +
            "WHERE `id` = 0")
    void refreshStatsFromMaterializedRuntime();

    @Query("SELECT EXISTS(SELECT 1 FROM `hosts_lists` WHERE `enabled` = 1 AND " +
            "(`source_id` = 1 OR (`generation` = " +
            "(SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0) " +
            "AND `source_id` != 1)) LIMIT 1)")
    boolean hasActiveRuntimeRows();

    @Transaction
    default void refreshStatsFromActiveGeneration() {
        refreshUserSourceStats();
        refreshStatsFromSourceMetadata();
        if (getActiveRuntimeRuleCountNow() == 0 && hasActiveRuntimeRows()) {
            refreshStatsFromActiveRows();
        }
    }

    @Query("SELECT `blocked_count` FROM `hosts_stats` WHERE `id` = 0")
    LiveData<Integer> getBlockedEntryCount();

    @Query("SELECT `blocked_count` FROM `hosts_stats` WHERE `id` = 0")
    int getBlockedEntryCountNow();

    @Query("SELECT `blocked_exact_count` FROM `hosts_stats` WHERE `id` = 0")
    LiveData<Integer> getBlockedExactEntryCount();

    @Query("SELECT `blocked_exact_count` FROM `hosts_stats` WHERE `id` = 0")
    int getBlockedExactEntryCountNow();

    @Query("SELECT `active_rule_count` FROM `hosts_stats` WHERE `id` = 0")
    int getActiveRuntimeRuleCountNow();

    @Query("SELECT `redirected_count` FROM `hosts_stats` WHERE `id` = 0")
    int getRedirectedEntryCountNow();

    @Query("SELECT EXISTS(SELECT 1 FROM `hosts_lists` WHERE `type` = 1 AND `enabled` = 1 " +
            "AND (`source_id` == 1 OR `generation` = " +
            "(SELECT active_generation FROM hosts_meta WHERE id = 0)) LIMIT 1)")
    boolean hasActiveAllowedRules();

    @Query("SELECT active_generation FROM hosts_meta WHERE id = 0 LIMIT 1")
    int getActiveGeneration();

    @Query("SELECT COUNT(*) FROM `hosts_lists` WHERE `enabled` = 1 AND " +
            "(`source_id` = 1 OR (`generation` = " +
            "(SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0) " +
            "AND `source_id` != 1))")
    long countActiveRuntimeRuleRows();

    @Query("SELECT EXISTS(SELECT 1 FROM `hosts_lists` WHERE `type` = 1 AND `enabled` = 1 " +
            "AND `source_id` == 1 LIMIT 1)")
    boolean hasUserAllowedRules();

    @Query("SELECT EXISTS(SELECT 1 FROM `hosts_lists` WHERE `type` = 1 AND `enabled` = 1 " +
            "AND `generation` = :activeGeneration AND `source_id` != 1 LIMIT 1)")
    boolean hasSourceAllowedRules(int activeGeneration);

    default boolean hasActiveWildcardExactAllowedRules(int activeGeneration) {
        return hasUserWildcardExactAllowedRules()
                || hasSourceWildcardExactAllowedRules(activeGeneration);
    }

    @Query("SELECT EXISTS(SELECT 1 FROM `hosts_lists` " +
            "INDEXED BY `index_hosts_lists_active_allow_source_kind_host` " +
            "WHERE `type` = 1 AND `enabled` = 1 AND `kind` = 0 AND `source_id` = 1 " +
            "AND (instr(`host`, '*') > 0 OR instr(`host`, '?') > 0 " +
            "OR instr(`host`, '%') > 0) LIMIT 1)")
    boolean hasUserWildcardExactAllowedRules();

    @Query("SELECT EXISTS(SELECT 1 FROM `hosts_lists` " +
            "INDEXED BY `index_hosts_lists_active_allow_generation_source_kind_host` " +
            "WHERE `type` = 1 AND `enabled` = 1 AND `kind` = 0 " +
            "AND `generation` = :activeGeneration AND `source_id` != 1 " +
            "AND (instr(`host`, '*') > 0 OR instr(`host`, '?') > 0 " +
            "OR instr(`host`, '%') > 0) LIMIT 1)")
    boolean hasSourceWildcardExactAllowedRules(int activeGeneration);

    @Query("INSERT OR IGNORE INTO `host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT `host`, `reverse_host`, `kind`, 0, MIN(`redirection`) " +
            "FROM `hosts_lists` WHERE `type` = 0 AND `enabled` = 1 AND " +
            "(source_id == 1 OR generation = (SELECT active_generation FROM hosts_meta WHERE id = 0)) " +
            "GROUP BY `host`, `reverse_host`, `kind` ORDER BY `host`, `kind`")
    void importBlocked();

    @Query("INSERT OR IGNORE INTO `host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT `host`, `reverse_host`, `kind`, 0, MIN(`redirection`) " +
            "FROM `hosts_lists` INDEXED BY `index_hosts_lists_type_enabled_source_id` " +
            "WHERE `type` = 0 AND `enabled` = 1 AND `source_id` == 1 " +
            "GROUP BY `host`, `reverse_host`, `kind` ORDER BY `host`, `kind`")
    void importBlockedUser();

    @Query("INSERT OR IGNORE INTO `host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT `host`, `reverse_host`, `kind`, 0, MIN(`redirection`) " +
            "FROM `hosts_lists` INDEXED BY `index_hosts_lists_type_enabled_generation` " +
            "WHERE `type` = 0 AND `enabled` = 1 " +
            "AND `generation` = :activeGeneration AND `source_id` != 1 " +
            "GROUP BY `host`, `reverse_host`, `kind` ORDER BY `host`, `kind`")
    void importBlockedSources(int activeGeneration);

    @Query("INSERT OR IGNORE INTO `host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT `host`, `reverse_host`, 0, 0, `redirection` " +
            "FROM `hosts_lists` INDEXED BY `index_hosts_lists_active_generation_kind_host` " +
            "WHERE `kind` = 0 AND `type` = 0 AND `enabled` = 1 " +
            "AND `generation` = :activeGeneration AND `source_id` != 1 " +
            "ORDER BY `host`")
    void importBlockedExactSources(int activeGeneration);

    @Query("INSERT OR IGNORE INTO `host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT `host`, `reverse_host`, 1, 0, `redirection` " +
            "FROM `hosts_lists` INDEXED BY `index_hosts_lists_active_generation_kind_host` " +
            "WHERE `kind` = 1 AND `type` = 0 AND `enabled` = 1 " +
            "AND `generation` = :activeGeneration AND `source_id` != 1 " +
            "ORDER BY `host`")
    void importBlockedSuffixSources(int activeGeneration);

    @Query("SELECT EXISTS(SELECT 1 FROM hosts_lists WHERE type = 1 AND enabled = 1 AND " +
            "(source_id == 1 OR generation = (SELECT active_generation FROM hosts_meta WHERE id = 0)) " +
            "AND ((kind = 0 AND :host LIKE REPLACE(REPLACE(LOWER(host), '*', '%'), '?', '_')) " +
            "OR (kind = 1 AND (:host = LOWER(host) OR :host LIKE '%.' || LOWER(host)))) LIMIT 1)")
    boolean isAllowedByActiveRule(String host);

    @Query("DELETE FROM `host_entries` WHERE `kind` = 0 AND `host` IN (" +
            "SELECT allowed.`host` FROM `hosts_lists` AS allowed WHERE allowed.`type` = 1 " +
            "AND allowed.`enabled` = 1 AND allowed.`kind` = 0 " +
            "AND (allowed.`source_id` == 1 OR (allowed.`generation` = :activeGeneration " +
            "AND allowed.`source_id` != 1)) " +
            "AND instr(allowed.`host`, '*') = 0 AND instr(allowed.`host`, '?') = 0 " +
            "AND instr(allowed.`host`, '%') = 0)")
    void deleteExactRowsAllowedByActiveLiteralRules(int activeGeneration);

    @Query("DELETE FROM `host_entries` WHERE `kind` = 0 AND EXISTS (" +
            "SELECT 1 FROM `hosts_lists` AS allowed WHERE allowed.`type` = 1 " +
            "AND allowed.`enabled` = 1 " +
            "AND allowed.`kind` = 0 " +
            "AND (allowed.`source_id` == 1 OR (allowed.`generation` = :activeGeneration " +
            "AND allowed.`source_id` != 1)) " +
            "AND (instr(allowed.`host`, '*') > 0 OR instr(allowed.`host`, '?') > 0 " +
            "OR instr(allowed.`host`, '%') > 0) " +
            "AND `host_entries`.`host` LIKE " +
            "REPLACE(REPLACE(LOWER(allowed.`host`), '*', '%'), '?', '_') LIMIT 1)")
    void deleteExactRowsAllowedByActiveWildcardRules(int activeGeneration);

    @Query("WITH RECURSIVE `exact_suffixes`(`host`, `suffix`) AS (" +
            "SELECT `host`, `host` FROM `host_entries` WHERE `kind` = 0 " +
            "AND `host` > :afterHost " +
            "AND (:upperHost IS NULL OR `host` <= :upperHost) " +
            "UNION ALL SELECT `host`, substr(`suffix`, instr(`suffix`, '.') + 1) " +
            "FROM `exact_suffixes` WHERE instr(`suffix`, '.') > 0) " +
            "DELETE FROM `host_entries` WHERE `kind` = 0 AND `host` IN (" +
            "SELECT suffixes.`host` FROM `exact_suffixes` AS suffixes " +
            "JOIN `hosts_lists` AS allowed ON allowed.`host` = suffixes.`suffix` " +
            "WHERE allowed.`type` = 1 AND allowed.`enabled` = 1 " +
            "AND allowed.`kind` = 1 " +
            "AND (allowed.`source_id` == 1 OR (allowed.`generation` = :activeGeneration " +
            "AND allowed.`source_id` != 1)))")
    int deleteExactRowsAllowedByActiveSuffixRulesBatch(
            int activeGeneration, String afterHost, @Nullable String upperHost);

    @Query("WITH RECURSIVE `blocked_suffixes`(`host`, `suffix`) AS (" +
            "SELECT `host`, `host` FROM `host_entries` WHERE `kind` = 1 " +
            "AND `host` > :afterHost " +
            "AND (:upperHost IS NULL OR `host` <= :upperHost) " +
            "UNION ALL SELECT `host`, substr(`suffix`, instr(`suffix`, '.') + 1) " +
            "FROM `blocked_suffixes` WHERE instr(`suffix`, '.') > 0) " +
            "DELETE FROM `host_entries` WHERE `kind` = 1 AND `host` IN (" +
            "SELECT suffixes.`host` FROM `blocked_suffixes` AS suffixes " +
            "JOIN `hosts_lists` AS allowed ON allowed.`host` = suffixes.`suffix` " +
            "WHERE allowed.`type` = 1 AND allowed.`enabled` = 1 " +
            "AND allowed.`kind` = 1 " +
            "AND (allowed.`source_id` == 1 OR (allowed.`generation` = :activeGeneration " +
            "AND allowed.`source_id` != 1)))")
    int deleteSuffixRowsAllowedByActiveSuffixRulesBatch(
            int activeGeneration, String afterHost, @Nullable String upperHost);

    @Query("SELECT `host` FROM `host_entries` WHERE `kind` = :kind AND `host` > :afterHost " +
            "ORDER BY `host` LIMIT 1 OFFSET :offset")
    @Nullable
    String getHostEntryBatchUpperBound(int kind, String afterHost, int offset);

    @Query("INSERT OR REPLACE INTO `host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT LOWER(`host`), `reverse_host`, 0, `type`, `redirection` " +
            "FROM `hosts_lists` " +
            "WHERE `type` = 2 AND `enabled` = 1 AND " +
            "(`source_id` == 1 OR `generation` = " +
            "(SELECT active_generation FROM hosts_meta WHERE id = 0)) " +
            "ORDER BY `host` ASC, `source_id` DESC, COALESCE(`redirection`, '') DESC")
    void importRedirected();

    @Query("INSERT OR REPLACE INTO `host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT LOWER(`host`), `reverse_host`, 0, `type`, `redirection` " +
            "FROM `hosts_lists` " +
            "WHERE `type` = 2 AND `enabled` = 1 " +
            "AND `generation` = :activeGeneration AND `source_id` != 1 " +
            "ORDER BY `host` ASC, `source_id` DESC, COALESCE(`redirection`, '') DESC")
    void importRedirectedSources(int activeGeneration);

    @Query("INSERT OR REPLACE INTO `host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT LOWER(`host`), `reverse_host`, 0, `type`, `redirection` " +
            "FROM `hosts_lists` " +
            "WHERE `type` = 2 AND `enabled` = 1 AND `source_id` == 1 " +
            "ORDER BY `host` ASC, COALESCE(`redirection`, '') DESC")
    void importRedirectedUser();

    @Query("INSERT INTO `root_host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT `host`, `reverse_host`, 0, `type`, `redirection` " +
            "FROM `host_entries` WHERE `kind` = 0")
    void materializeRootExactRows();

    @Query(ROOT_EXPORT_ACTIVE_INSERT_SQL)
    void materializeRootExportFromActiveRulesFallback();

    @Query("INSERT INTO `root_host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT entry.`host`, entry.`reverse_host`, 0, entry.`type`, entry.`redirection` " +
            "FROM `host_entries` AS entry WHERE entry.`kind` = 1 AND entry.`type` = 0 " +
            "AND NOT EXISTS (SELECT 1 FROM `host_entries` AS exact " +
            "WHERE exact.`kind` = 0 AND exact.`host` = entry.`host`)")
    void materializeRootSuffixRowsWithoutAllowRules();

    @Query("INSERT INTO `root_host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT entry.`host`, entry.`reverse_host`, 0, entry.`type`, entry.`redirection` " +
            "FROM `host_entries` AS entry WHERE entry.`kind` = 1 AND entry.`type` = 0 " +
            "AND NOT EXISTS (SELECT 1 FROM `host_entries` AS exact " +
            "WHERE exact.`kind` = 0 AND exact.`host` = entry.`host`) " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM `hosts_lists` AS allowed WHERE allowed.`type` = 1 " +
            "AND allowed.`enabled` = 1 " +
            "AND (allowed.`source_id` == 1 OR (allowed.`generation` = :activeGeneration " +
            "AND allowed.`source_id` != 1)) " +
            "AND ((allowed.`kind` = 0 AND entry.`host` LIKE " +
            "REPLACE(REPLACE(LOWER(allowed.`host`), '*', '%'), '?', '_')) " +
            "OR (allowed.`kind` = 1 AND (entry.`host` = LOWER(allowed.`host`) " +
            "OR entry.`host` LIKE '%.' || LOWER(allowed.`host`)))) LIMIT 1)")
    void materializeRootSuffixRowsWithAllowRules(int activeGeneration);

    @Query("INSERT INTO `root_host_entries` " +
            "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
            "SELECT entry.`host`, entry.`reverse_host`, 0, entry.`type`, entry.`redirection` " +
            "FROM `host_entries` AS entry WHERE entry.`kind` = 1 AND entry.`type` = 0 " +
            "AND NOT EXISTS (SELECT 1 FROM `host_entries` AS exact " +
            "WHERE exact.`kind` = 0 AND exact.`host` = entry.`host`) " +
            "AND entry.`host` NOT IN (" +
            "SELECT allowed.`host` FROM `hosts_lists` AS allowed WHERE allowed.`type` = 1 " +
            "AND allowed.`enabled` = 1 AND allowed.`kind` = 0 " +
            "AND (allowed.`source_id` == 1 OR (allowed.`generation` = :activeGeneration " +
            "AND allowed.`source_id` != 1)) " +
            "AND instr(allowed.`host`, '*') = 0 AND instr(allowed.`host`, '?') = 0 " +
            "AND instr(allowed.`host`, '%') = 0)")
    void materializeRootSuffixRowsWithIndexedAllowRules(int activeGeneration);

    default void materializeRootExport(@Nullable SupportSQLiteDatabase db, boolean hasAllowRules,
            boolean hasWildcardExactAllowRules, int activeGeneration) {
        setRootExportMaterialized(false);
        if (db != null) {
            dropRootExportIndexes(db);
        }
        clearRootExport();
        materializeRootExactRows();
        if (hasAllowRules) {
            if (hasWildcardExactAllowRules) {
                materializeRootSuffixRowsWithAllowRules(activeGeneration);
            } else {
                materializeRootSuffixRowsWithIndexedAllowRules(activeGeneration);
            }
        } else {
                materializeRootSuffixRowsWithoutAllowRules();
        }
        setRootExportMaterialized(true);
    }

    default void materializeRootExportFromActiveRules(@Nullable SupportSQLiteDatabase db,
            boolean hasAllowRules, boolean hasWildcardExactAllowRules, boolean hasRedirectRules,
            int activeGeneration) {
        setRootExportMaterialized(false);
        if (db == null) {
            clearRootExport();
            materializeRootExportFromActiveRulesFallback();
            setRootExportMaterialized(true);
            return;
        }

        long startedMs = SystemClock.elapsedRealtime();
        dropRootExportIndexes(db);
        long indexDropMs = SystemClock.elapsedRealtime();
        clearRootExport();
        long clearMs = SystemClock.elapsedRealtime();
        insertRootExportBlockedRows(db, true, EXACT.getValue(), SUFFIX.getValue(),
                activeGeneration);
        insertRootExportBlockedRows(db, false, EXACT.getValue(), SUFFIX.getValue(),
                activeGeneration);
        long blockedMs = SystemClock.elapsedRealtime();
        long indexCreateMs = SystemClock.elapsedRealtime();
        if (hasAllowRules) {
            deleteRootExportRowsAllowedByLiteralExactRules(db, activeGeneration);
            if (hasWildcardExactAllowRules) {
                deleteRootExportRowsAllowedByWildcardExactRules(db, activeGeneration);
            }
            deleteRootExportRowsAllowedBySuffixRules(db, activeGeneration);
        }
        long allowMs = SystemClock.elapsedRealtime();
        long redirectShadowMs = allowMs;
        if (hasRedirectRules) {
            insertRootExportRedirectedRows(db, false, activeGeneration);
            insertRootExportRedirectedRows(db, true, activeGeneration);
        }
        long redirectedMs = SystemClock.elapsedRealtime();
        dedupeRootExportRows(db, hasRedirectRules);
        long dedupeMs = SystemClock.elapsedRealtime();
        setRootExportMaterialized(true);
        long finishedMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.root-export-direct perf: indexDropMs=%d clearMs=%d " +
                        "blockedMs=%d indexCreateMs=%d allowMs=%d redirectShadowMs=%d " +
                        "redirectedMs=%d dedupeMs=%d finishMs=%d totalMs=%d allowRules=%s " +
                        "wildcardExactAllowRules=%s",
                indexDropMs - startedMs,
                clearMs - indexDropMs,
                blockedMs - clearMs,
                indexCreateMs - blockedMs,
                allowMs - indexCreateMs,
                redirectShadowMs - allowMs,
                redirectedMs - redirectShadowMs,
                dedupeMs - redirectedMs,
                finishedMs - dedupeMs,
                finishedMs - startedMs,
                hasAllowRules,
                hasWildcardExactAllowRules);
    }

    default void materializeRootExportFromStagedCandidates(@NonNull SupportSQLiteDatabase db,
            boolean hasAllowRules, boolean hasWildcardExactAllowRules, boolean hasRedirectRules,
            int activeGeneration, long stagedCandidateRows) {
        long startedMs = SystemClock.elapsedRealtime();
        setRootExportMaterialized(false);
        dropRootExportIndexes(db);
        long indexDropMs = SystemClock.elapsedRealtime();
        clearRootExport();
        long clearMs = SystemClock.elapsedRealtime();
        boolean useStageBackedRootExport = shouldUseStageBackedRootExport(
                stagedCandidateRows, hasWildcardExactAllowRules);
        if (hasAllowRules) {
            prepareRootExportSkippedStageRows(db, activeGeneration);
        } else if (useStageBackedRootExport) {
            createRootExportSkippedStageRowsTable(db);
        }
        if (useStageBackedRootExport && hasRedirectRules) {
            prepareRootExportRedirectSkippedStageRows(db, activeGeneration);
        }
        if (useStageBackedRootExport) {
            prepareRootExportDuplicateSkippedStageRows(db, activeGeneration);
        }
        if (useStageBackedRootExport) {
            long stageBackedMs = SystemClock.elapsedRealtime();
            setRootExportStageMaterialized(true);
            long finishedMs = SystemClock.elapsedRealtime();
            Timber.i("HostEntryDao.root-export-stage perf: indexDropMs=%d clearMs=%d " +
                            "blockedMs=%d indexCreateMs=0 allowMs=0 redirectShadowMs=0 " +
                            "redirectedMs=0 dedupeMs=0 finishMs=%d totalMs=%d " +
                            "allowRules=%s wildcardExactAllowRules=%s stageBacked=true",
                    indexDropMs - startedMs,
                    clearMs - indexDropMs,
                    stageBackedMs - clearMs,
                    finishedMs - stageBackedMs,
                    finishedMs - startedMs,
                    hasAllowRules,
                    hasWildcardExactAllowRules);
            return;
        }
        insertRootExportStagedBlockedRows(db, true, activeGeneration);
        insertRootExportStagedBlockedRows(db, false, activeGeneration);
        if (hasAllowRules) {
            deleteRootExportSkippedStageRows(db);
        }
        long blockedMs = SystemClock.elapsedRealtime();
        long indexCreateMs = SystemClock.elapsedRealtime();
        if (hasAllowRules) {
            if (hasWildcardExactAllowRules) {
                deleteRootExportRowsAllowedByWildcardExactRules(db, activeGeneration);
            }
            clearRootExportSkippedStageRows(db);
        }
        long allowMs = SystemClock.elapsedRealtime();
        long redirectShadowMs = allowMs;
        if (hasRedirectRules) {
            insertRootExportStagedRedirectedRows(db, false, activeGeneration);
            insertRootExportStagedRedirectedRows(db, true, activeGeneration);
        }
        long redirectedMs = SystemClock.elapsedRealtime();
        dedupeRootExportRows(db, hasRedirectRules);
        long dedupeMs = SystemClock.elapsedRealtime();
        setRootExportMaterialized(true);
        long finishedMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.root-export-stage perf: indexDropMs=%d clearMs=%d " +
                        "blockedMs=%d indexCreateMs=%d allowMs=%d redirectShadowMs=%d " +
                        "redirectedMs=%d dedupeMs=%d finishMs=%d totalMs=%d allowRules=%s " +
                        "wildcardExactAllowRules=%s",
                indexDropMs - startedMs,
                clearMs - indexDropMs,
                blockedMs - clearMs,
                indexCreateMs - blockedMs,
                allowMs - indexCreateMs,
                redirectShadowMs - allowMs,
                redirectedMs - redirectShadowMs,
                dedupeMs - redirectedMs,
                finishedMs - dedupeMs,
                finishedMs - startedMs,
                hasAllowRules,
                hasWildcardExactAllowRules);
    }

    private boolean shouldUseStageBackedRootExport(long stagedCandidateRows,
            boolean hasWildcardExactAllowRules) {
        return stagedCandidateRows > MATERIALIZED_RUNTIME_CACHE_MAX_ROWS
                && !hasWildcardExactAllowRules;
    }

    /**
     * Rebuild host_entries based on the current active hosts_lists truth.
     *
     * This method has no side effects outside SQLite, so SourceModel can call it inside a larger
     * transaction that also changes source generation state. Call {@link #sync()} for standalone
     * runtime rebuilds.
     */
    default void rebuildFromActiveGeneration() {
        rebuildFromActiveGeneration(null);
    }

    default void rebuildFromActiveGeneration(@Nullable SupportSQLiteDatabase db) {
        long startedMs = SystemClock.elapsedRealtime();
        int activeGeneration = getActiveGeneration();
        long stagedCandidateRows = db == null ? 0L
                : getCompleteRootExportStageRows(db, activeGeneration);
        long stageCheckMs = SystemClock.elapsedRealtime();
        refreshUserSourceStats();
        long userStatsMs = SystemClock.elapsedRealtime();
        refreshStatsFromSourceMetadata();
        long aggregateStatsMs = SystemClock.elapsedRealtime();
        long activeRuleRows = getActiveRuntimeRuleCountNow();
        long activeRuleCountMs = SystemClock.elapsedRealtime();
        activeRuleRows = Math.max(activeRuleRows, stagedCandidateRows);
        if (activeRuleRows == 0 && hasActiveRuntimeRows()) {
            refreshStatsFromActiveRows();
            activeRuleRows = getActiveRuntimeRuleCountNow();
        }
        long fallbackStatsMs = SystemClock.elapsedRealtime();
        if (activeRuleRows > MATERIALIZED_RUNTIME_CACHE_MAX_ROWS) {
            boolean hasAllowRules = hasUserAllowedRules() ||
                    hasSourceAllowedRules(activeGeneration);
            long allowProbeMs = SystemClock.elapsedRealtime();
            boolean hasWildcardExactAllowRules = hasAllowRules
                    && hasActiveWildcardExactAllowedRules(activeGeneration);
            long wildcardAllowProbeMs = SystemClock.elapsedRealtime();
            boolean hasRedirectRules = getRedirectedEntryCountNow() > 0;
            long redirectProbeMs = SystemClock.elapsedRealtime();
            clear();
            long clearMs = SystemClock.elapsedRealtime();
            if (stagedCandidateRows > 0) {
                materializeRootExportFromStagedCandidates(
                        db, hasAllowRules, hasWildcardExactAllowRules, hasRedirectRules,
                        activeGeneration, stagedCandidateRows);
            } else {
                materializeRootExportFromActiveRules(
                        db, hasAllowRules, hasWildcardExactAllowRules, hasRedirectRules,
                        activeGeneration);
            }
            long rootExportMs = SystemClock.elapsedRealtime();
            Timber.i("HostEntryDao.sync skipped materialized runtime cache and rebuilt root " +
                            "export: activeRuleRows=%d maxRows=%d stageCheckMs=%d " +
                            "userStatsMs=%d aggregateStatsMs=%d activeRuleCountMs=%d " +
                            "fallbackStatsMs=%d allowProbeMs=%d wildcardAllowProbeMs=%d " +
                            "redirectProbeMs=%d clearMs=%d rootExportMs=%d totalMs=%d",
                    activeRuleRows,
                    MATERIALIZED_RUNTIME_CACHE_MAX_ROWS,
                    stageCheckMs - startedMs,
                    userStatsMs - stageCheckMs,
                    aggregateStatsMs - userStatsMs,
                    activeRuleCountMs - aggregateStatsMs,
                    fallbackStatsMs - activeRuleCountMs,
                    allowProbeMs - fallbackStatsMs,
                    wildcardAllowProbeMs - allowProbeMs,
                    redirectProbeMs - wildcardAllowProbeMs,
                    clearMs - redirectProbeMs,
                    rootExportMs - clearMs,
                    rootExportMs - startedMs);
            return;
        }
        boolean hasAllowRules = hasUserAllowedRules() || hasSourceAllowedRules(activeGeneration);
        boolean hasWildcardExactAllowRules = hasAllowRules
                && hasActiveWildcardExactAllowedRules(activeGeneration);
        boolean deferRootExportIndex = db != null;
        if (deferRootExportIndex) {
            tuneRuntimeRebuildStorage(db);
        }
        if (deferRootExportIndex) {
            db.execSQL("DROP INDEX IF EXISTS `" + ROOT_EXPORT_INDEX_NAME + "`");
            db.execSQL("DROP INDEX IF EXISTS `" + RUNTIME_SUFFIX_INDEX_NAME + "`");
        }
        long indexDropMs = SystemClock.elapsedRealtime();
        clear();
        long clearMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.sync phase=clear ms=%d", clearMs - indexDropMs);
        importBlockedRules(activeGeneration);
        long blockedMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.sync phase=import-blocked ms=%d",
                blockedMs - clearMs);
        if (deferRootExportIndex) {
            db.execSQL(RUNTIME_SUFFIX_INDEX_SQL);
        }
        long suffixIndexCreateMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.sync phase=suffix-index-create ms=%d",
                suffixIndexCreateMs - blockedMs);
        if (hasAllowRules) {
            deleteExactRowsAllowedByActiveLiteralRules(activeGeneration);
            if (hasWildcardExactAllowRules) {
                deleteExactRowsAllowedByActiveWildcardRules(activeGeneration);
            }
            deleteRowsAllowedByActiveSuffixRules(activeGeneration, db);
        }
        long allowMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.sync phase=allow-filter ms=%d",
                allowMs - suffixIndexCreateMs);
        importRedirectedSources(activeGeneration);
        importRedirectedUser();
        long redirectedMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.sync phase=import-redirected ms=%d",
                redirectedMs - allowMs);
        if (deferRootExportIndex) {
            db.execSQL(ROOT_EXPORT_INDEX_SQL);
        }
        long indexCreateMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.sync phase=index-create ms=%d",
                indexCreateMs - redirectedMs);
        materializeRootExport(db, hasAllowRules, hasWildcardExactAllowRules, activeGeneration);
        long rootExportMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.sync phase=root-export ms=%d",
                rootExportMs - indexCreateMs);
        refreshStatsFromMaterializedRuntime();
        long statsMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.sync phase=stats-refresh ms=%d", statsMs - rootExportMs);
        Timber.i("HostEntryDao.sync perf: allowRules=%s clearMs=%d importBlockedMs=%d " +
                        "suffixIndexCreateMs=%d allowMs=%d importRedirectedMs=%d " +
                        "indexDropMs=%d indexCreateMs=%d rootExportMs=%d statsMs=%d " +
                        "totalMs=%d wildcardExactAllowRules=%s",
                hasAllowRules,
                clearMs - indexDropMs,
                blockedMs - clearMs,
                suffixIndexCreateMs - blockedMs,
                allowMs - suffixIndexCreateMs,
                redirectedMs - allowMs,
                indexDropMs - startedMs,
                indexCreateMs - redirectedMs,
                rootExportMs - indexCreateMs,
                statsMs - rootExportMs,
                statsMs - startedMs,
                hasWildcardExactAllowRules);
    }

    private void importBlockedRules(int activeGeneration) {
        importBlockedUser();
        importBlockedExactSources(activeGeneration);
        importBlockedSuffixSources(activeGeneration);
    }

    default void deleteExactRowsAllowedByActiveSuffixRules(int activeGeneration) {
        deleteExactRowsAllowedByActiveSuffixRules(activeGeneration, null);
    }

    default void deleteExactRowsAllowedByActiveSuffixRules(
            int activeGeneration, @Nullable SupportSQLiteDatabase db) {
        deleteRowsAllowedByActiveSuffixRules(
                activeGeneration,
                EXACT.getValue(),
                db,
                this::deleteExactRowsAllowedByActiveSuffixRulesBatch);
    }

    default void deleteSuffixRowsAllowedByActiveSuffixRules(int activeGeneration) {
        deleteSuffixRowsAllowedByActiveSuffixRules(activeGeneration, null);
    }

    default void deleteSuffixRowsAllowedByActiveSuffixRules(
            int activeGeneration, @Nullable SupportSQLiteDatabase db) {
        deleteRowsAllowedByActiveSuffixRules(
                activeGeneration,
                org.adaway.db.entity.RuleKind.SUFFIX.getValue(),
                db,
                this::deleteSuffixRowsAllowedByActiveSuffixRulesBatch);
    }

    private void deleteRowsAllowedByActiveSuffixRules(
            int activeGeneration,
            int kind,
            @Nullable SupportSQLiteDatabase db,
            SuffixAllowSqlBatchDeleter fallbackSqlBatchDeleter) {
        String afterHost = "";
        int offset = SUFFIX_ALLOW_DELETE_BATCH_SIZE - 1;
        while (true) {
            String upperHost = getHostEntryBatchUpperBound(kind, afterHost, offset);
            fallbackSqlBatchDeleter.delete(activeGeneration, afterHost, upperHost);
            if (upperHost == null) {
                return;
            }
            afterHost = upperHost;
        }
    }

    private void deleteRowsAllowedByActiveSuffixRules(
            int activeGeneration, @Nullable SupportSQLiteDatabase db) {
        if (db != null && db.inTransaction()) {
            deleteRowsAllowedByActiveSuffixRulesWithTempTable(activeGeneration, db);
            return;
        }

        deleteExactRowsAllowedByActiveSuffixRules(activeGeneration);
        deleteSuffixRowsAllowedByActiveSuffixRules(activeGeneration);
    }

    private void deleteRowsAllowedByActiveSuffixRulesWithTempTable(
            int activeGeneration, SupportSQLiteDatabase db) {
        long startedMs = SystemClock.elapsedRealtime();

        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS `" + TEMP_SUFFIX_ALLOW_DELETE_HOSTS +
                "` (`kind` INTEGER NOT NULL, `host` TEXT NOT NULL, " +
                "PRIMARY KEY(`kind`, `host`)) WITHOUT ROWID");
        db.execSQL("DELETE FROM `" + TEMP_SUFFIX_ALLOW_DELETE_HOSTS + "`");

        int exactMatches = insertSuffixAllowedDeleteRows(
                activeGeneration, EXACT.getValue(), db);
        int suffixMatches = insertSuffixAllowedDeleteRows(
                activeGeneration, org.adaway.db.entity.RuleKind.SUFFIX.getValue(), db);

        SupportSQLiteStatement deleteStatement = db.compileStatement(
                "DELETE FROM `host_entries` WHERE `kind` = ? AND `host` IN " +
                        "(SELECT `host` FROM `" + TEMP_SUFFIX_ALLOW_DELETE_HOSTS +
                        "` WHERE `kind` = ?)");
        deleteStatement.bindLong(1, EXACT.getValue());
        deleteStatement.bindLong(2, EXACT.getValue());
        deleteStatement.executeUpdateDelete();
        deleteStatement.clearBindings();
        deleteStatement.bindLong(1, org.adaway.db.entity.RuleKind.SUFFIX.getValue());
        deleteStatement.bindLong(2, org.adaway.db.entity.RuleKind.SUFFIX.getValue());
        deleteStatement.executeUpdateDelete();
        db.execSQL("DELETE FROM `" + TEMP_SUFFIX_ALLOW_DELETE_HOSTS + "`");

        Timber.i("HostEntryDao.sync phase=suffix-allow-index kind=%d matched=%d",
                EXACT.getValue(), exactMatches);
        Timber.i("HostEntryDao.sync phase=suffix-allow-index kind=%d matched=%d",
                org.adaway.db.entity.RuleKind.SUFFIX.getValue(),
                suffixMatches);
        Timber.i("HostEntryDao.sync phase=suffix-allow-delete ms=%d",
                SystemClock.elapsedRealtime() - startedMs);
    }

    private int insertSuffixAllowedDeleteRows(
            int activeGeneration, int kind, SupportSQLiteDatabase db) {
        SupportSQLiteStatement insertStatement = db.compileStatement(
                "INSERT OR IGNORE INTO `" + TEMP_SUFFIX_ALLOW_DELETE_HOSTS +
                        "` (`kind`, `host`) " +
                        "WITH `active_suffix_allow`(`reverse_host`) AS (" +
                        "SELECT `reverse_host` FROM `hosts_lists` " +
                        "INDEXED BY `index_hosts_lists_active_allow_source_kind_reverse_host` " +
                        "WHERE `type` = 1 AND `enabled` = 1 AND `kind` = 1 " +
                        "AND `source_id` = 1 " +
                        "UNION ALL SELECT `reverse_host` FROM `hosts_lists` " +
                        "INDEXED BY " +
                        "`index_hosts_lists_active_allow_generation_kind_reverse_host` " +
                        "WHERE `type` = 1 AND `enabled` = 1 AND `kind` = 1 " +
                        "AND `generation` = ? AND `source_id` != 1) " +
                        "SELECT ?, entry.`host` FROM `active_suffix_allow` AS allowed " +
                        "JOIN `host_entries` AS entry " +
                        "INDEXED BY `index_host_entries_kind_reverse_host` " +
                        "ON entry.`kind` = ? " +
                        "AND entry.`reverse_host` = allowed.`reverse_host` " +
                        "UNION ALL SELECT ?, entry.`host` " +
                        "FROM `active_suffix_allow` AS allowed " +
                        "JOIN `host_entries` AS entry " +
                        "INDEXED BY `index_host_entries_kind_reverse_host` " +
                        "ON entry.`kind` = ? " +
                        "AND entry.`reverse_host` >= allowed.`reverse_host` || '.' " +
                        "AND entry.`reverse_host` < allowed.`reverse_host` || '/'");
        insertStatement.bindLong(1, activeGeneration);
        insertStatement.bindLong(2, kind);
        insertStatement.bindLong(3, kind);
        insertStatement.bindLong(4, kind);
        insertStatement.bindLong(5, kind);
        return insertStatement.executeUpdateDelete();
    }

    private void dropRootExportReverseIndex(SupportSQLiteDatabase db) {
        db.execSQL("DROP INDEX IF EXISTS `" + ROOT_EXPORT_REVERSE_INDEX_NAME + "`");
    }

    private void dropRootExportHostIndex(SupportSQLiteDatabase db) {
        db.execSQL("DROP INDEX IF EXISTS `" + ROOT_EXPORT_HOST_INDEX_NAME + "`");
    }

    private void dropRootExportIndexes(SupportSQLiteDatabase db) {
        dropRootExportReverseIndex(db);
        dropRootExportHostIndex(db);
    }

    private void tuneRuntimeRebuildStorage(@Nullable SupportSQLiteDatabase db) {
        if (db == null) {
            return;
        }
        db.execSQL(RUNTIME_REBUILD_CACHE_SIZE_SQL);
        db.execSQL(RUNTIME_REBUILD_TEMP_STORE_SQL);
    }

    private void insertRootExportBlockedRows(
            SupportSQLiteDatabase db, boolean userRules, int minKind, int maxKind,
            int activeGeneration) {
        String sourceWhere = userRules
                ? "entry.`source_id` = 1"
                : "entry.`generation` = " + activeGeneration + " AND entry.`source_id` != 1";
        String scan = userRules
                ? "INDEXED BY `index_hosts_lists_type_enabled_source_id`"
                : "INDEXED BY `index_hosts_lists_active_generation_kind_host`";
        String kindWhere = minKind == maxKind
                ? "entry.`kind` = " + minKind
                : "entry.`kind` >= " + minKind + " AND entry.`kind` <= " + maxKind;
        db.execSQL("INSERT INTO `root_host_entries` " +
                "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
                "SELECT entry.`host`, entry.`reverse_host`, 0, 0, NULL " +
                "FROM `hosts_lists` AS entry " + scan + " " +
                "WHERE " + kindWhere + " AND entry.`type` = 0 " +
                "AND entry.`enabled` = 1 AND " + sourceWhere);
    }

    private long getCompleteRootExportStageRows(
            SupportSQLiteDatabase db, int activeGeneration) {
        long userCandidateRows = queryLong(db,
                "SELECT COUNT(*) FROM `hosts_lists` WHERE `source_id` = 1 " +
                        "AND `enabled` = 1 AND `type` IN (0, 2)");
        if (userCandidateRows > 0) {
            Timber.i("Root export stage incomplete: userCandidates=%d",
                    userCandidateRows);
            return 0L;
        }
        long stagedRows = queryLong(db,
                "SELECT COUNT(*) FROM `" + ROOT_EXPORT_STAGE_TABLE + "` AS stage " +
                        "JOIN `hosts_sources` AS source ON source.`id` = stage.`source_id` " +
                        "WHERE source.`enabled` = 1 AND stage.`source_id` != 1 " +
                        "AND stage.`generation` = " + activeGeneration);
        if (stagedRows <= 0) {
            return 0L;
        }
        long expectedStageRows = queryLong(db,
                "SELECT COALESCE(SUM(source.`blocked_count` + source.`redirected_count`), 0) " +
                        "FROM `hosts_sources` AS source WHERE source.`enabled` = 1 " +
                        "AND source.`id` != 1");
        if (stagedRows != expectedStageRows) {
            Timber.i("Root export stage incomplete: stagedRows=%d expectedStageRows=%d",
                    stagedRows, expectedStageRows);
            return 0L;
        }
        long enabledExternalSources = queryLong(db,
                "SELECT COUNT(*) FROM `hosts_sources` AS source " +
                        "WHERE source.`enabled` = 1 AND source.`id` != 1");
        if (enabledExternalSources <= 1) {
            return stagedRows;
        }
        long mismatchSources = queryLong(db,
                "SELECT COUNT(*) FROM `hosts_sources` AS source " +
                        "WHERE source.`enabled` = 1 AND source.`id` != 1 " +
                        "AND (source.`blocked_count` + source.`redirected_count`) != " +
                        "(SELECT COUNT(*) FROM `" + ROOT_EXPORT_STAGE_TABLE + "` AS stage " +
                        "INDEXED BY `" + ROOT_EXPORT_STAGE_SOURCE_GENERATION_INDEX_NAME + "` " +
                        "WHERE stage.`source_id` = source.`id` " +
                        "AND stage.`generation` = " + activeGeneration + ")");
        if (mismatchSources > 0) {
            Timber.i("Root export stage incomplete: perSourceMismatches=%d", mismatchSources);
            return 0L;
        }
        return stagedRows;
    }

    private long queryLong(SupportSQLiteDatabase db, String sql) {
        try (Cursor cursor = db.query(sql)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    private void insertRootExportStagedBlockedRows(
            SupportSQLiteDatabase db, boolean userRules, int activeGeneration) {
        String sourceWhere = userRules
                ? "entry.`source_id` = 1"
                : "entry.`generation` = " + activeGeneration + " AND entry.`source_id` != 1 " +
                        "AND EXISTS (SELECT 1 FROM `hosts_sources` AS source " +
                        "WHERE source.`id` = entry.`source_id` AND source.`enabled` = 1)";
        String scan = userRules
                ? "INDEXED BY `" + ROOT_EXPORT_STAGE_SOURCE_GENERATION_INDEX_NAME + "`"
                : "NOT INDEXED";
        db.execSQL("INSERT INTO `root_host_entries` " +
                "(`id`, `host`, `reverse_host`, `kind`, `type`, `redirection`) " +
                "SELECT entry.`id`, entry.`host`, '', 0, 0, NULL " +
                "FROM `" + ROOT_EXPORT_STAGE_TABLE + "` AS entry " + scan + " " +
                "WHERE entry.`type` = 0 AND " + sourceWhere);
    }

    private void prepareRootExportSkippedStageRows(
            SupportSQLiteDatabase db, int activeGeneration) {
        long startedMs = SystemClock.elapsedRealtime();
        createRootExportSkippedStageRowsTable(db);
        db.execSQL("WITH `active_exact_allow`(`reverse_host`) AS (" +
                "SELECT `reverse_host` FROM `hosts_lists` " +
                "INDEXED BY `index_hosts_lists_active_allow_source_kind_reverse_host` " +
                "WHERE `type` = 1 AND `enabled` = 1 AND `kind` = 0 " +
                "AND `source_id` = 1 " +
                "AND instr(`host`, '*') = 0 AND instr(`host`, '?') = 0 " +
                "AND instr(`host`, '%') = 0 " +
                "UNION ALL SELECT `reverse_host` FROM `hosts_lists` " +
                "INDEXED BY `index_hosts_lists_active_allow_generation_kind_reverse_host` " +
                "WHERE `type` = 1 AND `enabled` = 1 AND `kind` = 0 " +
                "AND `generation` = " + activeGeneration + " AND `source_id` != 1 " +
                "AND instr(`host`, '*') = 0 AND instr(`host`, '?') = 0 " +
                "AND instr(`host`, '%') = 0) " +
                "INSERT OR IGNORE INTO `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "` " +
                "SELECT entry.`id` FROM `active_exact_allow` AS allowed " +
                "JOIN `" + ROOT_EXPORT_STAGE_TABLE + "` AS entry " +
                "INDEXED BY `" + ROOT_EXPORT_STAGE_REVERSE_HOST_INDEX_NAME + "` " +
                "ON entry.`reverse_host` = allowed.`reverse_host` " +
                "WHERE entry.`type` = 0 AND " + activeStageSourceWhere(activeGeneration));
        long exactMs = SystemClock.elapsedRealtime();
        db.execSQL("WITH `active_suffix_allow`(`reverse_host`) AS (" +
                "SELECT `reverse_host` FROM `hosts_lists` " +
                "INDEXED BY `index_hosts_lists_active_allow_source_kind_reverse_host` " +
                "WHERE `type` = 1 AND `enabled` = 1 AND `kind` = 1 " +
                "AND `source_id` = 1 " +
                "UNION ALL SELECT `reverse_host` FROM `hosts_lists` " +
                "INDEXED BY `index_hosts_lists_active_allow_generation_kind_reverse_host` " +
                "WHERE `type` = 1 AND `enabled` = 1 AND `kind` = 1 " +
                "AND `generation` = " + activeGeneration + " AND `source_id` != 1) " +
                "INSERT OR IGNORE INTO `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "` " +
                "SELECT entry.`id` FROM `active_suffix_allow` AS allowed " +
                "JOIN `" + ROOT_EXPORT_STAGE_TABLE + "` AS entry " +
                "INDEXED BY `" + ROOT_EXPORT_STAGE_REVERSE_HOST_INDEX_NAME + "` " +
                "ON entry.`reverse_host` = allowed.`reverse_host` " +
                "WHERE entry.`type` = 0 AND " + activeStageSourceWhere(activeGeneration) +
                " UNION ALL SELECT entry.`id` " +
                "FROM `active_suffix_allow` AS allowed " +
                "JOIN `" + ROOT_EXPORT_STAGE_TABLE + "` AS entry " +
                "INDEXED BY `" + ROOT_EXPORT_STAGE_REVERSE_HOST_INDEX_NAME + "` " +
                "ON entry.`reverse_host` >= allowed.`reverse_host` || '.' " +
                "AND entry.`reverse_host` < allowed.`reverse_host` || '/' " +
                "WHERE entry.`type` = 0 AND " + activeStageSourceWhere(activeGeneration));
        long suffixMs = SystemClock.elapsedRealtime();
        long skippedRows = queryLong(db,
                "SELECT COUNT(*) FROM `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "`");
        Timber.i("HostEntryDao.root-export-stage allow-skip exactMs=%d suffixMs=%d " +
                        "skippedRows=%d totalMs=%d",
                exactMs - startedMs,
                suffixMs - exactMs,
                skippedRows,
                suffixMs - startedMs);
    }

    private void createRootExportSkippedStageRowsTable(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS +
                "` (`id` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("DELETE FROM `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "`");
    }

    private String activeStageSourceWhere(int activeGeneration) {
        return activeStageSourceWhere("entry", activeGeneration);
    }

    private String activeStageSourceWhere(String alias, int activeGeneration) {
        return "(" + alias + ".`source_id` = 1 OR (" + alias + ".`generation` = " +
                activeGeneration + " AND " + alias + ".`source_id` != 1 AND EXISTS (" +
                "SELECT 1 FROM `hosts_sources` AS source WHERE source.`id` = " + alias +
                ".`source_id` AND source.`enabled` = 1)))";
    }

    private void prepareRootExportRedirectSkippedStageRows(
            SupportSQLiteDatabase db, int activeGeneration) {
        long startedMs = SystemClock.elapsedRealtime();
        long existingSkippedRows = queryLong(db,
                "SELECT COUNT(*) FROM `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "`");
        db.execSQL("CREATE TABLE IF NOT EXISTS `" +
                TEMP_ROOT_EXPORT_REDIRECT_STAGE_CONFLICT_HOSTS + "` (" +
                "`reverse_host` TEXT NOT NULL, PRIMARY KEY(`reverse_host`)) WITHOUT ROWID");
        db.execSQL("DELETE FROM `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_CONFLICT_HOSTS + "`");
        db.execSQL("INSERT INTO `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_CONFLICT_HOSTS + "` " +
                "(`reverse_host`) SELECT entry.`reverse_host` FROM `" +
                ROOT_EXPORT_STAGE_TABLE + "` AS entry INDEXED BY `" +
                ROOT_EXPORT_STAGE_REVERSE_HOST_INDEX_NAME + "` " +
                "WHERE entry.`type` IN (0, 2) AND " +
                activeStageSourceWhere(activeGeneration) + " GROUP BY entry.`reverse_host` " +
                "HAVING COUNT(*) > 1 AND SUM(CASE WHEN entry.`type` = 2 " +
                "THEN 1 ELSE 0 END) > 0");
        long conflictMs = SystemClock.elapsedRealtime();
        long conflictHostRows = queryLong(db,
                "SELECT COUNT(*) FROM `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_CONFLICT_HOSTS +
                        "`");
        if (conflictHostRows == 0) {
            db.execSQL("DROP TABLE `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_CONFLICT_HOSTS + "`");
            long finishedMs = SystemClock.elapsedRealtime();
            Timber.i("HostEntryDao.root-export-stage redirect-skip conflicts=0 " +
                            "conflictMs=%d cleanupMs=%d totalMs=%d",
                    conflictMs - startedMs,
                    finishedMs - conflictMs,
                    finishedMs - startedMs);
            return;
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS `" +
                TEMP_ROOT_EXPORT_REDIRECT_STAGE_CANDIDATES + "` (" +
                "`host` TEXT NOT NULL, `id` INTEGER NOT NULL, " +
                "`source_priority` INTEGER NOT NULL, `source_id` INTEGER NOT NULL, " +
                "`redirection` TEXT NOT NULL, PRIMARY KEY(`host`, `source_priority`, " +
                "`source_id`, `redirection`, `id`)) WITHOUT ROWID");
        db.execSQL("DELETE FROM `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_CANDIDATES + "`");
        db.execSQL("INSERT INTO `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_CANDIDATES + "` " +
                "(`host`, `id`, `source_priority`, `source_id`, `redirection`) " +
                "SELECT entry.`host`, entry.`id`, " +
                "CASE WHEN entry.`source_id` = 1 THEN 0 ELSE 1 END, " +
                "entry.`source_id`, COALESCE(entry.`redirection`, '') " +
                "FROM `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_CONFLICT_HOSTS +
                "` AS conflict JOIN `" + ROOT_EXPORT_STAGE_TABLE +
                "` AS entry INDEXED BY `" + ROOT_EXPORT_STAGE_REVERSE_HOST_INDEX_NAME + "` " +
                "ON entry.`reverse_host` = conflict.`reverse_host` " +
                "WHERE entry.`type` = 2 AND " + activeStageSourceWhere(activeGeneration));
        long candidateMs = SystemClock.elapsedRealtime();
        long redirectCandidateRows = queryLong(db,
                "SELECT COUNT(*) FROM `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_CANDIDATES + "`");
        db.execSQL("CREATE TABLE IF NOT EXISTS `" +
                TEMP_ROOT_EXPORT_REDIRECT_STAGE_WINNER_IDS + "` (" +
                "`id` INTEGER NOT NULL, `host` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("DELETE FROM `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_WINNER_IDS + "`");
        db.execSQL("INSERT INTO `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_WINNER_IDS + "` " +
                "(`id`, `host`) SELECT candidate.`id`, candidate.`host` " +
                "FROM `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_CANDIDATES +
                "` AS candidate WHERE NOT EXISTS (" +
                "SELECT 1 FROM `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_CANDIDATES +
                "` AS better WHERE better.`host` = candidate.`host` AND (" +
                "better.`source_priority` < candidate.`source_priority` OR " +
                "(better.`source_priority` = candidate.`source_priority` " +
                "AND better.`source_id` < candidate.`source_id`) OR " +
                "(better.`source_priority` = candidate.`source_priority` " +
                "AND better.`source_id` = candidate.`source_id` " +
                "AND better.`redirection` < candidate.`redirection`) OR " +
                "(better.`source_priority` = candidate.`source_priority` " +
                "AND better.`source_id` = candidate.`source_id` " +
                "AND better.`redirection` = candidate.`redirection` " +
                "AND better.`id` < candidate.`id`)))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `" +
                TEMP_ROOT_EXPORT_REDIRECT_STAGE_WINNER_HOST_INDEX + "` ON `" +
                TEMP_ROOT_EXPORT_REDIRECT_STAGE_WINNER_IDS + "` (`host`)");
        long winnerMs = SystemClock.elapsedRealtime();
        long redirectWinnerRows = queryLong(db,
                "SELECT COUNT(*) FROM `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_WINNER_IDS + "`");
        if (redirectCandidateRows != redirectWinnerRows) {
            db.execSQL("INSERT OR IGNORE INTO `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "` " +
                    "SELECT candidate.`id` FROM `" +
                    TEMP_ROOT_EXPORT_REDIRECT_STAGE_CANDIDATES + "` AS candidate " +
                    "WHERE NOT EXISTS (SELECT 1 FROM `" +
                    TEMP_ROOT_EXPORT_REDIRECT_STAGE_WINNER_IDS + "` AS winner " +
                    "WHERE winner.`id` = candidate.`id`)");
        }
        long loserMs = SystemClock.elapsedRealtime();
        db.execSQL("INSERT OR IGNORE INTO `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "` " +
                "SELECT entry.`id` FROM `" +
                TEMP_ROOT_EXPORT_REDIRECT_STAGE_CONFLICT_HOSTS + "` AS conflict " +
                "JOIN `" + ROOT_EXPORT_STAGE_TABLE + "` AS entry INDEXED BY `" +
                ROOT_EXPORT_STAGE_REVERSE_HOST_INDEX_NAME + "` " +
                "ON entry.`reverse_host` = conflict.`reverse_host` " +
                "WHERE entry.`type` = 0 AND " + activeStageSourceWhere(activeGeneration) +
                " AND EXISTS (" +
                "SELECT 1 FROM `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_WINNER_IDS +
                "` AS winner WHERE winner.`host` = entry.`host`)");
        long shadowMs = SystemClock.elapsedRealtime();
        long totalSkippedRows = queryLong(db,
                "SELECT COUNT(*) FROM `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "`");
        db.execSQL("DROP INDEX IF EXISTS `" +
                TEMP_ROOT_EXPORT_REDIRECT_STAGE_WINNER_HOST_INDEX + "`");
        db.execSQL("DROP TABLE `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_WINNER_IDS + "`");
        db.execSQL("DROP TABLE `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_CANDIDATES + "`");
        db.execSQL("DROP TABLE `" + TEMP_ROOT_EXPORT_REDIRECT_STAGE_CONFLICT_HOSTS + "`");
        long finishedMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.root-export-stage redirect-skip conflicts=%d " +
                        "candidates=%d winners=%d addedSkippedRows=%d " +
                        "totalSkippedRows=%d conflictMs=%d candidateMs=%d winnerMs=%d " +
                        "loserMs=%d shadowMs=%d cleanupMs=%d totalMs=%d",
                conflictHostRows,
                redirectCandidateRows,
                redirectWinnerRows,
                totalSkippedRows - existingSkippedRows,
                totalSkippedRows,
                conflictMs - startedMs,
                candidateMs - conflictMs,
                winnerMs - candidateMs,
                loserMs - winnerMs,
                shadowMs - loserMs,
                finishedMs - shadowMs,
                finishedMs - startedMs);
    }

    private void prepareRootExportDuplicateSkippedStageRows(
            SupportSQLiteDatabase db, int activeGeneration) {
        long startedMs = SystemClock.elapsedRealtime();
        long existingSkippedRows = queryLong(db,
                "SELECT COUNT(*) FROM `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "`");
        db.execSQL("INSERT OR IGNORE INTO `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "` " +
                "SELECT entry.`id` FROM `" + ROOT_EXPORT_STAGE_TABLE + "` AS entry " +
                "INDEXED BY `" + ROOT_EXPORT_STAGE_REVERSE_HOST_INDEX_NAME + "` " +
                "WHERE entry.`type` = 0 AND " + activeStageSourceWhere(activeGeneration) +
                " AND EXISTS (SELECT 1 FROM `" + ROOT_EXPORT_STAGE_TABLE + "` AS better " +
                "INDEXED BY `" + ROOT_EXPORT_STAGE_REVERSE_HOST_INDEX_NAME + "` " +
                "WHERE better.`reverse_host` = entry.`reverse_host` " +
                "AND better.`type` = 0 AND better.`id` < entry.`id` AND " +
                activeStageSourceWhere("better", activeGeneration) + ")");
        long totalSkippedRows = queryLong(db,
                "SELECT COUNT(*) FROM `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "`");
        long finishedMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.root-export-stage duplicate-skip addedSkippedRows=%d " +
                        "totalSkippedRows=%d totalMs=%d",
                totalSkippedRows - existingSkippedRows,
                totalSkippedRows,
                finishedMs - startedMs);
    }

    private void clearRootExportSkippedStageRows(SupportSQLiteDatabase db) {
        db.execSQL("DELETE FROM `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "`");
    }

    private void deleteRootExportSkippedStageRows(SupportSQLiteDatabase db) {
        if (!tableExists(db, TEMP_ROOT_EXPORT_SKIP_STAGE_IDS)) {
            return;
        }
        long startedMs = SystemClock.elapsedRealtime();
        SupportSQLiteStatement deleteStatement = db.compileStatement(
                "DELETE FROM `root_host_entries` WHERE `id` IN (" +
                        "SELECT `id` FROM `" + TEMP_ROOT_EXPORT_SKIP_STAGE_IDS + "`)");
        int deletedRows = deleteStatement.executeUpdateDelete();
        long finishedMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.root-export-stage skip-delete rows=%d totalMs=%d",
                deletedRows,
                finishedMs - startedMs);
    }

    private boolean tableExists(SupportSQLiteDatabase db, String tableName) {
        return queryLong(db,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' " +
                        "AND name = '" + tableName + "'") > 0;
    }

    private void deleteRootExportRowsAllowedByLiteralExactRules(
            SupportSQLiteDatabase db, int activeGeneration) {
        db.execSQL("DELETE FROM `root_host_entries` WHERE `host` IN (" +
                "SELECT LOWER(allowed.`host`) FROM `hosts_lists` AS allowed " +
                "WHERE allowed.`type` = 1 AND allowed.`enabled` = 1 " +
                "AND allowed.`kind` = 0 " +
                "AND (allowed.`source_id` = 1 OR (allowed.`generation` = " +
                activeGeneration + " AND allowed.`source_id` != 1)) " +
                "AND instr(allowed.`host`, '*') = 0 AND instr(allowed.`host`, '?') = 0 " +
                "AND instr(allowed.`host`, '%') = 0)");
    }

    private void deleteRootExportRowsAllowedByWildcardExactRules(
            SupportSQLiteDatabase db, int activeGeneration) {
        db.execSQL("DELETE FROM `root_host_entries` WHERE EXISTS (" +
                "SELECT 1 FROM `hosts_lists` AS allowed WHERE allowed.`type` = 1 " +
                "AND allowed.`enabled` = 1 AND allowed.`kind` = 0 " +
                "AND (allowed.`source_id` = 1 OR (allowed.`generation` = " +
                activeGeneration + " AND allowed.`source_id` != 1)) " +
                "AND (instr(allowed.`host`, '*') > 0 OR instr(allowed.`host`, '?') > 0 " +
                "OR instr(allowed.`host`, '%') > 0) " +
                "AND `root_host_entries`.`host` LIKE " +
                "REPLACE(REPLACE(LOWER(allowed.`host`), '*', '%'), '?', '_') LIMIT 1)");
    }

    private void deleteRootExportRowsAllowedBySuffixRules(
            SupportSQLiteDatabase db, int activeGeneration) {
        long startedMs = SystemClock.elapsedRealtime();
        db.execSQL(ROOT_EXPORT_REVERSE_LOOKUP_INDEX_SQL);
        long indexMs = SystemClock.elapsedRealtime();
        db.execSQL("WITH `active_suffix_allow`(`reverse_host`) AS (" +
                "SELECT `reverse_host` FROM `hosts_lists` " +
                "INDEXED BY `index_hosts_lists_active_allow_source_kind_reverse_host` " +
                "WHERE `type` = 1 AND `enabled` = 1 AND `kind` = 1 " +
                "AND `source_id` = 1 " +
                "UNION ALL SELECT `reverse_host` FROM `hosts_lists` " +
                "INDEXED BY `index_hosts_lists_active_allow_generation_kind_reverse_host` " +
                "WHERE `type` = 1 AND `enabled` = 1 AND `kind` = 1 " +
                "AND `generation` = " + activeGeneration + " AND `source_id` != 1) " +
                "DELETE FROM `root_host_entries` WHERE `id` IN (" +
                "SELECT entry.`id` FROM `active_suffix_allow` AS allowed " +
                "JOIN `root_host_entries` AS entry " +
                "INDEXED BY `" + ROOT_EXPORT_REVERSE_INDEX_NAME + "` " +
                "ON entry.`reverse_host` = allowed.`reverse_host` " +
                "UNION ALL SELECT entry.`id` " +
                "FROM `active_suffix_allow` AS allowed " +
                "JOIN `root_host_entries` AS entry " +
                "INDEXED BY `" + ROOT_EXPORT_REVERSE_INDEX_NAME + "` " +
                "ON entry.`reverse_host` >= allowed.`reverse_host` || '.' " +
                "AND entry.`reverse_host` < allowed.`reverse_host` || '/')");
        long deleteMs = SystemClock.elapsedRealtime();
        dropRootExportReverseIndex(db);
        long dropMs = SystemClock.elapsedRealtime();
        Timber.i("HostEntryDao.root-export suffix-allow-delete reverseIndexMs=%d " +
                        "deleteMs=%d dropIndexMs=%d totalMs=%d",
                indexMs - startedMs,
                deleteMs - indexMs,
                dropMs - deleteMs,
                dropMs - startedMs);
    }

    private void dedupeRootExportRows(SupportSQLiteDatabase db, boolean hasRedirectRules) {
        if (hasRedirectRules) {
            dedupeRootExportRowsByPrecedence(db);
        } else if (shouldDedupeRootExportBlockedRows(db)
                && hasDuplicateRootExportBlockedRows(db)) {
            dedupeRootExportBlockedRowsByFirstId(db);
        }
    }

    private boolean shouldDedupeRootExportBlockedRows(SupportSQLiteDatabase db) {
        long rootRows = queryLong(db, "SELECT COUNT(*) FROM `root_host_entries`");
        if (rootRows > MATERIALIZED_RUNTIME_CACHE_MAX_ROWS) {
            Timber.i("HostEntryDao.root-export skipped duplicate cleanup: rootRows=%d " +
                    "maxRows=%d redirects=false", rootRows, MATERIALIZED_RUNTIME_CACHE_MAX_ROWS);
            return false;
        }
        return true;
    }

    private boolean hasDuplicateRootExportBlockedRows(SupportSQLiteDatabase db) {
        return queryLong(db,
                "SELECT EXISTS(SELECT 1 FROM (" +
                        "SELECT `host` FROM `root_host_entries` " +
                        "GROUP BY `host` HAVING COUNT(*) > 1 LIMIT 1))") > 0;
    }

    private void dedupeRootExportBlockedRowsByFirstId(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS `root_export_keep_ids` " +
                "(`id` INTEGER PRIMARY KEY) WITHOUT ROWID");
        db.execSQL("DELETE FROM `root_export_keep_ids`");
        db.execSQL("INSERT INTO `root_export_keep_ids` " +
                "SELECT MIN(`id`) FROM `root_host_entries` GROUP BY `host`");
        db.execSQL("DELETE FROM `root_host_entries` WHERE NOT EXISTS (" +
                "SELECT 1 FROM `root_export_keep_ids` AS keep " +
                "WHERE keep.`id` = `root_host_entries`.`id`)");
        db.execSQL("DROP TABLE `root_export_keep_ids`");
    }

    private void dedupeRootExportRowsByPrecedence(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS `root_export_keep_ids` " +
                "(`id` INTEGER PRIMARY KEY) WITHOUT ROWID");
        db.execSQL("DELETE FROM `root_export_keep_ids`");
        db.execSQL("WITH `ranked` AS (" +
                "SELECT `id`, `host`, CASE WHEN `type` = 2 THEN 0 ELSE 1 END AS `precedence` " +
                "FROM `root_host_entries`), " +
                "`best_precedence` AS (" +
                "SELECT `host`, MIN(`precedence`) AS `precedence` " +
                "FROM `ranked` GROUP BY `host`) " +
                "INSERT INTO `root_export_keep_ids` " +
                "SELECT MIN(ranked.`id`) FROM `ranked` " +
                "JOIN `best_precedence` AS best ON best.`host` = ranked.`host` " +
                "AND best.`precedence` = ranked.`precedence` GROUP BY ranked.`host`");
        db.execSQL("DELETE FROM `root_host_entries` WHERE NOT EXISTS (" +
                "SELECT 1 FROM `root_export_keep_ids` AS keep " +
                "WHERE keep.`id` = `root_host_entries`.`id`)");
        db.execSQL("DROP TABLE `root_export_keep_ids`");
    }

    private void insertRootExportRedirectedRows(
            SupportSQLiteDatabase db, boolean userRules, int activeGeneration) {
        String sourceWhere = userRules
                ? "entry.`source_id` = 1"
                : "entry.`generation` = " + activeGeneration + " AND entry.`source_id` != 1 " +
                        "AND NOT EXISTS (SELECT 1 FROM `hosts_lists` AS user_entry " +
                        "WHERE user_entry.`host` = entry.`host` " +
                        "AND user_entry.`type` = 2 AND user_entry.`enabled` = 1 " +
                        "AND user_entry.`source_id` = 1)";
        db.execSQL("INSERT INTO `root_host_entries` " +
                "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
                "SELECT entry.`host`, entry.`reverse_host`, 0, 2, entry.`redirection` " +
                "FROM `hosts_lists` AS entry " +
                "WHERE entry.`type` = 2 AND entry.`enabled` = 1 AND " + sourceWhere + " " +
                "ORDER BY entry.`host`, CASE WHEN entry.`source_id` = 1 THEN 0 ELSE 1 END, " +
                "entry.`source_id`, COALESCE(entry.`redirection`, '')");
    }

    private void insertRootExportStagedRedirectedRows(
            SupportSQLiteDatabase db, boolean userRules, int activeGeneration) {
        String sourceWhere = userRules
                ? "entry.`source_id` = 1"
                : "entry.`generation` = " + activeGeneration + " AND entry.`source_id` != 1 " +
                        "AND EXISTS (SELECT 1 FROM `hosts_sources` AS source " +
                        "WHERE source.`id` = entry.`source_id` AND source.`enabled` = 1) " +
                        "AND NOT EXISTS (SELECT 1 FROM `" + ROOT_EXPORT_STAGE_TABLE +
                        "` AS user_entry WHERE user_entry.`host` = entry.`host` " +
                        "AND user_entry.`type` = 2 AND user_entry.`source_id` = 1)";
        db.execSQL("INSERT INTO `root_host_entries` " +
                "(`id`, `host`, `reverse_host`, `kind`, `type`, `redirection`) " +
                "SELECT entry.`id`, entry.`host`, '', 0, 2, entry.`redirection` " +
                "FROM `" + ROOT_EXPORT_STAGE_TABLE + "` AS entry " +
                "WHERE entry.`type` = 2 AND " + sourceWhere + " " +
                "ORDER BY entry.`host`, CASE WHEN entry.`source_id` = 1 THEN 0 ELSE 1 END, " +
                "entry.`source_id`, COALESCE(entry.`redirection`, '')");
    }

    @FunctionalInterface
    interface SuffixAllowSqlBatchDeleter {
        int delete(int activeGeneration, String afterHost, @Nullable String upperHost);
    }

    /**
     * Synchronize the host entries based on the current hosts lists table records.
     * Wrapped in @Transaction to ensure all operations commit atomically,
     * dramatically reducing disk I/O overhead.
     */
    @Transaction
    default void sync() {
        rebuildFromActiveGeneration();
    }

    @Query("SELECT * FROM `host_entries` ORDER BY `host`, `kind`")
    List<HostEntry> getAll();

    @Query("SELECT * FROM `host_entries` WHERE `kind` = 0 ORDER BY `host`")
    List<HostEntry> getAllExact();

    @Query("SELECT `host`, `reverse_host`, `kind`, `type`, `redirection` " +
            "FROM `root_host_entries` " +
            "ORDER BY `host`, `kind`")
    List<HostEntry> getAllForRootHostsFileMaterialized();

    @Query("SELECT `entry`.`host`, '' AS `reverse_host`, 0 AS `kind`, `entry`.`type`, " +
            "`entry`.`redirection` FROM `root_host_entries_stage` AS `entry` NOT INDEXED " +
            "WHERE `entry`.`type` IN (0, 2) AND " + ROOT_STAGE_MATERIALIZED_SOURCE_WHERE +
            " AND " + ROOT_STAGE_SKIP_WHERE + " ORDER BY `entry`.`host`")
    List<HostEntry> getAllForRootHostsFileStageMaterialized(int activeGeneration);

    @Query("SELECT `host`, `type`, `redirection` FROM `root_host_entries`")
    Cursor getRootHostsFileCursorMaterialized();

    @Query("SELECT `entry`.`host`, `entry`.`type`, `entry`.`redirection` " +
            "FROM `root_host_entries_stage` AS `entry` NOT INDEXED " +
            "WHERE `entry`.`type` IN (0, 2) AND " + ROOT_STAGE_MATERIALIZED_SOURCE_WHERE +
            " AND " + ROOT_STAGE_SKIP_WHERE + " ORDER BY `entry`.`id`")
    Cursor getRootHostsFileCursorStageMaterialized(int activeGeneration);

    @Query("SELECT GROUP_CONCAT(`line`, :lineSeparator) AS `lines`, " +
            "MAX(`id`) AS `last_id`, COUNT(*) AS `row_count` FROM (" +
            "SELECT `id`, CASE WHEN `type` = 2 THEN COALESCE(`redirection`, '') " +
            "ELSE :redirectionIpv4 END || ' ' || `host` AS `line` " +
            "FROM `root_host_entries` WHERE `id` > :afterId ORDER BY `id` LIMIT :limit)")
    Cursor getRootHostsFileChunkCursorMaterialized(
            String redirectionIpv4, String lineSeparator, long afterId, int limit);

    @Query("SELECT GROUP_CONCAT(`line`, :lineSeparator) AS `lines`, " +
            "MAX(`id`) AS `last_id`, COUNT(*) AS `row_count` FROM (" +
            "SELECT `entry`.`id`, CASE WHEN `entry`.`type` = 2 THEN " +
            "COALESCE(`entry`.`redirection`, '') ELSE :redirectionIpv4 END || ' ' || " +
            "`entry`.`host` AS `line` FROM `root_host_entries_stage` AS `entry` NOT INDEXED " +
            "WHERE `entry`.`id` > :afterId AND `entry`.`type` IN (0, 2) AND " +
            ROOT_STAGE_MATERIALIZED_SOURCE_WHERE + " AND " + ROOT_STAGE_SKIP_WHERE +
            " ORDER BY `entry`.`id` LIMIT :limit)")
    Cursor getRootHostsFileChunkCursorStageMaterialized(
            String redirectionIpv4, String lineSeparator, long afterId, int limit,
            int activeGeneration);

    @Query("SELECT GROUP_CONCAT(`line`, :lineSeparator) AS `lines`, " +
            "MAX(`id`) AS `last_id`, COUNT(*) AS `row_count` FROM (" +
            "SELECT `id`, CASE WHEN `type` = 2 THEN COALESCE(`redirection`, '') || ' ' || " +
            "`host` ELSE :redirectionIpv4 || ' ' || `host` || :lineSeparator || " +
            ":redirectionIpv6 || ' ' || `host` END AS `line` " +
            "FROM `root_host_entries` WHERE `id` > :afterId ORDER BY `id` LIMIT :limit)")
    Cursor getRootHostsFileChunkCursorMaterializedIpv6(
            String redirectionIpv4, String redirectionIpv6, String lineSeparator, long afterId,
            int limit);

    @Query("SELECT GROUP_CONCAT(`line`, :lineSeparator) AS `lines`, " +
            "MAX(`id`) AS `last_id`, COUNT(*) AS `row_count` FROM (" +
            "SELECT `entry`.`id`, CASE WHEN `entry`.`type` = 2 THEN " +
            "COALESCE(`entry`.`redirection`, '') || ' ' || `entry`.`host` ELSE " +
            ":redirectionIpv4 || ' ' || `entry`.`host` || :lineSeparator || " +
            ":redirectionIpv6 || ' ' || `entry`.`host` END AS `line` " +
            "FROM `root_host_entries_stage` AS `entry` NOT INDEXED " +
            "WHERE `entry`.`id` > :afterId AND `entry`.`type` IN (0, 2) AND " +
            ROOT_STAGE_MATERIALIZED_SOURCE_WHERE +
            " AND " + ROOT_STAGE_SKIP_WHERE +
            " ORDER BY `entry`.`id` LIMIT :limit)")
    Cursor getRootHostsFileChunkCursorStageMaterializedIpv6(
            String redirectionIpv4, String redirectionIpv6, String lineSeparator, long afterId,
            int limit, int activeGeneration);

    @Query(ROOT_EXPORT_ACTIVE_QUERY)
    List<HostEntry> getAllForRootHostsFileActive();

    @Query(ROOT_EXPORT_ACTIVE_QUERY)
    Cursor getRootHostsFileCursorActive();

    @Query(ROOT_EXPORT_ACTIVE_CANDIDATE_QUERY)
    Cursor getRootHostsFileCandidateCursorActive(int activeGeneration);

    @Query("SELECT LOWER(`host`) AS `host`, `type`, `redirection` FROM `hosts_lists` " +
            "WHERE `type` = 2 AND `enabled` = 1 AND `source_id` = 1")
    Cursor getRootRedirectedUserCursorActive();

    @Query("SELECT LOWER(`host`) AS `host`, `type`, `redirection` FROM `hosts_lists` " +
            "WHERE `type` = 2 AND `enabled` = 1 " +
            "AND `generation` = :activeGeneration AND `source_id` != 1 " +
            "ORDER BY `source_id` ASC")
    Cursor getRootRedirectedSourceCursorActive(int activeGeneration);

    @Query("SELECT LOWER(`host`) AS `host`, `type`, `redirection` FROM `hosts_lists` " +
            "INDEXED BY `index_hosts_lists_type_enabled_source_id` " +
            "WHERE `type` = 0 AND `kind` = 0 AND `enabled` = 1 AND `source_id` = 1")
    Cursor getRootExactBlockedUserCursorActive();

    @Query("SELECT LOWER(`host`) AS `host`, `type`, `redirection` FROM `hosts_lists` " +
            "INDEXED BY `index_hosts_lists_active_generation_kind_host` " +
            "WHERE `type` = 0 AND `kind` = 0 AND `enabled` = 1 " +
            "AND `generation` = :activeGeneration AND `source_id` != 1")
    Cursor getRootExactBlockedSourceCursorActive(int activeGeneration);

    @Query("SELECT LOWER(`entry`.`host`) AS `host`, `entry`.`type`, `entry`.`redirection` " +
            "FROM `hosts_lists` AS `entry` " +
            "INDEXED BY `index_hosts_lists_type_enabled_source_id` " +
            "WHERE `entry`.`type` = 0 AND `entry`.`kind` = 1 " +
            "AND `entry`.`enabled` = 1 AND `entry`.`source_id` = 1 " +
            ACTIVE_EXACT_RULE_NOT_EXISTS)
    Cursor getRootSuffixBlockedUserCursorActive(int activeGeneration);

    @Query("SELECT LOWER(`entry`.`host`) AS `host`, `entry`.`type`, `entry`.`redirection` " +
            "FROM `hosts_lists` AS `entry` " +
            "INDEXED BY `index_hosts_lists_active_generation_kind_host` " +
            "WHERE `entry`.`type` = 0 AND `entry`.`kind` = 1 " +
            "AND `entry`.`enabled` = 1 " +
            "AND `entry`.`generation` = :activeGeneration AND `entry`.`source_id` != 1 " +
            ACTIVE_EXACT_RULE_NOT_EXISTS)
    Cursor getRootSuffixBlockedSourceCursorActive(int activeGeneration);

    @Query("SELECT LOWER(`host`) FROM `hosts_lists` WHERE `type` = 1 AND `kind` = 0 " +
            "AND " + ACTIVE_RULES_WITH_GENERATION_PARAM + " " +
            "AND instr(`host`, '*') = 0 AND instr(`host`, '?') = 0 " +
            "AND instr(`host`, '%') = 0")
    List<String> getActiveLiteralExactAllowedHosts(int activeGeneration);

    @Query("SELECT LOWER(`host`) FROM `hosts_lists` WHERE `type` = 1 AND `kind` = 0 " +
            "AND " + ACTIVE_RULES_WITH_GENERATION_PARAM + " " +
            "AND (instr(`host`, '*') > 0 OR instr(`host`, '?') > 0 " +
            "OR instr(`host`, '%') > 0)")
    List<String> getActiveWildcardExactAllowedHosts(int activeGeneration);

    @Query("SELECT LOWER(`host`) FROM `hosts_lists` WHERE `type` = 1 AND `kind` = 1 " +
            "AND " + ACTIVE_RULES_WITH_GENERATION_PARAM)
    List<String> getActiveSuffixAllowedHosts(int activeGeneration);

    @Query("SELECT `root_export_materialized` FROM `hosts_stats` WHERE `id` = 0")
    boolean hasMaterializedRootExportRows();

    @Query("SELECT `root_export_stage_materialized` FROM `hosts_stats` WHERE `id` = 0")
    boolean hasStageMaterializedRootExportRows();

    @Query("SELECT COUNT(*) FROM `root_host_entries`")
    long getRootExportEntryCountNow();

    @Query("SELECT COUNT(*) FROM `root_host_entries_stage` AS `entry` " +
            "WHERE `entry`.`type` IN (0, 2) AND " + ROOT_STAGE_MATERIALIZED_SOURCE_WHERE +
            " AND " + ROOT_STAGE_SKIP_WHERE)
    long getRootExportStageBackedEntryCountNow(int activeGeneration);

    default long getMaterializedRootExportEntryCountNow() {
        if (hasStageMaterializedRootExportRows()) {
            return getRootExportStageBackedEntryCountNow(getActiveGeneration());
        }
        return getRootExportEntryCountNow();
    }

    default List<HostEntry> getAllForRootHostsFile() {
        if (hasMaterializedRootExportRows()) {
            if (hasStageMaterializedRootExportRows()) {
                return getAllForRootHostsFileStageMaterialized(getActiveGeneration());
            }
            return getAllForRootHostsFileMaterialized();
        }

        List<HostEntry> entries = new ArrayList<>();
        try (Cursor cursor = getActiveRootHostsFileCursor()) {
            int hostColumn = cursor.getColumnIndexOrThrow("host");
            int typeColumn = cursor.getColumnIndexOrThrow("type");
            int redirectionColumn = cursor.getColumnIndexOrThrow("redirection");
            while (cursor.moveToNext()) {
                HostEntry entry = new HostEntry();
                entry.setHost(cursor.getString(hostColumn));
                entry.setKind(EXACT);
                entry.setType(ListType.fromValue(cursor.getInt(typeColumn)));
                entry.setRedirection(cursor.getString(redirectionColumn));
                entries.add(entry);
            }
        }
        return entries;
    }

    default Cursor getRootHostsFileCursor() {
        if (hasMaterializedRootExportRows()) {
            return hasStageMaterializedRootExportRows()
                    ? getRootHostsFileCursorStageMaterialized(getActiveGeneration())
                    : getRootHostsFileCursorMaterialized();
        }
        return getRootHostsFileCursorActiveFiltered();
    }

    default Cursor getActiveRootHostsFileCursor() {
        return getRootHostsFileCursorActiveFiltered();
    }

    private Cursor getRootHostsFileCursorActiveFiltered() {
        int activeGeneration = getActiveGeneration();
        List<Cursor> cursors = new ArrayList<>(6);
        cursors.add(getRootRedirectedUserCursorActive());
        cursors.add(getRootRedirectedSourceCursorActive(activeGeneration));
        cursors.add(getRootExactBlockedUserCursorActive());
        cursors.add(getRootExactBlockedSourceCursorActive(activeGeneration));
        cursors.add(getRootSuffixBlockedUserCursorActive(activeGeneration));
        cursors.add(getRootSuffixBlockedSourceCursorActive(activeGeneration));
        return new ActiveRootHostsCursor(
                cursors,
                Arrays.asList(true, true, true, false, true, false),
                getActiveLiteralExactAllowedHosts(activeGeneration),
                getActiveSuffixAllowedHosts(activeGeneration),
                compileWildcardAllowedHostPatterns(
                        getActiveWildcardExactAllowedHosts(activeGeneration)));
    }

    private static List<Pattern> compileWildcardAllowedHostPatterns(List<String> wildcardHosts) {
        List<Pattern> patterns = new ArrayList<>(wildcardHosts.size());
        for (String host : wildcardHosts) {
            patterns.add(Pattern.compile(toWildcardRegex(Hostnames.normalize(host))));
        }
        return patterns;
    }

    private static String toWildcardRegex(String host) {
        StringBuilder regex = new StringBuilder(host.length() + 8);
        regex.append('^');
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c == '*' || c == '%') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else {
                appendRegexLiteral(regex, c);
            }
        }
        regex.append('$');
        return regex.toString();
    }

    private static void appendRegexLiteral(StringBuilder regex, char c) {
        if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
            regex.append('\\');
        }
        regex.append(c);
    }

    @Query("SELECT * FROM `host_entries` WHERE `kind` = 1 ORDER BY `host`")
    List<HostEntry> getAllSuffix();

    @Query("SELECT `type` FROM `host_entries` WHERE `host` == :host AND `kind` == 0 LIMIT 1")
    ListType getTypeOfHost(String host);

    default ListType getTypeForHost(String host) {
        return resolveEntry(host).getType();
    }

    default ListType getRootTypeForHost(String host) {
        return resolveRootEntry(host).getType();
    }

    @Nullable
    @Query("SELECT * FROM `host_entries` WHERE `host` == :host AND `kind` == 0 LIMIT 1")
    HostEntry getEntry(String host);

    @Nullable
    @Query("SELECT * FROM `host_entries` WHERE `host` == :host AND `kind` == 1 LIMIT 1")
    HostEntry getSuffixEntry(String host);

    @Nullable
    @Query("SELECT LOWER(`host`) AS `host`, `reverse_host`, `kind`, `type`, `redirection` " +
            "FROM `hosts_lists` WHERE `host` = :host AND `kind` = 0 " +
            "AND `type` = 2 AND `enabled` = 1 " +
            "AND (`source_id` == 1 OR (`generation` = :activeGeneration " +
            "AND `source_id` != 1)) " +
            "ORDER BY CASE WHEN `source_id` = 1 THEN 0 ELSE 1 END, `source_id` ASC, " +
            "COALESCE(`redirection`, '') ASC LIMIT 1")
    HostEntry getActiveExactRedirectEntry(String host, int activeGeneration);

    @Nullable
    @Query("SELECT LOWER(`host`) AS `host`, `reverse_host`, `kind`, `type`, `redirection` " +
            "FROM `hosts_lists` WHERE `host` = :host AND `kind` = 0 " +
            "AND `type` = 0 AND `enabled` = 1 " +
            "AND (`source_id` == 1 OR (`generation` = :activeGeneration " +
            "AND `source_id` != 1)) " +
            "ORDER BY CASE WHEN `source_id` = 1 THEN 0 ELSE 1 END, `source_id` ASC LIMIT 1")
    HostEntry getActiveExactBlockedEntry(String host, int activeGeneration);

    @Nullable
    @Query("SELECT LOWER(`host`) AS `host`, `reverse_host`, `kind`, `type`, `redirection` " +
            "FROM `hosts_lists` WHERE `host` = :host AND `kind` = 1 " +
            "AND `type` = 0 AND `enabled` = 1 " +
            "AND (`source_id` == 1 OR (`generation` = :activeGeneration " +
            "AND `source_id` != 1)) " +
            "ORDER BY CASE WHEN `source_id` = 1 THEN 0 ELSE 1 END, `source_id` ASC LIMIT 1")
    HostEntry getActiveSuffixBlockedEntry(String host, int activeGeneration);

    default HostEntry resolveEntry(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        int activeGeneration = getActiveGeneration();

        HostEntry redirect = getActiveExactRedirectEntry(normalized, activeGeneration);
        if (redirect != null) {
            return redirect;
        }

        if (isAllowedByActiveRule(normalized)) {
            return createAllowedEntry(normalized);
        }

        HostEntry exact = getActiveExactBlockedEntry(normalized, activeGeneration);
        if (exact != null) {
            return exact;
        }

        String candidate = normalized;
        while (candidate != null && !candidate.isEmpty()) {
            HostEntry suffix = getActiveSuffixBlockedEntry(candidate, activeGeneration);
            if (suffix != null) {
                return suffix;
            }
            int dot = candidate.indexOf('.');
            candidate = dot < 0 ? null : candidate.substring(dot + 1);
        }

        return createAllowedEntry(normalized);
    }

    default HostEntry resolveRootEntry(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        int activeGeneration = getActiveGeneration();

        HostEntry redirect = getActiveExactRedirectEntry(normalized, activeGeneration);
        if (redirect != null) {
            return redirect;
        }

        if (isAllowedByActiveRule(normalized)) {
            return createAllowedEntry(normalized);
        }

        HostEntry exact = getActiveExactBlockedEntry(normalized, activeGeneration);
        if (exact != null) {
            return exact;
        }

        HostEntry suffix = getActiveSuffixBlockedEntry(normalized, activeGeneration);
        if (suffix != null) {
            return suffix;
        }

        return createAllowedEntry(normalized);
    }

    static HostEntry createAllowedEntry(String host) {
        HostEntry entry = new HostEntry();
        entry.setHost(host);
        entry.setKind(EXACT);
        entry.setType(ALLOWED);
        return entry;
    }
}
