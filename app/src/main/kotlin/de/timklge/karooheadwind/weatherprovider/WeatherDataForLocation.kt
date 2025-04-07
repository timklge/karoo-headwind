package de.timklge.karooheadwind.weatherprovider

import de.timklge.karooheadwind.datatypes.GpsCoordinates
import kotlinx.serialization.Serializable

@Serializable
data class WeatherDataForLocation(
    val current: WeatherData,
    val coords: GpsCoordinates,
    val timezone: String? = null,
    val elevation: Double? = null,
    val forecasts: List<WeatherData>? = null,
)