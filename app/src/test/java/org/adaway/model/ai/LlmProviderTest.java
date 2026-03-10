package org.adaway.model.ai;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link LlmProvider} enum.
 *
 * <p>Covers:
 * <ul>
 *   <li>Model list invariants (non-empty, display names match model IDs)
 *   <li>Default model index validity (ATK-15: in-bounds)
 *   <li>{@link LlmProvider#getModelId(int)} clamping (ATK-15 out-of-bounds guard)
 *   <li>{@link LlmProvider#getApiKeyName()} format contract
 *   <li>{@link LlmProvider#fromOrdinal(int)} bounds checking
 *   <li>{@link LlmProvider#allDisplayNames()} and {@link LlmProvider#allOrdinalValues()} stability
 * </ul>
 */
public class LlmProviderTest {

    // -------------------------------------------------------------------------
    // Model list invariants
    // -------------------------------------------------------------------------

    @Test
    public void allProviders_haveNonEmptyModelList() {
        for (LlmProvider p : LlmProvider.values()) {
            assertNotNull("Model IDs must not be null for " + p, p.getModelIds());
            assertTrue("Model IDs must not be empty for " + p, p.getModelIds().length > 0);
        }
    }

    @Test
    public void allProviders_modelIdsAndDisplayNamesSameLength() {
        for (LlmProvider p : LlmProvider.values()) {
            assertEquals("Model ID and display-name arrays must be same length for " + p,
                    p.getModelIds().length, p.getModelDisplayNames().length);
        }
    }

    @Test
    public void allProviders_noNullOrEmptyModelId() {
        for (LlmProvider p : LlmProvider.values()) {
            for (String id : p.getModelIds()) {
                assertNotNull("Model ID must not be null in " + p, id);
                assertFalse("Model ID must not be empty in " + p, id.isEmpty());
            }
        }
    }

    @Test
    public void allProviders_noNullOrEmptyDisplayName() {
        for (LlmProvider p : LlmProvider.values()) {
            assertNotNull("Display name must not be null for " + p, p.getDisplayName());
            assertFalse("Display name must not be empty for " + p, p.getDisplayName().isEmpty());
            for (String name : p.getModelDisplayNames()) {
                assertNotNull("Model display name must not be null in " + p, name);
                assertFalse("Model display name must not be empty in " + p, name.isEmpty());
            }
        }
    }

    // -------------------------------------------------------------------------
    // ATK-15: Default model index must be in-bounds
    // -------------------------------------------------------------------------

    @Test
    public void allProviders_defaultModelIndexInBounds() {
        for (LlmProvider p : LlmProvider.values()) {
            int idx = p.getDefaultModelIndex();
            assertTrue("Default model index must be >= 0 for " + p, idx >= 0);
            assertTrue("Default model index must be < model count for " + p,
                    idx < p.getModelIds().length);
        }
    }

    // -------------------------------------------------------------------------
    // ATK-15: getModelId() must clamp out-of-bounds indices
    // -------------------------------------------------------------------------

    @Test
    public void getModelId_negativeIndex_returnsDefault() {
        for (LlmProvider p : LlmProvider.values()) {
            String defaultId = p.getModelIds()[p.getDefaultModelIndex()];
            assertEquals("Negative index must return default model for " + p,
                    defaultId, p.getModelId(-1));
        }
    }

    @Test
    public void getModelId_tooLargeIndex_returnsDefault() {
        for (LlmProvider p : LlmProvider.values()) {
            String defaultId = p.getModelIds()[p.getDefaultModelIndex()];
            assertEquals("Out-of-bounds index must return default model for " + p,
                    defaultId, p.getModelId(999));
        }
    }

    @Test
    public void getModelId_validIndex_returnsCorrectModel() {
        for (LlmProvider p : LlmProvider.values()) {
            for (int i = 0; i < p.getModelIds().length; i++) {
                assertEquals("getModelId(" + i + ") must return modelIds[" + i + "] for " + p,
                        p.getModelIds()[i], p.getModelId(i));
            }
        }
    }

    // -------------------------------------------------------------------------
    // API key name format contract
    // -------------------------------------------------------------------------

    @Test
    public void getApiKeyName_hasCorrectPrefix() {
        for (LlmProvider p : LlmProvider.values()) {
            assertTrue("API key name must start with 'ai_key_' for " + p,
                    p.getApiKeyName().startsWith("ai_key_"));
        }
    }

    @Test
    public void getApiKeyName_isLowercase() {
        for (LlmProvider p : LlmProvider.values()) {
            String keyName = p.getApiKeyName();
            assertEquals("API key name must be all lowercase for " + p,
                    keyName.toLowerCase(), keyName);
        }
    }

    @Test
    public void getApiKeyName_claudeProvider_exactValue() {
        assertEquals("Claude API key name must be 'ai_key_claude'",
                "ai_key_claude", LlmProvider.CLAUDE.getApiKeyName());
    }

    @Test
    public void getApiKeyName_geminiProvider_exactValue() {
        assertEquals("Gemini API key name must be 'ai_key_gemini'",
                "ai_key_gemini", LlmProvider.GEMINI.getApiKeyName());
    }

    @Test
    public void getApiKeyName_openaiProvider_exactValue() {
        assertEquals("OpenAI API key name must be 'ai_key_openai'",
                "ai_key_openai", LlmProvider.OPENAI.getApiKeyName());
    }

    @Test
    public void apiKeyNames_areUnique() {
        // Each provider must have a distinct key name to avoid overwriting each other
        LlmProvider[] providers = LlmProvider.values();
        for (int i = 0; i < providers.length; i++) {
            for (int j = i + 1; j < providers.length; j++) {
                assertNotEquals(
                        "Providers " + providers[i] + " and " + providers[j] + " must have distinct key names",
                        providers[i].getApiKeyName(), providers[j].getApiKeyName());
            }
        }
    }

    // -------------------------------------------------------------------------
    // fromOrdinal() bounds checking
    // -------------------------------------------------------------------------

    @Test
    public void fromOrdinal_negativeOrdinal_returnsClaude() {
        assertEquals("Negative ordinal must fall back to CLAUDE",
                LlmProvider.CLAUDE, LlmProvider.fromOrdinal(-1));
    }

    @Test
    public void fromOrdinal_tooLargeOrdinal_returnsClaude() {
        assertEquals("Out-of-bounds ordinal must fall back to CLAUDE",
                LlmProvider.CLAUDE, LlmProvider.fromOrdinal(999));
    }

    @Test
    public void fromOrdinal_validOrdinals_roundTrip() {
        for (LlmProvider p : LlmProvider.values()) {
            assertEquals("fromOrdinal(ordinal()) must round-trip for " + p,
                    p, LlmProvider.fromOrdinal(p.ordinal()));
        }
    }

    // -------------------------------------------------------------------------
    // allDisplayNames() / allOrdinalValues() stability
    // -------------------------------------------------------------------------

    @Test
    public void allDisplayNames_lengthMatchesProviderCount() {
        assertEquals("allDisplayNames() length must match provider count",
                LlmProvider.values().length, LlmProvider.allDisplayNames().length);
    }

    @Test
    public void allOrdinalValues_lengthMatchesProviderCount() {
        assertEquals("allOrdinalValues() length must match provider count",
                LlmProvider.values().length, LlmProvider.allOrdinalValues().length);
    }

    @Test
    public void allOrdinalValues_areConsistentWithOrdinals() {
        String[] ordinals = LlmProvider.allOrdinalValues();
        LlmProvider[] providers = LlmProvider.values();
        for (int i = 0; i < providers.length; i++) {
            assertEquals("Ordinal string at position " + i + " must match provider ordinal",
                    String.valueOf(providers[i].ordinal()), ordinals[i]);
        }
    }

    @Test
    public void providerCount_isThree() {
        // If a 4th provider is added, existing preferences may break — alert the developer
        assertEquals("There must be exactly 3 providers (update test if intentionally adding one)",
                3, LlmProvider.values().length);
    }
}
