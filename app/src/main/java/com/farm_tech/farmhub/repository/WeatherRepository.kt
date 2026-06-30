package com.farm_tech.farmhub.repository

import com.farm_tech.farmhub.api.WeatherApiClient
import com.farm_tech.farmhub.models.weather.CurrentWeather
import com.farm_tech.farmhub.models.weather.DailyForecast
import com.farm_tech.farmhub.models.weather.HourlyForecast
import com.farm_tech.farmhub.models.weather.LocationInfo
import com.farm_tech.farmhub.models.weather.WeatherData
import com.farm_tech.farmhub.models.weather.WeatherForecastResponse
import com.farm_tech.farmhub.models.weather.openmeteo.OpenMeteoCurrent
import com.farm_tech.farmhub.models.weather.openmeteo.OpenMeteoForecastResponse
import com.farm_tech.farmhub.models.weather.openmeteo.OpenMeteoLocationResult
import com.farm_tech.farmhub.models.weather.openmeteo.OpenMeteoReverseGeocodingResponse
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class WeatherRepository {
    companion object {
        private const val CACHE_DURATION_MS = 45 * 60 * 1000L
    }

    private data class CacheEntry(
        val latKey: String,
        val lonKey: String,
        val timestamp: Long,
        val response: WeatherForecastResponse
    )

    private var cache: CacheEntry? = null

    suspend fun getWeatherForecast(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean = false
    ): NetworkResult<WeatherForecastResponse> = withContext(Dispatchers.IO) {
        val latKey = latitude.toString().take(7)
        val lonKey = longitude.toString().take(7)
        val cached = cache
        if (!forceRefresh && cached != null &&
            cached.latKey == latKey &&
            cached.lonKey == lonKey &&
            System.currentTimeMillis() - cached.timestamp < CACHE_DURATION_MS
        ) {
            return@withContext NetworkResult.Success(cached.response)
        }

        coroutineScope {
            val forecast = async {
                safeApiCall { WeatherApiClient.weatherService.getForecast(latitude, longitude).execute() }
            }
            val reverseGeocode = async {
                safeApiCall { WeatherApiClient.geocodingService.reverseGeocode(latitude, longitude).execute() }
            }

            when (val result = forecast.await()) {
                is NetworkResult.Success -> {
                    val response = mapResponse(result.data, reverseGeocode.await())
                    cache = CacheEntry(latKey, lonKey, System.currentTimeMillis(), response)
                    NetworkResult.Success(response)
                }
                is NetworkResult.Empty -> NetworkResult.Empty
                is NetworkResult.Error -> NetworkResult.Error(result.exception)
                NetworkResult.Loading -> NetworkResult.Loading
            }
        }
    }

    fun clearCache() {
        cache = null
    }

    private fun mapResponse(
        source: OpenMeteoForecastResponse,
        locationResult: NetworkResult<OpenMeteoReverseGeocodingResponse>
    ): WeatherForecastResponse {
        val daily = mapDaily(source)
        return WeatherForecastResponse(
            status = "success",
            data = WeatherData(
                current = mapCurrent(source.current, daily),
                hourly = mapHourly(source),
                daily = daily,
                location = when (locationResult) {
                    is NetworkResult.Success -> mapLocation(locationResult.data.results?.firstOrNull())
                    else -> null
                }
            )
        )
    }

    private fun mapCurrent(current: OpenMeteoCurrent?, daily: List<DailyForecast>): CurrentWeather {
        return CurrentWeather(
            temperature = current?.temperature2m,
            feelsLike = current?.apparentTemperature,
            condition = conditionLabel(current?.weatherCode),
            icon = iconFor(current?.weatherCode),
            humidity = current?.relativeHumidity2m,
            windSpeed = current?.windSpeed10m,
            uvIndex = current?.uvIndex,
            sunrise = daily.firstOrNull()?.sunrise,
            sunset = daily.firstOrNull()?.sunset
        )
    }

    private fun mapHourly(source: OpenMeteoForecastResponse): List<HourlyForecast> {
        val hourly = source.hourly ?: return emptyList()
        return hourly.time.orEmpty().mapIndexedNotNull { index, time ->
            HourlyForecast(
                timestamp = time,
                temperature = hourly.temperature2m?.getOrNull(index),
                condition = conditionLabel(hourly.weatherCode?.getOrNull(index)),
                icon = iconFor(hourly.weatherCode?.getOrNull(index))
            )
        }
    }

    private fun mapDaily(source: OpenMeteoForecastResponse): List<DailyForecast> {
        val daily = source.daily ?: return emptyList()
        return daily.time.orEmpty().mapIndexedNotNull { index, date ->
            DailyForecast(
                date = date,
                temperatureMax = daily.temperature2mMax?.getOrNull(index),
                temperatureMin = daily.temperature2mMin?.getOrNull(index),
                condition = conditionLabel(daily.weatherCode?.getOrNull(index)),
                icon = iconFor(daily.weatherCode?.getOrNull(index)),
                sunrise = daily.sunrise?.getOrNull(index),
                sunset = daily.sunset?.getOrNull(index)
            )
        }
    }

    private fun mapLocation(result: OpenMeteoLocationResult?): LocationInfo? {
        if (result == null) return null
        val name = listOfNotNull(result.name, result.admin1, result.country).joinToString(", ").ifBlank { null }
        return LocationInfo(
            name = name,
            region = result.admin1,
            country = result.country
        )
    }

    private fun conditionLabel(code: Int?): String = when (code) {
        0 -> "Clear sky"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        in 95..99 -> "Thunderstorm"
        else -> "Unknown"
    }

    private fun iconFor(code: Int?): String = when (code) {
        0 -> "☀"
        1, 2 -> "🌤"
        3 -> "☁"
        45, 48 -> "🌫"
        in 51..67, in 80..82 -> "🌧"
        in 71..77 -> "❄"
        in 95..99 -> "⛈"
        else -> "🌡"
    }
}
