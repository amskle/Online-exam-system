"""会话存储 — SQLite 持久化学生答疑对话历史（服务端可信来源，前端不再提交历史）"""
import logging
import sqlite3
import time
import uuid

from config.settings import get_settings

settings = get_settings()
logger = logging.getLogger("ai-tutor.sessions")


class SessionStore:
    """按 (session_id, user_id) 隔离的对话历史存储"""

    def __init__(self):
        self.db_path = settings.session_db_path
        self._init_db()

    def _init_db(self):
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL,
                    user_id INTEGER NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    created_at REAL NOT NULL
                )
            """)
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_session ON chat_messages (session_id, user_id, id)"
            )
            conn.commit()

    def new_session_id(self) -> str:
        return uuid.uuid4().hex

    def append(self, session_id: str, user_id: int, role: str, content: str):
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                "INSERT INTO chat_messages (session_id, user_id, role, content, created_at) VALUES (?, ?, ?, ?, ?)",
                (session_id, user_id, role, content, time.time()),
            )
            # 只保留每个会话最近 N 条，防止无限增长
            conn.execute(
                """
                DELETE FROM chat_messages
                WHERE session_id = ? AND user_id = ? AND id NOT IN (
                    SELECT id FROM chat_messages
                    WHERE session_id = ? AND user_id = ?
                    ORDER BY id DESC LIMIT ?
                )
                """,
                (session_id, user_id, session_id, user_id, settings.session_max_messages),
            )
            conn.commit()

    def history(self, session_id: str, user_id: int, limit: int | None = None) -> list[dict]:
        """取最近 limit 条历史，按时间正序返回"""
        k = limit or settings.session_history_limit
        with sqlite3.connect(self.db_path) as conn:
            rows = conn.execute(
                """
                SELECT role, content FROM (
                    SELECT id, role, content FROM chat_messages
                    WHERE session_id = ? AND user_id = ?
                    ORDER BY id DESC LIMIT ?
                ) ORDER BY id ASC
                """,
                (session_id, user_id, k),
            ).fetchall()
        return [{"role": r[0], "content": r[1]} for r in rows]

    def clear(self, session_id: str, user_id: int):
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                "DELETE FROM chat_messages WHERE session_id = ? AND user_id = ?",
                (session_id, user_id),
            )
            conn.commit()


# 模块级单例
session_store = SessionStore()
