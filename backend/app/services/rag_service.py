"""
RAG service — orchestrates the full pipeline:

  User query
      -> route: does this need web research? (QueryRouter)
      -> embed query (EmbeddingService)
      -> [parallel] retrieve top-K documents (SearchService.get_top_for_rag)
      -> [parallel] web research if needed (WebResearchService)
      -> build context (context_builder.build_context + build_web_context)
      -> format prompt (SYSTEM_PROMPT_TEMPLATE or HYBRID_SYSTEM_PROMPT_TEMPLATE)
      -> call LLM (LLMClient.generate_rag_response)
      -> merge LLM output with DB records (overlay deterministic fields)
      -> return ChatResponse (with web_sources if web research ran)

Critical: after the LLM returns place_id values, we look up those IDs in the
retrieved documents (NOT back to the database) to overlay deterministic fields.
This ensures entry_fee, lat, lon, visit_duration, etc. always come from the
verified database record — never from LLM text generation.

Web research:
- Uses needs_web_research() router to decide whether to trigger.
- Supabase retrieval and web search run concurrently (asyncio.gather).
- Web search failure never crashes the pipeline — degrades to local-only.
- Web sources are included in ChatResponse.web_sources (URLs are real, not invented).
"""

from __future__ import annotations

import asyncio
import logging
import re
from typing import List

from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.models.destination import Destination
from app.rag.context_builder import SYSTEM_PROMPT_TEMPLATE, build_context
from app.rag.llm_client import get_llm_client
from app.schemas.chat import ChatRequest, ChatResponse, DestinationRecommendation
from app.services.search_service import get_search_service

logger = logging.getLogger(__name__)


