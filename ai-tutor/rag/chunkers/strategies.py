"""按文档结构类型执行的分块策略。"""
from __future__ import annotations

import re

from rag.models import DocumentChunk, ParsedBlock


QUESTION_NUMBER_RE = re.compile(
    r"(?:^|\n)\s*"
    r"(?:"
    r"\d+[\.\、\．\)]\s*"
    r"|\(\d+\)\s*"
    r"|[①②③④⑤⑥⑦⑧⑨⑩]"
    r")"
    r"(?=[^\d])",
    re.MULTILINE,
)

_CJK_RE = re.compile(r"[\u4e00-\u9fff]")
_ASCII_WORD_RE = re.compile(r"[A-Za-z0-9]+")
_SENTENCE_SPLIT_RE = re.compile(r"(?<=[。！？；.!?])\s*")


def split_by_question_number(text: str) -> list[str]:
    """按题号将文本拆分为多个题目块。"""
    positions = [m.start() for m in QUESTION_NUMBER_RE.finditer(text)]
    if not positions:
        return [text.strip()] if text.strip() else []

    chunks = []
    for i, start in enumerate(positions):
        end = positions[i + 1] if i + 1 < len(positions) else len(text)
        chunk = text[start:end].strip()
        if chunk:
            chunks.append(chunk)

    merged = []
    for ch in chunks:
        has_cjk = bool(_CJK_RE.search(ch))
        if not has_cjk and len(ch) < 30 and merged:
            merged[-1] = merged[-1] + "\n" + ch
        else:
            merged.append(ch)
    return merged


def estimate_tokens(text: str) -> int:
    """轻量 token 估算：CJK 字符按 1 token，ASCII 单词按 1 token。"""
    cjk_tokens = len(_CJK_RE.findall(text))
    word_tokens = len(_ASCII_WORD_RE.findall(text))
    return max(1, cjk_tokens + word_tokens)


def _split_paragraphs(text: str) -> list[str]:
    return [part.strip() for part in re.split(r"\n\s*\n", text) if part.strip()]


def _split_sentences(text: str) -> list[str]:
    parts = [part.strip() for part in _SENTENCE_SPLIT_RE.split(text) if part.strip()]
    return parts or [text.strip()]


def _split_units(text: str) -> list[str]:
    paragraphs = _split_paragraphs(text)
    if len(paragraphs) > 1:
        return paragraphs
    sentences = _split_sentences(text)
    return sentences if len(sentences) > 1 else paragraphs or [text.strip()]


def _over_hard(text: str, max_tokens: int, max_chars: int) -> bool:
    return estimate_tokens(text) > max_tokens or len(text) > max_chars


def _tail_overlap(text: str, overlap_ratio: float) -> str:
    units = _split_units(text)
    if not units:
        return ""
    target = max(1, int(estimate_tokens(text) * overlap_ratio))
    selected: list[str] = []
    used = 0
    for unit in reversed(units):
        selected.append(unit)
        used += estimate_tokens(unit)
        if used >= target:
            break
    return "\n\n".join(reversed(selected))


def _split_long_paragraph(paragraph: str, max_tokens: int, max_chars: int, overlap_ratio: float) -> list[str]:
    sentences = _split_sentences(paragraph)
    if len(sentences) <= 1:
        chunks: list[str] = []
        text = paragraph
        overlap_chars = max(1, int(max_chars * overlap_ratio))
        step = max(1, max_chars - overlap_chars)
        while text:
            chunks.append(text[:max_chars])
            if len(text) <= max_chars:
                break
            text = text[step:]
        return chunks

    chunks: list[str] = []
    buffer: list[str] = []
    buffer_tokens = 0

    def flush():
        nonlocal buffer, buffer_tokens
        if buffer:
            chunks.append("\n".join(buffer))
        buffer = []
        buffer_tokens = 0

    for sentence in sentences:
        tokens = estimate_tokens(sentence)
        if buffer and buffer_tokens + tokens > max_tokens:
            prev = "\n".join(buffer)
            flush()
            overlap = _tail_overlap(prev, overlap_ratio)
            if overlap:
                buffer = [overlap]
                buffer_tokens = estimate_tokens(overlap)
        buffer.append(sentence)
        buffer_tokens += tokens
    flush()
    return chunks


