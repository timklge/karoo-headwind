package de.timklge.karooheadwind.datatypes

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import de.timklge.karooheadwind.TemperatureUnit
import de.timklge.karooheadwind.WeatherInterpretation
import io.hammerhead.karooext.KarooSystemService
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun PrecipitationForecast(
    precipitation: Int,
    precipitationProbability: Int?,
    distance: Double? = null,
    timeLabel: String? = null,
    rowAlignment: Alignment.Horizontal = Alignment.Horizontal.CenterHorizontally,
    isImperial: Boolean?
) {
    Column(modifier = GlanceModifier.fillMaxHeight().padding(1.dp).width(86.dp), horizontalAlignment = rowAlignment) {
        Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth(), horizontalAlignment = rowAlignment, verticalAlignment = Alignment.CenterVertically) {
            val precipitationProbabilityText = if (precipitationProbability != null) "${precipitationProbability}% " else ""
            val precipitationText = if (precipitation > 0) precipitation.toString() else ""
            Text(
                text = "${precipitationProbabilityText}${precipitationText}",
                style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontFamily = FontFamily.Monospace, fontSize = TextUnit(24f, TextUnitType.Sp), textAlign = TextAlign.Center)
            )
        }

        if (distance != null && isImperial != null){
            val distanceInUserUnit = (distance / (if(!isImperial) 1000.0 else 1609.34)).toInt()
            val label = "${distanceInUserUnit.absoluteValue}${if(!isImperial) "km" else "mi"}"
            val text = if(distanceInUserUnit > 0){
                "In $label"
            } else {
                "$label ago"
            }

            if (distanceInUserUnit != 0){
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = text,
                        style = TextStyle(
                            color = ColorProvider(Color.Black, Color.White),
                            fontFamily = FontFamily.Monospace,
                            fontSize = TextUnit(18f, TextUnitType.Sp)
                        )
                    )
                }
            }
        }

        if (timeLabel != null){
            Text(
                text = timeLabel,
                style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, fontSize = TextUnit(18f, TextUnitType.Sp)
                )
            )
        }
    }
}

class PrecipitationForecastDataType(karooSystem: KarooSystemService) : ForecastDataType(karooSystem, "precipitationForecast") {
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
        isImperial: Boolean
    ) {
        PrecipitationForecast(
            precipitation = precipitation.roundToInt().coerceAtLeast(0),
            precipitationProbability = precipitationProbability,
            distance = distance,
            timeLabel = timeLabel,
            isImperial = isImperial
        )
    }
}