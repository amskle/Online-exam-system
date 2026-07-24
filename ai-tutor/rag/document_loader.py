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

# ── PDF 页眉页脚噪音 ──
_PAGE_NOISE_RES = [
    re.compile(r'第\s*\d+\s*[／/]\s*\d+\s*页'),       # 第 1／18页
    re.compile(r'^\d{1,3}\s*$', re.MULTILINE),         # 孤立页码数字
    re.compile(r'^\s*[-—}》\s]+\s*$', re.MULTILINE),   # 竖排页眉残余短线
]

# ── OCR/字体常见误识别：题号 1. 被渲染为 l. / I.  11. 被渲染为 1 1. / I 3. ──
_OCR_FIXES = [
    (re.compile(r'(?:^|\n)\s*[lI]\.\s*(?=[^\d])'), '\n1. '),    # l. / I. → 1.
    (re.compile(r'(?:^|\n)\s*[lI]\s+(\d)\s*\.'), r'\n1\1.'),    # I 3. → 13.  l 1. → 11.
    (re.compile(r'(?:^|\n)\s*(\d)\s+(\d)\s*\.'), r'\n\1\2.'),   # 1 1. → 11.
]

# 竖排文字被拆成逐字符行：连续 ≥5 行的单字符 → 整块移除
_VERTICAL_CHAR_BLOCK = re.compile(
    r'(?:^[^\n]{1,3}\n){5,}',
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

    # ── 合并假分割块：极短块（<30字且无中文）可能是选项被误识别为题号 ──
    merged = []
    for ch in chunks:
        has_cjk = bool(re.search(r'[一-鿿]', ch))
        if not has_cjk and len(ch) < 30 and merged:
            merged[-1] = merged[-1] + '\n' + ch
        else:
            merged.append(ch)
    return merged


def _clean_pdf_text(text: str) -> str:
    """清洗 PDF 提取文本中的页眉页脚噪音 + OCR 纠错"""
    # OCR/字体误识别校正
    for pat, repl in _OCR_FIXES:
        text = pat.sub(repl, text)
    # 移除竖排文字块（逐字符行）
    text = _VERTICAL_CHAR_BLOCK.sub('\n', text)
    # 移除页码和分隔线
    for pat in _PAGE_NOISE_RES:
        text = pat.sub('', text)
    # 合并多余空行
    text = re.sub(r'\n{3,}', '\n\n', text)
    return text.strip()


class DocumentLoader:
    """文档加载器入口"""

    @staticmethod
    def load_pdf(file_path: str) -> str:
        """加载 PDF，提取纯文本（使用 pymupdf，中文支持更好）"""
        import fitz
        full_text = []
        with fitz.open(file_path) as doc:
            for page in doc:
                text = page.get_text()
                if text:
                    full_text.append(text)
        raw = "\n".join(full_text)
        return _clean_pdf_text(raw)

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
