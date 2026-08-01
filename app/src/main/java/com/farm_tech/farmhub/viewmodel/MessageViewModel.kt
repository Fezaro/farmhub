package com.farm_tech.farmhub.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farm_tech.farmhub.models.message.SendMessageResponse
import com.farm_tech.farmhub.models.messaging.MessageItemResponse
import com.farm_tech.farmhub.models.messaging.ThreadResponse
import com.farm_tech.farmhub.network.ErrorMapper
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.repository.MessageRepository
import com.farm_tech.farmhub.session.UserSession
import com.farm_tech.farmhub.util.FriendlyDateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class SendMessageUiState {
    data object Idle : SendMessageUiState()
    data object Loading : SendMessageUiState()
    data class Success(val response: SendMessageResponse) : SendMessageUiState()
    data class Error(val message: String) : SendMessageUiState()
}

sealed class ThreadsUiState {
    data object Idle: ThreadsUiState()
    data object Loading: ThreadsUiState()
    data object Empty: ThreadsUiState()
    data class Success(val threads: List<ThreadResponse>): ThreadsUiState()
    data class Error(val message: String): ThreadsUiState()
}

sealed class ConversationUiState {
    data object Idle: ConversationUiState()
    data object Loading: ConversationUiState()
    data object Empty: ConversationUiState()
    data class Success(val messages: List<MessageItemResponse>): ConversationUiState()
    data class Error(val message: String): ConversationUiState()
}

data class ThreadPreviewUi(
    val threadId: String,
    val avatarUrl: String?,
    val name: String,
    val role: String,
    val company: String?,
    val lastMessage: String,
    val relativeTime: String,
    val unreadCount: Int,
    val isOnline: Boolean,
    val lastSeenText: String
)

data class ChatHeaderUi(
    val avatarUrl: String?,
    val name: String,
    val role: String,
    val company: String?,
    val lastSeenText: String,
    val isOnline: Boolean
)

