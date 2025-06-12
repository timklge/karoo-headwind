package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.core.graphics.createBitmap
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.HeadwindWidgetSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.UpcomingRoute
import de.timklge.karooheadwind.WeatherDataProvider
import de.timklge.karooheadwind.getHeadingFlow
import de.timklge.karooheadwind.screens.LineGraphBuilder
import de.timklge.karooheadwind.streamCurrentForecastWeatherData
import de.timklge.karooheadwind.streamDatatypeIsVisible
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUpcomingRoute
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.streamWidgetSettings
import de.timklge.karooheadwind.throttle
import de.timklge.karooheadwind.util.getTimeFormatter
import de.timklge.karooheadwind.weatherprovider.WeatherData
import de.timklge.karooheadwind.weatherprovider.WeatherDataForLocation
import de.timklge.karooheadwind.weatherprovider.WeatherDataResponse
import de.timklge.karooheadwind.weatherprovider.WeatherInterpretation
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

abstract class LineGraphForecastDataType(private val karooSystem: KarooSystemService, typeId: String) : DataTypeImpl("karoo-headwind", typeId) {
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private val glance = GlanceRemoteViews()

    data class StreamData(val data: WeatherDataResponse?, val settings: SettingsAndProfile,
                          val widgetSettings: HeadwindWidgetSettings? = null,
                          val headingResponse: HeadingResponse? = null, val upcomingRoute: UpcomingRoute? = null, val isVisible: Boolean)

    data class SettingsAndProfile(val settings: HeadwindSettings, val isImperial: Boolean, val isImperialTemperature: Boolean)

    data class LineData(val time: Instant? = null, val distance: Float? = null, val weatherData: WeatherData)

    abstract fun getLineData(
        lineData: List<LineData>,
        isImperial: Boolean,
        upcomingRoute: UpcomingRoute?,
        isPreview: Boolean,
        context: Context
    ): Set<LineGraphBuilder.Line>

