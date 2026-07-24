#!/usr/bin/env python3
"""RAG 评估 CLI 入口（手写指标实现，零额外依赖）

用法:
    python -m eval.run_eval --collection teacher --samples 10
    python -m eval.run_eval --collection teacher --samples 10 --output report.json
"""
from __future__ import annotations

import argparse
import asyncio
import logging
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s %(message)s",
)
logger = logging.getLogger("eval")


async def main():
    parser = argparse.ArgumentParser(description="RAG 评估运行器")
    parser.add_argument(
        "--collection", "-c",
        default="teacher",
        choices=["teacher", "student"],
        help="要评估的知识库集合 (default: teacher)",
    )
    parser.add_argument(
        "--samples", "-n",
        type=int,
        default=None,
        help="采样数量 (default: 来自 settings.eval_sample_count)",
    )
    parser.add_argument(
        "--output", "-o",
        default=None,
        help="JSON 报告输出路径 (可选)",
    )
    args = parser.parse_args()

    # Step 1: 构建数据集
    logger.info("━━━ Step 1: 构建评估数据集 ━━━")
    from eval.dataset import build_eval_dataset
    dataset = await build_eval_dataset(
        collection=args.collection,
        sample_count=args.samples,
    )
    if dataset is None:
        logger.error("无法构建评估数据集，退出。请先上传知识库文档到 %s_kb", args.collection)
        sys.exit(1)

    # Step 2: 运行评估
    logger.info("━━━ Step 2: 运行评估 ━━━")
    from eval.runner import run_evaluation
    scores = await run_evaluation(dataset)

    # Step 3: 生成报告
    logger.info("━━━ Step 3: 生成报告 ━━━")
    from eval.report import generate_report, format_report, save_report_json
    report = generate_report(scores, args.collection, len(dataset["question"]))
    print("\n" + format_report(report))

    if args.output:
        save_report_json(report, args.output)

    # Exit code: 2 = 存在未达标项
    if not report.overall_pass:
        sys.exit(2)


if __name__ == "__main__":
    asyncio.run(main())
