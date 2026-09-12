import json, os, time, urllib.request, statistics
from concurrent.futures import ThreadPoolExecutor



BASE = "http://localhost:8077"
ACCOUNT = os.getenv("LOAD_TEST_ACCOUNT", "")
PASSWORD = os.getenv("LOAD_TEST_PASSWORD", "")

def post(_):
    data = json.dumps({"account": ACCOUNT, "password": PASSWORD}).encode()
    req = urllib.request.Request(
        BASE + "/user/login", data=data,
        headers={"Content-Type": "application/json"},
    )
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            ok = r.status == 200
    except Exception:
        ok = False
    return time.perf_counter() - t0, ok



if __name__ == "__main__":
    if not ACCOUNT or not PASSWORD:
        raise SystemExit("请先设置 LOAD_TEST_ACCOUNT 和 LOAD_TEST_PASSWORD")
    concurrency = 50
    total_requests = 500
    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as ex:
        results = list(ex.map(post, range(total_requests)))
    wall = time.perf_counter() - start

    ok = sorted(t for t, success in results if success)
    errors = total_requests - len(ok)
    print(f"总数={total_requests} 成功={len(ok)} 失败={errors}")
    print(f"QPS={total_requests / wall:.1f}")
    if ok:
        print(f"平均={statistics.mean(ok) * 1000:.1f}ms")
        print(f"P50={ok[len(ok) // 2] * 1000:.1f}ms P95={ok[int(len(ok) * 0.95) - 1] * 1000:.1f}ms")
