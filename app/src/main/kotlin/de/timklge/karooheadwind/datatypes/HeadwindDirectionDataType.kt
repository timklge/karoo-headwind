package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.WindDirectionIndicatorSetting
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamDatatypeIsVisible
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.HardwareType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class HeadwindDirectionDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-headwind", "headwind") {
    private val glance = GlanceRemoteViews()

    data class StreamData(val headingResponse: HeadingResponse, val absoluteWindDirection: Double?, val windSpeed: Double?, val settings: HeadwindSettings)

    private fun streamValues(): Flow<Double> = flow {
        combine(
            karooSystem.getRelativeHeadingFlow(applicationContext),
            applicationContext.streamCurrentWeatherData(karooSystem),
            applicationContext.streamSettings(karooSystem),
        ) { headingResponse, currentWeather, settings ->
            StreamData(
                headingResponse,
                currentWeather?.windDirection,
                currentWeather?.windSpeed,
                settings,
            )
        }.collect { streamData ->
            val value = (streamData.headingResponse as? HeadingResponse.Value)?.diff

            var returnValue = 0.0
            if (value == null || streamData.absoluteWindDirection == null || streamData.settings == null || streamData.windSpeed == null){
                var errorCode = 1.0
                var headingResponse = streamData.headingResponse

                if (headingResponse is HeadingResponse.Value && (streamData.absoluteWindDirection == null || streamData.windSpeed == null)){
                    headingResponse = HeadingResponse.NoWeatherData
                }

                if (streamData.settings?.welcomeDialogAccepted == false){
                    errorCode = ERROR_APP_NOT_SET_UP.toDouble()
                } else if (headingResponse is HeadingResponse.NoGps){
                    errorCode = ERROR_NO_GPS.toDouble()
                } else {
                    errorCode = ERROR_NO_WEATHER_DATA.toDouble()
                }

                returnValue = errorCode
            } else {
                var windDirection = when (streamData.settings.windDirectionIndicatorSetting){
                    WindDirectionIndicatorSetting.HEADWIND_DIRECTION -> value
                    WindDirectionIndicatorSetting.WIND_DIRECTION -> streamData.absoluteWindDirection + 180
                }

                if (windDirection < 0) windDirection += 360

                returnValue = windDirection
            }

            emit(returnValue)
        }
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            streamValues().collect { returnValue ->
                emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to returnValue))))
            }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    data class DirectionAndSpeed(val bearing: Double, val speed: Double?, val isVisible: Boolean)

    private fun previewFlow(): Flow<DirectionAndSpeed> {
        return flow {
            while (true) {
                val bearing = (0..360).random().toDouble()
                val windSpeed = (0..20).random()

                emit(DirectionAndSpeed(bearing, windSpeed.toDouble(), true))

                delay(2_000)
            }
        }
    }


    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooHeadwindExtension.TAG, "Starting headwind direction view with $emitter")

        val baseBitmap = BitmapFactory.decodeResource(
            context.resources,
            de.timklge.karooheadwind.R.drawable.circle
        )

        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val flow = if (config.preview) {
            previewFlow()
        } else {
            val directionFlow = streamValues()
            val speedFlow = flow {
                emit(0.0)
                emitAll(UserWindSpeedDataType.streamValues(context, karooSystem))
            }

            combine(directionFlow, speedFlow, karooSystem.streamDatatypeIsVisible(dataTypeId)) { direction, speed, isVisible ->
                DirectionAndSpeed(direction, speed, isVisible)
            }
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            val refreshRate = karooSystem.getRefreshRateInMilliseconds(context)

            flow.filter { it.isVisible }.throttle(refreshRate).collect { streamData ->
                Log.d(KarooHeadwindExtension.TAG, "Updating headwind direction view")

                val errorCode = streamData.bearing.let { if(it < 0) it.toInt() else null }
                if (errorCode != null) {
                    emitter.updateView(getErrorWidget(glance, context, errorCode).remoteViews)
                    return@collect
                }

                val windDirection = streamData.bearing
                val windSpeed = streamData.speed

                val result = glance.compose(context, DpSize.Unspecified) {
                    HeadwindDirection(
                        baseBitmap,
                        windDirection.roundToInt(),
                        config.textSize,
                        windSpeed?.toInt()?.toString() ?: "",
                        preview = config.preview,
                        wideMode = false
                    )
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

    companion object {
        const val ERROR_NO_GPS = -1
        const val ERROR_NO_WEATHER_DATA = -2
        const val ERROR_APP_NOT_SET_UP = -3
    }
}

suspend fun KarooSystemService.getRefreshRateInMilliseconds(context: Context): Long {
    val refreshRate = context.streamSettings(this).first().refreshRate
    val isK2 = hardwareType == HardwareType.K2

    return if (isK2){
        refreshRate.k2Ms
    } else {
        refreshRate.k3Ms
    }
}
