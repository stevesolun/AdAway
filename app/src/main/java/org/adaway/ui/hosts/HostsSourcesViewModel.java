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
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceApplyState;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.util.AppExecutors;
import org.adaway.vpn.VpnServiceControls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    private final SourceApplyState mApplyState;
    private final MutableLiveData<ApplyStatus> mApplyStatus =
            new MutableLiveData<>(ApplyStatus.IDLE);
    private final MutableLiveData<Set<Integer>> failedSelections =
            new MutableLiveData<>(Collections.emptySet());
    @Nullable
    private Integer mUpdateSourceId;
    private String mObservedConfiguration;
    private long mConfigurationRevision;

    public HostsSourcesViewModel(@NonNull Application application) {
        this(application, new SavedStateHandle());
    }

    public HostsSourcesViewModel(@NonNull Application application, @NonNull SavedStateHandle state) {
        super(application);
        this.hostsSourceDao = AppDatabase.getInstance(application).hostsSourceDao();
        Bundle baseline = state.get(APPLY_BASELINE);
        mApplyState = baseline == null ? new SourceApplyState() : new SourceApplyState(
                baseline.getString(FINGERPRINT), baseline.getBoolean(UNCERTAIN),
                baseline.getString(DOWNLOADED_WORK));
        state.setSavedStateProvider(APPLY_BASELINE, () -> {
            Bundle saved = new Bundle();
            saved.putString(FINGERPRINT, mApplyState.getAppliedConfiguration());
            saved.putBoolean(UNCERTAIN, mApplyState.isUncertainOnRestore()
                    || mApplyStatus.getValue() == ApplyStatus.RUNNING);
            if (mApplyStatus.getValue() != ApplyStatus.RUNNING) {
                saved.putString(DOWNLOADED_WORK, mApplyState.getDownloadedWorkToRestore());
            }
            return saved;
        });
    }

    public LiveData<List<HostsSource>> getHostsSources() {
        return this.hostsSourceDao.loadAll();
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
        mApplyStatus.setValue(acknowledgeEstablishedDownload()
                ? ApplyStatus.SUCCEEDED : mApplyStatus.getValue());
    }

    @UiThread
    public void observeAutomaticWork(String workId, boolean successful, String configuration) {
        observeAutomaticWork(workId, successful, configuration, null);
    }

    @UiThread
    void observeAutomaticWork(String workId, boolean successful, String configuration,
                              String downloaded) {
        boolean completed = mApplyState.observeAutomaticWork(workId, successful, configuration, downloaded);
        ApplyStatus status = mApplyStatus.getValue();
        if (completed && status != ApplyStatus.RUNNING) {
            status = downloaded == null || acknowledgeEstablishedDownload()
                    ? ApplyStatus.SUCCEEDED : ApplyStatus.IDLE;
        }
        mApplyStatus.setValue(status);
    }

    @UiThread
    void onVpnStateChanged() {
        if (acknowledgeEstablishedDownload()) mApplyStatus.setValue(ApplyStatus.SUCCEEDED);
    }

    private boolean acknowledgeEstablishedDownload() {
        return mApplyStatus.getValue() != ApplyStatus.RUNNING && mApplyState.hasMatchingDownload()
                && mApplyState.acknowledgeDownload(isVpnTunnelEstablished());
    }

    boolean isVpnTunnelEstablished() {
        return PreferenceHelper.getAdBlockMethod(getApplication()) == AdBlockMethod.VPN
                && VpnServiceControls.isTunnelEstablished(getApplication());
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
        mApplyStatus.setValue(ApplyStatus.RUNNING);
        try {
            AppExecutors.getInstance().networkIO().execute(() -> {
                boolean successful = false;
                boolean requiresTunnel = false;
                List<HostsSource> appliedSources = null;
                try (SourceOperationCoordinator.Lease ignored = lease) {
                    List<HostsSource> snapshot = hostsSourceDao.getAll();
                    String configuration = SourceApplyState.fingerprint(snapshot);
                    SourceModel sourceModel = application.getSourceModel();
                    AdBlockModel adBlockModel = application.getAdBlockModel();
                    boolean shouldApply = true;
                    if (sourceId == null) {
                        shouldApply = sourceModel.checkAndRetrieveHostsSources();
                    } else {
                        sourceModel.retrieveHostsSource(sourceId);
                    }
                    if (shouldApply) {
                        adBlockModel.apply();
                        requiresTunnel = adBlockModel.getMethod() == AdBlockMethod.VPN;
                        if (configuration.equals(
                                SourceApplyState.fingerprint(hostsSourceDao.getAll()))) {
                            appliedSources = snapshot;
                        }
                    }
                    successful = shouldApply;
                } catch (HostErrorException | RuntimeException exception) {
                    Timber.w(exception, "Failed to update and apply sources.");
                }
                boolean result = successful;
                boolean waitingForTunnel = requiresTunnel;
                List<HostsSource> snapshot = appliedSources;
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (snapshot != null && revision == mConfigurationRevision) {
                        if (waitingForTunnel) {
                            mApplyState.downloadedSources(snapshot, sourceId);
                        } else {
                            mApplyState.appliedSources(snapshot, sourceId);
                        }
                    } else if (result) {
                        mApplyState.invalidate();
                    }
                    mApplyStatus.setValue(!result ? ApplyStatus.FAILED
                            : waitingForTunnel ? ApplyStatus.IDLE : ApplyStatus.SUCCEEDED);
                    if (result && waitingForTunnel) onVpnStateChanged();
                });
            });
        } catch (RuntimeException exception) {
            lease.close();
            mApplyStatus.setValue(ApplyStatus.FAILED);
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
    }

    @UiThread
    void setGroupEnabled(List<Integer> sourceIds, boolean enabled) {
        if (sourceIds.isEmpty()) return;
        List<Integer> ids = new ArrayList<>(sourceIds);
        mConfigurationRevision++;
        try {
            AppExecutors.getInstance().diskIO().execute(() -> {
                try {
                    hostsSourceDao.applyGroupSelection(ids, enabled);
                } catch (RuntimeException error) {
                    Timber.w(error, "Could not save source selection.");
                    AppExecutors.getInstance().mainThread().execute(() -> selectionFinished(ids, false));
                    return;
                }
                FilterSetStore.markCustomProfile(getApplication());
                AppExecutors.getInstance().mainThread().execute(() -> selectionFinished(ids, true));
            });
        } catch (java.util.concurrent.RejectedExecutionException error) {
            Timber.w(error, "Could not schedule source selection.");
            selectionFinished(ids, false);
        }
    }
}
