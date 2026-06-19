package org.adaway.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Stage row id excluded from a stage-backed root hosts-file export.
 */
@Entity(tableName = "root_export_skip_stage_ids")
public class RootHostSkipEntry {
    @PrimaryKey
    private long id;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
