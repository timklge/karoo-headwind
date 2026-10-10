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
import de.timklge.karooheadwind.streamRideState
import de.timklge.karooheadwind.weatherprovider.WeatherData
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import kotlin.math.cos
import kotlin.math.sin

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
    }

    private val headwindSpeedBuckets = mutableMapOf<Int, Int>() // wind speed bucket to count of samples
    private val crosswindSpeedBuckets = mutableMapOf<Int, Int>() // wind speed bucket to count of samples
    private val headwindGustBuckets = mutableMapOf<Int, Int>() // gust speed bucket to count of samples
    private val crosswindGustBuckets = mutableMapOf<Int, Int>() // gust speed bucket to count of samples
    private var totalSamples = 0
    private val lock = Any()

    fun addSample(headwindSpeed: Double, crosswindSpeed: Double, headwindGustSpeed: Double, crosswindGustSpeed: Double) {
        synchronized(lock) {
            headwindSpeedBuckets.merge(toBucket(headwindSpeed), 1, Int::plus)
            crosswindSpeedBuckets.merge(toBucket(crosswindSpeed), 1, Int::plus)
            headwindGustBuckets.merge(toBucket(headwindGustSpeed), 1, Int::plus)
            crosswindGustBuckets.merge(toBucket(crosswindGustSpeed), 1, Int::plus)

            totalSamples++
        }
    }

    fun reset() {
        synchronized(lock) {
            headwindSpeedBuckets.clear()
            crosswindSpeedBuckets.clear()
            headwindGustBuckets.clear()
            crosswindGustBuckets.clear()
            totalSamples = 0
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

    fun getRideTimeInCrosswind(): Duration {
        synchronized(lock) {
            val crosswindSamples = crosswindSpeedBuckets.filter { (bucket, _) -> bucket * BUCKET_SIZE >= CROSSWIND_THRESHOLD }
                .values.sum()
            return Duration.ofMillis(crosswindSamples * SAMPLE_INTERVAL_MS)
        }
    }

    fun start(karooSystemService: KarooSystemService): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            val latestInput = MutableStateFlow<Pair<HeadingResponse, WeatherData?>?>(null)

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
                    crosswindGustSpeed = crosswind * weather.windGusts
                )
            }
        }
    }
}