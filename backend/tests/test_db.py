"""
Tests for configuration, database layer, and embedding service setup.

These are unit tests that verify:
- Settings load correctly from environment variables.
- Model names are configurable (not hard-coded).
- Database URL is constructed correctly from components.
- Embedding service rejects empty text.
- LLM client JSON parsing handles malformed responses.
- context_builder produces expected output for known inputs.
"""

from __future__ import annotations

import pytest


# ---------------------------------------------------------------------------
# Settings tests
# ---------------------------------------------------------------------------

def test_settings_load_from_env():
    """Settings correctly reads model names from environment variables."""
    import os
    os.environ["GEMINI_EMBEDDING_MODEL"] = "gemini-embedding-001"
    os.environ["GEMINI_LLM_MODEL"] = "gemini-3.5-flash"
    os.environ["GEMINI_EMBEDDING_DIMENSION"] = "768"

    # Clear cached settings instance
    from app.config import get_settings
    get_settings.cache_clear()

    settings = get_settings()
    assert settings.gemini_embedding_model == "gemini-embedding-001"
    assert settings.gemini_llm_model == "gemini-3.5-flash"
    assert settings.gemini_embedding_dimension == 768


def test_database_url_constructed_correctly():
    """database_url property constructs the asyncpg DSN correctly from POSTGRES_*."""
    import os
    old_db_url = os.environ.get("DATABASE_URL")
    os.environ["DATABASE_URL"] = ""
    os.environ["POSTGRES_USER"] = "testuser"
    os.environ["POSTGRES_PASSWORD"] = "testpass"
    os.environ["POSTGRES_HOST"] = "localhost"
    os.environ["POSTGRES_PORT"] = "5432"
    os.environ["POSTGRES_DB"] = "testdb"

    try:
        from app.config import get_settings
        get_settings.cache_clear()

        settings = get_settings()
        url = settings.database_url
        assert "postgresql+asyncpg://" in url
        assert "testuser" in url
        assert "testpass" in url
        assert "localhost" in url
        assert "testdb" in url
    finally:
        if old_db_url is not None:
            os.environ["DATABASE_URL"] = old_db_url
        else:
            os.environ.pop("DATABASE_URL", None)
        get_settings.cache_clear()


def test_database_url_override():
    """DATABASE_URL override takes precedence over POSTGRES_* variables."""
    import os
    old_db_url = os.environ.get("DATABASE_URL")
    os.environ["DATABASE_URL"] = "postgresql://override_user:override_pass@supabase.com:5432/override_db"
    try:
        from app.config import get_settings
        get_settings.cache_clear()

        settings = get_settings()
        assert settings.database_url == "postgresql+asyncpg://override_user:override_pass@supabase.com:5432/override_db"
        assert settings.sync_database_url == "postgresql://override_user:override_pass@supabase.com:5432/override_db"
    finally:
        if old_db_url is not None:
            os.environ["DATABASE_URL"] = old_db_url
        else:
            os.environ.pop("DATABASE_URL", None)
        get_settings.cache_clear()


def test_model_names_are_not_hardcoded():
    """Verify that model names can be overridden via environment variables."""
    import os
    os.environ["GEMINI_EMBEDDING_MODEL"] = "custom-embedding-model"
    os.environ["GEMINI_LLM_MODEL"] = "custom-llm-model"

    from app.config import get_settings
    get_settings.cache_clear()

    settings = get_settings()
    assert settings.gemini_embedding_model == "custom-embedding-model"
    assert settings.gemini_llm_model == "custom-llm-model"

    # Reset to defaults for subsequent tests
    os.environ["GEMINI_EMBEDDING_MODEL"] = "gemini-embedding-001"
    os.environ["GEMINI_LLM_MODEL"] = "gemini-3.5-flash"
    get_settings.cache_clear()


# ---------------------------------------------------------------------------
# Embedding service tests (no real API calls)
# ---------------------------------------------------------------------------

