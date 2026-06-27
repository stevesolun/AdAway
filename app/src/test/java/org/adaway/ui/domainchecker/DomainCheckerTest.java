package org.adaway.ui.domainchecker;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Unit tests for the Domain Checker backend.
 *
 * These tests cover:
 * - DomainCheckResult value-holder correctness
 * - DomainCheckerViewModel.checkDomain() URL-stripping logic (pure Java, no Android deps)
 * - Advice string generation logic
 * - Edge-case inputs (null, empty, URL with protocol and path)
 */
public class DomainCheckerTest {

    // ── DomainCheckResult tests ──────────────────────────────────────────────

    /**
     * DomainCheckResult stores all fields verbatim.
     * Fails (RED) until DomainCheckResult is created.
     */
    @Test
    public void domainCheckResult_storesAllFields() {
        DomainCheckResult.BlockingSource src = new DomainCheckResult.BlockingSource(42, "AdAway Default", false);
        DomainCheckResult result = new DomainCheckResult(
                "example.com",
                true,
                false,
                Arrays.asList(src),
                "Tap \"Add to Allow List\" to create a user exception for this domain."
        );

        assertEquals("example.com", result.domain);
        assertEquals(DomainCheckResult.Status.BLOCKED, result.status);
        assertTrue(result.blocked);
        assertFalse(result.userAllowed);
        assertEquals(1, result.blockingSources.size());
        assertEquals("AdAway Default", result.blockingSources.get(0).name);
        assertNotNull(result.unblockAdvice);
        assertFalse(result.unblockAdvice.isEmpty());
    }

    @Test
    public void domainCheckResult_notBlocked_emptySourceList() {
        DomainCheckResult result = new DomainCheckResult(
                "safe.com",
                false,
                false,
                Collections.emptyList(),
                "This domain is not in any of your blocked lists."
        );

        assertFalse(result.blocked);
        assertEquals(DomainCheckResult.Status.UNKNOWN, result.status);
        assertFalse(result.userAllowed);
        assertTrue(result.blockingSources.isEmpty());
    }

    @Test
    public void domainCheckResult_userAllowed_flagSet() {
        DomainCheckResult.BlockingSource src = new DomainCheckResult.BlockingSource(1, "Some list", false);
        DomainCheckResult result = new DomainCheckResult(
                "allowed.com",
                true,
                true,
                Arrays.asList(src),
                "You have already allowed this domain."
        );

        assertTrue(result.blocked);
        assertTrue(result.userAllowed);
    }

    @Test
    public void domainCheckResult_redirectedStatusCountsAsBlocked() {
        DomainCheckResult result = new DomainCheckResult(
                "redirected.com",
                DomainCheckResult.Status.REDIRECTED,
                false,
                Collections.emptyList(),
                "Redirected by an active rule."
        );

        assertEquals(DomainCheckResult.Status.REDIRECTED, result.status);
        assertTrue(result.blocked);
    }

    // ── URL-stripping logic tests ────────────────────────────────────────────
    // These test the same normalisation logic used inside checkDomain(), extracted
    // here as a static helper so we can unit-test it without an Android runtime.

    @Test
    public void stripDomain_httpUrl_returnsHostname() {
        assertEquals("example.com", DomainNormalizer.normalize("http://example.com/path?q=1"));
    }

    @Test
    public void stripDomain_httpsUrl_returnsHostname() {
        assertEquals("example.com", DomainNormalizer.normalize("https://example.com/"));
    }

    @Test
    public void stripDomain_bareDomain_returnsLowercase() {
        assertEquals("example.com", DomainNormalizer.normalize("  EXAMPLE.COM  "));
    }

    @Test
    public void stripDomain_domainWithPath_returnsHostOnly() {
        assertEquals("ads.example.com", DomainNormalizer.normalize("ads.example.com/tracking/pixel.gif"));
    }

    @Test
    public void stripDomain_nullOrEmpty_returnsNull() {
        assertNull(DomainNormalizer.normalize(null));
        assertNull(DomainNormalizer.normalize(""));
        assertNull(DomainNormalizer.normalize("   "));
    }

    // ── Advice string tests ──────────────────────────────────────────────────

    @Test
    public void advice_blockedNotAllowed_suggestsAllowList() {
        String advice = DomainCheckerViewModel.buildAdvice(true, false);
        assertTrue("Advice must mention allow list",
                advice.contains("Allow List") || advice.contains("allow list") || advice.contains("exception"));
    }

    @Test
    public void advice_blockedAndAllowed_confirmsAlreadyAllowed() {
        String advice = DomainCheckerViewModel.buildAdvice(true, true);
        assertTrue("Advice must say already allowed",
                advice.toLowerCase().contains("already"));
    }

    @Test
    public void advice_notBlocked_confirmsNotBlocked() {
        String advice = DomainCheckerViewModel.buildAdvice(false, false);
        assertTrue("Advice must mention not blocked",
                advice.toLowerCase().contains("not") || advice.toLowerCase().contains("blocked"));
    }

    // ── DomainCheckerViewModel class existence ───────────────────────────────

    @Test
    public void domainCheckerViewModelClass_exists() throws ClassNotFoundException {
        Class.forName("org.adaway.ui.domainchecker.DomainCheckerViewModel");
    }

    @Test
    public void domainCheckResultClass_exists() throws ClassNotFoundException {
        Class.forName("org.adaway.ui.domainchecker.DomainCheckResult");
    }

    @Test
    public void adwarePackageScanDoesNotBlockDomainCheckerDiskQueries() throws Exception {
        String appExecutors = readRepoFile("app/src/main/java/org/adaway/util/AppExecutors.java");
        String adwareLiveData = readRepoFile(
                "app/src/main/java/org/adaway/ui/adware/AdwareLiveData.java");

        assertTrue("AppExecutors must expose a separate package scan executor.",
                appExecutors.contains("packageScanIO()"));
        assertTrue("Adware package scans must not run on the single DB disk executor.",
                adwareLiveData.contains("packageScanIO().execute(this::loadData)"));
        assertFalse("Adware package scans must not starve domain checker DB lookups.",
                adwareLiveData.contains("diskIO().execute(this::loadData)"));
    }

    private static String readRepoFile(String relativePath) throws Exception {
        return new String(Files.readAllBytes(resolveRepoFile(relativePath)), StandardCharsets.UTF_8);
    }

    private static Path resolveRepoFile(String relativePath) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path repo = Files.isDirectory(cwd.resolve("app")) ? cwd : cwd.getParent();
        return repo.resolve(relativePath);
    }
}
