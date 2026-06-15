package org.adaway.util;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Hostname key helpers shared by Room entities and direct SQL import paths.
 */
public final class Hostnames {
    private Hostnames() {
    }

    @NonNull
    public static String normalize(@NonNull String host) {
        return isLowercaseAscii(host) ? host : host.toLowerCase(Locale.ROOT);
    }

    @NonNull
    public static String reverseLabels(@NonNull String host) {
        String normalized = normalize(host);
        StringBuilder builder = new StringBuilder(normalized.length());
        int labelEnd = normalized.length();
        while (labelEnd > 0) {
            int dot = normalized.lastIndexOf('.', labelEnd - 1);
            int labelStart = dot + 1;
            if (labelStart < labelEnd) {
                if (builder.length() > 0) {
                    builder.append('.');
                }
                builder.append(normalized, labelStart, labelEnd);
            }
            labelEnd = dot;
        }
        return builder.toString();
    }

    private static boolean isLowercaseAscii(@NonNull String host) {
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return false;
            }
        }
        return true;
    }
}
