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

function Get-ReviewPacketSourceCommit {
    $resolvedPacket = Resolve-RepoPath $ReviewPacket
    if (-not (Test-Path -LiteralPath $resolvedPacket -PathType Leaf)) {
        return "not-provided"
    }
    foreach ($line in Get-Content -LiteralPath $resolvedPacket) {
        if ($line -match "^\s*-\s+Source commit:\s*(?<commit>\S+)\s*$") {
            return $Matches["commit"].Trim().ToLowerInvariant()
        }
    }
    return "not-provided"
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

function Get-ReviewPacketStructureIssues([string[]] $Lines) {
    # Matches Write-UxMatrixReviewManifest. Generator-backed tests detect schema drift.
    $variants = @("baseline", "font-1.3", "font-1.6", "font-1.3-rtl", "font-1.6-rtl")
    $screens = @("home", "discover", "sources", "more", "domain_checker",
            "onboarding", "custom_rules", "update")
    $globalChecks = @(
        "Text is readable without clipping, ellipsizing, or overlap.",
        "Touch targets remain reachable and visually stable.",
        "The AdAway bird remains the first-screen brand signal.",
        "FABs, cards, and bottom navigation do not hide primary actions.",
        "RTL variants keep navigation, cards, and rule rows correctly anchored."
    )
    $required = @("title", "source commit", "global checklist")
    $required += @($globalChecks | ForEach-Object { "global/$_" })
    foreach ($variant in $variants) {
        $required += "section/$variant", "screens/$variant"
        $required += @($screens | ForEach-Object { "$variant/$_" })
    }
    $counts = @{}
    foreach ($key in $required) { $counts[$key] = 0 }
    $section = ""
    $inScreens = $false
    $lineNumber = 0
    foreach ($line in $Lines) {
        $lineNumber++
        $trimmed = $line.Trim()
        # Hidden/example checklists are not review evidence.
        if ($trimmed -match '^(`{3,}|~{3,})' -or $line.Contains('<!--')) {
            "line ${lineNumber}: Fenced blocks and HTML comments are not allowed in review packets."
        }
        if ($trimmed -match '^#{1,6}\s+') {
            $section = ""
            $inScreens = $false
            if ($trimmed -ceq "# UX Matrix Review Packet") {
                $counts["title"]++
            } elseif ($trimmed -cmatch '^## (?<variant>.+)$' -and
                    $variants -ccontains $Matches["variant"]) {
                $section = $Matches["variant"]
                $counts["section/$section"]++
            } else {
                "line ${lineNumber}: Unexpected review packet heading: $trimmed"
            }
            continue
        }
        if ($trimmed -match '^-\s+Source commit:') {
            $counts["source commit"]++
            continue
        }
        if ($trimmed -ceq "Manual sign-off checklist:") {
            $counts["global checklist"]++
            $section = "global"
            $inScreens = $false
            continue
        }
        if ($trimmed -ceq "- Screens:") {
            if ($variants -ccontains $section) {
                $counts["screens/$section"]++
                $inScreens = $true
            } else {
                "line ${lineNumber}: Screens list is outside a variant section."
            }
            continue
        }
        if ($trimmed -notmatch '^-\s+\[[ xX]\]\s+(?<text>.+)$') { continue }
        $text = $Matches["text"].Trim()
        if ($section -ceq "global" -and $globalChecks -ccontains $text) {
            $counts["global/$text"]++
        } elseif ($variants -ccontains $section -and $inScreens -and
                $text -cmatch '^(?<screen>[a-z_]+) - (?<path>.+)$' -and
                $screens -ccontains $Matches["screen"]) {
            $screen = $Matches["screen"]
            $path = $Matches["path"]
            $counts["$section/$screen"]++
            # Both layouts are emitted by Get-UxScreenshotPath. CI receives only the packet.
            if ($path -cne "$section/$screen.png" -and
                    $path -cne "$section/ux-matrix/$screen.png") {
                "line ${lineNumber}: Invalid screenshot path for ${section}/${screen}: $path"
            }
        } else {
            "line ${lineNumber}: Unexpected or misplaced checklist item: $text"
        }
    }
    foreach ($key in $required) {
        if ($counts[$key] -ne 1) {
            "Review packet requires exactly one '$key' entry; found $($counts[$key])."
        }
    }
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
    $lines.Add("- Review packet source commit: $(Get-ReviewPacketSourceCommit)")
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
    $packetLines = @(Get-Content -LiteralPath $resolvedPacket)
    foreach ($issue in Get-ReviewPacketStructureIssues $packetLines) {
        $issues.Add($issue)
    }
    $checkboxCount = 0
    $lineNumber = 0
    foreach ($line in $packetLines) {
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

    $currentSourceCommit = Get-SourceCommit
    $reviewPacketSourceCommit = Get-ReviewPacketSourceCommit
    $commitPattern = "^[0-9a-fA-F]{40}$"
    if ($reviewPacketSourceCommit -notmatch $commitPattern) {
        $issues.Add("Review packet source commit is missing or invalid.")
    }
    if ($currentSourceCommit -notmatch $commitPattern) {
        $issues.Add("Current source commit is missing or invalid.")
    }
    if ($reviewPacketSourceCommit -match $commitPattern -and
            $currentSourceCommit -match $commitPattern -and
            $reviewPacketSourceCommit -ne $currentSourceCommit) {
        $issues.Add("Review packet source commit '$reviewPacketSourceCommit' does not " +
                "match current source commit '$currentSourceCommit'.")
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
