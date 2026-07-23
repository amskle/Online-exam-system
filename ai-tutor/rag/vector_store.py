"""向量存储 — SQLite + numpy 余弦相似度，纯 Python 零编译"""
import json
import sqlite3
import numpy as np
from config.settings import get_settings

settings = get_settings()


def _cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    """余弦相似度"""
    dot = np.dot(a, b)
    norm_a = np.linalg.norm(a)
    norm_b = np.linalg.norm(b)
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return float(dot / (norm_a * norm_b))


class VectorStore:
    """SQLite 向量存储，teacher_kb / student_kb 两张表物理隔离"""

    def __init__(self):
        self.db_path = settings.vector_db_path
        self._init_db()

    # ── 初始化 ──

    def _init_db(self):
        """创建表"""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS teacher_kb (
                    id TEXT PRIMARY KEY,
                    embedding TEXT NOT NULL,  -- JSON float array
                    text TEXT NOT NULL,
                    subject TEXT DEFAULT '',
                    source_file TEXT DEFAULT '',
                    question_index INTEGER DEFAULT 0
                )
            """)
            conn.execute("""
                CREATE TABLE IF NOT EXISTS student_kb (
                    id TEXT PRIMARY KEY,
                    embedding TEXT NOT NULL,
                    text TEXT NOT NULL,
                    subject TEXT DEFAULT '',
                    source_file TEXT DEFAULT '',
                    question_index INTEGER DEFAULT 0
                )
            """)
            conn.commit()

    # ── 写入 ──

    def _insert_batch(self, table: str, ids: list[str], documents: list[str],
                      embeddings: list[list[float]], metadatas: list[dict]):
        with sqlite3.connect(self.db_path) as conn:
            rows = []
            for i, doc_id in enumerate(ids):
                rows.append((
                    doc_id,
                    json.dumps(embeddings[i]),
                    documents[i],
                    metadatas[i].get("subject", ""),
                    metadatas[i].get("source_file", ""),
                    metadatas[i].get("question_index", 0),
                ))
            conn.executemany(
                f"INSERT OR REPLACE INTO {table} (id, embedding, text, subject, source_file, question_index) VALUES (?, ?, ?, ?, ?, ?)",
                rows,
            )
            conn.commit()

    def add_to_teacher(
        self,
        ids: list[str],
        documents: list[str],
        embeddings: list[list[float]],
        metadatas: list[dict],
    ):
        self._insert_batch("teacher_kb", ids, documents, embeddings, metadatas)

    def add_to_student(
        self,
        ids: list[str],
        documents: list[str],
        embeddings: list[list[float]],
        metadatas: list[dict],
    ):
        self._insert_batch("student_kb", ids, documents, embeddings, metadatas)

    # ── 检索 ──

    def search_teacher(
        self,
        query_embedding: list[float],
        top_k: int | None = None,
        subject_filter: str | None = None,
    ) -> list[dict]:
        return self._search("teacher_kb", query_embedding, top_k, subject_filter)

    def search_student(
        self,
        query_embedding: list[float],
        top_k: int | None = None,
        subject_filter: str | None = None,
    ) -> list[dict]:
        return self._search("student_kb", query_embedding, top_k, subject_filter)

    def _search(
        self,
        table: str,
        query_embedding: list[float],
        top_k: int | None = None,
        subject_filter: str | None = None,
    ) -> list[dict]:
        k = top_k or settings.retrieval_top_k
        query_vec = np.array(query_embedding, dtype=np.float32)

        with sqlite3.connect(self.db_path) as conn:
            if subject_filter:
                rows = conn.execute(
                    f"SELECT id, embedding, text, subject, source_file, question_index FROM {table} WHERE subject = ?",
                    (subject_filter,),
                ).fetchall()
            else:
                rows = conn.execute(
                    f"SELECT id, embedding, text, subject, source_file, question_index FROM {table}"
                ).fetchall()

        if not rows:
            return []

        # 计算余弦相似度
        scored = []
        for row in rows:
            emb = np.array(json.loads(row[1]), dtype=np.float32)
            sim = _cosine_similarity(query_vec, emb)
            scored.append((sim, {
                "id": row[0],
                "document": row[2],
                "metadata": {
                    "subject": row[3],
                    "source_file": row[4],
                    "question_index": row[5],
                },
                "distance": 1.0 - sim,  # distance = 1 - cosine_similarity
            }))

        # 按相似度降序，取 top_k
        scored.sort(key=lambda x: x[0], reverse=True)
        return [item[1] for item in scored[:k]]

    # ── 清空 ──

    def clear_teacher(self):
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("DELETE FROM teacher_kb")
            conn.commit()

    def clear_student(self):
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("DELETE FROM student_kb")
            conn.commit()


# 模块级单例
vector_store = VectorStore()
