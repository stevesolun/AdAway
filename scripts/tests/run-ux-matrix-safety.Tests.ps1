# Executes production functions with in-process mocks only. Never launches adb or Gradle.
$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $PSScriptRoot "../run-ux-matrix.ps1"
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
        $scriptPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors -join "`n") }
foreach ($function in $ast.FindAll({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst]
}, $false)) {
    Invoke-Expression $function.Extent.Text
}
$variantAssignment = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and
            $node.Left.Extent.Text -eq '$UxMatrixVariantSpecs'
}, $false)
Invoke-Expression $variantAssignment.Extent.Text

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Assert-Throws {
    param([scriptblock]$Action, [string]$MessagePattern)
    $thrown = $null
    try { & $Action | Out-Null } catch { $thrown = $_ }
    Assert-True ($null -ne $thrown) "Expected failure: $MessagePattern"
    Assert-True ("$thrown" -match $MessagePattern) `
            "Unexpected failure: $thrown`n$($thrown.ScriptStackTrace)"
}

function Mock-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    Assert-True ($Args.Count -ge 3 -and $Args[0] -ceq "-s" -and
            $Args[1] -ceq "emulator-5554") "Unpinned adb command: $Args"
    $script:Calls.Add(($Args -join "|"))
    $global:LASTEXITCODE = 0
    $command = $Args[2..($Args.Count - 1)] -join " "
    switch ($command) {
        "get-state" {
            if ($script:ProbeFails) { $global:LASTEXITCODE = 1 }
            return $script:DeviceState
        }
        "shell getprop ro.kernel.qemu" { return $script:Qemu }
        "shell settings get system font_scale" { return $script:OriginalFont }
        "shell settings get system system_locales" { return $script:OriginalLocales }
    }
    if ($script:RestoreFontFails -and $command -eq "shell settings put system font_scale 1.15") {
        $global:LASTEXITCODE = 1
    }
}

function Invoke-Gradle {
    $script:BuildCount++
    if ($script:BuildFails) { throw "simulated build failure" }
}

function Start-Process {
    param($FilePath, [string[]]$ArgumentList, [switch]$NoNewWindow,
            $RedirectStandardOutput, $RedirectStandardError, [switch]$PassThru)
    Assert-True ($FilePath -eq "Mock-Adb") "Unexpected process executable"
    Assert-True (($ArgumentList[0..4] -join "|") -ceq
            "-s|emulator-5554|shell|am|instrument") "Unpinned instrumentation"
    $script:InstrumentationCount++
    $output = if ($script:InstrumentationFails) { "FAILURES!!!" } else { "OK (1 test)" }
    Set-Content -LiteralPath $RedirectStandardOutput -Value $output
    $process = [pscustomobject]@{
        Id = 123
        HasExited = -not $script:InstrumentationTimesOut
        ExitCode = 0
    }
    $process | Add-Member -MemberType ScriptMethod -Name Refresh -Value {}
    $process | Add-Member -MemberType ScriptMethod -Name WaitForExit -Value {}
    return $process
}

function Wait-Process { param($Id, $Timeout, $ErrorAction) }
function Stop-Process { param($Id, [switch]$Force, $ErrorAction) }
function Write-UxMatrixReviewManifest {
    param($Directory)
    if ($script:PacketFails) { throw "simulated packet failure" }
}

