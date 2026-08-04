"""文档加载器 — 格式识别 → 解析 → 分类 → 分块 Pipeline。"""
from __future__ import annotations

import time
from pathlib import Path

from config.settings import get_settings
from rag.chunkers.classifier import classify
from rag.chunkers.strategies import (
    ChapterChunker,
    NotesChunker,
    QuestionBankChunker,
    split_by_question_number,
)
from rag.loaders.format_detector import detect_format
from rag.loaders.parsers import parse_document, parse_pdf, parse_txt
from rag.models import DocumentChunk, DocumentLoadResult


settings = get_settings()


class DocumentLoader:
    """文档加载器入口。"""

    @staticmethod
    def load_pdf(file_path: str) -> str:
        parsed = parse_pdf(file_path)
        return "\n\n".join(b.text for b in parsed.blocks)

    @staticmethod
    def load_txt(file_path: str) -> str:
        parsed = parse_txt(file_path)
        return "\n\n".join(b.text for b in parsed.blocks)

    @classmethod
    def load(cls, file_path: str) -> str:
        fmt = detect_format(file_path)
        parsed = parse_document(file_path, fmt)
        return "\n\n".join(b.text for b in parsed.blocks)

    @classmethod
    def load_and_chunk_detail(
        cls,
        file_path: str,
        subject_name: str = "",
        modified_at: float | None = None,
        uploaded_at: float | None = None,
        source_name: str | None = None,
    ) -> DocumentLoadResult:
        """完整执行加载、分类与分块，返回结果对象。"""
        fmt = detect_format(file_path)
        parsed = parse_document(file_path, fmt)
        text = "\n\n".join(b.text for b in parsed.blocks)
        structure_type = classify(parsed.blocks, text)
        chunking_strategy = "scattered_notes" if structure_type == "unknown" else structure_type

        uploaded_at = uploaded_at or time.time()
        modified_at = modified_at or uploaded_at
        raw_created = parsed.metadata.get("created_at")
        created_at = modified_at if not isinstance(raw_created, (int, float)) else raw_created

        source = source_name or Path(file_path).name
        base_meta = {
            "source_file": source,
            "subject": subject_name,
            "format": fmt,
            "structure_type": structure_type,
            "chunk_type": "",
            "chunk_index": 0,
            "question_index": 0,
            "section_path": "",
            "section_title": "",
            "page_range": "",
            "created_at": created_at,
            "modified_at": modified_at,
            "uploaded_at": uploaded_at,
        }

        if structure_type == "question_bank":
            chunks = QuestionBankChunker.chunk(
                text,
                base_meta=base_meta,
                max_tokens=settings.notes_hard_token_limit,
                max_chars=settings.embedding_max_chars,
            )
        elif structure_type == "chapter":
            chunks = ChapterChunker.chunk(
                parsed.blocks,
                base_meta=base_meta,
                max_tokens=settings.notes_hard_token_limit,
                max_chars=settings.embedding_max_chars,
                overlap_ratio=settings.chunk_overlap_ratio,
                min_tokens=settings.min_chunk_tokens,
            )
        else:
            chunks = NotesChunker.chunk(
                parsed.blocks,
                base_meta=base_meta,
                max_tokens=settings.notes_hard_token_limit,
                max_chars=settings.embedding_max_chars,
                overlap_ratio=settings.chunk_overlap_ratio,
                soft_tokens=settings.notes_soft_token_limit,
                min_tokens=settings.min_chunk_tokens,
            )

        warnings: list[str] = []
        if structure_type == "unknown":
            warnings.append("未识别出稳定结构，已按零散笔记策略分块")

        return DocumentLoadResult(
            chunks=chunks,
            format=fmt,
            structure_type=structure_type,
            chunking_strategy=chunking_strategy,
            warnings=warnings,
        )

    @classmethod
    def load_and_chunk(
        cls,
        file_path: str,
        subject_name: str = "",
        modified_at: float | None = None,
        uploaded_at: float | None = None,
        source_name: str | None = None,
    ) -> list[DocumentChunk]:
        """兼容旧调用方式，返回分块列表。"""
        return cls.load_and_chunk_detail(
            file_path,
            subject_name=subject_name,
            modified_at=modified_at,
            uploaded_at=uploaded_at,
            source_name=source_name,
        ).chunks
