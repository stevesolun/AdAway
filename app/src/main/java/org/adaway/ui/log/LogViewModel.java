package org.adaway.ui.log;

import android.app.Application;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.util.AppExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;

/**
 * This class is an {@link AndroidViewModel} for the {@link LogActivity}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class LogViewModel extends AndroidViewModel {
    private final AdBlockModel adBlockModel;
    private final AppDatabase database;
    private final HostListItemDao hostListItemDao;
    private final HostEntryDao hostEntryDao;
    private final MutableLiveData<List<LogEntry>> logEntries;
    private final MutableLiveData<Boolean> recording;
    private LogEntrySort sort;
    private long refreshRevision;

    public LogViewModel(@NonNull Application application) {
        super(application);
        this.adBlockModel = ((AdAwayApplication) application).getAdBlockModel();
        this.database = AppDatabase.getInstance(application);
        this.hostListItemDao = this.database.hostsListItemDao();
        this.hostEntryDao = this.database.hostEntryDao();
        this.logEntries = new MutableLiveData<>();
        this.recording = new MutableLiveData<>(
                !isDnsRequestLoggingUnavailable() && this.adBlockModel.isRecordingLogs());
        this.sort = LogEntrySort.TOP_LEVEL_DOMAIN;
    }

    public boolean isDnsRequestLoggingUnavailable() {
        return this.adBlockModel.getMethod() == AdBlockMethod.ROOT;
    }

    public LiveData<List<LogEntry>> getLogs() {
        return this.logEntries;
    }

    public void clearLogs() {
        ++this.refreshRevision;
        this.adBlockModel.clearLogs();
        this.logEntries.setValue(Collections.emptyList());
    }

    public void updateLogs() {
        long revision = ++this.refreshRevision;
        LogEntrySort requestedSort = this.sort;
        AppExecutors executors = AppExecutors.getInstance();
        executors.diskIO().execute(() -> {
            List<LogEntry> entries = readLogEntries(requestedSort);
            executors.mainThread().execute(() -> {
                if (revision != this.refreshRevision) return;
                if (requestedSort != this.sort) entries.sort(this.sort.comparator());
                this.logEntries.setValue(entries);
            });
        });
    }

    List<LogEntry> readLogEntries(LogEntrySort order) {
        return this.adBlockModel.getLogs().parallelStream()
                .map(this::readEntry)
                .sorted(order.comparator())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private LogEntry readEntry(String host) {
        ListType type = this.hostEntryDao.getTypeForHost(host);
        if (type == ListType.ALLOWED && !this.hostEntryDao.isAllowedByActiveRule(host)) {
            return new LogEntry(host, null, false);
        }
        boolean removable = this.hostListItemDao.getExactEntriesForHost(host).stream()
                .anyMatch(rule -> rule.getSourceId() == USER_SOURCE_ID && rule.isEnabled()
                        && rule.getType() == type);
        return new LogEntry(host, type, removable);
    }

    public void toggleSort() {
        this.sortDnsRequests(this.sort == LogEntrySort.ALPHABETICAL ?
                LogEntrySort.TOP_LEVEL_DOMAIN :
                LogEntrySort.ALPHABETICAL
        );
    }

    public LiveData<Boolean> isRecording() {
        return this.recording;
    }

    public void toggleRecording() {
        if (isDnsRequestLoggingUnavailable()) {
            this.recording.postValue(false);
            return;
        }
        boolean recording = !this.adBlockModel.isRecordingLogs();
        this.adBlockModel.setRecordingLogs(recording);
        this.recording.postValue(this.adBlockModel.isRecordingLogs());
    }

    public void addListItem(@NonNull String host, @NonNull ListType type, String redirection) {
        // Create new host list item
        HostListItem item = new HostListItem();
        item.setType(type);
        item.setHost(host);
        item.setRedirection(redirection);
        item.setEnabled(true);
        item.setSourceId(USER_SOURCE_ID);
        // Insert host list item
        AppExecutors.getInstance().diskIO().execute(() -> this.database.runInTransaction(() -> {
            // There is no unique host constraint; inserting alone leaves older allow rules active.
            for (HostListItem existing : this.hostListItemDao.getExactEntriesForHost(item.getHost())) {
                if (existing.getSourceId() == USER_SOURCE_ID) {
                    this.hostListItemDao.delete(existing);
                }
            }
            this.hostListItemDao.insert(item);
            this.database.hostsSourceDao().updateActiveRuleStats(USER_SOURCE_ID);
        }));
        // Update log entries
        updateLogs();
    }

    public void removeListItem(@NonNull String host, @NonNull ListType type) {
        AppExecutors.getInstance().diskIO().execute(() -> this.database.runInTransaction(() -> {
            for (HostListItem rule : this.hostListItemDao.getExactEntriesForHost(host)) {
                if (rule.getSourceId() == USER_SOURCE_ID && rule.isEnabled() && rule.getType() == type) {
                    this.hostListItemDao.delete(rule);
                }
            }
            this.database.hostsSourceDao().updateActiveRuleStats(USER_SOURCE_ID);
        }));
        updateLogs();
    }

    private void sortDnsRequests(LogEntrySort sort) {
        // Save current sort
        this.sort = sort;
        // Apply sort to values
        List<LogEntry> entries = this.logEntries.getValue();
        if (entries != null) {
            List<LogEntry> sortedEntries = new ArrayList<>(entries);
            sortedEntries.sort(this.sort.comparator());
            this.logEntries.setValue(sortedEntries);
        }
        // Notify user
        Toast.makeText(
                getApplication(),
                this.sort.getName(),
                Toast.LENGTH_SHORT
        ).show();
    }
}
