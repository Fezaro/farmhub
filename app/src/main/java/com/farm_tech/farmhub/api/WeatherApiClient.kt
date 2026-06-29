package com.farm_tech.farmhub.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object WeatherApiClient {
    private const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"
    private const val OPEN_METEO_GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/"

    private val weatherRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(OPEN_METEO_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val geocodingRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(OPEN_METEO_GEOCODING_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val weatherService: WeatherService by lazy { weatherRetrofit.create(WeatherService::class.java) }
    val geocodingService: WeatherGeocodingService by lazy { geocodingRetrofit.create(WeatherGeocodingService::class.java) }
}
