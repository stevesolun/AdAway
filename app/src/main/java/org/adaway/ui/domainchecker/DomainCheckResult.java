package org.adaway.ui.domainchecker;

import java.util.List;

/**
 * Immutable value holder returned by DomainCheckerViewModel after checking a domain.
 */
public class DomainCheckResult {

    public final String domain;
    public final boolean blocked;
    public final boolean userAllowed;
    public final List<String> blockingSources;
    public final String unblockAdvice;

    public DomainCheckResult(String domain, boolean blocked, boolean userAllowed,
                             List<String> blockingSources, String unblockAdvice) {
        this.domain = domain;
        this.blocked = blocked;
        this.userAllowed = userAllowed;
        this.blockingSources = blockingSources;
        this.unblockAdvice = unblockAdvice;
    }
}
