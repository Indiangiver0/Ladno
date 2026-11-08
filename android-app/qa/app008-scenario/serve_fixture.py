"""Loopback-only QA server; serves the unmodified production UI and explicit synthetic fixture."""
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
import mimetypes

HERE = Path(__file__).resolve().parent
WWW = HERE.parents[1] / 'app/src/main/assets/www'
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        name = self.path.split('?', 1)[0].lstrip('/') or 'fixture.html'
        if name in {'fixture.html', 'fixture-adapter.js'}: path = HERE / name
        elif name in {'index.html', 'styles.css', 'view.js', 'app.js', 'bridge.js', 'preview-adapter.js'}: path = WWW / name
        else: self.send_error(404); return
        body = path.read_bytes()
        self.send_response(200)
        self.send_header('Content-Type', mimetypes.guess_type(str(path))[0] or 'application/octet-stream')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers(); self.wfile.write(body)
ThreadingHTTPServer(('127.0.0.1', 8769), Handler).serve_forever()
