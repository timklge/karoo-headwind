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

import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.weatherprovider.WeatherDataForLocation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenWeatherMapWeatherDataForLocation(
    val lat: Double,
    val lon: Double,
    val timezone: String,
    @SerialName("timezone_offset") val timezoneOffset: Int,
    val current: OpenWeatherMapWeatherData,
    val hourly: List<OpenWeatherMapForecastData>
){
    fun toWeatherDataForLocation(distanceAlongRoute: Double?): WeatherDataForLocation {
        return WeatherDataForLocation(
            current = current.toWeatherData(),
            coords = GpsCoordinates(
                lat,
                lon,
                bearing = null,
                distanceAlongRoute = distanceAlongRoute
            ),
            timezone = timezone,
            forecasts = hourly.map { it.toWeatherData(current) }
        )
    }
}