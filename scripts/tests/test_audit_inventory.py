import csv
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "audit_inventory", Path(__file__).resolve().parents[1] / "audit_inventory.py")
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


class AuditInventoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        self.patch = patch.object(AUDIT, "ROOT", self.root)
        self.patch.start()
        self.addCleanup(self.patch.stop)
        (self.root / "example.java").write_text("class Example {}\n")

    def run_inventory(self, *args):
        with patch("sys.argv", ["audit_inventory.py", *args]):
            AUDIT.main()

    def rows(self):
        return AUDIT.read_rows(self.root / AUDIT.LEDGER, AUDIT.FIELDS)

    def report(self, digest=None):
        path = self.root / "review.csv"
        with path.open("w", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["path", "sha256", "assessment", "bug_ids"])
            writer.writerow(["example.java", digest or AUDIT.fingerprint(Path("example.java")),
                             "Reviewed the entire example", ""])
        return str(path)

    def test_inventory_does_not_claim_review(self):
        self.run_inventory()
        self.assertEqual("pending", self.rows()["example.java"]["review_status"])
        self.run_inventory("--check")

    def test_changed_reviewed_file_becomes_stale(self):
        self.run_inventory("--record", self.report())
        self.assertEqual("reviewed", self.rows()["example.java"]["review_status"])
        (self.root / "example.java").write_text("class Example { int changed; }\n")
        with self.assertRaises(SystemExit):
            self.run_inventory("--check")
        self.run_inventory()
        self.assertEqual("stale", self.rows()["example.java"]["review_status"])

    def test_review_hash_must_match_current_file(self):
        with self.assertRaises(ValueError):
            self.run_inventory("--record", self.report("wrong-hash"))

    def test_dangling_symlink_remains_in_inventory(self):
        link = self.root / "broken-link"
        link.symlink_to("missing-target")
        self.run_inventory()
        self.assertEqual(AUDIT.fingerprint(Path("broken-link")),
                         self.rows()["broken-link"]["sha256"])
        self.run_inventory("--check")

    def test_ignored_files_are_not_inventory(self):
        (self.root / ".gitignore").write_text("private.txt\n")
        (self.root / "private.txt").write_text("excluded\n")
        self.run_inventory()
        self.assertNotIn("private.txt", self.rows())

    def test_deleted_files_are_reported_as_inventory_drift(self):
        self.run_inventory()
        (self.root / "example.java").unlink()
        with self.assertRaises(SystemExit):
            self.run_inventory("--check")
        self.run_inventory()
        self.assertNotIn("example.java", self.rows())


if __name__ == "__main__":
    unittest.main()
