package com.farm_tech.farmhub.repository

import android.content.Context
import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.models.media.MediaFeedResponse
import com.farm_tech.farmhub.models.media.MediaItemResponse
import com.farm_tech.farmhub.models.media.MediaTaxonomy
import com.farm_tech.farmhub.models.media.MediaTaxonomyCategory
import com.farm_tech.farmhub.models.media.MediaTaxonomyCategoryResponse
import com.farm_tech.farmhub.models.media.MediaUrlNormalizer
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.network.safeApiCall
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository {
    companion object {
        private const val TAG = "MediaRepository"
        private const val FEED_CACHE_TTL_MS = 30_000L
        private const val TAXONOMY_CACHE_TTL_MS = 5 * 60_000L
        private const val PAGE_SIZE = 20
        private const val MAX_PAGE_REQUESTS = 20

        private const val PREFS_NAME = "media_repository_cache"
        private const val PREF_TAXONOMY_JSON = "media_taxonomy_json"
        private const val PREF_TAXONOMY_TS = "media_taxonomy_timestamp"

        @Volatile private var appContext: Context? = null
        @Volatile private var cachedFeed: MediaFeedResponse? = null
        @Volatile private var lastFeedFetchAtMs: Long = 0L
        @Volatile private var cachedTaxonomy: MediaTaxonomy? = null
        @Volatile private var lastTaxonomyFetchAtMs: Long = 0L

        fun initialize(context: Context) {
            appContext = context.applicationContext
        }
    }

    private val gson = Gson()

    private fun normalizeItems(items: List<MediaItemResponse>?): List<MediaItemResponse> {
        return items.orEmpty().map { item ->
            item.copy(
                thumbnailUrl = MediaUrlNormalizer.normalize(item.resolvedThumbnailUrl()),
                mediaUrl = MediaUrlNormalizer.normalize(item.resolvedMediaUrl())
            )
        }
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun persistTaxonomy(taxonomy: MediaTaxonomy) {
        val sharedPrefs = prefs() ?: return
        sharedPrefs.edit()
            .putString(PREF_TAXONOMY_JSON, gson.toJson(taxonomy))
            .putLong(PREF_TAXONOMY_TS, taxonomy.fetchedAtMs)
            .apply()
    }

    private fun readPersistedTaxonomy(): MediaTaxonomy? {
        val sharedPrefs = prefs() ?: return null
        val json = sharedPrefs.getString(PREF_TAXONOMY_JSON, null) ?: return null
        return try {
            val type = object : TypeToken<MediaTaxonomy>() {}.type
            gson.fromJson<MediaTaxonomy>(json, type)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse persisted taxonomy: ${e.message}")
            null
        }
    }

    private fun isFresh(lastUpdated: Long, ttl: Long): Boolean {
        if (lastUpdated <= 0L) return false
        return System.currentTimeMillis() - lastUpdated < ttl
    }

    private fun deriveTaxonomyFromCategoryRows(rows: List<MediaTaxonomyCategoryResponse>?): LinkedHashMap<String, LinkedHashSet<String>> {
        val result = linkedMapOf<String, LinkedHashSet<String>>()
        rows.orEmpty().forEach { row ->
            val category = row.category?.trim().orEmpty()
            if (category.isBlank()) return@forEach
            val subSet = result.getOrPut(category) { linkedSetOf() }
            row.subcategories.orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { subSet.add(it) }
        }
        return result
    }

    private fun mergeMapSubcategories(
        target: LinkedHashMap<String, LinkedHashSet<String>>,
        source: Map<String, List<String>>?
    ) {
        source.orEmpty().forEach { (categoryName, subcategories) ->
            val category = categoryName.trim()
            if (category.isBlank()) return@forEach
            val subSet = target.getOrPut(category) { linkedSetOf() }
            subcategories.map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { subSet.add(it) }
        }
    }

    private fun buildTaxonomyFromFeed(feed: MediaFeedResponse, fallbackItems: List<MediaItemResponse>): MediaTaxonomy {
        val categoryMap = linkedMapOf<String, LinkedHashSet<String>>()

        deriveTaxonomyFromCategoryRows(feed.categories).forEach { (category, subcategories) ->
            categoryMap.getOrPut(category) { linkedSetOf() }.addAll(subcategories)
        }
        deriveTaxonomyFromCategoryRows(feed.taxonomy?.categories).forEach { (category, subcategories) ->
            categoryMap.getOrPut(category) { linkedSetOf() }.addAll(subcategories)
        }

        mergeMapSubcategories(categoryMap, feed.subcategories)
        mergeMapSubcategories(categoryMap, feed.taxonomy?.subcategories)

        fallbackItems.forEach { item ->
            val category = item.category?.trim().orEmpty()
            if (category.isBlank()) return@forEach
            val subSet = categoryMap.getOrPut(category) { linkedSetOf() }
            val subcategory = item.subcategory?.trim().orEmpty()
            if (subcategory.isNotBlank()) {
                subSet.add(subcategory)
            }
        }

        val categories = categoryMap.map { (category, subSet) ->
            MediaTaxonomyCategory(category = category, subcategories = subSet.toList())
        }
        return MediaTaxonomy(categories = categories, fetchedAtMs = System.currentTimeMillis())
    }

    private fun updateTaxonomyCache(taxonomy: MediaTaxonomy) {
        cachedTaxonomy = taxonomy
        lastTaxonomyFetchAtMs = taxonomy.fetchedAtMs
        persistTaxonomy(taxonomy)
    }

    private suspend fun fetchAllMediaPages(): NetworkResult<MediaFeedResponse> = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<MediaItemResponse>()
        val seenKeys = mutableSetOf<String>()
        var page = 1
        var pagesFetched = 0
        var hasMorePages = true
        var latestResponse: MediaFeedResponse? = null

        while (hasMorePages && pagesFetched < MAX_PAGE_REQUESTS) {
            when (val result = safeApiCall {
                ApiClient.userService.getMediaFeed(page = page, limit = PAGE_SIZE).execute()
            }) {
                is NetworkResult.Success -> {
                    latestResponse = result.data
                    val items = result.data.media.orEmpty()
                    if (items.isEmpty()) {
                        hasMorePages = false
                    } else {
                        val before = allItems.size
                        items.forEach { item ->
                            val key = item.id
                                ?: "${item.title.orEmpty()}|${item.resolvedUploadedAt().orEmpty()}|${item.resolvedMediaUrl().orEmpty()}"
                            if (seenKeys.add(key)) {
                                allItems.add(item)
                            }
                        }
                        val added = allItems.size - before
                        pagesFetched++
                        page++
                        if (items.size < PAGE_SIZE || added == 0) {
                            hasMorePages = false
                        }
                    }
                }
                is NetworkResult.Error -> {
                    if (allItems.isEmpty()) return@withContext result
                    hasMorePages = false
                }
                else -> {
                    if (allItems.isEmpty()) return@withContext result
                    hasMorePages = false
                }
            }
        }

        val normalizedItems = normalizeItems(allItems)
        val mergedResponse = MediaFeedResponse(
            status = latestResponse?.status,
            media = normalizedItems,
            categories = latestResponse?.categories,
            subcategories = latestResponse?.subcategories,
            taxonomy = latestResponse?.taxonomy
        )
        val taxonomy = buildTaxonomyFromFeed(mergedResponse, normalizedItems)
        updateTaxonomyCache(taxonomy)

        val sample = mergedResponse.media?.firstOrNull()
        Log.d(
            TAG,
            "Media contract sample: id=${sample?.id}, title=${sample?.title}, mediaUrl=${sample?.mediaUrl}, thumbnailUrl=${sample?.thumbnailUrl}, company=${sample?.company}, category=${sample?.category}, subcategory=${sample?.subcategory}, author=${sample?.author}, duration=${sample?.duration}, uploadedAt=${sample?.resolvedUploadedAt()}"
        )
        Log.d(TAG, "Media feed loaded: ${mergedResponse.media?.size ?: 0} items")
        NetworkResult.Success(mergedResponse)
    }

    suspend fun getMediaFeed(force: Boolean = false): NetworkResult<MediaFeedResponse> = withContext(Dispatchers.IO) {
        if (!force && cachedFeed != null && isFresh(lastFeedFetchAtMs, FEED_CACHE_TTL_MS)) {
            return@withContext NetworkResult.Success(cachedFeed!!)
        }

        when (val result = fetchAllMediaPages()) {
            is NetworkResult.Success -> {
                cachedFeed = result.data
                lastFeedFetchAtMs = System.currentTimeMillis()
                result
            }
            else -> result
        }
    }

    suspend fun getMediaTaxonomy(force: Boolean = false): NetworkResult<MediaTaxonomy> = withContext(Dispatchers.IO) {
        val memoryCached = cachedTaxonomy
        if (!force && memoryCached != null && isFresh(lastTaxonomyFetchAtMs, TAXONOMY_CACHE_TTL_MS)) {
            return@withContext NetworkResult.Success(memoryCached)
        }

        val persisted = readPersistedTaxonomy()
        if (!force && persisted != null && isFresh(persisted.fetchedAtMs, TAXONOMY_CACHE_TTL_MS)) {
            cachedTaxonomy = persisted
            lastTaxonomyFetchAtMs = persisted.fetchedAtMs
            return@withContext NetworkResult.Success(persisted)
        }

        when (val feedResult = getMediaFeed(force = force)) {
            is NetworkResult.Success -> {
                val taxonomy = buildTaxonomyFromFeed(feedResult.data, feedResult.data.media.orEmpty())
                updateTaxonomyCache(taxonomy)
                NetworkResult.Success(taxonomy)
            }
            is NetworkResult.Error -> {
                if (persisted != null) {
                    Log.w(TAG, "Using offline taxonomy cache due to network error")
                    cachedTaxonomy = persisted
                    lastTaxonomyFetchAtMs = persisted.fetchedAtMs
                    NetworkResult.Success(persisted)
                } else {
                    feedResult
                }
            }
            is NetworkResult.Empty -> {
                if (persisted != null) {
                    cachedTaxonomy = persisted
                    lastTaxonomyFetchAtMs = persisted.fetchedAtMs
                    NetworkResult.Success(persisted)
                } else {
                    NetworkResult.Empty
                }
            }
            NetworkResult.Loading -> NetworkResult.Loading
        }
    }
}
