"""学生智能体 — LangGraph 状态机：上下文加载 → 问题理解 → 知识检索 → Socratic 规划 → 生成 → 泄露检查"""
import json
import logging
import re
from typing import TypedDict

from langgraph.graph import StateGraph, END
from openai import AsyncOpenAI

from agents.common import (
    chat_text,
    extract_json,
    has_fatal,
    get_llm,
)
from config.settings import get_settings
from rag.retriever import retriever
from utils.exam_bridge import exam_bridge

settings = get_settings()
logger = logging.getLogger("ai-tutor.agent.student")


# ── 状态定义 ──
class StudentState(TypedDict):
    # 输入
    question_id: int | None
    question_content: str
    student_answer: str
    correct_answer: str  # 服务端加载，绝不返回给学生
    message: str
    conversation_history: list[dict]
    token: str

    # 中间产物
    weakness_analysis: str  # 薄弱点分析
    knowledge_context: str   # RAG 检索到的知识点
    socratic_plan: str
    draft_reply: str

    # 输出
    final_reply: str
    hints: list[str]
    related_concepts: list[str]
    contains_answer: bool
    warnings: list[str]
    fatal_error: str


# ── 确定性的答案泄露检查 ──


def _deterministic_leak(correct_answer: str, reply: str) -> bool:
    """不依赖 LLM 的确定性泄露检测；只标记高置信度模式，避免误伤"""
    if not correct_answer:
        return False
    a = correct_answer.strip()
    reply_clean = reply.replace(" ", "")

    # ── 选择题 ──
    letters = set(re.findall(r'[A-D]', a))
    if letters:
        # 结论性句式：答案是X / 选X / X是正确选项 / 选项X
        conclusive = re.compile(
            r'(?:答案|正确|选)(?:是|为|项|择|应该|应该选|的)|'
            r'([A-D])\s*(?:是|为)(?:正确|正解)',
            re.IGNORECASE,
        )
        # 匹配并检查是否命中目标字母
        for m in re.finditer(r'[A-D]', reply):
            ctx = reply[max(0, m.start() - 10):m.end() + 6]
            if conclusive.search(ctx) and m.group() in letters:
                logger.info("确定性泄露检测命中: 回复中出现了结论性的答案字母 %s", m.group())
                return True
        return False

    # ── 判断题 ──
    if a in ("正确", "错误"):
        opposite = "错误" if a == "正确" else "正确"
        # 结论性表述
        conclusive_patterns = [
            rf'(?:答案是|结论是|所以|因此|应该|最终|选)[：:\s]*{a}',
            rf'[这该]个?(?:说法|判断|题目|描述|陈述)[：:\s]*(?:是)?{a}的',
            rf'答案为[：:\s]*{a}',
            rf'(?:所以|因此)[这该]?(?:道)?题(?:应?该)?(?:选|填)[：:\s]*{a}',
        ]
        for pat in conclusive_patterns:
            if re.search(pat, reply):
                return True
        return False

    # ── 主观题/其他 ──
    if len(a) >= 4 and a in reply:
        return True
    return False


def _extract_hints(plan: str) -> list[str]:
    """从 Socratic 计划中提取提示列表"""
    steps = re.findall(r'引导步骤\d+[：:]\s*(.+)', plan)
    return [s.strip() for s in steps if s.strip()]


def _extract_concepts(context: str) -> list[str]:
    """从知识上下文中提取相关概念"""
    if "相关知识点" in context:
        part = context.split("相关知识点", 1)[1]
        lines = [l.strip("- *").strip() for l in part.split("\n") if l.strip()]
        return lines[:3]
    return []


async def _regenerate_safe_reply(state: StudentState) -> str:
    """答案泄露时重新生成安全回复"""
    prompt = f"""你之前的回复不小心包含了答案信息。请重新生成一段引导性回复。

要求：
- 只使用苏格拉底式提问
- 绝不提及正确答案、不给出选项字母、不判断正误
- 引导学生自己思考

题目: {state["question_content"][:300]}
学生疑问: {state["message"]}"""

    try:
        return await chat_text(prompt, temperature=0.7, max_tokens=512)
    except Exception:
        return "让我换个方式引导你思考这个问题..."

