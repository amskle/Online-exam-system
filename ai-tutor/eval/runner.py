"""评估执行器 — 手写 4 项 RAG 核心指标（LLM-as-judge，零额外依赖）"""
from __future__ import annotations

import asyncio
import logging
import math

from config.settings import get_settings
from agents.common import chat_text

settings = get_settings()
logger = logging.getLogger("ai-tutor.eval.runner")


# ── 指标 1：Faithfulness（生成忠实度）──

_FAITHFULNESS_PROMPT = """你是一位严格的 RAG 质量评审。请判断 AI 生成答案中的每一项事实主张，是否都能在参考上下文中找到依据。

【问题】
{question}

【参考上下文】
{contexts}

【AI 答案】
{answer}

请列出答案中每一项事实主张，并逐条标注：
- ✅ 有依据：上下文直接支持该主张
- ❌ 无依据：上下文未提及或与该主张矛盾

最后输出一行 JSON：{{"claims": N, "supported": M, "faithfulness_score": M/N}}

只输出 JSON："""


async def _evaluate_faithfulness(question: str, answer: str, contexts: list[str]) -> float:
    """评估答案是否忠实于检索到的上下文（防幻觉）"""
    ctx_text = "\n---\n".join(c[:400] for c in contexts) if contexts else "无参考上下文"
    prompt = _FAITHFULNESS_PROMPT.format(question=question, answer=answer, contexts=ctx_text)
    try:
        raw = await chat_text(prompt, temperature=0.0, max_tokens=512)
        return _parse_score(raw, "faithfulness_score", 0.5)
    except Exception as e:
        logger.warning("Faithfulness 评估失败: %s", e)
        return 0.5  # 保守默认值


# ── 指标 2：Answer Relevancy（答案相关性）──

_ANSWER_RELEVANCY_PROMPT = """你是一位严格的 RAG 质量评审。请判断 AI 生成的答案是否直接、准确地回答了用户的问题。

【用户问题】
{question}

【AI 答案】
{answer}

评分标准（0-1）：
- 1.0：完全切题，答案精准命中问题核心，无冗余无偏离
- 0.8：基本切题，略有无关内容但不影响主要回答
- 0.5：部分相关，但偏离严重或避重就轻
- 0.2：基本无关，答非所问
- 0.0：完全无关

返回 JSON：{{"relevancy_score": 0.X, "reason": "一句中文理由"}}
只输出 JSON："""


async def _evaluate_answer_relevancy(question: str, answer: str) -> float:
    """评估答案是否切题"""
    prompt = _ANSWER_RELEVANCY_PROMPT.format(question=question, answer=answer)
    try:
        raw = await chat_text(prompt, temperature=0.0, max_tokens=256)
        return _parse_score(raw, "relevancy_score", 0.5)
    except Exception as e:
        logger.warning("Answer Relevancy 评估失败: %s", e)
        return 0.5


# ── 指标 3：Context Precision（检索精度）──

_CONTEXT_PRECISION_PROMPT = """你是一位检索质量评审。请判断每个检索结果是否与用户问题相关。

【用户问题】
{question}

【检索结果（按排名顺序）】
{context_items}

对每条结果标注 relevance（0=无关, 0.5=部分相关, 1=高度相关），然后计算 precision@k。

返回 JSON：{{"relevances": [1.0, 0.5, 0.0, ...], "precision_at_k": 0.X}}
只输出 JSON："""


async def _evaluate_context_precision(question: str, contexts: list[str]) -> float:
    """评估检索精度：检索到的文档中有多少是真正相关的（加权排名）"""
    if not contexts:
        return 0.0

    items = "\n".join(f"[{i+1}] {c[:150]}..." for i, c in enumerate(contexts[:5]))
    prompt = _CONTEXT_PRECISION_PROMPT.format(question=question, context_items=items)
    try:
        raw = await chat_text(prompt, temperature=0.0, max_tokens=384)
        score = _parse_score(raw, "precision_at_k", 0.5)
        return score
    except Exception as e:
        logger.warning("Context Precision 评估失败: %s", e)
        return 0.5


# ── 指标 4：Context Recall（检索召回率）──

