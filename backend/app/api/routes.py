"""
FastAPI route definitions for the AI backend.

Endpoints:
  GET  /api/health   — health check (no auth required)
  POST /api/search   — semantic destination search (public)
  POST /api/chat     — RAG tourism assistant (Firebase auth required)

Authentication:
  /api/chat requires a valid Firebase ID token in the Authorization header:
      Authorization: Bearer <firebase-id-token>
  Requests without a valid token receive HTTP 401 Unauthorized.
  The verified Firebase UID is extracted server-side — client-supplied UIDs
  are never trusted.

Error handling:
  - 400 Bad Request: invalid input (Pydantic validation errors auto-handled by FastAPI)
  - 401 Unauthorized: missing, malformed, expired, or revoked Firebase token
  - 503 Service Unavailable: database or embedding service down
  - 500 Internal Server Error: unexpected errors
"""

from __future__ import annotations

import logging

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.firebase import require_firebase_user
from app.auth.rate_limiter import check_rate_limit
from app.db.database import get_db
from app.schemas.chat import ChatRequest, ChatResponse
from app.schemas.search import SearchRequest, SearchResponse
from app.services.rag_service import get_rag_service
from app.services.search_service import get_search_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api")


# ---------------------------------------------------------------------------
# Health check
# ---------------------------------------------------------------------------

@router.get(
    "/health",
    summary="Health check",
    tags=["System"],
    response_model=dict,
)
async def health_check(db: AsyncSession = Depends(get_db)):
    """
    Returns {"status": "ok"} if the API and database are reachable.
    Returns 503 if the database is unreachable.
    """
    try:
        from sqlalchemy import text
        await db.execute(text("SELECT 1"))
    except Exception as exc:
        logger.error("Health check DB ping failed: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={"status": "error", "message": "Database unreachable"},
        )

    return {"status": "ok", "version": "1.0.0", "service": "Belagavi Tourism AI"}


# ---------------------------------------------------------------------------
# Semantic search
# ---------------------------------------------------------------------------

@router.post(
    "/search",
    summary="Semantic destination search",
    tags=["Search"],
    response_model=SearchResponse,
)
async def semantic_search(
    request: SearchRequest,
    db: AsyncSession = Depends(get_db),
) -> SearchResponse:
    """
    Accepts a natural-language query and returns semantically similar destinations.

    - Embeds the query using Gemini gemini-embedding-001 (768-dim).
    - Performs pgvector cosine similarity search.
    - Returns structured destination data — no LLM involved.

    Example queries:
    - "waterfalls near Belagavi"
    - "quiet historical places"
    - "family-friendly destinations"
    - "places for photography"
    """
    search_svc = get_search_service()

    try:
        results = await search_svc.search(
            db=db,
            query=request.query,
            limit=request.limit,
            category=request.category,
        )
    except RuntimeError as exc:
        # Embedding or DB failure
        logger.error("Search failed: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"Search service unavailable: {exc}",
        )
    except Exception as exc:
        logger.exception("Unexpected error in /api/search")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="An unexpected error occurred",
        )

    return SearchResponse(
        query=request.query,
        total=len(results),
        results=results,
    )


# ---------------------------------------------------------------------------
# RAG Chat
# ---------------------------------------------------------------------------

@router.post(
    "/chat",
    summary="RAG-powered tourism assistant",
    tags=["Chat"],
    response_model=ChatResponse,
)
async def rag_chat(
    request: ChatRequest,
    db: AsyncSession = Depends(get_db),
    uid: str = Depends(require_firebase_user),
) -> ChatResponse:
    """
    RAG-powered tourism assistant — requires Firebase Authentication.

    Authentication:
        Send a valid Firebase ID token in the Authorization header:
            Authorization: Bearer <firebase-id-token>
        Missing or invalid tokens receive HTTP 401 Unauthorized.
        The uid is verified server-side from the token — never trusted from the client.

    Full pipeline:
    1. Verify Firebase ID token → extract verified uid.
    2. Embed user message (Gemini gemini-embedding-001).
    3. Retrieve top-K relevant destinations from pgvector.
    4. Build context from retrieved documents.
    5. Call Gemini LLM with grounding context.
    6. Parse structured JSON response.
    7. Overlay deterministic fields (entry_fee, lat, lon) from DB.
    8. Return ChatResponse.

    The LLM is explicitly instructed NOT to invent facts not present in the
    retrieved context. All factual fields in the response come from the database.
    """
    logger.info("Chat request from authenticated user (uid length=%d)", len(uid))
    await check_rate_limit(uid=uid, db=db)
    rag_svc = get_rag_service()

    try:
        response = await rag_svc.chat(db=db, request=request)
    except RuntimeError as exc:
        logger.error("RAG chat failed for authenticated user: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"AI service unavailable: {exc}",
        )
    except Exception as exc:
        logger.exception("Unexpected error in /api/chat")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="An unexpected error occurred",
        )

    return response
