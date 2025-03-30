package de.timklge.karooheadwind.weatherprovider

import de.timklge.karooheadwind.WeatherDataProvider
import kotlinx.serialization.Serializable

@Serializable
data class WeatherDataResponse(
    val error: String? = null,
    val provider: WeatherDataProvider,
    val data: List<WeatherDataForLocation>,
)