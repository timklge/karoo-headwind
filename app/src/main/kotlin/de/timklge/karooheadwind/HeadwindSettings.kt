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

package de.timklge.karooheadwind

import de.timklge.karooheadwind.datatypes.GpsCoordinates
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class WindUnit(val id: String, val label: String, val unitDisplay: String){
    KILOMETERS_PER_HOUR("kmh", "Kilometers (km/h)", "km/h"),
    METERS_PER_SECOND("ms", "Meters (m/s)", "m/s"),
    MILES_PER_HOUR("mph", "Miles (mph)", "mph"),
    KNOTS("kn", "Knots (kn)", "kn")
}

fun defaultWindUnit(isImperial: Boolean): WindUnit {
    return if (isImperial) WindUnit.MILES_PER_HOUR else WindUnit.KILOMETERS_PER_HOUR
}

enum class PrecipitationUnit(val id: String, val label: String, val unitDisplay: String){
    MILLIMETERS("mm", "Millimeters (mm)", "mm"),
    INCH("inch", "Inch", "in")
}

enum class TemperatureUnit(val id: String, val label: String, val unitDisplay: String){
    CELSIUS("celsius", "Celsius (°C)", "°C"),
    FAHRENHEIT("fahrenheit", "Fahrenheit (°F)", "°F")
}

enum class RoundLocationSetting(val id: String, val label: String, val km: Int){
    KM_1("1 km", "1 km", 1),
    KM_2("2 km", "2 km", 2),
    KM_3("3 km", "3 km", 3),
    KM_5("5 km", "5 km", 5)
}

@Serializable
data class HeadwindWidgetSettings(
    val currentForecastHourOffset: Int = 0
){
    companion object {
        val defaultWidgetSettings = Json.encodeToString(HeadwindWidgetSettings())
    }
}

@Serializable
data class HeadwindStats(
    val lastSuccessfulWeatherRequest: Long? = null,
    val lastSuccessfulWeatherPosition: GpsCoordinates? = null,
    val failedWeatherRequest: Long? = null,
    val lastSuccessfulWeatherProvider: WeatherDataProvider? = null
){
    companion object {
        val defaultStats = Json.encodeToString(HeadwindStats())
    }
}


enum class RefreshRate(val id: String, val k2Ms: Long, val k3Ms: Long) {
    FAST("fast", 1_000L, 500L),
    STANDARD("medium", 2_000L, 1_000L),
    SLOW("slow", 5_000L, 3_000L),
    MINIMUM("minimum", 10_000L, 10_000L);

    fun getDescription(isOnK2: Boolean): String {
        return if (isOnK2) {
            when (this) {
                FAST -> "Fast (1s)"
                STANDARD -> "Standard (2s)"
                SLOW -> "Slow (5s)"
                MINIMUM -> "Minimum (10s)"
            }
        } else {
            when (this) {
                FAST -> "Fastest"
                STANDARD -> "Standard (1s)"
                SLOW -> "Slow (3s)"
                MINIMUM -> "Minimum (10s)"
            }
        }
    }
}

@Serializable
data class HeadwindSettings(
    val welcomeDialogAccepted: Boolean = false,
    val roundLocationTo: RoundLocationSetting = RoundLocationSetting.KM_3,
    val forecastedKmPerHour: Int = 20,
    val forecastedMilesPerHour: Int = 12,
    val lastUpdateRequested: Long? = null,
    val showDistanceInForecast: Boolean = true,
    val weatherProvider: WeatherDataProvider = WeatherDataProvider.OPEN_METEO,
    val openWeatherMapApiKey: String = "",
    val refreshRate: RefreshRate = RefreshRate.STANDARD,
    val windUnit: WindUnit? = null,
){

    companion object {
        val defaultSettings = Json.encodeToString(HeadwindSettings())
    }

    fun getForecastMetersPerHour(isImperial: Boolean): Int {
        return if (isImperial) forecastedMilesPerHour * 1609 else forecastedKmPerHour * 1000
    }

    fun getWindUnit(isImperial: Boolean): WindUnit {
        return windUnit ?: defaultWindUnit(isImperial)
    }
}

