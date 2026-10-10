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

enum class WeatherInterpretation {
    CLEAR, CLOUDY, RAINY, SNOWY, DRIZZLE, THUNDERSTORM, UNKNOWN;

    companion object {
        // WMO weather interpretation codes (WW)
        fun fromWeatherCode(code: Int?): WeatherInterpretation {
            return when(code){
                0 -> CLEAR
                1, 2, 3 -> CLOUDY
                45, 48, 61, 63, 65, 66, 67, 80, 81, 82 -> RAINY
                71, 73, 75, 77, 85, 86 -> SNOWY
                51, 53, 55, 56, 57 -> DRIZZLE
                95, 96, 99 -> THUNDERSTORM
                else -> UNKNOWN
            }
        }

        fun getKnownWeatherCodes(): Set<Int> = setOf(0, 1, 2, 3, 45, 48, 61, 63, 65, 66, 67, 80, 81, 82, 71, 73, 75, 77, 85, 86, 51, 53, 55, 56, 57, 95, 96, 99)
    }
}