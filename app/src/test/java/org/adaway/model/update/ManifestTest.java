package org.adaway.model.update;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ManifestTest {
    private KeyPair keyPair;
    private String publicKeyBase64;

    @Before
    public void setUp() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @Test
    public void signedManifest_acceptsValidEnvelope() throws Exception {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        String payload = payload(42, validSha256(), expiresAt.toString());

        Manifest manifest = new Manifest(envelope(payload), 41, publicKeyBase64);

        assertEquals("7.0", manifest.version);
        assertEquals(42, manifest.versionCode);
        assertEquals(validSha256(), manifest.apkSha256);
        assertEquals(validSha256(), manifest.signingCertificateSha256);
        assertEquals("https://app.adaway.org/adaway.apk?versionCode=42", manifest.apkUrl);
        assertEquals(expiresAt, manifest.expiresAt);
        assertTrue(manifest.updateAvailable);
    }

    @Test
    public void signedManifest_acceptsGitHubReleaseApkUrl() throws Exception {
        String payload = payloadWithApkUrl(
                "https://github.com/stevesolun/AdAway/releases/download/v13.5.0/AdAway_13.5.0.apk");

        Manifest manifest = new Manifest(envelope(payload), 41, publicKeyBase64);

        assertEquals(
                "https://github.com/stevesolun/AdAway/releases/download/v13.5.0/AdAway_13.5.0.apk",
                manifest.apkUrl);
    }

    @Test
    public void signedManifest_acceptsAllowedHostCaseInsensitively() throws Exception {
        String payload = payloadWithApkUrl("HTTPS://APP.ADAWAY.ORG/adaway.apk?versionCode=42");

        Manifest manifest = new Manifest(envelope(payload), 41, publicKeyBase64);

        assertEquals("HTTPS://APP.ADAWAY.ORG/adaway.apk?versionCode=42", manifest.apkUrl);
    }

    @Test
    public void signedManifest_acceptsMissingApkUrlForFallback() throws Exception {
        String payload = new JSONObject()
                .put("version", "7.0")
                .put("versionCode", 42)
                .put("changelog", "Security update")
                .put("apkSha256", validSha256())
                .put("signingCertificateSha256", validSha256())
                .put("expiresAt", Instant.now().plusSeconds(3600).toString())
                .toString();

        Manifest manifest = new Manifest(envelope(payload), 41, publicKeyBase64);

        assertNull(manifest.apkUrl);
    }

    @Test
    public void signedManifest_acceptsExpectedDistribution() throws Exception {
        String payload = payloadWithDistribution("stable", "adaway");

        Manifest manifest = new Manifest(envelope(payload), 41, publicKeyBase64,
                "stable", "adaway");

        assertEquals("stable", manifest.channel);
        assertEquals("adaway", manifest.store);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsUnexpectedChannel() throws Exception {
        String payload = payloadWithDistribution("beta", "adaway");

        new Manifest(envelope(payload), 41, publicKeyBase64, "stable", "adaway");
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsUnexpectedStore() throws Exception {
        String payload = payloadWithDistribution("stable", "github");

        new Manifest(envelope(payload), 41, publicKeyBase64, "stable", "adaway");
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsMissingExpectedDistribution() throws Exception {
        String payload = payloadWithApkUrl("https://app.adaway.org/adaway.apk?versionCode=42");

        new Manifest(envelope(payload), 41, publicKeyBase64, "stable", "adaway");
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsArbitraryHttpsApkHost() throws Exception {
        String payload = payloadWithApkUrl("https://updates.example.com/adaway.apk");

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsLookalikeAllowedHostSubdomain() throws Exception {
        String payload = payloadWithApkUrl("https://github.com.evil.example/adaway.apk");

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsAppAdawayOrgSubdomain() throws Exception {
        String payload = payloadWithApkUrl("https://download.app.adaway.org/adaway.apk");

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsGithubOtherRepoReleaseApk() throws Exception {
        String payload = payloadWithApkUrl(
                "https://github.com/AdAway/AdAway/releases/download/v13.5.0/AdAway_13.5.0.apk");

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsGithubNonReleasePath() throws Exception {
        String payload = payloadWithApkUrl(
                "https://github.com/stevesolun/AdAway/archive/refs/tags/v13.5.0.zip");

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsGithubReleaseNonApk() throws Exception {
        String payload = payloadWithApkUrl(
                "https://github.com/stevesolun/AdAway/releases/download/v13.5.0/readme.txt");

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsAllowedHostWithNonDefaultPort() throws Exception {
        String payload = payloadWithApkUrl("https://github.com:444/adaway.apk");

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test
    public void signedManifest_reportsExpiredAfterOriginalExpiryInstant() throws Exception {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        String payload = payload(42, validSha256(), expiresAt.toString());
        Manifest manifest = new Manifest(envelope(payload), 41, publicKeyBase64);

        assertTrue(manifest.isExpiredAt(expiresAt.plusSeconds(1)));
    }

    @Test(expected = GeneralSecurityException.class)
    public void signedManifest_rejectsTamperedPayload() throws Exception {
        String payload = payload(42, validSha256(), Instant.now().plusSeconds(3600).toString());
        String signature = sign(payload);
        String tampered = payload(43, validSha256(), Instant.now().plusSeconds(3600).toString());

        new Manifest(envelope(tampered, signature), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsBadApkSha256() throws Exception {
        String payload = payload(42, "abc", Instant.now().plusSeconds(3600).toString());

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsBadSigningCertificateSha256() throws Exception {
        String payload = new JSONObject()
                .put("version", "7.0")
                .put("versionCode", 42)
                .put("changelog", "Security update")
                .put("apkSha256", validSha256())
                .put("signingCertificateSha256", "abc")
                .put("apkUrl", "https://app.adaway.org/adaway.apk?versionCode=42")
                .put("expiresAt", Instant.now().plusSeconds(3600).toString())
                .toString();

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsExpiredPayload() throws Exception {
        String payload = payload(42, validSha256(), Instant.now().minusSeconds(60).toString());

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsMissingExpiresAt() throws Exception {
        String payload = new JSONObject()
                .put("version", "7.0")
                .put("versionCode", 42)
                .put("changelog", "Security update")
                .put("apkSha256", validSha256())
                .put("signingCertificateSha256", validSha256())
                .put("apkUrl", "https://app.adaway.org/adaway.apk?versionCode=42")
                .toString();

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsExcessiveValidityWindow() throws Exception {
        String payload = payload(42, validSha256(), Instant.now().plusSeconds(15L * 24L * 60L * 60L).toString());

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsEqualOrLowerVersionCode() throws Exception {
        String payload = payload(42, validSha256(), Instant.now().plusSeconds(3600).toString());

        new Manifest(envelope(payload), 42, publicKeyBase64);
    }

    @Test(expected = GeneralSecurityException.class)
    public void signedManifest_rejectsMissingPublicKey() throws Exception {
        String payload = payload(42, validSha256(), Instant.now().plusSeconds(3600).toString());

        new Manifest(envelope(payload), 41, "");
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsHttpApkUrl() throws Exception {
        String payload = payloadWithApkUrl("http://app.adaway.org/adaway.apk");

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsOpaqueHttpsApkUrl() throws Exception {
        String payload = payloadWithApkUrl("https:adaway.apk");

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsUserInfoInApkUrl() throws Exception {
        String payload = payloadWithApkUrl("https://user@app.adaway.org/adaway.apk");

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    @Test(expected = JSONException.class)
    public void signedManifest_rejectsFragmentInApkUrl() throws Exception {
        String payload = payloadWithApkUrl("https://app.adaway.org/adaway.apk#payload");

        new Manifest(envelope(payload), 41, publicKeyBase64);
    }

    private String payload(int versionCode, String sha256, String expiresAt) throws JSONException {
        return new JSONObject()
                .put("version", "7.0")
                .put("versionCode", versionCode)
                .put("changelog", "Security update")
                .put("apkSha256", sha256)
                .put("signingCertificateSha256", validSha256())
                .put("apkUrl", "https://app.adaway.org/adaway.apk?versionCode=" + versionCode)
                .put("expiresAt", expiresAt)
                .toString();
    }

    private String payloadWithApkUrl(String apkUrl) throws JSONException {
        return new JSONObject()
                .put("version", "7.0")
                .put("versionCode", 42)
                .put("changelog", "Security update")
                .put("apkSha256", validSha256())
                .put("signingCertificateSha256", validSha256())
                .put("apkUrl", apkUrl)
                .put("expiresAt", Instant.now().plusSeconds(3600).toString())
                .toString();
    }

    private String payloadWithDistribution(String channel, String store) throws JSONException {
        return new JSONObject()
                .put("version", "7.0")
                .put("versionCode", 42)
                .put("changelog", "Security update")
                .put("apkSha256", validSha256())
                .put("signingCertificateSha256", validSha256())
                .put("apkUrl", "https://app.adaway.org/adaway.apk?versionCode=42")
                .put("channel", channel)
                .put("store", store)
                .put("expiresAt", Instant.now().plusSeconds(3600).toString())
                .toString();
    }

    private String envelope(String payload) throws Exception {
        return envelope(payload, sign(payload));
    }

    private String envelope(String payload, String signature) throws JSONException {
        return new JSONObject()
                .put("payload", payload)
                .put("signature", signature)
                .toString();
    }

    private String sign(String payload) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private static String validSha256() {
        return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    }
}