def split_text_into_chunks(
    text: str,
    max_tokens: int,
    max_chars: int,
    overlap_ratio: float = 0.15,
    soft_tokens: int | None = None,
    prefix: str = "",
) -> list[str]:
    """按段落/句子边界切分，超过硬上限前尽量保持语义完整。"""
    text = text.strip()
    if not text:
        return []

    budget_tokens = max(1, max_tokens - estimate_tokens(prefix))
    budget_chars = max(1, max_chars - len(prefix))
    units = _split_units(text)
    chunks: list[str] = []
    buffer: list[str] = []
    buffer_tokens = 0

    def flush():
        nonlocal buffer, buffer_tokens
        if buffer:
            chunks.append("\n\n".join(buffer))
        buffer = []
        buffer_tokens = 0

    for unit in units:
        unit_tokens = estimate_tokens(unit)
        if _over_hard(unit, budget_tokens, budget_chars):
            flush()
            chunks.extend(_split_long_paragraph(unit, budget_tokens, budget_chars, overlap_ratio))
            continue

        should_cut = False
        if buffer:
            combined = "\n\n".join(buffer + [unit])
            if _over_hard(combined, budget_tokens, budget_chars):
                should_cut = True
            elif soft_tokens and buffer_tokens + unit_tokens > soft_tokens:
                should_cut = True

        if should_cut:
            prev = "\n\n".join(buffer)
            flush()
            overlap = _tail_overlap(prev, overlap_ratio) if overlap_ratio > 0 else ""
            if overlap:
                buffer = [overlap]
                buffer_tokens = estimate_tokens(overlap)

        buffer.append(unit)
        buffer_tokens += unit_tokens

    flush()
    return chunks


def _build_sections(blocks: list[ParsedBlock]) -> list[dict]:
    sections: list[dict] = []
    path: list[tuple[int, str]] = []
    current: dict | None = None

    for block in blocks:
        if block.kind == "heading":
            while path and path[-1][0] >= block.level:
                path.pop()
            path.append((block.level, block.text))
            current = {
                "path": [title for _, title in path],
                "title": block.text,
                "blocks": [],
                "pages": set(),
            }
            sections.append(current)
        else:
            if current is None:
                current = {"path": [], "title": "", "blocks": [], "pages": set()}
                sections.append(current)
            current["blocks"].append(block)
            if block.page_range:
                current["pages"].add(block.page_range)

    for section in sections:
        section["text"] = "\n\n".join(b.text for b in section["blocks"]).strip()
        section["path_text"] = " / ".join(section["path"])
    return [s for s in sections if s["text"]]


def _same_parent(left_path: list[str], right_path: list[str]) -> bool:
    return left_path[:-1] == right_path[:-1]


def _merge_short_sections(sections: list[dict], min_tokens: int) -> list[dict]:
    merged: list[dict] = []
    for section in sections:
        item = {
            "path": section["path"],
            "path_text": section["path_text"],
            "title": section["title"],
            "text": section["text"],
            "pages": set(section["pages"]),
            "first_title": section["title"],
            "first_path": section["path"],
            "merged": False,
        }
        if not merged:
            merged.append(item)
            continue

        prev = merged[-1]
        if _same_parent(prev["path"], item["path"]) and (
            estimate_tokens(prev["text"]) < min_tokens or estimate_tokens(item["text"]) < min_tokens
        ):
            prev["text"] = (prev["text"] + "\n\n" + item["text"]).strip()
            prev["pages"].update(item["pages"])
            prev["title"] = prev["first_title"] + "等"
            prev["merged"] = True
            item["merged"] = True
        else:
            merged.append(item)
    return merged


def _page_range(pages: set[str]) -> str:
    numeric = sorted((int(p) for p in pages if p.isdigit()))
    if not numeric:
        return ""
    if len(numeric) == 1:
        return str(numeric[0])
    return f"{numeric[0]}-{numeric[-1]}"


