<#
.SYNOPSIS
Captures UX variants on an explicitly authorized disposable emulator. App data is erased.
.EXAMPLE
./scripts/run-ux-matrix.ps1 -DeviceSerial emulator-5554 -AllowDestructiveCapture
#>
param(
    [string]$AndroidHome = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$OutputDir = "app/build/reports/ux-matrix",
    [int]$InstrumentationTimeoutSeconds = 300,
    [string]$DeviceSerial,
    [switch]$AllowDestructiveCapture
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AndroidHome)) {
    $AndroidHome = $env:ANDROID_SDK_ROOT
}
if ([string]::IsNullOrWhiteSpace($AndroidHome) -and
        -not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    $AndroidHome = Join-Path $env:LOCALAPPDATA "Android/Sdk"
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $JavaHome = "C:/Program Files/Microsoft/jdk-21.0.9.10-hotspot"
}

function Test-WindowsHost {
    return [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
}

function Get-ExecutableSuffixes {
    if (Test-WindowsHost) {
        return @(".exe", ".bat", ".cmd", "")
    }

    return @("", ".exe", ".bat", ".cmd")
}

function Find-CommandOrSdkTool {
    param(
        [string]$CommandName,
        [string]$SdkRelativePath
    )

    $roots = @($AndroidHome, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT) |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -Unique

    foreach ($root in $roots) {
        foreach ($suffix in Get-ExecutableSuffixes) {
            $candidate = Join-Path $root ($SdkRelativePath + $suffix)
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                return (Resolve-Path -LiteralPath $candidate).Path
            }
        }
    }

    $command = Get-Command $CommandName -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "$CommandName was not found. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}

function Find-GradleWrapper {
    $repoRoot = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) {
        (Get-Location).Path
    } else {
        Split-Path -Parent $PSScriptRoot
    }
    $candidates = if (Test-WindowsHost) {
        @("gradlew.bat", "gradlew.cmd", "gradlew")
    } else {
        @("gradlew", "gradlew.bat", "gradlew.cmd")
    }

    foreach ($candidateName in $candidates) {
        $candidate = Join-Path $repoRoot $candidateName
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "Gradle wrapper not found in $repoRoot"
}

$adb = Find-CommandOrSdkTool "adb" "platform-tools/adb"
$gradle = Find-GradleWrapper
$appPackage = "org.adaway"
$testRunner = "org.adaway.test/androidx.test.runner.AndroidJUnitRunner"
$appApk = "app/build/outputs/apk/debug/app-debug.apk"
$testApk = "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
$UxMatrixScreens = @(
    "home",
    "discover",
    "sources",
    "more",
    "domain_checker",
    "onboarding",
    "custom_rules",
    "update"
)
$UxMatrixVariantSpecs = @(
    [pscustomobject]@{
        Name = "baseline"
        FontScale = ""
        Locales = ""
        Label = "Default font scale, English"
        ReviewFocus = "Default navigation, copy density, status hierarchy, and brand signal"
    },
    [pscustomobject]@{
        Name = "font-1.3"
        FontScale = "1.3"
        Locales = ""
        Label = "Large font scale 1.3, English"
        ReviewFocus = "Large-text wrapping, touch targets, and row density"
    },
    [pscustomobject]@{
        Name = "font-1.6"
        FontScale = "1.6"
        Locales = ""
        Label = "Large font scale 1.6, English"
        ReviewFocus = "Stress large-text clipping, scroll reachability, and controls"
    },
    [pscustomobject]@{
        Name = "font-1.3-rtl"
        FontScale = "1.3"
        Locales = "he-IL"
        Label = "Large font scale 1.3, Hebrew RTL"
        ReviewFocus = "RTL anchoring, mirrored navigation, and readable source rows"
    },
    [pscustomobject]@{
        Name = "font-1.6-rtl"
        FontScale = "1.6"
        Locales = "he-IL"
        Label = "Large font scale 1.6, Hebrew RTL"
        ReviewFocus = "Worst-case RTL text fit, FAB clearance, and bottom navigation"
    }
)

$env:JAVA_HOME = $JavaHome
if (-not [string]::IsNullOrWhiteSpace($AndroidHome)) {
    $env:ANDROID_HOME = $AndroidHome
}
$pathSeparator = [System.IO.Path]::PathSeparator
$pathParts = New-Object System.Collections.Generic.List[string]
if (-not [string]::IsNullOrWhiteSpace($JavaHome)) {
    $pathParts.Add((Join-Path $JavaHome "bin"))
}
if (-not [string]::IsNullOrWhiteSpace($AndroidHome)) {
    $pathParts.Add((Join-Path $AndroidHome "platform-tools"))
}
$pathParts.Add($env:PATH)
$env:PATH = $pathParts -join $pathSeparator
$script:AuthorizedDeviceSerial = $null

function Authorize-CaptureDevice {
    $script:AuthorizedDeviceSerial = $null
    if ($DeviceSerial -cnotmatch '^emulator-[0-9]+$' -or -not $AllowDestructiveCapture) {
        throw "Capture erases app data. Specify -DeviceSerial emulator-PORT and " +
                "-AllowDestructiveCapture for a disposable emulator. Physical devices are refused."
    }

    # These probes are read-only and always target the explicitly selected serial.
    $state = & $adb -s $DeviceSerial get-state
    if ($LASTEXITCODE -ne 0 -or ($state -join "`n").Trim() -cne "device") {
        throw "Selected emulator is not ready: $DeviceSerial"
    }
    $qemu = & $adb -s $DeviceSerial shell getprop ro.kernel.qemu
    if ($LASTEXITCODE -ne 0 -or ($qemu -join "`n").Trim() -cne "1") {
        throw "Selected serial is not a verified emulator: $DeviceSerial"
    }
    $script:AuthorizedDeviceSerial = $DeviceSerial
}

function Get-AuthorizedDeviceSerial {
    if (-not $script:AuthorizedDeviceSerial -or
            $script:AuthorizedDeviceSerial -cne $DeviceSerial -or
            -not $AllowDestructiveCapture) {
        throw "Destructive capture has not been authorized for this serial."
    }
    return $script:AuthorizedDeviceSerial
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$CommandArguments)
    $serial = Get-AuthorizedDeviceSerial
    & $adb -s $serial @CommandArguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed: $($CommandArguments -join ' ')"
    }
}

