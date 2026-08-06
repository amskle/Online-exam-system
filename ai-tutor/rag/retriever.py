"""检索器 — 多路 Query 改写 + 语义检索 + 关键词候选 + RRF 融合。"""
import logging
from typing import Literal

from config.settings import get_settings
from rag.embeddings import embedding_service
from rag.keywords import keyword_score
from rag.query_rewriter import generate_query_variants
from rag.vector_store import vector_store

settings = get_settings()
logger = logging.getLogger("ai-tutor.rag")


def _rrf_merge(semantic_docs: list[dict], keyword_docs: list[dict], top_k: int) -> list[dict]:
    """单路语义 + 关键词的 Reciprocal Rank Fusion。"""
    return _rrf_merge_many([semantic_docs, keyword_docs], top_k)


def _rrf_merge_many(result_lists: list[list[dict]], top_k: int) -> list[dict]:
    """多路检索结果的 Reciprocal Rank Fusion：score = 1 / (60 + rank)。"""
    scores: dict[str, float] = {}
    merged: dict[str, dict] = {}

    for ranked in result_lists:
        for rank, doc in enumerate(ranked, start=1):
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
    """统一检索接口：先多路改写，再逐路检索并 RRF 合并。"""

    @staticmethod
    async def retrieve(
        query: str,
        collection: Literal["teacher", "student"],
        top_k: int | None = None,
        subject_filter: str | None = None,
        query_history: list[str] | None = None,
    ) -> list[dict]:
        k = top_k or settings.retrieval_top_k
        hybrid_k = settings.hybrid_top_k
        query_variants = await generate_query_variants(query, history=query_history)

        result_lists: list[list[dict]] = []
        for variant in query_variants:
            docs = await Retriever._retrieve_variant(
                variant,
                collection=collection,
                hybrid_k=hybrid_k,
                subject_filter=subject_filter,
            )
            if docs:
                result_lists.append(docs)

        if result_lists:
            return _rrf_merge_many(result_lists, k)
        return await Retriever._keyword_retrieve(query, collection, k, subject_filter)

    @staticmethod
    async def _retrieve_variant(
        query: str,
        collection: Literal["teacher", "student"],
        hybrid_k: int,
        subject_filter: str | None,
    ) -> list[dict]:
        try:
            q_emb = await embedding_service.embed_one(query)
            if not q_emb:
                raise RuntimeError("Embedding API 返回空向量")

            if collection == "teacher":
                semantic_docs = vector_store.search_teacher(
                    q_emb, top_k=hybrid_k, subject_filter=subject_filter
                )
            else:
                semantic_docs = vector_store.search_student(
                    q_emb, top_k=hybrid_k, subject_filter=subject_filter
                )

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

            if semantic_docs or keyword_docs:
                return _rrf_merge(semantic_docs, keyword_docs, hybrid_k)
            return []
        except Exception as e:
            logger.warning("语义检索失败，降级到关键词匹配: %s", e)
            return await Retriever._keyword_retrieve(
                query, collection, hybrid_k, subject_filter
            )

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
