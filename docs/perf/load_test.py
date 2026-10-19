"""P1-D 压测脚本（I8 修订版）：分波提交、429 计入拒绝、按状态细分成功率。

用法：python load_test.py [total=12] [topic_id] [wave=4]
指标口径：到达终态率 ≠ 业务成功率——FAILED 单列。
"""
import json
import statistics
import sys
import time
import urllib.error
import urllib.request

API = "http://localhost:8080"
TOTAL = int(sys.argv[1]) if len(sys.argv) > 1 else 12
TOPIC = sys.argv[2]
WAVE = int(sys.argv[3]) if len(sys.argv) > 3 else 4
TERMINAL = ("COMPLETED", "PARTIAL_COMPLETED", "FAILED", "CANCELLED")


def post(url, body):
    r = urllib.request.urlopen(urllib.request.Request(url, data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"}), timeout=60)
    return json.loads(r.read().decode())


def get(url):
    return json.loads(urllib.request.urlopen(url, timeout=60).read().decode())


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p / 100))] if xs else 0


submit_lat, submits, rejected = [], {}, 0
durations_seen = []
status_hist = {}
t0 = time.time()


def submit_one(i):
    ts = time.time()
    try:
        d = post(f"{API}/api/rp/research", {"query": f"压测任务 {i}（stub）", "topic_id": TOPIC,
                                            "client_request_id": f"load-{t0}-{i}"})
        submit_lat.append((time.time() - ts) * 1000)
        submits[d["run_id"]] = {"submitted": time.time(), "status": d.get("status")}
    except urllib.error.HTTPError as e:
        if e.code == 429:
            return False
        print(f"提交失败 HTTP {e.code}（非 429，脚本终止）")
        raise
    return True


# 分波：每波 WAVE 个并发，整波到终态后进下一波（真实并发档位）
i = 0
wave_no = 0
while i < TOTAL:
    wave_no += 1
    wave_ids = []
    for k in range(min(WAVE, TOTAL - i)):
        if submit_one(i):
            wave_ids.append(list(submits)[-1])
        i += 1
    # 等本波全部到终态
    wdeadline = time.time() + 600
    while wave_ids and time.time() < wdeadline:
        time.sleep(2)
        for rid in list(wave_ids):
            st = get(f"{API}/api/rp/research/{rid}").get("status")
            if st in TERMINAL:
                durations_seen.append(time.time() - submits[rid]["submitted"])
                wave_ids.remove(rid)

print(f"提交 {TOTAL - rejected}/{TOTAL}（429 拒绝 {rejected}）：P50 {pct(submit_lat,50):.0f}ms P95 {pct(submit_lat,95):.0f}ms")

durations = durations_seen
done = len(durations)

print(f"到达终态 {done}/{TOTAL - rejected}")
print(f"状态细分: {status_hist}")
completed = status_hist.get("COMPLETED", 0)
total_terminal = sum(status_hist.values())
print(f"业务成功率（COMPLETED/终态）: {completed}/{total_terminal} = {completed and completed/total_terminal*100:.0f}%")
if durations:
    print(f"完成时间 P50 {pct(durations,50):.0f}s P95 {pct(durations,95):.0f}s")
metrics = urllib.request.urlopen(f"{API}/actuator/prometheus", timeout=10).read().decode()
for line in metrics.splitlines():
    if line.startswith(("rp_active_runs", "rp_queued_runs", "rp_admission_rejected", "rp_llm_tokens_total")):
        print(line)
