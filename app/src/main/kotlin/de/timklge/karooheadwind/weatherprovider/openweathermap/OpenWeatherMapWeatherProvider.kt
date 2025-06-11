package de.timklge.karooheadwind.weatherprovider.openweathermap

import android.util.Log
import de.timklge.karooheadwind.HeadwindSettings
import de.timklge.karooheadwind.KarooHeadwindExtension
import de.timklge.karooheadwind.WeatherDataProvider
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import de.timklge.karooheadwind.jsonWithUnknownKeys
import de.timklge.karooheadwind.weatherprovider.WeatherDataResponse
import de.timklge.karooheadwind.weatherprovider.WeatherProvider
import de.timklge.karooheadwind.weatherprovider.WeatherProviderException
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.timeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.seconds


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
        private const val MAX_API_CALLS = 4

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


        val selectedCoordinates = when {
            coordinates.size <= MAX_API_CALLS -> coordinates
            else -> {

                val mandatoryCoordinates = coordinates.take(3).toMutableList()


                val fourthIndex = if (coordinates.size > 6) {
                    coordinates.size - 3
                } else {
                    (coordinates.size / 2) + 1
                }

                mandatoryCoordinates.add(coordinates[fourthIndex.coerceIn(3, coordinates.lastIndex)])
                mandatoryCoordinates
            }
        }

        Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap: searching for ${selectedCoordinates.size} locations from ${coordinates.size} total")
        selectedCoordinates.forEachIndexed { index, coord ->
            Log.d(KarooHeadwindExtension.TAG, "Point #$index: ${coord.lat}, ${coord.lon}, distance: ${coord.distanceAlongRoute}")
        }


        val weatherDataForSelectedLocations = selectedCoordinates.map { coordinate ->
            async {
                val response = makeOpenWeatherMapRequest(karooSystem, coordinate, apiKey)
                val responseBody = response.body?.let { String(it) }
                    ?: throw WeatherProviderException(response.statusCode, "Null Response from OpenWeatherMap")

                val weatherData = jsonWithUnknownKeys.decodeFromString<OpenWeatherMapWeatherDataForLocation>(responseBody)
                coordinate to weatherData
            }
        }.awaitAll()


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


    @OptIn(FlowPreview::class)
    private suspend fun makeOpenWeatherMapRequest(
        service: KarooSystemService,
        coordinate: GpsCoordinates,
        apiKey: String
    ): HttpResponseState.Complete {
        val response = callbackFlow {

            // URL API 3.0 with onecall endpoint
            val url = "https://api.openweathermap.org/data/3.0/onecall?lat=${coordinate.lat}&lon=${coordinate.lon}" +
                    "&appid=$apiKey&exclude=minutely,daily,alerts&units=metric"

            Log.d(KarooHeadwindExtension.TAG, "Http request to OpenWeatherMap API 3.0: $url")

            val listenerId = service.addConsumer(
                OnHttpResponse.MakeHttpRequest(
                    "GET",
                    url,
                    waitForConnection = false,
                    headers = mapOf("User-Agent" to KarooHeadwindExtension.TAG)
                ),
                onEvent = { event: OnHttpResponse ->
                    if (event.state is HttpResponseState.Complete) {
                        Log.d(KarooHeadwindExtension.TAG, "Http response received from OpenWeatherMap")
                        trySend(event.state as HttpResponseState.Complete)
                        close()
                    }
                },
                onError = { err ->
                    Log.e(KarooHeadwindExtension.TAG, "Http error: $err")
                    close(WeatherProviderException(0, err))
                }
            )

            awaitClose {
                service.removeConsumer(listenerId)
            }
        }.timeout(30.seconds).catch { e: Throwable ->
            if (e is TimeoutCancellationException) {
                emit(HttpResponseState.Complete(500, mapOf(), null, "Timeout"))
            } else {
                throw e
            }
        }.single()

        if (response.statusCode == 401 || response.statusCode == 403){
            Log.e(KarooHeadwindExtension.TAG, "OpenWeatherMap API key is invalid or expired")
            throw WeatherProviderException(response.statusCode, "OpenWeatherMap API key is invalid or expired")
        } else if (response.statusCode !in 200..299) {
            Log.e(KarooHeadwindExtension.TAG, "OpenWeatherMap API request failed with status code ${response.statusCode}")
            throw WeatherProviderException(response.statusCode, "OpenWeatherMap API request failed with status code ${response.statusCode}")
        }

        return response
    }
}
