package org.adaway.model.source;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Conservative compatibility gates for FilterLists.com metadata.
 *
 * AdAway can extract useful domains from several browser-oriented syntaxes, but that is not
 * equivalent to full ABP/uBO/AdGuard/dnsmasq compatibility. Bulk subscription must only import
 * formats whose published semantics map directly to DNS/root-hosts blocking.
 */
public final class FilterListCompatibility {
    private static final int SYNTAX_HOSTS = 1;
    private static final int SYNTAX_DOMAINS = 2;
    private static final int SYNTAX_NON_LOCALHOST_HOSTS = 14;

    private FilterListCompatibility() {
    }

    public static boolean isBulkSafe(@Nullable int[] syntaxIds) {
        if (syntaxIds == null || syntaxIds.length == 0) {
            return false;
        }

        boolean hasSupportedSyntax = false;
        for (int syntaxId : syntaxIds) {
            if (isExactDnsSyntax(syntaxId)) {
                hasSupportedSyntax = true;
            } else {
                return false;
            }
        }
        return hasSupportedSyntax;
    }

    public static boolean isUsableDownloadUrl(@Nullable String url) {
        if (url == null) {
            return false;
        }

        String value = url.trim();
        if (value.isEmpty() || value.indexOf(' ') >= 0) {
            return false;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://")) {
            return false;
        }
        if (lower.contains("/blob/")) {
            return false;
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".pdf")) {
            return false;
        }
        return !lower.endsWith(".zip") && !lower.endsWith(".gz") && !lower.endsWith(".7z")
                && !lower.endsWith(".tar") && !lower.endsWith(".apk");
    }

    @NonNull
    public static String describe(@Nullable int[] syntaxIds) {
        if (isBulkSafe(syntaxIds)) {
            return "DNS-safe";
        }
        if (syntaxIds == null || syntaxIds.length == 0) {
            return "Unknown syntax";
        }
        return "Domain extraction only";
    }

    @NonNull
    public static String rowSummary(@Nullable int[] syntaxIds) {
        if (isBulkSafe(syntaxIds)) {
            return "DNS-safe: exact hosts and plain domains";
        }
        if (syntaxIds == null || syntaxIds.length == 0) {
            return "Limited support: unknown syntax";
        }
        return "Limited support: browser semantics skipped";
    }

    @NonNull
    public static String capabilitySummary(@Nullable int[] syntaxIds) {
        if (isBulkSafe(syntaxIds)) {
            return "Supported: exact hosts and plain domain entries. Browser-only options, "
                    + "exceptions, cosmetics, and scriptlets are not claimed.";
        }
        if (syntaxIds == null || syntaxIds.length == 0) {
            return "Unknown syntax. AdAway will not bulk-subscribe it; a single-list toggle "
                    + "can add it after a direct download URL is found.";
        }
        return "Domain extraction only. Exceptions, redirects, path/options rules, cosmetics, "
                + "scriptlets, and unsafe-to-flatten rules are skipped.";
    }

    public static int score(@Nullable int[] syntaxIds) {
        return isBulkSafe(syntaxIds) ? 100 : 0;
    }

    @Nullable
    public static int[] decodeSyntaxIds(@Nullable String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            return null;
        }

        String[] parts = encoded.split(",");
        List<Integer> ids = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                int id = Integer.parseInt(trimmed);
                if (id <= 0) {
                    return null;
                }
                ids.add(id);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        if (ids.isEmpty()) {
            return null;
        }

        int[] result = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            result[i] = ids.get(i);
        }
        return result;
    }

    @Nullable
    public static String encodeSyntaxIds(@Nullable int[] syntaxIds) {
        if (syntaxIds == null || syntaxIds.length == 0) {
            return null;
        }

        StringBuilder encoded = new StringBuilder();
        for (int syntaxId : syntaxIds) {
            if (encoded.length() > 0) {
                encoded.append(',');
            }
            encoded.append(syntaxId);
        }
        return encoded.toString();
    }

    private static boolean isExactDnsSyntax(int syntaxId) {
        return syntaxId == SYNTAX_HOSTS
                || syntaxId == SYNTAX_DOMAINS
                || syntaxId == SYNTAX_NON_LOCALHOST_HOSTS;
    }
}
