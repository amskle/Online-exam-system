# 在线考试系统 · Online Exam System

[![CI](https://github.com/amskle/Online-exam-system/actions/workflows/ci.yml/badge.svg)](https://github.com/amskle/Online-exam-system/actions/workflows/ci.yml)

一个融合 **RAG + LLM Agent** 的智能在线考试平台，支持管理员、老师、学生三类角色。核心亮点：知识库驱动的 AI 出题助手、Socratic 引导式 AI 答疑伙伴。

## 文档导航

| 文档 | 内容 |
|---|---|
| [exam-backend/README.md](exam-backend/README.md) | 后端完整文档：配置项、接口路由、测试与压测、常见问题 |
| [ai-tutor/eval/README.md](ai-tutor/eval/README.md) | AI 智能体与 RAG 的 Langfuse 评测指南 |
| [exam-backend/scripts/EXAM_OPTIMIZATION.md](exam-backend/scripts/EXAM_OPTIMIZATION.md) | 交卷链路优化实现与验证口径 |
| [exam-backend/scripts/JMETER_LOGIN.md](exam-backend/scripts/JMETER_LOGIN.md) / [JMETER_EXAM.md](exam-backend/scripts/JMETER_EXAM.md) | 登录压测、完整考试流程压测说明 |
| [docker-compose.yml](docker-compose.yml) / [docker-compose.dev.yml](docker-compose.dev.yml) | 一键部署编排 / 本地开发中间件编排 |
| [.env.docker.example](.env.docker.example) | Docker 部署环境变量模板 |

## 1. 项目简介与功能概览

系统由三个协作的服务组成：**exam-backend**（考试业务与权限）、**exam-frontend**（Web 界面与悬浮 AI 助手）、**ai-tutor**（RAG 出题与答疑智能体）。AI 服务与后端共享同一套 JWT 密钥，AI 以调用者本人的权限访问后端接口。

### 管理员端

- **仪表盘** — 系统统计、趋势图表、成绩分布、题型/科目分布
- **用户管理** — 学生/老师账号管理、状态封禁、权限展示（公开注册仅创建学生账号；教师和管理员账号由管理员创建）
- **科目管理** — 考试科目增删改查
- **题目管理** — 单选/多选/判断/主观题，支持 408 真题导入
- **试卷管理** — 手动选题 + 自动组卷，可配考试次数限制
- **考试记录** — 记录查看、主观题批改

### 老师端

- 题目管理、试卷管理、考试记录管理
- **AI 出题助手** — 知识库上传 / 对话查询 / AI 生成题目

### 学生端

- 试卷列表、在线考试（倒计时 + 答题卡 + 切屏告警记录）
- 考试记录与答题详情、错题集（自动收录客观题错题，可标记掌握/删除）
- **AI 学习伙伴** — 错题答疑、Socratic 引导、流式回复（考试进行中不可用）
- 账号能力：注册（邮箱验证码，QQ SMTP + Redis 限流）、登录、改密/改资料、头像上传

## 2. 架构概览

```
                        ┌───────────────────────────────────────────────┐
                        │      Nginx 统一入口（前端镜像内置）              │
   浏览器 ────────────▶ │  Docker：http://localhost:8088（容器内 :80）    │
                        │  /api/* → exam-backend    /ai/* → ai-tutor     │
                        │  /*（其余路径）→ exam-frontend 静态页（SPA 回退） │
                        └───────┬───────────────┬───────────────┬───────┘
                                ▼               ▼               ▼
                       ┌──────────────┐ ┌──────────────┐ ┌────────────────┐
                       │ exam-backend │ │   ai-tutor   │ │ exam-frontend  │
                       │ Spring Boot  │ │   FastAPI    │ │  Vue 3 + TS    │
                       │    :8077     │ │    :8080     │ │ 构建产物 dist/  │
                       └──┬────────┬──┘ └──┬────────┬──┘ └────────────────┘
                          ▼        ▼       ▼        ▼
                       ┌──────┐ ┌──────┐ ┌───────────────┐ ┌────────────────┐
                       │MySQL │ │Redis │ │ChromaDB（内嵌）│ │LLM / Embedding │
                       │ 8.0  │ │ 7.x  │ │SQLite（会话）  │ │ API（外部调用） │
                       └──────┘ └──────┘ └───────────────┘ └────────────────┘
```

| 组件 | 职责 | 端口 |
|---|---|---|
| exam-backend | 用户权限、题库管理、试卷组卷、在线考试、自动判分、错题集、统计 | 8077（经 `/api/` 对外） |
| ai-tutor | 知识库 RAG 检索 → LangGraph 出题流水线 / 流式答疑对话 | 8080（经 `/ai/` 对外） |
| exam-frontend | 管理员后台 / 学生考试端 / 悬浮 AI 助手（开发态 Vite :8076） | Docker 入口 8088 → 容器 80 |
| MySQL 8.0 | 业务主库（库名 `exam`） | 3306（仅开发编排对外暴露） |
| Redis 7.x | 验证码、可信设备、登录版本号（顶号检测） | 6379（仅开发编排对外暴露） |
| ChromaDB / SQLite | ai-tutor 内嵌使用：向量库存于 `chroma_store/`，会话库存于 `chat_sessions.db` | 无独立端口 |
| LLM / Embedding API | 外部模型服务（默认 DeepSeek 对话 + SiliconFlow BGE 向量） | 外网 HTTPS |

要点说明：

- Nginx 的 `/api/` 代理会去掉前缀转发到后端根路径（后端控制器本身没有 `/api` 前缀）；`/ai/` 原样转发到 AI 服务。
- 仓库根目录的 [nginx.conf](nginx.conf) 是**裸机部署模板**（upstream 指向 `127.0.0.1:8077` / `127.0.0.1:8080`）；Docker 部署使用 [exam-frontend/nginx.conf](exam-frontend/nginx.conf)（upstream 指向容器名）。
- 开发态前端由 Vite 代理 `/api` → `http://localhost:8077`，AI 请求直连 `http://localhost:8080/ai`（两个服务需分别启动）。

## 3. 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.5（3.5.16）· Java 21（启用虚拟线程）· MyBatis-Plus 3.5.7 · JJWT 0.11.5 · BCrypt · Redis（Lettuce） |
| AI 服务 | FastAPI · LangGraph · ChromaDB（内嵌）· DeepSeek API（OpenAI 兼容）· SiliconFlow BGE Embedding · SQLite 会话存储 |
| 前端 | Vue 3 · TypeScript · Vite · Element Plus · Pinia · ECharts · axios |
| 数据 | MySQL 8.0 · Redis 7.x · SQLite（AI 会话）· ChromaDB（向量） |
| 工程化 | Docker Compose · GitHub Actions CI · JaCoCo · SpringDoc OpenAPI (Swagger) · pytest |

## 4. 目录结构

```text
Online-exam-system/
├── exam-backend/              # Spring Boot 后端（:8077）
│   ├── src/                   # Java 源码、schema-admin.sql、data-408.sql 等
│   ├── scripts/               # JMeter 压测与数据脚本、专项文档
│   ├── exam.sql               # 可选数据库快照（建表 + 少量演示数据）
│   ├── pom.xml / mvnw*        # Maven 构建（含 Wrapper）
│   └── Dockerfile
├── exam-frontend/             # Vue 3 前端（开发 :8076；镜像内 Nginx）
│   ├── src/                   # 页面、组件、路由守卫、API 封装
│   ├── nginx.conf             # 容器内 Nginx 配置（/api、/ai、SPA 回退）
│   ├── package.json / vite.config.ts
│   └── Dockerfile             # Node 22 构建 → nginx:alpine 运行
├── ai-tutor/                  # FastAPI AI 服务（:8080）
│   ├── agents/                # LangGraph 教师/学生智能体
│   ├── rag/                   # 文档加载、格式感知分块、混合检索、Query 改写
│   ├── routers/               # /ai/teacher/*、/ai/student/* 接口
│   ├── utils/                 # JWT 校验、后端桥接、会话存储、可观测性
│   ├── eval/                  # Langfuse 评测 CLI 与数据集（见 eval/README.md）
│   ├── tests/                 # pytest 用例
│   ├── main.py / requirements.txt
│   └── Dockerfile
├── .github/workflows/ci.yml   # GitHub Actions CI
├── docker-compose.yml         # 一键编排（MySQL + Redis + 三服务）
├── docker-compose.dev.yml     # 本地开发中间件（仅 MySQL + Redis）
├── .env.docker.example        # Docker 部署环境变量模板
└── nginx.conf                 # 裸机 Nginx 反代模板
```

## 5. 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| Docker Engine + Compose v2 | 20.10+ / v2 | 一键部署（方式一）必需 |
| JDK | **21** | 后端编译运行（`pom.xml` 中 `java.version=21`） |
| Maven | 3.8+ | 也可直接使用仓库自带 `mvnw` / `mvnw.cmd` |
| Node.js | **20+**（CI 使用 20，前端镜像使用 22） | 前端开发与构建 |
| Python | **3.12** | ai-tutor（`requires-python >= 3.12`，镜像同为 3.12） |
| MySQL | **8.0+** | 本地开发需要；Docker 部署由容器提供 |
| Redis | **7.x** | 本地开发需要；Docker 部署由容器提供 |
| Nginx | 任意稳定版 | 仅裸机部署时需要 |

外部服务（需要自行申请 API Key）：

- **LLM**：默认 `https://api.deepseek.com/v1`，模型 `deepseek-chat`（OpenAI 兼容接口）。
- **Embedding**：默认 `https://api.siliconflow.cn/v1`，模型 `BAAI/bge-large-zh-v1.5`（DeepSeek 不提供 Embedding，需单独配置；也可指向其他兼容服务）。

## 6. 快速开始（Docker 一键部署，推荐）

### 6.1 步骤

1. 准备环境变量文件并填写必填项（见 [.env.docker.example](.env.docker.example) 与第 8 节表格）：

   ```bash
   cp .env.docker.example .env.docker
   # Windows PowerShell: Copy-Item .env.docker.example .env.docker
   ```

2. 构建并启动全部服务：

   ```bash
   docker compose --env-file .env.docker up -d --build
   ```

3. 访问系统：**http://localhost:8088**，使用初始管理员账号 `admin` 与 `.env.docker` 中设置的 `ADMIN_INITIAL_PASSWORD` 登录。

### 6.2 首次启动自动完成的事项

- MySQL 创建数据库 `exam` 及应用账号（`DB_USERNAME`，默认 `exam_app`，非 root）；
- 后端幂等建表（`schema-admin.sql`）+ 增量迁移，创建初始管理员（仅在库中无管理员时）；
- 首次导入 408 计算机学科真题题库（2009–2021 年，共 **559** 道，通过 `data_seed_log` 防重，不会重复导入）。

### 6.3 常用运维命令

```bash
docker compose --env-file .env.docker ps            # 查看状态（含健康检查）
docker compose --env-file .env.docker logs -f backend   # 查看某个服务日志
docker compose --env-file .env.docker down          # 停止（数据卷保留）
docker compose --env-file .env.docker down -v       # 停止并清空全部数据（慎用）
```

- 数据持久化在 `mysql_data`、`redis_data`、`backend_files`（上传文件）、`ai_data`（向量库 + 会话库）四个卷中，`down` 不会丢失数据。
- 生产编排中 MySQL / Redis / 后端 / AI 服务均**不暴露**宿主机端口，仅 Nginx 暴露 `8088`。
- 健康检查：后端 `/health`、AI 服务 `/ai/health`，容器启动顺序依赖健康状态自动编排。

## 7. 本地开发部署（源码方式）

### 7.0 启动依赖服务（MySQL + Redis）

推荐用开发编排只启动中间件：

```bash
docker compose -f docker-compose.dev.yml up -d
# MySQL: localhost:3306（默认 root / root123，可用 MYSQL_ROOT_PASSWORD 覆盖）
# Redis: localhost:6379
```

也可以在本机自行安装 MySQL 8.x 与 Redis 7.x（连接地址默认 `localhost:3306` / `localhost:6379`）。

### 7.1 初始化数据库

应用会自动建表，只需先创建空库：

```bash
mysql -u root -e "CREATE DATABASE IF NOT EXISTS exam DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
```

### 7.2 启动后端（exam-backend）

```bash
cd exam-backend

# 配置环境变量：新建 .env（已被 .gitignore 忽略），可参考 src/main/resources/application.example
# 最少需要：DB_PASSWORD、JWT_SECRET；首次启动还需 ADMIN_INITIAL_PASSWORD（≥8 位）
#   DB_PASSWORD=your-db-password
#   JWT_SECRET=replace-with-a-long-random-secret
#   ADMIN_INITIAL_PASSWORD=change-me-admin

# 启动
mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run          # macOS / Linux
```

验证：健康检查 <http://localhost:8077/health>；Swagger UI <http://localhost:8077/swagger-ui.html>。

### 7.3 启动 AI 服务（ai-tutor）

```bash
cd ai-tutor
python -m venv .venv && .venv\Scripts\activate     # Windows
# python -m venv .venv && source .venv/bin/activate  # macOS / Linux
pip install -r requirements.txt
cp .env.example .env        # Windows: Copy-Item .env.example .env
# 编辑 .env，至少配置：
#   JWT_SECRET（与后端完全一致，且 ≥32 字符）
#   LLM_API_KEY、EMBEDDING_API_KEY
python main.py              # → http://localhost:8080
```

验证：健康检查 <http://localhost:8080/ai/health>；FastAPI 自动文档 <http://localhost:8080/docs>。

### 7.4 启动前端（exam-frontend）

```bash
cd exam-frontend
npm install
npm run dev                 # → http://localhost:8076
```

开发态路由说明（由 [vite.config.ts](exam-frontend/vite.config.ts) 与 `.env.development` 决定）：

- 前端请求 `/api/*` 由 Vite 代理到 `http://localhost:8077`（自动去掉 `/api` 前缀），保持同源以便 HttpOnly Cookie 正常工作；
- AI 请求直连 `http://localhost:8080/ai`，因此使用 AI 功能前需先启动 ai-tutor。

### 7.5 访问地址速查

| 入口 | 本地开发 | Docker 部署 |
|---|---|---|
| 系统页面（SPA） | http://localhost:8076 | http://localhost:8088 |
| 后端 API / Swagger | http://localhost:8077/swagger-ui.html | 经 `http://localhost:8088/api/` |
| AI 服务 API / 文档 | http://localhost:8080/docs | 经 `http://localhost:8088/ai/` |

### 7.6 裸机 Nginx 部署（可选）

不使用 Docker 时：前端 `npm run build` 后将 `dist/` 作为 Nginx 静态根目录，把仓库根目录 [nginx.conf](nginx.conf) 复制到 `nginx/conf.d/`，并将各服务改为后台常驻运行（后端 jar、`uvicorn`）。该配置已包含 `/api`、`/ai` 分流与 SPA 回退、`client_max_body_size 52m`。

## 8. 环境变量与配置说明

### 8.1 Docker 部署（根目录 `.env.docker`）

由 [.env.docker.example](.env.docker.example) 提供模板，`docker compose` 读取：

| 变量 | 必填 | 默认值 | 说明 |
|---|---|---|---|
| `MYSQL_ROOT_PASSWORD` | ✅ | — | MySQL root 密码（仅容器内部使用） |
| `DB_USERNAME` | 否 | `exam_app` | 应用数据库账号（首次启动由 MySQL 创建，请勿用 root） |
| `DB_PASSWORD` | ✅ | — | 应用数据库密码 |
| `ADMIN_INITIAL_PASSWORD` | ✅ | — | 初始管理员密码（账号固定 `admin`，至少 8 位） |
| `ADMIN_INITIAL_EMAIL` | 否 | `admin@example.com` | 初始管理员邮箱 |
| `JWT_SECRET` | ✅ | — | JWT 签名密钥，**backend 与 ai-tutor 必须一致**，建议 ≥32 位随机字符串 |
| `LLM_API_KEY` | ✅ | — | DeepSeek（或兼容服务）API Key |
| `EMBEDDING_API_KEY` | ✅ | — | Embedding 服务 API Key（默认 SiliconFlow） |
| `MAIL_USERNAME` / `MAIL_AUTH_CODE` | 否 | 空 | QQ 邮箱 SMTP 授权码；不配置则邮箱验证码功能不可用 |
| `CORS_ALLOWED_ORIGINS` | 否 | `http://localhost:8076,http://localhost:8088` | 允许跨域的前端来源（同源生产部署通常无需修改） |
| `USE_UNSTRUCTURED` 等 RAG 参数 | 否 | 见模板 | 分块/检索/改写参数、上传大小上限（`MAX_UPLOAD_MB=50`）等 |

### 8.2 各服务本地开发配置

| 服务 | 配置文件 | 关键变量 |
|---|---|---|
| exam-backend | `exam-backend/.env`（模板见 `application.example`） | `DB_USERNAME`/`DB_PASSWORD`、`REDIS_PASSWORD`、`JWT_SECRET`、`ADMIN_INITIAL_PASSWORD`/`ADMIN_INITIAL_EMAIL`、`MAIL_USERNAME`/`MAIL_AUTH_CODE`、`CORS_ALLOWED_ORIGINS`；完整配置项表见 [exam-backend/README.md](exam-backend/README.md) |
| ai-tutor | `ai-tutor/.env`（模板见 [.env.example](ai-tutor/.env.example)） | `JWT_SECRET`、`LLM_API_BASE`/`LLM_API_KEY`/`LLM_MODEL`、`EMBEDDING_API_BASE`/`EMBEDDING_API_KEY`/`EMBEDDING_MODEL`、`EXAM_BACKEND_URL`（默认 `http://localhost:8077`）、`LANGFUSE_*`（可选可观测性，不需要时设 `LANGFUSE_ENABLED=false`） |
| exam-frontend | `.env.development` / `.env.production` | `VITE_API_BASE_URL`（开发 `/api` 走代理；生产留空 = 同源）、`VITE_AI_BASE_URL`（生产留空 = 同源 `/ai`）、`VITE_DEBUG` |

约定：所有敏感信息（数据库密码、JWT 密钥、API Key、SMTP 授权码）均通过 `.env` / 环境变量注入，不写入代码仓库；`.env*` 与 `.env.docker` 已在 `.gitignore` 中忽略。

## 9. 接口与 API 文档入口

| 入口 | 地址 | 说明 |
|---|---|---|
| 后端 Swagger UI | http://localhost:8077/swagger-ui.html | 在线查看与调试全部 REST 接口（本地开发） |
| AI 服务文档 | http://localhost:8080/docs | FastAPI 自动生成的 OpenAPI 文档（本地开发） |
| 统一入口（Docker） | http://localhost:8088/api/... 、/ai/... | 经 Nginx 分发；`/api/` 会去掉前缀转发 |

后端主要路由前缀（完整说明见 [exam-backend/README.md](exam-backend/README.md)）：

| 前缀 | 用途 | 权限 |
|---|---|---|
| `/user`、`/email` | 登录/注册/登出、改密改资料、邮箱验证码 | 登录/注册/验证码公开 |
| `/student` | 试卷列表/详情、开考、草稿、交卷、切屏告警、记录、错题集 | 学生 |
| `/question`、`/examPaper`、`/examRecord`、`/subject` | 题目/试卷/考试记录/科目管理 | 教师 / 管理员 |
| `/admin/users`、`/admin/dashboard` | 用户管理、统计仪表盘 | 管理员 |
| `/files`、`/health` | 文件上传（≤2MB）、健康检查 | 登录 / 公开 |

AI 服务接口（前缀 `/ai`，需登录且 JWT 与后端一致）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/ai/health` | 健康检查（公开） |
| POST | `/ai/teacher/upload` | 上传知识文档（PDF/TXT/MD/DOCX/PPTX，≤50MB） |
| POST | `/ai/teacher/generate` | AI 出题：检索 → 生成 → 质检 → 入库 |
| POST | `/ai/teacher/chat` | 基于知识库的 RAG 问答 |
| GET | `/ai/teacher/subjects` | 科目列表（教师端） |
| DELETE | `/ai/teacher/knowledge` | 清空知识库 |
| GET/DELETE | `/ai/teacher/sessions`、`/ai/teacher/sessions/{id}` | 出题会话管理 |
| GET | `/ai/student/status` | 学习伙伴可用状态（考试中不可用） |
| POST | `/ai/student/ask`、`/ai/student/ask/stream` | Socratic 答疑（普通 / SSE 流式） |
| POST | `/ai/student/recommend` | 基于错题集的复习推荐 |
| POST | `/ai/student/session/clear`、GET/DELETE `/ai/student/sessions` | 答疑会话管理 |

## 10. 权限模型与关键业务规则

### 权限模型

```
角色值：1 = 学生    2 = 老师    3 = 管理员（数据库、前后端一致）
```

- 后端：JWT 拦截器 + `@Auth(role)` 注解双重校验，未标注的接口为公开接口；登录成功签发 Token，同时支持 `Authorization: Bearer <token>` 请求头与 `exam_token` HttpOnly Cookie。
- 前端：路由守卫按 `meta.requiresAuth` + `meta.roles` 控制页面访问，未授权跳转 401。
- AI 服务：独立校验同一 JWT 后，以调用者本人身份回访后端接口，后端再次校验账号状态与角色。
- 顶号检测：基于 Redis 登录版本号，改密/登出后旧 Token 立即失效。

### 关键业务规则

- 重新考试同一试卷 → 覆盖上次记录，累计考试次数；达到上限后禁止进入。
- 客观题自动判分，主观题需老师/管理员批改。
- 错题集自动收录客观题错误，可标记掌握或删除；同一用户同一题只保留一条错题记录，重复答错累计次数。
- AI 学生端采用 Socratic 引导法——绝不直接给答案；回答经"正则 + LLM"双重答案泄露检测，触发即安全重生成。
- 考试进行中，学生端 AI 学习伙伴不可用。

## 11. AI 智能助手

```
┌─ 教师端（出题助手）─────────────────────────────────────┐
│  📄 上传知识库（PDF/TXT/MD/DOCX/PPTX，格式+结构感知分块）  │
│  💬 提问 — 基于知识库的 RAG 问答                          │
│  🧠 生成题目 — 检索参考题 → LLM 生成 → 质检 → 入库        │
│  题型：单选 / 多选 / 判断 / 主观                          │
└──────────────────────────────────────────────────────────┘

┌─ 学生端（学习伙伴）─────────────────────────────────────┐
│  📖 错题集一键触发 AI 答疑                                │
│  🎓 Socratic 引导式教学（不直接给答案）                    │
│  💡 思考提示 + 知识点关联                                 │
│  🔒 答案泄露检测（正则 + LLM 双重校验）                    │
└──────────────────────────────────────────────────────────┘
```

| 能力 | 实现 |
|---|---|
| 知识库检索 | ChromaDB（内嵌）+ BGE 中文 Embedding，语义搜索 + 关键词候选 + RRF 混合排序，支持 Query 改写 |
| 出题流水线 | LangGraph 状态机：需求理解 → 检索 → 生成 → 质检 → 入库 |
| 答疑对话 | SSE 流式输出；服务端加载题目上下文与会话历史，前端零敏感数据 |
| 答案防泄露 | 确定性正则 + LLM 二次校验，触发即安全重生成 |
| 可观测性 | Langfuse 追踪（可选启用，评测指南见 [ai-tutor/eval/README.md](ai-tutor/eval/README.md)） |

## 12. 测试说明

| 范围 | 命令 | 说明 |
|---|---|---|
| 后端单元测试 | `cd exam-backend && mvnw.cmd test`（Linux/macOS 用 `./mvnw test`） | 覆盖 JWT 拦截器、用户/邮件/考试记录服务等；JaCoCo 报告生成于 `target/site/jacoco/index.html`；手工压力测试默认禁用 |
| AI 服务测试 | `cd ai-tutor && pytest tests/ -v -m "not integration"` | 不依赖真实模型与后端；`integration` 标记的用例需要有效 JWT、模型服务与运行中的后端 |
| 前端类型检查与构建 | `cd exam-frontend && npx vue-tsc --noEmit && npm run build` | 构建命令本身即包含 `vue-tsc` 类型检查 |
| 压测 | 见 [exam-backend/README.md](exam-backend/README.md)「测试与压测」 | JMeter 5.6.3 + Python 脚本，覆盖登录、完整考试流程、并发交卷等；**勿在生产库执行** |
| AI 评测 | 见 [ai-tutor/eval/README.md](ai-tutor/eval/README.md) | Langfuse 数据集回归、RAG 五格式补充评测、安全评审校准 |

## 13. CI/CD 流程

工作流定义：[.github/workflows/ci.yml](.github/workflows/ci.yml)。触发条件：

- `push` 到 `main` 或 `codex/agent` 分支；
- 向 `main` 发起 Pull Request。

| Job | 运行环境 | 检查项 |
|---|---|---|
| Backend (Java 21) | ubuntu-latest · Temurin JDK 21 | `mvn -q -DskipTests compile` → `mvn test` → JaCoCo 覆盖率汇总至 Job Summary |
| AI Tutor (Python 3.12) | ubuntu-latest · Python 3.12 | 安装依赖 → `compileall` 语法检查（agents/routers/utils/eval/models/config）→ `pytest tests/ -v -m "not integration"` |
| Frontend (Vue 3) | ubuntu-latest · Node.js 20 | `npm ci` → `npx vue-tsc --noEmit` → `npm run build` |

说明：CI 使用固定的测试用 `JWT_SECRET`（长度满足 ≥32 字符，不需要真实密钥）；当前工作流仅做构建与测试校验（CI），未包含自动部署（CD）。

## 14. 常见问题（FAQ）

1. **后端启动失败，提示"必须通过 ADMIN_INITIAL_PASSWORD 设置至少 8 位的新密码"**：首次启动且库中不存在管理员时，必须在 `.env` 或环境变量中配置 `ADMIN_INITIAL_PASSWORD`（≥8 位）。
2. **启动失败，提示 `JWT_SECRET` 占位符无法解析**：`jwt.secret` 无默认值，必须配置 `JWT_SECRET`；同时确保 ai-tutor 的 `JWT_SECRET` 与其完全一致且 ≥32 字符，否则 AI 服务将拒绝所有请求。
3. **登录时要求邮箱验证码 / 验证码邮件发送失败**：`MAIL_AUTH_CODE` 必须填写 QQ 邮箱的 **SMTP 授权码**（不是邮箱密码）；不配置邮件时相关功能不可用。自动化场景可参考后端压测脚本预置可信设备以跳过验证码（见 exam-backend/README.md）。
4. **前端请求被 CORS 拦截**：将前端来源追加到 `CORS_ALLOWED_ORIGINS`（默认允许 `http://localhost:8076` 与 `http://localhost:8088`）。
5. **文件上传失败**：后端上传上限为 2MB（`spring.servlet.multipart`）；AI 知识库上传上限为 50MB（`MAX_UPLOAD_MB`，Nginx 侧 `client_max_body_size 52m`）。
6. **Redis 未启动会影响什么**：验证码、可信设备与登录版本校验依赖 Redis（连接超时 3s，不会长时间挂起请求）。本地开发建议先通过 `docker compose -f docker-compose.dev.yml up -d` 启动。
7. **Docker 部署报"请在 .env.docker 中设置 xxx"**：这是编排的必填校验，按第 8.1 节表格补齐 `.env.docker` 中的必填变量后重试。
8. **http://localhost:8088 无法访问**：确认端口未被占用（`docker compose ps` 查看健康状态），并等待 MySQL/后端/AI 服务健康检查通过（首次启动需等初始化与种子导入完成）。
9. **AI 功能不可用（出题/答疑报错）**：依次检查 ai-tutor 是否运行、`LLM_API_KEY`/`EMBEDDING_API_KEY` 是否有效、`JWT_SECRET` 是否与后端一致；出题前需先在教师端上传知识库文档。
10. **启动日志提示错题唯一索引迁移失败**：`wrong_question` 表存在历史重复数据，需人工核对处理（详见 [exam-backend/scripts/EXAM_OPTIMIZATION.md](exam-backend/scripts/EXAM_OPTIMIZATION.md)）。

## 15. 许可证与贡献指南

### 许可证

- 仓库当前**未包含**独立的 `LICENSE` 文件；后端 OpenAPI 文档元信息中标注为 MIT。如需正式开源授权，请先补充许可证文件。

### 贡献指南

- 提交前确保本地通过对应服务的检查（后端 `mvnw test`、AI 服务 `pytest -m "not integration"`、前端 `vue-tsc --noEmit`），CI 会在 PR 上复检。
- 提交信息保持简短、面向动作（如"修复 rag 分块问题"），每个提交聚焦单一变更。
- 不要提交 `.env`、`.env.docker` 等包含凭据的文件（已在 `.gitignore` 中忽略）。
- 数据库变更请遵循现有模式（`schema-admin.sql` 幂等建表 + `DatabaseMigrationRunner` 增量迁移），不要手工修改线上库结构。
- PR 请说明目的、影响的接口/流程、配置变更与测试证据；涉及接口行为变更时建议附示例 JSON/SSE 输出。
