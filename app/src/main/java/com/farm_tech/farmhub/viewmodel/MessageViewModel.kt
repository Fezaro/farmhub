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

class MessageViewModel(
    private val messageRepository: MessageRepository
) : ViewModel() {

    private data class PendingSend(
        val message: String,
        val attachmentUri: Uri?,
        val recipientPhone: String?
    )

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
                    val list = result.data.threads?.filter { it.derivedId() != null }.orEmpty()
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

    fun getThreadDisplayName(threadId: String?): String {
        if (threadId == null) return "Extension Officer"
        val threadsStateVal = _threadsState.value
        if (threadsStateVal is ThreadsUiState.Success) {
            val thread = threadsStateVal.threads.firstOrNull {
                it.derivedId() == threadId || it.conversationLookupId() == threadId
            }
            val otherParty = thread?.otherParty(UserSession.phone)
            if (!otherParty.isNullOrBlank()) {
                return friendlyParticipantName(otherParty)
            }
        }
        return friendlyParticipantName(threadId)
    }

    fun friendlyParticipantName(raw: String?): String {
        if (raw.isNullOrBlank()) return "Extension Officer"
        val value = raw.trim()
        if (value.isBlank()) return "Extension Officer"

        val isUuidLike = value.matches(Regex("^[0-9a-fA-F-]{32,}$"))
        val isNumericIdLike = value.matches(Regex("^[0-9]{8,}$"))
        if (isUuidLike || isNumericIdLike) return "Extension Officer"

        val normalizedPhone = when {
            value.startsWith("+") && value.drop(1).all { it.isDigit() } -> true
            value.startsWith("0") && value.all { it.isDigit() } -> true
            else -> false
        }
        if (normalizedPhone) return "Extension Officer"

        return value
    }

    fun sendMessage(message: String, attachmentUri: Uri?) {
        if (_uiState.value is SendMessageUiState.Loading) return
        _uiState.value = SendMessageUiState.Loading
        viewModelScope.launch {
            if (UserSession.phone.isNullOrBlank()) {
                messageRepository.hydrateSessionFromProfileIfNeeded()
            }
            val threads = (_threadsState.value as? ThreadsUiState.Success)?.threads.orEmpty()
            val recipientPhone = messageRepository.deriveRecipientPhone(_selectedThreadId.value, threads)
            val selectedThreadLookupId = threads.firstOrNull { thread ->
                thread.conversationLookupId() == _selectedThreadId.value || thread.derivedId() == _selectedThreadId.value
            }?.conversationLookupId() ?: _selectedThreadId.value
            if (message.isBlank() && attachmentUri == null) {
                _uiState.value = SendMessageUiState.Error("Type a message or attach a photo first.")
                return@launch
            }
            if (recipientPhone.isNullOrBlank()) {
                _uiState.value = SendMessageUiState.Error("Select a conversation first.")
                return@launch
            }
            lastFailedSend = PendingSend(message, attachmentUri, recipientPhone)

            when (val result = messageRepository.sendMessage(message, attachmentUri, recipientPhone)) {
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
        sendMessage(pending.message, pending.attachmentUri)
    }

    fun resetState() {
        _uiState.value = SendMessageUiState.Idle
    }
}
