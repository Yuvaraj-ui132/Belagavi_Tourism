"""
Database initialisation — creates the pgvector extension and all tables.

Run this once before starting the application or running the ingestion script.
It is also called automatically on FastAPI startup so the app is self-initialising.
"""

from __future__ import annotations

import logging

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncConnection

from app.db.database import Base, engine

logger = logging.getLogger(__name__)


async def init_db() -> None:
    """
    1. Enable the pgvector extension (idempotent — safe to re-run).
    2. Create all tables defined in the ORM models.
    """
    async with engine.begin() as conn:
        # Enable pgvector — must happen before any table with vector columns is created.
        await conn.execute(text("CREATE EXTENSION IF NOT EXISTS vector"))
        logger.info("pgvector extension enabled.")

        # Import models so SQLAlchemy's metadata knows about them.
        # This import must be here (after Base is defined) to avoid circular imports.
        import app.models.destination  # noqa: F401

        await conn.run_sync(Base.metadata.create_all)
        logger.info("Database tables created / verified.")
