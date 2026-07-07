from __future__ import annotations

import json
import re
from pathlib import Path

import pdfplumber


SOURCE_DIR = Path(r"A:\408-master\408-master")
OUTPUT_SQL = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "data-408.sql"
OUTPUT_REPORT = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "data-408-report.json"

SUBJECT_NAME = "408计算机学科专业基础"
SUBJECT_DESCRIPTION = "全国硕士研究生招生考试计算机学科专业基础综合（408）历年真题"


def sql_string(value: str | None) -> str:
    if value is None:
        return "NULL"
    cleaned = value.replace("\x00", "").strip()
    return "'" + cleaned.replace("\\", "\\\\").replace("'", "''") + "'"


def normalize_text(text: str) -> str:
    replacements = {
        "．": ".",
        "。": "。",
        "１": "1",
        "２": "2",
        "３": "3",
        "４": "4",
        "５": "5",
        "６": "6",
        "７": "7",
        "８": "8",
        "９": "9",
        "０": "0",
    }
    for source, target in replacements.items():
        text = text.replace(source, target)
    return re.sub(r"[ \t]+", " ", text)


def extract_pdf_text(pdf_path: Path) -> str:
    with pdfplumber.open(pdf_path) as pdf:
        pages = [page.extract_text(x_tolerance=1, y_tolerance=3) or "" for page in pdf.pages]
    return normalize_text("\n".join(pages))


def parse_year(pdf_path: Path) -> int:
    match = re.search(r"(20\d{2})408", pdf_path.stem)
    if not match:
        raise ValueError(f"Cannot parse year from {pdf_path.name}")
    return int(match.group(1))


def find_answer_start(text: str) -> int:
    ref_index = text.find("参考答案")
    if ref_index >= 0:
        return ref_index
    candidates: list[int] = []
    for pattern in [r"参考答案", r"(?m)^\s*答案\s*$", r"(?m)^\s*答案\s*\n\s*(?:一、单项选择题|选择题)"]:
        candidates.extend(m.start() for m in re.finditer(pattern, text) if m.start() > 1000)
    return min(candidates) if candidates else max(len(text) - 5000, 0)


def parse_choice_answers(answer_text: str) -> dict[int, str]:
    answers: dict[int, str] = {}
    for number, answer in re.findall(r"(?<!\d)(\d{1,2})\s*\.\s*([A-D])\b", answer_text):
        n = int(number)
        if 1 <= n <= 40:
            answers[n] = answer
    for number, block in extract_numbered_blocks(answer_text, 1, 40).items():
        match = re.search(r"[【\[\［]\s*(?:答案|管案)\s*[】\]\］]\s*([A-D])\b", block)
        if match:
            answers[number] = match.group(1)
    return answers


def clean_answer_block(block: str) -> str:
    block = re.sub(r"[【\[\［]\s*(?:答案|管案)\s*[】\]\］]\s*[A-D]\b", "", block)
    block = re.sub(r"[【\[\［]\s*解析\s*[】\]\］].*", "", block, flags=re.S)
    return block.strip()


def parse_inline_choice_answers(blocks: dict[int, str]) -> dict[int, str]:
    answers: dict[int, str] = {}
    for number, block in blocks.items():
        match = re.search(r"(?:解答|答案|选)\s*[:：]?\s*([A-D])(?:\b|。|，|,)", block)
        if match:
            answers[number] = match.group(1)
    return answers


def parse_subjective_answers(answer_text: str) -> dict[int, str]:
    answers: dict[int, str] = {}
    for match in re.finditer(r"(?m)^\s*(4[1-7])\s*[\.\、．]\s*(?:【答案要点】|\[答案要点］|答案要点)?", answer_text):
        n = int(match.group(1))
        next_match = re.search(rf"(?m)^\s*(?:{n + 1}|第\s*\d+)", answer_text[match.end():])
        end = match.end() + next_match.start() if next_match else len(answer_text)
        block = answer_text[match.start():end].strip()
        if len(block) > 20:
            answers[n] = block
    return answers


def objective_section(text: str, answer_start: int) -> str:
    body = text[:answer_start]
    start_match = re.search(r"一[、,，]\s*单项选择题", body)
    start = start_match.start() if start_match else 0
    end_match = re.search(r"(?m)^\s*(?:二[、,，]\s*综合应用题|41\s*[\.\、])", body[start:])
    end = start + end_match.start() if end_match else len(body)
    return body[start:end]


def subjective_section(text: str, answer_start: int) -> str:
    body = text[:answer_start]
    start_match = re.search(r"(?m)^\s*(?:二[、,，]\s*综合应用题|41\s*[\.\、])", body)
    if not start_match:
        return ""
    return body[start_match.start():]


def marker_pattern(number: int) -> re.Pattern[str]:
    if number == 1:
        token = r"(?:1|l|I)"
    else:
        token = str(number)
    return re.compile(rf"(?m)^\s*{token}\s*[\.\、．]\s*")


