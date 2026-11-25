#!/usr/bin/env python3
"""Local-only gh/adb process fixtures; never forwards a command to a real service."""
import json
import os
from pathlib import Path
import sys

args = sys.argv[1:]
config = json.loads(Path(os.environ['RELEASE_MOCK_CONFIG']).read_text())
with Path(os.environ['RELEASE_MOCK_LOG']).open('a') as log:
    log.write(json.dumps([Path(sys.argv[0]).name, *args]) + '\n')

if Path(sys.argv[0]).name == 'adb':
    if args == ['devices']:
        print('List of devices attached\nfixture-physical\tdevice')
    elif args[:2] == ['-s', 'fixture-physical']:
        command = args[2:]
        if command[:2] == ['shell', 'getprop']:
            print('fixture' if command[-1] == 'ro.hardware' else
                  ('1' if config.get('emulator') else '0'))
        elif command[:2] == ['shell', 'pidof']:
            print('4242')
        elif command[:2] == ['install', '-r']:
            print('Success')
        elif command[:2] != ['shell', 'am'] and command[:2] != ['shell', 'monkey']:
            sys.exit('unexpected mocked adb call')
    else:
        sys.exit('unexpected mocked adb target')
elif args[:1] == ['api']:
    assert args[1:] == [
        'repos/stevesolun/AdAway/commits/refs%2Ftags%2Fv13.5.0', '--jq', '.sha']
    state = Path(os.environ['RELEASE_MOCK_LOG']).read_text().count('"api"')
    if config.get('api_failure'):
        sys.exit('mock API failure')
    print(config.get('moved_commit', config['commit']) if state > 1 else config['commit'])
elif args[:2] == ['attestation', 'verify']:
    assert '--deny-self-hosted-runners' in args
    identities = {'--cert-identity', '--cert-identity-regex', '--signer-repo', '--signer-workflow'}
    if len(identities.intersection(args)) != 1:
        sys.exit('gh requires exactly one signer selector for this policy')
    values = dict(zip(args[3:-1:2], args[4:-1:2]))
    metadata = Path(args[2]).name in ('manifest.json', 'manifest.json.sha256')
    refreshed = metadata and config.get('metadata_commit')
    commit = config['metadata_commit'] if refreshed else config.get('attested_commit', config['commit'])
    ref = 'refs/heads/main' if refreshed else config.get('attested_ref', 'refs/tags/v13.5.0')
    workflow = 'stevesolun/AdAway/.github/workflows/' + (
        'refresh-update-manifest.yml' if refreshed else config.get('workflow', 'fork-release-apk.yml'))
    expected = {
        '--repo': 'stevesolun/AdAway', '--source-ref': ref, '--source-digest': commit,
        '--signer-digest': commit,
        '--cert-identity': 'https://github.com/' + workflow + '@' + ref,
        '--predicate-type': 'https://slsa.dev/provenance/v1',
    }
    if values != expected or config.get('deny_asset') == Path(args[2]).name:
        sys.exit('mock attestation policy rejected')
    checks = [json.loads(line) for line in Path(os.environ['RELEASE_MOCK_LOG']).read_text().splitlines()
              if '"attestation"' in line]
    if config.get('transient_miss') and len(checks) == 1:
        sys.exit('mock transient attestation lookup miss')
    print('mock cryptographic verification accepted')
else:
    sys.exit('unexpected mock command; no real gh or adb is ever invoked')
