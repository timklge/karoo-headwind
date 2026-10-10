/*
 * Copyright 2024-2026 karoo-headwind contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.streamRideState
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class RelativeElevationGainDataType(private val karooSystemService: KarooSystemService, private val context: Context): DataTypeImpl("karoo-headwind", "relativeElevationGain") {
    fun updateAccumulatedWindElevation(
        previousAccumulatedWindElevation: Double,
        relativeGrade: Double,
        actualGrade: Double,
        riderSpeed: Double,
        deltaTime: Double
    ): Double {
        val gradeDifferenceDueToWind = relativeGrade - actualGrade
        var intervalWindElevation = 0.0

        if (gradeDifferenceDueToWind > 0 && relativeGrade > 0) {
            val distanceCovered = riderSpeed * deltaTime
            intervalWindElevation = distanceCovered * gradeDifferenceDueToWind
        }

        return previousAccumulatedWindElevation + intervalWindElevation
    }

    private var currentWindElevationGain = 0.0
    private val currentWindElevationGainLock = Mutex()

    override fun startStream(emitter: Emitter<StreamState>) {
        val resetJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystemService.streamRideState().collect { rideState ->
                if (rideState is RideState.Idle) {
                    currentWindElevationGainLock.withLock { currentWindElevationGain = 0.0 }
                }
            }
        }
        val job = CoroutineScope(Dispatchers.IO).launch {
            var lastTime: Long? = null

            RelativeGradeDataType.streamRelativeGrade(karooSystemService, context).collect { streamValues ->
                val now = Instant.now().toEpochMilli()
                val deltaTime = (now - (lastTime ?: now)) / 1000.0
                lastTime = now

                val windElevation = currentWindElevationGainLock.withLock {
                    currentWindElevationGain = updateAccumulatedWindElevation(
                        currentWindElevationGain,
                        streamValues.relativeGrade,
                        streamValues.actualGrade,
                        streamValues.riderSpeed,
                        deltaTime
                    )

                    currentWindElevationGain
                }

                emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to windElevation))))
            }
        }
        emitter.setCancellable {
            resetJob.cancel()
            job.cancel()
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(formatDataTypeId = DataType.Type.ELEVATION_GAIN))
    }
}