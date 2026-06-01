package com.farm_tech.farmhub.repository
import android.content.Context
import android.util.Log
import com.farm_tech.farmhub.api.ApiClient
import com.farm_tech.farmhub.auth.TokenValidator
import com.farm_tech.farmhub.models.weather.WeatherForecastResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import retrofit2.Response
class WeatherRepository(private val context: Context) {
    companion object {
        private const val TAG = "WeatherRepository"
        private const val CACHE_DURATION_MS = 30 * 60 * 1000
    }
    private var cachedWeatherResponse: WeatherForecastResponse? = null
    private var lastFetchTime: Long = 0L
    suspend fun getWeatherForecast(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean = false
    ): Response<WeatherForecastResponse> = withContext(Dispatchers.IO) {
        if (!forceRefresh && isCacheValid()) {
            Log.d(TAG, "Returning cached weather data")
            return@withContext Response.success(cachedWeatherResponse)
        }
        if (!TokenValidator.isTokenValid()) {
            Log.w(TAG, "Token invalid, cannot fetch weather")
            return@withContext Response.error(
                401,
                ResponseBody.create("text/plain".toMediaTypeOrNull(), "No valid token")
            )
        }
        Log.d(TAG, "Fetching weather for lat:  lon: ")
        val resp = try {
            ApiClient.userService.getWeatherForecast(latitude, longitude).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ")
            return@withContext Response.error(500, ResponseBody.create(
                "text/plain".toMediaTypeOrNull(), e.message ?: "Network error"
            ))
        }
        if (resp.isSuccessful && resp.body() != null) {
            cachedWeatherResponse = resp.body()
            lastFetchTime = System.currentTimeMillis()
            Log.d(TAG, "Weather fetched successfully")
        } else {
            Log.e(TAG, "Weather fetch failed: ")
        }
        resp
    }
    private fun isCacheValid(): Boolean {
        val elapsedTime = System.currentTimeMillis() - lastFetchTime
        return cachedWeatherResponse != null && elapsedTime < CACHE_DURATION_MS
    }
    fun clearCache() {
        cachedWeatherResponse = null
        lastFetchTime = 0L
    }
}