function Invoke-Gradle {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & $gradle @Args
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed: $($Args -join ' ')"
    }
}

function Test-UxInstrumentationResult {
    param(
        [AllowNull()]$ExitCode,
        [AllowNull()][string]$Output
    )

    if ($null -eq $ExitCode -or $ExitCode -ne 0) { return $false }
    $text = if ($null -eq $Output) { "" } else { $Output }
    $hasFailure = $text -match "(?im)^\s*FAILURES!!!\s*$|Process crashed|" +
            "^\s*INSTRUMENTATION_(RESULT|STATUS):\s*(shortMsg|stack)="
    if ($hasFailure) {
        return $false
    }

    return $text -match "(?m)^\s*OK\s+\([1-9]\d*\s+tests?\)\s*$"
}

function Install-TestPackages {
    $null = Get-AuthorizedDeviceSerial
    Invoke-Gradle :app:assembleDebug :app:assembleDebugAndroidTest `
            --dependency-verification=strict --stacktrace
    Invoke-Adb install -r -g $appApk
    Invoke-Adb install -r -g $testApk
}

function Stop-TestPackages {
    Invoke-Adb shell am force-stop org.adaway.test | Out-Null
    Invoke-Adb shell am force-stop org.adaway | Out-Null
}

function Dismiss-ExternalSystemDialogs {
    # Locale/font-scale changes can leave launcher ANR/system dialogs focused on emulators.
    Invoke-Adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS | Out-Null
    Invoke-Adb shell input keyevent 4 | Out-Null
    Invoke-Adb shell am force-stop com.google.android.apps.nexuslauncher | Out-Null
    Invoke-Adb shell am force-stop com.android.launcher3 | Out-Null
}

function Invoke-UxTest {
    param([string]$Variant, [string]$FontScale, [string]$Locales)
    if (-not $FontScale) { $FontScale = "1.0" }
    if (-not $Locales) { $Locales = "en-US" }

    Stop-TestPackages
    Dismiss-ExternalSystemDialogs
    Invoke-Adb shell pm clear $appPackage
    $stdoutFile = New-TemporaryFile
    $stderrFile = New-TemporaryFile
    try {
        $process = Start-Process -FilePath $adb -ArgumentList @(
            "-s", (Get-AuthorizedDeviceSerial),
            "shell", "am", "instrument", "-w",
            "-e", "uxLocale", $Locales, "-e", "uxFontScale", $FontScale, "-e", "class",
            "org.adaway.ui.UxDeviceMatrixTest", $testRunner
        ) -NoNewWindow -RedirectStandardOutput $stdoutFile `
                -RedirectStandardError $stderrFile -PassThru
        Wait-Process -Id $process.Id -Timeout $InstrumentationTimeoutSeconds `
            -ErrorAction SilentlyContinue
        $process.Refresh()
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            Stop-TestPackages
            throw "UX matrix test timed out for $Variant after " +
                    "$InstrumentationTimeoutSeconds seconds"
        }
        $process.WaitForExit()
        $process.Refresh()
        $exitCode = $process.ExitCode

        $instrumentationOutput = @()
        if (Test-Path $stdoutFile) {
            $instrumentationOutput += Get-Content -LiteralPath $stdoutFile
        }
        if (Test-Path $stderrFile) {
            $instrumentationOutput += Get-Content -LiteralPath $stderrFile
        }
        $instrumentationOutput | ForEach-Object { Write-Host $_ }
        $joinedOutput = $instrumentationOutput -join "`n"
        if (-not (Test-UxInstrumentationResult -ExitCode $exitCode -Output $joinedOutput)) {
            throw "UX matrix test failed for $Variant"
        }
    } finally {
        try {
            Stop-TestPackages
        } finally {
            Remove-Item -LiteralPath $stdoutFile, $stderrFile -Force -ErrorAction SilentlyContinue
        }
    }

    $target = Join-Path $OutputDir $Variant
    if (Test-Path $target) {
        Remove-Item -Recurse -Force $target
    }
    New-Item -ItemType Directory -Force $target | Out-Null
    Invoke-Adb pull "/sdcard/Android/data/$appPackage/files/ux-matrix" $target
}

