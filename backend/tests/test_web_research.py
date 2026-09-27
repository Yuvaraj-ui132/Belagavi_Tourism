"""
Tests for web-grounded research feature.

Covers all 12 original requirements plus revised layer-order tests:

QUERY ROUTER (unchanged)
  1.  Current-info queries route to web research.
  2.  General tourism queries route to local RAG only.

WEB RESEARCH SERVICE — TAVILY (PRIMARY layer)
  3.  Tavily is called first when TAVILY_API_KEY is set.
  4.  Tavily success populates web_sources with real titles/URLs/domains/snippets.
  5.  Tavily failure falls back gracefully (no crash, returns []).
  6.  Missing TAVILY_API_KEY skips Tavily, does NOT crash.
  7.  Tavily-absent + Gemini-grounding-absent returns [] (graceful).

WEB RESEARCH SERVICE — GEMINI GROUNDING (SECONDARY / fallback)
  8.  Gemini grounding runs only when Tavily returns < WEB_RESULT_LIMIT results.
  9.  Gemini 429 is caught, logged, does NOT propagate.
  10. grounding_chunks are parsed into WebSource objects when present.

SOURCE INTEGRITY
  11. URLs come from search results, not the LLM response JSON.
  12. Duplicate URLs are deduplicated.

SCHEMA
  13. ChatResponse.web_sources is List[WebSource] defaulting to [].
  14. ChatResponse.web_research_used defaults to False.

INTEGRATION (via TestClient, mocked services)
  15. /api/health still returns 200.
  16. /api/search still returns results.
  17. General query → web_research_used = False, web_sources = [].
  18. Current-info query + Tavily results → web_research_used = True, web_sources populated.
  19. Tavily failure during current-info query → graceful fallback, still returns answer.
  20. Missing TAVILY_API_KEY → web_research_used = False, no crash.
  21. Secrets are NOT present in any API response body.
  22. Supabase RAG still works (deterministic DB fields from ORM, not LLM).
  23. web_research_used = False when WEB_RESEARCH_ENABLED = false.
"""

from __future__ import annotations

import os
from typing import List
from unittest.mock import AsyncMock, MagicMock, patch

import pytest


# ===========================================================================
# TEST GROUP 1: Query Router (deterministic — no API calls)
# ===========================================================================

