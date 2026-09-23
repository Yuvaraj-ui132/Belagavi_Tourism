# Belagavi Tourism AI Backend

RAG-powered semantic search and tourism assistant for the Belagavi Tourism project.

## Architecture

```
Existing System (UNCHANGED — Firebase):
  Android/Web App → Firebase Authentication → Cloud Firestore

New AI System (this backend):
  Android/Web App → FastAPI → PostgreSQL + pgvector → LLM
```

**Firebase remains the source of truth for the existing application.**
PostgreSQL + pgvector is the semantic/RAG knowledge layer ONLY.

---

## Why This Stack?

| Component | Choice | Reason |
|---|---|---|
| **FastAPI** | Python async API framework | Clean async I/O, auto OpenAPI docs, Pydantic validation |
| **PostgreSQL + pgvector** | Vector store | Lightweight, no external service needed, exact search is fast for 31 rows |
| **Gemini gemini-embedding-001** | Embedding model | Current supported model (text-embedding-004 deprecated Jan 2026), 768-dim MRL |
| **Gemini gemini-3.5-flash** | LLM | Current supported model (1.5/2.0 retired), fast and accurate |
| **google-genai SDK** | Python SDK | New unified SDK required for Gemini 3.x models |
| **No vector index** | Exact scan | 31 rows — sequential scan is faster than HNSW/IVFFlat overhead |

---

## Verified Models (September 2026)

| Purpose | Model ID | Dimension | SDK |
|---|---|---|---|
| Embedding | `gemini-embedding-001` | 768 (configured via env) | `google-genai` |
| LLM | `gemini-3.5-flash` | N/A | `google-genai` |

> **Deprecated/Retired (do not use):**
> - `text-embedding-004` — deprecated January 14, 2026
> - `gemini-1.5-flash` — retired
> - `gemini-2.0-flash` — retired

---

## Project Structure

```
backend/
├── app/
│   ├── main.py           # FastAPI application entry point
│   ├── config.py         # Settings loaded from .env (model names configurable)
│   ├── api/
│   │   └── routes.py     # GET /api/health, POST /api/search, POST /api/chat
│   ├── db/
│   │   ├── database.py   # SQLAlchemy async engine + session
│   │   └── init_db.py    # pgvector extension + table creation
│   ├── models/
│   │   └── destination.py  # SQLAlchemy ORM (vector(768) column)
│   ├── schemas/
│   │   ├── search.py     # SearchRequest / SearchResponse
│   │   └── chat.py       # ChatRequest / ChatResponse
│   ├── embeddings/
│   │   └── embedder.py   # Gemini embedding service (gemini-embedding-001)
│   ├── services/
│   │   ├── search_service.py  # pgvector cosine similarity search
│   │   └── rag_service.py     # RAG pipeline orchestration
│   └── rag/
│       ├── context_builder.py # Retrieved docs → LLM context block
│       └── llm_client.py      # Gemini LLM client (gemini-3.5-flash)
├── scripts/
│   └── ingest.py         # data.js → embeddings → PostgreSQL
├── tests/
│   ├── conftest.py       # pytest config + env var defaults
│   ├── test_health.py    # Health endpoint tests
│   ├── test_search.py    # Semantic search tests
│   ├── test_rag.py       # RAG pipeline tests
│   └── test_db.py        # Unit tests (config, parsing, context builder)
├── Dockerfile
├── docker-compose.yml
├── requirements.txt
├── .env.example          # Template — copy to .env and fill in values
└── README.md
```

---

## Environment Variables

Copy `.env.example` to `.env` and fill in your values:

```bash
cp .env.example .env
```

| Variable | Description | Default |
|---|---|---|
| `GEMINI_API_KEY` | Google AI Studio API key | **required** |
| `GEMINI_EMBEDDING_MODEL` | Embedding model name | `gemini-embedding-001` |
| `GEMINI_LLM_MODEL` | Generative model name | `gemini-3.5-flash` |
| `GEMINI_EMBEDDING_DIMENSION` | Output vector size | `768` |
| `POSTGRES_HOST` | PostgreSQL host | `localhost` |
| `POSTGRES_PORT` | PostgreSQL port | `5432` |
| `POSTGRES_DB` | Database name | `belagavi_tourism_ai` |
| `POSTGRES_USER` | Database user | `belagavi_user` |
| `POSTGRES_PASSWORD` | Database password | **required** |
| `RAG_TOP_K` | Documents retrieved per query | `5` |
| `RAG_SIMILARITY_THRESHOLD` | Min cosine similarity (0–1) | `0.3` |

Get a free Gemini API key: https://aistudio.google.com/app/apikey

---

## Database Schema

```sql
-- pgvector extension (auto-enabled on startup)
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE destinations (
    id               SERIAL PRIMARY KEY,
    place_id         INTEGER UNIQUE NOT NULL,  -- matches data.js 'id'
    name             VARCHAR(255) NOT NULL,
    category         VARCHAR(100),
    city             VARCHAR(100),
    description      TEXT,
    history          TEXT,
    architecture     TEXT,
    famous_features  TEXT,
    best_time        VARCHAR(255),
    entry_fee        VARCHAR(100),
    visit_duration   VARCHAR(100),
    how_to_reach     TEXT,
    local_tips       TEXT,
    detailed_history TEXT,
    transport_summary TEXT,
    folder_name      VARCHAR(100),
    lat              DOUBLE PRECISION,
    lon              DOUBLE PRECISION,
    document_text    TEXT,        -- concatenated text used for embedding
    embedding        vector(768), -- Gemini gemini-embedding-001, 768-dim
    created_at       TIMESTAMPTZ DEFAULT now(),
    updated_at       TIMESTAMPTZ DEFAULT now()
);
-- No vector index: 31 rows — sequential scan is faster than ANN overhead.
-- Add HNSW index when row count exceeds ~1,000.
```

