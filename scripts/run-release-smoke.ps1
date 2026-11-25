[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ApkPath,

    [string] $PackageName = "org.adaway",

    [string] $DeviceSerial = "",

    [string] $ExpectedCertSha256 = "",

    [string] $ReleaseTag = "",

    [string] $PublicKeyBase64 = $env:UPDATE_MANIFEST_PUBLIC_KEY_BASE64,

    [string] $MetadataSourceCommit = "",

    [string] $MetadataSourceRef = "",

    [int] $LaunchWaitSeconds = 5,

    [string] $ReportPath = "",

    [switch] $VerifyOnly
)

$ErrorActionPreference = "Stop"

function Fail([string] $message) {
    throw $message
}

function Find-CommandOrSdkTool([string] $commandName, [string] $sdkRelativePath) {
    $roots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    foreach ($root in $roots) {
        foreach ($suffix in @(".exe", ".bat", ".cmd", "")) {
            $candidate = Join-Path $root ($sdkRelativePath + $suffix)
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                return (Resolve-Path -LiteralPath $candidate).Path
            }
        }
    }

    $command = Get-Command $commandName -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    Fail "$commandName was not found. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}

function Normalize-Sha256([string] $value) {
    return ($value -replace "[:\s]", "").ToLowerInvariant()
}

function Get-Sha256Hex([string] $value) {
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($value)
        return -join ($sha256.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") })
    } finally {
        $sha256.Dispose()
    }
}

function Get-FileSha256Hex([string] $path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
}

function Get-VerifiedField([string] $field) {
    $matches = [Regex]::Matches($script:artifactReport,
        "(?m)^- " + [Regex]::Escape($field) + ":[ \t]*(.+?)[ \t]*\r?$")
    if ($matches.Count -ne 1) {
        Fail "Canonical artifact report must contain exactly one '$field'."
    }
    return $matches[0].Groups[1].Value
}

function Assert-ApkUnchanged {
    if ((Get-FileSha256Hex $apk.Path) -cne $apkSha256) {
        Fail "APK changed after artifact verification."
    }
}

function Write-ReleaseSmokeReport(
    [string] $Status,
    [string] $Mode,
    [string] $PhysicalDevice,
    [string] $ApkSha256,
    [string] $SignerCertificateSha256,
    [string] $DeviceSerialHash = "",
    [string] $LaunchPid = ""
) {
    if ([string]::IsNullOrWhiteSpace($ReportPath)) {
        return
    }

    $resolvedReport = [IO.Path]::GetFullPath($ReportPath)
    $reportDirectory = Split-Path -Parent $resolvedReport
    if (-not [string]::IsNullOrWhiteSpace($reportDirectory)) {
        New-Item -ItemType Directory -Force $reportDirectory | Out-Null
    }

    $apkName = Split-Path -Leaf $apk.Path
    $signerCheck = -not [string]::IsNullOrWhiteSpace($ExpectedCertSha256)
    $signerCertificate = if ([string]::IsNullOrWhiteSpace($SignerCertificateSha256)) {
        "not-checked"
    } else {
        Normalize-Sha256 $SignerCertificateSha256
    }
    $lines = @(
        "# Release Smoke Report",
        "",
        "Generated: $(Get-Date -Format o)",
        "",
        "- Status: $Status",
        "- Artifact source commit: $(Get-VerifiedField 'Artifact source commit')",
        "- Artifact source ref: $(Get-VerifiedField 'Artifact source ref')",
        "- Artifact source workflow: $(Get-VerifiedField 'Artifact source workflow')",
        "- Artifact provenance: $(Get-VerifiedField 'Artifact provenance')",
        "- Tooling source commit: $(Get-VerifiedField 'Tooling source commit')",
        "- Mode: $Mode",
        "- Release tag: $ReleaseTag",
        "- APK: $apkName",
        "- APK SHA-256: $ApkSha256",
        "- Package: $PackageName",
        "- APK identity: verified",
        "- APK version: $(Get-VerifiedField 'APK version')",
        "- APK versionCode: $(Get-VerifiedField 'APK versionCode')",
        "- APK debuggable: false",
        "- Signer certificate check: $signerCheck",
        "- Signer certificate SHA-256: $signerCertificate",
        "- Physical device: $PhysicalDevice"
    )
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerialHash)) {
        $lines += "- Device serial SHA-256: $DeviceSerialHash"
    }
    if (-not [string]::IsNullOrWhiteSpace($LaunchPid)) {
        $lines += "- Launch pid observed: $LaunchPid"
    }

    Set-Content -LiteralPath $resolvedReport -Value $lines -Encoding UTF8
    Write-Host "Release smoke report=$resolvedReport"
}

if (-not [string]::IsNullOrWhiteSpace($ReportPath) -and
        (Test-Path -LiteralPath $ReportPath -PathType Leaf)) {
    Remove-Item -LiteralPath $ReportPath
}
if ($ReleaseTag -cnotmatch '^v[0-9][0-9A-Za-z._-]*$' -or $PackageName -cne 'org.adaway' -or
        (Normalize-Sha256 $ExpectedCertSha256) -notmatch '^[0-9a-f]{64}$' -or
        [string]::IsNullOrWhiteSpace($PublicKeyBase64)) {
    Fail 'ReleaseTag, ExpectedCertSha256 and manifest PublicKeyBase64 are required for org.adaway.'
}
$apk = Resolve-Path -LiteralPath $ApkPath -ErrorAction Stop
if ((Get-Item -LiteralPath $apk.Path).Length -le 0) {
    Fail "APK is empty: $($apk.Path)"
}
$apkSha256 = Get-FileSha256Hex $apk.Path

