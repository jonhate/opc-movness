#!/usr/bin/env python3
"""Verify the handoff and restore its pinned source. Requires Python 3.12+."""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess, sys, tarfile

ROOT = Path(__file__).resolve().parents[1]

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def verify():
    manifest = json.loads((ROOT / 'SHA256SUMS.json').read_text(encoding='utf-8'))
    for relative, expected in manifest['files'].items():
        path = (ROOT / relative).resolve()
        if not path.is_relative_to(ROOT):
            raise ValueError('Manifest path is outside the handoff')
        if digest(path) != expected:
            raise ValueError(f'File checksum mismatch: {relative}')
    print(f"Verified {len(manifest['files'])} files")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dest', type=Path)
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    if sys.version_info < (3, 12):
        raise SystemExit('Python 3.12+ is required for safe archive extraction.')
    verify()
    if args.verify_only:
        return
    if args.dest is None:
        parser.error('--dest is required unless --verify-only is set')
    destination = args.dest.resolve()
    if destination == ROOT or ROOT.is_relative_to(destination):
        raise SystemExit('Use a destination outside the handoff bundle.')
    if destination.exists() and any(destination.iterdir()):
        raise SystemExit('Destination is not empty. Continue its existing work; do not overwrite it.')
    destination.mkdir(parents=True, exist_ok=True)
    for archive, target, strip in [
        ('upstream/Operit.tar.gz', destination, True),
        ('upstream/OperitTerminalCore.tar.gz', destination / 'terminal', True),
    ]:
        target.mkdir(parents=True, exist_ok=True)
        with tarfile.open(ROOT / archive) as source:
            for member in source.getmembers():
                relative = Path(*Path(member.name).parts[1:]) if strip else Path(member.name)
                if not relative.parts:
                    continue
                member.name = relative.as_posix()
                source.extract(member, target, filter='data')
    shutil.copytree(ROOT / 'overlay', destination, dirs_exist_ok=True)
    subprocess.run([
        sys.executable, str(destination / 'ci/script/prepare_android_dependencies.py'),
        '--profile', 'full', '--archives', str(ROOT / 'manual-deps'),
        '--repository', str(destination),
    ], check=True)
    # User-local SDK/signing/proxy configuration is deliberately not part of the source snapshot.
    print(f'Restored source: {destination}')
    print('Read HANDOFF.md build commands; keep all generated files in this worktree.')

if __name__ == '__main__':
    main()
