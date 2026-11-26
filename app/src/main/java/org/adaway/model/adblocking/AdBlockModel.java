package org.adaway.model.adblocking;

import android.content.Context;

import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.model.OperationStatus;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.root.RootModel;
import org.adaway.model.vpn.VpnModel;
import org.adaway.vpn.VpnServiceControls;

import java.util.List;

import timber.log.Timber;

/**
 * This class is the base model for all ad block model.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public abstract class AdBlockModel {
    /**
     * The application context.
     */
    protected final Context context;
    /**
     * The hosts installation status:
     * <ul>
     * <li>{@code null} if not defined,</li>
     * <li>{@code true} if hosts list is installed,</li>
     * <li>{@code false} if default hosts file.</li>
     * </ul>
     */
    protected final MutableLiveData<Boolean> applied;
    /**
     * The model state.
     */
    private final MutableLiveData<OperationStatus> state;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    protected AdBlockModel(Context context) {
        this.context = context;
        this.state = new MutableLiveData<>();
        this.applied = new MutableLiveData<>();
    }

    /**
     * Instantiate ad block model.
     *
     * @param context The application context.
     * @param method  The ad block method to get model.
     * @return The instantiated model.
     */
    public static AdBlockModel build(Context context, AdBlockMethod method) {
        switch (method) {
            case ROOT:
                return new RootModel(context);
            case VPN:
                return new VpnModel(context);
            default:
                return new UndefinedBlockModel(context);
        }
    }

    /**
     * Get ad block method.
     *
     * @return The ad block method of this model.
     */
    public abstract AdBlockMethod getMethod();

    /**
     * Checks if hosts list is applied.
     *
     * @return {@code true} if applied, {@code false} if default.
     */
    public LiveData<Boolean> isApplied() {
        return this.applied;
    }

    /**
     * Apply hosts list.
     *
     * @throws HostErrorException If the model configuration could not be applied.
     */
    public abstract void apply() throws HostErrorException;

    /** After SourceModel publishes refreshed rules, report whether protection uses them. */
    public boolean applyAfterBackgroundRefresh() throws HostErrorException {
        if (getMethod() == AdBlockMethod.VPN) {
            // Publishing runtime rows already invalidates the live VPN lookup cache.
            // Never prepare or start a VPN just to refresh those rows.
            return VpnServiceControls.isTunnelEstablished(this.context);
        }
        apply();
        return true;
    }

    /**
     * Revert the hosts list to the default one.
     *
     * @throws HostErrorException If the model configuration could not be revert.
     */
    public abstract void revert() throws HostErrorException;

    /**
     * Get the model state.
     *
     * @return The model state.
     */
    public LiveData<OperationStatus> getState() {
        return this.state;
    }

    protected void setState(@StringRes int stateResId, Object... details) {
        OperationStatus state = new OperationStatus(stateResId, details);
        Timber.d("%s", state.resolve(this.context));
        this.state.postValue(state);
    }

    /**
     * Get whether log are recoding or not.
     *
     * @return {@code true} if logs are recoding, {@code false} otherwise.
     */
    public abstract boolean isRecordingLogs();

    /**
     * Set log recoding.
     *
     * @param recording {@code true} to record logs, {@code false} otherwise.
     */
    public abstract void setRecordingLogs(boolean recording);

    /**
     * Get logs.
     *
     * @return The logs unique and sorted by date, older first.
     */
    public abstract List<String> getLogs();

    /**
     * Clear logs.
     */
    public abstract void clearLogs();
}
