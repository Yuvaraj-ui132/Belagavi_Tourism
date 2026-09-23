"""
RAG context builder.

Converts a list of retrieved Destination ORM objects into a structured
text block that is injected into the LLM system prompt.

Design rules:
- Only retrieved facts are included.
- The LLM is given numbers for each document so it can reference them.
- Deterministic fields (entry_fee, lat, lon, etc.) are explicitly labeled
  so the LLM can quote them verbatim — it must NOT re-invent these values.
"""

from __future__ import annotations

from typing import List

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
5. If the retrieved context does not contain information needed to answer a question, clearly state: "This information is not available in the Belagavi tourism database."
6. When recommending destinations, use ONLY the destinations present in the retrieved context.
7. Clearly distinguish: retrieved verified facts vs. your general travel recommendations.
8. Always include the destination ID (shown as "ID:" in the context) when mentioning a destination.

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
