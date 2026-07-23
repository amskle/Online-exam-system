"""JWT 工具 — 与 Spring Boot com.example.onlineexamsystem.utils.JwtUtil 兼容"""
import jwt
from config.settings import get_settings

settings = get_settings()

# 角色枚举
ROLE_STUDENT = 1
ROLE_TEACHER = 2
ROLE_ADMIN = 3


def verify_token(token: str) -> dict | None:
    """验证 JWT token，返回 claims dict；失败返回 None"""
    try:
        claims = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algorithm],
        )
        return claims
    except jwt.ExpiredSignatureError:
        return None
    except jwt.InvalidTokenError:
        return None


def get_user_id(token: str) -> int | None:
    """从 token 提取 userId"""
    claims = verify_token(token)
    if claims is None:
        return None
    return int(claims.get("sub", 0))


def get_role(token: str) -> int | None:
    """从 token 提取 role (1=STUDENT, 2=TEACHER, 3=ADMIN)"""
    claims = verify_token(token)
    if claims is None:
        return None
    return claims.get("role")


def extract_token_from_header(authorization: str) -> str:
    """从 'Bearer <token>' 中提取 token"""
    if not authorization:
        return ""
    parts = authorization.split(" ", 1)
    if len(parts) == 2 and parts[0].lower() == "bearer":
        return parts[1]
    return authorization  # fallback：直接当作 token
