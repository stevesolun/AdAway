#!/usr/bin/env python3
"""Exercise schedule edits and retain Android's actual local backup queue evidence.

Requires installed debug/test APKs and an already enabled, selected local backup
transport on a disposable emulator. This script does not configure a transport.
Pass --disposable-fixture to acknowledge that each seed clears application state.
Emulator identity, enabled backup and LocalTransport selection are rechecked before
each mutation; an unconfirmed prerequisite stops the batch.

A negative result means the package was absent from sampled idle queue snapshots
during the post-act observation window, not that no request was ever enqueued.
"""

import argparse
import csv
from datetime import datetime, timezone
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time


PACKAGE = "org.adaway"
RUNNER = "org.adaway.test/androidx.test.runner.AndroidJUnitRunner"
SELECTOR = (
    "org.adaway.ui.hosts.ScheduleBackupNotificationInstrumentedTest"
    "#exerciseBackupNotificationCase"
)
CASES = {
    "create": 1,
    "setTime": 1,
    "global": 1,
    "unchanged": 0,
    "housekeeping": 0,
    "restoreInput": 0,
    "cancel": 0,
    "managerUrl": 1,
    "editorUrl": 1,
    "editorNoop": 0,
    "globalDisable": 1,
    "setOff": 1,
    "membership": 1,
    "rename": 1,
    "delete": 1,
}
LOCAL_TRANSPORTS = {
    "com.android.localtransport/.LocalTransport",
    "com.android.localtransport/com.android.localtransport.LocalTransport",
    "android/com.android.internal.backup.LocalTransport",
}
OBSERVATION_SECONDS = 1.0


class ProbeError(RuntimeError):
    pass


class UnsafeEnvironment(ProbeError):
    """Do not attempt any further mutations in this batch."""


def pending_packages(output):
    """Parse only the pending queue, never package names elsewhere in dumpsys."""
    lines = output.splitlines()
    headers = [
        (index, match)
        for index, line in enumerate(lines)
        if (match := re.fullmatch(r"(\s*)Pending key/value backup:\s*(\d+)\s*", line))
    ]
    if len(headers) != 1:
        raise ProbeError("Expected exactly one Pending key/value backup section")
    index, header = headers[0]
    indentation = len(header.group(1))
    packages = []
    for line in lines[index + 1:]:
        if not line.strip():
            continue
        if len(line) - len(line.lstrip()) <= indentation:
            break
        match = re.fullmatch(r"\s*BackupRequest\{pkg=([^\s{}]+)\}\s*", line)
        if not match:
            raise ProbeError("Unrecognized entry in the pending backup queue")
        packages.append(match.group(1))
    if len(packages) != int(header.group(2)):
        raise ProbeError("Pending backup count does not match parsed queue entries")
    return set(packages)


