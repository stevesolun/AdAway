package org.adaway.ui.onboarding;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.lifecycle.ViewModel;

import org.adaway.util.AppExecutors;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Owns one onboarding download attempt across Home activity recreation. */
public final class InitialSourcesViewModel extends ViewModel {
    private boolean mStarted;

    @MainThread
    public void initialize(Context context, Runnable update) {
        initialize(context, update, () -> { }, () -> { });
    }

    @MainThread
    public void initialize(Context context, Runnable update, Runnable onStarted, Runnable onFinished) {
        Context applicationContext = context.getApplicationContext();
        AppExecutors executors = AppExecutors.getInstance();
        initialize(executors.diskIO(), executors.mainThread(),
                () -> DefaultListsSubscriber.prepareInitialSources(applicationContext), update,
                onStarted, onFinished);
    }

    @MainThread
    void initialize(Executor diskExecutor, Executor mainExecutor,
            BooleanSupplier prepareSources, Runnable update) {
        initialize(diskExecutor, mainExecutor, prepareSources, update, () -> { }, () -> { });
    }

    @MainThread
    void initialize(Executor diskExecutor, Executor mainExecutor,
            BooleanSupplier prepareSources, Runnable update, Runnable onStarted, Runnable onFinished) {
        if (this.mStarted) {
            return;
        }
        // This guards an attempt, not success. No persisted flag suppresses a later retry.
        this.mStarted = true;
        onStarted.run();
        AtomicBoolean finished = new AtomicBoolean();
        Runnable finishOnce = () -> {
            if (finished.compareAndSet(false, true)) onFinished.run();
        };
        try {
            diskExecutor.execute(() -> {
                final boolean needsUpdate;
                try {
                    needsUpdate = prepareSources.getAsBoolean();
                } catch (RuntimeException exception) {
                    mainExecutor.execute(finishOnce);
                    throw exception;
                }
                mainExecutor.execute(() -> {
                    try {
                        if (needsUpdate) update.run();
                    } finally {
                        finishOnce.run();
                    }
                });
            });
        } catch (RuntimeException exception) {
            finishOnce.run();
            throw exception;
        }
    }
}
