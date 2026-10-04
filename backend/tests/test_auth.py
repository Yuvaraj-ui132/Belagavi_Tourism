"""
Tests for Firebase ID-token authentication on POST /api/chat.

Covers the 5 required tests:
TEST 1: Authenticated user -> /api/chat -> returns normal AI response (200).
TEST 2: No Authorization header -> HTTP 401 Unauthorized.
TEST 3: Invalid/malformed token -> HTTP 401 Unauthorized.
TEST 4: Expired/revoked Firebase token -> HTTP 401 Unauthorized.
TEST 5: Authenticated user with normal AI request -> Gemini/Tavily work as before.
"""

from __future__ import annotations

from unittest.mock import AsyncMock, patch
import pytest
from starlette.testclient import TestClient

from app.main import app
from app.db.database import get_db
from app.models.destination import Destination


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
    "answer": "Belagavi Fort is a historic site built in 1204 AD.",
    "destinations": [
        {"place_id": 1, "name": "Belagavi Fort", "reason": "Historic fort built in 1204 AD."},
    ],
    "sources": ["Belagavi Fort"],
}


@pytest.fixture
def auth_test_client():
    """TestClient without any auth mock override, with DB mocked."""
    mock_session = AsyncMock()
    mock_session.execute = AsyncMock(return_value=None)
    mock_session.rollback = AsyncMock()
    mock_session.close = AsyncMock()

    async def _mock_db():
        yield mock_session

    app.dependency_overrides.clear()
    app.dependency_overrides[get_db] = _mock_db

    with patch("app.main.init_db", new=AsyncMock(return_value=None)):
        with TestClient(app, raise_server_exceptions=False) as c:
            yield c

    app.dependency_overrides.clear()


# ===========================================================================
# TEST 1: Authenticated user -> /api/chat -> returns normal AI response
# ===========================================================================

def test_1_authenticated_user_returns_200_normal_ai_response(auth_test_client):
    """
    TEST 1:
    A valid Firebase ID token in Authorization: Bearer <token>
    extracts the verified UID server-side and returns the normal AI response (200).
    """
    from app.services import search_service
    from app.rag import llm_client

    mock_dest = _make_dest(1, "Belagavi Fort")
    s = patch.object(search_service.SearchService, "get_top_for_rag", AsyncMock(return_value=[mock_dest]))
    l = patch.object(llm_client.LLMClient, "generate_rag_response", AsyncMock(return_value=VALID_AI_RESPONSE))

    with patch("app.auth.firebase.verify_token_payload", return_value={"uid": "firebase_user_123"}), s, l:
        response = auth_test_client.post(
            "/api/chat",
            json={"message": "Tell me about Belagavi Fort"},
            headers={"Authorization": "Bearer valid-firebase-token-sample"},
        )

        assert response.status_code == 200
        data = response.json()
        assert "answer" in data
        assert "Belagavi Fort" in data["answer"]
        assert len(data["destinations"]) >= 1
        assert data["destinations"][0]["place_id"] == 1
        assert data["destinations"][0]["name"] == "Belagavi Fort"
        # Verify no token or secret returned in response
        assert "token" not in response.text
        assert "Bearer" not in response.text


# ===========================================================================
# TEST 2: No Authorization header -> HTTP 401
# ===========================================================================

def test_2_no_authorization_header_rejected_401(auth_test_client):
    """
    TEST 2:
    Missing Authorization header must immediately return HTTP 401 Unauthorized.
    """
    response = auth_test_client.post(
        "/api/chat",
        json={"message": "Tell me about Belagavi Fort"},
    )

    assert response.status_code == 401
    data = response.json()
    assert "detail" in data
    assert "WWW-Authenticate" in response.headers
    assert response.headers["WWW-Authenticate"] == "Bearer"


# ===========================================================================
# TEST 3: Invalid / malformed token -> HTTP 401
# ===========================================================================

def test_3_malformed_or_invalid_token_rejected_401(auth_test_client):
    """
    TEST 3:
    Malformed, garbage, or fake tokens must return HTTP 401 Unauthorized.
    """
    # 3a. Malformed garbage string
    response1 = auth_test_client.post(
        "/api/chat",
        json={"message": "Tell me about Belagavi Fort"},
        headers={"Authorization": "Bearer not-a-valid-token-garbage.123"},
    )
    assert response1.status_code == 401

    # 3b. Empty bearer token
    response2 = auth_test_client.post(
        "/api/chat",
        json={"message": "Tell me about Belagavi Fort"},
        headers={"Authorization": "Bearer "},
    )
    assert response2.status_code == 401

    # 3c. Non-Bearer authorization scheme
    response3 = auth_test_client.post(
        "/api/chat",
        json={"message": "Tell me about Belagavi Fort"},
        headers={"Authorization": "Basic dXNlcjpwYXNz"},
    )
    assert response3.status_code == 401


