"""
Semantic vector search service.

Uses pgvector's cosine distance operator (<=>)  for similarity search.
pgvector stores cosine DISTANCE (0 = identical, 2 = opposite), so:
    similarity = 1 - distance

Why no vector index?
  The dataset has 31 destinations. A sequential exact scan on 31 rows takes
  microseconds. HNSW or IVFFlat indexes add overhead without any benefit at
  this scale. We will add HNSW when the row count grows past ~1,000.
  
Why cosine similarity?
  Embedding vectors from Gemini are unit-normalised (or near-unit), so cosine
  similarity is the natural metric and matches how the model was trained.
"""

from __future__ import annotations

import logging
from typing import List, Optional

from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.embeddings.embedder import get_embedding_service
from app.models.destination import Destination
from app.schemas.search import DestinationResult

logger = logging.getLogger(__name__)


class SearchService:
    """Handles semantic search against pgvector."""

    async def search(
        self,
        db: AsyncSession,
        query: str,
        limit: int = 5,
        category: Optional[str] = None,
    ) -> List[DestinationResult]:
        """
        Perform semantic similarity search.

        Steps:
        1. Embed the user query with task_type='retrieval_query'.
        2. Run pgvector cosine distance query against destinations table.
        3. Filter by similarity threshold and optional category.
        4. Return structured DestinationResult list (no LLM involved).

        Args:
            db:       Async SQLAlchemy session.
            query:    User's natural-language search query.
            limit:    Maximum number of results.
            category: Optional category filter (case-insensitive).

        Returns:
            List of DestinationResult, ordered by similarity descending.
        """
        settings = get_settings()
        embedder = get_embedding_service()

        # Step 1: Embed the query
        query_embedding = await embedder.embed_text(query, task_type="retrieval_query")
        embedding_str = f"[{','.join(str(v) for v in query_embedding)}]"

        # Step 2: Build cosine distance query
        # pgvector operator <=> = cosine distance (0 = identical, 1 = orthogonal)
        # We select all columns plus the distance score.
        distance_expr = text(
            f"embedding <=> '{embedding_str}'::vector AS distance"
        )

        stmt = (
            select(
                Destination,
                text(f"embedding <=> '{embedding_str}'::vector AS distance"),
            )
            .where(Destination.embedding.is_not(None))
            .order_by(text("distance ASC"))
            .limit(limit * 3)  # Fetch extra to allow post-filter by threshold/category
        )

        result = await db.execute(stmt)
        rows = result.all()

        # Step 3: Convert to DestinationResult, applying threshold and category filter
        threshold = settings.rag_similarity_threshold
        output: List[DestinationResult] = []

        for row in rows:
            dest: Destination = row[0]
            distance: float = float(row[1])
            similarity = 1.0 - distance

            if similarity < threshold:
                continue

            if category and dest.category:
                if dest.category.lower() != category.lower():
                    continue

            output.append(
                DestinationResult(
                    place_id=dest.place_id,
                    name=dest.name,
                    category=dest.category,
                    city=dest.city,
                    description=dest.description,
                    best_time=dest.best_time,
                    entry_fee=dest.entry_fee,
                    visit_duration=dest.visit_duration,
                    folder_name=dest.folder_name,
                    lat=dest.lat,
                    lon=dest.lon,
                    similarity=round(similarity, 4),
                )
            )

            if len(output) >= limit:
                break

        logger.info(
            "Search query=%r returned %d results (threshold=%.2f)",
            query, len(output), threshold,
        )
        return output

    async def get_top_for_rag(
        self,
        db: AsyncSession,
        query: str,
        top_k: int,
    ) -> List[Destination]:
        """
        Retrieve full Destination ORM objects for the RAG pipeline.
        Returns the raw DB objects so the RAG service can access ALL fields.

        This is separate from search() which returns a simplified DestinationResult.
        """
        embedder = get_embedding_service()
        query_embedding = await embedder.embed_text(query, task_type="retrieval_query")
        embedding_str = f"[{','.join(str(v) for v in query_embedding)}]"

        stmt = (
            select(
                Destination,
                text(f"embedding <=> '{embedding_str}'::vector AS distance"),
            )
            .where(Destination.embedding.is_not(None))
            .order_by(text("distance ASC"))
            .limit(top_k)
        )

        result = await db.execute(stmt)
        rows = result.all()

        destinations = []
        settings = get_settings()
        threshold = settings.rag_similarity_threshold

        for row in rows:
            dest: Destination = row[0]
            distance: float = float(row[1])
            similarity = 1.0 - distance
            if similarity >= threshold:
                destinations.append(dest)

        logger.info("RAG retrieval query=%r retrieved %d docs", query, len(destinations))
        return destinations


# Module-level singleton
_search_service: SearchService | None = None


def get_search_service() -> SearchService:
    global _search_service
    if _search_service is None:
        _search_service = SearchService()
    return _search_service
