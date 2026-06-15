param(
    [ValidateSet("WorkingTree", "GitTracked")]
    [string] $SourceMode = "WorkingTree",

    [string] $ApkPath = "",
    [string] $SbomPath = "",
    [switch] $StrictArtifacts,
    [switch] $StrictSourceArchive
)

$ErrorActionPreference = "Stop"

$mitClaimPattern = "\bMIT License\b|License:\s*MIT\b|SPDX-License-Identifier:\s*MIT\b|(?:licensed|released|distributed|available)\s+under\s+(?:the\s+)?MIT\b|MIT[- ]licensed\b|under\s+(?:the\s+)?MIT\s+terms\b"
$claimPaths = @(
    "LICENSE.md",
    "README.md",
    "CHANGELOG.md",
    "RELEASING.md",
    "THIRD_PARTY_LICENSES.md",
    "docs",
    ".github",
    "metadata",
    "app/src/main/res",
    "Resources"
)
$blockerPaths = @(
    "LICENSE.md",
    "app/src/main/java",
    "app/src/main/res",
    "Resources",
    "THIRD_PARTY_LICENSES.md"
)
$sourceRoots = @(
    "LICENSE.md",
    "README.md",
    "THIRD_PARTY_LICENSES.md",
    "docs",
    "metadata",
    "Resources",
    "settings.gradle",
    "app/src/main/assets",
    "app/src/main/java",
    "app/src/main/res",
    "tcpdump"
)

