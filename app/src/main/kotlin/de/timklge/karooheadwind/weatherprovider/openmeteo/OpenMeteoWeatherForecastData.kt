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
data class OpenMeteoWeatherForecastData(
    @SerialName("time") val time: List<Long>,
    @SerialName("temperature_2m") val temperature: List<Double>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int>,
    @SerialName("precipitation") val precipitation: List<Double>,
    @SerialName("weather_code") val weatherCode: List<Int>,
    @SerialName("wind_speed_10m") val windSpeed: List<Double>,
    @SerialName("wind_direction_10m") val windDirection: List<Double>,
    @SerialName("wind_gusts_10m") val windGusts: List<Double>,
    @SerialName("cloud_cover") val cloudCover: List<Double>,
    @SerialName("surface_pressure") val surfacePressure: List<Double>,
    @SerialName("pressure_msl") val sealevelPressure: List<Double>,
    @SerialName("is_day") val isDay: List<Int>,
    @SerialName("relative_humidity_2m") val relativeHumidity: List<Int>,
    @SerialName("uv_index") val uvi: List<Double>,
    ) {
    fun toWeatherData(): List<WeatherData> {
        return time.mapIndexed { index, t ->
            WeatherData(
                temperature = temperature[index],
                precipitation = precipitation[index],
                precipitationProbability = precipitationProbability[index].toDouble(),
                windSpeed = windSpeed[index],
                windDirection = windDirection[index],
                windGusts = windGusts[index],
                weatherCode = weatherCode[index],
                isNight = isDay[index] == 0,
                time = t,
                isForecast = true,
                cloudCover = cloudCover[index],
                surfacePressure = surfacePressure[index],
                sealevelPressure = sealevelPressure[index],
                relativeHumidity = relativeHumidity[index],
                uvi = uvi[index]
            )
        }
    }
}