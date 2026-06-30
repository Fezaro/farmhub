package com.farm_tech.farmhub.viewmodel
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farm_tech.farmhub.network.ErrorMapper
import com.farm_tech.farmhub.network.NetworkResult
import com.farm_tech.farmhub.models.weather.DailyWeatherUi
import com.farm_tech.farmhub.models.weather.HourlyWeatherUi
import com.farm_tech.farmhub.models.weather.WeatherUiModel
import com.farm_tech.farmhub.repository.WeatherRepository
import com.farm_tech.farmhub.services.LocationService
import com.farm_tech.farmhub.util.FriendlyDateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
sealed class WeatherUiState {
    data object Idle : WeatherUiState()
    data object Loading : WeatherUiState()
    data class Success(val weather: WeatherUiModel) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
    data object LocationPermissionDenied : WeatherUiState()
}
class WeatherViewModel(context: Context) : ViewModel() {
    private val TAG = "WeatherViewModel"
    private val weatherRepository = WeatherRepository()
    private val locationService = LocationService(context)
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()
    fun loadWeather() {
        if (!locationService.hasLocationPermission()) {
            Log.w(TAG, "Location permissions not granted")
            _uiState.value = WeatherUiState.LocationPermissionDenied
            return
        }
        _uiState.value = WeatherUiState.Loading
        viewModelScope.launch {
            try {
                val location = locationService.getCurrentLocation()
                Log.d(TAG, "Got location: lat=${location.latitude} lon=${location.longitude}")
                when (val response = weatherRepository.getWeatherForecast(
                    location.latitude,
                    location.longitude
                )) {
                    is NetworkResult.Success -> {
                        val weatherData = response.data.data
                        if (weatherData != null) {
                            val uiModel = mapToWeatherUiModel(weatherData)
                            _uiState.value = WeatherUiState.Success(uiModel)
                        } else {
                            _uiState.value = WeatherUiState.Error("No weather data available")
                        }
                    }
                    is NetworkResult.Empty -> {
                        _uiState.value = WeatherUiState.Error("No weather data available")
                    }
                    is NetworkResult.Error -> {
                        _uiState.value = WeatherUiState.Error(ErrorMapper.toUserMessage(response.exception))
                    }
                    NetworkResult.Loading -> Unit
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading weather: ${e.message}")
                _uiState.value = WeatherUiState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }
    fun retry() {
        weatherRepository.clearCache()
        loadWeather()
    }
    private fun mapToWeatherUiModel(data: com.farm_tech.farmhub.models.weather.WeatherData): WeatherUiModel {
        val current = data.current
        val hourly = data.hourly?.mapNotNull { hour ->
            hour.timestamp?.let {
                HourlyWeatherUi(
                    time = it.take(5),
                    temperature = hour.temperature ?: 0.0,
                    condition = hour.condition ?: "",
                    icon = getWeatherIcon(hour.condition)
                )
            }
        } ?: emptyList()
        val daily = data.daily?.mapNotNull { day ->
            day.date?.let {
                DailyWeatherUi(
                    day = getDayName(it),
                    date = it,
                    maxTemp = day.temperatureMax ?: 0.0,
                    minTemp = day.temperatureMin ?: 0.0,
                    condition = day.condition ?: "",
                    icon = getWeatherIcon(day.condition)
                )
            }
        } ?: emptyList()
        return WeatherUiModel(
            currentTemperature = current?.temperature ?: 0.0,
            feelsLike = current?.feelsLike ?: 0.0,
            condition = current?.condition ?: "Unknown",
            icon = getWeatherIcon(current?.condition),
            humidity = current?.humidity ?: 0,
            windSpeed = current?.windSpeed ?: 0.0,
            uvIndex = current?.uvIndex ?: 0.0,
            sunrise = current?.sunrise?.take(5) ?: "--:--",
            sunset = current?.sunset?.take(5) ?: "--:--",
            locationName = data.location?.name ?: "Current Location",
            hourlyForecasts = hourly.take(12),
            dailyForecasts = daily.take(7)
        )
    }
    private fun getWeatherIcon(condition: String?): String {
        return when {
            condition?.contains("clear", ignoreCase = true) == true -> "☀"
            condition?.contains("cloud", ignoreCase = true) == true -> "☁"
            condition?.contains("rain", ignoreCase = true) == true -> "🌧"
            condition?.contains("snow", ignoreCase = true) == true -> "❄"
            condition?.contains("thunder", ignoreCase = true) == true -> "⛈"
            condition?.contains("fog", ignoreCase = true) == true -> "🌫"
            else -> "🌡"
        }
    }
    private fun getDayName(dateString: String?): String {
        val friendly = FriendlyDateTimeFormatter.toShortDate(dateString)
        if (friendly.isNotBlank()) return friendly
        return dateString ?: "Today"
    }
}
