package com.example.app.repository

import com.example.app.api.ApiClient
import com.example.app.models.geo.CountiesResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

class CountiesRepository {
    private var cachedCounties: List<String>? = null
    private val subCountyCache = mutableMapOf<String, List<String>?>()

    suspend fun getCounties(force: Boolean = false): Response<CountiesResponse> = withContext(Dispatchers.IO) {
        if (!force && cachedCounties != null) {
            Response.success(CountiesResponse(counties = cachedCounties, status = "cached"))
        } else {
            val resp = ApiClient.userService.getCounties(null).execute()
            if (resp.isSuccessful) {
                cachedCounties = resp.body()?.counties
            }
            resp
        }
    }

    suspend fun getSubCounties(county: String, force: Boolean = false): Response<CountiesResponse> = withContext(Dispatchers.IO) {
        if (!force && subCountyCache.containsKey(county)) {
            Response.success(CountiesResponse(subCounties = subCountyCache[county], status = "cached"))
        } else {
            val resp = ApiClient.userService.getCounties(county).execute()
            if (resp.isSuccessful) {
                subCountyCache[county] = resp.body()?.subCounties
            }
            resp
        }
    }
}

