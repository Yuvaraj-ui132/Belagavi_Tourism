"""
Vercel serverless function entrypoint for the Belagavi Tourism FastAPI backend.
Exposes the existing FastAPI application instance for Vercel's Python runtime.
"""
from __future__ import annotations

import sys
from pathlib import Path

# Ensure the backend directory is in sys.path so 'app' can be imported reliably
backend_dir = Path(__file__).resolve().parent.parent
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))

from fastapi import Request

from app.main import app  # noqa: E402


@app.middleware("http")
async def _normalize_vercel_path(request: Request, call_next):
    """
    Handle Vercel's serverless path stripping.
    When Vercel routes /api/health to api/index.py, Vercel strips the /api
    prefix, delivering scope['path'] as /health instead of /api/health.
    This middleware restores the /api prefix so FastAPI's routes match seamlessly.
    """
    matched_path = request.headers.get("x-matched-path")
    path = request.scope.get("path", "")
    if matched_path and matched_path.startswith("/api"):
        request.scope["path"] = matched_path
    elif not path.startswith("/api") and path not in (
        "/docs",
        "/openapi.json",
        "/redoc",
        "/docs/oauth2-redirect",
        "/",
    ):
        request.scope["path"] = f"/api{path}"
    return await call_next(request)


@app.get("/", include_in_schema=False)
async def _root():
    return {
        "service": "Belagavi Tourism AI Backend",
        "status": "online",
        "docs": "/docs",
        "health": "/api/health",
    }
