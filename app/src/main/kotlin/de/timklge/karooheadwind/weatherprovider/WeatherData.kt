package de.timklge.karooheadwind.weatherprovider

import kotlinx.serialization.Serializable

@Serializable
data class WeatherData(
    val time: Long,
    val temperature: Double,
    val relativeHumidity: Int,
    val precipitation: Double,
    val precipitationProbability: Double? = null,
    val cloudCover: Double,
    val sealevelPressure: Double,
    val surfacePressure: Double,
    val windSpeed: Double,
    val windDirection: Double,
    val windGusts: Double,
    val weatherCode: Int,
    val isForecast: Boolean,
    val isNight: Boolean,
    val uvi: Double,
)

