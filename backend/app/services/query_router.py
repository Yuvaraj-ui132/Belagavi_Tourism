"""
Query router — decides whether a user query requires web-grounded research.

Design principles:
- Deterministic: same input always gives same output.
- Testable: a pure function with no side-effects.
- Conservative: prefers triggering web research when ambiguous, because
  a missed web-research opportunity is worse than an unnecessary one.

Triggers web research for questions about:
  - Current/live information  (today, now, currently, this week...)
  - Potentially-changing data (entry fee, opening hours, contact info...)
  - Recent news or events     (latest, recent, news, festival, update...)
  - Operational status        (open now, closed, restrictions, access...)
"""

from __future__ import annotations

import re

# ---------------------------------------------------------------------------
# Keyword sets (all lowercase, matched case-insensitively)
# ---------------------------------------------------------------------------

_TEMPORAL_KEYWORDS: set[str] = {
    "today",
    "tonight",
    "now",
    "currently",
    "current",
    "right now",
    "at the moment",
    "this week",
    "this month",
    "this year",
    "this season",
    "latest",
    "recent",
    "recently",
    "new",
    "updated",
    "update",
    "upcoming",
}

_OPERATIONAL_KEYWORDS: set[str] = {
    "open",
    "closed",
    "closure",
    "open now",
    "open today",
    "is open",
    "are open",
    "is closed",
    "still open",
    "reopen",
    "reopened",
    "reopening",
    "operating",
    "operational",
    "running",
    "available",
    "accessible",
    "access",
    "restricted",
    "restriction",
    "restrictions",
    "allowed",
    "permitted",
    "permission",
    "permit",
    "permits",
    "parking",
    "facility",
    "facilities",
    "camera",
    "photography",
    "drone",
    "rules",
    "rule",
    "guidelines",
    "safety",
    "road condition",
    "road conditions",
    "route condition",
}

_PRICE_KEYWORDS: set[str] = {
    "entry fee",
    "ticket price",
    "ticket cost",
    "admission",
    "admission fee",
    "cost",
    "price",
    "charge",
    "charges",
    "free entry",
    "paid entry",
    "how much",
    "fee",
    "fees",
    "rate",
    "rates",
    "tariff",
    "ticket",
    "tickets",
}

_SCHEDULE_KEYWORDS: set[str] = {
    "opening hour",
    "opening hours",
    "closing hour",
    "closing hours",
    "open hour",
    "open hours",
    "timing",
    "timings",
    "time",
    "times",
    "schedule",
    "visiting hour",
    "visiting hours",
    "visiting time",
    "visiting times",
    "time to visit",
    "when does",
    "what time",
    "open time",
    "closing time",
}

_CONTACT_KEYWORDS: set[str] = {
    "contact",
    "phone",
    "phone number",
    "number",
    "address",
    "email",
    "website",
    "official site",
    "book",
    "booking",
    "reservation",
}

_NEWS_KEYWORDS: set[str] = {
    "news",
    "notice",
    "announcement",
    "alert",
    "event",
    "festival",
    "fair",
    "condition",
    "road condition",
    "weather",
    "flood",
    "renovation",
    "construction",
    "maintenance",
    "travel advisory",
}

_LOCAL_SERVICE_KEYWORDS: set[str] = {
    # Medical, Healthcare & Emergency
    "hospital",
    "hospitals",
    "clinic",
    "clinics",
    "doctor",
    "doctors",
    "medical",
    "medical center",
    "medical centre",
    "medical facility",
    "medical store",
    "health center",
    "health centre",
    "phc",
    "primary health centre",
    "primary health center",
    "pharmacy",
    "pharmacies",
    "chemist",
    "chemists",
    "medicine",
    "medicines",
    "first aid",
    "emergency",
    "ambulance",
    "healthcare",
    # Financial & Banking
    "atm",
    "atms",
    "cash machine",
    "bank",
    "banks",
    "money exchange",
    # Safety & Law Enforcement
    "police",
    "police station",
    "cop",
    "cops",
    # Transport & Vehicle Assistance
    "petrol",
    "petrol pump",
    "petrol bunk",
    "gas station",
    "fuel",
    "diesel",
    "ev charging",
    "ev charger",
    "mechanic",
    "puncture",
    "repair shop",
    "garage",
}

# Combined flat set of all web-trigger keywords/phrases
_ALL_WEB_TRIGGERS: frozenset[str] = frozenset(
    _TEMPORAL_KEYWORDS
    | _OPERATIONAL_KEYWORDS
    | _PRICE_KEYWORDS
    | _SCHEDULE_KEYWORDS
    | _CONTACT_KEYWORDS
    | _NEWS_KEYWORDS
    | _LOCAL_SERVICE_KEYWORDS
)


def needs_web_research(query: str) -> bool:
    """
    Return True if the query likely requires current web information.

    Args:
        query: The raw user message string.

    Returns:
        True  → trigger web research (current info probably needed).
        False → Supabase RAG is sufficient (stable tourism information).

    Examples:
        >>> needs_web_research("What is the current entry fee for Belagavi Fort?")
        True
        >>> needs_web_research("Tell me about Belagavi Fort")
        False
        >>> needs_web_research("Is Gokak Falls open today?")
        True
        >>> needs_web_research("Suggest waterfalls near Belagavi")
        False
    """
    if not query or not query.strip():
        return False

    q_stripped = query.strip()

    # Fast-path: simple greetings never require web research
    if re.match(r"^(?:hello|hi|hey|greetings|good\s+(?:morning|afternoon|evening))\b[!.? ]*$", q_stripped, re.IGNORECASE):
        return False

    q_lower = q_stripped.lower()

    for trigger in _ALL_WEB_TRIGGERS:
        # Use word-boundary-aware matching to avoid partial matches
        # e.g. "now" should not match "known" or "renewal"
        pattern = r"\b" + re.escape(trigger) + r"\b"
        if re.search(pattern, q_lower):
            return True

    return False


def classify_query(query: str) -> str:
    """
    Return a human-readable label for the query classification.
    Used for logging and debugging.

    Returns:
        "web_research" or "local_rag"
    """
    return "web_research" if needs_web_research(query) else "local_rag"
