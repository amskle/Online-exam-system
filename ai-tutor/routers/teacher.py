"""教师智能体 API 路由 — 出题、推荐、文档上传"""
import logging

from fastapi import APIRouter, Depends, HTTPException, Header, UploadFile, File, Form

from models.schemas import (
    ApiResponse,
    TeacherGenerateRequest,
    TeacherRecommendRequest,
    TeacherChatRequest,
    TeacherChatData,
    DocumentUploadData,
)
from agents.common import chat_text, TYPE_NAMES, DIFF_NAMES
from agents.teacher_agent import teacher_graph
from utils.jwt_util import verify_token, ROLE_TEACHER, ROLE_ADMIN
from utils.exam_bridge import exam_bridge
from utils.session_store import session_store
from rag.document_loader import DocumentLoader
from rag.embeddings import embedding_service
from rag.vector_store import vector_store
from rag.retriever import retriever
import uuid
import json
import os

router = APIRouter()
logger = logging.getLogger("ai-tutor.router.teacher")


# ── 权限依赖 ──

async def require_teacher_or_admin(authorization: str = Header(...)):
    """仅允许教师/管理员访问"""
    token = authorization.removeprefix("Bearer ").strip()
    claims = verify_token(token)
    if not claims:
        raise HTTPException(status_code=401, detail="无效的认证令牌")
    role = claims.get("role")
    if role not in (ROLE_TEACHER, ROLE_ADMIN):
        raise HTTPException(status_code=403, detail="仅教师和管理员可访问")
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


