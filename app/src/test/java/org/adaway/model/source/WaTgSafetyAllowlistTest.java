package org.adaway.model.source;

import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.HostsSource;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for WaTgSafetyAllowlist.
 *
 * Tests the non-Android logic: required domains list, item construction,
 * duplicate-guard logic, and coverage of required wildcards.
 */
public class WaTgSafetyAllowlistTest {

    // -------------------------------------------------------------------------
    // Class existence (RED until class is created)
    // -------------------------------------------------------------------------

    @Test
    public void waTgSafetyAllowlistClass_exists() throws ClassNotFoundException {
        Class.forName("org.adaway.model.source.WaTgSafetyAllowlist");
    }

    // -------------------------------------------------------------------------
    // REQUIRED_DOMAINS list content
    // -------------------------------------------------------------------------

    @Test
    public void requiredDomains_containsWhatsappWildcards() {
        List<String> domains = WaTgSafetyAllowlist.REQUIRED_DOMAINS;
        assertTrue("Must contain *.whatsapp.com", domains.contains("*.whatsapp.com"));
        assertTrue("Must contain *.whatsapp.net", domains.contains("*.whatsapp.net"));
    }

    @Test
    public void requiredDomains_containsTelegramWildcards() {
        List<String> domains = WaTgSafetyAllowlist.REQUIRED_DOMAINS;
        assertTrue("Must contain telegram.org",  domains.contains("telegram.org"));
        assertTrue("Must contain *.telegram.org", domains.contains("*.telegram.org"));
        assertTrue("Must contain telegram.me",    domains.contains("telegram.me"));
        assertTrue("Must contain *.telegram.me",  domains.contains("*.telegram.me"));
        assertTrue("Must contain t.me",           domains.contains("t.me"));
        assertTrue("Must contain *.t.me",         domains.contains("*.t.me"));
    }

    @Test
    public void requiredDomains_containsWaMe() {
        List<String> domains = WaTgSafetyAllowlist.REQUIRED_DOMAINS;
        assertTrue("Must contain wa.me",   domains.contains("wa.me"));
        assertTrue("Must contain *.wa.me", domains.contains("*.wa.me"));
    }

    @Test
    public void requiredDomains_containsFbcdnAndFacebook() {
        List<String> domains = WaTgSafetyAllowlist.REQUIRED_DOMAINS;
        assertTrue("Must contain *.fbcdn.net",    domains.contains("*.fbcdn.net"));
        assertTrue("Must contain *.facebook.com", domains.contains("*.facebook.com"));
    }

    @Test
    public void requiredDomains_hasExpectedCount() {
        // Exactly 12 entries as specified in the task
        assertEquals("REQUIRED_DOMAINS must contain exactly 12 entries",
                12, WaTgSafetyAllowlist.REQUIRED_DOMAINS.size());
    }

    @Test
    public void requiredDomains_hasNoDuplicates() {
        List<String> domains = WaTgSafetyAllowlist.REQUIRED_DOMAINS;
        Set<String> unique = new HashSet<>(domains);
        assertEquals("REQUIRED_DOMAINS must not have duplicates",
                unique.size(), domains.size());
    }

    // -------------------------------------------------------------------------
    // buildItem() — factory method for HostListItem
    // -------------------------------------------------------------------------

    @Test
    public void buildItem_createsAllowedEntry() {
        HostListItem item = WaTgSafetyAllowlist.buildItem("*.whatsapp.net");
        assertEquals("type must be ALLOWED", ListType.ALLOWED, item.getType());
    }

    @Test
    public void buildItem_setsHost() {
        HostListItem item = WaTgSafetyAllowlist.buildItem("t.me");
        assertEquals("host must match input", "t.me", item.getHost());
    }

    @Test
    public void buildItem_setsSourceIdToUserList() {
        HostListItem item = WaTgSafetyAllowlist.buildItem("telegram.org");
        assertEquals("source_id must be USER_SOURCE_ID (1)",
                HostsSource.USER_SOURCE_ID, item.getSourceId());
    }

    @Test
    public void buildItem_isEnabled() {
        HostListItem item = WaTgSafetyAllowlist.buildItem("wa.me");
        assertTrue("item must be enabled", item.isEnabled());
    }

    @Test
    public void buildItem_hasZeroGeneration() {
        HostListItem item = WaTgSafetyAllowlist.buildItem("*.t.me");
        assertEquals("generation must be 0 (user list)", 0, item.getGeneration());
    }

    // -------------------------------------------------------------------------
    // Duplicate-guard logic (pure/static)
    // -------------------------------------------------------------------------

    @Test
    public void isAlreadyPresent_returnsFalse_whenListIsEmpty() {
        List<HostListItem> existing = java.util.Collections.emptyList();
        assertFalse("Empty list → not present",
                WaTgSafetyAllowlist.isAlreadyPresent("*.whatsapp.net", existing));
    }

    @Test
    public void isAlreadyPresent_returnsTrue_whenExactMatchExists() {
        HostListItem item = WaTgSafetyAllowlist.buildItem("*.whatsapp.net");
        List<HostListItem> existing = java.util.Collections.singletonList(item);
        assertTrue("Exact match → present",
                WaTgSafetyAllowlist.isAlreadyPresent("*.whatsapp.net", existing));
    }

    @Test
    public void isAlreadyPresent_returnsFalse_whenDifferentDomain() {
        HostListItem item = WaTgSafetyAllowlist.buildItem("*.whatsapp.com");
        List<HostListItem> existing = java.util.Collections.singletonList(item);
        assertFalse("Different domain → not present",
                WaTgSafetyAllowlist.isAlreadyPresent("*.whatsapp.net", existing));
    }
}
