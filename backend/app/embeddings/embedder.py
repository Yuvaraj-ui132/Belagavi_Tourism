"""
Embedding service using Google Gemini gemini-embedding-001.

Key facts (verified September 2026):
- text-embedding-004 was DEPRECATED January 14, 2026.
- gemini-embedding-001 is the current replacement.
- Default output: 3072 dimensions.
- We request 768 dimensions via output_dimensionality (MRL-supported).
- SDK: google-genai (new unified SDK), NOT google-generativeai.

The model name and dimension are read from Settings — never hard-coded here.
"""

from __future__ import annotations

import logging
from typing import List

from google import genai
from google.genai import types

from app.config import get_settings

logger = logging.getLogger(__name__)


class EmbeddingService:
    """
    Wraps the Google GenAI embedding API.
    One instance is created at startup and reused for the lifetime of the process.
    """

    def __init__(self) -> None:
        settings = get_settings()
        self._client = genai.Client(api_key=settings.gemini_api_key)
        self._model = settings.gemini_embedding_model
        self._dimension = settings.gemini_embedding_dimension
        logger.info(
            "EmbeddingService initialised: model=%s, dimension=%d",
            self._model,
            self._dimension,
        )

    async def embed_text(self, text: str, task_type: str = "retrieval_query") -> List[float]:
        """
        Generate an embedding for a single text string.

        Args:
            text:      The text to embed.
            task_type: Gemini task type hint.
                       Use 'retrieval_document' when indexing corpus documents.
                       Use 'retrieval_query' when embedding a user search query.

        Returns:
            List of floats of length self._dimension (768 by default).

        Raises:
            RuntimeError: if the API call fails or returns an unexpected response.
        """
        if not text or not text.strip():
            raise ValueError("Cannot embed empty text")

        try:
            # google-genai SDK call (2026 API)
            response = self._client.models.embed_content(
                model=self._model,
                contents=text,
                config=types.EmbedContentConfig(
                    task_type=task_type,
                    output_dimensionality=self._dimension,
                ),
            )
            embedding: List[float] = response.embeddings[0].values
            if len(embedding) != self._dimension:
                raise RuntimeError(
                    f"Embedding dimension mismatch: expected {self._dimension}, "
                    f"got {len(embedding)}"
                )
            return embedding

        except Exception as exc:
            logger.error("Embedding generation failed: %s", exc)
            raise RuntimeError(f"Embedding generation failed: {exc}") from exc

    async def embed_documents(self, texts: List[str]) -> List[List[float]]:
        """
        Embed multiple documents. Each document is embedded individually
        with task_type='retrieval_document'.

        Note: The Gemini embedding API does not currently support true batch requests
        in a single call, so we call embed_text in a loop. For 31 destinations this
        is fast enough (< 2 seconds total typically).
        """
        embeddings = []
        for i, text in enumerate(texts):
            logger.debug("Embedding document %d/%d", i + 1, len(texts))
            emb = await self.embed_text(text, task_type="retrieval_document")
            embeddings.append(emb)
        return embeddings


# Module-level singleton — instantiated once when first imported.
_embedding_service: EmbeddingService | None = None


def get_embedding_service() -> EmbeddingService:
    """Return the singleton EmbeddingService, creating it if needed."""
    global _embedding_service
    if _embedding_service is None:
        _embedding_service = EmbeddingService()
    return _embedding_service
