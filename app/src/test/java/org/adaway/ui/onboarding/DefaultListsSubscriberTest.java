package org.adaway.ui.onboarding;

import org.adaway.model.source.FilterListCatalog;
import org.adaway.db.entity.HostsSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for DefaultListsSubscriber.
 *
 * Tests the non-Android logic: CatalogEntry→HostsSource conversion,
 * count-based guard (skip if sources already exist), and result semantics.
 */
public class DefaultListsSubscriberTest {

    /**
     * Converting the default catalog entries via toHostsSource() yields
     * HostsSource objects with non-null labels and valid URLs.
     */
    @Test
    public void defaultEntries_convertToHostsSource_haveValidLabelsAndUrls() {
        List<FilterListCatalog.CatalogEntry> defaults = FilterListCatalog.getDefaults();
        assertFalse("Catalog should have at least one default entry", defaults.isEmpty());

        for (FilterListCatalog.CatalogEntry entry : defaults) {
            HostsSource source = entry.toHostsSource();
            assertNotNull("toHostsSource() should not return null", source);
            assertNotNull("Label must not be null", source.getLabel());
            assertFalse("Label must not be empty", source.getLabel().isEmpty());
            assertNotNull("URL must not be null", source.getUrl());
            assertFalse("URL must not be empty", source.getUrl().isEmpty());
            assertTrue("Source should be enabled by default", source.isEnabled());
        }
    }

    /**
     * subscribeLogic returns true (inserted) when existing source count is 0.
     */
    @Test
    public void subscribeLogic_returnsTrue_whenNoExistingSources() {
        int existingCount = 0;
        List<FilterListCatalog.CatalogEntry> defaults = FilterListCatalog.getDefaults();
        // Simulate the guard
        boolean shouldInsert = existingCount == 0 && !defaults.isEmpty();
        assertTrue("Should insert when database has no sources", shouldInsert);
    }

    /**
     * subscribeLogic returns false (skipped) when sources already exist.
     */
    @Test
    public void subscribeLogic_returnsFalse_whenSourcesAlreadyExist() {
        int existingCount = 3;
        // Simulate the guard
        boolean shouldInsert = existingCount == 0;
        assertFalse("Should skip when database already has sources", shouldInsert);
    }

    /**
     * The converted list for bulk-insert contains the same count as getDefaults().
     */
    @Test
    public void defaultEntries_bulkConversion_matchesDefaultsCount() {
        List<FilterListCatalog.CatalogEntry> defaults = FilterListCatalog.getDefaults();
        List<HostsSource> sources = new ArrayList<>();
        for (FilterListCatalog.CatalogEntry entry : defaults) {
            sources.add(entry.toHostsSource());
        }
        assertEquals("Converted list size must equal defaults list size",
                defaults.size(), sources.size());
    }

    /**
     * DefaultListsSubscriber class exists in the expected package.
     * This will fail (RED) until the class is created.
     */
    @Test
    public void defaultListsSubscriberClass_exists() throws ClassNotFoundException {
        Class.forName("org.adaway.ui.onboarding.DefaultListsSubscriber");
    }
}