function Get-UxScreenshotPath {
    param(
        [string]$Directory,
        [string]$Variant,
        [string]$Screen
    )

    $variantDir = Join-Path $Directory $Variant
    $directPath = Join-Path $variantDir "$Screen.png"
    if (Test-Path $directPath) {
        return $directPath
    }

    return Join-Path (Join-Path $variantDir "ux-matrix") "$Screen.png"
}

function Get-RelativeUxPath {
    param(
        [string]$Directory,
        [string]$Path
    )

    $root = (Resolve-Path -LiteralPath $Directory).Path
    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    return $resolvedPath.Substring($root.Length + 1).Replace("\", "/")
}

function Get-SourceCommit {
    if ($env:GITHUB_SHA -match "^[0-9a-fA-F]{40}$") {
        return $env:GITHUB_SHA.ToLowerInvariant()
    }

    $git = Get-Command git -ErrorAction SilentlyContinue
    if (-not $git) {
        return "not-provided"
    }
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $git.Source
    $startInfo.Arguments = "rev-parse HEAD"
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.UseShellExecute = $false
    $process = [System.Diagnostics.Process]::Start($startInfo)
    $commit = $process.StandardOutput.ReadToEnd().Trim()
    $process.WaitForExit()
    if ($process.ExitCode -eq 0 -and $commit -match "^[0-9a-fA-F]{40}$") {
        return $commit.ToLowerInvariant()
    }
    return "not-provided"
}

function Write-UxMatrixReviewManifest {
    param([string]$Directory = $OutputDir)

    New-Item -ItemType Directory -Force $Directory | Out-Null
    $manifestPath = Join-Path $Directory "ux-matrix-review.md"
    $lines = New-Object System.Collections.Generic.List[string]
    $missingScreens = New-Object System.Collections.Generic.List[string]

    $lines.Add("# UX Matrix Review Packet")
    $lines.Add("")
    $lines.Add("Generated: $(Get-Date -Format o)")
    $lines.Add("- Source commit: $(Get-SourceCommit)")
    $lines.Add("- Capture: VPN method; rendered locale, direction and font scale checked in each activity.")
    $lines.Add("")
    $lines.Add("Manual sign-off checklist:")
    $lines.Add("- [ ] Text is readable without clipping, ellipsizing, or overlap.")
    $lines.Add("- [ ] Touch targets remain reachable and visually stable.")
    $lines.Add("- [ ] The AdAway bird remains the first-screen brand signal.")
    $lines.Add("- [ ] FABs, cards, and bottom navigation do not hide primary actions.")
    $lines.Add("- [ ] RTL variants keep navigation, cards, and rule rows correctly anchored.")
    $lines.Add("")

    foreach ($variant in $UxMatrixVariantSpecs) {
        $lines.Add("## $($variant.Name)")
        $lines.Add("- Device state: $($variant.Label)")
        $lines.Add("- Review focus: $($variant.ReviewFocus)")
        $lines.Add("- Screens:")
        foreach ($screen in $UxMatrixScreens) {
            $screenshotPath = Get-UxScreenshotPath `
                    -Directory $Directory -Variant $variant.Name -Screen $screen
            if (Test-Path $screenshotPath) {
                $relativePath = Get-RelativeUxPath -Directory $Directory -Path $screenshotPath
                $lines.Add("  - [ ] $screen - $relativePath")
            } else {
                $lines.Add("  - [ ] $screen - MISSING")
                $missingScreens.Add("$($variant.Name)/$screen.png")
            }
        }
        $lines.Add("")
    }

    Set-Content -LiteralPath $manifestPath -Value $lines -Encoding UTF8
    Write-Host "UX matrix review packet=$manifestPath"
    if ($missingScreens.Count -gt 0) {
        throw "UX matrix review packet is missing screenshots: $($missingScreens -join ', ')"
    }
}

function Set-DeviceState {
    param(
        [string]$FontScale,
        [string]$Locales
    )

    Set-DeviceSetting -Name font_scale -Value $FontScale
    Set-DeviceSetting -Name system_locales -Value $Locales

    Invoke-Adb shell am force-stop org.adaway
    Dismiss-ExternalSystemDialogs
}

function Assert-DeviceSetting {
    param([string]$Name, [string]$Value)
    $pattern = switch ($Name) {
        "font_scale" { '^[0-9]+(\.[0-9]+)?$' }
        "system_locales" { '^[a-zA-Z0-9]+([-_,][a-zA-Z0-9]+)*$' }
        default { throw "Unsupported device setting: $Name" }
    }
    if ($Value -and $Value -cne "null" -and $Value -notmatch $pattern) {
        throw "Cannot safely preserve device setting: $Name"
    }
}

function Set-DeviceSetting {
    param([string]$Name, [string]$Value)
    Assert-DeviceSetting -Name $Name -Value $Value
    if (-not $Value -or $Value -ceq "null") {
        Invoke-Adb shell settings delete system $Name
    } else {
        Invoke-Adb shell settings put system $Name $Value
    }
}

function Get-DeviceState {
    $state = @{}
    foreach ($name in @("font_scale", "system_locales")) {
        $value = ((Invoke-Adb shell settings get system $name) -join "`n").Trim()
        if (-not $value) {
            throw "No value returned for device setting: $name"
        }
        Assert-DeviceSetting -Name $name -Value $value
        $state[$name] = $value
    }
    return $state
}

function Restore-DeviceState {
    param([hashtable]$State)
    $failures = @()
    foreach ($name in @("font_scale", "system_locales")) {
        try {
            Set-DeviceSetting -Name $name -Value $State[$name]
        } catch {
            $failures += "$name`: $_"
        }
    }
    if ($failures.Count -gt 0) {
        throw "Failed to restore emulator settings: $($failures -join '; ')"
    }
}

function Invoke-UxMatrix {
    Authorize-CaptureDevice
    $originalState = $null
    try {
        $originalState = Get-DeviceState
        New-Item -ItemType Directory -Force $OutputDir | Out-Null
        Install-TestPackages
        foreach ($variant in $UxMatrixVariantSpecs) {
            Set-DeviceState -FontScale $variant.FontScale -Locales $variant.Locales
            Invoke-UxTest -Variant $variant.Name -FontScale $variant.FontScale -Locales $variant.Locales
        }
        Write-UxMatrixReviewManifest -Directory $OutputDir
    } finally {
        try {
            if ($null -ne $originalState) {
                Restore-DeviceState -State $originalState
            }
        } finally {
            $script:AuthorizedDeviceSerial = $null
        }
    }
}

if ($MyInvocation.InvocationName -ne ".") {
    Invoke-UxMatrix
}
