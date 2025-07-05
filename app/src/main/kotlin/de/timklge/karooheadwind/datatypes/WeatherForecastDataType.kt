package de.timklge.karooheadwind.datatypes

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import de.timklge.karooheadwind.TemperatureUnit
import de.timklge.karooheadwind.weatherprovider.WeatherInterpretation
import io.hammerhead.karooext.KarooSystemService

class WeatherForecastDataType(karooSystem: KarooSystemService) : ForecastDataType(karooSystem, "weatherForecast") {
    @Composable
    override fun RenderWidget(
        arrowBitmap: Bitmap,
        current: WeatherInterpretation,
        windBearing: Int,
        windSpeed: Int,
        windGusts: Int,
        precipitation: Double,
        precipitationProbability: Int?,
        temperature: Int,
        temperatureUnit: TemperatureUnit,
        timeLabel: String,
        dateLabel: String?,
        distance: Double?,
        isImperial: Boolean,
        isNight: Boolean,
        uvi: Double,
    ) {
        Weather(
            arrowBitmap = arrowBitmap,
            current = current,
            windBearing = windBearing,
            windSpeed = windSpeed,
            windGusts = windGusts,
            precipitation = precipitation,
            precipitationProbability = precipitationProbability,
            temperature = temperature,
            temperatureUnit = temperatureUnit,
            timeLabel = timeLabel,
            dateLabel = dateLabel,
            distance = distance,
            isImperial = isImperial,
            isNight = isNight,
        )
    }

}