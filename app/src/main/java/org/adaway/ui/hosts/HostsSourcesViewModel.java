package org.adaway.ui.hosts;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.PendingSourceApply;
import org.adaway.model.source.SourceApplyState;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.util.AppExecutors;
import org.adaway.vpn.VpnServiceControls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import timber.log.Timber;

/**
 * This class is an {@link AndroidViewModel} for the {@link HostsSourcesFragment}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsSourcesViewModel extends AndroidViewModel {
    public enum ApplyStatus { IDLE, BUSY, RUNNING, FAILED, SUCCEEDED }

    private final HostsSourceDao hostsSourceDao;
    private static final String APPLY_BASELINE = "sources_apply_baseline";
    private static final String FINGERPRINT = "fingerprint";
    private static final String UNCERTAIN = "uncertain";
    private static final String DOWNLOADED_WORK = "downloaded_work";
    private static final String CHECKPOINT = "checkpoint";
    private final PendingSourceApply mPendingApply;
    private String mCheckpoint;
    private boolean mCleared;
    private final SourceApplyState mApplyState;
    private final MutableLiveData<ApplyStatus> mApplyStatus =
            new MutableLiveData<>(ApplyStatus.IDLE);
    private final MutableLiveData<Set<Integer>> failedSelections =
            new MutableLiveData<>(Collections.emptySet());
    private final MutableLiveData<Integer> metadataCheckMessage = new MutableLiveData<>();
    @Nullable
    private Integer mUpdateSourceId;
    private String mObservedConfiguration;
    private long mConfigurationRevision;
    private String mAutomaticWork;
    private long mAutomaticSelectionRevision = -1;

    public HostsSourcesViewModel(@NonNull Application application) {
        this(application, new SavedStateHandle());
    }

    public HostsSourcesViewModel(@NonNull Application application, @NonNull SavedStateHandle state) {
        super(application);
        this.hostsSourceDao = AppDatabase.getInstance(application).hostsSourceDao();
        mPendingApply = ((AdAwayApplication) application).getPendingSourceApply();
        mCheckpoint = mPendingApply.read();
        Bundle baseline = state.get(APPLY_BASELINE);
        if (baseline != null && Objects.equals(mCheckpoint, baseline.getString(CHECKPOINT))) {
            mApplyState = new SourceApplyState(baseline.getString(FINGERPRINT),
                    baseline.getBoolean(UNCERTAIN), baseline.getString(DOWNLOADED_WORK));
        } else {
            mApplyState = SourceApplyState.fromPendingCheckpoint(mCheckpoint);
        }
        state.setSavedStateProvider(APPLY_BASELINE, () -> {
            Bundle saved = new Bundle();
            saved.putString(CHECKPOINT, mCheckpoint);
            saved.putString(FINGERPRINT, mApplyState.getAppliedConfiguration());
            saved.putBoolean(UNCERTAIN, mApplyState.isUncertainOnRestore()
                    || mApplyStatus.getValue() == ApplyStatus.RUNNING);
            if (mApplyStatus.getValue() != ApplyStatus.RUNNING) {
                saved.putString(DOWNLOADED_WORK, mApplyState.getDownloadedWorkToRestore());
            }
            return saved;
        });
    }

    private void checkpoint(ApplyStatus status) {
        if (mCleared) return;
        mCheckpoint = mPendingApply.save(mApplyState, mObservedConfiguration,
                status == ApplyStatus.RUNNING);
    }

    private void publishStatus(ApplyStatus status) {
        checkpoint(status);
        mApplyStatus.setValue(status);
    }

    @Override
    protected void onCleared() {
        mCleared = true; // A late completion must not write over the next task's checkpoint.
        super.onCleared();
    }

    public LiveData<List<HostsSource>> getHostsSources() {
        return this.hostsSourceDao.loadAll();
    }

    LiveData<Integer> getMetadataCheckMessage() {
        return metadataCheckMessage;
    }

    @UiThread
    void dismissMetadataCheck() {
        if (!Objects.equals(metadataCheckMessage.getValue(), R.string.status_check)) {
            metadataCheckMessage.setValue(null);
        }
    }

    @UiThread
    void checkForSourceUpdates() {
        if (Objects.equals(metadataCheckMessage.getValue(), R.string.status_check)) return;
        metadataCheckMessage.setValue(R.string.status_check);
        AdAwayApplication application = getApplication();
        AppExecutors.getInstance().networkIO().execute(() -> {
            int message;
            try {
                message = application.getSourceModel().checkForUpdate()
                        ? R.string.status_update_available : R.string.status_no_update_found;
            } catch (HostErrorException e) {
                message = e.getError().getMessageKey();
            } catch (RuntimeException e) {
                Timber.w(e, "Could not complete the manual source update check.");
                message = R.string.status_source_check_failed;
            }
            metadataCheckMessage.postValue(message);
        });
    }

    public SourceApplyState getApplyState() {
        return mApplyState;
    }

    public LiveData<ApplyStatus> getApplyStatus() {
        return mApplyStatus;
    }

    @UiThread
    public void onSourcesChanged(List<HostsSource> sources) {
        if (sources != null) {
            Set<Integer> failed = failedSelections.getValue();
            if (failed != null && !failed.isEmpty()) {
                Set<Integer> remaining = new HashSet<>();
                for (HostsSource source : sources) {
                    if (failed.contains(source.getId())) remaining.add(source.getId());
                }
                if (remaining.size() != failed.size()) failedSelections.setValue(remaining);
            }
            String configuration = SourceApplyState.fingerprint(sources);
            if (!configuration.equals(mObservedConfiguration)) {
                if (mObservedConfiguration != null) {
                    mConfigurationRevision++;
                }
                mObservedConfiguration = configuration;
            }
        }
        mApplyState.updateSources(sources);
        publishStatus(acknowledgeEstablishedDownload()
                ? ApplyStatus.SUCCEEDED : mApplyStatus.getValue());
    }

    @UiThread
    public void observeAutomaticWork(String workId, boolean successful, String configuration) {
        observeAutomaticWork(workId, successful, configuration, null);
    }

    @UiThread
    void observeAutomaticWork(String workId, boolean successful, String configuration,
                              String downloaded) {
        if (workId != null && !workId.equals(mAutomaticWork)) {
            mAutomaticWork = workId;
            mAutomaticSelectionRevision = mPendingApply.settledSelectionRevision();
        }
        boolean completed = mApplyState.observeAutomaticWork(
                workId, successful, configuration, downloaded);
        ApplyStatus status = mApplyStatus.getValue();
        if (completed && status != ApplyStatus.RUNNING) {
            if (configuration != null) reconcileAutomaticSelection(configuration);
            status = downloaded == null || acknowledgeEstablishedDownload()
                    ? ApplyStatus.SUCCEEDED : ApplyStatus.IDLE;
        }
        publishStatus(status);
    }

    @UiThread
    void onVpnStateChanged() {
        if (acknowledgeEstablishedDownload()) publishStatus(ApplyStatus.SUCCEEDED);
    }

    private boolean acknowledgeEstablishedDownload() {
        if (mApplyStatus.getValue() == ApplyStatus.RUNNING || !mApplyState.hasMatchingDownload()) {
            return false;
        }
        String work = mApplyState.getDownloadedWorkToRestore();
        if (!mApplyState.acknowledgeDownload(isVpnTunnelEstablished())) return false;
        if (work != null && work.equals(mAutomaticWork)) {
            reconcileAutomaticSelection(mApplyState.getAppliedConfiguration());
        }
        return true;
    }

    private void reconcileAutomaticSelection(String configuration) {
        if (mCleared) return;
        long revision = mAutomaticSelectionRevision;
        Runnable retireGuard = mPendingApply.recordAutomaticBaseline(revision, configuration);
        if (retireGuard == null) return;
        String work = mAutomaticWork;
        // A late WorkInfo and stale LiveData can agree despite a newer committed selection.
        AppExecutors executors = AppExecutors.getInstance();
        Runnable verify = () -> {
            try {
                if (!configuration.equals(SourceApplyState.fingerprint(hostsSourceDao.getAll()))) return;
            } catch (RuntimeException error) {
                Timber.w(error, "Could not verify automatic source selection.");
                return;
            }
            retireGuard.run();
            executors.mainThread().execute(() -> {
                if (mCleared || !work.equals(mAutomaticWork)
                        || mApplyStatus.getValue() == ApplyStatus.RUNNING
                        || mApplyState.isUncertainOnRestore()
                        || revision != mPendingApply.settledSelectionRevision()
                        || !configuration.equals(mApplyState.getAppliedConfiguration())) return;
                checkpoint(mApplyStatus.getValue());
            });
        };
        try {
            executors.diskIO().execute(verify);
        } catch (java.util.concurrent.RejectedExecutionException error) {
            Timber.w(error, "Could not schedule automatic source selection verification.");
        }
    }

    boolean isVpnTunnelEstablished() {
        return PreferenceHelper.getAdBlockMethod(getApplication()) == AdBlockMethod.VPN
                && VpnServiceControls.isTunnelEstablished(getApplication());
    }

    @UiThread
    void beginSavedSetSelection() {
        mConfigurationRevision++;
        mPendingApply.beginSelection(mApplyState, mApplyStatus.getValue() == ApplyStatus.RUNNING);
        checkpoint(mApplyStatus.getValue());
    }

    void finishSavedSetSelection(boolean committed) {
        if (!committed) {
            mPendingApply.cancelSelection();
            return;
        }
        String configuration = null;
        try {
            configuration = SourceApplyState.fingerprint(hostsSourceDao.getAll());
        } catch (RuntimeException error) {
            Timber.w(error, "Could not read committed saved-set selection.");
        }
        mPendingApply.finishSelection(configuration);
    }

    @UiThread
    void savedSetSelectionFinished() {
        if (!mCleared) publishStatus(mApplyStatus.getValue());
    }

    @UiThread
    public void retry() {
        updateSources(mUpdateSourceId);
    }

    @UiThread
    public void updateSources(@Nullable Integer sourceId) {
        if (mApplyStatus.getValue() == ApplyStatus.RUNNING) {
            return;
        }
        mUpdateSourceId = sourceId;
        long revision = mConfigurationRevision;
        AdAwayApplication application = getApplication();
        SourceOperationCoordinator.Lease lease = application.getSourceOperations().tryAcquire();
        if (lease == null) {
            mApplyStatus.setValue(ApplyStatus.BUSY);
            return;
        }
        publishStatus(ApplyStatus.RUNNING);
        try {
            AppExecutors.getInstance().networkIO().execute(() -> {
                long selectionRevision = mPendingApply.settledSelectionRevision();
                boolean imported = false;
                boolean successful = false;
                boolean requiresTunnel = false;
                List<HostsSource> appliedSources = null;
                try (SourceOperationCoordinator.Lease ignored = lease) {
                    List<HostsSource> snapshot = hostsSourceDao.getAll();
                    String configuration = SourceApplyState.fingerprint(snapshot);
                    SourceModel sourceModel = application.getSourceModel();
                    AdBlockModel adBlockModel = application.getAdBlockModel();
                    String expectedConfiguration = configuration;
                    String importedConfiguration;
                    if (sourceId == null) {
                        importedConfiguration = sourceModel.retrieveHostsSourcesWithReceipt();
                    } else {
                        expectedConfiguration = snapshot.stream()
                                .filter(source -> source.getId() == sourceId)
                                .findFirst()
                                .map(source -> SourceApplyState.fingerprint(Collections.singletonList(source)))
                                .orElse(null);
                        importedConfiguration = sourceModel.retrieveHostsSourceWithReceipt(sourceId);
                    }
                    imported = importedConfiguration != null;
                    AdBlockMethod method = adBlockModel.getMethod();
                    boolean shouldApply = imported && method != AdBlockMethod.UNDEFINED;
                    if (shouldApply) {
                        adBlockModel.apply();
                        requiresTunnel = method == AdBlockMethod.VPN;
                        if (importedConfiguration.equals(expectedConfiguration) && configuration.equals(
                                SourceApplyState.fingerprint(hostsSourceDao.getAll()))) {
                            appliedSources = snapshot;
                        }
                    }
                    successful = shouldApply;
                } catch (HostErrorException | RuntimeException exception) {
                    Timber.w(exception, "Failed to update and apply sources.");
                }
                boolean result = successful;
                boolean rulesImported = imported;
                boolean waitingForTunnel = requiresTunnel;
                List<HostsSource> snapshot = appliedSources;
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (snapshot != null && revision == mConfigurationRevision) {
                        if (!mCleared && sourceId == null) {
                            mPendingApply.confirmSelection(selectionRevision,
                                    SourceApplyState.fingerprint(snapshot));
                        }
                        if (waitingForTunnel) {
                            mApplyState.downloadedSources(snapshot, sourceId);
                        } else {
                            mApplyState.appliedSources(snapshot, sourceId);
                        }
                    } else if (rulesImported) {
                        // The import may already have replaced rules even if protection failed.
                        mApplyState.invalidate();
                    }
                    publishStatus(!result ? ApplyStatus.FAILED
                            : waitingForTunnel ? ApplyStatus.IDLE : ApplyStatus.SUCCEEDED);
                    if (result && waitingForTunnel) onVpnStateChanged();
                });
            });
        } catch (RuntimeException exception) {
            lease.close();
            publishStatus(ApplyStatus.FAILED);
            Timber.w(exception, "Failed to schedule source update.");
        }
    }

    public void setSourceEnabled(HostsSource source, boolean enabled) {
        setGroupEnabled(Collections.singletonList(source.getId()), enabled);
    }

    LiveData<Set<Integer>> getFailedSelections() {
        return failedSelections;
    }

    @UiThread
    private void selectionFinished(List<Integer> ids, boolean succeeded) {
        Set<Integer> next = new HashSet<>(failedSelections.getValue());
        boolean changed = succeeded ? next.removeAll(ids) : next.addAll(ids);
        if (changed || !succeeded) failedSelections.setValue(next);
        publishStatus(mApplyStatus.getValue());
    }

    @UiThread
    void setGroupEnabled(List<Integer> sourceIds, boolean enabled) {
        if (sourceIds.isEmpty()) return;
        List<Integer> ids = new ArrayList<>(sourceIds);
        mConfigurationRevision++;
        mPendingApply.beginSelection(mApplyState, mApplyStatus.getValue() == ApplyStatus.RUNNING);
        checkpoint(mApplyStatus.getValue());
        try {
            AppExecutors.getInstance().diskIO().execute(() -> {
                boolean succeeded = false;
                try {
                    hostsSourceDao.applyGroupSelection(ids, enabled);
                    succeeded = true;
                    FilterSetStore.markCustomProfile(getApplication());
                } catch (RuntimeException error) {
                    Timber.w(error, "Could not save source selection.");
                } finally {
                    String configuration = null;
                    try {
                        configuration = SourceApplyState.fingerprint(hostsSourceDao.getAll());
                    } catch (RuntimeException error) {
                        Timber.w(error, "Could not read settled source selection.");
                    }
                    mPendingApply.finishSelection(configuration);
                }
                boolean result = succeeded;
                AppExecutors.getInstance().mainThread().execute(() -> selectionFinished(ids, result));
            });
        } catch (java.util.concurrent.RejectedExecutionException error) {
            mPendingApply.cancelSelection();
            Timber.w(error, "Could not schedule source selection.");
            selectionFinished(ids, false);
        }
    }
}
