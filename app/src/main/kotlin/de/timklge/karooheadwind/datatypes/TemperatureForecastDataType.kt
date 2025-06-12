package de.timklge.karooheadwind.datatypes

import android.content.Context
import de.timklge.karooheadwind.UpcomingRoute
import de.timklge.karooheadwind.screens.LineGraphBuilder
import io.hammerhead.karooext.KarooSystemService

class TemperatureForecastDataType(karooSystem: KarooSystemService) : LineGraphForecastDataType(karooSystem, "temperatureForecast") {
    override fun getLineData(
        lineData: List<LineData>,
        isImperial: Boolean,
        upcomingRoute: UpcomingRoute?,
        isPreview: Boolean,
        context: Context
    ): Set<LineGraphBuilder.Line> {
        val linePoints = lineData.map { data ->
            if (isImperial) {
                data.weatherData.temperature * 9 / 5 + 32 // Convert Celsius to Fahrenheit
            } else {
                data.weatherData.temperature // Keep Celsius
            }
        }

        return setOf(
            LineGraphBuilder.Line(
                dataPoints = linePoints.mapIndexed { index, value ->
                    LineGraphBuilder.DataPoint(index.toFloat(), value.toFloat())
                },
                color = android.graphics.Color.RED,
                label = if (!isImperial) "°C" else "°F",
            )
        )
    }

}

