"""低延迟混合检索：快慢路由、批量向量化、按需关键词与有界降级。"""
from __future__ import annotations

import asyncio
import contextlib
import logging
import time
from collections import OrderedDict
from typing import Literal

from config.settings import get_settings
from rag.embeddings import embedding_service
from rag.keyword_index import keyword_fallback_index
from rag.keywords import keyword_score
from rag.query_rewriter import generate_query_variants, is_complex_query
from rag.sanitizer import REDACTED_CHUNK_TEXT, strip_answer_content
from rag.vector_store import vector_store

settings = get_settings()
logger = logging.getLogger("ai-tutor.rag")

KeywordMode = Literal["on_demand", "always", "disabled"]
_result_cache: OrderedDict[tuple, tuple[float, list[dict]]] = OrderedDict()


def _student_safe_results(docs: list[dict]) -> list[dict]:
    """Defense in depth for documents indexed before the current sanitizer."""
    safe = []
    for doc in docs:
        content = strip_answer_content(doc.get("document", ""))
        if content and content != REDACTED_CHUNK_TEXT:
            safe.append(dict(doc, document=content))
    return safe


def _copy_docs(docs: list[dict]) -> list[dict]:
    return [dict(doc, metadata=dict(doc.get("metadata", {}))) for doc in docs]


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
            merged[doc_id] = dict(doc)

    if not scores:
        return []

    max_score = max(scores.values()) or 1.0
    ranked = sorted(merged.values(), key=lambda doc: scores[doc["id"]], reverse=True)
    for doc in ranked:
        score = scores[doc["id"]]
        doc["hybrid_score"] = round(score, 4)
        doc["distance"] = round(1.0 - score / max_score, 4)
    return ranked[:top_k]


def _filter_semantic_results(docs: list[dict]) -> list[dict]:
    """把 Chroma cosine distance 转成相似度并过滤弱相关结果。"""
    filtered = []
    for doc in docs:
        similarity = max(0.0, min(1.0, 1.0 - float(doc.get("distance", 1.0))))
        if similarity < settings.retrieval_min_similarity:
            continue
        item = dict(doc)
        item["semantic_score"] = round(similarity, 4)
        item["hybrid_score"] = round(similarity, 4)
        filtered.append(item)
    return filtered


def _semantic_is_sufficient(docs: list[dict], requested_k: int) -> bool:
    required = min(requested_k, settings.retrieval_early_stop_min_results)
    if len(docs) < required:
        return False
    top_score = max((float(doc.get("semantic_score", 0.0)) for doc in docs), default=0.0)
    return top_score >= settings.retrieval_early_stop_similarity


def _collection_revision(collection: str) -> int:
    revision = getattr(vector_store, "revision", None)
    return int(revision(collection)) if callable(revision) else 0


def _cache_get(key: tuple) -> list[dict] | None:
    cached = _result_cache.get(key)
    if cached is None:
        return None
    expires_at, docs = cached
    if time.monotonic() >= expires_at:
        _result_cache.pop(key, None)
        return None
    _result_cache.move_to_end(key)
    return _copy_docs(docs)


def _cache_put(key: tuple, docs: list[dict]):
    if not docs or settings.retrieval_cache_ttl_seconds <= 0:
        return
    _result_cache[key] = (
        time.monotonic() + settings.retrieval_cache_ttl_seconds,
        _copy_docs(docs),
    )
    _result_cache.move_to_end(key)
    while len(_result_cache) > settings.retrieval_cache_size:
        _result_cache.popitem(last=False)


def clear_retrieval_cache():
    _result_cache.clear()
    keyword_fallback_index.clear()


def _log_stage(stage: str, started: float, **fields):
    elapsed_ms = round((time.perf_counter() - started) * 1000, 2)
    details = " ".join(f"{key}={value}" for key, value in fields.items())
    logger.info("rag_stage=%s elapsed_ms=%.2f %s", stage, elapsed_ms, details)


