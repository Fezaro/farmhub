package com.example.app.repository

import android.util.Log
import com.example.app.api.ApiClient
import com.example.app.models.media.MediaFeedResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class MediaRepository {
    suspend fun getMediaFeed(): Response<MediaFeedResponse> = withContext(Dispatchers.IO) {
        try {
            val resp = ApiClient.userService.getMediaFeed().execute()
            if (!resp.isSuccessful) {
                Log.e("MediaRepository", "Media feed error code=${resp.code()} body=${resp.errorBody()?.string()}")
            } else {
                Log.d("MediaRepository", "Media feed success items=${resp.body()?.media?.size}")
            }
            resp
        } catch (e: Exception) {
            Log.e("MediaRepository", "Exception fetching media: ${e.message}")
            Response.error(500, okhttp3.ResponseBody.create(null, e.message ?: "error"))
        }
    }
}