def test_embedding_service_rejects_empty_text():
    """EmbeddingService.embed_text raises ValueError for empty input."""
    import asyncio
    from unittest.mock import MagicMock
    from app.embeddings.embedder import EmbeddingService

    svc = EmbeddingService.__new__(EmbeddingService)
    svc._client = MagicMock()
    svc._model = "gemini-embedding-001"
    svc._dimension = 768

    with pytest.raises((ValueError, RuntimeError)):
        asyncio.run(svc.embed_text(""))


def test_embedding_service_rejects_whitespace():
    """EmbeddingService.embed_text raises ValueError for whitespace input."""
    import asyncio
    from unittest.mock import MagicMock
    from app.embeddings.embedder import EmbeddingService

    svc = EmbeddingService.__new__(EmbeddingService)
    svc._client = MagicMock()
    svc._model = "gemini-embedding-001"
    svc._dimension = 768

    with pytest.raises((ValueError, RuntimeError)):
        asyncio.run(svc.embed_text("   "))


# ---------------------------------------------------------------------------
# LLM client JSON parsing tests (no real API calls)
# ---------------------------------------------------------------------------

def test_llm_client_parses_valid_json():
    """LLM client correctly parses well-formed JSON."""
    from app.rag.llm_client import LLMClient
    from unittest.mock import MagicMock

    client = LLMClient.__new__(LLMClient)
    client._client = MagicMock()
    client._model = "gemini-3.5-flash"

    raw = '{"answer": "Visit the fort!", "destinations": [{"place_id": 1, "name": "Belagavi Fort", "reason": "Historic"}], "sources": ["Belagavi Fort"]}'
    result = client._parse_llm_json(raw)

    assert result["answer"] == "Visit the fort!"
    assert len(result["destinations"]) == 1
    assert result["destinations"][0]["place_id"] == 1


def test_llm_client_strips_markdown_fences():
    """LLM client handles JSON wrapped in markdown code fences."""
    from app.rag.llm_client import LLMClient
    from unittest.mock import MagicMock

    client = LLMClient.__new__(LLMClient)
    client._client = MagicMock()
    client._model = "gemini-3.5-flash"

    raw = '```json\n{"answer": "Hello", "destinations": [], "sources": []}\n```'
    result = client._parse_llm_json(raw)

    assert result["answer"] == "Hello"
    assert result["destinations"] == []


def test_llm_client_handles_malformed_json():
    """Malformed LLM JSON returns safe fallback, not an exception."""
    from app.rag.llm_client import LLMClient
    from unittest.mock import MagicMock

    client = LLMClient.__new__(LLMClient)
    client._client = MagicMock()
    client._model = "gemini-3.5-flash"

    raw = "This is not valid JSON at all! { broken }"
    result = client._parse_llm_json(raw)

    # Should return a dict with answer (not raise)
    assert isinstance(result, dict)
    assert "answer" in result
    assert "destinations" in result
    assert result["destinations"] == []


# ---------------------------------------------------------------------------
# Context builder tests
# ---------------------------------------------------------------------------

def test_context_builder_empty_list():
    """build_context with empty list returns 'No relevant' message."""
    from app.rag.context_builder import build_context
    result = build_context([])
    assert "No relevant" in result


def test_context_builder_formats_destinations():
    """build_context formats destination into numbered blocks."""
    from app.rag.context_builder import build_context
    from app.models.destination import Destination

    dest = Destination()
    dest.place_id = 1
    dest.name = "Belagavi Fort"
    dest.category = "Fort"
    dest.city = "Belagavi"
    dest.description = "A historic fort"
    dest.history = "Built in 1204 AD"
    dest.architecture = "Stone masonry"
    dest.famous_features = "Ancient mosques"
    dest.best_time = "Year-round"
    dest.entry_fee = "Free"
    dest.visit_duration = "2 Hours"
    dest.how_to_reach = "By auto from CBT"
    dest.local_tips = "Carry water"
    dest.detailed_history = "Long history..."
    dest.transport_summary = "Auto: Rs.40-60"

    context = build_context([dest])

    assert "[DESTINATION 1]" in context
    assert "Belagavi Fort" in context
    assert "Free" in context  # entry_fee present
    assert "1204" in context  # historical fact present
    assert "2 Hours" in context  # visit_duration present


