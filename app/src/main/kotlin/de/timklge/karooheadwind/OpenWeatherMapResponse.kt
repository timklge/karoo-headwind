package de.timklge.karooheadwind

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenWeatherMapResponse(
    val coord: Coordinates,
    val weather: List<Weather>,
    val base: String,
    val main: Main,
    val visibility: Int,
    val wind: Wind,
    val clouds: Clouds,
    val rain: Rain? = null,
    val snow: Snow? = null,
    val dt: Long,
    val sys: Sys,
    val timezone: Int,
    val id: Int,
    val name: String,
    val cod: Int
) {
    fun toOpenMeteoData(): OpenMeteoData {
        return OpenMeteoData(
            time = dt,
            interval = 3600,
            temperature = main.temp,
            relativeHumidity = main.humidity,
            precipitation = rain?.h1 ?: 0.0,
            cloudCover = clouds.all,
            surfacePressure = main.pressure.toDouble(),
            sealevelPressure = main.seaLevel?.toDouble() ?: main.pressure.toDouble(),
            windSpeed = wind.speed,
            windDirection = wind.deg.toDouble(),
            windGusts = wind.gust ?: wind.speed,
            weatherCode = convertWeatherCodeToOpenMeteo(weather.firstOrNull()?.id ?: 800)
        )
    }

    private fun convertWeatherCodeToOpenMeteo(owmCode: Int): Int {
        // Mapping OpenWeatherMap to WMO OpenMeteo
        return when (owmCode) {
            in 200..299 -> 95 // Tormentas
            in 300..399 -> 51 // Llovizna
            in 500..599 -> 61 // Lluvia
            in 600..699 -> 71 // Nieve
            800 -> 0        // Despejado
            in 801..804 -> 1 // Nubosidad
            else -> 0
        }
    }
}

@Serializable
data class Coordinates(
    val lon: Double,
    val lat: Double
)

@Serializable
data class Weather(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

@Serializable
data class Main(
    val temp: Double,
    val feelsLike: Double = 0.0,
    @SerialName("feels_like") val feelsLikeAlt: Double = 0.0,
    val pressure: Int,
    val humidity: Int,
    @SerialName("temp_min") val tempMin: Double = 0.0,
    @SerialName("temp_max") val tempMax: Double = 0.0,
    @SerialName("sea_level") val seaLevel: Int? = null,
    @SerialName("grnd_level") val groundLevel: Int? = null
) {
    val feelsLikeValue: Double
        get() = if (feelsLikeAlt != 0.0) feelsLikeAlt else feelsLike
}

@Serializable
data class Wind(
    val speed: Double,
    val deg: Int,
    val gust: Double? = null
)

@Serializable
data class Clouds(
    val all: Int
)

@Serializable
data class Rain(
    @SerialName("1h") val h1: Double = 0.0,
    @SerialName("3h") val h3: Double = 0.0
)

@Serializable
data class Snow(
    @SerialName("1h") val h1: Double = 0.0,
    @SerialName("3h") val h3: Double = 0.0
)

@Serializable
data class Sys(
    val type: Int? = null,
    val id: Int? = null,
    val country: String? = null,
    val sunrise: Long? = null,
    val sunset: Long? = null
)
