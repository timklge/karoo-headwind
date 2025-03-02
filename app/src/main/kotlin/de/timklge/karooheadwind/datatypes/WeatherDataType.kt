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
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse
import de.timklge.karooheadwind.OpenMeteoData
import de.timklge.karooheadwind.TemperatureUnit
import de.timklge.karooheadwind.WeatherInterpretation
import de.timklge.karooheadwind.getHeadingFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUserProfile
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
            val currentWeatherData = applicationContext.streamCurrentWeatherData()

            currentWeatherData
                .collect { data ->
                    Log.d(KarooHeadwindExtension.TAG, "Wind code: ${data.firstOrNull()?.data?.current?.weatherCode}")
                    emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to (data.firstOrNull()?.data?.current?.weatherCode?.toDouble() ?: 0.0)))))
                }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    data class StreamData(val data: OpenMeteoCurrentWeatherResponse?, val settings: HeadwindSettings,
                          val profile: UserProfile? = null, val headingResponse: HeadingResponse? = null)

    private fun previewFlow(): Flow<StreamData> = flow {
        while (true){
            emit(StreamData(
                OpenMeteoCurrentWeatherResponse(
                    OpenMeteoData(Instant.now().epochSecond, 0, 20.0, 50, 3.0, 0, 1013.25, 980.0, 15.0, 30.0, 30.0, WeatherInterpretation.getKnownWeatherCodes().random()),
                    0.0, 0.0, "Europe/Berlin", 30.0, 0,

                    null
            ), HeadwindSettings()
            ))

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
            de.timklge.karooheadwind.R.drawable.arrow_0
        )

        val dataFlow = if (config.preview){
            previewFlow()
        } else {
            combine(context.streamCurrentWeatherData(), context.streamSettings(karooSystem), karooSystem.streamUserProfile(), karooSystem.getHeadingFlow(context)) { data, settings, profile, heading ->
                StreamData(data.firstOrNull()?.data, settings, profile, heading)
            }
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(ShowCustomStreamState("", null))

            dataFlow.collect { (data, settings, userProfile, headingResponse) ->
                    Log.d(KarooHeadwindExtension.TAG, "Updating weather view")

                    if (data == null){
                        emitter.updateView(getErrorWidget(glance, context, settings, headingResponse).remoteViews)

                        return@collect
                    }

                    val interpretation = WeatherInterpretation.fromWeatherCode(data.current.weatherCode)
                    val formattedTime = timeFormatter.format(Instant.ofEpochSecond(data.current.time))
                    val formattedDate = getShortDateFormatter().format(Instant.ofEpochSecond(data.current.time))

                    val result = glance.compose(context, DpSize.Unspecified) {
                        var modifier = GlanceModifier.fillMaxSize()
                        if (!config.preview) modifier = modifier.clickable(onClick = actionStartActivity<MainActivity>())

                        Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
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
                                rowAlignment = when (config.alignment){
                                    ViewConfig.Alignment.LEFT -> Alignment.Horizontal.Start
                                    ViewConfig.Alignment.CENTER -> Alignment.Horizontal.CenterHorizontally
                                    ViewConfig.Alignment.RIGHT -> Alignment.Horizontal.End
                                },
                                dateLabel = formattedDate,
                                singleDisplay = true,
                                isImperial = userProfile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
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