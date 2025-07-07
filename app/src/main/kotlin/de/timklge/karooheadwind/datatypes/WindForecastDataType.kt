package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.UpcomingRoute
import de.timklge.karooheadwind.lerpWeather
import de.timklge.karooheadwind.screens.LineGraphBuilder
import de.timklge.karooheadwind.screens.isNightMode
import de.timklge.karooheadwind.util.signedAngleDifference
import io.hammerhead.karooext.KarooSystemService
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

fun remap(value: Float, fromLow: Float, fromHigh: Float, toLow: Float, toHigh: Float): Float {
    if (fromHigh == fromLow) return toLow
    return toLow + (value - fromLow) / (fromHigh - fromLow) * (toHigh - toLow)
}

class WindForecastDataType(karooSystem: KarooSystemService) : LineGraphForecastDataType(karooSystem, "windForecast") {
    override fun getLineData(
        lineData: List<LineData>,
        isImperial: Boolean,
        upcomingRoute: UpcomingRoute?,
        isPreview: Boolean,
        context: Context
    ): LineGraphForecastData {
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