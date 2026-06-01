package com.example.app.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.app.api.ApiClient
import com.example.app.auth.TokenValidator
import com.example.app.models.message.SendMessageResponse
import com.example.app.models.messaging.ThreadListResponse
import com.example.app.models.messaging.MessagesResponse
import com.example.app.models.messaging.UserProfileCache
import com.example.app.session.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Repository for messaging endpoints.
 * Enforces token validation before making API calls.
 *
 * Senior practice: Centralized message repository handles all messaging API interactions,
 * including thread listing, message fetching, and sending with validation.
 */
class MessageRepository(private val context: Context) {
    companion object {
        private const val TAG = "MessageRepository"
    }

    // User name cache. Key: phone number, Value: user's full name
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

    suspend fun sendMessage(
        message: String,
        attachmentUri: Uri?,
        recipientPhone: String?
    ): Response<SendMessageResponse> = withContext(Dispatchers.IO) {
        // Pre-flight token validation
        if (!TokenValidator.isTokenValid()) {
            Log.w(TAG, "Cannot send message: token invalid or missing. Aborting request.")
            return@withContext Response.error(
                401,
                ResponseBody.create("text/plain".toMediaTypeOrNull(), "No valid authentication token")
            )
        }

        val currentUserPhone = UserSession.phone
        val normalizedRecipient = normalizePhone(recipientPhone)
        if (normalizedRecipient == null && currentUserPhone.isNullOrBlank()) {
            Log.e(
                TAG,
                "Aborting send: no recipientPhone provided and current user phone missing. tokenPresent=${ApiClient.currentToken()!=null}"
            )
            return@withContext Response.error(400, ResponseBody.create("text/plain".toMediaTypeOrNull(), "No recipient available"))
        }
        val messageBody = message.toRequestBody("text/plain".toMediaTypeOrNull())
        val targetPhone = normalizedRecipient ?: currentUserPhone!!.trim()
        val phoneBody = targetPhone.toRequestBody("text/plain".toMediaTypeOrNull())

        val attachmentPart = attachmentUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val requestFile = bytes.toRequestBody("image/*".toMediaTypeOrNull(), 0, bytes.size)
                    MultipartBody.Part.createFormData(
                        "attachment",
                        "image.jpg",
                        requestFile
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Attachment read failed: ${e.message}")
                null
            }
        }
        val start = System.currentTimeMillis()
        Log.d(
            TAG,
            "POST /messaging recipient=$targetPhone msgSize=${message.length} attachment=${attachmentPart!=null} tokenPresent=${ApiClient.currentToken()!=null} userPhone=$currentUserPhone"
        )
        val resp = try {
            ApiClient.userService.sendMessageWithAttachment(
                messageBody,
                phoneBody,
                attachmentPart
            ).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Network exception sending message: ${e.message}")
            return@withContext Response.error(500, ResponseBody.create("text/plain".toMediaTypeOrNull(), e.message ?: "Network error"))
        }
        val took = System.currentTimeMillis() - start
        if (!resp.isSuccessful) {
            try { Log.e(TAG, "SendMessage failed code=${resp.code()} body=${resp.errorBody()?.string()} took=${took}ms") } catch (_: Exception) {}
        } else {
            val body = resp.body()
            Log.d(TAG, "SendMessage success code=${resp.code()} took=${took}ms status=${body?.status}")
        }
        resp
    }

    /**
     * Fetches all conversation threads for the current user.
     * Enforces token validation before making the request.
     *
     * @return Response containing ThreadListResponse on success
     */
    suspend fun getThreads(): Response<ThreadListResponse> = withContext(Dispatchers.IO) {
        if (!TokenValidator.isTokenValid()) {
            Log.w(TAG, "Cannot fetch threads: token invalid or missing. Aborting request.")
            return@withContext Response.error(
                401,
                ResponseBody.create("text/plain".toMediaTypeOrNull(), "No valid authentication token")
            )
        }

        Log.d(TAG, "GET /messaging (threads) tokenPresent=true, initiating request...")
        val resp = ApiClient.userService.getThreads().execute()
        if (!resp.isSuccessful) {
            try { Log.e(TAG, "Threads fetch failed code=${resp.code()} body=${resp.errorBody()?.string()}") } catch (_: Exception) {}
        } else {
            Log.d(TAG, "Threads fetch success. Count=${resp.body()?.threads?.size ?: 0}")
        }
        resp
    }

    /**
     * Fetches messages in a specific thread with the given recipient.
     * Enforces token validation before making the request.
     *
     * @param threadRecipientId The recipient ID for the thread
     * @return Response containing MessagesResponse on success
     */
    suspend fun getMessages(threadRecipientId: String): Response<MessagesResponse> = withContext(Dispatchers.IO) {
        if (!TokenValidator.isTokenValid()) {
            Log.w(TAG, "Cannot fetch messages: token invalid or missing. Aborting request.")
            return@withContext Response.error(
                401,
                ResponseBody.create("text/plain".toMediaTypeOrNull(), "No valid authentication token")
            )
        }

        Log.d(TAG, "GET /messaging/$threadRecipientId tokenPresent=true, initiating request...")
        val resp = ApiClient.userService.getMessages(threadRecipientId).execute()
        if (!resp.isSuccessful) {
            try { Log.e(TAG, "Messages fetch failed code=${resp.code()} body=${resp.errorBody()?.string()}") } catch (_: Exception) {}
        } else {
            Log.d(TAG, "Messages fetch success. Count=${resp.body()?.messages?.size ?: 0}")
        }
        resp
    }

    /**
     * Fetches user profile information to map phone numbers to user names.
     * Uses in-memory cache to reduce API calls.
     *
     * @param phone The phone number to look up
     * @return User's full name or phone if unavailable
     */
    suspend fun getUserNameByPhone(phone: String?): String = withContext(Dispatchers.IO) {
        if (phone.isNullOrBlank()) return@withContext "Unknown User"

        // Check cache first
        val cached = userNameCache[phone]
        if (cached != null && !cached.isExpired()) {
            Log.d(TAG, "Returning cached name for $phone")
            return@withContext cached.name
        }

        // For current user, use session data
        if (phone == UserSession.phone) {
            val displayName = UserSession.userName.takeIf { !it.isNullOrBlank() } ?: phone
            userNameCache[phone] = UserProfileCache(phone, displayName)
            return@withContext displayName
        }

        // Try to fetch from API (optional - if endpoint supports it)
        // For now, we fall back to returning the phone as no dedicated endpoint exists
        val displayName = phone.takeIf { it.isNotBlank() } ?: "Unknown User"
        userNameCache[phone] = UserProfileCache(phone, displayName)
        Log.d(TAG, "Generated fallback name for $phone")
        displayName
    }

    fun clearUserNameCache() {
        userNameCache.clear()
        Log.d(TAG, "User name cache cleared")
    }
}
