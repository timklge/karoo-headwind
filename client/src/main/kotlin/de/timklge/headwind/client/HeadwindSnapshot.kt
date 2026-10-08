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

package de.timklge.headwind.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Current weather as streamed by karoo-headwind.
 *
 * All values are SI: wind in m/s, directions in degrees, temperature in °C, timestamps in epoch seconds.
 * [windUnit] is the wind unit the user chose for display in karoo-headwind. Values are not converted to it.
 * Fetch timestamps are the last successful and last failed weather download. Choose your own staleness threshold.
 * [forecast] holds the cached forecast for each location along the route, or null when nothing is cached.
 */
@Serializable
data class HeadwindSnapshot(
    val version: Int = 1,
    val error: String? = null,
    val lastSuccessfulFetchEpochSeconds: Long? = null,
    val lastFailedFetchEpochSeconds: Long? = null,
    val provider: WeatherDataProvider? = null,
    val windUnit: WindUnit? = null,
    val forecast: List<HeadwindForecastPoint>? = null,
) {
    companion object {
        // Unknown keys and unknown providers are tolerated so that newer karoo-headwind versions stay readable.
        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /** Parses a snapshot JSON string as delivered by karoo-headwind. Throws if the JSON is malformed. */
        fun fromJson(snapshotJson: String): HeadwindSnapshot = json.decodeFromString<HeadwindSnapshot>(snapshotJson)
    }
}
