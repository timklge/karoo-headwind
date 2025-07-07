package de.timklge.karooheadwind.datatypes

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.R
import de.timklge.karooheadwind.UpcomingRoute
import de.timklge.karooheadwind.lerpWeather
import de.timklge.karooheadwind.screens.LineGraphBuilder
import de.timklge.karooheadwind.screens.isNightMode
import de.timklge.karooheadwind.util.signedAngleDifference
import io.hammerhead.karooext.KarooSystemService
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

fun interpolateWindLineColor(windSpeedInKmh: Double, night: Boolean, context: Context): androidx.compose.ui.graphics.Color {
    val default = Color(ContextCompat.getColor(context, R.color.gray))
    val green = Color(ContextCompat.getColor(context, R.color.green))
    val red = Color(ContextCompat.getColor(context, R.color.red))
    val orange = Color(ContextCompat.getColor(context, R.color.orange))

    return when {
        windSpeedInKmh <= -10 -> green
        windSpeedInKmh >= 15 -> red
        windSpeedInKmh in -10.0..0.0 -> interpolateColor(green, default, -10.0, 0.0, windSpeedInKmh)
        windSpeedInKmh in 0.0..10.0 -> interpolateColor(default, orange, 0.0, 10.0, windSpeedInKmh)
        else -> interpolateColor(orange, red, 10.0, 15.0, windSpeedInKmh)
    }
}

class HeadwindForecastDataType(karooSystem: KarooSystemService) : LineGraphForecastDataType(karooSystem, "headwindForecast") {
    override fun getLineData(
        lineData: List<LineData>,
        isImperial: Boolean,
        upcomingRoute: UpcomingRoute?,
        isPreview: Boolean,
        context: Context
    ): LineGraphForecastData {
        if (upcomingRoute == null && !isPreview){
            return LineGraphForecastData.Error("No route loaded")
        }

        val windPoints = lineData.map { data ->
            if (isImperial) { // Convert m/s to mph
                data.weatherData.windSpeed * 2.23694 // Convert m/s to mph
            } else { // Convert m/s to km/h
                data.weatherData.windSpeed * 3.6 // Convert m/s to km/h
            }
        }

        val headwindPoints = try {
            if (upcomingRoute != null){
                (0..<HEADWIND_SAMPLE_COUNT).mapNotNull { i ->
                    val t = i / HEADWIND_SAMPLE_COUNT.toDouble()

                    if (isPreview) {
                        // Use a sine wave for headwind preview speed
                        val headwindSpeed = 10f * kotlin.math.sin(i * Math.PI * 2 / HEADWIND_SAMPLE_COUNT).toFloat()

                        return@mapNotNull LineGraphBuilder.DataPoint(x = i.toFloat() * (windPoints.size / HEADWIND_SAMPLE_COUNT.toFloat()),
                            y = headwindSpeed)
                    }

                    val beforeLineData = lineData.getOrNull(floor((lineData.size) * t).toInt().coerceAtLeast(0)) ?: lineData.firstOrNull()
                    val afterLineData = lineData.getOrNull(ceil((lineData.size) * t).toInt().coerceAtLeast(0)) ?: lineData.lastOrNull()

                    if (beforeLineData?.weatherData == null || afterLineData?.weatherData == null || beforeLineData.distance == null
                        || afterLineData.distance == null || beforeLineData == afterLineData) return@mapNotNull null

                    val dt = remap(t.toFloat(),
                        floor(lineData.size * t).toFloat() / lineData.size,
                        ceil(lineData.size * t).toFloat() / lineData.size,
                        0.0f, 1.0f
                    ).toDouble()
                    val interpolatedWeather = lerpWeather(beforeLineData.weatherData, afterLineData.weatherData, dt)
                    val beforeDistanceAlongRoute = beforeLineData.distance
                    val afterDistanceAlongRoute = afterLineData.distance
                    val distanceAlongRoute = (beforeDistanceAlongRoute + (afterDistanceAlongRoute - beforeDistanceAlongRoute) * dt).coerceIn(0.0, upcomingRoute.routeLength)
                    val coordsAlongRoute = try {
                        TurfMeasurement.along(upcomingRoute.routePolyline, distanceAlongRoute, TurfConstants.UNIT_METERS)
                    } catch(e: Exception) {
                        Log.e(KarooHeadwindExtension.TAG, "Error getting coordinates along route", e)
                        return@mapNotNull null
                    }
                    val nextCoordsAlongRoute = try {
                        TurfMeasurement.along(upcomingRoute.routePolyline, distanceAlongRoute + 5, TurfConstants.UNIT_METERS)
                    } catch(e: Exception) {
                        Log.e(KarooHeadwindExtension.TAG, "Error getting next coordinates along route", e)
                        return@mapNotNull null
                    }
                    val bearingAlongRoute = try {
                        TurfMeasurement.bearing(coordsAlongRoute, nextCoordsAlongRoute)
                    } catch(e: Exception) {
                        Log.e(KarooHeadwindExtension.TAG, "Error calculating bearing along route", e)
                        return@mapNotNull null
                    }
                    val windBearing = interpolatedWeather.windDirection + 180
                    val diff = signedAngleDifference(bearingAlongRoute, windBearing)
                    val headwindSpeed = cos( (diff + 180) * Math.PI / 180.0) * interpolatedWeather.windSpeed

                    val headwindSpeedInUserUnit = if (isImperial) {
                        headwindSpeed * 2.23694 // Convert m/s to mph
                    } else {
                        headwindSpeed * 3.6 // Convert m/s to km/h
                    }

                    LineGraphBuilder.DataPoint(
                        x = i.toFloat() * (windPoints.size / HEADWIND_SAMPLE_COUNT.toFloat()),
                        y = headwindSpeedInUserUnit.toFloat()
                    )
                }
            } else {
                emptyList()
            }
        } catch(e: Exception) {
            Log.e(KarooHeadwindExtension.TAG, "Error calculating headwind points", e)
            emptyList()
        }

        return LineGraphForecastData.LineData(buildSet {
            if (headwindPoints.isNotEmpty()) {
                add(LineGraphBuilder.Line(
                    dataPoints = headwindPoints,
                    color = android.graphics.Color.BLACK,
                    label = "Head", // if (!isImperial) "Headwind km/h" else "Headwind mph",
                    drawCircles = false,
                    colorFunc = { headwindSpeed ->
                        val headwindSpeedInKmh = headwindSpeed * 3.6 // Convert m/s to km/h
                        interpolateWindLineColor(headwindSpeedInKmh, isNightMode(context), context).toArgb()
                    },
                    alpha = 255
                ))
            }
        })
    }

    companion object {
        const val HEADWIND_SAMPLE_COUNT = 70
    }

}