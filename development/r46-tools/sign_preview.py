#!/usr/bin/env python3
"""Sign a source-built preview with the existing private key and verify its identity."""
import argparse, hashlib, json, os, re, subprocess, zipfile
from pathlib import Path

EXPECTED_SIGNER = 'ce3ca43c7ea2752dca11840c2e5a9f5c711da1656e0d805870d824ae5bb0c021'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ['apk', 'out', 'keystore', 'apksigner', 'aapt', 'evidence']:
        parser.add_argument('--' + option, type=Path, required=True)
    args = parser.parse_args()
    for variable in ['MOVNESS_PREVIEW_STORE_PASSWORD', 'MOVNESS_PREVIEW_KEY_PASSWORD']:
        if not os.environ.get(variable):
            raise SystemExit(f'Set {variable} privately before signing.')
    with zipfile.ZipFile(args.apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise SystemExit('Duplicate APK archive entries')
        if any(name.startswith('assets/models/') and Path(name).name.startswith('.') for name in names):
            raise SystemExit('A temporary model download was included in the APK')
    args.evidence.mkdir(parents=True, exist_ok=True)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([
        str(args.apksigner.resolve()), 'sign', '--ks', str(args.keystore.resolve()),
        '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'env:MOVNESS_PREVIEW_STORE_PASSWORD',
        '--key-pass', 'env:MOVNESS_PREVIEW_KEY_PASSWORD', '--v4-signing-enabled', 'false',
        '--out', str(args.out.resolve()), str(args.apk.resolve()),
    ], check=True)
    verified = subprocess.run([
        str(args.apksigner.resolve()), 'verify', '--verbose', '--print-certs', str(args.out.resolve()),
    ], check=True, capture_output=True, text=True).stdout
    match = re.search(r'certificate SHA-256 digest: ([0-9a-f]+)', verified)
    if match is None or match[1] != EXPECTED_SIGNER:
        raise SystemExit('Certificate does not match the existing Movness preview signer.')
    badging = subprocess.run([
        str(args.aapt.resolve()), 'dump', 'badging', str(args.out.resolve()),
    ], check=True, capture_output=True, text=True).stdout
    package = next(line for line in badging.splitlines() if line.startswith('package:'))
    launcher = next(line for line in badging.splitlines() if line.startswith('launchable-activity:'))
    if "name='io.movness.app.operit.preview'" not in package or "versionCode='46'" not in package:
        raise SystemExit('Unexpected package or version. Update the release contract deliberately.')
    if "label='Movness Preview'" not in launcher:
        raise SystemExit('Launcher label still names a different distribution.')
    with args.out.open('rb') as stream:
        sha = hashlib.file_digest(stream, 'sha256').hexdigest()
    result = dict(file=args.out.name, bytes=args.out.stat().st_size, sha256=sha,
                  certificate_sha256=match[1], package='io.movness.app.operit.preview',
                  version_code=46, version_name='0.5.0-operit-preview', signature_verified=True,
                  signed_using_existing_movness_preview_key=True, physical_device_tested=False)
    (args.evidence / 'APK_RESULT.json').write_text(json.dumps(result, indent=2) + '\n')
    (args.evidence / 'signature_check.txt').write_text(verified)
    (args.evidence / 'apk_badging.txt').write_text('\n'.join(line for line in badging.splitlines()
        if line.startswith(('package:', 'sdkVersion:', 'targetSdkVersion:', 'application-label:',
                            'launchable-activity:', 'native-code:'))) + '\n')
    print(json.dumps(result, indent=2))

if __name__ == '__main__':
    main()
