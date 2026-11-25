package org.adaway.model.backup;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.UiThread;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostListItemDao;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.RuleKind;
import org.adaway.model.backup.BackupFormat.ParsedBackup;
import org.adaway.model.error.HostErrorException;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.util.AppExecutors;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;

import timber.log.Timber;

/**
 * This class is a helper class to import user lists and hosts sources to a backup file.<br>
 * Importing a file source will no restore read access from Storage Access Framework.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class BackupImporter {
    static final int MAX_IMPORT_CHARACTERS = BackupFormat.MAX_CHARACTERS;

    private BackupImporter() {

    }

    /**
     * Import a backup file.
     *
     * @param context   The application context.
     * @param backupUri The URI of a backup file.
     */
    @UiThread
    public static void importFromBackup(Context context, Uri backupUri) {
        Context appContext = context.getApplicationContext();
        AppExecutors.getInstance().diskIO().execute(() -> {
            int message = R.string.import_success;
            try {
                importBackup(appContext, backupUri);
            } catch (BackupBusyException e) {
                Timber.i("Backup restore not started: another operation is in progress.");
                message = R.string.import_busy_retry;
            } catch (IOException | RuntimeException e) {
                Timber.e(e, "Failed to import backup.");
                message = R.string.import_failed;
            }
            int resultMessage = message;
            AppExecutors.getInstance().mainThread().execute(
                    () -> notifyImportEnd(appContext, resultMessage));
        });
    }

    @UiThread
    private static void notifyImportEnd(Context context, int message) {
        Toast.makeText(
                context,
                context.getString(message),
                Toast.LENGTH_LONG
        ).show();
    }

    static void importBackup(Context context, Uri backupUri) throws IOException {
        ParsedBackup backup;
        try (InputStream inputStream = context.getContentResolver().openInputStream(backupUri)) {
            if (inputStream == null) {
                throw new IOException("Backup provider returned no input stream.");
            }
            JSONObject backupObject = new JSONObject(readBackup(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)));
            backup = BackupFormat.parseBackup(backupObject);
        } catch (JSONException exception) {
            throw new IOException("Failed to parse backup file.", exception);
        } catch (SecurityException exception) {
            throw new IOException("Backup read permission denied.", exception);
        } catch (IOException exception) {
            throw new IOException("Failed to read backup file.", exception);
        }
        restoreBackup(context, backup);
    }

    static String readBackup(Reader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        char[] buffer = new char[8192];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (count > MAX_IMPORT_CHARACTERS - content.length()) {
                throw new IOException("Backup exceeds maximum size.");
            }
            content.append(buffer, 0, count);
        }
        return content.toString();
    }

    private static void restoreBackup(Context context, ParsedBackup backup) throws IOException {
        Context appContext = context.getApplicationContext();
        if (!(appContext instanceof AdAwayApplication)) {
            throw new IOException("Application restore coordinator is unavailable.");
        }
        AdAwayApplication application = (AdAwayApplication) appContext;
        SourceModel sourceModel = application.getSourceModel();
        if (sourceModel == null) {
            throw new IOException("Application source model is unavailable.");
        }
        try (SourceOperationCoordinator.Lease ignored =
                     application.getSourceOperations().acquire()) {
            sourceModel.runSourceMutation(() -> {
                applyBackup(AppDatabase.getInstance(appContext), backup);
                try {
                    sourceModel.syncHostEntries();
                } catch (RuntimeException exception) {
                    // The transaction committed even if rebuilding derived metadata failed.
                    application.invalidateVpnRulesCache();
                    throw exception;
                }
                return null;
            });
        } catch (HostErrorException exception) {
            throw new BackupBusyException(exception);
        } catch (RuntimeException exception) {
            throw new IOException("Failed to restore backup and refresh runtime rules.", exception);
        }
    }

    static void importBackup(AppDatabase database, JSONObject backupObject) throws JSONException {
        applyBackup(database, BackupFormat.parseBackup(backupObject));
    }

    private static void applyBackup(AppDatabase database, ParsedBackup backup) {
        HostsSourceDao hostsSourceDao = database.hostsSourceDao();
        HostListItemDao hostListItemDao = database.hostsListItemDao();
        database.runInTransaction(() -> {
            for (HostsSource source : backup.sources) {
                reconcileSource(database, source);
            }
            for (HostListItem host : backup.hosts) {
                List<HostListItem> existing = host.getKind() == RuleKind.EXACT
                        ? hostListItemDao.getExactEntriesForHost(host.getHost())
                        : hostListItemDao.getSuffixEntriesForHost(host.getHost());
                for (HostListItem item : existing) {
                    if (item.getSourceId() == USER_SOURCE_ID) {
                        hostListItemDao.delete(item);
                    }
                }
                hostListItemDao.insert(host);
            }
            hostsSourceDao.updateActiveRuleStats(USER_SOURCE_ID);
        });
    }

    private static void reconcileSource(AppDatabase database, HostsSource restored) {
        HostsSourceDao sources = database.hostsSourceDao();
        HostsSource current = sources.getByUrl(restored.getUrl()).orElse(null);
        if (current == null) {
            sources.insert(restored);
            return;
        }
        int id = current.getId();
        if (id == USER_SOURCE_ID) {
            throw new IllegalArgumentException("Cannot restore the reserved user source.");
        }
        boolean formatChanged = current.isAllowEnabled() != restored.isAllowEnabled()
                || current.isRedirectEnabled();
        boolean selectionChanged = current.isEnabled() != restored.isEnabled();
        current.setLabel(restored.getLabel());
        current.setEnabled(restored.isEnabled());
        current.setAllowEnabled(restored.isAllowEnabled());
        current.setRedirectEnabled(false);
        sources.update(current);
        if (formatChanged) {
            // Match source-editor invalidation without replacing the source or its stable ID.
            database.hostsListItemDao().clearSourceHosts(id);
            database.getOpenHelper().getWritableDatabase().execSQL(
                    "DELETE FROM root_host_entries_stage WHERE source_id = ?", new Object[]{id});
            sources.clearProperties(id);
            sources.updateSkippedCount(id, 0);
            sources.clearDownloadError(id);
        } else {
            if (selectionChanged) {
                sources.setSourceItemsEnabled(id, restored.isEnabled());
            }
            sources.updateActiveRuleStats(id);
        }
        if (formatChanged || selectionChanged) {
            database.hostEntryDao().invalidateRootExportMaterializedCache();
        }
    }

    static final class BackupBusyException extends IOException {
        BackupBusyException(HostErrorException cause) {
            super("Another operation is in progress. Wait for it to finish and retry restore.", cause);
        }
    }
}
