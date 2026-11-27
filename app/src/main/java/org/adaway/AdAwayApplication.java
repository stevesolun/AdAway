package org.adaway;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.model.source.PendingSourceApply;
import org.adaway.model.source.SiteCompatibilityAllowlist;
import org.adaway.model.source.SourceModel;
import org.adaway.model.source.SourceOperationCoordinator;
import org.adaway.model.update.UpdateModel;
import org.adaway.model.vpn.VpnModel;
import org.adaway.ui.hosts.FilterSetUpdateService;
import org.adaway.util.AppExecutors;
import org.adaway.util.Constants;
import org.adaway.util.log.ApplicationLog;

/**
 * This class is a custom {@link Application} for AdAway app.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class AdAwayApplication extends Application {
    /**
     * The common source model for the whole application.
     */
    private SourceModel sourceModel;
    /**
     * The common ad block model for the whole application.
     */
    private AdBlockModel adBlockModel;
    /**
     * The common update model for the whole application.
     */
    private UpdateModel updateModel;
    private final SourceOperationCoordinator sourceOperations = new SourceOperationCoordinator();
    private PendingSourceApply pendingSourceApply;

    @Override
    public void onCreate() {
        // Delegate application creation
        super.onCreate();
        // Re-apply locale on startup — AppCompatDelegate does not auto-restore on Android <13
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        boolean forceEnglish = prefs.getBoolean("forceEnglish", false);
        LocaleListCompat locales = forceEnglish
                ? LocaleListCompat.forLanguageTags("en")
                : LocaleListCompat.getEmptyLocaleList();
        AppCompatDelegate.setApplicationLocales(locales);
        // Initialize logging
        ApplicationLog.init(this);
        // Create notification channels
        NotificationHelper.createNotificationChannels(this);

        // Create models
        this.sourceModel = new SourceModel(this);
        this.updateModel = new UpdateModel(this);
        AppExecutors.getInstance().diskIO().execute(() -> {
            if (SiteCompatibilityAllowlist.ensureAllowlistSync(this)) {
                this.sourceModel.syncHostEntries();
            }
        });

        // A restore can be followed by immediate process death. Reconcile on launch too.
        FilterSetUpdateService.syncPreferences(this);
    }

    /**
     * Get the source model.
     *
     * @return The common source model for the whole application.
     */
    public SourceModel getSourceModel() {
        return this.sourceModel;
    }

    public SourceOperationCoordinator getSourceOperations() {
        return this.sourceOperations;
    }

    public synchronized PendingSourceApply getPendingSourceApply() {
        if (this.pendingSourceApply == null) {
            this.pendingSourceApply = new PendingSourceApply(
                    getSharedPreferences(Constants.PREFS_PENDING_SOURCE_APPLY, MODE_PRIVATE));
        }
        return this.pendingSourceApply;
    }

    /**
     * Get the ad block model.
     *
     * @return The common ad block model for the whole application.
     */
    public AdBlockModel getAdBlockModel() {
        // Check cached model
        AdBlockMethod method = PreferenceHelper.getAdBlockMethod(this);
        if (this.adBlockModel == null || this.adBlockModel.getMethod() != method) {
            this.adBlockModel = AdBlockModel.build(this, method);
        }
        return this.adBlockModel;
    }

    /**
     * Invalidate cached VPN rule lookups after direct runtime-table syncs.
     *
     * <p>Normal apply flows already clear the cache in {@link VpnModel#apply()}, but domain
     * checker actions can update user rules without restarting the VPN.</p>
     */
    public void invalidateVpnRulesCache() {
        if (this.adBlockModel instanceof VpnModel) {
            ((VpnModel) this.adBlockModel).invalidateRulesCache();
        }
    }

    /**
     * Get the update model.
     *
     * @return Teh common update model for the whole application.
     */
    public UpdateModel getUpdateModel() {
        return this.updateModel;
    }
}
