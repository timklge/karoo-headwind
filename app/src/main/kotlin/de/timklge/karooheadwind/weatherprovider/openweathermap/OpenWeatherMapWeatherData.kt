package de.timklge.karooheadwind.weatherprovider.openweathermap

import de.timklge.karooheadwind.weatherprovider.WeatherData
import kotlinx.serialization.Serializable

@Serializable
data class OpenWeatherMapWeatherData(
    val dt: Long,
    val sunrise: Long,
    val sunset: Long,
    val temp: Double,
    val feels_like: Double,
    val pressure: Int,
    val humidity: Int,
    val clouds: Int,
    val visibility: Int,
    val wind_speed: Double,
    val wind_deg: Int,
    val wind_gust: Double? = null,
    val rain: Rain? = null,
    val snow: Snow? = null,
    val uvi: Double,
    val weather: List<Weather>){

    fun toWeatherData(): WeatherData = WeatherData(
        uvi = uvi,
        temperature = temp,
        relativeHumidity = humidity,
        precipitation = rain?.h1 ?: 0.0,
        cloudCover = clouds.toDouble(),
        surfacePressure = pressure.toDouble(),
        sealevelPressure = pressure.toDouble(), // FIXME
        windSpeed = wind_speed,
        windDirection = wind_deg.toDouble(),
        windGusts = wind_gust ?: wind_speed,
        weatherCode = OpenWeatherMapWeatherProvider.convertWeatherCodeToOpenMeteo(
            weather.firstOrNull()?.id ?: 800
        ),
        time = dt,
        isNight = let {
            dt !in sunrise..<sunset
        },
        isForecast = false
    )
}