function Normalize-Path([string] $path) {
    return $path.Replace("\", "/").TrimStart("./")
}

function Get-RelativeRepoPath([string] $fullPath) {
    $root = (Get-Location).Path.TrimEnd("\", "/")
    if ($fullPath.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $fullPath.Substring($root.Length).TrimStart("\", "/")
    }
    return $fullPath
}

function Resolve-RepoPath([string] $path) {
    if ([string]::IsNullOrWhiteSpace($path)) {
        return ""
    }
    if ([System.IO.Path]::IsPathRooted($path)) {
        return $path
    }
    return (Join-Path (Get-Location) $path)
}

function Report-And-Fail([string[]] $lines) {
    foreach ($line in $lines) {
        [Console]::Error.WriteLine($line)
    }
    exit 1
}

function Add-Issue(
    [System.Collections.Generic.List[string]] $issues,
    [string] $title,
    [string[]] $details
) {
    $issues.Add($title)
    foreach ($detail in $details | Select-Object -First 50) {
        $issues.Add("  $detail")
    }
}

function Get-ExistingPaths([string[]] $paths) {
    return $paths | Where-Object { Test-Path -LiteralPath $_ }
}

function Get-WorkingTreeSourceEntries {
    $entries = New-Object System.Collections.Generic.List[string]
    foreach ($root in $sourceRoots) {
        if (-not (Test-Path -LiteralPath $root)) {
            continue
        }
        if (Test-Path -LiteralPath $root -PathType Leaf) {
            $entries.Add((Normalize-Path $root))
            continue
        }
        Get-ChildItem -LiteralPath $root -Recurse -File -ErrorAction SilentlyContinue |
            ForEach-Object {
                $relative = Get-RelativeRepoPath $_.FullName
                $entries.Add((Normalize-Path $relative))
            }
    }
    return $entries.ToArray()
}

function Get-GitTrackedSourceEntries {
    $output = & git ls-files 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "SourceMode GitTracked requires git ls-files to run successfully."
    }
    return $output | ForEach-Object { Normalize-Path $_ }
}

function Get-SourceEntries([string] $mode) {
    if ($mode -eq "GitTracked") {
        return Get-GitTrackedSourceEntries
    }
    return Get-WorkingTreeSourceEntries
}

function Get-ZipEntries([string] $path) {
    if ([string]::IsNullOrWhiteSpace($path)) {
        return @()
    }
    $resolved = Resolve-RepoPath $path
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        throw "Zip path '$path' does not exist."
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($resolved)
    try {
        return $zip.Entries | ForEach-Object { Normalize-Path $_.FullName }
    } finally {
        $zip.Dispose()
    }
}

function Get-SourceArchiveEntries([System.Collections.Generic.List[string]] $issues) {
    $archivePath = Join-Path ([System.IO.Path]::GetTempPath()) `
        ("adaway-source-archive-" + [System.Guid]::NewGuid().ToString("N") + ".zip")
    try {
        $output = & git archive --format=zip --worktree-attributes -o $archivePath HEAD 2>&1
        if ($LASTEXITCODE -ne 0) {
            Add-Issue $issues "Strict source archive mode requires git archive to run successfully." @(
                $output | ForEach-Object { "$_" }
            )
            return @()
        }
        return Get-ZipEntries $archivePath
    } finally {
        if (Test-Path -LiteralPath $archivePath -PathType Leaf) {
            Remove-Item -LiteralPath $archivePath -Force
        }
    }
}

function Get-AndroidSdkDir {
    foreach ($candidate in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and
                (Test-Path -LiteralPath $candidate -PathType Container)) {
            return $candidate
        }
    }

    if (Test-Path -LiteralPath "local.properties" -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath "local.properties" |
            Where-Object { $_ -match "^sdk\.dir=" } |
            Select-Object -First 1
        if ($sdkLine) {
            $sdkDir = $sdkLine.Substring("sdk.dir=".Length).Replace("\:", ":")
            if (Test-Path -LiteralPath $sdkDir -PathType Container) {
                return $sdkDir
            }
        }
    }
    return $null
}

function Find-Aapt {
    $sdkDir = Get-AndroidSdkDir
    if ($null -eq $sdkDir) {
        return $null
    }
    $aaptFiles = Get-ChildItem -LiteralPath (Join-Path $sdkDir "build-tools") `
        -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -in @("aapt", "aapt.exe", "aapt.cmd") } |
        Sort-Object FullName -Descending
    return ($aaptFiles | Select-Object -First 1).FullName
}

function Get-ApkResourceNames(
    [string] $path,
    [string[]] $zipEntries,
    [System.Collections.Generic.List[string]] $issues
) {
    if ([string]::IsNullOrWhiteSpace($path) -or $zipEntries -notcontains "resources.arsc") {
        return @()
    }
    $aapt = Find-Aapt
    if ($null -eq $aapt) {
        if ($StrictArtifacts) {
            Add-Issue $issues "Android aapt is required for strict APK resource boundary checks." @(
                "Set ANDROID_HOME or ANDROID_SDK_ROOT so build-tools/aapt can be found."
            )
        }
        return @()
    }

    $resolved = Resolve-RepoPath $path
    $output = & $aapt dump resources $resolved 2>$null
    if ($LASTEXITCODE -ne 0) {
        if ($StrictArtifacts) {
            Add-Issue $issues "aapt failed to inspect APK resources for license boundary checks." @(
                $path
            )
        }
        return @()
    }
    return $output |
        Select-String -Pattern "org\.adaway:(drawable|mipmap)/[A-Za-z0-9_]+" |
        ForEach-Object { $_.Matches.Value } |
        Sort-Object -Unique
}

function Test-TextLicenseClaims([System.Collections.Generic.List[string]] $issues) {
    $existingClaimPaths = Get-ExistingPaths $claimPaths
    if ($existingClaimPaths.Count -eq 0) {
        throw "No license claim paths found to inspect."
    }

    $licenseText = Get-Content -Raw -LiteralPath "LICENSE.md"
    if ($licenseText -notmatch "GNU General Public License") {
        throw "LICENSE.md must remain GPL while packaged GPL-derived blockers remain."
    }

    $mitClaims = foreach ($path in $existingClaimPaths) {
        Get-ChildItem -LiteralPath $path -Recurse -File -ErrorAction SilentlyContinue |
            Select-String -Pattern $mitClaimPattern
    }

    if ($mitClaims) {
        $gplBlockers = foreach ($path in (Get-ExistingPaths $blockerPaths)) {
            Get-ChildItem -LiteralPath $path -Recurse -File -ErrorAction SilentlyContinue |
                Select-String -Pattern "GNU General Public License|Derived from AdBuster|Dominik Sch"
        }

        if ($gplBlockers) {
            Add-Issue $issues "MIT-branded release wording is blocked while GPL-derived material remains." @(
                "MIT claims:"
            )
            $mitClaims | Select-Object -First 50 | ForEach-Object { $issues.Add("  " + $_.ToString()) }
            $issues.Add("GPL blockers:")
            $gplBlockers | Select-Object -First 50 | ForEach-Object { $issues.Add("  " + $_.ToString()) }
        }
    }
}

function Require-Notice(
    [System.Collections.Generic.List[string]] $issues,
    [string] $thirdPartyText,
    [string] $noticePattern,
    [string] $title,
    [string[]] $details
) {
    if ($thirdPartyText -notmatch $noticePattern) {
        Add-Issue $issues $title $details
    }
}

function Test-SourceBoundaryItems(
    [System.Collections.Generic.List[string]] $issues,
    [string[]] $sourceEntries,
    [string] $thirdPartyText
) {
    $forbiddenSource = $sourceEntries | Where-Object {
        $_ -match "^app/src/main/assets/localhost-2410\.(crt|key)$" -or
        $_ -match "^app/src/main/assets/test\.html$" -or
        $_ -match "^app/src/main/assets/icon\.svg$" -or
        $_ -match "^tcpdump/jni/libpcap/SUNOS4/.*\.o(\.|$)"
    }
    if ($forbiddenSource) {
        Add-Issue $issues "Forbidden source/archive boundary entries remain." $forbiddenSource
    }

    if ($sourceEntries | Where-Object { $_ -match "^Resources/" }) {
        Require-Notice $issues $thirdPartyText "Source-Only Resources Inventory" `
            "Source-only Resources entries require explicit license-boundary inventory." @(
                "Resources/ is present in the source tree."
            )
    }

    if ($sourceEntries | Where-Object { $_ -match "^tcpdump/" }) {
        Require-Notice $issues $thirdPartyText "Tcpdump / Libpcap" `
            "tcpdump source entries require explicit source-only inventory." @(
                "tcpdump/ is present in the source tree."
            )
    }

    if (Test-Path -LiteralPath "settings.gradle" -PathType Leaf) {
        $settings = Get-Content -Raw -LiteralPath "settings.gradle"
        if ($settings -match "['""]\:tcpdump['""]" -and
                $thirdPartyText -match "not part of the app APK build|No current.*packaging") {
            Add-Issue $issues "tcpdump is configured as a Gradle module while notices call it source-only." @(
                "settings.gradle includes :tcpdump."
            )
        }
    }
}

function Test-SourceArchiveBoundaryItems(
    [System.Collections.Generic.List[string]] $issues,
    [string[]] $sourceArchiveEntries
) {
    if ($sourceArchiveEntries.Count -eq 0) {
        return
    }

    $forbiddenSourceArchive = $sourceArchiveEntries | Where-Object {
        $_ -match "^Resources/" -or
        $_ -match "^tcpdump/" -or
        $_ -match "^webserver/" -or
        $_ -match "^app/src/main/assets/localhost-2410\.(crt|key)$" -or
        $_ -match "^app/src/main/assets/test\.html$" -or
        $_ -match "^app/src/main/assets/icon\.svg$" -or
        $_ -match "SUNOS4/nit_if\.o"
    }
    if ($forbiddenSourceArchive) {
        Add-Issue $issues "Forbidden source archive entries detected." $forbiddenSourceArchive
    }
}

function Test-ApkBoundaryItems(
    [System.Collections.Generic.List[string]] $issues,
    [string[]] $apkEntries,
    [string[]] $resourceNames,
    [string] $thirdPartyText
) {
    if ($apkEntries.Count -eq 0) {
        return
    }

    $forbiddenApkEntries = $apkEntries | Where-Object {
        $_ -match "^assets/localhost-2410\.(crt|key)$" -or
        $_ -match "^assets/test\.html$" -or
        $_ -match "^assets/icon\.svg$" -or
        $_ -match "^lib/.*/(libtcpdump|libpcap|libmongoose|libwebserver|libssl|libcrypto).*"
    }
    if ($forbiddenApkEntries) {
        Add-Issue $issues "Forbidden release APK entries detected." $forbiddenApkEntries
    }

    $forbiddenResourceNames = $resourceNames | Where-Object {
        $_ -match "org\.adaway:drawable/paypal" -or
        $_ -match "org\.adaway:drawable/ic_github(?:_|$)"
    }
    if ($forbiddenResourceNames) {
        Add-Issue $issues "Forbidden packaged third-party mark resources detected." $forbiddenResourceNames
    }

    $resourceNoticeRules = @(
        @{ Pattern = "org\.adaway:mipmap/.+icon|org\.adaway:drawable/icon_"; Notice = "Launcher adaptive icon|Launcher icons"; Label = "launcher icon resources" },
        @{ Pattern = "org\.adaway:drawable/logo"; Notice = "App logo"; Label = "app logo resource" }
    )
    foreach ($rule in $resourceNoticeRules) {
        $matches = $resourceNames | Where-Object { $_ -match $rule.Pattern }
        if ($matches) {
            Require-Notice $issues $thirdPartyText $rule.Notice `
                "Packaged $($rule.Label) requires third-party/license inventory." $matches
        }
    }
}

function Get-SbomComponents([string] $path) {
    if ([string]::IsNullOrWhiteSpace($path)) {
        return @()
    }
    $resolved = Resolve-RepoPath $path
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        throw "SBOM path '$path' does not exist."
    }
    $sbom = Get-Content -Raw -LiteralPath $resolved | ConvertFrom-Json
    if ($null -eq $sbom.components) {
        return @()
    }
    return @($sbom.components)
}

function Test-SbomNoticeCoverage(
    [System.Collections.Generic.List[string]] $issues,
    [object[]] $components,
    [string] $thirdPartyText
) {
    if ($components.Count -eq 0) {
        return
    }

    $rules = @(
        @{ Match = { param($c) "$($c.group)" -match "^androidx\." }; Notice = "Android Jetpack / AndroidX"; Label = "AndroidX" },
        @{ Match = { param($c) "$($c.group):$($c.name)" -match "^dnsjava:dnsjava$" }; Notice = "dnsjava"; Label = "dnsjava" },
        @{ Match = { param($c) "$($c.group)" -match "^com\.google\.guava$" }; Notice = "Guava"; Label = "Guava" },
        @{ Match = { param($c) "$($c.group)" -match "^com\.github\.topjohnwu\.libsu$" }; Notice = "libsu"; Label = "libsu" },
        @{ Match = { param($c) "$($c.group)" -match "^com\.google\.android\.material$" }; Notice = "Material Components"; Label = "Material Components" },
        @{ Match = { param($c) "$($c.group)" -match "^com\.squareup\.okhttp3$" }; Notice = "OkHttp"; Label = "OkHttp" },
        @{ Match = { param($c) "$($c.group)" -match "^com\.squareup\.okio$" }; Notice = "Okio"; Label = "Okio" },
        @{ Match = { param($c) "$($c.group):$($c.name)" -match "^net\.java\.dev\.jna:jna$" }; Notice = "JNA"; Label = "JNA" },
        @{ Match = { param($c) "$($c.group)" -match "^org\.pcap4j$" }; Notice = "Pcap4J"; Label = "Pcap4J" },
        @{ Match = { param($c) "$($c.group)" -match "^org\.slf4j$" }; Notice = "SLF4J"; Label = "SLF4J" },
        @{ Match = { param($c) "$($c.group)" -match "^com\.jakewharton\.timber$" }; Notice = "Timber"; Label = "Timber" },
        @{ Match = { param($c) "$($c.group)" -match "^io\.sentry" -or "$($c.name)" -match "sentrystub" }; Notice = "Sentry|sentrystub"; Label = "Sentry/sentrystub" }
    )

    foreach ($rule in $rules) {
        $matched = @($components | Where-Object { & $rule.Match $_ })
        if ($matched.Count -eq 0) {
            continue
        }
        if ($thirdPartyText -notmatch $rule.Notice) {
            $details = $matched | Select-Object -First 20 | ForEach-Object {
                "$($_.group):$($_.name):$($_.version)"
            }
            Add-Issue $issues "SBOM dependency missing THIRD_PARTY_LICENSES.md notice: $($rule.Label)" $details
        }
    }
}

$issues = New-Object System.Collections.Generic.List[string]

Test-TextLicenseClaims $issues

$thirdPartyText = Get-Content -Raw -LiteralPath "THIRD_PARTY_LICENSES.md"
if ($thirdPartyText -notmatch "future-only" -and
        $thirdPartyText -notmatch "not.*MIT" -and
        $thirdPartyText -notmatch "GPL-derived") {
    throw "THIRD_PARTY_LICENSES.md must document the current GPL/MIT boundary."
}

$sourceEntries = @(Get-SourceEntries $SourceMode)
Test-SourceBoundaryItems $issues $sourceEntries $thirdPartyText

$sourceArchiveEntries = @()
if ($StrictSourceArchive) {
    $sourceArchiveEntries = @(Get-SourceArchiveEntries $issues)
    Test-SourceArchiveBoundaryItems $issues $sourceArchiveEntries
}

$apkEntries = @(Get-ZipEntries $ApkPath)
$resourceNames = @(Get-ApkResourceNames $ApkPath $apkEntries $issues)
Test-ApkBoundaryItems $issues $apkEntries $resourceNames $thirdPartyText

$components = @(Get-SbomComponents $SbomPath)
Test-SbomNoticeCoverage $issues $components $thirdPartyText

if ($StrictArtifacts -and [string]::IsNullOrWhiteSpace($ApkPath)) {
    Add-Issue $issues "Strict artifact mode requires -ApkPath." @(
        "Release artifact checks must inspect the selected APK before upload."
    )
} elseif ($StrictArtifacts -and $apkEntries.Count -eq 0) {
    Add-Issue $issues "Strict artifact mode requires a readable APK artifact." @($ApkPath)
}
if ($StrictArtifacts -and [string]::IsNullOrWhiteSpace($SbomPath)) {
    Add-Issue $issues "Strict artifact mode requires -SbomPath." @(
        "Release artifact checks must inspect the generated CycloneDX SBOM before upload."
    )
} elseif ($StrictArtifacts -and $components.Count -eq 0) {
    Add-Issue $issues "Strict artifact mode requires a readable CycloneDX SBOM with components." @($SbomPath)
}

if ($issues.Count -gt 0) {
    Report-And-Fail $issues.ToArray()
}

Write-Host "License boundary check passed: no premature MIT release claim or artifact boundary drift detected."
