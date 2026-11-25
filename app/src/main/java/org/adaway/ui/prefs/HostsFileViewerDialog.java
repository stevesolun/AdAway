package org.adaway.ui.prefs;

import static org.adaway.util.Constants.ANDROID_SYSTEM_ETC_HOSTS;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.util.AppExecutors;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

import timber.log.Timber;

/** Internal viewer: no external intent, URI grant, root command, or write-back. */
public class HostsFileViewerDialog extends DialogFragment {
    static final String TAG = "hosts-file-viewer";
    private TextView mStatus;
    private TextView mContent;
    private FutureTask<Void> mLoadTask;
    private int mGeneration;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.hosts_file_viewer, null);
        mStatus = view.findViewById(R.id.hosts_viewer_status);
        mContent = view.findViewById(R.id.hosts_viewer_content);
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.hosts_viewer_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        int generation = ++mGeneration;
        mStatus.setText(R.string.hosts_viewer_loading);
        mContent.setText("");
        AppExecutors executors = AppExecutors.getInstance();
        mLoadTask = new FutureTask<>(() -> {
            HostsFileSnapshot snapshot;
            try {
                snapshot = readSnapshot();
            } catch (IOException | SecurityException exception) {
                Timber.w(exception, "Unable to read hosts preview.");
                snapshot = null;
            }
            HostsFileSnapshot result = snapshot;
            executors.mainThread().execute(() -> showSnapshot(generation, result));
            return null;
        });
        try {
            executors.diskIO().execute(mLoadTask);
        } catch (RejectedExecutionException exception) {
            Timber.w(exception, "Unable to schedule hosts preview.");
            showSnapshot(generation, null);
        }
    }

    HostsFileSnapshot readSnapshot() throws IOException {
        File hosts = new File(ANDROID_SYSTEM_ETC_HOSTS);
        if (!hosts.isFile()) {
            throw new IOException("Hosts path is not a readable regular file.");
        }
        return HostsFileSnapshot.read(new FileInputStream(hosts));
    }

    private void showSnapshot(int generation, HostsFileSnapshot snapshot) {
        // A stopped/dismissed/recreated dialog must never receive an earlier read result.
        if (generation != mGeneration || mContent == null || getDialog() == null) {
            return;
        }
        if (snapshot == null) {
            mStatus.setText(R.string.hosts_viewer_error);
            mContent.setText("");
            return;
        }
        mStatus.setText(snapshot.mTruncated ? R.string.hosts_viewer_truncated
                : R.string.hosts_viewer_complete);
        mContent.setText(snapshot.mText);
    }

    @Override
    public void onStop() {
        ++mGeneration;
        if (mLoadTask != null) {
            mLoadTask.cancel(true);
            mLoadTask = null;
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        mStatus = null;
        mContent = null;
        super.onDestroyView();
    }
}