---

## Local Development Setup

### Option A — Direct Python (without Docker)

```bash
# 1. Prerequisites: PostgreSQL with pgvector installed
#    On Windows: use pgvector/pgvector Docker image or install the extension manually

# 2. Navigate to backend directory
cd backend/

# 3. Create and activate virtual environment
python -m venv venv
venv\Scripts\activate        # Windows
# source venv/bin/activate   # Linux/Mac

# 4. Install dependencies
pip install -r requirements.txt

# 5. Create .env from template
copy .env.example .env       # Windows
# cp .env.example .env       # Linux/Mac
# Edit .env with your GEMINI_API_KEY and POSTGRES_* values

# 6. Start the API (tables auto-created on startup)
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### Option B — Docker Compose (recommended)

```bash
# 1. Navigate to backend directory
cd backend/

# 2. Create .env from template
copy .env.example .env       # Windows
# Edit .env with your values

# 3. Start both PostgreSQL and FastAPI
docker compose up -d

# 4. Check logs
docker compose logs -f api

# 5. Verify health
curl http://localhost:8000/api/health
```

---

## Data Ingestion

After the API is running and the database is initialised:

```bash
cd backend/

# With direct Python:
python -m scripts.ingest

# With Docker (project root mounted at /project):
docker compose exec api python -m scripts.ingest
```

The ingestion script:
1. Reads `public/static/data.js` (31 destinations, **does NOT modify it**).
2. Constructs rich document text per destination.
3. Calls `gemini-embedding-001` to generate 768-dim embeddings.
4. Upserts into PostgreSQL (safe to re-run).

Expected output:
```
Ingestion complete!
  Destinations processed : 31
  Successfully indexed   : 31
  Errors (skipped)       : 0
  Embedding model        : gemini-embedding-001
  Embedding dimension    : 768
```

---

## API Endpoints

### GET /api/health
```json
{"status": "ok", "version": "1.0.0", "service": "Belagavi Tourism AI"}
```

### POST /api/search
Natural-language semantic search.

Request:
```json
{
  "query": "waterfalls near Belagavi",
  "limit": 5,
  "category": null
}
```

Response:
```json
{
  "query": "waterfalls near Belagavi",
  "total": 3,
  "results": [
    {
      "place_id": 6,
      "name": "Gokak Falls",
      "category": "Waterfall",
      "city": "Gokak",
      "description": "...",
      "best_time": "July to October",
      "entry_fee": "Free",
      "visit_duration": "2 Hours",
      "folder_name": "gokak",
      "lat": 16.1917,
      "lon": 74.7765,
      "similarity": 0.9312
    }
  ]
}
```

### POST /api/chat
RAG-powered tourism assistant.

Request:
```json
{
  "message": "I have 5 hours and want to visit historical places",
  "history": []
}
```

Response:
```json
{
  "answer": "Based on the Belagavi tourism information available...",
  "destinations": [
    {
      "place_id": 1,
      "name": "Belagavi Fort",
      "category": "Fort",
      "city": "Belagavi",
      "entry_fee": "Free",
      "visit_duration": "2 Hours",
      "best_time": "Year-round",
      "folder_name": "belagavi_fort",
      "lat": 15.8589,
      "lon": 74.5228,
      "reason": "A historic fort with 800+ years of history, ideal for a 2-hour visit."
    }
  ],
  "sources": ["Belagavi Fort", "Yellur Fort"],
  "retrieved_count": 5
}
```

**Important**: All deterministic fields (`entry_fee`, `lat`, `lon`, `visit_duration`) in the
response come from the **database record**, not from the LLM. The LLM only generates
the `answer` and `reason` text fields.

---

## RAG Flow

```
User question
    ↓
FastAPI /api/chat
    ↓
Embed query (gemini-embedding-001, 768-dim)
    ↓
pgvector cosine similarity search (sequential scan, 31 rows)
    ↓
Top-5 retrieved Destination records
    ↓
context_builder: format as numbered DESTINATION blocks
    ↓
SYSTEM_PROMPT + context + user message
    ↓
Gemini gemini-3.5-flash (structured JSON output)
    ↓
Parse JSON response
    ↓
Filter: remove any place_id not in retrieved context (anti-hallucination)
    ↓
Overlay deterministic fields from DB records
    ↓
Return ChatResponse
```

---

## Running Tests

```bash
cd backend/
pip install -r requirements.txt

# Run all unit tests (no real API or DB required)
pytest tests/ -v

# Run with coverage
pytest tests/ -v --tb=short
```

Tests are designed to run without a real Gemini API key or PostgreSQL instance.
Integration tests (marked `@pytest.mark.integration`) require real credentials and are
not part of the default test run.

---

## Semantic Search Test Examples

After ingestion, test these queries:

```bash
curl -X POST http://localhost:8000/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "waterfalls"}'
# Expected: Gokak Falls, Godchinamalaki Falls

curl -X POST http://localhost:8000/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "quiet historical places"}'
# Expected: Belagavi Fort, Yellur Fort, temples

curl -X POST http://localhost:8000/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "family-friendly places"}'
# Expected: accessible destinations with low/free entry

curl -X POST http://localhost:8000/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "places for photography"}'
# Expected: scenic locations

curl -X POST http://localhost:8000/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "historical forts in Belagavi"}'
# Expected: Belagavi Fort, Yellur Fort, Bhimagad Fort
```

---

## Security

- API keys stored in `.env` only — never in source code, HTML, or Android.
- `.env` is in `.gitignore` — never committed.
- `/api/search` and `/api/chat` are public (only expose public tourism information).
- Firebase credentials remain in Firebase — this backend has no access to Firebase.
- No user PII is stored in PostgreSQL.
