package org.adaway.tile;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import org.adaway.AdAwayApplication;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.util.AppExecutors;

import java.util.concurrent.Executor;

import static android.service.quicksettings.Tile.STATE_ACTIVE;
import static android.service.quicksettings.Tile.STATE_INACTIVE;
import static android.service.quicksettings.Tile.STATE_UNAVAILABLE;

import timber.log.Timber;

/**
 * This class is a {@link TileService} to toggle ad-blocking.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class AdBlockingTileService extends TileService {
    private final Observer<Boolean> appliedObserver = applied -> updateTile();
    private final Observer<Boolean> busyObserver = busy -> updateTile();
    private LiveData<Boolean> observedApplied;
    private LiveData<Boolean> observedBusy;

    @Override
    public void onTileAdded() {
        updateTile();
    }

    @Override
    public void onStartListening() {
        stopObserving();
        this.observedApplied = getModel().isApplied();
        this.observedBusy = getSourceOperations().getBusy();
        this.observedApplied.observeForever(this.appliedObserver);
        this.observedBusy.observeForever(this.busyObserver);
    }

    @Override
    public void onStopListening() {
        stopObserving();
    }

    @Override
    public void onDestroy() {
        stopObserving();
        super.onDestroy();
    }

    private void stopObserving() {
        if (this.observedApplied != null) {
            this.observedApplied.removeObserver(this.appliedObserver);
            this.observedApplied = null;
        }
        if (this.observedBusy != null) {
            this.observedBusy.removeObserver(this.busyObserver);
            this.observedBusy = null;
        }
    }

    @Override
    public void onClick() {
        // Claim synchronously: queued clicks must not outlive a conflicting update.
        SourceOperationCoordinator.Lease lease = getSourceOperations().tryAcquire();
        if (lease == null) {
            updateTile();
            return;
        }
        try {
            updateTile();
            getCommandExecutor().execute(() -> toggleAdBlocking(lease));
        } catch (RuntimeException | Error exception) {
            lease.close();
            updateTile();
            throw exception;
        }
    }

    private void updateTile() {
        publishTileState(getTileState());
    }

    int getTileState() {
        if (getSourceOperations().isBusy()) {
            return STATE_UNAVAILABLE;
        }
        return Boolean.TRUE.equals(getModel().isApplied().getValue()) ? STATE_ACTIVE : STATE_INACTIVE;
    }

    void publishTileState(int state) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(state);
        tile.updateTile();
    }

    private void toggleAdBlocking(SourceOperationCoordinator.Lease lease) {
        try {
            AdBlockModel model = getModel();
            if (model.isApplied().getValue() == Boolean.TRUE) {
                model.revert();
            } else {
                model.apply();
            }
        } catch (HostErrorException e) {
            Timber.w(e, "Failed to toggle ad-blocking.");
        } finally {
            lease.close();
        }
    }

    SourceOperationCoordinator getSourceOperations() {
        return ((AdAwayApplication) getApplication()).getSourceOperations();
    }

    Executor getCommandExecutor() {
        return AppExecutors.getInstance().diskIO();
    }

    AdBlockModel getModel() {
        return ((AdAwayApplication) getApplication()).getAdBlockModel();
    }
}
