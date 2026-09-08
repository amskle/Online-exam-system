"""RAG 多路 Query 改写与会话记忆测试。"""
import asyncio

import rag.query_rewriter as query_rewriter
import rag.retriever as retriever_module
from rag.query_rewriter import QueryRewriteMemory, generate_query_variants
from rag.retriever import Retriever, _rrf_merge_many


def test_memory_remembers_and_seeds_only_once():
    memory = QueryRewriteMemory(max_entries=3)
    memory.seed_from_messages("s1", [
        {"role": "user", "content": "Spring Boot 是什么"},
        {"role": "assistant", "content": "回答"},
        {"role": "user", "content": "它和 FastAPI 的区别是什么"},
    ])
    assert memory.recent_queries("s1") == [
        "Spring Boot 是什么",
        "它和 FastAPI 的区别是什么",
    ]

    memory.seed_from_messages("s1", [
        {"role": "user", "content": "重复内容不应追加"},
    ])
    assert memory.recent_queries("s1")[-1] == "它和 FastAPI 的区别是什么"

    memory.remember("s1", "它和 FastAPI 的区别是什么")
    assert len(memory.recent_queries("s1")) == 2
    memory.remember("s1", "第三个问题")
    assert memory.recent_queries("s1")[-1] == "第三个问题"


def test_generate_query_variants_uses_history(monkeypatch):
    async def fake_chat_json(prompt, temperature=0.2, max_tokens=1024):
        return [
            "Spring Boot 和 FastAPI 的区别是什么",
            "Spring Boot 是什么",
            "FastAPI 和 Spring Boot 有什么不同",
        ]

    monkeypatch.setattr(query_rewriter, "chat_json", fake_chat_json)
    variants = asyncio.run(generate_query_variants(
        "它和FastAPI的区别是什么",
        history=["Spring Boot 是什么"],
        max_variants=4,
    ))
    assert variants[0] == "它和FastAPI的区别是什么"
    assert "Spring Boot 和 FastAPI 的区别是什么" in variants
    assert len(variants) == 4


def test_generate_query_variants_falls_back(monkeypatch):
    async def broken_chat_json(prompt, temperature=0.2, max_tokens=1024):
        raise RuntimeError("LLM unavailable")

    monkeypatch.setattr(query_rewriter, "chat_json", broken_chat_json)
    variants = asyncio.run(generate_query_variants("Thymeleaf 是什么"))
    assert variants == ["Thymeleaf 是什么"]


def test_rrf_merge_many_ranks_shared_docs_higher():
    ranked = _rrf_merge_many(
        [
            [{"id": "a"}, {"id": "b"}],
            [{"id": "b"}, {"id": "c"}],
        ],
        top_k=2,
    )
    assert [doc["id"] for doc in ranked] == ["b", "a"]


def test_multi_query_retrieval_has_bounded_concurrency(monkeypatch):
    active = 0
    max_active = 0

    async def fake_variants(*args, **kwargs):
        return ["q1", "q2", "q3", "q4"]

    async def fake_retrieve_variant(query, collection, hybrid_k, subject_filter):
        nonlocal active, max_active
        active += 1
        max_active = max(max_active, active)
        await asyncio.sleep(0.01)
        active -= 1
        return [{"id": query, "document": query}]

    monkeypatch.setattr(retriever_module, "generate_query_variants", fake_variants)
    monkeypatch.setattr(Retriever, "_retrieve_variant", staticmethod(fake_retrieve_variant))
    monkeypatch.setattr(retriever_module.settings, "query_rewrite_concurrency", 2)

    result = asyncio.run(Retriever.retrieve("query", "student", top_k=4))

    assert max_active == 2
    assert {doc["id"] for doc in result} == {"q1", "q2", "q3", "q4"}
