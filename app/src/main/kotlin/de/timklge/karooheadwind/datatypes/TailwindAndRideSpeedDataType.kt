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
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.R
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.screens.isNightMode
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamDataFlow
import de.timklge.karooheadwind.streamDatatypeIsVisible
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.throttle
import de.timklge.karooheadwind.util.msInUserUnit
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.roundToInt

fun interpolateColor(color1: Color, color2: Color, lowerBound: Double, upperBound: Double, actualValue: Double): Color {
    val factor = if (upperBound == lowerBound) 0.0 else ((actualValue - lowerBound) / (upperBound - lowerBound)).coerceIn(0.0, 1.0)
    return Color(ColorUtils.blendARGB(color1.toArgb(), color2.toArgb(), factor.toFloat()))
}

fun interpolateWindColor(windSpeedInKmh: Double, night: Boolean, context: Context): Color {
    val default = Color(ContextCompat.getColor(context, if(night) R.color.white else R.color.black))
    val green = Color(ContextCompat.getColor(context, if(night) R.color.green else R.color.hGreen))
    val red = Color(ContextCompat.getColor(context, if(night) R.color.red else R.color.hRed))
    val orange = Color(ContextCompat.getColor(context, if(night) R.color.orange else R.color.hOrange))

    return when {
        windSpeedInKmh <= -10 -> green
        windSpeedInKmh >= 15 -> red
        windSpeedInKmh in -10.0..0.0 -> interpolateColor(green, default, -10.0, 0.0, windSpeedInKmh)
        windSpeedInKmh in 0.0..10.0 -> interpolateColor(default, orange, 0.0, 10.0, windSpeedInKmh)
        else -> interpolateColor(orange, red, 10.0, 15.0, windSpeedInKmh)
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class TailwindAndRideSpeedDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-headwind", "tailwind-and-ride-speed") {
    private val glance = GlanceRemoteViews()

    data class StreamData(
        val headingResponse: HeadingResponse,
        val absoluteWindDirection: Double?,
        val windSpeed: Double?,
        val settings: HeadwindSettings,
        val rideSpeed: Double? = null,
        val gustSpeed: Double? = null,
        val isImperial: Boolean = false,
        val isVisible: Boolean = true
    )

    private fun previewFlow(profileFlow: Flow<UserProfile>): Flow<StreamData> {
        return flow {
            val profile = profileFlow.first()

            while (true) {
                val bearing = (0..360).random().toDouble()
                val windSpeed = (0..10).random()
                val rideSpeed = (5..10).random().toDouble()
                val gustSpeed = windSpeed * ((10..40).random().toDouble() / 10)
                val isImperial = profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL

                emit(StreamData(HeadingResponse.Value(bearing), bearing, windSpeed.toDouble(), HeadwindSettings(), rideSpeed, gustSpeed = gustSpeed, isImperial = isImperial, isVisible = true))

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
            previewFlow(karooSystem.streamUserProfile())
        } else {
            combine(karooSystem.getRelativeHeadingFlow(context),
                context.streamCurrentWeatherData(karooSystem),
                context.streamSettings(karooSystem),
                karooSystem.streamUserProfile(),
                streamSpeedInMs(),
                karooSystem.streamDatatypeIsVisible(dataTypeId)
            ) { data ->
                val headingResponse = data[0] as HeadingResponse
                val weatherData = data[1] as? WeatherData
                val settings = data[2] as HeadwindSettings
                val userProfile = data[3] as UserProfile
                val rideSpeedInMs = data[4] as Double
                val isVisible = data[5] as Boolean

                val isImperial = userProfile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
                val absoluteWindDirection = weatherData?.windDirection
                val windSpeed = weatherData?.windSpeed
                val gustSpeed = weatherData?.windGusts
                val rideSpeed = rideSpeedInMs

                StreamData(headingResponse, absoluteWindDirection, windSpeed, settings, rideSpeed = rideSpeed, isImperial = isImperial, gustSpeed = gustSpeed, isVisible = isVisible)
            }
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(ShowCustomStreamState("", null))

            val refreshRate = karooSystem.getRefreshRateInMilliseconds(context)
            flow.throttle(refreshRate).collect { streamData ->
                Log.d(KarooHeadwindExtension.TAG, "Updating headwind direction view")

                val value = (streamData.headingResponse as? HeadingResponse.Value)?.diff
                if (value == null || streamData.absoluteWindDirection == null || streamData.windSpeed == null){
                    var headingResponse = streamData.headingResponse

                    if (headingResponse is HeadingResponse.Value && (streamData.absoluteWindDirection == null || streamData.windSpeed == null)){
                        headingResponse = HeadingResponse.NoWeatherData
                    }

                    emitter.updateView(getErrorWidget(glance, context, streamData.settings, headingResponse).remoteViews)

                    return@collect
                }

                val windSpeed = streamData.windSpeed
                val windDirection = streamData.headingResponse.diff

                val rideSpeedInUserUnit = msInUserUnit(streamData.rideSpeed ?: 0.0, streamData.isImperial)
                val text = String.format(Locale.current.platformLocale, "%.1f", rideSpeedInUserUnit)

                val wideMode = config.gridSize.first == 60

                val gustSpeedInUserUnit = msInUserUnit(streamData.gustSpeed ?: 0.0, streamData.isImperial)

                val gustSpeedAddon = if (wideMode) {
                    "-${gustSpeedInUserUnit.roundToInt()}"
                } else {
                    ""
                }

                val windSpeedUserUnit = msInUserUnit(windSpeed, streamData.isImperial)

                val subtextWithSign = let {
                    val headwindSpeed = cos( (windDirection + 180) * Math.PI / 180.0) * windSpeed
                    headwindSpeed.roundToInt().toString()

                    val sign = if (headwindSpeed < 0) "+" else {
                        if (headwindSpeed > 0) "-" else ""
                    }

                    val headwindSpeedUserUnit = msInUserUnit(headwindSpeed, streamData.isImperial)

                    "$sign${headwindSpeedUserUnit.roundToInt().absoluteValue} ${windSpeedUserUnit.roundToInt()}${gustSpeedAddon}"
                }

                val headwindSpeed = cos( (windDirection + 180) * Math.PI / 180.0) * windSpeed
                val windSpeedInKmh = headwindSpeed * 3.6
                val dayColor = interpolateWindColor(windSpeedInKmh, false, context)
                val nightColor = interpolateWindColor(windSpeedInKmh, true, context)

                val result = glance.compose(context, DpSize.Unspecified) {
                    HeadwindDirection(
                        baseBitmap,
                        windDirection.roundToInt(),
                        config.textSize,
                        text,
                        subtextWithSign,
                        dayColor,
                        nightColor,
                        preview = config.preview,
                        wideMode = wideMode,
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
}