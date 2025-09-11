package com.example.app.repository

import com.example.app.api.ApiClient
import com.example.app.models.media.MediaFeedResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class MediaRepository {
    private var cache: MediaFeedResponse? = null
    private var lastFetch: Long = 0L
    private val cacheWindowMs = 30_000L

    suspend fun getMedia(forceRefresh: Boolean = false): Response<MediaFeedResponse> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cache != null && (now - lastFetch) < cacheWindowMs) {
            // Fake a successful Response using retrofit Response.success
            Response.success(cache!!)
        } else {
            val resp = ApiClient.userService.getMediaFeed().execute()
            if (resp.isSuccessful && resp.body() != null) {
                cache = resp.body()
                lastFetch = now
            }
            resp
        }
    }
}

