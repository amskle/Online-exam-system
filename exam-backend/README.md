# 在线考试系统 · 后端服务（exam-backend）

基于 **Spring Boot 3.5 / Java 21** 的在线考试系统后端，提供账号与权限、题库管理、试卷组卷、在线考试、自动判分、错题集与统计等 REST API。

## 项目定位

本目录（`exam-backend`）是「在线考试系统 + 智能学习伙伴」的三大服务之一：

| 服务 | 技术栈 | 端口 | 职责 |
|---|---|---|---|
| **exam-backend**（本目录） | Spring Boot 3.5 · Java 21 | `8077` | 用户权限、题库、试卷组卷、在线考试、判分、错题集、统计 |
| exam-frontend | Vue 3 · TypeScript · Vite | `8076` | 管理员后台 / 学生考试端 / 悬浮 AI 助手 |
| ai-tutor | FastAPI · LangGraph · ChromaDB | `8080` | 知识库 RAG 出题助手与 Socratic 答疑 |

生产环境下由 Nginx 将 `/api/*` 转发到本服务；`ai-tutor` 也会以调用者的身份回访本服务的接口。整套系统的架构概览与一键部署请见仓库根目录的 [README.md](../README.md)、[docker-compose.yml](../docker-compose.yml) 与 [.env.docker.example](../.env.docker.example)。

## 功能特性

**账号与权限**
- 注册（公开注册仅创建学生账号，教师/管理员由管理员创建）、登录、登出、修改密码/资料、头像上传
- JWT 认证：支持 `Authorization: Bearer <token>` 请求头或 `exam_token` HttpOnly Cookie 两种方式
- 邮箱验证码：QQ SMTP 发送，Redis 存储与限流（5 分钟有效、60 秒发送冷却、每日 10 次上限）；可信设备 7 天内免验证码
- 顶号检测：基于 Redis 登录版本号，改密/登出后旧 Token 立即失效
- 角色模型：`1` 学生、`2` 教师、`3` 管理员，`@Auth` 注解 + `JwtInterceptor` 双重校验

**题库与试卷**
- 题型：单选、多选、判断、主观；支持科目与难度分类
- 试卷：手动选题、自动组卷（按题型/数量配置抽题）、考试时长与总分、考试次数上限、自动阅卷开关
- 首次启动自动导入 408 计算机学科真题题库（2009–2021 年，共 559 道）

**在线考试**
- 学生端流程：试卷列表/详情 → 开考 → 草稿自动保存与恢复 → 切屏告警记录 → 交卷
- 判分：客观题自动判分；主观题由教师/管理员批改
- 重考：覆盖上次记录并累计考试次数，达到上限后禁止进入

**考试记录与错题集**
- 成绩与答题明细查询；错题自动收录（客观题答错），支持标记掌握与删除
- 同一用户同一题只保留一条错题记录，重复答错累计次数

**其他**
- 统计仪表盘：用户/试卷/记录/题目/科目统计、趋势、成绩分布与学生排行
- 文件上传（≤ 2MB，本地 `file/` 目录）、`/health` 健康检查、Swagger UI 在线接口文档

## 技术栈

| 类别 | 技术 |
|---|---|
| 语言 / 框架 | Java 21 · Spring Boot 3.5.16（启用虚拟线程 `spring.threads.virtual`） |
| 持久层 | MyBatis-Plus 3.5.7 · MySQL 8.x（`mysql-connector-j`）· HikariCP（上限 50 连接） |
| 缓存 | Redis（`spring-boot-starter-data-redis`，Lettuce + 连接池） |
| 鉴权 | JJWT 0.11.5（JWT 签发/校验）· `spring-security-crypto`（BCrypt 密码散列） |
| 邮件 | `spring-boot-starter-mail`（QQ SMTP，465/SSL） |
| 接口文档 | SpringDoc OpenAPI 2.8.17（Swagger UI） |
| 配置 | `spring-dotenv` 4.0.0（加载 `.env`）· Jakarta Validation |
| 其他 | Lombok（简化样板代码） |
| 测试与质量 | JUnit（`spring-boot-starter-test`）· JaCoCo 0.8.12 覆盖率 |
| 构建 / 部署 | Maven（含 `mvnw` Wrapper）· Docker 多阶段构建 |

