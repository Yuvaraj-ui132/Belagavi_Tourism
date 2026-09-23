"""
verify_supabase.py — Read-only Supabase / PostgreSQL verification script.

Checks (in order):
  1. Basic connectivity          — SELECT 1
  2. pgvector extension          — pg_extension lookup
  3. destinations table exists   — pg_catalog lookup (no lock, no scan)
  4. Row count                   — informational SELECT COUNT(*)

This script is SAFE:
  - No CREATE / DROP / INSERT / UPDATE / DELETE
  - No schema migrations
  - No data modifications
  - Read-only SELECT queries only

Exit codes:
  0 — All required checks passed
  1 — One or more checks failed

Usage (from backend/ directory with venv activated):
  python scripts/verify_supabase.py

To verify Supabase specifically, set DATABASE_URL in backend/.env first:
  DATABASE_URL=postgresql+asyncpg://postgres.<ref>:<pass>@<host>:5432/postgres
"""

from __future__ import annotations

import asyncio
import os
import re
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Path setup — allow running from anywhere inside the project
# ---------------------------------------------------------------------------
BACKEND_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(BACKEND_DIR))

# Load .env before importing app modules so Settings picks up DATABASE_URL
from dotenv import load_dotenv  # noqa: E402

_env_file = BACKEND_DIR / ".env"
if _env_file.exists():
    load_dotenv(_env_file, override=False)  # os.environ already-set vars take precedence


# ---------------------------------------------------------------------------
# Result helpers
# ---------------------------------------------------------------------------

PASS_STR = "PASS"
FAIL_STR = "FAIL"
INFO_STR = "INFO"

_results: list = []  # list of (label, passed)


def _report(label: str, passed: bool, detail: str = "") -> None:
    status = PASS_STR if passed else FAIL_STR
    detail_str = f"  {detail}" if detail else ""
    print(f"  [{status}] {label}{detail_str}")
    _results.append((label, passed))


def _redact(url: str) -> str:
    """Replace the password in a DSN with *** for safe display."""
    return re.sub(r"(:)([^:@/]+)(@)", r"\1***\3", url, count=1)


# ---------------------------------------------------------------------------
# Verification checks (all read-only)
# ---------------------------------------------------------------------------

async def _run_checks(url: str) -> None:
    """Connect to PostgreSQL and run read-only checks.

    Uses urllib.parse to extract credentials from the URL and passes them as
    keyword arguments to asyncpg.connect().  This bypasses asyncpg's own URL
    parser, which silently truncates passwords that contain spaces or other
    characters that are valid in a percent-encoded URL but not in a bare string.
    SQLAlchemy (used by the app) percent-decodes correctly, so the app is fine.
    """
    import asyncpg
    from urllib.parse import urlparse, unquote

    # Normalise scheme so urlparse understands it
    normalised = url
    for prefix in ("postgresql+asyncpg://", "postgres+asyncpg://", "postgres://"):
        if normalised.startswith(prefix):
            normalised = "postgresql://" + normalised[len(prefix):]
            break

    parsed = urlparse(normalised)

    # Decode percent-encoding in each component (handles %20, %40, etc.)
    host     = parsed.hostname or "localhost"
    port     = parsed.port or 5432
    database = (parsed.path or "/postgres").lstrip("/") or "postgres"
    user     = unquote(parsed.username or "postgres")
    password = unquote(parsed.password or "")

    print(f"\n  Connecting to: {user}@{host}:{port}/{database}\n")

    try:
        conn = await asyncpg.connect(
            host=host,
            port=port,
            database=database,
            user=user,
            password=password,
            timeout=15,
        )
    except Exception as exc:
        _report("PostgreSQL connectivity (SELECT 1)", False, f"— {exc}")
        _report("pgvector extension installed", False, "— skipped (no connection)")
        _report("destinations table exists", False, "— skipped (no connection)")
        _report("Row count (informational)", False, "— skipped (no connection)")
        return

    try:
        # ------------------------------------------------------------------
        # Check 1 — Basic connectivity
        # ------------------------------------------------------------------
        try:
            await conn.fetchval("SELECT 1")
            _report("PostgreSQL connectivity (SELECT 1)", True)
        except Exception as exc:
            _report("PostgreSQL connectivity (SELECT 1)", False, f"— {exc}")

        # ------------------------------------------------------------------
        # Check 2 — pgvector extension
        # ------------------------------------------------------------------
        try:
            row = await conn.fetchval(
                "SELECT extname FROM pg_extension WHERE extname = 'vector'"
            )
            if row == "vector":
                _report("pgvector extension installed", True)
            else:
                _report(
                    "pgvector extension installed",
                    False,
                    "— extension 'vector' not found in pg_extension.\n"
                    "         Enable it in Supabase: Dashboard -> Database -> Extensions -> vector",
                )
        except Exception as exc:
            _report("pgvector extension installed", False, f"— {exc}")

        # ------------------------------------------------------------------
        # Check 3 — destinations table exists
        # ------------------------------------------------------------------
        try:
            oid = await conn.fetchval(
                "SELECT oid FROM pg_catalog.pg_class "
                "WHERE relname = 'destinations' AND relnamespace = 'public'::regnamespace"
            )
            if oid is not None:
                _report("destinations table exists", True)
            else:
                _report(
                    "destinations table exists",
                    False,
                    "— table 'public.destinations' not found.\n"
                    "         Run scripts/ingest.py (pointed at Supabase) to create it.",
                )
        except Exception as exc:
            _report("destinations table exists", False, f"— {exc}")

        # ------------------------------------------------------------------
        # Check 4 — Row count (informational, no threshold)
        # ------------------------------------------------------------------
        try:
            if _results[-1][1]:  # destinations table PASS?
                count = await conn.fetchval("SELECT COUNT(*) FROM destinations")
                _report("Row count (informational)", True, f"— {count} rows in destinations")
            else:
                _report("Row count (informational)", False, "— skipped (table missing)")
        except Exception as exc:
            _report("Row count (informational)", False, f"— {exc}")

    finally:
        await conn.close()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    # Import Settings AFTER dotenv is loaded
    from app.config import get_settings

    try:
        settings = get_settings()
    except Exception as exc:
        print(f"\n  [FAIL] Failed to load settings — {exc}\n")
        sys.exit(1)

    async_url = settings.database_url
    source = (
        "DATABASE_URL override (Supabase / external PG)"
        if settings.database_url_override
        else "POSTGRES_* parts (local Docker default)"
    )

    print("\n" + "=" * 62)
    print("  Belagavi Tourism AI — Database Verification")
    print("=" * 62)
    print(f"\n  URL source : {source}")

    asyncio.run(_run_checks(async_url))

    print()
    print("=" * 62)

    required = [(label, passed) for label, passed in _results if "informational" not in label.lower()]
    all_passed = all(p for _, p in required)

    if all_passed:
        print(f"  Result: {PASS_STR}  — All required checks passed.")
        print()
        if not settings.database_url_override:
            print("  [HINT] DATABASE_URL is not set — verified LOCAL Docker PostgreSQL.")
            print("         To verify Supabase, uncomment DATABASE_URL in backend/.env")
            print("         and paste your Supabase connection string.")
    else:
        failed_labels = [label for label, passed in required if not passed]
        print(f"  Result: {FAIL_STR}  — {len(failed_labels)} check(s) failed: {', '.join(failed_labels)}")

    print("=" * 62 + "\n")
    sys.exit(0 if all_passed else 1)


if __name__ == "__main__":
    main()
