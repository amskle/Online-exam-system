# -*- coding: utf-8 -*-
"""考试提交接口压测：批量登录 -> 开始考试 -> 并发提交。

用法:
  python load_test_submit.py <paperId> [账号数] [并发数]
示例:
  python load_test_submit.py 1 500 50

前置条件:
  1. 已执行 seed_loadtest_accounts.sql 生成 loadtest_0001 ~ loadtest_0500
  2. 对应试卷已发布（status=1），且考试时长足够（提交时间不能超过开始时间+时长）
  3. 后端、MySQL、Redis 已启动

当前登录流程启用可信设备校验，运行前可通过环境变量提供测试数据：
  LOAD_TEST_TRUSTED_COOKIE_MAP  账号到 trusted_device Cookie 的 JSON 映射
  LOAD_TEST_ANSWERS_FILE        提交答案数组 JSON；不设置时提交空答案
  LOAD_TEST_REPORT_PATH         可选的 JSON 结果输出路径
  LOAD_TEST_BASE_URL            可选的后端地址，默认 http://localhost:8077
"""
import json
import math
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

BASE_URL = os.getenv("LOAD_TEST_BASE_URL", "http://localhost:8077").rstrip("/")
ACCOUNT_PREFIX = "loadtest_"
PASSWORD = "123456"

# 提交的答案；默认空数组只压提交主链路（删旧答案+更新成绩+写记录）
# 想同时压自动判分和错题写入，就填真实题目的 id 和作答，例如：
# ANSWERS = [{"questionId": 1, "userAnswer": "A"}]
ANSWERS = []
TRUSTED_COOKIES = {}


def http(method, path, token=None, cookie=None, body=None, timeout=15):
    url = BASE_URL + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    cookies = []
    if token:
        cookies.append("exam_token=" + token)
    if cookie:
        cookies.append(cookie)
    if cookies:
        headers["Cookie"] = "; ".join(cookies)
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            ok = resp.status == 200
            set_cookie = resp.headers.get_all("Set-Cookie") or []
    except urllib.error.HTTPError as e:
        raw = e.read()
        ok = False
        set_cookie = []
    except Exception:
        return time.perf_counter() - t0, False, b"", None
    token_value = None
    for c in set_cookie:
        if c.startswith("exam_token="):
            token_value = c.split(";", 1)[0].split("=", 1)[1]
    return time.perf_counter() - t0, ok, raw, token_value


def login(account):
    _, ok, raw, token = http(
        "POST", "/user/login",
        cookie=TRUSTED_COOKIES.get(account),
        body={"account": account, "password": PASSWORD},
    )
    if ok and token:
        return token, None
    body = raw.decode("utf-8", "ignore")[:150] if raw else ""
    return None, f"{account}: ok={ok} body={body or '无token'}"


def start_exam(token):
    _, ok, raw, _ = http(
        "POST", f"/student/examRecords/start?paperId={PAPER_ID}",
        token=token,
    )
    if not ok:
        return None
    try:
        return json.loads(raw.decode("utf-8"))["data"]["id"]
    except Exception:
        return None


def submit(item):
    token, record_id = item
    dur, ok, raw, _ = http(
        "POST", "/student/examRecords/submit",
        token=token,
        body={"recordId": record_id, "paperId": PAPER_ID, "answers": ANSWERS},
    )
    if ok:
        try:
            ok = json.loads(raw.decode("utf-8")).get("code") == 200
        except Exception:
            ok = False
    return dur, ok


def percentile(sorted_values, ratio):
    if not sorted_values:
        return None
    index = max(0, math.ceil(len(sorted_values) * ratio) - 1)
    return sorted_values[index]


def load_json_env(name, default):
    path = os.getenv(name, "").strip()
    if not path:
        return default
    with Path(path).open("r", encoding="utf-8") as file:
        return json.load(file)


def save_report(report):
    path = os.getenv("LOAD_TEST_REPORT_PATH", "").strip()
    if not path:
        return
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"报告已保存: {target}")


