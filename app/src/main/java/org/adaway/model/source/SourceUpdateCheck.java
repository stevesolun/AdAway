package org.adaway.model.source;

import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.R;
import org.adaway.model.error.HostErrorException;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import timber.log.Timber;

final class SourceUpdateCheck {
    @FunctionalInterface
    interface Check {
        boolean run() throws HostErrorException;
    }

    private final MutableLiveData<Integer> message = new MutableLiveData<>();
    private final Check check;
    private final Executor worker;
    private final Executor mainThread;

    SourceUpdateCheck(Check check, Executor worker, Executor mainThread) {
        this.check = check;
        this.worker = worker;
        this.mainThread = mainThread;
    }

    LiveData<Integer> getMessage() {
        return message;
    }

    @UiThread
    void dismiss() {
        if (!Objects.equals(message.getValue(), R.string.status_check)) {
            message.setValue(null);
        }
    }

    @UiThread
    void start() {
        if (Objects.equals(message.getValue(), R.string.status_check)) return;
        message.setValue(R.string.status_check);
        try {
            worker.execute(() -> {
                int result;
                try {
                    result = check.run()
                            ? R.string.status_update_available : R.string.status_no_update_found;
                } catch (HostErrorException error) {
                    result = error.getError().getMessageKey();
                } catch (RuntimeException error) {
                    Timber.w(error, "Could not complete the manual source update check.");
                    result = R.string.status_source_check_failed;
                }
                int terminalMessage = result;
                mainThread.execute(() -> message.setValue(terminalMessage));
            });
        } catch (RejectedExecutionException error) {
            Timber.w(error, "Could not schedule the manual source update check.");
            message.setValue(R.string.status_source_check_failed);
        }
    }
}
