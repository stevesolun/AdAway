import contextlib
import configparser
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
SCANNER_PATH = ROOT / "docs/wa-tg-audit/scan_lists.py"


class MemoryOutput(io.StringIO):
    def close(self):
        pass


class ScannerTest(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location("scan_lists", SCANNER_PATH)
        self.scanner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.scanner)
        network = patch.object(self.scanner.urllib.request, "urlopen",
                               side_effect=AssertionError("Network forbidden in offline tests"))
        network.start()
        self.addCleanup(network.stop)

    def hits(self, content):
        return self.scanner.scan_list_for_critical("fixture", content)

    def report(self, content, known=None):
        outputs = {}

        def capture(path, mode="r", **kwargs):
            self.assertEqual("w", mode)
            outputs[path] = MemoryOutput()
            return outputs[path]

        with patch.object(self.scanner, "ALREADY_KNOWN", known or {}), \
                patch.object(self.scanner, "LISTS_TO_SCAN", [
                    {"name": "fixture", "url": "https://invalid.example/list"}]), \
                patch.object(self.scanner, "download_list", return_value=content), \
                patch.object(self.scanner, "open", side_effect=capture, create=True), \
                contextlib.redirect_stdout(io.StringIO()):
            result = self.scanner.main()
        self.assertEqual(3, len(outputs))
        report = next(stream.getvalue() for path, stream in outputs.items()
                      if path.endswith("domain-scan-report.json"))
        markdown = next(stream.getvalue() for path, stream in outputs.items()
                        if path.endswith("domain-scan-summary.md"))
        return json.loads(report), markdown, result

    def test_hosts_parent_is_exact(self):
        self.assertEqual([{"domain": "whatsapp.net", "via": "whatsapp.net"}],
                         self.hits("0.0.0.0 whatsapp.net\n"))

    def test_plain_parent_is_exact(self):
        self.assertEqual([{"domain": "whatsapp.net", "via": "whatsapp.net"}],
                         self.hits("whatsapp.net\n"))

    def test_suffix_rules_respect_label_boundaries(self):
        domains = {name: "HIGH - fixture" for name in
                   ("whatsapp.net", "mmg.whatsapp.net", "notwhatsapp.net")}
        with patch.object(self.scanner, "CRITICAL_DOMAINS", domains):
            for rule in ("||whatsapp.net^", "||whatsapp.net"):
                with self.subTest(rule=rule):
                    self.assertEqual([
                        {"domain": "whatsapp.net", "via": "whatsapp.net"},
                        {"domain": "mmg.whatsapp.net", "via": "whatsapp.net"},
                    ], self.hits(rule))

    def test_hosts_aliases_whitespace_ipv6_and_mixed_input(self):
        fixtures = [
            "0.0.0.0 unrelated.example mmg.whatsapp.net\n",
            "127.0.0.1 unrelated.example mmg.whatsapp.net\n",
            "::1 mmg.whatsapp.net\n",
            "  0.0.0.0 mmg.whatsapp.net\n",
            "0.0.0.0 unrelated.example\nmmg.whatsapp.net\n",
        ]
        for content in fixtures:
            with self.subTest(content=content):
                self.assertEqual([{"domain": "mmg.whatsapp.net", "via": "mmg.whatsapp.net"}],
                                 self.hits(content))

    def test_browser_only_and_nonblocking_inputs_are_ignored(self):
        for content in ("||mmg.whatsapp.net^$third-party", "@@||mmg.whatsapp.net^",
                        "||mmg.whatsapp.net/path", "mmg.whatsapp.net##selector",
                        "mmg.whatsapp.net#@#selector", "mmg.whatsapp.net#$#script",
                        "mmg.whatsapp.net#?#selector", "*.whatsapp.net",
                        "192.0.2.1 mmg.whatsapp.net", "! mmg.whatsapp.net"):
            with self.subTest(content=content):
                self.assertEqual([], self.hits(content))

    def test_comments_case_duplicates_and_invalid_aliases(self):
        content = ("# 0.0.0.0 static.whatsapp.net\n"
                   "0.0.0.0 MMG.WHATSAPP.NET bad/alias mmg.whatsapp.net # static.whatsapp.net\n"
                   "! ||whatsapp.net^\n")
        self.assertEqual([{"domain": "mmg.whatsapp.net", "via": "mmg.whatsapp.net"}],
                         self.hits(content))

    def test_report_does_not_expand_downloaded_or_historical_exact_hits(self):
        for content, known, name in (
                ("0.0.0.0 whatsapp.net\n", {}, "fixture"),
                ("", {"known": {"url": "https://invalid.example/hosts",
                                 "blocks": ["whatsapp.net"]}}, "known")):
            with self.subTest(source=name):
                report, _, _ = self.report(content, known)
                self.assertEqual(1, report["domain_risk_map"]["whatsapp.net"]["blocked_by_count"])
                self.assertEqual(0, report["domain_risk_map"]["mmg.whatsapp.net"]["blocked_by_count"])

    def test_report_preserves_suffix_hit_origin(self):
        report, _, _ = self.report("||whatsapp.net^\n")
        self.assertEqual([{"list": "fixture", "via": "whatsapp.net"}],
                         report["domain_risk_map"]["mmg.whatsapp.net"]["blocking_lists"])

    def test_empty_scan_has_no_allowlist_recommendations(self):
        report, markdown, _ = self.report("")
        self.assertEqual([], report["recommended_additions_to_allowlist"])
        self.assertNotIn("*.", markdown)

    def test_ads_and_telemetry_remain_blocked_without_advice_to_unblock(self):
        report, markdown, _ = self.report(
            "0.0.0.0 ads.telegram.org crashlogs.whatsapp.net\n")
        self.assertEqual([], report["high_risk_lists"])
        self.assertEqual([], report["recommended_additions_to_allowlist"])
        self.assertEqual(1, report["domain_risk_map"]["ads.telegram.org"]["blocked_by_count"])
        advice = markdown.split("## Recommended", 1)[1]
        self.assertNotIn("ads.telegram.org", advice)
        self.assertNotIn("crashlogs.whatsapp.net", advice)
        self.assertNotIn("*.", advice)

    def test_compatibility_advice_is_exact_without_parent_expansion(self):
        report, markdown, _ = self.report(
            "0.0.0.0 mmg.whatsapp.net crashlogs.whatsapp.net ads.telegram.org\n")
        self.assertEqual(["mmg.whatsapp.net"], report["recommended_additions_to_allowlist"])
        advice = markdown.split("## Recommended", 1)[1].split("## Current Allowlist", 1)[0]
        self.assertIn("\nmmg.whatsapp.net\n", advice)
        self.assertNotIn("\nwhatsapp.net\n", advice)
        self.assertNotIn("crashlogs.whatsapp.net", advice)
        self.assertNotIn("ads.telegram.org", advice)
        self.assertNotIn("*.", advice)

    def test_suffix_hit_does_not_recommend_telemetry_siblings(self):
        report, markdown, _ = self.report("||whatsapp.net^\n")
        self.assertEqual(["graph.whatsapp.net", "mmg.whatsapp.net", "static.whatsapp.net"],
                         report["recommended_additions_to_allowlist"])
        self.assertEqual(1, report["domain_risk_map"]["crashlogs.whatsapp.net"]["blocked_by_count"])
        self.assertNotIn("*.", markdown)

    def test_supported_subset_matches_current_app_constants_and_abp_pattern(self):
        source = (ROOT / "app/src/main/java/org/adaway/model/source/SourceLoader.java").read_text()
        constants = (ROOT / "app/src/main/java/org/adaway/util/Constants.java").read_text()
        for name, expected in (("BOGUS_IPV4", "0.0.0.0"),
                               ("LOCALHOST_IPV4", "127.0.0.1"),
                               ("LOCALHOST_IPV6", "::1")):
            literal = re.search(r"\b" + name + r'\s*=\s*("[^"\n]*")', constants)
            self.assertIsNotNone(literal)
            self.assertEqual(expected, json.loads(literal.group(1)))
        literal = re.search(r'ADBLOCK_DOUBLE_PIPE\s*=\s*Pattern\.compile\(("(?:\\.|[^"\\])*")\)',
                            source)
        self.assertIsNotNone(literal)
        app_pattern = re.compile(json.loads(literal.group(1)))
        for rule in ("||whatsapp.net^", "||whatsapp.net", "||whatsapp.net^$third-party",
                     "||whatsapp.net/path", "||whatsapp.net^extra"):
            with self.subTest(rule=rule):
                self.assertEqual(bool(app_pattern.fullmatch(rule)), bool(self.hits(rule)))

    def test_exact_hit_takes_precedence_over_a_suffix_hit(self):
        hits = self.hits("||whatsapp.net^\n0.0.0.0 mmg.whatsapp.net\n")
        self.assertIn({"domain": "mmg.whatsapp.net", "via": "mmg.whatsapp.net"}, hits)

    def test_already_allowed_compatibility_host_has_no_duplicate_advice(self):
        with patch.object(self.scanner, "CURRENT_ALLOWLIST", {"mmg.whatsapp.net"}):
            report, _, _ = self.report("0.0.0.0 mmg.whatsapp.net\n")
        self.assertEqual([], report["recommended_additions_to_allowlist"])
        self.assertTrue(report["domain_risk_map"]["mmg.whatsapp.net"]["is_protected"])


class CertificateHelperTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="tooling-cert-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.helper_dir = self.root / "helper with spaces"
        self.helper_dir.mkdir()
        self.foreign = self.root / "foreign cwd"
        self.foreign.mkdir()
        self.bin_dir = self.root / "bin"
        self.bin_dir.mkdir()
        self.helper = self.helper_dir / "generate.sh"
        shutil.copyfile(ROOT / "Resources/certificate/generate.sh", self.helper)
        shutil.copyfile(ROOT / "Resources/certificate/ssl.conf", self.helper_dir / "ssl.conf")
        self.log = self.root / "openssl.json"
        self.env = dict(os.environ, PATH=str(self.bin_dir) + os.pathsep + os.defpath,
                        MOCK_ROOT=str(self.root), MOCK_LOG=str(self.log),
                        PYTHONDONTWRITEBYTECODE="1")
        self.write_command("openssl", '''
import json, os, pathlib, sys
args = sys.argv[1:]
root = pathlib.Path(os.environ["MOCK_ROOT"])
def argument(flag):
    path = pathlib.Path(args[args.index(flag) + 1]).resolve()
    assert root in path.parents, "mock must never touch the repository"
    return path
cert, key, config = argument("-out"), argument("-keyout"), argument("-config")
pathlib.Path(os.environ["MOCK_LOG"]).write_text(json.dumps({
    "args": args, "cwd": os.getcwd(), "cert": str(cert), "key": str(key),
    "config": str(config)}))
if not config.is_file():
    sys.exit(19)
cert.write_text("" if os.environ.get("MOCK_MODE") == "empty" else "mock new certificate")
if os.environ.get("MOCK_MODE") == "fail":
    sys.exit(23)
if os.environ.get("MOCK_MODE") != "incomplete":
    key.write_text("mock new key")
''')
        self.originals = {"localhost.crt": b"old certificate", "localhost.key": b"old key",
                          "localhost.backup": b"backup", "localhost.notes": b"notes"}
        for name, content in self.originals.items():
            (self.helper_dir / name).write_bytes(content)
        (self.foreign / "localhost.notes").write_text("foreign notes")

    def write_command(self, name, body):
        path = self.bin_dir / name
        path.write_text("#!" + sys.executable + "\n" + body, encoding="utf-8")
        path.chmod(0o755)

    def run_helper(self, cwd=None, mode="success"):
        return subprocess.run(["/bin/bash", str(self.helper)], cwd=cwd or self.foreign,
                              env=dict(self.env, MOCK_MODE=mode), capture_output=True,
                              text=True, timeout=10)

    def assert_originals(self):
        for name, content in self.originals.items():
            with self.subTest(path=name):
                self.assertTrue((self.helper_dir / name).is_file())
                self.assertEqual(content, (self.helper_dir / name).read_bytes())
        self.assertEqual("foreign notes", (self.foreign / "localhost.notes").read_text())

    def assert_no_staging_left(self):
        self.assertEqual(set(self.originals) | {"generate.sh", "ssl.conf"},
                         {p.name for p in self.helper_dir.iterdir()})

    def test_success_from_foreign_cwd_is_confined(self):
        result = self.run_helper()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("mock new certificate", (self.helper_dir / "localhost.crt").read_text())
        self.assertEqual("mock new key", (self.helper_dir / "localhost.key").read_text())
        self.assertEqual(b"backup", (self.helper_dir / "localhost.backup").read_bytes())
        self.assertEqual(b"notes", (self.helper_dir / "localhost.notes").read_bytes())
        self.assertEqual("foreign notes", (self.foreign / "localhost.notes").read_text())
        self.assertEqual({"localhost.notes"}, {p.name for p in self.foreign.iterdir()})
        invocation = json.loads(self.log.read_text())
        self.assertEqual(str(self.helper_dir / "ssl.conf"), invocation["config"])
        self.assertNotEqual(str(self.helper_dir / "localhost.key"), invocation["key"])
        self.assert_no_staging_left()

    def test_success_from_helper_directory_preserves_unrelated_files(self):
        result = self.run_helper(cwd=self.helper_dir)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b"backup", (self.helper_dir / "localhost.backup").read_bytes())
        self.assertEqual(b"notes", (self.helper_dir / "localhost.notes").read_bytes())
        self.assert_no_staging_left()

    def test_failed_generation_preserves_existing_pair_and_unrelated_files(self):
        result = self.run_helper(cwd=self.helper_dir, mode="fail")
        self.assertEqual(23, result.returncode, result.stderr)
        self.assert_originals()
        self.assert_no_staging_left()

    def test_incomplete_success_is_rejected_without_replacing_pair(self):
        result = self.run_helper(cwd=self.helper_dir, mode="incomplete")
        self.assertNotEqual(0, result.returncode)
        self.assert_originals()
        self.assert_no_staging_left()

    def test_new_key_is_private(self):
        result = self.run_helper(cwd=self.helper_dir)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(0o600, stat.S_IMODE((self.helper_dir / "localhost.key").stat().st_mode))

    def test_missing_configuration_does_not_touch_outputs(self):
        (self.helper_dir / "ssl.conf").unlink()
        result = self.run_helper(cwd=self.helper_dir)
        self.assertNotEqual(0, result.returncode)
        self.assert_originals()
        self.assertFalse(self.log.exists())

    def test_output_symlink_is_rejected_without_following_or_replacing_it(self):
        external = self.root / "external-key"
        external.write_text("unrelated external key")
        target = self.helper_dir / "localhost.key"
        target.unlink()
        target.symlink_to(external)
        result = self.run_helper(cwd=self.helper_dir)
        self.assertNotEqual(0, result.returncode)
        self.assertTrue(target.is_symlink())
        self.assertEqual("unrelated external key", external.read_text())
        self.assertEqual(b"old certificate", (self.helper_dir / "localhost.crt").read_bytes())
        self.assertFalse(self.log.exists())

    def fail_second_publish(self):
        self.write_command("mv", '''
import os, pathlib, sys
src, dst = map(pathlib.Path, [a for a in sys.argv[1:] if a != "--" and a != "-f"])
root = pathlib.Path(os.environ["MOCK_ROOT"])
assert root in src.resolve().parents and root in dst.resolve().parents
if src.name == "new.key":
    sys.exit(29)
os.replace(src, dst)
''')

    def test_second_publish_failure_restores_original_pair(self):
        self.fail_second_publish()
        result = self.run_helper(cwd=self.helper_dir)
        self.assertNotEqual(0, result.returncode)
        self.assert_originals()
        self.assert_no_staging_left()

    def test_empty_output_is_rejected_without_replacing_pair(self):
        result = self.run_helper(mode="empty")
        self.assertNotEqual(0, result.returncode)
        self.assert_originals()
        self.assert_no_staging_left()

    def remove_old_pair(self):
        (self.helper_dir / "localhost.crt").unlink()
        (self.helper_dir / "localhost.key").unlink()

    def test_first_time_generation_succeeds_without_old_pair(self):
        self.remove_old_pair()
        result = self.run_helper()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("mock new certificate", (self.helper_dir / "localhost.crt").read_text())
        self.assertEqual("mock new key", (self.helper_dir / "localhost.key").read_text())
        self.assert_no_staging_left()

    def test_first_time_failed_generation_leaves_no_outputs(self):
        self.remove_old_pair()
        result = self.run_helper(mode="fail")
        self.assertEqual(23, result.returncode, result.stderr)
        self.assertEqual({"generate.sh", "ssl.conf", "localhost.backup", "localhost.notes"},
                         {p.name for p in self.helper_dir.iterdir()})
        self.assertEqual("foreign notes", (self.foreign / "localhost.notes").read_text())

    def test_first_time_failed_publish_removes_only_its_partial_output(self):
        self.remove_old_pair()
        self.fail_second_publish()
        result = self.run_helper()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual({"generate.sh", "ssl.conf", "localhost.backup", "localhost.notes"},
                         {p.name for p in self.helper_dir.iterdir()})
        self.assertEqual(b"backup", (self.helper_dir / "localhost.backup").read_bytes())
        self.assertEqual(b"notes", (self.helper_dir / "localhost.notes").read_bytes())
        self.assertEqual("foreign notes", (self.foreign / "localhost.notes").read_text())

    def test_preexisting_lock_is_preserved_and_blocks_generation(self):
        lock = self.helper_dir / ".localhost.lock"
        lock.mkdir()
        (lock / "owner").write_text("another invocation")
        result = self.run_helper()
        self.assertNotEqual(0, result.returncode)
        self.assert_originals()
        self.assertFalse(self.log.exists())
        self.assertEqual("another invocation", (lock / "owner").read_text())

    def test_directory_output_is_rejected(self):
        target = self.helper_dir / "localhost.key"
        target.unlink()
        target.mkdir()
        (target / "keep").write_text("directory fixture")
        result = self.run_helper()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("directory fixture", (target / "keep").read_text())
        self.assertEqual(b"old certificate", (self.helper_dir / "localhost.crt").read_bytes())
        self.assertFalse(self.log.exists())


