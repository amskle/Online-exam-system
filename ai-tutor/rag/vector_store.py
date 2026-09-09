"""向量存储 — ChromaDB PersistentClient（HNSW 索引，O(log n) 检索）"""
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
            student_after = self._existing_ids(self._student, student_ids)
            if teacher_after != set(teacher_ids) or student_after != set(student_ids):
                raise RuntimeError("双库写入后完整性校验失败")
        except Exception:
            # 删除所有本次调用前不存在的目标 ID；即使底层发生部分批次写入也能补偿。
            self._delete_ids(self._teacher, teacher_added)
            self._delete_ids(self._student, student_added)
            logger.exception("双库写入失败，已执行补偿删除")
            raise

        paired_before = sum(
            1 for teacher_id, student_id in zip(teacher_ids, student_ids)
            if teacher_id in teacher_before and student_id in student_before
        )
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

    def _search(
        self,
        collection,
        query_embedding: list[float],
        top_k: int | None = None,
        subject_filter: str | None = None,
    ) -> list[dict]:
        k = top_k or settings.retrieval_top_k
        where = {"subject": subject_filter} if subject_filter else None

        try:
            results = collection.query(
                query_embeddings=[query_embedding],
                n_results=k,
                where=where,
                include=["documents", "metadatas", "distances"],
            )
        except Exception:
            logger.warning("ChromaDB 检索失败（可能是空库或 filter 无匹配）", exc_info=True)
            return []

        # ChromaDB 批量查询返回二维列表，我们只查单个 embedding
        ids = results.get("ids", [[]])[0]
        docs = results.get("documents", [[]])[0]
        metas = results.get("metadatas", [[]])[0]
        dists = results.get("distances", [[]])[0]

        output = []
        for i in range(len(ids)):
            meta = metas[i] if i < len(metas) and metas[i] else {}
            output.append({
                "id": ids[i],
                "document": docs[i] if i < len(docs) else "",
                "metadata": self._metadata_from(meta),
                "distance": float(dists[i]) if i < len(dists) else 1.0,
            })
        return output

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
            for cond in conditions:
                try:
                    partial = _get(cond)
                    self._merge_results(merged, partial)
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
            safe_limit = max(1, min(limit, 1000))
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

    def clear_teacher(self):
        try:
            self.client.delete_collection("teacher_kb")
        except Exception:
            pass
        self._teacher = self.client.create_collection(
            name="teacher_kb",
            metadata={"hnsw:space": "cosine"},
        )

    def clear_student(self):
        try:
            self.client.delete_collection("student_kb")
        except Exception:
            pass
        self._student = self.client.create_collection(
            name="student_kb",
            metadata={"hnsw:space": "cosine"},
        )


# 模块级单例
vector_store = VectorStore()
