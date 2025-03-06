package de.timklge.karooheadwind.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.timklge.karooheadwind.HeadwindStats
import de.timklge.karooheadwind.R
import de.timklge.karooheadwind.ServiceStatusSingleton
import de.timklge.karooheadwind.TemperatureUnit
import de.timklge.karooheadwind.weatherprovider.WeatherInterpretation
import de.timklge.karooheadwind.datatypes.ForecastDataType
import de.timklge.karooheadwind.datatypes.WeatherDataType.Companion.timeFormatter
import de.timklge.karooheadwind.datatypes.getShortDateFormatter
import de.timklge.karooheadwind.getGpsCoordinateFlow
import de.timklge.karooheadwind.streamCurrentForecastWeatherData
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamStats
import de.timklge.karooheadwind.streamUpcomingRoute
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.util.celciusInUserUnit
import de.timklge.karooheadwind.util.millimetersInUserUnit
import de.timklge.karooheadwind.util.msInUserUnit
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable
fun WeatherScreen(onFinish: () -> Unit) {
    var karooConnected by remember { mutableStateOf<Boolean?>(null) }
    val ctx = LocalContext.current
    val karooSystem = remember { KarooSystemService(ctx) }

    val profileFlow = remember { karooSystem.streamUserProfile() }
    val profile by profileFlow.collectAsStateWithLifecycle(null)
    
    val statsFlow = remember { ctx.streamStats() }
    val stats by statsFlow.collectAsStateWithLifecycle(HeadwindStats())
    
    val locationFlow = remember { karooSystem.getGpsCoordinateFlow(ctx) }
    val location by locationFlow.collectAsStateWithLifecycle(null)
    
    val currentWeatherDataFlow = remember { ctx.streamCurrentWeatherData(karooSystem) }
    val currentWeatherData by currentWeatherDataFlow.collectAsStateWithLifecycle(null)
    
    val forecastDataFlow = remember { ctx.streamCurrentForecastWeatherData() }
    val forecastData by forecastDataFlow.collectAsStateWithLifecycle(null)

    val upcomingRouteFlow = remember { karooSystem.streamUpcomingRoute() }
    val upcomingRoute by upcomingRouteFlow.collectAsStateWithLifecycle(null)

    val serviceStatusFlow = remember { ServiceStatusSingleton.getInstance().getServiceStatus() }
    val serviceStatus by serviceStatusFlow.collectAsStateWithLifecycle(false)

    val baseBitmap = BitmapFactory.decodeResource(
        ctx.resources,
        R.drawable.arrow_0
    )

    LaunchedEffect(Unit) {
        karooSystem.connect { connected ->
            karooConnected = connected
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            karooSystem.disconnect()
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(5.dp)) {
        if (karooConnected == false) {
            Text(
                modifier = Modifier.padding(5.dp),
                text = "Could not read device status. Is your Karoo updated?"
            )
        }

        val requestedWeatherPosition = forecastData?.data?.firstOrNull()?.coords

        val formattedTime = currentWeatherData?.let { timeFormatter.format(Instant.ofEpochSecond(it.time)) }
        val formattedDate = currentWeatherData?.let { getShortDateFormatter().format(Instant.ofEpochSecond(it.time)) }

        if (karooConnected == true && currentWeatherData != null) {
            WeatherWidget(
                baseBitmap = baseBitmap,
                current = WeatherInterpretation.fromWeatherCode(currentWeatherData?.weatherCode),
                windBearing = currentWeatherData?.windDirection?.roundToInt() ?: 0,
                windSpeed = msInUserUnit(currentWeatherData?.windSpeed ?: 0.0, profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL).roundToInt(),
                windGusts = msInUserUnit(currentWeatherData?.windGusts ?: 0.0, profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL).roundToInt(),
                precipitation = millimetersInUserUnit(currentWeatherData?.precipitation ?: 0.0, profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL),
                temperature = celciusInUserUnit(currentWeatherData?.temperature ?: 0.0, profile?.preferredUnit?.temperature == UserProfile.PreferredUnit.UnitType.IMPERIAL).roundToInt(),
                temperatureUnit = if(profile?.preferredUnit?.temperature == UserProfile.PreferredUnit.UnitType.METRIC) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT,
                timeLabel = formattedTime,
                dateLabel = formattedDate,
                distance = requestedWeatherPosition?.let { l -> location?.distanceTo(l)?.times(1000) },
                includeDistanceLabel = false,
                isImperial = profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL,
                isNight = currentWeatherData?.isNight == true
            )
        }

        val lastPosition = location?.let { l -> stats.lastSuccessfulWeatherPosition?.distanceTo(l) }
        val lastPositionDistanceStr =
            lastPosition?.let { dist -> " (${dist.roundToInt()} km away)" } ?: ""

        if (!serviceStatus){
            Text(
                modifier = Modifier.padding(5.dp),
                text = "Attempting to connect to weather background service... Please reboot your Karoo if this takes too long."
            )
        } else if (stats.failedWeatherRequest != null && (stats.lastSuccessfulWeatherRequest == null || stats.failedWeatherRequest!! > stats.lastSuccessfulWeatherRequest!!)) {
            val successfulTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(
                    stats.lastSuccessfulWeatherRequest ?: 0
                ), ZoneOffset.systemDefault()
            ).toLocalTime().truncatedTo(
                ChronoUnit.SECONDS
            )
            val successfulDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(
                    stats.lastSuccessfulWeatherRequest ?: 0
                ), ZoneOffset.systemDefault()
            ).toLocalDate()
            val lastTryTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(stats.failedWeatherRequest ?: 0),
                ZoneOffset.systemDefault()
            ).toLocalTime().truncatedTo(
                ChronoUnit.SECONDS
            )
            val lastTryDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(stats.failedWeatherRequest ?: 0),
                ZoneOffset.systemDefault()
            ).toLocalDate()

            val successStr =
                if (lastPosition != null) " Last data received at $successfulDate ${successfulTime}${lastPositionDistanceStr}." else ""
            Text(
                modifier = Modifier.padding(5.dp),
                text = "Failed to update weather data; last try at $lastTryDate ${lastTryTime}.${successStr}"
            )
        } else if (stats.lastSuccessfulWeatherRequest != null) {
            val localDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(
                    stats.lastSuccessfulWeatherRequest ?: 0
                ), ZoneOffset.systemDefault()
            ).toLocalDate()
            val localTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(
                    stats.lastSuccessfulWeatherRequest ?: 0
                ), ZoneOffset.systemDefault()
            ).toLocalTime().truncatedTo(
                ChronoUnit.SECONDS
            )

            val providerName = stats.lastSuccessfulWeatherProvider?.label ?: "Unknown"


            val providerName = stats.lastSuccessfulWeatherProvider?.label ?: "OpenMeteo"


            Text(
                modifier = Modifier.padding(5.dp),
                text = "Last weather data received from $providerName at $localDate ${localTime}${lastPositionDistanceStr}"
            )
        } else {
            Text(
                modifier = Modifier.padding(5.dp),
                text = "No weather data received yet, waiting for GPS fix..."
            )
        }

        for (index in 1..12){
            val positionIndex = if (forecastData?.data?.size == 1) 0 else index

            if (forecastData?.data?.getOrNull(positionIndex) == null) break
            if (index >= (forecastData?.data?.getOrNull(positionIndex)?.forecasts?.size ?: 0)) {
                break
            }

            val data = forecastData?.data?.getOrNull(positionIndex)
            val distanceAlongRoute = forecastData?.data?.getOrNull(positionIndex)?.coords?.distanceAlongRoute
            val position = forecastData?.data?.getOrNull(positionIndex)?.coords?.let { "${(it.distanceAlongRoute?.div(1000.0))?.toInt()} at ${it.lat}, ${it.lon}" }

            // Log.d(KarooHeadwindExtension.TAG, "Distance along route index ${positionIndex}: $position")

            if (index > 1) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color.Black
                        )
                        .height(1.dp)
                )
            }

            val distanceFromCurrent = upcomingRoute?.distanceAlongRoute?.let { currentDistanceAlongRoute ->
                distanceAlongRoute?.minus(currentDistanceAlongRoute)
            }

            val weatherData = data?.forecasts?.getOrNull(index)
            val interpretation = WeatherInterpretation.fromWeatherCode(weatherData?.weatherCode ?: 0)
            val unixTime = weatherData?.time ?: 0
            val formattedForecastTime = ForecastDataType.timeFormatter.format(Instant.ofEpochSecond(unixTime))
            val formattedForecastDate = getShortDateFormatter().format(Instant.ofEpochSecond(unixTime))

            WeatherWidget(
                baseBitmap,
                current = interpretation,
                windBearing = weatherData?.windDirection?.roundToInt() ?: 0,
                windSpeed = msInUserUnit(weatherData?.windSpeed ?: 0.0, profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL).roundToInt(),
                windGusts = msInUserUnit(weatherData?.windGusts ?: 0.0, profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL).roundToInt(),
                precipitation = millimetersInUserUnit(weatherData?.precipitation ?: 0.0, profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL),
                temperature = celciusInUserUnit(weatherData?.temperature ?: 0.0, profile?.preferredUnit?.temperature == UserProfile.PreferredUnit.UnitType.IMPERIAL).roundToInt(),
                temperatureUnit = if (profile?.preferredUnit?.temperature != UserProfile.PreferredUnit.UnitType.IMPERIAL) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT,
                timeLabel = formattedForecastTime,
                dateLabel = formattedForecastDate,
                distance = distanceFromCurrent,
                includeDistanceLabel = true,
                precipitationProbability = weatherData?.precipitationProbability?.toInt() ?: 0,
                isImperial = profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL,
                isNight = weatherData?.isNight == true,
            )
        }

        Spacer(modifier = Modifier.padding(30.dp))
    }
}
