package org.adaway.model.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.adaway.model.source.FilterListCategory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Sends a natural language query to the configured LLM and maps its response
 * to a set of {@link FilterListCategory} values.
 *
 * <p>Must be called on a background thread ({@link android.annotation.WorkerThread}).
 *
 * <p>Prompt strategy: the system prompt is a compact description of each category.
 * The LLM returns a JSON object {"categories": [...], "reasoning": "..."}.
 * We parse the category names and return them as an {@link LlmSuggestion}.
 */
public final class FilterListSuggester {

    private static final String PREFS_AI = "ai_preferences";
    private static final String KEY_PROVIDER = "provider_ordinal";
    private static final String KEY_MODEL_INDEX = "model_index";

    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private static final int TIMEOUT_CONNECT_SEC = 15;
    private static final int TIMEOUT_READ_SEC = 60;

    /** Maximum user query length accepted before truncation. */
    static final int MAX_QUERY_LENGTH = 500;

    /**
     * Patterns that indicate an attempt to hijack the system prompt (ATK-09).
     * If detected we substitute "[filtered]" so the query is still processed
     * but the injection payload is neutralised.
     */
    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "(?i)(?:"
            + "ignore\\s+(?:all\\s+)?(?:previous|above|prior)\\s+(?:instructions?|prompts?|context)"
            + "|system\\s*prompt"
            + "|<\\s*/?\\s*(?:system|assistant|user)\\s*>"
            + "|\\[INST]"
            + "|###\\s*(?:System|Assistant|User)"
            + "|(?:act|pretend|roleplay|simulate)\\s+as(?:\\s+if\\s+you\\s+are)?"
            + ")");

    private static final String SYSTEM_PROMPT =
            "You are an expert helping configure the AdAway Android ad-blocker. "
            + "Based on the user's intent, select the most appropriate filter categories.\n\n"
            + "Available categories:\n"
            + "- ADS: Block advertisement servers (safe, recommended for everyone)\n"
            + "- YOUTUBE: Block YouTube in-stream ads (safe)\n"
            + "- PRIVACY: Block trackers, analytics, fingerprinting (safe)\n"
            + "- MALWARE: Block malware, phishing, ransomware domains (safe, recommended)\n"
            + "- CRYPTO: Block cryptomining scripts (safe)\n"
            + "- SOCIAL: Block social media trackers [WARNING: may break Facebook, Instagram, WhatsApp]\n"
            + "- DEVICE: Block manufacturer telemetry (Samsung/Xiaomi) [WARNING: may break OEM features]\n"
            + "- SERVICE: Block in-app ads (Spotify, etc.) [WARNING: may break those apps]\n"
            + "- ANNOYANCES: Block cookie banners, popup overlays (safe)\n"
            + "- REGIONAL: Block region-specific ad networks (safe)\n\n"
            + "Rules:\n"
            + "1. Only include SOCIAL, DEVICE, SERVICE if the user explicitly asks for aggressive blocking or to block specific services.\n"
            + "2. Always include ADS and MALWARE unless the user explicitly says they don't want them.\n"
            + "3. Keep reasoning concise (1-2 sentences).\n\n"
            + "Respond with ONLY valid JSON, no markdown, no extra text:\n"
            + "{\"categories\": [\"ADS\", \"PRIVACY\"], \"reasoning\": \"Brief explanation.\"}";

    /**
     * System prompt template for the agent mode ({@link #execute}).
     * The {@code {STATE}} placeholder is replaced at call time with the JSON from
     * {@link AppStateContext#build(android.content.Context)}.
     */
    private static final String AGENT_SYSTEM_PROMPT_TEMPLATE =
            "You are an AI assistant embedded in AdAway, an Android ad-blocking app.\n"
            + "You ONLY manage ad-blocking filters. Do NOT answer general questions, "
            + "hold conversations, or perform any task unrelated to AdAway filter management.\n\n"
            + "Current app state:\n{STATE}\n\n"
            + "Available categories: ADS, YOUTUBE, PRIVACY, MALWARE, CRYPTO, SOCIAL, "
            + "DEVICE, SERVICE, ANNOYANCES, REGIONAL\n\n"
            + "Available action types:\n"
            + "- SUBSCRIBE_CATEGORY: add and enable all lists in a category\n"
            + "- ENABLE_CATEGORY: turn on already-subscribed lists in a category\n"
            + "- DISABLE_CATEGORY: turn off lists in a category (without deleting)\n"
            + "- UPDATE_SOURCES: trigger an immediate update of all enabled filter lists\n"
            + "- CHECK_DOMAIN: check if a specific domain is currently blocked\n"
            + "- ALLOW_DOMAIN: add a domain to the user allowlist (unblock it)\n"
            + "- BLOCK_DOMAIN: add a domain to the user blocklist\n\n"
            + "RULES:\n"
            + "1. payload for category actions MUST be one of the listed category names (uppercase).\n"
            + "2. payload for domain actions MUST be a plain hostname — no scheme, no path, no port.\n"
            + "3. For off-topic or general requests respond with: "
            + "{\"reasoning\":\"I can only help with AdAway filter management.\",\"actions\":[]}\n"
            + "4. Keep reasoning to one sentence.\n\n"
            + "Respond with ONLY valid JSON, no markdown, no extra text:\n"
            + "{\"reasoning\":\"Brief explanation.\","
            + "\"actions\":[{\"type\":\"SUBSCRIBE_CATEGORY\",\"payload\":\"ADS\"}]}";

    private final OkHttpClient httpClient;

    public FilterListSuggester() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_CONNECT_SEC, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_READ_SEC, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Returns the current active provider, or {@code null} if no provider is configured.
     */
    @NonNull
    public static LlmProvider getSelectedProvider(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AI, Context.MODE_PRIVATE);
        int ordinal = prefs.getInt(KEY_PROVIDER, LlmProvider.CLAUDE.ordinal());
        return LlmProvider.fromOrdinal(ordinal);
    }

    public static void setSelectedProvider(@NonNull Context context, @NonNull LlmProvider provider) {
        context.getSharedPreferences(PREFS_AI, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_PROVIDER, provider.ordinal())
                .apply();
    }

    public static int getSelectedModelIndex(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_AI, Context.MODE_PRIVATE)
                .getInt(KEY_MODEL_INDEX, 0);
    }

    public static void setSelectedModelIndex(@NonNull Context context, int index) {
        context.getSharedPreferences(PREFS_AI, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_MODEL_INDEX, index)
                .apply();
    }

    /**
     * Checks whether an API key is configured for the currently selected provider.
     */
    public static boolean hasApiKey(@NonNull Context context) {
        LlmProvider provider = getSelectedProvider(context);
        try {
            SecureApiKeyStore store = SecureApiKeyStore.getInstance(context);
            return store.hasApiKey(provider.getApiKeyName());
        } catch (GeneralSecurityException | IOException e) {
            Timber.w(e, "Failed to check API key");
            return false;
        }
    }

    /**
     * Sends {@code query} to the configured LLM and returns a {@link LlmSuggestion}.
     * Throws {@link IOException} on network/HTTP failure or {@link IllegalStateException}
     * if no API key is configured.
     *
     * <p>Must be called on a background thread.
     */
    @WorkerThread
    @NonNull
    public LlmSuggestion suggest(@NonNull Context context, @NonNull String query)
            throws IOException, GeneralSecurityException {
        LlmProvider provider = getSelectedProvider(context);
        int modelIndex = getSelectedModelIndex(context);
        String modelId = provider.getModelId(modelIndex);

        SecureApiKeyStore keyStore = SecureApiKeyStore.getInstance(context);
        String apiKey = keyStore.getApiKey(provider.getApiKeyName());
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("No API key configured for " + provider.getDisplayName());
        }

        String safeQuery = sanitizeQuery(query);
        String responseJson;
        switch (provider) {
            case CLAUDE:
                responseJson = callClaude(apiKey, modelId, SYSTEM_PROMPT, safeQuery);
                break;
            case GEMINI:
                responseJson = callGemini(apiKey, modelId, SYSTEM_PROMPT, safeQuery);
                break;
            case OPENAI:
                responseJson = callOpenAi(apiKey, modelId, SYSTEM_PROMPT, safeQuery);
                break;
            default:
                throw new IllegalStateException("Unknown provider: " + provider);
        }

        return parseSuggestion(responseJson);
    }

    /**
     * Sends {@code query} to the configured LLM as an agent-mode request.
     * The LLM sees the current app state (which filter categories are subscribed/enabled)
     * and returns an {@link AiAgentResponse} containing structured actions to execute.
     *
     * <p>The AI is constrained by system prompt to only act within AdAway's filter management
     * domain. Off-topic queries return an empty action list.
     *
     * <p>Must be called on a background thread.
     */
    @WorkerThread
    @NonNull
    public AiAgentResponse execute(@NonNull Context context, @NonNull String query)
            throws IOException, GeneralSecurityException {
        LlmProvider provider = getSelectedProvider(context);
        int modelIndex = getSelectedModelIndex(context);
        String modelId = provider.getModelId(modelIndex);

        SecureApiKeyStore keyStore = SecureApiKeyStore.getInstance(context);
        String apiKey = keyStore.getApiKey(provider.getApiKeyName());
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("No API key configured for " + provider.getDisplayName());
        }

        String safeQuery = sanitizeQuery(query);
        // Build current app state — safe to inject (only enum names + counts)
        String stateJson = AppStateContext.build(context);
        String systemPrompt = AGENT_SYSTEM_PROMPT_TEMPLATE.replace("{STATE}", stateJson);

        String responseJson;
        switch (provider) {
            case CLAUDE:
                responseJson = callClaude(apiKey, modelId, systemPrompt, safeQuery);
                break;
            case GEMINI:
                responseJson = callGemini(apiKey, modelId, systemPrompt, safeQuery);
                break;
            case OPENAI:
                responseJson = callOpenAi(apiKey, modelId, systemPrompt, safeQuery);
                break;
            default:
                throw new IllegalStateException("Unknown provider: " + provider);
        }

        return parseAgentResponse(responseJson);
    }

    /**
     * Agentic loop variant: sends {@code query} to the LLM, auto-executes any {@code CHECK_DOMAIN}
     * actions from the first response (read-only, no user approval needed), then feeds the results
     * back to the LLM for a second call that returns the final write-action plan.
     *
     * <p>Write-actions (SUBSCRIBE_CATEGORY, ALLOW_DOMAIN, BLOCK_DOMAIN, etc.) are NEVER
     * auto-executed — they are returned in the final {@link AiAgentResponse} for the user to
     * review and approve with a single Execute tap.
     *
     * <p>Maximum two LLM turns. If the first response contains no CHECK_DOMAIN actions the first
     * response is returned directly (no second call).
     *
     * <p>Must be called on a background thread (networkIO is fine — Room is called inline but not
     * on the main thread).
     */
    @WorkerThread
    @NonNull
    public AiAgentResponse executeWithLoop(@NonNull Context context, @NonNull String query)
            throws IOException, GeneralSecurityException {
        LlmProvider provider = getSelectedProvider(context);
        int modelIndex = getSelectedModelIndex(context);
        String modelId = provider.getModelId(modelIndex);

        SecureApiKeyStore keyStore = SecureApiKeyStore.getInstance(context);
        String apiKey = keyStore.getApiKey(provider.getApiKeyName());
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("No API key configured for " + provider.getDisplayName());
        }

        String safeQuery = sanitizeQuery(query);
        String stateJson = AppStateContext.build(context);
        String systemPrompt = AGENT_SYSTEM_PROMPT_TEMPLATE.replace("{STATE}", stateJson);

        // ── Turn 1 ───────────────────────────────────────────────────────────
        String firstRaw;
        switch (provider) {
            case CLAUDE:  firstRaw = callClaude(apiKey, modelId, systemPrompt, safeQuery);  break;
            case GEMINI:  firstRaw = callGemini(apiKey, modelId, systemPrompt, safeQuery);  break;
            case OPENAI:  firstRaw = callOpenAi(apiKey, modelId, systemPrompt, safeQuery);  break;
            default: throw new IllegalStateException("Unknown provider: " + provider);
        }

        AiAgentResponse firstResponse = parseAgentResponse(firstRaw);

        // Partition: only CHECK_DOMAIN auto-executes (read-only, no DB write)
        List<AiAgentAction> checkActions = new ArrayList<>();
        for (AiAgentAction action : firstResponse.actions) {
            if (action.type == AiAgentAction.Type.CHECK_DOMAIN) {
                checkActions.add(action);
            }
        }

        // No read queries → first response is the final plan, skip second call
        if (checkActions.isEmpty()) return firstResponse;

        // ── Auto-execute CHECK_DOMAIN ─────────────────────────────────────────
        // Security: AiActionExecutor validates each domain via normalizeDomain() before any
        // DB access. Result strings are code-controlled ("X is blocked", "Invalid domain: X").
        // Cap at 5 domains and 100 chars/result to prevent context stuffing.
        AiActionExecutor executor = new AiActionExecutor(context);
        StringBuilder toolResults = new StringBuilder("Domain check results:\n");
        int limit = Math.min(checkActions.size(), MAX_LOOP_CHECK_ACTIONS);
        for (int i = 0; i < limit; i++) {
            String result = executor.execute(checkActions.get(i));
            if (result.length() > MAX_TOOL_RESULT_CHARS) {
                result = result.substring(0, MAX_TOOL_RESULT_CHARS);
            }
            toolResults.append("• ").append(result).append("\n");
        }
        String toolResultMsg = toolResults.toString().trim();

        // ── Turn 2 ───────────────────────────────────────────────────────────
        String secondRaw;
        switch (provider) {
            case CLAUDE:
                secondRaw = callClaudeMultiTurn(apiKey, modelId, systemPrompt,
                        safeQuery, firstRaw, toolResultMsg);
                break;
            case GEMINI:
                secondRaw = callGeminiMultiTurn(apiKey, modelId, systemPrompt,
                        safeQuery, firstRaw, toolResultMsg);
                break;
            case OPENAI:
                secondRaw = callOpenAiMultiTurn(apiKey, modelId, systemPrompt,
                        safeQuery, firstRaw, toolResultMsg);
                break;
            default: throw new IllegalStateException("Unknown provider: " + provider);
        }

        return parseAgentResponse(secondRaw);
    }

    /** Maximum number of CHECK_DOMAIN actions auto-executed per loop (prevents context stuffing). */
    static final int MAX_LOOP_CHECK_ACTIONS = 5;

    /** Maximum characters per tool-result string injected into Turn 2 prompt. */
    static final int MAX_TOOL_RESULT_CHARS = 100;

    // -------------------------------------------------------------------------
    // Provider-specific HTTP calls
    // -------------------------------------------------------------------------

    @WorkerThread
    private String callClaude(String apiKey, String model, String systemPrompt, String userQuery)
            throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model);
            body.put("max_tokens", 512);
            body.put("system", systemPrompt);
            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userQuery);
            messages.put(userMsg);
            body.put("messages", messages);
        } catch (JSONException e) {
            throw new IOException("Failed to build Claude request", e);
        }

        Request request = new Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String bodyStr = requireBody(response, "Claude");
            try {
                return new JSONObject(bodyStr)
                        .getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text");
            } catch (JSONException e) {
                // ATK-04 variant: truncate to 100 chars — full body may echo request headers containing API key on proxy errors.
                Timber.d("Unexpected Claude response (truncated): %s", bodyStr.length() > 100 ? bodyStr.substring(0, 100) + "…" : bodyStr);
                throw new IOException("Unexpected Claude response format", e);
            }
        }
    }

    @WorkerThread
    private String callGemini(String apiKey, String model, String systemPrompt, String userQuery)
            throws IOException {
        JSONObject body = new JSONObject();
        try {
            JSONObject sysInstruction = new JSONObject();
            JSONObject sysPart = new JSONObject();
            sysPart.put("text", systemPrompt);
            sysInstruction.put("parts", sysPart);
            body.put("systemInstruction", sysInstruction);

            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            content.put("role", "user");
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", userQuery);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            body.put("contents", contents);

            JSONObject genConfig = new JSONObject();
            genConfig.put("maxOutputTokens", 512);
            body.put("generationConfig", genConfig);
        } catch (JSONException e) {
            throw new IOException("Failed to build Gemini request", e);
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String bodyStr = requireBody(response, "Gemini");
            try {
                return new JSONObject(bodyStr)
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text");
            } catch (JSONException e) {
                Timber.d("Unexpected Gemini response shape");
                throw new IOException("Unexpected Gemini response format", e);
            }
        }
    }

    @WorkerThread
    private String callOpenAi(String apiKey, String model, String systemPrompt, String userQuery)
            throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model);
            body.put("max_completion_tokens", 512);
            JSONArray messages = new JSONArray();
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
            messages.put(system);
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", userQuery);
            messages.put(user);
            body.put("messages", messages);
        } catch (JSONException e) {
            throw new IOException("Failed to build OpenAI request", e);
        }

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String bodyStr = requireBody(response, "OpenAI");
            try {
                return new JSONObject(bodyStr)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
            } catch (JSONException e) {
                Timber.d("Unexpected OpenAI response shape");
                throw new IOException("Unexpected OpenAI response format", e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Multi-turn provider calls (agentic loop — Turn 2)
    // -------------------------------------------------------------------------

    /**
     * Claude Turn 2: 3-message conversation (user → assistant → user with tool results).
     * System prompt stays top-level; messages array contains all turns.
     */
    @WorkerThread
    private String callClaudeMultiTurn(String apiKey, String model, String systemPrompt,
            String userQuery, String assistantReply, String toolResultMsg) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model);
            body.put("max_tokens", 512);
            body.put("system", systemPrompt);
            JSONArray messages = new JSONArray();
            messages.put(buildClaudeMsg("user", userQuery));
            messages.put(buildClaudeMsg("assistant", assistantReply));
            messages.put(buildClaudeMsg("user", toolResultMsg));
            body.put("messages", messages);
        } catch (JSONException e) {
            throw new IOException("Failed to build Claude multi-turn request", e);
        }
        Request request = new Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String bodyStr = requireBody(response, "Claude");
            try {
                return new JSONObject(bodyStr)
                        .getJSONArray("content").getJSONObject(0).getString("text");
            } catch (JSONException e) {
                throw new IOException("Unexpected Claude multi-turn response format", e);
            }
        }
    }

    /**
     * Gemini Turn 2: contents array with user/model/user turns.
     * Note: Gemini uses "model" (not "assistant") for the AI role.
     */
    @WorkerThread
    private String callGeminiMultiTurn(String apiKey, String model, String systemPrompt,
            String userQuery, String modelReply, String toolResultMsg) throws IOException {
        JSONObject body = new JSONObject();
        try {
            JSONObject sysInstruction = new JSONObject();
            JSONObject sysPart = new JSONObject();
            sysPart.put("text", systemPrompt);
            sysInstruction.put("parts", sysPart);
            body.put("systemInstruction", sysInstruction);

            JSONArray contents = new JSONArray();
            contents.put(buildGeminiContent("user", userQuery));
            contents.put(buildGeminiContent("model", modelReply));
            contents.put(buildGeminiContent("user", toolResultMsg));
            body.put("contents", contents);

            JSONObject genConfig = new JSONObject();
            genConfig.put("maxOutputTokens", 512);
            body.put("generationConfig", genConfig);
        } catch (JSONException e) {
            throw new IOException("Failed to build Gemini multi-turn request", e);
        }
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String bodyStr = requireBody(response, "Gemini");
            try {
                return new JSONObject(bodyStr)
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text");
            } catch (JSONException e) {
                throw new IOException("Unexpected Gemini multi-turn response format", e);
            }
        }
    }

    /**
     * OpenAI Turn 2: messages array including system, user, assistant, user.
     */
    @WorkerThread
    private String callOpenAiMultiTurn(String apiKey, String model, String systemPrompt,
            String userQuery, String assistantReply, String toolResultMsg) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model);
            body.put("max_completion_tokens", 512);
            JSONArray messages = new JSONArray();
            messages.put(buildClaudeMsg("system", systemPrompt));
            messages.put(buildClaudeMsg("user", userQuery));
            messages.put(buildClaudeMsg("assistant", assistantReply));
            messages.put(buildClaudeMsg("user", toolResultMsg));
            body.put("messages", messages);
        } catch (JSONException e) {
            throw new IOException("Failed to build OpenAI multi-turn request", e);
        }
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("content-type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String bodyStr = requireBody(response, "OpenAI");
            try {
                return new JSONObject(bodyStr)
                        .getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
            } catch (JSONException e) {
                throw new IOException("Unexpected OpenAI multi-turn response format", e);
            }
        }
    }

    /** Builds a {role, content} JSON object for Claude / OpenAI message format. */
    private static JSONObject buildClaudeMsg(String role, String content) throws JSONException {
        JSONObject msg = new JSONObject();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    /** Builds a {role, parts:[{text}]} JSON object for Gemini contents format. */
    private static JSONObject buildGeminiContent(String role, String text) throws JSONException {
        JSONObject content = new JSONObject();
        content.put("role", role);
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();
        part.put("text", text);
        parts.put(part);
        content.put("parts", parts);
        return content;
    }

    // -------------------------------------------------------------------------
    // Input sanitization
    // -------------------------------------------------------------------------

    /**
     * Sanitizes and caps the user's query before it is sent to any LLM.
     *
     * <ul>
     *   <li>Strips ASCII control characters (prevents newline injection into JSON strings)</li>
     *   <li>Truncates to {@link #MAX_QUERY_LENGTH} characters</li>
     * </ul>
     */
    @NonNull
    static String sanitizeQuery(@NonNull String raw) {
        // Remove ASCII control characters (0x00-0x1F except printable whitespace 0x09, 0x0A, 0x0D)
        String cleaned = raw.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "").trim();
        // Collapse newlines — prevent the user from injecting new "role" turns (ATK-09)
        cleaned = cleaned.replaceAll("[\\r\\n]+", " ");
        // ATK-29: NFKC-normalise to defeat Unicode homoglyph bypass (e.g. dotless-i ı→i,
        // zero-width joiners, look-alike Cyrillic letters in injection keywords).
        cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFKC);
        // Neutralise known prompt-injection patterns (ATK-09)
        if (INJECTION_PATTERN.matcher(cleaned).find()) {
            Timber.w("Prompt injection pattern detected in query; replacing.");
            cleaned = INJECTION_PATTERN.matcher(cleaned).replaceAll("[filtered]");
        }
        if (cleaned.length() > MAX_QUERY_LENGTH) {
            cleaned = cleaned.substring(0, MAX_QUERY_LENGTH);
        }
        return cleaned;
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private static String requireBody(Response response, String providerName) throws IOException {
        ResponseBody body = response.body();
        if (body == null) throw new IOException(providerName + " returned empty response");
        String text = body.string();
        if (!response.isSuccessful()) {
            int code = response.code();
            String userMessage = extractProviderError(text, code, providerName);
            throw new LlmApiException(code, userMessage);
        }
        return text;
    }

    /**
     * Attempts to parse a provider-specific JSON error body and return a user-friendly message.
     * Falls back to a generic message based on the HTTP code.
     *
     * <p>Supported error formats:
     * <ul>
     *   <li>Claude: {@code {"error":{"message":"..."}}}</li>
     *   <li>Gemini: {@code {"error":{"message":"..."}}}</li>
     *   <li>OpenAI: {@code {"error":{"message":"...", "code":"..."}}}</li>
     * </ul>
     */
    private static String extractProviderError(String body, int httpCode, String providerName) {
        // Try to extract message from provider JSON error body
        try {
            JSONObject json = new JSONObject(body);
            JSONObject error = json.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", null);
                if (message != null && !message.isEmpty()) {
                    // Prepend provider name and truncate long messages
                    if (message.length() > 200) message = message.substring(0, 200) + "\u2026";
                    return providerName + ": " + message;
                }
            }
        } catch (JSONException ignored) {
            // Body was not JSON — fall through to generic messages below
        }

        // Generic messages by HTTP code
        switch (httpCode) {
            case 401:
            case 403:
                return providerName + ": Invalid API key. Go to Settings \u2192 AI Assistant to update it.";
            case 402:
                return providerName + ": Payment required \u2014 add credits to your account.";
            case 429:
                return providerName + ": Rate limit or quota exceeded. Check your billing on the provider\u2019s dashboard.";
            case 500:
            case 502:
            case 503:
            case 529:
                return providerName + ": Service temporarily unavailable. Try again in a moment.";
            default:
                return providerName + ": API error (HTTP " + httpCode + ").";
        }
    }

    /**
     * Parses an agent-mode LLM response into an {@link AiAgentResponse}.
     */
    @NonNull
    private static AiAgentResponse parseAgentResponse(@NonNull String llmText) throws IOException {
        String cleaned = stripMarkdownFences(llmText);
        try {
            JSONObject json = new JSONObject(cleaned);
            return AiAgentResponse.fromJson(json);
        } catch (JSONException e) {
            Timber.d("Agent LLM response was not valid JSON");
            throw new IOException("Agent LLM response was not valid JSON", e);
        }
    }

    /** Strips {@code ```json ... ``` } fences from LLM output, if present. */
    @NonNull
    private static String stripMarkdownFences(@NonNull String text) {
        String s = text.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (start != -1 && end > start) s = s.substring(start, end).trim();
        }
        return s;
    }

    @NonNull
    private static LlmSuggestion parseSuggestion(@NonNull String llmText) throws IOException {
        String cleaned = stripMarkdownFences(llmText);
        try {
            JSONObject json = new JSONObject(cleaned);
            JSONArray catArray = json.optJSONArray("categories");
            String rawReasoning = json.optString("reasoning", "");
            // Sanitize: strip HTML-like tags, cap length to prevent injected content from displaying
            String reasoning = rawReasoning.replaceAll("<[^>]*>", "").trim();
            if (reasoning.length() > 300) reasoning = reasoning.substring(0, 300) + "\u2026";

            List<FilterListCategory> categories = new ArrayList<>();
            if (catArray != null) {
                for (int i = 0; i < catArray.length(); i++) {
                    String name = catArray.getString(i).toUpperCase().trim();
                    try {
                        FilterListCategory cat = FilterListCategory.valueOf(name);
                        // Skip USER and CUSTOM — those are user-managed, not AI-suggested
                        if (cat != FilterListCategory.USER && cat != FilterListCategory.CUSTOM) {
                            categories.add(cat);
                        }
                    } catch (IllegalArgumentException ignored) {
                        Timber.d("LLM returned unknown category: %s", name);
                    }
                }
            }

            if (categories.isEmpty()) {
                // Fallback: safe defaults if LLM returned nothing usable
                categories.add(FilterListCategory.ADS);
                categories.add(FilterListCategory.MALWARE);
            }

            return new LlmSuggestion(categories, reasoning);
        } catch (JSONException e) {
            Timber.d("LLM response was not valid JSON");
            throw new IOException("LLM response was not valid JSON", e);
        }
    }
}
