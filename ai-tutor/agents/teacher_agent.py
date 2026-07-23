"""教师智能体 — LangGraph 状态机：需求理解 → 检索 → 分批生成 → 质量检查 → 入库"""
import json
import logging
from typing import TypedDict

from langgraph.graph import StateGraph, END

from agents.common import (
    TYPE_NAMES,
    DIFF_NAMES,
    chat_text,
    chat_json,
    extract_json,
    has_fatal,
)
from config.settings import get_settings
from rag.retriever import retriever
from utils.exam_bridge import exam_bridge

settings = get_settings()
logger = logging.getLogger("ai-tutor.agent.teacher")


# ── 状态定义 ──
class TeacherState(TypedDict):
    # 输入
    subject_id: int
    subject_name: str
    question_type: int  # 1单选 2多选 3判断 4主观
    difficulty: int  # 1简单 2中等 3困难
    count: int
    extra_requirement: str
    token: str

    # 中间产物
    requirement_summary: str
    retrieved_docs: list[dict]
    generated_questions: list[dict]
    quality_checked: list[dict]

    # 输出
    saved_ids: list[int]
    failed_questions: list[dict]  # [{ idx, reason }]
    warnings: list[str]
    fatal_error: str


# ── 确定性质检 ──

def _answer_letters(answer: str) -> set[str]:
    """从答案串中提取字母集合：单选 A → {A}，多选 [A,C] 或 A,C → {A,C}"""
    import re
    letters = set(re.findall(r'[A-D]', str(answer)))
    return letters


def _validate_single_question(q: dict, question_type: int, index: int) -> str | None:
    """返回校验失败消息，成功则 None"""
    content = q.get("content", "")
    if not content or len(content) < 4:
        return f"第{index + 1}题 content 过短或为空"
    answer = str(q.get("answer", ""))
    if not answer:
        return f"第{index + 1}题缺少 answer"

    if question_type == 1:  # 单选
        letters = _answer_letters(answer)
        if len(letters) != 1:
            return f"第{index + 1}题单选题 answer 应为单个字母"
        options = q.get("options")
        if isinstance(options, list) and list(letters)[0] not in {"A", "B", "C", "D"}:
            return f"第{index + 1}题 answer 字母无效"
    elif question_type == 2:  # 多选
        letters = _answer_letters(answer)
        if len(letters) < 1:
            return f"第{index + 1}题多选题 answer 应至少包含一个字母"
        if not letters.issubset({"A", "B", "C", "D"}):
            return f"第{index + 1}题 answer 包含无效字母"
    elif question_type == 3:  # 判断
        if answer not in ("正确", "错误"):
            return f"第{index + 1}题判断题 answer 应为'正确'或'错误'"

    return None


def _coerce_options(q: dict, question_type: int):
    """将 options 统一为 JSON 字符串（与后端 VARCHAR 字段对齐）"""
    opts = q.get("options")
    if isinstance(opts, list):
        q["options"] = json.dumps(opts, ensure_ascii=False)
    elif opts is None:
        q["options"] = json.dumps([], ensure_ascii=False) if question_type == 4 else None


# ── 节点函数 ──

async def understand_requirement(state: TeacherState) -> TeacherState:
    """节点1: 需求理解 — 将用户需求转为结构化的出题指引"""
    prompt = f"""你是一位资深出题专家。请理解以下出题需求，生成一段结构化的出题指引。
科目: {state["subject_name"]}
题型: {TYPE_NAMES.get(state["question_type"], "未知")}
难度: {DIFF_NAMES.get(state["difficulty"], "中等")}
数量: {state["count"]} 题
额外要求: {state.get("extra_requirement", "无")}

请输出一段不超过200字的出题指引，说明题目应该覆盖哪些知识点、怎样的表述风格。"""

    try:
        state["requirement_summary"] = await chat_text(prompt, temperature=0.7, max_tokens=256)
    except Exception as e:
        state["fatal_error"] = f"LLM 需求理解失败: {e!s}"
    return state


