"""教师智能体 API 路由 — 出题、推荐、文档上传"""
import logging
import hashlib

from fastapi import APIRouter, Cookie, Depends, HTTPException, Header, UploadFile, File, Form

from models.schemas import (
    ApiResponse,
    TeacherGenerateRequest,
    TeacherChatRequest,
    TeacherChatData,
    DocumentUploadData,
)
from agents.common import chat_text, TYPE_NAMES, DIFF_NAMES
from agents.teacher_agent import teacher_graph
from config.settings import get_settings
from utils.jwt_util import verify_token, ROLE_TEACHER, ROLE_ADMIN
from utils.exam_bridge import exam_bridge
from utils.session_store import session_store
from rag.document_loader import DocumentLoader
from rag.embeddings import embedding_service
from rag.query_rewriter import query_rewrite_memory
from rag.vector_store import vector_store
from rag.retriever import retriever
from rag.sanitizer import REDACTED_CHUNK_TEXT, strip_answer_content, strip_answer_chunks
from rag.answering import answer_from_documents
from utils.observability import observe_agent_run, score_trace, update_observation
import uuid
import json
import os
import re
import time

router = APIRouter()
logger = logging.getLogger("ai-tutor.router.teacher")
settings = get_settings()


def _extract_user_id(claims: dict) -> int:
    """从 JWT claims 提取 user_id（Spring Boot 将 sub 存为字符串，需转为 int）"""
    raw = claims.get("sub") or claims.get("userId") or 0
    return int(raw)


def _looks_like_generate_request(message: str) -> bool:
    """判断教师聊天消息是否是出题请求。"""
    return ("生成" in message and "题" in message) or "出题" in message


def _parse_generate_request(message: str, fallback_subject: str | None = None) -> dict:
    """从自然语言中解析出题参数，解析不到时使用默认值或聊天当前科目。"""
    count_match = re.search(r"(\d+)\s*道", message)
    count = max(1, min(20, int(count_match.group(1)))) if count_match else 5

    if "判断" in message:
        question_type = 3
    elif "多选" in message:
        question_type = 2
    elif "主观" in message:
        question_type = 4
    elif "单选" in message:
        question_type = 1
    else:
        question_type = 1

    if "困难" in message:
        difficulty = 3
    elif "简单" in message:
        difficulty = 1
    elif "中等" in message:
        difficulty = 2
    else:
        difficulty = 2

    subject_name = fallback_subject
    explicit_match = re.search(r"(?:为|给)\s*(.+?)\s*科目", message)
    about_match = re.search(
        r"关于\s*([A-Za-z0-9]+|[\u4e00-\u9fff]{1,20}?)(?=的|难度|单选|多选|判断|主观|题)",
        message,
    )
    if explicit_match:
        subject_name = explicit_match.group(1).strip()
    elif subject_name is None and about_match:
        subject_name = about_match.group(1)

    return {
        "subject_name": subject_name,
        "question_type": question_type,
        "difficulty": difficulty,
        "count": count,
        "extra_requirement": message,
    }


async def _resolve_subject_id(token: str, subject_name: str) -> tuple[int | None, str]:
    """按名称精确或模糊匹配科目，返回 (subjectId, 规范化科目名)。"""
    subjects = await exam_bridge.get_subjects(token)
    lower = subject_name.strip().lower()

    exact = next((s for s in subjects if str(s.get("name", "")).strip().lower() == lower), None)
    if exact:
        return exact.get("id"), str(exact.get("name", subject_name))

    fuzzy = next(
        (
            s for s in subjects
            if lower in str(s.get("name", "")).lower() or str(s.get("name", "")).lower() in lower
        ),
        None,
    )
    if fuzzy:
        return fuzzy.get("id"), str(fuzzy.get("name", subject_name))
    return None, subject_name


