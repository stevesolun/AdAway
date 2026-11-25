package org.adaway.ui.domainchecker;

import android.app.Application;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/** Owns checks, rule changes and undelivered completion feedback across view recreation. */
public class DomainCheckerViewModel extends AndroidViewModel {
    private static final int USER_SOURCE_ID = HostsSource.USER_SOURCE_ID;

    enum Action { CHECK, ALLOW, BLOCK, DELETE, REMOVE_ALLOW }

    static final class OperationResult {
        final Action action;
        final String domain;
        @StringRes final int error;

        OperationResult(Action action, String domain, @StringRes int error) {
            this.action = action;
            this.domain = domain;
            this.error = error;
        }
    }

    public final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    public final MutableLiveData<DomainCheckResult> checkResult = new MutableLiveData<>();
    private final MutableLiveData<OperationResult> mOperationResult = new MutableLiveData<>();
    private final AppDatabase mDatabase;
    private final HostListItemDao mHostListItemDao;
    private final HostEntryDao mHostEntryDao;
    private final HostsSourceDao mHostsSourceDao;

    // Main-thread owned. Pending reads may supersede each other, but cannot admit mutations.
    private int mPendingOperations;
    private long mCheckGeneration;
    private String mLatestDomain;

    public DomainCheckerViewModel(@NonNull Application application) {
        super(application);
        mDatabase = AppDatabase.getInstance(application);
        mHostListItemDao = mDatabase.hostsListItemDao();
        mHostEntryDao = mDatabase.hostEntryDao();
        mHostsSourceDao = mDatabase.hostsSourceDao();
    }

    LiveData<OperationResult> getOperationResult() {
        return mOperationResult;
    }

    void consumeOperationResult(OperationResult result) {
        if (mOperationResult.getValue() == result) mOperationResult.setValue(null);
    }

    public void checkDomain(String input) {
        onMain(() -> {
            String domain = DomainNormalizer.normalize(input);
            long generation = ++mCheckGeneration;
            mLatestDomain = domain;
            if (domain == null) {
                checkResult.setValue(null);
                mOperationResult.setValue(new OperationResult(Action.CHECK, null,
                        R.string.domain_checker_invalid_input));
                return;
            }
            beginOperation();
            execute(Action.CHECK, domain, generation, domain, null);
        });
    }

    public void unblockDomain(String domain) {
        mutate(Action.ALLOW, domain, 0);
    }

    public void blockDomain(String domain) {
        mutate(Action.BLOCK, domain, 0);
    }

    public void deleteRule(int itemId, String domain) {
        mutate(Action.DELETE, domain, itemId);
    }

    public void removeUserAllowRule(String domain) {
        mutate(Action.REMOVE_ALLOW, domain, 0);
    }

    private void mutate(Action action, String input, int itemId) {
        onMain(() -> {
            if (mPendingOperations != 0) return;
            String domain = DomainNormalizer.normalize(input);
            if (domain == null) {
                mOperationResult.setValue(new OperationResult(action, null,
                        R.string.domain_checker_invalid_input));
                return;
            }
            String refreshDomain = mLatestDomain == null ? domain : mLatestDomain;
            beginOperation();
            execute(action, domain, mCheckGeneration, refreshDomain,
                    () -> writeRule(action, domain, itemId));
        });
    }

    private void beginOperation() {
        mPendingOperations++;
        mOperationResult.setValue(null);
        loading.setValue(true);
    }

    private void execute(Action action, String domain, long generation, String refreshDomain,
                         Runnable mutation) {
        try {
            AppExecutors.getInstance().diskIO().execute(() -> {
                DomainCheckResult result = null;
                int error = 0;
                try {
                    if (mutation == null) {
                        result = readDomain(refreshDomain);
                    } else {
                        AdAwayApplication application = getApplication();
                        try (SourceOperationCoordinator.Lease ignored =
                                     application.getSourceOperations().acquire()) {
                            result = application.getSourceModel().runSourceMutation(() -> {
                                mDatabase.runInTransaction(mutation);
                                application.getSourceModel().syncHostEntries();
                                return readDomain(refreshDomain);
                            });
                        }
                    }
                } catch (HostErrorException exception) {
                    error = R.string.error_update_in_progress_message;
                } catch (RuntimeException exception) {
                    Timber.w(exception, "Domain Checker operation failed.");
                    error = action == Action.CHECK ? R.string.domain_checker_check_failed
                            : R.string.domain_checker_change_failed;
                }
                DomainCheckResult completedResult = result;
                int completedError = error;
                onMain(() -> complete(action, domain, generation, completedResult, completedError));
            });
        } catch (RuntimeException exception) {
            Timber.w(exception, "Could not schedule Domain Checker operation.");
            complete(action, domain, generation, null, action == Action.CHECK
                    ? R.string.domain_checker_check_failed : R.string.domain_checker_change_failed);
        }
    }

    private void complete(Action action, String domain, long generation,
                          DomainCheckResult result, @StringRes int error) {
        boolean currentCheck = generation == mCheckGeneration;
        // A failed refresh must not leave stale protection status and mutation buttons visible.
        if (currentCheck) checkResult.setValue(result);
        mPendingOperations--;
        loading.setValue(mPendingOperations != 0);
        if (action != Action.CHECK || (currentCheck && error != 0)) {
            mOperationResult.setValue(new OperationResult(action, domain, error));
        }
    }

