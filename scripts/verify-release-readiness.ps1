param(
    [string] $ReleaseArtifactReport = "release-artifacts/verification-report.md",
    [string] $PhysicalSmokeReport = "release-smoke/release-smoke-report.md",
    [string] $UxSignOffReport = "app/build/reports/ux-matrix/ux-signoff-report.md",
    [string] $LicenseBoundaryReport = `
            "app/build/reports/license-boundary/license-boundary-report.md",
    [string] $ReportPath = "release-readiness-report.md"
)

$ErrorActionPreference = "Stop"

function Resolve-RepoPath([string] $path) {
    if ([System.IO.Path]::IsPathRooted($path)) {
        return $path
    }
    return (Join-Path (Get-Location) $path)
}

function Read-RequiredReport(
    [System.Collections.Generic.List[string]] $issues,
    [string] $label,
    [string] $path
) {
    $resolved = Resolve-RepoPath $path
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        $issues.Add("$label report is missing: $path")
        return ""
    }
    $content = Get-Content -Raw -LiteralPath $resolved
    if ([string]::IsNullOrWhiteSpace($content)) {
        $issues.Add("$label report is empty: $path")
    }
    return $content
}

function Test-ReportMarkers(
    [System.Collections.Generic.List[string]] $issues,
    [string] $label,
    [string] $content,
    [string[]] $requiredMarkers
) {
    if ([string]::IsNullOrWhiteSpace($content)) {
        return $false
    }

    $passed = $true
    foreach ($marker in $requiredMarkers) {
        if ($content -notlike "*$marker*") {
            $issues.Add("$label report must contain '$marker'.")
            $passed = $false
        }
    }
    return $passed
}

function Format-Status([bool] $passed) {
    if ($passed) {
        return "passed"
    }
    return "failed"
}

function Normalize-Sha256([string] $value) {
    return ($value -replace "[:\s]", "").ToLowerInvariant()
}

function Get-ReportSha256([string] $path) {
    $resolved = Resolve-RepoPath $path
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        return "not-provided"
    }
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $resolved).Hash.ToLowerInvariant()
}

function Get-ReportField(
    [System.Collections.Generic.List[string]] $issues,
    [string] $label,
    [string] $content,
    [string] $fieldName
) {
    if ([string]::IsNullOrWhiteSpace($content)) {
        return ""
    }

    $pattern = "(?m)^-\s*" + [Regex]::Escape($fieldName) + ":\s*(.+?)\s*$"
    $match = [Regex]::Match($content, $pattern)
    if (-not $match.Success) {
        $issues.Add("$label report must contain '$fieldName'.")
        return ""
    }
    return $match.Groups[1].Value.Trim()
}

function Get-OptionalReportField(
    [string] $content,
    [string] $fieldName
) {
    if ([string]::IsNullOrWhiteSpace($content)) {
        return "not-provided"
    }

    $pattern = "(?m)^-\s*" + [Regex]::Escape($fieldName) + ":\s*(.+?)\s*$"
    $match = [Regex]::Match($content, $pattern)
    if (-not $match.Success) {
        return "not-provided"
    }
    return $match.Groups[1].Value.Trim()
}

function Get-NormalizedOptionalReportSha256(
    [string] $content,
    [string] $fieldName
) {
    return Normalize-Sha256 (Get-OptionalReportField $content $fieldName)
}

function Test-ReleaseIdentity(
    [System.Collections.Generic.List[string]] $issues,
    [string] $releaseArtifactText,
    [string] $physicalSmokeText
) {
    if ([string]::IsNullOrWhiteSpace($releaseArtifactText) -or
            [string]::IsNullOrWhiteSpace($physicalSmokeText)) {
        return $false
    }

    $passed = $true
    $artifactReleaseTag = Get-ReportField $issues "Release artifact verification" `
        $releaseArtifactText "Release tag"
    $smokeReleaseTag = Get-ReportField $issues "Physical release smoke" `
        $physicalSmokeText "Release tag"
    if ($artifactReleaseTag -notmatch "^v[^/\s]+$") {
        $issues.Add("release artifact report must include a release tag such as v13.5.0.")
        $passed = $false
    }
    if ($smokeReleaseTag -notmatch "^v[^/\s]+$") {
        $issues.Add("physical smoke report must include a release tag such as v13.5.0.")
        $passed = $false
    }
    if ($artifactReleaseTag -match "^v[^/\s]+$" -and
            $smokeReleaseTag -match "^v[^/\s]+$" -and
            $artifactReleaseTag -ne $smokeReleaseTag) {
        $issues.Add("release artifact Release tag '$artifactReleaseTag' does not match " +
                "physical smoke Release tag '$smokeReleaseTag'.")
        $passed = $false
    }

    $artifactApk = Get-ReportField $issues "Release artifact verification" `
        $releaseArtifactText "APK"
    $smokeApk = Get-ReportField $issues "Physical release smoke" $physicalSmokeText "APK"
    if (-not [string]::IsNullOrWhiteSpace($artifactApk) -and
            -not [string]::IsNullOrWhiteSpace($smokeApk) -and
            $artifactApk -ne $smokeApk) {
        $issues.Add("release artifact APK '$artifactApk' does not match physical smoke APK " +
                "'$smokeApk'.")
        $passed = $false
    }

    $artifactApkSha256 = Normalize-Sha256 (Get-ReportField $issues `
        "Release artifact verification" $releaseArtifactText "APK SHA-256")
    $smokeApkSha256 = Normalize-Sha256 (Get-ReportField $issues `
        "Physical release smoke" $physicalSmokeText "APK SHA-256")
    if (-not [string]::IsNullOrWhiteSpace($artifactApkSha256) -and
            -not [string]::IsNullOrWhiteSpace($smokeApkSha256) -and
            $artifactApkSha256 -ne $smokeApkSha256) {
        $issues.Add("release artifact APK SHA-256 '$artifactApkSha256' does not match " +
                "physical smoke APK SHA-256 '$smokeApkSha256'.")
        $passed = $false
    }

    $artifactCertSha256 = Normalize-Sha256 (Get-ReportField $issues `
        "Release artifact verification" $releaseArtifactText "Manifest certificate SHA-256")
    $smokeCertSha256 = Get-ReportField $issues "Physical release smoke" `
        $physicalSmokeText "Signer certificate SHA-256"
    if ($smokeCertSha256 -eq "not-checked") {
        $issues.Add("physical smoke report must include a checked signer certificate SHA-256.")
        $passed = $false
    } else {
        $normalizedSmokeCert = Normalize-Sha256 $smokeCertSha256
        if (-not [string]::IsNullOrWhiteSpace($artifactCertSha256) -and
                -not [string]::IsNullOrWhiteSpace($normalizedSmokeCert) -and
                $artifactCertSha256 -ne $normalizedSmokeCert) {
            $issues.Add("release artifact signer certificate '$artifactCertSha256' does not " +
                    "match physical smoke signer certificate '$normalizedSmokeCert'.")
            $passed = $false
        }
    }

    return $passed
}

function Test-PhysicalSmokeEvidence(
    [System.Collections.Generic.List[string]] $issues,
    [string] $physicalSmokeText
) {
    if ([string]::IsNullOrWhiteSpace($physicalSmokeText)) {
        return $false
    }

    $passed = $true
    $packageName = Get-ReportField $issues "Physical release smoke" $physicalSmokeText "Package"
    if ([string]::IsNullOrWhiteSpace($packageName) -or $packageName -eq "not-provided") {
        $issues.Add("physical smoke report must include the checked package name.")
        $passed = $false
    }

    $signerCheck = Get-ReportField $issues "Physical release smoke" `
        $physicalSmokeText "Signer certificate check"
    if (-not $signerCheck.Equals("true", [System.StringComparison]::OrdinalIgnoreCase)) {
        $issues.Add("physical smoke report must have Signer certificate check: True.")
        $passed = $false
    }

    $deviceSerialHash = Normalize-Sha256 (Get-ReportField $issues "Physical release smoke" `
            $physicalSmokeText "Device serial SHA-256")
    if ($deviceSerialHash -notmatch "^[0-9a-f]{64}$") {
        $issues.Add("physical smoke report must include a SHA-256-shaped device serial hash.")
        $passed = $false
    }

    $launchPid = Get-ReportField $issues "Physical release smoke" `
        $physicalSmokeText "Launch pid observed"
    $parsedPid = 0
    if (-not [int]::TryParse($launchPid, [ref] $parsedPid) -or $parsedPid -le 0) {
        $issues.Add("physical smoke report must include a positive launch pid.")
        $passed = $false
    }

    return $passed
}

function Test-ReleaseArtifactEvidence(
    [System.Collections.Generic.List[string]] $issues,
    [string] $releaseArtifactText
) {
    if ([string]::IsNullOrWhiteSpace($releaseArtifactText)) {
        return $false
    }

    $passed = $true
    $expectedCertSha256 = Get-ReportField $issues "Release artifact verification" `
        $releaseArtifactText "Expected certificate SHA-256"
    if ([string]::IsNullOrWhiteSpace($expectedCertSha256) -or
            $expectedCertSha256 -eq "not-provided") {
        $issues.Add("release artifact report must include a checked expected certificate " +
                "SHA-256.")
        $passed = $false
    }

    $manifestCertSha256 = Get-ReportField $issues "Release artifact verification" `
        $releaseArtifactText "Manifest certificate SHA-256"
    $normalizedExpectedCert = Normalize-Sha256 $expectedCertSha256
    $normalizedManifestCert = Normalize-Sha256 $manifestCertSha256
    if (-not [string]::IsNullOrWhiteSpace($normalizedExpectedCert) -and
            -not [string]::IsNullOrWhiteSpace($normalizedManifestCert) -and
            $expectedCertSha256 -ne "not-provided" -and
            $normalizedExpectedCert -ne $normalizedManifestCert) {
        $issues.Add("release artifact expected certificate '$normalizedExpectedCert' does " +
                "not match manifest certificate '$normalizedManifestCert'.")
        $passed = $false
    }

    return $passed
}

function Test-LicenseBoundaryReleaseArtifact(
    [System.Collections.Generic.List[string]] $issues,
    [string] $licenseBoundaryText,
    [string] $releaseArtifactText
) {
    if ([string]::IsNullOrWhiteSpace($licenseBoundaryText)) {
        return $false
    }

    $passed = $true
    $sourceMode = Get-ReportField $issues "License boundary" `
        $licenseBoundaryText "Source mode"
    if ($sourceMode -ne "GitTracked") {
        $issues.Add("license boundary report must use Source mode: GitTracked for release " +
                "readiness.")
        $passed = $false
    }

    $strictSourceArchive = Get-ReportField $issues "License boundary" `
        $licenseBoundaryText "Strict source archive"
    if ($strictSourceArchive -ne "true") {
        $issues.Add("license boundary report must use Strict source archive: true.")
        $passed = $false
    }

    $strictArtifacts = Get-ReportField $issues "License boundary" `
        $licenseBoundaryText "Strict artifacts"
    if ($strictArtifacts -ne "true") {
        $issues.Add("license boundary report must use Strict artifacts: true.")
        $passed = $false
    }

    $licenseApk = Get-ReportField $issues "License boundary" $licenseBoundaryText "APK"
    if ([string]::IsNullOrWhiteSpace($licenseApk) -or $licenseApk -eq "not-provided") {
        $issues.Add("license boundary report must include the release APK artifact name.")
        $passed = $false
    }

    $licenseSbom = Get-ReportField $issues "License boundary" $licenseBoundaryText "SBOM"
    if ([string]::IsNullOrWhiteSpace($licenseSbom) -or $licenseSbom -eq "not-provided") {
        $issues.Add("license boundary report must include the release SBOM artifact name.")
        $passed = $false
    }

    if (-not [string]::IsNullOrWhiteSpace($releaseArtifactText)) {
        $artifactApk = Get-ReportField $issues "Release artifact verification" `
            $releaseArtifactText "APK"
        if (-not [string]::IsNullOrWhiteSpace($artifactApk) -and
                -not [string]::IsNullOrWhiteSpace($licenseApk) -and
                $artifactApk -ne $licenseApk) {
            $issues.Add("license boundary APK '$licenseApk' does not match release " +
                    "artifact APK '$artifactApk'.")
            $passed = $false
        }

        $artifactSbom = Get-ReportField $issues "Release artifact verification" `
            $releaseArtifactText "SBOM"
        if (-not [string]::IsNullOrWhiteSpace($artifactSbom) -and
                -not [string]::IsNullOrWhiteSpace($licenseSbom) -and
                $artifactSbom -ne $licenseSbom) {
            $issues.Add("license boundary SBOM '$licenseSbom' does not match release " +
                    "artifact SBOM '$artifactSbom'.")
            $passed = $false
        }
    }

    return $passed
}