async def _generate_and_build_reply(
    token: str,
    user_id: int,
    subject_id: int,
    subject_name: str,
    question_type: int,
    difficulty: int,
    count: int,
    extra_requirement: str = "",
    session_title: str = "",
    user_message: str = "",
) -> tuple[str, dict]:
    """运行完整出题流水线并保存会话，返回 (回复文本, 响应数据)。"""
    sid = session_store.new_session_id()
    state = {
        "subject_id": subject_id,
        "subject_name": subject_name,
        "question_type": question_type,
        "difficulty": difficulty,
        "count": count,
        "extra_requirement": extra_requirement,
        "token": token,
        "requirement_summary": "",
        "retrieved_docs": [],
        "generated_questions": [],
        "quality_checked": [],
        "saved_ids": [],
        "failed_questions": [],
        "warnings": [],
        "fatal_error": "",
    }

    with observe_agent_run(
        "teacher.generate",
        user_id=user_id,
        session_id=sid,
        input={
            "subject_id": subject_id,
            "subject_name": subject_name,
            "question_type": question_type,
            "difficulty": difficulty,
            "count": count,
            "extra_requirement": extra_requirement,
        },
        tags=["ai-tutor", "langgraph", "teacher"],
        metadata={"workflow": "question-generation"},
    ) as observation:
        try:
            result: dict = await teacher_graph.ainvoke(state)
        except Exception as e:
            logger.exception("教师智能体执行异常")
            raise HTTPException(status_code=500, detail=f"智能体执行失败: {e!s}")

        if result.get("fatal_error"):
            update_observation(observation, output={"fatal_error": result["fatal_error"]})
            score_trace(observation, "graph_completion", 0.0)
            score_trace(observation, "quality_gate_passed", 0.0)
            raise HTTPException(status_code=500, detail=result["fatal_error"])

        saved_count = len(result.get("saved_ids", []))
        update_observation(
            observation,
            output={
                "questions": result.get("quality_checked", []),
                "saved_ids": result.get("saved_ids", []),
                "failed_questions": result.get("failed_questions", []),
            },
        )
        score_trace(observation, "graph_completion", 1.0)
        score_trace(observation, "quality_gate_passed", 1.0)
        score_trace(
            observation,
            "database_save_rate",
            saved_count / count if count else 0.0,
        )

    questions = result.get("quality_checked", result.get("generated_questions", []))
    saved_ids = result.get("saved_ids", [])
    warnings = result.get("warnings", [])

    type_name = TYPE_NAMES.get(question_type, "题目")
    diff_name = DIFF_NAMES.get(difficulty, "中等")
    default_title = f"{subject_name} · {type_name} · {diff_name} x{count}"
    session_store.ensure_session(sid, user_id, 'teacher', session_title or default_title)

    default_user_msg = f"为「{subject_name}」生成{count}道{type_name}（难度：{diff_name}）"
    if extra_requirement:
        default_user_msg += f"\n额外要求：{extra_requirement}"
    session_store.append(sid, user_id, "user", user_message or default_user_msg)

    assistant_msg = f"✅ 已生成并入库 {len(saved_ids)} 道题目！\n\n"
    question_list = questions[:len(saved_ids)] if len(questions) >= len(saved_ids) else questions
    for i, q in enumerate(question_list):
        q_content = q.get('content', '') if isinstance(q, dict) else getattr(q, 'content', '')
        q_answer = q.get('answer', '') if isinstance(q, dict) else getattr(q, 'answer', '')
        q_analysis = q.get('analysis', '') if isinstance(q, dict) else getattr(q, 'analysis', '')
        q_opts = q.get('options', None) if isinstance(q, dict) else getattr(q, 'options', None)
        idx = saved_ids[i] if i < len(saved_ids) else '?'
        assistant_msg += f"📝 第{i+1}题 (ID:{idx})\n{q_content}\n"
        if q_opts:
            try:
                opt_list = json.loads(q_opts) if isinstance(q_opts, str) else q_opts
                if isinstance(opt_list, list):
                    for j, o in enumerate(opt_list):
                        assistant_msg += f"  {chr(65+j)}. {o}\n"
            except (json.JSONDecodeError, TypeError):
                assistant_msg += f"  选项: {q_opts}\n"
        assistant_msg += f"  答案: {q_answer}\n  解析: {q_analysis}\n\n"

    if failed_list := result.get("failed_questions", []):
        assistant_msg += f"\n⚠️ {len(failed_list)} 道题入库失败"
    session_store.append(sid, user_id, "assistant", assistant_msg)

    data = {
        "questions": questions,
        "saved_ids": saved_ids,
        "failed_questions": result.get("failed_questions", []),
        "warnings": warnings,
        "session_id": sid,
    }
    return assistant_msg, data