class QuestionBankChunker:
    @staticmethod
    def chunk(text: str, base_meta: dict, max_tokens: int, max_chars: int) -> list[DocumentChunk]:
        chunks: list[DocumentChunk] = []
        for q_index, raw in enumerate(split_by_question_number(text), start=1):
            if len(raw.strip()) < 10:
                continue
            parts = split_text_into_chunks(raw, max_tokens, max_chars, overlap_ratio=0.0)
            for part_index, part in enumerate(parts):
                meta = dict(base_meta)
                meta.update({
                    "chunk_type": "question" if part_index == 0 else "question_part",
                    "chunk_index": len(chunks),
                    "question_index": q_index,
                    "section_path": "",
                    "section_title": "",
                })
                chunks.append(DocumentChunk(content=part.strip(), metadata=meta))
        return chunks


class ChapterChunker:
    @staticmethod
    def chunk(
        blocks: list[ParsedBlock],
        base_meta: dict,
        max_tokens: int,
        max_chars: int,
        overlap_ratio: float,
        min_tokens: int,
    ) -> list[DocumentChunk]:
        sections = _build_sections(blocks)
        sections = _merge_short_sections(sections, min_tokens)
        chunks: list[DocumentChunk] = []

        for section in sections:
            prefix = f"[章节：{section['path_text']}]" if section["path_text"] else ""
            parts = split_text_into_chunks(
                section["text"],
                max_tokens=max_tokens,
                max_chars=max_chars,
                overlap_ratio=overlap_ratio,
                prefix=prefix,
            )
            for part in parts:
                content = f"{prefix}\n{part}" if prefix else part
                meta = dict(base_meta)
                meta.update({
                    "chunk_type": "section",
                    "chunk_index": len(chunks),
                    "question_index": 0,
                    "section_path": section["path_text"],
                    "section_title": section["title"] or "未命名章节",
                    "page_range": _page_range(section["pages"]),
                })
                chunks.append(DocumentChunk(content=content.strip(), metadata=meta))
        return chunks


class NotesChunker:
    @staticmethod
    def chunk(
        blocks: list[ParsedBlock],
        base_meta: dict,
        max_tokens: int,
        max_chars: int,
        overlap_ratio: float,
        soft_tokens: int,
        min_tokens: int,
    ) -> list[DocumentChunk]:
        sections = _build_sections(blocks)
        chunks: list[DocumentChunk] = []

        for section in sections:
            prefix = f"[标题：{section['title']}]" if section["title"] else ""
            parts = split_text_into_chunks(
                section["text"],
                max_tokens=max_tokens,
                max_chars=max_chars,
                overlap_ratio=overlap_ratio,
                soft_tokens=soft_tokens,
                prefix=prefix,
            )
            for part in parts:
                content = f"{prefix}\n{part}" if prefix else part
                meta = dict(base_meta)
                meta.update({
                    "chunk_type": "paragraph",
                    "chunk_index": len(chunks),
                    "question_index": 0,
                    "section_path": section["path_text"],
                    "section_title": section["title"] or "",
                    "page_range": _page_range(section["pages"]),
                })
                chunks.append(DocumentChunk(content=content.strip(), metadata=meta))

        return NotesChunker._merge_short_chunks(chunks, min_tokens)

    @staticmethod
    def _merge_short_chunks(chunks: list[DocumentChunk], min_tokens: int) -> list[DocumentChunk]:
        merged: list[DocumentChunk] = []
        for chunk in chunks:
            if merged:
                prev = merged[-1]
                same_section = (
                    prev.metadata.get("section_path") == chunk.metadata.get("section_path")
                )
                if same_section and estimate_tokens(chunk.content) < min_tokens:
                    prev.content = (prev.content + "\n\n" + chunk.content).strip()
                    prev.metadata["chunk_type"] = "merged"
                    continue
                if same_section and estimate_tokens(prev.content) < min_tokens:
                    prev.content = (prev.content + "\n\n" + chunk.content).strip()
                    prev.metadata["chunk_type"] = "merged"
                    continue
            merged.append(chunk)
        return merged
