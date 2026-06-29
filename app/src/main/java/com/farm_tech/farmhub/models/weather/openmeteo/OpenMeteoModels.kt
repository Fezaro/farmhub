package com.farm_tech.farmhub.models.weather.openmeteo

import com.google.gson.annotations.SerializedName

data class OpenMeteoForecastResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerializedName("current") val current: OpenMeteoCurrent? = null,
    @SerializedName("hourly") val hourly: OpenMeteoHourly? = null,
    @SerializedName("daily") val daily: OpenMeteoDaily? = null
)

data class OpenMeteoCurrent(
    @SerializedName("time") val time: String? = null,
    @SerializedName("temperature_2m") val temperature2m: Double? = null,
    @SerializedName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerializedName("weather_code") val weatherCode: Int? = null,
    @SerializedName("relative_humidity_2m") val relativeHumidity2m: Int? = null,
    @SerializedName("wind_speed_10m") val windSpeed10m: Double? = null,
    @SerializedName("uv_index") val uvIndex: Double? = null
)

data class OpenMeteoHourly(
    @SerializedName("time") val time: List<String>? = null,
    @SerializedName("temperature_2m") val temperature2m: List<Double>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int>? = null
)

data class OpenMeteoDaily(
    @SerializedName("time") val time: List<String>? = null,
    @SerializedName("temperature_2m_max") val temperature2mMax: List<Double>? = null,
    @SerializedName("temperature_2m_min") val temperature2mMin: List<Double>? = null,
    @SerializedName("weather_code") val weatherCode: List<Int>? = null,
    @SerializedName("sunrise") val sunrise: List<String>? = null,
    @SerializedName("sunset") val sunset: List<String>? = null
)

data class OpenMeteoReverseGeocodingResponse(
    val results: List<OpenMeteoLocationResult>? = null
)

data class OpenMeteoLocationResult(
    val name: String? = null,
    val admin1: String? = null,
    val country: String? = null
)
