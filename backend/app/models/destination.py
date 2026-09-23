"""
SQLAlchemy ORM model for the destinations table.

Design decisions:
- place_id (INTEGER): matches the 'id' field in data.js — used to link back to the
  existing Firebase/client-side dataset without duplicating data.
- document_text (TEXT): full concatenated text used for embedding; generated at ingestion time.
- embedding (Vector(768)): pgvector column. Dimension matches GEMINI_EMBEDDING_DIMENSION=768.
  If you change the dimension in .env, you must DROP and recreate the column.

Why no vector index?
  The dataset has 31 destinations. pgvector's sequential scan (exact nearest-neighbour)
  on 31 rows takes microseconds. HNSW or IVFFlat indexes add overhead and complexity at
  this scale. We will add HNSW when the row count grows past ~1,000.
"""

from __future__ import annotations

from datetime import datetime

from pgvector.sqlalchemy import Vector
from sqlalchemy import DateTime, Double, Integer, String, Text, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.database import Base
from app.config import get_settings


def _embedding_dim() -> int:
    """Read dimension from settings so the column size matches the embedding model."""
    return get_settings().gemini_embedding_dimension


class Destination(Base):
    __tablename__ = "destinations"
    __table_args__ = (
        UniqueConstraint("place_id", name="uq_destinations_place_id"),
    )

    # Primary key (internal DB identifier)
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)

    # External identifier — matches data.js 'id' field
    # Used by Android/Web to link AI results to existing destination data.
    place_id: Mapped[int] = mapped_column(Integer, nullable=False, index=True)

    # Core destination metadata (deterministic — from dataset, NOT from LLM)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    category: Mapped[str | None] = mapped_column(String(100))
    city: Mapped[str | None] = mapped_column(String(100))
    description: Mapped[str | None] = mapped_column(Text)
    history: Mapped[str | None] = mapped_column(Text)
    architecture: Mapped[str | None] = mapped_column(Text)
    famous_features: Mapped[str | None] = mapped_column(Text)
    best_time: Mapped[str | None] = mapped_column(String(255))
    entry_fee: Mapped[str | None] = mapped_column(String(100))
    visit_duration: Mapped[str | None] = mapped_column(String(100))
    how_to_reach: Mapped[str | None] = mapped_column(Text)
    local_tips: Mapped[str | None] = mapped_column(Text)
    detailed_history: Mapped[str | None] = mapped_column(Text)
    transport_summary: Mapped[str | None] = mapped_column(Text)
    folder_name: Mapped[str | None] = mapped_column(String(100))

    # Geographic coordinates (deterministic — never from LLM)
    lat: Mapped[float | None] = mapped_column(Double)
    lon: Mapped[float | None] = mapped_column(Double)

    # Full concatenated text used for embedding generation
    document_text: Mapped[str | None] = mapped_column(Text)

    # Vector embedding column — dimension set from config
    # Vector(768) stores a 768-float array; pgvector handles cosine similarity.
    embedding: Mapped[list | None] = mapped_column(Vector(_embedding_dim()))

    # Timestamps
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    def __repr__(self) -> str:
        return f"<Destination id={self.place_id} name={self.name!r}>"
