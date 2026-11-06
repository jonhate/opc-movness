"""Development-only Maven transport through the runtime's working Python networking."""
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
from pathlib import Path
import urllib.request,urllib.error,urllib.parse

cache=Path(__file__).parent/'maven-fetch-cache'
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parts=urllib.parse.urlsplit(self.path).path.strip('/').split('/')
        if len(parts)<2 or parts[0] not in {'central','google'} or '..' in parts:
            self.send_error(400);return
        base={'central':'https://repo.maven.apache.org/maven2/','google':'https://dl.google.com/dl/android/maven2/'}[parts[0]]
        path=cache.joinpath(*parts)
        try:
            if not path.is_file():
                with urllib.request.urlopen(base+'/'.join(parts[1:]),timeout=90) as response:data=response.read()
                path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
            data=path.read_bytes();self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
        except urllib.error.HTTPError as error:self.send_error(error.code)
        except Exception as error:self.send_error(502,type(error).__name__)
    def log_message(self,*args):pass
if __name__=='__main__':
    print('Public dependency transport ready on loopback',flush=True)
    ThreadingHTTPServer(('127.0.0.1',8871),Handler).serve_forever()
