"""Apply the verified complete DEX-group/resource patch to a FRESH no-src r49 decode.
Run from the extracted source-package root. Decode/build/sign with Apktool 3.0.3
and the original private signing material; this command never generates keys.
"""
from pathlib import Path
import argparse,hashlib,json
p=argparse.ArgumentParser();p.add_argument('--base',type=Path,required=True);p.add_argument('--decoded',type=Path,required=True);args=p.parse_args()
root=Path(__file__).resolve().parent;manifest=json.loads((root/'apk-patch-manifest.json').read_text())
assert hashlib.sha256(args.base.read_bytes()).hexdigest()==manifest['base_sha256'],'Wrong base APK'
target=args.decoded.resolve();assert (target/'apktool.yml').is_file(),'Decode r49 with Apktool --no-src first'
assert not any(target.glob('smali*')),'Use a fresh --no-src decode, not an existing development tree'
for group in manifest['rebuilt_dex_groups']:assert (target/f'classes{group}.dex').is_file()
patch=root/'apk-patch'
for relative,expected in manifest['files'].items():
    source=patch/relative;assert hashlib.sha256(source.read_bytes()).hexdigest()==expected,relative
    destination=target/relative;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(source.read_bytes())
for group in manifest['rebuilt_dex_groups']:(target/f'classes{group}.dex').unlink()
print('Applied the r50 patch. Build with Apktool 3.0.3, align, and sign with the original certificate.')
