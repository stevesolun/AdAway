package org.adaway.model.source;

import android.content.Context;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.util.AppExecutors;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Ensures that WhatsApp and Telegram critical domains are present in the
 * user allowlist so they are never accidentally blocked by filter lists.
 *
 * <p>The wildcard entries (e.g. {@code *.whatsapp.net}) are expanded during
 * the {@code HostEntryDao.sync()} phase via SQL LIKE patterns, so DNS-level
 * protection covers all subdomains automatically once an entry is present.</p>
 *
 * <p>Call {@link #ensureAllowlist(Context)} after onboarding or on first
 * run; subsequent calls are idempotent — existing entries are never
 * duplicated.</p>
 */
public final class WaTgSafetyAllowlist {

    /**
     * The ordered list of wildcard/domain entries to protect.
     * Exactly 12 entries: WhatsApp, WA.me, Telegram, fbcdn, facebook.
     */
    public static final List<String> REQUIRED_DOMAINS = Collections.unmodifiableList(Arrays.asList(
            "*.whatsapp.com",
            "*.whatsapp.net",
            "*.fbcdn.net",
            "*.facebook.com",
            "wa.me",
            "*.wa.me",
            "telegram.org",
            "*.telegram.org",
            "telegram.me",
            "*.telegram.me",
            "t.me",
            "*.t.me"
    ));

    private WaTgSafetyAllowlist() {
        // Utility class — no instances
    }

    /**
     * Ensure all required WhatsApp/Telegram allowlist entries exist in the
     * user list (source_id = {@link HostsSource#USER_SOURCE_ID}).
     *
     * <p>Runs on the {@link AppExecutors#diskIO()} thread. Safe to call from
     * any thread — the executor post is asynchronous.</p>
     *
     * @param context Any context; application context is obtained internally.
     */
    public static void ensureAllowlist(Context context) {
        final Context appContext = context.getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            HostListItemDao dao = AppDatabase.getInstance(appContext).hostsListItemDao();
            List<HostListItem> existing = dao.getUserList();

            for (String domain : REQUIRED_DOMAINS) {
                if (!isAlreadyPresent(domain, existing)) {
                    dao.insert(buildItem(domain));
                }
            }
        });
    }

    /**
     * Build a new {@link HostListItem} for the given domain with the correct
     * defaults for a user-list allowlist entry.
     *
     * <p>Package-visible so unit tests can call it without an Android runtime.</p>
     *
     * @param domain The hostname or wildcard pattern (e.g. {@code *.whatsapp.net}).
     * @return A configured but un-persisted {@link HostListItem}.
     */
    static HostListItem buildItem(String domain) {
        HostListItem item = new HostListItem();
        item.setHost(domain);
        item.setType(ListType.ALLOWED);
        item.setSourceId(HostsSource.USER_SOURCE_ID);
        item.setEnabled(true);
        item.setGeneration(0);
        return item;
    }

    /**
     * Return {@code true} if {@code domain} already appears in {@code existing}
     * as a user-list ALLOWED entry.
     *
     * <p>Package-visible for unit testing.</p>
     *
     * @param domain   The domain/wildcard to check.
     * @param existing The current user-list entries.
     * @return {@code true} if an exact host match is found.
     */
    static boolean isAlreadyPresent(String domain, List<HostListItem> existing) {
        for (HostListItem item : existing) {
            if (domain.equals(item.getHost())) {
                return true;
            }
        }
        return false;
    }
}
