package org.adaway.ui.domainchecker;

/**
 * Pure-Java utility for normalising user input into a bare hostname.
 *
 * Handles:
 *   - Leading/trailing whitespace
 *   - Full URLs (http://, https://)
 *   - Domains with a trailing path or query string
 *   - Mixed-case input — always returns lowercase
 *
 * Returns null for null, empty, or whitespace-only input.
 */
public class DomainNormalizer {

    private DomainNormalizer() {
        // utility class, no instances
    }

    /**
     * Normalise {@code input} to a bare lowercase hostname, or null if the
     * input is blank/null.
     */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Strip protocol prefix
        String host = trimmed;
        if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        } else if (host.startsWith("http://")) {
            host = host.substring("http://".length());
        }

        // Strip path, query, and fragment
        int slashIdx = host.indexOf('/');
        if (slashIdx != -1) {
            host = host.substring(0, slashIdx);
        }
        int queryIdx = host.indexOf('?');
        if (queryIdx != -1) {
            host = host.substring(0, queryIdx);
        }
        int fragmentIdx = host.indexOf('#');
        if (fragmentIdx != -1) {
            host = host.substring(0, fragmentIdx);
        }

        return host.toLowerCase();
    }
}
