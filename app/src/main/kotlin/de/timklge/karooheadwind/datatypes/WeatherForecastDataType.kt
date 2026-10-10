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

package de.timklge.karooheadwind.datatypes

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import de.timklge.karooheadwind.TemperatureUnit
import de.timklge.karooheadwind.WindUnit
import de.timklge.karooheadwind.weatherprovider.WeatherInterpretation
import io.hammerhead.karooext.KarooSystemService

class WeatherForecastDataType(karooSystem: KarooSystemService) : ForecastDataType(karooSystem, "weatherForecast") {
    @Composable
    override fun RenderWidget(
        arrowBitmap: Bitmap,
        current: WeatherInterpretation,
        windBearing: Int,
        windSpeed: Int,
        windGusts: Int,
        precipitation: Double,
        precipitationProbability: Int?,
        temperature: Int,
        temperatureUnit: TemperatureUnit,
        timeLabel: String,
        dateLabel: String?,
        distance: Double?,
        isImperial: Boolean,
        isNight: Boolean,
        windUnit: WindUnit,
        uvi: Double,
    ) {
        Weather(
            arrowBitmap = arrowBitmap,
            current = current,
            windBearing = windBearing,
            windSpeed = windSpeed,
            windGusts = windGusts,
            precipitation = precipitation,
            precipitationProbability = precipitationProbability,
            temperature = temperature,
            temperatureUnit = temperatureUnit,
            timeLabel = timeLabel,
            dateLabel = dateLabel,
            distance = distance,
            isImperial = isImperial,
            isNight = isNight,
        )
    }

}