_CONTEXT_RECALL_PROMPT = """你是一位检索质量评审。请判断"参考答案"中的关键信息点，是否能在"检索到的上下文"中找到。

【问题】
{question}

【参考答案（Ground Truth）】
{ground_truth}

【检索到的上下文】
{contexts}

列出参考答案中的关键信息点（3-5 条），标注每条是否被检索上下文覆盖：
- ✅ 覆盖：上下文中包含此信息
- ❌ 遗漏：上下文中未提及

返回 JSON：{{"key_points": N, "covered": M, "recall_score": M/N}}
只输出 JSON："""


async def _evaluate_context_recall(
    question: str, contexts: list[str], ground_truth: str
) -> float:
    """评估检索召回率：参考答案的信息有多少被检索到"""
    if not ground_truth:
        return 0.5

    ctx_text = "\n---\n".join(c[:300] for c in contexts) if contexts else "无上下文"
    prompt = _CONTEXT_RECALL_PROMPT.format(
        question=question, contexts=ctx_text, ground_truth=ground_truth[:500]
    )
    try:
        raw = await chat_text(prompt, temperature=0.0, max_tokens=512)
        return _parse_score(raw, "recall_score", 0.5)
    except Exception as e:
        logger.warning("Context Recall 评估失败: %s", e)
        return 0.5


# ── JSON 解析工具 ──

def _parse_score(raw: str, key: str, default: float) -> float:
    """从 LLM 返回文本中提取指定 key 的浮点数值"""
    from agents.common import extract_json
    try:
        obj = extract_json(raw)
    except Exception:
        logger.debug("extract_json 失败，尝试手动正则提取: raw=%s", raw[:100])
        import re
        import json
        # 尝试在 raw 中找 {} 包裹的 JSON
        m = re.search(r'\{[^{}]*\}', raw)
        if m:
            try:
                obj = json.loads(m.group())
            except json.JSONDecodeError:
                return default
        else:
            return default

    if not isinstance(obj, dict):
        return default
    val = obj.get(key)
    if isinstance(val, (int, float)):
        return max(0.0, min(1.0, float(val)))
    return default


# ── 主入口 ──


async def run_evaluation(dataset: dict) -> dict:
    """对评估数据集运行 4 项核心指标

    Args:
        dataset: {"question": [...], "answer": [...], "contexts": [[...], ...], "ground_truth": [...]}

    Returns:
        dict: {"faithfulness": float, "answer_relevancy": float,
               "context_precision": float, "context_recall": float}
    """
    questions = dataset["question"]
    answers = dataset["answer"]
    contexts_list = dataset["contexts"]
    ground_truths = dataset.get("ground_truth", [""] * len(questions))

    n = len(questions)
    logger.info("开始评估 %d 条样本...", n)

    f_scores, r_scores, p_scores, c_scores = [], [], [], []

    for i in range(n):
        q = questions[i]
        a = answers[i]
        ctxs = contexts_list[i] if i < len(contexts_list) else []
        gt = ground_truths[i] if i < len(ground_truths) else ""

        # 并行评估 4 项指标
        results = await asyncio.gather(
            _evaluate_faithfulness(q, a, ctxs),
            _evaluate_answer_relevancy(q, a),
            _evaluate_context_precision(q, ctxs),
            _evaluate_context_recall(q, ctxs, gt),
            return_exceptions=True,
        )

        f_scores.append(results[0] if not isinstance(results[0], Exception) else 0.5)
        r_scores.append(results[1] if not isinstance(results[1], Exception) else 0.5)
        p_scores.append(results[2] if not isinstance(results[2], Exception) else 0.5)
        c_scores.append(results[3] if not isinstance(results[3], Exception) else 0.5)

        if (i + 1) % 5 == 0:
            logger.info("已评估 %d/%d 条...", i + 1, n)

    return {
        "faithfulness": _safe_mean(f_scores),
        "answer_relevancy": _safe_mean(r_scores),
        "context_precision": _safe_mean(p_scores),
        "context_recall": _safe_mean(c_scores),
    }


def _safe_mean(vals: list[float]) -> float:
    """安全求均值，过滤 NaN"""
    clean = [v for v in vals if isinstance(v, (int, float)) and not math.isnan(v)]
    return float(sum(clean) / len(clean)) if clean else 0.0
