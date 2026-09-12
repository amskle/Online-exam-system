"""考试系统桥接 — 通过 HTTP 调用 Spring Boot 接口进行数据交互"""
import logging

import httpx
from config.settings import get_settings

settings = get_settings()
logger = logging.getLogger("ai-tutor.bridge")


class ExamBridgeError(RuntimeError):
    """后端 HTTP 或业务响应失败。"""


class ExamBridge:
    """封装对 exam-backend (Spring Boot) 的 HTTP 调用，共享一个 AsyncClient"""

    def __init__(self):
        self.base = settings.exam_backend_url.rstrip("/")
        self._client: httpx.AsyncClient | None = None

    def _get_client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(timeout=30)
        return self._client

    async def close(self):
        if self._client is not None and not self._client.is_closed:
            await self._client.aclose()
        self._client = None

    def _headers(self, token: str) -> dict:
        return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    @staticmethod
    def _checked_body(response: httpx.Response, operation: str) -> dict:
        """同时校验 HTTP 状态、JSON 结构和 Spring Boot Result.code。"""
        try:
            response.raise_for_status()
        except httpx.HTTPStatusError as exc:
            raise ExamBridgeError(
                f"{operation} HTTP 失败: {response.status_code}"
            ) from exc
        try:
            body = response.json()
        except ValueError as exc:
            raise ExamBridgeError(f"{operation} 返回了无效 JSON") from exc
        if not isinstance(body, dict):
            raise ExamBridgeError(f"{operation} 响应格式无效")
        if body.get("code") != 200:
            message = body.get("message") or body.get("msg") or "未知业务错误"
            raise ExamBridgeError(
                f"{operation} 业务失败: code={body.get('code')}, message={message}"
            )
        return body

    async def validate_auth(self, token: str) -> dict | None:
        """让后端校验用户是否仍存在、未被停用且登录版本有效。"""
        r = await self._get_client().get(
            f"{self.base}/user/auth",
            headers=self._headers(token),
        )
        if r.status_code in (401, 403):
            return None
        try:
            body = self._checked_body(r, "校验登录状态")
        except ExamBridgeError:
            return None
        data = body.get("data")
        return data if isinstance(data, dict) else None

    # ── 题目相关 ──────────────────────────────────

    async def get_questions(
        self,
        token: str,
        subject_name: str | None = None,
        question_type: int | None = None,
        difficulty: int | None = None,
        page: int = 1,
        size: int = 20,
    ) -> dict:
        """分页获取题目列表"""
        params = {"pageNum": page, "pageSize": size}
        if subject_name:
            subject_id = await self.get_subject_id(token, subject_name)
            if subject_id is None:
                return {"code": 200, "message": "success", "data": {"records": [], "total": 0}}
            params["subjectId"] = subject_id
        if question_type is not None:
            params["type"] = question_type
        if difficulty is not None:
            params["difficulty"] = difficulty
        r = await self._get_client().get(
            f"{self.base}/question/listPage",
            params=params,
            headers=self._headers(token),
        )
        return self._checked_body(r, "获取题目列表")

    async def create_question(self, token: str, question: dict) -> int:
        """创建题目并返回 Spring Boot 持久化后生成的真实主键。"""
        r = await self._get_client().post(
            f"{self.base}/question",
            json=question,
            headers=self._headers(token),
        )
        body = self._checked_body(r, "创建题目")
        data = body.get("data")
        if isinstance(data, dict):
            data = data.get("id")
        if isinstance(data, str) and data.isdigit():
            data = int(data)
        if not isinstance(data, int) or isinstance(data, bool) or data <= 0:
            raise ExamBridgeError("创建题目成功响应中缺少真实题目 ID")
        return data

    # ── 错题相关 ──────────────────────────────────

    async def get_wrong_questions(
        self, token: str, page: int = 1, size: int = 20
    ) -> dict:
        """获取学生错题集"""
        r = await self._get_client().get(
            f"{self.base}/student/wrongQuestions/listPage",
            params={"pageNum": page, "pageSize": size},
            headers=self._headers(token),
        )
        return self._checked_body(r, "获取错题列表")

    async def get_wrong_question(self, token: str, wrong_id: int) -> dict | None:
        """
        按错题ID获取错题详情（含正确答案）。
        后端没有单条详情接口，从学生本人错题列表中匹配——用学生自己的 token 调用，
        保证只能取到属于该学生的记录。
        """
        body = await self.get_wrong_questions(token, page=1, size=100)
        data = body.get("data", {})
        records = data.get("records", []) if isinstance(data, dict) else []
        for rec in records:
            if rec.get("id") == wrong_id:
                return rec
        logger.info("错题 %s 不在学生最近 100 条错题中", wrong_id)
        return None

    # ── 考试状态 ──────────────────────────────────

    async def get_active_exam(self, token: str) -> dict | None:
        """
        检查学生是否在进行中的考试。
        通过查询考试记录 status=0 (考试中) 来判断。
        返回 None 表示没有进行中的考试。
        """
        r = await self._get_client().get(
            f"{self.base}/student/examRecords/listPage",
            params={"pageNum": 1, "pageSize": 1, "status": 0},
            headers=self._headers(token),
        )
        body = self._checked_body(r, "获取进行中考试")
        data = body.get("data", {})
        records = data.get("records", []) if isinstance(data, dict) else []
        return records[0] if records else None

    # ── 科目 ──────────────────────────────────

    async def get_subjects(self, token: str) -> list[dict]:
        """获取全部科目列表"""
        r = await self._get_client().get(
            f"{self.base}/subject/list",
            headers=self._headers(token),
        )
        body = self._checked_body(r, "获取科目列表")
        subjects = body.get("data", []) if isinstance(body, dict) else body if isinstance(body, list) else []
        return subjects

    async def get_subject_id(self, token: str, subject_name: str) -> int | None:
        """按科目名称查找 subjectId，找不到返回 None"""
        subjects = await self.get_subjects(token)
        for s in subjects:
            if s.get("name") == subject_name:
                return s.get("id")
        logger.warning("未找到科目 '%s'", subject_name)
        return None


# 模块级单例
exam_bridge = ExamBridge()
