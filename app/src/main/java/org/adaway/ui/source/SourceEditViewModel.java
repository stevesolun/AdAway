package org.adaway.ui.source;

import android.app.Application;
import android.database.sqlite.SQLiteException;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.FilterListsSourceMetadata;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.util.AppExecutors;

/** Owns one editor's save/delete work and its result across configuration changes. */
public class SourceEditViewModel extends AndroidViewModel {
    enum Status {
        IDLE(0),
        PENDING(0),
        SUCCEEDED(0),
        DUPLICATE_URL(R.string.source_edit_duplicate_url),
        UPDATE_IN_PROGRESS(R.string.error_update_in_progress_message),
        FAILED(R.string.source_edit_save_failed);

        @StringRes
        final int mError;

        Status(@StringRes int error) {
            this.mError = error;
        }
    }

    private final AppDatabase mDatabase;
    private final HostsSourceDao mHostsSourceDao;
    private final MutableLiveData<Status> mStatus = new MutableLiveData<>(Status.IDLE);

    public SourceEditViewModel(@NonNull Application application) {
        super(application);
        this.mDatabase = AppDatabase.getInstance(application);
        this.mHostsSourceDao = this.mDatabase.hostsSourceDao();
    }

    LiveData<Status> getStatus() {
        return this.mStatus;
    }

    @UiThread
    boolean isMutationBlocked() {
        // Success remains terminal until the current editor can observe it and finish.
        return this.mStatus.getValue() == Status.PENDING
                || this.mStatus.getValue() == Status.SUCCEEDED;
    }

    @UiThread
    void saveSource(int editedId, HostsSource values) {
        if (isMutationBlocked()) return;
        this.mStatus.setValue(Status.PENDING);
        AppExecutors.getInstance().diskIO().execute(() -> {
            boolean[] needsDownload = {false};
            boolean[] runtimeChanged = {false};
            String[] previousUrl = {null};
            int error;
            SourceModel sourceModel = ((AdAwayApplication) getApplication()).getSourceModel();
            try {
                error = sourceModel.runSourceMutation(() -> {
                    int result = this.mDatabase.runInTransaction(() -> {
                        HostsSource duplicate = this.mHostsSourceDao.getByUrl(values.getUrl())
                                .orElse(null);
                        if (duplicate != null && duplicate.getId() != editedId) {
                            return R.string.source_edit_duplicate_url;
                        }
                        if (editedId == -1) {
                            this.mHostsSourceDao.insert(values);
                            needsDownload[0] = true;
                            return 0;
                        }
                        HostsSource current = this.mHostsSourceDao.getById(editedId).orElse(null);
                        if (current == null || editedId == HostsSource.USER_SOURCE_ID) {
                            return R.string.source_edit_save_failed;
                        }
                        boolean contentChanged = !current.getUrl().equals(values.getUrl())
                                || current.isAllowEnabled() != values.isAllowEnabled()
                                || current.isRedirectEnabled() != values.isRedirectEnabled();
                        if (!current.getUrl().equals(values.getUrl())) {
                            previousUrl[0] = current.getUrl();
                        }
                        // Updating in place preserves selection, rules and HTTP validators
                        // for label-only edits. Recreating the row cascaded away all imported rules.
                        current.setLabel(values.getLabel());
                        current.setUrl(values.getUrl());
                        current.setAllowEnabled(values.isAllowEnabled());
                        current.setRedirectEnabled(values.isRedirectEnabled());
                        FilterListsSourceMetadata.copy(values, current);
                        this.mHostsSourceDao.update(current);
                        if (contentChanged) {
                            this.mDatabase.hostsListItemDao().clearSourceHosts(editedId);
                            this.mDatabase.getOpenHelper().getWritableDatabase().execSQL(
                                    "DELETE FROM root_host_entries_stage WHERE source_id = ?",
                                    new Object[]{editedId});
                            this.mHostsSourceDao.clearProperties(editedId);
                            this.mHostsSourceDao.updateSkippedCount(editedId, 0);
                            this.mHostsSourceDao.clearDownloadError(editedId);
                            this.mDatabase.hostEntryDao().invalidateRootExportMaterializedCache();
                            runtimeChanged[0] = true;
                            needsDownload[0] = current.isEnabled();
                        }
                        return 0;
                    });
                    if (result == 0 && runtimeChanged[0]) {
                        sourceModel.syncHostEntries();
                    }
                    return result;
                });
            } catch (HostErrorException exception) {
                error = R.string.error_update_in_progress_message;
            } catch (SQLiteException exception) {
                error = R.string.source_edit_save_failed;
            }
            if (error == 0) {
                if (previousUrl[0] != null) {
                    SourceScheduleStore.clear(getApplication(), previousUrl[0]);
                }
                FilterSetStore.markCustomProfile(getApplication());
                if (needsDownload[0]) {
                    SourceUpdateService.enqueueUpdateNow(getApplication());
                }
            }
            postMutationResult(error);
        });
    }

    @UiThread
    void deleteSource(int sourceId) {
        if (isMutationBlocked()) return;
        this.mStatus.setValue(Status.PENDING);
        AppExecutors.getInstance().diskIO().execute(() -> {
            SourceModel sourceModel = ((AdAwayApplication) getApplication()).getSourceModel();
            String[] deletedUrl = {null};
            int error;
            try {
                error = sourceModel.runSourceMutation(() -> {
                    int result = this.mDatabase.runInTransaction(() -> {
                        HostsSource current = this.mHostsSourceDao.getById(sourceId)
                                .orElse(null);
                        if (current == null || current.getId() == HostsSource.USER_SOURCE_ID) {
                            return R.string.source_edit_save_failed;
                        }
                        deletedUrl[0] = current.getUrl();
                        this.mHostsSourceDao.delete(current);
                        this.mDatabase.getOpenHelper().getWritableDatabase().execSQL(
                                "DELETE FROM root_host_entries_stage WHERE source_id = ?",
                                new Object[]{current.getId()});
                        this.mDatabase.hostEntryDao().invalidateRootExportMaterializedCache();
                        return 0;
                    });
                    if (result == 0) sourceModel.syncHostEntries();
                    return result;
                });
            } catch (HostErrorException exception) {
                error = R.string.error_update_in_progress_message;
            } catch (SQLiteException exception) {
                error = R.string.source_edit_save_failed;
            }
            if (error == 0) {
                SourceScheduleStore.clear(getApplication(), deletedUrl[0]);
                FilterSetStore.markCustomProfile(getApplication());
            }
            postMutationResult(error);
        });
    }

    private void postMutationResult(int error) {
        Status result;
        if (error == 0) {
            result = Status.SUCCEEDED;
        } else if (error == R.string.source_edit_duplicate_url) {
            result = Status.DUPLICATE_URL;
        } else if (error == R.string.error_update_in_progress_message) {
            result = Status.UPDATE_IN_PROGRESS;
        } else {
            result = Status.FAILED;
        }
        AppExecutors.getInstance().mainThread().execute(() -> this.mStatus.setValue(result));
    }
}
