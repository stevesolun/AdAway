package org.adaway.scripts;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class ReleaseReadinessScriptTest {
    private static final String RELEASE_APK = "AdAway_13.5.0.apk";
    private static final String RELEASE_SBOM = "adaway.cdx.json";
    private static final String RELEASE_TAG = "v13.5.0";
    private static final String OTHER_RELEASE_TAG = "v13.5.1";
    private static final String RELEASE_APK_SHA256 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String RELEASE_CERT_SHA256 =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    private static final String UX_PACKET_SHA256 =
            "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";

    @Test
    public void releaseReadinessFailsWhenPhysicalSmokeDidNotRun() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the release-readiness script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-readiness-fail");
        try {
            Path releaseReport = fixture.resolve("release-artifact-verification-report.md");
            Path smokeReport = fixture.resolve("release-smoke-report.md");
            Path uxReport = fixture.resolve("ux-signoff-report.md");
            Path licenseReport = fixture.resolve("license-boundary-report.md");
            Path readinessReport = fixture.resolve("release-readiness-report.md");
            writePassingReleaseArtifactReport(releaseReport);
            writeUtf8(smokeReport,
                    "# Release Smoke Report\n\n" +
                            "- Status: passed\n" +
                            "- Mode: identity-only\n" +
                            "- Physical device: not-run\n");
            writePassingUxReport(uxReport);
            writePassingLicenseReport(licenseReport);

            ProcessResult result = runPowerShell(powershell,
                    readinessCommand(releaseReport, smokeReport, uxReport, licenseReport,
                            readinessReport));

            assertTrue("Readiness must fail until physical-device smoke ran.",
                    result.exitCode != 0);
            assertTrue("Readiness failure must write the requested report.",
                    Files.isRegularFile(readinessReport));
            String report = readUtf8(readinessReport);
            assertTrue("Readiness report must explain the missing physical smoke proof.",
                    report.contains("# Release Readiness Report") &&
                            report.contains("- Status: failed") &&
                            report.contains("physical-device") &&
                            report.contains("verified-real-device"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void releaseReadinessFailsWhenSmokeIdentityDoesNotMatchArtifactReport()
            throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the release-readiness script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-readiness-mismatch");
        try {
            Path releaseReport = fixture.resolve("release-artifact-verification-report.md");
            Path smokeReport = fixture.resolve("release-smoke-report.md");
            Path uxReport = fixture.resolve("ux-signoff-report.md");
            Path licenseReport = fixture.resolve("license-boundary-report.md");
            Path readinessReport = fixture.resolve("release-readiness-report.md");
            writePassingReleaseArtifactReport(releaseReport);
            writePassingPhysicalSmokeReport(smokeReport,
                    "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                    RELEASE_CERT_SHA256);
            writePassingUxReport(uxReport);
            writePassingLicenseReport(licenseReport);

            ProcessResult result = runPowerShell(powershell,
                    readinessCommand(releaseReport, smokeReport, uxReport, licenseReport,
                            readinessReport));

            assertTrue("Readiness must fail when smoke and artifact APK hashes differ.",
                    result.exitCode != 0);
            String report = readUtf8(readinessReport);
            assertTrue("Readiness report must explain the APK identity mismatch.",
                    report.contains("- Status: failed") &&
                            report.contains("APK SHA-256") &&
                            report.contains("release artifact") &&
                            report.contains("physical smoke"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void releaseReadinessFailsWhenReleaseTagsDoNotMatch()
            throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the release-readiness script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-readiness-tag-mismatch");
        try {
            Path releaseReport = fixture.resolve("release-artifact-verification-report.md");
            Path smokeReport = fixture.resolve("release-smoke-report.md");
            Path uxReport = fixture.resolve("ux-signoff-report.md");
            Path licenseReport = fixture.resolve("license-boundary-report.md");
            Path readinessReport = fixture.resolve("release-readiness-report.md");
            writePassingReleaseArtifactReport(releaseReport, RELEASE_TAG);
            writePassingPhysicalSmokeReport(smokeReport, RELEASE_APK_SHA256,
                    RELEASE_CERT_SHA256, OTHER_RELEASE_TAG);
            writePassingUxReport(uxReport);
            writePassingLicenseReport(licenseReport);

            ProcessResult result = runPowerShell(powershell,
                    readinessCommand(releaseReport, smokeReport, uxReport, licenseReport,
                            readinessReport));

            assertTrue("Readiness must fail when release artifact and smoke reports name " +
                    "different release tags.", result.exitCode != 0);
            String report = readUtf8(readinessReport);
            assertTrue("Readiness report must explain the release-tag mismatch.",
                    report.contains("- Status: failed") &&
                            report.contains("Release tag") &&
                            report.contains("release artifact") &&
                            report.contains("physical smoke"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void releaseReadinessFailsWhenPhysicalSmokeProvenanceIsIncomplete()
            throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the release-readiness script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-readiness-sparse-smoke");
        try {
            Path releaseReport = fixture.resolve("release-artifact-verification-report.md");
            Path smokeReport = fixture.resolve("release-smoke-report.md");
            Path uxReport = fixture.resolve("ux-signoff-report.md");
            Path licenseReport = fixture.resolve("license-boundary-report.md");
            Path readinessReport = fixture.resolve("release-readiness-report.md");
            writePassingReleaseArtifactReport(releaseReport);
            writeSparsePhysicalSmokeReport(smokeReport, RELEASE_APK_SHA256,
                    RELEASE_CERT_SHA256);
            writePassingUxReport(uxReport);
            writePassingLicenseReport(licenseReport);

            ProcessResult result = runPowerShell(powershell,
                    readinessCommand(releaseReport, smokeReport, uxReport, licenseReport,
                            readinessReport));

            assertTrue("Readiness must fail for sparse physical-smoke pass markers.",
                    result.exitCode != 0);
            String report = readUtf8(readinessReport);
            assertTrue("Readiness report must explain missing physical-smoke provenance.",
                    report.contains("- Status: failed") &&
                            report.contains("Package") &&
                            report.contains("Signer certificate check") &&
                            report.contains("Device serial SHA-256"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void releaseReadinessFailsWhenReleaseArtifactProofIsSparse()
            throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the release-readiness script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-readiness-sparse-artifact");
        try {
            Path releaseReport = fixture.resolve("release-artifact-verification-report.md");
            Path smokeReport = fixture.resolve("release-smoke-report.md");
            Path uxReport = fixture.resolve("ux-signoff-report.md");
            Path licenseReport = fixture.resolve("license-boundary-report.md");
            Path readinessReport = fixture.resolve("release-readiness-report.md");
            writeSparseReleaseArtifactReport(releaseReport);
            writePassingPhysicalSmokeReport(smokeReport, RELEASE_APK_SHA256,
                    RELEASE_CERT_SHA256);
            writePassingUxReport(uxReport);
            writePassingLicenseReport(licenseReport);

            ProcessResult result = runPowerShell(powershell,
                    readinessCommand(releaseReport, smokeReport, uxReport, licenseReport,
                            readinessReport));

            assertTrue("Readiness must fail for sparse release artifact pass markers.",
                    result.exitCode != 0);
            String report = readUtf8(readinessReport);
            assertTrue("Readiness report must explain missing artifact verifier detail.",
                    report.contains("- Status: failed") &&
                            report.contains("Checksum verification") &&
                            report.contains("Manifest signature") &&
                            report.contains("Manifest payload") &&
                            report.contains("Expected certificate"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void releaseReadinessFailsWhenLicenseBoundaryIsSourceOnly() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the release-readiness script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-readiness-license-source-only");
        try {
            Path releaseReport = fixture.resolve("release-artifact-verification-report.md");
            Path smokeReport = fixture.resolve("release-smoke-report.md");
            Path uxReport = fixture.resolve("ux-signoff-report.md");
            Path licenseReport = fixture.resolve("license-boundary-report.md");
            Path readinessReport = fixture.resolve("release-readiness-report.md");
            writePassingReleaseArtifactReport(releaseReport);
            writePassingPhysicalSmokeReport(smokeReport, RELEASE_APK_SHA256,
                    RELEASE_CERT_SHA256);
            writePassingUxReport(uxReport);
            writeSourceOnlyLicenseReport(licenseReport);

            ProcessResult result = runPowerShell(powershell,
                    readinessCommand(releaseReport, smokeReport, uxReport, licenseReport,
                            readinessReport));

            assertTrue("Readiness must fail until artifact license-boundary proof is used.",
                    result.exitCode != 0);
            String report = readUtf8(readinessReport);
            assertTrue("Readiness report must explain missing strict artifact proof.",
                    report.contains("- Status: failed") &&
                            report.contains("Strict artifacts") &&
                            report.contains("APK") &&
                            report.contains("SBOM"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void releaseReadinessFailsWhenLicenseBoundaryArtifactDoesNotMatchReleaseArtifact()
            throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the release-readiness script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-readiness-license-artifact-mismatch");
        try {
            Path releaseReport = fixture.resolve("release-artifact-verification-report.md");
            Path smokeReport = fixture.resolve("release-smoke-report.md");
            Path uxReport = fixture.resolve("ux-signoff-report.md");
            Path licenseReport = fixture.resolve("license-boundary-report.md");
            Path readinessReport = fixture.resolve("release-readiness-report.md");
            writePassingReleaseArtifactReport(releaseReport);
            writePassingPhysicalSmokeReport(smokeReport, RELEASE_APK_SHA256,
                    RELEASE_CERT_SHA256);
            writePassingUxReport(uxReport);
            writeArtifactLicenseReport(licenseReport, "OtherAdAway.apk", "other.cdx.json");

            ProcessResult result = runPowerShell(powershell,
                    readinessCommand(releaseReport, smokeReport, uxReport, licenseReport,
                            readinessReport));

            assertTrue("Readiness must fail when artifact and license reports name different " +
                    "release artifacts.", result.exitCode != 0);
            String report = readUtf8(readinessReport);
            assertTrue("Readiness report must explain the APK/SBOM artifact mismatch.",
                    report.contains("- Status: failed") &&
                            report.contains("license boundary") &&
                            report.contains("APK") &&
                            report.contains("SBOM"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void releaseReadinessFailsWhenUxSignOffProvenanceIsIncomplete()
            throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the release-readiness script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-readiness-ux-provenance");
        try {
            Path releaseReport = fixture.resolve("release-artifact-verification-report.md");
            Path smokeReport = fixture.resolve("release-smoke-report.md");
            Path uxReport = fixture.resolve("ux-signoff-report.md");
            Path licenseReport = fixture.resolve("license-boundary-report.md");
            Path readinessReport = fixture.resolve("release-readiness-report.md");
            writePassingReleaseArtifactReport(releaseReport);
            writePassingPhysicalSmokeReport(smokeReport, RELEASE_APK_SHA256,
                    RELEASE_CERT_SHA256);
            writeUtf8(uxReport,
                    "# UX Sign-Off Report\n\n" +
                            "- Status: passed\n" +
                            "- Unchecked items: 0\n");
            writePassingLicenseReport(licenseReport);

            ProcessResult result = runPowerShell(powershell,
                    readinessCommand(releaseReport, smokeReport, uxReport, licenseReport,
                            readinessReport));

            assertTrue("Readiness must fail for anonymous or hand-written UX pass markers.",
                    result.exitCode != 0);
            String report = readUtf8(readinessReport);
            assertTrue("Readiness report must explain missing UX sign-off provenance.",
                    report.contains("- Status: failed") &&
                            report.contains("Reviewer") &&
                            report.contains("Review packet") &&
                            report.contains("Checked items"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void releaseReadinessFailsWhenUxPacketHashIsMissing()
            throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the release-readiness script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-readiness-ux-packet-hash");
        try {
            Path releaseReport = fixture.resolve("release-artifact-verification-report.md");
            Path smokeReport = fixture.resolve("release-smoke-report.md");
            Path uxReport = fixture.resolve("ux-signoff-report.md");
            Path licenseReport = fixture.resolve("license-boundary-report.md");
            Path readinessReport = fixture.resolve("release-readiness-report.md");
            writePassingReleaseArtifactReport(releaseReport);
            writePassingPhysicalSmokeReport(smokeReport, RELEASE_APK_SHA256,
                    RELEASE_CERT_SHA256);
            writeUxReportWithoutPacketHash(uxReport);
            writePassingLicenseReport(licenseReport);

            ProcessResult result = runPowerShell(powershell,
                    readinessCommand(releaseReport, smokeReport, uxReport, licenseReport,
                            readinessReport));

            assertTrue("Readiness must fail when UX sign-off lacks packet hash provenance.",
                    result.exitCode != 0);
            String report = readUtf8(readinessReport);
            assertTrue("Readiness report must explain missing UX packet hash provenance.",
                    report.contains("- Status: failed") &&
                            report.contains("Review packet SHA-256"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void releaseReadinessPassesWhenAllProofReportsPass() throws Exception {
        String powershell = findPowerShell();
        assumeTrue("PowerShell is required to exercise the release-readiness script.",
                powershell != null);

        Path fixture = Files.createTempDirectory("adaway-readiness-pass");
        try {
            Path releaseReport = fixture.resolve("release-artifact-verification-report.md");
            Path smokeReport = fixture.resolve("release-smoke-report.md");
            Path uxReport = fixture.resolve("ux-signoff-report.md");
            Path licenseReport = fixture.resolve("license-boundary-report.md");
            Path readinessReport = fixture.resolve("release-readiness-report.md");
            writePassingReleaseArtifactReport(releaseReport);
            writePassingPhysicalSmokeReport(smokeReport, RELEASE_APK_SHA256,
                    RELEASE_CERT_SHA256);
            writePassingUxReport(uxReport);
            writePassingLicenseReport(licenseReport);

            ProcessResult result = runPowerShell(powershell,
                    readinessCommand(releaseReport, smokeReport, uxReport, licenseReport,
                            readinessReport));

            assertEquals("Readiness must pass when every proof report is complete.\n" +
                    result.stderr, 0, result.exitCode);
            String report = readUtf8(readinessReport);
            assertTrue("Passing readiness report must summarize all proof reports.",
                    report.contains("# Release Readiness Report") &&
                            report.contains("- Status: passed") &&
                            report.contains("- Release tag: " + RELEASE_TAG) &&
                            report.contains("- APK: " + RELEASE_APK) &&
                            report.contains("- APK SHA-256: " + RELEASE_APK_SHA256) &&
                            report.contains("- SBOM: " + RELEASE_SBOM) &&
                            report.contains("- UX review packet SHA-256: " +
                                    UX_PACKET_SHA256) &&
                            report.contains("- Release artifact verification: passed") &&
                            report.contains("- Physical release smoke: passed") &&
                            report.contains("- Release identity consistency: passed") &&
                            report.contains("- UX sign-off: passed") &&
                            report.contains("- License boundary: passed") &&
                            containsSha256Field(report, "Release artifact report SHA-256") &&
                            containsSha256Field(report, "Physical smoke report SHA-256") &&
                            containsSha256Field(report, "UX sign-off report SHA-256") &&
                            containsSha256Field(report, "License boundary report SHA-256"));
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    public void readmeDocumentsReleaseReadinessVerifier() throws IOException {
        String readme = readUtf8(repoDir().resolve("README.md"));

        assertTrue("README must document the release-readiness verifier command.",
                readme.contains("verify-release-readiness.ps1") &&
                        readme.contains("-ReleaseArtifactReport") &&
                        readme.contains("-PhysicalSmokeReport") &&
                        readme.contains("-UxSignOffReport") &&
                        readme.contains("-LicenseBoundaryReport") &&
                        readme.contains("same release tag") &&
                        readme.contains("same APK") &&
                        readme.contains("Release tag") &&
                        readme.contains("APK SHA-256") &&
                        readme.contains("Package") &&
                        readme.contains("Signer certificate check") &&
                        readme.contains("Device serial SHA-256") &&
                        readme.contains("Checksum verification") &&
                        readme.contains("Manifest signature") &&
                        readme.contains("Manifest payload") &&
                        readme.contains("Expected certificate") &&
                        readme.contains("artifact license-boundary") &&
                        readme.contains("same APK and SBOM") &&
                        readme.contains("UX sign-off report") &&
                        readme.contains("reviewer") &&
                        readme.contains("review packet") &&
                        readme.contains("Review packet SHA-256") &&
                        readme.contains("Release artifact report SHA-256") &&
                        readme.contains("Physical smoke report SHA-256") &&
                        readme.contains("UX sign-off report SHA-256") &&
                        readme.contains("License boundary report SHA-256") &&
                        readme.contains("Strict artifacts") &&
                        readme.contains("release-readiness-report.md"));
    }

    private static boolean containsSha256Field(String report, String fieldName) {
        return Pattern.compile("(?m)^- " + Pattern.quote(fieldName) + ": [0-9a-f]{64}$")
                .matcher(report)
                .find();
    }

    private static String readinessCommand(Path releaseReport, Path smokeReport,
            Path uxReport, Path licenseReport, Path readinessReport) {
        return "$ErrorActionPreference = 'Stop';" +
                "& " + quote(repoDir().resolve("scripts/verify-release-readiness.ps1")) +
                " -ReleaseArtifactReport " + quote(releaseReport) +
                " -PhysicalSmokeReport " + quote(smokeReport) +
                " -UxSignOffReport " + quote(uxReport) +
                " -LicenseBoundaryReport " + quote(licenseReport) +
                " -ReportPath " + quote(readinessReport) + ";";
    }

    private static void writePassingReleaseArtifactReport(Path path) throws IOException {
        writePassingReleaseArtifactReport(path, RELEASE_TAG);
    }

    private static void writePassingReleaseArtifactReport(Path path, String releaseTag)
            throws IOException {
        writeUtf8(path,
                "# Release Artifact Verification Report\n\n" +
                        "- Status: passed\n" +
                        "- Release tag: " + releaseTag + "\n" +
                        "- APK: " + RELEASE_APK + "\n" +
                        "- SBOM: " + RELEASE_SBOM + "\n" +
                        "- APK SHA-256: " + RELEASE_APK_SHA256 + "\n" +
                        "- Expected certificate SHA-256: " + RELEASE_CERT_SHA256 + "\n" +
                        "- Manifest certificate SHA-256: " + RELEASE_CERT_SHA256 + "\n" +
                        "- Checksum verification: passed\n" +
                        "- Manifest signature: passed\n" +
                        "- Manifest payload: passed\n" +
                        "- Attestations: verified\n" +
                        "- Attested artifacts: 6\n");
    }

    private static void writeSparseReleaseArtifactReport(Path path) throws IOException {
        writeUtf8(path,
                "# Release Artifact Verification Report\n\n" +
                        "- Status: passed\n" +
                        "- Release tag: " + RELEASE_TAG + "\n" +
                        "- APK: " + RELEASE_APK + "\n" +
                        "- SBOM: " + RELEASE_SBOM + "\n" +
                        "- APK SHA-256: " + RELEASE_APK_SHA256 + "\n" +
                        "- Manifest certificate SHA-256: " + RELEASE_CERT_SHA256 + "\n" +
                        "- Attestations: verified\n" +
                        "- Attested artifacts: 6\n");
    }

    private static void writePassingPhysicalSmokeReport(Path path, String apkSha256,
            String certSha256) throws IOException {
        writePassingPhysicalSmokeReport(path, apkSha256, certSha256, RELEASE_TAG);
    }

    private static void writePassingPhysicalSmokeReport(Path path, String apkSha256,
            String certSha256, String releaseTag) throws IOException {
        writeUtf8(path,
                "# Release Smoke Report\n\n" +
                        "- Status: passed\n" +
                        "- Mode: physical-device\n" +
                        "- Release tag: " + releaseTag + "\n" +
                        "- APK: " + RELEASE_APK + "\n" +
                        "- APK SHA-256: " + apkSha256 + "\n" +
                        "- Package: org.adaway\n" +
                        "- Signer certificate check: True\n" +
                        "- Signer certificate SHA-256: " + certSha256 + "\n" +
                        "- Physical device: verified-real-device\n" +
                        "- Device serial SHA-256: " + RELEASE_APK_SHA256 + "\n" +
                        "- Launch pid observed: 4242\n");
    }

    private static void writeSparsePhysicalSmokeReport(Path path, String apkSha256,
            String certSha256) throws IOException {
        writeUtf8(path,
                "# Release Smoke Report\n\n" +
                        "- Status: passed\n" +
                        "- Mode: physical-device\n" +
                        "- Release tag: " + RELEASE_TAG + "\n" +
                        "- APK: " + RELEASE_APK + "\n" +
                        "- APK SHA-256: " + apkSha256 + "\n" +
                        "- Signer certificate SHA-256: " + certSha256 + "\n" +
                        "- Physical device: verified-real-device\n" +
                        "- Launch pid observed: 4242\n");
    }

    private static void writePassingUxReport(Path path) throws IOException {
        writeUtf8(path,
                "# UX Sign-Off Report\n\n" +
                        "- Status: passed\n" +
                        "- Reviewer: QA Lead\n" +
                        "- Review packet: ux-matrix-review.md\n" +
                        "- Review packet SHA-256: " + UX_PACKET_SHA256 + "\n" +
                        "- Checked items: 42\n" +
                        "- Unchecked items: 0\n" +
                        "- Issues: 0\n");
    }

    private static void writeUxReportWithoutPacketHash(Path path) throws IOException {
        writeUtf8(path,
                "# UX Sign-Off Report\n\n" +
                        "- Status: passed\n" +
                        "- Reviewer: QA Lead\n" +
                        "- Review packet: ux-matrix-review.md\n" +
                        "- Checked items: 42\n" +
                        "- Unchecked items: 0\n" +
                        "- Issues: 0\n");
    }

    private static void writePassingLicenseReport(Path path) throws IOException {
        writeArtifactLicenseReport(path, RELEASE_APK, RELEASE_SBOM);
    }

    private static void writeArtifactLicenseReport(Path path, String apk, String sbom)
            throws IOException {
        writeUtf8(path,
                "# License Boundary Report\n\n" +
                        "- Status: passed\n" +
                        "- Source mode: GitTracked\n" +
                        "- Strict source archive: true\n" +
                        "- Strict artifacts: true\n" +
                        "- APK: " + apk + "\n" +
                        "- SBOM: " + sbom + "\n" +
                        "- MIT release status: blocked until GPL-derived material is cleared\n" +
                        "- Issues: 0\n");
    }

    private static void writeSourceOnlyLicenseReport(Path path) throws IOException {
        writeUtf8(path,
                "# License Boundary Report\n\n" +
                        "- Status: passed\n" +
                        "- Source mode: WorkingTree\n" +
                        "- Strict source archive: false\n" +
                        "- Strict artifacts: false\n" +
                        "- APK: not-provided\n" +
                        "- SBOM: not-provided\n" +
                        "- MIT release status: blocked until GPL-derived material is cleared\n" +
                        "- Issues: 0\n");
    }

    private static ProcessResult runPowerShell(String powershell, String command)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(powershell, "-NoProfile", "-ExecutionPolicy",
                "Bypass", "-Command", command)
                .directory(repoDir().toFile())
                .start();
        if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Timed out running PowerShell command.");
        }
        String stdout;
        String stderr;
        try (java.io.InputStream output = process.getInputStream();
                java.io.InputStream error = process.getErrorStream()) {
            stdout = new String(output.readAllBytes(), StandardCharsets.UTF_8);
            stderr = new String(error.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private static String findPowerShell() {
        for (String candidate : new String[]{"pwsh", "powershell", "powershell.exe"}) {
            try {
                Process process = new ProcessBuilder(candidate, "-NoProfile", "-Command",
                        "$PSVersionTable.PSVersion.Major").start();
                if (process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) &&
                        process.exitValue() == 0) {
                    return candidate;
                }
            } catch (IOException exception) {
                // Try the next executable name.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static Path repoDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.isDirectory(cwd.resolve("app/src/test"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        return parent != null && cwd.getFileName().toString().equals("app") ? parent : cwd;
    }

    private static String quote(Path path) {
        return "'" + path.toString().replace("'", "''") + "'";
    }

    private static String readUtf8(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void writeUtf8(Path path, String text) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to delete " + path, exception);
                }
            });
        }
    }

    private static final class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
