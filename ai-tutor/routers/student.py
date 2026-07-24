"""学生智能体 API 路由 — 答疑、推荐、状态查询、会话管理"""
import asyncio
import json
import logging

from fastapi import APIRouter, Depends, HTTPException, Header
from fastapi.responses import StreamingResponse

from models.schemas import (
    ApiResponse,
    StudentAskRequest,
    SessionClearRequest,
)
from agents.common import chat_text
from agents.student_agent import student_graph
from utils.jwt_util import verify_token, get_user_id, ROLE_STUDENT
from utils.exam_bridge import exam_bridge
from utils.session_store import session_store
from config.settings import get_settings

router = APIRouter()
settings = get_settings()
logger = logging.getLogger("ai-tutor.router.student")


# ── 权限依赖 ──

async def require_student(authorization: str = Header(...)):
    """仅允许学生访问"""
    token = authorization.removeprefix("Bearer ").strip()
    claims = verify_token(token)
    if not claims:
        raise HTTPException(status_code=401, detail="无效的认证令牌")
    role = claims.get("role")
    if role != ROLE_STUDENT:
        raise HTTPException(status_code=403, detail="仅学生可访问")
    return token, claims


# ── 接口 ──

@router.get("/status", response_model=ApiResponse)
async def check_status(auth=Depends(require_student)):
    """查询学生智能体可用状态。考试中不可用。"""
    token, claims = auth

    active_exam = await exam_bridge.get_active_exam(token)
    if active_exam:
        return ApiResponse(
            code=200,
            message="当前正在考试中，智能伙伴暂不可用",
            data={"available": False, "reason": "正在考试中，请完成考试后再使用学习伙伴"},
        )

    return ApiResponse(
        code=200,
        message="智能伙伴可用",
        data={"available": True, "reason": None},
    )


@router.post("/recommend", response_model=ApiResponse)
async def recommend(auth=Depends(require_student)):
    """主动推荐复习任务。基于错题集分析学生的薄弱知识点。"""
    token, claims = auth

    active_exam = await exam_bridge.get_active_exam(token)
    if active_exam:
        return ApiResponse(code=403, message="考试中不可用", data=None)

    try:
        wrong_data = await exam_bridge.get_wrong_questions(token, page=1, size=50)
    except Exception:
        wrong_data = {}

    prompt = f"""你是学习分析助手。根据学生的错题数据，分析薄弱知识点并给出复习建议。

错题数据: {json.dumps(wrong_data, ensure_ascii=False, default=str)[:1500]}

请输出:
1. 一段鼓励性的话（如 "你最近在XX知识点上进步很大！不过在YY方面..."）
2. 识别2-3个薄弱知识点
3. 给出一条具体的复习建议

回复长度不超过150字，语气亲切。"""

    try:
        suggestion = await chat_text(prompt, temperature=0.7, max_tokens=256)
    except Exception as e:
        logger.warning("学生推荐LLM调用失败: %s", e)
        suggestion = "最近学得很认真！继续查漏补缺，多回顾错题，你会越来越棒 💪"

    # 统计薄弱科目
    weak_points = []
    records = wrong_data.get("data", {}).get("records", []) if isinstance(wrong_data.get("data"), dict) else []
    subject_counts: dict[str, int] = {}
    for r in records[:50]:
        subj = r.get("subjectName", "未知")
        subject_counts[subj] = subject_counts.get(subj, 0) + 1
    for subj, count in sorted(subject_counts.items(), key=lambda x: -x[1])[:3]:
        weak_points.append({"concept": subj, "error_count": count})

    return ApiResponse(
        code=200,
        message="推荐成功",
        data={"message": suggestion, "weak_points": weak_points},
    )


