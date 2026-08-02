import json, time, urllib.request, statistics
from concurrent.futures import ThreadPoolExecutor



BASE = "http://localhost:8077"
TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjozLCJsb2dpblZlciI6IjI4MmI4ZjZjLWQzZjQtNGUxZC04OTRkLThiODNkN2RkMTNmZSIsInN1YiI6IjEiLCJpYXQiOjE3ODU2ODM2NjksImV4cCI6MTc4NjI4ODQ2OX0.xQre9BEAAWIXFv6VG-PK1MNBIA69zQE9hkiKELw_vtI"

def post(_):
    data = json.dumps({"account": "paul", "password": "327510"}).encode()
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



CONCURRENCY = 50
TOTAL = 500
start = time.perf_counter()
with ThreadPoolExecutor(max_workers=CONCURRENCY) as ex:
    results = list(ex.map(post, range(TOTAL)))
wall = time.perf_counter() - start

ok = [t for t, s in results if s]
total = sum(t for t, _ in results)
errors = TOTAL - len(ok)
print(f"总数={TOTAL} 成功={len(ok)} 失败={errors}")
print(f"QPS={TOTAL/wall:.1f}")
print(f"平均={statistics.mean(ok)*1000:.1f}ms P95={ok[int(len(ok)*0.95)-1]*1000:.1f}ms")
ok.sort()
print(f"P50={ok[len(ok)//2]*1000:.1f}ms P95={ok[int(len(ok)*0.95)-1]*1000:.1f}ms")