def extract_numbered_blocks(section: str, start: int, end: int) -> dict[int, str]:
    positions: dict[int, tuple[int, int]] = {}
    for number in range(start, end + 1):
        match = marker_pattern(number).search(section)
        if match:
            positions[number] = (match.start(), match.end())
    blocks: dict[int, str] = {}
    ordered = sorted(positions.items(), key=lambda item: item[1][0])
    for index, (number, (pos, content_start)) in enumerate(ordered):
        next_pos = ordered[index + 1][1][0] if index + 1 < len(ordered) else len(section)
        block = section[content_start:next_pos].strip()
        block = re.sub(r"\n\s*第\s*\d+\s*/\s*\d+\s*页\s*\n", "\n", block)
        block = re.sub(r"\n{3,}", "\n\n", block)
        if len(block) > 8:
            blocks[number] = block
    return blocks


def make_choice_options() -> str:
    return json.dumps(
        [
            {"label": "A", "content": "选项A（详见题干）"},
            {"label": "B", "content": "选项B（详见题干）"},
            {"label": "C", "content": "选项C（详见题干）"},
            {"label": "D", "content": "选项D（详见题干）"},
        ],
        ensure_ascii=False,
    )


def difficulty_for(number: int) -> int:
    if number <= 20:
        return 2
    return 3


def generate() -> None:
    rows: list[str] = []
    report: dict[str, dict[str, int | list[int]]] = {}
    option_json = make_choice_options()

    for pdf_path in sorted(SOURCE_DIR.glob("*408.pdf")):
        year = parse_year(pdf_path)
        text = extract_pdf_text(pdf_path)
        answer_start = find_answer_start(text)
        answer_text = text[answer_start:]
        choice_answers = parse_choice_answers(answer_text)
        subjective_answers = parse_subjective_answers(answer_text)
        objective_blocks = extract_numbered_blocks(objective_section(text, answer_start), 1, 40)
        answer_objective_blocks = extract_numbered_blocks(answer_text, 1, 40)
        choice_answers = {**parse_inline_choice_answers(objective_blocks), **parse_inline_choice_answers(answer_objective_blocks), **choice_answers}
        subjective_blocks = extract_numbered_blocks(subjective_section(text, answer_start), 41, 47)

        missing_choices: list[int] = []
        for number in range(1, 41):
            content = clean_answer_block(answer_objective_blocks.get(number, "")) if number in answer_objective_blocks else objective_blocks.get(number)
            answer = choice_answers.get(number)
            if not answer:
                missing_choices.append(number)
                continue
            if not content:
                content = f"本题 PDF 文本层无法稳定抽取题干，请参考源文件 {pdf_path.name} 第 {number} 题。"
            title = f"【{year}年408真题第{number}题】\n{content}"
            rows.append(
                "("
                "@subject_id, "
                f"{sql_string(SUBJECT_NAME)}, "
                "1, "
                f"{difficulty_for(number)}, "
                f"{sql_string(title)}, "
                f"{sql_string(option_json)}, "
                f"{sql_string(answer)}, "
                f"{sql_string('答案来自原 PDF 参考答案；选项文字请参见题干原文。')}, "
                "2, "
                "NOW()"
                ")"
            )

        subjective_missing: list[int] = []
        for number in range(41, 48):
            content = subjective_blocks.get(number)
            if not content:
                subjective_missing.append(number)
                continue
            answer = subjective_answers.get(number, "参考答案请见原 PDF 对应题号答案要点。")
            title = f"【{year}年408真题第{number}题】\n{content}"
            rows.append(
                "("
                "@subject_id, "
                f"{sql_string(SUBJECT_NAME)}, "
                "4, "
                "3, "
                f"{sql_string(title)}, "
                "NULL, "
                f"{sql_string(answer)}, "
                f"{sql_string('综合应用题，答案为原 PDF 参考答案要点或答案索引。')}, "
                "10, "
                "NOW()"
                ")"
            )

        report[str(year)] = {
            "choice_answers": len(choice_answers),
            "choice_questions": len(objective_blocks),
            "choice_imported": 40 - len(missing_choices),
            "choice_missing": missing_choices,
            "subjective_questions": len(subjective_blocks),
            "subjective_imported": 7 - len(subjective_missing),
            "subjective_missing": subjective_missing,
        }

    sql = [
        "-- 408计算机学科专业基础历年真题题库种子数据",
        "-- 来源：A:/408-master/408-master 下 2009-2021 年 408 真题 PDF",
        "-- 说明：PDF 文本层存在少量 OCR/排版噪声，选择题答案来自参考答案页。",
        "",
        "INSERT INTO subject (name, description, create_time)",
        f"SELECT {sql_string(SUBJECT_NAME)}, {sql_string(SUBJECT_DESCRIPTION)}, NOW()",
        f"WHERE NOT EXISTS (SELECT 1 FROM subject WHERE name = {sql_string(SUBJECT_NAME)});",
        "",
        f"SET @subject_id := (SELECT id FROM subject WHERE name = {sql_string(SUBJECT_NAME)} LIMIT 1);",
        "",
        "DELETE FROM question WHERE subject_name = '408计算机学科专业基础' AND content LIKE '【%年408真题第%题】%';",
        "",
    ]
    if rows:
        sql.extend(
            [
                "INSERT INTO question (subject_id, subject_name, type, difficulty, content, options, answer, analysis, score, create_time)",
                "VALUES",
                ",\n".join(rows) + ";",
                "",
            ]
        )

    OUTPUT_SQL.write_text("\n".join(sql), encoding="utf-8")
    OUTPUT_REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    generate()
