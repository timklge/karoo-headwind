package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.OpenMeteoCurrentWeatherResponse
import de.timklge.karooheadwind.WindDirectionIndicatorTextSetting
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamSettings
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.cos

class UserWindSpeedDataType(
    private val karooSystem: KarooSystemService,
    private val context: Context
) : DataTypeImpl("karoo-headwind", "userwindSpeed"){

    data class StreamData(val headingResponse: HeadingResponse, val weatherResponse: OpenMeteoCurrentWeatherResponse?, val settings: HeadwindSettings)

    companion object {
        fun streamValues(context: Context, karooSystem: KarooSystemService): Flow<Double> = flow {
            karooSystem.getRelativeHeadingFlow(context)
                .combine(context.streamCurrentWeatherData()) { value, data -> value to data }
                .combine(context.streamSettings(karooSystem)) { (value, data), settings ->
                    StreamData(value, data.firstOrNull()?.data, settings)
                }
                .filter { it.weatherResponse != null }
                .collect { streamData ->
                    val windSpeed = streamData.weatherResponse?.current?.windSpeed ?: 0.0
                    val windDirection = (streamData.headingResponse as? HeadingResponse.Value)?.diff ?: 0.0

                    if (streamData.settings.windDirectionIndicatorTextSetting == WindDirectionIndicatorTextSetting.HEADWIND_SPEED){
                        val headwindSpeed = cos((windDirection + 180) * Math.PI / 180.0) * windSpeed

                        emit(headwindSpeed)
                    } else {
                        emit(windSpeed)
                    }
                }
        }
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            streamValues(context, karooSystem)
                .collect { value ->
                    emitter.onNext(
                        StreamState.Streaming(
                            DataPoint(
                                dataTypeId,
                                mapOf(DataType.Field.SINGLE to value)
                            )
                        )
                    )
                }
        }

        emitter.setCancellable {
            job.cancel()
        }
    }
}