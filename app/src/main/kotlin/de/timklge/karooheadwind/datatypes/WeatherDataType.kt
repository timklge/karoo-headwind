package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.MainActivity
import de.timklge.karooheadwind.R
import de.timklge.karooheadwind.TemperatureUnit
import de.timklge.karooheadwind.getHeadingFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamDatatypeIsVisible
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.throttle
import de.timklge.karooheadwind.util.celciusInUserUnit
import de.timklge.karooheadwind.util.millimetersInUserUnit
import de.timklge.karooheadwind.util.msInUserUnit
import de.timklge.karooheadwind.weatherprovider.WeatherData
import de.timklge.karooheadwind.weatherprovider.WeatherInterpretation
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WeatherDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-headwind", "weather") {
    private val glance = GlanceRemoteViews()

    companion object {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            val currentWeatherData = applicationContext.streamCurrentWeatherData(karooSystem)

            currentWeatherData.collect { data ->
                Log.d(KarooHeadwindExtension.TAG, "Wind code: ${data?.weatherCode}")
                emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to (data?.weatherCode?.toDouble() ?: 0.0)))))
            }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    data class StreamData(val data: WeatherData?, val settings: HeadwindSettings,
                          val profile: UserProfile? = null, val headingResponse: HeadingResponse? = null,
                          val isVisible: Boolean)

    private fun previewFlow(): Flow<StreamData> = flow {
        while (true){
            emit(StreamData(
                WeatherData(
                    Instant.now().epochSecond, 0.0,
                    20.0, 50.0, 3.0, 0.0, 1013.25, 980.0, 15.0, 30.0, 30.0,
                    WeatherInterpretation.getKnownWeatherCodes().random(), isForecast = false,
                    isNight = listOf(true, false).random()
                ), HeadwindSettings(), isVisible = true))

            delay(5_000)
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooHeadwindExtension.TAG, "Starting weather view with $emitter")
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val baseBitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.arrow_0
        )

        val dataFlow = if (config.preview){
            previewFlow()
        } else {
            combine(
                context.streamCurrentWeatherData(karooSystem),
                context.streamSettings(karooSystem),
                karooSystem.streamUserProfile(),
                karooSystem.getHeadingFlow(context),
                karooSystem.streamDatatypeIsVisible(dataTypeId)
            ) { data, settings, profile, heading, isVisible ->
                StreamData(data, settings, profile, heading, isVisible)
            }
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(ShowCustomStreamState("", null))

            val refreshRate = karooSystem.getRefreshRateInMilliseconds(context)

            dataFlow.filter { it.isVisible }.throttle(refreshRate).collect { (data, settings, userProfile, headingResponse) ->
                    Log.d(KarooHeadwindExtension.TAG, "Updating weather view")

                    if (data == null){
                        emitter.updateView(getErrorWidget(glance, context, settings, headingResponse).remoteViews)

                        return@collect
                    }

                    val interpretation = WeatherInterpretation.fromWeatherCode(data.weatherCode)
                    val formattedTime = timeFormatter.format(Instant.ofEpochSecond(data.time))
                    val formattedDate = getShortDateFormatter().format(Instant.ofEpochSecond(data.time))

                    val result = glance.compose(context, DpSize.Unspecified) {
                        var modifier = GlanceModifier.fillMaxSize()
                        // TODO reenable once swipes are no longer interpreted as clicks if (!config.preview) modifier = modifier.clickable(onClick = actionStartActivity<MainActivity>())

                        Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
                            Weather(
                                baseBitmap,
                                current = interpretation,
                                windBearing = data.windDirection.roundToInt(),
                                windSpeed = msInUserUnit(data.windSpeed, userProfile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL).roundToInt(),
                                windGusts = msInUserUnit(data.windGusts, userProfile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL).roundToInt(),
                                precipitation = millimetersInUserUnit(data.precipitation, userProfile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL),
                                precipitationProbability = null,
                                temperature = celciusInUserUnit(data.temperature, userProfile?.preferredUnit?.temperature == UserProfile.PreferredUnit.UnitType.IMPERIAL).roundToInt(),
                                temperatureUnit = if (userProfile?.preferredUnit?.temperature != UserProfile.PreferredUnit.UnitType.IMPERIAL) TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT,
                                timeLabel = formattedTime,
                                rowAlignment = when (config.alignment){
                                    ViewConfig.Alignment.LEFT -> Alignment.Horizontal.Start
                                    ViewConfig.Alignment.CENTER -> Alignment.Horizontal.CenterHorizontally
                                    ViewConfig.Alignment.RIGHT -> Alignment.Horizontal.End
                                },
                                dateLabel = formattedDate,
                                singleDisplay = true,
                                isImperial = userProfile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL,
                                isNight = data.isNight,
                            )
                        }
                    }

                    emitter.updateView(result.remoteViews)
                }
        }
        emitter.setCancellable {
            Log.d(KarooHeadwindExtension.TAG, "Stopping headwind view with $emitter")
            configJob.cancel()
            viewJob.cancel()
        }
    }
}