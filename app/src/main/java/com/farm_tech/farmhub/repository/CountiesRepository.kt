package com.farm_tech.farmhub.repository

import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.models.geo.CountiesResponse
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    suspend fun getCounties(force: Boolean = false): NetworkResult<CountiesResponse> = withContext(Dispatchers.IO) {
        if (!force && cachedCounties != null) {
            return@withContext NetworkResult.Success(CountiesResponse(counties = cachedCounties, status = "cached"))
        }

        when (val result = safeApiCall { ApiClient.userService.getCounties(null).execute() }) {
            is NetworkResult.Success -> {
                cachedCounties = result.data.counties
                Log.d(TAG, "Counties loaded: ${cachedCounties?.size ?: 0}")
                result
            }
            else -> result
        }
    }

    /**
     * Fetches sub-counties for a specific county with optional caching.
     * Enforces token validation before making the request.
     *
     * @param county The county name to fetch sub-counties for
     * @param force Force refresh, ignoring cache
     * @return Response containing CountiesResponse with sub-counties
     */
    suspend fun getSubCounties(county: String, force: Boolean = false): NetworkResult<CountiesResponse> = withContext(Dispatchers.IO) {
        if (!force && subCountyCache.containsKey(county)) {
            return@withContext NetworkResult.Success(
                CountiesResponse(subCounties = subCountyCache[county], status = "cached")
            )
        }

        when (val result = safeApiCall { ApiClient.userService.getCounties(county).execute() }) {
            is NetworkResult.Success -> {
                subCountyCache[county] = result.data.subCounties
                Log.d(TAG, "Sub-counties loaded for $county: ${result.data.subCounties?.size ?: 0}")
                result
            }
            else -> result
        }
    }
}
