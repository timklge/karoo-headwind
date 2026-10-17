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
import de.timklge.karooheadwind.datatypes.WindAggregator
import de.timklge.karooheadwind.datatypes.WindCategoryStats
import de.timklge.karooheadwind.datatypes.WindSessionStats
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.throttle
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.models.WriteToSessionMesg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos

class HeadwindFitFileWriter(
    private val karooSystem: KarooSystemService,
    private val context: Context,
    private val windAggregator: WindAggregator,
) {
    companion object {
        // FIT base types
        private const val FIT_BASE_TYPE_UINT16: Short = 0x84
        internal const val FIT_BASE_TYPE_FLOAT32: Short = 0x88

        const val SESSION_UPDATE_INTERVAL_MS = 60_000L

        val WIND_SPEED = DeveloperField(0, FIT_BASE_TYPE_FLOAT32, "wind_speed", "m/s")
        val WIND_DIRECTION = DeveloperField(1, FIT_BASE_TYPE_UINT16, "wind_direction", "degrees")
        val HEADWIND_SPEED = DeveloperField(2, FIT_BASE_TYPE_FLOAT32, "headwind_speed", "m/s")
        val HEADWIND_DIRECTION = DeveloperField(3, FIT_BASE_TYPE_UINT16, "headwind_direction", "degrees")
        val WIND_GUST = DeveloperField(4, FIT_BASE_TYPE_FLOAT32, "wind_gust", "m/s")

        class CategoryFields(
            val timePercent: DeveloperField,
            val avgRideSpeed: DeveloperField,
            val maxRideSpeed: DeveloperField,
            val avgWindSpeed: DeveloperField,
            val maxWindSpeed: DeveloperField,
        )

        val HEADWIND_FIELDS = CategoryFields(
            timePercent = DeveloperField(5, FIT_BASE_TYPE_UINT16, "headwind_time_percent", "%"),
            avgRideSpeed = DeveloperField(6, FIT_BASE_TYPE_FLOAT32, "headwind_avg_ride_speed", "m/s"),
            maxRideSpeed = DeveloperField(7, FIT_BASE_TYPE_FLOAT32, "headwind_max_ride_speed", "m/s"),
            avgWindSpeed = DeveloperField(8, FIT_BASE_TYPE_FLOAT32, "headwind_avg_wind_speed", "m/s"),
            maxWindSpeed = DeveloperField(9, FIT_BASE_TYPE_FLOAT32, "headwind_max_wind_speed", "m/s"),
        )
        val TAILWIND_FIELDS = CategoryFields(
            timePercent = DeveloperField(10, FIT_BASE_TYPE_UINT16, "tailwind_time_percent", "%"),
            avgRideSpeed = DeveloperField(11, FIT_BASE_TYPE_FLOAT32, "tailwind_avg_ride_speed", "m/s"),
            maxRideSpeed = DeveloperField(12, FIT_BASE_TYPE_FLOAT32, "tailwind_max_ride_speed", "m/s"),
            avgWindSpeed = DeveloperField(13, FIT_BASE_TYPE_FLOAT32, "tailwind_avg_wind_speed", "m/s"),
            maxWindSpeed = DeveloperField(14, FIT_BASE_TYPE_FLOAT32, "tailwind_max_wind_speed", "m/s"),
        )
        val CROSSWIND_FIELDS = CategoryFields(
            timePercent = DeveloperField(15, FIT_BASE_TYPE_UINT16, "crosswind_time_percent", "%"),
            avgRideSpeed = DeveloperField(16, FIT_BASE_TYPE_FLOAT32, "crosswind_avg_ride_speed", "m/s"),
            maxRideSpeed = DeveloperField(17, FIT_BASE_TYPE_FLOAT32, "crosswind_max_ride_speed", "m/s"),
            avgWindSpeed = DeveloperField(18, FIT_BASE_TYPE_FLOAT32, "crosswind_avg_wind_speed", "m/s"),
            maxWindSpeed = DeveloperField(19, FIT_BASE_TYPE_FLOAT32, "crosswind_max_wind_speed", "m/s"),
        )
        val WIND_AVG_SPEED = DeveloperField(20, FIT_BASE_TYPE_FLOAT32, "wind_avg_speed", "m/s")
        val WIND_MAX_SPEED = DeveloperField(21, FIT_BASE_TYPE_FLOAT32, "wind_max_speed", "m/s")

        private fun MutableList<FieldValue>.addCategoryValues(fields: CategoryFields, stats: WindCategoryStats) {
            add(FieldValue(fields.timePercent, stats.timePercent))
            stats.avgRideSpeed?.let { add(FieldValue(fields.avgRideSpeed, it)) }
            stats.maxRideSpeed?.let { add(FieldValue(fields.maxRideSpeed, it)) }
            stats.avgWindSpeed?.let { add(FieldValue(fields.avgWindSpeed, it)) }
            stats.maxWindSpeed?.let { add(FieldValue(fields.maxWindSpeed, it)) }
        }

        fun buildSessionValues(stats: WindSessionStats): List<FieldValue> {
            return buildList {
                addCategoryValues(HEADWIND_FIELDS, stats.headwind)
                addCategoryValues(TAILWIND_FIELDS, stats.tailwind)
                addCategoryValues(CROSSWIND_FIELDS, stats.crosswind)
                stats.avgWindSpeed?.let { add(FieldValue(WIND_AVG_SPEED, it)) }
                stats.maxWindSpeed?.let { add(FieldValue(WIND_MAX_SPEED, it)) }
            }
        }

        fun buildRecordValues(
            relativeAngle: Double?,
            windSpeed: Double?,
            windDirection: Double?,
            windGust: Double?,
        ): List<FieldValue> {
            return buildList {
                if (windSpeed != null) add(FieldValue(WIND_SPEED, windSpeed))
                if (windDirection != null) add(FieldValue(WIND_DIRECTION, windDirection))
                if (windGust != null) add(FieldValue(WIND_GUST, windGust))

                if (relativeAngle != null && windSpeed != null) {
                    val headwindSpeed = cos(Math.toRadians(relativeAngle + 180)) * windSpeed
                    add(FieldValue(HEADWIND_SPEED, headwindSpeed))

                    val headwindDirection = if (relativeAngle < 0) relativeAngle + 360 else relativeAngle
                    add(FieldValue(HEADWIND_DIRECTION, headwindDirection))
                }
            }
        }
    }

    fun start(emitter: Emitter<FitEffect>): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            launch {
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

            while (isActive) {
                delay(SESSION_UPDATE_INTERVAL_MS)
                val values = buildSessionValues(windAggregator.getSessionStats())
                emitter.onNext(WriteToSessionMesg(values))
            }
        }
    }
}
