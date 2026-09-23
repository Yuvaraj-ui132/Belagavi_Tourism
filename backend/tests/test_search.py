"""
Tests for POST /api/search endpoint.

Tested scenarios:
- Empty query rejected (422).
- Whitespace query rejected (422).
- Missing query field rejected (422).
- Limit > 20 rejected (422).
- Valid query returns 200 with results containing required fields.
- No matching results returns 200 with empty list.
- Embedding service failure returns 503.
- Result fields are deterministic (place_id, name, similarity present).
"""

from __future__ import annotations

from unittest.mock import AsyncMock, patch

import pytest


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_result(place_id: int, name: str, category: str, similarity: float):
    from app.schemas.search import DestinationResult
    return DestinationResult(
        place_id=place_id,
        name=name,
        category=category,
        city="Belagavi",
        description=f"A wonderful {category}",
        best_time="Year-round",
        entry_fee="Free",
        visit_duration="2 Hours",
        folder_name=name.lower().replace(" ", "_"),
        lat=15.86,
        lon=74.52,
        similarity=similarity,
    )


# ---------------------------------------------------------------------------
# Validation tests (Pydantic rejects before route logic runs)
# ---------------------------------------------------------------------------

def test_search_empty_query_rejected(test_client):
    response = test_client.post("/api/search", json={"query": ""})
    assert response.status_code == 422


def test_search_whitespace_query_rejected(test_client):
    response = test_client.post("/api/search", json={"query": "   "})
    assert response.status_code == 422


def test_search_missing_query_rejected(test_client):
    response = test_client.post("/api/search", json={"limit": 5})
    assert response.status_code == 422


def test_search_limit_too_large_rejected(test_client):
    response = test_client.post("/api/search", json={"query": "waterfalls", "limit": 100})
    assert response.status_code == 422


# ---------------------------------------------------------------------------
# Functional tests (mock SearchService)
# ---------------------------------------------------------------------------

def test_search_returns_results(test_client):
    """Valid query returns 200 with results list."""
    from app.services import search_service

    mock_results = [_make_result(1, "Belagavi Fort", "Fort", 0.92)]

    with patch.object(
        search_service.SearchService,
        "search",
        AsyncMock(return_value=mock_results),
    ):
        response = test_client.post(
            "/api/search",
            json={"query": "historical forts in Belagavi", "limit": 5},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["query"] == "historical forts in Belagavi"
    assert body["total"] == 1
    assert len(body["results"]) == 1


def test_search_empty_results(test_client):
    """No matching results returns 200 with empty list."""
    from app.services import search_service

    with patch.object(
        search_service.SearchService,
        "search",
        AsyncMock(return_value=[]),
    ):
        response = test_client.post(
            "/api/search",
            json={"query": "xyzzy_nonexistent_place"},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["total"] == 0
    assert body["results"] == []


def test_search_embedding_failure_returns_503(test_client):
    """Embedding service failure returns 503."""
    from app.services import search_service

    async def _raise(*args, **kwargs):
        raise RuntimeError("Embedding API unavailable")

    with patch.object(search_service.SearchService, "search", _raise):
        response = test_client.post("/api/search", json={"query": "waterfalls"})

    assert response.status_code == 503


def test_search_result_has_required_fields(test_client):
    """Results contain place_id, name, similarity — all deterministic DB fields."""
    from app.services import search_service

    mock_results = [_make_result(6, "Gokak Falls", "Waterfall", 0.93)]

    with patch.object(
        search_service.SearchService,
        "search",
        AsyncMock(return_value=mock_results),
    ):
        response = test_client.post("/api/search", json={"query": "waterfalls"})

    assert response.status_code == 200
    results = response.json()["results"]
    assert len(results) == 1
    result = results[0]

    # Required deterministic fields
    assert result["place_id"] == 6
    assert result["name"] == "Gokak Falls"
    assert isinstance(result["similarity"], float)
    assert result["entry_fee"] == "Free"
    assert result["visit_duration"] == "2 Hours"


def test_search_multiple_results_ordered_by_similarity(test_client):
    """Multiple results are returned in similarity-descending order."""
    from app.services import search_service

    mock_results = [
        _make_result(6, "Gokak Falls", "Waterfall", 0.95),
        _make_result(5, "Godchinamalaki Falls", "Waterfall", 0.88),
        _make_result(3, "Yana Caves", "Cave", 0.72),
    ]

    with patch.object(
        search_service.SearchService,
        "search",
        AsyncMock(return_value=mock_results),
    ):
        response = test_client.post("/api/search", json={"query": "waterfalls"})

    assert response.status_code == 200
    body = response.json()
    assert body["total"] == 3
    scores = [r["similarity"] for r in body["results"]]
    assert scores == sorted(scores, reverse=True)  # Descending order
