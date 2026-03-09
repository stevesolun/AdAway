package org.adaway.model.ai;

import java.io.IOException;

/**
 * Thrown when an LLM provider API call fails with a structured error.
 *
 * <p>Carries a user-displayable message derived from the provider's error response
 * rather than raw HTTP body text.
 */
public class LlmApiException extends IOException {

    private final int httpCode;

    public LlmApiException(int httpCode, String userMessage) {
        super(userMessage);
        this.httpCode = httpCode;
    }

    /** The HTTP status code returned by the provider. */
    public int getHttpCode() {
        return httpCode;
    }

    /** True if the error is likely due to an invalid or missing API key. */
    public boolean isAuthError() {
        return httpCode == 401 || httpCode == 403;
    }

    /** True if the error is due to rate limiting or quota exhaustion. */
    public boolean isQuotaError() {
        return httpCode == 429;
    }

    /** True if the error is due to a billing / payment issue. */
    public boolean isBillingError() {
        return httpCode == 402;
    }

    /** True if the provider's servers are temporarily unavailable. */
    public boolean isServerError() {
        return httpCode >= 500;
    }
}
