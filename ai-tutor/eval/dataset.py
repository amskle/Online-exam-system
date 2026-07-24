"""评估数据集构建器 — 从 ChromaDB 知识库自动生成评估数据集"""
import logging
import random

from config.settings import get_settings
from rag.retriever import retriever
from rag.vector_store import vector_store
from agents.common import chat_text

settings = get_settings()
logger = logging.getLogger("ai-tutor.eval.dataset")


# ── LLM 反推问题 ──

_QUESTION_GEN_PROMPT = """你是一位出题老师。下面是一道参考题的内容，请根据它设计一个学生可能会问的问题（模拟真实查询）。只输出问题本身，不要额外解释。

参考题：
{content}

学生可能会问的问题："""


async def _generate_question_from_doc(doc_content: str) -> str:
    """根据知识库文档内容，用 LLM 反推一个学生可能提出的查询问题"""
    prompt = _QUESTION_GEN_PROMPT.format(content=doc_content[:600])
    try:
        return await chat_text(prompt, temperature=0.5, max_tokens=128)
    except Exception as e:
        logger.warning("生成问题失败: %s", e)
        return ""


# ── LLM 生成答案 ──

_ANSWER_GEN_PROMPT = """你是一位知识渊博的助教。请根据提供的参考资料回答学生的问题。

学生问题：{question}

参考资料：
{contexts}

请给出准确、简洁的回答（不超过 200 字）："""


async def _generate_answer(question: str, contexts: list[str]) -> str:
    """基于检索到的上下文生成答案"""
    ctx_text = "\n---\n".join(c[:400] for c in contexts) if contexts else "无参考资料"
    prompt = _ANSWER_GEN_PROMPT.format(question=question, contexts=ctx_text)
    try:
        return await chat_text(prompt, temperature=0.3, max_tokens=384)
    except Exception as e:
        logger.warning("生成答案失败: %s", e)
        return ""


# ── 主入口 ──


async def build_eval_dataset(
    collection: str = "teacher",
    sample_count: int | None = None,
) -> dict | None:
    """从 ChromaDB 知识库构建评估数据集

    Args:
        collection: 集合名 ("teacher" 或 "student")
        sample_count: 采样数，默认来自 settings.eval_sample_count

    Returns:
        dict: {"question": [...], "answer": [...], "contexts": [[...], ...], "ground_truth": [...]}
        如果知识库文档数不足则返回 None
    """
    n = sample_count or settings.eval_sample_count

    all_docs = vector_store.get_all_documents(collection)
    if not all_docs:
        logger.error("知识库 %s 为空，无法构建评估数据集", collection)
        return None

    if len(all_docs) < 3:
        logger.error("知识库 %s 仅 %d 条文档，至少需要 3 条", collection, len(all_docs))
        return None

    actual_n = min(n, len(all_docs))
    sampled = random.sample(all_docs, actual_n)
    logger.info("从 %s 采样 %d/%d 条文档用于评估", collection, actual_n, len(all_docs))

    questions: list[str] = []
    answers: list[str] = []
    contexts_list: list[list[str]] = []
    ground_truths: list[str] = []

    for i, doc in enumerate(sampled):
        content = doc.get("document", "")
        subject = doc.get("metadata", {}).get("subject", "")

        # Step 1: 从文档反推问题
        question = await _generate_question_from_doc(content)
        if not question:
            continue

        # Step 2: 执行检索
        try:
            retrieved = await retriever.retrieve(
                query=question,
                collection=collection if collection in ("teacher", "student") else "teacher",  # type: ignore[arg-type]
                subject_filter=subject or None,
            )
        except Exception as e:
            logger.warning("检索失败 (doc #%d): %s", i, e)
            retrieved = []

        # Step 3: 用检索到的上下文生成答案
        ctxs = [r.get("document", "") for r in retrieved]
        answer = await _generate_answer(question, ctxs)

        # Step 4: 组装
        questions.append(question)
        answers.append(answer)
        contexts_list.append(ctxs if ctxs else [content[:200]])  # 降级：用原文档片段
        ground_truths.append(content[:500])

        if (i + 1) % 5 == 0:
            logger.info("已处理 %d/%d 条...", i + 1, actual_n)

    if len(questions) < 3:
        logger.error("有效样本仅 %d 条，无法评估（至少 3 条）", len(questions))
        return None

    logger.info("构建完成：%d 条评估样本", len(questions))

    return {
        "question": questions,
        "answer": answers,
        "contexts": contexts_list,
        "ground_truth": ground_truths,
    }
