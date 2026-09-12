# 交卷优化实现与验证口径

## 最终实现

- `application.yml` 与 `application.example` 开启 `rewriteBatchedStatements=true`，让 JDBC 批量插入具备合并执行条件。Connector/J 是否合并以及如何拆分还受语句类型和包大小影响，不能把所有批量 API 都当作一次 SQL。[Connector/J 官方说明](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-performance-extensions.html)
- `StudentExamController` 先在内存完成判分并收集错题；`WrongQuestionServiceImpl` 一次查询本批已有错题，将新增与更新分开处理，分别批量插入和按主键批量更新。每批最多 200 条；本次 10 道错题的全新增或全更新路径分别为一次查询与一次批量写操作，混合路径最多三次。实际网络耗时以压测为准。
- 已有错题通过 SQL `wrong_count = wrong_count + 1` 累计，保留主键、创建时间和原有题目快照，并更新作答、标准答案、最近错误时间，重置掌握状态。
- 同一用户考试写入的加锁顺序统一为用户行、考试记录行、答案与错题。它串行化同一用户在多张试卷上的冲突操作，不串行化不同考生。答案与错题仍与交卷状态处于同一事务中，重复交卷仍被状态校验拒绝。
- 错题表添加 `(user_id, question_id)` 唯一索引。当前本机数据已检查无重复，迁移已执行。其他数据库若有历史重复，新增索引会明确失败，不会自动删除历史数据。
- 认证复用已经验签的 Claims，避免同一 Token 重复解析；成功鉴权日志改为 DEBUG。账户停用、角色变更和 Redis 登录版本仍实时校验。

连接池上限保留 50，没有通过扩大连接池换取本轮结果。连接数不是越大越好，应结合实测吞吐和等待判断。[HikariCP 官方连接池说明](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing)

## 并发修复证据

最初尝试“唯一索引 + 多值 INSERT ON DUPLICATE KEY UPDATE”。新增的共享错题并发回归发现 7 次死锁，InnoDB 报告显示 `wrong_question` 主键索引的 supremum 间隙锁与插入意向锁相互等待。排序输入未能消除此问题，因此最终改为批量查分、插入与主键更新分开执行，并统一用户/考试记录加锁顺序。

试验失败保留在 `load-test-results/exam-upsert-20260909-202540-559895/summary.json` 和 `load-test-results/backend-20260909-202226-002/stdout.log` 中。最终回归结果位于：

- `load-test-results/exam-upsert-20260909-202916-072771/summary.json`：20 个用户，两张共享题目的试卷同时交卷，错题每题恰好累计两次；重考累计三次；超时草稿判分累计四次。成绩、答案、错题次数、原始主键/创建时间、掌握状态均通过检查。
- `load-test-results/exam-races-20260909-202924-219078/summary.json`：20 个用户各两次交卷与一次保存同时竞争，每人一次交卷成功，另一次被拒绝，最终答案和错题记录正确。
- 最终代码的 Maven 测试：16 项通过，原有手动压力测试 1 项跳过；包含 5 项新增认证回归。日志为 `load-test-results/exam-optimization-unit-tests.log`。

## 性能结论的边界

旧报告的 `Slow_queries=0` 不能排除 SQL 耗时，因为本机 `long_query_time=10` 秒。根据池大小 50 和总耗时 4784 ms 推导的“单连接占用 239 ms”没有直接测量支撑；再用该数值回算吞吐相当于重述同一组数据。不能据此宣称连接池已被证实过小，也不能把 MySQL `Threads_running` 当作连接占用率。

最终性能对比应使用同一配置：1000 个不同账户、20 秒启动、每人 50 道客观题、20 轮自动保存、间隔 30 秒、同步交卷、HTTP 重试为零。短测可用来验证峰值重复表现，但应与十分钟长测分开列出。每轮都会新建独立试卷并核对数据库，保留既有考试数据，因此这些是同机、同配置的前后实测，不是随机化实验，也不能精确拆分每项改动的独立贡献。

简历可写“1000 人同步交卷”“每次 50 道答案”“最终通过的若干轮提交零失败”和同配置 P95 降幅。不要写成“所有开发试验均零错误”“支持生产 1000 QPS”或“已证明容量上限为 1000 人”。交卷批次的吞吐是本轮提交数除以批次完成时间，并非持续吞吐上限。
