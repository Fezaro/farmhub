package com.farm_tech.farmhub.repository

import android.content.Context
import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.BuildConfig
import com.farm_tech.farmhub.models.media.MediaFeedResponse
import com.farm_tech.farmhub.models.media.MediaItemResponse
import com.farm_tech.farmhub.models.media.MediaTaxonomy
import com.farm_tech.farmhub.models.media.MediaUrlNormalizer
import com.farm_tech.farmhub.network.ApiException
import com.farm_tech.farmhub.network.ErrorMapper
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.network.safeApiCall
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.farm_tech.farmhub.models.media.activeTaxonomy
import com.farm_tech.farmhub.network.MediaRetryPolicy
import com.farm_tech.farmhub.session.UserSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository {
    companion object {
        private const val TAG = "MediaRepository"
        private const val FEED_CACHE_TTL_MS = 30_000L
        private const val TAXONOMY_CACHE_TTL_MS = 30_000L

        private const val PREFS_NAME = "media_repository_cache"
        private const val PREF_TAXONOMY_JSON = "active_media_taxonomy_v2"

        @Volatile private var appContext: Context? = null
        @Volatile private var cachedFeed: MediaFeedResponse? = null
        @Volatile private var lastFeedFetchAtMs: Long = 0L
        @Volatile private var cachedTaxonomy: MediaTaxonomy? = null
        @Volatile private var cacheOwner: String? = null

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

    private fun prefs() = UserSession.userId?.let { owner ->
        appContext?.getSharedPreferences("${PREFS_NAME}_${owner}", Context.MODE_PRIVATE)
    }

    private fun checkCacheOwner() {
        val owner = UserSession.userId
        if (cacheOwner != owner) {
            cachedFeed = null
            cachedTaxonomy = null
            lastFeedFetchAtMs = 0L
            cacheOwner = owner
        }
    }

    private fun persistedFeed(): MediaFeedResponse? = try {
        val store = prefs()
        val timestamp = store?.getLong("feed_time_v1", 0L) ?: 0L
        if (isFresh(timestamp, 24 * 60 * 60 * 1000L)) {
            gson.fromJson(store?.getString("feed_v1", null), MediaFeedResponse::class.java)
        } else null
    } catch (_: Exception) { null }

    private fun persistFeed(feed: MediaFeedResponse) {
        val json = gson.toJson(feed)
        if (json.length <= 2_000_000) prefs()?.edit()?.putString("feed_v1", json)
            ?.putLong("feed_time_v1", System.currentTimeMillis())?.apply()
    }

    private fun persistTaxonomy(taxonomy: MediaTaxonomy) {
        val sharedPrefs = prefs() ?: return
        sharedPrefs.edit()
            .putString(PREF_TAXONOMY_JSON, gson.toJson(taxonomy))
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

    private suspend fun fetchMediaFeed(page: Int, size: Int): NetworkResult<MediaFeedResponse> = withContext(Dispatchers.IO) {
        val endpoint = "/media?page=$page&size=$size"
        try {
            var attempt = 0
            var response = ApiClient.userService.getMediaFeedRaw(page, size).execute()
            while (!response.isSuccessful) {
                val waitMs = MediaRetryPolicy.delayMs(response.code(), attempt, response.headers()["Retry-After"]?.toLongOrNull()) ?: break
                response.errorBody()?.close()
                delay(waitMs)
                attempt++
                response = ApiClient.userService.getMediaFeedRaw(page, size).execute()
            }
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
            if (t is CancellationException) throw t
            Log.e(TAG, "Media repository transport failure. endpoint=$endpoint", t)
            NetworkResult.Error(ErrorMapper.fromThrowable(t))
        }
    }

    private suspend fun fetchAdminTaxonomy(): NetworkResult<MediaTaxonomy> = withContext(Dispatchers.IO) {
        when (val result = safeApiCall { ApiClient.userService.getMediaCategories().execute() }) {
            is NetworkResult.Success -> try {
                NetworkResult.Success(result.data.activeTaxonomy())
            } catch (_: IllegalArgumentException) {
                NetworkResult.Error(ApiException.Unknown("Invalid category response"))
            }
            is NetworkResult.Error -> result
            else -> NetworkResult.Error(ApiException.Unknown("Missing category response"))
        }
    }

    private fun updateTaxonomyCache(taxonomy: MediaTaxonomy) {
        cachedTaxonomy = taxonomy
        persistTaxonomy(taxonomy)
    }

    private suspend fun fetchAllMediaPages(): NetworkResult<MediaFeedResponse> = withContext(Dispatchers.IO) {
        val pageSize = 100
        var page = 1
        val allItems = mutableListOf<MediaItemResponse>()
        var firstResponse: MediaFeedResponse? = null

        while (true) {
            when (val result = fetchMediaFeed(page, pageSize)) {
                is NetworkResult.Success -> {
                    firstResponse = firstResponse ?: result.data
                    val pageItems = result.data.media.orEmpty()
                    if (page > 100 || (page > 1 && pageItems.isNotEmpty() && pageItems.all { item -> allItems.any { it.id == item.id } })) {
                        return@withContext NetworkResult.Error(ApiException.Unknown("Media pagination did not advance"))
                    }
                    allItems.addAll(pageItems)
                    if (pageItems.size < pageSize) break
                    page += 1
                }
                is NetworkResult.Empty -> {
                    if (firstResponse == null) return@withContext NetworkResult.Empty
                    break
                }
                is NetworkResult.Error -> return@withContext result
                NetworkResult.Loading -> return@withContext NetworkResult.Loading
            }
        }

        val response = firstResponse ?: return@withContext NetworkResult.Empty
        val normalizedItems = normalizeItems(allItems)
        val mergedResponse = response.copy(media = normalizedItems)

                val sample = mergedResponse.media?.firstOrNull()
                Log.d(
                    TAG,
                    "Media contract sample: id=${sample?.id}, title=${sample?.title}, mediaUrl=${sample?.mediaUrl}, thumbnailUrl=${sample?.thumbnailUrl}, company=${sample?.company}, category=${sample?.resolvedCategoryLabel()}, subcategory=${sample?.resolvedSubcategoryLabel()}, author=${sample?.author}, duration=${sample?.duration}, uploadedAt=${sample?.resolvedUploadedAt()}"
                )
                Log.d(TAG, "Media feed loaded: ${mergedResponse.media?.size ?: 0} items")
        NetworkResult.Success(mergedResponse)
    }

    suspend fun getMediaFeed(force: Boolean = false): NetworkResult<MediaFeedResponse> = withContext(Dispatchers.IO) {
        checkCacheOwner()
        val owner = UserSession.userId
        if (!force && cachedFeed != null && isFresh(lastFeedFetchAtMs, FEED_CACHE_TTL_MS)) {
            return@withContext NetworkResult.Success(cachedFeed!!)
        }

        when (val result = fetchAllMediaPages()) {
            is NetworkResult.Success -> {
                if (UserSession.userId != owner) return@withContext NetworkResult.Error(ApiException.Unauthorized())
                persistFeed(result.data)
                cachedFeed = result.data
                lastFeedFetchAtMs = System.currentTimeMillis()
                result
            }
            is NetworkResult.Error -> {
                val fallback = cachedFeed ?: persistedFeed()
                if (fallback != null && result.exception !is ApiException.Unauthorized && result.exception !is ApiException.Forbidden && UserSession.userId == owner) {
                    Log.w(TAG, "Using stale cached media feed due to fetch error")
                    NetworkResult.Success(fallback.copy(cached = true))
                } else {
                    result
                }
            }
            else -> result
        }
    }

    suspend fun getMediaTaxonomy(force: Boolean = false): NetworkResult<MediaTaxonomy> = withContext(Dispatchers.IO) {
        checkCacheOwner()
        val owner = UserSession.userId
        val previous = cachedTaxonomy ?: readPersistedTaxonomy()
        if (!force && previous != null && isFresh(previous.fetchedAtMs, TAXONOMY_CACHE_TTL_MS)) {
            return@withContext NetworkResult.Success(previous)
        }
        when (val result = fetchAdminTaxonomy()) {
            is NetworkResult.Success -> {
                if (UserSession.userId != owner) return@withContext NetworkResult.Error(ApiException.Unauthorized())
                updateTaxonomyCache(result.data)
                result
            }
            is NetworkResult.Error -> {
                if (previous != null && result.exception !is ApiException.Unauthorized && result.exception !is ApiException.Forbidden && UserSession.userId == owner) {
                    NetworkResult.Success(previous.copy(stale = true))
                } else result
            }
            else -> result
        }
    }
}
