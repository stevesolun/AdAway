package org.adaway.model.ai;

import androidx.annotation.NonNull;

/**
 * A single action that the AI agent wants to perform within AdAway.
 *
 * <p>Payloads for category actions must be a {@link org.adaway.model.source.FilterListCategory}
 * name. Payloads for domain actions must be a plain hostname. The {@link #UPDATE_SOURCES} action
 * has no meaningful payload.
 */
public final class AiAgentAction {

    /**
     * The type of action to perform.
     */
    public enum Type {
        /** Add and enable all filter lists in a category (new subscription). */
        SUBSCRIBE_CATEGORY,
        /** Turn on already-subscribed lists in a category. */
        ENABLE_CATEGORY,
        /** Turn off lists in a category without deleting them. */
        DISABLE_CATEGORY,
        /** Trigger an immediate update of all enabled filter lists. */
        UPDATE_SOURCES,
        /** Read-only check: is the payload domain currently blocked? */
        CHECK_DOMAIN,
        /** Add the payload domain to the user allowlist (unblock it). */
        ALLOW_DOMAIN,
        /** Add the payload domain to the user blocklist. */
        BLOCK_DOMAIN
    }

    /** The type of action to perform. */
    public final Type type;

    /**
     * The action payload: a {@link org.adaway.model.source.FilterListCategory#name()} string for
     * category actions, a plain hostname for domain actions, or empty for {@link Type#UPDATE_SOURCES}.
     */
    public final String payload;

    public AiAgentAction(@NonNull Type type, @NonNull String payload) {
        this.type = type;
        this.payload = payload;
    }

    @NonNull
    @Override
    public String toString() {
        return type + "(" + payload + ")";
    }
}
