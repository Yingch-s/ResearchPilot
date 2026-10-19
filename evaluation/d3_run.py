"""D3 · 端到端研究评测（断点续跑）。

12 任务（PROTOCOL §3.2）× 3 配置（B0 / Basic RAG / Full）× 3 轮。

- B0:        无知识库，DeepSeek 直接回答（通用知识）
- Basic RAG: 产品 /api/rp/retrieve 单轮 Top-5 混合检索 → 拼接进 prompt 回答
             （不走双层验证 / 矩阵 / 多步骤）
- Full:      产品 /api/rp/research 深度研究全流程（auto_accept_plan，现网配置）

记录：最终状态、耗时、token（basic/b0 来自 API usage；full 来自 run events 的
TOKEN/LLM_CALL_COMPLETED 事件）、答案全文。

断点：evaluation/results/d3_e2e/state.json；逐条追加 runs.jsonl。
"""
import io
import json
import os
import time
import urllib.request

API = 'http://localhost:8080'
SIDECAR = 'http://localhost:8100'
TOPIC = 'e22e83f7-77b4-47fa-aa12-ce8a1ef203b8'  # Evaluation · Dense Retrieval Family
OUT = 'evaluation/results/d3_e2e'
STATE = f'{OUT}/state.json'

TASKS = [
    {'id': 'A1', 'type': 'A', 'query': 'DPR、ColBERT、ANCE、RocketQA 四篇论文各自提出的检索/建模核心方法是什么？'},
    {'id': 'A2', 'type': 'A', 'query': '这四篇论文（DPR、ColBERT、ANCE、RocketQA）分别使用了什么训练数据，标注需求有何不同？'},
    {'id': 'A3', 'type': 'A', 'query': 'DPR、ColBERT、ANCE、RocketQA 各自的负样本（hard negative）策略是什么？'},
    {'id': 'A4', 'type': 'A', 'query': 'DPR、ColBERT、ANCE、RocketQA 各自的索引结构与检索效率特征是什么？'},
    {'id': 'B1', 'type': 'B', 'query': '对比 DPR、ColBERT、ANCE、RocketQA 的训练配置全貌：训练数据、负样本策略、蒸馏/数据增强。'},
    {'id': 'B2', 'type': 'B', 'query': 'DPR、ColBERT、ANCE、RocketQA 各自报告了怎样的资源需求（算力、索引体积的定性描述）？'},
    {'id': 'C1', 'type': 'C', 'query': '梳理从 DPR 到 RocketQA 负样本策略的演进主线，覆盖全部四篇论文。'},
    {'id': 'D1', 'type': 'D', 'query': 'DPR、ColBERT、ANCE、RocketQA 各论文分别使用什么缓存/消息队列框架部署在线服务？'},
    {'id': 'D2', 'type': 'D', 'query': '这四篇论文的实验使用的操作系统和数据库软件是什么？'},
    {'id': 'D3', 'type': 'D', 'query': '四篇论文各自的通讯作者所属大学是哪所、建校年份是什么？'},
    {'id': 'E1', 'type': 'E', 'query': 'RocketQA 的去噪（denoising）策略在论文的哪一部分？具体做了什么？'},
    {'id': 'E2', 'type': 'E', 'query': 'ANCE 如何刷新全局难负例所用的 ANN 索引？'},
]

CONFIGS = ['b0', 'basic', 'full']
ROUNDS = 3

ANSWER_PROMPT = """你是研究助理。请回答以下研究问题。

%s

要求：
- 尽量覆盖问题涉及的每个对象（如多篇论文逐一说明）
- 明确区分"资料中有依据的结论"与"你的推测"
- 资料中没有的信息，直接说明未找到，不要编造
- 用中文回答"""


def log(msg):
    line = f'{time.strftime("%m-%d %H:%M:%S")} {msg}'
    print(line, flush=True)
    with io.open(f'{OUT}/run.log', 'a', encoding='utf-8') as f:
        f.write(line + '\n')


import http.cookiejar

