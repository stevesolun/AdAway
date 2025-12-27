package org.adaway.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Single-row meta table for hosts ingestion.
 * Used to store the active generation for atomic updates.
 */
@Entity(tableName = "hosts_meta")
public class HostsMeta {
    /**
     * Always 0 (single-row table).
     */
    @PrimaryKey
    public int id = 0;

    @ColumnInfo(name = "active_generation")
    public int activeGeneration = 0;
}





