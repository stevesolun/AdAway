param(
    [string] $ReviewPacket = "app/build/reports/ux-matrix/ux-matrix-review.md",
    [Parameter(Mandatory = $true)]
    [string] $Reviewer,
    [string] $ReportPath = ""
)

$ErrorActionPreference = "Stop"

function Resolve-RepoPath([string] $path) {
    if ([System.IO.Path]::IsPathRooted($path)) {
        return $path
    }
    return (Join-Path (Get-Location) $path)
}

function Format-RelativeName([string] $path) {
    if ([string]::IsNullOrWhiteSpace($path)) {
        return "not-provided"
    }
    return [System.IO.Path]::GetFileName($path)
}

function Get-ReviewPacketSha256 {
    $resolvedPacket = Resolve-RepoPath $ReviewPacket
    if (-not (Test-Path -LiteralPath $resolvedPacket -PathType Leaf)) {
        return "not-provided"
    }
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedPacket).Hash.ToLowerInvariant()
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

function Write-UxSignOffReport(
    [string] $Status,
    [int] $CheckedCount,
    [string[]] $UncheckedItems,
    [string[]] $Issues
) {
    $target = $ReportPath
    if ([string]::IsNullOrWhiteSpace($target)) {
        $packetDirectory = Split-Path -Parent (Resolve-RepoPath $ReviewPacket)
        $target = Join-Path $packetDirectory "ux-signoff-report.md"
    }

    $resolvedReport = [System.IO.Path]::GetFullPath((Resolve-RepoPath $target))
    $reportDirectory = Split-Path -Parent $resolvedReport
    if (-not [string]::IsNullOrWhiteSpace($reportDirectory)) {
        New-Item -ItemType Directory -Force $reportDirectory | Out-Null
    }

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# UX Sign-Off Report")
    $lines.Add("")
    $lines.Add("- Status: $Status")
    $lines.Add("- Source commit: $(Get-SourceCommit)")
    $lines.Add("- Reviewer: $Reviewer")
    $lines.Add("- Review packet: $(Format-RelativeName $ReviewPacket)")
    $lines.Add("- Review packet SHA-256: $(Get-ReviewPacketSha256)")
    $lines.Add("- Checked items: $CheckedCount")
    $lines.Add("- Unchecked items: $($UncheckedItems.Count)")
    $lines.Add("- Issues: $($Issues.Count)")

    if ($UncheckedItems.Count -gt 0) {
        $lines.Add("")
        $lines.Add("## Unchecked Items")
        foreach ($item in $UncheckedItems | Select-Object -First 200) {
            $lines.Add("- $item")
        }
    }

    if ($Issues.Count -gt 0) {
        $lines.Add("")
        $lines.Add("## Issues")
        foreach ($issue in $Issues | Select-Object -First 200) {
            $lines.Add("- $issue")
        }
    }

    Set-Content -LiteralPath $resolvedReport -Value $lines -Encoding UTF8
    Write-Host "UX sign-off report=$resolvedReport"
}

$issues = New-Object System.Collections.Generic.List[string]
$uncheckedItems = New-Object System.Collections.Generic.List[string]
$checkedCount = 0

if ([string]::IsNullOrWhiteSpace($Reviewer)) {
    $issues.Add("Reviewer is required.")
}

$resolvedPacket = Resolve-RepoPath $ReviewPacket
if (-not (Test-Path -LiteralPath $resolvedPacket -PathType Leaf)) {
    $issues.Add("Review packet does not exist: $ReviewPacket")
} else {
    $checkboxCount = 0
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $resolvedPacket) {
        $lineNumber++
        if ($line -notmatch "^\s*-\s+\[(?<mark>[ xX])\]\s+(?<text>.+)$") {
            continue
        }
        $checkboxCount++
        $text = $Matches["text"].Trim()
        if ($Matches["mark"].Equals("x", [System.StringComparison]::OrdinalIgnoreCase)) {
            $checkedCount++
        } else {
            $uncheckedItems.Add("line ${lineNumber}: $text")
        }
    }
    if ($checkboxCount -eq 0) {
        $issues.Add("Review packet contains no checklist items.")
    }
}

if ($uncheckedItems.Count -gt 0) {
    $issues.Add("Review packet still has unchecked items.")
}

if ($issues.Count -gt 0) {
    Write-UxSignOffReport "failed" $checkedCount $uncheckedItems.ToArray() $issues.ToArray()
    foreach ($issue in $issues) {
        [Console]::Error.WriteLine($issue)
    }
    exit 1
}

Write-UxSignOffReport "passed" $checkedCount $uncheckedItems.ToArray() $issues.ToArray()
Write-Host "UX sign-off verification passed."
