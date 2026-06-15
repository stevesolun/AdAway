#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage: generate-update-manifest.sh --apk PATH --version VERSION --version-code CODE \
  --cert-sha256 HEX --apk-url URL --private-key-base64 BASE64 --out PATH

Optional:
  --public-key-base64 BASE64  Verify the generated signature with the SPKI public key
  --channel VALUE             Default: stable
  --store VALUE               Default: adaway
  --changelog VALUE           Default: See release notes for VERSION.
  --valid-days DAYS           Default: 14
EOF
}

APK=""
VERSION=""
VERSION_CODE=""
CERT_SHA256=""
APK_URL=""
PRIVATE_KEY_BASE64="${UPDATE_MANIFEST_PRIVATE_KEY_BASE64:-}"
PUBLIC_KEY_BASE64="${UPDATE_MANIFEST_PUBLIC_KEY_BASE64:-}"
OUT=""
CHANNEL="stable"
STORE="adaway"
CHANGELOG=""
VALID_DAYS="14"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk) APK="${2:-}"; shift 2 ;;
    --version) VERSION="${2:-}"; shift 2 ;;
    --version-code) VERSION_CODE="${2:-}"; shift 2 ;;
    --cert-sha256) CERT_SHA256="${2:-}"; shift 2 ;;
    --apk-url) APK_URL="${2:-}"; shift 2 ;;
    --private-key-base64) PRIVATE_KEY_BASE64="${2:-}"; shift 2 ;;
    --public-key-base64) PUBLIC_KEY_BASE64="${2:-}"; shift 2 ;;
    --out) OUT="${2:-}"; shift 2 ;;
    --channel) CHANNEL="${2:-}"; shift 2 ;;
    --store) STORE="${2:-}"; shift 2 ;;
    --changelog) CHANGELOG="${2:-}"; shift 2 ;;
    --valid-days) VALID_DAYS="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$APK" || -z "$VERSION" || -z "$VERSION_CODE" || -z "$CERT_SHA256" ||
      -z "$APK_URL" || -z "$PRIVATE_KEY_BASE64" || -z "$OUT" ]]; then
  usage
  exit 2
fi
if [[ ! -s "$APK" ]]; then
  echo "APK does not exist or is empty: $APK" >&2
  exit 1
fi
if ! [[ "$VERSION_CODE" =~ ^[0-9]+$ ]]; then
  echo "version-code must be an integer." >&2
  exit 1
fi
if ! [[ "$VALID_DAYS" =~ ^[0-9]+$ ]] || [[ "$VALID_DAYS" -lt 1 || "$VALID_DAYS" -gt 14 ]]; then
  echo "valid-days must be between 1 and 14." >&2
  exit 1
fi

APK_SHA256="$(sha256sum "$APK" | awk '{ print tolower($1) }')"
CERT_SHA256="$(printf '%s' "$CERT_SHA256" | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')"
if ! [[ "$APK_SHA256" =~ ^[0-9a-f]{64}$ && "$CERT_SHA256" =~ ^[0-9a-f]{64}$ ]]; then
  echo "APK and certificate hashes must be 64-character SHA-256 hex values." >&2
  exit 1
fi
if [[ -z "$CHANGELOG" ]]; then
  CHANGELOG="See release notes for $VERSION."
fi
export APK_URL
python3 - <<'PY'
import os
import sys
from urllib.parse import urlparse

allowed_hosts = {"app.adaway.org", "github.com"}
parsed = urlparse(os.environ["APK_URL"])
host = (parsed.hostname or "").lower()
github_release_prefix = "/stevesolun/AdAway/releases/download/"
if parsed.scheme.lower() != "https":
    print("apk-url must use HTTPS.", file=sys.stderr)
    sys.exit(1)
if host not in allowed_hosts:
    print("apk-url host is not in the release allowlist.", file=sys.stderr)
    sys.exit(1)
if parsed.port not in (None, 443):
    print("apk-url must use the default HTTPS port.", file=sys.stderr)
    sys.exit(1)
if host == "github.com" and (
    not parsed.path.startswith(github_release_prefix)
    or not parsed.path.lower().endswith(".apk")
):
    print("apk-url must point to the fork GitHub APK release.", file=sys.stderr)
    sys.exit(1)
if parsed.username or parsed.password or parsed.fragment:
    print("apk-url must not include user info or a fragment.", file=sys.stderr)
    sys.exit(1)
PY

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT
PAYLOAD="$TMPDIR/payload.json"
SIGNATURE_BIN="$TMPDIR/payload.sig"
PRIVATE_KEY="$TMPDIR/update-manifest-private.pem"
PUBLIC_KEY_DER="$TMPDIR/update-manifest-public.der"

printf '%s' "$PRIVATE_KEY_BASE64" | base64 -d > "$PRIVATE_KEY"
chmod 600 "$PRIVATE_KEY"

export VERSION VERSION_CODE CHANGELOG APK_SHA256 CERT_SHA256 APK_URL CHANNEL STORE VALID_DAYS
python3 - <<'PY' > "$PAYLOAD"
import datetime
import json
import os

expires = (
    datetime.datetime.now(datetime.timezone.utc)
    + datetime.timedelta(days=int(os.environ["VALID_DAYS"]))
).replace(microsecond=0).isoformat().replace("+00:00", "Z")

payload = {
    "version": os.environ["VERSION"],
    "versionCode": int(os.environ["VERSION_CODE"]),
    "changelog": os.environ["CHANGELOG"],
    "apkSha256": os.environ["APK_SHA256"],
    "signingCertificateSha256": os.environ["CERT_SHA256"],
    "apkUrl": os.environ["APK_URL"],
    "channel": os.environ["CHANNEL"],
    "store": os.environ["STORE"],
    "expiresAt": expires,
}
print(json.dumps(payload, separators=(",", ":"), ensure_ascii=False), end="")
PY

openssl dgst -sha256 -sign "$PRIVATE_KEY" -out "$SIGNATURE_BIN" "$PAYLOAD"

if [[ -n "$PUBLIC_KEY_BASE64" ]]; then
  printf '%s' "$PUBLIC_KEY_BASE64" | base64 -d > "$PUBLIC_KEY_DER"
  openssl dgst -sha256 -verify "$PUBLIC_KEY_DER" -keyform DER \
    -signature "$SIGNATURE_BIN" "$PAYLOAD" >/dev/null
fi

SIGNATURE="$(openssl base64 -A -in "$SIGNATURE_BIN")"
export PAYLOAD SIGNATURE OUT
mkdir -p "$(dirname "$OUT")"
python3 - <<'PY'
import json
import os
from pathlib import Path

payload = Path(os.environ["PAYLOAD"]).read_text(encoding="utf-8")
envelope = {
    "payload": payload,
    "signature": os.environ["SIGNATURE"],
}
Path(os.environ["OUT"]).write_text(
    json.dumps(envelope, separators=(",", ":"), ensure_ascii=False),
    encoding="utf-8",
)
PY

(cd "$(dirname "$OUT")" && sha256sum "$(basename "$OUT")") > "$OUT.sha256"
echo "Generated signed update manifest: $OUT"
