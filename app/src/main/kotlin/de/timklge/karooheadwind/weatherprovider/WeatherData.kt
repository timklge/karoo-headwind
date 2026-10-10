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

package de.timklge.karooheadwind.weatherprovider

import kotlinx.serialization.Serializable

@Serializable
data class WeatherData(
    val time: Long,
    val temperature: Double,
    val relativeHumidity: Int,
    val precipitation: Double,
    val precipitationProbability: Double? = null,
    val cloudCover: Double,
    val sealevelPressure: Double,
    val surfacePressure: Double,
    val windSpeed: Double,
    val windDirection: Double,
    val windGusts: Double,
    val weatherCode: Int,
    val isForecast: Boolean,
    val isNight: Boolean,
    val uvi: Double,
)

