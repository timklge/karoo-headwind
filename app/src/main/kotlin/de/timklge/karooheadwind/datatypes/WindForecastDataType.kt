package de.timklge.karooheadwind.datatypes

import de.timklge.karooheadwind.screens.LineGraphBuilder
import io.hammerhead.karooext.KarooSystemService

class WindForecastDataType(karooSystem: KarooSystemService) : LineGraphForecastDataType(karooSystem, "windForecast") {
    override fun getLineData(lineData: List<LineData>, isImperial: Boolean): Set<LineGraphBuilder.Line> {
        val windPoints = lineData.map { data ->
            if (isImperial) { // Convert m/s to mph
                data.weatherData.windSpeed * 2.23694 // Convert m/s to mph
            } else { // Convert m/s to km/h
                data.weatherData.windSpeed * 3.6 // Convert m/s to km/h
            }
        }

        val gustPoints = lineData.map { data ->
            if (isImperial) { // Convert m/s to mph
                data.weatherData.windGusts * 2.23694 // Convert m/s to mph
            } else { // Convert m/s to km/h
                data.weatherData.windGusts * 3.6 // Convert m/s to km/h
            }
        }

        return setOf(
            LineGraphBuilder.Line(
                dataPoints = windPoints.mapIndexed { index, value ->
                    LineGraphBuilder.DataPoint(index.toFloat(), value.toFloat())
                },
                color = android.graphics.Color.GRAY,
                label = "Wind" // if (!isImperial) "Wind km/h" else "Wind mph",
            ),
            LineGraphBuilder.Line(
                dataPoints = gustPoints.mapIndexed { index, value ->
                    LineGraphBuilder.DataPoint(index.toFloat(), value.toFloat())
                },
                color = android.graphics.Color.DKGRAY,
                label = "Gust" // if (!isImperial) "Gust km/h" else "Gust mph",
            )
        )
    }

}