# P0-A：on 模式鉴权 —— 环境变量 RP_EVAL_USER/RP_EVAL_PASSWORD 提供评测账号
_jar = http.cookiejar.CookieJar()
_opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(_jar))
_authed = False


def _ensure_auth():
    global _authed
    if _authed:
        return
    user = os.environ.get('RP_EVAL_USER', 'jinkai')
    pw = os.environ.get('RP_EVAL_PASSWORD', '')
    if pw:
        _opener.open(urllib.request.Request(f'{API}/api/rp/auth/login',
            data=json.dumps({'username': user, 'password': pw}).encode(),
            headers={'Content-Type': 'application/json'}), timeout=30).read()
    _authed = True


def post_json(url, body, timeout=300):
    _ensure_auth()
    r = _opener.open(urllib.request.Request(url, data=json.dumps(body).encode(),
        headers={'Content-Type': 'application/json'}), timeout=timeout)
    return json.loads(r.read().decode('utf-8'))


def get_json(url, timeout=120):
    _ensure_auth()
    r = _opener.open(url, timeout=timeout)
    return json.loads(r.read().decode('utf-8'))


def deepseek(prompt, max_tokens=2000):
    """直接调 DeepSeek；返回 (text, usage dict)。"""
    key = os.environ.get('LLM_API_KEY', '')
    r = urllib.request.urlopen(urllib.request.Request(
        'https://api.deepseek.com/chat/completions',
        data=json.dumps({'model': 'deepseek-chat', 'temperature': 0.1,
                         'messages': [{'role': 'user', 'content': prompt}],
                         'max_tokens': max_tokens}).encode(),
        headers={'Content-Type': 'application/json', 'Authorization': f'Bearer {key}'}), timeout=300)
    d = json.loads(r.read().decode('utf-8'))
    return d['choices'][0]['message']['content'], d.get('usage', {})


def run_b0(task):
    t0 = time.time()
    text, usage = deepseek(f'你是研究助理。请回答以下研究问题（中文）：\n\n{task["query"]}\n\n'
                           f'- 尽量覆盖问题涉及的每个对象\n- 没有把握的信息明确说明不确定，不要编造')
    return {'answer': text, 'llm_usage': usage, 'duration_s': round(time.time() - t0, 1)}


def run_basic(task):
    t0 = time.time()
    d = post_json(f'{API}/api/rp/retrieve',
                  {'topic_id': TOPIC, 'query': task['query'], 'top_k': 5,
                   'channels': ['bm25', 'dense'], 'with_parents': False})
    cands = d.get('candidates', [])
    ctx = '\n\n---\n\n'.join(
        f'[片段{i+1}] {c.get("paperId", "")} · {c.get("sectionPath", "")}\n{c.get("text", "")[:1500]}'
        for i, c in enumerate(cands))
    prompt = (ANSWER_PROMPT % task['query']) + f'\n\n以下是从论文库检索到的资料片段：\n\n{ctx}'
    text, usage = deepseek(prompt)
    return {'answer': text, 'llm_usage': usage, 'n_chunks': len(cands),
            'duration_s': round(time.time() - t0, 1)}


