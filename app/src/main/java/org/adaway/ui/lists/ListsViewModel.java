package org.adaway.ui.lists;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.ui.lists.type.AbstractListFragment;
import org.adaway.util.AppExecutors;
import org.adaway.util.RegexUtils;

import java.util.Optional;

import static androidx.lifecycle.Transformations.switchMap;
import static androidx.lifecycle.Transformations.distinctUntilChanged;
import static androidx.lifecycle.Transformations.map;
import static androidx.paging.PagingLiveData.getLiveData;
import static androidx.paging.PagingLiveData.cachedIn;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.adaway.ui.lists.ListsFilter.ALL;

/**
 * This class is an {@link AndroidViewModel} for the
 * {@link AbstractListFragment} implementations.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class ListsViewModel extends AndroidViewModel {
    private final AppDatabase database;
    private final HostListItemDao hostListItemDao;
    private final HostsSourceDao hostsSourceDao;
    private final MutableLiveData<ListsFilter> filter;
    private final LiveData<String> searchQuery;
    private final LiveData<PagingData<HostListItem>> blockedListItems;
    private final LiveData<PagingData<HostListItem>> allowedListItems;
    private final LiveData<PagingData<HostListItem>> redirectedListItems;
    private final MutableLiveData<Boolean> modelChanged;

    public ListsViewModel(@NonNull Application application) {
        super(application);
        this.database = AppDatabase.getInstance(application);
        this.hostListItemDao = database.hostsListItemDao();
        this.hostsSourceDao = database.hostsSourceDao();
        this.filter = new MutableLiveData<>(ALL);
        this.searchQuery = distinctUntilChanged(map(this.filter, value -> value.query));
        PagingConfig pagingConfig = new PagingConfig(50, 150, true);
        this.blockedListItems = cachedIn(switchMap(
                this.filter,
                filter -> getLiveData(new Pager<>(pagingConfig, () -> this.hostListItemDao.loadList(BLOCKED.getValue(),
                        filter.sourcesIncluded, filter.sqlQuery)))), this);
        this.allowedListItems = cachedIn(switchMap(
                this.filter,
                filter -> getLiveData(new Pager<>(pagingConfig, () -> this.hostListItemDao.loadList(ALLOWED.getValue(),
                        filter.sourcesIncluded, filter.sqlQuery)))), this);
        this.redirectedListItems = cachedIn(switchMap(
                this.filter,
                filter -> getLiveData(new Pager<>(pagingConfig, () -> this.hostListItemDao
                        .loadList(REDIRECTED.getValue(), filter.sourcesIncluded, filter.sqlQuery)))), this);
        this.modelChanged = new MutableLiveData<>(false);
    }

    public LiveData<PagingData<HostListItem>> getBlockedListItems() {
        return this.blockedListItems;
    }

    public LiveData<PagingData<HostListItem>> getAllowedListItems() {
        return this.allowedListItems;
    }

    public LiveData<PagingData<HostListItem>> getRedirectedListItems() {
        return this.redirectedListItems;
    }

    public LiveData<Boolean> getModelChanged() {
        return this.modelChanged;
    }

    public void setItemEnabled(HostListItem item, boolean enabled) {
        HostListItem updated = copyItem(item);
        updated.setEnabled(enabled);
        AppExecutors.getInstance().diskIO().execute(() -> {
            this.database.runInTransaction(() -> {
                HostListItem current = this.hostListItemDao.getById(updated.getId());
                if (current == null) {
                    return;
                }
                current.setEnabled(updated.isEnabled());
                this.hostListItemDao.update(current);
                this.hostsSourceDao.updateActiveRuleStats(current.getSourceId());
            });
            this.modelChanged.postValue(true);
        });
    }

    public boolean addListItem(@NonNull ListType type, @NonNull String host, String redirection) {
        if (!isValidListItem(type, host, redirection)) {
            return false;
        }
        HostListItem item = new HostListItem();
        item.setType(type);
        item.setHost(host);
        item.setRedirection(redirection);
        item.setEnabled(true);
        item.setSourceId(USER_SOURCE_ID);
        AppExecutors.getInstance().diskIO().execute(() -> {
            this.database.runInTransaction(() -> {
                addOrUpdateItemInternal(item);
                this.hostsSourceDao.updateActiveRuleStats(USER_SOURCE_ID);
            });
            this.modelChanged.postValue(true);
        });
        return true;
    }

    private void addOrUpdateItemInternal(HostListItem item) {
        Optional<Integer> id = this.hostListItemDao.getHostId(item.getHost());
        if (id.isPresent()) {
            item.setId(id.get());
            this.hostListItemDao.update(item);
        } else {
            this.hostListItemDao.insert(item);
        }
    }

    public boolean updateListItem(@NonNull HostListItem item, @NonNull String host,
            String redirection) {
        if (!isValidListItem(item.getType(), host, redirection)) {
            return false;
        }
        HostListItem updated = copyItem(item);
        updated.setHost(host);
        updated.setRedirection(redirection);
        AppExecutors.getInstance().diskIO().execute(() -> {
            this.database.runInTransaction(() -> {
                HostListItem current = this.hostListItemDao.getById(updated.getId());
                if (current == null || !isValidListItem(current.getType(),
                        updated.getHost(), updated.getRedirection())) {
                    return;
                }
                current.setHost(updated.getHost());
                current.setRedirection(updated.getRedirection());
                this.hostListItemDao.update(current);
                this.hostsSourceDao.updateActiveRuleStats(current.getSourceId());
            });
            this.modelChanged.postValue(true);
        });
        return true;
    }

    public void removeListItem(HostListItem list) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            this.hostListItemDao.delete(list);
            this.hostsSourceDao.updateActiveRuleStats(list.getSourceId());
            this.modelChanged.postValue(true);
        });
    }

    public void moveListItem(HostListItem item) {
        if (!canMoveListItem(item)) {
            return;
        }
        ListType newType = item.getType() == ALLOWED ? BLOCKED : ALLOWED;
        HostListItem updated = copyItem(item);

        if (item.getSourceId() == USER_SOURCE_ID) {
            updated.setType(newType);
            updated.setRedirection(null);
            AppExecutors.getInstance().diskIO().execute(() -> {
                this.database.runInTransaction(() -> {
                    HostListItem current = this.hostListItemDao.getById(updated.getId());
                    if (current == null || !canMoveListItem(current)) {
                        return;
                    }
                    current.setType(updated.getType());
                    current.setRedirection(null);
                    this.hostListItemDao.update(current);
                    this.hostsSourceDao.updateActiveRuleStats(USER_SOURCE_ID);
                });
                this.modelChanged.postValue(true);
            });
        } else {
            HostListItem newItem = new HostListItem();
            newItem.setType(newType);
            newItem.setKind(item.getType() == REDIRECTED ? RuleKind.EXACT : item.getKind());
            newItem.setHost(item.getHost());
            newItem.setEnabled(true);
            newItem.setSourceId(USER_SOURCE_ID);
            updated.setEnabled(false);

            AppExecutors.getInstance().diskIO().execute(() -> {
                // Redirects outrank allows: disable this row with the exact exception.
                this.database.runInTransaction(() -> {
                    HostListItem current = this.hostListItemDao.getById(updated.getId());
                    if (current == null) {
                        return;
                    }
                    addOrUpdateItemInternal(newItem);
                    current.setEnabled(updated.isEnabled());
                    this.hostListItemDao.update(current);
                    this.hostsSourceDao.updateActiveRuleStats(USER_SOURCE_ID);
                    this.hostsSourceDao.updateActiveRuleStats(current.getSourceId());
                });
                this.modelChanged.postValue(true);
            });
        }
    }

    public static boolean canMoveListItem(HostListItem item) {
        return item.getType() == BLOCKED || item.getType() == ALLOWED
                || (item.getType() == REDIRECTED && item.getSourceId() != USER_SOURCE_ID);
    }

    private static boolean isValidListItem(ListType type, String host, String redirection) {
        if (type == ALLOWED) {
            return RegexUtils.isValidWildcardHostname(host);
        }
        return RegexUtils.isValidHostname(host) && (type != REDIRECTED
                || (redirection != null && RegexUtils.isValidRedirectIp(redirection)));
    }

    private static HostListItem copyItem(HostListItem item) {
        HostListItem copy = new HostListItem();
        copy.setId(item.getId());
        copy.setHost(item.getHost());
        copy.setType(item.getType());
        copy.setKind(item.getKind());
        copy.setEnabled(item.isEnabled());
        copy.setRedirection(item.getRedirection());
        copy.setSourceId(item.getSourceId());
        copy.setGeneration(item.getGeneration());
        return copy;
    }

    public void search(String query) {
        ListsFilter currentFilter = getFilter();
        if (currentFilter.query.equals(query)) {
            return;
        }
        ListsFilter newFilter = new ListsFilter(currentFilter.sourcesIncluded, query);
        setFilter(newFilter);
    }

    public boolean isSearching() {
        return !getFilter().query.isEmpty();
    }

    public void clearSearch() {
        ListsFilter currentFilter = getFilter();
        ListsFilter newFilter = new ListsFilter(currentFilter.sourcesIncluded, "");
        setFilter(newFilter);
    }

    public void toggleSources() {
        ListsFilter currentFilter = getFilter();
        ListsFilter newFilter = new ListsFilter(!currentFilter.sourcesIncluded, currentFilter.query);
        setFilter(newFilter);
    }

    public String getSearchQuery() {
        return getFilter().query;
    }

    public LiveData<String> getSearchQueryData() {
        return this.searchQuery;
    }

    private ListsFilter getFilter() {
        ListsFilter filter = this.filter.getValue();
        return filter == null ? ALL : filter;
    }

    private void setFilter(ListsFilter filter) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            this.filter.setValue(filter);
        } else {
            this.filter.postValue(filter);
        }
    }
}
