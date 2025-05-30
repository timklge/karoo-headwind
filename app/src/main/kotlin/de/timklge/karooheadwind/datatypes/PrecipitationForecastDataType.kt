package de.timklge.karooheadwind.datatypes

import de.timklge.karooheadwind.screens.LineGraphBuilder
import io.hammerhead.karooext.KarooSystemService

class PrecipitationForecastDataType(karooSystem: KarooSystemService) : LineGraphForecastDataType(karooSystem, "precipitationForecast") {
    override fun getLineData(lineData: List<LineData>, isImperial: Boolean): Set<LineGraphBuilder.Line> {
        val precipitationPoints = lineData.map { data ->
            if (isImperial) { // Convert mm to inches
                data.weatherData.precipitation * 0.0393701 // Convert mm to inches
            } else {
                data.weatherData.precipitation
            }
        }

        return setOf(
            LineGraphBuilder.Line(
                dataPoints = precipitationPoints.mapIndexed { index, value ->
                    LineGraphBuilder.DataPoint(index.toFloat(), value.toFloat())
                },
                color = android.graphics.Color.BLUE,
                label = if (!isImperial) "mm" else "in",
            )
        )
    }

}