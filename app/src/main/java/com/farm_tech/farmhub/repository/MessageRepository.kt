package com.farm_tech.farmhub.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.models.message.SendMessageResponse
import com.farm_tech.farmhub.models.messaging.MessagesResponse
import com.farm_tech.farmhub.models.messaging.ThreadListResponse
import com.farm_tech.farmhub.models.messaging.UserProfileCache
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.network.safeApiCall
import com.farm_tech.farmhub.session.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class MessageRepository(private val context: Context) {
    companion object {
        private const val TAG = "MessageRepository"
    }

    private val userNameCache = mutableMapOf<String, UserProfileCache>()

    private fun normalizePhone(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("+254") -> trimmed
            trimmed.startsWith("0") && trimmed.length == 10 -> "+254" + trimmed.drop(1)
            else -> trimmed
        }
    }

    private fun normalizeRecipientToken(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val token = raw.trim()
        if (token.isBlank()) return null
        return normalizePhone(token) ?: token
    }

    suspend fun sendMessage(
        message: String,
        attachmentUri: Uri?,
        recipientPhone: String?
    ): NetworkResult<SendMessageResponse> = withContext(Dispatchers.IO) {
        val currentUserPhone = UserSession.phone
        val normalizedRecipient = normalizePhone(recipientPhone)
        if (normalizedRecipient == null && currentUserPhone.isNullOrBlank()) {
            return@withContext NetworkResult.Error(
                com.farm_tech.farmhub.network.ApiException.BadRequest("No recipient available")
            )
        }

        val messageBody = message.toRequestBody("text/plain".toMediaTypeOrNull())
        val targetPhone = normalizedRecipient ?: currentUserPhone!!.trim()
        val phoneBody = targetPhone.toRequestBody("text/plain".toMediaTypeOrNull())

        val attachmentPart = attachmentUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull(), 0, bytes.size)
                    MultipartBody.Part.createFormData("attachment", "image.jpg", requestFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Attachment read failed: ${e.message}")
                null
            }
        }

        safeApiCall {
            ApiClient.userService.sendMessageWithAttachment(
                messageBody,
                phoneBody,
                attachmentPart
            ).execute()
        }
    }

    suspend fun getThreads(): NetworkResult<ThreadListResponse> = withContext(Dispatchers.IO) {
        safeApiCall { ApiClient.userService.getThreads().execute() }
    }

    suspend fun getMessages(threadRecipientId: String): NetworkResult<MessagesResponse> = withContext(Dispatchers.IO) {
        safeApiCall { ApiClient.userService.getMessages(threadRecipientId).execute() }
    }

    suspend fun hydrateSessionFromProfileIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (!UserSession.phone.isNullOrBlank()) return@withContext true
        if (UserSession.token.isNullOrBlank()) {
            ApiClient.currentToken()?.let { UserSession.token = it }
        }
        if (UserSession.token.isNullOrBlank()) return@withContext false

        when (val result = safeApiCall { ApiClient.userService.getUserProfile().execute() }) {
            is NetworkResult.Success -> {
                val data = result.data.data ?: return@withContext false
                if (UserSession.userId == null) UserSession.userId = data.id
                if (UserSession.userName == null) UserSession.userName = data.names
                if (UserSession.phone.isNullOrBlank()) UserSession.phone = data.phone
                if (UserSession.role == null) UserSession.role = data.role
                if (UserSession.county == null) UserSession.county = data.county
                if (UserSession.subCounty == null) UserSession.subCounty = data.subCounty
                !UserSession.phone.isNullOrBlank()
            }
            else -> false
        }
    }

    fun deriveRecipientPhone(selectedThreadId: String?, threads: List<com.farm_tech.farmhub.models.messaging.ThreadResponse>): String? {
        val currentPhone = UserSession.phone
        val thread = threads.firstOrNull { it.derivedId() == selectedThreadId }
        val other = thread?.otherParty(currentPhone)
        normalizeRecipientToken(other)?.let { token ->
            if (token != currentPhone) return token
        }

        val fromParticipants = thread?.participants?.firstOrNull { participant ->
            !participant.isNullOrBlank() && participant != currentPhone
        }
        normalizeRecipientToken(fromParticipants)?.let { token ->
            if (token != currentPhone) return token
        }

        val candidate = thread?.recipientId
        normalizeRecipientToken(candidate)?.let { token ->
            if (token != currentPhone) return token
        }

        val derived = thread?.derivedId()
        normalizeRecipientToken(derived)?.let { token ->
            if (token != currentPhone) return token
        }

        // If no explicit selection exists but threads are available, fallback to first resolvable recipient.
        threads.forEach { t ->
            val fallback = normalizeRecipientToken(t.otherParty(currentPhone))
                ?: normalizeRecipientToken(t.recipientId)
                ?: normalizeRecipientToken(t.derivedId())
            if (!fallback.isNullOrBlank() && fallback != currentPhone) return fallback
        }

        return null
    }

    suspend fun getUserNameByPhone(phone: String?): String = withContext(Dispatchers.IO) {
        if (phone.isNullOrBlank()) return@withContext "Unknown User"

        val cached = userNameCache[phone]
        if (cached != null && !cached.isExpired()) {
            return@withContext cached.name
        }

        if (phone == UserSession.phone) {
            val displayName = UserSession.userName.takeIf { !it.isNullOrBlank() } ?: phone
            userNameCache[phone] = UserProfileCache(phone, displayName)
            return@withContext displayName
        }

        val displayName = phone.takeIf { it.isNotBlank() } ?: "Unknown User"
        userNameCache[phone] = UserProfileCache(phone, displayName)
        displayName
    }

    fun clearUserNameCache() {
        userNameCache.clear()
    }
}
