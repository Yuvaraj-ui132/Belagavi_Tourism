"""
Web research service — retrieves current information from the internet.

TWO-LAYER ARCHITECTURE (revised):
==================================

LAYER 1 — Tavily Search API (PRIMARY)
  - Activated when TAVILY_API_KEY is present in environment variables.
  - Returns clean, LLM-ready snippets with real URLs and titles.
  - 1,000 free credits/month (no credit card required, sign up at tavily.com).
  - RAG-optimised: scrapes, cleans, and returns structured text ready for LLMs.
  - If TAVILY_API_KEY is absent or the call fails, falls through to Layer 2.

LAYER 2 — Gemini Grounding with Google Search (SECONDARY / FALLBACK)
  - Uses the existing google-genai SDK (same GEMINI_API_KEY — no extra credentials).
  - Passes types.Tool(google_search=types.GoogleSearch()) to Gemini.
  - Parses groundingMetadata.grounding_chunks for real source URLs.
  - NOTE: Requires billing enabled on the Gemini API project for grounding to work.
    On the free tier this returns 429 RESOURCE_EXHAUSTED. Caught and logged as a
    warning — never crashes the pipeline.

DESIGN RULES (unchanged):
- Never crash if web search is unavailable.
- Never invent URLs.
- Limit results to WEB_RESULT_LIMIT (5) per query.
- No recursive searches, no autonomous browsing loops.
- All credentials read from environment variables only — never hardcoded.
- TAVILY_API_KEY absent → web research is skipped; Supabase RAG answers alone.
"""

from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from typing import List

from app.config import get_settings

logger = logging.getLogger(__name__)

# Maximum web search results to pass into the LLM context
WEB_RESULT_LIMIT = 5

# Maximum snippet length per result (characters) to keep prompts reasonable
SNIPPET_MAX_CHARS = 400

# Sub-query template — keeps searches focused on Belagavi tourism
_SEARCH_QUERY_TEMPLATE = "Belagavi Karnataka India tourism: {query}"


def format_focused_search_query(query: str) -> str:
    """
    Format the user query for web search engines.
    For local service / emergency queries (hospital, ATM, pharmacy, police, etc.),
    avoid adding 'tourism:' so search engines prioritize real local facilities.
    """
    import re
    q_lower = query.lower()
    service_terms = (
        "hospital", "hospitals", "clinic", "clinics", "doctor", "doctors",
        "medical", "pharmacy", "pharmacies", "chemist", "chemists", "medicine",
        "medicines", "first aid", "emergency", "ambulance", "atm", "atms",
        "bank", "banks", "police", "petrol", "fuel", "diesel", "mechanic", "garage"
    )
    if any(re.search(r"\b" + re.escape(t) + r"\b", q_lower) for t in service_terms):
        return f"{query} Belagavi Karnataka India"
    return _SEARCH_QUERY_TEMPLATE.format(query=query)


# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------

@dataclass
class WebSource:
    """A single web search result with citation information."""
    title: str
    url: str
    domain: str
    snippet: str = ""

    @classmethod
    def from_dict(cls, data: dict) -> "WebSource":
        url = data.get("url", "")
        domain = _extract_domain(url)
        return cls(
            title=data.get("title", ""),
            url=url,
            domain=domain,
            snippet=data.get("snippet", "")[:SNIPPET_MAX_CHARS],
        )


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _extract_domain(url: str) -> str:
    """Extract the bare domain from a URL string."""
    if not url:
        return ""
    try:
        url = url.split("//")[-1]    # strip scheme
        domain = url.split("/")[0]   # strip path
        domain = domain.split("?")[0]  # strip query
        return domain.lower()
    except Exception:
        return ""


def _deduplicate_sources(sources: List[WebSource]) -> List[WebSource]:
    """Remove duplicate sources by URL, preserving insertion order."""
    seen_urls: set[str] = set()
    deduped: List[WebSource] = []
    for s in sources:
        if s.url and s.url not in seen_urls:
            seen_urls.add(s.url)
            deduped.append(s)
    return deduped


# ---------------------------------------------------------------------------
# Service
# ---------------------------------------------------------------------------

