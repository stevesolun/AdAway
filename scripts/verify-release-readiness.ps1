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

function Write-ReadinessReport(
    [string] $status,
    [bool] $releaseArtifactPassed,
    [bool] $physicalSmokePassed,
    [bool] $uxSignOffPassed,
    [bool] $licenseBoundaryPassed,
    [System.Collections.Generic.List[string]] $issues
) {
    $resolvedReport = [System.IO.Path]::GetFullPath((Resolve-RepoPath $ReportPath))
    $reportDirectory = Split-Path -Parent $resolvedReport
    if (-not [string]::IsNullOrWhiteSpace($reportDirectory)) {
        New-Item -ItemType Directory -Force $reportDirectory | Out-Null
    }

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Release Readiness Report")
    $lines.Add("")
    $lines.Add("- Status: $status")
    $lines.Add("- Release artifact verification: $(Format-Status $releaseArtifactPassed)")
    $lines.Add("- Physical release smoke: $(Format-Status $physicalSmokePassed)")
    $lines.Add("- UX sign-off: $(Format-Status $uxSignOffPassed)")
    $lines.Add("- License boundary: $(Format-Status $licenseBoundaryPassed)")
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
        "- Attestations: verified",
        "- Attested artifacts: 6"
    )
$physicalSmokePassed = Test-ReportMarkers $issues "Physical release smoke" $physicalSmokeText @(
        "# Release Smoke Report",
        "- Status: passed",
        "- Mode: physical-device",
        "- Physical device: verified-real-device",
        "- Launch pid observed:"
    )
$uxSignOffPassed = Test-ReportMarkers $issues "UX sign-off" $uxSignOffText @(
        "# UX Sign-Off Report",
        "- Status: passed",
        "- Unchecked items: 0"
    )
$licenseBoundaryPassed = Test-ReportMarkers $issues "License boundary" $licenseBoundaryText @(
        "# License Boundary Report",
        "- Status: passed",
        "- MIT release status: blocked until GPL-derived material is cleared",
        "- Issues: 0"
    )

if ($issues.Count -gt 0) {
    Write-ReadinessReport "failed" $releaseArtifactPassed $physicalSmokePassed `
        $uxSignOffPassed $licenseBoundaryPassed $issues
    foreach ($issue in $issues) {
        [Console]::Error.WriteLine($issue)
    }
    exit 1
}

Write-ReadinessReport "passed" $releaseArtifactPassed $physicalSmokePassed `
    $uxSignOffPassed $licenseBoundaryPassed $issues
Write-Host "Release readiness verification passed."
