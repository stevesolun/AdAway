package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class FilterSetStoreTest {
    @Test
    public void normalizePresetProfileKeepsKnownProfilesOnly() {
        assertEquals(FilterSetStore.PROFILE_SAFE,
                FilterSetStore.normalizePresetProfile(" Safe "));
        assertEquals(FilterSetStore.PROFILE_BALANCED,
                FilterSetStore.normalizePresetProfile("BALANCED"));
        assertEquals(FilterSetStore.PROFILE_AGGRESSIVE,
                FilterSetStore.normalizePresetProfile("aggressive"));
        assertEquals(FilterSetStore.PROFILE_CUSTOM,
                FilterSetStore.normalizePresetProfile("family"));
        assertEquals(FilterSetStore.PROFILE_CUSTOM,
                FilterSetStore.normalizePresetProfile(null));
    }

    @Test
    public void normalizeActiveProfilePreservesSavedSetIdentity() {
        assertEquals("Travel Wi-Fi",
                FilterSetStore.normalizeActiveProfile(" Travel Wi-Fi "));
        assertEquals("Family Plus",
                FilterSetStore.normalizeActiveProfile("Family Plus"));
        assertEquals("Family Pack",
                FilterSetStore.normalizeActiveProfile("Family   Pack"));
        assertEquals(FilterSetStore.PROFILE_CUSTOM,
                FilterSetStore.normalizeActiveProfile(" "));
        assertEquals(FilterSetStore.PROFILE_CUSTOM,
                FilterSetStore.normalizeActiveProfile(null));
    }

    @Test
    public void canonicalSetNameCollapsesCaseWhitespaceAndReservedNames() {
        assertEquals("family pack",
                FilterSetStore.canonicalSetName("  Family   Pack  "));
        assertEquals("safe",
                FilterSetStore.canonicalSetName("SAFE"));

        assertTrue(FilterSetStore.isReservedSetName("custom"));
        assertTrue(FilterSetStore.isReservedSetName(" Safe "));
        assertTrue(FilterSetStore.isReservedSetName("Safe Mode"));
        assertTrue(FilterSetStore.isReservedSetName("balanced mode"));
        assertTrue(FilterSetStore.isReservedSetName("AGGRESSIVE   MODE"));
        assertTrue(FilterSetStore.isReservedSetName("current selection"));
        assertFalse(FilterSetStore.isReservedSetName("Family Pack"));
    }

    @Test
    public void canonicalSetNameDetectsDuplicateDisplayNames() {
        Set<String> existing = new HashSet<>();
        existing.add("Family Pack");
        existing.add("Work");

        assertTrue(FilterSetStore.hasCanonicalSetName(existing, " family   pack "));
        assertTrue(FilterSetStore.hasCanonicalSetName(existing, "WORK"));
        assertFalse(FilterSetStore.hasCanonicalSetName(existing, "Travel"));
    }

    @Test
    public void validateSetNameClassifiesInlineSaveErrors() {
        Set<String> existing = new HashSet<>();
        existing.add("Family Pack");

        assertEquals(FilterSetStore.SetNameValidation.EMPTY,
                FilterSetStore.validateSetName("   ", existing));
        assertEquals(FilterSetStore.SetNameValidation.RESERVED,
                FilterSetStore.validateSetName("Safe Mode", existing));
        assertEquals(FilterSetStore.SetNameValidation.DUPLICATE,
                FilterSetStore.validateSetName(" family   pack ", existing));
        assertEquals(FilterSetStore.SetNameValidation.OK,
                FilterSetStore.validateSetName("Travel", existing));
    }

    @Test
    public void profileIdsAreStableOpaquePreferenceKeys() {
        assertEquals("profile_safe",
                FilterSetStore.reservedProfileId(" Safe "));
        assertEquals("profile_balanced",
                FilterSetStore.reservedProfileId("Balanced Mode"));
        assertEquals("profile_aggressive",
                FilterSetStore.reservedProfileId("AGGRESSIVE"));
        assertEquals("profile_custom",
                FilterSetStore.reservedProfileId("current selection"));

        String generated = FilterSetStore.newOpaqueProfileIdForTest("Family Pack");
        String regenerated = FilterSetStore.newOpaqueProfileIdForTest("Family Pack");
        assertTrue(generated.startsWith("profile_"));
        assertNotEquals("New saved profile ids must not be deterministic from display names.",
                generated, regenerated);
        assertFalse("Opaque profile ids must not expose the display name.",
                generated.toLowerCase().contains("family"));
        assertFalse("Opaque profile ids must not expose the display name.",
                generated.toLowerCase().contains("pack"));
        assertEquals("set_id_" + generated,
                FilterSetStore.profileValueKeyForTest("set_id_", generated));
        assertEquals("schedule_id_" + generated,
                FilterSetStore.profileValueKeyForTest("schedule_id_", generated));
    }
}
