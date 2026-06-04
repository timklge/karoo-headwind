package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.Color
import de.timklge.karooheadwind.UpcomingRoute
import de.timklge.karooheadwind.WindUnit
import de.timklge.karooheadwind.screens.LineGraphBuilder
import de.timklge.karooheadwind.util.msInWindUnit
import io.hammerhead.karooext.KarooSystemService

fun remap(value: Float, fromLow: Float, fromHigh: Float, toLow: Float, toHigh: Float): Float {
    if (fromHigh == fromLow) return toLow
    return toLow + (value - fromLow) / (fromHigh - fromLow) * (toHigh - toLow)
}

class WindForecastDataType(karooSystem: KarooSystemService) : LineGraphForecastDataType(karooSystem, "windForecast") {
    override fun getLineData(
        lineData: List<LineData>,
        isImperial: Boolean,
        windUnit: WindUnit,
        upcomingRoute: UpcomingRoute?,
        isPreview: Boolean,
        context: Context
    ): LineGraphForecastData {
        val windPoints = lineData.map { data ->
            msInWindUnit(data.weatherData.windSpeed, windUnit)
        }

        val gustPoints = lineData.map { data ->
            msInWindUnit(data.weatherData.windGusts, windUnit)
        }

        return LineGraphForecastData.LineData(buildSet {
            add(LineGraphBuilder.Line(
                dataPoints = gustPoints.mapIndexed { index, value ->
                    LineGraphBuilder.DataPoint(index.toFloat(), value.toFloat())
                },
                color = Color.DKGRAY,
                label = "Gust" // if (!isImperial) "Gust km/h" else "Gust mph",
            ))

            add(LineGraphBuilder.Line(
                dataPoints = windPoints.mapIndexed { index, value ->
                    LineGraphBuilder.DataPoint(index.toFloat(), value.toFloat())
                },
                color = Color.GRAY,
                label = "Wind" // if (!isImperial) "Wind km/h" else "Wind mph",
            ))
        })
    }
}
