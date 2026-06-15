package org.adaway.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import org.adaway.util.Hostnames;

/**
 * Import-time candidate row for the root hosts-file export.
 */
@Entity(
        tableName = "root_host_entries_stage",
        indices = {
                @Index(
                        value = {"source_id", "generation"},
                        name = "index_root_host_entries_stage_source_generation"
                ),
                @Index(
                        value = {"generation", "source_id"},
                        name = "index_root_host_entries_stage_generation_source"
                ),
                @Index(
                        value = {"reverse_host", "host"},
                        name = "index_root_host_entries_stage_reverse_host"
                )
        }
)
public class RootHostStageEntry {
    @PrimaryKey(autoGenerate = true)
    private long id;
    @NonNull
    private String host;
    @NonNull
    @ColumnInfo(name = "reverse_host", defaultValue = "''")
    private String reverseHost = "";
    @NonNull
    private ListType type;
    private String redirection;
    @ColumnInfo(name = "source_id")
    private int sourceId;
    @ColumnInfo(name = "generation")
    private int generation;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getHost() {
        return host;
    }

    public void setHost(@NonNull String host) {
        this.host = Hostnames.normalize(host);
        this.reverseHost = Hostnames.reverseLabels(this.host);
    }

    @NonNull
    public String getReverseHost() {
        return reverseHost;
    }

    public void setReverseHost(@NonNull String reverseHost) {
        this.reverseHost = reverseHost;
    }

    @NonNull
    public ListType getType() {
        return type;
    }

    public void setType(@NonNull ListType type) {
        this.type = type;
    }

    public String getRedirection() {
        return redirection;
    }

    public void setRedirection(String redirection) {
        this.redirection = redirection;
    }

    public int getSourceId() {
        return sourceId;
    }

    public void setSourceId(int sourceId) {
        this.sourceId = sourceId;
    }

    public int getGeneration() {
        return generation;
    }

    public void setGeneration(int generation) {
        this.generation = generation;
    }
}
