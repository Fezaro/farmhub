package com.example.app.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.app.api.ApiClient
import com.example.app.models.message.SendMessageResponse
import com.example.app.models.messaging.ThreadListResponse
import com.example.app.models.messaging.MessagesResponse
import com.example.app.session.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response

class MessageRepository(private val context: Context) {
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
        val currentUserPhone = UserSession.phone
        val normalizedRecipient = normalizePhone(recipientPhone)
        if (normalizedRecipient == null && currentUserPhone.isNullOrBlank()) {
            Log.e(
                "MessageRepository",
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
                Log.e("MessageRepository", "Attachment read failed: ${e.message}")
                null
            }
        }
        val start = System.currentTimeMillis()
        Log.d(
            "MessageRepository",
            "POST /messaging recipient=$targetPhone msgSize=${message.length} attachment=${attachmentPart!=null} tokenPresent=${ApiClient.currentToken()!=null} userPhone=$currentUserPhone derivedRecipient=$normalizedRecipient"
        )
        val resp = try {
            ApiClient.userService.sendMessageWithAttachment(
                messageBody,
                phoneBody,
                attachmentPart
            ).execute()
        } catch (e: Exception) {
            Log.e("MessageRepository", "Network exception sending message: ${e.message}")
            return@withContext Response.error(500, ResponseBody.create("text/plain".toMediaTypeOrNull(), e.message ?: "Network error"))
        }
        val took = System.currentTimeMillis() - start
        if (!resp.isSuccessful) {
            try { Log.e("MessageRepository", "SendMessage failed code=${resp.code()} body=${resp.errorBody()?.string()} took=${took}ms") } catch (_: Exception) {}
        } else {
            val body = resp.body()
            Log.d("MessageRepository", "SendMessage success code=${resp.code()} took=${took}ms status=${body?.status} preview='${body?.message?.take(40)}'")
        }
        resp
    }

    suspend fun getThreads(): Response<ThreadListResponse> = withContext(Dispatchers.IO) {
        Log.d("MessageRepository", "GET /messaging (threads) tokenPresent=${ApiClient.currentToken()!=null}")
        val resp = ApiClient.userService.getThreads().execute()
        if (!resp.isSuccessful) {
            try { Log.e("MessageRepository", "Threads fetch failed code=${resp.code()} body=${resp.errorBody()?.string()}") } catch (_: Exception) {}
        }
        resp
    }

    suspend fun getMessages(threadRecipientId: String): Response<MessagesResponse> = withContext(Dispatchers.IO) {
        Log.d("MessageRepository", "GET /messaging/$threadRecipientId tokenPresent=${ApiClient.currentToken()!=null}")
        val resp = ApiClient.userService.getMessages(threadRecipientId).execute()
        if (!resp.isSuccessful) {
            try { Log.e("MessageRepository", "Messages fetch failed code=${resp.code()} body=${resp.errorBody()?.string()}") } catch (_: Exception) {}
        }
        resp
    }
}