async def retrieve_references(state: TeacherState) -> TeacherState:
    """节点2: 相似检索 — 从教师知识库中找到参考题"""
    query = f"{state['subject_name']} {state['requirement_summary']}"
    try:
        docs = await retriever.retrieve(
            query=query,
            collection="teacher",
            top_k=state["count"] * 2,
            subject_filter=state["subject_name"],
        )
        state["retrieved_docs"] = docs
        if not docs:
            state["warnings"].append("教师知识库中未找到相似参考题，将完全依赖 LLM 生成")
    except Exception as e:
        logger.warning("教师检索失败: %s", e)
        state["warnings"].append(f"检索失败（不影响生成）: {e!s}")
        state["retrieved_docs"] = []
    return state


async def generate_questions(state: TeacherState) -> TeacherState:
    """节点3: 分批生成 — 每批 ≤ batch_size 道，不足时循环补全"""
    target = state["count"]
    batch_size = min(settings.generate_batch_size, target)
    all_questions: list[dict] = []
    remaining = target
    attempts = 0
    max_attempts = settings.generate_max_attempts

    type_name = TYPE_NAMES.get(state["question_type"], "题目")

    # 构建参考题上下文
    ref_text = ""
    if state["retrieved_docs"]:
        ref_text = "参考题目：\n" + "\n---\n".join(
            d["document"][:800] for d in state["retrieved_docs"]
        )

    while remaining > 0 and attempts < max_attempts:
        batch = min(batch_size, remaining)
        prompt = f"""你是一位经验丰富的出题老师。请根据以下指引生成 {batch} 道新的{type_name}。

出题指引: {state["requirement_summary"]}
科目: {state["subject_name"]}
难度: {state["difficulty"]} (1=简单, 2=中等, 3=困难)
这是第 {attempts + 1} 批，已生成 {len(all_questions)} 道，还需 {remaining} 道。请生成与已生成题不重复的新题。

{ref_text}

请严格按以下 JSON 数组格式输出:
[
  {{
    "content": "题目内容",
    "options": ["选项A","选项B","选项C","选项D"],
    "answer": "正确答案",
    "analysis": "题目解析",
    "score": 5
  }}
]
规则：
- 单选题: options 为4个选项字符串的数组，answer 为正确选项字母（A/B/C/D）
- 多选题: options 为4个选项字符串的数组，answer 为正确选项字母列表（如 ["A","C"]）
- 判断题: options 填 ["正确","错误"]，answer 为 "正确" 或 "错误"
- 主观题: options 填 []，answer 为参考答案要点"""

        try:
            result = await chat_json(prompt, temperature=0.8, max_tokens=4096)
            if not isinstance(result, list):
                raise ValueError("LLM 输出不是数组")
        except Exception as e:
            state["fatal_error"] = f"LLM 题目生成失败（第{attempts + 1}批）: {e!s}"
            return state

        # 逐题验证并保留有效的
        valid_batch = []
        for q in result:
            _coerce_options(q, state["question_type"])
            err = _validate_single_question(q, state["question_type"], len(all_questions) + len(valid_batch))
            if err:
                logger.warning("题目校验丢弃: %s — %s", err, json.dumps(q, ensure_ascii=False)[:200])
                state["warnings"].append(err)
            else:
                valid_batch.append(q)

        all_questions.extend(valid_batch)
        remaining = target - len(all_questions)
        attempts += 1

    if len(all_questions) < 1 and not state["fatal_error"]:
        state["fatal_error"] = f"经过 {attempts} 轮尝试后仍未生成有效题目"
    if 0 < len(all_questions) < target:
        state["warnings"].append(f"生成数量不足：目标 {target} 题，实际有效 {len(all_questions)} 题")

    state["generated_questions"] = all_questions
    return state


