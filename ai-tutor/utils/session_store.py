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
            conn.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    session_id TEXT PRIMARY KEY,
                    user_id INTEGER NOT NULL,
                    agent_mode TEXT NOT NULL DEFAULT 'student',
                    title TEXT NOT NULL DEFAULT '',
                    created_at REAL NOT NULL,
                    updated_at REAL NOT NULL
                )
            """)
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions (user_id, updated_at DESC)"
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
        self.touch_session(session_id)

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

    def ensure_session(self, session_id: str, user_id: int, agent_mode: str, title: str = ""):
        """确保 session 元数据存在（INSERT OR IGNORE），首次设定标题，每次调用更新 updated_at"""
        now = time.time()
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                "INSERT OR IGNORE INTO sessions (session_id, user_id, agent_mode, title, created_at, updated_at) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                (session_id, user_id, agent_mode, title, now, now),
            )
            # 如果传入了标题且已有标题为空，则更新标题
            if title:
                conn.execute(
                    "UPDATE sessions SET title = ? WHERE session_id = ? AND (title = '' OR title IS NULL)",
                    (title, session_id),
                )
            conn.execute(
                "UPDATE sessions SET updated_at = ? WHERE session_id = ?",
                (now, session_id),
            )
            conn.commit()

    def touch_session(self, session_id: str):
        """更新 session 的 updated_at 时间戳"""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                "UPDATE sessions SET updated_at = ? WHERE session_id = ?",
                (time.time(), session_id),
            )
            conn.commit()

    def list_sessions(self, user_id: int, agent_mode: str | None = None, limit: int = 50) -> list[dict]:
        """列出用户的 session 摘要，按最近更新时间倒序，含最后一条 assistant 消息预览"""
        with sqlite3.connect(self.db_path) as conn:
            if agent_mode:
                rows = conn.execute(
                    "SELECT session_id, agent_mode, title, created_at, updated_at "
                    "FROM sessions WHERE user_id = ? AND agent_mode = ? "
                    "ORDER BY updated_at DESC LIMIT ?",
                    (user_id, agent_mode, limit),
                ).fetchall()
            else:
                rows = conn.execute(
                    "SELECT session_id, agent_mode, title, created_at, updated_at "
                    "FROM sessions WHERE user_id = ? "
                    "ORDER BY updated_at DESC LIMIT ?",
                    (user_id, limit),
                ).fetchall()

        sessions = []
        with sqlite3.connect(self.db_path) as conn:
            for r in rows:
                sid = r[0]
                # 取最后一条 assistant 消息作为预览
                preview_row = conn.execute(
                    "SELECT content FROM chat_messages "
                    "WHERE session_id = ? AND role = 'assistant' "
                    "ORDER BY id DESC LIMIT 1",
                    (sid,),
                ).fetchone()
                preview = ""
                if preview_row and preview_row[0]:
                    raw = preview_row[0]
                    preview = raw[:100] + ("..." if len(raw) > 100 else "")
                sessions.append({
                    "session_id": sid,
                    "agent_mode": r[1],
                    "title": r[2],
                    "created_at": r[3],
                    "updated_at": r[4],
                    "preview": preview,
                })
        return sessions

    def delete_session(self, session_id: str, user_id: int):
        """删除指定 session 及其所有聊天消息"""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                "DELETE FROM chat_messages WHERE session_id = ? AND user_id = ?",
                (session_id, user_id),
            )
            conn.execute(
                "DELETE FROM sessions WHERE session_id = ? AND user_id = ?",
                (session_id, user_id),
            )
            conn.commit()

    def get_session(self, session_id: str, user_id: int) -> dict | None:
        """获取单个 session 的元数据 + 全部消息"""
        with sqlite3.connect(self.db_path) as conn:
            meta = conn.execute(
                "SELECT session_id, agent_mode, title, created_at, updated_at "
                "FROM sessions WHERE session_id = ? AND user_id = ?",
                (session_id, user_id),
            ).fetchone()
            if not meta:
                return None
        messages = self.history(session_id, user_id, limit=999)  # 取全部消息
        return {
            "session_id": meta[0],
            "agent_mode": meta[1],
            "title": meta[2],
            "created_at": meta[3],
            "updated_at": meta[4],
            "messages": messages,
        }


# 模块级单例
session_store = SessionStore()