## 目录结构

```text
exam-backend/
├── pom.xml                    # Maven 构建与依赖定义
├── mvnw / mvnw.cmd            # Maven Wrapper（无需本地安装 Maven）
├── Dockerfile                 # 多阶段构建（Maven+JDK21 → JRE21，暴露 8077）
├── settings.xml               # 构建用 Maven 镜像配置（阿里云，供 Docker 构建使用）
├── exam.sql                   # 数据库完整快照（9 张表结构 + 少量演示数据，可选导入）
├── src/main/java/com/example/onlineexamsystem/
│   ├── annotation/Auth.java             # 接口权限注解
│   ├── common/exception/                # 业务/校验异常与全局异常处理
│   ├── config/                          # Web/CORS/MyBatis-Plus/密码/OpenAPI/数据库迁移等
│   ├── controller/                      # REST 接口（12 个控制器）
│   ├── interceptor/JwtInterceptor.java  # JWT 鉴权拦截器
│   ├── mapper/                          # MyBatis-Plus BaseMapper（无 XML）
│   ├── pojo/                            # entity / dto / vo / enums / api（统一响应 Result）
│   ├── service/ + service/impl/         # 业务逻辑层
│   └── utils/                           # JwtUtil / RedisUtil / EmailUtil / UserContext
├── src/main/resources/
│   ├── application.yml           # 运行时配置（含默认值与环境变量占位符）
│   ├── application.example       # 配置模板参考
│   ├── schema-admin.sql          # 建表脚本（应用启动时自动执行，幂等）
│   ├── data-408.sql              # 408 真题种子数据（首次启动自动导入）
│   ├── data-408-report.json      # 408 题库导入审计报告
│   └── schema-update-20260707.sql# 历史增量迁移脚本（效果已并入 DatabaseMigrationRunner）
├── src/test/java/                # 单元测试（另有默认禁用的手工压力测试）
└── scripts/                      # 压测与数据准备脚本（见下文「测试与压测」）
```

## 环境依赖

| 依赖 | 版本要求 | 说明 |
|---|---|---|
| JDK | **21+** | `pom.xml` 中 `java.version=21` |
| MySQL | **8.0+** | 默认连接 `localhost:3306/exam` |
| Redis | **7.x** | 默认连接 `localhost:6379` |
| Maven | 3.8+ | 也可直接使用仓库自带的 `mvnw` / `mvnw.cmd` |
| Docker | 可选 | 构建镜像或整套系统编排 |
| Python + JMeter 5.6.3 | 可选 | 仅运行 `scripts/` 压测脚本时需要 |

## 快速开始

### 1. 初始化数据库

创建空数据库即可，表结构与种子数据由应用启动时自动初始化：

```sql
CREATE DATABASE IF NOT EXISTS exam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

首次启动时自动初始化顺序如下（由 `schema-admin.sql` + `DatabaseMigrationRunner` 完成）：

1. 执行 `schema-admin.sql` 幂等建表（`user`、`subject`、`question`、`exam_paper`、`exam_paper_question`、`exam_record`、`exam_record_answer`、`wrong_question` 等）；
2. 增量迁移：自动补齐缺失的列与索引（如 `wrong_question(user_id, question_id)` 唯一索引）；
3. 创建初始管理员（账号固定为 `admin`，密码取 `ADMIN_INITIAL_PASSWORD`，仅当库中不存在管理员时执行）；
4. 首次导入 408 真题种子数据（`data-408.sql`，559 道，通过 `data_seed_log` 表防重，不会重复导入）。

> 可选：如需一份带少量演示数据的完整快照，可手动导入 `exam.sql`（`mysql -u root -p exam < exam.sql`）；之后再启动应用，上述迁移与种子防重逻辑同样适用。

### 2. 配置环境变量

敏感配置通过 `.env` 文件注入（由 `spring-dotenv` 加载；该文件已被 `.gitignore` 忽略，不会被提交），也可使用同名系统环境变量。参考示例：

```dotenv
# 数据库与缓存
DB_PASSWORD=your-db-password
REDIS_PASSWORD=your-redis-password