# ── 节点函数 ──


async def load_question_context(state: StudentState) -> StudentState:
    """节点0: 从服务端加载错题上下文（正确答案、题目内容、学生答案）"""
    qid = state.get("question_id")
    if not qid:
        return state

    try:
        wq = await exam_bridge.get_wrong_question(state["token"], qid)
        if wq:
            if not state.get("question_content"):
                state["question_content"] = wq.get("content", "")
            if not state.get("student_answer"):
                state["student_answer"] = wq.get("userAnswer", "")
            if not state.get("correct_answer"):
                state["correct_answer"] = wq.get("correctAnswer", "")
            logger.info("从后端加载错题 %d 的上下文", qid)
        else:
            state["warnings"].append(f"未找到错题 {qid} 的记录，将以学生输入为准")
    except Exception as e:
        state["warnings"].append(f"加载错题上下文失败: {e!s}")

    return state


async def understand_question(state: StudentState) -> StudentState:
    """节点1: 问题理解 — 分析学生困惑，输出到 weakness_analysis"""
    history_str = ""
    if state.get("conversation_history"):
        history_str = "\n".join(
            f"{h['role']}: {h['content'][:200]}" for h in state["conversation_history"][-6:]
        )

    prompt = f"""你是一位耐心的学习导师。学生正在做以下题目并遇到了困难。

【题目内容】
{state["question_content"][:1000]}

【学生的答案】
{state.get("student_answer", "未作答")}

【正确答案】（仅供你了解方向，绝不能告诉学生）
{state.get("correct_answer", "未知")}

【学生的疑问】
{state["message"]}

【对话历史】
{history_str}

请分析学生当前的知识薄弱点和困惑所在。输出一段不超过150字的分析。"""

    try:
        state["weakness_analysis"] = await chat_text(prompt, temperature=0.5, max_tokens=256)
    except Exception as e:
        state["weakness_analysis"] = f"学生疑问：{state['message'][:200]}"
        state["warnings"].append(f"问题理解LLM失败（降级兜底）: {e!s}")
    return state


async def retrieve_knowledge(state: StudentState) -> StudentState:
    """节点2: 知识检索 — 从学生知识库中检索相关知识点"""
    query = f"{state['question_content'][:200]} {state['message']}"
    try:
        docs = await retriever.retrieve(query=query, collection="student", top_k=3)
    except Exception as e:
        logger.warning("学生检索失败: %s", e)
        state["warnings"].append(f"知识检索失败: {e!s}")
        docs = []

    if docs:
        context = "\n---\n".join(d["document"][:600] for d in docs)
        state["knowledge_context"] = "\n\n【相关知识点】\n" + context
    else:
        state["warnings"].append("学生知识库中未找到相关知识点")
        state["knowledge_context"] = ""
    return state


async def socratic_plan(state: StudentState) -> StudentState:
    """节点3: Socratic 规划 — 设计循序渐进的引导路径"""
    prompt = f"""你是一位采用苏格拉底教学法的导师。学生正在做一道题，你需要引导他/她自己找到答案，而不是直接告诉他/她。

【题目内容】
{state["question_content"][:800]}

【学生的答案】
{state.get("student_answer", "未作答")}

【正确答案】（仅供你判断方向，绝对不能透露）
{state.get("correct_answer", "未知")}

【学生知识薄弱点】
{state["weakness_analysis"][:500]}

【学生知识上下文】
{state.get("knowledge_context", "")[:500]}

【学生的疑问】
{state["message"]}

请设计一个循序渐进的引导计划，包含2-4个引导步骤。
每个步骤是一个启发性问题或提示，引导学生逐步接近正确答案。

格式要求：
引导步骤1: <第一步的引导问题>
引导步骤2: <第二步的引导问题>
...

注意：绝对不要出现正确答案的具体内容。"""

    try:
        state["socratic_plan"] = await chat_text(prompt, temperature=0.6, max_tokens=512)
    except Exception as e:
        state["socratic_plan"] = f"引导步骤1: 请尝试自己分析{state.get('student_answer', '这个答案')}为什么这样选择？\n引导步骤2: 回顾一下相关知识点。"
        state["warnings"].append(f"Socratic规划LLM失败（降级兜底）: {e!s}")
    return state


