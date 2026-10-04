"""
SQLAlchemy ORM model for API rate limits.
Used by app.auth.rate_limiter to persist user request counts across serverless instances.
"""

from __future__ import annotations

from datetime import datetime
from sqlalchemy import DateTime, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.database import Base


class ApiRateLimit(Base):
    __tablename__ = "api_rate_limits"

    user_id: Mapped[str] = mapped_column(String(128), primary_key=True, index=True)
    request_count: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
    window_start: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False,
    )
