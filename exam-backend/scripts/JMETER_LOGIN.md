# 1000 账户登录压测

已在 2026-09-09 完成验证：1000 个不同账户，Ramp-up 1 秒，循环 1 次；先执行独立的 10 次预热登录。最终配置重启后连续三轮共 3000 次正式登录，错误数为 0。

## 已保存的配置

- `application.yml` 与 `application.example`：启用 Java 21 虚拟线程，`server.tomcat.accept-count` 默认 2048。可分别通过 `VIRTUAL_THREADS_ENABLED` 和 `TOMCAT_ACCEPT_COUNT` 覆盖。
- 原 NIO 连接器保持不变；没有保留诊断期间的 NIO2、接收线程优先级或 32 线程调整。
- `seed_loadtest_accounts.sql`：补齐 `loadtest_0001` 到 `loadtest_1000`，已有账户不覆盖。
- `jmeter_accounts.csv`：1000 个账户及对应的可信设备 Cookie。可信设备有效期默认 7 天。
- `jmeter_login.jmx`：独立预热、正式测试、按线程组独立读取 CSV；检查 HTTP 200、业务 code 200、AUTHENTICATED、非空 token 和对应 userId。
- 正式线程组里的 Summary Report 只统计正式登录；View Results Tree 默认禁用，避免压测时保存大量响应。

## 复测

在后端目录执行：

```powershell
python scripts/run_jmeter_login.py --threads 1000 --ramp 1 --runs 3
```

脚本使用本机 JDK 21 与 JMeter 5.6.3，在无界面模式下运行，HTTP 重试次数明确设为 0。开始前检查健康接口和账户数量；结果必须包含每轮 1000 个不同账户的正式请求，缺少请求或任意连接/业务错误都会退出失败。预热结果单独统计，不纳入正式响应时间和吞吐量。

每次运行生成新的 `scripts/load-test-results/login-时间/`，其中 `summary.json` 是验收结果，`.jtl` 是逐条请求数据，`.log` 是运行日志。不会覆盖以前的结果。

在 JMeter 界面中使用时，重新打开 `scripts/jmeter_login.jmx`，清空旧结果，并启动整个 Test Plan，让 setUp 线程组先完成。默认参数为 1000 线程、1 秒启动、1 次循环。不要只运行正式线程组而跳过预热。CSV 不循环读取，增加线程数或循环次数前需调整数据准备方式。

## 启动后端与更新账户

本次操作结束时后端已经在后台运行，无需再启动一份。以后停止现有实例后，可执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start_loadtest_backend.ps1
```

启动脚本使用 JDK 21，构建后按正常 JVM 模式运行，不添加 IDE 的 `-XX:TieredStopAtLevel=1`，会输出 PID 和日志目录。8077 已占用时会停止操作，避免重复启动。

可信设备过期或 Redis 数据清空后，重新生成：

```powershell
python scripts/seed_jmeter_accounts.py --limit 1000
```

该 Python 脚本只读取已有数据库账户。数据库账户不足时，先执行 `seed_loadtest_accounts.sql`。凭据仍从项目 `.env` 读取。

## 结果的适用范围

这次验证覆盖预热后的短时登录突发，不等于长期维持 1000 个在途请求，也不等于完整考试流程的容量结论。正式登录 P95 仍约 3.6–4.2 秒；零错误与响应时间达标是不同指标。

Windows 临时端口对照实验中，普通 backlog=2048 实际排队到约 200 个连接；因此不能把配置数字直接当作系统实际队列容量。增大队列、预热、正常 JVM 模式和虚拟线程组合通过了本机复测，单独增大队列或更换 NIO2 未解决问题。

参考：[Tomcat HTTP 连接器](https://tomcat.apache.org/tomcat-10.1-doc/config/http.html)、[Windows listen](https://learn.microsoft.com/en-us/windows/win32/api/winsock2/nf-winsock2-listen)。
