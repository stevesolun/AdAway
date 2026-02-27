package org.adaway.ui.home;

import static android.app.Activity.RESULT_OK;
import static org.adaway.model.adblocking.AdBlockMethod.UNDEFINED;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.ui.Animations.removeView;
import static org.adaway.ui.Animations.showView;
import static org.adaway.ui.lists.ListsActivity.ALLOWED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.BLOCKED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.REDIRECTED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.TAB;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.net.VpnService;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.adaway.R;
import org.adaway.databinding.FragmentHomeBinding;
import org.adaway.db.AppDatabase;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.error.HostError;
import org.adaway.model.source.SourceModel.MultiPhaseProgress;
import org.adaway.ui.hosts.FilterListsSubscribeAllWorker;
import org.adaway.ui.hosts.FilterSetUpdateService;
import org.adaway.ui.hosts.FilterSetUpdateWorker;
import org.adaway.ui.hosts.HostsSourcesActivity;
import org.adaway.ui.lists.ListsActivity;
import org.adaway.util.AppExecutors;

import java.util.concurrent.Executor;
import java.text.NumberFormat;
import java.util.Locale;

import timber.log.Timber;

/**
 * Fragment displaying the main home screen (status, stats, update progress).
 * Mirrors the logic that was previously in HomeActivity.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel;
    private ActivityResultLauncher<Intent> prepareVpnLauncher;
    private Snackbar filterListsProgressSnackbar;
    private Snackbar scheduledUpdateSnackbar;
    private boolean sourceModelProgressActive = false;
    private boolean scheduledProgressActive = false;

    // UI-side monotonic guards
    private int filterListsLastDone = -1;
    private int filterListsLastTotal = -1;
    private int scheduledLastDone = -1;
    private int scheduledLastTotal = -1;
    // Initial blocked count when progress starts
    private long initialBlockedCount = -1;

    private static final NumberFormat COUNT_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private static final Executor COUNTS_EXECUTOR = AppExecutors.getInstance().diskIO();

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
    private boolean hostCountersPrimedDuringImport = false;

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
        bindFab();
        bindFilterListsSubscribeAllProgress();
        bindScheduledUpdateProgress();
        bindSourceModelProgress();
        bindMultiPhaseProgress();

        bindDiscoverCta();

        this.prepareVpnLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                if (PreferenceHelper.getAdBlockMethod(requireContext()) == VPN) {
                    Boolean isBlocked = this.homeViewModel.isAdBlocked().getValue();
                    if (isBlocked == null || !isBlocked) {
                        this.homeViewModel.toggleAdBlocking();
                    }
                }
            } else {
                PreferenceHelper.setAbBlockMethod(requireContext(), UNDEFINED);
                new MaterialAlertDialogBuilder(requireContext())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.welcome_vpn_method_title)
                        .setMessage(R.string.welcome_vpn_alwayson_blocked_description)
                        .setPositiveButton(R.string.button_close, (d, which) -> d.dismiss())
                        .show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (this.filterListsProgressSnackbar != null) {
            this.filterListsProgressSnackbar.dismiss();
            this.filterListsProgressSnackbar = null;
        }
        if (this.scheduledUpdateSnackbar != null) {
            this.scheduledUpdateSnackbar.dismiss();
            this.scheduledUpdateSnackbar = null;
        }
        this.binding = null;
    }

    // -----------------------------------------------------------------------------------------
    // Progress bindings (ported from HomeActivity)
    // -----------------------------------------------------------------------------------------

    private void bindFilterListsSubscribeAllProgress() {
        WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData(FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME)
                .observe(getViewLifecycleOwner(), infos -> {
                    if (this.binding == null) return;
                    WorkInfo info = null;
                    if (infos != null) {
                        for (WorkInfo wi : infos) {
                            if (wi.getState() == WorkInfo.State.RUNNING) {
                                info = wi;
                                break;
                            }
                        }
                        if (info == null) {
                            for (WorkInfo wi : infos) {
                                if (wi.getState() == WorkInfo.State.ENQUEUED) {
                                    info = wi;
                                    break;
                                }
                            }
                        }
                    }
                    boolean running = info != null && (info.getState() == WorkInfo.State.RUNNING
                            || info.getState() == WorkInfo.State.ENQUEUED);
                    if (!running || info == null) {
                        if (filterListsProgressSnackbar != null) {
                            filterListsProgressSnackbar.dismiss();
                            filterListsProgressSnackbar = null;
                        }
                        filterListsLastDone = -1;
                        filterListsLastTotal = -1;
                        if (!sourceModelProgressActive && !scheduledProgressActive) {
                            removeView(this.binding.content.filterListsSubscribeProgressTextView);
                            removeView(this.binding.content.filterListsSubscribeProgressBar);
                        }
                        return;
                    }

                    int done = info.getProgress().getInt(FilterListsSubscribeAllWorker.PROGRESS_DONE, 0);
                    int total = info.getProgress().getInt(FilterListsSubscribeAllWorker.PROGRESS_TOTAL, 0);

                    if (total != filterListsLastTotal) {
                        filterListsLastTotal = total;
                        filterListsLastDone = -1;
                    }
                    if (done < filterListsLastDone) return;
                    filterListsLastDone = done;
                    String currentName = info.getProgress()
                            .getString(FilterListsSubscribeAllWorker.PROGRESS_CURRENT_NAME);
                    int percent = total > 0 ? (int) Math.floor(done * 100.0 / total) : 0;
                    String msg;
                    if (total <= 0) {
                        msg = "FilterLists subscribing: Preparing\u2026";
                    } else {
                        msg = "FilterLists subscribing: " + done + "/" + total + " (" + percent + "%)";
                    }
                    if (currentName != null && !currentName.isEmpty()) {
                        msg += " \u2022 " + currentName;
                    }

                    this.binding.content.filterListsSubscribeProgressTextView.setText(msg);
                    this.binding.content.filterListsSubscribeProgressTextView
                            .setOnClickListener(v -> navigateToDiscover());
                    showView(this.binding.content.filterListsSubscribeProgressTextView);

                    this.binding.content.filterListsSubscribeProgressBar.setIndeterminate(total <= 0);
                    if (total > 0) {
                        this.binding.content.filterListsSubscribeProgressBar.setMax(100);
                        this.binding.content.filterListsSubscribeProgressBar.setProgressCompat(percent, true);
                    }
                    showView(this.binding.content.filterListsSubscribeProgressBar);

                    if (filterListsProgressSnackbar == null) {
                        filterListsProgressSnackbar = Snackbar
                                .make(this.binding.getRoot(), msg, Snackbar.LENGTH_INDEFINITE)
                                .setAction("View", v -> navigateToDiscover());
                        filterListsProgressSnackbar.show();
                    } else {
                        filterListsProgressSnackbar.setText(msg);
                    }
                });
    }

    private void bindScheduledUpdateProgress() {
        WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData(FilterSetUpdateService.WORK_NAME)
                .observe(getViewLifecycleOwner(), infos -> {
                    if (this.binding == null) return;
                    WorkInfo info = null;
                    if (infos != null) {
                        for (WorkInfo wi : infos) {
                            if (wi.getState() == WorkInfo.State.RUNNING) {
                                info = wi;
                                break;
                            }
                        }
                    }
                    if (info == null || info.getState() != WorkInfo.State.RUNNING) {
                        if (scheduledUpdateSnackbar != null) {
                            scheduledUpdateSnackbar.dismiss();
                            scheduledUpdateSnackbar = null;
                        }
                        scheduledLastDone = -1;
                        scheduledLastTotal = -1;
                        scheduledProgressActive = false;
                        return;
                    }
                    scheduledProgressActive = true;

                    int done = info.getProgress().getInt(FilterSetUpdateWorker.PROGRESS_DONE, 0);
                    int total = info.getProgress().getInt(FilterSetUpdateWorker.PROGRESS_TOTAL, 0);

                    if (total != scheduledLastTotal) {
                        scheduledLastTotal = total;
                        scheduledLastDone = -1;
                    }
                    if (done < scheduledLastDone) return;
                    scheduledLastDone = done;
                    String current = info.getProgress().getString(FilterSetUpdateWorker.PROGRESS_CURRENT);
                    int percent = total > 0 ? (int) Math.floor(done * 100.0 / total) : 0;

                    String msg = "Scheduled update: " + done + "/" + total + " (" + percent + "%)";
                    if (current != null && !current.isEmpty()) msg += " \u2022 " + current;

                    if (filterListsProgressSnackbar == null) {
                        this.binding.content.filterListsSubscribeProgressTextView.setText(msg);
                        showView(this.binding.content.filterListsSubscribeProgressTextView);
                        this.binding.content.filterListsSubscribeProgressBar.setIndeterminate(total <= 0);
                        if (total > 0) {
                            this.binding.content.filterListsSubscribeProgressBar.setMax(100);
                            this.binding.content.filterListsSubscribeProgressBar.setProgressCompat(percent, true);
                        }
                        showView(this.binding.content.filterListsSubscribeProgressBar);
                    }

                    if (scheduledUpdateSnackbar == null) {
                        scheduledUpdateSnackbar = Snackbar.make(this.binding.getRoot(), msg,
                                Snackbar.LENGTH_INDEFINITE);
                        scheduledUpdateSnackbar.show();
                    } else {
                        scheduledUpdateSnackbar.setText(msg);
                    }
                });
    }

    private void bindSourceModelProgress() {
        this.homeViewModel.getSourceProgress().observe(getViewLifecycleOwner(), progress -> {
            if (this.binding == null) return;
            MultiPhaseProgress multiPhase = this.homeViewModel.getMultiPhaseProgress().getValue();
            if (multiPhase != null && multiPhase.isActive()) {
                removeView(this.binding.content.filterListsSubscribeProgressTextView);
                removeView(this.binding.content.filterListsSubscribeProgressBar);
                return;
            }

            if (progress == null || !progress.isActive()) {
                sourceModelProgressActive = false;
                if (filterListsProgressSnackbar == null && !scheduledProgressActive) {
                    removeView(this.binding.content.filterListsSubscribeProgressTextView);
                    removeView(this.binding.content.filterListsSubscribeProgressBar);
                }
                return;
            }
            sourceModelProgressActive = true;

            if (multiPhase != null && multiPhase.isActive()) {
                removeView(this.binding.content.filterListsSubscribeProgressTextView);
                removeView(this.binding.content.filterListsSubscribeProgressBar);
                return;
            }

            int done = progress.done;
            int total = progress.total;
            double pct = progress.basisPoints / 100.0;
            int percentForBar = (int) Math.floor(pct);
            if (percentForBar <= 0 && progress.isActive()) percentForBar = 1;
            if (pct > 99.9) pct = 100.0;
            String msg = "Updating sources: " + done + "/" + total + " ("
                    + String.format(java.util.Locale.ROOT, "%.1f", pct) + "%)";
            if (progress.currentLabel != null && !progress.currentLabel.isEmpty()) {
                msg += " \u2022 " + progress.currentLabel;
            }
            if (progress.currentSourcePercent > 0 && progress.currentSourcePercent < 100) {
                msg += " \u2022 " + progress.currentSourcePercent + "% of this list";
            }

            if (filterListsProgressSnackbar == null) {
                this.binding.content.filterListsSubscribeProgressTextView.setText(msg);
                showView(this.binding.content.filterListsSubscribeProgressTextView);
                this.binding.content.filterListsSubscribeProgressBar.setIndeterminate(total <= 0);
                if (total > 0) {
                    this.binding.content.filterListsSubscribeProgressBar.setMax(100);
                    this.binding.content.filterListsSubscribeProgressBar.setProgressCompat(percentForBar, true);
                }
                showView(this.binding.content.filterListsSubscribeProgressBar);
            }
        });
    }

    private void bindMultiPhaseProgress() {
        this.binding.content.pauseResumeButton.setOnClickListener(v -> {
            MultiPhaseProgress progress = this.homeViewModel.getMultiPhaseProgress().getValue();
            if (progress == null) return;
            if (progress.isPaused) {
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

        this.homeViewModel.getMultiPhaseProgress().observe(getViewLifecycleOwner(), progress -> {
            if (this.binding == null) return;
            if (progress == null || !progress.isActive()) {
                removeView(this.binding.content.multiPhaseProgressContainer);
                initialBlockedCount = -1;
                hostCountersPrimedDuringImport = false;
                attachHostCounterObservers();
                return;
            }

            showView(this.binding.content.multiPhaseProgressContainer);
            detachHostCounterObservers();
            primeCountersOnceDuringImport();

            if (initialBlockedCount < 0) {
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
                    COUNTS_EXECUTOR.execute(() -> {
                        try {
                            int blockedNow = AppDatabase.getInstance(requireContext())
                                    .hostEntryDao().getBlockedEntryCountNow();
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

            long baseline = Math.max(0, initialBlockedCount);
            long liveBlockedCount = Math.max(baseline, progress.parsedHostCount);
            this.binding.content.blockedHostCounterTextView.setText(formatCount(liveBlockedCount));

            double overallPercentDouble = progress.getOverallPercentDouble();
            int overallPercent = (int) overallPercentDouble;
            this.binding.content.overallProgressBar.setMax(100);
            this.binding.content.overallProgressBar.setProgress(overallPercent);
            String progressText;
            if (progress.parsedHostCount > 0) {
                progressText = String.format(java.util.Locale.ROOT, "%.1f%% Complete \u2022 %,d blocked",
                        overallPercentDouble, progress.parsedHostCount);
            } else {
                progressText = String.format(java.util.Locale.ROOT, "%.1f%% Complete", overallPercentDouble);
            }
            this.binding.content.overallProgressText.setText(progressText);

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

            int totalToCheck = progress.totalToCheck;
            int checked = progress.checkedCount;
            int downloadBarProgress = totalToCheck > 0 ? (int) ((long) checked * 100 / totalToCheck) : 0;
            this.binding.content.downloadProgressBar.setMax(100);
            this.binding.content.downloadProgressBar.setProgress(downloadBarProgress);
            this.binding.content.downloadPhasePercent
                    .setText(String.format(java.util.Locale.ROOT, "%d/%d", checked, totalToCheck));

            double parsePercent = progress.getParsePercentDouble();
            this.binding.content.parseProgressBar.setMax(100);
            this.binding.content.parseProgressBar.setProgress((int) parsePercent);
            if (parsePercent > 99.9) parsePercent = 100.0;
            this.binding.content.parsePhasePercent
                    .setText(String.format(java.util.Locale.ROOT, "%.1f%%", parsePercent));

            if (progress.schedulerTaskName != null && !progress.schedulerTaskName.isEmpty()) {
                this.binding.content.schedulerTaskContainer.setVisibility(View.VISIBLE);
                this.binding.content.schedulerTaskName
                        .setText("Scheduled: " + progress.schedulerTaskName);
            } else {
                this.binding.content.schedulerTaskContainer.setVisibility(View.GONE);
            }

            if (progress.isPaused) {
                this.binding.content.pauseResumeButton.setImageResource(R.drawable.ic_play_24dp);
                this.binding.content.pauseResumeButton
                        .setContentDescription(getString(R.string.resume_update));
            } else {
                this.binding.content.pauseResumeButton.setImageResource(R.drawable.ic_pause_24dp);
                this.binding.content.pauseResumeButton
                        .setContentDescription(getString(R.string.pause_update));
            }
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
        versionTextView.setText(this.homeViewModel.getVersionName());
        versionTextView.setOnClickListener(v ->
                startActivity(new Intent(requireContext(),
                        org.adaway.ui.update.UpdateActivity.class)));

        this.homeViewModel.getAppManifest().observe(getViewLifecycleOwner(), manifest -> {
            if (manifest.updateAvailable) {
                versionTextView.setTypeface(versionTextView.getTypeface(), Typeface.BOLD);
                versionTextView.setText(R.string.update_available);
            }
        });
    }

    private void bindHostCounter() {
        this.blockedHostCountLiveData = this.homeViewModel.getBlockedHostCount();
        this.allowedHostCountLiveData = this.homeViewModel.getAllowedHostCount();
        this.redirectHostCountLiveData = this.homeViewModel.getRedirectHostCount();

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

    private void refreshHostCountersOnce() {
        if (hostCountersPrimedDuringImport) return;
        hostCountersPrimedDuringImport = true;
        COUNTS_EXECUTOR.execute(() -> {
            try {
                int allowedNow = AppDatabase.getInstance(requireContext())
                        .hostsListItemDao().getAllowedHostCountNow();
                int redirectNow = AppDatabase.getInstance(requireContext())
                        .hostsListItemDao().getRedirectHostCountNow();
                AppExecutors.getInstance().mainThread().execute(() -> {
                    if (this.binding == null) return;
                    this.binding.content.allowedHostCounterTextView.setText(formatCount(allowedNow));
                    this.binding.content.redirectHostCounterTextView.setText(formatCount(redirectNow));
                    CharSequence blockedText =
                            this.binding.content.blockedHostCounterTextView.getText();
                    if (blockedText == null || blockedText.length() == 0) {
                        this.binding.content.blockedHostCounterTextView.setText(formatCount(0));
                    }
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

        COUNTS_EXECUTOR.execute(() -> {
            try {
                int allowedNow = AppDatabase.getInstance(requireContext())
                        .hostsListItemDao().getAllowedHostCountNow();
                int redirectNow = AppDatabase.getInstance(requireContext())
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
            if (pending) {
                showView(this.binding.content.sourcesProgressBar);
                showView(this.binding.content.stateTextView);
            } else {
                removeView(this.binding.content.sourcesProgressBar);
            }
        });
    }

    private void bindState() {
        this.homeViewModel.getState().observe(getViewLifecycleOwner(), text -> {
            if (this.binding == null) return;
            this.binding.content.stateTextView.setText(text);
            if (text.isEmpty()) {
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
                .setOnClickListener(v -> startActivity(
                        new Intent(requireContext(), HostsSourcesActivity.class)));
        this.binding.content.checkForUpdateImageView
                .setOnClickListener(v -> this.homeViewModel.update());
        this.binding.content.updateImageView
                .setOnClickListener(v -> this.homeViewModel.sync());
    }

    private void bindFab() {
        this.binding.fab.setOnClickListener(v -> {
            MultiPhaseProgress progress = this.homeViewModel.getMultiPhaseProgress().getValue();
            if (progress != null && progress.isActive()) {
                if (progress.isPaused) {
                    this.homeViewModel.resumeUpdate();
                } else {
                    this.homeViewModel.pauseUpdate();
                }
            } else {
                AdBlockMethod method = PreferenceHelper.getAdBlockMethod(requireContext());
                if (method == VPN) {
                    Intent prepareIntent = VpnService.prepare(requireContext());
                    if (prepareIntent != null) {
                        this.prepareVpnLauncher.launch(prepareIntent);
                        return;
                    }
                }
                this.homeViewModel.toggleAdBlocking();
            }
        });
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
        this.binding.content.headerFrameLayout.setBackgroundColor(
                getResources().getColor(R.color.ui_bg, null));
        if (adBlocked) {
            this.binding.fab.setImageResource(R.drawable.icon_foreground_red);
        } else {
            this.binding.fab.setImageResource(R.drawable.icon_foreground_white);
        }
    }

    private void notifyError(HostError error) {
        if (this.binding == null) return;
        removeView(this.binding.content.stateTextView);
        if (error == null) return;

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
}
