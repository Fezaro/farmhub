package com.farm_tech.farmhub.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.models.message.SendMessageResponse
import com.farm_tech.farmhub.models.messaging.MessagesResponse
import com.farm_tech.farmhub.models.messaging.ThreadResponse
import com.farm_tech.farmhub.models.messaging.ThreadListResponse
import com.farm_tech.farmhub.models.messaging.UserProfileCache
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.network.safeApiCall
import com.farm_tech.farmhub.session.UserSession
import com.farm_tech.farmhub.util.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class MessageRepository(private val context: Context) {
    companion object {
        private const val TAG = "MessageRepository"
    }

    private val userNameCache = mutableMapOf<String, UserProfileCache>()
    private var cachedThreads: ThreadListResponse? = null
    private var cachedThreadsAtMs: Long = 0L
    private val conversationCache = mutableMapOf<String, CachedConversation>()

    private data class CachedConversation(
        val response: MessagesResponse,
        val cachedAtMs: Long
    )

    private fun normalizePhone(raw: String?): String? {
        return PhoneNumberFormatter.normalizeKenyanPhone(raw)
    }

    private fun writeAttachmentToTempFile(uri: Uri): File? {
        return try {
            val temp = File.createTempFile("msg_attachment_", ".jpg", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: return null
            temp
        } catch (e: Exception) {
            Log.e(TAG, "Attachment stream copy failed", e)
            null
        }
    }

    suspend fun sendMessage(
        message: String,
        attachmentUri: Uri?,
        recipientId: String,
        conversationId: String
    ): NetworkResult<SendMessageResponse> = withContext(Dispatchers.IO) {
        val messageBody = message.toRequestBody("text/plain".toMediaTypeOrNull())
        val recipientBody = recipientId.toRequestBody("text/plain".toMediaTypeOrNull())
        val conversationBody = conversationId.toRequestBody("text/plain".toMediaTypeOrNull())

        var attachmentFile: File? = null
        try {
            val attachmentPart = attachmentUri?.let { uri ->
                attachmentFile = writeAttachmentToTempFile(uri)
                val file = attachmentFile ?: return@let null
                MultipartBody.Part.createFormData(
                    "attachment",
                    file.name,
                    file.asRequestBody("image/*".toMediaTypeOrNull())
                )
            }

            safeApiCall {
                ApiClient.userService.sendMessageWithAttachment(
                    messageBody,
                    recipientBody,
                    conversationBody,
                    attachmentPart
                ).execute()
            }
        } finally {
            attachmentFile?.delete()
        }
    }

    suspend fun getThreads(force: Boolean = false): NetworkResult<ThreadListResponse> = withContext(Dispatchers.IO) {
        val cached = cachedThreads
        if (!force && cached != null && System.currentTimeMillis() - cachedThreadsAtMs < 30_000L) {
            return@withContext NetworkResult.Success(cached)
        }
        when (val result = safeApiCall { ApiClient.userService.getThreads().execute() }) {
            is NetworkResult.Success -> {
                cachedThreads = result.data
                cachedThreadsAtMs = System.currentTimeMillis()
                result
            }
            is NetworkResult.Error -> {
                if (cached != null) {
                    Log.w(TAG, "Using cached threads due to network/API error")
                    NetworkResult.Success(cached)
                } else {
                    result
                }
            }
            else -> result
        }
    }

    suspend fun getMessages(conversationId: String, force: Boolean = false): NetworkResult<MessagesResponse> = withContext(Dispatchers.IO) {
        val cached = conversationCache[conversationId]
        if (!force && cached != null && System.currentTimeMillis() - cached.cachedAtMs < 30_000L) {
            return@withContext NetworkResult.Success(cached.response)
        }
        when (val result = safeApiCall { ApiClient.userService.getMessages(conversationId).execute() }) {
            is NetworkResult.Success -> {
                conversationCache[conversationId] = CachedConversation(result.data, System.currentTimeMillis())
                result
            }
            is NetworkResult.Error -> {
                if (cached != null) {
                    Log.w(TAG, "Using cached conversation for $conversationId due to network/API error")
                    NetworkResult.Success(cached.response)
                } else {
                    result
                }
            }
            else -> result
        }
    }

    suspend fun hydrateSessionFromProfileIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        if (!UserSession.userId.isNullOrBlank()) return@withContext true
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
                !UserSession.userId.isNullOrBlank()
            }
            else -> false
        }
    }

    fun deriveRecipientId(selectedThreadId: String?, threads: List<com.farm_tech.farmhub.models.messaging.ThreadResponse>): String? {
        val thread = threads.firstOrNull {
            it.conversationLookupId() == selectedThreadId || it.derivedId() == selectedThreadId
        }
        return thread?.recipientId?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun getUserNameByPhone(phone: String?): String = withContext(Dispatchers.IO) {
        if (phone.isNullOrBlank()) return@withContext "Unknown User"
        val cacheKey = normalizePhone(phone) ?: phone.trim()

        val cached = userNameCache[cacheKey]
        if (cached != null && !cached.isExpired()) {
            return@withContext cached.name
        }

        if (PhoneNumberFormatter.samePhone(phone, UserSession.phone) || phone == UserSession.phone) {
            val displayName = UserSession.userName.takeIf { !it.isNullOrBlank() } ?: phone
            userNameCache[cacheKey] = UserProfileCache(cacheKey, displayName)
            return@withContext displayName
        }

        val displayName = phone.takeIf { it.isNotBlank() } ?: "Unknown User"
        userNameCache[cacheKey] = UserProfileCache(cacheKey, displayName)
        displayName
    }

    fun clearUserNameCache() {
        userNameCache.clear()
        cachedThreads = null
        cachedThreadsAtMs = 0L
        conversationCache.clear()
    }

    fun invalidateConversation(threadRecipientId: String? = null) {
        if (threadRecipientId == null) {
            cachedThreads = null
            cachedThreadsAtMs = 0L
            conversationCache.clear()
            return
        }
        conversationCache.remove(threadRecipientId)
    }
}
