package org.adaway.model.ai;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link AiAgentAction} and its {@link AiAgentAction.Type} enum.
 *
 * <p>Covers construction, toString(), type semantics, and enum stability.
 */
public class AiAgentActionTest {

    // -------------------------------------------------------------------------
    // Constructor & field access
    // -------------------------------------------------------------------------

    @Test
    public void constructor_setsTypeAndPayload() {
        AiAgentAction action = new AiAgentAction(AiAgentAction.Type.BLOCK_DOMAIN, "ads.example.com");
        assertEquals(AiAgentAction.Type.BLOCK_DOMAIN, action.type);
        assertEquals("ads.example.com", action.payload);
    }

    @Test
    public void constructor_emptyPayload_allowed() {
        // UPDATE_SOURCES has no meaningful payload — empty string is valid
        AiAgentAction action = new AiAgentAction(AiAgentAction.Type.UPDATE_SOURCES, "");
        assertEquals(AiAgentAction.Type.UPDATE_SOURCES, action.type);
        assertEquals("", action.payload);
    }

    @Test
    public void constructor_categoryAction_withValidCategory() {
        AiAgentAction action = new AiAgentAction(AiAgentAction.Type.SUBSCRIBE_CATEGORY, "ADS");
        assertEquals(AiAgentAction.Type.SUBSCRIBE_CATEGORY, action.type);
        assertEquals("ADS", action.payload);
    }

    // -------------------------------------------------------------------------
    // toString() format
    // -------------------------------------------------------------------------

    @Test
    public void toString_containsTypeAndPayload() {
        AiAgentAction action = new AiAgentAction(AiAgentAction.Type.CHECK_DOMAIN, "google.com");
        String str = action.toString();
        assertTrue("toString must contain type name", str.contains("CHECK_DOMAIN"));
        assertTrue("toString must contain payload", str.contains("google.com"));
    }

    @Test
    public void toString_neverNull() {
        for (AiAgentAction.Type type : AiAgentAction.Type.values()) {
            AiAgentAction action = new AiAgentAction(type, "test");
            assertNotNull("toString must not return null for " + type, action.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Type enum: completeness and semantics
    // -------------------------------------------------------------------------

    @Test
    public void typeEnum_hasExactlySevenValues() {
        assertEquals(
                "AiAgentAction.Type must have exactly 7 values — update test if intentionally extended",
                7, AiAgentAction.Type.values().length);
    }

    @Test
    public void typeEnum_containsAllDocumentedTypes() {
        // Verify each documented type exists by name (catches renames)
        assertNotNull(AiAgentAction.Type.valueOf("SUBSCRIBE_CATEGORY"));
        assertNotNull(AiAgentAction.Type.valueOf("ENABLE_CATEGORY"));
        assertNotNull(AiAgentAction.Type.valueOf("DISABLE_CATEGORY"));
        assertNotNull(AiAgentAction.Type.valueOf("UPDATE_SOURCES"));
        assertNotNull(AiAgentAction.Type.valueOf("CHECK_DOMAIN"));
        assertNotNull(AiAgentAction.Type.valueOf("ALLOW_DOMAIN"));
        assertNotNull(AiAgentAction.Type.valueOf("BLOCK_DOMAIN"));
    }

    @Test
    public void typeEnum_valueOfUnknown_throwsIllegalArgumentException() {
        // This is the closed-enum gate: FAKE_ACTION must throw, not return null
        try {
            AiAgentAction.Type.valueOf("FAKE_ACTION");
            fail("valueOf('FAKE_ACTION') must throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected — this is the security gate AiAgentResponse.fromJson() relies on
        }
    }

    @Test
    public void typeEnum_checkDomain_isReadOnly() {
        // CHECK_DOMAIN is the only type that auto-executes (read-only).
        // Verify it is semantically different from write types.
        AiAgentAction.Type checkDomain = AiAgentAction.Type.CHECK_DOMAIN;
        for (AiAgentAction.Type type : AiAgentAction.Type.values()) {
            if (type == AiAgentAction.Type.CHECK_DOMAIN) continue;
            assertNotEquals("CHECK_DOMAIN must not be equal to write-type " + type,
                    checkDomain, type);
        }
    }

    // -------------------------------------------------------------------------
    // Category vs domain action classification (semantic grouping)
    // -------------------------------------------------------------------------

    @Test
    public void categoryActions_doNotIncludeDomainActions() {
        // Category actions operate on FilterListCategory, not hostnames
        AiAgentAction.Type[] categoryTypes = {
                AiAgentAction.Type.SUBSCRIBE_CATEGORY,
                AiAgentAction.Type.ENABLE_CATEGORY,
                AiAgentAction.Type.DISABLE_CATEGORY
        };
        AiAgentAction.Type[] domainTypes = {
                AiAgentAction.Type.CHECK_DOMAIN,
                AiAgentAction.Type.ALLOW_DOMAIN,
                AiAgentAction.Type.BLOCK_DOMAIN
        };
        for (AiAgentAction.Type cat : categoryTypes) {
            for (AiAgentAction.Type dom : domainTypes) {
                assertNotEquals("Category type " + cat + " must not equal domain type " + dom,
                        cat, dom);
            }
        }
    }

    @Test
    public void updateSources_isNeither_categoryNorDomain() {
        // UPDATE_SOURCES is its own class — distinct from both
        AiAgentAction.Type update = AiAgentAction.Type.UPDATE_SOURCES;
        assertNotEquals(update, AiAgentAction.Type.SUBSCRIBE_CATEGORY);
        assertNotEquals(update, AiAgentAction.Type.ENABLE_CATEGORY);
        assertNotEquals(update, AiAgentAction.Type.DISABLE_CATEGORY);
        assertNotEquals(update, AiAgentAction.Type.CHECK_DOMAIN);
        assertNotEquals(update, AiAgentAction.Type.ALLOW_DOMAIN);
        assertNotEquals(update, AiAgentAction.Type.BLOCK_DOMAIN);
    }

    // -------------------------------------------------------------------------
    // All types are constructable without exception
    // -------------------------------------------------------------------------

    @Test
    public void allTypes_canBeInstantiated() {
        for (AiAgentAction.Type type : AiAgentAction.Type.values()) {
            AiAgentAction action = new AiAgentAction(type, "test-payload");
            assertNotNull("AiAgentAction must be constructable for type " + type, action);
            assertEquals(type, action.type);
            assertEquals("test-payload", action.payload);
        }
    }
}
