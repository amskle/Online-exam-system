# 在线考试系统 · Online Exam System

[![CI](https://github.com/amskle/Online-exam-system/actions/workflows/ci.yml/badge.svg)](https://github.com/amskle/Online-exam-system/actions/workflows/ci.yml)

一个融合 **RAG + LLM Agent** 的智能在线考试平台，支持管理员、老师、学生三类角色。核心亮点：知识库驱动的 AI 出题助手、Socratic 引导式 AI 答疑伙伴。

## 架构概览

```
┌─────────────────────────────────────────────────────────┐
│                     Nginx :80                           │
│   /api/*  → backend    /ai/*  → ai-tutor    /*  → SPA  │
└────┬───────────────────┬───────────────────┬───────────┘
     │                   │                   │
     ▼                   ▼                   ▼
┌─────────────┐   ┌─────────────┐   ┌──────────────┐
│ exam-backend│   │  ai-tutor   │   │ exam-frontend │
│ Spring Boot │   │  FastAPI    │   │  Vue 3 + TS  │
│   :8077     │   │   :8080     │   │    :8076      │
└──┬───┬──────┘   └──┬───┬──────┘   └──────────────┘
   │   │              │   │
   ▼   ▼              ▼   ▼
┌──────┐ ┌──┐   ┌─────────┐ ┌─────────┐
│MySQL │ │Redis│ │ChromaDB │ │DeepSeek │
│ 8.x  │ │   │  │ (向量库) │ │  (LLM)  │
└──────┘ └──┘   └─────────┘ └─────────┘
```

- **exam-backend**: 用户权限、题库管理、试卷组卷、在线考试、自动判分、错题集
- **ai-tutor**: 知识库 RAG 检索 → LangGraph 出题流水线 / 流式答疑对话
- **exam-frontend**: 管理员后台 / 学生考试 / 悬浮 AI 助手

## 快速启动（Docker）

```bash
cp .env.docker.example .env.docker
# 编辑 .env.docker，填入 LLM API Key
docker compose --env-file .env.docker up -d --build
```

首次启动自动建表并导入 408 真题题库（559 道）。

## AI 智能助手

```
┌─ 教师端（出题助手）─────────────────────────────┐
│  📄 上传知识库（PDF/TXT，自动按题号分块入库）      │
│  💬 提问 — 基于知识库的 RAG 问答                  │
│  🧠 生成题目 — 检索参考题 → LLM 生成 → 质检 → 入库 │
│  题型：单选 / 多选 / 判断 / 主观                  │
└────────────────────────────────────────────────┘

┌─ 学生端（学习伙伴）─────────────────────────────┐
│  📖 错题集一键触发 AI 答疑                        │
│  🎓 Socratic 引导式教学（不直接给答案）            │
│  💡 思考提示 + 知识点关联                         │
│  🔒 答案泄露检测（正则 + LLM 双重校验）            │
└────────────────────────────────────────────────┘
```

| 能力 | 实现 |
|---|---|
| 知识库检索 | ChromaDB + BGE 中文 Embedding，语义搜索 + 关键词后备 |
| 出题流水线 | LangGraph 状态机：需求理解 → 检索 → 生成 → 质检 → 入库 |
| 答疑对话 | SSE 流式输出，服务端加载上下文，前端零敏感数据 |
| 答案防泄露 | 确定性正则 + LLM 二次校验，触发即安全重生成 |

## 功能模块

### 管理员端

- **仪表盘** — 系统统计、趋势图表、题型/科目分布
- **用户管理** — 学生/老师账号管理、状态封禁、权限展示
- **科目管理** — 考试科目增删改查
- **题目管理** — 单选/多选/判断/主观题，支持 408 真题导入
- **试卷管理** — 手动选题 + 自动组卷，可配考试次数限制
- **考试记录** — 记录查看、主观题批改

### 老师端

- 题目管理、试卷管理、考试记录管理
- **AI 出题助手** — 知识库上传 / 对话查询 / AI 生成题目

### 学生端

- 试卷列表、在线考试（倒计时 + 答题卡 + 防作弊）
- 考试记录与答题详情、错题集
- **AI 学习伙伴** — 错题答疑、Socratic 引导、流式回复

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.2 · Java 21 · MyBatis-Plus 3.5 · JWT · BCrypt · Redis |
| AI 服务 | FastAPI · LangGraph · ChromaDB · DeepSeek API · BGE Embedding |
| 前端 | Vue 3 · TypeScript · Vite · Element Plus · Pinia · ECharts |
| 数据库 | MySQL 8.x · Redis · SQLite（会话） · ChromaDB（向量） |
| 工程化 | Docker Compose · GitHub Actions CI · JaCoCo · Swagger · pytest |

## 开发指南

### 环境要求

- Node.js 18+ · Java 21 · Maven 3.8+ · MySQL 8.x · Python 3.12+

### 后端

```bash
cd exam-backend

# 建库
mysql -u root -e "CREATE DATABASE IF NOT EXISTS exam DEFAULT CHARACTER SET utf8mb4"

# 启动
mvn spring-boot:run    # → http://localhost:8077
mvn test               # 单元测试 + JaCoCo 覆盖率
```

API 文档：http://localhost:8077/swagger-ui.html

### 前端

```bash
cd exam-frontend
npm install
npm run dev             # → http://localhost:8076
npm run build           # 类型检查 + 生产构建
```

### AI 服务

```bash
cd ai-tutor
pip install -r requirements.txt
cp .env.example .env
# 编辑 .env：LLM_API_KEY + EMBEDDING_API_KEY

python main.py          # → http://localhost:8080
pytest tests/ -v -m "not integration"
```

## 权限模型

```
1 = 学生    2 = 老师    3 = 管理员
```

前端路由守卫 + 后端 JWT 拦截器双重校验，`@Auth(role)` 注解控制接口访问。

## 关键业务规则

- 重新考试同一试卷 → 覆盖上次记录，累计次数；达上限后禁止进入
- 客观题自动判分，主观题需老师/管理员批改
- 错题集自动收录客观题错误，可标记掌握或删除
- AI 学生端采用 Socratic 引导法——绝不直接给答案

## CI / CD

push 到 `main` / `codex/agent` 自动触发：

| Job | 检查项 |
|---|---|
| Backend | `mvn compile` → `mvn test` → JaCoCo 覆盖率 |
| AI Tutor | 语法检查 → `pytest` (skip integration) |
| Frontend | `vue-tsc --noEmit` → `npm run build` |
