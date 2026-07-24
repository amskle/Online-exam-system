"""智能学习伙伴 (ai-tutor) — FastAPI 微服务入口"""
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from config.settings import get_settings

settings = get_settings()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s %(message)s",
)
logger = logging.getLogger("ai-tutor")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期：启动时初始化 SQLite，关闭时清理 httpx 连接"""
    logger.info("ai-tutor 启动 (port %d, debug=%s)", settings.app_port, settings.debug)
    yield
    from utils.exam_bridge import exam_bridge
    await exam_bridge.close()
    logger.info("ai-tutor 已关闭")


app = FastAPI(
    title="智能学习伙伴",
    description="基于 RAG + LangGraph 的教师出题 & 学生答疑智能体",
    version="0.2.0",
    lifespan=lifespan,
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/ai/health")
async def health_check():
    """健康检查"""
    return {"status": "ok", "service": settings.app_name}


from routers import teacher, student
app.include_router(teacher.router, prefix="/ai/teacher", tags=["教师智能体"])
app.include_router(student.router, prefix="/ai/student", tags=["学生智能体"])


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=settings.app_port, reload=settings.debug)