class RAGService:
    """Orchestrates the full Retrieve-Augment-Generate pipeline."""

    async def chat(self, db: AsyncSession, request: ChatRequest) -> ChatResponse:
        """
        Full RAG pipeline for the /api/chat endpoint.

        Args:
            db:      Async database session.
            request: ChatRequest with user message and optional history.

        Returns:
            ChatResponse with grounded answer, destination recommendations,
            source list, and optional web_sources when web research is triggered.
        """
        import time
        req_start = time.monotonic()
        settings = get_settings()

        logger.info("[AI] request received: message=%r", request.message)

        # ------------------------------------------------------------------
        # Step 1: Route query — decide if web research is needed
        # ------------------------------------------------------------------
        from app.services.query_router import needs_web_research
        from app.services.web_research_service import get_web_research_service

        do_web_research = (
            settings.web_research_enabled and needs_web_research(request.message)
        )
        logger.info(
            "[AI] query classification: message=%r, class=%s",
            request.message,
            "web_research" if do_web_research else "local_rag",
        )

        # ------------------------------------------------------------------
        # Step 2: Retrieve relevant destinations from pgvector
        #         (and optionally run web research in parallel)
        # ------------------------------------------------------------------
        search_svc = get_search_service()

        async def _run_rag() -> List[Destination]:
            t0 = time.monotonic()
            logger.info("[AI] local RAG started")
            docs = await search_svc.get_top_for_rag(
                db=db,
                query=request.message,
                top_k=settings.rag_top_k,
            )
            elapsed = round(time.monotonic() - t0, 2)
            logger.info("[AI] local RAG completed (docs=%d, elapsed=%.2fs)", len(docs), elapsed)
            return docs

        async def _run_web() -> list:
            t0 = time.monotonic()
            logger.info("[AI] web research started")
            web_svc = get_web_research_service()
            try:
                srcs = await asyncio.wait_for(web_svc.search(request.message), timeout=5.0)
                elapsed = round(time.monotonic() - t0, 2)
                logger.info("[AI] web research completed (sources=%d, elapsed=%.2fs)", len(srcs), elapsed)
                return srcs
            except asyncio.TimeoutError:
                elapsed = round(time.monotonic() - t0, 2)
                logger.warning("[AI] web research timeout after %.2fs", elapsed)
                return []
            except Exception as exc:
                elapsed = round(time.monotonic() - t0, 2)
                logger.warning("[AI] web research failed (elapsed=%.2fs): %s", elapsed, exc)
                return []

        if do_web_research:
            results = await asyncio.gather(_run_rag(), _run_web(), return_exceptions=True)
            db_result, web_result = results[0], results[1]

            if isinstance(db_result, Exception):
                raise db_result
            retrieved_docs: List[Destination] = db_result

            if isinstance(web_result, Exception):
                logger.warning("[AI] web research raised exception: %s", web_result)
                web_sources_raw: list = []
            else:
                web_sources_raw = web_result
        else:
            retrieved_docs = await _run_rag()
            web_sources_raw = []

            # Dynamic fallback: if local knowledge base has NO relevant documents for a non-trivial query,
            # attempt a fast web research fallback so the user still gets a helpful answer.
            if not retrieved_docs and settings.web_research_enabled:
                logger.info("[AI] No local destinations matched query; attempting web research fallback")
                web_sources_raw = await _run_web()
                do_web_research = bool(web_sources_raw)

        # ------------------------------------------------------------------
        # Step 3: Build context block(s) from retrieved documents
        # ------------------------------------------------------------------
        context = build_context(retrieved_docs)

        # ------------------------------------------------------------------
        # Step 4: Construct the full LLM prompt
        # ------------------------------------------------------------------
        if do_web_research and web_sources_raw:
            from app.rag.context_builder import (
                HYBRID_SYSTEM_PROMPT_TEMPLATE,
                build_web_context,
            )
            web_context_str = build_web_context(web_sources_raw)
            system_with_context = HYBRID_SYSTEM_PROMPT_TEMPLATE.format(
                context=context,
                web_context=web_context_str,
            )
        else:
            system_with_context = SYSTEM_PROMPT_TEMPLATE.format(context=context)

        # Append conversation history if provided
        history_text = ""
        if request.history:
            history_lines = []
            for msg in request.history[-6:]:  # Last 6 turns = 3 user/assistant pairs
                prefix = "User" if msg.role == "user" else "Assistant"
                history_lines.append(f"{prefix}: {msg.content}")
            history_text = "\n".join(history_lines) + "\n\n"

        full_prompt = f"{system_with_context}\n\n{history_text}User: {request.message}"

        # ------------------------------------------------------------------
        # Step 5: Call the LLM
        # ------------------------------------------------------------------
        llm_client = get_llm_client()
        raw_response = await llm_client.generate_rag_response(full_prompt)

        # ------------------------------------------------------------------
        # Step 5b: Dynamic web fallback if local DB answer explicitly states missing info
        # ------------------------------------------------------------------
        if not do_web_research and settings.web_research_enabled:
            missing_indicators = [
                "not available in the belagavi tourism database",
                "not available in the database",
                "not listed in the database",
                "not mentioned in the database",
                "not found in the database",
                "not available in the knowledge base",
                "not listed in our records",
                "database does not list",
                "database does not contain",
                "specific daily opening and closing timings are not listed",
                "timings are not listed",
                "hours are not listed",
                "fee is not listed",
            ]
            ans_check = (raw_response.get("answer") or "").lower()
            if any(indicator in ans_check for indicator in missing_indicators):
                logger.info("[AI] Local context lacked info for %r; attempting web fallback", request.message)
                fallback_sources = await _run_web()
                if fallback_sources:
                    web_sources_raw = fallback_sources
                    do_web_research = True
                    from app.rag.context_builder import (
                        HYBRID_SYSTEM_PROMPT_TEMPLATE,
                        build_web_context,
                    )
                    web_context_str = build_web_context(web_sources_raw)
                    hybrid_system = HYBRID_SYSTEM_PROMPT_TEMPLATE.format(
                        context=context,
                        web_context=web_context_str,
                    )
                    full_prompt = f"{hybrid_system}\n\n{history_text}User: {request.message}"
                    raw_response = await llm_client.generate_rag_response(full_prompt)

        # ------------------------------------------------------------------
        # Step 6: Build structured response
        # Overlay deterministic fields from DB onto LLM-suggested destinations.
        # We match by place_id. If LLM returns an ID not in retrieved docs,
        # we skip it (prevents hallucination of non-retrieved destinations).
        # Clean any accidental internal IDs from answer and reasons.
        # ------------------------------------------------------------------
        retrieved_by_id = {doc.place_id: doc for doc in retrieved_docs}
        sources = [doc.name for doc in retrieved_docs]

        raw_answer = raw_response.get("answer", "")
        # Remove any internal destination ID pattern like "(ID: 1)" from LLM output
        clean_answer = re.sub(r"[^\S\r\n]*\(\s*ID:\s*\d+\s*\)", "", raw_answer, flags=re.IGNORECASE)
        clean_answer = re.sub(r"^([^\S\r\n]*)\(\s*ID:\s*\d+\s*\)[^\S\r\n]*", r"\1", clean_answer, flags=re.MULTILINE | re.IGNORECASE)
        # Strip any internal citation markers or source labels
        clean_answer = re.sub(r"\[\s*WEB SOURCE\s*\d+\s*(?:,\s*WEB SOURCE\s*\d+\s*)*\]", "", clean_answer, flags=re.IGNORECASE)
        clean_answer = re.sub(r"\(\s*SOURCE\s*[A-Z]\s*\)", "", clean_answer, flags=re.IGNORECASE)
        clean_answer = re.sub(r"\bSOURCE\s*[A-Z]\b", "", clean_answer, flags=re.IGNORECASE)
        clean_answer = re.sub(r"  +", " ", clean_answer).strip()

        recommendations: List[DestinationRecommendation] = []
        llm_destinations = raw_response.get("destinations", [])

        for llm_dest in llm_destinations:
            pid = llm_dest.get("place_id")
            if pid is None:
                continue

            # Only accept place_ids that actually came from our retrieved context
            db_record = retrieved_by_id.get(int(pid))
            if db_record is None:
                logger.warning(
                    "LLM suggested place_id=%s not in retrieved context — skipped.", pid
                )
                continue

            reason = llm_dest.get("reason", "")
            if reason:
                reason = re.sub(r"[^\S\r\n]*\(\s*ID:\s*\d+\s*\)", "", reason, flags=re.IGNORECASE)
                reason = re.sub(r"\[\s*WEB SOURCE\s*\d+\s*(?:,\s*WEB SOURCE\s*\d+\s*)*\]", "", reason, flags=re.IGNORECASE)
                reason = re.sub(r"\(\s*SOURCE\s*[A-Z]\s*\)", "", reason, flags=re.IGNORECASE)
                reason = re.sub(r"  +", " ", reason).strip()

            recommendations.append(
                DestinationRecommendation(
                    # Deterministic fields — from DB record, not LLM
                    place_id=db_record.place_id,
                    name=db_record.name,
                    category=db_record.category,
                    city=db_record.city,
                    entry_fee=db_record.entry_fee,
                    visit_duration=db_record.visit_duration,
                    best_time=db_record.best_time,
                    folder_name=db_record.folder_name,
                    lat=db_record.lat,
                    lon=db_record.lon,
                    # LLM-generated text field
                    reason=reason,
                )
            )

        # If LLM returned no destinations but we have retrieved docs, add them all
        # unless the user query is asking for a local service / facility (e.g. hospital, ATM, pharmacy)
        is_service_query = any(
            re.search(r"\b" + re.escape(t) + r"\b", request.message.lower())
            for t in (
                "hospital", "clinic", "doctor", "medical", "pharmacy", "chemist",
                "medicine", "ambulance", "emergency", "atm", "bank", "police",
                "petrol", "fuel", "mechanic"
            )
        )
        if not recommendations and retrieved_docs and not is_service_query:
            for doc in retrieved_docs[:3]:
                recommendations.append(
                    DestinationRecommendation(
                        place_id=doc.place_id,
                        name=doc.name,
                        category=doc.category,
                        city=doc.city,
                        entry_fee=doc.entry_fee,
                        visit_duration=doc.visit_duration,
                        best_time=doc.best_time,
                        folder_name=doc.folder_name,
                        lat=doc.lat,
                        lon=doc.lon,
                        reason="Relevant to your query based on semantic similarity.",
                    )
                )

        # ------------------------------------------------------------------
        # Step 7: Convert web_sources_raw to schema WebSource objects.
        # URLs come from actual search results — never invented by the LLM.
        # ------------------------------------------------------------------
        from app.schemas.chat import WebSource as WebSourceSchema

        web_sources_schema = [
            WebSourceSchema(
                title=src.title,
                url=src.url,
                domain=src.domain,
            )
            for src in (web_sources_raw or [])
            if src.url  # only include sources with a real URL
        ]

        total_elapsed = round(time.monotonic() - req_start, 2)
        logger.info(
            "[AI] response returned (total_elapsed=%.2fs, answer_chars=%d, destinations=%d, web_sources=%d)",
            total_elapsed,
            len(clean_answer),
            len(recommendations),
            len(web_sources_schema),
        )

        return ChatResponse(
            answer=clean_answer,
            destinations=recommendations,
            sources=sources,
            retrieved_count=len(retrieved_docs),
            web_sources=web_sources_schema,
            web_research_used=bool(do_web_research and web_sources_schema),
        )


# Module-level singleton
_rag_service: RAGService | None = None


def get_rag_service() -> RAGService:
    global _rag_service
    if _rag_service is None:
        _rag_service = RAGService()
    return _rag_service
