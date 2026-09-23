"""
Tests for GET /api/health endpoint.

Tested scenarios:
- Health check returns 200 with status "ok" when DB is reachable.
- Health check returns 503 when database is unreachable.
"""

from __future__ import annotations

from unittest.mock import AsyncMock, patch

import pytest


def test_health_check_ok(test_client):
    """Health endpoint returns 200 + status ok when DB responds."""
    response = test_client.get("/api/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert "version" in body
    assert "service" in body


def test_health_check_db_down():
    """Health endpoint returns 503 when database is unreachable."""
    from app.main import app
    from app.db.database import get_db

    async def _broken_db():
        session = AsyncMock()
        session.execute = AsyncMock(side_effect=Exception("Connection refused"))
        yield session

    app.dependency_overrides[get_db] = _broken_db

    from fastapi.testclient import TestClient
    with patch("app.main.init_db", new=AsyncMock(return_value=None)):
        with TestClient(app, raise_server_exceptions=False) as client:
            response = client.get("/api/health")

    app.dependency_overrides.clear()

    assert response.status_code == 503
    body = response.json()
    assert body["detail"]["status"] == "error"
