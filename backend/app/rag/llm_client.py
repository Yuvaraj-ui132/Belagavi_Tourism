"""
LLM client using Google Gemini via google-genai SDK.

Verified models (September 2026):
- gemini-flash-lite-latest (fastest, lowest latency ~0.8-1.3s)
- gemini-3.5-flash-lite    (fast, high quality ~1.5-2.3s)
- gemini-3.1-flash-lite    (reliable fallback ~2-4s)
- gemini-3.6-flash         (standard flash fallback ~4-5s)

Note on excluded models:
- gemini-2.5-* : 404 NOT_FOUND (retired)
- gemini-3.7-flash : 503 UNAVAILABLE (intermittently overloaded)
- gemini-3.8-flash, gemini-3.5-flash : high timeout rates on small instances

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

# Models verified against Google GenAI API to return 200 OK within reasonable latency
VERIFIED_MODELS: List[str] = [
    "gemini-flash-lite-latest",
    "gemini-3.5-flash-lite",
    "gemini-3.1-flash-lite",
    "gemini-3.6-flash",
]


class LLMClient:
    """Wraps Gemini generative model for RAG responses with robust fallback chaining."""

    def __init__(self) -> None:
        settings = get_settings()
        self._client = genai.Client(api_key=settings.gemini_api_key)
        self._model = settings.gemini_llm_model
        logger.info("LLMClient initialised: primary_model=%s", self._model)

    async def generate_rag_response(self, prompt: str) -> Dict[str, Any]:
        """
        Call the LLM with the RAG-augmented prompt and parse the JSON response.

        Tries the primary model followed by verified fallbacks.
        If all LLM calls fail, returns a structured failure signal so RAGService
        can synthesize a high-quality RAG-grounded response instead of a dead-end message.
        """
        import time
        start_time = time.monotonic()

        # Build fallback model list
        models_to_try: List[str] = []
        if self._model and self._model in VERIFIED_MODELS:
            models_to_try.append(self._model)
        for m in VERIFIED_MODELS:
            if m not in models_to_try:
                models_to_try.append(m)

        last_exc: Exception | None = None
        last_error_type: str = "unknown"

        # Adaptive per-attempt timeouts
        timeouts = [5.0, 4.0, 3.5, 3.0]

        for idx, model_name in enumerate(models_to_try):
            per_call_timeout = timeouts[idx] if idx < len(timeouts) else 3.0
            call_start = time.monotonic()
            try:
                logger.info(
                    "[AI] LLM attempt %d/%d (model=%s, timeout=%.1fs)",
                    idx + 1,
                    len(models_to_try),
                    model_name,
                    per_call_timeout,
                )
                response = await asyncio.wait_for(
                    self._client.aio.models.generate_content(
                        model=model_name,
                        contents=prompt,
                    ),
                    timeout=per_call_timeout,
                )
                elapsed = round(time.monotonic() - call_start, 2)
                raw_text = (response.text or "").strip()
                parsed = self._parse_llm_json(raw_text)
                if parsed.get("answer"):
                    total_elapsed = round(time.monotonic() - start_time, 2)
                    logger.info(
                        "[AI] LLM success (model=%s, attempt_elapsed=%.2fs, total_elapsed=%.2fs)",
                        model_name,
                        elapsed,
                        total_elapsed,
                    )
                    parsed["_model_used"] = model_name
                    return parsed
                else:
                    logger.warning(
                        "[AI] LLM returned empty answer (model=%s, elapsed=%.2fs)",
                        model_name,
                        elapsed,
                    )
            except asyncio.TimeoutError:
                call_elapsed = round(time.monotonic() - call_start, 2)
                last_error_type = "timeout"
                last_exc = TimeoutError(f"LLM timed out after {call_elapsed}s on {model_name}")
                logger.warning(
                    "[AI] LLM timeout after %.2fs on model=%s -> failing over to next model",
                    call_elapsed,
                    model_name,
                )
            except Exception as exc:
                call_elapsed = round(time.monotonic() - call_start, 2)
                last_exc = exc
                exc_str = str(exc)
                if "429" in exc_str:
                    last_error_type = "429_quota"
                elif "503" in exc_str:
                    last_error_type = "503_unavailable"
                elif "404" in exc_str:
                    last_error_type = "404_not_found"
                else:
                    last_error_type = type(exc).__name__

                logger.warning(
                    "[AI] LLM call failed (model=%s, error_type=%s, elapsed=%.2fs): %s -> failing over to next model",
                    model_name,
                    last_error_type,
                    call_elapsed,
                    exc,
                )

        total_elapsed = round(time.monotonic() - start_time, 2)
        logger.error(
            "[AI] LLM generation failed across all %d verified models (total_elapsed=%.2fs, last_error=%s): %s",
            len(models_to_try),
            total_elapsed,
            last_error_type,
            last_exc,
        )

        # Return a structured failure dict so RAGService can generate a useful RAG-grounded response
        return {
            "answer": None,
            "destinations": [],
            "sources": [],
            "_failed": True,
            "_error_type": last_error_type,
            "_error": str(last_exc),
        }

    def _parse_llm_json(self, raw: str) -> Dict[str, Any]:
        """
        Parse the LLM JSON response.

        Handles:
        - Clean JSON
        - JSON wrapped in markdown fences (```json ... ```)
        - Malformed JSON (returns extracted plain text)
        """
        if not raw:
            return {"answer": "", "destinations": [], "sources": []}

        # Strip markdown fences if present
        cleaned = re.sub(r"^```(?:json)?\s*", "", raw, flags=re.MULTILINE)
        cleaned = re.sub(r"```\s*$", "", cleaned, flags=re.MULTILINE).strip()

        # Extract json object if surrounded by extra commentary
        json_match = re.search(r"(\{[\s\S]*\})", cleaned)
        json_str = json_match.group(1) if json_match else cleaned

        try:
            data = json.loads(json_str)
        except json.JSONDecodeError as exc:
            logger.warning("Failed to parse LLM JSON response: %s\nRaw: %r", exc, raw[:500])
            return {
                "answer": _extract_plain_text(raw),
                "destinations": [],
                "sources": [],
                "_parse_error": str(exc),
            }

        # Validate expected keys exist; fill defaults if missing
        return {
            "answer": data.get("answer", "") or _extract_plain_text(raw),
            "destinations": data.get("destinations", []) or [],
            "sources": data.get("sources", []) or [],
        }


def _extract_plain_text(raw: str) -> str:
    """Extract readable text from a partially broken LLM response."""
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