$fixture = Join-Path ([System.IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString())
$adb = "Mock-Adb"
$appPackage = "org.adaway"
$testRunner = "org.adaway.test/androidx.test.runner.AndroidJUnitRunner"
$appApk = "mock-app.apk"
$testApk = "mock-tests.apk"
$OutputDir = Join-Path $fixture "output"
$InstrumentationTimeoutSeconds = 1
$script:Passed = 0

function Reset-Fixture {
    $script:DeviceSerial = "emulator-5554"
    $script:AllowDestructiveCapture = $true
    $script:AuthorizedDeviceSerial = $null
    $script:Calls = New-Object 'System.Collections.Generic.List[string]'
    $script:DeviceState = "device"
    $script:Qemu = "1"
    $script:ProbeFails = $false
    $script:OriginalFont = "1.15"
    $script:OriginalLocales = "en-US,he-IL"
    $script:BuildFails = $false
    $script:InstrumentationFails = $false
    $script:InstrumentationTimesOut = $false
    $script:PacketFails = $false
    $script:RestoreFontFails = $false
    $script:BuildCount = 0
    $script:InstrumentationCount = 0
}

function Assert-NoMutation {
    Assert-True ($script:BuildCount -eq 0) "Build began before device authorization/snapshot"
    Assert-True ($script:InstrumentationCount -eq 0) "Instrumentation began before authorization"
    foreach ($call in $script:Calls) {
        Assert-True ($call -match '\|(get-state|shell\|getprop\|ro.kernel.qemu|shell\|settings\|get\|system\|(font_scale|system_locales))$') "Unexpected mutation: $call"
    }
    Assert-True (-not $script:AuthorizedDeviceSerial) "Authorization leaked after failure"
}

function Assert-Restored {
    $tail = $script:Calls.GetRange($script:Calls.Count - 2, 2) -join "`n"
    Assert-True ($tail -ceq ("-s|emulator-5554|shell|settings|put|system|font_scale|1.15`n" +
            "-s|emulator-5554|shell|settings|put|system|system_locales|en-US,he-IL")) `
            "Original settings were not restored: $tail"
    Assert-True (-not $script:AuthorizedDeviceSerial) "Authorization leaked after capture"
}

try {
    foreach ($scenario in @("no-args", "no-serial", "no-consent", "physical", "unsafe-serial")) {
        Reset-Fixture
        switch ($scenario) {
            "no-args" { $DeviceSerial = ""; $AllowDestructiveCapture = $false }
            "no-serial" { $DeviceSerial = "" }
            "no-consent" { $AllowDestructiveCapture = $false }
            "physical" { $DeviceSerial = "personal-phone" }
            "unsafe-serial" { $DeviceSerial = "emulator-5554 -d" }
        }
        Assert-Throws { Invoke-UxMatrix } "Physical devices are refused"
        Assert-NoMutation
        Assert-True ($script:Calls.Count -eq 0) "Invalid selection reached adb"
        $script:Passed++
    }
    foreach ($scenario in @("offline", "unauthorized", "probe-error", "not-qemu")) {
        Reset-Fixture
        switch ($scenario) {
            "offline" { $script:DeviceState = "offline" }
            "unauthorized" { $script:DeviceState = "unauthorized" }
            "probe-error" { $script:ProbeFails = $true }
            "not-qemu" { $script:Qemu = "0" }
        }
        Assert-Throws { Invoke-UxMatrix } "not ready|not a verified emulator"
        Assert-NoMutation
        $script:Passed++
    }
    foreach ($action in @(
        { Invoke-Adb shell pm clear org.adaway },
        { Stop-TestPackages },
        { Dismiss-ExternalSystemDialogs },
        { Install-TestPackages },
        { Invoke-UxTest baseline },
        { Set-DeviceState 1.3 ar-XB }
    )) {
        Reset-Fixture
        Assert-Throws $action "not been authorized"
        Assert-NoMutation
        $script:Passed++
    }
    Reset-Fixture
    Authorize-CaptureDevice
    $DeviceSerial = "emulator-5556"
    Assert-Throws { Invoke-Adb shell pm clear org.adaway } "not been authorized"
    Assert-True ($script:Calls.Count -eq 2) "Serial change was not refused"
    $script:Passed++

    foreach ($scenario in @("empty-font", "unsafe-locale")) {
        Reset-Fixture
        if ($scenario -eq "empty-font") { $script:OriginalFont = "" }
        else { $script:OriginalLocales = 'en-US;am force-stop example' }
        Assert-Throws { Invoke-UxMatrix } "No value returned|Cannot safely preserve"
        Assert-NoMutation
        $script:Passed++
    }
    foreach ($scenario in @("success", "build-failure", "instrument-failure", "timeout", "packet-failure")) {
        Reset-Fixture
        switch ($scenario) {
            "build-failure" { $script:BuildFails = $true }
            "instrument-failure" { $script:InstrumentationFails = $true }
            "timeout" { $script:InstrumentationTimesOut = $true }
            "packet-failure" { $script:PacketFails = $true }
        }
        if ($scenario -eq "success") {
            Invoke-UxMatrix
            Assert-True ($script:InstrumentationCount -eq 5) "Not all variants ran"
            Assert-True ($script:Calls.Contains("-s|emulator-5554|shell|pm|clear|org.adaway")) `
                    "Destructive path not exercised"
        } else {
            Assert-Throws { Invoke-UxMatrix } "simulated|UX matrix test failed|UX matrix test timed out"
        }
        Assert-Restored
        $script:Passed++
    }
    Reset-Fixture
    $script:OriginalFont = "null"
    $script:OriginalLocales = "null"
    Invoke-UxMatrix
    $tail = $script:Calls.GetRange($script:Calls.Count - 2, 2) -join "`n"
    Assert-True ($tail -ceq ("-s|emulator-5554|shell|settings|delete|system|font_scale`n" +
            "-s|emulator-5554|shell|settings|delete|system|system_locales")) "Absent settings not restored"
    $script:Passed++

    Reset-Fixture
    $script:BuildFails = $true
    $script:RestoreFontFails = $true
    Assert-Throws { Invoke-UxMatrix } "Failed to restore emulator settings"
    Assert-Restored
    $script:Passed++

    foreach ($case in @(
        @{ Code = 1; Text = "OK (1 test)" },
        @{ Code = $null; Text = "OK (1 test)" },
        @{ Code = 0; Text = "OK (0 tests)" },
        @{ Code = 0; Text = "FAILURES!!!`nOK (1 test)" }
    )) {
        Assert-True (-not (Test-UxInstrumentationResult -ExitCode $case.Code -Output $case.Text)) `
                "Incomplete or failed capture accepted: exit=$($case.Code) output=$($case.Text)"
        $script:Passed++
    }

    # No raw adb entry points may bypass serial pinning, including Start-Process.
    $rawAdb = @($ast.FindAll({
        param($node)
        $node -is [System.Management.Automation.Language.CommandAst] -and
                $node.CommandElements[0].Extent.Text -eq '$adb'
    }, $true))
    Assert-True ($rawAdb.Count -eq 3) "Unexpected raw adb entry point"
    foreach ($command in $rawAdb) {
        Assert-True ($command.Extent.Text -match '^& \$adb -s \$(DeviceSerial|serial) ') `
                "Raw adb command is not pinned: $command"
    }
    $script:Passed++
    Write-Host "PASS: $script:Passed capture safety scenarios (mocked; no adb/Gradle)."
} finally {
    if (Test-Path -LiteralPath $fixture) {
        Remove-Item -LiteralPath $fixture -Recurse -Force
    }
}
