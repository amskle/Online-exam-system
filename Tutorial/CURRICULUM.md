# 📚 在线考试系统 — 全栈课程大纲

> 共 **20 课时**，从项目宏观架构到微观实现，覆盖后端（Spring Boot）、前端（Vue 3）、AI 服务（FastAPI）全链路。
> 每课约 **90～120 分钟**（讲解 + 读代码 + 练习）。
> 前置要求：已掌握第 1 阶段（Java/Python/数据库/Git 基础）。

---

## 课时概览

```
第 1 部分：项目总览与后端核心（第 1～7 课）
第 2 部分：前端开发与全栈联调（第 8～12 课）
第 3 部分：AI 智能服务（第 13～17 课）
第 4 部分：集成、部署与质量（第 18～20 课）
```

---

## 第 1 部分：项目总览与后端核心（7 课时）

---

### 第 1 课：项目架构全景

**目标**：在大脑里建立完整的系统地图，理解三个服务如何协作。

- **概念讲解**
  - 微服务 vs 单体架构的取舍——本项目为什么拆成三个服务？
  - nginx 反向代理的 routing 规则：`/api/` → backend，`/ai/` → ai-tutor，`/` → SPA
  - docker-compose.yml 中 5 个容器的依赖关系
  - JWT 共享：前端 → 任意服务 → 后端验证（一张图讲清 Token 流动）

- **对照阅读**
  - `nginx.conf`：理解每个 location block
  - `docker-compose.yml`：services → depends_on → volumes 链路
  - `.env.docker`：所有环境变量的作用

- **练习**：画一张系统架构图（三服务 + nginx + MySQL + Redis），标注端口和通信协议。

---

### 第 2 课：后端项目骨架与配置体系

**目标**：读懂 Spring Boot 项目的组织方式和配置加载机制。

- **概念讲解**
  - Maven 项目结构：`pom.xml` 中的 parent、dependency、plugin
  - `application.yml` 多环境配置（dev / prod）
  - `spring-dotenv` 加载 `.env` → 比直接写死在 yml 中更安全
  - `@Configuration` 配置类一览：CorsConfig / WebConfig / MybatisPlusConfig / PasswordConfig

- **对照阅读**
  - `pom.xml`：MyBatis-Plus、JJWT、springdoc 等核心依赖
  - `application.yml`：datasource、redis、mail、jwt 各段
  - `config/` 下所有配置类（重点是 CorsConfig 和 WebConfig）

- **练习**：给 `application.yml` 新增一个自定义配置项 `app.feature.export-enabled: true`，并创建一个 `@ConfigurationProperties` 类读取它。

---

### 第 3 课：数据库设计与 ORM 层

**目标**：理解 8 张核心表的 ER 关系，掌握 MyBatis-Plus 的使用。

- **概念讲解**
  - ER 图：user → exam_record → exam_record_answer ← exam_paper_question ← exam_paper + question
  - 多对多关系拆解（`exam_paper_question` 中间表）
  - 错题集（`wrong_question`）的设计意图
  - MyBatis-Plus 的乐观锁 / 逻辑删除 / 自动填充
  - `BaseMapper<T>` + `LambdaQueryWrapper` — 不用写 SQL

- **对照阅读**
  - `schema-admin.sql`：全部建表语句（字段类型、索引、外键）
  - `pojo/entity/` 全部实体类 + `@TableName` `@TableId` `@TableField` 注解
  - `mapper/` 全部接口（观察它们有多简洁）
  - `service/impl/QuestionServiceImpl` 中 queryWrapper 的使用

- **练习**：新增一个 `exam_paper` 的 `is_published TINYINT` 字段（从 sql 到 entity 到 service），在 `schema-admin.sql` 加迁移语句。

---

### 第 4 课：REST API 设计与分层架构

**目标**：掌握 Controller → Service → Mapper 的标准分层和统一响应模式。

