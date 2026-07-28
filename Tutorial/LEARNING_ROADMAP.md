# 🗺️ 在线考试系统 — 零基础学习路线图

> 目标：从零基础出发，通过本项目掌握**后端开发**或**AI 应用开发**技能，找到实习。
> 预计周期：**3～6 个月**（每天 3～6 小时）

---

## 📋 项目技术栈总览

本项目由三个独立服务组成，覆盖了当前国内实习市场的主流技术：

| 服务 | 技术栈 | 实习方向 |
|------|--------|----------|
| **exam-backend** | Java 21 + Spring Boot 3.2 + MySQL + Redis + MyBatis-Plus + JWT | 🔵 **后端开发** |
| **exam-frontend** | Vue 3 + TypeScript + Vite + Element Plus | 辅助理解全栈 |
| **ai-tutor** | Python + FastAPI + LangGraph + RAG + DeepSeek API | 🟢 **AI 应用开发** |

---

## 🧭 学习路线总图

```
第 1 阶段 ─── 编程基础（2～4 周）
│
├─▶ Java 基础 ←─── 走后端方向，重点学
├─▶ Python 基础 ←── 走 AI 方向，重点学
└─▶ 计算机基础（必学：数据库、网络、数据结构）
     │
     ▼
第 2 阶段 ─── 方向分化（4～8 周）
│
├─▶ 🔵 后端路径
│   ├─ Spring Boot + MyBatis-Plus
│   ├─ MySQL + Redis
│   ├─ RESTful API + JWT 鉴权
│   └─ 理解 exam-backend 全部代码
│
├─▶ 🟢 AI 应用路径
│   ├─ FastAPI + Pydantic
│   ├─ 大模型 API 调用（DeepSeek / OpenAI）
│   ├─ RAG 检索增强生成
│   └─ LangGraph 智能体框架
│
└─▶ 🟣 全栈（可选）
    └─ Vue 3 基础 + 前后端联调
    │
    ▼
第 3 阶段 ─── 项目实战（4～6 周）
│
├─▶ 读懂本项目全部核心代码
├─▶ 给项目加一个小功能（如导出成绩）
├─▶ 修复一个 issue / 写单元测试
│
▼
第 4 阶段 ─── 面试准备（2～3 周）
│
├─▶ 八股文 + 项目深挖
├─▶ 刷题（LeetCode 热题 100）
└─▶ 修改简历 + 投递
```

---

## 🔵 后端开发方向（详细路线）

### 第 1 阶段：Java 与基础（3～4 周）

| 学习内容 | 参考资源 | 本项目对应代码 |
|----------|----------|---------------|
| Java 语法基础：变量、流程控制、数组 | 廖雪峰 Java 教程 / B站黑马 | — |
| 面向对象：类、继承、接口、多态 | 《Java核心技术 卷I》 | `pojo/entity/`, `service/impl/` |
| 异常处理 + 泛型 + 集合框架 | 同上 | `common/exception/` |
| Maven 构建工具 | 官方文档 | `pom.xml` |
| MySQL：CRUD、表设计、索引 | SQLZoo + 《SQL必知必会》 | `schema-admin.sql` |
| Git 基础 | 廖雪峰 Git 教程 | 整个仓库 |

**本项目对照学习**：`pojo/entity/` 目录下的实体类是最好的 Java Bean 示例。

---

### 第 2 阶段：Spring Boot 全家桶（4～6 周）

| 学习内容 | 掌握程度 | 本项目对应代码 |
|----------|----------|---------------|
| Spring Boot 自动配置原理 | 理解即可 | `application.yml` |
| RESTful API + `@RestController` | 熟练 | `controller/*Controller.java` |
| 依赖注入 `@Service` `@Component` | 熟练 | 所有 `service/impl/` |
| MyBatis-Plus：BaseMapper + 条件构造器 | 熟练 | `mapper/`, `service/impl/` |
| DTO / VO / Entity 分层设计 | 理解 | `pojo/dto/`, `pojo/vo/`, `pojo/entity/` |
| 统一响应格式 `Result<T>` | 理解 | `pojo/api/Result.java` |
| 全局异常处理 `@ControllerAdvice` | 理解 | `GlobalException.java` |
| JWT 鉴权 + 拦截器 | 深入理解 | `interceptor/JwtInterceptor.java` |
| Redis 缓存 | 熟练 | `utils/RedisUtil.java` |
| Spring Mail 邮件发送 | 了解 | `service/impl/EmailServiceImpl.java` |
| 参数校验 `@Valid` | 了解 | DTO 中的注解 |
| Swagger 接口文档 | 了解 | `springdoc` 依赖 |
| BCrypt 密码加密 | 理解 | `PasswordConfig.java` |

**最佳学习顺序**：一个 Controller → 一个 Service → 一个 Mapper → 一个 Entity，**按功能模块逐个攻克**。建议从最简单的 `SubjectController` 开始读。

---

### 第 3 阶段：深入项目（2～3 周）

**核心业务流程理解（按阅读顺序）：**

