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

/** Weather for one point in time. */
@Serializable
data class WeatherData(
    /** Epoch seconds. */
    val time: Long,
    /** °C. */
    val temperature: Double,
    /** Percent. */
    val relativeHumidity: Int,
    val precipitation: Double,
    /** Percent, or null if the provider does not report it. */
    val precipitationProbability: Double? = null,
    /** Percent. */
    val cloudCover: Double,
    val sealevelPressure: Double,
    val surfacePressure: Double,
    /** m/s. */
    val windSpeed: Double,
    /** Degrees, 0 = North. */
    val windDirection: Double,
    /** m/s. */
    val windGusts: Double,
    /** Provider-specific weather code. */
    val weatherCode: Int,
    val isForecast: Boolean,
    val isNight: Boolean,
    val uvi: Double,
)
