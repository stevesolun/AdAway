param([string] $VerifierPath = (Join-Path $PSScriptRoot "../verify-ux-signoff.ps1"))

# Extract only the actual packet generator and its data/helpers, never capture setup or execution.
$ErrorActionPreference = "Stop"
$generatorPath = Join-Path $PSScriptRoot "../run-ux-matrix.ps1"
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
        $generatorPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw ($parseErrors -join "`n") }
foreach ($name in @('$UxMatrixScreens', '$UxMatrixVariantSpecs')) {
    $assignment = $ast.Find({
        param($node)
        $node -is [System.Management.Automation.Language.AssignmentStatementAst] -and
                $node.Left.Extent.Text -eq $name
    }, $false)
    if (-not $assignment) { throw "Missing generator data: $name" }
    Invoke-Expression $assignment.Extent.Text
}
foreach ($name in @("Get-UxScreenshotPath", "Get-RelativeUxPath", "Get-SourceCommit",
        "Write-UxMatrixReviewManifest")) {
    $function = $ast.Find({
        param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
                $node.Name -eq $name
    }, $false)
    if (-not $function) { throw "Missing generator function: $name" }
    Invoke-Expression $function.Extent.Text
}

function Assert-True([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

$fixture = Join-Path ([System.IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString())
$savedSha = $env:GITHUB_SHA
$savedError = [Console]::Error
$script:Passed = 0
$packet = Join-Path $fixture "packet.md"
$report = Join-Path $fixture "report.md"
$commit = "0123456789abcdef0123456789abcdef01234567"

function Test-Packet {
    param([string] $Name, [string] $Text, [bool] $Pass = $false,
            [string] $IssuePattern = "", [string] $Reviewer = "QA Lead")
    Set-Content -LiteralPath $packet -Value $Text -Encoding UTF8
    Remove-Item -LiteralPath $report -Force -ErrorAction SilentlyContinue
    $runner = [powershell]::Create()
    try {
        $null = $runner.AddScript({
            param($Verifier, $Packet, $Report, $Reviewer)
            $global:LASTEXITCODE = 0
            & $Verifier -ReviewPacket $Packet -Reviewer $Reviewer -ReportPath $Report |
                    Out-Null
            $LASTEXITCODE
        }).AddArgument($VerifierPath).AddArgument($packet).AddArgument($report).
                AddArgument($Reviewer)
        $result = @($runner.Invoke())
        Assert-True ($runner.Streams.Error.Count -eq 0) `
                "$Name raised an unexpected PowerShell error: $($runner.Streams.Error)"
        Assert-True ($result.Count -eq 1) "$Name did not return an exit code"
        $expectedExit = if ($Pass) { 0 } else { 1 }
        Assert-True ($result[0] -eq $expectedExit) "$Name exit=$($result[0]); expected=$expectedExit"
        $reportText = Get-Content -Raw -LiteralPath $report
        $status = if ($Pass) { "passed" } else { "failed" }
        Assert-True ($reportText.Contains("- Status: $status")) "$Name report status incorrect"
        Assert-True ($reportText.Contains("- Review packet source commit: $commit")) `
                "$Name report must retain packet provenance"
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $packet).Hash.ToLowerInvariant()
        Assert-True ($reportText.Contains("- Review packet SHA-256: $hash")) "$Name packet hash incorrect"
        if ($Pass) {
            Assert-True ($reportText.Contains("- Checked items: 45") -and
                    $reportText.Contains("- Unchecked items: 0") -and
                    $reportText.Contains("- Issues: 0")) "$Name must have all 45 checks"
        }
        if ($IssuePattern) {
            Assert-True ($reportText -match $IssuePattern) "$Name missing diagnostic: $IssuePattern"
        }
        $script:Passed++
    } finally {
        $runner.Dispose()
    }
}

try {
    $env:GITHUB_SHA = $commit
    [Console]::SetError([System.IO.TextWriter]::Null)
    New-Item -ItemType Directory $fixture | Out-Null
    # Test real generator output for both supported layouts with no device or tool discovery.
    foreach ($layout in @("nested", "direct")) {
        $output = Join-Path $fixture $layout
        foreach ($variant in $UxMatrixVariantSpecs) {
            $directory = Join-Path $output $variant.Name
            if ($layout -eq "nested") { $directory = Join-Path $directory "ux-matrix" }
            New-Item -ItemType Directory -Force $directory | Out-Null
            foreach ($screen in $UxMatrixScreens) {
                New-Item -ItemType File (Join-Path $directory "$screen.png") | Out-Null
            }
        }
        Write-UxMatrixReviewManifest -Directory $output | Out-Null
        $generated = Get-Content -Raw -LiteralPath (Join-Path $output "ux-matrix-review.md")
        $complete = $generated.Replace("[ ]", "[x]")
        Test-Packet "$layout generated packet" $complete $true
    }
    Test-Packet "uppercase marks and CRLF" `
            (($complete -replace '\r?\n', "`r`n").Replace("[x]", "[X]")) $true
    Test-Packet "unchecked generated packet" $generated $false "unchecked items"
    Test-Packet "single arbitrary checkbox" "- Source commit: $commit`n- [x] One arbitrary item" `
            $false "requires exactly one"
    $lines = @($complete -split '\r?\n')
    $itemIndices = @(0..($lines.Count - 1) | Where-Object { $lines[$_] -match '\[x\]' })
    Assert-True ($itemIndices.Count -eq 45) "Generator schema changed: expected 45 checklist items"
    foreach ($index in $itemIndices) {
        $changed = $lines.Clone()
        $changed[$index] = ""
        Test-Packet "missing item $index" ($changed -join "`n") $false "found 0"
        $changed[$index] = "$($lines[$index])`n$($lines[$index])"
        Test-Packet "duplicate item $index" ($changed -join "`n") $false "found 2"
    }
    foreach ($variant in $UxMatrixVariantSpecs) {
        $pattern = '(?ms)^## ' + [regex]::Escape($variant.Name) + '\r?\n.*?(?=^## |\z)'
        $section = [regex]::Match($complete, $pattern).Value
        Assert-True (-not [string]::IsNullOrEmpty($section)) "Missing generated variant"
        Test-Packet "missing section $($variant.Name)" $complete.Replace($section, "") $false "found 0"
        Test-Packet "duplicate section $($variant.Name)" ($complete + "`n" + $section) $false "found 2"
    }
    foreach ($marker in @("# UX Matrix Review Packet", "Manual sign-off checklist:", "- Screens:")) {
        Test-Packet "missing $marker" $complete.Replace($marker, "") $false "found 0"
        Test-Packet "duplicate $marker" $complete.Replace($marker, "$marker`n$marker") $false "found 2"
    }
    foreach ($path in @("MISSING", "font-1.3/home.png", "baseline/update.png", "../home.png",
            "/tmp/home.png", "https://example.invalid/home.png")) {
        Test-Packet "invalid path $path" $complete.Replace("baseline/home.png", $path) `
                $false "Invalid screenshot path"
    }
    Test-Packet "same count with replaced screen" `
            $complete.Replace("update - baseline/update.png", "home - baseline/home.png") $false "found 2"
    Test-Packet "misplaced global item" `
            $complete.Replace("- Screens:", "- Screens:`n- [x] Touch targets remain reachable and visually stable.") `
            $false "misplaced checklist"
    Test-Packet "unexpected variant" $complete.Replace("## baseline", "## unknown") $false "Unexpected.*heading"
    Test-Packet "unexpected screen" $complete.Replace("home - baseline/home.png", "unknown - baseline/unknown.png") `
            $false "misplaced checklist"
    Test-Packet "duplicate commit" ($complete + "`n- Source commit: $commit") $false "found 2"
    Test-Packet "hidden packet" ("<!--`n" + $complete + "`n-->") $false "HTML comments"
    Test-Packet "example packet" ('```markdown' + "`n" + $complete + '`n```') $false "Fenced blocks"
    Test-Packet "blank reviewer" $complete $false "Reviewer is required" " "
    $env:GITHUB_SHA = "fedcba9876543210fedcba9876543210fedcba98"
    Test-Packet "source drift" $complete $false "does not match"
    Write-Host "PASS: $script:Passed UX sign-off safety scenarios (generator-backed; no adb/Gradle)"
} finally {
    [Console]::SetError($savedError)
    $env:GITHUB_SHA = $savedSha
    Remove-Item -Recurse -Force -LiteralPath $fixture -ErrorAction SilentlyContinue
}
