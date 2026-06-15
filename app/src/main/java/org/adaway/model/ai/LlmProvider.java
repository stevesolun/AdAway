package org.adaway.model.ai;

import java.util.Locale;

/**
 * Supported LLM providers and their available models.
 * Each provider is accessible via raw HTTP with OkHttp — no SDK dependencies.
 */
public enum LlmProvider {

    CLAUDE(
            "Claude (Anthropic)",
            new String[]{
                    "claude-haiku-4-5-20251001",
                    "claude-sonnet-4-6",
                    "claude-opus-4-6"
            },
            new String[]{
                    "Haiku 4.5 (fast, cheap)",
                    "Sonnet 4.6 (balanced)",
                    "Opus 4.6 (best quality)"
            },
            0 /* default index */
    ),

    GEMINI(
            "Gemini (Google)",
            new String[]{
                    "gemini-2.5-flash",
                    "gemini-2.5-pro",
                    "gemini-2.5-flash-lite"
            },
            new String[]{
                    "Flash (balanced)",
                    "Pro (best quality)",
                    "Flash Lite (fast, cheap)"
            },
            0
    ),

    OPENAI(
            "ChatGPT (OpenAI)",
            new String[]{
                    "gpt-4.1-mini",
                    "gpt-4.1",
                    "gpt-4.1-nano"
            },
            new String[]{
                    "GPT-4.1 Mini (balanced)",
                    "GPT-4.1 (best quality)",
                    "GPT-4.1 Nano (fast, cheap)"
            },
            0
    );

    private final String displayName;
    private final String[] modelIds;
    private final String[] modelDisplayNames;
    private final int defaultModelIndex;

    LlmProvider(String displayName, String[] modelIds,
                String[] modelDisplayNames, int defaultModelIndex) {
        this.displayName = displayName;
        this.modelIds = modelIds;
        this.modelDisplayNames = modelDisplayNames;
        this.defaultModelIndex = defaultModelIndex;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String[] getModelIds() {
        return modelIds;
    }

    public String[] getModelDisplayNames() {
        return modelDisplayNames;
    }

    public int getDefaultModelIndex() {
        return defaultModelIndex;
    }

    public String getModelId(int index) {
        if (index < 0 || index >= modelIds.length) return modelIds[defaultModelIndex];
        return modelIds[index];
    }

    /** Preference key for storing this provider's API key in SecureApiKeyStore. */
    public String getApiKeyName() {
        return "ai_key_" + name().toLowerCase(Locale.ROOT);
    }

    /** Ordered list of display names for all providers (for ListPreference). */
    public static String[] allDisplayNames() {
        LlmProvider[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }

    /** Ordered list of ordinal strings for all providers (for ListPreference entryValues). */
    public static String[] allOrdinalValues() {
        LlmProvider[] values = values();
        String[] ordinals = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            ordinals[i] = String.valueOf(values[i].ordinal());
        }
        return ordinals;
    }

    public static LlmProvider fromOrdinal(int ordinal) {
        LlmProvider[] values = values();
        if (ordinal < 0 || ordinal >= values.length) return CLAUDE;
        return values[ordinal];
    }
}
