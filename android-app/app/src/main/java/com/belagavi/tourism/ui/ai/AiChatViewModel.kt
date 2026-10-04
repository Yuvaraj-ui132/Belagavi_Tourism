package com.belagavi.tourism.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belagavi.tourism.data.model.AiChatMessage
import com.belagavi.tourism.data.model.AiChatSession
import com.belagavi.tourism.data.model.ChatMessageDto
import com.belagavi.tourism.data.model.Place
import com.belagavi.tourism.data.model.SuggestionChip
import com.belagavi.tourism.data.repository.AiRepository
import com.belagavi.tourism.data.repository.PlacesRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val placesRepository: PlacesRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _messages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _chatSessions = MutableStateFlow<List<AiChatSession>>(emptyList())
    val chatSessions: StateFlow<List<AiChatSession>> = _chatSessions.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    private val _contextualPlace = MutableStateFlow<Place?>(null)
    val contextualPlace: StateFlow<Place?> = _contextualPlace.asStateFlow()

    private val _isHistoryDrawerOpen = MutableStateFlow(false)
    val isHistoryDrawerOpen: StateFlow<Boolean> = _isHistoryDrawerOpen.asStateFlow()

    // Last user message for retry capability
    private var lastUserQuery: String? = null
    private val isSubmitting = AtomicBoolean(false)

    val currentUid: String?
        get() = firebaseAuth.currentUser?.uid

    init {
        restoreOrInitializeChat()
        observeAuth()
    }

    private fun restoreOrInitializeChat() {
        if (aiRepository.hasActiveSession()) {
            _activeChatId.value = aiRepository.getActiveChatId()
            _messages.value = aiRepository.getActiveMessages()
            _contextualPlace.value = aiRepository.getActivePlace()
            val uid = currentUid
            if (uid != null) {
                loadHistorySessions(uid)
            }
            return
        }

        startNewChat()

        val uid = currentUid
        if (uid != null) {
            viewModelScope.launch {
                try {
                    val sessions = aiRepository.loadUserChatSessions(uid)
                    _chatSessions.value = sessions
                } catch (e: Exception) {
                    // Fall back
                }
            }
        }
    }

    private fun observeAuth() {
        firebaseAuth.addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid != null) {
                loadHistorySessions(uid)
            } else {
                _chatSessions.value = emptyList()
            }
        }
    }

    fun loadHistorySessions(uid: String? = currentUid) {
        val targetUid = uid ?: return
        viewModelScope.launch {
            val sessions = aiRepository.loadUserChatSessions(targetUid)
            _chatSessions.value = sessions
        }
    }

    fun setContextualPlace(placeId: Int?) {
        if (placeId == null) {
            return
        }
        val place = placesRepository.getPlaceById(placeId)
        if (place != null && _contextualPlace.value?.id != place.id) {
            _contextualPlace.value = place
            startNewChat()
        }
    }

    fun clearContext() {
        if (_contextualPlace.value != null) {
            _contextualPlace.value = null
            startNewChat()
        }
    }

    fun toggleHistoryDrawer(open: Boolean? = null) {
        _isHistoryDrawerOpen.value = open ?: !_isHistoryDrawerOpen.value
    }

    fun startNewChat() {
        aiRepository.clearActiveSession()
        val newId = "local_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        _activeChatId.value = newId
        _errorMessage.value = null
        lastUserQuery = null

        val place = _contextualPlace.value
        val initialGreeting = if (place != null) {
            "**I can help you learn more about ${place.name}.**\n\nAsk me anything about its history, timings, entry fee, transport, or travel tips. You can select one of the suggested topics below or type your own question!"
        } else {
            "**Hello! I'm your Belagavi Tourism AI Assistant.**\n\nI can help you discover waterfalls, forts, temples, wildlife sanctuaries, and more across the Belagavi district.\n\nTry one of the suggestions below, or ask me anything about Belagavi tourism!"
        }

        val greetingList = listOf(
            AiChatMessage(
                role = "assistant",
                content = initialGreeting
            )
        )
        _messages.value = greetingList
        aiRepository.setActiveSession(newId, greetingList, place)
    }

    fun loadChatSession(chatId: String) {
        val uid = currentUid ?: return
        if (_activeChatId.value == chatId && _messages.value.size > 1) return

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _activeChatId.value = chatId
            _contextualPlace.value = null // clear contextual lock when viewing an existing session

            val loadedMessages = aiRepository.loadChatMessages(uid, chatId)
            if (loadedMessages.isNotEmpty()) {
                _messages.value = loadedMessages
                aiRepository.setActiveSession(chatId, loadedMessages, null)
            } else {
                startNewChat()
            }
            _isLoading.value = false
            _isHistoryDrawerOpen.value = false
        }
    }

    fun deleteChatSession(chatId: String) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            aiRepository.deleteChatSession(uid, chatId)
            _chatSessions.value = _chatSessions.value.filter { it.id != chatId }
            if (_activeChatId.value == chatId) {
                aiRepository.clearActiveSession()
                startNewChat()
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        // Prevent duplicate/concurrent submissions atomically
        if (!isSubmitting.compareAndSet(false, true)) return

        // Check if the latest message is an identical unresponded user message
        val lastMsg = _messages.value.lastOrNull()
        if (lastMsg != null && lastMsg.role == "user" && lastMsg.content == trimmed) {
            isSubmitting.set(false)
            return
        }

        // Set loading state immediately before any async work
        _isLoading.value = true
        _errorMessage.value = null
        lastUserQuery = trimmed
        val uid = currentUid
        var chatId = _activeChatId.value ?: "local_${System.currentTimeMillis()}"
        val isFirstUserMessage = _messages.value.none { it.role == "user" }

        // Append user bubble to UI
        val userBubble = AiChatMessage(
            role = "user",
            content = trimmed
        )
        _messages.value = _messages.value + userBubble
        aiRepository.setActiveSession(chatId, _messages.value, _contextualPlace.value)

        viewModelScope.launch {
            try {
                // If logged in and first message, create a new persistent Firestore chat session
                if (uid != null) {
                    try {
                        if (isFirstUserMessage || chatId.startsWith("local_")) {
                            chatId = "chat_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
                            _activeChatId.value = chatId
                            aiRepository.setActiveSession(chatId, _messages.value, _contextualPlace.value)
                            val title = aiRepository.generateChatTitle(trimmed)
                            aiRepository.createChatSession(uid, chatId, title)
                            
                            // Save initial assistant welcome greeting as message #1 in Firestore
                            val greetingContent = _messages.value.firstOrNull { it.role == "assistant" }?.content
                                ?: "**Hello! I'm your Belagavi Tourism AI Assistant.**\n\nI can help you discover waterfalls, forts, temples, wildlife sanctuaries, and more across the Belagavi district."
                            aiRepository.saveAssistantMessage(
                                uid = uid,
                                chatId = chatId,
                                content = greetingContent,
                                destinations = emptyList(),
                                webSources = emptyList()
                            )
                            loadHistorySessions(uid)
                        }
                        aiRepository.saveUserMessage(uid, chatId, trimmed)
                    } catch (e: Exception) {
                        // Non-fatal: if offline or Firestore write fails, continue local chat
                    }
                }

                // Ground message if contextual destination is active and place name is omitted
                val contextPlace = _contextualPlace.value
                val apiMessage = if (contextPlace != null && !trimmed.contains(contextPlace.name, ignoreCase = true)) {
                    "${contextPlace.name}: $trimmed"
                } else {
                    trimmed
                }

                // Build history payload from recent turns
                val historyPayload = _messages.value
                    .dropLast(1) // exclude current user message
                    .takeLast(8)
                    .map { ChatMessageDto(role = it.role, content = it.content) }

                // Network call to POST /api/chat
                when (val result = aiRepository.sendChatMessage(apiMessage, historyPayload)) {
                    is AiRepository.NetworkResult.Success -> {
                        val response = result.data
                        val rawAnswer = response.safeAnswer.ifBlank { "Here is the information from Belagavi Tourism." }
                        val cleanAnswer = aiRepository.stripInternalIds(rawAnswer)
                        val showRecs = aiRepository.shouldShowRecommendations(trimmed, response)
                        val finalDestinations = if (showRecs) response.safeDestinations else emptyList()

                        val assistantBubble = AiChatMessage(
                            role = "assistant",
                            content = cleanAnswer,
                            destinations = finalDestinations,
                            webSources = response.safeWebSources
                        )

                        _messages.value = _messages.value + assistantBubble
                        aiRepository.setActiveSession(chatId, _messages.value, _contextualPlace.value)

                        // Persist assistant message in Firestore if logged in
                        if (uid != null && !chatId.startsWith("local_")) {
                            try {
                                aiRepository.saveAssistantMessage(
                                    uid = uid,
                                    chatId = chatId,
                                    content = cleanAnswer,
                                    destinations = finalDestinations,
                                    webSources = response.safeWebSources
                                )
                            } catch (e: Exception) {
                                // Non-fatal
                            }
                        }
                    }
                    is AiRepository.NetworkResult.Error -> {
                        _errorMessage.value = result.message
                        val errorBubble = AiChatMessage(
                            role = "assistant",
                            content = result.message,
                            isError = true
                        )
                        _messages.value = _messages.value + errorBubble
                        aiRepository.setActiveSession(chatId, _messages.value, _contextualPlace.value)
                    }
                }
            } finally {
                _isLoading.value = false
                isSubmitting.set(false)
            }
        }
    }

    fun retryLastMessage() {
        val query = lastUserQuery ?: return
        if (_isLoading.value) return
        // Remove trailing error bubble if present
        if (_messages.value.isNotEmpty() && _messages.value.last().isError) {
            _messages.value = _messages.value.dropLast(1)
        }
        // Remove duplicate user message if present before resending
        if (_messages.value.isNotEmpty() && _messages.value.last().role == "user" && _messages.value.last().content == query) {
            _messages.value = _messages.value.dropLast(1)
        }
        sendMessage(query)
    }

    fun getSuggestionChips(): List<SuggestionChip> {
        val place = _contextualPlace.value
        return if (place != null) {
            listOf(
                SuggestionChip("History", "What is the history of ${place.name}?"),
                SuggestionChip("Entry Fee", "What is the entry fee for ${place.name}?"),
                SuggestionChip("Best Time", "What is the best time to visit ${place.name}?"),
                SuggestionChip("How to Reach", "How to reach ${place.name} from Belagavi?"),
                SuggestionChip("Nearby Places", "What are nearby places to visit around ${place.name}?")
            )
        } else {
            listOf(
                SuggestionChip("Best places to visit", "Best places to visit in Belagavi"),
                SuggestionChip("Waterfalls near Belagavi", "Waterfalls near Belagavi"),
                SuggestionChip("Hidden gems in Belagavi", "Hidden gems in Belagavi"),
                SuggestionChip("Weekend trip ideas", "Weekend trip ideas in Belagavi"),
                SuggestionChip("Wildlife sanctuaries", "Wildlife sanctuaries in Belagavi"),
                SuggestionChip("Best time to visit", "Best time to visit Belagavi")
            )
        }
    }
}
