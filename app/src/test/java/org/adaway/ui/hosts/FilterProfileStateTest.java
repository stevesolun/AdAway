package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FilterProfileStateTest {
    @Test
    public void resolveClassifiesProfileRelationship() {
        Set<String> profile = setOf("https://a.test/hosts", "https://b.test/hosts");

        assertEquals(FilterProfileState.NONE,
                FilterProfileState.resolve(Collections.emptySet(), profile));
        assertEquals(FilterProfileState.EXACT,
                FilterProfileState.resolve(profile, setOf("https://a.test/hosts",
                        "https://b.test/hosts")));
        assertEquals(FilterProfileState.EXTENDED,
                FilterProfileState.resolve(profile, setOf("https://a.test/hosts",
                        "https://b.test/hosts", "https://custom.test/hosts")));
        assertEquals(FilterProfileState.PARTIAL,
                FilterProfileState.resolve(profile, setOf("https://a.test/hosts")));
        assertEquals(FilterProfileState.PARTIAL,
                FilterProfileState.resolve(profile, Collections.emptySet()));
    }

    @Test
    public void diffCountsProfileApplyImpact() {
        Set<String> currentEnabled = setOf("https://a.test/hosts", "https://b.test/hosts");
        Set<String> targetProfile = setOf("https://b.test/hosts", "https://c.test/hosts",
                "https://missing.test/hosts");
        Set<String> available = setOf("https://a.test/hosts", "https://b.test/hosts",
                "https://c.test/hosts", "https://d.test/hosts");

        FilterProfileDiff diff = FilterProfileDiff.resolve(
                currentEnabled, targetProfile, available);

        assertEquals(1, diff.getEnableCount());
        assertEquals(1, diff.getDisableCount());
        assertEquals(1, diff.getKeepEnabledCount());
        assertEquals(1, diff.getMissingCount());
        assertTrue(diff.hasChanges());
        assertTrue(diff.weakensProtection());
    }

    @Test
    public void diffTreatsExactProfileAsNoChange() {
        Set<String> currentEnabled = setOf("https://a.test/hosts", "https://b.test/hosts");

        FilterProfileDiff diff = FilterProfileDiff.resolve(
                currentEnabled, currentEnabled, currentEnabled);

        assertEquals(0, diff.getEnableCount());
        assertEquals(0, diff.getDisableCount());
        assertEquals(2, diff.getKeepEnabledCount());
        assertEquals(0, diff.getMissingCount());
        assertFalse(diff.hasChanges());
        assertFalse(diff.weakensProtection());
    }

    @Test
    public void diffMarksEmptyProfileAsDisableAllRisk() {
        Set<String> currentEnabled = setOf("https://a.test/hosts", "https://b.test/hosts");
        Set<String> available = setOf("https://a.test/hosts", "https://b.test/hosts",
                "https://c.test/hosts");

        FilterProfileDiff diff = FilterProfileDiff.resolve(
                currentEnabled, Collections.emptySet(), available);

        assertEquals(0, diff.getEnableCount());
        assertEquals(2, diff.getDisableCount());
        assertEquals(0, diff.getKeepEnabledCount());
        assertEquals(0, diff.getMissingCount());
        assertTrue(diff.isEmptyProfile());
        assertTrue(diff.disablesAllEnabledSources());
    }

    @Test
    public void diffMarksMissingOnlyProfileAsPartialNoLocalChange() {
        Set<String> currentEnabled = setOf("https://a.test/hosts");
        Set<String> targetProfile = setOf("https://missing.test/hosts");
        Set<String> available = setOf("https://a.test/hosts", "https://b.test/hosts");

        FilterProfileDiff diff = FilterProfileDiff.resolve(
                currentEnabled, targetProfile, available);

        assertEquals(0, diff.getEnableCount());
        assertEquals(1, diff.getDisableCount());
        assertEquals(0, diff.getKeepEnabledCount());
        assertEquals(1, diff.getMissingCount());
        assertFalse(diff.isEmptyProfile());
        assertTrue(diff.isMissingOnlyProfile());
        assertTrue(diff.disablesAllEnabledSources());
    }

    private static Set<String> setOf(String... values) {
        Set<String> result = new HashSet<>();
        Collections.addAll(result, values);
        return result;
    }
}
