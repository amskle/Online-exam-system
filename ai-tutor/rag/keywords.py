"""关键词提取与本地评分。"""
from __future__ import annotations

import re

try:
    import jieba
except ImportError:
    jieba = None


_CJK_RUN_RE = re.compile(r"[\u4e00-\u9fff]+")
_ASCII_RE = re.compile(r"[A-Za-z0-9]+")
_STOPWORDS = {
    "的", "了", "和", "与", "是", "在", "中", "也", "都", "而", "及", "或",
    "一个", "我们", "你们", "他们", "这个", "那个", "怎么", "什么", "如何",
}


def extract_keyword_terms(query: str) -> list[str]:
    """提取用于 Chroma $contains 候选查询的词。"""
    raw_terms: list[str] = []
    if jieba is not None:
        raw_terms = [w.strip() for w in jieba.lcut(query)]
    else:
        raw_terms = _CJK_RUN_RE.findall(query) + _ASCII_RE.findall(query)

    terms: list[str] = []
    for term in raw_terms:
        term = term.strip()
        if not term or term.lower() in _STOPWORDS:
            continue
        if len(term) < 2:
            continue
        if term.lower() not in {t.lower() for t in terms}:
            terms.append(term)
    return terms[:8]


def keyword_score(query: str, document: str) -> float:
    """简单关键词命中率评分，大小写不敏感。"""
    terms = extract_keyword_terms(query)
    if not terms:
        return 0.0
    doc_lower = document.lower()
    hits = sum(1 for term in terms if term in doc_lower)
    return hits / len(terms)
