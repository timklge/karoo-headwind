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
import de.timklge.karooheadwind.HeadingResponse
import de.timklge.karooheadwind.getRelativeHeadingFlow
import de.timklge.karooheadwind.streamCurrentWeatherData
import de.timklge.karooheadwind.streamDataFlow
import de.timklge.karooheadwind.streamRideState
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class WindCategoryStats(
    val timePercent: Double,
    val avgRideSpeed: Double?, // m/s
    val maxRideSpeed: Double?, // m/s
    val avgWindSpeed: Double?, // m/s
    val maxWindSpeed: Double?, // m/s
)

data class WindSessionStats(
    val headwind: WindCategoryStats,
    val tailwind: WindCategoryStats,
    val crosswind: WindCategoryStats,
    val avgWindSpeed: Double?, // m/s, absolute wind speed over all samples
    val maxWindSpeed: Double?, // m/s, absolute wind speed over all samples
)

internal class CategoryAccumulator {
    private var samples = 0
    private var windSpeedSum = 0.0
    private var windSpeedMax = 0.0
    private var rideSpeedSamples = 0
    private var rideSpeedSum = 0.0
    private var rideSpeedMax = 0.0

    fun add(windSpeed: Double, rideSpeed: Double?) {
        samples++
        windSpeedSum += windSpeed
        windSpeedMax = max(windSpeedMax, windSpeed)

        if (rideSpeed != null) {
            rideSpeedSamples++
            rideSpeedSum += rideSpeed
            rideSpeedMax = max(rideSpeedMax, rideSpeed)
        }
    }

    fun clear() {
        samples = 0
        windSpeedSum = 0.0
        windSpeedMax = 0.0
        rideSpeedSamples = 0
        rideSpeedSum = 0.0
        rideSpeedMax = 0.0
    }

    fun toStats(totalSamples: Int): WindCategoryStats {
        return WindCategoryStats(
            timePercent = if (totalSamples > 0) samples * 100.0 / totalSamples else 0.0,
            avgRideSpeed = if (rideSpeedSamples > 0) rideSpeedSum / rideSpeedSamples else null,
            maxRideSpeed = if (rideSpeedSamples > 0) rideSpeedMax else null,
            avgWindSpeed = if (samples > 0) windSpeedSum / samples else null,
            maxWindSpeed = if (samples > 0) windSpeedMax else null,
        )
    }
}

class WindAggregator(val context: Context) {
    companion object {
        const val BUCKET_SIZE = 1 / 3.0 // m/s per bucket
        const val MAX_WIND_SPEED = 20.0 // m/s, bucket indices are limited to [-MAX_WIND_SPEED, MAX_WIND_SPEED]
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val HEADWIND_THRESHOLD = 1.0 // m/s, only count samples with headwind speed >= this threshold
        const val CROSSWIND_THRESHOLD = 1.0 // m/s, only count samples with crosswind speed >= this threshold

        fun toBucket(speed: Double): Int {
            return (speed.coerceIn(-MAX_WIND_SPEED, MAX_WIND_SPEED) / BUCKET_SIZE).toInt()
        }
        
        fun toBucketSpeed(speed: Double): Double = toBucket(speed) * BUCKET_SIZE
    }

    private val headwindSpeedBuckets = mutableMapOf<Int, Int>() // wind speed bucket to count of samples
    private val crosswindSpeedBuckets = mutableMapOf<Int, Int>() // wind speed bucket to count of samples
    private val headwindGustBuckets = mutableMapOf<Int, Int>() // gust speed bucket to count of samples
    private val crosswindGustBuckets = mutableMapOf<Int, Int>() // gust speed bucket to count of samples
    private val headwindAccumulator = CategoryAccumulator()
    private val tailwindAccumulator = CategoryAccumulator()
    private val crosswindAccumulator = CategoryAccumulator()
    private val allWindAccumulator = CategoryAccumulator()
    private var totalSamples = 0
    private val lock = Any()

