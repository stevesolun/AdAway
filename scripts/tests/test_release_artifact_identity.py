#!/usr/bin/env python3
"""Host regression gate: real APK tools, ephemeral keys, mocked GitHub/device operations.

Requires JDK 21, Android SDK build-tools 36.0.0/platform android-36, openssl, pwsh, gh.
No Gradle, Android runtime, release download, or network calls are performed.
"""
import base64
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[2]
COMMIT = '1' * 40
OTHER = '2' * 40


class ReleaseArtifactIdentityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix='adaway-release-tests-')
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.work = Path(cls.temporary.name)
        sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '')))
        cls.tools = sdk / 'build-tools/36.0.0'
        cls.platform = sdk / 'platforms/android-36/android.jar'
        for path in (cls.tools / 'aapt2', cls.tools / 'lib/apksigner.jar', cls.platform):
            if not path.is_file():
                raise RuntimeError('Required local SDK file missing: ' + str(path))
        cls.java = shutil.which('java')
        cls.pwsh = shutil.which('pwsh')
        if not cls.java or not cls.pwsh:
            raise RuntimeError('java and pwsh are required')
        cls.classes = cls.work / 'classes'
        cls.classes.mkdir()
        cls.env = dict(os.environ)
        cls.env['GITHUB_SHA'] = 'f' * 40  # Must never become artifact or tooling identity.
        cls.env['GH_TOKEN'] = 'local-mock-only'
        cls.env['GITHUB_TOKEN'] = 'local-mock-only'
        fixture_sdk = cls.work / 'sdk'
        (fixture_sdk / 'build-tools').mkdir(parents=True)
        (fixture_sdk / 'build-tools/36.0.0').symlink_to(cls.tools.resolve(), target_is_directory=True)
        cls.env['ANDROID_HOME'] = str(fixture_sdk)
        cls.env['ANDROID_SDK_ROOT'] = str(fixture_sdk)
        mock_bin = cls.work / 'bin'
        mock_bin.mkdir()
        for name in ('gh', 'adb'):
            target = mock_bin / name
            shutil.copyfile(ROOT / 'scripts/tests/fixtures/release-tool-mock.py', target)
            target.chmod(0o755)
        cls.env['PATH'] = str(mock_bin) + os.pathsep + cls.env['PATH']
        cls.env['GH_CLI_PATH'] = str(mock_bin / 'gh')
        cls.exec(['javac', '--release', '17', '-d', cls.classes,
                  ROOT / 'scripts/VerifyReleaseArtifacts.java'])
        cls.key = cls.work / 'metadata.pem'
        cls.exec(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048',
                  '-out', cls.key])
        public = cls.exec(['openssl', 'pkey', '-in', cls.key, '-pubout', '-outform', 'DER'], binary=True)
        cls.public = base64.b64encode(public.stdout).decode()
        cls.keys = []
        for index in range(2):
            key = cls.work / ('apk-key-' + str(index) + '.p12')
            cls.exec(['keytool', '-genkeypair', '-keystore', key, '-storetype', 'PKCS12',
                      '-storepass', 'fixture-only', '-keypass', 'fixture-only',
                      '-alias', 'test-' + str(index),
                      '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2',
                      '-dname', 'CN=Local Release Test'])
            cls.keys.append(key)
        cls.cache = {}
        cls.good = cls.make_apk()
        certs = cls.exec([cls.java, '-jar', cls.tools / 'lib/apksigner.jar',
                         'verify', '--print-certs', cls.good]).stdout
        cls.cert = re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)', certs)[1]
        cls.checkout = cls.work / 'tool checkout'
        cls.exec(['git', 'init', '-q', cls.checkout])
        cls.verifier = cls.checkout / 'VerifyReleaseArtifacts.java'
        cls.smoke_script = cls.checkout / 'run-release-smoke.ps1'
        for target in (cls.verifier, cls.smoke_script):
            shutil.copyfile(ROOT / 'scripts' / target.name, target)
        cls.exec(['git', 'add', '--', cls.verifier.name, cls.smoke_script.name], cwd=cls.checkout)
        cls.exec(['git', '-c', 'user.name=QA', '-c', 'user.email=qa@example.invalid',
                  '-c', 'commit.gpgSign=false', 'commit', '-q', '-m', 'verifier fixture'],
                 cwd=cls.checkout)
        cls.head = cls.exec(['git', 'rev-parse', 'HEAD'], cwd=cls.checkout).stdout.strip()

    @classmethod
    def exec(cls, args, *, env=None, binary=False, check=True, cwd=ROOT):
        result = subprocess.run([str(arg) for arg in args], cwd=cwd, env=env or cls.env,
                                capture_output=True, text=not binary, timeout=180)
        if check and result.returncode:
            raise AssertionError(f'Command failed ({result.returncode}): {args}\n'
                                 f'{result.stdout}\n{result.stderr}')
        return result

    @classmethod
    def make_apk(cls, package='org.adaway', version='13.5.0', code='130500',
                 debug=False, signer=0, unsigned=False, multiple=False):
        identity = (package, version, code, debug, signer, unsigned, multiple)
        if identity in cls.cache:
            return cls.cache[identity]
        directory = cls.work / ('apk-' + str(len(cls.cache)))
        directory.mkdir()
        manifest = directory / 'AndroidManifest.xml'
        manifest.write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
            f'package="{package}" android:versionCode="{code}" android:versionName="{version}">'
            '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>'
            f'<application android:hasCode="false" android:debuggable="{str(debug).lower()}"/>'
            '</manifest>')
        apk = directory / 'fixture.apk'
        cls.exec([cls.tools / 'aapt2', 'link', '-I', cls.platform, '--manifest', manifest, '-o', apk])
        if not unsigned:
            args = [cls.java, '-jar', cls.tools / 'lib/apksigner.jar', 'sign',
                    '--ks', cls.keys[signer], '--ks-pass', 'pass:fixture-only',
                    '--key-pass', 'pass:fixture-only', '--v1-signer-name', 'SIGNER0',
                    '--v3-signing-enabled', 'false']
            if multiple:
                args += ['--next-signer', '--ks', cls.keys[1], '--ks-pass', 'pass:fixture-only',
                         '--key-pass', 'pass:fixture-only', '--v1-signer-name', 'SIGNER1']
            cls.exec(args + [apk])
        cls.cache[identity] = apk
        return apk

    def setUp(self):
        self.case = Path(tempfile.mkdtemp(prefix='case-', dir=self.work))
        self.config = self.case / 'mock.json'
        self.config.write_text(json.dumps({'commit': COMMIT}))
        self.log = self.case / 'mock.log'
        self.case_env = dict(self.env, RELEASE_MOCK_CONFIG=str(self.config),
                             RELEASE_MOCK_LOG=str(self.log))
        self.report = self.case / 'verification.md'
        self.bundle()

    def configure(self, **values):
        self.log.unlink(missing_ok=True)
        self.config.write_text(json.dumps(dict(commit=COMMIT, **values)))

    @staticmethod
    def checksum(path):
        Path(str(path) + '.sha256').write_text(hashlib.sha256(path.read_bytes()).hexdigest()
                                             + '  ' + path.name + '\n')

    def bundle(self, apk=None, **payload_changes):
        self.apk = self.case / 'AdAway_13.5.0.apk'
        shutil.copyfile(apk or self.good, self.apk)
        self.checksum(self.apk)
        self.sbom = self.case / 'adaway.cdx.json'
        self.sbom.write_text(json.dumps({'bomFormat': 'CycloneDX', 'components': []}))
        self.checksum(self.sbom)
        self.payload = dict(version='13.5.0', versionCode=130500,
                            apkSha256=hashlib.sha256(self.apk.read_bytes()).hexdigest(),
                            signingCertificateSha256=self.cert,
                            apkUrl='https://github.com/stevesolun/AdAway/releases/download/'
                                   'v13.5.0/AdAway_13.5.0.apk', channel='stable', store='adaway',
                            expiresAt=(datetime.now(timezone.utc) + timedelta(days=1))
                            .isoformat().replace('+00:00', 'Z'))
        self.payload.update(payload_changes)
        self.sign_metadata()

    def sign_metadata(self):
        payload = json.dumps(self.payload, separators=(',', ':'))
        data = self.case / 'payload.json'
        data.write_text(payload)
        signed = self.exec(['openssl', 'dgst', '-sha256', '-sign', self.key, data], binary=True)
        self.manifest = self.case / 'manifest.json'
        self.manifest.write_text(json.dumps(dict(payload=payload,
            signature=base64.b64encode(signed.stdout).decode())))
        self.checksum(self.manifest)

    def verify(self, *, attested=False, extra=(), passed=True, cwd=ROOT, source=None):
        launcher = [source] if source else ['-cp', self.classes, 'VerifyReleaseArtifacts']
        args = [self.java, *launcher,
                '--apk', self.apk, '--apk-sha256', str(self.apk) + '.sha256',
                '--manifest', self.manifest, '--manifest-sha256', str(self.manifest) + '.sha256',
                '--sbom', self.sbom, '--sbom-sha256', str(self.sbom) + '.sha256',
                '--public-key-base64', self.public, '--expected-cert-sha256', self.cert,
                '--expected-version', '13.5.0', '--expected-channel', 'stable',
                '--expected-store', 'adaway', '--expected-apk-url',
                'https://github.com/stevesolun/AdAway/releases/download/v13.5.0/AdAway_13.5.0.apk',
                '--report', self.report]
        if attested:
            args.append('--verify-attestations')
        result = self.exec(args + list(extra), env=self.case_env, check=False, cwd=cwd)
        self.assertEqual(passed, result.returncode == 0, result.stdout + result.stderr)
        if not passed:
            self.assertFalse(self.report.exists(), 'A stale passing report must not survive failure')
        return result

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def test_real_signed_apk_passes_and_unattested_source_is_unknown(self):
        self.verify()
        text = self.report.read_text()
        self.assertIn('- APK identity: verified', text)
        self.assertIn('- APK versionCode: 130500', text)
        self.assertIn('- Artifact source commit: not-verified', text)
        self.assertIn('- Tooling source commit: not-provided', text)
        self.assertNotIn('- Source commit:', text)
        self.assertFalse(self.calls())

    def test_tooling_commit_follows_tracked_source_not_callers_checkout(self):
        self.exec(['git', 'init', '-q', self.case])
        self.exec(['git', '-c', 'user.name=QA', '-c', 'user.email=qa@example.invalid',
                   '-c', 'commit.gpgSign=false', 'commit', '-q', '--allow-empty', '-m', 'fixture'],
                  cwd=self.case)
        self.case_env.update(GIT_DIR=str(self.case / '.git'), GIT_WORK_TREE=str(self.case))
        self.verify(source=self.verifier, cwd=self.case)
        self.assertIn('- Tooling source commit: ' + self.head, self.report.read_text())
        copied = self.case / self.verifier.name
        shutil.copyfile(self.verifier, copied)
        self.verify(source=copied, cwd=ROOT)
        self.assertIn('- Tooling source commit: not-provided', self.report.read_text())
        self.exec(['git', 'add', '--', copied.name], cwd=self.case)
        self.exec(['git', '-c', 'user.name=QA', '-c', 'user.email=qa@example.invalid',
                   '-c', 'commit.gpgSign=false', 'commit', '-q', '-m', 'copied verifier'],
                  cwd=self.case)
        with copied.open('a') as changed:
            changed.write('\n// Local change not present in the commit.\n')
        self.verify(source=copied, cwd=self.case)
        self.assertIn('- Tooling source commit: not-provided', self.report.read_text())

    def test_non_apk_unsigned_and_extra_signer_fail_with_authenticated_metadata(self):
        plain = self.case / 'plain.apk'
        plain.write_text('test apk bytes')
        for apk in (plain, self.make_apk(unsigned=True), self.make_apk(multiple=True)):
            with self.subTest(apk=apk):
                self.bundle(apk)
                self.verify(passed=False)

    def test_identity_mismatches_fail_with_authenticated_metadata(self):
        for identity in (dict(package='org.example.wrong'), dict(version='13.5.1'),
                         dict(code='130501'), dict(debug=True), dict(signer=1)):
            with self.subTest(identity=identity):
                self.bundle(self.make_apk(**identity))
                self.verify(passed=False)

    def test_metadata_version_code_is_required_and_positive(self):
        for value in (None, 0, -1, '130500', 130500.0, 2147483648):
            with self.subTest(value=value):
                self.bundle(versionCode=value)
                self.verify(passed=False)

    def test_signature_expiry_checksum_and_pin_checks_remain(self):
        for change in (dict(expiresAt='2020-01-01T00:00:00Z'),
                       dict(signingCertificateSha256='a' * 64), dict(apkSha256='a' * 64)):
            with self.subTest(change=change):
                self.bundle(**change)
                self.verify(passed=False)
        self.bundle()
        envelope = json.loads(self.manifest.read_text())
        envelope['payload'] += ' '
        self.manifest.write_text(json.dumps(envelope))
        self.checksum(self.manifest)
        self.verify(passed=False)
        self.bundle()
        self.apk.write_bytes(self.apk.read_bytes() + b'tampered')
        result = self.verify(passed=False)
        self.assertIn('Checksum mismatch for', result.stderr)

    def test_tampered_signed_apk_fails_even_after_metadata_is_resigned(self):
        with zipfile.ZipFile(self.apk, 'a') as archive:
            archive.writestr('assets/tampered.txt', 'fixture tampering')
        self.checksum(self.apk)
        self.payload['apkSha256'] = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        self.sign_metadata()
        self.verify(passed=False)

    def test_missing_pinned_sdk_never_falls_back_to_path(self):
        self.case_env['ANDROID_HOME'] = str(self.case / 'missing-sdk')
        self.case_env['ANDROID_SDK_ROOT'] = str(self.case / 'missing-sdk')
        result = self.verify(passed=False)
        self.assertIn('Android build-tools 36.0.0 required', result.stderr)
        self.assertFalse(self.calls())

    def test_source_tag_commit_workflow_policy_covers_all_six_artifacts(self):
        self.verify(attested=True, source=self.verifier)
        text = self.report.read_text()
        self.assertIn('- Artifact source commit: ' + COMMIT, text)
        self.assertIn('- Tooling source commit: ' + self.head, text)
        for marker in ('Expected version: 13.5.0', 'Expected channel: stable',
                       'Expected store: adaway', 'Checksum verification: passed',
                       'Manifest signature: passed', 'Manifest payload: passed',
                       'Attestations: verified', 'Attested artifacts: 6'):
            self.assertIn('- ' + marker, text)
        self.assertNotIn(self.public, text)
        self.assertNotEqual(COMMIT, self.head)
        checks = [call for call in self.calls() if call[1] == 'attestation']
        self.assertEqual(6, len(checks))
        self.assertEqual({self.apk.name, self.apk.name + '.sha256', 'manifest.json',
                          'manifest.json.sha256', 'adaway.cdx.json', 'adaway.cdx.json.sha256'},
                         {Path(call[3]).name for call in checks})
        self.assertEqual(2, len([call for call in self.calls() if call[1] == 'api']))

    def test_transient_attestation_lookup_is_retried(self):
        self.configure(transient_miss=True)
        self.verify(attested=True)
        checks = [call for call in self.calls() if call[1] == 'attestation']
        self.assertEqual(7, len(checks))
        self.assertEqual(checks[0], checks[1])

    def test_real_gh_accepts_generated_policy_before_reading_local_evidence(self):
        self.verify(attested=True)
        call = next(call for call in self.calls() if call[1] == 'attestation')
        gh = shutil.which('gh')
        self.assertIsNotNone(gh, 'The real gh CLI is required for the release policy check.')
        # Missing local inputs keep this offline. Flag conflicts fail before either file is read.
        root = self.case / 'absent-trusted-root.json'
        bundle = self.case / 'absent-bundle.jsonl'
        result = self.exec([gh, *call[1:], '--bundle', bundle, '--custom-trusted-root', root],
                           check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertTrue(any(str(path) in result.stderr for path in (root, bundle)), result.stderr)

    def test_mismatched_provenance_rejects_even_when_metadata_and_apk_match(self):
        for change in (dict(attested_commit=OTHER), dict(attested_ref='refs/heads/main'),
                       dict(workflow='untrusted.yml'), dict(deny_asset='adaway.cdx.json.sha256')):
            with self.subTest(change=change):
                self.configure(**change)
                self.verify(attested=True, passed=False)

    def test_unknown_failed_or_moving_tag_is_rejected(self):
        for config in ({'commit': 'not-provided'}, {'commit': COMMIT, 'api_failure': True},
                       {'commit': COMMIT, 'moved_commit': OTHER}):
            with self.subTest(config=config):
                self.log.unlink(missing_ok=True)
                self.config.write_text(json.dumps(config))
                result = self.verify(attested=True, passed=False)
                if 'moved_commit' in config:
                    self.assertIn('Release tag moved during verification', result.stderr)

    def test_metadata_refresh_is_explicit_and_distinct_from_apk_source(self):
        self.configure(metadata_commit=OTHER)
        self.verify(attested=True, passed=False)
        self.verify(attested=True, extra=['--metadata-source-commit', OTHER,
                    '--metadata-source-ref', 'refs/heads/main'])
        text = self.report.read_text()
        self.assertIn('- Metadata source commit: ' + OTHER, text)
        self.assertIn('- Artifact source commit: ' + COMMIT, text)
        self.verify(attested=True, extra=['--metadata-source-commit', OTHER], passed=False)

    def test_stale_success_report_removed_on_identity_failure(self):
        self.verify()
        self.bundle(self.make_apk(debug=True))
        self.verify(passed=False)

    def smoke(self, *, passed=True, verify_only=True, certificate=None):
        report = self.case / 'smoke.md'
        args = [self.pwsh, '-NoProfile', '-File', self.smoke_script,
                '-ApkPath', self.apk, '-ReleaseTag', 'v13.5.0', '-ExpectedCertSha256',
                self.cert if certificate is None else certificate,
                '-PublicKeyBase64', self.public, '-ReportPath', report, '-LaunchWaitSeconds', '0']
        if verify_only:
            args.append('-VerifyOnly')
        result = self.exec(args, env=self.case_env, check=False)
        self.assertEqual(passed, result.returncode == 0, result.stdout + result.stderr)
        self.smoke_output = result.stdout + result.stderr
        return report

    def install_tool_traps(self, *, signer_installed=True):
        sdk = self.case / 'sdk'
        for version in ('9.0.0', '36.0.0', '37.0.0'):
            directory = sdk / 'build-tools' / version
            (directory / 'lib').mkdir(parents=True)
            if version != '36.0.0' or signer_installed:
                (directory / 'lib/apksigner.jar').symlink_to(
                    (self.tools / 'lib/apksigner.jar').resolve())
            if version == '36.0.0':
                (directory / 'aapt').symlink_to((self.tools / 'aapt').resolve())
            else:
                self.write_trap(directory / 'aapt')
            self.write_trap(directory / 'apksigner')
        bin_path = self.case / 'bin'
        bin_path.mkdir()
        for name in ('aapt', 'apksigner'):
            self.write_trap(bin_path / name)
        self.case_env['ANDROID_HOME'] = str(sdk)
        self.case_env['ANDROID_SDK_ROOT'] = str(sdk)
        self.case_env['PATH'] = str(bin_path) + os.pathsep + self.case_env['PATH']

    @staticmethod
    def write_trap(path):
        shutil.copyfile(ROOT / 'scripts/tests/fixtures/release-tool-mock.py', path)
        path.chmod(0o755)

    def test_smoke_prefers_pinned_tools_over_old_new_and_path(self):
        self.install_tool_traps()
        report = self.smoke(certificate=':'.join(self.cert[i:i + 2] for i in range(0, 64, 2)))
        text = report.read_text()
        for marker in ('Status: passed', 'Mode: identity-only', 'Physical device: not-run',
                       'Release tag: v13.5.0', 'Artifact source commit: ' + COMMIT,
                       'Tooling source commit: ' + self.head,
                       'APK SHA-256: ' + hashlib.sha256(self.apk.read_bytes()).hexdigest(),
                       'Signer certificate SHA-256: ' + self.cert):
            self.assertIn('- ' + marker, text)
        self.assertIn('Physical-device install/launch smoke was not run', self.smoke_output)
        self.assertFalse([call for call in self.calls() if call[0] in ('aapt', 'apksigner', 'adb')])

    def test_smoke_missing_pinned_signer_never_falls_back(self):
        self.install_tool_traps(signer_installed=False)
        report = self.smoke(passed=False)
        self.assertIn('Android build-tools 36.0.0 required: lib/apksigner.jar', self.smoke_output)
        self.assertFalse(report.exists())
        self.assertFalse(self.calls())

    def test_smoke_wrong_certificate_and_actual_signer_fail_before_device_access(self):
        self.install_tool_traps()
        self.smoke(passed=False, verify_only=False, certificate='34' * 32)
        self.assertIn('does not match expected certificate', self.smoke_output)
        self.bundle(self.make_apk(signer=1))
        self.smoke(passed=False, verify_only=False)
        self.assertIn('Actual APK signer does not match', self.smoke_output)
        self.assertFalse(self.calls())

    def test_smoke_physical_report_binds_identity_and_redacts_serial(self):
        report = self.smoke(verify_only=False)
        text = report.read_text()
        for marker in ('Mode: physical-device', 'Physical device: verified-real-device',
                       'Artifact source commit: ' + COMMIT, 'Tooling source commit: ' + self.head,
                       'Launch pid observed: 4242', 'Release tag: v13.5.0',
                       'APK SHA-256: ' + hashlib.sha256(self.apk.read_bytes()).hexdigest(),
                       'Signer certificate SHA-256: ' + self.cert,
                       'Device serial SHA-256: ' + hashlib.sha256(b'fixture-physical').hexdigest()):
            self.assertIn('- ' + marker, text)
        self.assertNotIn('fixture-physical', text)
        commands = [call[3:] for call in self.calls() if call[0] == 'adb' and call[1] == '-s']
        self.assertIn(['install', '-r', str(self.apk)], commands)
        self.assertIn(['shell', 'am', 'force-stop', 'org.adaway'], commands)
        self.assertIn(['shell', 'monkey', '-p', 'org.adaway', '-c',
                       'android.intent.category.LAUNCHER', '1'], commands)
        self.assertIn(['shell', 'pidof', 'org.adaway'], commands)

    def test_smoke_rejects_emulator_before_install(self):
        self.configure(emulator=True)
        report = self.smoke(passed=False, verify_only=False)
        self.assertIn('must run on a real physical device', self.smoke_output)
        self.assertFalse(report.exists())
        self.assertFalse([call for call in self.calls() if 'install' in call or 'monkey' in call])

    def test_smoke_verifies_bundle_before_any_device_access(self):
        report = self.smoke()
        self.assertIn('- Artifact source commit: ' + COMMIT, report.read_text())
        self.assertIn('- Mode: identity-only', report.read_text())
        self.assertFalse([call for call in self.calls() if call[0] == 'adb'])
        self.bundle(self.make_apk(debug=True))
        self.smoke(passed=False, verify_only=False)
        self.assertFalse(report.exists())
        self.assertFalse([call for call in self.calls() if call[0] == 'adb'])

    def test_smoke_provenance_failure_never_reaches_adb(self):
        self.configure(attested_commit=OTHER)
        self.smoke(passed=False, verify_only=False)
        self.assertFalse([call for call in self.calls() if call[0] == 'adb'])

    def test_readiness_binds_artifact_not_tooling_revision(self):
        self.verify(attested=True, source=self.verifier)
        smoke = self.smoke(verify_only=False)  # All device operations are the local fake adb.
        ux = self.case / 'ux.md'
        ux.write_text('# UX Sign-Off Report\n' + '\n'.join([
            '- Status: passed', '- Source commit: ' + COMMIT, '- Reviewer: Fixture',
            '- Review packet: fixture.md', '- Review packet SHA-256: ' + 'a' * 64,
            '- Checked items: 45', '- Unchecked items: 0', '- Issues: 0']))
        license_report = self.case / 'license.md'
        license_report.write_text('# License Boundary Report\n' + '\n'.join([
            '- Status: passed', '- Source commit: ' + COMMIT, '- Source mode: GitTracked',
            '- Strict source archive: true', '- Strict artifacts: true',
            '- APK: AdAway_13.5.0.apk', '- SBOM: adaway.cdx.json',
            '- MIT release status: blocked until GPL-derived material is cleared', '- Issues: 0']))
        args = [self.pwsh, '-NoProfile', '-File', ROOT / 'scripts/verify-release-readiness.ps1',
                '-ReleaseArtifactReport', self.report, '-PhysicalSmokeReport', smoke,
                '-UxSignOffReport', ux, '-LicenseBoundaryReport', license_report,
                '-ReportPath', self.case / 'readiness.md']
        self.exec(args, env=self.case_env)
        original = self.report.read_text()
        smoke_text = smoke.read_text()
        for before, after in (
                ('Artifact source commit: ' + COMMIT, 'Artifact source commit: ' + self.head),
                ('Artifact source commit: ' + COMMIT, 'Source commit: ' + self.head),
                ('Artifact source commit: ' + COMMIT, 'Artifact source commit: not-verified'),
                ('Artifact source ref: refs/tags/v13.5.0', 'Artifact source ref: refs/heads/main'),
                ('fork-release-apk.yml', 'other.yml'),
                ('APK identity: verified', 'APK identity: not-verified'),
                ('APK debuggable: false', 'APK debuggable: true'),
                ('APK versionCode: 130500', 'APK versionCode: 130501'),
                ('Artifact provenance: verified', 'Artifact provenance: verified-not'),
                ('Metadata source commit: ' + COMMIT, 'Metadata source commit: not-verified'),
                ('Metadata source ref: refs/tags/v13.5.0', 'Metadata source ref: refs/heads/main'),
                ('Status: passed', 'Status: passed\n- Artifact source commit: ' + COMMIT)):
            with self.subTest(after=after):
                self.report.write_text(original.replace(before, after))
                result = self.exec(args, env=self.case_env, check=False)
                self.assertNotEqual(0, result.returncode, result.stdout)
        self.report.write_text(original)
        smoke.write_text(smoke_text.replace('Tooling source commit: ' + self.head,
                                           'Tooling source commit: ' + OTHER))
        self.exec(args, env=self.case_env)  # Independent tool revisions are allowed.
        smoke.write_text(smoke_text.replace('Artifact source commit: ' + COMMIT,
                                           'Artifact source commit: ' + OTHER))
        self.assertNotEqual(0, self.exec(args, env=self.case_env, check=False).returncode)


if __name__ == '__main__':
    unittest.main(verbosity=2)
