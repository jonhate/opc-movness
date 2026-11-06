from pathlib import Path
from collections import Counter
from dex_audit import Dex,audit_references
import hashlib,json,re,subprocess,zipfile,io,sys,os

R=Path(__file__).resolve().parent;B=R.parent/'r49-brand';T=B/'tools/android-15';E=R/'evidence';D=R/'deliverables';D.mkdir(exist_ok=True)
apk=D/'Movness-r50.apk';base=B/'deliverables/Movness-r49.apk'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def run(args,log):
    result=subprocess.run([str(x) for x in args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=True)
    (E/log).write_bytes(result.stdout);return result.stdout.decode('utf8','replace')
expected=json.loads((E/'production-inputs.json').read_text())
assert expected=={str(p.relative_to(R)):sha(p) for p in sorted((R/'src').rglob('*.java'))},'Unbuilt source changes'
assert 'OK (9 tests)' in (E/'native-ui-tests.log').read_text(),'Native UI checks must pass'
assert sha(base)=='3fe5c3fcc61927772606d80c690f995ec19801ce39f125aa0cea6df0ed1c773c'
certificate='ce3ca43c7ea2752dca11840c2e5a9f5c711da1656e0d805870d824ae5bb0c021'
if '--verify-only' not in sys.argv:
    run([T/'zipalign','-f','4',R/'Movness-r50-unsigned.apk',R/'Movness-r50-aligned.apk'],'align.log')
    key=B/'private-signing';assert sha(key/'cert.der')==certificate
    run(['java','-jar',T/'lib/apksigner.jar','sign','--ks',key/'debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','file:'+str(key/'store.pass'),'--key-pass','file:'+str(key/'key.pass'),'--v4-signing-enabled','false','--out',apk,R/'Movness-r50-aligned.apk'],'sign.log')
sig=run(['java','-jar',T/'lib/apksigner.jar','verify','--verbose','--print-certs',apk],'signature.log')
for line in ['Verified using v2 scheme (APK Signature Scheme v2): true','Verified using v3 scheme (APK Signature Scheme v3): true','ce3ca43c7ea2752dca11840c2e5a9f5c711da1656e0d805870d824ae5bb0c021']:assert line in sig,line
run([T/'zipalign','-c','4',apk],'alignment-check.log')
badging=run([T/'aapt2','dump','badging',apk],'badging.log')
assert "package: name='io.movness.app.operit.preview' versionCode='50' versionName='0.5.2'" in badging
assert all("'Movness'" in line for line in badging.splitlines() if line.startswith('application-label'))
resources=run([T/'aapt2','dump','resources',apk],'resources-final.log');oldresources=run([T/'aapt2','dump','resources',base],'resources-base.log')
ids=lambda s:dict(re.findall(r'resource (0x[0-9a-fA-F]+) ([^\s]+)',s))
assert ids(oldresources).items()<=ids(resources).items(),'Existing resource IDs changed'
manifest=run([T/'aapt2','dump','xmltree',apk,'--file','AndroidManifest.xml'],'manifest-final.log')
for value in ['io.movness.ui.LearningService','android.permission.BIND_ACCESSIBILITY_SERVICE','movness_learning_accessibility']:assert value in manifest or value in resources,value
with zipfile.ZipFile(base) as old,zipfile.ZipFile(apk) as new:
    assert len(new.namelist())==len(set(new.namelist())),'Duplicate ZIP entries'
    assert new.testzip() is None,'APK CRC error'
    a=set(old.namelist());b=set(new.namelist())
    extra=b-a
    assert not [n for n in extra if not (n.endswith('/') or n.startswith('res/') or n in {'classes45.dex','assets/packages/movness_phone_bundle.toolpkg','assets/movness-notices.txt'})],sorted(extra)
    assert not [n for n in a-b if not n.startswith('META-INF/')],sorted(a-b)
    changed=[];preserved=[];oldclasses=Counter();newclasses=Counter()
    rebuilt={18,20,22,29}
    for n in sorted(a&b):
        if n.endswith('/'):continue
        first,last=old.read(n),new.read(n)
        if first!=last:changed.append(n)
        match=re.fullmatch(r'classes(\d*)\.dex',n)
        if match:
            group=int(match.group(1) or '1')
            if group not in rebuilt:assert first==last,'Unexpected DEX mutation: '+n
            else:assert Counter(Dex(first).classes())==Counter(Dex(last).classes()),n
        if n.startswith('lib/') or n.startswith('assets/models/') or n=='assets/packages/movness_computer_bundle.toolpkg':
            assert first==last,n
            preserved.append({'path':n,'sha256':hashlib.sha256(last).hexdigest()})
    for n in a:
        if re.fullmatch(r'classes\d*\.dex',n):oldclasses.update(Dex(old.read(n)).classes())
    for n in b:
        if re.fullmatch(r'classes\d*\.dex',n):newclasses.update(Dex(new.read(n)).classes())
    assert not oldclasses-newclasses,'Original class definitions removed'
    additions=newclasses-oldclasses;program=Counter(Dex((R/'native-dex/classes.dex').read_bytes()).classes())
    assert additions==program,'Packaged new classes differ from the compiled program'
    assert all(n.startswith(('Lio/movness/ui/','Lorg/commonmark/')) for n in additions),list(additions)
    assert all(count==1 for count in newclasses.values()),'Duplicate class definitions'
    host=audit_references(old,new.read('classes45.dex'));(E/'host-direct-references.json').write_text(json.dumps(host,indent=2))
    assert hashlib.sha256(new.read('assets/packages/movness_computer_bundle.toolpkg')).hexdigest()=='5927121156dde2308082a69511e579a4b89662354a65a6a853e0b357ffdf2b55'
    pkg=new.read('assets/packages/movness_phone_bundle.toolpkg')
    assert pkg==(R/'phone-package/movness_phone_bundle.toolpkg').read_bytes()
    with zipfile.ZipFile(io.BytesIO(pkg)) as z:
        assert z.testzip() is None
        assert json.loads(z.read('manifest.json'))['toolpkg_id']=='io.movness.phone'
        script=z.read('phone.js').decode();metadata=json.loads(re.search(r'/\* METADATA\s*(.*?)\s*\*/',script,re.S).group(1))
        assert len(metadata['tools'])==8
        run([os.environ.get('CODEX_PRIMARY_RUNTIME_NODE','node'),R/'tests/phone-tools.cjs'],'phone-tools-test.log')
    notices=new.read('assets/movness-notices.txt').decode();assert 'CommonMark' in notices and 'BSD' in notices
    (E/'apk-content-audit.json').write_text(json.dumps({'changed':changed,'added':sorted(extra),'removed':sorted(a-b),'old_classes':sum(oldclasses.values()),'new_classes':sum(newclasses.values()),'preserved_native_models_bundle':preserved},ensure_ascii=False,indent=2))
status={'artifact':apk.name,'bytes':apk.stat().st_size,'sha256':sha(apk),'package':'io.movness.app.operit.preview','versionCode':50,'versionName':'0.5.2','label':'Movness','certificate_sha256':certificate,'signature_v2':True,'signature_v3':True,'zip_alignment':True,'zip_crc':True,'existing_resource_ids_preserved':True,'all_original_classes_preserved':True,'unchanged_dex_groups':40,'host_direct_references_verified':len(host['checked_host_method_references']),'native_ui_tests':9,'phone_tool_protocol_fixture_passed':True,'r48_computer_bundle_preserved':True,'physical_device_tested':False,'real_model_and_windows_tested':False}
(D/'verification.json').write_text(json.dumps(status,ensure_ascii=False,indent=2));(D/'SHA256SUMS.txt').write_text(status['sha256']+'  '+apk.name+'\n');print(json.dumps(status,ensure_ascii=False))