```
1. 用户注册/登录流
   BaseUserController.login()
   → BaseUserServiceImpl.login()
   → JwtUtil 生成 Token
   → JwtInterceptor 验证

2. 考试答题流
   StudentExamController.startExam()
   → ExamRecordServiceImpl.startExam()
   → 答题 → 提交 → 自动判分
   → WrongQuestionServiceImpl 录错题

3. AI 出题流（跨服务）
   前端调用 → ai-tutor teacher_agent
   → 理解需求 → RAG 检索 → LLM 出题
   → quality_check → exam_bridge 存回后端
```

**动手实践（选做 2～3 个）：**

1. ✅ `BaseUserServiceImplTest` 补充测试用例（已有测试，可以模仿）
2. ✅ 新增一个「导出成绩单」的 Excel 下载接口（Apache POI + 新 Controller）
3. ✅ 给试卷表加一个「是否发布」字段，影响可选试卷列表
4. ✅ 写一个 Docker Compose 编排三个服务（练习容器化）

---

## 🟢 AI 应用开发方向（详细路线）

### 第 1 阶段：Python 与基础（2～3 周）

| 学习内容 | 参考资源 | 本项目对应代码 |
|----------|----------|---------------|
| Python 语法：变量、流程、函数、类 | Python 官方教程 / 廖雪峰 Python | `agents/common.py` |
| 异步编程 `async/await` | Real Python 教程 | `main.py` |
| Pydantic 数据模型 | 官方文档 | `models/schemas.py` |
| httpx 异步 HTTP | 官方文档 | `utils/exam_bridge.py` |
| pytest 测试 | 官方文档 | `tests/test_api.py` |

---

### 第 2 阶段：FastAPI + LLM 基础（3～4 周）

| 学习内容 | 掌握程度 | 本项目对应代码 |
|----------|----------|---------------|
| FastAPI 路由 + 请求/响应模型 | 熟练 | `routers/teacher.py`, `routers/student.py` |
| 依赖注入 + 中间件 | 理解 | `main.py` |
| OpenAI API 调用（兼容 DeepSeek） | 熟练 | `agents/common.py` |
| Prompt Engineering 基础 | 理解 | teacher_agent, student_agent 中的 prompt |
| Pydantic Settings 配置管理 | 了解 | `config/settings.py` |
| JWT 验证（Python 端） | 理解 | `utils/jwt_util.py` |

---

### 第 3 阶段：RAG + LangGraph（3～4 周）

| 学习内容 | 掌握程度 | 本项目对应代码 |
|----------|----------|---------------|
| **RAG 概念**：文档切分 → Embedding → 向量检索 | 深入理解 | `rag/` 目录全部 |
| ChromaDB 向量数据库 | 理解 | `rag/vector_store.py` |
| Embedding 模型选型与调用 | 理解 | `rag/embeddings.py` |
| **LangGraph 状态机**：StateGraph、Node、Edge | 熟练 | `agents/teacher_agent.py` |
| 条件跳转 + 错误处理 | 理解 | `agents/student_agent.py` |
| 会话管理 SQLite | 理解 | `utils/session_store.py` |
| 质量检查（LLM 自检 + 确定性规则） | 了解 | teacher_agent `quality_check` |

**核心流程图（Teacher Agent）：**

```
requirement_understanding
    → retrieval (RAG 检索)
    → batch_generation (LLM 出题，可循环)
    → quality_check (LLM 质检 + 规则校验)
    → save_questions (通过 exam_bridge 存回后端)
    → END
    任一节点 fatal_error → 短路到 END
```

#### 推荐学习顺序

1. **先看** `models/schemas.py` — 理解数据模型
2. **再看** `routers/teacher.py` — 理解 API 入口
3. **重点** `agents/teacher_agent.py` — LangGraph 状态机全流程
4. **重点** `rag/retriever.py` — 检索逻辑
5. **辅助** `utils/exam_bridge.py` — 后端通信

---

## 🛠️ 开发环境搭建指南

### 后端（exam-backend）

```bash
# 1. 安装 JDK 21 (推荐 Eclipse Temurin)
# 2. 安装 MySQL 8.x，创建 exam 数据库
# 3. 安装 Redis
# 4. 配置环境变量
cd exam-backend
cp src/main/resources/application.example src/main/resources/application.yml
# 编辑 application.yml，填入你的 MySQL 密码、Redis 地址

# 5. 启动
mvn spring-boot:run
# 访问 http://localhost:8077/swagger-ui.html 验证
```

### AI 学习助手（ai-tutor）

```bash
cd ai-tutor
cp .env.example .env
# 编辑 .env，填入 DeepSeek API Key

pip install -r requirements.txt
python main.py
# 访问 http://localhost:8080/docs 查看 API
```

---

## 📚 推荐学习资源

### Java / 后端

