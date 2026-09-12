"""基于规则的文档结构分类器。"""
from __future__ import annotations

import re

from rag.chunkers.strategies import QUESTION_NUMBER_RE
from rag.models import ParsedBlock


_HEADING_LINE_RE = re.compile(
    r"^(?:#{1,6}\s+"
    r"|第[一二三四五六七八九十百千万0-9]+[章节篇卷]"
    r"|[一二三四五六七八九十]+、"
    r"|(?:\d+\.)+\d*)"
)
_ANSWER_LABEL = re.compile(
    r"(?:^|\n)\s*(?:[【\[(]\s*)?(?:(?:正确|参考|标准|期望)?答案|answer(?: key)?)"
    r"(?:\s*[】\])])?\s*[:：]", re.I,
)


def _heading_lines(text: str) -> list[str]:
    return [line.strip() for line in text.splitlines() if _HEADING_LINE_RE.match(line.strip())]


def classify(blocks: list[ParsedBlock], text: str) -> str:
    """返回 question_bank / chapter / scattered_notes / unknown。"""
    text = text.strip()
    if not text:
        return "unknown"

    question_hits = len(QUESTION_NUMBER_RE.findall(text))
    # A prose mention of “答案” is not evidence of an answer key.
    has_answer = bool(_ANSWER_LABEL.search(text))
    if question_hits >= 2 and has_answer:
        return "question_bank"

    heading_blocks = [b for b in blocks if b.kind == "heading"]
    if len(heading_blocks) >= 2 or any(b.level > 1 for b in heading_blocks):
        return "chapter"
    if len(_heading_lines(text)) >= 2:
        return "chapter"

    return "scattered_notes"
