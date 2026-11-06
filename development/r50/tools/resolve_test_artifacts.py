from pathlib import Path
import threading,subprocess
from maven_fetch_bridge import ThreadingHTTPServer,Handler
r=Path(__file__).resolve().parent.parent
server=ThreadingHTTPServer(('127.0.0.1',8873),Handler)
threading.Thread(target=server.serve_forever,daemon=True).start()
try:
    cp=':'.join(str(p) for p in (r/'tools/apache-maven-3.9.11/lib').glob('*.jar'))
    subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',cp,'-d',str(r/'tools'),str(r/'tools/ResolveTests.java')],check=True)
    subprocess.run(['java','-cp',str(r/'tools')+':'+cp,'ResolveTests','/root/.m2/repository',str(r/'tools/test-deps'),'http://127.0.0.1:8873'],check=True)
finally:server.shutdown();server.server_close()