def assert_instrumentation_passed(output):
    if re.search(r"FAILURES!!!|INSTRUMENTATION_(?:FAILED|ABORTED)", output):
        raise ProbeError("Instrumentation reported a failure or abort")
    lines = output.splitlines()
    try:
        end = lines.index("INSTRUMENTATION_RESULT: stream=")
    except ValueError as error:
        raise ProbeError("Missing instrumentation result stream") from error

    codes = []
    fields = {}
    for line in lines[:end]:
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, equal, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            if (not equal or key in fields
                    or key not in {"class", "test", "current", "numtests", "id", "stream"}):
                raise ProbeError(f"Unexpected instrumentation status field: {key}")
            fields[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            if (f"{fields.get('class')}#{fields.get('test')}" != SELECTOR
                    or fields.get("current", "1") != "1" or fields.get("numtests", "1") != "1"):
                raise ProbeError(f"Expected a single test record for {SELECTOR}")
            code = line[len("INSTRUMENTATION_STATUS_CODE: "):]
            if code == "0" and fields.get("stream", ".") != ".":
                raise ProbeError("Conflicting instrumentation terminal stream")
            codes.append(code)
            fields.clear()
        elif line.startswith("INSTRUMENTATION_"):
            raise ProbeError("Unexpected instrumentation record before the result stream")
    if fields or codes != ["1", "0"]:
        raise ProbeError(f"Missing or conflicting start/success records for {SELECTOR}")

    summary = "\n".join(lines[end + 1:])
    if not re.fullmatch(r"\s*(?:Time: [\d.,]+\s+)?OK \(1 tests?\)\s+"
                        r"INSTRUMENTATION_CODE: -1\s*", summary):
        raise ProbeError("Expected one successful test summary and final runner code")


class Controller:
    def __init__(self, arguments):
        self.adb = [arguments.adb, "-s", arguments.serial]
        self.evidence = arguments.evidence_dir
        self.device_evidence_dir = arguments.device_evidence_dir

    def run(self, name, arguments, timeout=30):
        command = self.adb + arguments
        path = self.evidence / (name + ".txt")
        # Exclusive creation protects earlier evidence from an accidental rerun.
        with path.open("x", encoding="utf-8") as evidence:
            evidence.write("UTC: " + datetime.now(timezone.utc).isoformat() + "\n")
            evidence.write("Command: " + shlex.join(command) + "\n\n")
            try:
                result = subprocess.run(command, capture_output=True, text=True,
                                        timeout=timeout, check=False)
            except subprocess.TimeoutExpired as error:
                for stream in (error.stdout, error.stderr):
                    if stream:
                        evidence.write(stream.decode(errors="replace")
                                       if isinstance(stream, bytes) else stream)
                evidence.write(f"\nTimed out after {timeout} seconds\n")
                raise ProbeError(f"{name}: command timed out") from error
            except OSError as error:
                evidence.write(str(error) + "\n")
                raise ProbeError(f"{name}: could not execute adb: {error}") from error
            evidence.write(result.stdout)
            if result.stderr:
                evidence.write("\n--- stderr ---\n" + result.stderr)
            evidence.write(f"\nExit code: {result.returncode}\n")
        if result.returncode:
            raise ProbeError(f"{name}: command exited {result.returncode}")
        return result.stdout

    def run_mutation(self, name, arguments, timeout=30):
        # Recheck before each seed, backupnow, scheduled pass and act. Even a
        # failed read makes the fixture unsafe to use for subsequent cases.
        try:
            qemu = self.run(name + "-qemu", ["shell", "getprop", "ro.kernel.qemu"])
            if qemu.strip() != "1":
                raise ProbeError("ro.kernel.qemu does not identify an emulator")
            enabled = self.run(name + "-enabled", ["shell", "bmgr", "enabled"])
            if enabled.strip() != "Backup Manager currently enabled":
                raise ProbeError("backup is not confirmed enabled")
            output = self.run(name + "-transport", ["shell", "bmgr", "list", "transports"])
            selections = [line.split() for line in output.splitlines()
                          if line.lstrip().startswith("*")]
            active = selections[0] if len(selections) == 1 else []
            if len(active) != 2 or active[0] != "*" or active[1] not in LOCAL_TRANSPORTS:
                raise ProbeError("active transport must be Android LocalTransport")
        except ProbeError as error:
            raise UnsafeEnvironment(f"Refusing {name}; stopping batch: {error}") from error
        return self.run(name, arguments, timeout=timeout)

    def instrument(self, test_case, phase):
        command = [
            "shell", "am", "instrument", "-w", "-r", "-e", "class", SELECTOR,
            "-e", "backupNotificationCase", test_case,
            "-e", "backupNotificationPhase", phase,
        ]
        if self.device_evidence_dir:
            command.extend(["-e", "evidenceDir", self.device_evidence_dir])
        output = self.run_mutation(f"{test_case}-{phase}", command + [RUNNER], timeout=180)
        assert_instrumentation_passed(output)

    def queue_sample(self, name):
        output = self.run(name, ["shell", "dumpsys", "backup"])
        states = [line.strip() for line in output.splitlines() if line.strip() in
                  {"No backups running", "Backup currently running", "Backup in progress"}]
        return PACKAGE in pending_packages(output), states == ["No backups running"]

    def drain_queue(self, test_case):
        # backupnow can finish an explicit request while the earlier dataChanged
        # request remains queued. Run the scheduled pass too, on local transport.
        self.run_mutation(f"{test_case}-drain-run", ["shell", "bmgr", "run"])
        deadline = time.monotonic() + 30.0
        stable_absence = 0
        sample = 0
        while True:
            pending, idle = self.queue_sample(f"{test_case}-before-{sample:02d}")
            stable_absence = stable_absence + 1 if not pending and idle else 0
            if stable_absence >= 2:
                return
            if time.monotonic() >= deadline:
                raise ProbeError("Local backup queue did not become stably empty for the app "
                                 "with no backups running; act phase was not run")
            sample += 1
            time.sleep(min(0.5, max(0, deadline - time.monotonic())))

    def exercise(self, test_case):
        self.instrument(test_case, "seed")
        output = self.run_mutation(f"{test_case}-backup", [
            "shell", "bmgr", "backupnow", PACKAGE,
        ], timeout=180)
        if not re.search(r"(?m)^Package org\.adaway with result:\s*Success\s*$", output) \
                or not re.search(r"(?m)^Backup finished with result:\s*Success\s*$", output):
            raise ProbeError("Local backupnow did not report package and overall success")
        self.drain_queue(test_case)
        self.instrument(test_case, "act")
        deadline = time.monotonic() + OBSERVATION_SECONDS
        sample = 0
        while True:
            name = f"{test_case}-after-{sample:02d}"
            pending, idle = self.queue_sample(name)
            if not idle:
                raise ProbeError(f"{name}: backup active or idle state unknown; "
                                 "queue observation is indeterminate")
            if pending or time.monotonic() >= deadline:
                return int(pending)
            sample += 1
            time.sleep(min(0.1, max(0, deadline - time.monotonic())))


def parse_arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--disposable-fixture", action="store_true", required=True,
                        help="Acknowledge this is your disposable emulator: seed clears app state")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--evidence-dir", required=True, type=Path)
    parser.add_argument("--device-evidence-dir",
                        help="Optional single directory name for probe UI screenshots on device")
    parser.add_argument("--cases", default=",".join(CASES),
                        help="Comma-separated probe case names (default: all focused cases)")
    arguments = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", arguments.serial):
        parser.error("--serial must identify a disposable emulator (emulator-<port>)")
    arguments.cases = arguments.cases.split(",")
    if not arguments.cases or any(case not in CASES for case in arguments.cases):
        parser.error("--cases must contain only these names: " + ",".join(CASES))
    if len(set(arguments.cases)) != len(arguments.cases):
        parser.error("--cases must not contain duplicate case names")
    if arguments.device_evidence_dir and not re.fullmatch(
            r"[A-Za-z0-9_-][A-Za-z0-9._-]*", arguments.device_evidence_dir):
        parser.error("--device-evidence-dir must be a single simple directory name")
    return arguments


