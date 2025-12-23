package org.adaway.ui.home;

import static android.app.Activity.RESULT_OK;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;
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
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.adaway.R;
import org.adaway.databinding.HomeActivityBinding;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.error.HostError;
import org.adaway.ui.help.HelpActivity;
import org.adaway.ui.hosts.HostsSourcesActivity;
import org.adaway.ui.hosts.FilterListsImportActivity;
import org.adaway.ui.hosts.FilterListsSubscribeAllWorker;
import org.adaway.ui.hosts.FilterSetUpdateService;
import org.adaway.ui.hosts.FilterSetUpdateWorker;
import org.adaway.ui.lists.ListsActivity;
import org.adaway.ui.log.LogActivity;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.ui.support.SupportActivity;
import org.adaway.ui.update.UpdateActivity;
import org.adaway.ui.welcome.WelcomeActivity;
import org.adaway.model.source.SourceModel.MultiPhaseProgress;

import kotlin.jvm.functions.Function1;
import timber.log.Timber;

/**
 * This class is the application main activity.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HomeActivity extends AppCompatActivity {
    /**
     * The project link.
     */
    private static final String PROJECT_LINK = "https://github.com/AdAway/AdAway";

    private HomeActivityBinding binding;
    private BottomSheetBehavior<View> drawerBehavior;
    private OnBackPressedCallback onBackPressedCallback;
    private HomeViewModel homeViewModel;
    private ActivityResultLauncher<Intent> prepareVpnLauncher;
    private Snackbar filterListsProgressSnackbar;
    private Snackbar scheduledUpdateSnackbar;
    private boolean sourceModelProgressActive = false;
    private boolean scheduledProgressActive = false;
    
    // UI-side monotonic guards: WorkManager delivers progress async and out-of-order
    private int filterListsLastDone = -1;
    private int filterListsLastTotal = -1;
    private int scheduledLastDone = -1;
    private int scheduledLastTotal = -1;
    // Initial blocked count when progress starts (for live counter update)
    private long initialBlockedCount = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        NotificationHelper.clearUpdateNotifications(this);
        Timber.i("Starting main activity");
        this.binding = HomeActivityBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());

        this.homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        this.homeViewModel.isAdBlocked().observe(this, this::notifyAdBlocked);
        this.homeViewModel.getError().observe(this, this::notifyError);

        applyActionBar();
        bindAppVersion();
        bindHostCounter();
        bindSourceCounter();
        bindPending();
        bindState();
        bindClickListeners();
        setUpBottomDrawer();
        bindFab();
        bindFilterListsSubscribeAllProgress();
        bindScheduledUpdateProgress();
        bindSourceModelProgress();
        bindMultiPhaseProgress();

        this.binding.navigationView.setNavigationItemSelectedListener(item -> {
            if (showFragment(item.getItemId())) {
                this.drawerBehavior.setState(STATE_HIDDEN);
            }
            return false; // TODO Handle selection
        });

        this.prepareVpnLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                // Permission granted: only start ad-blocking if VPN is still the selected method.
                if (PreferenceHelper.getAdBlockMethod(this) == VPN) {
                    Boolean isBlocked = this.homeViewModel.isAdBlocked().getValue();
                    if (isBlocked == null || !isBlocked) {
                        // Use the model toggle so UI state stays consistent via LiveData.
                        this.homeViewModel.toggleAdBlocking();
                    }
                }
            } else {
                // Permission denied/canceled: revert to UNDEFINED so we don't keep prompting on every resume.
                PreferenceHelper.setAbBlockMethod(this, UNDEFINED);
                new MaterialAlertDialogBuilder(this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.welcome_vpn_method_title)
                        .setMessage(R.string.welcome_vpn_alwayson_blocked_description)
                        .setPositiveButton(R.string.button_close, (d, which) -> d.dismiss())
                        .show();
            }
        });

        if (savedInstanceState == null) {
            checkUpdateAtStartup();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkFirstStep();
    }

    private void bindFilterListsSubscribeAllProgress() {
        WorkManager.getInstance(this)
                .getWorkInfosForUniqueWorkLiveData(FilterListsSubscribeAllWorker.UNIQUE_WORK_NAME)
                .observe(this, infos -> {
                    WorkInfo info = null;
                    if (infos != null) {
                        // Pick the RUNNING one first; if none, pick ENQUEUED.
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
                    boolean running = info != null && (info.getState() == WorkInfo.State.RUNNING || info.getState() == WorkInfo.State.ENQUEUED);
                    if (!running || info == null) {
                        if (filterListsProgressSnackbar != null) {
                            filterListsProgressSnackbar.dismiss();
                            filterListsProgressSnackbar = null;
                        }
                        // Reset monotonic guards when job finishes
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
                    
                    // UI-side monotonic guard: WorkManager delivers async, can be out-of-order
                    // Reset if total changes (new job); otherwise only accept if done >= last seen
                    if (total != filterListsLastTotal) {
                        filterListsLastTotal = total;
                        filterListsLastDone = -1;
                    }
                    if (done < filterListsLastDone) {
                        return; // Stale/out-of-order update, ignore
                    }
                    filterListsLastDone = done;
                    String currentName = info.getProgress().getString(FilterListsSubscribeAllWorker.PROGRESS_CURRENT_NAME);
                    int percent = total > 0 ? (int) Math.floor(done * 100.0 / total) : 0;
                    String msg;
                    if (total <= 0) {
                        msg = "FilterLists subscribing: Preparing…";
                    } else {
                        msg = "FilterLists subscribing: " + done + "/" + total + " (" + percent + "%)";
                    }
                    if (currentName != null && !currentName.isEmpty()) {
                        msg += " • " + currentName;
                    }

                    // Persistent progress on the main screen
                    this.binding.content.filterListsSubscribeProgressTextView.setText(msg);
                    this.binding.content.filterListsSubscribeProgressTextView.setOnClickListener(v ->
                            startActivity(new Intent(this, FilterListsImportActivity.class))
                    );
                    showView(this.binding.content.filterListsSubscribeProgressTextView);

                    this.binding.content.filterListsSubscribeProgressBar.setIndeterminate(total <= 0);
                    if (total > 0) {
                        this.binding.content.filterListsSubscribeProgressBar.setMax(100);
                        this.binding.content.filterListsSubscribeProgressBar.setProgressCompat(percent, true);
                    }
                    showView(this.binding.content.filterListsSubscribeProgressBar);

                    if (filterListsProgressSnackbar == null) {
                        filterListsProgressSnackbar = Snackbar.make(this.binding.getRoot(), msg, Snackbar.LENGTH_INDEFINITE)
                                .setAction("View", v -> startActivity(new Intent(this, FilterListsImportActivity.class)));
                        filterListsProgressSnackbar.show();
                    } else {
                        filterListsProgressSnackbar.setText(msg);
                    }
                });
    }

    private void bindScheduledUpdateProgress() {
        WorkManager.getInstance(this)
                .getWorkInfosForUniqueWorkLiveData(FilterSetUpdateService.WORK_NAME)
                .observe(this, infos -> {
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
                        // Reset monotonic guards when job finishes
                        scheduledLastDone = -1;
                        scheduledLastTotal = -1;
                        scheduledProgressActive = false;
                        return;
                    }
                    scheduledProgressActive = true;

                    int done = info.getProgress().getInt(FilterSetUpdateWorker.PROGRESS_DONE, 0);
                    int total = info.getProgress().getInt(FilterSetUpdateWorker.PROGRESS_TOTAL, 0);
                    
                    // UI-side monotonic guard: WorkManager delivers async, can be out-of-order
                    if (total != scheduledLastTotal) {
                        scheduledLastTotal = total;
                        scheduledLastDone = -1;
                    }
                    if (done < scheduledLastDone) {
                        return; // Stale/out-of-order update, ignore
                    }
                    scheduledLastDone = done;
                    String current = info.getProgress().getString(FilterSetUpdateWorker.PROGRESS_CURRENT);
                    int percent = total > 0 ? (int) Math.floor(done * 100.0 / total) : 0;

                    String msg = "Scheduled update: " + done + "/" + total + " (" + percent + "%)";
                    if (current != null && !current.isEmpty()) msg += " • " + current;

                    // If subscribe-all is not running, reuse the main-screen percent UI.
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
                        scheduledUpdateSnackbar = Snackbar.make(this.binding.getRoot(), msg, Snackbar.LENGTH_INDEFINITE);
                        scheduledUpdateSnackbar.show();
                    } else {
                        scheduledUpdateSnackbar.setText(msg);
                    }
                });
    }

    private void bindSourceModelProgress() {
        this.homeViewModel.getSourceProgress().observe(this, progress -> {
            if (progress == null || !progress.isActive()) {
                sourceModelProgressActive = false;
                // Only hide if nothing else is currently showing in the shared progress UI.
                if (filterListsProgressSnackbar == null && !scheduledProgressActive) {
                    removeView(this.binding.content.filterListsSubscribeProgressTextView);
                    removeView(this.binding.content.filterListsSubscribeProgressBar);
                }
                return;
            }
            sourceModelProgressActive = true;

            // Skip legacy progress display when multi-phase progress is active (avoid duplicate/confusing percentages)
            MultiPhaseProgress multiPhase = this.homeViewModel.getMultiPhaseProgress().getValue();
            if (multiPhase != null && multiPhase.isActive()) {
                // Multi-phase UI is showing, hide legacy progress to avoid confusion
                removeView(this.binding.content.filterListsSubscribeProgressTextView);
                removeView(this.binding.content.filterListsSubscribeProgressBar);
                return;
            }

            int done = progress.done;
            int total = progress.total;
            double pct = progress.basisPoints / 100.0;
            int percentForBar = (int) Math.floor(pct);
            if (percentForBar <= 0 && progress.isActive()) percentForBar = 1; // ensure it doesn't look stuck at 0
            String msg = "Updating sources: " + done + "/" + total + " (" + String.format(java.util.Locale.ROOT, "%.1f", pct) + "%)";
            if (progress.currentLabel != null && !progress.currentLabel.isEmpty()) {
                msg += " • " + progress.currentLabel;
            }
            if (progress.currentSourcePercent > 0 && progress.currentSourcePercent < 100) {
                msg += " • " + progress.currentSourcePercent + "% of this list";
            }

            // If FilterLists subscribe-all is showing, don't fight it.
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
        // Bind pause/resume and stop buttons with null check
        this.binding.content.pauseResumeButton.setOnClickListener(v -> {
            MultiPhaseProgress progress = this.homeViewModel.getMultiPhaseProgress().getValue();
            if (progress == null) {
                return; // No progress to pause/resume
            }
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

        // Observe multi-phase progress
        this.homeViewModel.getMultiPhaseProgress().observe(this, progress -> {
            if (progress == null || !progress.isActive()) {
                removeView(this.binding.content.multiPhaseProgressContainer);
                // Reset initial blocked count so LiveData takes over again
                initialBlockedCount = -1;
                return;
            }

            showView(this.binding.content.multiPhaseProgressContainer);

            // Capture initial blocked count once when progress starts
            if (initialBlockedCount < 0) {
                Integer currentBlocked = this.homeViewModel.getBlockedHostCount().getValue();
                initialBlockedCount = currentBlocked != null ? currentBlocked : 0;
            }

            // Update main blocked counter with initial + parsed hosts
            long liveBlockedCount = initialBlockedCount + progress.parsedHostCount;
            this.binding.content.blockedHostCounterTextView.setText(String.valueOf(liveBlockedCount));

            // Update overall progress bar and bird position with x.y% format
            double overallPercentDouble = progress.getOverallPercentDouble();
            int overallPercent = (int) overallPercentDouble;
            this.binding.content.overallProgressBar.setProgressCompat(overallPercent, true);
            // Show live blocked count during update
            String progressText;
            if (progress.parsedHostCount > 0) {
                progressText = String.format(java.util.Locale.ROOT, "%.1f%% Complete • %,d blocked",
                        overallPercentDouble, progress.parsedHostCount);
            } else {
                progressText = String.format(java.util.Locale.ROOT, "%.1f%% Complete", overallPercentDouble);
            }
            this.binding.content.overallProgressText.setText(progressText);

            // Animate bird icon position along the progress bar
            // Capture values in local variables to avoid memory leak from capturing 'this.binding' in post()
            View progressFrame = this.binding.content.overallProgressFrame;
            View progressBar = this.binding.content.overallProgressBar;
            View birdIcon = this.binding.content.birdProgressIcon;
            float density = getResources().getDisplayMetrics().density;
            int marginStart = 20;
            int marginStartPx = (int) (marginStart * density);
            int capturedPercent = overallPercent;

            progressFrame.post(() -> {
                int barWidth = progressBar.getWidth();
                int birdWidth = birdIcon.getWidth();
                // Calculate bird position: from left edge to right edge minus bird width
                int maxTravel = barWidth - birdWidth;
                int birdX = marginStartPx + (int) (maxTravel * capturedPercent / 100.0f);
                birdIcon.setTranslationX(birdX);
            });

            // Update individual phase progress bars with x.y% format
            double checkPercent = progress.getCheckPercentDouble();
            this.binding.content.checkProgressBar.setProgressCompat((int) checkPercent, true);
            this.binding.content.checkPhasePercent.setText(String.format(java.util.Locale.ROOT, "%.1f%%", checkPercent));

            double downloadPercent = progress.getDownloadPercentDouble();
            this.binding.content.downloadProgressBar.setProgressCompat((int) downloadPercent, true);
            this.binding.content.downloadPhasePercent.setText(String.format(java.util.Locale.ROOT, "%.1f%%", downloadPercent));

            double parsePercent = progress.getParsePercentDouble();
            this.binding.content.parseProgressBar.setProgressCompat((int) parsePercent, true);
            this.binding.content.parsePhasePercent.setText(String.format(java.util.Locale.ROOT, "%.1f%%", parsePercent));

            // Show scheduler task info if present
            if (progress.schedulerTaskName != null && !progress.schedulerTaskName.isEmpty()) {
                this.binding.content.schedulerTaskContainer.setVisibility(View.VISIBLE);
                this.binding.content.schedulerTaskName.setText("Scheduled: " + progress.schedulerTaskName);
            } else {
                this.binding.content.schedulerTaskContainer.setVisibility(View.GONE);
            }

            // Update pause/resume button state
            if (progress.isPaused) {
                this.binding.content.pauseResumeButton.setImageResource(R.drawable.ic_play_24dp);
                this.binding.content.pauseResumeButton.setContentDescription(getString(R.string.resume_update));
            } else {
                this.binding.content.pauseResumeButton.setImageResource(R.drawable.ic_pause_24dp);
                this.binding.content.pauseResumeButton.setContentDescription(getString(R.string.pause_update));
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return showFragment(item.getItemId());
    }

    private void checkFirstStep() {
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(this);
        Intent prepareIntent;
        if (adBlockMethod == UNDEFINED) {
            // Start welcome activity
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
        } else if (adBlockMethod == VPN && (prepareIntent = VpnService.prepare(this)) != null) {
            // Prepare VPN
            this.prepareVpnLauncher.launch(prepareIntent);
        }
    }

    private void checkUpdateAtStartup() {
        boolean checkAppUpdateAtStartup = PreferenceHelper.getUpdateCheckAppStartup(this);
        if (checkAppUpdateAtStartup) {
            this.homeViewModel.checkForAppUpdate();
        }
        boolean checkUpdateAtStartup = PreferenceHelper.getUpdateCheck(this);
        if (checkUpdateAtStartup) {
            this.homeViewModel.update();
        }
    }

    private void applyActionBar() {
        setSupportActionBar(this.binding.bar);
    }

    private void bindAppVersion() {
        TextView versionTextView = this.binding.content.versionTextView;
        versionTextView.setText(this.homeViewModel.getVersionName());
        versionTextView.setOnClickListener(this::showUpdate);

        this.homeViewModel.getAppManifest().observe(
                this,
                manifest -> {
                    if (manifest.updateAvailable) {
                        versionTextView.setTypeface(versionTextView.getTypeface(), Typeface.BOLD);
                        versionTextView.setText(R.string.update_available);
                    }
                }
        );
    }

    private void bindHostCounter() {
        Function1<Integer, CharSequence> stringMapper = count -> Integer.toString(count);

        TextView blockedHostCountTextView = this.binding.content.blockedHostCounterTextView;
        LiveData<Integer> blockedHostCount = this.homeViewModel.getBlockedHostCount();
        // Skip updating if multi-phase progress is active (we handle it there)
        Transformations.map(blockedHostCount, stringMapper).observe(this, text -> {
            if (initialBlockedCount < 0) {
                blockedHostCountTextView.setText(text);
            }
        });

        TextView allowedHostCountTextView = this.binding.content.allowedHostCounterTextView;
        LiveData<Integer> allowedHostCount = this.homeViewModel.getAllowedHostCount();
        Transformations.map(allowedHostCount, stringMapper).observe(this, allowedHostCountTextView::setText);

        TextView redirectHostCountTextView = this.binding.content.redirectHostCounterTextView;
        LiveData<Integer> redirectHostCount = this.homeViewModel.getRedirectHostCount();
        Transformations.map(redirectHostCount, stringMapper).observe(this, redirectHostCountTextView::setText);
    }

    private void bindSourceCounter() {
        Resources resources = getResources();

        TextView upToDateSourcesTextView = this.binding.content.upToDateSourcesTextView;
        LiveData<Integer> upToDateSourceCount = this.homeViewModel.getUpToDateSourceCount();
        upToDateSourceCount.observe(this, count ->
                upToDateSourcesTextView.setText(resources.getQuantityString(R.plurals.up_to_date_source_label, count, count))
        );

        TextView outdatedSourcesTextView = this.binding.content.outdatedSourcesTextView;
        LiveData<Integer> outdatedSourceCount = this.homeViewModel.getOutdatedSourceCount();
        outdatedSourceCount.observe(this, count ->
                outdatedSourcesTextView.setText(resources.getQuantityString(R.plurals.outdated_source_label, count, count))
        );
    }

    private void bindPending() {
        this.homeViewModel.getPending().observe(this, pending -> {
            if (pending) {
                showView(this.binding.content.sourcesProgressBar);
                showView(this.binding.content.stateTextView);
            } else {
                removeView(this.binding.content.sourcesProgressBar);
            }
        });
    }

    private void bindState() {
        this.homeViewModel.getState().observe(this, text -> {
            this.binding.content.stateTextView.setText(text);
            if (text.isEmpty()) {
                removeView(this.binding.content.stateTextView);
            } else {
                showView(this.binding.content.stateTextView);
            }
        });
    }

    private void bindClickListeners() {
        this.binding.content.blockedHostCardView.setOnClickListener(v -> startHostListActivity(BLOCKED_HOSTS_TAB));
        this.binding.content.allowedHostCardView.setOnClickListener(v -> startHostListActivity(ALLOWED_HOSTS_TAB));
        this.binding.content.redirectHostCardView.setOnClickListener(v -> startHostListActivity(REDIRECTED_HOSTS_TAB));
        this.binding.content.sourcesCardView.setOnClickListener(this::startHostsSourcesActivity);
        this.binding.content.checkForUpdateImageView.setOnClickListener(v -> this.homeViewModel.update());
        this.binding.content.updateImageView.setOnClickListener(v -> this.homeViewModel.sync());
        // DNS Log, Help, and Donate cards moved to Settings
    }

    private void setUpBottomDrawer() {
        this.drawerBehavior = BottomSheetBehavior.from(this.binding.bottomDrawer);
        this.drawerBehavior.setState(STATE_HIDDEN);

        this.onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                // Hide drawer if expanded
                HomeActivity.this.drawerBehavior.setState(STATE_HIDDEN);
                HomeActivity.this.onBackPressedCallback.setEnabled(false);
            }
        };
        getOnBackPressedDispatcher().addCallback(this.onBackPressedCallback);

        this.binding.bar.setNavigationOnClickListener(v -> {
            this.drawerBehavior.setState(STATE_HALF_EXPANDED);
            this.onBackPressedCallback.setEnabled(true);
        });
//        this.binding.bar.setNavigationIcon(R.drawable.ic_menu_24dp);
//        this.binding.bar.replaceMenu(R.menu.next_actions);
    }

    private void bindFab() {
        this.binding.fab.setOnClickListener(v -> this.homeViewModel.toggleAdBlocking());
    }

    private boolean showFragment(@IdRes int actionId) {
        if (actionId == R.id.drawer_preferences) {
            startPrefsActivity();
            this.drawerBehavior.setState(STATE_HIDDEN);
            return true;
        } else if (actionId == R.id.drawer_github_project) {
            showProjectPage();
            this.drawerBehavior.setState(STATE_HIDDEN);
            return true;
        }
        return false;
    }

    /**
     * Start hosts lists activity.
     *
     * @param tab The tab to show.
     */
    private void startHostListActivity(int tab) {
        Intent intent = new Intent(this, ListsActivity.class);
        intent.putExtra(TAB, tab);
        startActivity(intent);
    }

    /**
     * Start hosts source activity.
     *
     * @param view The event source view.
     */
    private void startHostsSourcesActivity(View view) {
        startActivity(new Intent(this, HostsSourcesActivity.class));
    }

    /**
     * Start help activity.
     *
     * @param view The source event view.
     */
    private void startHelpActivity(View view) {
        startActivity(new Intent(this, HelpActivity.class));
    }

    /**
     * Show development project page.
     */
    private void showProjectPage() {
        // Show development page
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_LINK));
        startActivity(browserIntent);
    }

    /**
     * Show support activity.
     *
     * @param view The source event view.
     */
    private void showSupportActivity(View view) {
        startActivity(new Intent(this, SupportActivity.class));
    }

    /**
     * Start preferences activity.
     */
    private void startPrefsActivity() {
        startActivity(new Intent(this, PrefsActivity.class));
    }

    /**
     * Start DNS log activity.
     *
     * @param view The source event view.
     */
    private void startDnsLogActivity(View view) {
        startActivity(new Intent(this, LogActivity.class));
    }

    private void notifyAdBlocked(boolean adBlocked) {
        int color = adBlocked ? getResources().getColor(R.color.primary, null) : Color.GRAY;
        this.binding.content.headerFrameLayout.setBackgroundColor(color);
        this.binding.fab.setImageResource(adBlocked ? R.drawable.ic_pause_24dp : R.drawable.logo);
    }

    private void notifyError(HostError error) {
        removeView(this.binding.content.stateTextView);
        if (error == null) {
            return;
        }

        String message = getString(error.getDetailsKey()) + "\n\n" + getString(R.string.error_dialog_help);
        new MaterialAlertDialogBuilder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(error.getMessageKey())
                .setMessage(message)
                .setPositiveButton(R.string.button_close, (dialog, id) -> dialog.dismiss())
                .setNegativeButton(R.string.button_help, (dialog, id) -> {
                    dialog.dismiss();
                    startActivity(new Intent(this, HelpActivity.class));
                })
                .create()
                .show();
    }

    private void showUpdate(View view) {
        Intent intent = new Intent(this, UpdateActivity.class);
        startActivity(intent);
    }
}
