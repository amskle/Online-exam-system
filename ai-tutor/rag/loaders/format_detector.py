"""文档格式识别工具。"""
from pathlib import Path
import zipfile


SUPPORTED_FORMATS = {"pdf", "txt", "md", "docx", "pptx"}
_TEXT_SUFFIXES = {".txt", ".md", ".markdown"}


def _looks_like_text(path: Path) -> bool:
    try:
        head = path.read_bytes()[:4096]
    except OSError:
        return False
    if not head or b"\x00" in head:
        return False
    for encoding in ("utf-8", "gbk"):
        try:
            decoded = head.decode(encoding)
        except UnicodeDecodeError:
            continue
        if not decoded.strip():
            return False
        printable = sum(1 for ch in decoded if ch.isprintable() or ch in "\r\n\t")
        return printable / len(decoded) >= 0.8
    return False


def _detect_office_format(path: Path) -> str | None:
    if not zipfile.is_zipfile(path):
        return None
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
    if "word/document.xml" in names:
        return "docx"
    if "ppt/presentation.xml" in names:
        return "pptx"
    return None


def detect_format(file_path: str | Path) -> str:
    """根据扩展名、魔数和压缩包内容识别文档格式。"""
    path = Path(file_path)
    if not path.exists():
        raise ValueError(f"文件不存在: {path}")

    head = path.read_bytes()[:8]
    if head.startswith(b"%PDF"):
        return "pdf"

    office_format = _detect_office_format(path)
    if office_format:
        return office_format

    suffix = path.suffix.lower()
    if suffix == ".pdf":
        # 允许扫描型 PDF 缺少标准文件头时仍按扩展名处理
        return "pdf"

    if suffix in _TEXT_SUFFIXES:
        return "md" if suffix in (".md", ".markdown") else "txt"

    if _looks_like_text(path):
        return "txt"

    raise ValueError(f"不支持或无法识别的文件格式: {path.name}")
