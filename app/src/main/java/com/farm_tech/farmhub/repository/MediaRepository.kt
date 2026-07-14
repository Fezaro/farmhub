package com.farm_tech.farmhub.repository

import android.content.Context
import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.BuildConfig
import com.farm_tech.farmhub.models.media.MediaFeedResponse
import com.farm_tech.farmhub.models.media.MediaItemResponse
import com.farm_tech.farmhub.models.media.MediaTaxonomy
import com.farm_tech.farmhub.models.media.MediaTaxonomyCategory
import com.farm_tech.farmhub.models.media.MediaTaxonomyCategoryResponse
import com.farm_tech.farmhub.models.media.MediaUrlNormalizer
import com.farm_tech.farmhub.network.ApiException
import com.farm_tech.farmhub.network.ErrorMapper
import com.farm_tech.farmhub.network.NetworkResult
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository {
    companion object {
        private const val TAG = "MediaRepository"
        private const val FEED_CACHE_TTL_MS = 30_000L
        private const val TAXONOMY_CACHE_TTL_MS = 5 * 60_000L

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

    private fun correlationIdFor(responseHeaders: okhttp3.Headers): String {
        val keys = listOf("X-Correlation-Id", "x-correlation-id", "X-Request-Id", "x-request-id")
        return keys.firstNotNullOfOrNull { key -> responseHeaders[key] } ?: "n/a"
    }

    private fun safeSnippet(raw: String?, limit: Int = 600): String {
        val value = raw.orEmpty().replace("\n", " ").trim()
        if (value.isBlank()) return "<empty>"
        return if (value.length <= limit) value else value.take(limit) + "..."
    }

    private fun parseMediaFeedResponse(rawBody: String, endpoint: String, correlationId: String): MediaFeedResponse {
        val mediaListType = object : TypeToken<List<MediaItemResponse>>() {}.type
        val root = JsonParser().parse(rawBody)

        fun asJsonObjectOrNull(element: JsonElement?): com.google.gson.JsonObject? {
            return try {
                if (element == null || element.isJsonNull || !element.isJsonObject) null else element.asJsonObject
            } catch (_: Exception) {
                null
            }
        }

        fun asJsonArrayOrNull(element: JsonElement?): com.google.gson.JsonArray? {
            return try {
                if (element == null || element.isJsonNull || !element.isJsonArray) null else element.asJsonArray
            } catch (_: Exception) {
                null
            }
        }

        fun readString(source: com.google.gson.JsonObject, key: String): String? {
            val element = source.get(key) ?: return null
            return try {
                if (element.isJsonNull || !element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
                element.asJsonPrimitive.asString.trim().takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }

        fun readMediaItems(element: JsonElement?): List<MediaItemResponse> {
            val array = asJsonArrayOrNull(element) ?: return emptyList()
            return gson.fromJson(array, mediaListType)
        }

        val parsed = when {
            root.isJsonArray -> {
                val mediaItems: List<MediaItemResponse> = gson.fromJson(root, mediaListType)
                MediaFeedResponse(media = mediaItems)
            }
            root.isJsonObject -> {
                val rootObj = root.asJsonObject

                // Backend shape drift support: some deployments wrap feed in a `data` object.
                val wrappedData = rootObj.get("data")
                val wrappedDataObject = asJsonObjectOrNull(wrappedData)
                val wrappedDataArray = asJsonArrayOrNull(wrappedData)
                val rootMediaItems = sequenceOf("media", "videos", "items", "results")
                    .map { key -> readMediaItems(rootObj.get(key)) }
                    .firstOrNull { it.isNotEmpty() }
                    .orEmpty()

                if (rootMediaItems.isNotEmpty()) {
                    MediaFeedResponse(
                        status = readString(rootObj, "status"),
                        message = readString(rootObj, "message"),
                        media = rootMediaItems
                    )
                } else if (wrappedDataObject != null) {
                    val wrappedMediaItems = sequenceOf("media", "videos", "items", "results")
                        .map { key -> readMediaItems(wrappedDataObject.get(key)) }
                        .firstOrNull { it.isNotEmpty() }
                        .orEmpty()
                    val nestedDataItems = readMediaItems(wrappedDataObject.get("data"))
                    MediaFeedResponse(
                        status = readString(wrappedDataObject, "status") ?: readString(rootObj, "status"),
                        message = readString(wrappedDataObject, "message") ?: readString(rootObj, "message"),
                        media = if (wrappedMediaItems.isNotEmpty()) wrappedMediaItems else nestedDataItems
                    )
                } else if (wrappedDataArray != null) {
                    val mediaItems: List<MediaItemResponse> = gson.fromJson(wrappedDataArray, mediaListType)
                    MediaFeedResponse(
                        status = readString(rootObj, "status"),
                        message = readString(rootObj, "message"),
                        media = mediaItems
                    )
                } else {
                    MediaFeedResponse(
                        status = readString(rootObj, "status"),
                        message = readString(rootObj, "message"),
                        media = sequenceOf("media", "videos", "items", "results")
                            .map { key -> readMediaItems(rootObj.get(key)) }
                            .firstOrNull { it.isNotEmpty() }
                            .orEmpty()
                    )
                }
            }
            else -> {
                throw JsonParseException("Unexpected media response shape. endpoint=$endpoint correlationId=$correlationId")
            }
        }

        if (BuildConfig.DEBUG) {
            val topLevelKeys = if (root.isJsonObject) root.asJsonObject.keySet().joinToString(",") else "<array>"
            Log.d(
                TAG,
                "Media parse diagnostics: endpoint=$endpoint correlationId=$correlationId bodyLength=${rawBody.length} topLevelKeys=$topLevelKeys parsedMediaCount=${parsed.media?.size ?: 0} status=${parsed.status}"
            )
        }

        return parsed
    }

    private suspend fun fetchMediaFeed(): NetworkResult<MediaFeedResponse> = withContext(Dispatchers.IO) {
        val endpoint = "/media"
        try {
            val response = ApiClient.userService.getMediaFeedRaw().execute()
            val correlationId = correlationIdFor(response.headers())

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Media HTTP diagnostics: endpoint=$endpoint code=${response.code()} correlationId=$correlationId")
            }

            if (!response.isSuccessful) {
                val errorBody = try {
                    response.errorBody()?.string()
                } catch (readError: Exception) {
                    Log.w(TAG, "Failed reading media error body. endpoint=$endpoint correlationId=$correlationId", readError)
                    null
                }

                Log.e(
                    TAG,
                    "Media HTTP failure. endpoint=$endpoint code=${response.code()} correlationId=$correlationId body=${safeSnippet(errorBody)}"
                )
                return@withContext NetworkResult.Error(
                    ErrorMapper.fromHttpCode(response.code(), "Unable to load videos right now.")
                )
            }

            val rawBody = try {
                response.body()?.string()
            } catch (readError: Exception) {
                Log.e(TAG, "Media response body read failure. endpoint=$endpoint correlationId=$correlationId", readError)
                null
            }

            if (rawBody.isNullOrBlank()) {
                Log.w(TAG, "Media response is empty. endpoint=$endpoint code=${response.code()} correlationId=$correlationId")
                return@withContext NetworkResult.Empty
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Media body diagnostics: endpoint=$endpoint bodyLength=${rawBody.length} correlationId=$correlationId")
            }

            return@withContext try {
                val parsed = parseMediaFeedResponse(rawBody, endpoint, correlationId)
                NetworkResult.Success(parsed)
            } catch (converterFailure: Exception) {
                Log.e(
                    TAG,
                    "Media converter/DTO parse failure. endpoint=$endpoint code=${response.code()} correlationId=$correlationId raw=${safeSnippet(rawBody)}",
                    converterFailure
                )
                NetworkResult.Error(ApiException.Unknown("Unable to load videos right now."))
            }
        } catch (illegalConverter: IllegalArgumentException) {
            // Retrofit can throw here when converter creation fails for a service method.
            Log.e(TAG, "Retrofit converter creation failure for media endpoint=$endpoint", illegalConverter)
            NetworkResult.Error(ApiException.Unknown("Unable to load videos right now."))
        } catch (t: Throwable) {
            Log.e(TAG, "Media repository transport failure. endpoint=$endpoint", t)
            NetworkResult.Error(ErrorMapper.fromThrowable(t))
        }
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
            val category = item.resolvedCategoryLabel().orEmpty()
            if (category.isBlank()) return@forEach
            val subSet = categoryMap.getOrPut(category) { linkedSetOf() }
            val subcategory = item.resolvedSubcategoryLabel().orEmpty()
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
        when (val result = fetchMediaFeed()) {
            is NetworkResult.Success -> {
                val normalizedItems = normalizeItems(result.data.media)
                val mergedResponse = result.data.copy(media = normalizedItems)
                val taxonomy = buildTaxonomyFromFeed(mergedResponse, normalizedItems)
                updateTaxonomyCache(taxonomy)

                val sample = mergedResponse.media?.firstOrNull()
                Log.d(
                    TAG,
                    "Media contract sample: id=${sample?.id}, title=${sample?.title}, mediaUrl=${sample?.mediaUrl}, thumbnailUrl=${sample?.thumbnailUrl}, company=${sample?.company}, category=${sample?.resolvedCategoryLabel()}, subcategory=${sample?.resolvedSubcategoryLabel()}, author=${sample?.author}, duration=${sample?.duration}, uploadedAt=${sample?.resolvedUploadedAt()}"
                )
                Log.d(TAG, "Media feed loaded: ${mergedResponse.media?.size ?: 0} items")
                NetworkResult.Success(mergedResponse)
            }
            else -> result
        }
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
            is NetworkResult.Error -> {
                val fallback = cachedFeed
                if (fallback != null) {
                    Log.w(TAG, "Using stale cached media feed due to fetch error")
                    NetworkResult.Success(fallback)
                } else {
                    result
                }
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