| 资源 | 说明 |
|------|------|
| [B站 - 黑马程序员 Spring Boot 全套](https://www.bilibili.com/video/BV15b4y1a7yG) | 最适合入门的 Spring Boot 视频 |
| [《Spring Boot 实战》](https://book.douban.com/subject/26857489/) | 体系化学习 |
| [MyBatis-Plus 官方文档](https://baomidou.com/) | 必备参考 |
| [《Java 核心技术 卷I》](https://book.douban.com/subject/35750929/) | Java 基础圣经 |

### AI / Python

| 资源 | 说明 |
|------|------|
| [FastAPI 官方教程](https://fastapi.tiangolo.com/zh/tutorial/) | 有中文版，非常友好 |
| [LangGraph 官方教程](https://langchain-ai.github.io/langgraph/tutorials/) | 必读，本项目基于此 |
| [DeepSeek API 文档](https://platform.deepseek.com/) | 本项目的 LLM 提供商 |
| [OpenAI Cookbook](https://cookbook.openai.com/) | RAG 和 Prompt 模式参考 |
| [吴恩达《Building Systems with ChatGPT》](https://www.deeplearning.ai/short-courses/building-systems-with-chatgpt/) | LLM 应用开发最佳入门 |

### 通用

| 资源 | 说明 |
|------|------|
| [《图解 HTTP》](https://book.douban.com/subject/25863515/) | 网络基础，面试必考 |
| LeetCode 热题 100 | 刷题用 |
| [代码随想录](https://programmercarl.com/) | 算法题解 |

---

## 📝 面试准备（第 4 阶段）

### 后端方向常考问题

**Java 基础：**
- JVM 内存模型、垃圾回收
- HashMap 原理、ConcurrentHashMap 分段锁
- 线程池参数、执行流程
- 反射、注解原理

**Spring Boot：**
- IOC 和 DI 的区别
- Bean 生命周期
- Spring Boot 自动配置原理（`@EnableAutoConfiguration`）
- 事务传播行为、隔离级别
- AOP 实现原理

**MySQL：**
- 索引类型（B+树）、聚簇索引 vs 二级索引
- 最左前缀法则、索引失效场景
- SQL 优化（explain、慢查询）
- 事务隔离级别 MVCC

**Redis：**
- 五大数据类型
- 缓存穿透、缓存击穿、缓存雪崩
- Redis 过期策略

**项目深挖（结合本项目）：**
- JWT 鉴权流程 + 顶号检测怎么实现的？
- 为什么用 ThreadLocal 存用户信息？
- 自动判分的实现策略？
- 数据库迁移机制（DatabaseMigrationRunner）？

### AI 方向常考问题

**Python：**
- GIL 是什么？影响？
- `async/await` 原理
- `__init__.py` 作用

**大模型：**
- RAG 的完整流程是什么？
- Embedding 模型选择标准？
- LangGraph 状态图怎么设计？
- LLM 输出质量控制策略？

**项目深挖（结合本项目）：**
- Teacher Agent 的容错机制（fatal_error 短路）？
- 学生侧答案泄露防护怎么做？
- RAG 退化为关键词搜索的逻辑？
- ChromaDB vs 其他向量数据库？

### 简历建议

1. **项目名称**：在线考试系统（全栈 + AI 智能学习助手）
2. **你的角色**：后端开发 / AI 应用开发
3. **技术亮点**：
   - 基于 Spring Boot 3.2 + MyBatis-Plus 构建 RESTful API
   - 实现 JWT + Redis 顶号检测的多角色鉴权系统
   - 基于 LangGraph 构建教师/学生双智能体
   - 实现 RAG 检索增强生成的知识问答系统
   - 集成 DeepSeek 大模型 API，实现自动出题与智能答疑

---

## ⏰ 每周学习计划模板（建议）

```
周一至周五（每天 2～4 小时）：
  - 看视频 / 读书 1～2 小时
  - 看本项目对应代码 0.5 小时
  - 动手写代码 0.5～1 小时

周末（每天 4～6 小时）：
  - 梳理本周知识点
  - 完整过一遍相关模块代码
  - 写一个小练习或给项目加功能
```

**第一个月目标**：能看懂 `controller/` 和 `service/` 的代码
**第二个月目标**：能独立在项目中加一个简单接口
**第三个月目标**：能讲清楚整个项目的架构和核心流程

---

## 💡 学习方法建议

1. **不要死记硬背**，以读懂本项目代码为驱动逆向学习 — 遇到不懂的语法/框架特性再去查
2. **动手＞看书**：每学一个概念，立刻在本项目里搜索对应代码
3. **先跑起来**：第一周的目标是让项目在本地运行，看到效果
4. **记笔记**：把常遗忘的知识点存到本项目 `memory/` 目录，反复阅读
5. **写博客**：每学完一个模块，写一篇技术博客（面试时加分的亮点）
6. **参与开源**：本项目就是你能参与的第一个开源项目 — 修 bug、加功能、写测试

---

> **如果时间有限**：
> - 后端方向重点读 `JwtInterceptor.java`、`BaseUserServiceImpl.java`、`ExamRecordServiceImpl.java`
> - AI 方向重点读 `teacher_agent.py`、`student_agent.py`、`rag/retriever.py`
> - 两个方向都重点理解**跨服务的 JWT 共享**和**考试答题完整流程**