class Retriever:
    """按查询复杂度选择快通道或多路混合检索。"""

    @staticmethod
    async def retrieve(
        query: str,
        collection: Literal["teacher", "student"],
        top_k: int | None = None,
        subject_filter: str | None = None,
        query_history: list[str] | None = None,
        *,
        rewrite: bool | None = None,
        keyword_mode: KeywordMode = "on_demand",
        route_query: str | None = None,
    ) -> list[dict]:
        """检索文档；rewrite=None 时由轻量规则自动选择快慢通道。"""
        started = time.perf_counter()
        query = " ".join(query.strip().split())
        if not query:
            return []
        if keyword_mode not in {"on_demand", "always", "disabled"}:
            raise ValueError(f"不支持的 keyword_mode: {keyword_mode}")

        k = top_k or settings.retrieval_top_k
        hybrid_k = settings.hybrid_top_k
        history = [item for item in (query_history or []) if item]
        complex_query = rewrite if rewrite is not None else is_complex_query(
            route_query or query,
            history=history,
        )
        route = "complex" if complex_query else "fast"
        history_key = tuple(
            history[-settings.query_rewrite_history_limit:]
        ) if complex_query else ()
        cache_key = (
            collection,
            _collection_revision(collection),
            query,
            subject_filter or "",
            history_key,
            k,
            rewrite,
            keyword_mode,
            route_query or "",
        )
        cached = _cache_get(cache_key)
        if cached is not None:
            _log_stage("total", started, route=route, cache_hit=1, results=len(cached))
            return cached

        if complex_query:
            results = await Retriever._complex_path(
                query,
                collection=collection,
                requested_k=k,
                hybrid_k=hybrid_k,
                subject_filter=subject_filter,
                query_history=history,
                keyword_mode=keyword_mode,
            )
        else:
            results = await Retriever._fast_path(
                query,
                collection=collection,
                requested_k=k,
                hybrid_k=hybrid_k,
                subject_filter=subject_filter,
                keyword_mode=keyword_mode,
            )

        if collection == "student":
            results = _student_safe_results(results)
        results = results[:k]
        _cache_put(cache_key, results)
        _log_stage("total", started, route=route, cache_hit=0, results=len(results))
        return results

    @staticmethod
    async def _fast_path(
        query: str,
        *,
        collection: str,
        requested_k: int,
        hybrid_k: int,
        subject_filter: str | None,
        keyword_mode: KeywordMode,
    ) -> list[dict]:
        if keyword_mode == "always":
            semantic_task = asyncio.create_task(Retriever._semantic_retrieve_many(
                [query], collection, hybrid_k, subject_filter
            ))
            keyword_task = asyncio.create_task(Retriever._keyword_retrieve(
                query, collection, hybrid_k, subject_filter
            ))
            semantic_lists, keyword_docs = await asyncio.gather(semantic_task, keyword_task)
            semantic_docs = semantic_lists[0]
        else:
            semantic_lists = await Retriever._semantic_retrieve_many(
                [query], collection, hybrid_k, subject_filter
            )
            semantic_docs = semantic_lists[0]
            keyword_docs = []
            if keyword_mode == "on_demand" and not _semantic_is_sufficient(
                semantic_docs, requested_k
            ):
                keyword_docs = await Retriever._keyword_retrieve(
                    query, collection, hybrid_k, subject_filter
                )

        if semantic_docs and keyword_docs:
            return _rrf_merge(semantic_docs, keyword_docs, hybrid_k)
        return semantic_docs or keyword_docs

    @staticmethod
    async def _complex_path(
        query: str,
        *,
        collection: str,
        requested_k: int,
        hybrid_k: int,
        subject_filter: str | None,
        query_history: list[str],
        keyword_mode: KeywordMode,
    ) -> list[dict]:
        original_task = asyncio.create_task(Retriever._semantic_retrieve_many(
            [query], collection, hybrid_k, subject_filter
        ))
        rewrite_task = asyncio.create_task(generate_query_variants(query, history=query_history))
        original_lists = await original_task
        original_docs = original_lists[0]

        if keyword_mode != "always" and _semantic_is_sufficient(original_docs, requested_k):
            rewrite_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await rewrite_task
            return original_docs

        variants = await rewrite_task
        variants = list(dict.fromkeys([query, *variants]))[:settings.query_rewrite_max_variants]
        extension_queries = variants[1:]
        extension_lists = await Retriever._semantic_retrieve_many(
            extension_queries, collection, hybrid_k, subject_filter
        ) if extension_queries else []
        semantic_lists = [original_docs, *extension_lists]

        keyword_queries = []
        if keyword_mode == "always":
            keyword_queries = variants
        elif keyword_mode == "on_demand":
            keyword_queries = [
                variant for variant, docs in zip(variants, semantic_lists)
                if not _semantic_is_sufficient(docs, requested_k)
            ]
        keyword_by_query = await Retriever._keyword_retrieve_many(
            keyword_queries, collection, hybrid_k, subject_filter
        )

        variant_results = []
        for variant, semantic_docs in zip(variants, semantic_lists):
            keyword_docs = keyword_by_query.get(variant, [])
            if semantic_docs and keyword_docs:
                variant_results.append(_rrf_merge(semantic_docs, keyword_docs, hybrid_k))
            elif semantic_docs or keyword_docs:
                variant_results.append(semantic_docs or keyword_docs)

        if not variant_results:
            return []
        if len(variant_results) == 1:
            return variant_results[0]
        return _rrf_merge_many(variant_results, hybrid_k)

    @staticmethod
    async def _semantic_retrieve_many(
        queries: list[str],
        collection: str,
        top_k: int,
        subject_filter: str | None,
    ) -> list[list[dict]]:
        if not queries:
            return []
        embedding_started = time.perf_counter()
        try:
            embeddings = await asyncio.wait_for(
                embedding_service.embed_queries(queries),
                timeout=settings.retrieval_embedding_timeout_seconds,
            )
        except asyncio.TimeoutError:
            logger.warning(
                "批量 Embedding 超过 %.1fs，将按需降级到关键词",
                settings.retrieval_embedding_timeout_seconds,
            )
            _log_stage("embedding", embedding_started, queries=len(queries), success=0)
            return [[] for _ in queries]
        except Exception as exc:
            logger.warning("批量 Embedding 失败，将按需降级到关键词: %s", exc)
            _log_stage("embedding", embedding_started, queries=len(queries), success=0)
            return [[] for _ in queries]
        _log_stage("embedding", embedding_started, queries=len(queries), success=1)

        output: list[list[dict]] = [[] for _ in queries]
        valid_indices = [index for index, embedding in enumerate(embeddings) if embedding]
        if not valid_indices:
            return output

        semantic_started = time.perf_counter()
        valid_embeddings = [embeddings[index] for index in valid_indices]
        try:
            searched = await asyncio.wait_for(
                asyncio.to_thread(
                    vector_store.search_many,
                    collection,
                    valid_embeddings,
                    top_k,
                    subject_filter,
                ),
                timeout=settings.retrieval_semantic_timeout_ms / 1000,
            )
        except asyncio.TimeoutError:
            logger.warning(
                "Chroma 批量语义查询超过 %dms，将按需降级到关键词",
                settings.retrieval_semantic_timeout_ms,
            )
            _log_stage("semantic", semantic_started, queries=len(valid_indices), success=0)
            return output
        for index, docs in zip(valid_indices, searched):
            output[index] = _filter_semantic_results(docs)

        if subject_filter:
            fallback_indices = [index for index in valid_indices if not output[index]]
            if fallback_indices:
                logger.info(
                    "subject_filter='%s' 有 %d 路无高相关结果，回退全局共享知识库",
                    subject_filter,
                    len(fallback_indices),
                )
                try:
                    fallback_results = await asyncio.wait_for(
                        asyncio.to_thread(
                            vector_store.search_many,
                            collection,
                            [embeddings[index] for index in fallback_indices],
                            top_k,
                            None,
                        ),
                        timeout=settings.retrieval_semantic_timeout_ms / 1000,
                    )
                except asyncio.TimeoutError:
                    logger.warning("Chroma 全局语义回退超时")
                    fallback_results = [[] for _ in fallback_indices]
                for index, docs in zip(fallback_indices, fallback_results):
                    output[index] = _filter_semantic_results(docs)
        _log_stage("semantic", semantic_started, queries=len(valid_indices))
        return output

    @staticmethod
    async def _keyword_retrieve_many(
        queries: list[str],
        collection: str,
        top_k: int,
        subject_filter: str | None,
    ) -> dict[str, list[dict]]:
        if not queries:
            return {}
        unique_queries = list(dict.fromkeys(queries))
        semaphore = asyncio.Semaphore(max(1, settings.query_rewrite_concurrency))

        async def retrieve_one(item: str):
            async with semaphore:
                return item, await Retriever._keyword_retrieve(
                    item, collection, top_k, subject_filter
                )

        pairs = await asyncio.gather(*(retrieve_one(item) for item in unique_queries))
        return dict(pairs)

    @staticmethod
    async def _retrieve_variant(
        query: str,
        collection: Literal["teacher", "student"],
        hybrid_k: int,
        subject_filter: str | None,
    ) -> list[dict]:
        """兼容单路调用；生产多路检索使用批量实现。"""
        try:
            q_emb = await embedding_service.embed_one(query)
            if not q_emb:
                raise RuntimeError("Embedding API 返回空向量")

            search = (
                vector_store.search_teacher if collection == "teacher"
                else vector_store.search_student
            )
            semantic_docs = _filter_semantic_results(await asyncio.to_thread(
                search, q_emb, hybrid_k, subject_filter
            ))

            if not semantic_docs and subject_filter:
                semantic_docs = _filter_semantic_results(await asyncio.to_thread(
                    search, q_emb, hybrid_k, None
                ))

            keyword_docs = await Retriever._keyword_retrieve(
                query, collection, hybrid_k, subject_filter
            ) if not _semantic_is_sufficient(semantic_docs, hybrid_k) else []
            if semantic_docs and keyword_docs:
                return _rrf_merge(semantic_docs, keyword_docs, hybrid_k)
            return semantic_docs or keyword_docs
        except Exception as exc:
            logger.warning("语义检索失败，降级到关键词匹配: %s", exc)
            return await Retriever._keyword_retrieve(
                query, collection, hybrid_k, subject_filter
            )

    @staticmethod
    async def _keyword_retrieve(
        query: str,
        collection: Literal["teacher", "student"] | str,
        top_k: int,
        subject_filter: str | None,
    ) -> list[dict]:
        started = time.perf_counter()

        async def search(subject: str | None) -> list[dict]:
            try:
                candidates = await asyncio.wait_for(
                    asyncio.to_thread(
                        vector_store.search_keyword,
                        collection,
                        query,
                        top_k,
                        subject,
                    ),
                    timeout=settings.keyword_query_timeout_ms / 1000,
                )
            except asyncio.TimeoutError:
                logger.warning(
                    "Chroma 关键词查询超过 %dms，转入索引兜底",
                    settings.keyword_query_timeout_ms,
                )
                return []
            scored = []
            for doc in candidates:
                score = keyword_score(query, doc.get("document", ""))
                if score >= settings.retrieval_min_keyword_score:
                    item = dict(doc)
                    item["distance"] = 1.0 - score
                    item["hybrid_score"] = round(score, 4)
                    scored.append((score, item))
            scored.sort(key=lambda item: item[0], reverse=True)
            return [doc for _, doc in scored[:top_k]]

        results = await search(subject_filter)
        if not results and subject_filter:
            logger.info("关键词在科目 '%s' 内无命中，回退全局共享知识库", subject_filter)
            results = await search(None)
        if results:
            _log_stage("keyword", started, source="chroma", results=len(results))
            return results

        try:
            document_count = await asyncio.wait_for(
                asyncio.to_thread(vector_store.count_documents, collection),
                timeout=settings.keyword_query_timeout_ms / 1000,
            )
        except asyncio.TimeoutError:
            logger.warning("Chroma 文档计数超时，停止关键词降级")
            return []
        if document_count > settings.keyword_fallback_max_docs:
            logger.warning(
                "关键词索引兜底跳过 %d 条记录（上限 %d）",
                document_count,
                settings.keyword_fallback_max_docs,
            )
            _log_stage("keyword", started, source="bounded_empty", results=0)
            return []

        async def indexed_search(subject: str | None) -> list[dict]:
            return await asyncio.wait_for(
                asyncio.to_thread(
                    keyword_fallback_index.search,
                    collection,
                    query,
                    revision=_collection_revision(collection),
                    document_count=document_count,
                    loader=vector_store.get_all_documents,
                    top_k=top_k,
                    subject_filter=subject,
                ),
                timeout=settings.keyword_fallback_timeout_ms / 1000,
            )

        try:
            results = await indexed_search(subject_filter)
            if not results and subject_filter:
                results = await indexed_search(None)
        except asyncio.TimeoutError:
            logger.warning(
                "关键词索引兜底超过 %dms，返回空结果",
                settings.keyword_fallback_timeout_ms,
            )
            results = []
        _log_stage("keyword", started, source="memory_index", results=len(results))
        return results


# 模块级单例
retriever = Retriever()