def main():
    arguments = parse_arguments()
    arguments.evidence_dir.mkdir(parents=True, exist_ok=True)
    controller = Controller(arguments)
    result_path = arguments.evidence_dir / "queue-results.csv"
    with result_path.open("x", encoding="utf-8", newline="") as result_file:
        writer = csv.writer(result_file)
        writer.writerow(["case", "expected_pending", "actual_pending", "result", "detail"])
        result_file.flush()
        failed = False
        for test_case in arguments.cases:
            actual = ""
            detail = ""
            stop_batch = False
            try:
                actual = controller.exercise(test_case)
                result = "PASS" if actual == CASES[test_case] else "FAIL"
                if actual == 0:
                    detail = (f"{PACKAGE} absent in sampled idle snapshots only "
                              f"({OBSERVATION_SECONDS:g} s post-act observation window)")
                elif result == "FAIL":
                    detail = "Android pending queue did not match the expected edit behavior"
            except ProbeError as error:
                result = "ERROR"
                detail = str(error)
                stop_batch = isinstance(error, UnsafeEnvironment)
            writer.writerow([test_case, CASES[test_case], actual, result, detail])
            result_file.flush()
            print(f"{test_case}: expected pending={CASES[test_case]}, "
                  f"actual={actual if actual != '' else 'unavailable'}; {result}", flush=True)
            if detail:
                print(detail, file=sys.stdout if result == "PASS" else sys.stderr, flush=True)
            failed |= result != "PASS"
            if stop_batch:
                break
    print(f"Evidence: {arguments.evidence_dir}")
    return int(failed)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ProbeError, OSError) as error:
        print(f"Backup queue check stopped: {error}", file=sys.stderr)
        sys.exit(1)
