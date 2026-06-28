package org.adaway.model.source;

import android.content.Context;

import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.util.AppExecutors;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Protects narrowly scoped first-party site infrastructure that common regional lists can
 * over-block at DNS level.
 */
public final class SiteCompatibilityAllowlist {
    public static final List<String> REQUIRED_DOMAINS = Collections.unmodifiableList(Arrays.asList(
            "ynet.co.il",
            "www.ynet.co.il",
            "images1.ynet.co.il",
            "pplus.ynet.co.il",
            "premium.ynet.co.il",
            "projects.ynet.co.il",
            "weather.ynet.co.il",
            "z.ynet.co.il",
            "www.mynet.co.il",
            "www.ynetnews.com",
            "www.vesty.co.il",
            "yit.co.il",
            "ynet-pic1.yit.co.il",
            "vod-hls.ynethd.com",
            "vod-progressive.ynethd.com"
    ));

    private SiteCompatibilityAllowlist() {
    }

    public static void ensureAllowlist(Context context) {
        final Context appContext = context.getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> ensureAllowlistSync(appContext));
    }

    public static boolean ensureAllowlistSync(Context context) {
        final Context appContext = context.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(appContext);
        ensureUserSource(appContext, database);
        HostListItemDao dao = database.hostsListItemDao();
        List<HostListItem> existing = dao.getUserList();
        boolean inserted = false;

        for (String domain : REQUIRED_DOMAINS) {
            if (!isAlreadyPresent(domain, existing)) {
                dao.insert(buildItem(domain));
                inserted = true;
            }
        }
        return inserted;
    }

    private static void ensureUserSource(Context context, AppDatabase database) {
        if (database.hostsSourceDao().getById(HostsSource.USER_SOURCE_ID).isPresent()) {
            return;
        }
        HostsSource userSource = new HostsSource();
        userSource.setLabel(context.getString(R.string.hosts_user_source));
        userSource.setId(HostsSource.USER_SOURCE_ID);
        userSource.setUrl(HostsSource.USER_SOURCE_URL);
        userSource.setAllowEnabled(true);
        userSource.setRedirectEnabled(true);
        database.hostsSourceDao().insert(userSource);
    }

    static HostListItem buildItem(String domain) {
        HostListItem item = new HostListItem();
        item.setHost(domain);
        item.setType(ListType.ALLOWED);
        item.setKind(RuleKind.EXACT);
        item.setSourceId(HostsSource.USER_SOURCE_ID);
        item.setEnabled(true);
        item.setGeneration(0);
        return item;
    }

    static boolean isAlreadyPresent(String domain, List<HostListItem> existing) {
        for (HostListItem item : existing) {
            if (domain.equals(item.getHost())) {
                return true;
            }
        }
        return false;
    }
}
