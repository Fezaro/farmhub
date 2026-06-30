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
        private const val PAGE_SIZE = 20
        private const val MAX_PAGE_REQUESTS = 20
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

        try {
            val allItems = mutableListOf<MediaItemResponse>()
            val seenKeys = mutableSetOf<String>()
            var page = 1
            var pagesFetched = 0
            var hasMorePages = true

            while (hasMorePages && pagesFetched < MAX_PAGE_REQUESTS) {
                when (val result = safeApiCall {
                    ApiClient.userService.getMediaFeed(page = page, limit = PAGE_SIZE).execute()
                }) {
                    is NetworkResult.Success -> {
                        val items = result.data.media.orEmpty()
                        if (items.isEmpty()) {
                            hasMorePages = false
                        } else {
                            val before = allItems.size
                            items.forEach { item ->
                                val key = item.id
                                    ?: "${item.title.orEmpty()}|${item.createdAt.orEmpty()}|${item.resolvedMediaUrl().orEmpty()}"
                                if (seenKeys.add(key)) {
                                    allItems.add(item)
                                }
                            }
                            val added = allItems.size - before
                            pagesFetched++
                            page++
                            // Stop at last page (short page) or if backend repeats the same page.
                            if (items.size < PAGE_SIZE || added == 0) {
                                hasMorePages = false
                            }
                        }
                    }
                    is NetworkResult.Error -> {
                        if (allItems.isEmpty()) {
                            return@withContext result
                        } else {
                            hasMorePages = false
                        }
                    }
                    else -> {
                        if (allItems.isEmpty()) {
                            return@withContext result
                        } else {
                            hasMorePages = false
                        }
                    }
                }
            }
            
            val normalizedFeed = MediaFeedResponse(media = normalizeItems(allItems))
            val sample = normalizedFeed.media?.firstOrNull()
            Log.d(
                TAG,
                "Media contract sample: id=${sample?.id}, title=${sample?.title}, mediaUrl=${sample?.mediaUrl}, thumbnailUrl=${sample?.thumbnailUrl}, mediaType=${sample?.resolvedMediaType()}, category=${sample?.category}, subcategory=${sample?.subcategory}"
            )
            cachedFeed = normalizedFeed
            lastFetchAtMs = System.currentTimeMillis()
            Log.d(TAG, "Media feed loaded: ${normalizedFeed.media?.size ?: 0} items")
            NetworkResult.Success(normalizedFeed)
        } catch (e: Exception) {
            Log.e(TAG, "Exception while fetching paginated media: ${e.message}", e)
            NetworkResult.Error(com.farm_tech.farmhub.network.ApiException.Network(e.message ?: "Media request failed"))
        }
    }
}
