import argparse
import csv
from contextlib import redirect_stderr, redirect_stdout
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import check_schedule_backup_queue as probe


# Transcript from the saved API 34 create-act run, without the host log wrapper.
TRANSCRIPT = """INSTRUMENTATION_STATUS: class=org.adaway.ui.hosts.ScheduleBackupNotificationInstrumentedTest
INSTRUMENTATION_STATUS: current=1
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=1
INSTRUMENTATION_STATUS: stream=
org.adaway.ui.hosts.ScheduleBackupNotificationInstrumentedTest:
INSTRUMENTATION_STATUS: test=exerciseBackupNotificationCase
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: class=org.adaway.ui.hosts.ScheduleBackupNotificationInstrumentedTest
INSTRUMENTATION_STATUS: current=1
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=1
INSTRUMENTATION_STATUS: stream=.
INSTRUMENTATION_STATUS: test=exerciseBackupNotificationCase
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_RESULT: stream=

Time: 0.043

OK (1 test)


INSTRUMENTATION_CODE: -1
"""
# Relevant sections of unchanged-after-00. An unrelated request is pending.
ABSENT = """Backup Manager is enabled / setup complete / not pending init
Auto-restore is enabled
No backups running
Framework scheduling is enabled
Pending key/value backup: 1
    BackupRequest{pkg=com.google.android.inputmethod.latin}
Full backup queue:147
"""
LOCAL = "  * com.android.localtransport/.LocalTransport\n"
ENABLED = "Backup Manager currently enabled\n"


class InstrumentationEvidenceTest(unittest.TestCase):
    def test_recorded_method(self):
        probe.assert_instrumentation_passed(TRANSCRIPT)

    def test_incomplete_or_conflicting_results(self):
        start, terminal = TRANSCRIPT.split("INSTRUMENTATION_STATUS_CODE: 1\n")
        records, footer = TRANSCRIPT.split("INSTRUMENTATION_RESULT:", 1)
        for reason, transcript in {
            "wrong method": TRANSCRIPT.replace("test=exerciseBackupNotificationCase", "test=other"),
            "wrong class at start": TRANSCRIPT.replace("class=org.adaway", "class=other", 1),
            "missing terminal method": TRANSCRIPT.replace("stream=.\nINSTRUMENTATION_STATUS: test=exerciseBackupNotificationCase", "stream=."),
            "error with green footer": TRANSCRIPT.replace("STATUS_CODE: 0", "STATUS_CODE: -1"),
            "skipped": TRANSCRIPT.replace("STATUS_CODE: 0", "STATUS_CODE: -3"),
            "duplicate start": start + "INSTRUMENTATION_STATUS_CODE: 1\n" + TRANSCRIPT,
            "missing start": terminal,
            "missing terminal": TRANSCRIPT.replace("INSTRUMENTATION_STATUS_CODE: 0\n", ""),
            "footer alone": "INSTRUMENTATION_RESULT:" + footer,
            "duplicate method field": TRANSCRIPT.replace("test=exercise", "test=other\nINSTRUMENTATION_STATUS: test=exercise", 1),
            "wrong count": TRANSCRIPT.replace("numtests=1", "numtests=2"),
            "extra test": records + TRANSCRIPT,
            "early runner code": "INSTRUMENTATION_CODE: -1\n" + TRANSCRIPT,
            "conflicting runner codes": TRANSCRIPT + "INSTRUMENTATION_CODE: 0\n",
            "conflicting result": TRANSCRIPT.replace("INSTRUMENTATION_CODE:", "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE:"),
            "conflicting summary": TRANSCRIPT.replace("OK (1 test)", "OK (2 tests)\nOK (1 test)"),
            "failed test stream": TRANSCRIPT.replace("stream=.", "stream=F"),
            "failure marker": TRANSCRIPT.replace("stream=.", "stream=FAILURES!!!"),
            "missing summary": TRANSCRIPT.replace("OK (1 test)", ""),
            "failed run": TRANSCRIPT.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0"),
        }.items():
            with self.subTest(reason=reason), self.assertRaises(probe.ProbeError):
                probe.assert_instrumentation_passed(transcript)


class RunnerTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.args = argparse.Namespace(adb="adb", serial="emulator-1234",
                                       evidence_dir=Path(temp.name), device_evidence_dir=None,
                                       disposable_fixture=True, cases=["unchanged"])
        self.now = 0.0
        self.mutations = []
        self.after = [ABSENT]
        self.enabled = ENABLED
        self.transport = LOCAL
        self.qemu = "1"
        self.change_after = {}

    def command(self, name, arguments, timeout=30):
        command = arguments[1:]
        if command == ["getprop", "ro.kernel.qemu"]:
            return self.qemu
        if command == ["bmgr", "enabled"]:
            if isinstance(self.enabled, Exception):
                raise self.enabled
            return self.enabled
        if command == ["bmgr", "list", "transports"]:
            return self.transport
        if command == ["dumpsys", "backup"]:
            if "-after-" in name:
                return self.after.pop(0) if len(self.after) > 1 else self.after[0]
            return ABSENT
        if command[:2] == ["am", "instrument"]:
            phase = command[command.index("backupNotificationPhase") + 1]
            result = TRANSCRIPT
        elif command == ["bmgr", "backupnow", probe.PACKAGE]:
            phase = "backupnow"
            result = "Package org.adaway with result: Success\nBackup finished with result: Success\n"
        elif command == ["bmgr", "run"]:
            phase, result = "drain", ""
        else:
            self.fail(f"Unexpected command: {arguments}")
        self.mutations.append(phase)
        for key, value in self.change_after.get(phase, {}).items():
            setattr(self, key, value)
        return result

    def run_batch(self):
        def advance(seconds):
            self.now += seconds
        with patch.object(probe, "parse_arguments", return_value=self.args), \
                patch.object(probe.Controller, "run", side_effect=self.command), \
                patch.object(probe.time, "monotonic", side_effect=lambda: self.now), \
                patch.object(probe.time, "sleep", side_effect=advance), \
                redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            code = probe.main()
        with (self.args.evidence_dir / "queue-results.csv").open() as results:
            return code, list(csv.DictReader(results))

    def test_idle_absence_is_bounded(self):
        code, (row,) = self.run_batch()
        self.assertEqual((code, row["result"], row["actual_pending"]), (0, "PASS", "0"))
        self.assertIn("window", row["detail"])

    def test_observed_request(self):
        self.args.cases = ["create"]
        self.after.append(ABSENT.replace("pkg=com.google.android.inputmethod.latin", "pkg=org.adaway"))
        code, (row,) = self.run_batch()
        self.assertEqual((code, row["result"], row["actual_pending"]), (0, "PASS", "1"))

    def assert_indeterminate(self, marker):
        self.after += [ABSENT.replace("No backups running", marker), ABSENT]
        code, (row,) = self.run_batch()
        self.assertEqual((code, row["result"], row["actual_pending"]), (1, "ERROR", ""))

    def test_busy_intermediate_sample(self):
        self.assert_indeterminate("Backup currently running")

    def test_unknown_intermediate_sample(self):
        self.assert_indeterminate("")

    def test_conflicting_idle_markers(self):
        self.assert_indeterminate("Backup currently running\nNo backups running")

    def test_backup_in_progress_overrides_idle(self):
        self.assert_indeterminate("No backups running\nBackup in progress")

    def assert_aborted(self, mutations):
        self.args.cases = ["unchanged", "create"]
        code, rows = self.run_batch()
        self.assertEqual(self.mutations, mutations)
        self.assertEqual((code, [row["result"] for row in rows]), (1, ["ERROR"]))

    def test_disabled_before_seed(self):
        self.enabled = "Backup Manager currently disabled"
        self.assert_aborted([])

    def test_ambiguous_enabled_state(self):
        self.enabled += "Backup Manager currently disabled"
        self.assert_aborted([])

    def test_non_emulator_before_seed(self):
        self.qemu = "0"
        self.assert_aborted([])

    def test_ambiguous_transport_before_seed(self):
        self.transport = LOCAL + "  * remote/Transport\n"
        self.assert_aborted([])

    def test_preflight_command_fails_after_seed(self):
        self.change_after["seed"] = {"enabled": probe.ProbeError("command timed out")}
        self.assert_aborted(["seed"])

    def test_transport_changes_before_second_seed(self):
        self.change_after["act"] = {"transport": " * remote/BackupTransport"}
        self.args.cases = ["unchanged", "create"]
        code, rows = self.run_batch()
        self.assertEqual(self.mutations, ["seed", "backupnow", "drain", "act"])
        self.assertEqual((code, [row["result"] for row in rows]), (1, ["PASS", "ERROR"]))

    def test_prerequisite_loss_before_backupnow(self):
        self.change_after["seed"] = {"enabled": "unknown"}
        self.assert_aborted(["seed"])

    def test_prerequisite_loss_before_drain(self):
        self.change_after["backupnow"] = {"transport": ""}
        self.assert_aborted(["seed", "backupnow"])

    def test_prerequisite_loss_before_act(self):
        self.change_after["drain"] = {"enabled": ""}
        self.assert_aborted(["seed", "backupnow", "drain"])

    def test_disposable_acknowledgement_required(self):
        with patch.object(probe.sys, "argv", ["runner", "--serial", self.args.serial,
                                              "--evidence-dir", str(self.args.evidence_dir)]), \
                redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            probe.parse_arguments()


if __name__ == "__main__":
    unittest.main()
