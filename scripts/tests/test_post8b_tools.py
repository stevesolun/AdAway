"""Offline boundary tests. All input files are disposable; no adb, git or Gradle."""
import hashlib
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock

import check_schedule_backup_queue as probe


ROOT = Path(__file__).resolve().parents[2]


class InstrumentationSourceContractTest(unittest.TestCase):
    """Ordering/guard checks only. These do not execute an Android fixture."""

    def test_checkpoint_reset_follows_both_model_drains_and_checks_commit(self):
        path = ROOT / "app/src/androidTest/java/org/adaway/testing/InstrumentedTestState.java"
        source = path.read_text()
        reset = source.split("void resetForPassiveRootUi(", 1)[1].split(
            "public static void resetWorkManager", 1)[0]
        self.assertLess(reset.index("resetWorkManager(context"), reset.rindex("resetSourceModel("))
        end = reset[reset.rindex("resetSourceModel("):]
        self.assertRegex(end, r"if \(!context\.getSharedPreferences\(Constants\.PREFS_PENDING_SOURCE_APPLY,"
                         r"\s*Context\.MODE_PRIVATE\)\s*\.edit\(\)\.clear\(\)\.commit\(\)\)\s*\{"
                         r"\s*throw new AssertionError")
        self.assertNotIn("getUpdateInProgress(sourceModel).set(false)", source)

    def test_font_scale_guard_is_independent_of_locale(self):
        path = ROOT / ("app/src/androidTest/java/org/adaway/ui/discover/"
                       "DiscoverStateRegressionInstrumentedTest.java")
        guard = path.read_text().split("if (locale != null) {", 1)[1].split("assertRowTextClearsControls", 1)[0]
        locale_only, independent = guard.split("}", 1)
        self.assertNotIn("uxFontScale", locale_only)
        self.assertIn('getString("uxFontScale")', independent)
        self.assertRegex(independent, r"if \(scale != null\)\s*assertEquals\(Float\.parseFloat\(scale\),"
                         r" config\.fontScale,")


class QueueOutcomeTest(unittest.TestCase):
    def test_conflicting_or_duplicate_backup_receipts_stop_before_drain(self):
        package = "Package org.adaway with result: "
        overall = "Backup finished with result: "
        for extra in [package + "Failure", overall + "Failure", overall + "Success"]:
            with self.subTest(extra=extra):
                controller = Mock()
                controller.queue_sample.return_value = (True, True)
                controller.run_mutation.return_value = "\n".join(
                    [package + "Success", extra, overall + "Success"])
                with self.assertRaises(probe.ProbeError):
                    probe.Controller.exercise(controller, "unchanged")
                controller.drain_queue.assert_not_called()
                controller.instrument.assert_called_once_with("unchanged", "seed")


class ReleaseInputsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        classes = tempfile.TemporaryDirectory()
        cls.addClassCleanup(classes.cleanup)
        cls.classes = classes.name
        verifier = ROOT / "scripts/VerifyReleaseArtifacts.java"
        subprocess.run(["javac", "--release", "17", "-d", cls.classes, verifier], check=True)

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.args = ["java", "-cp", self.classes, "VerifyReleaseArtifacts",
                     "--public-key-base64", "unused"]
        self.inputs = []
        for label, content in [("apk", b"fixture"), ("manifest", b"{}"),
                               ("sbom", b'{"components":[],"bomFormat":"CycloneDX"}')]:
            artifact = self.root / label
            artifact.write_bytes(content)
            sidecar = self.write_checksum(artifact)
            self.inputs.extend((artifact, sidecar))
            self.args.extend(("--" + label, artifact, "--" + label + "-sha256", sidecar))

    @staticmethod
    def write_checksum(path):
        sidecar = path.with_suffix(".sha256")
        sidecar.write_text(hashlib.sha256(path.read_bytes()).hexdigest() + "  " + path.name + "\n")
        return sidecar

    def expect_failure(self, message, *args):
        run = subprocess.run(self.args + list(args), capture_output=True, text=True, timeout=15)
        self.assertNotEqual(run.returncode, 0)
        self.assertIn(message, run.stderr)

    def test_report_never_consumes_input_or_alias(self):
        alias = self.root / "report"
        alias.symlink_to(self.inputs[0])
        for path in [*self.inputs, alias]:
            with self.subTest(path=path):
                original = path.read_bytes()
                self.expect_failure("Report path", "--report", path)
                self.assertEqual(original, path.read_bytes())

    def test_checksum_sidecar_has_one_record(self):
        sidecar = self.inputs[1]
        sidecar.write_text(sidecar.read_text() * 2)
        self.expect_failure("one checksum")

    def test_sbom_requires_top_level_format_and_component_array(self):
        sbom = self.root / "sbom"
        for document in ['{"bomFormat":"CycloneDX","components":{}}',
                         '{"metadata":{"bomFormat":"CycloneDX","components":[]}}']:
            with self.subTest(document=document):
                sbom.write_text(document)
                self.write_checksum(sbom)
                self.expect_failure("SBOM")

    def test_valid_sbom_reaches_manifest_check(self):
        sbom = self.root / "sbom"
        sbom.write_text(r'{"components":[{"name":"A\"B","props":[null,true,false,-1.5e2]}],'
                        r'"bomForm\u0061t":"CycloneDX"}')
        self.write_checksum(sbom)
        self.expect_failure("payload")

    def test_json_must_be_one_unambiguous_document(self):
        manifest = self.root / "manifest"
        for document in ['{} {}', '{"payload":null,"payload":null}', '{"payload":}',
                         '{"other":[1,]}', r'{"other":"\u0zzz"}']:
            with self.subTest(document=document):
                manifest.write_text(document)
                self.write_checksum(manifest)
                self.expect_failure("JSON at offset")

    def test_unknown_option_does_not_silently_disable_check(self):
        self.expect_failure("Unknown argument", "--expected-versoin", "1.0")
