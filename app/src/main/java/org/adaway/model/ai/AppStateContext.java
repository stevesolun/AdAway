package org.adaway.model.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.FilterListCategory;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Builds a compact, safe JSON snapshot of the app's current ad-blocking state for injection
 * into the AI agent system prompt.
 *
 * <p><strong>Security:</strong> Only {@link FilterListCategory#name()} enum strings and integer
 * counts are injected — never user-controlled labels, URLs, or domain names. This prevents
 * prompt-injection via crafted source labels or blocked hostnames.
 *
 * <p>Must be called on a background (diskIO) thread.
 */
public final class AppStateContext {

    private AppStateContext() {}

    /**
     * Returns a compact JSON string safe to embed directly in the AI system prompt.
     *
     * <p>Example output:
     * <pre>
     * {"categories":{"ADS":{"subscribed":3,"enabled":3},"PRIVACY":{"subscribed":0,"enabled":0},...},
     *  "userAllowlist":12,"userBlocklist":5}
     * </pre>
     */
    @NonNull
    @WorkerThread
    public static String build(@NonNull Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        HostsSourceDao sourceDao = db.hostsSourceDao();
        HostListItemDao listItemDao = db.hostsListItemDao();

        // Build URL → category map from the catalog (no user data — catalog is code-defined)
        Map<String, FilterListCategory> urlToCategory = new HashMap<>();
        for (FilterListCategory cat : FilterListCategory.values()) {
            if (cat == FilterListCategory.USER || cat == FilterListCategory.CUSTOM) continue;
            for (FilterListCatalog.CatalogEntry entry : FilterListCatalog.getByCategory(cat)) {
                urlToCategory.put(entry.url, cat);
            }
        }

        // Count subscribed + enabled per category
        Map<FilterListCategory, int[]> counts = new HashMap<>();
        for (FilterListCategory cat : FilterListCategory.values()) {
            if (cat == FilterListCategory.USER || cat == FilterListCategory.CUSTOM) continue;
            counts.put(cat, new int[]{0, 0}); // [subscribed, enabled]
        }
        List<HostsSource> allSources = sourceDao.getAll();
        for (HostsSource source : allSources) {
            FilterListCategory cat = urlToCategory.get(source.getUrl());
            if (cat == null) continue;
            int[] c = counts.get(cat);
            if (c == null) continue;
            c[0]++; // subscribed
            if (source.isEnabled()) c[1]++; // enabled
        }

        // Count user allowlist + blocklist
        int userAllowlist = 0, userBlocklist = 0;
        List<HostListItem> userItems = listItemDao.getUserList();
        for (HostListItem item : userItems) {
            if (item.getType() == ListType.ALLOWED) userAllowlist++;
            else if (item.getType() == ListType.BLOCKED) userBlocklist++;
        }

        // Build JSON — only enum names + integers, never user-provided strings
        try {
            JSONObject root = new JSONObject();
            JSONObject categories = new JSONObject();
            for (Map.Entry<FilterListCategory, int[]> entry : counts.entrySet()) {
                JSONObject catObj = new JSONObject();
                catObj.put("subscribed", entry.getValue()[0]);
                catObj.put("enabled", entry.getValue()[1]);
                categories.put(entry.getKey().name(), catObj);
            }
            root.put("categories", categories);
            root.put("userAllowlist", userAllowlist);
            root.put("userBlocklist", userBlocklist);
            return root.toString();
        } catch (JSONException e) {
            Timber.w(e, "Failed to build app state JSON");
            return "{}";
        }
    }
}
