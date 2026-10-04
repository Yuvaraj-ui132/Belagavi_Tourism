package com.belagavi.tourism.data.repository

import com.belagavi.tourism.data.model.AiChatMessage
import com.belagavi.tourism.data.model.AiChatSession
import com.belagavi.tourism.data.model.ChatMessageDto
import com.belagavi.tourism.data.model.ChatRequestDto
import com.belagavi.tourism.data.model.ChatResponseDto
import com.belagavi.tourism.data.model.DestinationRecommendationDto
import com.belagavi.tourism.data.model.WebSourceDto
import com.belagavi.tourism.data.network.AiApiService
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import com.belagavi.tourism.data.model.Place
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val aiApiService: AiApiService,
    private val firestore: FirebaseFirestore
) {

    // ─────────────────────────────────────────────────────────────
    // IN-MEMORY ACTIVE SESSION CACHE (SURVIVES SCREEN NAVIGATION)
    // ─────────────────────────────────────────────────────────────
    private var cachedActiveChatId: String? = null
    private var cachedMessages: List<AiChatMessage> = emptyList()
    private var cachedPlace: Place? = null

    fun getActiveChatId(): String? = cachedActiveChatId
    fun getActiveMessages(): List<AiChatMessage> = cachedMessages
    fun getActivePlace(): Place? = cachedPlace

    fun setActiveSession(chatId: String?, messages: List<AiChatMessage>, place: Place? = null) {
        cachedActiveChatId = chatId
        cachedMessages = messages
        cachedPlace = place
    }

    fun clearActiveSession() {
        cachedActiveChatId = null
        cachedMessages = emptyList()
        cachedPlace = null
    }

    fun hasActiveSession(): Boolean {
        // True if there is an active session in memory
        return cachedActiveChatId != null && cachedMessages.isNotEmpty()
    }

    // ─────────────────────────────────────────────────────────────
    // API CALLS
    // ─────────────────────────────────────────────────────────────

    sealed class NetworkResult<out T> {
        data class Success<out T>(val data: T) : NetworkResult<T>()
        data class Error(val message: String, val statusCode: Int? = null) : NetworkResult<Nothing>()
    }

    suspend fun sendChatMessage(
        message: String,
        history: List<ChatMessageDto>
    ): NetworkResult<ChatResponseDto> {
        return try {
            val request = ChatRequestDto(
                message = message,
                history = history.takeLast(8)
            )
            val response = aiApiService.sendChatMessage(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    NetworkResult.Success(body)
                } else {
                    NetworkResult.Error("Received empty response from AI server.")
                }
            } else {
                val code = response.code()
                val errorMsg = when (code) {
                    422 -> "Your message could not be processed. Please rephrase and try again."
                    503 -> "AI service is temporarily busy. Please try again in a moment."
                    in 500..599 -> "AI server experienced an error ($code). Please try again shortly."
                    else -> "AI request failed with status $code."
                }
                NetworkResult.Error(errorMsg, statusCode = code)
            }
        } catch (e: SocketTimeoutException) {
            NetworkResult.Error("The request timed out. The AI assistant may be busy — please try again.")
        } catch (e: UnknownHostException) {
            NetworkResult.Error("No internet connection detected. Please check your network and try again.")
        } catch (e: IOException) {
            NetworkResult.Error("Network error: ${e.localizedMessage ?: "Unable to contact server."}")
        } catch (e: Exception) {
            NetworkResult.Error("An unexpected error occurred: ${e.localizedMessage ?: "Please try again."}")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // TEXT CLEANING & INTENT CLASSIFICATION (PORTED FROM WEB)
    // ─────────────────────────────────────────────────────────────

    fun stripInternalIds(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace(Regex("""[^\S\r\n]*\(\s*ID:\s*\d+\s*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^([^\S\r\n]*)\(\s*ID:\s*\d+\s*\)[^\S\r\n]*""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)), "$1")
            .replace(Regex("""\[\s*WEB\s*SOURCE\s*\d+\s*(?:,\s*WEB\s*SOURCE\s*\d+\s*)*\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\(\s*SOURCE\s*[A-Z]\s*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bSOURCE\s*[A-Z]\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }

    fun generateChatTitle(message: String): String {
        if (message.isBlank()) return "New Conversation"
        var text = message.trim()
        val cleaned = text
            .replace(Regex("""^(can you\s+)?(tell me about|what is|what are|where is|where are|how to reach|how do i reach|how can i go to|show me|give me|i want to know about|do you know about|what about|any info on|information about)\s+(the\s+)?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(is|are)\s+([a-z0-9\s]+)\s+(open today|open now|open\??)$""", RegexOption.IGNORE_CASE), "$2 opening hours")
            .replace(Regex("""^(what is the\s+)?(entry fee|ticket price|timings?|opening hours?)\s+(for|of)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(what are\s+)?(the\s+)?best\s+""", RegexOption.IGNORE_CASE), "Best ")
            .replace(Regex("""\?+$"""), "")
            .trim()

        val finalTitle = cleaned.ifBlank { text }
        val capitalized = finalTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        return if (capitalized.length > 45) {
            capitalized.take(42).trim() + "..."
        } else {
            capitalized
        }
    }

    fun shouldShowRecommendations(userQuery: String, data: ChatResponseDto): Boolean {
        if (userQuery.isBlank() || data.safeDestinations.isEmpty()) return false
        val q = userQuery.trim().lowercase(Locale.ROOT)

        // 1. Explicit Negation / No-Recommendation Intent
        val negationPatterns = listOf(
            Regex("""\bdon'?t\s+recommend\b"""),
            Regex("""\bdo\s+not\s+recommend\b"""),
            Regex("""\bdon'?t\s+suggest\b"""),
            Regex("""\bdo\s+not\s+suggest\b"""),
            Regex("""\bno\s+recommendations?\b"""),
            Regex("""\bno\s+suggestions?\b"""),
            Regex("""\bwithout\s+recommendations?\b"""),
            Regex("""\bwithout\s+suggestions?\b"""),
            Regex("""\bstop\s+recommending\b"""),
            Regex("""\bstop\s+suggesting\b"""),
            Regex("""\bjust\s+answer\b"""),
            Regex("""\b(?:i\s+)?don'?t\s+want\s+(?:any\s+)?(?:recommendations?|suggestions?|places)\b"""),
            Regex("""\b(?:i\s+)?do\s+not\s+want\s+(?:any\s+)?(?:recommendations?|suggestions?|places)\b"""),
            Regex("""\bdon'?t\s+(?:show|give)\s+(?:me\s+)?(?:places|recommendations?|suggestions?)\b"""),
            Regex("""\bdo\s+not\s+(?:show|give)\s+(?:me\s+)?(?:places|recommendations?|suggestions?)\b"""),
            Regex("""\bnot?\s+(?:looking\s+for|interested\s+in)\s+recommendations?\b""")
        )
        for (pattern in negationPatterns) {
            if (pattern.containsMatchIn(q)) return false
        }

        // 2. Local utility / emergency / medical queries should not show tourist cards
        val servicePatterns = listOf(
            Regex("""\b(?:hospital|hospitals|clinic|clinics|doctor|doctors|medical|pharmacy|chemist|medicine|ambulance|emergency|atm|bank|police|petrol|fuel|mechanic)\b""")
        )
        for (pattern in servicePatterns) {
            if (pattern.containsMatchIn(q)) return false
        }

        // 3. Weather queries should not show tourist cards
        if (Regex("""\b(?:weather|temperature|forecast|rain|climate|humidity)\b""").containsMatchIn(q)) {
            return false
        }

        // If the query asks about a specific destination (e.g. "Tell me about Belagavi Fort" or "Is Belagavi Fort open now?"),
        // and that destination is in the response, show the destination card!
        val destNames = data.safeDestinations.map { it.safeName.lowercase(Locale.ROOT) }
        val matchesSpecificDest = destNames.any { name -> name.isNotBlank() && q.contains(name) }
        if (matchesSpecificDest) {
            return true
        }

        // 4. Recommendation & Discovery Intent Triggers
        val discoveryPatterns = listOf(
            Regex("""\brecommend\b"""),
            Regex("""\brecommendations?\b"""),
            Regex("""\bsuggest\b"""),
            Regex("""\bsuggestions?\b"""),
            Regex("""\bwhich\s+(?:places?|destinations?|spots?|attractions?|waterfalls?|temples?|forts?|sanctuar(?:y|ies))\b"""),
            Regex("""\bwhich\s+(?:one|waterfall|temple|fort|sanctuary|place)\s+should\s+(?:i|we)\s+visit\b"""),
            Regex("""\bwhat\s+(?:are\s+the\s+)?best\s+(?:places?|destinations?|spots?|waterfalls?|temples?|forts?|sanctuar(?:y|ies))\b"""),
            Regex("""\bbest\s+places?\b"""),
            Regex("""\btop\s+places?\b"""),
            Regex("""\bplaces?\s+to\s+visit\b"""),
            Regex("""\bspots?\s+to\s+visit\b"""),
            Regex("""\bdestinations?\s+to\s+visit\b"""),
            Regex("""\battractions?\s+to\s+visit\b"""),
            Regex("""\bwhere\s+(?:should|can|could|to)\s+(?:i|we|one)\s+(?:go|visit|travel)\b"""),
            Regex("""\bwhere\s+to\s+go\b"""),
            Regex("""\bhidden\s+gems?\b"""),
            Regex("""\boffbeat\s+places?\b"""),
            Regex("""\bweekend\s+trip\b"""),
            Regex("""\bday\s+trip\b"""),
            Regex("""\btrip\s+ideas?\b"""),
            Regex("""\bitinerary\b"""),
            Regex("""\bplan\s+(?:a|my|our)\s+trip\b"""),
            Regex("""\bplan\s+(?:an?\s+)?itinerary\b"""),
            Regex("""\bwhat\s+(?:else|other\s+places)\s+can\s+i\s+visit\b"""),
            Regex("""\bwhat\s+other\s+places\b"""),
            Regex("""\bother\s+places\b"""),
            Regex("""\bnearby\s+places\b"""),
            Regex("""\bplaces\s+nearby\b"""),
            Regex("""\bplaces\s+around\b"""),
            Regex("""\balternatives?\s+(?:to|for)\b"""),
            Regex("""\bplaces\s+like\b"""),
            Regex("""\bgood\s+places?\s+for\b"""),
            Regex("""\bthings\s+to\s+do\b"""),
            Regex("""\bwhat\s+to\s+see\b"""),
            Regex("""\bwhat\s+to\s+visit\b"""),
            Regex("""\b(?:waterfalls?|temples?|forts?|sanctuar(?:y|ies)|lakes?|dams?|gardens?)\s+(?:in|near|around|across)\s+belagavi\b""")
        )
        for (pattern in discoveryPatterns) {
            if (pattern.containsMatchIn(q)) return true
        }

        return false
    }

    // ─────────────────────────────────────────────────────────────
    // FIRESTORE HISTORY PERSISTENCE (MATCHES WEB EXACTLY)
    // users/{uid}/ai_chats/{chatId}/messages/{msgId}
    // ─────────────────────────────────────────────────────────────

    suspend fun loadUserChatSessions(uid: String): List<AiChatSession> {
        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("ai_chats")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.map { doc ->
                val title = doc.getString("title") ?: "Conversation"
                val createdAtTs = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                val updatedAtTs = doc.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
                AiChatSession(
                    id = doc.id,
                    title = title,
                    createdAt = createdAtTs,
                    updatedAt = updatedAtTs
                )
            }
        } catch (e: Exception) {
            // Fallback: without orderBy if index is building
            try {
                val snapshot = firestore.collection("users")
                    .document(uid)
                    .collection("ai_chats")
                    .get()
                    .await()

                snapshot.documents.map { doc ->
                    val title = doc.getString("title") ?: "Conversation"
                    val createdAtTs = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                    val updatedAtTs = doc.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
                    AiChatSession(
                        id = doc.id,
                        title = title,
                        createdAt = createdAtTs,
                        updatedAt = updatedAtTs
                    )
                }.sortedByDescending { it.updatedAt }
            } catch (fallbackEx: Exception) {
                emptyList()
            }
        }
    }

    suspend fun loadChatMessages(uid: String, chatId: String): List<AiChatMessage> {
        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("ai_chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()

            val rawList = snapshot.documents.map { doc ->
                val role = doc.getString("role") ?: "assistant"
                val content = doc.getString("content") ?: ""
                val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()

                val rawDests = doc.get("destinations") as? List<Map<String, Any>>
                val destinations = rawDests?.map { map ->
                    DestinationRecommendationDto(
                        place_id = (map["place_id"] as? Number)?.toInt() ?: 0,
                        name = map["name"] as? String ?: "",
                        category = map["category"] as? String,
                        city = map["city"] as? String,
                        entry_fee = map["entry_fee"] as? String,
                        visit_duration = map["visit_duration"] as? String,
                        best_time = map["best_time"] as? String,
                        folder_name = map["folder_name"] as? String,
                        lat = (map["lat"] as? Number)?.toDouble(),
                        lon = (map["lon"] as? Number)?.toDouble(),
                        reason = map["reason"] as? String ?: ""
                    )
                } ?: emptyList()

                val rawSources = doc.get("web_sources") as? List<Map<String, Any>>
                val webSources = rawSources?.map { map ->
                    WebSourceDto(
                        title = map["title"] as? String ?: "",
                        url = map["url"] as? String ?: "",
                        domain = map["domain"] as? String ?: ""
                    )
                } ?: emptyList()

                AiChatMessage(
                    id = doc.id,
                    role = role,
                    content = content,
                    timestamp = timestamp,
                    destinations = destinations,
                    webSources = webSources
                )
            }
            if (rawList.isNotEmpty() && rawList.first().role == "user") {
                val defaultGreeting = AiChatMessage(
                    id = "greeting_intro",
                    role = "assistant",
                    content = "**Hello! I'm your Belagavi Tourism AI Assistant.**\n\nI can help you discover waterfalls, forts, temples, wildlife sanctuaries, and more across the Belagavi district.\n\nTry one of the suggestions above, or ask me anything about Belagavi tourism!"
                )
                listOf(defaultGreeting) + rawList
            } else {
                rawList
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createChatSession(uid: String, chatId: String, title: String) {
        val chatDoc = firestore.collection("users")
            .document(uid)
            .collection("ai_chats")
            .document(chatId)

        val data = mapOf(
            "title" to title,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        chatDoc.set(data).await()
    }

    suspend fun saveUserMessage(
        uid: String,
        chatId: String,
        content: String
    ) {
        val msgId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}"
        val msgDoc = firestore.collection("users")
            .document(uid)
            .collection("ai_chats")
            .document(chatId)
            .collection("messages")
            .document(msgId)

        val msgData = mapOf(
            "role" to "user",
            "content" to content,
            "timestamp" to FieldValue.serverTimestamp()
        )
        msgDoc.set(msgData).await()

        // Update chat updatedAt
        firestore.collection("users")
            .document(uid)
            .collection("ai_chats")
            .document(chatId)
            .update("updatedAt", FieldValue.serverTimestamp())
            .await()
    }

    suspend fun saveAssistantMessage(
        uid: String,
        chatId: String,
        content: String,
        destinations: List<DestinationRecommendationDto>,
        webSources: List<WebSourceDto>
    ) {
        val msgId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}"
        val msgDoc = firestore.collection("users")
            .document(uid)
            .collection("ai_chats")
            .document(chatId)
            .collection("messages")
            .document(msgId)

        val msgData = mutableMapOf<String, Any>(
            "role" to "assistant",
            "content" to content,
            "timestamp" to FieldValue.serverTimestamp()
        )

        if (destinations.isNotEmpty()) {
            msgData["destinations"] = destinations.map { d ->
                mapOf(
                    "place_id" to d.safePlaceId,
                    "name" to d.safeName,
                    "category" to (d.category ?: ""),
                    "city" to (d.city ?: ""),
                    "entry_fee" to (d.entry_fee ?: ""),
                    "visit_duration" to (d.visit_duration ?: ""),
                    "best_time" to (d.best_time ?: ""),
                    "folder_name" to (d.folder_name ?: ""),
                    "lat" to (d.lat ?: 0.0),
                    "lon" to (d.lon ?: 0.0),
                    "reason" to d.safeReason
                )
            }
        }

        if (webSources.isNotEmpty()) {
            msgData["web_sources"] = webSources.map { s ->
                mapOf(
                    "title" to s.safeTitle,
                    "url" to s.safeUrl,
                    "domain" to s.safeDomain
                )
            }
        }

        msgDoc.set(msgData).await()

        // Update parent chat updatedAt
        firestore.collection("users")
            .document(uid)
            .collection("ai_chats")
            .document(chatId)
            .update("updatedAt", FieldValue.serverTimestamp())
            .await()
    }

    suspend fun deleteChatSession(uid: String, chatId: String) {
        val msgsRef = firestore.collection("users")
            .document(uid)
            .collection("ai_chats")
            .document(chatId)
            .collection("messages")

        val snapshot = msgsRef.get().await()
        for (doc in snapshot.documents) {
            doc.reference.delete().await()
        }

        firestore.collection("users")
            .document(uid)
            .collection("ai_chats")
            .document(chatId)
            .delete()
            .await()
    }
}