- **概念讲解**
  - DTO（请求参数） vs VO（响应数据） vs Entity（数据库映射）— 为何三层分离？
  - `Result<T>` 统一响应体：`ResultCode` 枚举
  - `@Valid` + `@NotBlank` 等参数校验注解
  - 异常处理链：`BusinessException` 抛出 → `GlobalException` 捕获 → `Result.fail()` 返回
  - RESTful URL 设计规范（本项目实际范式）

- **对照阅读**
  - `pojo/api/Result.java` + `ResultCode.java`
  - `common/exception/BusinessException.java` + `GlobalException.java`
  - `BaseUserController.java`（最完整的 CRUD 示例）
  - 对比 `SubjectController.java`（简单）→ `QuestionController.java`（复杂）→ `StudentExamController.java`（业务密集型）

- **练习**：新增一个 `ExamRecordExportController`，提供 `GET /examRecord/export?paperId=1` 接口，使用同样的 `Result` 返回格式。

---

### 第 5 课：认证鉴权系统（JWT + 拦截器）

**目标**：深入理解 JWT 的原理与本项目的三层鉴权设计。

- **概念讲解**
  - JWT 结构：Header + Payload + Signature，为什么不用 session？
  - `@Auth` 注解 + 反射读取 + `JwtInterceptor.preHandle()` 执行流程
  - `UserContext`（ThreadLocal）存储当前用户信息 — 线程隔离原理
  - 顶号检测：JWT 中的 `login_version` vs Redis 中的版本号
  - `@Auth({1,2})` vs `@Auth(3)` — 角色枚举关系

- **对照阅读**
  - `annotation/Auth.java`：自定义注解定义
  - `interceptor/JwtInterceptor.java`：全流程（重点：白名单、Token 提取、Claims 解析、角色校验、顶号检测）
  - `utils/JwtUtil.java`：生成 + 解析 Token
  - `utils/UserContext.java`：ThreadLocal 实现
  - `utils/RedisUtil.java`：Redis 操作的封装

- **练习**：在 `JwtInterceptor` 中添加一个「操作审计日志」——每个请求记录 userId + path + time 到 Redis 队列。

---

### 第 6 课：Redis 与邮件验证

**目标**：掌握 Redis 在项目中的实际应用场景，理解邮箱验证码的完整流程。

- **概念讲解**
  - Redis 五大数据结构在本项目中的应用（String、Hash、List、Set、Zset）
  - 注册流程：发送验证码 → 存入 Redis（5min TTL） → 校验 → 完成注册
  - 邮箱发送频率控制：60s cooldown + 每日 10 次限制（Redis incr）
  - 受信设备机制：Cookie + 长期 Token 绕过二次验证

- **对照阅读**
  - `service/impl/EmailServiceImpl.java`：核心业务逻辑（sendCode / verifyCode / 频率控制）
  - `utils/RedisUtil.java`：opsForValue / opsForHash 操作
  - `controller/EmailController.java`：三方接口（发送 / 校验 / 受信设备）
  - `BaseUserController.login()` 中调用的 `beginLogin()` 流程

- **练习**：实现一个「登录失败次数限制」——5 次失败后锁定账号 15 分钟，使用 Redis。

---

### 第 7 课：核心业务流程 — 考试全生命周期

**目标**：串联最复杂的业务链路：试卷 → 开考 → 答题 → 提交 → 自动判分 → 错题录入。

- **概念讲解**
  - 考试状态机：待考试 → 考试中 → 已交卷
  - 重考覆盖策略：删除旧记录 → 创建新记录 → 递增 attempt_count
  - 自动判分：单选题/多选题/判断题 vs 主观题（人工评分）
  - 客观题答错 → 自动写入 wrong_question 表
  - 切屏警告（warning_count） + 超时强制提交

- **对照阅读**
  - `StudentExamController.java`：所有考试端接口（纸卷列表→开始→保存进度→提交→记录查看）
  - `ExamRecordServiceImpl.java`：startExam / submitExam / autoGrade 核心方法
  - `WrongQuestionServiceImpl.java`：错题自动录入
  - `pojo/entity/ExamRecord.java` + `ExamRecordAnswer.java`：状态字段设计

