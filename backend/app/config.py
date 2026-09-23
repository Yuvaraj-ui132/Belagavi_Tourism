"""
Belagavi Tourism AI Backend — Application Configuration

All settings are loaded from environment variables (or .env file).
Model names are intentionally NOT hard-coded — change them via env vars.

Database URL resolution order (first match wins):
  1. DATABASE_URL env var (set this for Supabase or any external PostgreSQL)
  2. Constructed from POSTGRES_HOST / PORT / DB / USER / PASSWORD (local Docker default)
"""

from __future__ import annotations

import os
from functools import lru_cache
from typing import List, Optional, Union

from pydantic import Field, computed_field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # -----------------------------------------------------------------------
    # Google Gemini API
    # -----------------------------------------------------------------------
    gemini_api_key: str = Field(..., description="Google Gemini API key")

    # Model names — kept configurable; never hard-coded elsewhere in the app.
    # Verified current models (September 2026):
    #   Embedding: gemini-embedding-001  (replaces deprecated text-embedding-004)
    #   LLM:       gemini-3.5-flash      (replaces retired gemini-1.5-flash / 2.0)
    gemini_embedding_model: str = Field(
        default="gemini-embedding-001",
        description="Gemini embedding model name",
    )
    gemini_llm_model: str = Field(
        default="gemini-3.5-flash",
        description="Gemini generative model name",
    )

    # Embedding output dimension.
    # gemini-embedding-001 default is 3072; Google recommends 768, 1536, or 3072.
    # We use 768 for this project: good quality, lower storage, exact match to pgvector column.
    gemini_embedding_dimension: int = Field(
        default=768,
        description="Embedding vector dimension (must match pgvector column size)",
    )

    # -----------------------------------------------------------------------
    # PostgreSQL + pgvector
    # -----------------------------------------------------------------------
    # Individual connection parts — used when DATABASE_URL is NOT set.
    # These drive the local Docker setup and remain the fallback for all
    # environments that do not override DATABASE_URL.
    postgres_host: str = Field(default="localhost")
    postgres_port: int = Field(default=5432)
    postgres_db: str = Field(default="belagavi_tourism_ai")
    postgres_user: str = Field(default="belagavi_user")
    postgres_password: str = Field(default="changeme")

    # Optional direct URL overrides — set either of these to point at Supabase
    # (or any external PostgreSQL) without touching the POSTGRES_* vars.
    #
    # DATABASE_URL      — async DSN  (postgresql+asyncpg://... or postgresql://...)
    # SYNC_DATABASE_URL — sync  DSN  (postgresql://...)          [optional; derived
    #                                  from DATABASE_URL if only one is provided]
    #
    # Leave both unset to use the local Docker PostgreSQL (default behaviour).
    database_url_override: Optional[str] = Field(
        default=None,
        alias="DATABASE_URL",
        description="Direct DATABASE_URL override. Wins over POSTGRES_* parts.",
    )
    sync_database_url_override: Optional[str] = Field(
        default=None,
        alias="SYNC_DATABASE_URL",
        description="Direct SYNC_DATABASE_URL override. Derived from DATABASE_URL if absent.",
    )

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _to_async_dsn(url: str) -> str:
        """Ensure the URL uses the postgresql+asyncpg:// scheme."""
        if url.startswith("postgresql+asyncpg://"):
            return url
        if url.startswith("postgresql://"):
            return "postgresql+asyncpg://" + url[len("postgresql://"):]
        if url.startswith("postgres://"):
            # Heroku / Supabase sometimes emit postgres:// (non-standard alias)
            return "postgresql+asyncpg://" + url[len("postgres://"):]
        return url  # unknown scheme — pass through and let SQLAlchemy error

    @staticmethod
    def _to_sync_dsn(url: str) -> str:
        """Ensure the URL uses the plain postgresql:// scheme (psycopg2 / asyncpg sync)."""
        if url.startswith("postgresql+asyncpg://"):
            return "postgresql://" + url[len("postgresql+asyncpg://"):]
        if url.startswith("postgres://"):
            return "postgresql://" + url[len("postgres://"):]
        return url

    @computed_field  # type: ignore[misc]
    @property
    def database_url(self) -> str:
        """
        Async DSN for SQLAlchemy asyncpg driver.

        Resolution order:
          1. DATABASE_URL env var   (normalised to postgresql+asyncpg://...)
          2. Constructed from POSTGRES_* parts  (local Docker default)
        """
        if self.database_url_override:
            return self._to_async_dsn(self.database_url_override)
        return (
            f"postgresql+asyncpg://{self.postgres_user}:{self.postgres_password}"
            f"@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
        )

    @computed_field  # type: ignore[misc]
    @property
    def sync_database_url(self) -> str:
        """
        Sync DSN used by the ingestion script and verify_supabase.py.

        Resolution order:
          1. SYNC_DATABASE_URL env var
          2. DATABASE_URL env var   (normalised to postgresql://...)
          3. Constructed from POSTGRES_* parts  (local Docker default)
        """
        if self.sync_database_url_override:
            return self._to_sync_dsn(self.sync_database_url_override)
        if self.database_url_override:
            return self._to_sync_dsn(self.database_url_override)
        return (
            f"postgresql://{self.postgres_user}:{self.postgres_password}"
            f"@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
        )

    # -----------------------------------------------------------------------
    # FastAPI
    # -----------------------------------------------------------------------
    app_host: str = Field(default="0.0.0.0")
    app_port: int = Field(default=8000)
    app_reload: bool = Field(default=False)
    cors_origins: Union[List[str], str] = Field(
        default=[
            "http://localhost:3000",
            "http://localhost:5000",
            "http://127.0.0.1:5500",
            "https://belagavi-tourism-planner.web.app",
            "https://belagavi-tourism-planner.firebaseapp.com",
        ],
        description="Allowed CORS origins as a list, comma-separated string, or '*'",
    )

    @field_validator("cors_origins", mode="after")
    @classmethod
    def assemble_cors_origins(cls, v: Union[List[str], str]) -> List[str]:
        if isinstance(v, str):
            v = v.strip()
            if v.startswith("[") and v.endswith("]"):
                try:
                    import json
                    parsed = json.loads(v)
                    if isinstance(parsed, list):
                        return [str(x).strip() for x in parsed]
                except Exception:
                    pass
            return [x.strip() for x in v.split(",") if x.strip()]
        return v

    # -----------------------------------------------------------------------
    # RAG settings
    # -----------------------------------------------------------------------
    rag_top_k: int = Field(default=5, description="Documents to retrieve per query")
    rag_similarity_threshold: float = Field(
        default=0.3, description="Minimum cosine similarity to include a result"
    )

    # -----------------------------------------------------------------------
    # Logging
    # -----------------------------------------------------------------------
    log_level: str = Field(default="INFO")


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return singleton Settings instance — cached after first call."""
    return Settings()
