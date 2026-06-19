package org.adaway.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Small active-truth counter table for dashboard reads.
 */
@Entity(tableName = "hosts_stats")
public class HostsStats {
    @PrimaryKey
    public int id = 0;

    @ColumnInfo(name = "blocked_count")
    public int blockedCount = 0;

    @ColumnInfo(name = "blocked_exact_count")
    public int blockedExactCount = 0;

    @ColumnInfo(name = "allowed_count")
    public int allowedCount = 0;

    @ColumnInfo(name = "redirected_count")
    public int redirectedCount = 0;

    @ColumnInfo(name = "active_rule_count")
    public int activeRuleCount = 0;

    @ColumnInfo(name = "root_export_materialized", defaultValue = "0")
    public boolean rootExportMaterialized = false;

    @ColumnInfo(name = "root_export_stage_materialized", defaultValue = "0")
    public boolean rootExportStageMaterialized = false;
}
