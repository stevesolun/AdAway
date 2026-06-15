package org.adaway.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

import org.adaway.util.Hostnames;

/**
 * This entity represents an entry of the built hosts file.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Entity(
        tableName = "host_entries",
        primaryKeys = {"host", "kind"},
        indices = {
                @Index(
                        name = "index_host_entries_kind_host",
                        value = {"kind", "host", "type", "redirection"}
                ),
                @Index(
                        name = "index_host_entries_kind_reverse_host",
                        value = {"kind", "reverse_host", "host"}
                )
        }
)
public class HostEntry {
    @NonNull
    private String host;
    @NonNull
    @ColumnInfo(name = "reverse_host", defaultValue = "''")
    private String reverseHost = "";
    @NonNull
    private RuleKind kind = RuleKind.EXACT;
    @NonNull
    private ListType type;
    private String redirection;

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
    public RuleKind getKind() {
        return kind;
    }

    public void setKind(@NonNull RuleKind kind) {
        this.kind = kind;
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
}
