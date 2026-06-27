param(
    [string]$AndroidHome = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$OutputDir = "app/build/reports/ux-matrix",
    [int]$InstrumentationTimeoutSeconds = 300
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
        Label = "Default font scale, default locale"
        ReviewFocus = "Default navigation, copy density, status hierarchy, and brand signal"
    },
    [pscustomobject]@{
        Name = "font-1.3"
        FontScale = "1.3"
        Locales = ""
        Label = "Large font scale 1.3, default locale"
        ReviewFocus = "Large-text wrapping, touch targets, and row density"
    },
    [pscustomobject]@{
        Name = "font-1.6"
        FontScale = "1.6"
        Locales = ""
        Label = "Large font scale 1.6, default locale"
        ReviewFocus = "Stress large-text clipping, scroll reachability, and controls"
    },
    [pscustomobject]@{
        Name = "font-1.3-rtl"
        FontScale = "1.3"
        Locales = "ar-XB"
        Label = "Large font scale 1.3, RTL pseudo-locale"
        ReviewFocus = "RTL anchoring, mirrored navigation, and readable source rows"
    },
    [pscustomobject]@{
        Name = "font-1.6-rtl"
        FontScale = "1.6"
        Locales = "ar-XB"
        Label = "Large font scale 1.6, RTL pseudo-locale"
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

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & $adb @Args
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed: $($Args -join ' ')"
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

    $text = if ($null -eq $Output) { "" } else { $Output }
    $hasFailure = $text -match "(?im)^\s*FAILURES!!!\s*$|Process crashed|" +
            "^\s*INSTRUMENTATION_(RESULT|STATUS):\s*(shortMsg|stack)="
    if ($hasFailure) {
        return $false
    }

    return $text -match "(?m)^\s*OK\s+\(\d+\s+tests?\)\s*$"
}

function Install-TestPackages {
    Invoke-Gradle :app:assembleDebug :app:assembleDebugAndroidTest `
            --dependency-verification=strict --stacktrace
    Invoke-Adb install -r -g $appApk
    Invoke-Adb install -r -g $testApk
}

function Stop-TestPackages {
    & $adb shell am force-stop org.adaway.test | Out-Null
    & $adb shell am force-stop org.adaway | Out-Null
}

function Dismiss-ExternalSystemDialogs {
    # Locale/font-scale changes can leave launcher ANR/system dialogs focused on emulators.
    & $adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS | Out-Null
    & $adb shell input keyevent 4 | Out-Null
    & $adb shell am force-stop com.google.android.apps.nexuslauncher | Out-Null
    & $adb shell am force-stop com.android.launcher3 | Out-Null
}

function Invoke-UxTest {
    param([string]$Variant)

    Stop-TestPackages
    Dismiss-ExternalSystemDialogs
    Invoke-Adb shell pm clear $appPackage
    $stdoutFile = New-TemporaryFile
    $stderrFile = New-TemporaryFile
    try {
        $process = Start-Process -FilePath $adb -ArgumentList @(
            "shell", "am", "instrument", "-w", "-e", "class",
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
        Stop-TestPackages
        Remove-Item -LiteralPath $stdoutFile, $stderrFile -Force -ErrorAction SilentlyContinue
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

    if ($FontScale) {
        Invoke-Adb shell settings put system font_scale $FontScale
    } else {
        Invoke-Adb shell settings delete system font_scale
    }

    if ($Locales) {
        Invoke-Adb shell settings put system system_locales $Locales
    } else {
        Invoke-Adb shell settings delete system system_locales
    }

    Invoke-Adb shell am force-stop org.adaway
    Dismiss-ExternalSystemDialogs
}

function Invoke-UxMatrix {
    New-Item -ItemType Directory -Force $OutputDir | Out-Null
    Invoke-Adb wait-for-device
    Install-TestPackages

    try {
        foreach ($variant in $UxMatrixVariantSpecs) {
            Set-DeviceState -FontScale $variant.FontScale -Locales $variant.Locales
            Invoke-UxTest -Variant $variant.Name
        }
        Write-UxMatrixReviewManifest -Directory $OutputDir
    } finally {
        Set-DeviceState -FontScale "" -Locales ""
    }
}

if ($MyInvocation.InvocationName -ne ".") {
    Invoke-UxMatrix
}