function Test-UxSignOffEvidence(
    [System.Collections.Generic.List[string]] $issues,
    [string] $uxSignOffText
) {
    if ([string]::IsNullOrWhiteSpace($uxSignOffText)) {
        return $false
    }

    $passed = $true
    $reviewer = Get-ReportField $issues "UX sign-off" $uxSignOffText "Reviewer"
    if ([string]::IsNullOrWhiteSpace($reviewer) -or $reviewer -eq "not-provided") {
        $issues.Add("UX sign-off report must include a reviewer identity.")
        $passed = $false
    }

    $reviewPacket = Get-ReportField $issues "UX sign-off" $uxSignOffText "Review packet"
    if ([string]::IsNullOrWhiteSpace($reviewPacket) -or $reviewPacket -eq "not-provided") {
        $issues.Add("UX sign-off report must include the reviewed packet name.")
        $passed = $false
    }

    $reviewPacketSha256 = Normalize-Sha256 (Get-ReportField $issues "UX sign-off" `
            $uxSignOffText "Review packet SHA-256")
    if ($reviewPacketSha256 -notmatch "^[0-9a-f]{64}$") {
        $issues.Add("UX sign-off report must include a SHA-256-shaped review packet hash.")
        $passed = $false
    }

    $checkedItems = Get-ReportField $issues "UX sign-off" $uxSignOffText "Checked items"
    $checkedCount = 0
    if (-not [int]::TryParse($checkedItems, [ref] $checkedCount) -or $checkedCount -le 0) {
        $issues.Add("UX sign-off report must include a positive checked item count.")
        $passed = $false
    }

    return $passed
}

function Write-ReadinessReport(
    [string] $status,
    [bool] $releaseArtifactPassed,
    [bool] $physicalSmokePassed,
    [bool] $releaseIdentityPassed,
    [bool] $uxSignOffPassed,
    [bool] $licenseBoundaryPassed,
    [System.Collections.Generic.List[string]] $issues
) {
    $resolvedReport = [System.IO.Path]::GetFullPath((Resolve-RepoPath $ReportPath))
    $reportDirectory = Split-Path -Parent $resolvedReport
    if (-not [string]::IsNullOrWhiteSpace($reportDirectory)) {
        New-Item -ItemType Directory -Force $reportDirectory | Out-Null
    }

    $releaseTag = Get-OptionalReportField $releaseArtifactText "Release tag"
    $releaseApk = Get-OptionalReportField $releaseArtifactText "APK"
    $releaseApkSha256 = Get-NormalizedOptionalReportSha256 $releaseArtifactText "APK SHA-256"
    $releaseSbom = Get-OptionalReportField $releaseArtifactText "SBOM"
    $uxPacketSha256 = Get-NormalizedOptionalReportSha256 `
        $uxSignOffText "Review packet SHA-256"

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Release Readiness Report")
    $lines.Add("")
    $lines.Add("- Status: $status")
    $lines.Add("- Release tag: $releaseTag")
    $lines.Add("- APK: $releaseApk")
    $lines.Add("- APK SHA-256: $releaseApkSha256")
    $lines.Add("- SBOM: $releaseSbom")
    $lines.Add("- UX review packet SHA-256: $uxPacketSha256")
    $lines.Add("- Release artifact verification: $(Format-Status $releaseArtifactPassed)")
    $lines.Add("- Physical release smoke: $(Format-Status $physicalSmokePassed)")
    $lines.Add("- Release identity consistency: $(Format-Status $releaseIdentityPassed)")
    $lines.Add("- UX sign-off: $(Format-Status $uxSignOffPassed)")
    $lines.Add("- License boundary: $(Format-Status $licenseBoundaryPassed)")
    $lines.Add("- Release artifact report SHA-256: $(Get-ReportSha256 $ReleaseArtifactReport)")
    $lines.Add("- Physical smoke report SHA-256: $(Get-ReportSha256 $PhysicalSmokeReport)")
    $lines.Add("- UX sign-off report SHA-256: $(Get-ReportSha256 $UxSignOffReport)")
    $lines.Add("- License boundary report SHA-256: $(Get-ReportSha256 $LicenseBoundaryReport)")
    $lines.Add("- Issues: $($issues.Count)")
    if ($issues.Count -gt 0) {
        $lines.Add("")
        $lines.Add("## Issues")
        foreach ($issue in $issues | Select-Object -First 200) {
            $lines.Add("- $issue")
        }
    }

    Set-Content -LiteralPath $resolvedReport -Value $lines -Encoding UTF8
    Write-Host "Release readiness report=$resolvedReport"
}

$issues = New-Object System.Collections.Generic.List[string]

$releaseArtifactText = Read-RequiredReport $issues "Release artifact verification" `
    $ReleaseArtifactReport
$physicalSmokeText = Read-RequiredReport $issues "Physical release smoke" $PhysicalSmokeReport
$uxSignOffText = Read-RequiredReport $issues "UX sign-off" $UxSignOffReport
$licenseBoundaryText = Read-RequiredReport $issues "License boundary" $LicenseBoundaryReport

$releaseArtifactPassed = Test-ReportMarkers $issues "Release artifact verification" `
    $releaseArtifactText @(
        "# Release Artifact Verification Report",
        "- Status: passed",
        "- Release tag:",
        "- APK:",
        "- SBOM:",
        "- APK SHA-256:",
        "- Expected certificate SHA-256:",
        "- Manifest certificate SHA-256:",
        "- Checksum verification: passed",
        "- Manifest signature: passed",
        "- Manifest payload: passed",
        "- Attestations: verified",
        "- Attested artifacts: 6"
    )
$releaseArtifactPassed = $releaseArtifactPassed -and
        (Test-ReleaseArtifactEvidence $issues $releaseArtifactText)
$physicalSmokePassed = Test-ReportMarkers $issues "Physical release smoke" $physicalSmokeText @(
        "# Release Smoke Report",
        "- Status: passed",
        "- Mode: physical-device",
        "- Release tag:",
        "- APK:",
        "- APK SHA-256:",
        "- Package:",
        "- Signer certificate check:",
        "- Signer certificate SHA-256:",
        "- Physical device: verified-real-device",
        "- Device serial SHA-256:",
        "- Launch pid observed:"
    )
$physicalSmokePassed = $physicalSmokePassed -and
        (Test-PhysicalSmokeEvidence $issues $physicalSmokeText)
$uxSignOffPassed = Test-ReportMarkers $issues "UX sign-off" $uxSignOffText @(
        "# UX Sign-Off Report",
        "- Status: passed",
        "- Reviewer:",
        "- Review packet:",
        "- Review packet SHA-256:",
        "- Checked items:",
        "- Unchecked items: 0",
        "- Issues: 0"
    )
$uxSignOffPassed = $uxSignOffPassed -and (Test-UxSignOffEvidence $issues $uxSignOffText)
$licenseBoundaryPassed = Test-ReportMarkers $issues "License boundary" $licenseBoundaryText @(
        "# License Boundary Report",
        "- Status: passed",
        "- Source mode:",
        "- Strict source archive:",
        "- Strict artifacts:",
        "- APK:",
        "- SBOM:",
        "- MIT release status: blocked until GPL-derived material is cleared",
        "- Issues: 0"
    )
$licenseBoundaryPassed = $licenseBoundaryPassed -and
        (Test-LicenseBoundaryReleaseArtifact $issues $licenseBoundaryText $releaseArtifactText)
$releaseIdentityPassed = Test-ReleaseIdentity $issues $releaseArtifactText $physicalSmokeText

if ($issues.Count -gt 0) {
    Write-ReadinessReport "failed" $releaseArtifactPassed $physicalSmokePassed `
        $releaseIdentityPassed $uxSignOffPassed $licenseBoundaryPassed $issues
    foreach ($issue in $issues) {
        [Console]::Error.WriteLine($issue)
    }
    exit 1
}

Write-ReadinessReport "passed" $releaseArtifactPassed $physicalSmokePassed `
    $releaseIdentityPassed $uxSignOffPassed $licenseBoundaryPassed $issues
Write-Host "Release readiness verification passed."
