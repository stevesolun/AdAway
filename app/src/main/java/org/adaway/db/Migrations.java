package org.adaway.db;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * This class declares database schema migrations.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
final class Migrations {
    private static final String HOST_ENTRIES_WITHOUT_ROWID_SQL =
            "CREATE TABLE IF NOT EXISTS `host_entries_new` " +
                    "(`host` TEXT NOT NULL, `kind` INTEGER NOT NULL DEFAULT 0, " +
                    "`type` INTEGER NOT NULL, `redirection` TEXT, " +
                    "PRIMARY KEY(`host`, `kind`)) WITHOUT ROWID";
    private static final String HOST_ENTRIES_WITH_REVERSE_SQL =
            "CREATE TABLE IF NOT EXISTS `host_entries_new` " +
                    "(`host` TEXT NOT NULL, `reverse_host` TEXT NOT NULL DEFAULT '', " +
                    "`kind` INTEGER NOT NULL DEFAULT 0, `type` INTEGER NOT NULL, " +
                    "`redirection` TEXT, PRIMARY KEY(`host`, `kind`))";
    private static final String HOST_ENTRIES_KIND_HOST_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_host_entries_kind_host` " +
                    "ON `host_entries` (`kind`, `host`, `type`, `redirection`)";
    private static final String HOST_ENTRIES_KIND_REVERSE_HOST_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_host_entries_kind_reverse_host` " +
                    "ON `host_entries` (`kind`, `reverse_host`, `host`)";
    private static final String ROOT_HOST_ENTRIES_REVERSE_HOST_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_root_host_entries_reverse_host` " +
                    "ON `root_host_entries` (`reverse_host`, `host`)";
    private static final String ROOT_HOST_ENTRIES_HOST_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_root_host_entries_host` " +
                    "ON `root_host_entries` (`host`)";
    private static final String ROOT_HOST_ENTRIES_WITHOUT_ROWID_SQL =
            "CREATE TABLE IF NOT EXISTS `root_host_entries_new` " +
                    "(`host` TEXT NOT NULL, `reverse_host` TEXT NOT NULL DEFAULT '', " +
                    "`kind` INTEGER NOT NULL, `type` INTEGER NOT NULL, " +
                    "`redirection` TEXT, PRIMARY KEY(`host`)) WITHOUT ROWID";
    private static final String ROOT_HOST_ENTRIES_APPEND_SQL =
            "CREATE TABLE IF NOT EXISTS `root_host_entries_new` " +
                    "(`id` INTEGER NOT NULL, `host` TEXT NOT NULL, " +
                    "`reverse_host` TEXT NOT NULL DEFAULT '', `kind` INTEGER NOT NULL, " +
                    "`type` INTEGER NOT NULL, `redirection` TEXT, PRIMARY KEY(`id`))";
    private static final String ROOT_HOST_ENTRIES_STAGE_SQL =
            "CREATE TABLE IF NOT EXISTS `root_host_entries_stage` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`host` TEXT NOT NULL, `reverse_host` TEXT NOT NULL DEFAULT '', " +
                    "`type` INTEGER NOT NULL, `redirection` TEXT, " +
                    "`source_id` INTEGER NOT NULL, `generation` INTEGER NOT NULL)";
    private static final String ROOT_HOST_ENTRIES_STAGE_SOURCE_GENERATION_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_root_host_entries_stage_source_generation` " +
                    "ON `root_host_entries_stage` (`source_id`, `generation`)";
    private static final String ROOT_HOST_ENTRIES_STAGE_GENERATION_SOURCE_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_root_host_entries_stage_generation_source` " +
                    "ON `root_host_entries_stage` (`generation`, `source_id`)";
    private static final String ROOT_HOST_ENTRIES_STAGE_REVERSE_HOST_LEGACY_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_root_host_entries_stage_reverse_host` " +
                    "ON `root_host_entries_stage` (`reverse_host`, `host`)";
    private static final String ROOT_HOST_ENTRIES_STAGE_REVERSE_HOST_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_root_host_entries_stage_reverse_host` " +
                    "ON `root_host_entries_stage` (`reverse_host`)";
    private static final String ROOT_HOST_ENTRIES_STAGE_REVERSE_HOST_COVERING_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_root_host_entries_stage_reverse_host` " +
                    "ON `root_host_entries_stage` " +
                    "(`reverse_host`, `type`, `source_id`, `generation`)";
    private static final String ROOT_EXPORT_SKIP_STAGE_IDS_SQL =
            "CREATE TABLE IF NOT EXISTS `root_export_skip_stage_ids` " +
                    "(`id` INTEGER NOT NULL, PRIMARY KEY(`id`))";
    static final String CREATE_HOSTS_STATS_SQL =
            "CREATE TABLE IF NOT EXISTS `hosts_stats` " +
                    "(`id` INTEGER NOT NULL, `blocked_count` INTEGER NOT NULL, " +
                    "`blocked_exact_count` INTEGER NOT NULL, `allowed_count` INTEGER NOT NULL, " +
                    "`redirected_count` INTEGER NOT NULL, `active_rule_count` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))";
    static final String REFRESH_HOSTS_STATS_FROM_ACTIVE_ROWS_SQL =
            "INSERT OR REPLACE INTO `hosts_stats` " +
                    "(`id`, `blocked_count`, `blocked_exact_count`, `allowed_count`, " +
                    "`redirected_count`, `active_rule_count`) SELECT 0, " +
                    "COALESCE(SUM(CASE WHEN `type` = 0 THEN 1 ELSE 0 END), 0), " +
                    "COALESCE(SUM(CASE WHEN `type` = 0 AND `kind` = 0 THEN 1 ELSE 0 END), 0), " +
                    "COALESCE(SUM(CASE WHEN `type` = 1 THEN 1 ELSE 0 END), 0), " +
                    "COALESCE(SUM(CASE WHEN `type` = 2 THEN 1 ELSE 0 END), 0), " +
                    "COUNT(*) FROM (" +
                    "SELECT `type`, `kind` FROM `hosts_lists` " +
                    "INDEXED BY `index_hosts_lists_kind_host` " +
                    "WHERE `enabled` = 1 AND (`source_id` = 1 OR (`generation` = " +
                    "(SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0) " +
                    "AND `source_id` != 1)))";
    static final String REFRESH_HOSTS_SOURCE_STATS_SQL =
            "UPDATE `hosts_sources` SET " +
                    "`size` = (SELECT COUNT(`id`) FROM `hosts_lists` " +
                    "WHERE `source_id` = `hosts_sources`.`id` " +
                    "AND (`hosts_sources`.`id` = 1 OR `generation` = " +
                    "(SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0))), " +
                    "`active_rule_count` = (SELECT COUNT(`id`) FROM `hosts_lists` " +
                    "WHERE `source_id` = `hosts_sources`.`id` AND `enabled` = 1 " +
                    "AND (`hosts_sources`.`id` = 1 OR `generation` = " +
                    "(SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0))), " +
                    "`blocked_count` = (SELECT COUNT(`id`) FROM `hosts_lists` " +
                    "WHERE `source_id` = `hosts_sources`.`id` AND `enabled` = 1 " +
                    "AND `type` = 0 AND (`hosts_sources`.`id` = 1 OR `generation` = " +
                    "(SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0))), " +
                    "`blocked_exact_count` = (SELECT COUNT(`id`) FROM `hosts_lists` " +
                    "WHERE `source_id` = `hosts_sources`.`id` AND `enabled` = 1 " +
                    "AND `type` = 0 AND `kind` = 0 AND (`hosts_sources`.`id` = 1 " +
                    "OR `generation` = " +
                    "(SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0))), " +
                    "`allowed_count` = (SELECT COUNT(`id`) FROM `hosts_lists` " +
                    "WHERE `source_id` = `hosts_sources`.`id` AND `enabled` = 1 " +
                    "AND `type` = 1 AND (`hosts_sources`.`id` = 1 OR `generation` = " +
                    "(SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0))), " +
                    "`redirected_count` = (SELECT COUNT(`id`) FROM `hosts_lists` " +
                    "WHERE `source_id` = `hosts_sources`.`id` AND `enabled` = 1 " +
                    "AND `type` = 2 AND (`hosts_sources`.`id` = 1 OR `generation` = " +
                    "(SELECT `active_generation` FROM `hosts_meta` WHERE `id` = 0)))";
    static final String REFRESH_HOSTS_STATS_SQL =
            "INSERT OR REPLACE INTO `hosts_stats` " +
                    "(`id`, `blocked_count`, `blocked_exact_count`, `allowed_count`, " +
                    "`redirected_count`, `active_rule_count`) SELECT 0, " +
                    "COALESCE(SUM(`blocked_count`), 0), " +
                    "COALESCE(SUM(`blocked_exact_count`), 0), " +
                    "COALESCE(SUM(`allowed_count`), 0), " +
                    "COALESCE(SUM(`redirected_count`), 0), " +
                    "COALESCE(SUM(`active_rule_count`), 0) " +
                    "FROM `hosts_sources` WHERE `enabled` = 1";

    /**
     * Private constructor of utility class.
     */
    private Migrations() {

    }

    /**
     * The migration script from v1 to v2.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add hosts sources id column and migrate data
            database.execSQL("CREATE TABLE `hosts_sources_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `last_modified_local` INTEGER, `last_modified_online` INTEGER)");
            database.execSQL("INSERT INTO `hosts_sources_new` (`id`, `url`, `enabled`) VALUES (" + USER_SOURCE_ID + ", '" + USER_SOURCE_URL + "', 1)");
            database.execSQL("INSERT INTO `hosts_sources_new` (`url`, `enabled`, `last_modified_local`, `last_modified_online`) SELECT `url`, `enabled`, `last_modified_local`, `last_modified_online` FROM `hosts_sources`");
            database.execSQL("DROP TABLE `hosts_sources`");
            database.execSQL("ALTER TABLE `hosts_sources_new` RENAME TO `hosts_sources`");
            // Add hosts list source id and migrate data
            database.execSQL("CREATE TABLE `hosts_lists_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `host` TEXT NOT NULL, `type` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `redirection` TEXT, `source_id` INTEGER NOT NULL, FOREIGN KEY(`source_id`) REFERENCES `hosts_sources`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )");
            database.execSQL("INSERT INTO `hosts_lists_new` (`host`, `type`, `enabled`, `redirection`, `source_id`) SELECT `host`, `type`, `enabled`, `redirection`, " + USER_SOURCE_ID + " FROM `hosts_lists`");
            database.execSQL("DROP TABLE `hosts_lists`");
            database.execSQL("ALTER TABLE `hosts_lists_new` RENAME TO `hosts_lists`");
            // Create index
            database.execSQL("CREATE UNIQUE INDEX `index_hosts_sources_url` ON `hosts_sources` (`url`)");
            database.execSQL("CREATE UNIQUE INDEX `index_hosts_lists_host` ON `hosts_lists` (`host`)");
            database.execSQL("CREATE INDEX `index_hosts_lists_source_id` ON `hosts_lists` (`source_id`)");
        }
    };
    /**
     * The migration script from v2 to v3.
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE VIEW `host_entries` AS SELECT `host`, `type`, `redirection` FROM `hosts_lists` WHERE `enabled` = 1 AND ((`type` = 0 AND `host` NOT LIKE (SELECT `host` FROM `hosts_lists` WHERE `enabled` = 1 and `type` = 1)) OR `type` = 2) ORDER BY `host` ASC, `type` DESC, `redirection` ASC");
        }
    };

    /**
     * Migration script from v3 to v4.
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Remove unique constraint to hosts_lists.host column
            database.execSQL("DROP INDEX `index_hosts_lists_host`");
            database.execSQL("CREATE INDEX `index_hosts_lists_host` ON `hosts_lists` (`host`)");
            // Update host_entries view
            database.execSQL("DROP VIEW `host_entries`");
            database.execSQL("CREATE VIEW `host_entries` AS SELECT `host`, `type`, `redirection` FROM `hosts_lists` WHERE `enabled` = 1 AND ((`type` = 0 AND `host` NOT LIKE (SELECT `host` FROM `hosts_lists` WHERE `enabled` = 1 and `type` = 1)) OR `type` = 2) GROUP BY `host` ORDER BY `host` ASC, `type` DESC, `redirection` ASC");
        }
    };

    /**
     * Migration script from v4 to v5.
     */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Remove host_entries view
            database.execSQL("DROP VIEW `host_entries`");
            // Create new host_entries table
            database.execSQL("CREATE TABLE IF NOT EXISTS `host_entries` (`host` TEXT NOT NULL, `type` INTEGER NOT NULL, `redirection` TEXT, PRIMARY KEY(`host`))");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_host_entries_host` ON `host_entries` (`host`)");
        }
    };

    /**
     * Migration script from v5 to v6.
     */
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Update hosts_sources table
            database.execSQL("ALTER TABLE `hosts_sources` ADD `label` TEXT NOT NULL DEFAULT \"\"");
            database.execSQL("ALTER TABLE `hosts_sources` ADD `allowEnabled` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `hosts_sources` ADD `redirectEnabled` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `hosts_sources` ADD `size` INTEGER NOT NULL DEFAULT 0");
            // Set default values to new source attributes
            database.execSQL("UPDATE `hosts_sources` SET `label` = `url`");
            // Update user hosts list
            database.execSQL("UPDATE `hosts_sources` SET `url` = \"content://org.adaway/user/hosts\", `allowEnabled` = 1, `redirectEnabled` = 1 WHERE `url` = \"file://app/user/hosts\"");
            // Update default hosts source label
            database.execSQL("UPDATE `hosts_sources` SET `label` = \"AdAway official hosts\" WHERE `url` = \"https://adaway.org/hosts.txt\"");
            database.execSQL("UPDATE `hosts_sources` SET `label` = \"StevenBlack Unified hosts\" WHERE `url` = \"https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts\"");
            database.execSQL("UPDATE `hosts_sources` SET `label` = \"Pete Lowe blocklist hosts\" WHERE `url` = \"https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext\"");
            // Reset local date to rebuild cache
            database.execSQL("UPDATE `hosts_sources` SET `last_modified_local` = NULL");
            // Update hosts source date format
            database.execSQL("UPDATE `hosts_sources` SET `last_modified_online` = `last_modified_online` / 1000");
            // Clear previous file type hosts sources
            database.execSQL("DELETE FROM `hosts_sources` WHERE `url` LIKE \"file://%\"");
        }
    };

    /**
     * Migration script from v6 to v7.
     */
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Update hosts_sources table
            database.execSQL("ALTER TABLE `hosts_sources` ADD `entityTag` TEXT DEFAULT NULL");
        }
    };

    /**
     * Migration script from v7 to v8.
     * Adds generation-based atomic updates and a small meta table storing the active generation.
     */
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Add generation column to hosts_lists (default 0 for existing data)
            database.execSQL("ALTER TABLE `hosts_lists` ADD COLUMN `generation` INTEGER NOT NULL DEFAULT 0");
            // Index generation for faster active-dataset queries
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_lists_generation` ON `hosts_lists` (`generation`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_lists_generation_source_id` ON `hosts_lists` (`generation`, `source_id`)");

            // Create meta table for active generation (Room entity)
            database.execSQL("CREATE TABLE IF NOT EXISTS `hosts_meta` (`id` INTEGER NOT NULL, `active_generation` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            // Ensure exactly one row exists (id=0)
            database.execSQL("INSERT OR REPLACE INTO `hosts_meta` (`id`, `active_generation`) VALUES (0, 0)");
        }
    };

    /**
     * Migration script from v8 to v9.
     * Adds indexes to speed up host counter queries (type/enabled filters were previously full table scans).
     */
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_lists_type_enabled` ON `hosts_lists` (`type`, `enabled`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_lists_type_enabled_source_id` ON `hosts_lists` (`type`, `enabled`, `source_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_lists_type_enabled_generation` ON `hosts_lists` (`type`, `enabled`, `generation`)");
        }
    };

    /**
     * Migration script from v9 to v10.
     * Adds skipped_count column to hosts_sources to track entries skipped during parsing.
     */
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN `skipped_count` INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * Migration script from v10 to v11.
     * Adds last_download_error column to hosts_sources to surface download failures in UI.
     */
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN `last_download_error` TEXT");
        }
    };

    /**
     * Migration script from v11 to v12.
     * Adds rule-kind storage so exact and suffix rules are not conflated.
     */
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `hosts_lists` ADD COLUMN `kind` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_lists_kind_host` ON `hosts_lists` (`kind`, `host`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `host_entries_new` " +
                    "(`host` TEXT NOT NULL, `kind` INTEGER NOT NULL DEFAULT 0, " +
                    "`type` INTEGER NOT NULL, `redirection` TEXT, PRIMARY KEY(`host`, `kind`))");
            database.execSQL("INSERT INTO `host_entries_new` (`host`, `kind`, `type`, `redirection`) " +
                    "SELECT `host`, 0, `type`, `redirection` FROM `host_entries`");
            database.execSQL("DROP TABLE `host_entries`");
            database.execSQL("ALTER TABLE `host_entries_new` RENAME TO `host_entries`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_host_entries_host` ON `host_entries` (`host`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_host_entries_kind_host` ON `host_entries` (`kind`, `host`)");
        }
    };

    /**
     * Migration script from v12 to v13.
     * Drops a redundant host_entries(host) index. The primary key already covers
     * exact `(host, kind)` lookups, while index_host_entries_kind_host covers
     * kind-scoped scans and root export ordering.
     */
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP INDEX IF EXISTS `index_host_entries_host`");
        }
    };

    /**
     * Migration script from v13 to v14.
     * Rebuilds the root-export index as a covering index so large root cursor
     * exports can read host, type, and redirection directly from the index.
     */
    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP INDEX IF EXISTS `index_host_entries_kind_host`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_host_entries_kind_host` " +
                    "ON `host_entries` (`kind`, `host`, `type`, `redirection`)");
        }
    };

    /**
     * Migration script from v14 to v15.
     * Adds materialized root hosts-file export rows so applying root mode does
     * not recompute suffix/allow semantics while draining the cursor.
     */
    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `root_host_entries` " +
                    "(`host` TEXT NOT NULL, `kind` INTEGER NOT NULL, `type` INTEGER NOT NULL, " +
                    "`redirection` TEXT, PRIMARY KEY(`host`))");
            database.execSQL("DELETE FROM `root_host_entries`");
            database.execSQL("INSERT OR REPLACE INTO `root_host_entries` " +
                    "(`host`, `kind`, `type`, `redirection`) " +
                    "SELECT `host`, 0, `type`, `redirection` FROM `host_entries` " +
                    "WHERE `kind` = 0");
            database.execSQL("INSERT OR IGNORE INTO `root_host_entries` " +
                    "(`host`, `kind`, `type`, `redirection`) " +
                    "SELECT entry.`host`, 0, entry.`type`, entry.`redirection` " +
                    "FROM `host_entries` AS entry WHERE entry.`kind` = 1 " +
                    "AND entry.`type` = 0 AND NOT EXISTS (" +
                    "SELECT 1 FROM `hosts_lists` AS allowed WHERE allowed.`type` = 1 " +
                    "AND allowed.`enabled` = 1 " +
                    "AND (allowed.`source_id` = 1 OR (allowed.`generation` = " +
                    "(SELECT active_generation FROM hosts_meta WHERE id = 0) " +
                    "AND allowed.`source_id` != 1)) " +
                    "AND ((allowed.`kind` = 0 AND entry.`host` LIKE " +
                    "REPLACE(REPLACE(LOWER(allowed.`host`), '*', '%'), '?', '_')) " +
                    "OR (allowed.`kind` = 1 AND (entry.`host` = LOWER(allowed.`host`) " +
                    "OR entry.`host` LIKE '%.' || LOWER(allowed.`host`)))) LIMIT 1)");
        }
    };

    /**
     * Migration script from v15 to v16.
     * Adds covering indexes for active allow-rule probes used by runtime rebuild.
     */
    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_hosts_lists_active_allow_source_kind_host` ON `hosts_lists` " +
                    "(`type`, `enabled`, `kind`, `source_id`, `host`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_hosts_lists_active_allow_generation_source_kind_host` " +
                    "ON `hosts_lists` " +
                    "(`type`, `enabled`, `kind`, `generation`, `source_id`, `host`)");
        }
    };

    /**
     * Migration script from v16 to v17.
     * Rebuilds host_entries without rowid so large runtime imports maintain one
     * composite primary-key b-tree instead of a rowid table plus a separate
     * primary-key autoindex.
     */
    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            optimizeHostEntriesStorage(database);
        }
    };

    /**
     * Migration script from v17 to v18.
     * Rebuilds the existing kind/host index as a covering index for the runtime
     * import path. This keeps the host-sorted scan while avoiding millions of
     * random hosts_lists table lookups at large filter sizes.
     */
    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP INDEX IF EXISTS `index_hosts_lists_kind_host`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_lists_kind_host` " +
                    "ON `hosts_lists` (`kind`, `host`, `type`, `enabled`, " +
                    "`generation`, `source_id`, `redirection`)");
        }
    };

    /**
     * Migration script from v18 to v19.
     * Adds a tiny active-truth stats table for O(1) dashboard counters.
     */
    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(CREATE_HOSTS_STATS_SQL);
            database.execSQL(REFRESH_HOSTS_STATS_FROM_ACTIVE_ROWS_SQL);
        }
    };

    /**
     * Migration script from v19 to v20.
     * Adds per-source active counters so refreshing dashboard stats never scans
     * the multi-million-row hosts_lists table.
     */
    static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN " +
                    "`active_rule_count` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN " +
                    "`blocked_count` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN " +
                    "`blocked_exact_count` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN " +
                    "`allowed_count` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN " +
                    "`redirected_count` INTEGER NOT NULL DEFAULT 0");
            database.execSQL(REFRESH_HOSTS_SOURCE_STATS_SQL);
            database.execSQL(REFRESH_HOSTS_STATS_SQL);
        }
    };

    /**
     * Migration script from v20 to v21.
     * Adds durable FilterLists.com provenance metadata to subscribed sources.
     */
    static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN `filter_list_id` INTEGER");
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN " +
                    "`filter_list_syntax_ids` TEXT");
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN " +
                    "`filter_list_compatibility` TEXT");
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN " +
                    "`filter_list_compatibility_score` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN " +
                    "`filter_list_selected_url` TEXT");
        }
    };

    /**
     * Migration script from v21 to v22.
     * Adds label-reversed host keys so suffix allow rules can be resolved with
     * indexed prefix-range probes instead of scanning every runtime row.
     */
    static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `hosts_lists` ADD COLUMN " +
                    "`reverse_host` TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE `host_entries` ADD COLUMN " +
                    "`reverse_host` TEXT NOT NULL DEFAULT ''");

            backfillHostsListsReverseHosts(database);
            backfillHostEntriesReverseHosts(database);
            optimizeHostEntriesStorage(database);

            database.execSQL("DROP INDEX IF EXISTS `index_hosts_lists_kind_host`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_lists_kind_host` " +
                    "ON `hosts_lists` (`kind`, `host`, `type`, `enabled`, " +
                    "`generation`, `source_id`, `redirection`, `reverse_host`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_hosts_lists_active_generation_kind_host` ON `hosts_lists` " +
                    "(`type`, `enabled`, `generation`, `kind`, `host`, `source_id`, " +
                    "`reverse_host`, `redirection`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_hosts_lists_active_allow_source_kind_reverse_host` " +
                    "ON `hosts_lists` " +
                    "(`type`, `enabled`, `kind`, `source_id`, `reverse_host`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS " +
                    "`index_hosts_lists_active_allow_generation_kind_reverse_host` " +
                    "ON `hosts_lists` " +
                    "(`type`, `enabled`, `kind`, `generation`, `source_id`, `reverse_host`)");
            database.execSQL(HOST_ENTRIES_KIND_REVERSE_HOST_INDEX_SQL);
        }
    };

    /**
     * Migration script from v22 to v23.
     * Tracks whether root_host_entries is a valid materialized cache even when
     * the valid root export contains zero rows.
     */
    static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `hosts_stats` ADD COLUMN " +
                    "`root_export_materialized` INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * Migration script from v23 to v24.
     * Adds label-reversed host keys to materialized root export rows so the
     * large-cache root rebuild path can apply suffix allow rules directly in
     * root_host_entries without a separate staging table.
     */
    static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `root_host_entries` ADD COLUMN " +
                    "`reverse_host` TEXT NOT NULL DEFAULT ''");
            backfillRootHostEntriesReverseHosts(database);
            database.execSQL(ROOT_HOST_ENTRIES_REVERSE_HOST_INDEX_SQL);
        }
    };

    /**
     * Migration script from v24 to v25.
     * Rebuilds root_host_entries without rowid so large root export rebuilds maintain one
     * primary-key b-tree instead of a rowid table plus a separate text-key autoindex.
     */
    static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            optimizeRootHostEntriesWithoutRowid(database);
        }
    };

    /**
     * Migration script from v25 to v26.
     * Rebuilds root_host_entries as an append-friendly rowid table. Large root export rebuilds
     * dedupe in set-based SQL phases instead of paying one text primary-key conflict check per row.
     */
    static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            optimizeRootHostEntriesStorage(database);
        }
    };

    /**
     * Migration script from v26 to v27.
     * Adds an import-time staging table for root hosts-file candidates so large
     * updates do not need to rescan hosts_lists for the root export.
     */
    static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            createRootHostEntriesStage(database);
        }
    };

    /**
     * Migration script from v27 to v28.
     * Adds richer FilterLists.com directory provenance to subscribed source rows.
     */
    static final Migration MIGRATION_27_28 = new Migration(27, 28) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN `filter_list_name` TEXT");
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN `filter_list_tag_ids` TEXT");
            database.execSQL("ALTER TABLE `hosts_sources` ADD COLUMN " +
                    "`filter_list_language_ids` TEXT");
        }
    };

    /**
     * Migration script from v28 to v29.
     * Drops the unused root export staging reverse-host index to reduce large import write
     * amplification.
     */
    static final Migration MIGRATION_28_29 = new Migration(28, 29) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP INDEX IF EXISTS `index_root_host_entries_stage_reverse_host`");
        }
    };

    /**
     * Migration script from v29 to v30.
     * Drops persistent indexes from root_host_entries and creates a narrow reverse-host index on
     * root_host_entries_stage. The final table is a large append/read-through hosts-file cache
     * streamed by row id; suffix allow lookups use the import-time stage instead.
     */
    static final Migration MIGRATION_29_30 = new Migration(29, 30) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            dropRootHostEntryIndexes(database);
            database.execSQL(ROOT_HOST_ENTRIES_STAGE_REVERSE_HOST_INDEX_SQL);
        }
    };

    /**
     * Migration script from v30 to v31.
     * Tracks when a complete root export can be streamed directly from the import-time stage
     * table instead of copying millions of rows into root_host_entries.
     */
    static final Migration MIGRATION_30_31 = new Migration(30, 31) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `hosts_stats` ADD COLUMN " +
                    "`root_export_stage_materialized` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("UPDATE `hosts_stats` SET `root_export_stage_materialized` = 0");
            database.execSQL(ROOT_EXPORT_SKIP_STAGE_IDS_SQL);
        }
    };

    /**
     * Migration script from v31 to v32.
     * Makes the root export stage reverse-host index covering for large redirect conflict and
     * allow-skip scans.
     */
    static final Migration MIGRATION_31_32 = new Migration(31, 32) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP INDEX IF EXISTS `index_root_host_entries_stage_reverse_host`");
            database.execSQL(ROOT_HOST_ENTRIES_STAGE_REVERSE_HOST_COVERING_INDEX_SQL);
        }
    };

    static void createRootHostEntriesStage(@NonNull SupportSQLiteDatabase database) {
        database.execSQL(ROOT_HOST_ENTRIES_STAGE_SQL);
        database.execSQL(ROOT_HOST_ENTRIES_STAGE_SOURCE_GENERATION_INDEX_SQL);
        database.execSQL(ROOT_HOST_ENTRIES_STAGE_GENERATION_SOURCE_INDEX_SQL);
        database.execSQL(ROOT_HOST_ENTRIES_STAGE_REVERSE_HOST_LEGACY_INDEX_SQL);
    }

    static void optimizeHostEntriesStorage(@NonNull SupportSQLiteDatabase database) {
        boolean hasReverseHost = hasColumn(database, "host_entries", "reverse_host");
        database.execSQL("DROP INDEX IF EXISTS `index_host_entries_kind_host`");
        database.execSQL("DROP INDEX IF EXISTS `index_host_entries_kind_reverse_host`");
        if (hasReverseHost) {
            database.execSQL(HOST_ENTRIES_WITH_REVERSE_SQL);
            database.execSQL("INSERT OR IGNORE INTO `host_entries_new` " +
                    "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
                    "SELECT `host`, `reverse_host`, `kind`, `type`, `redirection` " +
                    "FROM `host_entries`");
        } else {
            database.execSQL(HOST_ENTRIES_WITHOUT_ROWID_SQL);
            database.execSQL("INSERT OR IGNORE INTO `host_entries_new` " +
                    "(`host`, `kind`, `type`, `redirection`) " +
                    "SELECT `host`, `kind`, `type`, `redirection` FROM `host_entries`");
        }
        database.execSQL("DROP TABLE `host_entries`");
        database.execSQL("ALTER TABLE `host_entries_new` RENAME TO `host_entries`");
        database.execSQL(HOST_ENTRIES_KIND_HOST_INDEX_SQL);
        if (hasReverseHost) {
            database.execSQL(HOST_ENTRIES_KIND_REVERSE_HOST_INDEX_SQL);
        }
    }

    static void optimizeRootHostEntriesWithoutRowid(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("DROP INDEX IF EXISTS `index_root_host_entries_reverse_host`");
        database.execSQL(ROOT_HOST_ENTRIES_WITHOUT_ROWID_SQL);
        database.execSQL("INSERT OR REPLACE INTO `root_host_entries_new` " +
                "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
                "SELECT `host`, `reverse_host`, `kind`, `type`, `redirection` " +
                "FROM `root_host_entries`");
        database.execSQL("DROP TABLE `root_host_entries`");
        database.execSQL("ALTER TABLE `root_host_entries_new` RENAME TO `root_host_entries`");
        database.execSQL(ROOT_HOST_ENTRIES_REVERSE_HOST_INDEX_SQL);
    }

    static void optimizeRootHostEntriesStorage(@NonNull SupportSQLiteDatabase database) {
        boolean hasId = hasColumn(database, "root_host_entries", "id");
        if (hasId) {
            dropRootHostEntryIndexes(database);
            return;
        }
        dropRootHostEntryIndexes(database);
        database.execSQL(ROOT_HOST_ENTRIES_APPEND_SQL);
        database.execSQL("INSERT INTO `root_host_entries_new` " +
                "(`host`, `reverse_host`, `kind`, `type`, `redirection`) " +
                "SELECT `host`, `reverse_host`, `kind`, `type`, `redirection` " +
                "FROM `root_host_entries`");
        database.execSQL("DROP TABLE `root_host_entries`");
        database.execSQL("ALTER TABLE `root_host_entries_new` RENAME TO `root_host_entries`");
        dropRootHostEntryIndexes(database);
    }

    private static void dropRootHostEntryIndexes(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("DROP INDEX IF EXISTS `index_root_host_entries_host`");
        database.execSQL("DROP INDEX IF EXISTS `index_root_host_entries_reverse_host`");
    }

    private static void backfillHostsListsReverseHosts(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("CREATE TEMP TABLE IF NOT EXISTS `reverse_host_backfill_hosts_lists` " +
                "(`id` INTEGER NOT NULL, `reverse_host` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`)) WITHOUT ROWID");
        database.execSQL("DELETE FROM `reverse_host_backfill_hosts_lists`");
        database.execSQL("INSERT INTO `reverse_host_backfill_hosts_lists` " +
                "(`id`, `reverse_host`) " + reverseLabelCteSql("`id`", "`id`",
                "`hosts_lists`", "`reverse_host` = ''"));
        database.execSQL("UPDATE `hosts_lists` SET `reverse_host` = (" +
                "SELECT `reverse_host` FROM `reverse_host_backfill_hosts_lists` AS backfill " +
                "WHERE backfill.`id` = `hosts_lists`.`id`) " +
                "WHERE `id` IN (SELECT `id` FROM `reverse_host_backfill_hosts_lists`)");
        database.execSQL("DROP TABLE `reverse_host_backfill_hosts_lists`");
    }

    private static void backfillHostEntriesReverseHosts(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("CREATE TEMP TABLE IF NOT EXISTS `reverse_host_backfill_host_entries` " +
                "(`host` TEXT NOT NULL, `kind` INTEGER NOT NULL, " +
                "`reverse_host` TEXT NOT NULL, PRIMARY KEY(`host`, `kind`)) WITHOUT ROWID");
        database.execSQL("DELETE FROM `reverse_host_backfill_host_entries`");
        database.execSQL("INSERT INTO `reverse_host_backfill_host_entries` " +
                "(`host`, `kind`, `reverse_host`) " + reverseLabelCteSql(
                "`host`, `kind`", "`host`, `kind`", "`host_entries`",
                "`reverse_host` = ''"));
        database.execSQL("UPDATE `host_entries` SET `reverse_host` = (" +
                "SELECT `reverse_host` FROM `reverse_host_backfill_host_entries` AS backfill " +
                "WHERE backfill.`host` = `host_entries`.`host` " +
                "AND backfill.`kind` = `host_entries`.`kind`) " +
                "WHERE EXISTS (SELECT 1 FROM `reverse_host_backfill_host_entries` AS backfill " +
                "WHERE backfill.`host` = `host_entries`.`host` " +
                "AND backfill.`kind` = `host_entries`.`kind`)");
        database.execSQL("DROP TABLE `reverse_host_backfill_host_entries`");
    }

    private static void backfillRootHostEntriesReverseHosts(
            @NonNull SupportSQLiteDatabase database) {
        database.execSQL("CREATE TEMP TABLE IF NOT EXISTS " +
                "`reverse_host_backfill_root_host_entries` " +
                "(`host` TEXT NOT NULL, `reverse_host` TEXT NOT NULL, " +
                "PRIMARY KEY(`host`)) WITHOUT ROWID");
        database.execSQL("DELETE FROM `reverse_host_backfill_root_host_entries`");
        database.execSQL("INSERT INTO `reverse_host_backfill_root_host_entries` " +
                "(`host`, `reverse_host`) " + reverseLabelCteSql(
                "`host`", "`host`", "`root_host_entries`", "`reverse_host` = ''"));
        database.execSQL("UPDATE `root_host_entries` SET `reverse_host` = (" +
                "SELECT `reverse_host` FROM `reverse_host_backfill_root_host_entries` " +
                "AS backfill WHERE backfill.`host` = `root_host_entries`.`host`) " +
                "WHERE `host` IN (SELECT `host` FROM " +
                "`reverse_host_backfill_root_host_entries`)");
        database.execSQL("DROP TABLE `reverse_host_backfill_root_host_entries`");
    }

    private static String reverseLabelCteSql(String keyColumns, String selectKeyColumns,
            String tableName, String whereClause) {
        String keys = keyColumns + ", ";
        return "WITH RECURSIVE `parts`(" + keys + "`rest`, `reversed`) AS (" +
                "SELECT " + selectKeyColumns + ", LOWER(`host`), '' FROM " + tableName +
                " WHERE " + whereClause + " " +
                "UNION ALL SELECT " + keyColumns + ", " +
                "CASE WHEN instr(`rest`, '.') = 0 THEN '' " +
                "ELSE substr(`rest`, instr(`rest`, '.') + 1) END, " +
                "CASE WHEN `reversed` = '' THEN " +
                "CASE WHEN instr(`rest`, '.') = 0 THEN `rest` " +
                "ELSE substr(`rest`, 1, instr(`rest`, '.') - 1) END " +
                "ELSE CASE WHEN instr(`rest`, '.') = 0 THEN `rest` " +
                "ELSE substr(`rest`, 1, instr(`rest`, '.') - 1) END || '.' || " +
                "`reversed` END FROM `parts` WHERE `rest` != '') " +
                "SELECT " + selectKeyColumns + ", `reversed` FROM `parts` WHERE `rest` = ''";
    }

    private static boolean hasColumn(@NonNull SupportSQLiteDatabase database,
            @NonNull String table, @NonNull String column) {
        try (Cursor cursor = database.query("PRAGMA table_info(`" + table + "`)")) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(1))) {
                    return true;
                }
            }
        }
        return false;
    }

}
