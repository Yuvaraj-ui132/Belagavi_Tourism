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
        On failure or timeout, returns a safe fallback dict (does not raise).
        """
        import time
        start_time = time.monotonic()
        logger.info("[AI] LLM started (model=%s)", self._model)

        models_to_try = [self._model]
        for candidate in ["gemini-3.1-flash-lite", "gemini-3.5-flash-lite", "gemini-3.8-flash"]:
            if candidate not in models_to_try:
                models_to_try.append(candidate)

        last_exc: Exception | None = None

        for idx, model_name in enumerate(models_to_try[:2]):
            per_call_timeout = 12.0 if idx == 0 else 6.0
            call_start = time.monotonic()
            try:
                response = await asyncio.wait_for(
                    self._client.aio.models.generate_content(
                        model=model_name,
                        contents=prompt,
                    ),
                    timeout=per_call_timeout,
                )
                elapsed = round(time.monotonic() - start_time, 2)
                logger.info("[AI] LLM completed (model=%s, elapsed=%.2fs)", model_name, elapsed)
                raw_text = response.text.strip()
                return self._parse_llm_json(raw_text)

            except asyncio.TimeoutError:
                call_elapsed = round(time.monotonic() - call_start, 2)
                logger.warning(
                    "[AI] LLM timeout after %.2fs on model=%s",
                    call_elapsed,
                    model_name,
                )
                last_exc = TimeoutError(f"LLM timed out after {call_elapsed}s on {model_name}")
            except Exception as exc:
                call_elapsed = round(time.monotonic() - call_start, 2)
                last_exc = exc
                logger.warning(
                    "[AI] LLM call failed (model=%s, elapsed=%.2fs): %s",
                    model_name,
                    call_elapsed,
                    exc,
                )

        total_elapsed = round(time.monotonic() - start_time, 2)
        logger.error("[AI] LLM generation failed across models (total_elapsed=%.2fs): %s", total_elapsed, last_exc)
        return {
            "answer": (
                "The AI assistant took longer than expected to process your request. "
                "Please tap Retry or try asking again in a moment."
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
