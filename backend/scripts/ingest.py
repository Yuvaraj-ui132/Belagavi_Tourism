#!/usr/bin/env python3
"""
Data ingestion script: public/static/data.js -> PostgreSQL + pgvector

This script:
1. Reads the EXISTING data.js file (NOT data.py — data.js has 31 destinations and is richer).
2. Constructs rich document text per destination.
3. Generates 768-dim embeddings using Gemini gemini-embedding-001.
4. Inserts/upserts into the PostgreSQL destinations table.

Rules:
- Does NOT modify data.js.
- Does NOT create fake destinations.
- Idempotent: re-running updates existing rows (upsert on place_id).
- Uses synchronous psycopg2-compatible connection for simplicity in a CLI script.

Usage:
    cd backend/
    python -m scripts.ingest

Prerequisites:
    - .env file with GEMINI_API_KEY and POSTGRES_* vars
    - PostgreSQL running with pgvector extension
    - Run app startup once (or `python -c "import asyncio; from app.db.init_db import init_db; asyncio.run(init_db())"`) to create the table first.
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import re
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional

# Ensure the backend/ directory is on the Python path
sys.path.insert(0, str(Path(__file__).parent.parent))

from app.config import get_settings
from app.db.init_db import init_db

logging.basicConfig(
    stream=sys.stdout,
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(message)s",
)
logger = logging.getLogger("ingest")


# ---------------------------------------------------------------------------
# Step 1: Parse data.js
# ---------------------------------------------------------------------------

def load_data_js(data_js_path: Path) -> List[Dict[str, Any]]:
    """
    Parse the JS file that defines window.allPlacesData = [...].
    We strip the JS wrapper and parse the inner JSON array.
    
    data.js format:
        window.allPlacesData = [
          { "id": 1, "name": "...", ... },
          ...
        ];
    """
    logger.info("Loading data.js from: %s", data_js_path)

    raw = data_js_path.read_text(encoding="utf-8")

    # Remove the JS variable assignment wrapper: window.allPlacesData = [...];
    # Use a regex to extract the JSON array between the first [ and last ]
    match = re.search(r"window\.allPlacesData\s*=\s*(\[.*\])\s*;?\s*$", raw, re.DOTALL)
    if not match:
        raise ValueError(
            f"Could not find 'window.allPlacesData = [...]' in {data_js_path}. "
            "Has the file format changed?"
        )

    json_array_str = match.group(1)
    destinations = json.loads(json_array_str)
    logger.info("Parsed %d destinations from data.js", len(destinations))
    return destinations


# ---------------------------------------------------------------------------
# Step 2: Build document text for embedding
# ---------------------------------------------------------------------------

def build_document_text(dest: Dict[str, Any]) -> str:
    """
    Construct a rich, natural-language document for each destination.
    This text is what gets embedded into vector space.

    Including all relevant fields helps the embedding capture diverse query types:
    - Category/history queries ("historical forts")
    - Feature queries ("waterfalls", "photography")
    - Practical queries ("family-friendly", "free entry")
    - Location queries ("near Belagavi")
    """
    transport = dest.get("transport", {})
    bus_routes = transport.get("bus", [])
    bus_summary = "; ".join(
        f"{b.get('route', '')} ({b.get('fare', '')})" for b in bus_routes
    ) if bus_routes else ""
    auto_taxi = transport.get("auto_taxi", "")
    distance = transport.get("distance_from_city", "")

    transport_summary = f"{distance}. {bus_summary}. {auto_taxi}".strip(". ")

    parts = [
        f"Name: {dest.get('name', '')}.",
        f"Category: {dest.get('category', '')}.",
        f"City: {dest.get('city', '')}.",
        f"Description: {dest.get('description', '')}.",
        f"History: {dest.get('history', '')}.",
        f"Architecture: {dest.get('architecture', '')}.",
        f"Famous Features: {dest.get('famous_features', '')}.",
        f"Best Time to Visit: {dest.get('best_time', '')}.",
        f"Entry Fee: {dest.get('entry_fee', '')}.",
        f"Recommended Visit Duration: {dest.get('visit_duration', '')}.",
        f"How to Reach: {dest.get('how_to_reach', '')}.",
        f"Local Tips: {dest.get('local_tips', '')}.",
        f"Detailed History: {dest.get('detailed_history', '')}.",
        f"Transport: {transport_summary}.",
    ]

    return " ".join(p for p in parts if p and p != ". " and len(p) > 5)


# ---------------------------------------------------------------------------
# Step 3: Embed and upsert
# ---------------------------------------------------------------------------

async def run_ingestion():
    settings = get_settings()

    # Locate data.js — relative to this script's grandparent directory (project root)
    project_root = Path(__file__).parent.parent.parent
    data_js_path = project_root / "public" / "static" / "data.js"

    if not data_js_path.exists():
        raise FileNotFoundError(
            f"data.js not found at {data_js_path}. "
            "Make sure you run this script from the backend/ directory."
        )

    # Parse the dataset
    destinations = load_data_js(data_js_path)

    # Initialise the database (idempotent — creates tables if not exist)
    logger.info("Initialising database…")
    await init_db()

    # Import after DB init to avoid circular imports
    from app.embeddings.embedder import EmbeddingService
    from app.db.database import AsyncSessionLocal
    from app.models.destination import Destination
    from sqlalchemy import select
    from sqlalchemy.dialects.postgresql import insert as pg_insert

    embedder = EmbeddingService()

    async with AsyncSessionLocal() as session:
        success_count = 0
        error_count = 0

        for i, dest in enumerate(destinations, start=1):
            place_id = dest.get("id")
            name = dest.get("name", "Unknown")

            logger.info("[%d/%d] Processing: %s (id=%s)", i, len(destinations), name, place_id)

            # Build the document text for embedding
            doc_text = build_document_text(dest)

            # Generate embedding
            try:
                embedding = await embedder.embed_text(doc_text, task_type="retrieval_document")
                logger.info("  Embedding generated: %d dims", len(embedding))
            except Exception as exc:
                logger.error("  Failed to embed %s: %s — skipping", name, exc)
                error_count += 1
                continue

            # Build transport summary for storage
            transport = dest.get("transport", {})
            bus_routes = transport.get("bus", [])
            bus_str = "; ".join(
                f"{b.get('route', '')} ({b.get('fare', '')})" for b in bus_routes
            )
            transport_summary = f"{transport.get('distance_from_city', '')}. {bus_str}. {transport.get('auto_taxi', '')}".strip(". ")

            # Upsert into PostgreSQL (insert or update on conflict)
            row = {
                "place_id": place_id,
                "name": name,
                "category": dest.get("category"),
                "city": dest.get("city"),
                "description": dest.get("description"),
                "history": dest.get("history"),
                "architecture": dest.get("architecture"),
                "famous_features": dest.get("famous_features"),
                "best_time": dest.get("best_time"),
                "entry_fee": dest.get("entry_fee"),
                "visit_duration": dest.get("visit_duration"),
                "how_to_reach": dest.get("how_to_reach"),
                "local_tips": dest.get("local_tips"),
                "detailed_history": dest.get("detailed_history"),
                "transport_summary": transport_summary,
                "folder_name": dest.get("folder_name"),
                "lat": dest.get("lat"),
                "lon": dest.get("lon"),
                "document_text": doc_text,
                "embedding": embedding,
            }

            stmt = pg_insert(Destination).values(**row)
            stmt = stmt.on_conflict_do_update(
                index_elements=["place_id"],
                set_={k: v for k, v in row.items() if k != "place_id"},
            )

            await session.execute(stmt)
            await session.commit()

            success_count += 1
            logger.info("  Upserted destination id=%s: %s", place_id, name)

            # Small delay to respect API rate limits
            time.sleep(0.3)

    logger.info("=" * 60)
    logger.info("Ingestion complete!")
    logger.info("  Destinations processed : %d", len(destinations))
    logger.info("  Successfully indexed   : %d", success_count)
    logger.info("  Errors (skipped)       : %d", error_count)
    logger.info("  Embedding model        : %s", settings.gemini_embedding_model)
    logger.info("  Embedding dimension    : %d", settings.gemini_embedding_dimension)
    logger.info("=" * 60)


if __name__ == "__main__":
    asyncio.run(run_ingestion())
