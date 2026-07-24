"""检索器 — 语义检索（API） + 关键词后备（纯本地）"""
import logging
import re
from typing import Literal

from rag.embeddings import embedding_service
from rag.vector_store import vector_store
from config.settings import get_settings

settings = get_settings()
logger = logging.getLogger("ai-tutor.rag")


def _keyword_score(query: str, document: str) -> float:
    """简单的关键词匹配得分（0~1），纯本地"""
    query_words = set(re.findall(r'[一-鿿]+|[a-zA-Z]+', query.lower()))
    if not query_words:
        return 0.0
    doc_lower = document.lower()
    hits = sum(1 for w in query_words if w in doc_lower)
    return hits / len(query_words)


class Retriever:
    """统一检索接口：优先语义检索，API 不可用时降级为关键词匹配"""

    @staticmethod
    async def retrieve(
        query: str,
        collection: Literal["teacher", "student"],
        top_k: int | None = None,
        subject_filter: str | None = None,
    ) -> list[dict]:
        k = top_k or settings.retrieval_top_k

        # ── 尝试语义检索 ──
        try:
            q_emb = await embedding_service.embed_one(query)
            if q_emb:
                if collection == "teacher":
                    docs = vector_store.search_teacher(q_emb, top_k=k, subject_filter=subject_filter)
                else:
                    docs = vector_store.search_student(q_emb, top_k=k, subject_filter=subject_filter)
                # 如果 subject filter 无结果，回退到无 filter 搜索
                if not docs and subject_filter:
                    logger.info("subject_filter='%s' 无结果，回退到全局搜索", subject_filter)
                    if collection == "teacher":
                        docs = vector_store.search_teacher(q_emb, top_k=k)
                    else:
                        docs = vector_store.search_student(q_emb, top_k=k)
                return docs
        except Exception as e:
            logger.warning("语义检索失败，降级到关键词匹配: %s", e)

        # ── 后备：关键词匹配检索 ──
        return await Retriever._keyword_retrieve(query, collection, k, subject_filter)

    @staticmethod
    async def _keyword_retrieve(
        query: str,
        collection: Literal["teacher", "student"],
        top_k: int,
        subject_filter: str | None,
    ) -> list[dict]:
        """纯本地关键词检索，通过 ChromaDB get() 获取全量文档后评分"""
        all_docs = vector_store.get_all_documents(collection)

        if not all_docs:
            return []

        if len(all_docs) >= 1000:
            logger.warning(
                "关键词检索在 %s 的 %d 条记录中性能下降，建议扩充语义检索覆盖",
                collection, len(all_docs),
            )

        # 过滤 subject
        if subject_filter:
            all_docs = [d for d in all_docs if d.get("metadata", {}).get("subject") == subject_filter]

        scored = []
        for doc in all_docs:
            score = _keyword_score(query, doc["document"])
            if score > 0:
                doc["distance"] = 1.0 - score
                scored.append((score, doc))

        scored.sort(key=lambda x: x[0], reverse=True)
        return [item[1] for item in scored[:top_k]]


# 模块级单例
retriever = Retriever()