# JWT 签名密钥（必填，生产环境请使用足够长的随机字符串；需与 ai-tutor 保持一致）
JWT_SECRET=replace-with-a-long-random-secret

# 初始管理员（首次启动且库中无管理员时必须提供，至少 8 位）
ADMIN_INITIAL_PASSWORD=change-me-admin
ADMIN_INITIAL_EMAIL=admin@example.com

# 邮件验证码（QQ 邮箱 SMTP 授权码；不配置则验证码相关功能不可用）
MAIL_USERNAME=your_account@qq.com
MAIL_AUTH_CODE=your-smtp-auth-code
```

### 3. 启动应用

```bash
# Windows
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

启动后验证：

- 健康检查：<http://localhost:8077/health>
- Swagger UI：<http://localhost:8077/swagger-ui.html>

### 4. 打包运行（可选）

```bash
mvnw.cmd clean package -DskipTests
java -jar target/Online-exam-system-0.0.1-SNAPSHOT.jar
```

### 5. Docker 方式（可选）

```bash
docker build -t exam-backend .
docker run -d --name exam-backend -p 8077:8077 \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://host.docker.internal:3306/exam?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  -e DB_PASSWORD=your-db-password \
  -e JWT_SECRET=replace-with-a-long-random-secret \
  -e ADMIN_INITIAL_PASSWORD=change-me-admin \
  -v exam-backend-files:/app/file \
  exam-backend
```

说明：镜像为多阶段构建（构建阶段使用阿里云 Maven 镜像加速），时区为 `Asia/Shanghai`，上传文件目录为 `/app/file`（建议以卷挂载持久化），容器内需能访问外部 MySQL/Redis。整套系统（MySQL + Redis + 后端 + 前端 + AI 服务 + Nginx）的一键编排见仓库根目录 `../docker-compose.yml`。

## 关键配置项

配置定义于 `src/main/resources/application.yml`，敏感项通过环境变量覆盖：

| 配置项 | 环境变量 | 默认值 | 说明 |
|---|---|---|---|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/exam?...` | 数据库连接串 |
| `spring.datasource.username` | `DB_USERNAME` | `root` | 数据库账号 |
| `spring.datasource.password` | `DB_PASSWORD` | 无（必填） | 数据库密码 |
| `spring.data.redis.host` / `port` | `SPRING_DATA_REDIS_HOST` | `localhost:6379` | 密码使用 `REDIS_PASSWORD`（默认空） |
| `jwt.secret` | `JWT_SECRET` | 无（必填） | JWT 签名密钥，需与 ai-tutor 一致 |
| `admin.initial-password` | `ADMIN_INITIAL_PASSWORD` | 无（首次启动必填） | 初始管理员密码，至少 8 位 |
| `admin.initial-email` | `ADMIN_INITIAL_EMAIL` | `admin@example.com` | 初始管理员邮箱 |
| `spring.mail.username` / `password` | `MAIL_USERNAME` / `MAIL_AUTH_CODE` | 见 `application.yml` | QQ SMTP 授权码 |
| `app.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | `http://localhost:8076,http://localhost:8088` | 允许跨域的前端来源 |
| `server.tomcat.accept-count` | `TOMCAT_ACCEPT_COUNT` | `2048` | 突发连接等待队列 |
| `spring.threads.virtual.enabled` | `VIRTUAL_THREADS_ENABLED` | `true` | Java 21 虚拟线程开关 |
| `file.upload-dir` | — | `file` | 上传文件目录（相对运行目录，Docker 中为 `/app/file`） |
| `auth.*` | — | 见 `application.yml` | 验证码 TTL `5m`、发送冷却 `60s`、每日上限 `10`、可信设备 `7d` |

## 接口与鉴权约定

- **统一响应**：所有接口返回 `Result { code, message, data }`；业务错误由全局异常处理器转为统一结构返回。
- **认证方式**：请求头 `Authorization: Bearer <token>`（优先）或 HttpOnly Cookie `exam_token`（登录/邮箱验证成功后由服务端签发）。
- **权限控制**：通过 `@Auth` 注解声明（方法级优先于类级）；未标注的接口为公开接口，拦截器白名单精确放行 `/user/login` 与 `/user/register`。
  - 无注解：公开；`@Auth`：仅需登录；`@Auth(3)`：仅管理员；`@Auth({2,3})`：教师或管理员；`@Auth(1)`：仅学生。
