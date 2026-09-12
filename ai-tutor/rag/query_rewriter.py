"""RAG 多路 Query 改写与会话级查询记忆。"""
from __future__ import annotations

import asyncio
import logging
import re
import time
from collections import OrderedDict, defaultdict, deque

from agents.common import chat_json
from config.settings import get_settings

settings = get_settings()
logger = logging.getLogger("ai-tutor.rag.query_rewriter")

_rewrite_cache: OrderedDict[tuple[str, tuple[str, ...], int], list[str]] = OrderedDict()
_REFERENCE_PATTERN = re.compile(r"(?:它|这个|那个|这些|那些|这道题|该题|该内容|上述|上面|前者|后者|其)")
_COMPOUND_PATTERN = re.compile(r"(?:分别|对比|比较|区别|异同|以及|并且|同时|还是|或者|和|与|或)")


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


def is_complex_query(query: str, history: list[str] | None = None) -> bool:
    """用低成本规则识别需要指代消解或拆分的复杂查询。"""
    compact = " ".join(query.strip().split())
    if not compact:
        return False
    if len(compact) >= settings.query_complexity_length_threshold:
        return True
    if compact.count("？") + compact.count("?") > 1:
        return True
    if _REFERENCE_PATTERN.search(compact):
        return True
    if _COMPOUND_PATTERN.search(compact):
        return True
    return False


def clear_rewrite_cache():
    """清理进程内改写缓存，供测试和运维热刷新使用。"""
    _rewrite_cache.clear()


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
    limit = max_variants or settings.query_rewrite_max_variants
    cache_key = (
        query,
        tuple(history[-settings.query_rewrite_history_limit:]),
        limit,
    )
    cached = _rewrite_cache.get(cache_key)
    if cached is not None:
        _rewrite_cache.move_to_end(cache_key)
        logger.info("rag_stage=rewrite elapsed_ms=0 cache_hit=1 variants=%d", len(cached))
        return list(cached)

    history_text = "\n".join(
        f"{i + 1}. {item}" for i, item in enumerate(history[-settings.query_rewrite_history_limit:])
    ) or "（暂无历史问题）"

    prompt = _REWRITE_PROMPT.format(history=history_text, query=query)
    started = time.perf_counter()
    try:
        raw = await asyncio.wait_for(
            chat_json(prompt, temperature=0.2, max_tokens=1024),
            timeout=settings.query_rewrite_timeout_seconds,
        )
        if not isinstance(raw, list):
            raise ValueError(f"改写结果不是数组: {raw!r}")
        rewritten = [str(item).strip() for item in raw if str(item).strip()]
        variants.extend(rewritten)
    except asyncio.TimeoutError:
        logger.info(
            "Query 改写超过 %.0fms，仅使用原始问题",
            settings.query_rewrite_timeout_seconds * 1000,
        )
    except Exception as e:
        logger.warning("Query 改写失败，仅使用原始问题: %s", e)

    result = _dedupe_queries(variants)[:limit]
    if len(result) > 1:
        _rewrite_cache[cache_key] = list(result)
        _rewrite_cache.move_to_end(cache_key)
        while len(_rewrite_cache) > settings.query_rewrite_cache_size:
            _rewrite_cache.popitem(last=False)
    logger.info(
        "rag_stage=rewrite elapsed_ms=%.2f cache_hit=0 variants=%d",
        (time.perf_counter() - started) * 1000,
        len(result),
    )
    return result


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
