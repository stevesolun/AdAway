package org.adaway.model.vpn;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.db.entity.RuleKind.SUFFIX;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VpnModelCacheInvalidationTest {
    private static final int TEST_SOURCE_ID = 909090;
    private static final String TEST_HOST_PATTERN = "%cache-invalidation%.invalid";

    private AdAwayApplication application;
    private AppDatabase database;
    private HostEntryDao hostEntryDao;
    private HostListItemDao hostListItemDao;
    private HostsSourceDao hostsSourceDao;
    private AdBlockMethod originalMethod;
    private String suffixHost;
    private String childHost;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        application = (AdAwayApplication) context.getApplicationContext();
        originalMethod = PreferenceHelper.getAdBlockMethod(application);
        PreferenceHelper.setAbBlockMethod(application, VPN);
        database = AppDatabase.getInstance(application);
        hostEntryDao = database.hostEntryDao();
        hostListItemDao = database.hostsListItemDao();
        hostsSourceDao = database.hostsSourceDao();
        long uniqueSuffix = System.nanoTime();
        suffixHost = "cache-invalidation-" + uniqueSuffix + ".invalid";
        childHost = "ads." + suffixHost;
        cleanup();
        insertSource();
    }

    @After
    public void tearDown() {
        cleanup();
        PreferenceHelper.setAbBlockMethod(application, originalMethod);
    }

    @Test
    public void invalidateRulesCacheRefreshesLiveVpnTruthAfterDirectSync() {
        VpnModel vpnModel = (VpnModel) application.getAdBlockModel();
        insertHostListItem(suffixHost, BLOCKED, SUFFIX, TEST_SOURCE_ID);
        hostEntryDao.sync();
        application.invalidateVpnRulesCache();

        HostEntry blocked = vpnModel.getEntry(childHost);
        assertEquals(BLOCKED, blocked.getType());

        insertHostListItem(childHost, ALLOWED, EXACT, USER_SOURCE_ID);
        hostEntryDao.sync();
        application.invalidateVpnRulesCache();

        assertEquals(ALLOWED, vpnModel.getEntry(childHost).getType());
    }

    @Test
    public void sourceModelSyncHostEntriesInvalidatesLiveVpnTruth() {
        VpnModel vpnModel = (VpnModel) application.getAdBlockModel();
        insertHostListItem(suffixHost, BLOCKED, SUFFIX, TEST_SOURCE_ID);
        hostEntryDao.sync();
        application.invalidateVpnRulesCache();

        assertEquals(BLOCKED, vpnModel.getEntry(childHost).getType());

        insertHostListItem(childHost, ALLOWED, EXACT, USER_SOURCE_ID);
        application.getSourceModel().syncHostEntries();

        assertEquals(ALLOWED, vpnModel.getEntry(childHost).getType());
    }

    private void insertSource() {
        HostsSource source = new HostsSource();
        source.setId(TEST_SOURCE_ID);
        source.setLabel("Cache invalidation test");
        source.setUrl("https://example.invalid/cache-invalidation-hosts.txt");
        source.setEnabled(true);
        hostsSourceDao.insert(source);
    }

    private void insertHostListItem(String host, org.adaway.db.entity.ListType type,
            org.adaway.db.entity.RuleKind kind, int sourceId) {
        HostListItem item = new HostListItem();
        item.setHost(host);
        item.setType(type);
        item.setKind(kind);
        item.setEnabled(true);
        item.setSourceId(sourceId);
        item.setGeneration(sourceId == USER_SOURCE_ID ? 0 : hostEntryDao.getActiveGeneration());
        hostListItemDao.insert(item);
    }

    private void cleanup() {
        hostListItemDao.clearSourceHosts(TEST_SOURCE_ID);
        hostListItemDao.deleteUserFromHost(suffixHost);
        hostListItemDao.deleteUserFromHost(childHost);
        database.getOpenHelper().getWritableDatabase().execSQL(
                "DELETE FROM hosts_lists WHERE source_id = ? AND host LIKE ?",
                new Object[]{USER_SOURCE_ID, TEST_HOST_PATTERN});
        hostsSourceDao.getById(TEST_SOURCE_ID).ifPresent(hostsSourceDao::delete);
        hostEntryDao.sync();
        application.invalidateVpnRulesCache();
    }
}
