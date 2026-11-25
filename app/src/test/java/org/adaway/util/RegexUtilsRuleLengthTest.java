package org.adaway.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegexUtilsRuleLengthTest {
    @Test
    public void wildcardRejectsNullEmptyAndOverlengthRawInput() {
        assertFalse(RegexUtils.isValidWildcardHostname(null));
        assertFalse(RegexUtils.isValidWildcardHostname(""));
        assertFalse(RegexUtils.isValidWildcardHostname("example.com" + stars(244)));
        assertFalse(RegexUtils.isValidWildcardHostname("example.com" + stars(243)));
    }

    @Test
    public void wildcardAcceptsRawLengthBoundaryAndNormalPatterns() {
        assertTrue(RegexUtils.isValidWildcardHostname("example.com" + stars(242)));
        assertTrue(RegexUtils.isValidWildcardHostname("*.example.com"));
        assertTrue(RegexUtils.isValidWildcardHostname("ad?.example.com"));
        assertTrue(RegexUtils.isValidWildcardHostname("EXAMPLE.COM"));
    }

    @Test
    public void wildcardStillRejectsMalformedShortPatterns() {
        assertFalse(RegexUtils.isValidWildcardHostname("*"));
        assertFalse(RegexUtils.isValidWildcardHostname("a..example.com"));
        assertFalse(RegexUtils.isValidWildcardHostname("https://example.com"));
    }

    @Test
    public void exactHostnameKeepsItsOwnLengthAndWildcardRules() {
        String label = repeat('a', 63);
        String maxHost = label + "." + label + "." + label + "." + repeat('b', 61);
        assertTrue(RegexUtils.isValidHostname(maxHost));
        assertFalse(RegexUtils.isValidHostname(maxHost + "b"));
        assertFalse(RegexUtils.isValidHostname("*.example.com"));
    }

    private static String stars(int count) {
        return repeat('*', count);
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }
}