@router.post("/ask", response_model=ApiResponse)
async def ask_question(
    req: StudentAskRequest,
    auth=Depends(require_student),
):
    """
    学生答疑。
    使用 Socratic 引导式回答，绝不直接给出答案。
    完整走 LangGraph 流水线：上下文加载 → 问题理解 → 检索 → Socratic规划 → 生成 → 泄露检查。
    对话历史由服务端会话存储管理，request 只需传 session_id。
    """
    token, claims = auth
    user_id = claims.get("sub") or claims.get("userId") or 0

    # 考试中禁止使用
    active_exam = await exam_bridge.get_active_exam(token)
    if active_exam:
        return ApiResponse(
            code=403,
            message="正在考试中，学习伙伴暂不可用",
            data={"reply": "考试期间我不能帮你哦，请专心完成考试！加油💪", "session_id": req.session_id or ""},
        )

    # 会话管理
    sid = req.session_id or session_store.new_session_id()
    session_store.ensure_session(sid, user_id, 'student', req.message[:50])
    history = session_store.history(sid, user_id)

    state = {
        "question_id": req.question_id,
        "question_content": req.question_content,
        "student_answer": req.student_answer or "",
        "correct_answer": "",  # 由 load_context 节点从后端加载
        "message": req.message,
        "conversation_history": history,
        "token": token,
        "weakness_analysis": "",
        "knowledge_context": "",
        "socratic_plan": "",
        "draft_reply": "",
        "final_reply": "",
        "hints": [],
        "related_concepts": [],
        "contains_answer": False,
        "warnings": [],
        "fatal_error": "",
    }

    try:
        result: dict = await student_graph.ainvoke(state)
    except Exception as e:
        logger.exception("学生智能体执行异常")
        raise HTTPException(status_code=500, detail=f"智能体执行失败: {e!s}")

    if result.get("fatal_error"):
        return ApiResponse(code=500, message=result["fatal_error"], data=None)

    # 持久化本轮对话
    session_store.append(sid, user_id, "user", req.message)
    session_store.append(sid, user_id, "assistant", result["final_reply"])

    return ApiResponse(
        code=200,
        message="答疑成功" if not result.get("contains_answer") else "答疑成功（经过答案泄露检查并修正）",
        data={
            "reply": result["final_reply"],
            "hints": result.get("hints", []),
            "related_concepts": result.get("related_concepts", []),
            "session_id": sid,
        },
    )


