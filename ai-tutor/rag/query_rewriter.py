"""RAG 多路 Query 改写与会话级查询记忆。"""
from __future__ import annotations

import logging
from collections import defaultdict, deque

from agents.common import chat_json
from config.settings import get_settings

settings = get_settings()
logger = logging.getLogger("ai-tutor.rag.query_rewriter")


_REWRITE_PROMPT = """你是 RAG 查询改写助手。根据最近的用户问题历史，把当前问题改写成多个适合向量检索的独立查询。

改写要求：
1. 如果当前问题包含指代词（如“它、这个、该、上述、那类”），先用历史问题中明确的实体替换，得到完整的指代消解问题。
2. 如果当前问题是复合问题，拆成多个只包含单一意图的子问题。
3. 对每个子问题至少给一个语义等价但措辞不同的改写，用于提高召回率。

注意：
- 每个改写必须是完整、可独立检索的问题，不要用“它”这类指代词。
- 不要编造历史中不存在的实体。
- 最多输出 5 个查询，去重。

最近用户问题历史：
{history}

当前问题：
{query}

只输出 JSON 字符串数组，例如：["Spring Boot 是什么", "Spring Boot 与 FastAPI 的区别是什么"]"""


def _dedupe_queries(queries: list[str]) -> list[str]:
    seen = set()
    result = []
    for query in queries:
        normalized = " ".join(query.strip().split()).lower()
        if normalized and normalized not in seen:
            seen.add(normalized)
            result.append(query.strip())
    return result


async def generate_query_variants(
    query: str,
    history: list[str] | None = None,
    max_variants: int | None = None,
) -> list[str]:
    """基于历史问题生成多路检索查询，失败时只返回原始问题。"""
    query = query.strip()
    variants = [query]
    if not query or not settings.query_rewrite_enabled:
        return variants

    history = [h.strip() for h in (history or []) if h and h.strip()]
    history_text = "\n".join(
        f"{i + 1}. {item}" for i, item in enumerate(history[-settings.query_rewrite_history_limit:])
    ) or "（暂无历史问题）"

    prompt = _REWRITE_PROMPT.format(history=history_text, query=query)
    try:
        raw = await chat_json(prompt, temperature=0.2, max_tokens=1024)
        if not isinstance(raw, list):
            raise ValueError(f"改写结果不是数组: {raw!r}")
        rewritten = [str(item).strip() for item in raw if str(item).strip()]
        variants.extend(rewritten)
    except Exception as e:
        logger.warning("Query 改写失败，仅使用原始问题: %s", e)

    limit = max_variants or settings.query_rewrite_max_variants
    return _dedupe_queries(variants)[:limit]


class QueryRewriteMemory:
    """会话级查询记忆：保存每个会话最近若干条用户问题。"""

    def __init__(self, max_entries: int | None = None):
        self.max_entries = max_entries or settings.query_rewrite_history_limit
        self._history: dict[str, deque[str]] = defaultdict(
            lambda: deque(maxlen=self.max_entries)
        )

    def remember(self, session_id: str, query: str):
        query = query.strip()
        if not query:
            return
        history = self._history[session_id]
        if not history or history[-1] != query:
            history.append(query)

    def recent_queries(self, session_id: str, limit: int | None = None) -> list[str]:
        history = list(self._history.get(session_id, []))
        return history[-limit:] if limit else history

    def seed_from_messages(self, session_id: str, messages: list[dict]):
        if session_id in self._history:
            return
        for message in messages:
            if message.get("role") == "user":
                self.remember(session_id, message.get("content", ""))

    def clear(self, session_id: str):
        self._history.pop(session_id, None)


query_rewrite_memory = QueryRewriteMemory()
