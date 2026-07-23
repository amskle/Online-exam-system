"""教师智能体 API 路由 — 出题、推荐、文档上传"""
import logging

from fastapi import APIRouter, Depends, HTTPException, Header, UploadFile, File, Form

from models.schemas import (
    ApiResponse,
    TeacherGenerateRequest,
    TeacherRecommendRequest,
    DocumentUploadData,
)
from agents.common import chat_text, TYPE_NAMES, DIFF_NAMES
from agents.teacher_agent import teacher_graph
from utils.jwt_util import verify_token, ROLE_TEACHER, ROLE_ADMIN
from utils.exam_bridge import exam_bridge
from rag.document_loader import DocumentLoader
from rag.embeddings import embedding_service
from rag.vector_store import vector_store
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

    return ApiResponse(
        code=200,
        message=f"成功生成并入库 {len(saved_ids)} 道题目",
        data={
            "questions": questions,
            "saved_ids": saved_ids,
            "failed_questions": result.get("failed_questions", []),
            "warnings": warnings,
        },
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


def _strip_answer(text: str) -> str:
    """从题目文本中剥离答案信息，用于学生库"""
    import re
    text = re.sub(r'答案[：:]\s*[^\n]+', '', text)
    text = re.sub(r'正确答案[：:]\s*[^\n]+', '', text)
    text = re.sub(r'Answer[：:]\s*[^\n]+', '', text, flags=re.IGNORECASE)
    return text.strip()