@router.post("/recommend", response_model=ApiResponse)
async def recommend(
    req: TeacherRecommendRequest,
    auth=Depends(require_teacher_or_admin),
):
    """
    主动推荐出题任务。
    分析题库缺口 → 给出推荐建议。
    """
    token, claims = auth

    try:
        stats = await exam_bridge.get_question_stats(token, req.subject_name)
    except Exception:
        stats = {}

    subject_hint = f"请专门针对 {req.subject_name} 科目" if req.subject_name else "请从全局题库出发"
    type_names = {1: "单选题", 2: "多选题", 3: "判断题", 4: "主观题"}
    stats_desc = ", ".join(f"{type_names.get(k, k)}:{v}" for k, v in stats.items() if isinstance(k, int) and v >= 0)
    prompt = f"""你是题库管理助手。{subject_hint}，根据以下统计信息推荐最需要补充的题目类型。

统计: {json.dumps(stats, ensure_ascii=False, default=str)[:800]}

请给出简短的推荐建议（不超过100字），说明应该补充什么题型、什么难度的题目。"""

    try:
        suggestion_message = await chat_text(prompt, temperature=0.7, max_tokens=256)
    except Exception as e:
        logger.warning("教师推荐LLM调用失败: %s", e)
        suggestion_message = f"建议补充{req.subject_name or '各科目'}的题目（LLM 暂时不可用: {e!s})"

    return ApiResponse(
        code=200,
        message="推荐成功",
        data={
            "message": suggestion_message,
            "suggestion": {"subject_name": req.subject_name or "全部", "recommended_count": 5},
        },
    )


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
    user_id = claims.get("sub") or claims.get("userId") or 0

    state = {
        "subject_id": req.subject_id,
        "subject_name": req.subject_name,
        "question_type": req.question_type,
        "difficulty": req.difficulty,
        "count": req.count,
        "extra_requirement": req.extra_requirement or "",
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

    try:
        result: dict = await teacher_graph.ainvoke(state)
    except Exception as e:
        logger.exception("教师智能体执行异常")
        raise HTTPException(status_code=500, detail=f"智能体执行失败: {e!s}")

    if result.get("fatal_error"):
        return ApiResponse(code=500, message=result["fatal_error"], data={
            "questions": result.get("generated_questions", []),
            "saved_ids": result.get("saved_ids", []),
            "failed_questions": result.get("failed_questions", []),
            "warnings": result.get("warnings", []),
        })

    questions = result.get("quality_checked", result.get("generated_questions", []))
    saved_ids = result.get("saved_ids", [])
    warnings = result.get("warnings", [])

    # ── 保存到历史会话 ──
    type_name = TYPE_NAMES.get(req.question_type, "题目")
    diff_name = DIFF_NAMES.get(req.difficulty, "中等")
    session_title = f"{req.subject_name} · {type_name} · {diff_name} x{req.count}"
    sid = session_store.new_session_id()
    session_store.ensure_session(sid, user_id, 'teacher', session_title)

    user_msg = f"为「{req.subject_name}」生成{req.count}道{type_name}（难度：{diff_name}）"
    if req.extra_requirement:
        user_msg += f"\n额外要求：{req.extra_requirement}"
    session_store.append(sid, user_id, "user", user_msg)

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

    return ApiResponse(
        code=200,
        message=f"成功生成并入库 {len(saved_ids)} 道题目",
        data={
            "questions": questions,
            "saved_ids": saved_ids,
            "failed_questions": result.get("failed_questions", []),
            "warnings": warnings,
            "session_id": sid,
        },
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
    user_id = claims.get("sub") or claims.get("userId") or 0

    # ── 检索相关文档 ──
    try:
        docs = await retriever.retrieve(
            query=req.message,
            collection="teacher",
            top_k=5,
            subject_filter=req.subject_name or None,
        )
    except Exception as e:
        logger.warning("知识库检索失败: %s", e)
        docs = []

    # ── 构建提示词 ──
    if docs:
        kb_context = "\n\n---\n".join(
            f"【来源：{d['metadata'].get('source_file','')} 第{d['metadata'].get('question_index','?')}题】\n{d['document']}"
            for d in docs
        )
        prompt = f"""你是一位学科助教，请根据以下知识库内容回答用户的问题。如果知识库中有相关内容，请准确引用；如果知识库内容不足以回答，请如实告知。

知识库内容：
{kb_context}

用户问题：{req.message}

请用中文回答，并尽量标注信息来源（文件名或题号）。"""
    else:
        prompt = f"""你是一位学科助教。用户上传过知识库文档，但目前知识库中没有检索到与以下问题相关的内容。

用户问题：{req.message}

请如实告知用户知识库中没有匹配的内容，并建议用户尝试上传相关文档或换个问题。"""

    # ── LLM 回答 ──
    try:
        reply = await chat_text(prompt, temperature=0.5, max_tokens=1024)
    except Exception as e:
        logger.exception("教师对话 LLM 调用失败")
        raise HTTPException(status_code=502, detail=f"LLM 调用失败: {e!s}")

    # ── 保存会话历史 ──
    sid = req.session_id or session_store.new_session_id()
    title = req.message[:50]
    session_store.ensure_session(sid, user_id, 'teacher', title)
    session_store.append(sid, user_id, "user", req.message)
    session_store.append(sid, user_id, "assistant", reply)

    # ── 构建引用来源 ──
    sources = [
        {
            "source_file": d["metadata"].get("source_file", ""),
            "question_index": d["metadata"].get("question_index", 0),
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
    auth=Depends(require_teacher_or_admin),
):
    """
    上传 PDF/TXT 文档入库。
    文档会被按题号分块，分别存入 teacher_kb（含完整内容）和 student_kb（剥离答案）。
    学生库使用剥离答案后的文本重新向量化，确保向量与内容一致。
    """
    token, claims = auth

    ext = file.filename.rsplit(".", 1)[-1].lower() if file.filename else "txt"
    tmp_path = f"./upload_temp_{uuid.uuid4().hex}.{ext}"
    content = await file.read()
    with open(tmp_path, "wb") as f:
        f.write(content)

    try:
        chunks = DocumentLoader.load_and_chunk(tmp_path, subject_name=subject_name)
        if not chunks:
            return ApiResponse(code=400, message="文档中未检测到有效题目内容", data=None)

        full_texts = [c.content for c in chunks]
        metadatas = [c.metadata for c in chunks]

        # ── 教师库：完整文本向量化并入库 ──
        full_embeddings = await embedding_service.embed(full_texts)
        teacher_ids = [f"teacher_{file.filename}_{i}" for i in range(len(chunks))]
        vector_store.add_to_teacher(teacher_ids, full_texts, full_embeddings, metadatas)

        # ── 学生库：剥离答案后单独向量化，确保向量与文本一致 ──
        student_texts = [_strip_answer(t) for t in full_texts]
        student_embeddings = await embedding_service.embed(student_texts)
        student_ids = [f"student_{file.filename}_{i}" for i in range(len(chunks))]
        vector_store.add_to_student(student_ids, student_texts, student_embeddings, metadatas)

        return ApiResponse(
            code=200,
            message="文档入库成功",
            data={
                "file_name": file.filename,
                "subject_name": subject_name,
                "chunk_count": len(chunks),
                "message": f"已将 {len(chunks)} 个题目块分别写入教师库和学生库",
            },
        )
    except Exception as e:
        logger.exception("文档处理失败")
        raise HTTPException(status_code=500, detail=f"文档处理失败: {e!s}")
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


@router.delete("/knowledge", response_model=ApiResponse)
async def clear_knowledge_base(auth=Depends(require_teacher_or_admin)):
    """
    清空教师/学生知识库（ChromaDB 两个 collection 全部删除并重建）。
    用于重新上传文档前清除旧数据。
    """
    try:
        before_t = len(vector_store.get_all_documents("teacher"))
        before_s = len(vector_store.get_all_documents("student"))
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
    """从题目文本中剥离答案信息，用于学生库"""
    import re
    text = re.sub(r'答案[：:]\s*[^\n]+', '', text)
    text = re.sub(r'正确答案[：:]\s*[^\n]+', '', text)
    text = re.sub(r'Answer[：:]\s*[^\n]+', '', text, flags=re.IGNORECASE)
    return text.strip()


# ── 会话历史 ──


@router.get("/sessions", response_model=ApiResponse)
async def list_teacher_sessions(auth=Depends(require_teacher_or_admin)):
    """列出当前教师的出题历史会话，按最近更新时间倒序"""
    _, claims = auth
    user_id = claims.get("sub") or claims.get("userId") or 0
    sessions = session_store.list_sessions(user_id, agent_mode='teacher')
    return ApiResponse(code=200, message="成功", data=sessions)


@router.delete("/sessions/{session_id}", response_model=ApiResponse)
async def delete_teacher_session(session_id: str, auth=Depends(require_teacher_or_admin)):
    """删除指定出题历史会话"""
    _, claims = auth
    user_id = claims.get("sub") or claims.get("userId") or 0
    session_store.delete_session(session_id, user_id)
    return ApiResponse(code=200, message="会话已删除", data={"session_id": session_id})


@router.get("/sessions/{session_id}", response_model=ApiResponse)
async def get_teacher_session(session_id: str, auth=Depends(require_teacher_or_admin)):
    """获取单个出题会话的完整记录"""
    _, claims = auth
    user_id = claims.get("sub") or claims.get("userId") or 0
    session = session_store.get_session(session_id, user_id)
    if not session:
        raise HTTPException(status_code=404, detail="会话不存在")
    return ApiResponse(code=200, message="成功", data=session)