# ── 权限依赖 ──

async def require_teacher_or_admin(
    authorization: str | None = Header(None),
    exam_token: str | None = Cookie(None, alias="exam_token"),
):
    """仅允许教师/管理员访问 — 优先取 Authorization 头，回退到 exam_token Cookie（与 Spring Boot 一致）"""
    token = None
    if authorization:
        token = authorization.removeprefix("Bearer ").strip()
    if not token and exam_token:
        token = exam_token

    if not token:
        raise HTTPException(status_code=401, detail="未提供认证令牌")

    claims = verify_token(token)
    if not claims:
        raise HTTPException(status_code=401, detail="无效的认证令牌")
    try:
        current_user = await exam_bridge.validate_auth(token)
    except Exception:
        logger.exception("后端认证服务不可用")
        raise HTTPException(status_code=503, detail="认证服务暂时不可用")
    if not current_user:
        raise HTTPException(status_code=401, detail="登录状态已失效")
    role = claims.get("role")
    if role not in (ROLE_TEACHER, ROLE_ADMIN):
        raise HTTPException(status_code=403, detail="仅教师和管理员可访问")
    claims["sub"] = str(current_user.get("id"))
    return token, claims


async def require_admin(auth=Depends(require_teacher_or_admin)):
    """全局破坏性操作仅允许管理员执行。"""
    token, claims = auth
    if claims.get("role") != ROLE_ADMIN:
        raise HTTPException(status_code=403, detail="仅管理员可执行此操作")
    return token, claims


# ── 接口 ──

@router.get("/subjects", response_model=ApiResponse)
async def list_subjects(auth=Depends(require_teacher_or_admin)):
    """获取所有科目列表，供前端下拉框使用"""
    token, claims = auth
    try:
        subjects = await exam_bridge.get_subjects(token)
        logger.info("返回 %d 个科目", len(subjects))
        return ApiResponse(code=200, message="成功", data=subjects)
    except Exception as e:
        logger.exception("获取科目列表失败")
        raise HTTPException(status_code=502, detail=f"无法获取科目列表: {e!s}")


@router.post("/generate", response_model=ApiResponse)
async def generate_questions(
    req: TeacherGenerateRequest,
    auth=Depends(require_teacher_or_admin),
):
    """
    按需生成题目。
    完整走 LangGraph 流水线：需求理解 → 检索 → 分批生成 → 质检 → 入库。
    """
    token, claims = auth
    user_id = _extract_user_id(claims)

    try:
        _, data = await _generate_and_build_reply(
            token=token,
            user_id=user_id,
            subject_id=req.subject_id,
            subject_name=req.subject_name,
            question_type=req.question_type,
            difficulty=req.difficulty,
            count=req.count,
            extra_requirement=req.extra_requirement or "",
        )
    except HTTPException as e:
        raise

    return ApiResponse(
        code=200,
        message=f"成功生成并入库 {len(data['saved_ids'])} 道题目",
        data=data,
    )