- **练习**：补全场景——在 `submitExam` 中如果发现考生答案全部为空，返回一个特殊错误码而非继续判分（画出流程图再改代码）。

---

## 第 2 部分：前端开发与全栈联调（5 课时）

---

### 第 8 课：Vue 3 前端骨架与路由守卫

**目标**：理解 Vite + Vue 3 + TypeScript 的项目结构与权限路由设计。

- **概念讲解**
  - Vite 构建流程：`vite.config.ts` 中的 alias、proxy、plugins
  - 自动导入机制：`unplugin-auto-import` + `unplugin-vue-components`（解释为何不用手动 import Element Plus）
  - 路由设计：`meta.requiresAuth` + `meta.roles` 驱动鉴权
  - `router.beforeEach` 导航守卫：无 Token → `/login`，无权限 → `/401`

- **对照阅读**
  - `vite.config.ts`：所有配置项
  - `router/index.ts`：路由表 + 导航守卫（重点：role 校验逻辑）
  - `main.ts`：应用入口（pinia + router + Element Plus 注册）
  - `src/types/`：TypeScript 类型定义

- **练习**：新增一个 `/about` 路由（公开访问）和一个 `/admin/settings` 路由（仅 admin），并配置守卫。

---

### 第 9 课：Element Plus 布局系统与主题

**目标**：理解中后台系统的两套布局（Admin / Student）和常见 UI 模式。

- **概念讲解**
  - Element Plus 组件体系：Container / Menu / Table / Form / Dialog
  - 两套 Layout 设计：侧边栏菜单 + 顶栏 vs 简洁学生界面
  - SVG 图标处理：`vite-svg-loader` 将 SVG 转为 Vue 组件
  - 响应式适配基础

- **对照阅读**
  - `layouts/AdminLayout.vue`：侧边栏菜单、面包屑、用户头像
  - `layouts/StudentLayout.vue`：学生界面的布局
  - `components/UserProfileMenu.vue`：用户下拉菜单
  - `views/user/Dashboards.vue`：学生首页（试卷列表卡片）

- **练习**：在 AdminLayout 侧边栏新增一个菜单项「操作日志」，链接到 `/admin/logs`。

---

### 第 10 课：Axios 封装与前后端认证协同

**目标**：打通前端与后端的认证链路，掌握 Token 管理。

- **概念讲解**
  - Axios 实例的 `baseURL` / `timeout` / `withCredentials` 配置
  - 请求拦截器：自动注入 `Authorization: Bearer <token>`
  - 响应拦截器：401 → 清除 Token 并跳转登录页
  - Token 存储策略：`sessionStorage`（多 Tab 隔离） vs `localStorage`（记住我）
  - 跨 Tab 登录状态同步：`sessionSync.ts` 的实现

- **对照阅读**
  - `utils/request.ts`：完整的 Axios 封装（重点：interceptor）
  - `utils/localStorage.ts`：getToken / setToken / clearAllAuth 等
  - `utils/sessionSync.ts`：storage 事件监听
  - `api/user-api.ts`：登录 / 注册 / Token 验证 API 调用

- **练习**：模拟 Token 过期场景，追踪请求拦截器 → 响应拦截器 → 路由重定向的完整链路。

---

### 第 11 课：核心页面深度解析

**目标**：读懂最复杂的两张页面——登录页和考试页。

- **概念讲解**
  - 登录页（Login.vue）：表单验证 + 角色登录（记住我） + 注册/邮箱验证跳转
  - 考试页（Exam.vue）：试卷加载 → 倒计时 → 逐题作答 → 提交确认
  - 考试页的防作弊逻辑：`visibilitychange` 事件监听切屏 + 窗口失焦警告
  - ECharts 仪表盘：数据统计图表的 Vue 集成

- **对照阅读**
  - `views/Login.vue`：完整了解登录注册流程（重点：`handleLogin` 方法）
  - `views/exam/Exam.vue`：考试全流程（重点：定时器、自动保存、提交逻辑）
  - `views/EmailVerifyPage.vue`：邮箱验证页面
  - `components/FloatingTutor.vue`：浮动 AI 助手入口

