package org.adaway.ui.domainchecker;

import org.adaway.util.RegexUtils;

import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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

        boolean fullUrl = trimmed.regionMatches(true, 0, "https://", 0, 8)
                || trimmed.regionMatches(true, 0, "http://", 0, 7);
        String address = trimmed;
        if (!fullUrl) {
            // Keep the bare-host contract even when its path/query contains another URL.
            int end = address.length();
            for (char delimiter : new char[]{'/', '?', '#'}) {
                int index = address.indexOf(delimiter);
                if (index >= 0) end = Math.min(end, index);
            }
            address = "https://" + address.substring(0, end).trim();
        }
        String host;
        try {
            URI uri = new URI(address);
            String authority = uri.getRawAuthority();
            if (authority == null || authority.contains("@") || authority.endsWith(":")) {
                return null;
            }
            // URL's host accessor also supports Unicode authorities, unlike URI.getHost().
            // This only parses the URL; it does not open a connection.
            URL url = uri.toURL();
            if (url.getUserInfo() != null || url.getPort() < -1 || url.getPort() > 65535) {
                return null;
            }
            host = url.getHost();
        } catch (URISyntaxException | MalformedURLException exception) {
            return null;
        }
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