    private void writeRule(Action action, String domain, int itemId) {
        if (action == Action.ALLOW || action == Action.BLOCK) {
            if (action == Action.BLOCK) {
                // Replacement and insert roll back together if storage fails.
                mHostListItemDao.deleteUserFromHost(domain);
            } else {
                // An allow exception preserves the underlying block. Retry reuses the exception.
                for (HostListItem existing : mHostListItemDao.getExactEntriesForHost(domain)) {
                    if (existing.getSourceId() == USER_SOURCE_ID
                            && existing.getType() == ListType.ALLOWED) {
                        existing.setEnabled(true);
                        mHostListItemDao.update(existing);
                        return;
                    }
                }
            }
            HostListItem item = new HostListItem();
            item.setHost(domain);
            item.setType(action == Action.ALLOW ? ListType.ALLOWED : ListType.BLOCKED);
            item.setEnabled(true);
            item.setSourceId(USER_SOURCE_ID);
            mHostListItemDao.insert(item);
        } else if (action == Action.REMOVE_ALLOW) {
            mHostListItemDao.removeExactUserAllow(domain);
        } else {
            for (HostListItem item : mHostListItemDao.getExactEntriesForHost(domain)) {
                if (item.getSourceId() != USER_SOURCE_ID) continue;
                if (item.getId() == itemId && item.getType() != ListType.ALLOWED) {
                    mHostListItemDao.deleteById(item.getId());
                    return;
                }
            }
            throw new IllegalStateException("No matching exact user block or redirect to delete");
        }
    }

    private DomainCheckResult readDomain(String domain) {
        boolean rootMode = PreferenceHelper.getAdBlockMethod(getApplication()) == AdBlockMethod.ROOT;
        return mDatabase.runInTransaction(() -> readDomainSnapshot(domain, rootMode));
    }

    private DomainCheckResult readDomainSnapshot(String domain, boolean rootMode) {
        List<HostListItem> entries = rootMode
                ? mHostListItemDao.getRootEntriesForHost(domain)
                : mHostListItemDao.getEntriesForHost(domain);
        List<HostsSource> allSources = mHostsSourceDao.getAll();
        List<DomainCheckResult.BlockingSource> blockingSources = new ArrayList<>();
        ListType runtimeType = rootMode
                ? mHostEntryDao.getRootTypeForHost(domain) : mHostEntryDao.getTypeForHost(domain);
        boolean blocked = runtimeType == ListType.BLOCKED || runtimeType == ListType.REDIRECTED;
        for (HostListItem item : entries) {
            if (!item.isEnabled()) continue;
            if (blocked && (item.getType() == ListType.BLOCKED
                    || item.getType() == ListType.REDIRECTED)) {
                blockingSources.add(new DomainCheckResult.BlockingSource(item.getId(),
                        resolveSourceName(allSources, item.getSourceId()),
                        item.getSourceId() == USER_SOURCE_ID, item.getHost(), item.getKind()));
            }
        }
        List<DomainCheckResult.AllowSource> allows = new ArrayList<>();
        boolean userAllowed = false;
        for (HostListItemDao.AllowMatch match : mHostListItemDao.getMatchingAllowRules(domain)) {
            boolean user = match.sourceId == USER_SOURCE_ID;
            userAllowed |= user;
            String name = resolveSourceName(allSources, match.sourceId);
            allows.add(new DomainCheckResult.AllowSource(match.sourceId, name, match.host, match.kind));
        }
        return new DomainCheckResult(domain, resolveStatus(runtimeType, !allows.isEmpty()),
                userAllowed, blockingSources, allows,
                buildAdvice(blocked, userAllowed));
    }

    static String buildAdvice(boolean blocked, boolean userAllowed) {
        if (!blocked) return "This domain is not blocked by any active filter list.";
        if (userAllowed) return "You have already added an allow-list exception for this domain.";
        return "Tap \"Add to Allow List\" to create a user exception for this domain.";
    }

    private String resolveSourceName(List<HostsSource> sources, int sourceId) {
        if (sourceId == USER_SOURCE_ID) return getApplication().getString(R.string.hosts_user_source);
        for (HostsSource source : sources) {
            if (source.getId() == sourceId) {
                String label = source.getLabel();
                if (label != null && !label.isEmpty()) return label;
                String url = source.getUrl();
                return url != null ? url : String.valueOf(sourceId);
            }
        }
        return "Source " + sourceId;
    }

    private static DomainCheckResult.Status resolveStatus(ListType type, boolean explicitlyAllowed) {
        if (type == ListType.BLOCKED) return DomainCheckResult.Status.BLOCKED;
        if (type == ListType.REDIRECTED) return DomainCheckResult.Status.REDIRECTED;
        return explicitlyAllowed ? DomainCheckResult.Status.ALLOWED : DomainCheckResult.Status.UNKNOWN;
    }

    private static void onMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else AppExecutors.getInstance().mainThread().execute(action);
    }
}
