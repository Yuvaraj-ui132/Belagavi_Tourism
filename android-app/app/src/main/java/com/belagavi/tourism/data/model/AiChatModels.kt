package com.belagavi.tourism.data.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * Request payload sent to POST /api/chat matching backend ChatRequest schema.
 */
data class ChatRequestDto(
    @SerializedName("message") val message: String,
    @SerializedName("history") val history: List<ChatMessageDto> = emptyList()
)

/**
 * Individual turn in conversation history.
 */
data class ChatMessageDto(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

/**
 * Structured response from POST /api/chat matching backend ChatResponse schema.
 */
data class ChatResponseDto(
    @SerializedName("answer") val answer: String? = "",
    @SerializedName("destinations") val destinations: List<DestinationRecommendationDto>? = emptyList(),
    @SerializedName("sources") val sources: List<String>? = emptyList(),
    @SerializedName("retrieved_count") val retrievedCount: Int? = 0,
    @SerializedName("web_sources") val webSources: List<WebSourceDto>? = emptyList(),
    @SerializedName("web_research_used") val webResearchUsed: Boolean? = false
) {
    val safeAnswer: String get() = answer ?: ""
    val safeDestinations: List<DestinationRecommendationDto> get() = destinations ?: emptyList()
    val safeWebSources: List<WebSourceDto> get() = webSources ?: emptyList()
}

/**
 * Destination recommendation overlaid with deterministic database fields.
 */
data class DestinationRecommendationDto(
    @SerializedName("place_id") val place_id: Int? = 0,
    @SerializedName("name") val name: String? = "",
    @SerializedName("category") val category: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("entry_fee") val entry_fee: String? = null,
    @SerializedName("visit_duration") val visit_duration: String? = null,
    @SerializedName("best_time") val best_time: String? = null,
    @SerializedName("folder_name") val folder_name: String? = null,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lon") val lon: Double? = null,
    @SerializedName("reason") val reason: String? = ""
) {
    val safeName: String get() = name ?: ""
    val safeReason: String get() = reason ?: ""
    val safePlaceId: Int get() = place_id ?: 0
}

/**
 * Real web source citation returned when web research is triggered.
 */
data class WebSourceDto(
    @SerializedName("title") val title: String? = "",
    @SerializedName("url") val url: String? = "",
    @SerializedName("domain") val domain: String? = ""
) {
    val safeTitle: String get() = title ?: ""
    val safeUrl: String get() = url ?: ""
    val safeDomain: String get() = domain ?: ""
}

/**
 * In-memory / UI representation of a chat message.
 */
data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val destinations: List<DestinationRecommendationDto> = emptyList(),
    val webSources: List<WebSourceDto> = emptyList(),
    val isError: Boolean = false
)

/**
 * Conversation session metadata for the history drawer.
 */
data class AiChatSession(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Suggestion chip item.
 */
data class SuggestionChip(
    val label: String,
    val query: String
)
