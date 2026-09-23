"""
Tests for POST /api/chat (RAG pipeline) endpoint.

Tested scenarios:
- Empty message rejected (422).
- Whitespace message rejected (422).
- Missing message field rejected (422).
- Valid query returns ChatResponse structure.
- Destination fields come from DB, not LLM (deterministic fields verified).
- LLM hallucinated place_id (not in retrieved docs) is filtered out.
- LLM failure returns graceful degradation (not 500 crash).
- Zero retrieved documents returns appropriate answer.
"""

from __future__ import annotations

from unittest.mock import AsyncMock, patch

import pytest

from app.models.destination import Destination


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_orm_dest(place_id: int, name: str, category: str) -> Destination:
    """Create a Destination ORM object without a DB."""
    d = Destination()
    d.place_id = place_id
    d.name = name
    d.category = category
    d.city = "Belagavi"
    d.description = f"A wonderful {category}"
    d.history = "Historic site"
    d.architecture = "Stone"
    d.famous_features = "Great views"
    d.best_time = "October to March"
    d.entry_fee = "Free"
    d.visit_duration = "2 Hours"
    d.how_to_reach = "By road"
    d.local_tips = "Carry water"
    d.detailed_history = "Long history"
    d.transport_summary = "Auto: Rs.60"
    d.folder_name = name.lower().replace(" ", "_")
    d.lat = 15.86
    d.lon = 74.52
    return d


VALID_LLM_RESPONSE = {
    "answer": "Belagavi Fort is a great historical destination.",
    "destinations": [
        {"place_id": 1, "name": "Belagavi Fort", "reason": "800+ years of history."},
    ],
    "sources": ["Belagavi Fort"],
}

HALLUCINATED_LLM_RESPONSE = {
    "answer": "Here are some places.",
    "destinations": [
        {"place_id": 999, "name": "Invented Place", "reason": "LLM invented this."},
    ],
    "sources": ["Invented Place"],
}

EMPTY_LLM_RESPONSE = {
    "answer": "This information is not available in the Belagavi tourism database.",
    "destinations": [],
    "sources": [],
}

GRACEFUL_ERROR_RESPONSE = {
    "answer": "I'm sorry, I'm unable to process your request at the moment. Please try again later.",
    "destinations": [],
    "sources": [],
    "_error": "API quota exceeded",
}


def _patch_rag(mock_retrieved_docs, mock_llm_dict):
    """Returns a tuple of patch context managers for SearchService and LLMClient."""
    from app.services import search_service
    from app.rag import llm_client

    s = patch.object(
        search_service.SearchService,
        "get_top_for_rag",
        AsyncMock(return_value=mock_retrieved_docs),
    )
    l = patch.object(
        llm_client.LLMClient,
        "generate_rag_response",
        AsyncMock(return_value=mock_llm_dict),
    )
    return s, l


# ---------------------------------------------------------------------------
# Validation tests
# ---------------------------------------------------------------------------

def test_chat_empty_message_rejected(test_client):
    response = test_client.post("/api/chat", json={"message": ""})
    assert response.status_code == 422


def test_chat_whitespace_message_rejected(test_client):
    response = test_client.post("/api/chat", json={"message": "   "})
    assert response.status_code == 422


def test_chat_missing_message_rejected(test_client):
    response = test_client.post("/api/chat", json={})
    assert response.status_code == 422


# ---------------------------------------------------------------------------
# Functional tests
# ---------------------------------------------------------------------------

def test_chat_valid_query_returns_structure(test_client):
    """Valid query returns ChatResponse with all required fields."""
    s_p, l_p = _patch_rag(
        [_make_orm_dest(1, "Belagavi Fort", "Fort")],
        VALID_LLM_RESPONSE,
    )
    with s_p, l_p:
        response = test_client.post(
            "/api/chat",
            json={"message": "Tell me about historical forts"},
        )

    assert response.status_code == 200
    body = response.json()
    assert "answer" in body
    assert "destinations" in body
    assert "sources" in body
    assert "retrieved_count" in body
    assert isinstance(body["answer"], str)
    assert isinstance(body["destinations"], list)
    assert isinstance(body["sources"], list)
    assert body["retrieved_count"] == 1


