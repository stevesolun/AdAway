#!/usr/bin/env python3
"""Exercise schedule edits and retain Android's actual local backup queue evidence.

Requires installed debug/test APKs and an already enabled, selected local backup
transport on a disposable emulator. This script does not configure a transport.
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


class ProbeError(RuntimeError):
    pass


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
    if not re.search(r"(?m)^OK \(1 tests?\)\s*$", output):
        raise ProbeError("Instrumentation did not report OK (1 test)")
    if not re.search(r"(?m)^INSTRUMENTATION_CODE:\s*-1\s*$", output):
        raise ProbeError("Instrumentation did not finish successfully")
    if re.search(r"FAILURES!!!|INSTRUMENTATION_(?:FAILED|ABORTED)|"
                 r"INSTRUMENTATION_STATUS_CODE:\s*-[2345]\b", output):
        raise ProbeError("Instrumentation reported a failure, error, or skipped test")


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

    def check_transport(self, name):
        output = self.run(name, ["shell", "bmgr", "list", "transports"])
        active = re.findall(r"(?m)^\s*\*\s+(\S+)\s*$", output)
        if len(active) != 1 or active[0] not in LOCAL_TRANSPORTS:
            raise ProbeError("Refusing backup: active transport must be Android LocalTransport")

    def instrument(self, test_case, phase):
        command = [
            "shell", "am", "instrument", "-w", "-r", "-e", "class", SELECTOR,
            "-e", "backupNotificationCase", test_case,
            "-e", "backupNotificationPhase", phase,
        ]
        if self.device_evidence_dir:
            command.extend(["-e", "evidenceDir", self.device_evidence_dir])
        output = self.run(f"{test_case}-{phase}", command + [RUNNER], timeout=180)
        assert_instrumentation_passed(output)

    def queue_contains_app(self, name):
        output = self.run(name, ["shell", "dumpsys", "backup"])
        return PACKAGE in pending_packages(output)

    def drain_queue(self, test_case):
        # backupnow can finish an explicit request while the earlier dataChanged
        # request remains queued. Run the scheduled pass too, on local transport.
        self.check_transport(f"{test_case}-drain-transport")
        self.run(f"{test_case}-drain-run", ["shell", "bmgr", "run"])
        deadline = time.monotonic() + 30.0
        stable_absence = 0
        sample = 0
        while True:
            output = self.run(f"{test_case}-before-{sample:02d}",
                              ["shell", "dumpsys", "backup"])
            absent = PACKAGE not in pending_packages(output)
            idle = re.search(r"(?m)^\s*No backups running\s*$", output) is not None
            stable_absence = stable_absence + 1 if absent and idle else 0
            if stable_absence >= 2:
                return
            if time.monotonic() >= deadline:
                raise ProbeError("Local backup queue did not become stably empty for the app "
                                 "with no backups running; act phase was not run")
            sample += 1
            time.sleep(min(0.5, max(0, deadline - time.monotonic())))

    def exercise(self, test_case):
        self.instrument(test_case, "seed")
        self.check_transport(f"{test_case}-transport")
        output = self.run(f"{test_case}-backup", [
            "shell", "bmgr", "backupnow", PACKAGE,
        ], timeout=180)
        if not re.search(r"(?m)^Package org\.adaway with result:\s*Success\s*$", output) \
                or not re.search(r"(?m)^Backup finished with result:\s*Success\s*$", output):
            raise ProbeError("Local backupnow did not report package and overall success")
        self.drain_queue(test_case)
        self.instrument(test_case, "act")
        deadline = time.monotonic() + 1.0
        sample = 0
        while True:
            pending = self.queue_contains_app(f"{test_case}-after-{sample:02d}")
            if pending or time.monotonic() >= deadline:
                return int(pending)
            sample += 1
            time.sleep(min(0.1, max(0, deadline - time.monotonic())))


def parse_arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
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
        qemu = controller.run("emulator-identity", ["shell", "getprop", "ro.kernel.qemu"])
        if qemu.strip() != "1":
            raise ProbeError("Refusing backup: ro.kernel.qemu does not identify an emulator")
        controller.check_transport("active-transport")
        failed = False
        for test_case in arguments.cases:
            actual = ""
            detail = ""
            try:
                actual = controller.exercise(test_case)
                result = "PASS" if actual == CASES[test_case] else "FAIL"
                if result == "FAIL":
                    detail = "Android pending queue did not match the expected edit behavior"
            except ProbeError as error:
                result = "ERROR"
                detail = str(error)
            writer.writerow([test_case, CASES[test_case], actual, result, detail])
            result_file.flush()
            print(f"{test_case}: expected pending={CASES[test_case]}, "
                  f"actual={actual if actual != '' else 'unavailable'}; {result}", flush=True)
            if detail:
                print(detail, file=sys.stderr, flush=True)
            failed |= result != "PASS"
    print(f"Evidence: {arguments.evidence_dir}")
    return int(failed)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ProbeError, OSError) as error:
        print(f"Backup queue check stopped: {error}", file=sys.stderr)
        sys.exit(1)