- **练习**：在 Login.vue 中增加一个「微信扫码登录」的占位按钮，点击后弹出提示"功能开发中"。

---

### 第 12 课：全栈联调 — 一个功能从数据库到页面的完整链路

**目标**：亲手追踪一个完整请求，建立全栈思维。

- **概念讲解**
  - 请求链路全图：Vue 组件 → Axios → nginx → Spring Boot Controller → Service → Mapper → MySQL → 反向返回
  - 开发时的跨域问题：CorsConfig 的 `allowedOriginPatterns` 配置
  - Vue 代理 vs nginx 代理的区别（dev vs prod）
  - 浏览器 DevTools Network 面板 + Spring Boot 日志 + MySQL 慢查询日志的联合调试

- **对照阅读**
  - 选择一个完整功能追踪（推荐：学生查看可用试卷列表）
    1. `views/user/Dashboards.vue` — 页面渲染
    2. `api/student-api.ts` — API 调用
    3. `StudentExamController.getAvailablePapers()` — 接收请求
    4. `ExamPaperServiceImpl.getAvailablePapers()` — 业务逻辑
    5. `ExamPaperMapper` 查询数据库
  - `config/CorsConfig.java`：跨域配置

- **练习**：在上述链路中增加一个日志埋点，在每次查询试卷列表时输出"用户 [userId] 查看了试卷列表"，分别在前端 console、后端 log、MySQL general_log 中看到。

---

## 第 3 部分：AI 智能服务（5 课时）

---

### 第 13 课：FastAPI 服务入门与 JWT 共享

**目标**：理解 Python 微服务的结构，以及如何与 Spring Boot 共享认证。

- **概念讲解**
  - FastAPI vs Spring Boot — 为什么 AI 服务选择 Python？
  - Pydantic schemas 与 Spring Boot DTO 的对应关系
  - JWT 共享机制：ai-tutor 用同样的 `JWT_SECRET` 验证 Token
  - `ExamBridge`：Python 服务 → Spring Boot 的 HTTP 代理调用
  - `POST /ai/student/ask` 完整的请求处理流程

- **对照阅读**
  - `main.py`：FastAPI 入口（lifespan、CORS、异常处理器）
  - `config/settings.py`：所有配置（LLM / Embedding / RAG 参数）
  - `models/schemas.py`：请求/响应模型（与 Spring Boot Result 对齐）
  - `utils/jwt_util.py`：Python 端 JWT 验证（`verify_token` / `get_user_id`）
  - `utils/exam_bridge.py`：ExamBridge（httpx 异步客户端）

- **练习**：在 ai-tutor 中新增一个 `GET /ai/health` 端点，返回后端 exam-backend 的健康状态（通过 ExamBridge 调用后端 `/api/health`，需先在 backend 创建该端点）。

---

### 第 14 课：大模型 API 调用与 Prompt 工程

**目标**：掌握 OpenAI 兼容 API 的调用模式，理解结构化输出。

- **概念讲解**
  - `AsyncOpenAI` 客户端配置（base_url、api_key、model）
  - Chat Completion API：`messages` 数组（system / user / assistant role）
  - 结构化输出：System Prompt 中要求 JSON → `extract_json()` 后处理
  - 错误处理与重试：`chat_text()` / `chat_json()` 的异常处理
  - DeepSeek API 的特性（上下文 1M、价格优势）

- **对照阅读**
  - `agents/common.py`：`chat_text()` / `chat_json()` / `extract_json()` / `has_fatal()`
  - `config/settings.py`：LLM 相关配置（model、max_tokens、temperature）
  - Teacher Agent 的 `understand` 节点：如何将用户需求转为 LLM 可理解的 prompt

- **练习**：调用 DeepSeek API 写一个「关键词提取函数」——输入一段中文文本，输出 JSON 数组格式的关键词列表，模仿 `chat_json` 的调用模式。

