package de.timklge.karooheadwind.weatherprovider.openweathermap

import de.timklge.karooheadwind.weatherprovider.WeatherData
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset

@Serializable
data class OpenWeatherMapForecastData(
    val dt: Long,
    val temp: Double,
    val feels_like: Double,
    val pressure: Int,
    val humidity: Int,
    val clouds: Int,
    val visibility: Int,
    val wind_speed: Double,
    val wind_deg: Int,
    val wind_gust: Double? = null,
    val pop: Double,
    val rain: Rain? = null,
    val snow: Snow? = null,
    val weather: List<Weather>
) {
    fun toWeatherData(currentWeatherData: OpenWeatherMapWeatherData): WeatherData {
        val dtInstant = Instant.ofEpochSecond(dt)
        val sunriseInstant = Instant.ofEpochSecond(currentWeatherData.sunrise)
        val sunsetInstant = Instant.ofEpochSecond(currentWeatherData.sunset)

        val dtTime = dtInstant.atZone(ZoneOffset.UTC).toLocalTime()
        val sunriseTime = sunriseInstant.atZone(ZoneOffset.UTC).toLocalTime()
        val sunsetTime = sunsetInstant.atZone(ZoneOffset.UTC).toLocalTime()

        return WeatherData(
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
            isForecast = true,
            isNight = dtTime.isBefore(sunriseTime) || dtTime.isAfter(sunsetTime)
        )
    }
}
