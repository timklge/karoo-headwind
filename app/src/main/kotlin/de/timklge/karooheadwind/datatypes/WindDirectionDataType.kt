package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.streamDataFlow
import de.timklge.karooheadwind.streamDatatypeIsVisible
import de.timklge.karooheadwind.throttle
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.ViewEmitter
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
import kotlin.math.roundToInt

class WindDirectionDataType(val karooSystem: KarooSystemService, context: Context) : BaseDataType(karooSystem, context, "windDirection"){
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private val glance = GlanceRemoteViews()

    companion object {
        private val windDirections = arrayOf(
            "N", "NNE", "NE", "ENE",
            "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW",
            "W", "WNW", "NW", "NNW"
        )
    }

    override fun getValue(data: WeatherData, userProfile: UserProfile): Double {
        return data.windDirection
    }

    data class StreamData(
        val windBearing: Double,
        val isVisible: Boolean
    )

    private fun previewFlow(): Flow<StreamData> {
        return flow {
            while (true) {
                emit(StreamData(
                    (0..360).random().toDouble(),
                    true
                ))
                delay(1_000)
            }
        }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = true))
            awaitCancellation()
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(ShowCustomStreamState("", null))

            val flow = if (config.preview){
                previewFlow()
            } else {
                combine(
                    karooSystem.streamDataFlow(dataTypeId),
                    karooSystem.streamDatatypeIsVisible(dataTypeId)
                ) { windBearing, isVisible ->
                    StreamData(
                        windBearing = (windBearing as? StreamState.Streaming)?.dataPoint?.singleValue ?: 0.0,
                        isVisible = isVisible
                    )
                }
            }

            val refreshRate = karooSystem.getRefreshRateInMilliseconds(context)

            flow.filter { it.isVisible }.throttle(refreshRate).collect { (windBearing, isVisible) ->
                val windCardinalDirectionIndex = ((windBearing % 360) / 22.5).roundToInt() % 16

                val text = windDirections[windCardinalDirectionIndex]
                Log.d(KarooHeadwindExtension.TAG,"Updating wind direction view")
                val result = glance.compose(context, DpSize.Unspecified) {
                    Box(modifier = GlanceModifier.fillMaxSize(),
                        contentAlignment = Alignment(
                            vertical = Alignment.Vertical.Top,
                            horizontal = when(config.alignment){
                                ViewConfig.Alignment.LEFT -> Alignment.Horizontal.Start
                                ViewConfig.Alignment.CENTER -> Alignment.Horizontal.CenterHorizontally
                                ViewConfig.Alignment.RIGHT -> Alignment.Horizontal.End
                            },
                        )) {
                        Text(text, style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontFamily = FontFamily.Monospace, fontSize = TextUnit(
                            config.textSize.toFloat(), TextUnitType.Sp)))
                    }
                }
                emitter.updateView(result.remoteViews)
            }
        }

        emitter.setCancellable {
            configJob.cancel()
            viewJob.cancel()
        }
    }
}