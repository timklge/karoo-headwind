package de.timklge.karooheadwind.datatypes

import de.timklge.karooheadwind.UpcomingRoute
import de.timklge.karooheadwind.screens.LineGraphBuilder
import io.hammerhead.karooext.KarooSystemService

class PrecipitationForecastDataType(karooSystem: KarooSystemService) : LineGraphForecastDataType(karooSystem, "precipitationForecast") {
    override fun getLineData(
        lineData: List<LineData>,
        isImperial: Boolean,
        upcomingRoute: UpcomingRoute?,
        isPreview: Boolean
    ): Set<LineGraphBuilder.Line> {
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

        return setOf(
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
        )
    }

}