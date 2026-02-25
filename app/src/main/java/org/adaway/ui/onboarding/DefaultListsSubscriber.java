package org.adaway.ui.onboarding;

import android.content.Context;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.model.source.FilterListCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Subscribes the user to default filter lists on first run.
 *
 * <p>Called from {@link org.adaway.ui.home.HomeActivity} on the diskIO executor
 * when the onboarding complete flag is present in the launch intent.</p>
 *
 * <p>This class performs synchronous DB operations and must never be called
 * on the main thread.</p>
 */
public final class DefaultListsSubscriber {

    private DefaultListsSubscriber() {
        // Utility class — no instances
    }

    /**
     * Subscribe the user to the default filter lists if none are currently configured.
     *
     * @param context Any context; the application context is obtained internally.
     * @return {@code true} if default sources were inserted, {@code false} if sources
     *         already existed (no changes made).
     */
    public static boolean subscribeDefaultsIfEmpty(Context context) {
        HostsSourceDao hostsSourceDao = AppDatabase.getInstance(
                context.getApplicationContext()).hostsSourceDao();

        List<HostsSource> existing = hostsSourceDao.getAll();
        if (!existing.isEmpty()) {
            return false;
        }

        List<FilterListCatalog.CatalogEntry> defaults = FilterListCatalog.getDefaults();
        if (defaults.isEmpty()) {
            return false;
        }

        List<HostsSource> toInsert = new ArrayList<>(defaults.size());
        for (FilterListCatalog.CatalogEntry entry : defaults) {
            toInsert.add(entry.toHostsSource());
        }

        hostsSourceDao.insertAll(toInsert);
        return true;
    }
}
