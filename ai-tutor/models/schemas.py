"""Pydantic 请求 / 响应模型"""
from pydantic import BaseModel, Field
from typing import Optional, Literal


# ═══════════════════════════════════════════════════
#  通用
# ═══════════════════════════════════════════════════

class ApiResponse(BaseModel):
    """与 Spring Boot Result<T> 对齐的响应格式"""
    code: int = 200
    message: str = "成功"
    data: Optional[object] = None


class AuthUser(BaseModel):
    """从 JWT 解析出的当前用户"""
    user_id: int
    role: int  # 1=STUDENT, 2=TEACHER, 3=ADMIN


# ═══════════════════════════════════════════════════
#  教师智能体
# ═══════════════════════════════════════════════════

class TeacherGenerateRequest(BaseModel):
    """教师生成题目请求"""
    subject_id: int = Field(..., ge=0, description="科目ID，0 为未知")
    subject_name: str = Field(..., description="科目名称")
    question_type: int = Field(..., description="题型：1单选 2多选 3判断 4主观")
    difficulty: int = Field(default=2, ge=1, le=3, description="难度：1简单 2中等 3困难")
    count: int = Field(default=5, ge=1, le=20, description="生成数量")
    extra_requirement: Optional[str] = Field(default=None, description="额外要求")


class TeacherRecommendRequest(BaseModel):
    """教师推荐任务请求"""
    subject_name: Optional[str] = Field(default=None, description="指定科目；不传则根据题库缺口推荐")


class GeneratedQuestion(BaseModel):
    """生成的单道题目"""
    content: str
    options: Optional[str] = Field(default=None, description="JSON 格式选项字符串，主观题为 []")
    answer: str
    analysis: str
    score: int = 5


class TeacherGenerateData(BaseModel):
    """生成题目响应数据"""
    questions: list[GeneratedQuestion]
    saved_ids: list[int] = Field(default_factory=list)
    failed_questions: list[dict] = Field(default_factory=list, description="入库失败的题目")
    warnings: list[str] = Field(default_factory=list, description="生成过程中的警告")


class TeacherRecommendData(BaseModel):
    """推荐任务响应数据"""
    message: str
    suggestion: dict


class TeacherChatRequest(BaseModel):
    """教师知识库对话请求"""
    message: str = Field(..., description="用户提问内容")
    subject_name: Optional[str] = Field(default=None, description="限定检索的科目范围")
    session_id: Optional[str] = Field(default=None, description="对话会话ID，不传则新建")


class TeacherChatData(BaseModel):
    """教师知识库对话响应"""
    reply: str = Field(..., description="基于知识库的回复")
    session_id: str = Field(..., description="本次对话会话ID")
    sources: list[dict] = Field(default_factory=list, description="引用的知识库条目")


# ═══════════════════════════════════════════════════
#  学生智能体
# ═══════════════════════════════════════════════════

class StudentAskRequest(BaseModel):
    """学生答疑请求 — 答案由服务端从错题库加载，前端不提交也不会泄题"""
    question_id: Optional[int] = Field(default=None, description="错题ID（提供后可加载题目上下文）")
    question_content: str = Field(..., description="题目内容或学生当前输入")
    student_answer: Optional[str] = Field(default=None, description="学生自己的答案")
    message: str = Field(..., description="学生的疑问")
    session_id: Optional[str] = Field(default=None, description="对话会话ID，不传则新建")


class StudentAskData(BaseModel):
    """学生答疑响应（Socratic 引导式，绝不直接给出答案）"""
    reply: str = Field(..., description="引导式回复")
    hints: list[str] = Field(default_factory=list, description="思考提示")
    related_concepts: list[str] = Field(default_factory=list, description="相关知识点")
    session_id: str = Field(..., description="本次对话会话ID")


class StudentRecommendData(BaseModel):
    """学生推荐响应"""
    message: str
    weak_points: list[dict] = Field(default_factory=list)


class StudentStatusData(BaseModel):
    """学生智能体可用状态"""
    available: bool
    reason: Optional[str] = None


# ═══════════════════════════════════════════════════
#  会话管理
# ═══════════════════════════════════════════════════

class SessionClearRequest(BaseModel):
    """清空会话请求"""
    session_id: str = Field(..., description="要清空的会话ID")


# ═══════════════════════════════════════════════════
#  文档上传
# ═══════════════════════════════════════════════════

class DocumentUploadData(BaseModel):
    """文档上传 & 入库结果"""
    file_name: str
    subject_name: str
    chunk_count: int
    message: str


# ═══════════════════════════════════════════════════
#  会话历史
# ═══════════════════════════════════════════════════

class SessionListItem(BaseModel):
    """会话列表条目"""
    session_id: str
    agent_mode: str
    title: str
    created_at: float
    updated_at: float
    preview: str = ""


class SessionDetail(BaseModel):
    """单个会话完整数据"""
    session_id: str
    agent_mode: str
    title: str
    created_at: float
    updated_at: float
    messages: list[dict] = Field(default_factory=list)


# ═══════════════════════════════════════════════════
#  对话消息（SSE/流式，预留）
# ═══════════════════════════════════════════════════

class ChatMessage(BaseModel):
    role: Literal["user", "assistant", "system"]
    content: str


class ChatRequest(BaseModel):
    agent: Literal["teacher", "student"]
    messages: list[ChatMessage]
    context: Optional[dict] = Field(default_factory=dict, description="额外上下文")
