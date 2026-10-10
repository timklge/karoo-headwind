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

package de.timklge.karooheadwind.weatherprovider.openmeteo

import de.timklge.karooheadwind.weatherprovider.WeatherData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoWeatherData(
    val time: Long, val interval: Int,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("relative_humidity_2m") val relativeHumidity: Int,
    @SerialName("precipitation") val precipitation: Double,
    @SerialName("cloud_cover") val cloudCover: Int,
    @SerialName("surface_pressure") val surfacePressure: Double,
    @SerialName("pressure_msl") val sealevelPressure: Double,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("wind_direction_10m") val windDirection: Double,
    @SerialName("wind_gusts_10m") val windGusts: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("is_day") val isDay: Int,
    @SerialName("uv_index") val uvi: Double,
) {
    fun toWeatherData(): WeatherData = WeatherData(
        temperature = temperature,
        relativeHumidity = relativeHumidity,
        precipitation = precipitation,
        cloudCover = cloudCover.toDouble(),
        surfacePressure = surfacePressure,
        sealevelPressure = sealevelPressure,
        windSpeed = windSpeed,
        windDirection = windDirection,
        windGusts = windGusts,
        weatherCode = weatherCode,
        time = time,
        isForecast = false,
        isNight = isDay == 0,
        uvi = uvi
    )
}

