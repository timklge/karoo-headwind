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

import android.content.Context
import de.timklge.karooheadwind.UpcomingRoute
import de.timklge.karooheadwind.WindUnit
import de.timklge.karooheadwind.screens.LineGraphBuilder
import io.hammerhead.karooext.KarooSystemService

class TemperatureForecastDataType(karooSystem: KarooSystemService) : LineGraphForecastDataType(karooSystem, "temperatureForecast") {
    override fun getLineData(
        lineData: List<LineData>,
        isImperial: Boolean,
        windUnit: WindUnit,
        upcomingRoute: UpcomingRoute?,
        isPreview: Boolean,
        context: Context
    ): LineGraphForecastData {
        val linePoints = lineData.map { data ->
            if (isImperial) {
                data.weatherData.temperature * 9 / 5 + 32 // Convert Celsius to Fahrenheit
            } else {
                data.weatherData.temperature // Keep Celsius
            }
        }

        return LineGraphForecastData.LineData(setOf(
            LineGraphBuilder.Line(
                dataPoints = linePoints.mapIndexed { index, value ->
                    LineGraphBuilder.DataPoint(index.toFloat(), value.toFloat())
                },
                color = android.graphics.Color.RED,
                label = if (!isImperial) "°C" else "°F",
            )
        ))
    }

}
