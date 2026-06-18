[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $Arguments
)

$ErrorActionPreference = "Stop"

$java = Get-Command "java" -ErrorAction SilentlyContinue
if (-not $java) {
    throw "java was not found. Install JDK 21 and ensure java is on PATH."
}

$scriptDir = Split-Path -Parent $PSCommandPath
$verifier = Join-Path $scriptDir "VerifyReleaseArtifacts.java"
if (-not (Test-Path -LiteralPath $verifier -PathType Leaf)) {
    throw "Release artifact verifier was not found: $verifier"
}

& $java.Source $verifier @Arguments
exit $LASTEXITCODE
