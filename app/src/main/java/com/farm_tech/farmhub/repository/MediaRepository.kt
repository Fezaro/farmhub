package com.farm_tech.farmhub.repository

import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.models.media.MediaFeedResponse
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository {
    companion object {
        private const val TAG = "MediaRepository"
        private const val CACHE_TTL_MS = 30_000L
    }

    private var cachedFeed: MediaFeedResponse? = null
    private var lastFetchAtMs: Long = 0L

    suspend fun getMediaFeed(force: Boolean = false): NetworkResult<MediaFeedResponse> = withContext(Dispatchers.IO) {
        if (!force && cachedFeed != null && (System.currentTimeMillis() - lastFetchAtMs) < CACHE_TTL_MS) {
            return@withContext NetworkResult.Success(cachedFeed!!)
        }

        when (val result = safeApiCall { ApiClient.userService.getMediaFeed().execute() }) {
            is NetworkResult.Success -> {
                cachedFeed = result.data
                lastFetchAtMs = System.currentTimeMillis()
                Log.d(TAG, "Media feed loaded: ${result.data.media?.size ?: 0} items")
                result
            }
            else -> result
        }
    }
}
