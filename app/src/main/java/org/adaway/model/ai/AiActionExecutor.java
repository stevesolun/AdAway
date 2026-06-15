package org.adaway.model.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.FilterListCategory;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.util.RegexUtils;

import java.util.List;
import java.util.Locale;

import timber.log.Timber;

/**
 * Executes {@link AiAgentAction}s against the app's database and services.
 *
 * <p>All public methods must be called on a background thread (diskIO).
 *
 * <p><strong>Security:</strong> All LLM-provided payloads are validated before any DB write:
 * <ul>
 *   <li>Category payloads: {@link FilterListCategory#valueOf} gate (IllegalArgumentException → skip)</li>
 *   <li>Domain payloads: {@link RegexUtils#isValidHostname} after stripping scheme/path/port</li>
 *   <li>USER and CUSTOM categories are rejected as targets for AI category actions</li>
 * </ul>
 */
public final class AiActionExecutor {

    /** source_id = 1 is always the user's personal list. */
    private static final int USER_SOURCE_ID = 1;

    private final Context context;
    private final HostsSourceDao sourceDao;
    private final HostListItemDao listItemDao;
    private final HostEntryDao entryDao;

    public AiActionExecutor(@NonNull Context context) {
        this.context = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(this.context);
        this.sourceDao = db.hostsSourceDao();
        this.listItemDao = db.hostsListItemDao();
        this.entryDao = db.hostEntryDao();
    }

    /**
     * Executes a single action and returns a human-readable result string for display to the user.
     */
    @NonNull
    @WorkerThread
    public String execute(@NonNull AiAgentAction action) {
        switch (action.type) {
            case SUBSCRIBE_CATEGORY: return subscribeCategory(action.payload);
            case ENABLE_CATEGORY:    return setCategoryEnabled(action.payload, true);
            case DISABLE_CATEGORY:   return setCategoryEnabled(action.payload, false);
            case UPDATE_SOURCES:     return triggerUpdate();
            case CHECK_DOMAIN:       return checkDomain(action.payload);
            case ALLOW_DOMAIN:       return applyDomain(action.payload, ListType.ALLOWED);
            case BLOCK_DOMAIN:       return applyDomain(action.payload, ListType.BLOCKED);
            default:                 return "Unknown action";
        }
    }

    // -------------------------------------------------------------------------
    // Action implementations
    // -------------------------------------------------------------------------

    @NonNull
    @WorkerThread
    private String subscribeCategory(@NonNull String payload) {
        FilterListCategory cat = parseCategoryOrNull(payload);
        if (cat == null) return "Unknown category: " + payload;

        List<FilterListCatalog.CatalogEntry> entries = FilterListCatalog.getByCategory(cat);
        int added = 0;
        for (FilterListCatalog.CatalogEntry entry : entries) {
            if (!sourceDao.getByUrl(entry.url).isPresent()) {
                HostsSource source = entry.toHostsSource();
                source.setEnabled(true);
                sourceDao.insert(source);
                added++;
            }
        }
        if (added == 0) {
            // Already fully subscribed — ensure they are all enabled
            return setCategoryEnabled(payload, true);
        }
        return "Subscribed " + added + " new filter list" + (added == 1 ? "" : "s")
                + " in " + cat.name();
    }

    @NonNull
    @WorkerThread
    private String setCategoryEnabled(@NonNull String payload, boolean enable) {
        FilterListCategory cat = parseCategoryOrNull(payload);
        if (cat == null) return "Unknown category: " + payload;

        List<FilterListCatalog.CatalogEntry> entries = FilterListCatalog.getByCategory(cat);
        int count = 0;
        for (FilterListCatalog.CatalogEntry entry : entries) {
            java.util.Optional<HostsSource> opt = sourceDao.getByUrl(entry.url);
            if (opt.isPresent()) {
                int id = opt.get().getId();
                sourceDao.setSourceEnabled(id, enable);
                sourceDao.setSourceItemsEnabled(id, enable);
                count++;
            }
        }
        String verb = enable ? "Enabled" : "Disabled";
        if (count == 0) return cat.name() + " filters are not subscribed yet";
        return verb + " " + count + " filter list" + (count == 1 ? "" : "s")
                + " in " + cat.name();
    }

    @NonNull
    private String triggerUpdate() {
        SourceUpdateService.enqueueUpdateNow(context);
        return "Filter list update queued";
    }

    @NonNull
    @WorkerThread
    private String checkDomain(@NonNull String payload) {
        String host = normalizeDomain(payload);
        if (host == null) return "Invalid domain: " + payload;

        HostEntry entry = PreferenceHelper.getAdBlockMethod(context) == AdBlockMethod.ROOT
                ? entryDao.resolveRootEntry(host)
                : entryDao.resolveEntry(host);
        if (entry.getType() == ListType.ALLOWED) return host + " is not blocked";
        if (entry.getType() == ListType.REDIRECTED) {
            return host + " is redirected";
        }
        return host + " is blocked";
    }

    @NonNull
    @WorkerThread
    private String applyDomain(@NonNull String payload, @NonNull ListType type) {
        String host = normalizeDomain(payload);
        if (host == null) return "Invalid domain: " + payload;

        // Delete any existing user entry for this host to prevent duplicates
        listItemDao.deleteUserFromHost(host);

        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(type);
        item.setEnabled(true);
        item.setSourceId(USER_SOURCE_ID);
        item.setGeneration(0);
        listItemDao.insert(item);
        ((AdAwayApplication) context).getSourceModel().syncHostEntries();

        String verb = type == ListType.ALLOWED ? "Allowed" : "Blocked";
        return verb + ": " + host;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static FilterListCategory parseCategoryOrNull(@NonNull String name) {
        try {
            FilterListCategory cat = FilterListCategory.valueOf(
                    name.toUpperCase(Locale.ROOT).trim());
            // USER and CUSTOM are user-managed, not valid targets for AI category actions
            if (cat == FilterListCategory.USER || cat == FilterListCategory.CUSTOM) return null;
            return cat;
        } catch (IllegalArgumentException e) {
            Timber.d("AI agent provided invalid category: %s", name);
            return null;
        }
    }

    /**
     * Normalizes a domain string from LLM output: strips scheme, path, and port.
     * Returns {@code null} if the result is not a valid hostname.
     */
    static String normalizeDomain(@NonNull String raw) {
        String host = raw.trim().toLowerCase(Locale.ROOT);
        // Strip scheme (http:// or https://)
        if (host.contains("://")) host = host.substring(host.indexOf("://") + 3);
        // Strip path
        if (host.contains("/")) host = host.substring(0, host.indexOf('/'));
        // Strip port
        if (host.contains(":")) host = host.substring(0, host.indexOf(':'));
        host = host.trim();
        return RegexUtils.isValidHostname(host) ? host : null;
    }

}
