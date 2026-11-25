package org.adaway.ui.update;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import org.adaway.R;
import org.adaway.databinding.UpdateActityBinding;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.update.Manifest;

/**
 * This class is the application main activity.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateActivity extends AppCompatActivity {
    /** PayPal support link (inlined from deleted SupportActivity). */
    private static final Uri SUPPORT_LINK = Uri.parse("https://paypal.me/BruceBUJON");
    /** GitHub sponsors link (inlined from deleted SupportActivity). */
    private static final Uri SPONSORSHIP_LINK = Uri.parse("https://github.com/sponsors/PerfectSlayer");

    private UpdateActityBinding binding;
    private UpdateViewModel updateViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        this.binding = UpdateActityBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());

        this.updateViewModel = new ViewModelProvider(this).get(UpdateViewModel.class);
        bindListeners();
        bindManifest();
        bindProgress();
    }

    private void bindListeners() {
        this.binding.updateButton.setOnClickListener(v -> this.updateViewModel.update());
        this.binding.updateDonateButton.setOnClickListener(v -> openSupportLink(SUPPORT_LINK));
        this.binding.updateSponsorButton.setOnClickListener(v -> openSupportLink(SPONSORSHIP_LINK));
    }

    private void openSupportLink(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException | SecurityException ignored) {
            Snackbar.make(this.binding.getRoot(), R.string.update_support_link_unavailable,
                    Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.updateViewModel.resumePendingInstall();
    }

    private void bindManifest() {
        this.updateViewModel.getAppManifest().observe(this, manifest -> {
            this.binding.changelogTextView.setText(manifest == null ? null : manifest.changelog);
            renderProgress(this.updateViewModel.getDownloadProgress().getValue());
        });
    }

    private void bindProgress() {
        this.updateViewModel.getDownloadProgress().observe(this, this::renderProgress);
    }

    private void renderProgress(@Nullable DownloadStatus progress) {
        LiveData<Manifest> result = this.updateViewModel.getAppManifest();
        Manifest manifest = result.getValue();
        boolean fresh = manifest != null && !manifest.isExpired();
        boolean canCheck = this.updateViewModel.canCheckForUpdates();
        int header = R.string.update_title;
        if (fresh) {
            header = manifest.updateAvailable ? R.string.update_update_available_header
                    : R.string.update_up_to_date_header;
        } else if (progress == null && (result.isInitialized() || !canCheck)) {
            header = R.string.update_unavailable_header;
        }
        this.binding.headerTextView.setText(header);
        this.binding.changelogTitleTextView.setVisibility(fresh ? VISIBLE : GONE);
        this.binding.changelogTextView.setVisibility(fresh ? VISIBLE : GONE);
        if (progress == null) {
            boolean offerCheck = !fresh && canCheck;
            int action = R.string.update_update_button;
            if (offerCheck) {
                action = result.isInitialized() ? R.string.update_check_again_button
                        : R.string.menu_check_updates;
            }
            this.binding.updateButton.setText(action);
            this.binding.updateButton.setVisibility(offerCheck || (fresh && manifest.updateAvailable)
                    ? VISIBLE : GONE);
            this.binding.downloadProgressBar.setVisibility(GONE);
            this.binding.downloadProgressBar.setIndeterminate(false);
            this.binding.downloadProgressBar.setProgress(0);
            this.binding.progressTextView.setText(null);
            return;
        }
        InstallDownloadStatus install = progress instanceof InstallDownloadStatus
                ? (InstallDownloadStatus) progress : null;
        boolean retry = install != null && install.canRetry();
        this.binding.updateButton.setText(retry
                ? install.actionLabel() : R.string.update_update_button);
        this.binding.updateButton.setVisibility(retry ? VISIBLE : INVISIBLE);
        this.binding.downloadProgressBar.setVisibility(retry ? GONE : VISIBLE);
        this.binding.downloadProgressBar.setIndeterminate(install != null && !retry);
        this.binding.downloadProgressBar.setProgress(progress.getProgress(), true);
        this.binding.progressTextView.setText(progress.format(this));
    }

}
