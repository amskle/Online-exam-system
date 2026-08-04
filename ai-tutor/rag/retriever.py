"""检索器 — 语义检索 + 关键词候选 + RRF 融合。"""
import logging
from typing import Literal

from config.settings import get_settings
from rag.embeddings import embedding_service
from rag.keywords import keyword_score
from rag.vector_store import vector_store

settings = get_settings()
logger = logging.getLogger("ai-tutor.rag")


def _rrf_merge(semantic_docs: list[dict], keyword_docs: list[dict], top_k: int) -> list[dict]:
    """Reciprocal Rank Fusion：score = 1 / (60 + rank)。"""
    scores: dict[str, float] = {}
    merged: dict[str, dict] = {}

    for rank, doc in enumerate(semantic_docs, start=1):
        doc_id = doc["id"]
        scores[doc_id] = scores.get(doc_id, 0.0) + 1.0 / (60 + rank)
        merged[doc_id] = doc

    for rank, doc in enumerate(keyword_docs, start=1):
        doc_id = doc["id"]
        scores[doc_id] = scores.get(doc_id, 0.0) + 1.0 / (60 + rank)
        merged[doc_id] = doc

    if not scores:
        return []

    max_score = max(scores.values()) or 1.0
    ranked = sorted(merged.values(), key=lambda doc: scores[doc["id"]], reverse=True)
    for doc in ranked:
        score = scores[doc["id"]]
        doc["hybrid_score"] = round(score, 4)
        doc["distance"] = round(1.0 - score / max_score, 4)
    return ranked[:top_k]


class Retriever:
    """统一检索接口：语义优先，关键词候选补充，失败时纯关键词降级。"""

    @staticmethod
    async def retrieve(
        query: str,
        collection: Literal["teacher", "student"],
        top_k: int | None = None,
        subject_filter: str | None = None,
    ) -> list[dict]:
        k = top_k or settings.retrieval_top_k
        hybrid_k = settings.hybrid_top_k

        try:
            q_emb = await embedding_service.embed_one(query)
            if not q_emb:
                raise RuntimeError("Embedding API 返回空向量")

            if collection == "teacher":
                semantic_docs = vector_store.search_teacher(q_emb, top_k=hybrid_k, subject_filter=subject_filter)
            else:
                semantic_docs = vector_store.search_student(q_emb, top_k=hybrid_k, subject_filter=subject_filter)

            if not semantic_docs and subject_filter:
                logger.info(
                    "subject_filter='%s' 无结果，回退到全局共享知识库",
                    subject_filter,
                )
                if collection == "teacher":
                    semantic_docs = vector_store.search_teacher(q_emb, top_k=hybrid_k)
                else:
                    semantic_docs = vector_store.search_student(q_emb, top_k=hybrid_k)

            keyword_docs = vector_store.search_keyword(
                collection,
                query,
                top_k=hybrid_k,
                subject_filter=subject_filter,
            )
            keyword_docs = [d for d in keyword_docs if keyword_score(query, d["document"]) > 0]

            if semantic_docs and keyword_docs:
                return _rrf_merge(semantic_docs, keyword_docs, k)
            if semantic_docs:
                return semantic_docs[:k]
            if keyword_docs:
                return keyword_docs[:k]
            return []
        except Exception as e:
            logger.warning("语义检索失败，降级到关键词匹配: %s", e)
            return await Retriever._keyword_retrieve(query, collection, k, subject_filter)

    @staticmethod
    async def _keyword_retrieve(
        query: str,
        collection: Literal["teacher", "student"],
        top_k: int,
        subject_filter: str | None,
    ) -> list[dict]:
        candidates = vector_store.search_keyword(
            collection,
            query,
            top_k=settings.hybrid_top_k,
            subject_filter=subject_filter,
        )

        if not candidates and subject_filter:
            logger.info("关键词在科目 '%s' 内无命中，回退全局共享知识库", subject_filter)
            candidates = vector_store.search_keyword(
                collection,
                query,
                top_k=settings.hybrid_top_k,
                subject_filter=None,
            )

        if not candidates:
            # 关键词候选无命中时，小集合回退到全量本地评分
            all_docs = vector_store.get_all_documents(collection)
            subject_docs = [
                d for d in all_docs
                if d.get("metadata", {}).get("subject") == subject_filter
            ] if subject_filter else all_docs
            if not subject_docs:
                logger.info("全量评分在科目 '%s' 内无命中，使用全局共享知识库", subject_filter)
            all_docs = subject_docs or all_docs
            if len(all_docs) > 1000:
                logger.warning("关键词降级在全量 %d 条记录上执行，仅建议小集合使用", len(all_docs))
                return []
            candidates = all_docs

        scored = []
        for doc in candidates:
            score = keyword_score(query, doc["document"])
            if score > 0:
                doc["distance"] = 1.0 - score
                doc["hybrid_score"] = round(score, 4)
                scored.append((score, doc))

        scored.sort(key=lambda item: item[0], reverse=True)
        return [doc for _, doc in scored[:top_k]]


# 模块级单例
retriever = Retriever()
