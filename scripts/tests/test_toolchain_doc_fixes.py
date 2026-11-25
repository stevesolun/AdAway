import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
RELEASE_GUIDANCE = (
    "Without release signing properties, this uses the local debug key.\n"
    "For signed production builds and their required signing and update-trust inputs,\n"
    "follow the [release guide](RELEASING.md). Release trust checks remain required."
)


class ToolchainDocumentationTest(unittest.TestCase):
    def setUp(self):
        self.document = (ROOT / "CONTRIBUTING.md").read_text(encoding="utf-8")
        self.section = self.document.split("#### Building with Gradle\n", 1)[1].split(
            "#### Running on an emulator", 1)[0]

    def commands(self):
        # Accept the old inline commands as well as the corrected fenced setup.
        blocks = re.findall(r"^```(?:sh|bash)\n(.*?)^```|^[ \t]*`([^`\n]+)`",
                            self.section, re.MULTILINE | re.DOTALL)
        commands = "\n".join(fenced or inline for fenced, inline in blocks)
        self.assertTrue(commands, "No contributor shell commands found")
        return commands

    def run_bootstrap(self, directory):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / directory
            sdk = root / "Android SDK"
            jdk = root / "JDK 21"
            checkout = root / "checkout"
            empty_path = root / "empty-path"
            log = root / "calls.jsonl"
            sdkmanager = sdk / "cmdline-tools/latest/bin/sdkmanager"
            java = jdk / "bin/java"
            gradlew = checkout / "gradlew"
            for path in (sdkmanager, java, gradlew):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(f"#!{sys.executable}\n" + '''
import json, os, pathlib, shutil, sys
record = {
    "executable": str(pathlib.Path(sys.argv[0]).resolve()),
    "args": sys.argv[1:],
    "java_home": os.environ.get("JAVA_HOME"),
    "android_home": os.environ.get("ANDROID_HOME"),
    "path": os.environ.get("PATH"),
    "java": shutil.which("java"),
    "cwd": os.getcwd(),
}
with open(os.environ["CALL_LOG"], "a", encoding="utf-8") as output:
    output.write(json.dumps(record) + "\\n")
''', encoding="utf-8")
                path.chmod(0o755)
            empty_path.mkdir()
            commands = self.commands()
            # Only substitute user-selected paths; keep quoting and command syntax intact.
            commands = commands.replace("/path/to/jdk-21", str(jdk))
            commands = commands.replace("/path/to/android-sdk", str(sdk))
            commands = commands.replace("/path/to/your/sdk", str(sdk))
            # Fail closed before execution if the documentation adds another command.
            for line in commands.replace("\\\n", " ").splitlines():
                if not line.strip():
                    continue
                self.assertNotRegex(line, r"`|\$\(")
                lexer = shlex.shlex(line, posix=True, punctuation_chars="|;&<>")
                lexer.whitespace_split = True
                tokens = list(lexer)
                self.assertFalse(any(token and set(token) <= set("|;&<>")
                                     for token in tokens), "Unexpected shell operator")
                self.assertIn(tokens[0], (
                    "export", "tools/bin/sdkmanager",
                    "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager", "./gradlew"))
                if tokens[0] == "export":
                    self.assertIn(tokens[1].split("=", 1)[0], ("JAVA_HOME", "ANDROID_HOME", "PATH"))
            result = subprocess.run(
                ["/bin/bash", "--noprofile", "--norc", "-euc", commands],
                cwd=checkout, env={
                    "HOME": str(root), "PATH": str(empty_path), "LC_ALL": "C",
                    "CALL_LOG": str(log), "PYTHONDONTWRITEBYTECODE": "1",
                }, capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr)
            records = [json.loads(line) for line in log.read_text(encoding="utf-8").splitlines()]
            self.assertEqual([
                (str(sdkmanager.resolve()), [f"--sdk_root={sdk}", "--install",
                                            "platform-tools", "platforms;android-36",
                                            "build-tools;36.0.0", "ndk;27.2.12479018"]),
                (str(sdkmanager.resolve()), [f"--sdk_root={sdk}", "--licenses"]),
                (str(gradlew.resolve()), ["assembleDebug"]),
            ], [(record["executable"], record["args"]) for record in records])
            for record in records:
                self.assertEqual(str(sdk), record["android_home"])
                self.assertEqual(str(jdk), record["java_home"])
                self.assertEqual(f"{jdk / 'bin'}{os.pathsep}{empty_path}", record["path"])
                self.assertEqual(str(java), record["java"])
                self.assertEqual(str(checkout.resolve()), record["cwd"])

    def test_extracted_bootstrap_uses_pinned_packages_and_jdk(self):
        self.run_bootstrap("toolchain")

    def test_extracted_bootstrap_handles_checkout_paths_with_spaces(self):
        self.run_bootstrap("toolchain with spaces")

    def test_pins_match_active_project_configuration(self):
        app = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
        self.assertRegex(app, r"compileSdk\s*=\s*36\b")
        self.assertRegex(app, r"ndkVersion\s*=\s*'27\.2\.12479018'")
        self.assertRegex(app, r"sourceCompatibility\s*=\s*JavaVersion\.VERSION_21\b")
        self.assertRegex(app, r"targetCompatibility\s*=\s*JavaVersion\.VERSION_21\b")
        self.assertIn('"build-tools;36.0.0"',
                      (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8"))

    def test_debug_first_and_release_trust_guidance_remain_intact(self):
        self.assertIn(RELEASE_GUIDANCE, self.section)
        self.assertEqual(["assembleDebug"], re.findall(r"\./gradlew\s+(\w+)", self.commands()))
        self.assertNotRegex(self.commands(), r"\byes\s*\|")
        self.assertTrue((ROOT / "RELEASING.md").is_file())


if __name__ == "__main__":
    unittest.main()
