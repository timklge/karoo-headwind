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

import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.weatherprovider.WeatherDataForLocation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoWeatherDataForLocation(
    val current: OpenMeteoWeatherData,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val elevation: Double,
    @SerialName("utc_offset_seconds") val utfOffsetSeconds: Int,
    @SerialName("hourly") val forecastData: OpenMeteoWeatherForecastData?,
) {
    fun toWeatherDataForLocation(distanceAlongRoute: Double?): WeatherDataForLocation {
        val forecasts = forecastData?.toWeatherData()
        return WeatherDataForLocation(
            current = current.toWeatherData(),
            coords = GpsCoordinates(latitude, longitude, bearing = null, distanceAlongRoute = distanceAlongRoute),
            timezone = timezone,
            elevation = elevation,
            forecasts = forecasts
        )
    }
}