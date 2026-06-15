package org.adaway.model.update;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApkIntegrityVerifierTest {
    @Test
    public void sha256Hex_hashesStreamBytes() throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
                "adaway".getBytes(StandardCharsets.UTF_8));

        assertEquals(
                "0ea755783e259893cd0adb1fa5d3eafe24792c1c87997b1a80b23cbccaa73e54",
                ApkIntegrityVerifier.sha256Hex(inputStream));
    }

    @Test
    public void signerDecision_requiresManifestAndInstalledSignerMatch() {
        String expected = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String other = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

        assertTrue(ApkIntegrityVerifier.hasExpectedAndInstalledSigningCertificate(
                Set.of(expected), Set.of(expected), expected));
        assertFalse(ApkIntegrityVerifier.hasExpectedAndInstalledSigningCertificate(
                Set.of(expected), Set.of(other), expected));
        assertFalse(ApkIntegrityVerifier.hasExpectedAndInstalledSigningCertificate(
                Set.of(other), Set.of(other), expected));
    }
}
