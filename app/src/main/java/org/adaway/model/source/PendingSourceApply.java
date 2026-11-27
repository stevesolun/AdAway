package org.adaway.model.source;

import android.content.SharedPreferences;

import java.util.Objects;

public final class PendingSourceApply {
    private static final String FINGERPRINT = "fingerprint";
    private static final String SELECTION = "selection";
    private static final String UNCONFIRMED = "unconfirmed";
    private final SharedPreferences preferences;
    private int pendingSelections;
    private long selectionRevision;
    private String settledSelection;

    public PendingSourceApply(SharedPreferences preferences) {
        this.preferences = preferences;
        if (UNCONFIRMED.equals(preferences.getString(SELECTION, null))) {
            preferences.edit().putString(FINGERPRINT, UNCONFIRMED).remove(SELECTION).apply();
        }
    }

    public synchronized String read() {
        return preferences.getString(FINGERPRINT, null);
    }

    public synchronized void beginSelection(SourceApplyState state, boolean running) {
        if (pendingSelections == 0) {
            settledSelection = preferences.getString(SELECTION, null);
        }
        pendingSelections++;
        selectionRevision++;
        String checkpoint = read();
        if (checkpoint == null) checkpoint = state.pendingCheckpoint(true, running);
        preferences.edit().putString(FINGERPRINT, checkpoint)
                .putString(SELECTION, UNCONFIRMED).apply();
    }

    public synchronized void finishSelection(String configuration) {
        settledSelection = configuration == null ? UNCONFIRMED : configuration;
        cancelSelection();
    }

    public synchronized void cancelSelection() {
        pendingSelections--;
        if (pendingSelections == 0) {
            preferences.edit().putString(SELECTION, settledSelection).apply();
        }
    }

    public synchronized long settledSelectionRevision() {
        return pendingSelections == 0 ? selectionRevision : -1;
    }

    public synchronized void confirmSelection(long revision, String configuration) {
        if (configuration != null && revision >= 0 && revision == selectionRevision
                && pendingSelections == 0
                && preferences.getString(SELECTION, null) != null) {
            preferences.edit().putString(SELECTION, configuration).apply();
        }
    }

    public synchronized String save(SourceApplyState state, String observedConfiguration,
                                    boolean running) {
        if (running) {
            if (!UNCONFIRMED.equals(read())) {
                preferences.edit().putString(FINGERPRINT, UNCONFIRMED).apply();
            }
            return UNCONFIRMED;
        }
        if (pendingSelections != 0) return read();
        String selection = preferences.getString(SELECTION, null);
        if (UNCONFIRMED.equals(selection)) {
            state.invalidate();
        } else if (selection != null && !selection.equals(observedConfiguration)) {
            return read();
        }
        String next = state.pendingCheckpoint(false, running);
        if (selection != null || !Objects.equals(read(), next)) {
            preferences.edit().putString(FINGERPRINT, next).remove(SELECTION).apply();
        }
        return next;
    }
}
