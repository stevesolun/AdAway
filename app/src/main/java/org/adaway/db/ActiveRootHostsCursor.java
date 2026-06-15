package org.adaway.db;

import static org.adaway.db.entity.ListType.REDIRECTED;

import android.database.AbstractCursor;
import android.database.Cursor;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.adaway.util.Hostnames;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * Forward-only root hosts cursor backed by prioritized active hosts-list scans.
 *
 * Large updates use this cursor to avoid rebuilding a million-row root export cache during sync.
 */
public final class ActiveRootHostsCursor extends AbstractCursor {
    private static final String[] COLUMNS = {"host", "type", "redirection"};

    private final List<Cursor> mCursors;
    private final List<Boolean> mTrackResolvedHosts;
    private final Set<String> mResolvedHosts = new HashSet<>();
    private final Set<String> mExactAllowedHosts;
    private final SuffixAllowMatcher mSuffixAllowMatcher;
    private final List<Pattern> mWildcardAllowedHosts;
    private int mCursorIndex;
    private int mCursorReadCount;
    private int mCursorEmittedCount;
    private int mCursorDuplicateSkipCount;
    private int mCursorAllowedSkipCount;
    private int mEmittedCount;
    private long mCursorStartedAtMs;
    private boolean mExhausted;
    private String mCurrentHost;
    private int mCurrentType;
    private String mCurrentRedirection;

    public ActiveRootHostsCursor(
            @NonNull List<Cursor> cursors,
            @NonNull List<Boolean> trackResolvedHosts,
            @NonNull Collection<String> exactAllowedHosts,
            @NonNull Collection<String> suffixAllowedHosts,
            @NonNull List<Pattern> wildcardAllowedHosts) {
        this.mCursors = cursors;
        this.mTrackResolvedHosts = trackResolvedHosts;
        this.mExactAllowedHosts = normalizedSet(exactAllowedHosts);
        this.mSuffixAllowMatcher = suffixAllowedHosts.isEmpty()
                ? null : new SuffixAllowMatcher(suffixAllowedHosts);
        this.mWildcardAllowedHosts = wildcardAllowedHosts;
        this.mCursorStartedAtMs = SystemClock.elapsedRealtime();
    }

    @Override
    public int getCount() {
        return this.mExhausted ? this.mEmittedCount : Integer.MAX_VALUE;
    }

    @Override
    public String[] getColumnNames() {
        return COLUMNS.clone();
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        if (newPosition < oldPosition) {
            return false;
        }
        while (oldPosition < newPosition) {
            if (!moveForwardOneRow()) {
                return false;
            }
            oldPosition++;
        }
        return true;
    }

    @Override
    public String getString(int columnIndex) {
        if (columnIndex == 0) {
            return this.mCurrentHost;
        }
        if (columnIndex == 2) {
            return this.mCurrentRedirection;
        }
        return Integer.toString(this.mCurrentType);
    }

    @Override
    public short getShort(int columnIndex) {
        return (short) getInt(columnIndex);
    }

    @Override
    public int getInt(int columnIndex) {
        if (columnIndex == 1) {
            return this.mCurrentType;
        }
        return Integer.parseInt(getString(columnIndex));
    }

    @Override
    public long getLong(int columnIndex) {
        return getInt(columnIndex);
    }

    @Override
    public float getFloat(int columnIndex) {
        return getInt(columnIndex);
    }

    @Override
    public double getDouble(int columnIndex) {
        return getInt(columnIndex);
    }

    @Override
    public boolean isNull(int columnIndex) {
        return columnIndex == 2 && this.mCurrentRedirection == null;
    }

    @Override
    public void close() {
        for (Cursor cursor : this.mCursors) {
            cursor.close();
        }
        super.close();
    }

    private boolean moveForwardOneRow() {
        while (this.mCursorIndex < this.mCursors.size()) {
            Cursor cursor = this.mCursors.get(this.mCursorIndex);
            while (cursor.moveToNext()) {
                this.mCursorReadCount++;
                String host = cursor.getString(0);
                if (!this.mResolvedHosts.isEmpty() && this.mResolvedHosts.contains(host)) {
                    this.mCursorDuplicateSkipCount++;
                    continue;
                }

                int type = cursor.getInt(1);
                if (this.mTrackResolvedHosts.get(this.mCursorIndex)) {
                    this.mResolvedHosts.add(host);
                }
                if (type != REDIRECTED.getValue() && isAllowed(host)) {
                    this.mCursorAllowedSkipCount++;
                    continue;
                }

                this.mCurrentHost = host;
                this.mCurrentType = type;
                this.mCurrentRedirection = cursor.getString(2);
                this.mCursorEmittedCount++;
                this.mEmittedCount++;
                return true;
            }
            logCursorCompleted();
            this.mCursorIndex++;
            this.mCursorStartedAtMs = SystemClock.elapsedRealtime();
            this.mCursorReadCount = 0;
            this.mCursorEmittedCount = 0;
            this.mCursorDuplicateSkipCount = 0;
            this.mCursorAllowedSkipCount = 0;
        }

        this.mCurrentHost = null;
        this.mCurrentRedirection = null;
        this.mExhausted = true;
        return false;
    }

    private void logCursorCompleted() {
        Timber.i("ActiveRootHostsCursor phase=%d read=%d emitted=%d duplicateSkipped=%d " +
                        "allowedSkipped=%d resolvedHosts=%d ms=%d",
                this.mCursorIndex,
                this.mCursorReadCount,
                this.mCursorEmittedCount,
                this.mCursorDuplicateSkipCount,
                this.mCursorAllowedSkipCount,
                this.mResolvedHosts.size(),
                SystemClock.elapsedRealtime() - this.mCursorStartedAtMs);
    }

    private boolean isAllowed(String host) {
        if (this.mExactAllowedHosts.contains(host)) {
            return true;
        }
        if (this.mSuffixAllowMatcher != null && this.mSuffixAllowMatcher.matches(host)) {
            return true;
        }
        for (Pattern pattern : this.mWildcardAllowedHosts) {
            if (pattern.matcher(host).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalizedSet(Collection<String> hosts) {
        Set<String> normalized = new HashSet<>(hosts.size());
        for (String host : hosts) {
            normalized.add(Hostnames.normalize(host));
        }
        return normalized;
    }

    private static final class SuffixAllowMatcher {
        private final Node mRoot = new Node();

        SuffixAllowMatcher(Collection<String> suffixes) {
            for (String suffix : suffixes) {
                add(Hostnames.normalize(suffix));
            }
        }

        boolean matches(String host) {
            Node node = this.mRoot;
            int end = host.length();
            while (end > 0) {
                int dot = host.lastIndexOf('.', end - 1);
                int start = dot < 0 ? 0 : dot + 1;
                node = node.mChildren.get(host.substring(start, end));
                if (node == null) {
                    return false;
                }
                if (node.mTerminal) {
                    return true;
                }
                if (dot < 0) {
                    return false;
                }
                end = dot;
            }
            return false;
        }

        private void add(String suffix) {
            Node node = this.mRoot;
            int end = suffix.length();
            while (end > 0) {
                int dot = suffix.lastIndexOf('.', end - 1);
                int start = dot < 0 ? 0 : dot + 1;
                String label = suffix.substring(start, end);
                Node child = node.mChildren.get(label);
                if (child == null) {
                    child = new Node();
                    node.mChildren.put(label, child);
                }
                node = child;
                if (dot < 0) {
                    break;
                }
                end = dot;
            }
            node.mTerminal = true;
        }
    }

    private static final class Node {
        private final Map<String, Node> mChildren = new HashMap<>();
        private boolean mTerminal;
    }
}