@router.post("/ask/stream")
async def ask_question_stream(
    req: StudentAskRequest,
    auth=Depends(require_student),
):
    """
    学生答疑 — SSE 流式版。
    使用 LangGraph astream(updates) 逐节点推送进度和 LLM token，提供实时打字体验。
    """
    token, claims = auth
    user_id = claims.get("sub") or claims.get("userId") or 0

    # 考试中禁止使用
    active_exam = await exam_bridge.get_active_exam(token)
    if active_exam:
        async def _blocked():
            yield f"data: {json.dumps({'type': 'error', 'message': '考试期间我不能帮你哦，请专心完成考试！加油💪'})}\n\n"
            yield "data: [DONE]\n\n"
        return StreamingResponse(_blocked(), media_type="text/event-stream")

    # 会话管理
    sid = req.session_id or session_store.new_session_id()
    session_store.ensure_session(sid, user_id, 'student', req.message[:50])
    history = session_store.history(sid, user_id)

    state = {
        "question_id": req.question_id,
        "question_content": req.question_content,
        "student_answer": req.student_answer or "",
        "correct_answer": "",
        "message": req.message,
        "conversation_history": history,
        "token": token,
        "weakness_analysis": "",
        "knowledge_context": "",
        "socratic_plan": "",
        "draft_reply": "",
        "final_reply": "",
        "hints": [],
        "related_concepts": [],
        "contains_answer": False,
        "warnings": [],
        "fatal_error": "",
    }

    async def event_generator():
        status_map = {
            "load_context": "正在加载题目上下文…",
            "understand": "正在分析你的薄弱点…",
            "retrieve": "正在检索相关知识点…",
            "plan": "正在设计引导方案…",
            "generate": "正在生成回复…",
            "check": "正在检查答案安全性…",
        }
        draft_reply = ""
        final_reply = ""

        try:
            async for chunk in student_graph.astream(state, stream_mode="updates"):
                for node_name, node_output in chunk.items():
                    # 推送节点状态
                    status_text = status_map.get(node_name)
                    if status_text:
                        yield f"data: {json.dumps({'type': 'status', 'text': status_text})}\n\n"

                    # generate 节点完成后，模拟逐字流式输出
                    if node_name == "generate":
                        draft_reply = node_output.get("draft_reply", "")
                        if draft_reply:
                            # 按字符分组发送，模拟打字效果（中文每字符、英文每词）
                            buffer = ""
                            for ch in draft_reply:
                                buffer += ch
                                # 中文/标点逐字发送，英文单词按空格发送
                                if ch in "，。！？；：、\n" or (ch == " " and len(buffer) > 1):
                                    yield f"data: {json.dumps({'type': 'token', 'text': buffer})}\n\n"
                                    await asyncio.sleep(0.01)  # 微小延迟，给前端渲染时间
                                    buffer = ""
                            if buffer:
                                yield f"data: {json.dumps({'type': 'token', 'text': buffer})}\n\n"

                    # check 节点完成后，获取最终回复
                    if node_name == "check":
                        final_reply = node_output.get("final_reply", "")
                        hints = node_output.get("hints", [])
                        concepts = node_output.get("related_concepts", [])
                        contains_answer = node_output.get("contains_answer", False)

                        # 如果泄露检测重写了回复，推送替换事件
                        if final_reply and final_reply != draft_reply:
                            yield f"data: {json.dumps({'type': 'rewrite', 'text': final_reply})}\n\n"

                        yield f"data: {json.dumps({
                            'type': 'final',
                            'reply': final_reply,
                            'hints': hints,
                            'related_concepts': concepts,
                            'session_id': sid,
                            'contains_answer': contains_answer,
                        })}\n\n"

            # 持久化本轮对话
            session_store.append(sid, user_id, "user", req.message)
            session_store.append(sid, user_id, "assistant", final_reply)

            yield "data: [DONE]\n\n"

        except Exception as e:
            logger.exception("流式答疑执行异常")
            yield f"data: {json.dumps({'type': 'error', 'message': f'智能体执行失败: {e!s}'})}\n\n"
            yield "data: [DONE]\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/session/clear", response_model=ApiResponse)
async def clear_session(
    req: SessionClearRequest,
    auth=Depends(require_student),
):
    """清空指定会话的对话历史"""
    token, claims = auth
    user_id = claims.get("sub") or claims.get("userId") or 0
    session_store.clear(req.session_id, user_id)
    return ApiResponse(code=200, message="会话已清空", data={"session_id": req.session_id})


@router.get("/sessions", response_model=ApiResponse)
async def list_student_sessions(auth=Depends(require_student)):
    """列出当前学生的答疑会话历史，按最近更新时间倒序"""
    _, claims = auth
    user_id = claims.get("sub") or claims.get("userId") or 0
    sessions = session_store.list_sessions(user_id, agent_mode='student')
    return ApiResponse(code=200, message="成功", data=sessions)


@router.delete("/sessions/{session_id}", response_model=ApiResponse)
async def delete_student_session(session_id: str, auth=Depends(require_student)):
    """删除指定会话及其所有消息"""
    _, claims = auth
    user_id = claims.get("sub") or claims.get("userId") or 0
    session_store.delete_session(session_id, user_id)
    return ApiResponse(code=200, message="会话已删除", data={"session_id": session_id})


@router.get("/sessions/{session_id}", response_model=ApiResponse)
async def get_student_session(session_id: str, auth=Depends(require_student)):
    """获取单个会话的完整消息历史"""
    _, claims = auth
    user_id = claims.get("sub") or claims.get("userId") or 0
    session = session_store.get_session(session_id, user_id)
    if not session:
        raise HTTPException(status_code=404, detail="会话不存在")
    return ApiResponse(code=200, message="成功", data=session)
