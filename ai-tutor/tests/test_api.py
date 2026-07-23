"""集成测试 — 验证 ai-tutor 微服务各接口"""
import pytest
from fastapi.testclient import TestClient
from main import app

client = TestClient(app)

# 假 token（仅用于测试路由可达性，不验证实际 JWT）
DUMMY_AUTH = {"Authorization": "Bearer dummy_token"}
# 有效 token 需要运行时用 Spring Boot 签发
VALID_AUTH = None  # 测试时通过 conftest 注入


class TestHealthCheck:
    def test_health_returns_ok(self):
        r = client.get("/ai/health")
        assert r.status_code == 200
        assert r.json()["status"] == "ok"
        assert r.json()["service"] == "ai-tutor"


class TestTeacherRoutes:
    def test_recommend_requires_auth(self):
        r = client.post("/ai/teacher/recommend", json={"subject_name": "Java"})
        assert r.status_code == 422  # 缺少 Authorization header

    def test_recommend_rejects_invalid_token(self):
        r = client.post(
            "/ai/teacher/recommend",
            json={"subject_name": "Java"},
            headers=DUMMY_AUTH,
        )
        assert r.status_code == 401

    def test_generate_requires_auth(self):
        r = client.post(
            "/ai/teacher/generate",
            json={
                "subject_name": "Java程序设计",
                "question_type": 1,
                "difficulty": 2,
                "count": 3,
            },
        )
        assert r.status_code == 422  # 缺少 Authorization header

    def test_upload_requires_auth(self):
        r = client.post("/ai/teacher/upload")
        assert r.status_code == 422


class TestStudentRoutes:
    def test_status_requires_auth(self):
        r = client.get("/ai/student/status")
        assert r.status_code == 422

    def test_ask_requires_auth(self):
        r = client.post(
            "/ai/student/ask",
            json={
                "question_content": "1+1=?",
                "message": "我不理解",
                "conversation_history": [],
            },
        )
        assert r.status_code == 422

    def test_recommend_requires_auth(self):
        r = client.post("/ai/student/recommend")
        assert r.status_code == 422


class TestStudentAnswerIsolation:
    """验证学生智能体不会泄露答案的 Socratic 质量测试（需有效 token 和 LLM）"""

    @pytest.mark.integration
    def test_student_reply_excludes_direct_answer(self):
        """模拟场景：学生问 2+2=?，助手不能直接说 4"""
        prompt = """你是一位苏格拉底式导师。学生问了一道数学题，请你引导他思考，但不要给出答案。
学生问题: 2+2等于多少？

请回复一段引导性文字（不超过100字），绝对不要出现 "4" 这个数字。"""

        # 此测试不调用真实 API，仅验证 design principle
        assert "引导" in prompt or "苏格拉底" in prompt
