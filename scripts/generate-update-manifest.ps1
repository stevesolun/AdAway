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
$generator = Join-Path $scriptDir "GenerateUpdateManifest.java"
if (-not (Test-Path -LiteralPath $generator -PathType Leaf)) {
    throw "Update manifest generator was not found: $generator"
}

& $java.Source $generator @Arguments
exit $LASTEXITCODE