---

### 第 15 课：RAG 检索增强生成

**目标**：完整理解 RAG 流程——文档加载 → 切分 → Embedding → 向量检索。

- **概念讲解**
  - RAG 为什么比纯 LLM 更适合知识问答？（解决幻觉 / 知识滞后问题）
  - 两阶段流程：**Indexing**（文档处理 + Embedding + 存入向量库）→ **Retrieval**（查询向量化 + 相似度检索 + 上下文注入）
  - ChromaDB `PersistentClient` 本地持久化
  - Embedding 模型选型（本项目用 BGE-large-zh-v1.5，兼容硅基流动 API）
  - 语义搜索退化机制：API 不可用时回退到关键词搜索

- **对照阅读**
  - `rag/document_loader.py`：PDF/TXT 解析 + 按题号切分
  - `rag/embeddings.py`：`EmbeddingService` + LRU 缓存
  - `rag/vector_store.py`：ChromaDB 封装（teacher_kb / student_kb 双集合）
  - `rag/retriever.py`：`Retriever.retrieve()` — 语义优先 → 关键词兜底

- **练习**：在 ai-tutor 启动时，用代码向 ChromaDB 中写入一条自定义文档："本题库包含 408 计算机学科专业基础综合试题"，然后通过 Retriever 检索"计算机基础"验证能否召回。

---

### 第 16 课：LangGraph 智能体（上）— Teacher Agent

**目标**：深入理解 LangGraph 状态机在 AI 出题场景的应用。

- **概念讲解**
  - LangGraph 核心概念：`StateGraph` + `Node` + `Edge` + `END`
  - Teacher Agent 五步状态机：`understand → retrieve → generate(循环) → check → save`
  - `generate` 节点的批量生成循环：`generate_max_attempts` 次尝试 + 确定性校验
  - 容错机制：`fatal_error` 字段短路到 `END`
  - `quality_check` 的双重保障：LLM 评分 + 规则校验（`_validate_single_question`）

- **对照阅读**
  - `agents/teacher_agent.py`：完整代码（重点：`build_graph()` 中的节点注册和边连接）
  - `agents/common.py`：`has_fatal()` 条件边判断
  - 从 `routers/teacher.py` 的 `generate` 端点出发 → 追踪到 `teacher_agent.run()`

- **练习**：给 Teacher Agent 新增一个 `deduplicate` 节点（在 `generate` 和 `check` 之间），用于去除生成的题目中与已存题目相似度 > 90% 的题目。

---

### 第 17 课：LangGraph 智能体（下）— Student Agent 与答案泄露防护

**目标**：理解学生智能体的 Socratic 教学法以及双重答案泄露检测。

- **概念讲解**
  - Student Agent 六步状态机：`load_context → understand → retrieve → plan → generate → check`
  - 服务端加载答案（`load_context` 节点）→ 客户端永远不传答案
  - Socratic 教学法：不直接给答案，而是通过引导性问题启发思考
  - 双重答案泄露检测：
    - 第一层：正则匹配（`A、B、C、D`、`正确/错误` 等模式）
    - 第二层：LLM 重新检查
    - 命中任一 → 安全 regenerate（不带答案的上下文）

- **对照阅读**
  - `agents/student_agent.py`：完整代码（重点：`leak_check` 节点的双重检测 + `safe_generate` 的降级回复）
  - `routers/student.py`：`ask` 端点和 `ask/stream`（SSE）端点
  - `utils/session_store.py`：SQLite 聊天历史管理

- **练习**：设计一种新的泄露模式——如果 AI 回复中包含"根据以上分析，正确答案是"，Student Agent 应该如何检测并处理？修改 `leak_check` 节点实现。

---

## 第 4 部分：集成、部署与质量（3 课时）

---

### 第 18 课：Docker 容器化与 Nginx 配置

**目标**：理解从开发到生产的部署流程，掌握 Docker 编排。

