package org.adaway.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.MainThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.OperationStatus;
import org.adaway.model.adblocking.AdBlockModel;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.FilterOperationState;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.model.update.Manifest;
import org.adaway.model.update.UpdateModel;
import org.adaway.model.vpn.VpnModel;
import org.adaway.util.AppExecutors;
import org.adaway.vpn.VpnServiceControls;

import timber.log.Timber;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;

/**
 * This class is an {@link AndroidViewModel} for the {@link HomeActivity} cards.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HomeViewModel extends AndroidViewModel {
    private static final String AUTHORIZED_VPN_ENABLE = "authorized_vpn_enable";

    private final SourceModel sourceModel;
    private final AdBlockModel adBlockModel;
    private final UpdateModel updateModel;
    private final SourceOperationCoordinator sourceOperations;
    private final SavedStateHandle savedState;

    private final HostsSourceDao hostsSourceDao;
    private final HostListItemDao hostListItemDao;
    private final HostEntryDao hostEntryDao;

    private final MutableLiveData<Boolean> pending;
    private final MediatorLiveData<Boolean> homeApplied = new MediatorLiveData<>();
    private final MediatorLiveData<OperationStatus> state;
    private final MutableLiveData<HostError> error;
    private final LiveData<FilterOperationState> filterOperationState;
    private final AtomicBoolean operationPending = new AtomicBoolean();
    private final MutableLiveData<Boolean> vpnConsentPending = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> protectionControlPending = new MutableLiveData<>(false);
    private final Observer<Boolean> operationObserver = ignored -> resumeDeferredOperations();
    private final Observer<FilterOperationState> progressObserver =
            ignored -> resumeDeferredOperations();
    private boolean initialSourcesPreparing;
    private boolean initialUpdateRequested;
    private boolean cleared;
    // Cache initial blocked count to prevent UI race conditions where it resets to 0
    private long cachedInitialBlockedCount = -1;

    public HomeViewModel(@NonNull Application application) {
        this(application, new SavedStateHandle());
    }

    public HomeViewModel(@NonNull Application application, @NonNull SavedStateHandle savedState) {
        super(application);
        AdAwayApplication awayApplication = (AdAwayApplication) application;
        this.sourceModel = awayApplication.getSourceModel();
        this.adBlockModel = awayApplication.getAdBlockModel();
        this.updateModel = awayApplication.getUpdateModel();
        this.sourceOperations = awayApplication.getSourceOperations();
        this.savedState = savedState;

        AppDatabase database = AppDatabase.getInstance(application);
        this.hostsSourceDao = database.hostsSourceDao();
        this.hostListItemDao = database.hostsListItemDao();
        this.hostEntryDao = database.hostEntryDao();

        this.pending = new MutableLiveData<>(false);
        this.homeApplied.addSource(this.adBlockModel.isApplied(), this.homeApplied::setValue);
        this.state = new MediatorLiveData<>();
        this.state.addSource(this.sourceModel.getState(), this.state::setValue);
        this.state.addSource(this.adBlockModel.getState(), this.state::setValue);
        this.error = new MutableLiveData<>();
        this.filterOperationState = this.sourceModel.getFilterOperationState();
        this.sourceOperations.getBusy().observeForever(this.operationObserver);
        this.filterOperationState.observeForever(this.progressObserver);
    }

    private static boolean isTrue(LiveData<Boolean> liveData) {
        Boolean value = liveData.getValue();
        return value != null && value;
    }

    public LiveData<Boolean> isAdBlocked() {
        return this.homeApplied;
    }

    @MainThread
    void reconcileVpnProtection() {
        if (!(this.adBlockModel instanceof VpnModel)) return;
        // prepare() can revoke someone else's VPN. Only the enable flow may call it.
        boolean enabled = VpnServiceControls.isEnabled(getApplication());
        if (!Boolean.valueOf(enabled).equals(this.homeApplied.getValue())) {
            this.homeApplied.setValue(enabled);
        }
    }

    public LiveData<Boolean> isUpdateAvailable() {
        return this.sourceModel.isUpdateAvailable();
    }

    public String getVersionName() {
        return this.updateModel.getVersionName();
    }

    public LiveData<Manifest> getAppManifest() {
        return this.updateModel.getManifest();
    }

    public LiveData<Integer> getBlockedHostCount() {
        // Use the tiny active-truth stats table; direct DISTINCT counts can stall on huge lists.
        if (this.adBlockModel.getMethod() == ROOT) {
            return this.hostEntryDao.getBlockedExactEntryCount();
        }
        return this.hostEntryDao.getBlockedEntryCount();
    }

    public LiveData<Integer> getAllowedHostCount() {
        return this.hostListItemDao.getAllowedHostCount();
    }

    public LiveData<Integer> getRedirectHostCount() {
        return this.hostListItemDao.getRedirectHostCount();
    }

    public LiveData<List<HostsSource>> getHosts() {
        return this.hostsSourceDao.loadAll();
    }

    public LiveData<Integer> getUpToDateSourceCount() {
        return this.hostsSourceDao.countUpToDate();
    }

    public LiveData<Integer> getOutdatedSourceCount() {
        return this.hostsSourceDao.countOutdated();
    }

    public LiveData<Boolean> getPending() {
        return this.pending;
    }

    public LiveData<OperationStatus> getState() {
        return this.state;
    }

    public LiveData<HostError> getError() {
        return this.error;
    }

    @MainThread
    void acknowledgeError(HostError displayed) {
        if (this.error.getValue() == displayed) this.error.setValue(null);
    }

    public LiveData<FilterOperationState> getFilterOperationState() {
        return this.filterOperationState;
    }

    public long getCachedInitialBlockedCount() {
        return cachedInitialBlockedCount;
    }

    public void setCachedInitialBlockedCount(long count) {
        this.cachedInitialBlockedCount = count;
    }

    public void pauseUpdate() {
        this.sourceModel.requestPause();
    }

    public void resumeUpdate() {
        this.sourceModel.requestResume();
    }

    public void stopUpdate() {
        this.sourceModel.requestStop();
    }

    public void checkForAppUpdate() {
        AppExecutors.getInstance().networkIO().execute(this.updateModel::checkForUpdate);
    }

    public LiveData<Boolean> getProtectionControlPending() {
        return this.protectionControlPending;
    }

    @MainThread
    public void setVpnConsentPending(boolean pending) {
        this.vpnConsentPending.setValue(pending);
        notifyProtectionControl();
    }

    @MainThread
    void onVpnConsentResult(boolean authorized) {
        this.vpnConsentPending.setValue(false);
        this.savedState.set(AUTHORIZED_VPN_ENABLE, authorized
                && PreferenceHelper.getAdBlockMethod(getApplication()) == VPN);
        resumeDeferredOperations();
    }

    boolean hasAuthorizedVpnEnableRequest() {
        return Boolean.TRUE.equals(this.savedState.get(AUTHORIZED_VPN_ENABLE));
    }

    @MainThread
    public void beginInitialSources() {
        this.initialSourcesPreparing = true;
        notifyProtectionControl();
    }

    @MainThread
    public void endInitialSources() {
        this.initialSourcesPreparing = false;
        resumeDeferredOperations();
    }

    private boolean isImporting() {
        FilterOperationState progress = this.filterOperationState.getValue();
        return progress != null
                && progress.kind == FilterOperationState.Kind.SOURCE_UPDATE
                && progress.phase != FilterOperationState.Phase.IDLE
                && progress.phase != FilterOperationState.Phase.COMPLETE
                && progress.phase != FilterOperationState.Phase.STOPPED;
    }

    public boolean canChangeProtection() {
        return canStartOperation() && !this.initialUpdateRequested
                && !hasAuthorizedVpnEnableRequest();
    }

    private boolean canStartOperation() {
        return !this.operationPending.get() && !this.sourceOperations.isBusy()
                && !isTrue(this.vpnConsentPending) && !this.initialSourcesPreparing
                && !isImporting();
    }

    @MainThread
    public void setProtectionEnabled(boolean enabled) {
        if (!canChangeProtection()) {
            return;
        }
        startProtectionOperation(enabled);
    }

    private void startProtectionOperation(boolean enabled) {
        SourceOperationCoordinator.Lease lease = beginOperation();
        if (lease == null) return;
        // The authorized request is consumed only after exclusive execution is accepted.
        this.savedState.set(AUTHORIZED_VPN_ENABLE, false);
        try {
            AppExecutors.getInstance().diskIO().execute(() -> {
                try {
                    if (enabled) {
                        this.adBlockModel.apply();
                    } else {
                        this.adBlockModel.revert();
                    }
                } catch (HostErrorException exception) {
                    Timber.w(exception, "Failed to toggle ad blocking.");
                    this.error.postValue(exception.getError());
                } finally {
                    finishOperation(lease);
                }
            });
        } catch (RuntimeException exception) {
            finishOperation(lease);
            throw exception;
        }
    }

    @MainThread
    public void update() {
        if (this.initialSourcesPreparing) {
            this.initialUpdateRequested = true;
            notifyProtectionControl();
            return;
        }
        startUpdate(false);
    }

    private void startUpdate(boolean initialDownload) {
        SourceOperationCoordinator.Lease lease = beginOperation();
        if (lease == null) return;
        this.initialUpdateRequested = false;
        try {
            AppExecutors.getInstance().networkIO().execute(() -> {
                try {
                    if (this.sourceModel.checkAndRetrieveHostsSources()) {
                        if (initialDownload) {
                            // Onboarding already enabled protection. A delayed download must not.
                            this.adBlockModel.applyAfterBackgroundRefresh();
                        } else {
                            this.adBlockModel.apply();
                        }
                    }
                } catch (HostErrorException exception) {
                    Timber.w(exception, "Failed to update.");
                    this.error.postValue(exception.getError());
                } finally {
                    finishOperation(lease);
                }
            });
        } catch (RuntimeException exception) {
            finishOperation(lease);
            throw exception;
        }
    }

    @MainThread
    public void sync() {
        update();
    }

    public void enableAllSources() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            if (this.sourceModel.enableAllSources()) {
                AppExecutors.getInstance().mainThread().execute(this::sync);
            }
        });
    }

    private SourceOperationCoordinator.Lease beginOperation() {
        // Claim before dispatch: LiveData.postValue does not exclude same-frame taps.
        if (!this.operationPending.compareAndSet(false, true)) {
            return null;
        }
        SourceOperationCoordinator.Lease lease = this.sourceOperations.tryAcquire();
        if (lease == null) {
            this.operationPending.set(false);
            return null;
        }
        this.pending.setValue(true);
        this.error.setValue(null);
        notifyProtectionControl();
        return lease;
    }

    private void finishOperation(SourceOperationCoordinator.Lease lease) {
        lease.close();
        AppExecutors.getInstance().mainThread().execute(() -> {
            this.operationPending.set(false);
            this.pending.setValue(false);
            resumeDeferredOperations();
        });
    }

    @MainThread
    public void resumeDeferredOperations() {
        if (this.cleared) return;
        if (PreferenceHelper.getAdBlockMethod(getApplication()) != VPN) {
            this.savedState.set(AUTHORIZED_VPN_ENABLE, false);
        }
        if (canStartOperation()) {
            if (this.initialUpdateRequested) {
                startUpdate(true);
            } else if (hasAuthorizedVpnEnableRequest()) {
                if (this.adBlockModel instanceof VpnModel
                        ? VpnServiceControls.isRunning(getApplication())
                        : isTrue(this.adBlockModel.isApplied())) {
                    this.savedState.set(AUTHORIZED_VPN_ENABLE, false);
                } else {
                    startProtectionOperation(true);
                }
            }
        }
        notifyProtectionControl();
    }

    private void notifyProtectionControl() {
        this.protectionControlPending.setValue(!canChangeProtection());
    }

    @Override
    protected void onCleared() {
        this.cleared = true;
        this.sourceOperations.getBusy().removeObserver(this.operationObserver);
        this.filterOperationState.removeObserver(this.progressObserver);
        super.onCleared();
    }
}
