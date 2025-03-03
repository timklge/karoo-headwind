package de.timklge.karooheadwind

import android.util.Log
import de.timklge.karooheadwind.datatypes.GpsCoordinates
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.timeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

class OpenWeatherMapProvider(private val apiKey: String) : WeatherProvider {
    override suspend fun getWeatherData(
        service: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        settings: HeadwindSettings,
        profile: UserProfile?
    ): HttpResponseState.Complete {

        val currentResponse = makeOpenWeatherMapCurrentRequest(service, coordinates, apiKey)
        if (currentResponse.error != null || currentResponse.body == null) {
            return currentResponse
        }

        // Obtener pronóstico
        val forecastResponse = makeOpenWeatherMapForecastRequest(service, coordinates, apiKey)
        if (forecastResponse.error != null) {
            Log.w(KarooHeadwindExtension.TAG, "Error in forecast: ${forecastResponse.error}")
        }

        try {

            val currentResponseBody = currentResponse.body?.let { String(it) }
                ?: throw Exception("Null answer to current weather")
            val owmCurrentResponse = jsonWithUnknownKeys.decodeFromString<OpenWeatherMapResponse>(currentResponseBody)
            val currentData = owmCurrentResponse.toOpenMeteoData()


            var forecastData: OpenMeteoForecastData? = null
            if (forecastResponse.error == null && forecastResponse.body != null) {
                val forecastResponseBody = forecastResponse.body?.let { String(it) }
                    ?: throw Exception("Null answer to forecast")

                try {
                    val owmForecastResponse = jsonWithUnknownKeys.decodeFromString<OpenWeatherMapForecastResponse>(forecastResponseBody)
                    forecastData = owmForecastResponse.toOpenMeteoForecastData()
                } catch (e: Exception) {
                    Log.e(KarooHeadwindExtension.TAG, "Error processing forecast", e)
                }
            }


            val convertedResponse = OpenWeatherMapCurrentWeatherResponse(
                current = currentData,
                latitude = owmCurrentResponse.coord.lat,
                longitude = owmCurrentResponse.coord.lon,
                timezone = "UTC",
                elevation = 0.0,
                utfOffsetSeconds = owmCurrentResponse.timezone,
                forecastData = forecastData
            )

            val convertedJson = jsonWithUnknownKeys.encodeToString(convertedResponse)
            return HttpResponseState.Complete(
                statusCode = currentResponse.statusCode,
                headers = currentResponse.headers,
                body = convertedJson.toByteArray(),
                error = null
            )
        } catch (e: Exception) {
            Log.e(KarooHeadwindExtension.TAG, "Error answer", e)
            return HttpResponseState.Complete(
                statusCode = 500,
                headers = emptyMap(),
                body = null,
                error = "Error in answers: ${e.message}"
            )
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun makeOpenWeatherMapCurrentRequest(
        service: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        apiKey: String
    ): HttpResponseState.Complete {
        return callbackFlow {
            val coordinate = coordinates.first()
            val url = "https://api.openweathermap.org/data/2.5/weather?lat=${coordinate.lat}&lon=${coordinate.lon}&appid=$apiKey&units=metric"

            Log.d(KarooHeadwindExtension.TAG, "Http request to OpenWeatherMap current: $url")

            val listenerId = service.addConsumer(
                OnHttpResponse.MakeHttpRequest(
                    "GET",
                    url,
                    waitForConnection = false,
                    headers = mapOf("User-Agent" to KarooHeadwindExtension.TAG)
                ),
                onEvent = { event: OnHttpResponse ->
                    if (event.state is HttpResponseState.Complete) {
                        trySendBlocking(event.state as HttpResponseState.Complete)
                        close()
                    }
                },
                onError = { err ->
                    Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap current Http error: $err")
                    close(RuntimeException(err))
                }
            )
            awaitClose {
                service.removeConsumer(listenerId)
            }
        }.timeout(20.seconds).catch { e: Throwable ->
            if (e is TimeoutCancellationException) {
                Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap current Http request timed out")
                emit(HttpResponseState.Complete(statusCode = 408, headers = emptyMap(), body = null, error = "Request timed out"))
            } else {
                Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap current Http request failed", e)
                emit(HttpResponseState.Complete(statusCode = 500, headers = emptyMap(), body = null, error = e.message))
            }
        }.single()
    }

    @OptIn(FlowPreview::class)
    private suspend fun makeOpenWeatherMapForecastRequest(
        service: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        apiKey: String
    ): HttpResponseState.Complete {
        return callbackFlow {
            val coordinate = coordinates.first()
            val url = "https://api.openweathermap.org/data/2.5/forecast?lat=${coordinate.lat}&lon=${coordinate.lon}&appid=$apiKey&units=metric"

            Log.d(KarooHeadwindExtension.TAG, "Http request to OpenWeatherMap forecast: $url")

            val listenerId = service.addConsumer(
                OnHttpResponse.MakeHttpRequest(
                    "GET",
                    url,
                    waitForConnection = false,
                    headers = mapOf("User-Agent" to KarooHeadwindExtension.TAG)
                ),
                onEvent = { event: OnHttpResponse ->
                    if (event.state is HttpResponseState.Complete) {
                        trySendBlocking(event.state as HttpResponseState.Complete)
                        close()
                    }
                },
                onError = { err ->
                    Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap forecast Http error: $err")
                    close(RuntimeException(err))
                }
            )
            awaitClose {
                service.removeConsumer(listenerId)
            }
        }.timeout(20.seconds).catch { e: Throwable ->
            if (e is TimeoutCancellationException) {
                Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap forecast Http request timed out")
                emit(HttpResponseState.Complete(statusCode = 408, headers = emptyMap(), body = null, error = "Request timed out"))
            } else {
                Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap forecast Http request failed", e)
                emit(HttpResponseState.Complete(statusCode = 500, headers = emptyMap(), body = null, error = e.message))
            }
        }.single()
    }
}

@Serializable
data class OpenWeatherMapForecastResponse(
    val list: List<OpenWeatherMapForecastItem>,
    val city: OpenWeatherMapCity
) {
    fun toOpenMeteoForecastData(): OpenMeteoForecastData {
        val times = mutableListOf<Long>()
        val temperatures = mutableListOf<Double>()
        val precipProbabilities = mutableListOf<Int>()
        val precipitations = mutableListOf<Double>()
        val weatherCodes = mutableListOf<Int>()
        val windSpeeds = mutableListOf<Double>()
        val windDirections = mutableListOf<Double>()
        val windGusts = mutableListOf<Double>()

        list.forEach { item ->
            times.add(item.dt)
            temperatures.add(item.main.temp)

            precipProbabilities.add((item.pop * 100).toInt())
            precipitations.add(item.rain?.h3 ?: 0.0)
            windSpeeds.add(item.wind.speed)
            windDirections.add(item.wind.deg.toDouble())
            windGusts.add(item.wind.gust ?: item.wind.speed)
            weatherCodes.add(convertWeatherCodeToOpenMeteo(item.weather.firstOrNull()?.id ?: 800))
        }

        return OpenMeteoForecastData(
            time = times,
            temperature = temperatures,
            precipitationProbability = precipProbabilities,
            precipitation = precipitations,
            weatherCode = weatherCodes,
            windSpeed = windSpeeds,
            windDirection = windDirections,
            windGusts = windGusts
        )
    }

    private fun convertWeatherCodeToOpenMeteo(owmCode: Int): Int {
        return when (owmCode) {
            in 200..299 -> 95 // Tormentas
            in 300..399 -> 51 // Llovizna
            in 500..599 -> 61 // Lluvia
            in 600..699 -> 71 // Nieve
            800 -> 0        // Despejado
            in 801..804 -> 1 // Nubosidad
            else -> 0
        }
    }
}

@Serializable
data class OpenWeatherMapForecastItem(
    val dt: Long,
    val main: Main,
    val weather: List<Weather>,
    val clouds: Clouds,
    val wind: Wind,
    val visibility: Int,
    val pop: Double,
    val rain: Rain? = null,
    val snow: Snow? = null,
    @SerialName("dt_txt") val dtTxt: String
)

@Serializable
data class OpenWeatherMapCity(
    val id: Int,
    val name: String,
    val coord: Coordinates,
    val country: String,
    val timezone: Int,
    val sunrise: Long,
    val sunset: Long
)