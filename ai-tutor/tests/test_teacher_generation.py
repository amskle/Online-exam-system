"""教师自然语言出题解析测试。"""
from routers.teacher import (
    _looks_like_generate_request,
    _parse_generate_request,
)


def test_detects_generate_request_in_chat():
    assert _looks_like_generate_request("生成5道关于Thymeleaf的难度中等的判断题")
    assert _looks_like_generate_request("帮我出题，考考Thymeleaf")
    assert not _looks_like_generate_request("Thymeleaf 是什么")


def test_parse_generate_request_full():
    parsed = _parse_generate_request(
        "生成5道关于Thymeleaf的难度中等的判断题",
        fallback_subject="Java",
    )
    assert parsed["subject_name"] == "Java"
    assert parsed["question_type"] == 3
    assert parsed["difficulty"] == 2
    assert parsed["count"] == 5
    assert "Thymeleaf" in parsed["extra_requirement"]


def test_parse_generate_request_with_explicit_subject():
    parsed = _parse_generate_request(
        "为框架编程技术科目生成关于Thymeleaf的难度中等的判断题",
        fallback_subject="Java",
    )
    assert parsed["subject_name"] == "框架编程技术"
    assert parsed["question_type"] == 3
    assert parsed["difficulty"] == 2
    assert parsed["count"] == 5
    assert "Thymeleaf" in parsed["extra_requirement"]


def test_parse_generate_request_uses_fallback_subject():
    parsed = _parse_generate_request("生成3道单选题", fallback_subject="计算机组成原理")
    assert parsed["subject_name"] == "计算机组成原理"
    assert parsed["question_type"] == 1
    assert parsed["count"] == 3
