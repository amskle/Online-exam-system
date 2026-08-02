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
"""
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE_URL = "http://localhost:8077"
ACCOUNT_PREFIX = "loadtest_"
PASSWORD = "123456"

# 提交的答案；默认空数组只压提交主链路（删旧答案+更新成绩+写记录）
# 想同时压自动判分和错题写入，就填真实题目的 id 和作答，例如：
# ANSWERS = [{"questionId": 1, "userAnswer": "A"}]
ANSWERS = []


def http(method, path, token=None, body=None, timeout=15):
    url = BASE_URL + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Cookie"] = "exam_token=" + token
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
    _, ok, _, token = http(
        "POST", "/user/login",
        body={"account": account, "password": PASSWORD},
    )
    return token if ok else None


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
    dur, ok, _, _ = http(
        "POST", "/student/examRecords/submit",
        token=token,
        body={"recordId": record_id, "paperId": PAPER_ID, "answers": ANSWERS},
    )
    return dur, ok


def main():
    if len(sys.argv) < 2:
        print("用法: python load_test_submit.py <paperId> [账号数] [并发数]")
        return
    paper_id = int(sys.argv[1])
    count = int(sys.argv[2]) if len(sys.argv) > 2 else 500
    concurrency = int(sys.argv[3]) if len(sys.argv) > 3 else 50

    global PAPER_ID
    PAPER_ID = paper_id
    accounts = [f"{ACCOUNT_PREFIX}{i:04d}" for i in range(1, count + 1)]

    # 1. 登录拿 token
    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        tokens = list(ex.map(login, accounts))
    tokens = [t for t in tokens if t is not None]
    print(f"登录成功: {len(tokens)}/{count} 用时 {time.perf_counter() - t0:.1f}s")
    if len(tokens) < concurrency:
        print("登录成功数过少，请检查 seed SQL 是否已执行、email_verify_time 是否为当前时间")
        return

    # 2. 开始考试拿 recordId
    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        record_ids = list(ex.map(start_exam, tokens))
    records = [(token, rid) for token, rid in zip(tokens, record_ids) if rid is not None]
    print(f"开考成功: {len(records)}/{len(tokens)} 用时 {time.perf_counter() - t0:.1f}s")
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
        print(f"平均={statistics.mean(latencies) * 1000:.1f}ms")
        print(f"P50={latencies[len(latencies) // 2] * 1000:.1f}ms")
        print(f"P95={latencies[int(len(latencies) * 0.95) - 1] * 1000:.1f}ms")
        print(f"P99={latencies[int(len(latencies) * 0.99) - 1] * 1000:.1f}ms")


if __name__ == "__main__":
    main()
