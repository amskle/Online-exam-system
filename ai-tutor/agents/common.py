"""Agent 共享工具 — 统一 LLM 客户端、容错 JSON 解析、常量映射"""
import ast
import json
import logging
import re

from openai import AsyncOpenAI

from config.settings import get_settings

settings = get_settings()
logger = logging.getLogger("ai-tutor.agents")

TYPE_NAMES = {1: "单选题", 2: "多选题", 3: "判断题", 4: "主观题"}
DIFF_NAMES = {1: "简单", 2: "中等", 3: "困难"}

_llm: AsyncOpenAI | None = None


def get_llm() -> AsyncOpenAI:
    """共享的异步 LLM 客户端（懒加载单例）"""
    global _llm
    if _llm is None:
        _llm = AsyncOpenAI(
            api_key=settings.llm_api_key,
            base_url=settings.llm_api_base,
            timeout=settings.llm_timeout,
            max_retries=2,
        )
    return _llm


async def chat_text(prompt: str, temperature: float = 0.7, max_tokens: int = 2048) -> str:
    """单次文本对话，返回 stripped 文本"""
    resp = await get_llm().chat.completions.create(
        model=settings.llm_model,
        messages=[{"role": "user", "content": prompt}],
        temperature=temperature,
        max_tokens=max_tokens,
    )
    return (resp.choices[0].message.content or "").strip()


_JSON_BLOCK = re.compile(r"```(?:json)?\s*(.*?)```", re.DOTALL)
# 键值对的 key 被单引号包裹: 'key': or 'key':
_SINGLE_QUOTED_KEY = re.compile(r"'(\w+)'\s*:")
# Python 字面量 null/none → JSON null
_PY_NONE = re.compile(r'\bNone\b')


def _fix_single_quoted_json(raw: str) -> str:
    """把 Python 字典风格的单引号键转为 JSON 双引号键，并处理 None → null"""
    text = raw.strip()
    # 'key': → "key":
    text = _SINGLE_QUOTED_KEY.sub(r'"\1":', text)
    # 'key' : → "key" :
    text = re.sub(r"'(\w+)'\s*:", r'"\1":', text)
    # : 'value' → : "value"
    text = re.sub(r":\s*'([^']*)'", r': "\1"', text)
    # None → null
    text = _PY_NONE.sub('null', text)
    return text


def extract_json(raw: str):
    """容错提取 JSON：markdown 代码块 → json.loads → 单引号修复 → ast.literal_eval → 括号切片"""
    text = raw.strip()
    # 1. 剥离 markdown 代码块
    m = _JSON_BLOCK.search(text)
    if m:
        text = m.group(1).strip()

    # 2. 精确定位最外层括号区间
    starts = [i for i in (text.find("["), text.find("{")) if i != -1]
    if not starts:
        raise ValueError(f"输出中未找到 JSON 结构: {raw[:200]}")
    start = min(starts)
    end = max(text.rfind("]"), text.rfind("}"))
    if end <= start:
        raise ValueError(f"JSON 结构不完整: {raw[:200]}")
    body = text[start:end + 1]

    # 3. 尝试标准 json.loads
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        pass

    # 4. 单引号修复后再试
    try:
        fixed = _fix_single_quoted_json(body)
        return json.loads(fixed)
    except (json.JSONDecodeError, ValueError):
        pass

    # 5. ast.literal_eval（支持单引号、Python 字面量、末尾逗号等）
    try:
        result = ast.literal_eval(body)
        # ast.literal_eval 可能返回 tuple/list/dict，再走一轮 json 规范化
        return json.loads(json.dumps(result, ensure_ascii=False))
    except (ValueError, SyntaxError):
        pass

    # 6. 最后尝试：重新切割（LLM 可能在 JSON 前后加了说明文字），用上一个定位的 body
    raise ValueError(f"无法解析 JSON: {body[:200]}")


async def chat_json(prompt: str, temperature: float = 0.7, max_tokens: int = 4096):
    """单次 JSON 对话：显式要求双引号 JSON，解析失败时加重提示重试一次"""
    full_prompt = (
        prompt
        + "\n\n⚠️ 务必输出标准 JSON（键和字符串值都用双引号），不要用单引号。只输出 JSON，不要其他文字。"
    )
    last_error: Exception | None = None
    for attempt in range(2):
        raw = await chat_text(full_prompt, temperature=temperature, max_tokens=max_tokens)
        try:
            return extract_json(raw)
        except (ValueError, json.JSONDecodeError) as e:
            last_error = e
            logger.warning("LLM JSON 解析失败（第 %d 次）: raw[:300]=%s", attempt + 1, raw[:300])
            # 第二次加重语气
            full_prompt = prompt + "\n\n‼️ 你上一次输出不是合法 JSON。请这次只输出合法 JSON。键必须用双引号包裹，字符串值也必须用双引号。只输出 JSON，不要任何其他内容。"
    raise ValueError(f"LLM 输出多次无法解析为 JSON: {last_error}")


def has_fatal(state: dict) -> str:
    """LangGraph 条件边：存在 fatal_error 时跳到 END"""
    return "end" if state.get("fatal_error") else "continue"
