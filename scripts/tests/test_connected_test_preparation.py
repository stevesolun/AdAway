import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/android-ci.yml"
PREPARE_ARGUMENTS = [
    ":app:assembleDebug", ":app:assembleDebugAndroidTest",
    "--dependency-verification=strict", "--stacktrace", "--console=plain",
]
RATE_LIMIT = (
    "Could not GET 'https://repo.maven.apache.org/maven2/com/squareup/okhttp3/"
    "mockwebserver/5.4.0/mockwebserver-5.4.0.pom'. "
    "Received status code 429 from server: Too Many Requests"
)


def step_script(name):
    """Extract the actual literal run block without a YAML dependency."""
    lines = WORKFLOW.read_text(encoding="utf-8").splitlines()
    marker = re.compile(r"^(\s*)- name: " + re.escape(name) + r"\s*$")
    for index, line in enumerate(lines):
        match = marker.fullmatch(line)
        if not match:
            continue
        step_indent = len(match.group(1))
        for run_index in range(index + 1, len(lines)):
            candidate = lines[run_index]
            if candidate.strip() and len(candidate) - len(candidate.lstrip()) <= step_indent:
                break
            if candidate.strip() != "run: |":
                continue
            body_indent = len(candidate) - len(candidate.lstrip()) + 2
            body = []
            for body_line in lines[run_index + 1:]:
                if body_line.strip() and len(body_line) - len(body_line.lstrip()) < body_indent:
                    break
                body.append(body_line[body_indent:] if body_line.strip() else "")
            return "\n".join(body) + "\n"
        raise AssertionError(f"Workflow step {name!r} has no literal run block")
    raise AssertionError(f"Workflow step {name!r} is missing")


