#!/usr/bin/env bash
set -euo pipefail
umask 077

script_dir=$(CDPATH='' cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
config="$script_dir/ssl.conf"
if [[ ! -f "$config" || ! -r "$config" ]]; then
  printf 'Missing readable certificate configuration: %s\n' "$config" >&2
  exit 1
fi
command -v openssl >/dev/null

lock_dir="$script_dir/.localhost.lock"
if ! mkdir -- "$lock_dir"; then
  printf 'Certificate generation already locked: %s\n' "$lock_dir" >&2
  exit 1
fi
work_dir=""
publishing=0

cleanup() {
  local status=$?
  local recovery_failed=0
  trap - EXIT HUP INT TERM
  if [[ "$publishing" == 1 ]]; then
    for extension in crt key; do
      if [[ -f "$work_dir/old.$extension" ]]; then
        mv -f -- "$work_dir/old.$extension" "$script_dir/localhost.$extension" || recovery_failed=1
      else
        rm -f -- "$script_dir/localhost.$extension" || recovery_failed=1
      fi
    done
    if [[ "$recovery_failed" == 1 ]]; then
      printf 'Recovery failed; retain lock and recover originals from: %s\n' "$work_dir" >&2
      exit 1
    fi
  fi
  if [[ -n "$work_dir" ]]; then
    rm -f -- "$work_dir/new.crt" "$work_dir/new.key" \
      "$work_dir/old.crt" "$work_dir/old.key" || status=1
    rmdir -- "$work_dir" || status=1
  fi
  rmdir -- "$lock_dir" || status=1
  exit "$status"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

for extension in crt key; do
  output="$script_dir/localhost.$extension"
  if [[ -L "$output" || ( -e "$output" && ! -f "$output" ) ]]; then
    printf 'Refusing non-regular certificate output: %s\n' "$output" >&2
    exit 1
  fi
done

work_dir=$(mktemp -d "$script_dir/.localhost.XXXXXXXX")
for extension in crt key; do
  if [[ -f "$script_dir/localhost.$extension" ]]; then
    cp -p -- "$script_dir/localhost.$extension" "$work_dir/old.$extension"
  fi
done

# Add compatibility for MINGW
kernel=$(uname -s)
if [[ $kernel == MINGW* ]]; then
  export MSYS_NO_PATHCONV=1
fi

openssl req -x509 -out "$work_dir/new.crt" -keyout "$work_dir/new.key" \
  -newkey rsa:2048 -nodes -sha256 -days 1126 -subj /CN=localhost \
  -extensions EXT -config "$config"
for extension in crt key; do
  if [[ ! -f "$work_dir/new.$extension" || ! -s "$work_dir/new.$extension" || -L "$work_dir/new.$extension" ]]; then
    printf 'OpenSSL did not produce a complete certificate/key pair.\n' >&2
    exit 1
  fi
done

# Keep backups until both same-filesystem replacements succeed.
publishing=1
mv -f -- "$work_dir/new.crt" "$script_dir/localhost.crt"
mv -f -- "$work_dir/new.key" "$script_dir/localhost.key"
publishing=0
