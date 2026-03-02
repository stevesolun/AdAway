package org.adaway.ui.domainchecker;

import java.util.List;

/**
 * Immutable value holder returned by DomainCheckerViewModel after checking a domain.
 */
public class DomainCheckResult {

    /** One entry per filter source that blocks the domain. */
    public static class BlockingSource {
        public final int itemId;         // DB row ID — used to delete user-defined rules
        public final String name;        // Display name of the filter source
        public final boolean isUserRule; // true when source_id == USER_SOURCE_ID

        public BlockingSource(int itemId, String name, boolean isUserRule) {
            this.itemId = itemId;
            this.name = name;
            this.isUserRule = isUserRule;
        }
    }

    public final String domain;
    public final boolean blocked;
    public final boolean userAllowed;
    public final List<BlockingSource> blockingSources;
    public final String unblockAdvice;

    public DomainCheckResult(String domain, boolean blocked, boolean userAllowed,
                             List<BlockingSource> blockingSources, String unblockAdvice) {
        this.domain = domain;
        this.blocked = blocked;
        this.userAllowed = userAllowed;
        this.blockingSources = blockingSources;
        this.unblockAdvice = unblockAdvice;
    }
}