async def _handle_chat_generate(
    req: TeacherChatRequest,
    token: str,
    user_id: int,
) -> ApiResponse:
    """聊天中的自然语言出题：解析参数 → 走完整流水线 → 自动入库。"""
    parsed = _parse_generate_request(req.message, req.subject_name)
    subject_name = parsed["subject_name"]
    if not subject_name:
        return ApiResponse(
            code=400,
            message="未识别到科目",
            data=TeacherChatData(
                reply="请告诉我科目，例如：生成5道关于Java的单选题。",
                session_id="",
                sources=[],
            ),
        )

    subject_id, matched_subject = await _resolve_subject_id(token, subject_name)
    if subject_id is None:
        return ApiResponse(
            code=400,
            message=f"未找到科目「{subject_name}」",
            data=TeacherChatData(
                reply=f"未找到科目「{subject_name}」，请先在科目管理中创建该科目后再出题。",
                session_id="",
                sources=[],
            ),
        )

    try:
        reply, data = await _generate_and_build_reply(
            token=token,
            user_id=user_id,
            subject_id=subject_id,
            subject_name=matched_subject,
            question_type=parsed["question_type"],
            difficulty=parsed["difficulty"],
            count=parsed["count"],
            extra_requirement=parsed["extra_requirement"],
            session_title=req.message[:50],
            user_message=req.message,
        )
    except HTTPException as e:
        return ApiResponse(
            code=e.status_code,
            message=e.detail,
            data=TeacherChatData(reply=e.detail, session_id="", sources=[]),
        )

    return ApiResponse(
        code=200,
        message="成功",
        data=TeacherChatData(
            reply=reply,
            session_id=data["session_id"],
            sources=[],
        ),
    )


@router.post("/chat", response_model=ApiResponse)
async def chat(
    req: TeacherChatRequest,
    auth=Depends(require_teacher_or_admin),
):
    """
    教师知识库对话 — 基于已上传文档进行自由问答。
    语义检索知识库 → 构建上下文 → LLM 回答，不走出题流水线。
    """
    token, claims = auth
    user_id = _extract_user_id(claims)

    if _looks_like_generate_request(req.message):
        return await _handle_chat_generate(req, token, user_id)

    sid = req.session_id or session_store.new_session_id()
    history = session_store.history(sid, user_id)
    query_rewrite_memory.seed_from_messages(sid, history)
    query_history = query_rewrite_memory.recent_queries(sid)

    # ── 检索相关文档 ──
    try:
        docs = await retriever.retrieve(
            query=req.message,
            collection="teacher",
            top_k=5,
            subject_filter=req.subject_name or None,
            query_history=query_history,
            route_query=req.message,
        )
    except Exception as e:
        logger.warning("知识库检索失败: %s", e)
        docs = []

    # ── LLM 回答 ──
    try:
        reply = await answer_from_documents(req.message, docs)
    except Exception as e:
        logger.exception("教师对话 LLM 调用失败")
        raise HTTPException(status_code=502, detail=f"LLM 调用失败: {e!s}")

    # ── 保存会话历史 ──
    title = req.message[:50]
    session_store.ensure_session(sid, user_id, 'teacher', title)
    session_store.append(sid, user_id, "user", req.message)
    session_store.append(sid, user_id, "assistant", reply)
    query_rewrite_memory.remember(sid, req.message)

    # ── 构建引用来源 ──
    sources = [
        {
            "source_file": d["metadata"].get("source_file", ""),
            "question_index": d["metadata"].get("question_index", 0),
            "section_title": d["metadata"].get("section_title", ""),
            "section_path": d["metadata"].get("section_path", ""),
            "format": d["metadata"].get("format", ""),
            "preview": d["document"][:120],
        }
        for d in docs
    ]

    return ApiResponse(
        code=200,
        message="成功",
        data=TeacherChatData(
            reply=reply,
            session_id=sid,
            sources=sources,
        ),
    )