def test_context_builder_multiple_destinations():
    """build_context produces numbered blocks for multiple destinations."""
    from app.rag.context_builder import build_context
    from app.models.destination import Destination

    def _make(pid, name):
        d = Destination()
        d.place_id = pid
        d.name = name
        d.category = "Waterfall"
        d.city = "Gokak"
        d.description = f"A waterfall called {name}"
        d.history = "Natural"
        d.architecture = "N/A"
        d.famous_features = "Water"
        d.best_time = "Monsoon"
        d.entry_fee = "Free"
        d.visit_duration = "2 Hours"
        d.how_to_reach = "By road"
        d.local_tips = "Be careful"
        d.detailed_history = "Formed by Ghataprabha river"
        d.transport_summary = "Bus from Gokak"
        return d

    result = build_context([_make(6, "Gokak Falls"), _make(5, "Godchinamalaki Falls")])
    assert "[DESTINATION 1]" in result
    assert "[DESTINATION 2]" in result
    assert "Gokak Falls" in result
    assert "Godchinamalaki Falls" in result


# ---------------------------------------------------------------------------
# Data.js parsing test
# ---------------------------------------------------------------------------

def test_ingest_script_can_parse_data_js():
    """Ingestion script can locate and parse data.js without errors."""
    from pathlib import Path
    import sys
    sys.path.insert(0, str(Path(__file__).parent.parent))
    from scripts.ingest import load_data_js

    project_root = Path(__file__).parent.parent.parent
    data_js = project_root / "public" / "static" / "data.js"

    destinations = load_data_js(data_js)

    assert len(destinations) == 31  # exact count from our dataset
    assert all("id" in d for d in destinations)
    assert all("name" in d for d in destinations)
    assert all("category" in d for d in destinations)

    # Verify first destination
    first = next(d for d in destinations if d["id"] == 1)
    assert first["name"] == "Belagavi Fort"


def test_ingest_document_text_contains_key_fields():
    """build_document_text includes all important fields in the document."""
    from pathlib import Path
    import sys
    sys.path.insert(0, str(Path(__file__).parent.parent))
    from scripts.ingest import build_document_text, load_data_js

    project_root = Path(__file__).parent.parent.parent
    data_js = project_root / "public" / "static" / "data.js"
    destinations = load_data_js(data_js)

    fort = next(d for d in destinations if d["id"] == 1)
    doc_text = build_document_text(fort)

    assert "Belagavi Fort" in doc_text
    assert "Fort" in doc_text  # category
    assert "1204" in doc_text  # from detailed_history
    assert "Free" in doc_text  # entry_fee
    assert "2 Hours" in doc_text  # visit_duration


def test_cors_origins_parsing():
    """Verify CORS_ORIGINS parses list, comma-separated string, and JSON string."""
    import os
    from app.config import get_settings

    # Test comma-separated string
    old_cors = os.environ.get("CORS_ORIGINS")
    try:
        os.environ["CORS_ORIGINS"] = "https://app1.com, https://app2.com"
        get_settings.cache_clear()
        s = get_settings()
        assert s.cors_origins == ["https://app1.com", "https://app2.com"]

        # Test JSON string
        os.environ["CORS_ORIGINS"] = '["https://app3.com"]'
        get_settings.cache_clear()
        s = get_settings()
        assert s.cors_origins == ["https://app3.com"]

        # Test wildcard
        os.environ["CORS_ORIGINS"] = "*"
        get_settings.cache_clear()
        s = get_settings()
        assert s.cors_origins == ["*"]
    finally:
        if old_cors is not None:
            os.environ["CORS_ORIGINS"] = old_cors
        else:
            os.environ.pop("CORS_ORIGINS", None)
        get_settings.cache_clear()


def test_vercel_entrypoint_imports_app():
    """Verify that api/index.py exports a valid FastAPI application instance."""
    from fastapi import FastAPI
    from api.index import app as vercel_app

    assert isinstance(vercel_app, FastAPI)
    assert vercel_app.title == "Belagavi Tourism AI Backend"
    # Verify core routes are mounted
    route_paths = [r.path for r in vercel_app.routes]
    assert "/api/health" in route_paths
    assert "/api/search" in route_paths
    assert "/api/chat" in route_paths

