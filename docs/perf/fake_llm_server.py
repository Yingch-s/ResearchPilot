"""P1-D 压测用 mock OpenAI 服务（可控延迟，返回合法规划 JSON）。

用法：python fake_llm_server.py [port] [delay_ms]
压测时后端以 researchpilot.model.base-url=http://localhost:<port> 启动。
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 18081
DELAY = float(sys.argv[2]) / 1000 if len(sys.argv) > 2 else 0.2

PLAN = {"steps": [{"title": f"步骤{i}：stub 研究"} for i in range(1, 5)],
        "dimensions": ["核心方法"], "clarifying_questions": []}


class Handler(BaseHTTPRequestHandler):

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0))).decode("utf-8")
        time.sleep(DELAY)
        content = json.dumps(PLAN, ensure_ascii=False) if "步骤" in body else "（stub 发现）有直接支撑。"
        resp = json.dumps({"id": "stub", "object": "chat.completion", "created": 0, "model": "stub-model",
                           "choices": [{"index": 0, "message": {"role": "assistant", "content": content},
                                        "finish_reason": "stop"}],
                           "usage": {"prompt_tokens": 100, "completion_tokens": 50, "total_tokens": 150}},
                          ensure_ascii=False)
        data = resp.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    print(f"fake llm on :{PORT} delay={DELAY}s", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
