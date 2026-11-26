package org.adaway.db;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.adaway.R;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.ListType;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class FreshSourceIdentityTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void firstRestoredSourceIsVisibleBeforeDeferredDefaultsRun() {
        AtomicBoolean queued = new AtomicBoolean();
        AppDatabase db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .addCallback(AppDatabase.creationCallback(context, task -> queued.set(true)))
                .build();
        try {
            HostsSourceDao sources = db.hostsSourceDao();
            HostsSource first = source("AdAway Official", "https://adaway.org/hosts.txt", true);
            HostsSource second = source("Second", "https://second.example/hosts.txt", true);
            HostsSource disabled = source("Third", "https://third.example/hosts.txt", false);
            db.runInTransaction(() -> {
                sources.insert(first);
                sources.insert(second);
                sources.insert(disabled);
            });
            assertTrue("Opened a new database without executing the default seed", queued.get());
            int firstId = sources.getByUrl(first.getUrl()).orElseThrow().getId();
            assertTrue("First restored source took ID " + firstId, firstId > USER_SOURCE_ID);
            assertEquals(3, sources.getAll().size());
            assertEquals(firstId, sources.getAll().get(0).getId());
            assertEquals(2, sources.getEnabled().size());
            assertTrue(sources.getByUrl(first.getUrl()).orElseThrow().isEnabled());
            assertFalse(sources.getByUrl(disabled.getUrl()).orElseThrow().isEnabled());

            HostsSource user = sources.getById(USER_SOURCE_ID).orElseThrow();
            assertEquals(USER_SOURCE_URL, user.getUrl());
            assertEquals(context.getString(R.string.hosts_user_source), user.getLabel());
            assertTrue(user.isEnabled() && user.isAllowEnabled() && user.isRedirectEnabled());
        } finally {
            db.close();
        }
    }

    @Test
    public void reopenDoesNotRewriteConflictingIdentityOrItsRules() {
        String name = "identity-conflict-" + UUID.randomUUID() + ".db";
        String url = "https://previous.example/hosts.txt";
        String host = "keep-existing.example";
        try {
            AppDatabase old = Room.databaseBuilder(context, AppDatabase.class, name).build();
            try {
                old.hostsSourceDao().insert(source("Already conflicted", url, false));
                assertEquals(USER_SOURCE_ID, old.hostsSourceDao().getByUrl(url).orElseThrow().getId());
                HostListItem rule = new HostListItem();
                rule.setHost(host);
                rule.setType(ListType.ALLOWED);
                rule.setEnabled(true);
                rule.setSourceId(USER_SOURCE_ID);
                old.hostsListItemDao().insert(rule);
            } finally {
                old.close();
            }
            AppDatabase reopened = Room.databaseBuilder(context, AppDatabase.class, name)
                    .addCallback(AppDatabase.creationCallback(context, task -> fail("Not a new DB")))
                    .build();
            try {
                HostsSource preserved = reopened.hostsSourceDao().getById(USER_SOURCE_ID).orElseThrow();
                assertEquals(url, preserved.getUrl());
                assertFalse(preserved.isEnabled());
                HostListItem rule = reopened.hostsListItemDao().getExactEntriesForHost(host).get(0);
                assertEquals(USER_SOURCE_ID, rule.getSourceId());
                assertEquals(ListType.ALLOWED, rule.getType());
            } finally {
                reopened.close();
            }
        } finally {
            context.deleteDatabase(name);
        }
    }

    private static HostsSource source(String label, String url, boolean enabled) {
        HostsSource row = new HostsSource();
        row.setLabel(label);
        row.setUrl(url);
        row.setEnabled(enabled);
        return row;
    }
}
