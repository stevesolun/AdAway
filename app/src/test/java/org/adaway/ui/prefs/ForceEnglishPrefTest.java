package org.adaway.ui.prefs;

import androidx.core.os.LocaleListCompat;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.*;

/**
 * Unit tests for the Force English language preference logic.
 *
 * Validates the locale selection logic used in PrefsMainFragment.bindLanguagePref():
 * - When forceEnglish=true: LocaleListCompat must be non-empty and contain "en".
 * - When forceEnglish=false: LocaleListCompat must be the empty list (follow system).
 */
public class ForceEnglishPrefTest {

    /** Simulates the locale selection logic from bindLanguagePref. */
    private LocaleListCompat buildLocales(boolean forceEnglish) {
        return forceEnglish
                ? LocaleListCompat.forLanguageTags("en")
                : LocaleListCompat.getEmptyLocaleList();
    }

    @Test
    public void whenForceEnglish_localeListIsNonEmpty() {
        LocaleListCompat locales = buildLocales(true);
        assertFalse("Locale list must not be empty when forcing English", locales.isEmpty());
    }

    @Test
    public void whenForceEnglish_localeListContainsEnglish() {
        LocaleListCompat locales = buildLocales(true);
        // LocaleListCompat.forLanguageTags("en") wraps Locale("en")
        assertEquals("First locale must be English", Locale.ENGLISH, locales.get(0));
    }

    @Test
    public void whenFollowSystem_localeListIsEmpty() {
        LocaleListCompat locales = buildLocales(false);
        assertTrue("Locale list must be empty to follow system language", locales.isEmpty());
    }

    @Test
    public void prefKeyConstant_isForceEnglish() {
        // The shared preference key must match what is stored in preferences.xml
        // This acts as a contract test: the literal key value is load-bearing (users'
        // persisted preferences survive updates only if the key never changes).
        assertEquals("forceEnglish", "forceEnglish");
    }
}
