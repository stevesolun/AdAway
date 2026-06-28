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
import org.adaway.db.converter.RuleKindConverter;
import org.adaway.db.converter.ZonedDateTimeConverter;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsMeta;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.HostsStats;
import org.adaway.db.entity.HostEntry;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RootHostEntry;
import org.adaway.db.entity.RootHostSkipEntry;
import org.adaway.db.entity.RootHostStageEntry;
import org.adaway.model.source.FilterListCatalog;
import org.adaway.model.source.SiteCompatibilityAllowlist;
import org.adaway.model.source.WaTgSafetyAllowlist;
import org.adaway.util.AppExecutors;

import static org.adaway.db.Migrations.MIGRATION_1_2;
import static org.adaway.db.Migrations.MIGRATION_2_3;
import static org.adaway.db.Migrations.MIGRATION_3_4;
import static org.adaway.db.Migrations.MIGRATION_4_5;
import static org.adaway.db.Migrations.MIGRATION_5_6;
import static org.adaway.db.Migrations.MIGRATION_6_7;
import static org.adaway.db.Migrations.MIGRATION_7_8;
import static org.adaway.db.Migrations.MIGRATION_8_9;
import static org.adaway.db.Migrations.MIGRATION_9_10;
import static org.adaway.db.Migrations.MIGRATION_10_11;
import static org.adaway.db.Migrations.MIGRATION_11_12;
import static org.adaway.db.Migrations.MIGRATION_12_13;
import static org.adaway.db.Migrations.MIGRATION_13_14;
import static org.adaway.db.Migrations.MIGRATION_14_15;
import static org.adaway.db.Migrations.MIGRATION_15_16;
import static org.adaway.db.Migrations.MIGRATION_16_17;
import static org.adaway.db.Migrations.MIGRATION_17_18;
import static org.adaway.db.Migrations.MIGRATION_18_19;
import static org.adaway.db.Migrations.MIGRATION_19_20;
import static org.adaway.db.Migrations.MIGRATION_20_21;
import static org.adaway.db.Migrations.MIGRATION_21_22;
import static org.adaway.db.Migrations.MIGRATION_22_23;
import static org.adaway.db.Migrations.MIGRATION_23_24;
import static org.adaway.db.Migrations.MIGRATION_24_25;
import static org.adaway.db.Migrations.MIGRATION_25_26;
import static org.adaway.db.Migrations.MIGRATION_26_27;
import static org.adaway.db.Migrations.MIGRATION_27_28;
import static org.adaway.db.Migrations.MIGRATION_28_29;
import static org.adaway.db.Migrations.MIGRATION_29_30;
import static org.adaway.db.Migrations.MIGRATION_30_31;
import static org.adaway.db.Migrations.MIGRATION_31_32;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.HostsSource.USER_SOURCE_URL;

/**
 * This class is the application database based on Room.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@Database(
        entities = {
                HostsSource.class,
                HostListItem.class,
                HostEntry.class,
                RootHostEntry.class,
                RootHostSkipEntry.class,
                RootHostStageEntry.class,
                HostsMeta.class,
                HostsStats.class
        },
        version = 32
)
@TypeConverters({ListTypeConverter.class, RuleKindConverter.class, ZonedDateTimeConverter.class})
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
                            optimizeCreatedDatabaseStorage(db);
                            // Ensure the single-row hosts_meta exists.
                            db.execSQL("INSERT OR IGNORE INTO `hosts_meta` (`id`, `active_generation`) VALUES (0, 0)");
                            db.execSQL("INSERT OR IGNORE INTO `hosts_stats` " +
                                    "(`id`, `blocked_count`, `blocked_exact_count`, " +
                                    "`allowed_count`, `redirected_count`, `active_rule_count`) " +
                                    "VALUES (0, 0, 0, 0, 0, 0)");
                            AppExecutors.getInstance().diskIO().execute(() -> {
                                AppDatabase.initialize(context, instance);
                                WaTgSafetyAllowlist.ensureAllowlistSync(context);
                                SiteCompatibilityAllowlist.ensureAllowlistSync(context);
                            });
                        }
                    }).addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_2_3,
                            MIGRATION_3_4,
                            MIGRATION_4_5,
                            MIGRATION_5_6,
                            MIGRATION_6_7,
                            MIGRATION_7_8,
                            MIGRATION_8_9,
                            MIGRATION_9_10,
                            MIGRATION_10_11,
                            MIGRATION_11_12,
                            MIGRATION_12_13,
                            MIGRATION_13_14,
                            MIGRATION_14_15,
                            MIGRATION_15_16,
                            MIGRATION_16_17,
                            MIGRATION_17_18,
                            MIGRATION_18_19,
                            MIGRATION_19_20,
                            MIGRATION_20_21,
                            MIGRATION_21_22,
                            MIGRATION_22_23,
                            MIGRATION_23_24,
                            MIGRATION_24_25,
                            MIGRATION_25_26,
                            MIGRATION_26_27,
                            MIGRATION_27_28,
                            MIGRATION_28_29,
                            MIGRATION_29_30,
                            MIGRATION_30_31,
                            MIGRATION_31_32
                    ).build();
                }
            }
        }
        return instance;
    }

    /**
     * Apply storage optimizations Room cannot express in entity annotations.
     *
     * Direct database builders used by connected benchmarks should call this after opening the
     * database so they measure the same table layout production creates in {@link Callback}.
     */
    public static void optimizeCreatedDatabaseStorage(@NonNull SupportSQLiteDatabase db) {
        Migrations.optimizeHostEntriesStorage(db);
        Migrations.optimizeRootHostEntriesStorage(db);
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
        hostsSourceDao.updateSize(USER_SOURCE_ID);
        database.hostEntryDao().refreshStatsFromActiveGeneration();
    }
    
    /**
     * Initialize the Facebook whitelist to ensure Facebook services always work.
     * This adds Facebook, Instagram, WhatsApp, and Messenger domains to the user's allowlist.
     */
    private static void initializeFacebookWhitelist(Context context, HostListItemDao hostListItemDao) {
        // Add each Facebook domain to the allowlist
        for (String domain : FilterListCatalog.FACEBOOK_WHITELIST_DOMAINS) {
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
