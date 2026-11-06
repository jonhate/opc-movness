"""DSH-side installer for r46's existing debug receiver. Requires an authorized ADB device.
Uploading or broadcasting is not treated as successful loading. No application is uninstalled.
"""
import argparse, hashlib, json, re, shutil, subprocess, time, uuid
from pathlib import Path
APP='io.movness.app.operit.preview'
PACKAGE='io.movness.computer'
p=argparse.ArgumentParser();p.add_argument('archive',type=Path);p.add_argument('--serial');p.add_argument('--adb',default='adb');p.add_argument('--receipt',type=Path,default=Path('adb-install-result.json'));a=p.parse_args()
adb=shutil.which(a.adb)
if not adb:raise SystemExit('ADB is unavailable; this installer has not run.')
def command(*args,check=True):
    return subprocess.run([adb,*prefix,*args],capture_output=True,text=True,check=check)
prefix=[]
rows=command('devices').stdout.splitlines()[1:]
devices=[line.split()[0] for line in rows if len(line.split())>=2 and line.split()[1]=='device']
serial=a.serial
if serial is None:
    if len(devices)!=1:raise SystemExit('Exactly one authorized ADB device is required; select --serial when several are connected.')
    serial=devices[0]
if serial not in devices:raise SystemExit('The selected ADB device is not authorized or is offline.')
prefix=['-s',serial]
if not a.archive.is_file() or a.archive.suffix!='.toolpkg':raise SystemExit('Provide the built .toolpkg file.')
if not command('shell','pm','path',APP).stdout.strip():raise SystemExit('Install Movness r46 first; no older app will be removed.')
user=int(command('shell','am','get-current-user').stdout.strip())
digest=hashlib.sha256(a.archive.read_bytes()).hexdigest()
folder=f'/storage/emulated/{user}/Android/data/{APP}/files/packages'
remote=f'{folder}/movness-computer-{digest[:12]}-{uuid.uuid4().hex[:8]}.toolpkg'
receipt={'archive_sha256':digest,'uploaded':False,'broadcast_sent':False,'load_observed':False,'native_ui_verified':False,'physical_pairing_verified':False}
try:
    command('shell','mkdir','-p',folder);command('push',str(a.archive.resolve()),remote)
    checksum=command('shell','toybox','sha256sum',remote).stdout.split()[0]
    if checksum!=digest:raise RuntimeError('Device copy checksum mismatch')
    receipt['uploaded']=True
    component=APP+'/com.ai.assistance.operit.core.tools.packTool.ToolPkgDebugInstallReceiver'
    result=command('shell','am','broadcast','-n',component,'-a','com.ai.assistance.operit.DEBUG_INSTALL_TOOLPKG','--es','package_name',PACKAGE,'--es','file_path',remote,'--ez','reset_subpackage_states','true')
    if re.search('SecurityException|Permission Denial|Error:',result.stdout+result.stderr):raise RuntimeError('Android rejected the installation broadcast')
    receipt['broadcast_sent']=True
    deadline=time.monotonic()+25
    while time.monotonic()<deadline:
        lines=command('logcat','-d','-t','500','-s','ToolPkgDebugInstallReceiver:D','*:S').stdout
        marker=lines.rfind('Starting debug toolpkg install: package='+PACKAGE+', filePath='+remote)
        if marker>=0:
            current=lines[marker:]
            if 'Successfully installed debug toolpkg: '+PACKAGE in current:
                receipt['load_observed']=True;break
            if 'Failed to reload debug toolpkg' in current or 'Debug toolpkg install failed' in current:
                raise RuntimeError('The application rejected ToolPkg loading; inspect its debug log locally.')
        time.sleep(.5)
    if not receipt['load_observed']:raise RuntimeError('Broadcast sent, but application loading was not observed. Do not mark installation verified.')
finally:
    a.receipt.write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(receipt,ensure_ascii=False,indent=2))
