package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamRideState
import de.timklge.karooheadwind.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.cos

class HeadwindTimeDataType(
    private val karooSystemService: KarooSystemService,
    private val context: Context
) : DataTypeImpl("karoo-headwind", "headwindTime") {

    companion object {
        const val HEADWIND_THRESHOLD_MS = 1.0

        fun updateAccumulatedHeadwindTime(
            previousAccumulatedTime: Double,
            headwindSpeed: Double,
            deltaTime: Double
        ): Double {
            return if (headwindSpeed >= HEADWIND_THRESHOLD_MS) {
                previousAccumulatedTime + deltaTime
            } else {
                previousAccumulatedTime
            }
        }
    }

    private var currentHeadwindTime = 0.0
    private val currentHeadwindTimeLock = Mutex()

    override fun startStream(emitter: Emitter<StreamState>) {
        val resetJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystemService.streamRideState().collect { rideState ->
                if (rideState is RideState.Idle) {
                    currentHeadwindTimeLock.withLock { currentHeadwindTime = 0.0 }
                }
            }
        }

        val job = CoroutineScope(Dispatchers.IO).launch {
            val refreshRate = karooSystemService.getRefreshRateInMilliseconds(context)

            karooSystemService.getRelativeHeadingFlow(context)
                .combine(context.streamCurrentWeatherData(karooSystemService)) { value, data -> value to data }
                .throttle(refreshRate)
                .collect { (headingResponse, weatherData) ->
                    val windSpeed = weatherData?.windSpeed ?: 0.0
                    val windDirection = (headingResponse as? HeadingResponse.Value)?.diff
                    val headwindSpeed = if (windDirection != null) {
                        cos((windDirection + 180) * Math.PI / 180.0) * windSpeed
                    } else {
                        0.0
                    }

                    val accumulatedTime = currentHeadwindTimeLock.withLock {
                        currentHeadwindTime = updateAccumulatedHeadwindTime(
                            currentHeadwindTime,
                            headwindSpeed,
                            refreshRate / 1000.0
                        )
                        currentHeadwindTime
                    }

                    emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to accumulatedTime * 1000.0))))
                }
        }

        emitter.setCancellable {
            resetJob.cancel()
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(formatDataTypeId = DataType.Type.RIDE_TIME))
    }
}
