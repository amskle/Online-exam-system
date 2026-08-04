"""RAG 管道共享数据模型。"""
from dataclasses import dataclass, field


@dataclass
class ParsedBlock:
    """解析器产出的结构化文本块。"""
    text: str
    kind: str = "paragraph"  # heading / paragraph / code / table / note
    level: int = 0
    page_range: str = ""


@dataclass
class ParsedDocument:
    """解析器返回的完整文档表示。"""
    format: str
    blocks: list[ParsedBlock]
    metadata: dict = field(default_factory=dict)


@dataclass
class DocumentChunk:
    """最终入库的知识分块。"""
    content: str
    metadata: dict = field(default_factory=dict)

    def __repr__(self) -> str:
        preview = self.content[:60].replace("\n", " ")
        return f"Chunk(meta={self.metadata}, text='{preview}...')"


@dataclass
class DocumentLoadResult:
    """文档加载与分块结果。"""
    chunks: list[DocumentChunk]
    format: str
    structure_type: str
    chunking_strategy: str
    warnings: list[str] = field(default_factory=list)
