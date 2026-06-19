package org.adaway.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Locale;

/**
 * Materialized row for root hosts-file export.
 */
@Entity(tableName = "root_host_entries")
public class RootHostEntry {
    @PrimaryKey
    private long id;
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
        this.host = host.toLowerCase(Locale.ROOT);
    }

    @NonNull
    public String getReverseHost() {
        return reverseHost;
    }

    public void setReverseHost(@NonNull String reverseHost) {
        this.reverseHost = reverseHost.toLowerCase(Locale.ROOT);
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
