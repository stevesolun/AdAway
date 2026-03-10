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

    // -------------------------------------------------------------------------
    // Edge cases: malformed / adversarial JSON
    // -------------------------------------------------------------------------

    @Test
    public void actionWithNullPayload_handledGracefully() throws JSONException {
        // LLM might return JSON null for payload
        JSONObject json = new JSONObject(
                "{\"reasoning\":\"\",\"actions\":[{\"type\":\"CHECK_DOMAIN\",\"payload\":null}]}");
        try {
            AiAgentResponse resp = AiAgentResponse.fromJson(json);
            // If parsed, payload should be null or empty string, not blow up
            if (!resp.actions.isEmpty()) {
                String payload = resp.actions.get(0).payload;
                assertTrue("Null payload must be null or empty",
                        payload == null || payload.isEmpty());
            }
        } catch (JSONException e) {
            // Acceptable — just must not produce a corrupt action with raw "null" string
        }
    }

    @Test
    public void actionMissingTypeField_skippedGracefully() throws JSONException {
        // Action object with no "type" key at all
        JSONObject json = new JSONObject(
                "{\"reasoning\":\"\",\"actions\":[{\"payload\":\"ads.example.com\"}]}");
        try {
            AiAgentResponse resp = AiAgentResponse.fromJson(json);
            assertTrue("Action missing type field must be skipped", resp.actions.isEmpty());
        } catch (JSONException e) {
            // Also acceptable — must not silently produce wrong action
        }
    }

    @Test
    public void maliciousHtmlInPayload_passedThrough() throws JSONException {
        // Payload is validated downstream, not here — just verify no crash and payload preserved
        String malHtml = "<script>alert(1)</script>";
        JSONObject json = new JSONObject(
                "{\"reasoning\":\"\",\"actions\":[{\"type\":\"BLOCK_DOMAIN\",\"payload\":\"" + malHtml + "\"}]}");
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        assertEquals(1, resp.actions.size());
        // Payload is raw — sanitization is AiActionExecutor's job, not parser's
        assertEquals(malHtml.trim(), resp.actions.get(0).payload);
    }

    @Test
    public void manyActionsInSingleResponse_allParsed() throws JSONException {
        // LLM returns many valid actions at once
        StringBuilder sb = new StringBuilder("{\"reasoning\":\"bulk\",\"actions\":[");
        AiAgentAction.Type[] types = AiAgentAction.Type.values();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"type\":\"").append(types[i].name()).append("\",\"payload\":\"x\"}");
        }
        sb.append("]}");
        AiAgentResponse resp = AiAgentResponse.fromJson(new JSONObject(sb.toString()));
        assertEquals("All " + types.length + " distinct action types must be parsed",
                types.length, resp.actions.size());
    }

    @Test
    public void actionsIsNotArray_handledGracefully() throws JSONException {
        // LLM returns "actions" as a string instead of array
        JSONObject json = new JSONObject("{\"reasoning\":\"...\",\"actions\":\"BLOCK_DOMAIN\"}");
        try {
            AiAgentResponse resp = AiAgentResponse.fromJson(json);
            assertTrue("Non-array actions field must yield empty list",
                    resp.actions.isEmpty());
        } catch (JSONException e) {
            // Acceptable — just must not crash with NPE or corrupt state
        }
    }

    @Test
    public void htmlInjectionInReasoning_scriptTagStripped() throws JSONException {
        // HTML stripping uses replaceAll("<[^>]*>","") — removes tags but not text content.
        // Text content is fine in a plain-text TextView (no JS execution risk).
        // We only guarantee the angle-bracket tags are gone.
        JSONObject json = new JSONObject();
        json.put("reasoning", "<b>Block</b> <script>alert(1)</script> ads <em>now</em>");
        json.put("actions", new org.json.JSONArray());
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        assertFalse("Opening script tag must be stripped", resp.reasoning.contains("<script>"));
        assertFalse("Closing script tag must be stripped", resp.reasoning.contains("</script>"));
        assertFalse("<b> tag must be stripped", resp.reasoning.contains("<b>"));
        assertFalse("<em> tag must be stripped", resp.reasoning.contains("<em>"));
        assertTrue("Text 'Block' must remain", resp.reasoning.contains("Block"));
        assertTrue("Text 'ads' must remain", resp.reasoning.contains("ads"));
    }

    @Test
    public void exactlyMaxReasoningLength_notTruncated() throws JSONException {
        // 300 chars exactly — should NOT be truncated (truncation is > 300)
        String text = "x".repeat(300);
        JSONObject json = new JSONObject("{\"reasoning\":\"" + text + "\",\"actions\":[]}");
        AiAgentResponse resp = AiAgentResponse.fromJson(json);
        // Should be exactly 300 chars, no ellipsis
        assertFalse("300-char reasoning must not be truncated",
                resp.reasoning.endsWith("\u2026"));
        assertEquals(300, resp.reasoning.length());
    }
}