@router.post("/upload", response_model=ApiResponse)
async def upload_document(
    file: UploadFile = File(...),
    subject_name: str = Form(...),
    last_modified: float | None = Form(default=None),
    auth=Depends(require_teacher_or_admin),
):
    """
    上传 PDF/TXT/MD/DOCX/PPTX 文档入库。
    文档先识别格式与结构类型，再按对应 Pipeline 分块，分别写入 teacher_kb 和 student_kb。
    学生库使用剥离答案后的文本重新向量化，确保向量与内容一致。
    """
    token, claims = auth

    ext = file.filename.rsplit(".", 1)[-1].lower() if file.filename else "txt"
    if ext not in {"pdf", "txt", "md", "docx", "pptx"}:
        raise HTTPException(status_code=400, detail="仅支持 PDF、TXT、MD、DOCX、PPTX 文件")
    subject_name = subject_name.strip()
    if not subject_name or len(subject_name) > 100:
        raise HTTPException(status_code=400, detail="科目名称不能为空且不能超过100个字符")
    tmp_path = f"./upload_temp_{uuid.uuid4().hex}.{ext}"
    max_bytes = settings.max_upload_mb * 1024 * 1024

    try:
        total_bytes = 0
        with open(tmp_path, "wb") as f:
            while chunk := await file.read(1024 * 1024):
                total_bytes += len(chunk)
                if total_bytes > max_bytes:
                    raise HTTPException(
                        status_code=413,
                        detail=f"文件大小不能超过 {settings.max_upload_mb}MB",
                    )
                f.write(chunk)
        now = time.time()
        modified_at = last_modified / 1000.0 if last_modified else now
        result = DocumentLoader.load_and_chunk_detail(
            tmp_path,
            subject_name=subject_name,
            modified_at=modified_at,
            uploaded_at=now,
            source_name=file.filename or tmp_path,
        )
        chunks = result.chunks
        if not chunks:
            return ApiResponse(code=400, message="文档中未检测到有效内容", data=None)

        upload_id = uuid.uuid4().hex
        records: dict[str, tuple[str, str, dict]] = {}
        dropped_empty = 0
        student_texts = strip_answer_chunks(
            [chunk.content.strip() for chunk in chunks],
            [chunk.metadata.get("section_path", "") for chunk in chunks],
        )
        for chunk, student_text in zip(chunks, student_texts):
            full_text = chunk.content.strip()
            if not student_text:
                dropped_empty += 1
                # Preserve the complete teacher corpus and the paired student ID.
                # The retriever excludes this non-content placeholder.
                student_text = REDACTED_CHUNK_TEXT
            normalized = " ".join(full_text.split())
            digest = hashlib.sha256(
                f"{subject_name}\0{normalized}".encode("utf-8")
            ).hexdigest()[:32]
            if digest not in records:
                metadata = dict(chunk.metadata)
                metadata.update({"content_hash": digest, "upload_id": upload_id})
                records[digest] = (full_text, student_text, metadata)

        if not records:
            return ApiResponse(code=400, message="清洗后没有可安全入库的内容", data=None)

        duplicate_count = len(chunks) - len(records)
        if dropped_empty:
            result.warnings.append(f"学生侧已屏蔽 {dropped_empty} 个答案或解析块，教师原文已保留")
        if duplicate_count:
            result.warnings.append(f"已合并 {duplicate_count} 个重复知识块")

        digests = list(records)
        full_texts = [records[digest][0] for digest in digests]
        student_texts = [records[digest][1] for digest in digests]
        metadatas = [records[digest][2] for digest in digests]
        teacher_ids = [f"teacher_{digest}" for digest in digests]
        student_ids = [f"student_{digest}" for digest in digests]

        # EmbeddingService 内部分批；学生文本单独向量化，避免答案影响学生侧向量。
        full_embeddings = await embedding_service.embed(full_texts)
        student_embeddings = await embedding_service.embed(student_texts)
        write_stats = vector_store.add_consistent_pairs(
            teacher_ids=teacher_ids,
            student_ids=student_ids,
            teacher_documents=full_texts,
            student_documents=student_texts,
            teacher_embeddings=full_embeddings,
            student_embeddings=student_embeddings,
            metadatas=metadatas,
        )
        if write_stats["deduplicated"]:
            result.warnings.append(
                f"向量库中已有 {write_stats['deduplicated']} 个相同知识块，未重复写入"
            )

        return ApiResponse(
            code=200,
            message="文档入库成功",
            data=DocumentUploadData(
                file_name=file.filename,
                subject_name=subject_name,
                chunk_count=len(records),
                format=result.format,
                structure_type=result.structure_type,
                chunking_strategy=result.chunking_strategy,
                warnings=result.warnings,
                message=(
                    f"双库一致性写入完成：新增/修复 {write_stats['inserted']} 个，"
                    f"复用 {write_stats['deduplicated']} 个"
                ),
            ),
        )
    except HTTPException:
        raise
    except ValueError as e:
        logger.warning("文档格式或内容处理失败: %s", e)
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.exception("文档处理失败")
        raise HTTPException(status_code=500, detail=f"文档处理失败: {e!s}")
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


