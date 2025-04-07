package de.timklge.karooheadwind.weatherprovider

import kotlinx.serialization.Serializable

@Serializable
data class WeatherData(
    val time: Long,
    val temperature: Double,
    val relativeHumidity: Double? = null,
    val precipitation: Double,
    val precipitationProbability: Double? = null,
    val cloudCover: Double? = null,
    val sealevelPressure: Double? = null,
    val surfacePressure: Double? = null,
    val windSpeed: Double,
    val windDirection: Double,
    val windGusts: Double,
    val weatherCode: Int,
    val isForecast: Boolean
)

