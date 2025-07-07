package de.timklge.karooheadwind.weatherprovider.openmeteo

import de.timklge.karooheadwind.weatherprovider.WeatherData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoWeatherForecastData(
    @SerialName("time") val time: List<Long>,
    @SerialName("temperature_2m") val temperature: List<Double>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int>,
    @SerialName("precipitation") val precipitation: List<Double>,
    @SerialName("weather_code") val weatherCode: List<Int>,
    @SerialName("wind_speed_10m") val windSpeed: List<Double>,
    @SerialName("wind_direction_10m") val windDirection: List<Double>,
    @SerialName("wind_gusts_10m") val windGusts: List<Double>,
    @SerialName("cloud_cover") val cloudCover: List<Double>,
    @SerialName("surface_pressure") val surfacePressure: List<Double>,
    @SerialName("pressure_msl") val sealevelPressure: List<Double>,
    @SerialName("is_day") val isDay: List<Int>,
    @SerialName("relative_humidity_2m") val relativeHumidity: List<Int>,
    @SerialName("uv_index") val uvi: List<Double>,
    ) {
    fun toWeatherData(): List<WeatherData> {
        return time.mapIndexed { index, t ->
            WeatherData(
                temperature = temperature[index],
                precipitation = precipitation[index],
                precipitationProbability = precipitationProbability[index].toDouble(),
                windSpeed = windSpeed[index],
                windDirection = windDirection[index],
                windGusts = windGusts[index],
                weatherCode = weatherCode[index],
                isNight = isDay[index] == 0,
                time = t,
                isForecast = true,
                cloudCover = cloudCover[index],
                surfacePressure = surfacePressure[index],
                sealevelPressure = sealevelPressure[index],
                relativeHumidity = relativeHumidity[index],
                uvi = uvi[index]
            )
        }
    }
}