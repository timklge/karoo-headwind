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
import de.timklge.karooheadwind.R
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamDatatypeIsVisible
import de.timklge.karooheadwind.streamSettings
import de.timklge.karooheadwind.streamUserProfile
import de.timklge.karooheadwind.throttle
import de.timklge.karooheadwind.util.msInUserUnit
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.roundToInt

class WindDirectionAndSpeedDataType(
    private val karooSystem: KarooSystemService,
    private val applicationContext: Context
) : DataTypeImpl("karoo-headwind", "windDirectionAndSpeed") {
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private val glance = GlanceRemoteViews()

    data class StreamData(val headingResponse: HeadingResponse,
                          val absoluteWindDirection: Double?,
                          val windSpeed: Double?,
                          val settings: HeadwindSettings,
                          val gustSpeed: Double?,
                          val isImperial: Boolean,
                          val isVisible: Boolean)

    private fun previewFlow(profileFlow: Flow<UserProfile>): Flow<StreamData> {
        return flow {
            val profile = profileFlow.first()

            while (true) {
                val bearing = (0..360).random().toDouble()
                val windSpeed = (0..20).random()
                val gustSpeed = windSpeed * ((10..40).random().toDouble() / 10)
                val isImperial = profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL

                emit(StreamData(HeadingResponse.Value(bearing), bearing, windSpeed.toDouble(), HeadwindSettings(), gustSpeed = gustSpeed, isImperial = isImperial, isVisible = true))

                delay(2_000)
            }
        }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
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
                karooSystem.streamDatatypeIsVisible(dataTypeId)
            ) { headingResponse, weatherData, settings, userProfile, isVisible ->
                val isImperial = userProfile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
                val absoluteWindDirection = weatherData?.windDirection
                val windSpeed = weatherData?.windSpeed
                val gustSpeed = weatherData?.windGusts

                StreamData(headingResponse, absoluteWindDirection, windSpeed, settings, isImperial = isImperial, gustSpeed = gustSpeed, isVisible = isVisible)
            }
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(ShowCustomStreamState("", null))

            val refreshRate = karooSystem.getRefreshRateInMilliseconds(context)
            flow.filter { it.isVisible }.throttle(refreshRate).collect { streamData ->
                Log.d(KarooHeadwindExtension.TAG, "Updating wind speed and direction view")

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

                val windSpeedUserUnit = msInUserUnit(windSpeed, streamData.isImperial)
                val gustSpeedUserUnit = msInUserUnit(streamData.gustSpeed ?: 0.0, streamData.isImperial)

                val mainText = let {
                    "${windSpeedUserUnit.roundToInt().absoluteValue}"
                }


                val subtext = "Max ${gustSpeedUserUnit.roundToInt()}"

                val headwindSpeed = cos( (windDirection + 180) * Math.PI / 180.0) * windSpeed
                val windSpeedInKmh = headwindSpeed * 3.6

                val result = glance.compose(context, DpSize.Unspecified) {
                    HeadwindDirection(
                        baseBitmap,
                        windDirection.roundToInt(),
                        config.textSize,
                        mainText,
                        subtext,
                        interpolateWindColor(windSpeedInKmh, false, context),
                        interpolateWindColor(windSpeedInKmh, true, context),
                        wideMode = config.gridSize.first == 60,
                        preview = config.preview,
                    )
                }

                emitter.updateView(result.remoteViews)
            }
        }
        emitter.setCancellable {
            Log.d(KarooHeadwindExtension.TAG, "Stopping wind speed and direction view with $emitter")
            configJob.cancel()
            viewJob.cancel()
        }
    }
}