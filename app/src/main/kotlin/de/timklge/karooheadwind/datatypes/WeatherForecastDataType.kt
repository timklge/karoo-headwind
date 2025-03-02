package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.width
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.HeadwindWidgetSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse
import de.timklge.karooheadwind.OpenMeteoData
import de.timklge.karooheadwind.OpenMeteoForecastData
import de.timklge.karooheadwind.TemperatureUnit
import de.timklge.karooheadwind.UpcomingRoute
import de.timklge.karooheadwind.WeatherDataResponse
import de.timklge.karooheadwind.WeatherInterpretation
import de.timklge.karooheadwind.getHeadingFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUpcomingRoute
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.streamWidgetSettings
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WeatherForecastDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-headwind", "weatherForecast") {
    private val glance = GlanceRemoteViews()

    companion object {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    }

    data class StreamData(val data: List<WeatherDataResponse>?, val settings: SettingsAndProfile,
                          val widgetSettings: HeadwindWidgetSettings? = null, val profile: UserProfile? = null,
                          val headingResponse: HeadingResponse? = null, val upcomingRoute: UpcomingRoute? = null)

    data class SettingsAndProfile(val settings: HeadwindSettings, val isImperial: Boolean)

    private fun previewFlow(settingsAndProfileStream: Flow<SettingsAndProfile>): Flow<StreamData> = flow {
        val settingsAndProfile = settingsAndProfileStream.firstOrNull()

        while (true){
            val data = (0..<10).map { index ->
                val timeAtFullHour = Instant.now().truncatedTo(ChronoUnit.HOURS).epochSecond
                val forecastTimes = (0..<12).map { timeAtFullHour + it * 60 * 60 }
                val forecastTemperatures = (0..<12).map { 20.0 + (-20..20).random() }
                val forecastPrecipitationPropability = (0..<12).map { (0..100).random() }
                val forecastPrecipitation = (0..<12).map { 0.0 + (0..10).random() }
                val forecastWeatherCodes = (0..<12).map { WeatherInterpretation.getKnownWeatherCodes().random() }
                val forecastWindSpeed = (0..<12).map { 0.0 + (0..10).random() }
                val forecastWindDirection = (0..<12).map { 0.0 + (0..360).random() }
                val forecastWindGusts = (0..<12).map { 0.0 + (0..10).random() }
                val weatherData = OpenMeteoCurrentWeatherResponse(
                    OpenMeteoData(Instant.now().epochSecond, 0, 20.0, 50, 3.0, 0, 1013.25, 980.0, 15.0, 30.0, 30.0, WeatherInterpretation.getKnownWeatherCodes().random()),
                    0.0, 0.0, "Europe/Berlin", 30.0, 0,

                    OpenMeteoForecastData(forecastTimes, forecastTemperatures, forecastPrecipitationPropability,
                        forecastPrecipitation, forecastWeatherCodes, forecastWindSpeed, forecastWindDirection,
                        forecastWindGusts)
                )

                val distancePerHour = settingsAndProfile?.settings?.getForecastMetersPerHour(settingsAndProfile.isImperial)?.toDouble() ?: 0.0
                val gpsCoords = GpsCoordinates(0.0, 0.0, distanceAlongRoute = index * distancePerHour)

                WeatherDataResponse(weatherData, gpsCoords)
            }


            emit(
                StreamData(data, SettingsAndProfile(HeadwindSettings(), settingsAndProfile?.isImperial == true))
            )

            delay(5_000)
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooHeadwindExtension.TAG, "Starting weather forecast view with $emitter")
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val baseBitmap = BitmapFactory.decodeResource(
            context.resources,
            de.timklge.karooheadwind.R.drawable.arrow_0
        )

        val settingsAndProfileStream = context.streamSettings(karooSystem).combine(karooSystem.streamUserProfile()) { settings, userProfile ->
            SettingsAndProfile(settings = settings, isImperial = userProfile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL)
        }

        val dataFlow = if (config.preview){
            previewFlow(settingsAndProfileStream)
        } else {
            combine(context.streamCurrentWeatherData(),
                settingsAndProfileStream,
                context.streamWidgetSettings(),
                karooSystem.getHeadingFlow(context),
                karooSystem.streamUpcomingRoute()) { weatherData, settings, widgetSettings, heading, upcomingRoute ->
                    StreamData(data = weatherData, settings = settings, widgetSettings = widgetSettings, headingResponse = heading, upcomingRoute = upcomingRoute)
            }
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(ShowCustomStreamState("", null))

            dataFlow.collect { (allData, settingsAndProfile, widgetSettings, userProfile, headingResponse, upcomingRoute) ->
                    Log.d(KarooHeadwindExtension.TAG, "Updating weather forecast view")

                    if (allData == null){
                        emitter.updateView(getErrorWidget(glance, context, settingsAndProfile.settings, headingResponse).remoteViews)

                        return@collect
                    }

                    val result = glance.compose(context, DpSize.Unspecified) {
                        var modifier = GlanceModifier.fillMaxSize()

                        if (!config.preview) modifier = modifier.clickable(onClick = actionRunCallback<CycleHoursAction>())

                        Row(modifier = modifier, horizontalAlignment = Alignment.Horizontal.Start) {
                            val hourOffset = widgetSettings?.currentForecastHourOffset ?: 0
                            val positionOffset = if (allData.size == 1) 0 else hourOffset

                            var previousDate: String? = let {
                                val unixTime = allData.getOrNull(positionOffset)?.data?.forecastData?.time?.getOrNull(hourOffset)
                                val formattedDate = unixTime?.let { Instant.ofEpochSecond(unixTime).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)) }

                                formattedDate
                            }

                            for (baseIndex in hourOffset..hourOffset + 2){
                                val positionIndex = if (allData.size == 1) 0 else baseIndex

                                if (allData.getOrNull(positionIndex) == null) break
                                if (baseIndex >= (allData.getOrNull(positionOffset)?.data?.forecastData?.weatherCode?.size ?: 0)) {
                                    break
                                }

                                val data = allData.getOrNull(positionIndex)?.data
                                val distanceAlongRoute = allData.getOrNull(positionIndex)?.requestedPosition?.distanceAlongRoute
                                val position = allData.getOrNull(positionIndex)?.requestedPosition?.let { "${(it.distanceAlongRoute?.div(1000.0))?.toInt()} at ${it.lat}, ${it.lon}" }

                                Log.d(KarooHeadwindExtension.TAG, "Distance along route ${positionIndex}: $position")

                                if (baseIndex > hourOffset) {
                                    Spacer(
                                        modifier = GlanceModifier.fillMaxHeight().background(
                                            ColorProvider(Color.Black, Color.White)
                                        ).width(1.dp)
                                    )
                                }

                                val distanceFromCurrent = upcomingRoute?.distanceAlongRoute?.let { currentDistanceAlongRoute ->
                                    distanceAlongRoute?.minus(currentDistanceAlongRoute)
                                }

                                val isCurrent = baseIndex == 0 && positionIndex == 0

                                if (isCurrent && data?.current != null){
                                    val interpretation = WeatherInterpretation.fromWeatherCode(data.current.weatherCode)
                                    val unixTime = data.current.time
                                    val formattedTime = timeFormatter.format(Instant.ofEpochSecond(unixTime))
                                    val formattedDate = Instant.ofEpochSecond(unixTime).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
                                    val hasNewDate = formattedDate != previousDate || baseIndex == 0

                                    Weather(
                                        baseBitmap,
                                        current = interpretation,
                                        windBearing = data.current.windDirection.roundToInt(),
                                        windSpeed = data.current.windSpeed.roundToInt(),
                                        windGusts = data.current.windGusts.roundToInt(),
                                        precipitation = data.current.precipitation,
                                        precipitationProbability = null,
                                        temperature = data.current.temperature.roundToInt(),
                                        temperatureUnit = if (userProfile?.preferredUnit?.temperature != UserProfile.PreferredUnit.UnitType.IMPERIAL) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT,
                                        timeLabel = formattedTime,
                                        dateLabel = if (hasNewDate) formattedDate else null,
                                        isImperial = userProfile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
                                    )

                                    previousDate = formattedDate
                                } else {
                                    val interpretation = WeatherInterpretation.fromWeatherCode(data?.forecastData?.weatherCode?.get(baseIndex) ?: 0)
                                    val unixTime = data?.forecastData?.time?.get(baseIndex) ?: 0
                                    val formattedTime = timeFormatter.format(Instant.ofEpochSecond(unixTime))
                                    val formattedDate = Instant.ofEpochSecond(unixTime).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
                                    val hasNewDate = formattedDate != previousDate || baseIndex == 0

                                    Weather(
                                        baseBitmap,
                                        current = interpretation,
                                        windBearing = data?.forecastData?.windDirection?.get(baseIndex)?.roundToInt() ?: 0,
                                        windSpeed = data?.forecastData?.windSpeed?.get(baseIndex)?.roundToInt() ?: 0,
                                        windGusts = data?.forecastData?.windGusts?.get(baseIndex)?.roundToInt() ?: 0,
                                        precipitation = data?.forecastData?.precipitation?.get(baseIndex) ?: 0.0,
                                        precipitationProbability = data?.forecastData?.precipitationProbability?.get(baseIndex) ?: 0,
                                        temperature = data?.forecastData?.temperature?.get(baseIndex)?.roundToInt() ?: 0,
                                        temperatureUnit = if (userProfile?.preferredUnit?.temperature != UserProfile.PreferredUnit.UnitType.IMPERIAL) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT,
                                        timeLabel = formattedTime,
                                        dateLabel = if (hasNewDate) formattedDate else null,
                                        distance = distanceFromCurrent,
                                        isImperial = settingsAndProfile.isImperial
                                    )

                                    previousDate = formattedDate
                                }
                            }
                        }
                    }

                    emitter.updateView(result.remoteViews)
                }
        }
        emitter.setCancellable {
            Log.d(KarooHeadwindExtension.TAG, "Stopping headwind weather forecast view with $emitter")
            configJob.cancel()
            viewJob.cancel()
        }
    }
}