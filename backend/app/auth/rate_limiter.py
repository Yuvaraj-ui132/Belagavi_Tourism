"""
Production-safe Rate Limiter for Vercel Serverless / PostgreSQL.

Enforces per-user rate limits on /api/chat based on the verified Firebase UID.
Uses an atomic PostgreSQL table (api_rate_limits) with row-level locking (FOR UPDATE)
so limits are accurately tracked across all concurrent serverless instances.
"""

from __future__ import annotations

import logging
from typing import Optional

from fastapi import HTTPException, status
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings

logger = logging.getLogger(__name__)


_table_ensured = False


async def check_rate_limit(uid: str, db: AsyncSession) -> None:
    """
    Check and increment the rate limit counter for the authenticated Firebase UID.

    Raises:
        HTTPException(429): If the user has exceeded their allowed quota.
    """
    global _table_ensured
    settings = get_settings()
    if not settings.rate_limit_enabled:
        return

    limit = settings.rate_limit_per_minute
    window = settings.rate_limit_window_seconds

    try:
        # 1. Fetch current record for user with row lock
        res = await db.execute(text("""
            SELECT request_count, EXTRACT(EPOCH FROM (NOW() - window_start)) AS elapsed
            FROM api_rate_limits
            WHERE user_id = :uid
            FOR UPDATE;
        """), {"uid": uid})
        row = res.fetchone()

        if row is None:
            # First request in window
            await db.execute(text("""
                INSERT INTO api_rate_limits (user_id, request_count, window_start)
                VALUES (:uid, 1, NOW())
                ON CONFLICT (user_id) DO UPDATE
                SET request_count = 1, window_start = NOW();
            """), {"uid": uid})
            await db.commit()
            return

        request_count = int(row[0])
        elapsed = float(row[1] or 0.0)

        # 3. Check if window has expired
        if elapsed >= window:
            await db.execute(text("""
                UPDATE api_rate_limits
                SET request_count = 1, window_start = NOW()
                WHERE user_id = :uid;
            """), {"uid": uid})
            await db.commit()
            return

        # 4. Check if limit exceeded
        if request_count >= limit:
            retry_after = max(1, int(window - elapsed))
            logger.warning(
                "Rate limit exceeded (uid_len=%d, count=%d, limit=%d)",
                len(uid), request_count, limit
            )
            await db.rollback()
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail=f"Too many requests. Please wait {retry_after} seconds before trying again.",
                headers={
                    "Retry-After": str(retry_after),
                    "X-RateLimit-Limit": str(limit),
                    "X-RateLimit-Remaining": "0",
                    "X-RateLimit-Reset": str(retry_after),
                },
            )

        # 5. Increment counter
        await db.execute(text("""
            UPDATE api_rate_limits
            SET request_count = request_count + 1
            WHERE user_id = :uid;
        """), {"uid": uid})
        await db.commit()

    except HTTPException:
        raise
    except Exception as exc:
        logger.warning("Rate limiter database check error: %s", type(exc).__name__)
        try:
            await db.rollback()
        except Exception:
            pass
        return
