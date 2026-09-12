#!/usr/bin/env python3
"""RAG 评估 CLI 入口（手写指标实现，零额外依赖）

用法:
    python -m eval.run_eval --collection teacher --samples 10
    python -m eval.run_eval --collection teacher --samples 10 --output report.json
    python -m eval.run_eval --mode student-agent --dataset student_cases.json
    python -m eval.run_eval --mode teacher-agent --dataset teacher_cases.json
"""
from __future__ import annotations

import argparse
import asyncio
import logging
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s %(message)s",
)
logger = logging.getLogger("eval")


async def main():
    parser = argparse.ArgumentParser(
        description="RAG / 完整 LangGraph 评估运行器",
        epilog=(
            "学生用例字段：question_content、correct_answer、message、token；"
            "教师用例字段：subject_id、subject_name、question_type、difficulty、count、token。"
        ),
    )
    parser.add_argument(
        "--mode",
        default="rag",
        choices=["rag", "student-agent", "teacher-agent"],
        help="评估模式；teacher-agent 会调用真实题目入库接口",
    )
    parser.add_argument(
        "--dataset",
        default=None,
        help="Agent 模式的 JSON 用例文件（数组或包含 cases 数组的对象）",
    )
    parser.add_argument(
        "--langfuse-dataset",
        default=None,
        help="同步或运行的 Langfuse Dataset 名称，例如 ai-tutor/student-v1",
    )
    parser.add_argument(
        "--experiment-name",
        default=None,
        help="Langfuse Experiment 名称；默认使用 ai-tutor-<mode>",
    )
    parser.add_argument("--run-name", default=None, help="可选的 Dataset Run 精确名称")
    parser.add_argument(
        "--sync-only",
        action="store_true",
        help="只把本地 JSON 同步到 Langfuse Dataset，不执行 Agent",
    )
    parser.add_argument(
        "--max-concurrency",
        type=int,
        default=2,
        help="Langfuse Experiment 的 Agent 并发数（默认 2）",
    )
    parser.add_argument(
        "--no-injections",
        action="store_true",
        help="学生数据集不自动扩充三种提示注入用例",
    )
    parser.add_argument(
        "--allow-db-writes",
        action="store_true",
        help="明确允许 teacher-agent 评估向测试题库真实写入题目",
    )
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

    if args.mode != "rag":
        cases = []
        if args.dataset:
            payload = json.loads(Path(args.dataset).read_text(encoding="utf-8"))
            cases = payload.get("cases", []) if isinstance(payload, dict) else payload
            if not isinstance(cases, list) or not cases:
                parser.error("Agent 评估数据集必须包含非空 cases 数组")
        elif not args.langfuse_dataset:
            parser.error("Agent 模式需要 --dataset 或 --langfuse-dataset")

        if args.mode == "teacher-agent" and not args.sync_only and not args.allow_db_writes:
            parser.error("teacher-agent 会真实写入题库；确认测试环境后传 --allow-db-writes")

        if args.langfuse_dataset:
            from eval.langfuse_experiment import (
                run_langfuse_dataset_experiment,
                sync_langfuse_dataset,
            )

            sync_result = None
            if cases:
                sync_result = sync_langfuse_dataset(
                    cases,
                    kind=args.mode,
                    dataset_name=args.langfuse_dataset,
                    include_injections=not args.no_injections,
                )
            if args.sync_only:
                if sync_result is None:
                    parser.error("--sync-only 必须同时提供 --dataset")
                rendered = json.dumps(sync_result, ensure_ascii=False, indent=2)
                print(rendered)
                if args.output:
                    Path(args.output).write_text(rendered, encoding="utf-8")
                return

            report = run_langfuse_dataset_experiment(
                kind=args.mode,
                dataset_name=args.langfuse_dataset,
                experiment_name=args.experiment_name or f"ai-tutor-{args.mode}",
                run_name=args.run_name,
                max_concurrency=args.max_concurrency,
            )
            rendered = json.dumps(report, ensure_ascii=False, indent=2)
            print(rendered)
            if args.output:
                Path(args.output).write_text(rendered, encoding="utf-8")
            if not report["overall_pass"]:
                sys.exit(2)
            return

        from eval.agent_runner import (
            run_student_agent_evaluation,
            run_teacher_agent_evaluation,
        )
        if args.mode == "student-agent":
            report = await run_student_agent_evaluation(
                cases, include_injections=not args.no_injections
            )
        else:
            report = await run_teacher_agent_evaluation(cases)
        rendered = json.dumps(report, ensure_ascii=False, indent=2)
        print(rendered)
        if args.output:
            Path(args.output).write_text(rendered, encoding="utf-8")
        if not report["overall_pass"]:
            sys.exit(2)
        return

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
