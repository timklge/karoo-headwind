package de.timklge.karooheadwind

import kotlinx.serialization.Serializable

@Serializable
enum class WeatherDataProvider(val id: String, val label: String) {
    OPEN_METEO("open-meteo", "OpenMeteo"),
    OPEN_WEATHER_MAP("open-weather-map", "OpenWeatherMap")
}