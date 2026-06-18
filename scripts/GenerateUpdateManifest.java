import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GenerateUpdateManifest {
    private static final Set<String> ALLOWED_HOSTS = new HashSet<>(
            Arrays.asList("app.adaway.org", "github.com"));
    private static final String GITHUB_RELEASE_PREFIX =
            "/stevesolun/AdAway/releases/download/";
    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN ([A-Z ]+)-----(.*?)-----END \\1-----", Pattern.DOTALL);

    private GenerateUpdateManifest() {
    }

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Options options = Options.parse(args);
        byte[] apkBytes = Files.readAllBytes(options.apk);
        if (apkBytes.length == 0) {
            fail("APK does not exist or is empty: " + options.apk);
        }

        String apkSha256 = sha256Hex(apkBytes);
        String certSha256 = normalizeSha256(options.certSha256);
        if (!isSha256(apkSha256) || !isSha256(certSha256)) {
            fail("APK and certificate hashes must be 64-character SHA-256 hex values.");
        }
        validateApkUrl(options.apkUrl);

        String payload = buildPayload(options, apkSha256, certSha256);
        PrivateKey privateKey = loadPrivateKey(options.privateKeyBase64);
        byte[] signatureBytes = signPayload(privateKey, payload);
        if (!options.publicKeyBase64.isEmpty()) {
            verifyPayload(options.publicKeyBase64, payload, signatureBytes);
        }

        String signature = Base64.getEncoder().encodeToString(signatureBytes);
        String envelope = "{\"payload\":" + quoteJson(payload) +
                ",\"signature\":" + quoteJson(signature) + "}";
        Path parent = options.out.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(options.out, envelope, StandardCharsets.UTF_8);
        String checksum = sha256Hex(Files.readAllBytes(options.out)) + "  " +
                options.out.getFileName() + "\n";
        Files.writeString(Path.of(options.out.toString() + ".sha256"), checksum,
                StandardCharsets.UTF_8);
        System.out.println("Generated signed update manifest: " + options.out);
    }

    private static String buildPayload(Options options, String apkSha256, String certSha256) {
        String expiresAt = Instant.now()
                .plus(options.validDays, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.SECONDS)
                .toString();
        return "{" +
                "\"version\":" + quoteJson(options.version) + "," +
                "\"versionCode\":" + options.versionCode + "," +
                "\"changelog\":" + quoteJson(options.changelog) + "," +
                "\"apkSha256\":" + quoteJson(apkSha256) + "," +
                "\"signingCertificateSha256\":" + quoteJson(certSha256) + "," +
                "\"apkUrl\":" + quoteJson(options.apkUrl) + "," +
                "\"channel\":" + quoteJson(options.channel) + "," +
                "\"store\":" + quoteJson(options.store) + "," +
                "\"expiresAt\":" + quoteJson(expiresAt) +
                "}";
    }

    private static byte[] signPayload(PrivateKey privateKey, String payload) throws Exception {
        if (!(privateKey instanceof RSAPrivateKey)) {
            fail("Update manifest private key must be an RSA private key.");
        }
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return signer.sign();
    }

    private static void verifyPayload(String publicKeyBase64, String payload, byte[] signatureBytes)
            throws Exception {
        byte[] publicKeyDer = decodeBase64OrPem(publicKeyBase64, "PUBLIC KEY");
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyDer)));
        verifier.update(payload.getBytes(StandardCharsets.UTF_8));
        if (!verifier.verify(signatureBytes)) {
            fail("Generated update manifest signature did not verify with the public key.");
        }
    }

    private static PrivateKey loadPrivateKey(String privateKeyBase64) throws Exception {
        byte[] privateKeyDer = decodeBase64OrPem(privateKeyBase64, "PRIVATE KEY");
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyDer));
    }

    private static byte[] decodeBase64OrPem(String value, String expectedBlock) {
        byte[] decoded = Base64.getMimeDecoder().decode(value);
        String text = new String(decoded, StandardCharsets.US_ASCII);
        if (!text.contains("-----BEGIN ")) {
            return decoded;
        }

        Matcher matcher = PEM_BLOCK.matcher(text);
        while (matcher.find()) {
            String blockName = matcher.group(1);
            if (blockName.equals(expectedBlock)) {
                return Base64.getMimeDecoder().decode(matcher.group(2));
            }
        }
        fail("Expected PEM block was not found: " + expectedBlock);
        return new byte[0];
    }

    private static void validateApkUrl(String apkUrl) {
        URI parsed;
        try {
            parsed = URI.create(apkUrl);
        } catch (IllegalArgumentException exception) {
            fail("apk-url is not a valid URI.");
            return;
        }

        String host = parsed.getHost() == null ? "" :
                parsed.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(parsed.getScheme())) {
            fail("apk-url must use HTTPS.");
        }
        if (!ALLOWED_HOSTS.contains(host)) {
            fail("apk-url host is not in the release allowlist.");
        }
        if (parsed.getPort() != -1 && parsed.getPort() != 443) {
            fail("apk-url must use the default HTTPS port.");
        }
        String path = parsed.getPath() == null ? "" : parsed.getPath();
        if ("github.com".equals(host) && (!path.startsWith(GITHUB_RELEASE_PREFIX) ||
                !path.toLowerCase(Locale.ROOT).endsWith(".apk"))) {
            fail("apk-url must point to the fork GitHub APK release.");
        }
        if (parsed.getUserInfo() != null || parsed.getRawFragment() != null) {
            fail("apk-url must not include user info or a fragment.");
        }
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    private static String normalizeSha256(String value) {
        return value.replace(":", "")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static boolean isSha256(String value) {
        return value.matches("[0-9a-f]{64}");
    }

    private static String quoteJson(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
            }
        }
        out.append('"');
        return out.toString();
    }

    private static void fail(String message) {
        throw new IllegalArgumentException(message);
    }

    private static final class Options {
        final Path apk;
        final String version;
        final int versionCode;
        final String certSha256;
        final String apkUrl;
        final String privateKeyBase64;
        final String publicKeyBase64;
        final Path out;
        final String channel;
        final String store;
        final String changelog;
        final int validDays;

        private Options(Map<String, String> values) {
            apk = Path.of(required(values, "apk"));
            version = required(values, "version");
            versionCode = parsePositiveInt(required(values, "version-code"), "version-code");
            certSha256 = required(values, "cert-sha256");
            apkUrl = required(values, "apk-url");
            privateKeyBase64 = required(values, "private-key-base64");
            publicKeyBase64 = values.getOrDefault("public-key-base64", "");
            out = Path.of(required(values, "out"));
            channel = values.getOrDefault("channel", "stable");
            store = values.getOrDefault("store", "adaway");
            changelog = values.getOrDefault("changelog", "See release notes for " + version + ".");
            validDays = parsePositiveInt(values.getOrDefault("valid-days", "14"), "valid-days");
            if (validDays < 1 || validDays > 14) {
                fail("valid-days must be between 1 and 14.");
            }
        }

        static Options parse(String[] args) throws IOException {
            Map<String, String> values = new HashMap<>();
            values.put("private-key-base64",
                    System.getenv().getOrDefault("UPDATE_MANIFEST_PRIVATE_KEY_BASE64", ""));
            values.put("public-key-base64",
                    System.getenv().getOrDefault("UPDATE_MANIFEST_PUBLIC_KEY_BASE64", ""));

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("-h".equals(arg) || "--help".equals(arg)) {
                    usage();
                    System.exit(0);
                }
                if (!arg.startsWith("--")) {
                    fail("Unknown argument: " + arg);
                }
                String key = arg.substring(2);
                if (i + 1 >= args.length) {
                    fail("Missing value for " + arg);
                }
                values.put(key, args[++i]);
            }
            Options options = new Options(values);
            if (!Files.isRegularFile(options.apk)) {
                fail("APK does not exist or is empty: " + options.apk);
            }
            return options;
        }

        private static String required(Map<String, String> values, String key) {
            String value = values.get(key);
            if (value == null || value.trim().isEmpty()) {
                usage();
                fail("Missing required argument: --" + key);
            }
            return value;
        }

        private static int parsePositiveInt(String value, String label) {
            if (!value.matches("[0-9]+")) {
                fail(label + " must be an integer.");
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                fail(label + " must be an integer.");
                return 0;
            }
        }

        private static void usage() {
            System.err.println("Usage: generate-update-manifest --apk PATH --version VERSION " +
                    "--version-code CODE --cert-sha256 HEX --apk-url URL " +
                    "--private-key-base64 BASE64 --out PATH");
            System.err.println();
            System.err.println("Optional:");
            System.err.println("  --public-key-base64 BASE64  Verify the generated signature");
            System.err.println("  --channel VALUE             Default: stable");
            System.err.println("  --store VALUE               Default: adaway");
            System.err.println("  --changelog VALUE           Default: See release notes for VERSION.");
            System.err.println("  --valid-days DAYS           Default: 14");
        }
    }
}
