"""
pytest configuration for the backend test suite.

Sets env vars BEFORE any app imports, and provides fixtures for clean testing
without requiring a running PostgreSQL instance.

Key approach: we patch init_db at the point it's CALLED (app.main.init_db),
not where it's defined, to prevent DB connections during TestClient startup.
"""

import os
import sys
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

# Ensure backend/ is on the path
sys.path.insert(0, str(Path(__file__).parent.parent))

# Set test environment variables BEFORE importing any app modules.
os.environ["GEMINI_API_KEY"] = "test-api-key-placeholder"
os.environ["POSTGRES_HOST"] = "localhost"
os.environ["POSTGRES_PORT"] = "5432"
os.environ["POSTGRES_DB"] = "belagavi_tourism_ai_test"
os.environ["POSTGRES_USER"] = "belagavi_user"
os.environ["POSTGRES_PASSWORD"] = "testpassword"
os.environ["GEMINI_EMBEDDING_MODEL"] = "gemini-embedding-001"
os.environ["GEMINI_LLM_MODEL"] = "gemini-3.5-flash"
os.environ["GEMINI_EMBEDDING_DIMENSION"] = "768"


def _make_test_client(extra_overrides: dict = None):
    """
    Create a TestClient that:
    1. Patches init_db (called in lifespan) so no DB connection is attempted.
    2. Overrides get_db with a mock session.
    
    Returns a context manager that yields (client, mock_session).
    """
    from contextlib import contextmanager
    from fastapi.testclient import TestClient
    from app.main import app
    from app.db.database import get_db

    mock_session = AsyncMock()
    mock_session.execute = AsyncMock(return_value=None)
    mock_session.rollback = AsyncMock()
    mock_session.close = AsyncMock()

    async def _mock_db():
        yield mock_session

    app.dependency_overrides[get_db] = _mock_db
    if extra_overrides:
        app.dependency_overrides.update(extra_overrides)

    @contextmanager
    def _ctx():
        # Patch init_db in the module where it is called from (app.main)
        with patch("app.main.init_db", new=AsyncMock(return_value=None)):
            with TestClient(app, raise_server_exceptions=False) as c:
                yield c, mock_session
        app.dependency_overrides.clear()

    return _ctx()


@pytest.fixture
def test_client():
    """Fixture: TestClient with mocked DB and no-op init_db."""
    with _make_test_client() as (client, session):
        yield client


@pytest.fixture
def test_client_with_session():
    """Fixture: TestClient + the mock session (for configuring execute results)."""
    with _make_test_client() as (client, session):
        yield client, session
