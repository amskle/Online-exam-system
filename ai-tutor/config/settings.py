"""应用配置 — 基于 pydantic-settings，从 .env / 环境变量加载"""
from pydantic import Field
from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    # ── 服务 ──
    app_name: str = "ai-tutor"
    app_port: int = 8080
    debug: bool = False
    cors_allowed_origins: str = "http://localhost:8076,http://localhost:8088"

    # ── JWT — 与 Spring Boot 共享同一 secret ──
    jwt_secret: str = Field(min_length=32)
    jwt_algorithm: str = "HS256"

    # ── Spring Boot 考试系统地址 ──
    exam_backend_url: str = "http://localhost:8077"

    # ── LLM API (国内模型, OpenAI-compatible) ──
    llm_api_base: str = "https://api.deepseek.com/v1"
    llm_api_key: str = "your-api-key-here"
    llm_model: str = "deepseek-chat"
    embedding_api_base: str = "https://api.siliconflow.cn/v1"
    embedding_api_key: str = "your-embedding-api-key-here"
    embedding_model: str = "BAAI/bge-large-zh-v1.5"
    embedding_use_llm_credentials: bool = False

    # ── ChromaDB 向量存储 ──
    vector_db_path: str = "./chroma_store"
    session_db_path: str = "./chat_sessions.db"

    # ── RAG ──
    chunk_size: int = 800
    chunk_overlap: int = 100
    retrieval_top_k: int = 5
    embedding_max_chars: int = 8000  # BAAI/bge-large-zh-v1.5 上限 512 tokens，中文字符按 1 token/字截断 改用bge-m3上限8192
    embedding_batch_size: int = Field(default=32, ge=1, le=256)
    use_unstructured: bool = False
    document_classifier: str = "rule"
    notes_soft_token_limit: int = 256
    notes_hard_token_limit: int = 400
    chunk_overlap_ratio: float = 0.15
    min_chunk_tokens: int = 50
    hybrid_top_k: int = 20
    keyword_max_docs: int = 50000
    keyword_query_timeout_ms: int = Field(default=250, ge=10, le=5000)
    keyword_fallback_max_docs: int = Field(default=5000, ge=1)
    keyword_fallback_timeout_ms: int = Field(default=100, ge=10, le=5000)
    max_upload_mb: int = 50
    retrieval_embedding_timeout_seconds: float = Field(default=5.0, gt=0.0)
    retrieval_semantic_timeout_ms: int = Field(default=1000, ge=10, le=10000)
    retrieval_min_similarity: float = Field(default=0.60, ge=0.0, le=1.0)
    retrieval_early_stop_similarity: float = Field(default=0.82, ge=0.0, le=1.0)
    retrieval_early_stop_min_results: int = Field(default=3, ge=1)
    retrieval_min_keyword_score: float = Field(default=0.25, ge=0.0, le=1.0)
    retrieval_cache_ttl_seconds: float = Field(default=60.0, ge=0.0)
    retrieval_cache_size: int = Field(default=256, ge=1)
    query_rewrite_enabled: bool = True
    query_rewrite_max_variants: int = 5
    query_rewrite_history_limit: int = 6
    query_rewrite_concurrency: int = 3
    query_rewrite_timeout_seconds: float = Field(default=0.5, gt=0.0)
    query_rewrite_cache_size: int = Field(default=256, ge=1)
    query_complexity_length_threshold: int = Field(default=80, ge=10)

    # ── Agent ──
    llm_timeout: float = 100.0
    generate_batch_size: int = 5  # 单次 LLM 调用生成的最大题数
    generate_max_attempts: int = 4  # 数量不足时的最大补生成轮数
    quality_check_max_attempts: int = 2
    session_history_limit: int = 12  # 注入 prompt 的对话历史条数
    session_max_messages: int = 50  # 每个会话在库中保留的最大消息数

    # ── Langfuse 可观测性（兼容自托管 v4）──
    langfuse_enabled: bool = True
    langfuse_public_key: str = ""
    langfuse_secret_key: str = ""
    langfuse_base_url: str = "http://localhost:3000"
    langfuse_environment: str = "development"
    langfuse_release: str = "ai-tutor-0.2.0"
    langfuse_sample_rate: float = Field(default=1.0, ge=0.0, le=1.0)
    langfuse_trace_tests: bool = False

    # 评估运行时凭据仅从本地环境读取，不写入 Langfuse Dataset。
    eval_student_token: str = ""
    eval_teacher_token: str = ""

    # ── 评估 ──
    eval_sample_count: int = 20  # Ragas 评估采样数
    eval_llm_temperature: float = 0.0  # 评估 LLM 温度（0=更确定）

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


@lru_cache()
def get_settings() -> Settings:
    return Settings()
