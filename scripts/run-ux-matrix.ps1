param(
    [string]$AndroidHome = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$OutputDir = "app/build/reports/ux-matrix",
    [int]$InstrumentationTimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AndroidHome)) {
    $AndroidHome = Join-Path $env:LOCALAPPDATA "Android/Sdk"
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $JavaHome = "C:/Program Files/Microsoft/jdk-21.0.9.10-hotspot"
}

$adb = Join-Path $AndroidHome "platform-tools/adb.exe"
if (!(Test-Path $adb)) {
    throw "adb not found at $adb"
}

$appPackage = "org.adaway"
$testRunner = "org.adaway.test/androidx.test.runner.AndroidJUnitRunner"
$appApk = "app/build/outputs/apk/debug/app-debug.apk"
$testApk = "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidHome
$env:PATH = "$JavaHome/bin;$AndroidHome/platform-tools;$env:PATH"

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & $adb @Args
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed: $($Args -join ' ')"
    }
}

function Invoke-Gradle {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    .\gradlew.bat @Args
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
        Set-DeviceState -FontScale "" -Locales ""
        Invoke-UxTest -Variant "baseline"

        Set-DeviceState -FontScale "1.3" -Locales ""
        Invoke-UxTest -Variant "font-1.3"

        Set-DeviceState -FontScale "1.6" -Locales ""
        Invoke-UxTest -Variant "font-1.6"

        Set-DeviceState -FontScale "1.3" -Locales "ar-XB"
        Invoke-UxTest -Variant "font-1.3-rtl"

        Set-DeviceState -FontScale "1.6" -Locales "ar-XB"
        Invoke-UxTest -Variant "font-1.6-rtl"
    } finally {
        Set-DeviceState -FontScale "" -Locales ""
    }
}

if ($MyInvocation.InvocationName -ne ".") {
    Invoke-UxMatrix
}
