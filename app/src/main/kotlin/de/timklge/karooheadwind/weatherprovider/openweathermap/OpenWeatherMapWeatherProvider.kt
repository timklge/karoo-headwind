package de.timklge.karooheadwind.weatherprovider.openweathermap

import android.util.Log
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.WeatherDataProvider
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.jsonWithUnknownKeys
import de.timklge.karooheadwind.util.buildKarooOkHttpClient
import de.timklge.karooheadwind.weatherprovider.WeatherDataResponse
import de.timklge.karooheadwind.weatherprovider.WeatherProvider
import de.timklge.karooheadwind.weatherprovider.WeatherProviderException
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.absoluteValue


@Serializable
data class Weather(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
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

class OpenWeatherMapWeatherProvider(private val apiKey: String) : WeatherProvider {
    companion object {
        private const val MAX_API_CALLS = 3

        fun convertWeatherCodeToOpenMeteo(owmCode: Int): Int {
            // Mapping OpenWeatherMap to WMO OpenMeteo
            return when (owmCode) {
                in 200..299 -> 95 // Thunderstorm
                in 300..399 -> 51 // Drizzle
                in 500..599 -> 61 // Rain
                in 600..699 -> 71 // Snow
                800 -> 0        // Clear
                in 801..804 -> 1 // Cloudy
                else -> 0
            }
        }
    }

    override suspend fun getWeatherData(
        karooSystem: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        settings: HeadwindSettings,
        profile: UserProfile?
    ): WeatherDataResponse = coroutineScope {
        val selectedCoordinates = coordinates.take((MAX_API_CALLS - 1).coerceAtLeast(1)).toMutableList()

        if (coordinates.isNotEmpty() && !selectedCoordinates.contains(coordinates.last())) {
            selectedCoordinates.add(coordinates.last())
        }

        Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap: searching for ${selectedCoordinates.size} locations from ${coordinates.size} total")
        selectedCoordinates.forEachIndexed { index, coord ->
            Log.d(KarooHeadwindExtension.TAG, "Point #$index: ${coord.lat}, ${coord.lon}, distance: ${coord.distanceAlongRoute}")
        }

        val client = buildKarooOkHttpClient(karooSystem)

        val weatherDataForSelectedLocations = buildList {
            for (coordinate in selectedCoordinates) {
                val responseBody = makeOpenWeatherMapRequest(client, coordinate, apiKey)
                val weatherData = jsonWithUnknownKeys.decodeFromString<OpenWeatherMapWeatherDataForLocation>(responseBody)

                add(coordinate to weatherData)
            }
        }

        val allLocationData = coordinates.map { originalCoord ->
            val directMatch = weatherDataForSelectedLocations.find { it.first == originalCoord }

            if (directMatch != null) {
                directMatch.second.toWeatherDataForLocation(originalCoord.distanceAlongRoute)
            } else {

                val closestCoord = weatherDataForSelectedLocations.minByOrNull { (coord, _) ->
                    if (originalCoord.distanceAlongRoute != null && coord.distanceAlongRoute != null) {
                        (originalCoord.distanceAlongRoute - coord.distanceAlongRoute).absoluteValue
                    } else {
                        originalCoord.distanceTo(coord)
                    }
                } ?: throw WeatherProviderException(500, "Error finding nearest coordinate")


                closestCoord.second.toWeatherDataForLocation(originalCoord.distanceAlongRoute)
            }
        }

        WeatherDataResponse(
            provider = WeatherDataProvider.OPEN_WEATHER_MAP,
            data = allLocationData
        )
    }

    private suspend fun makeOpenWeatherMapRequest(
        client: OkHttpClient,
        coordinate: GpsCoordinates,
        apiKey: String
    ): String {
        val url = "https://api.openweathermap.org/data/3.0/onecall" +
            "?lat=${coordinate.lat}&lon=${coordinate.lon}" +
            "&appid=$apiKey&exclude=minutely,daily,alerts&units=metric"

        Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap: GET $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        when (response.code) {
            401, 403 -> {
                Log.e(KarooHeadwindExtension.TAG, "OpenWeatherMap API key is invalid or expired")
                throw WeatherProviderException(response.code, "OpenWeatherMap API key is invalid or expired")
            }
            !in 200..299 -> {
                Log.e(KarooHeadwindExtension.TAG, "OpenWeatherMap API request failed with status code ${response.code}")
                throw WeatherProviderException(response.code, "OpenWeatherMap API request failed with status code ${response.code}")
            }
        }

        return response.body?.string() ?: throw WeatherProviderException(500, "Null Response from OpenWeatherMap")
    }
}