    private fun previewFlow(settingsAndProfileStream: Flow<SettingsAndProfile>): Flow<StreamData> =
        flow {
            val settingsAndProfile = settingsAndProfileStream.firstOrNull()

            while (true) {
                val data = (0..<12).map { index ->
                    val timeAtFullHour = Instant.now().truncatedTo(ChronoUnit.HOURS).epochSecond

                    val weatherData = (0..<12).map {
                        val forecastTime = timeAtFullHour + it * 60 * 60
                        val forecastTemperature = 20.0 + (-20..20).random()
                        val forecastPrecipitation = 0.0 + (0..10).random()
                        val forecastPrecipitationProbability = (0..100).random()
                        val forecastWeatherCode = WeatherInterpretation.getKnownWeatherCodes().random()
                        val forecastWindSpeed = 0.0 + (0..10).random()
                        val forecastWindDirection = 0.0 + (0..360).random()
                        val forecastWindGusts = 0.0 + (0..10).random()
                        WeatherData(
                            time = forecastTime,
                            temperature = forecastTemperature,
                            relativeHumidity = 20,
                            precipitation = forecastPrecipitation,
                            cloudCover = 3.0,
                            sealevelPressure = 1013.25,
                            surfacePressure = 1013.25,
                            precipitationProbability = forecastPrecipitationProbability.toDouble(),
                            windSpeed = forecastWindSpeed,
                            windDirection = forecastWindDirection,
                            windGusts = forecastWindGusts,
                            weatherCode = forecastWeatherCode,
                            isForecast = true,
                            isNight = it < 2
                        )
                    }

                    val distancePerHour =
                        settingsAndProfile?.settings?.getForecastMetersPerHour(settingsAndProfile.isImperial)
                            ?.toDouble() ?: 0.0

                    WeatherDataForLocation(
                        current = WeatherData(
                            time = timeAtFullHour,
                            temperature = 20.0,
                            relativeHumidity = 20,
                            precipitation = 0.0,
                            cloudCover = 3.0,
                            sealevelPressure = 1013.25,
                            surfacePressure = 1013.25,
                            windSpeed = 5.0,
                            windDirection = 180.0,
                            windGusts = 10.0,
                            weatherCode = WeatherInterpretation.getKnownWeatherCodes().random(),
                            isForecast = false,
                            isNight = false
                        ),
                        coords = GpsCoordinates(0.0, 0.0, distanceAlongRoute = index * distancePerHour),
                        timezone = "UTC",
                        elevation = null,
                        forecasts = weatherData
                    )
                }


                emit(
                    StreamData(
                        WeatherDataResponse(provider = WeatherDataProvider.OPEN_METEO, data = data),
                        SettingsAndProfile(
                            HeadwindSettings(),
                            settingsAndProfile?.isImperial == true,
                            settingsAndProfile?.isImperialTemperature == true
                        ),
                        isVisible = true
                    )
                )

                delay(5_000)
            }
        }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooHeadwindExtension.TAG, "Starting weather forecast view with $emitter")
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val settingsAndProfileStream = context.streamSettings(karooSystem).combine(karooSystem.streamUserProfile()) { settings, userProfile ->
            SettingsAndProfile(settings = settings, isImperial = userProfile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL,
                isImperialTemperature = userProfile.preferredUnit.temperature == UserProfile.PreferredUnit.UnitType.IMPERIAL)
        }

        val dataFlow = if (config.preview){
            previewFlow(settingsAndProfileStream)
        } else {
            combine(
                context.streamCurrentForecastWeatherData(),
                settingsAndProfileStream,
                context.streamWidgetSettings(),
                karooSystem.getHeadingFlow(context).throttle(3 * 60_000L),
                karooSystem.streamUpcomingRoute().distinctUntilChanged { old, new ->
                    val oldDistance = old?.distanceAlongRoute
                    val newDistance = new?.distanceAlongRoute

                    if (oldDistance == null && newDistance == null) return@distinctUntilChanged true
                    if (oldDistance == null || newDistance == null) return@distinctUntilChanged false

                    abs(oldDistance - newDistance) < 1_000
                },
                karooSystem.streamDatatypeIsVisible(dataTypeId)
            ) { data ->
                val weatherData = data[0] as WeatherDataResponse?
                val settings = data[1] as SettingsAndProfile
                val widgetSettings = data[2] as HeadwindWidgetSettings?
                val heading = data[3] as HeadingResponse?
                val upcomingRoute = data[4] as UpcomingRoute?
                val isVisible = data[5] as Boolean

                StreamData(
                    data = weatherData,
                    settings = settings,
                    widgetSettings = widgetSettings,
                    headingResponse = heading,
                    upcomingRoute = upcomingRoute,
                    isVisible = isVisible
                )
            }
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(ShowCustomStreamState("", null))

            dataFlow.filter { it.isVisible }.collect { (allData, settingsAndProfile, _, headingResponse, upcomingRoute) ->
                Log.d(KarooHeadwindExtension.TAG, "Updating weather forecast view")

                if (allData?.data.isNullOrEmpty()){
                    emitter.updateView(
                        getErrorWidget(
                            glance,
                            context,
                            settingsAndProfile.settings,
                            headingResponse
                        ).remoteViews)

                    return@collect
                }

                val result = glance.compose(context, DpSize.Unspecified) {
                    val data = buildList {
                        for(i in 0..<12){
                            val isRouteLoaded = if (config.preview){
                                true
                            } else {
                                upcomingRoute != null
                            }

                            val locationData = if (isRouteLoaded){
                                allData?.data?.getOrNull(i)
                            } else {
                                allData?.data?.firstOrNull()
                            }
                            val data = if (i == 0){
                                locationData?.current
                            } else {
                                locationData?.forecasts?.getOrNull(i)
                            }

                            if (data == null) {
                                Log.w(KarooHeadwindExtension.TAG, "No weather data available for forecast index $i")
                                continue
                            }

                            val time = Instant.ofEpochSecond(data.time)

                            if (time.isBefore(Instant.now().minus(1, ChronoUnit.HOURS)) || (locationData?.coords?.distanceAlongRoute == null && time.isAfter(Instant.now().plus(6, ChronoUnit.HOURS)))) {
                                Log.d(KarooHeadwindExtension.TAG, "Skipping forecast data for time $time as it is in the past or too close to now")
                                continue
                            }

                            add(LineData(
                                time = time,
                                distance = locationData?.coords?.distanceAlongRoute?.toFloat(),
                                weatherData = data,
                            ))
                        }
                    }

                    val pointData = getLineData(
                        data,
                        settingsAndProfile.isImperialTemperature,
                        upcomingRoute,
                        config.preview,
                        context
                    )

                    val bitmap = LineGraphBuilder(context).drawLineGraph(config.viewSize.first, config.viewSize.second, config.gridSize.first, config.gridSize.second, pointData) { x ->
                        val startTime = data.firstOrNull()?.time
                        val time = startTime?.plus(floor(x).toLong(), ChronoUnit.HOURS)
                        val timeLabel = getTimeFormatter(context).format(time?.atZone(ZoneId.systemDefault())?.toLocalTime())
                        val beforeData = data.getOrNull(floor(x).toInt().coerceAtLeast(0))
                        val afterData = data.getOrNull(ceil(x).toInt().coerceAtMost(data.size - 1))

                        if (beforeData?.distance != null || afterData?.distance != null) {
                            val start = beforeData?.distance ?: 0.0f
                            val end = (afterData?.distance ?: upcomingRoute?.routeLength?.toFloat()) ?: 0.0f
                            val distance = start + (end - start) * (x - floor(x))
                            val distanceLabel = if (settingsAndProfile.isImperial) {
                                "${(distance * 0.000621371).toInt()}"
                            } else {
                                "${(distance / 1000).toInt()}"
                            }
                            return@drawLineGraph distanceLabel
                        } else {
                            timeLabel
                        }
                    }

                    Box(modifier = GlanceModifier.fillMaxSize()){
                        Image(ImageProvider(bitmap), "Forecast", modifier = GlanceModifier.fillMaxSize())
                    }
                }

                emitter.updateView(result.remoteViews)
            }
        }
        emitter.setCancellable {
            Log.d(
                KarooHeadwindExtension.TAG,
                "Stopping headwind weather forecast view with $emitter"
            )
            configJob.cancel()
            viewJob.cancel()
        }
    }
}