package com.farm_tech.farmhub.api

import com.farm_tech.farmhub.models.weather.openmeteo.OpenMeteoForecastResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {
    @GET("v1/forecast")
    fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,weather_code,relative_humidity_2m,wind_speed_10m,uv_index",
        @Query("hourly") hourly: String = "temperature_2m,weather_code",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7
    ): Call<OpenMeteoForecastResponse>
}

interface WeatherGeocodingService {
    @GET("v1/reverse")
    fun reverseGeocode(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): Call<com.farm_tech.farmhub.models.weather.openmeteo.OpenMeteoReverseGeocodingResponse>
}
