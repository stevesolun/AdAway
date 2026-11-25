#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# Only the DSL is evaluated. No production key or APK is used.
if ./gradlew --dependency-verification=strict :app:help \
        -I scripts/tests/fixtures/unsafe-debug-signing.init.gradle \
        "-PsigningStoreLocation=$work/not-a-keystore" \
        -PsigningStorePassword=fixture -PsigningKeyAlias=fixture -PsigningKeyPassword=fixture \
        >"$work/output" 2>&1; then
    echo "Debug signing unexpectedly accepted the production key configuration." >&2
    exit 1
fi
if ! grep -Fq "Refusing to sign debuggable build type debug with the release key." "$work/output"; then
    cat "$work/output" >&2
    exit 1
fi
echo "Rejected production signing on a debuggable build type."
