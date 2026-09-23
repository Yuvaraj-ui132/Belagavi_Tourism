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

from app.main import app  # noqa: E402
