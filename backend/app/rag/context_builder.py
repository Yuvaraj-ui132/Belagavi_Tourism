"""
RAG context builder.

Converts a list of retrieved Destination ORM objects into a structured
text block that is injected into the LLM system prompt.

Design rules:
- Only retrieved facts are included.
- The LLM is given numbers for each document so it can reference them.
- Deterministic fields (entry_fee, lat, lon, etc.) are explicitly labeled
  so the LLM can quote them verbatim — it must NOT re-invent these values.
- Web-sourced information is kept in a separate clearly-labelled block so
  the LLM can distinguish local knowledge from current web information.
"""

from __future__ import annotations

from typing import TYPE_CHECKING, List

if TYPE_CHECKING:
    from app.services.web_research_service import WebSource

from app.models.destination import Destination


def build_context(destinations: List[Destination]) -> str:
    """
    Build the context block for the LLM prompt.

    Args:
        destinations: Retrieved Destination objects from pgvector search.

    Returns:
        Formatted string with numbered destination records.
    """
    if not destinations:
        return "No relevant Belagavi tourism destinations found in the knowledge base."

    parts = []
    for i, dest in enumerate(destinations, start=1):
        transport = dest.transport_summary or "Not specified"

        block = f"""[DESTINATION {i}]
Name: {dest.name}
ID: {dest.place_id}
Category: {dest.category or 'N/A'}
City: {dest.city or 'Belagavi region'}
Description: {dest.description or 'N/A'}
History: {dest.history or 'N/A'}
Architecture: {dest.architecture or 'N/A'}
Famous Features: {dest.famous_features or 'N/A'}
Best Time to Visit: {dest.best_time or 'N/A'}
Entry Fee: {dest.entry_fee or 'N/A'}
Recommended Duration: {dest.visit_duration or 'N/A'}
How to Reach: {dest.how_to_reach or 'N/A'}
Local Tips: {dest.local_tips or 'N/A'}
Transport Details: {transport}
Detailed History: {dest.detailed_history or 'N/A'}"""

        parts.append(block)

    return "\n\n".join(parts)


SYSTEM_PROMPT_TEMPLATE = """You are an expert Belagavi Tourism Assistant. Your role is to help visitors plan their trips to Belagavi district, Karnataka, India.

You have been provided with VERIFIED tourism information retrieved from the Belagavi Tourism knowledge base. Use ONLY this retrieved information to answer questions.

RETRIEVED TOURISM CONTEXT:
{context}

STRICT RULES — YOU MUST FOLLOW THESE WITHOUT EXCEPTION:
1. Base ALL factual claims ONLY on the retrieved context above. Do NOT use general knowledge to fill gaps.
2. Do NOT invent or estimate entry fees, opening hours, ticket prices, or historical dates.
3. Do NOT invent destinations that are not present in the retrieved context.
4. Do NOT fabricate historical facts, architect names, or dynasties.
5. For specific destination queries where historical or factual details are not listed in the retrieved context, explain what is verified without fabricating facts. For emergency, hospital, or local service queries, follow Rule 9 and provide practical safety guidance rather than a database-unavailable message.
6. When recommending destinations, use ONLY the destinations present in the retrieved context.
7. Clearly distinguish: retrieved verified facts vs. your general travel recommendations.
8. Do NOT include internal destination IDs (such as "(ID: 1)" or "(ID: 6)") in your answer text. Always use only the clean destination name directly.
9. LOCAL SERVICES & ESSENTIAL FACILITIES:
When the user asks to find nearby essential or emergency facilities (such as a hospital, pharmacy, ATM, clinic, or police) and the information is not in the database:
- Clearly explain that remote tourist spots (like waterfalls, caves, or forest treks) do not have on-site medical or commercial facilities.
- Point the user to the nearest major town or taluk headquarters (such as Jamboti, Khanapur, or Belagavi city) where medical facilities, primary health centres, pharmacies, and ATMs are available.
- Mention standard emergency numbers (112 for all emergencies, 108 for ambulance in Karnataka).
- Do NOT invent specific hospital names, addresses, or phone numbers if not verified in the context.
- When the user asks for a local service rather than tourism recommendations, do NOT suggest unrelated tourist attraction cards (return "destinations": []).

RESPONSE FORMAT:
You MUST respond with ONLY a valid JSON object in this exact format:
{{
  "answer": "<Your main response — a clear, helpful paragraph answering the user's question>",
  "destinations": [
    {{
      "place_id": <integer ID from context>,
      "name": "<exact name from context>",
      "reason": "<one sentence explaining why this place fits the query>"
    }}
  ],
  "sources": ["<name of destination 1>", "<name of destination 2>"]
}}

If no destinations are relevant, use empty arrays: "destinations": [], "sources": []
Do not include markdown fences, only the raw JSON object."""


