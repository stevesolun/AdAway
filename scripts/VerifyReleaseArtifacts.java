import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

public final class VerifyReleaseArtifacts {
    private static final Set<String> ALLOWED_HOSTS = new HashSet<>(
            Arrays.asList("app.adaway.org", "github.com"));
    private static final String GITHUB_RELEASE_PREFIX =
            "/stevesolun/AdAway/releases/download/";
    private static final Pattern CHECKSUM_LINE = Pattern.compile(
            "^([0-9a-fA-F]{64})\\s+\\*?(.+?)\\s*$");
    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN ([A-Z ]+)-----(.*?)-----END \\1-----", Pattern.DOTALL);
    private static final int ATTESTATION_VERIFY_ATTEMPTS = 4;
    private static final long ATTESTATION_RETRY_DELAY_MILLIS = 3000L;

    private VerifyReleaseArtifacts() {
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
        if (options.report != null) {
            Files.deleteIfExists(options.report);
        }
        options.validateProvenanceOptions();

        String apkSha256 = verifyChecksum(options.apk, options.apkSha256);
        verifyChecksum(options.manifest, options.manifestSha256);
        verifyChecksum(options.sbom, options.sbomSha256);
        verifySbom(options.sbom);

        String manifest = Files.readString(options.manifest, StandardCharsets.UTF_8);
        String payload = readJsonString(manifest, "payload");
        String signature = readJsonString(manifest, "signature");
        verifyManifestSignature(payload, signature, options.publicKeyBase64);
        ManifestPayload manifestPayload = verifyManifestPayload(payload, apkSha256, options);
        verifyApkIdentity(options, manifestPayload);

        int attestedArtifacts = 0;
        String artifactCommit = "not-verified";
        if (options.verifyAttestations) {
            artifactCommit = resolveArtifactCommit(options, manifestPayload);
            for (Path artifact : Arrays.asList(
                    options.apk,
                    options.apkSha256,
                    options.manifest,
                    options.manifestSha256,
                    options.sbom,
                    options.sbomSha256)) {
                boolean metadata = artifact.equals(options.manifest) ||
                        artifact.equals(options.manifestSha256);
                verifyAttestation(artifact, options, manifestPayload, artifactCommit, metadata);
                attestedArtifacts++;
            }
            if (!artifactCommit.equals(resolveArtifactCommit(options, manifestPayload))) {
                fail("Release tag moved during verification.");
            }
        }

        writeReport(options, apkSha256, manifestPayload, attestedArtifacts, artifactCommit);
        System.out.println("Release artifact verification passed.");
    }

    private static String verifyChecksum(Path artifact, Path checksumFile) throws Exception {
        requireReadableFile(artifact, "artifact");
        requireReadableFile(checksumFile, "checksum");

        String actual = sha256Hex(Files.readAllBytes(artifact));
        String checksum = readChecksum(checksumFile, artifact.getFileName().toString());
        if (!actual.equals(checksum)) {
            fail("Checksum mismatch for " + artifact + ".");
        }
        return actual;
    }

    private static String readChecksum(Path checksumFile, String expectedFileName) throws IOException {
        List<String> lines = Files.readAllLines(checksumFile, StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher matcher = CHECKSUM_LINE.matcher(trimmed);
            if (!matcher.matches()) {
                fail("Invalid checksum line in " + checksumFile + ".");
            }
            String fileName = Path.of(matcher.group(2)).getFileName().toString();
            if (!expectedFileName.equals(fileName)) {
                fail("Checksum file " + checksumFile + " points at " + fileName +
                        " instead of " + expectedFileName + ".");
            }
            return matcher.group(1).toLowerCase(Locale.ROOT);
        }
        fail("Checksum file is empty: " + checksumFile + ".");
        return "";
    }

    private static void verifySbom(Path sbom) throws IOException {
        String text = Files.readString(sbom, StandardCharsets.UTF_8);
        if (!text.contains("\"bomFormat\"") || !text.contains("CycloneDX")) {
            fail("SBOM must be a CycloneDX document.");
        }
        if (!text.contains("\"components\"")) {
            fail("SBOM must include a components array.");
        }
    }

