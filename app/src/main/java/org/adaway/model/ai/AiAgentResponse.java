package org.adaway.model.ai;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import timber.log.Timber;

/**
 * Parsed response from the AI agent containing a reasoning sentence and a list of
 * {@link AiAgentAction}s to execute.
 *
 * <p>Unknown action types in the LLM output are silently skipped (closed-enum gate via
 * {@link AiAgentAction.Type#valueOf(String)}).
 */
public final class AiAgentResponse {

    /** One-sentence explanation shown to the user. */
    @NonNull
    public final String reasoning;

    /** Ordered list of actions to perform. May be empty (e.g. off-topic query). */
    @NonNull
    public final List<AiAgentAction> actions;

    public AiAgentResponse(@NonNull String reasoning, @NonNull List<AiAgentAction> actions) {
        this.reasoning = reasoning;
        this.actions = actions;
    }

    /**
     * Parses an LLM JSON response into an {@link AiAgentResponse}.
     *
     * <p>Expected format:
     * <pre>{"reasoning": "...", "actions": [{"type": "SUBSCRIBE_CATEGORY", "payload": "ADS"}]}</pre>
     *
     * @throws JSONException if the JSON is structurally invalid.
     */
    @NonNull
    public static AiAgentResponse fromJson(@NonNull JSONObject json) throws JSONException {
        // Sanitize reasoning: strip HTML tags, cap length to prevent injected content displaying
        String raw = json.optString("reasoning", "");
        String reasoning = raw.replaceAll("<[^>]*>", "").trim();
        if (reasoning.length() > 300) reasoning = reasoning.substring(0, 300) + "\u2026";

        List<AiAgentAction> actions = new ArrayList<>();
        JSONArray arr = json.optJSONArray("actions");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String typeStr = item.optString("type", "").toUpperCase(Locale.ROOT).trim();
                String payload = item.optString("payload", "").trim();
                try {
                    AiAgentAction.Type type = AiAgentAction.Type.valueOf(typeStr);
                    actions.add(new AiAgentAction(type, payload));
                } catch (IllegalArgumentException e) {
                    // Closed-enum gate: silently skip hallucinated action types
                    Timber.d("AI agent returned unknown action type: %s", typeStr);
                }
            }
        }
        return new AiAgentResponse(reasoning, actions);
    }
}
