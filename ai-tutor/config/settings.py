"""应用配置 — 基于 pydantic-settings，从 .env / 环境变量加载"""
from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    # ── 服务 ──
    app_name: str = "ai-tutor"
    app_port: int = 8080
    debug: bool = True

    # ── JWT — 与 Spring Boot 共享同一 secret ──
    jwt_secret: str = "t8Kx9mN2vB5qW3pL7sF4cH6jU1yR8eZ0aD5gJ9nM2xP4sV7wC3"
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

    # ── ChromaDB 向量存储 ──
    vector_db_path: str = "./chroma_store"
    session_db_path: str = "./chat_sessions.db"

    # ── RAG ──
    chunk_size: int = 800
    chunk_overlap: int = 100
    retrieval_top_k: int = 5
    embedding_max_chars: int = 500  # BAAI/bge-large-zh-v1.5 上限 512 tokens，中文字符按 1 token/字截断

    # ── Agent ──
    llm_timeout: float = 60.0
    generate_batch_size: int = 5  # 单次 LLM 调用生成的最大题数
    generate_max_attempts: int = 4  # 数量不足时的最大补生成轮数
    session_history_limit: int = 12  # 注入 prompt 的对话历史条数
    session_max_messages: int = 50  # 每个会话在库中保留的最大消息数

    # ── 评估 ──
    eval_sample_count: int = 20  # Ragas 评估采样数
    eval_llm_temperature: float = 0.0  # 评估 LLM 温度（0=更确定）

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


@lru_cache()
def get_settings() -> Settings:
    return Settings()
