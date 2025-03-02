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
import kotlin.time.Duration.Companion.seconds

class OpenWeatherMapProvider(private val apiKey: String) : WeatherProvider {
    override suspend fun getWeatherData(
        service: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        settings: HeadwindSettings,
        profile: UserProfile?
    ): HttpResponseState.Complete {

        val response = makeOpenWeatherMapRequest(service, coordinates, apiKey, settings)

        if (response.error != null || response.body == null) {
            return response
        }

        try {

            val responseBody = response.body?.let { String(it) } ?: throw Exception("Respuesta vacía")
            val owmResponse = jsonWithUnknownKeys.decodeFromString<OpenWeatherMapResponse>(responseBody)


            val openMeteoData = owmResponse.toOpenMeteoData()


            val convertedResponse = OpenMeteoCurrentWeatherResponse(
                current = openMeteoData,
                latitude = owmResponse.coord.lat,
                longitude = owmResponse.coord.lon,
                timezone = "UTC", // OpenWeatherMap usa segundos en timezone
                elevation = 0.0,  // Valor por defecto
                utfOffsetSeconds = owmResponse.timezone,
                forecastData = null  // Valor por defecto
            )


            val convertedJson = jsonWithUnknownKeys.encodeToString(convertedResponse)


            return HttpResponseState.Complete(
                statusCode = response.statusCode,
                headers = response.headers,
                body = convertedJson.toByteArray(),
                error = null
            )
        } catch (e: Exception) {
            Log.e(KarooHeadwindExtension.TAG, "Error al procesar respuesta de OpenWeatherMap", e)
            return HttpResponseState.Complete(
                statusCode = 500,
                headers = emptyMap(),
                body = null,
                error = "Error al procesar respuesta: ${e.message}"
            )
        }
    }


    @OptIn(FlowPreview::class)
    private suspend fun makeOpenWeatherMapRequest(
        service: KarooSystemService,
        coordinates: List<GpsCoordinates>,
        apiKey: String,
        settings: HeadwindSettings
    ): HttpResponseState.Complete {
        return callbackFlow {
            val coordinate = coordinates.first() // Por ahora solo usamos la primera coordenada
            val url = "https://api.openweathermap.org/data/2.5/weather?lat=${coordinate.lat}&lon=${coordinate.lon}&appid=$apiKey&units=metric"

            Log.d(KarooHeadwindExtension.TAG, "Http request to OpenWeatherMap: $url")

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
                    Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap Http error: $err")
                    close(RuntimeException(err))
                }
            )
            awaitClose {
                service.removeConsumer(listenerId)
            }
        }.timeout(30.seconds).catch { e: Throwable ->
            if (e is TimeoutCancellationException) {
                Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap Http request timed out")
                emit(HttpResponseState.Complete(statusCode = 408, headers = emptyMap(), body = null, error = "Request timed out"))
            } else {
                Log.d(KarooHeadwindExtension.TAG, "OpenWeatherMap Http request failed", e)
                emit(HttpResponseState.Complete(statusCode = 500, headers = emptyMap(), body = null, error = e.message))
            }
        }.single()
    }
}