#!/usr/bin/env python3
"""Track file-by-file review evidence without treating inventory as review."""

import argparse
import csv
import hashlib
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
LEDGER = Path("tasks/audit-2026-11-25/files.csv")
FIELDS = ("path", "sha256", "kind", "review_status", "review_evidence", "bug_ids")


def tracked_and_new_files():
    output = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT,
    )
    return sorted({Path(path.decode()) for path in output.split(b"\0") if path}
                  | {LEDGER})


def fingerprint(path):
    if path == LEDGER:
        return "self-generated"
    file = ROOT / path
    if file.is_symlink():
        return hashlib.sha256(str(file.readlink()).encode()).hexdigest()
    return hashlib.sha256(file.read_bytes()).hexdigest()


def classify(path):
    name = path.as_posix()
    if name.startswith("tcpdump/jni/"):
        return "native-source-or-vendor"
    if name.startswith("tasks/"):
        return "review-evidence"
    if path.suffix in {".java", ".kt", ".c", ".h", ".cpp", ".py", ".sh", ".ps1", ".rb"}:
        return "test-source" if {"test", "tests", "androidTest"}.intersection(path.parts[:-1]) else "source"
    if "/res/" in name or name.startswith("Resources/"):
        return "resource"
    if path.suffix in {".gradle", ".yml", ".yaml", ".properties", ".pro", ".toml", ".pom", ".module"}:
        return "build-or-configuration"
    if path.suffix in {".png", ".jpg", ".jar", ".aar", ".webp", ".gif", ".pdf", ".zip"}:
        return "binary-asset"
    return "documentation-or-data"


def read_rows(path, required):
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        if not set(required).issubset(reader.fieldnames or []):
            raise ValueError(f"Missing columns in {path}: {required}")
        rows = {}
        for row in reader:
            key = row["path"]
            if key in rows:
                raise ValueError(f"Duplicate file in {path}: {key}")
            rows[key] = row
        return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail for inventory/hash drift")
    parser.add_argument("--record", type=Path, action="append", default=[],
                        help="Import path,sha256,assessment,bug_ids review CSV")
    args = parser.parse_args()
    if args.check and args.record:
        parser.error("--check cannot import review evidence")
    previous = read_rows(ROOT / LEDGER, FIELDS) if (ROOT / LEDGER).exists() else {}
    rows = {}
    for path in tracked_and_new_files():
        if path != LEDGER and not (ROOT / path).exists() and not (ROOT / path).is_symlink():
            continue
        key = path.as_posix()
        digest = fingerprint(path)
        old = previous.get(key, {})
        status = old.get("review_status", "pending")
        if old.get("sha256", digest) != digest and status != "pending":
            status = "stale"
        rows[key] = dict(path=key, sha256=digest, kind=classify(path),
                         review_status=status,
                         review_evidence=old.get("review_evidence", ""),
                         bug_ids=old.get("bug_ids", ""))
    rows[LEDGER.as_posix()].update(review_status="generated",
                                  review_evidence="CSV inventory; not a code-review claim")
    for report in args.record:
        for key, reviewed in read_rows(report, ("path", "sha256", "assessment", "bug_ids")).items():
            if key not in rows or rows[key]["sha256"] != reviewed["sha256"]:
                raise ValueError(f"Review is not for the current file: {key} ({report})")
            if not reviewed["assessment"].strip():
                raise ValueError(f"Empty assessment: {key}")
            rows[key].update(review_status="reviewed",
                             review_evidence=f"{report}: {reviewed['assessment']}",
                             bug_ids=reviewed["bug_ids"])
    if args.check:
        drift = sorted(key for key in rows.keys() | previous.keys()
                       if rows.get(key) != previous.get(key))
        if drift:
            raise SystemExit("Inventory needs refresh:\n" + "\n".join(drift))
    else:
        (ROOT / LEDGER).parent.mkdir(parents=True, exist_ok=True)
        with (ROOT / LEDGER).open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=FIELDS, lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows.values())
    counts = {}
    for row in rows.values():
        counts[row["review_status"]] = counts.get(row["review_status"], 0) + 1
    print(f"{len(rows)} files: {counts}")


if __name__ == "__main__":
    main()
