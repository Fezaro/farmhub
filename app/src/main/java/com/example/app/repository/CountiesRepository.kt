package com.example.app.repository

import android.util.Log
import com.example.app.api.ApiClient
import com.example.app.auth.TokenValidator
import com.example.app.models.geo.CountiesResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * Repository for accessing geographic data (counties/sub-counties).
 * Enforces token validation before making API calls.
 *
 * Senior practice: Centralized repository with caching to reduce API calls,
 * token validation, logging, and error handling in one place.
 */
class CountiesRepository {
    companion object {
        private const val TAG = "CountiesRepository"
    }

    private var cachedCounties: List<String>? = null
    private val subCountyCache = mutableMapOf<String, List<String>?>()

    /**
     * Fetches list of all counties with optional caching.
     * Enforces token validation before making the request.
     *
     * @param force Force refresh, ignoring cache
     * @return Response containing CountiesResponse with all counties
     */
    suspend fun getCounties(force: Boolean = false): Response<CountiesResponse> = withContext(Dispatchers.IO) {
        if (!force && cachedCounties != null) {
            Log.d(TAG, "Returning cached counties (count=${cachedCounties?.size ?: 0})")
            return@withContext Response.success(CountiesResponse(counties = cachedCounties, status = "cached"))
        }

        // Pre-flight token validation
        if (!TokenValidator.isTokenValid()) {
            Log.w(TAG, "Cannot fetch counties: token invalid or missing. Aborting request.")
            return@withContext Response.error(
                401,
                okhttp3.ResponseBody.create(null, "No valid authentication token")
            )
        }

        Log.d(TAG, "GET /data/counties tokenPresent=true, initiating request...")
        val resp = ApiClient.userService.getCounties(null).execute()

        if (resp.isSuccessful) {
            cachedCounties = resp.body()?.counties
            Log.d(TAG, "Counties fetch success. Cached ${resp.body()?.counties?.size ?: 0} counties")
        } else {
            Log.e(TAG, "Counties fetch error code=${resp.code()}")
        }
        resp
    }

    /**
     * Fetches sub-counties for a specific county with optional caching.
     * Enforces token validation before making the request.
     *
     * @param county The county name to fetch sub-counties for
     * @param force Force refresh, ignoring cache
     * @return Response containing CountiesResponse with sub-counties
     */
    suspend fun getSubCounties(county: String, force: Boolean = false): Response<CountiesResponse> = withContext(Dispatchers.IO) {
        if (!force && subCountyCache.containsKey(county)) {
            Log.d(TAG, "Returning cached sub-counties for $county (count=${subCountyCache[county]?.size ?: 0})")
            return@withContext Response.success(CountiesResponse(subCounties = subCountyCache[county], status = "cached"))
        }

        // Pre-flight token validation
        if (!TokenValidator.isTokenValid()) {
            Log.w(TAG, "Cannot fetch sub-counties for $county: token invalid or missing. Aborting request.")
            return@withContext Response.error(
                401,
                okhttp3.ResponseBody.create(null, "No valid authentication token")
            )
        }

        Log.d(TAG, "GET /data/counties?county=$county tokenPresent=true, initiating request...")
        val resp = ApiClient.userService.getCounties(county).execute()

        if (resp.isSuccessful) {
            subCountyCache[county] = resp.body()?.subCounties
            Log.d(TAG, "Sub-counties fetch success for $county. Cached ${resp.body()?.subCounties?.size ?: 0} items")
        } else {
            Log.e(TAG, "Sub-counties fetch error for $county code=${resp.code()}")
        }
        resp
    }
}