async def quality_check(state: TeacherState) -> TeacherState:
    """节点4: 质量检查 — LLM 自检生成的所有题目，失败降级保留原结果"""
    if not state.get("generated_questions"):
        return state

    prompt = f"""请检查以下生成的题目质量，找出明显错误，并修复。如果没问题就原样返回。

科目: {state["subject_name"]}
题目: {json.dumps(state["generated_questions"], ensure_ascii=False, indent=2)}

检查规则：
1. 单选题答案必须是单个大写字母(A/B/C/D)，且必须在 options 内
2. 多选题答案必须是大写字母数组，且都在 options 内
3. 判断题答案必须是"正确"或"错误"
4. 题目内容不能与参考题完全重复
5. 解析要解释为什么是这个答案

返回修复后的 JSON 数组:"""

    try:
        checked = await chat_json(prompt, temperature=0.3, max_tokens=4096)
        if isinstance(checked, list):
            # 对质检结果也跑确定性质检
            valid = []
            for i, q in enumerate(checked):
                _coerce_options(q, state["question_type"])
                err = _validate_single_question(q, state["question_type"], i)
                if err:
                    valid.append(state["generated_questions"][i] if i < len(state["generated_questions"]) else q)
                else:
                    valid.append(q)
            state["quality_checked"] = valid
        else:
            state["quality_checked"] = state["generated_questions"]
            state["warnings"].append("质检 LLM 未返回数组，保留原始题目")
    except Exception as e:
        logger.warning("质检失败，保留原始题目: %s", e)
        state["warnings"].append(f"质检失败（保留原始题目）: {e!s}")
        state["quality_checked"] = state["generated_questions"]

    return state


async def save_to_db(state: TeacherState) -> TeacherState:
    """节点5: 入库 — 通过 ExamBridge 逐题写入，记成功和失败"""
    import logging
    logger = logging.getLogger("ai-tutor.agent.teacher")

    subject_id = state.get("subject_id", 0)
    # 如果前端传了 0，尝试从名称解析
    if not subject_id:
        try:
            subject_id = await exam_bridge.get_subject_id(state["token"], state["subject_name"]) or 0
        except Exception as e:
            state["warnings"].append(f"科目查询失败: {e!s}")

    if not subject_id:
        state["warnings"].append(f"未找到科目「{state['subject_name']}」的ID，题目将挂到 subjectId=0")
    elif subject_id != state.get("subject_id", 0):
        state["warnings"].append(f"科目「{state['subject_name']}」的ID已从名称解析: {subject_id}")

    saved = []
    failed = []
    questions = state.get("quality_checked", state.get("generated_questions", []))

    for i, q in enumerate(questions):
        try:
            payload = {
                "subjectId": subject_id,
                "subjectName": state["subject_name"],
                "type": state["question_type"],
                "difficulty": state["difficulty"],
                "content": q.get("content", ""),
                "options": q.get("options"),
                "answer": str(q.get("answer", "")),
                "analysis": q.get("analysis", ""),
                "score": q.get("score", 5),
            }
            await exam_bridge.create_question(state["token"], payload)
            saved.append(i + 1)  # 用序号标识（后端 add 返回 Result<Void>）
        except Exception as e:
            msg = f"第{i + 1}题入库失败: {e!s}"
            failed.append({"index": i, "content_preview": q.get("content", "")[:50], "reason": msg})
            logger.warning(msg)

    state["saved_ids"] = saved
    state["failed_questions"] = failed

    if not saved and questions:
        state["fatal_error"] = f"全部 {len(questions)} 道题入库失败（Spring Boot 可能未启动）"
    elif failed:
        state["warnings"].append(f"{len(failed)}/{len(questions)} 道题入库失败，详情见 failed_questions")

    return state


# ── 构建 Graph ──

def build_teacher_graph() -> StateGraph:
    workflow = StateGraph(TeacherState)

    workflow.add_node("understand", understand_requirement)
    workflow.add_node("retrieve", retrieve_references)
    workflow.add_node("generate", generate_questions)
    workflow.add_node("check", quality_check)
    workflow.add_node("save", save_to_db)

    workflow.set_entry_point("understand")
    workflow.add_conditional_edges("understand", has_fatal, {"continue": "retrieve", "end": END})
    workflow.add_edge("retrieve", "generate")
    workflow.add_conditional_edges("generate", has_fatal, {"continue": "check", "end": END})
    workflow.add_edge("check", "save")
    workflow.add_edge("save", END)

    return workflow.compile()


teacher_graph = build_teacher_graph()
