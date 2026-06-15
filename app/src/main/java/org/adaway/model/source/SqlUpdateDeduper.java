package org.adaway.model.source;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;

import static org.adaway.db.entity.RuleKind.EXACT;

/**
 * Update-scoped SQL deduplication table for large imports.
 *
 * The table is intentionally not TEMP. Android's SQLite connection pool can move Room work
 * between connections, while TEMP tables are connection-local and disappear from other handles.
 * Each update run clears the table before parser workers start inserting rows.
 */
final class SqlUpdateDeduper {
    private static final String TABLE = "update_seen_hosts";
    private static final String PENDING_TABLE = "update_pending_hosts";
    private static final String ROOT_EXPORT_STAGE_TABLE = "root_host_entries_stage";

    private final SupportSQLiteDatabase db;

    SqlUpdateDeduper(@NonNull SupportSQLiteDatabase db) {
        this.db = db;
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + PENDING_TABLE);
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "source_id INTEGER NOT NULL, " +
                "type INTEGER NOT NULL, " +
                "kind INTEGER NOT NULL, " +
                "host TEXT NOT NULL, " +
                "redirection_is_null INTEGER NOT NULL, " +
                "redirection_value TEXT NOT NULL, " +
                "PRIMARY KEY(source_id, type, kind, host, redirection_is_null, " +
                "redirection_value)) " +
                "WITHOUT ROWID");
        db.execSQL("CREATE TABLE " + PENDING_TABLE + " (" +
                "source_id INTEGER NOT NULL, " +
                "host TEXT NOT NULL, " +
                "reverse_host TEXT NOT NULL, " +
                "type INTEGER NOT NULL, " +
                "kind INTEGER NOT NULL, " +
                "enabled INTEGER NOT NULL, " +
                "redirection TEXT, " +
                "redirection_is_null INTEGER NOT NULL, " +
                "redirection_value TEXT NOT NULL, " +
                "generation INTEGER NOT NULL, " +
                "PRIMARY KEY(source_id, type, kind, host, redirection_is_null, " +
                "redirection_value)) WITHOUT ROWID");
    }

    @NonNull
    SupportSQLiteStatement compilePendingInsertStatement() {
        return this.db.compileStatement(
                "INSERT OR IGNORE INTO " + PENDING_TABLE +
                        " (source_id, host, reverse_host, type, kind, enabled, redirection, " +
                        "redirection_is_null, redirection_value, generation) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    }

    long count() {
        try (Cursor cursor = this.db.query("SELECT COUNT(*) FROM " + TABLE)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    long pendingCount() {
        try (Cursor cursor = this.db.query("SELECT COUNT(*) FROM " + PENDING_TABLE)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    int flushPendingRowsToHostsLists() {
        SupportSQLiteStatement insertStatement = this.db.compileStatement(
                "INSERT INTO hosts_lists " +
                        "(host, reverse_host, type, kind, enabled, redirection, " +
                        "source_id, generation) " +
                        "SELECT pending.host, pending.reverse_host, pending.type, " +
                        "pending.kind, pending.enabled, pending.redirection, " +
                        "pending.source_id, pending.generation " +
                        "FROM " + PENDING_TABLE + " AS pending " +
                        "WHERE NOT EXISTS (" +
                        "SELECT 1 FROM " + TABLE + " AS seen " +
                        "WHERE seen.source_id = pending.source_id " +
                        "AND seen.type = pending.type " +
                        "AND seen.kind = pending.kind " +
                        "AND seen.host = pending.host " +
                        "AND seen.redirection_is_null = pending.redirection_is_null " +
                        "AND seen.redirection_value = pending.redirection_value)");
        int inserted = insertStatement.executeUpdateDelete();
        SupportSQLiteStatement stageStatement = this.db.compileStatement(
                "INSERT INTO " + ROOT_EXPORT_STAGE_TABLE + " " +
                        "(host, reverse_host, type, redirection, source_id, generation) " +
                        "SELECT pending.host, pending.reverse_host, pending.type, " +
                        "pending.redirection, pending.source_id, pending.generation " +
                        "FROM " + PENDING_TABLE + " AS pending " +
                        "WHERE pending.enabled = 1 AND pending.type IN (0, 2) " +
                        "AND NOT EXISTS (" +
                        "SELECT 1 FROM " + TABLE + " AS seen " +
                        "WHERE seen.source_id = pending.source_id " +
                        "AND seen.type = pending.type " +
                        "AND seen.kind = pending.kind " +
                        "AND seen.host = pending.host " +
                        "AND seen.redirection_is_null = pending.redirection_is_null " +
                        "AND seen.redirection_value = pending.redirection_value)");
        stageStatement.executeUpdateDelete();
        SupportSQLiteStatement markStatement = this.db.compileStatement(
                "INSERT OR IGNORE INTO " + TABLE +
                        " (source_id, type, kind, host, redirection_is_null, " +
                        "redirection_value) " +
                        "SELECT source_id, type, kind, host, redirection_is_null, " +
                        "redirection_value " +
                        "FROM " + PENDING_TABLE);
        markStatement.executeUpdateDelete();
        this.db.execSQL("DELETE FROM " + PENDING_TABLE);
        return inserted;
    }

    int copyUnseenSourceGeneration(int sourceId, int oldGeneration, int newGeneration) {
        this.db.beginTransaction();
        try {
            SupportSQLiteStatement clearStatement = this.db.compileStatement(
                    "DELETE FROM hosts_lists WHERE source_id = ? AND generation = ?");
            clearStatement.bindLong(1, sourceId);
            clearStatement.bindLong(2, newGeneration);
            clearStatement.executeUpdateDelete();
            SupportSQLiteStatement clearStageStatement = this.db.compileStatement(
                    "DELETE FROM " + ROOT_EXPORT_STAGE_TABLE +
                            " WHERE source_id = ? AND generation = ?");
            clearStageStatement.bindLong(1, sourceId);
            clearStageStatement.bindLong(2, newGeneration);
            clearStageStatement.executeUpdateDelete();

            SupportSQLiteStatement copyStatement = this.db.compileStatement(
                    "WITH canonical AS (" +
                            "SELECT MIN(id) AS id FROM hosts_lists " +
                            "WHERE source_id = ? AND generation = ? " +
                            "GROUP BY source_id, generation, type, kind, host, " +
                            "CASE WHEN redirection IS NULL THEN 1 ELSE 0 END, " +
                            "COALESCE(redirection, '')) " +
                            "INSERT INTO hosts_lists " +
                            "(host, reverse_host, type, kind, enabled, redirection, " +
                            "source_id, generation) " +
                            "SELECT src.host, src.reverse_host, src.type, src.kind, " +
                            "src.enabled, src.redirection, src.source_id, ? " +
                            "FROM hosts_lists AS src " +
                            "JOIN canonical ON canonical.id = src.id " +
                            "WHERE NOT EXISTS (" +
                            "SELECT 1 FROM " + TABLE + " AS seen " +
                            "WHERE seen.source_id = src.source_id " +
                            "AND seen.type = src.type " +
                            "AND seen.kind = src.kind " +
                            "AND seen.host = src.host " +
                            "AND ((seen.redirection_is_null = 1 AND src.redirection IS NULL) " +
                            "OR (seen.redirection_is_null = 0 AND src.redirection IS NOT NULL " +
                            "AND seen.redirection_value = src.redirection)))");
            copyStatement.bindLong(1, sourceId);
            copyStatement.bindLong(2, oldGeneration);
            copyStatement.bindLong(3, newGeneration);
            int copied = copyStatement.executeUpdateDelete();
            SupportSQLiteStatement stageStatement = this.db.compileStatement(
                    "INSERT INTO " + ROOT_EXPORT_STAGE_TABLE + " " +
                            "(host, reverse_host, type, redirection, source_id, generation) " +
                            "SELECT host, reverse_host, type, redirection, source_id, " +
                            "generation FROM hosts_lists " +
                            "WHERE source_id = ? AND generation = ? AND enabled = 1 " +
                            "AND type IN (0, 2)");
            stageStatement.bindLong(1, sourceId);
            stageStatement.bindLong(2, newGeneration);
            stageStatement.executeUpdateDelete();

            SupportSQLiteStatement markStatement = this.db.compileStatement(
                    "INSERT OR IGNORE INTO " + TABLE +
                            " (source_id, type, kind, host, redirection_is_null, " +
                            "redirection_value) " +
                            "SELECT source_id, type, kind, host, " +
                            "CASE WHEN redirection IS NULL THEN 1 ELSE 0 END, " +
                            "COALESCE(redirection, '') " +
                            "FROM hosts_lists WHERE source_id = ? AND generation = ? " +
                            "GROUP BY source_id, type, kind, host, " +
                            "CASE WHEN redirection IS NULL THEN 1 ELSE 0 END, " +
                            "COALESCE(redirection, '')");
            markStatement.bindLong(1, sourceId);
            markStatement.bindLong(2, newGeneration);
            markStatement.executeUpdateDelete();

            this.db.setTransactionSuccessful();
            return copied;
        } finally {
            this.db.endTransaction();
        }
    }

    void drop() {
        this.db.execSQL("DROP TABLE IF EXISTS " + PENDING_TABLE);
        this.db.execSQL("DROP TABLE IF EXISTS " + TABLE);
    }

    static void stagePending(
            @NonNull SupportSQLiteStatement statement,
            @NonNull HostListItem item,
            int generation) {
        statement.clearBindings();
        statement.bindLong(1, item.getSourceId());
        statement.bindString(2, item.getHost());
        statement.bindString(3, item.getReverseHost());
        ListType type = item.getType();
        statement.bindLong(4, type != null ? type.getValue() : 0);
        RuleKind kind = item.getKind();
        statement.bindLong(5, kind != null ? kind.getValue() : EXACT.getValue());
        statement.bindLong(6, item.isEnabled() ? 1L : 0L);
        String redirection = item.getRedirection();
        if (redirection != null) {
            statement.bindString(7, redirection);
        } else {
            statement.bindNull(7);
        }
        statement.bindLong(8, redirection == null ? 1L : 0L);
        statement.bindString(9, redirection != null ? redirection : "");
        statement.bindLong(10, generation);
        statement.executeInsert();
    }
}