# Always verify the complete downloaded bundle before any device access.
$bundle = Split-Path -Parent $apk.Path
$verificationReport = [IO.Path]::GetTempFileName()
try {
    $version = $ReleaseTag.Substring(1)
    $channel = if ($version.EndsWith('b')) { 'beta' } else { 'stable' }
    $verifyArgs = @(
        (Join-Path $PSScriptRoot 'VerifyReleaseArtifacts.java'),
        '--apk', $apk.Path, '--apk-sha256', "$($apk.Path).sha256",
        '--manifest', (Join-Path $bundle 'manifest.json'),
        '--manifest-sha256', (Join-Path $bundle 'manifest.json.sha256'),
        '--sbom', (Join-Path $bundle 'adaway.cdx.json'),
        '--sbom-sha256', (Join-Path $bundle 'adaway.cdx.json.sha256'),
        '--public-key-base64', $PublicKeyBase64,
        '--expected-version', $version, '--expected-channel', $channel,
        '--expected-store', 'adaway', '--expected-cert-sha256', $ExpectedCertSha256,
        '--expected-apk-url', "https://github.com/stevesolun/AdAway/releases/download/$ReleaseTag/$(Split-Path -Leaf $apk.Path)",
        '--repo', 'stevesolun/AdAway', '--verify-attestations', '--report', $verificationReport
    )
    if ($MetadataSourceCommit -or $MetadataSourceRef) {
        $verifyArgs += @('--metadata-source-commit', $MetadataSourceCommit,
            '--metadata-source-ref', $MetadataSourceRef)
    }
    & java @verifyArgs
    if ($LASTEXITCODE -ne 0) {
        Fail 'Canonical artifact identity/provenance verification failed; no device was accessed.'
    }
    $script:artifactReport = Get-Content -Raw -LiteralPath $verificationReport
    $actualCertSha256 = Get-VerifiedField 'APK certificate SHA-256'
    if ((Get-VerifiedField 'Artifact provenance') -cne 'verified' -or
            (Get-VerifiedField 'Artifact source commit') -cnotmatch '^[0-9a-f]{40}$' -or
            (Get-VerifiedField 'APK SHA-256') -cne $apkSha256) {
        Fail 'Canonical artifact report has unknown or mismatched provenance.'
    }
} finally {
    Remove-Item -LiteralPath $verificationReport -ErrorAction SilentlyContinue
}
Assert-ApkUnchanged

if ($VerifyOnly) {
    Write-ReleaseSmokeReport `
        -Status "passed" `
        -Mode "identity-only" `
        -PhysicalDevice "not-run" `
        -ApkSha256 $apkSha256 `
        -SignerCertificateSha256 $actualCertSha256
    Write-Host "Release APK identity verification passed for ${PackageName}: $($apk.Path)"
    Write-Host "Physical-device install/launch smoke was not run."
    exit 0
}

$adb = Find-CommandOrSdkTool "adb" "platform-tools\adb"

$deviceLines = @(& $adb devices | Select-String "`tdevice$" | ForEach-Object {
    ($_.Line -split "`t")[0]
})
if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    if ($deviceLines -notcontains $DeviceSerial) {
        Fail "Requested device '$DeviceSerial' is not attached and ready."
    }
    $serial = $DeviceSerial
} else {
    if ($deviceLines.Count -ne 1) {
        Fail "Attach exactly one real device or pass -DeviceSerial. Found $($deviceLines.Count)."
    }
    $serial = $deviceLines[0]
}

$adbTarget = @("-s", $serial)
$kernelQemu = (& $adb @adbTarget shell getprop ro.kernel.qemu).Trim()
$bootQemu = (& $adb @adbTarget shell getprop ro.boot.qemu).Trim()
$hardware = (& $adb @adbTarget shell getprop ro.hardware).Trim()
if ($serial.StartsWith("emulator-") -or $kernelQemu -eq "1" -or $bootQemu -eq "1" -or
        $hardware -match "^(goldfish|ranchu)$") {
    Fail "Release smoke must run on a real physical device, not an emulator."
}

Write-Host "Running adb install for $PackageName on $serial..."
Assert-ApkUnchanged
& $adb @adbTarget install -r $apk.Path
if ($LASTEXITCODE -ne 0) {
    Fail "adb install failed for $($apk.Path)."
}

& $adb @adbTarget shell am force-stop $PackageName | Out-Null
& $adb @adbTarget shell monkey -p $PackageName -c android.intent.category.LAUNCHER 1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Fail "Failed to launch $PackageName with monkey."
}

Start-Sleep -Seconds $LaunchWaitSeconds
$launchPid = (& $adb @adbTarget shell pidof $PackageName).Trim()
if ([string]::IsNullOrWhiteSpace($launchPid)) {
    Fail "$PackageName is not running after launch."
}

Write-ReleaseSmokeReport `
    -Status "passed" `
    -Mode "physical-device" `
    -PhysicalDevice "verified-real-device" `
    -ApkSha256 $apkSha256 `
    -SignerCertificateSha256 $actualCertSha256 `
    -DeviceSerialHash (Get-Sha256Hex $serial) `
    -LaunchPid $launchPid
Write-Host "Release smoke passed for $PackageName on $serial (pid $launchPid)."