class DocumentationAndTranslationTest(unittest.TestCase):
    def translation_resources(self):
        config = configparser.ConfigParser(interpolation=None)
        config.read(ROOT / ".tx/config", encoding="utf-8")
        cli = {config[section]["source_file"]: config[section]
               for section in config.sections() if "source_file" in config[section]}
        result = subprocess.run([
            "ruby", "-ryaml", "-rjson", "-e",
            "puts JSON.generate(YAML.load_file(ARGV.fetch(0)))",
            str(ROOT / ".tx/github-integration.yaml")
        ], capture_output=True, text=True, check=True, timeout=10)
        filters = json.loads(result.stdout)["filters"]
        github = {item["source_file"]: item for item in filters}
        self.assertEqual(len(filters), len(github), "Duplicate translation source")
        return cli, github

    def test_translation_sources_exist_in_both_configs(self):
        cli, github = self.translation_resources()
        for label, resources in (("CLI", cli), ("GitHub", github)):
            with self.subTest(config=label):
                self.assertEqual([], [path for path in resources if not (ROOT / path).is_file()])

    def test_translation_mappings_agree_after_retiring_four_resources(self):
        cli, github = self.translation_resources()
        self.assertEqual(set(cli), set(github))
        self.assertEqual(19, len(cli))
        for source, entry in cli.items():
            with self.subTest(source=source):
                self.assertEqual(entry["file_filter"], github[source]["translation_files_expression"])
                self.assertEqual(entry["source_lang"], github[source]["source_language"])
                self.assertEqual(entry["type"], github[source]["file_format"])

    def test_contributor_first_build_uses_debug(self):
        text = (ROOT / "CONTRIBUTING.md").read_text(encoding="utf-8")
        commands = re.findall(r"`(\./gradlew [^`]+)`", text)
        self.assertTrue(commands)
        self.assertEqual("./gradlew assembleDebug", commands[0])

    def test_contributor_release_guidance_links_to_maintained_guide(self):
        text = (ROOT / "CONTRIBUTING.md").read_text(encoding="utf-8")
        self.assertRegex(text, r"\[[^\]]+\]\(RELEASING\.md\)")
        self.assertTrue((ROOT / "RELEASING.md").is_file())


if __name__ == "__main__":
    unittest.main()
