package de.timklge.karooheadwind.weatherprovider.openmeteo

import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.weatherprovider.WeatherDataForLocation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoWeatherDataForLocation(
    val current: OpenMeteoWeatherData,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val elevation: Double,
    @SerialName("utc_offset_seconds") val utfOffsetSeconds: Int,
    @SerialName("hourly") val forecastData: OpenMeteoWeatherForecastData?,
) {
    fun toWeatherDataForLocation(): WeatherDataForLocation {
        val forecasts = forecastData?.toWeatherData()
        return WeatherDataForLocation(
            current = current.toWeatherData(),
            coords = GpsCoordinates(latitude, longitude),
            timezone = timezone,
            elevation = elevation,
            forecasts = forecasts
        )
    }
}