package org.adaway.model.update;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * Verified application update manifest.
 */
public class Manifest {
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String KEY_ALGORITHM = "RSA";
    private static final String SHA_256_HEX_PATTERN = "^[0-9a-f]{64}$";
    private static final long MAX_VALIDITY_SECONDS = 14L * 24L * 60L * 60L;
    private static final Set<String> ALLOWED_APK_HOSTS = Set.of(
            "app.adaway.org",
            "github.com"
    );
    private static final String GITHUB_RELEASE_APK_PATH_PREFIX =
            "/stevesolun/AdAway/releases/download/";

    public final String version;
    public final int versionCode;
    public final String changelog;
    public final String apkSha256;
    public final String signingCertificateSha256;
    @Nullable
    public final String apkUrl;
    @Nullable
    public final String channel;
    @Nullable
    public final String store;
    public final Instant expiresAt;
    public final boolean updateAvailable;

    public Manifest(@NonNull String manifest, long currentVersionCode,
            @NonNull String publicKeyBase64) throws JSONException, GeneralSecurityException {
        this(manifest, currentVersionCode, publicKeyBase64, null, null);
    }

    public Manifest(
            @NonNull String manifest,
            long currentVersionCode,
            @NonNull String publicKeyBase64,
            @Nullable String expectedChannel,
            @Nullable String expectedStore) throws JSONException, GeneralSecurityException {
        if (publicKeyBase64.trim().isEmpty()) {
            throw new GeneralSecurityException("Missing update manifest public key.");
        }

        JSONObject envelope = new JSONObject(manifest);
        String payload = envelope.getString("payload");
        String signature = envelope.getString("signature");
        verifyPayloadSignature(payload, signature, publicKeyBase64);

        JSONObject manifestObject = new JSONObject(payload);
        this.version = manifestObject.getString("version");
        this.versionCode = manifestObject.getInt("versionCode");
        this.changelog = manifestObject.getString("changelog");
        this.apkSha256 = normalizeSha256(manifestObject.getString("apkSha256"));
        this.signingCertificateSha256 = normalizeSha256(
                manifestObject.getString("signingCertificateSha256"));
        this.apkUrl = normalizeOptionalUrl(manifestObject.optString("apkUrl", null));
        this.channel = normalizeOptionalToken(manifestObject.optString("channel", null));
        this.store = normalizeOptionalToken(manifestObject.optString("store", null));
        requireExpectedToken("channel", this.channel, expectedChannel);
        requireExpectedToken("store", this.store, expectedStore);

        this.expiresAt = parseAndValidateExpiry(manifestObject.optString("expiresAt", null));
        if (this.versionCode <= currentVersionCode) {
            throw new JSONException("Manifest version is not newer than current version.");
        }
        this.updateAvailable = true;
    }

    private static void verifyPayloadSignature(
            @NonNull String payload, @NonNull String signatureBase64,
            @NonNull String publicKeyBase64) throws GeneralSecurityException {
        byte[] keyBytes;
        byte[] signatureBytes;
        try {
            keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            signatureBytes = Base64.getDecoder().decode(signatureBase64);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Invalid manifest signature encoding.", e);
        }

        PublicKey publicKey = KeyFactory.getInstance(KEY_ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(keyBytes));
        Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
        verifier.initVerify(publicKey);
        verifier.update(payload.getBytes(StandardCharsets.UTF_8));
        if (!verifier.verify(signatureBytes)) {
            throw new GeneralSecurityException("Invalid update manifest signature.");
        }
    }

    @NonNull
    private static String normalizeSha256(@NonNull String value) throws JSONException {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches(SHA_256_HEX_PATTERN)) {
            throw new JSONException("Manifest apkSha256 must be a 64-character hex SHA-256.");
        }
        return normalized;
    }

    @Nullable
    private static String normalizeOptionalUrl(@Nullable String value) throws JSONException {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String trimmed = value.trim();
        try {
            URI uri = new URI(trimmed);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new JSONException("Manifest apkUrl must use HTTPS.");
            }
            if (uri.isOpaque() || uri.getHost() == null || uri.getHost().trim().isEmpty()) {
                throw new JSONException("Manifest apkUrl must include a host.");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (!ALLOWED_APK_HOSTS.contains(host)) {
                throw new JSONException("Manifest apkUrl host is not allowed.");
            }
            if (uri.getPort() != -1 && uri.getPort() != 443) {
                throw new JSONException("Manifest apkUrl must use the default HTTPS port.");
            }
            if ("github.com".equals(host)) {
                validateGitHubReleaseApkPath(uri.getPath());
            }
            if (uri.getUserInfo() != null) {
                throw new JSONException("Manifest apkUrl must not include user info.");
            }
            if (uri.getFragment() != null) {
                throw new JSONException("Manifest apkUrl must not include a fragment.");
            }
            return trimmed;
        } catch (URISyntaxException e) {
            throw new JSONException("Manifest apkUrl is invalid.");
        }
    }

    private static void validateGitHubReleaseApkPath(@Nullable String path) throws JSONException {
        if (path == null || !path.startsWith(GITHUB_RELEASE_APK_PATH_PREFIX) ||
                !path.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            throw new JSONException("Manifest apkUrl must point to the fork GitHub APK release.");
        }
    }

    @Nullable
    private static String normalizeOptionalToken(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static void requireExpectedToken(
            @NonNull String name, @Nullable String actual, @Nullable String expected)
            throws JSONException {
        String normalizedExpected = normalizeOptionalToken(expected);
        if (normalizedExpected == null) {
            return;
        }
        if (!normalizedExpected.equals(actual)) {
            throw new JSONException("Manifest " + name + " does not match request.");
        }
    }

    public boolean isExpired() {
        return isExpiredAt(Instant.now());
    }

    boolean isExpiredAt(@NonNull Instant now) {
        return !this.expiresAt.isAfter(now);
    }

    private static Instant parseAndValidateExpiry(@Nullable String expiresAt) throws JSONException {
        if (expiresAt == null || expiresAt.trim().isEmpty()) {
            throw new JSONException("Manifest expiresAt is required.");
        }

        Instant expiry;
        try {
            expiry = Instant.parse(expiresAt);
        } catch (DateTimeParseException e) {
            throw new JSONException("Manifest expiresAt must be an ISO-8601 instant.");
        }
        Instant now = Instant.now();
        if (!expiry.isAfter(now)) {
            throw new JSONException("Update manifest is expired.");
        }
        if (expiry.isAfter(now.plusSeconds(MAX_VALIDITY_SECONDS))) {
            throw new JSONException("Manifest expiresAt is too far in the future.");
        }
        return expiry;
    }
}
