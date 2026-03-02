package org.adaway.ui.domainchecker;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for the Domain Checker screen.
 *
 * Exposes two LiveData streams:
 *   - {@link #loading} — true while a check or unblock operation is in progress
 *   - {@link #checkResult} — the most recent {@link DomainCheckResult}
 *
 * All database work runs on {@link AppExecutors#diskIO()} so the main thread
 * is never blocked.
 */
public class DomainCheckerViewModel extends AndroidViewModel {

    /** Source_id=1 is the hard-coded user-managed list (never changes). */
    private static final int USER_SOURCE_ID = 1;

    public final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    public final MutableLiveData<DomainCheckResult> checkResult = new MutableLiveData<>();

    private final HostListItemDao mHostListItemDao;
    private final HostsSourceDao mHostsSourceDao;

    public DomainCheckerViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getInstance(application);
        mHostListItemDao = database.hostsListItemDao();
        mHostsSourceDao = database.hostsSourceDao();
    }

    /**
     * Normalise {@code input} and query the database to determine whether the
     * resulting hostname is blocked by any active filter list, and whether the
     * user has already added an allow-list exception for it.
     *
     * Posts a {@link DomainCheckResult} to {@link #checkResult} when done.
     */
    public void checkDomain(String input) {
        String domain = DomainNormalizer.normalize(input);
        if (domain == null || domain.isEmpty()) {
            return;
        }

        loading.setValue(true);
        AppExecutors.getInstance().diskIO().execute(() -> {
            // Fetch only the rows for this specific host — O(hosts) not O(all entries)
            List<HostListItem> entries = mHostListItemDao.getEntriesForHost(domain);
            List<HostsSource> allSources = mHostsSourceDao.getAll();

            List<DomainCheckResult.BlockingSource> blockingSources = new ArrayList<>();
            boolean userAllowed = false;
            boolean blocked = false;

            for (HostListItem item : entries) {
                if (!item.isEnabled()) {
                    continue;
                }
                if (item.getType() == ListType.BLOCKED) {
                    blocked = true;
                    boolean isUserRule = item.getSourceId() == USER_SOURCE_ID;
                    String sourceName = resolveSourceName(allSources, item.getSourceId());
                    blockingSources.add(
                            new DomainCheckResult.BlockingSource(item.getId(), sourceName, isUserRule));
                } else if (item.getType() == ListType.ALLOWED && item.getSourceId() == USER_SOURCE_ID) {
                    userAllowed = true;
                }
            }

            String advice = buildAdvice(blocked, userAllowed);
            DomainCheckResult result = new DomainCheckResult(domain, blocked, userAllowed,
                    blockingSources, advice);

            checkResult.postValue(result);
            loading.postValue(false);
        });
    }

    /**
     * Add {@code domain} to the user allow list (source_id=1, type=ALLOWED).
     * Posts an updated {@link DomainCheckResult} to {@link #checkResult} after
     * the write so the UI refreshes automatically.
     */
    public void unblockDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return;
        }
        loading.setValue(true);
        AppExecutors.getInstance().diskIO().execute(() -> {
            HostListItem item = new HostListItem();
            item.setHost(domain);
            item.setType(ListType.ALLOWED);
            item.setEnabled(true);
            item.setSourceId(USER_SOURCE_ID);
            mHostListItemDao.insert(item);

            // Refresh the check so UI reflects the new allow rule
            loading.postValue(false);
            checkDomain(domain);
        });
    }

    /**
     * Delete a user-defined block rule by its database row ID and refresh the check.
     * Only call this for rules where {@link DomainCheckResult.BlockingSource#isUserRule} is true.
     */
    public void deleteRule(int itemId, String domain) {
        loading.setValue(true);
        AppExecutors.getInstance().diskIO().execute(() -> {
            mHostListItemDao.deleteById(itemId);
            loading.postValue(false);
            checkDomain(domain);
        });
    }

    /**
     * Build a human-readable advice string for the given state.
     *
     * Exposed as package-visible static so unit tests can exercise it without
     * an Android runtime.
     */
    static String buildAdvice(boolean blocked, boolean userAllowed) {
        if (!blocked) {
            return "This domain is not blocked by any active filter list.";
        }
        if (userAllowed) {
            return "You have already added an allow-list exception for this domain.";
        }
        return "Tap \"Add to Allow List\" to create a user exception for this domain.";
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String resolveSourceName(List<HostsSource> sources, int sourceId) {
        for (HostsSource source : sources) {
            if (source.getId() == sourceId) {
                String label = source.getLabel();
                if (label != null && !label.isEmpty()) {
                    return label;
                }
                String url = source.getUrl();
                return url != null ? url : String.valueOf(sourceId);
            }
        }
        return "Source " + sourceId;
    }
}
