package de.timklge.karooheadwind

import de.timklge.karooheadwind.datatypes.GpsCoordinates
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class WindUnit(val id: String, val label: String, val unitDisplay: String){
    KILOMETERS_PER_HOUR("kmh", "Kilometers (km/h)", "km/h"),
    METERS_PER_SECOND("ms", "Meters (m/s)", "m/s"),
    MILES_PER_HOUR("mph", "Miles (mph)", "mph"),
    KNOTS("kn", "Knots (kn)", "kn")
}

enum class PrecipitationUnit(val id: String, val label: String, val unitDisplay: String){
    MILLIMETERS("mm", "Millimeters (mm)", "mm"),
    INCH("inch", "Inch", "in")
}

enum class WindDirectionIndicatorTextSetting(val id: String, val label: String){
    HEADWIND_SPEED("headwind-speed", "Headwind speed"),
    WIND_SPEED("absolute-wind-speed", "Absolute wind speed"),
    NONE("none", "None")
}

enum class WindDirectionIndicatorSetting(val id: String, val label: String){
    HEADWIND_DIRECTION("headwind-direction", "Headwind"),
    WIND_DIRECTION("wind-direction", "Absolute wind direction"),
}

enum class TemperatureUnit(val id: String, val label: String, val unitDisplay: String){
    CELSIUS("celsius", "Celsius (°C)", "°C"),
    FAHRENHEIT("fahrenheit", "Fahrenheit (°F)", "°F")
}

enum class RoundLocationSetting(val id: String, val label: String, val km: Int){
    KM_1("1 km", "1 km", 1),
    KM_2("2 km", "2 km", 2),
    KM_3("3 km", "3 km", 3),
    KM_5("5 km", "5 km", 5)
}

@Serializable
data class HeadwindWidgetSettings(
    val currentForecastHourOffset: Int = 0
){
    companion object {
        val defaultWidgetSettings = Json.encodeToString(HeadwindWidgetSettings())
    }
}

//Moded with openweahtermap.org
@Serializable
data class HeadwindStats(
    val lastSuccessfulWeatherRequest: Long? = null,
    val lastSuccessfulWeatherPosition: GpsCoordinates? = null,
    val failedWeatherRequest: Long? = null,
    val lastSuccessfulWeatherProvider: WeatherDataProvider? = null
){
    companion object {
        val defaultStats = Json.encodeToString(HeadwindStats())
    }
}



@Serializable
data class HeadwindSettings(
    val welcomeDialogAccepted: Boolean = false,
    val windDirectionIndicatorTextSetting: WindDirectionIndicatorTextSetting = WindDirectionIndicatorTextSetting.HEADWIND_SPEED,
    val windDirectionIndicatorSetting: WindDirectionIndicatorSetting = WindDirectionIndicatorSetting.HEADWIND_DIRECTION,
    val roundLocationTo: RoundLocationSetting = RoundLocationSetting.KM_3,
    val forecastedKmPerHour: Int = 20,
    val forecastedMilesPerHour: Int = 12,
    val lastUpdateRequested: Long? = null,
    val showDistanceInForecast: Boolean = true,
    val weatherProvider: WeatherDataProvider = WeatherDataProvider.OPEN_METEO,
    val openWeatherMapApiKey: String = ""
){
    companion object {
        val defaultSettings = Json.encodeToString(HeadwindSettings())
    }

    fun getForecastMetersPerHour(isImperial: Boolean): Int {
        return if (isImperial) forecastedMilesPerHour * 1609 else forecastedKmPerHour * 1000
    }
}

//added openweathermap.org

@Serializable
enum class WeatherDataProvider(val id: String, val label: String) {
    OPEN_METEO("open-meteo", "OpenMeteo"),
    OPEN_WEATHER_MAP("open-weather-map", "OpenWeatherMap")
}