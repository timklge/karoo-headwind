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

package de.timklge.karooheadwind.fit

import android.content.Context
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.WriteToRecordMesg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt

class HeadwindFitFileWriter(
    private val karooSystem: KarooSystemService,
    private val context: Context,
) {
    companion object {
        // FIT base types
        private const val FIT_BASE_TYPE_SINT16: Short = 0x83
        private const val FIT_BASE_TYPE_UINT16: Short = 0x84

        val WIND_SPEED = DeveloperField(0, FIT_BASE_TYPE_SINT16, "wind_speed", "cm/s")
        val WIND_DIRECTION = DeveloperField(1, FIT_BASE_TYPE_UINT16, "wind_direction", "degrees")
        val HEADWIND_SPEED = DeveloperField(2, FIT_BASE_TYPE_SINT16, "headwind_speed", "cm/s")
        val HEADWIND_DIRECTION = DeveloperField(3, FIT_BASE_TYPE_UINT16, "headwind_direction", "degrees")
        val WIND_GUST = DeveloperField(4, FIT_BASE_TYPE_SINT16, "wind_gust", "cm/s")
        
        private fun toCentimetersPerSecond(metersPerSecond: Double): Double {
            return (metersPerSecond * 100.0).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toDouble()
        }

        fun buildRecordValues(
            relativeAngle: Double?,
            windSpeed: Double?,
            windDirection: Double?,
            windGust: Double?,
        ): List<FieldValue> {
            return buildList {
                if (windSpeed != null) add(FieldValue(WIND_SPEED, toCentimetersPerSecond(windSpeed)))
                if (windDirection != null) add(FieldValue(WIND_DIRECTION, windDirection))
                if (windGust != null) add(FieldValue(WIND_GUST, toCentimetersPerSecond(windGust)))

                if (relativeAngle != null && windSpeed != null) {
                    val headwindSpeed = cos(Math.toRadians(relativeAngle + 180)) * windSpeed
                    add(FieldValue(HEADWIND_SPEED, toCentimetersPerSecond(headwindSpeed)))

                    val headwindDirection = if (relativeAngle < 0) relativeAngle + 360 else relativeAngle
                    add(FieldValue(HEADWIND_DIRECTION, headwindDirection))
                }
            }
        }
    }

    fun start(emitter: Emitter<FitEffect>): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            combine(
                karooSystem.getRelativeHeadingFlow(context),
                context.streamCurrentWeatherData(karooSystem),
            ) { heading, weather ->
                buildRecordValues(
                    relativeAngle = (heading as? HeadingResponse.Value)?.diff,
                    windSpeed = weather?.windSpeed,
                    windDirection = weather?.windDirection,
                    windGust = weather?.windGusts,
                )
            }
                .throttle(1_000L)
                .collect { values ->
                    if (values.isNotEmpty()) {
                        emitter.onNext(WriteToRecordMesg(values))
                    }
                }
        }
    }
}