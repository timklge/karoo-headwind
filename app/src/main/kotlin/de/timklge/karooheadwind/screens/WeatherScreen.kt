package de.timklge.karooheadwind.screens

import android.graphics.BitmapFactory
import android.util.Log
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
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.PrecipitationUnit
import de.timklge.karooheadwind.R
import de.timklge.karooheadwind.TemperatureUnit
import de.timklge.karooheadwind.WeatherInterpretation
import de.timklge.karooheadwind.datatypes.WeatherDataType.Companion.timeFormatter
import de.timklge.karooheadwind.datatypes.WeatherForecastDataType
import de.timklge.karooheadwind.getGpsCoordinateFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamStats
import de.timklge.karooheadwind.streamUpcomingRoute
import de.timklge.karooheadwind.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable
fun WeatherScreen(onFinish: () -> Unit) {
    var karooConnected by remember { mutableStateOf<Boolean?>(null) }
    val ctx = LocalContext.current
            val karooSystem = remember { KarooSystemService(ctx) }

    val profile by karooSystem.streamUserProfile().collectAsStateWithLifecycle(null)
    val stats by ctx.streamStats().collectAsStateWithLifecycle(HeadwindStats())
    val location by karooSystem.getGpsCoordinateFlow(ctx).collectAsStateWithLifecycle(null)
    val weatherData by ctx.streamCurrentWeatherData().collectAsStateWithLifecycle(emptyList())

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

        val currentWeatherData = weatherData.firstOrNull()?.data
        val requestedWeatherPosition = weatherData.firstOrNull()?.requestedPosition

        val formattedTime = currentWeatherData?.let { timeFormatter.format(Instant.ofEpochSecond(currentWeatherData.current.time)) }
        val formattedDate = currentWeatherData?.let { Instant.ofEpochSecond(currentWeatherData.current.time).atZone(ZoneId.systemDefault()).toLocalDate().format(
            DateTimeFormatter.ofLocalizedDate(
            FormatStyle.SHORT))
        }

        if (karooConnected == true && currentWeatherData != null) {
            WeatherWidget(
                dateLabel = formattedDate,
                timeLabel = formattedTime,
                baseBitmap = baseBitmap,
                current = WeatherInterpretation.fromWeatherCode(currentWeatherData.current.weatherCode),
                windBearing = currentWeatherData.current.windDirection.roundToInt(),
                windSpeed = currentWeatherData.current.windSpeed.roundToInt(),
                windGusts = currentWeatherData.current.windGusts.roundToInt(),
                precipitation = currentWeatherData.current.precipitation,
                temperature = currentWeatherData.current.temperature.toInt(),
                temperatureUnit = if(profile?.preferredUnit?.temperature == UserProfile.PreferredUnit.UnitType.METRIC) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT,
                isImperial = profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL,
                precipitationUnit = if (profile?.preferredUnit?.distance != UserProfile.PreferredUnit.UnitType.IMPERIAL) PrecipitationUnit.MILLIMETERS else PrecipitationUnit.INCH,
                distance = requestedWeatherPosition?.let { l -> location?.distanceTo(l)?.times(1000) },
                includeDistanceLabel = false,
            )
        }

        val lastPosition = location?.let { l -> stats.lastSuccessfulWeatherPosition?.distanceTo(l) }
        val lastPositionDistanceStr =
            lastPosition?.let { dist -> " (${dist.roundToInt()} km away)" } ?: ""

        if (stats.failedWeatherRequest != null && (stats.lastSuccessfulWeatherRequest == null || stats.failedWeatherRequest!! > stats.lastSuccessfulWeatherRequest!!)) {
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
            val providerName = stats.lastSuccessfulWeatherProvider?.label ?: "Unknow"


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

        val upcomingRoute by karooSystem.streamUpcomingRoute().collectAsStateWithLifecycle(null)

        for (index in 1..12){
            val positionIndex = if (weatherData.size == 1) 0 else index

            if (weatherData.getOrNull(positionIndex) == null) break
            if (index >= (weatherData.getOrNull(positionIndex)?.data?.forecastData?.weatherCode?.size ?: 0)) {
                break
            }

            val data = weatherData.getOrNull(positionIndex)?.data
            val distanceAlongRoute = weatherData.getOrNull(positionIndex)?.requestedPosition?.distanceAlongRoute
            val position = weatherData.getOrNull(positionIndex)?.requestedPosition?.let { "${(it.distanceAlongRoute?.div(1000.0))?.toInt()} at ${it.lat}, ${it.lon}" }

            Log.d(KarooHeadwindExtension.TAG, "Distance along route index ${positionIndex}: $position")

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

            val interpretation = WeatherInterpretation.fromWeatherCode(data?.forecastData?.weatherCode?.get(index) ?: 0)
            val unixTime = data?.forecastData?.time?.get(index) ?: 0
            val formattedForecastTime = WeatherForecastDataType.timeFormatter.format(Instant.ofEpochSecond(unixTime))
            val formattedForecastDate = Instant.ofEpochSecond(unixTime).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))

            WeatherWidget(
                baseBitmap,
                current = interpretation,
                windBearing = data?.forecastData?.windDirection?.get(index)?.roundToInt() ?: 0,
                windSpeed = data?.forecastData?.windSpeed?.get(index)?.roundToInt() ?: 0,
                windGusts = data?.forecastData?.windGusts?.get(index)?.roundToInt() ?: 0,
                precipitation = data?.forecastData?.precipitation?.get(index) ?: 0.0,
                precipitationProbability = data?.forecastData?.precipitationProbability?.get(index) ?: 0,
                temperature = data?.forecastData?.temperature?.get(index)?.roundToInt() ?: 0,
                temperatureUnit = if (profile?.preferredUnit?.temperature != UserProfile.PreferredUnit.UnitType.IMPERIAL) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT,
                timeLabel = formattedForecastTime,
                dateLabel = formattedForecastDate,
                distance = distanceFromCurrent,
                isImperial = profile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL,
                precipitationUnit = if (profile?.preferredUnit?.distance != UserProfile.PreferredUnit.UnitType.IMPERIAL) PrecipitationUnit.MILLIMETERS else PrecipitationUnit.INCH,
                includeDistanceLabel = true
            )
        }

        Spacer(modifier = Modifier.padding(30.dp))
    }
}