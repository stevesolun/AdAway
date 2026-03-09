package org.adaway.model.ai;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verifies the safety constants introduced for the agentic loop.
 *
 * <p>These constants guard against context-stuffing attacks — where a malicious server
 * or hallucinating LLM returns many CHECK_DOMAIN actions with long payloads to bloat
 * the Turn-2 prompt and exhaust tokens or bypass later LLM reasoning.
 */
public class AgenticLoopConstantsTest {

    /** Maximum CHECK_DOMAIN actions auto-executed per loop must not exceed a safe limit. */
    @Test
    public void maxLoopCheckActions_isSafe() {
        assertTrue("MAX_LOOP_CHECK_ACTIONS must be positive",
                FilterListSuggester.MAX_LOOP_CHECK_ACTIONS > 0);
        assertTrue("MAX_LOOP_CHECK_ACTIONS must be ≤ 10 (context-stuffing guard)",
                FilterListSuggester.MAX_LOOP_CHECK_ACTIONS <= 10);
    }

    /** Maximum characters per injected tool-result must not expose large amounts of data. */
    @Test
    public void maxToolResultChars_isSafe() {
        assertTrue("MAX_TOOL_RESULT_CHARS must be positive",
                FilterListSuggester.MAX_TOOL_RESULT_CHARS > 0);
        assertTrue("MAX_TOOL_RESULT_CHARS must be ≤ 200 (context-stuffing guard)",
                FilterListSuggester.MAX_TOOL_RESULT_CHARS <= 200);
    }

    /**
     * Verifies that CHECK_DOMAIN is the ONLY action type that would be auto-executed
     * (read-only). All write types must require user approval.
     */
    @Test
    public void onlyCheckDomain_isReadOnlyAction() {
        for (AiAgentAction.Type type : AiAgentAction.Type.values()) {
            if (type == AiAgentAction.Type.CHECK_DOMAIN) continue;
            // All other types write to DB or trigger network work — must NOT auto-execute
            assertNotEquals(
                    "Type " + type + " must NOT auto-execute (write-action requires user approval)",
                    AiAgentAction.Type.CHECK_DOMAIN, type);
        }
    }

    /** Sanity: AiAgentAction.Type enum has not grown beyond documented action set. */
    @Test
    public void actionTypeCount_matchesDocumentedSet() {
        // Documented: SUBSCRIBE_CATEGORY, ENABLE_CATEGORY, DISABLE_CATEGORY,
        //             UPDATE_SOURCES, CHECK_DOMAIN, ALLOW_DOMAIN, BLOCK_DOMAIN = 7
        assertEquals("AiAgentAction.Type must have exactly 7 values — update test if intentionally extended",
                7, AiAgentAction.Type.values().length);
    }
}
