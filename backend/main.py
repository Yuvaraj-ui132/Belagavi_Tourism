"""
Vercel serverless entrypoint for Belagavi Tourism AI backend.
Exposes the existing FastAPI application instance for Vercel's Python runtime.
"""
from app.main import app
