"""
FastAPI application entry point.

Startup sequence:
1. Load settings from .env
2. Configure logging
3. Initialise database (enable pgvector extension + create tables)
4. Mount API router

CORS is configured to allow the web frontend and local dev tools.
"""

from __future__ import annotations

import logging
import sys
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import get_settings
from app.db.init_db import init_db
from app.api.routes import router


def _configure_logging(level: str) -> None:
    logging.basicConfig(
        stream=sys.stdout,
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s | %(levelname)-8s | %(name)s | %(message)s",
    )


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan: init DB on startup, cleanup on shutdown."""
    settings = get_settings()
    _configure_logging(settings.log_level)

    logger = logging.getLogger(__name__)
    logger.info("Starting Belagavi Tourism AI Backend…")
    logger.info("Embedding model : %s (dim=%d)", settings.gemini_embedding_model, settings.gemini_embedding_dimension)
    logger.info("LLM model       : %s", settings.gemini_llm_model)
    logger.info("Database        : %s:%d/%s", settings.postgres_host, settings.postgres_port, settings.postgres_db)

    # Initialise PostgreSQL + pgvector
    await init_db()
    logger.info("Database initialised.")

    yield  # Application runs here

    logger.info("Shutting down…")


def create_app() -> FastAPI:
    settings = get_settings()

    app = FastAPI(
        title="Belagavi Tourism AI Backend",
        description=(
            "RAG-powered semantic search and tourism assistant. "
            "Uses pgvector for semantic retrieval and Gemini for grounded responses. "
            "Firebase remains the source of truth for the existing application."
        ),
        version="1.0.0",
        docs_url="/docs",
        redoc_url="/redoc",
        lifespan=lifespan,
    )

    # CORS — allow frontend origins
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["Content-Type", "Authorization"],
    )

    # Mount the API router
    app.include_router(router)

    return app


# Module-level app instance used by uvicorn
app = create_app()