def run_full(task, round_no):
    t0 = time.time()
    d = post_json(f'{API}/api/rp/research',
                  {'query': task['query'], 'topic_id': TOPIC, 'auto_accept_plan': True,
                   'client_request_id': f'd3-{task["id"]}-r{round_no}-{int(t0)}'})
    run_id = d['run_id']
    log(f'  full run {run_id} created')
    # 轮询至终态（上限 1300s，略超全局 deadline）
    terminal = ('COMPLETED', 'PARTIAL_COMPLETED', 'FAILED', 'CANCELLED')
    status = None
    while time.time() - t0 < 1300:
        time.sleep(15)
        try:
            s = get_json(f'{API}/api/rp/research/{run_id}')
            status = s.get('status')
            if status in terminal:
                break
        except Exception as e:
            log(f'  poll error: {str(e)[:80]}')
    # 回收 token 事件 + 报告
    tokens = None
    try:
        ev = get_json(f'{API}/api/rp/research/{run_id}/events?after=0')
        items = ev if isinstance(ev, list) else ev.get('events', [])
        pt = sum(e.get('prompt_tokens') or 0 for e in items if e.get('event') == 'LLM_CALL_COMPLETED')
        ct = sum(e.get('completion_tokens') or 0 for e in items if e.get('event') == 'LLM_CALL_COMPLETED')
        if pt or ct:
            tokens = {'prompt': pt, 'completion': ct}
    except Exception as e:
        log(f'  events error: {str(e)[:80]}')
    report = None
    try:
        r = get_json(f'{API}/api/rp/research/{run_id}/report')
        report = r.get('report')
    except Exception:
        pass
    return {'run_id': run_id, 'status': status or 'TIMEOUT', 'answer': report or '',
            'llm_usage': tokens, 'duration_s': round(time.time() - t0, 1)}


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(f'{OUT}/outputs', exist_ok=True)
    state = {'done': []}
    if os.path.exists(STATE):
        state = json.load(io.open(STATE, encoding='utf-8'))
    plan = [(cfg, t, r) for cfg in CONFIGS for r in range(1, ROUNDS + 1) for t in TASKS]
    # 顺序：配置外层 → 轮次 → 任务（同配置连续跑，减少上下文切换）
    pending = [x for x in plan if f'{x[0]}|{x[1]["id"]}|{x[2]}' not in state['done']]
    log(f'计划 {len(plan)} 次，已完成 {len(plan) - len(pending)}，待跑 {len(pending)}')
    started = time.time()

    import threading
    from concurrent.futures import ThreadPoolExecutor
    lock = threading.Lock()
    submit_lock = threading.Lock()
    last_submit = [0.0]

    def throttled_submit():
        # P1-D 限速器 5 次/分钟 —— 批量评测需节流到 ~4.6 次/分钟
        with submit_lock:
            wait = 13 - (time.time() - last_submit[0])
            if wait > 0:
                time.sleep(wait)
            last_submit[0] = time.time()

    def run_one(cfg, task, rnd):
        key = f'{cfg}|{task["id"]}|{rnd}'
        log(f'== {cfg} · {task["id"]} · round {rnd} ==')
        if cfg == 'full':
            throttled_submit()
        try:
            if cfg == 'b0':
                res = run_b0(task)
            elif cfg == 'basic':
                res = run_basic(task)
            else:
                res = run_full(task, rnd)
        except Exception as e:
            res = {'error': str(e)[:300], 'duration_s': 0}
            log(f'  错误: {str(e)[:150]}')
        rec = {'key': key, 'config': cfg, 'task': task['id'], 'type': task['type'],
               'round': rnd, 'query': task['query'], **res}
        with lock:
            with io.open(f'{OUT}/runs.jsonl', 'a', encoding='utf-8') as f:
                f.write(json.dumps(rec, ensure_ascii=False) + '\n')
            # 答案落盘（评分用，免得读 jsonl 里的长文本）
            ans = rec.get('answer') or ''
            if ans:
                ext = 'md' if cfg == 'full' else 'txt'
                with io.open(f'{OUT}/outputs/{cfg}_{task["id"]}_r{rnd}.{ext}', 'w', encoding='utf-8') as f:
                    f.write(f'# {task["id"]} · {cfg} · round {rnd}\n\n{task["query"]}\n\n---\n\n{ans}')
            state['done'].append(key)
            with io.open(STATE, 'w', encoding='utf-8') as f:
                json.dump(state, f)
            log(f'  完成 {len(state["done"])}/{len(plan)} 用时 {time.time() - started:.0f}s')

    with ThreadPoolExecutor(max_workers=3) as pool:
        list(pool.map(lambda j: run_one(*j), pending))
    log('D3 全部执行完成。评分前等待 GT 核验（PROTOCOL §3.3）。')


if __name__ == '__main__':
    main()