- **角色值**：`1` 学生、`2` 教师、`3` 管理员。

主要路由前缀：

| 前缀 | 用途 | 权限 |
|---|---|---|
| `/user` | 登录、注册、登出、当前用户、改密/改资料/头像 | 混合（登录/注册公开） |
| `/email` | 邮箱验证码发送与校验 | 公开 |
| `/student` | 试卷列表/详情、开考、草稿保存/恢复、交卷、切屏告警、考试记录、错题集 | 学生 |
| `/question` | 题目管理 | 教师 / 管理员 |
| `/examPaper` | 试卷管理、自动组卷 | 教师 / 管理员 |
| `/examRecord` | 考试记录查询、主观题批改 | 教师 / 管理员 |
| `/subject` | 科目管理 | 教师 / 管理员 |
| `/admin/users` | 用户管理、状态封禁 | 管理员 |
| `/admin/dashboard` | 统计仪表盘 | 管理员 |
| `/files` | 文件上传 | 登录用户 |
| `/health` | 健康检查 | 公开 |

完整的接口定义与在线调试见 Swagger UI：<http://localhost:8077/swagger-ui.html>。

## 测试与压测

### 单元测试

```bash
mvnw.cmd test
```

- 覆盖 JWT 拦截器、用户服务、邮件服务、考试记录服务等 16 个单元测试；
- JaCoCo 覆盖率报告生成于 `target/site/jacoco/index.html`；
- `src/test/java/pressureTest.java` 为手工压力测试，默认 `@Disabled`，不在单元测试/CI 中执行。

### 压测脚本（`scripts/`）

压测需要本机具备 JDK 21、JMeter 5.6.3 与 Python 环境（`pymysql`、`psutil` 等），且会向数据库写入测试数据。**不要在生产数据库执行。**

| 脚本 | 用途 |
|---|---|
| `jmeter_login.jmx` + `run_jmeter_login.py` | 登录接口压测（默认 1000 账户、1 秒启动、3 轮，每轮含 10 次预热） |
| `jmeter_login.jmx` + `run_jmeter_exam.py` | 完整考试流程压测（登录 → 答题 → 定时自动保存 → 同步集中交卷 → 数据核对） |
| `report_exam_loadtest.py` | 为考试压测结果生成可读报告与资源图 |
| `probe_exam_submit_races.py` | 重复交卷 / 保存与交卷竞态探针 |
| `probe_wrong_question_upsert.py` | 跨试卷共享错题并发累计、重考、超时判分回归 |
| `seed_loadtest_accounts.sql` | 创建 `loadtest_0001` ~ `loadtest_1000` 测试账户 |
| `seed_jmeter_accounts.py` | 为测试账户预置可信设备 Cookie，生成 `jmeter_accounts.csv` |
| `start_loadtest_backend.ps1` | 启动压测用后端实例（JDK/Maven 路径可通过参数覆盖） |
| `load_test_submit.py` | 早期提交接口压测脚本（通过环境变量驱动，保留作参考） |
| `generate_408_seed.py` | 生成 408 真题种子 SQL（`data-408.sql` 的来源脚本） |

典型流程：

```powershell
# 1. 准备测试账户（数据库账户不足时先执行 SQL）
mysql -u root -p exam < scripts/seed_loadtest_accounts.sql
python scripts/seed_jmeter_accounts.py --limit 1000   # 生成可信设备 Cookie CSV

# 2. 启动后端（如尚未运行）
powershell -ExecutionPolicy Bypass -File scripts/start_loadtest_backend.ps1

# 3. 登录压测：1000 线程、1 秒启动、3 轮
python scripts/run_jmeter_login.py --threads 1000 --ramp 1 --runs 3

# 4. 完整考试流程压测：1000 用户、20 轮自动保存后集中交卷
python scripts/run_jmeter_exam.py --users 1000 --ramp 20 --cycles 20 --save-interval 30
python scripts/report_exam_loadtest.py scripts/load-test-results/exam-<时间戳>
```

