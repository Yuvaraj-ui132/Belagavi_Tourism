"""
Pydantic schemas for semantic search endpoints.

Separation of concerns:
- SearchRequest:  what the client sends
- DestinationResult: one search hit (deterministic fields only — no LLM text)
- SearchResponse: full search response

All deterministic fields (place_id, lat, lon, entry_fee, etc.) come from the
database record — never from the LLM. Clients must use these values instead of
asking the LLM to regenerate them.
"""

from __future__ import annotations

from typing import List, Optional

from pydantic import BaseModel, Field, field_validator


class SearchRequest(BaseModel):
    query: str = Field(..., min_length=1, max_length=500, description="Natural language search query")
    limit: int = Field(default=5, ge=1, le=20, description="Maximum results to return")
    category: Optional[str] = Field(default=None, description="Optional category filter (e.g. Fort, Waterfall)")

    @field_validator("query")
    @classmethod
    def query_must_not_be_blank(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("query must not be empty or whitespace")
        return v.strip()


class DestinationResult(BaseModel):
    """One semantic search result. All fields come from the database, not the LLM."""
    place_id: int = Field(description="Matches data.js 'id' — used to open existing place detail")
    name: str
    category: Optional[str] = None
    city: Optional[str] = None
    description: Optional[str] = None
    best_time: Optional[str] = None
    entry_fee: Optional[str] = None
    visit_duration: Optional[str] = None
    folder_name: Optional[str] = None
    lat: Optional[float] = None
    lon: Optional[float] = None
    similarity: float = Field(description="Cosine similarity score [0, 1]")


class SearchResponse(BaseModel):
    query: str
    total: int
    results: List[DestinationResult]
