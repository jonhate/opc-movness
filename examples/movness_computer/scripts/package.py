"""Package the existing build; fails on missing entries and never includes private state."""
import argparse, hashlib, json, zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);a=p.parse_args()
files=[root/'manifest.json',root/'LICENSE',root/'README.md',root/'THIRD_PARTY_NOTICES.md',root/'resources/android-bridge.jar',root/'dist/main.js',root/'dist/packages/computer.js',*sorted((root/'licenses').glob('*'))]
for f in files:
    if not f.is_file():raise SystemExit('Build entry missing: '+str(f))
with zipfile.ZipFile(root/'resources/android-bridge.jar') as dex:
    if not dex.read('classes.dex').startswith(b'dex\n'):raise SystemExit('Android DEX missing')
metadata=(root/'dist/packages/computer.js').read_text().split('/* METADATA\n',1)[1].split('*/',1)[0]
if len([t for t in json.loads(metadata)['tools'] if not t.get('advice')])!=8:raise SystemExit('Expected eight callable tools')
a.output.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as archive:
    for f in files:
        info=zipfile.ZipInfo(f.relative_to(root).as_posix(),(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
        archive.writestr(info,f.read_bytes())
with zipfile.ZipFile(a.output) as archive:
    if archive.testzip() is not None:raise SystemExit('Archive integrity check failed')
print(json.dumps({'file':str(a.output.resolve()),'bytes':a.output.stat().st_size,'sha256':hashlib.sha256(a.output.read_bytes()).hexdigest()}))