class WebResearchService:
    """
    Retrieves current web information using Tavily (primary) and
    Gemini Grounding (secondary fallback).

    Safe by design:
    - Returns [] on any error (never raises to callers).
    - Skips gracefully when TAVILY_API_KEY is not configured.
    - Limits results to WEB_RESULT_LIMIT.
    """

    # -----------------------------------------------------------------------
    # Public interface
    # -----------------------------------------------------------------------

    async def search(self, query: str) -> List[WebSource]:
        """
        Search for current information about the query.

        Priority:
          1. Tavily (if TAVILY_API_KEY is set)
          2. Gemini Grounding (only if Tavily produced 0 sources and billing-enabled)

        Returns:
            List[WebSource] with real URLs. Empty list when unavailable.
            Never raises — all errors are caught and logged.
        """
        settings = get_settings()
        sources: List[WebSource] = []

        # ----------------------------------------------------------------
        # Layer 1 — Tavily (PRIMARY)
        # ----------------------------------------------------------------
        tavily_key = settings.tavily_api_key
        if tavily_key:
            try:
                tavily_sources = await asyncio.wait_for(
                    self._search_via_tavily(query, tavily_key, max_results=WEB_RESULT_LIMIT),
                    timeout=5.0,
                )
                sources.extend(tavily_sources)
                if tavily_sources:
                    logger.info(
                        "Tavily returned %d sources for query=%r",
                        len(tavily_sources), query,
                    )
            except asyncio.TimeoutError:
                logger.warning("Tavily search timed out after 5.0s for query=%r", query)
            except Exception as exc:
                logger.warning("Tavily search failed: %s", exc)
        else:
            logger.info(
                "TAVILY_API_KEY not set — skipping Tavily web research for query=%r",
                query,
            )

        # ----------------------------------------------------------------
        # Layer 2 — Gemini Grounding (SECONDARY / FALLBACK)
        # Runs if Tavily produced fewer than WEB_RESULT_LIMIT sources.
        # ----------------------------------------------------------------
        if len(sources) < WEB_RESULT_LIMIT:
            try:
                gemini_sources = await asyncio.wait_for(
                    self._search_via_gemini_grounding(query, settings),
                    timeout=4.0,
                )
                sources.extend(gemini_sources)
                if gemini_sources:
                    logger.info(
                        "Gemini grounding returned %d sources for query=%r",
                        len(gemini_sources), query,
                    )
            except asyncio.TimeoutError:
                logger.info("Gemini grounding timed out after 4.0s for query=%r", query)
            except Exception as exc:
                # 429 RESOURCE_EXHAUSTED is the expected error on free-tier accounts.
                logger.info("Gemini grounding unavailable (likely free-tier): %s", exc)

        # Deduplicate and cap
        sources = _deduplicate_sources(sources)[:WEB_RESULT_LIMIT]

        if not sources:
            logger.info(
                "No web sources found for query=%r — "
                "Supabase RAG will answer without current web context.",
                query,
            )

        return sources

    # -----------------------------------------------------------------------
    # Layer 1: Tavily Search API
    # -----------------------------------------------------------------------

    async def _search_via_tavily(
        self, query: str, api_key: str, max_results: int = WEB_RESULT_LIMIT
    ) -> List[WebSource]:
        """
        Use Tavily Search API to retrieve current information.

        Tavily is RAG-optimised: it returns clean, deduplicated text snippets
        and real source URLs without requiring HTML scraping or parsing.

        API key is read from TAVILY_API_KEY environment variable only.
        Free tier: 1,000 queries/month. Sign up at https://tavily.com.

        Args:
            query:       The user's original question.
            api_key:     TAVILY_API_KEY value (passed in, never hardcoded here).
            max_results: Maximum number of results to request.

        Returns:
            List[WebSource] with real titles, URLs, domains, and snippets.
        """
        focused_query = format_focused_search_query(query)
        results_data = None

        try:
            from tavily import TavilyClient  # type: ignore[import]
            loop = asyncio.get_event_loop()
            results_data = await asyncio.wait_for(
                loop.run_in_executor(
                    None,
                    lambda: TavilyClient(api_key=api_key).search(
                        query=focused_query,
                        search_depth="basic",
                        max_results=max_results,
                        include_answer=False,
                    ),
                ),
                timeout=5.0,
            )
        except (ImportError, Exception):
            # Direct REST fallback using httpx with 5s timeout
            try:
                import httpx
                async with httpx.AsyncClient(timeout=5.0) as client:
                    resp = await client.post(
                        "https://api.tavily.com/search",
                        json={
                            "api_key": api_key,
                            "query": focused_query,
                            "search_depth": "basic",
                            "max_results": max_results,
                            "include_answer": False,
                        },
                    )
                    if resp.status_code == 200:
                        results_data = resp.json()
                    else:
                        logger.warning("Tavily REST API returned HTTP %s", resp.status_code)
                        return []
            except Exception as rest_exc:
                logger.error("Tavily REST fallback failed: %s", rest_exc)
                return []

        sources: List[WebSource] = []
        raw_results = (
            results_data.get("results", [])
            if isinstance(results_data, dict)
            else []
        )

        for item in raw_results[:max_results]:
            url = item.get("url", "")
            if not url:
                continue
            snippet = (item.get("content", "") or "")[:SNIPPET_MAX_CHARS]
            sources.append(
                WebSource(
                    title=item.get("title", ""),
                    url=url,
                    domain=_extract_domain(url),
                    snippet=snippet,
                )
            )

        return sources

    # -----------------------------------------------------------------------
    # Layer 2: Gemini Grounding with Google Search (secondary / fallback)
    # -----------------------------------------------------------------------

    async def _search_via_gemini_grounding(
        self, query: str, settings
    ) -> List[WebSource]:
        """
        Use Gemini's built-in Google Search grounding to retrieve current info.

        This is the secondary layer. It is attempted only when Tavily did not
        return enough results. On free-tier Gemini API keys this will return
        429 RESOURCE_EXHAUSTED — that error is caught by the caller (search())
        and logged, never propagated.

        Returns:
            List[WebSource] with real URLs from groundingMetadata.grounding_chunks.
        """
        from google import genai
        from google.genai import types

        client = genai.Client(api_key=settings.gemini_api_key)
        model = settings.gemini_llm_model

        grounding_prompt = format_focused_search_query(query)

        google_search_tool = types.Tool(
            google_search=types.GoogleSearch()
        )
        config = types.GenerateContentConfig(
            tools=[google_search_tool],
            max_output_tokens=512,
        )

        loop = asyncio.get_event_loop()
        response = await loop.run_in_executor(
            None,
            lambda: client.models.generate_content(
                model=model,
                contents=grounding_prompt,
                config=config,
            ),
        )

        return self._extract_grounding_sources(response)

    def _extract_grounding_sources(self, response) -> List[WebSource]:
        """
        Parse groundingMetadata from a Gemini response to extract web sources.

        Structure (google-genai SDK):
          response.candidates[0].grounding_metadata
            .grounding_chunks[i]
              .web.uri   — the source URL
              .web.title — the page title

        Falls back gracefully if the structure is different or absent.
        """
        sources: List[WebSource] = []

        try:
            candidates = getattr(response, "candidates", None) or []
            for candidate in candidates:
                gm = getattr(candidate, "grounding_metadata", None)
                if not gm:
                    continue

                chunks = getattr(gm, "grounding_chunks", None) or []
                for chunk in chunks:
                    web = getattr(chunk, "web", None)
                    if not web:
                        continue

                    uri = getattr(web, "uri", "") or ""
                    title = getattr(web, "title", "") or ""

                    if not uri:
                        continue

                    sources.append(
                        WebSource(
                            title=title,
                            url=uri,
                            domain=_extract_domain(uri),
                            snippet="",  # grounding chunks do not carry snippets
                        )
                    )

                    if len(sources) >= WEB_RESULT_LIMIT:
                        break

                if len(sources) >= WEB_RESULT_LIMIT:
                    break

        except Exception as exc:
            logger.warning("Failed to parse grounding metadata: %s", exc)

        return sources


# ---------------------------------------------------------------------------
# Module-level singleton
# ---------------------------------------------------------------------------

_web_research_service: WebResearchService | None = None


def get_web_research_service() -> WebResearchService:
    """Return the singleton WebResearchService."""
    global _web_research_service
    if _web_research_service is None:
        _web_research_service = WebResearchService()
    return _web_research_service
