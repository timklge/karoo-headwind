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

class PrecipitationForecastDataType(karooSystem: KarooSystemService) : LineGraphForecastDataType(karooSystem, "precipitationForecast") {
    override fun getLineData(
        lineData: List<LineData>,
        isImperial: Boolean,
        windUnit: WindUnit,
        upcomingRoute: UpcomingRoute?,
        isPreview: Boolean,
        context: Context
    ): LineGraphForecastData {
        val precipitationPoints = lineData.map { data ->
            if (isImperial) { // Convert mm to inches
                data.weatherData.precipitation * 0.0393701 // Convert mm to inches
            } else {
                data.weatherData.precipitation
            }
        }

        val precipitationPropagation = lineData.map { data ->
            (data.weatherData.precipitationProbability?.coerceAtMost(99.0)) ?: 0.0 // Max 99 % so that the label doesn't take up too much space
        }

        return LineGraphForecastData.LineData(setOf(
            LineGraphBuilder.Line(
                dataPoints = precipitationPoints.mapIndexed { index, value ->
                    LineGraphBuilder.DataPoint(index.toFloat(), value.toFloat())
                },
                color = android.graphics.Color.BLUE,
                label = if (!isImperial) "mm" else "in",
            ),

            LineGraphBuilder.Line(
                dataPoints = precipitationPropagation.mapIndexed { index, value ->
                    LineGraphBuilder.DataPoint(index.toFloat(), value.toFloat())
                },
                color = android.graphics.Color.CYAN,
                label = "%",
                yAxis = LineGraphBuilder.YAxis.RIGHT
            )
        ))
    }

}
