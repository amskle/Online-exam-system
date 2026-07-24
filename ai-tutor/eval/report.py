"""评估报告生成 — 格式化评估结果为可读报告"""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field
from datetime import datetime, timezone

logger = logging.getLogger("ai-tutor.eval.report")

# 各指标合理阈值
_THRESHOLDS = {
    "faithfulness": 0.80,
    "answer_relevancy": 0.75,
    "context_precision": 0.70,
    "context_recall": 0.70,
}

# 各指标中文名
_NAMES = {
    "faithfulness": "生成忠实度",
    "answer_relevancy": "答案相关性",
    "context_precision": "检索精度",
    "context_recall": "检索召回率",
}

# 各指标诊断建议
_DIAGNOSTICS = {
    "faithfulness": {
        "low": "模型存在幻觉风险 — 生成的答案未充分基于检索到的上下文，可能编造了信息。"
               "建议：检查 prompt 是否强调了 '必须基于参考资料回答'，或增大 top_k 确保上下文中包含正确答案。",
    },
    "answer_relevancy": {
        "low": "答案不够切题 — AI 输出可能偏离了用户的问题。"
               "建议：优化生成 prompt 的指令明确性，或在检索阶段引入问题重写提高语义匹配度。",
    },
    "context_precision": {
        "low": "检索噪声大 — 返回的文档中有较多与查询无关的内容，干扰了 LLM 判断。"
               "建议：减小 retrieval_top_k（当前 {top_k}），或引入 reranker 对检索结果二次排序。",
    },
    "context_recall": {
        "low": "检索遗漏信息 — 检索未找到回答问题所需的全部知识点。"
               "建议：增大 retrieval_top_k（当前 {top_k}），或检查知识库是否包含了该领域的完整内容（可能需要扩充文档）。",
    },
}


@dataclass
class EvalReport:
    """单次评估报告"""
    collection: str
    sample_count: int
    scores: dict[str, float]
    diagnostics: list[str] = field(default_factory=list)
    timestamp: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())

    @property
    def overall_pass(self) -> bool:
        """所有指标均达标"""
        return all(
            self.scores.get(k, 0) >= _THRESHOLDS.get(k, 0.7)
            for k in _NAMES
        )


def generate_report(scores: dict[str, float], collection: str, sample_count: int) -> EvalReport:
    """从评估分数生成报告

    Args:
        scores: {"faithfulness": 0.92, "answer_relevancy": 0.88, ...}
        collection: 评估的集合名
        sample_count: 样本数

    Returns:
        EvalReport 实例
    """
    from config.settings import get_settings
    settings = get_settings()

    # 生成诊断建议
    diagnostics = []
    for metric, name in _NAMES.items():
        score = scores.get(metric)
        if score is None:
            diagnostics.append(f"[{name}] 无数据")
            continue
        threshold = _THRESHOLDS.get(metric, 0.7)
        if score < threshold:
            template = _DIAGNOSTICS.get(metric, {}).get("low", "")
            template = template.format(top_k=settings.retrieval_top_k)
            diagnostics.append(f"[{name}] {template}")
        else:
            diagnostics.append(f"[{name}] 达标 ({score:.2f} ≥ {threshold})")

    return EvalReport(
        collection=collection,
        sample_count=sample_count,
        scores=scores,
        diagnostics=diagnostics,
    )


def format_report(report: EvalReport) -> str:
    """格式化为 Markdown 报告"""
    lines = [
        "=" * 54,
        "  RAG 评估报告 — 智能学习伙伴教师智能体",
        "=" * 54,
        f"  评估时间: {report.timestamp[:19]}",
        f"  知识库:   {report.collection}_kb",
        f"  样本数:   {report.sample_count}",
        f"  综合判定: {'✅ 全部达标' if report.overall_pass else '⚠️  存在未达标项'}",
        "",
        "─" * 54,
        "  指标得分",
        "─" * 54,
    ]

    for metric, name in _NAMES.items():
        score = report.scores.get(metric)
        if score is not None:
            threshold = _THRESHOLDS.get(metric, 0.7)
            flag = "✅" if score >= threshold else "⚠️"
            bar = _score_bar(score)
            lines.append(f"  {flag} {name:<6}  {bar}  {score:.3f}  (阈值 {threshold})")
        else:
            lines.append(f"  ❌ {name:<6}  无数据")

    if report.diagnostics:
        lines.append("")
        lines.append("─" * 54)
        lines.append("  诊断建议")
        lines.append("─" * 54)
        for i, diag in enumerate(report.diagnostics, 1):
            lines.append(f"\n  {i}. {diag}")

    lines.append("")
    lines.append("=" * 54)
    return "\n".join(lines)


def save_report_json(report: EvalReport, path: str):
    """保存 JSON 格式报告（可被 CI 读取）"""
    data = {
        "timestamp": report.timestamp,
        "collection": report.collection,
        "sample_count": report.sample_count,
        "scores": report.scores,
        "overall_pass": report.overall_pass,
        "diagnostics": report.diagnostics,
    }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    logger.info("报告已保存到 %s", path)


def _score_bar(score: float, width: int = 20) -> str:
    """简单的 ASCII 进度条"""
    filled = int(score * width)
    empty = width - filled
    return f"[{'█' * filled}{'░' * empty}]"
