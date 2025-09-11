package com.example.app.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.models.message.SendMessageResponse
import com.example.app.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.app.models.messaging.ThreadResponse
import com.example.app.models.messaging.MessageItemResponse
import com.example.app.session.UserSession
import java.util.regex.Pattern
import retrofit2.awaitResponse
import com.example.app.api.ApiClient
import com.example.app.models.profile.UserProfileResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class SendMessageUiState {
    data object Idle : SendMessageUiState()
    data object Loading : SendMessageUiState()
    data class Success(val response: SendMessageResponse) : SendMessageUiState()
    data class Error(val message: String) : SendMessageUiState()
}

// Additional UI states for threads & messages list
sealed class ThreadsUiState {
    data object Idle: ThreadsUiState()
    data object Loading: ThreadsUiState()
    data class Success(val threads: List<ThreadResponse>): ThreadsUiState()
    data class Error(val message: String): ThreadsUiState()
}

sealed class ConversationUiState {
    data object Idle: ConversationUiState()
    data object Loading: ConversationUiState()
    data class Success(val messages: List<MessageItemResponse>): ConversationUiState()
    data class Error(val message: String): ConversationUiState()
}

class MessageViewModel(
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SendMessageUiState>(SendMessageUiState.Idle)
    val uiState: StateFlow<SendMessageUiState> = _uiState

    private val _threadsState = MutableStateFlow<ThreadsUiState>(ThreadsUiState.Idle)
    val threadsState: StateFlow<ThreadsUiState> = _threadsState

    private val _conversationState = MutableStateFlow<ConversationUiState>(ConversationUiState.Idle)
    val conversationState: StateFlow<ConversationUiState> = _conversationState

    private val _selectedThreadId = MutableStateFlow<String?>(null)
    val selectedThreadId: StateFlow<String?> = _selectedThreadId

    private val phoneRegex = Pattern.compile("^(?:\\+254|0)\\d{9}")

    fun loadThreads(force: Boolean = false) {
        if (ApiClient.currentToken()==null) {
            Log.w("MessageViewModel", "loadThreads aborted: no token")
            _threadsState.value = ThreadsUiState.Error("Not authenticated")
            return
        }
        if (_threadsState.value is ThreadsUiState.Loading) return
        Log.d("MessageViewModel", "loadThreads(force=$force) starting")
        _threadsState.value = ThreadsUiState.Loading
        viewModelScope.launch {
            try {
                val resp = messageRepository.getThreads()
                Log.d("MessageViewModel", "Threads response code=${resp.code()} success=${resp.isSuccessful}")
                if (resp.isSuccessful && resp.body()?.threads != null) {
                    val raw = resp.body()!!.threads!!.filterNotNull()
                    val list = raw.filter { it.derivedId() != null }
                    Log.d("MessageViewModel", "Threads fetched count=${list.size}")
                    _threadsState.value = ThreadsUiState.Success(list)
                    // Auto select first thread if nothing selected
                    if (_selectedThreadId.value == null && list.isNotEmpty()) {
                        Log.d("MessageViewModel", "Auto selecting first thread id=${list.first().derivedId()}")
                        selectThread(list.first().derivedId())
                    }
                } else {
                    _threadsState.value = ThreadsUiState.Error("Threads error: ${resp.code()} ${resp.message()}")
                }
            } catch (e: Exception) {
                Log.e("MessageViewModel", "Threads exception: ${e.message}")
                _threadsState.value = ThreadsUiState.Error(e.localizedMessage ?: "Unexpected error")
            }
        }
    }

    fun selectThread(threadRecipientId: String?) {
        Log.d("MessageViewModel", "selectThread id=$threadRecipientId")
        _selectedThreadId.value = threadRecipientId
        if (threadRecipientId != null) {
            loadConversation(threadRecipientId)
        } else {
            _conversationState.value = ConversationUiState.Idle
        }
    }

    private fun loadConversation(recipientId: String) {
        Log.d("MessageViewModel", "loadConversation recipientId=$recipientId")
        _conversationState.value = ConversationUiState.Loading
        viewModelScope.launch {
            try {
                val resp = messageRepository.getMessages(recipientId)
                Log.d("MessageViewModel", "Messages response code=${resp.code()} success=${resp.isSuccessful}")
                if (resp.isSuccessful && resp.body()?.messages != null) {
                    val msgs = resp.body()!!.messages!!.filterNotNull()
                    Log.d("MessageViewModel", "Messages fetched count=${msgs.size}")
                    _conversationState.value = ConversationUiState.Success(msgs)
                } else {
                    _conversationState.value = ConversationUiState.Error("Messages error: ${resp.code()} ${resp.message()}")
                }
            } catch (e: Exception) {
                Log.e("MessageViewModel", "Messages exception: ${e.message}")
                _conversationState.value = ConversationUiState.Error(e.localizedMessage ?: "Unexpected error")
            }
        }
    }

    private suspend fun hydrateSessionIfNeeded(): Boolean {
        if (!UserSession.phone.isNullOrBlank()) return true
        // If UserSession.token missing but ApiClient has bearer, sync it
        if (UserSession.token.isNullOrBlank()) {
            ApiClient.currentToken()?.let { UserSession.token = it }
        }
        val tokenPresent = !UserSession.token.isNullOrBlank()
        if (!tokenPresent) {
            Log.w("MessageViewModel", "hydrateSessionIfNeeded: no token to fetch profile")
            return false
        }
        return try {
            Log.d("MessageViewModel", "hydrateSessionIfNeeded: fetching profile to obtain phone (IO)")
            val resp = withContext(Dispatchers.IO) { ApiClient.userService.getUserProfile().execute() }
            if (resp.isSuccessful) {
                val data = resp.body()?.data
                if (data != null) {
                    if (UserSession.userId == null) UserSession.userId = data.id
                    if (UserSession.userName == null) UserSession.userName = data.names
                    if (UserSession.phone.isNullOrBlank()) UserSession.phone = data.phone
                    if (UserSession.role == null) UserSession.role = data.role
                    if (UserSession.county == null) UserSession.county = data.county
                    if (UserSession.subCounty == null) UserSession.subCounty = data.subCounty
                    Log.d("MessageViewModel", "hydrateSessionIfNeeded: phone now=${UserSession.phone}")
                }
                !UserSession.phone.isNullOrBlank()
            } else {
                Log.e("MessageViewModel", "hydrateSessionIfNeeded profile fetch failed code=${resp.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e("MessageViewModel", "hydrateSessionIfNeeded exception: ${e.message}")
            false
        }
    }

    private fun deriveRecipientPhone(): String? {
        val threadsStateVal = _threadsState.value
        val currentPhone = UserSession.phone
        if (threadsStateVal is ThreadsUiState.Success) {
            val targetId = _selectedThreadId.value
            val thread = threadsStateVal.threads.firstOrNull { it.derivedId() == targetId }
            // Prefer explicit otherParty helper
            val other = thread?.otherParty(currentPhone)
            if (!other.isNullOrBlank() && phoneRegex.matcher(other).find()) return other
            // 1. Try participants excluding current user
            val fromParticipants = thread?.participants?.firstOrNull { participant ->
                val mePhone = currentPhone
                participant != null && participant != mePhone && phoneRegex.matcher(participant).find()
            }
            if (!fromParticipants.isNullOrBlank()) return fromParticipants
            // 2. If recipientId looks like a phone use it
            val candidate = thread?.recipientId
            if (!candidate.isNullOrBlank() && phoneRegex.matcher(candidate).find()) return candidate
            // 3. If derivedId itself looks like a phone number use it
            val derived = thread?.derivedId()
            if (!derived.isNullOrBlank() && phoneRegex.matcher(derived).find()) return derived
        }
        return null
    }

    fun sendMessage(message: String, attachmentUri: Uri?) {
        val currentPhone = UserSession.phone
        val tokenPresent = !UserSession.token.isNullOrBlank() || ApiClient.currentToken()!=null
        Log.d("MessageViewModel", "sendMessage invoked textLength=${message.length} hasAttachment=${attachmentUri!=null} selectedThread=${_selectedThreadId.value} tokenPresent=$tokenPresent currentPhone=$currentPhone")
        _uiState.value = SendMessageUiState.Loading
        viewModelScope.launch {
            // Attempt hydration if phone missing
            if (UserSession.phone.isNullOrBlank()) {
                val hydrated = hydrateSessionIfNeeded()
                Log.d("MessageViewModel", "Hydration attempted, success=$hydrated phone=${UserSession.phone}")
            }
            try {
                val recipientPhone = deriveRecipientPhone()
                if (recipientPhone==null && UserSession.phone.isNullOrBlank()) {
                    Log.e("MessageViewModel", "Cannot send: no recipient and user phone still null")
                    _uiState.value = SendMessageUiState.Error("No recipient available")
                    return@launch
                }
                Log.d("MessageViewModel", "Derived recipient phone: $recipientPhone (before repository call) userPhone=${UserSession.phone}")
                val response = messageRepository.sendMessage(message, attachmentUri, recipientPhone)
                Log.d("MessageViewModel", "Send response code=${response.code()} success=${response.isSuccessful}")
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = SendMessageUiState.Success(response.body()!!)
                    _selectedThreadId.value?.let { loadConversation(it) }
                } else {
                    _uiState.value = SendMessageUiState.Error("Failed: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("MessageViewModel", "Send exception: ${e.message}")
                _uiState.value = SendMessageUiState.Error("Exception: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = SendMessageUiState.Idle
    }
}