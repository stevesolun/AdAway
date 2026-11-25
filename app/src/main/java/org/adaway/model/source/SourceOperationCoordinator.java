package org.adaway.model.source;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.model.error.HostError;
import org.adaway.model.error.HostErrorException;

/** Owns the complete update/apply lifetime, independently of parser progress. */
public final class SourceOperationCoordinator {
    private final MutableLiveData<Boolean> mBusy = new MutableLiveData<>(false);
    @Nullable
    private Lease mOwner;

    public LiveData<Boolean> getBusy() {
        return this.mBusy;
    }

    public synchronized boolean isBusy() {
        return this.mOwner != null;
    }

    /** Nonblocking and deliberately non-reentrant, including on the owning thread. */
    @Nullable
    public synchronized Lease tryAcquire() {
        if (this.mOwner != null) {
            return null;
        }
        this.mOwner = new Lease();
        this.mBusy.postValue(true);
        return this.mOwner;
    }

    public Lease acquire() throws HostErrorException {
        Lease lease = tryAcquire();
        if (lease == null) {
            throw new HostErrorException(HostError.UPDATE_IN_PROGRESS);
        }
        return lease;
    }

    public final class Lease implements AutoCloseable {
        private Lease() {
        }

        @Override
        public void close() {
            synchronized (SourceOperationCoordinator.this) {
                // A repeated close must not release a subsequent owner's operation.
                if (mOwner == this) {
                    mOwner = null;
                    mBusy.postValue(false);
                }
            }
        }
    }
}
