/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.util;

import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

public class RegexUtils {
    private static final Pattern WILDCARD_PATTERN = Pattern.compile("[*?]");

    /**
     * Check whether a hostname is valid.
     * Uses a fast-path check for common valid hostnames before falling back to Guava's
     * InternetDomainName.isValid() for edge cases. This significantly improves performance
     * when validating millions of hostnames during bulk parsing.
     *
     * @param hostname The hostname to validate.
     * @return return {@code true} if hostname is valid, {@code false} otherwise.
     */
    public static boolean isValidHostname(String hostname) {
        // Fast-path: reject obviously invalid hostnames before expensive Guava call
        if (hostname == null || hostname.isEmpty()) {
            return false;
        }

        int len = hostname.length();
        // Max DNS hostname length is 253 characters
        if (len > 253) {
            return false;
        }

        // Fast-path validation: check for valid DNS hostname characters
        // Valid: a-z, A-Z, 0-9, hyphen (-), dot (.)
        // Hostname cannot start or end with hyphen or dot
        char first = hostname.charAt(0);
        char last = hostname.charAt(len - 1);
        if (first == '-' || first == '.' || last == '-' || last == '.') {
            return false;
        }

        boolean hasLetter = false;
        boolean prevWasDot = false;
        int labelLength = 0;

        for (int i = 0; i < len; i++) {
            char c = hostname.charAt(i);
            if (c == '.') {
                // Empty label (double dot) is invalid
                if (prevWasDot || labelLength == 0) {
                    return false;
                }
                // Label max length is 63 characters
                if (labelLength > 63) {
                    return false;
                }
                labelLength = 0;
                prevWasDot = true;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                hasLetter = true;
                labelLength++;
                prevWasDot = false;
            } else if ((c >= '0' && c <= '9') || c == '-') {
                labelLength++;
                prevWasDot = false;
            } else {
                // Invalid character - fall back to Guava for IDN/punycode
                return InternetDomainName.isValid(hostname);
            }
        }

        // Last label length check
        if (labelLength > 63) {
            return false;
        }

        // Must have at least one dot (TLD required) and contain at least one letter
        if (!hostname.contains(".") || !hasLetter) {
            return false;
        }

        return true;
    }

    /**
     * Check whether a wildcard hostname is valid.
     * Wildcard hostname is an hostname with one of the following wildcard:
     * <ul>
     * <li>{@code *} for any character sequence,</li>
     * <li>{@code ?} for any character</li>
     * </ul>
     * <p/>
     * Wildcard validation is quite tricky, because wildcards can be placed anywhere and can match with
     * anything. To make sure we don't dismiss certain valid wildcard host names, we trim wildcards
     * or replace them with an alphanumeric character for further validation.<br/>
     * We only reject whitelist host names which cannot match against valid host names under any circumstances.
     *
     * @param hostname The wildcard hostname to validate.
     * @return return {@code true} if wildcard hostname is valid, {@code false} otherwise.
     */
    public static boolean isValidWildcardHostname(String hostname) {
        // Clear wildcards from host name then validate it
        Matcher matcher = WILDCARD_PATTERN.matcher(hostname);
        String clearedHostname = matcher.replaceAll("");
        // Replace wildcards from host name by an alphanumeric character
        String replacedHostname = matcher.replaceAll("a");
        // Check if any hostname is valid
        return isValidHostname(clearedHostname) || isValidHostname(replacedHostname);
    }

    /**
     * Check if an IP address is valid.
     *
     * @param ip The IP to validate.
     * @return {@code true} if the IP is valid, {@code false} otherwise.
     */
    public static boolean isValidIP(String ip) {
        try {
            InetAddresses.forString(ip);
            return true;
        } catch (IllegalArgumentException exception) {
            Timber.d(exception, "Invalid IP address: %s.", ip);
            return false;
        }
    }

    /*
     * Transforms String with * and ? characters to regex String, convert "example*.*" to regex
     * "^example.*\\..*$", from http://www.rgagnon.com/javadetails/java-0515.html
     */
    public static String wildcardToRegex(String wildcard) {
        StringBuilder regex = new StringBuilder(wildcard.length());
        regex.append('^');
        for (int i = 0, is = wildcard.length(); i < is; i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
                    break;
                // escape special regex-characters
                case '(':
                case ')':
                case '[':
                case ']':
                case '$':
                case '^':
                case '.':
                case '{':
                case '}':
                case '|':
                case '\\':
                    regex.append("\\");
                    regex.append(c);
                    break;
                default:
                    regex.append(c);
                    break;
            }
        }
        regex.append('$');
        return regex.toString();
    }
}
