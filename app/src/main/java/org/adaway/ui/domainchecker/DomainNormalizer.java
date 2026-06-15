package org.adaway.ui.domainchecker;

import org.adaway.util.RegexUtils;

import java.net.IDN;
import java.util.Locale;

/**
 * Pure-Java utility for normalising user input into a bare hostname.
 *
 * Handles:
 *   - Leading/trailing whitespace
 *   - Full URLs (http://, https://)
 *   - Domains with a trailing path or query string
 *   - Mixed-case input — always returns lowercase
 *
 * Returns null for null, empty, whitespace-only, or invalid hostname input.
 */
public class DomainNormalizer {

    private DomainNormalizer() {
        // utility class, no instances
    }

    /**
     * Normalise {@code input} to a bare lowercase ASCII hostname, or null if the
     * input is blank/null/invalid.
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
        if (host.regionMatches(true, 0, "https://", 0, "https://".length())) {
            host = host.substring("https://".length());
        } else if (host.regionMatches(true, 0, "http://", 0, "http://".length())) {
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

        // Strip port number (e.g. example.com:8080 → example.com)
        int colonIdx = host.indexOf(':');
        if (colonIdx != -1) {
            host = host.substring(0, colonIdx);
        }

        host = host.trim();
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty() || RegexUtils.isValidIP(host)) {
            return null;
        }

        try {
            host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return null;
        }

        return RegexUtils.isValidHostname(host) ? host : null;
    }
}
