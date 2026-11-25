package org.adaway.ui.domainchecker;

import org.adaway.db.entity.HostsSource;
import org.adaway.db.entity.RuleKind;

import java.util.Collections;
import java.util.List;

/**
 * Immutable value holder returned by DomainCheckerViewModel after checking a domain.
 */
public class DomainCheckResult {
    public enum Status {
        BLOCKED,
        ALLOWED,
        REDIRECTED,
        UNKNOWN
    }

    /** One entry per filter source that blocks the domain. */
    public static class BlockingSource {
        public final int itemId;         // DB row ID — used to delete user-defined rules
        public final String name;        // Display name of the filter source
        public final boolean isUserRule; // true when source_id == USER_SOURCE_ID
        public final String host;
        public final RuleKind kind;

        public BlockingSource(int itemId, String name, boolean isUserRule, String host, RuleKind kind) {
            this.itemId = itemId;
            this.name = name;
            this.isUserRule = isUserRule;
            this.host = host;
            this.kind = kind;
        }

        public boolean canDelete() {
            return isUserRule && kind == RuleKind.EXACT;
        }
    }

    public static class AllowSource {
        public final int sourceId;
        public final String name;
        public final String host;
        public final RuleKind kind;

        AllowSource(int sourceId, String name, String host, RuleKind kind) {
            this.sourceId = sourceId;
            this.name = name;
            this.host = host;
            this.kind = kind;
        }
    }

    public final String domain;
    public final Status status;
    public final boolean blocked;
    public final boolean userAllowed;
    public final List<BlockingSource> blockingSources;
    public final List<AllowSource> allowSources;
    public final String unblockAdvice;

    public DomainCheckResult(String domain, boolean blocked, boolean userAllowed,
                             List<BlockingSource> blockingSources, String unblockAdvice) {
        this(domain, blocked ? Status.BLOCKED : userAllowed ? Status.ALLOWED : Status.UNKNOWN,
                userAllowed, blockingSources, unblockAdvice);
    }

    public DomainCheckResult(String domain, Status status, boolean userAllowed,
                             List<BlockingSource> blockingSources, String unblockAdvice) {
        this(domain, status, userAllowed, blockingSources, Collections.emptyList(), unblockAdvice);
    }

    DomainCheckResult(String domain, Status status, boolean userAllowed,
                      List<BlockingSource> blockingSources, List<AllowSource> allowSources,
                      String unblockAdvice) {
        this.domain = domain;
        this.status = status;
        this.blocked = status == Status.BLOCKED || status == Status.REDIRECTED;
        this.userAllowed = userAllowed;
        this.blockingSources = blockingSources;
        this.allowSources = allowSources;
        this.unblockAdvice = unblockAdvice;
    }

    public boolean hasRemovableAllow() {
        return allowSources.stream().anyMatch(s -> s.sourceId == HostsSource.USER_SOURCE_ID
                && s.kind == RuleKind.EXACT && s.host.equalsIgnoreCase(domain));
    }
}
