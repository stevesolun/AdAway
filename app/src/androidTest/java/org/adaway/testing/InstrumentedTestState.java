package org.adaway.testing;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.WorkManager;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.model.update.ApkUpdateService;
import org.adaway.ui.hosts.FilterSetStore;
import org.adaway.ui.hosts.FilterSetUpdateService;
import org.adaway.util.Constants;

import java.util.concurrent.TimeUnit;

public final class InstrumentedTestState {
    private InstrumentedTestState() {
    }

    public static void resetForPassiveRootUi(@NonNull Context context, @NonNull String phase) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit().clear();
        putBackgroundUpdatePrefs(context, editor, false);
        if (!editor.commit()) {
            throw new AssertionError("Failed to reset app preferences during " + phase);
        }

        PreferenceHelper.setAbBlockMethod(context, AdBlockMethod.ROOT);
        FilterSetStore.setGlobalSchedule(context, FilterSetStore.SCHEDULE_OFF, 1, 3, 0);
        FilterSetStore.setGlobalEnabled(context, false);
        SourceUpdateService.disable(context);
        ApkUpdateService.disable(context);
        FilterSetUpdateService.disable(context);
        resetWorkManager(context, phase);
    }

    public static void resetWorkManager(@NonNull Context context, @NonNull String phase) {
        try {
            WorkManager workManager = WorkManager.getInstance(context);
            workManager.cancelAllWork().getResult().get(5, TimeUnit.SECONDS);
            workManager.pruneWork().getResult().get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Failed to reset WorkManager during " + phase, exception);
        }
    }

    private static void putBackgroundUpdatePrefs(
            @NonNull Context context,
            @NonNull SharedPreferences.Editor editor,
            boolean enabled) {
        editor.putBoolean(context.getString(R.string.pref_update_check_key), enabled)
                .putBoolean(context.getString(R.string.pref_update_check_app_startup_key), enabled)
                .putBoolean(context.getString(R.string.pref_update_check_app_daily_key), enabled)
                .putBoolean(context.getString(R.string.pref_update_check_hosts_daily_key), enabled)
                .putBoolean(context.getString(R.string.pref_automatic_update_daily_key), enabled);
    }
}
