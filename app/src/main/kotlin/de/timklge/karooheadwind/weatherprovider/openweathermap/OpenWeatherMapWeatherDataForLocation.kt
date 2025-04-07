package de.timklge.karooheadwind.weatherprovider.openweathermap

import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.weatherprovider.WeatherDataForLocation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenWeatherMapWeatherDataForLocation(
    val lat: Double,
    val lon: Double,
    val timezone: String,
    @SerialName("timezone_offset") val timezoneOffset: Int,
    val current: OpenWeatherMapWeatherData,
    val hourly: List<OpenWeatherMapForecastData>
){
    fun toWeatherDataForLocation(distanceAlongRoute: Double?): WeatherDataForLocation = WeatherDataForLocation(
        current = current.toWeatherData(),
        coords = GpsCoordinates(lat, lon, bearing = null, distanceAlongRoute = distanceAlongRoute),
        timezone = timezone,
        forecasts = hourly.map { it.toWeatherData() }
    )
}