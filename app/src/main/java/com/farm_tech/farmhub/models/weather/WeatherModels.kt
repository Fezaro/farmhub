package com.farm_tech.farmhub.models.weather

data class WeatherForecastResponse(
    val status: String? = null,
    val data: WeatherData? = null
)

data class WeatherData(
    val current: CurrentWeather? = null,
    val hourly: List<HourlyForecast>? = null,
    val daily: List<DailyForecast>? = null,
    val location: LocationInfo? = null
)

data class CurrentWeather(
    val temperature: Double? = null,
    val feelsLike: Double? = null,
    val condition: String? = null,
    val icon: String? = null,
    val humidity: Int? = null,
    val windSpeed: Double? = null,
    val uvIndex: Double? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val pressure: Int? = null,
    val visibility: Int? = null,
    val cloudCover: Int? = null
)

data class HourlyForecast(
    val timestamp: String? = null,
    val temperature: Double? = null,
    val condition: String? = null,
    val icon: String? = null,
    val precipitation: Double? = null,
    val humidity: Int? = null,
    val windSpeed: Double? = null
)

data class DailyForecast(
    val date: String? = null,
    val temperatureMax: Double? = null,
    val temperatureMin: Double? = null,
    val condition: String? = null,
    val icon: String? = null,
    val precipitation: Double? = null,
    val humidity: Int? = null,
    val windSpeed: Double? = null,
    val uvIndex: Double? = null,
    val sunrise: String? = null,
    val sunset: String? = null
)

data class LocationInfo(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val name: String? = null,
    val region: String? = null,
    val country: String? = null,
    val timezone: String? = null
)

// UI Models for easier display
data class WeatherUiModel(
    val currentTemperature: Double = 0.0,
    val feelsLike: Double = 0.0,
    val condition: String = "Unknown",
    val icon: String = "🌤️",
    val humidity: Int = 0,
    val windSpeed: Double = 0.0,
    val uvIndex: Double = 0.0,
    val sunrise: String = "--:--",
    val sunset: String = "--:--",
    val locationName: String = "Current Location",
    val hourlyForecasts: List<HourlyWeatherUi> = emptyList(),
    val dailyForecasts: List<DailyWeatherUi> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class HourlyWeatherUi(
    val time: String = "",
    val temperature: Double = 0.0,
    val condition: String = "",
    val icon: String = "🌤️"
)

data class DailyWeatherUi(
    val day: String = "",
    val date: String = "",
    val maxTemp: Double = 0.0,
    val minTemp: Double = 0.0,
    val condition: String = "",
    val icon: String = "🌤️"
)


