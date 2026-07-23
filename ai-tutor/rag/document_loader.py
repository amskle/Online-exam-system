"""文档加载器 — 支持 PDF、TXT，按题号分块"""
import re
from pathlib import Path
from dataclasses import dataclass, field


@dataclass
class DocumentChunk:
    """文档分块"""
    content: str
    metadata: dict = field(default_factory=dict)

    def __repr__(self) -> str:
        preview = self.content[:60].replace("\n", " ")
        return f"Chunk(meta={self.metadata}, text='{preview}...')"


# ── 题号正则：匹配 1. 1、1．1) (1) ① 等多种中文编号 ──
_QUESTION_NUMBER_RE = re.compile(
    r"(?:^|\n)\s*"
    r"(?:"
    r"\d+[\.\、\．\)]\s*"          # 1. 1、1．1)
    r"|\(\d+\)\s*"                 # (1)
    r"|[①②③④⑤⑥⑦⑧⑨⑩]"              # ①
    r")"
    r"(?=[^\d])",                  # 后面不能紧跟数字（避免误匹配日期）
    re.MULTILINE,
)


def split_by_question_number(text: str) -> list[str]:
    """按题号将文本拆分为多个题目块"""
    positions = [m.start() for m in _QUESTION_NUMBER_RE.finditer(text)]
    if not positions:
        return [text.strip()] if text.strip() else []

    chunks = []
    for i, start in enumerate(positions):
        end = positions[i + 1] if i + 1 < len(positions) else len(text)
        chunk = text[start:end].strip()
        if chunk:
            chunks.append(chunk)
    return chunks


class DocumentLoader:
    """文档加载器入口"""

    @staticmethod
    def load_pdf(file_path: str) -> str:
        """加载 PDF，提取纯文本"""
        import pdfplumber
        full_text = []
        with pdfplumber.open(file_path) as pdf:
            for page in pdf.pages:
                text = page.extract_text()
                if text:
                    full_text.append(text)
        return "\n\n".join(full_text)

    @staticmethod
    def load_txt(file_path: str) -> str:
        """加载 TXT 文件（UTF-8 / GBK 自动检测）"""
        path = Path(file_path)
        raw = path.read_bytes()
        # 尝试 UTF-8
        try:
            return raw.decode("utf-8")
        except UnicodeDecodeError:
            pass
        # 尝试 GBK
        try:
            return raw.decode("gbk")
        except UnicodeDecodeError:
            pass
        # 最后用 errors='replace'
        return raw.decode("utf-8", errors="replace")

    @classmethod
    def load(cls, file_path: str) -> str:
        """根据扩展名自动选择加载器"""
        ext = Path(file_path).suffix.lower()
        if ext == ".pdf":
            return cls.load_pdf(file_path)
        elif ext == ".txt":
            return cls.load_txt(file_path)
        else:
            raise ValueError(f"不支持的文件格式: {ext}")

    @classmethod
    def load_and_chunk(
        cls,
        file_path: str,
        subject_name: str = "",
    ) -> list[DocumentChunk]:
        """加载文件并按题号分块，每个块附带元数据"""
        text = cls.load(file_path)
        raw_chunks = split_by_question_number(text)

        chunks = []
        for i, content in enumerate(raw_chunks):
            if len(content.strip()) < 10:  # 跳过过短的块
                continue
            chunks.append(DocumentChunk(
                content=content.strip(),
                metadata={
                    "source_file": Path(file_path).name,
                    "subject": subject_name,
                    "question_index": i + 1,
                },
            ))
        return chunks
