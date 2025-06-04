package de.timklge.karooheadwind.datatypes

import android.util.Log
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.UpcomingRoute
import de.timklge.karooheadwind.lerpWeather
import de.timklge.karooheadwind.screens.LineGraphBuilder
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
        upcomingRoute: UpcomingRoute?
    ): Set<LineGraphBuilder.Line> {
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

        val headwindPoints = try {
            if (upcomingRoute != null){
                (0..<HEADWIND_SAMPLE_COUNT).mapNotNull { i ->
                    val t = i / HEADWIND_SAMPLE_COUNT.toDouble()
                    val beforeLineData = lineData.getOrNull(floor(lineData.size * t).toInt()) ?: lineData.firstOrNull()
                    val afterLineData = lineData.getOrNull(ceil(lineData.size * t).toInt()) ?: lineData.lastOrNull()

                    if (beforeLineData?.weatherData == null || afterLineData?.weatherData == null || beforeLineData.distance == null
                        || afterLineData.distance == null) return@mapNotNull null

                    val dt = remap(t.toFloat(),
                        floor(lineData.size * t).toFloat() / lineData.size,
                        ceil(lineData.size * t).toFloat() / lineData.size,
                        0.0f, 1.0f
                    ).toDouble()
                    val interpolatedWeather = lerpWeather(beforeLineData.weatherData, afterLineData.weatherData, dt)
                    val beforeDistanceAlongRoute = beforeLineData.distance
                    val afterDistanceAlongRoute = afterLineData.distance
                    val distanceAlongRoute = (beforeDistanceAlongRoute + (afterDistanceAlongRoute - beforeDistanceAlongRoute) * dt).coerceIn(0.0, upcomingRoute.routeLength)
                    val coordsAlongRoute = TurfMeasurement.along(upcomingRoute.routePolyline, distanceAlongRoute, TurfConstants.UNIT_METERS)
                    val nextCoordsAlongRoute = TurfMeasurement.along(upcomingRoute.routePolyline, distanceAlongRoute + 5, TurfConstants.UNIT_METERS)
                    val bearingAlongRoute = TurfMeasurement.bearing(coordsAlongRoute, nextCoordsAlongRoute)
                    val windBearing = interpolatedWeather.windDirection + 180
                    val diff = signedAngleDifference(bearingAlongRoute, windBearing)
                    val headwindSpeed = cos( (diff + 180) * Math.PI / 180.0) * interpolatedWeather.windSpeed

                    val headwindSpeedInUserUnit = if (isImperial) {
                        headwindSpeed * 2.23694 // Convert m/s to mph
                    } else {
                        headwindSpeed * 3.6 // Convert m/s to km/h
                    }

                    LineGraphBuilder.DataPoint(i.toFloat() * (windPoints.size / HEADWIND_SAMPLE_COUNT.toFloat()), headwindSpeedInUserUnit.toFloat())
                }
            } else {
                emptyList()
            }
        } catch(e: Exception) {
            Log.e(KarooHeadwindExtension.TAG, "Error calculating headwind points", e)
            emptyList()
        }

        return buildSet {
            add(LineGraphBuilder.Line(
                dataPoints = windPoints.mapIndexed { index, value ->
                    LineGraphBuilder.DataPoint(index.toFloat(), value.toFloat())
                },
                color = android.graphics.Color.GRAY,
                label = "Wind" // if (!isImperial) "Wind km/h" else "Wind mph",
            ))
            add(LineGraphBuilder.Line(
                dataPoints = gustPoints.mapIndexed { index, value ->
                    LineGraphBuilder.DataPoint(index.toFloat(), value.toFloat())
                },
                color = android.graphics.Color.DKGRAY,
                label = "Gust" // if (!isImperial) "Gust km/h" else "Gust mph",
            ))

            if (headwindPoints.isNotEmpty()) {
                add(LineGraphBuilder.Line(
                    dataPoints = headwindPoints,
                    color = android.graphics.Color.MAGENTA,
                    label = "Headwind", // if (!isImperial) "Headwind km/h" else "Headwind mph",
                    drawCircles = false
                ))
            }
        }
    }

    companion object {
        const val HEADWIND_SAMPLE_COUNT = 30
    }

}