def test_chat_destination_fields_are_from_db(test_client):
    """
    Destination fields in response come from DB record, NOT from LLM.
    The LLM only contributes the 'reason' text field.
    """
    s_p, l_p = _patch_rag(
        [_make_orm_dest(1, "Belagavi Fort", "Fort")],
        VALID_LLM_RESPONSE,
    )
    with s_p, l_p:
        response = test_client.post("/api/chat", json={"message": "historical places"})

    assert response.status_code == 200
    destinations = response.json()["destinations"]
    assert len(destinations) == 1
    dest = destinations[0]

    # Deterministic DB fields — must match mock ORM object values
    assert dest["place_id"] == 1
    assert dest["name"] == "Belagavi Fort"
    assert dest["entry_fee"] == "Free"          # from mock DB record
    assert dest["visit_duration"] == "2 Hours"  # from mock DB record
    assert dest["city"] == "Belagavi"           # from mock DB record
    assert dest["lat"] == 15.86                 # from mock DB record

    # LLM-generated text field
    assert "reason" in dest
    assert isinstance(dest["reason"], str)


def test_chat_hallucinated_place_id_filtered_out(test_client):
    """
    LLM returns place_id=999 which is NOT in the retrieved docs.
    The RAG service must filter it out (anti-hallucination).
    Fallback: retrieved docs (place_id=1) are included instead.
    """
    s_p, l_p = _patch_rag(
        [_make_orm_dest(1, "Belagavi Fort", "Fort")],
        HALLUCINATED_LLM_RESPONSE,
    )
    with s_p, l_p:
        response = test_client.post("/api/chat", json={"message": "places to visit"})

    assert response.status_code == 200
    destinations = response.json()["destinations"]
    place_ids = [d["place_id"] for d in destinations]

    # place_id 999 must NOT appear (hallucinated)
    assert 999 not in place_ids
    # place_id 1 should appear (fallback from retrieved docs)
    assert 1 in place_ids


def test_chat_llm_failure_returns_graceful_response(test_client):
    """LLM API failure returns graceful 200 response, not a 500 crash."""
    s_p, l_p = _patch_rag(
        [_make_orm_dest(1, "Belagavi Fort", "Fort")],
        GRACEFUL_ERROR_RESPONSE,
    )
    with s_p, l_p:
        response = test_client.post("/api/chat", json={"message": "what to visit?"})

    # Must return 200 (graceful degradation) not 500
    assert response.status_code == 200
    body = response.json()
    answer = body["answer"].lower()
    assert "unable" in answer or "sorry" in answer


def test_chat_no_retrieved_docs_returns_answer(test_client):
    """Zero retrieved docs returns an answer without crashing."""
    s_p, l_p = _patch_rag(
        [],  # No retrieved documents
        EMPTY_LLM_RESPONSE,
    )
    with s_p, l_p:
        response = test_client.post(
            "/api/chat",
            json={"message": "best beach in Goa"},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["retrieved_count"] == 0
    assert body["destinations"] == []
    assert "not available" in body["answer"].lower()


def test_chat_sources_list_populated(test_client):
    """Sources list contains names of retrieved destinations."""
    docs = [
        _make_orm_dest(1, "Belagavi Fort", "Fort"),
        _make_orm_dest(22, "Yellur Fort", "Fort"),
    ]
    llm_resp = {
        "answer": "Both forts are worth visiting.",
        "destinations": [
            {"place_id": 1, "name": "Belagavi Fort", "reason": "Historic fort."},
            {"place_id": 22, "name": "Yellur Fort", "reason": "Scenic fort."},
        ],
        "sources": ["Belagavi Fort", "Yellur Fort"],
    }
    s_p, l_p = _patch_rag(docs, llm_resp)
    with s_p, l_p:
        response = test_client.post("/api/chat", json={"message": "tell me about forts"})

    assert response.status_code == 200
    body = response.json()
    assert "Belagavi Fort" in body["sources"]
    assert "Yellur Fort" in body["sources"]
