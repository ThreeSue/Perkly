"""Loopback-only host for the dedicated management AI browser fixture. Never a production server."""
from pathlib import Path
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from urllib.parse import urlsplit, parse_qs
import http.client
import json

ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / "cool-admin-vue-8.x/dist"
SESSION = ROOT / "cool-admin-java-test/logs/test/management-browser-session.json"

class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(DIST), **kwargs)
    def log_message(self, format, *args):
        pass
    def valid_host(self):
        return self.headers.get("Host", "").split(":")[0] in {"127.0.0.1", "localhost"}
    def fixture(self):
        return json.loads(SESSION.read_text(encoding="utf-8"))
    def do_GET(self):
        if not self.valid_host():
            self.send_error(403); return
        path = urlsplit(self.path)
        if path.path == "/__fixture":
            role = parse_qs(path.query).get("role", ["admin"])[0]
            if role not in {"admin", "owner", "staff"}:
                self.send_error(400); return
            config = self.fixture()
            identity = {"admin": "PLATFORM", "owner": "MERCHANT", "staff": "STORE"}[role]
            prefix = "" if role == "admin" else "workspace:" + identity + ":"
            values = {
                prefix + "token": config[role + "Token"],
                prefix + "token_deadtime": __import__("time").time() * 1000 + 3500000,
                prefix + "userInfo": {"id": config[role + "Id"], "username": config["prefix"] + role, "name": "开发验收" + role}
            }
            script = "const values=" + json.dumps(values, ensure_ascii=False) + ";Object.entries(values).forEach(([k,v])=>localStorage.setItem(k,JSON.stringify(v)));sessionStorage.setItem('perkly.workspace'," + json.dumps(identity) + ");location.replace('/my/assistant?identity=" + identity + "');"
            body = ("<!doctype html><meta charset='utf-8'><title>开发验收身份</title><p>进入隔离管理端验收…</p><script>" + script + "</script>").encode()
            self.send_response(200);self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Cache-Control","no-store");self.send_header("Content-Length",str(len(body)));self.end_headers();self.wfile.write(body);return
        if path.path.startswith("/api/"):
            self.proxy();return
        if not Path(self.translate_path(path.path)).is_file():
            self.path = "/index.html"
        super().do_GET()
    def do_POST(self):
        if not self.valid_host() or not urlsplit(self.path).path.startswith("/api/"):
            self.send_error(403);return
        self.proxy()
    def do_DELETE(self):
        self.do_POST()
    def proxy(self):
        config = self.fixture()
        connection = http.client.HTTPConnection("127.0.0.1", config["port"], timeout=180)
        headers = {k:v for k,v in self.headers.items() if k.lower() not in {"host", "connection", "accept-encoding"}}
        body = self.rfile.read(int(self.headers.get("Content-Length","0")))
        try:
            connection.request(self.command, self.path[len("/api"):], body=body, headers=headers)
            response = connection.getresponse(); content = response.read()
            self.send_response(response.status)
            for k,v in response.getheaders():
                if k.lower() not in {"transfer-encoding", "connection", "content-length"}:
                    self.send_header(k,v)
            self.send_header("Content-Length",str(len(content)));self.end_headers();self.wfile.write(content)
        finally:
            connection.close()

if __name__ == "__main__":
    if not SESSION.exists() or not (DIST / "index.html").exists():
        raise SystemExit("Start isolated fixture and build frontend first.")
    print("Management fixture UI: http://127.0.0.1:9140/__fixture?role=admin", flush=True)
    ThreadingHTTPServer(("127.0.0.1",9140),Handler).serve_forever()
