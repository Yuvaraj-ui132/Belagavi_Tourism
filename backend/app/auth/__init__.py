"""auth package — Firebase ID token verification and rate limiting."""

from app.auth.firebase import require_firebase_user
from app.auth.rate_limiter import check_rate_limit

__all__ = ["require_firebase_user", "check_rate_limit"]
