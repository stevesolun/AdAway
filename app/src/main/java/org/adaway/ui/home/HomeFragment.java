package org.adaway.ui.home;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.UNDEFINED;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.ui.Animations.removeView;
import static org.adaway.ui.Animations.showView;
import static org.adaway.ui.lists.ListsActivity.ALLOWED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.BLOCKED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.REDIRECTED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.TAB;

import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.databinding.FragmentHomeBinding;
import org.adaway.db.AppDatabase;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.error.HostError;
import org.adaway.model.source.FilterOperationState;
import org.adaway.ui.hosts.FilterListsSubscribeAllWorker;
import org.adaway.ui.hosts.FilterSetUpdateService;
import org.adaway.ui.hosts.FilterSetUpdateWorker;
import org.adaway.ui.lists.ListsActivity;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.util.AppExecutors;
import org.adaway.vpn.VpnStatus;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import timber.log.Timber;

/**
 * Fragment displaying the main home screen (status, stats, update progress).
 * Mirrors the logic that was previously in HomeActivity.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel;
    @Nullable
    private AlertDialog downloadErrorDialog;
    @Nullable
    private WorkInfo filterListsProgress;
    @Nullable
    private WorkInfo scheduledProgress;
    // Initial blocked count when progress starts
    private long initialBlockedCount = -1;

    private static final NumberFormat COUNT_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    @Nullable
    private LiveData<Integer> blockedHostCountLiveData;
    @Nullable
    private LiveData<Integer> allowedHostCountLiveData;
    @Nullable
    private LiveData<Integer> redirectHostCountLiveData;
    @Nullable
    private Observer<Integer> blockedHostCountObserver;
    @Nullable
    private Observer<Integer> allowedHostCountObserver;
    @Nullable
    private Observer<Integer> redirectHostCountObserver;
    private boolean hostCountersAttached = false;
    private boolean vpnTunnelEstablished;
    private boolean hostCountersPrimedDuringImport = false;
    @Nullable
    private String lastTerminalProgressAnnouncement;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        this.binding = FragmentHomeBinding.inflate(inflater, container, false);
        return this.binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Share the ViewModel with the host Activity so other fragments can reference it.
        this.homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        this.homeViewModel.isAdBlocked().observe(getViewLifecycleOwner(), this::notifyAdBlocked);
        this.homeViewModel.getError().observe(getViewLifecycleOwner(), this::notifyError);

        bindAppVersion();
        bindHostCounter();
        bindSourceCounter();
        bindPending();
        bindState();
        bindClickListeners();
        bindProtectionControl();
        bindFilterListsSubscribeAllProgress();
        bindScheduledUpdateProgress();
        bindFilterOperationState();

        bindDiscoverCta();
        bindLeakStatus();
    }

    @Override
    public void onDestroyView() {
        dismissDownloadErrorDialog();
        detachHostCounterObservers();
        this.initialBlockedCount = -1;
        this.vpnTunnelEstablished = false;
        this.hostCountersPrimedDuringImport = false;
        this.filterListsProgress = null;
        this.scheduledProgress = null;
        super.onDestroyView();
        this.binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        renderLeakStatus();
    }

    // -----------------------------------------------------------------------------------------
    // Progress bindings (ported from HomeActivity)
    // -----------------------------------------------------------------------------------------

    private void bindFilterListsSubscribeAllProgress() {
        WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData(FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME)
                .observe(getViewLifecycleOwner(), this::updateFilterListsSubscribeAllProgress);
    }

    private void bindScheduledUpdateProgress() {
        WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData(FilterSetUpdateService.WORK_NAME)
                .observe(getViewLifecycleOwner(), this::updateScheduledUpdateProgress);
    }

    void updateFilterListsSubscribeAllProgress(@Nullable List<WorkInfo> infos) {
        if (this.binding == null) return;
        this.filterListsProgress = keepMonotonicProgress(this.filterListsProgress,
                findActiveWork(infos, true), FilterListsSubscribeAllWorker.PROGRESS_DONE,
                FilterListsSubscribeAllWorker.PROGRESS_TOTAL);
        renderWorkProgress();
    }

    void updateScheduledUpdateProgress(@Nullable List<WorkInfo> infos) {
        if (this.binding == null) return;
        this.scheduledProgress = keepMonotonicProgress(this.scheduledProgress,
                findActiveWork(infos, false), FilterSetUpdateWorker.PROGRESS_DONE,
                FilterSetUpdateWorker.PROGRESS_TOTAL);
        renderWorkProgress();
    }

    @Nullable
    private static WorkInfo findActiveWork(@Nullable List<WorkInfo> infos, boolean includeQueued) {
        if (infos == null) return null;
        for (WorkInfo info : infos) {
            if (info.getState() == WorkInfo.State.RUNNING) return info;
        }
        if (includeQueued) {
            for (WorkInfo info : infos) {
                if (info.getState() == WorkInfo.State.ENQUEUED) return info;
            }
        }
        return null;
    }

    @Nullable
    private static WorkInfo keepMonotonicProgress(@Nullable WorkInfo previous,
            @Nullable WorkInfo current, String doneKey, String totalKey) {
        if (previous != null && current != null && previous.getId().equals(current.getId())
                && previous.getRunAttemptCount() == current.getRunAttemptCount()
                && previous.getGeneration() == current.getGeneration()
                && previous.getProgress().getInt(totalKey, 0)
                == current.getProgress().getInt(totalKey, 0)
                && previous.getProgress().getInt(doneKey, 0)
                > current.getProgress().getInt(doneKey, 0)) {
            return previous;
        }
        return current;
    }

    private void renderWorkProgress() {
        if (this.binding == null) return;
        // Both observers own state; only this renderer owns the shared progress views.
        boolean subscribing = this.filterListsProgress != null;
        WorkInfo info = subscribing ? this.filterListsProgress : this.scheduledProgress;
        TextView text = this.binding.content.filterListsSubscribeProgressTextView;
        if (info == null) {
            text.setText("");
            text.setOnClickListener(null);
            text.setClickable(false);
            text.setVisibility(View.GONE);
            this.binding.content.filterListsSubscribeProgressBar.setVisibility(View.GONE);
            return;
        }

        int done = info.getProgress().getInt(subscribing
                ? FilterListsSubscribeAllWorker.PROGRESS_DONE
                : FilterSetUpdateWorker.PROGRESS_DONE, 0);
        int total = info.getProgress().getInt(subscribing
                ? FilterListsSubscribeAllWorker.PROGRESS_TOTAL
                : FilterSetUpdateWorker.PROGRESS_TOTAL, 0);
        String current = info.getProgress().getString(subscribing
                ? FilterListsSubscribeAllWorker.PROGRESS_CURRENT_NAME
                : FilterSetUpdateWorker.PROGRESS_CURRENT);
        int percent = total > 0 ? (int) Math.floor(done * 100.0 / total) : 0;
        String message = subscribing && total <= 0
                ? "FilterLists subscribing: Preparing\u2026"
                : (subscribing ? "FilterLists subscribing: " : "Scheduled update: ")
                + done + "/" + total + " (" + percent + "%)";
        if (current != null && !current.isEmpty()) message += " \u2022 " + current;
        text.setText(message);
        text.setOnClickListener(subscribing ? v -> navigateToDiscover() : null);
        text.setClickable(subscribing);
        text.setVisibility(View.VISIBLE);
        this.binding.content.filterListsSubscribeProgressBar.setIndeterminate(total <= 0);
        if (total > 0) {
            this.binding.content.filterListsSubscribeProgressBar.setMax(100);
            this.binding.content.filterListsSubscribeProgressBar.setProgressCompat(percent, true);
        }
        this.binding.content.filterListsSubscribeProgressBar.setVisibility(View.VISIBLE);
    }

    private void bindFilterOperationState() {
        this.binding.content.pauseResumeButton.setOnClickListener(v -> {
            FilterOperationState progress = this.homeViewModel.getFilterOperationState().getValue();
            if (progress == null || progress.kind != FilterOperationState.Kind.SOURCE_UPDATE) return;
            if (progress.paused) {
                this.homeViewModel.resumeUpdate();
                this.binding.content.pauseResumeButton.setImageResource(R.drawable.ic_pause_24dp);
                this.binding.content.pauseResumeButton.setContentDescription(getString(R.string.pause_update));
            } else {
                this.homeViewModel.pauseUpdate();
                this.binding.content.pauseResumeButton.setImageResource(R.drawable.ic_play_24dp);
                this.binding.content.pauseResumeButton.setContentDescription(getString(R.string.resume_update));
            }
        });

        this.binding.content.stopButton.setOnClickListener(v -> this.homeViewModel.stopUpdate());

        this.homeViewModel.getFilterOperationState().observe(getViewLifecycleOwner(), progress -> {
            if (this.binding == null) return;
            renderProtectionControl();
            if (progress == null
                    || progress.kind == FilterOperationState.Kind.IDLE
                    || progress.phase == FilterOperationState.Phase.IDLE) {
                removeView(this.binding.content.multiPhaseProgressContainer);
                resetImportCounterGuards();
                lastTerminalProgressAnnouncement = null;
                attachHostCounterObservers();
                return;
            }

            boolean isStopped = progress.phase == FilterOperationState.Phase.STOPPED
                    || progress.stopped;
            boolean isFinalizing = progress.phase == FilterOperationState.Phase.FINALIZE;
            boolean isComplete = progress.phase == FilterOperationState.Phase.COMPLETE;

            showView(this.binding.content.multiPhaseProgressContainer);
            if (isStopped || isComplete) {
                resetImportCounterGuards();
                attachHostCounterObservers();
                refreshHostCountersOnce();
            } else {
                detachHostCounterObservers();
                primeCountersOnceDuringImport();
            }

            if (!isStopped && !isComplete && initialBlockedCount < 0) {
                long cached = this.homeViewModel.getCachedInitialBlockedCount();
                if (cached >= 0) {
                    initialBlockedCount = cached;
                } else {
                    Integer currentBlocked = this.homeViewModel.getBlockedHostCount().getValue();
                    if (currentBlocked != null && currentBlocked > 0) {
                        initialBlockedCount = currentBlocked;
                        this.homeViewModel.setCachedInitialBlockedCount(initialBlockedCount);
                    } else {
                        try {
                            String text = this.binding.content.blockedHostCounterTextView.getText().toString();
                            long parsed = Long.parseLong(text.replaceAll("[^0-9]", ""));
                            if (parsed > 0) {
                                initialBlockedCount = parsed;
                                this.homeViewModel.setCachedInitialBlockedCount(initialBlockedCount);
                            } else if (currentBlocked != null) {
                                initialBlockedCount = 0;
                                this.homeViewModel.setCachedInitialBlockedCount(0);
                            }
                        } catch (Exception e) {
                            if (currentBlocked != null) {
                                initialBlockedCount = currentBlocked;
                                this.homeViewModel.setCachedInitialBlockedCount(initialBlockedCount);
                            }
                        }
                    }
                }

                if (initialBlockedCount <= 0) {
                    final Context appContext = requireContext().getApplicationContext();
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        try {
                            int blockedNow = getBlockedEntryCountNow(appContext);
                            AppExecutors.getInstance().mainThread().execute(() -> {
                                if (this.binding == null) return;
                                if (initialBlockedCount <= 0) {
                                    initialBlockedCount = blockedNow;
                                    this.homeViewModel.setCachedInitialBlockedCount(initialBlockedCount);
                                    this.binding.content.blockedHostCounterTextView
                                            .setText(formatCount(blockedNow));
                                }
                            });
                        } catch (Exception ignored) {
                        }
                    });
                }
            }

            double overallPercentDouble = progress.overallPercent;
            int overallPercent = (int) overallPercentDouble;
            this.binding.content.overallProgressBar.setMax(100);
            this.binding.content.overallProgressBar.setProgress(overallPercent);
            String progressText;
            if (isStopped) {
                progressText = getString(R.string.update_progress_stopped);
            } else if (isFinalizing) {
                if (progress.parsedHostCount > 0) {
                    progressText = getString(R.string.update_progress_finalizing_with_rules,
                            progress.parsedHostCount);
                } else {
                    progressText = getString(R.string.update_progress_finalizing);
                }
            } else if (isComplete) {
                progressText = getString(R.string.update_progress_complete);
            } else if (progress.parsedHostCount > 0) {
                progressText = getString(R.string.update_progress_with_rules,
                        overallPercentDouble, progress.parsedHostCount);
            } else {
                progressText = getString(R.string.update_progress_percent, overallPercentDouble);
            }
            this.binding.content.overallProgressText.setText(progressText);
            if (isComplete || isStopped) {
                if (!progressText.equals(lastTerminalProgressAnnouncement)) {
                    this.binding.content.overallProgressText
                            .announceForAccessibility(progressText);
                    lastTerminalProgressAnnouncement = progressText;
                }
            } else {
                lastTerminalProgressAnnouncement = null;
            }

            View progressFrame = this.binding.content.overallProgressFrame;
            View progressBar = this.binding.content.overallProgressBar;
            View birdIcon = this.binding.content.birdProgressIcon;
            float density = getResources().getDisplayMetrics().density;
            int marginStartPx = (int) (20 * density);
            int capturedPercent = overallPercent;

            progressFrame.post(() -> {
                if (birdIcon == null) return;
                int barWidth = progressBar.getWidth();
                int birdWidth = birdIcon.getWidth();
                int trackWidth = barWidth;
                int progressX = marginStartPx + (int) (trackWidth * capturedPercent / 100.0f);
                int overlap = birdWidth / 5;
                birdIcon.setTranslationX(progressX - overlap);
            });

            this.binding.content.checkProgressBar.setVisibility(View.GONE);
            this.binding.content.checkPhasePercent.setVisibility(View.GONE);
            this.binding.content.checkPhaseLabel.setVisibility(View.GONE);
            this.binding.content.downloadProgressBar.setVisibility(View.GONE);
            this.binding.content.downloadPhasePercent.setVisibility(View.GONE);
            this.binding.content.downloadPhaseLabel.setVisibility(View.GONE);
            this.binding.content.parseProgressBar.setVisibility(View.GONE);
            this.binding.content.parsePhasePercent.setVisibility(View.GONE);
            this.binding.content.parsePhaseLabel.setVisibility(View.GONE);

            int totalToCheck = progress.totalSources;
            int checked = progress.checkedCount;
            int downloadBarProgress = totalToCheck > 0 ? (int) ((long) checked * 100 / totalToCheck) : 0;
            this.binding.content.downloadProgressBar.setMax(100);
            this.binding.content.downloadProgressBar.setProgress(downloadBarProgress);
            this.binding.content.downloadPhasePercent
                    .setText(String.format(java.util.Locale.ROOT, "%d/%d", checked, totalToCheck));

            double parsePercent = totalToCheck > 0
                    ? progress.parsedCount * 100.0 / totalToCheck
                    : 0.0;
            this.binding.content.parseProgressBar.setMax(100);
            this.binding.content.parseProgressBar.setProgress((int) parsePercent);
            if (parsePercent > 99.9) parsePercent = 100.0;
            this.binding.content.parsePhasePercent
                    .setText(String.format(java.util.Locale.ROOT, "%.1f%%", parsePercent));

            this.binding.content.schedulerTaskContainer.setVisibility(View.VISIBLE);
            if (progress.schedulerTaskName != null && !progress.schedulerTaskName.isEmpty()) {
                this.binding.content.schedulerTaskName.setVisibility(View.VISIBLE);
                this.binding.content.schedulerTaskName
                        .setText(getString(
                                R.string.scheduled_update_task_label,
                                progress.schedulerTaskName));
            } else {
                this.binding.content.schedulerTaskName.setVisibility(View.INVISIBLE);
                this.binding.content.schedulerTaskName.setText("");
            }

            if (progress.paused) {
                this.binding.content.pauseResumeButton.setImageResource(R.drawable.ic_play_24dp);
                this.binding.content.pauseResumeButton
                        .setContentDescription(getString(R.string.resume_update));
            } else {
                this.binding.content.pauseResumeButton.setImageResource(R.drawable.ic_pause_24dp);
                this.binding.content.pauseResumeButton
                        .setContentDescription(getString(R.string.pause_update));
            }
            boolean controlsEnabled = !isFinalizing
                    && !isStopped
                    && !isComplete;
            this.binding.content.pauseResumeButton.setEnabled(controlsEnabled);
            this.binding.content.stopButton.setEnabled(controlsEnabled);
        });
    }

    // -----------------------------------------------------------------------------------------
    // Discover CTA
    // -----------------------------------------------------------------------------------------

    private void bindDiscoverCta() {
        this.binding.discoverCta.setOnClickListener(v -> {
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).navigateTo(R.id.nav_discover);
            }
        });

        this.homeViewModel.getHosts().observe(getViewLifecycleOwner(), sources -> {
            if (this.binding == null) return;
            this.binding.discoverCta.setVisibility(
                    (sources == null || sources.isEmpty()) ? View.VISIBLE : View.GONE);
        });
    }

    // -----------------------------------------------------------------------------------------
    // Counter / state bindings
    // -----------------------------------------------------------------------------------------

    private void bindAppVersion() {
        TextView versionTextView = this.binding.content.versionTextView;
        Typeface defaultTypeface = versionTextView.getTypeface();
        versionTextView.setText(this.homeViewModel.getVersionName());
        versionTextView.setOnClickListener(v ->
                startActivity(new Intent(requireContext(),
                        org.adaway.ui.update.UpdateActivity.class)));

        this.homeViewModel.getAppManifest().observe(getViewLifecycleOwner(), manifest -> {
            if (manifest != null && manifest.updateAvailable) {
                versionTextView.setTypeface(versionTextView.getTypeface(), Typeface.BOLD);
                versionTextView.setText(R.string.update_available);
            } else {
                versionTextView.setTypeface(defaultTypeface, Typeface.NORMAL);
                versionTextView.setText(this.homeViewModel.getVersionName());
            }
        });
    }

    private void bindHostCounter() {
        this.blockedHostCountLiveData = this.homeViewModel.getBlockedHostCount();
        this.allowedHostCountLiveData = this.homeViewModel.getAllowedHostCount();
        this.redirectHostCountLiveData = this.homeViewModel.getRedirectHostCount();

        // Status follows the active rules snapshot even while display counters are frozen.
        this.blockedHostCountLiveData.observe(getViewLifecycleOwner(),
                count -> renderProtectionStatus());
        renderProtectionStatus();

        TextView blockedHostCountTextView = this.binding.content.blockedHostCounterTextView;
        TextView allowedHostCountTextView = this.binding.content.allowedHostCounterTextView;
        TextView redirectHostCountTextView = this.binding.content.redirectHostCounterTextView;

        this.blockedHostCountObserver = count -> {
            if (count == null) return;
            if (initialBlockedCount >= 0) return;
            blockedHostCountTextView.setText(formatCount(count));
        };
        this.allowedHostCountObserver = count -> {
            if (count == null) return;
            allowedHostCountTextView.setText(formatCount(count));
        };
        this.redirectHostCountObserver = count -> {
            if (count == null) return;
            redirectHostCountTextView.setText(formatCount(count));
        };

        attachHostCounterObservers();
        refreshHostCountersOnce();
    }

    private void resetImportCounterGuards() {
        initialBlockedCount = -1;
        this.homeViewModel.setCachedInitialBlockedCount(-1);
        hostCountersPrimedDuringImport = false;
    }

    private void refreshHostCountersOnce() {
        if (hostCountersPrimedDuringImport) return;
        hostCountersPrimedDuringImport = true;
        final Context appContext = requireContext().getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                int blockedNow = getBlockedEntryCountNow(appContext);
                int allowedNow = AppDatabase.getInstance(appContext)
                        .hostsListItemDao().getAllowedHostCountNow();
                int redirectNow = AppDatabase.getInstance(appContext)
                        .hostsListItemDao().getRedirectHostCountNow();
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    this.binding.content.blockedHostCounterTextView.setText(formatCount(blockedNow));
                    this.binding.content.allowedHostCounterTextView.setText(formatCount(allowedNow));
                    this.binding.content.redirectHostCounterTextView.setText(formatCount(redirectNow));
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void attachHostCounterObservers() {
        if (hostCountersAttached) return;
        if (this.blockedHostCountLiveData != null && this.blockedHostCountObserver != null) {
            this.blockedHostCountLiveData.observe(getViewLifecycleOwner(), this.blockedHostCountObserver);
        }
        if (this.allowedHostCountLiveData != null && this.allowedHostCountObserver != null) {
            this.allowedHostCountLiveData.observe(getViewLifecycleOwner(), this.allowedHostCountObserver);
        }
        if (this.redirectHostCountLiveData != null && this.redirectHostCountObserver != null) {
            this.redirectHostCountLiveData.observe(getViewLifecycleOwner(),
                    this.redirectHostCountObserver);
        }
        hostCountersAttached = true;
    }

    private void detachHostCounterObservers() {
        if (!hostCountersAttached) return;
        if (this.blockedHostCountLiveData != null && this.blockedHostCountObserver != null) {
            this.blockedHostCountLiveData.removeObserver(this.blockedHostCountObserver);
        }
        if (this.allowedHostCountLiveData != null && this.allowedHostCountObserver != null) {
            this.allowedHostCountLiveData.removeObserver(this.allowedHostCountObserver);
        }
        if (this.redirectHostCountLiveData != null && this.redirectHostCountObserver != null) {
            this.redirectHostCountLiveData.removeObserver(this.redirectHostCountObserver);
        }
        hostCountersAttached = false;
    }

    private void primeCountersOnceDuringImport() {
        if (hostCountersPrimedDuringImport) return;
        hostCountersPrimedDuringImport = true;

        Integer blocked = blockedHostCountLiveData != null
                ? blockedHostCountLiveData.getValue() : null;
        Integer allowed = allowedHostCountLiveData != null
                ? allowedHostCountLiveData.getValue() : null;
        Integer redirected = redirectHostCountLiveData != null
                ? redirectHostCountLiveData.getValue() : null;
        if (blocked != null && initialBlockedCount < 0) {
            initialBlockedCount = blocked;
            this.homeViewModel.setCachedInitialBlockedCount(initialBlockedCount);
            this.binding.content.blockedHostCounterTextView.setText(formatCount(blocked));
        }
        if (allowed != null) {
            this.binding.content.allowedHostCounterTextView.setText(formatCount(allowed));
        }
        if (redirected != null) {
            this.binding.content.redirectHostCounterTextView.setText(formatCount(redirected));
        }
        if (blocked != null && allowed != null && redirected != null) return;

        final Context appContext = requireContext().getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                int allowedNow = AppDatabase.getInstance(appContext)
                        .hostsListItemDao().getAllowedHostCountNow();
                int redirectNow = AppDatabase.getInstance(appContext)
                        .hostsListItemDao().getRedirectHostCountNow();
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    this.binding.content.allowedHostCounterTextView.setText(formatCount(allowedNow));
                    this.binding.content.redirectHostCounterTextView.setText(formatCount(redirectNow));
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void bindSourceCounter() {
        Resources resources = getResources();

        TextView upToDateSourcesTextView = this.binding.content.upToDateSourcesTextView;
        LiveData<Integer> upToDateSourceCount = this.homeViewModel.getUpToDateSourceCount();
        upToDateSourceCount.observe(getViewLifecycleOwner(), count ->
                upToDateSourcesTextView.setText(
                        resources.getQuantityString(R.plurals.up_to_date_source_label, count, count)));

        TextView outdatedSourcesTextView = this.binding.content.outdatedSourcesTextView;
        LiveData<Integer> outdatedSourceCount = this.homeViewModel.getOutdatedSourceCount();
        outdatedSourceCount.observe(getViewLifecycleOwner(), count ->
                outdatedSourcesTextView.setText(
                        resources.getQuantityString(R.plurals.outdated_source_label, count, count)));
    }

    private void bindPending() {
        this.homeViewModel.getPending().observe(getViewLifecycleOwner(), pending -> {
            if (this.binding == null) return;
            renderProtectionControl();
            if (pending) {
                showView(this.binding.content.sourcesProgressBar);
                // Clear the previous operation's text until the new state is published.
                removeView(this.binding.content.stateTextView);
            } else {
                removeView(this.binding.content.sourcesProgressBar);
            }
        });
    }

    private void bindState() {
        this.homeViewModel.getState().observe(getViewLifecycleOwner(), status -> {
            if (this.binding == null) return;
            this.binding.content.stateTextView.setText(status == null ? null : status.resolve(requireContext()));
            if (status == null) {
                removeView(this.binding.content.stateTextView);
            } else {
                showView(this.binding.content.stateTextView);
            }
        });
    }

    private void bindClickListeners() {
        this.binding.content.blockedHostCardView
                .setOnClickListener(v -> startHostListActivity(BLOCKED_HOSTS_TAB));
        this.binding.content.allowedHostCardView
                .setOnClickListener(v -> startHostListActivity(ALLOWED_HOSTS_TAB));
        this.binding.content.redirectHostCardView
                .setOnClickListener(v -> startHostListActivity(REDIRECTED_HOSTS_TAB));
        this.binding.content.sourcesCardView
                .setOnClickListener(v -> {
                    if (getActivity() instanceof HomeActivity) {
                        ((HomeActivity) getActivity()).navigateTo(R.id.nav_sources);
                    }
                });
        this.binding.content.checkForUpdateImageView
                .setOnClickListener(v -> this.homeViewModel.update());
        this.binding.content.updateImageView
                .setOnClickListener(v -> this.homeViewModel.sync());
    }

    private void bindProtectionControl() {
        this.binding.content.protectionSwitch.setOnClickListener(view -> {
            boolean enabled = this.binding.content.protectionSwitch.isChecked();
            renderProtectionControl();
            if (getActivity() instanceof HomeActivity) {
                ((HomeActivity) getActivity()).setProtectionEnabled(enabled);
            }
        });
        this.homeViewModel.getProtectionControlPending().observe(
                getViewLifecycleOwner(), pending -> renderProtectionControl());
        renderProtectionControl();
    }

    private void renderProtectionControl() {
        if (this.binding == null) return;
        Boolean applied = this.homeViewModel.isAdBlocked().getValue();
        this.binding.content.protectionSwitch.setChecked(Boolean.TRUE.equals(applied));
        this.binding.content.protectionSwitch.setEnabled(
                applied != null && this.homeViewModel.canChangeProtection());
    }

    private void bindLeakStatus() {
        this.binding.content.leakStatusCardView
                .setOnClickListener(v -> startActivity(new Intent(requireContext(), PrefsActivity.class)));
        this.binding.content.leakPrivateDnsButton
                .setOnClickListener(v -> openPrivateDnsSettings());
        this.binding.content.leakVpnSettingsButton
                .setOnClickListener(v -> openVpnSettings());
        if (PreferenceHelper.getAdBlockMethod(requireContext()) == VPN) {
            getViewLifecycleOwner().getLifecycle().addObserver(
                    new VpnLeakStatusObserver(requireContext(), this::renderLeakStatus));
        }
        renderLeakStatus();
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private void startHostListActivity(int tab) {
        Intent intent = new Intent(requireContext(), ListsActivity.class);
        intent.putExtra(TAB, tab);
        startActivity(intent);
    }

    private void notifyAdBlocked(boolean adBlocked) {
        if (this.binding == null) return;
        renderProtectionControl();
        this.binding.content.headerFrameLayout.setBackgroundColor(
                getResources().getColor(R.color.ui_bg, null));
        renderLeakStatus();
    }

    private void renderProtectionStatus() {
        if (this.binding == null) return;
        boolean applied = Boolean.TRUE.equals(this.homeViewModel.isAdBlocked().getValue());
        Integer blocked = this.blockedHostCountLiveData == null
                ? null : this.blockedHostCountLiveData.getValue();
        boolean vpn = PreferenceHelper.getAdBlockMethod(requireContext()) == VPN;
        int status = protectionStatus(applied, vpn, this.vpnTunnelEstablished,
                vpn && PreferenceHelper.getVpnServiceStatus(requireContext()) == VpnStatus.WAITING_FOR_NETWORK,
                blocked);
        this.binding.content.protectionStatusTextView.setMaxLines(Integer.MAX_VALUE);
        this.binding.content.protectionStatusTextView.setEllipsize(null);
        this.binding.content.protectionStatusTextView.setText(status);
        int color = !applied ? R.color.home_protection_off_text
                : status == R.string.home_protection_status_active
                ? R.color.home_protection_on_text : R.color.ui_text_primary;
        this.binding.content.protectionStatusTextView.setTextColor(
                getResources().getColor(color, null));
    }

    static int protectionStatus(boolean applied, boolean vpn, boolean established,
                                boolean waiting, @Nullable Integer blocked) {
        if (!applied) return R.string.home_protection_status_inactive;
        if (vpn && waiting) return R.string.home_protection_status_vpn_waiting;
        if (vpn && !established) return R.string.home_protection_status_vpn_disconnected;
        if (blocked == null) return vpn ? R.string.home_protection_status_vpn_checking_rules
                : R.string.home_protection_status_checking_rules;
        if (blocked <= 0) return vpn ? R.string.home_protection_status_vpn_no_rules
                : R.string.home_protection_status_no_rules;
        return R.string.home_protection_status_active;
    }

    private void notifyError(HostError error) {
        if (this.binding == null) return;
        dismissDownloadErrorDialog();
        if (error == null) return;
        removeView(this.binding.content.stateTextView);

        if (error == HostError.DOWNLOAD_FAILED) {
            this.downloadErrorDialog = new MaterialAlertDialogBuilder(requireContext())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(error.getMessageKey())
                    .setMessage(error.getDetailsKey())
                    .setPositiveButton(R.string.nav_sources, (dialog, id) -> {
                        if (acknowledgeDownloadError(dialog)) {
                            ((HomeActivity) requireActivity()).navigateTo(R.id.nav_sources);
                        }
                    })
                    .setNegativeButton(R.string.button_close, (dialog, id) ->
                            acknowledgeDownloadError(dialog))
                    .setOnCancelListener(this::acknowledgeDownloadError)
                    .show();
            return;
        }
        String message = getString(error.getDetailsKey()) + "\n\n"
                + getString(R.string.error_dialog_help);
        new MaterialAlertDialogBuilder(requireContext())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(error.getMessageKey())
                .setMessage(message)
                .setPositiveButton(R.string.button_close, (dialog, id) -> dialog.dismiss())
                .setNegativeButton(R.string.button_help, (dialog, id) -> {
                    dialog.dismiss();
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/AdAway/AdAway/wiki")));
                })
                .show();
    }

    private void dismissDownloadErrorDialog() {
        if (this.downloadErrorDialog == null) return;
        this.downloadErrorDialog.dismiss();
        this.downloadErrorDialog = null;
    }

    private boolean acknowledgeDownloadError(DialogInterface dialog) {
        // A queued button/cancel callback may belong to a replaced dialog or Home view.
        if (dialog != this.downloadErrorDialog) return false;
        this.homeViewModel.acknowledgeError(HostError.DOWNLOAD_FAILED);
        return true;
    }

    private void renderLeakStatus() {
        if (this.binding == null) return;
        LeakStatus status = LeakStatus.from(requireContext());
        onVpnTunnelObserved(status.vpnRunning);
        int riskCount = status.riskCount();
        this.binding.content.leakStatusSummaryTextView.setText(status.hasRisks()
                ? getResources().getQuantityString(
                R.plurals.leak_status_summary_risky, riskCount, riskCount)
                : getString(R.string.leak_status_summary_clean));
        this.binding.content.leakStatusDetailTextView.setText(buildLeakStatusDetails(status));
        boolean showPrivateDnsButton = status.hasPrivateDnsRisk();
        boolean showVpnSettingsButton = status.method == VPN;
        this.binding.content.leakPrivateDnsButton.setVisibility(
                showPrivateDnsButton ? View.VISIBLE : View.GONE);
        this.binding.content.leakVpnSettingsButton.setVisibility(
                showVpnSettingsButton ? View.VISIBLE : View.GONE);
        this.binding.content.leakStatusActions.setVisibility(
                showPrivateDnsButton || showVpnSettingsButton ? View.VISIBLE : View.GONE);
    }

    void onVpnTunnelObserved(boolean established) {
        this.vpnTunnelEstablished = established;
        renderProtectionStatus();
    }

    private String buildLeakStatusDetails(LeakStatus status) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, getProtectionModeLine(status));
        appendLine(builder, getPrivateDnsLine(status));
        appendLine(builder, getDohLine(status));
        appendLine(builder, getVpnBypassLine(status));
        if (status.method == VPN) {
            appendLine(builder, getString(R.string.leak_status_strict_hint));
        }
        return builder.toString();
    }

    private String getProtectionModeLine(LeakStatus status) {
        switch (status.method) {
            case ROOT:
                return getString(R.string.leak_status_mode_root);
            case VPN:
                return getString(status.vpnRunning
                        ? R.string.leak_status_mode_vpn_running
                        : R.string.leak_status_mode_vpn_stopped);
            case UNDEFINED:
            default:
                return getString(R.string.leak_status_mode_unconfigured);
        }
    }

    private String getPrivateDnsLine(LeakStatus status) {
        if (status.isPrivateDnsUnknown()) {
            return getString(R.string.leak_status_private_dns_unknown);
        }
        if (!status.isPrivateDnsActive()) {
            return getString(R.string.leak_status_private_dns_off);
        }
        if (LeakStatus.PRIVATE_DNS_MODE_HOSTNAME.equals(status.privateDnsMode)
                && status.privateDnsSpecifier != null
                && !status.privateDnsSpecifier.isEmpty()) {
            return getString(R.string.leak_status_private_dns_provider,
                    status.privateDnsSpecifier);
        }
        return getString(R.string.leak_status_private_dns_auto);
    }

    private String getDohLine(LeakStatus status) {
        return status.hasCommonDohRouteCoverage()
                ? getString(R.string.leak_status_doh_limited)
                : getString(R.string.leak_status_doh_risk);
    }

    private String getVpnBypassLine(LeakStatus status) {
        if (status.method != VPN) {
            return getString(R.string.leak_status_excluded_none);
        }
        StringBuilder builder = new StringBuilder();
        if (status.vpnBypassAllowed) {
            builder.append(getString(R.string.leak_status_app_bypass_allowed));
        }
        if (status.excludedUserAppCount > 0) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(getResources().getQuantityString(
                    R.plurals.leak_status_excluded_user_apps,
                    status.excludedUserAppCount,
                    status.excludedUserAppCount));
        } else if (builder.length() == 0) {
            builder.append(getString(R.string.leak_status_excluded_none));
        }
        if (!LeakStatus.VPN_EXCLUDED_SYSTEM_NONE.equals(status.excludedSystemApps)) {
            builder.append(" · ");
            builder.append("all".equals(status.excludedSystemApps)
                    ? getString(R.string.leak_status_excluded_system_all)
                    : getString(R.string.leak_status_excluded_system_browsers));
        }
        return builder.toString();
    }

    private void openPrivateDnsSettings() {
        Intent intent = new Intent("android.settings.PRIVATE_DNS_SETTINGS");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        }
    }

    private void openVpnSettings() {
        startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));
    }

    private static void appendLine(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private void navigateToDiscover() {
        if (getActivity() instanceof HomeActivity) {
            ((HomeActivity) getActivity()).navigateTo(R.id.nav_discover);
        }
    }

    private static String formatCount(long value) {
        if (value < 1000) return String.valueOf(value);
        int unit = 1000;
        int exp = (int) (Math.log(value) / Math.log(unit));
        String pre = "kMGTPE".charAt(exp - 1) + "";
        return String.format(java.util.Locale.ROOT, "%.1f%s", value / Math.pow(unit, exp), pre);
    }

    private static int getBlockedEntryCountNow(@NonNull Context context) {
        if (PreferenceHelper.getAdBlockMethod(context) == ROOT) {
            return AppDatabase.getInstance(context).hostEntryDao().getBlockedExactEntryCountNow();
        }
        return AppDatabase.getInstance(context).hostEntryDao().getBlockedEntryCountNow();
    }
}
