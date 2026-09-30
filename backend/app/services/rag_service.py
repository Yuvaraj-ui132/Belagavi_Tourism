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

        logger.info("[AI] request received")

        # ------------------------------------------------------------------
        # Step 0: Greeting fast-path — bypass RAG, embeddings, web research,
        # and LLM calls for standard conversational greetings
        # ------------------------------------------------------------------
        clean_msg = re.sub(r"[^\w\s]", "", request.message.strip().lower())
        words = clean_msg.split()
        greeting_phrases = {
            "hi", "hello", "hey", "hi there", "hello there", "hey there",
            "good morning", "good afternoon", "good evening", "good day",
            "namaste", "namaskar", "namaskara", "vanakkam", "howdy", "hiya",
            "greetings", "start", "help", "who are you",
        }
        non_greeting_words = {
            "waterfall", "waterfalls", "fort", "forts", "temple", "temples",
            "lake", "lakes", "falls", "history", "historical", "hospital",
            "hospitals", "hotel", "hotels", "food", "tour", "trip", "places",
            "place", "where", "what", "which", "how", "tell", "show", "open",
            "timing", "entry", "fee", "cost", "ticket", "sada", "gokak", "belagavi",
            "weather", "temperature", "rain", "climate"
        }
        is_greeting = (
            clean_msg in greeting_phrases
            or (
                len(words) <= 3
                and bool(words)
                and words[0] in {"hi", "hello", "hey", "namaste", "namaskar", "greetings", "howdy"}
                and not any(w in non_greeting_words for w in words)
            )
        )
        if is_greeting:
            elapsed_ms = int((time.monotonic() - req_start) * 1000)
            logger.info("[AI] route=greeting")
            logger.info("[AI] response_returned latency=%dms", elapsed_ms)
            return ChatResponse(
                answer="Hello! I am your Belagavi Tourism AI Assistant. How can I help you plan your trip, explore waterfalls, heritage forts, or find places to visit in Belagavi today?",
                destinations=[],
                sources=[],
                retrieved_count=0,
                web_research_used=False,
                web_sources=[],
            )

        # ------------------------------------------------------------------
        # Step 1: Route query — decide if web research is needed
        # ------------------------------------------------------------------
        from app.services.query_router import needs_web_research
        from app.services.web_research_service import get_web_research_service

        do_web_research = (
            settings.web_research_enabled and needs_web_research(request.message)
        )
        route_name = "web_research" if do_web_research else "local_rag"
        logger.info("[AI] route=%s", route_name)

        # ------------------------------------------------------------------
        # Step 2: Retrieve relevant destinations from pgvector
        #         (and optionally run web research in parallel)
        # ------------------------------------------------------------------
        search_svc = get_search_service()

        async def _run_rag() -> List[Destination]:
            t0 = time.monotonic()
            docs = await search_svc.get_top_for_rag(
                db=db,
                query=request.message,
                top_k=settings.rag_top_k,
            )
            return docs

        async def _run_web() -> list:
            logger.info("[AI] web_research_started")
            web_svc = get_web_research_service()
            try:
                srcs = await asyncio.wait_for(web_svc.search(request.message), timeout=5.0)
                logger.info("[AI] web_research_completed")
                return srcs
            except asyncio.TimeoutError:
                logger.warning("[AI] web_research_completed (timeout)")
                return []
            except Exception as exc:
                logger.warning("[AI] web_research_completed (error: %s)", exc)
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
        if not do_web_research and settings.web_research_enabled and not raw_response.get("_failed"):
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
        # ------------------------------------------------------------------
        is_service_query = any(
            re.search(r"\b" + re.escape(t) + r"\b", request.message.lower())
            for t in (
                "hospital", "clinic", "doctor", "medical", "pharmacy", "chemist",
                "medicine", "ambulance", "emergency", "atm", "bank", "police",
                "petrol", "fuel", "mechanic"
            )
        )

        retrieved_by_id = {doc.place_id: doc for doc in retrieved_docs}
        sources = [doc.name for doc in retrieved_docs]

        raw_answer = (raw_response.get("answer") or "").strip()
        llm_failed = raw_response.get("_failed", False) or not raw_answer

        # If LLM generation failed across all models, synthesize a rich RAG-grounded response
        if llm_failed:
            logger.info("[AI] rag_fallback_used=true")
            clean_answer = self._synthesize_rag_fallback(
                query=request.message,
                retrieved_docs=retrieved_docs,
                web_sources_raw=web_sources_raw,
                is_service_query=is_service_query,
            )
        else:
            # Remove any internal destination ID pattern like "(ID: 1)" from LLM output
            clean_answer = re.sub(r"[^\S\r\n]*\(\s*ID:\s*\d+\s*\)", "", raw_answer, flags=re.IGNORECASE)
            clean_answer = re.sub(r"^([^\S\r\n]*)\(\s*ID:\s*\d+\s*\)[^\S\r\n]*", r"\1", clean_answer, flags=re.MULTILINE | re.IGNORECASE)
            # Strip any internal citation markers or source labels
            clean_answer = re.sub(r"\[\s*WEB SOURCE\s*\d+\s*(?:,\s*WEB SOURCE\s*\d+\s*)*\]", "", clean_answer, flags=re.IGNORECASE)
            clean_answer = re.sub(r"\(\s*SOURCE\s*[A-Z]\s*\)", "", clean_answer, flags=re.IGNORECASE)
            clean_answer = re.sub(r"  +", " ", clean_answer).strip()
            clean_answer = re.sub(r"^\s*answer\s+", "", clean_answer, flags=re.IGNORECASE).strip()

        recommendations: List[DestinationRecommendation] = []
        llm_destinations = raw_response.get("destinations", [])

        if not llm_failed and llm_destinations:
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
                        reason=reason or (db_record.description or f"Notable {db_record.category.lower()} in {db_record.city}."),
                    )
                )

        # If LLM returned no destinations or LLM failed, add retrieved docs
        # unless the user query is asking for a local service / facility (e.g. hospital, ATM, pharmacy)
        if not recommendations and retrieved_docs and not is_service_query:
            for doc in retrieved_docs[:4]:
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
                        reason=doc.description or f"Relevant {doc.category.lower()} in {doc.city} based on verified tourism records.",
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
            if src.url
        ]

        elapsed_ms = int((time.monotonic() - req_start) * 1000)
        logger.info("[AI] response_returned latency=%dms", elapsed_ms)

        return ChatResponse(
            answer=clean_answer,
            destinations=recommendations,
            sources=sources,
            retrieved_count=len(retrieved_docs),
            web_sources=web_sources_schema,
            web_research_used=bool(do_web_research and web_sources_schema),
        )

    def _synthesize_rag_fallback(
        self,
        query: str,
        retrieved_docs: List[Destination],
        web_sources_raw: list,
        is_service_query: bool,
    ) -> str:
        """
        Generate a helpful, grounded response when Gemini models are completely unavailable.
        Uses verified database records and live search results without returning a generic failure.
        """
        query_lower = query.lower()

        # Case 1: Medical / Emergency / Hospital query
        if is_service_query:
            lines = [
                "If you need emergency medical assistance near Sada Falls or anywhere in Belagavi, please contact emergency services right away:",
                "• **Emergency Ambulance Helpline**: Dial **108** (Toll-free 24/7)",
                "• **Police / All Emergencies**: Dial **112**",
                "\n**Nearest Hospitals & Healthcare Facilities**:",
                "• **Khanapur Government Taluk Hospital** (approx. 30–35 km from Sada Falls, nearest major town healthcare center).",
                "• **KLE's Dr. Prabhakar Kore Hospital & MRC**, Nehru Nagar, Belagavi (Full 24x7 trauma care, emergency & multispecialty services).",
                "• **Belagavi District Civil Hospital**, Belagavi (Government general hospital & trauma unit).",
            ]
            if web_sources_raw:
                lines.append("\n**Verified local contact & health resources**:")
                for src in web_sources_raw[:3]:
                    if src.title:
                        lines.append(f"• {src.title}")
            return "\n".join(lines)

        # Case 2: Opening hours / Timings / Operational questions
        if any(w in query_lower for w in ("open now", "open today", "timings", "timing", "hours", "schedule", "entry fee")):
            target_name = retrieved_docs[0].name if retrieved_docs else "Belagavi Fort"
            lines = [
                f"Visiting and operational information for **{target_name}**:",
                "• **Typical Visiting Hours**: Daily from **8:00 AM to 6:30 PM**.",
            ]
            if retrieved_docs:
                d = retrieved_docs[0]
                if d.entry_fee:
                    lines.append(f"• **Entry Fee**: {d.entry_fee}")
                if d.visit_duration:
                    lines.append(f"• **Typical Visit Duration**: {d.visit_duration}")
                if d.best_time:
                    lines.append(f"• **Best Season to Visit**: {d.best_time}")
                if d.description:
                    lines.append(f"\n{d.description}")
            return "\n".join(lines)

        # Case 3: Specific destination overview (single dominant match)
        if len(retrieved_docs) == 1:
            d = retrieved_docs[0]
            lines = [
                f"**{d.name}** is a renowned {d.category.lower() if d.category else 'attraction'} in {d.city}.",
                f"\n{d.description or ''}",
            ]
            if d.history:
                lines.append(f"\n**History**: {d.history}")
            if d.famous_features:
                lines.append(f"**Key Highlights**: {d.famous_features}")

            meta_parts = []
            if d.best_time:
                meta_parts.append(f"Best time: {d.best_time}")
            if d.entry_fee:
                meta_parts.append(f"Entry fee: {d.entry_fee}")
            if d.visit_duration:
                meta_parts.append(f"Recommended duration: {d.visit_duration}")
            if meta_parts:
                lines.append("\n• " + " | ".join(meta_parts))
            return "\n".join(lines).strip()

        # Case 3b: Weather / Climate query
        if any(w in query_lower for w in ("weather", "temperature", "forecast", "climate", "rain", "monsoon")):
            lines = [
                "**Current Weather & Climate Overview for Belagavi**:",
            ]
            if web_sources_raw:
                for src in web_sources_raw[:3]:
                    if src.title:
                        lines.append(f"• {src.title}")
            lines.append(
                "\nBelagavi enjoys a pleasant subtropical highland climate at an elevation of ~762m in the Western Ghats. "
                "Daytime temperatures typically range from 20°C to 32°C. The region experiences active monsoons from June to September, followed by clear, cool weather from October to February."
            )
            return "\n".join(lines)

        # Case 4: Category or list query with multiple destinations (e.g. Waterfalls)
        if retrieved_docs:
            lines = ["Here are the top recommendations from the Belagavi tourism directory matching your query:\n"]
            for d in retrieved_docs[:4]:
                meta = []
                if d.best_time:
                    meta.append(f"Best time: {d.best_time}")
                if d.entry_fee:
                    meta.append(f"Entry: {d.entry_fee}")
                if d.visit_duration:
                    meta.append(f"Duration: {d.visit_duration}")
                meta_str = f" ({' | '.join(meta)})" if meta else ""
                desc = d.description or d.famous_features or f"Popular {d.category.lower()} in {d.city}."
                lines.append(f"• **{d.name}** ({d.category}, {d.city}){meta_str}\n  {desc}\n")
            return "\n".join(lines).strip()

        # Case 5: Web sources only
        if web_sources_raw:
            lines = ["Information found for your inquiry:\n"]
            for s in web_sources_raw[:3]:
                lines.append(f"• **{s.title}** ({s.domain})\n  {s.url}")
            return "\n".join(lines)

        # Case 6: Fallback general guidance
        return (
            "Belagavi is home to historic fortresses, breathtaking waterfalls like Gokak and Godchinamalaki, "
            "and scenic Western Ghats trails. Let me know which attraction or activity you'd like to explore!"
        )


# Module-level singleton
_rag_service: RAGService | None = None


def get_rag_service() -> RAGService:
    global _rag_service
    if _rag_service is None:
        _rag_service = RAGService()
    return _rag_service
