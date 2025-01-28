package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.DpSize
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.R
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.screens.HeadwindSettings
import de.timklge.karooheadwind.screens.WindDirectionIndicatorSetting
import de.timklge.karooheadwind.screens.WindDirectionIndicatorTextSetting
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamDataFlow
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUserProfile
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.roundToInt

fun interpolateColor(color1: Color, color2: Color, factor: Float): Color {
    return Color(ColorUtils.blendARGB(color1.toArgb(), color2.toArgb(), factor))
}

fun interpolateWindColor(windSpeedInKmh: Double, night: Boolean, context: Context): Color {
    val default = Color(ContextCompat.getColor(context, if(night) R.color.white else R.color.black))
    val green = Color(ContextCompat.getColor(context, if(night) R.color.green else R.color.hGreen))
    val red = Color(ContextCompat.getColor(context, if(night) R.color.red else R.color.hRed))
    val orange = Color(ContextCompat.getColor(context, if(night) R.color.orange else R.color.hOrange))

    return when {
        windSpeedInKmh <= -15 -> green
        windSpeedInKmh >= 30 -> red
        windSpeedInKmh in -15.0..0.0 -> interpolateColor(green, default, (windSpeedInKmh + 15).toFloat() / 15)
        windSpeedInKmh in 0.0..15.0 -> interpolateColor(default, orange, windSpeedInKmh.toFloat() / 15)
        else -> interpolateColor(orange, red, (windSpeedInKmh - 15).toFloat() / 15)
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class TailwindAndRideSpeedDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-headwind", "tailwind-and-ride-speed") {
    private val glance = GlanceRemoteViews()

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.getRelativeHeadingFlow(applicationContext)
                .collect { diff ->
                    val value = (diff as? HeadingResponse.Value)?.diff ?: 0.0
                    emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to value))))
                }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    data class StreamData(val headingResponse: HeadingResponse,
                          val absoluteWindDirection: Double?,
                          val windSpeed: Double?,
                          val settings: HeadwindSettings?,
                          val rideSpeed: Double? = null,
                          val isImperial: Boolean? = null)

    private fun previewFlow(): Flow<StreamData> {
        return flow {
            while (true) {
                val bearing = (0..360).random().toDouble()
                val windSpeed = (0..20).random()
                val rideSpeed = (10..40).random().toDouble()

                emit(StreamData(HeadingResponse.Value(bearing), bearing, windSpeed.toDouble(), HeadwindSettings(), rideSpeed))

                delay(2_000)
            }
        }
    }

    private fun streamSpeedInMs(): Flow<Double> {
        return karooSystem.streamDataFlow(DataType.Type.SMOOTHED_3S_AVERAGE_SPEED)
            .map { (it as? StreamState.Streaming)?.dataPoint?.singleValue ?: 0.0 }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooHeadwindExtension.TAG, "Starting headwind direction view with $emitter")

        val baseBitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.arrow_0
        )

        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))
            awaitCancellation()
        }

        val flow = if (config.preview) {
            previewFlow()
        } else {
            karooSystem.getRelativeHeadingFlow(context)
                .combine(context.streamCurrentWeatherData()) { value, data -> value to data }
                .combine(context.streamSettings(karooSystem)) { (value, data), settings ->
                    StreamData(value, data?.current?.windDirection, data?.current?.windSpeed, settings)
                }
                .combine(karooSystem.streamUserProfile()) { streamData, userProfile ->
                    val isImperial = userProfile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
                    streamData.copy(isImperial = isImperial)
                }
                .combine(streamSpeedInMs()) { streamData, rideSpeedInMs ->
                    val rideSpeed = if (streamData.isImperial == true){
                        rideSpeedInMs * 2.23694
                    } else {
                        rideSpeedInMs * 3.6
                    }
                    streamData.copy(rideSpeed = rideSpeed)
                }
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            flow.collect { streamData ->
                Log.d(KarooHeadwindExtension.TAG, "Updating headwind direction view")

                val value = (streamData.headingResponse as? HeadingResponse.Value)?.diff
                if (value == null || streamData.absoluteWindDirection == null || streamData.settings == null || streamData.windSpeed == null){
                    var headingResponse = streamData.headingResponse

                    if (headingResponse is HeadingResponse.Value && (streamData.absoluteWindDirection == null || streamData.windSpeed == null)){
                        headingResponse = HeadingResponse.NoWeatherData
                    }

                    emitter.updateView(getErrorWidget(glance, context, streamData.settings, headingResponse).remoteViews)

                    return@collect
                }

                val windSpeed = streamData.windSpeed
                val windDirection = when (streamData.settings.windDirectionIndicatorSetting){
                    WindDirectionIndicatorSetting.HEADWIND_DIRECTION -> streamData.headingResponse.diff
                    WindDirectionIndicatorSetting.WIND_DIRECTION -> streamData.absoluteWindDirection + 180
                }

                val text = streamData.rideSpeed?.let { String.format(Locale.current.platformLocale, "%.1f", it) } ?: ""

                val subtextWithSign = when (streamData.settings.windDirectionIndicatorTextSetting) {
                    WindDirectionIndicatorTextSetting.HEADWIND_SPEED -> {
                        val headwindSpeed = cos( (windDirection + 180) * Math.PI / 180.0) * windSpeed
                        headwindSpeed.roundToInt().toString()

                        val sign = if (headwindSpeed < 0) "+" else {
                            if (headwindSpeed > 0) "-" else ""
                        }
                        "$sign${headwindSpeed.roundToInt().absoluteValue} ${windSpeed.roundToInt()}"
                    }
                    WindDirectionIndicatorTextSetting.WIND_SPEED -> windSpeed.roundToInt().toString()
                    WindDirectionIndicatorTextSetting.NONE -> ""
                }

                var dayColor = Color(ContextCompat.getColor(context, R.color.black))
                var nightColor = Color(ContextCompat.getColor(context, R.color.white))

                if (streamData.settings.windDirectionIndicatorSetting == WindDirectionIndicatorSetting.HEADWIND_DIRECTION) {
                    val headwindSpeed = cos( (windDirection + 180) * Math.PI / 180.0) * windSpeed
                    val windSpeedInKmh = if (streamData.isImperial == true){
                        headwindSpeed / 2.23694 * 3.6
                    } else {
                        headwindSpeed
                    }
                    dayColor = interpolateWindColor(windSpeedInKmh, false, context)
                    nightColor = interpolateWindColor(windSpeedInKmh, true, context)
                }

                val result = glance.compose(context, DpSize.Unspecified) {
                    HeadwindDirection(baseBitmap, windDirection.roundToInt(), config.textSize, text, subtextWithSign,
                        dayColor, nightColor, viewSize = config.viewSize)
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