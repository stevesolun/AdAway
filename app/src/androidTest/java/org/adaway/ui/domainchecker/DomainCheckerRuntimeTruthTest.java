package org.adaway.ui.domainchecker;

import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.RuleKind.SUFFIX;
import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DomainCheckerRuntimeTruthTest {
    private static final int TEST_SOURCE_ID = 929292;
    private static final String SUFFIX_HOST = "domainchecker-runtime.example";
    private static final String CHILD_HOST = "ads.domainchecker-runtime.example";

    private AdAwayApplication application;
    private AppDatabase database;
    private HostListItemDao hostListItemDao;
    private HostEntryDao hostEntryDao;
    private HostsSourceDao hostsSourceDao;
    private AdBlockMethod originalMethod;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        application = (AdAwayApplication) context.getApplicationContext();
        database = AppDatabase.getInstance(application);
        hostListItemDao = database.hostsListItemDao();
        hostEntryDao = database.hostEntryDao();
        hostsSourceDao = database.hostsSourceDao();
        originalMethod = PreferenceHelper.getAdBlockMethod(application);
        cleanup();
        insertSource();
        insertSuffixBlock();
        hostEntryDao.sync();
        application.invalidateVpnRulesCache();
    }

    @After
    public void tearDown() {
        cleanup();
        PreferenceHelper.setAbBlockMethod(application, originalMethod);
        hostEntryDao.sync();
        application.invalidateVpnRulesCache();
    }

    @Test
    public void domainCheckerUsesRootExactTruthAndVpnSuffixTruth() throws Exception {
        PreferenceHelper.setAbBlockMethod(application, ROOT);
        DomainCheckResult rootBaseResult = check(SUFFIX_HOST);
        assertTrue("Root hosts-file mode must report suffix rules for their materialized base host.",
                rootBaseResult.blocked);
        assertEquals(1, rootBaseResult.blockingSources.size());
        assertEquals("Domain checker runtime truth test",
                rootBaseResult.blockingSources.get(0).name);

        DomainCheckResult rootChildResult = check(CHILD_HOST);
        assertFalse("Root hosts-file mode must not report suffix-only child matches as blocked.",
                rootChildResult.blocked);
        assertTrue(rootChildResult.blockingSources.isEmpty());

        PreferenceHelper.setAbBlockMethod(application, VPN);
        DomainCheckResult vpnBaseResult = check(SUFFIX_HOST);
        assertTrue("VPN mode must report suffix rules for base domains.", vpnBaseResult.blocked);
        assertEquals(1, vpnBaseResult.blockingSources.size());
        assertEquals("Domain checker runtime truth test", vpnBaseResult.blockingSources.get(0).name);

        DomainCheckResult vpnChildResult = check(CHILD_HOST);
        assertTrue("VPN mode must report suffix rules for child domains.", vpnChildResult.blocked);
        assertEquals(1, vpnChildResult.blockingSources.size());
        assertEquals("Domain checker runtime truth test", vpnChildResult.blockingSources.get(0).name);
    }

    private DomainCheckResult check(String host) throws InterruptedException {
        DomainCheckerViewModel viewModel = new DomainCheckerViewModel(application);
        CountDownLatch latch = new CountDownLatch(1);
        final DomainCheckResult[] result = new DomainCheckResult[1];
        Observer<DomainCheckResult> observer = new Observer<DomainCheckResult>() {
            @Override
            public void onChanged(@Nullable DomainCheckResult value) {
                result[0] = value;
                latch.countDown();
                viewModel.checkResult.removeObserver(this);
            }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            viewModel.checkResult.observeForever(observer);
            viewModel.checkDomain(host);
        });
        if (!latch.await(3, TimeUnit.SECONDS)) {
            viewModel.checkResult.removeObserver(observer);
            fail("Timed out waiting for domain checker result.");
        }
        return result[0];
    }

    private void insertSource() {
        HostsSource source = new HostsSource();
        source.setId(TEST_SOURCE_ID);
        source.setLabel("Domain checker runtime truth test");
        source.setUrl("https://example.invalid/domain-checker-runtime.txt");
        source.setEnabled(true);
        hostsSourceDao.insert(source);
    }

    private void insertSuffixBlock() {
        HostListItem item = new HostListItem();
        item.setHost(SUFFIX_HOST);
        item.setType(BLOCKED);
        item.setKind(SUFFIX);
        item.setEnabled(true);
        item.setSourceId(TEST_SOURCE_ID);
        item.setGeneration(getActiveGeneration());
        hostListItemDao.insert(item);
    }

    private int getActiveGeneration() {
        try (Cursor cursor = database.getOpenHelper().getWritableDatabase().query(
                "SELECT active_generation FROM hosts_meta WHERE id = 0 LIMIT 1")) {
            if (!cursor.moveToFirst()) {
                return 0;
            }
            return cursor.getInt(0);
        }
    }

    private void cleanup() {
        hostListItemDao.clearSourceHosts(TEST_SOURCE_ID);
        hostsSourceDao.getById(TEST_SOURCE_ID).ifPresent(hostsSourceDao::delete);
    }
}
