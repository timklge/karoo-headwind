package de.timklge.karooheadwind.weatherprovider.openmeteo

import de.timklge.karooheadwind.weatherprovider.WeatherData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoWeatherData(
    val time: Long, val interval: Int,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("relative_humidity_2m") val relativeHumidity: Int,
    @SerialName("precipitation") val precipitation: Double,
    @SerialName("cloud_cover") val cloudCover: Int,
    @SerialName("surface_pressure") val surfacePressure: Double,
    @SerialName("pressure_msl") val sealevelPressure: Double,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("wind_direction_10m") val windDirection: Double,
    @SerialName("wind_gusts_10m") val windGusts: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("is_day") val isDay: Int,
) {
    fun toWeatherData(): WeatherData = WeatherData(
        temperature = temperature,
        relativeHumidity = relativeHumidity,
        precipitation = precipitation,
        cloudCover = cloudCover.toDouble(),
        surfacePressure = surfacePressure,
        sealevelPressure = sealevelPressure,
        windSpeed = windSpeed,
        windDirection = windDirection,
        windGusts = windGusts,
        weatherCode = weatherCode,
        time = time,
        isForecast = false,
        isNight = isDay == 0,
    )
}

