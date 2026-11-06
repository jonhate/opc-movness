from pathlib import Path
import threading,subprocess,sys,zipfile,json
R=Path(__file__).resolve().parent;T=R/'tools';E=R/'evidence'
sys.path.insert(0,str(T))
from maven_fetch_bridge import ThreadingHTTPServer,Handler
for aar in (T/'test-deps').glob('*.aar'):
    with zipfile.ZipFile(aar) as z:aar.with_suffix('.jar').write_bytes(z.read('classes.jar'))
cp=[R/'classes',T/'android-35.jar',R.parent/'r48/native-deps/kotlin-stdlib.jar']+list(T.glob('*.jar'))+list((T/'test-deps').glob('*.jar'))
classpath=':'.join(map(str,cp));classes=R/'test-classes';classes.mkdir(exist_ok=True)
with (E/'test-compile.log').open('w') as out:
    subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-encoding','UTF-8','-cp',classpath,'-d',str(classes),*map(str,(R/'tests').rglob('*.java'))],stdout=out,stderr=subprocess.STDOUT,check=True)
server=ThreadingHTTPServer(('127.0.0.1',8874),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
try:
    command=['java','-Xmx3g','-Drobolectric.dependency.repo.url=http://127.0.0.1:8874/central','-Djava.awt.headless=true','-Duser.language=zh','-Duser.country=CN','-cp',str(classes)+':'+classpath,'org.junit.runner.JUnitCore','io.movness.ui.NativeUiTest']
    with (E/'native-ui-tests.log').open('w') as out:result=subprocess.run(command,stdout=out,stderr=subprocess.STDOUT)
    raise SystemExit(result.returncode)
finally:server.shutdown();server.server_close()