    private static void verifyManifestSignature(
            String payload, String signatureBase64, String publicKeyBase64) throws Exception {
        byte[] publicKeyDer = decodeBase64OrPem(publicKeyBase64, "PUBLIC KEY");
        byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(publicKeyDer)));
        verifier.update(payload.getBytes(StandardCharsets.UTF_8));
        if (!verifier.verify(signatureBytes)) {
            fail("Signed update manifest signature is invalid.");
        }
    }

    private static ManifestPayload verifyManifestPayload(
            String payload, String apkSha256, Options options) {
        String manifestApkSha256 = normalizeSha256(readJsonString(payload, "apkSha256"));
        if (!apkSha256.equals(manifestApkSha256)) {
            fail("Manifest apkSha256 does not match the release APK.");
        }

        String actualVersion = readJsonString(payload, "version");
        if (!options.expectedVersion.isEmpty()) {
            if (!options.expectedVersion.equals(actualVersion)) {
                fail("Manifest version does not match expected version.");
            }
        }
        Matcher code = Pattern.compile("\"versionCode\"\\s*:\\s*([1-9][0-9]*)\\s*[,}]")
                .matcher(payload);
        if (!code.find()) {
            fail("Manifest versionCode must be a positive integer.");
        }
        int versionCode = Integer.parseInt(code.group(1));
        if (code.find()) {
            fail("Manifest versionCode must occur exactly once.");
        }

        String certSha256 = normalizeSha256(readJsonString(payload, "signingCertificateSha256"));
        if (!options.expectedCertSha256.isEmpty() &&
                !normalizeSha256(options.expectedCertSha256).equals(certSha256)) {
            fail("Manifest signingCertificateSha256 does not match expected certificate.");
        }

        String apkUrl = readJsonString(payload, "apkUrl");
        validateApkUrl(apkUrl);
        if (!options.expectedApkUrl.isEmpty() && !options.expectedApkUrl.equals(apkUrl)) {
            fail("Manifest apkUrl does not match expected release URL.");
        }

        String actualChannel = "";
        if (!options.expectedChannel.isEmpty()) {
            actualChannel = readJsonString(payload, "channel");
            requireEqualToken("channel", actualChannel, options.expectedChannel);
        }
        String actualStore = "";
        if (!options.expectedStore.isEmpty()) {
            actualStore = readJsonString(payload, "store");
            requireEqualToken("store", actualStore, options.expectedStore);
        }

        Instant expiresAt = Instant.parse(readJsonString(payload, "expiresAt"));
        if (!expiresAt.isAfter(Instant.now())) {
            fail("Signed update manifest is expired.");
        }
        if (expiresAt.isAfter(Instant.now().plusSeconds(14L * 24L * 60L * 60L))) {
            fail("Signed update manifest expiry is too far in the future.");
        }
        return new ManifestPayload(actualVersion, versionCode, actualChannel, actualStore,
                apkUrl, certSha256, expiresAt.toString());
    }

    private static void verifyApkIdentity(Options options, ManifestPayload manifest)
            throws Exception {
        Path signer = sdkBuildTool("lib/apksigner.jar");
        String certs = checkedCommand(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-jar", signer.toString(), "verify", "--verbose", "--print-certs",
                options.apk.toAbsolutePath().toString());
        String count = singleMatch(certs, "(?m)^Number of signers: ([0-9]+)\\r?$",
                "APK signer count");
        String cert = singleMatch(certs,
                "(?m)^Signer #\\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)\\r?$",
                "APK signing certificate");
        if (!"1".equals(count) || !manifest.certSha256.equals(normalizeSha256(cert))) {
            fail("Actual APK signer does not match the authenticated manifest certificate.");
        }

        String badging = checkedCommand(sdkBuildTool(isWindows() ? "aapt.exe" : "aapt")
                .toString(), "dump", "badging", options.apk.toAbsolutePath().toString());
        String identity = singleMatch(badging, "(?m)^package: (.+)\\r?$", "APK package");
        String packageName = singleMatch(identity, "(?:^| )name='([^']*)'", "APK package name");
        String version = singleMatch(identity, "(?:^| )versionName='([^']*)'", "APK version");
        String versionCode = singleMatch(identity, "(?:^| )versionCode='([0-9]+)'",
                "APK versionCode");
        if (!"org.adaway".equals(packageName)) {
            fail("Actual APK package must be org.adaway.");
        }
        if (!manifest.version.equals(version) ||
                manifest.versionCode != Long.parseLong(versionCode) ||
                Pattern.compile("versionCodeMajor='[1-9][0-9]*'").matcher(identity).find()) {
            fail("Actual APK version/versionCode does not match the authenticated manifest.");
        }
        if (Pattern.compile("(?m)^application-debuggable(?:\\s|$)").matcher(badging).find()) {
            fail("Actual APK is debuggable.");
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private static Path sdkBuildTool(String name) {
        for (String variable : List.of("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
            String root = System.getenv(variable);
            if (root != null && !root.isBlank()) {
                Path tool = Path.of(root, "build-tools", "36.0.0", name);
                if (Files.isRegularFile(tool)) {
                    return tool;
                }
            }
        }
        fail("Android build-tools 36.0.0 required: " + name +
                ". Set ANDROID_HOME or ANDROID_SDK_ROOT.");
        return null;
    }

    private static String singleMatch(String text, String regex, String label) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (!matcher.find()) {
            fail(label + " is missing or unrecognized.");
        }
        String value = matcher.group(1);
        if (matcher.find()) {
            fail(label + " is ambiguous.");
        }
        return value;
    }

    private static void writeReport(Options options, String apkSha256,
            ManifestPayload manifestPayload, int attestedArtifacts, String artifactCommit)
            throws IOException {
        if (options.report == null) {
            return;
        }

        Path absoluteReport = options.report.toAbsolutePath();
        Path parent = absoluteReport.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        StringBuilder report = new StringBuilder();
        report.append("# Release Artifact Verification Report\n\n");
        report.append("- Status: passed\n");
        report.append("- Artifact source commit: ").append(artifactCommit).append('\n');
        report.append("- Artifact source ref: refs/tags/")
                .append(releaseTagFromApkUrl(manifestPayload.apkUrl)).append('\n');
        report.append("- Artifact source workflow: ").append(options.repository)
                .append("/.github/workflows/fork-release-apk.yml\n");
        report.append("- Artifact provenance: ")
                .append(options.verifyAttestations ? "verified" : "not-verified").append('\n');
        report.append("- Metadata source commit: ")
                .append(options.verifyAttestations && !options.metadataCommit.isEmpty() ?
                        options.metadataCommit : artifactCommit).append('\n');
        report.append("- Metadata source ref: ")
                .append(options.metadataCommit.isEmpty() ? "refs/tags/" +
                        releaseTagFromApkUrl(manifestPayload.apkUrl) : options.metadataRef)
                .append('\n');
        report.append("- Metadata source workflow: ").append(options.repository)
                .append("/.github/workflows/")
                .append(options.metadataCommit.isEmpty() ? "fork-release-apk.yml" :
                        "refresh-update-manifest.yml").append('\n');
        report.append("- Tooling source commit: ").append(sourceCommit()).append('\n');
        report.append("- Generated at: ").append(Instant.now()).append('\n');
        report.append("- Repository: ").append(options.repository).append('\n');
        report.append("- Release tag: ")
                .append(releaseTagFromApkUrl(manifestPayload.apkUrl))
                .append('\n');
        report.append("- APK: ").append(baseName(options.apk)).append('\n');
        report.append("- Manifest: ").append(baseName(options.manifest)).append('\n');
        report.append("- SBOM: ").append(baseName(options.sbom)).append('\n');
        report.append("- APK SHA-256: ").append(apkSha256).append('\n');
        report.append("- APK identity: verified\n");
        report.append("- Package: org.adaway\n");
        report.append("- APK version: ").append(manifestPayload.version).append('\n');
        report.append("- APK versionCode: ").append(manifestPayload.versionCode).append('\n');
        report.append("- APK debuggable: false\n");
        report.append("- APK certificate SHA-256: ").append(manifestPayload.certSha256)
                .append('\n');
        report.append("- Expected version: ").append(orNotProvided(options.expectedVersion))
                .append('\n');
        report.append("- Manifest version: ").append(orNotProvided(manifestPayload.version))
                .append('\n');
        report.append("- Expected channel: ").append(orNotProvided(options.expectedChannel))
                .append('\n');
        report.append("- Manifest channel: ").append(orNotProvided(manifestPayload.channel))
                .append('\n');
        report.append("- Expected store: ").append(orNotProvided(options.expectedStore))
                .append('\n');
        report.append("- Manifest store: ").append(orNotProvided(manifestPayload.store))
                .append('\n');
        report.append("- Expected APK URL: ").append(orNotProvided(options.expectedApkUrl))
                .append('\n');
        report.append("- Manifest APK URL: ").append(manifestPayload.apkUrl).append('\n');
        report.append("- Expected certificate SHA-256: ")
                .append(options.expectedCertSha256.isEmpty() ? "not-provided" :
                        normalizeSha256(options.expectedCertSha256))
                .append('\n');
        report.append("- Manifest certificate SHA-256: ")
                .append(manifestPayload.certSha256)
                .append('\n');
        report.append("- Manifest expires at: ").append(manifestPayload.expiresAt).append('\n');
        report.append("- Checksum verification: passed\n");
        report.append("- Manifest signature: passed\n");
        report.append("- Manifest payload: passed\n");
        report.append("- Attestations: ")
                .append(options.verifyAttestations ? "verified" : "not-requested")
                .append('\n');
        report.append("- Attested artifacts: ").append(attestedArtifacts).append('\n');

        Files.writeString(absoluteReport, report.toString(), StandardCharsets.UTF_8);
        System.out.println("Release artifact verification report=" + absoluteReport);
    }

    private static String baseName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private static String orNotProvided(String value) {
        return value == null || value.trim().isEmpty() ? "not-provided" : value;
    }

    private static String sourceCommit() {
        String launcherSource = System.getProperty("jdk.launcher.sourcefile");
        if (launcherSource == null) {
            return "not-provided";
        }
        try {
            Path source = Path.of(launcherSource).toRealPath();
            String name = source.getFileName().toString();
            if (!"VerifyReleaseArtifacts.java".equals(name)) {
                return "not-provided";
            }
            // Require a tracked, unchanged verifier in its own checkout, not the caller's.
            Process process = gitAt(source, "rev-parse", "HEAD", "HEAD:./" + name).start();
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            String[] ids = output.split("\\R");
            if (process.waitFor() == 0 && ids.length == 2 && isSourceCommit(ids[0])
                    && isSourceCommit(ids[1]) && gitAt(source, "diff", "--quiet", "--no-ext-diff",
                    ids[0], "--", name).start().waitFor() == 0) {
                return ids[0].toLowerCase(Locale.ROOT);
            }
        } catch (IOException exception) {
            return "not-provided";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "not-provided";
        }
        return "not-provided";
    }

    private static ProcessBuilder gitAt(Path source, String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(source.getParent().toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        for (String variable : List.of("GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE")) {
            builder.environment().remove(variable);
        }
        return builder;
    }

    private static boolean isSourceCommit(String value) {
        return value != null && value.trim().matches("[0-9a-fA-F]{40}");
    }

    private static String releaseTagFromApkUrl(String apkUrl) {
        if (apkUrl == null || apkUrl.trim().isEmpty()) {
            return "not-provided";
        }
        URI uri = URI.create(apkUrl);
        String path = uri.getPath();
        int markerIndex = path.indexOf("/releases/download/");
        if (markerIndex < 0) {
            return "not-provided";
        }
        String rest = path.substring(markerIndex + "/releases/download/".length());
        int slashIndex = rest.indexOf('/');
        if (slashIndex <= 0) {
            return "not-provided";
        }
        return rest.substring(0, slashIndex);
    }

    private static void requireEqualToken(String name, String actual, String expected) {
        if (!actual.trim().equalsIgnoreCase(expected.trim())) {
            fail("Manifest " + name + " does not match expected value.");
        }
    }

    private static void validateApkUrl(String apkUrl) {
        URI parsed;
        try {
            parsed = URI.create(apkUrl);
        } catch (IllegalArgumentException exception) {
            fail("Manifest apkUrl is not a valid URI.");
            return;
        }

        String host = parsed.getHost() == null ? "" :
                parsed.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(parsed.getScheme())) {
            fail("Manifest apkUrl must use HTTPS.");
        }
        if (!ALLOWED_HOSTS.contains(host)) {
            fail("Manifest apkUrl host is not in the release allowlist.");
        }
        if (parsed.getPort() != -1 && parsed.getPort() != 443) {
            fail("Manifest apkUrl must use the default HTTPS port.");
        }
        String path = parsed.getPath() == null ? "" : parsed.getPath();
        if ("github.com".equals(host) && (!path.startsWith(GITHUB_RELEASE_PREFIX) ||
                !path.toLowerCase(Locale.ROOT).endsWith(".apk"))) {
            fail("Manifest apkUrl must point to the fork GitHub APK release.");
        }
        if (parsed.getUserInfo() != null || parsed.getRawFragment() != null) {
            fail("Manifest apkUrl must not include user info or a fragment.");
        }
    }

    private static String resolveArtifactCommit(Options options, ManifestPayload manifest)
            throws IOException, InterruptedException {
        String tag = releaseTagFromApkUrl(manifest.apkUrl);
        if (!tag.matches("v[0-9][0-9A-Za-z._-]*") ||
                !manifest.apkUrl.equals("https://github.com/" + options.repository +
                        "/releases/download/" + tag + "/" + baseName(options.apk)) ||
                !tag.equals("v" + manifest.version)) {
            fail("Artifact provenance requires an exact repository/tag/version/APK URL match.");
        }
        String ref = URLEncoder.encode("refs/tags/" + tag, StandardCharsets.UTF_8);
        String commit = checkedCommand(ghCommand(), "api", "repos/" + options.repository +
                "/commits/" + ref, "--jq", ".sha").trim();
        if (!isSourceCommit(commit)) {
            fail("Release tag target is unknown.");
        }
        return commit.toLowerCase(Locale.ROOT);
    }

    private static String ghCommand() {
        return System.getenv().getOrDefault("GH_CLI_PATH", "gh");
    }

    private static void verifyAttestation(Path artifact, Options options,
            ManifestPayload manifest, String artifactCommit, boolean metadata)
            throws IOException, InterruptedException {
        boolean refreshed = metadata && !options.metadataCommit.isEmpty();
        String commit = refreshed ? options.metadataCommit : artifactCommit;
        String ref = refreshed ? options.metadataRef :
                "refs/tags/" + releaseTagFromApkUrl(manifest.apkUrl);
        String workflow = options.repository + "/.github/workflows/" +
                (refreshed ? "refresh-update-manifest.yml" : "fork-release-apk.yml");
        String lastOutput = "";
        for (int attempt = 1; attempt <= ATTESTATION_VERIFY_ATTEMPTS; attempt++) {
            try {
                checkedCommand(ghCommand(), "attestation", "verify",
                        artifact.toAbsolutePath().toString(), "--repo", options.repository,
                        "--source-ref", ref, "--source-digest", commit,
                        "--signer-digest", commit,
                        // The exact identity pins both workflow and ref.
                        // gh forbids a second identity selector.
                        "--cert-identity", "https://github.com/" + workflow + "@" + ref,
                        "--predicate-type", "https://slsa.dev/provenance/v1",
                        "--deny-self-hosted-runners");
                return;
            } catch (IllegalArgumentException exception) {
                lastOutput = exception.getMessage();
            }
            if (attempt < ATTESTATION_VERIFY_ATTEMPTS) {
                Thread.sleep(ATTESTATION_RETRY_DELAY_MILLIS);
            }
        }
        fail("GitHub attestation verification failed for " + artifact + " after " +
                ATTESTATION_VERIFY_ATTEMPTS + " attempts.\n" + lastOutput);
    }

    private static String checkedCommand(String... command)
            throws IOException, InterruptedException {
        Path output = Files.createTempFile("adaway-verification-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(output.toFile()).start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                fail("Verification tool timed out: " + command[0]);
            }
            String text = Files.readString(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                fail("Verification tool failed: " + command[0] + "\n" + text);
            }
            return text;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
            Files.deleteIfExists(output);
        }
    }

    private static String readJsonString(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) {
            fail("JSON field is missing: " + field + ".");
        }
        int colon = json.indexOf(':', keyIndex + key.length());
        if (colon < 0) {
            fail("JSON field has no value: " + field + ".");
        }
        int quote = skipWhitespace(json, colon + 1);
        if (quote >= json.length() || json.charAt(quote) != '"') {
            fail("JSON field is not a string: " + field + ".");
        }

        StringBuilder out = new StringBuilder();
        for (int i = quote + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                return out.toString();
            }
            if (ch != '\\') {
                out.append(ch);
                continue;
            }
            if (++i >= json.length()) {
                fail("JSON string escape is incomplete: " + field + ".");
            }
            char escaped = json.charAt(i);
            switch (escaped) {
                case '"':
                case '\\':
                case '/':
                    out.append(escaped);
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    if (i + 4 >= json.length()) {
                        fail("JSON unicode escape is incomplete: " + field + ".");
                    }
                    out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                    i += 4;
                    break;
                default:
                    fail("JSON string escape is unsupported: \\" + escaped + ".");
            }
        }
        fail("JSON string is unterminated: " + field + ".");
        return "";
    }

    private static int skipWhitespace(String text, int index) {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static byte[] decodeBase64OrPem(String value, String expectedBlock) {
        byte[] decoded = Base64.getMimeDecoder().decode(value);
        String text = new String(decoded, StandardCharsets.US_ASCII);
        if (!text.contains("-----BEGIN ")) {
            return decoded;
        }

        Matcher matcher = PEM_BLOCK.matcher(text);
        while (matcher.find()) {
            if (matcher.group(1).equals(expectedBlock)) {
                return Base64.getMimeDecoder().decode(matcher.group(2));
            }
        }
        fail("Expected PEM block was not found: " + expectedBlock);
        return new byte[0];
    }

    private static String normalizeSha256(String value) {
        String normalized = value.replace(":", "")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            fail("SHA-256 value must be 64 hex characters.");
        }
        return normalized;
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    private static void requireReadableFile(Path path, String label) {
        if (!Files.isRegularFile(path)) {
            fail(label + " does not exist: " + path + ".");
        }
        try {
            if (Files.size(path) <= 0) {
                fail(label + " is empty: " + path + ".");
            }
        } catch (IOException exception) {
            fail("Failed to inspect " + label + ": " + path + ".");
        }
    }

    private static void fail(String message) {
        throw new IllegalArgumentException(message);
    }

    private static final class ManifestPayload {
        final String version;
        final int versionCode;
        final String channel;
        final String store;
        final String apkUrl;
        final String certSha256;
        final String expiresAt;

        ManifestPayload(String version, int versionCode, String channel, String store, String apkUrl,
                String certSha256, String expiresAt) {
            this.version = version;
            this.versionCode = versionCode;
            this.channel = channel;
            this.store = store;
            this.apkUrl = apkUrl;
            this.certSha256 = certSha256;
            this.expiresAt = expiresAt;
        }
    }

    private static final class Options {
        final Path apk;
        final Path apkSha256;
        final Path manifest;
        final Path manifestSha256;
        final Path sbom;
        final Path sbomSha256;
        final String publicKeyBase64;
        final String expectedVersion;
        final String expectedChannel;
        final String expectedStore;
        final String expectedApkUrl;
        final String expectedCertSha256;
        final String repository;
        final Path report;
        final boolean verifyAttestations;
        final String metadataCommit;
        final String metadataRef;

        private Options(Map<String, String> values) {
            apk = Path.of(required(values, "apk"));
            apkSha256 = Path.of(required(values, "apk-sha256"));
            manifest = Path.of(required(values, "manifest"));
            manifestSha256 = Path.of(required(values, "manifest-sha256"));
            sbom = Path.of(required(values, "sbom"));
            sbomSha256 = Path.of(required(values, "sbom-sha256"));
            publicKeyBase64 = required(values, "public-key-base64");
            expectedVersion = values.getOrDefault("expected-version", "");
            expectedChannel = values.getOrDefault("expected-channel", "");
            expectedStore = values.getOrDefault("expected-store", "adaway");
            expectedApkUrl = values.getOrDefault("expected-apk-url", "");
            expectedCertSha256 = values.getOrDefault("expected-cert-sha256", "");
            repository = values.getOrDefault("repo", "stevesolun/AdAway");
            String reportPath = values.getOrDefault("report", "").trim();
            report = reportPath.isEmpty() ? null : Path.of(reportPath);
            verifyAttestations = values.containsKey("verify-attestations");
            metadataCommit = values.getOrDefault("metadata-source-commit", "")
                    .toLowerCase(Locale.ROOT);
            metadataRef = values.getOrDefault("metadata-source-ref", "");
        }

        private void validateProvenanceOptions() {
            if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
                fail("Invalid repository.");
            }
            if ((!metadataCommit.isEmpty() || !metadataRef.isEmpty()) &&
                    (!verifyAttestations || !isSourceCommit(metadataCommit) ||
                    !metadataRef.matches("refs/heads/[A-Za-z0-9_./-]+"))) {
                fail("Refreshed metadata requires attestations, a 40-hex metadata-source-commit " +
                        "and a metadata-source-ref under refs/heads/.");
            }
        }

        static Options parse(String[] args) {
            Map<String, String> values = new HashMap<>();
            values.put("public-key-base64",
                    System.getenv().getOrDefault("UPDATE_MANIFEST_PUBLIC_KEY_BASE64", ""));

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("-h".equals(arg) || "--help".equals(arg)) {
                    usage();
                    System.exit(0);
                }
                if ("--verify-attestations".equals(arg)) {
                    values.put("verify-attestations", "true");
                    continue;
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
            return new Options(values);
        }

        private static String required(Map<String, String> values, String key) {
            String value = values.get(key);
            if (value == null || value.trim().isEmpty()) {
                usage();
                fail("Missing required argument: --" + key);
            }
            return value;
        }

        private static void usage() {
            System.err.println("Usage: verify-release-artifacts --apk PATH " +
                    "--apk-sha256 PATH --manifest PATH --manifest-sha256 PATH " +
                    "--sbom PATH --sbom-sha256 PATH --public-key-base64 BASE64");
            System.err.println();
            System.err.println("Optional:");
            System.err.println("  --expected-version VALUE");
            System.err.println("  --expected-channel VALUE");
            System.err.println("  --expected-store VALUE        Default: adaway");
            System.err.println("  --expected-apk-url URL");
            System.err.println("  --expected-cert-sha256 HEX");
            System.err.println("  --repo OWNER/REPO             Default: stevesolun/AdAway");
            System.err.println("  --report PATH                 Write a verification report");
            System.err.println("  --verify-attestations         Also run gh attestation verify");
            System.err.println("  --metadata-source-commit HEX  Pin refreshed metadata workflow commit");
            System.err.println("  --metadata-source-ref REF     Pin refreshed metadata branch ref");
            System.err.println("Requires Android build-tools 36.0.0 via ANDROID_HOME/ANDROID_SDK_ROOT.");
        }
    }
}
