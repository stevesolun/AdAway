package org.adaway.ui.domainchecker;

import static org.junit.Assert.*;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.adaway.util.AppExecutors;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DomainCheckerRuleScopeInstrumentedTest {
    @Test public void userSuffixHasLocalizedNameAndCannotBeDeletedFromChild() throws Exception {
        AdAwayApplication app = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(app);
        HostListItemDao dao = db.hostsListItemDao();
        String base = UUID.randomUUID() + ".invalid";
        HostListItem rule = new HostListItem();
        rule.setHost(base);
        rule.setSourceId(HostsSource.USER_SOURCE_ID);
        rule.setKind(RuleKind.SUFFIX);
        rule.setType(ListType.BLOCKED);
        rule.setEnabled(true);
        dao.insert(rule);
        try {
            int id = dao.getSuffixEntriesForHost(base).get(0).getId();
            DomainCheckerViewModel model = new DomainCheckerViewModel(app);
            // At the apex a suffix matches in both root and VPN modes.
            settle(() -> model.checkDomain(base));
            DomainCheckResult.BlockingSource src = model.checkResult.getValue().blockingSources.get(0);
            assertEquals(app.getString(R.string.hosts_user_source), src.name);
            assertFalse(src.canDelete());
            settle(() -> model.deleteRule(id, "first." + base));
            assertEquals(R.string.domain_checker_change_failed, model.getOperationResult().getValue().error);
            assertNotNull(dao.getById(id));
            assertEquals(ListType.BLOCKED, db.hostEntryDao().getTypeForHost("second." + base));
            settle(() -> model.blockDomain(base));
            assertEquals(0, model.getOperationResult().getValue().error);
            assertNotNull("Blocking only the apex must preserve the suffix row", dao.getById(id));
            assertEquals(ListType.BLOCKED, db.hostEntryDao().getTypeForHost("second." + base));
        } finally {
            dao.deleteUserFromHost(base);
        }
    }

    private static void settle(Runnable action) throws InterruptedException {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
        CountDownLatch done = new CountDownLatch(1);
        AppExecutors.getInstance().diskIO().execute(done::countDown);
        assertTrue(done.await(30, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
