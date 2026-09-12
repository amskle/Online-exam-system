"""RAG 多路 Query 改写与会话记忆测试。"""
import asyncio

import rag.query_rewriter as query_rewriter
import rag.retriever as retriever_module
from rag.query_rewriter import (
    QueryRewriteMemory,
    clear_rewrite_cache,
    generate_query_variants,
    is_complex_query,
)
from rag.retriever import Retriever, _rrf_merge_many, clear_retrieval_cache


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


def test_query_complexity_router_uses_history_and_compound_markers():
    assert is_complex_query("Spring Boot 是什么") is False
    assert is_complex_query("它和 FastAPI 的区别是什么", ["Spring Boot 是什么"]) is True
    assert is_complex_query("分别说明事务隔离和传播机制") is True


def test_multi_query_retrieval_batches_extension_queries(monkeypatch):
    calls = []

    async def fake_variants(*args, **kwargs):
        return ["q1", "q2", "q3", "q4"]

    async def fake_semantic(queries, collection, top_k, subject_filter):
        calls.append(queries)
        return [[{
            "id": query,
            "document": query,
            "metadata": {},
            "semantic_score": 0.7,
        }] for query in queries]

    clear_retrieval_cache()
    monkeypatch.setattr(retriever_module, "generate_query_variants", fake_variants)
    monkeypatch.setattr(Retriever, "_semantic_retrieve_many", staticmethod(fake_semantic))

    result = asyncio.run(Retriever.retrieve(
        "query", "teacher", top_k=5, rewrite=True, keyword_mode="disabled"
    ))

    assert calls == [["query"], ["q1", "q2", "q3", "q4"]]
    assert {doc["id"] for doc in result} == {"query", "q1", "q2", "q3", "q4"}


def test_query_rewrite_cache_avoids_duplicate_llm_calls(monkeypatch):
    calls = 0

    async def fake_chat_json(*_args, **_kwargs):
        nonlocal calls
        calls += 1
        return ["事务有哪些隔离级别"]

    clear_rewrite_cache()
    monkeypatch.setattr(query_rewriter, "chat_json", fake_chat_json)
    first = asyncio.run(generate_query_variants("事务隔离级别是什么"))
    second = asyncio.run(generate_query_variants("事务隔离级别是什么"))

    assert first == second
    assert calls == 1


def test_query_rewrite_timeout_falls_back_to_original(monkeypatch):
    async def slow_chat_json(*_args, **_kwargs):
        await asyncio.sleep(0.05)
        return ["不应等到的改写"]

    clear_rewrite_cache()
    monkeypatch.setattr(query_rewriter, "chat_json", slow_chat_json)
    monkeypatch.setattr(query_rewriter.settings, "query_rewrite_timeout_seconds", 0.01)

    assert asyncio.run(generate_query_variants("解释事务传播机制")) == ["解释事务传播机制"]