def main():
    if len(sys.argv) < 2:
        print("用法: python load_test_submit.py <paperId> [账号数] [并发数]")
        return
    paper_id = int(sys.argv[1])
    count = int(sys.argv[2]) if len(sys.argv) > 2 else 500
    concurrency = int(sys.argv[3]) if len(sys.argv) > 3 else 50

    global PAPER_ID
    global ANSWERS
    global TRUSTED_COOKIES
    PAPER_ID = paper_id
    ANSWERS = load_json_env("LOAD_TEST_ANSWERS_FILE", ANSWERS)
    TRUSTED_COOKIES = load_json_env("LOAD_TEST_TRUSTED_COOKIE_MAP", TRUSTED_COOKIES)
    accounts = [f"{ACCOUNT_PREFIX}{i:04d}" for i in range(1, count + 1)]

    # 1. 登录拿 token（登录只是准备阶段，用较低并发避免打爆 Redis 连接池）
    login_concurrency = min(concurrency, 16)
    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=login_concurrency) as ex:
        raw_tokens = list(ex.map(login, accounts))
    tokens = [t for t, _ in raw_tokens if t is not None]
    login_seconds = time.perf_counter() - t0
    print(f"登录成功: {len(tokens)}/{count} 用时 {login_seconds:.1f}s")
    failures = [msg for _, msg in raw_tokens if msg]
    if failures:
        print("登录失败样例（前5条）:")
        for msg in failures[:5]:
            print(" ", msg)
    if len(tokens) < concurrency:
        print("登录成功数过少，请先检查 seed SQL 是否已执行、Redis 连接池是否够用")
        return

    # 2. 开始考试拿 recordId
    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        record_ids = list(ex.map(start_exam, tokens))
    records = [(token, rid) for token, rid in zip(tokens, record_ids) if rid is not None]
    start_seconds = time.perf_counter() - t0
    print(f"开考成功: {len(records)}/{len(tokens)} 用时 {start_seconds:.1f}s")
    if len(records) < concurrency:
        print("开考成功数过少，请确认 paperId 有效、试卷已发布且考试时长足够")
        return

    # 3. 并发提交（只统计这一段的耗时）
    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        results = list(ex.map(submit, records))
    wall = time.perf_counter() - t0

    latencies = sorted(d for d, ok in results if ok)
    errors = len(results) - len(latencies)
    print(f"提交: 总数={len(results)} 成功={len(latencies)} 失败={errors}")
    print(f"耗时={wall:.2f}s 真实QPS={len(latencies) / wall:.1f}")
    if latencies:
        mean_ms = statistics.mean(latencies) * 1000
        p50_ms = percentile(latencies, 0.50) * 1000
        p95_ms = percentile(latencies, 0.95) * 1000
        p99_ms = percentile(latencies, 0.99) * 1000
        print(f"平均={mean_ms:.1f}ms")
        print(f"P50={p50_ms:.1f}ms")
        print(f"P95={p95_ms:.1f}ms")
        print(f"P99={p99_ms:.1f}ms")
    else:
        mean_ms = p50_ms = p95_ms = p99_ms = None

    report = {
        "timestamp": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
        "base_url": BASE_URL,
        "paper_id": PAPER_ID,
        "accounts_requested": count,
        "concurrency": concurrency,
        "answers_per_submit": len(ANSWERS),
        "login_success": len(tokens),
        "login_seconds": round(login_seconds, 4),
        "start_success": len(records),
        "start_seconds": round(start_seconds, 4),
        "submit_total": len(results),
        "submit_success": len(latencies),
        "submit_errors": errors,
        "submit_success_rate": round(len(latencies) / len(results), 6) if results else 0,
        "submit_wall_seconds": round(wall, 4),
        "submit_qps": round(len(latencies) / wall, 4),
        "mean_ms": round(mean_ms, 4) if mean_ms is not None else None,
        "p50_ms": round(p50_ms, 4) if p50_ms is not None else None,
        "p95_ms": round(p95_ms, 4) if p95_ms is not None else None,
        "p99_ms": round(p99_ms, 4) if p99_ms is not None else None,
    }
    save_report(report)


if __name__ == "__main__":
    main()
