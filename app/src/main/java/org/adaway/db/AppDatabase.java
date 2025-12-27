package org.adaway.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.adaway.R;
import org.adaway.db.converter.ListTypeConverter;
import org.adaway.db.converter.ZonedDateTimeConverter;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsMeta;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.ListType;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.util.AppExecutors;

import static org.adaway.db.Migrations.MIGRATION_1_2;
import static org.adaway.db.Migrations.MIGRATION_2_3;
import static org.adaway.db.Migrations.MIGRATION_3_4;
import static org.adaway.db.Migrations.MIGRATION_4_5;
import static org.adaway.db.Migrations.MIGRATION_5_6;
import static org.adaway.db.Migrations.MIGRATION_6_7;
import static org.adaway.db.Migrations.MIGRATION_7_8;
import static org.adaway.db.Migrations.MIGRATION_8_9;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;

/**
 * This class is the application database based on Room.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Database(entities = {HostsSource.class, HostListItem.class, HostEntry.class, HostsMeta.class}, version = 9)
@TypeConverters({ListTypeConverter.class, ZonedDateTimeConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    /**
     * The database singleton instance.
     */
    private static volatile AppDatabase instance;

    /**
     * Get the database instance.
     *
     * @param context The application context.
     * @return The database instance.
     */
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "app.db"
                    ).setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(new Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            // Ensure the single-row hosts_meta exists.
                            db.execSQL("INSERT OR IGNORE INTO `hosts_meta` (`id`, `active_generation`) VALUES (0, 0)");
                            AppExecutors.getInstance().diskIO().execute(
                                    () -> AppDatabase.initialize(context, instance)
                            );
                        }
                    }).addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_2_3,
                            MIGRATION_3_4,
                            MIGRATION_4_5,
                            MIGRATION_5_6,
                            MIGRATION_6_7,
                            MIGRATION_7_8,
                            MIGRATION_8_9
                    ).build();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the database content.
     * Sets up default filter sources and the Facebook whitelist.
     */
    private static void initialize(Context context, AppDatabase database) {
        // Check if there is no hosts source
        HostsSourceDao hostsSourceDao = database.hostsSourceDao();
        HostListItemDao hostListItemDao = database.hostsListItemDao();
        if (!hostsSourceDao.getAll().isEmpty()) {
            return;
        }
        
        // User list
        HostsSource userSource = new HostsSource();
        userSource.setLabel(context.getString(R.string.hosts_user_source));
        userSource.setId(USER_SOURCE_ID);
        userSource.setUrl(USER_SOURCE_URL);
        userSource.setAllowEnabled(true);
        userSource.setRedirectEnabled(true);
        hostsSourceDao.insert(userSource);
        
        // Add default sources from catalog (Ads + Malware categories enabled by default)
        for (FilterListCatalog.CatalogEntry entry : FilterListCatalog.getDefaults()) {
            hostsSourceDao.insert(entry.toHostsSource());
        }
        
        // Initialize Facebook whitelist - ensures Facebook always works
        initializeFacebookWhitelist(context, hostListItemDao);
    }
    
    /**
     * Initialize the Facebook whitelist to ensure Facebook services always work.
     * This adds Facebook, Instagram, WhatsApp, and Messenger domains to the user's allowlist.
     */
    private static void initializeFacebookWhitelist(Context context, HostListItemDao hostListItemDao) {
        // Add each Facebook domain to the allowlist
        for (String domain : FilterListCatalog.FACEBOOK_WHITELIST_DOMAINS) {
            // Skip wildcard patterns (they would need special handling)
            if (domain.startsWith("*")) {
                continue;
            }
            HostListItem item = new HostListItem();
            item.setType(ListType.ALLOWED);
            item.setHost(domain);
            item.setEnabled(true);
            item.setSourceId(USER_SOURCE_ID);
            hostListItemDao.insert(item);
        }
    }

    /**
     * Get the hosts source DAO.
     *
     * @return The hosts source DAO.
     */
    public abstract HostsSourceDao hostsSourceDao();

    /**
     * Get the hosts list item DAO.
     *
     * @return The hosts list item DAO.
     */
    public abstract HostListItemDao hostsListItemDao();

    /**
     * Get the hosts entry DAO.
     *
     * @return The hosts entry DAO.
     */
    public abstract HostEntryDao hostEntryDao();
}