async def generate_reply(state: StudentState) -> StudentState:
    """节点4: 生成引导回复 — 基于 Socratic 计划生成对学生友好的回复"""
    prompt = f"""你是一位亲切耐心的学习伙伴，正在通过苏格拉底式提问帮助学生。

【Socratic 引导计划】
{state["socratic_plan"]}

【对话历史】
{chr(10).join(f"{h['role']}: {h['content'][:150]}" for h in state.get("conversation_history", [])[-4:])}

请根据引导计划的第一步，生成一段对学生友好的回复。
要求：
1. 语气亲切、鼓励性强（如 "我们来一起看看..." "你有没有注意到..."）
2. 用提问引导，而不是直接解释
3. 每次只引导一个步骤，不要一次性给太多信息
4. 如果学生答对了方向，给予肯定然后继续深入
5. 如果学生明显跑偏，温和地引导回正轨
6. 绝对不给出正确答案本身

回复长度控制在150-300字。"""

    try:
        state["draft_reply"] = await chat_text(prompt, temperature=0.7, max_tokens=512)
    except Exception as e:
        logger.warning("LLM 回复生成失败，使用兜底回复: %s", e)
        state["draft_reply"] = "抱歉，我暂时无法回答这个问题。请稍后再试。"
        state["warnings"].append(f"回复生成LLM失败（使用兜底回复）: {e!s}")
    return state


async def leak_check(state: StudentState) -> StudentState:
    """节点5: 答案泄露检查 — 确定性 + LLM 双重检查"""
    draft = state.get("draft_reply", "")
    correct = state.get("correct_answer", "")

    # ── 1. 确定性检查 ──
    det_leak = _deterministic_leak(correct, draft)

    # ── 2. LLM 严格检查 ──
    llm_leak = False
    if not det_leak and correct:
        prompt = f"""请严格检查以下 AI 助教的回复是否泄露了正确答案。

【题目】
{state["question_content"][:500]}

【正确答案】（绝对不能透露给学生的内容）
{correct}

【AI 回复】
{draft}

检查规则：
1. 回复中是否直接出现了正确答案的值？（如答案选项字母、判断结果、关键数字等）
2. 回复中是否通过排除法隐式给出了答案？（如 "A不对，B也不对，只剩下C了"）
3. 回复是否过于明显地暗示了正确答案？

只输出一个词：LEAK（泄露了）或 SAFE（安全）。不要输出其他内容。"""

        try:
            raw = await chat_text(prompt, temperature=0.1, max_tokens=32)
            if raw.strip().upper().startswith("LEAK"):
                llm_leak = True
        except Exception as e:
            logger.warning("LLM 泄露检查失败: %s", e)

    if det_leak:
        logger.info("确定性泄露检测命中，正在重写回复")
        state["contains_answer"] = True
        state["final_reply"] = await _regenerate_safe_reply(state)
    elif llm_leak:
        logger.info("LLM 泄露检测命中，正在重写回复")
        state["contains_answer"] = True
        state["final_reply"] = await _regenerate_safe_reply(state)
    else:
        state["contains_answer"] = False
        state["final_reply"] = draft

    state["hints"] = _extract_hints(state["socratic_plan"])
    state["related_concepts"] = _extract_concepts(state.get("knowledge_context", ""))
    return state


# ── 构建 Graph ──

def build_student_graph() -> StateGraph:
    workflow = StateGraph(StudentState)

    workflow.add_node("load_context", load_question_context)
    workflow.add_node("understand", understand_question)
    workflow.add_node("retrieve", retrieve_knowledge)
    workflow.add_node("plan", socratic_plan)
    workflow.add_node("generate", generate_reply)
    workflow.add_node("check", leak_check)

    workflow.set_entry_point("load_context")
    workflow.add_edge("load_context", "understand")
    workflow.add_conditional_edges("understand", has_fatal, {"continue": "retrieve", "end": END})
    workflow.add_edge("retrieve", "plan")
    workflow.add_conditional_edges("plan", has_fatal, {"continue": "generate", "end": END})
    workflow.add_edge("generate", "check")
    workflow.add_edge("check", END)

    return workflow.compile()


student_graph = build_student_graph()
