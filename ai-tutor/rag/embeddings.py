"""Embedding 模块 — 接入国内模型 Embedding API（OpenAI-compatible，异步）"""
import logging
from collections import OrderedDict

from openai import AsyncOpenAI
from config.settings import get_settings
from utils.observability import observe_model_call

settings = get_settings()
logger = logging.getLogger("ai-tutor.rag")

_QUERY_CACHE_SIZE = 256


class EmbeddingService:
    """文本向量化服务，单条查询带 LRU 缓存"""

    def __init__(self):
        api_key = (
            settings.llm_api_key
            if settings.embedding_use_llm_credentials
            else settings.embedding_api_key
        )
        base_url = (
            settings.llm_api_base
            if settings.embedding_use_llm_credentials
            else settings.embedding_api_base
        )
        self.client = AsyncOpenAI(
            api_key=api_key,
            base_url=base_url,
            timeout=settings.llm_timeout,
        )
        self.model = settings.embedding_model
        self._query_cache: OrderedDict[str, list[float]] = OrderedDict()

    async def embed(self, texts: list[str]) -> list[list[float]]:
        """分批向量化；批内去重并保持输入顺序。"""
        if not texts:
            return []
        truncated = []
        for i, t in enumerate(texts):
            if len(t) > settings.embedding_max_chars:
                truncated.append(t[:settings.embedding_max_chars])
                logger.warning(
                    "文本 #%d 过长 (%d chars)，已截断至 %d chars（模型上限 ~8192 tokens）",
                    i, len(t), settings.embedding_max_chars,
                )
            else:
                truncated.append(t)
        unique_texts = list(dict.fromkeys(truncated))
        vectors_by_text: dict[str, list[float]] = {}
        batch_size = settings.embedding_batch_size
        for start in range(0, len(unique_texts), batch_size):
            batch = unique_texts[start:start + batch_size]
            with observe_model_call(
                "embedding.batch",
                as_type="embedding",
                model=self.model,
                input={"texts": batch, "batch_size": len(batch)},
            ) as observation:
                resp = await self.client.embeddings.create(model=self.model, input=batch)
                if observation is not None:
                    usage = getattr(resp, "usage", None)
                    usage_details = {
                        key: int(value)
                        for key, value in {
                            "input": getattr(usage, "prompt_tokens", None),
                            "total": getattr(usage, "total_tokens", None),
                        }.items()
                        if value is not None
                    }
                    observation.update(
                        output={"embedding_count": len(resp.data)},
                        usage_details=usage_details or None,
                    )
            if len(resp.data) != len(batch):
                raise RuntimeError(
                    f"Embedding 返回数量不一致：请求 {len(batch)}，返回 {len(resp.data)}"
                )
            for text, item in zip(batch, resp.data):
                vectors_by_text[text] = item.embedding
        return [vectors_by_text[text] for text in truncated]

    async def embed_one(self, text: str) -> list[float]:
        """单条向量化（带缓存，检索查询多为重复知识点）"""
        results = await self.embed_queries([text])
        return results[0] if results else []

    async def embed_queries(self, texts: list[str]) -> list[list[float]]:
        """批量向量化检索 Query，只为缓存未命中的文本发起一次批请求。"""
        if not texts:
            return []

        missing = []
        for text in dict.fromkeys(texts):
            cached = self._query_cache.get(text)
            if cached is not None:
                self._query_cache.move_to_end(text)
            else:
                missing.append(text)

        if missing:
            vectors = await self.embed(missing)
            for text, vector in zip(missing, vectors):
                if vector:
                    self._query_cache[text] = vector
                    self._query_cache.move_to_end(text)
            while len(self._query_cache) > _QUERY_CACHE_SIZE:
                self._query_cache.popitem(last=False)

        return [self._query_cache.get(text, []) for text in texts]


# 模块级单例
embedding_service = EmbeddingService()
