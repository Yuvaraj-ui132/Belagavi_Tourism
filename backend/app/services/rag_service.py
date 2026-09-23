"""
RAG service — orchestrates the full pipeline:

  User query
      -> embed query (EmbeddingService)
      -> retrieve top-K documents (SearchService.get_top_for_rag)
      -> build context (context_builder.build_context)
      -> format prompt (SYSTEM_PROMPT_TEMPLATE)
      -> call LLM (LLMClient.generate_rag_response)
      -> merge LLM output with DB records (overlay deterministic fields)
      -> return ChatResponse

Critical: after the LLM returns place_id values, we look up those IDs in the
retrieved documents (NOT back to the database) to overlay deterministic fields.
This ensures entry_fee, lat, lon, visit_duration, etc. always come from the
verified database record — never from LLM text generation.
"""

from __future__ import annotations

import logging
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
            and source list.
        """
        settings = get_settings()

        # ------------------------------------------------------------------
        # Step 1: Retrieve relevant destinations from pgvector
        # ------------------------------------------------------------------
        search_svc = get_search_service()
        retrieved_docs: List[Destination] = await search_svc.get_top_for_rag(
            db=db,
            query=request.message,
            top_k=settings.rag_top_k,
        )

        logger.info(
            "RAG retrieved %d documents for query=%r",
            len(retrieved_docs),
            request.message,
        )

        # ------------------------------------------------------------------
        # Step 2: Build context block from retrieved documents
        # ------------------------------------------------------------------
        context = build_context(retrieved_docs)

        # ------------------------------------------------------------------
        # Step 3: Construct the full LLM prompt
        # ------------------------------------------------------------------
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
        # Step 4: Call the LLM
        # ------------------------------------------------------------------
        llm_client = get_llm_client()
        raw_response = await llm_client.generate_rag_response(full_prompt)

        # ------------------------------------------------------------------
        # Step 5: Build structured response
        # Overlay deterministic fields from DB onto LLM-suggested destinations.
        # We match by place_id. If LLM returns an ID not in retrieved docs,
        # we skip it (prevents hallucination of non-retrieved destinations).
        # ------------------------------------------------------------------
        retrieved_by_id = {doc.place_id: doc for doc in retrieved_docs}
        sources = [doc.name for doc in retrieved_docs]

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
                    reason=llm_dest.get("reason", ""),
                )
            )

        # If LLM returned no destinations but we have retrieved docs, add them all
        if not recommendations and retrieved_docs:
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

        return ChatResponse(
            answer=raw_response.get("answer", ""),
            destinations=recommendations,
            sources=sources,
            retrieved_count=len(retrieved_docs),
        )


# Module-level singleton
_rag_service: RAGService | None = None


def get_rag_service() -> RAGService:
    global _rag_service
    if _rag_service is None:
        _rag_service = RAGService()
    return _rag_service
