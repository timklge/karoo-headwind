package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
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
import de.timklge.karooheadwind.R
import de.timklge.karooheadwind.TemperatureUnit
import de.timklge.karooheadwind.UpcomingRoute
import de.timklge.karooheadwind.WeatherDataProvider
import de.timklge.karooheadwind.getHeadingFlow
import de.timklge.karooheadwind.streamCurrentForecastWeatherData
import de.timklge.karooheadwind.streamDatatypeIsVisible
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUpcomingRoute
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.streamWidgetSettings
import de.timklge.karooheadwind.throttle
import de.timklge.karooheadwind.util.celciusInUserUnit
import de.timklge.karooheadwind.util.getTimeFormatter
import de.timklge.karooheadwind.util.millimetersInUserUnit
import de.timklge.karooheadwind.util.msInUserUnit
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
import kotlin.math.roundToInt

abstract class ForecastDataType(private val karooSystem: KarooSystemService, typeId: String) : DataTypeImpl("karoo-headwind", typeId) {
    @Composable
    abstract fun RenderWidget(
        arrowBitmap: Bitmap,
        current: WeatherInterpretation,
        windBearing: Int,
        windSpeed: Int,
        windGusts: Int,
        precipitation: Double,
        precipitationProbability: Int?,
        temperature: Int,
        temperatureUnit: TemperatureUnit,
        timeLabel: String,
        dateLabel: String?,
        distance: Double?,
        isImperial: Boolean,
        isNight: Boolean,
        uvi: Double
    )

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private val glance = GlanceRemoteViews()

    data class StreamData(val data: WeatherDataResponse?, val settings: SettingsAndProfile,
                          val widgetSettings: HeadwindWidgetSettings? = null,
                          val headingResponse: HeadingResponse? = null, val upcomingRoute: UpcomingRoute? = null, val isVisible: Boolean)

    data class SettingsAndProfile(val settings: HeadwindSettings, val isImperial: Boolean, val isImperialTemperature: Boolean)