@router.delete("/knowledge", response_model=ApiResponse)
async def clear_knowledge_base(auth=Depends(require_admin)):
    """
    清空教师/学生知识库（ChromaDB 两个 collection 全部删除并重建）。
    用于重新上传文档前清除旧数据。
    """
    try:
        before_t = vector_store.count_documents("teacher")
        before_s = vector_store.count_documents("student")
        vector_store.clear_teacher()
        vector_store.clear_student()
        logger.info("知识库已清空（教师库 %d 条，学生库 %d 条）", before_t, before_s)
        return ApiResponse(
            code=200,
            message=f"知识库已清空",
            data={"teacher_deleted": before_t, "student_deleted": before_s},
        )
    except Exception as e:
        logger.exception("清空知识库失败")
        raise HTTPException(status_code=500, detail=f"清空知识库失败: {e!s}")


def _strip_answer(text: str) -> str:
    """兼容旧调用；实际清洗逻辑集中在 rag.sanitizer。"""
    return strip_answer_content(text)


def _format_source_label(metadata: dict) -> str:
    """生成用于 LLM 上下文和前端来源展示的文件位置标签。"""
    source = metadata.get("source_file", "")
    subject = metadata.get("subject", "")
    subject_part = f"[{subject}] " if subject else ""
    if metadata.get("section_path"):
        return f"《{source}》{subject_part}{metadata['section_path']}"
    if metadata.get("section_title"):
        return f"《{source}》{subject_part}{metadata['section_title']}"
    question_index = metadata.get("question_index", 0)
    if question_index:
        return f"《{source}》{subject_part}第{question_index}题"
    return f"《{source}》{subject_part}".strip()


# ── 会话历史 ──


@router.get("/sessions", response_model=ApiResponse)
async def list_teacher_sessions(auth=Depends(require_teacher_or_admin)):
    """列出当前教师的出题历史会话，按最近更新时间倒序"""
    _, claims = auth
    user_id = _extract_user_id(claims)
    sessions = session_store.list_sessions(user_id, agent_mode='teacher')
    return ApiResponse(code=200, message="成功", data=sessions)


@router.delete("/sessions/{session_id}", response_model=ApiResponse)
async def delete_teacher_session(session_id: str, auth=Depends(require_teacher_or_admin)):
    """删除指定出题历史会话"""
    _, claims = auth
    user_id = _extract_user_id(claims)
    session_store.delete_session(session_id, user_id)
    return ApiResponse(code=200, message="会话已删除", data={"session_id": session_id})


@router.get("/sessions/{session_id}", response_model=ApiResponse)
async def get_teacher_session(session_id: str, auth=Depends(require_teacher_or_admin)):
    """获取单个出题会话的完整记录"""
    _, claims = auth
    user_id = _extract_user_id(claims)
    session = session_store.get_session(session_id, user_id)
    if not session:
        raise HTTPException(status_code=404, detail="会话不存在")
    return ApiResponse(code=200, message="成功", data=session)