# ===========================================================================
# TEST 4: Expired / revoked Firebase token -> HTTP 401
# ===========================================================================

def test_4_expired_or_revoked_token_rejected_401(auth_test_client):
    """
    TEST 4:
    Expired or revoked Firebase tokens must return HTTP 401 Unauthorized.
    """
    from firebase_admin import auth

    # 4a. Expired token
    with patch("app.auth.firebase.auth.verify_id_token", side_effect=auth.ExpiredIdTokenError("Token expired", None)):
        response_expired = auth_test_client.post(
            "/api/chat",
            json={"message": "Tell me about Belagavi Fort"},
            headers={"Authorization": "Bearer expired-token"},
        )
        assert response_expired.status_code == 401
        assert "expired" in response_expired.json()["detail"].lower()

    # 4b. Revoked token
    with patch("app.auth.firebase.auth.verify_id_token", side_effect=auth.RevokedIdTokenError("Token revoked")):
        response_revoked = auth_test_client.post(
            "/api/chat",
            json={"message": "Tell me about Belagavi Fort"},
            headers={"Authorization": "Bearer revoked-token"},
        )
        assert response_revoked.status_code == 401
        assert "revoked" in response_revoked.json()["detail"].lower()


# ===========================================================================
# TEST 5: Authenticated user with normal AI request -> Gemini/Tavily work as before
# ===========================================================================

def test_5_authenticated_user_pipeline_gemini_tavily_working(auth_test_client):
    """
    TEST 5:
    Full pipeline with authenticated user: RAG context retrieval, Gemini LLM,
    and optional Tavily web sources are preserved and work exactly as before.
    """
    from app.services import search_service
    from app.rag import llm_client

    mock_dest = _make_dest(1, "Belagavi Fort")
    ai_resp_with_sources = {
        "answer": "Belagavi Fort timings are 8 AM to 6 PM.",
        "destinations": [
            {"place_id": 1, "name": "Belagavi Fort", "reason": "Timings verified."},
        ],
        "sources": ["Belagavi Fort"],
    }

    s = patch.object(search_service.SearchService, "get_top_for_rag", AsyncMock(return_value=[mock_dest]))
    l = patch.object(llm_client.LLMClient, "generate_rag_response", AsyncMock(return_value=ai_resp_with_sources))

    with patch("app.auth.firebase.verify_token_payload", return_value={"uid": "verified_uid_456"}), s, l:
        response = auth_test_client.post(
            "/api/chat",
            json={"message": "What are the timings for Belagavi Fort today?"},
            headers={"Authorization": "Bearer valid-token-user-456"},
        )

        assert response.status_code == 200
        data = response.json()
        assert data["answer"] == "Belagavi Fort timings are 8 AM to 6 PM."
        assert len(data["destinations"]) == 1
        assert data["destinations"][0]["place_id"] == 1
        # Confirm deterministic DB fields were overlaid properly
        assert data["destinations"][0]["entry_fee"] == "Free"
        assert data["destinations"][0]["lat"] == 15.86
        assert data["destinations"][0]["lon"] == 74.52
        assert "web_sources" in data
        assert "web_research_used" in data


# ===========================================================================
# TEST 6: FIREBASE_SERVICE_ACCOUNT_JSON parsing and handling
# ===========================================================================

def test_firebase_service_account_json_parsing_and_credential_initialization(monkeypatch):
    """
    Verifies that FIREBASE_SERVICE_ACCOUNT_JSON (including escaped newlines \\n)
    is parsed and initialized via credentials.Certificate cleanly.
    """
    import json
    import app.auth.firebase as fb
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.hazmat.primitives import serialization

    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem = key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    ).decode("utf-8")

    sa_dict = {
        "type": "service_account",
        "project_id": "belagavi-tourism-planner",
        "private_key_id": "key123",
        "private_key": pem.replace("\n", "\\n"),  # Escaped newlines as often stored in env vars
        "client_email": "test-admin@belagavi-tourism-planner.iam.gserviceaccount.com",
        "client_id": "client123",
        "auth_uri": "https://accounts.google.com/o/oauth2/auth",
        "token_uri": "https://oauth2.googleapis.com/token",
    }

    monkeypatch.setenv("FIREBASE_SERVICE_ACCOUNT_JSON", json.dumps(sa_dict))
    fb._firebase_app = None

    with patch("firebase_admin.get_app", side_effect=ValueError("No app")), \
         patch("firebase_admin.initialize_app") as mock_init:
        mock_init.return_value = "mock_app"
        app = fb._get_firebase_app()
        assert app == "mock_app"
        assert mock_init.called
        # Verify the Certificate credential was constructed successfully
        cred_arg = mock_init.call_args[0][0]
        assert hasattr(cred_arg, "_g_credential")

