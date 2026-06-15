package io.sentry.android.timber;

import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryLogLevel;

public class SentryTimberIntegration implements Integration {
    public SentryTimberIntegration(
            SentryLevel minEventLevel,
            SentryLevel minBreadcrumbLevel,
            SentryLogLevel minLogLevel
    ) {
        // Stub
    }
}
