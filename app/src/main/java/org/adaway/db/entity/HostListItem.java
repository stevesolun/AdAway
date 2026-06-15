package org.adaway.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

import org.adaway.util.Hostnames;

import static androidx.room.ForeignKey.CASCADE;

/**
 * This entity represents a black, white or redirect list item.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Entity(
        tableName = "hosts_lists",
        indices = {
                @Index(value = "host"),
                @Index(value = "source_id"),
                @Index(value = "generation"),
                @Index(value = {"generation", "source_id"}),
                @Index(
                        name = "index_hosts_lists_kind_host",
                        value = {
                                "kind", "host", "type", "enabled",
                                "generation", "source_id", "redirection", "reverse_host"
                        }
                ),
                @Index(
                        name = "index_hosts_lists_active_generation_kind_host",
                        value = {
                                "type", "enabled", "generation", "kind", "host",
                                "source_id", "reverse_host", "redirection"
                        }
                ),
                // Speed up Home screen counter queries (type=0/1/2 + enabled + generation/source_id).
                @Index(value = {"type", "enabled"}),
                @Index(value = {"type", "enabled", "source_id"}),
                @Index(value = {"type", "enabled", "generation"}),
                @Index(
                        name = "index_hosts_lists_active_allow_source_kind_host",
                        value = {"type", "enabled", "kind", "source_id", "host"}
                ),
                @Index(
                        name = "index_hosts_lists_active_allow_generation_source_kind_host",
                        value = {"type", "enabled", "kind", "generation", "source_id", "host"}
                ),
                @Index(
                        name = "index_hosts_lists_active_allow_source_kind_reverse_host",
                        value = {"type", "enabled", "kind", "source_id", "reverse_host"}
                ),
                @Index(
                        name = "index_hosts_lists_active_allow_generation_kind_reverse_host",
                        value = {
                                "type", "enabled", "kind", "generation",
                                "source_id", "reverse_host"
                        }
                )
        },
        foreignKeys = @ForeignKey(
                entity = HostsSource.class,
                parentColumns = "id",
                childColumns = "source_id",
                onUpdate = CASCADE,
                onDelete = CASCADE
        )
)
public class HostListItem {
    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull
    private String host;
    @NonNull
    @ColumnInfo(name = "reverse_host", defaultValue = "''")
    private String reverseHost = "";
    @NonNull
    private ListType type;
    @NonNull
    private RuleKind kind = RuleKind.EXACT;
    private boolean enabled;
    private String redirection;
    @ColumnInfo(name = "source_id")
    private int sourceId;
    /**
     * Generation id used for atomic updates.
     * Active dataset is the one where generation == hosts_meta.active_generation (plus user source).
     */
    @ColumnInfo(name = "generation")
    private int generation;

    public int getId() {
        return id;
    }

    public void setId(int id) {
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

    @NonNull
    public RuleKind getKind() {
        return kind;
    }

    public void setKind(@NonNull RuleKind kind) {
        this.kind = kind;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HostListItem item = (HostListItem) o;

        if (id != item.id) return false;
        if (enabled != item.enabled) return false;
        if (sourceId != item.sourceId) return false;
        if (generation != item.generation) return false;
        if (!host.equals(item.host)) return false;
        if (!reverseHost.equals(item.reverseHost)) return false;
        if (type != item.type) return false;
        if (kind != item.kind) return false;
        return Objects.equals(redirection, item.redirection);

    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + host.hashCode();
        result = 31 * result + reverseHost.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + kind.hashCode();
        result = 31 * result + (enabled ? 1 : 0);
        result = 31 * result + (redirection != null ? redirection.hashCode() : 0);
        result = 31 * result + sourceId;
        result = 31 * result + generation;
        return result;
    }
}
