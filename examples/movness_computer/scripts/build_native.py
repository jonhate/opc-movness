"""Compile the small Android bridge; dependencies are pinned by digest, never embedded."""
import argparse, hashlib, json, subprocess, urllib.request, zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--cache', type=Path, required=True)
parser.add_argument('--test', action='store_true')
args = parser.parse_args()
cache = args.cache.resolve()
cache.mkdir(parents=True, exist_ok=True)
for row in json.loads((root/'native/dependencies.json').read_text()):
    target = cache/row['file']
    if not target.exists():
        with urllib.request.urlopen(row['source'], timeout=45) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != row['sha256']:
            raise RuntimeError('Dependency checksum mismatch: '+row['file'])
        target.write_bytes(data)
    if hashlib.sha256(target.read_bytes()).hexdigest() != row['sha256']:
        raise RuntimeError('Dependency checksum mismatch: '+row['file'])
with zipfile.ZipFile(cache/'security-crypto.aar') as archive:
    (cache/'security-crypto.jar').write_bytes(archive.read('classes.jar'))
classes = cache/'classes'
classes.mkdir(exist_ok=True)
import os
classpath = os.pathsep.join(str(cache/name) for name in ['android.jar','security-crypto.jar','zxing-core.jar','okhttp.jar','okio.jar','json.jar','kotlin-stdlib.jar'])
# Direct jdk.compiler invocation needs an explicit classpath, not a wildcard.
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-source','8','-target','8','-cp',classpath,'-d',str(classes),*(str(p) for p in sorted((root/'native').glob('*.java')))],check=True)
jar = cache/'bridge-classes.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(classes.rglob('*.class')):
        info = zipfile.ZipInfo(path.relative_to(classes).as_posix(), date_time=(1980,1,1,0,0,0))
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info,path.read_bytes())
(root/'resources').mkdir(exist_ok=True)
subprocess.run(['java','-cp',str(cache/'r8.jar'),'com.android.tools.r8.D8','--min-api','26','--lib',str(cache/'android.jar'),'--output',str(root/'resources/android-bridge.jar'),str(jar)],check=True)
if args.test:
    tests = cache/'test-classes'
    tests.mkdir(exist_ok=True)
    runtime = [classes, tests, *(cache/name for name in ['json.jar','zxing-core.jar','okhttp.jar','okio.jar','kotlin-stdlib.jar'])]
    cp = os.pathsep.join(str(p) for p in runtime)
    subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-cp',cp,'-d',str(tests),*(str(p) for p in sorted((root/'test/native').glob('*.java')))],check=True)
    messages = []
    for main in ['NativeFixture','ArtifactFixture']:
        result = subprocess.run(['java','-Djava.awt.headless=true','-cp',cp,'io.movness.computer.'+main],capture_output=True,text=True,check=True)
        messages.append(result.stdout)
        print(result.stdout,end='')
    (root/'evidence').mkdir(exist_ok=True)
    (root/'evidence/native-jvm.log').write_text(''.join(messages)+'Android Bitmap/Keystore/callback bridge and real phone intents not exercised.\n')
    (cache/'runtime-classpath.txt').write_text(cp)
print('Built Android DEX bridge; physical-device loading remains a separate acceptance step.')