class TestQueryRouter:

    def test_current_triggers_web_research(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("What is the current entry fee for Belagavi Fort?") is True

    def test_today_triggers(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("Is Belagavi Fort open today?") is True

    def test_now_triggers(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("Is Gokak Falls open now?") is True

    def test_latest_triggers(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("What are the latest tourism updates in Belagavi?") is True

    def test_recent_triggers(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("Recent changes to Belagavi Fort timings") is True

    def test_entry_fee_triggers(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("What is the entry fee for Gokak Falls?") is True

    def test_ticket_price_triggers(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("ticket price for Belagavi Fort") is True

    def test_timing_triggers(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("What are the timings for Sambra viewpoint?") is True

    def test_contact_triggers(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("Contact number for Belagavi Fort?") is True

    def test_event_triggers(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("Any events at Kittur this month?") is True

    def test_closure_triggers(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("Is there a closure at Gokak Falls?") is True

    def test_general_query_does_not_trigger(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("Tell me about Belagavi Fort") is False

    def test_waterfalls_does_not_trigger(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("Suggest waterfalls near Belagavi") is False

    def test_historical_places_does_not_trigger(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("What historical places are in Belagavi?") is False

    def test_temples_does_not_trigger(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("Which temples can I visit?") is False

    def test_empty_query_does_not_trigger(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("") is False

    def test_whitespace_query_does_not_trigger(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("   ") is False

    def test_case_insensitive(self):
        from app.services.query_router import needs_web_research
        assert needs_web_research("CURRENTLY OPEN?") is True
        assert needs_web_research("Latest NEWS") is True

    def test_classify_query_labels(self):
        from app.services.query_router import classify_query
        assert classify_query("Tell me about the fort") == "local_rag"
        assert classify_query("Is it open today?") == "web_research"


# ===========================================================================
# TEST GROUP 2: WebResearchService — Tavily (PRIMARY layer)
# ===========================================================================

class TestTavilyPrimary:
    """Tavily is the primary layer — tests for success, failure, and absent key."""

    def _make_tavily_result(self, n: int = 2) -> dict:
        """Build a fake Tavily API response dict."""
        return {
            "results": [
                {
                    "title": f"Belagavi Tourism Page {i}",
                    "url": f"https://example{i}.com/belagavi",
                    "content": f"Snippet about Belagavi destination {i}.",
                }
                for i in range(1, n + 1)
            ]
        }

    def test_tavily_is_called_first_when_key_is_set(self):
        """Requirement 3: Tavily runs first when TAVILY_API_KEY is set.
        Gemini grounding may still supplement if Tavily returns < WEB_RESULT_LIMIT results.
        The key assertion is: Tavily is called AND its results appear in the output.
        """
        import asyncio
        from app.services.web_research_service import WebResearchService, WebSource, WEB_RESULT_LIMIT

        svc = WebResearchService()

        tavily_called = []
        tavily_source = WebSource(title="Tavily Result", url="https://tavily-source.com", domain="tavily-source.com", snippet="s")

        async def _mock_tavily(query, api_key, max_results=5):
            tavily_called.append(True)
            # Return WEB_RESULT_LIMIT results so Gemini is NOT triggered
            return [
                WebSource(title=f"T{i}", url=f"https://t{i}.com", domain=f"t{i}.com", snippet="")
                for i in range(WEB_RESULT_LIMIT)
            ]

        async def _mock_gemini(query, settings):
            return []

        with patch.object(svc, "_search_via_tavily", _mock_tavily):
            with patch.object(svc, "_search_via_gemini_grounding", _mock_gemini):
                with patch.dict(os.environ, {"TAVILY_API_KEY": "tvly-test-key"}):
                    from app.config import get_settings
                    get_settings.cache_clear()
                    try:
                        result = asyncio.run(svc.search("current entry fee"))
                    finally:
                        get_settings.cache_clear()

        # Tavily must have been called
        assert len(tavily_called) == 1, "Tavily must be called when key is present"
        # All results come from Tavily (WEB_RESULT_LIMIT returned, all kept)
        assert len(result) == WEB_RESULT_LIMIT
        assert all(r.url.startswith("https://t") for r in result)


    def test_tavily_success_populates_web_sources(self):
        """Requirement 4: Tavily success → real titles/URLs/domains/snippets."""
        import asyncio
        from app.services.web_research_service import WebResearchService, WebSource

        svc = WebResearchService()

        async def _mock_tavily(query, api_key, max_results=5):
            return [
                WebSource(
                    title="Belagavi Fort Entry Fee 2024",
                    url="https://karnatakatourism.org/fort",
                    domain="karnatakatourism.org",
                    snippet="Entry to Belagavi Fort is free.",
                ),
                WebSource(
                    title="Visit Belagavi",
                    url="https://belagavitourism.in/fort",
                    domain="belagavitourism.in",
                    snippet="The fort is open daily from 8 AM to 6 PM.",
                ),
            ]

        with patch.object(svc, "_search_via_tavily", _mock_tavily):
            with patch.dict(os.environ, {"TAVILY_API_KEY": "tvly-test-key"}):
                from app.config import get_settings
                get_settings.cache_clear()
                try:
                    result = asyncio.run(svc.search("current entry fee"))
                finally:
                    get_settings.cache_clear()

        assert len(result) == 2
        assert result[0].url == "https://karnatakatourism.org/fort"
        assert result[0].title == "Belagavi Fort Entry Fee 2024"
        assert result[0].domain == "karnatakatourism.org"
        assert result[0].snippet == "Entry to Belagavi Fort is free."
        assert result[1].url == "https://belagavitourism.in/fort"

    def test_tavily_failure_falls_back_gracefully(self):
        """Requirement 5: Tavily exception → returns [] without crashing."""
        import asyncio
        from app.services.web_research_service import WebResearchService

        svc = WebResearchService()

        async def _broken_tavily(query, api_key, max_results=5):
            raise RuntimeError("Tavily network error")

        async def _no_gemini(query, settings):
            return []

        with patch.object(svc, "_search_via_tavily", _broken_tavily):
            with patch.object(svc, "_search_via_gemini_grounding", _no_gemini):
                with patch.dict(os.environ, {"TAVILY_API_KEY": "tvly-test-key"}):
                    from app.config import get_settings
                    get_settings.cache_clear()
                    try:
                        result = asyncio.run(svc.search("current entry fee"))
                    finally:
                        get_settings.cache_clear()

        assert isinstance(result, list)
        assert result == []

    def test_missing_tavily_key_skips_tavily_no_crash(self):
        """Requirement 6: No TAVILY_API_KEY → Tavily skipped silently."""
        import asyncio
        from unittest.mock import MagicMock, patch
        from app.services.web_research_service import WebResearchService

        svc = WebResearchService()

        tavily_called = []

        async def _mock_tavily(query, api_key, max_results=5):
            tavily_called.append(True)
            return []

        async def _no_gemini(query, settings):
            return []

        mock_settings = MagicMock()
        mock_settings.tavily_api_key = None
        mock_settings.gemini_api_key = "test"

        with patch.object(svc, "_search_via_tavily", _mock_tavily):
            with patch.object(svc, "_search_via_gemini_grounding", _no_gemini):
                with patch("app.services.web_research_service.get_settings", return_value=mock_settings):
                    result = asyncio.run(svc.search("current entry fee"))

        # Tavily must not have been called when key is absent
        assert len(tavily_called) == 0
        assert isinstance(result, list)
        assert result == []

    def test_tavily_parses_raw_api_response_correctly(self):
        """_search_via_tavily correctly maps Tavily JSON → WebSource objects."""
        import asyncio
        from app.services.web_research_service import WebResearchService

        svc = WebResearchService()

        fake_response = {
            "results": [
                {
                    "title": "Belagavi Fort",
                    "url": "https://example.com/fort",
                    "content": "The fort entry is free. Open 8 AM to 6 PM.",
                },
                {
                    "title": "Gokak Falls",
                    "url": "https://example.com/falls",
                    "content": "Spectacular 52 metre falls.",
                },
            ]
        }

        def _mock_client_search(*args, **kwargs):
            return fake_response

        with patch("tavily.TavilyClient") as mock_client_cls:
            mock_instance = MagicMock()
            mock_instance.search.return_value = fake_response
            mock_client_cls.return_value = mock_instance

            result = asyncio.run(
                svc._search_via_tavily("current entry fee", "tvly-fake-key", max_results=5)
            )

        assert len(result) == 2
        assert result[0].title == "Belagavi Fort"
        assert result[0].url == "https://example.com/fort"
        assert result[0].domain == "example.com"
        assert "free" in result[0].snippet
        assert result[1].url == "https://example.com/falls"

    def test_tavily_skips_items_without_url(self):
        """Items missing 'url' in Tavily response are silently skipped."""
        import asyncio
        from app.services.web_research_service import WebResearchService

        svc = WebResearchService()

        fake_response = {
            "results": [
                {"title": "No URL item", "url": "", "content": "Should be skipped."},
                {"title": "Good item", "url": "https://example.com", "content": "Good."},
            ]
        }

        with patch("tavily.TavilyClient") as mock_cls:
            mock_cls.return_value.search.return_value = fake_response
            result = asyncio.run(
                svc._search_via_tavily("test", "tvly-fake-key", max_results=5)
            )

        assert len(result) == 1
        assert result[0].url == "https://example.com"

    def test_tavily_respects_max_results(self):
        """Tavily results are capped at max_results."""
        import asyncio
        from app.services.web_research_service import WebResearchService

        svc = WebResearchService()

        fake_response = {
            "results": [
                {"title": f"Item {i}", "url": f"https://example.com/{i}", "content": ""}
                for i in range(10)
            ]
        }

        with patch("tavily.TavilyClient") as mock_cls:
            mock_cls.return_value.search.return_value = fake_response
            result = asyncio.run(
                svc._search_via_tavily("test", "tvly-fake-key", max_results=3)
            )

        assert len(result) <= 3


# ===========================================================================
# TEST GROUP 3: WebResearchService — Gemini Grounding (SECONDARY layer)
# ===========================================================================

class TestGeminiGroundingSecondary:
    """Gemini grounding is the secondary layer — runs only if Tavily is insufficient."""

    def test_gemini_runs_only_when_tavily_insufficient(self):
        """Requirement 8: Gemini grounding runs only when Tavily returns < WEB_RESULT_LIMIT."""
        import asyncio
        from app.services.web_research_service import WebResearchService, WebSource, WEB_RESULT_LIMIT

        svc = WebResearchService()
        gemini_called = []

        # Tavily returns fewer than WEB_RESULT_LIMIT results
        async def _partial_tavily(query, api_key, max_results=5):
            return [WebSource(title="T", url="https://t.com", domain="t.com", snippet="")]

        async def _mock_gemini(query, settings):
            gemini_called.append(True)
            return []

        with patch.object(svc, "_search_via_tavily", _partial_tavily):
            with patch.object(svc, "_search_via_gemini_grounding", _mock_gemini):
                with patch.dict(os.environ, {"TAVILY_API_KEY": "tvly-test"}):
                    from app.config import get_settings
                    get_settings.cache_clear()
                    try:
                        asyncio.run(svc.search("current entry fee"))
                    finally:
                        get_settings.cache_clear()

        # Gemini should be attempted since Tavily returned < WEB_RESULT_LIMIT
        assert len(gemini_called) == 1

    def test_gemini_not_called_when_tavily_fills_limit(self):
        """Gemini should NOT be called if Tavily already filled WEB_RESULT_LIMIT slots."""
        import asyncio
        from app.services.web_research_service import WebResearchService, WebSource, WEB_RESULT_LIMIT

        svc = WebResearchService()
        gemini_called = []

        async def _full_tavily(query, api_key, max_results=5):
            return [
                WebSource(title=f"T{i}", url=f"https://t{i}.com", domain=f"t{i}.com", snippet="")
                for i in range(WEB_RESULT_LIMIT)
            ]

        async def _mock_gemini(query, settings):
            gemini_called.append(True)
            return []

        with patch.object(svc, "_search_via_tavily", _full_tavily):
            with patch.object(svc, "_search_via_gemini_grounding", _mock_gemini):
                with patch.dict(os.environ, {"TAVILY_API_KEY": "tvly-test"}):
                    from app.config import get_settings
                    get_settings.cache_clear()
                    try:
                        asyncio.run(svc.search("current entry fee"))
                    finally:
                        get_settings.cache_clear()

        assert len(gemini_called) == 0

    def test_gemini_429_caught_does_not_propagate(self):
        """Requirement 9: Gemini 429 RESOURCE_EXHAUSTED is caught, never raises."""
        import asyncio
        from app.services.web_research_service import WebResearchService

        svc = WebResearchService()

        async def _no_tavily(query, api_key, max_results=5):
            return []

        async def _grounding_429(query, settings):
            raise Exception("429 RESOURCE_EXHAUSTED. You exceeded your current quota.")

        with patch.object(svc, "_search_via_tavily", _no_tavily):
            with patch.object(svc, "_search_via_gemini_grounding", _grounding_429):
                with patch.dict(os.environ, {"TAVILY_API_KEY": "tvly-test"}):
                    from app.config import get_settings
                    get_settings.cache_clear()
                    try:
                        result = asyncio.run(svc.search("current entry fee"))
                    finally:
                        get_settings.cache_clear()

        assert isinstance(result, list)
        assert result == []

    def test_grounding_sources_extracted_correctly(self):
        """Requirement 10: grounding_chunks are parsed into WebSource objects."""
        from app.services.web_research_service import WebResearchService, WebSource

        svc = WebResearchService()

        mock_chunk = MagicMock()
        mock_chunk.web.uri = "https://karnatakatourism.org/fort"
        mock_chunk.web.title = "Belagavi Fort - Karnataka Tourism"

        mock_gm = MagicMock()
        mock_gm.grounding_chunks = [mock_chunk]

        mock_candidate = MagicMock()
        mock_candidate.grounding_metadata = mock_gm

        mock_response = MagicMock()
        mock_response.candidates = [mock_candidate]

        result = svc._extract_grounding_sources(mock_response)

        assert len(result) == 1
        assert isinstance(result[0], WebSource)
        assert result[0].url == "https://karnatakatourism.org/fort"
        assert result[0].title == "Belagavi Fort - Karnataka Tourism"
        assert result[0].domain == "karnatakatourism.org"

    def test_grounding_empty_response_returns_empty(self):
        from app.services.web_research_service import WebResearchService

        svc = WebResearchService()
        mock_response = MagicMock()
        mock_response.candidates = []
        assert svc._extract_grounding_sources(mock_response) == []


# ===========================================================================
# TEST GROUP 4: Source Integrity
# ===========================================================================

class TestSourceIntegrity:

    def test_urls_not_invented_by_llm(self):
        """Requirement 11: URLs come from search results, not LLM JSON."""
        from app.services.web_research_service import WebResearchService

        svc = WebResearchService()
        mock_response = MagicMock()
        mock_response.candidates = []
        result = svc._extract_grounding_sources(mock_response)
        assert result == []

    def test_deduplicate_sources_removes_duplicate_urls(self):
        """Requirement 12: Duplicate URLs are removed."""
        from app.services.web_research_service import WebSource, _deduplicate_sources

        sources = [
            WebSource(title="A", url="https://example.com/a", domain="example.com"),
            WebSource(title="B", url="https://example.com/a", domain="example.com"),
            WebSource(title="C", url="https://other.com/c",   domain="other.com"),
        ]
        result = _deduplicate_sources(sources)
        assert len(result) == 2
        assert result[0].url == "https://example.com/a"
        assert result[1].url == "https://other.com/c"

    def test_domain_extraction(self):
        from app.services.web_research_service import _extract_domain
        assert _extract_domain("https://karnatakatourism.org/fort") == "karnatakatourism.org"
        assert _extract_domain("http://www.example.com/page?q=1")   == "www.example.com"
        assert _extract_domain("") == ""


# ===========================================================================
# TEST GROUP 5: Schema
# ===========================================================================

class TestChatResponseSchema:

    def test_chat_response_has_web_sources_field(self):
        from app.schemas.chat import ChatResponse
        assert "web_sources" in ChatResponse.model_fields

    def test_chat_response_has_web_research_used_field(self):
        from app.schemas.chat import ChatResponse
        assert "web_research_used" in ChatResponse.model_fields

    def test_chat_response_web_sources_defaults_to_empty(self):
        from app.schemas.chat import ChatResponse
        r = ChatResponse(answer="Test", destinations=[], sources=[], retrieved_count=0)
        assert r.web_sources == []
        assert r.web_research_used is False

    def test_web_source_schema_fields(self):
        from app.schemas.chat import WebSource
        src = WebSource(title="Fort", url="https://example.com", domain="example.com")
        assert src.title  == "Fort"
        assert src.url    == "https://example.com"
        assert src.domain == "example.com"

    def test_existing_sources_field_is_list_of_strings(self):
        from app.schemas.chat import ChatResponse
        r = ChatResponse(
            answer="Answer", destinations=[], sources=["Belagavi Fort"], retrieved_count=1
        )
        assert isinstance(r.sources, list)
        assert all(isinstance(s, str) for s in r.sources)


# ===========================================================================
# TEST GROUP 6: Context Builder
# ===========================================================================

class TestBuildWebContext:

    def test_empty_sources_returns_no_results_message(self):
        from app.rag.context_builder import build_web_context
        result = build_web_context([])
        assert "No current web information" in result

    def test_single_source_formatted_correctly(self):
        from app.rag.context_builder import build_web_context
        from app.services.web_research_service import WebSource

        src = WebSource(
            title="Belagavi Fort Info",
            url="https://karnatakatourism.org/fort",
            domain="karnatakatourism.org",
            snippet="Entry is free.",
        )
        result = build_web_context([src])
        assert "[WEB SOURCE 1]" in result
        assert "karnatakatourism.org/fort" in result
        assert "Entry is free." in result

    def test_multiple_sources_numbered_sequentially(self):
        from app.rag.context_builder import build_web_context
        from app.services.web_research_service import WebSource

        sources = [
            WebSource(title="A", url="https://a.com", domain="a.com", snippet="A"),
            WebSource(title="B", url="https://b.com", domain="b.com", snippet="B"),
        ]
        result = build_web_context(sources)
        assert "[WEB SOURCE 1]" in result
        assert "[WEB SOURCE 2]" in result

    def test_hybrid_prompt_has_both_placeholders(self):
        from app.rag.context_builder import HYBRID_SYSTEM_PROMPT_TEMPLATE
        assert "{context}"     in HYBRID_SYSTEM_PROMPT_TEMPLATE
        assert "{web_context}" in HYBRID_SYSTEM_PROMPT_TEMPLATE

    def test_hybrid_prompt_mentions_source_attribution(self):
        from app.rag.context_builder import HYBRID_SYSTEM_PROMPT_TEMPLATE
        lower = HYBRID_SYSTEM_PROMPT_TEMPLATE.lower()
        assert "source a" in lower or "local knowledge" in lower
        assert "source b" in lower or "web" in lower


# ===========================================================================
# Integration helpers
# ===========================================================================

def _make_orm_dest(place_id: int, name: str, category: str):
    from app.models.destination import Destination
    d = Destination()
    d.place_id        = place_id
    d.name            = name
    d.category        = category
    d.city            = "Belagavi"
    d.description     = f"A wonderful {category}"
    d.history         = "Historic site"
    d.architecture    = "Stone"
    d.famous_features = "Great views"
    d.best_time       = "October to March"
    d.entry_fee       = "Free"
    d.visit_duration  = "2 Hours"
    d.how_to_reach    = "By road"
    d.local_tips      = "Carry water"
    d.detailed_history   = "Long history"
    d.transport_summary  = "Auto: Rs.60"
    d.folder_name     = name.lower().replace(" ", "_")
    d.lat             = 15.86
    d.lon             = 74.52
    return d


VALID_LLM_RESPONSE = {
    "answer": "Belagavi Fort is a historic destination.",
    "destinations": [
        {"place_id": 1, "name": "Belagavi Fort", "reason": "800+ years of history."},
    ],
    "sources": ["Belagavi Fort"],
}


def _make_tavily_web_source(n: int = 1):
    from app.services.web_research_service import WebSource
    return [
        WebSource(
            title=f"Belagavi Tourism Source {i}",
            url=f"https://source{i}.com/belagavi",
            domain=f"source{i}.com",
            snippet=f"Current information {i}",
        )
        for i in range(1, n + 1)
    ]


def _patch_pipeline(mock_docs, mock_llm_dict, mock_web_sources=None):
    """Patch SearchService, LLMClient, and WebResearchService together."""
    from app.services import search_service
    from app.rag import llm_client
    from app.services import web_research_service

    s = patch.object(
        search_service.SearchService,
        "get_top_for_rag",
        AsyncMock(return_value=mock_docs),
    )
    l = patch.object(
        llm_client.LLMClient,
        "generate_rag_response",
        AsyncMock(return_value=mock_llm_dict),
    )
    w = patch.object(
        web_research_service.WebResearchService,
        "search",
        AsyncMock(return_value=mock_web_sources or []),
    )
    return s, l, w


# ===========================================================================
# TEST GROUP 7: Integration via TestClient
# ===========================================================================

class TestWebResearchIntegration:

    def test_health_still_works(self, test_client):
        """Requirement 15: /api/health returns 200."""
        r = test_client.get("/api/health")
        assert r.status_code == 200
        assert r.json()["status"] == "ok"

    def test_search_still_works(self, test_client):
        """Requirement 16: /api/search returns results."""
        from app.services import search_service
        from app.schemas.search import DestinationResult

        mock = DestinationResult(
            place_id=1, name="Belagavi Fort", category="Fort", city="Belagavi",
            description="Historic", best_time="Year-round", entry_fee="Free",
            visit_duration="2 Hours", folder_name="belagavi_fort",
            lat=15.86, lon=74.52, similarity=0.92,
        )
        with patch.object(search_service.SearchService, "search", AsyncMock(return_value=[mock])):
            r = test_client.post("/api/search", json={"query": "historic forts"})

        assert r.status_code == 200
        assert r.json()["results"][0]["name"] == "Belagavi Fort"

    def test_general_query_no_web_research(self, test_client):
        """Requirement 17: General query → web_research_used=False, web_sources=[]."""
        s, l, w = _patch_pipeline(
            [_make_orm_dest(1, "Belagavi Fort", "Fort")],
            VALID_LLM_RESPONSE,
        )
        with s, l, w:
            r = test_client.post("/api/chat", json={"message": "Tell me about Belagavi Fort"})

        assert r.status_code == 200
        body = r.json()
        assert body["web_research_used"] is False
        assert body["web_sources"] == []
        assert "answer" in body
        assert "destinations" in body
        assert "sources" in body
        assert "retrieved_count" in body

    def test_current_query_with_tavily_results(self, test_client):
        """Requirement 18: Current-info query + Tavily results → web_research_used=True."""
        s, l, w = _patch_pipeline(
            [_make_orm_dest(1, "Belagavi Fort", "Fort")],
            VALID_LLM_RESPONSE,
            _make_tavily_web_source(2),
        )
        with s, l, w:
            r = test_client.post(
                "/api/chat",
                json={"message": "What is the current entry fee for Belagavi Fort?"},
            )

        assert r.status_code == 200
        body = r.json()
        assert body["web_research_used"] is True
        assert len(body["web_sources"]) == 2

    def test_web_sources_have_required_fields(self, test_client):
        """web_sources items have title, url, domain."""
        s, l, w = _patch_pipeline(
            [_make_orm_dest(1, "Belagavi Fort", "Fort")],
            VALID_LLM_RESPONSE,
            _make_tavily_web_source(1),
        )
        with s, l, w:
            r = test_client.post(
                "/api/chat",
                json={"message": "current entry fee belagavi fort"},
            )

        assert r.status_code == 200
        src = r.json()["web_sources"][0]
        assert "title"  in src
        assert "url"    in src
        assert "domain" in src
        assert src["url"].startswith("https://")

    def test_tavily_urls_not_fabricated(self, test_client):
        """Requirement 11: URL in web_sources comes from Tavily, not LLM JSON."""
        real_url = "https://real-tavily-source.com/belagavi"
        from app.services.web_research_service import WebSource
        mock_web = [WebSource(title="Real", url=real_url, domain="real-tavily-source.com", snippet="")]

        s, l, w = _patch_pipeline(
            [_make_orm_dest(1, "Belagavi Fort", "Fort")],
            # LLM JSON has no URL — URL must come from mock_web only
            {"answer": "Current fee info.", "destinations": [], "sources": []},
            mock_web,
        )
        with s, l, w:
            r = test_client.post("/api/chat", json={"message": "current entry fee"})

        assert r.status_code == 200
        assert r.json()["web_sources"][0]["url"] == real_url

    def test_tavily_failure_falls_back_to_supabase_rag(self, test_client):
        """Requirement 19: Tavily failure → answer still returned from Supabase RAG."""
        from app.services import web_research_service

        s_p = patch.object(
            __import__("app.services.search_service", fromlist=["SearchService"]).SearchService,
            "get_top_for_rag",
            AsyncMock(return_value=[_make_orm_dest(1, "Belagavi Fort", "Fort")]),
        )
        l_p = patch.object(
            __import__("app.rag.llm_client", fromlist=["LLMClient"]).LLMClient,
            "generate_rag_response",
            AsyncMock(return_value=VALID_LLM_RESPONSE),
        )
        w_p = patch.object(
            web_research_service.WebResearchService,
            "search",
            AsyncMock(side_effect=RuntimeError("Tavily error")),
        )

        with s_p, l_p, w_p:
            r = test_client.post(
                "/api/chat",
                json={"message": "current entry fee for belagavi fort"},
            )

        assert r.status_code == 200
        body = r.json()
        assert "answer" in body
        assert body["web_research_used"] is False

    def test_missing_tavily_key_no_crash(self, test_client):
        """Requirement 20: Missing TAVILY_API_KEY → no crash, RAG still answers."""
        from app.config import get_settings
        old = os.environ.pop("TAVILY_API_KEY", None)
        get_settings.cache_clear()
        try:
            s, l, w = _patch_pipeline(
                [_make_orm_dest(1, "Belagavi Fort", "Fort")],
                VALID_LLM_RESPONSE,
            )
            with s, l, w:
                r = test_client.post("/api/chat", json={"message": "Tell me about Belagavi Fort"})
        finally:
            if old:
                os.environ["TAVILY_API_KEY"] = old
            get_settings.cache_clear()

        assert r.status_code == 200

    def test_secrets_not_in_api_response(self, test_client):
        """Requirement 21: API keys never appear in response body."""
        from app.config import get_settings
        settings = get_settings()
        gemini_key = settings.gemini_api_key

        s, l, w = _patch_pipeline(
            [_make_orm_dest(1, "Belagavi Fort", "Fort")],
            VALID_LLM_RESPONSE,
        )
        with s, l, w:
            r = test_client.post("/api/chat", json={"message": "Tell me about Belagavi Fort"})

        assert r.status_code == 200
        body_text = r.text
        assert gemini_key not in body_text
        for secret in ["GEMINI_API_KEY", "TAVILY_API_KEY", "POSTGRES_PASSWORD", "DATABASE_URL"]:
            assert secret not in body_text

    def test_supabase_rag_still_works(self, test_client):
        """Requirement 22: Deterministic DB fields come from ORM, not LLM."""
        s, l, w = _patch_pipeline(
            [_make_orm_dest(1, "Belagavi Fort", "Fort"),
             _make_orm_dest(6, "Gokak Falls",   "Waterfall")],
            {
                "answer": "Great places.",
                "destinations": [
                    {"place_id": 1, "name": "Belagavi Fort", "reason": "Historic."},
                    {"place_id": 6, "name": "Gokak Falls",   "reason": "Scenic."},
                ],
                "sources": ["Belagavi Fort", "Gokak Falls"],
            },
        )
        with s, l, w:
            r = test_client.post("/api/chat", json={"message": "Suggest places to visit"})

        assert r.status_code == 200
        body = r.json()
        assert body["retrieved_count"] == 2
        fort = next(d for d in body["destinations"] if d["place_id"] == 1)
        assert fort["entry_fee"] == "Free"
        assert fort["city"]      == "Belagavi"

    def test_web_research_used_false_when_disabled(self, test_client):
        """Requirement 23: WEB_RESEARCH_ENABLED=false → web_research_used=False."""
        from app.config import get_settings
        old = os.environ.get("WEB_RESEARCH_ENABLED")
        os.environ["WEB_RESEARCH_ENABLED"] = "false"
        get_settings.cache_clear()
        try:
            s, l, w = _patch_pipeline(
                [_make_orm_dest(1, "Belagavi Fort", "Fort")],
                VALID_LLM_RESPONSE,
            )
            with s, l, w:
                r = test_client.post("/api/chat", json={"message": "current entry fee"})
        finally:
            if old is not None:
                os.environ["WEB_RESEARCH_ENABLED"] = old
            else:
                os.environ.pop("WEB_RESEARCH_ENABLED", None)
            get_settings.cache_clear()

        assert r.status_code == 200
        assert r.json()["web_research_used"] is False

    def test_chat_response_json_has_all_six_keys(self, test_client):
        """Requirement: /api/chat JSON always includes all 6 keys in both web and non-web modes."""
        s, l, w = _patch_pipeline(
            [_make_orm_dest(1, "Belagavi Fort", "Fort")],
            VALID_LLM_RESPONSE,
        )
        with s, l, w:
            r = test_client.post("/api/chat", json={"message": "Tell me about Belagavi Fort"})

        assert r.status_code == 200
        body = r.json()
        required_keys = {"answer", "destinations", "sources", "retrieved_count", "web_sources", "web_research_used"}
        assert required_keys.issubset(body.keys()), f"Missing keys: {required_keys - set(body.keys())}"
        assert isinstance(body["web_sources"], list)
        assert isinstance(body["web_research_used"], bool)