- **概念讲解**
  - 三个 Dockerfile 分析（multi-stage build、基础镜像选择）
  - `docker-compose.yml` 完整编排（depends_on + healthcheck + volumes）
  - `docker-compose.dev.yml` vs 正式环境的区别
  - nginx 反向代理 + SPA 路由 fallback（`try_files`）
  - .env 文件管理不同环境的密钥

- **对照阅读**
  - `exam-backend/Dockerfile`：Spring Boot 容器化
  - `exam-frontend/Dockerfile`：Nginx + 静态文件多阶段构建
  - `ai-tutor/Dockerfile`：FastAPI + uvicorn
  - `nginx.conf`：所有 location 路由规则
  - `docker-compose.yml` + `docker-compose.dev.yml`

- **练习**：在本地用 `docker-compose up` 启动整个系统，验证三个服务都能正常工作，并用浏览器访问。

---

### 第 19 课：测试体系与代码质量

**目标**：掌握单元测试、集成测试，理解代码质量保障手段。

- **概念讲解**
  - Spring Boot Test：`@SpringBootTest` + Mockito + `@MockBean`
  - JaCoCo 覆盖率报告解读
  - AI 服务的测试策略：`pytest` + 跳过集成测试的 mark 机制
  - RAG 评估工具：`eval/` 目录的自动化评估流程

- **对照阅读**
  - `exam-backend/src/test/`：所有后端测试
  - `exam-backend/pom.xml`：JaCoCo plugin 配置
  - `ai-tutor/tests/test_api.py`：API 测试（跳过集成测试的 mark）
  - `ai-tutor/eval/`：RAG 评估模块（runner + report）

- **练习**：给 `SubjectServiceImpl` 写一个单元测试（模仿已有的 `BaseUserServiceImplTest`），覆盖正常查询和空结果两种情况。

---

### 第 20 课：项目回顾与面试准备

**目标**：系统复习全部知识点，模拟面试问答，规划简历。

- **概念讲解**
  - **核心问题逐一点击**：
    - 本项目架构中最巧妙的设计？（JWT 共享、顶号检测、AI 双 Agent）
    - 如果让你重新设计，你会怎么改？（缓存策略、异步消息、更多 AI 场景）
    - 最复杂的 BUG 排查经历？（日志 + 链路追踪）
  - **简历亮点提炼**（已含于 LEARNING_ROADMAP.md）
  - **面试问答模拟**（每组 5 分钟互问互答）

- **自我测验清单**（能回答 80% 说明掌握了）：
  - ❓ 描述一次完整的考试请求从浏览器到数据库再返回的全过程
  - ❓ JWT 的 login_version 顶号检测如何工作？
  - ❓ Teacher Agent 的容错机制有哪几层？
  - ❓ 为什么 AI 服务放在 Python 而不是 Java？有什么代价？
  - ❓ ChromaDB 向量检索和 MySQL LIKE 查询各自的适用场景？
  - ❓ nginx 如何同时处理 SPA 路由和 API 代理？
  - ❓ 前端 sessionStorage 存储 Token 有什么优缺点？

- **最终项目产出建议**：选一个方向做深
  - 🔵 后端：给本项目加一个 Redis 消息队列处理异步阅卷 + WebSocket 通知
  - 🟢 AI：加一个考试报告自动生成的 AI Agent + PDF 导出
  - 🟣 全栈：做一个多人在线监考大屏（教师端实时看考生状态）

---

## 📅 推荐学习节奏

```
每周 3～4 节课（约 6～8 小时）
→ 6 周完成全部 20 课时

每天 1 节课（约 2 小时）
→ 20 天速通

推荐：每周 3 节课 + 周末复习
→ 7 周扎实掌握
```

每节课遵守 **3 步法**：
1. **读概念**（20 min）— 理解这节课的核心知识
2. **读代码**（30～40 min）— 在本项目代码中找到对应实现
3. **动手练**（30 min）— 完成练习，巩固理解

---

> 📌 本课程与 `LEARNING_ROADMAP.md` 配套使用：路线图回答"学什么、为什么"，课表回答"怎么学、按什么顺序"。