class MessageViewModel(
    private val messageRepository: MessageRepository
) : ViewModel() {

    private data class PendingSend(
        val message: String,
        val attachmentUri: Uri?,
        val recipientId: String,
        val conversationId: String
    )

    private val internalIdRegex = Regex("^[0-9a-fA-F-]{32,}$")
    private val numericIdRegex = Regex("^[0-9]{8,}$")

    private val _uiState = MutableStateFlow<SendMessageUiState>(SendMessageUiState.Idle)
    val uiState: StateFlow<SendMessageUiState> = _uiState

    private val _threadsState = MutableStateFlow<ThreadsUiState>(ThreadsUiState.Idle)
    val threadsState: StateFlow<ThreadsUiState> = _threadsState

    private val _conversationState = MutableStateFlow<ConversationUiState>(ConversationUiState.Idle)
    val conversationState: StateFlow<ConversationUiState> = _conversationState

    private val _selectedThreadId = MutableStateFlow<String?>(null)
    val selectedThreadId: StateFlow<String?> = _selectedThreadId

    private var lastFailedSend: PendingSend? = null

    fun loadThreads(force: Boolean = false) {
        if (_threadsState.value is ThreadsUiState.Loading) return
        Log.d("MessageViewModel", "loadThreads(force=$force) starting")
        _threadsState.value = ThreadsUiState.Loading
        viewModelScope.launch {
            when (val result = messageRepository.getThreads(force)) {
                is NetworkResult.Success -> {
                    val conversations = result.data.conversations.orEmpty()
                    val list = if (conversations.isNotEmpty()) {
                        conversations.map { it.toThreadResponse(UserSession.userId) }
                    } else {
                        result.data.threads.orEmpty()
                    }.filter { it.conversationLookupId() != null && it.derivedId() != null }
                    if (list.isEmpty()) {
                        _threadsState.value = ThreadsUiState.Empty
                    } else {
                        _threadsState.value = ThreadsUiState.Success(list)
                        val currentSelection = _selectedThreadId.value
                        val selectedStillExists = currentSelection != null &&
                            list.any { it.conversationLookupId() == currentSelection || it.derivedId() == currentSelection }
                        if (!selectedStillExists) {
                            selectThread(list.first().conversationLookupId() ?: list.first().derivedId())
                        } else {
                            refreshSelectedConversation(force = force)
                        }
                    }
                }
                is NetworkResult.Empty -> _threadsState.value = ThreadsUiState.Empty
                is NetworkResult.Error -> _threadsState.value =
                    ThreadsUiState.Error(ErrorMapper.toUserMessage(result.exception))
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun selectThread(threadRecipientId: String?) {
        _selectedThreadId.value = threadRecipientId
        if (threadRecipientId != null) {
            loadConversation(threadRecipientId)
        } else {
            _conversationState.value = ConversationUiState.Idle
        }
    }

    private fun loadConversation(recipientId: String, force: Boolean = false) {
        _conversationState.value = ConversationUiState.Loading
        viewModelScope.launch {
            when (val result = messageRepository.getMessages(recipientId, force)) {
                is NetworkResult.Success -> {
                    val messages = result.data.messages.orEmpty()
                    _conversationState.value =
                        if (messages.isEmpty()) ConversationUiState.Empty else ConversationUiState.Success(messages)
                }
                is NetworkResult.Empty -> _conversationState.value = ConversationUiState.Empty
                is NetworkResult.Error -> _conversationState.value =
                    ConversationUiState.Error(ErrorMapper.toUserMessage(result.exception))
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun refreshSelectedConversation(force: Boolean = false) {
        val selected = _selectedThreadId.value ?: return
        loadConversation(selected, force)
    }

    private fun findThread(threadId: String?): ThreadResponse? {
        if (threadId == null) return null
        val threadsStateVal = _threadsState.value as? ThreadsUiState.Success ?: return null
        return threadsStateVal.threads.firstOrNull {
            it.derivedId() == threadId || it.conversationLookupId() == threadId
        }
    }

    private fun isIdentifierLike(value: String): Boolean {
        if (internalIdRegex.matches(value) || numericIdRegex.matches(value)) return true
        if (value.startsWith("+") && value.drop(1).all { it.isDigit() }) return true
        if (value.startsWith("0") && value.all { it.isDigit() }) return true
        return false
    }

    fun friendlyParticipantName(raw: String?): String {
        if (raw.isNullOrBlank()) return "Extension Officer"
        val value = raw.trim()
        if (value.isBlank()) return "Extension Officer"
        if (isIdentifierLike(value)) return "Extension Officer"
        return value
    }

    fun getThreadDisplayName(threadId: String?): String {
        val thread = findThread(threadId)
        val resolved = thread?.derivedDisplayName(UserSession.phone)
        return friendlyParticipantName(resolved ?: threadId)
    }

    fun threadPreviews(): List<ThreadPreviewUi> {
        val threads = (_threadsState.value as? ThreadsUiState.Success)?.threads.orEmpty()
        return threads.mapNotNull { thread ->
            val threadId = thread.conversationLookupId() ?: thread.derivedId() ?: return@mapNotNull null
            val rawName = thread.derivedDisplayName(UserSession.phone)
            val name = friendlyParticipantName(rawName)
            val role = thread.derivedRole()
            val company = thread.derivedCompany()
            val relativeTime = FriendlyDateTimeFormatter.toRelativeOrDateTime(thread.derivedUpdatedAt()).ifBlank { "Now" }
            val lastSeenText = if (thread.isOnline == true) {
                "Online"
            } else {
                FriendlyDateTimeFormatter.toRelativeOrDateTime(thread.derivedLastSeen()).ifBlank { "Recently active" }
            }
            ThreadPreviewUi(
                threadId = threadId,
                avatarUrl = thread.avatarUrl,
                name = name,
                role = role,
                company = company,
                lastMessage = thread.derivedLastMessage().ifBlank {
                    if (!thread.lastAttachmentUrl.isNullOrBlank()) "Sent an attachment" else "No messages yet"
                },
                relativeTime = relativeTime,
                unreadCount = thread.unreadCount ?: 0,
                isOnline = thread.isOnline == true,
                lastSeenText = lastSeenText
            )
        }
    }

    fun selectedHeader(): ChatHeaderUi? {
        val selected = _selectedThreadId.value ?: return null
        val preview = threadPreviews().firstOrNull { it.threadId == selected } ?: return null
        return ChatHeaderUi(
            avatarUrl = preview.avatarUrl,
            name = preview.name,
            role = preview.role,
            company = preview.company,
            lastSeenText = preview.lastSeenText,
            isOnline = preview.isOnline
        )
    }

    fun sendMessage(
        message: String,
        attachmentUri: Uri?,
        recipientOverride: String? = null,
        conversationOverride: String? = null
    ) {
        if (_uiState.value is SendMessageUiState.Loading) return
        _uiState.value = SendMessageUiState.Loading
        viewModelScope.launch {
            if (UserSession.phone.isNullOrBlank()) {
                messageRepository.hydrateSessionFromProfileIfNeeded()
            }
            val threads = (_threadsState.value as? ThreadsUiState.Success)?.threads.orEmpty()
            val recipientId = recipientOverride
                ?: messageRepository.deriveRecipientId(_selectedThreadId.value, threads)
            val selectedThreadLookupId = conversationOverride ?: threads.firstOrNull { thread ->
                thread.conversationLookupId() == _selectedThreadId.value || thread.derivedId() == _selectedThreadId.value
            }?.conversationLookupId() ?: _selectedThreadId.value
            if (message.isBlank() && attachmentUri == null) {
                _uiState.value = SendMessageUiState.Error("Type a message or attach a photo first.")
                return@launch
            }
            if (recipientId.isNullOrBlank() || selectedThreadLookupId.isNullOrBlank()) {
                _uiState.value = SendMessageUiState.Error("Select a conversation first.")
                return@launch
            }
            lastFailedSend = PendingSend(message, attachmentUri, recipientId, selectedThreadLookupId)

            when (val result = messageRepository.sendMessage(message, attachmentUri, recipientId, selectedThreadLookupId)) {
                is NetworkResult.Success -> {
                    _uiState.value = SendMessageUiState.Success(result.data)
                    lastFailedSend = null
                    messageRepository.invalidateConversation(selectedThreadLookupId)
                    loadThreads(force = true)
                }
                is NetworkResult.Empty -> {
                    _uiState.value = SendMessageUiState.Error("Message sent but response was empty")
                }
                is NetworkResult.Error -> {
                    _uiState.value = SendMessageUiState.Error(ErrorMapper.toUserMessage(result.exception))
                }
                NetworkResult.Loading -> Unit
            }
        }
    }

    fun retryLastFailedMessage() {
        val pending = lastFailedSend ?: return
        sendMessage(pending.message, pending.attachmentUri, pending.recipientId, pending.conversationId)
    }

    fun resetState() {
        _uiState.value = SendMessageUiState.Idle
    }
}
