[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ApkPath,

    [string] $PackageName = "org.adaway",

    [string] $DeviceSerial = "",

    [string] $ExpectedCertSha256 = "",

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

function Find-BuildTool([string] $toolName) {
    $roots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    foreach ($root in $roots) {
        $buildTools = Join-Path $root "build-tools"
        if (-not (Test-Path -LiteralPath $buildTools -PathType Container)) {
            continue
        }
        $directories = Get-ChildItem -LiteralPath $buildTools -Directory |
            Sort-Object @{ Expression = { Convert-BuildToolsVersion $_.Name }; Descending = $true },
                @{ Expression = { $_.Name }; Descending = $true }
        foreach ($directory in $directories) {
            foreach ($suffix in @(".exe", ".bat", ".cmd", "")) {
                $candidate = Join-Path $directory.FullName ($toolName + $suffix)
                if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                    return (Resolve-Path -LiteralPath $candidate).Path
                }
            }
        }
    }

    $command = Get-Command $toolName -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    Fail "$toolName was not found. Install Android build-tools and set ANDROID_HOME."
}

function Convert-BuildToolsVersion([string] $name) {
    $match = [Regex]::Match($name, "^(?<major>\d+)(?:\.(?<minor>\d+))?(?:\.(?<patch>\d+))?")
    if (-not $match.Success) {
        return [Version]::new(0, 0, 0)
    }

    $major = [int] $match.Groups["major"].Value
    $minor = if ($match.Groups["minor"].Success) { [int] $match.Groups["minor"].Value } else { 0 }
    $patch = if ($match.Groups["patch"].Success) { [int] $match.Groups["patch"].Value } else { 0 }
    return [Version]::new($major, $minor, $patch)
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

function Write-ReleaseSmokeReport(
    [string] $Status,
    [string] $Mode,
    [string] $PhysicalDevice,
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
    $lines = @(
        "# Release Smoke Report",
        "",
        "Generated: $(Get-Date -Format o)",
        "",
        "- Status: $Status",
        "- Mode: $Mode",
        "- APK: $apkName",
        "- Package: $PackageName",
        "- Signer certificate check: $signerCheck",
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

$apk = Resolve-Path -LiteralPath $ApkPath -ErrorAction Stop
if ((Get-Item -LiteralPath $apk.Path).Length -le 0) {
    Fail "APK is empty: $($apk.Path)"
}

$aapt = Find-BuildTool "aapt"

$badging = & $aapt dump badging $apk.Path
if ($LASTEXITCODE -ne 0) {
    Fail "aapt dump badging failed for $($apk.Path)."
}
if (($badging -join "`n") -match "application-debuggable") {
    Fail "Refusing to smoke-test a debuggable APK. Use the release APK."
}
$packagePattern = "package: name='$([Regex]::Escape($PackageName))'"
if (-not (($badging -join "`n") -match $packagePattern)) {
    Fail "APK package does not match $PackageName."
}

if (-not [string]::IsNullOrWhiteSpace($ExpectedCertSha256)) {
    $apksigner = Find-BuildTool "apksigner"
    $certOutput = & $apksigner verify --print-certs $apk.Path
    if ($LASTEXITCODE -ne 0) {
        Fail "apksigner verification failed for $($apk.Path)."
    }
    $actualCertLine = ($certOutput | Select-String "Signer #1 certificate SHA-256 digest" |
            Select-Object -First 1).Line
    if ([string]::IsNullOrWhiteSpace($actualCertLine)) {
        Fail "Signer #1 certificate SHA-256 digest was not found."
    }
    $actualCert = ($actualCertLine -replace "^.*certificate SHA-256 digest:\s*", "")
    if ((Normalize-Sha256 $actualCert) -ne (Normalize-Sha256 $ExpectedCertSha256)) {
        Fail "Release APK signer fingerprint does not match ExpectedCertSha256."
    }
}

if ($VerifyOnly) {
    Write-ReleaseSmokeReport `
        -Status "passed" `
        -Mode "identity-only" `
        -PhysicalDevice "not-run"
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
    -DeviceSerialHash (Get-Sha256Hex $serial) `
    -LaunchPid $launchPid
Write-Host "Release smoke passed for $PackageName on $serial (pid $launchPid)."
