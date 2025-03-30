package de.timklge.karooheadwind.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.timklge.karooheadwind.R
import de.timklge.karooheadwind.TemperatureUnit
import de.timklge.karooheadwind.weatherprovider.WeatherInterpretation
import de.timklge.karooheadwind.datatypes.getArrowBitmapByBearing
import de.timklge.karooheadwind.datatypes.getWeatherIcon
import kotlin.math.absoluteValue
import kotlin.math.ceil

@Composable
fun WeatherWidget(
    baseBitmap: Bitmap,
    current: WeatherInterpretation,
    windBearing: Int,
    windSpeed: Int,
    windGusts: Int,
    precipitation: Double,
    temperature: Int,
    temperatureUnit: TemperatureUnit,
    timeLabel: String? = null,
    dateLabel: String? = null,
    distance: Double? = null,
    includeDistanceLabel: Boolean = false,
    precipitationProbability: Int? = null,
    isImperial: Boolean
) {
    val fontSize = 20.sp

    Row(
        modifier = Modifier.fillMaxWidth().padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            if (dateLabel != null) {
                Text(
                    text = dateLabel,
                    style = TextStyle(
                        fontSize = fontSize
                    )
                )
            }

            if (distance != null && distance > 200) {
                val distanceInUserUnit = (distance / (if(!isImperial) 1000.0 else 1609.34)).toInt()
                val label = "${distanceInUserUnit.absoluteValue}${if(!isImperial) "km" else "mi"}"
                val text = if (includeDistanceLabel){
                    if(distanceInUserUnit > 0){
                        "In $label"
                    } else {
                        "$label ago"
                    }
                } else {
                    label
                }

                Text(
                    text = text,
                    style = TextStyle(
                        fontSize = fontSize
                    )
                )
            }

            if (timeLabel != null) {
                Text(
                    text = timeLabel,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize
                    )
                )
            }
        }

        // Weather icon (larger)
        Icon(
            painter = painterResource(id = getWeatherIcon(current)),
            contentDescription = "Current weather",
            modifier = Modifier.size(72.dp)
        )

        Column(horizontalAlignment = Alignment.End) {
            // Temperature (larger)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.thermometer),
                    contentDescription = "Temperature",
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "${temperature}${temperatureUnit.unitDisplay}",
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Precipitation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                val precipitationProbabilityLabel =
                    if (precipitationProbability != null) "${precipitationProbability}% " else ""

                Icon(
                    painter = painterResource(id = R.drawable.droplet_regular),
                    contentDescription = "Precipitation",
                    modifier = Modifier.size(18.dp)
                )

                Text(
                    text = "${precipitationProbabilityLabel}${ceil(precipitation).toInt()}",
                    style = TextStyle(
                        fontSize = fontSize
                    )
                )
            }

            // Wind
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Image(
                    bitmap = getArrowBitmapByBearing(baseBitmap, windBearing).asImageBitmap(),
                    colorFilter = ColorFilter.tint(Color.Black),
                    contentDescription = "Wind direction",
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "$windSpeed,$windGusts",
                    style = TextStyle(
                        fontSize = fontSize
                    )
                )
            }
        }
    }
}