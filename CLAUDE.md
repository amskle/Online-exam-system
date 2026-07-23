# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

在线考试系统 (Online Exam System) — three cooperating services:

- **exam-backend/** — Spring Boot 3.2 / Java 21 / MyBatis-Plus / MySQL / Redis, port **8077**. All API paths are under `/api/**`. Main class: `com.example.onlineexamsystem.OnlineExamSystemApplication`.
- **exam-frontend/** — Vue 3 + TypeScript + Vite + Element Plus + Pinia + ECharts. Dev server on port **8076**, binds `0.0.0.0`.
- **ai-tutor/** — FastAPI microservice (port **8080**), a RAG + LangGraph "智能学习伙伴" with two agents: teacher question-generation and student Q&A. Uses DeepSeek API (OpenAI-compatible) for both LLM and embeddings. Integrated into the frontend via `src/components/FloatingTutor.vue` and `src/api/tutor-api.ts`.

`nginx.conf` (repo root) shows the production topology: `/api/` → exam-backend, `/ai/` → ai-tutor, everything else → frontend SPA served from `/usr/share/nginx/html`.

## Common commands

```bash
# Backend (requires MySQL 8.x on localhost:3306 with database `exam`, and Redis on localhost:6379)
cd exam-backend
mvn spring-boot:run                  # run dev server
mvn -q -DskipTests compile           # compile check
mvn test                             # run all tests
mvn test -Dtest=ClassName            # run a single test class

# Frontend
cd exam-frontend
npm run dev                          # Vite dev server on :8076
npm run build                        # type-check + production build
npx vue-tsc --ignoreDeprecations 5.0 --noEmit   # type check only

# AI tutor (Python >= 3.12)
cd ai-tutor
pip install -r requirements.txt
cp .env.example .env                 # create and edit .env with your API keys
python main.py                       # runs on :8080
pytest tests/ -v                     # run all tests
pytest tests/ -v -m "not integration"  # skip integration tests (require valid JWT + LLM)
pytest tests/test_api.py::TestHealthCheck -v  # run a single test class
```

## Backend architecture

- **Auth model:** JWT via `interceptor/JwtInterceptor` + `annotation/Auth.java`. The `@Auth` annotation requires an explicit role value (no default): `@Auth(3)` = admin only; `@Auth({1,2})` = student or teacher. Routes without `@Auth` are public. Role values: **1 = student, 2 = teacher, 3 = admin** (same values in DB, backend, and frontend `RoleEnum`). Current user is available through `utils/UserContext` (ThreadLocal).
- **Standard layers:** `controller/` → `service/` + `service/impl/` → `mapper/` (pure MyBatis-Plus `BaseMapper` interfaces — **no XML mapper files**; the `mybatis-plus.mapper-locations` config resolves to nothing). DTOs in `pojo/dto`, VOs in `pojo/vo`, entities in `pojo/entity`. Uniform response wrapper `pojo/api/Result` + `ResultCode`; business errors throw `common/exception/BusinessException`, handled by `GlobalException`.
- **Key config classes:** `CorsConfig` (CORS), `FileUploadConfig` (binds `file.upload-dir`), `MybatisPlusConfig` (pagination plugin), `PasswordConfig` (BCrypt via `spring-security-crypto` — note: not full Spring Security), `WebConfig` (registers `JwtInterceptor` on `/api/**`).
- **Schema management:** `application.yml` sets `spring.sql.init.mode: always` with `schema-admin.sql` (idempotent DDL), and `config/DatabaseMigrationRunner` runs incremental DDL changes (e.g. `schema-update-20260707.sql` which added `max_attempts` to `exam_paper` and `attempt_count` to `exam_record`) plus seeds the 408 CS question bank (seed script: `exam-backend/scripts/generate_408_seed.py`, output: `data-408.sql`; audit report at `data-408-report.json`). New tables/columns should follow this pattern — don't hand-edit a live DB.
- **Email verification:** registration uses QQ SMTP (`spring.mail`) with codes stored in Redis (`utils/RedisUtil`, TTL/limits under `auth.*` in application.yml: `email-code-ttl: 5m`, `email-send-cooldown: 60s`, `email-daily-limit: 10`). Mail password comes from `MAIL_AUTH_CODE` in `exam-backend/.env`, loaded via the `spring-dotenv` dependency. A clean config template is at `src/main/resources/application.example` — new developers should copy and customize this.
- **Connection pool:** HikariCP (Spring Boot default), configured in `application.yml` under `spring.datasource.hikari` (max 20 connections, 30s timeout).
- **File uploads** land in `exam-backend/file/` (runtime dir, git-ignored), configurable via `file.upload-dir` in application.yml.

### Key business rules (from README, enforced in service layer)

- Retaking a paper **overwrites** the previous exam record and answers, and increments the attempt count; students are blocked once a paper's attempt limit is reached.
- Objective questions (single/multi/judge) are auto-graded; subjective questions require teacher/admin grading (`ExamRecordController` grading endpoints).
- Wrong objective answers are recorded into the student's 错题集 (wrong-question book).

## Frontend architecture

- `src/router/index.ts` is the authorization source of truth: route `meta.requiresAuth` + `meta.roles` drive the guard; unauthorized → `/401`, unknown → `/404`. Login redirects by role: admin → `/admin-home/dashboards`, teacher → `/admin-home/questions`, student → `/user-home/dashboards`. Teachers share the admin layout but must only see question/paper/exam-record management. Notable routes: `/email-verify` (post-registration email verification, no auth required), `/exam/:id` (the live exam-taking page). There are two student-home paths — `/user_home` (redirect-only stub) and `/user-home` (actual layout with children).
- `src/utils/request.ts` — axios instance, `baseURL` from `VITE_API_BASE_URL` (dev default `http://localhost:8077`), `timeout: 15000`, `withCredentials: true`. AI calls use a separate base URL `VITE_AI_BASE_URL` (default `http://localhost:8080/ai`).
- Element Plus components and APIs are **auto-imported** (`unplugin-auto-import` + `unplugin-vue-components`); `components.d.ts` is generated — don't edit it by hand, and don't add manual imports for Element Plus components.
- **Token/role storage:** Token, role, and role-name are stored in **sessionStorage** (not localStorage) — each browser tab is isolated, so different tabs can use different accounts. The only exception is "remember me" (account name), which uses localStorage. Use the helpers in `src/utils/localStorage.ts` (`getToken`, `getRole`, `RoleEnum`, `clearAllAuth`, etc.) rather than reading storage directly.
- **Path alias:** `@` maps to `src/` (configured in `vite.config.ts`).
- **SVG handling:** `vite-svg-loader` plugin imports `.svg` files as Vue components.
- **Env vars:** `VITE_API_BASE_URL`, `VITE_AI_BASE_URL`, `VITE_DEBUG` (set in `.env.development` / `.env.production`). In production, `VITE_AI_BASE_URL` is deliberately left unset — `tutor-api.ts` falls back to `'/ai'` and nginx proxies it to the ai-tutor service.

## ai-tutor architecture

- **JWT is shared with Spring Boot:** `config/settings.py` uses the same `jwt_secret` to verify tokens itself (`utils/jwt_util.py`), then forwards the user's token to the backend through `utils/exam_bridge.py` (`ExamBridge` — httpx calls into `/api/**` with a shared AsyncClient), so the AI acts with the caller's own permissions. Changing the JWT secret requires updating both services.
- **LLM provider:** DeepSeek API (OpenAI-compatible) via the `openai` Python library. Configured in `config/settings.py` / `.env`: `llm_api_base`, `llm_api_key`, `llm_model` (default `deepseek-chat`). Embeddings default to the same provider but **can use a different one** (e.g. `embedding_api_base` pointed at SiliconFlow with `BAAI/bge-large-zh-v1.5`) — the two are configured independently.
- **RAG pipeline:** `rag/` (document_loader → embeddings → vector_store → retriever) backed by SQLite (`vector_store.db`) + numpy — no external vector DB. Embeddings are async with an in-memory LRU query cache; semantic retrieval auto-degrades to keyword search (with truncation logging) when the API is unavailable.
- **Agents:** `agents/teacher_agent.py` and `agents/student_agent.py` are LangGraph state graphs with conditional edges — nodes set `fatal_error` to short-circuit on LLM failure instead of running blind. Shared async LLM client and JSON extraction helpers live in `agents/common.py`.
  - **Teacher agent:** requirement understanding → retrieval → batch generation (count loop with per-question deterministic validation, `max_tokens`, and retry rounds) → LLM quality check (failure → warning + original preserved) → save via `ExamBridge` with per-question error capture (partial failures reported as `failed_questions`).
  - **Student agent:** server-side context loading (fetches question content + correct answer from backend via `get_wrong_question` so the client never sends answers) → weakness analysis → knowledge retrieval → Socratic planning → reply generation → double leak check: deterministic regex patterns (choice answers / true-false / subjective) first, LLM re-check second; either triggers safe regeneration without the answer in context.
- **Session store:** `utils/session_store.py` — SQLite-backed chat history keyed by `(session_id, user_id)`. Students no longer send raw conversation history from the client (eliminates prompt injection). `POST /ai/student/ask` returns `session_id`; `POST /ai/student/session/clear` resets it.
- **Logging:** `logging` configured at INFO in `main.py`; retriever downgrades, quality-check failures, and leak rewrites all log their events.
- **Configurable agent parameters** (in `config/settings.py`):
  - `llm_timeout: 60.0` — seconds before LLM call times out
  - `generate_batch_size: 5` — max questions per LLM generation call
  - `generate_max_attempts: 4` — max retry rounds when question count is insufficient
  - `session_history_limit: 12` — conversation turns injected into the agent prompt
  - `session_max_messages: 50` — max messages retained per session in SQLite
  - `retrieval_top_k: 5` — number of RAG chunks retrieved per query