class ConnectedTestPreparationTests(unittest.TestCase):
    def setUp(self):
        temporary_root = ROOT / ".gradle" / "connected-preparation-tests"
        temporary_root.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(dir=temporary_root)
        self.addCleanup(self.temporary.cleanup)
        self.checkout = Path(self.temporary.name)
        self.bin = self.checkout / "bin"
        self.bin.mkdir()
        self.calls = self.checkout / "gradle-calls.jsonl"
        self.sleeps = self.checkout / "sleep-calls.jsonl"
        self.outcomes = self.checkout / "outcomes.json"
        self.write_executable(self.checkout / "gradlew", '''
import json, os, pathlib, sys
calls = pathlib.Path(os.environ["TEST_GRADLE_CALLS"])
previous = calls.read_text().splitlines() if calls.exists() else []
with calls.open("a", encoding="utf-8") as output:
    output.write(json.dumps(sys.argv[1:]) + "\\n")
outcomes = json.loads(pathlib.Path(os.environ["TEST_GRADLE_OUTCOMES"]).read_text())
if len(previous) >= len(outcomes):
    print("Unexpected extra Gradle invocation", file=sys.stderr)
    sys.exit(98)
outcome = outcomes[len(previous)]
print(outcome["output"], file=sys.stderr)
sys.exit(outcome["status"])
''')
        self.write_executable(self.bin / "sleep", '''
import json, os, pathlib, sys
with pathlib.Path(os.environ["TEST_SLEEP_CALLS"]).open("a", encoding="utf-8") as output:
    output.write(json.dumps(sys.argv[1:]) + "\\n")
''')

    @staticmethod
    def write_executable(path, code):
        path.write_text(f"#!{sys.executable}\n" + code, encoding="utf-8")
        path.chmod(0o755)

    @staticmethod
    def read_calls(path):
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()] \
            if path.exists() else []

    def run_step(self, outcomes, name="Prepare connected test APKs"):
        self.outcomes.write_text(json.dumps([
            {"status": status, "output": output} for status, output in outcomes
        ]), encoding="utf-8")
        environment = os.environ.copy()
        environment.update({
            "PATH": str(self.bin) + os.pathsep + environment.get("PATH", ""),
            "TEST_GRADLE_CALLS": str(self.calls),
            "TEST_GRADLE_OUTCOMES": str(self.outcomes),
            "TEST_SLEEP_CALLS": str(self.sleeps),
            "PYTHONDONTWRITEBYTECODE": "1",
        })
        result = subprocess.run(
            ["bash", "--noprofile", "--norc", "-euo", "pipefail", "-c", step_script(name)],
            cwd=self.checkout, env=environment, capture_output=True, text=True, timeout=10,
        )
        self.assertTrue(self.calls.exists(), result.stdout + result.stderr)
        if name == "Prepare connected test APKs":
            for arguments in self.read_calls(self.calls):
                self.assertEqual(PREPARE_ARGUMENTS, arguments)
        return result

    def assert_attempts(self, count, sleeps):
        self.assertEqual(count, len(self.read_calls(self.calls)))
        self.assertEqual([[str(delay)] for delay in sleeps], self.read_calls(self.sleeps))

    def assert_logs(self, outputs):
        log_directory = self.checkout / "app/build/ci-artifacts"
        self.assertEqual(
            [f"connected-apk-build-{attempt}.log" for attempt in range(1, len(outputs) + 1)],
            sorted(path.name for path in log_directory.glob("connected-apk-build-*.log")),
        )
        for attempt, output in enumerate(outputs, start=1):
            self.assertIn(output, (log_directory / f"connected-apk-build-{attempt}.log")
                          .read_text(encoding="utf-8"))

    def test_success_runs_once(self):
        result = self.run_step([(0, "BUILD SUCCESSFUL")])
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_attempts(1, [])
        self.assert_logs(["BUILD SUCCESSFUL"])

    def test_maven_rate_limit_retries_then_succeeds(self):
        result = self.run_step([(7, RATE_LIMIT), (0, "BUILD SUCCESSFUL")])
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_attempts(2, [30])
        self.assert_logs([RATE_LIMIT, "BUILD SUCCESSFUL"])

    def test_head_rate_limit_is_retried(self):
        result = self.run_step([(7, RATE_LIMIT.replace("GET", "HEAD")), (0, "BUILD SUCCESSFUL")])
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_attempts(2, [30])

    def test_exhaustion_returns_last_failure(self):
        result = self.run_step([(7, RATE_LIMIT), (8, RATE_LIMIT), (9, RATE_LIMIT)])
        self.assertEqual(9, result.returncode, result.stdout + result.stderr)
        self.assert_attempts(3, [30, 60])
        self.assert_logs([RATE_LIMIT] * 3)

    def test_other_failures_are_not_retried(self):
        failures = [
            "Compilation failed: cannot find symbol",
            "Dependency verification failed: checksum mismatch",
            "There were failing tests: java.lang.AssertionError",
            RATE_LIMIT.replace("repo.maven.apache.org", "other.example"),
            RATE_LIMIT.replace("repo.maven.apache.org", "repo.maven.apache.org.example"),
            RATE_LIMIT.replace("status code 429", "status code 503"),
            "Received status code 429 from server: Too Many Requests",
        ]
        for output in failures:
            with self.subTest(output=output):
                if self.calls.exists():
                    self.calls.unlink()
                result = self.run_step([(19, output)])
                self.assertEqual(19, result.returncode, result.stdout + result.stderr)
                self.assert_attempts(1, [])

    def test_later_non_rate_limit_failure_is_not_retried(self):
        compile_failure = "Compilation failed: cannot find symbol"
        result = self.run_step([(7, RATE_LIMIT), (23, compile_failure)])
        self.assertEqual(23, result.returncode, result.stdout + result.stderr)
        self.assert_attempts(2, [30])
        self.assert_logs([RATE_LIMIT, compile_failure])

    def test_log_write_failure_fails_the_step(self):
        self.write_executable(self.bin / "tee", '''
import sys
sys.stdout.write(sys.stdin.read())
print("tee: No space left on device", file=sys.stderr)
sys.exit(74)
''')
        result = self.run_step([(0, "BUILD SUCCESSFUL")])
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_attempts(1, [])

    def test_connected_assertion_failure_is_single_shot(self):
        result = self.run_step([(31, "There were failing tests: java.lang.AssertionError")],
                               name="Run connected Android tests")
        self.assertEqual(31, result.returncode, result.stdout + result.stderr)
        self.assert_attempts(1, [])
        self.assertEqual([[
            ":app:connectedDebugAndroidTest", "--dependency-verification=strict", "--stacktrace",
        ]], self.read_calls(self.calls))


if __name__ == "__main__":
    unittest.main()
