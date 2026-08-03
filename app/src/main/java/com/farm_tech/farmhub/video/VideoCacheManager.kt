package com.farm_tech.farmhub.video

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Singleton that owns the shared ExoPlayer [SimpleCache] for video disk caching.
 *
 * Usage:
 *  - Call [getCache] to obtain (or lazily create) the shared cache instance.
 *  - Call [release] only when the application is terminating or during low-memory scenarios.
 *
 * Cache parameters:
 *  - Max size: 200 MB (suitable for several recently-watched farming videos)
 *  - Eviction: Least-Recently-Used
 *  - Location: app's internal cache dir / "exoplayer_video" (cleared by system on low storage)
 */
@androidx.annotation.OptIn(UnstableApi::class)
object VideoCacheManager {

    private const val TAG = "VideoCacheManager"
    private const val MAX_CACHE_BYTES = 200L * 1024L * 1024L // 200 MB

    @Volatile private var cache: SimpleCache? = null

    /**
     * Returns the shared [SimpleCache] instance, creating it on first call.
     * Thread-safe via double-checked locking.
     */
    @Synchronized
    fun getCache(context: Context): SimpleCache {
        return cache ?: run {
            val cacheDir = File(context.applicationContext.cacheDir, "exoplayer_video")
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            Log.d(TAG, "Creating SimpleCache at ${cacheDir.absolutePath} (max ${MAX_CACHE_BYTES / 1024 / 1024} MB)")
            SimpleCache(cacheDir, evictor, databaseProvider).also { cache = it }
        }
    }

    /**
     * Releases the cache. Call only when the cache will no longer be used
     * (e.g., Application.onTerminate or explicit cleanup). A new instance will
     * be created on the next [getCache] call.
     */
    fun release() {
        try {
            cache?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing cache: ${e.message}")
        } finally {
            cache = null
        }
    }
}
