"""Embedding 模块 — 接入国内模型 Embedding API（OpenAI-compatible，异步）"""
import logging
from collections import OrderedDict

from openai import AsyncOpenAI
from config.settings import get_settings

settings = get_settings()
logger = logging.getLogger("ai-tutor.rag")

_QUERY_CACHE_SIZE = 256


class EmbeddingService:
    """文本向量化服务，单条查询带 LRU 缓存"""

    def __init__(self):
        self.client = AsyncOpenAI(
            api_key=settings.embedding_api_key,
            base_url=settings.embedding_api_base,
            timeout=settings.llm_timeout,
        )
        self.model = settings.embedding_model
        self._query_cache: OrderedDict[str, list[float]] = OrderedDict()

    async def embed(self, texts: list[str]) -> list[list[float]]:
        """批量向量化（超过模型上限的文本自动截断）"""
        if not texts:
            return []
        truncated = []
        for i, t in enumerate(texts):
            if len(t) > settings.embedding_max_chars:
                truncated.append(t[:settings.embedding_max_chars])
                logger.warning(
                    "文本 #%d 过长 (%d chars)，已截断至 %d chars（模型上限 ~512 tokens）",
                    i, len(t), settings.embedding_max_chars,
                )
            else:
                truncated.append(t)
        resp = await self.client.embeddings.create(model=self.model, input=truncated)
        return [d.embedding for d in resp.data]

    async def embed_one(self, text: str) -> list[float]:
        """单条向量化（带缓存，检索查询多为重复知识点）"""
        cached = self._query_cache.get(text)
        if cached is not None:
            self._query_cache.move_to_end(text)
            return cached
        results = await self.embed([text])
        vec = results[0] if results else []
        if vec:
            self._query_cache[text] = vec
            if len(self._query_cache) > _QUERY_CACHE_SIZE:
                self._query_cache.popitem(last=False)
        return vec


# 模块级单例
embedding_service = EmbeddingService()