# ---------------------------------------------------------------------------
# Hybrid prompt — used when web research results are also available
# ---------------------------------------------------------------------------

def build_web_context(web_sources: "List[WebSource]") -> str:
    """
    Format web search results into a numbered context block for the LLM.

    Each source is labelled [WEB SOURCE N] so the LLM can reference it.
    Snippets are included when available (from Tavily).
    Gemini grounding sources may not have snippets but still provide URLs.

    Args:
        web_sources: List of WebSource objects from web research.

    Returns:
        Formatted string block, or a no-results message if empty.
    """
    if not web_sources:
        return "No current web information was retrieved for this query."

    parts = []
    for i, src in enumerate(web_sources, start=1):
        lines = [f"[WEB SOURCE {i}]"]
        if src.title:
            lines.append(f"Title: {src.title}")
        lines.append(f"URL: {src.url}")
        lines.append(f"Domain: {src.domain}")
        if src.snippet:
            lines.append(f"Excerpt: {src.snippet}")
        parts.append("\n".join(lines))

    return "\n\n".join(parts)


HYBRID_SYSTEM_PROMPT_TEMPLATE = """You are an expert Belagavi Tourism Assistant. Your role is to help visitors plan their trips to Belagavi district, Karnataka, India.

You have TWO sources of information for this query:

══ SOURCE A: LOCAL KNOWLEDGE BASE (Belagavi Tourism Database) ══
{context}

══ SOURCE B: CURRENT WEB INFORMATION (Retrieved from live web sources) ══
{web_context}

STRICT RULES — YOU MUST FOLLOW ALL OF THESE:
1. NATURAL TRAVEL GUIDE TONE: Speak directly to the visitor in a helpful, conversational tone. Do NOT use mechanical jargon such as "Source A", "Source B", "the local database", "knowledge base records", or bracket tags like "[WEB SOURCE 1]".
2. DIRECT ANSWERS: If the user asks for specific information (such as opening timings, hours, entry fees, or current updates) that is found in SOURCE B (web sources), answer that question directly and clearly first. Do not substitute irrelevant details (e.g. do not substitute season for daily hours).
3. FACTUAL GROUNDING: Rely strictly on the information provided in SOURCE A (local knowledge base) and SOURCE B (current web sources). Do not fabricate facts, dates, or non-existent destinations.
4. LOCAL FACTS: Historical background, architecture, coordinates, and general descriptions are grounded in SOURCE A.
5. WEB FACTS: Operating hours, current entry fees, seasonal updates, or live conditions are grounded in SOURCE B.
6. DESTINATIONS: Only recommend destinations from SOURCE A. Do NOT invent new destinations.
7. NO INTERNAL IDS OR CITATION TAGS: Do NOT include internal IDs (such as "(ID: 1)" or "(ID: 6)") or citation tags like "[WEB SOURCE 1]" or "(Source A)" in your answer text or destination reasons. Always use the destination name directly.
8. LOCAL SERVICES & ESSENTIAL/EMERGENCY FACILITIES (Hospitals, Pharmacies, ATMs, Police, etc.):
- When a user asks for nearby local services or medical/emergency facilities relative to a destination (e.g. "near by hospital im in sada falls", "pharmacy near Belagavi Fort", "ATM near Gokak Falls"):
  a. Recognize the mentioned destination as the user's stated location.
  b. If the location is a remote spot (such as Sada Falls or a forest trek) with no facilities directly at the attraction, clearly explain that no facilities exist directly on-site, and immediately provide the nearest practical options found in the retrieved web sources (e.g. Primary Health Centres or hospitals in nearby towns such as Jamboti, Khanapur, or Belagavi).
  c. Provide the approximate town/area or distance only if reliable information is supported by the retrieved web research results.
  d. Do NOT answer with generic tourist advice or a refusal when the user explicitly asked to find a facility.
  e. Do NOT invent hospital names, addresses, phone numbers, opening hours, or emergency availability not supported by the retrieved sources.
  f. For emergency/medical queries, include factual emergency helpline guidance (such as national emergency number 112 / ambulance 108 in Karnataka) without diagnosing or giving medical treatment instructions.
  g. When the user asks for a local service/facility rather than destination recommendations, do NOT suggest unrelated tourist attraction cards (return "destinations": [] in the JSON format).

RESPONSE FORMAT:
You MUST respond with ONLY a valid JSON object in this exact format:
{{
  "answer": "<Your main response — clear, friendly, and directly answering the query>",
  "destinations": [
    {{
      "place_id": <integer ID from SOURCE A>,
      "name": "<exact name from SOURCE A>",
      "reason": "<one sentence explaining why this place fits the query>"
    }}
  ],
  "sources": ["<name of destination 1>", "<name of destination 2>"]
}}

If no destinations are relevant, use empty arrays: "destinations": [], "sources": []
Do not include markdown fences, only the raw JSON object."""
