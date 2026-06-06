package com.farm_tech.farmhub.repository

import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.models.media.MediaFeedResponse
import com.farm_tech.farmhub.models.media.MediaItemResponse
import com.farm_tech.farmhub.models.media.MediaUrlNormalizer
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

    private fun normalizeItems(items: List<MediaItemResponse>?): List<MediaItemResponse> {
        return items.orEmpty().map { item ->
            item.copy(
                thumbnailUrl = MediaUrlNormalizer.normalize(item.resolvedThumbnailUrl()),
                mediaUrl = MediaUrlNormalizer.normalize(item.resolvedMediaUrl())
            )
        }
    }

    suspend fun getMediaFeed(force: Boolean = false): NetworkResult<MediaFeedResponse> = withContext(Dispatchers.IO) {
        if (!force && cachedFeed != null && (System.currentTimeMillis() - lastFetchAtMs) < CACHE_TTL_MS) {
            return@withContext NetworkResult.Success(cachedFeed!!)
        }

        when (val result = safeApiCall { ApiClient.userService.getMediaFeed().execute() }) {
            is NetworkResult.Success -> {
                val normalizedFeed = result.data.copy(media = normalizeItems(result.data.media))
                val sample = normalizedFeed.media?.firstOrNull()
                Log.d(
                    TAG,
                    "Media contract sample: id=${sample?.id}, title=${sample?.title}, mediaUrl=${sample?.mediaUrl}, thumbnailUrl=${sample?.thumbnailUrl}, mediaType=${sample?.resolvedMediaType()}, category=${sample?.category}, subcategory=${sample?.subcategory}"
                )
                cachedFeed = normalizedFeed
                lastFetchAtMs = System.currentTimeMillis()
                Log.d(TAG, "Media feed loaded: ${normalizedFeed.media?.size ?: 0} items")
                NetworkResult.Success(normalizedFeed)
            }
            else -> result
        }
    }
}
