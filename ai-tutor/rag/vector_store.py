"""向量存储 — ChromaDB PersistentClient（HNSW 索引，O(log n) 检索）"""
from concurrent.futures import ThreadPoolExecutor, as_completed
import logging
import threading

import chromadb
from chromadb.config import Settings as ChromaSettings

from config.settings import get_settings
from rag.keywords import extract_keyword_terms

settings = get_settings()
logger = logging.getLogger("ai-tutor.vector_store")


_DEFAULT_META = {
    "subject": "",
    "source_file": "",
    "format": "",
    "structure_type": "",
    "chunk_type": "",
    "chunk_index": 0,
    "question_index": 0,
    "section_path": "",
    "section_title": "",
    "page_range": "",
    "created_at": 0,
    "modified_at": 0,
    "uploaded_at": 0,
    "content_hash": "",
    "upload_id": "",
}


class VectorStore:
    """ChromaDB 向量存储，teacher_kb / student_kb 两个 Collection 物理隔离"""

    def __init__(self):
        self.client = chromadb.PersistentClient(
            path=settings.vector_db_path,
            settings=ChromaSettings(anonymized_telemetry=False),
        )
        self._teacher = self.client.get_or_create_collection(
            name="teacher_kb",
            metadata={"hnsw:space": "cosine"},
        )
        self._student = self.client.get_or_create_collection(
            name="student_kb",
            metadata={"hnsw:space": "cosine"},
        )
        self._write_lock = threading.RLock()
        self._revisions = {"teacher": 0, "student": 0}

    # ── 写入 ──

    def add_to_teacher(
        self,
        ids: list[str],
        documents: list[str],
        embeddings: list[list[float]],
        metadatas: list[dict],
    ):
        if not ids:
            return
        clean_metas = [self._clean_metadata(m) for m in metadatas]
        self._teacher.add(ids=ids, documents=documents, embeddings=embeddings, metadatas=clean_metas)
        self._touch("teacher")

    def add_to_student(
        self,
        ids: list[str],
        documents: list[str],
        embeddings: list[list[float]],
        metadatas: list[dict],
    ):
        if not ids:
            return
        clean_metas = [self._clean_metadata(m) for m in metadatas]
        self._student.add(ids=ids, documents=documents, embeddings=embeddings, metadatas=clean_metas)
        self._touch("student")

    @staticmethod
    def _chunks(items: list, size: int):
        for start in range(0, len(items), size):
            yield items[start:start + size]

    def _existing_ids(self, collection, ids: list[str]) -> set[str]:
        existing: set[str] = set()
        for batch in self._chunks(ids, settings.embedding_batch_size):
            if batch:
                existing.update(collection.get(ids=batch, include=[]).get("ids", []))
        return existing

    @staticmethod
    def _delete_ids(collection, ids: list[str]):
        if ids:
            collection.delete(ids=ids)

    def add_consistent_pairs(
        self,
        teacher_ids: list[str],
        student_ids: list[str],
        teacher_documents: list[str],
        student_documents: list[str],
        teacher_embeddings: list[list[float]],
        student_embeddings: list[list[float]],
        metadatas: list[dict],
    ) -> dict[str, int]:
        """串行化双库提交，避免并发上传的去重/补偿竞态。"""
        lock = getattr(self, "_write_lock", None)
        if lock is None:
            lock = self._write_lock = threading.RLock()
        with lock:
            return self._add_consistent_pairs_locked(
                teacher_ids, student_ids, teacher_documents, student_documents,
                teacher_embeddings, student_embeddings, metadatas,
            )

    def _add_consistent_pairs_locked(
        self,
        teacher_ids: list[str],
        student_ids: list[str],
        teacher_documents: list[str],
        student_documents: list[str],
        teacher_embeddings: list[list[float]],
        student_embeddings: list[list[float]],
        metadatas: list[dict],
    ) -> dict[str, int]:
        """成对写入双库；失败时删除本次新增内容，避免留下半次上传。"""
        lengths = {
            len(teacher_ids), len(student_ids), len(teacher_documents),
            len(student_documents), len(teacher_embeddings),
            len(student_embeddings), len(metadatas),
        }
        if len(lengths) != 1:
            raise ValueError("双库写入参数长度不一致")
        if not teacher_ids:
            return {"inserted": 0, "deduplicated": 0}
        if len(set(teacher_ids)) != len(teacher_ids) or len(set(student_ids)) != len(student_ids):
            raise ValueError("双库写入 ID 存在重复")

        clean_metas = [self._clean_metadata(meta) for meta in metadatas]
        teacher_before = self._existing_ids(self._teacher, teacher_ids)
        student_before = self._existing_ids(self._student, student_ids)
        teacher_added = [doc_id for doc_id in teacher_ids if doc_id not in teacher_before]
        student_added = [doc_id for doc_id in student_ids if doc_id not in student_before]
        # Refresh stale student content on re-upload; retain rollback snapshots.
        student_originals: dict[str, tuple] = {}
        desired = dict(zip(student_ids, student_documents))
        for batch in self._chunks(sorted(student_before), settings.embedding_batch_size):
            old = self._student.get(ids=batch, include=["documents", "embeddings", "metadatas"])
            for i, doc_id in enumerate(old.get("ids", [])):
                documents = old.get("documents") or []
                if i < len(documents) and documents[i] != desired[doc_id]:
                    student_originals[doc_id] = (
                        documents[i], list(old["embeddings"][i]), old["metadatas"][i],
                    )

        def add_missing(collection, ids, documents, embeddings, wanted):
            indices = [i for i, doc_id in enumerate(ids) if doc_id in wanted]
            for index_batch in self._chunks(indices, settings.embedding_batch_size):
                collection.add(
                    ids=[ids[i] for i in index_batch],
                    documents=[documents[i] for i in index_batch],
                    embeddings=[embeddings[i] for i in index_batch],
                    metadatas=[clean_metas[i] for i in index_batch],
                )

        try:
            add_missing(
                self._teacher, teacher_ids, teacher_documents,
                teacher_embeddings, set(teacher_added),
            )
            add_missing(
                self._student, student_ids, student_documents,
                student_embeddings, set(student_added),
            )
            teacher_after = self._existing_ids(self._teacher, teacher_ids)
            for batch in self._chunks(
                [i for i, doc_id in enumerate(student_ids) if doc_id in student_originals],
                settings.embedding_batch_size,
            ):
                self._student.update(
                    ids=[student_ids[i] for i in batch],
                    documents=[student_documents[i] for i in batch],
                    embeddings=[student_embeddings[i] for i in batch],
                    metadatas=[clean_metas[i] for i in batch],
                )
            student_after = self._existing_ids(self._student, student_ids)
            if teacher_after != set(teacher_ids) or student_after != set(student_ids):
                raise RuntimeError("双库写入后完整性校验失败")
        except Exception:
            # 删除所有本次调用前不存在的目标 ID；即使底层发生部分批次写入也能补偿。
            self._delete_ids(self._teacher, teacher_added)
            self._delete_ids(self._student, student_added)
            for batch in self._chunks(list(student_originals), settings.embedding_batch_size):
                self._student.update(
                    ids=batch,
                    documents=[student_originals[doc_id][0] for doc_id in batch],
                    embeddings=[student_originals[doc_id][1] for doc_id in batch],
                    metadatas=[student_originals[doc_id][2] for doc_id in batch],
                )
            logger.exception("双库写入失败，已执行补偿删除")
            raise

        paired_before = sum(
            1 for teacher_id, student_id in zip(teacher_ids, student_ids)
            if teacher_id in teacher_before and student_id in student_before
            and student_id not in student_originals
        )
        if len(teacher_ids) != paired_before:
            self._touch("teacher")
            self._touch("student")
        return {
            "inserted": len(teacher_ids) - paired_before,
            "deduplicated": paired_before,
        }

    @staticmethod
    def _clean_metadata(metadata: dict) -> dict:
        cleaned = {}
        for key, default in _DEFAULT_META.items():
            value = metadata.get(key, default)
            if key in ("chunk_index", "question_index"):
                cleaned[key] = int(value or 0)
            elif key in ("created_at", "modified_at", "uploaded_at"):
                try:
                    cleaned[key] = float(value or 0)
                except (TypeError, ValueError):
                    cleaned[key] = 0.0
            else:
                cleaned[key] = str(value or "")
        return cleaned

    @staticmethod
    def _metadata_from(meta: dict) -> dict:
        return {
            key: meta.get(key, default)
            for key, default in _DEFAULT_META.items()
        }

    # ── 检索 ──

    def search_teacher(
        self,
        query_embedding: list[float],
        top_k: int | None = None,
        subject_filter: str | None = None,
    ) -> list[dict]:
        return self._search(self._teacher, query_embedding, top_k, subject_filter)

    def search_student(
        self,
        query_embedding: list[float],
        top_k: int | None = None,
        subject_filter: str | None = None,
    ) -> list[dict]:
        return self._search(self._student, query_embedding, top_k, subject_filter)

    def search_many(
        self,
        collection: str,
        query_embeddings: list[list[float]],
        top_k: int | None = None,
        subject_filter: str | None = None,
    ) -> list[list[dict]]:
        """一次 Chroma 调用检索多条 Query，返回与向量顺序一致的结果列表。"""
        col = self._teacher if collection == "teacher" else self._student
        return self._search_many(col, query_embeddings, top_k, subject_filter)

    def _search(
        self,
        collection,
        query_embedding: list[float],
        top_k: int | None = None,
        subject_filter: str | None = None,
    ) -> list[dict]:
        results = self._search_many(
            collection,
            [query_embedding],
            top_k=top_k,
            subject_filter=subject_filter,
        )
        return results[0] if results else []

    def _search_many(
        self,
        collection,
        query_embeddings: list[list[float]],
        top_k: int | None = None,
        subject_filter: str | None = None,
    ) -> list[list[dict]]:
        if not query_embeddings:
            return []
        k = top_k or settings.retrieval_top_k
        where = {"subject": subject_filter} if subject_filter else None

        try:
            results = collection.query(
                query_embeddings=query_embeddings,
                n_results=k,
                where=where,
                include=["documents", "metadatas", "distances"],
            )
        except Exception:
            logger.warning("ChromaDB 检索失败（可能是空库或 filter 无匹配）", exc_info=True)
            return [[] for _ in query_embeddings]

        all_ids = results.get("ids", [])
        all_docs = results.get("documents", [])
        all_metas = results.get("metadatas", [])
        all_dists = results.get("distances", [])
        batches: list[list[dict]] = []
        for query_index in range(len(query_embeddings)):
            ids = all_ids[query_index] if query_index < len(all_ids) else []
            docs = all_docs[query_index] if query_index < len(all_docs) else []
            metas = all_metas[query_index] if query_index < len(all_metas) else []
            dists = all_dists[query_index] if query_index < len(all_dists) else []
            output = []
            for i, doc_id in enumerate(ids):
                meta = metas[i] if i < len(metas) and metas[i] else {}
                output.append({
                    "id": doc_id,
                    "document": docs[i] if i < len(docs) else "",
                    "metadata": self._metadata_from(meta),
                    "distance": float(dists[i]) if i < len(dists) else 1.0,
                })
            batches.append(output)
        return batches

    def search_keyword(
        self,
        collection: str,
        query: str,
        top_k: int | None = None,
        subject_filter: str | None = None,
    ) -> list[dict]:
        """基于 Chroma $contains 的关键词候选查询，超过 5 万条时降级为空。"""
        col = self._teacher if collection == "teacher" else self._student
        try:
            if col.count() > settings.keyword_max_docs:
                logger.warning(
                    "%s 超过 %d 条，关键词检索降级为纯语义检索",
                    collection, settings.keyword_max_docs,
                )
                return []
        except Exception:
            return []

        terms = extract_keyword_terms(query)
        conditions: list[dict] = []
        for term in terms:
            conditions.append({"$contains": term})
            if any(ch.isascii() and ch.isalpha() for ch in term):
                conditions.append({"$contains": term.lower()})
        if not conditions:
            return []

        where_document: dict = {"$or": conditions} if len(conditions) > 1 else conditions[0]
        where = {"subject": subject_filter} if subject_filter else None
        k = top_k or settings.hybrid_top_k

        def _get(criteria):
            return col.get(
                where_document=criteria,
                where=where,
                include=["documents", "metadatas"],
                limit=k * 2,
            )

        try:
            results = _get(where_document)
        except Exception:
            logger.warning("ChromaDB $or 关键词查询失败，逐词回退", exc_info=True)
            merged: dict[str, dict] = {}
            with ThreadPoolExecutor(max_workers=min(len(conditions), 8)) as pool:
                futures = [pool.submit(_get, cond) for cond in conditions]
                for future in as_completed(futures):
                    try:
                        self._merge_results(merged, future.result())
                    except Exception:
                        continue
            return list(merged.values())

        return self._build_results(results)

    @staticmethod
    def _merge_results(target: dict[str, dict], partial: dict):
        ids = partial.get("ids", [])
        docs = partial.get("documents", [])
        metas = partial.get("metadatas", [])
        for i, doc_id in enumerate(ids):
            if doc_id in target:
                continue
            meta = metas[i] if i < len(metas) and metas[i] else {}
            target[doc_id] = {
                "id": doc_id,
                "document": docs[i] if i < len(docs) else "",
                "metadata": VectorStore._metadata_from(meta),
                "distance": 1.0,
            }

    @staticmethod
    def _build_results(results: dict) -> list[dict]:
        output = []
        ids = results.get("ids", [])
        docs = results.get("documents", [])
        metas = results.get("metadatas", [])
        for i, doc_id in enumerate(ids):
            meta = metas[i] if i < len(metas) and metas[i] else {}
            output.append({
                "id": doc_id,
                "document": docs[i] if i < len(docs) else "",
                "metadata": VectorStore._metadata_from(meta),
                "distance": 1.0,
            })
        return output

    # ── 批量获取（供关键词检索降级使用）──

    def count_documents(self, collection_name: str) -> int:
        """返回 collection 文档数，不加载文档正文。"""
        col = self._teacher if collection_name == "teacher" else self._student
        try:
            return int(col.count())
        except Exception:
            logger.warning("ChromaDB count() 失败", exc_info=True)
            return 0

    def get_all_documents(self, collection_name: str, limit: int = 1000) -> list[dict]:
        """有限量获取文档（含 metadata），避免一次读取整个向量库。"""
        col = self._teacher if collection_name == "teacher" else self._student
        try:
            safe_limit = max(1, min(limit, settings.keyword_fallback_max_docs))
            results = col.get(include=["documents", "metadatas"], limit=safe_limit)
        except Exception:
            logger.warning("ChromaDB get() 失败", exc_info=True)
            return []

        ids = results.get("ids", [])
        docs = results.get("documents", [])
        metas = results.get("metadatas", [])

        output = []
        for i in range(len(ids)):
            meta = metas[i] if i < len(metas) and metas[i] else {}
            output.append({
                "id": ids[i],
                "document": docs[i] if i < len(docs) else "",
                "metadata": self._metadata_from(meta),
            })
        return output

    # ── 清空 ──

    def _touch(self, collection_name: str):
        revisions = getattr(self, "_revisions", None)
        if revisions is None:
            revisions = self._revisions = {"teacher": 0, "student": 0}
        revisions[collection_name] = revisions.get(collection_name, 0) + 1

    def revision(self, collection_name: str) -> int:
        """返回进程内集合版本，用于使检索与倒排索引缓存自动失效。"""
        return getattr(self, "_revisions", {}).get(collection_name, 0)

    def clear_teacher(self):
        try:
            self.client.delete_collection("teacher_kb")
        except Exception:
            pass
        self._teacher = self.client.create_collection(
            name="teacher_kb",
            metadata={"hnsw:space": "cosine"},
        )
        self._touch("teacher")

    def clear_student(self):
        try:
            self.client.delete_collection("student_kb")
        except Exception:
            pass
        self._student = self.client.create_collection(
            name="student_kb",
            metadata={"hnsw:space": "cosine"},
        )
        self._touch("student")


# 模块级单例
vector_store = VectorStore()
