package org.adaway.ui.update;

import android.content.Context;

import org.adaway.R;
import org.adaway.model.update.UpdateModel.InstallState;

/** The authorization/installer phase, independent of DownloadManager's byte count. */
final class InstallDownloadStatus implements DownloadStatus {
    private final InstallState state;

    InstallDownloadStatus(InstallState state) {
        this.state = state;
    }

    boolean canRetry() {
        return this.state != InstallState.DOWNLOADING && this.state != InstallState.VERIFYING
                && this.state != InstallState.CHECKING && this.state != InstallState.IDLE;
    }

    int actionLabel() {
        return this.state == InstallState.METADATA_EXPIRED || this.state == InstallState.CHECK_FAILED
                ? R.string.update_check_again_button : R.string.update_retry_button;
    }

    @Override
    public int getProgress() {
        return 0;
    }

    @Override
    public String format(Context context) {
        int label;
        switch (this.state) {
            case DOWNLOADING: label = R.string.update_downloading_label; break;
            case VERIFYING: label = R.string.update_verifying_label; break;
            case PERMISSION_REQUIRED: label = R.string.update_permission_required_label; break;
            case INSTALLER_OPENED: label = R.string.update_installer_opened_label; break;
            case DOWNLOAD_FAILED: label = R.string.update_download_failed_label; break;
            case VERIFICATION_FAILED: label = R.string.update_verification_failed_label; break;
            case INSTALL_FAILED: label = R.string.update_install_failed_label; break;
            case METADATA_EXPIRED: label = R.string.update_metadata_expired_label; break;
            case CHECKING: label = R.string.update_checking_label; break;
            default: label = R.string.update_unavailable_header; break;
        }
        return context.getString(label);
    }
}
