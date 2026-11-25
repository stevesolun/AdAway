package org.adaway.db;

import static org.junit.Assert.assertFalse;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class HostListSnapshotTest {
    private AppDatabase database;

    @Test
    public void exactAndSuffixReadsShareATransactionInBothModes() {
        String host = "child.snapshot.example";
        List<Boolean> transactions = new ArrayList<>();
        this.database = Room.inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(), AppDatabase.class)
                .allowMainThreadQueries()
                .setQueryCallback((sql, args) -> {
                    if (args.contains(host) || args.contains("snapshot.example")) {
                        transactions.add(this.database.inTransaction());
                    }
                }, Runnable::run)
                .build();
        try {
            this.database.hostsListItemDao().getEntriesForHost(host);
            assertFalse(transactions.isEmpty());
            assertFalse(transactions.contains(false));
            transactions.clear();
            this.database.hostsListItemDao().getRootEntriesForHost(host);
            assertFalse(transactions.isEmpty());
            assertFalse(transactions.contains(false));
        } finally {
            this.database.close();
        }
    }
}
