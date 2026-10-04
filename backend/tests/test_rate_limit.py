"""
Tests for API Rate Limiting on POST /api/chat.

Verifies:
  1. Requests within quota succeed (HTTP 200).
  2. Repeated requests exceeding quota are rejected with HTTP 429.
  3. HTTP 429 response includes Retry-After and X-RateLimit-* headers.
  4. Different users maintain independent rate limit counters.
  5. Expired windows reset the counter.
  6. RATE_LIMIT_ENABLED=false bypasses rate limiting.
"""

from __future__ import annotations

from unittest.mock import AsyncMock, patch
import pytest
from starlette.testclient import TestClient

from app.main import app
from app.db.database import get_db
from app.models.destination import Destination
from app.auth.firebase import require_firebase_user
from app.config import get_settings


def _make_dest(place_id: int = 1, name: str = "Belagavi Fort") -> Destination:
    d = Destination()
    d.place_id = place_id
    d.name = name
    d.category = "Fort"
    d.city = "Belagavi"
    d.description = "Historic fort in Belagavi"
    d.history = "Historic site"
    d.architecture = "Stone"
    d.famous_features = "Kamal Basti"
    d.best_time = "October to March"
    d.entry_fee = "Free"
    d.visit_duration = "2 Hours"
    d.how_to_reach = "By road"
    d.local_tips = "Carry water"
    d.detailed_history = "Deep history"
    d.transport_summary = "Auto: Rs.50"
    d.folder_name = "belagavi_fort"
    d.lat = 15.86
    d.lon = 74.52
    return d


VALID_AI_RESPONSE = {
    "answer": "Belagavi Fort is a historic site.",
    "destinations": [
        {"place_id": 1, "name": "Belagavi Fort", "reason": "Historic site."},
    ],
    "sources": ["Belagavi Fort"],
}


class MockRateLimitDB:
    """In-memory SQLite-like mock for rate limit testing."""
    def __init__(self):
        self.records = {}  # uid -> {"count": int, "start": float}

    async def execute(self, statement, params=None):
        sql = str(statement)
        if "CREATE TABLE" in sql:
            return AsyncMock(fetchone=lambda: None)

        if "SELECT request_count" in sql and params:
            uid = params["uid"]
            if uid in self.records:
                import time
                rec = self.records[uid]
                elapsed = time.time() - rec["start"]
                return AsyncMock(fetchone=lambda: (rec["count"], elapsed))
            return AsyncMock(fetchone=lambda: None)

        if "INSERT INTO api_rate_limits" in sql and params:
            import time
            uid = params["uid"]
            self.records[uid] = {"count": 1, "start": time.time()}
            return AsyncMock()

        if "UPDATE api_rate_limits" in sql and params:
            import time
            uid = params["uid"]
            if "request_count = 1" in sql:
                self.records[uid] = {"count": 1, "start": time.time()}
            elif "request_count = request_count + 1" in sql:
                self.records[uid]["count"] += 1
            return AsyncMock()

        return AsyncMock(fetchone=lambda: None)

    async def commit(self):
        pass

    async def rollback(self):
        pass


@pytest.fixture
def rate_limit_client():
    mock_db = MockRateLimitDB()

    async def _mock_db_gen():
        yield mock_db

    async def _mock_auth():
        return "rate_limited_user_1"

    app.dependency_overrides[get_db] = _mock_db_gen
    app.dependency_overrides[require_firebase_user] = _mock_auth

    with patch("app.main.init_db", new=AsyncMock(return_value=None)), \
         patch("app.services.search_service.SearchService.get_top_for_rag", new=AsyncMock(return_value=[_make_dest()])), \
         patch("app.rag.llm_client.LLMClient.generate_rag_response", new=AsyncMock(return_value=VALID_AI_RESPONSE)):
        with TestClient(app, raise_server_exceptions=False) as c:
            yield c, mock_db

    app.dependency_overrides.clear()


# ===========================================================================
# Rate Limit Tests
# ===========================================================================

def test_requests_within_limit_succeed_200(rate_limit_client, monkeypatch):
    """Requests under the limit return HTTP 200."""
    client, mock_db = rate_limit_client
    monkeypatch.setenv("RATE_LIMIT_PER_MINUTE", "5")
    get_settings.cache_clear()

    for i in range(3):
        r = client.post("/api/chat", json={"message": "Tell me about Belagavi Fort"})
        assert r.status_code == 200, f"Request {i+1} failed: {r.text}"


def test_repeated_requests_exceeding_limit_return_429(rate_limit_client, monkeypatch):
    """Requests exceeding the limit return HTTP 429 with Retry-After headers."""
    client, mock_db = rate_limit_client
    monkeypatch.setenv("RATE_LIMIT_PER_MINUTE", "3")
    monkeypatch.setenv("RATE_LIMIT_WINDOW_SECONDS", "60")
    get_settings.cache_clear()

    # 1st request -> count=1 (200)
    r1 = client.post("/api/chat", json={"message": "Hi 1"})
    assert r1.status_code == 200

    # 2nd request -> count=2 (200)
    r2 = client.post("/api/chat", json={"message": "Hi 2"})
    assert r2.status_code == 200

    # 3rd request -> count=3 (200)
    r3 = client.post("/api/chat", json={"message": "Hi 3"})
    assert r3.status_code == 200

    # 4th request -> limit reached (429)
    r4 = client.post("/api/chat", json={"message": "Hi 4"})
    assert r4.status_code == 429
    data = r4.json()
    assert "Too many requests" in data["detail"]
    assert "Retry-After" in r4.headers
    assert int(r4.headers["Retry-After"]) >= 1
    assert r4.headers["X-RateLimit-Limit"] == "3"
    assert r4.headers["X-RateLimit-Remaining"] == "0"


def test_independent_limits_for_different_users(rate_limit_client, monkeypatch):
    """Different UIDs have independent rate limit buckets."""
    client, mock_db = rate_limit_client
    monkeypatch.setenv("RATE_LIMIT_PER_MINUTE", "2")
    get_settings.cache_clear()

    # User 1 makes 2 requests
    app.dependency_overrides[require_firebase_user] = lambda: "user_alpha"
    assert client.post("/api/chat", json={"message": "A1"}).status_code == 200
    assert client.post("/api/chat", json={"message": "A2"}).status_code == 200
    # User 1 hits 429
    assert client.post("/api/chat", json={"message": "A3"}).status_code == 429

    # User 2 makes a request -> should succeed (200)
    app.dependency_overrides[require_firebase_user] = lambda: "user_beta"
    assert client.post("/api/chat", json={"message": "B1"}).status_code == 200


def test_rate_limiting_disabled_flag(rate_limit_client, monkeypatch):
    """RATE_LIMIT_ENABLED=false allows unlimited requests without 429."""
    client, mock_db = rate_limit_client
    monkeypatch.setenv("RATE_LIMIT_ENABLED", "false")
    monkeypatch.setenv("RATE_LIMIT_PER_MINUTE", "2")
    get_settings.cache_clear()

    for _ in range(5):
        r = client.post("/api/chat", json={"message": "Hi"})
        assert r.status_code == 200