说明：
- `run_jmeter_login.py` 默认使用本机路径 `A:/jdk21` 与 `A:/apache-jmeter-5.6.3`，可用 `--java`、`--jmeter-home` 参数覆盖；
- 压测结果会输出到 `scripts/load-test-results/<时间戳>/`，包含 `summary.json`（验收结果）、`.jtl`（逐条请求）、日志与报告，不会覆盖历史结果；
- 不要并行运行多轮压测（共用同一批账户，且会干扰资源统计）。

### 相关专项文档

| 文档 | 内容 |
|---|---|
| [scripts/JMETER_LOGIN.md](scripts/JMETER_LOGIN.md) | 1000 账户登录压测：配置、复测步骤与结果适用范围 |
| [scripts/JMETER_EXAM.md](scripts/JMETER_EXAM.md) | 完整考试流程与集中交卷压测：场景设计、数据核对与产物说明 |
| [scripts/EXAM_OPTIMIZATION.md](scripts/EXAM_OPTIMIZATION.md) | 交卷链路优化实现与验证口径（技术细节与证据索引） |
| [scripts/load-test-results/EXAM_OPTIMIZATION_RESULT.md](scripts/load-test-results/EXAM_OPTIMIZATION_RESULT.md) | 交卷优化前后对比结果与结论边界 |
| [scripts/load-test-results/LOGIN_VERIFICATION.md](scripts/load-test-results/LOGIN_VERIFICATION.md) | 登录压测验收记录（含排查证据） |

## 常见问题

1. **启动失败，提示 `必须通过 ADMIN_INITIAL_PASSWORD 设置至少8位的新密码`**
   首次启动且库中不存在管理员时，必须在 `.env` 或环境变量中配置 `ADMIN_INITIAL_PASSWORD`（≥ 8 位）。
2. **启动失败，提示 `JWT_SECRET` 占位符无法解析**
   `jwt.secret` 无默认值，必须在 `.env` 或环境变量中配置 `JWT_SECRET`。
3. **登录时要求邮箱验证码 / 验证码邮件发送失败**
   密码校验通过后，无可信设备 Cookie 时会进入邮箱验证码流程；`MAIL_AUTH_CODE` 必须填写 QQ 邮箱的 **SMTP 授权码**（不是邮箱密码）。自动化/压测场景可用 `scripts/seed_jmeter_accounts.py` 预置可信设备跳过验证码。
4. **前端请求被 CORS 拦截**
   将前端来源追加到 `CORS_ALLOWED_ORIGINS`（默认允许 `http://localhost:8076` 与 `http://localhost:8088`）。
5. **文件上传失败**
   上传上限为 2MB（`spring.servlet.multipart`）；上传目录由 `file.upload-dir` 控制（默认 `file/`，Docker 中为 `/app/file`，注意卷权限）。
6. **启动日志提示错题唯一索引迁移失败**
   `wrong_question` 表存在历史重复的 `(user_id, question_id)` 数据；迁移不会自动删除历史记录，需先人工核对处理后再启动（详见 [scripts/EXAM_OPTIMIZATION.md](scripts/EXAM_OPTIMIZATION.md)）。
7. **Redis 未启动会影响什么**
   验证码、可信设备与登录版本校验依赖 Redis；连接超时配置为 3s，避免 Redis 不可用时请求长时间挂起。本地开发建议先启动 Redis。
8. **压测时提示 8077 端口已被占用**
   `scripts/start_loadtest_backend.ps1` 检测到端口占用会拒绝启动，请先停止已有后端实例。

## 许可证与贡献

- 仓库当前未包含独立的 `LICENSE` 文件；OpenAPI 文档元信息中标注为 MIT。如需正式开源授权，请先补充许可证文件。
- 参与开发建议：
  - 提交前运行 `mvnw.cmd test` 确保测试通过；
  - 不要提交 `.env`、`.env.docker` 等包含凭据的文件（已在 `.gitignore` 中忽略）；
  - 数据库变更请遵循现有模式（`schema-admin.sql` 幂等建表 + `DatabaseMigrationRunner` 增量迁移），不要手工修改线上库结构。
