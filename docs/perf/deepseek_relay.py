"""DeepSeek 本地 TLS 中继：绕过网关对 Java JSSE TLS 指纹的响应截断（2026-10-19 起）。

背景：DeepSeek 网关对 JVM(JSSE) TLS 指纹的请求一律只回 1 字节 `{`（curl/python 正常）。
本中继在本地 18090 收 OpenAI 格式请求，用 python urllib（OpenSSL 指纹）转发，
响应字节级透传（含状态码与错误体，保证 LlmBackoff 能看到 429/503）。

用法：python deepseek_relay.py [port=18090]
后端：RESEARCHPILOT_MODEL_BASE_URL=http://localhost:18090
"""
import json
import sys
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

UPSTREAM = "https://api.deepseek.com"
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 18090


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _read_body(self):
        # httpclient5 常用 chunked 发送（无 Content-Length）——两种都要支持
        te = (self.headers.get("Transfer-Encoding") or "").lower()
        if "chunked" in te:
            chunks = []
            while True:
                size = int(self.rfile.readline().strip().split(b";")[0], 16)
                if size == 0:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.readline()
            return b"".join(chunks)
        length = int(self.headers.get("Content-Length", 0) or 0)
        return self.rfile.read(length) if length else b""

    def _relay(self):
        body = self._read_body()
        req = urllib.request.Request(UPSTREAM + self.path, data=body, method=self.command)
        for h in ("Authorization", "Content-Type", "Accept"):
            if self.headers.get(h):
                req.add_header(h, self.headers[h])
        req.add_header("Content-Length", str(len(body)))
        req.add_unredirected_header("Content-Length", str(len(body)))
        try:
            r = urllib.request.urlopen(req, timeout=600)
            data, status = r.read(), r.status
            ctype = r.headers.get("Content-Type", "application/json")
        except urllib.error.HTTPError as e:
            data, status = e.read(), e.code
            ctype = e.headers.get("Content-Type", "application/json")
        except Exception as e:
            data = json.dumps({"error": {"message": f"relay upstream error: {e}"}}).encode()
            status, ctype = 502, "application/json"
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        self._relay()

    def do_GET(self):
        self._relay()

    def log_message(self, fmt, *a):
        sys.stdout.write("%s %s\n" % (self.command, self.path.split("?")[0]))
        sys.stdout.flush()


if __name__ == "__main__":
    print(f"deepseek relay on :{PORT} -> {UPSTREAM}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
