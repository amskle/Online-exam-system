"""向量存储 — ChromaDB PersistentClient（HNSW 索引，O(log n) 检索）"""
import logging

import chromadb
from chromadb.config import Settings as ChromaSettings

from config.settings import get_settings

settings = get_settings()
logger = logging.getLogger("ai-tutor.vector_store")


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
        # ChromaDB 的 metadatas 要求所有值都是 str/int/float/bool
        clean_metas = [
            {
                "subject": str(m.get("subject", "")),
                "source_file": str(m.get("source_file", "")),
                "question_index": int(m.get("question_index", 0)),
            }
            for m in metadatas
        ]
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
        clean_metas = [
            {
                "subject": str(m.get("subject", "")),
                "source_file": str(m.get("source_file", "")),
                "question_index": int(m.get("question_index", 0)),
            }
            for m in metadatas
        ]
        self._student.add(ids=ids, documents=documents, embeddings=embeddings, metadatas=clean_metas)

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

    @staticmethod
    def _search(
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
                "metadata": {
                    "subject": meta.get("subject", ""),
                    "source_file": meta.get("source_file", ""),
                    "question_index": meta.get("question_index", 0),
                },
                "distance": float(dists[i]) if i < len(dists) else 1.0,
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
                "metadata": {
                    "subject": meta.get("subject", ""),
                    "source_file": meta.get("source_file", ""),
                    "question_index": meta.get("question_index", 0),
                },
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
