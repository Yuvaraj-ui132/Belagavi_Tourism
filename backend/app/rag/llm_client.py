"""
LLM client using Google Gemini (gemini-3.5-flash via google-genai SDK).

Key facts (verified September 2026):
- Other preview/experimental flash variants may return 503 under load.
- gemini-3.5-flash: verified working (may need 1-3 retries during high-demand spikes).
- SDK: google-genai (NOT google-generativeai).
- Response parsing: we expect structured JSON from the LLM (enforced by system prompt).
  If parsing fails, we return a safe error response instead of crashing.

The LLM generates ONLY free-text fields (answer, reason).
Deterministic fields (place_id, entry_fee, lat, lon) come from the database.
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
from typing import Any, Dict, List, Optional

from google import genai

from app.config import get_settings

logger = logging.getLogger(__name__)


class LLMClient:
    """Wraps Gemini generative model for RAG responses."""

    def __init__(self) -> None:
        settings = get_settings()
        self._client = genai.Client(api_key=settings.gemini_api_key)
        self._model = settings.gemini_llm_model
        logger.info("LLMClient initialised: model=%s", self._model)

    async def generate_rag_response(self, prompt: str) -> Dict[str, Any]:
        """
        Call the LLM with the RAG-augmented prompt and parse the JSON response.

        Returns a dict with keys: answer, destinations, sources.
        On failure, returns a safe fallback dict (does not raise).

        Retry policy: up to 3 attempts with exponential back-off (2 s, 4 s) when
        a 503 UNAVAILABLE is returned.  This handles transient demand spikes on
        Gemini models without changing the public interface or raising to callers.
        """
        max_attempts = 3
        last_exc: Exception | None = None

        for attempt in range(1, max_attempts + 1):
            try:
                response = self._client.models.generate_content(
                    model=self._model,
                    contents=prompt,
                )
                raw_text = response.text.strip()
                if attempt > 1:
                    logger.info("LLM succeeded on attempt %d", attempt)
                return self._parse_llm_json(raw_text)

            except Exception as exc:
                last_exc = exc
                exc_str = str(exc)

                # Identify if this is a transient 503 / UNAVAILABLE error
                status_code = getattr(exc, "code", None) or getattr(exc, "status_code", None)
                is_503 = status_code == 503 or "503" in exc_str or "UNAVAILABLE" in exc_str
                is_4xx = (
                    (isinstance(status_code, int) and 400 <= status_code < 500)
                    or any(code in exc_str for code in ["400", "401", "403", "404", "422", "INVALID_ARGUMENT", "PERMISSION_DENIED"])
                )

                if is_503 and not is_4xx and attempt < max_attempts:
                    wait = 2 ** (attempt - 1) * 2  # 2 s, 4 s
                    logger.warning(
                        "LLM transient 503/UNAVAILABLE on attempt %d/%d — retrying in %ds: %s",
                        attempt, max_attempts, wait, exc_str[:100],
                    )
                    await asyncio.sleep(wait)
                else:
                    # Non-retryable error (e.g. 4xx/config) or final attempt exhausted
                    break

        logger.error("LLM generation failed after %d attempts: %s", max_attempts, last_exc)
        return {
            "answer": (
                "I'm sorry, I'm unable to process your request at the moment. "
                "Please try again later."
            ),
            "destinations": [],
            "sources": [],
            "_error": str(last_exc),
        }

    def _parse_llm_json(self, raw: str) -> Dict[str, Any]:
        """
        Parse the LLM JSON response.

        Handles:
        - Clean JSON
        - JSON wrapped in markdown fences (```json ... ```)
        - Malformed JSON (returns safe fallback)
        """
        # Strip markdown fences if present
        cleaned = re.sub(r"^```(?:json)?\s*", "", raw, flags=re.MULTILINE)
        cleaned = re.sub(r"```\s*$", "", cleaned, flags=re.MULTILINE).strip()

        try:
            data = json.loads(cleaned)
        except json.JSONDecodeError as exc:
            logger.warning("Failed to parse LLM JSON response: %s\nRaw: %r", exc, raw[:500])
            # Return safe fallback with the raw text as the answer
            return {
                "answer": _extract_plain_text(raw),
                "destinations": [],
                "sources": [],
                "_parse_error": str(exc),
            }

        # Validate expected keys exist; fill defaults if missing
        return {
            "answer": data.get("answer", ""),
            "destinations": data.get("destinations", []),
            "sources": data.get("sources", []),
        }


def _extract_plain_text(raw: str) -> str:
    """Extract readable text from a partially broken LLM response."""
    # Remove JSON-like syntax and return whatever plain text is there
    text = re.sub(r'[{}\[\]":]', " ", raw)
    text = re.sub(r"\s+", " ", text).strip()
    return text[:1000] if text else "Unable to generate a response."


# Module-level singleton
_llm_client: LLMClient | None = None


def get_llm_client() -> LLMClient:
    global _llm_client
    if _llm_client is None:
        _llm_client = LLMClient()
    return _llm_client
