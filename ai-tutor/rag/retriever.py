"""检索器 — 语义检索（API） + 关键词后备（纯本地）"""
import logging
import re
import sqlite3
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
                    return vector_store.search_teacher(q_emb, top_k=k, subject_filter=subject_filter)
                else:
                    return vector_store.search_student(q_emb, top_k=k, subject_filter=subject_filter)
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
        """纯本地关键词检索，不依赖任何外部 API"""
        table = "teacher_kb" if collection == "teacher" else "student_kb"
        with sqlite3.connect(settings.vector_db_path) as conn:
            if subject_filter:
                rows = conn.execute(
                    f"SELECT id, text, subject, source_file, question_index FROM {table} WHERE subject = ? LIMIT 500",
                    (subject_filter,),
                ).fetchall()
            else:
                rows = conn.execute(
                    f"SELECT id, text, subject, source_file, question_index FROM {table} LIMIT 500"
                ).fetchall()

        if not rows:
            return []

        if len(rows) >= 500:
            logger.warning(
                "关键词检索在表 %s 的 %d 条记录中被 LIMIT 截断，召回范围受限",
                table, len(rows),
            )

        scored = []
        for row in rows:
            score = _keyword_score(query, row[1])
            if score > 0:
                scored.append((score, {
                    "id": row[0],
                    "document": row[1],
                    "metadata": {
                        "subject": row[2],
                        "source_file": row[3],
                        "question_index": row[4],
                    },
                    "distance": 1.0 - score,
                }))

        scored.sort(key=lambda x: x[0], reverse=True)
        return [item[1] for item in scored[:top_k]]


# 模块级单例
retriever = Retriever()
