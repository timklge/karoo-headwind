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

package de.timklge.karooheadwind.weatherprovider.openweathermap

import de.timklge.karooheadwind.weatherprovider.WeatherData
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset

@Serializable
data class OpenWeatherMapForecastData(
    val dt: Long,
    val temp: Double,
    val pressure: Int,
    val humidity: Int,
    val clouds: Int,
    val wind_speed: Double,
    val wind_deg: Int,
    val wind_gust: Double? = null,
    val rain: Rain? = null,
    val snow: Snow? = null,
    val weather: List<Weather>,
    val uvi: Double,
) {
    fun toWeatherData(currentWeatherData: OpenWeatherMapWeatherData): WeatherData {
        val dtInstant = Instant.ofEpochSecond(dt)
        val sunriseInstant = Instant.ofEpochSecond(currentWeatherData.sunrise)
        val sunsetInstant = Instant.ofEpochSecond(currentWeatherData.sunset)

        val dtTime = dtInstant.atZone(ZoneOffset.UTC).toLocalTime()
        val sunriseTime = sunriseInstant.atZone(ZoneOffset.UTC).toLocalTime()
        val sunsetTime = sunsetInstant.atZone(ZoneOffset.UTC).toLocalTime()

        return WeatherData(
            uvi = uvi,
            temperature = temp,
            relativeHumidity = humidity,
            precipitation = rain?.h1 ?: 0.0,
            cloudCover = clouds.toDouble(),
            surfacePressure = pressure.toDouble(),
            sealevelPressure = pressure.toDouble(), // FIXME
            windSpeed = wind_speed,
            windDirection = wind_deg.toDouble(),
            windGusts = wind_gust ?: wind_speed,
            weatherCode = OpenWeatherMapWeatherProvider.convertWeatherCodeToOpenMeteo(
                weather.firstOrNull()?.id ?: 800
            ),
            time = dt,
            isForecast = true,
            isNight = dtTime.isBefore(sunriseTime) || dtTime.isAfter(sunsetTime)
        )
    }
}
