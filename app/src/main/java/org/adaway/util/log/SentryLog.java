package org.adaway.util.log;

import static io.sentry.SentryLevel.ERROR;
import static io.sentry.SentryLevel.INFO;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import org.adaway.helper.PreferenceHelper;

import java.lang.reflect.InvocationTargetException;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.SentryLogLevel;
import io.sentry.android.core.SentryAndroid;
import io.sentry.android.timber.SentryTimberIntegration;

/**
 * This class is a helper to initialize and configuration Sentry.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class SentryLog {
    private static final String SENTRY_DSN_META_DATA = "io.sentry.dsn";

    /**
     * Private constructor.
     */
    private SentryLog() {

    }

    /**
     * Initialize Sentry logging client according user preferences.
     *
     * @param application The application instance.
     */
    public static void init(Application application) {
        setEnabled(application, PreferenceHelper.getTelemetryEnabled(application));
    }

    /**
     * Initialize Sentry logging client.
     *
     * @param application The application instance.
     * @param enabled Whether the application is allowed to send events to Sentry or not.
     */
    public static void setEnabled(Application application, boolean enabled) {
        if (!enabled || !isSupported(application)) {
            closeIfAvailable();
            return;
        }

        // Initialize sentry client manually and bind it to logging
        SentryAndroid.init(application, options -> {
            // Only ERROR-level logs are shipped to Sentry (ATK-12: removed
            // FragmentLifecycleIntegration which was sending all navigation events
            // and INFO-level logs to Sentry cloud).
            options.addIntegration(new SentryTimberIntegration(ERROR, ERROR, SentryLogLevel.ERROR));
        });
    }

    /**
     * Check if this build can send telemetry events.
     *
     * @param application The application instance.
     * @return {@code true} if Sentry is present and configured with a DSN.
     */
    public static boolean isSupported(Application application) {
        return !isStub() && hasConfiguredDsn(application);
    }

    /**
     * Record a breadcrumb.
     *
     * @param message The breadcrumb message.
     */
    public static void recordBreadcrumb(String message) {
        Sentry.configureScope(scope -> {
            Breadcrumb breadcrumb = new Breadcrumb();
            breadcrumb.setMessage(message);
            breadcrumb.setLevel(INFO);
            scope.addBreadcrumb(breadcrumb);
        });
    }

    /**
     * Check if {@link Sentry} implementation is a stub or not.
     *
     * @return {@code true} if the runtime implementation is a stub, {@code false} otherwise.
     */
    public static boolean isStub() {
        try {
            Sentry.class.getDeclaredField("STUB");
            return true;
        } catch (NoSuchFieldException exception) {
            return false;
        }
    }

    private static boolean hasConfiguredDsn(Application application) {
        Bundle metaData = getApplicationMetaData(application);
        if (metaData == null) {
            return false;
        }
        String dsn = metaData.getString(SENTRY_DSN_META_DATA);
        return dsn != null && !dsn.trim().isEmpty();
    }

    private static Bundle getApplicationMetaData(Application application) {
        PackageManager packageManager = application.getPackageManager();
        try {
            ApplicationInfo applicationInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationInfo = packageManager.getApplicationInfo(
                        application.getPackageName(),
                        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA)
                );
            } else {
                applicationInfo = packageManager.getApplicationInfo(
                        application.getPackageName(),
                        PackageManager.GET_META_DATA
                );
            }
            return applicationInfo.metaData;
        } catch (PackageManager.NameNotFoundException exception) {
            return null;
        }
    }

    private static void closeIfAvailable() {
        try {
            Sentry.class.getMethod("close").invoke(null);
        } catch (NoSuchMethodException exception) {
            // The sentrystub implementation has no active client to close.
        } catch (IllegalAccessException | InvocationTargetException exception) {
            // If shutdown is unavailable at runtime, avoid surfacing logging failures to users.
        }
    }
}
