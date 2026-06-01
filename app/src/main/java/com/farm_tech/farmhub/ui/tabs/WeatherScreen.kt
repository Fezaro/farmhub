package com.farm_tech.farmhub.ui.tabs
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.farm_tech.farmhub.models.weather.DailyWeatherUi
import com.farm_tech.farmhub.models.weather.HourlyWeatherUi
import com.farm_tech.farmhub.viewmodel.WeatherUiState
import com.farm_tech.farmhub.viewmodel.WeatherViewModel
import androidx.compose.ui.platform.LocalContext
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    // 'activity' not required; avoid relying on AppCompatActivity to keep this composable reusable
    val viewModel = remember { WeatherViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()
    var showPermissionRationale by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.loadWeather()
        } else {
            showPermissionRationale = true
        }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.loadWeather()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Weather Forecast") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        when (uiState) {
            WeatherUiState.Idle -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            WeatherUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            WeatherUiState.LocationPermissionDenied -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Location Permission Required",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Please enable location permissions to view weather for your area",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
            is WeatherUiState.Success -> {
                val weather = (uiState as WeatherUiState.Success).weather
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { CurrentWeatherCard(weather) }
                    item { HourlyForecastSection(weather.hourlyForecasts) }
                    item { DailyForecastSection(weather.dailyForecasts) }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.retry() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                                Spacer(Modifier.width(4.dp))
                                Text("Refresh")
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
            is WeatherUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Failed to load weather",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            (uiState as WeatherUiState.Error).message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadWeather() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun CurrentWeatherCard(weather: com.farm_tech.farmhub.models.weather.WeatherUiModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = weather.locationName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${'$'}{weather.icon}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(weather.condition, style = MaterialTheme.typography.titleSmall)
                    Text("Feels like ${'$'}{weather.feelsLike.toInt()}°", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WeatherDetail("Humidity", "${'$'}{weather.humidity}%")
                WeatherDetail("Wind", "${'$'}{weather.windSpeed} km/h")
                WeatherDetail("UV Index", "${'$'}{weather.uvIndex}")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WeatherDetail("Sunrise", weather.sunrise)
                WeatherDetail("Sunset", weather.sunset)
            }
        }
    }
}
@Composable
private fun HourlyForecastSection(hourly: List<HourlyWeatherUi>) {
    Column {
        Text(
            text = "Hourly Forecast",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(hourly) { forecast ->
                HourlyCard(forecast)
            }
        }
    }
}
@Composable
private fun HourlyCard(forecast: HourlyWeatherUi) {
    Card(
        modifier = Modifier.width(80.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(forecast.time, style = MaterialTheme.typography.labelSmall)
            Text(forecast.icon, style = MaterialTheme.typography.titleMedium)
            Text("${'$'}{forecast.temperature.toInt()}°", style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable
private fun DailyForecastSection(daily: List<DailyWeatherUi>) {
    Column {
        Text(
            text = "7-Day Forecast",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            daily.forEach { forecast ->
                DailyCard(forecast)
            }
        }
    }
}
@Composable
private fun DailyCard(forecast: DailyWeatherUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(forecast.day, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(forecast.condition, style = MaterialTheme.typography.bodySmall)
            }
            Text(forecast.icon, style = MaterialTheme.typography.headlineMedium)
            Text("${'$'}{forecast.maxTemp.toInt()}° / ${'$'}{forecast.minTemp.toInt()}°", style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable
private fun WeatherDetail(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

