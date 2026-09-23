"""
SQLAlchemy async engine + session factory for the AI backend.

We use asyncpg driver (postgresql+asyncpg://) for non-blocking I/O inside FastAPI.
The ingestion script (scripts/ingest.py) uses a separate sync connection to avoid
asyncio complexity in a one-shot CLI script.
"""

from __future__ import annotations

from typing import AsyncGenerator

from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.orm import DeclarativeBase

from app.config import get_settings


class Base(DeclarativeBase):
    """Base class for all ORM models."""
    pass


def _build_engine():
    settings = get_settings()
    return create_async_engine(
        settings.database_url,
        echo=False,           # Set True to log SQL queries (useful for debugging)
        pool_pre_ping=True,   # Check connection health before using from pool
        pool_size=5,
        max_overflow=10,
    )


# Module-level engine — created once when the module is imported.
engine = _build_engine()

# Session factory
AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autoflush=False,
    autocommit=False,
)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    """
    FastAPI dependency that provides a database session per request.
    Automatically closes the session when the request is done.
    """
    async with AsyncSessionLocal() as session:
        try:
            yield session
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()