    fun addSample(
        headwindSpeed: Double,
        crosswindSpeed: Double,
        headwindGustSpeed: Double,
        crosswindGustSpeed: Double,
        windSpeed: Double,
        rideSpeed: Double?,
    ) {
        synchronized(lock) {
            headwindSpeedBuckets.merge(toBucket(headwindSpeed), 1, Int::plus)
            crosswindSpeedBuckets.merge(toBucket(crosswindSpeed), 1, Int::plus)
            headwindGustBuckets.merge(toBucket(headwindGustSpeed), 1, Int::plus)
            crosswindGustBuckets.merge(toBucket(crosswindGustSpeed), 1, Int::plus)

            totalSamples++

            val headwindBucketSpeed = toBucketSpeed(headwindSpeed)
            val crosswindBucketSpeed = toBucketSpeed(crosswindSpeed)
            if (headwindBucketSpeed >= HEADWIND_THRESHOLD) {
                headwindAccumulator.add(headwindSpeed, rideSpeed)
            } else if (headwindBucketSpeed <= -HEADWIND_THRESHOLD) {
                tailwindAccumulator.add(-headwindSpeed, rideSpeed)
            }
            if (abs(crosswindBucketSpeed) >= CROSSWIND_THRESHOLD) {
                crosswindAccumulator.add(abs(crosswindSpeed), rideSpeed)
            }
            allWindAccumulator.add(abs(windSpeed), null)
        }
    }

    fun reset() {
        synchronized(lock) {
            headwindSpeedBuckets.clear()
            crosswindSpeedBuckets.clear()
            headwindGustBuckets.clear()
            crosswindGustBuckets.clear()
            headwindAccumulator.clear()
            tailwindAccumulator.clear()
            crosswindAccumulator.clear()
            allWindAccumulator.clear()
            totalSamples = 0
        }
    }

    fun getSessionStats(): WindSessionStats {
        synchronized(lock) {
            val allWind = allWindAccumulator.toStats(totalSamples)
            return WindSessionStats(
                headwind = headwindAccumulator.toStats(totalSamples),
                tailwind = tailwindAccumulator.toStats(totalSamples),
                crosswind = crosswindAccumulator.toStats(totalSamples),
                avgWindSpeed = allWind.avgWindSpeed,
                maxWindSpeed = allWind.maxWindSpeed,
            )
        }
    }

    fun getRideTimeInHeadwind(): Duration {
        synchronized(lock) {
            val headwindSamples = headwindSpeedBuckets.filter { (bucket, _) -> bucket * BUCKET_SIZE >= HEADWIND_THRESHOLD }
                .values.sum()
            return Duration.ofMillis(headwindSamples * SAMPLE_INTERVAL_MS)
        }
    }

    fun getHeadwindSpeedDistribution(): Map<Int, Int> {
        synchronized(lock) {
            return headwindSpeedBuckets.toMap()
        }
    }

    fun getHeadwindGustDistribution(): Map<Int, Int> {
        synchronized(lock) {
            return headwindGustBuckets.toMap()
        }
    }

    fun start(karooSystemService: KarooSystemService): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            val latestInput = MutableStateFlow<Pair<HeadingResponse, WeatherData?>?>(null)
            val latestRideSpeed = MutableStateFlow<Double?>(null)

            launch {
                karooSystemService.streamDataFlow(DataType.Type.SPEED).collect { state ->
                    latestRideSpeed.value = (state as? StreamState.Streaming)?.dataPoint?.singleValue
                }
            }

            launch {
                var previousRideState: RideState? = null

                karooSystemService.streamRideState().collect { rideState ->
                    // A new ride starts when transitioning from Idle to Recording (not on resume from Paused)
                    if (previousRideState is RideState.Idle && rideState is RideState.Recording) {
                        reset()
                    }
                    previousRideState = rideState
                }
            }

            launch {
                karooSystemService.getRelativeHeadingFlow(context)
                    .combine(context.streamCurrentWeatherData(karooSystemService)) { heading, weather -> heading to weather }
                    .collect { latestInput.value = it }
            }

            while (isActive) {
                delay(SAMPLE_INTERVAL_MS)

                val (heading, weather) = latestInput.value ?: continue
                if (heading !is HeadingResponse.Value || weather == null) continue

                // 0 = direct headwind, 90 = crosswind right (see RelativeGradeDataType)
                val relativeAngle = (heading.diff + 180) * Math.PI / 180.0
                val headwind = cos(relativeAngle)
                val crosswind = sin(relativeAngle)

                addSample(
                    headwindSpeed = headwind * weather.windSpeed,
                    crosswindSpeed = crosswind * weather.windSpeed,
                    headwindGustSpeed = headwind * weather.windGusts,
                    crosswindGustSpeed = crosswind * weather.windGusts,
                    windSpeed = weather.windSpeed,
                    rideSpeed = latestRideSpeed.value,
                )
            }
        }
    }
}