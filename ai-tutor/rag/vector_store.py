"""向量存储 — ChromaDB PersistentClient（HNSW 索引，O(log n) 检索）"""
import logging

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

    def get_all_documents(self, collection_name: str) -> list[dict]:
        """获取 collection 中全部文档（含 metadata），用于本地关键词评分"""
        col = self._teacher if collection_name == "teacher" else self._student
        try:
            results = col.get(include=["documents", "metadatas"])
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
