package org.adaway.ui.adblocking;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.lifecycle.Observer;

import com.google.android.material.snackbar.Snackbar;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.ui.hosts.HostsSourcesViewModel;
import org.adaway.ui.hosts.HostsSourcesViewModel.ApplyStatus;
import org.adaway.util.AppExecutors;
import org.adaway.vpn.VpnServiceControls;

import timber.log.Timber;

import static com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE;
import static com.google.android.material.snackbar.Snackbar.LENGTH_LONG;

/**
 * This class is a {@link Snackbar} to notify about adblock model new
 * configuration to apply.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ApplyConfigurationSnackbar {
    enum ApplyOutcome { APPLIED, VPN_REQUESTED, NO_METHOD, BUSY, FAILED }

    /**
     * The view to bind the snackbar to.
     */
    private final View view;
    /**
     * The notify snackbar when hosts update available.
     */
    private Snackbar notifySnackbar;
    /**
     * The wait snackbar during hosts install.
     */
    private final Snackbar waitSnackbar;
    /**
     * To synchronize sources before installing or not.
     */
    private final boolean syncSources;
    /**
     * The current hosts update available status ({@code true} if update available,
     * {@code false} otherwise).
     */
    private boolean update;
    /**
     * Whether or not ignore the next update event ({@code true} to ignore,
     * {@code false} otherwise).
     */
    private boolean skipUpdate;
    /**
     * Whether or not ignore update events during the install ({@code true} to
     * ignore, {@code false} otherwise).
     */
    private boolean ignoreEventDuringInstall;
    private boolean applying;
    @Nullable
    private final HostsSourcesViewModel mSourceViewModel;
    @Nullable
    private ApplyStatus mLastSourceStatus;
    @Nullable
    private Snackbar mResultSnackbar;
    @Nullable
    private Snackbar mAppliedSnackbar;

    /**
     * Constructor.
     *
     * @param view                     The view to bind the snackbar to.
     * @param syncSources              To synchronize sources before installing or
     *                                 not.
     * @param ignoreEventDuringInstall {@code true} to ignore events, {@code false}
     *                                 otherwise.
     */
    public ApplyConfigurationSnackbar(@NonNull View view, boolean syncSources, boolean ignoreEventDuringInstall) {
        this(view, syncSources, ignoreEventDuringInstall, null);
    }

    public ApplyConfigurationSnackbar(@NonNull View view, @NonNull HostsSourcesViewModel sourceViewModel) {
        this(view, true, false, sourceViewModel);
    }

    private ApplyConfigurationSnackbar(@NonNull View view, boolean syncSources,
            boolean ignoreEventDuringInstall, @Nullable HostsSourcesViewModel sourceViewModel) {
        this.view = view;
        this.notifySnackbar = createNotifySnackbar();
        this.waitSnackbar = Snackbar.make(this.view, R.string.notification_configuration_installing, LENGTH_INDEFINITE);
        appendViewToSnackbar(this.waitSnackbar, new ProgressBar(this.view.getContext()));
        this.syncSources = syncSources;
        this.ignoreEventDuringInstall = ignoreEventDuringInstall;
        this.update = false;
        this.skipUpdate = false;
        this.mSourceViewModel = sourceViewModel;
    }

    @UiThread
    public void refreshSourceState() {
        if (this.mSourceViewModel == null) return;
        ApplyStatus status = this.mSourceViewModel.getApplyStatus().getValue();
        boolean changed = status != this.mLastSourceStatus;
        this.mLastSourceStatus = status;
        if (status == ApplyStatus.RUNNING) {
            if (changed) showLoading();
            return;
        }
        this.waitSnackbar.dismiss();
        if (status == ApplyStatus.BUSY || status == ApplyStatus.FAILED) {
            if (changed || this.mResultSnackbar == null) {
                showUnapplied(status == ApplyStatus.BUSY, this.mSourceViewModel::retry);
            }
            return;
        }
        if (status == ApplyStatus.SUCCEEDED) dismissResult();
        if (this.mSourceViewModel.getApplyState().isPending()) {
            notifyUpdateAvailable();
        } else {
            dismissNotifySnackbar();
            if (changed && status == ApplyStatus.SUCCEEDED) showApplied();
        }
    }

    private Snackbar createNotifySnackbar() {
        return Snackbar.make(this.view, R.string.notification_configuration_changed,
                LENGTH_INDEFINITE)
                .setAction(R.string.notification_configuration_changed_action, v -> apply());
    }

    private void dismissNotifySnackbar() {
        if (this.notifySnackbar.isShownOrQueued()) {
            // A dismissing snackbar still reports shown; a new request needs its own callback.
            Snackbar previous = this.notifySnackbar;
            this.notifySnackbar = createNotifySnackbar();
            previous.dismiss();
        }
    }

    /**
     * Create {@link Observer} which ignores first (initialization) event.
     *
     * @param <T> The type of data to observe.
     * @return The observer instance.
     */
    public <T> Observer<T> createObserver() {
        return new Observer<T>() {
            boolean firstUpdate = true;

            @Override
            public void onChanged(@Nullable T t) {
                // Check new data
                // Check new data
                if (t == null) {
                    return;
                }
                // First update
                if (this.firstUpdate) {
                    this.firstUpdate = false;
                    return;
                }
                ApplyConfigurationSnackbar.this.notifyUpdateAvailable();
            }
        };
    }

    public void notifyUpdateAvailable(@NonNull CharSequence message) {
        this.notifySnackbar.setText(message);
        notifyUpdateAvailable();
    }

    /**
     * Notify update available.
     */
    public void notifyUpdateAvailable() {
        // An edit arriving during an explicit Apply may need another Apply.
        if (this.applying) {
            this.update = true;
            return;
        }
        // The failure/busy banner already offers Apply; keep its explanation visible.
        if (this.mSourceViewModel == null && this.mResultSnackbar != null
                && this.mResultSnackbar.isShownOrQueued()) return;
        // Check if notify snackbar is already displayed
        if (this.notifySnackbar.isShownOrQueued()) {
            return;
        }
        // Check if update event should be skipped
        if (this.skipUpdate) {
            this.skipUpdate = false;
            return;
        }
        // Show notify snackbar
        this.notifySnackbar.show();
        // Mark update as notified
        this.update = false;
    }

    @UiThread
    void apply() {
        if (this.mSourceViewModel != null) {
            this.mSourceViewModel.updateSources(null);
            return;
        }
        if (this.applying) {
            return;
        }
        this.applying = true;
        this.update = false;
        showLoading();
        AdAwayApplication application =
                (AdAwayApplication) this.view.getContext().getApplicationContext();
        SourceOperationCoordinator.Lease lease = application.getSourceOperations().tryAcquire();
        if (lease == null) {
            endLoading(ApplyOutcome.BUSY);
            return;
        }
        try {
            AppExecutors.getInstance().diskIO().execute(() -> {
                ApplyOutcome outcome;
                try (SourceOperationCoordinator.Lease ignored = lease) {
                    outcome = applyConfiguration(application);
                } catch (HostErrorException | RuntimeException exception) {
                    outcome = ApplyOutcome.FAILED;
                    Timber.w(exception, "Failed to apply configuration.");
                }
                ApplyOutcome result = outcome;
                AppExecutors.getInstance().mainThread().execute(() -> endLoading(result));
            });
        } catch (RuntimeException exception) {
            lease.close();
            endLoading(ApplyOutcome.FAILED);
            Timber.w(exception, "Failed to schedule configuration apply.");
        }
    }

    private ApplyOutcome applyConfiguration(AdAwayApplication application) throws HostErrorException {
        SourceModel source = application.getSourceModel();
        if (this.syncSources) {
            if (!source.checkAndRetrieveHostsSources()) return ApplyOutcome.FAILED;
        } else {
            source.syncHostEntries();
        }
        AdBlockModel model = application.getAdBlockModel();
        AdBlockMethod method = model.getMethod();
        if (method == AdBlockMethod.UNDEFINED) return ApplyOutcome.NO_METHOD;
        model.apply();
        // VPN apply returns once Android accepts the request, before a tunnel necessarily exists.
        return method == AdBlockMethod.VPN && !isTunnelEstablished(application)
                ? ApplyOutcome.VPN_REQUESTED : ApplyOutcome.APPLIED;
    }

    boolean isTunnelEstablished(Context context) {
        return VpnServiceControls.isTunnelEstablished(context);
    }

    private void showLoading() {
        // Clear notify snackbar
        dismissResult();
        dismissNotifySnackbar();
        // Show wait snackbar
        if (this.mSourceViewModel != null) {
            this.waitSnackbar.setText(R.string.sources_apply_installing);
        }
        this.waitSnackbar.show();
    }

    @UiThread
    void endLoading(ApplyOutcome outcome) {
        this.applying = false;
        // Clear snackbars
        this.waitSnackbar.dismiss();
        if (!this.view.isAttachedToWindow()) {
            return;
        }
        if (outcome == ApplyOutcome.FAILED || outcome == ApplyOutcome.BUSY) {
            showUnapplied(outcome == ApplyOutcome.BUSY, this::apply);
            return;
        }
        if (this.update) {
            if (!this.ignoreEventDuringInstall) {
                notifyUpdateAvailable();
                return;
            }
            this.skipUpdate = true;
        }
        switch (outcome) {
            case APPLIED -> showApplied();
            case VPN_REQUESTED -> showCompletion(R.string.notification_configuration_vpn_requested);
            case NO_METHOD -> showCompletion(R.string.leak_status_mode_unconfigured);
        }
    }

    private void showApplied() {
        showCompletion(R.string.notification_configuration_applied);
    }

    private void showCompletion(@StringRes int message) {
        // The queue holds callbacks weakly while the previous snackbar is dismissing.
        this.mAppliedSnackbar = Snackbar.make(this.view, message, LENGTH_LONG);
        this.mAppliedSnackbar.show();
    }

    @UiThread
    private void showUnapplied(boolean busy, @Nullable Runnable retry) {
        dismissResult();
        dismissNotifySnackbar();
        this.mResultSnackbar = Snackbar.make(this.view,
                busy ? R.string.error_update_in_progress_details : R.string.notification_configuration_failed,
                retry == null ? LENGTH_LONG : LENGTH_INDEFINITE);
        if (retry != null) {
            this.mResultSnackbar.setAction(R.string.notification_configuration_changed_action, v -> {
                dismissResult();
                retry.run();
            });
        }
        if (!busy) {
            ImageView icon = new ImageView(this.view.getContext());
            icon.setImageResource(R.drawable.ic_error_outline_24dp);
            appendViewToSnackbar(this.mResultSnackbar, icon);
        }
        this.mResultSnackbar.show();
    }

    private void dismissResult() {
        if (this.mResultSnackbar != null) {
            this.mResultSnackbar.dismiss();
            this.mResultSnackbar = null;
        }
    }

    private void appendViewToSnackbar(Snackbar snackbar, View view) {
        ViewGroup viewGroup = (ViewGroup) snackbar.getView()
                .findViewById(com.google.android.material.R.id.snackbar_text).getParent();
        viewGroup.addView(view);
    }
}
