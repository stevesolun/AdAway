package org.adaway.model.ai;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link AiAgentResponse#fromJson(JSONObject)}.
 *
 * <p>Verifies the closed-enum gate (unknown action types silently skipped),
 * reasoning sanitization, and action parsing.
 */
public class AiAgentResponseParseTest {

    // -------------------------------------------------------------------------
    // Action parsing — closed-enum gate
    // -------------------------------------------------------------------------

    @Test
    public void validActionType_parsed() throws JSONException {
        JSONObject json = new JSONObject(
                "{\"reasoning\":\"Block ads\",\"actions\":[{\"type\":\"SUBSCRIBE_CATEGORY\",\"payload\":\"ADS\"}]}");
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        assertEquals(1, resp.actions.size());
        assertEquals(AiAgentAction.Type.SUBSCRIBE_CATEGORY, resp.actions.get(0).type);
        assertEquals("ADS", resp.actions.get(0).payload);
    }

    @Test
    public void unknownActionType_silentlySkipped() throws JSONException {
        JSONObject json = new JSONObject(
                "{\"reasoning\":\"...\",\"actions\":[{\"type\":\"HACK_EVERYTHING\",\"payload\":\"x\"}]}");
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        assertTrue("Hallucinated action type must be silently skipped", resp.actions.isEmpty());
    }

    @Test
    public void mixedValidAndUnknownActions_onlyValidKept() throws JSONException {
        String raw = "{\"reasoning\":\"...\",\"actions\":["
                + "{\"type\":\"BLOCK_DOMAIN\",\"payload\":\"ads.example.com\"},"
                + "{\"type\":\"FAKE_ACTION\",\"payload\":\"evil\"},"
                + "{\"type\":\"UPDATE_SOURCES\",\"payload\":\"\"}"
                + "]}";
        AiAgentResponse resp = AiAgentResponse.fromJson(new JSONObject(raw));
        assertEquals("Only 2 valid actions must survive", 2, resp.actions.size());
        assertEquals(AiAgentAction.Type.BLOCK_DOMAIN, resp.actions.get(0).type);
        assertEquals(AiAgentAction.Type.UPDATE_SOURCES, resp.actions.get(1).type);
    }

    @Test
    public void missingActionsArray_returnsEmptyList() throws JSONException {
        JSONObject json = new JSONObject("{\"reasoning\":\"Off-topic\"}");
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        assertTrue("Missing actions array must produce empty list", resp.actions.isEmpty());
    }

    @Test
    public void emptyActionsArray_returnsEmptyList() throws JSONException {
        JSONObject json = new JSONObject("{\"reasoning\":\"...\",\"actions\":[]}");
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        assertTrue(resp.actions.isEmpty());
    }

    @Test
    public void allActionTypes_parseable() throws JSONException {
        AiAgentAction.Type[] allTypes = AiAgentAction.Type.values();
        for (AiAgentAction.Type type : allTypes) {
            String raw = "{\"reasoning\":\"test\",\"actions\":[{\"type\":\"" + type.name() + "\",\"payload\":\"x\"}]}";
            AiAgentResponse resp = AiAgentResponse.fromJson(new JSONObject(raw));
            assertEquals("Type " + type.name() + " must be parsed correctly", 1, resp.actions.size());
            assertEquals(type, resp.actions.get(0).type);
        }
    }

    // -------------------------------------------------------------------------
    // Reasoning sanitization
    // -------------------------------------------------------------------------

    @Test
    public void htmlTagsStrippedFromReasoning() throws JSONException {
        JSONObject json = new JSONObject(
                "{\"reasoning\":\"<b>Block</b> <script>evil()</script> ads\",\"actions\":[]}");
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        assertFalse("HTML tags must be stripped from reasoning",
                resp.reasoning.contains("<b>"));
        assertFalse("Script tags must be stripped",
                resp.reasoning.contains("<script>"));
        assertTrue("Text content must remain", resp.reasoning.contains("Block"));
        assertTrue("Text content must remain", resp.reasoning.contains("ads"));
    }

    @Test
    public void reasoningCappedAt300Chars() throws JSONException {
        String long300Plus = "x".repeat(400);
        JSONObject json = new JSONObject("{\"reasoning\":\"" + long300Plus + "\",\"actions\":[]}");
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        // 300 chars + ellipsis = 301 chars total (U+2026 = 1 char)
        assertTrue("Reasoning must be capped", resp.reasoning.length() <= 301);
        assertTrue("Truncated reasoning must end with ellipsis",
                resp.reasoning.endsWith("\u2026"));
    }

    @Test
    public void shortReasoning_notTruncated() throws JSONException {
        String text = "Block ads and trackers.";
        JSONObject json = new JSONObject("{\"reasoning\":\"" + text + "\",\"actions\":[]}");
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        assertEquals(text, resp.reasoning);
    }

    @Test
    public void missingReasoning_defaultsToEmpty() throws JSONException {
        JSONObject json = new JSONObject("{\"actions\":[]}");
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        assertEquals("", resp.reasoning);
    }

    // -------------------------------------------------------------------------
    // Payload handling
    // -------------------------------------------------------------------------

    @Test
    public void payloadTrimmed() throws JSONException {
        JSONObject json = new JSONObject(
                "{\"reasoning\":\"\",\"actions\":[{\"type\":\"BLOCK_DOMAIN\",\"payload\":\"  ads.evil.com  \"}]}");
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        assertEquals("ads.evil.com", resp.actions.get(0).payload);
    }

    @Test
    public void typeUppercased_caseInsensitiveParse() throws JSONException {
        // LLM might return lowercase type strings
        JSONObject json = new JSONObject(
                "{\"reasoning\":\"\",\"actions\":[{\"type\":\"block_domain\",\"payload\":\"ads.example.com\"}]}");
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        assertEquals("Lowercase type string must be parsed", 1, resp.actions.size());
        assertEquals(AiAgentAction.Type.BLOCK_DOMAIN, resp.actions.get(0).type);
    }
}
