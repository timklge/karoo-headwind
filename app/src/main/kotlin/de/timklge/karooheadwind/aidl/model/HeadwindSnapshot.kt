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

package de.timklge.karooheadwind.aidl.model

import de.timklge.karooheadwind.HeadwindStats
import de.timklge.karooheadwind.WeatherDataProvider
import de.timklge.karooheadwind.WindUnit
import de.timklge.karooheadwind.weatherprovider.WeatherDataResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val headwindSnapshotJson = Json { encodeDefaults = true }

fun buildHeadwindSnapshot(stats: HeadwindStats, response: WeatherDataResponse?, windUnit: WindUnit): HeadwindSnapshot {
    val forecast = response?.data?.takeIf { it.isNotEmpty() }?.map { location ->
        HeadwindForecastPoint(
            lat = location.coords.lat,
            lon = location.coords.lon,
            distanceAlongRoute = location.coords.distanceAlongRoute,
            current = location.current,
            hourly = location.forecasts ?: emptyList(),
        )
    }

    return HeadwindSnapshot(
        error = stats.lastWeatherError,
        lastSuccessfulFetchEpochSeconds = stats.lastSuccessfulWeatherRequest?.let { it / 1000 },
        lastFailedFetchEpochSeconds = stats.failedWeatherRequest?.let { it / 1000 },
        provider = stats.lastSuccessfulWeatherProvider,
        windUnit = windUnit,
        forecast = forecast,
    )
}

/**
 * Current weather as streamed over [de.timklge.karooheadwind.aidl.HeadwindService].
 *
 * All values are SI: wind in m/s, directions in degrees, temperature in °C, timestamps in epoch seconds.
 * [windUnit] is the wind unit the user chose for display in karoo-headwind. Values are not converted to it.
 * Fetch timestamps are the last successful and last failed weather download. Clients choose their own staleness threshold.
 * [error] is the message of the most recent failed weather download, or null if the last download succeeded.
 * [forecast] holds the cached forecast for each requested location along the route, or null when nothing is cached.
 */
@Serializable
data class HeadwindSnapshot(
    val version: Int = VERSION,
    val error: String? = null,
    val lastSuccessfulFetchEpochSeconds: Long? = null,
    val lastFailedFetchEpochSeconds: Long? = null,
    val provider: WeatherDataProvider? = null,
    val windUnit: WindUnit? = null,
    val forecast: List<HeadwindForecastPoint>? = null,
) {
    companion object {
        const val VERSION = 1
    }
}