    private fun previewFlow(settingsAndProfileStream: Flow<SettingsAndProfile>): Flow<StreamData> =
        flow {
            val settingsAndProfile = settingsAndProfileStream.firstOrNull()

            while (true) {
                val data = (0..<10).map { index ->
                    val timeAtFullHour = Instant.now().truncatedTo(ChronoUnit.HOURS).epochSecond

                    val weatherData = (0..<12).map {
                        val forecastTime = timeAtFullHour + it * 60 * 60
                        val forecastTemperature = 20.0 + (-20..20).random()
                        val forecastUvi = 0.0 + (0..12).random().toDouble()
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
                            isNight = it < 2,
                            uvi = forecastUvi
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
                            isNight = false,
                            uvi = 2.0
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

        val baseBitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.arrow_0
        )

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

            dataFlow.filter { it.isVisible }.collect { (allData, settingsAndProfile, widgetSettings, headingResponse, upcomingRoute) ->
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
                    var modifier = GlanceModifier.fillMaxSize()

                    if (!config.preview) modifier = modifier.clickable(onClick = actionRunCallback<CycleHoursAction>())

                    Row(modifier = modifier, horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                        val hourOffset = widgetSettings?.currentForecastHourOffset ?: 0
                        val positionOffset = if (allData?.data?.size == 1) 0 else hourOffset

                        var previousDate: String? = let {
                            val unixTime = allData?.data?.getOrNull(positionOffset)?.forecasts?.getOrNull(hourOffset)?.time
                            val formattedDate = unixTime?.let {
                                getShortDateFormatter().format(Instant.ofEpochSecond(unixTime))
                            }

                            formattedDate
                        }

                        for (baseIndex in hourOffset..hourOffset + 2) {
                            // Only show first value if placed in a 1x1 grid cell
                            if (baseIndex > 0 && config.gridSize.first == 30) break

                            val positionIndex = if (allData?.data?.size == 1) 0 else baseIndex

                            if (allData?.data?.getOrNull(positionIndex) == null) break
                            if (baseIndex >= (allData.data.getOrNull(positionOffset)?.forecasts?.size ?: 0)) break

                            val data = allData.data.getOrNull(positionIndex)
                            val distanceAlongRoute = allData.data.getOrNull(positionIndex)?.coords?.distanceAlongRoute
                            val position = allData.data.getOrNull(positionIndex)?.coords?.let {
                                "${(it.distanceAlongRoute?.div(1000.0))?.toInt()} at ${it.lat}, ${it.lon}"
                            }

                            if (baseIndex > hourOffset) {
                                Spacer(
                                    modifier = GlanceModifier.fillMaxHeight().background(
                                        ColorProvider(Color.Black, Color.White)
                                    ).width(1.dp)
                                )
                            }

                            Log.d(
                                KarooHeadwindExtension.TAG,
                                "Distance along route ${positionIndex}: $position"
                            )

                            val distanceFromCurrent =
                                upcomingRoute?.distanceAlongRoute?.let { currentDistanceAlongRoute ->
                                    distanceAlongRoute?.minus(currentDistanceAlongRoute)
                                }

                            val isCurrent = baseIndex == 0 && positionIndex == 0

                            val time = if (isCurrent && data?.current != null) {
                                Instant.ofEpochSecond(data.current.time)
                            } else {
                                Instant.ofEpochSecond(data?.forecasts?.getOrNull(baseIndex)?.time ?: 0)
                            }

                            if (time.isBefore(Instant.now().minus(1, ChronoUnit.HOURS)) || (upcomingRoute == null && time.isAfter(Instant.now().plus(6, ChronoUnit.HOURS)))) {
                                Log.d(KarooHeadwindExtension.TAG, "Skipping forecast data for time $time as it is in the past or too close to now")
                                continue
                            }

                            if (isCurrent && data?.current != null) {
                                val interpretation = WeatherInterpretation.fromWeatherCode(data.current.weatherCode)
                                val unixTime = data.current.time
                                val formattedTime = getTimeFormatter(context).format(Instant.ofEpochSecond(unixTime).atZone(ZoneId.systemDefault()).toLocalTime())
                                val formattedDate = getShortDateFormatter().format(Instant.ofEpochSecond(unixTime).atZone(ZoneId.systemDefault()))
                                val hasNewDate = formattedDate != previousDate || baseIndex == 0

                                RenderWidget(
                                    arrowBitmap = baseBitmap,
                                    current = interpretation,
                                    windBearing = data.current.windDirection.roundToInt(),
                                    windSpeed = msInUserUnit(data.current.windSpeed, settingsAndProfile.isImperial).roundToInt(),
                                    windGusts = msInUserUnit(data.current.windGusts, settingsAndProfile.isImperial).roundToInt(),
                                    precipitation = millimetersInUserUnit(data.current.precipitation, settingsAndProfile.isImperial),
                                    precipitationProbability = null,
                                    temperature = celciusInUserUnit(data.current.temperature, settingsAndProfile.isImperialTemperature).roundToInt(),
                                    temperatureUnit = if (settingsAndProfile.isImperialTemperature) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS,
                                    timeLabel = formattedTime,
                                    dateLabel = if (hasNewDate) formattedDate else null,
                                    distance = null,
                                    isImperial = settingsAndProfile.isImperial,
                                    isNight = data.current.isNight,
                                    uvi = data.current.uvi
                                )

                                previousDate = formattedDate
                            } else {
                                val weatherData = data?.forecasts?.getOrNull(baseIndex)
                                val interpretation = WeatherInterpretation.fromWeatherCode(weatherData?.weatherCode ?: 0)
                                val unixTime = data?.forecasts?.getOrNull(baseIndex)?.time ?: 0
                                val formattedTime = getTimeFormatter(context).format(Instant.ofEpochSecond(unixTime).atZone(ZoneId.systemDefault()).toLocalTime())
                                val formattedDate = getShortDateFormatter().format(Instant.ofEpochSecond(unixTime))
                                val hasNewDate = formattedDate != previousDate || baseIndex == 0

                                RenderWidget(
                                    arrowBitmap = baseBitmap,
                                    current = interpretation,
                                    windBearing = weatherData?.windDirection?.roundToInt() ?: 0,
                                    windSpeed = msInUserUnit(weatherData?.windSpeed ?: 0.0, settingsAndProfile.isImperial).roundToInt(),
                                    windGusts = msInUserUnit(weatherData?.windGusts ?: 0.0, settingsAndProfile.isImperial).roundToInt(),
                                    precipitation = millimetersInUserUnit(weatherData?.precipitation ?: 0.0, settingsAndProfile.isImperial),
                                    precipitationProbability = weatherData?.precipitationProbability?.toInt(),
                                    temperature = celciusInUserUnit(weatherData?.temperature ?: 0.0, settingsAndProfile.isImperialTemperature).roundToInt(),
                                    temperatureUnit = if (settingsAndProfile.isImperialTemperature) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS,
                                    timeLabel = formattedTime,
                                    dateLabel = if (hasNewDate) formattedDate else null,
                                    distance = if (settingsAndProfile.settings.showDistanceInForecast) distanceFromCurrent else null,
                                    isImperial = settingsAndProfile.isImperial,
                                    isNight = weatherData?.isNight == true,
                                    uvi = weatherData?.uvi ?: 0.0
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
            Log.d(
                KarooHeadwindExtension.TAG,
                "Stopping headwind weather forecast view with $emitter"
            )
            configJob.cancel()
            viewJob.cancel()
        }
    }
}